package com.throne.ui.vpn

import android.content.Context
import com.throne.ui.data.ConfigBuilder
import com.throne.ui.data.ScanProgress
import com.throne.ui.data.ServerProfile
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LatencyScanner(private val context: Context) {

    suspend fun scanProfiles(
        profiles: List<ServerProfile>,
        onProgress: (ScanProgress, Map<Long, Int>) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (profiles.isEmpty()) return@withContext

        val isSingle = profiles.size == 1
        val total = profiles.size
        val ordered = sortByPriority(profiles)
        val doneCount = AtomicInteger(0)
        val onlineCount = AtomicInteger(0)
        val results = ConcurrentHashMap<Long, Int>()
        val pendingDelta = ConcurrentHashMap<Long, Int>()

        fun emit(activeIds: Set<Long>, active: Boolean) {
            val delta = HashMap<Long, Int>()
            val keys = pendingDelta.keys().toList()
            for (k in keys) {
                pendingDelta.remove(k)?.let { delta[k] = it }
            }
            onProgress(
                ScanProgress(
                    active = active,
                    single = isSingle,
                    total = total,
                    done = doneCount.get(),
                    onlineCount = onlineCount.get(),
                    activeIds = activeIds,
                    targetName = if (isSingle) profiles.first().name else ""
                ),
                delta
            )
        }

        emit(ordered.take(6).map { it.id }.toSet(), true)

        // Stage 1: fast TCP pre-filter when VPN is disconnected (skip UDP-only like HY2)
        val vpnRunning = ThroneEngine.runtime.value.state != VpnConnectionState.STOPPED
        val candidates = if (!vpnRunning && ordered.size > 8) {
            val sem = Semaphore(24)
            val alive = ArrayList<ServerProfile>()
            val lock = Any()
            val jobs = ordered.map { p ->
                async {
                    currentCoroutineContext().ensureActive()
                    if (p.type == "HY2") {
                        synchronized(lock) { alive.add(p) }
                    } else {
                        val reachable = sem.withPermit { tcpReachable(p.server, p.port, 1400) }
                        if (reachable) {
                            synchronized(lock) { alive.add(p) }
                        } else {
                            results[p.id] = -1
                            pendingDelta[p.id] = -1
                            doneCount.incrementAndGet()
                        }
                    }
                }
            }
            jobs.awaitAll()
            emit(alive.take(6).map { it.id }.toSet(), true)
            alive
        } else {
            ordered
        }

        // Stage 2: real sing-box URLTest via ephemeral Clash API in batches of 25
        val batches = candidates.chunked(25)
        for (batch in batches) {
            currentCoroutineContext().ensureActive()

            val validBatch = filterValidConfigs(batch) { badId ->
                results[badId] = -1
                pendingDelta[badId] = -1
                doneCount.incrementAndGet()
            }
            if (validBatch.isEmpty()) {
                emit(emptySet(), true)
                continue
            }

            val port = findFreePort()
            val (configJson, tagMap) = ConfigBuilder.buildLatencyTestConfig(validBatch, port)
            var testBox: BoxService? = null

            try {
                val platform = PlatformInterfaceImpl(
                    context = context.applicationContext,
                    vpnServiceProvider = { ThroneEngine.activeVpnService },
                    onLog = {}
                )
                testBox = Libbox.newService(configJson, platform)
                testBox.start()
                waitForClashApi(port)

                val sem = Semaphore(8)
                val activeBatchIds = ConcurrentHashMap.newKeySet<Long>()
                validBatch.take(6).forEach { activeBatchIds.add(it.id) }
                emit(activeBatchIds.toSet(), true)

                val batchJobs = validBatch.map { profile ->
                    async {
                        currentCoroutineContext().ensureActive()
                        sem.withPermit {
                            activeBatchIds.add(profile.id)
                            val tag = tagMap[profile.id]!!
                            val delayMs = queryClashDelay(port, tag, timeoutMs = 3500)
                            results[profile.id] = delayMs
                            pendingDelta[profile.id] = delayMs
                            if (delayMs > 0) onlineCount.incrementAndGet()
                            doneCount.incrementAndGet()
                            activeBatchIds.remove(profile.id)
                            emit(activeBatchIds.toSet(), true)
                        }
                    }
                }
                batchJobs.awaitAll()
            } catch (_: Throwable) {
                for (p in validBatch) {
                    if (!results.containsKey(p.id)) {
                        results[p.id] = -1
                        pendingDelta[p.id] = -1
                        doneCount.incrementAndGet()
                    }
                }
                emit(emptySet(), true)
            } finally {
                try {
                    testBox?.close()
                } catch (_: Throwable) {
                }
            }
        }

        emit(emptySet(), false)
    }

    private fun sortByPriority(profiles: List<ServerProfile>): List<ServerProfile> {
        if (profiles.size <= 1) return profiles
        val fast = profiles.filter { (it.latency ?: 0) in 1..150 }.sortedBy { it.latency }
        val otherAlive = profiles.filter { (it.latency ?: 0) > 150 }.sortedBy { it.latency }
        val untested = profiles.filter { it.latency == null || it.latency == 0 }
        val dead = profiles.filter { (it.latency ?: 0) < 0 }
        return fast + otherAlive + untested + dead
    }

    private fun filterValidConfigs(
        batch: List<ServerProfile>,
        onInvalid: (Long) -> Unit
    ): List<ServerProfile> {
        val samplePort = 19099
        val (fullCfg, _) = ConfigBuilder.buildLatencyTestConfig(batch, samplePort)
        val batchOk = try {
            Libbox.checkConfig(fullCfg)
            true
        } catch (_: Throwable) {
            false
        }
        if (batchOk) return batch

        val valid = ArrayList<ServerProfile>(batch.size)
        for (p in batch) {
            val ok = try {
                Libbox.checkConfig(ConfigBuilder.buildSingleCheckConfig(p))
                true
            } catch (_: Throwable) {
                false
            }
            if (ok) valid.add(p) else onInvalid(p.id)
        }
        return valid
    }

    private fun tcpReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun findFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    private suspend fun waitForClashApi(port: Int) {
        repeat(15) {
            currentCoroutineContext().ensureActive()
            try {
                val conn = URL("http://127.0.0.1:$port/version").openConnection() as HttpURLConnection
                conn.connectTimeout = 200
                conn.readTimeout = 200
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..499) return
            } catch (_: Throwable) {
            }
            delay(35)
        }
    }

    private fun queryClashDelay(port: Int, tag: String, timeoutMs: Int): Int {
        return try {
            val encodedTag = URLEncoder.encode(tag, "UTF-8")
            val testUrl = URLEncoder.encode("http://cp.cloudflare.com/generate_204", "UTF-8")
            val url = URL("http://127.0.0.1:$port/proxies/$encodedTag/delay?timeout=$timeoutMs&url=$testUrl")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs + 1000
                readTimeout = timeoutMs + 1000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            conn.disconnect()
            if (code in 200..299 && body.isNotBlank()) {
                val delayVal = JSONObject(body).optInt("delay", -1)
                if (delayVal > 0) delayVal else -1
            } else {
                -1
            }
        } catch (_: Throwable) {
            -1
        }
    }
}

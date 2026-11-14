package com.throne.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.throne.ui.data.AppInstalledItem
import com.throne.ui.data.AppSettings
import com.throne.ui.data.AppStorage
import com.throne.ui.data.BUILTIN_SOURCES
import com.throne.ui.data.BuiltinSource
import com.throne.ui.data.ConfigBuilder
import com.throne.ui.data.ConnectionMode
import com.throne.ui.data.LinkParser
import com.throne.ui.data.ScanProgress
import com.throne.ui.data.ServerGroup
import com.throne.ui.data.ServerProfile
import com.throne.ui.data.SplitTunnelMode
import com.throne.ui.vpn.EngineRuntimeState
import com.throne.ui.vpn.LatencyScanner
import com.throne.ui.vpn.ThroneEngine
import com.throne.ui.vpn.VpnConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class UiState(
    val groups: List<ServerGroup> = listOf(ServerGroup(0L, "Default")),
    val profiles: List<ServerProfile> = emptyList(),
    val filteredProfiles: List<ServerProfile> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val selectedGroupId: Long = -1L,
    val searchQuery: String = "",
    val scan: ScanProgress = ScanProgress(),
    val isBusy: Boolean = false,
    val toastMessage: String? = null
) {
    val activeProfile: ServerProfile?
        get() {
            val selId = settings.selectedProfileId
            if (selId != null) {
                profiles.firstOrNull { it.id == selId }?.let { return it }
            }
            return profiles.firstOrNull()
        }
}

class ThroneViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val storage = AppStorage(application)
    private val scanner = LatencyScanner(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val runtimeState: StateFlow<EngineRuntimeState> = ThroneEngine.runtime

    private val _installedApps = MutableStateFlow<List<AppInstalledItem>>(emptyList())
    val installedApps: StateFlow<List<AppInstalledItem>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private var scanJob: Job? = null
    private var pendingVpnStart: (() -> Unit)? = null

    init {
        val groups = storage.loadGroups()
        val profiles = storage.loadProfiles()
        val settings = storage.loadSettings()
        _uiState.value = rebuildFiltered(
            UiState(
                groups = groups,
                profiles = profiles,
                settings = settings
            )
        )
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val action = pendingVpnStart
        pendingVpnStart = null
        if (granted) {
            action?.invoke()
        } else {
            showToast("VPN permission is required for VPN mode")
        }
    }

    fun selectProfile(profileId: Long) {
        val newSettings = _uiState.value.settings.copy(selectedProfileId = profileId)
        storage.saveSettings(newSettings)
        _uiState.update { it.copy(settings = newSettings) }
    }

    fun selectGroup(groupId: Long) {
        _uiState.update { rebuildFiltered(it.copy(selectedGroupId = groupId)) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { rebuildFiltered(it.copy(searchQuery = query)) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun toggleConnection(requestVpnPermission: (Intent) -> Unit) {
        val cur = runtimeState.value.state
        if (cur != VpnConnectionState.STOPPED) {
            disconnect()
            return
        }
        val profile = _uiState.value.activeProfile
        if (profile == null) {
            showToast("Add a server first")
            return
        }
        connectProfile(profile, requestVpnPermission)
    }

    fun connectProfile(profile: ServerProfile, requestVpnPermission: (Intent) -> Unit) {
        cancelScan()
        selectProfile(profile.id)

        val settings = _uiState.value.settings
        val startAction = {
            try {
                val cfg = ConfigBuilder.buildRuntimeConfig(profile, settings, app.packageName)
                ThroneEngine.start(app, cfg, profile.id, profile.name, settings.mode)
            } catch (t: Throwable) {
                showToast(t.message ?: "Config error")
            }
        }

        if (settings.mode == ConnectionMode.VPN) {
            val prep = VpnService.prepare(app)
            if (prep != null) {
                pendingVpnStart = startAction
                requestVpnPermission(prep)
                return
            }
        }
        startAction()
    }

    fun connectFastest(groupId: Long, requestVpnPermission: (Intent) -> Unit) {
        val pool = _uiState.value.profiles.filter { groupId == -1L || it.groupId == groupId }
        if (pool.isEmpty()) {
            showToast("No servers available")
            return
        }

        val testedAlive = pool.filter { (it.latency ?: 0) > 0 }.minByOrNull { it.latency!! }
        if (testedAlive != null) {
            connectProfile(testedAlive, requestVpnPermission)
            return
        }

        // No tested servers yet — scan first, then connect to best
        if (scanJob?.isActive == true) return
        showToast("Scanning for fastest server...")
        scanJob = viewModelScope.launch {
            try {
                runScanInternal(pool)
                val best = _uiState.value.profiles
                    .filter { (groupId == -1L || it.groupId == groupId) && (it.latency ?: 0) > 0 }
                    .minByOrNull { it.latency!! }
                if (best != null) {
                    connectProfile(best, requestVpnPermission)
                } else {
                    showToast("No reachable servers found")
                }
            } catch (_: CancellationException) {
            }
        }
    }

    fun disconnect() {
        ThroneEngine.stop(app)
    }

    fun setMode(mode: ConnectionMode, requestVpnPermission: (Intent) -> Unit) {
        val oldSettings = _uiState.value.settings
        if (oldSettings.mode == mode) return
        val nextSettings = oldSettings.copy(mode = mode)
        storage.saveSettings(nextSettings)
        _uiState.update { it.copy(settings = nextSettings) }

        val isRunning = runtimeState.value.state != VpnConnectionState.STOPPED
        if (isRunning) {
            val active = _uiState.value.activeProfile
            if (active != null) {
                connectProfile(active, requestVpnPermission)
            } else {
                disconnect()
            }
        }
    }

    fun restartEngine(requestVpnPermission: (Intent) -> Unit) {
        val isRunning = runtimeState.value.state != VpnConnectionState.STOPPED
        val active = _uiState.value.activeProfile
        if (isRunning && active != null) {
            disconnect()
            viewModelScope.launch {
                kotlinx.coroutines.delay(350)
                connectProfile(active, requestVpnPermission)
            }
        } else {
            ThroneEngine.appendLog("Engine ready (${runtimeState.value.coreVersion})")
            showToast("Engine is ready")
        }
    }

    fun importInput(rawText: String, targetGroupId: Long) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return

        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                    importSubscriptionUrl(trimmed, customName = null)
                } else {
                    val gid = if (targetGroupId >= 0L) targetGroupId else 0L
                    val nextId = (_uiState.value.profiles.maxOfOrNull { it.id } ?: 0L) + 1L
                    val parsed = LinkParser.parseLinks(trimmed, gid, nextId)
                    if (parsed.isEmpty()) {
                        showToast("No supported links found (VLESS, SS, Hy2, Trojan, VMess)")
                    } else {
                        val merged = _uiState.value.profiles + parsed
                        storage.saveProfiles(merged)
                        _uiState.update { rebuildFiltered(it.copy(profiles = merged)) }
                        showToast("Imported ${parsed.size} servers")
                    }
                }
            } catch (t: Throwable) {
                showToast(t.message ?: "Import failed")
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun addOrUpdateBuiltin(source: BuiltinSource) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                importSubscriptionUrl(source.url, customName = source.name)
            } catch (t: Throwable) {
                showToast(t.message ?: "Failed to load ${source.name}")
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun removeBuiltinGroup(source: BuiltinSource) {
        val group = _uiState.value.groups.firstOrNull { it.url == source.url || it.name == source.name }
            ?: return
        deleteGroup(group.id)
    }

    fun updateAllSubscriptions() {
        val subGroups = _uiState.value.groups.filter { it.url.isNotBlank() }
        if (subGroups.isEmpty()) {
            showToast("No subscriptions to update")
            return
        }
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                var totalImported = 0
                for (g in subGroups) {
                    totalImported += refreshSubscriptionGroup(g)
                }
                showToast("Subscriptions updated ($totalImported servers)")
            } catch (t: Throwable) {
                showToast(t.message ?: "Update failed")
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun importSubscriptionUrl(url: String, customName: String?) {
        val existing = _uiState.value.groups.firstOrNull { it.url == url }
        val group = if (existing != null) {
            existing
        } else {
            val nextGid = (_uiState.value.groups.maxOfOrNull { it.id } ?: 0L) + 1L
            val name = customName ?: deriveSubName(url)
            val created = ServerGroup(id = nextGid, name = name, url = url)
            val nextGroups = _uiState.value.groups + created
            storage.saveGroups(nextGroups)
            _uiState.update { it.copy(groups = nextGroups) }
            created
        }
        val count = refreshSubscriptionGroup(group)
        showToast("${group.name}: $count servers loaded")
    }

    private suspend fun refreshSubscriptionGroup(group: ServerGroup): Int {
        val body = withContext(Dispatchers.IO) { fetchUrl(group.url) }
        val curProfiles = _uiState.value.profiles
        val kept = curProfiles.filter { it.groupId != group.id }
        val nextId = (curProfiles.maxOfOrNull { it.id } ?: 0L) + 1L
        val parsed = LinkParser.parseLinks(body, group.id, nextId)
        val merged = kept + parsed
        storage.saveProfiles(merged)
        _uiState.update { rebuildFiltered(it.copy(profiles = merged)) }
        return parsed.size
    }

    private fun fetchUrl(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", "ThroneUI-Android/1.0")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $code while fetching subscription")
        }
        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()
        return text
    }

    private fun deriveSubName(url: String): String {
        return try {
            val u = URL(url)
            val seg = u.path.trim('/').split('/').lastOrNull()?.removeSuffix(".txt").orEmpty()
            seg.ifBlank { u.host }.take(22)
        } catch (_: Throwable) {
            "Subscription"
        }
    }

    fun pingSingle(profile: ServerProfile) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            try {
                runScanInternal(listOf(profile))
                val updated = _uiState.value.profiles.firstOrNull { it.id == profile.id }
                val lat = updated?.latency
                if (lat != null && lat > 0) showToast("${profile.name}: $lat ms")
                else showToast("${profile.name}: Timeout")
            } catch (_: CancellationException) {
            }
        }
    }

    fun pingFiltered() {
        if (scanJob?.isActive == true) return
        val targets = _uiState.value.filteredProfiles
        if (targets.isEmpty()) {
            showToast("No servers to test")
            return
        }
        scanJob = viewModelScope.launch {
            try {
                runScanInternal(targets)
                val scan = _uiState.value.scan
                showToast("Scan complete: ${scan.onlineCount}/${scan.total} online")
            } catch (_: CancellationException) {
                showToast("Scan stopped")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(scan = ScanProgress()) }
    }

    private suspend fun runScanInternal(targets: List<ServerProfile>) {
        var lastSortAt = 0L
        scanner.scanProfiles(targets) { progress, delta ->
            val now = System.currentTimeMillis()
            _uiState.update { cur ->
                val updatedProfiles = if (delta.isEmpty()) {
                    cur.profiles
                } else {
                    cur.profiles.map { p ->
                        val lat = delta[p.id]
                        if (lat != null) p.copy(latency = lat, latencyAt = now) else p
                    }
                }
                val shouldResort = !progress.active || (now - lastSortAt > 450L)
                val nextState = cur.copy(profiles = updatedProfiles, scan = progress)
                if (shouldResort) {
                    lastSortAt = now
                    rebuildFiltered(nextState)
                } else {
                    nextState
                }
            }
        }
        storage.saveProfiles(_uiState.value.profiles)
    }

    fun deleteProfile(profileId: Long) {
        val activeId = runtimeState.value.activeProfileId
        val isRunning = runtimeState.value.state != VpnConnectionState.STOPPED
        if (isRunning && activeId == profileId) {
            disconnect()
        }
        val next = _uiState.value.profiles.filter { it.id != profileId }
        storage.saveProfiles(next)
        _uiState.update { rebuildFiltered(it.copy(profiles = next)) }
    }

    fun deleteGroup(groupId: Long) {
        if (groupId < 0L) return
        cancelScan()
        val activeId = runtimeState.value.activeProfileId
        val isRunning = runtimeState.value.state != VpnConnectionState.STOPPED
        if (isRunning && _uiState.value.profiles.any { it.id == activeId && it.groupId == groupId }) {
            disconnect()
        }

        val nextProfiles = _uiState.value.profiles.filter { it.groupId != groupId }
        storage.saveProfiles(nextProfiles)

        val nextGroups = if (groupId > 0L) {
            _uiState.value.groups.filter { it.id != groupId }
        } else {
            _uiState.value.groups
        }
        storage.saveGroups(nextGroups)

        _uiState.update {
            val nextSelGroup = if (it.selectedGroupId == groupId && groupId > 0L) -1L else it.selectedGroupId
            rebuildFiltered(
                it.copy(
                    groups = nextGroups,
                    profiles = nextProfiles,
                    selectedGroupId = nextSelGroup
                )
            )
        }
        showToast("Group removed")
    }

    fun removeDeadAndSlow(groupId: Long) {
        val threshold = _uiState.value.settings.maxLatencyMs
        val activeId = runtimeState.value.activeProfileId
        val isRunning = runtimeState.value.state != VpnConnectionState.STOPPED
        var removedCount = 0

        val next = _uiState.value.profiles.filter { p ->
            if (groupId != -1L && p.groupId != groupId) return@filter true
            val lat = p.latency ?: return@filter true
            if (lat == 0) return@filter true
            val bad = lat < 0 || lat > threshold
            if (!bad) return@filter true
            if (isRunning && p.id == activeId) return@filter true
            removedCount++
            false
        }

        if (removedCount == 0) {
            showToast("No dead or >${threshold}ms servers to remove")
            return
        }
        storage.saveProfiles(next)
        _uiState.update { rebuildFiltered(it.copy(profiles = next)) }
        showToast("Removed $removedCount servers (dead / >${threshold}ms)")
    }

    fun updateProxyPort(port: Int) {
        val clamped = port.coerceIn(1024, 65535)
        val next = _uiState.value.settings.copy(proxyPort = clamped)
        storage.saveSettings(next)
        _uiState.update { it.copy(settings = next) }
        showToast("Proxy endpoint: 127.0.0.1:$clamped")
    }

    fun updateMaxLatencyMs(ms: Int) {
        val next = _uiState.value.settings.copy(maxLatencyMs = ms.coerceIn(50, 2000))
        storage.saveSettings(next)
        _uiState.update { it.copy(settings = next) }
    }

    fun updateSplitTunnel(mode: SplitTunnelMode, packages: Set<String>) {
        val next = _uiState.value.settings.copy(splitMode = mode, splitPackages = packages)
        storage.saveSettings(next)
        _uiState.update { it.copy(settings = next) }
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty() || _appsLoading.value) return
        viewModelScope.launch {
            _appsLoading.value = true
            val list = withContext(Dispatchers.IO) {
                val pm = app.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                apps.filter { it.packageName != app.packageName }
                    .map { info ->
                        val label = runCatching { pm.getApplicationLabel(info).toString() }
                            .getOrDefault(info.packageName)
                        val isSys = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        AppInstalledItem(info.packageName, label, isSys)
                    }
                    .sortedWith(compareBy<AppInstalledItem> { it.isSystem }.thenBy { it.label.lowercase() })
            }
            _installedApps.value = list
            _appsLoading.value = false
        }
    }

    fun clearLogs() {
        ThroneEngine.clearLogs()
    }

    private fun rebuildFiltered(state: UiState): UiState {
        val q = state.searchQuery.trim().lowercase()
        val gid = state.selectedGroupId
        val list = state.profiles.filter { p ->
            if (gid != -1L && p.groupId != gid) return@filter false
            if (q.isNotEmpty()) {
                val match = p.name.lowercase().contains(q) ||
                    p.type.lowercase().contains(q) ||
                    p.server.lowercase().contains(q)
                if (!match) return@filter false
            }
            true
        }.sortedWith(
            compareBy<ServerProfile>(
                { p ->
                    val l = p.latency
                    when {
                        l != null && l > 0 -> 0
                        l == null || l == 0 -> 1
                        else -> 2
                    }
                },
                { p ->
                    val l = p.latency
                    if (l != null && l > 0) l else Int.MAX_VALUE
                },
                { it.id }
            )
        )
        return state.copy(filteredProfiles = list)
    }
}

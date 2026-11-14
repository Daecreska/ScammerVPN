package com.throne.ui.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.service.quicksettings.TileService
import com.throne.ui.MainActivity
import com.throne.ui.R
import com.throne.ui.data.ConnectionMode
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Connections
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VpnConnectionState {
    STOPPED,
    CONNECTING,
    CONNECTED
}

data class EngineRuntimeState(
    val state: VpnConnectionState = VpnConnectionState.STOPPED,
    val activeProfileId: Long? = null,
    val activeProfileName: String = "",
    val activeMode: ConnectionMode = ConnectionMode.VPN,
    val connectedSinceMs: Long = 0L,
    val downloadBps: Long = 0L,
    val uploadBps: Long = 0L,
    val coreVersion: String = safeCoreVersion(),
    val lastError: String? = null,
    val logs: List<String> = emptyList()
)

private fun safeCoreVersion(): String {
    return try {
        "sing-box ${Libbox.version()}"
    } catch (_: Throwable) {
        "sing-box 1.12.4"
    }
}

object ThroneEngine {
    private val _runtime = MutableStateFlow(EngineRuntimeState())
    val runtime: StateFlow<EngineRuntimeState> = _runtime.asStateFlow()

    @Volatile
    var activeVpnService: ThroneVpnService? = null

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun appendLog(msg: String) {
        val clean = msg.trim()
        if (clean.isEmpty()) return
        val stamp = synchronized(timeFmt) { timeFmt.format(Date()) }
        val lines = clean.lines().filter { it.isNotBlank() }.map { "[$stamp] $it" }
        if (lines.isEmpty()) return
        _runtime.update { cur ->
            cur.copy(logs = (cur.logs + lines).takeLast(180))
        }
    }

    fun clearLogs() {
        _runtime.update { it.copy(logs = emptyList()) }
    }

    fun updateState(block: (EngineRuntimeState) -> EngineRuntimeState) {
        _runtime.update(block)
    }

    fun start(
        context: Context,
        configJson: String,
        profileId: Long,
        profileName: String,
        mode: ConnectionMode
    ) {
        val intent = Intent(context, ThroneVpnService::class.java).apply {
            action = ThroneVpnService.ACTION_START
            putExtra(ThroneVpnService.EXTRA_CONFIG, configJson)
            putExtra(ThroneVpnService.EXTRA_PROFILE_ID, profileId)
            putExtra(ThroneVpnService.EXTRA_PROFILE_NAME, profileName)
            putExtra(ThroneVpnService.EXTRA_MODE, mode.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        val intent = Intent(context, ThroneVpnService::class.java).apply {
            action = ThroneVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}

class ThroneVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.throne.ui.action.START"
        const val ACTION_STOP = "com.throne.ui.action.STOP"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
        const val EXTRA_MODE = "extra_mode"

        private const val CHANNEL_ID = "throne_vpn_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serviceLock = Any()

    private var boxService: BoxService? = null
    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ThroneEngine.activeVpnService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                promoteForeground("Disconnecting...")
                scope.launch {
                    stopCore()
                    stopSelfCompat()
                }
            }
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
                val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty()
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: ConnectionMode.VPN.name
                val mode = runCatching { ConnectionMode.valueOf(modeName) }
                    .getOrDefault(ConnectionMode.VPN)

                promoteForeground("Connecting to $profileName...")
                ThroneEngine.updateState {
                    it.copy(
                        state = VpnConnectionState.CONNECTING,
                        activeProfileId = profileId,
                        activeProfileName = profileName,
                        activeMode = mode,
                        lastError = null
                    )
                }
                requestTileUpdate()

                scope.launch {
                    startCore(config, profileId, profileName, mode)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        scope.launch {
            stopCore()
            stopSelfCompat()
        }
    }

    override fun onDestroy() {
        ThroneEngine.activeVpnService = null
        stopCoreSync()
        super.onDestroy()
    }

    private fun startCore(
        configJson: String,
        profileId: Long,
        profileName: String,
        mode: ConnectionMode
    ) {
        synchronized(serviceLock) {
            stopCoreSync()
            try {
                acquireWakeLock()

                val srv = Libbox.newCommandServer(object : CommandServerHandler {
                    override fun serviceReload() {}
                    override fun postServiceClose() {
                        scope.launch {
                            stopCore()
                            stopSelfCompat()
                        }
                    }

                    override fun getSystemProxyStatus(): SystemProxyStatus {
                        return SystemProxyStatus().apply {
                            available = false
                            enabled = false
                        }
                    }

                    override fun setSystemProxyEnabled(isEnabled: Boolean) {}
                }, 150)

                srv.start()
                commandServer = srv

                val platform = PlatformInterfaceImpl(
                    context = applicationContext,
                    vpnServiceProvider = { if (mode == ConnectionMode.VPN) this else null },
                    onLog = { line ->
                        ThroneEngine.appendLog(line)
                        try {
                            commandServer?.writeMessage(line)
                        } catch (_: Throwable) {
                        }
                    }
                )

                val box = Libbox.newService(configJson, platform)
                box.start()
                boxService = box
                srv.setService(box)

                startStatusClient()

                ThroneEngine.appendLog("Started ${mode.name} -> $profileName")
                ThroneEngine.updateState {
                    it.copy(
                        state = VpnConnectionState.CONNECTED,
                        activeProfileId = profileId,
                        activeProfileName = profileName,
                        activeMode = mode,
                        connectedSinceMs = System.currentTimeMillis(),
                        lastError = null
                    )
                }
                promoteForeground("Connected · $profileName")
                requestTileUpdate()
            } catch (t: Throwable) {
                val msg = t.message ?: "Failed to start sing-box engine"
                ThroneEngine.appendLog("ERROR: $msg")
                stopCoreSync()
                ThroneEngine.updateState {
                    it.copy(
                        state = VpnConnectionState.STOPPED,
                        connectedSinceMs = 0L,
                        downloadBps = 0L,
                        uploadBps = 0L,
                        lastError = msg
                    )
                }
                requestTileUpdate()
                stopSelfCompat()
            }
        }
    }

    private fun startStatusClient() {
        val opts = CommandClientOptions().apply {
            command = Libbox.CommandStatus
            statusInterval = 1_000_000_000L
        }
        val client = Libbox.newCommandClient(object : CommandClientHandler {
            override fun connected() {}
            override fun disconnected(message: String?) {}
            override fun clearLogs() {}
            override fun writeLogs(messageList: StringIterator?) {}
            override fun writeGroups(message: OutboundGroupIterator?) {}
            override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) {}
            override fun updateClashMode(newMode: String?) {}
            override fun writeConnections(message: Connections?) {}

            override fun writeStatus(message: StatusMessage?) {
                if (message == null) return
                ThroneEngine.updateState {
                    it.copy(
                        downloadBps = message.downlink,
                        uploadBps = message.uplink
                    )
                }
            }
        }, opts)
        try {
            client.connect()
            commandClient = client
        } catch (_: Throwable) {
        }
    }

    private fun stopCore() {
        synchronized(serviceLock) {
            stopCoreSync()
            ThroneEngine.appendLog("Disconnected")
            ThroneEngine.updateState {
                it.copy(
                    state = VpnConnectionState.STOPPED,
                    connectedSinceMs = 0L,
                    downloadBps = 0L,
                    uploadBps = 0L
                )
            }
            requestTileUpdate()
        }
    }

    private fun stopCoreSync() {
        try { commandClient?.disconnect() } catch (_: Throwable) {}
        commandClient = null
        try { boxService?.close() } catch (_: Throwable) {}
        boxService = null
        try { commandServer?.close() } catch (_: Throwable) {}
        commandServer = null
        releaseWakeLock()
    }

    private fun stopSelfCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun promoteForeground(contentText: String) {
        val notif = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ThroneVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_tile_shield)
            .setContentTitle("Throne UI")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Disconnect",
                    disconnectIntent
                ).build()
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Throne VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ThroneUI::VpnWakeLock").apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    private fun requestTileUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    this,
                    ComponentName(this, ThroneTileService::class.java)
                )
            } catch (_: Throwable) {
            }
        }
    }
}

package com.throne.ui.data

enum class ConnectionMode {
    VPN,
    PROXY
}

enum class SplitTunnelMode {
    DISABLED,
    BYPASS_SELECTED,
    ONLY_SELECTED
}

data class BuiltinSource(
    val id: String,
    val name: String,
    val desc: String,
    val url: String
)

val BUILTIN_SOURCES = listOf(
    BuiltinSource(
        id = "igareck-mobile",
        name = "BLACK VLESS Mobile",
        desc = "TOP-150 mix (VLESS, SS, Hy2)",
        url = "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/BLACK_VLESS_RUS_mobile.txt"
    ),
    BuiltinSource(
        id = "igareck-full",
        name = "BLACK VLESS Full",
        desc = "Full public VLESS collection",
        url = "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/BLACK_VLESS_RUS.txt"
    ),
    BuiltinSource(
        id = "hidashimora-34",
        name = "Anti-RKN Mix (#34)",
        desc = "Filtered free configs list",
        url = "https://raw.githubusercontent.com/Hidashimora/free-vpn-anti-rkn/main/configs/34.txt"
    ),
    BuiltinSource(
        id = "pulseproxy-3",
        name = "PulseProxy (#3)",
        desc = "Auto-checked every 30 min",
        url = "https://raw.githubusercontent.com/pie-with-jam/PulseProxy/main/githubmirror/3.txt"
    )
)

data class ServerGroup(
    val id: Long,
    val name: String,
    val url: String = ""
)

data class ServerProfile(
    val id: Long,
    val groupId: Long,
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val outboundJson: String,
    val latency: Int? = null,
    val latencyAt: Long = 0L
)

data class AppSettings(
    val mode: ConnectionMode = ConnectionMode.VPN,
    val proxyPort: Int = 2080,
    val maxLatencyMs: Int = 120,
    val splitMode: SplitTunnelMode = SplitTunnelMode.DISABLED,
    val splitPackages: Set<String> = emptySet(),
    val selectedProfileId: Long? = null
)

data class ScanProgress(
    val active: Boolean = false,
    val single: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val onlineCount: Int = 0,
    val activeIds: Set<Long> = emptySet(),
    val targetName: String = ""
)

data class AppInstalledItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

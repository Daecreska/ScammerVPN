package com.throne.ui.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppStorage(context: Context) {

    private val stateFile = File(context.filesDir, "throne_ui_state.json")

    @Synchronized
    private fun readRoot(): JSONObject {
        if (!stateFile.exists()) return JSONObject()
        return try {
            JSONObject(stateFile.readText(Charsets.UTF_8))
        } catch (_: Throwable) {
            JSONObject()
        }
    }

    @Synchronized
    private fun writeRoot(root: JSONObject) {
        val tmp = File(stateFile.parentFile, "${stateFile.name}.tmp")
        tmp.writeText(root.toString(), Charsets.UTF_8)
        if (stateFile.exists()) stateFile.delete()
        tmp.renameTo(stateFile)
    }

    fun loadGroups(): List<ServerGroup> {
        val root = readRoot()
        val arr = root.optJSONArray("groups")
        val list = ArrayList<ServerGroup>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    ServerGroup(
                        id = o.optLong("id", 0L),
                        name = o.optString("name", "Default"),
                        url = o.optString("url", "")
                    )
                )
            }
        }
        if (list.none { it.id == 0L }) {
            list.add(0, ServerGroup(id = 0L, name = "Default", url = ""))
        }
        return list
    }

    fun saveGroups(groups: List<ServerGroup>) {
        val root = readRoot()
        val arr = JSONArray()
        for (g in groups) {
            arr.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("url", g.url)
            })
        }
        root.put("groups", arr)
        writeRoot(root)
    }

    fun loadProfiles(): List<ServerProfile> {
        val root = readRoot()
        val arr = root.optJSONArray("profiles") ?: return emptyList()
        val list = ArrayList<ServerProfile>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val latObj = o.opt("latency")
            val latency = if (latObj == null || latObj == JSONObject.NULL) null else (latObj as Number).toInt()
            list.add(
                ServerProfile(
                    id = o.optLong("id", 0L),
                    groupId = o.optLong("groupId", 0L),
                    name = o.optString("name", "Server"),
                    type = o.optString("type", "PROXY"),
                    server = o.optString("server", ""),
                    port = o.optInt("port", 443),
                    outboundJson = o.optString("outboundJson", "{}"),
                    latency = latency,
                    latencyAt = o.optLong("latencyAt", 0L)
                )
            )
        }
        return list
    }

    fun saveProfiles(profiles: List<ServerProfile>) {
        val root = readRoot()
        val arr = JSONArray()
        for (p in profiles) {
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("groupId", p.groupId)
                put("name", p.name)
                put("type", p.type)
                put("server", p.server)
                put("port", p.port)
                put("outboundJson", p.outboundJson)
                if (p.latency != null) put("latency", p.latency) else put("latency", JSONObject.NULL)
                put("latencyAt", p.latencyAt)
            })
        }
        root.put("profiles", arr)
        writeRoot(root)
    }

    fun loadSettings(): AppSettings {
        val root = readRoot()
        val o = root.optJSONObject("settings") ?: return AppSettings()
        val mode = runCatching {
            ConnectionMode.valueOf(o.optString("mode", ConnectionMode.VPN.name))
        }.getOrDefault(ConnectionMode.VPN)
        val splitMode = runCatching {
            SplitTunnelMode.valueOf(o.optString("splitMode", SplitTunnelMode.DISABLED.name))
        }.getOrDefault(SplitTunnelMode.DISABLED)

        val pkgArr = o.optJSONArray("splitPackages")
        val pkgs = LinkedHashSet<String>()
        if (pkgArr != null) {
            for (i in 0 until pkgArr.length()) {
                val p = pkgArr.optString(i)
                if (p.isNotBlank()) pkgs.add(p)
            }
        }

        val selObj = o.opt("selectedProfileId")
        val selId = if (selObj == null || selObj == JSONObject.NULL) null else (selObj as Number).toLong()

        return AppSettings(
            mode = mode,
            proxyPort = o.optInt("proxyPort", 2080).coerceIn(1024, 65535),
            maxLatencyMs = o.optInt("maxLatencyMs", 120).coerceIn(50, 2000),
            splitMode = splitMode,
            splitPackages = pkgs,
            selectedProfileId = selId
        )
    }

    fun saveSettings(settings: AppSettings) {
        val root = readRoot()
        root.put("settings", JSONObject().apply {
            put("mode", settings.mode.name)
            put("proxyPort", settings.proxyPort)
            put("maxLatencyMs", settings.maxLatencyMs)
            put("splitMode", settings.splitMode.name)
            put("splitPackages", JSONArray(settings.splitPackages.toList()))
            if (settings.selectedProfileId != null) {
                put("selectedProfileId", settings.selectedProfileId)
            } else {
                put("selectedProfileId", JSONObject.NULL)
            }
        })
        writeRoot(root)
    }
}

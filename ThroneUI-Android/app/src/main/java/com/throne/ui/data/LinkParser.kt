package com.throne.ui.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object LinkParser {

    fun decodeSubscriptionBody(rawBody: String): List<String> {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return emptyList()

        val hasScheme = trimmed.contains("://")
        val text = if (!hasScheme) {
            tryDecodeBase64(trimmed) ?: trimmed
        } else {
            trimmed
        }

        return text.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.startsWith("vless://", ignoreCase = true) ||
                    line.startsWith("ss://", ignoreCase = true) ||
                    line.startsWith("hy2://", ignoreCase = true) ||
                    line.startsWith("hysteria2://", ignoreCase = true) ||
                    line.startsWith("trojan://", ignoreCase = true) ||
                    line.startsWith("vmess://", ignoreCase = true)
            }
            .toList()
    }

    fun parseLinks(rawText: String, groupId: Long, startId: Long): List<ServerProfile> {
        val lines = decodeSubscriptionBody(rawText)
        val result = ArrayList<ServerProfile>(lines.size)
        var nextId = startId
        for (line in lines) {
            val parsed = parseSingleLink(line, groupId, nextId)
            if (parsed != null) {
                result.add(parsed)
                nextId++
            }
        }
        return result
    }

    fun parseSingleLink(rawLink: String, groupId: Long, id: Long): ServerProfile? {
        val link = rawLink.trim()
        return try {
            when {
                link.startsWith("vless://", true) -> parseVless(link, groupId, id)
                link.startsWith("trojan://", true) -> parseTrojan(link, groupId, id)
                link.startsWith("hy2://", true) || link.startsWith("hysteria2://", true) ->
                    parseHysteria2(link, groupId, id)
                link.startsWith("ss://", true) -> parseShadowsocks(link, groupId, id)
                link.startsWith("vmess://", true) -> parseVmess(link, groupId, id)
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseVless(link: String, groupId: Long, id: Long): ServerProfile? {
        val uri = URI(link)
        val uuid = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQuery(uri.rawQuery)
        val name = decodeFragment(uri.rawFragment).ifBlank { "$host:$port" }

        val type = params["type"]?.lowercase().orEmpty().ifBlank { "tcp" }
        // sing-box v1.12 does not natively support XHTTP transport in upstream libbox
        if (type == "xhttp" || type == "splithttp") return null

        val outbound = JSONObject().apply {
            put("type", "vless")
            put("tag", "proxy")
            put("server", host)
            put("server_port", port)
            put("uuid", uuid)
            val flow = params["flow"].orEmpty()
            if (flow.isNotBlank()) put("flow", flow)
            val packetEnc = params["packetEncoding"].orEmpty().ifBlank { "xudp" }
            put("packet_encoding", packetEnc)

            buildTlsJson(params, defaultSni = host)?.let { put("tls", it) }
            buildTransportJson(params, type)?.let { put("transport", it) }
        }

        return ServerProfile(
            id = id,
            groupId = groupId,
            name = name,
            type = "VLESS",
            server = host,
            port = port,
            outboundJson = outbound.toString()
        )
    }

    private fun parseTrojan(link: String, groupId: Long, id: Long): ServerProfile? {
        val uri = URI(link)
        val password = uri.userInfo?.let { safeUrlDecode(it) } ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQuery(uri.rawQuery)
        val name = decodeFragment(uri.rawFragment).ifBlank { "$host:$port" }
        val type = params["type"]?.lowercase().orEmpty().ifBlank { "tcp" }

        val outbound = JSONObject().apply {
            put("type", "trojan")
            put("tag", "proxy")
            put("server", host)
            put("server_port", port)
            put("password", password)

            val tlsParams = params.toMutableMap()
            if (!tlsParams.containsKey("security")) tlsParams["security"] = "tls"
            buildTlsJson(tlsParams, defaultSni = host)?.let { put("tls", it) }
            buildTransportJson(params, type)?.let { put("transport", it) }
        }

        return ServerProfile(
            id = id,
            groupId = groupId,
            name = name,
            type = "TROJAN",
            server = host,
            port = port,
            outboundJson = outbound.toString()
        )
    }

    private fun parseHysteria2(link: String, groupId: Long, id: Long): ServerProfile? {
        val normalized = if (link.startsWith("hy2://", true)) {
            "hysteria2://" + link.substring(6)
        } else link

        val uri = URI(normalized)
        val password = uri.userInfo?.let { safeUrlDecode(it) } ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQuery(uri.rawQuery)
        val name = decodeFragment(uri.rawFragment).ifBlank { "$host:$port" }

        val outbound = JSONObject().apply {
            put("type", "hysteria2")
            put("tag", "proxy")
            put("server", host)
            put("server_port", port)
            put("password", password)

            val obfsType = params["obfs"].orEmpty()
            val obfsPass = params["obfs-password"].orEmpty()
            if (obfsType == "salamander" && obfsPass.isNotBlank()) {
                put("obfs", JSONObject().apply {
                    put("type", "salamander")
                    put("password", obfsPass)
                })
            }

            val sni = params["sni"].orEmpty().ifBlank { host }
            val insecure = params["insecure"] == "1" || params["insecure"].equals("true", true)
            put("tls", JSONObject().apply {
                put("enabled", true)
                put("server_name", sni)
                put("insecure", insecure)
                val alpn = params["alpn"]
                if (!alpn.isNullOrBlank()) {
                    put("alpn", JSONArray(alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                }
            })
        }

        return ServerProfile(
            id = id,
            groupId = groupId,
            name = name,
            type = "HY2",
            server = host,
            port = port,
            outboundJson = outbound.toString()
        )
    }

    private fun parseShadowsocks(link: String, groupId: Long, id: Long): ServerProfile? {
        val withoutScheme = link.substring(5)
        val hashIdx = withoutScheme.indexOf('#')
        val name = if (hashIdx >= 0) decodeFragment(withoutScheme.substring(hashIdx + 1)) else ""
        val mainPart = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme

        val queryIdx = mainPart.indexOf('?')
        val corePart = if (queryIdx >= 0) mainPart.substring(0, queryIdx) else mainPart

        val method: String
        val password: String
        val host: String
        val port: Int

        if (corePart.contains('@')) {
            val atIdx = corePart.lastIndexOf('@')
            val userInfoRaw = corePart.substring(0, atIdx)
            val hostPort = corePart.substring(atIdx + 1)
            val decodedUser = if (userInfoRaw.contains(':')) {
                safeUrlDecode(userInfoRaw)
            } else {
                tryDecodeBase64(userInfoRaw) ?: safeUrlDecode(userInfoRaw)
            }
            val colonIdx = decodedUser.indexOf(':')
            if (colonIdx <= 0) return null
            method = decodedUser.substring(0, colonIdx)
            password = decodedUser.substring(colonIdx + 1)

            val hp = splitHostPort(hostPort) ?: return null
            host = hp.first
            port = hp.second
        } else {
            val decoded = tryDecodeBase64(corePart) ?: return null
            val atIdx = decoded.lastIndexOf('@')
            if (atIdx <= 0) return null
            val creds = decoded.substring(0, atIdx)
            val hostPort = decoded.substring(atIdx + 1)
            val colonIdx = creds.indexOf(':')
            if (colonIdx <= 0) return null
            method = creds.substring(0, colonIdx)
            password = creds.substring(colonIdx + 1)
            val hp = splitHostPort(hostPort) ?: return null
            host = hp.first
            port = hp.second
        }

        val outbound = JSONObject().apply {
            put("type", "shadowsocks")
            put("tag", "proxy")
            put("server", host)
            put("server_port", port)
            put("method", method)
            put("password", password)
        }

        return ServerProfile(
            id = id,
            groupId = groupId,
            name = name.ifBlank { "$host:$port" },
            type = "SS",
            server = host,
            port = port,
            outboundJson = outbound.toString()
        )
    }

    private fun parseVmess(link: String, groupId: Long, id: Long): ServerProfile? {
        val payload = link.substring(8).trim()
        val jsonStr = tryDecodeBase64(payload) ?: return null
        val obj = JSONObject(jsonStr)
        val host = obj.optString("add").trim()
        val port = obj.opt("port")?.toString()?.toIntOrNull() ?: return null
        val uuid = obj.optString("id").trim()
        if (host.isBlank() || uuid.isBlank()) return null

        val name = obj.optString("ps").ifBlank { "$host:$port" }
        val net = obj.optString("net", "tcp").lowercase()
        val tlsEnabled = obj.optString("tls").equals("tls", true)
        val sni = obj.optString("sni").ifBlank { obj.optString("host").ifBlank { host } }

        val outbound = JSONObject().apply {
            put("type", "vmess")
            put("tag", "proxy")
            put("server", host)
            put("server_port", port)
            put("uuid", uuid)
            put("alter_id", obj.optString("aid", "0").toIntOrNull() ?: 0)
            put("security", obj.optString("scy", "auto").ifBlank { "auto" })

            if (tlsEnabled) {
                put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", sni)
                    val fp = obj.optString("fp")
                    if (fp.isNotBlank()) {
                        put("utls", JSONObject().apply {
                            put("enabled", true)
                            put("fingerprint", fp)
                        })
                    }
                })
            }

            val params = mapOf(
                "host" to obj.optString("host"),
                "path" to obj.optString("path"),
                "serviceName" to obj.optString("path"),
                "headerType" to obj.optString("type")
            )
            buildTransportJson(params, net)?.let { put("transport", it) }
        }

        return ServerProfile(
            id = id,
            groupId = groupId,
            name = name,
            type = "VMESS",
            server = host,
            port = port,
            outboundJson = outbound.toString()
        )
    }

    private fun buildTlsJson(params: Map<String, String>, defaultSni: String): JSONObject? {
        val sec = params["security"]?.lowercase().orEmpty()
        if (sec != "tls" && sec != "reality") return null

        val sni = params["sni"].orEmpty().ifBlank {
            params["peer"].orEmpty().ifBlank { defaultSni }
        }
        val insecure = params["allowInsecure"] == "1" ||
            params["insecure"] == "1" ||
            params["insecure"].equals("true", true)

        return JSONObject().apply {
            put("enabled", true)
            if (sni.isNotBlank()) put("server_name", sni)
            put("insecure", insecure)

            val alpn = params["alpn"].orEmpty()
            if (alpn.isNotBlank()) {
                val list = alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (list.isNotEmpty()) put("alpn", JSONArray(list))
            }

            val fp = params["fp"].orEmpty().ifBlank { if (sec == "reality") "chrome" else "" }
            if (fp.isNotBlank()) {
                put("utls", JSONObject().apply {
                    put("enabled", true)
                    put("fingerprint", fp)
                })
            }

            if (sec == "reality") {
                val pbk = params["pbk"].orEmpty()
                if (pbk.isNotBlank()) {
                    put("reality", JSONObject().apply {
                        put("enabled", true)
                        put("public_key", pbk)
                        put("short_id", params["sid"].orEmpty())
                    })
                }
            }
        }
    }

    private fun buildTransportJson(params: Map<String, String>, type: String): JSONObject? {
        return when (type) {
            "ws" -> JSONObject().apply {
                put("type", "ws")
                val path = params["path"].orEmpty().ifBlank { "/" }
                put("path", path)
                val host = params["host"].orEmpty()
                if (host.isNotBlank()) {
                    put("headers", JSONObject().put("Host", host))
                }
            }
            "grpc" -> JSONObject().apply {
                put("type", "grpc")
                val svc = params["serviceName"].orEmpty()
                if (svc.isNotBlank()) put("service_name", svc)
            }
            "httpupgrade" -> JSONObject().apply {
                put("type", "httpupgrade")
                val path = params["path"].orEmpty().ifBlank { "/" }
                put("path", path)
                val host = params["host"].orEmpty()
                if (host.isNotBlank()) put("host", host)
            }
            "http", "h2" -> JSONObject().apply {
                put("type", "http")
                val path = params["path"].orEmpty().ifBlank { "/" }
                put("path", path)
                val host = params["host"].orEmpty()
                if (host.isNotBlank()) {
                    put("host", JSONArray(host.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                }
            }
            "tcp" -> {
                if (params["headerType"].equals("http", true)) {
                    JSONObject().apply {
                        put("type", "http")
                        val path = params["path"].orEmpty().ifBlank { "/" }
                        put("path", path)
                        val host = params["host"].orEmpty()
                        if (host.isNotBlank()) {
                            put("host", JSONArray(host.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                        }
                    }
                } else null
            }
            else -> null
        }
    }

    private fun splitHostPort(hostPort: String): Pair<String, Int>? {
        val clean = hostPort.trim()
        return if (clean.startsWith("[")) {
            val end = clean.indexOf(']')
            if (end <= 1) return null
            val host = clean.substring(1, end)
            val port = clean.substring(end + 1).removePrefix(":").toIntOrNull() ?: return null
            host to port
        } else {
            val idx = clean.lastIndexOf(':')
            if (idx <= 0) return null
            val host = clean.substring(0, idx)
            val port = clean.substring(idx + 1).toIntOrNull() ?: return null
            host to port
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) safeUrlDecode(pair.substring(0, idx)) else safeUrlDecode(pair)
            val value = if (idx >= 0) safeUrlDecode(pair.substring(idx + 1)) else ""
            map[key] = value
        }
        return map
    }

    private fun decodeFragment(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return safeUrlDecode(raw).trim()
    }

    private fun safeUrlDecode(value: String): String {
        return try {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: Throwable) {
            value
        }
    }

    private fun tryDecodeBase64(input: String): String? {
        val clean = input.replace("\\s".toRegex(), "")
        if (clean.isEmpty()) return null
        val flags = if (clean.contains('-') || clean.contains('_')) {
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        } else {
            Base64.DEFAULT
        }
        return try {
            String(Base64.decode(clean, flags), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            try {
                String(Base64.decode(clean, Base64.URL_SAFE), StandardCharsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

package com.throne.ui.data

import org.json.JSONArray
import org.json.JSONObject

object ConfigBuilder {

    fun buildRuntimeConfig(
        profile: ServerProfile,
        settings: AppSettings,
        appPackageName: String
    ): String {
        val outbound = JSONObject(profile.outboundJson).apply {
            put("tag", "proxy")
        }

        val inbounds = JSONArray()
        if (settings.mode == ConnectionMode.VPN) {
            inbounds.put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("address", JSONArray(listOf("172.19.0.1/30")))
                put("mtu", 1350)
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "gvisor")

                when (settings.splitMode) {
                    SplitTunnelMode.BYPASS_SELECTED -> {
                        val excluded = (settings.splitPackages + appPackageName).toList()
                        put("exclude_package", JSONArray(excluded))
                    }
                    SplitTunnelMode.ONLY_SELECTED -> {
                        val included = settings.splitPackages.filter { it != appPackageName }
                        if (included.isNotEmpty()) {
                            put("include_package", JSONArray(included))
                        } else {
                            put("exclude_package", JSONArray(listOf(appPackageName)))
                        }
                    }
                    SplitTunnelMode.DISABLED -> {
                        put("exclude_package", JSONArray(listOf(appPackageName)))
                    }
                }
            })
        }

        inbounds.put(JSONObject().apply {
            put("type", "mixed")
            put("tag", "mixed-in")
            put("listen", "127.0.0.1")
            put("listen_port", settings.proxyPort.coerceIn(1024, 65535))
        })

        val config = JSONObject().apply {
            put("log", JSONObject().apply {
                put("level", "info")
                put("timestamp", true)
            })
            put("dns", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("tag", "dns-remote")
                        put("type", "udp")
                        put("server", "1.1.1.1")
                        put("detour", "proxy")
                    })
                    put(JSONObject().apply {
                        put("tag", "dns-direct")
                        put("type", "udp")
                        put("server", "8.8.8.8")
                    })
                })
                put("final", "dns-remote")
            })
            put("inbounds", inbounds)
            put("outbounds", JSONArray().apply {
                put(outbound)
                put(JSONObject().apply {
                    put("type", "direct")
                    put("tag", "direct")
                })
            })
            put("route", JSONObject().apply {
                put("auto_detect_interface", true)
                put("default_domain_resolver", "dns-direct")
                put("rules", JSONArray().apply {
                    put(JSONObject().put("action", "sniff"))
                    put(JSONObject().apply {
                        put("protocol", "dns")
                        put("action", "hijack-dns")
                    })
                    put(JSONObject().apply {
                        put("ip_is_private", true)
                        put("outbound", "direct")
                    })
                })
                put("final", "proxy")
            })
        }

        return config.toString()
    }

    fun buildLatencyTestConfig(
        profiles: List<ServerProfile>,
        clashApiPort: Int
    ): Pair<String, Map<Long, String>> {
        val tagMap = LinkedHashMap<Long, String>(profiles.size)
        val outbounds = JSONArray()

        for (p in profiles) {
            val tag = "n_${p.id}"
            tagMap[p.id] = tag
            outbounds.put(JSONObject(p.outboundJson).apply {
                put("tag", tag)
            })
        }
        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        })

        val config = JSONObject().apply {
            put("log", JSONObject().put("level", "error"))
            put("dns", JSONObject().apply {
                put("servers", JSONArray().put(JSONObject().apply {
                    put("tag", "dns-direct")
                    put("type", "udp")
                    put("server", "8.8.8.8")
                }))
                put("final", "dns-direct")
            })
            put("inbounds", JSONArray())
            put("outbounds", outbounds)
            put("route", JSONObject().apply {
                put("auto_detect_interface", true)
                put("default_domain_resolver", "dns-direct")
                put("final", "direct")
            })
            put("experimental", JSONObject().apply {
                put("clash_api", JSONObject().apply {
                    put("external_controller", "127.0.0.1:$clashApiPort")
                })
            })
        }

        return config.toString() to tagMap
    }

    fun buildSingleCheckConfig(profile: ServerProfile): String {
        return JSONObject().apply {
            put("log", JSONObject().put("level", "error"))
            put("dns", JSONObject().apply {
                put("servers", JSONArray().put(JSONObject().apply {
                    put("tag", "dns-direct")
                    put("type", "udp")
                    put("server", "8.8.8.8")
                }))
                put("final", "dns-direct")
            })
            put("inbounds", JSONArray())
            put("outbounds", JSONArray().apply {
                put(JSONObject(profile.outboundJson).apply { put("tag", "proxy") })
                put(JSONObject().apply {
                    put("type", "direct")
                    put("tag", "direct")
                })
            })
            put("route", JSONObject().apply {
                put("default_domain_resolver", "dns-direct")
                put("final", "proxy")
            })
        }.toString()
    }
}

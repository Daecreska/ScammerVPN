package com.throne.ui.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.system.OsConstants
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections

class PlatformInterfaceImpl(
    private val context: Context,
    private val vpnServiceProvider: () -> VpnService?,
    private val onLog: (String) -> Unit
) : PlatformInterface {

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        val svc = vpnServiceProvider() ?: return
        svc.protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        val svc = vpnServiceProvider()
            ?: throw IllegalStateException("VpnService is not active")

        val builder = svc.Builder()
            .setSession("Throne UI")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder.setUnderlyingNetworks(null)
        }

        val mtu = options.mtu
        if (mtu > 0) {
            builder.setMtu(mtu)
        }

        val v4Addrs = options.inet4Address
        while (v4Addrs.hasNext()) {
            val prefix = v4Addrs.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }

        val v6Addrs = options.inet6Address
        while (v6Addrs.hasNext()) {
            val prefix = v6Addrs.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }

        try {
            val dns = options.dnsServerAddress?.value
            if (!dns.isNullOrBlank()) {
                builder.addDnsServer(dns)
            }
        } catch (_: Throwable) {
            builder.addDnsServer("172.19.0.2")
        }

        var hasV4Route = false
        val v4Routes = options.inet4RouteAddress
        while (v4Routes.hasNext()) {
            val p = v4Routes.next()
            builder.addRoute(p.address(), p.prefix())
            hasV4Route = true
        }
        if (!hasV4Route) {
            val v4Ranges = options.inet4RouteRange
            while (v4Ranges.hasNext()) {
                val p = v4Ranges.next()
                builder.addRoute(p.address(), p.prefix())
                hasV4Route = true
            }
        }
        if (!hasV4Route && options.autoRoute) {
            builder.addRoute("0.0.0.0", 0)
        }

        val v6Routes = options.inet6RouteAddress
        while (v6Routes.hasNext()) {
            val p = v6Routes.next()
            builder.addRoute(p.address(), p.prefix())
        }

        val includePackages = options.includePackage
        while (includePackages.hasNext()) {
            val pkg = includePackages.next()
            try {
                builder.addAllowedApplication(pkg)
            } catch (_: Throwable) {
            }
        }

        val excludePackages = options.excludePackage
        while (excludePackages.hasNext()) {
            val pkg = excludePackages.next()
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Throwable) {
            }
        }

        val pfd = builder.establish()
            ?: throw IllegalStateException("Failed to establish Android VPN TUN interface")

        return pfd.detachFd()
    }

    override fun writeLog(message: String?) {
        if (!message.isNullOrBlank()) {
            onLog(message)
        }
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !sourceAddress.isNullOrBlank() &&
            !destinationAddress.isNullOrBlank()
        ) {
            return try {
                connectivity.getConnectionOwnerUid(
                    ipProtocol,
                    InetSocketAddress(InetAddress.getByName(sourceAddress), sourcePort),
                    InetSocketAddress(InetAddress.getByName(destinationAddress), destinationPort)
                )
            } catch (_: Throwable) {
                -1
            }
        }
        return -1
    }

    override fun packageNameByUid(uid: Int): String {
        return try {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    override fun uidByPackageName(packageName: String?): Int {
        if (packageName.isNullOrBlank()) return -1
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (_: Throwable) {
            -1
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        if (listener == null) return
        notifyActiveNetwork(connectivity.activeNetwork, listener)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyActiveNetwork(network, listener)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                notifyActiveNetwork(network, listener)
            }

            override fun onLost(network: Network) {
                val active = connectivity.activeNetwork
                if (active == null) {
                    listener.updateDefaultInterface("", -1, false, false)
                } else {
                    notifyActiveNetwork(active, listener)
                }
            }
        }
        networkCallback = cb
        try {
            connectivity.registerNetworkCallback(request, cb)
        } catch (_: Throwable) {
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        networkCallback?.let {
            try {
                connectivity.unregisterNetworkCallback(it)
            } catch (_: Throwable) {
            }
        }
        networkCallback = null
    }

    private fun notifyActiveNetwork(network: Network?, listener: InterfaceUpdateListener) {
        if (network == null) {
            listener.updateDefaultInterface("", -1, false, false)
            return
        }
        try {
            val caps = connectivity.getNetworkCapabilities(network)
            val linkProps = connectivity.getLinkProperties(network)
            val ifName = linkProps?.interfaceName ?: ""
            val ifIndex = if (ifName.isNotBlank()) {
                java.net.NetworkInterface.getByName(ifName)?.index ?: -1
            } else -1
            val isExpensive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
            listener.updateDefaultInterface(ifName, ifIndex, isExpensive, false)
        } catch (_: Throwable) {
            listener.updateDefaultInterface("", -1, false, false)
        }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val list = ArrayList<NetworkInterface>()
        try {
            val ifaces = Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (ni in ifaces) {
                val name = ni.name ?: continue
                if (name.startsWith("tun") || name.startsWith("ppp") || ni.isLoopback) continue

                val addrs = ni.interfaceAddresses.mapNotNull { ia ->
                    val host = ia.address?.hostAddress?.substringBefore('%') ?: return@mapNotNull null
                    "$host/${ia.networkPrefixLength}"
                }
                if (addrs.isEmpty()) continue

                var flags = 0
                if (ni.isUp) flags = flags or OsConstants.IFF_UP
                if (ni.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST

                val type = when {
                    name.startsWith("wlan") || name.startsWith("wifi") -> Libbox.InterfaceTypeWIFI
                    name.startsWith("rmnet") || name.startsWith("ccmni") -> Libbox.InterfaceTypeCellular
                    name.startsWith("eth") -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }

                list.add(NetworkInterface().apply {
                    setIndex(ni.index)
                    setMTU(ni.mtu)
                    setName(name)
                    setAddresses(ListStringIterator(addrs))
                    setFlags(flags)
                    setType(type)
                    setDNSServer(ListStringIterator(emptyList()))
                    setMetered(type == Libbox.InterfaceTypeCellular)
                })
            }
        } catch (_: Throwable) {
        }
        return NetInterfaceIterator(list)
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? = null

    override fun systemCertificates(): StringIterator = ListStringIterator(emptyList())

    override fun clearDNSCache() {}

    override fun sendNotification(notification: Notification?) {}

    private class ListStringIterator(private val items: List<String>) : StringIterator {
        private var idx = 0
        override fun len(): Int = items.size
        override fun hasNext(): Boolean = idx < items.size
        override fun next(): String = items[idx++]
    }

    private class NetInterfaceIterator(private val items: List<NetworkInterface>) : NetworkInterfaceIterator {
        private var idx = 0
        override fun hasNext(): Boolean = idx < items.size
        override fun next(): NetworkInterface = items[idx++]
    }
}

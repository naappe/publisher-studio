package com.example.lanlens

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque
import java.util.LinkedHashSet

class MdnsDiscovery(private val context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val seenServices = linkedSetOf<String>()
    private val discoveredTypes = LinkedHashSet<String>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var stopped = false
    private var callback: ((ip: String, serviceName: String, serviceType: String, port: Int) -> Unit)? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val curatedTypes = listOf(
        "_http._tcp.",
        "_https._tcp.",
        "_ipp._tcp.",
        "_printer._tcp.",
        "_googlecast._tcp.",
        "_airplay._tcp.",
        "_raop._tcp.",
        "_workstation._tcp.",
        "_smb._tcp.",
        "_ssh._tcp.",
        "_hap._tcp.",
        "_companion-link._tcp.",
        "_matter._tcp.",
        "_spotify-connect._tcp."
    )

    fun start(onService: (ip: String, serviceName: String, serviceType: String, port: Int) -> Unit) {
        stopped = false
        callback = onService
        seenServices.clear()
        discoveredTypes.clear()
        resolveQueue.clear()
        resolving = false

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            multicastLock = wifi.createMulticastLock("lanlens-mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) { }

        // First ask DNS-SD which service types actually exist on this LAN.
        // This avoids blindly opening many simultaneous discovery sessions,
        // which can hit Android NSD's outstanding-request limit.
        startMetaDiscovery()
    }

    private fun startMetaDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                metaTypeFrom(service)?.let { discoveredTypes += it }
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) { }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        listeners += listener
        try {
            nsd.discoverServices("_services._dns-sd._udp.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) { }

        handler.postDelayed({
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) { }
            listeners.remove(listener)

            val queue = LinkedHashSet<String>()
            // Prefer types the network itself advertised, then add common fallbacks.
            queue += discoveredTypes.take(16)
            curatedTypes.forEach { if (queue.size < 20) queue += it }
            browseTypesSequentially(ArrayDeque(queue.toList()))
        }, 1800)
    }

    private fun browseTypesSequentially(types: ArrayDeque<String>) {
        if (stopped || types.isEmpty()) return
        val type = types.removeFirst()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                enqueueResolve(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) { }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        listeners += listener
        try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) { }

        handler.postDelayed({
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) { }
            listeners.remove(listener)
            browseTypesSequentially(types)
        }, 850)
    }

    @Synchronized
    private fun enqueueResolve(service: NsdServiceInfo) {
        if (stopped) return
        val key = "${service.serviceName}|${service.serviceType}"
        if (!seenServices.add(key)) return
        resolveQueue.addLast(service)
        if (!resolving) resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        if (stopped || resolving || resolveQueue.isEmpty()) return
        val service = resolveQueue.removeFirst()
        resolving = true

        handler.post {
            try {
                @Suppress("DEPRECATION")
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolutionFinished()
                    }

                    @Suppress("DEPRECATION")
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val ip = info.host?.hostAddress
                        if (!ip.isNullOrBlank()) {
                            callback?.invoke(ip, info.serviceName, info.serviceType, info.port)
                        }
                        resolutionFinished()
                    }
                })
            } catch (_: Exception) {
                resolutionFinished()
            }
        }
    }

    @Synchronized
    private fun resolutionFinished() {
        resolving = false
        handler.postDelayed({ resolveNext() }, 35)
    }

    private fun metaTypeFrom(service: NsdServiceInfo): String? {
        val name = service.serviceName.trim().trimEnd('.')
        val rawType = service.serviceType.trim().trimEnd('.')
        val protocol = when {
            rawType.startsWith("_tcp") -> "_tcp"
            rawType.startsWith("_udp") -> "_udp"
            rawType.contains("._tcp") -> "_tcp"
            rawType.contains("._udp") -> "_udp"
            else -> null
        } ?: return null
        if (!name.startsWith("_")) return null
        return "$name.$protocol."
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        listeners.toList().forEach { listener ->
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) { }
        }
        listeners.clear()
        synchronized(this) {
            resolveQueue.clear()
            resolving = false
        }
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) { }
        multicastLock = null
    }
}

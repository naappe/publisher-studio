package com.example.lanlens

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class MdnsDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val types = listOf(
        "_http._tcp.",
        "_https._tcp.",
        "_ipp._tcp.",
        "_printer._tcp.",
        "_pdl-datastream._tcp.",
        "_googlecast._tcp.",
        "_airplay._tcp.",
        "_raop._tcp.",
        "_workstation._tcp.",
        "_device-info._tcp.",
        "_ssh._tcp.",
        "_smb._tcp.",
        "_hap._tcp."
    )

    fun start(onService: (ip: String, serviceName: String, serviceType: String, port: Int) -> Unit) {
        types.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit

                override fun onServiceFound(service: NsdServiceInfo) {
                    try {
                        @Suppress("DEPRECATION")
                        nsd.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                            @Suppress("DEPRECATION")
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val ip = info.host?.hostAddress ?: return
                                onService(ip, info.serviceName, info.serviceType, info.port)
                            }
                        })
                    } catch (_: Exception) { }
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
        }
    }

    fun stop() {
        listeners.forEach { listener ->
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) { }
        }
        listeners.clear()
    }
}

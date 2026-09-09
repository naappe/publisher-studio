package com.example.lanlens

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import java.net.Inet4Address

data class LocalNetwork(val ip: String, val prefixLength: Int, val gateway: String?) {
    fun scanTargets(): List<String> {
        // MVP safety/performance cap: inspect only the local /24 around the phone.
        val parts = ip.split(".")
        if (parts.size != 4) return emptyList()
        val base = parts.take(3).joinToString(".")
        return (1..254).map { "$base.$it" }.filter { it != ip }
    }
}

object NetworkInfo {
    fun current(context: Context): LocalNetwork? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null

        val lp = cm.getLinkProperties(network) ?: return null
        val address: LinkAddress = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val gateway = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        return LocalNetwork(address.address.hostAddress ?: return null, address.prefixLength, gateway)
    }
}

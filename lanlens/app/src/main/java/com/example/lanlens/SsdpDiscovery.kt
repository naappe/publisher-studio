package com.example.lanlens

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.HttpURLConnection
import java.net.URL

class SsdpDiscovery(private val context: Context) {
    fun discover(onResponse: (ip: String, headers: Map<String, String>) -> Unit) {
        Thread {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("lanlens-ssdp").apply {
                setReferenceCounted(false)
                acquire()
            }
            try {
                MulticastSocket().use { socket ->
                    socket.soTimeout = 800
                    val target = InetAddress.getByName("239.255.255.250")
                    val message = ("M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\n" +
                        "ST: ssdp:all\r\n\r\n").toByteArray()

                    // A second search helps with devices that miss the first multicast burst.
                    repeat(2) {
                        socket.send(DatagramPacket(message, message.size, target, 1900))
                        Thread.sleep(120)
                    }

                    val seenLocations = linkedSetOf<String>()
                    val end = System.currentTimeMillis() + 4200
                    while (System.currentTimeMillis() < end) {
                        try {
                            val buf = ByteArray(8192)
                            val packet = DatagramPacket(buf, buf.size)
                            socket.receive(packet)
                            val text = String(packet.data, 0, packet.length)
                            val headers = linkedMapOf<String, String>()
                            text.lineSequence().drop(1).forEach { line ->
                                val i = line.indexOf(':')
                                if (i > 0) headers[line.substring(0, i).trim().uppercase()] = line.substring(i + 1).trim()
                            }
                            val ip = packet.address.hostAddress ?: continue
                            onResponse(ip, headers)

                            val location = headers["LOCATION"]
                            if (!location.isNullOrBlank() && seenLocations.add(location)) {
                                Thread {
                                    val extra = fetchDescription(location)
                                    if (extra.isNotEmpty()) onResponse(ip, headers + extra)
                                }.start()
                            }
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (lock.isHeld) lock.release()
            }
        }.start()
    }

    private fun fetchDescription(location: String): Map<String, String> {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(location).openConnection() as HttpURLConnection
            connection.connectTimeout = 700
            connection.readTimeout = 900
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "LAN-Lens/0.3")
            val body = connection.inputStream.bufferedReader().use { it.readText().take(131072) }

            fun tag(name: String): String? {
                val regex = Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                return regex.find(body)?.groupValues?.getOrNull(1)?.replace(Regex("<[^>]+>"), "")?.trim()?.takeIf { it.isNotBlank() }
            }

            buildMap {
                tag("friendlyName")?.let { put("X-FRIENDLY-NAME", it) }
                tag("manufacturer")?.let { put("X-MANUFACTURER", it) }
                tag("modelName")?.let { put("X-MODEL-NAME", it) }
                tag("modelNumber")?.let { put("X-MODEL-NUMBER", it) }
                tag("deviceType")?.let { put("X-DEVICE-TYPE", it) }
            }
        } catch (_: Exception) {
            emptyMap()
        } finally {
            connection?.disconnect()
        }
    }
}

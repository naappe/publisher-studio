package com.example.lanlens

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

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
                    socket.soTimeout = 900
                    val target = InetAddress.getByName("239.255.255.250")
                    val message = ("M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 1\r\n" +
                        "ST: ssdp:all\r\n\r\n").toByteArray()
                    socket.send(DatagramPacket(message, message.size, target, 1900))

                    val end = System.currentTimeMillis() + 2500
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
                            val ip = packet.address.hostAddress
                            if (ip != null) onResponse(ip, headers)
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (lock.isHeld) lock.release()
            }
        }.start()
    }
}

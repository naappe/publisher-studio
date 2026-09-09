package com.example.lanlens

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

class NetbiosDiscovery {
    fun scan(
        targets: List<String>,
        onFound: (ip: String, name: String?, mac: String?) -> Unit
    ) {
        Thread {
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 280
                    val tx = AtomicInteger(0x2200)

                    // Send a lightweight NetBIOS node-status query to each local IPv4 target.
                    // Windows PCs, NAS devices and some printers answer with their LAN name.
                    targets.forEach { ip ->
                        try {
                            val id = tx.getAndIncrement() and 0xffff
                            val query = buildNodeStatusQuery(id)
                            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(ip), 137))
                        } catch (_: Exception) { }
                    }

                    val end = System.currentTimeMillis() + 3200
                    while (System.currentTimeMillis() < end) {
                        try {
                            val buffer = ByteArray(2048)
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val parsed = parseNodeStatus(packet.data, packet.length)
                            if (parsed != null) {
                                onFound(packet.address.hostAddress ?: continue, parsed.first, parsed.second)
                            }
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) { }
        }.start()
    }

    private fun buildNodeStatusQuery(id: Int): ByteArray {
        val name = ByteArray(16)
        name[0] = 0x2a // wildcard '*'
        val encoded = ByteArray(32)
        for (i in name.indices) {
            val b = name[i].toInt() and 0xff
            encoded[i * 2] = ('A'.code + ((b ushr 4) and 0x0f)).toByte()
            encoded[i * 2 + 1] = ('A'.code + (b and 0x0f)).toByte()
        }

        return ByteArray(50).apply {
            this[0] = ((id ushr 8) and 0xff).toByte()
            this[1] = (id and 0xff).toByte()
            this[4] = 0x00
            this[5] = 0x01 // QDCOUNT
            this[12] = 0x20 // 32-byte NetBIOS encoded label
            System.arraycopy(encoded, 0, this, 13, encoded.size)
            this[45] = 0x00 // end of DNS name
            this[46] = 0x00
            this[47] = 0x21 // NBSTAT
            this[48] = 0x00
            this[49] = 0x01 // IN
        }
    }

    private fun parseNodeStatus(data: ByteArray, length: Int): Pair<String?, String?>? {
        if (length < 12) return null
        val qd = u16(data, 4)
        val an = u16(data, 6)
        if (an <= 0) return null

        var offset = 12
        repeat(qd) {
            offset = skipName(data, offset, length) ?: return null
            if (offset + 4 > length) return null
            offset += 4
        }

        repeat(an) {
            offset = skipName(data, offset, length) ?: return null
            if (offset + 10 > length) return null
            val type = u16(data, offset)
            val rdLen = u16(data, offset + 8)
            val rdata = offset + 10
            if (rdata + rdLen > length) return null

            if (type == 0x21 && rdLen >= 1) {
                val count = data[rdata].toInt() and 0xff
                var p = rdata + 1
                var bestName: String? = null

                repeat(count) {
                    if (p + 18 > rdata + rdLen) return@repeat
                    val raw = String(data, p, 15, Charsets.US_ASCII).trim().trim('\u0000')
                    val suffix = data[p + 15].toInt() and 0xff
                    val flags = u16(data, p + 16)
                    val group = (flags and 0x8000) != 0
                    if (!group && raw.isNotBlank() && raw != "*") {
                        if (bestName == null || suffix == 0x00 || suffix == 0x20) bestName = raw
                    }
                    p += 18
                }

                val mac = if (p + 6 <= rdata + rdLen) {
                    val bytes = data.copyOfRange(p, p + 6)
                    if (bytes.any { it.toInt() != 0 }) {
                        bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
                    } else null
                } else null

                return bestName to mac
            }
            offset = rdata + rdLen
        }
        return null
    }

    private fun skipName(data: ByteArray, start: Int, length: Int): Int? {
        var p = start
        var guard = 0
        while (p < length && guard++ < 64) {
            val n = data[p].toInt() and 0xff
            if (n == 0) return p + 1
            if ((n and 0xc0) == 0xc0) return if (p + 1 < length) p + 2 else null
            p += 1 + n
        }
        return null
    }

    private fun u16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)
    }
}

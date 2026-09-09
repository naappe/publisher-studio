package com.example.lanlens

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LanScanner {
    // A deliberately small set of common LAN service ports. This is service
    // discovery only: no authentication, exploit or payload traffic is sent.
    private val ports = intArrayOf(
        22, 53, 80, 139, 443, 445, 554, 623, 631,
        1883, 3389, 5000, 7000, 8008, 8009, 8080,
        8123, 8443, 8883, 9100, 32400
    )

    fun scan(
        targets: List<String>,
        onProgress: (done: Int, total: Int) -> Unit,
        onFound: (DeviceRecord) -> Unit,
        onFinished: () -> Unit
    ) {
        val executor = Executors.newFixedThreadPool(48)
        val done = AtomicInteger(0)
        val found = ConcurrentHashMap<String, DeviceRecord>()

        targets.forEach { ip ->
            executor.execute {
                try {
                    val open = mutableListOf<Int>()
                    for (port in ports) {
                        try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(ip, port), 110)
                                open += port
                            }
                        } catch (_: Exception) { }
                    }

                    if (open.isNotEmpty()) {
                        val record = found.computeIfAbsent(ip) { DeviceRecord(ip) }
                        record.openPorts += open
                        record.sources += "TCP service probe"
                        try {
                            val host = InetAddress.getByName(ip).canonicalHostName
                            if (host != ip) {
                                record.hostname = host
                                record.details += "Reverse DNS hostname: $host"
                            }
                        } catch (_: Exception) { }
                        onFound(record)
                    }
                } finally {
                    onProgress(done.incrementAndGet(), targets.size)
                }
            }
        }

        executor.shutdown()
        Thread {
            executor.awaitTermination(60, TimeUnit.SECONDS)
            onFinished()
        }.start()
    }
}

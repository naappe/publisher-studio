package com.example.lanlens

import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LanScanner {
    // Service discovery only. A refused TCP connection is also useful evidence:
    // it proves a host answered even when the port itself is closed.
    private val ports = intArrayOf(
        22, 53, 80, 139, 443, 445, 548, 554, 623, 631,
        1883, 3389, 5000, 5357, 7000, 8008, 8009, 8080,
        8123, 8443, 8883, 9100, 32400, 62078
    )

    private enum class ProbeResult { OPEN, REFUSED, TIMEOUT, UNREACHABLE, OTHER }

    fun scan(
        targets: List<String>,
        onProgress: (done: Int, total: Int) -> Unit,
        onFound: (DeviceRecord) -> Unit,
        onFinished: () -> Unit
    ) {
        val executor = Executors.newFixedThreadPool(56)
        val done = AtomicInteger(0)
        val found = ConcurrentHashMap<String, DeviceRecord>()

        targets.forEach { ip ->
            executor.execute {
                try {
                    val record = DeviceRecord(ip)
                    var alive = false
                    var refusedSeen = false

                    try {
                        if (InetAddress.getByName(ip).isReachable(180)) {
                            alive = true
                            record.sources += "Host reachability probe"
                            record.details += "Host answered an IP reachability probe"
                        }
                    } catch (_: Exception) { }

                    for (port in ports) {
                        when (probe(ip, port, 105)) {
                            ProbeResult.OPEN -> {
                                alive = true
                                record.openPorts += port
                            }
                            ProbeResult.REFUSED -> {
                                // Closed port, but the peer sent a TCP reset/refusal. That is
                                // still strong evidence the IP is occupied by a live host.
                                alive = true
                                refusedSeen = true
                            }
                            else -> Unit
                        }
                    }

                    if (record.openPorts.isNotEmpty()) {
                        record.sources += "TCP service probe"
                    }
                    if (refusedSeen) {
                        record.sources += "TCP host response"
                        record.details += "Host actively refused at least one TCP connection"
                    }

                    if (alive) {
                        try {
                            val host = InetAddress.getByName(ip).canonicalHostName
                            if (host != ip) {
                                record.hostname = host
                                record.details += "Reverse DNS hostname: $host"
                                record.sources += "Reverse DNS"
                            }
                        } catch (_: Exception) { }

                        val merged = found.computeIfAbsent(ip) { record }
                        if (merged !== record) {
                            merged.openPorts += record.openPorts
                            merged.services += record.services
                            merged.details += record.details
                            merged.sources += record.sources
                            if (merged.hostname.isNullOrBlank()) merged.hostname = record.hostname
                        }
                        onFound(merged)
                    }
                } finally {
                    onProgress(done.incrementAndGet(), targets.size)
                }
            }
        }

        executor.shutdown()
        Thread {
            executor.awaitTermination(70, TimeUnit.SECONDS)
            onFinished()
        }.start()
    }

    private fun probe(ip: String, port: Int, timeoutMs: Int): ProbeResult {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
            }
            ProbeResult.OPEN
        } catch (_: SocketTimeoutException) {
            ProbeResult.TIMEOUT
        } catch (_: NoRouteToHostException) {
            ProbeResult.UNREACHABLE
        } catch (e: ConnectException) {
            val message = e.message.orEmpty().lowercase()
            if ("refused" in message || "econnrefused" in message) ProbeResult.REFUSED
            else if ("unreachable" in message || "no route" in message || "ehostunreach" in message) ProbeResult.UNREACHABLE
            else ProbeResult.OTHER
        } catch (e: Exception) {
            val message = e.message.orEmpty().lowercase()
            when {
                "refused" in message || "econnrefused" in message -> ProbeResult.REFUSED
                "unreachable" in message || "no route" in message || "ehostunreach" in message -> ProbeResult.UNREACHABLE
                else -> ProbeResult.OTHER
            }
        }
    }
}

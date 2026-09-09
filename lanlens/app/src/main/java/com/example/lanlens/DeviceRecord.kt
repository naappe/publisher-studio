package com.example.lanlens

data class DeviceIdentity(
    val label: String,
    val vendorHint: String? = null,
    val confidence: Int,
    val evidence: List<String>
)

data class DeviceRecord(
    val ip: String,
    var hostname: String? = null,
    val openPorts: MutableSet<Int> = linkedSetOf(),
    val services: MutableSet<String> = linkedSetOf(),
    val details: MutableSet<String> = linkedSetOf(),
    val sources: MutableSet<String> = linkedSetOf()
) {
    fun identity(): DeviceIdentity {
        val text = buildString {
            append(hostname.orEmpty())
            append(' ')
            append(services.joinToString(" "))
            append(' ')
            append(details.joinToString(" "))
        }.lowercase()

        val evidence = mutableListOf<String>()
        var label = "Unknown network device"
        var vendor: String? = null
        var confidence = 25

        fun containsAny(vararg values: String) = values.any { it in text }
        fun port(vararg values: Int) = values.any { it in openPorts }

        when {
            "role:this-phone" in text -> {
                label = "This Android phone"
                confidence = 100
                evidence += "IP comes from this phone's active Wi-Fi interface"
            }
            "role:default-gateway" in text -> {
                label = "Router / default gateway"
                confidence = 99
                evidence += "Android reports this address as the network's default gateway"
            }
            containsAny("prusa") -> {
                label = "Prusa / 3D printer"
                vendor = "Prusa Research"
                confidence = 97
                evidence += "Hostname or advertised service contains “Prusa”"
            }
            containsAny("esp32", "espressif") -> {
                label = "ESP32 / IoT device"
                vendor = "Espressif"
                confidence = 95
                evidence += "Hostname/service signature contains ESP32 or Espressif"
            }
            containsAny("samsung", "galaxy") -> {
                label = "Samsung / Android device"
                vendor = "Samsung"
                confidence = 93
                evidence += "Hostname/service signature contains Samsung or Galaxy"
            }
            containsAny("iphone", "ipad", "apple", "airplay", "raop", "companion-link") -> {
                label = "Apple device"
                vendor = "Apple"
                confidence = 91
                evidence += "Apple Bonjour/AirPlay naming or service signature observed"
            }
            containsAny("googlecast", "chromecast") || port(8008, 8009) -> {
                label = "Cast / smart display"
                vendor = "Google-compatible Cast"
                confidence = 88
                evidence += if (containsAny("googlecast", "chromecast")) {
                    "Google Cast service advertised"
                } else {
                    "Cast-associated TCP service observed"
                }
            }
            containsAny("home-assistant", "home assistant") || port(8123) -> {
                label = "Home Assistant / automation hub"
                confidence = 86
                evidence += "Home Assistant service name or port 8123 observed"
            }
            containsAny("sonos") -> {
                label = "Sonos / network audio device"
                vendor = "Sonos"
                confidence = 95
                evidence += "Sonos signature observed in advertised service data"
            }
            containsAny("ubiquiti", "unifi") -> {
                label = "Ubiquiti / network appliance"
                vendor = "Ubiquiti"
                confidence = 94
                evidence += "Ubiquiti/UniFi signature observed"
            }
            containsAny("xiaomi", "miio") -> {
                label = "Xiaomi / smart device"
                vendor = "Xiaomi"
                confidence = 92
                evidence += "Xiaomi service signature observed"
            }
            containsAny("megatrac", "megarac", "ami") && port(80, 443, 623, 8443) -> {
                label = "Server management controller"
                vendor = "AMI / MegaRAC"
                confidence = 93
                evidence += "AMI/MegaRAC signature plus management service observed"
            }
            containsAny("_ipp", "_printer", "pdl-datastream") || port(631, 9100) -> {
                label = "Printer / print appliance"
                confidence = 86
                evidence += when {
                    containsAny("_ipp", "_printer", "pdl-datastream") -> "Printer service advertised over mDNS"
                    else -> "Printer-associated TCP port observed"
                }
            }
            port(554) -> {
                label = "Camera / RTSP device"
                confidence = 78
                evidence += "RTSP port 554 is accepting connections"
            }
            port(445, 139) && port(3389) -> {
                label = "Windows PC / server"
                confidence = 90
                evidence += "SMB and Remote Desktop service ports observed"
            }
            port(445, 139) -> {
                label = "Windows / SMB-capable device"
                confidence = 78
                evidence += "SMB service port observed"
            }
            port(53) && port(80, 443, 8080, 8443) -> {
                label = "Router / DNS appliance"
                confidence = 82
                evidence += "DNS plus web-management service ports observed"
            }
            port(22) && port(80, 443, 8080, 8443) -> {
                label = "Linux / server / appliance"
                confidence = 74
                evidence += "SSH and web service ports observed"
            }
            port(1883, 8883) -> {
                label = "IoT / MQTT appliance"
                confidence = 72
                evidence += "MQTT service port observed"
            }
            port(32400) -> {
                label = "Plex media server"
                confidence = 91
                evidence += "Plex service port 32400 observed"
            }
            port(80, 443, 8080, 8443) -> {
                label = "Web-managed network device"
                confidence = 54
                evidence += "HTTP/HTTPS management port observed"
            }
            port(22) -> {
                label = "SSH-capable host"
                confidence = 58
                evidence += "SSH port 22 observed"
            }
        }

        if (!hostname.isNullOrBlank() && hostname != ip && "This phone" != hostname) {
            evidence += "Hostname: $hostname"
            confidence += 2
        }
        if (sources.size >= 2) {
            evidence += "Seen through ${sources.size} independent discovery methods"
            confidence += 4
        }
        if (services.isNotEmpty()) {
            evidence += "Advertised ${services.size} network service${if (services.size == 1) "" else "s"}"
            confidence += 2
        }

        if (vendor == null) {
            vendor = when {
                containsAny("samsung", "galaxy") -> "Samsung"
                containsAny("apple", "iphone", "ipad", "airplay", "raop") -> "Apple"
                containsAny("prusa") -> "Prusa Research"
                containsAny("espressif", "esp32") -> "Espressif"
                containsAny("ubiquiti", "unifi") -> "Ubiquiti"
                containsAny("xiaomi", "miio") -> "Xiaomi"
                containsAny("sonos") -> "Sonos"
                else -> null
            }
        }

        return DeviceIdentity(
            label = label,
            vendorHint = vendor,
            confidence = confidence.coerceIn(20, 100),
            evidence = evidence.distinct().take(6)
        )
    }

    fun serviceSummary(): List<String> {
        val names = linkedSetOf<String>()
        openPorts.sorted().forEach { port ->
            names += when (port) {
                22 -> "22 SSH"
                53 -> "53 DNS"
                80 -> "80 HTTP"
                139 -> "139 NetBIOS"
                443 -> "443 HTTPS"
                445 -> "445 SMB"
                554 -> "554 RTSP"
                623 -> "623 IPMI"
                631 -> "631 IPP"
                1883 -> "1883 MQTT"
                3389 -> "3389 RDP"
                5000 -> "5000 Web/API"
                7000 -> "7000 AirPlay/web"
                8008 -> "8008 Cast/web"
                8009 -> "8009 Cast"
                8080 -> "8080 HTTP-alt"
                8123 -> "8123 Home Assistant"
                8443 -> "8443 HTTPS-alt"
                8883 -> "8883 MQTT-TLS"
                9100 -> "9100 Printer"
                32400 -> "32400 Plex"
                else -> port.toString()
            }
        }
        return names.toList()
    }
}

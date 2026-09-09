package com.example.lanlens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {
    private lateinit var networkText: TextView
    private lateinit var statusText: TextView
    private lateinit var summaryText: TextView
    private lateinit var diagnosticText: TextView
    private lateinit var results: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var copyButton: Button
    private val records = ConcurrentHashMap<String, DeviceRecord>()
    private var mdns: MdnsDiscovery? = null
    private var scanStartedAt = 0L
    private var scanCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestNearbyPermissionIfNeeded()
        showNetwork()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, stroke: Int? = null, radius: Int = 18): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        val title = TextView(this).apply {
            text = "LAN Lens"
            textSize = 29f
            setTextColor(Color.rgb(18, 27, 42))
            setTypeface(typeface, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "Discover devices that are actually visible to this Android phone on your Wi-Fi/LAN."
            textSize = 14f
            setTextColor(Color.rgb(88, 99, 115))
            setPadding(0, dp(5), 0, dp(14))
        }

        val networkCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, Color.rgb(224, 230, 238), 16)
        }
        val networkLabel = TextView(this).apply {
            text = "CURRENT NETWORK"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(106, 119, 137))
            setTypeface(typeface, Typeface.BOLD)
        }
        networkText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(29, 39, 54))
            setPadding(0, dp(4), 0, 0)
        }
        networkCard.addView(networkLabel)
        networkCard.addView(networkText)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scanButton = Button(this).apply {
            text = "Scan network"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(32, 95, 230), null, 14)
            setOnClickListener { startScan() }
        }
        copyButton = Button(this).apply {
            text = "Copy report"
            isAllCaps = false
            textSize = 14f
            isEnabled = false
            setOnClickListener { copyReport() }
        }
        buttons.addView(scanButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) })
        buttons.addView(copyButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(6) })

        summaryText = TextView(this).apply {
            text = "No scan yet"
            textSize = 14f
            setTextColor(Color.rgb(25, 35, 50))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(2))
        }
        statusText = TextView(this).apply {
            text = "Ready"
            textSize = 12f
            setTextColor(Color.rgb(94, 106, 123))
            setPadding(0, 0, 0, dp(8))
        }
        diagnosticText = TextView(this).apply {
            visibility = View.GONE
            textSize = 12f
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(results)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(networkCard)
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(summaryText)
        root.addView(statusText)
        root.addView(diagnosticText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun requestNearbyPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 100)
        }
    }

    private fun showNetwork() {
        val n = NetworkInfo.current(this)
        networkText.text = if (n == null) {
            "Connect to Wi-Fi to scan your LAN."
        } else {
            "Phone ${n.ip}/${n.prefixLength}  •  Gateway ${n.gateway ?: "unknown"}"
        }
    }

    private fun startScan() {
        val network = NetworkInfo.current(this)
        if (network == null) {
            Toast.makeText(this, "Connect to Wi-Fi first.", Toast.LENGTH_SHORT).show()
            return
        }

        records.clear()
        results.removeAllViews()
        scanButton.isEnabled = false
        copyButton.isEnabled = false
        scanCompleted = false
        scanStartedAt = System.currentTimeMillis()
        diagnosticText.visibility = View.VISIBLE
        diagnosticText.text = "Peer visibility: testing direct LAN responses…"
        diagnosticText.setTextColor(Color.rgb(86, 97, 114))
        diagnosticText.background = rounded(Color.rgb(238, 243, 250), null, 12)
        statusText.text = "Starting host probes, mDNS, SSDP and NetBIOS discovery…"

        records[network.ip] = DeviceRecord(network.ip).apply {
            hostname = "This phone"
            details += "role:this-phone"
            sources += "Android network config"
        }
        network.gateway?.let { gateway ->
            records.computeIfAbsent(gateway) { DeviceRecord(gateway) }.apply {
                details += "role:default-gateway"
                sources += "Android network config"
            }
        }
        render()

        val targets = network.scanTargets()

        mdns?.stop()
        mdns = MdnsDiscovery(this).also { discovery ->
            discovery.start { ip, name, type, port ->
                val r = records.computeIfAbsent(ip) { DeviceRecord(ip) }
                if (r.hostname.isNullOrBlank()) r.hostname = name
                r.services += "$type:$port"
                r.details += "mDNS service: $name"
                r.sources += "mDNS / Bonjour"
                if (port > 0) r.openPorts += port
                runOnUiThread { render() }
            }
        }

        SsdpDiscovery(this).discover { ip, headers ->
            val r = records.computeIfAbsent(ip) { DeviceRecord(ip) }
            headers["X-FRIENDLY-NAME"]?.let {
                if (r.hostname.isNullOrBlank()) r.hostname = it
                r.details += "SSDP friendly name: $it"
            }
            headers["X-MANUFACTURER"]?.let { r.details += "SSDP manufacturer: $it" }
            headers["X-MODEL-NAME"]?.let { r.details += "SSDP model: $it" }
            headers["X-MODEL-NUMBER"]?.let { r.details += "SSDP model number: $it" }
            headers["X-DEVICE-TYPE"]?.let { r.details += "SSDP device type: $it" }
            headers["SERVER"]?.let { r.details += "SSDP server: $it" }
            headers["ST"]?.let { r.services += "SSDP $it" }
            headers["USN"]?.let { r.details += "SSDP USN: $it" }
            headers["LOCATION"]?.let { r.details += "UPnP description: $it" }
            r.sources += "SSDP / UPnP"
            runOnUiThread { render() }
        }

        NetbiosDiscovery().scan(targets) { ip, name, mac ->
            val r = records.computeIfAbsent(ip) { DeviceRecord(ip) }
            if (!name.isNullOrBlank() && r.hostname.isNullOrBlank()) r.hostname = name
            name?.let { r.details += "NetBIOS name: $it" }
            mac?.let { r.details += "NetBIOS MAC: $it" }
            r.details += "NetBIOS node status"
            r.sources += "NetBIOS node status"
            runOnUiThread { render() }
        }

        LanScanner().scan(
            targets,
            onProgress = { done, total ->
                if (done % 8 == 0 || done == total) runOnUiThread {
                    val peerCount = peerRecords().size
                    statusText.text = "Scanning $done / $total addresses  •  $peerCount peer device${if (peerCount == 1) "" else "s"} answering"
                }
            },
            onFound = { incoming ->
                val r = records.computeIfAbsent(incoming.ip) { DeviceRecord(incoming.ip) }
                incoming.hostname?.let { if (r.hostname.isNullOrBlank()) r.hostname = it }
                r.openPorts += incoming.openPorts
                r.services += incoming.services
                r.details += incoming.details
                r.sources += incoming.sources
                runOnUiThread { render() }
            },
            onFinished = {
                runOnUiThread {
                    scanCompleted = true
                    val seconds = ((System.currentTimeMillis() - scanStartedAt) / 1000.0)
                    val peers = peerRecords()
                    val typed = peers.count { it.identity().confidence >= 70 }
                    statusText.text = "Host sweep finished in ${"%.1f".format(seconds)} s  •  ${peers.size} peers seen  •  $typed strongly typed  •  Bonjour may continue briefly"
                    scanButton.isEnabled = true
                    copyButton.isEnabled = records.isNotEmpty()
                    render()
                }
            }
        )
    }

    private fun isSelfOrGateway(r: DeviceRecord): Boolean {
        return r.details.contains("role:this-phone") || r.details.contains("role:default-gateway")
    }

    private fun peerRecords(): List<DeviceRecord> = records.values.filterNot { isSelfOrGateway(it) }

    private fun render() {
        results.removeAllViews()

        val identities = records.values.map { it to it.identity() }
        val peers = identities.filterNot { isSelfOrGateway(it.first) }
        val stronglyTyped = peers.count { it.second.confidence >= 70 }
        val uncertain = peers.size - stronglyTyped
        summaryText.text = "${peers.size} peer device${if (peers.size == 1) "" else "s"}  •  $stronglyTyped strongly identified  •  $uncertain uncertain  •  ${identities.size} total incl. phone/router"

        if (!scanCompleted) {
            diagnosticText.visibility = View.VISIBLE
            diagnosticText.text = "Peer visibility: testing direct LAN responses…"
            diagnosticText.setTextColor(Color.rgb(86, 97, 114))
            diagnosticText.background = rounded(Color.rgb(238, 243, 250), null, 12)
        } else if (peers.isEmpty()) {
            diagnosticText.visibility = View.VISIBLE
            diagnosticText.text = "No peer device answered this phone. Likely causes: Wi-Fi client/AP isolation, guest or managed Wi-Fi, or peers filtering direct probes. A normal Android app cannot enter full Wi-Fi monitor mode, so dedicated radio hardware can still observe RF activity that this app cannot see."
            diagnosticText.setTextColor(Color.rgb(126, 78, 0))
            diagnosticText.background = rounded(Color.rgb(255, 247, 225), null, 12)
        } else {
            diagnosticText.visibility = View.VISIBLE
            diagnosticText.text = "Peer visibility is working: ${peers.size} other LAN device${if (peers.size == 1) "" else "s"} answered at least one discovery method."
            diagnosticText.setTextColor(Color.rgb(22, 103, 69))
            diagnosticText.background = rounded(Color.rgb(232, 247, 239), null, 12)
        }

        identities
            .sortedWith(compareBy<Pair<DeviceRecord, DeviceIdentity>> { isSelfOrGateway(it.first) }
                .thenByDescending { it.second.confidence }
                .thenBy { ipToLong(it.first.ip) })
            .forEach { (r, identity) ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                    background = rounded(Color.WHITE, Color.rgb(225, 231, 239), 16)
                }

                val topRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val name = TextView(this).apply {
                    text = r.hostname ?: "Unnamed device"
                    textSize = 16f
                    setTextColor(Color.rgb(20, 29, 43))
                    setTypeface(typeface, Typeface.BOLD)
                }
                val confidence = TextView(this).apply {
                    text = "${identity.confidence}%"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(9), dp(4), dp(9), dp(4))
                    setTextColor(if (identity.confidence >= 70) Color.rgb(18, 110, 70) else Color.rgb(142, 93, 0))
                    background = rounded(
                        if (identity.confidence >= 70) Color.rgb(232, 247, 239) else Color.rgb(255, 246, 221),
                        null,
                        20
                    )
                }
                topRow.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                topRow.addView(confidence)

                val identityText = TextView(this).apply {
                    text = identity.label
                    textSize = 14f
                    setTextColor(Color.rgb(39, 50, 67))
                    setPadding(0, dp(4), 0, 0)
                }
                val addressText = TextView(this).apply {
                    val vendorPart = identity.vendorHint?.let { "  •  Vendor hint: $it" } ?: ""
                    text = "${r.ip}$vendorPart"
                    textSize = 12f
                    setTextColor(Color.rgb(96, 108, 125))
                    setPadding(0, dp(2), 0, 0)
                }

                card.addView(topRow)
                card.addView(identityText)
                card.addView(addressText)

                if (r.sources.isNotEmpty()) {
                    val sources = TextView(this).apply {
                        text = "Seen by: ${r.sources.joinToString(" • ")}"
                        textSize = 11f
                        setTextColor(Color.rgb(78, 95, 122))
                        setPadding(0, dp(7), 0, 0)
                    }
                    card.addView(sources)
                }

                val serviceList = r.serviceSummary()
                if (serviceList.isNotEmpty() || r.services.isNotEmpty()) {
                    val networkMeta = TextView(this).apply {
                        val tcp = if (serviceList.isEmpty()) "" else "TCP: ${serviceList.take(8).joinToString(" • ")}"
                        val advertised = if (r.services.isEmpty()) "" else "Advertised: ${r.services.take(4).joinToString(" • ")}"
                        text = listOf(tcp, advertised).filter { it.isNotBlank() }.joinToString("\n")
                        textSize = 11f
                        setTextColor(Color.rgb(90, 102, 119))
                        setPadding(0, dp(7), 0, 0)
                    }
                    card.addView(networkMeta)
                }

                if (identity.evidence.isNotEmpty()) {
                    val whyTitle = TextView(this).apply {
                        text = "WHY IDENTIFIED"
                        textSize = 10f
                        letterSpacing = 0.07f
                        setTextColor(Color.rgb(107, 119, 136))
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(0, dp(9), 0, dp(2))
                    }
                    val why = TextView(this).apply {
                        text = identity.evidence.joinToString("\n") { "• $it" }
                        textSize = 11f
                        setTextColor(Color.rgb(63, 75, 92))
                    }
                    card.addView(whyTitle)
                    card.addView(why)
                }

                val rawClues = r.details.filterNot { it.startsWith("role:") }.take(5)
                if (rawClues.isNotEmpty()) {
                    val clueTitle = TextView(this).apply {
                        text = "RAW CLUES"
                        textSize = 10f
                        letterSpacing = 0.07f
                        setTextColor(Color.rgb(107, 119, 136))
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(0, dp(9), 0, dp(2))
                    }
                    val clues = TextView(this).apply {
                        text = rawClues.joinToString("\n") { "• $it" }
                        textSize = 10.5f
                        setTextColor(Color.rgb(74, 85, 101))
                    }
                    card.addView(clueTitle)
                    card.addView(clues)
                }

                results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            }
    }

    private fun copyReport() {
        if (records.isEmpty()) return
        val report = buildReport()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LAN Lens report", report))
        Toast.makeText(this, "LAN report copied.", Toast.LENGTH_SHORT).show()
    }

    private fun buildReport(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val peers = peerRecords()
        return buildString {
            appendLine("LAN Lens report — $timestamp")
            appendLine("Peer devices: ${peers.size}")
            appendLine("Total records including phone/router: ${records.size}")
            if (scanCompleted && peers.isEmpty()) appendLine("Diagnostic: no peer answered; client/AP isolation or peer filtering may be active")
            appendLine()
            records.values.sortedBy { ipToLong(it.ip) }.forEach { r ->
                val id = r.identity()
                appendLine("${r.ip} — ${r.hostname ?: "Unnamed device"}")
                appendLine("Identity: ${id.label} (${id.confidence}% confidence)")
                id.vendorHint?.let { appendLine("Vendor hint: $it") }
                if (r.sources.isNotEmpty()) appendLine("Sources: ${r.sources.joinToString(", ")}")
                if (r.serviceSummary().isNotEmpty()) appendLine("TCP: ${r.serviceSummary().joinToString(", ")}")
                if (r.services.isNotEmpty()) appendLine("Advertised: ${r.services.joinToString(", ")}")
                if (r.details.isNotEmpty()) appendLine("Raw clues: ${r.details.filterNot { it.startsWith("role:") }.joinToString(" | ")}")
                if (id.evidence.isNotEmpty()) appendLine("Evidence: ${id.evidence.joinToString(" | ")}")
                appendLine()
            }
        }
    }

    private fun ipToLong(ip: String): Long {
        return ip.split('.').fold(0L) { acc, s -> (acc shl 8) + (s.toLongOrNull() ?: 0L) }
    }

    override fun onDestroy() {
        mdns?.stop()
        super.onDestroy()
    }
}

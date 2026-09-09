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
    private lateinit var results: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var copyButton: Button
    private val records = ConcurrentHashMap<String, DeviceRecord>()
    private var mdns: MdnsDiscovery? = null
    private var scanStartedAt = 0L

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
            text = "See what a normal Android phone can identify on your own Wi-Fi/LAN."
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
            setPadding(0, 0, 0, dp(10))
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
        scanStartedAt = System.currentTimeMillis()
        statusText.text = "Starting mDNS, SSDP and local service discovery…"

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
            headers["SERVER"]?.let { r.details += "SSDP server: $it" }
            headers["ST"]?.let { r.services += "SSDP $it" }
            headers["USN"]?.let { r.details += "SSDP USN: $it" }
            headers["LOCATION"]?.let { r.details += "UPnP description: $it" }
            r.sources += "SSDP / UPnP"
            runOnUiThread { render() }
        }

        val targets = network.scanTargets()
        LanScanner().scan(
            targets,
            onProgress = { done, total ->
                if (done % 8 == 0 || done == total) runOnUiThread {
                    val identified = records.values.count { it.identity().confidence >= 70 }
                    statusText.text = "Scanning $done / $total addresses  •  ${records.size} seen  •  $identified identified"
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
                    val seconds = ((System.currentTimeMillis() - scanStartedAt) / 1000.0)
                    val identified = records.values.count { it.identity().confidence >= 70 }
                    statusText.text = "Finished in ${"%.1f".format(seconds)} s  •  ${records.size} devices seen  •  $identified confidently identified"
                    scanButton.isEnabled = true
                    copyButton.isEnabled = records.isNotEmpty()
                    render()
                }
            }
        )
    }

    private fun render() {
        results.removeAllViews()

        val identities = records.values.map { it to it.identity() }
        val identified = identities.count { it.second.confidence >= 70 }
        val unknown = identities.size - identified
        summaryText.text = "${identities.size} devices  •  $identified identified  •  $unknown uncertain"

        identities
            .sortedWith(compareByDescending<Pair<DeviceRecord, DeviceIdentity>> { it.second.confidence }
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
        return buildString {
            appendLine("LAN Lens report — $timestamp")
            appendLine("Devices: ${records.size}")
            appendLine()
            records.values.sortedBy { ipToLong(it.ip) }.forEach { r ->
                val id = r.identity()
                appendLine("${r.ip} — ${r.hostname ?: "Unnamed device"}")
                appendLine("Identity: ${id.label} (${id.confidence}% confidence)")
                id.vendorHint?.let { appendLine("Vendor hint: $it") }
                if (r.sources.isNotEmpty()) appendLine("Sources: ${r.sources.joinToString(", ")}")
                if (r.serviceSummary().isNotEmpty()) appendLine("TCP: ${r.serviceSummary().joinToString(", ")}")
                if (r.services.isNotEmpty()) appendLine("Advertised: ${r.services.joinToString(", ")}")
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

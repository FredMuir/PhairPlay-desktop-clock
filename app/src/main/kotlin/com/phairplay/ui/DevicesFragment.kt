package com.phairplay.ui

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.phairplay.R

/** Browses AirPlay receivers visible on the current Wi-Fi/LAN using Android DNS-SD. */
class DevicesFragment : Fragment() {
    private lateinit var nsd: NsdManager
    private lateinit var content: LinearLayout
    private val devices = linkedMapOf<String, NsdServiceInfo>()
    private val pendingServices = linkedMapOf<String, NsdServiceInfo>()
    private var resolvingName: String? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var scanning = false
    private var refreshPending = false
    private var resumePending = false
    private var stopPending = false
    private val managedDevices by lazy {
        requireContext().getSharedPreferences("airplay_lan_devices", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        nsd = requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(22), dp(28), dp(18))
            setBackgroundColor(requireContext().getColor(R.color.background_dark))
        }
        val heading = TextView(requireContext()).apply {
            text = getString(R.string.devices_title)
            textSize = 28f
            setTextColor(requireContext().getColor(R.color.text_primary))
        }
        root.addView(heading)
        root.addView(TextView(requireContext()).apply {
            text = getString(R.string.devices_subtitle)
            textSize = 15f
            setTextColor(requireContext().getColor(R.color.text_secondary))
            setPadding(0, dp(6), 0, dp(14))
        })
        root.addView(Button(requireContext()).apply {
            text = getString(R.string.devices_refresh)
            isAllCaps = false
            isFocusable = true
            minWidth = dp(112)
            setPadding(dp(20), 0, dp(20), 0)
            setTextColor(requireContext().getColor(R.color.background_dark))
            background = requireContext().getDrawable(R.drawable.btn_devices_refresh_selector)
            setOnClickListener { refreshDiscovery() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)).apply {
            bottomMargin = dp(12)
        })
        val scroll = ScrollView(requireContext())
        content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        showEmpty(getString(R.string.devices_scanning))
        return root
    }

    override fun onResume() {
        super.onResume()
        resumePending = true
        if (listener == null) {
            resumePending = false
            beginDiscovery()
        }
    }
    override fun onPause() { stopDiscovery(); super.onPause() }

    private fun beginDiscovery() {
        if (!isAdded || scanning || listener != null || refreshPending) return
        devices.clear()
        pendingServices.clear()
        resolvingName = null
        showEmpty(getString(R.string.devices_scanning))
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { scanning = true }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType?.contains("_airplay._tcp") == true) queueResolve(serviceInfo)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                activity?.runOnUiThread {
                    devices.remove(serviceInfo.serviceName)
                    renderDevices()
                }
            }
            override fun onDiscoveryStopped(serviceType: String) {
                scanning = false
                if (listener === this) listener = null
                stopPending = false
                if (refreshPending || resumePending) {
                    refreshPending = false
                    resumePending = false
                    activity?.runOnUiThread { if (isAdded && isResumed) beginDiscovery() }
                }
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                activity?.runOnUiThread {
                    scanning = false
                    showEmpty(getString(R.string.devices_scan_error, errorCode))
                }
                stopPending = true
                runCatching { nsd.stopServiceDiscovery(this) }
                    .onFailure {
                        stopPending = false
                        if (listener === this) listener = null
                    }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Keep the active listener reference so a user can retry stopping it.
                refreshPending = false
                stopPending = false
                activity?.runOnUiThread {
                    if (isAdded) showEmpty(getString(R.string.devices_scan_error, errorCode))
                }
                runCatching { nsd.stopServiceDiscovery(this) }
            }
        }
        listener = callback
        runCatching { nsd.discoverServices("_airplay._tcp.", NsdManager.PROTOCOL_DNS_SD, callback) }
            .onFailure {
                scanning = false
                if (listener === callback) listener = null
                showEmpty(getString(R.string.devices_scan_error, -1))
            }
    }

    /** Clear stale rows and restart DNS-SD discovery after Android confirms the old scan stopped. */
    private fun refreshDiscovery() {
        devices.clear()
        pendingServices.clear()
        resolvingName = null
        showEmpty(getString(R.string.devices_scanning))

        val active = listener
        if (active == null) {
            beginDiscovery()
            return
        }
        if (refreshPending) return
        refreshPending = true
        if (stopPending) return
        stopPending = true
        runCatching { nsd.stopServiceDiscovery(active) }
            .onFailure {
                refreshPending = false
                stopPending = false
                if (listener === active) listener = null
                showEmpty(getString(R.string.devices_scan_error, -1))
            }
    }

    private fun queueResolve(info: NsdServiceInfo) {
        if (devices.containsKey(info.serviceName) || pendingServices.containsKey(info.serviceName) || resolvingName == info.serviceName) return
        pendingServices[info.serviceName] = info
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolvingName != null || pendingServices.isEmpty()) return
        val (name, info) = pendingServices.entries.first()
        pendingServices.remove(name)
        resolvingName = name
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolvingName = null
                resolveNext()
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolvingName = null
                activity?.runOnUiThread {
                    if (isAdded) {
                        devices[serviceInfo.serviceName] = serviceInfo
                        renderDevices()
                    }
                }
                resolveNext()
            }
        })
    }

    private fun renderDevices() {
        if (!isAdded) return
        content.removeAllViews()
        if (devices.isEmpty()) { showEmpty(getString(R.string.devices_none)); return }
        devices.values.sortedWith(compareBy<NsdServiceInfo> { !isFavorite(it) }
            .thenBy { displayName(it).lowercase() }).forEach { info ->
            val rawModel = runCatching { String(info.attributes["model"] ?: byteArrayOf()) }.getOrDefault("").ifBlank { getString(R.string.devices_unknown_model) }
            val model = when (rawModel) {
                "AudioAccessory5,1" -> getString(R.string.device_mode_homepod)
                "AppleTV5,3" -> getString(R.string.device_mode_apple_tv)
                else -> rawModel
            }
            val ip = info.host?.hostAddress ?: "—"
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(12), dp(18), dp(12))
                setBackgroundResource(R.drawable.focus_ring_selector)
                isFocusable = true
                setOnClickListener { showDetails(info, rawModel, ip) }
            }
            card.addView(TextView(requireContext()).apply {
                text = displayName(info) + if (isFavorite(info)) "  ★" else ""
                textSize = 18f
                setTextColor(requireContext().getColor(R.color.text_primary))
            })
            card.addView(TextView(requireContext()).apply {
                text = "$model   ·   $ip:${info.port}"
                textSize = 14f
                setTextColor(requireContext().getColor(R.color.text_secondary))
                setPadding(0, dp(4), 0, 0)
            })
            content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)).apply { bottomMargin = dp(8) })
        }
    }

    private fun showDetails(info: NsdServiceInfo, model: String, ip: String) {
        val features = runCatching { String(info.attributes["features"] ?: byteArrayOf()) }.getOrDefault("—")
        val key = managedDeviceKey(info)
        AlertDialog.Builder(requireContext())
            .setTitle(displayName(info))
            .setMessage(getString(R.string.devices_detail, model, ip, info.port, features))
            .setPositiveButton(R.string.devices_copy_address) { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(info.serviceName, ip))
                android.widget.Toast.makeText(requireContext(), R.string.devices_address_copied, android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.devices_rename) { _, _ -> showRenameDialog(key) }
            .setNeutralButton(if (isFavorite(info)) R.string.devices_unfavorite else R.string.devices_favorite) { _, _ ->
                managedDevices.edit().putBoolean("favorite_$key", !isFavorite(info)).apply()
                renderDevices()
            }
            .show()
    }

    private fun showRenameDialog(key: String) {
        val input = EditText(requireContext()).apply {
            setSingleLine(true)
            hint = getString(R.string.devices_alias_hint)
            setText(managedDevices.getString("alias_$key", "").orEmpty())
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.devices_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val alias = input.text.toString().trim().take(40)
                managedDevices.edit().apply {
                    if (alias.isBlank()) remove("alias_$key") else putString("alias_$key", alias)
                }.apply()
                renderDevices()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun displayName(info: NsdServiceInfo): String =
        managedDevices.getString("alias_${managedDeviceKey(info)}", null)?.takeIf { it.isNotBlank() }
            ?: info.serviceName

    private fun isFavorite(info: NsdServiceInfo): Boolean =
        managedDevices.getBoolean("favorite_${managedDeviceKey(info)}", false)

    /** Prefer the receiver's stable DNS-SD identity; fall back to its service name if absent. */
    private fun managedDeviceKey(info: NsdServiceInfo): String {
        val model = runCatching { String(info.attributes["model"] ?: byteArrayOf()) }.getOrDefault("")
        val identity = info.attributes.entries.firstOrNull { (name, value) ->
            name.lowercase() in setOf("deviceid", "id", "pi") && value.isNotEmpty()
        }?.let { (name, value) -> "$model|${name.lowercase()}=${String(value).trim()}" }
            ?: "${info.serviceType}|$model|${info.serviceName.lowercase()}"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun showEmpty(message: String) {
        if (!::content.isInitialized) return
        content.removeAllViews()
        content.addView(TextView(requireContext()).apply {
            text = message
            textSize = 16f
            setTextColor(requireContext().getColor(R.color.text_secondary))
            setPadding(dp(4), dp(20), dp(4), dp(20))
        })
    }

    private fun stopDiscovery() {
        refreshPending = false
        resumePending = false
        val active = listener ?: run { scanning = false; return }
        if (stopPending) return
        stopPending = true
        runCatching { nsd.stopServiceDiscovery(active) }
            .onFailure {
                stopPending = false
                if (listener === active) listener = null
                scanning = false
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

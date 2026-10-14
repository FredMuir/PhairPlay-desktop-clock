package com.phairplay.ui

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.phairplay.R
import com.phairplay.integrations.HomeAssistant
import com.phairplay.settings.AppSettings
import com.phairplay.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Home Assistant discovery, setup, entity overview, and common device controls. */
class HomeAssistantFragment : Fragment() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var entityList: LinearLayout
    private lateinit var search: EditText
    private lateinit var categoryTabs: LinearLayout
    private lateinit var settingsRepository: SettingsRepository
    private var currentSettings = AppSettings.DEFAULT
    private var nsd: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val discovered = linkedMapOf<String, NsdServiceInfo>()
    private var allStates: List<JSONObject> = emptyList()
    private var category = CATEGORY_ALL
    private var loading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        settingsRepository = SettingsRepository(requireContext().applicationContext)
        nsd = requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(18))
            setBackgroundColor(requireContext().getColor(R.color.background_dark))
        }
        return ScrollView(requireContext()).apply {
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(20))
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            currentSettings = settingsRepository.settingsFlow.first()
            renderPage()
            if (currentSettings.homeAssistantUrl.isNotBlank() && HomeAssistant.token(requireContext()) != null) {
                refreshStates()
            }
        }
    }

    override fun onDestroyView() {
        stopDiscovery()
        super.onDestroyView()
    }

    private fun renderPage() {
        root.removeAllViews()
        root.addView(text(getString(R.string.ha_page_title), 28f, R.color.text_primary))
        root.addView(text(getString(R.string.ha_page_subtitle), 15f, R.color.text_secondary).apply {
            setPadding(0, dp(5), 0, dp(14))
        })
        val connected = currentSettings.homeAssistantUrl.isNotBlank() && HomeAssistant.token(requireContext()) != null
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        status = text(
            if (connected) getString(R.string.ha_connected_to, currentSettings.homeAssistantUrl)
            else getString(R.string.ha_not_connected), 14f,
            if (connected) R.color.accent_blue else R.color.text_secondary
        ).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        header.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        if (connected) {
            header.addView(button(getString(R.string.ha_refresh)) { refreshStates() })
            header.addView(button(getString(R.string.ha_connection_settings)) { showConnectionActions() })
        }
        root.addView(header)

        if (!connected) {
            root.addView(text(getString(R.string.ha_setup_hint), 16f, R.color.text_secondary).apply {
                setPadding(0, dp(12), 0, dp(14))
            })
            root.addView(button(getString(R.string.ha_discover_button)) { startDiscovery() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { bottomMargin = dp(8) })
            root.addView(button(getString(R.string.ha_manual_address)) { showManualAddressDialog() },
                LinearLayout.LayoutParams(-2, dp(48)))
            if (discoveryListener != null) root.addView(text(getString(R.string.ha_discovery_running), 14f, R.color.text_secondary))
            discovered.values.forEach { info ->
                val address = info.host?.hostAddress ?: return@forEach
                val host = if (':' in address && !address.startsWith("[")) "[$address]" else address
                val url = "http://$host:${info.port}"
                root.addView(button(getString(R.string.ha_discovered_instance, info.serviceName, address)) {
                    stopDiscovery()
                    chooseInstance(url)
                }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            }
            return
        }

        search = EditText(requireContext()).apply {
            hint = getString(R.string.ha_search_hint)
            isSingleLine = true
            setTextColor(requireContext().getColor(R.color.text_primary))
            setHintTextColor(requireContext().getColor(R.color.text_secondary))
            setPadding(dp(14), dp(4), dp(14), dp(4))
            background = AppCompatResources.getDrawable(requireContext(), R.drawable.nav_panel_glass)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderEntities() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        val tabScroll = HorizontalScrollView(requireContext()).apply { isHorizontalScrollBarEnabled = false }
        categoryTabs = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        tabScroll.addView(categoryTabs)
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        buildCategoryTabs()
        entityList = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(entityList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        renderEntities()
    }

    private fun buildCategoryTabs() {
        categoryTabs.removeAllViews()
        listOf(CATEGORY_ALL, CATEGORY_CONTROLS, "climate", "sensor").forEach { value ->
            categoryTabs.addView(button(getString(when (value) {
                CATEGORY_ALL -> R.string.ha_category_all
                CATEGORY_CONTROLS -> R.string.ha_category_controls
                "climate" -> R.string.ha_category_climate
                else -> R.string.ha_category_sensors
            })) {
                category = value
                buildCategoryTabs()
                renderEntities()
            }.apply {
                isSelected = category == value
                background = AppCompatResources.getDrawable(requireContext(),
                    if (isSelected) R.drawable.nav_item_selector else R.drawable.btn_service_secondary_selector)
            }, LinearLayout.LayoutParams(-2, dp(40)).apply {
                marginEnd = dp(8)
            })
        }
    }

    private fun renderEntities() {
        if (!::entityList.isInitialized) return
        entityList.removeAllViews()
        if (loading) {
            entityList.addView(text(getString(R.string.ha_loading_states), 15f, R.color.text_secondary))
            return
        }
        val query = if (::search.isInitialized) search.text.toString().trim().lowercase() else ""
        val matches = allStates.filter { state ->
            val id = state.optString("entity_id")
            val domain = id.substringBefore('.', "")
            val name = state.optJSONObject("attributes")?.optString("friendly_name").orEmpty()
            val categoryMatch = when (category) {
                CATEGORY_CONTROLS -> domain in CONTROL_DOMAINS
                "climate" -> domain == "climate"
                "sensor" -> domain in SENSOR_DOMAINS
                else -> true
            }
            categoryMatch && (query.isBlank() || id.lowercase().contains(query) || name.lowercase().contains(query))
        }.sortedBy { it.optJSONObject("attributes")?.optString("friendly_name").orEmpty().ifBlank { it.optString("entity_id") }.lowercase() }
        if (matches.isEmpty()) {
            entityList.addView(text(getString(if (allStates.isEmpty()) R.string.ha_no_entities else R.string.ha_no_matching_entities), 15f, R.color.text_secondary))
            return
        }
        matches.take(MAX_VISIBLE_ENTITIES).forEach { entity -> entityList.addView(entityCard(entity)) }
        if (matches.size > MAX_VISIBLE_ENTITIES) entityList.addView(text(getString(R.string.ha_more_entities, matches.size - MAX_VISIBLE_ENTITIES), 13f, R.color.text_secondary))
    }

    private fun entityCard(state: JSONObject): View {
        val entityId = state.optString("entity_id")
        val domain = entityId.substringBefore('.', "")
        val attrs = state.optJSONObject("attributes") ?: JSONObject()
        val title = attrs.optString("friendly_name").ifBlank { entityId }
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = AppCompatResources.getDrawable(requireContext(), R.drawable.nav_panel_glass)
        }
        val heading = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(title, 17f, R.color.text_primary), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(text(state.optString("state"), 15f, R.color.text_secondary))
        card.addView(heading)
        val detail = listOf(
            attrs.optString("current_temperature").takeIf(String::isNotBlank)?.let { getString(R.string.ha_current_temperature, it) },
            attrs.optString("temperature").takeIf(String::isNotBlank)?.let { getString(R.string.ha_target_temperature, it) },
            attrs.optString("unit_of_measurement").takeIf(String::isNotBlank)
        ).filterNotNull().distinct().joinToString("  ")
        if (detail.isNotBlank()) card.addView(text(detail, 13f, R.color.text_secondary).apply { setPadding(0, dp(4), 0, 0) })
        val controls = controlButtons(domain, entityId, state)
        if (controls.childCount > 0) {
            card.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        val favorites = currentSettings.homeAssistantEntities.split(',', '\n').map(String::trim).filter(String::isNotBlank).toSet()
        card.addView(button(getString(if (entityId in favorites) R.string.ha_remove_from_clock else R.string.ha_add_to_clock)) {
            toggleClockFavorite(entityId)
        }, LinearLayout.LayoutParams(-2, dp(40)).apply { topMargin = dp(8) })
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        return card
    }

    private fun toggleClockFavorite(entityId: String) {
        lifecycleScope.launch {
            val favorites = currentSettings.homeAssistantEntities.split(',', '\n').map(String::trim)
                .filter(String::isNotBlank).toMutableSet()
            if (!favorites.add(entityId)) favorites.remove(entityId)
            settingsRepository.update { it.copy(homeAssistantEntities = favorites.joinToString(","), homeAssistantEnabled = true) }
            currentSettings = settingsRepository.settingsFlow.first()
            renderEntities()
        }
    }

    private fun controlButtons(domain: String, entityId: String, state: JSONObject): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            when (domain) {
                "light", "switch", "input_boolean", "fan", "siren" -> addControlButton(
                    if (state.optString("state") == "on") R.string.ha_turn_off else R.string.ha_turn_on,
                    domain, "toggle", entityId)
                "automation" -> addControlButton(
                    if (state.optString("state") == "on") R.string.ha_turn_off else R.string.ha_turn_on,
                    domain, "toggle", entityId)
                "scene", "script" -> addControlButton(R.string.ha_activate, domain, "turn_on", entityId)
                "button" -> addControlButton(R.string.ha_press, domain, "press", entityId)
                "cover" -> {
                    addControlButton(R.string.ha_open, domain, "open_cover", entityId)
                    addControlButton(R.string.ha_close, domain, "close_cover", entityId)
                }
                "lock" -> addControlButton(if (state.optString("state") == "locked") R.string.ha_unlock else R.string.ha_lock,
                    domain, if (state.optString("state") == "locked") "unlock" else "lock", entityId)
                "vacuum" -> addControlButton(if (state.optString("state") == "cleaning") R.string.ha_pause else R.string.ha_start,
                    domain, if (state.optString("state") == "cleaning") "pause" else "start", entityId)
                "climate" -> {
                    addControlButton(R.string.ha_temperature_down, domain, "set_temperature", entityId,
                        JSONObject().put("temperature", (state.optJSONObject("attributes")?.optDouble("temperature", 20.0) ?: 20.0) - 0.5))
                    addControlButton(R.string.ha_temperature_up, domain, "set_temperature", entityId,
                        JSONObject().put("temperature", (state.optJSONObject("attributes")?.optDouble("temperature", 20.0) ?: 20.0) + 0.5))
                }
            }
        }

    private fun LinearLayout.addControlButton(label: Int, domain: String, service: String, entityId: String, data: JSONObject = JSONObject()) {
        addView(button(getString(label)) { runService(domain, service, entityId, data) },
            LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(8) })
    }

    private fun runService(domain: String, service: String, entityId: String, data: JSONObject) {
        val token = HomeAssistant.token(requireContext()) ?: return showTokenDialog(currentSettings.homeAssistantUrl)
        lifecycleScope.launch {
            loading = true
            renderEntities()
            val result = withContext(Dispatchers.IO) {
                runCatching { HomeAssistant.callService(currentSettings.homeAssistantUrl, token, domain, service, entityId, data) }
            }
            loading = false
            if (!isAdded) return@launch
            result.onFailure { Toast.makeText(requireContext(), getString(R.string.ha_action_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show() }
            refreshStates()
        }
    }

    private fun refreshStates() {
        if (loading || !isAdded) return
        val url = currentSettings.homeAssistantUrl
        val token = HomeAssistant.token(requireContext())
        if (url.isBlank() || token == null) {
            renderPage()
            return
        }
        loading = true
        if (::status.isInitialized) status.text = getString(R.string.ha_connecting)
        if (::entityList.isInitialized) renderEntities()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    HomeAssistant.request(url, token, "/api/")
                    HomeAssistant.states(url, token)
                }
            }
            loading = false
            if (!isAdded) return@launch
            result.onSuccess {
                allStates = it
                if (::status.isInitialized) status.text = getString(R.string.ha_connected_to, url)
                renderEntities()
            }.onFailure {
                allStates = emptyList()
                if (::status.isInitialized) status.text = getString(R.string.ha_connection_error)
                renderEntities()
                Toast.makeText(requireContext(), getString(R.string.ha_action_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun chooseInstance(url: String) {
        val existing = HomeAssistant.token(requireContext())
        if (existing != null) {
            lifecycleScope.launch {
                saveUrl(url)
                refreshStates()
            }
        } else showTokenDialog(url)
    }

    private fun showTokenDialog(url: String) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.ha_token_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            addView(text(getString(R.string.ha_token_help), 14f, R.color.text_secondary))
            addView(button(getString(R.string.ha_open_security_page)) {
                val target = Uri.parse(url.trimEnd('/') + "/profile/security")
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, target)) }
            }, LinearLayout.LayoutParams(-2, dp(44)).apply { topMargin = dp(8) })
            addView(input, LinearLayout.LayoutParams(-1, dp(52)))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ha_token_title)
            .setView(content)
            .setPositiveButton(R.string.ha_connect) { _, _ ->
                val entered = input.text.toString().trim()
                if (entered.isBlank()) return@setPositiveButton
                HomeAssistant.saveToken(requireContext(), entered)
                lifecycleScope.launch {
                    saveUrl(url)
                    refreshStates()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showManualAddressDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.ha_manual_address_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(currentSettings.homeAssistantUrl)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ha_manual_address_title)
            .setMessage(R.string.ha_manual_address_help)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text.toString().trim().trimEnd('/')
                if (value.isBlank()) return@setPositiveButton
                val url = when {
                    value.startsWith("http://") || value.startsWith("https://") -> value
                    Regex("^[^:]+:\\d+$").matches(value) -> "http://$value"
                    else -> "http://$value:8123"
                }
                chooseInstance(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showConnectionActions() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ha_connection_settings)
            .setItems(arrayOf(getString(R.string.ha_change_server), getString(R.string.ha_change_token), getString(R.string.ha_disconnect))) { _, which ->
                when (which) {
                    0 -> showManualAddressDialog()
                    1 -> showTokenDialog(currentSettings.homeAssistantUrl)
                    else -> disconnect()
                }
            }
            .show()
    }

    private fun disconnect() {
        HomeAssistant.clearToken(requireContext())
        lifecycleScope.launch {
            settingsRepository.update { it.copy(homeAssistantUrl = "", homeAssistantEnabled = false,
                homeAssistantCalendarEnabled = false, homeAssistantTodoEnabled = false, homeAssistantEntities = "") }
            currentSettings = settingsRepository.settingsFlow.first()
            allStates = emptyList()
            renderPage()
        }
    }

    private suspend fun saveUrl(url: String) {
        settingsRepository.update { it.copy(homeAssistantUrl = url, homeAssistantEnabled = true) }
        currentSettings = settingsRepository.settingsFlow.first()
        allStates = emptyList()
        renderPage()
    }

    private fun startDiscovery() {
        if (discoveryListener != null) return
        discovered.clear()
        val manager = nsd ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType?.contains("_home-assistant._tcp") != true) return
                @Suppress("DEPRECATION")
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (info.host == null) return
                        activity?.runOnUiThread {
                            if (!isAdded) return@runOnUiThread
                            discovered[info.serviceName] = info
                            renderPage()
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                activity?.runOnUiThread {
                    discovered.remove(serviceInfo.serviceName)
                    if (isAdded) renderPage()
                }
            }
            override fun onDiscoveryStopped(serviceType: String) { discoveryListener = null }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
                activity?.runOnUiThread {
                    if (isAdded) Toast.makeText(requireContext(), getString(R.string.ha_discovery_error), Toast.LENGTH_LONG).show()
                }
                runCatching { manager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
                runCatching { manager.stopServiceDiscovery(this) }
            }
        }
        discoveryListener = listener
        renderPage()
        runCatching { manager.discoverServices("_home-assistant._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure {
                discoveryListener = null
                Toast.makeText(requireContext(), R.string.ha_discovery_error, Toast.LENGTH_LONG).show()
            }
    }

    private fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { nsd?.stopServiceDiscovery(listener) }
    }

    private fun button(label: String, click: () -> Unit): Button = Button(requireContext()).apply {
        text = label
        isAllCaps = false
        isFocusable = true
        setTextColor(requireContext().getColor(R.color.text_primary))
        background = AppCompatResources.getDrawable(requireContext(), R.drawable.btn_service_secondary_selector)
        setOnClickListener { click() }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(requireContext()).apply {
        text = value
        textSize = size
        setTextColor(requireContext().getColor(color))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CATEGORY_ALL = "all"
        private const val CATEGORY_CONTROLS = "controls"
        private const val MAX_VISIBLE_ENTITIES = 120
        private val CONTROL_DOMAINS = setOf("light", "switch", "input_boolean", "fan", "siren", "automation", "script", "scene", "button", "cover", "lock", "vacuum", "climate")
        private val SENSOR_DOMAINS = setOf("sensor", "binary_sensor", "weather", "sun", "device_tracker", "person")
    }
}

package com.phairplay.ui

import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import com.phairplay.service.ServiceController
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.phairplay.BuildConfig
import com.phairplay.R
import com.phairplay.settings.AppSettings
import com.phairplay.settings.AirPlayDeviceMode
import com.phairplay.settings.SettingsRepository
import com.phairplay.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SettingsFragment — Settings screen for PhairPlay.
 *
 * WHY: Centralizes all user-configurable options in one screen. By separating
 * settings into their own Fragment, we keep MainActivity lean and make it easy
 * to navigate to/from settings via the nav panel.
 *
 * HOW: Reads current settings from [SettingsRepository] and populates the UI.
 * Each toggle/row saves immediately when changed (no "Save" button needed).
 * Settings changes take effect on the next service restart.
 *
 * Navigation: accessed via the "Settings" item in MainActivity's nav panel.
 */
class SettingsFragment : Fragment() {

    private val calendarPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            setToggle(rowWidgetAgenda, true)
            save { it.copy(nowPlayingAgendaEnabled = true) }
        } else {
            setToggle(rowWidgetAgenda, false)
            Toast.makeText(requireContext(), R.string.setting_widget_agenda_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private lateinit var settingsRepository: SettingsRepository

    // Section header TextViews — set via include layout tag IDs
    private lateinit var headerDisplay: TextView
    private lateinit var headerProtocols: TextView
    private lateinit var headerAirPlay: TextView
    private lateinit var headerService: TextView
    private lateinit var headerDeveloper: TextView
    private lateinit var headerAbout: TextView

    // Settings rows
    private lateinit var rowDisplayName: LinearLayout
    private lateinit var textDisplayNameValue: TextView
    private lateinit var rowAirPlay: View
    private lateinit var rowMiracast: View
    private lateinit var rowCast: View
    private lateinit var rowMirrorAudio: View
    private lateinit var rowDeviceMode: View
    private lateinit var seekAudioSync: SeekBar
    private lateinit var textAudioSyncValue: TextView
    private lateinit var textDeviceModeValue: TextView
    private lateinit var rowPinAuth: View
    private lateinit var rowStartOnBoot: View
    private lateinit var rowDebugOverlay: View
    private lateinit var rowForceHighRes: View
    private lateinit var rowAudioScreenOff: View
    private lateinit var rowWidgetClock: View
    private lateinit var rowWidgetDate: View
    private lateinit var rowWidgetAgenda: View
    private lateinit var rowWidgetWeather: View
    private lateinit var rowWidgetStyle: View
    private lateinit var rowWeatherCity: View
    private lateinit var rowBedsideClockKeepOn: View
    private lateinit var rowBedsideClockDim: View
    private lateinit var rowBedsideClockSeconds: View
    private lateinit var textWidgetStyleValue: TextView
    private lateinit var textWeatherCityValue: TextView
    private lateinit var rowAudioScreenTimeout: View
    private lateinit var textAudioScreenTimeoutValue: TextView
    private lateinit var textVersionValue: TextView
    private lateinit var rowReset: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsRepository = SettingsRepository(requireContext())
        bindViews(view)
        setSectionTitles()
        setRowLabels()
        loadAndPopulate()
    }

    // ─── View Binding ────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        // Each header is an <include> of settings_section_header.xml (a bare
        // TextView). The include's android:id IS the TextView's id, so look it up
        // directly — no nested lookup.
        headerDisplay   = view.findViewById(R.id.header_display)
        headerProtocols = view.findViewById(R.id.header_protocols)
        headerAirPlay   = view.findViewById(R.id.header_airplay)
        headerService   = view.findViewById(R.id.header_service)
        headerDeveloper = view.findViewById(R.id.header_developer)
        headerAbout     = view.findViewById(R.id.header_about)

        rowDisplayName      = view.findViewById(R.id.row_display_name)
        textDisplayNameValue = view.findViewById(R.id.text_display_name_value)
        rowAirPlay          = view.findViewById(R.id.row_airplay)
        rowMiracast         = view.findViewById(R.id.row_miracast)
        rowCast             = view.findViewById(R.id.row_cast)
        rowMirrorAudio      = view.findViewById(R.id.row_mirror_audio)
        rowDeviceMode       = view.findViewById(R.id.row_device_mode)
        seekAudioSync       = view.findViewById(R.id.seek_audio_sync)
        textAudioSyncValue  = view.findViewById(R.id.text_audio_sync_value)
        textDeviceModeValue = view.findViewById(R.id.text_device_mode_value)
        rowPinAuth          = view.findViewById(R.id.row_pin_auth)
        rowStartOnBoot      = view.findViewById(R.id.row_start_on_boot)
        rowDebugOverlay     = view.findViewById(R.id.row_debug_overlay)
        rowForceHighRes     = view.findViewById(R.id.row_force_high_res)
        rowAudioScreenOff   = view.findViewById(R.id.row_audio_screen_off)
        rowWidgetClock = view.findViewById(R.id.row_widget_clock)
        rowWidgetDate = view.findViewById(R.id.row_widget_date)
        rowWidgetAgenda = view.findViewById(R.id.row_widget_agenda)
        rowWidgetWeather = view.findViewById(R.id.row_widget_weather)
        rowWidgetStyle = view.findViewById(R.id.row_widget_style)
        rowWeatherCity = view.findViewById(R.id.row_weather_city)
        rowBedsideClockKeepOn = view.findViewById(R.id.row_bedside_clock_keep_on)
        rowBedsideClockDim = view.findViewById(R.id.row_bedside_clock_dim)
        rowBedsideClockSeconds = view.findViewById(R.id.row_bedside_clock_seconds)
        textWidgetStyleValue = view.findViewById(R.id.text_widget_style_value)
        textWeatherCityValue = view.findViewById(R.id.text_weather_city_value)
        rowAudioScreenTimeout = view.findViewById(R.id.row_audio_screen_timeout)
        textAudioScreenTimeoutValue = view.findViewById(R.id.text_audio_screen_timeout_value)
        textVersionValue    = view.findViewById(R.id.text_version_value)
        rowReset            = view.findViewById(R.id.row_reset)
    }

    /** Sets all section header titles from string resources. */
    private fun setSectionTitles() {
        headerDisplay.setText(R.string.settings_section_display)
        headerProtocols.setText(R.string.settings_section_protocols)
        headerAirPlay.setText(R.string.settings_section_airplay)
        headerService.setText(R.string.settings_section_service)
        headerDeveloper.setText(R.string.settings_section_developer)
        headerAbout.setText(R.string.settings_section_about)
    }

    /** Sets all row labels and subtitles from string resources. */
    private fun setRowLabels() {
        configureToggleRow(rowAirPlay,      R.string.setting_airplay_enabled,    R.string.setting_airplay_subtitle)
        configureToggleRow(rowMiracast,     R.string.setting_miracast_enabled,   R.string.setting_miracast_subtitle)
        configureToggleRow(rowCast,         R.string.setting_cast_enabled,       R.string.setting_cast_subtitle)
        configureToggleRow(rowMirrorAudio,  R.string.setting_mirror_audio,       R.string.setting_mirror_audio_subtitle)
        configureToggleRow(rowPinAuth,      R.string.setting_pin_auth,           R.string.setting_pin_auth_subtitle)
        configureToggleRow(rowStartOnBoot,  R.string.setting_start_on_boot,      0)
        configureToggleRow(rowDebugOverlay, R.string.setting_debug_overlay,      R.string.setting_debug_overlay_subtitle)
        configureToggleRow(rowForceHighRes, R.string.setting_force_high_res,      R.string.setting_force_high_res_subtitle)
        configureToggleRow(rowAudioScreenOff, R.string.setting_audio_screen_off, R.string.setting_audio_screen_off_subtitle)
        configureToggleRow(rowWidgetClock, R.string.setting_widget_clock, 0)
        configureToggleRow(rowWidgetDate, R.string.setting_widget_date, 0)
        configureToggleRow(rowWidgetAgenda, R.string.setting_widget_agenda, R.string.setting_widget_agenda_subtitle)
        configureToggleRow(rowWidgetWeather, R.string.setting_widget_weather, 0)
        configureToggleRow(rowBedsideClockKeepOn, R.string.setting_bedside_keep_on, R.string.setting_bedside_keep_on_subtitle)
        configureToggleRow(rowBedsideClockDim, R.string.setting_bedside_dim, R.string.setting_bedside_dim_subtitle)
        configureToggleRow(rowBedsideClockSeconds, R.string.setting_bedside_seconds, R.string.setting_bedside_seconds_subtitle)

        textVersionValue.text = BuildConfig.VERSION_NAME
    }

    /**
     * Sets the label and optional subtitle on a toggle row view.
     *
     * @param row       The row view (from settings_toggle_row.xml)
     * @param labelRes  String resource for the main label
     * @param subtitleRes String resource for the subtitle, or 0 to hide it
     */
    private fun configureToggleRow(row: View, labelRes: Int, subtitleRes: Int) {
        row.findViewById<TextView>(R.id.text_setting_label)?.setText(labelRes)
        val subtitle = row.findViewById<TextView>(R.id.text_setting_subtitle)
        if (subtitleRes != 0) {
            subtitle?.setText(subtitleRes)
            subtitle?.visibility = View.VISIBLE
        } else {
            subtitle?.visibility = View.GONE
        }
    }

    // ─── Settings Load & Save ────────────────────────────────────────────────

    /**
     * Loads the current settings and populates the UI.
     * Then sets up click/toggle listeners for each row.
     */
    private fun loadAndPopulate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            populateUI(settings)
            setupListeners()
        }
    }

    /** Populates all UI elements with values from [settings]. */
    private fun populateUI(settings: AppSettings) {
        textDisplayNameValue.text = settings.effectiveDisplayName.ifEmpty {
            getString(R.string.setting_display_name_system_default)
        }
        setToggle(rowAirPlay,      settings.airPlayEnabled)
        setToggle(rowMiracast,     settings.miracastEnabled)
        setToggle(rowCast,         settings.castEnabled)
        setToggle(rowMirrorAudio,  settings.mirrorAudioEnabled)
        textDeviceModeValue.text = getString(if (settings.deviceMode == AirPlayDeviceMode.HOMEPOD) R.string.device_mode_homepod else R.string.device_mode_apple_tv)
        setToggle(rowPinAuth,      settings.airPlayPinAuthEnabled)
        setToggle(rowStartOnBoot,  settings.startOnBoot)
        setToggle(rowDebugOverlay, settings.showDebugOverlay)
        setToggle(rowForceHighRes, settings.forceHighResolution)
        setToggle(rowAudioScreenOff, settings.audioScreenOffEnabled)
        setToggle(rowWidgetClock, settings.nowPlayingClockEnabled)
        setToggle(rowWidgetDate, settings.nowPlayingDateEnabled)
        setToggle(rowWidgetAgenda, settings.nowPlayingAgendaEnabled)
        setToggle(rowWidgetWeather, settings.nowPlayingWeatherEnabled)
        setToggle(rowBedsideClockKeepOn, settings.bedsideClockKeepScreenOn)
        setToggle(rowBedsideClockDim, settings.bedsideClockDimScreen)
        setToggle(rowBedsideClockSeconds, settings.bedsideClockShowSeconds)
        textWidgetStyleValue.setText(if (settings.nowPlayingWidgetStyle == "CARD") R.string.setting_widget_style_card else R.string.setting_widget_style_minimal)
        textWeatherCityValue.text = settings.weatherCity.ifBlank { getString(R.string.setting_weather_not_set) }
        textAudioScreenTimeoutValue.text = getString(R.string.setting_audio_screen_timeout_value, settings.audioScreenOffTimeoutMinutes)
        rowAudioScreenTimeout.isEnabled = settings.audioScreenOffEnabled
        rowAudioScreenTimeout.alpha = if (settings.audioScreenOffEnabled) 1f else 0.45f
        seekAudioSync.progress = settings.audioSyncOffsetMs.coerceIn(-250, 250) + 250
        updateAudioSyncLabel(settings.audioSyncOffsetMs)
    }

    private fun setToggle(row: View, value: Boolean) {
        row.findViewById<SwitchCompat>(R.id.switch_setting)?.isChecked = value
    }

    /**
     * Sets up click and toggle listeners for all settings rows.
     * Each listener immediately persists the change via [SettingsRepository.update].
     *
     * No "Save" button is needed — settings are saved on every interaction.
     * A restart prompt is shown after protocol-affecting changes.
     */
    private fun setupListeners() {
        rowDisplayName.setOnClickListener { showDisplayNameDialog() }
        rowWidgetStyle.setOnClickListener { showWidgetStyleDialog() }
        rowWeatherCity.setOnClickListener { showWeatherCityDialog() }
        rowDeviceMode.setOnClickListener { showDeviceModeDialog() }
        rowAudioScreenTimeout.setOnClickListener { showAudioScreenTimeoutDialog() }
        seekAudioSync.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) updateAudioSyncLabel(progress - 250)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val offsetMs = ((seekBar?.progress ?: 250) - 250).coerceIn(-250, 250)
                save { it.copy(audioSyncOffsetMs = offsetMs) }
                Logger.i("Audio sync offset saved: ${offsetMs}ms (negative means earlier)")
            }
        })

        setToggleListener(rowAirPlay)      { enabled -> save { it.copy(airPlayEnabled = enabled) } }
        setToggleListener(rowMiracast)     { enabled -> save { it.copy(miracastEnabled = enabled) } }
        setToggleListener(rowCast)         { enabled -> save { it.copy(castEnabled = enabled) } }
        setToggleListener(rowMirrorAudio)  { enabled -> saveAndRestart { it.copy(mirrorAudioEnabled = enabled) } }
        setToggleListener(rowPinAuth)      { enabled -> saveAndRestart { it.copy(airPlayPinAuthEnabled = enabled) } }
        setToggleListener(rowStartOnBoot)  { enabled -> save { it.copy(startOnBoot = enabled) } }
        setToggleListener(rowDebugOverlay) { enabled -> save { it.copy(showDebugOverlay = enabled) } }
        setToggleListener(rowForceHighRes) { enabled -> save { it.copy(forceHighResolution = enabled) } }
        setToggleListener(rowAudioScreenOff) { enabled ->
            save { it.copy(audioScreenOffEnabled = enabled) }
            rowAudioScreenTimeout.isEnabled = enabled
            rowAudioScreenTimeout.alpha = if (enabled) 1f else 0.45f
        }
        setToggleListener(rowWidgetClock) { enabled -> save { it.copy(nowPlayingClockEnabled = enabled) } }
        setToggleListener(rowWidgetDate) { enabled -> save { it.copy(nowPlayingDateEnabled = enabled) } }
        rowWidgetAgenda.setOnClickListener {
            val switch = rowWidgetAgenda.findViewById<SwitchCompat>(R.id.switch_setting) ?: return@setOnClickListener
            if (switch.isChecked) {
                setToggle(rowWidgetAgenda, false)
                save { it.copy(nowPlayingAgendaEnabled = false) }
            } else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                setToggle(rowWidgetAgenda, true)
                save { it.copy(nowPlayingAgendaEnabled = true) }
            } else {
                calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        }
        setToggleListener(rowWidgetWeather) { enabled -> save { it.copy(nowPlayingWeatherEnabled = enabled) } }
        setToggleListener(rowBedsideClockKeepOn) { enabled -> save { it.copy(bedsideClockKeepScreenOn = enabled) } }
        setToggleListener(rowBedsideClockDim) { enabled -> save { it.copy(bedsideClockDimScreen = enabled) } }
        setToggleListener(rowBedsideClockSeconds) { enabled -> save { it.copy(bedsideClockShowSeconds = enabled) } }

        rowReset.setOnClickListener { resetSettings() }
    }

    private fun showWidgetStyleDialog() {
        val labels = arrayOf(getString(R.string.setting_widget_style_minimal), getString(R.string.setting_widget_style_card))
        AlertDialog.Builder(requireContext()).setTitle(R.string.setting_widget_style)
            .setSingleChoiceItems(labels, if (textWidgetStyleValue.text == labels[1]) 1 else 0) { dialog, which ->
                val style = if (which == 1) "CARD" else "MINIMAL"
                textWidgetStyleValue.text = labels[which]
                save { it.copy(nowPlayingWidgetStyle = style) }; dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showWeatherCityDialog() {
        val input = EditText(requireContext()).apply { setText(if (textWeatherCityValue.text == getString(R.string.setting_weather_not_set)) "" else textWeatherCityValue.text); hint = getString(R.string.setting_weather_city_hint); inputType = InputType.TYPE_CLASS_TEXT }
        AlertDialog.Builder(requireContext()).setTitle(R.string.setting_weather_city).setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> val city = input.text.toString().trim(); textWeatherCityValue.text = city.ifBlank { getString(R.string.setting_weather_not_set) }; save { it.copy(weatherCity = city) } }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showDeviceModeDialog() {
        val labels = arrayOf(getString(R.string.device_mode_homepod), getString(R.string.device_mode_apple_tv))
        val current = if (textDeviceModeValue.text == labels[0]) 0 else 1
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_device_mode)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val mode = if (which == 0) AirPlayDeviceMode.HOMEPOD else AirPlayDeviceMode.APPLE_TV
                textDeviceModeValue.text = labels[which]
                dialog.dismiss()
                saveAndRestart { it.copy(deviceMode = mode) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setToggleListener(row: View, onChanged: (Boolean) -> Unit) {
        // The whole row is clickable (better TV UX than just the Switch widget)
        row.setOnClickListener {
            val switch = row.findViewById<SwitchCompat>(R.id.switch_setting) ?: return@setOnClickListener
            val newValue = !switch.isChecked
            switch.isChecked = newValue
            onChanged(newValue)
        }
    }

    private fun updateAudioSyncLabel(offsetMs: Int) {
        textAudioSyncValue.text = when {
            offsetMs < 0 -> getString(R.string.setting_audio_sync_early, -offsetMs)
            offsetMs > 0 -> getString(R.string.setting_audio_sync_late, offsetMs)
            else -> getString(R.string.setting_audio_sync_neutral)
        }
    }

    /**
     * Saves an updated [AppSettings] via the repository.
     * Runs in a coroutine so it doesn't block the UI thread.
     *
     * @param transform A function that takes the current settings and returns updated settings.
     */
    private fun save(transform: (AppSettings) -> AppSettings) {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.update(transform)
            Logger.d("Settings saved")
        }
    }

    /**
     * Saves a setting that the AirPlay receiver only reads at startup (mirror-audio, PIN auth), then
     * restarts the service so the change applies immediately instead of on the next manual restart.
     */
    private fun saveAndRestart(transform: (AppSettings) -> AppSettings) {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.update(transform)
            ServiceController.restart(requireContext())
            Logger.i("Settings saved — restarting receivers to apply")
        }
    }

    /**
     * Shows a dialog allowing the user to edit the AirPlay display name.
     *
     * WHY: The display name is what appears in the macOS/iOS AirPlay picker.
     * Changing it is infrequent but important for multi-TV households.
     *
     * TV UX notes:
     * - The EditText is pre-filled with the current name (empty = system default)
     * - Max length is enforced to [AppSettings.DISPLAY_NAME_MAX_LENGTH] (63 chars, mDNS limit)
     * - "OK" saves the new name; "Reset to default" clears to "" (system name); "Cancel" = no-op
     * - Name trimming is applied on save — pure-whitespace names are treated as blank
     *
     * Collision detection: Android's NsdManager automatically appends " (2)", " (3)" etc. if
     * another device on the network already uses the same mDNS name. This is transparent to
     * the user at save-time; the actual registered name is logged at registration.
     */
    private fun showDisplayNameDialog() {
        val currentName = viewLifecycleOwner.lifecycleScope.run {
            // Read directly from the displayed value (already loaded)
            val displayed = textDisplayNameValue.text?.toString() ?: ""
            if (displayed == getString(R.string.setting_display_name_system_default)) "" else displayed
        }

        val editText = EditText(requireContext()).apply {
            setText(currentName)
            hint = getString(R.string.setting_display_name_dialog_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(AppSettings.DISPLAY_NAME_MAX_LENGTH))
            setSingleLine(true)
            // Move cursor to end so user can append rather than overwrite
            setSelection(currentName.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_display_name)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = editText.text?.toString()?.trim() ?: ""
                save { it.copy(displayName = newName) }
                textDisplayNameValue.text = newName.ifEmpty {
                    getString(R.string.setting_display_name_system_default)
                }
                Logger.i("Display name updated to: '${newName.ifEmpty { "(system default)" }}'")
            }
            .setNeutralButton(R.string.setting_display_name_reset) { _, _ ->
                save { it.copy(displayName = "") }
                textDisplayNameValue.text = getString(R.string.setting_display_name_system_default)
                Logger.i("Display name reset to system default")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAudioScreenTimeoutDialog() {
        val minutes = intArrayOf(1, 2, 3, 5, 10, 15, 30)
        viewLifecycleOwner.lifecycleScope.launch {
            val current = settingsRepository.settingsFlow.first().audioScreenOffTimeoutMinutes
            val labels = minutes.map { getString(R.string.setting_audio_screen_timeout_value, it) }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.setting_audio_screen_timeout_dialog)
                .setSingleChoiceItems(labels, minutes.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                    val chosen = minutes[which]
                    save { it.copy(audioScreenOffTimeoutMinutes = chosen) }
                    textAudioScreenTimeoutValue.text = getString(R.string.setting_audio_screen_timeout_value, chosen)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun resetSettings() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_reset_defaults)
            .setMessage(R.string.setting_reset_defaults_message)
            .setPositiveButton(R.string.setting_reset_confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    settingsRepository.resetToDefaults()
                    populateUI(AppSettings.DEFAULT)
                    Logger.i("Settings reset to defaults")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

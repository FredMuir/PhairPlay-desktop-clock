package com.phairplay.settings

/**
 * AppSettings — Immutable data model for all user-configurable PhairPlay settings.
 *
 * WHY: Centralizing all settings in one data class gives a single source of truth.
 * Any component that needs a setting reads from here. Any component that changes a
 * setting creates a new copy via [copy]. This makes settings changes explicit and
 * easy to test.
 *
 * HOW: Settings are persisted via [SettingsRepository]. Get the current settings
 * from [SettingsRepository.settingsFlow] and update them via [SettingsRepository.update].
 *
 * Example:
 *   // Read settings
 *   val settings = settingsRepository.settingsFlow.first()
 *   if (settings.airPlayEnabled) { ... }
 *
 *   // Change a setting
 *   settingsRepository.update { it.copy(displayName = "My TV") }
 */
data class AppSettings(

    /** Device identity advertised to AirPlay senders. */
    val deviceMode: AirPlayDeviceMode = AirPlayDeviceMode.HOMEPOD,

    // ─── Display ───────────────────────────────────────────────────────────
    /**
     * The name shown in sender pickers (AirPlay menu on Mac, Cast picker in Chrome, etc.).
     * If empty, the Android device name is used as a fallback.
     * Validated: max 63 characters, must not be blank after trimming.
     */
    val displayName: String = "",

    // ─── Protocols ─────────────────────────────────────────────────────────
    /**
     * Whether the AirPlay 2 receiver is enabled.
     * When false: mDNS advertisement is stopped, RTSP port 7000 is not opened.
     */
    val airPlayEnabled: Boolean = true,

    /**
     * Whether the Miracast (Wi-Fi Display) receiver is enabled.
     * When false: Wi-Fi P2P service advertisement is stopped.
     */
    val miracastEnabled: Boolean = true,

    /**
     * Whether the Google Cast receiver is enabled.
     * On Fire TV (no Google Play Services), this is ignored.
     * When false: Cast SDK is not initialized.
     */
    val castEnabled: Boolean = true,

    // ─── AirPlay specific ──────────────────────────────────────────────────
    /**
     * Whether AirPlay connections require PIN authentication.
     * When true: the user must confirm a 4-digit PIN shown on screen.
     * When false (default): any nearby Mac can connect without confirmation.
     */
    val airPlayPinAuthEnabled: Boolean = false,

    /** Allow legacy DACP reverse control as a compatibility fallback. Disabled by default. */
    val legacyDacpControlEnabled: Boolean = false,

    /** Show and send AirPlay sender transport controls. Disabled to protect default playback. */
    val airPlayRemoteControlsEnabled: Boolean = false,

    // ─── Service behavior ──────────────────────────────────────────────────
    /**
     * Whether PhairPlayService starts automatically on device boot.
     * Requires the RECEIVE_BOOT_COMPLETED permission to be effective.
     */
    val startOnBoot: Boolean = false,

    // ─── Developer / Debug ─────────────────────────────────────────────────
    /**
     * Overlays a debug HUD on the streaming screen showing:
     * - Current frames per second
     * - Estimated A/V latency (ms)
     * - Active protocol name
     * Only useful for development and testing.
     */
    val showDebugOverlay: Boolean = false,

    // ─── Video ─────────────────────────────────────────────────────────────
    /**
     * When true, advertise a higher mirroring resolution (1440p) in the AirPlay `/info`
     * `displays` record so macOS renders/encodes the mirror at 2560×1440 instead of 1920×1080.
     * The TV surface is 1080p, so frames are downscaled (sharper text via supersampling) at
     * the cost of more decode work — heavier on low-end SoCs.
     */
    val forceHighResolution: Boolean = false,

    /**
     * When true, accept the mirroring audio stream (type 96, AAC-ELD). EXPERIMENTAL: macOS uses
     * realtime audio clock-sync (RTCP) that isn't fully implemented yet, which can make macOS tear
     * the whole mirror session down after a couple of seconds — so this defaults OFF to keep video
     * mirroring rock-solid. Turn on to experiment with audio.
     */
    val mirrorAudioEnabled: Boolean = true,

    /** Signed local audio output compensation in milliseconds; negative starts earlier. */
    val audioSyncOffsetMs: Int = 0,

    /** Let the display sleep during audio-only AirPlay after this much user inactivity. */
    val audioScreenOffEnabled: Boolean = true,
    val audioScreenOffTimeoutMinutes: Int = 1,
    val nowPlayingClockEnabled: Boolean = true,
    val nowPlayingDateEnabled: Boolean = true,
    val nowPlayingAgendaEnabled: Boolean = false,
    val nowPlayingWeatherEnabled: Boolean = false,
    val nowPlayingWidgetStyle: String = "MINIMAL",
    val weatherCity: String = "",
    val bedsideClockKeepScreenOn: Boolean = true,
    val bedsideClockDimScreen: Boolean = true,
    val bedsideClockShowSeconds: Boolean = false,
    val clockBatteryEnabled: Boolean = false,
    val clockNextAlarmEnabled: Boolean = false,
    val homeAssistantUrl: String = "",
    val homeAssistantEntities: String = "",
    val homeAssistantCalendars: String = "",
    val homeAssistantTodos: String = "",
    val homeAssistantEnabled: Boolean = false,
    val homeAssistantCalendarEnabled: Boolean = false,
    val homeAssistantTodoEnabled: Boolean = false,
    val iCloudEmail: String = "",
    val iCloudCalendarHrefs: String = "",
    val iCloudCalendarEnabled: Boolean = false
) {

    /** Advertised mirroring display size: 2560×1440 when [forceHighResolution], else 1920×1080. */
    val mirrorWidth: Int get() = if (forceHighResolution) 2560 else 1920
    val mirrorHeight: Int get() = if (forceHighResolution) 1440 else 1080

    /**
     * Returns the validated, trimmed display name.
     * If the stored name is blank, returns an empty string so callers
     * can fall back to the system device name.
     */
    val effectiveDisplayName: String
        get() = displayName.trim()

    /**
     * Returns true if at least one protocol is enabled.
     * If all three are disabled, the service has nothing to do.
     */
    val anyProtocolEnabled: Boolean
        get() = airPlayEnabled || miracastEnabled || castEnabled

    companion object {
        /** The default settings instance used on first launch. */
        val DEFAULT = AppSettings()

        /** Maximum allowed length for the display name (mDNS limit). */
        const val DISPLAY_NAME_MAX_LENGTH = 63
        const val AUDIO_SYNC_OFFSET_LIMIT_MS = 250
    }
}

enum class AirPlayDeviceMode(val model: String, val displayLabel: String, val features: Long, val sourceVersion: String) {
    // Profile values observed from the two HomePod mini emulators on the user's LAN.
    HOMEPOD("AudioAccessory5,1", "HomePod mini", 0x1C340405C4A00L, "377.40.00"),
    APPLE_TV("AppleTV5,3", "Apple TV", 0x1E5A7FFFF7L, "220.68");

    val featuresTxt: String
        get() = "0x${(features and 0xffffffffL).toString(16).uppercase()},0x${(features ushr 32).toString(16).uppercase()}"

    companion object {
        fun fromStored(value: String?): AirPlayDeviceMode =
            entries.firstOrNull { it.name == value } ?: HOMEPOD
    }
}

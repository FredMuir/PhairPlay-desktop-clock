package com.phairplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.phairplay.service.PhairPlayService
import com.phairplay.service.PhotoFrame
import com.phairplay.service.ProtocolState
import com.phairplay.service.ServiceController
import com.phairplay.airplay.NowPlayingInfo
import com.phairplay.ui.HomeFragment
import com.phairplay.ui.NowPlayingScreen
import com.phairplay.ui.PhotoScreen
import com.phairplay.ui.PinScreen
import com.phairplay.ui.SettingsFragment
import com.phairplay.ui.DevicesFragment
import com.phairplay.ui.StreamingScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * MainActivity — The single Activity hosting PhairPlay's navigation and fragments.
 *
 * WHY: PhairPlay uses a single-Activity architecture with Fragment-based navigation.
 * This is the recommended pattern for Android TV apps: one Activity with swappable
 * Fragments avoids the overhead of Activity transitions and keeps the Leanback
 * launcher integration simple.
 *
 * Layout structure:
 *   ┌─ Nav Panel ──┬─ Content (FrameLayout) ─────────────────┐
 *   │  Home        │  HomeFragment  OR  SettingsFragment      │
 *   │  Settings    │                                          │
 *   └──────────────┴──────────────────────────────────────────┘
 *   [streaming_container] — full-screen overlay (GONE when idle)
 *
 * HOW: D-pad left/right navigation between nav panel and content area.
 * The nav panel items switch fragments. PhairPlayService is started on app launch.
 */
class MainActivity : AppCompatActivity() {

    private val mediaVolumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { service?.publishSystemMediaVolume() }
    }
    private val mediaVolumeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", android.media.AudioManager.STREAM_MUSIC) == android.media.AudioManager.STREAM_MUSIC) {
                service?.publishSystemMediaVolume()
            }
        }
    }

    // UI references
    private lateinit var navItemHome: TextView
    private lateinit var navItemSettings: TextView
    private lateinit var navItemDevices: TextView
    private lateinit var navItemNowPlaying: TextView
    private lateinit var contentContainer: FrameLayout
    private lateinit var streamingContainer: FrameLayout

    // The SurfaceView for full-screen video output
    private lateinit var streamingScreen: StreamingScreen
    private lateinit var photoScreen: PhotoScreen
    private lateinit var nowPlayingScreen: NowPlayingScreen
    private lateinit var pinScreen: PinScreen

    // Service binding — gives access to state flows for showing/hiding the streaming overlay
    private var service: PhairPlayService? = null
    private var isBound = false
    private var currentAirPlayState = ProtocolState.DISABLED
    private var currentPhotoFrame: PhotoFrame? = null
    private var currentNowPlaying: NowPlayingInfo? = null
    private var currentPin: String? = null
    private var nowPlayingDismissed = false
    private var audioScreenOffEnabled = true
    private var audioScreenOffTimeoutMinutes = 1
    private var nowPlayingWasVisible = false
    private var audioScreenSleepExpired = false
    private var bedsideClockRequested = false
    private var bedsideKeepScreenOn = true
    private var bedsideDimScreen = true
    private var previousBrightness: Float? = null
    private var bedsideImmersive = false
    private val screenHandler = Handler(Looper.getMainLooper())
    private val allowAudioScreenOff = Runnable {
        if (currentNowPlaying != null && audioScreenOffEnabled && nowPlayingScreen.visibility == View.VISIBLE) {
            audioScreenSleepExpired = true
            setKeepScreenOn(false)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? PhairPlayService.LocalBinder)?.getService()
            isBound = true
            Timber.d("MainActivity: bound to PhairPlayService")

            // Wire the streaming Surface so the service can pass it to VideoDecoder
            service?.setVideoSurfaceProvider { getVideoSurface() }
            service?.publishSystemMediaVolume()

            // Show/hide the full-screen overlay for video streams and photos.
            observeOverlayState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            Timber.d("MainActivity: unbound from PhairPlayService")
        }
    }

    // Currently selected nav item index (0 = Home, 1 = Settings)
    private var selectedNavIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC)
        setContentView(R.layout.activity_main)

        Timber.d("MainActivity created")
        bindViews()
        setupOverlayScreens()
        setupNavigation()

        // Show HomeFragment on first launch
        if (savedInstanceState == null) {
            navigateTo(HomeFragment(), navItemHome)
        }

        // Start the service immediately so it's running before any sender discovers us
        ServiceController.start(this)

        // Android 13+ requires an explicit runtime grant for POST_NOTIFICATIONS
        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        // Bind so we can observe StateFlows and supply the video Surface
        val intent = Intent(this, PhairPlayService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, mediaVolumeObserver)
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(mediaVolumeReceiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(mediaVolumeReceiver, filter)
        service?.publishSystemMediaVolume()
    }

    override fun onPause() {
        runCatching { contentResolver.unregisterContentObserver(mediaVolumeObserver) }
        runCatching { unregisterReceiver(mediaVolumeReceiver) }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Clear surface reference before unbinding to avoid holding a dead Surface
        service?.setVideoSurfaceProvider { null }
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // A user-initiated exit (Back out of the app) should end any active mirror — closing the
        // service stops the receiver, which drops the RTSP connection so the sender stops mirroring
        // too. isFinishing distinguishes a real exit from a config-change recreation (where the
        // service must keep running). Backgrounding via Home goes through onStop only (no destroy),
        // so the receiver keeps advertising for a quick return.
        if (isFinishing) {
            Timber.d("MainActivity finishing — stopping service so mirroring doesn't linger")
            ServiceController.stop(this)
        } else {
            Timber.d("MainActivity destroyed (recreation) — leaving service running")
        }
    }

    // ─── View Setup ──────────────────────────────────────────────────────────

    private fun bindViews() {
        navItemHome       = findViewById(R.id.nav_item_home)
        navItemSettings   = findViewById(R.id.nav_item_settings)
        navItemDevices    = findViewById(R.id.nav_item_devices)
        navItemNowPlaying = findViewById(R.id.nav_item_now_playing)
        contentContainer  = findViewById(R.id.content_container)
        streamingContainer = findViewById(R.id.streaming_container)

        // The TV header has a brand label plus four navigation items. On narrow phone
        // widths that row exceeds the available space and silently clips the last items.
        // Keep the full TV treatment on tablets/TVs and reclaim that space on phones.
        val widthDp = resources.configuration.screenWidthDp
        if (widthDp < 600) {
            findViewById<TextView>(R.id.nav_app_name).visibility = View.GONE
            listOf(navItemHome, navItemSettings, navItemDevices, navItemNowPlaying).forEach {
                val verticalPadding = it.paddingTop
                val compactPadding = (8 * resources.displayMetrics.density).toInt()
                it.setPaddingRelative(compactPadding, verticalPadding, compactPadding, it.paddingBottom)
            }
        }
    }

    /**
     * Creates the StreamingScreen (SurfaceView for video) and adds it to the
     * streaming_container. Created eagerly so the Surface is ready before streaming starts.
     */
    private fun setupOverlayScreens() {
        streamingScreen = StreamingScreen(this)
        photoScreen = PhotoScreen(this)
        nowPlayingScreen = NowPlayingScreen(this)
        nowPlayingScreen.setVolumeChangeHandler { volumeDb ->
            service?.setAirPlayOutputVolumeDb(volumeDb)
        }
        nowPlayingScreen.setReturnToAppHandler { dismissNowPlayingCard() }
        pinScreen = PinScreen(this)
        streamingContainer.addView(streamingScreen)
        streamingContainer.addView(photoScreen)
        streamingContainer.addView(nowPlayingScreen)
        streamingContainer.addView(pinScreen)
        photoScreen.visibility = View.GONE
        nowPlayingScreen.visibility = View.GONE
        pinScreen.visibility = View.GONE
    }

    /**
     * Sets up click listeners for the navigation panel items.
     * Also updates the visual selected state (text color) of the active item.
     */
    private fun setupNavigation() {
        navItemHome.setOnClickListener {
            if (selectedNavIndex != 0) {
                navigateTo(HomeFragment(), navItemHome)
            }
        }
        navItemSettings.setOnClickListener {
            if (selectedNavIndex != 1) {
                navigateTo(SettingsFragment(), navItemSettings)
            }
        }
        navItemDevices.setOnClickListener {
            if (selectedNavIndex != 2) navigateTo(DevicesFragment(), navItemDevices)
        }
        navItemNowPlaying.setOnClickListener {
            if (currentNowPlaying != null) {
                val info = currentNowPlaying!!
                nowPlayingDismissed = false
                showNowPlayingScreen(info)
            } else {
                showBedsideClockScreen()
            }
        }

        listOf(navItemHome, navItemSettings, navItemDevices, navItemNowPlaying).forEach { item ->
            item.setOnFocusChangeListener { view, _ ->
                val navItem = view as TextView
                navItem.setTextColor(getColor(
                    if (navItem.isSelected || navItem.hasFocus()) R.color.text_primary else R.color.nav_item_normal
                ))
            }
        }

        // Set initial selected state
        setNavSelected(navItemHome, true)
        setNavSelected(navItemSettings, false)
        setNavSelected(navItemDevices, false)
        setNavSelected(navItemNowPlaying, false)
    }

    /**
     * Replaces the content_container fragment with [fragment] and updates
     * the nav panel selection highlight.
     *
     * @param fragment  The Fragment to show in the content area.
     * @param navItem   The nav panel TextView that was clicked (for highlight update).
     */
    private fun navigateTo(fragment: Fragment, navItem: TextView) {
        // Update nav highlight
        setNavSelected(navItemHome, navItem == navItemHome)
        setNavSelected(navItemSettings, navItem == navItemSettings)
        setNavSelected(navItemDevices, navItem == navItemDevices)
        setNavSelected(navItemNowPlaying, navItem == navItemNowPlaying)
        selectedNavIndex = when (navItem) { navItemHome -> 0; navItemSettings -> 1; navItemDevices -> 2; else -> 3 }

        // Replace fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .commit()
    }

    /**
     * Updates the nav panel item's visual state.
     *
     * @param item     The nav item TextView.
     * @param selected True if this item is currently active.
     */
    private fun setNavSelected(item: TextView, selected: Boolean) {
        item.isSelected = selected
        item.setTextColor(
            getColor(if (selected || item.hasFocus()) R.color.text_primary else R.color.nav_item_normal)
        )
    }

    /**
     * Shows the full-screen streaming overlay (called by PhairPlayService
     * via a state update or broadcast when a stream becomes active).
     *
     * Hides the nav panel and content area to give the stream the full screen.
     */
    fun showStreamingScreen() {
        screenHandler.removeCallbacks(allowAudioScreenOff)
        nowPlayingWasVisible = false
        photoScreen.visibility = View.GONE
        nowPlayingScreen.visibility = View.GONE
        nowPlayingScreen.clear()
        pinScreen.visibility = View.GONE
        streamingScreen.visibility = View.VISIBLE
        streamingContainer.visibility = View.VISIBLE
        streamingContainer.bringToFront()
        setKeepScreenOn(true)
    }

    fun showPhotoScreen(photoFrame: PhotoFrame) {
        if (photoScreen.showPhoto(photoFrame.bytes)) {
            screenHandler.removeCallbacks(allowAudioScreenOff)
            nowPlayingWasVisible = false
            streamingScreen.visibility = View.GONE
            nowPlayingScreen.visibility = View.GONE
            pinScreen.visibility = View.GONE
            photoScreen.visibility = View.VISIBLE
            streamingContainer.visibility = View.VISIBLE
            streamingContainer.bringToFront()
            setKeepScreenOn(true)
        }
    }

    /** Shows the audio-only now-playing card (AirPlay audio with no video). */
    fun showNowPlayingScreen(info: NowPlayingInfo) {
        nowPlayingScreen.setBedsideClockMode(false)
        nowPlayingScreen.update(info)
        streamingScreen.visibility = View.GONE
        photoScreen.visibility = View.GONE
        pinScreen.visibility = View.GONE
        nowPlayingScreen.visibility = View.VISIBLE
        streamingContainer.visibility = View.VISIBLE
        streamingContainer.bringToFront()
        setImmersiveBars(true)
        if (!nowPlayingWasVisible) {
            nowPlayingWasVisible = true
            audioScreenSleepExpired = false
            scheduleAudioScreenOff()
        }
        if (!audioScreenOffEnabled) {
            screenHandler.removeCallbacks(allowAudioScreenOff)
            setKeepScreenOn(true)
        } else if (!audioScreenSleepExpired) {
            setKeepScreenOn(true)
        }
    }

    /**
     * Hides the streaming overlay and returns to the normal app UI.
     * Called when a stream ends.
     */
    fun hideStreamingScreen() {
        screenHandler.removeCallbacks(allowAudioScreenOff)
        nowPlayingWasVisible = false
        photoScreen.clearPhoto()
        photoScreen.visibility = View.GONE
        nowPlayingScreen.clear()
        nowPlayingScreen.visibility = View.GONE
        pinScreen.visibility = View.GONE
        streamingScreen.visibility = View.VISIBLE
        streamingContainer.visibility = View.GONE
        setKeepScreenOn(false)
        applyBedsideBrightness(false)
        setImmersiveBars(false)
    }

    private fun showBedsideClockScreen() {
        bedsideClockRequested = true
        nowPlayingDismissed = false
        screenHandler.removeCallbacks(allowAudioScreenOff)
        streamingScreen.visibility = View.GONE
        photoScreen.visibility = View.GONE
        pinScreen.visibility = View.GONE
        nowPlayingScreen.clear()
        nowPlayingScreen.setBedsideClockMode(true)
        nowPlayingScreen.visibility = View.VISIBLE
        streamingContainer.visibility = View.VISIBLE
        streamingContainer.bringToFront()
        setKeepScreenOn(bedsideKeepScreenOn)
        applyBedsideBrightness(true)
        setImmersiveBars(true)
    }

    private fun applyBedsideBrightness(enabled: Boolean) {
        val attrs = window.attributes
        if (enabled && bedsideDimScreen) {
            if (previousBrightness == null) previousBrightness = attrs.screenBrightness
            attrs.screenBrightness = 0.10f
            window.attributes = attrs
        } else if (previousBrightness != null) {
            attrs.screenBrightness = previousBrightness!!
            window.attributes = attrs
            previousBrightness = null
        }
    }

    private fun setImmersiveBars(enabled: Boolean) {
        if (bedsideImmersive == enabled) return
        bedsideImmersive = enabled
        if (enabled) window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        applySystemBarVisibility(enabled)
    }

    private fun applySystemBarVisibility(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                if (enabled) {
                    controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                } else controller.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (enabled) (
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            ) else View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && bedsideImmersive) applySystemBarVisibility(true)
    }

    /**
     * Holds the display awake for as long as something is actually being received.
     *
     * Without this the TV's own screensaver / power-saving timer fires straight through an
     * active session — mirroring simply stops being watchable after the timeout, which is
     * what every other casting app avoids. `FLAG_KEEP_SCREEN_ON` is the right instrument
     * rather than a `PowerManager` wake lock: it needs no permission, it is scoped to this
     * window, and Android drops it automatically if the activity goes away, so a crash or a
     * forgotten `false` can never leave the panel burning indefinitely.
     *
     * Set from every path that raises or lowers the overlay: mirroring, photos, and
     * audio-only now-playing all count as "in use".
     */
    private fun setKeepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        Timber.d("Keep-screen-on: $keepOn")
    }

    private fun scheduleAudioScreenOff() {
        screenHandler.removeCallbacks(allowAudioScreenOff)
        if (audioScreenOffEnabled) {
            audioScreenSleepExpired = false
            screenHandler.postDelayed(allowAudioScreenOff, audioScreenOffTimeoutMinutes * 60_000L)
        }
    }

    /** Restart the audio card idle countdown when the user interacts with the screen. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN && currentNowPlaying != null &&
            nowPlayingScreen.visibility == View.VISIBLE && audioScreenOffEnabled
        ) {
            audioScreenSleepExpired = false
            setKeepScreenOn(true)
            scheduleAudioScreenOff()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun dismissNowPlayingCard() {
        bedsideClockRequested = false
        nowPlayingDismissed = true
        hideStreamingScreen()
    }

    @Deprecated("Use the now-playing card's return button to return to the app")
    override fun onBackPressed() {
        if (nowPlayingScreen.visibility == View.VISIBLE) {
            dismissNowPlayingCard()
        } else if (supportFragmentManager.backStackEntryCount == 0) {
            // Back from the app's root returns to the launcher without stopping the receiver.
            moveTaskToBack(true)
        } else {
            super.onBackPressed()
        }
    }

    /** Returns the SurfaceView Surface for the VideoDecoder. */
    fun getVideoSurface() = streamingScreen.getSurface()

    /**
     * Routes TV-remote media keys to the AirPlay sender (DACP reverse control) while audio-only or a
     * stream is showing — so the remote can play/pause/skip what the Mac/iPhone is streaming. Returns
     * false for other keys so normal navigation is unaffected.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val overlayActive = currentNowPlaying != null || currentAirPlayState == ProtocolState.CONNECTED
        if (overlayActive) {
            val command = when (keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER -> com.phairplay.airplay.DacpClient.CMD_PLAY_PAUSE
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> com.phairplay.airplay.DacpClient.CMD_NEXT
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> com.phairplay.airplay.DacpClient.CMD_PREV
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> com.phairplay.airplay.DacpClient.CMD_FF
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> com.phairplay.airplay.DacpClient.CMD_REW
                else -> null
            }
            if (command != null) {
                service?.sendAirPlayRemoteCommand(command)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Requests POST_NOTIFICATIONS permission on Android 13+ (API 33+).
     * On older versions the permission is granted automatically with the manifest declaration.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_NOTIFICATIONS
                )
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_NOTIFICATIONS = 1001
    }

    // ─── Streaming overlay ────────────────────────────────────────────────────

    /**
     * Observes [PhairPlayService.airPlayState] and [PhairPlayService.photoFrame]
     * and shows the appropriate full-screen overlay.
     *
     * Called once after the service is bound. The coroutine is automatically cancelled
     * by [lifecycleScope] when the Activity stops.
     */
    private fun observeOverlayState() {
        val svc = service ?: return
        lifecycleScope.launch {
            svc.airPlayState.collectLatest { state ->
                currentAirPlayState = state
                if (state == ProtocolState.DISABLED && currentNowPlaying == null) nowPlayingDismissed = false
                updateOverlay()
            }
        }
        lifecycleScope.launch {
            svc.photoFrame.collectLatest { frame ->
                currentPhotoFrame = frame
                updateOverlay()
            }
        }
        lifecycleScope.launch {
            svc.nowPlaying.collectLatest { info ->
                currentNowPlaying = info
                if (info == null) nowPlayingDismissed = false
                updateOverlay()
            }
        }
        lifecycleScope.launch {
            com.phairplay.settings.SettingsRepository(this@MainActivity).settingsFlow.collectLatest { settings ->
                audioScreenOffEnabled = settings.audioScreenOffEnabled
                audioScreenOffTimeoutMinutes = settings.audioScreenOffTimeoutMinutes.coerceIn(1, 30)
                nowPlayingScreen.setWidgetSettings(settings.nowPlayingClockEnabled, settings.nowPlayingDateEnabled,
                    settings.nowPlayingAgendaEnabled, settings.nowPlayingWeatherEnabled, settings.weatherCity,
                    settings.nowPlayingWidgetStyle, settings.bedsideClockShowSeconds)
                bedsideKeepScreenOn = settings.bedsideClockKeepScreenOn
                bedsideDimScreen = settings.bedsideClockDimScreen
                if (bedsideClockRequested) {
                    setKeepScreenOn(bedsideKeepScreenOn)
                    applyBedsideBrightness(true)
                }
                if (currentNowPlaying != null && nowPlayingScreen.visibility == View.VISIBLE) {
                    if (audioScreenOffEnabled) scheduleAudioScreenOff()
                    else { screenHandler.removeCallbacks(allowAudioScreenOff); setKeepScreenOn(true) }
                }
            }
        }
        lifecycleScope.launch {
            svc.outputVolumeDb.collectLatest { volumeDb -> nowPlayingScreen.setVolumeDb(volumeDb) }
        }
        lifecycleScope.launch {
            svc.pairingPin.collectLatest { pin ->
                currentPin = pin
                updateOverlay()
            }
        }
    }

    private fun updateOverlay() {
        val photoFrame = currentPhotoFrame
        val nowPlaying = currentNowPlaying
        val pin = currentPin
        when {
            // PIN pairing (access control) happens before streaming — show the code over everything.
            pin != null -> showPinScreen(pin)
            // Audio-only AirPlay (system audio, Music, podcasts): show the now-playing card instead
            // of the black video surface. Set whenever audio plays without video.
            nowPlaying != null -> if (nowPlayingDismissed) hideStreamingScreen() else showNowPlayingScreen(nowPlaying)
            bedsideClockRequested -> showBedsideClockScreen()
            currentAirPlayState == ProtocolState.CONNECTED -> showStreamingScreen()
            photoFrame != null -> showPhotoScreen(photoFrame)
            else -> hideStreamingScreen()
        }
    }

    /** Shows the AirPlay pairing PIN over the full screen during SRP pair-setup. */
    fun showPinScreen(pin: String) {
        pinScreen.setPin(pin)
        streamingScreen.visibility = View.GONE
        photoScreen.visibility = View.GONE
        nowPlayingScreen.visibility = View.GONE
        pinScreen.visibility = View.VISIBLE
        streamingContainer.visibility = View.VISIBLE
        streamingContainer.bringToFront()
        setImmersiveBars(true)
    }
}

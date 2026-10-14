package com.phairplay.ui

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.AlarmManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.animation.ValueAnimator
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.MotionEvent
import android.view.GestureDetector
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.method.ScrollingMovementMethod
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.phairplay.R
import com.phairplay.airplay.NowPlayingInfo
import com.phairplay.integrations.HomeAssistant
import com.phairplay.integrations.ICloudCalDav
import com.phairplay.util.Logger

/**
 * NowPlayingScreen — full-screen card shown while AirPlay audio plays without video (system audio
 * from a Mac, Apple Music, podcasts). Mirroring shows the video [StreamingScreen] instead; this
 * screen fills the otherwise-black surface for audio-only sessions.
 *
 * Layout (centered): album art (or an AirPlay-glyph placeholder when the sender sends no artwork),
 * track title, artist, album, and a "♪ Audio from <sender>" footer. Built programmatically to match
 * the other overlay views ([PhotoScreen]/[StreamingScreen]).
 */
class NowPlayingScreen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var displayedArtwork: ByteArray? = null
    private var artworkInitialized = false
    private var displayedArtworkSample: IntArray? = null
    private var displayedArtworkTrackKey: String? = null
    private var artworkTrackKey: String? = null
    private var pendingArtwork: Bitmap? = null
    private var pendingArtworkKey: String? = null
    private var pendingArtworkTask: Runnable? = null
    private var missingArtworkTask: Runnable? = null
    private var backdropStart = Color.rgb(8, 14, 27)
    private var backdropEnd = Color.rgb(24, 32, 53)
    private var baseBackdropStart = backdropStart
    private var baseBackdropEnd = backdropEnd
    private var backdropAnimator: ValueAnimator? = null
    private var ambientBackdropAnimator: ValueAnimator? = null
    private var ambientPhase = 0f
    private var onVolumeChange: ((Float) -> Unit)? = null
    private var onReturnToApp: (() -> Unit)? = null

    private val artwork: ImageView
    private val titleView: TextView
    private val artistView: TextView
    private val albumView: TextView
    private val volumeCaption: TextView
    private val volumeLabel: TextView
    private val volumeSlider: SeekBar
    private val volumeTrack: FrameLayout
    private val volumeColumn: LinearLayout
    private val transportControls: LinearLayout
    private val playPauseButton: ImageButton
    private var onRemoteCommand: ((String) -> Unit)? = null
    private var remoteControlsAvailable = false
    private var remoteControlsEnabled = false
    private lateinit var clockView: TextView
    private lateinit var widgets: TextView
    private lateinit var pageTabs: LinearLayout
    private lateinit var pageTabsScroll: HorizontalScrollView
    private lateinit var dashboardHost: FrameLayout
    private lateinit var dashboardWebView: WebView
    private lateinit var contentViewport: View
    private val haDashboardPageIndices = mutableSetOf<Int>()
    private var loadedDashboardUrl: String? = null
    private var infoPages: List<Pair<String, List<String>>> = emptyList()
    private var selectedInfoPage = 0
    private var renderedPageLabels: List<String> = emptyList()
    private var renderedSelectedPage = -1
    private var clockBaseSizeSp = CLOCK_SP
    private var lastAdaptSignature: String? = null
    private val widgetHandler = Handler(Looper.getMainLooper())
    private var clockEnabled = true
    private var dateEnabled = true
    private var agendaEnabled = false
    private var weatherEnabled = false
    private var weatherCity = ""
    private var widgetStyle = "MINIMAL"
    private var weatherLoadPending = false
    private var weatherFailed = false
    private var weatherRetryScheduledFor: String? = null
    private var idleClockMode = false
    private var showClockSeconds = false
    private var batteryEnabled = false
    private var nextAlarmEnabled = false
    private var batteryText: String? = null
    private var nextAlarmText: String? = null
    private var lastAlarmRefreshAt = 0L
    private var systemInfoReceiverRegistered = false
    private lateinit var clockContentColumn: LinearLayout
    private lateinit var pageTabsLeftFade: View
    private lateinit var pageTabsRightFade: View
    @Volatile private var weatherText: String? = null
    @Volatile private var agendaText: String? = null
    private var haEnabled = false
    private var haCalendarEnabled = false
    private var haTodoEnabled = false
    private var haUrl = ""
    private var haEntities = ""
    private var haCalendars = ""
    private var haTodos = ""
    @Volatile private var haDeviceText: String? = null
    @Volatile private var haDashboardPages: List<List<String>> = emptyList()
    @Volatile private var haScheduleText: String? = null
    @Volatile private var haPending = false
    private var lastHaRefreshAt = 0L
    private var iCloudEnabled = false
    private var iCloudEmail = ""
    private var iCloudCalendarHrefs = ""
    @Volatile private var iCloudText: String? = null
    @Volatile private var iCloudPending = false
    private var lastICloudRefreshAt = 0L
    private var lastWeatherCity = ""
    @Volatile private var agendaQueryPending = false
    private var lastAgendaQueryAt = 0L
    private val weatherExecutor = Executors.newSingleThreadExecutor()
    private val clockTick = object : Runnable { override fun run() { refreshWidgets(); widgetHandler.postDelayed(this, 1000) } }
    private val systemInfoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> refreshBatteryText(intent)
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED -> {
                    lastAlarmRefreshAt = 0L
                    refreshWidgets()
                }
            }
        }
    }

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(backdropStart, backdropEnd, Color.rgb(8, 12, 22)))
        // This full-screen overlay sits above the app's navigation and settings pages.
        // Consume touches that miss a child control so they cannot activate controls below.
        isClickable = true

        val returnButton = android.widget.Button(context).apply {
            text = context.getString(R.string.now_playing_return_short)
            contentDescription = context.getString(R.string.now_playing_return)
            textSize = 14f
            isAllCaps = false
            isSingleLine = true
            gravity = Gravity.CENTER
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(color(R.color.accent_blue))
            }
            setTextColor(color(R.color.background_dark))
            setPadding(0, 0, 0, 0)
            setOnClickListener { onReturnToApp?.invoke() }
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL or Gravity.START }

        artwork = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ART_SIZE_DP), dp(ART_SIZE_DP))
            scaleType = ImageView.ScaleType.CENTER_CROP
            // Rounded surface so the placeholder glyph (and any non-square art) sits on a card.
            background = GradientDrawable().apply {
                setColor(color(R.color.background_surface))
                cornerRadius = dp(16).toFloat()
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        clockView = textView(CLOCK_SP, R.color.text_primary).apply {
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            maxLines = 1
            gravity = Gravity.START
            setPadding(dp(16), 0, dp(16), dp(2))
        }
        titleView = textView(TITLE_SP, R.color.text_primary, bold = true).apply {
            setPadding(0, dp(16), 0, 0)
            minHeight = dp(82)
            maxLines = Int.MAX_VALUE
        }
        artistView = textView(ARTIST_SP, R.color.text_secondary).apply {
            setPadding(0, dp(12), 0, 0)
            minHeight = dp(40)
            maxLines = Int.MAX_VALUE
        }
        albumView = textView(ALBUM_SP, R.color.text_tertiary).apply {
            setPadding(0, dp(2), 0, 0)
            minHeight = dp(42)
            maxLines = Int.MAX_VALUE
        }
        transportControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glassBackground(28)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun transportButton(icon: Int, description: Int, command: String, sizeDp: Int = 52) =
            ImageButton(context).apply {
                setImageResource(icon)
                contentDescription = context.getString(description)
                background = ContextCompat.getDrawable(context, R.drawable.focus_ring_selector)
                imageTintList = ContextCompat.getColorStateList(context, R.color.transport_icon_tint)
                isFocusable = true
                isEnabled = false
                setOnClickListener { onRemoteCommand?.invoke(command) }
                layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                }
            }
        transportControls.addView(transportButton(R.drawable.ic_transport_previous,
            R.string.now_playing_previous, com.phairplay.airplay.DacpClient.CMD_PREV))
        playPauseButton = transportButton(R.drawable.ic_transport_pause,
            R.string.now_playing_pause, com.phairplay.airplay.DacpClient.CMD_PLAY_PAUSE, 60)
        transportControls.addView(playPauseButton)
        transportControls.addView(transportButton(R.drawable.ic_transport_next,
            R.string.now_playing_next, com.phairplay.airplay.DacpClient.CMD_NEXT))
        transportControls.visibility = View.GONE
        volumeCaption = textView(VOLUME_SP, R.color.text_secondary).apply {
            text = context.getString(R.string.now_playing_volume_caption)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
        }
        volumeLabel = textView(VOLUME_SP, R.color.text_secondary).apply {
            text = context.getString(R.string.now_playing_volume_value, 100)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
        }
        volumeSlider = SeekBar(context).apply {
            max = 100
            progress = 100
            // Use Android's themed SeekBar and let View's transform handle touch coordinates.
            // Rotating a native horizontal control avoids maintaining custom thumb/touch logic.
            rotation = -90f
            contentDescription = context.getString(R.string.now_playing_volume_accessibility)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    volumeLabel.text = context.getString(R.string.now_playing_volume_value, progress)
                    if (fromUser) onVolumeChange?.invoke(progressToDb(progress))
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        volumeLabel.textSize = 14f
        volumeColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glassBackground(30)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        volumeTrack = FrameLayout(context).apply {
            addView(volumeSlider, FrameLayout.LayoutParams(dp(250), dp(44), Gravity.CENTER))
        }
        volumeColumn.addView(volumeCaption, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(22)))
        volumeColumn.addView(volumeLabel, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(22)))
        volumeColumn.addView(volumeTrack, LinearLayout.LayoutParams(dp(58), dp(250)))
        widgets = textView(22f, R.color.text_secondary).apply {
            setPadding(dp(16), dp(4), dp(16), dp(8))
            maxLines = Int.MAX_VALUE
            movementMethod = ScrollingMovementMethod()
            isVerticalScrollBarEnabled = true
            gravity = Gravity.START
        }

        pageTabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        pageTabsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(20))
            clipToPadding = false
            setPadding(dp(8), 0, dp(8), 0)
            addView(pageTabs, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            visibility = View.GONE
        }
        val pageTabsFadeLayer = FrameLayout(context).apply {
            addView(pageTabsScroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            pageTabsLeftFade = View(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = edgeFade(backdropStart, left = true)
                visibility = View.GONE
            }
            pageTabsRightFade = View(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = edgeFade(backdropStart, left = false)
                visibility = View.GONE
            }
            addView(pageTabsLeftFade, FrameLayout.LayoutParams(dp(18), LayoutParams.MATCH_PARENT, Gravity.START))
            addView(pageTabsRightFade, FrameLayout.LayoutParams(dp(18), LayoutParams.MATCH_PARENT, Gravity.END))
            pageTabsScroll.setOnScrollChangeListener { _, _, _, _, _ -> updatePageTabFades() }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> post { updatePageTabFades() } }
            pageTabs.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> post { updatePageTabFades() } }
        }
        val swipeDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || kotlin.math.abs(e2.x - e1.x) < dp(48) || kotlin.math.abs(velocityX) < dp(120)) return false
                showInfoPage(selectedInfoPage + if (velocityX < 0) 1 else -1)
                return true
            }
        })
        widgets.setOnTouchListener { _, event ->
            val gestureHandled = swipeDetector.onTouchEvent(event)
            gestureHandled && event.actionMasked == MotionEvent.ACTION_UP
        }

        details.addView(clockView)
        details.addView(widgets)
        details.addView(titleView)
        details.addView(artistView)
        details.addView(albumView)
        details.addView(transportControls)
        column.addView(artwork)
        column.addView(details)

        val scrollContent = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val scrollViewport = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(20))
            clipChildren = false
            clipToPadding = false
            addView(scrollContent, FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        contentViewport = scrollViewport
        addView(scrollViewport, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(volumeColumn, LayoutParams(dp(80), LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(18)
        })
        val topControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
            addView(returnButton, LinearLayout.LayoutParams(dp(INFO_TAB_WIDTH_DP), dp(INFO_TAB_HEIGHT_DP)).apply {
                marginEnd = dp(4)
            })
            addView(pageTabsFadeLayer, LinearLayout.LayoutParams(0, dp(INFO_TAB_HEIGHT_DP), 1f))
        }
        dashboardWebView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            isVerticalScrollBarEnabled = true
            addJavascriptInterface(object {
                @JavascriptInterface
                fun getExternalAuth(message: String?) {
                    val payload = runCatching { org.json.JSONObject(message.orEmpty()) }.getOrNull() ?: return
                    if (payload.optString("callback") != "externalAuthSetToken") return
                    val accessToken = HomeAssistant.token(context)
                    val success = accessToken != null
                    val tokenJson = if (success) org.json.JSONObject.quote(accessToken) else "null"
                    val js = "window.externalAuthSetToken($success,${if (success) "{access_token:$tokenJson,expires_in:315360000}" else "undefined"});"
                    post { evaluateJavascript(js, null) }
                }
            }, "externalApp")
            webViewClient = object : WebViewClient() {
                private fun allowed(uri: Uri): Boolean {
                    val base = Uri.parse(haUrl.trim().trimEnd('/'))
                    return uri.scheme == base.scheme && uri.host.equals(base.host, ignoreCase = true) &&
                        uri.port == base.port
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !allowed(request.url)
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    !runCatching { allowed(Uri.parse(url)) }.getOrDefault(false)
            }
        }
        dashboardHost = FrameLayout(context).apply {
            visibility = View.GONE
            addView(dashboardWebView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        addView(dashboardHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(INFO_TAB_HEIGHT_DP + 12)
        })
        addView(topControls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        clockContentColumn = column
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adaptOrientation(column, scrollViewport) }
    }

    /** Connects the on-screen volume slider to the receiver's local AudioTrack gain. */
    fun setVolumeChangeHandler(handler: (Float) -> Unit) {
        onVolumeChange = handler
    }

    fun setReturnToAppHandler(handler: () -> Unit) {
        onReturnToApp = handler
    }

    fun setRemoteCommandHandler(handler: (String) -> Unit) {
        onRemoteCommand = handler
    }

    fun setRemoteControlsAvailable(available: Boolean) {
        remoteControlsAvailable = available
        updateTransportControls()
    }

    fun setRemoteControlsEnabled(enabled: Boolean) {
        remoteControlsEnabled = enabled
        updateTransportControls()
    }

    private fun updateTransportControls() {
        // The idle bedside clock has no playback session to control. Keeping this wide row
        // visible there also consumes the horizontal space needed by the clock and widgets.
        transportControls.visibility = if (remoteControlsEnabled && !idleClockMode) View.VISIBLE else View.GONE
        val canSend = remoteControlsEnabled && remoteControlsAvailable && !idleClockMode
        for (i in 0 until transportControls.childCount) {
            transportControls.getChildAt(i).isEnabled = canSend
        }
    }

    fun setRemotePlaybackActive(playing: Boolean) {
        playPauseButton.setImageResource(if (playing) R.drawable.ic_transport_pause else R.drawable.ic_transport_play)
        playPauseButton.contentDescription = context.getString(
            if (playing) R.string.now_playing_pause else R.string.now_playing_play)
    }

    /** Turns the playback card into a standalone bedside clock when there is no active stream. */
    fun setBedsideClockMode(idle: Boolean) {
        idleClockMode = idle
        artwork.visibility = if (idle) View.GONE else View.VISIBLE
        titleView.visibility = if (idle) View.GONE else View.VISIBLE
        artistView.visibility = if (idle) View.GONE else View.VISIBLE
        albumView.visibility = if (idle) View.GONE else View.VISIBLE
        volumeColumn.visibility = if (idle) View.GONE else View.VISIBLE
        updateTransportControls()
        clockBaseSizeSp = if (idle) 148f else if (isLandscapeUi()) 120f else 84f
        updateClockAutoSize()
    }

    fun setWidgetSettings(clock: Boolean, date: Boolean, agenda: Boolean, weather: Boolean, city: String, style: String, seconds: Boolean = false) {
        clockEnabled = clock; dateEnabled = date; agendaEnabled = agenda; weatherEnabled = weather; weatherCity = city; widgetStyle = style
        showClockSeconds = seconds
        val weatherNeedsRetry = weatherFailed && !weatherLoadPending && weatherRetryScheduledFor == null
        if (weather && city.isNotBlank() && (city != lastWeatherCity || weatherNeedsRetry)) loadWeather(city)
        refreshWidgets()
    }

    fun setClockInfoSettings(battery: Boolean, nextAlarm: Boolean) {
        batteryEnabled = battery
        nextAlarmEnabled = nextAlarm
        if (battery) refreshBatteryText(null)
        if (!nextAlarm) nextAlarmText = null
        lastAlarmRefreshAt = 0L
        refreshWidgets()
    }

    fun setHomeAssistantSettings(enabled: Boolean, url: String, entities: String, calendarEnabled: Boolean,
                                 calendars: String, todoEnabled: Boolean, todos: String) {
        haEnabled = enabled
        haUrl = url
        haEntities = entities
        haCalendarEnabled = calendarEnabled
        haCalendars = calendars
        haTodoEnabled = todoEnabled
        haTodos = todos
        lastHaRefreshAt = 0L
        if (!enabled) { haDeviceText = null; haDashboardPages = emptyList(); haScheduleText = null }
        refreshWidgets()
    }

    fun setICloudSettings(enabled: Boolean, email: String, calendarHrefs: String) {
        iCloudEnabled = enabled
        iCloudEmail = email
        iCloudCalendarHrefs = calendarHrefs
        lastICloudRefreshAt = 0L
        if (!enabled) iCloudText = null
        refreshWidgets()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!systemInfoReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            val stickyBattery = ContextCompat.registerReceiver(
                context, systemInfoReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            systemInfoReceiverRegistered = true
            refreshBatteryText(stickyBattery)
        }
        widgetHandler.removeCallbacks(clockTick)
        widgetHandler.post(clockTick)
    }

    override fun onDetachedFromWindow() {
        stopAmbientBackdropAnimation()
        widgetHandler.removeCallbacks(clockTick)
        if (systemInfoReceiverRegistered) {
            runCatching { context.unregisterReceiver(systemInfoReceiver) }
            systemInfoReceiverRegistered = false
        }
        super.onDetachedFromWindow()
    }

    private fun refreshBatteryText(intent: Intent?) {
        if (!batteryEnabled) return
        val battery = intent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) {
            batteryText = context.getString(R.string.now_playing_battery_unavailable)
        } else {
            val percent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
            val status = battery!!.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
            val full = status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val eta = if (charging && android.os.Build.VERSION.SDK_INT >= 28) {
                runCatching { (context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager).computeChargeTimeRemaining() }
                    .getOrNull()?.takeIf { it > 0L }
            } else null
            batteryText = when {
                full -> context.getString(R.string.now_playing_battery_full, percent)
                charging && eta != null -> {
                    val minutes = (eta / 60_000L).toInt().coerceAtLeast(1)
                    val hours = minutes / 60
                    val remainder = minutes % 60
                    context.getString(R.string.now_playing_battery_charging_eta, percent,
                        if (hours > 0) context.getString(R.string.now_playing_battery_eta_hours_minutes, hours, remainder)
                        else context.getString(R.string.now_playing_battery_eta_minutes, minutes))
                }
                charging -> context.getString(R.string.now_playing_battery_charging_unknown_eta, percent)
                else -> context.getString(R.string.now_playing_battery, percent)
            }
        }
        if (::widgets.isInitialized) refreshWidgets()
    }

    private fun refreshWidgets() {
        if (!::widgets.isInitialized) return
        val timeLines = mutableListOf<String>()
        val scheduleLines = mutableListOf<String>()
        val weatherLines = mutableListOf<String>()
        val pages = mutableListOf<Pair<String, List<String>>>()
        val haPages = mutableListOf<Pair<String, List<String>>>()
        val nowMillis = System.currentTimeMillis()
        val now = java.util.Date(nowMillis)
        val locale = java.util.Locale.getDefault()
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val clockPattern = when {
            showClockSeconds && is24Hour -> "HH:mm:ss"
            showClockSeconds -> "h:mm:ss a"
            is24Hour -> "HH:mm"
            else -> "h:mm a"
        }
        val clockText = if (clockEnabled) java.text.SimpleDateFormat(clockPattern, locale).format(now) else ""
        if (clockView.text.toString() != clockText) clockView.text = clockText
        clockView.visibility = if (clockEnabled) View.VISIBLE else View.GONE
        if (dateEnabled) {
            // The bedside clock places its widgets beside the clock on landscape screens.
            // A full weekday can wrap awkwardly at large system font scales, so use the
            // locale's abbreviated weekday there while keeping the complete date elsewhere.
            val dateSkeleton = if (idleClockMode && isLandscapeUi()) "MMMd EEE" else "yMMMd EEEE"
            timeLines += java.text.SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(locale, dateSkeleton), locale).format(now)
        }
        if (weatherEnabled) weatherLines += if (weatherCity.isBlank()) context.getString(R.string.now_playing_weather_city_missing)
            else weatherText ?: context.getString(R.string.now_playing_weather_loading, weatherCity)
        if (agendaEnabled) {
            if (nowMillis - lastAgendaQueryAt >= 60_000L) loadAgenda(nowMillis)
            scheduleLines += agendaText ?: context.getString(R.string.now_playing_agenda_loading)
        } else {
            agendaText = null
            lastAgendaQueryAt = 0L
        }
        if (batteryEnabled) timeLines += batteryText ?: context.getString(R.string.now_playing_battery_unavailable)
        if (nextAlarmEnabled) {
            if (nowMillis - lastAlarmRefreshAt >= 60_000L || nextAlarmText == null) refreshNextAlarm(nowMillis)
            timeLines += nextAlarmText ?: context.getString(R.string.now_playing_alarm_none)
        }
        if (haEnabled) {
            if (nowMillis - lastHaRefreshAt >= 60_000L && !haPending) loadHomeAssistant(nowMillis)
            if (haUrl.isNotBlank() && HomeAssistant.token(context) != null)
                haPages += context.getString(R.string.now_playing_info_ha) to emptyList()
            if (haCalendarEnabled || haTodoEnabled) scheduleLines += haScheduleText ?: context.getString(R.string.now_playing_ha_loading)
        }
        if (iCloudEnabled) {
            if (nowMillis - lastICloudRefreshAt >= 15 * 60_000L && !iCloudPending) loadICloudCalendar(nowMillis)
            scheduleLines += iCloudText ?: context.getString(R.string.now_playing_icloud_loading)
        }
        // Current weather and today's agenda are compact clock-page context. Keep
        // page turns for the larger Home Assistant device/control view.
        val clockPageLines = timeLines + weatherLines + scheduleLines
        if (clockPageLines.isNotEmpty()) pages += context.getString(R.string.now_playing_info_time) to clockPageLines
        haDashboardPageIndices.clear()
        val dashboardStart = pages.size
        pages.addAll(haPages)
        for (index in dashboardStart until pages.size) haDashboardPageIndices += index
        if (pages.map { it.first } != infoPages.map { it.first }) selectedInfoPage = selectedInfoPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        infoPages = pages
        updateInfoPageControls()
        val bg = if (widgetStyle == "CARD") glassBackground(24) else null
        widgets.background = bg
        updateBurnInOffset(nowMillis)
    }

    private fun updateInfoPageControls() {
        if (!::pageTabs.isInitialized) return
        pageTabs.visibility = View.VISIBLE
        pageTabsScroll.visibility = View.VISIBLE
        val labels = infoPages.map { it.first }
        if (labels != renderedPageLabels) {
            pageTabs.removeAllViews()
            infoPages.forEachIndexed { index, page ->
                val tab = TextView(context).apply {
                    text = page.first
                    textSize = 14f
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isClickable = true
                    background = ContextCompat.getDrawable(context, R.drawable.focus_ring_selector)
                    setPadding(dp(4), 0, dp(4), 0)
                    setOnClickListener { showInfoPage(index) }
                }
                pageTabs.addView(tab, LinearLayout.LayoutParams(dp(INFO_TAB_WIDTH_DP), dp(INFO_TAB_HEIGHT_DP)).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                })
            }
            renderedPageLabels = labels
            renderedSelectedPage = -1
        }
        if (renderedSelectedPage != selectedInfoPage) {
            infoPages.forEachIndexed { index, page ->
                val tab = pageTabs.getChildAt(index) as? TextView ?: return@forEachIndexed
                tab.setTextColor(color(if (index == selectedInfoPage) R.color.text_primary else R.color.text_secondary))
                tab.background = if (index == selectedInfoPage) glassBackground(18)
                    else ContextCompat.getDrawable(context, R.drawable.focus_ring_selector)
                tab.isSelected = index == selectedInfoPage
                tab.contentDescription = context.getString(
                    R.string.now_playing_info_page_accessibility, page.first, index + 1, infoPages.size)
            }
            renderedSelectedPage = selectedInfoPage
        }
        val lines = infoPages.getOrNull(selectedInfoPage)?.second.orEmpty()
        val text = lines.joinToString("\n")
        if (widgets.text.toString() != text) widgets.text = text
        widgets.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        val showingDashboard = selectedInfoPage in haDashboardPageIndices
        dashboardHost.visibility = if (showingDashboard) View.VISIBLE else View.GONE
        contentViewport.visibility = if (showingDashboard) View.GONE else View.VISIBLE
        volumeColumn.visibility = if (showingDashboard) View.GONE else View.VISIBLE
        if (showingDashboard) loadDashboardIfNeeded()
        pageTabsScroll.post { updatePageTabFades() }
    }

    private fun loadDashboardIfNeeded() {
        val base = haUrl.trim().trimEnd('/')
        if (base.isBlank() || HomeAssistant.token(context) == null) return
        if (loadedDashboardUrl == base) return
        loadedDashboardUrl = base
        dashboardWebView.loadUrl("$base/?external_auth=1")
    }

    private fun showInfoPage(index: Int) {
        if (infoPages.isEmpty()) return
        val nextPage = Math.floorMod(index, infoPages.size)
        if (nextPage == selectedInfoPage) return
        val direction = if (index > selectedInfoPage) 1 else -1
        val previousText = widgets.text
        selectedInfoPage = nextPage
        updateInfoPageControls()
        val nextText = widgets.text
        val nextVisibility = widgets.visibility
        widgets.text = previousText
        widgets.visibility = if (previousText.isNullOrEmpty()) View.GONE else View.VISIBLE
        val distance = dp(22).toFloat() * direction
        widgets.animate().cancel()
        widgets.alpha = 1f
        widgets.translationX = 0f
        widgets.animate()
            .alpha(0f)
            .translationX(-distance)
            .setDuration(110L)
            .withEndAction {
                if (!isAttachedToWindow) return@withEndAction
                widgets.text = nextText
                widgets.visibility = nextVisibility
                widgets.translationX = distance
                widgets.alpha = 0f
                widgets.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(190L)
                    .start()
            }
            .start()
        pageTabs.getChildAt(selectedInfoPage)?.let { pageTabsScroll.smoothScrollTo(it.left - dp(12), 0) }
    }

    private fun loadHomeAssistant(nowMillis: Long) {
        val token = HomeAssistant.token(context) ?: run {
            haDeviceText = context.getString(R.string.now_playing_ha_not_configured)
            haScheduleText = context.getString(R.string.now_playing_ha_not_configured)
            return
        }
        if (haUrl.isBlank()) return
        lastHaRefreshAt = nowMillis
        haPending = true
        val url = haUrl
        val entities = haEntities.split(',', '\n').map { it.trim() }.filter { it.matches(Regex("[a-z_]+\\.[a-zA-Z0-9_]+")) }.take(8)
        val calendars = if (haCalendarEnabled) haCalendars.split(',', '\n').map(String::trim).filter { it.startsWith("calendar.") }.take(3) else emptyList()
        val todos = if (haTodoEnabled) haTodos.split(',', '\n').map(String::trim).filter { it.startsWith("todo.") }.take(3) else emptyList()
        weatherExecutor.execute {
            val result = runCatching {
                val deviceLines = mutableListOf<String>()
                val scheduleLines = mutableListOf<String>()
                entities.forEach { id ->
                    val state = HomeAssistant.state(url, token, id)
                    val attrs = state.optJSONObject("attributes")
                    val title = attrs?.optString("friendly_name").orEmpty().ifBlank { id }
                    deviceLines += "$title: ${state.optString("state")}"
                }
                val start = java.time.Instant.ofEpochMilli(nowMillis).toString()
                val end = java.time.Instant.ofEpochMilli(nowMillis + 86_400_000L).toString()
                calendars.forEach { id ->
                    HomeAssistant.calendars(url, token, id, start, end).let { events ->
                        for (i in 0 until minOf(events.length(), 2)) {
                            val event = events.optJSONObject(i) ?: continue
                            val summary = event.optString("summary").ifBlank { context.getString(R.string.now_playing_agenda_untitled) }
                            val time = event.optString("start").let { if (it.length >= 16) it.substring(11, 16) else "" }
                            scheduleLines += context.getString(R.string.now_playing_ha_event, time, summary)
                        }
                    }
                }
                todos.forEach { id ->
                    val state = HomeAssistant.state(url, token, id)
                    val title = state.optJSONObject("attributes")?.optString("friendly_name").orEmpty().ifBlank { id }
                    scheduleLines += context.getString(R.string.now_playing_ha_todo_experimental, title, state.optString("state"))
                }
                deviceLines.chunked(4) to scheduleLines.joinToString("\n")
            }.getOrElse { listOf(listOf(context.getString(R.string.now_playing_ha_unavailable))) to context.getString(R.string.now_playing_ha_unavailable) }
            widgetHandler.post {
                haPending = false
                if (haEnabled) {
                    haDashboardPages = result.first.ifEmpty {
                        if (haUrl.isNotBlank() && HomeAssistant.token(context) != null) listOf(listOf(context.getString(R.string.now_playing_ha_no_selection))) else emptyList()
                    }
                    haDeviceText = haDashboardPages.firstOrNull()?.joinToString("\n")
                    haScheduleText = result.second.ifBlank { if (haCalendarEnabled || haTodoEnabled) context.getString(R.string.now_playing_ha_no_selection) else null }
                    refreshWidgets()
                }
            }
        }
    }

    private fun loadICloudCalendar(nowMillis: Long) {
        val password = ICloudCalDav.password(context) ?: run {
            iCloudText = context.getString(R.string.now_playing_icloud_not_configured)
            return
        }
        if (iCloudEmail.isBlank()) return
        val hrefs = runCatching {
            val json = org.json.JSONArray(iCloudCalendarHrefs)
            (0 until json.length()).mapNotNull { json.optJSONObject(it)?.optString("href")?.takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        if (hrefs.isEmpty()) {
            iCloudText = context.getString(R.string.now_playing_icloud_no_calendar)
            return
        }
        lastICloudRefreshAt = nowMillis
        iCloudPending = true
        val email = iCloudEmail
        weatherExecutor.execute {
            val result = runCatching {
                val events = ICloudCalDav.fetchEvents(email, password, hrefs, nowMillis, nowMillis + 24 * 60 * 60_000L).take(3)
                if (events.isEmpty()) context.getString(R.string.now_playing_icloud_empty)
                else events.joinToString("\n") { event ->
                    val time = if (event.allDay) context.getString(R.string.now_playing_agenda_all_day)
                        else android.text.format.DateFormat.getTimeFormat(context).format(Date(event.startMillis))
                    context.getString(R.string.now_playing_icloud_event, time, event.title)
                }
            }.getOrElse { context.getString(R.string.now_playing_icloud_unavailable) }
            widgetHandler.post {
                iCloudPending = false
                if (iCloudEnabled) { iCloudText = result; refreshWidgets() }
            }
        }
    }

    private fun refreshNextAlarm(nowMillis: Long) {
        lastAlarmRefreshAt = nowMillis
        val alarm = runCatching {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock
        }.getOrNull()
        nextAlarmText = if (alarm == null) context.getString(R.string.now_playing_alarm_none)
        else context.getString(R.string.now_playing_alarm, android.text.format.DateFormat.getTimeFormat(context).format(Date(alarm.triggerTime)))
    }

    private fun updateBurnInOffset(nowMillis: Long) {
        if (!::clockContentColumn.isInitialized) return
        if (!idleClockMode && infoPages.isEmpty()) {
            clockContentColumn.translationX = 0f
            clockContentColumn.translationY = 0f
            return
        }
        // Slowly drift the information content by a few pixels to reduce static OLED pixels.
        val elapsedMinutes = nowMillis / 60_000.0
        val x = kotlin.math.sin(elapsedMinutes * (2.0 * Math.PI / 17.0)) * 3.0
        val y = kotlin.math.sin(elapsedMinutes * (2.0 * Math.PI / 23.0) + 1.2) * 2.0
        clockContentColumn.translationX = dp(3).toFloat() * (x / 3.0).toFloat()
        clockContentColumn.translationY = dp(2).toFloat() * (y / 2.0).toFloat()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView !== this) return
        if (visibility == View.VISIBLE && isAttachedToWindow) startAmbientBackdropAnimation()
        else stopAmbientBackdropAnimation()
    }

    /** Gently shifts the dark wallpaper through nearby hues while keeping contrast stable. */
    private fun startAmbientBackdropAnimation() {
        if (ambientBackdropAnimator?.isStarted == true) return
        ambientBackdropAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { _ ->
                val elapsed = android.os.SystemClock.elapsedRealtime()
                val cycle = (elapsed % (30L * 60L * 1000L)).toFloat() / (30f * 60f * 1000f)
                ambientPhase = ((kotlin.math.sin(cycle * (2f * Math.PI.toFloat())) + 1f) / 2f)
                applyAmbientBackdrop()
            }
            start()
        }
    }

    private fun stopAmbientBackdropAnimation() {
        ambientBackdropAnimator?.cancel()
        ambientBackdropAnimator = null
        ambientPhase = 0f
        renderBackdrop(baseBackdropStart, baseBackdropEnd)
    }

    private fun applyAmbientBackdrop() {
        val teal = Color.rgb(12, 35, 43)
        val violet = Color.rgb(33, 22, 47)
        val tint = android.animation.ArgbEvaluator().evaluate(ambientPhase, teal, violet) as Int
        renderBackdrop(blend(baseBackdropStart, tint, 0.16f), blend(baseBackdropEnd, tint, 0.12f))
    }

    private fun renderBackdrop(start: Int, end: Int) {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(start, end, blend(Color.rgb(8, 12, 22), end, 0.28f)))
        backdropStart = start
        backdropEnd = end
        if (::pageTabsLeftFade.isInitialized && ::pageTabsRightFade.isInitialized) {
            pageTabsLeftFade.background = edgeFade(start, left = true)
            pageTabsRightFade.background = edgeFade(start, left = false)
        }
    }

    private fun updateClockAutoSize() {
        if (!::clockView.isInitialized) return
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            clockView, 48, clockBaseSizeSp.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
    }

    private fun loadAgenda(requestedAt: Long) {
        if (agendaQueryPending) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            lastAgendaQueryAt = requestedAt
            agendaText = context.getString(R.string.now_playing_agenda_permission)
            return
        }
        agendaQueryPending = true
        lastAgendaQueryAt = requestedAt
        weatherExecutor.execute {
            val result = runCatching {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val begin = start.timeInMillis
                val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                val nowMillis = System.currentTimeMillis()
                val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Events.ALL_DAY)
                val cursor = CalendarContract.Instances.query(context.contentResolver, projection, begin, end)
                val events = mutableListOf<String>()
                cursor?.use { c ->
                    val titleCol = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                    val beginCol = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    val endCol = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
                    val allDayCol = c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                    while (c.moveToNext() && events.size < 2) {
                        val eventStart = c.getLong(beginCol)
                        val eventEnd = c.getLong(endCol)
                        val allDay = c.getInt(allDayCol) != 0
                        if (eventEnd <= nowMillis) continue
                        val title = c.getString(titleCol).orEmpty().ifBlank { context.getString(R.string.now_playing_agenda_untitled) }
                        val time = if (allDay) context.getString(R.string.now_playing_agenda_all_day)
                            else android.text.format.DateFormat.getTimeFormat(context).format(Date(eventStart))
                        events += context.getString(R.string.now_playing_agenda_event, time, title)
                    }
                }
                if (events.isEmpty()) context.getString(R.string.now_playing_agenda_empty)
                else context.getString(R.string.now_playing_agenda_events, events.joinToString("  /  "))
            }.getOrElse { context.getString(R.string.now_playing_agenda_unavailable) }
            widgetHandler.post {
                agendaQueryPending = false
                if (agendaEnabled) {
                    agendaText = result
                    refreshWidgets()
                }
            }
        }
    }

    private fun loadWeather(city: String) {
        if (city.isBlank() || weatherLoadPending) return
        lastWeatherCity = city
        weatherLoadPending = true
        weatherExecutor.execute {
            val result = runCatching {
                fun get(url: String) = URL(url).openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }.getInputStream().bufferedReader().use { it.readText() }
                val encoded = URLEncoder.encode(city, "UTF-8")
                val language = URLEncoder.encode(java.util.Locale.getDefault().language, "UTF-8")
                val geo = org.json.JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=$language&format=json"))
                val place = geo.optJSONArray("results")?.optJSONObject(0) ?: error(context.getString(R.string.now_playing_weather_city_not_found))
                val lat = place.getDouble("latitude"); val lon = place.getDouble("longitude")
                val response = org.json.JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=precipitation_probability,precipitation&forecast_days=2&timezone=auto"))
                val forecast = response.getJSONObject("current")
                val temp = forecast.getDouble("temperature_2m").toInt()
                val code = forecast.getInt("weather_code")
                val condition = when (code) {
                    0 -> R.string.now_playing_weather_clear
                    1, 2 -> R.string.now_playing_weather_partly_cloudy
                    3 -> R.string.now_playing_weather_overcast
                    45, 48 -> R.string.now_playing_weather_fog
                    in 51..67 -> R.string.now_playing_weather_rain
                    in 71..77 -> R.string.now_playing_weather_snow
                    in 80..82 -> R.string.now_playing_weather_showers
                    in 95..99 -> R.string.now_playing_weather_thunderstorm
                    else -> R.string.now_playing_weather_cloudy
                }
                val hourly = response.getJSONObject("hourly")
                val times = hourly.optJSONArray("time")
                val probabilities = hourly.optJSONArray("precipitation_probability")
                val amounts = hourly.optJSONArray("precipitation")
                val nowText = forecast.optString("time")
                val endHour = runCatching { java.time.LocalDateTime.parse(nowText).plusHours(24).toString().take(13) }.getOrDefault(nowText.take(13))
                val rainIndices = (0 until (times?.length() ?: 0)).filter { i ->
                    val time = times?.optString(i).orEmpty()
                    time >= nowText.take(13) && time <= endHour &&
                        (probabilities?.optInt(i, 0) ?: 0) >= 30 && (amounts?.optDouble(i, 0.0) ?: 0.0) >= 0.1
                }
                val precipitation = if (rainIndices.isEmpty()) context.getString(R.string.now_playing_weather_rain_none)
                else {
                    val first = times!!.optString(rainIndices.first()).takeLast(5)
                    val maxProbability = rainIndices.maxOf { probabilities!!.optInt(it, 0) }
                    context.getString(R.string.now_playing_weather_rain_expected, first, maxProbability)
                }
                context.getString(R.string.now_playing_weather_result, city, context.getString(condition), temp) +
                    "\n" + precipitation
            }
            result.onSuccess { weather ->
                widgetHandler.post {
                    weatherLoadPending = false
                    weatherFailed = false
                    weatherRetryScheduledFor = null
                    if (lastWeatherCity == city && weatherCity == city) {
                        weatherText = context.getString(R.string.now_playing_weather_updated, weather)
                        refreshWidgets()
                    } else if (weatherEnabled && weatherCity.isNotBlank()) {
                        loadWeather(weatherCity)
                    }
                }
            }.onFailure { error ->
                Logger.w("Weather request for '$city' failed: ${error.javaClass.simpleName}: ${error.message}")
                widgetHandler.post {
                    weatherLoadPending = false
                    weatherFailed = true
                    if (lastWeatherCity == city && weatherCity == city) {
                        weatherText = context.getString(R.string.now_playing_weather_unavailable)
                        refreshWidgets()
                        scheduleWeatherRetry(city)
                    } else if (weatherEnabled && weatherCity.isNotBlank()) {
                        loadWeather(weatherCity)
                    }
                }
            }
        }
    }

    private fun scheduleWeatherRetry(city: String) {
        if (weatherRetryScheduledFor == city) return
        weatherRetryScheduledFor = city
        widgetHandler.postDelayed({
            weatherRetryScheduledFor = null
            if (weatherEnabled && weatherCity == city && lastWeatherCity == city) loadWeather(city)
        }, WEATHER_RETRY_DELAY_MS)
    }

    private fun adaptOrientation(column: LinearLayout, scrollViewport: ScrollView) {
        val landscape = isLandscapeUi()
        val bedsideLandscape = idleClockMode && landscape
        val signature = "${width}x${height}:$landscape:$idleClockMode:${resources.configuration.orientation}"
        if (signature == lastAdaptSignature) return
        lastAdaptSignature = signature
        column.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val density = resources.displayMetrics.density
        val availableWidth = (width / density).toInt().coerceAtLeast(320)
        val availableWidthDp = availableWidth.toFloat()
        val availableHeight = (height / density).toInt().coerceAtLeast(320)
        scrollViewport.clipToPadding = true
        scrollViewport.setPadding(0, dp(if (landscape) 72 else 84), 0, dp(if (landscape) 16 else 104))
        val volumeRailReserve = 98f // 80dp rail + 18dp end inset
        val portraitSideInset = 16f
        val compactPortrait = !landscape && availableWidthDp < 720f
        val shortPortrait = !landscape && availableHeight < 720
        val compactPortraitContentWidth = (availableWidthDp - portraitSideInset * 2f).coerceAtLeast(240f)
        // Phone landscape windows have little vertical room. The TV/tablet minimum art
        // size and type scale otherwise make the centered card extend beyond the screen.
        val shortLandscape = landscape && availableHeight < 500
        val art = if (landscape) (availableHeight * if (shortLandscape) 0.50f else 0.62f).toInt()
            .coerceIn(if (shortLandscape) 160 else 300, if (shortLandscape) 240 else 440)
            else minOf((availableWidth * 0.58f).toInt().coerceIn(220, 360), compactPortraitContentWidth.toInt(),
                if (shortPortrait) 160 else 360)
                .coerceAtLeast(160)
        val clockSize = when {
            idleClockMode && landscape && shortLandscape -> 112f
            idleClockMode && landscape -> 164f
            idleClockMode -> 148f
            shortPortrait -> 64f
            landscape && shortLandscape -> 72f
            landscape -> 120f
            else -> 84f
        }
        clockBaseSizeSp = clockSize
        updateClockAutoSize()
        // Let the containing ScrollView handle long metadata so text is never clipped
        // between the fixed return button and volume rail on short screens.
        titleView.maxHeight = Int.MAX_VALUE
        titleView.movementMethod = null
        titleView.isVerticalScrollBarEnabled = false
        artistView.maxHeight = Int.MAX_VALUE
        albumView.maxHeight = Int.MAX_VALUE
        artistView.movementMethod = null
        albumView.movementMethod = null
        artistView.isVerticalScrollBarEnabled = false
        albumView.isVerticalScrollBarEnabled = false
        (artwork.layoutParams as LinearLayout.LayoutParams).apply { width = dp(art); height = dp(art); artwork.layoutParams = this }
        // Keep the content column clear of the volume rail. On wider tablets use the
        // centered 1050dp composition; on narrower landscape windows reserve both the rail
        // and a 32dp gap, then pin the content to the left safe area.
        val sideInset = 32f
        val centeredLayoutMinWidth = 1050f + volumeRailReserve + sideInset * 2f
        val compactLandscape = landscape && (availableWidthDp < centeredLayoutMinWidth || availableHeight < 500)
        val contentWidth = when {
            landscape -> minOf(1050f, availableWidthDp - volumeRailReserve - sideInset * 2f).coerceAtLeast(320f)
            compactPortrait -> compactPortraitContentWidth
            else -> minOf(500f, availableWidthDp).coerceAtLeast(320f)
        }
        val maxWidth = dp(contentWidth.roundToInt())
        val lp = column.layoutParams as? LayoutParams ?: return
        column.gravity = if (landscape) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
        val pinToStart = compactLandscape
        val desiredGravity = if (pinToStart) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
        val desiredMarginStart = when {
            compactLandscape -> dp(sideInset.roundToInt())
            else -> 0
        }
        if (lp.width != maxWidth || lp.gravity != desiredGravity || lp.marginStart != desiredMarginStart) {
            lp.width = maxWidth
            lp.gravity = desiredGravity
            lp.marginStart = desiredMarginStart
            column.layoutParams = lp
        }
        val details = column.getChildAt(1) as? LinearLayout ?: return
        details.translationY = if (idleClockMode || shortLandscape) 0f else dp(if (landscape) -24 else 0).toFloat()
        (details.layoutParams as LinearLayout.LayoutParams).apply { width = if (landscape) 0 else LayoutParams.MATCH_PARENT; height = LayoutParams.WRAP_CONTENT; weight = if (landscape) 1f else 0f; marginStart = if (landscape) dp(44) else 0; details.layoutParams = this }
        details.orientation = if (bedsideLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        details.gravity = if (landscape) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER

        val clockParams = clockView.layoutParams as LinearLayout.LayoutParams
        val widgetsParams = widgets.layoutParams as LinearLayout.LayoutParams
        if (bedsideLandscape) {
            clockParams.width = 0
            clockParams.height = LayoutParams.WRAP_CONTENT
            clockParams.weight = 0.64f
            clockParams.marginEnd = dp(28)
            widgetsParams.width = 0
            widgetsParams.height = LayoutParams.WRAP_CONTENT
            widgetsParams.weight = 0.36f
            widgetsParams.marginStart = dp(28)
            widgets.textSize = if (shortLandscape) 18f else 20f
            widgets.maxLines = Int.MAX_VALUE
            widgets.maxHeight = dp(if (shortLandscape) 96 else 180)
            widgets.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        } else {
            clockParams.width = LayoutParams.MATCH_PARENT
            clockParams.height = LayoutParams.WRAP_CONTENT
            clockParams.weight = 0f
            clockParams.marginEnd = 0
            widgetsParams.width = LayoutParams.MATCH_PARENT
            widgetsParams.height = LayoutParams.WRAP_CONTENT
            widgetsParams.weight = 0f
            widgetsParams.marginStart = 0
            widgets.textSize = if (shortPortrait) 18f else 22f
            widgets.maxLines = Int.MAX_VALUE
            widgets.maxHeight = dp(240)
            widgets.gravity = Gravity.CENTER
        }
        clockView.layoutParams = clockParams
        widgets.layoutParams = widgetsParams
        titleView.gravity = if (landscape) Gravity.START else Gravity.CENTER
        artistView.gravity = if (landscape) Gravity.START else Gravity.CENTER
        albumView.gravity = if (landscape) Gravity.START else Gravity.CENTER
        titleView.textAlignment = if (landscape) View.TEXT_ALIGNMENT_TEXT_START else View.TEXT_ALIGNMENT_CENTER
        artistView.textAlignment = if (landscape) View.TEXT_ALIGNMENT_TEXT_START else View.TEXT_ALIGNMENT_CENTER
        albumView.textAlignment = if (landscape) View.TEXT_ALIGNMENT_TEXT_START else View.TEXT_ALIGNMENT_CENTER
        clockView.gravity = if (landscape) Gravity.START else Gravity.CENTER
        widgets.textAlignment = if (landscape) View.TEXT_ALIGNMENT_TEXT_START else View.TEXT_ALIGNMENT_CENTER
        details.gravity = if (landscape) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
        val volumeLength = when { shortLandscape -> 150; landscape -> 250; else -> 168 }
        if (landscape) {
            volumeColumn.orientation = LinearLayout.VERTICAL
            volumeColumn.gravity = Gravity.CENTER
            volumeColumn.setPadding(dp(4), dp(8), dp(4), dp(8))
            volumeCaption.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(22))
            volumeLabel.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(22))
            volumeTrack.layoutParams = LinearLayout.LayoutParams(dp(58), dp(volumeLength))
            volumeSlider.rotation = -90f
            volumeSlider.layoutParams = FrameLayout.LayoutParams(dp(volumeLength), dp(44), Gravity.CENTER)
            val parent = volumeColumn.layoutParams as LayoutParams
            parent.width = dp(80); parent.height = LayoutParams.WRAP_CONTENT
            parent.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            parent.marginEnd = dp(18); parent.bottomMargin = 0
            volumeColumn.layoutParams = parent
        } else {
            volumeColumn.orientation = LinearLayout.HORIZONTAL
            volumeColumn.gravity = Gravity.CENTER
            volumeColumn.setPadding(dp(12), dp(4), dp(12), dp(4))
            volumeCaption.layoutParams = LinearLayout.LayoutParams(dp(44), LayoutParams.MATCH_PARENT)
            volumeLabel.layoutParams = LinearLayout.LayoutParams(dp(48), LayoutParams.MATCH_PARENT)
            volumeTrack.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            volumeSlider.rotation = 0f
            volumeSlider.layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44), Gravity.CENTER)
            val parent = volumeColumn.layoutParams as LayoutParams
            parent.width = LayoutParams.MATCH_PARENT; parent.height = dp(64)
            parent.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            parent.marginStart = dp(16); parent.marginEnd = dp(16); parent.bottomMargin = dp(16)
            volumeColumn.layoutParams = parent
        }
    }

    /** Reflects volume changes originating from either iOS or this screen without feeding back. */
    fun setVolumeDb(db: Float) {
        val progress = if (db <= -144f) 0 else {
            val fraction = (db.coerceIn(-30f, 0f) + 30f) / 30f
            1 + (fraction * 99f).roundToInt()
        }
        volumeSlider.progress = progress.coerceIn(0, 100)
        volumeLabel.text = context.getString(R.string.now_playing_volume_value, volumeSlider.progress)
    }

    /** Updates the card to reflect [info]. Falls back to a placeholder glyph when no artwork. */
    fun update(info: NowPlayingInfo) {
        val trackKey = listOf(info.title, info.artist, info.album)
            .map { it?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty() }
            .joinToString("\u001f")
        val hadArtworkState = artworkInitialized
        val isNewTrack = !artworkInitialized || trackKey != displayedArtworkTrackKey
        if (isNewTrack) {
            artworkInitialized = true
            displayedArtworkTrackKey = trackKey
            artworkTrackKey = null
            pendingArtworkTask?.let(widgetHandler::removeCallbacks)
            pendingArtworkTask = null
            pendingArtwork?.takeIf { !it.isRecycled }?.recycle()
            pendingArtwork = null
            missingArtworkTask?.let(widgetHandler::removeCallbacks)
            missingArtworkTask = null
            if (displayedArtworkTrackKey == trackKey && !artworkInitialized) artworkInitialized = true
        }
        val bytes = info.artwork
        if (bytes != null) {
            val candidate = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            if (candidate != null) {
                missingArtworkTask?.let(widgetHandler::removeCallbacks)
                missingArtworkTask = null
                if (isVisuallySameArtwork(candidate)) {
                    candidate.recycle()
                    artworkTrackKey = trackKey
                } else {
                    pendingArtwork?.takeIf { !it.isRecycled }?.recycle()
                    pendingArtwork = candidate
                    pendingArtworkKey = trackKey
                    pendingArtworkTask?.let(widgetHandler::removeCallbacks)
                    val task = Runnable {
                        val bitmap = pendingArtwork
                        pendingArtwork = null
                        pendingArtworkTask = null
                        if (bitmap != null && !bitmap.isRecycled && pendingArtworkKey == displayedArtworkTrackKey) {
                            artworkTrackKey = pendingArtworkKey
                            animateBackdropTo(bitmap)
                            artwork.scaleType = ImageView.ScaleType.CENTER_CROP
                            transitionArtwork(BitmapDrawable(resources, bitmap))
                            displayedArtworkSample = sampleArtwork(bitmap)
                        } else bitmap?.takeIf { !it.isRecycled }?.recycle()
                    }
                    pendingArtworkTask = task
                    widgetHandler.postDelayed(task, 180L)
                }
            } else Logger.w("NowPlayingScreen: artwork bytes (${bytes.size}B) failed to decode")
        } else if (isNewTrack && !hadArtworkState && displayedArtworkSample == null) {
            showArtworkPlaceholder()
        } else if (isNewTrack) {
            val task = Runnable {
                missingArtworkTask = null
                if (displayedArtworkTrackKey == trackKey && artworkTrackKey != trackKey) showArtworkPlaceholder()
            }
            missingArtworkTask = task
            widgetHandler.postDelayed(task, 1600L)
        }

        titleView.text = info.title ?: context.getString(R.string.now_playing_audio)
        artistView.setTextVisible(info.artist)
        albumView.setTextVisible(info.album)
    }

    private fun showArtworkPlaceholder() {
        animateBackdropTo(null)
        artwork.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val glyph = requireNotNull(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.ic_airplay)).mutate().apply {
            setTint(color(R.color.text_secondary))
        }
        transitionArtwork(glyph)
        displayedArtworkSample = null
    }

    /** Crossfades from the currently composited frame, including an in-flight transition. */
    private fun transitionArtwork(next: Drawable) {
        val current = artwork.drawable
        val previous = when (current) {
            is TransitionDrawable -> {
                val width = artwork.width.coerceAtLeast(1)
                val height = artwork.height.coerceAtLeast(1)
                BitmapDrawable(resources, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { snapshot ->
                    val canvas = Canvas(snapshot)
                    artwork.draw(canvas)
                })
            }
            null -> ColorDrawable(Color.TRANSPARENT)
            else -> current
        }
        val transition = TransitionDrawable(arrayOf(previous, next)).apply {
            isCrossFadeEnabled = true
        }
        artwork.alpha = 1f
        artwork.setImageDrawable(transition)
        transition.startTransition(420)
        artwork.postDelayed({
            if (artwork.drawable === transition) artwork.setImageDrawable(next)
        }, 440L)
    }

    /** Releases the (potentially large) artwork bitmap when the card is hidden. */
    fun clear() {
        setBedsideClockMode(false)
        pendingArtworkTask?.let(widgetHandler::removeCallbacks)
        missingArtworkTask?.let(widgetHandler::removeCallbacks)
        pendingArtwork?.takeIf { !it.isRecycled }?.recycle()
        pendingArtwork = null
        pendingArtworkTask = null
        missingArtworkTask = null
        artworkInitialized = false
        displayedArtwork = null
        displayedArtworkSample = null
        displayedArtworkTrackKey = null
        artworkTrackKey = null
        artwork.setImageDrawable(null)
    }

    private fun sampleArtwork(bitmap: android.graphics.Bitmap): IntArray {
        val side = 24
        return IntArray(side * side) { index ->
            val x = ((index % side) * bitmap.width / side).coerceAtMost(bitmap.width - 1)
            val y = ((index / side) * bitmap.height / side).coerceAtMost(bitmap.height - 1)
            bitmap.getPixel(x, y)
        }
    }

    private fun isVisuallySameArtwork(bitmap: android.graphics.Bitmap): Boolean {
        val previous = displayedArtworkSample ?: return false
        val candidate = sampleArtwork(bitmap)
        var difference = 0L
        for (i in candidate.indices) {
            difference += kotlin.math.abs(Color.red(candidate[i]) - Color.red(previous[i]))
            difference += kotlin.math.abs(Color.green(candidate[i]) - Color.green(previous[i]))
            difference += kotlin.math.abs(Color.blue(candidate[i]) - Color.blue(previous[i]))
        }
        return difference.toDouble() / (candidate.size * 3.0) < 8.0
    }

    /** Sample the cover and transition the dark glass backdrop without a sudden color jump. */
    private fun animateBackdropTo(bitmap: android.graphics.Bitmap?) {
        val sampled = bitmap?.let(::averageArtworkColor) ?: Color.rgb(25, 34, 55)
        val targetStart = blend(Color.rgb(7, 12, 24), sampled, 0.62f)
        val targetEnd = blend(Color.rgb(15, 22, 39), sampled, 0.48f)
        val fromStart = backdropStart
        val fromEnd = backdropEnd
        backdropAnimator?.cancel()
        backdropAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                val a = android.animation.ArgbEvaluator().evaluate(t, fromStart, targetStart) as Int
                val b = android.animation.ArgbEvaluator().evaluate(t, fromEnd, targetEnd) as Int
                baseBackdropStart = a
                baseBackdropEnd = b
                if (ambientBackdropAnimator?.isStarted == true) applyAmbientBackdrop()
                else renderBackdrop(a, b)
            }
            start()
        }
    }

    private fun averageArtworkColor(bitmap: android.graphics.Bitmap): Int {
        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 24).coerceAtLeast(1)
        var red = 0L; var green = 0L; var blue = 0L; var count = 0L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color); count++
                x += stepX
            }
            y += stepY
        }
        if (count == 0L) return Color.rgb(25, 34, 55)
        // Keep the artwork hue but limit brightness so white text stays legible.
        return Color.rgb((red / count).toInt().coerceAtMost(150),
            (green / count).toInt().coerceAtMost(150), (blue / count).toInt().coerceAtMost(150))
    }

    private fun blend(base: Int, tint: Int, amount: Float): Int = Color.rgb(
        (Color.red(base) * (1f - amount) + Color.red(tint) * amount).toInt(),
        (Color.green(base) * (1f - amount) + Color.green(tint) * amount).toInt(),
        (Color.blue(base) * (1f - amount) + Color.blue(tint) * amount).toInt())

    private fun isLandscapeUi(): Boolean = if (width > 0 && height > 0) width > height
        else resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun progressToDb(progress: Int): Float =
        if (progress <= 0) -144f else -30f + progress.coerceIn(1, 100) * 0.3f

    private fun TextView.setTextVisible(value: String?) {
        if (value.isNullOrBlank()) {
            text = ""
            visibility = View.INVISIBLE
        } else {
            text = value
            visibility = View.VISIBLE
        }
    }

    private fun textView(sizeSp: Float, colorRes: Int, bold: Boolean = false) = TextView(context).apply {
        setTextColor(color(colorRes))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.START
        maxLines = 2
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun color(res: Int): Int = try { context.getColor(res) } catch (_: Exception) { Color.WHITE }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val INFO_TAB_WIDTH_DP = 92
        private const val INFO_TAB_HEIGHT_DP = 44
        private const val WEATHER_RETRY_DELAY_MS = 10 * 60 * 1000L
        private const val ART_SIZE_DP = 360
        private const val VOLUME_SP = 16f
        private const val CLOCK_SP = 120f
        private const val TITLE_SP = 32f
        private const val ARTIST_SP = 22f
        private const val ALBUM_SP = 22f
    }

    private fun glassBackground(radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.argb(76, 177, 196, 230))
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), Color.argb(70, 255, 255, 255))
    }

    private fun edgeFade(color: Int, left: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        if (left) intArrayOf(color, Color.TRANSPARENT) else intArrayOf(Color.TRANSPARENT, color)
    )

    private fun updatePageTabFades() {
        if (!::pageTabsScroll.isInitialized || !::pageTabsLeftFade.isInitialized || !::pageTabsRightFade.isInitialized) return
        val contentWidth = pageTabs.width + pageTabsScroll.paddingStart + pageTabsScroll.paddingEnd
        val visibleRight = pageTabsScroll.scrollX + pageTabsScroll.width - pageTabsScroll.paddingEnd
        pageTabsLeftFade.visibility = if (pageTabsScroll.scrollX > 0) View.VISIBLE else View.GONE
        pageTabsRightFade.visibility = if (visibleRight < contentWidth) View.VISIBLE else View.GONE
    }
}

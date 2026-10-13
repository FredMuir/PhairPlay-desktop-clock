package com.phairplay.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.method.ScrollingMovementMethod
import androidx.core.content.ContextCompat
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.phairplay.R
import com.phairplay.airplay.NowPlayingInfo
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
    private var backdropStart = Color.rgb(8, 14, 27)
    private var backdropEnd = Color.rgb(24, 32, 53)
    private var backdropAnimator: ValueAnimator? = null
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
    private var contentColumn: LinearLayout? = null
    private lateinit var clockView: TextView
    private lateinit var widgets: TextView
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
    private var lastLayoutProfile: LayoutProfile? = null

    private data class LayoutProfile(
        val width: Int,
        val height: Int,
        val orientation: Int,
        val fontScale: Float,
        val idleClockMode: Boolean,
        val showClockSeconds: Boolean
    )
    @Volatile private var weatherText: String? = null
    @Volatile private var agendaText: String? = null
    private var lastWeatherCity = ""
    @Volatile private var agendaQueryPending = false
    private var lastAgendaQueryAt = 0L
    private val weatherExecutor = Executors.newSingleThreadExecutor()
    private val clockTick = object : Runnable { override fun run() { refreshWidgets(); widgetHandler.postDelayed(this, 1000) } }

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(backdropStart, backdropEnd, Color.rgb(8, 12, 22)))

        val returnButton = android.widget.Button(context).apply {
            text = context.getString(R.string.now_playing_return)
            isFocusable = true
            background = glassBackground(28)
            setTextColor(Color.WHITE)
            setPadding(dp(18), 0, dp(18), 0)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { onReturnToApp?.invoke() }
        }
        addView(returnButton, LayoutParams(LayoutParams.WRAP_CONTENT, dp(52), Gravity.TOP or Gravity.START).apply {
            topMargin = dp(16)
            marginStart = dp(20)
        })

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        contentColumn = column
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
            id = R.id.now_playing_clock
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            maxLines = 1
            gravity = Gravity.START
            // Keep a little trailing room for glyph overhang and OEM TextView differences.
            setPadding(0, 0, dp(12), dp(2))
        }
        titleView = textView(TITLE_SP, R.color.text_primary, bold = true).apply {
            id = R.id.now_playing_track_title
            setPadding(0, dp(16), 0, 0)
            minHeight = dp(82)
            maxLines = Int.MAX_VALUE
            movementMethod = ScrollingMovementMethod()
            maxHeight = dp(118)
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            isVerticalScrollBarEnabled = true
        }
        artistView = textView(ARTIST_SP, R.color.text_secondary).apply {
            setPadding(0, dp(12), 0, 0)
            minHeight = dp(40)
        }
        albumView = textView(ALBUM_SP, R.color.text_tertiary).apply {
            setPadding(0, dp(2), 0, 0)
            minHeight = dp(42)
        }
        volumeCaption = textView(VOLUME_SP, R.color.text_secondary).apply {
            text = context.getString(R.string.now_playing_volume_caption)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dp(104)
        }
        volumeLabel = textView(VOLUME_SP, R.color.text_secondary).apply {
            text = context.getString(R.string.now_playing_volume_value, 100)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dp(88)
        }
        volumeSlider = SeekBar(context).apply {
            id = R.id.now_playing_volume_slider
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
            id = R.id.now_playing_volume_column
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glassBackground(30)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        volumeTrack = FrameLayout(context).apply {
            id = R.id.now_playing_volume_track
            addView(volumeSlider, FrameLayout.LayoutParams(dp(250), dp(44), Gravity.CENTER))
        }
        volumeColumn.addView(volumeCaption, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(22)))
        volumeColumn.addView(volumeLabel, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(22)))
        volumeColumn.addView(volumeTrack, LinearLayout.LayoutParams(dp(58), dp(250)))
        widgets = textView(22f, R.color.text_secondary).apply {
            setPadding(0, dp(4), 0, dp(8))
            maxLines = Int.MAX_VALUE
            movementMethod = ScrollingMovementMethod()
            isVerticalScrollBarEnabled = true
            gravity = Gravity.START
        }

        details.addView(clockView)
        details.addView(widgets)
        details.addView(titleView)
        details.addView(artistView)
        details.addView(albumView)
        column.addView(artwork)
        column.addView(details)

        addView(
            column,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        )
        addView(volumeColumn, LayoutParams(dp(80), LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(18)
        })
        widgetHandler.post(clockTick)
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adaptOrientation(column) }
    }

    /** Connects the on-screen volume slider to the receiver's local AudioTrack gain. */
    fun setVolumeChangeHandler(handler: (Float) -> Unit) {
        onVolumeChange = handler
    }

    fun setReturnToAppHandler(handler: () -> Unit) {
        onReturnToApp = handler
    }

    /** Turns the playback card into a standalone bedside clock when there is no active stream. */
    fun setBedsideClockMode(idle: Boolean) {
        idleClockMode = idle
        artwork.visibility = if (idle) View.GONE else View.VISIBLE
        titleView.visibility = if (idle) View.GONE else View.VISIBLE
        artistView.visibility = if (idle) View.GONE else View.VISIBLE
        albumView.visibility = if (idle) View.GONE else View.VISIBLE
        volumeColumn.visibility = if (idle) View.GONE else View.VISIBLE
        contentColumn?.let { if (width > 0) adaptOrientation(it) }
    }

    fun setWidgetSettings(clock: Boolean, date: Boolean, agenda: Boolean, weather: Boolean, city: String, style: String, seconds: Boolean = false) {
        clockEnabled = clock; dateEnabled = date; agendaEnabled = agenda; weatherEnabled = weather; weatherCity = city; widgetStyle = style
        val secondsChanged = showClockSeconds != seconds
        showClockSeconds = seconds
        val weatherNeedsRetry = weatherFailed && !weatherLoadPending && weatherRetryScheduledFor == null
        if (weather && city.isNotBlank() && (city != lastWeatherCity || weatherNeedsRetry)) loadWeather(city)
        refreshWidgets()
        if (secondsChanged) contentColumn?.let { if (width > 0) adaptOrientation(it) }
    }

    private fun refreshWidgets() {
        if (!::widgets.isInitialized) return
        val lines = mutableListOf<String>()
        val nowMillis = System.currentTimeMillis()
        val now = java.util.Date(nowMillis)
        val locale = java.util.Locale.getDefault()
        val clockText = if (clockEnabled) java.text.SimpleDateFormat(
            if (showClockSeconds) "HH:mm:ss" else "HH:mm", locale).format(now) else ""
        if (clockView.text.toString() != clockText) {
            clockView.text = clockText
        }
        clockView.visibility = if (clockEnabled) View.VISIBLE else View.GONE
        if (dateEnabled) lines += java.text.SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMd EEEE"), locale).format(now)
        if (weatherEnabled) lines += if (weatherCity.isBlank()) context.getString(R.string.now_playing_weather_city_missing)
            else weatherText ?: context.getString(R.string.now_playing_weather_loading, weatherCity)
        if (agendaEnabled) {
            if (nowMillis - lastAgendaQueryAt >= 60_000L) loadAgenda(nowMillis)
            lines += agendaText ?: context.getString(R.string.now_playing_agenda_loading)
        } else {
            agendaText = null
            lastAgendaQueryAt = 0L
        }
        val widgetText = lines.joinToString("\n")
        if (widgets.text.toString() != widgetText) widgets.text = widgetText
        widgets.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        val bg = if (widgetStyle == "CARD") glassBackground(24) else null
        widgets.background = bg
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
                val forecast = org.json.JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto"))
                    .getJSONObject("current")
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
                context.getString(R.string.now_playing_weather_result, city, context.getString(condition), temp)
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

    private fun adaptOrientation(column: LinearLayout) {
        val landscape = isLandscapeUi()
        val bedsideLandscape = idleClockMode && landscape
        column.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val density = resources.displayMetrics.density
        val availableWidth = (width / density).toInt().coerceAtLeast(320)
        val availableWidthDp = availableWidth.toFloat()
        val availableHeight = (height / density).toInt().coerceAtLeast(320)
        // Phone landscape windows have little vertical room. The TV/tablet minimum art
        // size and type scale otherwise make the centered card extend beyond the screen.
        val shortLandscape = landscape && availableHeight < 500
        val layoutProfile = LayoutProfile(
            width = width,
            height = height,
            orientation = resources.configuration.orientation,
            fontScale = resources.configuration.fontScale,
            idleClockMode = idleClockMode,
            showClockSeconds = showClockSeconds
        )
        if (layoutProfile == lastLayoutProfile) return
        val art = if (landscape) (availableHeight * if (shortLandscape) 0.42f else 0.45f).toInt()
            .coerceIn(if (shortLandscape) 160 else 220, if (shortLandscape) 220 else 360)
            else minOf((availableWidth * 0.58f).toInt(), ((availableHeight - 230).coerceAtLeast(250) * 0.34f).toInt()).coerceIn(144, 360)
        val baseClockSize = when {
            idleClockMode && landscape && shortLandscape -> 112f
            idleClockMode && landscape -> 164f
            idleClockMode -> 112f
            landscape && shortLandscape -> 64f
            landscape -> 76f
            else -> 84f
        }
        val clockColumnWidthDp = if (landscape) {
            minOf(1050f, availableWidthDp - 98f - 64f).coerceAtLeast(320f)
        } else {
            minOf(500f, availableWidthDp - 32f).coerceAtLeast(240f)
        }
        val clockTextWidthDp = when {
            landscape && idleClockMode -> ((clockColumnWidthDp - 44f - 28f).coerceAtLeast(180f)) * 0.64f
            landscape -> (clockColumnWidthDp - art - 44f).coerceAtLeast(180f)
            else -> clockColumnWidthDp
        }
        val clockSize = if (showClockSeconds) {
            // Seconds add three digits and the tablet's landscape bedside layout allocates
            // the clock a weighted fraction of the content column. Keep extra width in reserve:
            // a fit based on the nominal advance width can still clip the final digit on device.
            minOf(baseClockSize, (clockTextWidthDp * 0.18f / resources.configuration.fontScale).coerceAtLeast(48f))
        } else {
            baseClockSize
        }
        // The timestamp can gain three characters when seconds are enabled. Let TextView
        // Keep a conservative width for every digit and account for accessibility font scale.
        // Changing text size only when the layout profile changes avoids a measure/layout loop
        // while the clock's seconds update once per second.
        val currentClockSizeSp = clockView.textSize / resources.displayMetrics.scaledDensity
        if (kotlin.math.abs(currentClockSizeSp - clockSize) > 0.5f) {
            clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSize)
        }
        val targetTitleMaxHeight = dp(if (shortLandscape) 72 else if (landscape) 170 else 84)
        if (titleView.maxHeight != targetTitleMaxHeight) titleView.maxHeight = targetTitleMaxHeight
        (artwork.layoutParams as LinearLayout.LayoutParams).let { params ->
            val targetArtSize = dp(art)
            if (params.width != targetArtSize || params.height != targetArtSize) {
                params.width = targetArtSize
                params.height = targetArtSize
                artwork.layoutParams = params
            }
        }
        // Keep the content column clear of the volume rail. On wider tablets use the
        // centered 1050dp composition; on narrower landscape windows reserve both the rail
        // and a 32dp gap, then pin the content to the left safe area.
        val volumeRailReserve = if (landscape) 98f else 0f // Portrait uses a dedicated bottom volume bar.
        val sideInset = 32f
        val centeredLayoutMinWidth = 1050f + volumeRailReserve + sideInset * 2f
        val compactLandscape = landscape && (availableWidthDp < centeredLayoutMinWidth || availableHeight < 500)
        val contentWidth = when {
            landscape -> minOf(1050f, availableWidthDp - volumeRailReserve - sideInset * 2f).coerceAtLeast(320f)
            else -> minOf(500f, availableWidthDp - 32f).coerceAtLeast(240f)
        }
        val maxWidth = dp(contentWidth.roundToInt())
        val lp = column.layoutParams as? LayoutParams ?: return
        val desiredGravity = if (compactLandscape) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
        val desiredMarginStart = if (compactLandscape) dp(sideInset.roundToInt()) else 0
        val desiredBottomMargin = if (landscape) 0 else dp(78)
        if (lp.width != maxWidth || lp.gravity != desiredGravity || lp.marginStart != desiredMarginStart || lp.bottomMargin != desiredBottomMargin) {
            lp.width = maxWidth
            lp.gravity = desiredGravity
            lp.marginStart = desiredMarginStart
            lp.bottomMargin = desiredBottomMargin
            column.layoutParams = lp
        }
        val details = column.getChildAt(1) as? LinearLayout ?: return
        column.gravity = if (landscape) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER_HORIZONTAL
        details.translationY = if (idleClockMode || shortLandscape) 0f else dp(if (landscape) -24 else 0).toFloat()
        (details.layoutParams as LinearLayout.LayoutParams).let { params ->
            val targetWidth = if (landscape) 0 else LayoutParams.MATCH_PARENT
            val targetWeight = if (landscape) 1f else 0f
            val targetMarginStart = if (landscape) dp(44) else 0
            if (params.width != targetWidth || params.height != LayoutParams.WRAP_CONTENT ||
                params.weight != targetWeight || params.marginStart != targetMarginStart
            ) {
                params.width = targetWidth
                params.height = LayoutParams.WRAP_CONTENT
                params.weight = targetWeight
                params.marginStart = targetMarginStart
                details.layoutParams = params
            }
        }
        details.orientation = if (bedsideLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        details.gravity = if (landscape) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER

        val clockParams = clockView.layoutParams as LinearLayout.LayoutParams
        val widgetsParams = widgets.layoutParams as LinearLayout.LayoutParams
        val targetClockWidth = if (bedsideLandscape) 0 else LayoutParams.MATCH_PARENT
        val targetClockWeight = if (bedsideLandscape) 0.64f else 0f
        val targetClockMargin = if (bedsideLandscape) dp(28) else 0
        val targetWidgetsWidth = if (bedsideLandscape) 0 else LayoutParams.MATCH_PARENT
        val targetWidgetsWeight = if (bedsideLandscape) 0.36f else 0f
        val targetWidgetsMargin = if (bedsideLandscape) dp(28) else 0
        val clockParamsChanged = clockParams.width != targetClockWidth ||
            clockParams.height != LayoutParams.WRAP_CONTENT || clockParams.weight != targetClockWeight ||
            clockParams.marginEnd != targetClockMargin
        val widgetsParamsChanged = widgetsParams.width != targetWidgetsWidth ||
            widgetsParams.height != LayoutParams.WRAP_CONTENT || widgetsParams.weight != targetWidgetsWeight ||
            widgetsParams.marginStart != targetWidgetsMargin
        if (clockParamsChanged) {
            clockParams.width = targetClockWidth
            clockParams.height = LayoutParams.WRAP_CONTENT
            clockParams.weight = targetClockWeight
            clockParams.marginEnd = targetClockMargin
            clockView.layoutParams = clockParams
        }
        if (widgetsParamsChanged) {
            widgetsParams.width = targetWidgetsWidth
            widgetsParams.height = LayoutParams.WRAP_CONTENT
            widgetsParams.weight = targetWidgetsWeight
            widgetsParams.marginStart = targetWidgetsMargin
            widgets.layoutParams = widgetsParams
        }
        if (bedsideLandscape) {
            setWidgetsTextSizeIfChanged(24f)
            if (widgets.maxLines != Int.MAX_VALUE) widgets.maxLines = Int.MAX_VALUE
            val targetWidgetsMaxHeight = dp(if (shortLandscape) 96 else 180)
            if (widgets.maxHeight != targetWidgetsMaxHeight) widgets.maxHeight = targetWidgetsMaxHeight
            widgets.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        } else {
            setWidgetsTextSizeIfChanged(if (landscape) 18f else 22f)
            if (widgets.maxLines != Int.MAX_VALUE) widgets.maxLines = Int.MAX_VALUE
            val targetWidgetsMaxHeight = dp(240)
            if (widgets.maxHeight != targetWidgetsMaxHeight) widgets.maxHeight = targetWidgetsMaxHeight
            widgets.gravity = Gravity.START
        }
        val textGravity = if (landscape) Gravity.START else Gravity.CENTER
        clockView.gravity = textGravity
        widgets.gravity = textGravity
        titleView.gravity = textGravity
        artistView.gravity = textGravity
        albumView.gravity = textGravity
        details.gravity = if (landscape) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
        volumeColumn.orientation = if (landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        volumeColumn.gravity = Gravity.CENTER
        volumeColumn.setPadding(if (landscape) dp(4) else dp(12), if (landscape) dp(8) else dp(4), if (landscape) dp(4) else dp(12), if (landscape) dp(8) else dp(4))
        val volumeParams = volumeColumn.layoutParams as? LayoutParams
        volumeParams?.let {
            val targetWidth = if (landscape) dp(80) else LayoutParams.WRAP_CONTENT
            val targetGravity = if (landscape) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val targetEndMargin = if (landscape) dp(18) else 0
            // Leave room for gesture/three-button navigation when immersive bars are
            // temporarily visible (for example after a swipe or during Activity launch).
            val targetBottomMargin = if (landscape) 0 else dp(96)
            if (it.width != targetWidth || it.gravity != targetGravity || it.marginEnd != targetEndMargin || it.bottomMargin != targetBottomMargin) {
                it.width = targetWidth
                it.gravity = targetGravity
                it.marginEnd = targetEndMargin
                it.bottomMargin = targetBottomMargin
                volumeColumn.layoutParams = it
            }
        }
        val volumeLength = when { shortLandscape -> 150; landscape -> 250; else -> (availableWidth - 176).coerceIn(132, 220) }
        val labelParams = volumeLabel.layoutParams as LinearLayout.LayoutParams
        val captionParams = volumeCaption.layoutParams as LinearLayout.LayoutParams
        val targetLabelWidth = if (landscape) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
        val targetLabelHeight = if (landscape) dp(22) else LayoutParams.WRAP_CONTENT
        val targetLabelMargin = if (landscape) 0 else dp(8)
        val targetCaptionWidth = if (landscape) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
        val targetCaptionHeight = if (landscape) dp(22) else LayoutParams.WRAP_CONTENT
        val targetCaptionMargin = if (landscape) 0 else dp(8)
        val labelChanged = labelParams.width != targetLabelWidth || labelParams.height != targetLabelHeight || labelParams.marginStart != targetLabelMargin
        val captionChanged = captionParams.width != targetCaptionWidth || captionParams.height != targetCaptionHeight || captionParams.marginEnd != targetCaptionMargin
        if (landscape) {
            captionParams.width = targetCaptionWidth; captionParams.height = targetCaptionHeight
            captionParams.marginEnd = targetCaptionMargin
            labelParams.width = targetLabelWidth; labelParams.height = targetLabelHeight
            labelParams.marginStart = targetLabelMargin
        } else {
            captionParams.width = targetCaptionWidth; captionParams.height = targetCaptionHeight
            captionParams.marginEnd = targetCaptionMargin
            labelParams.width = targetLabelWidth; labelParams.height = targetLabelHeight
            labelParams.marginStart = targetLabelMargin
        }
        if (captionChanged) volumeCaption.layoutParams = captionParams
        if (labelChanged) volumeLabel.layoutParams = labelParams
        val trackWidth = if (landscape) dp(58) else dp(volumeLength)
        val trackHeight = if (landscape) dp(volumeLength) else dp(48)
        val trackParams = volumeTrack.layoutParams as LinearLayout.LayoutParams
        if (trackParams.width != trackWidth || trackParams.height != trackHeight) {
            trackParams.width = trackWidth
            trackParams.height = trackHeight
            volumeTrack.layoutParams = trackParams
        }
        val targetRotation = if (landscape) -90f else 0f
        val sliderWidth = if (landscape) dp(volumeLength) else LayoutParams.MATCH_PARENT
        val sliderParams = volumeSlider.layoutParams as FrameLayout.LayoutParams
        if (volumeSlider.rotation != targetRotation || sliderParams.width != sliderWidth || sliderParams.height != dp(44)) {
            volumeSlider.rotation = targetRotation
            sliderParams.width = sliderWidth
            sliderParams.height = dp(44)
            volumeSlider.layoutParams = sliderParams
        }
        lastLayoutProfile = layoutProfile
    }

    private fun setWidgetsTextSizeIfChanged(sizeSp: Float) {
        val currentSizeSp = widgets.textSize / resources.displayMetrics.scaledDensity
        if (kotlin.math.abs(currentSizeSp - sizeSp) > 0.5f) {
            widgets.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
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
        val isNewTrack = !artworkInitialized || trackKey != displayedArtworkTrackKey
        if (isNewTrack) {
            artworkInitialized = true
            displayedArtworkTrackKey = trackKey
            displayedArtwork = info.artwork?.copyOf()
            val bitmap = info.artwork?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
            if (bitmap != null && isVisuallySameArtwork(bitmap)) {
                // The decoded frame is unused; release it rather than accumulating one Bitmap per
                // metadata refresh. Sampling tolerance ignores compression noise that used to
                // cause the same cover to blink on every metadata packet.
                bitmap.recycle()
                Logger.d("NowPlayingScreen: visually unchanged artwork; skipped redraw")
            } else {
                if (bitmap != null) {
                    animateBackdropTo(bitmap)
                    artwork.scaleType = ImageView.ScaleType.CENTER_CROP
                    transitionArtwork(BitmapDrawable(resources, bitmap))
                    displayedArtworkSample = sampleArtwork(bitmap)
                } else {
                    animateBackdropTo(null)
                    // No artwork from the sender (typical for raw system audio) — show the AirPlay glyph.
                    artwork.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val glyph = requireNotNull(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.ic_airplay)).mutate().apply {
                        setTint(color(R.color.text_secondary))
                    }
                    transitionArtwork(glyph)
                    displayedArtworkSample = null
                    if (info.artwork != null) {
                        Logger.w("NowPlayingScreen: artwork bytes (${info.artwork.size}B) failed to decode")
                    }
                }
            }
        }

        titleView.text = info.title ?: context.getString(R.string.now_playing_audio)
        titleView.scrollTo(0, 0)
        artistView.setTextVisible(info.artist)
        albumView.setTextVisible(info.album)
    }

    /** Crossfades only on a track-key change; metadata refreshes never touch the artwork view. */
    private fun transitionArtwork(next: Drawable) {
        val current = artwork.drawable
        val previous = when (current) {
            is TransitionDrawable -> current.getDrawable(1)
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
        artworkInitialized = false
        displayedArtwork = null
        displayedArtworkSample = null
        displayedArtworkTrackKey = null
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
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(a, b, blend(Color.rgb(8, 12, 22), b, 0.28f)))
                backdropStart = a
                backdropEnd = b
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
}

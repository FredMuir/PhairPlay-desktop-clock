package com.phairplay

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import com.phairplay.airplay.NowPlayingInfo
import com.phairplay.ui.NowPlayingScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivityTest — Instrumented tests for MainActivity.
 *
 * WHY: Unit tests can mock Android APIs, but some things must be tested on
 * a real Android device (or emulator) to verify they actually work:
 * - Does the Activity start without crashing?
 * - Is the home screen visible on startup?
 * - Does the UI respond correctly to orientation changes?
 *
 * HOW: These are instrumented tests — they run on a real Android device or
 * emulator (not on the development machine). Run with:
 *   ./gradlew connectedAndroidTest
 *
 * The [ActivityScenarioRule] automatically starts and stops the Activity
 * for each test.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    // ActivityScenarioRule starts the Activity before each test and stops it after
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    /**
     * Test: MainActivity starts without crashing.
     *
     * WHY: This is Acceptance Criterion AC-1.3 / AC-1.4.
     * The most basic requirement: the app must start.
     * If this test fails, something is fundamentally broken.
     */
    @Test
    fun mainActivity_startsWithoutCrash() {
        // If we reach this line, the Activity started without throwing an exception.
        // ActivityScenarioRule would have failed the test during setup if a crash occurred.
    }

    /**
     * Test: The home screen title is visible when the app starts.
     *
     * WHY: The home screen must be shown on startup.
     * If the wrong screen is shown (or nothing at all), the user won't know
     * what to do.
     */
    @Test
    fun mainActivity_showsHomeTitleOnStart() {
        assertViewDisplayed(R.id.text_page_title)
    }

    /** Verify the primary page and navigation remain visible after a configuration rotation. */
    @Test
    fun mainActivity_keepsHomeVisibleAcrossOrientationChanges() {
        try {
            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
            assertViewDisplayed(R.id.nav_item_devices)
            assertViewDisplayed(R.id.text_page_title)

            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
            assertViewDisplayed(R.id.nav_item_home)
            assertViewDisplayed(R.id.text_page_title)
        } finally {
            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun settingsScreen_remainsReachableAndVisibleAcrossOrientations() {
        assertPageAcrossOrientations(R.id.nav_item_settings, R.id.header_display)
    }

    @Test
    fun devicesScreen_remainsReachableAndVisibleAcrossOrientations() {
        assertPageAcrossOrientations(R.id.nav_item_devices, R.id.text_devices_title)
    }

    @Test
    fun devicesSubtitleDoesNotEllipsizeOnNarrowScreens() {
        clickView(R.id.nav_item_devices)
        assertViewDisplayed(R.id.text_devices_subtitle)
        activityRule.scenario.onActivity { activity ->
            val subtitle = activity.findViewById<android.widget.TextView>(R.id.text_devices_subtitle)
            assertEquals(activity.getString(R.string.devices_subtitle), subtitle.text.toString())
            val ellipsisCount = (0 until subtitle.layout.lineCount).sumOf { subtitle.layout.getEllipsisCount(it) }
            assertEquals("Device discovery help text is truncated", 0, ellipsisCount)
        }
    }

    @Test
    fun nowPlayingTitleAndVolumeControlsStayOnscreenInBothOrientations() {
        val info = NowPlayingInfo(
            senderName = "Instrumented sender",
            title = "A long track title used to verify the playback overlay keeps its content in bounds",
            artist = "Instrumented artist",
            album = "Instrumented album"
        )
        try {
            publishNowPlaying(info)
            assertOverlayControlsFullyVisible()

            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
            publishNowPlaying(info)
            assertOverlayControlsFullyVisible()

            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
            publishNowPlaying(info)
            assertOverlayControlsFullyVisible()
        } finally {
            publishNowPlaying(null)
            activityRule.scenario.onActivity {
                it.hideStreamingScreen()
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun bedsideClockWithSecondsFitsInBothOrientations() {
        val info = NowPlayingInfo(senderName = "Clock layout test")
        try {
            publishNowPlaying(info)
            enableClockSecondsForLayoutTest()
            assertClockTextFits()

            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
            publishNowPlaying(info)
            enableClockSecondsForLayoutTest()
            assertClockTextFits()

            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
            publishNowPlaying(info)
            enableClockSecondsForLayoutTest()
            assertClockTextFits()
        } finally {
            publishNowPlaying(null)
            activityRule.scenario.onActivity {
                it.hideStreamingScreen()
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun enableClockSecondsForLayoutTest() {
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<android.view.ViewGroup>(R.id.streaming_container)
            val screen = (0 until container.childCount)
                .map(container::getChildAt)
                .filterIsInstance<NowPlayingScreen>()
                .single()
            screen.setWidgetSettings(
                clock = true,
                date = false,
                agenda = false,
                weather = false,
                city = "",
                style = "MINIMAL",
                seconds = true
            )
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun assertClockTextFits() {
        activityRule.scenario.onActivity { activity ->
            val clock = activity.findViewById<android.widget.TextView>(R.id.now_playing_clock)
            assertEquals(View.VISIBLE, clock.visibility)
            assertTrue("Clock should include seconds: ${clock.text}", clock.text.toString().matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
            val layout = clock.layout ?: throw AssertionError("Clock has no measured text layout")
            val availableTextWidth = clock.width - clock.paddingLeft - clock.paddingRight
            assertTrue(
                "Clock text is clipped in orientation ${activity.resources.configuration.orientation}: " +
                    "text=${clock.text} lineWidth=${layout.getLineWidth(0)} available=$availableTextWidth " +
                    "bounds=${clock.left},${clock.top}-${clock.right},${clock.bottom}",
                layout.getLineWidth(0) <= availableTextWidth - activity.resources.displayMetrics.density * 4f
            )
            val visible = Rect()
            assertTrue("Clock is outside the visible screen bounds", clock.getGlobalVisibleRect(visible))
            assertEquals("Clock is clipped horizontally: $visible", clock.width, visible.width())
            assertEquals("Clock is clipped vertically: $visible", clock.height, visible.height())
        }
    }

    private fun publishNowPlaying(info: NowPlayingInfo?) {
        activityRule.scenario.onActivity { activity ->
            if (info == null) activity.hideStreamingScreen()
            else activity.showNowPlayingScreen(info)
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun assertOverlayControlsFullyVisible() {
        listOf(
            R.id.now_playing_track_title,
            R.id.now_playing_volume_column,
            R.id.now_playing_volume_track,
            R.id.now_playing_volume_slider
        ).forEach { id ->
            activityRule.scenario.onActivity { activity ->
                val view = activity.findViewById<View>(id)
                assertEquals("View $id is not visible", View.VISIBLE, view.visibility)
                val visible = Rect()
                val hasVisibleBounds = view.getGlobalVisibleRect(visible)
                assertTrue(
                    "View $id (${activity.resources.getResourceEntryName(id)}) is not fully visible: " +
                        "view=${view.width}x${view.height} bounds=${view.left},${view.top}-${view.right},${view.bottom} " +
                        "visible=$visible parent=${(view.parent as? View)?.width}x${(view.parent as? View)?.height} " +
                        "root=${view.rootView.width}x${view.rootView.height}",
                    hasVisibleBounds
                )
                // getGlobalVisibleRect reports the transformed bounds for rotated views,
                // while width/height remain the unrotated layout dimensions.
                val quarterTurn = view.rotation % 180f != 0f
                val expectedWidth = if (quarterTurn) view.height else view.width
                val expectedHeight = if (quarterTurn) view.width else view.height
                assertTrue(
                    "View $id is clipped horizontally in orientation ${activity.resources.configuration.orientation}: " +
                        "expected=$expectedWidth actual=${visible.width()} bounds=$visible view=${view.width}x${view.height}",
                    kotlin.math.abs(expectedWidth - visible.width()) <= 1
                )
                assertTrue(
                    "View $id is clipped vertically in orientation ${activity.resources.configuration.orientation}: " +
                        "expected=$expectedHeight actual=${visible.height()} bounds=$visible view=${view.width}x${view.height}",
                    kotlin.math.abs(expectedHeight - visible.height()) <= 1
                )
            }
        }
    }

    private fun assertPageAcrossOrientations(navItemId: Int, pageMarkerId: Int) {
        try {
            clickView(navItemId)
            assertViewDisplayed(pageMarkerId)

            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
            assertViewDisplayed(navItemId)
            assertViewDisplayed(pageMarkerId)

            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
            assertViewDisplayed(navItemId)
            assertViewDisplayed(pageMarkerId)
        } finally {
            activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun clickView(id: Int) {
        activityRule.scenario.onActivity { activity ->
            val view = requireNotNull(activity.findViewById<View>(id)) {
                "View ${activity.resources.getResourceEntryName(id)} not found"
            }
            assertTrue("View ${activity.resources.getResourceEntryName(id)} is not clickable", view.isClickable)
            assertTrue("Click was not handled by ${activity.resources.getResourceEntryName(id)}", view.performClick())
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun assertViewDisplayed(id: Int) {
        activityRule.scenario.onActivity { activity ->
            val view = activity.findViewById<View>(id)
            assertTrue(
                "View ${activity.resources.getResourceEntryName(id)} is not displayed",
                view?.isShown == true
            )
        }
    }

    private fun awaitOrientation(expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        var actual = Configuration.ORIENTATION_UNDEFINED
        do {
            activityRule.scenario.onActivity { actual = it.resources.configuration.orientation }
            if (actual == expected) return
            SystemClock.sleep(100L)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals(expected, actual)
    }

    /**
     * Test: The StreamingScreen container is hidden when the app starts.
     *
     * WHY: The StreamingScreen should only be visible when streaming is active.
     * If it's visible at startup, it would show a black surface over the UI.
     */
    @Test
    fun mainActivity_streamingContainerHiddenOnStart() {
        // The streaming container exists in the layout but has visibility GONE initially
        activityRule.scenario.onActivity { activity ->
            val container = requireNotNull(activity.findViewById<View>(R.id.streaming_container))
            assertTrue("Streaming container should start hidden", !container.isShown)
        }
    }
}

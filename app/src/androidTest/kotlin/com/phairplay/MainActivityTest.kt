package com.phairplay

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ScrollView
import com.phairplay.R
import com.phairplay.airplay.NowPlayingInfo
import com.phairplay.ui.NowPlayingScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivityTest — Instrumented tests for MainActivity.
 *
 * WHY: Unit tests can mock Android APIs, but some things must be tested on
 * a real Android device (or emulator) to verify they actually work:
 * - Does the Activity start without crashing?
 * - Is the WaitingScreen visible on startup?
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
     * Test: The WaitingScreen container is visible when the app starts.
     *
     * WHY: Acceptance Criterion AC-1.5 — the waiting screen must be shown on startup.
     * If the wrong screen is shown (or nothing at all), the user won't know
     * what to do.
     */
    @Test
    fun mainActivity_showsHomeNavigationOnStart() {
        activityRule.scenario.onActivity { activity ->
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.nav_item_home).visibility)
        }
    }

    /**
     * Test: The StreamingScreen container is hidden when the app starts.
     *
     * WHY: The StreamingScreen should only be visible when streaming is active.
     * If it's visible at startup, it would show a black surface over the UI.
     */
    @Test
    fun mainActivity_streamingContainerHiddenOnStart() {
        activityRule.scenario.onActivity { activity ->
            assertEquals(View.GONE, activity.findViewById<View>(R.id.streaming_container).visibility)
        }
    }

    @Test
    fun nowPlayingOverlayShowsLongTrackMetadata() {
        val title = "A deliberately long track title that should wrap instead of clipping on a narrow screen"
        val info = NowPlayingInfo(
            senderName = "UI layout check",
            title = title,
            artist = "Artist with a long name for layout validation",
            album = "Album metadata that may wrap on compact portrait displays",
        )
        activityRule.scenario.onActivity { activity ->
            val currentInfo = MainActivity::class.java.getDeclaredField("currentNowPlaying").apply {
                isAccessible = true
            }
            currentInfo.set(activity, info)
            activity.showNowPlayingScreen(info)

            val container = activity.findViewById<ViewGroup>(R.id.streaming_container)
            val screen = (0 until container.childCount)
                .map { container.getChildAt(it) }
                .filterIsInstance<NowPlayingScreen>()
                .firstOrNull()
            assertNotNull(screen)
            assertEquals(View.VISIBLE, screen!!.visibility)

            val titleView = findTextView(screen) { it.text.toString() == title }
            assertNotNull(titleView)
            assertEquals(View.VISIBLE, titleView!!.visibility)
            assertNotNull(findScrollView(screen))
        }
    }

    private fun findTextView(root: View, predicate: (TextView) -> Boolean): TextView? {
        if (root is TextView && predicate(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findTextView(root.getChildAt(index), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun findScrollView(root: View): ScrollView? {
        if (root is ScrollView) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findScrollView(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}

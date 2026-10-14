package com.phairplay

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.phairplay.ui.NowPlayingScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockInfoDisplayTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun optionalBatteryAndNextAlarmAppearInBedsideClock() {
        activityRule.scenario.onActivity { activity ->
            val screen = NowPlayingScreen(activity).apply {
                setWidgetSettings(clock = false, date = false, agenda = false, weather = false, city = "", style = "MINIMAL")
                setClockInfoSettings(battery = true, nextAlarm = true)
                setBedsideClockMode(true)
            }
            activity.addContentView(screen, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        activityRule.scenario.onActivity { activity ->
            val screen = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            val text = collectText(screen)
            val batteryPrefix = activity.getString(R.string.now_playing_battery, 50).substringBefore("50")
            val alarmPrefix = activity.getString(R.string.now_playing_alarm, "TEST").substringBefore("TEST")
            assertTrue("battery status should be displayed", text.contains(batteryPrefix))
            assertTrue("next alarm status should be displayed", text.contains(alarmPrefix))
        }
    }

    private fun collectText(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { collectText(view.getChildAt(it)) }
        else -> ""
    }
}

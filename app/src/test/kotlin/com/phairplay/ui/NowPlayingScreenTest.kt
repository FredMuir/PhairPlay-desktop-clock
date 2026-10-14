package com.phairplay.ui

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import com.phairplay.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NowPlayingScreenTest {

    @Test
    fun `transport buttons respect remote control and bedside clock settings`() {
        val screen = NowPlayingScreen(RuntimeEnvironment.getApplication())
        val sentCommands = mutableListOf<String>()
        screen.setRemoteCommandHandler(sentCommands::add)

        val previous = button(screen, R.string.now_playing_previous)
        val toggle = button(screen, R.string.now_playing_pause)
        val next = button(screen, R.string.now_playing_next)
        val controls = previous.parent as ViewGroup

        assertEquals(View.GONE, controls.visibility)
        assertTrue(!previous.isEnabled && !toggle.isEnabled && !next.isEnabled)
        screen.setRemoteControlsEnabled(true)
        assertEquals(View.VISIBLE, controls.visibility)
        assertTrue(!previous.isEnabled && !toggle.isEnabled && !next.isEnabled)
        screen.setRemoteControlsAvailable(true)
        assertEquals(View.VISIBLE, controls.visibility)
        assertTrue(previous.isEnabled && toggle.isEnabled && next.isEnabled)

        screen.measure(exactly(1200), exactly(2000))
        screen.layout(0, 0, 1200, 2000)
        screen.measure(exactly(1200), exactly(2000))
        screen.layout(0, 0, 1200, 2000)
        assertTrue(previous.left < toggle.left)
        assertTrue(toggle.left < next.left)

        previous.performClick()
        toggle.performClick()
        next.performClick()
        assertEquals(listOf("previtem", "playpause", "nextitem"), sentCommands)

        screen.setBedsideClockMode(true)
        assertEquals(View.GONE, controls.visibility)
        assertTrue(!previous.isEnabled && !toggle.isEnabled && !next.isEnabled)
        screen.setBedsideClockMode(false)
        assertEquals(View.VISIBLE, controls.visibility)
        screen.setRemoteControlsAvailable(false)
        assertEquals(View.VISIBLE, controls.visibility)
        assertTrue(!previous.isEnabled && !toggle.isEnabled && !next.isEnabled)
    }

    @Test
    fun `transport icon has distinct normal pressed focused and disabled colors`() {
        val screen = NowPlayingScreen(RuntimeEnvironment.getApplication())
        val button = button(screen, R.string.now_playing_previous)
        val tint = button.imageTintList
        assertNotNull(tint)
        assertEquals(
            screen.context.getColor(R.color.text_primary),
            tint!!.getColorForState(intArrayOf(android.R.attr.state_enabled), 0)
        )
        assertEquals(
            screen.context.getColor(R.color.accent_blue_pressed),
            tint.getColorForState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), 0)
        )
        assertEquals(
            screen.context.getColor(R.color.accent_blue),
            tint.getColorForState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused), 0)
        )
        assertEquals(
            screen.context.getColor(R.color.text_tertiary),
            tint.getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)
        )
    }

    private fun button(root: View, description: Int): ImageButton {
        val target = root.context.getString(description)
        if (root.contentDescription == target && root is ImageButton) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                runCatching { button(root.getChildAt(index), description) }.getOrNull()?.let { return it }
            }
        }
        throw AssertionError("No transport button with content description '$target'")
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}

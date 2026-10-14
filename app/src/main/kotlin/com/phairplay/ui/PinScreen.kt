package com.phairplay.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.phairplay.R

/**
 * PinScreen — full-screen overlay that shows the AirPlay pairing PIN during SRP pair-setup (PIN
 * access control). The user reads the code here and types it on the Mac/iPhone to authorize pairing.
 */
class PinScreen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val pinView: TextView

    init {
        setBackgroundColor(color(R.color.background_dark))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
        }
        val label = TextView(context).apply {
            text = context.getString(R.string.pairing_pin_title)
            setTextColor(color(R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            maxLines = 2
            gravity = Gravity.CENTER
        }
        pinView = TextView(context).apply {
            setTextColor(color(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(16), 0, 0)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                setAutoSizeTextTypeUniformWithConfiguration(40, 72, 2, TypedValue.COMPLEX_UNIT_SP)
            }
        }
        val hint = TextView(context).apply {
            text = context.getString(R.string.pairing_pin_hint)
            setTextColor(color(R.color.text_tertiary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 3
            breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }
        column.addView(label, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(pinView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(hint, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
    }

    fun setPin(pin: String) { pinView.text = pin }

    private fun color(res: Int): Int = try { context.getColor(res) } catch (_: Exception) { Color.WHITE }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

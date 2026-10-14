package com.phairplay.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.phairplay.R
import com.phairplay.service.PhairPlayService
import com.phairplay.service.Protocol
import com.phairplay.service.ProtocolState
import com.phairplay.service.ServiceController
import com.phairplay.service.ServiceState
import com.phairplay.util.Logger
import com.phairplay.util.NetworkUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * HomeFragment — The main screen of PhairPlay.
 *
 * WHY: Shows the status of all three receiver protocols (AirPlay / Miracast / Cast)
 * and provides Start / Stop / Restart controls. Designed for TV: large cards,
 * D-pad navigable, Google TV Streamer design language.
 *
 * HOW: Binds to [PhairPlayService] to receive real-time state updates.
 * User interactions call [ServiceController] to send commands to the service.
 *
 * Navigation: accessed via the "Home" item in MainActivity's nav panel.
 */
class HomeFragment : Fragment() {

    // Service binding — gives direct access to PhairPlayService StateFlows
    private var service: PhairPlayService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? PhairPlayService.LocalBinder)?.getService()
            isBound = true
            Logger.d("HomeFragment: bound to PhairPlayService")
            if (view != null) observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            Logger.d("HomeFragment: unbound from PhairPlayService")
        }
    }

    // View references — bound in onViewCreated
    private lateinit var textDeviceName: TextView
    private lateinit var textServiceState: TextView
    private lateinit var dotServiceState: View
    private lateinit var cardAirPlay: View
    private lateinit var cardMiracast: View
    private lateinit var cardCast: View
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnRestart: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as? ScrollView)?.apply {
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength((18 * resources.displayMetrics.density).toInt())
        }
        bindViews(view)
        configureProtocolCards()
        configureButtons()
        showDeviceName()
        if (service != null) observeServiceState()
    }

    override fun onStart() {
        super.onStart()
        // Bind to the service so we can observe its StateFlows
        val intent = Intent(requireContext(), PhairPlayService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
    }

    // ─── View Setup ──────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        textDeviceName   = view.findViewById(R.id.text_device_name)
        textServiceState = view.findViewById(R.id.text_service_state)
        dotServiceState  = view.findViewById(R.id.dot_service_state)
        cardAirPlay      = view.findViewById(R.id.card_airplay)
        cardMiracast     = view.findViewById(R.id.card_miracast)
        cardCast         = view.findViewById(R.id.card_cast)
        btnStart         = view.findViewById(R.id.btn_start)
        btnStop          = view.findViewById(R.id.btn_stop)
        btnRestart       = view.findViewById(R.id.btn_restart)

        val configuration = resources.configuration
        if (configuration.screenWidthDp < 360 || configuration.fontScale >= 1.25f) {
            val identityRow = view.findViewById<LinearLayout>(R.id.home_identity_row)
            val statusChip = view.findViewById<LinearLayout>(R.id.home_service_status_chip)
            identityRow.orientation = LinearLayout.VERTICAL
            identityRow.gravity = Gravity.START
            (textDeviceName.layoutParams as LinearLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                weight = 0f
                textDeviceName.layoutParams = this
            }
            (statusChip.layoutParams as LinearLayout.LayoutParams).apply {
                topMargin = (6 * resources.displayMetrics.density).toInt()
                statusChip.layoutParams = this
            }
        }
    }

    /**
     * Sets the static content on each protocol card: icon and protocol name.
     * The dynamic parts (state, detail text) are updated when service state changes.
     */
    private fun configureProtocolCards() {
        setupCard(cardAirPlay,   R.drawable.ic_airplay,  R.string.protocol_airplay)
        setupCard(cardMiracast,  R.drawable.ic_miracast, R.string.protocol_miracast)
        setupCard(cardCast,      R.drawable.ic_cast,     R.string.protocol_cast)
    }

    private fun setupCard(card: View, iconRes: Int, nameRes: Int) {
        card.findViewById<android.widget.ImageView>(R.id.img_protocol_icon)?.setImageResource(iconRes)
        card.findViewById<TextView>(R.id.text_protocol_name)?.setText(nameRes)
    }

    /**
     * Configures Start / Stop / Restart button click listeners.
     * Calls [ServiceController] which sends Intent actions to [PhairPlayService].
     */
    private fun configureButtons() {
        btnStart.setOnClickListener {
            Logger.d("User tapped Start")
            ServiceController.start(requireContext())
        }
        btnStop.setOnClickListener {
            Logger.d("User tapped Stop")
            ServiceController.stop(requireContext())
        }
        btnRestart.setOnClickListener {
            Logger.d("User tapped Restart")
            ServiceController.restart(requireContext())
        }
    }

    /**
     * Shows the device's AirPlay name on the HomeScreen so the user knows
     * what to look for in their sender's picker.
     */
    private fun showDeviceName() {
        val name = NetworkUtils.getDeviceName(requireContext())
        textDeviceName.text = getString(R.string.home_device_visible_as, name)
    }

    // ─── State Observation ───────────────────────────────────────────────────

    /**
     * Starts collecting state updates from [PhairPlayService].
     * Called after the service is bound. Each StateFlow is collected independently
     * so that a change in one protocol card doesn't trigger a full UI redraw.
     */
    private fun observeServiceState() {
        val svc = service ?: return
        val owner = viewLifecycleOwnerLiveData.value ?: return
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return

        owner.lifecycleScope.launch {
            svc.serviceState.collectLatest { state -> updateServiceStateBadge(state) }
        }
        owner.lifecycleScope.launch {
            svc.airPlayState.collectLatest { state -> updateProtocolCard(cardAirPlay, state) }
        }
        owner.lifecycleScope.launch {
            svc.miracastState.collectLatest { state -> updateProtocolCard(cardMiracast, state) }
        }
        owner.lifecycleScope.launch {
            svc.castState.collectLatest { state -> updateProtocolCard(cardCast, state) }
        }
    }

    /**
     * Updates the global service state badge (top-right corner of HomeScreen).
     * Colors and text reflect whether the service is running, stopped, or restarting.
     */
    private fun updateServiceStateBadge(state: ServiceState) {
        val (textRes, colorRes) = when (state) {
            is ServiceState.Running    -> Pair(R.string.service_state_running,    R.color.status_running)
            is ServiceState.Stopped    -> Pair(R.string.service_state_stopped,    R.color.status_stopped)
            is ServiceState.Restarting -> Pair(R.string.service_state_restarting, R.color.status_transitioning)
            is ServiceState.Error      -> Pair(R.string.service_state_error,      R.color.status_stopped)
        }
        textServiceState.setText(textRes)
        dotServiceState.background.setTint(requireContext().getColor(colorRes))

        // Keep the visual emphasis aligned with the action that is useful in this state.
        // Redundant service commands are disabled so a focused TV remote cannot trigger them.
        val running = state is ServiceState.Running
        val restarting = state is ServiceState.Restarting
        val canStart = !running && !restarting
        val canStop = running && !restarting
        val canRestart = (running || state is ServiceState.Error) && !restarting

        btnStart.isEnabled = canStart
        btnStart.setBackgroundResource(if (canStart) R.drawable.btn_service_primary_selector else R.drawable.btn_service_secondary_selector)
        btnStart.setTextColor(requireContext().getColor(if (canStart) R.color.background_dark else R.color.text_secondary))

        btnStop.isEnabled = canStop
        btnStop.setBackgroundResource(if (canStop) R.drawable.btn_service_destructive_selector else R.drawable.btn_service_secondary_selector)
        btnStop.setTextColor(requireContext().getColor(if (canStop) R.color.status_stopped else R.color.text_secondary))

        btnRestart.isEnabled = canRestart
        btnRestart.setBackgroundResource(R.drawable.btn_service_secondary_selector)
        btnRestart.setTextColor(requireContext().getColor(R.color.text_primary))
    }

    /**
     * Updates a single protocol status card with the current [ProtocolState].
     *
     * @param card      The card root view (cardAirPlay, cardMiracast, or cardCast).
     * @param state     The current state of this protocol.
     */
    private fun updateProtocolCard(card: View, state: ProtocolState) {
        val dot    = card.findViewById<View>(R.id.dot_protocol_status)
        val stateText = card.findViewById<TextView>(R.id.text_protocol_state)
        val detail = card.findViewById<TextView>(R.id.text_protocol_detail)

        val (stateRes, colorRes, detailRes) = when (state) {
            ProtocolState.DISABLED    -> Triple(R.string.protocol_state_disabled,    R.color.status_disabled,  R.string.protocol_detail_disabled)
            ProtocolState.ADVERTISING -> Triple(R.string.protocol_state_advertising, R.color.status_running,   R.string.protocol_detail_waiting)
            ProtocolState.CONNECTED   -> Triple(R.string.protocol_state_connected,   R.color.status_running,   R.string.protocol_detail_connected)
            ProtocolState.ERROR       -> Triple(R.string.protocol_state_error,       R.color.status_stopped,   R.string.protocol_detail_error)
        }

        stateText.setText(stateRes)
        detail.setText(detailRes)
        dot.background.setTint(requireContext().getColor(colorRes))
    }
}

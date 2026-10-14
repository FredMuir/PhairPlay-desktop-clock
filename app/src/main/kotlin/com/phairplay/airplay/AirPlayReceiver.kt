package com.phairplay.airplay

import android.content.Context
import android.view.Surface
import com.phairplay.airplay.handshake.AirPlayNtpClient
import com.phairplay.airplay.handshake.AudioStreamServer
import com.phairplay.airplay.handshake.BufferedAudioServer
import com.phairplay.airplay.handshake.HapEventCrypto
import com.phairplay.airplay.handshake.InfoResponder
import com.phairplay.airplay.handshake.MirrorStreamServer
import com.phairplay.airplay.handshake.PlistCodec
import com.phairplay.airplay.handshake.PtpClock
import com.phairplay.service.ProtocolState
import com.phairplay.settings.AirPlayDeviceMode
import com.phairplay.util.Logger
import com.phairplay.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.io.ByteArrayOutputStream

/**
 * AirPlayReceiver — Top-level orchestrator for the AirPlay 2 receiver pipeline.
 *
 * WHY: Coordinates all AirPlay components into a single lifecycle:
 * - [MdnsService]: mDNS advertising (makes device visible in sender pickers)
 * - [RtspHandler]: RTSP handshake (OPTIONS → ANNOUNCE → SETUP → RECORD)
 * - [VideoDecoder]: H.264 hardware decode via MediaCodec → SurfaceView
 * - [AudioPlayer]: AES-128-CTR decrypt + AAC/ALAC decode → AudioTrack
 *
 * HOW: [PhairPlayService] creates this receiver and calls [start]/[stop].
 * The pipeline activates lazily — VideoDecoder and AudioPlayer are created
 * only after RECORD is received, when [SessionDescription] is available.
 *
 * For audio-only streams (music, podcasts), only [AudioPlayer] is started —
 * no [VideoDecoder] and no fullscreen streaming surface is needed.
 *
 * State changes are reported via [onStateChanged] to [PhairPlayService].
 *
 * Example:
 *   val receiver = AirPlayReceiver(
 *       context = context,
 *       displayName = settings.effectiveDisplayName,
 *       videoSurfaceProvider = { streamingScreen.getSurface() },
 *       onStateChanged = { state -> /* update UI */ }
 *   )
 *   receiver.start()
 *   receiver.stop()
 */
class AirPlayReceiver(
    private val context: Context,
    /** User-configured display name from Settings (blank = use system device name). */
    private val displayName: String = "",
    /** Advertised mirroring resolution (from the "high resolution" setting). */
    private val mirrorWidth: Int = 1920,
    private val mirrorHeight: Int = 1080,
    /** Whether to accept the mirroring audio stream (experimental — see AppSettings.mirrorAudioEnabled). */
    private val audioEnabled: Boolean = false,
    private val deviceMode: AirPlayDeviceMode = AirPlayDeviceMode.HOMEPOD,
    /** Require HomeKit-style SRP PIN pairing before streaming (AppSettings.airPlayPinAuthEnabled). */
    private val pinAuthEnabled: Boolean = false,
    /** Lazy Surface provider — called only for video streams when RECORD arrives. */
    private val videoSurfaceProvider: () -> Surface?,
    private val onStateChanged: (ProtocolState) -> Unit,
    /**
     * Called with the sender name when a streaming session starts (RECORD received).
     *
     * The name is extracted from the RTSP `User-Agent` header. The caller
     * ([PhairPlayService]) uses this to update the [ActiveConnection] and notification
     * text with the real sender identifier instead of the generic "AirPlay Sender".
     *
     * Guaranteed to be called BEFORE [onStateChanged] is called with [ProtocolState.CONNECTED].
     */
    private val onSenderNameChanged: (String) -> Unit = {},
    /** Called when iOS/macOS sends a JPEG/PNG to the `/photo` endpoint. */
    private val onPhotoReceived: (bytes: ByteArray, imageType: PhotoImageType) -> Unit = { _, _ -> },
    /** Called when iOS/macOS clears the currently displayed `/photo`. */
    private val onPhotoCleared: () -> Unit = {},
    /**
     * Called with the actual mDNS-registered name after [start].
     *
     * The name may differ from [displayName] if another device on the network already uses
     * the same name — NsdManager resolves the collision by appending " (2)", " (3)", etc.
     * The UI can use this callback to show the user the real registered name.
     */
    private val onActualNameRegistered: (String) -> Unit = {},
    /**
     * Audio-only "now playing" state. Emits a [NowPlayingInfo] when audio is streaming WITHOUT video
     * (system audio, Apple Music, podcasts) so the UI can show a now-playing card instead of a black
     * surface; emits null when video is mirroring (the video screen takes over) or audio stops.
     */
    private val onNowPlayingChanged: (NowPlayingInfo?) -> Unit = {},
    /** Reports receiver-side volume changes so the app's now-playing slider stays in sync. */
    private val onOutputVolumeChanged: (Float) -> Unit = {},
    private val initialOutputVolumeDb: Float = 0f,
    /** Pairing PIN to show ([pin]) or hide (null) on the TV during SRP pair-setup. */
    private val onPinChanged: (pin: String?) -> Unit = {},
    private val onRemoteControlAvailabilityChanged: (Boolean) -> Unit = {},
    private val onRemoteControlCommandsChanged: (Set<Int>?) -> Unit = {},
    private val onRemotePlaybackChanged: (Boolean) -> Unit = {},
    legacyDacpControlEnabled: Boolean = false,
    airPlayRemoteControlsEnabled: Boolean = false
) {

    // Persistent store of paired controllers (for PIN access control / pair-verify).
    private val pairingStore = com.phairplay.airplay.handshake.PairingStore(context)

    // SupervisorJob: child coroutine failures don't propagate to siblings.
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // Child components
    private var mdnsService: MdnsService? = null
    private var rtspHandler: RtspHandler? = null
    private var timingHandler: TimingHandler? = null
    private var videoDecoder: VideoDecoder? = null
    private var audioPlayer: AudioPlayer? = null

    // UDP socket for receiving audio RTP packets — opened after RECORD, closed on TEARDOWN
    @Volatile private var audioSocket: DatagramSocket? = null

    // AirPlay 2 mirroring: data stream server + event channel + keys (set during SETUP).
    @Volatile private var mirrorServer: MirrorStreamServer? = null
    @Volatile private var audioServer: AudioStreamServer? = null
    @Volatile private var bufferedAudioServer: BufferedAudioServer? = null
    @Volatile private var ptpClock: PtpClock? = null
    @Volatile private var urlVideoPlayer: AirPlayVideoPlayer? = null

    // Reverse remote control (TV → sender). Created lazily once a sender advertises DACP-ID.
    private val dacpClient = DacpClient(context) { publishRemoteControlAvailability() }
    @Volatile private var remoteControlsEnabled = airPlayRemoteControlsEnabled
    @Volatile private var legacyDacpControlRequested = legacyDacpControlEnabled
    @Volatile private var ntpClient: AirPlayNtpClient? = null
    @Volatile private var eventSocket: ServerSocket? = null
    @Volatile private var eventClientSocket: java.net.Socket? = null
    @Volatile private var eventCrypto: HapEventCrypto? = null
    @Volatile private var senderSupportedRemoteCommands: Set<Int>? = null
    @Volatile private var senderPlaybackActive = true
    private val eventCommandMutex = Mutex()
    private var eventCommandSequence = 1
    @Volatile private var mirrorAesKey: ByteArray? = null
    @Volatile private var mirrorEcdhSecret: ByteArray? = null
    @Volatile private var mirrorAesIv: ByteArray? = null

    init {
        dacpClient.setEnabled(remoteControlsEnabled && legacyDacpControlRequested)
    }

    // ─── Now-playing (audio-only) state ──────────────────────────────────────
    // The now-playing card shows only when audio plays WITHOUT video. We track both stream kinds
    // plus the latest DMAP metadata/artwork and recompute on every change (see [emitNowPlaying]).
    @Volatile private var audioPlaying = false
    @Volatile private var videoPlaying = false
    @Volatile private var npSenderName = "AirPlay"
    @Volatile private var npTitle: String? = null
    @Volatile private var npArtist: String? = null
    @Volatile private var npAlbum: String? = null
    @Volatile private var npArtwork: ByteArray? = null
    @Volatile private var airplayVolumeDb: Float = initialOutputVolumeDb.coerceIn(-144f, 0f)

    /**
     * Starts the AirPlay receiver.
     *
     * 1. Starts mDNS advertising with the configured display name.
     * 2. Opens the RTSP server socket (port 7000).
     * 3. Emits [ProtocolState.ADVERTISING] once both mDNS services are registered.
     *
     * Non-blocking — all network work runs in background coroutines.
     */
    fun start() {
        Logger.i("AirPlayReceiver starting (displayName='$displayName')")
        scope.launch {
            try {
                startTimingHandler()
                startMdnsService()
                startRtspHandler()
            } catch (e: Exception) {
                Logger.e("Failed to start AirPlayReceiver", e)
                emitState(ProtocolState.ERROR)
            }
        }
    }

    /**
     * Stops the AirPlay receiver and releases all resources.
     *
     * Stops RTSP handler, mDNS advertising, video decoder, and audio player.
     * Cancels all background coroutines.
     *
     * MUST be called when [PhairPlayService] stops or is destroyed.
     */
    fun stop() {
        Logger.i("AirPlayReceiver stopping")
        try {
            rtspHandler?.stop()
            timingHandler?.stop()
            mdnsService?.stop()
            dacpClient.stop()
            releaseMediaComponents()
            ptpClock?.stop()
            ptpClock = null
        } catch (e: Exception) {
            Logger.e("Error during AirPlayReceiver stop", e)
        } finally {
            scope.cancel()
        }
    }

    /** Sends modern MediaRemote commands; legacy DACP is an explicit opt-in fallback. */
    fun sendRemoteCommand(command: String) {
        if (!remoteControlsEnabled) return
        if (!sendEventRemoteCommand(command) && dacpClient.isEnabled) dacpClient.sendCommand(command)
    }

    /** Applies the master transport-controls setting without restarting audio reception. */
    fun setRemoteControlsEnabled(enabled: Boolean) {
        remoteControlsEnabled = enabled
        dacpClient.setEnabled(enabled && legacyDacpControlRequested)
    }

    /** Applies the legacy DACP setting without restarting an active AirPlay audio session. */
    fun setLegacyDacpControlEnabled(enabled: Boolean) {
        legacyDacpControlRequested = enabled
        dacpClient.setEnabled(remoteControlsEnabled && enabled)
    }

    private fun publishRemoteControlAvailability() {
        onRemoteControlAvailabilityChanged(
            (dacpClient.isEnabled && dacpClient.isAvailable) || eventClientSocket?.isConnected == true
        )
    }

    /** AirPlay 2's event channel carries modern MediaRemote commands; DACP remains the legacy fallback. */
    private fun sendEventRemoteCommand(command: String): Boolean {
        val modernCommand = when (command) {
            DacpClient.CMD_PLAY_PAUSE -> {
                val supported = senderSupportedRemoteCommands
                when {
                    supported == null || 2 in supported -> 2 // prefer sender-native toggle
                    senderPlaybackActive && 1 in supported -> 1 // pause
                    !senderPlaybackActive && 0 in supported -> 0 // play
                    else -> return false
                }
            }
            DacpClient.CMD_NEXT -> 4
            DacpClient.CMD_PREV -> 5
            else -> return false
        }
        val socket = eventClientSocket?.takeIf { it.isConnected && !it.isClosed } ?: return false
        val crypto = eventCrypto ?: return false
        scope.launch(Dispatchers.IO) {
            runCatching {
                postEventCommand(socket, crypto, mapOf(
                    "type" to "sendMediaRemoteCommand",
                    "modernMediaRemoteCommand" to modernCommand
                ), "remote command $command (MediaRemote=$modernCommand)")
            }.onFailure { Logger.e("AirPlay 2 event remote command $command failed", it) }
        }
        return true
    }

    private suspend fun postEventCommand(socket: Socket, crypto: HapEventCrypto, message: Map<String, Any?>, label: String) {
        eventCommandMutex.withLock {
            val body = PlistCodec.encode(message)
            val cseq = eventCommandSequence++
            val header = buildString {
                append("POST /command RTSP/1.0\r\n")
                append("CSeq: $cseq\r\n")
                append("Content-Type: application/x-apple-binary-plist\r\n")
                append("Content-Length: ${body.size}\r\n\r\n")
            }.toByteArray(Charsets.US_ASCII)
            crypto.write(socket.getOutputStream(), header + body)
            Logger.i("AirPlay 2 event $label sent (CSeq=$cseq)")
        }
    }

    /** DNS-SD TXT wire bytes copied into updateInfo, matching the receiver's current mDNS record. */
    private fun buildAirPlayTxtRecord(): ByteArray {
        val fields = listOf(
            "deviceid" to NetworkUtils.getMacAddress(context),
            "features" to deviceMode.featuresTxt,
            "model" to deviceMode.model,
            "srcvers" to deviceMode.sourceVersion,
            "vv" to "2",
            "pi" to NetworkUtils.getPersistentUuid(context),
            "flags" to if (pinAuthEnabled) "0xC" else "0x4"
        )
        val advertisedFields = if (pinAuthEnabled) fields + ("pw" to "true") else fields
        return ByteArrayOutputStream().apply {
            advertisedFields.forEach { (key, value) ->
                val entry = "$key=$value".toByteArray(Charsets.UTF_8)
                require(entry.size <= 255) { "AirPlay TXT entry exceeds DNS-SD limit: $key" }
                write(entry.size)
                write(entry)
            }
        }.toByteArray()
    }

    /** Sets local output gain and remembers it for streams created later in this AirPlay session. */
    fun setOutputVolumeDb(volumeDb: Float) {
        val clamped = volumeDb.coerceIn(-144f, 0f)
        airplayVolumeDb = clamped
        audioServer?.setVolume(clamped)
        bufferedAudioServer?.setVolume(clamped)
        onOutputVolumeChanged(clamped)
    }

    /** Mirrors Android's media stream value without echoing another system-volume write. */
    fun syncOutputVolumeFromSystem(volumeDb: Float) {
        val clamped = volumeDb.coerceIn(-144f, 0f)
        airplayVolumeDb = clamped
        audioServer?.setVolume(clamped)
        bufferedAudioServer?.setVolume(clamped)
    }

    /** True while either modern event-channel or legacy DACP transport control is available. */
    fun isRemoteControlAvailable(): Boolean =
        (dacpClient.isEnabled && dacpClient.isAvailable) || eventClientSocket?.isConnected == true

    /**
     * Re-registers the mDNS advertisement without touching the RTSP/timing servers.
     *
     * For when the network came back under us — the TV waking from standby being the case that
     * prompted this. An NsdManager registration does not survive its interface going down, and
     * nothing brings it back on its own, so the receiver keeps listening on port 7000 while
     * being invisible in every AirPlay picker. Restarting the whole receiver would do it too,
     * but that needlessly drops the sockets; only the advertisement is stale.
     *
     * No-op when the receiver was never started.
     */
    fun readvertise() {
        val mdns = mdnsService ?: return
        Logger.i("Re-advertising AirPlay over mDNS (network returned)")
        scope.launch {
            try {
                mdns.restart(displayName.ifBlank { null })
            } catch (e: Exception) {
                Logger.e("Failed to re-advertise mDNS", e)
            }
        }
    }

    // ─── Private: startup ────────────────────────────────────────────────────

    private fun startTimingHandler() {
        timingHandler = TimingHandler().also { it.start(scope) }
        Logger.d("Timing handler started on UDP port ${TimingHandler.TIMING_PORT}")
    }

    private fun startMdnsService() {
        mdnsService = MdnsService(
            context = context,
            deviceMode = deviceMode,
            pinAuthEnabled = pinAuthEnabled,
            onStateChange = { state -> emitState(state) },
            onActualNameRegistered = { actualName -> onActualNameRegistered(actualName) }
        ).also { it.start(displayName.ifBlank { null }) }
        Logger.d("mDNS service started")
    }

    private fun startRtspHandler() {
        rtspHandler = RtspHandler(
            context = context,
            deviceMode = deviceMode,
            displayWidth = mirrorWidth,
            displayHeight = mirrorHeight,
            audioEnabled = audioEnabled,
            videoSurfaceProvider = videoSurfaceProvider,
            onStreamingStarted = { session -> onStreamingStarted(session) },
            onStreamingStopped = { onStreamingStopped() },
            onPhotoReceived = { bytes, imageType -> onPhotoReceived(bytes, imageType) },
            onPhotoCleared = { onPhotoCleared() },
            onMirrorSetupKeys = { aesKey, ecdhSecret, aesIv, remoteAddr, senderTimingPort ->
                startMirrorKeys(aesKey, ecdhSecret, aesIv, remoteAddr, senderTimingPort)
            },
            onMirrorStreamStart = { streamConnectionId -> startMirrorStream(streamConnectionId) },
            onMirrorAudioStart = { sampleRate, channels, ct, spf -> startMirrorAudio(sampleRate, channels, ct, spf) },
            onMirrorAudioStop = { stopMirrorAudio() },
            onMirrorVideoStop = { stopMirrorVideo() },
            onBufferedAudioStart = { key, format, spf -> startBufferedAudio(key, format, spf) },
            onBufferedAudioRate = { command ->
                val rate = (command["rate"] as? Number)?.toDouble()
                val seconds = command["networkTimeSecs"]
                val fraction = command["networkTimeFrac"]
                val rtp = command["rtpTime"]
                Logger.i("Buffered anchor: rate=$rate PTP=$seconds:$fraction RTP=$rtp (no PTP clock mapping)")
                val secondsValue = (seconds as? Number)?.toLong()
                val fractionValue = (fraction as? Number)?.toLong()
                val anchorNanos = if (secondsValue != null && fractionValue != null) {
                    val fractionNs = (fractionValue.toULong().toDouble() / 18446744073709551616.0 * 1_000_000_000.0).toLong()
                    secondsValue * 1_000_000_000L + fractionNs
                } else null
                val rtpTime = (rtp as? Number)?.toLong()?.and(0xffff_ffffL)
                bufferedAudioServer?.setAnchor(rate, anchorNanos, rtpTime, ptpClock?.masterMinusLocalNanos)
            },
              onPtpPeers = { peers, localAddress ->
                val clock = ptpClock ?: PtpClock(context) { offset -> bufferedAudioServer?.setPtpOffset(offset) }
                    .also { ptpClock = it }
                  clock.setPeers(peers, localAddress?.hostAddress)
            },
            onBufferedAudioStop = { stopBufferedAudio() },
            onVolume = { v ->
                setOutputVolumeDb(v)
            },
            currentOutputVolumeDb = { airplayVolumeDb },
            onNowPlayingMetadata = { title, artist, album ->
                npTitle = title; npArtist = artist; npAlbum = album
                emitNowPlaying()
            },
            onArtwork = { bytes ->
                // iOS periodically sends image/none as a placeholder before retransmitting
                // the current JPEG. Treat an empty body as "no update" to avoid flashing.
                if (bytes.isNotEmpty() && !npArtwork.contentEquals(bytes)) {
                    npArtwork = bytes
                    emitNowPlaying()
                }
            },
            onVideoPlay = { url, start -> startUrlVideo(url, start) },
            onVideoRate = { rate -> urlVideoPlayer?.setRate(rate) },
            onVideoScrub = { pos -> urlVideoPlayer?.scrub(pos) },
            onVideoStop = { stopUrlVideo() },
            onPlaybackInfo = { urlVideoPlayer?.info() },
            onRemoteControlInfo = { dacpId, activeRemote -> dacpClient.configure(dacpId, activeRemote) },
            pinAuthEnabled = pinAuthEnabled,
            pairingStore = pairingStore,
            onShowPin = { pin -> onPinChanged(pin) },
            onTransientEventSetup = { secret, _ -> startEventChannel(secret) }
        ).also { it.start(scope) }
        Logger.i("RTSP handler started on port 7000 (audioEnabled=$audioEnabled pinAuth=$pinAuthEnabled)")
    }

    // ─── Private: streaming lifecycle ────────────────────────────────────────

    /**
     * Called by [RtspHandler] when RECORD is received and [SessionDescription] is ready.
     *
     * Wires the media pipeline:
     * - video stream: creates [VideoDecoder] + wires [RtspHandler.onVideoNalUnit]
     * - audio stream: creates [AudioPlayer]
     * - audio-only:   only [AudioPlayer], app stays on HomeScreen
     */
    private fun onStreamingStarted(session: SessionDescription) {
        Logger.i("Streaming started — video=${session.hasVideo} audio=${session.hasAudio} " +
                 "audioOnly=${session.isAudioOnly}")

        scope.launch {
            try {
                if (session.hasVideo) startVideoDecoder(session)
                if (session.hasAudio) startAudioPlayer(session)
                // Legacy (SDP) session: reflect its stream kinds into now-playing state so an
                // audio-only RAOP session shows the now-playing card.
                npSenderName = session.senderName.ifBlank { npSenderName }
                videoPlaying = session.hasVideo
                audioPlaying = session.hasAudio
                emitNowPlaying()
                // Notify PhairPlayService of the sender name BEFORE emitting CONNECTED,
                // so the name is ready when the ActiveConnection is created.
                onSenderNameChanged(session.senderName)
                emitState(ProtocolState.CONNECTED)
            } catch (e: Exception) {
                Logger.e("Failed to start media pipeline", e)
                emitState(ProtocolState.ERROR)
            }
        }
    }

    /**
     * Called when streaming ends (TEARDOWN received or socket closed).
     *
     * Releases media components and re-advertises so the device reappears
     * in sender pickers immediately.
     */
    private fun onStreamingStopped() {
        Logger.i("Streaming stopped — releasing media components")
        releaseMediaComponents()
        emitState(ProtocolState.ADVERTISING)

        scope.launch {
            try {
                mdnsService?.restart(displayName.ifBlank { null })
            } catch (e: Exception) {
                Logger.e("Failed to restart mDNS after streaming", e)
            }
        }
    }

    // ─── Private: media pipeline ──────────────────────────────────────────────

    /**
     * Initializes [VideoDecoder] with SPS/PPS from the [SessionDescription].
     *
     * Resolution hint: AirPlay SDP does not include width/height — the actual
     * resolution is embedded in the SPS NAL unit. We pass [DEFAULT_VIDEO_WIDTH] ×
     * [DEFAULT_VIDEO_HEIGHT] as a hint; MediaCodec reads the real size from SPS.
     *
     * [RtspHandler.onVideoNalUnit] is wired here so RTP interleaved NAL units
     * flow directly into [VideoDecoder.decodeNalUnit].
     */
    private fun startVideoDecoder(session: SessionDescription) {
        val surface = videoSurfaceProvider() ?: run {
            Logger.w("VideoDecoder: no surface available — skipping video pipeline")
            return
        }
        val sps = session.spsBytes ?: run {
            Logger.w("VideoDecoder: no SPS in SDP — skipping")
            return
        }
        val pps = session.ppsBytes ?: run {
            Logger.w("VideoDecoder: no PPS in SDP — skipping")
            return
        }

        videoDecoder = VideoDecoder(surface).also { decoder ->
            decoder.initialize(sps, pps, DEFAULT_VIDEO_WIDTH, DEFAULT_VIDEO_HEIGHT)
            rtspHandler?.onVideoNalUnit = { nalUnit, ptsUs ->
                decoder.decodeNalUnit(nalUnit, ptsUs)
            }
        }
        Logger.i("VideoDecoder started (${DEFAULT_VIDEO_WIDTH}x${DEFAULT_VIDEO_HEIGHT} hint)")
    }

    /**
     * Initializes [AudioPlayer] with codec and encryption params from [SessionDescription].
     *
     * When the SDP contains no AES key/IV (unencrypted or missing keys), null is passed —
     * [AudioPlayer.initialize] skips cipher setup entirely and writes audio payload directly.
     * This prevents a zero-key cipher from producing garbage audio (S6-4 fix).
     */
    private fun startAudioPlayer(session: SessionDescription) {
        audioPlayer = AudioPlayer().also { player ->
            player.initialize(
                aesKey     = session.aesKey.takeIf { session.isAudioEncrypted },
                aesIv      = session.aesIv.takeIf  { session.isAudioEncrypted },
                sampleRate = session.sampleRate,
                channels   = session.channels,
                codec      = session.audioCodec,
                alacFramesPerPacket = session.alacFramesPerPacket
            )
        }
        Logger.i("AudioPlayer started (${session.sampleRate}Hz × ${session.channels}ch, " +
                 "codec=${session.audioCodec}, encrypted=${session.isAudioEncrypted})")

        startAudioUdpReceiver()
    }

    /**
     * Opens a UDP socket on [AUDIO_RTP_PORT] and feeds every received packet to
     * [AudioPlayer.playAudioPacket].
     *
     * WHY UDP: AirPlay audio is sent as RTP over UDP — low latency is more important
     * than guaranteed delivery. A missing packet produces a brief audio glitch,
     * which is far less disruptive than the buffering delays that TCP would introduce.
     *
     * The socket is closed in [releaseMediaComponents] when streaming ends.
     */
    private fun startAudioUdpReceiver() {
        scope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket(AUDIO_RTP_PORT)
                audioSocket = socket
                Logger.i("Audio UDP receiver listening on port $AUDIO_RTP_PORT")

                val buf    = ByteArray(MAX_AUDIO_PACKET_BYTES)
                val packet = DatagramPacket(buf, buf.size)

                while (isActive) {
                    socket.receive(packet)
                    // copyOf trims to actual packet length before passing to the player
                    audioPlayer?.playAudioPacket(packet.data.copyOf(packet.length))
                }
            } catch (e: Exception) {
                // SocketException thrown when audioSocket.close() is called — expected
                if (audioSocket != null) {
                    Logger.e("Audio UDP receiver error (unexpected)", e)
                } else {
                    Logger.d("Audio socket closed (expected during shutdown)")
                }
            }
        }
    }

    // ─── Private: AirPlay 2 mirroring ─────────────────────────────────────────

    /**
     * Mirror SETUP msg 1: stash the decrypted AES key + pairing secret, open the event
     * channel (macOS connects to it), and switch the UI to the streaming surface.
     * @return the event channel's TCP port.
     */
    private fun startMirrorKeys(
        aesKey: ByteArray,
        ecdhSecret: ByteArray,
        aesIv: ByteArray,
        remoteAddress: java.net.InetAddress,
        senderTimingPort: Int,
    ): Pair<Int, Int> {
        mirrorAesKey = aesKey
        mirrorEcdhSecret = ecdhSecret
        mirrorAesIv = aesIv
        val eventPort = startEventChannel(ecdhSecret)
        // AirPlay 2 NTP is receiver-initiated: poll the sender's timing port so macOS proceeds.
        val ntp = AirPlayNtpClient(remoteAddress, senderTimingPort).also { ntpClient = it; it.start(scope) }
        onSenderNameChanged("AirPlay")
        emitState(ProtocolState.CONNECTED)
        Logger.i("Mirror keys set; eventPort=$eventPort timingPort=${ntp.localPort}")
        return eventPort to ntp.localPort
    }

    /** Opens and serves the encrypted AirPlay event channel used for MediaRemote commands. */
    private fun startEventChannel(eventSecret: ByteArray): Int {
        runCatching { eventClientSocket?.close() }
        eventClientSocket = null
        eventCrypto = null
        senderSupportedRemoteCommands = null
        senderPlaybackActive = true
        onRemoteControlCommandsChanged(null)
        publishRemoteControlAvailability()
        runCatching { eventSocket?.close() }
        // Explicit IPv4 wildcard — see RtspHandler.bindRtspSocket() for why.
        val event = ServerSocket(0, 50, java.net.InetAddress.getByName("0.0.0.0"))
        eventSocket = event
        // Accept the encrypted event connection. AirPlay 2's modern transport commands share this
        // channel; it is separate from both the RTSP control crypto and the optional RC stream.
        scope.launch(Dispatchers.IO) {
            var acceptedSocket: Socket? = null
            try {
                event.accept().use { s ->
                    acceptedSocket = s
                    eventClientSocket = s
                    val crypto = HapEventCrypto(eventSecret)
                    eventCrypto = crypto
                    publishRemoteControlAvailability()
                    Logger.i("Encrypted AirPlay event channel connected from ${s.inetAddress.hostAddress}")
                    val info = PlistCodec.decode(
                        InfoResponder.build(context, mirrorWidth, mirrorHeight, pinAuthEnabled, deviceMode)
                    ).toMutableMap().apply {
                        put("name", displayName.ifBlank { get("name") ?: "PhairPlay" })
                        put("txtAirPlay", buildAirPlayTxtRecord())
                    }
                    postEventCommand(s, crypto, mapOf("type" to "updateInfo", "value" to info), "initial updateInfo")
                    val input = crypto.wrapInput(s.getInputStream())
                    val reader = RtspRequestReader(maxMessageBytes = 64 * 1024, maxPhotoBytes = 64 * 1024)
                    while (isActive && !s.isClosed) {
                        val request = reader.read(input) ?: break
                        if (request.method.equals("RTSP/1.0", ignoreCase = true)) {
                            Logger.d("AirPlay event command response: ${request.uri} ${request.protocol}")
                            continue
                        }
                        if (request.bodyBytes.isNotEmpty()) {
                            runCatching { PlistCodec.decode(request.bodyBytes) }
                                .onSuccess { event ->
                                    handleMediaRemoteEvent(event)
                                    Logger.d("AirPlay event request ${event["type"] ?: request.uri}")
                                }
                                .onFailure { Logger.w("Could not decode AirPlay event request body: ${it.message}") }
                        } else {
                            Logger.d("AirPlay event request ${request.method} ${request.uri}")
                        }
                        val cseq = request.headers.entries.firstOrNull { it.key.equals("CSeq", ignoreCase = true) }?.value
                        val response = buildString {
                            append("RTSP/1.0 200 OK\r\n")
                            if (cseq != null) append("CSeq: $cseq\r\n")
                            append("Content-Length: 0\r\n\r\n")
                        }
                        crypto.write(s.getOutputStream(), response.toByteArray(Charsets.US_ASCII))
                    }
                }
            } catch (e: Exception) {
                if (eventSocket != null) Logger.d("Event channel closed")
            } finally {
                if (acceptedSocket != null && eventClientSocket === acceptedSocket) {
                    eventClientSocket = null
                    eventCrypto = null
                    onRemoteControlCommandsChanged(null)
                    publishRemoteControlAvailability()
                }
            }
        }
        return event.localPort
    }

    /** Reads the sender's advertised transport capabilities and playback-rate state. */
    private fun handleMediaRemoteEvent(event: Map<String, Any?>) {
        when (event["type"] as? String) {
            "updateMRSupportedCommands" -> {
                val params = event["params"] as? Map<*, *> ?: return
                val serialized = params["mrSupportedCommandsFromSender"] as? List<*> ?: return
                val commands = serialized.mapNotNull { item ->
                    val bytes = item as? ByteArray ?: return@mapNotNull null
                    val info = runCatching { PlistCodec.decode(bytes) }.getOrNull() ?: return@mapNotNull null
                    val command = (info["kCommandInfoCommandKey"] as? Number)?.toInt()
                        ?: return@mapNotNull null
                    val enabled = info["kCommandInfoEnabledKey"] as? Boolean ?: true
                    command.takeIf { enabled }
                }.toSet()
                senderSupportedRemoteCommands = commands
                onRemoteControlCommandsChanged(commands)
                Logger.i("Sender advertises enabled MediaRemote commands=$commands")
            }
            "updateMRNowPlayingInfo" -> {
                val envelope = event["params"] as? Map<*, *> ?: return
                val info = envelope["params"] as? Map<*, *> ?: return
                val rate = (info["PlaybackRate"] as? Number)?.toDouble() ?: return
                senderPlaybackActive = rate > 0.0
                onRemotePlaybackChanged(senderPlaybackActive)
                Logger.i("Sender playback rate=$rate; playing=${rate > 0.0}")
            }
        }
    }

    /**
     * Mirror SETUP msg 2: start the data-stream server for the requested stream.
     * @return the data server's TCP port (macOS connects here to send H.264).
     */
    private fun startMirrorStream(streamConnectionId: Long): Int {
        val aesKey = mirrorAesKey ?: run { Logger.e("mirror stream start before keys set"); return 0 }
        val ecdhSecret = mirrorEcdhSecret ?: return 0
        return MirrorStreamServer(aesKey, ecdhSecret, streamConnectionId, videoSurfaceProvider, mirrorWidth, mirrorHeight)
            .also { mirrorServer = it; it.start(scope); videoPlaying = true; emitNowPlaying() }
            .dataPort
            .also { Logger.i("Mirror data server started on port $it") }
    }

    /** Mirror SETUP audio stream (type 96): start the AAC-ELD / AAC-LC / ALAC audio server. @return (dataPort, controlPort). */
    private fun startMirrorAudio(sampleRate: Int, channels: Int, codecType: Int, framesPerPacket: Int): Pair<Int, Int> {
        val aesKey = mirrorAesKey ?: run { Logger.e("audio start before keys set"); return 0 to 0 }
        val ecdhSecret = mirrorEcdhSecret ?: return 0 to 0
        val aesIv = mirrorAesIv ?: return 0 to 0
        val server = AudioStreamServer(aesKey, ecdhSecret, aesIv, sampleRate, channels, codecType, framesPerPacket)
            .also { audioServer = it; it.start(scope) }
        audioPlaying = true
        emitNowPlaying()
        Logger.i("Mirror audio server started: dataPort=${server.dataPort} controlPort=${server.controlPort}")
        return server.dataPort to server.controlPort
    }

    /** Stops ONLY the mirror audio stream (macOS dynamic-stream TEARDOWN) — video keeps running. */
    private fun stopMirrorAudio() {
        audioServer?.stop()
        audioServer = null
        audioPlaying = false
        clearNowPlayingMetadata()
        emitNowPlaying()
        Logger.i("Mirror audio stream stopped (video mirroring continues)")
    }

    /** Stops ONLY the mirror video stream (macOS dynamic-stream TEARDOWN) — audio keeps playing. */
    private fun stopMirrorVideo() {
        mirrorServer?.stop()
        mirrorServer = null
        videoPlaying = false
        emitNowPlaying()   // audio may still be playing → now-playing card can take over
        Logger.i("Mirror video stream stopped (audio playback continues)")
    }

    /**
     * AirPlay video URL mode (non-mirroring): show the streaming surface and hand the URL to
     * [AirPlayVideoPlayer], which fetches + plays it via MediaPlayer onto the same Surface.
     */
    private fun startUrlVideo(url: String, startFraction: Double) {
        onSenderNameChanged("AirPlay")
        emitState(ProtocolState.CONNECTED)   // shows StreamingScreen → Surface becomes available
        val player = urlVideoPlayer ?: AirPlayVideoPlayer(
            surfaceProvider = videoSurfaceProvider,
            onEnded = { stopUrlVideo() }
        ).also { urlVideoPlayer = it }
        player.play(url, startFraction)
        Logger.i("AirPlay URL video started: $url (start=$startFraction)")
    }

    /** Stops AirPlay video URL playback (POST /stop or end-of-media) and ends the session. */
    private fun stopUrlVideo() {
        urlVideoPlayer?.release()
        urlVideoPlayer = null
        onStreamingStopped()
        Logger.i("AirPlay URL video stopped")
    }

    /** Starts the AirPlay 2 buffered audio-only stream (type 103, Apple Music → TV); returns its TCP port. */
    private fun startBufferedAudio(sessionKey: ByteArray, audioFormat: Long, samplesPerFrame: Int): Int {
        bufferedAudioServer?.stop()
        val server = BufferedAudioServer(context, sessionKey, audioFormat, samplesPerFrame)
            .also { bufferedAudioServer = it; it.setVolume(airplayVolumeDb); it.start(scope) }
        audioPlaying = true   // buffered audio (type 103) is always audio-only
        emitNowPlaying()
        Logger.i("Buffered audio server started: dataPort=${server.dataPort}, format=0x${audioFormat.toString(16)}")
        return server.dataPort
    }

    /** Stops the buffered audio-only stream (type 103 TEARDOWN). */
    private fun stopBufferedAudio() {
        bufferedAudioServer?.stop()
        bufferedAudioServer = null
        audioPlaying = false
        clearNowPlayingMetadata()
        emitNowPlaying()
        Logger.i("Buffered audio stream stopped")
    }

    /** Clears the video NAL callback, closes the audio socket, and releases media components. */
    private fun releaseMediaComponents() {
        // Keep the RTSP session's PTP clock alive across stream-level TEARDOWN/SETUP.
        // iOS commonly re-creates type 103 on pause/resume without sending SETPEERS again;
        // stopping PTP here makes the resumed stream fall back to unsynchronized playback.
        rtspHandler?.onVideoNalUnit = null
        try { audioSocket?.close() } catch (e: Exception) { /* non-fatal */ }
        audioSocket = null
        mirrorServer?.stop()
        mirrorServer = null
        audioServer?.stop()
        audioServer = null
        bufferedAudioServer?.stop()
        bufferedAudioServer = null
        urlVideoPlayer?.release()
        urlVideoPlayer = null
        ntpClient?.stop()
        ntpClient = null
        try { eventClientSocket?.close() } catch (e: Exception) { /* non-fatal */ }
        eventClientSocket = null
        eventCrypto = null
                    senderSupportedRemoteCommands = null
                    senderPlaybackActive = true
        publishRemoteControlAvailability()
        try { eventSocket?.close() } catch (e: Exception) { /* non-fatal */ }
        eventSocket = null
        // Clear the FairPlay/ECDH keys on FULL teardown only. This method runs on a genuine session
        // end (last-stream / session TEARDOWN, or control-connection close) — NOT on a per-stream
        // teardown, which goes through stopMirrorAudio/stopMirrorVideo and leaves the keys intact so
        // macOS can re-add a dynamic stream on the same live session without re-sending keys (that
        // dynamic-readd path is why the keys must survive a stream stop). Clearing here prevents a
        // brand-new control connection from reusing a previous session's stale keys.
        mirrorAesKey = null
        mirrorEcdhSecret = null
        mirrorAesIv = null
        videoDecoder?.release()
        videoDecoder = null
        audioPlayer?.release()
        audioPlayer = null
        // Session fully torn down — clear now-playing so the UI leaves the audio card.
        audioPlaying = false
        videoPlaying = false
        clearNowPlayingMetadata()
        emitNowPlaying()
    }

    /** Pushes the current now-playing state out: a [NowPlayingInfo] when audio plays without video, else null. */
    private fun emitNowPlaying() {
        val show = audioPlaying && !videoPlaying
        onNowPlayingChanged(
            if (show) NowPlayingInfo(npSenderName, npTitle, npArtist, npAlbum, npArtwork) else null
        )
    }

    /** Drops stale track metadata/artwork when an audio stream ends (so it can't bleed into the next). */
    private fun clearNowPlayingMetadata() {
        npTitle = null; npArtist = null; npAlbum = null; npArtwork = null
    }

    // ─── Private: state emission ─────────────────────────────────────────────

    /** Dispatches [state] on the Main thread (Android UI rule). */
    private fun emitState(state: ProtocolState) {
        scope.launch {
            withContext(Dispatchers.Main) {
                onStateChanged(state)
            }
        }
    }

    companion object {
        // Hint dimensions for MediaCodec configuration.
        // Real resolution is encoded in the H.264 SPS NAL unit.
        private const val DEFAULT_VIDEO_WIDTH  = 1920
        private const val DEFAULT_VIDEO_HEIGHT = 1080

        /**
         * UDP port for receiving audio RTP packets.
         * Advertised in the RTSP SETUP response so the sender knows where to send audio.
         * Must not conflict with the RTSP port (7000) or timing port ([TimingHandler.TIMING_PORT]).
         */
        internal const val AUDIO_RTP_PORT = 6001

        /**
         * Maximum UDP audio packet size in bytes.
         * ALAC frames are typically ≤ 8 KB. 16 KB is a safe upper bound.
         */
        private const val MAX_AUDIO_PACKET_BYTES = 16 * 1024
    }
}

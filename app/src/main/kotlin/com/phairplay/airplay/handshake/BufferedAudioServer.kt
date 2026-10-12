package com.phairplay.airplay.handshake

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioTimestamp
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.phairplay.util.Logger
import com.phairplay.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

/** AirPlay 2 buffered (type 103) TCP audio receiver. */
class BufferedAudioServer(
    private val context: Context,
    private val sessionKey: ByteArray,
    private val audioFormat: Long,
    private val samplesPerFrame: Int,
) {
    private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("0.0.0.0"))
    @Volatile private var running = false
    @Volatile private var client: Socket? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var requestedRate = 0.0
    @Volatile private var ptpOffsetNs: Long? = null
    @Volatile private var audioSyncOffsetMs = 0
    @Volatile private var anchorPtpNs: Long? = null
    @Volatile private var anchorRtpTime: Long? = null
    @Volatile private var needsScheduledStart = true
    @Volatile private var firstFrameWaitStartedNs = 0L
    @Volatile private var writtenPcmFrames = 0L
    @Volatile private var lastQueueLogNs = 0L
    @Volatile private var positionErrorFilterNs = 0L
    @Volatile private var positionSpeedCorrectionPpm = 0
    @Volatile private var lastPositionSampleNs = 0L
    @Volatile private var lastRawPositionErrorNs = 0L
    @Volatile private var lastPositionSampleRejected = false
    @Volatile private var lastPositionErrorNs = 0L
    @Volatile private var lastTimestampFramePosition = -1L
    @Volatile private var lastTimestampNanoTime = 0L
    @Volatile private var advancingTimestampSamples = 0
    @Volatile private var hasPositionErrorSample = false
    @Volatile private var startupPhaseSkippedNs = 0L
    private var lastObservedUnderruns = -1
    private var lastUnderrunNs = 0L
    val dataPort: Int get() = serverSocket.localPort

    /** Android STREAM_MUSIC is the single output gain stage. */
    fun setVolume(airplayVolume: Float) {
        runCatching { audioTrack?.setVolume(1f) }
        Logger.i("Buffered AirPlay volume routed to Android media stream: $airplayVolume dB")
    }

    fun start(scope: CoroutineScope) {
        running = true
        scope.launch(Dispatchers.IO) {
            audioSyncOffsetMs = SettingsRepository(context).settingsFlow.first().audioSyncOffsetMs
                .coerceIn(-250, 250)
            Logger.i("Buffered output sync adjustment=${audioSyncOffsetMs}ms")
            receiveAndPlay()
        }
    }

    fun stop() {
        running = false
        runCatching { client?.close() }
        runCatching { serverSocket.close() }
        Logger.i("BufferedAudioServer stopped")
    }

    /** Apply the complete SETRATEANCHORTIME tuple, including its shared-clock timeline mapping. */
    fun setAnchor(rate: Double?, networkTimeNs: Long?, rtpTime: Long?, masterMinusLocalNs: Long?) {
        if (networkTimeNs != null) anchorPtpNs = networkTimeNs
        if (rtpTime != null) anchorRtpTime = rtpTime and 0xffff_ffffL
        if (masterMinusLocalNs != null) ptpOffsetNs = masterMinusLocalNs
        if (rate != null) {
            requestedRate = rate
            needsScheduledStart = true
            // A pause/resume, seek, or new anchor creates a new phase-error epoch. Do not
            // carry the previous stream's filtered position or speed correction into it.
            positionErrorFilterNs = 0L
            lastPositionErrorNs = 0L
            lastRawPositionErrorNs = 0L
            lastPositionSampleNs = 0L
            lastPositionSampleRejected = false
            lastTimestampFramePosition = -1L
            lastTimestampNanoTime = 0L
            advancingTimestampSamples = 0
            hasPositionErrorSample = false
            startupPhaseSkippedNs = 0L
            positionSpeedCorrectionPpm = 0
            val track = audioTrack
            runCatching { if (rate <= 0.0) track?.pause() }
                .onFailure { Logger.w("Buffered audio rate=$rate could not be applied: ${it.message}") }
        }
        Logger.i("Buffered anchor applied: rate=$rate PTP=$anchorPtpNs RTP=$anchorRtpTime offset=$ptpOffsetNs")
    }

    fun setPtpOffset(masterMinusLocalNs: Long) {
        ptpOffsetNs = masterMinusLocalNs
        Logger.i("Buffered audio PTP mapping updated: master-local=$masterMinusLocalNs ns")
    }

    /** Retained for callers that only have a rate (legacy NTP/realtime path). */
    fun setRate(rate: Double) {
        requestedRate = rate
        val track = audioTrack ?: return
        runCatching {
            if (rate <= 0.0) track.pause() else track.play()
        }.onFailure { Logger.w("Buffered audio rate=$rate could not be applied: ${it.message}") }
        Logger.i("Buffered audio playback rate=$rate")
    }

    private fun receiveAndPlay() {
        var decoder: MediaCodec? = null
        var track: AudioTrack? = null
        try {
            val (sampleRate, channels, encoding) = decodeFormat(audioFormat)
            require(encoding == CODEC_AAC_LC) { "unsupported buffered audio format 0x${audioFormat.toString(16)}" }
            val outputRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
                .takeIf { it in 8_000..192_000 } ?: sampleRate
            val asc = AudioStreamServer.buildAacLcAsc(sampleRate, channels)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setByteBuffer("csd-0", ByteBuffer.wrap(asc))
            }
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, 0)
                start()
            }
            val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minBuffer = AudioTrack.getMinBufferSize(outputRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            val audioManager = context.getSystemService(AudioManager::class.java)
            val outputBurstFrames = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                ?.toIntOrNull()?.takeIf { it > 0 } ?: 192
            val packetFrames = (samplesPerFrame.toLong() * outputRate / sampleRate).toInt().coerceAtLeast(1)
            val bufferFloorFrames = maxOf(outputBurstFrames * 2, packetFrames)
            // Start with two AAC frames for jitter headroom, then tune the effective
            // AudioTrack buffer downward while monitoring getUnderrunCount().
            val targetBufferFrames = maxOf(bufferFloorFrames, packetFrames * 2)
            val targetBufferBytes = targetBufferFrames * channels * 2
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(outputRate).setChannelMask(channelMask).build())
                .setBufferSizeInBytes(maxOf(targetBufferBytes, 2048))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM).build().also {
                    it.setVolume(1f)
                    audioTrack = it
                }
            val initialBufferResult = track.setBufferSizeInFrames(targetBufferFrames)
            // Android 12 defaults the start threshold to the full AudioTrack capacity. We only
            // prequeue one AAC frame before the PTP deadline, so that default can delay the first
            // audible frame until a second AAC frame arrives. Starting at one decoded frame keeps
            // playback aligned with the scheduled anchor while retaining the same underrun floor.
            val startThresholdFrames = if (android.os.Build.VERSION.SDK_INT >= 31) {
                runCatching { track.setStartThresholdInFrames(bufferFloorFrames) }
                    .onFailure { Logger.w("Could not set AudioTrack start threshold: ${it.message}") }
                    .getOrDefault(-1)
            } else -1
            val outputRoute = track.routedDevice
            if (outputRoute != null) {
                val preferences = context.getSharedPreferences(OUTPUT_TIMING_PREFS, Context.MODE_PRIVATE)
                val key = outputLeadPreferenceKey(outputRoute)
                val learnedLead = preferences.getLong(key, cachedOutputPipelineLeadNs)
                    .coerceIn(MIN_OUTPUT_LEAD_NS, MAX_OUTPUT_LEAD_NS)
                cachedOutputPipelineLeadNs = learnedLead
                Logger.i("Audio output route=${outputRoute.type}/${outputRoute.productName}; " +
                    "restored measured render lead=${learnedLead / 1_000_000}ms")
            }

            Logger.i("BufferedAudioServer listening TCP $dataPort (${sampleRate}Hz AAC -> ${outputRate}Hz ${channels}ch, " +
                "spf=$samplesPerFrame); AudioTrack state=${track.state} minBuffer=$minBuffer requested=${targetBufferBytes}B " +
                "burst=$outputBurstFrames bufferResult=$initialBufferResult " +
                "startThreshold=$startThresholdFrames " +
                "buffer=${track.bufferSizeInFrames}/${track.bufferCapacityInFrames} frames " +
                "(${track.bufferSizeInFrames * 1000L / outputRate}ms)")
            val socket = serverSocket.accept().also { client = it; it.tcpNoDelay = true }
            Logger.i("Buffered audio TCP connection from ${socket.inetAddress.hostAddress}")
            val input = DataInputStream(socket.getInputStream())
            // Keep the resampler active even when rates match. It becomes a very low-authority
            // position servo, correcting measured PTP phase error without changing the nominal
            // output rate or making a fixed per-device timing guess.
            val rateConverter = Pcm16RateConverter(sampleRate, outputRate, channels)
            var packets = 0L
            var pcmFrames = 0L
            while (running) {
                val size = try { input.readUnsignedShort() } catch (_: java.io.EOFException) { break }
                if (size !in (2 + RTP_HEADER + TAG_SIZE + NONCE_SIZE)..MAX_PACKET) {
                    throw IllegalArgumentException("invalid buffered RTP frame size $size")
                }
                val packet = ByteArray(size - 2)
                input.readFully(packet)
                val packetReceivedNs = System.nanoTime()
                val rtpTimestamp = readUint32(packet, 4)
                if (packets < STARTUP_PACKET_DIAGNOSTICS) {
                    logPacketTimeline(packets, rtpTimestamp, packetReceivedNs, sampleRate)
                }
                val payload = decryptPacket(packet)
                pcmFrames += decodeAndPlay(decoder, track, payload, rtpTimestamp, sampleRate, rateConverter,
                    bufferFloorFrames, pcmFrames == 0L)
                packets++
                if (packets == 1L) Logger.i("Buffered audio: first authenticated frame (${payload.size}B)")
                if (packets % 500L == 0L) Logger.d("Buffered audio frames=$packets")
            }
            Logger.i("Buffered audio TCP ended after $packets frames")
        } catch (e: Exception) {
            if (running) Logger.e("Buffered audio playback failed", e)
        } finally {
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }
            runCatching { track?.stop() }; runCatching { track?.release() }
            audioTrack = null
        }
    }

    /** Capture the sender's packet cadence against its PTP/RTP schedule before decode/output work. */
    private fun logPacketTimeline(index: Long, rtpTimestamp: Long, receivedNs: Long, sampleRate: Int) {
        val anchor = anchorPtpNs
        val anchorRtp = anchorRtpTime
        val offset = ptpOffsetNs
        if (anchor == null || anchorRtp == null || offset == null || sampleRate <= 0) {
            Logger.i("Buffered packet timeline[$index]: rtp=$rtpTimestamp receivedNs=$receivedNs " +
                "anchorReady=${anchor != null && anchorRtp != null && offset != null}")
            return
        }
        val delta = (rtpTimestamp - anchorRtp).toInt().toLong()
        val targetLocalNs = anchor - offset + delta * 1_000_000_000L / sampleRate
        Logger.i("Buffered packet timeline[$index]: rtp=$rtpTimestamp delta=$delta " +
            "receivedNs=$receivedNs targetLocalNs=$targetLocalNs " +
            "arrivalLatenessMs=${(receivedNs - targetLocalNs) / 1_000_000}")
    }

    /** Frame is RTP header + ciphertext + Poly1305 tag + transmitted low 8 nonce bytes. */
    private fun decryptPacket(packet: ByteArray): ByteArray {
        require(packet.size >= RTP_HEADER + TAG_SIZE + NONCE_SIZE)
        val payloadEnd = packet.size - TAG_SIZE - NONCE_SIZE
        val nonce = ByteArray(12)
        packet.copyInto(nonce, 4, packet.size - NONCE_SIZE, packet.size)
        val cipherTextAndTag = packet.copyOfRange(RTP_HEADER, packet.size - NONCE_SIZE)
        val aad = packet.copyOfRange(4, 12) // RTP timestamp + SSRC
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(sessionKey), TAG_SIZE * 8, nonce, aad))
        return try { ByteArray(cipher.getOutputSize(cipherTextAndTag.size)).let { out ->
            val count = cipher.processBytes(cipherTextAndTag, 0, cipherTextAndTag.size, out, 0)
            val end = cipher.doFinal(out, count)
            out.copyOf(count + end)
        } } catch (e: InvalidCipherTextException) {
            throw IllegalStateException("buffered audio authentication failed (payload=${payloadEnd - RTP_HEADER}B)", e)
        }
    }

    private fun decodeAndPlay(codec: MediaCodec, audioTrack: AudioTrack, aac: ByteArray, rtpTimestamp: Long,
                              sampleRate: Int, rateConverter: Pcm16RateConverter, bufferFloorFrames: Int,
                              logFirstPcm: Boolean): Long {
        var input = codec.dequeueInputBuffer(10_000)
        if (input >= 0) {
            codec.getInputBuffer(input)!!.apply { clear(); put(aac) }
            val presentationTimeUs = rtpTimestamp * 1_000_000L / sampleRate
            codec.queueInputBuffer(input, 0, aac.size, presentationTimeUs, 0)
        }
        val info = MediaCodec.BufferInfo()
        var output = codec.dequeueOutputBuffer(info, 0)
        var pcmFrames = 0L
        while (output >= 0) {
            val buffer = codec.getOutputBuffer(output)!!
            val pcm = ByteArray(info.size)
            buffer.position(info.offset); buffer.get(pcm)
            if (pcm.isNotEmpty()) {
                var peak = 0
                var sampleIndex = 0
                while (sampleIndex + 1 < pcm.size) {
                    val sample = ((pcm[sampleIndex + 1].toInt() shl 8) or (pcm[sampleIndex].toInt() and 0xff)).toShort().toInt()
                    peak = maxOf(peak, kotlin.math.abs(sample))
                    sampleIndex += 2
                }
                val outputRtpTimestamp = (info.presentationTimeUs * sampleRate / 1_000_000L) and 0xffff_ffffL
                updatePositionServo(audioTrack, outputRtpTimestamp, sampleRate, rateConverter)
                val outputPcm = rateConverter.convert(pcm, positionSpeedCorrectionPpm)
                val prewritten = if (needsScheduledStart) {
                    ensureScheduledStart(audioTrack, outputRtpTimestamp, sampleRate, outputPcm)
                } else 0
                if (prewritten < 0) {
                    codec.releaseOutputBuffer(output, false)
                    output = codec.dequeueOutputBuffer(info, 0)
                    continue
                }
                val written = if (prewritten > 0) prewritten else
                    audioTrack.write(outputPcm, 0, outputPcm.size, AudioTrack.WRITE_BLOCKING)
                if (written > 0) writtenPcmFrames += written / (audioTrack.channelCount * 2)
                val nowNs = System.nanoTime()
                if (logFirstPcm || nowNs - lastQueueLogNs >= 1_000_000_000L) {
                    lastQueueLogNs = nowNs
                    val timestamp = AudioTimestamp()
                    val hasTimestamp = runCatching { audioTrack.getTimestamp(timestamp) }.getOrDefault(false)
                    val queuedFrames = if (hasTimestamp) writtenPcmFrames - timestamp.framePosition else -1L
                    val queuedMs = if (queuedFrames >= 0L) queuedFrames * 1000L / audioTrack.sampleRate else -1L
                    if (queuedMs > 0L) updateCachedOutputLead(audioTrack, queuedMs)
                    if (hasTimestamp) tuneOutputBuffer(audioTrack, bufferFloorFrames, nowNs)
                    Logger.i("Buffered output timing: pcm=${pcm.size}B peak=$peak write=$written framesWritten=$writtenPcmFrames " +
                        "head=${if (hasTimestamp) timestamp.framePosition else -1L} queue=${queuedMs}ms " +
                        "timestampNs=${if (hasTimestamp) timestamp.nanoTime else -1L} " +
                        "positionErrorMs=${lastPositionErrorNs / 1_000_000} " +
                        "rawPositionErrorUs=${lastRawPositionErrorNs / 1_000} " +
                        "startupPhaseSkippedMs=${startupPhaseSkippedNs / 1_000_000} " +
                        "positionSampleRejected=$lastPositionSampleRejected " +
                        "speedCorrectionPpm=$positionSpeedCorrectionPpm")
                }
                pcmFrames++
            }
            codec.releaseOutputBuffer(output, false)
            output = codec.dequeueOutputBuffer(info, 0)
        }
        return pcmFrames
    }

    /**
     * Compare AudioTrack's presented-frame timestamp with that frame's PTP/RTP deadline.
     * A bounded resampling correction gradually removes persistent phase error; the correction
     * is small enough to avoid audible pitch changes and is reset with each stream instance.
     */
    private fun updatePositionServo(
        track: AudioTrack,
        nextRtpTimestamp: Long,
        sampleRate: Int,
        rateConverter: Pcm16RateConverter,
    ) {
        if (needsScheduledStart || requestedRate <= 0.0 || sampleRate <= 0) return
        val nowNs = System.nanoTime()
        if (nowNs - lastPositionSampleNs < POSITION_SAMPLE_INTERVAL_NS) return
        lastPositionSampleNs = nowNs

        val anchor = anchorPtpNs ?: return
        val anchorRtp = anchorRtpTime ?: return
        val offset = ptpOffsetNs ?: return
        val timestamp = AudioTimestamp()
        if (!runCatching { track.getTimestamp(timestamp) }.getOrDefault(false)) return

        // AudioTimestamp.framePosition is a wrapping 32-bit playback cursor. Extend it into
        // the same frame domain as writtenPcmFrames before estimating the queued source position.
        val written = writtenPcmFrames
        val rawHead = timestamp.framePosition and 0xffff_ffffL
        val timestampAdvanced = rawHead != lastTimestampFramePosition && timestamp.nanoTime > lastTimestampNanoTime
        if (timestampAdvanced) advancingTimestampSamples++ else advancingTimestampSamples = 0
        lastTimestampFramePosition = rawHead
        lastTimestampNanoTime = timestamp.nanoTime
        // AudioTrack may return a synthetic/stalled timestamp as the route wakes. Wait for
        // two advancing frame/time samples before trusting its phase measurement.
        if (advancingTimestampSamples < STABLE_TIMESTAMP_SAMPLES) return
        var head = (written and -0x1_0000_0000L) or rawHead
        if (head > written) head -= 0x1_0000_0000L
        val queuedOutputFrames = (written - head).coerceAtLeast(0L)
        val queuedSourceFrames = queuedOutputFrames * sampleRate / track.sampleRate
        val headRtp = (nextRtpTimestamp - queuedSourceFrames) and 0xffff_ffffL
        val delta = (headRtp - anchorRtp).toInt().toLong()
        val targetLocalNs = anchor - offset + delta * 1_000_000_000L / sampleRate +
            audioSyncOffsetMs * 1_000_000L
        val errorNs = timestamp.nanoTime - targetLocalNs
        lastRawPositionErrorNs = errorNs
        // Discard timestamp discontinuities (route changes, underrun recovery, or anchor jumps).
        if (kotlin.math.abs(errorNs) > MAX_POSITION_ERROR_NS) {
            lastPositionSampleRejected = true
            return
        }
        lastPositionSampleRejected = false

        // Seed the filter from the first stable sample. Starting it at zero ramps the
        // catch-up command over several seconds, exactly when a newly created AudioTrack
        // most needs phase correction.
        var residualErrorNs = errorNs
        if (!hasPositionErrorSample) {
            // A newly created AudioTrack can already be far behind by the time its first
            // trustworthy timestamp arrives. The user accepts up to one second of missing
            // startup audio, so drop that already-late source interval in one bounded phase
            // step instead of making every following sample catch up over tens of seconds.
            if (errorNs > STARTUP_PHASE_SKIP_THRESHOLD_NS) {
                val skippedNs = minOf(errorNs, MAX_STARTUP_PHASE_SKIP_NS)
                val skippedFrames = skippedNs * sampleRate / 1_000_000_000L
                if (skippedFrames > 0L) {
                    rateConverter.skipSourceFrames(skippedFrames)
                    startupPhaseSkippedNs = skippedFrames * 1_000_000_000L / sampleRate
                    residualErrorNs -= startupPhaseSkippedNs
                    Logger.w("Buffered startup phase correction: skipped ${startupPhaseSkippedNs / 1_000_000}ms " +
                        "of late audio (measured=${errorNs / 1_000_000}ms)")
                }
            }
            positionErrorFilterNs = residualErrorNs
            hasPositionErrorSample = true
        } else {
            positionErrorFilterNs += (residualErrorNs - positionErrorFilterNs) / POSITION_FILTER_DIVISOR
        }
        lastPositionErrorNs = positionErrorFilterNs
        val filteredMs = positionErrorFilterNs / 1_000_000.0
        // Large join-time phase errors otherwise take tens of seconds to converge at the
        // fine-servo limit. Allow temporary 2% catch-up while the error is clearly large, then
        // return to the quieter 0.15% limit inside the fine-correction range.
        val speedLimitPpm = if (kotlin.math.abs(filteredMs) > FAST_CATCHUP_ERROR_MS) {
            MAX_FAST_CATCHUP_PPM
        } else {
            MAX_POSITION_SPEED_PPM
        }
        positionSpeedCorrectionPpm = when {
            filteredMs > POSITION_SERVO_DEADBAND_MS ->
                (filteredMs * POSITION_SERVO_PPM_PER_MS).toInt().coerceAtMost(speedLimitPpm)
            filteredMs < -POSITION_SERVO_DEADBAND_MS ->
                (filteredMs * POSITION_SERVO_PPM_PER_MS).toInt().coerceAtLeast(-speedLimitPpm)
            else -> 0
        }
    }

    /** Waits for the PTP/RTP anchor, then starts the Android output pipeline at its render deadline. */
    private fun ensureScheduledStart(track: AudioTrack, rtpTimestamp: Long, sampleRate: Int, firstPcm: ByteArray): Int {
        if (!needsScheduledStart) return if (requestedRate > 0.0) 0 else -1
        if (firstFrameWaitStartedNs == 0L) firstFrameWaitStartedNs = System.nanoTime()
        while (running && requestedRate <= 0.0) Thread.sleep(10)
        if (!running || requestedRate <= 0.0) return -1

        val anchor = anchorPtpNs
        val anchorRtp = anchorRtpTime
        val offset = ptpOffsetNs
        if (anchor != null && anchorRtp != null && offset != null) {
            var rtpDelta = (rtpTimestamp - anchorRtp).toInt().toLong()
            val rawDelta = rtpDelta
            // Apple can continue an RTP counter across a buffered-stream recreation while
            // sending a fresh SETRATEANCHORTIME for the new stream. In that case the pair is
            // discontinuous by hours even though PTP and playback are current. Do not wait for
            // that impossible timestamp; treat the first decoded frame as the new RTP origin.
            // Small negative deltas are still pre-anchor packets and are discarded as usual.
            if (kotlin.math.abs(rtpDelta) > sampleRate.toLong() * MAX_ANCHOR_RTP_DELTA_SECONDS) {
                Logger.w("Buffered RTP anchor discontinuity: delta=$rtpDelta samples " +
                    "(${rtpDelta / sampleRate}s); rebasing first frame to PTP anchor")
                rtpDelta = 0L
            }
            if (rtpDelta < 0L) return -1 // discard audio preceding the commanded render position
            val targetLocalNs = anchor - offset + rtpDelta * 1_000_000_000L / sampleRate
            val arrivalLatenessMs = (System.nanoTime() - targetLocalNs) / 1_000_000L
            val outputLeadNs = cachedOutputPipelineLeadNs
            val syncOffsetNs = audioSyncOffsetMs * 1_000_000L
            val startAtNs = targetLocalNs + syncOffsetNs - outputLeadNs
            Logger.i("Buffered schedule candidate: rtp=$rtpTimestamp anchorRtp=$anchorRtp delta=$rtpDelta " +
                "rawDelta=$rawDelta " +
                "targetLocalNs=$targetLocalNs startLocalNs=$startAtNs ptpOffsetNs=$offset " +
                "leadMs=${outputLeadNs / 1_000_000} syncOffsetMs=$audioSyncOffsetMs " +
                "targetArrivalLatenessMs=$arrivalLatenessMs")
            val prewritten = writeFirstPcm(track, firstPcm)
            if (prewritten <= 0) return -1
            val threadId = android.os.Process.myTid()
            val previousPriority = runCatching { android.os.Process.getThreadPriority(threadId) }.getOrDefault(0)
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO) }
            try {
                // Coarse sleeps can overshoot this PTP deadline by tens of milliseconds on Android.
                // Park until 1.5 ms before it, then wait in a short high-priority window.
                while (running && requestedRate > 0.0) {
                    val remaining = startAtNs - System.nanoTime()
                    if (remaining <= 0L) break
                    if (remaining > FINAL_DEADLINE_SPIN_NS) {
                        java.util.concurrent.locks.LockSupport.parkNanos(remaining - FINAL_DEADLINE_SPIN_NS)
                    }
                }
                if (!running || requestedRate <= 0.0) return -1
                track.play()
                needsScheduledStart = false
                Logger.i("Buffered audio started at PTP anchor; start error=${(System.nanoTime() - startAtNs) / 1_000_000}ms " +
                    "renderLeadMs=${outputLeadNs / 1_000_000} prequeued=${prewritten / (track.channelCount * 2)} frames")
            } finally {
                runCatching { android.os.Process.setThreadPriority(previousPriority) }
            }
            return prewritten
        } else if (System.nanoTime() - firstFrameWaitStartedNs > PTP_WAIT_FALLBACK_NS) {
            val prewritten = writeFirstPcm(track, firstPcm)
            if (prewritten <= 0) return -1
            track.play()
            needsScheduledStart = false
            Logger.w("Buffered audio PTP clock unavailable after timeout; starting unsynchronized")
            return prewritten
        } else {
            Thread.sleep(10)
            return -1
        }
    }

    /** Prime the stopped stream so its first sample sees the same output queue as later samples. */
    private fun writeFirstPcm(track: AudioTrack, pcm: ByteArray): Int {
        var total = 0
        while (total < pcm.size) {
            val written = track.write(pcm, total, pcm.size - total, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) {
                Logger.w("Could not prequeue first PCM buffer: write=$written")
                return -1
            }
            total += written
        }
        return total
    }

    /** Adapt the active AudioTrack limit to the lowest stable size, backing off on glitches. */
    private fun tuneOutputBuffer(track: AudioTrack, floorFrames: Int, nowNs: Long) {
        val underruns = track.underrunCount
        val previousUnderruns = lastObservedUnderruns
        lastObservedUnderruns = underruns
        val current = track.bufferSizeInFrames
        val next = when {
            previousUnderruns >= 0 && underruns > previousUnderruns -> {
                lastUnderrunNs = nowNs
                minOf(track.bufferCapacityInFrames, maxOf(current + floorFrames, current * 3 / 2))
            }
            nowNs - lastUnderrunNs >= 3_000_000_000L && current > floorFrames ->
                maxOf(floorFrames, current * 3 / 4)
            else -> return
        }
        if (next == current) return
        val result = track.setBufferSizeInFrames(next)
        Logger.i("AudioTrack adaptive buffer: $current->${track.bufferSizeInFrames} frames " +
            "target=$floorFrames underruns=$underruns result=$result")
    }

    private fun updateCachedOutputLead(track: AudioTrack, queuedMs: Long) {
        if (queuedMs !in 20L..500L) return
        val measuredNs = queuedMs * 1_000_000L
        cachedOutputPipelineLeadNs += (measuredNs - cachedOutputPipelineLeadNs) / 4L
        val route = track.routedDevice ?: return
        context.getSharedPreferences(OUTPUT_TIMING_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(outputLeadPreferenceKey(route), cachedOutputPipelineLeadNs)
            .apply()
    }

    private fun outputLeadPreferenceKey(device: android.media.AudioDeviceInfo): String =
        "render_lead_${device.type}_${device.productName}"

    private fun readUint32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun decodeFormat(format: Long): Triple<Int, Int, Long> {
        val sr = when {
            (format and (1L shl 23)) != 0L || (format and (1L shl 25)) != 0L -> 48000
            else -> 44100
        }
        val channels = if ((format and ((1L shl 24) or (1L shl 25))) != 0L) 2 else 2
        val codec = when {
            (format and ((1L shl 22) or (1L shl 23))) != 0L -> CODEC_AAC_LC
            else -> CODEC_UNKNOWN
        }
        return Triple(sr, channels, codec)
    }

    /** Linear PCM rate conversion with fractional phase retained across AAC decoder buffers. */
    private class Pcm16RateConverter(
        private val inputRate: Int,
        private val outputRate: Int,
        private val channels: Int,
    ) {
        private val nominalInputStep = inputRate.toDouble() / outputRate
        private var totalInputFrames = 0L
        private var nextOutputPosition = 0.0
        private var previousFrame = ShortArray(channels)
        private var hasPreviousFrame = false

        fun convert(input: ByteArray, speedCorrectionPpm: Int = 0): ByteArray {
            val frameBytes = channels * 2
            val inputFrames = input.size / frameBytes
            if (inputFrames == 0) return ByteArray(0)
            // The servo has a higher temporary limit for large phase errors. Keep the
            // converter's own clamp aligned with that limit or fast catch-up is silently
            // reduced to the fine-servo cap before it reaches the audio samples.
            val inputStep = nominalInputStep * (1.0 + speedCorrectionPpm.coerceIn(-MAX_FAST_CATCHUP_PPM, MAX_FAST_CATCHUP_PPM) / 1_000_000.0)
            val startFrame = totalInputFrames
            val windowStart = if (hasPreviousFrame) startFrame - 1L else startFrame
            val output = java.io.ByteArrayOutputStream(((inputFrames.toLong() * outputRate / inputRate + 2) * frameBytes).toInt())
            while (nextOutputPosition < startFrame + inputFrames - 1.0) {
                val leftFrame = kotlin.math.floor(nextOutputPosition).toLong()
                val fraction = nextOutputPosition - leftFrame
                val leftOffset = (leftFrame - windowStart).toInt()
                val rightOffset = leftOffset + 1
                for (channel in 0 until channels) {
                    val left = readSample(input, leftOffset, channel, frameBytes)
                    val right = readSample(input, rightOffset, channel, frameBytes)
                    val value = (left + (right - left) * fraction).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    output.write(value and 0xff)
                    output.write((value ushr 8) and 0xff)
                }
                nextOutputPosition += inputStep
            }
            val lastOffset = (inputFrames - 1) * frameBytes
            for (channel in 0 until channels) {
                val byteOffset = lastOffset + channel * 2
                previousFrame[channel] = ((input[byteOffset + 1].toInt() shl 8) or (input[byteOffset].toInt() and 0xff)).toShort()
            }
            totalInputFrames += inputFrames
            hasPreviousFrame = true
            return output.toByteArray()
        }

        /** Discards late source audio while keeping the output wall-clock rate unchanged. */
        fun skipSourceFrames(frames: Long) {
            if (frames > 0L) nextOutputPosition += frames
        }

        private fun readSample(input: ByteArray, frameOffset: Int, channel: Int, frameBytes: Int): Int {
            if (hasPreviousFrame && frameOffset == 0) return previousFrame[channel].toInt()
            val byteOffset = (frameOffset - if (hasPreviousFrame) 1 else 0) * frameBytes + channel * 2
            return ((input[byteOffset + 1].toInt() shl 8) or (input[byteOffset].toInt() and 0xff)).toShort().toInt()
        }
    }

    private companion object {
        const val RTP_HEADER = 12
        const val TAG_SIZE = 16
        const val NONCE_SIZE = 8
        const val MAX_PACKET = 65535
        // First-stream fallback before AudioTrack timestamps have calibrated the path.
        @Volatile var cachedOutputPipelineLeadNs = 100_000_000L
        const val MIN_OUTPUT_LEAD_NS = 20_000_000L
        const val MAX_OUTPUT_LEAD_NS = 250_000_000L
        const val OUTPUT_TIMING_PREFS = "phairplay_output_timing"
        const val MAX_ANCHOR_RTP_DELTA_SECONDS = 5L
        const val STARTUP_PACKET_DIAGNOSTICS = 16L
        const val POSITION_SAMPLE_INTERVAL_NS = 250_000_000L
        const val POSITION_FILTER_DIVISOR = 16L
        const val STABLE_TIMESTAMP_SAMPLES = 2
        const val STARTUP_PHASE_SKIP_THRESHOLD_NS = 20_000_000L
        const val MAX_STARTUP_PHASE_SKIP_NS = 1_000_000_000L
        // A valid PTP-anchored stream can start more than 500 ms behind after a device
        // route/session recreation. Rejecting that phase error forever disables the servo
        // (the output then remains audibly late). Keep rejecting multi-second timestamp or
        // anchor discontinuities, while allowing the bounded fast-catch-up servo to recover.
        const val MAX_POSITION_ERROR_NS = 2_000_000_000L
        const val POSITION_SERVO_DEADBAND_MS = 2.0
        const val POSITION_SERVO_PPM_PER_MS = 100.0
        const val MAX_POSITION_SPEED_PPM = 1_500
        const val FAST_CATCHUP_ERROR_MS = 20.0
        const val MAX_FAST_CATCHUP_PPM = 20_000
        const val FINAL_DEADLINE_SPIN_NS = 1_500_000L
        const val PTP_WAIT_FALLBACK_NS = 3_000_000_000L
        const val CODEC_AAC_LC = 4L
        const val CODEC_UNKNOWN = -1L
    }
}

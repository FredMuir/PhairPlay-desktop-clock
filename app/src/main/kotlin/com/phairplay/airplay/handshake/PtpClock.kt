package com.phairplay.airplay.handshake

import android.content.Context
import com.phairplay.util.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Owns the privileged PTP sockets (319/320) and exposes the sender-clock offset to the app. */
class PtpClock(private val context: Context, private val onOffset: (Long) -> Unit = {}) {
    @Volatile var masterMinusLocalNanos: Long? = null
        private set
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val running = AtomicBoolean(false)

    @Synchronized fun start(localAddress: String? = null) {
        if (running.get()) return
        stopOrphanedDaemon()
        val apk = context.applicationInfo.sourceDir
        val bindArg = localAddress?.takeIf { it.isNotBlank() }?.let { " '$it'" }.orEmpty()
        val command = "CLASSPATH='$apk' exec /system/bin/app_process /system/bin " +
            "com.phairplay.airplay.handshake.RootPtpDaemon$bindArg"
        try {
            process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            running.set(true)
            thread(name = "PtpRootOutput", isDaemon = true) {
                try {
                    BufferedReader(InputStreamReader(process!!.inputStream)).useLines { lines ->
                        lines.forEach { line ->
                            if (line.startsWith("OFFSET_NS=") || line.startsWith("OFFSET_REBASED_NS=")) {
                                masterMinusLocalNanos = line.substringAfter('=').toLongOrNull()
                                masterMinusLocalNanos?.let {
                                    onOffset(it)
                                    Logger.i("PTP clock ${if (line.startsWith("OFFSET_REBASED_NS=")) "relocked" else "locked"}: master-local=$it ns")
                                }
                            } else if (line.startsWith("PTP_BOUND=")) {
                                Logger.i("PTP helper bound ${line.substringAfter('=')}")
                            } else if (line.startsWith("PTP_RX=")) {
                                Logger.i("PTP packet ${line.substringAfter('=')}")
                            } else if (line.startsWith("PTP_DROP=")) {
                                Logger.w("PTP packet ignored ${line.substringAfter('=')}")
                            } else if (line.startsWith("OFFSET_REJECTED_NS=")) {
                                Logger.w("PTP offset sample rejected: ${line.substringAfter('=')} ns")
                            } else if (line.startsWith("PTP_ERROR=")) {
                                Logger.e("PTP helper: ${line.substringAfter('=')}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (running.get()) Logger.e("PTP helper output ended", e)
                } finally {
                    running.set(false)
                }
            }
            Logger.i("PTP root helper started for UDP 319/320")
        } catch (e: Exception) {
            running.set(false)
            Logger.e("Could not start root PTP helper", e)
        }
    }

    @Synchronized fun setPeers(addresses: List<String>, localAddress: String? = null) {
        if (addresses.isEmpty()) return
        start(localAddress)
        val values = addresses.mapNotNull { value ->
            val address = value.substringBefore('%').trim()
            runCatching { InetAddress.getByName(address) }.getOrNull()?.hostAddress
        }.distinct()
        if (values.isEmpty()) return
        try {
            writer?.apply {
                write("PEERS=${values.joinToString(",")}")
                newLine(); flush()
            }
            Logger.i("PTP peer list set (${values.size} address(es)): ${values.joinToString()}")
        } catch (e: Exception) {
            Logger.e("Could not update PTP peers", e)
        }
    }

    @Synchronized fun stop() {
        running.set(false)
        masterMinusLocalNanos = null
        runCatching { writer?.write("STOP\n"); writer?.flush() }
        // `su -c app_process` may outlive the app if only the su wrapper is destroyed.
        // Let the daemon close its sockets first, then terminate the exact child process.
        runCatching { process?.waitFor(1200, java.util.concurrent.TimeUnit.MILLISECONDS) }
        runCatching { process?.destroyForcibly() }
        runCatching { process?.waitFor(1200, java.util.concurrent.TimeUnit.MILLISECONDS) }
        stopOrphanedDaemon()
        process = null
        writer = null
    }

    /** Stops only the PID recorded by our helper, after verifying its command line. */
    private fun stopOrphanedDaemon() {
        runCatching {
            ProcessBuilder("su", "-c", "pid=\$(cat /data/local/tmp/phairplay-ptp.pid 2>/dev/null); " +
                "case \"\$pid\" in ''|*[!0-9]*) exit 0;; esac; " +
                "cmd=\$(tr '\\000' ' ' < /proc/\$pid/cmdline 2>/dev/null); " +
                "case \"\$cmd\" in *com.phairplay.airplay.handshake.RootPtpDaemon*) kill \$pid;; esac")
                .redirectErrorStream(true).start().waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
}

/**
 * Executed only by `su app_process`. It is deliberately isolated from the UI/app process so
 * Android's unprivileged app UID never attempts to bind the reserved PTP ports.
 *
 * Implements the gPTP two-step Sync/Follow_Up + Delay_Req/Delay_Resp exchange used by AirPlay.
 */
object RootPtpDaemon {
    @Volatile private var smoothedOffsetNs: Long? = null
    @Volatile private var sampleCount = 0
    @Volatile private var pendingOffsetNs: Long? = null
    @Volatile private var pendingOffsetSamples = 0
    private data class Sync(val rxLocalNs: Long, val correctionNs: Long)
    private data class Probe(val masterTxNs: Long, val localSyncRxNs: Long, val localTxNs: Long)

    @JvmStatic fun main(args: Array<String>) {
        val peers = ConcurrentHashMap.newKeySet<String>()
        val probes = ConcurrentHashMap<String, Probe>()
        val syncs = ConcurrentHashMap<String, Sync>()
        val running = AtomicBoolean(true)
        val packetLogBudget = java.util.concurrent.atomic.AtomicInteger(24)
        var event: DatagramSocket? = null
        var general: DatagramSocket? = null
        var lockFile: java.io.RandomAccessFile? = null
        var daemonLock: java.nio.channels.FileLock? = null
        try {
            // app_process is launched from a privileged child and can survive force-stopped
            // app instances. A kernel file lock makes concurrent/stale launch attempts harmless.
            lockFile = java.io.RandomAccessFile("/data/local/tmp/phairplay-ptp.lock", "rw")
            daemonLock = try { lockFile.channel.tryLock() } catch (_: Exception) { null }
            if (daemonLock == null) {
                System.out.println("PTP_ERROR=already_running")
                System.out.flush()
                return
            }
            java.io.File("/data/local/tmp/phairplay-ptp.pid").writeText(android.os.Process.myPid().toString())
            // Apple sends PTP to the receiver's IPv6 address when the RTSP session is IPv6.
            // Bind the exact RTSP local address so responses to our Delay_Req use the address
            // iOS registered for this receiver, even when wlan0 has temporary IPv6 addresses.
            // Fall back to a dual-stack wildcard only when the RTSP socket did not provide one.
            val bindAddress = args.firstOrNull()?.let { InetAddress.getByName(it) }
                ?: InetAddress.getByName("::")
            event = DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(bindAddress, 319)); soTimeout = 500 }
            general = DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(bindAddress, 320)); soTimeout = 500 }
            System.out.println("PTP_BOUND=${bindAddress.hostAddress}:319,${bindAddress.hostAddress}:320")
            System.out.flush()
            val eventSocket = event
            val generalSocket = general
            thread(name = "PtpRootCommands", isDaemon = true) {
                try {
                    BufferedReader(InputStreamReader(System.`in`)).useLines { lines ->
                        lines.forEach { line ->
                            when {
                                line == "STOP" -> running.set(false)
                                line.startsWith("PEERS=") -> {
                                    peers.clear()
                                    peers.addAll(line.substringAfter('=').split(',').map { it.substringBefore('%') })
                                }
                            }
                        }
                    }
                } catch (_: Exception) { running.set(false) }
            }
            val eventThread = thread(name = "PtpEvent319", isDaemon = true) {
                val buffer = ByteArray(2048)
                while (running.get()) receiveOne(eventSocket, eventSocket, buffer, peers, syncs, probes, running, packetLogBudget)
            }
            val generalThread = thread(name = "PtpGeneral320", isDaemon = true) {
                val buffer = ByteArray(2048)
                while (running.get()) receiveOne(generalSocket, eventSocket, buffer, peers, syncs, probes, running, packetLogBudget)
            }
            while (running.get()) Thread.sleep(250)
            eventThread.join(1000); generalThread.join(1000)
        } catch (e: Exception) {
            System.out.println("PTP_ERROR=${e.javaClass.simpleName}:${e.message}")
            System.out.flush()
        } finally {
            running.set(false)
            runCatching { event?.close() }
            runCatching { general?.close() }
            runCatching {
                val pidFile = java.io.File("/data/local/tmp/phairplay-ptp.pid")
                if (pidFile.readText().trim() == android.os.Process.myPid().toString()) pidFile.delete()
            }
            runCatching { daemonLock?.release() }
            runCatching { lockFile?.close() }
        }
    }

    private fun receiveOne(
        socket: DatagramSocket,
        eventSocket: DatagramSocket,
        bytes: ByteArray,
        peers: Set<String>,
        syncs: MutableMap<String, Sync>,
        probes: MutableMap<String, Probe>,
        running: AtomicBoolean,
        packetLogBudget: java.util.concurrent.atomic.AtomicInteger,
    ) {
        val packet = DatagramPacket(bytes, bytes.size)
        try { socket.receive(packet) } catch (_: java.net.SocketTimeoutException) { return }
        catch (_: Exception) { if (running.get()) System.out.println("PTP_ERROR=receive:${socket.localPort}"); return }
        if (packet.length < 34) return
        val type = bytes[0].toInt() and 0x0f
        val source = packet.address.hostAddress?.substringBefore('%') ?: return
        val seq = u16(bytes, 30)
        val accepted = peers.isEmpty() || source in peers
        if (packetLogBudget.getAndDecrement() > 0) {
            val line = "${socket.localPort},type=$type,source=$source,seq=$seq,peer=$accepted"
            System.out.println(if (accepted) "PTP_RX=$line" else "PTP_DROP=$line")
            System.out.flush()
        }
        if (!accepted) return
        val key = "$source:$seq"
        val now = System.nanoTime()
        when (type) {
            0 -> syncs[key] = Sync(now, correction(bytes)) // two-step Sync; timestamp is in Follow_Up
            8 -> {
                val sync = syncs.remove(key) ?: return
                if (packet.length < 44) return
                val origin = timestamp(bytes, 34) + sync.correctionNs + correction(bytes)
                val seqOut = seq
                val delayReq = ByteArray(44)
                header(delayReq, 1, seqOut, u8(bytes, 4))
                // sourcePortIdentity is a stable, locally unique clock identity.
                val id = CLOCK_ID
                for (i in 0 until 8) delayReq[20 + i] = (id ushr (56 - i * 8)).toByte()
                delayReq[28] = 0x80.toByte(); delayReq[29] = 0x01
                val localTx = System.nanoTime()
                probes[key] = Probe(origin, sync.rxLocalNs, localTx)
                runCatching { eventSocket.send(DatagramPacket(delayReq, delayReq.size, packet.address, 319)) }
            }
            9 -> {
                val probe = probes.remove(key) ?: return
                if (packet.length < 44) return
                val masterRx = timestamp(bytes, 34) + correction(bytes)
                // Offset is master time minus local monotonic time; path delay cancels in the
                // standard four-timestamp calculation (assuming approximately symmetric Wi-Fi).
                val offset = ((probe.masterTxNs - probe.localSyncRxNs) +
                    (masterRx - probe.localTxNs)) / 2L
                val previous = smoothedOffsetNs
                if (previous != null && kotlin.math.abs(offset - previous) > MAX_OFFSET_STEP_NS) {
                    // Reject isolated Wi-Fi timestamp outliers, but allow a real PTP master
                    // offset change after several mutually consistent samples. A strict
                    // per-sample step limit otherwise leaves the receiver stuck on a stale
                    // clock offset indefinitely after a master/session transition.
                    val pending = pendingOffsetNs
                    if (pending != null && kotlin.math.abs(offset - pending) <= MAX_RELOCK_CLUSTER_SPREAD_NS) {
                        pendingOffsetSamples++
                        pendingOffsetNs = pending + (offset - pending) / pendingOffsetSamples
                    } else {
                        pendingOffsetNs = offset
                        pendingOffsetSamples = 1
                    }
                    if (pendingOffsetSamples >= RELOCK_CONFIRM_SAMPLES) {
                        val relocked = pendingOffsetNs!!
                        smoothedOffsetNs = relocked
                        pendingOffsetNs = null
                        pendingOffsetSamples = 0
                        sampleCount++
                        System.out.println("OFFSET_REBASED_NS=$relocked")
                        System.out.flush()
                    } else {
                        System.out.println("OFFSET_REJECTED_NS=$offset (previous=$previous, cluster=$pendingOffsetSamples)")
                        System.out.flush()
                    }
                    return
                }
                pendingOffsetNs = null
                pendingOffsetSamples = 0
                val filtered = if (previous == null) offset else previous + (offset - previous) / 8L
                smoothedOffsetNs = filtered
                sampleCount++
                if (sampleCount == 1 || sampleCount % 8 == 0) {
                    System.out.println("OFFSET_NS=$filtered")
                    System.out.flush()
                }
            }
        }
    }

    private fun header(out: ByteArray, messageType: Int, seq: Int, domain: Int) {
        out.fill(0, 0, 34)
        out[0] = (0x10 or messageType).toByte() // AirPlay gPTP majorSdoId=1
        out[1] = 2
        out[2] = 0; out[3] = 44
        out[4] = domain.toByte()
        out[20] = 0x02; out[21] = 0x50; out[22] = 0x4c; out[23] = 0x41
        out[24] = 0x59; out[25] = 0x50; out[26] = 0x54; out[27] = 0x50
        out[28] = 0x80.toByte(); out[29] = 0x01
        out[30] = (seq ushr 8).toByte(); out[31] = seq.toByte()
        out[32] = 1
    }

    private fun correction(b: ByteArray): Long {
        var value = 0L
        for (i in 8..15) value = (value shl 8) or (b[i].toLong() and 0xff)
        return value shr 16 // PTP scaled nanoseconds, signed 64-bit 2^-16 ns units
    }

    private fun timestamp(b: ByteArray, at: Int): Long {
        var seconds = 0L
        for (i in at until at + 6) seconds = (seconds shl 8) or (b[i].toLong() and 0xff)
        var nanos = 0L
        for (i in at + 6 until at + 10) nanos = (nanos shl 8) or (b[i].toLong() and 0xff)
        return seconds * 1_000_000_000L + nanos
    }

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xff) shl 8) or (b[at + 1].toInt() and 0xff)
    private fun u8(b: ByteArray, at: Int) = b[at].toInt() and 0xff
    private const val MAX_OFFSET_STEP_NS = 5_000_000L
    private const val MAX_RELOCK_CLUSTER_SPREAD_NS = 2_000_000L
    private const val RELOCK_CONFIRM_SAMPLES = 3
    private const val CLOCK_ID = 0x02504c4159505450L
}

package com.phairplay.airplay.handshake

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import com.phairplay.util.Logger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** HAP control-channel framing and ChaCha20-Poly1305 protection after transient Pair-Setup. */
internal class HapControlCrypto(sharedSecret: ByteArray) {
    // AirPlay's HKP control channel uses the Control-Salt HKDF labels from the
    // AirPlay pairing spec and interoperable receiver/client implementations.
    private val inputKey = hkdf(sharedSecret, "Control-Salt", "Control-Write-Encryption-Key")
    private val outputKey = hkdf(sharedSecret, "Control-Salt", "Control-Read-Encryption-Key")
    private var inputCounter = 0L
    private var outputCounter = 0L
    private var loggedFirstInputFrame = false

    fun wrapInput(source: InputStream): InputStream = object : InputStream() {
        private var plain = ByteArray(0)
        private var offset = 0

        override fun read(): Int {
            if (!fill()) return -1
            return plain[offset++].toInt() and 0xff
        }

        override fun read(target: ByteArray, targetOffset: Int, length: Int): Int {
            if (length == 0) return 0
            if (!fill()) return -1
            val n = minOf(length, plain.size - offset)
            System.arraycopy(plain, offset, target, targetOffset, n)
            offset += n
            return n
        }

        private fun fill(): Boolean {
            if (offset < plain.size) return true
            val first = source.read()
            if (first < 0) return false
            val second = source.read()
            if (second < 0) throw EOFException("incomplete HAP frame length")
            val size = first or (second shl 8)
            if (!loggedFirstInputFrame) {
                loggedFirstInputFrame = true
                Logger.i("HAP encrypted input frame header=$first,$second size=$size counter=$inputCounter")
            }
            require(size in 1..MAX_BLOCK) { "invalid HAP frame size $size" }
            val sealed = ByteArray(size + TAG_BYTES)
            readFully(source, sealed)
            plain = crypt(Cipher.DECRYPT_MODE, inputKey, inputCounter++, sealed, byteArrayOf(first.toByte(), second.toByte()))
            require(plain.size == size) { "HAP frame plaintext length mismatch" }
            offset = 0
            return true
        }
    }

    fun write(output: OutputStream, plaintext: ByteArray) {
        var offset = 0
        while (offset < plaintext.size) {
            val count = minOf(MAX_BLOCK, plaintext.size - offset)
            val block = plaintext.copyOfRange(offset, offset + count)
            val lengthHeader = byteArrayOf((count and 0xff).toByte(), ((count ushr 8) and 0xff).toByte())
            val sealed = crypt(Cipher.ENCRYPT_MODE, outputKey, outputCounter++, block, lengthHeader)
            output.write(lengthHeader)
            output.write(sealed)
            offset += count
        }
        output.flush()
    }

    private fun nonce(counter: Long) = ByteArray(12).also { n ->
        for (i in 0 until 8) n[4 + i] = (counter ushr (8 * i)).toByte()
    }

    private fun crypt(mode: Int, key: ByteArray, counter: Long, data: ByteArray, aad: ByteArray): ByteArray =
        Cipher.getInstance("ChaCha20-Poly1305").run {
            init(mode, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce(counter)))
            updateAAD(aad)
            doFinal(data)
        }

    private fun readFully(source: InputStream, data: ByteArray) {
        var p = 0
        while (p < data.size) {
            val n = source.read(data, p, data.size - p)
            if (n < 0) throw EOFException("incomplete HAP encrypted frame")
            p += n
        }
    }

    private fun hkdf(ikm: ByteArray, salt: String, info: String): ByteArray {
        val extract = Mac.getInstance("HmacSHA512").run {
            init(SecretKeySpec(salt.toByteArray(Charsets.US_ASCII), "HmacSHA512"))
            doFinal(ikm)
        }
        return Mac.getInstance("HmacSHA512").run {
            init(SecretKeySpec(extract, "HmacSHA512"))
            doFinal(info.toByteArray(Charsets.US_ASCII) + byteArrayOf(1)).copyOf(32)
        }
    }

    companion object {
        private const val TAG_BYTES = 16
        private const val MAX_BLOCK = 1024
    }
}

package com.phairplay.airplay.handshake

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Independent HAP frame protection for AirPlay 2's event channel (not the RTSP control channel). */
internal class HapEventCrypto(sharedSecret: ByteArray) {
    // The sender encrypts its event requests with Events-Write; receiver commands use Events-Read.
    private val inputKey = hkdf(sharedSecret, "Events-Salt", "Events-Write-Encryption-Key")
    private val outputKey = hkdf(sharedSecret, "Events-Salt", "Events-Read-Encryption-Key")
    private var inputCounter = 0L
    private var outputCounter = 0L

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
            val count = minOf(length, plain.size - offset)
            System.arraycopy(plain, offset, target, targetOffset, count)
            offset += count
            return count
        }

        private fun fill(): Boolean {
            if (offset < plain.size) return true
            val low = source.read()
            if (low < 0) return false
            val high = source.read()
            if (high < 0) throw EOFException("incomplete HAP event frame length")
            val size = low or (high shl 8)
            require(size in 1..MAX_BLOCK) { "invalid HAP event frame size $size" }
            val sealed = ByteArray(size + TAG_BYTES)
            readFully(source, sealed)
            val counter = synchronized(this@HapEventCrypto) { inputCounter++ }
            plain = crypt(Cipher.DECRYPT_MODE, inputKey, counter, sealed, byteArrayOf(low.toByte(), high.toByte()))
            require(plain.size == size) { "HAP event frame plaintext length mismatch" }
            offset = 0
            return true
        }
    }

    /** Writes complete RTSP messages as independently framed encrypted event-channel data. */
    @Synchronized
    fun write(output: OutputStream, plaintext: ByteArray) {
        var offset = 0
        while (offset < plaintext.size) {
            val count = minOf(MAX_BLOCK, plaintext.size - offset)
            val block = plaintext.copyOfRange(offset, offset + count)
            val header = byteArrayOf((count and 0xff).toByte(), ((count ushr 8) and 0xff).toByte())
            val sealed = crypt(Cipher.ENCRYPT_MODE, outputKey, outputCounter++, block, header)
            output.write(header)
            output.write(sealed)
            offset += count
        }
        output.flush()
    }

    private fun nonce(counter: Long) = ByteArray(12).also { nonce ->
        for (index in 0 until 8) nonce[4 + index] = (counter ushr (8 * index)).toByte()
    }

    private fun crypt(mode: Int, key: ByteArray, counter: Long, data: ByteArray, aad: ByteArray): ByteArray =
        Cipher.getInstance("ChaCha20-Poly1305").run {
            init(mode, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce(counter)))
            updateAAD(aad)
            doFinal(data)
        }

    private fun readFully(source: InputStream, data: ByteArray) {
        var position = 0
        while (position < data.size) {
            val count = source.read(data, position, data.size - position)
            if (count < 0) throw EOFException("incomplete HAP encrypted event frame")
            position += count
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

    private companion object {
        const val TAG_BYTES = 16
        const val MAX_BLOCK = 1024
    }
}

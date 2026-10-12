package com.phairplay.airplay.handshake

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HomeKit transient Pair-Setup (HKP 4), the auth mode used by HomePod-class AirPlay receivers. */
internal class HapTransientPairSetup {
    data class Step(val response: ByteArray, val controlSecret: ByteArray? = null)

    private val random = SecureRandom()
    private val salt = ByteArray(16).also(random::nextBytes)
    private val b = BigInteger(512, random)
    private val verifier = G.modPow(x(salt), N)
    private val multiplier = BigInteger(1, sha512(pad(N), pad(G)))
    private val serverPublic = multiplier.multiply(verifier).add(G.modPow(b, N)).mod(N)
    private var completed = false

    fun handle(body: ByteArray): Step {
        val tlv = decode(body)
        return when (tlv[STATE]?.singleOrNull()?.toInt()?.and(0xff)) {
            1 -> {
                require(tlv[METHOD]?.singleOrNull()?.toInt()?.and(0xff) == 0) { "unsupported HKP method" }
                require((tlv[FLAGS]?.singleOrNull()?.toInt()?.and(0xff) ?: 0) and TRANSIENT_FLAG != 0) {
                    "HKP 4 request did not ask for transient pairing"
                }
                Step(encode(listOf(STATE to byteArrayOf(2), SALT to salt, PUBLIC_KEY to pad(serverPublic))))
            }
            3 -> {
                require(!completed) { "duplicate transient Pair-Setup M3" }
                val clientABytes = tlv[PUBLIC_KEY] ?: error("M3 missing public key")
                val clientProof = tlv[PROOF] ?: error("M3 missing proof")
                require(clientABytes.size == GROUP_BYTES && clientProof.size == 64) { "invalid M3 field length" }
                val clientA = BigInteger(1, clientABytes)
                require(clientA.mod(N) != BigInteger.ZERO) { "invalid SRP public value" }
                val serverB = pad(serverPublic)
                val u = BigInteger(1, sha512(pad(clientA), serverB))
                val shared = clientA.multiply(verifier.modPow(u, N)).mod(N).modPow(b, N)
                val sessionKey = sha512(unsigned(shared))
                val expected = sha512(
                    xor(sha512(unsigned(N)), sha512(unsigned(G))),
                    sha512(USER), salt, unsigned(clientA), unsigned(serverPublic), sessionKey
                )
                require(MessageDigest.isEqual(expected, clientProof)) { "transient SRP proof mismatch" }
                val serverProof = sha512(unsigned(clientA), clientProof, sessionKey)
                completed = true
                Step(encode(listOf(STATE to byteArrayOf(4), PROOF to serverProof)), sessionKey)
            }
            else -> error("unexpected transient Pair-Setup state")
        }
    }

    private fun x(s: ByteArray): BigInteger = BigInteger(1, sha512(s, sha512(USER + byteArrayOf(':'.code.toByte()) + TRANSIENT_PIN)))

    private fun decode(bytes: ByteArray): Map<Int, ByteArray> {
        val result = mutableMapOf<Int, ByteArray>()
        var p = 0
        while (p + 2 <= bytes.size) {
            val tag = bytes[p].toInt() and 0xff
            val length = bytes[p + 1].toInt() and 0xff
            require(p + 2 + length <= bytes.size) { "malformed TLV8" }
            val value = bytes.copyOfRange(p + 2, p + 2 + length)
            if (tag == SEPARATOR) result.clear() else result[tag] = (result[tag] ?: ByteArray(0)) + value
            p += 2 + length
        }
        require(p == bytes.size) { "trailing malformed TLV8 byte" }
        return result
    }

    private fun encode(items: List<Pair<Int, ByteArray>>): ByteArray {
        val out = ArrayList<Byte>()
        items.forEach { (tag, value) ->
            var offset = 0
            while (offset < value.size || (value.isEmpty() && offset == 0)) {
                val length = minOf(255, value.size - offset)
                out += tag.toByte(); out += length.toByte()
                for (i in offset until offset + length) out += value[i]
                offset += length
                if (value.isEmpty()) break
            }
        }
        return out.toByteArray()
    }

    private fun pad(value: BigInteger): ByteArray {
        val raw = unsigned(value)
        require(raw.size <= GROUP_BYTES)
        return ByteArray(GROUP_BYTES - raw.size) + raw
    }

    private fun unsigned(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }

    private fun sha512(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        parts.forEach(md::update)
        return md.digest()
    }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

    companion object {
        private const val METHOD = 0
        private const val PUBLIC_KEY = 3
        private const val PROOF = 4
        private const val STATE = 6
        private const val SALT = 2
        private const val FLAGS = 19
        private const val SEPARATOR = 255
        private const val TRANSIENT_FLAG = 0x10
        private val GROUP_BYTES = (SRP6StandardGroups.rfc5054_3072.n.bitLength() + 7) / 8
        private val USER = "Pair-Setup".toByteArray(Charsets.UTF_8)
        private val TRANSIENT_PIN = "3939".toByteArray(Charsets.US_ASCII)
        // RFC 5054 3072-bit SRP group used by HomeKit Pair-Setup with SHA-512.
        private val N = SRP6StandardGroups.rfc5054_3072.n
        private val G = SRP6StandardGroups.rfc5054_3072.g
    }
}

package io.github.oluengo.tansu.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado NIP-44 v2 (ChaCha20 + HMAC-SHA256 con claves HKDF a partir del ECDH). Lo usan los firmantes
 * remotos NIP-46 (nsecBunker). ChaCha20 va implementado aquí: Android no lo ofrece antes de la API 28.
 */
object Nip44 {
    private val SALT = "nip44-v2".toByteArray()

    fun conversationKey(sk: ByteArray, peerPubX: ByteArray): ByteArray = hmac(SALT, Secp256k1.sharedX(sk, peerPubX))

    fun encrypt(plain: String, conversationKey: ByteArray, nonce: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)): String {
        val pt = plain.toByteArray(Charsets.UTF_8)
        require(pt.isNotEmpty() && pt.size <= 65535) { "longitud no válida" }
        val (key, cn, hk) = messageKeys(conversationKey, nonce)
        val padded = ByteArray(2 + paddedLen(pt.size))
        padded[0] = (pt.size ushr 8).toByte(); padded[1] = pt.size.toByte()
        pt.copyInto(padded, 2)
        val ct = chacha20(key, cn, padded)
        val mac = hmac(hk, nonce + ct)
        return Base64.getEncoder().encodeToString(byteArrayOf(2) + nonce + ct + mac)
    }

    fun decrypt(payload: String, conversationKey: ByteArray): String {
        require(payload.isNotEmpty() && payload[0] != '#') { "versión no soportada" }
        val data = Base64.getDecoder().decode(payload)
        require(data.size >= 99 && data[0].toInt() == 2) { "versión no soportada" }
        val nonce = data.copyOfRange(1, 33)
        val ct = data.copyOfRange(33, data.size - 32)
        val mac = data.copyOfRange(data.size - 32, data.size)
        val (key, cn, hk) = messageKeys(conversationKey, nonce)
        require(MessageDigest.isEqual(mac, hmac(hk, nonce + ct))) { "mac no válido" }
        val padded = chacha20(key, cn, ct)
        val len = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        require(len in 1..65535 && padded.size == 2 + paddedLen(len)) { "relleno no válido" }
        return String(padded, 2, len, Charsets.UTF_8)
    }

    fun paddedLen(n: Int): Int {
        if (n <= 32) return 32
        val next = Integer.highestOneBit(n - 1) shl 1
        val chunk = if (next <= 256) 32 else next / 8
        return chunk * ((n - 1) / chunk + 1)
    }

    private fun messageKeys(ck: ByteArray, nonce: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        require(ck.size == 32 && nonce.size == 32)
        val okm = ByteArray(76)
        var t = ByteArray(0); var pos = 0; var i = 1
        while (pos < 76) {
            t = hmac(ck, t + nonce + byteArrayOf(i.toByte()))
            val n = minOf(t.size, 76 - pos); t.copyInto(okm, pos, 0, n); pos += n; i++
        }
        return Triple(okm.copyOfRange(0, 32), okm.copyOfRange(32, 44), okm.copyOfRange(44, 76))
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    // ---------- ChaCha20 (RFC 8439), contador desde 0 ----------

    private fun chacha20(key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val k = IntArray(8) { le(key, it * 4) }
        val n = IntArray(3) { le(nonce, it * 4) }
        val out = ByteArray(input.size)
        val block = ByteArray(64)
        var counter = 0
        var off = 0
        while (off < input.size) {
            chachaBlock(k, counter++, n, block)
            val len = minOf(64, input.size - off)
            for (j in 0 until len) out[off + j] = (input[off + j].toInt() xor block[j].toInt()).toByte()
            off += len
        }
        return out
    }

    private fun chachaBlock(k: IntArray, counter: Int, n: IntArray, out: ByteArray) {
        val s = intArrayOf(0x61707865, 0x3320646e, 0x79622d32, 0x6b206574, k[0], k[1], k[2], k[3], k[4], k[5], k[6], k[7], counter, n[0], n[1], n[2])
        val x = s.copyOf()
        fun qr(a: Int, b: Int, c: Int, d: Int) {
            x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
            x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
            x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
            x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
        }
        repeat(10) {
            qr(0, 4, 8, 12); qr(1, 5, 9, 13); qr(2, 6, 10, 14); qr(3, 7, 11, 15)
            qr(0, 5, 10, 15); qr(1, 6, 11, 12); qr(2, 7, 8, 13); qr(3, 4, 9, 14)
        }
        for (i in 0 until 16) {
            val v = x[i] + s[i]
            out[i * 4] = v.toByte(); out[i * 4 + 1] = (v ushr 8).toByte(); out[i * 4 + 2] = (v ushr 16).toByte(); out[i * 4 + 3] = (v ushr 24).toByte()
        }
    }

    private fun le(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8) or ((b[i + 2].toInt() and 0xff) shl 16) or ((b[i + 3].toInt() and 0xff) shl 24)
}

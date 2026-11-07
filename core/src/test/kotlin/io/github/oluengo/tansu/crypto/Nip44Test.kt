package io.github.oluengo.tansu.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Nip44Test {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun sk(n: Int) = ByteArray(32).also { it[31] = n.toByte() }

    /** Vector oficial de NIP-44 (sec1 = 1, sec2 = 2, nonce = 1, texto «a»). */
    @Test fun officialVector() {
        val ck = Nip44.conversationKey(sk(1), Secp256k1.pubkey(sk(2)))
        assertEquals("c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d", ck.joinToString("") { "%02x".format(it) })
        val payload = Nip44.encrypt("a", ck, hex("00".repeat(31) + "01"))
        assertEquals("AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb", payload)
        assertEquals("a", Nip44.decrypt(payload, ck))
    }

    @Test fun roundTripBothSides() {
        val a = Secp256k1.randomSecret(); val b = Secp256k1.randomSecret()
        val ckA = Nip44.conversationKey(a, Secp256k1.pubkey(b))
        val ckB = Nip44.conversationKey(b, Secp256k1.pubkey(a))
        val msg = "{\"id\":\"1\",\"method\":\"sign_event\",\"params\":[\"ñ ✓ " + "x".repeat(300) + "\"]}"
        assertEquals(msg, Nip44.decrypt(Nip44.encrypt(msg, ckA), ckB))
    }

    @Test fun padding() {
        assertEquals(32, Nip44.paddedLen(1)); assertEquals(32, Nip44.paddedLen(32)); assertEquals(64, Nip44.paddedLen(33))
        assertEquals(320, Nip44.paddedLen(257)); assertEquals(384, Nip44.paddedLen(321))
    }

    @Test fun tamperedMacFails() {
        val ck = Nip44.conversationKey(sk(1), Secp256k1.pubkey(sk(2)))
        val p = Nip44.encrypt("hola", ck).toCharArray().also { it[60] = if (it[60] == 'A') 'B' else 'A' }.concatToString()
        assertThrows(IllegalArgumentException::class.java) { Nip44.decrypt(p, ck) }
    }
}

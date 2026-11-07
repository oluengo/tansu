package io.github.oluengo.tansu.crypto

import io.github.oluengo.tansu.nostr.EventSigning
import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.NostrJson
import io.github.oluengo.tansu.nostr.hexToBytes
import io.github.oluengo.tansu.nostr.toHex
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {
    private fun h(s: String) = s.lowercase().hexToBytes()

    // Vectores oficiales de BIP-340 (índices 0 y 1)
    @Test fun bip340Vector0() {
        val sk = h("0000000000000000000000000000000000000000000000000000000000000003")
        assertEquals("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", Secp256k1.pubkey(sk).toHex())
        val sig = Secp256k1.sign(ByteArray(32), sk, ByteArray(32))
        assertEquals(
            "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0",
            sig.toHex(),
        )
        assertTrue(Secp256k1.verify(ByteArray(32), Secp256k1.pubkey(sk), sig))
    }

    @Test fun bip340Vector1() {
        val sk = h("B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF")
        val msg = h("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89")
        val aux = h("0000000000000000000000000000000000000000000000000000000000000001")
        assertEquals("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", Secp256k1.pubkey(sk).toHex())
        val sig = Secp256k1.sign(msg, sk, aux)
        assertEquals(
            "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de33418906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a",
            sig.toHex(),
        )
        assertTrue(Secp256k1.verify(msg, Secp256k1.pubkey(sk), sig))
        val bad = sig.copyOf().also { it[63] = (it[63].toInt() xor 1).toByte() }
        assertFalse(Secp256k1.verify(msg, Secp256k1.pubkey(sk), bad))
    }

    @Test fun generatorAndRandomKeys() {
        assertEquals("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798", Secp256k1.pubkey(h("0000000000000000000000000000000000000000000000000000000000000001")).toHex())
        repeat(3) {
            val sk = Secp256k1.randomSecret()
            val msg = Secp256k1.sha256("hola $it".toByteArray())
            assertTrue(Secp256k1.verify(msg, Secp256k1.pubkey(sk), Secp256k1.sign(msg, sk)))
        }
    }

    @Test fun nip04BothWays() {
        val a = Secp256k1.randomSecret()
        val b = Secp256k1.randomSecret()
        assertEquals(Secp256k1.sharedX(a, Secp256k1.pubkey(b)).toHex(), Secp256k1.sharedX(b, Secp256k1.pubkey(a)).toHex())
        val msg = "¡Hola! «pay_invoice» ⚡ 21 sats"
        val enc = Nip04.encrypt(msg, a, Secp256k1.pubkey(b))
        assertTrue(enc.contains("?iv="))
        assertEquals(msg, Nip04.decrypt(enc, b, Secp256k1.pubkey(a)))
    }

    @Test fun eventsSignAndVerify() {
        val sk = Secp256k1.randomSecret()
        val e = EventSigning.sign(1, listOf(listOf("t", "radio"), listOf("e", "x", "")), "línea 1\n\"cita\"\t\\ \u0001", sk, 1_700_000_000)
        assertTrue(EventSigning.verify(e))
        assertTrue(EventSigning.verify(Event.fromJson(e.toJson().toString())!!))
        assertFalse(EventSigning.verify(e.copy(content = "otro")))
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", EventSigning.quote("a\"b\\c\nd\u0001"))
    }
}

package io.github.oluengo.tansu.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Nip46Test {
    private val pk = "fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52"

    @Test fun parseBunkerUri() {
        val u = BunkerUri.parse("bunker://$pk?relay=wss%3A%2F%2Frelay.nsec.app&relay=nos.lol&secret=abc123")!!
        assertEquals(pk, u.signerPubkey)
        assertEquals(listOf("wss://relay.nsec.app", "wss://nos.lol"), u.relays)
        assertEquals("abc123", u.secret)
        assertEquals(u, BunkerUri.parse(u.toUri()))
    }

    @Test fun rejectsBad() {
        assertNull(BunkerUri.parse("bunker://nope?relay=wss://x.y"))
        assertNull(BunkerUri.parse("bunker://$pk"))
        assertNull(BunkerUri.parse("nostrconnect://$pk?relay=wss://x.y"))
    }
}

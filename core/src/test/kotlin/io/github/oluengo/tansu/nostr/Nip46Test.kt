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

class ReactionTest {
    private val addr = "30311:abc:d1"
    private fun ev(content: String, tags: List<List<String>>) = Event("i", "pk", 1, Kinds.REACTION, tags, content)

    @Test fun parse() {
        assertEquals("🔥", Reaction.from(ev("🔥", Reaction.tags(addr, "p1", "m1")), addr)!!.emoji)
        assertEquals("m1", Reaction.from(ev("+", Reaction.tags(addr, "p1", "m1")), addr)!!.eventId)
        assertEquals("❤️", Reaction.from(ev("+", Reaction.tags(addr, "p1", null)), addr)!!.emoji)
        assertNull(Reaction.from(ev("-", Reaction.tags(addr, "p1", null)), addr))
        assertNull(Reaction.from(ev("+", listOf(listOf("p", "x"))), addr))
        assertEquals(listOf("k", "1311"), Reaction.tags(addr, "p1", "m1").last())
    }
}

class ScheduledTest {
    @Test fun plannedStreamAndCalendar() {
        val live = Event("1", "aa".repeat(32), 10, Kinds.LIVE_EVENT, listOf(listOf("d", "x"), listOf("title", "Jam"), listOf("status", "planned"), listOf("starts", "2000000000")))
        val s = Scheduled.from(live)!!
        assertEquals(2000000000L, s.startsAt); assertEquals(false, s.fromCalendar)
        val ended = live.copy(tags = live.tags.map { if (it[0] == "status") listOf("status", "ended") else it })
        assertNull(Scheduled.from(ended))
        val cal = Event("2", "bb".repeat(32), 10, Kinds.CALENDAR_TIME, listOf(listOf("d", "y"), listOf("title", "Show"), listOf("start", "2000003600"), listOf("start_tzid", "America/New_York")))
        val k = Scheduled.from(cal)!!
        assertEquals("America/New_York", k.tzid)
        assertEquals("America/New_York", k.otherZone(java.time.ZoneId.of("Europe/Madrid"))?.id)
        assertNull(k.otherZone(java.time.ZoneId.of("America/New_York")))
    }
}

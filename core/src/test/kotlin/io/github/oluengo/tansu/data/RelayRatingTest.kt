package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.nostr.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RelayRatingTest {
    @Test fun perfecto() {
        val r = RelayRating.of(RelaySignals(4, 4, 0, 30, 30, 0, 24, 212, 140))!!
        assertEquals(5, r.stars); assertTrue(!r.partial)
    }

    @Test fun perderDatosTopeTres() {
        val r = RelayRating.of(RelaySignals(4, 4, 0, 29, 30, 3, 24, 154, 180))!!
        assertTrue(r.capped); assertEquals(3, r.stars)
    }

    @Test fun malo() {
        assertEquals(1, RelayRating.of(RelaySignals(1, 4, 0, 9, 20, 6, 10, 2, 1400))!!.stars)
    }

    @Test fun provisionalYSinDatos() {
        val r = RelayRating.of(RelaySignals(monOnline = 3, monTotal = 3, follows = 18, ms = 260))!!
        assertTrue(r.partial); assertNull(r.keep)
        assertNull(RelayRating.of(RelaySignals(follows = 5)))
        // Menos de 3 cargas no cuenta como historial
        assertNull(RelayRating.of(RelaySignals(histOk = 1, histTotal = 1, ms = 100)))
    }

    @Test fun monitores() {
        fun ev(pk: String, at: Long, d: String, rtt: String? = null) =
            Event("x$pk$at", pk, at, 30166, listOfNotNull(listOf("d", d), rtt?.let { listOf("rtt-open", it) }))
        val now = 1_000_000L
        val m = RelayReputation.parseMonitors(listOf(
            ev("a", now - 600, "wss://nos.lol/", "120"), ev("a", now - 90_000, "wss://nos.lol/"),
            ev("b", now - 20_000, "wss://nos.lol", "300"), ev("c", now - 100, "wss://Nos.lol", "200"),
        ), now)
        val n = m.getValue("wss://nos.lol")
        assertEquals(3, n.total); assertEquals(2, n.online); assertEquals(200L, n.ms)
    }

    @Test fun seguidos() {
        val l = listOf(
            Event("1", "a", 10, 10002, listOf(listOf("r", "wss://nos.lol"), listOf("r", "wss://nos.lol/"))),
            Event("2", "a", 5, 10002, listOf(listOf("r", "wss://old.example.com"))),
            Event("3", "b", 10, 10002, listOf(listOf("r", "nos.lol"), listOf("r", "wss://relay.damus.io"))),
        )
        val c = RelayReputation.countFollows(l)
        assertEquals(2, c["wss://nos.lol"]); assertEquals(1, c["wss://relay.damus.io"]); assertNull(c["wss://old.example.com"])
    }

    @Test fun perdidas() {
        val f = File.createTempFile("stats", ".json"); f.delete()
        var t = 1_000L
        val st = RelayStats(f) { t }
        val pk = "p".repeat(64)
        val a1 = "30078:$pk:x"; val a2 = "30078:$pk:y"
        fun snap(idx: Map<String, Set<String>>, reached: Set<String> = idx.keys) =
            Snapshot(pk, listOf("wss://a", "wss://b"), emptyList(), emptyList(), 0, reached, idx.mapValues { (_, s) -> s.associateWith { "id" to 1L } })
        st.record(snap(mapOf("wss://a" to setOf(a1, a2), "wss://b" to setOf(a1, a2))))
        // b pierde y (sigue en a): pérdida. Luego lo borras tú de a: ya no cuenta.
        t += 10
        assertEquals(listOf(a2), st.record(snap(mapOf("wss://a" to setOf(a1, a2), "wss://b" to setOf(a1))))["wss://b"])
        assertEquals(1 to 2, st.retention(pk, "wss://b"))
        st.forget(pk, listOf(a2), "wss://a")
        assertTrue(st.record(snap(mapOf("wss://a" to setOf(a1), "wss://b" to setOf(a1)))).isEmpty())
        assertEquals(3 to 3, st.history("wss://a"))
        // Persistencia
        assertEquals(1 to 2, RelayStats(f) { t }.retention(pk, "wss://b"))
    }
}

package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.EventSigning
import io.github.oluengo.tansu.nostr.Filter
import io.github.oluengo.tansu.nostr.Kinds
import io.github.oluengo.tansu.nostr.RelayPool

/** Lo que dicen los monitores NIP-66 de un relay: cuántos lo ven en línea, cuántos lo vigilan, último aviso, respuesta. */
data class MonitorInfo(val online: Int, val total: Int, val last: Long, val ms: Long?)

/**
 * Señales públicas de fiabilidad: monitores NIP-66 (eventos 30166) y cuántos de tus seguidos usan cada relay (NIP-65).
 * Ninguna es imprescindible: si no hay datos, la valoración usa el resto.
 */
class RelayReputation(private val pool: RelayPool, private val now: () -> Long = { System.currentTimeMillis() / 1000 }) {

    /** Monitores de los últimos 7 días; «en línea» = aviso de las últimas [ONLINE_H] horas. */
    suspend fun monitors(relays: Collection<String>): Map<String, MonitorInfo> {
        if (relays.isEmpty()) return emptyMap()
        val ds = relays.flatMap { listOf(it, "$it/") }
        val evs = pool.query(MONITOR_RELAYS, listOf(Filter(kinds = listOf(KIND_DISCOVERY), tags = mapOf("d" to ds), since = now() - 7 * 86_400, limit = 2000)), timeoutMs = 7_000)
        return parseMonitors(evs.filter { EventSigning.verify(it) }, now())
    }

    /** Relay → cuántas cuentas de las que sigues lo tienen en su lista NIP-65. */
    suspend fun follows(pubkey: String, relays: List<String>): Map<String, Int> {
        val contacts = pool.query((relays + RelayPool.INDEXERS).distinct(), listOf(Filter(kinds = listOf(Kinds.CONTACTS), authors = listOf(pubkey), limit = 1)), timeoutMs = 6_000)
            .filter { it.pubkey == pubkey }.maxByOrNull { it.createdAt } ?: return emptyMap()
        val follows = contacts.tagsNamed("p").mapNotNull { it.getOrNull(1)?.lowercase() }.filter { it.length == 64 }.distinct().take(MAX_FOLLOWS)
        if (follows.isEmpty()) return emptyMap()
        val lists = follows.chunked(250).flatMap { chunk ->
            pool.query(RelayPool.INDEXERS + relays.take(3), listOf(Filter(kinds = listOf(Kinds.RELAY_LIST), authors = chunk)), timeoutMs = 8_000)
        }
        return countFollows(lists)
    }

    companion object {
        const val KIND_DISCOVERY = 30166
        const val ONLINE_H = 3L
        const val MAX_FOLLOWS = 1000
        val MONITOR_RELAYS = listOf("wss://relay.nostr.watch", "wss://history.nostr.watch", "wss://monitorlizard.nostr1.com", "wss://relaypag.es")

        fun parseMonitors(evs: List<Event>, now: Long): Map<String, MonitorInfo> =
            evs.mapNotNull { e -> e.tag("d")?.let(RelayPool::normalize)?.let { it to e } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, l) ->
                    val latest = l.groupBy { it.pubkey }.mapValues { (_, x) -> x.maxBy { it.createdAt } }.values
                    val rtts = latest.mapNotNull { e -> e.tagsNamed("rtt-open").firstNotNullOfOrNull { it.getOrNull(1)?.toLongOrNull() } }.sorted()
                    MonitorInfo(
                        online = latest.count { it.createdAt > now - ONLINE_H * 3600 },
                        total = latest.size,
                        last = latest.maxOf { it.createdAt },
                        ms = rtts.getOrNull(rtts.size / 2),
                    )
                }

        /** Cuenta cada relay una vez por autor (la lista más nueva de cada uno). */
        fun countFollows(lists: List<Event>): Map<String, Int> {
            val latest = lists.filter { it.kind == Kinds.RELAY_LIST }.groupBy { it.pubkey }.mapValues { (_, l) -> l.maxBy { it.createdAt } }.values
            val out = HashMap<String, Int>()
            for (e in latest) e.tagsNamed("r").mapNotNull { it.getOrNull(1)?.let(RelayPool::normalize) }.toSet().forEach { out[it] = (out[it] ?: 0) + 1 }
            return out
        }
    }
}

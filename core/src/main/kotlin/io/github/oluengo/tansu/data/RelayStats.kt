package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.nostr.NostrJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File

/**
 * Historial de cada relay, solo en este dispositivo:
 * - si respondió en las últimas [HISTORY] cargas;
 * - qué datos de cada cuenta tenía (direcciones), para notar si pierde alguno sin que lo borres;
 * - datos perdidos (dirección + cuándo), de los últimos [LOST_DAYS] días.
 * Solo direcciones (kind:pubkey:d), nunca contenido.
 */
class RelayStats(private val file: File, private val now: () -> Long = { System.currentTimeMillis() / 1000 }) {
    private val lock = Any()
    private var seen = HashMap<String, String>()                         // relay → "1101…" (1 = respondió)
    private var had = HashMap<String, HashMap<String, Set<String>>>()    // pubkey → relay → direcciones
    private var lost = HashMap<String, HashMap<String, Long>>()          // relay → dirección → cuándo
    private var loaded = false

    /** Apunta una carga: quién respondió y qué tiene cada relay. Devuelve los datos perdidos ahora, por relay. */
    fun record(snap: Snapshot): Map<String, List<String>> = synchronized(lock) {
        load()
        val out = HashMap<String, List<String>>()
        val everywhere = snap.index.values.flatMap { it.keys }.toSet()
        val mine = had.getOrPut(snap.pubkey) { HashMap() }
        for (r in snap.relays) {
            seen[r] = ((seen[r] ?: "") + if (r in snap.reached) "1" else "0").takeLast(HISTORY)
            if (r !in snap.reached || r in snap.truncated) continue
            val has = snap.index[r]?.keys.orEmpty()
            val gone = mine[r].orEmpty().filter { it !in has && it in everywhere }
            if (gone.isNotEmpty()) {
                val l = lost.getOrPut(r) { HashMap() }
                gone.forEach { l.putIfAbsent(it, now()) }
                out[r] = gone
            }
            mine[r] = has
        }
        save()
        out
    }

    /** Lo has borrado tú de [relay] (o de todos, con null): no cuenta como pérdida. */
    fun forget(pubkey: String, addresses: Collection<String>, relay: String? = null) = synchronized(lock) {
        load()
        val drop = addresses.toSet()
        had[pubkey]?.replaceAll { r, set -> if (relay == null || r == relay) set - drop else set }
        save()
    }

    /** (respondió, cargas) */
    fun history(relay: String): Pair<Int, Int>? = synchronized(lock) {
        load(); seen[relay]?.let { s -> s.count { it == '1' } to s.length }
    }

    /** (perdidos, que ha tenido) de la cuenta en [relay]; null si nunca tuvo nada suyo. */
    fun retention(pubkey: String, relay: String): Pair<Int, Int>? = synchronized(lock) {
        load()
        val prefixOk = { a: String -> a.split(':').getOrNull(1) == pubkey }
        val l = lost[relay].orEmpty().filter { (a, at) -> prefixOk(a) && at > now() - LOST_DAYS * 86_400 }.keys
        val h = had[pubkey]?.get(relay).orEmpty()
        val kept = (h + l).size
        if (kept == 0) null else l.size to kept
    }

    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val o = NostrJson.parseToJsonElement(file.readText()).jsonObject
            o["seen"]?.jsonObject?.forEach { (k, v) -> seen[k] = v.jsonPrimitive.content }
            o["had"]?.jsonObject?.forEach { (pk, m) ->
                had[pk] = HashMap(m.jsonObject.mapValues { (_, a) -> a.jsonArray.map { it.jsonPrimitive.content }.toSet() })
            }
            o["lost"]?.jsonObject?.forEach { (r, m) -> lost[r] = HashMap(m.jsonObject.mapValues { it.value.jsonPrimitive.long }) }
        }
    }

    private fun save() {
        val cut = now() - LOST_DAYS * 86_400
        val json = buildJsonObject {
            put("seen", JsonObject(seen.mapValues { JsonPrimitive(it.value) }))
            put("had", JsonObject(had.mapValues { (_, m) -> JsonObject(m.mapValues { (_, s) -> buildJsonArray { s.forEach { add(JsonPrimitive(it)) } } }) }))
            put("lost", JsonObject(lost.mapValues { (_, m) -> JsonObject(m.filterValues { it > cut }.mapValues { JsonPrimitive(it.value) }) }))
        }
        runCatching { file.writeText(json.toString()) }
    }

    companion object { const val HISTORY = 30; const val LOST_DAYS = 90L }
}

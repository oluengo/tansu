package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.NostrJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File

/** Copia local de lo que se borra: los eventos tal cual (cifrados), para poder restaurarlos. */
data class TrashItem(val id: String, val pubkey: String, val d: String, val deletedAt: Long, val events: List<Event>)

class Trash(private val file: File) {
    private val lock = Any()

    fun all(): List<TrashItem> = synchronized(lock) { read() }.sortedByDescending { it.deletedAt }

    fun of(pubkey: String): List<TrashItem> = all().filter { it.pubkey == pubkey }

    fun add(pubkey: String, entry: Entry): TrashItem = synchronized(lock) {
        val item = TrashItem(entry.main.id.take(16) + System.currentTimeMillis(), pubkey, entry.d, System.currentTimeMillis(), entry.events)
        write((listOf(item) + read()).take(MAX))
        item
    }

    fun remove(id: String) = synchronized(lock) { write(read().filterNot { it.id == id }) }

    fun clear() = synchronized(lock) { write(emptyList()) }

    private fun read(): List<TrashItem> = runCatching {
        (NostrJson.parseToJsonElement(file.readText()) as JsonArray).mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                TrashItem(
                    o["id"]!!.jsonPrimitive.content, o["pubkey"]!!.jsonPrimitive.content, o["d"]!!.jsonPrimitive.content,
                    o["at"]!!.jsonPrimitive.long, o["events"]!!.jsonArray.mapNotNull(Event::fromJson),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun write(items: List<TrashItem>) {
        file.parentFile?.mkdirs()
        val json = buildJsonArray {
            items.forEach { t ->
                add(buildJsonObject {
                    put("id", t.id); put("pubkey", t.pubkey); put("d", t.d); put("at", t.deletedAt)
                    put("events", buildJsonArray { t.events.forEach { add(it.toJson()) } })
                })
            }
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    private companion object { const val MAX = 100 }
}

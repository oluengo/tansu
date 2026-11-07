package io.github.oluengo.tansu.nostr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.int
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val NostrJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Evento Nostr (NIP-01). La firma se delega en el firmante del usuario (Amber o bunker). */
data class Event(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
    val sig: String = "",
) {
    fun tag(name: String): String? = tags.firstOrNull { it.size > 1 && it[0] == name }?.get(1)
    fun tagValues(name: String): List<String> = tags.filter { it.size > 1 && it[0] == name }.map { it[1] }
    fun tagsNamed(name: String): List<List<String>> = tags.filter { it.isNotEmpty() && it[0] == name }

    /** Clave de evento direccionable: kind:pubkey:d */
    val address: String get() = "$kind:$pubkey:${tag("d").orEmpty()}"

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("pubkey", pubkey)
        put("created_at", createdAt)
        put("kind", kind)
        put("tags", buildJsonArray { tags.forEach { t -> add(buildJsonArray { t.forEach { add(it) } }) } })
        put("content", content)
        put("sig", sig)
    }

    companion object {
        /** Parseo manual y tolerante: un evento mal formado devuelve null en vez de romper la lista. */
        fun fromJson(el: JsonElement): Event? = runCatching {
            val o = el as JsonObject
            Event(
                id = o["id"]!!.jsonPrimitive.content,
                pubkey = o["pubkey"]!!.jsonPrimitive.content,
                createdAt = o["created_at"]!!.jsonPrimitive.long,
                kind = o["kind"]!!.jsonPrimitive.int,
                tags = o["tags"]?.jsonArray?.map { t -> t.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } } ?: emptyList(),
                content = o["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                sig = o["sig"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()

        fun fromJson(text: String): Event? = runCatching { fromJson(NostrJson.parseToJsonElement(text)) }.getOrNull()
    }
}

/** Filtro de suscripción (NIP-01). */
data class Filter(
    val kinds: List<Int>? = null,
    val authors: List<String>? = null,
    val ids: List<String>? = null,
    val tags: Map<String, List<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        ids?.let { put("ids", JsonArray(it.map(::JsonPrimitive))) }
        authors?.let { put("authors", JsonArray(it.map(::JsonPrimitive))) }
        kinds?.let { put("kinds", JsonArray(it.map(::JsonPrimitive))) }
        tags.forEach { (k, v) -> put("#$k", JsonArray(v.map(::JsonPrimitive))) }
        since?.let { put("since", it) }
        until?.let { put("until", it) }
        limit?.let { put("limit", it) }
    }
}

object Kinds {
    const val METADATA = 0
    const val DELETION = 5
    const val CONTACTS = 3
    const val ZAP_REQUEST = 9734
    const val ZAP_RECEIPT = 9735
    const val RELAY_LIST = 10002
    const val BOOKMARK_SET = 30003
    const val APP_DATA = 30078
    const val LIVE_EVENT = 30311
    const val LIVE_CHAT = 1311
    const val REACTION = 7
    const val NOSTR_CONNECT = 24133
    const val CALENDAR_TIME = 31923
    const val CALENDAR_DATE = 31922
}

/** Se queda con la versión más reciente de cada evento reemplazable/direccionable. */
fun List<Event>.latestByAddress(): List<Event> =
    groupBy { it.address }.values.map { group -> group.maxBy { it.createdAt } }

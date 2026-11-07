package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.NostrJson
import io.github.oluengo.tansu.nostr.latestByAddress
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Listas NIP-51: estándar (10000…) y conjuntos con nombre (30000…).
 * Al editar, nada pasa a ser público: los elementos privados van cifrados (NIP-44) y los públicos
 * solo se pueden quitar o hacer privados (ver docs/decisiones/0001).
 */
object Lists {
    /** Tipos que se buscan, con una clave de texto para la UI. */
    val KINDS = linkedMapOf(
        10000 to "mute", 10001 to "pins", 10003 to "bookmarks", 10004 to "communities", 10005 to "chats",
        10006 to "blocked_relays", 10007 to "search_relays", 10009 to "groups", 10015 to "interests",
        10030 to "emojis", 10050 to "dm_relays", 10063 to "blossom",
        30000 to "follow_set", 30002 to "relay_set", 30003 to "bookmark_set", 30004 to "curation",
        30005 to "video_set", 30015 to "interest_set", 30030 to "emoji_set",
    )

    /** Etiquetas que describen la lista y no son elementos. */
    private val META = setOf("d", "title", "name", "description", "image", "summary", "alt")

    fun latest(events: List<Event>): List<Event> =
        events.filter { it.kind in KINDS }.latestByAddress().sortedWith(compareBy<Event> { it.kind >= 30000 }.thenBy { it.kind }.thenByDescending { it.createdAt })

    fun title(e: Event): String? = e.tag("title") ?: e.tag("name") ?: e.tag("d")?.takeIf { e.kind >= 30000 && it.isNotBlank() }

    fun publicItems(e: Event): List<List<String>> = e.tags.filter { it.size > 1 && it[0] !in META }

    fun hasPrivate(e: Event): Boolean = e.content.isNotBlank()

    /** Etiquetas de la lista que no son elementos (d, title…): se conservan al guardar. */
    fun metaTags(e: Event): List<List<String>> = e.tags.filter { it.isNotEmpty() && (it.size < 2 || it[0] in META) }

    /** Texto (sin cifrar) de la parte privada: array JSON de etiquetas. */
    fun privateJson(items: List<List<String>>): String =
        kotlinx.serialization.json.JsonArray(items.map { t -> kotlinx.serialization.json.JsonArray(t.map { kotlinx.serialization.json.JsonPrimitive(it) }) }).toString()

    /** true si [public] no añade nada público respecto a la lista original (solo quita). */
    fun onlyRemovesPublic(original: Event, public: List<List<String>>): Boolean {
        val before = publicItems(original).toMutableList()
        return public.all { before.remove(it) }
    }

    /** Etiquetas que se suelen usar en cada tipo de lista, para el selector al añadir. */
    fun suggestedTags(kind: Int): List<String> = when (kind) {
        10000 -> listOf("p", "t", "word", "e")
        10001 -> listOf("e")
        10003, 30003 -> listOf("e", "a", "t", "r")
        10004 -> listOf("a")
        10005 -> listOf("e")
        10006, 10007, 10050, 30002 -> listOf("relay")
        10009 -> listOf("group", "r")
        10015, 30015 -> listOf("t", "a")
        10030, 30030 -> listOf("emoji", "a")
        10063 -> listOf("server")
        30000 -> listOf("p")
        30004 -> listOf("a", "e")
        30005 -> listOf("e")
        else -> listOf("p", "e", "a", "t", "r")
    }

    /** Contenido privado ya descifrado: un array JSON de etiquetas. null si no lo es. */
    fun parsePrivate(plain: String): List<List<String>>? = runCatching {
        (NostrJson.parseToJsonElement(plain) as JsonArray).map { t -> t.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.filter { it.size > 1 }
    }.getOrNull()
}

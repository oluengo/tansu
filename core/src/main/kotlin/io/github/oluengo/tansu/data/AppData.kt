package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.Kinds
import io.github.oluengo.tansu.nostr.latestByAddress
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Cómo está guardado el contenido de un evento. Tansu solo escribe [NIP44]. */
enum class Encoding { NIP44, NIP04, PLAIN, EMPTY }

fun detectEncoding(content: String): Encoding {
    val t = content.trim()
    if (t.isEmpty()) return Encoding.EMPTY
    if ("?iv=" in t) return Encoding.NIP04
    if (t.startsWith("{") || t.startsWith("[")) return Encoding.PLAIN
    val b = runCatching { Base64.getDecoder().decode(t) }.getOrNull()
    return if (b != null && b.size >= 99 && b[0] == 2.toByte()) Encoding.NIP44 else Encoding.PLAIN
}

/**
 * Una «carpeta-archivo» del explorador: un dato de app (NIP-78, kind 30078).
 * Si existen eventos `d/0`, `d/1`… además de `d`, es una copia por partes (formato de Usus:
 * índice en `d` y el contenido comprimido con gzip, en base64, repartido en las partes).
 */
data class Entry(
    /** Prefijo de la etiqueta d antes de la primera «/» (wavestr, usus…); "" si no tiene. */
    val app: String,
    val d: String,
    val main: Event,
    val parts: List<Event> = emptyList(),
) {
    val chunked: Boolean get() = parts.isNotEmpty()
    /** Nombre dentro de su carpeta: la etiqueta d sin el prefijo de la app. */
    val name: String get() = if (app.isEmpty()) d else d.removePrefix("$app/").ifEmpty { d }
    val encoding: Encoding get() = detectEncoding(main.content)
    val updatedAt: Long get() = (parts + main).maxOf { it.createdAt }
    val bytes: Int get() = (parts + main).sumOf { it.content.length }
    val events: List<Event> get() = parts + main
}

object AppData {
    /** Nombres visibles de las apps conocidas (el resto se muestra con su prefijo). */
    val KNOWN = mapOf(
        "wavestr" to "Wavestr",
        "usus" to "Usus",
        "mado" to "Mado",
        "kiroku" to "Kiroku",
        "bitacora" to "Bitácora",
    )

    private val PART = Regex("^(.+)/(\\d+)$")

    fun appOf(d: String): String = if ('/' in d) d.substringBefore('/') else ""

    /** Agrupa los eventos 30078 de un autor en entradas (las partes se unen a su índice). */
    fun group(events: List<Event>): List<Entry> {
        val latest = events.filter { it.kind == Kinds.APP_DATA && it.tag("d") != null }.latestByAddress()
        val byD = latest.associateBy { it.tag("d")!! }
        val partsOf = mutableMapOf<String, MutableList<Pair<Int, Event>>>()
        val mains = mutableListOf<Event>()
        for ((d, e) in byD) {
            val m = PART.matchEntire(d)
            if (m != null && byD.containsKey(m.groupValues[1])) {
                partsOf.getOrPut(m.groupValues[1]) { mutableListOf() } += m.groupValues[2].toInt() to e
            } else {
                mains += e
            }
        }
        return mains.map { e ->
            val d = e.tag("d")!!
            Entry(appOf(d), d, e, partsOf[d].orEmpty().sortedBy { it.first }.map { it.second })
        }.sortedWith(compareBy<Entry> { it.app.isEmpty() }.thenBy { it.app }.thenByDescending { it.updatedAt })
    }

    // ---------- Formato por partes (Usus) ----------

    /** Caracteres por parte: NIP-44 cifra hasta 64 KB; Usus usa 40 000. */
    const val PART_SIZE = 40_000

    fun gzipB64(text: String): String {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    fun gunzipB64(b64: String): String =
        GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(b64.trim()))).use { String(it.readBytes(), Charsets.UTF_8) }

    fun chunks(text: String, size: Int = PART_SIZE): List<String> =
        if (text.isEmpty()) listOf("") else (text.indices step size).map { text.substring(it, minOf(text.length, it + size)) }

    /** Etiquetas de una petición de borrado (NIP-09) para todos los eventos de una entrada. */
    fun deletionTags(events: List<Event>): List<List<String>> =
        events.flatMap { listOf(listOf("e", it.id), listOf("a", it.address)) } + listOf(listOf("k", Kinds.APP_DATA.toString()))
}

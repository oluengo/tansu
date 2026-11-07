package io.github.oluengo.tansu.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Reglas de la vista «Visual» del contenido (las mismas que la web, web/app.js).
 * Genéricas: sirven para el JSON de cualquier app, sin conocer su formato.
 */
object JsonView {
    /** Lista de textos cortos → etiquetas. */
    const val CHIP_MAX = 40
    /** Filas que se muestran de una tabla antes de «Ver todas». */
    const val TABLE_ROWS = 15

    fun isPrim(e: JsonElement) = e is JsonPrimitive || e is JsonNull

    /** `restSeconds`, `rest_seconds` → «Rest seconds». */
    fun human(key: String): String {
        val s = key.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
        return s.replaceFirstChar { it.uppercase() }
    }

    private val DATE_KEY = Regex("(^|_)(t|ts|at|date|time|created|updated|exported|last|since|until)($|_|[A-Z])|At$|Date$|Time$|^last|^created|^updated", RegexOption.IGNORE_CASE)
    private val ISO = Regex("^\\d{4}-\\d{2}-\\d{2}([T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:?\\d{2})?)?$")

    /** Milisegundos si el valor parece una fecha (texto ISO, o número de segundos/milisegundos con clave de fecha). */
    fun dateMillis(e: JsonElement, key: String?): Long? {
        val p = e as? JsonPrimitive ?: return null
        if (p.isString) {
            val s = p.content
            if (!ISO.matches(s)) return null
            return runCatching {
                when {
                    s.length == 10 -> LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    s.endsWith("Z") || s.contains('+') || s.lastIndexOf('-') > 9 -> OffsetDateTime.parse(s.replace(' ', 'T')).toInstant().toEpochMilli()
                    else -> Instant.parse(s.replace(' ', 'T') + "Z").toEpochMilli()
                }
            }.getOrNull()
        }
        val n = p.longOrNull ?: return null
        if (key == null || !DATE_KEY.containsMatchIn(key)) return null
        return when (n) {
            in 1_000_000_000_000L until 10_000_000_000_000L -> n
            in 1_000_000_000L until 10_000_000_000L -> n * 1000
            else -> null
        }
    }

    fun bool(e: JsonElement): Boolean? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    fun isUrl(s: String) = Regex("^https?://\\S+$").matches(s)

    fun titleOf(o: JsonObject): String? =
        listOf("name", "title", "label", "d").firstNotNullOfOrNull { k -> (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content }

    fun childrenKey(o: JsonObject): String? = listOf("children", "items", "folders", "entries", "files").firstOrNull { o[it] is JsonArray }

    /** Carpetas con hijos (como los favoritos de Wavestr) → árbol. */
    fun isTree(a: JsonArray): Boolean =
        a.isNotEmpty() && a.all { it is JsonObject && titleOf(it) != null } && a.any { childrenKey(it as JsonObject) != null }

    fun isChips(a: JsonArray): Boolean =
        a.size <= CHIP_MAX && a.all { it is JsonPrimitive && it.isString && it.content.length <= CHIP_MAX }

    /** Objetos con valores simples (o listas de simples) → tabla. */
    fun isTable(a: JsonArray): Boolean =
        a.isNotEmpty() && a.all { o -> o is JsonObject && o.values.all { v -> isPrim(v) || (v is JsonArray && v.all(::isPrim)) } }

    /** Columnas de una tabla, en orden de aparición. */
    fun columns(a: JsonArray): List<String> = a.flatMap { (it as JsonObject).keys }.distinct()

    /** Campos que van en la ficha (clave → valor) frente a los que van en secciones plegables. */
    fun isFlat(e: JsonElement): Boolean = isPrim(e) || (e is JsonArray && e.size <= 12 && isChips(e))
}

package io.github.oluengo.tansu.data

import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Señales de fiabilidad de un relay. null = no lo sabemos.
 * [monOnline]/[monTotal]: monitores NIP-66 que lo ven en línea / que lo vigilan.
 * [histOk]/[histTotal]: cargas de Tansu en las que respondió / cargas.
 * [lost]/[kept]: datos tuyos que perdió sin que los borraras / datos que ha tenido.
 * [follows]: cuentas que sigues que lo tienen en su lista NIP-65. [ms]: tiempo de respuesta.
 */
data class RelaySignals(
    val monOnline: Int? = null, val monTotal: Int? = null, val monLast: Long? = null,
    val histOk: Int? = null, val histTotal: Int? = null,
    val lost: Int? = null, val kept: Int? = null,
    val follows: Int? = null, val ms: Long? = null,
)

/** Valoración: [score] 0..1 → [stars] 1..5. Señales sin datos = null (su peso se reparte). */
data class RelayRating(
    val score: Double, val stars: Int, val capped: Boolean, val partial: Boolean,
    val avail: Double?, val keep: Double?, val follows: Double?, val speed: Double?,
) {
    companion object {
        const val W_AVAIL = 40; const val W_KEEP = 30; const val W_FOLLOWS = 20; const val W_SPEED = 10

        fun of(s: RelaySignals): RelayRating? {
            val mon = if (s.monTotal != null && s.monTotal > 0) (s.monOnline ?: 0).toDouble() / s.monTotal else null
            val hist = if (s.histTotal != null && s.histTotal >= 3) (s.histOk ?: 0).toDouble() / s.histTotal else null
            val avail = when { mon == null -> hist; hist == null -> mon; else -> 0.4 * mon + 0.6 * hist }
            val keep = if (s.lost == null || s.kept == null || s.kept == 0) null else (1 - s.lost.toDouble() / s.kept * 2).coerceAtLeast(0.0)
            val fol = s.follows?.let { (log10(1.0 + it) / log10(51.0)).coerceAtMost(1.0) }
            val speed = s.ms?.let { ((1500.0 - it) / 1200.0).coerceIn(0.0, 1.0) }
            val parts = listOf(avail to W_AVAIL, keep to W_KEEP, fol to W_FOLLOWS, speed to W_SPEED).filter { it.first != null }
            if (parts.size < 2) return null
            val score = parts.sumOf { it.first!! * it.second } / parts.sumOf { it.second }
            val raw = (score * 5).roundToInt().coerceIn(1, 5)
            // Para copias de seguridad, perder datos pesa más: como mucho 3 estrellas
            val capped = (s.lost ?: 0) > 0 && raw > 3
            return RelayRating(score, if (capped) 3 else raw, capped, parts.size < 4, avail, keep, fol, speed)
        }
    }
}

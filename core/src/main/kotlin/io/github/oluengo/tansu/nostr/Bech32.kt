package io.github.oluengo.tansu.nostr

/** Bech32 (BIP-173) para npub/nsec/note (NIP-19). */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val b = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if (((b ushr i) and 1) == 1) chk = chk xor GEN[i]
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray {
        val r = IntArray(hrp.length * 2 + 1)
        hrp.forEachIndexed { i, c -> r[i] = c.code ushr 5; r[i + hrp.length + 1] = c.code and 31 }
        return r
    }

    fun encode(hrp: String, data: ByteArray): String {
        val values = requireNotNull(convertBits(data.map { it.toInt() and 0xff }, 8, 5, true))
        val combined = hrpExpand(hrp) + values.toIntArray() + IntArray(6)
        val mod = polymod(combined) xor 1
        val checksum = (0 until 6).map { (mod ushr (5 * (5 - it))) and 31 }
        return hrp + "1" + (values + checksum).joinToString("") { CHARSET[it].toString() }
    }

    /** Devuelve (hrp, bytes) o null si no es válido. */
    fun decode(input: String): Pair<String, ByteArray>? {
        val s = input.trim().lowercase()
        val pos = s.lastIndexOf('1')
        if (pos < 1 || pos + 7 > s.length) return null
        val hrp = s.substring(0, pos)
        val data = s.substring(pos + 1).map { CHARSET.indexOf(it) }
        if (data.any { it < 0 }) return null
        if (polymod(hrpExpand(hrp) + data.toIntArray()) != 1) return null
        val bytes = convertBits(data.dropLast(6), 5, 8, false) ?: return null
        return hrp to ByteArray(bytes.size) { bytes[it].toByte() }
    }

    private fun convertBits(data: List<Int>, from: Int, to: Int, pad: Boolean): List<Int>? {
        var acc = 0
        var bits = 0
        val maxv = (1 shl to) - 1
        val out = ArrayList<Int>()
        for (v in data) {
            if (v < 0 || (v ushr from) != 0) return null
            acc = (acc shl from) or v
            bits += from
            while (bits >= to) {
                bits -= to
                out.add((acc ushr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) out.add((acc shl (to - bits)) and maxv)
        } else if (bits >= from || ((acc shl (to - bits)) and maxv) != 0) {
            return null
        }
        return out
    }
}

object Nip19 {
    fun npubToHex(npub: String): String? {
        val (hrp, bytes) = Bech32.decode(npub) ?: return null
        if (hrp != "npub" || bytes.size != 32) return null
        return bytes.toHex()
    }

    fun hexToNpub(hex: String): String = Bech32.encode("npub", hex.hexToBytes())

    /** Acepta npub o hex de 64 caracteres. */
    fun normalizePubkey(input: String): String? {
        val t = input.trim().removePrefix("nostr:")
        if (t.startsWith("npub1", ignoreCase = true)) return npubToHex(t)
        if (t.length == 64 && t.all { it in "0123456789abcdefABCDEF" }) return t.lowercase()
        return null
    }

    fun shortNpub(hex: String): String = hexToNpub(hex).let { it.take(10) + "…" + it.takeLast(4) }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

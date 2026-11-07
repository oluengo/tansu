package io.github.oluengo.tansu.nostr

import io.github.oluengo.tansu.crypto.Secp256k1

/** Id y firma de eventos (NIP-01). Para claves propias de la app (NWC, zaps anónimos); el usuario firma con Amber. */
object EventSigning {
    /** Escapado exacto de NIP-01 (como JSON.stringify). */
    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }

    fun serialize(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String =
        buildString {
            append("[0,").append(quote(pubkey)).append(',').append(createdAt).append(',').append(kind).append(",[")
            tags.forEachIndexed { i, t -> if (i > 0) append(','); append('['); t.forEachIndexed { j, v -> if (j > 0) append(','); append(quote(v)) }; append(']') }
            append("],").append(quote(content)).append(']')
        }

    fun computeId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String =
        Secp256k1.sha256(serialize(pubkey, createdAt, kind, tags, content).toByteArray(Charsets.UTF_8)).toHex()

    /** Evento sin firmar (con id calculado) para un autor dado: lo que se manda a Amber. */
    fun unsigned(pubkey: String, kind: Int, tags: List<List<String>>, content: String, createdAt: Long = System.currentTimeMillis() / 1000) =
        Event(computeId(pubkey, createdAt, kind, tags, content), pubkey, createdAt, kind, tags, content, "")

    fun sign(kind: Int, tags: List<List<String>>, content: String, sk: ByteArray, createdAt: Long = System.currentTimeMillis() / 1000): Event {
        val pub = Secp256k1.pubkey(sk).toHex()
        val e = unsigned(pub, kind, tags, content, createdAt)
        return e.copy(sig = Secp256k1.sign(e.id.hexToBytes(), sk).toHex())
    }

    fun verify(e: Event): Boolean =
        e.id == computeId(e.pubkey, e.createdAt, e.kind, e.tags, e.content) &&
            Secp256k1.verify(e.id.hexToBytes(), e.pubkey.hexToBytes(), e.sig.hexToBytes())
}

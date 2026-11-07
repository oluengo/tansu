package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.EventSigning
import io.github.oluengo.tansu.nostr.Filter
import io.github.oluengo.tansu.nostr.Kinds
import io.github.oluengo.tansu.nostr.NostrJson
import io.github.oluengo.tansu.nostr.RelayPool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Firma y cifrado de una cuenta (Amber, bunker…). null = el firmante no respondió o lo rechazó. */
interface AccountCrypto {
    val pubkey: String
    suspend fun sign(unsigned: Event): Event?
    suspend fun nip44Encrypt(plain: String, peer: String): String?
    suspend fun nip44Decrypt(cipher: String, peer: String): String?
    suspend fun nip04Decrypt(cipher: String, peer: String): String?
}

class VaultException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NEEDS_SIGNER, READ_ONLY, OFFLINE, UNREADABLE, TOO_BIG, NOT_ENCRYPTED }
}

/** Lo que hay en Nostr para una cuenta. */
data class Snapshot(val pubkey: String, val relays: List<String>, val entries: List<Entry>, val lists: List<Event>, val loadedAt: Long)

/** Nombre e imagen públicos de una cuenta (kind 0), solo para reconocerla en la lista. */
data class Profile(val name: String?, val picture: String?)

/** Contenido de una entrada ya descifrado. [manifest]: índice de una copia por partes. */
data class Opened(val text: String, val manifest: JsonObject? = null) {
    val isJson: Boolean get() = runCatching { NostrJson.parseToJsonElement(text); true }.getOrDefault(false)
}

/**
 * Lee y escribe los datos de apps (NIP-78) de una cuenta. Regla de Tansu (ver docs/decisiones/0001):
 * **todo lo que publica va cifrado con NIP-44 para la propia cuenta**; nunca publica nada legible.
 */
class Vault(private val pool: RelayPool) {

    private fun now() = System.currentTimeMillis() / 1000

    /** Relays donde vive la cuenta: su lista NIP-65 (todas) más los de Tansu. */
    suspend fun relaysFor(pubkey: String): List<String> {
        val ev = pool.query(RelayPool.INDEXERS + RelayPool.DEFAULT, listOf(Filter(kinds = listOf(Kinds.RELAY_LIST), authors = listOf(pubkey), limit = 1)))
            .filter { it.pubkey == pubkey }.maxByOrNull { it.createdAt }
        val own = ev?.tagsNamed("r")?.mapNotNull { t -> t.getOrNull(1)?.let(RelayPool::normalize) }.orEmpty()
        return (own.take(6) + RelayPool.DEFAULT).distinct()
    }

    suspend fun load(pubkey: String): Snapshot {
        val relays = relaysFor(pubkey)
        val events = pool.query(
            relays,
            listOf(
                Filter(kinds = listOf(Kinds.APP_DATA), authors = listOf(pubkey), limit = 500),
                Filter(kinds = Lists.KINDS.keys.toList(), authors = listOf(pubkey), limit = 200),
            ),
            timeoutMs = 8_000,
        ).filter { it.pubkey == pubkey && EventSigning.verify(it) }
        return Snapshot(pubkey, relays, AppData.group(events), Lists.latest(events), System.currentTimeMillis())
    }

    suspend fun profiles(pubkeys: List<String>): Map<String, Profile> {
        if (pubkeys.isEmpty()) return emptyMap()
        return pool.query(RelayPool.INDEXERS + RelayPool.DEFAULT, listOf(Filter(kinds = listOf(Kinds.METADATA), authors = pubkeys)))
            .filter { it.pubkey in pubkeys }
            .groupBy { it.pubkey }
            .mapValues { (_, list) ->
                val o = runCatching { NostrJson.parseToJsonElement(list.maxBy { it.createdAt }.content) as JsonObject }.getOrNull()
                fun s(k: String) = runCatching { (o?.get(k) as? JsonPrimitive)?.content }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                Profile(s("display_name") ?: s("name"), s("picture")?.takeIf { it.startsWith("https://") })
            }
    }

    // ---------- Leer ----------

    suspend fun open(entry: Entry, crypto: AccountCrypto): Opened {
        val first = decode(entry.main.content, crypto)
        if (!entry.chunked) return Opened(first)
        val manifest = runCatching { NostrJson.parseToJsonElement(first) as JsonObject }.getOrNull()
            ?: throw VaultException(VaultException.Reason.UNREADABLE)
        val n = runCatching { manifest["parts"]!!.jsonPrimitive.int }.getOrNull() ?: entry.parts.size
        if (entry.parts.size < n) throw VaultException(VaultException.Reason.UNREADABLE)
        val z = StringBuilder()
        entry.parts.take(n).forEach { z.append(decode(it.content, crypto)) }
        val text = runCatching { AppData.gunzipB64(z.toString()) }.getOrElse { throw VaultException(VaultException.Reason.UNREADABLE, it) }
        return Opened(text, manifest)
    }

    suspend fun decode(content: String, crypto: AccountCrypto): String = when (detectEncoding(content)) {
        Encoding.NIP44 -> crypto.nip44Decrypt(content, crypto.pubkey) ?: throw VaultException(VaultException.Reason.NEEDS_SIGNER)
        Encoding.NIP04 -> crypto.nip04Decrypt(content, crypto.pubkey) ?: throw VaultException(VaultException.Reason.NEEDS_SIGNER)
        Encoding.PLAIN, Encoding.EMPTY -> content
    }

    // ---------- Escribir (siempre cifrado) ----------

    /** Plan de escritura: pares (d, texto sin cifrar) en orden de publicación, y las partes que sobran. */
    fun plan(d: String, text: String, manifest: JsonObject?, oldParts: Int): Pair<List<Pair<String, String>>, List<String>> {
        if (manifest == null) return listOf(d to text) to (0 until oldParts).map { "$d/$it" }
        val z = AppData.gzipB64(text)
        val parts = AppData.chunks(z)
        val m = buildJsonObject {
            manifest.forEach { (k, v) -> if (k !in setOf("parts", "len", "t")) put(k, v) }
            put("parts", parts.size); put("len", z.length); put("t", System.currentTimeMillis())
        }
        val writes = parts.mapIndexed { i, p -> "$d/$i" to p } + (d to m.toString())
        return writes to (parts.size until oldParts).map { "$d/$it" }
    }

    /**
     * Guarda [text] en la entrada [d] de la cuenta de [crypto] (cifrado con NIP-44 para ella).
     * [manifest] ≠ null: copia por partes. Devuelve los eventos publicados.
     */
    suspend fun write(
        crypto: AccountCrypto, relays: List<String>, d: String, text: String, manifest: JsonObject?, oldParts: Int = 0,
        onStep: (Int, Int) -> Unit = { _, _ -> },
    ): List<Event> {
        val (writes, extra) = plan(d, text, manifest, oldParts)
        val me = crypto.pubkey
        val at = now()
        val out = mutableListOf<Event>()
        writes.forEachIndexed { i, (dd, plain) ->
            onStep(i + 1, writes.size)
            if (plain.toByteArray().size > 65_000) throw VaultException(VaultException.Reason.TOO_BIG)
            val enc = crypto.nip44Encrypt(plain, me) ?: throw VaultException(VaultException.Reason.NEEDS_SIGNER)
            if (detectEncoding(enc) != Encoding.NIP44) throw VaultException(VaultException.Reason.NOT_ENCRYPTED)
            out += publish(crypto, relays, EventSigning.unsigned(me, Kinds.APP_DATA, listOf(listOf("d", dd)), enc, at))
        }
        if (extra.isNotEmpty()) {
            val tags = extra.map { listOf("a", "${Kinds.APP_DATA}:$me:$it") } + listOf(listOf("k", Kinds.APP_DATA.toString()))
            runCatching { publish(crypto, relays, EventSigning.unsigned(me, Kinds.DELETION, tags, "", at)) }
        }
        return out
    }

    /** Petición de borrado (NIP-09) de todos los eventos de la entrada. */
    suspend fun delete(crypto: AccountCrypto, relays: List<String>, entry: Entry): Event =
        publish(crypto, relays, EventSigning.unsigned(crypto.pubkey, Kinds.DELETION, AppData.deletionTags(entry.events), ""))

    /**
     * Vuelve a publicar eventos ya cifrados (papelera) con fecha nueva: los relays rechazan la versión
     * antigua tras un borrado, pero el contenido sigue siendo válido porque está cifrado para la misma cuenta.
     */
    suspend fun republish(crypto: AccountCrypto, relays: List<String>, events: List<Event>): List<Event> {
        val at = now()
        return events.map { e ->
            if (detectEncoding(e.content) != Encoding.NIP44) throw VaultException(VaultException.Reason.NOT_ENCRYPTED)
            publish(crypto, relays, EventSigning.unsigned(crypto.pubkey, e.kind, e.tags, e.content, at))
        }
    }

    private suspend fun publish(crypto: AccountCrypto, relays: List<String>, unsigned: Event): Event {
        val signed = crypto.sign(unsigned) ?: throw VaultException(VaultException.Reason.NEEDS_SIGNER)
        if (!EventSigning.verify(signed) || signed.pubkey != crypto.pubkey) throw VaultException(VaultException.Reason.NEEDS_SIGNER)
        if (pool.publish(relays, signed) == 0) throw VaultException(VaultException.Reason.OFFLINE)
        return signed
    }

    companion object {
        /** Texto legible de un índice de copia por partes, para mostrarlo. */
        fun manifestInfo(m: JsonObject): Map<String, String> =
            m.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }.toMap()
    }
}

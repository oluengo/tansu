package io.github.oluengo.tansu.nostr

import io.github.oluengo.tansu.crypto.Nip04
import io.github.oluengo.tansu.crypto.Nip44
import io.github.oluengo.tansu.crypto.Secp256k1
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URLDecoder
import java.util.UUID

/** Datos de un enlace `bunker://<firmante>?relay=…&secret=…` (NIP-46, p. ej. nsecBunker). */
data class BunkerUri(val signerPubkey: String, val relays: List<String>, val secret: String?) {
    fun toUri(): String = buildString {
        append("bunker://").append(signerPubkey)
        val q = relays.map { "relay=" + java.net.URLEncoder.encode(it, "UTF-8") } + listOfNotNull(secret?.let { "secret=" + java.net.URLEncoder.encode(it, "UTF-8") })
        if (q.isNotEmpty()) append('?').append(q.joinToString("&"))
    }

    companion object {
        fun parse(input: String): BunkerUri? = runCatching {
            val t = input.trim()
            if (!t.startsWith("bunker://", true)) return null
            val rest = t.substring("bunker://".length)
            val pk = Nip19.normalizePubkey(rest.substringBefore('?').trimEnd('/')) ?: return null
            val params = rest.substringAfter('?', "").split('&').filter { '=' in it }
                .map { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            val relays = params.filter { it.first == "relay" }.mapNotNull { RelayPool.normalize(it.second) }.distinct()
            if (relays.isEmpty()) return null
            BunkerUri(pk, relays, params.firstOrNull { it.first == "secret" }?.second?.takeIf { it.isNotBlank() })
        }.getOrNull()
    }
}

/**
 * Cliente NIP-46: habla con un firmante remoto mediante eventos 24133 cifrados (NIP-44; se aceptan
 * respuestas NIP-04 de firmantes antiguos). La app tiene una clave propia de cliente; la del usuario
 * nunca sale del firmante. [onAuthUrl] se llama si el firmante pide aprobar en su web.
 */
class Nip46Client(
    private val pool: RelayPool,
    val bunker: BunkerUri,
    private val clientSecret: ByteArray,
    private val onAuthUrl: (String) -> Unit = {},
) {
    class SignerError(message: String) : Exception(message)

    val clientPubkey: String = Secp256k1.pubkey(clientSecret).toHex()
    private val ck = Nip44.conversationKey(clientSecret, bunker.signerPubkey.hexToBytes())
    private val mutex = Mutex()

    /** Primer contacto: «connect» (con el secreto del enlace) y luego la clave del usuario. */
    suspend fun connect(): String {
        val r = call("connect", listOfNotNull(bunker.signerPubkey, bunker.secret ?: ""), timeoutMs = 120_000)
        if (r != "ack" && r != bunker.secret) throw SignerError("connect: $r")
        return userPubkey()
    }

    suspend fun userPubkey(): String =
        Nip19.normalizePubkey(call("get_public_key", emptyList())) ?: throw SignerError("clave no válida")

    suspend fun sign(unsigned: Event): Event {
        val json = buildJsonObject {
            put("kind", unsigned.kind); put("content", unsigned.content); put("created_at", unsigned.createdAt)
            put("pubkey", unsigned.pubkey)
            put("tags", unsigned.toJson()["tags"]!!)
        }.toString()
        val signed = Event.fromJson(call("sign_event", listOf(json))) ?: throw SignerError("respuesta no válida")
        if (!EventSigning.verify(signed) || signed.kind != unsigned.kind || signed.content != unsigned.content) throw SignerError("firma no válida")
        return signed
    }

    suspend fun nip44Encrypt(plain: String, peer: String): String = call("nip44_encrypt", listOf(peer, plain))
    suspend fun nip44Decrypt(cipher: String, peer: String): String = call("nip44_decrypt", listOf(peer, cipher))
    /** Solo para leer contenido antiguo (listas NIP-51 cifradas con NIP-04). Tansu nunca cifra con NIP-04. */
    suspend fun nip04Decrypt(cipher: String, peer: String): String = call("nip04_decrypt", listOf(peer, cipher))

    private suspend fun call(method: String, params: List<String>, timeoutMs: Long = 60_000): String = mutex.withLock {
        val id = UUID.randomUUID().toString().take(12)
        val body = buildJsonObject {
            put("id", id); put("method", method)
            putJsonArray("params") { params.forEach { add(JsonPrimitive(it)) } }
        }.toString()
        val req = EventSigning.sign(Kinds.NOSTR_CONNECT, listOf(listOf("p", bunker.signerPubkey)), Nip44.encrypt(body, ck), clientSecret)
        val filter = Filter(
            kinds = listOf(Kinds.NOSTR_CONNECT), authors = listOf(bunker.signerPubkey),
            tags = mapOf("p" to listOf(clientPubkey)), since = req.createdAt - 10,
        )
        var answer: Pair<String?, String?>? = null
        pool.roundTrip(bunker.relays, req, filter, timeoutMs) { ev ->
            val o = decode(ev.content) ?: return@roundTrip false
            if (o.s("id") != id) return@roundTrip false
            val result = o.s("result"); val error = o.s("error")
            if (result == "auth_url" && error != null) { onAuthUrl(error); return@roundTrip false }
            answer = result to error
            true
        } ?: throw SignerError("sin respuesta del firmante")
        val (result, error) = answer!!
        if (!error.isNullOrBlank()) throw SignerError(error)
        result ?: throw SignerError("respuesta vacía")
    }

    private fun decode(content: String): JsonObject? = runCatching {
        val plain = if ("?iv=" in content) Nip04.decrypt(content, clientSecret, bunker.signerPubkey.hexToBytes()) else Nip44.decrypt(content, ck)
        NostrJson.parseToJsonElement(plain) as JsonObject
    }.getOrNull()

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}

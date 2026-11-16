package io.github.oluengo.tansu.nostr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Una tarifa de un relay (NIP-11 «fees»): [period] en segundos para suscripciones. */
data class RelayFee(val kind: String, val amount: Long, val unit: String, val period: Long? = null)

/**
 * Ficha de un relay (NIP-11). La publica el propio relay y es voluntaria: cualquier campo puede faltar.
 * null en un campo = el relay no lo dice.
 */
data class RelayInfo(
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val pubkey: String? = null,
    val contact: String? = null,
    val supportedNips: List<Int>? = null,
    val software: String? = null,
    val version: String? = null,
    val termsOfService: String? = null,
    val maxMessageLength: Int? = null,
    val maxContentLength: Int? = null,
    val minPowDifficulty: Int? = null,
    val authRequired: Boolean? = null,
    val paymentRequired: Boolean? = null,
    val restrictedWrites: Boolean? = null,
    val createdAtLowerLimit: Long? = null,
    val createdAtUpperLimit: Long? = null,
    val fees: List<RelayFee> = emptyList(),
    val paymentsUrl: String? = null,
) {
    /** null = no lo sabemos (no publica sus NIPs). */
    val supportsDeletion: Boolean? get() = supportedNips?.contains(9)
    val paid: Boolean get() = paymentRequired == true || fees.isNotEmpty()

    companion object {
        fun parse(json: String): RelayInfo? = runCatching { from(NostrJson.parseToJsonElement(json) as JsonObject) }.getOrNull()

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }
        private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
        private fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.longOrNull
        private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull
        private fun httpsOnly(s: String?) = s?.takeIf { it.startsWith("https://") }

        fun from(o: JsonObject): RelayInfo {
            val lim = o["limitation"] as? JsonObject
            val fees = (o["fees"] as? JsonObject)?.flatMap { (kind, list) ->
                (list as? JsonArray).orEmpty().mapNotNull { f ->
                    val fo = f as? JsonObject ?: return@mapNotNull null
                    val amount = fo.long("amount") ?: return@mapNotNull null
                    RelayFee(kind, amount, fo.str("unit") ?: "msats", fo.long("period"))
                }
            }.orEmpty()
            return RelayInfo(
                name = o.str("name"), description = o.str("description"), icon = httpsOnly(o.str("icon")),
                pubkey = o.str("pubkey")?.takeIf { Regex("^[0-9a-f]{64}$").matches(it) },
                contact = o.str("contact"),
                supportedNips = (o["supported_nips"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull },
                software = o.str("software"), version = o.str("version"), termsOfService = httpsOnly(o.str("terms_of_service")),
                maxMessageLength = lim?.int("max_message_length"), maxContentLength = lim?.int("max_content_length"),
                minPowDifficulty = lim?.int("min_pow_difficulty"),
                authRequired = lim?.bool("auth_required"), paymentRequired = lim?.bool("payment_required"),
                restrictedWrites = lim?.bool("restricted_writes"),
                createdAtLowerLimit = lim?.long("created_at_lower_limit"), createdAtUpperLimit = lim?.long("created_at_upper_limit"),
                fees = fees, paymentsUrl = httpsOnly(o.str("payments_url")),
            )
        }

        /** wss://relay.ejemplo.com → https://relay.ejemplo.com */
        fun httpUrl(relay: String) = relay.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")
    }
}

/** Lee las fichas NIP-11 de los relays (petición HTTP con Accept: application/nostr+json). */
class RelayInfoClient(http: OkHttpClient) {
    private val client = http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()

    suspend fun fetch(relay: String): RelayInfo? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(RelayInfo.httpUrl(relay)).header("Accept", "application/nostr+json").build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()?.takeIf { it.length < 200_000 }?.let(RelayInfo::parse)
            }
        }.getOrNull()
    }

    suspend fun fetchAll(relays: Collection<String>): Map<String, RelayInfo?> = coroutineScope {
        relays.distinct().map { r -> async { r to fetch(r) } }.awaitAll().toMap()
    }
}


package io.github.oluengo.tansu.nostr

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Collections
import java.util.UUID

/**
 * Cliente de relays mínimo (NIP-01): abre un WebSocket por consulta, recoge eventos hasta EOSE
 * o hasta el tiempo límite, y cierra. También publica (EVENT → OK).
 */
class RelayPool(private val http: OkHttpClient) {

    suspend fun query(
        relays: Collection<String>,
        filters: List<Filter>,
        timeoutMs: Long = 6_000,
    ): List<Event> = coroutineScope {
        relays.distinct().map { url -> async(Dispatchers.IO) { queryOne(url, filters, timeoutMs) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.id }
    }

    /** Lo que respondió un relay: [reached] = se pudo conectar. */
    data class RelayResult(val url: String, val reached: Boolean, val events: List<Event>)

    /** Como [query], pero relay a relay: para saber en cuáles está cada evento. */
    suspend fun queryEach(
        relays: Collection<String>,
        filters: List<Filter>,
        timeoutMs: Long = 6_000,
    ): List<RelayResult> = coroutineScope {
        relays.distinct().map { url -> async(Dispatchers.IO) { queryOneResult(url, filters, timeoutMs) } }.awaitAll()
    }

    private suspend fun queryOne(url: String, filters: List<Filter>, timeoutMs: Long): List<Event> =
        queryOneResult(url, filters, timeoutMs).events

    private suspend fun queryOneResult(url: String, filters: List<Filter>, timeoutMs: Long): RelayResult =
        withContext(Dispatchers.IO) {
            val opened = java.util.concurrent.atomic.AtomicBoolean(false)
            val events = Collections.synchronizedList(mutableListOf<Event>())
            val done = CompletableDeferred<Unit>()
            val subId = "rs" + UUID.randomUUID().toString().take(8)
            val req = buildJsonArray {
                add("REQ"); add(subId)
                filters.forEach { add(it.toJson()) }
            }.toString()

            val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.set(true)
                    webSocket.send(req)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val arr = runCatching { NostrJson.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return
                    val type = (arr.getOrNull(0) as? JsonPrimitive)?.contentOrNull
                    val sid = (arr.getOrNull(1) as? JsonPrimitive)?.contentOrNull
                    if (sid != subId) return
                    when (type) {
                        "EVENT" -> arr.getOrNull(2)?.let(Event::fromJson)?.let(events::add)
                        "EOSE", "CLOSED" -> done.complete(Unit)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    done.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    done.complete(Unit)
                }
            })

            try {
                withTimeoutOrNull(timeoutMs) { done.await() }
            } finally {
                runCatching { ws.send(buildJsonArray { add("CLOSE"); add(subId) }.toString()) }
                runCatching { ws.close(1000, null) }
            }
            RelayResult(url, opened.get(), events.toList())
        }

    /**
     * Petición y respuesta (NWC): se suscribe a [response] y publica [request] en cada relay;
     * devuelve el primer evento que llegue (y que acepte [accept]) o null si se agota el tiempo.
     */
    suspend fun roundTrip(
        relays: Collection<String>, request: Event, response: Filter, timeoutMs: Long = 45_000,
        accept: (Event) -> Boolean = { true },
    ): Event? =
        withContext(Dispatchers.IO) {
            val result = CompletableDeferred<Event?>()
            val subId = "rt" + UUID.randomUUID().toString().take(8)
            val req = buildJsonArray { add("REQ"); add(subId); add(response.toJson()) }.toString()
            val pub = buildJsonArray { add("EVENT"); add(request.toJson()) }.toString()
            val sockets = relays.distinct().map { url ->
                http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(req)
                        webSocket.send(pub)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val arr = runCatching { NostrJson.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return
                        if ((arr.getOrNull(0) as? JsonPrimitive)?.contentOrNull == "EVENT" &&
                            (arr.getOrNull(1) as? JsonPrimitive)?.contentOrNull == subId
                        ) arr.getOrNull(2)?.let(Event::fromJson)?.takeIf { runCatching { accept(it) }.getOrDefault(false) }?.let { result.complete(it) }
                    }
                })
            }
            try {
                withTimeoutOrNull(timeoutMs) { result.await() }
            } finally {
                sockets.forEach { ws -> runCatching { ws.send(buildJsonArray { add("CLOSE"); add(subId) }.toString()); ws.close(1000, null) } }
            }
        }

    /** Publica en cada relay y devuelve cuántos lo aceptaron (mensaje OK con true). */
    suspend fun publish(relays: Collection<String>, event: Event, timeoutMs: Long = 8_000): Int = coroutineScope {
        relays.distinct().map { url -> async(Dispatchers.IO) { publishOne(url, event, timeoutMs) } }.awaitAll().count { it }
    }

    private suspend fun publishOne(url: String, event: Event, timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        val ok = CompletableDeferred<Boolean>()
        val msg = buildJsonArray { add("EVENT"); add(event.toJson()) }.toString()
        val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(msg) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val arr = runCatching { NostrJson.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return
                if ((arr.getOrNull(0) as? JsonPrimitive)?.contentOrNull == "OK" && (arr.getOrNull(1) as? JsonPrimitive)?.contentOrNull == event.id) {
                    ok.complete((arr.getOrNull(2) as? JsonPrimitive)?.contentOrNull == "true")
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { ok.complete(false) }
        })
        try { withTimeoutOrNull(timeoutMs) { ok.await() } ?: false } finally { runCatching { ws.close(1000, null) } }
    }

    companion object {
        /** Relays por defecto: generales + índice de perfiles/listas. */
        val BUILTIN = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
        )

        /** Relays de Tansu: los de serie o los que haya puesto el usuario en Ajustes. */
        @Volatile
        var DEFAULT: List<String> = BUILTIN

        /** Normaliza lo que escribe el usuario: añade wss:// si falta; null si no es un relay válido. */
        fun normalize(input: String): String? {
            val t = input.trim().trimEnd('/').lowercase()
            if (t.isEmpty() || t.any { it.isWhitespace() }) return null
            val u = if ("://" in t) t else "wss://$t"
            val ok = (u.startsWith("wss://") || u.startsWith("ws://")) && u.substringAfter("://").let { h -> h.contains('.') || h.startsWith("localhost") }
            return u.takeIf { ok }
        }
        val INDEXERS = listOf(
            "wss://purplepag.es",
        )
    }
}

package io.github.oluengo.tansu.data

import android.content.Context
import io.github.oluengo.tansu.nostr.NostrJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Cómo se entró con cada cuenta. Solo lectura: se ve lo público y nada cifrado. */
enum class Method { AMBER, BUNKER, READ_ONLY }

data class Account(
    val pubkey: String,
    val method: Method,
    /** Paquete del firmante NIP-55 (Amber u otro compatible). */
    val signerPackage: String? = null,
    /** Nombre que le pone el usuario (p. ej. «Copias»), opcional. */
    val label: String? = null,
) {
    val canSign: Boolean get() = method != Method.READ_ONLY
}

data class AccountsState(val list: List<Account> = emptyList(), val active: String? = null) {
    val current: Account? get() = list.firstOrNull { it.pubkey == active } ?: list.firstOrNull()
}

/** Varias cuentas de Nostr a la vez. Nunca se guarda ninguna nsec: solo la pubkey y cómo firmar. */
class AccountsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    val current: Account? get() = _state.value.current
    fun find(pubkey: String): Account? = _state.value.list.firstOrNull { it.pubkey == pubkey }

    /** Añade (o actualiza si ya estaba) y la deja como activa. */
    fun add(a: Account) {
        val list = _state.value.list.filterNot { it.pubkey == a.pubkey } + a.copy(label = a.label ?: find(a.pubkey)?.label)
        save(AccountsState(list, a.pubkey))
    }

    fun setActive(pubkey: String) = save(_state.value.copy(active = pubkey))

    fun rename(pubkey: String, label: String?) =
        save(_state.value.copy(list = _state.value.list.map { if (it.pubkey == pubkey) it.copy(label = label?.trim()?.ifEmpty { null }) else it }))

    fun remove(pubkey: String) {
        val list = _state.value.list.filterNot { it.pubkey == pubkey }
        save(AccountsState(list, _state.value.active.takeIf { it != pubkey } ?: list.firstOrNull()?.pubkey))
    }

    private fun save(s: AccountsState) {
        _state.value = s
        val json = buildJsonArray {
            s.list.forEach { a ->
                add(buildJsonObject {
                    put("pubkey", a.pubkey); put("method", a.method.name)
                    a.signerPackage?.let { put("signer", it) }
                    a.label?.let { put("label", it) }
                })
            }
        }
        prefs.edit().putString("list", json.toString()).putString("active", s.active).apply()
    }

    private fun load(): AccountsState {
        val list = runCatching {
            (NostrJson.parseToJsonElement(prefs.getString("list", "[]")!!) as JsonArray).mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    Account(
                        o["pubkey"]!!.jsonPrimitive.content,
                        Method.valueOf(o["method"]!!.jsonPrimitive.content),
                        o["signer"]?.jsonPrimitive?.contentOrNull,
                        o["label"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
        return AccountsState(list, prefs.getString("active", null))
    }
}

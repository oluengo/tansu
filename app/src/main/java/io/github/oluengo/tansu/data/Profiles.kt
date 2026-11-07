package io.github.oluengo.tansu.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nombres y fotos de las cuentas añadidas (perfil público kind 0), para distinguirlas. */
class Profiles(private val vault: Vault, private val accounts: AccountsRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val state: StateFlow<Map<String, Profile>> = _state.asStateFlow()

    fun start() {
        scope.launch {
            accounts.state.collect { s ->
                val missing = s.list.map { it.pubkey }.filter { it !in _state.value }
                if (missing.isNotEmpty()) launch {
                    val got = try { vault.profiles(missing) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
                    _state.update { it + got }
                }
            }
        }
    }
}

package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.Signer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del explorador para la cuenta activa: lo que hay en Nostr, lo ya descifrado (solo en memoria)
 * y las operaciones (guardar, copiar a otra cuenta, borrar, restaurar). Todo lo que se publica va cifrado.
 */
class Explorer(
    private val accounts: AccountsRepository,
    private val signer: Signer,
    private val vault: Vault,
    val trash: Trash,
    private val scope: CoroutineScope,
) {
    sealed interface Load {
        data object Loading : Load
        data class Ready(val snapshot: Snapshot) : Load
        data object Failed : Load
    }

    data class State(
        val pubkey: String? = null,
        val load: Load = Load.Loading,
        val refreshing: Boolean = false,
        /** Contenido descifrado por etiqueta d (solo en memoria; se olvida al cambiar de cuenta). */
        val opened: Map<String, Opened> = emptyMap(),
        /** Elementos privados de listas NIP-51 descifrados, por dirección. */
        val listPrivate: Map<String, List<List<String>>> = emptyMap(),
    ) {
        val snapshot: Snapshot? get() = (load as? Load.Ready)?.snapshot
    }

    /** Paso actual de una operación larga (p. ej. «3 de 5» firmas). */
    data class Progress(val step: Int, val total: Int)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val cache = HashMap<String, Snapshot>()
    private var loadJob: Job? = null

    fun start() {
        scope.launch {
            accounts.state.map { it.current?.pubkey }.distinctUntilChanged().collect { pk ->
                val cached = pk?.let { cache[it] }
                _state.value = State(pk, if (cached != null) Load.Ready(cached) else Load.Loading)
                if (pk != null) refresh()
            }
        }
    }

    fun refresh() {
        val pk = _state.value.pubkey ?: return
        loadJob?.cancel()
        _state.update { it.copy(refreshing = true) }
        loadJob = scope.launch {
            val r = try { vault.load(pk) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (_state.value.pubkey != pk) return@launch
            if (r != null) cache[pk] = r
            _state.update {
                it.copy(
                    load = when {
                        r != null -> Load.Ready(r)
                        it.load is Load.Ready -> it.load
                        else -> Load.Failed
                    },
                    refreshing = false,
                )
            }
        }
    }

    private fun account(): Account = accounts.current ?: throw VaultException(VaultException.Reason.READ_ONLY)

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: VaultException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(VaultException(VaultException.Reason.OFFLINE, e))
    } finally {
        _progress.value = null
    }

    suspend fun open(entry: Entry): Result<Opened> = attempt {
        _state.value.opened[entry.d]?.let { return@attempt it }
        val o = vault.open(entry, signer.crypto(account()))
        _state.update { it.copy(opened = it.opened + (entry.d to o)) }
        o
    }

    suspend fun openList(e: Event): Result<List<List<String>>> = attempt {
        _state.value.listPrivate[e.address]?.let { return@attempt it }
        val plain = vault.decode(e.content, signer.crypto(account()))
        val items = Lists.parsePrivate(plain) ?: throw VaultException(VaultException.Reason.UNREADABLE)
        _state.update { it.copy(listPrivate = it.listPrivate + (e.address to items)) }
        items
    }

    /** Guarda el texto editado (cifrado). Solo entradas ya descifradas y cifradas con NIP-44. */
    suspend fun save(entry: Entry, text: String): Result<Unit> = attempt {
        val a = account()
        if (!a.canSign) throw VaultException(VaultException.Reason.READ_ONLY)
        val old = _state.value.opened[entry.d] ?: throw VaultException(VaultException.Reason.UNREADABLE)
        val snap = _state.value.snapshot ?: throw VaultException(VaultException.Reason.OFFLINE)
        trash.add(a.pubkey, entry)
        vault.write(signer.crypto(a), snap.relays, entry.d, text, old.manifest, entry.parts.size) { i, n -> _progress.value = Progress(i, n) }
        _state.update { it.copy(opened = it.opened + (entry.d to old.copy(text = text))) }
        refresh()
    }

    /** Guarda una lista NIP-51 editada (privados cifrados; públicos solo se quitan). Copia la anterior a la papelera. */
    suspend fun saveList(e: Event, public: List<List<String>>, private: List<List<String>>): Result<Unit> = attempt {
        val a = account()
        if (!a.canSign) throw VaultException(VaultException.Reason.READ_ONLY)
        val snap = _state.value.snapshot ?: throw VaultException(VaultException.Reason.OFFLINE)
        // Si tenía parte privada, hay que haberla descifrado antes para no perderla.
        if (Lists.hasPrivate(e) && _state.value.listPrivate[e.address] == null) throw VaultException(VaultException.Reason.UNREADABLE)
        val label = e.tag("title") ?: e.tag("d")?.takeIf { it.isNotEmpty() } ?: "kind ${e.kind}"
        trash.add(a.pubkey, label, listOf(e))
        val saved = vault.writeList(signer.crypto(a), snap.relays, e, public, private)
        val lists = snap.lists.map { if (it.address == e.address) saved else it }
        _state.update {
            it.copy(
                load = Load.Ready(snap.copy(lists = lists)).also { r -> cache[a.pubkey] = r.snapshot },
                listPrivate = it.listPrivate + (saved.address to private),
            )
        }
    }

    /** Copia la entrada a otra cuenta: se descifra con esta y se vuelve a cifrar para la otra. */
    suspend fun copyTo(entry: Entry, target: Account): Result<Unit> = attempt {
        if (!target.canSign) throw VaultException(VaultException.Reason.READ_ONLY)
        val opened = _state.value.opened[entry.d] ?: open(entry).getOrThrow()
        val relays = vault.relaysFor(target.pubkey)
        val existing = cache[target.pubkey]?.entries?.firstOrNull { it.d == entry.d }
        vault.write(signer.crypto(target), relays, entry.d, opened.text, opened.manifest, existing?.parts?.size ?: entry.parts.size) { i, n ->
            _progress.value = Progress(i, n)
        }
        cache.remove(target.pubkey)
    }

    /** Copia una entrada o lista (eventos ya firmados) a los relays de la cuenta donde falta. Devuelve en cuántos. */
    suspend fun syncCopies(events: List<Event>): Result<Int> = attempt {
        val snap = _state.value.snapshot ?: throw VaultException(VaultException.Reason.OFFLINE)
        val n = vault.syncCopies(snap, events)
        refresh()
        n
    }

    /** Borra en Nostr (NIP-09) tras guardar una copia local en la papelera. */
    suspend fun delete(entry: Entry): Result<Unit> = attempt {
        val a = account()
        if (!a.canSign) throw VaultException(VaultException.Reason.READ_ONLY)
        val snap = _state.value.snapshot ?: throw VaultException(VaultException.Reason.OFFLINE)
        val item = trash.add(a.pubkey, entry)
        try {
            vault.delete(signer.crypto(a), snap.relays, entry)
        } catch (e: Exception) {
            trash.remove(item.id)
            throw e
        }
        _state.update { s ->
            s.copy(
                opened = s.opened - entry.d,
                load = Load.Ready(snap.copy(entries = snap.entries.filterNot { it.d == entry.d })).also { cache[a.pubkey] = it.snapshot },
            )
        }
    }

    /** Vuelve a publicar una copia de la papelera con su cuenta. */
    suspend fun restore(item: TrashItem): Result<Unit> = attempt {
        val a = accounts.find(item.pubkey) ?: throw VaultException(VaultException.Reason.READ_ONLY)
        if (!a.canSign) throw VaultException(VaultException.Reason.READ_ONLY)
        val relays = vault.relaysFor(a.pubkey)
        val crypto = signer.crypto(a)
        var i = 0
        item.events.forEach { e ->
            _progress.value = Progress(++i, item.events.size)
            vault.republish(crypto, relays, listOf(e))
        }
        trash.remove(item.id)
        cache.remove(a.pubkey)
        if (a.pubkey == _state.value.pubkey) refresh()
    }

    /** Al quitar una cuenta se olvida lo que había en memoria. */
    fun forget(pubkey: String) { cache.remove(pubkey) }
}

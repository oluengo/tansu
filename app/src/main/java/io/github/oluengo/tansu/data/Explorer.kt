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
    private val infoClient: io.github.oluengo.tansu.nostr.RelayInfoClient,
    private val pool: io.github.oluengo.tansu.nostr.RelayPool,
    private val accounts: AccountsRepository,
    private val signer: Signer,
    private val vault: Vault,
    val trash: Trash,
    private val scope: CoroutineScope,
    private val stats: RelayStats,
    private val reputation: RelayReputation,
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
        /** Fichas NIP-11 por relay (null = no publica ficha o no respondió). */
        val info: Map<String, io.github.oluengo.tansu.nostr.RelayInfo?> = emptyMap(),
        /** Último motivo de rechazo de cada relay. */
        val rejections: Map<String, String> = emptyMap(),
        /** Monitores NIP-66 por relay. */
        val monitors: Map<String, MonitorInfo> = emptyMap(),
        /** Relay → seguidos de la cuenta que lo usan (null = aún no se sabe). */
        val follows: Map<String, Int>? = null,
        /** Cambia cuando cambia el historial local (para volver a pintar). */
        val statsTick: Int = 0,
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
    private val monCache = java.util.concurrent.ConcurrentHashMap<String, MonitorInfo>()
    private val monAsked = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val followsCache = java.util.concurrent.ConcurrentHashMap<String, Map<String, Int>>()

    /** Señales de fiabilidad de [relay] para la cuenta activa (todas opcionales). */
    fun signals(relay: String): RelaySignals {
        val st = _state.value
        val snap = st.snapshot
        val mon = st.monitors[relay]
        val hist = stats.history(relay)
        val ret = st.pubkey?.let { stats.retention(it, relay) }
        return RelaySignals(
            monOnline = mon?.online, monTotal = mon?.total, monLast = mon?.last,
            histOk = hist?.first, histTotal = hist?.second,
            lost = ret?.first, kept = ret?.second,
            follows = st.follows?.let { it[relay] ?: 0 },
            ms = snap?.latency?.get(relay) ?: mon?.ms,
        )
    }

    fun rating(relay: String): RelayRating? = RelayRating.of(signals(relay))

    /** Pide a los monitores lo que saben de [relays] (como mucho cada 30 min por relay). */
    fun askMonitors(relays: Collection<String>) {
        val t = System.currentTimeMillis()
        val todo = relays.filter { (monAsked[it] ?: 0) < t - 30 * 60_000 }
        if (todo.isEmpty()) return
        todo.forEach { monAsked[it] = t }
        scope.launch {
            val m = try { reputation.monitors(todo) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
            monCache.putAll(m)
            _state.update { it.copy(monitors = HashMap(monCache)) }
        }
    }
    // HashMap sincronizado: un relay sin ficha se guarda como null (ConcurrentHashMap no admite null)
    private val infoCache: MutableMap<String, io.github.oluengo.tansu.nostr.RelayInfo?> = java.util.Collections.synchronizedMap(HashMap())

    /** Vuelve a leer la ficha de un relay (y recarga). */
    fun reloadInfo(relay: String) { infoCache.remove(relay); refresh() }

    /** Ficha de un relay que no es de la cuenta (p. ej. añadido a mano). */
    suspend fun infoOf(relay: String): io.github.oluengo.tansu.nostr.RelayInfo? {
        askMonitors(listOf(relay))
        if (!infoCache.containsKey(relay)) infoCache[relay] = infoClient.fetch(relay)
        _state.update { it.copy(info = synchronized(infoCache) { HashMap(infoCache) }) }
        return infoCache[relay]
    }

    /** Copia a [relay] los datos elegidos (cada uno, sus eventos tal cual). Devuelve cuántos datos llegaron. */
    suspend fun copyItemsTo(relay: String, todo: List<List<Event>>): Result<Int> = attempt {
        var n = 0
        todo.forEachIndexed { i, ev -> _progress.value = Progress(i + 1, todo.size); if (runCatching { vault.copyTo(listOf(relay), ev) }.isSuccess) n++ }
        if (todo.isNotEmpty() && n == 0) throw VaultException(VaultException.Reason.OFFLINE)
        refresh()
        n
    }
    private var loadJob: Job? = null

    fun start() {
        scope.launch {
            accounts.state.map { it.current?.pubkey }.distinctUntilChanged().collect { pk ->
                val cached = pk?.let { cache[it] }
                _state.value = State(
                    pk, if (cached != null) Load.Ready(cached) else Load.Loading,
                    info = synchronized(infoCache) { HashMap(infoCache) }, monitors = HashMap(monCache), follows = pk?.let { followsCache[it] },
                )
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
            if (r != null) { cache[pk] = r; runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { stats.record(r) } } }
            _state.update {
                it.copy(
                    load = when {
                        r != null -> Load.Ready(r)
                        it.load is Load.Ready -> it.load
                        else -> Load.Failed
                    },
                    refreshing = false,
                    rejections = HashMap(pool.rejections),
                    statsTick = it.statsTick + 1,
                )
            }
            if (r != null) {
                askMonitors(r.relays)
                if (!followsCache.containsKey(pk)) scope.launch {
                    val f = try { reputation.follows(pk, r.relays) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (f != null) { followsCache[pk] = f; if (_state.value.pubkey == pk) _state.update { it.copy(follows = f) } }
                }
            }
            // Fichas NIP-11 de los relays que aún no tenemos (se guardan mientras la app está abierta)
            r?.relays?.filter { !infoCache.containsKey(it) }?.takeIf { it.isNotEmpty() }?.let { missing ->
                infoCache.putAll(try { infoClient.fetchAll(missing) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() })
                _state.update { it.copy(info = synchronized(infoCache) { HashMap(infoCache) }) }
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

    /** Copia los eventos tal cual a un relay concreto (puede ser uno que no esté en la lista de la cuenta). */
    suspend fun copyToRelay(events: List<Event>, relay: String): Result<Int> = attempt {
        val n = vault.copyTo(listOf(relay), events)
        refresh()
        n
    }

    /** Borra solo de un relay (NIP-09 enviado únicamente a ese relay). Antes guarda copia en la papelera. */
    suspend fun deleteFromRelay(events: List<Event>, label: String, relay: String): Result<Unit> = attempt {
        val a = account()
        if (!a.canSign) throw VaultException(VaultException.Reason.READ_ONLY)
        trash.add(a.pubkey, label, events)
        vault.deleteFrom(signer.crypto(a), listOf(relay), events)
        stats.forget(a.pubkey, events.map { it.address }, relay)
        refresh()
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

    // ---------- Operaciones en grupo ----------

    /** Resultado en un relay: hechos, fallidos (con el último motivo del relay) y, al borrar, los que siguen ahí. */
    data class RelayOutcome(val relay: String, val done: Int, val failed: Int = 0, val reason: String? = null, val ignored: Int = 0, val skipped: Int = 0)

    /** Un dato elegido: sus eventos y cómo se llama (para la papelera). */
    data class Pick(val key: String, val label: String, val events: List<Event>)

    /** Lo que tiene [relay] de [p] según la última carga (null = no lo sabemos: no respondió o no es de la cuenta). */
    fun hasAt(relay: String, p: Pick): Boolean? {
        val snap = _state.value.snapshot ?: return null
        if (relay !in snap.reached) return null
        return snap.copies(p.events).firstOrNull { it.relay == relay }?.state?.let { it != RelayCopy.State.MISSING }
    }

    /** Lo que le falta (o tiene anticuado) a [relay]; si no lo sabemos, todo. */
    fun missingAt(relay: String, p: Pick): Boolean {
        val snap = _state.value.snapshot ?: return true
        if (relay !in snap.reached) return true
        return snap.copies(p.events).firstOrNull { it.relay == relay }?.needsSync ?: true
    }

    /** Copia a varios relays lo que les falta de [picks]. Lo sin cifrar se salta (nunca se reenvía nada legible). */
    suspend fun groupCopy(relays: List<String>, picks: List<Pick>): Result<List<RelayOutcome>> = attempt {
        val ok = picks.filter { p -> p.events.none { detectEncoding(it.content) == Encoding.PLAIN } }
        val jobs = relays.associateWith { r -> ok.filter { missingAt(r, it) } }
        val total = jobs.values.sumOf { it.size }.coerceAtLeast(1)
        var step = 0
        val out = jobs.map { (r, todo) ->
            var done = 0
            todo.forEach { p ->
                _progress.value = Progress(++step, total)
                if (runCatching { vault.copyTo(listOf(r), p.events) }.isSuccess) done++
            }
            RelayOutcome(r, done, todo.size - done, if (done < todo.size) pool.rejections[r] else null, skipped = picks.size - ok.size)
        }
        _progress.value = null
        reloadNow()
        out
    }

    /**
     * Borra [picks] de varios relays: una petición NIP-09 por relay (en tandas de 50 datos).
     * Lo que se quedaría sin ninguna copia va antes a la papelera (cifrado, como estaba).
     * Al acabar vuelve a cargar y cuenta lo que cada relay sigue teniendo (no todos hacen caso del borrado).
     */
    suspend fun groupDeleteFrom(relays: List<String>, picks: List<Pick>): Result<List<RelayOutcome>> = attempt {
        val a = account()
        if (!a.canSign) throw VaultException(VaultException.Reason.READ_ONLY)
        val snap = _state.value.snapshot ?: throw VaultException(VaultException.Reason.OFFLINE)
        orphans(relays, picks).forEach { trash.add(a.pubkey, it.label, it.events) }
        val crypto = signer.crypto(a)
        val jobs = relays.associateWith { r -> picks.filter { hasAt(r, it) != false } }
        val total = jobs.values.sumOf { (it.size + 49) / 50 }.coerceAtLeast(1)
        var step = 0
        val sent = jobs.map { (r, todo) ->
            var done = 0
            todo.chunked(50).forEach { chunk ->
                _progress.value = Progress(++step, total)
                if (runCatching { vault.deleteFrom(crypto, listOf(r), chunk.flatMap { it.events }) }.isSuccess) done += chunk.size
            }
            stats.forget(a.pubkey, todo.flatMap { p -> p.events.map { it.address } }, r)
            Triple(r, todo, done)
        }
        _progress.value = null
        val after = reloadNow() ?: snap
        sent.map { (r, todo, done) ->
            val still = if (r in after.reached) todo.count { p -> after.copies(p.events).firstOrNull { it.relay == r }?.state.let { it != null && it != RelayCopy.State.MISSING } } else 0
            RelayOutcome(r, done - still.coerceAtMost(done), todo.size - done, if (done < todo.size) pool.rejections[r] else null, ignored = still)
        }
    }

    /** Datos de [picks] que, borrados de [relays], no quedarían en ningún otro relay que respondió. */
    fun orphans(relays: List<String>, picks: List<Pick>): List<Pick> {
        val snap = _state.value.snapshot ?: return emptyList()
        return picks.filter { p -> snap.copies(p.events).none { it.relay !in relays && it.state != RelayCopy.State.MISSING && it.state != RelayCopy.State.UNKNOWN } }
    }

    /** Copia varias entradas a otra cuenta (se descifran y se vuelven a cifrar para ella). Devuelve cuántas. */
    suspend fun groupCopyToAccount(entries: List<Entry>, target: Account): Result<Int> = attempt {
        if (!target.canSign) throw VaultException(VaultException.Reason.READ_ONLY)
        var n = 0
        entries.forEachIndexed { i, e ->
            _progress.value = Progress(i + 1, entries.size)
            if (copyTo(e, target).isSuccess) n++
        }
        _progress.value = null
        if (entries.isNotEmpty() && n == 0) throw VaultException(VaultException.Reason.OFFLINE)
        n
    }

    /** Carga ahora (esperando) y actualiza el estado. */
    private suspend fun reloadNow(): Snapshot? {
        val pk = _state.value.pubkey ?: return null
        loadJob?.cancel()
        val r = try { vault.load(pk) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return null
        cache[pk] = r
        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { stats.record(r) } }
        if (_state.value.pubkey == pk) _state.update { it.copy(load = Load.Ready(r), refreshing = false, rejections = HashMap(pool.rejections), statsTick = it.statsTick + 1) }
        return r
    }

    /** Al quitar una cuenta se olvida lo que había en memoria. */
    fun forget(pubkey: String) { cache.remove(pubkey) }
}

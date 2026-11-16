package io.github.oluengo.tansu.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.tansu.AppContainer
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.data.AppSettings
import io.github.oluengo.tansu.data.Snapshot
import io.github.oluengo.tansu.data.RelayRating
import io.github.oluengo.tansu.data.RelaySignals
import io.github.oluengo.tansu.ui.common.ago
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.oluengo.tansu.data.RelayCopy
import io.github.oluengo.tansu.data.Encoding
import io.github.oluengo.tansu.data.detectEncoding
import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.Nip19
import io.github.oluengo.tansu.nostr.RelayInfo
import io.github.oluengo.tansu.nostr.RelayPool
import io.github.oluengo.tansu.ui.common.Badge
import io.github.oluengo.tansu.ui.common.EmptyNote
import io.github.oluengo.tansu.ui.common.Group
import io.github.oluengo.tansu.ui.common.InfoRow
import io.github.oluengo.tansu.ui.common.SectionLabel
import io.github.oluengo.tansu.ui.common.errorText
import io.github.oluengo.tansu.ui.common.fullDate
import io.github.oluengo.tansu.ui.common.size
import io.github.oluengo.tansu.ui.theme.Ts
import kotlinx.coroutines.launch

private val Good = Color(0xFF2E8A5A)
private val GoodSoft = Color(0x262E8A5A)

internal fun short(relay: String) = relay.removePrefix("wss://")

/** Avisos de un relay para una entrada concreta (o la cuenta, si [events] es null). */
@Composable
internal fun relayWarnings(info: RelayInfo?, rejection: String?, events: List<Event>?): List<String> {
    val ctx = LocalContext.current
    val out = ArrayList<String>()
    val biggest = events?.maxOfOrNull { it.content.length }
    val limit = info?.maxContentLength ?: info?.maxMessageLength
    if (biggest != null && limit != null && biggest > limit) out += stringResource(R.string.ri_w_small, size(ctx, limit))
    if (info?.supportsDeletion == false) out += stringResource(R.string.ri_w_nodel)
    if (info?.authRequired == true) out += stringResource(R.string.ri_w_auth)
    if (info?.paid == true && events == null) out += stringResource(R.string.ri_w_paid)
    rejection?.let { out += stringResource(R.string.ri_w_reject, it.take(120)) }
    return out
}

@Composable
internal fun WarningLines(lines: List<String>) {
    lines.forEach { Text("⚠ $it", color = Ts.colors.lock, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)) }
}

/** Chips cortos de un relay: gratis/de pago, login, escritura, borrados, latencia. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelayChips(info: RelayInfo?, ms: Long?, reached: Boolean) {
    val ctx = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!reached) Badge(stringResource(R.string.relay_unknown), Ts.colors.danger, Ts.colors.danger.copy(alpha = 0.12f))
        ms?.let { Badge(stringResource(R.string.ri_ms, it.toInt())) }
        if (info != null) {
            if (info.paid) Badge(stringResource(R.string.ri_paid), Ts.colors.lock, Ts.colors.lockSoft)
            else if (info.paymentRequired == false) Badge(stringResource(R.string.ri_free), Good, GoodSoft)
            if (info.authRequired == true) Badge(stringResource(R.string.ri_auth), Ts.colors.lock, Ts.colors.lockSoft)
            if (info.restrictedWrites == true) Badge(stringResource(R.string.ri_restricted), Ts.colors.lock, Ts.colors.lockSoft)
            if (info.supportsDeletion == false) Badge(stringResource(R.string.ri_nodel), Ts.colors.lock, Ts.colors.lockSoft)
            (info.maxContentLength ?: info.maxMessageLength)?.let { Badge(stringResource(R.string.ri_max, size(ctx, it))) }
        }
    }
}

private fun role(snap: Snapshot?, relay: String, tansu: List<String>): Int = when {
    snap != null && relay in snap.own -> R.string.ri_role_nip65
    relay in tansu -> R.string.ri_role_tansu
    else -> R.string.ri_role_other
}

private fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("relay", text))
    Toast.makeText(ctx, ctx.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

private fun feeText(ctx: Context, f: io.github.oluengo.tansu.nostr.RelayFee): String {
    val kind = when (f.kind) {
        "admission" -> ctx.getString(R.string.ri_fee_admission)
        "subscription" -> ctx.getString(R.string.ri_fee_subscription)
        else -> ctx.getString(R.string.ri_fee_publication)
    }
    val amount = if (f.unit == "msats") "${f.amount / 1000} sats" else "${f.amount} ${f.unit}"
    val period = f.period?.let { " / ${(it / 86_400).coerceAtLeast(1)} d" } ?: ""
    return "$kind: $amount$period"
}

/** Ficha de un relay (NIP-11) más lo que sabemos de él en esta cuenta. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RelayInfoSheet(c: AppContainer, relay: String, current: List<Event>? = null, onClose: () -> Unit) {
    val st by c.explorer.state.collectAsStateWithLifecycle()
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snap = st.snapshot
    val tansu = s.appRelays ?: RelayPool.BUILTIN
    val known = st.info.containsKey(relay)
    val info = st.info[relay]
    LaunchedEffect(relay) { if (!known) c.explorer.infoOf(relay) }
    val reached = snap?.reached?.contains(relay) ?: false
    val inAccount = snap?.relays?.contains(relay) == true
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val unknown = stringResource(R.string.ri_unknown)
    fun yesNo(b: Boolean?) = when (b) { true -> ctx.getString(R.string.ri_required); false -> ctx.getString(R.string.ri_not_required); null -> unknown }

    fun copy(items: List<List<Event>>) {
        busy = true
        scope.launch {
            c.explorer.copyItemsTo(relay, items)
                .onSuccess { Toast.makeText(ctx, ctx.resources.getQuantityString(R.plurals.ri_synced_here, it, it), Toast.LENGTH_SHORT).show(); picking = false }
                .onFailure { Toast.makeText(ctx, errorText(ctx, it), Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (picking && snap != null) {
            CopyPicker(snap, relay, info, current, onBack = { picking = false }, onCopy = ::copy)
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text(info?.name ?: short(relay), style = MaterialTheme.typography.titleLarge)
            Text(relay, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted)
            Spacer(Modifier.size(8.dp))
            RelayChips(info, snap?.latency?.get(relay), reached || !inAccount)
            info?.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)) }
            when {
                !known -> Text(stringResource(R.string.loading), color = Ts.colors.muted, modifier = Modifier.padding(top = 10.dp))
                info == null -> Text(stringResource(R.string.ri_none), color = Ts.colors.muted, modifier = Modifier.padding(top = 10.dp))
            }
            if (inAccount && !reached) {
                Text(stringResource(R.string.ri_down), color = Ts.colors.danger, modifier = Modifier.padding(top = 10.dp))
                TextButton(onClick = { c.explorer.reloadInfo(relay) }) { Text(stringResource(R.string.retry)) }
            }
            Spacer(Modifier.size(6.dp))
            val sig = remember(st, relay) { c.explorer.signals(relay) }
            WarningLines(relayWarnings(info, st.rejections[relay], null) + if ((sig.lost ?: 0) > 0) listOf(stringResource(R.string.rel_w_lost)) else emptyList())
            Reliability(sig, st.follows == null)

            if (info != null) {
                SectionLabel(stringResource(R.string.ri_access))
                InfoRow(stringResource(R.string.ri_payment), if (info.paid) stringResource(R.string.ri_paid) else if (info.paymentRequired == false) stringResource(R.string.ri_free) else unknown)
                if (info.fees.isNotEmpty()) InfoRow(stringResource(R.string.ri_fees), info.fees.joinToString("\n") { feeText(ctx, it) })
                info.paymentsUrl?.let { u ->
                    Row(Modifier.fillMaxWidth().clickable { uri.openUri(u) }) { InfoRow(stringResource(R.string.ri_pay_link), u) }
                }
                InfoRow(stringResource(R.string.ri_login), yesNo(info.authRequired))
                InfoRow(stringResource(R.string.ri_writes), when (info.restrictedWrites) { true -> stringResource(R.string.ri_restricted); false -> stringResource(R.string.ri_writes_open); null -> unknown })
                info.minPowDifficulty?.takeIf { it > 0 }?.let { InfoRow(stringResource(R.string.ri_pow), stringResource(R.string.ri_pow_v, it)) }

                SectionLabel(stringResource(R.string.ri_limits))
                InfoRow(stringResource(R.string.ri_max_msg), info.maxMessageLength?.let { size(ctx, it) } ?: unknown)
                InfoRow(stringResource(R.string.ri_max_content), info.maxContentLength?.let { size(ctx, it) } ?: unknown)
                info.createdAtLowerLimit?.let { v ->
                    // Unos relays ponen una fecha y otros los segundos hacia atrás
                    InfoRow(stringResource(R.string.ri_oldest), if (v > 1_000_000_000) fullDate(v) else stringResource(R.string.ri_days, (v / 86_400).toInt()))
                }

                info.supportedNips?.let { nips ->
                    SectionLabel(stringResource(R.string.ri_nips))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        nips.sorted().forEach { n ->
                            val key = n == 9 || n == 42 || n == 44 || n == 65 || n == 78 || n == 51
                            if (key) Badge("NIP-%02d".format(n), MaterialTheme.colorScheme.primary, Ts.colors.accentSoft) else Badge("%02d".format(n))
                        }
                    }
                }

                SectionLabel(stringResource(R.string.ri_who))
                info.pubkey?.let { pk ->
                    val np = runCatching { Nip19.hexToNpub(pk) }.getOrDefault(pk)
                    Row(Modifier.fillMaxWidth().clickable { copyText(ctx, np) }) { InfoRow(stringResource(R.string.ri_operator), Nip19.shortNpub(pk)) }
                }
                info.contact?.let { InfoRow(stringResource(R.string.ri_contact), it) }
                info.termsOfService?.let { u -> Row(Modifier.fillMaxWidth().clickable { uri.openUri(u) }) { InfoRow(stringResource(R.string.ri_terms), u) } }
                info.software?.let { InfoRow(stringResource(R.string.ri_software), listOfNotNull(it.substringAfterLast('/'), info.version).joinToString(" ")) }
            }

            if (snap != null && inAccount) {
                SectionLabel(stringResource(R.string.ri_account))
                InfoRow(stringResource(R.string.ri_role), stringResource(role(snap, relay, tansu)))
                snap.stats(relay)?.let { (cur, old, miss) -> InfoRow(stringResource(R.string.ri_data), stringResource(R.string.ri_stats, cur, old, miss)) }
                snap.latency[relay]?.let { InfoRow(stringResource(R.string.ri_latency), stringResource(R.string.ri_ms, it.toInt())) }
            }
            if (snap != null) {
                // Lo que estás viendo, primero; elegir otros datos, aparte. Nunca se copia todo de golpe.
                val here = current?.let { ev -> snap.copies(ev).firstOrNull { it.relay == relay }?.state }
                val showThis = current != null && (here == null || here == RelayCopy.State.MISSING || here == RelayCopy.State.OLDER || here == RelayCopy.State.PARTIAL)
                val todo = snap.missingAt(relay).size
                if (showThis) Button(enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), onClick = { copy(listOf(current!!)) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.ri_copy_this))
                        Text(itemLabel(ctx, snap, current!!) + " · " + size(ctx, current.sumOf { it.content.length }), style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = { picking = true }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.ri_pick))
                        if (todo > 0 && relay in snap.reached) Text(ctx.resources.getQuantityString(R.plurals.ri_pick_sub, todo, todo), style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { copyText(ctx, relay) }) { Text(stringResource(R.string.ri_copy_url)) }
                if (relay in tansu) {
                    if (tansu.size > 1) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch { val l = tansu - relay; c.settings.setAppRelays(if (l == RelayPool.BUILTIN) null else l); onClose() }
                    }) { Text(stringResource(R.string.ri_remove_tansu), color = Ts.colors.danger) }
                } else OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch { val l = tansu + relay; c.settings.setAppRelays(if (l == RelayPool.BUILTIN) null else l) }
                }) { Text(stringResource(R.string.ri_add_tansu)) }
            }
            Text(stringResource(R.string.ri_note), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }
    }
    if (busy) BusyDialog(c)
}

private enum class RelayFilter(val label: Int) { ALL(R.string.ri_f_all), PAID(R.string.ri_f_paid), AUTH(R.string.ri_f_auth), NODEL(R.string.ri_f_nodel), DOWN(R.string.ri_f_down), LOW(R.string.rel_f_low) }

/** Todos los relays de la cuenta con su ficha resumida. */
@Composable
fun RelaysScreen(c: AppContainer, snap: Snapshot, refreshing: Boolean) {
    val st by c.explorer.state.collectAsStateWithLifecycle()
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val tansu = s.appRelays ?: RelayPool.BUILTIN
    var filter by rememberSaveable { mutableStateOf(RelayFilter.ALL) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val all = (snap.relays + tansu).distinct()
    LaunchedEffect(all) { c.explorer.askMonitors(all) }
    val sigs = remember(st, all) { all.associateWith { c.explorer.signals(it) } }
    val ratings = remember(sigs) { sigs.mapValues { RelayRating.of(it.value) } }
    val shown = all.filter { r ->
        val i = st.info[r]
        when (filter) {
            RelayFilter.ALL -> true
            RelayFilter.PAID -> i?.paid == true
            RelayFilter.AUTH -> i?.authRequired == true
            RelayFilter.NODEL -> i?.supportsDeletion == false
            RelayFilter.DOWN -> r in snap.relays && r !in snap.reached
            RelayFilter.LOW -> (ratings[r]?.stars ?: 5) <= 2
        }
    }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { c.explorer.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.ri_list_note), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RelayFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.label)) }) }
                }
            }
            if (shown.isEmpty()) item { EmptyNote(stringResource(R.string.ri_empty_filter), Modifier.padding(horizontal = 4.dp)) }
            items(shown, key = { it }) { r ->
                val info = st.info[r]
                val inAccount = r in snap.relays
                Group(Modifier.clip(RoundedCornerShape(14.dp)).clickable { open = r }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(info?.name ?: short(r), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (info?.name != null && info.name != short(r)) Text(short(r), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Stars(ratings[r]?.stars)
                    }
                    SummaryLine(sigs.getValue(r))
                    Spacer(Modifier.size(6.dp))
                    RelayChips(info, snap.latency[r], !inAccount || r in snap.reached)
                    Text(stringResource(role(snap, r, tansu)), color = Ts.colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    snap.stats(r)?.let { (cur, old, miss) ->
                        Text(stringResource(R.string.ri_stats, cur, old, miss), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    st.rejections[r]?.let { WarningLines(listOf(stringResource(R.string.ri_w_reject, it.take(120)))) }
                }
            }
            item {
                OutlinedButton(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), onClick = { adding = true }) { Text("+ " + stringResource(R.string.ri_add_relay)) }
            }
        }
    }
    open?.let { RelayInfoSheet(c, it) { open = null } }
    if (adding) AddRelayDialog(onDone = { r -> adding = false; open = r }, onCancel = { adding = false })
}

/** Pide una dirección wss:// y abre su ficha (desde ahí se puede añadir a los de Tansu). */
@Composable
private fun AddRelayDialog(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.ri_add_relay)) },
        text = {
            OutlinedTextField(
                value = input, onValueChange = { input = it; invalid = false }, singleLine = true,
                placeholder = { Text("wss://relay.example.com") }, isError = invalid,
                supportingText = { if (invalid) Text(stringResource(R.string.relay_invalid)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        },
        confirmButton = { TextButton(onClick = { RelayPool.normalize(input)?.let(onDone) ?: run { invalid = true } }) { Text(stringResource(R.string.ri_open)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------- Elegir qué copiar a un relay ----------

/** Un dato de la cuenta (entrada NIP-78 con sus partes, o lista NIP-51) visto desde un relay. */
private data class PickItem(
    val key: String, val label: String, val group: String, val events: List<Event>,
    val state: RelayCopy.State, val parts: Int, val bytes: Int, val blocked: String?,
) { val needs get() = state == RelayCopy.State.MISSING || state == RelayCopy.State.OLDER || state == RelayCopy.State.PARTIAL || state == RelayCopy.State.UNKNOWN }

private fun itemLabel(ctx: Context, snap: Snapshot, ev: List<Event>): String {
    val main = ev.last()
    return snap.entries.firstOrNull { it.main.id == main.id }?.d ?: listTitle(main, ctx)
}

private fun pickItems(ctx: Context, snap: Snapshot, relay: String, info: RelayInfo?): List<PickItem> {
    val limit = info?.maxContentLength ?: info?.maxMessageLength
    fun state(ev: List<Event>) = snap.copies(ev).firstOrNull { it.relay == relay }?.state ?: RelayCopy.State.UNKNOWN
    fun blocked(ev: List<Event>): String? = when {
        ev.any { detectEncoding(it.content) == Encoding.PLAIN } -> ctx.getString(R.string.pk_w_plain)
        limit != null && ev.any { it.content.length > limit } -> ctx.getString(R.string.pk_w_big, size(ctx, limit))
        else -> null
    }
    val entries = snap.entries.map { e ->
        PickItem(e.main.address, e.d, if (e.app.isEmpty()) ctx.getString(R.string.folder_other) else io.github.oluengo.tansu.data.AppData.KNOWN[e.app] ?: e.app, e.events, state(e.events), e.parts.size, e.bytes, blocked(e.events))
    }
    val lists = snap.lists.map { l ->
        PickItem(l.address, listTitle(l, ctx), ctx.getString(R.string.drawer_lists), listOf(l), state(listOf(l)), 0, l.content.length, blocked(listOf(l)))
    }
    return entries + lists
}

@Composable
private fun StateBadge(s: RelayCopy.State) {
    when (s) {
        RelayCopy.State.CURRENT -> Badge("✓ " + stringResource(R.string.relay_current), Good, GoodSoft)
        RelayCopy.State.OLDER -> Badge(stringResource(R.string.relay_older, "").substringBefore(" ("), Ts.colors.lock, Ts.colors.lockSoft)
        RelayCopy.State.PARTIAL -> Badge(stringResource(R.string.relay_partial), Ts.colors.lock, Ts.colors.lockSoft)
        RelayCopy.State.MISSING -> Badge("✕ " + stringResource(R.string.relay_missing), Ts.colors.danger, Ts.colors.danger.copy(alpha = 0.12f))
        RelayCopy.State.UNKNOWN -> Unit
    }
}

/**
 * Selector de datos para copiar a un relay. Pensado para cuentas con mucho contenido:
 * solo lo que no está al día (o todo), por carpetas plegadas, buscador y resumen fijo abajo.
 * Lo sin cifrar y lo que no cabe en el relay no se puede marcar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyPicker(snap: Snapshot, relay: String, info: RelayInfo?, current: List<Event>?, onBack: () -> Unit, onCopy: (List<List<Event>>) -> Unit) {
    val ctx = LocalContext.current
    val all = remember(snap, info) { pickItems(ctx, snap, relay, info) }
    val known = relay in snap.reached
    val currentKey = current?.last()?.address
    val currentGroup = all.firstOrNull { it.key == currentKey }?.group
    var onlyMissing by rememberSaveable { mutableStateOf(known) }
    var q by rememberSaveable { mutableStateOf("") }
    var sel by remember { mutableStateOf(setOfNotNull(currentKey?.takeIf { k -> all.any { it.key == k && it.blocked == null } })) }
    var open by remember { mutableStateOf(setOfNotNull(currentGroup)) }
    var more by remember { mutableStateOf(emptySet<String>()) }
    val needle = q.trim().lowercase()
    val shown = all.filter { (!onlyMissing || it.needs) && (needle.isEmpty() || needle in it.label.lowercase()) }
    val groups = shown.groupBy { it.group }
    val missingOk = all.filter { it.needs && it.blocked == null }
    val chosen = all.filter { it.key in sel }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
                    Text(stringResource(R.string.pk_title, short(relay)), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (known) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    SegmentedButton(selected = onlyMissing, onClick = { onlyMissing = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.pk_mode_miss, all.count { it.needs }), maxLines = 1)
                    }
                    SegmentedButton(selected = !onlyMissing, onClick = { onlyMissing = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.pk_mode_all, all.size), maxLines = 1)
                    }
                }
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text(stringResource(R.string.pk_search)) },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (known && missingOk.isNotEmpty()) TextButton(onClick = { sel = sel + missingOk.map { it.key } }) { Text(stringResource(R.string.pk_select_missing, missingOk.size)) }
                    Spacer(Modifier.weight(1f))
                    if (sel.isNotEmpty()) TextButton(onClick = { sel = emptySet() }) { Text(stringResource(R.string.pk_clear), color = Ts.colors.muted) }
                }
            }
            if (groups.isEmpty()) item { EmptyNote(stringResource(R.string.pk_nothing)) }
            groups.forEach { (g, items) ->
                val can = items.filter { it.blocked == null }
                val on = can.count { it.key in sel }
                val isOpen = g in open || needle.isNotEmpty()
                val total = all.filter { it.group == g }
                item(key = "g:$g") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Ts.colors.folderSoft.copy(alpha = 0.5f))
                            .clickable { open = if (g in open) open - g else open + g }.padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(
                            state = when { on == 0 -> ToggleableState.Off; on == can.size -> ToggleableState.On; else -> ToggleableState.Indeterminate },
                            enabled = can.isNotEmpty(),
                            onClick = { sel = if (on == can.size) sel - can.map { it.key }.toSet() else sel + can.map { it.key }; open = open + g },
                        )
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(g, fontWeight = FontWeight.SemiBold)
                            val sub = if (onlyMissing) ctx.resources.getQuantityString(R.plurals.pk_group_miss, items.size, items.size)
                            else stringResource(R.string.pk_group_all, total.size, total.count { it.needs && known })
                            Text(sub + if (on > 0) " · " + stringResource(R.string.pk_chosen, on) else "", style = MaterialTheme.typography.bodySmall, color = if (on > 0) MaterialTheme.colorScheme.primary else Ts.colors.muted)
                        }
                        Text(if (isOpen) "▾" else "▸", color = Ts.colors.muted)
                    }
                }
                if (isOpen) {
                    val limit = if (g in more) items.size else 6
                    items(items.take(limit), key = { it.key }) {
                        val enabled = it.blocked == null
                        val here = it.key == currentKey
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (here) Ts.colors.accentSoft.copy(alpha = 0.5f) else Color.Transparent)
                                .clickable(enabled = enabled) { sel = if (it.key in sel) sel - it.key else sel + it.key }.padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = it.key in sel, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(it.label, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.Unspecified else Ts.colors.muted,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (here) Badge(stringResource(R.string.pk_here), MaterialTheme.colorScheme.primary, Ts.colors.accentSoft)
                                }
                                val meta = listOfNotNull(
                                    if (it.parts > 0) ctx.resources.getQuantityString(R.plurals.parts, it.parts, it.parts) else null,
                                    size(ctx, it.bytes),
                                ).joinToString(" · ")
                                Text(meta, style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted)
                                it.blocked?.let { b -> Text("⚠ $b", style = MaterialTheme.typography.bodySmall, color = Ts.colors.lock) }
                            }
                            StateBadge(it.state)
                        }
                    }
                    if (items.size > limit) item(key = "m:$g") {
                        TextButton(onClick = { more = more + g }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pk_more, items.size - limit)) }
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            val ev = chosen.sumOf { it.events.size }
            Text(
                if (chosen.isEmpty()) stringResource(R.string.pk_choose)
                else ctx.resources.getQuantityString(R.plurals.pk_items, chosen.size, chosen.size) + " · " +
                    ctx.resources.getQuantityString(R.plurals.pk_events, ev, ev) + " · " + size(ctx, chosen.sumOf { it.bytes }),
                style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted,
            )
            Button(enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp), onClick = { onCopy(chosen.map { it.events }) }) {
                Text(if (chosen.isEmpty()) stringResource(R.string.pk_choose) else stringResource(R.string.pk_go, chosen.size, short(relay)))
            }
        }
    }
}

// ---------- Fiabilidad ----------

private val StarOn = Color(0xFFE9A23B)

/** 1 a 5 estrellas: en color las que tiene, en gris las que faltan. null = sin datos suficientes. */
@Composable
fun Stars(n: Int?, size: Dp = 15.dp) {
    if (n == null) { Text(stringResource(R.string.rel_nodata), style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted); return }
    val desc = stringResource(R.string.rel_stars, n)
    Row(Modifier.semantics { contentDescription = desc }) {
        (1..5).forEach { i -> Icon(Icons.Filled.Star, null, tint = if (i <= n) StarOn else Ts.colors.line, modifier = Modifier.size(size)) }
    }
}

private fun ratio(a: Int?, b: Int?) = if (a != null && b != null && b > 0) a.toDouble() / b else null

/** Línea corta del listado: en línea · seguidos · % respondió · pérdidas. */
@Composable
private fun SummaryLine(s: RelaySignals) {
    val parts = ArrayList<Pair<String, Color?>>()
    ratio(s.monOnline, s.monTotal)?.let { parts += (if (it < 0.5) stringResource(R.string.rel_down) else stringResource(R.string.rel_online)) to (if (it < 0.5) Ts.colors.danger else Good) }
    s.follows?.let { parts += stringResource(R.string.rel_follows_short, it) to null }
    if ((s.histTotal ?: 0) >= 3) parts += stringResource(R.string.rel_answered, "${s.histOk!! * 100 / s.histTotal!!}%") to null
    if ((s.lost ?: 0) > 0) parts += stringResource(R.string.rel_lost_short, s.lost!!) to Ts.colors.lock
    if (parts.isEmpty()) return
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            parts.forEachIndexed { i, (t, col) ->
                if (i > 0) append(" · ")
                if (col != null) pushStyle(androidx.compose.ui.text.SpanStyle(color = col))
                append(t)
                if (col != null) pop()
            }
        },
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
    )
}

/** Sección «Fiabilidad» de la ficha: estrellas, las 4 señales con su barra y la tabla del cálculo. */
@Composable
private fun Reliability(s: RelaySignals, countingFollows: Boolean) {
    val ctx = LocalContext.current
    val g = RelayRating.of(s)
    var how by remember { mutableStateOf(false) }
    SectionLabel(stringResource(R.string.rel_title))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ts.colors.accentSoft.copy(alpha = 0.35f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Stars(g?.stars, 22.dp)
        Column {
            Text(g?.let { stringResource(listOf(R.string.rel_w1, R.string.rel_w2, R.string.rel_w3, R.string.rel_w4, R.string.rel_w5)[it.stars - 1]) } ?: stringResource(R.string.rel_unrated), fontWeight = FontWeight.SemiBold)
            if (g != null) Text(stringResource(if (g.partial) R.string.rel_partial else R.string.rel_full), style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted)
        }
    }
    val sig = g ?: RelayRating(0.0, 1, false, true, null, null, null, null)
    val monTotal = s.monTotal ?: 0
    val kept = s.kept ?: 0
    val lost = s.lost ?: 0
    val mon = if (monTotal > 0) stringResource(R.string.rel_avail_mon, s.monOnline ?: 0, monTotal, ago(ctx, s.monLast ?: 0)) else stringResource(R.string.rel_avail_nomon)
    val hist = if ((s.histTotal ?: 0) >= 3) stringResource(R.string.rel_avail_hist, s.histOk ?: 0, s.histTotal ?: 0) else stringResource(R.string.rel_avail_nohist)
    Signal("📡", stringResource(R.string.rel_avail), "$mon · $hist", g?.avail)
    Signal("🗄", stringResource(R.string.rel_keep), when {
        kept == 0 -> stringResource(R.string.rel_keep_none)
        lost > 0 -> stringResource(R.string.rel_keep_lost, lost, kept)
        else -> stringResource(R.string.rel_keep_ok, kept)
    }, g?.keep)
    Signal("👥", stringResource(R.string.rel_follows), if (countingFollows) stringResource(R.string.rel_follows_wait) else stringResource(R.string.rel_follows_v, s.follows ?: 0), g?.follows)
    Signal("⚡", stringResource(R.string.rel_speed), s.ms?.let { stringResource(R.string.rel_speed_v, it.toInt()) } ?: stringResource(R.string.rel_speed_none), g?.speed)
    if (g != null) {
        TextButton(onClick = { how = !how }) { Text((if (how) "▾ " else "▸ ") + stringResource(R.string.rel_how)) }
        if (how) {
            val rows = listOf(
                Triple(R.string.rel_avail, RelayRating.W_AVAIL, sig.avail), Triple(R.string.rel_keep, RelayRating.W_KEEP, sig.keep),
                Triple(R.string.rel_follows, RelayRating.W_FOLLOWS, sig.follows), Triple(R.string.rel_speed, RelayRating.W_SPEED, sig.speed),
            )
            rows.forEach { (name, w, v) -> InfoRow("${stringResource(name)} ($w%)", v?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.rel_no_signal)) }
            HorizontalDivider()
            InfoRow(stringResource(R.string.rel_total), "${(g.score * 100).toInt()}% → ${g.stars} ★" + if (g.capped) " " + stringResource(R.string.rel_cap) else "")
        }
    }
}

@Composable
private fun Signal(icon: String, title: String, body: String, v: Double?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(icon, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted)
            if (v != null) LinearProgressIndicator(
                progress = { v.toFloat() }, color = StarOn, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

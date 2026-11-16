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
fun RelayInfoSheet(c: AppContainer, relay: String, onClose: () -> Unit) {
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
    val unknown = stringResource(R.string.ri_unknown)
    fun yesNo(b: Boolean?) = when (b) { true -> ctx.getString(R.string.ri_required); false -> ctx.getString(R.string.ri_not_required); null -> unknown }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
            WarningLines(relayWarnings(info, st.rejections[relay], null))

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
                val todo = snap.missingAt(relay).size
                if (todo > 0) OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
                    busy = true
                    scope.launch {
                        c.explorer.syncRelay(relay)
                            .onSuccess { Toast.makeText(ctx, ctx.resources.getQuantityString(R.plurals.ri_synced_here, it, it), Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(ctx, errorText(ctx, it), Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text(stringResource(R.string.ri_sync_here, todo)) }
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

private enum class RelayFilter(val label: Int) { ALL(R.string.ri_f_all), PAID(R.string.ri_f_paid), AUTH(R.string.ri_f_auth), NODEL(R.string.ri_f_nodel), DOWN(R.string.ri_f_down) }

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
    val shown = all.filter { r ->
        val i = st.info[r]
        when (filter) {
            RelayFilter.ALL -> true
            RelayFilter.PAID -> i?.paid == true
            RelayFilter.AUTH -> i?.authRequired == true
            RelayFilter.NODEL -> i?.supportsDeletion == false
            RelayFilter.DOWN -> r in snap.relays && r !in snap.reached
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
                        Text(stringResource(role(snap, r, tansu)), color = Ts.colors.muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.size(6.dp))
                    RelayChips(info, snap.latency[r], !inAccount || r in snap.reached)
                    snap.stats(r)?.let { (cur, old, miss) ->
                        Text(stringResource(R.string.ri_stats, cur, old, miss), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
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

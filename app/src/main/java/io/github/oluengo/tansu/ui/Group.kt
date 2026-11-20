package io.github.oluengo.tansu.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.tansu.AppContainer
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.data.Account
import io.github.oluengo.tansu.data.AppSettings
import io.github.oluengo.tansu.data.Encoding
import io.github.oluengo.tansu.data.Explorer
import io.github.oluengo.tansu.data.Snapshot
import io.github.oluengo.tansu.data.detectEncoding
import io.github.oluengo.tansu.nostr.RelayPool
import io.github.oluengo.tansu.ui.common.Avatar
import io.github.oluengo.tansu.ui.common.accountName
import io.github.oluengo.tansu.ui.common.errorText
import io.github.oluengo.tansu.ui.common.size
import io.github.oluengo.tansu.ui.theme.Ts
import kotlinx.coroutines.launch

/**
 * Selección de varios datos (claves "e:<d>" para datos de apps y "l:<dirección>" para listas).
 * Se mantiene al entrar y salir de carpetas; [active] = modo selección.
 */
class Selection(val active: Boolean, val keys: Set<String>, private val set: (Boolean, Set<String>) -> Unit) {
    operator fun contains(k: String) = k in keys
    fun toggle(k: Collection<String>) { val all = k.isNotEmpty() && k.all { it in keys }; set(true, if (all) keys - k.toSet() else keys + k) }
    fun start(k: Collection<String>) = set(true, keys + k)
    fun exit() = set(false, emptySet())
    fun replace(k: Set<String>) = set(true, k)
}

internal fun entryKey(d: String) = "e:$d"
internal fun listKey(address: String) = "l:$address"

/** Los datos elegidos, con sus eventos, en el orden de la carga. */
internal fun picksOf(snap: Snapshot, keys: Set<String>, ctx: android.content.Context): List<Explorer.Pick> =
    snap.entries.filter { entryKey(it.d) in keys }.map { Explorer.Pick(entryKey(it.d), it.d, it.events) } +
        snap.lists.filter { listKey(it.address) in keys }.map { Explorer.Pick(listKey(it.address), listTitle(it, ctx), listOf(it)) }

@Composable
internal fun SelectionTopBar(sel: Selection, folderKeys: List<String>?) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().background(Ts.colors.accentSoft).padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = sel::exit) { Icon(Icons.Outlined.Close, stringResource(R.string.sel_exit)) }
        Text(
            ctx.resources.getQuantityString(R.plurals.sel_count, sel.keys.size, sel.keys.size),
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
        )
        if (!folderKeys.isNullOrEmpty()) {
            val all = folderKeys.all { it in sel }
            TextButton(onClick = { sel.toggle(folderKeys) }) { Text(stringResource(if (all) R.string.sel_none else R.string.sel_all)) }
        }
    }
}

private data class GroupResult(val delete: Boolean, val outcomes: List<Explorer.RelayOutcome>)

/** Barra de acciones del modo selección y todo lo que abre (relays, confirmación, otra cuenta, resultado). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupActionBar(c: AppContainer, account: Account, snap: Snapshot, sel: Selection) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    val profiles by c.profiles.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<String?>(null) }      // "copy" | "delete" | "account"
    var preset by remember { mutableStateOf<Set<String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    var target by remember { mutableStateOf<Account?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<GroupResult?>(null) }
    val picks = remember(snap, sel.keys) { picksOf(snap, sel.keys, ctx) }
    val empty = picks.isEmpty()

    fun fail(e: Throwable) = Toast.makeText(ctx, errorText(ctx, e), Toast.LENGTH_LONG).show()

    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Action(Icons.Outlined.CloudUpload, stringResource(R.string.g_copy), !empty) { preset = null; sheet = "copy" }
            Action(Icons.Outlined.CloudOff, stringResource(R.string.g_delete), !empty && account.canSign, Ts.colors.danger) { preset = null; sheet = "delete" }
            Action(Icons.Outlined.PersonAdd, stringResource(R.string.g_account), picks.any { it.key.startsWith("e:") } && acc.list.any { it.pubkey != account.pubkey && it.canSign }) { sheet = "account" }
            Column(Modifier.weight(1f)) {
                Action(Icons.Outlined.MoreHoriz, stringResource(R.string.g_more), true) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.g_select_everything)) }, onClick = {
                        menu = false
                        sel.replace((snap.entries.map { entryKey(it.d) } + snap.lists.map { listKey(it.address) }).toSet())
                    })
                    if (account.canSign) DropdownMenuItem(
                        enabled = !empty,
                        text = { Text(stringResource(R.string.g_delete_all), color = Ts.colors.danger) },
                        onClick = { menu = false; preset = snap.relays.toSet(); sheet = "delete" },
                    )
                }
            }
        }
    }

    when (sheet) {
        "copy", "delete" -> GroupRelaysSheet(
            c, snap, picks, delete = sheet == "delete", preset = preset,
            onClose = { sheet = null },
            onCopy = { relays ->
                sheet = null; busy = true
                scope.launch {
                    c.explorer.groupCopy(relays, picks).onSuccess { result = GroupResult(false, it) }.onFailure(::fail)
                    busy = false
                }
            },
            onDelete = { relays -> sheet = null; confirm = relays },
        )
        "account" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(stringResource(R.string.copy_to_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                val lists = picks.count { it.key.startsWith("l:") }
                if (lists > 0) Text(ctx.resources.getQuantityString(R.plurals.g_acc_note, lists, lists), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                acc.list.filter { it.pubkey != account.pubkey && it.canSign }.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sheet = null; target = a }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(a, profiles[a.pubkey], 36.dp)
                        Text(accountName(a, profiles[a.pubkey]), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    confirm?.let { relays ->
        val n = picks.size
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(ctx.resources.getQuantityString(R.plurals.g_del_title, n, n)) },
            text = { Text(stringResource(R.string.g_del_body, relays.joinToString(", ") { short(it) })) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null; busy = true
                    scope.launch {
                        c.explorer.groupDeleteFrom(relays, picks).onSuccess { result = GroupResult(true, it) }.onFailure(::fail)
                        busy = false
                    }
                }) { Text(stringResource(R.string.delete), color = Ts.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    target?.let { t ->
        val entries = snap.entries.filter { entryKey(it.d) in sel }
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(stringResource(R.string.copy_confirm_title, accountName(t, profiles[t.pubkey]))) },
            text = { Text(ctx.resources.getQuantityString(R.plurals.g_acc_body, entries.size, entries.size)) },
            confirmButton = {
                TextButton(onClick = {
                    target = null; busy = true
                    scope.launch {
                        c.explorer.groupCopyToAccount(entries, t)
                            .onSuccess { Toast.makeText(ctx, ctx.resources.getQuantityString(R.plurals.g_acc_done, it, it), Toast.LENGTH_SHORT).show(); sel.exit() }
                            .onFailure(::fail)
                        busy = false
                    }
                }) { Text(stringResource(R.string.copy)) }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    result?.let { r ->
        AlertDialog(
            onDismissRequest = { result = null; sel.exit() },
            title = { Text(stringResource(R.string.g_result)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.outcomes.forEach { o ->
                        val bad = o.failed > 0 || o.ignored > 0
                        val main = stringResource(if (r.delete) R.string.g_r_deleted else R.string.g_r_copied, short(o.relay), o.done)
                        val extra = listOfNotNull(
                            if (o.failed > 0) stringResource(R.string.g_r_failed, o.failed) + (o.reason?.let { " («${it.take(80)}»)" } ?: "") else null,
                            if (o.ignored > 0) stringResource(R.string.g_r_ignored, o.ignored) else null,
                        )
                        Text((if (bad) "⚠ " else "✓ ") + (listOf(main) + extra).joinToString(" · "), color = if (bad) Ts.colors.lock else Color.Unspecified, style = MaterialTheme.typography.bodyMedium)
                    }
                    r.outcomes.firstOrNull()?.skipped?.takeIf { it > 0 }?.let {
                        Text(ctx.resources.getQuantityString(R.plurals.g_r_skipped, it, it), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { result = null; sel.exit() }) { Text(stringResource(R.string.g_done)) } },
        )
    }

    if (busy) BusyDialog(c)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Action(icon: ImageVector, label: String, enabled: Boolean, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    ActionContent(Modifier.weight(1f), icon, label, enabled, tint, onClick)
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Action(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    ActionContent(Modifier.fillMaxWidth(), icon, label, enabled, Color.Unspecified, onClick)
}

@Composable
private fun ActionContent(modifier: Modifier, icon: ImageVector, label: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    val color = (if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint).let { if (enabled) it else it.copy(alpha = 0.35f) }
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * Elegir relays para copiar o borrar varios datos. Cada relay dice cuántos le faltan (copiar) o cuántos tiene (borrar).
 * Al copiar vienen marcados los que tienen algo pendiente, salvo los de 1–2★. Al borrar, nada (o todos, desde «Borrar de todos»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupRelaysSheet(
    c: AppContainer, snap: Snapshot, picks: List<Explorer.Pick>, delete: Boolean, preset: Set<String>?,
    onClose: () -> Unit, onCopy: (List<String>) -> Unit, onDelete: (List<String>) -> Unit,
) {
    val ctx = LocalContext.current
    val st by c.explorer.state.collectAsStateWithLifecycle()
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    var extra by remember { mutableStateOf(listOf<String>()) }
    val relays = (snap.relays + (s.appRelays ?: RelayPool.BUILTIN) + extra).distinct()
    val ok = picks.filter { p -> p.events.none { detectEncoding(it.content) == Encoding.PLAIN } }
    val plain = picks.size - ok.size
    fun count(r: String) = if (delete) picks.count { c.explorer.hasAt(r, it) != false } else ok.count { c.explorer.missingAt(r, it) }
    val counts = remember(st, picks, relays) { relays.associateWith(::count) }
    val ratings = remember(st, relays) { relays.associateWith { c.explorer.rating(it)?.stars } }
    var chosen by remember {
        mutableStateOf(preset ?: if (delete) emptySet() else relays.filter { (counts[it] ?: 0) > 0 && (ratings[it] ?: 3) >= 3 }.toSet())
    }
    var adding by remember { mutableStateOf(false) }
    val tgt = relays.filter { it in chosen }
    val work = tgt.sumOf { counts[it] ?: 0 }
    val orphans = if (delete) c.explorer.orphans(tgt, picks).size else 0
    val nRel = ctx.resources.getQuantityString(R.plurals.g_relays, tgt.size, tgt.size)

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(stringResource(if (delete) R.string.g_delete else R.string.g_copy), style = MaterialTheme.typography.titleLarge)
                Text(
                    ctx.resources.getQuantityString(R.plurals.sel_count, picks.size, picks.size) + " · " + size(ctx, picks.sumOf { p -> p.events.sumOf { it.content.length } }),
                    color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(8.dp))
                relays.forEach { r ->
                    val n = counts[r] ?: 0
                    val known = r in snap.reached
                    val enabled = n > 0
                    val sub = when {
                        !known && r in snap.relays -> stringResource(R.string.g_unknown)
                        delete -> if (n > 0) stringResource(R.string.g_has, n, picks.size) else stringResource(R.string.g_has_none)
                        else -> if (n > 0) stringResource(R.string.g_missing, n, picks.size) else stringResource(R.string.g_all_there)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled) { chosen = if (r in chosen) chosen - r else chosen + r }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = r in chosen && enabled, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(short(r), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (enabled) Color.Unspecified else Ts.colors.muted)
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ts.colors.muted)
                        }
                        Stars(ratings[r])
                    }
                }
                if (!delete) TextButton(onClick = { adding = true }) { Text(stringResource(R.string.g_other)) }
                val warnings = buildList {
                    if (!delete && plain > 0) add(ctx.resources.getQuantityString(R.plurals.g_w_plain, plain, plain))
                    if (!delete) tgt.filter { (ratings[it] ?: 3) <= 2 }.forEach { add(stringResource(R.string.g_w_low, short(it), ratings[it] ?: 0)) }
                    if (delete && orphans > 0) add(ctx.resources.getQuantityString(R.plurals.g_w_orphan, orphans, orphans))
                }
                warnings.forEach { w ->
                    Text(
                        "⚠ $w", style = MaterialTheme.typography.bodySmall, color = if (delete) Ts.colors.danger else Ts.colors.lock,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                            .background((if (delete) Ts.colors.danger.copy(alpha = 0.1f) else Ts.colors.lockSoft)).padding(10.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    if (tgt.isEmpty()) stringResource(R.string.g_pick_relays)
                    else stringResource(if (delete) R.string.g_del_sum else R.string.g_copy_sum, work, nRel),
                    color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = tgt.isNotEmpty() && work > 0,
                    onClick = { if (delete) onDelete(tgt) else onCopy(tgt) },
                    colors = if (delete) ButtonDefaults.buttonColors(containerColor = Ts.colors.danger) else ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp),
                ) { Text(stringResource(if (delete) R.string.g_del_go else R.string.g_copy_go, nRel)) }
            }
        }
    }

    if (adding) {
        var input by remember { mutableStateOf("") }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.ri_add_relay)) },
            text = {
                OutlinedTextField(
                    value = input, onValueChange = { input = it; invalid = false }, singleLine = true,
                    placeholder = { Text("wss://relay.example.com") }, isError = invalid,
                    supportingText = { if (invalid) Text(stringResource(R.string.relay_invalid)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = RelayPool.normalize(input)
                    if (r == null) invalid = true else { adding = false; extra = extra + r; chosen = chosen + r; c.explorer.askMonitors(listOf(r)) }
                }) { Text(stringResource(R.string.ri_add_relay)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

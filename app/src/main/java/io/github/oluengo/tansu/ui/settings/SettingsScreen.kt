package io.github.oluengo.tansu.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.tansu.AppContainer
import io.github.oluengo.tansu.BuildConfig
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.data.Accent
import io.github.oluengo.tansu.data.Account
import io.github.oluengo.tansu.data.AppLanguage
import io.github.oluengo.tansu.data.AppSettings
import io.github.oluengo.tansu.data.ThemeMode
import io.github.oluengo.tansu.data.TrashItem
import io.github.oluengo.tansu.nostr.Nip19
import io.github.oluengo.tansu.nostr.RelayPool
import io.github.oluengo.tansu.ui.BusyDialog
import io.github.oluengo.tansu.ui.common.AddAccountButtons
import io.github.oluengo.tansu.ui.common.Avatar
import io.github.oluengo.tansu.ui.common.Group
import io.github.oluengo.tansu.ui.common.SectionLabel
import io.github.oluengo.tansu.ui.common.accountName
import io.github.oluengo.tansu.ui.common.ago
import io.github.oluengo.tansu.ui.common.errorText
import io.github.oluengo.tansu.ui.common.methodLabel
import io.github.oluengo.tansu.ui.theme.Ts
import io.github.oluengo.tansu.ui.theme.accentSwatch
import kotlinx.coroutines.launch

/** Ajustes agrupados por bloques (como en Wavestr): un índice y cada sección en su pantalla. */
private enum class Section(@StringRes val title: Int, val icon: ImageVector) {
    ACCOUNTS(R.string.accounts, Icons.Outlined.People),
    APPEARANCE(R.string.settings_appearance, Icons.Outlined.Palette),
    RELAYS(R.string.settings_relays, Icons.Outlined.Hub),
    TRASH(R.string.trash, Icons.Outlined.DeleteOutline),
    ABOUT(R.string.settings_about, Icons.Outlined.Info),
}

@Composable
fun SettingsScreen(c: AppContainer, onBack: () -> Unit) {
    var section by rememberSaveable { mutableStateOf<Section?>(null) }
    BackHandler(enabled = section != null) { section = null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (section != null) section = null else onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(section?.title ?: R.string.settings), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (section) {
                null -> Index(c) { section = it }
                Section.ACCOUNTS -> AccountsSection(c)
                Section.APPEARANCE -> AppearanceSection(c)
                Section.RELAYS -> RelaysSection(c)
                Section.TRASH -> TrashSection(c)
                Section.ABOUT -> AboutSection()
            }
        }
    }
}

@Composable
private fun Index(c: AppContainer, onOpen: (Section) -> Unit) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val theme = stringResource(when (s.theme) { ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark; ThemeMode.SYSTEM -> R.string.theme_system })
    val lang = when (AppLanguage.current()) { "ca" -> "Català"; "es" -> "Español"; "en" -> "English"; else -> stringResource(R.string.lang_system) }
    val trashCount = remember { c.explorer.trash.all().size }
    fun summary(sec: Section): String = when (sec) {
        Section.ACCOUNTS -> ctx.resources.getQuantityString(R.plurals.accounts_count, acc.list.size, acc.list.size)
        Section.APPEARANCE -> "$theme · ${ctx.getString(accentLabel(s.accent))} · $lang"
        Section.RELAYS -> ctx.resources.getQuantityString(R.plurals.relays_count, (s.appRelays ?: RelayPool.BUILTIN).size, (s.appRelays ?: RelayPool.BUILTIN).size)
        Section.TRASH -> ctx.resources.getQuantityString(R.plurals.trash_count, trashCount, trashCount)
        Section.ABOUT -> ctx.getString(R.string.version, versionLabel)
    }
    listOf(listOf(Section.ACCOUNTS), listOf(Section.APPEARANCE, Section.RELAYS), listOf(Section.TRASH), listOf(Section.ABOUT)).forEach { block ->
        Group {
            block.forEachIndexed { i, sec ->
                if (i > 0) HorizontalDivider(color = Ts.colors.line)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(sec) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(38.dp).background(Ts.colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(sec.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(sec.title), fontWeight = FontWeight.SemiBold)
                        Text(summary(sec), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Ts.colors.muted)
                }
            }
        }
    }
}

// ---------- Cuentas ----------

@Composable
private fun AccountsSection(c: AppContainer) {
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    val profiles by c.profiles.state.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Account?>(null) }
    var renaming by remember { mutableStateOf<Account?>(null) }
    Group {
        acc.list.forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(color = Ts.colors.line)
            Row(Modifier.fillMaxWidth().clickable { c.accounts.setActive(a.pubkey) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(a, profiles[a.pubkey], 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(accountName(a, profiles[a.pubkey]) + if (a.pubkey == acc.current?.pubkey) " ✓" else "", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Nip19.shortNpub(a.pubkey) + " · " + stringResource(methodLabel(a.method)), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Row {
                        TextButton(onClick = { renaming = a }) { Text(stringResource(R.string.rename)) }
                        TextButton(onClick = { removing = a }) { Text(stringResource(R.string.remove), color = Ts.colors.danger) }
                    }
                }
            }
        }
    }
    SectionLabel(stringResource(R.string.account_add))
    AddAccountButtons(c)
    Text(stringResource(R.string.accounts_note), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)

    removing?.let { a ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_account_title, accountName(a, profiles[a.pubkey]))) },
            text = { Text(stringResource(R.string.remove_account_body)) },
            confirmButton = { TextButton(onClick = { c.removeAccount(a.pubkey); removing = null }) { Text(stringResource(R.string.remove), color = Ts.colors.danger) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    renaming?.let { a ->
        var name by remember(a.pubkey) { mutableStateOf(a.label ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text(profiles[a.pubkey]?.name ?: Nip19.shortNpub(a.pubkey)) })
            },
            confirmButton = { TextButton(onClick = { c.accounts.rename(a.pubkey, name); renaming = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------- Apariencia e idioma ----------

private fun accentLabel(a: Accent): Int = when (a) {
    Accent.INDIGO -> R.string.accent_indigo
    Accent.VIOLET -> R.string.accent_violet
    Accent.GREEN -> R.string.accent_green
    Accent.CORAL -> R.string.accent_coral
    Accent.RASPBERRY -> R.string.accent_raspberry
}

@Composable
private fun AppearanceSection(c: AppContainer) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    Group {
        Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.SemiBold)
        val themes = listOf(ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark, ThemeMode.SYSTEM to R.string.theme_system)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            themes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(selected = s.theme == mode, onClick = { scope.launch { c.settings.setTheme(mode) } }, shape = SegmentedButtonDefaults.itemShape(i, themes.size)) { Text(stringResource(label)) }
            }
        }
    }
    Group {
        Text(stringResource(R.string.settings_accent), fontWeight = FontWeight.SemiBold)
        Text(stringResource(accentLabel(s.accent)), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
            Accent.entries.forEach { a ->
                val name = stringResource(accentLabel(a))
                Box(
                    Modifier.size(34.dp)
                        .border(if (a == s.accent) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .padding(if (a == s.accent) 5.dp else 0.dp)
                        .background(accentSwatch(a), CircleShape)
                        .selectable(selected = a == s.accent, role = Role.RadioButton, onClick = { scope.launch { c.settings.setAccent(a) } })
                        .semantics { contentDescription = name },
                )
            }
        }
    }
    SectionLabel(stringResource(R.string.settings_language))
    var current by remember { mutableStateOf(AppLanguage.current()) }
    Group {
        listOf<Pair<String?, String>>(null to stringResource(R.string.lang_system), "ca" to "Català", "es" to "Español", "en" to "English").forEach { (tag, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = current == tag, role = Role.RadioButton, onClick = { current = tag; AppLanguage.set(tag) }).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == tag, onClick = null)
                Text(label, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text(stringResource(R.string.lang_note), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------- Relays ----------

@Composable
private fun RelaysSection(c: AppContainer) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val st by c.explorer.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val mine = s.appRelays ?: RelayPool.BUILTIN
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    fun save(list: List<String>) = scope.launch { c.settings.setAppRelays(if (list == RelayPool.BUILTIN) null else list) }
    fun add() {
        val r = RelayPool.normalize(input) ?: run { invalid = true; return }
        if (r !in mine) save(mine + r)
        input = ""; invalid = false
    }
    Group {
        Text(stringResource(R.string.relays_app), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.relays_app_sub), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
        mine.forEach { url ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { save(mine - url) }, enabled = mine.size > 1) { Icon(Icons.Outlined.Close, stringResource(R.string.relay_remove, url)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it; invalid = false },
                placeholder = { Text("wss://relay.example.com") }, singleLine = true, isError = invalid,
                supportingText = { if (invalid) Text(stringResource(R.string.relay_invalid)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { add() }, enabled = input.isNotBlank()) { Text(stringResource(R.string.add)) }
        }
        if (s.appRelays != null) TextButton(onClick = { save(RelayPool.BUILTIN) }) { Text(stringResource(R.string.relays_restore)) }
    }
    st.snapshot?.let { snap ->
        Group {
            Text(stringResource(R.string.relays_account), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.relays_account_sub), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
            snap.relays.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

// ---------- Papelera ----------

@Composable
private fun TrashSection(c: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    val profiles by c.profiles.state.collectAsStateWithLifecycle()
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { c.explorer.trash.all() }
    var busy by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<TrashItem?>(null) }
    var clearing by remember { mutableStateOf(false) }

    Text(stringResource(R.string.trash_note), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
    if (items.isEmpty()) {
        Text(stringResource(R.string.trash_empty), color = Ts.colors.muted, modifier = Modifier.padding(vertical = 16.dp))
        return
    }
    Group {
        items.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = Ts.colors.line)
            val owner = acc.list.firstOrNull { it.pubkey == t.pubkey }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.d, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (owner?.let { accountName(it, profiles[it.pubkey]) } ?: Nip19.shortNpub(t.pubkey)) + " · " + ago(ctx, t.deletedAt / 1000),
                        color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    )
                }
                TextButton(onClick = { restoring = t }, enabled = owner?.canSign == true) { Text(stringResource(R.string.restore)) }
                IconButton(onClick = { c.explorer.trash.remove(t.id); version++ }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove)) }
            }
        }
    }
    OutlinedButton(onClick = { clearing = true }) { Text(stringResource(R.string.trash_clear)) }

    restoring?.let { t ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(stringResource(R.string.restore_title, t.d)) },
            text = { Text(stringResource(R.string.restore_body)) },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null; busy = true
                    scope.launch {
                        c.explorer.restore(t)
                            .onSuccess { Toast.makeText(ctx, R.string.restored, Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(ctx, errorText(ctx, it), Toast.LENGTH_LONG).show() }
                        busy = false; version++
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (clearing) AlertDialog(
        onDismissRequest = { clearing = false },
        title = { Text(stringResource(R.string.trash_clear)) },
        text = { Text(stringResource(R.string.trash_clear_body)) },
        confirmButton = { TextButton(onClick = { c.explorer.trash.clear(); clearing = false; version++ }) { Text(stringResource(R.string.trash_clear), color = Ts.colors.danger) } },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (busy) BusyDialog(c)
}

val versionLabel: String get() = BuildConfig.VERSION_NAME.substringBefore("-beta") + if (BuildConfig.BETA) " beta" else ""

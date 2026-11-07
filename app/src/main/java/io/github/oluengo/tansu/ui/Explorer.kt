package io.github.oluengo.tansu.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.tansu.AppContainer
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.data.Account
import io.github.oluengo.tansu.data.Encoding
import io.github.oluengo.tansu.data.Entry
import io.github.oluengo.tansu.data.Explorer
import io.github.oluengo.tansu.data.Lists
import io.github.oluengo.tansu.data.Snapshot
import io.github.oluengo.tansu.data.Vault
import io.github.oluengo.tansu.data.detectEncoding
import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.NostrJson
import io.github.oluengo.tansu.ui.common.AppLogo
import io.github.oluengo.tansu.ui.common.Avatar
import io.github.oluengo.tansu.ui.common.Badge
import io.github.oluengo.tansu.ui.common.EmptyNote
import io.github.oluengo.tansu.ui.common.Group
import io.github.oluengo.tansu.ui.common.InfoRow
import io.github.oluengo.tansu.ui.common.accountName
import io.github.oluengo.tansu.ui.common.ago
import io.github.oluengo.tansu.ui.common.appName
import io.github.oluengo.tansu.ui.common.errorText
import io.github.oluengo.tansu.ui.common.fullDate
import io.github.oluengo.tansu.ui.common.size
import io.github.oluengo.tansu.ui.theme.Ts
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val Pretty = Json { prettyPrint = true }

private fun pretty(text: String): String =
    runCatching { Pretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), NostrJson.parseToJsonElement(text)) }.getOrDefault(text)

@Composable
fun ExplorerScreen(
    c: AppContainer, route: Route, go: (Route) -> Unit, back: () -> Unit,
    onAccounts: () -> Unit, onSettings: () -> Unit,
) {
    val st by c.explorer.state.collectAsStateWithLifecycle()
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    val profiles by c.profiles.state.collectAsStateWithLifecycle()
    val account = acc.current ?: return

    Column(Modifier.fillMaxSize()) {
        TopBar(route, account, profiles[account.pubkey], st.snapshot, back, onAccounts, onSettings)
        val snap = st.snapshot
        when {
            snap == null && st.load is Explorer.Load.Failed -> Failed { c.explorer.refresh() }
            snap == null -> Loading()
            else -> when (route) {
                Route.Home -> Home(c, snap, st.refreshing, go)
                is Route.Folder -> Folder(c, snap, route.app, st.refreshing, go)
                Route.Lists -> ListsFolder(c, snap, st.refreshing, go)
                is Route.Item -> snap.entries.firstOrNull { it.d == route.d }?.let { ItemScreen(c, account, it, go, back) } ?: Gone()
                is Route.Edit -> snap.entries.firstOrNull { it.d == route.d }?.let { EditScreen(c, it, back) } ?: Gone()
                is Route.ListItem -> snap.lists.firstOrNull { it.address == route.address }?.let { ListScreen(c, account, it) } ?: Gone()
                Route.Settings -> Unit
            }
        }
    }
}

// ---------- Cabecera ----------

@Composable
private fun TopBar(
    route: Route, account: Account, profile: io.github.oluengo.tansu.data.Profile?, snap: Snapshot?,
    back: () -> Unit, onAccounts: () -> Unit, onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (route == Route.Home) 16.dp else 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (route == Route.Home) {
            AppLogo(32.dp)
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        } else {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(title(route, snap), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAccounts).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Avatar(account, profile, 30.dp)
            if (route == Route.Home) Text(
                accountName(account, profile), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 6.dp).widthIn(max = 110.dp),
            )
        }
        if (route == Route.Home) IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
    }
}

@Composable
private fun title(route: Route, snap: Snapshot?): String = when (route) {
    is Route.Folder -> appName(route.app)
    Route.Lists -> stringResource(R.string.folder_lists)
    is Route.Item -> snap?.entries?.firstOrNull { it.d == route.d }?.name ?: route.d
    is Route.Edit -> stringResource(R.string.edit)
    is Route.ListItem -> snap?.lists?.firstOrNull { it.address == route.address }?.let { listTitle(it) } ?: ""
    else -> stringResource(R.string.app_name)
}

// ---------- Estados ----------

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.loading), color = Ts.colors.muted)
        }
    }
}

@Composable
private fun Failed(retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.load_failed), color = Ts.colors.muted)
        Spacer(Modifier.size(12.dp))
        OutlinedButton(onClick = retry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun Gone() { EmptyNote(stringResource(R.string.item_gone), Modifier.padding(horizontal = 20.dp)) }

// ---------- Inicio: cajones ----------

@Composable
private fun Home(c: AppContainer, snap: Snapshot, refreshing: Boolean, go: (Route) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val groups = snap.entries.groupBy { it.app }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { c.explorer.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) } },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            if (q.isNotBlank()) {
                val needle = q.trim().lowercase()
                val es = snap.entries.filter { needle in it.d.lowercase() }
                val ls = snap.lists.filter { needle in (listTitle(it, ctx) + " " + it.kind).lowercase() }
                item { Section(ctx.resources.getQuantityString(R.plurals.results, es.size + ls.size, es.size + ls.size)) }
                items(es, key = { "e:" + it.d }) { EntryRow(it, showApp = true) { go(Route.Item(it.d)) } }
                items(ls, key = { "l:" + it.address }) { ListRow(it) { go(Route.ListItem(it.address)) } }
                if (es.isEmpty() && ls.isEmpty()) item { EmptyNote(stringResource(R.string.search_none), Modifier.padding(horizontal = 12.dp)) }
                return@LazyColumn
            }
            item { Section(stringResource(R.string.drawer_apps)) }
            if (groups.isEmpty()) item { EmptyNote(stringResource(R.string.apps_empty), Modifier.padding(horizontal = 12.dp)) }
            items(groups.keys.toList(), key = { "f:$it" }) { app ->
                val list = groups.getValue(app)
                FolderRow(appName(app), ctx.resources.getQuantityString(R.plurals.items, list.size, list.size) + " · " + ago(ctx, list.maxOf { it.updatedAt }), Icons.Outlined.Folder) {
                    go(Route.Folder(app))
                }
            }
            item { Section(stringResource(R.string.drawer_lists)) }
            item {
                FolderRow(
                    stringResource(R.string.folder_lists),
                    ctx.resources.getQuantityString(R.plurals.items, snap.lists.size, snap.lists.size) + " · " + stringResource(R.string.lists_readonly),
                    Icons.AutoMirrored.Outlined.List,
                ) { go(Route.Lists) }
            }
            item {
                Text(
                    stringResource(R.string.relays_used, snap.relays.size) + " · " + stringResource(R.string.privacy_short),
                    color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ts.colors.muted, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun RowIcon(icon: ImageVector, folder: Boolean) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(if (folder) Ts.colors.folderSoft else Ts.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (folder) Ts.colors.folder else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun FolderRow(name: String, meta: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowIcon(icon, folder = true)
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Ts.colors.muted)
    }
}

@Composable
private fun EncodingBadge(enc: Encoding) {
    when (enc) {
        Encoding.NIP44 -> Badge("NIP-44", Ts.colors.lock, Ts.colors.lockSoft)
        Encoding.NIP04 -> Badge("NIP-04", Ts.colors.lock, Ts.colors.lockSoft)
        Encoding.PLAIN -> Badge(stringResource(R.string.badge_plain), Ts.colors.danger, Ts.colors.danger.copy(alpha = 0.12f))
        Encoding.EMPTY -> Badge(stringResource(R.string.badge_empty))
    }
}

@Composable
private fun EntryRow(e: Entry, showApp: Boolean = false, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val enc = e.encoding
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowIcon(if (enc == Encoding.NIP44 || enc == Encoding.NIP04) Icons.Outlined.Lock else Icons.Outlined.Description, folder = false)
        Column(Modifier.weight(1f)) {
            Text(if (showApp) e.d else e.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = if (e.chunked) " · " + ctx.resources.getQuantityString(R.plurals.parts, e.parts.size, e.parts.size) else ""
            Text(ago(ctx, e.updatedAt) + " · " + size(ctx, e.bytes) + parts, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        EncodingBadge(enc)
    }
}

// ---------- Carpeta de una app ----------

@Composable
private fun Folder(c: AppContainer, snap: Snapshot, app: String, refreshing: Boolean, go: (Route) -> Unit) {
    val list = snap.entries.filter { it.app == app }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { c.explorer.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            if (list.isEmpty()) item { EmptyNote(stringResource(R.string.folder_empty), Modifier.padding(horizontal = 12.dp)) }
            items(list, key = { it.d }) { e -> EntryRow(e) { go(Route.Item(e.d)) } }
        }
    }
}

// ---------- Listas NIP-51 ----------

private fun listKey(e: Event): String = Lists.KINDS[e.kind] ?: "other"

private fun listTitle(e: Event, ctx: android.content.Context): String {
    val res = ctx.resources.getIdentifier("list_" + listKey(e), "string", ctx.packageName)
    val kind = if (res != 0) ctx.getString(res) else "kind ${e.kind}"
    return Lists.title(e)?.let { "$it · $kind" } ?: kind
}

@Composable
private fun listTitle(e: Event): String = listTitle(e, LocalContext.current)

@Composable
private fun ListRow(e: Event, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowIcon(Icons.AutoMirrored.Outlined.List, folder = false)
        Column(Modifier.weight(1f)) {
            Text(listTitle(e), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "kind ${e.kind} · " + ctx.resources.getQuantityString(R.plurals.public_items, Lists.publicItems(e).size, Lists.publicItems(e).size) + " · " + ago(ctx, e.createdAt),
                color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
        }
        if (Lists.hasPrivate(e)) EncodingBadge(detectEncoding(e.content))
    }
}

@Composable
private fun ListsFolder(c: AppContainer, snap: Snapshot, refreshing: Boolean, go: (Route) -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { c.explorer.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            item { Text(stringResource(R.string.lists_note), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
            if (snap.lists.isEmpty()) item { EmptyNote(stringResource(R.string.lists_empty), Modifier.padding(horizontal = 12.dp)) }
            items(snap.lists, key = { it.address }) { e -> ListRow(e) { go(Route.ListItem(e.address)) } }
        }
    }
}

@Composable
private fun TagRow(t: List<String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            t[0], fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ts.colors.accentSoft).padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(t[1], style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (t.size > 2) Text(t.drop(2).joinToString(" · "), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ListScreen(c: AppContainer, account: Account, e: Event) {
    val st by c.explorer.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val priv = st.listPrivate[e.address]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Group {
            InfoRow("kind", "${e.kind}")
            e.tag("d")?.takeIf { it.isNotEmpty() }?.let { InfoRow("d", it) }
            InfoRow(stringResource(R.string.info_updated), fullDate(e.createdAt))
        }
        Group {
            Text(stringResource(R.string.list_public, Lists.publicItems(e).size), fontWeight = FontWeight.SemiBold)
            val pub = Lists.publicItems(e)
            if (pub.isEmpty()) Text(stringResource(R.string.none), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
            pub.forEach { TagRow(it) }
        }
        if (Lists.hasPrivate(e)) Group {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = Ts.colors.lock, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.list_private), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (priv != null) Badge(priv.size.toString())
            }
            when {
                priv != null -> if (priv.isEmpty()) Text(stringResource(R.string.none), color = Ts.colors.muted) else priv.forEach { TagRow(it) }
                !account.canSign -> Text(stringResource(R.string.readonly_cannot_decrypt), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
                else -> Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        c.explorer.openList(e).onFailure { Toast.makeText(ctx, errorText(ctx, it), Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.decrypt)) }
            }
        }
        Text(stringResource(R.string.lists_readonly_note), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------- Archivo (dato de app) ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemScreen(c: AppContainer, account: Account, e: Entry, go: (Route) -> Unit, back: () -> Unit) {
    val st by c.explorer.state.collectAsStateWithLifecycle()
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    val profiles by c.profiles.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val opened = st.opened[e.d]
    val enc = e.encoding
    var busy by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var copyPick by remember { mutableStateOf(false) }
    var copyTarget by remember { mutableStateOf<Account?>(null) }
    val plainText = if (enc == Encoding.PLAIN || enc == Encoding.EMPTY) e.main.content.takeIf { !e.chunked } else null
    val text = opened?.text ?: plainText

    fun fail(t: Throwable) = Toast.makeText(ctx, errorText(ctx, t), Toast.LENGTH_LONG).show()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val t = text ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(t.toByteArray()) } }.isSuccess
            Toast.makeText(ctx, if (ok) R.string.exported else R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EncodingBadge(enc)
            Badge(appName(e.app))
            if (e.chunked) Badge(ctx.resources.getQuantityString(R.plurals.parts, e.parts.size, e.parts.size))
            Badge(size(ctx, e.bytes))
        }
        Group {
            InfoRow("d", e.d)
            InfoRow("kind", "30078 · NIP-78")
            InfoRow(stringResource(R.string.info_updated), fullDate(e.updatedAt))
            opened?.manifest?.let { m ->
                Vault.manifestInfo(m).filterKeys { it in setOf("app", "profile", "v") }.forEach { (k, v) -> InfoRow(k, v) }
            }
        }
        if (enc == Encoding.PLAIN) Text(stringResource(R.string.plain_warning), color = Ts.colors.danger, style = MaterialTheme.typography.bodySmall)

        Group(padding = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.content), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (text != null) Text(size(ctx, text.toByteArray().size), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = Ts.colors.line)
            when {
                text != null -> {
                    val shown = remember(text) { pretty(text) }
                    val cut = !showAll && shown.length > PREVIEW
                    SelectionContainer {
                        Text(
                            if (cut) shown.take(PREVIEW) + "\n…" else shown,
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                        )
                    }
                    if (cut) TextButton(onClick = { showAll = true }, modifier = Modifier.padding(start = 4.dp)) { Text(stringResource(R.string.show_all)) }
                }
                !account.canSign -> Text(stringResource(R.string.readonly_cannot_decrypt), color = Ts.colors.muted, modifier = Modifier.padding(16.dp))
                else -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.encrypted_for_you), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(10.dp))
                    Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch { c.explorer.open(e).onFailure(::fail); busy = false }
                    }) { Text(stringResource(R.string.decrypt)) }
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (opened != null && enc == Encoding.NIP44 && account.canSign) OutlinedButton(onClick = { go(Route.Edit(e.d)) }) { Text(stringResource(R.string.edit)) }
            if (text != null) OutlinedButton(onClick = { export.launch(e.d.replace('/', '-') + if (opened?.isJson != false) ".json" else ".txt") }) { Text(stringResource(R.string.export)) }
            if (opened != null && acc.list.any { it.pubkey != account.pubkey && it.canSign }) OutlinedButton(onClick = { copyPick = true }) { Text(stringResource(R.string.copy_to)) }
            if (account.canSign) OutlinedButton(onClick = { askDelete = true }) { Text(stringResource(R.string.delete), color = Ts.colors.danger) }
        }
        if (enc != Encoding.NIP44 && enc != Encoding.EMPTY) Text(stringResource(R.string.edit_only_nip44), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
    }

    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text(stringResource(R.string.delete_title, e.d)) },
        text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = {
            TextButton(onClick = {
                askDelete = false; busy = true
                scope.launch {
                    c.explorer.delete(e).onSuccess { Toast.makeText(ctx, R.string.deleted, Toast.LENGTH_SHORT).show(); back() }.onFailure(::fail)
                    busy = false
                }
            }) { Text(stringResource(R.string.delete), color = Ts.colors.danger) }
        },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )

    if (copyPick) ModalBottomSheet(onDismissRequest = { copyPick = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.copy_to_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            acc.list.filter { it.pubkey != account.pubkey && it.canSign }.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { copyPick = false; copyTarget = a }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(a, profiles[a.pubkey], 36.dp)
                    Text(accountName(a, profiles[a.pubkey]), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    copyTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { copyTarget = null },
            title = { Text(stringResource(R.string.copy_confirm_title, accountName(t, profiles[t.pubkey]))) },
            text = { Text(stringResource(R.string.copy_confirm_body, e.d)) },
            confirmButton = {
                TextButton(onClick = {
                    copyTarget = null; busy = true
                    scope.launch {
                        c.explorer.copyTo(e, t).onSuccess { Toast.makeText(ctx, R.string.copied_to_account, Toast.LENGTH_SHORT).show() }.onFailure(::fail)
                        busy = false
                    }
                }) { Text(stringResource(R.string.copy)) }
            },
            dismissButton = { TextButton(onClick = { copyTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (busy) BusyDialog(c)
}

private const val PREVIEW = 20_000

/** Mientras se espera al firmante o a los relays. */
@Composable
fun BusyDialog(c: AppContainer) {
    val p by c.explorer.progress.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text(p?.let { stringResource(R.string.busy_step, it.step, it.total) } ?: stringResource(R.string.busy_signer))
            }
        },
    )
}

// ---------- Editar ----------

@Composable
private fun EditScreen(c: AppContainer, e: Entry, back: () -> Unit) {
    val st by c.explorer.state.collectAsStateWithLifecycle()
    val opened = st.opened[e.d]
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    if (opened == null) { Gone(); return }
    // Sin rememberSaveable: una copia grande no cabe en el estado guardado de Android.
    var text by remember(e.d) { mutableStateOf(pretty(opened.text)) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val badJson = stringResource(R.string.json_invalid)

    fun prepared(): String? {
        if (!opened.isJson) return text
        return runCatching { NostrJson.parseToJsonElement(text).toString() }.getOrElse { error = badJson + " " + (it.message ?: ""); null }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(e.d, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        OutlinedTextField(
            value = text, onValueChange = { text = it; error = null },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            isError = error != null, supportingText = { error?.let { Text(it, maxLines = 3) } },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = back) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { if (prepared() != null) confirm = true }) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.save_title)) },
        text = {
            Text(
                stringResource(R.string.save_body) + if (e.chunked) "\n\n" + stringResource(R.string.save_body_parts) else ""
            )
        },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                val t = prepared() ?: return@TextButton
                busy = true
                scope.launch {
                    c.explorer.save(e, t)
                        .onSuccess { Toast.makeText(ctx, R.string.saved, Toast.LENGTH_SHORT).show(); back() }
                        .onFailure { Toast.makeText(ctx, errorText(ctx, it), Toast.LENGTH_LONG).show() }
                    busy = false
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (busy) BusyDialog(c)
}

package io.github.oluengo.tansu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.tansu.AppContainer
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.nostr.Nip19
import io.github.oluengo.tansu.ui.common.AddAccountButtons
import io.github.oluengo.tansu.ui.common.AppLogo
import io.github.oluengo.tansu.ui.common.Avatar
import io.github.oluengo.tansu.ui.common.SectionLabel
import io.github.oluengo.tansu.ui.common.SignerHost
import io.github.oluengo.tansu.ui.common.accountName
import io.github.oluengo.tansu.ui.common.methodLabel
import io.github.oluengo.tansu.ui.settings.SettingsScreen
import io.github.oluengo.tansu.ui.theme.Ts

/** Pantallas. El explorador navega como un gestor de archivos: carpetas → archivo → editar. */
sealed interface Route {
    data object Home : Route
    data class Folder(val app: String) : Route
    data object Lists : Route
    data class Item(val d: String) : Route
    data class Edit(val d: String) : Route
    data class ListItem(val address: String) : Route
    data object Settings : Route
}

@Composable
fun TansuRoot(c: AppContainer) {
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    SignerHost(c)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (acc.list.isEmpty()) Welcome(c) else Main(c)
        }
    }
}

@Composable
private fun Main(c: AppContainer) {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    // Al cambiar de cuenta se vuelve al inicio (lo abierto era de la otra cuenta).
    LaunchedEffect(acc.current?.pubkey) { if (stack.lastOrNull() != Route.Settings) { stack.clear(); stack.add(Route.Home) } }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    val go: (Route) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    var switcher by remember { mutableStateOf(false) }

    when (val r = stack.last()) {
        Route.Settings -> SettingsScreen(c, onBack = back)
        else -> ExplorerScreen(c, r, go, back, onAccounts = { switcher = true }, onSettings = { go(Route.Settings) })
    }
    if (switcher) AccountSwitcher(c) { switcher = false }
}

@Composable
private fun Welcome(c: AppContainer) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppLogo(96.dp)
        Spacer(Modifier.size(18.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.welcome_body), color = Ts.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.privacy_note), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.size(24.dp))
        AddAccountButtons(c, Modifier.widthIn(max = 420.dp).fillMaxWidth())
    }
}

/** Cambiar de cuenta, o añadir otra. */
@Composable
fun AccountSwitcher(c: AppContainer, onClose: () -> Unit) {
    val acc by c.accounts.state.collectAsStateWithLifecycle()
    val profiles by c.profiles.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text(stringResource(R.string.accounts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            acc.list.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { c.accounts.setActive(a.pubkey); onClose() }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(a, profiles[a.pubkey], 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(accountName(a, profiles[a.pubkey]), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Nip19.shortNpub(a.pubkey) + " · " + stringResource(methodLabel(a.method)),
                            color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (a.pubkey == acc.current?.pubkey) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            SectionLabel(stringResource(R.string.account_add))
            if (adding) AddAccountButtons(c, onAdded = onClose)
            else Text(
                "+ " + stringResource(R.string.account_add),
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { adding = true }.padding(vertical = 8.dp),
            )
        }
    }
}

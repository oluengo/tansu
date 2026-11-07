package io.github.oluengo.tansu.ui.common

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.tansu.AppContainer
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.data.Account
import io.github.oluengo.tansu.data.Method
import io.github.oluengo.tansu.nostr.Amber
import io.github.oluengo.tansu.nostr.Nip19
import io.github.oluengo.tansu.nostr.Signer
import kotlinx.coroutines.launch

/** Añadir una cuenta: Amber (NIP-55), bunker (NIP-46) o npub en solo lectura. Tansu nunca ve la nsec. */
@Composable
fun AddAccountButtons(c: AppContainer, modifier: Modifier = Modifier, onAdded: () -> Unit = {}) {
    val ctx = LocalContext.current
    var askNpub by remember { mutableStateOf(false) }
    var askBunker by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.amber_failed)
    val missing = stringResource(R.string.amber_missing)

    val amber = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val login = Amber.parseLogin(res.data)
        if (login != null) {
            c.accounts.add(Account(login.pubkeyHex, Method.AMBER, login.signerPackage))
            onAdded()
        } else {
            Toast.makeText(ctx, failed, Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { if (Amber.isInstalled(ctx)) amber.launch(Amber.loginIntent()) else Toast.makeText(ctx, missing, Toast.LENGTH_LONG).show() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.login_amber)) }
        OutlinedButton(onClick = { askBunker = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_bunker)) }
        OutlinedButton(onClick = { askNpub = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_npub)) }
    }

    if (askBunker) BunkerDialog(c) { pk ->
        askBunker = false
        if (pk != null) { c.accounts.add(Account(pk, Method.BUNKER)); onAdded() }
    }
    if (askNpub) NpubDialog(onDismiss = { askNpub = false }) { hex ->
        askNpub = false
        c.accounts.add(Account(hex, Method.READ_ONLY)); onAdded()
    }
}

@Composable
private fun NpubDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_npub)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.npub_explain))
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = false },
                    placeholder = { Text("npub1…") }, singleLine = true, isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.npub_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { Nip19.normalizePubkey(text)?.let(onSave) ?: run { error = true } }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Pegar el enlace `bunker://…` y conectar (puede pedir aprobarlo en la web del firmante). */
@Composable
private fun BunkerDialog(c: AppContainer, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bad = stringResource(R.string.bunker_bad_uri)
    val failed = stringResource(R.string.bunker_failed)
    AlertDialog(
        onDismissRequest = { if (!working) onDone(null) },
        title = { Text(stringResource(R.string.login_bunker)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.bunker_explain))
                OutlinedTextField(
                    value = text, onValueChange = { text = it.trim(); error = null },
                    placeholder = { Text("bunker://…") }, singleLine = true, enabled = !working, isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    trailingIcon = {
                        TextButton(onClick = { clipboard.getText()?.text?.let { text = it.trim(); error = null } }, enabled = !working) {
                            Text(stringResource(R.string.paste))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (working) Text(stringResource(R.string.bunker_connecting), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !working && text.isNotBlank(), onClick = {
                working = true
                scope.launch {
                    when (val r = c.signer.connectBunker(text)) {
                        is Signer.BunkerResult.Ok -> onDone(r.pubkey)
                        Signer.BunkerResult.BadUri -> error = bad
                        is Signer.BunkerResult.Failed -> error = failed + (r.message?.let { " ($it)" } ?: "")
                    }
                    working = false
                }
            }) { Text(stringResource(R.string.bunker_connect)) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }, enabled = !working) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Abre Amber cuando una operación necesita su pantalla, y la web del bunker si pide aprobar. */
@Composable
fun SignerHost(c: AppContainer) {
    val call by c.signer.pending.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res -> call?.complete(res.data) }
    LaunchedEffect(call) {
        val cl = call ?: return@LaunchedEffect
        runCatching { launcher.launch(cl.intent) }.onFailure { cl.complete(null) }
    }
    val auth by c.signer.authUrl.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(auth) {
        val url = auth?.takeIf { it.startsWith("https://") } ?: return@LaunchedEffect
        runCatching {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        c.signer.authUrlShown()
    }
}

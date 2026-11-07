package io.github.oluengo.tansu

import android.app.Application
import android.content.Context
import io.github.oluengo.tansu.data.AccountsRepository
import io.github.oluengo.tansu.data.Explorer
import io.github.oluengo.tansu.data.SettingsRepository
import io.github.oluengo.tansu.data.Trash
import io.github.oluengo.tansu.data.Vault
import io.github.oluengo.tansu.nostr.RelayPool
import io.github.oluengo.tansu.nostr.Signer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class TansuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Tema y relays guardados antes de la primera Activity (evita un parpadeo).
        val s = runBlocking { container.settings.current() }
        SettingsRepository.applyNightMode(s.theme)
        RelayPool.DEFAULT = s.appRelays ?: RelayPool.BUILTIN
        container.start()
    }
}

/** Dependencias de la app, sin framework de inyección (como en Wavestr). */
class AppContainer(app: Application) {
    val appContext: Context = app
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    val settings = SettingsRepository(app)
    val accounts = AccountsRepository(app)
    val relays = RelayPool(http)
    val signer = Signer(app, relays)
    val vault = Vault(relays)
    val profiles = io.github.oluengo.tansu.data.Profiles(vault, accounts, appScope)
    val explorer = Explorer(accounts, signer, vault, Trash(File(app.filesDir, "trash.json")), appScope)

    fun start() {
        explorer.start()
        profiles.start()
        // Relays de Tansu cambiados en Ajustes: se aplican al momento y se recarga.
        appScope.launch {
            settings.flow.map { it.appRelays }.distinctUntilChanged().collect { list ->
                val next = list ?: RelayPool.BUILTIN
                if (next != RelayPool.DEFAULT) { RelayPool.DEFAULT = next; explorer.refresh() }
            }
        }
    }

    /** Quita una cuenta: su conexión con el bunker y lo descifrado en memoria. La papelera se queda. */
    fun removeAccount(pubkey: String) {
        signer.forget(pubkey)
        explorer.forget(pubkey)
        accounts.remove(pubkey)
    }
}

val Context.container: AppContainer get() = (applicationContext as TansuApp).container

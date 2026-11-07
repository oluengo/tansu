package io.github.oluengo.tansu.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colores de acento. Índigo, el del icono, por defecto. */
enum class Accent { INDIGO, VIOLET, GREEN, CORAL, RASPBERRY }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.INDIGO,
    /** Relays de Tansu; null = los de serie. */
    val appRelays: List<String>? = null,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val relays = stringPreferencesKey("app_relays")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            accent = p[K.accent]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.INDIGO,
            appRelays = p[K.relays]?.split('\n')?.filter { it.isNotBlank() },
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[K.theme] = mode.name }
        applyNightMode(mode)
    }

    suspend fun setAccent(a: Accent) = context.dataStore.edit { it[K.accent] = a.name }

    /** null = volver a los de serie. */
    suspend fun setAppRelays(list: List<String>?) = context.dataStore.edit {
        if (list == null) it.remove(K.relays) else it[K.relays] = list.joinToString("\n")
    }

    companion object {
        fun applyNightMode(mode: ThemeMode) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                }
            )
        }
    }
}

/**
 * Idioma de la app (ca/es/en). Sin elección explícita sigue al dispositivo;
 * si el dispositivo usa otro idioma, Android cae en values/ = inglés.
 */
object AppLanguage {
    /** null = idioma del dispositivo. */
    fun current(): String? {
        val l = AppCompatDelegate.getApplicationLocales()
        return if (l.isEmpty) null else l[0]?.language
    }

    fun set(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }
}

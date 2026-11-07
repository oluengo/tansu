package io.github.oluengo.tansu

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.tansu.data.Accent
import io.github.oluengo.tansu.data.AppSettings
import io.github.oluengo.tansu.ui.TansuRoot
import io.github.oluengo.tansu.ui.theme.TansuTheme

/** AppCompatActivity para que el selector de idioma por app funcione también en Android < 13. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by container.settings.flow.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
            TansuTheme(accent = s?.accent ?: Accent.INDIGO) {
                TansuRoot(container)
            }
        }
    }
}

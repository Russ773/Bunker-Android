package app.getbunker.rcon

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import app.getbunker.rcon.ui.AppLockGate
import app.getbunker.rcon.ui.BunkerNav
import app.getbunker.rcon.ui.BunkerTheme
import app.getbunker.rcon.ui.LocalApp
import app.getbunker.rcon.ui.isDark

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = (application as BunkerApp).container

        setContent {
            val settings by app.settings.state.collectAsState()
            val dark = isDark(settings.theme)

            // Draw behind the system bars with icons that suit the theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            DisposableEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            DisposableEffect(settings.secureScreen) {
                if (settings.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }

            CompositionLocalProvider(LocalApp provides app) {
                BunkerTheme(settings.theme) {
                    AppLockGate(this@MainActivity) { BunkerNav() }
                }
            }
        }
    }
}

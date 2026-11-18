package app.getbunker.rcon

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.getbunker.rcon.core.AppContainer
import app.getbunker.rcon.steam.SteamLookup
import java.io.File

class BunkerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
        if (BuildConfig.DEBUG) {
            // Debug builds only: put "plain", "key" or "banned" in files/steamfake.txt (adb run-as) to get a made-up Steam profile on every card.
            SteamLookup.testHook = { id, name ->
                val f = File(filesDir, "steamfake.txt")
                if (f.exists()) SteamLookup.fake(id, f.readText().trim(), name) else null
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.setForeground(true)
            override fun onStop(owner: LifecycleOwner) = container.setForeground(false)
        })
    }
}

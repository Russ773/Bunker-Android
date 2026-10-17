package app.getbunker.rcon.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.getbunker.rcon.R
import app.getbunker.rcon.core.AppContainer

val LocalApp = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

fun biometricAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

/** True if the phone has a fingerprint, face or screen lock that can be used to unlock the app. */
fun canLockApp(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(biometricAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS

/** Asks for the phone's fingerprint, face or screen lock before showing the app (when turned on in Settings). */
@Composable
fun AppLockGate(activity: FragmentActivity, content: @Composable () -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val foreground by app.inForeground.collectAsState()
    var unlocked by remember { mutableStateOf(false) }
    var leftAt by remember { mutableLongStateOf(0L) }
    var attempt by remember { mutableLongStateOf(0L) }

    // Lock again if the app was out of sight for more than half a minute.
    LaunchedEffect(foreground) {
        if (!foreground) leftAt = System.currentTimeMillis()
        else if (leftAt > 0 && System.currentTimeMillis() - leftAt > 30_000) { unlocked = false; attempt++ }
    }

    if (!settings.appLock || unlocked) {
        content()
        return
    }

    LaunchedEffect(attempt, foreground) {
        if (!foreground) return@LaunchedEffect
        if (!canLockApp(activity)) { unlocked = true; return@LaunchedEffect }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Bunker")
            .setAllowedAuthenticators(biometricAuthenticators())
            .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Cancel") }
            .build()
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
        })
        prompt.authenticate(info)
    }

    Column(
        Modifier.fillMaxSize().background(B.c.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.bunker_logo), contentDescription = "Bunker", modifier = Modifier.size(96.dp))
        Spacer(Modifier.size(20.dp))
        Text("Bunker is locked")
        Spacer(Modifier.size(16.dp))
        Button(onClick = { attempt++ }, colors = ButtonDefaults.buttonColors(containerColor = B.c.accent)) { Text("Unlock") }
        Spacer(Modifier.width(0.dp))
    }
}

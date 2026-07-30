package com.example.ohmyssh.platform

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.ComponentActivity
import io.github.vinceglb.filekit.core.FileKit
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

@SuppressLint("StaticFieldLeak")
object AndroidApp {
    lateinit var context: Context
        private set

    fun init(appContext: Context) {
        context = appContext.applicationContext
        installBouncyCastle()
    }

    // Android ships a cut-down BouncyCastle under the name "BC" that has no
    // X25519, and sshj asks for its key exchange algorithms from "BC" by name,
    // so curve25519-sha256 — what every current server offers first — died with
    // NoSuchAlgorithmException. Swap in the full provider bundled with sshj.
    // Appended rather than inserted first, so Conscrypt keeps serving TLS and
    // only lookups that name "BC" reach this one.
    private fun installBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) is BouncyCastleProvider) return
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }

    // FileKit runs every pick and save through the activity's result registry
    // and throws FileKitNotInitializedException until handed one, so an import
    // never opened a picker. It keeps only a weak reference, hence every
    // onCreate rather than once per process.
    fun attach(activity: ComponentActivity) {
        FileKit.init(activity)
    }
}

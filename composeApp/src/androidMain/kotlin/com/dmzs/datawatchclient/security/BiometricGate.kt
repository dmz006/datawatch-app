package com.dmzs.datawatchclient.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric unlock gate per ADR-0042 (BL2 promoted to v0.10.0). Optional —
 * off by default. When on, app entry shows a BiometricPrompt before
 * AppRoot composes; failure leaves the user at the prompt (no bypass).
 *
 * We deliberately use BIOMETRIC_STRONG (class 3 — fingerprint/face with
 * secure storage backing) to match the Keystore-wrapped DB passphrase's
 * threat model — weaker biometrics (class 2) would be a strictly looser
 * check than we already ask the user to set up.
 *
 * Enabled flag lives in plain [SharedPreferences] (it's a boolean
 * preference, not a secret). The Keystore-bound DB passphrase does NOT
 * gate on biometric yet — Sprint 5 Phase 2 wraps `deriveDatabasePassphrase`
 * in a biometric-bound key to tie the two together.
 */
public class BiometricGate(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    public fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    public fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** True when hardware supports Class-3 biometric AND the user has enrolled. */
    public fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    public fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Error = user cancelled / lockout / hardware — surface the
                // string but don't auto-retry; let AppRoot decide to prompt again.
                onFailure(errString.toString())
            }
            override fun onAuthenticationFailed() {
                // One failed attempt — BiometricPrompt handles retry internally.
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock datawatch")
            .setSubtitle("Confirm it's you to open sessions and tokens")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info)
    }

    public companion object {
        public const val PREFS_FILE: String = "dw.biometric.v1"
        public const val KEY_ENABLED: String = "enabled"
    }
}

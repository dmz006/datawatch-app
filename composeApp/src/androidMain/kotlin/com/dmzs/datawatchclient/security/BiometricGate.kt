package com.dmzs.datawatchclient.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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
 * The prompt is CryptoObject-bound: a Keystore AES key that requires a
 * Class-3 biometric for every use (invalidated on new enrolment) is
 * initialised before the prompt, and unlock only proceeds once the
 * authenticated Cipher completes an encryption. A hooked/forged
 * `onAuthenticationSucceeded` callback therefore cannot unlock the gate —
 * Keystore refuses the operation unless the TEE/StrongBox saw a real match.
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
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (verifyAuthenticated(result)) {
                        onSuccess()
                    } else {
                        onFailure("Biometric verification failed")
                    }
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    // Error = user cancelled / lockout / hardware — surface the
                    // string but don't auto-retry; let AppRoot decide to prompt again.
                    onFailure(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // One failed attempt — BiometricPrompt handles retry internally.
                }
            }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock datawatch")
                .setSubtitle("Confirm it's you to open sessions and tokens")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build()
        val cipher = runCatching { initGateCipher() }.getOrNull()
        if (cipher == null) {
            // Keystore couldn't provide an auth-bound key (rare OEM Keystore
            // failure). Fail closed: never fall back to an unbound prompt.
            onFailure("Biometric unlock is unavailable on this device right now.")
            return
        }
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /**
     * Verifies the authentication cryptographically: the Cipher is only usable
     * after a genuine Class-3 biometric match, so a successful doFinal proves it.
     */
    private fun verifyAuthenticated(result: BiometricPrompt.AuthenticationResult): Boolean =
        runCatching {
            val cipher = result.cryptoObject?.cipher ?: return false
            cipher.doFinal(GATE_CHALLENGE).isNotEmpty()
        }.getOrDefault(false)

    private fun initGateCipher(): Cipher {
        fun newCipher(): Cipher =
            Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}")
                .apply { init(Cipher.ENCRYPT_MODE, gateKey()) }
        return try {
            newCipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Biometric enrolment changed since the key was made — rotate it.
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
            newCipher()
        }
    }

    private fun gateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Every use needs a fresh Class-3 biometric.
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    public companion object {
        public const val PREFS_FILE: String = "dw.biometric.v1"
        public const val KEY_ENABLED: String = "enabled"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dw.biometric.gate.v1"
        private val GATE_CHALLENGE = "datawatch-biometric-gate".encodeToByteArray()
    }
}

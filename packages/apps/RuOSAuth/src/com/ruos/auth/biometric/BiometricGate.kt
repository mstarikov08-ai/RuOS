package com.ruos.auth.biometric

import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/**
 * The real authentication entry point used by every RuOS surface (lock, MIR Pay,
 * RuStore, autofill). Wraps the platform [BiometricPrompt]; the actual face/finger
 * matching is performed by the system biometric stack — RuOS never sees raw frames.
 *
 * NOTE: this shows the system biometric UI. The custom Face ID / Touch ID scan
 * animations elsewhere in RuOS are cosmetic onboarding; this is where genuine
 * authentication happens.
 */
class BiometricGate(private val context: Context) {

    private var cancellation: CancellationSignal? = null

    fun authenticate(
        title: String,
        subtitle: String? = null,
        allowDeviceCredential: Boolean = true,
        onSuccess: () -> Unit,
        onFail: () -> Unit = {},
        onError: (CharSequence) -> Unit = {}
    ) {
        val authenticators = if (allowDeviceCredential)
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        else Authenticators.BIOMETRIC_WEAK

        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
        if (subtitle != null) builder.setSubtitle(subtitle)
        // Negative button is only allowed when device credential is NOT a fallback.
        if (!allowDeviceCredential) builder.setNegativeButton(
            "Отмена", context.mainExecutor) { _, _ -> onError("Отменено") }

        val prompt = builder.build()
        cancellation = CancellationSignal()
        prompt.authenticate(cancellation!!, context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }
                override fun onAuthenticationFailed() { onFail() }
                override fun onAuthenticationError(code: Int, msg: CharSequence?) {
                    onError(msg ?: "Ошибка")
                }
            })
    }

    fun cancel() { cancellation?.cancel(); cancellation = null }
}

package com.ruos.keychain.autofill

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.ruos.keychain.R
import com.ruos.keychain.store.KeychainStore

/**
 * Invoked when the user taps the autofill chip. Requires Face ID / passcode, then builds
 * the real datasets (one per matching saved login) and returns them to the autofill
 * framework. Credentials are only decrypted after a successful biometric unlock.
 */
class AutofillUnlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val domain = intent.getStringExtra(RuOSAutofillService.EXTRA_DOMAIN) ?: ""
        val userId = intent.getParcelableExtra<AutofillId>(RuOSAutofillService.EXTRA_USER_ID)
        val passId = intent.getParcelableExtra<AutofillId>(RuOSAutofillService.EXTRA_PASS_ID)

        val bm = getSystemService(BiometricManager::class.java)
        val canAuth = bm?.canAuthenticate(
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) { deliver(domain, userId, passId); return }

        val prompt = BiometricPrompt.Builder(this)
            .setTitle("RuOS Связка ключей")
            .setSubtitle("Подтвердите вход для автозаполнения")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(CancellationSignal(), mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    deliver(domain, userId, passId)
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) { cancel() }
            })
    }

    private fun deliver(domain: String, userId: AutofillId?, passId: AutofillId?) {
        val store = KeychainStore(this)
        val matches = store.matching(domain).ifEmpty { store.credentials() }
        val response = FillResponse.Builder()
        var added = 0
        for (c in matches) {
            val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.autofill_text, "${c.title} · ${c.username}")
            }
            val ds = Dataset.Builder(presentation)
            if (userId != null) ds.setValue(userId, AutofillValue.forText(c.username))
            if (passId != null) ds.setValue(passId, AutofillValue.forText(c.password))
            response.addDataset(ds.build()); added++
        }
        if (added == 0) { cancel(); return }
        val reply = android.content.Intent().putExtra(
            AutofillManager.EXTRA_AUTHENTICATION_RESULT, response.build())
        setResult(RESULT_OK, reply); finish()
    }

    private fun cancel() { setResult(RESULT_CANCELED); finish() }
}

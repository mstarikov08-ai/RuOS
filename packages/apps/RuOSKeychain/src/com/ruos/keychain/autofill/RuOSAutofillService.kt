package com.ruos.keychain.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import com.ruos.keychain.R
import com.ruos.keychain.store.Credential
import com.ruos.keychain.store.KeychainStore

/**
 * RuOS Keychain autofill. On a fill request it locates the username/password fields and
 * returns a response whose datasets are gated behind biometric unlock (response-level
 * authentication → [AutofillUnlockActivity] builds the real datasets after Face ID). On a
 * save request it stores the newly entered credentials in the encrypted vault.
 *
 * Enable as the system autofill service (Settings → Autofill, or
 *   adb shell settings put secure autofill_service \
 *     com.ruos.keychain/com.ruos.keychain.autofill.RuOSAutofillService).
 */
class RuOSAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancel: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val parsed = structure?.let { parse(it) }
        if (parsed == null || (parsed.usernameId == null && parsed.passwordId == null)) {
            callback.onSuccess(null); return
        }
        val ids = listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()

        // Response-level authentication: tapping the chip unlocks, then datasets appear.
        val authIntent = Intent(this, AutofillUnlockActivity::class.java).apply {
            putExtra(EXTRA_DOMAIN, parsed.domain)
            putExtra(EXTRA_USER_ID, parsed.usernameId)
            putExtra(EXTRA_PASS_ID, parsed.passwordId)
        }
        val sender = PendingIntent.getActivity(this, 0, authIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).intentSender

        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_text, "RuOS Связка ключей — разблокировать")
        }

        val builder = FillResponse.Builder()
            .setAuthentication(ids, sender, presentation)
        // Offer to save newly typed credentials.
        val saveIds = listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()
        builder.setSaveInfo(SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD, saveIds).build())
        callback.onSuccess(builder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val parsed = structure?.let { parse(it) }
        if (parsed != null && (parsed.usernameValue.isNotEmpty() || parsed.passwordValue.isNotEmpty())) {
            val store = KeychainStore(this)
            store.upsertCredential(Credential(
                id = "cred_${System.currentTimeMillis()}",
                title = parsed.domain.ifEmpty { parsed.usernameValue },
                domain = parsed.domain,
                username = parsed.usernameValue,
                password = parsed.passwordValue))
        }
        callback.onSuccess()
    }

    // ── AssistStructure parsing ───────────────────────────────────────────────────

    private class Parsed {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameValue = ""
        var passwordValue = ""
        var domain = ""
    }

    private fun parse(structure: AssistStructure): Parsed {
        val p = Parsed()
        p.domain = structure.activityComponent?.packageName ?: ""
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode, p)
        }
        return p
    }

    private fun walk(node: AssistStructure.ViewNode, p: Parsed) {
        node.webDomain?.takeIf { it.isNotEmpty() }?.let { p.domain = it }
        val hints = node.autofillHints
        val id = node.autofillId
        val text = node.autofillValue?.let { if (it.isText) it.textValue.toString() else "" } ?: ""

        val isPassword = (node.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
            (node.inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
            hints?.any { it == View.AUTOFILL_HINT_PASSWORD } == true
        val isUsername = hints?.any {
            it == View.AUTOFILL_HINT_USERNAME || it == View.AUTOFILL_HINT_EMAIL_ADDRESS
        } == true || (node.inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0

        if (id != null && isPassword && p.passwordId == null) {
            p.passwordId = id; if (text.isNotEmpty()) p.passwordValue = text
        } else if (id != null && isUsername && p.usernameId == null) {
            p.usernameId = id; if (text.isNotEmpty()) p.usernameValue = text
        }

        for (i in 0 until node.childCount) walk(node.getChildAt(i), p)
    }

    companion object {
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_PASS_ID = "pass_id"
    }
}

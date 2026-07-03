package com.ruos.phone.spam

import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse

/**
 * Screens incoming calls against the user's spam rules ([SpamFilter]). When RuOS Phone holds the
 * call-screening role, blocked numbers are rejected and kept out of the log/notifications, like
 * iOS's «Заглушение неизвестных». Everything is guarded so a screening failure never drops a
 * legitimate call — on any error we simply allow it.
 */
class SpamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val response = runCatching { buildResponse(callDetails) }.getOrNull()
        respondToCall(callDetails, response ?: CallResponse.Builder().build())
    }

    private fun buildResponse(details: Call.Details): CallResponse {
        // Only screen incoming calls.
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return CallResponse.Builder().build()
        val number = details.handle?.schemeSpecificPart ?: details.handle?.toString()
        val store = SpamStore(this)
        val isContact = number != null && isContact(number)
        val decision = SpamFilter.decide(store.rules(), number, null, isContact)
        if (decision == SpamFilter.Decision.BLOCK) {
            runCatching { store.logBlocked(SpamFilter.normalize(number), "call") }
            return CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)          // keep it in the log so the user can review/undo
                .setSkipNotification(true)
                .build()
        }
        return CallResponse.Builder().build()
    }

    private fun isContact(number: String): Boolean = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
            it.count > 0
        } ?: false
    }.getOrDefault(false)
}

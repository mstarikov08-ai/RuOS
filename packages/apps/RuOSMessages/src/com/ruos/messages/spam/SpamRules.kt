package com.ruos.messages.spam

import android.content.Context
import android.net.Uri

/**
 * Fetches the current spam rules from RuOS Phone's signature-guarded provider
 * (content://com.ruos.phone.spam). One place for the URI + cursor contract so the
 * receiver and the list screens can't drift. Returns null when the provider is
 * unreachable — callers must fail open (never block/hide on error).
 */
object SpamRules {

    private val URI: Uri = Uri.parse("content://com.ruos.phone.spam")

    fun fetch(context: Context): SpamFilter.Rules? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) SpamFilter.fromJson(c.getString(0)) else null
        }
    }.getOrNull()

    fun logBlocked(context: Context, sender: String) {
        runCatching { context.contentResolver.call(URI, "logBlocked", sender, null) }
    }
}

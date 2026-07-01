package com.ruos.phone.spam

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle

/**
 * Exposes the current spam rules (as JSON) to other platform-signed RuOS apps — namely
 * RuOSMessages, so a number blocked in Phone also filters SMS. Guarded by the signature permission
 * com.ruos.permission.SPAM so only RuOS can read it. Read-only; the rules are edited in
 * [SpamSettingsActivity]. A `call("logBlocked", "<number>")` lets Messages record a blocked SMS in
 * the same rolling log the Phone UI shows.
 */
class SpamProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(uri: Uri, proj: Array<String>?, sel: String?, args: Array<String>?, sort: String?): Cursor {
        val json = SpamStore(context!!).let { SpamStore.toJson(it.rules()) }
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(json)) }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "logBlocked" && arg != null) {
            runCatching { SpamStore(context!!).logBlocked(SpamFilter.normalize(arg), "sms") }
        }
        return null
    }

    override fun getType(uri: Uri) = "vnd.ruos/spam"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, sel: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, sel: String?, args: Array<String>?) = 0
}

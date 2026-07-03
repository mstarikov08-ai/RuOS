package com.ruos.screenshot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore

/**
 * Single MediaStore image-save path shared by markup, the scrolling-screenshot stitcher and the
 * document scanner (previously three inline copies). Returns the inserted Uri, or null on any
 * failure so callers can toast.
 */
object MediaImages {

    fun savePng(context: Context, bmp: Bitmap, relativePath: String, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri).use { os ->
                requireNotNull(os) { "no output stream" }
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
        }.isSuccess
        return if (ok) uri else null
    }
}

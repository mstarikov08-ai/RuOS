package com.ruos.screenshot.scroll

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast

/**
 * Builds a "scrolling screenshot" from several overlapping captures. Launched with an ordered list
 * of image URIs (ACTION_SEND_MULTIPLE from the gallery, or an "uris" extra); it decodes them,
 * stitches with [ScrollStitch] (overlaps removed), and saves one long PNG to Pictures/Screenshots.
 * Decode + stitch run off the UI thread. On the RuOS build the SystemUI screenshot flow feeds this
 * with auto-scrolled slices; standalone it also works on hand-picked images.
 */
class StitchActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = collectUris()
        if (uris.size < 2) {
            Toast.makeText(this, "Нужно минимум два снимка для склейки", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        Toast.makeText(this, "Склейка снимков…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                val slices = uris.mapNotNull { decode(it) }
                if (slices.size < 2) return@runCatching null
                val stitched = ScrollStitch.stitch(slices) ?: return@runCatching null
                save(stitched)
            }.getOrNull()
            main.post {
                if (result != null) Toast.makeText(this, "Длинный снимок сохранён", Toast.LENGTH_LONG).show()
                else Toast.makeText(this, "Не удалось склеить снимки", Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }

    private fun collectUris(): List<Uri> {
        intent.getParcelableArrayListExtra<Uri>("uris")?.let { if (it.isNotEmpty()) return it }
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { return it }
        }
        return intent.data?.let { listOf(it) } ?: emptyList()
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            ?.copy(Bitmap.Config.ARGB_8888, false)
    }.getOrNull()

    private fun save(bmp: Bitmap): Uri? {
        val name = "RuOS_${System.currentTimeMillis()}_long.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        contentResolver.openOutputStream(uri).use { os -> os?.let { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        return uri
    }
}

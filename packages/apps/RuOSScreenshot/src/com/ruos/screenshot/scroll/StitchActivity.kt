package com.ruos.screenshot.scroll

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Builds a "scrolling screenshot" from several overlapping captures. Launched with an ordered list
 * of image URIs — multi-select screenshots in the gallery and share them here («Длинный снимок»),
 * or send the custom STITCH action with a "uris" extra. Decodes, stitches with [ScrollStitch]
 * (overlaps removed), and saves one long PNG to Pictures/Screenshots. Decode + stitch run off the
 * UI thread.
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

    private fun save(bmp: Bitmap): Uri? =
        com.ruos.screenshot.MediaImages.savePng(
            this, bmp, "Pictures/Screenshots", "RuOS_${System.currentTimeMillis()}_long.png")
}

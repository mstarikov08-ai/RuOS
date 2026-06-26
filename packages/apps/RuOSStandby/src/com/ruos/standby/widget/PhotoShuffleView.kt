package com.ruos.standby.widget

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Full-screen photo shuffle: cycles recent gallery images every 30s with a smooth
 * crossfade between two stacked ImageViews. Reads via MediaStore (needs
 * READ_MEDIA_IMAGES). Shows nothing gracefully if no photos / no permission.
 */
class PhotoShuffleView(context: Context) : FrameLayout(context) {

    private val back = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val front = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val handler = Handler(Looper.getMainLooper())
    private var uris: List<Uri> = emptyList()
    private var index = 0
    private var showingFront = true

    private val cycle = object : Runnable {
        override fun run() { next(); handler.postDelayed(this, 30_000) }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(back, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(front, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadPhotos()
        if (uris.isNotEmpty()) { front.setImageURI(uris[0]); handler.postDelayed(cycle, 30_000) }
    }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(cycle) }

    private fun loadPhotos() {
        val out = ArrayList<Uri>()
        runCatching {
            val proj = arrayOf(MediaStore.Images.Media._ID)
            val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var n = 0
                while (c.moveToNext() && n < 60) {
                    out.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol).toString()))
                    n++
                }
            }
        }
        uris = out.shuffled()
    }

    private fun next() {
        if (uris.isEmpty()) return
        index = (index + 1) % uris.size
        val target = if (showingFront) back else front
        val current = if (showingFront) front else back
        target.setImageURI(uris[index])
        target.alpha = 0f
        target.animate().alpha(1f).setDuration(1200).start()
        current.animate().alpha(0f).setDuration(1200).start()
        showingFront = !showingFront
    }
}

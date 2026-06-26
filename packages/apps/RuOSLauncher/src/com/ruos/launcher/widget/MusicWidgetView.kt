package com.ruos.launcher.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.launcher.R

/**
 * Now Playing music widget for the first home screen page.
 * Connects to the active MediaSession (VK Music, Yandex Music, etc.)
 * and shows album art, track name, artist, controls.
 */
class MusicWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private val albumArt = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        background = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(Color.parseColor("#2C2C2E"))
        }
    }
    private val trackName = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        setTypeface(null, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val artistName = TextView(context).apply {
        setTextColor(Color.argb(180, 255, 255, 255))
        textSize = 12f
        maxLines = 1
    }
    private val playPauseBtn = ImageButton(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.WHITE)
        setImageResource(android.R.drawable.ic_media_play)
    }
    private val prevBtn = ImageButton(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.WHITE)
        setImageResource(android.R.drawable.ic_media_previous)
    }
    private val nextBtn = ImageButton(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.WHITE)
        setImageResource(android.R.drawable.ic_media_next)
    }

    private var controller: MediaController? = null

    init {
        background = GradientDrawable().apply {
            cornerRadius = 20f * density
            setColor(Color.argb(160, 28, 28, 30))
        }
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        val p = (12 * density).toInt()

        val artSize = (60 * density).toInt()
        val artParams = LayoutParams(artSize, artSize).also {
            it.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            it.marginStart = p
        }
        addView(albumArt, artParams)

        val infoCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        infoCol.addView(trackName)
        infoCol.addView(artistName)

        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controlRow.addView(prevBtn, (32 * density).toInt(), (32 * density).toInt())
        controlRow.addView(playPauseBtn, (36 * density).toInt(), (36 * density).toInt())
        controlRow.addView(nextBtn, (32 * density).toInt(), (32 * density).toInt())

        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((artSize + p * 2), p, p, p)
        }
        rightCol.addView(infoCol, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rightCol.addView(controlRow, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addView(rightCol, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // Wire up controls
        playPauseBtn.setOnClickListener {
            val state = controller?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING)
                controller?.transportControls?.pause()
            else
                controller?.transportControls?.play()
        }
        prevBtn.setOnClickListener { controller?.transportControls?.skipToPrevious() }
        nextBtn.setOnClickListener { controller?.transportControls?.skipToNext() }

        connectToMediaSession()
    }

    private fun connectToMediaSession() {
        try {
            val msm = context.getSystemService(MediaSessionManager::class.java)
            msm.addOnActiveSessionsChangedListener({ controllers ->
                controller = controllers?.firstOrNull()
                controller?.registerCallback(sessionCallback, handler)
                controller?.metadata?.let { updateMetadata(it) }
            }, null, handler)
        } catch (_: SecurityException) {}
    }

    private val sessionCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata?.let { updateMetadata(it) }
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            playPauseBtn.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }
    }

    private fun updateMetadata(meta: MediaMetadata) {
        trackName.text = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artistName.text = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (bitmap != null) albumArt.setImageBitmap(bitmap)
        else albumArt.setImageDrawable(null)
    }
}

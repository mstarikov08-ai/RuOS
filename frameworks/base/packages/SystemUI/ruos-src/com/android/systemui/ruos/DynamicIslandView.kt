package com.android.systemui.ruos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Dynamic Island — pill-shaped cutout at top-centre of screen.
 *
 * States:
 *  PILL      — default compact pill (matches the camera cutout)
 *  MUSIC     — expanded downward showing: album art, track name, progress, controls
 *  CALL      — compact left+right expansion showing caller info
 *  TIMER     — compact showing countdown
 *  NAV       — compact showing turn-by-turn icon
 *
 * Spring spec:
 *   expand:  stiffness 320, damping 0.72  (matches Apple's island expand)
 *   collapse: stiffness 420, damping 0.80
 *
 * The pill is BLACK (matches the physical cutout). Content fades in after
 * expansion completes.
 */
class DynamicIslandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class IslandState { PILL, MUSIC, CALL, TIMER, NAV }

    private var state = IslandState.PILL
    private val handler = Handler(Looper.getMainLooper())

    // Geometry — set from onSizeChanged
    private var pillCx = 0f
    private var pillCy = 0f

    // Animated dimensions
    private var animWidth = 0f
    private var animHeight = 0f
    private var animCorner = 0f

    // Background paint — always black
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val bgRect = RectF()

    // Music content views — invisible until expanded
    private val albumArt = ImageView(context).apply { visibility = View.INVISIBLE; scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true }
    private val trackName = TextView(context).apply { visibility = View.INVISIBLE; setTextColor(Color.WHITE); textSize = 13f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val artistName = TextView(context).apply { visibility = View.INVISIBLE; setTextColor(Color.argb(180, 255, 255, 255)); textSize = 11f }
    private val progressBar = SeekBar(context).apply { visibility = View.INVISIBLE; progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE) }
    private val playPause = ImageButton(context).apply { visibility = View.INVISIBLE; setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE) }
    private val prev = ImageButton(context).apply { visibility = View.INVISIBLE; setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE) }
    private val next = ImageButton(context).apply { visibility = View.INVISIBLE; setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE) }

    private val pillWidthPx get() = context.resources.getDimensionPixelSize(com.android.internal.R.dimen.dynamic_island_pill_width).toFloat()
    private val pillHeightPx get() = context.resources.getDimensionPixelSize(com.android.internal.R.dimen.dynamic_island_pill_height).toFloat()
    private val pillCornerPx get() = pillHeightPx / 2f

    private val musicWidthPx = context.resources.displayMetrics.widthPixels * 0.85f
    private val musicHeightPx = 200f * context.resources.displayMetrics.density

    // Springs
    private val widthSpring = makeSpring(SpringForce.STIFFNESS_MEDIUM, 0.75f)
    private val heightSpring = makeSpring(SpringForce.STIFFNESS_MEDIUM, 0.75f)
    private val cornerSpring = makeSpring(SpringForce.STIFFNESS_HIGH, 0.80f)

    private fun makeSpring(stiffness: Float, damping: Float) =
        SpringForce().apply { this.stiffness = stiffness; this.dampingRatio = damping }

    init {
        setWillNotDraw(false)
        clipChildren = false
        isClickable = true

        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (state == IslandState.PILL) expandToMusic()
            else collapseToPill()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        pillCx = w / 2f
        pillCy = pillHeightPx / 2f + context.resources.getDimensionPixelSize(R.dimen.island_top_margin)

        if (state == IslandState.PILL) {
            animWidth = pillWidthPx
            animHeight = pillHeightPx
            animCorner = pillCornerPx
        }
    }

    override fun onDraw(canvas: Canvas) {
        val left = pillCx - animWidth / 2f
        val top = pillCy - animHeight / 2f
        bgRect.set(left, top, left + animWidth, top + animHeight)
        canvas.drawRoundRect(bgRect, animCorner, animCorner, bgPaint)
    }

    fun expandToMusic() {
        state = IslandState.MUSIC
        animateTo(musicWidthPx, musicHeightPx, 24f * resources.displayMetrics.density)
        handler.postDelayed({ showMusicContent() }, 280)
    }

    fun collapseToPill() {
        hideMusicContent()
        state = IslandState.PILL
        animateTo(pillWidthPx, pillHeightPx, pillCornerPx)
    }

    fun updateMusicMetadata(metadata: MediaMetadata?) {
        metadata ?: return
        trackName.text = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artistName.text = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (bitmap != null) albumArt.setImageBitmap(bitmap)
        if (state == IslandState.PILL) expandToMusic()
    }

    fun updatePlaybackState(state: PlaybackState?) {
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        playPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun animateTo(targetW: Float, targetH: Float, targetCorner: Float) {
        val wProp = object : FloatPropertyCompat<DynamicIslandView>("animWidth") {
            override fun getValue(obj: DynamicIslandView) = obj.animWidth
            override fun setValue(obj: DynamicIslandView, value: Float) { obj.animWidth = value; obj.invalidate() }
        }
        val hProp = object : FloatPropertyCompat<DynamicIslandView>("animHeight") {
            override fun getValue(obj: DynamicIslandView) = obj.animHeight
            override fun setValue(obj: DynamicIslandView, value: Float) { obj.animHeight = value; obj.invalidate() }
        }
        val cProp = object : FloatPropertyCompat<DynamicIslandView>("animCorner") {
            override fun getValue(obj: DynamicIslandView) = obj.animCorner
            override fun setValue(obj: DynamicIslandView, value: Float) { obj.animCorner = value; obj.invalidate() }
        }

        SpringAnimation(this, wProp, targetW).apply { spring = makeSpring(320f, 0.72f); start() }
        SpringAnimation(this, hProp, targetH).apply { spring = makeSpring(320f, 0.72f); start() }
        SpringAnimation(this, cProp, targetCorner).apply { spring = makeSpring(420f, 0.80f); start() }
    }

    private fun showMusicContent() {
        listOf(albumArt, trackName, artistName, progressBar, playPause, prev, next).forEach {
            it.visibility = View.VISIBLE
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun hideMusicContent() {
        listOf(albumArt, trackName, artistName, progressBar, playPause, prev, next).forEach {
            it.animate().alpha(0f).setDuration(150).withEndAction { it.visibility = View.INVISIBLE }.start()
        }
    }
}

private abstract class FloatPropertyCompat<T>(name: String) :
    androidx.dynamicanimation.animation.FloatPropertyCompat<T>(name)

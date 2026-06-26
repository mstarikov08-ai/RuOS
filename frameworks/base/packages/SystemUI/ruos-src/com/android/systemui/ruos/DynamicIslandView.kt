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
import com.android.systemui.R

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

    enum class IslandState { PILL, MUSIC, CALL, TIMER, NAV, CHARGING, NEXTALARM, LIVE, SPLIT }

    /** Tapping a Live Activity (compact or split) asks the manager to expand the card. */
    var onLiveTap: (() -> Unit)? = null

    // Split (two simultaneous Live Activities) — drawn as two bubbles.
    private var splitLeft = ""; private var splitRight = ""
    private var splitLeftColor = Color.WHITE; private var splitRightColor = Color.WHITE
    private val splitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val splitText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
    }

    private var state = IslandState.PILL
    private val handler = Handler(Looper.getMainLooper())

    // Compact content shared by CALL / TIMER / CHARGING (a single centred label).
    private val statusLabel = TextView(context).apply {
        visibility = View.INVISIBLE
        setTextColor(Color.WHITE)
        textSize = 14f
        maxLines = 1
        gravity = Gravity.CENTER
        setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
    }
    // Accent dot drawn left of the label (green=charging, red=call, white=timer).
    private var accentColor = Color.WHITE
    private var showAccent = false
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Timer countdown
    private var timerEndMs = 0L
    private val timerTick = object : Runnable {
        override fun run() {
            val remain = (timerEndMs - System.currentTimeMillis()).coerceAtLeast(0L)
            statusLabel.text = formatDuration(remain)
            if (remain <= 0L) collapseToPill() else handler.postDelayed(this, 500)
        }
    }

    // Charging auto-show
    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: android.content.Intent?) {
            i ?: return
            val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (plugged && level >= 0 && i.action == android.content.Intent.ACTION_POWER_CONNECTED) {
                showCharging((level * 100) / scale.coerceAtLeast(1))
            }
        }
    }

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

    // Pill dimensions — pulled from overlay resource so they're overridable per-device
    // without touching this file. Matches physical Pixel 8/9 cutout exactly.
    private val pillWidthPx get() = context.resources.getDimension(R.dimen.dynamic_island_pill_width)
    private val pillHeightPx get() = context.resources.getDimension(R.dimen.dynamic_island_pill_height)
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
            when (state) {
                IslandState.LIVE, IslandState.SPLIT -> onLiveTap?.invoke()
                IslandState.PILL -> expandToMusic()
                else -> collapseToPill()
            }
        }

        // Centred status label for compact states.
        addView(statusLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            it.topMargin = context.resources.getDimensionPixelSize(R.dimen.island_top_margin) +
                (6 * resources.displayMetrics.density).toInt()
        })

        runCatching {
            context.registerReceiver(batteryReceiver,
                android.content.IntentFilter(android.content.Intent.ACTION_POWER_CONNECTED))
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60; val s = totalSec % 60
        return "%d:%02d".format(m, s)
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
        if (state == IslandState.SPLIT) { drawSplit(canvas); return }

        val left = pillCx - animWidth / 2f
        val top = pillCy - animHeight / 2f
        bgRect.set(left, top, left + animWidth, top + animHeight)
        canvas.drawRoundRect(bgRect, animCorner, animCorner, bgPaint)

        // Accent dot left of the status label (call/timer/charging).
        if (showAccent && statusLabel.visibility == View.VISIBLE) {
            accentPaint.color = accentColor
            val r = 4f * resources.displayMetrics.density
            val dotX = statusLabel.left - r * 3f
            val dotY = pillCy
            if (dotX > left + r) canvas.drawCircle(dotX, dotY, r, accentPaint)
        }
    }

    fun expandToMusic() {
        state = IslandState.MUSIC
        animateTo(musicWidthPx, musicHeightPx, 24f * resources.displayMetrics.density)
        handler.postDelayed({ showMusicContent() }, 280)
    }

    fun collapseToPill() {
        hideMusicContent()
        hideStatusContent()
        handler.removeCallbacks(timerTick)
        state = IslandState.PILL
        animateTo(pillWidthPx, pillHeightPx, pillCornerPx)
    }

    // ── Live Activities (generic) ──────────────────────────────────────────────

    /** One Live Activity → compact pill: accent dot + "leading  trailing". */
    fun showLive(leading: String, trailing: String, color: Int) {
        state = IslandState.LIVE
        accentColor = color; showAccent = leading.isNotBlank()
        statusLabel.text = listOf(leading, trailing).filter { it.isNotBlank() }.joinToString("   ")
        animateTo(compactWidthPx, compactHeightPx, compactHeightPx / 2f)
        handler.postDelayed({ showStatusContent() }, 200)
    }

    /** Two Live Activities → split into two bubbles either side of the cutout. */
    fun showSplit(left: String, leftColor: Int, right: String, rightColor: Int) {
        state = IslandState.SPLIT
        splitLeft = left; splitRight = right
        splitLeftColor = leftColor; splitRightColor = rightColor
        hideStatusContent()
        animateTo(pillWidthPx, pillHeightPx, pillCornerPx)  // sizes are per-bubble in drawSplit
        invalidate()
    }

    fun clearLive() {
        if (state == IslandState.LIVE || state == IslandState.SPLIT) collapseToPill()
    }

    private fun drawSplit(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val h = compactHeightPx
        val bw = compactWidthPx * 0.42f
        val gap = pillWidthPx * 0.9f          // centre gap clears the camera cutout
        val top = pillCy - h / 2f
        val r = h / 2f
        splitText.textSize = 13f * d

        // Left bubble
        val lLeft = pillCx - gap / 2f - bw
        bgRect.set(lLeft, top, lLeft + bw, top + h)
        canvas.drawRoundRect(bgRect, r, r, bgPaint)
        splitPaint.color = splitLeftColor
        canvas.drawCircle(lLeft + r * 0.7f, pillCy, 3.5f * d, splitPaint)
        splitText.color = Color.WHITE
        canvas.drawText(splitLeft, lLeft + bw / 2f + r * 0.3f,
            pillCy - (splitText.descent() + splitText.ascent()) / 2f, splitText)

        // Right bubble
        val rLeft = pillCx + gap / 2f
        bgRect.set(rLeft, top, rLeft + bw, top + h)
        canvas.drawRoundRect(bgRect, r, r, bgPaint)
        splitPaint.color = splitRightColor
        canvas.drawCircle(rLeft + r * 0.7f, pillCy, 3.5f * d, splitPaint)
        canvas.drawText(splitRight, rLeft + bw / 2f + r * 0.3f,
            pillCy - (splitText.descent() + splitText.ascent()) / 2f, splitText)
    }

    // ── CALL / TIMER / CHARGING compact states ─────────────────────────────────

    private val compactWidthPx get() = context.resources.displayMetrics.widthPixels * 0.55f
    private val compactHeightPx get() = pillHeightPx * 1.1f

    /** Show an ongoing-call island: red dot + caller name. */
    fun showCall(caller: String) {
        state = IslandState.CALL
        accentColor = Color.parseColor("#FF453A")   // iOS red
        showAccent = true
        statusLabel.text = caller
        animateTo(compactWidthPx, compactHeightPx, compactHeightPx / 2f)
        handler.postDelayed({ showStatusContent() }, 220)
    }

    /** Show a countdown timer island. [durationMs] from now. */
    fun showTimer(durationMs: Long) {
        state = IslandState.TIMER
        accentColor = Color.WHITE
        showAccent = true
        timerEndMs = System.currentTimeMillis() + durationMs
        statusLabel.text = formatDuration(durationMs)
        animateTo(compactWidthPx * 0.7f, compactHeightPx, compactHeightPx / 2f)
        handler.postDelayed({ showStatusContent() }, 220)
        handler.post(timerTick)
    }

    /** Briefly show a charging island, then auto-collapse. */
    fun showCharging(percent: Int) {
        state = IslandState.CHARGING
        accentColor = Color.parseColor("#34C759")   // iOS green
        showAccent = true
        statusLabel.text = "$percent%"
        animateTo(compactWidthPx * 0.7f, compactHeightPx, compactHeightPx / 2f)
        handler.postDelayed({ showStatusContent() }, 220)
        handler.postDelayed({ if (state == IslandState.CHARGING) collapseToPill() }, 3200)
    }

    /** Persistent next-alarm chip, fed by RuOSAlarm's UpcomingNotifier broadcast. */
    fun showNextAlarm(timeStr: String) {
        if (timeStr.isBlank()) {
            if (state == IslandState.NEXTALARM) collapseToPill()
            return
        }
        // Don't override a more urgent live state (call/timer/charging/music).
        if (state != IslandState.PILL && state != IslandState.NEXTALARM) return
        state = IslandState.NEXTALARM
        accentColor = Color.parseColor("#FF9F0A")   // iOS amber alarm
        showAccent = true
        statusLabel.text = "Будильник $timeStr"
        animateTo(compactWidthPx, compactHeightPx, compactHeightPx / 2f)
        handler.postDelayed({ showStatusContent() }, 220)
    }

    private fun showStatusContent() {
        statusLabel.visibility = View.VISIBLE
        statusLabel.alpha = 0f
        statusLabel.animate().alpha(1f).setDuration(180).start()
        invalidate()
    }

    private fun hideStatusContent() {
        showAccent = false
        statusLabel.animate().alpha(0f).setDuration(120)
            .withEndAction { statusLabel.visibility = View.INVISIBLE }.start()
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

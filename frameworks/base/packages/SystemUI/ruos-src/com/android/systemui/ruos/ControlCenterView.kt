package com.android.systemui.ruos

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * RuOS Control Centre — swipe down from top-right.
 *
 * Layout (top to bottom):
 *   Connectivity block: WiFi | Bluetooth | Mobile Data | Hotspot  (2×2 grid)
 *   Quick toggles row: Silent | Flashlight | DND | Rotation
 *   Brightness slider (full width)
 *   Volume slider (full width)
 *   Now Playing card (visible when music is active)
 *
 * All tiles use spring haptic feedback on toggle.
 * Frosted glass background: dark blur + 18% white overlay.
 */
class ControlCenterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val cornerRadius = 20f * density

    // Services
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    // Panels
    private val connectivityGrid = ConnectivityGridView(context)
    private val quickToggles = QuickToggleRowView(context)
    private val brightnessSlider = SliderView(context, "Brightness", 0, 255)
    private val volumeSlider = SliderView(context, "Volume", 0, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    private val nowPlaying = NowPlayingCardView(context)

    // Frosted glass backdrop
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 28, 28, 30)  // #1C1C1E at 82% — iOS dark CC
    }
    private val glassBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.5f * density
    }
    private val glassRect = RectF()

    // Slide-in animation
    var dismissProgress = 0f  // 0 = fully shown, 1 = dismissed upward

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false

        val p = (20 * density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p, p, p)
            gravity = Gravity.TOP
        }

        content.addView(connectivityGrid, ViewGroup.LayoutParams.MATCH_PARENT, (180 * density).toInt())
        content.addView(spacer(12), ViewGroup.LayoutParams.MATCH_PARENT, (12 * density).toInt())
        content.addView(quickToggles, ViewGroup.LayoutParams.MATCH_PARENT, (70 * density).toInt())
        content.addView(spacer(20), ViewGroup.LayoutParams.MATCH_PARENT, (20 * density).toInt())
        content.addView(brightnessSlider, ViewGroup.LayoutParams.MATCH_PARENT, (60 * density).toInt())
        content.addView(spacer(12), ViewGroup.LayoutParams.MATCH_PARENT, (12 * density).toInt())
        content.addView(volumeSlider, ViewGroup.LayoutParams.MATCH_PARENT, (60 * density).toInt())
        content.addView(spacer(16), ViewGroup.LayoutParams.MATCH_PARENT, (16 * density).toInt())
        content.addView(nowPlaying, ViewGroup.LayoutParams.MATCH_PARENT, (80 * density).toInt())

        addView(content, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        brightnessSlider.onProgress = { value ->
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
        }

        volumeSlider.onProgress = { value ->
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        }

        connectivityGrid.onToggle = { tile, enabled -> hapticTick() }
        quickToggles.onToggle = { tile, enabled -> hapticTick() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        glassRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(glassRect, cornerRadius, cornerRadius, glassPaint)
        canvas.drawRoundRect(glassRect, cornerRadius, cornerRadius, glassBorder)
    }

    fun animateIn() {
        translationY = -height.toFloat()
        SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
    }

    fun animateOut(onDone: () -> Unit) {
        SpringAnimation(this, SpringAnimation.TRANSLATION_Y, -height.toFloat()).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            addEndListener { _, _, _, _ -> onDone() }
            start()
        }
    }

    private fun hapticTick() {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    private fun spacer(dpH: Int) = View(context)
}

// ---------------------------------------------------------------------------
// Sub-views
// ---------------------------------------------------------------------------

private class ConnectivityGridView(context: Context) : android.widget.GridLayout(context) {
    var onToggle: ((String, Boolean) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val tiles = listOf(
        Tile("WiFi", android.R.drawable.stat_sys_wifi_signal_4, true),
        Tile("Bluetooth", android.R.drawable.stat_sys_data_bluetooth, false),
        Tile("Mobile", android.R.drawable.stat_sys_signal_4, true),
        Tile("Hotspot", android.R.drawable.ic_menu_share, false)
    )

    data class Tile(val label: String, val iconRes: Int, var enabled: Boolean)

    init {
        columnCount = 2
        rowCount = 2

        tiles.forEach { tile ->
            val tileView = ConnTileView(context, tile)
            tileView.onToggle = { enabled ->
                tile.enabled = enabled
                onToggle?.invoke(tile.label, enabled)
            }
            val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            addView(tileView, GridLayout.LayoutParams(spec, spec).apply {
                width = 0
                height = (80 * density).toInt()
                setMargins(4, 4, 4, 4)
            })
        }
    }
}

private class ConnTileView(context: Context, tile: ConnectivityGridView.Tile) : FrameLayout(context) {
    var onToggle: ((Boolean) -> Unit)? = null

    private var enabled = tile.enabled
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val icon = ImageView(context).apply { setColorFilter(Color.WHITE) }
    private val label = TextView(context).apply { setTextColor(Color.WHITE); textSize = 11f }

    init {
        setWillNotDraw(false)
        setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        icon.setImageResource(tile.iconRes)

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
        }
        col.addView(icon, (22 * density).toInt(), (22 * density).toInt())
        col.addView(label)
        label.text = tile.label
        addView(col, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        isClickable = true
        setOnClickListener {
            enabled = !enabled
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onToggle?.invoke(enabled)
            invalidate()
        }

        updateBackground()
    }

    private fun updateBackground() {
        background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
            FloatArray(8) { 14 * density }, null, null
        )).apply {
            paint.color = if (enabled) Color.WHITE else Color.argb(100, 80, 80, 80)
        }
        icon.setColorFilter(if (enabled) Color.BLACK else Color.WHITE)
        label.setTextColor(if (enabled) Color.BLACK else Color.WHITE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
    }
}

private class QuickToggleRowView(context: Context) : LinearLayout(context) {
    var onToggle: ((String, Boolean) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val density = context.resources.displayMetrics.density

        listOf(
            Pair("Silent", android.R.drawable.ic_lock_silent_mode),
            Pair("Torch", android.R.drawable.ic_menu_camera),
            Pair("DND", android.R.drawable.ic_lock_silent_mode_off),
            Pair("Rotate", android.R.drawable.ic_menu_rotate)
        ).forEach { (label, icon) ->
            val btn = QuickToggleButton(context, label, icon).apply {
                this.onToggle = { enabled -> this@QuickToggleRowView.onToggle?.invoke(label, enabled) }
            }
            addView(btn, LinearLayout.LayoutParams(0, (60 * density).toInt(), 1f).also {
                it.setMargins(4, 0, 4, 0)
            })
        }
    }
}

private class QuickToggleButton(context: Context, label: String, iconRes: Int) : FrameLayout(context) {
    var onToggle: ((Boolean) -> Unit)? = null
    private var enabled = false

    init {
        val density = context.resources.displayMetrics.density
        setBackgroundColor(Color.argb(80, 80, 80, 80))
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }
        addView(icon, LayoutParams((24 * density).toInt(), (24 * density).toInt()).also {
            it.gravity = Gravity.CENTER
        })
        isClickable = true
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        setOnClickListener {
            enabled = !enabled
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            setBackgroundColor(if (enabled) Color.WHITE else Color.argb(80, 80, 80, 80))
            icon.setColorFilter(if (enabled) Color.BLACK else Color.WHITE)
            onToggle?.invoke(enabled)
        }
    }
}

private class SliderView(context: Context, label: String, min: Int, max: Int) : LinearLayout(context) {
    var onProgress: ((Int) -> Unit)? = null

    private val density = context.resources.displayMetrics.density

    init {
        orientation = VERTICAL
        val lbl = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        val seek = SeekBar(context).apply {
            this.min = min
            this.max = max
            progress = max / 2
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) onProgress?.invoke(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        addView(lbl)
        addView(seek, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
}

private class NowPlayingCardView(context: Context) : FrameLayout(context) {
    init {
        val density = context.resources.displayMetrics.density
        setBackgroundColor(Color.argb(120, 255, 255, 255))
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    }
}

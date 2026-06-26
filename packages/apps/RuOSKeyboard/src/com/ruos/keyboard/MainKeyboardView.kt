package com.ruos.keyboard

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

// ── Colour palette (iOS 18 dark keyboard) ─────────────────────────────────────

private const val BG             = "#1B1B1B"
private const val KEY_LETTER     = "#6B6B6B"
private const val KEY_LETTER_HI  = "#898989"
private const val KEY_ACTION     = "#4A4A4A"
private const val KEY_ACTION_HI  = "#5A5A5A"
private const val KEY_RETURN_COL = "#4A4A4A"
private const val TEXT_ON_KEY    = "#FFFFFF"
private const val SHADOW_COL     = "#55000000"

class MainKeyboardView(context: Context) : View(context) {

    // ── Callbacks ─────────────────────────────────────────────────────────────

    var onKeyPress:         ((Key) -> Unit)? = null
    var onShiftToggle:      ((Int) -> Unit)? = null  // 0=off, 1=once, 2=caps
    var onSuggestionNeeded: ((String) -> Unit)? = null
    var onCursorMove:       ((Int) -> Unit)? = null  // delta chars

    // ── State ─────────────────────────────────────────────────────────────────

    var shiftState  = 0  // 0=off, 1=single, 2=caps
    var currentMode = Layouts.RUSSIAN
        set(v) { field = v; invalidate() }

    private var pressedKey: Key? = null
    private var keyRects   = mutableListOf<List<Pair<Key, RectF>>>()
    private var isSwipeMode = false
    private val swipePath   = mutableListOf<Key>()

    // Space-bar cursor sliding
    private var spaceBarRect: RectF? = null
    private var spaceSlidingActive = false
    private var spaceSlideStartX = 0f
    private var cursorMoveAccum  = 0f

    // Long-press
    private val handler       = Handler(Looper.getMainLooper())
    private var longPressKey: Key? = null
    private val LONG_PRESS_MS = 380L
    private val longPressRunnable = Runnable { triggerLongPress() }

    // Double-tap space
    private var lastSpaceMs = 0L
    private val DOUBLE_TAP_MS = 350L

    // Popup
    private val popup = KeyPopupWindow(context)

    // Paint objects (reused)
    private val bgPaint     = Paint().apply { isAntiAlias = true }
    private val shadowPaint = Paint().apply { isAntiAlias = true }
    private val keyPaint    = Paint().apply { isAntiAlias = true }
    private val textPaint   = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    // Canvas drawables for special keys
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Vibrator for haptic
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // ── Measurement & layout ──────────────────────────────────────────────────

    private val KEY_ROW_HEIGHT_DP  = 47f
    private val KEY_GAP_DP         = 5f
    private val H_PAD_DP           = 3f
    private val V_PAD_DP           = 5f
    private val CORNER_RADIUS_DP   = 5f
    private val SHADOW_OFFSET_DP   = 1.5f
    private val SUGGESTION_H_DP    = 44f   // reserved for suggestion bar (managed by Layout)
    private val BOTTOM_EXTRA_DP    = 6f    // padding below last row

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = currentMode.rows.size
        val h = (rows * dp(KEY_ROW_HEIGHT_DP) +
                 (rows + 1) * dp(V_PAD_DP) +
                 dp(BOTTOM_EXTRA_DP)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        buildKeyRects(w, h)
    }

    private fun buildKeyRects(viewW: Int, viewH: Int) {
        keyRects.clear()
        spaceBarRect = null
        val mode = currentMode
        val numRows = mode.rows.size
        val rowH = dp(KEY_ROW_HEIGHT_DP)
        val hPad = dp(H_PAD_DP)
        val kGap = dp(KEY_GAP_DP)
        val vPad = dp(V_PAD_DP)

        // Reference unit width from the letter rows (first letter row)
        val refRow = mode.rows.firstOrNull { r -> r.keys.any { it.style == KeyStyle.LETTER } }
            ?: mode.rows[0]
        val refLetterCount = refRow.keys.count { it.style == KeyStyle.LETTER }.coerceAtLeast(1)
        val refWeight = refRow.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
        val refUsable = viewW - 2 * hPad - (refRow.keys.size - 1) * kGap
        val unitW = refUsable / refWeight

        for ((rIdx, row) in mode.rows.withIndex()) {
            val rowY = (vPad + rIdx * (rowH + vPad)).toFloat()
            val rowKeys = mutableListOf<Pair<Key, RectF>>()

            // Compute row total width and centre it
            val totalW = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat() * unitW +
                         (row.keys.size - 1) * kGap
            var x = ((viewW - totalW) / 2f)

            for (key in row.keys) {
                val kW = key.widthWeight * unitW
                val rect = RectF(x, rowY, x + kW, rowY + rowH)
                rowKeys.add(key to rect)
                if (key.code == CODE_SPACE) spaceBarRect = rect
                x += kW + kGap
            }
            keyRects.add(rowKeys)
        }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor(BG))

        for (row in keyRects) {
            for ((key, rect) in row) {
                drawKey(canvas, key, rect, key == pressedKey)
            }
        }
    }

    private fun drawKey(canvas: Canvas, key: Key, rect: RectF, pressed: Boolean) {
        val r = dp(CORNER_RADIUS_DP)
        val shadowOff = dp(SHADOW_OFFSET_DP)

        // Shadow
        shadowPaint.color = Color.parseColor(SHADOW_COL)
        val shadow = RectF(rect.left, rect.top + shadowOff, rect.right, rect.bottom + shadowOff)
        canvas.drawRoundRect(shadow, r, r, shadowPaint)

        // Key body
        val bgColor = when {
            key.style == KeyStyle.LETTER && !pressed -> Color.parseColor(KEY_LETTER)
            key.style == KeyStyle.LETTER && pressed  -> Color.parseColor(KEY_LETTER_HI)
            key.style == KeyStyle.SPACE  && !pressed -> Color.parseColor(KEY_LETTER)
            key.style == KeyStyle.SPACE  && pressed  -> Color.parseColor(KEY_LETTER_HI)
            pressed -> Color.parseColor(KEY_ACTION_HI)
            else    -> Color.parseColor(KEY_ACTION)
        }
        keyPaint.color = bgColor
        canvas.drawRoundRect(rect, r, r, keyPaint)

        // Content
        when (key.code) {
            CODE_SHIFT  -> drawShiftIcon(canvas, rect)
            CODE_DELETE -> drawDeleteIcon(canvas, rect)
            CODE_SWITCH_LANG -> drawGlobeIcon(canvas, rect)
            else        -> drawKeyLabel(canvas, rect, key, pressed)
        }
    }

    private fun drawKeyLabel(canvas: Canvas, rect: RectF, key: Key, pressed: Boolean) {
        val label = when {
            key.style == KeyStyle.RETURN -> if (currentMode.name == "ru") "Ввод" else "return"
            key.style == KeyStyle.SPACE  -> ""  // no label on space bar
            shiftState > 0 && key.label.length == 1 && key.label[0].isLetter() -> key.label.uppercase()
            else -> key.label
        }

        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER

        when (key.style) {
            KeyStyle.LETTER -> {
                textPaint.textSize = dp(20f)
                textPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }
            KeyStyle.RETURN, KeyStyle.ACTION -> {
                textPaint.textSize = dp(15f)
                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            else -> {
                textPaint.textSize = dp(16f)
                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
        }

        val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), cy, textPaint)
    }

    // Up-chevron for shift; filled when caps, double chevron when caps-lock
    private fun drawShiftIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX(); val cy = rect.centerY()
        val w  = rect.width() * 0.36f; val h = rect.height() * 0.34f

        iconPaint.strokeWidth = dp(2f)
        iconPaint.style = if (shiftState == 2) Paint.Style.FILL else Paint.Style.STROKE
        iconPaint.color = Color.WHITE

        val path = Path()
        // Arrow head (chevron)
        path.moveTo(cx - w, cy + h * 0.1f)
        path.lineTo(cx, cy - h * 0.8f)
        path.lineTo(cx + w, cy + h * 0.1f)
        if (shiftState == 2) {
            // Filled arrow body for caps-lock
            path.lineTo(cx + w * 0.45f, cy + h * 0.1f)
            path.lineTo(cx + w * 0.45f, cy + h * 0.9f)
            path.lineTo(cx - w * 0.45f, cy + h * 0.9f)
            path.lineTo(cx - w * 0.45f, cy + h * 0.1f)
            path.close()
        }
        canvas.drawPath(path, iconPaint)

        // Small dot below arrow for caps-lock indicator
        if (shiftState == 2) {
            iconPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy + h * 1.2f, dp(2f), iconPaint)
        }
    }

    // Backspace arrow with X inside
    private fun drawDeleteIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX(); val cy = rect.centerY()
        val w = rect.width() * 0.38f; val h = rect.height() * 0.28f

        iconPaint.strokeWidth = dp(1.8f)
        iconPaint.style = Paint.Style.STROKE
        iconPaint.color = Color.WHITE

        val path = Path()
        path.moveTo(cx - w * 0.3f, cy - h * 1.0f)
        path.lineTo(cx - w,        cy)
        path.lineTo(cx - w * 0.3f, cy + h * 1.0f)
        path.lineTo(cx + w,        cy + h * 1.0f)
        path.lineTo(cx + w,        cy - h * 1.0f)
        path.close()
        canvas.drawPath(path, iconPaint)

        // X inside
        val xInset = w * 0.25f; val yInset = h * 0.5f
        canvas.drawLine(cx - xInset * 0.6f, cy - yInset, cx + xInset * 0.9f, cy + yInset, iconPaint)
        canvas.drawLine(cx + xInset * 0.9f, cy - yInset, cx - xInset * 0.6f, cy + yInset, iconPaint)
    }

    // Simple meridian globe
    private fun drawGlobeIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX(); val cy = rect.centerY()
        val r  = rect.height() * 0.28f

        iconPaint.strokeWidth = dp(1.5f)
        iconPaint.style = Paint.Style.STROKE
        iconPaint.color = Color.WHITE

        canvas.drawCircle(cx, cy, r, iconPaint)
        // Horizontal equator
        canvas.drawLine(cx - r, cy, cx + r, cy, iconPaint)
        // Vertical axis
        canvas.drawLine(cx, cy - r, cx, cy + r, iconPaint)
        // Elliptical meridians
        canvas.drawArc(RectF(cx - r * 0.5f, cy - r, cx + r * 0.5f, cy + r), 0f, 360f, false, iconPaint)
    }

    // ── Touch handling ────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(x, y)
            MotionEvent.ACTION_MOVE -> handleMove(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(x, y, event.action)
        }
        return true
    }

    private fun handleDown(x: Float, y: Float) {
        val key = keyAt(x, y) ?: return

        // Space bar — start potential cursor-slide
        if (key.code == CODE_SPACE) {
            spaceSlidingActive = true
            spaceSlideStartX = x
            cursorMoveAccum = 0f
        }

        pressedKey = key
        longPressKey = key
        isSwipeMode = false
        swipePath.clear()
        swipePath.add(key)

        invalidate()
        haptic()

        // Show key popup for letter keys
        if (key.style == KeyStyle.LETTER) {
            popup.show(this, key.label, rectOf(key) ?: RectF(), shiftState > 0)
        }

        // Schedule long-press
        handler.removeCallbacks(longPressRunnable)
        handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
    }

    private fun handleMove(x: Float, y: Float) {
        // Cursor sliding on space bar
        if (spaceSlidingActive) {
            val delta = x - spaceSlideStartX
            cursorMoveAccum += delta - (cursorMoveAccum % dp(14f))
            val steps = (cursorMoveAccum / dp(14f)).toInt()
            if (steps != 0) {
                onCursorMove?.invoke(steps)
                cursorMoveAccum -= steps * dp(14f)
                haptic()
            }
            spaceSlideStartX = x
            return
        }

        val key = keyAt(x, y) ?: return

        if (key != pressedKey) {
            // Moved to a new key — switch to swipe mode
            if (!isSwipeMode && swipePath.size >= 1) {
                isSwipeMode = true
                popup.dismiss()
            }

            if (isSwipeMode) {
                val last = swipePath.lastOrNull()
                if (key != last && key.style == KeyStyle.LETTER) swipePath.add(key)
            }

            // Cancel long press on move
            handler.removeCallbacks(longPressRunnable)
            longPressKey = null
            pressedKey = key
            invalidate()
        }
    }

    private fun handleUp(x: Float, y: Float, action: Int) {
        handler.removeCallbacks(longPressRunnable)
        popup.dismiss()

        val wasSpaceSliding = spaceSlidingActive && kotlin.math.abs(x - spaceSlideStartX) > dp(10f)
        spaceSlidingActive = false

        val key = pressedKey ?: run { pressedKey = null; invalidate(); return }
        pressedKey = null
        invalidate()

        if (action == MotionEvent.ACTION_CANCEL) return

        if (wasSpaceSliding) return  // cursor was moved, don't type space

        if (isSwipeMode && swipePath.size >= 3) {
            handleSwipeCommit()
        } else {
            handleKeyCommit(key)
        }

        isSwipeMode = false
        swipePath.clear()
    }

    private fun handleKeyCommit(key: Key) {
        when (key.code) {
            CODE_SPACE -> {
                val now = System.currentTimeMillis()
                if (now - lastSpaceMs < DOUBLE_TAP_MS) {
                    // Double-tap space → replace last space with ". "
                    onKeyPress?.invoke(Key("_BACK", CODE_DELETE))
                    onKeyPress?.invoke(Key(". ", '.'.code))
                } else {
                    onKeyPress?.invoke(key)
                }
                lastSpaceMs = now
            }
            else -> onKeyPress?.invoke(key)
        }
    }

    private fun handleSwipeCommit() {
        val lang = if (currentMode.name == "ru") Dictionary.Lang.RU else Dictionary.Lang.EN
        val dedup = swipePath.map { it.label }.distinct()
        val candidates = Dictionary.matchSwipe(dedup, lang)
        val word = candidates.firstOrNull() ?: swipePath.map { it.label }.joinToString("")
        onKeyPress?.invoke(Key(word, -999))  // synthetic commit
    }

    private fun triggerLongPress() {
        val key = longPressKey ?: return
        if (key.alternatives.isNotEmpty()) {
            popup.showAlternatives(this, key.alternatives, rectOf(key) ?: RectF())
            haptic()
        } else if (key.code == CODE_DELETE) {
            // Long press delete = clear word
            repeat(8) { onKeyPress?.invoke(Key("", CODE_DELETE)) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun keyAt(x: Float, y: Float): Key? {
        for (row in keyRects) {
            for ((key, rect) in row) {
                if (rect.contains(x, y)) return key
            }
        }
        return null
    }

    private fun rectOf(target: Key): RectF? {
        for (row in keyRects) {
            for ((key, rect) in row) {
                if (key === target) return rect
            }
        }
        return null
    }

    private fun haptic() {
        vibrator?.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        )
    }

    fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun dp(v: Int) = dp(v.toFloat()).toInt()
}

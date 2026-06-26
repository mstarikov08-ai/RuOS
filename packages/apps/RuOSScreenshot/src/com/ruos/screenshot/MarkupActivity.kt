package com.ruos.screenshot

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream

/**
 * iOS-style screenshot markup. Loads the captured image (intent data URI or "path"
 * extra), offers pen / highlighter / crop / undo, a colour palette, and Share / Done.
 * "Done" saves an edited copy to the gallery. Programmatic dark UI, Golos, haptics.
 */
class MarkupActivity : Activity() {

    private lateinit var markup: MarkupView
    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val palette = intArrayOf(
        Color.parseColor("#FF3B30"), Color.parseColor("#FF9500"), Color.parseColor("#FFCC00"),
        Color.parseColor("#34C759"), Color.parseColor("#007AFF"), Color.parseColor("#AF52DE"),
        Color.WHITE, Color.BLACK)
    private var selectedColor = 0
    private val swatches = ArrayList<View>()
    private val tools = HashMap<MarkupView.Tool, ToolButton>()
    private lateinit var undoBtn: ToolButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bmp = loadBitmap()
        if (bmp == null) { Toast.makeText(this, "Не удалось открыть снимок", Toast.LENGTH_SHORT).show(); finish(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK)
            setPadding(0, dp(36), 0, 0)
        }
        root.addView(topBar())

        markup = MarkupView(this).apply {
            setBitmap(bmp); setColor(palette[0])
            onHistoryChanged = { undoBtn.isEnabled = canUndo(); undoBtn.alpha = if (canUndo()) 1f else 0.35f }
        }
        root.addView(markup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(colorRow())
        root.addView(toolRow())
        setContentView(root)
    }

    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(navText("Отмена") { finish() })
        addView(TextView(this@MarkupActivity).apply {
            text = "Разметка"; setTextColor(Color.WHITE); textSize = 17f; typeface = golos
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(navText("Готово") { saveCopy(); finish() })
    }

    private fun navText(t: String, onTap: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#0A84FF")); textSize = 17f; typeface = golos
        isClickable = true; setOnClickListener { onTap() }
    }

    private fun colorRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        palette.forEachIndexed { i, c ->
            val sw = object : View(this) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = dp(2.5).toFloat(); color = Color.WHITE }
                override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(34), dp(34))
                override fun onDraw(canvas: Canvas) {
                    p.color = c
                    canvas.drawCircle(width/2f, height/2f, dp(11).toFloat(), p)
                    if (i == selectedColor) canvas.drawCircle(width/2f, height/2f, dp(14).toFloat(), ring)
                    if (c == Color.WHITE) {
                        ring.color = 0xFF8E8E93.toInt()
                        canvas.drawCircle(width/2f, height/2f, dp(11).toFloat(), ring)
                        ring.color = Color.WHITE
                    }
                }
            }
            sw.isClickable = true
            sw.setOnClickListener {
                selectedColor = i; markup.setColor(c)
                swatches.forEach { it.invalidate() }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            swatches.add(sw)
            row.addView(sw, LinearLayout.LayoutParams(dp(38), dp(38)))
        }
        return row
    }

    private fun toolRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(20))
        }
        val pen = ToolButton(this, ToolGlyph.PEN)
        val hl = ToolButton(this, ToolGlyph.HIGHLIGHTER)
        val crop = ToolButton(this, ToolGlyph.CROP)
        undoBtn = ToolButton(this, ToolGlyph.UNDO).also { it.alpha = 0.35f; it.isEnabled = false }
        val share = ToolButton(this, ToolGlyph.SHARE)
        tools[MarkupView.Tool.PEN] = pen; tools[MarkupView.Tool.HIGHLIGHTER] = hl; tools[MarkupView.Tool.CROP] = crop

        fun select(t: MarkupView.Tool) {
            markup.setTool(t); tools.forEach { (k, v) -> v.selected = (k == t) }
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        pen.setOnClickListener { select(MarkupView.Tool.PEN) }
        hl.setOnClickListener { select(MarkupView.Tool.HIGHLIGHTER) }
        crop.setOnClickListener { select(MarkupView.Tool.CROP) }
        undoBtn.setOnClickListener { markup.undo() }
        share.setOnClickListener { shareEdited() }
        pen.selected = true

        val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
        listOf(pen, hl, crop, undoBtn, share).forEach { row.addView(it, lp) }
        return row
    }

    // ── load / save / share ─────────────────────────────────────────────────────

    private fun loadBitmap(): Bitmap? {
        val path = intent.getStringExtra("path")
        if (path != null) return runCatching {
            BitmapFactory.decodeFile(path)?.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull()
        val uri = intent.data ?: return null
        return runCatching {
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?.copy(Bitmap.Config.ARGB_8888, true)
        }.getOrNull()
    }

    private fun saveCopy(): Uri? {
        val out = markup.export()
        val name = "RuOS_${System.currentTimeMillis()}_edited.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri).use { os: OutputStream? ->
                os?.let { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            Toast.makeText(this, "Сохранено в Фото", Toast.LENGTH_SHORT).show()
        }
        return uri
    }

    private fun shareEdited() {
        val uri = saveCopy() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(send, "Поделиться")) }
    }
}

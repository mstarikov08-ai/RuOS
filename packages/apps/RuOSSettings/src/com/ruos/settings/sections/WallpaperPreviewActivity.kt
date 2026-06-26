package com.ruos.settings.sections

import android.app.Activity
import android.app.WallpaperManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class WallpaperPreviewActivity : Activity() {

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_NAME = "extra_name"
    }

    private var bitmap: Bitmap? = null
    private lateinit var previewImg: ImageView
    private lateinit var applyBtn: TextView

    private var blurOn = false
    private var perspOn = true
    private var targetFlags = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK

    private fun closeDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = android.graphics.Paint.Style.STROKE
                strokeWidth = bounds.width() * 0.14f; strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val b = bounds; val m = b.width() * 0.28f
            canvas.drawLine(b.left + m, b.top + m, b.right - m, b.bottom - m, p)
            canvas.drawLine(b.right - m, b.top + m, b.left + m, b.bottom - m, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(buildUI())
        loadBitmap()
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private fun buildUI(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        previewImg = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            scaleX = 1.1f
            scaleY = 1.1f
        }
        root.addView(previewImg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Gradient scrim at bottom for panel legibility
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#F0000000"), Color.TRANSPARENT)
            )
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(360)).apply {
            gravity = Gravity.BOTTOM
        })

        // Cancel button (top-left)
        root.addView(buildCancelBtn(), FrameLayout.LayoutParams(dp(44), dp(44)).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(dp(16), statusBarHeight() + dp(10), 0, 0)
        })

        // Bottom control panel
        root.addView(buildPanel(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

        return root
    }

    private fun buildCancelBtn() = ImageButton(this).apply {
        val d = closeDrawable(Color.WHITE)
        d.setBounds(0, 0, dp(20), dp(20))
        setImageDrawable(d)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#77000000"))
        }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isClickable = true
        isFocusable = true
        setOnClickListener { finish() }
    }

    private fun buildPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), navBarHeight() + dp(24))
        }

        panel.addView(buildScreenSelector())
        panel.addView(spacer(12))
        panel.addView(buildOptionToggles())
        panel.addView(spacer(18))

        applyBtn = TextView(this).apply {
            text = "Установить обои"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#D94F3D"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { applyWallpaper() }
        }
        panel.addView(applyBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return panel
    }

    // ── Screen selector (segmented control) ──────────────────────────────────

    private fun buildScreenSelector(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#44FFFFFF"))
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        data class Seg(val label: String, val flags: Int)
        val segs = listOf(
            Seg("Блокировка", WallpaperManager.FLAG_LOCK),
            Seg("Рабочий стол", WallpaperManager.FLAG_SYSTEM),
            Seg("Оба", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        )

        val btns = segs.map { seg ->
            TextView(this).apply {
                text = seg.label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                isClickable = true
                isFocusable = true
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
        }

        fun select(idx: Int) {
            targetFlags = segs[idx].flags
            btns.forEachIndexed { i, btn ->
                if (i == idx) {
                    btn.background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(Color.parseColor("#D94F3D"))
                    }
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.background = null
                    btn.setTextColor(Color.parseColor("#CCFFFFFF"))
                }
            }
        }

        btns.forEachIndexed { i, btn ->
            btn.setOnClickListener { select(i) }
            container.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        select(2)

        return container
    }

    // ── Option toggles (blur + perspective) ──────────────────────────────────

    private fun buildOptionToggles(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(buildToggleCard("Размытие", false) { on ->
            blurOn = on
            previewImg.setRenderEffect(
                if (on) RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP) else null
            )
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))

        row.addView(buildToggleCard("Перспектива", true) { on ->
            perspOn = on
            val s = if (on) 1.1f else 1.0f
            previewImg.animate().scaleX(s).scaleY(s).setDuration(250).start()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        return row
    }

    private fun buildToggleCard(label: String, initial: Boolean, onToggle: (Boolean) -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#44FFFFFF"))
            }
        }
        card.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(Switch(this).apply {
            isChecked = initial
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList.valueOf(
                if (initial) Color.parseColor("#D94F3D") else Color.parseColor("#767680")
            )
            setOnCheckedChangeListener { _, checked ->
                trackTintList = ColorStateList.valueOf(
                    if (checked) Color.parseColor("#D94F3D") else Color.parseColor("#767680")
                )
                onToggle(checked)
            }
        })
        return card
    }

    // ── Bitmap loading ────────────────────────────────────────────────────────

    private fun loadBitmap() {
        Thread {
            val bmp = try {
                when {
                    intent.hasExtra(EXTRA_PATH) ->
                        BitmapFactory.decodeFile(intent.getStringExtra(EXTRA_PATH))
                    intent.hasExtra(EXTRA_URI) ->
                        contentResolver.openInputStream(Uri.parse(intent.getStringExtra(EXTRA_URI)))
                            ?.use { BitmapFactory.decodeStream(it) }
                    else -> null
                }
            } catch (_: Exception) { null }

            runOnUiThread {
                bitmap = bmp
                if (bmp != null) {
                    previewImg.setImageBitmap(bmp)
                } else {
                    Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ── Applying wallpaper ────────────────────────────────────────────────────

    private fun applyWallpaper() {
        val bmp = bitmap ?: run {
            Toast.makeText(this, "Изображение ещё загружается…", Toast.LENGTH_SHORT).show()
            return
        }
        applyBtn.isClickable = false
        applyBtn.text = "Установка…"
        applyBtn.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#99D94F3D"))
        }

        Thread {
            try {
                val wm = WallpaperManager.getInstance(this)
                if (targetFlags and WallpaperManager.FLAG_SYSTEM != 0)
                    wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
                if (targetFlags and WallpaperManager.FLAG_LOCK != 0)
                    wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
                runOnUiThread {
                    Toast.makeText(this, "Обои установлены", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    applyBtn.isClickable = true
                    applyBtn.text = "Установить обои"
                    applyBtn.background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(Color.parseColor("#D94F3D"))
                    }
                    Toast.makeText(this, "Не удалось установить обои", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun spacer(dp: Int) = View(this).also {
        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp))
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun navBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(34)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

package com.ruos.settings.sections

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class WallpaperPickerActivity : Activity() {

    private val PICK_IMAGE = 1001

    private val builtIn = listOf(
        "/product/media/wallpapers/ruos_space_01.jpg" to "Глубокий космос",
        "/product/media/wallpapers/ruos_space_02.jpg" to "Северное сияние",
        "/product/media/wallpapers/ruos_space_03.jpg" to "Байконур",
        "/product/media/wallpapers/ruos_space_04.jpg" to "Туманность",
        "/product/media/wallpapers/ruos_space_05.jpg" to "RuOS"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), statusBarHeight() + dp(12), dp(16), dp(60))
        }

        col.addView(TextView(this).apply {
            text = "Обои"
            textSize = 34f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(20))
        })

        col.addView(galleryRow())
        col.addView(spacer(24))

        col.addView(TextView(this).apply {
            text = "ВСТРОЕННЫЕ ОБОИ"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(dp(4), 0, 0, dp(10))
        })

        col.addView(wallpaperGrid())

        scroll.addView(col)
        root.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        return root
    }

    private fun galleryRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#1C1C1E"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    },
                    PICK_IMAGE
                )
            }
        }

        val iconBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(7).toFloat()
            setColor(Color.parseColor("#D94F3D"))
        }
        row.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_gallery)
            setColorFilter(Color.WHITE)
            background = iconBg
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }, dp(32), dp(32))
        row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        row.addView(TextView(this).apply {
            text = "Из галереи"
            textSize = 17f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"; textSize = 22f; setTextColor(Color.parseColor("#636366"))
        })
        return row
    }

    private fun wallpaperGrid(): View {
        val screenW = resources.displayMetrics.widthPixels - dp(32)
        val gap = dp(10)
        val cellW = (screenW - gap) / 2
        val cellH = (cellW * 2400f / 1080f).toInt()

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        builtIn.chunked(2).forEachIndexed { rowIdx, pair ->
            if (rowIdx > 0) grid.addView(spacer(10))
            val rowView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { colIdx, (path, name) ->
                if (colIdx > 0) rowView.addView(View(this), LinearLayout.LayoutParams(gap, cellH))
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

                val thumb = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(v: View, o: android.graphics.Outline) {
                            o.setRoundRect(0, 0, v.width, v.height, dp(12).toFloat())
                        }
                    }
                    setBackgroundColor(Color.parseColor("#0D0D1A"))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openPreview(path, name) }
                }
                cell.addView(thumb, LinearLayout.LayoutParams(cellW, cellH))
                cell.addView(TextView(this).apply {
                    text = name
                    textSize = 12f
                    setTextColor(Color.parseColor("#AEAEB2"))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                }, LinearLayout.LayoutParams(cellW, LinearLayout.LayoutParams.WRAP_CONTENT))

                rowView.addView(cell)

                Thread {
                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        val bmp = BitmapFactory.decodeFile(path, opts)
                        if (bmp != null) runOnUiThread { thumb.setImageBitmap(bmp) }
                    } catch (_: Exception) {}
                }.start()
            }
            grid.addView(rowView)
        }
        return grid
    }

    private fun openPreview(path: String, name: String) {
        startActivity(Intent(this, WallpaperPreviewActivity::class.java).apply {
            putExtra(WallpaperPreviewActivity.EXTRA_PATH, path)
            putExtra(WallpaperPreviewActivity.EXTRA_NAME, name)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            startActivity(Intent(this, WallpaperPreviewActivity::class.java).apply {
                putExtra(WallpaperPreviewActivity.EXTRA_URI, uri.toString())
                putExtra(WallpaperPreviewActivity.EXTRA_NAME, "Из галереи")
            })
        }
    }

    private fun spacer(dp: Int) = View(this).also {
        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp))
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

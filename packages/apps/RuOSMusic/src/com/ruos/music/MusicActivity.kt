package com.ruos.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// ─────────────────────────── DATA ────────────────────────────────────────────

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri
)

// ─────────────────────────── HELPERS ─────────────────────────────────────────

private fun dp(ctx: Context, v: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

private fun colorInt(hex: String) = Color.parseColor(hex)

private fun roundCard(ctx: Context, color: Int = Color.parseColor("#1C1C1E"), radius: Float = 14f)
    : GradientDrawable = GradientDrawable().also { it.setColor(color); it.cornerRadius = dp(ctx, radius).toFloat() }

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// ─────────────────────────── ACTIVITY ────────────────────────────────────────

class MusicActivity : AppCompatActivity() {

    private val PERM_REQUEST = 1001
    private val mainHandler  = Handler(Looper.getMainLooper())

    // Playback
    private var mediaPlayer: MediaPlayer? = null
    private var tracks        = mutableListOf<Track>()
    private var currentIndex  = -1
    private var isPlaying     = false
    private var isShuffled    = false
    private var isRepeating   = false
    private var isFavourite   = false

    // Audio focus
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // UI refs
    private lateinit var rootLayout: LinearLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var miniBar: LinearLayout
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlayBtn: ImageButton
    private var nowPlayingOverlay: FrameLayout? = null

    // Current tab index
    private var currentTab = 0
    private val tabNames   = listOf("Слушать", "Обзор", "Радио", "Библиотека")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setContentView(buildMainLayout())
        checkPermissions()
    }

    // ─────────────────────────── CANVAS DRAWABLES ────────────────────────────

    private fun musicNoteDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = android.graphics.Paint.Style.FILL }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            canvas.drawRect(w*0.55f, h*0.1f, w*0.68f, h*0.72f, p)
            val flagPath = android.graphics.Path()
            flagPath.moveTo(w*0.55f, h*0.1f); flagPath.quadTo(w*0.9f, h*0.2f, w*0.68f, h*0.4f); flagPath.close()
            canvas.drawPath(flagPath, p)
            canvas.drawOval(android.graphics.RectF(w*0.25f, h*0.62f, w*0.62f, h*0.88f), p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun heartDrawable(filled: Boolean, color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = if (filled) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
                strokeWidth = bounds.width() * 0.08f
            }
            val b = bounds; val cx = b.exactCenterX(); val cy = b.exactCenterY(); val r = b.width() * 0.38f
            val path = android.graphics.Path()
            path.moveTo(cx, b.bottom.toFloat() - b.height()*0.1f)
            path.cubicTo(b.left.toFloat(), cy, b.left.toFloat(), b.top.toFloat(), cx, cy - r*0.2f)
            path.cubicTo(b.right.toFloat(), b.top.toFloat(), b.right.toFloat(), cy, cx, b.bottom.toFloat() - b.height()*0.1f)
            canvas.drawPath(path, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun tabIconDrawable(index: Int, color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = android.graphics.Paint.Style.FILL
            }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            when (index) {
                0 -> {
                    val path = android.graphics.Path()
                    path.moveTo(w*0.2f, h*0.15f); path.lineTo(w*0.85f, h*0.5f); path.lineTo(w*0.2f, h*0.85f); path.close()
                    canvas.drawPath(path, p)
                }
                1 -> {
                    p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = w*0.1f
                    canvas.drawCircle(w*0.5f, h*0.5f, w*0.35f, p)
                    p.style = android.graphics.Paint.Style.FILL
                    canvas.drawCircle(w*0.5f, h*0.5f, w*0.1f, p)
                }
                2 -> {
                    p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = w*0.08f; p.strokeCap = android.graphics.Paint.Cap.ROUND
                    canvas.drawArc(android.graphics.RectF(w*0.35f, h*0.35f, w*0.65f, h*0.65f), 180f, 180f, false, p)
                    canvas.drawArc(android.graphics.RectF(w*0.2f, h*0.2f, w*0.8f, h*0.8f), 180f, 180f, false, p)
                    canvas.drawArc(android.graphics.RectF(w*0.05f, h*0.05f, w*0.95f, h*0.95f), 180f, 180f, false, p)
                    p.style = android.graphics.Paint.Style.FILL
                    canvas.drawCircle(w*0.5f, h*0.65f, w*0.06f, p)
                }
                3 -> {
                    canvas.drawRect(w*0.55f, h*0.1f, w*0.68f, h*0.72f, p)
                    val flagPath = android.graphics.Path()
                    flagPath.moveTo(w*0.55f, h*0.1f); flagPath.quadTo(w*0.9f, h*0.2f, w*0.68f, h*0.4f); flagPath.close()
                    canvas.drawPath(flagPath, p)
                    canvas.drawOval(android.graphics.RectF(w*0.25f, h*0.62f, w*0.62f, h*0.88f), p)
                }
            }
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun playDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = android.graphics.Paint.Style.FILL }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            val path = android.graphics.Path()
            path.moveTo(w*0.2f, h*0.1f); path.lineTo(w*0.9f, h*0.5f); path.lineTo(w*0.2f, h*0.9f); path.close()
            canvas.drawPath(path, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun pauseDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = android.graphics.Paint.Style.FILL }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            canvas.drawRoundRect(android.graphics.RectF(w*0.15f, h*0.1f, w*0.42f, h*0.9f), w*0.08f, w*0.08f, p)
            canvas.drawRoundRect(android.graphics.RectF(w*0.58f, h*0.1f, w*0.85f, h*0.9f), w*0.08f, w*0.08f, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun nextDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = android.graphics.Paint.Style.FILL }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            val path = android.graphics.Path()
            path.moveTo(w*0.1f, h*0.15f); path.lineTo(w*0.6f, h*0.5f); path.lineTo(w*0.1f, h*0.85f); path.close()
            canvas.drawPath(path, p)
            canvas.drawRoundRect(android.graphics.RectF(w*0.65f, h*0.15f, w*0.88f, h*0.85f), w*0.06f, w*0.06f, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun prevDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = android.graphics.Paint.Style.FILL }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            val path = android.graphics.Path()
            path.moveTo(w*0.9f, h*0.15f); path.lineTo(w*0.4f, h*0.5f); path.lineTo(w*0.9f, h*0.85f); path.close()
            canvas.drawPath(path, p)
            canvas.drawRoundRect(android.graphics.RectF(w*0.12f, h*0.15f, w*0.35f, h*0.85f), w*0.06f, w*0.06f, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun genreColorFor(name: String): Int = when (name) {
        "Моя волна"   -> Color.parseColor("#0A84FF")
        "Поп"         -> Color.parseColor("#FF9F0A")
        "Рок"         -> Color.parseColor("#D94F3D")
        "Электроника" -> Color.parseColor("#30D158")
        "Джаз"        -> Color.parseColor("#BF5AF2")
        "Классика"    -> Color.parseColor("#5E5CE6")
        "Хип-хоп"     -> Color.parseColor("#FF6B35")
        "Русский рок" -> Color.parseColor("#D94F3D")
        "Ретро"       -> Color.parseColor("#8E8E93")
        "Романтика"   -> Color.parseColor("#FF375F")
        else          -> Color.parseColor("#0A84FF")
    }

    // ─────────────────────────── MAIN LAYOUT ─────────────────────────────────

    private fun buildMainLayout(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        rootLayout.addView(contentFrame)

        miniBar = buildMiniBar()
        miniBar.visibility = View.GONE
        rootLayout.addView(miniBar)

        rootLayout.addView(buildTabBar())

        switchTab(0)

        return rootLayout
    }

    private fun buildTabBar(): LinearLayout {
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colorInt("#111111"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 60f)
            )

            for ((i, name) in tabNames.withIndex()) {
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity     = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    isClickable = true
                    isFocusable = true

                    val activeColor   = colorInt("#D94F3D")
                    val inactiveColor = colorInt("#8E8E93")
                    val iconColor     = if (i == 0) activeColor else inactiveColor

                    val iconView = ImageView(ctx).apply {
                        val d = tabIconDrawable(i, iconColor)
                        d.setBounds(0, 0, dp(ctx, 22f), dp(ctx, 22f))
                        setImageDrawable(d)
                        layoutParams = LinearLayout.LayoutParams(dp(ctx, 22f), dp(ctx, 22f))
                    }
                    val label = TextView(ctx).apply {
                        text = name
                        textSize = 10f
                        gravity  = Gravity.CENTER
                        setTextColor(iconColor)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.topMargin = dp(ctx, 2f) }
                    }
                    addView(iconView); addView(label)

                    val tabIndex = i
                    setOnClickListener {
                        val tabBar = parent as LinearLayout
                        for (j in 0 until tabBar.childCount) {
                            val t  = tabBar.getChildAt(j) as LinearLayout
                            val iv = t.getChildAt(0) as ImageView
                            val lb = t.getChildAt(1) as TextView
                            val c  = if (j == tabIndex) colorInt("#D94F3D") else colorInt("#8E8E93")
                            val nd = tabIconDrawable(j, c)
                            nd.setBounds(0, 0, dp(ctx, 22f), dp(ctx, 22f))
                            iv.setImageDrawable(nd)
                            lb.setTextColor(c)
                        }
                        switchTab(tabIndex)
                    }
                })
            }
        }
    }

    private fun switchTab(index: Int) {
        currentTab = index
        contentFrame.removeAllViews()
        val view = when (index) {
            0 -> buildListenTab()
            1 -> buildBrowseTab()
            2 -> buildRadioTab()
            3 -> buildLibraryTab()
            else -> buildListenTab()
        }
        contentFrame.addView(view)
    }

    // ─────────────────────────── MINI BAR ────────────────────────────────────

    private fun buildMiniBar(): LinearLayout {
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorInt("#1C1C1E"))
            setPadding(dp(ctx, 12f), dp(ctx, 8f), dp(ctx, 12f), dp(ctx, 8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 64f)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { showNowPlaying() }

            addView(FrameLayout(ctx).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(colorInt("#1A237E"), colorInt("#6A1B9A"))
                ).apply { cornerRadius = dp(ctx, 6f).toFloat() }
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 46f), dp(ctx, 46f)).also {
                    it.marginEnd = dp(ctx, 12f)
                }
            })

            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                miniTitle = TextView(ctx).apply {
                    text = "Нет треков"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                addView(miniTitle)

                miniArtist = TextView(ctx).apply {
                    text = ""
                    setTextColor(colorInt("#8E8E93"))
                    textSize = 12f
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                addView(miniArtist)
            })

            miniPlayBtn = ImageButton(ctx).apply {
                val d = playDrawable(Color.WHITE)
                d.setBounds(0, 0, dp(ctx, 22f), dp(ctx, 22f))
                setImageDrawable(d)
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f)).also {
                    it.marginStart = dp(ctx, 12f)
                    it.marginEnd = dp(ctx, 4f)
                }
                setOnClickListener { togglePlayback() }
            }
            addView(miniPlayBtn)

            addView(ImageButton(ctx).apply {
                val d = nextDrawable(Color.WHITE)
                d.setBounds(0, 0, dp(ctx, 20f), dp(ctx, 20f))
                setImageDrawable(d)
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f))
                setOnClickListener { playNext() }
            })
        }
    }

    // ─────────────────────────── TAB: СЛУШАТЬ ────────────────────────────────

    private fun buildListenTab(): View {
        val ctx = this
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 16f), dp(ctx, 20f), dp(ctx, 16f))
        }

        inner.addView(TextView(ctx).apply {
            text = "Слушать"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 20f) }
        })

        val vkInstalled = isAppInstalled("com.vkontakte.android") || isAppInstalled("com.vk.vkdj")
        val vkCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorInt("#0077FF"), colorInt("#005BBB"))
            ).apply { cornerRadius = dp(ctx, 16f).toFloat() }
            setPadding(dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 20f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 16f) }
        }

        vkCard.addView(TextView(ctx).apply {
            text = "ВК Музыка"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
        })
        vkCard.addView(TextView(ctx).apply {
            text = if (vkInstalled) "Миллионы треков доступны"
            else "Установите ВК Музыку для доступа\nк 100 миллионам треков"
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 6f); it.bottomMargin = dp(ctx, 16f) }
        })

        val vkBtn = TextView(ctx).apply {
            text = if (vkInstalled) "Открыть ВК Музыку" else "Установить ВК Музыку"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = dp(ctx, 10f).toFloat()
            }
            setPadding(0, dp(ctx, 12f), 0, dp(ctx, 12f))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (vkInstalled) openVkMusic()
                else openRuStoreVkMusic()
            }
        }
        vkCard.addView(vkBtn)
        inner.addView(vkCard)

        inner.addView(buildFeaturedCard(ctx))

        inner.addView(TextView(ctx).apply {
            text = "Недавно слушали"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 16f); it.bottomMargin = dp(ctx, 12f) }
        })

        val recentItems = listOf(
            "Rammstein" to "Zeit",
            "Земфира"   to "Ариво",
            "IC3PEAK"   to "Сказка"
        )
        for ((artist, title) in recentItems) {
            inner.addView(buildTrackRow(ctx, title, artist))
        }

        scroll.addView(inner)
        return scroll
    }

    private fun buildFeaturedCard(ctx: Context): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorInt("#1A237E"), colorInt("#6A1B9A"))
            ).apply { cornerRadius = dp(ctx, 16f).toFloat() }
            setPadding(dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 20f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 180f)
            ).also { it.bottomMargin = dp(ctx, 16f) }
        }
        card.addView(TextView(ctx).apply {
            text = "ВК Микс"
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 12f
            letterSpacing = 0.08f
        })
        card.addView(TextView(ctx).apply {
            text = "Ваш персональный микс"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 4f) }
        })
        card.addView(TextView(ctx).apply {
            text = "Подобрано специально для вас"
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 4f) }
        })
        return card
    }

    private fun buildTrackRow(ctx: Context, title: String, artist: String): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 8f), 0, dp(ctx, 8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )

            addView(FrameLayout(ctx).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(colorInt("#2C2C2E"), colorInt("#3A3A3C"))
                ).apply { cornerRadius = dp(ctx, 6f).toFloat() }
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f)).also {
                    it.marginEnd = dp(ctx, 12f)
                }
            })

            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(ctx).apply {
                    text = title; setTextColor(Color.WHITE); textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
                addView(TextView(ctx).apply {
                    text = artist; setTextColor(colorInt("#8E8E93")); textSize = 13f
                })
            })

            addView(TextView(ctx).apply {
                text = "···"
                setTextColor(colorInt("#8E8E93"))
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 8f), 0, 0, 0)
            })
        }
    }

    // ─────────────────────────── TAB: ОБЗОР ──────────────────────────────────

    private fun buildBrowseTab(): View {
        val ctx = this
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.BLACK)
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 16f), dp(ctx, 20f), dp(ctx, 16f))
        }

        inner.addView(TextView(ctx).apply {
            text = "Обзор"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 20f) }
        })

        val categories = listOf(
            Triple("Русская поп-музыка", "#D94F3D", "#FF8A65"),
            Triple("Электроника",        "#0A84FF", "#40C4FF"),
            Triple("Рок",                "#6D4C41", "#A1887F"),
            Triple("Подкасты",           "#2E7D32", "#66BB6A"),
            Triple("Новинки",            "#6A1B9A", "#AB47BC"),
            Triple("Ретро-хиты",         "#E65100", "#FFA726")
        )

        for (i in categories.indices step 2) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 100f)
                ).also { it.bottomMargin = dp(ctx, 12f) }
            }
            for (j in i until minOf(i + 2, categories.size)) {
                val (name, c1, c2) = categories[j]
                val card = FrameLayout(ctx).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
                    ).apply { cornerRadius = dp(ctx, 12f).toFloat() }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also {
                        if (j % 2 == 0) it.marginEnd = dp(ctx, 6f) else it.marginStart = dp(ctx, 6f)
                    }
                    setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
                    isClickable = true; isFocusable = true
                }
                card.addView(TextView(ctx).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START
                    )
                })
                row.addView(card)
            }
            inner.addView(row)
        }

        scroll.addView(inner)
        return scroll
    }

    // ─────────────────────────── TAB: РАДИО ──────────────────────────────────

    private fun buildRadioTab(): View {
        val ctx = this
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.BLACK)
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 16f), dp(ctx, 20f), dp(ctx, 16f))
        }

        inner.addView(TextView(ctx).apply {
            text = "Радио"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 20f) }
        })

        inner.addView(TextView(ctx).apply {
            text = "Яндекс Радио"
            setTextColor(colorInt("#8E8E93"))
            textSize = 12f
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 12f) }
        })

        val channels = listOf(
            "Моя волна",
            "Поп",
            "Рок",
            "Электроника",
            "Джаз",
            "Классика",
            "Хип-хоп",
            "Русский рок",
            "Ретро",
            "Романтика"
        )

        for (name in channels) {
            val genreColor = genreColorFor(name)
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(0, dp(ctx, 12f), 0, dp(ctx, 12f))
                isClickable = true; isFocusable = true
                setOnClickListener {
                    Toast.makeText(ctx, "Яндекс Радио: $name", Toast.LENGTH_SHORT).show()
                    openYandexRadio(name)
                }
            }
            row.addView(FrameLayout(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(genreColor)
                }
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 52f), dp(ctx, 52f)).also {
                    it.marginEnd = dp(ctx, 14f)
                }
                addView(TextView(ctx).apply {
                    text = name.take(1)
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                    )
                })
            })
            row.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(ctx).apply {
                    text = name; setTextColor(Color.WHITE); textSize = 16f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
                addView(TextView(ctx).apply {
                    text = "Яндекс Радио"; setTextColor(colorInt("#8E8E93")); textSize = 13f
                })
            })
            row.addView(ImageView(ctx).apply {
                val d = playDrawable(colorInt("#D94F3D"))
                d.setBounds(0, 0, dp(ctx, 16f), dp(ctx, 16f))
                setImageDrawable(d)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 32f), dp(ctx, 32f)).also {
                    it.marginStart = dp(ctx, 8f)
                }
            })
            inner.addView(row)
            inner.addView(View(ctx).apply {
                setBackgroundColor(colorInt("#1C1C1E"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            })
        }
        scroll.addView(inner)
        return scroll
    }

    // ─────────────────────────── TAB: БИБЛИОТЕКА ─────────────────────────────

    private fun buildLibraryTab(): View {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(TextView(ctx).apply {
            text = "Библиотека"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            setPadding(dp(ctx, 20f), dp(ctx, 16f), dp(ctx, 20f), dp(ctx, 16f))
        })

        if (tracks.isEmpty()) {
            root.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                addView(ImageView(ctx).apply {
                    val d = musicNoteDrawable(colorInt("#2C2C2E"))
                    d.setBounds(0, 0, dp(ctx, 80f), dp(ctx, 80f))
                    setImageDrawable(d)
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 80f), dp(ctx, 80f)).also {
                        it.gravity = Gravity.CENTER_HORIZONTAL
                    }
                })
                addView(TextView(ctx).apply {
                    text = "Нет аудиофайлов"
                    setTextColor(colorInt("#8E8E93"))
                    textSize = 17f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = dp(ctx, 12f) }
                })
                addView(TextView(ctx).apply {
                    text = "Добавьте музыку на устройство"
                    setTextColor(colorInt("#636366"))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = dp(ctx, 6f) }
                })
            })
        } else {
            val scroll = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 20f), 0, dp(ctx, 20f), dp(ctx, 20f))
            }
            inner.addView(TextView(ctx).apply {
                text = "${tracks.size} треков"
                setTextColor(colorInt("#8E8E93"))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(ctx, 12f) }
            })
            for ((i, track) in tracks.withIndex()) {
                val idx = i
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity     = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(ctx, 10f), 0, dp(ctx, 10f))
                    isClickable = true; isFocusable = true
                    setOnClickListener { playTrack(idx) }
                }
                row.addView(FrameLayout(ctx).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(colorInt("#1C1C1E"), colorInt("#2C2C2E"))
                    ).apply { cornerRadius = dp(ctx, 6f).toFloat() }
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f)).also {
                        it.marginEnd = dp(ctx, 12f)
                    }
                    addView(ImageView(ctx).apply {
                        val d = musicNoteDrawable(colorInt("#D94F3D"))
                        d.setBounds(0, 0, dp(ctx, 24f), dp(ctx, 24f))
                        setImageDrawable(d)
                        layoutParams = FrameLayout.LayoutParams(dp(ctx, 24f), dp(ctx, 24f)).also {
                            it.gravity = Gravity.CENTER
                        }
                    })
                })
                row.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(ctx).apply {
                        text = track.title; setTextColor(Color.WHITE); textSize = 15f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(TextView(ctx).apply {
                        text = "${track.artist} — ${track.album}"
                        setTextColor(colorInt("#8E8E93")); textSize = 12f
                        isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                })
                row.addView(TextView(ctx).apply {
                    text = formatDuration(track.duration)
                    setTextColor(colorInt("#8E8E93")); textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(dp(ctx, 8f), 0, 0, 0)
                })
                inner.addView(row)
                inner.addView(View(ctx).apply {
                    setBackgroundColor(colorInt("#1C1C1E"))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                })
            }
            scroll.addView(inner)
            root.addView(scroll)
        }
        return root
    }

    // ─────────────────────────── NOW PLAYING SCREEN ──────────────────────────

    private fun showNowPlaying() {
        if (currentIndex < 0 || currentIndex >= tracks.size) return
        val track = tracks[currentIndex]
        val ctx   = this

        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(colorInt("#0D0D0D"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        overlay.addView(View(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorInt("#1A237E"), colorInt("#000000"))
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        })

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            setPadding(dp(ctx, 24f), dp(ctx, 48f), dp(ctx, 24f), dp(ctx, 40f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        content.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                setColor(colorInt("#48FFFFFF"))
                cornerRadius = dp(ctx, 3f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 40f), dp(ctx, 5f)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(ctx, 32f)
            }
        })

        content.addView(TextView(ctx).apply {
            text = "Сейчас играет"
            setTextColor(colorInt("#8E8E93"))
            textSize = 12f
            gravity = Gravity.CENTER
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 20f) }
        })

        content.addView(FrameLayout(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorInt("#1A237E"), colorInt("#6A1B9A"))
            ).apply { cornerRadius = dp(ctx, 20f).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 280f), dp(ctx, 280f)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(ctx, 32f)
            }
            addView(ImageView(ctx).apply {
                val d = musicNoteDrawable(Color.parseColor("#33FFFFFF"))
                d.setBounds(0, 0, dp(ctx, 120f), dp(ctx, 120f))
                setImageDrawable(d)
                layoutParams = FrameLayout.LayoutParams(dp(ctx, 120f), dp(ctx, 120f)).also {
                    it.gravity = Gravity.CENTER
                }
            })
        })

        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 4f) }
        }
        titleRow.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(ctx).apply {
                text = track.title; setTextColor(Color.WHITE); textSize = 22f
                typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
                isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(TextView(ctx).apply {
                text = track.artist; setTextColor(colorInt("#8E8E93")); textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(ctx, 2f) }
            })
        })
        val heartImg = ImageView(ctx).apply {
            val d = heartDrawable(isFavourite, if (isFavourite) colorInt("#D94F3D") else Color.WHITE)
            d.setBounds(0, 0, dp(ctx, 28f), dp(ctx, 28f))
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f)).also {
                it.marginStart = dp(ctx, 8f)
                it.gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener {
                isFavourite = !isFavourite
                val nd = heartDrawable(isFavourite, if (isFavourite) colorInt("#D94F3D") else Color.WHITE)
                nd.setBounds(0, 0, dp(ctx, 28f), dp(ctx, 28f))
                setImageDrawable(nd)
            }
        }
        titleRow.addView(heartImg)
        content.addView(titleRow)

        val progressSeek = SeekBar(ctx).apply {
            max     = track.duration.toInt().coerceAtLeast(1)
            progress = mediaPlayer?.currentPosition ?: 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumb.setTint(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 16f) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) mediaPlayer?.seekTo(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        content.addView(progressSeek)

        val timeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 4f); it.bottomMargin = dp(ctx, 24f) }
        }
        val currentTimeLabel = TextView(ctx).apply {
            text = "0:00"; setTextColor(colorInt("#8E8E93")); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeRow.addView(currentTimeLabel)
        timeRow.addView(TextView(ctx).apply {
            text = formatDuration(track.duration)
            setTextColor(colorInt("#8E8E93")); textSize = 12f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        content.addView(timeRow)

        val progressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { mp ->
                    progressSeek.progress   = mp.currentPosition
                    currentTimeLabel.text   = formatDuration(mp.currentPosition.toLong())
                }
                mainHandler.postDelayed(this, 500)
            }
        }
        mainHandler.post(progressRunnable)

        val controls = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 24f) }
        }

        val shuffleBtn = TextView(ctx).apply {
            text = "RND"
            textSize = 14f
            setTextColor(if (isShuffled) Color.parseColor("#D94F3D") else Color.parseColor("#AAFFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val prevImg      = makeBigCtrlImgBtn(ctx, prevDrawable(Color.WHITE), dp(ctx, 28f))
        val ppSize       = dp(ctx, 40f)
        val playPauseImg = makeBigCtrlImgBtn(ctx,
            if (isPlaying) pauseDrawable(Color.WHITE) else playDrawable(Color.WHITE), ppSize)
        val nextImg      = makeBigCtrlImgBtn(ctx, nextDrawable(Color.WHITE), dp(ctx, 28f))

        val repeatBtn = TextView(ctx).apply {
            text = "RPT"
            textSize = 14f
            setTextColor(if (isRepeating) Color.parseColor("#D94F3D") else Color.parseColor("#AAFFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        shuffleBtn.setOnClickListener {
            isShuffled = !isShuffled
            shuffleBtn.setTextColor(if (isShuffled) colorInt("#D94F3D") else colorInt("#AAFFFFFF"))
        }
        prevImg.setOnClickListener {
            playPrev()
            val nd = if (isPlaying) pauseDrawable(Color.WHITE) else playDrawable(Color.WHITE)
            nd.setBounds(0, 0, ppSize, ppSize)
            playPauseImg.setImageDrawable(nd)
        }
        playPauseImg.setOnClickListener {
            togglePlayback()
            val nd = if (isPlaying) pauseDrawable(Color.WHITE) else playDrawable(Color.WHITE)
            nd.setBounds(0, 0, ppSize, ppSize)
            playPauseImg.setImageDrawable(nd)
        }
        nextImg.setOnClickListener {
            playNext()
            val nd = if (isPlaying) pauseDrawable(Color.WHITE) else playDrawable(Color.WHITE)
            nd.setBounds(0, 0, ppSize, ppSize)
            playPauseImg.setImageDrawable(nd)
        }
        repeatBtn.setOnClickListener {
            isRepeating = !isRepeating
            repeatBtn.setTextColor(if (isRepeating) colorInt("#D94F3D") else colorInt("#AAFFFFFF"))
        }

        controls.addView(shuffleBtn); controls.addView(prevImg); controls.addView(playPauseImg)
        controls.addView(nextImg);    controls.addView(repeatBtn)
        content.addView(controls)

        val extraRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        extraRow.addView(TextView(ctx).apply {
            text = "AirPlay"
            setTextColor(colorInt("#8E8E93")); textSize = 14f; gravity = Gravity.CENTER
            setPadding(0, 0, dp(ctx, 32f), 0)
            setOnClickListener { Toast.makeText(ctx, "AirPlay недоступен", Toast.LENGTH_SHORT).show() }
        })
        extraRow.addView(TextView(ctx).apply {
            text = "Текст"
            setTextColor(colorInt("#8E8E93")); textSize = 14f; gravity = Gravity.CENTER
            setOnClickListener { Toast.makeText(ctx, "Текст недоступен", Toast.LENGTH_SHORT).show() }
        })
        content.addView(extraRow)

        overlay.addView(content)

        var startY = 0f
        overlay.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { startY = ev.y; true }
                MotionEvent.ACTION_UP   -> {
                    if (ev.y - startY > 100) {
                        mainHandler.removeCallbacks(progressRunnable)
                        nowPlayingOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                        nowPlayingOverlay = null
                    }
                    true
                }
                else -> false
            }
        }

        nowPlayingOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        nowPlayingOverlay = overlay
        (rootLayout.parent as? ViewGroup)?.addView(overlay)
            ?: run { rootLayout.addView(overlay) }
    }

    private fun makeBigCtrlImgBtn(ctx: Context, drawable: android.graphics.drawable.Drawable, sizePx: Int): ImageButton {
        drawable.setBounds(0, 0, sizePx, sizePx)
        return ImageButton(ctx).apply {
            setImageDrawable(drawable)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, sizePx + dp(ctx, 16f), 1f).also {
                it.gravity = Gravity.CENTER_VERTICAL
            }
        }
    }

    // ─────────────────────────── PLAYBACK ────────────────────────────────────

    private fun playTrack(index: Int) {
        if (index < 0 || index >= tracks.size) return
        currentIndex = index
        val track = tracks[index]

        mediaPlayer?.stop()
        mediaPlayer?.release()

        requestAudioFocus()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(this@MusicActivity, track.uri)
                prepare()
                start()
                setOnCompletionListener {
                    if (isRepeating) seekTo(0).also { start() }
                    else playNext()
                }
            }
            isPlaying = true
            updateMiniBar(track)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось воспроизвести", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayback() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause(); isPlaying = false
        } else {
            mp.start(); isPlaying = true
        }
        val d = if (isPlaying) pauseDrawable(Color.WHITE) else playDrawable(Color.WHITE)
        d.setBounds(0, 0, dp(this, 22f), dp(this, 22f))
        miniPlayBtn.setImageDrawable(d)
    }

    private fun playNext() {
        if (tracks.isEmpty()) return
        val next = if (isShuffled) (0 until tracks.size).random()
        else (currentIndex + 1) % tracks.size
        playTrack(next)
    }

    private fun playPrev() {
        if (tracks.isEmpty()) return
        val prev = if (currentIndex > 0) currentIndex - 1 else tracks.size - 1
        playTrack(prev)
    }

    private fun updateMiniBar(track: Track) {
        miniBar.visibility = View.VISIBLE
        miniTitle.text  = track.title
        miniArtist.text = track.artist
        val d = if (isPlaying) pauseDrawable(Color.WHITE) else playDrawable(Color.WHITE)
        d.setBounds(0, 0, dp(this, 22f), dp(this, 22f))
        miniPlayBtn.setImageDrawable(d)
    }

    private fun requestAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnAudioFocusChangeListener { focus ->
                    when (focus) {
                        AudioManager.AUDIOFOCUS_LOSS -> { mediaPlayer?.pause(); isPlaying = false }
                        AudioManager.AUDIOFOCUS_GAIN -> { mediaPlayer?.start(); isPlaying = true }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { mediaPlayer?.pause() }
                    }
                }
            }.build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        }
    }

    // ─────────────────────────── MEDIA STORE ─────────────────────────────────

    private fun loadLocalTracks() {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val sort = "${MediaStore.Audio.Media.TITLE} ASC"
        tracks.clear()

        contentResolver.query(uri, proj, null, null, sort)?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id       = cursor.getLong(idCol)
                val trackUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                tracks.add(Track(
                    id       = id,
                    title    = cursor.getString(titleCol) ?: "Неизвестно",
                    artist   = cursor.getString(artistCol) ?: "Неизвестный исполнитель",
                    album    = cursor.getString(albumCol) ?: "",
                    duration = cursor.getLong(durationCol),
                    uri      = trackUri
                ))
            }
        }
    }

    // ─────────────────────────── PERMISSIONS ─────────────────────────────────

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), PERM_REQUEST
            )
        } else {
            loadLocalTracks()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadLocalTracks()
            if (currentTab == 3) switchTab(3)
        }
    }

    // ─────────────────────────── EXTERNAL APPS ───────────────────────────────

    private fun isAppInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun openVkMusic() {
        val pkg = when {
            isAppInstalled("com.vkontakte.android") -> "com.vkontakte.android"
            isAppInstalled("com.vk.vkdj")           -> "com.vk.vkdj"
            else -> return
        }
        startActivity(packageManager.getLaunchIntentForPackage(pkg) ?: return)
    }

    private fun openRuStoreVkMusic() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("rustore://apps/vkontakte.android")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://apps.rustore.ru/app/com.vkontakte.android")))
        }
    }

    private fun openYandexRadio(channel: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://music.yandex.ru/radio/user/onyourwave")))
        } catch (_: Exception) {}
    }

    // ─────────────────────────── LIFECYCLE ───────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}

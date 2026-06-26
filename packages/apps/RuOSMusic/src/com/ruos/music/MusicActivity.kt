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
    private lateinit var miniPlayBtn: TextView
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

    // ─────────────────────────── MAIN LAYOUT ─────────────────────────────────

    private fun buildMainLayout(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Content area
        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        rootLayout.addView(contentFrame)

        // Mini player bar
        miniBar = buildMiniBar()
        miniBar.visibility = View.GONE
        rootLayout.addView(miniBar)

        // Tab bar
        rootLayout.addView(buildTabBar())

        // Load initial tab
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

                    val icon = TextView(ctx).apply {
                        text = tabIcon(i)
                        textSize = 22f
                        gravity  = Gravity.CENTER
                        setTextColor(if (i == 0) colorInt("#D94F3D") else colorInt("#8E8E93"))
                    }
                    val label = TextView(ctx).apply {
                        text = name
                        textSize = 10f
                        gravity  = Gravity.CENTER
                        setTextColor(if (i == 0) colorInt("#D94F3D") else colorInt("#8E8E93"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.topMargin = dp(ctx, 2f) }
                    }
                    addView(icon); addView(label)

                    val tabIndex = i
                    setOnClickListener {
                        // Update all tab colors
                        val tabBar = parent as LinearLayout
                        for (j in 0 until tabBar.childCount) {
                            val t = tabBar.getChildAt(j) as LinearLayout
                            val ic = t.getChildAt(0) as TextView
                            val lb = t.getChildAt(1) as TextView
                            val c  = if (j == tabIndex) colorInt("#D94F3D") else colorInt("#8E8E93")
                            ic.setTextColor(c); lb.setTextColor(c)
                        }
                        switchTab(tabIndex)
                    }
                })
            }
        }
    }

    private fun tabIcon(i: Int) = when (i) {
        0 -> "▶"; 1 -> "◉"; 2 -> "📻"; 3 -> "♪"; else -> "?"
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

            // Album art placeholder
            addView(FrameLayout(ctx).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(colorInt("#1A237E"), colorInt("#6A1B9A"))
                ).apply { cornerRadius = dp(ctx, 6f).toFloat() }
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 46f), dp(ctx, 46f)).also {
                    it.marginEnd = dp(ctx, 12f)
                }
            })

            // Track info
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

            // Play/Pause
            miniPlayBtn = TextView(ctx).apply {
                text = "▶"
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 12f), 0, dp(ctx, 4f), 0)
                setOnClickListener { togglePlayback() }
            }
            addView(miniPlayBtn)

            // Next
            addView(TextView(ctx).apply {
                text = "⏭"
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 4f), 0, 0, 0)
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

        // VK Music card
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

        // Featured playlist card
        inner.addView(buildFeaturedCard(ctx))

        // Recently played section
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
            "Моя волна"      to "🌊",
            "Поп"            to "⭐",
            "Рок"            to "🎸",
            "Электроника"    to "⚡",
            "Джаз"           to "🎷",
            "Классика"       to "🎼",
            "Хип-хоп"        to "🎤",
            "Русский рок"    to "🇷🇺",
            "Ретро"          to "🕰️",
            "Романтика"      to "❤️"
        )

        for ((name, emoji) in channels) {
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
                background = roundCard(ctx, colorInt("#1C1C1E"), 10f)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 52f), dp(ctx, 52f)).also {
                    it.marginEnd = dp(ctx, 14f)
                }
                addView(TextView(ctx).apply {
                    text = emoji; textSize = 26f; gravity = Gravity.CENTER
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
            row.addView(TextView(ctx).apply {
                text = "▶"; setTextColor(colorInt("#D94F3D")); textSize = 16f
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
                addView(TextView(ctx).apply {
                    text = "♪"
                    textSize = 64f
                    setTextColor(colorInt("#2C2C2E"))
                    gravity = Gravity.CENTER
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
                    addView(TextView(ctx).apply {
                        text = "♪"; textSize = 20f; gravity = Gravity.CENTER
                        setTextColor(colorInt("#D94F3D"))
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                        )
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

        // Background gradient
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

        // Drag down indicator
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

        // "Сейчас играет" label
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

        // Large album art
        content.addView(FrameLayout(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorInt("#1A237E"), colorInt("#6A1B9A"))
            ).apply { cornerRadius = dp(ctx, 20f).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 280f), dp(ctx, 280f)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(ctx, 32f)
            }
            addView(TextView(ctx).apply {
                text = "♪"; textSize = 100f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#33FFFFFF"))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
        })

        // Title + heart row
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
        val heartBtn = TextView(ctx).apply {
            text = if (isFavourite) "♥" else "♡"
            textSize = 24f
            setTextColor(if (isFavourite) colorInt("#D94F3D") else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 8f), 0, 0, 0)
            setOnClickListener {
                isFavourite = !isFavourite
                text = if (isFavourite) "♥" else "♡"
                setTextColor(if (isFavourite) colorInt("#D94F3D") else Color.WHITE)
            }
        }
        titleRow.addView(heartBtn)
        content.addView(titleRow)

        // Progress bar
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

        // Time row
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

        // Update progress loop
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

        // Controls row
        val controls = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 24f) }
        }
        val shuffleBtn = makeBigCtrlBtn(ctx, "⇀", if (isShuffled) "#D94F3D" else "#AAFFFFFF")
        val prevBtn    = makeBigCtrlBtn(ctx, "⏮", "#FFFFFF")
        val playPauseBtn = makeBigCtrlBtn(ctx, if (isPlaying) "⏸" else "▶", "#FFFFFF").also { it.textSize = 36f }
        val nextBtn    = makeBigCtrlBtn(ctx, "⏭", "#FFFFFF")
        val repeatBtn  = makeBigCtrlBtn(ctx, "⇁", if (isRepeating) "#D94F3D" else "#AAFFFFFF")

        shuffleBtn.setOnClickListener {
            isShuffled = !isShuffled
            shuffleBtn.setTextColor(if (isShuffled) colorInt("#D94F3D") else colorInt("#AAFFFFFF"))
        }
        prevBtn.setOnClickListener { playPrev(); playPauseBtn.text = if (isPlaying) "⏸" else "▶" }
        playPauseBtn.setOnClickListener {
            togglePlayback()
            playPauseBtn.text = if (isPlaying) "⏸" else "▶"
        }
        nextBtn.setOnClickListener { playNext(); playPauseBtn.text = if (isPlaying) "⏸" else "▶" }
        repeatBtn.setOnClickListener {
            isRepeating = !isRepeating
            repeatBtn.setTextColor(if (isRepeating) colorInt("#D94F3D") else colorInt("#AAFFFFFF"))
        }

        controls.addView(shuffleBtn); controls.addView(prevBtn); controls.addView(playPauseBtn)
        controls.addView(nextBtn);    controls.addView(repeatBtn)
        content.addView(controls)

        // Extra row: Airplay | Lyrics
        val extraRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        extraRow.addView(TextView(ctx).apply {
            text = "📡 AirPlay"
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

        // Dismiss on swipe down
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

    private fun makeBigCtrlBtn(ctx: Context, symbol: String, colorHex: String): TextView =
        TextView(ctx).apply {
            text = symbol
            textSize = 28f
            setTextColor(Color.parseColor(colorHex))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
        miniPlayBtn.text = if (isPlaying) "⏸" else "▶"
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
        miniPlayBtn.text = if (isPlaying) "⏸" else "▶"
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

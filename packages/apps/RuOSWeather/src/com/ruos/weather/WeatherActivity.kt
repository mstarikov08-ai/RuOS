package com.ruos.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.*
import android.app.AlertDialog
import android.widget.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

// ─────────────────────────── DATA CLASSES ────────────────────────────────────

data class HourForecast(val hour: String, val condition: String, val temp: Int)
data class DayForecast(val day: String, val condition: String, val low: Int, val high: Int)
data class WeatherData(
    val city: String,
    val temp: Int,
    val condition: String,
    val feelsLike: Int,
    val high: Int,
    val low: Int,
    val uvIndex: Int,
    val sunrise: String,
    val sunset: String,
    val precipMm: Float,
    val hourly: List<HourForecast>,
    val daily: List<DayForecast>,
    val conditionCode: String   // "clear" | "cloudy" | "rain" | "snow" | "night"
)

// ──────────────────────────── HELPER EXTENSIONS ──────────────────────────────

private fun dp(ctx: Context, v: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

private fun sp(ctx: Context, v: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

private fun gradientBg(ctx: Context, top: Int, bottom: Int): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).also {
        it.cornerRadius = 0f
    }

private fun roundCard(ctx: Context, alpha: Int = 0x22): GradientDrawable =
    GradientDrawable().also {
        it.setColor(Color.argb(alpha, 255, 255, 255))
        it.cornerRadius = dp(ctx, 14).toFloat()
    }

// ──────────────────────────── WEATHER ICON DRAWABLE ──────────────────────────

class WeatherIconDrawable(private val type: String, private val tint: Int) : android.graphics.drawable.Drawable() {
    override fun draw(canvas: android.graphics.Canvas) {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = tint; style = android.graphics.Paint.Style.FILL
        }
        val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
        when (type) {
            "sun" -> {
                canvas.drawCircle(w*0.5f, h*0.5f, w*0.28f, p)
                p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = w*0.06f; p.strokeCap = android.graphics.Paint.Cap.ROUND
                for (i in 0..7) {
                    val a = Math.toRadians(i * 45.0)
                    val x1 = (w*0.5f + w*0.38f * Math.cos(a)).toFloat(); val y1 = (h*0.5f + h*0.38f * Math.sin(a)).toFloat()
                    val x2 = (w*0.5f + w*0.48f * Math.cos(a)).toFloat(); val y2 = (h*0.5f + h*0.48f * Math.sin(a)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, p)
                }
            }
            "cloud" -> {
                canvas.drawOval(android.graphics.RectF(w*0.15f, h*0.3f, w*0.65f, h*0.75f), p)
                canvas.drawOval(android.graphics.RectF(w*0.3f, h*0.15f, w*0.75f, h*0.55f), p)
                canvas.drawOval(android.graphics.RectF(w*0.5f, h*0.3f, w*0.9f, h*0.7f), p)
                canvas.drawRect(w*0.15f, h*0.55f, w*0.9f, h*0.75f, p)
            }
            "rain" -> {
                p.color = android.graphics.Color.parseColor("#636366")
                canvas.drawOval(android.graphics.RectF(w*0.1f, h*0.15f, w*0.6f, h*0.55f), p)
                canvas.drawOval(android.graphics.RectF(w*0.3f, h*0.05f, w*0.75f, h*0.45f), p)
                canvas.drawOval(android.graphics.RectF(w*0.5f, h*0.15f, w*0.9f, h*0.5f), p)
                canvas.drawRect(w*0.1f, h*0.4f, w*0.9f, h*0.55f, p)
                p.color = tint; p.strokeWidth = w*0.07f; p.style = android.graphics.Paint.Style.STROKE; p.strokeCap = android.graphics.Paint.Cap.ROUND
                canvas.drawLine(w*0.25f, h*0.65f, w*0.2f, h*0.8f, p)
                canvas.drawLine(w*0.5f, h*0.65f, w*0.45f, h*0.8f, p)
                canvas.drawLine(w*0.75f, h*0.65f, w*0.7f, h*0.8f, p)
                canvas.drawLine(w*0.35f, h*0.72f, w*0.3f, h*0.88f, p)
                canvas.drawLine(w*0.62f, h*0.72f, w*0.57f, h*0.88f, p)
            }
            "snow" -> {
                p.color = android.graphics.Color.parseColor("#636366")
                canvas.drawOval(android.graphics.RectF(w*0.1f, h*0.1f, w*0.65f, h*0.5f), p)
                canvas.drawOval(android.graphics.RectF(w*0.35f, h*0.0f, w*0.8f, h*0.4f), p)
                canvas.drawRect(w*0.1f, h*0.35f, w*0.85f, h*0.5f, p)
                p.color = tint; p.strokeWidth = w*0.07f; p.style = android.graphics.Paint.Style.STROKE; p.strokeCap = android.graphics.Paint.Cap.ROUND
                for (cx in listOf(w*0.3f, w*0.5f, w*0.7f)) {
                    val cy = h*0.72f
                    canvas.drawLine(cx, cy-h*0.1f, cx, cy+h*0.1f, p)
                    canvas.drawLine(cx-h*0.09f, cy, cx+h*0.09f, cy, p)
                    canvas.drawLine(cx-h*0.07f, cy-h*0.07f, cx+h*0.07f, cy+h*0.07f, p)
                    canvas.drawLine(cx+h*0.07f, cy-h*0.07f, cx-h*0.07f, cy+h*0.07f, p)
                }
            }
            "thunder" -> {
                p.color = android.graphics.Color.parseColor("#636366")
                canvas.drawOval(android.graphics.RectF(w*0.1f, h*0.1f, w*0.65f, h*0.5f), p)
                canvas.drawOval(android.graphics.RectF(w*0.35f, h*0.0f, w*0.8f, h*0.4f), p)
                canvas.drawRect(w*0.1f, h*0.35f, w*0.85f, h*0.5f, p)
                p.color = android.graphics.Color.parseColor("#FFD60A"); p.style = android.graphics.Paint.Style.FILL
                val bolt = android.graphics.Path()
                bolt.moveTo(w*0.55f, h*0.52f); bolt.lineTo(w*0.42f, h*0.7f); bolt.lineTo(w*0.52f, h*0.7f)
                bolt.lineTo(w*0.38f, h*0.92f); bolt.lineTo(w*0.62f, h*0.68f); bolt.lineTo(w*0.5f, h*0.68f)
                bolt.lineTo(w*0.62f, h*0.52f); bolt.close()
                canvas.drawPath(bolt, p)
            }
            "fog" -> {
                p.strokeWidth = w*0.08f; p.style = android.graphics.Paint.Style.STROKE; p.strokeCap = android.graphics.Paint.Cap.ROUND
                for (i in 0..4) {
                    val y = h*(0.2f + i*0.15f); val endX = if (i % 2 == 0) w*0.8f else w*0.7f
                    canvas.drawLine(w*0.1f, y, endX, y, p)
                }
            }
            "partly-cloudy" -> {
                p.color = android.graphics.Color.parseColor("#FFD60A")
                canvas.drawCircle(w*0.65f, h*0.6f, w*0.22f, p)
                p.color = tint
                canvas.drawOval(android.graphics.RectF(w*0.05f, h*0.2f, w*0.55f, h*0.6f), p)
                canvas.drawOval(android.graphics.RectF(w*0.2f, h*0.1f, w*0.65f, h*0.5f), p)
                canvas.drawRect(w*0.05f, h*0.45f, w*0.65f, h*0.6f, p)
            }
            "moon" -> {
                canvas.drawCircle(w*0.5f, h*0.5f, w*0.35f, p)
                p.color = android.graphics.Color.parseColor("#1C1C1E")
                canvas.drawCircle(w*0.62f, h*0.38f, w*0.28f, p)
            }
            else -> canvas.drawCircle(w*0.5f, h*0.5f, w*0.3f, p)
        }
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

// ──────────────────────────── MAIN ACTIVITY ──────────────────────────────────

class WeatherActivity : android.app.Activity() {

    private val YANDEX_API_KEY = "YANDEX_WEATHER_API_KEY"   // replace with real key
    private val mainHandler = Handler(Looper.getMainLooper())

    // Cities list
    private val cities = mutableListOf("Москва", "Санкт-Петербург", "Казань", "Екатеринбург")
    private var currentCityIndex = 0

    // Coordinates mapped to cities (Moscow default)
    private val cityCoords = mapOf(
        "Москва"          to Pair(55.7558, 37.6176),
        "Санкт-Петербург" to Pair(59.9311, 30.3609),
        "Казань"          to Pair(55.7887, 49.1221),
        "Екатеринбург"    to Pair(56.8389, 60.6057)
    )

    // Views we'll need to update
    private lateinit var rootFrame: FrameLayout
    private lateinit var weatherScrollView: ScrollView
    private lateinit var citiesView: LinearLayout
    private var weatherContentView: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootFrame)

        // Load saved cities
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        val savedCities = prefs.getString("cities", null)
        if (savedCities != null) {
            try {
                val arr = org.json.JSONArray(savedCities)
                cities.clear()
                for (i in 0 until arr.length()) cities.add(arr.getString(i))
            } catch (_: Exception) {}
        }

        buildWeatherScreen()
        loadWeather(cities[currentCityIndex])
    }

    // ──────────────────────────── SCREEN BUILDERS ────────────────────────────

    private fun buildWeatherScreen() {
        rootFrame.removeAllViews()

        // Gradient background (default: Moscow day)
        val bgView = View(this).apply {
            background = gradientBg(this@WeatherActivity, Color.parseColor("#1C4587"), Color.parseColor("#4FC3F7"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootFrame.addView(bgView)

        // Scroll view for weather content
        weatherScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }
        rootFrame.addView(weatherScrollView)

        // Detect swipe-down on scroll: when already at top, show cities list
        var lastY = 0f
        weatherScrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastY = event.y; false }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - lastY
                    if (dy > 60 && weatherScrollView.scrollY == 0) {
                        showCitiesView()
                        true
                    } else false
                }
                else -> false
            }
        }

        // Placeholder content shown while loading
        val loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(this@WeatherActivity, 200f), 0, 0)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        loadingLayout.addView(TextView(this).apply {
            text = "Загрузка..."
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        })
        weatherScrollView.addView(loadingLayout)

        // Float button: city list -- canvas-drawn hamburger menu
        val listBtn = ImageButton(this).apply {
            val d = menuDrawable(Color.WHITE)
            d.setBounds(0, 0, dp(this@WeatherActivity, 24f), dp(this@WeatherActivity, 24f))
            setImageDrawable(d)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(
                dp(this@WeatherActivity, 16f),
                dp(this@WeatherActivity, 48f),
                dp(this@WeatherActivity, 16f),
                dp(this@WeatherActivity, 8f)
            )
            setOnClickListener { showCitiesView() }
        }
        val listBtnParams = FrameLayout.LayoutParams(
            dp(this, 56f),
            dp(this, 80f),
            Gravity.TOP or Gravity.END
        )
        rootFrame.addView(listBtn, listBtnParams)
    }

    private fun menuDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = android.graphics.Paint.Style.STROKE
                strokeWidth = bounds.height()*0.11f; strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            canvas.drawLine(w*0.15f, h*0.28f, w*0.85f, h*0.28f, p)
            canvas.drawLine(w*0.15f, h*0.5f, w*0.85f, h*0.5f, p)
            canvas.drawLine(w*0.15f, h*0.72f, w*0.85f, h*0.72f, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun weatherIconView(condition: String, sizeDp: Int): ImageView {
        val ctx = this
        val (type, tint) = conditionToIconType(condition)
        return ImageView(ctx).apply {
            val d = WeatherIconDrawable(type, tint)
            d.setBounds(0, 0, dp(ctx, sizeDp.toFloat()), dp(ctx, sizeDp.toFloat()))
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(
                dp(ctx, sizeDp.toFloat()), dp(ctx, sizeDp.toFloat())
            ).also { it.topMargin = dp(ctx, 4f); it.bottomMargin = dp(ctx, 4f); it.gravity = Gravity.CENTER_HORIZONTAL }
        }
    }

    private fun updateWeatherUI(data: WeatherData) {
        // Update gradient
        val (topColor, bottomColor) = when (data.conditionCode) {
            "clear"  -> Color.parseColor("#1C4587") to Color.parseColor("#4FC3F7")
            "cloudy" -> Color.parseColor("#37474F") to Color.parseColor("#546E7A")
            "rain"   -> Color.parseColor("#1A237E") to Color.parseColor("#3949AB")
            "snow"   -> Color.parseColor("#37474F") to Color.parseColor("#78909C")
            "night"  -> Color.parseColor("#0D0D2B") to Color.parseColor("#1A1A3E")
            else     -> Color.parseColor("#1C4587") to Color.parseColor("#4FC3F7")
        }
        (rootFrame.getChildAt(0) as? View)?.background =
            gradientBg(this, topColor, bottomColor)

        // Build main content
        val content = buildWeatherContent(data)
        weatherContentView = content
        weatherScrollView.removeAllViews()
        weatherScrollView.addView(content)

        // Cache "temp° condition" in Settings.Secure for the lock-screen weather widget
        // (SystemUI LockScreenView reads "ruos_weather_now"). Guarded: without
        // WRITE_SECURE_SETTINGS this is a silent no-op.
        runCatching {
            android.provider.Settings.Secure.putString(
                contentResolver, "ruos_weather_now", "${data.temp}° ${data.condition}")
        }
    }

    private fun buildWeatherContent(data: WeatherData): LinearLayout {
        val ctx = this
        val pad = dp(ctx, 20f)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(ctx, 60f), pad, dp(ctx, 40f))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // CITY NAME
            addView(TextView(ctx).apply {
                text = data.city
                setTextColor(Color.WHITE)
                textSize = 36f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(ctx, 4f) }
            })

            // TEMPERATURE
            addView(TextView(ctx).apply {
                text = "${data.temp}°"
                setTextColor(Color.WHITE)
                textSize = 96f
                typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // CONDITION
            addView(TextView(ctx).apply {
                text = data.condition
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(ctx, 4f) }
            })

            // H / L
            addView(TextView(ctx).apply {
                text = "В: ${data.high}°  Н: ${data.low}°"
                setTextColor(Color.parseColor("#CCFFFFFF"))
                textSize = 16f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(ctx, 24f) }
            })

            // HOURLY FORECAST CARD
            addView(buildHourlyCard(data.hourly))

            // SEPARATOR
            addView(View(ctx).apply {
                setBackgroundColor(Color.parseColor("#40FFFFFF"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.topMargin = dp(ctx, 8f); it.bottomMargin = dp(ctx, 8f) }
            })

            // 7-DAY FORECAST CARD
            addView(buildDailyCard(data.daily))

            // 2x2 DETAIL CARDS
            addView(buildDetailCards(data))
        }
    }

    private fun buildHourlyCard(hourly: List<HourForecast>): View {
        val ctx = this
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundCard(ctx)
            setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 16f) }
        }

        card.addView(TextView(ctx).apply {
            text = "ПОЧАСОВОЙ ПРОГНОЗ"
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 11f
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 8f) }
        })

        val scrollRow = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (h in hourly) {
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 10f), dp(ctx, 4f), dp(ctx, 10f), dp(ctx, 4f))
            }
            cell.addView(TextView(ctx).apply {
                text = h.hour
                setTextColor(Color.parseColor("#CCFFFFFF"))
                textSize = 12f
                gravity = Gravity.CENTER
            })
            cell.addView(weatherIconView(h.condition, 28))
            cell.addView(TextView(ctx).apply {
                text = "${h.temp}°"
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
            })
            row.addView(cell)
        }

        scrollRow.addView(row)
        card.addView(scrollRow)
        return card
    }

    private fun buildDailyCard(daily: List<DayForecast>): View {
        val ctx = this
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundCard(ctx)
            setPadding(dp(ctx, 16f), dp(ctx, 12f), dp(ctx, 16f), dp(ctx, 12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 16f) }
        }

        card.addView(TextView(ctx).apply {
            text = "ПРОГНОЗ НА 7 ДНЕЙ"
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 11f
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 8f) }
        })

        val globalLow  = daily.minOf { it.low }
        val globalHigh = daily.maxOf { it.high }
        val range = (globalHigh - globalLow).coerceAtLeast(1)

        for ((index, d) in daily.withIndex()) {
            if (index > 0) {
                card.addView(View(ctx).apply {
                    setBackgroundColor(Color.parseColor("#33FFFFFF"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.topMargin = dp(ctx, 6f); it.bottomMargin = dp(ctx, 6f) }
                })
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 36f)
                )
            }

            // Day name
            row.addView(TextView(ctx).apply {
                text = d.day
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 90f), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            // Canvas weather icon
            val iconSize = dp(ctx, 24f)
            val (iconType, iconTint) = conditionToIconType(d.condition)
            row.addView(ImageView(ctx).apply {
                val drawable = WeatherIconDrawable(iconType, iconTint)
                drawable.setBounds(0, 0, iconSize, iconSize)
                setImageDrawable(drawable)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 36f), iconSize)
            })

            // Low temp
            row.addView(TextView(ctx).apply {
                text = "${d.low}°"
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 14f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 32f), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            // Progress bar container
            val barContainer = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(ctx, 6f), 1f).also {
                    it.marginStart = dp(ctx, 6f)
                    it.marginEnd  = dp(ctx, 6f)
                    it.gravity    = Gravity.CENTER_VERTICAL
                }
            }

            // Background bar
            barContainer.addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#33FFFFFF"))
                    cornerRadius = dp(ctx, 3f).toFloat()
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            })

            // Filled bar
            val startFrac = (d.low - globalLow).toFloat() / range
            val endFrac   = (d.high - globalLow).toFloat() / range
            val filledBar = View(ctx).apply {
                post {
                    val w = barContainer.width
                    val params = FrameLayout.LayoutParams(
                        ((endFrac - startFrac) * w).toInt().coerceAtLeast(dp(ctx, 4f)),
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    params.leftMargin = (startFrac * w).toInt()
                    layoutParams = params
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#4FC3F7"), Color.parseColor("#F9A825"))
                ).apply { cornerRadius = dp(ctx, 3f).toFloat() }
                layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            barContainer.addView(filledBar)
            row.addView(barContainer)

            // High temp
            row.addView(TextView(ctx).apply {
                text = "${d.high}°"
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.START
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 32f), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            card.addView(row)
        }
        return card
    }

    private fun buildDetailCards(data: WeatherData): View {
        val ctx = this
        val uvDesc = when {
            data.uvIndex <= 2  -> "Низкий"
            data.uvIndex <= 5  -> "Умеренный"
            data.uvIndex <= 7  -> "Высокий"
            data.uvIndex <= 10 -> "Очень высокий"
            else               -> "Экстремальный"
        }

        val cards = listOf(
            "УФ-ИНДЕКС"       to "${data.uvIndex}\n$uvDesc",
            "ВОСХОД / ЗАКАТ"  to "${data.sunrise}\n${data.sunset}",
            "ОСАДКИ"          to "${data.precipMm} мм\nОжидается",
            "ОЩУЩАЕТСЯ КАК"   to "${data.feelsLike}°\nОщущается"
        )

        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        for (i in cards.indices step 2) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(ctx, 12f) }
            }

            for (j in i until minOf(i + 2, cards.size)) {
                val (title, value) = cards[j]
                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundCard(ctx)
                    setPadding(dp(ctx, 14f), dp(ctx, 12f), dp(ctx, 14f), dp(ctx, 12f))
                    layoutParams = LinearLayout.LayoutParams(0, dp(ctx, 100f), 1f).also {
                        if (j % 2 == 0) it.marginEnd = dp(ctx, 6f)
                        else it.marginStart = dp(ctx, 6f)
                    }
                }
                card.addView(TextView(ctx).apply {
                    text = title
                    setTextColor(Color.parseColor("#99FFFFFF"))
                    textSize = 11f
                    letterSpacing = 0.06f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = dp(ctx, 6f) }
                })
                val lines = value.split("\n")
                card.addView(TextView(ctx).apply {
                    text = lines[0]
                    setTextColor(Color.WHITE)
                    textSize = 28f
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                })
                if (lines.size > 1) {
                    card.addView(TextView(ctx).apply {
                        text = lines[1]
                        setTextColor(Color.parseColor("#CCFFFFFF"))
                        textSize = 13f
                    })
                }
                row.addView(card)
            }
            outer.addView(row)
        }
        return outer
    }

    // ─────────────────────────── CITIES VIEW ──────────────────────────────────

    private fun showCitiesView() {
        rootFrame.removeAllViews()

        val bg = View(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootFrame.addView(bg)

        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        citiesView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@WeatherActivity, 20f), dp(this@WeatherActivity, 56f),
                dp(this@WeatherActivity, 20f), dp(this@WeatherActivity, 40f))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(this@WeatherActivity, 20f) }
        }

        header.addView(TextView(this).apply {
            text = "Погода"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        header.addView(TextView(this).apply {
            text = "+"
            setTextColor(Color.parseColor("#D94F3D"))
            textSize = 28f
            setPadding(dp(this@WeatherActivity, 8f), 0, 0, 0)
            setOnClickListener { showAddCityDialog() }
        })
        citiesView.addView(header)

        refreshCityCards()
        scroll.addView(citiesView)
        rootFrame.addView(scroll)
    }

    private fun refreshCityCards() {
        // Remove all cards (keep header at index 0)
        while (citiesView.childCount > 1) citiesView.removeViewAt(1)

        for ((idx, city) in cities.withIndex()) {
            val card = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    val (top, bot) = if (idx == currentCityIndex)
                        Color.parseColor("#1C4587") to Color.parseColor("#4FC3F7")
                    else
                        Color.parseColor("#1C1C1E") to Color.parseColor("#1C1C1E")
                    colors = intArrayOf(top, bot)
                    cornerRadius = dp(this@WeatherActivity, 16f).toFloat()
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(this@WeatherActivity, 100f)
                ).also { it.bottomMargin = dp(this@WeatherActivity, 12f) }
                setPadding(dp(this@WeatherActivity, 16f), dp(this@WeatherActivity, 16f),
                    dp(this@WeatherActivity, 16f), dp(this@WeatherActivity, 16f))
                isClickable = true
                isFocusable = true
            }

            card.addView(TextView(this).apply {
                text = city
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.START or Gravity.TOP
                )
            })

            card.addView(TextView(this).apply {
                text = if (idx == currentCityIndex) "Выбрано" else "Нажмите для выбора"
                setTextColor(Color.parseColor("#CCFFFFFF"))
                textSize = 13f
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.START or Gravity.BOTTOM
                )
            })

            val cityIdx = idx
            card.setOnClickListener {
                currentCityIndex = cityIdx
                buildWeatherScreen()
                loadWeather(cities[currentCityIndex])
            }

            citiesView.addView(card)
        }
    }

    private fun showAddCityDialog() {
        val ctx = this
        val input = EditText(ctx).apply {
            hint = "Введите город"
            setHintTextColor(Color.parseColor("#8E8E93"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = dp(ctx, 8f).toFloat()
            }
            setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
        }

        AlertDialog.Builder(ctx)
            .setTitle("Добавить город")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                val city = input.text.toString().trim()
                if (city.isNotEmpty() && !cities.contains(city)) {
                    cities.add(city)
                    saveCities()
                    refreshCityCards()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun saveCities() {
        val arr = org.json.JSONArray(cities)
        getSharedPreferences("weather_prefs", MODE_PRIVATE).edit()
            .putString("cities", arr.toString()).apply()
    }

    // ─────────────────────────── NETWORK ─────────────────────────────────────

    private fun loadWeather(city: String) {
        val coords = cityCoords[city] ?: Pair(55.7558, 37.6176)
        Thread {
            val data = try {
                fetchYandexWeather(coords.first, coords.second, city)   // if a Yandex key is set
            } catch (_: Exception) {
                try {
                    fetchOpenMeteo(coords.first, coords.second, city)   // keyless real data
                } catch (_: Exception) {
                    buildMockData(city)                                 // offline: last resort
                }
            }
            mainHandler.post { updateWeatherUI(data) }
        }.start()
    }

    private fun fetchYandexWeather(lat: Double, lon: Double, city: String): WeatherData {
        if (YANDEX_API_KEY == "YANDEX_WEATHER_API_KEY") throw Exception("No API key")

        val url = URL("https://api.weather.yandex.ru/v2/forecast?lat=$lat&lon=$lon&lang=ru_RU&limit=7&hours=true")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("X-Yandex-API-Key", YANDEX_API_KEY)
        conn.connectTimeout = 10_000
        conn.readTimeout    = 10_000

        val json = if (conn.responseCode == 200) {
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } else throw Exception("HTTP ${conn.responseCode}")

        return parseYandexWeather(JSONObject(json), city)
    }

    private fun parseYandexWeather(root: JSONObject, city: String): WeatherData {
        val fact     = root.getJSONObject("fact")
        val temp     = fact.getInt("temp")
        val feelsLike= fact.getInt("feels_like")
        val condCode = fact.getString("condition")
        val uvIdx    = if (fact.has("uv_index")) fact.getInt("uv_index") else 3

        val forecasts = root.getJSONArray("forecasts")
        val todayFc   = forecasts.getJSONObject(0)
        val parts     = todayFc.getJSONObject("parts")
        val dayPart   = parts.optJSONObject("day") ?: parts.optJSONObject("day_short")!!
        val high      = dayPart.getInt("temp_max")
        val low       = dayPart.getInt("temp_min")

        val sunrise   = todayFc.optString("sunrise", "06:00")
        val sunset    = todayFc.optString("sunset", "21:00")
        val precipMm  = todayFc.optJSONObject("parts")
            ?.optJSONObject("day")?.optDouble("prec_mm", 0.0)?.toFloat() ?: 0f

        // Hourly
        val hourlyList = mutableListOf<HourForecast>()
        val hoursArr   = todayFc.optJSONArray("hours")
        if (hoursArr != null) {
            for (i in 0 until minOf(24, hoursArr.length())) {
                val h   = hoursArr.getJSONObject(i)
                val hr  = h.getString("hour") + ":00"
                val ec  = h.getString("condition")
                val tmp = h.getInt("temp")
                hourlyList.add(HourForecast(hr, conditionToIconCode(ec), tmp))
            }
        }

        // 7-day
        val dayNames  = listOf("Вс","Пн","Вт","Ср","Чт","Пт","Сб")
        val dailyList = mutableListOf<DayForecast>()
        for (i in 0 until minOf(7, forecasts.length())) {
            val fc  = forecasts.getJSONObject(i)
            val cal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, i) }
            val day = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val dp2 = fc.getJSONObject("parts").optJSONObject("day")
                ?: fc.getJSONObject("parts").optJSONObject("day_short")!!
            dailyList.add(DayForecast(
                if (i == 0) "Сегодня" else day,
                conditionToIconCode(dp2.getString("condition")),
                dp2.getInt("temp_min"), dp2.getInt("temp_max")
            ))
        }

        val condStr   = conditionToRussian(condCode)
        val condColor = conditionToColorCode(condCode)

        return WeatherData(city, temp, condStr, feelsLike, high, low,
            uvIdx, sunrise, sunset, precipMm, hourlyList, dailyList, condColor)
    }

    // ──────────────────── KEYLESS REAL DATA (open-meteo) ─────────────────────
    // open-meteo.com needs no API key, so weather is real out-of-the-box. Used when no
    // Yandex key is configured; falls through to mock only if the network is unavailable.

    private fun fetchOpenMeteo(lat: Double, lon: Double, city: String): WeatherData {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code" +
            "&hourly=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,uv_index_max" +
            "&timezone=auto&forecast_days=7")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        val json = if (conn.responseCode == 200)
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        else throw Exception("HTTP ${conn.responseCode}")
        return parseOpenMeteo(JSONObject(json), city)
    }

    private fun parseOpenMeteo(root: JSONObject, city: String): WeatherData {
        val cur = root.getJSONObject("current")
        val temp = cur.getDouble("temperature_2m").toInt()
        val feels = cur.getDouble("apparent_temperature").toInt()
        val curCond = wmoToCondition(cur.getInt("weather_code"))

        val daily = root.getJSONObject("daily")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dCode = daily.getJSONArray("weather_code")
        val high = dMax.getDouble(0).toInt(); val low = dMin.getDouble(0).toInt()
        val sunrise = isoTime(daily.getJSONArray("sunrise").getString(0))
        val sunset  = isoTime(daily.getJSONArray("sunset").getString(0))
        val precip  = daily.getJSONArray("precipitation_sum").optDouble(0, 0.0).toFloat()
        val uv      = daily.getJSONArray("uv_index_max").optDouble(0, 0.0).toInt()

        // Hourly: open-meteo lists from 00:00 today, so today's hour == array index.
        val hourly = root.getJSONObject("hourly")
        val hTime = hourly.getJSONArray("time"); val hTemp = hourly.getJSONArray("temperature_2m")
        val hCode = hourly.getJSONArray("weather_code")
        val startIdx = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hourlyList = mutableListOf<HourForecast>()
        for (i in startIdx until minOf(startIdx + 24, hTime.length())) {
            hourlyList.add(HourForecast(
                isoTime(hTime.getString(i)),
                conditionToIconCode(wmoToCondition(hCode.getInt(i))),
                hTemp.getDouble(i).toInt()))
        }

        val dayNames = listOf("Вс","Пн","Вт","Ср","Чт","Пт","Сб")
        val dailyList = mutableListOf<DayForecast>()
        for (i in 0 until minOf(7, dCode.length())) {
            val cal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, i) }
            dailyList.add(DayForecast(
                if (i == 0) "Сегодня" else dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1],
                conditionToIconCode(wmoToCondition(dCode.getInt(i))),
                dMin.getDouble(i).toInt(), dMax.getDouble(i).toInt()))
        }

        return WeatherData(city, temp, conditionToRussian(curCond), feels, high, low,
            uv, sunrise, sunset, precip, hourlyList, dailyList, conditionToColorCode(curCond))
    }

    /** ISO "2026-06-27T05:47" → "05:47". */
    private fun isoTime(iso: String): String =
        if (iso.length >= 16 && iso.contains('T')) iso.substring(11, 16) else iso

    /** WMO weather code → the condition vocabulary used by the converters. */
    private fun wmoToCondition(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "partly-cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55, 56, 57 -> "light-rain"
        61, 63, 66, 67, 80, 81, 82 -> "rain"
        65 -> "heavy-rain"
        71, 77, 85 -> "light-snow"
        73, 75, 86 -> "snow"
        95, 96, 99 -> "thunderstorm"
        else -> "partly-cloudy"
    }

    // ─────────────────────────── MOCK DATA ───────────────────────────────────

    private fun buildMockData(city: String): WeatherData {
        val now   = Calendar.getInstance()
        val hour  = now.get(Calendar.HOUR_OF_DAY)
        val cond  = if (hour in 7..20) "cloudy" else "night"

        val hourlyList = (0..23).map { h ->
            val t  = when (h) {
                in 0..5   -> 11; in 6..9 -> 13; in 10..15 -> 18
                in 16..19 -> 17; else     -> 13
            }
            val c = if (h in 6..20) "partly-cloudy" else "moon"
            HourForecast(String.format("%02d:00", h), c, t)
        }

        val dayNames   = listOf("Вс","Пн","Вт","Ср","Чт","Пт","Сб")
        val conditions = listOf("partly-cloudy", "cloud", "rain", "partly-cloudy", "sun", "partly-cloudy", "cloud")
        val dailyList  = (0..6).map { i ->
            val cal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, i) }
            val day = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
            DayForecast(
                if (i == 0) "Сегодня" else day,
                conditions[i], 11 + i, 18 + i
            )
        }

        return WeatherData(
            city = city,
            temp = 18,
            condition = "Облачно с прояснениями",
            feelsLike = 16,
            high = 21,
            low = 11,
            uvIndex = 3,
            sunrise = "05:47",
            sunset = "21:32",
            precipMm = 0.4f,
            hourly = hourlyList,
            daily = dailyList,
            conditionCode = cond
        )
    }

    // ─────────────────────────── HELPERS ────────────────────────────────────

    private fun conditionToIconCode(code: String): String = when {
        code.contains("clear")         -> "sun"
        code.contains("partly-cloudy") -> "partly-cloudy"
        code.contains("cloudy")        -> "cloud"
        code.contains("overcast")      -> "cloud"
        code.contains("rain")          -> "rain"
        code.contains("drizzle")       -> "rain"
        code.contains("thunder")       -> "thunder"
        code.contains("snow")          -> "snow"
        code.contains("hail")          -> "snow"
        code.contains("fog")           -> "fog"
        else                           -> "partly-cloudy"
    }

    private fun conditionToIconType(condition: String): Pair<String, Int> = when (condition) {
        "sun"           -> "sun" to Color.parseColor("#FFD60A")
        "partly-cloudy" -> "partly-cloudy" to Color.WHITE
        "cloud"         -> "cloud" to Color.parseColor("#AEAEB2")
        "rain"          -> "rain" to Color.parseColor("#0A84FF")
        "thunder"       -> "thunder" to Color.parseColor("#AEAEB2")
        "snow"          -> "snow" to Color.WHITE
        "fog"           -> "fog" to Color.parseColor("#AEAEB2")
        "moon"          -> "moon" to Color.WHITE
        else            -> "partly-cloudy" to Color.WHITE
    }

    private fun conditionToRussian(code: String) = when {
        code.contains("clear")              -> "Ясно"
        code.contains("partly-cloudy")      -> "Переменная облачность"
        code.contains("cloudy")             -> "Облачно с прояснениями"
        code.contains("overcast")           -> "Пасмурно"
        code.contains("light-rain")         -> "Небольшой дождь"
        code.contains("rain")               -> "Дождь"
        code.contains("heavy-rain")         -> "Сильный дождь"
        code.contains("showers")            -> "Ливень"
        code.contains("thunderstorm")       -> "Гроза"
        code.contains("hail")               -> "Град"
        code.contains("light-snow")         -> "Небольшой снег"
        code.contains("snow-showers")       -> "Снегопад"
        code.contains("snow")               -> "Снег"
        code.contains("blizzard")           -> "Метель"
        code.contains("fog")                -> "Туман"
        else                                -> "Переменная облачность"
    }

    private fun conditionToColorCode(code: String) = when {
        code.contains("clear")   -> "clear"
        code.contains("snow")    -> "snow"
        code.contains("rain")    -> "rain"
        code.contains("thunder") -> "rain"
        code.contains("fog")     -> "cloudy"
        else                     -> "cloudy"
    }
}

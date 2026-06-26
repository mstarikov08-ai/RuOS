package com.ruos.demo

import android.app.Activity
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class NotificationCenterDemoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0A0A")) }

        root.addView(TextView(this).apply {
            text = "← Swipe down from top-left"
            setTextColor(Color.argb(60, 255, 255, 255))
            gravity = Gravity.CENTER
            textSize = 14f
        }, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val nc = NotificationCenterPanel(this) { finish() }
        root.addView(nc, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.85f).toInt()
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.topMargin = dp(52)
            it.leftMargin = dp(10)
        })

        nc.translationY = -600f
        SpringAnimation(nc, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            startDelay = 80
            start()
        }

        root.setOnClickListener { finish() }
        nc.setOnClickListener { }
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

private class NotificationCenterPanel(
    context: android.content.Context,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 28, 28, 30)
    }
    private val rect = RectF()
    private val corner = 20f * density

    // Sample notifications
    private val fakeNotifs = listOf(
        FakeNotif("ВКонтакте", "Дмитрий Ларин", "Привет! Как дела? Что делаешь вечером?", "сейчас"),
        FakeNotif("ВКонтакте", "Команда VK", "Новые фото в альбоме «Лето 2024»", "2 мин"),
        FakeNotif("Яндекс Браузер", "Яндекс.Новости", "Курс доллара снизился на 0.5 рубля", "5 мин"),
        FakeNotif("RuStore", "Обновления", "3 приложения обновились в фоне", "12 мин"),
        FakeNotif("Госуслуги", "Уведомление", "Ваш документ готов к получению", "1 ч"),
        FakeNotif("Яндекс Почта", "mstarikov@yandex.ru", "Подтверждение заказа №12847: Ваш заказ...", "3 ч"),
    )

    private lateinit var cardContainer: LinearLayout

    init {
        setWillNotDraw(false)
        clipChildren = false

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Header row
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        header.addView(TextView(context).apply {
            text = todayDate()
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = "Очистить все"
            textSize = 13f
            setTextColor(Color.argb(160, 255, 255, 255))
            isClickable = true
            setOnClickListener { cardContainer.removeAllViews(); }
        })
        content.addView(header)

        // Cards
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        cardContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        groupByApp(fakeNotifs).forEach { (appName, notifs) ->
            cardContainer.addView(NotifCard(context, appName, notifs).also { card ->
                card.onSwipeDismiss = { cardContainer.removeView(card) }
            })
            cardContainer.addView(spacer(8))
        }
        scroll.addView(cardContainer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        content.addView(scroll)

        addView(content, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        canvas.drawRoundRect(rect, corner, corner, bgPaint)
    }

    private fun groupByApp(list: List<FakeNotif>): Map<String, List<FakeNotif>> =
        list.groupBy { it.appName }

    private fun todayDate(): String {
        val cal = java.util.Calendar.getInstance()
        val days = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
        val months = arrayOf("янв", "фев", "мар", "апр", "май", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек")
        return "${days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]}, " +
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${months[cal.get(java.util.Calendar.MONTH)]}"
    }

    private fun spacer(dpH: Int) = View(context).also {
        it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(dpH))
    }

    private fun dp(v: Int) = (v * density).toInt()
}

private data class FakeNotif(val appName: String, val title: String, val body: String, val time: String)

private class NotifCard(
    context: android.content.Context,
    appName: String,
    notifs: List<FakeNotif>
) : LinearLayout(context) {

    var onSwipeDismiss: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var dragging = false

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(Color.argb(160, 44, 44, 46))
        }
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        setPadding(dp(14), dp(10), dp(14), dp(10))

        // App label
        addView(TextView(context).apply {
            text = appName.uppercase()
            textSize = 11f
            setTextColor(Color.argb(140, 255, 255, 255))
            letterSpacing = 0.06f
            setPadding(0, 0, 0, dp(6))
        })

        notifs.forEach { n ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }.also { row ->
                row.addView(LinearLayout(context).apply {
                    orientation = VERTICAL
                    addView(TextView(context).apply {
                        text = n.title; textSize = 14f; setTextColor(Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(context).apply {
                        text = n.body; textSize = 13f; setTextColor(Color.argb(200, 255, 255, 255))
                        maxLines = 2
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(context).apply {
                    text = n.time; textSize = 12f; setTextColor(Color.argb(120, 255, 255, 255))
                    setPadding(dp(8), 0, 0, 0)
                })
            })
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downX = event.x; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                if (!dragging && dx < -10f) { dragging = true; parent?.requestDisallowInterceptTouchEvent(true) }
                if (dragging && dx < 0) translationX = dx
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event); velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle(); velocityTracker = null
                if (dragging && (vx < -600f || translationX < -width * 0.4f)) {
                    animate().translationX(-width.toFloat()).alpha(0f).setDuration(220).withEndAction { onSwipeDismiss?.invoke() }.start()
                } else {
                    SpringAnimation(this, SpringAnimation.TRANSLATION_X, 0f).apply {
                        spring.stiffness = SpringForce.STIFFNESS_MEDIUM; spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY; start()
                    }
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle(); velocityTracker = null
                SpringAnimation(this, SpringAnimation.TRANSLATION_X, 0f).apply {
                    spring.stiffness = SpringForce.STIFFNESS_MEDIUM; start()
                }; dragging = false
            }
        }
        return true
    }

    private fun dp(v: Int) = (v * density).toInt()
}

package com.ruos.settings.sections

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Конфиденциальность — iOS-style Privacy: a list of permission categories (Геолокация,
 * Камера, Микрофон…). Tapping one opens [PermissionDetailActivity] listing every app that
 * requests it, with a switch to grant/revoke.
 */
class PrivacyActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)

    data class Category(val title: String, val color: Int, val perms: Array<String>)

    private val categories = listOf(
        Category("Геолокация", 0xFF34C759.toInt(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
        Category("Камера", 0xFF8E8E93.toInt(), arrayOf(Manifest.permission.CAMERA)),
        Category("Микрофон", 0xFFFF9500.toInt(), arrayOf(Manifest.permission.RECORD_AUDIO)),
        Category("Контакты", 0xFF0A84FF.toInt(), arrayOf(Manifest.permission.READ_CONTACTS)),
        Category("Календарь", 0xFFFF3B30.toInt(), arrayOf(Manifest.permission.READ_CALENDAR)),
        Category("Фото", 0xFFAF52DE.toInt(), arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE)),
        Category("Уведомления", 0xFFFF453A.toInt(), arrayOf(Manifest.permission.POST_NOTIFICATIONS)),
        Category("Телефон", 0xFF34C759.toInt(), arrayOf(Manifest.permission.READ_PHONE_STATE)),
        Category("SMS", 0xFF34C759.toInt(), arrayOf(Manifest.permission.READ_SMS)),
        Category("Физическая активность", 0xFFFF2D55.toInt(), arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7")); setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title("Конфиденциальность"))
        col.addView(note("Здесь видно, какие приложения запрашивали доступ к данным. " +
            "Нажмите категорию, чтобы изменить разрешения."))

        val cardCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
        }
        categories.forEachIndexed { i, cat ->
            cardCol.addView(catRow(cat))
            if (i < categories.size - 1) cardCol.addView(divider())
        }
        col.addView(LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(cardCol,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) })

        col.addView(note("Зелёная и оранжевая точки в статус-баре показывают активное " +
            "использование камеры и микрофона."))

        // Access timeline (iOS App Privacy Report).
        val reportCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
            addView(LinearLayout(this@PrivacyActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                setOnClickListener { startActivity(Intent(this@PrivacyActivity, PrivacyTimelineActivity::class.java)) }
                addView(TextView(this@PrivacyActivity).apply { text = "Отчёт о конфиденциальности"; setTextColor(Color.BLACK); textSize = 17f; typeface = golos },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@PrivacyActivity).apply { text = "›"; setTextColor(Color.parseColor("#C7C7CC")); textSize = 20f })
            })
        }
        col.addView(LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(reportCard,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) })
        col.addView(note("Показывает, какие приложения недавно использовали камеру, микрофон и геолокацию."))

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun catRow(cat: Category): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true
        setOnClickListener {
            startActivity(Intent(this@PrivacyActivity, PermissionDetailActivity::class.java)
                .putExtra("title", cat.title).putExtra("perms", cat.perms))
        }
        addView(View(this@PrivacyActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(cat.color) }
        }, LinearLayout.LayoutParams(dp(28), dp(28)).also { it.marginEnd = dp(12) })
        addView(TextView(this@PrivacyActivity).apply { text = cat.title; setTextColor(Color.BLACK); textSize = 17f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@PrivacyActivity).apply { text = "›"; setTextColor(Color.parseColor("#C7C7CC")); textSize = 20f })
    }

    private fun divider() = View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
        .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { p -> p.marginStart = dp(56) } }
    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(10), dp(32), dp(8))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

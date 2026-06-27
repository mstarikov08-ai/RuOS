package com.ruos.emergency.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.emergency.store.EmergencyStore
import com.ruos.emergency.store.MedicalId

/**
 * Read-only Medical ID, designed to be opened over the lock screen (showWhenLocked) so
 * first responders can see allergies / conditions / blood type and call an emergency
 * contact without unlocking — exactly like iOS Medical ID.
 */
class MedicalIdViewActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        val store = EmergencyStore(this)
        val m = store.medicalId()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = "Медкарта"; setTextColor(Color.WHITE); textSize = 30f; typeface = Fonts.bold; setPadding(0, 0, 0, dp(12f))
        })

        if (m.isEmpty() && store.contacts().isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Медкарта не заполнена."; setTextColor(0xFF8E8E93.toInt()); textSize = 16f; typeface = Fonts.regular
            })
        } else {
            val rows = ArrayList<Pair<String, String>>()
            if (m.name.isNotBlank()) rows.add("Имя" to m.name)
            if (m.dob.isNotBlank()) rows.add("Дата рождения" to m.dob)
            if (m.bloodType.isNotBlank()) rows.add("Группа крови" to m.bloodType)
            if (m.height.isNotBlank()) rows.add("Рост" to m.height)
            if (m.weight.isNotBlank()) rows.add("Вес" to m.weight)
            if (m.organDonor) rows.add("Донор органов" to "Да")
            if (rows.isNotEmpty()) col.addView(card(rows, Color.WHITE))

            val medical = ArrayList<Pair<String, String>>()
            if (m.allergies.isNotBlank()) medical.add("Аллергии и реакции" to m.allergies)
            if (m.conditions.isNotBlank()) medical.add("Заболевания" to m.conditions)
            if (m.medications.isNotBlank()) medical.add("Лекарства" to m.medications)
            if (m.notes.isNotBlank()) medical.add("Примечания" to m.notes)
            if (medical.isNotEmpty()) { col.addView(label("МЕДИЦИНСКИЕ ДАННЫЕ")); col.addView(card(medical, 0xFFFF453A.toInt())) }

            val contacts = store.contacts()
            if (contacts.isNotEmpty()) {
                col.addView(label("ЭКСТРЕННЫЕ КОНТАКТЫ"))
                val cc = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
                }
                contacts.forEach { c ->
                    cc.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
                        addView(LinearLayout(this@MedicalIdViewActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(TextView(this@MedicalIdViewActivity).apply { text = c.name; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.medium })
                            addView(TextView(this@MedicalIdViewActivity).apply { text = c.relation; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular })
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        addView(TextView(this@MedicalIdViewActivity).apply {
                            text = "Позвонить"; setTextColor(0xFF34C759.toInt()); textSize = 15f; typeface = Fonts.medium
                            isClickable = true; setOnClickListener {
                                runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        })
                    })
                }
                col.addView(cc)
            }
        }

        col.addView(TextView(this).apply {
            text = "Экстренный вызов 112"; setTextColor(Color.WHITE); textSize = 17f; typeface = Fonts.medium; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(0xFFFF3B30.toInt()) }
            setPadding(0, dp(14f), 0, dp(14f)); isClickable = true
            setOnClickListener { startActivity(Intent(this@MedicalIdViewActivity, SosActivity::class.java)) }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(20f); layoutParams = lp
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun card(rows: List<Pair<String, String>>, valueColor: Int): View {
        val cc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
        }
        rows.forEach { (k, v) ->
            cc.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(0, dp(8f), 0, dp(8f))
                addView(TextView(this@MedicalIdViewActivity).apply { text = k; setTextColor(0xFF8E8E93.toInt()); textSize = 12f; typeface = Fonts.medium })
                addView(TextView(this@MedicalIdViewActivity).apply { text = v; setTextColor(valueColor); textSize = 16f; typeface = Fonts.regular })
            })
        }
        return cc
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 12f; typeface = Fonts.medium; setPadding(dp(4f), dp(16f), 0, dp(6f))
    }
}

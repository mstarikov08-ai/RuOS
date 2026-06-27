package com.ruos.keyboard

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * iOS «Замена текста» management screen. Lists the user's shortcut→phrase rules, each
 * removable, with an add form at the bottom. Backed by [TextReplacementStore]; changes are
 * live for the keyboard on the next word boundary.
 */
class TextReplacementActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private lateinit var store: TextReplacementStore
    private lateinit var listCol: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TextReplacementStore(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(60), 0, dp(40))
        }
        col.addView(title("Замена текста"))
        col.addView(note("Введённое сокращение автоматически заменяется на фразу. Например, «спс» → «спасибо»."))

        col.addView(sectionLabel("СОКРАЩЕНИЯ"))
        listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listCol)

        col.addView(sectionLabel("НОВОЕ"))
        col.addView(addForm())

        setContentView(ScrollView(this).apply { addView(col) })
        rebuild()
    }

    private fun rebuild() {
        listCol.removeAllViews()
        val rules = store.all()
        if (rules.isEmpty()) { listCol.addView(note("Нет сокращений.")); return }
        rules.forEach { r ->
            listCol.addView(card(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(this@TextReplacementActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@TextReplacementActivity).apply {
                        text = r.phrase; setTextColor(Color.BLACK); textSize = 16f; typeface = golos
                    })
                    addView(TextView(this@TextReplacementActivity).apply {
                        text = r.shortcut; setTextColor(Color.parseColor("#8E8E93")); textSize = 13f; typeface = golos
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@TextReplacementActivity).apply {
                    text = "Удалить"; setTextColor(Color.parseColor("#FF3B30")); textSize = 14f; typeface = golos
                    isClickable = true; setOnClickListener { store.delete(r.shortcut); rebuild() }
                })
            }))
        }
    }

    private fun addForm(): View {
        val phraseF = field("Фраза")
        val shortcutF = field("Сокращение")
        val add = TextView(this).apply {
            text = "Добавить"; setTextColor(Color.parseColor("#0A84FF")); textSize = 16f; typeface = golosM
            gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14)); isClickable = true
            setOnClickListener {
                val sc = shortcutF.text.toString().trim(); val ph = phraseF.text.toString().trim()
                if (sc.isNotEmpty() && ph.isNotEmpty()) {
                    store.upsert(Replacement(sc, ph)); shortcutF.text = null; phraseF.text = null; rebuild()
                }
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card(phraseF)); addView(card(shortcutF)); addView(card(add))
        }
    }

    private fun field(hint: String) = EditText(this).apply {
        setHint(hint); setHintTextColor(Color.parseColor("#8E8E93")); setTextColor(Color.BLACK)
        textSize = 16f; typeface = golos; inputType = InputType.TYPE_CLASS_TEXT
        background = null
    }

    private fun card(inner: View) = LinearLayout(this).apply {
        setPadding(dp(16), 0, dp(16), 0)
        addView(LinearLayout(this@TextReplacementActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8); layoutParams = lp
            addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD)
        setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM
        setPadding(dp(32), dp(16), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos
        setPadding(dp(32), dp(2), dp(32), dp(8))
    }
}

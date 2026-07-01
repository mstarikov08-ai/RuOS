package com.ruos.keyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * «Буфер обмена» — the clipboard-history panel. Lists what the RuOS keyboard has captured; tapping
 * an entry copies it back to the system clipboard so it can be pasted, and each entry can be pinned
 * (kept past the ring-buffer trim) or removed. A single «Очистить» wipes the unpinned history.
 * Backed by [ClipHistoryStore].
 */
class ClipboardActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private lateinit var store: ClipHistoryStore
    private lateinit var listCol: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ClipHistoryStore(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(60), 0, dp(40))
        }
        col.addView(title("Буфер обмена"))
        col.addView(note("История скопированного текста. Нажмите, чтобы скопировать снова; " +
            "закрепите нужное, чтобы оно не удалялось."))

        col.addView(sectionLabel("ИСТОРИЯ"))
        listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listCol)

        col.addView(card(TextView(this).apply {
            text = "Очистить историю"; setTextColor(Color.parseColor("#FF3B30")); textSize = 16f
            typeface = golosM; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14)); isClickable = true
            setOnClickListener { store.clearUnpinned(); rebuild(); toast("История очищена") }
        }))

        setContentView(ScrollView(this).apply { addView(col) })
        rebuild()
    }

    private fun rebuild() {
        listCol.removeAllViews()
        val items = store.all()
        if (items.isEmpty()) { listCol.addView(note("Пусто. Скопируйте текст — он появится здесь.")); return }
        // Pinned first, then most-recent.
        items.sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.time }).forEach { item ->
            listCol.addView(card(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@ClipboardActivity).apply {
                    text = item.text; setTextColor(Color.BLACK); textSize = 16f; typeface = golos
                    maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                    isClickable = true; setOnClickListener { copyBack(item.text) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@ClipboardActivity).apply {
                    text = if (item.pinned) "Открепить" else "Закрепить"
                    setTextColor(Color.parseColor("#0A84FF")); textSize = 13f; typeface = golos
                    setPadding(dp(10), 0, dp(10), 0); isClickable = true
                    setOnClickListener { store.setPinned(item.text, !item.pinned); rebuild() }
                })
                addView(TextView(this@ClipboardActivity).apply {
                    text = "Удалить"; setTextColor(Color.parseColor("#FF3B30")); textSize = 13f; typeface = golos
                    isClickable = true; setOnClickListener { store.delete(item.text); rebuild() }
                })
            }))
        }
    }

    private fun copyBack(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("RuOS", text))
        toast("Скопировано")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun card(inner: View) = LinearLayout(this).apply {
        setPadding(dp(16), 0, dp(16), 0)
        addView(LinearLayout(this@ClipboardActivity).apply {
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

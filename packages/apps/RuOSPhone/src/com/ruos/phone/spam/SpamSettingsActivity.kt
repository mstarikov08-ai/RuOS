package com.ruos.phone.spam

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
import android.widget.Switch
import android.widget.TextView

/**
 * «Блокировка и фильтрация» — manage spam rules: a blocked-numbers list, blocked prefixes and SMS
 * keywords, the «блокировать неизвестные короткие номера» toggle, and a log of what was recently
 * blocked. Backed by [SpamStore]; the call-screening service and RuOSMessages read the same rules.
 */
class SpamSettingsActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private lateinit var store: SpamStore
    private lateinit var numbersCol: LinearLayout
    private lateinit var keywordsCol: LinearLayout
    private lateinit var logCol: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SpamStore(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(60), 0, dp(40))
        }
        col.addView(title("Блокировка и фильтрация"))
        col.addView(note("Заблокированные номера не смогут звонить и присылать SMS. " +
            "Фильтр по словам и коротким номерам помогает отсеять спам."))

        col.addView(sectionLabel("НЕИЗВЕСТНЫЕ НОМЕРА"))
        col.addView(card(switchRow("Блокировать короткие номера", store.rules().blockUnknownShort) { on ->
            store.saveRules(store.rules().copy(blockUnknownShort = on))
        }))

        col.addView(sectionLabel("ЗАБЛОКИРОВАННЫЕ НОМЕРА"))
        numbersCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(numbersCol)
        col.addView(addNumberForm())

        col.addView(sectionLabel("СЛОВА-ФИЛЬТРЫ (SMS)"))
        keywordsCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(keywordsCol)
        col.addView(addKeywordForm())

        col.addView(sectionLabel("НЕДАВНО ЗАБЛОКИРОВАНО"))
        logCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(logCol)

        setContentView(ScrollView(this).apply { addView(col) })
        rebuild()
    }

    private fun rebuild() {
        val r = store.rules()
        numbersCol.removeAllViews()
        if (r.blockedNumbers.isEmpty()) numbersCol.addView(note("Нет заблокированных номеров."))
        else r.blockedNumbers.sorted().forEach { number ->
            numbersCol.addView(card(deletableRow(number) { store.unblockNumber(number); rebuild() }))
        }

        keywordsCol.removeAllViews()
        if (r.blockedKeywords.isEmpty()) keywordsCol.addView(note("Нет слов-фильтров."))
        else r.blockedKeywords.forEach { kw ->
            keywordsCol.addView(card(deletableRow(kw) {
                store.saveRules(store.rules().let { it.copy(blockedKeywords = it.blockedKeywords - kw) }); rebuild()
            }))
        }

        logCol.removeAllViews()
        val log = store.blockedLog()
        if (log.isEmpty()) logCol.addView(note("Пока ничего не заблокировано."))
        else {
            log.take(20).forEach { (num, kind, _) ->
                val what = if (kind == "call") "Звонок" else "SMS"
                logCol.addView(card(logRow("$what · $num")))
            }
            logCol.addView(card(TextView(this).apply {
                text = "Очистить журнал"; setTextColor(Color.parseColor("#FF3B30")); textSize = 16f; typeface = golosM
                gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12)); isClickable = true
                setOnClickListener { store.clearLog(); rebuild() }
            }))
        }
    }

    private fun addNumberForm(): View = addForm("Номер телефона", InputType.TYPE_CLASS_PHONE) { n ->
        store.blockNumber(n)
    }

    private fun addKeywordForm(): View = addForm("Слово или фраза", InputType.TYPE_CLASS_TEXT) { kw ->
        store.saveRules(store.rules().let { it.copy(blockedKeywords = it.blockedKeywords + kw) })
    }

    /** A text field + «Добавить» button pair; [onAdd] gets the trimmed non-empty value. */
    private fun addForm(hint: String, inputType: Int, onAdd: (String) -> Unit): View {
        val field = field(hint, inputType)
        val add = TextView(this).apply {
            text = "Добавить"; setTextColor(Color.parseColor("#0A84FF")); textSize = 16f; typeface = golosM
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12)); isClickable = true
            setOnClickListener {
                val v = field.text.toString().trim()
                if (v.isNotEmpty()) { onAdd(v); field.text = null; rebuild() }
            }
        }
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(card(field)); addView(card(add)) }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private fun field(hint: String, type: Int) = EditText(this).apply {
        setHint(hint); setHintTextColor(Color.parseColor("#8E8E93")); setTextColor(Color.BLACK)
        textSize = 16f; typeface = golos; inputType = type; background = null
    }

    private fun deletableRow(text: String, onDelete: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@SpamSettingsActivity).apply { this.text = text; setTextColor(Color.BLACK); textSize = 16f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@SpamSettingsActivity).apply {
            this.text = "Удалить"; setTextColor(Color.parseColor("#FF3B30")); textSize = 14f; typeface = golos
            isClickable = true; setOnClickListener { onDelete() }
        })
    }

    private fun logRow(text: String) = TextView(this).apply {
        this.text = text; setTextColor(Color.BLACK); textSize = 15f; typeface = golos
    }

    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@SpamSettingsActivity).apply { text = label; setTextColor(Color.BLACK); textSize = 16f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(this@SpamSettingsActivity).apply { isChecked = initial; setOnCheckedChangeListener { _, v -> onChange(v) } })
    }

    private fun card(inner: View) = LinearLayout(this).apply {
        setPadding(dp(16), 0, dp(16), 0)
        addView(LinearLayout(this@SpamSettingsActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8); layoutParams = lp
            addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 26f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM; setPadding(dp(32), dp(16), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(2), dp(32), dp(8))
    }
}

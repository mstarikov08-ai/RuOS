package com.ruos.keychain.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.ruos.keychain.store.Credential
import com.ruos.keychain.store.KeychainStore

/** Add / view / edit a saved login. Reveal + copy password; delete existing. */
class CredentialEditActivity : Activity() {

    private lateinit var store: KeychainStore
    private var existing: Credential? = null
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private lateinit var titleF: EditText
    private lateinit var domainF: EditText
    private lateinit var userF: EditText
    private lateinit var passF: EditText
    private lateinit var noteF: EditText
    private var revealed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = KeychainStore(this)
        existing = intent.getStringExtra("id")?.let { id -> store.credentials().firstOrNull { it.id == id } }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK)
            setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = if (existing == null) "Новый пароль" else "Пароль"
            setTextColor(Color.WHITE); textSize = 28f; typeface = Fonts.bold
            setPadding(0, 0, 0, dp(16f))
        })

        titleF = field("Название", existing?.title ?: "")
        domainF = field("Сайт или приложение", existing?.domain ?: "")
        userF = field("Имя пользователя", existing?.username ?: "")
        passF = field("Пароль", existing?.password ?: "", password = true)
        noteF = field("Заметка", existing?.note ?: "")

        col.addView(labeled("Название", titleF))
        col.addView(labeled("Сайт / приложение", domainF))
        col.addView(labeled("Имя пользователя", userF, copyTarget = { userF.text.toString() }))
        col.addView(labeled("Пароль", passF, copyTarget = { passF.text.toString() }, reveal = true))
        col.addView(labeled("Заметка", noteF))

        col.addView(Button(this).apply {
            text = "Сохранить"; setTextColor(Color.WHITE); typeface = Fonts.medium
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF0A84FF.toInt()) }
            setOnClickListener { save() }
        }, lp(dp(52f)).also { it.topMargin = dp(20f) })

        if (existing != null) col.addView(Button(this).apply {
            text = "Удалить"; setTextColor(0xFFFF453A.toInt()); typeface = Fonts.medium
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setOnClickListener { store.deleteCredential(existing!!.id); finish() }
        }, lp(dp(52f)).also { it.topMargin = dp(10f) })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun field(hint: String, value: String, password: Boolean = false) = EditText(this).apply {
        setText(value); setHint(hint); setHintTextColor(0xFF8E8E93.toInt())
        setTextColor(Color.WHITE); textSize = 17f; typeface = Fonts.regular
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT
    }

    private fun labeled(label: String, f: EditText, copyTarget: (() -> String)? = null, reveal: Boolean = false): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        }
        card.addView(TextView(this).apply { text = label; setTextColor(0xFF8E8E93.toInt()); textSize = 12f; typeface = Fonts.medium })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(f, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (reveal) row.addView(action("Показать") {
            revealed = !revealed
            passF.inputType = if (revealed) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passF.setSelection(passF.text.length)
            (it as TextView).text = if (revealed) "Скрыть" else "Показать"
        })
        if (copyTarget != null) row.addView(action("Копировать") { copy(copyTarget()) })
        card.addView(row)
        return FrameWrap(card)
    }

    private fun action(label: String, onTap: (View) -> Unit) = TextView(this).apply {
        text = label; setTextColor(0xFF0A84FF.toInt()); textSize = 14f; typeface = Fonts.medium
        setPadding(dp(10f), 0, dp(2f), 0); isClickable = true; setOnClickListener { onTap(this) }
    }

    private fun FrameWrap(v: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(6f), 0, dp(6f)); addView(v)
    }

    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("ruos", text))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun save() {
        val c = Credential(
            id = existing?.id ?: "cred_${System.currentTimeMillis()}",
            title = titleF.text.toString().ifBlank { domainF.text.toString() }.ifBlank { "Без названия" },
            domain = domainF.text.toString().trim(),
            username = userF.text.toString().trim(),
            password = passF.text.toString(),
            note = noteF.text.toString())
        store.upsertCredential(c)
        finish()
    }

    private fun lp(h: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
}

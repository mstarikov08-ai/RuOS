package com.ruos.auth.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.auth.model.AuthStore

/**
 * iOS-style passcode setup: enter, then confirm. Supports 6-digit (default), 4-digit,
 * custom-length and alphanumeric. Mismatch shakes and restarts.
 */
class PasscodeSetupActivity : Activity() {

    private lateinit var store: AuthStore
    private lateinit var pad: PasscodeView
    private var first: String? = null
    private var length = 6
    private var alphanumeric = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AuthStore(this)
        if (alphanumeric) { buildAlphanumeric(); return }
        buildDigit()
    }

    private fun buildDigit() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK); setPadding(0, dp(72), 0, dp(24))
        }
        pad = PasscodeView(this).apply {
            setLength(length); setTitle("Введите код-пароль")
            onComplete = { code -> onEntered(code) }
        }
        // "Параметры кода" affordance.
        root.addView(TextView(this).apply {
            text = "Параметры код-пароля"; setTextColor(Color.parseColor("#4F9DFF")); textSize = 15f
            gravity = Gravity.CENTER
            isClickable = true; setOnClickListener { showOptions() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = dp(8)
        })
        root.addView(pad)
        setContentView(root)
    }

    private fun onEntered(code: String) {
        if (first == null) {
            first = code
            pad.setTitle("Повторите код-пароль"); pad.clear()
        } else if (first == code) {
            store.setPasscode(code, alphanumeric = false)
            store.setToggle(AuthStore.USE_UNLOCK, true)
            store.markUnlocked()
            setResult(RESULT_OK); finish()
        } else {
            first = null
            pad.setTitle("Коды не совпадают. Введите код-пароль")
            pad.shake()
        }
    }

    private fun showOptions() {
        val opts = arrayOf("6 цифр", "4 цифры", "Произвольный код (цифры)", "Буквенно-цифровой код")
        AlertDialog.Builder(this).setTitle("Параметры код-пароля")
            .setItems(opts) { _, which ->
                first = null
                when (which) {
                    0 -> { length = 6; alphanumeric = false; pad.setLength(6); pad.setTitle("Введите код-пароль") }
                    1 -> { length = 4; alphanumeric = false; pad.setLength(4); pad.setTitle("Введите код-пароль") }
                    2 -> askCustomLength()
                    3 -> { alphanumeric = true; recreate() }
                }
            }.show()
    }

    private fun askCustomLength() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "4–12" }
        AlertDialog.Builder(this).setTitle("Длина кода").setView(input)
            .setPositiveButton("ОК") { _, _ ->
                val n = input.text.toString().toIntOrNull()?.coerceIn(4, 12) ?: 6
                length = n; first = null; pad.setLength(n); pad.setTitle("Введите код-пароль")
            }.setNegativeButton("Отмена", null).show()
    }

    private fun buildAlphanumeric() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val label = TextView(this).apply {
            text = "Введите код-пароль"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
        }
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; textSize = 20f
        }
        val confirm = TextView(this).apply {
            text = "Далее"; setTextColor(Color.parseColor("#4F9DFF")); textSize = 17f; gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0); isClickable = true
            setOnClickListener {
                val code = field.text.toString()
                if (code.length < 4) { label.text = "Минимум 4 символа"; return@setOnClickListener }
                if (first == null) { first = code; field.setText(""); label.text = "Повторите код-пароль" }
                else if (first == code) {
                    store.setPasscode(code, alphanumeric = true)
                    store.setToggle(AuthStore.USE_UNLOCK, true); store.markUnlocked()
                    setResult(RESULT_OK); finish()
                } else { first = null; field.setText(""); label.text = "Коды не совпадают" }
            }
        }
        col.addView(label); col.addView(field, LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(confirm)
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

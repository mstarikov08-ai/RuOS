package com.ruos.keychain.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ruos.keychain.store.KeychainStore
import com.ruos.keychain.store.TotpAccount
import com.ruos.keychain.totp.Totp

/**
 * Add a 2FA account: paste an otpauth:// URI (auto-fills issuer/account/secret) or enter
 * them manually. The Base32 secret is validated by generating a test code before saving.
 */
class TotpAddActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = KeychainStore(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK)
            setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = "Новый код 2FA"; setTextColor(Color.WHITE); textSize = 28f; typeface = Fonts.bold
            setPadding(0, 0, 0, dp(8f))
        })
        col.addView(TextView(this).apply {
            text = "Вставьте ссылку otpauth:// из приложения, либо введите данные вручную."
            setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular
            setPadding(0, 0, 0, dp(12f))
        })

        val uriF = field("otpauth://… (необязательно)")
        val issuerF = field("Сервис (например, Госуслуги)")
        val accountF = field("Аккаунт (логин или e-mail)")
        val secretF = field("Секретный ключ (Base32)")

        col.addView(card(uriF))
        col.addView(Button(this).apply {
            text = "Разобрать ссылку"; setTextColor(0xFF0A84FF.toInt()); typeface = Fonts.medium
            background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setOnClickListener {
                val parsed = Totp.parseUri(uriF.text.toString().trim())
                if (parsed == null) Toast.makeText(this@TotpAddActivity, "Не удалось разобрать ссылку", Toast.LENGTH_SHORT).show()
                else { issuerF.setText(parsed.first); accountF.setText(parsed.second); secretF.setText(parsed.third) }
            }
        }, lp(dp(46f)).also { it.bottomMargin = dp(12f) })

        col.addView(card(issuerF)); col.addView(card(accountF)); col.addView(card(secretF))

        col.addView(Button(this).apply {
            text = "Сохранить"; setTextColor(Color.WHITE); typeface = Fonts.medium
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF0A84FF.toInt()) }
            setOnClickListener {
                val secret = secretF.text.toString().trim()
                if (secret.isEmpty() || Totp.code(secret).all { it == '-' }) {
                    Toast.makeText(this@TotpAddActivity, "Неверный секретный ключ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                store.upsertTotp(TotpAccount(
                    id = "totp_${System.currentTimeMillis()}",
                    issuer = issuerF.text.toString().trim().ifEmpty { "Код" },
                    account = accountF.text.toString().trim(),
                    secret = secret))
                finish()
            }
        }, lp(dp(52f)).also { it.topMargin = dp(16f) })

        setContentView(android.widget.ScrollView(this).apply { addView(col) })
    }

    private fun field(hint: String) = EditText(this).apply {
        setHint(hint); setHintTextColor(0xFF8E8E93.toInt()); setTextColor(Color.WHITE)
        textSize = 16f; typeface = Fonts.regular; inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun card(f: View): View = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
        setPadding(dp(14f), dp(4f), dp(14f), dp(4f))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8f); layoutParams = lp
        addView(f, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun lp(h: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
}

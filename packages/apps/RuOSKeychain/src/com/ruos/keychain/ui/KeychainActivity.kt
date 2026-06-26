package com.ruos.keychain.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.keychain.store.KeychainStore

/**
 * Связка ключей (Keychain) home. Biometric-gated. Two tabs: «Пароли» (saved logins) and
 * «Коды» (TOTP authenticator). Programmatic dark UI, Golos, spring-pressed list.
 */
class KeychainActivity : Activity() {

    private lateinit var store: KeychainStore
    private lateinit var listHost: LinearLayout
    private var tab = 0
    private lateinit var tabPasswords: TextView
    private lateinit var tabCodes: TextView
    private val accent = 0xFF0A84FF.toInt()
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = KeychainStore(this)
        gate { buildUi() }
    }

    override fun onResume() {
        super.onResume()
        if (::listHost.isInitialized) rebuild()
    }

    private fun gate(onUnlock: () -> Unit) {
        val bm = getSystemService(BiometricManager::class.java)
        val ok = bm?.canAuthenticate(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
        if (!ok) { onUnlock(); return }
        setContentView(LinearLayout(this).apply { setBackgroundColor(Color.BLACK) })
        BiometricPrompt.Builder(this)
            .setTitle("Связка ключей")
            .setSubtitle("Доступ к паролям и кодам")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) { runOnUiThread { onUnlock() } }
                override fun onAuthenticationError(code: Int, msg: CharSequence) { finish() }
            })
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = "Связка ключей"; setTextColor(Color.WHITE); textSize = 30f; typeface = Fonts.bold
            setPadding(dp(16f), dp(48f), dp(16f), dp(8f))
        })

        // segmented tabs
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(12f), 0, dp(12f), dp(8f))
        }
        tabPasswords = tabPill("Пароли") { switchTab(0) }
        tabCodes = tabPill("Коды") { switchTab(1) }
        tabs.addView(tabPasswords, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(6f) })
        tabs.addView(tabCodes, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabs)

        listHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(listHost) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // add button
        root.addView(TextView(this).apply {
            text = "+  Добавить"; setTextColor(Color.WHITE); typeface = Fonts.medium; textSize = 17f
            gravity = Gravity.CENTER; setPadding(0, dp(14f), 0, dp(18f))
            background = GradientDrawable().apply { setColor(accent) }
            isClickable = true
            setOnClickListener {
                if (tab == 0) startActivity(Intent(this@KeychainActivity, CredentialEditActivity::class.java))
                else startActivity(Intent(this@KeychainActivity, TotpAddActivity::class.java))
            }
        })
        setContentView(root)
        switchTab(0)
    }

    private fun tabPill(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 15f; typeface = Fonts.medium
        setPadding(0, dp(9f), 0, dp(9f)); isClickable = true; setOnClickListener { onTap() }
    }

    private fun switchTab(which: Int) {
        tab = which
        listOf(tabPasswords, tabCodes).forEachIndexed { i, t ->
            t.background = GradientDrawable().apply {
                cornerRadius = dp(9f).toFloat(); setColor(if (i == which) accent else 0xFF1C1C1E.toInt())
            }
            t.setTextColor(if (i == which) Color.WHITE else 0xFF8E8E93.toInt())
        }
        rebuild()
    }

    private fun rebuild() {
        listHost.removeAllViews()
        if (tab == 0) {
            val creds = store.credentials()
            if (creds.isEmpty()) listHost.addView(empty("Нет сохранённых паролей"))
            creds.forEach { c ->
                listHost.addView(row(c.title, "${c.username} · ••••••") {
                    startActivity(Intent(this, CredentialEditActivity::class.java).putExtra("id", c.id))
                })
            }
        } else {
            val accounts = store.totpAccounts()
            if (accounts.isEmpty()) listHost.addView(empty("Нет кодов двухфакторной аутентификации"))
            accounts.forEach { a ->
                val rowv = TotpRowView(this, a)
                rowv.setOnLongClickListener { store.deleteTotp(a.id); rebuild(); true }
                listHost.addView(rowv)
                listHost.addView(divider())
            }
        }
    }

    private fun row(title: String, sub: String, onTap: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        isClickable = true; setOnClickListener { onTap() }
        addView(TextView(this@KeychainActivity).apply { text = title; setTextColor(Color.WHITE); textSize = 17f; typeface = Fonts.medium })
        addView(TextView(this@KeychainActivity).apply { text = sub; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular })
    }.also { it.addView(divider()) }

    private fun empty(msg: String) = TextView(this).apply {
        text = msg; setTextColor(0xFF8E8E93.toInt()); textSize = 15f; typeface = Fonts.regular
        gravity = Gravity.CENTER; setPadding(0, dp(40f), 0, 0)
    }

    private fun divider() = View(this).apply { setBackgroundColor(0xFF2C2C2E.toInt()) }
        .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1) }
}

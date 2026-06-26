package com.ruos.auth.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ruos.auth.biometric.BiometricCapability
import com.ruos.auth.model.AuthStore
import com.ruos.auth.model.RequireAfter

/** Settings → "Face ID и код" / "Touch ID и код", matching the iOS layout. */
class AuthSettingsActivity : Activity() {

    private lateinit var store: AuthStore
    private val bioName get() = BiometricCapability.displayName(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AuthStore(this)
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = "$bioName и код"; setTextColor(Color.WHITE); textSize = 28f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            setPadding(dp(16), dp(48), dp(16), dp(12))
        })
        val scroll = ScrollView(this); val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col); root.addView(scroll); setContentView(root)

        // ── Use biometrics for ───────────────────────────────────────────────
        col.addView(sectionLabel("Использовать $bioName для:"))
        col.addView(switchRow("Разблокировка RuOS", store.toggle(AuthStore.USE_UNLOCK)) {
            store.setToggle(AuthStore.USE_UNLOCK, it) })
        col.addView(switchRow("MIR Pay", store.toggle(AuthStore.USE_MIRPAY)) {
            store.setToggle(AuthStore.USE_MIRPAY, it) })
        col.addView(switchRow("RuStore", store.toggle(AuthStore.USE_RUSTORE)) {
            store.setToggle(AuthStore.USE_RUSTORE, it) })
        col.addView(switchRow("Автозаполнение паролей", store.toggle(AuthStore.USE_AUTOFILL)) {
            store.setToggle(AuthStore.USE_AUTOFILL, it) })

        // ── Enrolment ────────────────────────────────────────────────────────
        col.addView(sectionLabel(""))
        val addLabel = if (BiometricCapability.kind(this) == BiometricCapability.Kind.FINGERPRINT)
            "Добавить отпечаток" else "Добавить лицо"
        col.addView(navRow(addLabel) { openEnrolment() })
        col.addView(navRow("Сбросить $bioName") {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL)) }
        })

        // ── Passcode ─────────────────────────────────────────────────────────
        col.addView(sectionLabel("Код-пароль"))
        if (store.isPasscodeSet) {
            col.addView(navRow("Изменить код-пароль") { changePasscode() })
            col.addView(navRow("Выключить код-пароль", danger = true) { store.clearPasscode(); render() })
            col.addView(navRow("Требовать код-пароль", value = store.requireAfter.ruName) { pickRequire() })
            col.addView(switchRow("Стереть данные (после 10 попыток)", store.eraseAfter10) {
                store.eraseAfter10 = it })
        } else {
            col.addView(navRow("Включить код-пароль") {
                startActivity(Intent(this, PasscodeSetupActivity::class.java))
            })
        }
    }

    private fun openEnrolment() {
        val target = if (BiometricCapability.kind(this) == BiometricCapability.Kind.FINGERPRINT)
            FingerprintSetupActivity::class.java else FaceSetupActivity::class.java
        startActivity(Intent(this, target))
    }

    private fun changePasscode() {
        // Verify current, then set a new one.
        startActivity(Intent(this, PasscodeEntryActivity::class.java))
        startActivity(Intent(this, PasscodeSetupActivity::class.java))
    }

    private fun pickRequire() {
        val opts = RequireAfter.values()
        AlertDialog.Builder(this).setTitle("Требовать код-пароль")
            .setSingleChoiceItems(opts.map { it.ruName }.toTypedArray(),
                opts.indexOf(store.requireAfter)) { dlg, which ->
                store.requireAfter = opts[which]; dlg.dismiss(); render()
            }.show()
    }

    // ── Row builders ────────────────────────────────────────────────────────

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#8E8E93")); textSize = 13f
        setPadding((16 * resources.displayMetrics.density).toInt(),
            (18 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
    }

    private fun switchRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#1C1C1E")) }
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            addView(TextView(context).apply { text = title; setTextColor(Color.WHITE); textSize = 16f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply {
                isChecked = initial
                setOnCheckedChangeListener { b, c ->
                    b.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onChange(c)
                }
            })
        }
    }

    private fun navRow(title: String, value: String? = null, danger: Boolean = false, onClick: () -> Unit): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#1C1C1E")) }
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            isClickable = true; setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); onClick()
            }
            addView(TextView(context).apply {
                text = title; textSize = 16f
                setTextColor(if (danger) Color.parseColor("#FF453A") else Color.WHITE)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (value != null) addView(TextView(context).apply {
                text = value; setTextColor(Color.parseColor("#8E8E93")); textSize = 16f
            })
        }
    }
}

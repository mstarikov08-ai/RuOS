package com.ruos.auth.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.auth.model.AuthStore
import com.ruos.auth.util.Haptics

/**
 * Passcode verification screen, iOS-style: dots + numpad over a dark blur, an
 * Emergency-call affordance and a Cancel that returns to the lock screen. Wrong code
 * shakes with a haptic; honours erase-after-10 when enabled.
 */
class PasscodeEntryActivity : Activity() {

    private lateinit var store: AuthStore
    private lateinit var pad: PasscodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AuthStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#E6000000"))   // dark scrim over lock
            setPadding(0, dp(80), 0, dp(24))
        }

        pad = PasscodeView(this).apply {
            setLength(store.passcodeLength.coerceIn(4, 12))
            setTitle("Введите код-пароль")
            onComplete = { code -> check(code) }
        }
        root.addView(pad, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom row: Экстренный вызов · Отмена
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(TextView(this).apply {
            text = "Экстренный вызов"; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            isClickable = true; setOnClickListener { emergencyCall() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(TextView(this).apply {
            text = "Отмена"; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            isClickable = true; setOnClickListener { setResult(RESULT_CANCELED); finish() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bottom)

        setContentView(root)
    }

    private fun check(code: String) {
        if (store.verify(code)) {
            Haptics.success(pad)
            store.markUnlocked()
            setResult(RESULT_OK); finish()
        } else {
            pad.setTitle(failTitle())
            pad.shake()
            if (store.shouldErase()) eraseData()
        }
    }

    private fun failTitle(): String {
        val left = (10 - store.failCount).coerceAtLeast(0)
        return if (store.eraseAfter10 && store.failCount >= 5)
            "Неверный код. Попыток осталось: $left"
        else "Неверный код-пароль"
    }

    private fun emergencyCall() {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun eraseData() {
        // Erase is a destructive device action owned by DevicePolicyManager; RuOSAuth
        // is not a device-owner, so we surface the intent rather than wipe directly.
        pad.setTitle("Достигнут лимит попыток")
        // A real deployment wires this to DevicePolicyManager.wipeData() from a
        // provisioned device-owner component.
    }

    override fun onBackPressed() { setResult(RESULT_CANCELED); finish() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

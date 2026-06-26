package com.ruos.auth.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ruos.auth.biometric.BiometricCapability
import com.ruos.auth.biometric.BiometricGate
import com.ruos.auth.model.AuthStore
import com.ruos.auth.util.Haptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RuOS lock surface: large thin time/date, camera (bottom-left) and MIR Pay
 * (bottom-right) shortcuts. On wake it attempts biometrics automatically; success
 * springs the screen up to reveal what's behind, failure falls back to the passcode.
 *
 * NOTE: this is RuOS's app-level lock UX. The hardware-secured device keyguard is
 * owned by system_server/SystemUI — this demonstrates the iOS unlock feel and gates
 * RuOS surfaces, it is not the gatekeeper-backed keyguard.
 */
class LockActivity : Activity() {

    private lateinit var store: AuthStore
    private lateinit var gate: BiometricGate
    private lateinit var content: LinearLayout
    private val passcodeReq = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AuthStore(this)
        gate = BiometricGate(this)
        setShowWhenLocked(true); setTurnScreenOn(true)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        attemptUnlock()
    }

    private fun attemptUnlock() {
        if (!store.isPasscodeSet) { unlocked(); return }
        if (store.toggle(AuthStore.USE_UNLOCK) && BiometricCapability.isReady(this)) {
            gate.authenticate(
                title = "Разблокировка RuOS",
                onSuccess = { unlocked() },
                onFail = { Haptics.reject(content) },     // subtle pulse, stay on lock
                onError = { showPasscode() }
            )
        } else {
            showPasscode()
        }
    }

    private fun showPasscode() {
        startActivityForResult(Intent(this, PasscodeEntryActivity::class.java), passcodeReq)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == passcodeReq && res == RESULT_OK) unlocked()
    }

    /** Spring the lock screen up to reveal the home screen behind, then finish. */
    private fun unlocked() {
        store.markUnlocked()
        Haptics.success(content)
        SpringAnimation(content, SpringAnimation.TRANSLATION_Y, -content.height.toFloat()).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            addEndListener { _, _, _, _ -> finish(); overridePendingTransition(0, 0) }
            start()
        }
    }

    private fun buildUi(): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#05070F"))
        }

        val clockCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        clockCol.addView(TextView(this).apply {
            text = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
            setTextColor(Color.WHITE); textSize = 80f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); gravity = Gravity.CENTER
        })
        clockCol.addView(TextView(this).apply {
            text = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())
            setTextColor(Color.parseColor("#C8D0E0")); textSize = 16f; gravity = Gravity.CENTER
        })
        content.addView(clockCol, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).also {
            it.topMargin = dp(120)
        })

        // Bottom shortcuts: camera (left) · MIR Pay (right)
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(28), 0, dp(28), dp(40))
        }
        bottom.addView(shortcut("Камера") { launch(Intent("android.media.action.STILL_IMAGE_CAMERA")) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.gravity = Gravity.START })
        bottom.addView(shortcut("MIR Pay") { launchPkg("ru.nspk.mirpay") },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.gravity = Gravity.END })
        content.addView(bottom)

        root.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    private fun shortcut(label: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f * resources.displayMetrics.density
                setColor(Color.argb(70, 255, 255, 255))
            }
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (14 * resources.displayMetrics.density).toInt())
            isClickable = true
            setOnClickListener { Haptics.tap(it); onClick() }
        }
    }

    private fun launch(intent: Intent) { runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    private fun launchPkg(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { launch(it) }
    }

    override fun onBackPressed() { /* a lock screen has no back */ }
}

package com.ruos.auth.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.auth.model.AuthStore
import com.ruos.auth.util.Haptics

/**
 * Branded Face ID setup: the iOS green-dots scan with rotate prompts and a checkmark,
 * then hands off to the REAL system biometric enrolment so an actual face is stored.
 * The animation here is onboarding chrome, not the capture itself.
 */
class FaceSetupActivity : Activity() {

    private lateinit var ring: FaceScanRingView
    private lateinit var instruction: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }

        col.addView(TextView(this).apply {
            text = "Настройка Face ID"; setTextColor(Color.WHITE); textSize = 24f; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        instruction = TextView(this).apply {
            text = "Расположите лицо в рамке"; setTextColor(Color.parseColor("#AEAEB2"))
            textSize = 15f; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(28))
        }
        col.addView(instruction)
        ring = FaceScanRingView(this)
        col.addView(ring, LinearLayout.LayoutParams(dp(240), dp(240)))

        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        runPass(0) { instruction.text = "Медленно поверните голову"; runPass(1) { complete() } }
    }

    private fun runPass(pass: Int, onEnd: () -> Unit) {
        ring.pass = pass
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600
            addUpdateListener {
                ring.progress = it.animatedValue as Float
                if (ring.progress > 0.5f && pass == 0) instruction.text = "Поверните голову в другую сторону"
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { Haptics.tick(ring); onEnd() }
            })
            start()
        }
    }

    private fun complete() {
        instruction.text = "Face ID готов"
        Haptics.success(ring)
        AuthStore(this).setToggle(AuthStore.USE_UNLOCK, true)
        // Hand off to the real system enrolment so an actual face is stored.
        ring.postDelayed({
            runCatching {
                startActivity(Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                    .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK))
            }
            finish()
        }, 900)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

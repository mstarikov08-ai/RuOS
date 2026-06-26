package com.ruos.auth.ui

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
 * Branded Touch ID setup ("how Apple would do an in-display sensor"): a fingerprint
 * that fills as the user lifts and re-touches, captured across several placements,
 * then hands off to the REAL system enrolment. Supports naming Палец 1..5.
 */
class FingerprintSetupActivity : Activity() {

    private lateinit var print: FingerprintView
    private lateinit var instruction: TextView
    private var placements = 0
    private val needed = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val index = (intent.getIntExtra("index", 1)).coerceIn(1, 5)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            setOnClickListener { onPlacement() }   // tap simulates a sensor touch
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(TextView(this).apply {
            text = "Палец $index"; setTextColor(Color.WHITE); textSize = 24f; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        instruction = TextView(this).apply {
            text = "Прикоснитесь к датчику"; setTextColor(Color.parseColor("#AEAEB2"))
            textSize = 15f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(28))
        }
        col.addView(instruction)
        print = FingerprintView(this)
        col.addView(print, LinearLayout.LayoutParams(dp(160), dp(160)))
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun onPlacement() {
        if (placements >= needed) return
        placements++
        Haptics.tick(print)
        print.progress = placements / needed.toFloat()
        instruction.text = if (placements % 2 == 0) "Прикоснитесь снова" else "Поднимите и переставьте палец"
        if (placements >= needed) complete()
    }

    private fun complete() {
        instruction.text = "Готово"
        Haptics.success(print)
        AuthStore(this).setToggle(AuthStore.USE_UNLOCK, true)
        print.postDelayed({
            runCatching {
                startActivity(Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                    .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK))
            }
            finish()
        }, 800)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

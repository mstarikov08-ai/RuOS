package com.ruos.auth.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.auth.util.Haptics

/**
 * The iOS passcode pad: a row of dots that fill as digits are entered, and a 3×4
 * numpad (digit + small letters) with a delete key. Fires [onComplete] when the
 * required number of digits is reached, and [shake]s with a haptic on a wrong code.
 */
class PasscodeView(context: Context) : LinearLayout(context) {

    var onComplete: ((String) -> Unit)? = null
    var bottomLeft: TextView? = null   // host can repurpose (e.g. emergency / cancel)

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private var length = 6
    private val entry = StringBuilder()
    private val dotsRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
    private val dots = mutableListOf<View>()
    private val title = TextView(context).apply {
        setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(8).toInt(); it.bottomMargin = dp(22).toInt()
        })
        addView(dotsRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = dp(40).toInt()
        })
        addView(buildPad())
        rebuildDots()
    }

    fun setTitle(t: String) { title.text = t }
    fun setLength(n: Int) { length = n; entry.clear(); rebuildDots() }
    fun clear() { entry.clear(); refreshDots() }

    fun shake() {
        Haptics.reject(this)
        animate().translationX(-dp(16)).setDuration(50).withEndAction {
            animate().translationX(dp(16)).setDuration(50).withEndAction {
                animate().translationX(-dp(10)).setDuration(45).withEndAction {
                    animate().translationX(0f).setDuration(45).withEndAction { clear() }.start()
                }.start()
            }.start()
        }.start()
    }

    private fun rebuildDots() {
        dotsRow.removeAllViews(); dots.clear()
        for (i in 0 until length) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(1.4f).toInt(), Color.WHITE)
                    setColor(Color.TRANSPARENT)
                }
            }
            dotsRow.addView(dot, LayoutParams(dp(13).toInt(), dp(13).toInt()).also {
                it.marginStart = dp(9).toInt(); it.marginEnd = dp(9).toInt()
            })
            dots.add(dot)
        }
        refreshDots()
    }

    private fun refreshDots() {
        dots.forEachIndexed { i, dot ->
            (dot.background as GradientDrawable).setColor(
                if (i < entry.length) Color.WHITE else Color.TRANSPARENT)
        }
    }

    private fun press(digit: String) {
        if (entry.length >= length) return
        Haptics.tap(this)
        entry.append(digit); refreshDots()
        if (entry.length == length) onComplete?.invoke(entry.toString())
    }

    private fun del() {
        if (entry.isEmpty()) return
        Haptics.tick(this)
        entry.deleteCharAt(entry.length - 1); refreshDots()
    }

    private val letters = mapOf(
        "2" to "АБВГ", "3" to "ДЕЁЖЗ", "4" to "ИЙКЛ", "5" to "МНОП",
        "6" to "РСТУ", "7" to "ФХЦЧ", "8" to "ШЩЪЫ", "9" to "ЬЭЮЯ")

    private fun buildPad(): View {
        val grid = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
        val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","del"))
        for (r in rows) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            for (key in r) {
                row.addView(buildKey(key), LayoutParams(dp(74).toInt(), dp(74).toInt()).also {
                    it.setMargins(dp(9).toInt(), dp(8).toInt(), dp(9).toInt(), dp(8).toInt())
                })
            }
            grid.addView(row)
        }
        return grid
    }

    private fun buildKey(key: String): View {
        if (key == "") {
            // Bottom-left slot — host may attach an action (emergency / cancel).
            return TextView(context).apply {
                setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
                bottomLeft = this
            }
        }
        if (key == "del") {
            return TextView(context).apply {
                text = "Стереть"; setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener { del() }
            }
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#33FFFFFF"))
            }
            isClickable = true
            setOnClickListener { press(key) }
            addView(TextView(context).apply {
                text = key; setTextColor(Color.WHITE); textSize = 30f; gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            })
            letters[key]?.let { l ->
                addView(TextView(context).apply {
                    text = l; setTextColor(Color.parseColor("#CFCFCF")); textSize = 9f
                    gravity = Gravity.CENTER; letterSpacing = 0.12f
                })
            }
        }
    }
}

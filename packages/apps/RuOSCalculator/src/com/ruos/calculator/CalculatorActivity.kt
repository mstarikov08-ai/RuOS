package com.ruos.calculator

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Activity
import kotlin.math.*

class CalculatorActivity : Activity() {

    // Colors
    private val colorBg = Color.parseColor("#000000")
    private val colorSurface = Color.parseColor("#1C1C1E")
    private val colorSurface2 = Color.parseColor("#2C2C2E")
    private val colorRed = Color.parseColor("#D94F3D")
    private val colorDarkGray = Color.parseColor("#636366")
    private val colorText = Color.parseColor("#FFFFFF")
    private val colorTextSecondary = Color.parseColor("#8E8E93")

    // State
    private var currentInput: String = "0"
    private var previousInput: Double = 0.0
    private var pendingOperation: String? = null
    private var justEvaluated: Boolean = false
    private var expressionDisplay: String = ""
    private var isRadMode: Boolean = true
    private var memoryValue: Double = 0.0
    private var hasParenOpen: Boolean = false

    // Views
    private var displayTextView: TextView? = null
    private var expressionTextView: TextView? = null
    private var root: FrameLayout? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    private fun navBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(34)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        buildUI()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildUI()
    }

    private fun buildUI() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val frame = FrameLayout(this)
        frame.setBackgroundColor(colorBg)

        val outerScroll = ScrollView(this)
        outerScroll.isFillViewport = true

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setBackgroundColor(colorBg)

        // Top padding for status bar
        val topPad = LinearLayout(this)
        topPad.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, statusBarHeight()
        )
        mainLayout.addView(topPad)

        // Display area
        val displayArea = LinearLayout(this)
        displayArea.orientation = LinearLayout.VERTICAL
        displayArea.gravity = Gravity.BOTTOM or Gravity.END
        val displayHeight = if (isLandscape) dp(100) else dp(200)
        val displayParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, displayHeight
        )
        displayParams.setMargins(dp(16), 0, dp(16), dp(8))
        displayArea.layoutParams = displayParams
        displayArea.setPadding(dp(8), dp(8), dp(8), dp(8))

        // Expression display (secondary, smaller)
        val exprView = TextView(this)
        exprView.textSize = if (isLandscape) 18f else 24f
        exprView.setTextColor(colorTextSecondary)
        exprView.gravity = Gravity.END
        exprView.text = expressionDisplay
        exprView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        exprView.maxLines = 2
        displayArea.addView(exprView)
        expressionTextView = exprView

        // Main display
        val mainDisplay = TextView(this)
        mainDisplay.textSize = if (isLandscape) 48f else 72f
        mainDisplay.setTextColor(colorText)
        mainDisplay.gravity = Gravity.END
        mainDisplay.text = formatDisplay(currentInput)
        mainDisplay.setTypeface(null, Typeface.LIGHT)
        mainDisplay.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mainDisplay.setSingleLine(true)
        mainDisplay.textScaleX = 1.0f
        displayArea.addView(mainDisplay)
        displayTextView = mainDisplay

        mainLayout.addView(displayArea)

        // Buttons area
        val buttonArea = LinearLayout(this)
        buttonArea.orientation = LinearLayout.VERTICAL

        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        buttonArea.layoutParams = buttonParams

        if (isLandscape) {
            buildLandscapeButtons(buttonArea, screenWidth)
        } else {
            buildPortraitButtons(buttonArea, screenWidth)
        }

        // Bottom nav padding
        val bottomPad = LinearLayout(this)
        bottomPad.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, navBarHeight()
        )
        buttonArea.addView(bottomPad)

        mainLayout.addView(buttonArea)

        outerScroll.addView(mainLayout)
        frame.addView(outerScroll)

        root = frame
        setContentView(frame)
    }

    private fun buildPortraitButtons(container: LinearLayout, screenWidth: Int) {
        val margin = dp(12)
        val gap = dp(12)
        val cols = 4
        val btnSize = (screenWidth - margin * 2 - gap * (cols - 1)) / cols

        val rows = listOf(
            listOf("AC", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=") // 0 is double-wide
        )

        rows.forEachIndexed { rowIdx, row ->
            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowParams.setMargins(margin, if (rowIdx == 0) gap else 0, margin, gap)
            rowLayout.layoutParams = rowParams

            if (rowIdx == 4) {
                // Special last row: 0 is double-wide
                val zeroBtn = makeButton("0", getButtonColor("0"), btnSize, btnSize,
                    btnSize * 2 + gap, btnSize, true)
                rowLayout.addView(zeroBtn)

                val space = View(this)
                space.layoutParams = LinearLayout.LayoutParams(gap, btnSize)
                rowLayout.addView(space)

                val dotBtn = makeButton(".", getButtonColor("."), btnSize, btnSize, btnSize, btnSize, false)
                rowLayout.addView(dotBtn)

                val space2 = View(this)
                space2.layoutParams = LinearLayout.LayoutParams(gap, btnSize)
                rowLayout.addView(space2)

                val eqBtn = makeButton("=", getButtonColor("="), btnSize, btnSize, btnSize, btnSize, false)
                rowLayout.addView(eqBtn)
            } else {
                row.forEachIndexed { colIdx, label ->
                    if (colIdx > 0) {
                        val space = View(this)
                        space.layoutParams = LinearLayout.LayoutParams(gap, btnSize)
                        rowLayout.addView(space)
                    }
                    val btn = makeButton(label, getButtonColor(label), btnSize, btnSize, btnSize, btnSize, false)
                    rowLayout.addView(btn)
                }
            }

            container.addView(rowLayout)
        }
    }

    private fun buildLandscapeButtons(container: LinearLayout, screenWidth: Int) {
        val margin = dp(8)
        val gap = dp(8)
        val cols = 10
        val btnH = dp(52)
        val btnW = (screenWidth - margin * 2 - gap * (cols - 1)) / cols

        // Row 0: scientific top row + standard top row
        val sciRows = listOf(
            listOf("(", ")", "mc", "m+", "m−", "mr", "AC", "±", "%", "÷"),
            listOf("2ⁿᵈ", "x²", "x³", "xʸ", "eˣ", "10ˣ", "7", "8", "9", "×"),
            listOf("√", "∛", "ˣ√", "ln", "log₁₀", "x!", "4", "5", "6", "−"),
            listOf("sin", "cos", "tan", "e", "π", "rand", "1", "2", "3", "+"),
            listOf("sinh", "cosh", "tanh", if (isRadMode) "Rad" else "Deg", "EE", "0", "0", ".", "=", "")
        )

        sciRows.forEachIndexed { rowIdx, row ->
            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowParams.setMargins(margin, if (rowIdx == 0) gap / 2 else 0, margin, gap)
            rowLayout.layoutParams = rowParams

            if (rowIdx == 4) {
                // Special last row handling for 0 double-wide
                val specials = listOf("sinh", "cosh", "tanh", if (isRadMode) "Rad" else "Deg", "EE")
                specials.forEachIndexed { ci, lbl ->
                    if (ci > 0) {
                        val sp = View(this); sp.layoutParams = LinearLayout.LayoutParams(gap, btnH); rowLayout.addView(sp)
                    }
                    rowLayout.addView(makeButton(lbl, getButtonColor(lbl), btnW, btnH, btnW, btnH, false))
                }
                val sp = View(this); sp.layoutParams = LinearLayout.LayoutParams(gap, btnH); rowLayout.addView(sp)
                // 0 double wide
                rowLayout.addView(makeButton("0", getButtonColor("0"), btnW, btnH, btnW * 2 + gap, btnH, true))
                val sp2 = View(this); sp2.layoutParams = LinearLayout.LayoutParams(gap, btnH); rowLayout.addView(sp2)
                rowLayout.addView(makeButton(".", getButtonColor("."), btnW, btnH, btnW, btnH, false))
                val sp3 = View(this); sp3.layoutParams = LinearLayout.LayoutParams(gap, btnH); rowLayout.addView(sp3)
                rowLayout.addView(makeButton("=", getButtonColor("="), btnW, btnH, btnW, btnH, false))
            } else {
                row.forEachIndexed { colIdx, label ->
                    if (colIdx > 0) {
                        val space = View(this)
                        space.layoutParams = LinearLayout.LayoutParams(gap, btnH)
                        rowLayout.addView(space)
                    }
                    val btn = makeButton(label, getButtonColor(label), btnW, btnH, btnW, btnH, false)
                    rowLayout.addView(btn)
                }
            }

            container.addView(rowLayout)
        }
    }

    private fun makeButton(
        label: String,
        bgColor: Int,
        measuredW: Int,
        measuredH: Int,
        displayW: Int,
        displayH: Int,
        isWide: Boolean
    ): TextView {
        val btn = TextView(this)
        btn.text = label
        btn.gravity = Gravity.CENTER
        btn.setTextColor(colorText)
        btn.textSize = when {
            label.length > 3 -> 13f
            label.length > 2 -> 16f
            else -> 20f
        }
        btn.setTypeface(null, Typeface.NORMAL)

        val cornerRadius = if (isWide) {
            (measuredH / 2).toFloat()
        } else {
            (minOf(displayW, displayH) / 2).toFloat()
        }

        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = cornerRadius
        drawable.setColor(bgColor)
        btn.background = drawable

        val params = LinearLayout.LayoutParams(displayW, displayH)
        btn.layoutParams = params

        btn.setOnClickListener { onButtonClick(label) }
        btn.setOnLongClickListener {
            if (label == "AC" || label == "C") {
                clearAll()
                true
            } else false
        }

        // Ripple press effect
        btn.isClickable = true
        btn.isFocusable = true

        return btn
    }

    private fun getButtonColor(label: String): Int {
        return when (label) {
            "÷", "×", "−", "+", "=" -> colorRed
            "AC", "C", "±", "%", "mc", "m+", "m−", "mr",
            "(", ")", "2ⁿᵈ", "x²", "x³", "xʸ", "eˣ", "10ˣ",
            "√", "∛", "ˣ√", "ln", "log₁₀", "x!",
            "sin", "cos", "tan", "e", "π", "rand",
            "sinh", "cosh", "tanh", "Rad", "Deg", "EE" -> colorDarkGray
            else -> colorSurface
        }
    }

    private fun onButtonClick(label: String) {
        when (label) {
            "AC", "C" -> clearAll()
            "±" -> negateInput()
            "%" -> percentInput()
            "÷", "×", "−", "+" -> handleOperator(label)
            "=" -> handleEquals()
            "." -> handleDecimal()
            "(" -> handleOpenParen()
            ")" -> handleCloseParen()
            "x²" -> applyUnary { it * it }
            "x³" -> applyUnary { it * it * it }
            "√" -> applyUnary { if (it < 0) Double.NaN else sqrt(it) }
            "∛" -> applyUnary { it.pow(1.0 / 3.0) }
            "x!" -> applyUnary { factorial(it.toInt()).toDouble() }
            "ln" -> applyUnary { if (it <= 0) Double.NaN else ln(it) }
            "log₁₀" -> applyUnary { if (it <= 0) Double.NaN else log10(it) }
            "eˣ" -> applyUnary { exp(it) }
            "10ˣ" -> applyUnary { 10.0.pow(it) }
            "sin" -> applyUnary { if (isRadMode) sin(it) else sin(Math.toRadians(it)) }
            "cos" -> applyUnary { if (isRadMode) cos(it) else cos(Math.toRadians(it)) }
            "tan" -> applyUnary { if (isRadMode) tan(it) else tan(Math.toRadians(it)) }
            "sinh" -> applyUnary { sinh(it) }
            "cosh" -> applyUnary { cosh(it) }
            "tanh" -> applyUnary { tanh(it) }
            "e" -> insertConstant(Math.E)
            "π" -> insertConstant(Math.PI)
            "rand" -> insertConstant(Math.random())
            "Rad" -> { isRadMode = false; buildUI() }
            "Deg" -> { isRadMode = true; buildUI() }
            "EE" -> handleEE()
            "mc" -> { memoryValue = 0.0 }
            "m+" -> { memoryValue += currentInput.toDoubleOrNull() ?: 0.0 }
            "m−" -> { memoryValue -= currentInput.toDoubleOrNull() ?: 0.0 }
            "mr" -> {
                currentInput = formatNumber(memoryValue)
                justEvaluated = false
                updateDisplay()
            }
            "xʸ" -> handleOperator("^")
            "2ⁿᵈ" -> { /* toggle second function — for simplicity no-op */ }
            "ˣ√" -> handleOperator("ˣ√")
            else -> handleNumber(label)
        }
    }

    private fun handleNumber(digit: String) {
        if (justEvaluated) {
            currentInput = digit
            justEvaluated = false
        } else {
            if (currentInput == "0" && digit != ".") {
                currentInput = digit
            } else {
                if (currentInput.length < 12) {
                    currentInput += digit
                }
            }
        }
        updateDisplay()
        updateACButton()
    }

    private fun handleDecimal() {
        if (justEvaluated) {
            currentInput = "0."
            justEvaluated = false
        } else if (!currentInput.contains(".")) {
            currentInput += "."
        }
        updateDisplay()
    }

    private fun handleOperator(op: String) {
        val current = currentInput.toDoubleOrNull() ?: 0.0
        if (pendingOperation != null && !justEvaluated) {
            val result = evaluate(previousInput, current, pendingOperation!!)
            currentInput = formatNumber(result)
            previousInput = result
            expressionDisplay = "${formatNumber(result)} $op"
        } else {
            previousInput = current
            expressionDisplay = "${formatDisplay(currentInput)} $op"
        }
        pendingOperation = op
        justEvaluated = true
        updateDisplay()
    }

    private fun handleEquals() {
        val current = currentInput.toDoubleOrNull() ?: 0.0
        if (pendingOperation != null) {
            val result = evaluate(previousInput, current, pendingOperation!!)
            expressionDisplay = "${formatDisplay(formatNumber(previousInput))} $pendingOperation ${formatDisplay(currentInput)} ="
            currentInput = if (result.isNaN() || result.isInfinite()) "Ошибка" else formatNumber(result)
            pendingOperation = null
            justEvaluated = true
        }
        updateDisplay()
        updateACButton()
    }

    private fun evaluate(a: Double, b: Double, op: String): Double {
        return when (op) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> if (b == 0.0) Double.NaN else a / b
            "^" -> a.pow(b)
            "ˣ√" -> b.pow(1.0 / a)
            else -> b
        }
    }

    private fun applyUnary(fn: (Double) -> Double) {
        val current = currentInput.toDoubleOrNull() ?: 0.0
        val result = fn(current)
        currentInput = if (result.isNaN() || result.isInfinite()) "Ошибка" else formatNumber(result)
        expressionDisplay = ""
        justEvaluated = true
        updateDisplay()
    }

    private fun insertConstant(value: Double) {
        currentInput = formatNumber(value)
        justEvaluated = false
        updateDisplay()
    }

    private fun clearAll() {
        currentInput = "0"
        previousInput = 0.0
        pendingOperation = null
        justEvaluated = false
        expressionDisplay = ""
        updateDisplay()
    }

    private fun negateInput() {
        val v = currentInput.toDoubleOrNull()
        if (v != null) {
            currentInput = formatNumber(-v)
            updateDisplay()
        }
    }

    private fun percentInput() {
        val v = currentInput.toDoubleOrNull()
        if (v != null) {
            currentInput = formatNumber(v / 100.0)
            updateDisplay()
        }
    }

    private fun handleOpenParen() {
        // Simplified: just add to expression display
        if (justEvaluated) {
            expressionDisplay = "("
            currentInput = "0"
            justEvaluated = false
        } else {
            expressionDisplay += "("
        }
        hasParenOpen = true
        updateDisplay()
    }

    private fun handleCloseParen() {
        if (hasParenOpen) {
            expressionDisplay += currentInput + ")"
            hasParenOpen = false
            updateDisplay()
        }
    }

    private fun handleEE() {
        if (!currentInput.contains("e") && !currentInput.contains("E")) {
            currentInput += "e+"
            updateDisplay()
        }
    }

    private fun updateACButton() {
        // The AC/C button text changes — we rebuild UI minimally via display update only
        // Full rebuild would lose state; instead we just track it
    }

    private fun updateDisplay() {
        val displayText = if (currentInput == "Ошибка") "Ошибка" else formatDisplay(currentInput)
        displayTextView?.text = displayText
        expressionTextView?.text = expressionDisplay
    }

    private fun formatDisplay(value: String): String {
        if (value == "Ошибка") return "Ошибка"
        val d = value.toDoubleOrNull() ?: return value
        return formatNumber(d)
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN()) return "Ошибка"
        if (value.isInfinite()) return "Ошибка"
        // Remove trailing zeros
        val long = value.toLong()
        if (value == long.toDouble() && !value.isInfinite()) {
            // Check magnitude
            val abs = abs(value)
            return if (abs >= 1e10 || (abs < 1e-6 && abs > 0)) {
                String.format("%.4e", value)
            } else {
                long.toString()
            }
        }
        val abs = abs(value)
        return if (abs >= 1e10 || (abs < 1e-6 && abs > 0)) {
            String.format("%.4e", value)
        } else {
            // Up to 10 significant figures, strip trailing zeros
            val str = String.format("%.10f", value).trimEnd('0').trimEnd('.')
            str
        }
    }

    private fun factorial(n: Int): Long {
        if (n < 0) return -1
        if (n > 20) return Long.MAX_VALUE
        var result = 1L
        for (i in 2..n) result *= i
        return result
    }
}

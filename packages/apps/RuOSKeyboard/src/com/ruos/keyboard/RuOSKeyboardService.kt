package com.ruos.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class RuOSKeyboardService : InputMethodService() {

    private lateinit var layout: RuOSKeyboardLayout
    private val handler = Handler(Looper.getMainLooper())

    // Modes: cycle RUSSIAN → ENGLISH → (back)
    private val langModes = listOf(Layouts.RUSSIAN, Layouts.ENGLISH)
    private var langIndex = 0

    // Current word being composed (for autocorrect suggestions)
    private val composingWord = StringBuilder()
    private var isComposing = false

    // iOS-style text replacement (shortcut → phrase), loaded lazily.
    private val textReplacements by lazy { TextReplacementStore(this) }

    // Clipboard history — persisted; captured while this IME is active (see [clipListener]).
    private val clipHistory by lazy { ClipHistoryStore(this) }
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        runCatching {
            val clip = clipboardManager?.primaryClip ?: return@runCatching
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).coerceToText(this).toString().takeIf { it.isNotBlank() }
                    ?.let { clipHistory.add(it) }
            }
        }
    }

    // Shift state mirrors what MainKeyboardView shows
    private var shiftState = 0  // 0=off, 1=once, 2=caps

    // Whether next letter should auto-capitalise (start of sentence)
    private var autoCapNext = true

    // ── IME lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        // Capture copies while the RuOS keyboard is the active input method (clipboard reads are
        // allowed for the active IME; this is why we listen here rather than from a background app).
        runCatching { clipboardManager?.addPrimaryClipChangedListener(clipListener) }
    }

    override fun onDestroy() {
        runCatching { clipboardManager?.removePrimaryClipChangedListener(clipListener) }
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        layout = RuOSKeyboardLayout(this)

        layout.keyboardView.apply {
            // Sync shift state
            this.shiftState = if (autoCapNext && shiftState == 0) 1 else shiftState

            onKeyPress = { key -> handleKey(key) }

            onShiftToggle = { newState ->
                shiftState = newState
                layout.keyboardView.shiftState = shiftState
                layout.keyboardView.invalidate()
            }

            onCursorMove = { delta ->
                val ic = currentInputConnection ?: return@onCursorMove
                if (delta < 0) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_DPAD_LEFT))
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_DPAD_RIGHT))
                }
            }
        }

        layout.suggestionBar.setOnSuggestionPicked { word ->
            acceptSuggestion(word)
        }

        return layout
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composingWord.clear()
        isComposing = false

        // Auto-cap at start of text field
        val ic = currentInputConnection
        autoCapNext = when {
            info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> {
                // Check if we're at the start of a sentence
                val before = ic?.getTextBeforeCursor(2, 0)?.toString() ?: ""
                before.isEmpty() || before.last() in ".!?"
            }
            info.inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> true
            info.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> {
                shiftState = 2; false
            }
            else -> false
        }

        val appliedShift = if (autoCapNext && shiftState == 0) 1 else shiftState
        layout.keyboardView.shiftState = appliedShift
        layout.keyboardView.invalidate()
        updateSuggestions()
    }

    // ── Key handling ──────────────────────────────────────────────────────────

    private fun handleKey(key: Key) {
        val ic = currentInputConnection ?: return

        when (key.code) {

            CODE_SHIFT -> {
                shiftState = (shiftState + 1) % 3
                layout.keyboardView.shiftState = shiftState
                layout.keyboardView.invalidate()
            }

            CODE_DELETE -> {
                if (isComposing && composingWord.isNotEmpty()) {
                    composingWord.deleteCharAt(composingWord.length - 1)
                    if (composingWord.isEmpty()) {
                        ic.finishComposingText()
                        isComposing = false
                    } else {
                        ic.setComposingText(composingWord, 1)
                    }
                } else {
                    ic.deleteSurroundingText(1, 0)
                    checkAutoCapAfterDelete(ic)
                }
                updateSuggestions()
            }

            CODE_SWITCH_LANG -> {
                langIndex = (langIndex + 1) % langModes.size
                layout.keyboardView.currentMode = langModes[langIndex]
                composingWord.clear()
                ic.finishComposingText()
                isComposing = false
                updateSuggestions()
            }

            CODE_NUMBERS -> {
                commitComposing(ic)
                layout.keyboardView.currentMode = Layouts.NUMBERS
            }

            CODE_SYMBOLS -> {
                layout.keyboardView.currentMode = Layouts.SYMBOLS
            }

            CODE_BACK_ALPHA -> {
                layout.keyboardView.currentMode = langModes[langIndex]
            }

            CODE_BACK_NUMBERS -> {
                layout.keyboardView.currentMode = Layouts.NUMBERS
            }

            CODE_RETURN -> {
                commitComposing(ic)
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
                autoCapNext = true
                applyAutoShift()
            }

            CODE_SPACE -> {
                val wasComposing = isComposing
                commitComposing(ic)
                ic.commitText(" ", 1)
                autoCapNext = checkSentenceEnd(ic)
                applyAutoShift()
            }

            -999 -> {
                // Swipe-to-type synthetic commit
                val word = key.label
                commitComposing(ic)
                ic.commitText("$word ", 1)
                updateSuggestions()
            }

            else -> {
                // Regular character
                val char = if (shiftState > 0 && key.label.length == 1 && key.label[0].isLetter())
                    key.label.uppercase()
                else
                    key.label

                // Add to composing word if it's a letter
                if (char.length == 1 && char[0].isLetter()) {
                    composingWord.append(char)
                    isComposing = true
                    ic.setComposingText(composingWord, 1)
                } else {
                    // Punctuation etc — commit composing first
                    commitComposing(ic)
                    ic.commitText(char, 1)
                    if (char in "." + "!" + "?") {
                        autoCapNext = true
                        applyAutoShift()
                    }
                }

                // Revert shift-once after first letter
                if (shiftState == 1) {
                    shiftState = 0
                    layout.keyboardView.shiftState = 0
                    layout.keyboardView.invalidate()
                }

                updateSuggestions()
            }
        }
    }

    // ── Autocorrect / suggestions ─────────────────────────────────────────────

    private fun updateSuggestions() {
        if (composingWord.isEmpty()) {
            layout.suggestionBar.setSuggestions("", "", "")
            return
        }

        val lang = if (langModes[langIndex].name == "ru") Dictionary.Lang.RU else Dictionary.Lang.EN
        val word = composingWord.toString()
        val suggestions = Dictionary.suggest(word, lang)
        val correction  = Dictionary.autocorrect(word, lang)

        val center = correction ?: word
        val left   = suggestions.getOrElse(0) { "" }
        val right  = suggestions.getOrElse(1) { "" }

        layout.suggestionBar.setSuggestions(left, center, right)
    }

    private fun acceptSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (isComposing) {
            ic.setComposingText(word, 1)
            ic.finishComposingText()
            composingWord.clear()
            isComposing = false
        } else {
            ic.commitText(word, 1)
        }
        ic.commitText(" ", 1)
        autoCapNext = false
        updateSuggestions()
    }

    // ── Auto-capitalisation helpers ───────────────────────────────────────────

    private fun checkSentenceEnd(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(3, 0)?.toString() ?: return false
        return before.trimEnd().lastOrNull() in listOf('.', '!', '?')
    }

    private fun checkAutoCapAfterDelete(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: return
        autoCapNext = before.isEmpty() || before.last() in listOf('.', '!', '?', '\n')
        applyAutoShift()
    }

    private fun applyAutoShift() {
        if (autoCapNext && shiftState == 0) {
            layout.keyboardView.shiftState = 1
            layout.keyboardView.invalidate()
        }
    }

    // ── Composing text ────────────────────────────────────────────────────────

    private fun commitComposing(ic: InputConnection) {
        if (isComposing && composingWord.isNotEmpty()) {
            // iOS text replacement: if the finished word is a shortcut, swap in its phrase.
            val expansion = textReplacements.expand(composingWord.toString())
            if (expansion != null) ic.setComposingText(expansion, 1)
            ic.finishComposingText()
            composingWord.clear()
            isComposing = false
        }
    }

    // ── Shake to undo ────────────────────────────────────────────────────────

    // Shake is detected by the hosting activity/SystemUI. If future implementation
    // adds a shake sensor, call this method.
    fun performUndo() {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z,
            0, KeyEvent.META_CTRL_ON))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z,
            0, KeyEvent.META_CTRL_ON))
    }
}

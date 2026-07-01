package com.ruos.notes

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.*
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.StrikethroughSpan
import android.text.style.ImageSpan
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import java.io.File
import android.view.*
import android.widget.*

class NoteEditorActivity : Activity() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }

    private val colorBg = Color.parseColor("#000000")
    private val colorSurface = Color.parseColor("#1C1C1E")
    private val colorSurface2 = Color.parseColor("#2C2C2E")
    private val colorRed = Color.parseColor("#D94F3D")
    private val colorText = Color.parseColor("#FFFFFF")
    private val colorSecondary = Color.parseColor("#8E8E93")
    private val colorSeparator = Color.parseColor("#38383A")

    private lateinit var db: NotesDatabase
    private var noteId: Long = -1L
    private var existingNote: Note? = null

    private lateinit var noteEditText: EditText
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable { saveNote() }
    private val AUTO_SAVE_DELAY = 500L
    private var hasUnsavedChanges = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun navBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(34)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        db = NotesDatabase(this)
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId != -1L) {
            existingNote = db.getNoteById(noteId)
        }

        buildUI()
    }

    override fun onDestroy() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        saveNote()
        super.onDestroy()
    }

    override fun onBackPressed() {
        saveNote()
        setResult(RESULT_OK)
        super.onBackPressed()
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(colorBg)
        root.fitsSystemWindows = true

        // Top bar
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setBackgroundColor(colorBg)
        val topBarParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            statusBarHeight() + dp(52)
        )
        topBar.layoutParams = topBarParams
        topBar.setPadding(dp(8), statusBarHeight(), dp(8), 0)

        // Back button
        val backBtn = TextView(this)
        backBtn.text = "‹ Заметки"
        backBtn.textSize = 17f
        backBtn.setTextColor(colorRed)
        backBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
        backBtn.isClickable = true
        backBtn.isFocusable = true
        backBtn.setOnClickListener {
            saveNote()
            setResult(RESULT_OK)
            finish()
        }
        topBar.addView(backBtn)

        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        topBar.addView(spacer)

        // Share button
        val shareBtn = ImageView(this)
        shareBtn.setImageResource(android.R.drawable.ic_menu_share)
        shareBtn.setColorFilter(colorRed)
        shareBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
        shareBtn.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        shareBtn.isClickable = true
        shareBtn.isFocusable = true
        shareBtn.setOnClickListener { shareNote() }
        topBar.addView(shareBtn)

        // Done button
        val doneBtn = TextView(this)
        doneBtn.text = "Готово"
        doneBtn.textSize = 17f
        doneBtn.setTextColor(colorRed)
        doneBtn.setTypeface(null, Typeface.BOLD)
        doneBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
        doneBtn.setOnClickListener {
            saveNote()
            // Hide keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(noteEditText.windowToken, 0)
        }
        topBar.addView(doneBtn)

        root.addView(topBar)

        // Separator
        val topSep = View(this)
        topSep.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        topSep.setBackgroundColor(colorSeparator)
        root.addView(topSep)

        // Editor area
        val editorScroll = ScrollView(this)
        editorScroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        editorScroll.isFillViewport = true

        val editorContainer = LinearLayout(this)
        editorContainer.orientation = LinearLayout.VERTICAL
        editorContainer.setPadding(dp(16), dp(12), dp(16), dp(12))

        noteEditText = EditText(this)
        noteEditText.background = null
        noteEditText.setTextColor(colorText)
        noteEditText.setHintTextColor(colorSecondary)
        noteEditText.hint = "Начните писать..."
        noteEditText.textSize = 16f
        noteEditText.gravity = Gravity.TOP
        noteEditText.isVerticalScrollBarEnabled = false
        noteEditText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        (noteEditText.layoutParams as LinearLayout.LayoutParams).weight = 1f
        noteEditText.minHeight = dp(400)

        // Set initial text
        val initialText = buildInitialText()
        noteEditText.setText(initialText)
        if (initialText.isNotEmpty()) {
            noteEditText.setSelection(initialText.length)
        }

        // Apply title styling to first line
        applyTitleStyle()
        // Render any [photo:…] markers from a saved note into inline images
        noteEditText.post { renderPhotos() }

        noteEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hasUnsavedChanges = true
                applyTitleStyle()
                scheduleAutoSave()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        editorContainer.addView(noteEditText)
        editorScroll.addView(editorContainer)
        root.addView(editorScroll)

        // Format toolbar (shows above keyboard)
        val formatBar = buildFormatToolbar()
        root.addView(formatBar)

        // Bottom nav spacer
        val navSpacer = View(this)
        navSpacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, navBarHeight()
        )
        navSpacer.setBackgroundColor(colorSurface2)
        root.addView(navSpacer)

        setContentView(root)

        // Auto-focus
        noteEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        noteEditText.postDelayed({
            imm.showSoftInput(noteEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun buildFormatToolbar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(colorSurface2)
        bar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
        )
        bar.setPadding(dp(8), 0, dp(8), 0)

        fun makeToolBtn(label: String, action: () -> Unit): TextView {
            val btn = TextView(this)
            btn.text = label
            btn.textSize = 16f
            btn.setTextColor(colorText)
            btn.gravity = Gravity.CENTER
            btn.layoutParams = LinearLayout.LayoutParams(dp(44), dp(36))
            btn.isClickable = true
            btn.isFocusable = true
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.TRANSPARENT)
            }
            btn.setOnClickListener { action() }
            return btn
        }

        bar.addView(makeToolBtn("B") { applyBold() })
        bar.addView(makeToolBtn("I") { applyItalic() })
        bar.addView(makeToolBtn("U") { applyUnderline() })

        val sep = View(this)
        sep.layoutParams = LinearLayout.LayoutParams(1, dp(24))
        sep.setBackgroundColor(colorSeparator)
        val sepParams = sep.layoutParams as LinearLayout.LayoutParams
        sepParams.setMargins(dp(4), 0, dp(4), 0)
        sep.layoutParams = sepParams
        bar.addView(sep)

        bar.addView(makeToolBtn("Список") { insertChecklist() })
        bar.addView(makeToolBtn("Фото") { pickPhoto() })   // now functional: persists as a marker + file

        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        bar.addView(spacer)

        bar.addView(makeToolBtn("Скрыть") {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(noteEditText.windowToken, 0)
        })

        return bar
    }

    private fun buildInitialText(): String {
        val note = existingNote ?: return ""
        return if (note.title.isEmpty() && note.body.isEmpty()) {
            ""
        } else if (note.body.isEmpty()) {
            note.title
        } else {
            "${note.title}\n${note.body}"
        }
    }

    private fun applyTitleStyle() {
        val text = noteEditText.text ?: return
        val fullText = text.toString()
        val firstNewline = fullText.indexOf('\n')
        val titleEnd = if (firstNewline >= 0) firstNewline else fullText.length

        // Remove existing title spans
        val existingBold = text.getSpans(0, titleEnd, StyleSpan::class.java)
        existingBold.forEach { span ->
            if ((span as StyleSpan).style == Typeface.BOLD) {
                text.removeSpan(span)
            }
        }

        if (titleEnd > 0) {
            text.setSpan(StyleSpan(Typeface.BOLD), 0, titleEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun applyBold() {
        val start = noteEditText.selectionStart
        val end = noteEditText.selectionEnd
        if (start < end) {
            val editable = noteEditText.text
            val spans = editable.getSpans(start, end, StyleSpan::class.java)
            val hasBold = spans.any { it.style == Typeface.BOLD }
            if (hasBold) {
                spans.filter { it.style == Typeface.BOLD }.forEach { editable.removeSpan(it) }
            } else {
                editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun applyItalic() {
        val start = noteEditText.selectionStart
        val end = noteEditText.selectionEnd
        if (start < end) {
            val editable = noteEditText.text
            val spans = editable.getSpans(start, end, StyleSpan::class.java)
            val hasItalic = spans.any { it.style == Typeface.ITALIC }
            if (hasItalic) {
                spans.filter { it.style == Typeface.ITALIC }.forEach { editable.removeSpan(it) }
            } else {
                editable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun applyUnderline() {
        val start = noteEditText.selectionStart
        val end = noteEditText.selectionEnd
        if (start < end) {
            val editable = noteEditText.text
            val spans = editable.getSpans(start, end, UnderlineSpan::class.java)
            if (spans.isNotEmpty()) {
                spans.forEach { editable.removeSpan(it) }
            } else {
                editable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun insertChecklist() {
        val editable = noteEditText.text ?: return
        val cursor = noteEditText.selectionStart
        // Insert a checkbox marker at current line start
        val lineStart = if (cursor > 0) {
            val text = editable.toString()
            val lastNewline = text.lastIndexOf('\n', cursor - 1)
            if (lastNewline < 0) 0 else lastNewline + 1
        } else 0
        editable.insert(lineStart, "[ ] ")
        noteEditText.setSelection(lineStart + 4)
    }

    private fun scheduleAutoSave() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY)
    }

    private fun saveNote() {
        val text = noteEditText.text?.toString() ?: ""
        if (text.isBlank()) {
            // Don't save empty notes
            return
        }

        val firstNewline = text.indexOf('\n')
        val title = if (firstNewline >= 0) text.substring(0, firstNewline).trim() else text.trim()
        val body = if (firstNewline >= 0) text.substring(firstNewline + 1).trim() else ""

        if (noteId != -1L) {
            db.updateNote(noteId, title, body)
        } else {
            noteId = db.insertNote(title, body)
        }
        hasUnsavedChanges = false
    }

    // ── Photo attachments (persist as a [photo:file] marker + a file in the note dir) ──
    private val REQ_PHOTO = 4001
    private val photoRe = Regex("\\[photo:([^\\]]+)\\]")

    private fun photosDir(): File = File(filesDir, "notes/$noteId").apply { mkdirs() }

    /** A new note has no id yet; materialise one so photos have a stable home. */
    private fun ensureNoteId() {
        if (noteId != -1L) return
        val text = noteEditText.text?.toString() ?: ""
        val nl = text.indexOf('\n')
        val title = if (nl >= 0) text.substring(0, nl).trim() else text.trim()
        val body = if (nl >= 0) text.substring(nl + 1).trim() else ""
        noteId = db.insertNote(title, body)
    }

    private fun pickPhoto() {
        ensureNoteId()
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        runCatching { startActivityForResult(i, REQ_PHOTO) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PHOTO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val name = "${System.currentTimeMillis()}.jpg"
        val ok = runCatching { copyDownscaled(uri, File(photosDir(), name)) }.getOrDefault(false)
        if (!ok) return
        // insert the marker at the cursor; ImageSpan overlays it so text.toString() keeps the marker
        val pos = noteEditText.selectionStart.coerceAtLeast(0)
        noteEditText.text?.insert(pos, "\n[photo:$name]\n")
        renderPhotos()
        hasUnsavedChanges = true
        scheduleAutoSave()
    }

    /** Decode a picked image at a sane size and re-encode as JPEG into the note dir. */
    private fun copyDownscaled(uri: Uri, dest: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxDim = 1440
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        dest.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        return true
    }

    /** Replace every [photo:file] marker in the text with the loaded image (ImageSpan). */
    private fun renderPhotos() {
        val editable = noteEditText.text ?: return
        // clear stale spans so we don't stack them on re-render
        editable.getSpans(0, editable.length, ImageSpan::class.java).forEach { editable.removeSpan(it) }
        val maxW = (noteEditText.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - dp(48)
        for (m in photoRe.findAll(editable.toString())) {
            val file = File(photosDir(), m.groupValues[1])
            if (!file.exists()) continue
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: continue
            val scale = if (bmp.width > maxW) maxW.toFloat() / bmp.width else 1f
            val dr = BitmapDrawable(resources, bmp).apply {
                setBounds(0, 0, (bmp.width * scale).toInt(), (bmp.height * scale).toInt())
            }
            editable.setSpan(ImageSpan(dr, ImageSpan.ALIGN_BASELINE),
                m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun shareNote() {
        val text = noteEditText.text?.toString() ?: ""
        if (text.isBlank()) return
        val firstNewline = text.indexOf('\n')
        val title = if (firstNewline >= 0) text.substring(0, firstNewline).trim() else text.trim()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, "Поделиться заметкой"))
    }
}

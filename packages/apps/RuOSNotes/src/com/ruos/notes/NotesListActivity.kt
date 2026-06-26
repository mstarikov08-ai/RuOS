package com.ruos.notes

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class NotesListActivity : Activity() {

    private val colorBg = Color.parseColor("#000000")
    private val colorSurface = Color.parseColor("#1C1C1E")
    private val colorSurface2 = Color.parseColor("#2C2C2E")
    private val colorRed = Color.parseColor("#D94F3D")
    private val colorText = Color.parseColor("#FFFFFF")
    private val colorSecondary = Color.parseColor("#8E8E93")
    private val colorSeparator = Color.parseColor("#38383A")

    private lateinit var db: NotesDatabase
    private var notes = listOf<Note>()
    private var searchQuery = ""
    private var notesListContainer: LinearLayout? = null
    private var notesCountView: TextView? = null
    private var searchBar: EditText? = null

    companion object {
        const val REQUEST_EDIT = 1
        const val REQUEST_NEW = 2
    }

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

        db = NotesDatabase(this)
        seedIfEmpty()
        buildUI()
        loadNotes()
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            loadNotes()
        }
    }

    private fun seedIfEmpty() {
        if (db.getCount() == 0) {
            db.insertNote(
                getString(R.string.welcome_note_title),
                getString(R.string.welcome_note_body)
            )
            db.insertNote(
                getString(R.string.shopping_note_title),
                getString(R.string.shopping_note_body)
            )
        }
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(colorBg)

        // Status bar spacer
        val statusSpacer = View(this)
        statusSpacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, statusBarHeight()
        )
        root.addView(statusSpacer)

        // Scrollable content
        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        scroll.isFillViewport = true

        val contentLayout = LinearLayout(this)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.setPadding(dp(16), dp(8), dp(16), dp(16))

        // Title row
        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL
        titleRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val titleView = TextView(this)
        titleView.text = "Заметки"
        titleView.textSize = 34f
        titleView.setTextColor(colorText)
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        titleRow.addView(titleView)

        contentLayout.addView(titleRow)

        // Search bar
        val searchContainer = LinearLayout(this)
        searchContainer.orientation = LinearLayout.HORIZONTAL
        searchContainer.gravity = Gravity.CENTER_VERTICAL
        searchContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(colorSurface2)
        }
        val searchParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(36)
        )
        searchParams.topMargin = dp(12)
        searchParams.bottomMargin = dp(12)
        searchContainer.layoutParams = searchParams
        searchContainer.setPadding(dp(8), 0, dp(8), 0)

        val searchIcon = ImageView(this)
        searchIcon.setImageResource(android.R.drawable.ic_menu_search)
        searchIcon.setColorFilter(colorSecondary)
        searchIcon.layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        searchIcon.setPadding(0, 0, dp(4), 0)
        searchContainer.addView(searchIcon)

        val searchEdit = EditText(this)
        searchEdit.hint = "Поиск"
        searchEdit.setHintTextColor(colorSecondary)
        searchEdit.setTextColor(colorText)
        searchEdit.textSize = 16f
        searchEdit.background = null
        searchEdit.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        )
        searchEdit.setSingleLine(true)
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                loadNotes()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        searchBar = searchEdit
        searchContainer.addView(searchEdit)

        contentLayout.addView(searchContainer)

        // Folders section
        val folderHeader = TextView(this)
        folderHeader.text = "Папки"
        folderHeader.textSize = 22f
        folderHeader.setTextColor(colorText)
        folderHeader.setTypeface(null, Typeface.BOLD)
        folderHeader.setPadding(dp(4), 0, 0, dp(8))
        contentLayout.addView(folderHeader)

        val folderCard = LinearLayout(this)
        folderCard.orientation = LinearLayout.VERTICAL
        folderCard.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(colorSurface)
        }
        folderCard.setPadding(dp(16), 0, dp(16), 0)

        // All notes folder row
        val allNotesRow = LinearLayout(this)
        allNotesRow.orientation = LinearLayout.HORIZONTAL
        allNotesRow.gravity = Gravity.CENTER_VERTICAL
        allNotesRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        )
        allNotesRow.isClickable = true
        allNotesRow.isFocusable = true

        val folderIcon = ImageView(this)
        folderIcon.setImageDrawable(folderDrawable(colorSecondary))
        folderIcon.layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).also {
            it.marginEnd = dp(12)
        }
        allNotesRow.addView(folderIcon)

        val folderNameLbl = TextView(this)
        folderNameLbl.text = "Все заметки"
        folderNameLbl.textSize = 17f
        folderNameLbl.setTextColor(colorText)
        folderNameLbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        allNotesRow.addView(folderNameLbl)

        val countLbl = TextView(this)
        countLbl.textSize = 17f
        countLbl.setTextColor(colorSecondary)
        notesCountView = countLbl
        allNotesRow.addView(countLbl)

        val chevron = TextView(this)
        chevron.text = "›"
        chevron.textSize = 20f
        chevron.setTextColor(colorSecondary)
        chevron.setPadding(dp(8), 0, 0, 0)
        allNotesRow.addView(chevron)

        folderCard.addView(allNotesRow)
        contentLayout.addView(folderCard)

        // Spacing
        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(24)
        )
        contentLayout.addView(spacer)

        // Notes header
        val notesHeader = TextView(this)
        notesHeader.text = "Заметки"
        notesHeader.textSize = 22f
        notesHeader.setTextColor(colorText)
        notesHeader.setTypeface(null, Typeface.BOLD)
        notesHeader.setPadding(dp(4), 0, 0, dp(8))
        contentLayout.addView(notesHeader)

        // Notes list
        val notesList = LinearLayout(this)
        notesList.orientation = LinearLayout.VERTICAL
        notesListContainer = notesList
        contentLayout.addView(notesList)

        // Bottom spacer for nav bar
        val navSpacer = View(this)
        navSpacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, navBarHeight() + dp(80)
        )
        contentLayout.addView(navSpacer)

        scroll.addView(contentLayout)
        root.addView(scroll)

        // Floating action area (bottom bar)
        val bottomBar = FrameLayout(this)
        val bottomBarHeight = dp(56) + navBarHeight()
        bottomBar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        )
        // We use a FrameLayout overlay trick with relative positioning
        root.addView(bottomBar)

        setContentView(root)

        // Floating + button — overlay on top of scroll
        val overlayFrame = FrameLayout(this)
        overlayFrame.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val bottomToolbar = LinearLayout(this)
        bottomToolbar.orientation = LinearLayout.HORIZONTAL
        bottomToolbar.gravity = Gravity.CENTER_VERTICAL
        bottomToolbar.setBackgroundColor(Color.parseColor("#111111"))
        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(56) + navBarHeight()
        )
        barParams.gravity = Gravity.BOTTOM
        bottomToolbar.layoutParams = barParams
        bottomToolbar.setPadding(dp(16), 0, dp(16), navBarHeight())

        val notesCountBar = TextView(this)
        notesCountBar.textSize = 13f
        notesCountBar.setTextColor(colorSecondary)
        notesCountBar.gravity = Gravity.CENTER
        notesCountBar.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        bottomToolbar.addView(notesCountBar)

        val newNoteBtn = ImageView(this)
        newNoteBtn.setImageResource(android.R.drawable.ic_menu_edit)
        newNoteBtn.setColorFilter(colorText)
        newNoteBtn.isClickable = true
        newNoteBtn.isFocusable = true
        newNoteBtn.setOnClickListener { openNoteEditor(null) }
        newNoteBtn.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        bottomToolbar.addView(newNoteBtn)

        overlayFrame.addView(bottomToolbar)

        // Add overlay to root
        val rootDecor = window.decorView as FrameLayout
        rootDecor.post {
            val rootView = rootDecor.getChildAt(0)
            if (rootView is FrameLayout) {
                rootView.addView(overlayFrame)
            } else {
                val wrapperFrame = FrameLayout(this)
                wrapperFrame.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                wrapperFrame.addView(overlayFrame)
                rootDecor.addView(wrapperFrame)
            }
        }
    }

    private fun loadNotes() {
        notes = if (searchQuery.isEmpty()) {
            db.getAllNotes()
        } else {
            db.searchNotes(searchQuery)
        }
        notesCountView?.text = notes.size.toString()
        renderNotesList()
    }

    private fun renderNotesList() {
        val container = notesListContainer ?: return
        container.removeAllViews()

        if (notes.isEmpty()) {
            val empty = TextView(this)
            empty.text = if (searchQuery.isEmpty()) "Нет заметок" else "Ничего не найдено"
            empty.textSize = 16f
            empty.setTextColor(colorSecondary)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(32), 0, 0)
            container.addView(empty)
            return
        }

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(colorSurface)
        }

        notes.forEachIndexed { i, note ->
            val rowWrapper = FrameLayout(this)
            rowWrapper.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // Delete background (shown on swipe)
            val deleteBg = LinearLayout(this)
            deleteBg.orientation = LinearLayout.HORIZONTAL
            deleteBg.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            deleteBg.setBackgroundColor(colorRed)
            deleteBg.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            deleteBg.setPadding(0, 0, dp(20), 0)
            val deleteIcon = ImageView(this)
            deleteIcon.setImageDrawable(trashDrawable(colorText))
            deleteIcon.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            deleteBg.addView(deleteIcon)
            deleteBg.visibility = View.GONE
            rowWrapper.addView(deleteBg)

            // Note row
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            row.setPadding(dp(16), dp(12), dp(16), dp(12))
            row.setBackgroundColor(colorSurface)
            row.isClickable = true
            row.isFocusable = true

            val titleView = TextView(this)
            titleView.text = note.title.ifEmpty { "Без названия" }
            titleView.textSize = 16f
            titleView.setTextColor(colorText)
            titleView.setTypeface(null, Typeface.BOLD)
            titleView.maxLines = 1
            row.addView(titleView)

            val previewRow = LinearLayout(this)
            previewRow.orientation = LinearLayout.HORIZONTAL

            val dateStr = formatNoteDate(note.updatedAt)
            val dateLbl = TextView(this)
            dateLbl.text = dateStr
            dateLbl.textSize = 13f
            dateLbl.setTextColor(colorSecondary)
            dateLbl.setPadding(0, 0, dp(8), 0)
            previewRow.addView(dateLbl)

            val bodyPreview = TextView(this)
            val bodyLine = note.body.lines().firstOrNull { it.isNotBlank() } ?: ""
            bodyPreview.text = bodyLine.take(40)
            bodyPreview.textSize = 13f
            bodyPreview.setTextColor(colorSecondary)
            bodyPreview.maxLines = 1
            bodyPreview.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            previewRow.addView(bodyPreview)

            row.addView(previewRow)

            // Click to edit
            row.setOnClickListener { openNoteEditor(note) }

            // Swipe gesture for delete
            var startX = 0f
            var swipedOut = false
            row.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX
                        if (dx < -dp(20)) {
                            deleteBg.visibility = View.VISIBLE
                            row.translationX = maxOf(dx, -dp(80).toFloat())
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dx = event.rawX - startX
                        if (dx < -dp(60) && !swipedOut) {
                            // Confirm delete
                            row.translationX = 0f
                            deleteBg.visibility = View.GONE
                            db.deleteNote(note.id)
                            loadNotes()
                            true
                        } else {
                            row.translationX = 0f
                            deleteBg.visibility = View.GONE
                            false
                        }
                    }
                    else -> false
                }
            }

            rowWrapper.addView(row)
            card.addView(rowWrapper)

            if (i < notes.size - 1) {
                val sep = View(this)
                sep.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                sep.setBackgroundColor(colorSeparator)
                sep.setPadding(dp(16), 0, 0, 0)
                card.addView(sep)
            }
        }

        container.addView(card)
    }

    private fun openNoteEditor(note: Note?) {
        val intent = Intent(this, NoteEditorActivity::class.java)
        if (note != null) {
            intent.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id)
        }
        startActivityForResult(intent, if (note == null) REQUEST_NEW else REQUEST_EDIT)
    }

    private fun folderDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = android.graphics.Paint.Style.FILL
            }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            // Folder tab
            val tabPath = android.graphics.Path()
            tabPath.moveTo(w*0.05f, h*0.35f); tabPath.lineTo(w*0.05f, h*0.3f)
            tabPath.lineTo(w*0.35f, h*0.3f); tabPath.lineTo(w*0.45f, h*0.4f)
            tabPath.lineTo(w*0.95f, h*0.4f); tabPath.lineTo(w*0.95f, h*0.9f)
            tabPath.lineTo(w*0.05f, h*0.9f); tabPath.close()
            canvas.drawPath(tabPath, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun trashDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = android.graphics.Paint.Style.STROKE; strokeWidth = bounds.width() * 0.09f
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            // Lid
            canvas.drawLine(w*0.2f, h*0.2f, w*0.8f, h*0.2f, p)
            canvas.drawLine(w*0.4f, h*0.1f, w*0.6f, h*0.1f, p)
            // Body
            canvas.drawRoundRect(android.graphics.RectF(w*0.25f, h*0.25f, w*0.75f, h*0.9f), w*0.05f, w*0.05f, p)
            // Lines inside
            canvas.drawLine(w*0.4f, h*0.38f, w*0.4f, h*0.78f, p)
            canvas.drawLine(w*0.5f, h*0.38f, w*0.5f, h*0.78f, p)
            canvas.drawLine(w*0.6f, h*0.38f, w*0.6f, h*0.78f, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun formatNoteDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        cal.timeInMillis = timestamp

        return when {
            diff < 60_000 -> "Только что"
            diff < 3600_000 -> "${diff / 60_000} мин назад"
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                SimpleDateFormat("HH:mm", Locale("ru")).format(Date(timestamp))
            }
            diff < 7 * 24 * 3600_000 -> {
                val days = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
                days[cal.get(Calendar.DAY_OF_WEEK) - 1]
            }
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                SimpleDateFormat("d MMM", Locale("ru")).format(Date(timestamp))
            }
            else -> SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(timestamp))
        }
    }
}

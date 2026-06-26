package com.ruos.contacts

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.ContactsContract
import android.text.*
import android.view.*
import android.widget.*

class ContactsActivity : Activity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 102
        val BG = Color.parseColor("#000000")
        val SURFACE = Color.parseColor("#1C1C1E")
        val SURFACE2 = Color.parseColor("#2C2C2E")
        val RED = Color.parseColor("#D94F3D")
        val BLUE = Color.parseColor("#0A84FF")
        val TEXT = Color.WHITE
        val TEXT_SEC = Color.parseColor("#8E8E93")
        val SEP = Color.parseColor("#38383A")

        val AVATAR_COLORS = listOf(
            Color.parseColor("#5E5CE6"),
            Color.parseColor("#30D158"),
            Color.parseColor("#0A84FF"),
            Color.parseColor("#FF9F0A"),
            Color.parseColor("#D94F3D"),
            Color.parseColor("#BF5AF2")
        )
    }

    data class Contact(
        val id: Long,
        val name: String,
        val hasPhoto: Boolean,
        val photoUri: String?
    )

    private lateinit var listContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var searchEdit: EditText
    private lateinit var scrubberLayout: LinearLayout
    private lateinit var rootLayout: FrameLayout

    private var allContacts: List<Contact> = emptyList()
    private val sectionOffsets = mutableMapOf<String, Int>() // letter -> scroll Y offset

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildUI()
        checkPermissionsAndLoad()
    }

    private fun buildUI() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(BG)
        }
        setContentView(rootLayout)

        // Main scroll area
        scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).also { it.marginEnd = dp(28) } // room for scrubber
            isVerticalScrollBarEnabled = false
        }
        rootLayout.addView(scrollView)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(56), 0, dp(20))
        }
        scrollView.addView(listContainer)

        // Title row
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(4))
        }

        titleRow.addView(TextView(this).apply {
            text = "Контакты"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val addBtn = TextView(this).apply {
            text = "+"
            textSize = 28f
            setTextColor(BLUE)
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener {
                startActivity(Intent(this@ContactsActivity, AddContactActivity::class.java))
            }
        }
        titleRow.addView(addBtn)
        listContainer.addView(titleRow)

        // Search bar
        searchEdit = EditText(this).apply {
            hint = "Поиск"
            setHintTextColor(TEXT_SEC)
            setTextColor(TEXT)
            textSize = 16f
            background = GradientDrawable().apply {
                setColor(SURFACE2)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(36), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }

        val searchContainer = FrameLayout(this).apply {
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        // Search icon
        val searchIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(TEXT_SEC)
            layoutParams = FrameLayout.LayoutParams(dp(32), dp(40)).also {
                it.gravity = Gravity.CENTER_VERTICAL
                it.marginStart = dp(8)
            }
        }
        searchContainer.addView(searchEdit, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(40)
        ))
        searchContainer.addView(searchIcon)
        listContainer.addView(searchContainer)

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterContacts(s?.toString() ?: "")
            }
        })

        // Alphabet scrubber (right side)
        scrubberLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(dp(28), FrameLayout.LayoutParams.MATCH_PARENT).also {
                it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            setPadding(0, dp(80), 0, dp(20))
        }
        rootLayout.addView(scrubberLayout)
    }

    private fun checkPermissionsAndLoad() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            showPermissionDenied()
        }
    }

    private fun showPermissionDenied() {
        listContainer.addView(TextView(this).apply {
            text = "Требуется разрешение на чтение контактов"
            textSize = 15f
            setTextColor(TEXT_SEC)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(40), dp(20), dp(40))
        })
    }

    private fun loadContacts() {
        Thread {
            val contacts = queryContacts()
            runOnUiThread {
                allContacts = contacts
                renderContacts(contacts)
                buildScrubber(contacts)
            }
        }.start()
    }

    private fun queryContacts(): List<Contact> {
        val result = mutableListOf<Contact>()
        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
            )
            val cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL",
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
            ) ?: return result

            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val name = it.getString(1) ?: continue
                    val photoUri = it.getString(2)
                    result.add(Contact(
                        id = id,
                        name = name,
                        hasPhoto = photoUri != null,
                        photoUri = photoUri
                    ))
                }
            }
        } catch (e: Exception) {}
        return result
    }

    private fun renderContacts(contacts: List<Contact>) {
        // Remove all items below search bar (index 2+)
        while (listContainer.childCount > 2) {
            listContainer.removeViewAt(2)
        }
        sectionOffsets.clear()

        if (contacts.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Нет контактов"
                textSize = 15f
                setTextColor(TEXT_SEC)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            })
            return
        }

        var lastLetter = ""
        contacts.forEach { contact ->
            val firstChar = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            if (firstChar != lastLetter) {
                lastLetter = firstChar
                val header = buildSectionHeader(firstChar)
                listContainer.addView(header)
                // Record offset after layout
                header.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        header.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        sectionOffsets[firstChar] = header.top
                    }
                })
            }
            listContainer.addView(buildContactRow(contact))
            listContainer.addView(buildSeparator())
        }
    }

    private fun filterContacts(query: String) {
        val filtered = if (query.isEmpty()) allContacts
        else allContacts.filter { it.name.contains(query, ignoreCase = true) }
        renderContacts(filtered)
    }

    private fun buildSectionHeader(letter: String): TextView {
        return TextView(this).apply {
            text = letter
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_SEC)
            setBackgroundColor(SURFACE2)
            setPadding(dp(20), dp(6), dp(20), dp(6))
        }
    }

    private fun buildContactRow(contact: Contact): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener {
                val intent = Intent(this@ContactsActivity, ContactDetailActivity::class.java).apply {
                    putExtra("contact_id", contact.id)
                    putExtra("contact_name", contact.name)
                    putExtra("photo_uri", contact.photoUri)
                }
                startActivity(intent)
            }

            val avatar = buildAvatar(contact, dp(44))
            addView(avatar)

            addView(TextView(this@ContactsActivity).apply {
                text = contact.name
                textSize = 16f
                setTextColor(TEXT)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(14) }
            })

            // Chevron
            addView(TextView(this@ContactsActivity).apply {
                text = "›"
                textSize = 22f
                setTextColor(TEXT_SEC)
            })
        }
    }

    private fun buildAvatar(contact: Contact, sizePx: Int): View {
        if (contact.hasPhoto && contact.photoUri != null) {
            return ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                    }
                }
                try {
                    val bmp = contentResolver.loadThumbnail(
                        android.net.Uri.parse(contact.photoUri),
                        android.util.Size(sizePx, sizePx),
                        null
                    )
                    setImageBitmap(bmp)
                } catch (e: Exception) {
                    setImageDrawable(buildInitialsDrawable(contact.name, sizePx))
                }
            }
        }

        return buildInitialsView(contact.name, sizePx)
    }

    private fun buildInitialsView(name: String, sizePx: Int): View {
        return object : View(this) {
            override fun onDraw(canvas: Canvas) {
                val bgColor = AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.size]
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = bgColor
                }
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, cx, paint)

                val initials = getInitials(name)
                paint.color = Color.WHITE
                paint.textSize = sizePx * 0.38f
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT_BOLD
                val metrics = paint.fontMetrics
                canvas.drawText(initials, cx, cy - (metrics.ascent + metrics.descent) / 2, paint)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }
    }

    private fun buildInitialsDrawable(name: String, sizePx: Int): android.graphics.drawable.Drawable {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgColor = AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.size]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
        paint.color = Color.WHITE
        paint.textSize = sizePx * 0.38f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        val metrics = paint.fontMetrics
        canvas.drawText(getInitials(name), sizePx / 2f, sizePx / 2f - (metrics.ascent + metrics.descent) / 2, paint)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun getInitials(name: String): String {
        val words = name.trim().split("\\s+".toRegex())
        return when {
            words.size >= 2 -> "${words[0].firstOrNull() ?: ""}${words[1].firstOrNull() ?: ""}".uppercase()
            words.isNotEmpty() -> words[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun buildScrubber(contacts: List<Contact>) {
        scrubberLayout.removeAllViews()
        val letters = contacts.map { it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            .distinct().sorted()

        letters.forEach { letter ->
            val tv = TextView(this).apply {
                text = letter
                textSize = 10f
                setTextColor(BLUE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(18)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                }
                setOnClickListener {
                    val offset = sectionOffsets[letter] ?: 0
                    scrollView.smoothScrollTo(0, offset)
                }
            }
            scrubberLayout.addView(tv)
        }
    }

    private fun buildSeparator(): View {
        return View(this).apply {
            setBackgroundColor(SEP)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.marginStart = dp(78) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

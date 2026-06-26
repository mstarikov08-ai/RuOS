package com.ruos.messages

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.text.*
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MessagesActivity : Activity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 103
        private val PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS
        )
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

    data class Conversation(
        val threadId: Long,
        val address: String,
        val contactName: String?,
        val snippet: String,
        val date: Long,
        val unreadCount: Int
    )

    private lateinit var listContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var searchEdit: EditText
    private var allConversations: List<Conversation> = emptyList()
    private var smsObserver: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildUI()
        checkPermissionsAndLoad()
    }

    private fun buildUI() {
        val rootLayout = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(rootLayout)

        scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        rootLayout.addView(scrollView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

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
            text = "Сообщения"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Edit/compose button
        val composeBtn = TextView(this).apply {
            text = "✏"
            textSize = 22f
            setTextColor(BLUE)
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { showComposeDialog() }
        }
        titleRow.addView(composeBtn)
        listContainer.addView(titleRow)

        // Search bar
        val searchContainer = FrameLayout(this).apply {
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
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
        }
        val searchIcon = TextView(this).apply {
            text = "🔍"
            textSize = 13f
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
                filterConversations(s?.toString() ?: "")
            }
        })
    }

    private fun checkPermissionsAndLoad() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            loadConversations()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            loadConversations()
        }
    }

    private fun loadConversations() {
        Thread {
            val conversations = queryConversations()
            runOnUiThread {
                allConversations = conversations
                renderConversations(conversations)
                registerSmsObserver()
            }
        }.start()
    }

    private fun queryConversations(): List<Conversation> {
        val result = mutableListOf<Conversation>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return result

        try {
            val uri = Uri.parse("content://sms/conversations")
            val projection = arrayOf("thread_id", "address", "body", "date", "read")
            val cursor = contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("thread_id", "address", "body", "date", "read", "type"),
                null, null,
                "date DESC"
            ) ?: return result

            val threadSeen = mutableSetOf<Long>()
            cursor.use {
                while (it.moveToNext()) {
                    val threadId = it.getLong(0)
                    if (threadId in threadSeen) continue
                    threadSeen.add(threadId)

                    val address = it.getString(1) ?: continue
                    val body = it.getString(2) ?: ""
                    val date = it.getLong(3)
                    val read = it.getInt(4)

                    val contactName = resolveContactName(address)

                    // Count unread
                    val unreadCursor = contentResolver.query(
                        Uri.parse("content://sms"),
                        arrayOf("_id"),
                        "thread_id = ? AND read = 0",
                        arrayOf(threadId.toString()),
                        null
                    )
                    val unreadCount = unreadCursor?.count ?: 0
                    unreadCursor?.close()

                    result.add(Conversation(
                        threadId = threadId,
                        address = address,
                        contactName = contactName,
                        snippet = body,
                        date = date,
                        unreadCount = unreadCount
                    ))
                }
            }
        } catch (e: Exception) {}
        return result
    }

    private fun resolveContactName(address: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            val cursor = contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { null }
    }

    private fun renderConversations(conversations: List<Conversation>) {
        while (listContainer.childCount > 2) {
            listContainer.removeViewAt(2)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            listContainer.addView(buildPermissionNote())
            return
        }

        if (conversations.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Нет сообщений"
                textSize = 15f
                setTextColor(TEXT_SEC)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            })
            return
        }

        conversations.forEach { conv ->
            listContainer.addView(buildConversationRow(conv))
            listContainer.addView(buildSeparator())
        }
    }

    private fun filterConversations(query: String) {
        val filtered = if (query.isEmpty()) allConversations
        else allConversations.filter {
            (it.contactName ?: it.address).contains(query, true) ||
                it.snippet.contains(query, true)
        }
        renderConversations(filtered)
    }

    private fun buildConversationRow(conv: Conversation): LinearLayout {
        val displayName = conv.contactName ?: conv.address
        val isUnread = conv.unreadCount > 0

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            setOnClickListener {
                val intent = Intent(this@MessagesActivity, ConversationActivity::class.java).apply {
                    putExtra("thread_id", conv.threadId)
                    putExtra("address", conv.address)
                    putExtra("contact_name", displayName)
                }
                startActivity(intent)
            }

            // Avatar
            addView(buildAvatarCircle(displayName, dp(50)))

            // Middle: name + snippet
            val textCol = LinearLayout(this@MessagesActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(12) }
            }

            textCol.addView(TextView(this@MessagesActivity).apply {
                text = displayName
                textSize = 16f
                typeface = if (isUnread) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(TEXT)
            })

            textCol.addView(TextView(this@MessagesActivity).apply {
                text = conv.snippet
                textSize = 14f
                setTextColor(if (isUnread) TEXT else TEXT_SEC)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            addView(textCol)

            // Right: time + unread badge
            val rightCol = LinearLayout(this@MessagesActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(4), 0)
            }

            rightCol.addView(TextView(this@MessagesActivity).apply {
                text = formatTime(conv.date)
                textSize = 12f
                setTextColor(if (isUnread) BLUE else TEXT_SEC)
            })

            if (conv.unreadCount > 0) {
                val badge = TextView(this@MessagesActivity).apply {
                    text = conv.unreadCount.toString()
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(RED)
                    }
                    val size = dp(20)
                    layoutParams = LinearLayout.LayoutParams(size, size).also {
                        it.topMargin = dp(4)
                        it.gravity = Gravity.END
                    }
                }
                rightCol.addView(badge)
            }

            addView(rightCol)
        }
    }

    private fun buildAvatarCircle(name: String, sizePx: Int): View {
        return object : View(this) {
            override fun onDraw(canvas: Canvas) {
                val bgColor = AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.size]
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
                val cx = width / 2f; val cy = height / 2f
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

    private fun getInitials(name: String): String {
        val words = name.trim().split("\\s+".toRegex())
        return when {
            words.size >= 2 -> "${words[0].firstOrNull() ?: ""}${words[1].firstOrNull() ?: ""}".uppercase()
            words.isNotEmpty() -> words[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun formatTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "сейчас"
            diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)} мин"
            diff < TimeUnit.DAYS.toMillis(1) -> SimpleDateFormat("HH:mm", Locale("ru")).format(Date(timestamp))
            diff < TimeUnit.DAYS.toMillis(7) -> SimpleDateFormat("EEE", Locale("ru")).format(Date(timestamp))
            else -> SimpleDateFormat("dd.MM.yy", Locale("ru")).format(Date(timestamp))
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

    private fun buildPermissionNote(): TextView {
        return TextView(this).apply {
            text = "Требуется разрешение на чтение SMS"
            textSize = 15f
            setTextColor(TEXT_SEC)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(40), dp(20), dp(40))
        }
    }

    private fun showComposeDialog() {
        val dialog = android.app.AlertDialog.Builder(this)
        val input = EditText(this).apply {
            hint = "Введите номер или имя"
            setHintTextColor(TEXT_SEC)
            setTextColor(TEXT)
            inputType = InputType.TYPE_CLASS_PHONE
        }
        dialog.setTitle("Новое сообщение")
        dialog.setView(input)
        dialog.setPositiveButton("Начать") { _, _ ->
            val number = input.text.toString().trim()
            if (number.isNotEmpty()) {
                startActivity(Intent(this, ConversationActivity::class.java).apply {
                    putExtra("address", number)
                    putExtra("contact_name", number)
                    putExtra("thread_id", -1L)
                })
            }
        }
        dialog.setNegativeButton("Отмена", null)
        dialog.show()
    }

    private fun registerSmsObserver() {
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                loadConversations()
            }
        }
        contentResolver.registerContentObserver(
            Uri.parse("content://sms"), true, smsObserver!!
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) {
            loadConversations()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

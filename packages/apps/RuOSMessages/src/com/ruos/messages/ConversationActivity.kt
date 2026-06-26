package com.ruos.messages

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.text.*
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ConversationActivity : Activity() {

    companion object {
        val BG = Color.parseColor("#000000")
        val SURFACE = Color.parseColor("#1C1C1E")
        val SURFACE2 = Color.parseColor("#2C2C2E")
        val RED = Color.parseColor("#D94F3D")
        val BLUE = Color.parseColor("#0A84FF")
        val TEXT = Color.WHITE
        val TEXT_SEC = Color.parseColor("#8E8E93")
        val SEP = Color.parseColor("#38383A")
        val MSG_BUBBLE_MINE = Color.parseColor("#0A84FF")
        val MSG_BUBBLE_THEIRS = Color.parseColor("#2C2C2E")

        val AVATAR_COLORS = listOf(
            Color.parseColor("#5E5CE6"),
            Color.parseColor("#30D158"),
            Color.parseColor("#0A84FF"),
            Color.parseColor("#FF9F0A"),
            Color.parseColor("#D94F3D"),
            Color.parseColor("#BF5AF2")
        )

        // Message types from SMS content provider
        const val SMS_TYPE_INBOX = 1
        const val SMS_TYPE_SENT = 2
    }

    data class Message(
        val id: Long,
        val body: String,
        val date: Long,
        val type: Int // 1=inbox, 2=sent
    )

    private var threadId = -1L
    private var address = ""
    private var contactName = ""

    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputEdit: EditText
    private lateinit var sendBtn: ImageView
    private var smsObserver: ContentObserver? = null
    private val messages = mutableListOf<Message>()
    private var lastGroupSender = -1 // track grouping (-1=none, 0=mine, 1=theirs)
    private var lastMessageDate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        threadId = intent.getLongExtra("thread_id", -1L)
        address = intent.getStringExtra("address") ?: ""
        contactName = intent.getStringExtra("contact_name") ?: address

        buildUI()
        loadMessages()
        registerSmsObserver()
    }

    private fun buildUI() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        setContentView(rootLayout)

        // Top bar
        val topBar = buildTopBar()
        rootLayout.addView(topBar)

        // Messages scroll area (flex to fill available space)
        scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        messagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        scrollView.addView(messagesContainer)
        rootLayout.addView(scrollView)

        // Bottom input bar
        val inputBar = buildInputBar()
        rootLayout.addView(inputBar)
    }

    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(8), dp(52), dp(8), dp(8))

            // Back button
            addView(TextView(this@ConversationActivity).apply {
                text = "‹"
                textSize = 28f
                setTextColor(BLUE)
                setPadding(dp(8), 0, dp(4), 0)
                setOnClickListener { finish() }
            })

            // Avatar
            addView(buildAvatarCircle(contactName, dp(36)))

            // Name column
            val nameCol = LinearLayout(this@ConversationActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(8) }
            }

            nameCol.addView(TextView(this@ConversationActivity).apply {
                text = contactName
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT)
                gravity = Gravity.CENTER_HORIZONTAL
            })

            nameCol.addView(TextView(this@ConversationActivity).apply {
                text = address
                textSize = 11f
                setTextColor(TEXT_SEC)
                gravity = Gravity.CENTER_HORIZONTAL
            })

            addView(nameCol)

            // Info button
            addView(TextView(this@ConversationActivity).apply {
                text = "ⓘ"
                textSize = 22f
                setTextColor(BLUE)
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    Toast.makeText(this@ConversationActivity, "Информация о контакте", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun buildInputBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(8), dp(8), dp(8), dp(24))

            // Add media button
            addView(TextView(this@ConversationActivity).apply {
                text = "+"
                textSize = 22f
                setTextColor(BLUE)
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(4), 0)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).also {
                    it.gravity = Gravity.BOTTOM
                }
            })

            // Message input
            inputEdit = EditText(this@ConversationActivity).apply {
                hint = "Сообщение"
                setHintTextColor(TEXT_SEC)
                setTextColor(TEXT)
                textSize = 16f
                maxLines = 4
                background = GradientDrawable().apply {
                    setColor(SURFACE2)
                    cornerRadius = dp(18).toFloat()
                }
                setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.gravity = Gravity.BOTTOM }
            }
            addView(inputEdit)

            // Send button
            sendBtn = ImageView(this@ConversationActivity).apply {
                setImageDrawable(micDrawable(BLUE))
                setPadding(dp(4), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(40)).also {
                    it.gravity = Gravity.BOTTOM
                }
                setOnClickListener { sendMessage() }
            }
            addView(sendBtn)

            // Update send button icon based on text
            inputEdit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!s.isNullOrEmpty()) {
                        sendBtn.setImageDrawable(null)
                        sendBtn.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(BLUE)
                        }
                        sendBtn.setImageResource(android.R.drawable.ic_menu_send)
                        sendBtn.setColorFilter(Color.WHITE)
                    } else {
                        sendBtn.background = null
                        sendBtn.clearColorFilter()
                        sendBtn.setImageDrawable(micDrawable(BLUE))
                    }
                }
            })
        }
    }

    private fun loadMessages() {
        if (checkSelfPermission(Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return

        Thread {
            val loaded = queryMessages()
            runOnUiThread {
                messages.clear()
                messages.addAll(loaded)
                renderMessages()
                scrollToBottom()
            }
        }.start()
    }

    private fun queryMessages(): List<Message> {
        val result = mutableListOf<Message>()
        try {
            val selection = if (threadId > 0) "thread_id = ?" else "address = ?"
            val selectionArgs = if (threadId > 0) arrayOf(threadId.toString()) else arrayOf(address)

            val cursor = contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("_id", "body", "date", "type"),
                selection,
                selectionArgs,
                "date ASC"
            ) ?: return result

            cursor.use {
                while (it.moveToNext()) {
                    result.add(Message(
                        id = it.getLong(0),
                        body = it.getString(1) ?: "",
                        date = it.getLong(2),
                        type = it.getInt(3)
                    ))
                }
            }
        } catch (e: Exception) {}
        return result
    }

    private fun renderMessages() {
        messagesContainer.removeAllViews()
        lastGroupSender = -1
        lastMessageDate = 0L

        messages.forEach { msg ->
            val isMine = msg.type == SMS_TYPE_SENT

            // Timestamp divider if >1hr apart
            if (lastMessageDate > 0 &&
                msg.date - lastMessageDate > TimeUnit.HOURS.toMillis(1)) {
                messagesContainer.addView(buildTimestamp(msg.date))
                lastGroupSender = -1 // reset grouping after timestamp
            }
            lastMessageDate = msg.date

            val currentSender = if (isMine) 0 else 1
            val isFirstInGroup = currentSender != lastGroupSender
            lastGroupSender = currentSender

            messagesContainer.addView(buildMessageBubble(msg, isMine, isFirstInGroup))
        }
    }

    private fun buildTimestamp(date: Long): TextView {
        return TextView(this).apply {
            text = formatMessageDate(date)
            textSize = 12f
            setTextColor(TEXT_SEC)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun buildMessageBubble(msg: Message, isMine: Boolean, isFirstInGroup: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isMine) Gravity.END else Gravity.START
            setPadding(0, dp(1), 0, dp(1))
        }

        if (!isMine && isFirstInGroup) {
            // Avatar for first message in a group from them
            val avatar = buildAvatarCircle(contactName, dp(28))
            avatar.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).also {
                it.marginEnd = dp(6)
                it.gravity = Gravity.BOTTOM
            }
            row.addView(avatar)
        } else if (!isMine) {
            // Spacer to align with avatar
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), 1)
            })
        }

        // Bubble
        val bubbleColor = if (isMine) MSG_BUBBLE_MINE else MSG_BUBBLE_THEIRS
        val bubble = buildBubble(msg.body, bubbleColor, isMine)

        val bubbleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            maxWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        }
        bubble.layoutParams = bubbleParams
        row.addView(bubble)

        return row
    }

    private fun buildBubble(text: String, bgColor: Int, isMine: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(TEXT)
            setPadding(dp(12), dp(8), dp(12), dp(8))

            // iOS-style bubble: all corners 18dp except tail corner = 4dp
            // My bubble: bottom-right = 4dp (tail bottom right)
            // Their bubble: bottom-left = 4dp (tail bottom left)
            val r = dp(18).toFloat()
            val tail = dp(4).toFloat()

            background = GradientDrawable().apply {
                setColor(bgColor)
                if (isMine) {
                    // TL, TR, BR, BL — setCornerRadii takes float[8] (x,y pairs per corner)
                    // Order: top-left, top-right, bottom-right, bottom-left
                    cornerRadii = floatArrayOf(r, r, r, r, tail, tail, r, r)
                } else {
                    cornerRadii = floatArrayOf(r, r, r, r, r, r, tail, tail)
                }
            }
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

    private fun sendMessage() {
        val text = inputEdit.text.toString().trim()
        if (text.isEmpty()) return
        if (address.isEmpty()) {
            Toast.makeText(this, "Неизвестный получатель", Toast.LENGTH_SHORT).show()
            return
        }

        if (checkSelfPermission(Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 301)
            return
        }

        // Optimistically add message to UI
        val optimisticMsg = Message(
            id = -1L,
            body = text,
            date = System.currentTimeMillis(),
            type = SMS_TYPE_SENT
        )
        messages.add(optimisticMsg)
        renderMessages()
        scrollToBottom()
        inputEdit.setText("")

        // Actually send
        try {
            @Suppress("DEPRECATION")
            SmsManager.getDefault().sendTextMessage(address, null, text, null, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка отправки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun formatMessageDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when {
            now.get(Calendar.DATE) == msgCal.get(Calendar.DATE) ->
                SimpleDateFormat("HH:mm", Locale("ru")).format(Date(timestamp))
            now.get(Calendar.WEEK_OF_YEAR) == msgCal.get(Calendar.WEEK_OF_YEAR) ->
                SimpleDateFormat("EEE HH:mm", Locale("ru")).format(Date(timestamp))
            else ->
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(timestamp))
        }
    }

    private fun registerSmsObserver() {
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                loadMessages()
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
        // Mark messages as read
        if (threadId > 0) {
            try {
                val values = ContentValues().apply { put("read", 1) }
                contentResolver.update(
                    Uri.parse("content://sms"),
                    values,
                    "thread_id = ? AND read = 0",
                    arrayOf(threadId.toString())
                )
            } catch (e: Exception) {}
        }
    }

    private fun micDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val b = bounds; val cx = b.exactCenterX(); val cy = b.exactCenterY()
            val w = b.width().toFloat(); val h = b.height().toFloat()
            // Mic body (rounded rect)
            p.style = android.graphics.Paint.Style.FILL
            canvas.drawRoundRect(android.graphics.RectF(cx-w*0.2f, h*0.1f, cx+w*0.2f, h*0.6f), w*0.2f, w*0.2f, p)
            // Mic stand arc
            p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = w*0.08f
            canvas.drawArc(android.graphics.RectF(cx-w*0.3f, h*0.35f, cx+w*0.3f, h*0.75f), 0f, 180f, false, p)
            canvas.drawLine(cx, h*0.75f, cx, h*0.9f, p)
            canvas.drawLine(cx-w*0.2f, h*0.9f, cx+w*0.2f, h*0.9f, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

package com.ruos.phone

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.CallLog
import android.provider.ContactsContract
import android.text.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PhoneActivity : Activity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 101
        private val PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE
        )
        // Colors
        val BG = Color.parseColor("#000000")
        val SURFACE = Color.parseColor("#1C1C1E")
        val SURFACE2 = Color.parseColor("#2C2C2E")
        val RED = Color.parseColor("#D94F3D")
        val BLUE = Color.parseColor("#0A84FF")
        val GREEN = Color.parseColor("#30D158")
        val TEXT = Color.WHITE
        val TEXT_SEC = Color.parseColor("#8E8E93")
        val SEP = Color.parseColor("#38383A")
    }

    private lateinit var rootLayout: FrameLayout
    private lateinit var contentFrame: FrameLayout
    private var currentTab = 0
    private val tabViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(BG)
        setContentView(rootLayout)

        contentFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).also { it.bottomMargin = dp(83) }
        }
        rootLayout.addView(contentFrame)

        buildTabBar()
        selectTab(3) // Default: Keypad

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data?.scheme == "tel") {
            selectTab(3)
            val number = data.schemeSpecificPart
            (contentFrame.getChildAt(0) as? DialpadView)?.setNumber(number)
        }
    }

    private fun buildTabBar() {
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(SURFACE)
            gravity = Gravity.BOTTOM
        }
        val tabBarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(83)
        ).apply { gravity = Gravity.BOTTOM }
        rootLayout.addView(tabBar, tabBarParams)

        // Separator line
        val sep = View(this).apply {
            setBackgroundColor(SEP)
        }
        val sepParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { gravity = Gravity.BOTTOM; bottomMargin = dp(83) }
        rootLayout.addView(sep, sepParams)

        val tabs = listOf(
            Pair("★", "Избранное"),
            Pair("🕐", "Недавние"),
            Pair("👤", "Контакты"),
            Pair("#", "Клавиатура")
        )

        tabs.forEachIndexed { index, (icon, label) ->
            val tabView = buildTabItem(icon, label, index)
            tabBar.addView(tabView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun buildTabItem(icon: String, label: String, index: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            tag = index

            val iconView = TextView(this@PhoneActivity).apply {
                text = icon
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(if (index == 3) BLUE else TEXT_SEC)
                tag = "icon_$index"
            }
            addView(iconView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))

            val labelView = TextView(this@PhoneActivity).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(if (index == 3) BLUE else TEXT_SEC)
                tag = "label_$index"
            }
            addView(labelView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)))

            setOnClickListener { selectTab(index) }
        }
    }

    fun selectTab(index: Int) {
        currentTab = index
        contentFrame.removeAllViews()

        val view = when (index) {
            0 -> buildFavouritesView()
            1 -> buildRecentsView()
            2 -> buildContactsView()
            3 -> DialpadView(this)
            else -> buildFavouritesView()
        }
        contentFrame.addView(view)

        // Update tab colors
        val tabBar = (rootLayout.getChildAt(1) as? LinearLayout) ?: return
        for (i in 0 until tabBar.childCount) {
            val tab = tabBar.getChildAt(i) as? LinearLayout ?: continue
            val iconView = tab.findViewWithTag<TextView>("icon_$i")
            val labelView = tab.findViewWithTag<TextView>("label_$i")
            val color = if (i == index) BLUE else TEXT_SEC
            iconView?.setTextColor(color)
            labelView?.setTextColor(color)
        }
    }

    // ==============================
    // Tab 1: Favourites
    // ==============================
    private fun buildFavouritesView(): ScrollView {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(56), 0, dp(20))
        }
        scroll.addView(container)

        // Title
        container.addView(TextView(this).apply {
            text = "Избранное"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
            setPadding(dp(20), dp(10), dp(20), dp(16))
        })

        // Load favourites from SharedPreferences
        val prefs = getSharedPreferences("favourites", Context.MODE_PRIVATE)
        val favJson = prefs.getString("list", "[]")
        val names = parseFavourites(favJson ?: "[]")

        if (names.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Нет избранных контактов.\nНажмите + чтобы добавить."
                textSize = 15f
                setTextColor(TEXT_SEC)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            })
        } else {
            names.forEach { (name, phone) ->
                container.addView(buildContactRow(name, phone))
                container.addView(buildSeparator())
            }
        }

        return scroll
    }

    private fun parseFavourites(json: String): List<Pair<String, String>> {
        // Simple parser for stored favourites
        return emptyList()
    }

    // ==============================
    // Tab 2: Recents
    // ==============================
    private fun buildRecentsView(): ScrollView {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(56), 0, dp(20))
        }
        scroll.addView(container)

        container.addView(TextView(this).apply {
            text = "Недавние"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
            setPadding(dp(20), dp(10), dp(20), dp(16))
        })

        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            requestPermissionsIfNeeded()
            container.addView(buildPermissionPlaceholder("Требуется разрешение на чтение журнала звонков"))
            return scroll
        }

        val calls = queryCallLog()
        if (calls.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "История звонков пуста"
                textSize = 15f
                setTextColor(TEXT_SEC)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            })
        } else {
            calls.forEach { call ->
                container.addView(buildCallRow(call))
                container.addView(buildSeparator())
            }
        }

        return scroll
    }

    data class CallEntry(
        val number: String,
        val name: String?,
        val type: Int,
        val date: Long,
        val duration: Long
    )

    private fun queryCallLog(): List<CallEntry> {
        val result = mutableListOf<CallEntry>()
        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null, null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return result

            cursor.use {
                var count = 0
                while (it.moveToNext() && count < 50) {
                    result.add(CallEntry(
                        number = it.getString(0) ?: "",
                        name = it.getString(1),
                        type = it.getInt(2),
                        date = it.getLong(3),
                        duration = it.getLong(4)
                    ))
                    count++
                }
            }
        } catch (e: Exception) {
            // Permission denied or provider unavailable
        }
        return result
    }

    private fun buildCallRow(call: CallEntry): LinearLayout {
        val isMissed = call.type == CallLog.Calls.MISSED_TYPE
        val nameOrNumber = call.name?.takeIf { it.isNotEmpty() } ?: call.number

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(12), dp(12))
            setOnClickListener { dialNumber(call.number) }
        }

        // Direction icon
        val directionIcon = TextView(this).apply {
            text = when (call.type) {
                CallLog.Calls.OUTGOING_TYPE -> "↗"
                CallLog.Calls.INCOMING_TYPE -> "↙"
                else -> "✗"
            }
            textSize = 16f
            setTextColor(if (isMissed) RED else TEXT_SEC)
            setPadding(0, 0, dp(12), 0)
        }
        row.addView(directionIcon)

        // Name/number + time
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        infoCol.addView(TextView(this).apply {
            text = nameOrNumber
            textSize = 16f
            setTextColor(if (isMissed) RED else TEXT)
        })

        infoCol.addView(TextView(this).apply {
            text = formatTimeAgo(call.date)
            textSize = 13f
            setTextColor(TEXT_SEC)
        })

        row.addView(infoCol)

        // Info button
        val infoBtn = TextView(this).apply {
            text = "ⓘ"
            textSize = 20f
            setTextColor(BLUE)
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { showContactInfo(call.number) }
        }
        row.addView(infoBtn)

        return row
    }

    // ==============================
    // Tab 3: Contacts
    // ==============================
    fun buildContactsView(): ScrollView {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(56), 0, dp(20))
        }
        scroll.addView(container)

        container.addView(TextView(this).apply {
            text = "Контакты"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
            setPadding(dp(20), dp(10), dp(20), dp(16))
        })

        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            requestPermissionsIfNeeded()
            container.addView(buildPermissionPlaceholder("Требуется разрешение на чтение контактов"))
            return scroll
        }

        val contacts = queryContacts()
        var lastLetter = ""

        contacts.forEach { (name, phone) ->
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            if (initial != lastLetter) {
                lastLetter = initial
                container.addView(buildSectionHeader(initial))
            }
            container.addView(buildContactRow(name, phone))
            container.addView(buildSeparator())
        }

        return scroll
    }

    private fun queryContacts(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            ) ?: return result

            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(0) ?: continue
                    val phone = it.getString(1) ?: ""
                    result.add(Pair(name, phone))
                }
            }
        } catch (e: Exception) {}
        return result
    }

    // ==============================
    // Shared UI helpers
    // ==============================
    private fun buildContactRow(name: String, phone: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener { dialNumber(phone) }

            // Avatar
            val avatar = buildInitialsAvatar(name, dp(40))
            addView(avatar)

            // Name
            addView(TextView(this@PhoneActivity).apply {
                text = name
                textSize = 16f
                setTextColor(TEXT)
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(12) }
            })
        }
    }

    fun buildInitialsAvatar(name: String, sizePx: Int): View {
        val words = name.trim().split(" ")
        val initials = when {
            words.size >= 2 -> "${words[0].firstOrNull() ?: ""}${words[1].firstOrNull() ?: ""}"
            words.isNotEmpty() -> "${words[0].firstOrNull() ?: ""}"
            else -> "?"
        }.uppercase()

        val colors = listOf(
            Color.parseColor("#5E5CE6"),
            Color.parseColor("#30D158"),
            Color.parseColor("#0A84FF"),
            Color.parseColor("#FF9F0A"),
            Color.parseColor("#D94F3D"),
            Color.parseColor("#BF5AF2")
        )
        val bgColor = colors[Math.abs(name.hashCode()) % colors.size]

        val view = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = bgColor
                    style = Paint.Style.FILL
                }
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, cx, paint)

                paint.color = Color.WHITE
                paint.textSize = sizePx * 0.4f
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT_BOLD
                val metrics = paint.fontMetrics
                val textY = cy - (metrics.ascent + metrics.descent) / 2
                canvas.drawText(initials, cx, textY, paint)
            }
        }
        view.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        return view
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

    private fun buildSeparator(): View {
        return View(this).apply {
            setBackgroundColor(SEP)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.marginStart = dp(20) }
        }
    }

    private fun buildPermissionPlaceholder(msg: String): TextView {
        return TextView(this).apply {
            text = msg
            textSize = 15f
            setTextColor(TEXT_SEC)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(40), dp(20), dp(40))
        }
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "только что"
            diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)} мин. назад"
            diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)} ч. назад"
            diff < TimeUnit.DAYS.toMillis(7) -> "${diff / TimeUnit.DAYS.toMillis(1)} дн. назад"
            else -> SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(timestamp))
        }
    }

    private fun dialNumber(number: String) {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            requestPermissionsIfNeeded()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось совершить звонок", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showContactInfo(number: String) {
        Toast.makeText(this, "Контакт: $number", Toast.LENGTH_SHORT).show()
    }

    // --- Permissions ---
    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsIfNeeded() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            selectTab(currentTab) // Refresh current tab
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ==============================
// Dialpad View
// ==============================
class DialpadView(private val activity: PhoneActivity) : LinearLayout(activity) {

    private val BG = Color.parseColor("#000000")
    private val SURFACE2 = Color.parseColor("#2C2C2E")
    private val TEXT = Color.WHITE
    private val TEXT_SEC = Color.parseColor("#8E8E93")
    private val GREEN = Color.parseColor("#30D158")
    private val RED = Color.parseColor("#D94F3D")

    private lateinit var numberDisplay: TextView
    private val currentNumber = StringBuilder()

    init {
        orientation = VERTICAL
        setBackgroundColor(BG)
        setPadding(0, dp(56), 0, dp(16))
        buildDialpad()
    }

    fun setNumber(number: String) {
        currentNumber.clear()
        currentNumber.append(number)
        numberDisplay.text = formatNumber(number)
    }

    private fun buildDialpad() {
        // Number display
        numberDisplay = TextView(context).apply {
            textSize = 48f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(8))
            minHeight = dp(80)
            isSingleLine = true
        }
        addView(numberDisplay, LayoutParams(LayoutParams.MATCH_PARENT, dp(100)))

        // Delete button row
        val deleteRow = FrameLayout(context)
        val deleteBtn = TextView(context).apply {
            text = "⌫"
            textSize = 24f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(dp(60), dp(44)).also {
                it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                it.marginEnd = dp(32)
            }
            setOnClickListener { deleteLast() }
            setOnLongClickListener { clearNumber(); true }
        }
        deleteRow.addView(deleteBtn)
        addView(deleteRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))

        // Keypad grid
        val keys = listOf(
            listOf(Triple("1", "", ""), Triple("2", "АБВ", "ABC"), Triple("3", "ГДЕ", "DEF")),
            listOf(Triple("4", "ЖЗИ", "GHI"), Triple("5", "ЙКЛ", "JKL"), Triple("6", "МНО", "MNO")),
            listOf(Triple("7", "ПРСТ", "PQRS"), Triple("8", "УФХ", "TUV"), Triple("9", "ЦЧШЩ", "WXYZ")),
            listOf(Triple("*", "", ""), Triple("0", "+", ""), Triple("#", "", ""))
        )

        keys.forEach { row ->
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }

            row.forEach { (digit, _, subLabel) ->
                val keyView = buildKey(digit, subLabel)
                rowLayout.addView(keyView, LayoutParams(dp(100), dp(88)))
            }

            addView(rowLayout, LayoutParams(LayoutParams.MATCH_PARENT, dp(88)))
        }

        // Bottom row: call button
        val callRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }

        // Empty spacer left
        callRow.addView(View(context), LayoutParams(dp(100), dp(88)))

        // Call button (green circle)
        val callBtn = buildCallButton()
        callRow.addView(callBtn, LayoutParams(dp(100), dp(88)))

        // Empty spacer right
        callRow.addView(View(context), LayoutParams(dp(100), dp(88)))

        addView(callRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(88)))
    }

    private fun buildKey(digit: String, subLabel: String): FrameLayout {
        return FrameLayout(context).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2C2C2E"))
            }

            val inner = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(dp(80), dp(80)).also {
                    it.gravity = Gravity.CENTER
                }
                background = bg
                isClickable = true
                isFocusable = true

                val mainText = TextView(context).apply {
                    text = digit
                    textSize = 28f
                    setTextColor(TEXT)
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT
                }
                addView(mainText)

                if (subLabel.isNotEmpty()) {
                    val sub = TextView(context).apply {
                        text = subLabel
                        textSize = 10f
                        setTextColor(TEXT_SEC)
                        gravity = Gravity.CENTER
                        letterSpacing = 0.1f
                    }
                    addView(sub)
                }

                setOnClickListener {
                    val press = if (digit == "0" && currentNumber.isEmpty()) "+" else digit
                    currentNumber.append(press)
                    numberDisplay.text = formatNumber(currentNumber.toString())
                    hapticFeedback()
                }
                setOnLongClickListener {
                    if (digit == "0") {
                        if (currentNumber.isNotEmpty()) currentNumber[currentNumber.length - 1] = '+'
                        else currentNumber.append("+")
                        numberDisplay.text = formatNumber(currentNumber.toString())
                    }
                    true
                }
            }

            addView(inner)
        }
    }

    private fun buildCallButton(): FrameLayout {
        return FrameLayout(context).apply {
            val callBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(GREEN)
            }

            val callCircle = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(80), dp(80)).also {
                    it.gravity = Gravity.CENTER
                }
                background = callBg

                val icon = TextView(context).apply {
                    text = "📞"
                    textSize = 28f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                addView(icon)

                setOnClickListener {
                    val number = currentNumber.toString()
                    if (number.isNotEmpty()) {
                        dialNumber(number)
                    }
                }
            }

            addView(callCircle)
        }
    }

    private fun dialNumber(number: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка звонка", Toast.LENGTH_SHORT).show()
            }
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CALL_PHONE), 101)
        }
    }

    private fun deleteLast() {
        if (currentNumber.isNotEmpty()) {
            currentNumber.deleteCharAt(currentNumber.length - 1)
            numberDisplay.text = formatNumber(currentNumber.toString())
        }
    }

    private fun clearNumber() {
        currentNumber.clear()
        numberDisplay.text = ""
    }

    private fun formatNumber(number: String): String {
        // Basic Russian phone formatting
        if (number.startsWith("+7") && number.length == 12) {
            return "+7 (${number.substring(2, 5)}) ${number.substring(5, 8)}-${number.substring(8, 10)}-${number.substring(10)}"
        }
        return number
    }

    private fun hapticFeedback() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

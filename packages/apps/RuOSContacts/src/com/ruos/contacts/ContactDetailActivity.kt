package com.ruos.contacts

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException

class ContactDetailActivity : Activity() {

    companion object {
        val BG = Color.parseColor("#000000")
        val SURFACE = Color.parseColor("#1C1C1E")
        val SURFACE2 = Color.parseColor("#2C2C2E")
        val RED = Color.parseColor("#D94F3D")
        val BLUE = Color.parseColor("#0A84FF")
        val GREEN = Color.parseColor("#30D158")
        val TEXT = Color.WHITE
        val TEXT_SEC = Color.parseColor("#8E8E93")
        val SEP = Color.parseColor("#38383A")
        val VK_BLUE = Color.parseColor("#0077FF")

        val AVATAR_COLORS = listOf(
            Color.parseColor("#5E5CE6"),
            Color.parseColor("#30D158"),
            Color.parseColor("#0A84FF"),
            Color.parseColor("#FF9F0A"),
            Color.parseColor("#D94F3D"),
            Color.parseColor("#BF5AF2")
        )
    }

    data class PhoneEntry(val number: String, val label: String)
    data class EmailEntry(val address: String, val label: String)

    private var contactId = -1L
    private var contactName = ""
    private var photoUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        contactId = intent.getLongExtra("contact_id", -1L)
        contactName = intent.getStringExtra("contact_name") ?: "Контакт"
        photoUri = intent.getStringExtra("photo_uri")

        buildUI()
    }

    private fun buildUI() {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        scroll.addView(container)
        setContentView(scroll)

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(8))
        }

        val backBtn = TextView(this).apply {
            text = "‹ Назад"
            textSize = 17f
            setTextColor(BLUE)
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn)

        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val editBtn = TextView(this).apply {
            text = "Изменить"
            textSize = 17f
            setTextColor(BLUE)
            setOnClickListener { openEdit() }
        }
        topBar.addView(editBtn)
        container.addView(topBar)

        // Photo + Name section
        val headerSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val avatarSize = dp(100)
        val avatar = buildAvatarView(contactName, avatarSize)
        avatar.layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.bottomMargin = dp(16)
        }
        headerSection.addView(avatar)

        headerSection.addView(TextView(this).apply {
            text = contactName
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
            gravity = Gravity.CENTER
        })
        container.addView(headerSection)

        // Action chips row: Call, Message, Mail, VK
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(20))
        }

        val phones = loadPhones()
        val emails = loadEmails()
        val primaryPhone = phones.firstOrNull()?.number ?: ""
        val primaryEmail = emails.firstOrNull()?.address ?: ""
        val vkProfile = getVkProfile(contactName)

        listOf(
            Triple("📞", "Позвонить", Runnable { dialPhone(primaryPhone) }),
            Triple("💬", "Сообщение", Runnable { sendSms(primaryPhone) }),
            Triple("✉️", "Email", Runnable { sendEmail(primaryEmail) }),
            Triple("VK", "ВКонтакте", Runnable { openVk(vkProfile) })
        ).forEach { (icon, label, action) ->
            val chip = buildActionChip(icon, label, action)
            actionsRow.addView(chip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        container.addView(actionsRow)

        // Phone numbers section
        if (phones.isNotEmpty()) {
            container.addView(buildSectionCard("Телефон", phones.map { entry ->
                Pair(entry.label, entry.number)
            }))
        }

        // Email section
        if (emails.isNotEmpty()) {
            container.addView(buildSectionCard("Email", emails.map { entry ->
                Pair(entry.label, entry.address)
            }))
        }

        // Birthday
        val birthday = loadBirthday()
        if (birthday != null) {
            container.addView(buildSectionCard("День рождения", listOf(Pair("", birthday))))
        }

        // Notes
        val notes = loadNotes()
        if (notes != null && notes.isNotEmpty()) {
            container.addView(buildSectionCard("Заметки", listOf(Pair("", notes))))
        }

        // VK section
        if (vkProfile != null) {
            container.addView(buildSectionCard("Социальные сети", listOf(Pair("ВКонтакте", vkProfile))))
        }

        // Share button
        val shareBtn = buildPrimaryButton("Поделиться контактом") { shareContact() }
        container.addView(shareBtn)
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(32)) })
    }

    private fun buildAvatarView(name: String, sizePx: Int): View {
        if (photoUri != null) {
            return ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                    }
                }
                try {
                    val bmp = contentResolver.loadThumbnail(
                        Uri.parse(photoUri), android.util.Size(sizePx, sizePx), null
                    )
                    setImageBitmap(bmp)
                } catch (e: Exception) {
                    setImageDrawable(buildInitialsDrawable(name, sizePx))
                }
            }
        }

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
        }
    }

    private fun buildActionChip(icon: String, label: String, action: Runnable): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)

            val circle = FrameLayout(this@ContactDetailActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(SURFACE2)
                }
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                    it.bottomMargin = dp(6)
                }

                val iconView = TextView(this@ContactDetailActivity).apply {
                    text = icon
                    textSize = if (icon == "VK") 14f else 22f
                    gravity = Gravity.CENTER
                    setTextColor(if (icon == "VK") VK_BLUE else BLUE)
                    typeface = if (icon == "VK") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                addView(iconView)
                setOnClickListener { action.run() }
            }
            addView(circle)

            addView(TextView(this@ContactDetailActivity).apply {
                text = label
                textSize = 11f
                setTextColor(BLUE)
                gravity = Gravity.CENTER
            })
        }
    }

    private fun buildSectionCard(title: String, items: List<Pair<String, String>>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))

            addView(TextView(this@ContactDetailActivity).apply {
                text = title.uppercase()
                textSize = 12f
                setTextColor(TEXT_SEC)
                setPadding(dp(4), 0, 0, dp(4))
            })

            val card = LinearLayout(this@ContactDetailActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(SURFACE)
                    cornerRadius = dp(10).toFloat()
                }
                clipToOutline = true
            }

            items.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    card.addView(View(this@ContactDetailActivity).apply {
                        setBackgroundColor(SEP)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        ).also { it.marginStart = dp(16) }
                    })
                }

                val row = LinearLayout(this@ContactDetailActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                }

                if (label.isNotEmpty()) {
                    row.addView(TextView(this@ContactDetailActivity).apply {
                        text = label
                        textSize = 12f
                        setTextColor(TEXT_SEC)
                    })
                }

                row.addView(TextView(this@ContactDetailActivity).apply {
                    text = value
                    textSize = 16f
                    setTextColor(BLUE)
                    setOnClickListener { handleValueClick(title, value) }
                })

                card.addView(row)
            }

            addView(card)
        }
    }

    private fun buildPrimaryButton(text: String, action: () -> Unit): FrameLayout {
        return FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), 0)

            addView(TextView(this@ContactDetailActivity).apply {
                this.text = text
                textSize = 16f
                setTextColor(RED)
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(16))
                background = GradientDrawable().apply {
                    setColor(SURFACE)
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { action() }
            })
        }
    }

    // --- Data loading ---
    private fun loadPhones(): List<PhoneEntry> {
        val result = mutableListOf<PhoneEntry>()
        if (contactId < 0) return result
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            ) ?: return result

            cursor.use {
                while (it.moveToNext()) {
                    val number = it.getString(0) ?: continue
                    val type = it.getInt(1)
                    val customLabel = it.getString(2)
                    val label = when (type) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "мобильный"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "домашний"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "рабочий"
                        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel ?: "другой"
                        else -> "другой"
                    }
                    result.add(PhoneEntry(number, label))
                }
            }
        } catch (e: Exception) {}
        return result
    }

    private fun loadEmails(): List<EmailEntry> {
        val result = mutableListOf<EmailEntry>()
        if (contactId < 0) return result
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE,
                    ContactsContract.CommonDataKinds.Email.LABEL
                ),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            ) ?: return result

            cursor.use {
                while (it.moveToNext()) {
                    val address = it.getString(0) ?: continue
                    val type = it.getInt(1)
                    val customLabel = it.getString(2)
                    val label = when (type) {
                        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "домашний"
                        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "рабочий"
                        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> customLabel ?: "другой"
                        else -> "другой"
                    }
                    result.add(EmailEntry(address, label))
                }
            }
        } catch (e: Exception) {}
        return result
    }

    private fun loadBirthday(): String? {
        if (contactId < 0) return null
        try {
            val cursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null
            ) ?: return null

            cursor.use {
                if (it.moveToFirst()) return it.getString(0)
            }
        } catch (e: Exception) {}
        return null
    }

    private fun loadNotes(): String? {
        if (contactId < 0) return null
        try {
            val cursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Note.NOTE),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE),
                null
            ) ?: return null

            cursor.use {
                if (it.moveToFirst()) return it.getString(0)
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getVkProfile(name: String): String? {
        val prefs = getSharedPreferences("vk_contacts_map", MODE_PRIVATE)
        return prefs.getString(name, null)
    }

    // --- Actions ---
    private fun handleValueClick(section: String, value: String) {
        when (section) {
            "Телефон" -> dialPhone(value)
            "Email" -> sendEmail(value)
            "Социальные сети" -> openVk(value)
        }
    }

    private fun dialPhone(number: String) {
        if (number.isEmpty()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 201)
        }
    }

    private fun sendSms(number: String) {
        if (number.isEmpty()) return
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    }

    private fun sendEmail(address: String) {
        if (address.isEmpty()) return
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    }

    private fun openVk(profile: String?) {
        if (profile == null) {
            Toast.makeText(this, "ВКонтакте профиль не настроен", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vk.com/$profile")))
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть ВКонтакте", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openEdit() {
        if (contactId < 0) return
        val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        startActivity(Intent(Intent.ACTION_EDIT, contactUri))
    }

    private fun shareContact() {
        if (contactId < 0) return
        val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, contactUri)
        }
        startActivity(Intent.createChooser(shareIntent, "Поделиться контактом"))
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

package com.ruos.contacts

import android.app.Activity
import android.content.ContentProviderOperation
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.view.*
import android.widget.*

class AddContactActivity : Activity() {

    companion object {
        val BG = Color.parseColor("#000000")
        val SURFACE = Color.parseColor("#1C1C1E")
        val SURFACE2 = Color.parseColor("#2C2C2E")
        val BLUE = Color.parseColor("#0A84FF")
        val RED = Color.parseColor("#D94F3D")
        val TEXT = Color.WHITE
        val TEXT_SEC = Color.parseColor("#8E8E93")
        val SEP = Color.parseColor("#38383A")
    }

    private lateinit var firstNameEdit: EditText
    private lateinit var lastNameEdit: EditText
    private lateinit var phoneEdit: EditText
    private lateinit var emailEdit: EditText
    private lateinit var notesEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
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

        val cancelBtn = TextView(this).apply {
            text = "Отмена"
            textSize = 17f
            setTextColor(BLUE)
            setOnClickListener { finish() }
        }
        topBar.addView(cancelBtn)

        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val titleView = TextView(this).apply {
            text = "Новый контакт"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
        }
        topBar.addView(titleView)

        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val saveBtn = TextView(this).apply {
            text = "Готово"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(BLUE)
            setOnClickListener { saveContact() }
        }
        topBar.addView(saveBtn)
        container.addView(topBar)

        // Avatar placeholder
        val avatarContainer = FrameLayout(this).apply {
            setPadding(0, dp(16), 0, dp(24))
        }
        val avatarCircle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2C2C2E"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(80), dp(80)).also {
                it.gravity = Gravity.CENTER
            }
        }
        avatarCircle.addView(TextView(this).apply {
            text = "+"
            textSize = 28f
            setTextColor(BLUE)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        avatarContainer.addView(avatarCircle)
        container.addView(avatarContainer)

        // Name section
        container.addView(buildFieldSection("Имя и фамилия", listOf(
            "Имя".also { firstNameEdit = buildEditText(it) } to firstNameEdit,
            "Фамилия".also { lastNameEdit = buildEditText(it) } to lastNameEdit
        )))

        // Phone section
        container.addView(buildSingleFieldSection("Телефон",
            "мобильный".also { phoneEdit = buildEditText(it, android.text.InputType.TYPE_CLASS_PHONE) }
                to phoneEdit
        ))

        // Email section
        container.addView(buildSingleFieldSection("Email",
            "email".also { emailEdit = buildEditText(it, android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) }
                to emailEdit
        ))

        // Notes section
        container.addView(buildSingleFieldSection("Заметки",
            "Заметки".also { notesEdit = buildEditText(it) } to notesEdit
        ))

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(40)) })
    }

    private fun buildEditText(hint: String, inputType: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText {
        return EditText(this).apply {
            this.hint = hint
            setHintTextColor(TEXT_SEC)
            setTextColor(TEXT)
            textSize = 16f
            background = null
            this.inputType = inputType
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
    }

    private fun buildFieldSection(title: String, fields: List<Pair<String, EditText>>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), 0)

            addView(TextView(this@AddContactActivity).apply {
                text = title.uppercase()
                textSize = 12f
                setTextColor(TEXT_SEC)
                setPadding(dp(4), 0, 0, dp(6))
            })

            val card = LinearLayout(this@AddContactActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(SURFACE)
                    cornerRadius = dp(10).toFloat()
                }
            }

            fields.forEachIndexed { index, (_, editText) ->
                if (index > 0) {
                    card.addView(View(this@AddContactActivity).apply {
                        setBackgroundColor(SEP)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        ).also { it.marginStart = dp(16) }
                    })
                }
                card.addView(editText)
            }
            addView(card)
        }
    }

    private fun buildSingleFieldSection(title: String, field: Pair<String, EditText>): LinearLayout {
        return buildFieldSection(title, listOf(field))
    }

    private fun saveContact() {
        val firstName = firstNameEdit.text.toString().trim()
        val lastName = lastNameEdit.text.toString().trim()
        val phone = phoneEdit.text.toString().trim()
        val email = emailEdit.text.toString().trim()
        val notes = notesEdit.text.toString().trim()

        if (firstName.isEmpty() && lastName.isEmpty()) {
            Toast.makeText(this, "Введите имя контакта", Toast.LENGTH_SHORT).show()
            return
        }

        val ops = arrayListOf<ContentProviderOperation>()
        val rawContactInsertIndex = 0

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build()
        )

        // Name
        if (firstName.isNotEmpty() || lastName.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                .build()
            )
        }

        // Phone
        if (phone.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
            )
        }

        // Email
        if (email.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                .build()
            )
        }

        // Notes
        if (notes.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
                .build()
            )
        }

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(this, "Контакт сохранён", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

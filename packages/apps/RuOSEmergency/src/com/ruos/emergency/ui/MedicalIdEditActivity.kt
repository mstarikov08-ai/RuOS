package com.ruos.emergency.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.ruos.emergency.store.EmergencyContact
import com.ruos.emergency.store.EmergencyStore
import com.ruos.emergency.store.MedicalId

/** Edit the Medical ID + emergency contacts. */
class MedicalIdEditActivity : Activity() {

    private lateinit var store: EmergencyStore
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private lateinit var nameF: EditText
    private lateinit var dobF: EditText
    private lateinit var bloodF: EditText
    private lateinit var heightF: EditText
    private lateinit var weightF: EditText
    private lateinit var allergiesF: EditText
    private lateinit var conditionsF: EditText
    private lateinit var medsF: EditText
    private lateinit var notesF: EditText
    private lateinit var donorSwitch: Switch
    private lateinit var contactsBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = EmergencyStore(this)
        val m = store.medicalId()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply { text = "Медкарта"; setTextColor(Color.WHITE); textSize = 28f; typeface = Fonts.bold; setPadding(0, 0, 0, dp(4f)) })
        col.addView(note("Эти данные видны на экране блокировки в экстренной ситуации."))

        nameF = field("Имя", m.name); dobF = field("Дата рождения", m.dob)
        bloodF = field("Группа крови", m.bloodType); heightF = field("Рост", m.height); weightF = field("Вес", m.weight)
        allergiesF = field("Аллергии и реакции", m.allergies); conditionsF = field("Заболевания", m.conditions)
        medsF = field("Лекарства", m.medications); notesF = field("Примечания", m.notes)

        col.addView(label("ЛИЧНОЕ"))
        col.addView(card(nameF)); col.addView(card(dobF)); col.addView(card(bloodF)); col.addView(card(heightF)); col.addView(card(weightF))
        col.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MedicalIdEditActivity).apply { text = "Донор органов"; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            donorSwitch = Switch(this@MedicalIdEditActivity).apply { isChecked = m.organDonor }
            addView(donorSwitch)
        }))

        col.addView(label("МЕДИЦИНСКИЕ ДАННЫЕ"))
        col.addView(card(allergiesF)); col.addView(card(conditionsF)); col.addView(card(medsF)); col.addView(card(notesF))

        col.addView(label("ЭКСТРЕННЫЕ КОНТАКТЫ"))
        contactsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(contactsBox)
        store.contacts().forEach { addContactRow(it) }
        if (store.contacts().isEmpty()) addContactRow(null)
        col.addView(linkBtn("+ Добавить контакт") { addContactRow(null) })

        col.addView(Button(this).apply {
            text = "Сохранить"; setTextColor(Color.WHITE); typeface = Fonts.medium; isAllCaps = false
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFFFF3B30.toInt()) }
            setOnClickListener { save() }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52f)); lp.topMargin = dp(20f); layoutParams = lp
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun addContactRow(c: EmergencyContact?) {
        val name = field("Имя", c?.name ?: "")
        val phone = field("Телефон", c?.phone ?: "").apply { inputType = InputType.TYPE_CLASS_PHONE }
        val relation = field("Кто это (например, мама)", c?.relation ?: "")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            tag = arrayOf(name, phone, relation)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(8f); layoutParams = lp
        }
        box.addView(name); box.addView(phone); box.addView(relation)
        box.addView(TextView(this).apply {
            text = "Удалить контакт"; setTextColor(0xFFFF453A.toInt()); textSize = 13f; typeface = Fonts.medium
            setPadding(0, dp(6f), 0, dp(4f)); isClickable = true; setOnClickListener { contactsBox.removeView(box) }
        })
        contactsBox.addView(box)
    }

    private fun save() {
        store.saveMedicalId(MedicalId(
            nameF.text.toString().trim(), dobF.text.toString().trim(), bloodF.text.toString().trim(),
            heightF.text.toString().trim(), weightF.text.toString().trim(), allergiesF.text.toString().trim(),
            conditionsF.text.toString().trim(), medsF.text.toString().trim(), notesF.text.toString().trim(),
            donorSwitch.isChecked))
        val contacts = ArrayList<EmergencyContact>()
        for (i in 0 until contactsBox.childCount) {
            @Suppress("UNCHECKED_CAST")
            val fields = (contactsBox.getChildAt(i).tag as? Array<EditText>) ?: continue
            val name = fields[0].text.toString().trim(); val phone = fields[1].text.toString().trim()
            if (phone.isNotEmpty()) contacts.add(EmergencyContact(name.ifEmpty { phone }, phone, fields[2].text.toString().trim()))
        }
        store.saveContacts(contacts)
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show(); finish()
    }

    private fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setHintTextColor(0xFF8E8E93.toInt()); setTextColor(Color.WHITE)
        textSize = 16f; typeface = Fonts.regular; inputType = InputType.TYPE_CLASS_TEXT
    }
    private fun card(inner: View) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
        setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(6f); layoutParams = lp
        addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun linkBtn(t: String, onTap: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(0xFF0A84FF.toInt()); textSize = 15f; typeface = Fonts.medium; setPadding(dp(4f), dp(12f), 0, dp(4f))
        isClickable = true; setOnClickListener { onTap() }
    }
    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 12f; typeface = Fonts.medium; setPadding(dp(4f), dp(18f), 0, dp(6f))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular; setPadding(dp(2f), dp(4f), dp(2f), dp(4f))
    }
}

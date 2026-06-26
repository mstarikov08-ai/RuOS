package com.ruos.findmy.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import com.ruos.findmy.admin.FindMyAdminReceiver
import com.ruos.findmy.core.FindMySettings
import com.ruos.findmy.core.RemoteActions

/**
 * Find My RuOS control panel: enable protection, set the secret passphrase + trusted
 * number, and run the actions locally (play sound / lock / show on map). Explains the SMS
 * commands that trigger the same actions remotely. Dark, Golos, no emoji.
 */
class FindMyActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private lateinit var settings: FindMySettings
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private val perms = arrayOf(
        android.Manifest.permission.RECEIVE_SMS,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = FindMySettings(this)
        requestPermissions(perms, 1)
        setContentView(build())
    }

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK)
            setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = "Найти устройство"; setTextColor(Color.WHITE); textSize = 30f; typeface = Typeface.create(golos, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4f))
        })
        col.addView(note("Защита от потери: дистанционно по SMS — звук, блокировка, поиск на карте, стирание."))

        // master switch
        col.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@FindMyActivity).apply {
                text = "Защита включена"; setTextColor(Color.WHITE); textSize = 17f; typeface = golosM
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(this@FindMyActivity).apply {
                isChecked = settings.enabled
                setOnCheckedChangeListener { _, v -> settings.enabled = v }
            })
        }))

        // device admin status
        col.addView(label("ДИСТАНЦИОННЫЕ ПРАВА"))
        col.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@FindMyActivity).apply {
                text = if (isAdmin()) "Права администратора: активны" else "Права администратора: не выданы"
                setTextColor(if (isAdmin()) 0xFF34C759.toInt() else 0xFFFF9F0A.toInt()); textSize = 15f; typeface = golos
            })
            if (!isAdmin()) addView(linkButton("Выдать права (для блокировки и стирания)") { requestAdmin() })
        }))

        // secret passphrase + trusted number
        col.addView(label("СЕКРЕТНАЯ ФРАЗА И ДОВЕРЕННЫЙ НОМЕР"))
        val passF = field("Секретная фраза (минимум 4 символа)", settings.passphrase)
        val numF = field("Доверенный номер (необязательно)", settings.trustedNumber).apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        col.addView(card(passF)); col.addView(card(numF))
        col.addView(linkButton("Сохранить") {
            settings.passphrase = passF.text.toString()
            settings.trustedNumber = numF.text.toString()
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        })

        // local actions
        col.addView(label("ДЕЙСТВИЯ"))
        col.addView(actionButton("Воспроизвести звук") { RemoteActions.playSound(this) })
        col.addView(actionButton("Остановить звук") { RemoteActions.stopSound() })
        col.addView(actionButton("Заблокировать сейчас") {
            if (!RemoteActions.lock(this)) Toast.makeText(this, "Сначала выдайте права администратора", Toast.LENGTH_SHORT).show()
        })
        col.addView(actionButton("Показать на карте") { showOnMap() })

        // SMS help
        col.addView(label("КОМАНДЫ ПО SMS"))
        col.addView(card(TextView(this).apply {
            text = "Отправьте с доверенного телефона SMS:\n\n" +
                "«ФРАЗА ЗВУК» — звуковой сигнал\n" +
                "«ФРАЗА БЛОК» — заблокировать\n" +
                "«ФРАЗА ГДЕ» — прислать ссылку на карту\n" +
                "«ФРАЗА СТЕРЕТЬ» — сброс к заводским настройкам\n\n" +
                "Замените ФРАЗА на вашу секретную фразу."
            setTextColor(0xFFB0B0B5.toInt()); textSize = 14f; typeface = golos; setLineSpacing(dp(3f).toFloat(), 1f)
        }))

        return ScrollView(this).apply { addView(col) }
    }

    private fun showOnMap() {
        val loc = RemoteActions.lastLocation(this)
        if (loc == null) { Toast.makeText(this, "Местоположение пока недоступно", Toast.LENGTH_SHORT).show(); return }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(RuOS)")))
        }.onFailure {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RemoteActions.mapsLink(loc))))
        }
    }

    private fun isAdmin(): Boolean {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        return dpm?.isAdminActive(FindMyAdminReceiver.component(this)) == true
    }

    private fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, FindMyAdminReceiver.component(this@FindMyActivity))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Для дистанционной блокировки и стирания устройства функции «Найти» нужны права администратора.")
        }
        runCatching { startActivity(intent) }
    }

    override fun onResume() { super.onResume(); setContentView(build()) }

    // ── small builders ──────────────────────────────────────────────────────────

    private fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setHintTextColor(0xFF8E8E93.toInt()); setTextColor(Color.WHITE)
        textSize = 16f; typeface = golos; inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun card(inner: View) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
        setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6f); layoutParams = lp
        addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun actionButton(label: String, onTap: () -> Unit) = Button(this).apply {
        text = label; setTextColor(Color.WHITE); typeface = golosM; isAllCaps = false
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
        setOnClickListener { onTap() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50f)); lp.topMargin = dp(8f); layoutParams = lp
    }

    private fun linkButton(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(0xFF0A84FF.toInt()); textSize = 15f; typeface = golosM
        setPadding(0, dp(10f), 0, dp(2f)); isClickable = true; setOnClickListener { onTap() }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 12f; typeface = golosM
        setPadding(dp(4f), dp(18f), 0, dp(6f))
    }

    private fun note(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 14f; typeface = golos; setPadding(0, 0, 0, dp(10f))
    }
}

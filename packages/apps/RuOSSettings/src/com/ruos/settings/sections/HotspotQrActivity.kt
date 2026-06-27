package com.ruos.settings.sections

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.settings.util.QrCodes

/**
 * Share the personal hotspot via QR — a WIFI: code others scan to join. Reads the current
 * SoftAp SSID/passphrase (best effort on a platform build); both are editable and the QR
 * updates live. Includes a shortcut into the system hotspot settings.
 */
class HotspotQrActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private lateinit var qr: ImageView
    private lateinit var ssidF: EditText
    private lateinit var passF: EditText
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (ssid, pass) = softApConfig()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7")); setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title("Поделиться точкой доступа"))
        col.addView(note("Наведите камеру другого устройства, чтобы подключиться к вашей точке доступа."))

        qr = ImageView(this).apply { setBackgroundColor(Color.WHITE) }
        col.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(16))
            addView(qr, LinearLayout.LayoutParams(dp(240), dp(240)))
        })

        ssidF = field("Имя сети (SSID)", ssid)
        passF = field("Пароль", pass).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD }
        col.addView(label("ТОЧКА ДОСТУПА"))
        col.addView(card(ssidF)); col.addView(card(passF))

        col.addView(linkButton("Открыть настройки точки доступа") { openTethering() })

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        ssidF.addTextChangedListener(watcher); passF.addTextChangedListener(watcher)
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    private fun refresh() {
        val ssid = ssidF.text.toString()
        if (ssid.isBlank()) { qr.setImageDrawable(null); return }
        val payload = QrCodes.wifiPayload(ssid, passF.text.toString(), passF.text.toString().isNotEmpty())
        qr.setImageBitmap(QrCodes.render(payload, dp(240)))
    }

    private fun softApConfig(): Pair<String, String> {
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        return runCatching {
            val cfg = WifiManager::class.java.getMethod("getSoftApConfiguration").invoke(wm)
            val ssid = cfg.javaClass.getMethod("getSsid").invoke(cfg) as? String ?: ""
            val pass = cfg.javaClass.getMethod("getPassphrase").invoke(cfg) as? String ?: ""
            ssid to pass
        }.getOrDefault("" to "")
    }

    private fun openTethering() {
        val candidates = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
            Intent("android.settings.WIRELESS_SETTINGS"))
        for (i in candidates) if (runCatching { startActivity(i); true }.getOrDefault(false)) return
    }

    private fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setHintTextColor(Color.parseColor("#8E8E93")); setTextColor(Color.BLACK)
        textSize = 16f; typeface = golos; inputType = InputType.TYPE_CLASS_TEXT
    }
    private fun card(inner: View) = LinearLayout(this).apply {
        setPadding(dp(16), 0, dp(16), 0)
        addView(LinearLayout(this@HotspotQrActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(6); layoutParams = lp
            addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun linkButton(t: String, onTap: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#0A84FF")); textSize = 16f; typeface = golosM
        setPadding(dp(32), dp(16), dp(16), dp(8)); isClickable = true; setOnClickListener { onTap() }
    }
    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM; setPadding(dp(32), dp(14), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(2), dp(32), dp(8))
    }
}

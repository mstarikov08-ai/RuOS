package com.ruos.settings.sections

import android.app.Activity
import android.content.Context
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
 * Share Wi-Fi via QR: builds a standard WIFI: QR that iOS/Android cameras scan to join.
 * Pre-fills the current SSID and (best effort, on a platform build) the saved password;
 * both are editable and the QR updates live.
 */
class WifiQrActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private lateinit var qr: ImageView
    private lateinit var ssidF: EditText
    private lateinit var passF: EditText
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (ssid, pass) = currentNetwork()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7")); setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title("Поделиться Wi-Fi"))
        col.addView(note("Наведите камеру другого устройства на код, чтобы подключиться к сети."))

        qr = ImageView(this).apply { setBackgroundColor(Color.WHITE) }
        col.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(16))
            addView(qr, LinearLayout.LayoutParams(dp(240), dp(240)))
        })

        ssidF = field("Имя сети (SSID)", ssid)
        passF = field("Пароль", pass).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD }
        col.addView(label("СЕТЬ"))
        col.addView(card(ssidF)); col.addView(card(passF))

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

    private fun currentNetwork(): Pair<String, String> {
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        val ssid = runCatching {
            @Suppress("DEPRECATION") wm.connectionInfo.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" }
        }.getOrNull() ?: ""
        // best effort password read from privileged configs (platform builds only)
        val pass = runCatching {
            @Suppress("UNCHECKED_CAST")
            val list = WifiManager::class.java.getMethod("getPrivilegedConfiguredNetworks").invoke(wm) as? List<Any>
            list?.firstNotNullOfOrNull { cfg ->
                val cls = cfg.javaClass
                val s = cls.getField("SSID").get(cfg) as? String
                if (s?.trim('"') == ssid) (cls.getField("preSharedKey").get(cfg) as? String)?.trim('"') else null
            }
        }.getOrNull()?.takeIf { it != "*" } ?: ""
        return ssid to pass
    }

    private fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setHintTextColor(Color.parseColor("#8E8E93")); setTextColor(Color.BLACK)
        textSize = 16f; typeface = golos; inputType = InputType.TYPE_CLASS_TEXT
    }
    private fun card(inner: View) = LinearLayout(this).apply {
        setPadding(dp(16), 0, dp(16), 0)
        addView(LinearLayout(this@WifiQrActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(6); layoutParams = lp
            addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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

package com.ruos.settings.sections

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * NFC — оплата касанием: lists installed HCE *payment* apps (services that declare a
 * payment aid-group) and lets the user pick the default one used when the phone is tapped
 * on a terminal. The default is the real AOSP secure setting
 * nfc_payment_default_component, written by this platform-signed app.
 */
class NfcPaymentActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private val KEY = "nfc_payment_default_component"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(build())
    }

    override fun onResume() { super.onResume(); setContentView(build()) }

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7")); setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title("Оплата касанием"))

        if (NfcAdapter.getDefaultAdapter(this) == null) {
            col.addView(note("На этом устройстве нет модуля NFC."))
            return ScrollView(this).apply { addView(col) }
        }

        col.addView(note("Выберите приложение, которое открывается при касании телефоном " +
            "платёжного терминала."))

        val current = currentDefault()
        val apps = paymentApps()
        if (apps.isEmpty()) {
            col.addView(label("ПРИЛОЖЕНИЯ"))
            col.addView(card(listOf(infoRow("Платёжные приложения не найдены", ""))))
        } else {
            col.addView(label("ПРИЛОЖЕНИЕ ПО УМОЛЧАНИЮ"))
            val cardCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
            }
            apps.forEachIndexed { i, app ->
                cardCol.addView(appRow(app, app.component == current))
                if (i < apps.size - 1) cardCol.addView(divider())
            }
            col.addView(LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(cardCol,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) })
        }

        return ScrollView(this).apply { addView(col) }
    }

    private data class PayApp(val label: String, val icon: android.graphics.drawable.Drawable?, val component: ComponentName)

    private fun paymentApps(): List<PayApp> {
        val pm = packageManager
        val intent = Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE")
        val services = runCatching { pm.queryIntentServices(intent, PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        return services.filter { isPayment(pm, it) }.map { ri ->
            val si = ri.serviceInfo
            PayApp(si.loadLabel(pm).toString(), runCatching { si.loadIcon(pm) }.getOrNull(),
                ComponentName(si.packageName, si.name))
        }.distinctBy { it.component }
    }

    /** A service is a payment app if its HCE meta-data declares an aid-group category="payment". */
    private fun isPayment(pm: PackageManager, ri: ResolveInfo): Boolean {
        val parser = runCatching {
            ri.serviceInfo.loadXmlMetaData(pm, "android.nfc.cardemulation.host_apdu_service")
        }.getOrNull() ?: return false
        return runCatching {
            var event = parser.eventType
            val ns = "http://schemas.android.com/apk/res/android"
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "aid-group") {
                    if (parser.getAttributeValue(ns, "category") == "payment") return@runCatching true
                }
                event = parser.next()
            }
            false
        }.getOrDefault(false)
    }

    private fun currentDefault(): ComponentName? =
        runCatching { Settings.Secure.getString(contentResolver, KEY)?.let { ComponentName.unflattenFromString(it) } }.getOrNull()

    private fun setDefault(component: ComponentName) {
        runCatching { Settings.Secure.putString(contentResolver, KEY, component.flattenToString()) }
            .onSuccess { setContentView(build()) }
            .onFailure { Toast.makeText(this, "Нет прав (WRITE_SECURE_SETTINGS)", Toast.LENGTH_SHORT).show() }
    }

    private fun appRow(app: PayApp, selected: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true; setOnClickListener { setDefault(app.component) }
        addView(ImageView(context).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(dp(34), dp(34)).also { it.marginEnd = dp(12) })
        addView(TextView(context).apply { text = app.label; setTextColor(Color.BLACK); textSize = 17f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (selected) addView(checkMark(), LinearLayout.LayoutParams(dp(22), dp(22)))
    }

    /** Canvas-drawn blue checkmark (no emoji). */
    private fun checkMark(): View = object : View(this) {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
            strokeCap = android.graphics.Paint.Cap.ROUND; color = Color.parseColor("#0A84FF")
        }
        override fun onDraw(c: android.graphics.Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            c.drawLine(w * 0.2f, h * 0.55f, w * 0.42f, h * 0.75f, p)
            c.drawLine(w * 0.42f, h * 0.75f, w * 0.82f, h * 0.28f, p)
        }
    }

    private fun infoRow(k: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(TextView(context).apply { text = k; setTextColor(Color.BLACK); textSize = 16f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply { text = v; setTextColor(Color.GRAY); textSize = 16f; typeface = golos })
    }

    private fun card(rows: List<View>): View {
        val cardCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
        }
        rows.forEach { cardCol.addView(it) }
        return LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(cardCol,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }
    private fun divider() = View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
        .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { p -> p.marginStart = dp(16) } }
    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM; setPadding(dp(32), dp(14), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(6), dp(32), dp(8))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

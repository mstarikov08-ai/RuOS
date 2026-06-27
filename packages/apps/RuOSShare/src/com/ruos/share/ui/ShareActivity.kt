package com.ruos.share.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ruos.share.service.ReceiverService

/**
 * RuOS Share home: toggle receive mode (makes the device discoverable and runs the
 * receiver service) and pick files to send. Sharing from any app's share-sheet jumps
 * straight to [SendActivity]. Dark, Golos, no emoji.
 */
class ShareActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()
    private val accent = 0xFF0A84FF.toInt()
    private val PICK = 11

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(neededPerms(), 1)
        setContentView(build())
    }

    override fun onResume() { super.onResume(); setContentView(build()) }

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = "RuOS Share"; setTextColor(Color.WHITE); textSize = 30f; typeface = Fonts.bold; setPadding(0, 0, 0, dp(4f))
        })
        col.addView(note("Быстрый обмен файлами и фото между устройствами RuOS поблизости " +
            "по Wi-Fi Direct — без интернета и проводов."))

        col.addView(label("ПРИЁМ"))
        col.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(this@ShareActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@ShareActivity).apply { text = "Приём включён"; setTextColor(Color.WHITE); textSize = 17f; typeface = Fonts.medium })
                addView(TextView(this@ShareActivity).apply {
                    text = if (ReceiverService.running) "Видно как «${deviceName()}»" else "Сделать устройство видимым"
                    setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(this@ShareActivity).apply {
                isChecked = ReceiverService.running
                setOnCheckedChangeListener { _, on -> toggleReceive(on) }
            })
        }))

        col.addView(label("ОТПРАВКА"))
        col.addView(actionButton("Выбрать файлы для отправки") { pickFiles() })
        col.addView(note("Или нажмите «Поделиться» в любом приложении и выберите RuOS Share."))

        return ScrollView(this).apply { addView(col) }
    }

    private fun toggleReceive(on: Boolean) {
        val intent = Intent(this, ReceiverService::class.java)
            .setAction(if (on) ReceiverService.ACTION_START else ReceiverService.ACTION_STOP)
        if (on) startForegroundService(intent) else startService(intent)
    }

    private fun pickFiles() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); addCategory(Intent.CATEGORY_OPENABLE)
        }
        runCatching { startActivityForResult(Intent.createChooser(intent, "Выберите файлы"), PICK) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
            ?: data.data?.let { uris.add(it) }
        if (uris.isEmpty()) return
        startActivity(Intent(this, SendActivity::class.java).putParcelableArrayListExtra("uris", uris))
    }

    private fun deviceName(): String =
        runCatching { Settings.Global.getString(contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    private fun neededPerms(): Array<String> {
        val p = ArrayList<String>()
        p.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            p.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            p.add(android.Manifest.permission.POST_NOTIFICATIONS)
            p.add(android.Manifest.permission.READ_MEDIA_IMAGES)
        }
        return p.toTypedArray()
    }

    private fun card(inner: View) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
        setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(6f); layoutParams = lp
        addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun actionButton(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.medium; gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(accent) }
        setPadding(0, dp(14f), 0, dp(14f)); isClickable = true; setOnClickListener { onTap() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(8f); layoutParams = lp
    }
    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 12f; typeface = Fonts.medium; setPadding(dp(4f), dp(18f), 0, dp(6f))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular; setPadding(dp(2f), dp(6f), dp(2f), 0)
    }
}

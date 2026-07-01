package com.ruos.update

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * «Обновление ПО» — shows the installed RuOS version, checks the server for a newer build, and
 * (on a real build) downloads → SHA-256-verifies → applies it via the A/B update_engine.
 */
class UpdateActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private val main = Handler(Looper.getMainLooper())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private lateinit var status: TextView
    private lateinit var actionBtn: TextView
    private lateinit var progress: ProgressBar
    private var found: UpdateManifest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(60), 0, dp(40))
        }
        col.addView(title("Обновление ПО"))
        col.addView(note("Текущая версия RuOS ${UpdateChecker.currentVersion()} (${UpdateChecker.currentBuild()})"))

        status = TextView(this).apply {
            text = "Нажмите «Проверить», чтобы найти обновления."
            setTextColor(Color.parseColor("#3C3C43")); textSize = 15f; typeface = golos
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        col.addView(card(status))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE; max = 100
        }
        col.addView(progress)

        actionBtn = button("Проверить") { onCheck() }
        col.addView(card(actionBtn))

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun onCheck() {
        setBusy("Проверка обновлений…")
        Thread {
            val m = UpdateChecker.check()
            main.post {
                if (m == null) { setIdle("У вас установлена последняя версия RuOS."); actionBtn.text = "Проверить"; actionBtn.setOnClickListener { onCheck() } }
                else {
                    found = m
                    status.text = "Доступна версия ${m.version}\n${m.changelog}\n\n${m.size / 1024 / 1024} МБ"
                    actionBtn.text = "Загрузить и установить"
                    actionBtn.setOnClickListener { onInstall(m) }
                    actionBtn.isEnabled = true
                }
            }
        }.start()
    }

    private fun onInstall(m: UpdateManifest) {
        setBusy("Загрузка…"); progress.visibility = View.VISIBLE; progress.progress = 0
        val dest = File(cacheDir, "ruos_ota.zip")
        Thread {
            val ok = UpdateInstaller.download(m.url, dest) { done, total ->
                if (total > 0) main.post { progress.progress = (done * 100 / total).toInt() }
            }
            if (!ok) { main.post { setIdle("Не удалось загрузить обновление."); resetCheck() }; return@Thread }
            main.post { setBusy("Проверка целостности…") }
            if (!UpdateInstaller.verify(dest, m.sha256)) {
                dest.delete(); main.post { setIdle("Контрольная сумма не совпала — файл повреждён."); resetCheck() }; return@Thread
            }
            val spec = UpdateInstaller.payloadSpec(dest)
            if (spec == null) { main.post { setIdle("Это не A/B пакет обновления."); resetCheck() }; return@Thread }
            main.post { setBusy("Установка… не выключайте устройство") }
            UpdateInstaller.applyPayload(dest, spec,
                onStatus = { _, pct -> main.post { progress.progress = (pct * 100).toInt() } },
                onComplete = { err -> main.post {
                    if (err == 0) setIdle("Обновление готово. Перезагрузите устройство, чтобы завершить.")
                    else setIdle("Ошибка установки (код $err).")
                    resetCheck()
                } })
        }.start()
    }

    private fun resetCheck() { actionBtn.text = "Проверить"; actionBtn.isEnabled = true; actionBtn.setOnClickListener { onCheck() } }
    private fun setBusy(s: String) { status.text = s; actionBtn.isEnabled = false }
    private fun setIdle(s: String) { status.text = s; progress.visibility = View.GONE; actionBtn.isEnabled = true }

    // ── iOS-style helpers ─────────────────────────────────────────────────────
    private fun button(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.parseColor("#0A84FF")); textSize = 17f; typeface = golosM
        gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14)); isClickable = true
        setOnClickListener { onTap() }
    }
    private fun card(inner: View) = LinearLayout(this).apply {
        setPadding(dp(16), 0, dp(16), 0)
        addView(LinearLayout(this@UpdateActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10); layoutParams = lp
            addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD)
        setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos
        setPadding(dp(20), dp(2), dp(20), dp(10))
    }
}

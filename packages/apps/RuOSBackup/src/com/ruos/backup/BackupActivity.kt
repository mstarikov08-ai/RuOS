package com.ruos.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * "Резервная копия" — create an encrypted RuOS backup you can save to Files / Yandex.Disk,
 * and restore it later (after a wipe or on a new phone). The archive is password-encrypted
 * ([BackupCrypto], PBKDF2 + AES-256-GCM) so it's portable and openable only with the passphrase.
 */
class BackupActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val REQ_CREATE = 1
    private val REQ_OPEN = 2
    private var pendingPassword: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(60), 0, dp(40))
        }
        col.addView(title("Резервная копия"))
        col.addView(note("Зашифрованная копия ваших данных RuOS — пароли, напоминания, " +
            "фокусы, замены текста, виджеты, будильники, журнал. Сохраните её в Файлы или на " +
            "Яндекс.Диск. Копия защищена паролем и восстанавливается на любом устройстве."))

        col.addView(card(button("Создать резервную копию", "#0A84FF") { askPassword(create = true) }))
        col.addView(card(button("Восстановить из копии", "#0A84FF") { pickForRestore() }))

        setContentView(android.widget.ScrollView(this).apply { addView(col) })
    }

    private fun askPassword(create: Boolean) {
        val input = EditText(this).apply {
            hint = "Пароль копии"; inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        AlertDialog.Builder(this)
            .setTitle(if (create) "Пароль для копии" else "Пароль копии")
            .setMessage(if (create) "Запомните пароль — без него копию не восстановить." else "")
            .setView(input)
            .setPositiveButton(if (create) "Создать" else "Восстановить") { _, _ ->
                val pw = input.text.toString()
                if (pw.length < 4) { toast("Пароль слишком короткий (мин. 4 символа)"); return@setPositiveButton }
                pendingPassword = pw
                if (create) startCreate() else startRestore()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startCreate() {
        val name = "RuOS-backup-${android.text.format.DateFormat.format("yyyyMMdd-HHmm", System.currentTimeMillis())}.ruosbak"
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, name)
        }
        startActivityForResult(i, REQ_CREATE)
    }

    private fun pickForRestore() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        }
        startActivityForResult(i, REQ_OPEN)
    }

    private fun startRestore() { /* password captured; handled in onActivityResult of REQ_OPEN */ }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_CREATE -> {
                val pw = pendingPassword ?: return
                runCatching {
                    val bytes = BackupArchive.create(this, pw)
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    toast("Копия сохранена (${bytes.size / 1024} КБ)")
                }.onFailure { toast("Не удалось создать копию: ${it.message}") }
            }
            REQ_OPEN -> {
                val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                if (bytes == null || !BackupArchive.peek(bytes)) { toast("Это не файл резервной копии RuOS"); return }
                // ask password AFTER picking the file
                val input = EditText(this).apply {
                    hint = "Пароль копии"; inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                AlertDialog.Builder(this)
                    .setTitle("Восстановление")
                    .setView(input)
                    .setPositiveButton("Восстановить") { _, _ ->
                        runCatching {
                            val n = BackupArchive.restore(this, bytes, input.text.toString())
                            toast("Восстановлено разделов: $n")
                        }.onFailure { toast("Неверный пароль или повреждённый файл") }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ── tiny UI helpers (iOS-style Settings look) ─────────────────────────────
    private fun button(label: String, color: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.parseColor(color)); textSize = 17f; typeface = golosM
        gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14)); isClickable = true
        setOnClickListener { onTap() }
    }
    private fun card(inner: View) = LinearLayout(this).apply {
        setPadding(dp(16), 0, dp(16), 0)
        addView(LinearLayout(this@BackupActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
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

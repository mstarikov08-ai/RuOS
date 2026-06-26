package com.ruos.journal.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.journal.model.JournalEntry
import com.ruos.journal.model.JournalStore
import com.ruos.journal.prompts.PromptEngine
import com.ruos.journal.util.Fonts
import com.ruos.journal.util.Haptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal home: Face ID/Touch ID gate on open (real BiometricPrompt), a privacy
 * statement on first launch, a streak counter, AI prompt cards, an "В этот день год
 * назад" memory card, search, and the entry list. Compose FAB bottom-right.
 */
class JournalListActivity : Activity() {

    private lateinit var store: JournalStore
    private lateinit var listColumn: LinearLayout
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = JournalStore(this)
        setContentView(buildScaffold())
        if (!store.seenPrivacy) showPrivacy() else gateThenShow()
    }

    override fun onResume() { super.onResume(); if (store.seenPrivacy && unlocked) refresh() }

    private var unlocked = false

    // ── Privacy + lock ────────────────────────────────────────────────────────

    private fun showPrivacy() {
        AlertDialog.Builder(this)
            .setTitle("Конфиденциальность")
            .setMessage("Журнал хранится в зашифрованном виде только на этом устройстве. " +
                "Записи не выгружаются в облако и не используются ни для каких целей. " +
                "Для открытия требуется Face ID или Touch ID.")
            .setCancelable(false)
            .setPositiveButton("Понятно") { _, _ -> store.seenPrivacy = true; gateThenShow() }
            .show()
    }

    private fun gateThenShow() {
        if (!store.lockEnabled) { unlocked = true; refresh(); return }
        val bm = getSystemService(BiometricManager::class.java)
        val can = bm?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        if (!can) { unlocked = true; refresh(); return }
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Журнал").setSubtitle("Подтвердите личность")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult?) { unlocked = true; refresh() }
            override fun onAuthenticationError(c: Int, m: CharSequence?) { finish() }
        })
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────

    private fun buildScaffold(): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        listColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(48), dp(16), dp(96)) }
        scroll.addView(listColumn)
        root.addView(scroll)

        val fab = ImageView(this).apply {
            setImageDrawable(plusIcon())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#D94F3D")) }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            isClickable = true
            setOnClickListener { Haptics.confirm(it); startActivity(Intent(this@JournalListActivity, EntryEditorActivity::class.java)) }
        }
        root.addView(fab, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM or Gravity.END).also {
            it.bottomMargin = dp(24); it.rightMargin = dp(20)
        })
        return root
    }

    private fun refresh() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        listColumn.removeAllViews()

        // Header + streak.
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Журнал"; setTextColor(Color.WHITE); textSize = 34f; typeface = Fonts.bold
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val streak = store.streak()
        header.addView(TextView(this).apply {
            text = "Серия $streak"
            setTextColor(Color.parseColor("#D94F3D")); textSize = 16f; typeface = Fonts.medium
        })
        header.addView(TextView(this).apply {
            text = "  •••"; setTextColor(Color.parseColor("#8E8E93")); textSize = 18f
            isClickable = true
            setOnClickListener { startActivity(Intent(this@JournalListActivity, JournalSettingsActivity::class.java)) }
        })
        listColumn.addView(header)

        // Search.
        listColumn.addView(EditText(this).apply {
            hint = "Поиск"; setHintTextColor(Color.parseColor("#8E8E93")); setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 12f * d; setColor(Color.parseColor("#1C1C1E")) }
            setPadding(dp(14), dp(10), dp(14), dp(10)); setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); renderEntries() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })

        // AI prompt cards.
        PromptEngine(this).generate(store).forEach { p ->
            listColumn.addView(promptCard(p.text) {
                startActivity(Intent(this, EntryEditorActivity::class.java).putExtra("prompt", p.text))
            })
        }

        // Memory card.
        store.onThisDayLastYears().firstOrNull()?.let { mem ->
            listColumn.addView(memoryCard(mem))
        }

        renderEntries()
    }

    private fun renderEntries() {
        val d = resources.displayMetrics.density
        // Drop everything after the fixed header section by re-rendering only the entry list.
        // Simpler: keep an entries container.
        entriesContainer?.let { listColumn.removeView(it) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        entriesContainer = container
        listColumn.addView(container)

        val q = query.trim().lowercase()
        val entries = store.getEntries().filter {
            q.isEmpty() || it.text.lowercase().contains(q) || it.tags.any { t -> t.lowercase().contains(q) }
        }
        if (entries.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Пока нет записей"; setTextColor(Color.parseColor("#8E8E93")); textSize = 15f
                setPadding(0, (40 * d).toInt(), 0, 0); gravity = Gravity.CENTER
            })
        }
        entries.forEach { container.addView(entryCard(it)) }
    }
    private var entriesContainer: LinearLayout? = null

    // ── Cards ─────────────────────────────────────────────────────────────────

    private fun promptCard(text: String, onTap: () -> Unit): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 16f * d
                colors = intArrayOf(Color.parseColor("#3A2A4A"), Color.parseColor("#2A2A4A"))
                gradientType = GradientDrawable.LINEAR_GRADIENT
            }
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            isClickable = true; setOnClickListener { Haptics.select(it); onTap() }
            addView(TextView(context).apply { text = "Предложение"; setTextColor(Color.parseColor("#B0A8C8")); textSize = 12f; typeface = Fonts.medium })
            addView(TextView(context).apply { this.text = text; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular; setPadding(0,(4*d).toInt(),0,0) })
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (12 * d).toInt() }
        }
    }

    private fun memoryCard(e: JournalEntry): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = 16f * d; setColor(Color.parseColor("#1C1C2E")) }
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            isClickable = true
            setOnClickListener { startActivity(Intent(this@JournalListActivity, EntryEditorActivity::class.java).putExtra("id", e.id)) }
            addView(TextView(context).apply { text = "В этот день год назад"; setTextColor(Color.parseColor("#4F9DFF")); textSize = 12f; typeface = Fonts.medium })
            addView(TextView(context).apply { text = e.firstLine(); setTextColor(Color.WHITE); textSize = 16f; maxLines = 2; setPadding(0,(4*d).toInt(),0,0) })
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (12 * d).toInt() }
        }
    }

    private fun entryCard(e: JournalEntry): View {
        val d = resources.displayMetrics.density
        val dateFmt = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = 16f * d; setColor(Color.parseColor("#FFFFFF")) }
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            isClickable = true
            setOnClickListener { startActivity(Intent(this@JournalListActivity, EntryEditorActivity::class.java).putExtra("id", e.id)) }
            val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(context).apply {
                text = dateFmt.format(Date(e.timestamp)).replaceFirstChar { it.uppercase() }
                setTextColor(Color.parseColor("#8E8E93")); textSize = 12f; typeface = Fonts.medium
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            e.mood?.let { m ->
                head.addView(MoodFaceView(context).apply { setMood(m) }, LinearLayout.LayoutParams((22*d).toInt(), (22*d).toInt()))
            }
            addView(head)
            addView(TextView(context).apply {
                text = e.firstLine(); setTextColor(Color.parseColor("#111111")); textSize = 16f; typeface = Fonts.regular
                maxLines = 3; setPadding(0,(6*d).toInt(),0,0)
            })
            if (e.photoUris.isNotEmpty()) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,(8*d).toInt(),0,0) }
                e.photoUris.take(3).forEach { uri ->
                    row.addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                        runCatching { setImageURI(android.net.Uri.parse(uri)) }
                    }, LinearLayout.LayoutParams((64*d).toInt(), (64*d).toInt()).also { it.marginEnd = (6*d).toInt() })
                }
                addView(row)
            }
            e.location?.let { loc ->
                addView(TextView(context).apply { text = loc; setTextColor(Color.parseColor("#8E8E93")); textSize = 12f; setPadding(0,(6*d).toInt(),0,0) })
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (12 * d).toInt() }
        }
    }

    private fun plusIcon(): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds; val cx = b.exactCenterX(); val cy = b.exactCenterY(); val r = b.width() * 0.26f
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; strokeWidth = b.width() * 0.08f; strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawLine(cx - r, cy, cx + r, cy, p); canvas.drawLine(cx, cy - r, cx, cy + r, p)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(c: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

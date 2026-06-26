package com.ruos.journal.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.journal.model.JournalEntry
import com.ruos.journal.model.JournalStore
import com.ruos.journal.model.Mood
import com.ruos.journal.util.Fonts
import com.ruos.journal.util.Haptics
import java.io.File
import java.util.Locale

/** Distraction-free full-screen editor: Golos text, photos, mood, tags, voice memo,
 *  location. Pre-fills from an AI prompt; edits an existing entry by id. */
class EntryEditorActivity : Activity() {

    private lateinit var store: JournalStore
    private var editing: JournalEntry? = null
    private var id = 0L

    private lateinit var textField: EditText
    private val photoUris = ArrayList<String>()
    private val tags = ArrayList<String>()
    private var selectedMood: Mood? = null
    private var location: String? = null
    private var audioPath: String? = null

    private lateinit var photoRow: LinearLayout
    private lateinit var tagRow: LinearLayout
    private lateinit var moodRow: LinearLayout
    private lateinit var metaLine: TextView
    private var recorder: MediaRecorder? = null
    private var recording = false
    private lateinit var audioBtn: TextView

    private val PICK = 9100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = JournalStore(this)
        id = intent.getLongExtra("id", -1L)
        editing = if (id >= 0) store.get(id) else null
        if (editing == null) id = store.newId()
        editing?.let {
            photoUris.addAll(it.photoUris); tags.addAll(it.tags)
            selectedMood = it.mood; location = it.location; audioPath = it.audioPath
        }
        setContentView(build())
    }

    private fun build(): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }

        // Header
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(44), dp(16), dp(8)) }
        header.addView(TextView(this).apply {
            text = "Отмена"; setTextColor(Color.parseColor("#D94F3D")); textSize = 17f
            isClickable = true; setOnClickListener { finish() }
        })
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (editing != null) header.addView(TextView(this).apply {
            text = "Удалить"; setTextColor(Color.parseColor("#FF453A")); textSize = 17f
            isClickable = true; setOnClickListener { store.delete(id); finish() }
            setPadding(0,0,dp(16),0)
        })
        header.addView(TextView(this).apply {
            text = "Готово"; setTextColor(Color.parseColor("#D94F3D")); textSize = 17f; typeface = Fonts.medium
            isClickable = true; setOnClickListener { save() }
        })
        root.addView(header)

        val scroll = ScrollView(this); val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),0,dp(16),dp(24)) }
        scroll.addView(col); root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Text
        textField = EditText(this).apply {
            setText(intent.getStringExtra("prompt")?.let { "$it\n\n" } ?: editing?.text ?: "")
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#8E8E93"))
            hint = "Напишите о своём дне…"; textSize = 17f; typeface = Fonts.regular
            background = null; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 6
            setSelection(text.length)
        }
        col.addView(textField, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })

        // Mood
        col.addView(label("Настроение"))
        moodRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Mood.values().forEach { m ->
            val face = MoodFaceView(this).apply {
                setMood(m); selected = (m == selectedMood)
                isClickable = true
                setOnClickListener { Haptics.select(it); selectedMood = m; refreshMoods() }
            }
            moodRow.addView(face, LinearLayout.LayoutParams(dp(44), dp(44)).also { it.marginEnd = dp(10) })
        }
        col.addView(moodRow)

        // Photos
        col.addView(label("Фото"))
        photoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(photoRow)
        refreshPhotos()
        col.addView(chipButton("Добавить фото") { pickPhotos() })

        // Tags
        col.addView(label("Теги"))
        tagRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(tagRow)
        refreshTags()
        val tagInput = EditText(this).apply {
            hint = "Добавить тег"; setHintTextColor(Color.parseColor("#8E8E93")); setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 10f * d; setColor(Color.parseColor("#1C1C1E")) }
            setPadding(dp(12),dp(8),dp(12),dp(8)); setSingleLine()
            setOnEditorActionListener { v, _, _ ->
                val t = (v as EditText).text.toString().trim()
                if (t.isNotEmpty()) { tags.add(t); v.setText(""); refreshTags() }; true
            }
        }
        col.addView(tagInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6) })

        // Audio + location row
        col.addView(label("Вложения"))
        audioBtn = chipButton("Записать аудио") { toggleRecord() }
        col.addView(audioBtn)
        col.addView(chipButton("Добавить геопозицию") { addLocation() })
        metaLine = TextView(this).apply { setTextColor(Color.parseColor("#8E8E93")); textSize = 13f; setPadding(0,dp(8),0,0) }
        col.addView(metaLine)
        refreshMeta()

        return root
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#8E8E93")); textSize = 13f; typeface = Fonts.medium
        setPadding(0, (18 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
    }

    private fun chipButton(text: String, onClick: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text; setTextColor(Color.parseColor("#4F9DFF")); textSize = 15f; typeface = Fonts.medium
            background = GradientDrawable().apply { cornerRadius = 12f * d; setColor(Color.parseColor("#15233A")) }
            setPadding((14*d).toInt(),(10*d).toInt(),(14*d).toInt(),(10*d).toInt())
            isClickable = true; setOnClickListener { Haptics.tick(it); onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (8*d).toInt() }
        }
    }

    private fun refreshMoods() { for (i in 0 until moodRow.childCount) (moodRow.getChildAt(i) as MoodFaceView).selected = (Mood.values()[i] == selectedMood) }

    private fun refreshPhotos() {
        val d = resources.displayMetrics.density
        photoRow.removeAllViews()
        photoUris.forEach { uri ->
            photoRow.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                runCatching { setImageURI(Uri.parse(uri)) }
            }, LinearLayout.LayoutParams((72*d).toInt(), (72*d).toInt()).also { it.marginEnd = (8*d).toInt() })
        }
    }

    private fun refreshTags() {
        val d = resources.displayMetrics.density
        tagRow.removeAllViews()
        tags.forEach { tag ->
            tagRow.addView(TextView(this).apply {
                text = "#$tag"; setTextColor(Color.WHITE); textSize = 13f
                background = GradientDrawable().apply { cornerRadius = 12f * d; setColor(Color.parseColor("#2C2C2E")) }
                setPadding((10*d).toInt(),(5*d).toInt(),(10*d).toInt(),(5*d).toInt())
                isClickable = true; setOnClickListener { tags.remove(tag); refreshTags() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginEnd = (6*d).toInt() })
        }
    }

    private fun refreshMeta() {
        val parts = ArrayList<String>()
        location?.let { parts.add("Место: $it") }
        if (audioPath != null) parts.add("Аудио прикреплено")
        metaLine.text = parts.joinToString("  •  ")
    }

    // ── Photos ────────────────────────────────────────────────────────────────

    private fun pickPhotos() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, PICK) }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == PICK && res == RESULT_OK && data != null) {
            val uris = ArrayList<Uri>()
            data.clipData?.let { for (i in 0 until it.itemCount) uris.add(it.getItemAt(i).uri) }
            data.data?.let { uris.add(it) }
            uris.forEach { u ->
                runCatching { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                photoUris.add(u.toString())
            }
            refreshPhotos()
        }
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    private fun toggleRecord() {
        if (recording) { stopRecord() } else { startRecord() }
    }

    private fun startRecord() {
        val path = File(filesDir, "audio_$id.m4a").absolutePath
        runCatching {
            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(path); prepare(); start()
            }
            recording = true; audioPath = path
            audioBtn.text = "Остановить запись"
        }.onFailure { audioBtn.text = "Микрофон недоступен" }
    }

    private fun stopRecord() {
        runCatching { recorder?.stop(); recorder?.release() }
        recorder = null; recording = false
        audioBtn.text = "Аудио записано"
        refreshMeta()
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private fun addLocation() {
        runCatching {
            val lm = getSystemService(LocationManager::class.java)
            val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc != null) {
                val geo = Geocoder(this, Locale("ru"))
                @Suppress("DEPRECATION")
                val addr = geo.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                location = addr?.locality ?: addr?.subAdminArea ?: "${"%.3f".format(loc.latitude)}, ${"%.3f".format(loc.longitude)}"
                refreshMeta()
            } else { location = "Геопозиция недоступна"; refreshMeta() }
        }.onFailure { location = "Нет доступа к геопозиции"; refreshMeta() }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun save() {
        if (recording) stopRecord()
        val text = textField.text.toString().trim()
        if (text.isEmpty() && photoUris.isEmpty() && audioPath == null) { finish(); return }
        store.upsert(JournalEntry(
            id = id,
            timestamp = editing?.timestamp ?: System.currentTimeMillis(),
            text = text, photoUris = photoUris.toList(), moodId = selectedMood?.id,
            tags = tags.toList(), location = location, audioPath = audioPath))
        Haptics.confirm(textField)
        finish()
    }

    override fun onDestroy() { super.onDestroy(); runCatching { recorder?.release() } }
}

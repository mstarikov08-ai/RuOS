package com.ruos.screenshot.scanner

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * «Сканер документов» — turn a photo of a page into a clean scan: pick/capture an image, drag the
 * four corners to the page edges ([DocScanView]), then RuOS perspective-corrects it and applies a
 * high-contrast grayscale "scan" filter. Save to Photos as PNG or export a PDF to Documents. No
 * network, no cloud — everything is on-device.
 */
class DocScanActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private lateinit var scanView: DocScanView
    private var scanned: Bitmap? = null
    private val REQ_PICK = 101
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(0, dp(36), 0, 0)
        }
        root.addView(bar())
        scanView = DocScanView(this)
        root.addView(scanView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar())
        setContentView(root)

        // If launched with an image, load it; otherwise open the picker.
        val supplied = intent.data
        if (supplied != null) loadInto(supplied) else pickImage()
    }

    private fun bar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(nav("Отмена") { finish() })
        addView(TextView(this@DocScanActivity).apply {
            text = "Сканер документов"; setTextColor(Color.WHITE); textSize = 17f; typeface = golos; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(nav("Другое фото") { pickImage() })
    }

    private fun bottomBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(20), dp(12), dp(20), dp(24))
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(action("Сохранить PNG") { doScan()?.let { savePng(it) } }, lp)
        addView(action("Экспорт PDF") { doScan()?.let { savePdf(it) } }, lp)
    }

    private fun action(t: String, onTap: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#0A84FF")); textSize = 16f; typeface = golos
        gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12)); isClickable = true; setOnClickListener { onTap() }
    }

    private fun nav(t: String, onTap: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#0A84FF")); textSize = 16f; typeface = golos
        isClickable = true; setOnClickListener { onTap() }
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
        runCatching { startActivityForResult(i, REQ_PICK) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) loadInto(uri) else if (scanned == null) finish()
        }
    }

    private fun loadInto(uri: Uri) {
        val bmp = runCatching {
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?.copy(Bitmap.Config.ARGB_8888, true)
        }.getOrNull()
        if (bmp == null) { toast("Не удалось открыть изображение"); return }
        scanView.setBitmap(bmp)
    }

    /** Perspective-correct then apply the scan filter. */
    private fun doScan(): Bitmap? {
        val warped = scanView.warp() ?: run { toast("Не удалось обработать"); return null }
        return ScanFilter.enhance(warped).also { scanned = it }
    }

    private fun savePng(bmp: Bitmap) {
        val name = "RuOS_Scan_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Scans")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { toast("Не удалось сохранить"); return }
        runCatching {
            contentResolver.openOutputStream(uri).use { os -> os?.let { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            toast("Скан сохранён в Фото")
        }.onFailure { toast("Ошибка сохранения") }
    }

    private fun savePdf(bmp: Bitmap) {
        val name = "RuOS_Scan_${System.currentTimeMillis()}.pdf"
        runCatching {
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create())
            page.canvas.drawBitmap(bmp, 0f, 0f, null)
            doc.finishPage(page)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/Scans")
            }
            val collection = MediaStore.Files.getContentUri("external")
            val uri = contentResolver.insert(collection, values) ?: throw IllegalStateException("insert failed")
            contentResolver.openOutputStream(uri).use { os -> os?.let { doc.writeTo(it) } }
            doc.close()
            toast("PDF сохранён в Документы")
        }.onFailure { toast("Не удалось создать PDF") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

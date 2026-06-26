package com.ruos.files

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilesActivity : AppCompatActivity() {

    private val BG = Color.parseColor("#000000")
    private val SURFACE = Color.parseColor("#1C1C1E")
    private val SURFACE2 = Color.parseColor("#2C2C2E")
    private val RED = Color.parseColor("#D94F3D")
    private val BLUE = Color.parseColor("#0A84FF")
    private val TEXT_PRIMARY = Color.WHITE
    private val TEXT_SECONDARY = Color.parseColor("#8E8E93")
    private val SEPARATOR = Color.parseColor("#38383A")

    private lateinit var rootFrame: FrameLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var bottomTabBar: LinearLayout

    private var currentTab = 0
    private var currentDir: File = Environment.getExternalStorageDirectory()
    private val dirStack = mutableListOf<File>()

    // Sort for file browser
    private var sortMode = SortMode.NAME
    private var isGridMode = false

    // Multi-select state
    private var inSelectMode = false
    private val selectedFiles = mutableSetOf<File>()

    // View refs for file browser
    private var fileListContainer: LinearLayout? = null
    private var breadcrumbView: TextView? = null
    private var fileBrowserScroll: ScrollView? = null

    enum class SortMode { NAME, DATE, SIZE }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            showTab(currentTab)
        } else {
            // Try MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                try { startActivity(intent) } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#1A1A1A")

        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).also { it.bottomMargin = dp(83) }
        }

        bottomTabBar = buildBottomTabBar()
        rootFrame.addView(contentContainer)
        rootFrame.addView(bottomTabBar)
        setContentView(rootFrame)

        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        val hasStorage = Environment.isExternalStorageManager() ||
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (hasStorage) {
            showTab(0)
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun buildBottomTabBar(): LinearLayout {
        val labels = listOf("Обзор", "На устройстве", "Недавние")
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(83)
            ).also { it.gravity = Gravity.BOTTOM }
            setPadding(0, 0, 0, dp(20))
        }
        labels.forEachIndexed { i, label ->
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                isFocusable = true
                setOnClickListener { showTab(i) }
                tag = "tab_$i"
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(if (i == 0) RED else TEXT_SECONDARY)
                gravity = Gravity.CENTER
                tag = "tab_label_$i"
            }
            tab.addView(tv)
            bar.addView(tab)
        }
        return bar
    }

    private fun updateTabColors(active: Int) {
        for (i in 0..2) {
            rootFrame.findViewWithTag<TextView>("tab_label_$i")
                ?.setTextColor(if (i == active) RED else TEXT_SECONDARY)
        }
    }

    private fun showTab(index: Int) {
        currentTab = index
        updateTabColors(index)
        contentContainer.removeAllViews()
        when (index) {
            0 -> contentContainer.addView(buildBrowseTab())
            1 -> {
                currentDir = Environment.getExternalStorageDirectory()
                dirStack.clear()
                contentContainer.addView(buildLocalFilesTab())
            }
            2 -> contentContainer.addView(buildRecentsTab())
        }
    }

    // ── Tab 1: Browse ────────────────────────────────────────────────────────

    private fun buildBrowseTab(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Обзор"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(8), 0, dp(20))
        })

        // Favourites section
        root.addView(sectionCard("Избранное", listOf(
            BrowseRow("Яндекс Диск", "Облачное хранилище", BLUE) {
                openYandexDisk()
            },
            BrowseRow("На устройстве", "Локальное хранилище", BLUE) {
                showTab(1)
            }
        )))

        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)
            )
        })

        // Locations section
        val extDir = Environment.getExternalStorageDirectory()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val trashDir = File(extDir, ".Trash")

        root.addView(sectionCard("Места", listOf(
            BrowseRow("Недавно удалённые", formatFileCount(trashDir), RED) {
                navigateToDir(trashDir)
            },
            BrowseRow("Загрузки", formatDirSize(downloadsDir), BLUE) {
                navigateToDir(downloadsDir)
            },
            BrowseRow("Документы", formatDirSize(docsDir), BLUE) {
                navigateToDir(docsDir)
            },
            BrowseRow("Хранилище", formatDirSize(extDir), BLUE) {
                navigateToDir(extDir)
            }
        )))

        scroll.addView(root)
        return scroll
    }

    private data class BrowseRow(val name: String, val subtitle: String, val tint: Int, val action: () -> Unit)

    private fun sectionCard(title: String, rows: List<BrowseRow>): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(8))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rows.forEachIndexed { i, row ->
            val rowView = buildBrowseRow(row)
            card.addView(rowView)
            if (i < rows.size - 1) {
                card.addView(View(this).apply {
                    setBackgroundColor(SEPARATOR)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    ).also { it.leftMargin = dp(52) }
                })
            }
        }
        container.addView(card)
        return container
    }

    private fun buildBrowseRow(row: BrowseRow): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { row.action() }

            // Icon circle
            val iconCircle = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also {
                    it.marginEnd = dp(12)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(row.tint)
                }
            }
            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_save)
                layoutParams = FrameLayout.LayoutParams(dp(18), dp(18)).also {
                    it.gravity = Gravity.CENTER
                }
                setColorFilter(Color.WHITE)
            }
            iconCircle.addView(icon)
            addView(iconCircle)

            // Text group
            val textGroup = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textGroup.addView(TextView(context).apply {
                text = row.name
                textSize = 16f
                setTextColor(TEXT_PRIMARY)
            })
            textGroup.addView(TextView(context).apply {
                text = row.subtitle
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
            })
            addView(textGroup)

            // Chevron
            addView(TextView(context).apply {
                text = "›"
                textSize = 20f
                setTextColor(TEXT_SECONDARY)
            })
        }
    }

    private fun openYandexDisk() {
        val yandexPkg = "ru.yandex.disk"
        val pm = packageManager
        try {
            pm.getPackageInfo(yandexPkg, 0)
            startActivity(pm.getLaunchIntentForPackage(yandexPkg))
        } catch (_: PackageManager.NameNotFoundException) {
            Toast.makeText(this, "Установите Яндекс Диск", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToDir(dir: File) {
        currentDir = dir
        dirStack.clear()
        showTab(1)
    }

    // ── Tab 2: Local Files ──────────────────────────────────────────────────

    private fun buildLocalFilesTab(): LinearLayout {
        inSelectMode = false
        selectedFiles.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Title bar
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(8))
        }

        // Back button
        val backBtn = TextView(this).apply {
            text = "‹"
            textSize = 28f
            setTextColor(TEXT_PRIMARY)
            setPadding(0, 0, dp(12), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { navigateUp() }
            visibility = if (dirStack.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
        titleBar.addView(backBtn)

        // Large title
        titleBar.addView(TextView(this).apply {
            text = "На устройстве"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Sort + grid toggle buttons
        val sortBtn = TextView(this).apply {
            text = "⋮"
            textSize = 22f
            setTextColor(BLUE)
            setPadding(dp(12), 0, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { showSortMenu() }
        }
        titleBar.addView(sortBtn)

        val gridBtn = ImageView(this).apply {
            setImageDrawable(if (isGridMode) listViewDrawable(BLUE) else gridViewDrawable(BLUE))
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                isGridMode = !isGridMode
                setImageDrawable(if (isGridMode) listViewDrawable(BLUE) else gridViewDrawable(BLUE))
                refreshFileList()
            }
        }
        titleBar.addView(gridBtn)

        root.addView(titleBar)

        // Breadcrumb
        val breadcrumb = buildBreadcrumb()
        breadcrumbView = breadcrumb
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(breadcrumb)
            setPadding(dp(16), 0, dp(16), dp(4))
        })

        // Separator
        root.addView(View(this).apply {
            setBackgroundColor(SEPARATOR)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        })

        // File list scroll
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val fileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fileListContainer = fileList
        scroll.addView(fileList)
        fileBrowserScroll = scroll
        root.addView(scroll)

        populateFileList()
        return root
    }

    private fun buildBreadcrumb(): TextView {
        val parts = mutableListOf("Хранилище")
        var dir = currentDir
        val extRoot = Environment.getExternalStorageDirectory()
        val relParts = mutableListOf<String>()
        while (dir != extRoot && dir.parentFile != null) {
            relParts.add(0, dir.name)
            dir = dir.parentFile!!
        }
        parts.addAll(relParts)
        return TextView(this).apply {
            text = parts.joinToString(" / ")
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
        }
    }

    private fun refreshFileList() {
        fileListContainer?.removeAllViews()
        // Update breadcrumb
        breadcrumbView?.text = buildBreadcrumb().text
        populateFileList()
    }

    private fun populateFileList() {
        val container = fileListContainer ?: return
        val files = currentDir.listFiles()?.toMutableList() ?: mutableListOf()
        when (sortMode) {
            SortMode.NAME -> files.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortMode.DATE -> files.sortWith(compareByDescending { it.lastModified() })
            SortMode.SIZE -> files.sortWith(compareByDescending { it.length() })
        }

        if (files.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Папка пуста"
                textSize = 16f
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(32), dp(16), dp(32))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        files.forEachIndexed { i, file ->
            container.addView(buildFileRow(file))
            if (i < files.size - 1) {
                container.addView(View(this).apply {
                    setBackgroundColor(SEPARATOR)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    ).also { it.leftMargin = dp(60) }
                })
            }
        }
    }

    private fun buildFileRow(file: File): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isFocusable = true
            background = if (selectedFiles.contains(file)) {
                GradientDrawable().apply { setColor(Color.parseColor("#2C2C2E")) }
            } else null

            setOnClickListener {
                if (inSelectMode) {
                    toggleFileSelection(file, this)
                } else if (file.isDirectory) {
                    enterDirectory(file)
                } else {
                    openFile(file)
                }
            }
            setOnLongClickListener {
                if (!inSelectMode) {
                    inSelectMode = true
                    selectedFiles.clear()
                }
                toggleFileSelection(file, this)
                true
            }

            // File icon
            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).also {
                    it.marginEnd = dp(12)
                }
                setImageResource(getFileIconRes(file))
                setColorFilter(getFileIconColor(file))
            }
            addView(iconView)

            // Name + details
            val textGroup = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textGroup.addView(TextView(context).apply {
                text = file.name
                textSize = 15f
                setTextColor(TEXT_PRIMARY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            textGroup.addView(TextView(context).apply {
                text = if (file.isDirectory) {
                    val count = file.listFiles()?.size ?: 0
                    "$count элем."
                } else {
                    "${formatSize(file.length())}  •  ${formatDate(file.lastModified())}"
                }
                textSize = 12f
                setTextColor(TEXT_SECONDARY)
            })
            addView(textGroup)

            // Chevron for directories
            if (file.isDirectory) {
                addView(TextView(context).apply {
                    text = "›"
                    textSize = 20f
                    setTextColor(TEXT_SECONDARY)
                })
            }
        }
    }

    private fun toggleFileSelection(file: File, view: View) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
            view.background = null
        } else {
            selectedFiles.add(file)
            view.background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C2E"))
            }
        }
        if (selectedFiles.isEmpty()) {
            inSelectMode = false
        }
    }

    private fun enterDirectory(dir: File) {
        dirStack.add(currentDir)
        currentDir = dir
        contentContainer.removeAllViews()
        contentContainer.addView(buildLocalFilesTab())
    }

    private fun navigateUp() {
        if (dirStack.isNotEmpty()) {
            currentDir = dirStack.removeAt(dirStack.size - 1)
            contentContainer.removeAllViews()
            contentContainer.addView(buildLocalFilesTab())
        }
    }

    override fun onBackPressed() {
        if (currentTab == 1 && dirStack.isNotEmpty()) {
            navigateUp()
        } else {
            super.onBackPressed()
        }
    }

    private fun openFile(file: File) {
        val mime = getMimeType(file)
        val uri = Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Нет приложения для открытия этого файла", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSortMenu() {
        val sorts = listOf("По имени" to SortMode.NAME, "По дате" to SortMode.DATE, "По размеру" to SortMode.SIZE)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Сортировка")
            .setItems(sorts.map { it.first }.toTypedArray()) { _, i ->
                sortMode = sorts[i].second
                refreshFileList()
            }
            .create()
        dialog.show()
    }

    private fun getFileIconRes(file: File): Int {
        if (file.isDirectory) return android.R.drawable.ic_menu_agenda
        return when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> android.R.drawable.ic_menu_gallery
            "mp4", "avi", "mkv", "mov", "wmv", "flv" -> android.R.drawable.ic_media_play
            "mp3", "aac", "flac", "wav", "ogg", "m4a" -> android.R.drawable.ic_media_play
            "pdf" -> android.R.drawable.ic_menu_info_details
            "zip", "rar", "7z", "tar", "gz" -> android.R.drawable.ic_menu_upload
            "doc", "docx", "txt", "odt" -> android.R.drawable.ic_menu_edit
            "xls", "xlsx", "csv" -> android.R.drawable.ic_menu_agenda
            "apk" -> android.R.drawable.ic_menu_add
            else -> android.R.drawable.ic_menu_save
        }
    }

    private fun getFileIconColor(file: File): Int {
        if (file.isDirectory) return BLUE
        return when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> Color.parseColor("#30D158")
            "mp4", "avi", "mkv", "mov" -> Color.parseColor("#FF453A")
            "mp3", "aac", "flac", "wav", "ogg" -> Color.parseColor("#BF5AF2")
            "pdf" -> Color.parseColor("#FF453A")
            "zip", "rar", "7z" -> Color.parseColor("#FFD60A")
            "doc", "docx", "txt" -> BLUE
            "apk" -> Color.parseColor("#30D158")
            else -> TEXT_SECONDARY
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "apk" -> "application/vnd.android.package-archive"
            "doc", "docx" -> "application/msword"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }

    // ── Tab 3: Recents ──────────────────────────────────────────────────────

    private fun buildRecentsTab(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Недавние"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(8), 0, dp(16))
        })

        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val recentFiles = mutableListOf<Triple<Uri, String, Long>>() // uri, name, modified

        // Query images
        val imgProjection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            imgProjection,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} > ?",
            arrayOf((sevenDaysAgo / 1000).toString()),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            while (cursor.moveToNext() && recentFiles.size < 100) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val modified = cursor.getLong(dateCol) * 1000
                val uri = ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri("external"), id
                )
                recentFiles.add(Triple(uri, name, modified))
            }
        }

        if (recentFiles.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Нет недавних файлов"
                textSize = 16f
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, dp(32), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        } else {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(SURFACE)
                    cornerRadius = dp(12).toFloat()
                }
            }
            recentFiles.forEachIndexed { i, (uri, name, modified) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val mime = guessMimeFromName(name)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try { startActivity(intent) }
                        catch (_: Exception) {
                            Toast.makeText(this@FilesActivity, "Нет приложения для этого файла", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val iconRes = getFileIconResByName(name)
                val iconColor = getFileIconColorByName(name)
                row.addView(ImageView(this).apply {
                    setImageResource(iconRes)
                    setColorFilter(iconColor)
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.marginEnd = dp(12) }
                })
                val textGroup = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                textGroup.addView(TextView(this).apply {
                    text = name
                    textSize = 15f
                    setTextColor(TEXT_PRIMARY)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                textGroup.addView(TextView(this).apply {
                    text = formatDate(modified)
                    textSize = 12f
                    setTextColor(TEXT_SECONDARY)
                })
                row.addView(textGroup)
                card.addView(row)
                if (i < recentFiles.size - 1) {
                    card.addView(View(this).apply {
                        setBackgroundColor(SEPARATOR)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                        ).also { it.leftMargin = dp(60) }
                    })
                }
            }
            root.addView(card)
        }

        scroll.addView(root)
        return scroll
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes Б"
            bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
            bytes < 1024 * 1024 * 1024 -> "${DecimalFormat("#.#").format(bytes.toFloat() / (1024 * 1024))} МБ"
            else -> "${DecimalFormat("#.##").format(bytes.toFloat() / (1024 * 1024 * 1024))} ГБ"
        }
    }

    private fun formatDate(ms: Long): String {
        return SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(ms))
    }

    private fun formatFileCount(dir: File): String {
        val count = try { dir.listFiles()?.size ?: 0 } catch (_: Exception) { 0 }
        return "$count элем."
    }

    private fun formatDirSize(dir: File): String {
        return try {
            val size = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            formatSize(size)
        } catch (_: Exception) { "—" }
    }

    private fun getFileIconResByName(name: String): Int {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> android.R.drawable.ic_menu_gallery
            "mp4", "avi", "mkv", "mov" -> android.R.drawable.ic_media_play
            "mp3", "aac", "flac", "wav" -> android.R.drawable.ic_media_play
            "pdf" -> android.R.drawable.ic_menu_info_details
            "zip", "rar", "7z" -> android.R.drawable.ic_menu_upload
            "doc", "docx", "txt" -> android.R.drawable.ic_menu_edit
            else -> android.R.drawable.ic_menu_save
        }
    }

    private fun getFileIconColorByName(name: String): Int {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp" -> Color.parseColor("#30D158")
            "mp4", "avi", "mkv", "mov" -> Color.parseColor("#FF453A")
            "mp3", "aac", "flac", "wav" -> Color.parseColor("#BF5AF2")
            "pdf" -> Color.parseColor("#FF453A")
            "zip", "rar", "7z" -> Color.parseColor("#FFD60A")
            "doc", "docx", "txt" -> BLUE
            else -> TEXT_SECONDARY
        }
    }

    private fun guessMimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "*/*"
        }
    }

    private fun listViewDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = android.graphics.Paint.Style.FILL
            }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            val lh = h * 0.12f; val gap = h * 0.06f; val startY = h * 0.18f
            for (i in 0..3) {
                val y = startY + i * (lh + gap)
                canvas.drawRoundRect(android.graphics.RectF(w*0.1f, y, w*0.9f, y+lh), lh/2, lh/2, p)
            }
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun gridViewDrawable(color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = android.graphics.Paint.Style.FILL
            }
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            val cellW = w * 0.35f; val cellH = h * 0.35f; val gap = w * 0.1f
            val startX = w * 0.1f; val startY = h * 0.1f
            for (row in 0..1) for (col in 0..1) {
                val x = startX + col * (cellW + gap); val y = startY + row * (cellH + gap)
                canvas.drawRoundRect(android.graphics.RectF(x, y, x+cellW, y+cellH), cellW*0.15f, cellW*0.15f, p)
            }
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics).toInt()
}

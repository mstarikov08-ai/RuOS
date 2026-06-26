package com.ruos.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class GalleryActivity : AppCompatActivity() {

    // Colors
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

    private val photoUris = mutableListOf<Uri>()
    private val albumMap = mutableMapOf<String, MutableList<Uri>>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            loadPhotos()
            showTab(currentTab)
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
        val perms = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        val missing = perms.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            loadPhotos()
            showTab(0)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadPhotos() {
        photoUris.clear()
        albumMap.clear()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                photoUris.add(uri)
                if (bucketCol >= 0) {
                    val bucket = cursor.getString(bucketCol) ?: "Прочее"
                    albumMap.getOrPut(bucket) { mutableListOf() }.add(uri)
                }
            }
        }
    }

    private fun buildBottomTabBar(): LinearLayout {
        val labels = listOf("Библиотека", "Альбомы", "Поиск")
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
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).also {
                    it.bottomMargin = dp(3)
                }
                tag = "tab_icon_$i"
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(if (i == 0) RED else TEXT_SECONDARY)
                gravity = Gravity.CENTER
                tag = "tab_label_$i"
            }
            tab.addView(icon)
            tab.addView(tv)
            bar.addView(tab)
        }
        return bar
    }

    private fun updateTabColors(active: Int) {
        for (i in 0..2) {
            val label = rootFrame.findViewWithTag<TextView>("tab_label_$i")
            label?.setTextColor(if (i == active) RED else TEXT_SECONDARY)
        }
    }

    private fun showTab(index: Int) {
        currentTab = index
        updateTabColors(index)
        contentContainer.removeAllViews()
        when (index) {
            0 -> contentContainer.addView(buildLibraryTab())
            1 -> contentContainer.addView(buildAlbumsTab())
            2 -> contentContainer.addView(buildSearchTab())
        }
    }

    // ── Tab 1: Library ──────────────────────────────────────────────────────

    private fun buildLibraryTab(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(56), 0, dp(16))
        }

        // Title
        root.addView(TextView(this).apply {
            text = "Библиотека"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(dp(16), dp(8), dp(16), dp(16))
        })

        // Memories section
        root.addView(TextView(this).apply {
            text = "Воспоминания"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        })
        root.addView(buildMemoriesStrip())

        // Separator
        root.addView(View(this).apply {
            setBackgroundColor(SEPARATOR)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.setMargins(dp(16), dp(12), dp(16), dp(12)) }
        })

        // "Все фото" header
        val countLabel = TextView(this).apply {
            text = "Все фото  ${photoUris.size}"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        root.addView(countLabel)

        // Photo grid
        root.addView(buildPhotoGrid(photoUris))

        scroll.addView(root)
        return scroll
    }

    private fun buildMemoriesStrip(): HorizontalScrollView {
        val months = listOf("Январь 2024", "Февраль 2024", "Март 2024")
        val hsv = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        months.forEachIndexed { i, month ->
            val card = FrameLayout(this).apply {
                setBackgroundColor(SURFACE)
                layoutParams = LinearLayout.LayoutParams(dp(200), dp(120)).also {
                    it.marginEnd = dp(12)
                }
                // Rounded corners via background
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(SURFACE)
                    cornerRadius = dp(12).toFloat()
                }
            }
            val tv = TextView(this).apply {
                text = month
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
                gravity = Gravity.BOTTOM or Gravity.START
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            card.addView(tv)
            row.addView(card)
        }
        hsv.addView(row)
        return hsv
    }

    private fun buildPhotoGrid(uris: List<Uri>): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val gapDp = dp(2)
        val colCount = 3
        val screenW = resources.displayMetrics.widthPixels
        val cellSize = (screenW - gapDp * (colCount - 1)) / colCount

        var row: LinearLayout? = null
        uris.forEachIndexed { idx, uri ->
            if (idx % colCount == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { if (idx > 0) it.topMargin = gapDp }
                }
                container.addView(row)
            }
            val cell = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(cellSize, cellSize).also {
                    if (idx % colCount > 0) it.leftMargin = gapDp
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(SURFACE)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val intent = Intent(this@GalleryActivity, PhotoViewActivity::class.java).apply {
                        putExtra("photo_uri", uri.toString())
                        putExtra("photo_index", idx)
                        putExtra("photo_count", uris.size)
                    }
                    startActivity(intent)
                }
            }
            row?.addView(cell)
            // Load thumbnail in background
            Thread {
                try {
                    val bmp = contentResolver.loadThumbnail(uri, Size(200, 200), null)
                    runOnUiThread { cell.setImageBitmap(bmp) }
                } catch (_: Exception) {}
            }.start()
        }
        // Fill last row if needed
        val remainder = uris.size % colCount
        if (remainder != 0) {
            val needed = colCount - remainder
            for (i in 0 until needed) {
                row?.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(cellSize, cellSize).also {
                        it.leftMargin = gapDp
                    }
                })
            }
        }
        return container
    }

    // ── Tab 2: Albums ───────────────────────────────────────────────────────

    private fun buildAlbumsTab(): ScrollView {
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
            text = "Альбомы"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(8), 0, dp(16))
        })

        // My Albums section header
        root.addView(sectionHeader("Мои альбомы"))

        // Standard system albums in 2-column grid
        val systemAlbums = listOf(
            "Все фото" to photoUris.size,
            "Видео" to 0,
            "Избранное" to 0,
            "Недавно удалённые" to 0
        )
        root.addView(buildAlbumGrid(systemAlbums, photoUris))

        // Custom albums from MediaStore
        if (albumMap.isNotEmpty()) {
            root.addView(sectionHeader("Другие альбомы"))
            val customAlbums = albumMap.map { (name, uris) -> name to uris.size }
            root.addView(buildAlbumGrid(customAlbums, null))
        }

        scroll.addView(root)
        return scroll
    }

    private fun sectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun buildAlbumGrid(albums: List<Pair<String, Int>>, uriSource: List<Uri>?): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val screenW = resources.displayMetrics.widthPixels - dp(32)
        val cellW = (screenW - dp(12)) / 2

        var row: LinearLayout? = null
        albums.forEachIndexed { idx, (name, count) ->
            if (idx % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { if (idx > 0) it.topMargin = dp(16) }
                }
                container.addView(row)
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(cellW, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                    if (idx % 2 == 1) it.leftMargin = dp(12)
                }
            }
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(cellW, cellW)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(SURFACE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(SURFACE)
                    cornerRadius = dp(12).toFloat()
                }
            }
            // Load first photo of album as thumbnail
            val thumbUri = when {
                name == "Все фото" && uriSource != null -> uriSource.firstOrNull()
                else -> albumMap[name]?.firstOrNull()
            }
            if (thumbUri != null) {
                Thread {
                    try {
                        val bmp = contentResolver.loadThumbnail(thumbUri, Size(300, 300), null)
                        runOnUiThread { thumb.setImageBitmap(bmp) }
                    } catch (_: Exception) {}
                }.start()
            }
            val nameView = TextView(this).apply {
                text = name
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
                setPadding(0, dp(6), 0, dp(2))
            }
            val countView = TextView(this).apply {
                text = count.toString()
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
            }
            card.addView(thumb)
            card.addView(nameView)
            card.addView(countView)
            row?.addView(card)
        }
        return container
    }

    // ── Tab 3: Search ───────────────────────────────────────────────────────

    private var searchResults: LinearLayout? = null
    private var allPhotosForSearch: List<Pair<Uri, String>> = emptyList()

    private fun buildSearchTab(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(16), dp(56), dp(16), 0)
        }

        root.addView(TextView(this).apply {
            text = "Поиск"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(8), 0, dp(12))
        })

        // Search bar
        val searchBar = android.widget.EditText(this).apply {
            hint = "Поиск"
            setHintTextColor(TEXT_SECONDARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 16f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    performSearch(s?.toString() ?: "")
                }
            })
        }
        root.addView(searchBar)

        // Categories
        root.addView(TextView(this).apply {
            text = "Категории"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(20), 0, dp(12))
        })

        val categories = listOf("Люди", "Места", "Даты", "Природа", "Еда", "Спорт")
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        categories.forEach { cat ->
            val chip = TextView(this).apply {
                text = cat
                textSize = 14f
                setTextColor(TEXT_PRIMARY)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(SURFACE2)
                    cornerRadius = dp(16).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(8) }
            }
            chipRow.addView(chip)
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        // Scroll view for search results
        val resultsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        searchResults = resultsContainer
        resultsScroll.addView(resultsContainer)
        root.addView(resultsScroll)

        // Pre-load search data
        loadSearchData()
        return root
    }

    private fun loadSearchData() {
        val list = mutableListOf<Pair<Uri, String>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                list.add(uri to name)
            }
        }
        allPhotosForSearch = list
    }

    private fun performSearch(query: String) {
        val container = searchResults ?: return
        container.removeAllViews()
        if (query.isBlank()) return

        val filtered = allPhotosForSearch.filter {
            it.second.contains(query, ignoreCase = true)
        }.take(30)

        filtered.forEach { (uri, name) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val intent = Intent(this@GalleryActivity, PhotoViewActivity::class.java).apply {
                        putExtra("photo_uri", uri.toString())
                    }
                    startActivity(intent)
                }
            }
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).also {
                    it.marginEnd = dp(12)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(SURFACE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(SURFACE)
                    cornerRadius = dp(8).toFloat()
                }
            }
            Thread {
                try {
                    val bmp = contentResolver.loadThumbnail(uri, Size(100, 100), null)
                    runOnUiThread { thumb.setImageBitmap(bmp) }
                } catch (_: Exception) {}
            }.start()

            val nameView = TextView(this).apply {
                text = name
                textSize = 15f
                setTextColor(TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.gravity = Gravity.CENTER_VERTICAL
                }
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(thumb)
            row.addView(nameView)
            container.addView(row)

            // Separator
            container.addView(View(this).apply {
                setBackgroundColor(SEPARATOR)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).also { it.leftMargin = dp(68) }
            })
        }

        if (filtered.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Ничего не найдено"
                textSize = 16f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, dp(24), 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics).toInt()
}

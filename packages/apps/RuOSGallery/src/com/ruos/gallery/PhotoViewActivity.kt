package com.ruos.gallery

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoViewActivity : AppCompatActivity() {

    private val BG = Color.parseColor("#000000")
    private val SURFACE = Color.parseColor("#1C1C1E")
    private val RED = Color.parseColor("#D94F3D")
    private val BLUE = Color.parseColor("#0A84FF")
    private val TEXT_PRIMARY = Color.WHITE
    private val TEXT_SECONDARY = Color.parseColor("#8E8E93")

    private lateinit var photoImageView: ImageView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var dateView: TextView
    private lateinit var favouriteBtn: TextView

    private var barsVisible = true
    private var currentScale = 1f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var translateX = 0f
    private var translateY = 0f
    private val matrix = Matrix()

    private var currentUri: Uri? = null
    private var currentIndex = 0
    private var photoCount = 0
    private val allUris = mutableListOf<Uri>()
    private var isFavourite = false

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val uriString = intent.getStringExtra("photo_uri")
            ?: intent.data?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_STREAM)
        currentIndex = intent.getIntExtra("photo_index", 0)
        photoCount = intent.getIntExtra("photo_count", 1)

        currentUri = uriString?.let { Uri.parse(it) }
        loadAllUris()

        val root = FrameLayout(this).apply {
            setBackgroundColor(BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Photo ImageView
        photoImageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.MATRIX
            setBackgroundColor(BG)
        }
        root.addView(photoImageView)

        // Top bar
        topBar = buildTopBar()
        root.addView(topBar)

        // Bottom bar
        bottomBar = buildBottomBar()
        root.addView(bottomBar)

        setContentView(root)

        setupGestures()
        loadPhoto(currentUri)
        updateTitle()
    }

    private fun loadAllUris() {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                allUris.add(ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                ))
            }
        }
    }

    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(88)
            ).also { it.gravity = Gravity.TOP }
            gravity = Gravity.BOTTOM
            setPadding(dp(8), 0, dp(8), dp(8))

            // Back button
            addView(TextView(context).apply {
                text = "‹  Назад"
                textSize = 17f
                setTextColor(TEXT_PRIMARY)
                setPadding(dp(8), dp(8), dp(16), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener { finish() }
            })

            // Title + date in center
            val titleGroup = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleView = TextView(context).apply {
                text = ""
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
                gravity = Gravity.CENTER
            }
            dateView = TextView(context).apply {
                text = ""
                textSize = 12f
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.CENTER
            }
            titleGroup.addView(titleView)
            titleGroup.addView(dateView)
            addView(titleGroup)

            // Spacer to balance back button
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(80), dp(1))
            })
        }
    }

    private fun buildBottomBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(90)
            ).also { it.gravity = Gravity.BOTTOM }
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))

            // Share
            addView(buildActionButton("Поделиться", android.R.drawable.ic_menu_share) {
                sharePhoto()
            })

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })

            // Favourite
            favouriteBtn = TextView(context).apply {
                text = "♡"
                textSize = 26f
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { toggleFavourite() }
            }
            addView(favouriteBtn)

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })

            // Delete
            addView(buildActionButton("Удалить", android.R.drawable.ic_menu_delete) {
                deletePhoto()
            })
        }
    }

    private fun buildActionButton(label: String, iconRes: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                }
            }
            val tv = TextView(context).apply {
                text = label
                textSize = 10f
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.CENTER
            }
            addView(icon)
            addView(tv)
        }
    }

    private fun setupGestures() {
        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    val newScale = (currentScale * scaleFactor).coerceIn(1f, 5f)
                    val dsf = newScale / currentScale
                    currentScale = newScale
                    matrix.postScale(dsf, dsf, detector.focusX, detector.focusY)
                    photoImageView.imageMatrix = matrix
                    return true
                }
            })

        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleBars()
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?, e2: MotionEvent,
                    velocityX: Float, velocityY: Float
                ): Boolean {
                    if (currentScale > 1f) return false
                    if (Math.abs(velocityX) > Math.abs(velocityY) * 1.5f) {
                        if (velocityX < -500) navigatePhoto(1)
                        else if (velocityX > 500) navigatePhoto(-1)
                        return true
                    }
                    return false
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (currentScale > 1f) {
                        currentScale = 1f
                        matrix.reset()
                        fitPhoto()
                    } else {
                        currentScale = 3f
                        matrix.postScale(3f, 3f, e.x, e.y)
                        photoImageView.imageMatrix = matrix
                    }
                    return true
                }
            })

        photoImageView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress && currentScale > 1f) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        matrix.postTranslate(dx, dy)
                        photoImageView.imageMatrix = matrix
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }
            true
        }
    }

    private fun loadPhoto(uri: Uri?) {
        if (uri == null) return
        currentUri = uri
        Thread {
            try {
                val bmp = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
                runOnUiThread {
                    if (bmp != null) {
                        photoImageView.setImageBitmap(bmp)
                        currentScale = 1f
                        matrix.reset()
                        fitPhoto()
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun fitPhoto() {
        val drawable = photoImageView.drawable ?: return
        val vw = photoImageView.width.toFloat()
        val vh = photoImageView.height.toFloat()
        if (vw == 0f || vh == 0f) {
            photoImageView.post { fitPhoto() }
            return
        }
        val iw = drawable.intrinsicWidth.toFloat()
        val ih = drawable.intrinsicHeight.toFloat()
        if (iw == 0f || ih == 0f) return
        val scale = minOf(vw / iw, vh / ih)
        val dx = (vw - iw * scale) / 2f
        val dy = (vh - ih * scale) / 2f
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        photoImageView.imageMatrix = matrix
    }

    private fun navigatePhoto(delta: Int) {
        if (allUris.isEmpty()) return
        currentIndex = (currentIndex + delta).coerceIn(0, allUris.size - 1)
        loadPhoto(allUris[currentIndex])
        updateTitle()
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val alpha = if (barsVisible) 1f else 0f
        ObjectAnimator.ofFloat(topBar, "alpha", alpha).apply { duration = 200 }.start()
        ObjectAnimator.ofFloat(bottomBar, "alpha", alpha).apply { duration = 200 }.start()
    }

    private fun updateTitle() {
        val uri = currentUri ?: return
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val name = if (nameCol >= 0) cursor.getString(nameCol) else ""
                val dateTaken = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                val dateStr = if (dateTaken > 0) {
                    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(dateTaken))
                } else ""
                runOnUiThread {
                    titleView.text = name ?: ""
                    dateView.text = dateStr
                }
            }
        }
    }

    private fun toggleFavourite() {
        isFavourite = !isFavourite
        favouriteBtn.text = if (isFavourite) "♥" else "♡"
        favouriteBtn.setTextColor(if (isFavourite) RED else TEXT_SECONDARY)
        val uri = currentUri ?: return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_FAVORITE, if (isFavourite) 1 else 0)
            }
            contentResolver.update(uri, values, null, null)
        } catch (_: Exception) {}
    }

    private fun sharePhoto() {
        val uri = currentUri ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Поделиться фото"))
    }

    private fun deletePhoto() {
        val uri = currentUri ?: return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_TRASHED, 1)
            }
            contentResolver.update(uri, values, null, null)
            Toast.makeText(this, "Фото перемещено в корзину", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось удалить фото", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fitPhoto()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics).toInt()
}

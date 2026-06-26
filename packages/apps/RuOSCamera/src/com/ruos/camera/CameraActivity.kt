package com.ruos.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.media.MediaActionSound
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.app.Activity
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.graphics.drawable.GradientDrawable
import android.view.ScaleGestureDetector

class CameraActivity : Activity() {

    companion object {
        private const val TAG = "RuOSCamera"
        private const val REQUEST_PERMISSIONS = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    // --- Views ---
    private lateinit var textureView: TextureView
    private lateinit var focusView: FocusView
    private lateinit var thumbnailView: ImageView
    private lateinit var shutterButton: View
    private lateinit var flipButton: ImageView
    private lateinit var flashButton: TextView
    private lateinit var modeContainer: LinearLayout
    private lateinit var rootLayout: FrameLayout

    // --- Camera2 ---
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraId: String = ""
    private var isFrontCamera = false
    private val cameraThread = HandlerThread("CameraThread").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private lateinit var cameraManager: CameraManager
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var sensorArraySize: Rect? = null
    private var maxZoom = 1f
    private var currentZoom = 1f

    // --- State ---
    private var flashMode = FlashMode.AUTO
    private var currentMode = CameraMode.PHOTO
    private val mediaActionSound = MediaActionSound()

    enum class FlashMode { AUTO, ON, OFF }
    enum class CameraMode { PHOTO, VIDEO, PORTRAIT, PANORAMA }

    // --- Zoom ---
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // --- Surface listener ---
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    // --- Camera state callback ---
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }
        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }
        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            Log.e(TAG, "Camera error: $error")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        buildUI()
    }

    private fun buildUI() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(rootLayout)

        // Full-screen TextureView
        textureView = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(textureView)

        // Focus overlay
        focusView = FocusView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(focusView)

        // Top bar
        buildTopBar()

        // Mode selector (horizontal, centered vertically in lower third)
        buildModeSelector()

        // Bottom controls
        buildBottomControls()

        // Touch listeners
        setupTouchListeners()
    }

    private fun buildTopBar() {
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(12))
        }
        val topBarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        rootLayout.addView(topBar, topBarParams)

        // Flash button
        flashButton = TextView(this).apply {
            text = "⚡AUTO"
            textSize = 13f
            setTextColor(Color.YELLOW)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener { cycleFlash() }
        }
        topBar.addView(flashButton)

        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        // Timer button
        TextView(this).apply {
            text = "⏱"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }.also { topBar.addView(it) }

        // Ratio button
        TextView(this).apply {
            text = "4:3"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }.also { topBar.addView(it) }

        // Live photo button
        TextView(this).apply {
            text = "◎"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }.also { topBar.addView(it) }
    }

    private fun buildModeSelector() {
        val scrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        modeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        scrollView.addView(modeContainer)

        val modes = listOf("ФОТО", "ВИДЕО", "ПОРТРЕТ", "ПАНОРАМА")
        val modeEnums = listOf(CameraMode.PHOTO, CameraMode.VIDEO, CameraMode.PORTRAIT, CameraMode.PANORAMA)

        modes.forEachIndexed { index, name ->
            val btn = TextView(this).apply {
                text = name
                textSize = 14f
                letterSpacing = 0.05f
                setTextColor(if (index == 0) Color.YELLOW else Color.parseColor("#AAAAAA"))
                setPadding(dp(16), dp(8), dp(16), dp(8))
                tag = index
                setOnClickListener { selectMode(modeEnums[index], this) }
            }
            modeContainer.addView(btn)
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(160)
        }
        rootLayout.addView(scrollView, params)
    }

    private fun selectMode(mode: CameraMode, selectedView: TextView) {
        currentMode = mode
        for (i in 0 until modeContainer.childCount) {
            val child = modeContainer.getChildAt(i) as? TextView
            child?.setTextColor(Color.parseColor("#AAAAAA"))
        }
        selectedView.setTextColor(Color.YELLOW)
        updateShutterForMode()
    }

    private fun updateShutterForMode() {
        // Visual update for video mode — shutter becomes record button
        val shutterInner = shutterButton.findViewWithTag<View>("inner")
        if (currentMode == CameraMode.VIDEO) {
            shutterInner?.setBackgroundColor(Color.parseColor("#D94F3D"))
        } else {
            shutterInner?.setBackgroundColor(Color.WHITE)
        }
    }

    private fun buildBottomControls() {
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(40))
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        rootLayout.addView(bottomBar, params)

        // Thumbnail (last photo)
        thumbnailView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).also {
                it.marginEnd = 0
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = createRoundedDrawable(Color.parseColor("#333333"), dp(27).toFloat())
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                }
            }
            setOnClickListener { openGallery() }
        }
        bottomBar.addView(thumbnailView)

        // Spacer
        bottomBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        // Shutter button
        shutterButton = buildShutterButton()
        bottomBar.addView(shutterButton)

        // Spacer
        bottomBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        // Flip camera button
        flipButton = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
            setImageDrawable(createFlipIcon())
            background = createRoundedDrawable(Color.parseColor("#33FFFFFF"), dp(27).toFloat())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { flipCamera() }
        }
        bottomBar.addView(flipButton)
    }

    private fun buildShutterButton(): FrameLayout {
        val outer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        }

        // Outer ring
        val ring = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(76), dp(76)).also {
                it.gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), Color.WHITE)
                setColor(Color.TRANSPARENT)
            }
        }
        outer.addView(ring)

        // Inner circle
        val inner = View(this).apply {
            tag = "inner"
            layoutParams = FrameLayout.LayoutParams(dp(64), dp(64)).also {
                it.gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }
        outer.addView(inner)

        outer.setOnClickListener {
            when (currentMode) {
                CameraMode.PHOTO, CameraMode.PORTRAIT -> takePicture()
                CameraMode.VIDEO -> toggleVideo()
                CameraMode.PANORAMA -> takePicture()
            }
        }
        return outer
    }

    private fun setupTouchListeners() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newZoom = (currentZoom * factor).coerceIn(1f, maxZoom)
                if (newZoom != currentZoom) {
                    currentZoom = newZoom
                    updateZoom()
                }
                return true
            }
        })

        textureView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.pointerCount == 1 && event.action == MotionEvent.ACTION_UP) {
                handleTapToFocus(event.x, event.y)
            }
            true
        }
    }

    private fun updateZoom() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

        val ratio = 1f / currentZoom
        val cropW = (sensorSize.width() * ratio).toInt()
        val cropH = (sensorSize.height() * ratio).toInt()
        val cropX = (sensorSize.width() - cropW) / 2
        val cropY = (sensorSize.height() - cropH) / 2
        val cropRegion = Rect(cropX, cropY, cropX + cropW, cropY + cropH)

        builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
        try {
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Zoom update failed", e)
        }
    }

    private fun handleTapToFocus(x: Float, y: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

        val viewWidth = textureView.width.toFloat()
        val viewHeight = textureView.height.toFloat()
        val focusSize = 0.1f

        val afX = ((x / viewWidth) * sensorSize.width()).toInt()
        val afY = ((y / viewHeight) * sensorSize.height()).toInt()
        val halfW = (sensorSize.width() * focusSize / 2).toInt()
        val halfH = (sensorSize.height() * focusSize / 2).toInt()

        val focusRect = Rect(
            (afX - halfW).coerceAtLeast(0),
            (afY - halfH).coerceAtLeast(0),
            (afX + halfW).coerceAtMost(sensorSize.width()),
            (afY + halfH).coerceAtMost(sensorSize.height())
        )

        val meteringRect = MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX)
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRect))
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRect))
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)

        try {
            session.capture(builder.build(), null, cameraHandler)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Focus failed", e)
        }

        focusView.showFocusAt(x, y)
    }

    private fun cycleFlash() {
        flashMode = when (flashMode) {
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
            FlashMode.OFF -> FlashMode.AUTO
        }
        flashButton.text = when (flashMode) {
            FlashMode.AUTO -> "⚡AUTO"
            FlashMode.ON -> "⚡ON"
            FlashMode.OFF -> "⚡OFF"
        }
        flashButton.setTextColor(when (flashMode) {
            FlashMode.OFF -> Color.WHITE
            else -> Color.YELLOW
        })
        updateFlashMode()
    }

    private fun updateFlashMode() {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val aeMode = when (flashMode) {
            FlashMode.AUTO -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            FlashMode.ON -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
            FlashMode.OFF -> CaptureRequest.CONTROL_AE_MODE_ON
        }
        builder.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
        try {
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Flash update failed", e)
        }
    }

    private fun flipCamera() {
        isFrontCamera = !isFrontCamera
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        openCamera()
    }

    // --- Camera2 lifecycle ---

    private fun openCamera() {
        if (!allPermissionsGranted()) {
            requestPermissions()
            return
        }
        try {
            cameraId = findCameraId()
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "openCamera failed", e)
        }
    }

    private fun findCameraId(): String {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val target = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT
                         else CameraCharacteristics.LENS_FACING_BACK
            if (facing == target) return id
        }
        return cameraManager.cameraIdList[0]
    }

    private fun createCameraPreviewSession() {
        try {
            val surfaceTexture = textureView.surfaceTexture ?: return
            surfaceTexture.setDefaultBufferSize(1920, 1080)
            val previewSurface = Surface(surfaceTexture)

            imageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.let { saveImage(it) }
            }, cameraHandler)

            val surfaces = listOf(previewSurface, imageReader!!.surface)

            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }

            cameraDevice!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        session.setRepeatingRequest(previewRequestBuilder!!.build(), null, cameraHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Preview failed", e)
                    }
                    runOnUiThread { loadLastThumbnail() }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Session config failed")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "createCameraPreviewSession failed", e)
        }
    }

    private fun takePicture() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val reader = imageReader ?: return

        mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                val aeMode = when (flashMode) {
                    FlashMode.AUTO -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    FlashMode.ON -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    FlashMode.OFF -> CaptureRequest.CONTROL_AE_MODE_ON
                }
                set(CaptureRequest.CONTROL_AE_MODE, aeMode)
                set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())
            }

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    runOnUiThread { loadLastThumbnail() }
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "takePicture failed", e)
        }
    }

    private fun toggleVideo() {
        // Placeholder — full video implementation would use MediaRecorder
        Toast.makeText(this, "Запись видео", Toast.LENGTH_SHORT).show()
    }

    private fun saveImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "IMG_$timestamp.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RuOS")
        }

        try {
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) }
            }
        } catch (e: Exception) {
            // Fallback to direct file write
            val dir = File(getExternalFilesDir(null), "RuOS")
            dir.mkdirs()
            FileOutputStream(File(dir, filename)).use { it.write(bytes) }
        }
    }

    private fun loadLastThumbnail() {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val cursor = contentResolver.query(
            uri, projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val imageUri = android.net.Uri.withAppendedPath(uri, id.toString())
                try {
                    val bmp = contentResolver.loadThumbnail(imageUri, android.util.Size(200, 200), null)
                    thumbnailView.setImageBitmap(bmp)
                } catch (e: Exception) {
                    Log.d(TAG, "No thumbnail: ${e.message}")
                }
            }
        }
    }

    private fun openGallery() {
        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            type = "image/*"
        })
    }

    private fun getJpegOrientation(): Int {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation
        return when (rotation) {
            Surface.ROTATION_0 -> 90
            Surface.ROTATION_90 -> 0
            Surface.ROTATION_180 -> 270
            Surface.ROTATION_270 -> 180
            else -> 90
        }
    }

    // --- Permissions ---
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (allPermissionsGranted()) {
                if (textureView.isAvailable) openCamera()
                else textureView.surfaceTextureListener = surfaceTextureListener
            } else {
                Toast.makeText(this, "Требуются разрешения для камеры", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // --- Lifecycle ---
    override fun onResume() {
        super.onResume()
        if (!allPermissionsGranted()) {
            requestPermissions()
            return
        }
        if (textureView.isAvailable) openCamera()
        else textureView.surfaceTextureListener = surfaceTextureListener
    }

    override fun onPause() {
        super.onPause()
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        mediaActionSound.release()
    }

    // --- Helpers ---
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createRoundedDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun createFlipIcon(): android.graphics.drawable.Drawable {
        val bitmap = Bitmap.createBitmap(dp(30), dp(30), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = dp(2).toFloat()
            style = Paint.Style.STROKE
        }
        val cx = dp(15).toFloat()
        val cy = dp(15).toFloat()
        val r = dp(10).toFloat()
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 30f, 300f, false, paint)
        // Arrow head
        paint.style = Paint.Style.FILL
        val path = Path().apply {
            moveTo(cx + r * 0.5f, cy - r)
            lineTo(cx + r, cy - r * 0.5f)
            lineTo(cx + r * 0.5f, cy - r * 1.5f)
            close()
        }
        canvas.drawPath(path, paint)
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }
}

// --- Focus overlay view ---
class FocusView(context: Context) : View(context) {
    private var focusX = 0f
    private var focusY = 0f
    private var alpha2 = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 2f * context.resources.displayMetrics.density
    }
    private val size = (80 * context.resources.displayMetrics.density)

    fun showFocusAt(x: Float, y: Float) {
        focusX = x
        focusY = y
        alpha2 = 1f
        invalidate()
        animate().alpha(0f).setDuration(800).setStartDelay(600)
            .withEndAction { alpha2 = 0f; invalidate() }
            .start()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (alpha2 > 0f) {
            paint.alpha = (255 * alpha2).toInt()
            val half = size / 2
            canvas.drawRect(focusX - half, focusY - half, focusX + half, focusY + half, paint)
            // Corner marks
            val mark = size * 0.2f
            // TL
            canvas.drawLine(focusX - half, focusY - half, focusX - half + mark, focusY - half, paint)
            canvas.drawLine(focusX - half, focusY - half, focusX - half, focusY - half + mark, paint)
        }
    }
}

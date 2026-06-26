package com.android.systemui.ruos.screenshot

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager

/**
 * RuOS global screenshot. Captures the display, saves a PNG to Pictures/Screenshots, and
 * shows the iOS-style floating thumbnail (bottom-left) — tap to mark up, swipe to dismiss.
 *
 * Trigger: call [takeScreenshot]. For testing it also listens for the broadcast
 *   adb shell am broadcast -a com.ruos.action.SCREENSHOT -p com.android.systemui
 * To wire the hardware combo (Power + Volume-Down), route PhoneWindowManager's
 * interceptScreenshotChord / ScreenshotHelper to this controller — see docs/Screenshots.md.
 *
 * The display capture uses the platform screen-capture API via reflection so it builds
 * against any AOSP 14 checkout; it is the real call path but tagged [device] (validate on
 * panther + check for avc denials). The thumbnail + markup pipeline are fully functional.
 */
class RuOSScreenshotController(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private var thumb: ScreenshotThumbnailView? = null

    private val trigger = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { takeScreenshot() }
    }

    fun start() {
        runCatching {
            context.registerReceiver(trigger, IntentFilter(ACTION_SCREENSHOT), Context.RECEIVER_EXPORTED)
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(trigger) }
        dismissThumb()
    }

    fun takeScreenshot() {
        val bmp = captureDisplay()
        if (bmp == null) { Log.w(TAG, "display capture unavailable on this build [device]"); return }
        val uri = saveToGallery(bmp)
        main.post { showThumbnail(bmp, uri) }
    }

    // ── capture ───────────────────────────────────────────────────────────────

    private fun captureDisplay(): Bitmap? {
        return runCatching {
            val dm = context.getSystemService(DisplayManager::class.java)
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
            val size = android.graphics.Point()
            @Suppress("DEPRECATION") display.getRealSize(size)

            // token = SurfaceControl.getInternalDisplayToken()
            val sc = Class.forName("android.view.SurfaceControl")
            val token = sc.getMethod("getInternalDisplayToken").invoke(null) ?: return null

            // args = ScreenCapture.DisplayCaptureArgs.Builder(token)
            //          .setSourceCrop(Rect).setSize(w,h).build()
            val capture = Class.forName("android.window.ScreenCapture")
            val builderCls = Class.forName("android.window.ScreenCapture\$DisplayCaptureArgs\$Builder")
            val builder = builderCls.getConstructor(android.os.IBinder::class.java).newInstance(token)
            builderCls.getMethod("setSourceCrop", Rect::class.java)
                .invoke(builder, Rect(0, 0, size.x, size.y))
            builderCls.getMethod("setSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(builder, size.x, size.y)
            val args = builderCls.getMethod("build").invoke(builder)
            val argsCls = Class.forName("android.window.ScreenCapture\$DisplayCaptureArgs")
            val buffer = capture.getMethod("captureDisplay", argsCls).invoke(null, args) ?: return null
            val asBitmap = buffer.javaClass.getMethod("asBitmap").invoke(buffer) as? Bitmap
            asBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
    }

    // ── save ────────────────────────────────────────────────────────────────────

    private fun saveToGallery(bmp: Bitmap): Uri? {
        val name = "Screenshot_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri).use { os ->
                os?.let { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return uri
    }

    // ── floating thumbnail ───────────────────────────────────────────────────────

    private fun showThumbnail(bmp: Bitmap, uri: Uri?) {
        dismissThumb()
        val preview = scaleForThumb(bmp)
        val view = ScreenshotThumbnailView(context, preview,
            onTap = { openMarkup(uri); dismissThumb() },
            onDismiss = { dismissThumb() })
        val d = context.resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.BOTTOM
            x = (16 * d).toInt(); y = (24 * d).toInt()
        }
        runCatching { wm.addView(view, lp); thumb = view; view.slideIn() }
        main.postDelayed({ dismissThumb() }, AUTO_DISMISS_MS)
    }

    private fun dismissThumb() {
        thumb?.let { v -> v.slideOut { runCatching { wm.removeView(v) } } }
        thumb = null
        main.removeCallbacksAndMessages(null)
    }

    private fun scaleForThumb(bmp: Bitmap): Bitmap {
        val d = context.resources.displayMetrics.density
        val targetW = (96 * d)
        val s = targetW / bmp.width
        return Bitmap.createScaledBitmap(bmp, targetW.toInt(), (bmp.height * s).toInt(), true)
    }

    private fun openMarkup(uri: Uri?) {
        if (uri == null) return
        val i = Intent().setClassName("com.ruos.screenshot", "com.ruos.screenshot.MarkupActivity")
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    companion object {
        private const val TAG = "RuOSScreenshot"
        private const val ACTION_SCREENSHOT = "com.ruos.action.SCREENSHOT"
        private const val AUTO_DISMISS_MS = 5000L
    }
}

package com.ruos.screenshot.scanner

import android.graphics.Bitmap

/**
 * Turns a photographed page into a clean "scanned" look: convert to luminance, then push it through
 * a contrast curve that whitens the paper and darkens the ink (a soft threshold around a mid grey).
 * The per-pixel tone map ([tone]) and luminance ([luma]) are pure so they can be unit-verified; only
 * the pixel loop touches Android.
 */
object ScanFilter {

    /** Rec. 601 luminance of an ARGB pixel, 0..255. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 77 + g * 150 + b * 29) shr 8
    }

    /**
     * Contrast curve: values below [low] clamp to black, above [high] clamp to white, and the middle
     * is linearly stretched — a gentle threshold that keeps anti-aliased text smooth instead of
     * jagged. Pure and monotic; verified against reference points.
     */
    fun tone(v: Int, low: Int = 80, high: Int = 180): Int {
        if (v <= low) return 0
        if (v >= high) return 255
        return ((v - low) * 255) / (high - low)
    }

    fun enhance(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val g = tone(luma(pixels[i]))
            pixels[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}

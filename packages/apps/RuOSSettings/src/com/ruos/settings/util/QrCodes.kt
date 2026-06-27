package com.ruos.settings.util

import android.graphics.Bitmap
import android.graphics.Color

/** Renders a QR payload to a crisp black-on-white Bitmap with a 4-module quiet zone. */
object QrCodes {

    fun render(text: String, sizePx: Int): Bitmap? {
        val matrix = runCatching { QrEncoder.encode(text) }.getOrNull() ?: return null
        val n = matrix.size
        val quiet = 4
        val total = n + quiet * 2
        val scale = (sizePx / total).coerceAtLeast(1)
        val dim = total * scale
        val bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        val px = IntArray(dim * dim) { Color.WHITE }
        for (r in 0 until n) for (c in 0 until n) {
            if (matrix[r][c]) {
                val x0 = (c + quiet) * scale; val y0 = (r + quiet) * scale
                for (y in y0 until y0 + scale) {
                    val base = y * dim
                    for (x in x0 until x0 + scale) px[base + x] = Color.BLACK
                }
            }
        }
        bmp.setPixels(px, 0, dim, 0, 0, dim, dim)
        return bmp
    }

    /** Build a standard Wi-Fi join payload (scanned by iOS/Android cameras). */
    fun wifiPayload(ssid: String, password: String, wpa: Boolean): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
            .replace(":", "\\:").replace("\"", "\\\"")
        return if (wpa && password.isNotEmpty())
            "WIFI:T:WPA;S:${esc(ssid)};P:${esc(password)};;"
        else "WIFI:T:nopass;S:${esc(ssid)};;"
    }
}

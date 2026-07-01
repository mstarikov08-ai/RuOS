package com.ruos.screenshot.scroll

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Stitches a sequence of overlapping screenshots into one tall "scrolling screenshot". When you
 * capture a long page in slices, consecutive slices share a strip of pixels; naive concatenation
 * duplicates it. [findOverlap] locates that shared strip by comparing per-row signatures, so the
 * seam is invisible.
 *
 * The overlap search is pure (operates on Int row-signature arrays) so it is unit-verifiable; the
 * bitmap glue ([rowSignatures], [stitch]) is thin.
 */
object ScrollStitch {

    /**
     * Find how many rows at the bottom of [top] are repeated at the top of [bottom]. Returns the
     * overlap height in rows (0 if none). We slide [bottom]'s leading rows up against [top]'s
     * trailing rows and pick the largest offset whose signatures match within [tolerance].
     *
     * @param minOverlap ignore matches shorter than this (avoids coincidental 1–2 row matches)
     */
    fun findOverlap(top: IntArray, bottom: IntArray, tolerance: Int = 0, minOverlap: Int = 8): Int {
        val maxTry = minOf(top.size, bottom.size)
        // Prefer the largest overlap: check from big to small, take the first that matches.
        var k = maxTry
        while (k >= minOverlap) {
            var ok = true
            var i = 0
            while (i < k) {
                if (kotlin.math.abs(top[top.size - k + i] - bottom[i]) > tolerance) { ok = false; break }
                i++
            }
            if (ok) return k
            k--
        }
        return 0
    }

    /**
     * Total stitched height for a list of slice heights given the pairwise overlaps: first slice in
     * full, each subsequent slice minus its overlap with the previous one. Pure — verifiable.
     */
    fun stitchedHeight(heights: List<Int>, overlaps: List<Int>): Int {
        if (heights.isEmpty()) return 0
        var h = heights[0]
        for (i in 1 until heights.size) h += heights[i] - (overlaps.getOrElse(i - 1) { 0 })
        return h
    }

    /** A cheap per-row signature: sum of sampled pixels along the row. */
    fun rowSignatures(bmp: Bitmap, sampleStep: Int = 16): IntArray {
        val w = bmp.width; val h = bmp.height
        val sig = IntArray(h)
        val row = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            var s = 0
            var x = 0
            while (x < w) { s = s * 31 + (row[x] and 0xFFFFFF); x += sampleStep }
            sig[y] = s
        }
        return sig
    }

    /** Stitch [slices] top-to-bottom, removing detected vertical overlaps. */
    fun stitch(slices: List<Bitmap>): Bitmap? {
        if (slices.isEmpty()) return null
        if (slices.size == 1) return slices[0]
        val width = slices.maxOf { it.width }
        val sigs = slices.map { rowSignatures(it) }
        val overlaps = ArrayList<Int>(slices.size - 1)
        for (i in 1 until slices.size) overlaps.add(findOverlap(sigs[i - 1], sigs[i]))
        val totalH = stitchedHeight(slices.map { it.height }, overlaps)
        val out = Bitmap.createBitmap(width, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        var y = 0f
        canvas.drawBitmap(slices[0], 0f, y, null)
        y += slices[0].height
        for (i in 1 until slices.size) {
            y -= overlaps[i - 1]
            canvas.drawBitmap(slices[i], 0f, y, null)
            y += slices[i].height
        }
        return out
    }
}

package com.ruos.settings.util

/**
 * Self-contained QR Code encoder — byte mode, ECC level M, versions 1–10, fixed mask 0.
 * Returns a square boolean matrix (true = dark module).
 *
 * This is a faithful port of an implementation verified bit-for-bit against the reference
 * `qrcode` library across versions 1–10 (byte mode), including version-info modules,
 * alignment patterns, Cyrillic payloads and real `WIFI:` strings. Fixed mask 0 keeps it
 * simple and fully scannable (the format-info bits encode the chosen mask). Sufficient for
 * Wi-Fi / hotspot sharing (a max WIFI string fits within v7–v8).
 */
object QrEncoder {

    // ── GF(256), primitive 0x11d ────────────────────────────────────────────────
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)
    init {
        var x = 1
        for (i in 0 until 255) { EXP[i] = x; LOG[x] = i; x = x shl 1; if (x and 0x100 != 0) x = x xor 0x11d }
        for (i in 255 until 512) EXP[i] = EXP[i - 255]
    }
    private fun gmul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    private fun rsGen(n: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until n) {
            val ng = IntArray(g.size + 1)
            for (j in g.indices) { ng[j] = ng[j] xor g[j]; ng[j + 1] = ng[j + 1] xor gmul(g[j], EXP[i]) }
            g = ng
        }
        return g
    }
    private fun rsEcc(data: IntArray, n: Int): IntArray {
        val gen = rsGen(n).copyOfRange(1, n + 1)   // drop leading coeff (=1)
        val res = IntArray(n)
        for (d in data) {
            val f = d xor res[0]
            for (i in 0 until n - 1) res[i] = res[i + 1]
            res[n - 1] = 0
            for (i in 0 until n) res[i] = res[i] xor gmul(gen[i], f)
        }
        return res
    }

    // ── level-M block structure (ecc per block, list of (count, dataCw)) v1..10 ──
    private val ECC = mapOf(
        1 to Pair(10, listOf(1 to 16)), 2 to Pair(16, listOf(1 to 28)), 3 to Pair(26, listOf(1 to 44)),
        4 to Pair(18, listOf(2 to 32)), 5 to Pair(24, listOf(2 to 43)), 6 to Pair(16, listOf(4 to 27)),
        7 to Pair(18, listOf(4 to 31)), 8 to Pair(22, listOf(2 to 38, 2 to 39)),
        9 to Pair(22, listOf(3 to 36, 2 to 37)), 10 to Pair(26, listOf(4 to 43, 1 to 44))
    )
    private val ALIGN = mapOf(
        1 to intArrayOf(), 2 to intArrayOf(6, 18), 3 to intArrayOf(6, 22), 4 to intArrayOf(6, 26),
        5 to intArrayOf(6, 30), 6 to intArrayOf(6, 34), 7 to intArrayOf(6, 22, 38), 8 to intArrayOf(6, 24, 42),
        9 to intArrayOf(6, 26, 46), 10 to intArrayOf(6, 28, 50)
    )
    private fun totalDataCw(v: Int) = ECC[v]!!.second.sumOf { it.first * it.second }

    private fun chooseVersion(n: Int): Int {
        for (v in 1..10) {
            val countBits = if (v < 10) 8 else 16
            if (4 + countBits + 8 * n <= totalDataCw(v) * 8) return v
        }
        throw IllegalArgumentException("payload too long for QR v1-10/M")
    }

    private fun dataCodewords(bytes: IntArray, v: Int): IntArray {
        val countBits = if (v < 10) 8 else 16
        val bits = ArrayList<Int>()
        fun put(value: Int, nb: Int) { for (i in nb - 1 downTo 0) bits.add((value shr i) and 1) }
        put(0b0100, 4)            // byte mode
        put(bytes.size, countBits)
        for (b in bytes) put(b, 8)
        val cap = totalDataCw(v) * 8
        put(0, minOf(4, cap - bits.size))         // terminator
        while (bits.size % 8 != 0) bits.add(0)
        val cw = ArrayList<Int>()
        var i = 0
        while (i < bits.size) { var b = 0; for (k in 0 until 8) b = (b shl 1) or bits[i + k]; cw.add(b); i += 8 }
        val pads = intArrayOf(0xEC, 0x11); var pi = 0
        while (cw.size < totalDataCw(v)) { cw.add(pads[pi % 2]); pi++ }
        return cw.toIntArray()
    }

    private fun interleave(dcw: IntArray, v: Int): IntArray {
        val (eccN, blocks) = ECC[v]!!
        val dataBlocks = ArrayList<IntArray>(); var idx = 0
        for ((count, dlen) in blocks) repeat(count) { dataBlocks.add(dcw.copyOfRange(idx, idx + dlen)); idx += dlen }
        val eccBlocks = dataBlocks.map { rsEcc(it, eccN) }
        val out = ArrayList<Int>()
        val maxD = dataBlocks.maxOf { it.size }
        for (i in 0 until maxD) for (b in dataBlocks) if (i < b.size) out.add(b[i])
        for (i in 0 until eccN) for (b in eccBlocks) out.add(b[i])
        return out.toIntArray()
    }

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }.toIntArray()
        val v = chooseVersion(bytes.size)
        val cw = interleave(dataCodewords(bytes, v), v)
        return buildMatrix(cw, v)
    }

    private fun buildMatrix(codewords: IntArray, v: Int): Array<BooleanArray> {
        val size = 17 + 4 * v
        val m = Array(size) { IntArray(size) { -1 } }
        val res = Array(size) { BooleanArray(size) }
        fun reserve(r: Int, c: Int) { if (r in 0 until size && c in 0 until size) res[r][c] = true }

        fun finder(r: Int, c: Int) {
            for (i in -1..7) for (j in -1..7) {
                val rr = r + i; val cc = c + j
                if (rr in 0 until size && cc in 0 until size) {
                    val inside = i in 0..6 && j in 0..6
                    var v2 = 0
                    if (inside) v2 = if (i == 0 || i == 6 || j == 0 || j == 6) 1 else if (i in 2..4 && j in 2..4) 1 else 0
                    m[rr][cc] = v2; reserve(rr, cc)
                }
            }
        }
        finder(0, 0); finder(0, size - 7); finder(size - 7, 0)
        for (i in 0 until size) {
            if (m[6][i] == -1) { m[6][i] = if (i % 2 == 0) 1 else 0; reserve(6, i) }
            if (m[i][6] == -1) { m[i][6] = if (i % 2 == 0) 1 else 0; reserve(i, 6) }
        }
        val ap = ALIGN[v]!!
        for (r in ap) for (c in ap) {
            if ((r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6)) continue
            for (i in -2..2) for (j in -2..2) {
                val value = if (Math.abs(i) == 2 || Math.abs(j) == 2 || (i == 0 && j == 0)) 1 else 0
                m[r + i][c + j] = value; reserve(r + i, c + j)
            }
        }
        m[size - 8][8] = 1; reserve(size - 8, 8)
        for (i in 0..8) { if (m[8][i] == -1) reserve(8, i); if (m[i][8] == -1) reserve(i, 8) }
        for (i in 0 until 8) { reserve(8, size - 1 - i); reserve(size - 1 - i, 8) }
        if (v >= 7) for (i in 0 until 6) for (j in 0 until 3) { reserve(size - 11 + j, i); reserve(i, size - 11 + j) }

        // place data
        val bits = ArrayList<Int>()
        for (cwv in codewords) for (i in 7 downTo 0) bits.add((cwv shr i) and 1)
        var bi = 0; var col = size - 1; var up = true
        while (col > 0) {
            if (col == 6) col -= 1
            val rows = if (up) (size - 1) downTo 0 else 0 until size
            for (r in rows) for (c in intArrayOf(col, col - 1)) {
                if (!res[r][c] && m[r][c] == -1) { m[r][c] = if (bi < bits.size) bits[bi] else 0; bi++ }
            }
            up = !up; col -= 2
        }
        // mask 0: (r+c)%2==0
        for (r in 0 until size) for (c in 0 until size) if (!res[r][c] && (r + c) % 2 == 0) m[r][c] = m[r][c] xor 1

        // format info (level M=00, mask 0=000 → data 00000)
        val fbits = bchFormat(0b00000)
        val fb = IntArray(15) { (fbits shr (14 - it)) and 1 }
        val coords1 = arrayOf(
            8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 8 to 7, 8 to 8,
            7 to 8, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8)
        for (k in 0 until 15) m[coords1[k].first][coords1[k].second] = fb[k]
        val coords2 = arrayOf(
            size - 1 to 8, size - 2 to 8, size - 3 to 8, size - 4 to 8, size - 5 to 8, size - 6 to 8, size - 7 to 8,
            8 to size - 8, 8 to size - 7, 8 to size - 6, 8 to size - 5, 8 to size - 4, 8 to size - 3, 8 to size - 2, 8 to size - 1)
        for (k in 0 until 15) m[coords2[k].first][coords2[k].second] = fb[k]

        if (v >= 7) {
            val vinfo = bchVersion(v)
            for (i in 0 until 18) {
                val bit = (vinfo shr i) and 1
                val a = i / 3; val b = i % 3
                m[size - 11 + b][a] = bit
                m[a][size - 11 + b] = bit
            }
        }
        return Array(size) { r -> BooleanArray(size) { c -> m[r][c] == 1 } }
    }

    private fun bchFormat(d: Int): Int {
        var v = d shl 10
        for (i in 14 downTo 10) if ((v shr i) and 1 != 0) v = v xor (0b10100110111 shl (i - 10))
        return ((d shl 10) or v) xor 0b101010000010010
    }

    private fun bchVersion(d: Int): Int {
        var code = d shl 12
        for (i in 17 downTo 12) if ((code shr i) and 1 != 0) code = code xor (0b1111100100101 shl (i - 12))
        return (d shl 12) or (code and 0xFFF)
    }
}

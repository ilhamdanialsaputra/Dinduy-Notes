package app.dinduy

import android.app.Application
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// =====================================================================================
//  Convert to Note: PDF (teks asli) -> catatan native Dinduy (paragraf, judul, daftar, tabel).
//
//  Prinsip (v2): LOKAL DULU, Groq SEKEDAR JURI untuk yang benar-benar ambigu.
//   1. Teks diekstrak lokal (PDFBox) per baris + posisi tiap sel. Halaman dua kolom dipisah, header/footer/nomor
//      halaman dibuang, ligatur dan tanda sambung diperbaiki.
//   2. Tabel dikenali dari geometri (kolom sejajar), daftar dari penanda + indentasi, paragraf dari jarak baris,
//      judul dari ukuran huruf SELURUH dokumen. Semua pasti, tanpa Groq.
//   3. Hanya baris pendek yang ambigu (mis. tebal ukuran isi, HURUF BESAR, rata tengah) yang dikelompokkan per
//      GAYA HURUF. Groq menerima beberapa contoh tiap gaya dalam SATU permintaan kecil dan menjawab "id kode".
//      Seluruh baris bergaya sama otomatis mendapat keputusan sama = konsisten.
//   4. Groq tidak pernah menulis ulang teks. Catatan dirakit dari teks asli, jadi kata tidak mungkin berubah.
//   5. Bila Groq gagal / kena limit, gaya yang ambigu diputuskan aturan lokal. Konversi tetap selesai.
//  Hasil hanya berisi: paragraf, judul, daftar, tabel (tanpa kutipan / kode / garis / gambar sisipan).
// =====================================================================================

// ---------------------------------------------------------------- model baris

class CLine(
    val t: String,           // teks baris; '¦' (menggantikan satu spasi) = jarak lebar antar sel
    val sp: List<Sp>,        // tebal / miring dari jenis huruf PDF
    val sz: Float,           // ukuran huruf (pt)
    val x: Float,            // posisi kiri (pt)
    val y: Float,            // posisi atas (pt)
    val page: Int,
    val cs: FloatArray       // pasangan [awal, akhir] tiap sel; ukuran = 2 * jumlah sel
) {
    var xe = 0f
    var bold = false
    var ital = false
    var rel = 1f
    var gap = false          // jarak kosong di atasnya lebih besar dari biasa
    var indent = false
    var center = false
    var short = false
    var noise = false        // nomor halaman / header / footer berulang (dibuang)
    var foot = false         // catatan kaki (huruf kecil di bagian bawah halaman)
    var col = 0              // 0 lebar penuh, 1 kolom kiri, 2 kolom kanan
    var lm = 0f
    var rm = 0f
    var mk = 0               // penanda butir: 1 simbol, 2 angka, 3 huruf
    var wp = IntArray(0)     // indeks awal tiap kata di [t]
    var wa = FloatArray(0)   // x awal tiap kata
    var wb = FloatArray(0)   // x akhir tiap kata
    val cells: Int get() = cs.size / 2
}

private fun fixChar(c: Char, start: Boolean): String = when {
    c == 'ﬁ' -> "fi"
    c == 'ﬂ' -> "fl"
    c == 'ﬀ' -> "ff"
    c == 'ﬃ' -> "ffi"
    c == 'ﬄ' -> "ffl"
    c == 'ﬅ' || c == 'ﬆ' -> "st"
    c == '¦' -> "|"
    c == ' ' || c == ' ' || c == ' ' || c == ' ' || c == ' ' || c == ' ' -> " "
    c == '­' || c == '​' || c == '‌' || c == '‍' || c == '﻿' -> ""
    c in ''..'' -> if (start) "•" else ""
    c < ' ' -> ""
    else -> c.toString()
}

private class LineGrab : PDFTextStripper() {
    val out = ArrayList<CLine>()
    private val sb = StringBuilder()
    private val bf = ArrayList<Boolean>()
    private val itf = ArrayList<Boolean>()
    private val bounds = ArrayList<Float>()
    private val wps = ArrayList<Int>()
    private val wxa = ArrayList<Float>()
    private val wxb = ArrayList<Float>()
    private var sz = 0f
    private var x0 = 0f
    private var y0 = 0f
    private var lastEnd = -1f
    private var pg = 0

    init { setSortByPosition(true) }

    private fun styleOf(tp: TextPosition): Pair<Boolean, Boolean> {
        var b = false
        var i = false
        try {
            val f = tp.font
            val n = (f?.name ?: "").lowercase()
            b = n.contains("bold") || n.contains("black") || n.contains("heavy") || n.contains("semibold") || n.contains("demi")
            i = n.contains("italic") || n.contains("oblique")
            val d = f?.fontDescriptor
            if (d != null) {
                if (d.isItalic) i = true
                if (d.isForceBold || d.fontWeight >= 700f) b = true
            }
        } catch (e: Throwable) { }
        return b to i
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (textPositions.isEmpty()) return
        val first = textPositions[0]
        if (sb.isEmpty()) {
            x0 = first.xDirAdj
            y0 = first.yDirAdj
            pg = currentPageNo - 1
        } else if (lastEnd >= 0f && sb[sb.length - 1] == ' ') {
            val s = if (first.fontSizeInPt > 0f) first.fontSizeInPt else 10f
            if (first.xDirAdj - lastEnd > max(8f, s * 1.1f)) {
                sb.setCharAt(sb.length - 1, '¦')
                bounds.add(lastEnd); bounds.add(first.xDirAdj)
            }
        }
        val ws = sb.length
        for (tp in textPositions) {
            val u = tp.unicode ?: continue
            val st = styleOf(tp)
            for (ch in u) {
                for (c2 in fixChar(ch, sb.isEmpty())) { sb.append(c2); bf.add(st.first); itf.add(st.second) }
            }
            if (tp.fontSizeInPt > sz) sz = tp.fontSizeInPt
        }
        val last = textPositions[textPositions.size - 1]
        lastEnd = last.xDirAdj + last.widthDirAdj
        if (sb.length > ws) { wps.add(ws); wxa.add(first.xDirAdj); wxb.add(lastEnd) }
    }

    override fun writeWordSeparator() { sb.append(' '); bf.add(false); itf.add(false) }

    override fun writeLineSeparator() { flushLine() }

    override fun writePageEnd() {
        flushLine()
        super.writePageEnd()
    }

    private fun ranges(a: Int, b: Int, f: List<Boolean>, k: Int, into: MutableList<Sp>) {
        var s = -1
        var lastNw = -1
        for (i in a until b) {
            val c = sb[i]
            if (c == ' ' || c == '¦') continue
            if (f[i]) { if (s < 0) s = i }
            else if (s >= 0) { into.add(Sp(s - a, lastNw + 1 - a, k)); s = -1 }
            lastNw = i
        }
        if (s >= 0) into.add(Sp(s - a, lastNw + 1 - a, k))
    }

    private fun flushLine() {
        if (sb.isEmpty()) { bounds.clear(); wps.clear(); wxa.clear(); wxb.clear(); return }
        var a = 0
        var b = sb.length
        while (a < b && (sb[a] == ' ' || sb[a] == '¦')) a++
        while (b > a && (sb[b - 1] == ' ' || sb[b - 1] == '¦')) b--
        if (b > a) {
            val sp = ArrayList<Sp>()
            ranges(a, b, bf, K.B, sp)
            ranges(a, b, itf, K.I, sp)
            val cs = FloatArray(bounds.size + 2)
            cs[0] = x0
            for (k in bounds.indices) cs[k + 1] = bounds[k]
            cs[cs.size - 1] = lastEnd
            val l = CLine(sb.substring(a, b), sp, sz, x0, y0, pg, cs)
            l.xe = lastEnd
            val nw = wps.size
            l.wp = IntArray(nw) { wps[it] - a }
            l.wa = FloatArray(nw) { wxa[it] }
            l.wb = FloatArray(nw) { wxb[it] }
            out.add(l)
        }
        sb.setLength(0); bf.clear(); itf.clear(); bounds.clear(); wps.clear(); wxa.clear(); wxb.clear()
        sz = 0f; lastEnd = -1f
    }
}

private class Extracted(val lines: List<CLine>, val title: String?, val body: Float, val pitch: Float)

private val RX_PGNUM = Regex("^(page|halaman|hal\\.?|hlm\\.?)?\\s*\\d{1,4}(\\s*(of|dari|/)\\s*\\d{1,4})?$", RegexOption.IGNORE_CASE)
private val RX_DIG = Regex("\\d+")
private val RX_GLYPH = Regex("^[•●▪◦·∙‣⁃■□○◆►▶➢➤✓✔]\\s*")
private val RX_ASCII = Regex("^[-–—*+]\\s+")
private val RX_NUM = Regex("^\\(?(\\d{1,3})[.)]\\s+")
private val RX_LET = Regex("^\\(?([A-Za-z])[.)]\\s+")
private val TERM = Regex("[.!?…:;\"”’)\\]]$")
private val RX_SENT = Regex("[.!?…][\"”’)\\]]?$")
private val LEAD = Regex("(\\.\\s?){4,}|…{2,}")
private val RX_HDR = Regex("^((\\d+(\\.\\d+){0,3}\\.?)\\s+\\S|(bab|chapter|bagian|section|lampiran|appendix|pasal|part)\\s+[\\dIVXLC]+\\b)", RegexOption.IGNORE_CASE)
private val RX_HNUM = Regex("^(\\d+(\\.\\d+)*\\.?|[IVX]+\\.)\\s+\\S")
private val RX_NUMC = Regex("^[-+−(]?\\s*(Rp\\.?|\\u0024|€|£)?\\s*\\d[\\d.,\\s]*\\s*(%|\\)|x|jt|rb)?$", RegexOption.IGNORE_CASE)
private val RX_TOCN = Regex("^(\\d{1,4}|[ivxlcdm]{1,6})$", RegexOption.IGNORE_CASE)

private fun cover(l: CLine, k: Int): Float {
    var tot = 0
    var hit = 0
    for (i in l.t.indices) {
        val c = l.t[i]
        if (c == ' ' || c == '¦') continue
        tot++
        if (l.sp.any { it.k == k && it.s <= i && it.e > i }) hit++
    }
    return if (tot == 0) 0f else hit.toFloat() / tot
}

private fun modeX(ls: List<CLine>): Float {
    val w = HashMap<Int, Int>()
    for (l in ls) { val k = Math.round(l.x / 3f); w[k] = (w[k] ?: 0) + l.t.length }
    return (w.maxByOrNull { it.value }?.key ?: 0) * 3f
}

private fun edgeX(ls: List<CLine>): Float? {
    val xs = ls.filter { it.t.length > 25 }.map { it.xe }.sorted()
    if (xs.size < 5) return null
    return xs[(xs.size * 9 / 10).coerceAtMost(xs.size - 1)]
}

/** Simbol / nomor butir yang terpisah jarak lebar dari teksnya ("•      teks") bukan kolom tabel: gabungkan. */
private fun mergeMarker(l: CLine): CLine {
    if (l.cells < 2) return l
    val i = l.t.indexOf('¦')
    if (i <= 0) return l
    val head = l.t.substring(0, i).trim()
    val hs = "$head "
    val ok = head.length <= 4 && (RX_GLYPH.matches(head) || RX_ASCII.containsMatchIn(hs) || RX_NUM.containsMatchIn(hs) || RX_LET.containsMatchIn(hs))
    if (!ok) return l
    val cs = FloatArray(l.cs.size - 2)
    cs[0] = l.cs[0]
    for (k in 3 until l.cs.size) cs[k - 2] = l.cs[k]
    val n = CLine(l.t.substring(0, i) + " " + l.t.substring(i + 1), l.sp, l.sz, l.x, l.y, l.page, cs)
    n.xe = l.xe
    n.wp = l.wp; n.wa = l.wa; n.wb = l.wb
    return n
}

// ---------------------------------------------------------------- halaman dua kolom

private fun gapAt(l: CLine, g: Float): Int {
    for (i in 0 until l.cells - 1) if (l.cs[2 * i + 1] <= g + 1f && l.cs[2 * i + 2] >= g - 1f) return i
    return -1
}

/** Potong baris di sel [k0]..[k1] (inklusif), lengkap dengan gaya dan posisinya. */
private fun sub(l: CLine, k0: Int, k1: Int): CLine {
    val parts = l.t.split('¦')
    var a = 0
    for (k in 0 until k0) a += parts[k].length + 1
    var b = a
    for (k in k0..k1) b += parts[k].length + (if (k < k1) 1 else 0)
    val sp = ArrayList<Sp>()
    for (s in l.sp) {
        val s0 = max(s.s, a)
        val e0 = min(s.e, b)
        if (e0 > s0) sp.add(Sp(s0 - a, e0 - a, s.k))
    }
    val cs = l.cs.copyOfRange(2 * k0, 2 * k1 + 2)
    val n = CLine(l.t.substring(a, b), sp, l.sz, cs[0], l.y, l.page, cs)
    n.xe = cs[cs.size - 1]
    return n
}

/** Dua kolom: bacaan kiri dulu baru kanan; baris selebar halaman (judul, abstrak) memisahkan blok. */
private fun splitCols(ls: List<CLine>, pw: Float): List<CLine> {
    if (ls.size < 14 || pw < 100f) return ls
    var bg = -1f
    var bs = Int.MIN_VALUE
    var g = pw * .36f
    while (g <= pw * .64f) {
        var left = 0; var right = 0; var cross = 0; var split = 0; var wide = 0
        for (l in ls) {
            if (l.xe <= g + 1f) left++
            else if (l.x >= g - 1f) right++
            else {
                val i = gapAt(l, g)
                if (i < 0) cross++
                else {
                    split++
                    if (l.cs[2 * i + 1] - l.x >= pw * .15f && l.xe - l.cs[2 * i + 2] >= pw * .15f) wide++
                }
            }
        }
        if (cross * 10 <= ls.size && wide >= 5 && wide * 10 >= split * 6 && left + split >= 6 && right + split >= 6) {
            val sc = wide * 100 - cross
            if (sc > bs) { bs = sc; bg = g }
        }
        g += 3f
    }
    if (bg < 0f) return ls
    val out = ArrayList<CLine>(ls.size + 8)
    val lq = ArrayList<CLine>()
    val rq = ArrayList<CLine>()
    fun flush() { out.addAll(lq); out.addAll(rq); lq.clear(); rq.clear() }
    for (l in ls) {
        if (l.xe <= bg + 1f) { l.col = 1; lq.add(l) }
        else if (l.x >= bg - 1f) { l.col = 2; rq.add(l) }
        else {
            val i = gapAt(l, bg)
            if (i < 0) { flush(); l.col = 0; out.add(l) }
            else {
                val a = sub(l, 0, i)
                val b = sub(l, i + 1, l.cells - 1)
                a.col = 1; b.col = 2
                lq.add(a); rq.add(b)
            }
        }
    }
    flush()
    return out
}

/** Potong baris di jarak antarkata nomor [cuts] (jadi '¦') dan hitung ulang posisi sel. */
private fun recut(l: CLine, cuts: Set<Int>): CLine {
    val sb = StringBuilder(l.t)
    for (k in cuts) { val i = l.wp[k] - 1; if (i in 0 until sb.length && sb[i] == ' ') sb.setCharAt(i, '¦') }
    val cl = ArrayList<Float>()
    var st = l.wa[0]
    for (k in 1 until l.wp.size) {
        val i = l.wp[k] - 1
        if (i in 0 until sb.length && sb[i] == '¦') { cl.add(st); cl.add(l.wb[k - 1]); st = l.wa[k] }
    }
    cl.add(st); cl.add(l.wb[l.wp.size - 1])
    val n = CLine(sb.toString(), l.sp, l.sz, l.x, l.y, l.page, cl.toFloatArray())
    n.xe = l.xe; n.col = l.col; n.wp = l.wp; n.wa = l.wa; n.wb = l.wb
    return n
}

private fun isMarkerText(head: String): Boolean {
    if (head.isEmpty() || head.length > 4) return false
    val hs = "$head "
    return RX_GLYPH.matches(head) || RX_ASCII.containsMatchIn(hs) || RX_NUM.containsMatchIn(hs) || RX_LET.containsMatchIn(hs)
}

private fun crosses(l: CLine, gx: Float): Boolean {
    for (k in l.wp.indices) if (l.wa[k] < gx - 1f && l.wb[k] > gx + 1f) return true
    return false
}

/**
 * Kolom tabel yang jaraknya sempit atau tidak sama persis di tiap baris (rata kanan / tengah): cari SALURAN KOSONG vertikal,
 * yaitu posisi x yang jatuh di jarak antarkata pada >= 3 baris dan hampir tidak dilintasi kata di antara baris-baris itu.
 * Baris di sekitarnya (judul, baris pendek) dipotong di saluran yang sama, jadi seluruh tabel terbaca utuh.
 */
private fun reseg(raw: List<CLine>): List<CLine> {
    val out = ArrayList<CLine>(raw)
    val W = 1600
    for (iter in 0 until 3) {
        val groups = HashMap<Int, ArrayList<Int>>()
        for (i in out.indices) groups.getOrPut(out[i].page * 3 + out[i].col) { ArrayList() }.add(i)
        var changed = false
        for ((_, ix) in groups) {
            if (ix.size < 3) continue
            val ents = ArrayList<FloatArray>()   // [awal, akhir, y]
            val arr = IntArray(W + 2)
            for (i in ix) {
                val l = out[i]
                val thr = max(6f, l.sz * .55f)
                for (k in 0 until l.wp.size - 1) {
                    val gs = l.wb[k]
                    val ge = l.wa[k + 1]
                    if (ge - gs < thr) continue
                    ents.add(floatArrayOf(gs, ge, l.y))
                    val a0 = max(0, gs.toInt())
                    val b0 = min(W, ge.toInt() + 1)
                    for (x in a0..b0) arr[x]++
                }
            }
            if (ents.size < 3) continue
            var x = 0
            while (x <= W) {
                if (arr[x] < 3) { x++; continue }
                var y = x
                var mx = 0
                while (y <= W && arr[y] >= 3) { if (arr[y] > mx) mx = arr[y]; y++ }
                var p0 = -1
                var p1 = -1
                for (z in x until y) if (arr[z] == mx) { if (p0 < 0) p0 = z; p1 = z }
                val gx = (p0 + p1) / 2f + .5f
                x = y
                var ylo = Float.MAX_VALUE
                var yhi = -Float.MAX_VALUE
                for (e in ents) if (e[0] <= gx && e[1] >= gx) { ylo = min(ylo, e[2]); yhi = max(yhi, e[2]) }
                if (ylo > yhi) continue
                var tot = 0
                var cr = 0
                for (i in ix) { val l = out[i]; if (l.y < ylo - .5f || l.y > yhi + .5f) continue; tot++; if (crosses(l, gx)) cr++ }
                if (tot < 3 || cr * 8 > tot) continue
                for (i in ix) {
                    val l = out[i]
                    if (l.wp.size < 2) continue
                    val win = l.sz * 1.9f
                    if (l.y < ylo - win || l.y > yhi + win) continue
                    for (k in 0 until l.wp.size - 1) {
                        if (l.wb[k] <= gx + 1f && l.wa[k + 1] >= gx - 1f && l.wa[k + 1] - l.wb[k] >= 2.5f) {
                            val ci = l.wp[k + 1] - 1
                            if (ci in 0 until l.t.length && l.t[ci] == ' ' && !isMarkerText(l.t.substring(0, ci).trim())) {
                                out[i] = recut(l, setOf(k + 1)); changed = true
                            }
                            break
                        }
                    }
                }
            }
        }
        if (!changed) break
    }
    return out
}

// ---------------------------------------------------------------- ekstraksi + statistik dokumen

private fun extract(ctx: Context, file: File, check: () -> Unit, progress: (Int, Int) -> Unit): Extracted {
    PDFBoxResourceLoader.init(ctx)
    val pages = ArrayList<List<CLine>>()
    val pws = ArrayList<Float>()
    val phs = ArrayList<Float>()
    var title: String? = null
    // buku tebal: simpan struktur PDF di file sementara, bukan di heap (kalau tidak, OOM -> aplikasi tertutup)
    val d = try { PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()) } catch (e: Throwable) { PDDocument.load(file) }
    try {
        val n = d.numberOfPages
        try { title = d.documentInformation?.title } catch (e: Throwable) { }
        for (p in 0 until n) {
            check()
            var w = 0f
            var h = 0f
            try { val bx = d.getPage(p).mediaBox; w = bx.width; h = bx.height } catch (e: Throwable) { }
            val g = LineGrab()
            g.startPage = p + 1
            g.endPage = p + 1
            try { g.getText(d) } catch (e: Throwable) { }
            pages.add(g.out); pws.add(w); phs.add(h)
            if (p % 4 == 0 || p == n - 1) progress(p + 1, n)
            if (p % 40 == 39) System.gc()
        }
    } finally {
        try { d.close() } catch (e: Throwable) { }
    }
    val raw0 = ArrayList<CLine>()
    for (p in pages.indices) raw0.addAll(splitCols(pages[p].map { mergeMarker(it) }, pws[p]))
    val raw = reseg(raw0)
    if (raw.isEmpty()) return Extracted(raw, title, 10f, 12f)
    for (l in raw) { l.bold = cover(l, K.B) >= .9f; l.ital = cover(l, K.I) >= .9f }

    // nomor halaman dan header / footer berulang
    val byPage = LinkedHashMap<Int, ArrayList<Int>>()
    for (i in raw.indices) byPage.getOrPut(raw[i].page) { ArrayList() }.add(i)
    fun key(l: CLine) = RX_DIG.replace(l.t, "#").lowercase()
    fun edge(ix: ArrayList<Int>): List<Int> {
        val h = phs.getOrElse(raw[ix[0]].page) { 0f }
        val set = LinkedHashSet<Int>()
        set.addAll(ix.take(2)); set.addAll(ix.takeLast(2))
        if (h > 0f) for (i in ix) { val y = raw[i].y; if (y < h * .07f || y > h * .93f) set.add(i) }
        return set.toList()
    }
    val rep = HashMap<String, Int>()
    for ((_, ix) in byPage) for (k in edge(ix).map { key(raw[it]) }.toSet()) rep[k] = (rep[k] ?: 0) + 1
    val np = byPage.size
    for ((_, ix) in byPage) for (i in edge(ix)) {
        val l = raw[i]
        val repeated = np >= 4 && (rep[key(l)] ?: 0) >= max(3, np * 3 / 10) && l.t.length < 120
        if (RX_PGNUM.matches(l.t.trim()) || repeated) l.noise = true
    }
    val L = raw.filter { !it.noise }
    if (L.isEmpty()) return Extracted(raw, title, 10f, 12f)

    // ukuran isi
    val sizeW = HashMap<Int, Int>()
    for (l in L) { val k = Math.round(l.sz * 2f); sizeW[k] = (sizeW[k] ?: 0) + l.t.length }
    val body = ((sizeW.maxByOrNull { it.value }?.key ?: 20) / 2f).coerceAtLeast(4f)
    for (l in L) l.rel = l.sz / body

    // margin kiri / kanan per (halaman, kolom); halaman yang terlalu sedikit teks memakai nilai seluruh dokumen
    val grp = HashMap<Int, ArrayList<CLine>>()
    val gcol = HashMap<Int, ArrayList<CLine>>()
    for (l in L) {
        grp.getOrPut(l.page * 3 + l.col) { ArrayList() }.add(l)
        gcol.getOrPut(l.col) { ArrayList() }.add(l)
    }
    val gl = HashMap<Int, Float>()
    val gr = HashMap<Int, Float>()
    for ((c, ls) in gcol) { gl[c] = modeX(ls); gr[c] = edgeX(ls) ?: ls.maxOf { it.xe } }
    val kl = HashMap<Int, Float>()
    val kr = HashMap<Int, Float>()
    for ((k, ls) in grp) {
        val c = k % 3
        if (ls.sumOf { it.t.length } >= 300) { kl[k] = modeX(ls); kr[k] = edgeX(ls) ?: gr[c]!! }
        else { kl[k] = gl[c]!!; kr[k] = gr[c]!! }
    }
    for (l in L) { val k = l.page * 3 + l.col; l.lm = kl[k]!!; l.rm = kr[k]!! }

    // jarak baris tipikal
    val dys = ArrayList<Float>()
    for (i in 1 until L.size) {
        val a = L[i - 1]
        val b = L[i]
        if (a.page == b.page && a.col == b.col) { val dy = b.y - a.y; if (dy > 0f && dy < body * 5f) dys.add(dy) }
    }
    dys.sort()
    val pitch = if (dys.isEmpty()) body * 1.25f else dys[dys.size / 2]

    val ymin = HashMap<Int, Float>()
    val ymax = HashMap<Int, Float>()
    for (l in L) { ymin[l.page] = min(ymin[l.page] ?: l.y, l.y); ymax[l.page] = max(ymax[l.page] ?: l.y, l.y) }

    for (i in L.indices) {
        val l = L[i]
        val p: CLine? = if (i > 0) L[i - 1] else null
        val same = p != null && p.page == l.page && p.col == l.col
        l.gap = p != null && same && (l.y - p.y) > pitch * 1.35f
        l.indent = l.x - l.lm > body * 1.2f
        l.short = l.rm - l.lm > 0f && (l.xe - l.lm) < .72f * (l.rm - l.lm)
        val mid = (l.x + l.xe) / 2f
        l.center = l.x - l.lm > body * 1.5f && l.rm - l.xe > body * 1.5f && abs(mid - (l.lm + l.rm) / 2f) < body * 1.2f
        val y0 = ymin[l.page] ?: l.y
        val y1 = ymax[l.page] ?: l.y
        l.foot = l.rel < .88f && l.y > y0 + .55f * (y1 - y0)
        var mk = if (RX_GLYPH.containsMatchIn(l.t) || RX_ASCII.containsMatchIn(l.t)) 1 else if (RX_NUM.containsMatchIn(l.t)) 2 else 0
        // angka di tengah kalimat bukan butir
        if (mk == 2 && p != null && same && !TERM.containsMatchIn(p.t) && !p.short && p.mk == 0) mk = 0
        l.mk = mk
    }
    // butir huruf (a. b. c.) hanya bila ada tetangga berurutan di margin yang sama
    val cand = ArrayList<Int>()
    for (i in L.indices) if (L[i].mk == 0 && RX_LET.containsMatchIn(L[i].t)) cand.add(i)
    if (cand.size in 2..4000) {
        for (a in cand) {
            val ca = RX_LET.find(L[a].t)!!.groupValues[1][0]
            for (b in cand) {
                if (b == a || abs(b - a) > 60) continue
                val cb = RX_LET.find(L[b].t)!!.groupValues[1][0]
                if (abs(L[a].x - L[b].x) <= 4f && (cb.code == ca.code + 1 || cb.code == ca.code - 1)) { L[a].mk = 3; break }
            }
        }
    }
    return Extracted(raw, title, body, pitch)
}

// ---------------------------------------------------------------- unit, tabel

private class U(val ls: List<CLine>) {
    var tb: Blk? = null
    var rows: List<String>? = null
    var role = '?'              // P H L X (teks), T tabel, R daftar isi, ? belum diputuskan
    var pg0 = ls.firstOrNull()?.page ?: 0
    var pg1 = ls.lastOrNull()?.page ?: 0
}

private fun unitText(u: U): String = u.ls.joinToString(" ") { it.t.replace('¦', ' ') }

private fun isCaps(t: String): Boolean {
    var n = 0
    for (c in t) { if (c.isLowerCase()) return false; if (c.isLetter()) n++ }
    return n >= 3
}

private fun sentEnd(t: String) = RX_SENT.containsMatchIn(t.trimEnd())

private class Tab(val blk: Blk?, val toc: List<String>?, val end: Int, val bands: List<FloatArray>? = null)

/** Tabel dari geometri: baris berjarak lebar yang kolomnya sejajar. Sel yang membungkus ke baris bawah digabung. */
private fun tryTable(L: List<CLine>, i0: Int, pitch: Float): Tab? {
    val f = L[i0]
    val rows = ArrayList<CLine>()
    var j = i0
    while (j < L.size) {
        val l = L[j]
        if (l.page != f.page || l.col != f.col) break
        if (j > i0 && l.y - L[j - 1].y > pitch * 2.6f) break
        if (l.cells < 2) {
            if (l.cells == 1 && rows.isNotEmpty() && l.mk == 0 && l.y - L[j - 1].y <= pitch * 1.6f) { rows.add(l); j++; continue }
            break
        }
        rows.add(l); j++
    }
    val strict = rows.filter { it.cells >= 2 }
    if (strict.size < 2) return null
    val freq = HashMap<Int, Int>()
    for (r in strict) freq[r.cells] = (freq[r.cells] ?: 0) + 1
    // jumlah sel baris penuh = yang terbesar yang muncul >= 2 kali (baris lanjutan punya sel lebih sedikit)
    var m = 0
    for ((k, v) in freq) if (v >= 2 && k > m) m = k
    if (m == 0) { var mc = -1; for ((k, v) in freq) if (v > mc || (v == mc && k > m)) { m = k; mc = v } }
    val full = strict.filter { it.cells == m }
    if (full.size < 2 || m > 12) return null

    // pita kolom dari baris yang jumlah selnya paling umum; baris judul (sering rata tengah) boleh tidak sejajar
    fun near(v: List<Float>): Float {
        val sv = v.sorted()
        val med = sv[sv.size / 2]
        return v.count { abs(it - med) <= 7f }.toFloat() / v.size
    }
    fun bandsOf(src: List<CLine>): List<FloatArray>? {
        val iv = ArrayList<FloatArray>()
        for (r in src) for (c in 0 until m) iv.add(floatArrayOf(r.cs[2 * c], r.cs[2 * c + 1]))
        val bs = ArrayList<FloatArray>()
        for (a in iv.sortedBy { it[0] }) {
            val b = bs.lastOrNull()
            if (b != null && a[0] <= b[1]) { if (a[1] > b[1]) b[1] = a[1] } else bs.add(floatArrayOf(a[0], a[1]))
        }
        if (bs.size < 2 || bs.size > 12) return null
        for (b in bs) {
            val st = ArrayList<Float>(); val en = ArrayList<Float>(); val ce = ArrayList<Float>()
            for (r in src) for (c in 0 until m) {
                val s0 = r.cs[2 * c]; val e0 = r.cs[2 * c + 1]; val mid = (s0 + e0) / 2f
                if (mid >= b[0] && mid <= b[1]) { st.add(s0); en.add(e0); ce.add(mid) }
            }
            if (st.size < 2) return null
            if (max(near(st), max(near(en), near(ce))) < .75f) return null
        }
        return bs
    }
    val bands = bandsOf(full) ?: (if (full.size >= 3) bandsOf(full.drop(1)) else null) ?: return null
    fun bandOf(s: Float, e: Float): Int {
        var best = -1
        var bo = 0f
        for (k in bands.indices) { val o = min(e, bands[k][1]) - max(s, bands[k][0]); if (o > bo) { bo = o; best = k } }
        if (best < 0) {
            val mid = (s + e) / 2f
            best = bands.indices.minByOrNull { abs((bands[it][0] + bands[it][1]) / 2f - mid) } ?: 0
        }
        return best
    }
    // jarak antarbaris: baris yang sangat dekat dengan baris di atasnya = lanjutan sel yang membungkus
    val sd = ArrayList<Float>()
    for (q in 1 until rows.size) { val dd = rows[q].y - rows[q - 1].y; if (dd > 0f) sd.add(dd) }
    sd.sort()
    val intra = if (sd.isEmpty()) pitch else sd[sd.size / 4]
    val varied = sd.isNotEmpty() && sd[sd.size - 1] >= sd[0] * 1.25f
    val tr = ArrayList<Array<String>>()
    var used = 0
    for (qi in rows.indices) {
        val r = rows[qi]
        val dy = if (qi > 0) r.y - rows[qi - 1].y else 0f
        val tight = dy <= intra * 1.25f
        if (r.cells >= 2) {
            val parts = r.t.split('¦')
            if (parts.size != r.cells) return null
            val row = Array(bands.size) { "" }
            for (c in 0 until r.cells) {
                val k = bandOf(r.cs[2 * c], r.cs[2 * c + 1])
                val p = parts[c].trim()
                row[k] = if (row[k].isEmpty()) p else row[k] + " " + p
            }
            val cont = tr.isNotEmpty() && (if (varied) tight else row[0].isEmpty())
            if (cont) {
                val last = tr[tr.size - 1]
                for (k in row.indices) if (row[k].isNotEmpty()) last[k] = if (last[k].isEmpty()) row[k] else last[k] + " " + row[k]
            } else tr.add(row)
            used++
        } else {
            if (tr.isEmpty()) break
            val k = bandOf(r.cs[0], r.cs[1])
            val t0 = r.t.trim()
            val last = tr[tr.size - 1]
            val ok = abs(r.cs[0] - bands[k][0]) <= 8f && (!varied || tight) &&
                (k > 0 || t0.firstOrNull()?.isUpperCase() != true)
            if (!ok) break
            last[k] = if (last[k].isEmpty()) t0 else last[k] + " " + t0
            used++
        }
    }
    if (tr.size < 2) return null
    val nc = bands.size
    if (nc == 2 && tr.all { RX_TOCN.matches(it[1].trim()) }) {
        return Tab(null, tr.map { it[0] + " … " + it[1].trim() }, i0 + used)   // daftar isi, bukan tabel
    }
    val al = List(nc) { c ->
        val v = tr.drop(1).map { it[c].trim() }.filter { it.isNotEmpty() }
        if (v.isNotEmpty() && v.count { RX_NUMC.matches(it) } * 4 >= v.size * 3) "right" else "left"
    }
    return Tab(Blk(ty = "tbl", tb = tr.map { r -> r.map { Rt(it) } }, ca = al), null, i0 + used, bands)
}

private fun bandIdx(bands: List<FloatArray>, s: Float, e: Float): Int {
    var best = -1
    var bo = 0f
    for (k in bands.indices) { val o = min(e, bands[k][1]) - max(s, bands[k][0]); if (o > bo) { bo = o; best = k } }
    if (best < 0) {
        val mid = (s + e) / 2f
        best = bands.indices.minByOrNull { abs((bands[it][0] + bands[it][1]) / 2f - mid) } ?: 0
    }
    return best
}

private fun wordText(l: CLine, k: Int): String {
    val a = l.wp[k]
    val b = if (k + 1 < l.wp.size) l.wp[k + 1] - 1 else l.t.length
    return if (a in 0..b && b <= l.t.length) l.t.substring(a, b).trim() else ""
}

/**
 * Baris judul tabel yang tertinggal di atas tabel (jarak antarkolomnya terlalu sempit untuk dikenali sebagai sel):
 * tiap kata harus masuk satu kolom dan tidak ada kata yang melintasi celah antarkolom. Mengembalikan (jumlah baris, sel-sel judul).
 */
private fun headerRow(above: List<CLine>, first: CLine, bands: List<FloatArray>, pitch: Float): Pair<Int, Array<String>>? {
    var n = 0
    var multi = false
    var nxt = first
    var j = above.size - 1
    while (j >= 0 && n < 3) {
        val c = above[j]
        if (c.page != nxt.page || c.col != nxt.col || nxt.y - c.y > pitch * 1.8f || c.wp.isEmpty() || c.mk != 0) break
        if (RX_CAP.containsMatchIn(c.t)) break
        val used = HashSet<Int>()
        var bad = false
        for (k in c.wp.indices) {
            val mid = (c.wa[k] + c.wb[k]) / 2f
            if (mid < bands[0][0] - 6f || mid > bands[bands.size - 1][1] + 6f) { bad = true; break }
            used.add(bandIdx(bands, c.wa[k], c.wb[k]))
        }
        if (bad) break
        val lo = used.minOrNull() ?: 0
        val hi = used.maxOrNull() ?: 0
        var ok = true
        for (g in lo until hi) { val gx = (bands[g][1] + bands[g + 1][0]) / 2f; if (crosses(c, gx)) ok = false }
        if (!ok) break
        if (hi > lo) multi = true
        n++; nxt = c; j--
    }
    if (n == 0 || !multi) return null
    val row = Array(bands.size) { "" }
    for (q in above.size - n until above.size) {
        val c = above[q]
        for (k in c.wp.indices) {
            val b = bandIdx(bands, c.wa[k], c.wb[k])
            val w = wordText(c, k)
            if (w.isNotEmpty()) row[b] = if (row[b].isEmpty()) w else row[b] + " " + w
        }
    }
    return n to row
}

private const val PART_MAX = 600

/** Pecah daftar blok menjadi beberapa bagian (sekitar [PART_MAX] blok), dipotong di judul bab terdekat. */
private fun splitParts(bl: List<Blk>): List<List<Blk>> {
    if (bl.size <= PART_MAX * 4 / 3) return listOf(bl)
    val res = ArrayList<List<Blk>>()
    var st = 0
    while (bl.size - st > PART_MAX * 4 / 3) {
        val lo = st + PART_MAX * 6 / 10
        val hi = st + PART_MAX * 12 / 10
        var cut = -1
        var bestLv = 99
        var bestD = Int.MAX_VALUE
        for (i in lo..hi) {
            val b = bl[i]
            if (b.ty != "h") continue
            val d = abs(i - (st + PART_MAX))
            if (b.lv < bestLv || (b.lv == bestLv && d < bestD)) { cut = i; bestLv = b.lv; bestD = d }
        }
        if (cut < 0) cut = st + PART_MAX
        res.add(bl.subList(st, cut).toList())
        st = cut
    }
    res.add(bl.subList(st, bl.size).toList())
    return res
}

/** Apakah [l] memulai unit baru (paragraf / judul / butir) setelah [p]. */
private val RX_BABONLY = Regex("^(bab|chapter|bagian)\\s+[IVXLC\\d]+$", RegexOption.IGNORE_CASE)
private val RX_REF = Regex("^\\p{Lu}[\\p{L}'’\\-]+(\\s\\p{Lu}[\\p{L}'’\\-]+)?,\\s+(\\p{Lu}\\.|&)")

private fun startsNew(cur: List<CLine>, l: CLine): Boolean {
    val p = cur[cur.size - 1]
    if (l.mk != 0) return true
    if (abs(l.rel - p.rel) > .12f) return true
    if (cur.size == 1 && RX_BABONLY.matches(p.t.trim())) return false          // "BAB I" + "PENDAHULUAN" = satu judul
    if (RX_REF.containsMatchIn(l.t) && p.t.trimEnd().endsWith(".")) return true   // entri daftar pustaka baru ("Nama, A. (2020)...")
    if (LEAD.containsMatchIn(l.t) || LEAD.containsMatchIn(p.t)) return true
    val ends = TERM.containsMatchIn(p.t)
    val first = l.t.firstOrNull()
    val pHead = p.bold && p.short
    // baris berawalan huruf kecil: hampir pasti lanjutan (dalam butir daftar selalu, di mana pun tanpa tanda akhir kalimat)
    if (first?.isLowerCase() == true && !(p.center && l.center) && !pHead &&
        (cur[0].mk != 0 || !ends)) return false
    if (l.page != p.page || l.col != p.col) {
        val ind = l.indent && cur[0].mk == 0   // lanjutan butir yang menjorok bukan awal paragraf
        val cont = !pHead && !ind && !(ends && p.short) &&
            (first?.isLowerCase() == true || (!ends && !p.short))
        return !cont
    }
    // judul / blok rata tengah: baris baru kecuali baris sebelumnya hampir penuh (judul yang membungkus)
    if (p.center && l.center) return (p.xe - p.x) < .55f * (p.rm - p.lm)
    if (p.center && !l.center && (p.xe - p.x) < .85f * (p.rm - p.lm)) return true   // judul rata tengah lalu teks biasa
    // baris-baris pendek yang berdiri sendiri (sampul, alamat): tiap baris satu unit
    val wpf = (p.xe - p.x) / max(1f, p.rm - p.lm)
    val wlf = (l.xe - l.x) / max(1f, l.rm - l.lm)
    if (p.bold && l.bold && wpf < .68f && isCaps(p.t)) return true
    if (p.mk == 0 && cur[0].mk == 0 && wpf < .5f && wlf < .6f) return true
    // daftar pustaka (hanging indent): baris kembali ke margin setelah baris menjorok = entri baru
    if (cur.size >= 2 && cur[1].x - cur[0].x > 8f && l.x < cur[1].x - 6f) return true
    if (l.gap && (ends || p.short || pHead || p.bold)) return true
    if (ends && p.short) return true
    if (p.bold && l.bold && RX_HNUM.containsMatchIn(l.t)) return true
    if (p.bold && !l.bold && p.t.length < 100) return true
    if (!p.bold && l.bold && l.short && l.t.length < 100) return true
    return false
}

private fun makeUnits(L: List<CLine>, pitch: Float): List<U> {
    val out = ArrayList<U>()
    var cur = ArrayList<CLine>()
    val foot = ArrayList<CLine>()
    fun closeCur() {
        if (cur.isNotEmpty()) { out.add(U(cur)); cur = ArrayList() }
        if (foot.isNotEmpty()) {
            var f = ArrayList<CLine>()
            for (l in foot) {
                val pv = f.lastOrNull()
                if (pv != null && (l.mk != 0 || l.gap || (TERM.containsMatchIn(pv.t) && pv.short))) { out.add(U(f)); f = ArrayList() }
                f.add(l)
            }
            if (f.isNotEmpty()) out.add(U(f))
            foot.clear()
        }
    }
    var i = 0
    while (i < L.size) {
        val l = L[i]
        if (l.cells >= 2) {
            val tb = tryTable(L, i, pitch)
            var blk = tb?.blk
            var e = tb?.end ?: (i + 1)
            val bd = tb?.bands
            if (tb != null && blk != null && bd != null) {
                // judul tabel yang jaraknya terlalu sempit dan tertinggal di atas
                val hr = headerRow(cur, l, bd, pitch)
                if (hr != null) {
                    repeat(hr.first) { cur.removeAt(cur.size - 1) }
                    blk = blk.copy(tb = listOf(hr.second.map { Rt(it) }) + blk.tb)
                }
                // sel terakhir yang lanjut di halaman berikutnya
                var cb: Blk = blk
                while (e < L.size) {
                    val c = L[e]
                    val pv = L[e - 1]
                    val newPage = c.page == pv.page + 1
                    if (c.cells != 1 || c.col != pv.col) break
                    if (e == tb.end) { if (!newPage) break } else if (c.page != pv.page || c.y - pv.y > pitch * 1.6f) break
                    val k = bandIdx(bd, c.cs[0], c.cs[1])
                    val t0 = c.t.trim()
                    if (abs(c.cs[0] - bd[k][0]) > 8f || (k == 0 && t0.firstOrNull()?.isUpperCase() == true) || RX_CAP.containsMatchIn(t0)) break
                    val rws = cb.tb.toMutableList()
                    val last = rws[rws.size - 1]
                    rws[rws.size - 1] = last.mapIndexed { ix, rt -> if (ix == k) Rt(if (rt.t.isEmpty()) t0 else rt.t + " " + t0) else rt }
                    cb = cb.copy(tb = rws)
                    e++
                }
                blk = cb
            }
            closeCur()
            if (tb != null) {
                val u = U(ArrayList(L.subList(i, e)))
                if (blk != null) { u.tb = blk; u.role = 'T' } else { u.rows = tb.toc; u.role = 'R' }
                out.add(u)
                i = e
            } else { out.add(U(listOf(l))); i++ }
            continue
        }
        if (l.foot) {
            if (cur.isEmpty()) out.add(U(listOf(l))) else foot.add(l)
            i++; continue
        }
        if (cur.isEmpty()) { cur.add(l); i++; continue }
        if (startsNew(cur, l)) closeCur()
        cur.add(l)
        i++
    }
    closeCur()
    return out
}

/** Tabel yang berlanjut di halaman berikutnya (kolom sama) disatukan; baris judul yang diulang dibuang. */
private fun stitch(us: List<U>): List<U> {
    val out = ArrayList<U>()
    for (u in us) {
        val p = out.lastOrNull()
        val a = p?.tb
        val b = u.tb
        if (p != null && a != null && b != null && a.tb.isNotEmpty() && b.tb.isNotEmpty() &&
            a.tb[0].size == b.tb[0].size && u.pg0 == p.pg1 + 1
        ) {
            val dup = a.tb[0].map { it.t.trim() } == b.tb[0].map { it.t.trim() }
            val m = U(p.ls + u.ls)
            m.tb = a.copy(tb = a.tb + b.tb.drop(if (dup) 1 else 0))
            m.role = 'T'; m.pg0 = p.pg0; m.pg1 = u.pg1
            out[out.size - 1] = m
        } else out.add(u)
    }
    return out
}

// ---------------------------------------------------------------- peran unit: pasti (lokal) atau ambigu (klaster)

private val RX_CAP = Regex("^(tabel|table|gambar|figure|grafik|bagan|diagram)\\s+[\\dIVX]+([.:]\\d+)*", RegexOption.IGNORE_CASE)

private fun localRole(u: U): Char {
    val f = u.ls[0]
    val t = unitText(u)
    val n = u.ls.size
    val len = t.length
    val se = sentEnd(t)
    val boldAll = u.ls.all { it.bold }
    val head = (boldAll && len < 100 && !se) || (f.rel >= 1.15f && len < 120 && !se)
    if (RX_CAP.containsMatchIn(t) && f.rel < 1.25f) return 'P'   // keterangan tabel / gambar
    when (f.mk) {
        1 -> return 'L'
        2, 3 -> return if (head && n <= 2) 'H' else 'L'
    }
    if (f.foot || f.rel < .9f) return 'P'
    if (boldAll && !se && len < 300 && n <= 6) return 'H'   // blok tebal tanpa titik akhir = judul
    if (f.rel >= 1.25f && n <= 3 && len <= 150 && !se) return 'H'
    if (len < 100 && !se && (boldAll || f.rel > 1.04f) && RX_HDR.containsMatchIn(t)) return 'H'
    if (n >= 3 || len >= 160 || se) return 'P'
    val amb = len < 110 && (f.rel >= 1.05f || boldAll || isCaps(t) || f.center)
    return if (amb) '?' else 'P'
}

private fun fallbackRole(u: U): Char {
    val f = u.ls[0]
    return if (f.rel >= 1.08f || u.ls.all { it.bold } || isCaps(unitText(u))) 'H' else 'P'
}

private class Clu(val key: String) {
    var id = 0
    val us = ArrayList<Int>()
}

private class An(val units: List<U>, val clu: List<Clu>)

private fun clusterKey(u: U): String {
    val f = u.ls[0]
    return Math.round(f.sz * 2f).toString() + (if (u.ls.all { it.bold }) "b" else "") + (if (u.ls.all { it.ital }) "i" else "") +
        (if (isCaps(unitText(u))) "C" else "") + (if (f.center) "c" else "") + (if (f.gap) "g" else "") + (if (f.indent) ">" else "")
}

private fun analyze(ex: Extracted): An {
    val L = ex.lines.filter { !it.noise }
    val units = stitch(makeUnits(L, ex.pitch))
    val map = LinkedHashMap<String, Clu>()
    for (ui in units.indices) {
        val u = units[ui]
        if (u.role == 'T' || u.role == 'R') continue
        val r = localRole(u)
        u.role = r
        if (r == '?') map.getOrPut(clusterKey(u)) { Clu(clusterKey(u)) }.us.add(ui)
    }
    val clu = map.values.sortedByDescending { it.us.size }.take(40).sortedBy { it.us[0] }
    clu.forEachIndexed { i, c -> c.id = i + 1 }
    return An(units, clu)
}

// ---------------------------------------------------------------- Groq: satu permintaan kecil per dokumen

private const val SYS =
    "You label text-style clusters of a PDF so it can be rebuilt as a clean note. You never rewrite text.\n" +
        "Each cluster groups short text units that share one font style: size relative to body text (1.0x), b = bold, i = italic, CAPS, ctr = centered, gap = blank space above, n = number of units. " +
        "A few samples follow, each as `text ⇒ start of the next unit`.\n" +
        "Answer ONE code per cluster:\n" +
        "H = heading or title (a short label that introduces the text after it)\n" +
        "P = normal text (caption, author, name, date, field label, sentence fragment, anything that is not a heading)\n" +
        "L = list item\n" +
        "X = junk to delete, only if clearly page furniture (running header or footer, watermark, stray page number)\n" +
        "When unsure choose P. Reply exactly one line per cluster as `id code`, nothing else.\n" +
        "Example:\n1 H\n2 P\n3 X"

private val RX_CODE = Regex("^\\D{0,12}?(\\d{1,3})\\s*[:.\\-=)>→]*\\s*\\(?(heading|title|paragraph|normal|text|list|delete|junk|[HPLX])(?![A-Za-z])", RegexOption.IGNORE_CASE)
private val RX_ONLY = Regex("^\\W*(heading|title|paragraph|normal|text|list|delete|junk|[HPLX])(?![A-Za-z])", RegexOption.IGNORE_CASE)

private fun codeOf(w: String): Char = when (w.lowercase()) {
    "h", "heading", "title" -> 'H'
    "l", "list" -> 'L'
    "x", "delete", "junk" -> 'X'
    else -> 'P'
}

private fun clip(s: String, n: Int): String {
    val t = s.replace('\n', ' ').trim()
    return if (t.length <= n) t else t.substring(0, n - 1) + "…"
}

private fun promptOf(units: List<U>, clu: List<Clu>): String {
    val sb = StringBuilder()
    for (c in clu) {
        val u0 = units[c.us[0]]
        val f = u0.ls[0]
        sb.append(c.id).append(' ').append(Math.round(f.rel * 10f) / 10f).append('x')
        if (u0.ls.all { it.bold }) sb.append(" b")
        if (u0.ls.all { it.ital }) sb.append(" i")
        if (isCaps(unitText(u0))) sb.append(" CAPS")
        if (f.center) sb.append(" ctr")
        if (f.gap) sb.append(" gap")
        sb.append(" n=").append(c.us.size).append('\n')
        for (k in linkedSetOf(0, c.us.size / 2, c.us.size - 1)) {
            val ui = c.us[k]
            val nx = units.getOrNull(ui + 1)
            val nt = when {
                nx == null -> "(end)"
                nx.tb != null || nx.rows != null -> "[table]"
                else -> clip(unitText(nx), 35)
            }
            sb.append("- ").append(clip(unitText(units[ui]), 70)).append(" ⇒ ").append(nt).append('\n')
        }
    }
    return sb.toString()
}

/** "id kode;" dari jawaban Groq. Toleran: markdown, "Cluster 1:", kata penuh, atau hanya deretan kode berurutan. Null bila terlalu sedikit yang terbaca. */
private fun parseCodes(raw: String, m: Int): String? {
    val lines = raw.replace("*", "").replace("`", "").replace("#", "").lines().map { it.trim() }.filter { it.isNotEmpty() }
    val sb = StringBuilder()
    val seen = HashSet<Int>()
    for (ln in lines) {
        val mm = RX_CODE.find(ln.removePrefix("-").trim()) ?: continue
        val n = mm.groupValues[1].toIntOrNull() ?: continue
        if (n < 1 || n > m || !seen.add(n)) continue
        sb.append(n).append(' ').append(codeOf(mm.groupValues[2])).append(';')
    }
    if (seen.size * 5 >= m * 3) return sb.toString()
    // jawaban hanya deretan kode tanpa nomor: pakai urutan
    val only = lines.mapNotNull { RX_ONLY.find(it)?.groupValues?.get(1) }
    if (only.size == m) {
        val o = StringBuilder()
        only.forEachIndexed { i, w -> o.append(i + 1).append(' ').append(codeOf(w)).append(';') }
        return o.toString()
    }
    return null
}

private fun decodeCodes(s: String): Map<Int, Char> {
    val m = HashMap<Int, Char>()
    for (e in s.split(';')) {
        val p = e.trim().split(' ')
        if (p.size == 2) { val n = p[0].toIntOrNull(); if (n != null && p[1].isNotEmpty()) m[n] = p[1][0] }
    }
    return m
}

// ---------------------------------------------------------------- perakitan blok

private fun joinRt(ls: List<CLine>): Rt {
    val sb = StringBuilder()
    val sp = ArrayList<Sp>()
    for (l in ls) {
        val txt = l.t.replace('¦', ' ')
        if (sb.isNotEmpty()) {
            val n = sb.length
            var hy = false
            if (n >= 3 && sb[n - 1] == '-' && sb[n - 2].isLetter() && txt.isNotEmpty() && txt[0].isLowerCase()) {
                var a = n - 2
                while (a >= 0 && sb[a].isLetter()) a--
                hy = (n - 2 - a) >= 2 && (a < 0 || sb[a] != '-')   // "inter-" + "national"; bukan "state-of-the-" + "art"
            }
            if (hy) {
                sb.setLength(n - 1)
                for (k in sp.indices) if (sp[k].e > n - 1) sp[k] = sp[k].copy(e = n - 1)
            } else sb.append(' ')
        }
        val off = sb.length
        sb.append(txt)
        for (s in l.sp) sp.add(Sp(s.s + off, s.e + off, s.k))
    }
    val t = sb.toString()
    return Rt(t, normSp(sp.filter { it.e > it.s }, t.length))
}

/** Judul tanpa tebal; paragraf / butir yang seluruhnya tebal atau miring tanpa penekanan sebagian. */
private fun tidy(rt: Rt, head: Boolean): Rt {
    val n = rt.t.length
    if (n == 0) return rt
    val keep = rt.sp.filter { s ->
        when {
            head && s.k == K.B -> false
            (s.k == K.B || s.k == K.I) && (s.e - s.s) * 10 >= n * 9 -> false
            else -> true
        }
    }
    return Rt(rt.t, normSp(keep, n))
}

private fun addP(out: MutableList<Blk>, b: Blk) {
    val last = out.lastOrNull()
    val ft = b.r.t.trimStart()
    if (last != null && last.ty == "p" && last.r.t.isNotBlank() && !TERM.containsMatchIn(last.r.t.trimEnd()) &&
        ft.isNotEmpty() && ft.first().isLowerCase()
    ) out[out.size - 1] = last.copy(r = last.r + Rt(" ") + b.r)
    else out.add(b)
}

private val RX_BIB = Regex("^(daftar pustaka|daftar referensi|referensi|references|bibliography|sumber pustaka|pustaka)\\b", RegexOption.IGNORE_CASE)

/** Daftar pustaka seragam: semua butir poin, atau semuanya paragraf polos, mengikuti mayoritas. */
private fun normBib(out: MutableList<Blk>) {
    var i = 0
    while (i < out.size) {
        val h = out[i]
        if (h.ty == "h" && RX_BIB.containsMatchIn(h.r.t.trim())) {
            var j = i + 1
            while (j < out.size && !(out[j].ty == "h" && out[j].lv <= h.lv)) j++
            val ents = (i + 1 until j).filter { (out[it].ty == "p" && out[it].r.t.isNotBlank()) || (out[it].ty == "li" && out[it].lt == "b") }
            val nLi = ents.count { out[it].ty == "li" }
            if (ents.size >= 3) {
                val asLi = nLi * 2 > ents.size
                for (e in ents) {
                    val b = out[e]
                    if (asLi && b.ty == "p") out[e] = Blk(ty = "li", lt = "b", d = 0, r = b.r)
                    else if (!asLi && b.ty == "li") out[e] = Blk(ty = "p", r = b.r)
                }
            }
            i = j
        } else i++
    }
}

private data class HS(val sz: Int, val b: Boolean, val i: Boolean, val c: Boolean, val cls: Int)

private val RX_N_BAB = Regex("^(bab|chapter)\\b", RegexOption.IGNORE_CASE)
private val RX_N_ROM = Regex("^[IVXL]{2,}\\.\\s")
private val RX_N_LET = Regex("^[A-Za-z][.)]\\s")
private val RX_N_3 = Regex("^\\d+\\.\\d+\\.\\d+")
private val RX_N_2 = Regex("^\\d+\\.\\d+")
private val RX_N_1 = Regex("^\\d+[.)]\\s")

/** Urutan tingkat penomoran judul: BAB < I. < A. < 1. < 1.1 < 1.1.1 < tanpa nomor. */
private fun numClass(t: String): Int = when {
    RX_N_BAB.containsMatchIn(t) -> 0
    RX_N_ROM.containsMatchIn(t) -> 1
    RX_N_LET.containsMatchIn(t) -> 2
    RX_N_3.containsMatchIn(t) -> 5
    RX_N_2.containsMatchIn(t) -> 4
    RX_N_1.containsMatchIn(t) -> 3
    isCaps(t) -> 0   // judul HURUF BESAR tanpa nomor (DAFTAR PUSTAKA, judul dokumen) sejajar dengan BAB
    else -> 9
}

private fun assemble(an: An, dec: Map<Int, Char>?): List<Blk> {
    val us = an.units
    // keputusan klaster -> semua unit bergaya sama mendapat peran yang sama
    for (c in an.clu) {
        val code = dec?.get(c.id)
        for (ui in c.us) {
            val u = us[ui]
            val t = unitText(u)
            u.role = when (code) {
                'H' -> if (sentEnd(t) && t.length > 80) 'P' else 'H'
                'L' -> 'L'
                'X' -> if (c.us.size >= 3 && t.length < 60) 'X' else 'P'
                'P' -> 'P'
                else -> fallbackRole(u)
            }
        }
    }
    for (u in us) if (u.role == '?') u.role = fallbackRole(u)

    // tingkat judul ditentukan sekali untuk seluruh dokumen dari gaya huruf (besar -> kecil)
    fun hsig(u: U): HS {
        val t = unitText(u)
        return HS(Math.round(u.ls[0].sz), u.ls.all { it.bold }, u.ls.all { it.ital }, isCaps(t), numClass(t))
    }
    val sigs = us.filter { it.role == 'H' }.map { hsig(it) }.distinct()
        .sortedWith(compareByDescending<HS> { it.sz }.thenByDescending { it.b }.thenByDescending { it.c }.thenBy { it.i }.thenBy { it.cls })
    val lvl = HashMap<HS, Int>()
    sigs.forEachIndexed { i, s -> lvl[s] = (i + 1).coerceAtMost(4) }

    // kedalaman butir dari indentasi, dihitung per deret butir berurutan (bullet di bawah daftar bernomor tetap tingkat 1)
    val depth = HashMap<U, Int>()
    var di = 0
    while (di < us.size) {
        if (us[di].role != 'L') { di++; continue }
        var dj = di
        while (dj < us.size && us[dj].role == 'L') dj++
        val vs = (di until dj).map { (us[it].ls[0].x - us[it].ls[0].lm).coerceAtLeast(0f) }.sorted()
        val reps = ArrayList<Float>()
        for (v in vs) if (reps.isEmpty() || v - reps[reps.size - 1] > 6f) reps.add(v)
        for (k in di until dj) {
            val v = (us[k].ls[0].x - us[k].ls[0].lm).coerceAtLeast(0f)
            var d = 0
            for (q in reps.indices) if (v >= reps[q] - 3f) d = q
            depth[us[k]] = d.coerceIn(0, 3)
        }
        di = dj
    }
    fun depthOf(u: U): Int = depth[u] ?: 0

    val out = ArrayList<Blk>()
    for (u in us) {
        when (u.role) {
            'T' -> u.tb?.let { out.add(it) }
            'R' -> u.rows?.forEach { out.add(Blk(ty = "p", r = Rt(it))) }
            'H' -> {
                val rt = tidy(joinRt(u.ls), true)
                if (rt.t.isNotBlank()) out.add(Blk(ty = "h", lv = lvl[hsig(u)] ?: 2, r = rt))
            }
            'L' -> {
                var rt = joinRt(u.ls)
                val f = u.ls[0]
                var lt = "b"
                var st = 0
                val mg = RX_GLYPH.find(rt.t) ?: RX_ASCII.find(rt.t)
                val mn = if (mg == null) RX_NUM.find(rt.t) else null
                val ml = if (mg == null && mn == null && f.mk == 3) RX_LET.find(rt.t) else null
                if (mg != null) rt = rt.slice(mg.range.last + 1, rt.t.length)
                else if (mn != null) { lt = "o"; st = mn.groupValues[1].toIntOrNull() ?: 0; rt = rt.slice(mn.range.last + 1, rt.t.length) }
                else if (ml != null) { lt = "a"; st = Character.toLowerCase(ml.groupValues[1][0]) - 'a' + 1; rt = rt.slice(ml.range.last + 1, rt.t.length) }
                rt = tidy(rt, false)
                if (rt.t.isNotBlank()) out.add(Blk(ty = "li", lt = lt, d = depthOf(u), st = st, r = rt))
            }
            'P' -> {
                val cap = RX_CAP.containsMatchIn(u.ls[0].t)
                var rt = if (cap) joinRt(u.ls) else tidy(joinRt(u.ls), false)
                if (LEAD.containsMatchIn(rt.t)) rt = Rt(rt.t.replace(Regex("\\s*(\\.\\s?){4,}\\s*|\\s*…{2,}\\s*"), " … ").trim())
                if (rt.t.isNotBlank()) addP(out, Blk(ty = "p", r = rt))
            }
        }
    }
    normBib(out)
    if (out.isEmpty()) out.add(Blk())
    if (out.last().isObj || out.last().isMulti) out.add(Blk())
    return out
}

// ---------------------------------------------------------------- status dan pengendali

class ConvState {
    var phase by mutableIntStateOf(0)    // 0 menyiapkan, 1 menata, 2 berhenti (kesalahan / menunggu Try again)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var msg by mutableStateOf("")
    var model by mutableStateOf("")
}

/** Titik pulih bila aplikasi dimatikan di tengah konversi. [n], [done], [mode] tidak dipakai lagi (tetap ada agar berkas lama terbaca). */
@Serializable
class ConvCk(val nid: String, val src: String, val n: Int = 0, val done: List<String?> = emptyList(), val mode: Int = 1)

private class Pause(msg: String, val fatal: Boolean = false) : Exception(msg)

object PdfConv {
    /** Catatan yang sedang / pernah berhenti di tengah konversi. Ada di sini = layar skeleton tampil. */
    val map = mutableStateMapOf<String, ConvState>()
    /** Hanya selama animasi geser masuk: id PDF asal yang masih terlihat di bawah halaman baru. */
    val under = mutableStateMapOf<String, String>()
    private val jobs = HashMap<String, Job>()
    private val cool = HashMap<String, Long>()
    private var restored = false
    private var why = ""

    fun has(id: String): Boolean = map.containsKey(id)

    private fun dir(ctx: Context) = File(ctx.filesDir, "conv").apply { mkdirs() }
    private fun ckFile(ctx: Context, id: String) = File(dir(ctx), "$id.json")

    private fun saveCk(ctx: Context, ck: ConvCk) {
        try {
            val f = ckFile(ctx, ck.nid)
            val tmp = File(dir(ctx), ck.nid + ".tmp")
            tmp.writeText(J.encodeToString(ConvCk.serializer(), ck))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (e: Exception) { }
    }

    private fun loadCk(ctx: Context, id: String): ConvCk? = try {
        J.decodeFromString(ConvCk.serializer(), ckFile(ctx, id).readText())
    } catch (e: Exception) { null }

    /** Setelah aplikasi dimatikan di tengah konversi: tampilkan lagi sebagai berhenti (Try again). Aman dipanggil berkali-kali. */
    fun restore(vm: AppVM) {
        if (restored) return
        restored = true
        val ctx = vm.getApplication<Application>()
        val fs = dir(ctx).listFiles() ?: return
        for (f in fs) {
            if (!f.name.endsWith(".json")) { if (f.name.endsWith(".tmp")) f.delete(); continue }
            val id = f.name.removeSuffix(".json")
            val ck = loadCk(ctx, id)
            if (ck == null || vm.note(id) == null) { f.delete(); continue }
            if (map.containsKey(id)) continue
            val s = ConvState()
            s.phase = 2
            s.msg = "The conversion was interrupted. Tap Try again to start it again."
            map[id] = s
        }
    }

    /** Notifikasi berjalan (dipakai AppVM.bgSnap). Null bila tidak ada konversi yang sedang jalan. */
    fun snap(): Bg.Snap? {
        val s = map.values.firstOrNull { it.phase < 2 } ?: return null
        val p = if (s.total > 0) (s.done * 100 / s.total).coerceIn(0, 99) else -1
        val txt = if (s.msg.isNotEmpty()) s.msg else "Reading PDF…"
        return Bg.Snap("Converting PDF to note", txt, p, false, false)
    }

    /** Mulai konversi PDF [srcId]. Membuat catatan baru, pindah ke sana (geser masuk), lalu bekerja di latar belakang. */
    fun begin(vm: AppVM, srcId: String): Boolean {
        val src = vm.note(srcId) ?: return false
        if (map.values.any { it.phase < 2 }) {
            vm.show("Another PDF is still being converted")
            return false
        }
        val ctx = vm.getApplication<Application>()
        val t = System.currentTimeMillis()
        val id = uid()
        val n = Note(id = id, t = src.t, x = "Converting PDF…", u = t, c = t, lb = listOfNotNull(vm.filter))
        vm.docs.add(0, n)
        vm.save()
        saveCk(ctx, ConvCk(id, src.pdf))
        val st = ConvState()
        map[id] = st
        under[id] = srcId
        // ganti layar PDF (dan menu titik tiganya) dengan catatan baru; halaman baru bergeser masuk di atas PDF
        val stack = vm.nav.stack
        if (stack.isNotEmpty() && stack[stack.size - 1] is Scr.NoteMenu) stack.removeAt(stack.size - 1)
        if (stack.isNotEmpty() && stack[stack.size - 1] is Scr.Note) stack.removeAt(stack.size - 1)
        vm.nav.noteId = id
        vm.nav.pendingTitle = false
        vm.openId = id
        stack.add(Scr.Note(id))
        run(vm, id)
        return true
    }

    fun resume(vm: AppVM, id: String) {
        val st = map[id] ?: return
        if (st.phase < 2) return
        if (map.values.any { it !== st && it.phase < 2 }) {
            vm.show("Another PDF is still being converted")
            return
        }
        run(vm, id)
    }

    /** Hentikan dan hapus catatan hasil konversi. PDF asli tidak disentuh. */
    fun cancel(vm: AppVM, id: String) {
        val ctx = vm.getApplication<Application>()
        jobs.remove(id)?.cancel()
        Groq.abort()
        map[id]?.let { it.msg = "Canceled" }
        vm.nav.pop()
        vm.viewModelScope.launch {
            delay(600)   // tunggu animasi tutup selesai
            map.remove(id)
            under.remove(id)
            withContext(Dispatchers.IO) { try { ckFile(ctx, id).delete() } catch (e: Exception) { } }
            vm.docs.removeAll { it.id == id }
            vm.save()
        }
    }

    private fun run(vm: AppVM, id: String) {
        val st = map[id] ?: return
        st.phase = 0
        st.msg = ""
        jobs[id]?.cancel()
        jobs[id] = vm.viewModelScope.launch {
            try {
                work(vm, id, st)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Pause) {
                st.phase = 2
                st.msg = e.message ?: "The conversion stopped."
                Bg.done(vm.getApplication<Application>(), "Conversion paused", "Open the note to continue.")
            } catch (e: Throwable) {
                st.phase = 2
                st.msg = "Something went wrong (" + (e.message ?: e.javaClass.simpleName) + "). Your PDF is untouched."
                Bg.done(vm.getApplication<Application>(), "Conversion failed", "Open the note to try again.")
            }
        }
        vm.bgSync()
    }

    private fun enMsg(e: GroqException): String = when (e.code) {
        401 -> "Groq rejected the API key. Replace it in Voice transcription settings, then tap Try again."
        403 -> "Groq denied access (the key has no permission, or your region is blocked)."
        else -> if (e.code == 0) "Can't reach Groq. Check your internet connection, then tap Try again." else (e.message ?: "Groq error")
    }

    private fun limitMsg(sec: Long): String {
        val w = when {
            sec < 90 -> "a minute"
            sec < 3600 -> "about " + (sec / 60 + 1) + " min"
            else -> "about " + ((sec + 1799) / 3600) + " h"
        }
        return "Groq's free limit is reached for now. Tap Try again in $w."
    }

    private suspend fun waitSec(st: ConvState, sec: Int, why: String) {
        var left = sec
        while (left > 0) {
            st.msg = "$why. Waiting " + left + "s…"
            delay(1000)
            left--
        }
        st.msg = ""
    }

    /** Satu permintaan Groq (model cadangan + penanganan limit) sampai [accept] menerima jawabannya. "~" = pakai aturan lokal. */
    private suspend fun planChunk(
        key: String, models: List<String>, sys: String, user: String, maxTok: Int, st: ConvState,
        accept: (String) -> String?
    ): String {
        val skip = HashSet<String>()
        val tries = HashMap<String, Int>()
        var net = 0
        var rl = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            val avail = models.filter { it !in skip }
            if (avail.isEmpty()) return "~"
            val m = avail.firstOrNull { (cool[it] ?: 0L) <= now }
            if (m == null) {
                val soon = avail.minOf { cool[it] ?: 0L }
                throw Pause(limitMsg((soon - now) / 1000L))
            }
            st.model = m.substringAfterLast('/')
            try {
                val temp = if ((tries[m] ?: 0) == 0) 0.0 else 0.3
                val raw = withContext(Dispatchers.IO) { Groq.chatOnce(key, m, sys, user, maxTok, temp) }
                val p = if (raw == null) null else accept(raw)
                if (p != null) return p
                why = st.model + ": " + (if (raw == null) "no reply" else if (raw.isBlank()) "empty reply" else "unexpected reply (" + clip(raw, 90) + ")")
                val t = (tries[m] ?: 0) + 1
                tries[m] = t
                if (t >= 2) skip.add(m)
            } catch (e: GroqException) {
                when {
                    e.code == 429 -> {
                        val w = if (e.wait > 0) e.wait else 3
                        rl++
                        if (w <= 75 && rl <= 6) waitSec(st, w, "Rate limit reached")
                        else cool[m] = System.currentTimeMillis() + (if (w > 75) w else 120) * 1000L
                    }
                    e.code == 401 || e.code == 403 -> throw Pause(enMsg(e), true)
                    e.code == 413 -> { why = "request too large for " + st.model; skip.add(m) }
                    else -> {
                        net++
                        if (net > 4) throw Pause(enMsg(e))
                        waitSec(st, 2 shl net, "Connection problem")
                    }
                }
            }
        }
    }

    private suspend fun work(vm: AppVM, id: String, st: ConvState) {
        val ctx = vm.getApplication<Application>()
        val ck0 = withContext(Dispatchers.IO) { loadCk(ctx, id) }
            ?: throw Pause("The conversion data is missing. Cancel, then start again from the PDF.", true)
        val pdf = PdfFiles.file(ctx, ck0.src)
        if (!pdf.exists()) throw Pause("The original PDF is missing, so it can't be converted.", true)
        val key = withContext(Dispatchers.IO) { vm.vault.load() }
            ?: throw Pause("Groq API key not found. Add it in Voice transcription settings, then tap Try again.", true)

        st.msg = "Reading PDF…"
        val ex = withContext(Dispatchers.IO) {
            extract(ctx, pdf, { ensureActive() }) { p, n -> st.total = n + 2; st.done = p; st.msg = "Reading PDF… page $p of $n" }
        }
        if (ex.lines.none { !it.noise }) throw Pause("This PDF has no extractable text (it may be a scan), so it can't be converted.", true)

        st.phase = 1
        st.msg = "Analyzing layout…"
        val an = withContext(Dispatchers.Default) { analyze(ex) }
        st.done = st.total - 1

        var dec: Map<Int, Char>? = null
        var fb = false
        why = ""
        if (an.clu.isNotEmpty()) {
            st.msg = "Checking headings…"
            try {
                val models = try {
                    withContext(Dispatchers.IO) { Groq.layoutModels(key) }
                } catch (e: GroqException) {
                    if (e.code == 401 || e.code == 403) throw Pause(enMsg(e), true)
                    throw Pause("")
                }
                if (models.isEmpty()) throw Pause("")
                val r = planChunk(key, models, SYS, promptOf(an.units, an.clu), max(1000, an.clu.size * 10 + 400), st) { parseCodes(it.replace(Regex("(?s)<think>.*?</think>"), ""), an.clu.size) }
                if (r == "~") fb = true else dec = decodeCodes(r)
            } catch (e: Pause) {
                if (e.fatal) throw e
                why = e.message ?: ""
                fb = true
            }
        }

        st.msg = "Finishing…"
        st.done = st.total
        val blocks = withContext(Dispatchers.Default) { assemble(an, dec) }
        if (vm.note(id) == null) {   // catatan dihapus saat konversi berjalan
            withContext(Dispatchers.IO) { try { ckFile(ctx, id).delete() } catch (e: Exception) { } }
            map.remove(id); jobs.remove(id)
            return
        }
        // editor merender semua blok sekaligus: buku tebal dipecah per bab menjadi beberapa catatan supaya tidak crash
        val parts = splitParts(blocks)
        withContext(Dispatchers.Main) {
            val now = System.currentTimeMillis()
            val base = vm.note(id)?.t ?: ""
            if (parts.size == 1) {
                vm.upd(id) { it.copy(bl = blocks, x = plainOf(blocks), u = now) }
            } else {
                val lb = vm.note(id)?.lb ?: emptyList()
                for (k in parts.size - 1 downTo 1) {
                    val pb = parts[k]
                    vm.docs.add(0, Note(id = uid(), t = base + " (" + (k + 1) + "/" + parts.size + ")", bl = pb, x = plainOf(pb), u = now - k, c = now, lb = lb))
                }
                vm.save()
                vm.upd(id) { it.copy(t = base + " (1/" + parts.size + ")", bl = parts[0], x = plainOf(parts[0]), u = now) }
            }
        }
        withContext(Dispatchers.IO) { try { ckFile(ctx, id).delete() } catch (e: Exception) { } }
        val title = vm.note(id)?.t ?: ""
        map.remove(id)
        jobs.remove(id)
        Bg.done(ctx, "Note ready", title)
        val tail = if (fb) ". Groq failed (" + (if (why.isBlank()) "unknown" else why) + "), so a few headings were guessed locally" else ""
        val sp = if (parts.size > 1) ", split into " + parts.size + " notes" else ""
        if (vm.nav.stack.none { it is Scr.Note && it.id == id }) vm.show("Your converted note is ready$sp$tail")
        else if (fb) vm.show("Done$tail")
    }
}

// ---------------------------------------------------------------- UI

@Composable
fun GroqKeyDialog(vm: AppVM, onDismiss: () -> Unit, onDone: () -> Unit) {
    val pal = LocalPal.current
    var key by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (vm.gqState != 1) { vm.gqState = 0; vm.gqMsg = "" }
        delay(150)
        try { fr.requestFocus() } catch (e: Exception) { }
    }
    LaunchedEffect(sent, vm.gqState, vm.gqSet) { if (sent && vm.gqState == 2 && vm.gqSet) onDone() }
    AlertDialog(
        onDismissRequest = { if (vm.gqState != 1) onDismiss() },
        containerColor = pal.c3,
        title = { T("Groq API key", 22f, 900, pal.os) },
        text = {
            Column {
                T("Needed once for Convert to Note. Get a free key at console.groq.com/keys. It is stored encrypted on this phone.", 14f, 400, pal.osv, lh = 1.3f)
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = key, onValueChange = { if (it.length <= 200) key = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.c4).focusRequester(fr),
                    textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 16.sp, fontWeight = FontWeight(600)),
                    cursorBrush = SolidColor(pal.p),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (key.isNotBlank() && vm.gqState != 1) { sent = true; vm.saveGroqKey(key) } }),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                            if (key.isEmpty()) T("gsk_…", 16f, 500, pal.osv)
                            inner()
                        }
                    }
                )
                if (vm.gqState != 0 && vm.gqMsg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    T(vm.gqMsg, 14f, 500, if (vm.gqState == 3) Color(0xFFD93025) else pal.osv, lh = 1.3f)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank() && vm.gqState != 1,
                onClick = { sent = true; vm.saveGroqKey(key) }
            ) { T(if (vm.gqState == 1) "Checking…" else "Save", 16f, 900, pal.p) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 16f, 700, pal.osv) } }
    )
}

private sealed class SK {
    class H(val w: Float) : SK()
    class P(val n: Int) : SK()
    class L(val n: Int) : SK()
    class Q(val n: Int) : SK()
    class Tb(val r: Int) : SK()
}

private val SKEL = listOf(
    SK.H(.62f), SK.P(3), SK.P(2), SK.H(.4f), SK.L(3), SK.P(3), SK.Q(2), SK.H(.5f), SK.Tb(3), SK.P(3), SK.L(2), SK.P(2)
)

@Composable
private fun SkBar(frac: Float, h: Dp, sh: State<Float>, c1: Color, c2: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth(frac).height(h).clip(RoundedCornerShape(h / 2.4f)).drawBehind {
            drawRect(c1)
            val w = size.width
            val x = (sh.value * 2f - .5f) * w
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c2, Color.Transparent), startX = x - w * .5f, endX = x + w * .5f))
        }
    )
}

@Composable
private fun SkBlock(b: SK, sh: State<Float>, c1: Color, c2: Color) {
    val g = 9.dp
    when (b) {
        is SK.H -> SkBar(b.w, 26.dp, sh, c1, c2)
        is SK.P -> Column(verticalArrangement = Arrangement.spacedBy(g)) {
            for (i in 0 until b.n) SkBar(if (i == b.n - 1) .6f else 1f, 14.dp, sh, c1, c2)
        }
        is SK.L -> Column(verticalArrangement = Arrangement.spacedBy(g)) {
            for (i in 0 until b.n) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c1))
                SkBar(if (i % 2 == 0) .85f else .7f, 14.dp, sh, c1, c2)
            }
        }
        is SK.Q -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(4.dp).height(14.dp * b.n + g * (b.n - 1)).clip(RoundedCornerShape(2.dp)).background(c1))
            Column(verticalArrangement = Arrangement.spacedBy(g)) {
                for (i in 0 until b.n) SkBar(if (i == b.n - 1) .55f else .95f, 14.dp, sh, c1, c2)
            }
        }
        is SK.Tb -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until b.r) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until 3) Box(Modifier.weight(1f)) { SkBar(1f, if (r == 0) 20.dp else 16.dp, sh, c1, c2) }
            }
        }
    }
}

/**
 * Layar "sedang mengonversi" di atas catatan hasil konversi. Menampilkan judul + skeleton teks Markdown yang tersusun bertahap,
 * lalu memudar begitu hasilnya masuk (isi catatan sudah tersusun di bawahnya). Bergeser masuk dari kanan seperti halaman lain.
 */
@Composable
fun PdfConvOverlay(vm: AppVM, id: String) {
    val st = PdfConv.map[id]
    val hold = remember(id) { arrayOfNulls<ConvState>(1) }
    if (st != null) hold[0] = st
    val keep = st ?: hold[0]
    val fade by animateFloatAsState(if (st != null) 1f else 0f, tween(420), label = "cvf")
    if (keep != null && (st != null || fade > .01f)) ConvBody(vm, id, keep, fade, st != null)
}

@Composable
private fun ConvBody(vm: AppVM, id: String, s: ConvState, fadeA: Float, busy: Boolean) {
    val pal = LocalPal.current
    val note = vm.note(id)
    val slide = remember(id) { Animatable(if (PdfConv.under.containsKey(id)) 0f else 1f) }
    LaunchedEffect(id) {
        if (slide.value < 1f) {
            slide.animateTo(1f, tween(340, easing = EmphDecel))
            PdfConv.under.remove(id)
        }
    }
    DisposableEffect(id) {
        onDispose {
            val p = PdfConv.map[id]?.phase
            if (p != null && p < 2) vm.show("Still converting in the background. We'll let you know when it's ready.")
        }
    }
    var keyDlg by remember { mutableStateOf(false) }
    var askCancel by remember { mutableStateOf(false) }
    val tr = rememberInfiniteTransition(label = "sk")
    val sh = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "sh")
    var shown by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (shown < SKEL.size) { delay(420); shown++ }
    }
    val frac = if (s.total > 0) s.done.toFloat() / s.total else 0f
    val pf by animateFloatAsState(frac, tween(450), label = "pf")

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationX = (1f - slide.value) * size.width; alpha = fadeA }
            .background(pal.bg)
            .then(if (busy) Modifier.pointerInput(Unit) { } else Modifier)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn("back", pal.os, { vm.nav.pop() }, size = 44.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    T(note?.t?.ifBlank { null } ?: "PDF", 22f, 800, pal.os)
                    T("Converting to a Markdown note", 13f, 500, pal.osv)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                for (i in SKEL.indices) {
                    AnimatedVisibility(
                        visible = i < shown,
                        enter = fadeIn(tween(320)) + expandVertically(spring(.85f, 380f), expandFrom = Alignment.Top)
                    ) { SkBlock(SKEL[i], sh, pal.c4, pal.c3) }
                }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp, top = 8.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(pal.c4)) {
                    Box(Modifier.fillMaxWidth(pf.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(pal.p))
                }
                Spacer(Modifier.height(10.dp))
                val line = when {
                    s.phase == 2 -> s.msg
                    s.msg.isNotEmpty() -> s.msg
                    s.phase == 0 -> "Preparing…"
                    else -> "Structuring…"
                }
                T(line, 15f, 600, pal.os, lh = 1.3f)
                if (s.phase < 2) {
                    T(
                        (if (s.model.isNotEmpty() && s.phase == 1) "Model: " + s.model + ". " else "") +
                            "You can leave this note. It keeps going in the background.",
                        13f, 400, pal.osv, Modifier.padding(top = 2.dp), lh = 1.3f
                    )
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.phase == 2 && busy) {
                        TextButton(onClick = { if (vm.gqSet) PdfConv.resume(vm, id) else keyDlg = true }) { T("Try again", 16f, 900, pal.p) }
                    }
                    if (busy) TextButton(onClick = { askCancel = true }) { T("Cancel conversion", 16f, 700, pal.osv) }
                }
            }
        }
    }
    if (keyDlg) GroqKeyDialog(vm, { keyDlg = false }) { keyDlg = false; PdfConv.resume(vm, id) }
    if (askCancel) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            containerColor = pal.c3,
            title = { T("Stop converting?", 22f, 900, pal.os) },
            text = { T("The new note will be deleted. Your PDF stays untouched.", 15f, 400, pal.osv, lh = 1.3f) },
            confirmButton = { TextButton(onClick = { askCancel = false; PdfConv.cancel(vm, id) }) { T("Stop", 16f, 900, pal.p) } },
            dismissButton = { TextButton(onClick = { askCancel = false }) { T("Keep going", 16f, 700, pal.osv) } }
        )
    }
}

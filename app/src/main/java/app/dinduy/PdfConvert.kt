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
import androidx.compose.foundation.clickable
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
import kotlin.math.max

// =====================================================================================
//  Convert to Note: PDF (teks asli) -> catatan Markdown native Dinduy.
//
//  Cara kerja (sengaja begini supaya 100% setia dan hemat token Groq gratisan):
//   1. Teks PDF diekstrak lokal (PDFBox) per baris, lengkap dengan tebal / miring dari jenis huruf PDF.
//   2. Groq TIDAK PERNAH menulis ulang teks. Ia hanya menerima baris bernomor lalu membalas "daftar jenis blok"
//      sangat singkat (mis. "12 h2", "13 p", "20 li"). Kode-lah yang merakit catatan dari teks asli,
//      jadi tidak mungkin ada kalimat yang berubah, hilang, atau bertambah.
//   3. Dokumen dipotong bertahap (~5000 huruf per permintaan). Tiap potongan disimpan sebagai checkpoint, jadi
//      kalau kena batas pemakaian Groq / koneksi putus / aplikasi dimatikan, proses bisa dilanjutkan (Try again).
//   4. Kalau Groq gagal untuk satu potongan, potongan itu ditata dengan aturan lokal (teks tetap utuh).
// =====================================================================================

// ---------------------------------------------------------------- ekstraksi baris

class CLine(
    val t: String,          // teks baris; '¦' (menggantikan satu spasi) = jarak lebar antar kolom
    val sp: List<Sp>,       // tebal / miring dari jenis huruf PDF
    val sz: Float,          // ukuran huruf (pt)
    val x: Float,           // posisi kiri (pt)
    val y: Float,           // posisi atas (pt)
    val bold: Boolean,      // seluruh baris tebal
    val page: Int
) {
    var rel = 1f            // ukuran huruf relatif terhadap teks isi
    var gap = false         // ada jarak kosong lebih besar di atasnya (awal paragraf)
    var indent = false
    var hl = 0              // tingkat judul menurut ukuran huruf SELURUH dokumen (1-3), 4 = baris tebal pendek (mungkin judul kecil)
    var xe = 0f             // posisi kanan baris (pt)
    var short = false       // baris pendek (kemungkinan akhir paragraf)
    var noise = false       // kandidat nomor halaman / header-footer berulang (petunjuk untuk Groq; yang diputuskan x dibuang)
}

private class LineGrab : PDFTextStripper() {
    val out = ArrayList<CLine>()
    private val sb = StringBuilder()
    private val bf = ArrayList<Boolean>()
    private val itf = ArrayList<Boolean>()
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
            if (first.xDirAdj - lastEnd > max(8f, s * 1.1f)) sb.setCharAt(sb.length - 1, '¦')
        }
        for (tp in textPositions) {
            val u = tp.unicode ?: continue
            val st = styleOf(tp)
            for (ch in u) { sb.append(ch); bf.add(st.first); itf.add(st.second) }
            if (tp.fontSizeInPt > sz) sz = tp.fontSizeInPt
        }
        val last = textPositions[textPositions.size - 1]
        lastEnd = last.xDirAdj + last.widthDirAdj
    }

    override fun writeWordSeparator() { sb.append(' '); bf.add(false); itf.add(false) }

    override fun writeLineSeparator() { flushLine() }

    override fun writePageEnd() {
        flushLine()
        super.writePageEnd()
    }

    private fun ranges(a: Int, b: Int, f: List<Boolean>, k: Int, into: MutableList<Sp>): Int {
        var s = -1
        var lastNw = -1
        var cover = 0
        for (i in a until b) {
            val c = sb[i]
            if (c == ' ' || c == '¦') continue
            if (f[i]) { if (s < 0) s = i; cover++ }
            else if (s >= 0) { into.add(Sp(s - a, lastNw + 1 - a, k)); s = -1 }
            lastNw = i
        }
        if (s >= 0) into.add(Sp(s - a, lastNw + 1 - a, k))
        return cover
    }

    private fun flushLine() {
        if (sb.isEmpty()) return
        var a = 0
        var b = sb.length
        while (a < b && (sb[a] == ' ' || sb[a] == '¦')) a++
        while (b > a && (sb[b - 1] == ' ' || sb[b - 1] == '¦')) b--
        if (b > a) {
            val sp = ArrayList<Sp>()
            val bc = ranges(a, b, bf, K.B, sp)
            ranges(a, b, itf, K.I, sp)
            var nw = 0
            for (i in a until b) if (sb[i] != ' ' && sb[i] != '¦') nw++
            out.add(CLine(sb.substring(a, b), sp, sz, x0, y0, nw > 0 && bc * 10 >= nw * 9, pg).also { it.xe = lastEnd })
        }
        sb.setLength(0); bf.clear(); itf.clear()
        sz = 0f; lastEnd = -1f
    }
}

private class Extracted(val lines: List<CLine>, val title: String?)

private val RX_PGNUM = Regex("^(page|halaman|hal\\.?|hlm\\.?)?\\s*\\d{1,4}(\\s*(of|dari|/)\\s*\\d{1,4})?$", RegexOption.IGNORE_CASE)

/** Ekstrak seluruh teks PDF jadi baris-baris. Dipanggil di thread IO. */
private fun extract(ctx: Context, file: File, check: () -> Unit, progress: (Int, Int) -> Unit): Extracted {
    PDFBoxResourceLoader.init(ctx)
    val raw = ArrayList<CLine>()
    var title: String? = null
    val d = PDDocument.load(file)
    try {
        val n = d.numberOfPages
        try { title = d.documentInformation?.title } catch (e: Throwable) { }
        for (p in 0 until n) {
            check()
            val g = LineGrab()
            g.startPage = p + 1
            g.endPage = p + 1
            try { g.getText(d) } catch (e: Throwable) { }
            raw.addAll(g.out)
            if (p % 8 == 0 || p == n - 1) progress(p + 1, n)
        }
    } finally {
        try { d.close() } catch (e: Throwable) { }
    }
    if (raw.isEmpty()) return Extracted(raw, title)

    // ukuran isi = ukuran huruf yang paling banyak hurufnya; margin kiri = posisi x yang paling sering
    val sizeW = HashMap<Int, Int>()
    val xW = HashMap<Int, Int>()
    for (l in raw) {
        val k = (l.sz * 2f).toInt()
        sizeW[k] = (sizeW[k] ?: 0) + l.t.length
        val xk = (l.x / 3f).toInt()
        xW[xk] = (xW[xk] ?: 0) + l.t.length
    }
    val body = ((sizeW.maxByOrNull { it.value }?.key ?: 20) / 2f).coerceAtLeast(4f)
    val margin = (xW.maxByOrNull { it.value }?.key ?: 0) * 3f

    val xes = raw.filter { it.t.length > 25 }.map { it.xe }.sorted()
    val right = if (xes.isEmpty()) raw.maxOf { it.xe } else xes[(xes.size * 9 / 10).coerceAtMost(xes.size - 1)]
    for (l in raw) l.short = (l.xe - margin) < 0.72f * (right - margin)

    // jarak baris tipikal (median), lalu tandai awal paragraf
    val dys = ArrayList<Float>()
    for (i in 1 until raw.size) {
        if (raw[i].page != raw[i - 1].page) continue
        val dy = raw[i].y - raw[i - 1].y
        if (dy > 0f && dy < body * 5f) dys.add(dy)
    }
    dys.sort()
    val pitch = if (dys.isEmpty()) body * 1.25f else dys[dys.size / 2]
    for (i in raw.indices) {
        val l = raw[i]
        l.rel = l.sz / body
        l.indent = l.x - margin > body * 1.2f
        if (i > 0 && raw[i - 1].page == l.page) l.gap = (l.y - raw[i - 1].y) > pitch * 1.35f
    }

    // tingkat judul ditentukan sekali untuk seluruh dokumen (ukuran huruf terbesar = H1, dst), supaya konsisten antar potongan
    val endSent = Regex("[.!?]$")
    val hs = raw.filter { it.rel >= 1.15f && it.t.length < 120 && !endSent.containsMatchIn(it.t) }
        .map { (it.sz * 2f).toInt() }.distinct().sortedDescending().take(3)
    for (l in raw) {
        if (l.t.length >= 120 || endSent.containsMatchIn(l.t)) continue
        if (l.rel >= 1.15f) { val k = hs.indexOf((l.sz * 2f).toInt()); if (k >= 0) l.hl = k + 1 }
        else if (l.bold && l.t.length < 90 && l.rel > 0.92f) l.hl = 4
    }

    // nomor halaman dan header / footer berulang: 2 baris pertama dan terakhir tiap halaman
    val byPage = HashMap<Int, ArrayList<Int>>()
    for (i in raw.indices) byPage.getOrPut(raw[i].page) { ArrayList() }.add(i)
    val rep = HashMap<String, Int>()
    fun edge(ix: ArrayList<Int>): List<Int> = (ix.take(2) + ix.takeLast(2)).distinct()
    for ((_, ix) in byPage) for (i in edge(ix)) {
        val key = raw[i].t.replace(Regex("\\d+"), "#").lowercase()
        rep[key] = (rep[key] ?: 0) + 1
    }
    val pages = byPage.size
    for ((_, ix) in byPage) for (i in edge(ix)) {
        val l = raw[i]
        val key = l.t.replace(Regex("\\d+"), "#").lowercase()
        val repeated = pages >= 4 && (rep[key] ?: 0) >= max(4, pages * 3 / 10) && l.t.length < 120
        if (RX_PGNUM.matches(l.t.trim()) || repeated) l.noise = true
    }
    return Extracted(raw, title)
}

// ---------------------------------------------------------------- unit paragraf, potongan, perintah ke Groq

private const val BUDGET = 5000

private val RX_GLYPH = Regex("^[•●▪◦·∙‣⁃■□○◆►▶]\\s*")
private val RX_ASCII = Regex("^[-–—*+]\\s+")
private val RX_NUM = Regex("^\\(?(\\d{1,3})[.)]\\s+")
private val RX_LET = Regex("^\\(?([A-Za-z])[.)]\\s+")
private val TERM = Regex("[.!?…:;\"”’)\\]]$")

private fun isMarker(t: String) = RX_GLYPH.containsMatchIn(t) || RX_ASCII.containsMatchIn(t) || RX_NUM.containsMatchIn(t)

/**
 * Gabungkan baris-baris PDF jadi "unit" (kandidat paragraf / judul / butir) dengan aturan lokal yang hati-hati:
 * baris menyambung ke atasnya kecuali ada tanda awal blok baru. Groq lalu menilai tiap unit utuh (bukan baris lepas),
 * jadi paragraf tidak terpotong dan paragraf biasa tidak salah jadi judul.
 */
private fun makeUnits(lines: List<CLine>): List<IntArray> {
    val out = ArrayList<IntArray>()
    var cur = ArrayList<Int>()
    for (i in lines.indices) {
        val l = lines[i]
        if (cur.isEmpty()) { cur.add(i); continue }
        val p = lines[cur[cur.size - 1]]
        val ends = TERM.containsMatchIn(p.t)
        val first = l.t.firstOrNull()
        val newU = when {
            l.noise || p.noise -> true
            l.t.contains('¦') || p.t.contains('¦') -> true
            isMarker(l.t) -> true
            kotlin.math.abs(l.rel - p.rel) > 0.12f -> true
            p.short && !ends && p.bold && !l.bold -> true
            l.gap && (ends || p.short) -> true
            ends && p.short && (first != null && first.isUpperCase() || l.indent) -> true
            l.indent && ends -> true
            else -> false
        }
        if (newU) { out.add(cur.toIntArray()); cur = ArrayList() }
        cur.add(i)
    }
    if (cur.isNotEmpty()) out.add(cur.toIntArray())
    return out
}

private fun unitText(lines: List<CLine>, u: IntArray): String = u.joinToString(" ") { lines[it].t }

/** Kelompokkan unit jadi potongan. Mode Best: ~1600 huruf teks penuh per potongan (Groq menulis Markdown); mode Fast: hanya awal tiap unit dikirim. */
private fun makeChunks(lines: List<CLine>, units: List<IntArray>, full: Boolean): List<IntArray> {
    val out = ArrayList<IntArray>()
    var cur = ArrayList<Int>()
    var n = 0
    val budget = if (full) 3000 else BUDGET
    val cap = if (full) 80 else 60
    fun len(k: Int) = (if (full) unitText(lines, units[k]).length else minOf(unitText(lines, units[k]).length, 240)) + 12
    for (k in units.indices) {
        val l = len(k)
        if (cur.isNotEmpty() && (n + l > budget || cur.size >= cap)) {
            // lebih suka memotong setelah unit yang berakhir kalimat, supaya paragraf tidak terbelah antar potongan
            var cut = cur.size
            val lo = max(1, cur.size * 6 / 10)
            for (j in cur.size - 1 downTo lo) {
                val prevU = units[cur[j - 1]]
                if (TERM.containsMatchIn(lines[prevU[prevU.size - 1]].t)) { cut = j; break }
            }
            out.add(cur.subList(0, cut).toIntArray())
            cur = ArrayList(cur.subList(cut, cur.size))
            n = cur.sumOf { len(it) }
        }
        cur.add(k); n += l
    }
    if (cur.isNotEmpty()) out.add(cur.toIntArray())
    return out
}

private const val SYS =
    "You classify units of text extracted from a PDF so they can be rebuilt as clean Markdown. You NEVER rewrite or repeat the text.\n" +
        "Input: numbered units like `N tags| text` (long units are shortened with …). Each unit is a candidate paragraph, heading, list item, or table row. " +
        "Tags: L = font much larger than body, M = somewhat larger, S = smaller, b = bold, ¶ = blank gap above, > = indented, " +
        "E = candidate page number or repeated header/footer, n = number of PDF lines. A ¦ in text marks a wide gap between table columns.\n" +
        "Output: one entry `N code` per unit, in order. Codes: h1 h2 h3 h4 = heading (h1 = document title; headings are short, usually have no final period, " +
        "and are NOT sentences of body text), p = normal paragraph, li = bullet item (li2 li3 nested), ol = numbered or lettered item (ol2 ol3 nested), " +
        "q = block quote, c = code, t = table row (columns separated by ¦), x = page number / running header / footer / page furniture (deleted), " +
        "+ = this unit is the continuation of the previous unit (the paragraph was wrongly split, e.g. by a page break).\n" +
        "Most units are p. Use a heading only when clearly a title or section heading. Reply with entries only.\n" +
        "Example:\n1 L| Chapter One\n2 ¶ n3| The study began in 1990 and continued for years …\n3 | 12\n4 E| 12\n5 | lazily forgotten by the end.\n6 ¶ n1| 1. First point\n" +
        "Output:\n1 h1\n2 p\n3 x\n4 x\n5 +\n6 ol"

private val RX_CODE = Regex("^(\\d{1,4})\\s*[:.\\-=)]?\\s*(h[1-4]|li[1-3]?|ol[1-3]?|p|q|c|t|x|\\+)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)

private fun promptOf(lines: List<CLine>, units: List<IntArray>, uidx: IntArray, prev: String?): String {
    val sb = StringBuilder()
    if (prev != null) sb.append("(the unit before the first one was ").append(prev).append(")\n")
    for (k in uidx.indices) {
        val u = units[uidx[k]]
        val l = lines[u[0]]
        sb.append(k + 1).append(' ')
        if (l.rel >= 1.45f) sb.append('L') else if (l.rel >= 1.15f) sb.append('M') else if (l.rel <= 0.88f) sb.append('S')
        if (u.all { lines[it].bold }) sb.append('b')
        if (l.noise) sb.append('E')
        if (l.gap) sb.append('¶')
        if (l.indent) sb.append('>')
        if (u.size > 1) sb.append('n').append(u.size)
        var t = unitText(lines, u).replace("¦", " ¦ ")
        if (t.length > 240) t = t.substring(0, 170) + " … " + t.substring(t.length - 50)
        sb.append("| ").append(t).append('\n')
    }
    return sb.toString()
}

/** Entri valid dari jawaban Groq ("n kode;n kode"), atau null bila jawabannya tidak bisa dipakai. */
private fun parseCodes(raw: String, m: Int): String? {
    val sb = StringBuilder()
    var last = 0
    var cnt = 0
    for (ln0 in raw.lines()) {
        var ln = ln0.trim()
        if (ln.startsWith("```") || ln.isEmpty()) continue
        ln = ln.removePrefix("-").removePrefix("*").trim()
        val mm = RX_CODE.find(ln) ?: continue
        val n = mm.groupValues[1].toIntOrNull() ?: continue
        if (n < 1 || n > m || n <= last) continue
        sb.append(n).append(' ').append(mm.groupValues[2].lowercase()).append(';')
        last = n; cnt++
    }
    if (cnt * 2 < m) return null   // jawaban terlalu sedikit: dianggap gagal
    return sb.toString()
}

private const val SYS_MD =
    "You are a Markdown typesetter. The user sends units of text extracted from a PDF (each unit is a paragraph, heading, list item or table row, separated by blank lines; consecutive table rows are on consecutive lines). " +
        "Rebuild them as clean, consistent, well-structured Markdown WITHOUT changing a single word.\n" +
        "Rules:\n" +
        "- Keep every word, number and symbol in the same order. Never add, remove, rephrase, translate, fix spelling, or summarize. No commentary, no code fences.\n" +
        "- A unit is normally one paragraph on ONE line. Keep paragraphs on one long line (no hard wraps), separated by one blank line. If a unit clearly continues the previous one (a sentence cut in the middle), join them into one paragraph.\n" +
        "- Heading tags: ⟦H1⟧ ⟦H2⟧ ⟦H3⟧ at a unit start give the heading level decided for the whole document: output # / ## / ### accordingly (unless the unit is obviously not a heading). ⟦H4?⟧ is only a weak hint: use #### only if it is clearly a short heading label. Units without a tag are NOT headings. NEVER output the tags.\n" +
        "- Lists: `- ` for bullets, `1. ` for numbered items (keep the original number); drop the original bullet glyph. Nested items are indented by 2 spaces.\n" +
        "- Block quote `> ` only for clearly quoted passages.\n" +
        "- Tables: rows whose columns are separated by ¦ (a wide gap) and line up from row to row become ONE Markdown pipe table: header row, then a `| --- | --- |` separator, then the rows. Cells keep their original words; a cell wrapped over several rows becomes one cell. Remove the ¦ marks.\n" +
        "- Keep **bold** and *italic* markers and backslash escapes exactly as given.\n" +
        "- CONSISTENCY: the user may show the end of the previous output in <previous_output_end>. Continue in exactly the same conventions: the same heading levels, and the same style for items of the same kind (for example every entry of a reference list or bibliography uses the same format: all bullets or all plain paragraphs, matching what came before). If the previous output ended inside a table and the new units continue it, output the continuing rows as a pipe table with the same number of columns (repeat the original header row first).\n" +
        "Output only the Markdown for the units inside <units>; never repeat <previous_output_end>."

private val RX_TAG = Regex("⟦[^⟧]*⟧")
private val RX_WORD = Regex("[\\p{L}\\p{N}]+")

private fun wordsOf(s: String): List<String> = RX_WORD.findAll(RX_TAG.replace(s, " ").lowercase()).map { it.value }.toList()

private fun unfenceMd(s: String): String {
    var t = s.trim()
    if (t.startsWith("```")) {
        t = t.substringAfter("\n", "")
        t = t.trimEnd().removeSuffix("```")
    }
    return t.trim()
}

/** Satu baris PDF dengan penanda **tebal** dan *miring* dari jenis huruf; karakter yang berarti di Markdown di-escape. */
private fun styled(l: CLine): String {
    val t = l.t
    val spans = ArrayList<Sp>()
    for (s in l.sp) {
        if (s.k == K.B && l.bold) continue   // baris tebal penuh biasanya judul; cukup jadi petunjuk ⟦b⟧
        spans.add(s)
    }
    val keep = spans.filter { i ->
        i.k != K.I || spans.none { b -> b.k == K.B && b.s < i.e && b.e > i.s && !(b.s <= i.s && b.e >= i.e) && !(i.s <= b.s && i.e >= b.e) }
    }
    val open = HashMap<Int, StringBuilder>()
    val close = HashMap<Int, StringBuilder>()
    for (s in keep) {
        val m = if (s.k == K.B) "**" else "*"
        open.getOrPut(s.s) { StringBuilder() }.append(m)
        close.getOrPut(s.e) { StringBuilder() }.append(m)
    }
    val sb = StringBuilder()
    for (i in 0..t.length) {
        close[i]?.let { sb.append(it) }
        open[i]?.let { sb.append(it) }
        if (i < t.length) {
            val c = t[i]
            if (c == '¦') sb.append(" ¦ ")
            else {
                if (c == '*' || c == '_' || c == '`' || c == '|' || c == '~') sb.append('\\')
                sb.append(c)
            }
        }
    }
    return sb.toString()
}

/** Teks sumber satu potongan untuk mode Best: satu unit (paragraf) per baris. Nomor halaman / header-footer berulang sudah dibuang. */
private fun rawOf(lines: List<CLine>, units: List<IntArray>, uidx: IntArray): String {
    val sb = StringBuilder()
    var prevTable = false
    for (k in uidx) {
        val u = units[k].filter { !lines[it].noise }
        if (u.isEmpty()) continue
        val first = lines[u[0]]
        val tbl = u.any { lines[it].t.contains('¦') }
        val t = StringBuilder()
        var prev: CLine? = null
        for (i in u) {
            val l = lines[i]
            val p = prev
            if (p != null) {
                val hy = p.t.length >= 2 && p.t.last() == '-' && p.t[p.t.length - 2].isLetter() &&
                    l.t.isNotEmpty() && l.t[0].isLowerCase() && !p.t.contains('¦') && !l.t.contains('¦')
                if (!hy) t.append(' ')
            }
            t.append(styled(l))
            prev = l
        }
        if (sb.isNotEmpty()) sb.append(if (tbl && prevTable) "\n" else "\n\n")
        val tag = when (first.hl) { 1 -> "⟦H1⟧ "; 2 -> "⟦H2⟧ "; 3 -> "⟦H3⟧ "; 4 -> "⟦H4?⟧ "; else -> "" }
        sb.append(tag).append(t)
        prevTable = tbl
    }
    return sb.toString()
}

/** Ujung keluaran potongan sebelumnya (maks ~700 huruf, mulai di awal baris) sebagai konteks gaya untuk potongan berikutnya. */
private fun tailOf(md: String): String {
    if (md.length <= 700) return md
    val t = md.takeLast(700)
    val nl = t.indexOf('\n')
    return if (nl in 0..250) t.substring(nl + 1) else t
}

/** Tebakan lokal untuk satu unit (dipakai bila Groq gagal, atau tidak menyebut unit itu). Teks tetap utuh. */
private fun heurUnit(lines: List<CLine>, u: IntArray): String {
    val l = lines[u[0]]
    val t = unitText(lines, u)
    val sentence = Regex("[.!?]$").containsMatchIn(t)
    return when {
        l.noise -> "x"
        t.contains('¦') -> "t"
        RX_GLYPH.containsMatchIn(t) || RX_ASCII.containsMatchIn(t) -> "li"
        RX_NUM.containsMatchIn(t) -> "ol"
        l.rel >= 1.45f && t.length < 100 && !sentence -> "h1"
        l.rel >= 1.15f && t.length < 100 && !sentence -> "h2"
        u.all { lines[it].bold } && t.length < 90 && !sentence -> "h3"
        else -> "p"
    }
}

// ---------------------------------------------------------------- perakitan blok Markdown native

private class OpenB(val kind: String) {
    val ls = ArrayList<CLine>()
    val rows = ArrayList<Int>()   // tabel: indeks awal tiap baris tabel di [ls]
}

private fun joinRt(ls: List<CLine>, stripBold: Boolean): Rt {
    val sb = StringBuilder()
    val sp = ArrayList<Sp>()
    for (l in ls) {
        if (sb.isNotEmpty()) {
            val hy = sb[sb.length - 1] == '-' && sb.length >= 2 && sb[sb.length - 2].isLetter() &&
                l.t.isNotEmpty() && l.t[0].isLowerCase()
            if (!hy) sb.append(' ')
        }
        val off = sb.length
        sb.append(l.t.replace('¦', ' '))   // sama panjang, jadi posisi tebal / miring tetap pas
        for (s in l.sp) sp.add(Sp(s.s + off, s.e + off, s.k))
    }
    val t = sb.toString()
    return Rt(t, normSp(sp.filter { !(stripBold && it.k == K.B) }, t.length))
}

private fun emit(o: OpenB, out: MutableList<Blk>) {
    if (o.ls.isEmpty()) return
    val k = o.kind
    when {
        k.startsWith("h") -> out.add(Blk(ty = "h", lv = (k.substring(1).toIntOrNull() ?: 2).coerceIn(1, 4), r = joinRt(o.ls, true)))
        k.startsWith("li") || k.startsWith("ol") -> {
            val depth = ((k.substring(2).toIntOrNull() ?: 1) - 1).coerceIn(0, 3)
            var rt = joinRt(o.ls, false)
            if (k.startsWith("li")) {
                val m = RX_GLYPH.find(rt.t) ?: RX_ASCII.find(rt.t)
                if (m != null) rt = rt.slice(m.range.last + 1, rt.t.length)
                out.add(Blk(ty = "li", lt = "b", d = depth, r = rt))
            } else {
                val mn = RX_NUM.find(rt.t)
                val ml = if (mn == null) RX_LET.find(rt.t) else null
                if (mn != null) {
                    val st = mn.groupValues[1].toIntOrNull() ?: 0
                    out.add(Blk(ty = "li", lt = "o", d = depth, st = st, r = rt.slice(mn.range.last + 1, rt.t.length)))
                } else if (ml != null) {
                    val st = Character.toLowerCase(ml.groupValues[1][0]) - 'a' + 1
                    out.add(Blk(ty = "li", lt = "a", d = depth, st = st, r = rt.slice(ml.range.last + 1, rt.t.length)))
                } else out.add(Blk(ty = "li", lt = "o", d = depth, r = rt))
            }
        }
        k == "q" -> out.add(Blk(ty = "q", k = "quote", r = joinRt(o.ls, false)))
        k == "c" -> out.add(Blk(ty = "code", r = Rt(o.ls.joinToString("\n") { it.t.replace('¦', ' ') })))
        k == "t" -> {
            val starts = if (o.rows.isEmpty()) listOf(0) else o.rows
            val rows = ArrayList<String>()
            for (i in starts.indices) {
                val a = starts[i]
                val b = if (i + 1 < starts.size) starts[i + 1] else o.ls.size
                rows.add(o.ls.subList(a, b).joinToString(" ") { it.t })
            }
            val cells = rows.map { r -> r.split('¦').map { it.trim() } }
            val cols = cells.maxOfOrNull { it.size } ?: 0
            if (rows.size >= 2 && cols in 2..12) {
                out.add(Blk(
                    ty = "tbl",
                    tb = cells.map { c -> (0 until cols).map { Rt(c.getOrElse(it) { "" }) } },
                    ca = List(cols) { "left" }
                ))
            } else {
                for (r in rows) out.add(Blk(ty = "p", r = Rt(r.replace('¦', ' '))))
            }
        }
        else -> out.add(Blk(ty = "p", r = joinRt(o.ls, false)))
    }
}

private fun unitBlocks(lines: List<CLine>, units: List<IntArray>, uidx: IntArray, str: String?): List<Blk> {
    val code = Array(uidx.size) { heurUnit(lines, units[uidx[it]]) }
    if (str != null && str != "~" && !str.startsWith("M:")) {
        for (e in str.split(';')) {
            val sp = e.trim().split(' ')
            if (sp.size != 2) continue
            val n = sp[0].toIntOrNull() ?: continue
            if (n in 1..uidx.size) code[n - 1] = sp[1]
        }
    }
    val out = ArrayList<Blk>()
    var cur: OpenB? = null
    fun close() {
        cur?.let { emit(it, out) }
        cur = null
    }
    for (k in uidx.indices) {
        val u = units[uidx[k]]
        val c = code[k]
        if (c == "x") continue   // nomor halaman / header / footer dibuang
        val o = cur
        if (c == "+" && o != null) { for (i in u) o.ls.add(lines[i]); continue }
        if (c == "t" && o != null && o.kind == "t") {
            o.rows.add(o.ls.size)
            for (i in u) o.ls.add(lines[i])
            continue
        }
        close()
        val no = OpenB(if (c == "+") "p" else c)
        if (no.kind == "t") no.rows.add(0)
        for (i in u) no.ls.add(lines[i])
        cur = no
    }
    close()
    return out
}

/** Markdown hasil Groq -> blok native Dinduy. Paragraf dijadikan satu baris (tanpa enter paksa dari pemenggalan PDF). */
private fun mdBlocks(text: String): List<Blk> {
    val out = ArrayList<Blk>()
    for (b in md(text)) {
        if (b.ty == "p" || b.ty == "li" || b.ty == "h") {
            val t = b.r.t.replace('\n', ' ')
            if (b.ty == "p" && t.isBlank()) continue
            out.add(b.copy(r = b.r.copy(t = t)))
        } else out.add(b)
    }
    return out
}

/** Paragraf yang terbelah di batas potongan disambung lagi. */
private fun appendMerged(out: MutableList<Blk>, bl: List<Blk>) {
    var rest = bl
    val last = out.lastOrNull()
    val first = bl.firstOrNull()
    // tabel yang berlanjut di potongan berikutnya disatukan lagi
    if (last != null && first != null && last.ty == "tbl" && first.ty == "tbl" &&
        last.tb.isNotEmpty() && first.tb.isNotEmpty() && last.tb[0].size == first.tb[0].size
    ) {
        val dup = first.tb[0].map { it.t.trim() } == last.tb[0].map { it.t.trim() }
        out[out.size - 1] = last.copy(tb = last.tb + first.tb.drop(if (dup) 1 else 0))
        out.addAll(bl.drop(1))
        return
    }
    if (last != null && first != null && last.ty == "p" && first.ty == "p" &&
        last.r.t.isNotBlank() && first.r.t.isNotBlank() &&
        !TERM.containsMatchIn(last.r.t.trimEnd()) && first.r.t.trimStart().first().isLowerCase()
    ) {
        out[out.size - 1] = last.copy(r = last.r + Rt(" ") + first.r)
        rest = bl.drop(1)
    }
    out.addAll(rest)
}

private val RX_BIB = Regex("^(daftar pustaka|daftar referensi|referensi|references|bibliography|sumber pustaka|pustaka)\\b", RegexOption.IGNORE_CASE)

/** Daftar pustaka dibuat seragam: semua butir pakai poin, atau semuanya paragraf polos, mengikuti mayoritas. */
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

private fun assemble(lines: List<CLine>, units: List<IntArray>, chunks: List<IntArray>, done: List<String?>): List<Blk> {
    val out = ArrayList<Blk>()
    for (c in chunks.indices) {
        val s = done.getOrNull(c)
        val bl = if (s != null && s.startsWith("M:")) mdBlocks(s.substring(2)) else unitBlocks(lines, units, chunks[c], s)
        appendMerged(out, bl)
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

@Serializable
class ConvCk(val nid: String, val src: String, val n: Int = 0, val done: List<String?> = emptyList(), val mode: Int = 1)

private class Pause(msg: String) : Exception(msg)

object PdfConv {
    /** Catatan yang sedang / pernah berhenti di tengah konversi. Ada di sini = layar skeleton tampil. */
    val map = mutableStateMapOf<String, ConvState>()
    /** Hanya selama animasi geser masuk: id PDF asal yang masih terlihat di bawah halaman baru. */
    val under = mutableStateMapOf<String, String>()
    private val jobs = HashMap<String, Job>()
    private val cool = HashMap<String, Long>()
    private var restored = false

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
            s.total = ck.n
            s.done = ck.done.count { it != null }
            s.msg = "The conversion was interrupted. Tap Try again to continue where it stopped."
            map[id] = s
        }
    }

    /** Notifikasi berjalan (dipakai AppVM.bgSnap). Null bila tidak ada konversi yang sedang jalan. */
    fun snap(): Bg.Snap? {
        val s = map.values.firstOrNull { it.phase < 2 } ?: return null
        val p = if (s.total > 0) (s.done * 100 / s.total).coerceIn(0, 99) else -1
        val txt = if (s.total > 0) "Part " + (s.done + 1).coerceAtMost(s.total) + " of " + s.total else "Reading PDF…"
        return Bg.Snap("Converting PDF to note", txt, p, false, false)
    }

    /** Mulai konversi PDF [srcId]. Membuat catatan baru, pindah ke sana (geser masuk), lalu bekerja di latar belakang. */
    fun begin(vm: AppVM, srcId: String, mode: Int = 1): Boolean {
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
        saveCk(ctx, ConvCk(id, src.pdf, mode = mode))
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
        return "Groq's free limit is reached for now. Your progress is saved. Tap Try again in $w."
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

    /** Satu potongan: panggil Groq (dengan model cadangan dan penanganan limit) sampai [accept] menerima jawabannya. "~" = pakai aturan lokal. */
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
                val t = (tries[m] ?: 0) + 1
                tries[m] = t
                if (t >= 2) skip.add(m)   // model ini dua kali tidak setia / tidak sesuai format: coba model lain, atau aturan lokal
            } catch (e: GroqException) {
                when {
                    e.code == 429 -> {
                        val w = if (e.wait > 0) e.wait else 3
                        rl++
                        if (w <= 75 && rl <= 6) waitSec(st, w, "Rate limit reached")
                        else cool[m] = System.currentTimeMillis() + (if (w > 75) w else 120) * 1000L
                    }
                    e.code == 401 || e.code == 403 -> throw Pause(enMsg(e))
                    e.code == 413 -> skip.add(m)
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
            ?: throw Pause("The conversion data is missing. Cancel, then start again from the PDF.")
        val pdf = PdfFiles.file(ctx, ck0.src)
        if (!pdf.exists()) throw Pause("The original PDF is missing, so it can't be converted.")
        val key = withContext(Dispatchers.IO) { vm.vault.load() }
            ?: throw Pause("Groq API key not found. Add it in Voice transcription settings, then tap Try again.")

        st.msg = "Reading PDF…"
        val ex = withContext(Dispatchers.IO) {
            extract(ctx, pdf, { ensureActive() }) { p, n -> st.msg = "Reading PDF… page $p of $n" }
        }
        st.msg = ""
        val lines = ex.lines
        if (lines.none { !it.noise }) throw Pause("This PDF has no extractable text (it may be a scan), so it can't be converted.")
        val units = makeUnits(lines)
        val full = ck0.mode == 1
        val chunks = makeChunks(lines, units, full)
        val done = ArrayList<String?>()
        if (ck0.n == chunks.size && ck0.done.size == chunks.size) done.addAll(ck0.done) else repeat(chunks.size) { done.add(null) }
        st.total = chunks.size
        st.done = done.count { it != null }

        val models = try {
            withContext(Dispatchers.IO) { Groq.layoutModels(key) }
        } catch (e: GroqException) {
            throw Pause(enMsg(e))
        }
        if (models.isEmpty()) throw Pause("No Groq chat model is available for this key.")

        st.phase = 1
        for (i in chunks.indices) {
            if (done[i] != null) continue
            currentCoroutineContext().ensureActive()
            if (vm.note(id) == null) {   // catatan dihapus saat konversi berjalan
                withContext(Dispatchers.IO) { try { ckFile(ctx, id).delete() } catch (e: Exception) { } }
                map.remove(id); jobs.remove(id)
                return
            }
            val codes: String
            if (full) {
                val raw = rawOf(lines, units, chunks[i])
                if (raw.isBlank()) codes = "~"
                else {
                    val want = wordsOf(raw)
                    val tail = if (i > 0) done[i - 1]?.takeIf { it.startsWith("M:") }?.let { tailOf(it.substring(2)) } else null
                    val user = (if (tail != null) "<previous_output_end>\n$tail\n</previous_output_end>\n" else "") + "<units>\n$raw\n</units>"
                    codes = planChunk(key, models, SYS_MD, user, (raw.length / 2 + 400).coerceIn(600, 3600), st) { out ->
                        val m = unfenceMd(out)
                        if (m.isNotBlank() && wordsOf(m) == want) "M:$m" else null
                    }
                }
            } else {
                val prev = if (i > 0) done[i - 1]?.trimEnd(';')?.substringAfterLast(' ')?.takeIf { it.isNotEmpty() && it != "~" && !it.startsWith("M:") } else null
                codes = planChunk(key, models, SYS, promptOf(lines, units, chunks[i], prev), (chunks[i].size * 7 + 60).coerceIn(120, 1400), st) { parseCodes(it, chunks[i].size) }
            }
            done[i] = codes
            withContext(Dispatchers.IO) { saveCk(ctx, ConvCk(id, ck0.src, chunks.size, done.toList(), ck0.mode)) }
            st.done = done.count { it != null }
            delay(900)   // jaga RPM Groq gratisan
        }

        st.msg = "Finishing…"
        val blocks = withContext(Dispatchers.Default) { assemble(lines, units, chunks, done) }
        withContext(Dispatchers.Main) {
            vm.upd(id) { it.copy(bl = blocks, x = plainOf(blocks), u = System.currentTimeMillis()) }
        }
        withContext(Dispatchers.IO) { try { ckFile(ctx, id).delete() } catch (e: Exception) { } }
        val title = vm.note(id)?.t ?: ""
        map.remove(id)
        jobs.remove(id)
        Bg.done(ctx, "Note ready", title)
        val fb = done.count { it == "~" }
        val tail = if (fb > 0) " ($fb of ${done.size} parts used basic formatting)" else ""
        if (vm.nav.stack.none { it is Scr.Note && it.id == id }) vm.show("Your converted note is ready" + tail)
        else if (fb > 0) vm.show("Done" + tail)
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
                    else -> "Structuring · part " + (s.done + 1).coerceAtMost(s.total.coerceAtLeast(1)) + " of " + s.total
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

@Composable
fun ConvModeDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val pal = LocalPal.current
    @Composable
    fun Opt(title: String, sub: String, mode: Int) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.c4)
                .clickable { onPick(mode) }.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            T(title, 16f, 800, pal.os)
            T(sub, 13f, 400, pal.osv, Modifier.padding(top = 2.dp), lh = 1.3f)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.c3,
        title = { T("Convert to Note", 22f, 900, pal.os) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Opt("Best quality", "Groq lays out the text itself: tidy paragraphs, headings, lists and tables. Every result is checked word by word. Uses more of your free Groq limit, so a long book can take a few days (it resumes where it stopped).", 1)
                Opt("Fast", "Groq only labels each paragraph. Much lighter on the free limit, but tables and spacing are less polished.", 0)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 16f, 700, pal.osv) } }
    )
}

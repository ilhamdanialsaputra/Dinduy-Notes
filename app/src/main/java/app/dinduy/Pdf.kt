package app.dinduy

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.dinduy.silkcurl.SilkController
import app.dinduy.silkcurl.SilkGl
import app.dinduy.silkcurl.SilkGlView
import app.dinduy.silkcurl.SilkPage
import app.dinduy.silkcurl.SilkShape

// =====================================================================================
//  PDF di Dinduy: tiap PDF adalah satu "catatan" (Note.pdf = id berkas) supaya ikut label, sematan,
//  arsip, sampah, dan morph buka/tutup di beranda. Berkas disalin ke filesDir/pdf/<id>.pdf.
// =====================================================================================

object PdfFiles {
    fun dir(ctx: Context) = File(ctx.filesDir, "pdf").apply { mkdirs() }
    fun file(ctx: Context, id: String) = File(dir(ctx), "$id.pdf")
    fun ink(ctx: Context, id: String) = File(dir(ctx), "$id.ink.json")
}

/** Dipakai menu titik tiga PDF dan layar baca untuk saling memberi tahu (tanpa mengubah AppVM). */
object PdfBus {
    var find by mutableIntStateOf(0)       // dinaikkan menu: buka bilah cari
    var hasText by mutableStateOf(false)   // PDF punya lapisan teks (bukan hasil scan)
    var mode by mutableIntStateOf(-1)      // 0 gulir, 1 halaman; -1 belum dimuat
    var curl by mutableIntStateOf(-1)      // 1 = efek balik halaman 3D (hanya mode halaman); -1 belum dimuat
    var curlStyle by mutableIntStateOf(-1) // gaya 3D: 0 Classic curl, 1 Silk curl; -1 belum dimuat
}

/** Hapus berkas PDF / coretan yang tidak dipakai catatan mana pun lagi. */
fun AppVM.gcPdf() {
    val ctx = getApplication<Application>()
    val keep = docs.mapNotNull { it.pdf.takeIf { p -> p.isNotEmpty() } }.toSet()
    val limit = System.currentTimeMillis() - 120_000L
    PdfFiles.dir(ctx).listFiles()?.forEach { f ->
        val id = f.name.substringBefore('.')
        if (id !in keep && f.lastModified() < limit) f.delete()
    }
}

fun isPdfUri(ctx: Context, u: Uri): Boolean {
    val t = try { ctx.contentResolver.getType(u) } catch (e: Exception) { null }
    if (t == "application/pdf") return true
    val n = pdfName(ctx, u)
    return n.endsWith(".pdf", true)
}

private fun pdfName(ctx: Context, u: Uri): String {
    var name = ""
    try {
        ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: ""
        }
    } catch (e: Exception) { }
    if (name.isEmpty()) name = u.lastPathSegment?.substringAfterLast('/') ?: ""
    return name
}

/** Salin PDF-PDF ke penyimpanan aplikasi dan buat satu catatan PDF untuk tiap berkas. Berkas yang rusak / berkata sandi dilewati. */
suspend fun AppVM.importPdfs(uris: List<Uri>): List<Note> = withContext(Dispatchers.IO) {
    val ctx = getApplication<Application>()
    val out = ArrayList<Note>()
    for (u in uris) {
        val id = uid()
        val f = PdfFiles.file(ctx, id)
        try {
            ctx.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: continue
            var pages = 0
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val r = PdfRenderer(pfd)
                try { pages = r.pageCount } finally { r.close() }
            } finally { pfd.close() }
            if (pages <= 0) { f.delete(); continue }
            val title = pdfName(ctx, u).replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "").ifBlank { "PDF" }
            val t = System.currentTimeMillis()
            val cv = makePdfCover(this@importPdfs, f)
            val n = Note(id = id, t = title, x = "PDF · " + cnt(pages, "page"), u = t, c = t, lb = listOfNotNull(filter), pdf = id, cv = cv, cx = .5f, cy = 0f, cz = 1f)
            withContext(Dispatchers.Main) { docs.add(0, n); save() }
            out.add(n)
        } catch (e: Throwable) {
            f.delete()
        }
    }
    out
}

/** Render halaman pertama PDF jadi sampul (img/<id>.jpg) supaya kartu beranda dan morph buka/tutup memakai gambar PDF-nya. */
fun makePdfCover(vm: AppVM, pdf: File): String {
    try {
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val r = PdfRenderer(pfd)
            try {
                val pg = r.openPage(0)
                try {
                    val w = 900
                    val h = (w * pg.height.toFloat() / pg.width.coerceAtLeast(1)).roundToInt().coerceIn(1, 2400)
                    val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bm.eraseColor(android.graphics.Color.WHITE)
                    pg.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val id = uid()
                    vm.imgDir.mkdirs()
                    File(vm.imgDir, "$id.jpg").outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                    bm.recycle()
                    return id
                } finally { pg.close() }
            } finally { r.close() }
        } finally { pfd.close() }
    } catch (e: Throwable) { return "" }
}

// ---------------------------------------------------------------- render

object PdfCache {
    private val c = object : android.util.LruCache<String, Bitmap>(maxOf(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    fun get(k: String): Bitmap? = c.get(k)
    fun put(k: String, b: Bitmap) { c.put(k, b) }
}

private fun bucket(px: Int): Int = (((px + 255) / 256) * 256).coerceIn(256, 2400)

/** Satu PDF yang sedang dibuka: PdfRenderer (bawaan Android) hanya boleh membuka satu halaman sekaligus, jadi semua akses dikunci. */
@Stable
class PdfDoc(private val file: File) {
    private var pfd: ParcelFileDescriptor? = null
    private var rend: PdfRenderer? = null
    private val lock = Mutex()
    var count by mutableIntStateOf(0)
    var ok by mutableStateOf(false)
    var failed by mutableStateOf(false)
    val ratio = androidx.compose.runtime.mutableStateListOf<Float>()   // tinggi / lebar tiap halaman; 0 = belum diketahui

    suspend fun open() = withContext(Dispatchers.IO) {
        try {
            val p = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = p
            val r = PdfRenderer(p)
            rend = r
            val n = r.pageCount
            val l = ArrayList<Float>(n)
            repeat(n) { l.add(0f) }
            withContext(Dispatchers.Main) { ratio.addAll(l); count = n; ok = n > 0; failed = n <= 0 }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) { failed = true }
        }
    }

    /** Ukur semua halaman di latar belakang supaya tinggi daftar tepat (halaman yang belum terukur memakai halaman pertama). */
    suspend fun loadRatios() = withContext(Dispatchers.IO) {
        for (i in 0 until count) {
            val rt = lock.withLock {
                val r = rend ?: return@withLock 0f
                try {
                    val p = r.openPage(i)
                    try { p.height.toFloat() / p.width.coerceAtLeast(1) } finally { p.close() }
                } catch (e: Throwable) { 0f }
            }
            if (rt > 0f) withContext(Dispatchers.Main) { if (i < ratio.size) ratio[i] = rt }
            if (i % 8 == 7) yield()
        }
    }

    fun ratioOf(i: Int): Float {
        val r = ratio.getOrNull(i) ?: 0f
        if (r > 0f) return r
        return ratio.firstOrNull { it > 0f } ?: 1.4142f
    }

    suspend fun render(i: Int, wPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            val r = rend ?: return@withLock null
            try {
                val p = r.openPage(i)
                try {
                    val w = wPx.coerceIn(64, 2400)
                    val h = (w * p.height.toFloat() / p.width.coerceAtLeast(1)).roundToInt().coerceIn(1, 6000)
                    val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bm.eraseColor(android.graphics.Color.WHITE)
                    p.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bm
                } finally { p.close() }
            } catch (e: Throwable) { null }
        }
    }

    fun close() {
        val r = rend; val p = pfd
        rend = null; pfd = null
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                try { r?.close() } catch (e: Throwable) { }
                try { p?.close() } catch (e: Throwable) { }
            }
        }
    }
}

// ---------------------------------------------------------------- teks PDF (untuk pencarian)

/** Teks satu halaman + kotak tiap huruf (pecahan lebar/tinggi halaman: kiri, atas, kanan, bawah). Spasi / baris baru = null. */
class PgText(val t: String, val r: List<FloatArray?>, val lb: Set<Int> = emptySet())

private class Grab : PDFTextStripper() {
    val sb = StringBuilder()
    val rs = ArrayList<FloatArray?>()
    val lbs = HashSet<Int>()   // indeks pemisah baris (untuk salin)
    init { setSortByPosition(true) }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        for (tp in textPositions) {
            val u = tp.unicode ?: continue
            val pw = tp.pageWidth
            val ph = tp.pageHeight
            if (pw <= 0f || ph <= 0f) continue
            val x = tp.xDirAdj
            val y = tp.yDirAdj
            val w = tp.widthDirAdj
            val h = tp.heightDir
            val box = floatArrayOf(x / pw, (y - h) / ph, (x + w) / pw, (y + h * .22f) / ph)
            for (ch in u) { sb.append(ch); rs.add(box) }
        }
    }

    override fun writeWordSeparator() { sb.append(' '); rs.add(null) }
    override fun writeLineSeparator() { lbs.add(sb.length); sb.append(' '); rs.add(null) }
}

@Stable
class PdfText(private val ctx: Context, private val file: File) {
    private var doc: PDDocument? = null
    private var broken = false
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, PgText>()
    private val lock = Mutex()

    private fun docNow(): PDDocument? {
        if (broken) return null
        doc?.let { return it }
        return try {
            PDFBoxResourceLoader.init(ctx)
            PDDocument.load(file).also { doc = it }
        } catch (e: Throwable) { broken = true; null }
    }

    /** Teks halaman bila sudah pernah dimuat (aman dipanggil dari UI), kalau belum null. */
    fun peek(i: Int): PgText? = cache[i]

    suspend fun page(i: Int): PgText? = withContext(Dispatchers.IO) {
        lock.withLock {
            cache[i]?.let { return@withLock it }
            val d = docNow() ?: return@withLock null
            try {
                val g = Grab()
                g.startPage = i + 1
                g.endPage = i + 1
                g.getText(d)
                val p = PgText(g.sb.toString(), g.rs, g.lbs)
                cache[i] = p
                p
            } catch (e: Throwable) { null }
        }
    }

    /** True bila PDF punya lapisan teks (beberapa halaman pertama berisi huruf); PDF hasil scan = false. */
    suspend fun detect(count: Int): Boolean {
        for (i in 0 until min(count, 8)) {
            val p = page(i) ?: continue
            if (p.t.count { !it.isWhitespace() } >= 12) return true
        }
        return false
    }

    fun close() {
        val d = doc
        doc = null
        CoroutineScope(Dispatchers.IO).launch { lock.withLock { try { d?.close() } catch (e: Throwable) { } } }
    }
}

class PHit(val page: Int, val rects: List<FloatArray>, val snip: String, val ms: Int, val me: Int)

private fun hitsIn(page: Int, pt: PgText, q: String): List<PHit> {
    val out = ArrayList<PHit>()
    var i = pt.t.indexOf(q, 0, ignoreCase = true)
    while (i >= 0) {
        val e = i + q.length
        val rs = ArrayList<FloatArray>()
        var cur: FloatArray? = null
        for (k in i until e) {
            val r = pt.r.getOrNull(k) ?: continue
            val c = cur
            if (c != null && abs(r[1] - c[1]) < (c[3] - c[1]) * .6f) {
                c[0] = min(c[0], r[0]); c[1] = min(c[1], r[1]); c[2] = max(c[2], r[2]); c[3] = max(c[3], r[3])
            } else { val n = r.copyOf(); cur = n; rs.add(n) }
        }
        if (rs.isNotEmpty()) {
            val a = max(0, i - 30)
            val b = min(pt.t.length, e + 50)
            out.add(PHit(page, rs, pt.t.substring(a, b), i - a, e - a))
        }
        i = pt.t.indexOf(q, i + max(1, q.length), ignoreCase = true)
    }
    return out
}

// ---------------------------------------------------------------- seleksi teks (khusus PDF non-scan)

data class PPos(val p: Int, val c: Int) : Comparable<PPos> {
    override fun compareTo(other: PPos): Int = if (p != other.p) p.compareTo(other.p) else c.compareTo(other.c)
}

/** a = awal (termasuk), b = akhir (tidak termasuk); a < b. */
class PSel(val a: PPos, val b: PPos)

private fun rangeOn(s: PSel, p: Int, t: PgText): Pair<Int, Int>? {
    if (p < s.a.p || p > s.b.p) return null
    val a = if (p == s.a.p) s.a.c else 0
    val b = if (p == s.b.p) min(s.b.c, t.t.length) else t.t.length
    return if (b > a) a to b else null
}

/** Kotak sorotan per baris untuk huruf [i, e). */
private fun rectsOf(pt: PgText, i: Int, e: Int): List<FloatArray> {
    val rs = ArrayList<FloatArray>()
    var cur: FloatArray? = null
    for (k in i until e) {
        val r = pt.r.getOrNull(k) ?: continue
        val c = cur
        if (c != null && abs(r[1] - c[1]) < (c[3] - c[1]) * .6f) {
            c[0] = min(c[0], r[0]); c[1] = min(c[1], r[1]); c[2] = max(c[2], r[2]); c[3] = max(c[3], r[3])
        } else { val n = r.copyOf(); cur = n; rs.add(n) }
    }
    return rs
}

/** Huruf terdekat dari titik (x, y) (pecahan lebar / tinggi halaman); ar = tinggi / lebar. -1 bila tak ada teks. */
private fun nearestChar(pt: PgText, x: Float, y: Float, ar: Float): Int {
    var best = -1; var bs = Float.MAX_VALUE
    for (k in pt.r.indices) {
        val r = pt.r[k] ?: continue
        val dx = if (x < r[0]) r[0] - x else if (x > r[2]) x - r[2] else 0f
        val dy = (if (y < r[1]) r[1] - y else if (y > r[3]) y - r[3] else 0f) * ar
        val sc = dy * 4f + dx
        if (sc < bs) { bs = sc; best = k }
    }
    return best
}

/** Posisi kursor (di antara dua huruf) terdekat dari titik. */
private fun caretAt(pt: PgText, x: Float, y: Float, ar: Float): Int {
    val k = nearestChar(pt, x, y, ar)
    if (k < 0) return -1
    val r = pt.r[k] ?: return -1
    return if (x > (r[0] + r[2]) / 2f) k + 1 else k
}

private fun handleXY(pt: PgText, a: Int, b: Int, start: Boolean, w: Float, h: Float): Offset? {
    if (start) { for (k in a until b) { val r = pt.r.getOrNull(k) ?: continue; return Offset(r[0] * w, r[3] * h) } }
    else { for (k in b - 1 downTo a) { val r = pt.r.getOrNull(k) ?: continue; return Offset(r[2] * w, r[3] * h) } }
    return null
}

private val SEL_COLOR = Color(0xFF4F8CFF)

/** Jari-jari tetesan gagang seleksi (dp). Titik sudut lancipnya menempel di tepi teks. */
private const val HDL_R = 9f

/** Jarak tambahan (dp) gagang awal / akhir ke arah luar, supaya keduanya tidak dempet saat yang dipilih hanya satu kata. */
private const val HDL_GAP = 2f

/** Jari-jari area sentuh gagang (dp), dihitung dari pusat tetesan. */
private const val HDL_HIT = 36f

/** Titik kursor terdekat dari posisi [pos] (px, ruang halaman [index] selebar [W]); bila melewati batas halaman (mode gulir) turun / naik ke halaman tetangga. */
private fun posAtPage(ui: PdfUi, index: Int, W: Float, pos: Offset, dens: Float, miss: (Int) -> Unit = {}): PPos? {
    var p = index; var y = pos.y
    if (!ui.paged) {
        val gap = 6f * dens
        while (p < ui.doc.count - 1 && y > ui.doc.ratioOf(p) * W) { y -= ui.doc.ratioOf(p) * W + gap; p++ }
        while (y < 0f && p > 0) { p--; y += ui.doc.ratioOf(p) * W + gap }
    }
    val h = (ui.doc.ratioOf(p) * W).coerceAtLeast(1f)
    val t = ui.txt?.peek(p) ?: run { miss(p); return null }
    val c = caretAt(t, (pos.x / W).coerceIn(0f, 1f), (y / h).coerceIn(0f, 1f), h / W)
    return if (c < 0) null else PPos(p, c)
}

private class HdlHit(val page: Int, val start: Boolean, val coords: LayoutCoordinates, val tip: Offset)

/** Gagang seleksi yang paling dekat dengan [p] (koordinat [host]) dan masih dalam jangkauan sentuh, atau null. */
private fun hitHandle(ui: PdfUi, host: LayoutCoordinates, p: Offset, dens: Float): HdlHit? {
    val s = ui.sel ?: return null
    val rr = HDL_R * dens
    val gx = HDL_GAP * dens
    var best: HdlHit? = null
    var bd = HDL_HIT * dens
    for ((idx, pc) in ui.pageCoords) {
        if (!pc.isAttached) continue
        val t = ui.txt?.peek(idx) ?: continue
        val g = rangeOn(s, idx, t) ?: continue
        val w = pc.size.width.toFloat()
        val h = pc.size.height.toFloat()
        for (st in booleanArrayOf(true, false)) {
            if (if (st) s.a.p != idx else s.b.p != idx) continue
            val o = handleXY(t, g.first, g.second, st, w, h) ?: continue
            // pusat tetesan di ruang halaman (sama persis dengan yang digambar), lalu ke ruang induk lewat transformasi layar yang sebenarnya
            val c = Offset(o.x + (if (st) -(rr + gx) else rr + gx), o.y + rr)
            val d = (host.localPositionOf(pc, c) - p).getDistance()
            if (d <= bd) { bd = d; best = HdlHit(idx, st, pc, o) }
        }
    }
    return best
}

/**
 * Pemegang gagang seleksi, dipasang di induk PALING LUAR (pass Initial): induk selalu melihat sentuhan lebih dulu daripada
 * pager / daftar gulir / PageCurl / ketukan halaman, jadi begitu jari mendarat di gagang, sentuhan itu langsung dimakan di sini
 * dan tidak ada gestur lain yang sempat memakainya (tidak menghapus seleksi, tidak menggulir, tidak membalik halaman).
 * Seleksi diseret di sini juga; mengikuti GERAKAN jari (bukan lokasi jari) sehingga jari tidak menutupi huruf dan seleksi tidak meloncat.
 */
private fun Modifier.selHandleTouch(
    ui: PdfUi, host: () -> LayoutCoordinates?, scope: CoroutineScope, hap: androidx.compose.ui.hapticfeedback.HapticFeedback
): Modifier = pointerInput(ui) {
    val dens = density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (ui.sel == null || ui.inkOn || down.type == PointerType.Stylus) return@awaitEachGesture
        val hc = host()?.takeIf { it.isAttached } ?: return@awaitEachGesture
        val hit = hitHandle(ui, hc, down.position, dens) ?: return@awaitEachGesture
        val pc = hit.coords
        val s0 = ui.sel ?: return@awaitEachGesture
        val an = if (hit.start) s0.b else s0.a
        val grab = down.position - hc.localPositionOf(pc, Offset(hit.tip.x, hit.tip.y - 5f * dens))
        down.consume()
        ui.drawing = true
        ui.selGrab = true
        hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        var finger = down.position
        // Hitung ulang seleksi dari posisi jari sekarang. Halaman acuan dipilih dari halaman yang sedang tampil (halaman asal bisa saja
        // sudah tergulir keluar dan dibuang), lalu posAtPage berjalan turun / naik ke halaman yang benar.
        fun refresh() {
            val ref = ui.pageCoords.entries.filter { it.value.isAttached }.minByOrNull { abs(it.key - hit.page) } ?: return
            val rc = ref.value
            val local = rc.localPositionOf(hc, finger - grab)
            val f = posAtPage(ui, ref.key, rc.size.width.toFloat().coerceAtLeast(1f), local, dens) { p -> scope.launch { ui.txt?.page(p) } } ?: return
            val ns = if (f < an) PSel(f, an) else if (f > an) PSel(an, f) else null
            val cs = ui.sel
            if (ns != null && (cs == null || cs.a != ns.a || cs.b != ns.b)) {
                hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                ui.sel = ns
            }
        }
        // Gulir otomatis (mode gulir): jari di dekat tepi atas / bawah layar menggulir daftar terus-menerus, makin dekat tepi makin cepat,
        // walau jari diam (tidak ada event sentuhan), jadi dijalankan sebagai coroutine terpisah per frame.
        val auto = if (ui.scrollBy != null) scope.launch {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(.05f)
                last = now
                val hh = hc.size.height.toFloat()
                val topZ = 130f * dens
                val botZ = 150f * dens
                val y = finger.y
                val k = when {
                    y > hh - botZ -> ((y - (hh - botZ)) / botZ).coerceIn(0f, 1.2f)
                    y < topZ -> -((topZ - y) / topZ).coerceIn(0f, 1.2f)
                    else -> 0f
                }
                if (k != 0f) {
                    val moved = ui.scrollBy?.invoke(k * 900f * dens * dt) ?: 0f
                    if (moved != 0f) refresh()
                }
            }
        } else null
        try {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (ev.changes.count { it.pressed } >= 2) break
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                c.consume()
                if (c.changedToUp() || !hc.isAttached) break
                finger = c.position
                refresh()
            }
        } finally { auto?.cancel(); ui.drawing = false; ui.selGrab = false }
    }
}

// ---------------------------------------------------------------- coretan di PDF

/** t: 0 pulpen, 1 stabilo. c: indeks warna 0..4. w: tebal sebagai pecahan lebar halaman. p: (x, y, k) tiga-tiga, x dan y dibagi lebar halaman. */
@Serializable
class PInk(val pg: Int, val t: Int, val c: Int, val w: Float, val p: List<Float>)

@Serializable
class PInkDoc(val s: List<PInk> = emptyList())

@Stable
class PInkState(private val file: File, private val scope: CoroutineScope) {
    var list by mutableStateOf<List<PInk>>(emptyList()); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    private val undoS = ArrayList<List<PInk>>()
    private val redoS = ArrayList<List<PInk>>()
    private var job: Job? = null
    val byPage by derivedStateOf { list.groupBy { it.pg } }

    suspend fun load() {
        val l = withContext(Dispatchers.IO) {
            try { if (file.exists()) J.decodeFromString(PInkDoc.serializer(), file.readText()).s else emptyList() } catch (e: Throwable) { emptyList() }
        }
        list = l
    }

    private fun flags() { canUndo = undoS.isNotEmpty(); canRedo = redoS.isNotEmpty() }

    fun commit(next: List<PInk>) {
        undoS.add(list); if (undoS.size > 100) undoS.removeAt(0)
        redoS.clear(); list = next; flags(); save()
    }

    fun add(s: PInk) = commit(list + s)
    fun remove(rm: Set<PInk>) { if (rm.isNotEmpty()) commit(list.filter { it !in rm }) }

    fun undo() { if (undoS.isEmpty()) return; redoS.add(list); list = undoS.removeAt(undoS.size - 1); flags(); save() }
    fun redo() { if (redoS.isEmpty()) return; undoS.add(list); list = redoS.removeAt(redoS.size - 1); flags(); save() }

    private fun write(l: List<PInk>) {
        try {
            if (l.isEmpty()) file.delete() else file.writeText(J.encodeToString(PInkDoc.serializer(), PInkDoc(l)))
        } catch (e: Throwable) { }
    }

    private fun save() {
        job?.cancel()
        val snap = list
        job = scope.launch(Dispatchers.IO) { delay(500); write(snap) }
    }

    fun flush() { job?.cancel(); write(list) }
}

private fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val l2 = dx * dx + dy * dy
    val t = if (l2 < 1e-12f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
    val px = ax + t * dx - x; val py = ay + t * dy - y
    return kotlin.math.sqrt(px * px + py * py)
}

private fun hitStroke(s: PInk, x: Float, y: Float, r: Float): Boolean {
    val n = s.p.size / 3
    val rr = r + s.w / 2f
    if (n == 1) return kotlin.math.hypot(s.p[0] - x, s.p[1] - y) <= rr
    for (i in 0 until n - 1) {
        if (segDist(x, y, s.p[i * 3], s.p[i * 3 + 1], s.p[(i + 1) * 3], s.p[(i + 1) * 3 + 1]) <= rr) return true
    }
    return false
}

/** Coretan yang sedang digambar (piksel, tiga-tiga). */
private class PLive(val tool: Int, val color: Int, val wPx: Float) {
    var pts = FloatArray(192)
    var n = 0
    fun add(x: Float, y: Float, k: Float) {
        if (n * 3 + 3 > pts.size) pts = pts.copyOf(pts.size * 2)
        pts[n * 3] = x; pts[n * 3 + 1] = y; pts[n * 3 + 2] = k; n++
    }
}

// ---------------------------------------------------------------- ikon tambahan

private val PIC = mapOf(
    "scan" to "M4 8V4h4V2H2v6h2zm16 0h2V2h-6v2h4v4zM4 16H2v6h6v-2H4v-4zm16 4h-4v2h6v-6h-2v4zM7 9h10v2H7V9zm0 4h10v2H7v-2z",
    "page" to "M6 2h12c1.1 0 2 .9 2 2v16c0 1.1-.9 2-2 2H6c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2zm1 3v14h10V5H7z",
    "expand" to "M21 11V3h-8l3.29 3.29-10 10L3 13v8h8l-3.29-3.29 10-10z"
)
private val PIC_CACHE = HashMap<String, androidx.compose.ui.graphics.Path>()

@Composable
fun PIc(n: String, tint: Color, sz: Dp = 24.dp, modifier: Modifier = Modifier) {
    val path = remember(n) { PIC_CACHE.getOrPut(n) { PathParser().parsePathString(PIC[n] ?: "").toPath() } }
    Canvas(modifier.size(sz)) {
        val sc = this.size.width / 24f
        scale(sc, sc, pivot = Offset.Zero) { drawPath(path, tint) }
    }
}

// ---------------------------------------------------------------- keadaan layar baca

@Stable
class PdfUi(val doc: PdfDoc, val ink: PInkState, val fid: String) {
    var dark by mutableStateOf(false)
    var inkOn by mutableStateOf(false)
    var tool by mutableIntStateOf(0)
    var color by mutableIntStateOf(0)
    var wDp by mutableFloatStateOf(3f)
    var ts by mutableStateOf("auto")
    var inkPal by mutableStateOf<Pal?>(null)
    var hits by mutableStateOf<List<PHit>>(emptyList())
    var cur by mutableIntStateOf(-1)
    var drawing by mutableStateOf(false)
    val pageCoords = HashMap<Int, LayoutCoordinates>()   // koordinat tiap halaman yang sedang tampil (dipakai pemegang gagang di induk)
    var scrollBy: ((Float) -> Float)? = null   // hanya terisi di mode gulir: menggulir daftar sebesar px, mengembalikan yang benar-benar terpakai
    var selGrab by mutableStateOf(false)   // jari sedang memegang gagang seleksi / menyeret seleksi: gestur halaman (pager / curl / ketukan) harus minggir
    var pulse: androidx.compose.runtime.State<Float> = mutableFloatStateOf(.6f)
    var txt: PdfText? = null
    var sel by mutableStateOf<PSel?>(null)
    var paged = false
    var multi by mutableStateOf(false)   // dua jari (atau lebih) sedang menyentuh layar: gulir / zoom, bukan coretan
}

private val INVERT = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -.88f, 0f, 0f, 0f, 224f,
            0f, -.88f, 0f, 0f, 224f,
            0f, 0f, -.88f, 0f, 224f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/**
 * Cubit / geser dua jari, dibaca di pass Initial pada induk supaya melihat SEMUA jari (halaman anak hanya melihat jari yang jatuh di dalamnya:
 * dua jari di dua halaman berbeda tidak saling tahu). [onMulti] memberi tahu halaman bahwa ini gestur dua jari, jadi coretan dibatalkan.
 * Jari pena (stylus) diabaikan: telapak yang menempel saat menulis dengan pena tidak boleh membatalkan goresan.
 */
private fun Modifier.pinch(onPinch: (Float, Offset, Offset) -> Unit, onEnd: () -> Unit, onMulti: (Boolean) -> Unit = {}): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var multi = false
        try {
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val pen = ev.changes.any { it.pressed && it.type == PointerType.Stylus }
                if (!pen && ev.changes.count { it.pressed } >= 2) {
                    if (!multi) onMulti(true)
                    multi = true
                    onPinch(ev.calculateZoom(), ev.calculatePan(), ev.calculateCentroid(useCurrent = true))
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (ev.changes.any { it.pressed })
        } finally {
            if (multi) onMulti(false)
        }
        if (multi) onEnd()
    }
}

/**
 * Sentuhan untuk mode 3D, dipasang di induk (pass Initial) supaya mendahului library:
 * ketukan tunggal / ganda dipakai di sini (library tidak ikut membalik halaman),
 * saat diperbesar satu jari menggeser gambar dan library tidak pernah melihat geseran itu (jadi tidak bisa pindah halaman).
 * Geseran saat tidak diperbesar dibiarkan lewat ke library untuk melengkungkan halaman.
 * Ketukan di zona tepi kiri / kanan (saat tidak diperbesar) langsung pindah halaman lewat onEdge(-1 / +1), tanpa menunggu ketukan ganda.
 * Saat locked() (mode cari) geseran dikonsumsi di sini sehingga tidak ada yang bisa pindah halaman, dan ketukan tepi diabaikan.
 */
private const val EDGE_TAP = .22f

private fun Modifier.curlTouch(
    scope: CoroutineScope, enabled: Boolean = true, zoomed: () -> Boolean, locked: () -> Boolean = { false },
    onPan: (Offset) -> Unit, onEdge: (Int) -> Unit,
    onTap: () -> Unit, onDouble: (Offset) -> Unit,
    // true = ketukan di zona tepi TIDAK dikonsumsi di sini: library PageCurl yang membalik halaman (tap bawaan library)
    edgeToLib: Boolean = false,
    // true = anak sedang memakai sentuhan ini (menyeret pegangan seleksi teks): jangan menggeser gambar yang diperbesar
    busy: () -> Boolean = { false },
    // true = jari memegang gagang seleksi: semua perubahan sentuhan dimakan di pass Initial (pager / library tidak boleh ikut menggeser halaman),
    // dan lepasnya jari tidak dihitung sebagai ketukan (tidak menghapus seleksi, tidak membalik halaman di zona tepi)
    selBusy: () -> Boolean = { false }
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    var lastT = 0L
    var lastP = Offset.Zero
    var pending: Job? = null
    val tol = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val z0 = zoomed()
        var moved = false
        var multi = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.count { it.pressed } >= 2) multi = true
            if (multi) { if (ev.changes.none { it.pressed }) break else continue }   // cubitan ditangani pinch()
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (selBusy() || c.isConsumed) {   // dimakan pemegang gagang di induk (atau sedang memegang gagang): bukan ketukan / geseran halaman
                c.consume()
                if (c.changedToUp()) break else continue
            }
            if (c.changedToUp()) {
                val quick = c.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                val ex = c.position.x
                val ew = size.width.toFloat()
                if (edgeToLib && !moved && quick && !zoomed() && (ex < ew * EDGE_TAP || ex > ew * (1f - EDGE_TAP))) {
                    // dibiarkan lewat ke library; saat mode cari (locked) ketukan tepi dimakan supaya tidak membalik
                    if (locked()) c.consume()
                    break
                }
                if (!moved && quick) {
                    c.consume()
                    val x = c.position.x
                    val w = size.width.toFloat()
                    if (!zoomed() && (x < w * EDGE_TAP || x > w * (1f - EDGE_TAP))) {
                        // zona tepi: pindah halaman seketika
                        pending?.cancel(); pending = null; lastT = 0L
                        if (!locked()) onEdge(if (x < w / 2f) -1 else 1)
                    } else {
                        val now = c.uptimeMillis
                        if (pending?.isActive == true && now - lastT < 300L && (c.position - lastP).getDistance() < tol) {
                            pending?.cancel(); pending = null; lastT = 0L
                            onDouble(c.position)
                        } else {
                            lastT = now; lastP = c.position
                            pending = scope.launch { delay(300); onTap() }
                        }
                    }
                }
                break
            }
            if (!moved && (c.position - down.position).getDistance() > slop) moved = true
            if (z0 && moved && !busy()) { onPan(c.positionChange()); c.consume() }
            else if (!z0 && locked()) c.consume()   // mode cari: geseran tidak sampai ke pager / PageCurl
        }
    }
}

// ---------------------------------------------------------------- satu halaman

@Composable
// active = false: halaman ini tidak memasang gestur apa pun (seleksi / coret). Dipakai mode 3D: library PageCurl menumpuk halaman
// sebelum / sesudahnya di atas halaman aktif, sehingga sentuhan jatuh ke halaman yang salah (seleksi muncul di halaman tak terlihat).
private fun PdfPage(ui: PdfUi, index: Int, widthDp: Dp, mul: Float, modifier: Modifier = Modifier, active: Boolean = true) {
    val dens = LocalDensity.current.density
    val wPx = (widthDp.value * dens * mul).roundToInt()
    val bk = bucket(wPx)
    val key = ui.fid + ":" + index + "@" + bk
    var bmp by remember(index) { mutableStateOf<ImageBitmap?>(PdfCache.get(key)?.asImageBitmap()) }
    LaunchedEffect(index, bk, ui.doc.ok) {
        if (!ui.doc.ok) return@LaunchedEffect
        val hit = PdfCache.get(key)
        if (hit != null) { bmp = hit.asImageBitmap(); return@LaunchedEffect }
        delay(40)   // lewat cepat saat menggulir kencang: yang sudah keluar layar dibatalkan sebelum dirender
        val b = ui.doc.render(index, bk) ?: return@LaunchedEffect
        PdfCache.put(key, b)
        bmp = b.asImageBitmap()
    }
    val strokes = ui.ink.byPage[index] ?: emptyList()
    val pal = ui.inkPal
    val scp = rememberCoroutineScope()
    val hap = LocalHapticFeedback.current
    var pt by remember(index) { mutableStateOf<PgText?>(ui.txt?.peek(index)) }
    LaunchedEffect(index, PdfBus.hasText) { if (PdfBus.hasText && pt == null) { delay(350); pt = ui.txt?.page(index) } }
    val sel = ui.sel
    val ptv = pt
    val rg = remember(sel, ptv) { if (sel != null && ptv != null) rangeOn(sel, index, ptv) else null }
    val selR = remember(rg, ptv) { if (rg != null && ptv != null) rectsOf(ptv, rg.first, rg.second) else emptyList() }
    val live = remember(index) { mutableStateOf<PLive?>(null) }
    var tick by remember(index) { mutableIntStateOf(0) }
    // gagang seleksi: memantul keluar dari tepi teks saat seleksi muncul, membesar + berhalo saat diseret
    val hdlPop = remember { Animatable(0f) }
    LaunchedEffect(sel != null) { if (sel != null) { hdlPop.snapTo(0f); hdlPop.animateTo(1f, spring(.46f, 420f)) } else hdlPop.snapTo(0f) }
    val hdlGrow by animateFloatAsState(if (ui.drawing && sel != null) 1.3f else 1f, spring(.42f, 520f), label = "hdlg")
    DisposableEffect(index, active) { onDispose { ui.pageCoords.remove(index) } }
    val marks = remember(ui.hits, index) { ui.hits.withIndex().filter { it.value.page == index } }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val paths = remember(strokes, sizePx) {
        val w = sizePx.width.toFloat()
        strokes.map { s ->
            val n = s.p.size / 3
            val a = FloatArray(n * 3)
            for (i in 0 until n) { a[i * 3] = s.p[i * 3] * w; a[i * 3 + 1] = s.p[i * 3 + 1] * w; a[i * 3 + 2] = s.p[i * 3 + 2] }
            Triple(s, inkPath(a, n, s.t, s.w * w), s.w * w)
        }
    }
    Box(
        modifier
            .onSizeChanged { sizePx = it }
            .onGloballyPositioned { if (active) ui.pageCoords[index] = it }
            .background(if (ui.dark) Color.Black else Color.White)
            .then(if (!active) Modifier else Modifier.pointerInput(ui.inkOn, ui.tool, ui.ts, index) {
                if (!ui.inkOn) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val stylus = down.type == PointerType.Stylus
                    if (stylus) InkEnv.stylus = true
                    val allow = when (ui.ts) {
                        "stylus" -> stylus
                        "finger" -> true
                        else -> if (InkEnv.stylus) stylus else true
                    }
                    if (!allow) return@awaitEachGesture
                    // dua jari sedang menyentuh layar (mis. menggulir dengan dua jari): bukan coretan
                    if (!stylus && ui.multi) return@awaitEachGesture
                    val W = size.width.toFloat().coerceAtLeast(1f)
                    val wPxTool = ui.wDp * dens
                    val lv = PLive(ui.tool, ui.color, wPxTool)
                    val erased = HashSet<PInk>()
                    // Jari baru ditampilkan / disimpan setelah menyentuh sebentar tanpa jari kedua: dua jari yang mendarat berurutan
                    // (jari pertama beberapa milidetik lebih dulu) tidak sempat meninggalkan coretan. Pena langsung tampil.
                    val grace = if (stylus) 0L else 90L
                    val t0 = down.uptimeMillis
                    var shown = stylus
                    ui.drawing = true
                    fun kOf(p: Float) = if (stylus && p > .01f) (.35f + .95f * p).coerceIn(.3f, 1.3f) else 1f
                    fun point(pos: Offset, pr: Float) {
                        if (ui.tool == 2) {
                            val r = wPxTool / 2f / W
                            (ui.ink.byPage[index] ?: emptyList()).forEach { s -> if (s !in erased && hitStroke(s, pos.x / W, pos.y / W, r)) erased.add(s) }
                        } else lv.add(pos.x, pos.y, kOf(pr))
                    }
                    fun publish() { if (shown) { live.value = lv; tick++ } }
                    down.consume()
                    point(down.position, down.pressure)
                    publish()
                    var cancelled = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        // jari kedua (di halaman mana pun, dilihat induk lewat ui.multi), atau gestur lain mengambil sentuhan ini
                        if (!stylus && (ui.multi || ev.changes.count { it.pressed } >= 2 || c.isConsumed)) { cancelled = true; break }
                        if (stylus && c.isConsumed) { cancelled = true; break }
                        if (c.changedToUp()) break
                        if (!shown && c.uptimeMillis - t0 >= grace) shown = true
                        for (h in c.historical) point(h.position, c.pressure)
                        point(c.position, c.pressure)
                        publish()
                        c.consume()
                    }
                    ui.drawing = false
                    live.value = null; tick++
                    if (cancelled) return@awaitEachGesture
                    if (ui.tool == 2) ui.ink.remove(erased)
                    else if (lv.n > 0) {
                        val pts = ArrayList<Float>(lv.n * 3)
                        for (i in 0 until lv.n) { pts.add(lv.pts[i * 3] / W); pts.add(lv.pts[i * 3 + 1] / W); pts.add(lv.pts[i * 3 + 2]) }
                        ui.ink.add(PInk(index, ui.tool, ui.color, lv.wPx / W, pts))
                    }
                }
            })
            .then(if (!active) Modifier else Modifier.pointerInput(ui.inkOn, index) {
                if (ui.inkOn) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!PdfBus.hasText) return@awaitEachGesture
                    if (down.isConsumed) return@awaitEachGesture   // sudah dimakan pemegang gagang di induk (atau penghenti fling)
                    val W = size.width.toFloat().coerceAtLeast(1f)
                    val H = size.height.toFloat().coerceAtLeast(1f)
                    val here = pt
                    var anchor: PPos? = null
                    var grab = Offset.Zero   // seleksi mengikuti GERAKAN jari, bukan lokasi jari (jari tidak menutupi / meloncat)
                    // (menyentuh gagang yang sudah ada ditangani selHandleTouch di induk paling luar)
                    // tekan lama di atas teks -> pilih satu kata; geser terus untuk memperluas
                    if (anchor == null) {
                        awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        if (here == null) { scp.launch { pt = ui.txt?.page(index) }; return@awaitEachGesture }
                        val fx = (down.position.x / W).coerceIn(0f, 1f)
                        val fy = (down.position.y / H).coerceIn(0f, 1f)
                        val k = nearestChar(here, fx, fy, H / W)
                        val r = if (k >= 0) here.r[k] else null
                        if (r == null) return@awaitEachGesture
                        val ddx = max(0f, max(r[0] - fx, fx - r[2]))
                        val ddy = max(0f, max(r[1] - fy, fy - r[3])) * (H / W)
                        if (ddx > .06f || ddy > .04f) return@awaitEachGesture
                        var a = k; var b = k + 1
                        if (here.t[k].isLetterOrDigit()) {
                            while (a > 0 && here.t[a - 1].isLetterOrDigit()) a--
                            while (b < here.t.length && here.t[b].isLetterOrDigit()) b++
                        }
                        hap.performHapticFeedback(HapticFeedbackType.LongPress)
                        ui.sel = PSel(PPos(index, a), PPos(index, b))
                        anchor = PPos(index, a)
                    }
                    val an = anchor ?: return@awaitEachGesture
                    fun posAt(pos: Offset): PPos? = posAtPage(ui, index, W, pos, dens) { p -> scp.launch { ui.txt?.page(p) } }
                    ui.drawing = true
                    ui.selGrab = true
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.size >= 2) break
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            c.consume()
                            if (c.changedToUp()) break
                            val f = posAt(c.position - grab) ?: continue
                            val ns = if (f < an) PSel(f, an) else if (f > an) PSel(an, f) else null
                            val cs = ui.sel
                            if (ns != null && (cs == null || cs.a != ns.a || cs.b != ns.b)) {
                                hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                ui.sel = ns
                            }
                        }
                    } finally { ui.drawing = false; ui.selGrab = false }
                }
            })
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val b = bmp
            if (b != null) {
                drawImage(
                    b, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium, colorFilter = if (ui.dark) INVERT else null
                )
            }
            for (r in selR) {
                val tl = Offset(r[0] * size.width, r[1] * size.height)
                drawRect(SEL_COLOR.copy(alpha = .38f), tl, Size((r[2] - r[0]) * size.width, max((r[3] - r[1]) * size.height, 10f * density)))
            }
            for ((vi, h) in marks) {
                val cur = vi == ui.cur
                val pad = 2f * density
                for (r in h.rects) {
                    val tl = Offset(r[0] * size.width - pad, r[1] * size.height - pad)
                    val sz = Size((r[2] - r[0]) * size.width + pad * 2, max((r[3] - r[1]) * size.height, 10f * density) + pad * 2)
                    if (cur) {
                        drawRect(Color(0xFFFF8A00).copy(alpha = ui.pulse.value), tl, sz)
                        drawRect(Color(0xFFE65100), tl, sz, style = Stroke(2.5f * density))
                    } else drawRect(Color(0xFFFFD54F).copy(alpha = .6f), tl, sz)
                }
            }
            if (pal != null) {
                for ((s, path, wpx) in paths) {
                    val col = inkColor(pal, s.c, s.t == 1)
                    if (s.t == 1) drawPath(
                        path, col, alpha = .85f, style = Stroke(wpx, cap = StrokeCap.Butt, join = StrokeJoin.Round),
                        blendMode = if (ui.dark) BlendMode.Screen else BlendMode.Multiply
                    ) else drawPath(path, col)
                }
                tick
                val lv = live.value
                if (lv != null && lv.tool != 2 && lv.n > 0) {
                    val col = inkColor(pal, lv.color, lv.tool == 1)
                    val path = inkPath(lv.pts, lv.n, lv.tool, lv.wPx)
                    if (lv.tool == 1) drawPath(
                        path, col, alpha = .85f, style = Stroke(lv.wPx, cap = StrokeCap.Butt, join = StrokeJoin.Round),
                        blendMode = if (ui.dark) BlendMode.Screen else BlendMode.Multiply
                    ) else drawPath(path, col)
                }
            }
        }
        if (sel != null && rg != null && ptv != null) Canvas(Modifier.fillMaxSize()) {
            val k = (hdlPop.value * hdlGrow).coerceAtLeast(0f)
            val halo = ((hdlGrow - 1f) / .3f).coerceIn(0f, 1f)
            val rr = HDL_R * density
            // tetesan bergaya M3 Expressive: bulat penuh dengan satu sudut lancip yang menyentuh tepi teks
            fun hdl(o: Offset, start: Boolean) {
                if (k <= .001f) return
                val gx = HDL_GAP * density
                val l = if (start) o.x - 2f * rr - gx else o.x + gx
                val full = CornerRadius(rr)
                val tip = CornerRadius(2.5f * density)
                val shape = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        RoundRect(
                            l, o.y, l + 2f * rr, o.y + 2f * rr,
                            topLeftCornerRadius = if (start) full else tip, topRightCornerRadius = if (start) tip else full,
                            bottomRightCornerRadius = full, bottomLeftCornerRadius = full
                        )
                    )
                }
                scale(k, k, pivot = o) {
                    if (halo > 0f) drawCircle(SEL_COLOR.copy(alpha = .2f * halo), 2.1f * rr, Offset(l + rr, o.y + rr))
                    translate(0f, 1.6f * density) { drawPath(shape, Color.Black.copy(alpha = .18f)) }
                    drawPath(shape, SEL_COLOR)
                }
            }
            if (sel.a.p == index) handleXY(ptv, rg.first, rg.second, true, size.width, size.height)?.let { hdl(it, true) }
            if (sel.b.p == index) handleXY(ptv, rg.first, rg.second, false, size.width, size.height)?.let { hdl(it, false) }
        }
        if (bmp == null) Box(Modifier.align(Alignment.Center)) { LoadingBlob(Modifier.size(36.dp)) }
    }
}


// ---------------------------------------------------------------- bilah seleksi (grup tombol tersambung M3 Expressive)

@Composable
private fun PdfSelBar(onCopy: () -> Unit, onAll: () -> Unit, onCancel: () -> Unit) {
    val pal = LocalPal.current
    val items = listOf<Pair<String, () -> Unit>>("Copy" to onCopy, "Select all" to onAll, "Cancel" to onCancel)
    Row(
        Modifier.shadow(10.dp, CircleShape).clip(CircleShape).background(pal.c3).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (label, f) -> PdfSelBtn(i, items.size, label, i == 0, f) }
    }
}

/** Tombol bilah seleksi: ujung luar bulat penuh, sambungan antar tombol bersudut kecil; ditekan = semua sudut membulat + menyempit. Muncul memantul berurutan. */
@Composable
private fun PdfSelBtn(i: Int, n: Int, label: String, primary: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val hap = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val outer by animateDpAsState(if (pr) 22.dp else 26.dp, spring(.55f, 700f), label = "pso")
    val inner by animateDpAsState(if (pr) 22.dp else 7.dp, spring(.55f, 700f), label = "psi")
    val sc by animateFloatAsState(if (pr) .92f else 1f, spring(.45f, 650f), label = "pss")
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(70L + i * 45L); pop.animateTo(1f, spring(.5f, 480f)) }
    val sC = if (i == 0) outer else inner
    val eC = if (i == n - 1) outer else inner
    Box(
        Modifier.graphicsLayer { val p = pop.value.coerceAtLeast(0f); scaleX = p * sc; scaleY = p * sc }
            .height(46.dp).widthIn(min = 64.dp)
            .clip(RoundedCornerShape(topStart = sC, bottomStart = sC, topEnd = eC, bottomEnd = eC))
            .background(if (primary) pal.p else pal.c4)
            .clickable(interactionSource = src, indication = LocalIndication.current) {
                hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp),
        Alignment.Center
    ) { T(label, 14f, 800, (if (primary) pal.op else pal.os).copy(alpha = if (label == "Cancel") .78f else 1f)) }
}

// ---------------------------------------------------------------- layar baca

@Composable
fun PdfScreen(vm: AppVM, id: String, act: Actions) {
    val note = vm.note(id) ?: return
    val ctx = LocalContext.current
    val appPal = LocalPal.current
    val file = remember(note.pdf) { PdfFiles.file(ctx, note.pdf) }
    val scope = rememberCoroutineScope()
    val doc = remember(file) { PdfDoc(file) }
    val txt = remember(file) { PdfText(ctx, file) }
    val ink = remember(file) { PInkState(PdfFiles.ink(ctx, note.pdf), scope) }
    DisposableEffect(file) { onDispose { ink.flush(); doc.close(); txt.close() } }
    LaunchedEffect(file) {
        if (!file.exists()) { doc.failed = true; return@LaunchedEffect }
        launch { ink.load() }
        if (vm.note(id)?.cv?.isEmpty() == true) {
            val cvId = withContext(Dispatchers.IO) { makePdfCover(vm, file) }
            if (cvId.isNotEmpty()) vm.upd(id) { if (it.cv.isEmpty()) it.copy(cv = cvId, cx = .5f, cy = 0f, cz = 1f) else it }
        }
        doc.open()
        if (doc.ok) {
            launch { doc.loadRatios() }
            PdfBus.hasText = false
            PdfBus.hasText = txt.detect(doc.count)
        }
    }

    val dark = when (note.pdk) { 1 -> true; 2 -> false; else -> appPal.dark }
    val vpal = remember(dark, appPal, vm.pr.seed, vm.pr.dyn, vm.pr.mono) {
        if (dark == appPal.dark) appPal else {
            val base = (if (vm.pr.dyn && Build.VERSION.SDK_INT >= 31) dynamicPal(ctx, if (dark) "dark" else "light") else null)
                ?: buildPal(if (dark) "dark" else "light", vm.pr.seed)
            if (vm.pr.mono) base.toMono() else base
        }
    }
    val cp = remember(appPal, vm.pr.seed, vm.pr.dyn, vm.pr.mono) {
        if (appPal.dark) appPal else {
            val base = (if (vm.pr.dyn && Build.VERSION.SDK_INT >= 31) dynamicPal(ctx, "dark") else null) ?: buildPal("dark", vm.pr.seed)
            if (vm.pr.mono) base.toMono() else base
        }
    }
    val ui = remember(file) { PdfUi(doc, ink, note.pdf).also { it.dark = dark; it.inkPal = vpal; it.ts = vm.pr.ts; it.txt = txt } }
    SideEffect { ui.dark = dark; ui.inkPal = vpal; ui.ts = vm.pr.ts }
    val view = LocalView.current
    LaunchedEffect(Unit) {
        val w = (view.context as Activity).window
        snapshotFlow { vm.openP.value > .2f }.collect { dk ->
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = if (dk) false else !appPal.dark
        }
    }
    DisposableEffect(appPal.dark) {
        onDispose {
            val w = (view.context as Activity).window
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !appPal.dark
        }
    }
    if (PdfBus.mode < 0) PdfBus.mode = ctx.getSharedPreferences("dinduy", 0).getInt("pdfmode", 0)
    if (PdfBus.curl < 0) PdfBus.curl = ctx.getSharedPreferences("dinduy", 0).getInt("pdfcurl", 0)
    if (PdfBus.curlStyle < 0) PdfBus.curlStyle = ctx.getSharedPreferences("dinduy", 0).getInt("pdfcurlstyle", 0)

    androidx.compose.runtime.CompositionLocalProvider(
        LocalPal provides cp,
        androidx.compose.material3.LocalContentColor provides cp.os
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).graphicsLayer {
                // buka/tutup dari beranda: isi PDF berkedip sangat cepat (pudar-silang dengan sampul kartu), bukan ikut membesar dari kartu.
                // Rentang pudar dipersempit (dulu .20-.45, sekarang .14-.26 dari progres morph) supaya kedipnya singkat.
                val x = ((vm.openP.value - .14f) / .12f).coerceIn(0f, 1f)
                alpha = x * x * (3f - 2f * x)
            }
        ) {
            when {
                doc.failed -> {
                    var shown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { shown = true }
                    val pop by animateFloatAsState(if (shown) 1f else 0f, spring(.45f, 260f), label = "errpop")
                    Column(
                        Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.graphicsLayer { scaleX = pop; scaleY = pop; rotationZ = (1f - pop) * -14f }
                                .size(112.dp).clip(RoundedCornerShape(40.dp)).background(cp.ec),
                            Alignment.Center
                        ) { PIc("page", cp.oec, 52.dp) }
                        Spacer(Modifier.height(28.dp))
                        T("This PDF can't be opened", 24f, 800, cp.os)
                        Spacer(Modifier.height(8.dp))
                        T("The file is missing, damaged, or protected with a password.", 15f, 400, cp.osv)
                        Spacer(Modifier.height(28.dp))
                        val esrc = remember { MutableInteractionSource() }
                        val epr by esrc.collectIsPressedAsState()
                        val er by animateDpAsState(if (epr) 14.dp else 28.dp, spring(.55f, 700f), label = "errbtn")
                        Row(
                            Modifier.clip(RoundedCornerShape(er)).background(cp.p)
                                .clickable(interactionSource = esrc, indication = LocalIndication.current) { vm.nav.pop() }
                                .padding(horizontal = 26.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Ic("back", cp.op, 20.dp)
                            T("Go back", 15f, 800, cp.op)
                        }
                    }
                }
                !doc.ok -> Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingBlob(Modifier.size(56.dp)) }
                else -> PdfReader(vm, id, act, ui, txt, note)
            }
        }
    }
}

// ---------------------------------------------------------------- Silk curl: gaya 3D kedua (OpenGL ES 2.0, kode GL di app.dinduy.silkcurl)

private const val SILK_GAIN_F = 1.1f    // maju: jarak geser jari (pecahan lebar halaman) -> gerak siluet
private const val SILK_GAIN_B = 1.25f   // mundur

private class SilkGeo(
    val pw: Float, val ph: Float, val vw: Float, val vh: Float,
    val left: Float, val top: Float, val avail: Float, val topPad: Float
)

/**
 * Geser horizontal untuk Silk curl, dipasang SETELAH pinch() dan curlTouch() (pass Main): geseran saat diperbesar, saat mode cari,
 * atau saat dua jari sudah dimakan di pass Initial, jadi tidak pernah sampai sini. [onMove] menerima jarak searah gestur (px, selalu >= 0 bila searah).
 */
private fun Modifier.silkDrag(
    canStart: (Int) -> Boolean,
    onStart: (Int, Float) -> Unit,
    onMove: (Float, Float) -> Unit,
    onEnd: (Float, Boolean) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
        val dir = if (slop.position.x < down.position.x) 1 else -1   // 1 = maju (geser ke kiri)
        if (!canStart(dir)) return@awaitEachGesture
        val vt = VelocityTracker()
        vt.addPosition(down.uptimeMillis, down.position)
        onStart(dir, down.position.y)
        val ok = horizontalDrag(slop.id) { c ->
            vt.addPosition(c.uptimeMillis, c.position)
            val dx = c.position.x - down.position.x
            onMove(if (dir > 0) -dx else dx, c.position.y)
            c.consume()
        }
        val vx = vt.calculateVelocity().x
        onEnd(if (dir > 0) -vx else vx, !ok)
    }
}

private suspend fun silkLoad(ui: PdfUi, i: Int, bk: Int): Bitmap? {
    if (!ui.doc.ok) return null
    val key = ui.fid + ":" + i + "@" + bk
    PdfCache.get(key)?.let { return it }
    val b = ui.doc.render(i, bk) ?: return null
    PdfCache.put(key, b)
    return b
}

/**
 * Halaman yang punya coretan dibakar ke salinan bitmap (bitmap PdfCache bersama dan tidak pernah diubah / di-recycle). Di mode gelap
 * halaman dibalik dulu persis seperti PdfPage, baru dicoret, lalu ditandai preInverted supaya shader tidak membalik lagi.
 */
private fun silkBake(src: Bitmap, strokes: List<PInk>, dark: Boolean, pal: Pal): SilkPage {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val cv = androidx.compose.ui.graphics.Canvas(out.asImageBitmap())
    val w = src.width.toFloat()
    val h = src.height.toFloat()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, cv, Size(w, h)) {
        drawImage(src.asImageBitmap(), colorFilter = if (dark) INVERT else null)
        for (s in strokes) {
            val n = s.p.size / 3
            val a = FloatArray(n * 3)
            for (i in 0 until n) { a[i * 3] = s.p[i * 3] * w; a[i * 3 + 1] = s.p[i * 3 + 1] * w; a[i * 3 + 2] = s.p[i * 3 + 2] }
            val path = inkPath(a, n, s.t, s.w * w)
            val col = inkColor(pal, s.c, s.t == 1)
            if (s.t == 1) drawPath(
                path, col, alpha = .85f, style = Stroke(s.w * w, cap = StrokeCap.Butt, join = StrokeJoin.Round),
                blendMode = if (dark) BlendMode.Screen else BlendMode.Multiply
            ) else drawPath(path, col)
        }
    }
    return SilkPage(out, preInverted = dark)
}

/**
 * Mode halaman 3D gaya Silk. Keadaan: [cur] = halaman yang sedang dibaca; dir 0 = diam, 1 = lembar halaman [cur] sedang dibalik maju,
 * -1 = lembar halaman [cur]-1 sedang dibawa kembali. xr = posisi siluet lembar (1 = datar, XR_GONE = sudah lewat).
 * Hanya tiga halaman (sebelumnya, sekarang, berikutnya) yang dipegang. Teks dan coretan baru tidak aktif di sini (seperti Classic, coret aktif -> pager biasa).
 */
@Composable
private fun PdfSilkCurl(
    vm: AppVM, ui: PdfUi, count: Int, vwDp: Dp, vhDp: Dp, topPad: Dp, sab: Dp, dens: Float, startPage: Int,
    page: Int, onPage: (Int) -> Unit, goTo: PHit?, clearGoTo: () -> Unit, findOn: Boolean,
    onTap: () -> Unit, onFail: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val countNow by rememberUpdatedState(count)
    val findNow by rememberUpdatedState(findOn)
    val onPageNow by rememberUpdatedState(onPage)
    val onTapNow by rememberUpdatedState(onTap)
    val onFailNow by rememberUpdatedState(onFail)

    // ---- ukuran: persis seperti cabang Classic (halaman di tengah area di antara bilah atas dan bawah)
    val r0 = ui.doc.ratioOf(startPage)
    val availDp = vhDp - topPad - sab - 12.dp
    val w0 = minOf(vwDp, availDp / r0)
    val pwPx = w0.value * dens
    val phPx = pwPx * r0
    val vwPx = vwDp.value * dens
    val vhPx = vhDp.value * dens
    val availPx = availDp.value * dens
    val geo = SilkGeo(pwPx, phPx, vwPx, vhPx, (vwPx - pwPx) / 2f, topPad.value * dens + (availPx - phPx) / 2f, availPx, topPad.value * dens)
    val geoNow by rememberUpdatedState(geo)
    val bk = bucket(pwPx.roundToInt())

    var cur by remember { mutableIntStateOf(page.coerceIn(0, max(0, count - 1))) }
    var dir by remember { mutableIntStateOf(0) }
    var xr by remember { mutableFloatStateOf(1f) }
    var tilt by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var cz by remember { mutableFloatStateOf(1f) }
    var cpan by remember { mutableStateOf(Offset.Zero) }
    val cMul by remember { derivedStateOf { ceil(cz).coerceAtLeast(1f) } }

    val ctl = remember { SilkController { onFailNow() } }

    // seleksi teks tidak jalan di atas GLSurfaceView
    LaunchedEffect(Unit) { ui.sel = null }

    // ---- bitmap: peta kecil, hanya jendela cur-1..cur+1; dibuat ulang bila resolusi / mode gelap / coretan berubah
    val pages = remember(bk, ui.dark, ui.ink.list) { mutableStateMapOf<Int, SilkPage>() }
    suspend fun make(i: Int, res: Int): SilkPage? {
        val b = silkLoad(ui, i, res) ?: return null
        val strokes = ui.ink.byPage[i]
        val pal = ui.inkPal
        return if (!strokes.isNullOrEmpty() && pal != null) withContext(Dispatchers.Default) { silkBake(b, strokes, ui.dark, pal) } else SilkPage(b, false)
    }
    LaunchedEffect(pages, cur, ui.doc.ok) {
        for (k in pages.keys.toList()) if (k < cur - 1 || k > cur + 1) pages.remove(k)
        for (i in intArrayOf(cur, cur + 1, cur - 1)) {
            if (i !in 0 until countNow || pages[i] != null) continue
            val sp = make(i, bk) ?: continue
            if (i in (cur - 1)..(cur + 1)) pages[i] = sp
        }
    }
    // resolusi tinggi hanya untuk halaman yang sedang diperbesar (seperti mul di PdfPage)
    var hi by remember { mutableStateOf<Triple<Int, Float, SilkPage>?>(null) }
    LaunchedEffect(pages, cur, cMul, ui.doc.ok) {
        if (cMul <= 1f) { hi = null; return@LaunchedEffect }
        val sp = make(cur, bucket((pwPx * cMul).roundToInt())) ?: return@LaunchedEffect
        hi = Triple(cur, cMul, sp)
    }
    val hiPage = hi?.takeIf { it.first == cur && cz > 1.02f }?.third
    val pPrev = pages[cur - 1]
    val pCur = hiPage ?: pages[cur]
    val pNext = pages[cur + 1]

    // ---- sinkron dengan state luar: loncat dari pencarian / kapsul, halaman terakhir (page -> vm.upd di PdfReader)
    LaunchedEffect(goTo) {
        val h = goTo ?: return@LaunchedEffect
        clearGoTo()
        job?.cancel()
        dir = 0; xr = 1f; tilt = 0f
        cur = h.page.coerceIn(0, max(0, countNow - 1))
    }
    LaunchedEffect(page) {
        if (page != cur && dir == 0 && job?.isActive != true) cur = page.coerceIn(0, max(0, countNow - 1))
    }
    LaunchedEffect(cur) { cz = 1f; cpan = Offset.Zero }

    // ---- gerak
    fun settle(to: Float, after: () -> Unit) {
        job?.cancel()
        val from = xr
        job = scope.launch {
            val dur = (220 + 260 * (abs(to - from) / 2.3f).coerceIn(0f, 1f)).toInt()
            animate(from, to, animationSpec = tween(dur, easing = FastOutSlowInEasing)) { v, _ -> xr = v }
            after()
        }
    }
    fun finishForward() { cur += 1; dir = 0; xr = 1f; tilt = 0f; onPageNow(cur) }
    fun finishBackward() { cur -= 1; dir = 0; xr = 1f; tilt = 0f; onPageNow(cur) }
    fun idle() = job?.isActive != true && cz <= 1.02f && !findNow

    fun cClamp(o: Offset, z: Float): Offset {
        val g = geoNow
        val mx = ((g.pw * z - g.vw) / 2f).coerceAtLeast(0f)
        val my = ((g.ph * z - g.avail) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    fun cZoom(z1: Float, c: Offset, delta: Offset = Offset.Zero) {
        val g = geoNow
        val k = z1 / cz
        val origin = Offset(g.vw / 2f, g.topPad + g.avail / 2f)
        cpan = cClamp((c - origin) * (1f - k) + cpan * k + delta, z1)
        cz = z1
    }

    // ---- kirim ke renderer
    SideEffect {
        ctl.setPages(pPrev, pCur, pNext)
        ctl.setState(
            if (dir < 0) 0 else 1, xr, tilt, ui.dark, cz, cpan.x, cpan.y,
            geo.left, geo.top, geo.pw, geo.ph, geo.vw, geo.vh
        )
    }

    Box(
        Modifier.fillMaxSize()
            .pinch(
                onPinch = { z, p, c -> cZoom((cz * z).coerceIn(1f, 4f), c, p) },
                onEnd = { if (cz < 1.05f) { cz = 1f; cpan = Offset.Zero } }
            )
            .curlTouch(
                scope, true, { cz > 1.02f }, { findNow }, { d -> cpan = cClamp(cpan + d, cz) },
                onEdge = { d ->
                    if (idle()) {
                        if (d > 0 && cur + 1 < countNow) { dir = 1; xr = 1f; tilt = .45f; settle(SilkShape.XR_GONE) { finishForward() } }
                        else if (d < 0 && cur > 0) { dir = -1; xr = 0f; tilt = .45f; settle(1f) { finishBackward() } }
                    }
                },
                onTap = { if (ui.sel != null) ui.sel = null else onTapNow() },
                onDouble = { p ->
                    val z0 = cz
                    val z1 = if (z0 > 1.2f) 1f else 2.5f
                    scope.launch { animate(0f, 1f, animationSpec = spring(1f, 420f)) { v, _ -> cZoom(z0 + (z1 - z0) * v, p) } }
                },
                busy = { ui.drawing },
                selBusy = { ui.selGrab }
            )
            .silkDrag(
                canStart = { d -> idle() && (if (d > 0) cur + 1 < countNow else cur > 0) },
                onStart = { d, y ->
                    val g = geoNow
                    dir = d
                    xr = if (d > 0) 1f else 0f
                    tilt = (((y - g.top) / g.ph - .5f) * 2f).coerceIn(-1f, 1f) * .9f
                },
                onMove = { dist, y ->
                    val g = geoNow
                    xr = if (dir > 0) (1f - SILK_GAIN_F * dist / g.pw).coerceIn(SilkShape.XR_GONE, 1f)
                    else (SILK_GAIN_B * dist / g.pw).coerceIn(0f, 1f)
                    tilt += ((((y - g.top) / g.ph - .5f) * 2f).coerceIn(-1f, 1f) * .9f - tilt) * .25f
                },
                onEnd = { vel, cancelled ->
                    val d = dir
                    if (d != 0) {
                        val commit = !cancelled && when {
                            vel > 900f -> true
                            vel < -900f -> false
                            d > 0 -> xr < .55f
                            else -> xr > .4f
                        }
                        if (d > 0) {
                            if (commit) settle(SilkShape.XR_GONE) { finishForward() } else settle(1f) { dir = 0; tilt = 0f }
                        } else {
                            if (commit) settle(1f) { finishBackward() } else settle(0f) { dir = 0; xr = 1f; tilt = 0f }
                        }
                    }
                }
            )
    ) {
        // GLSurfaceView tak ikut pudar bersama induknya, jadi disembunyikan selama morph buka/tutup dari beranda
        SilkGlView(ctl, vm.openP.value > .2f, Modifier.fillMaxSize())

        // penutup warna kertas sampai frame GL pertama tergambar (hindari kedip hitam saat surface dibuat)
        val cover by animateFloatAsState(if (ctl.ready) 0f else 1f, tween(140), label = "silkcover")
        if (cover > .001f) Canvas(Modifier.fillMaxSize()) {
            drawRect(if (ui.dark) Color.Black else Color.White, Offset(geo.left, geo.top), Size(geo.pw, geo.ph), alpha = cover)
        }

        // sorotan hasil cari (GL tidak menggambar PdfPage): hanya saat halaman diam
        val marks = remember(ui.hits, cur) { ui.hits.withIndex().filter { it.value.page == cur } }
        if (dir == 0 && marks.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            fun mx(f: Float) = geo.left + (f * geo.pw - geo.pw / 2f) * cz + geo.pw / 2f + cpan.x
            fun my(f: Float) = geo.top + (f * geo.ph - geo.ph / 2f) * cz + geo.ph / 2f + cpan.y
            for ((vi, h) in marks) {
                val isCur = vi == ui.cur
                val pad = 2f * density
                for (r in h.rects) {
                    val tl = Offset(mx(r[0]) - pad, my(r[1]) - pad)
                    val sz = Size((r[2] - r[0]) * geo.pw * cz + pad * 2, max((r[3] - r[1]) * geo.ph * cz, 10f * density) + pad * 2)
                    if (isCur) {
                        drawRect(Color(0xFFFF8A00).copy(alpha = ui.pulse.value), tl, sz)
                        drawRect(Color(0xFFE65100), tl, sz, style = Stroke(2.5f * density))
                    } else drawRect(Color(0xFFFFD54F).copy(alpha = .6f), tl, sz)
                }
            }
        }
    }
}

@OptIn(ExperimentalPageCurlApi::class)
@Composable
private fun PdfReader(vm: AppVM, id: String, act: Actions, ui: PdfUi, txt: PdfText, note: Note) {
    val pal = LocalPal.current
    val dens = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val count = ui.doc.count
    val sat = satDp()
    val sab = sabDp()
    var bars by remember { mutableStateOf(true) }
    var findOn by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val mode = PdfBus.mode
    val startPage = remember { note.pg.coerceIn(0, count - 1) }
    var page by remember { mutableIntStateOf(startPage) }
    val hasText = PdfBus.hasText
    // mode 3D (efek balik halaman melengkung); mati saat pensil coret aktif
    val curlOn = mode != 0 && PdfBus.curl == 1 && !ui.inkOn
    // Silk curl (OpenGL, app.dinduy.silkcurl). Gagal / tak didukung => otomatis jatuh ke Classic. curlMode di bawah = cabang Classic, tidak berubah.
    var silkFailed by remember { mutableStateOf(false) }
    val silkMode = curlOn && PdfBus.curlStyle == 1 && !silkFailed && SilkGl.supported(LocalContext.current)
    val curlMode = curlOn && !silkMode

    // pengaturan alat coret terakhir
    LaunchedEffect(Unit) {
        ui.tool = 0
        ui.color = vm.pr.ic.getOrElse(0) { 0 }
        ui.wDp = vm.pr.iw.getOrElse(0) { 3f }
    }
    fun saveTool() {
        val ic = vm.pr.ic.toMutableList(); val iw = vm.pr.iw.toMutableList()
        while (ic.size < 3) ic.add(0); while (iw.size < 3) iw.add(3f)
        ic[ui.tool] = ui.color; iw[ui.tool] = ui.wDp
        vm.setPr(vm.pr.copy(ic = ic, iw = iw))
    }

    // simpan halaman terakhir
    LaunchedEffect(page) { delay(900); if (page != note.pg) vm.upd(id) { it.copy(pg = page) } }

    // menu titik tiga -> "Search in PDF"
    LaunchedEffect(PdfBus.find) { if (PdfBus.find > 0) { PdfBus.find = 0; if (PdfBus.hasText) findOn = true else vm.show("This PDF is a scan, so there's no text to search") } }
    LaunchedEffect(findOn) { if (findOn) { delay(120); try { fr.requestFocus() } catch (e: Exception) { } } else { ui.hits = emptyList(); ui.cur = -1; query = "" } }

    var goTo by remember { mutableStateOf<PHit?>(null) }
    LaunchedEffect(query, findOn) {
        if (!findOn) return@LaunchedEffect
        val q = query.trim()
        ui.hits = emptyList(); ui.cur = -1
        if (q.isEmpty()) { searching = false; return@LaunchedEffect }
        delay(280)
        searching = true
        val acc = ArrayList<PHit>()
        var first = true
        for (p in 0 until count) {
            val pt = txt.page(p) ?: continue
            val h = hitsIn(p, pt, q)
            if (h.isNotEmpty()) {
                acc.addAll(h)
                ui.hits = acc.toList()
                if (first) { first = false; ui.cur = 0; goTo = acc[0]; page = acc[0].page }
            }
        }
        searching = false
        if (acc.isEmpty()) vm.show("No results")
    }
    var listOn by remember { mutableStateOf(false) }
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusMgr = androidx.compose.ui.platform.LocalFocusManager.current
    fun jump(i: Int) {
        if (i !in ui.hits.indices) return
        ui.cur = i
        goTo = ui.hits[i]
        page = ui.hits[i].page
    }
    fun step(d: Int) {
        val n = ui.hits.size
        if (n == 0) return
        jump(((ui.cur + d) % n + n) % n)
    }
    if (findOn) {
        val inf = rememberInfiniteTransition(label = "hit")
        val pl = inf.animateFloat(.3f, .75f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "hitp")
        SideEffect { ui.pulse = pl }
    }

    BackHandler(enabled = findOn || ui.inkOn || ui.sel != null) {
        if (ui.sel != null) ui.sel = null else if (ui.inkOn) { ui.inkOn = false; ui.drawing = false } else if (findOn) findOn = false
    }
    SideEffect { ui.paged = mode != 0 }
    LaunchedEffect(ui.inkOn, findOn) { if (ui.inkOn || findOn) ui.sel = null }
    val ctx = LocalContext.current
    fun copySel() {
        val s = ui.sel ?: return
        scope.launch {
            val sb = StringBuilder()
            for (p in s.a.p..s.b.p) {
                val t = txt.page(p) ?: continue
                val a = if (p == s.a.p) s.a.c else 0
                val b = min(if (p == s.b.p) s.b.c else t.t.length, t.t.length)
                if (b <= a) continue
                val part = StringBuilder()
                for (k in a until b) part.append(if (k in t.lb) '\n' else t.t[k])
                val str = part.toString().replace(Regex(" *\n *"), "\n").trim()
                if (str.isNotEmpty()) { if (sb.isNotEmpty()) sb.append("\n\n"); sb.append(str) }
            }
            if (sb.isEmpty()) return@launch
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("PDF", sb.toString()))
            if (Build.VERSION.SDK_INT < 33) vm.show("Copied")
            ui.sel = null
        }
    }
    fun selectAll() {
        scope.launch {
            var a: PPos? = null
            var b: PPos? = null
            for (p in 0 until count) {
                val t = txt.page(p) ?: continue
                val k = t.r.indexOfFirst { it != null }
                if (k >= 0) { a = PPos(p, k); break }
            }
            for (p in count - 1 downTo 0) {
                val t = txt.page(p) ?: continue
                val k = t.r.indexOfLast { it != null }
                if (k >= 0) { b = PPos(p, k + 1); break }
            }
            if (a != null && b != null) ui.sel = PSel(a, b)
        }
    }

    val hostRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hapScr = LocalHapticFeedback.current
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { hostRef[0] = it }.selHandleTouch(ui, { hostRef[0] }, scope, hapScr)
    ) {
        val vwDp = maxWidth
        val vhDp = maxHeight
        val topPad = sat + 64.dp
        val botPad = sab + 96.dp

        val vwPx = vwDp.value * dens
        SideEffect { ui.scrollBy = null }   // mode lain tidak punya gulir otomatis; mode gulir mengisinya lagi tepat di bawah
        if (mode == 0) {
            // ---------------- mode gulir (menerus) ----------------
            val list = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
            SideEffect { ui.scrollBy = { d -> list.dispatchRawDelta(d) } }
            var zoom by remember { mutableFloatStateOf(1f) }
            var hx by remember { mutableFloatStateOf(0f) }   // geseran horizontal (px) saat diperbesar
            fun clampX(x: Float, z: Float) = x.coerceIn(0f, max(0f, vwPx * (z - 1f)))
            // zoom ke z1 dengan titik (cx, cy) tetap di bawah jari
            fun applyZoom(z1: Float, cx: Float, cy: Float, panX: Float = 0f, panY: Float = 0f) {
                val k = z1 / zoom
                if (k != 1f) list.dispatchRawDelta((list.firstVisibleItemScrollOffset + cy) * (k - 1f))
                hx = clampX((hx + cx) * k - cx - panX, z1)
                zoom = z1
                if (panY != 0f) list.dispatchRawDelta(-panY)
            }
            val curPage by remember(list) {
                derivedStateOf {
                    val li = list.layoutInfo
                    val mid = li.viewportStartOffset + li.viewportSize.height / 2
                    li.visibleItemsInfo.firstOrNull { it.offset <= mid && it.offset + it.size >= mid }?.index ?: list.firstVisibleItemIndex
                }
            }
            LaunchedEffect(curPage) { page = curPage }
            LaunchedEffect(goTo) {
                val h = goTo ?: return@LaunchedEffect
                goTo = null
                val itemH = vwDp.value * zoom * ui.doc.ratioOf(h.page) * dens
                val top = h.rects.minOf { it[1] } * itemH
                list.scrollToItem(h.page, 0)
                val off = top - list.layoutInfo.viewportSize.height * .3f
                if (off > 0f) list.scrollBy(off)
            }
            val fs = remember { PdfFast() }
            val fsJob = remember { arrayOfNulls<Job>(1) }
            val fsLast = remember { intArrayOf(-1) }
            fun fastSeek(nf: Float) {
                val pos = nf * (count - 1)
                val i = pos.toInt().coerceIn(0, count - 1)
                if (i != fsLast[0]) { fsLast[0] = i; hapScr.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                fsJob[0]?.cancel()
                fsJob[0] = scope.launch {
                    val itemH = vwDp.value * zoom * ui.doc.ratioOf(i) * dens
                    list.scrollToItem(i, ((pos - i) * itemH).roundToInt())
                }
            }
            Box(
                Modifier.fillMaxSize().clipToBounds()
                    .fastTouch(fs) { fastSeek(it) }
                    .pinch(
                        onPinch = { z, pan, c -> applyZoom((zoom * z).coerceIn(1f, 5f), c.x, c.y, pan.x, pan.y) },
                        onEnd = { },
                        onMulti = { ui.multi = it }
                    )
                    .pointerInput(ui.inkOn) {
                        if (ui.inkOn) return@pointerInput
                        detectTapGestures(
                            onTap = { if (ui.sel != null) ui.sel = null else bars = !bars },
                            onDoubleTap = { p ->
                                val z0 = zoom
                                val z1 = if (z0 > 1.2f) 1f else 2.5f
                                scope.launch { animate(0f, 1f, animationSpec = spring(1f, 420f)) { v, _ -> applyZoom(z0 + (z1 - z0) * v, p.x, p.y) } }
                            }
                        )
                    }
                    .pointerInput(zoom > 1.01f, ui.inkOn, ui.drawing) {
                        if (zoom <= 1.01f || ui.inkOn || ui.drawing) return@pointerInput
                        detectHorizontalDragGestures { c, d -> c.consume(); hx = clampX(hx - d, zoom) }
                    }
            ) {
                val contentW = vwDp * zoom
                LazyColumn(
                    state = list,
                    userScrollEnabled = !ui.drawing,   // menyeret pegangan seleksi / menggoreskan pena: daftar tidak boleh ikut bergulir
                    modifier = Modifier.requiredWidth(contentW).fillMaxHeight().graphicsLayer { translationX = vwPx * (zoom - 1f) / 2f - hx },
                    contentPadding = PaddingValues(top = topPad, bottom = botPad),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(count, key = { it }) { i ->
                        PdfPage(ui, i, contentW, 1f, Modifier.width(contentW).height(contentW * ui.doc.ratioOf(i)))
                    }
                }
                PdfFastScrollUi(ui, fs, list, count, topPad, botPad)
            }
        } else if (silkMode) {
            // ---------------- mode halaman 3D, gaya Silk curl (OpenGL ES 2.0) ----------------
            PdfSilkCurl(
                vm, ui, count, vwDp, vhDp, topPad, sab, dens, startPage, page, { page = it }, goTo, { goTo = null }, findOn,
                { if (ui.sel != null) ui.sel = null else bars = !bars },
                {
                    if (!silkFailed) vm.show("Silk curl isn't available on this device, using Classic curl")
                    silkFailed = true
                }
            )
        } else if (curlMode) {
            // ---------------- mode halaman 3D: efek balik halaman melengkung (library PageCurl) ----------------
            // awal HARUS konstan (startPage), bukan `page`: `page` berubah tiap balik halaman, dan state yang ikut berubah membuat
            // lambda pointerInput di bawah (dibuat sekali) memegang state lama yang sudah mati => ketukan hanya jalan sekali
            // state PageCurl dibuat ulang setelah tiap balikan lewat ketukan (gen++): next()/prev() bawaan library meninggalkan state
            // internalnya macet sehingga ketukan berikutnya tak bereaksi, sedangkan geser jari tidak terpengaruh. State baru mulai
            // dari halaman tujuan, jadi tidak ada lompatan.
            var gen by remember { mutableIntStateOf(0) }
            // mulai dari halaman yang sedang dibaca (bukan startPage): cabang ini dibuat ulang tiap ink dimatikan, jadi startPage membuat lompat ke awal
            var baseP by remember { mutableIntStateOf(page.coerceIn(0, count - 1)) }
            var flip by remember { mutableStateOf<Job?>(null) }
            val cs = androidx.compose.runtime.key(gen) { rememberPageCurlState(baseP) }
            val csNow by androidx.compose.runtime.rememberUpdatedState(cs)
            val r0 = ui.doc.ratioOf(startPage)
            val w0 = minOf(vwDp, (vhDp - topPad - sab - 12.dp) / r0)
            val cfg = rememberPageCurlConfig(
                backPageColor = if (ui.dark) Color(0xFF1C1C1E) else Color.White,
                dragInteraction = PageCurlConfig.GestureDragInteraction()
            )
            var cz by remember { mutableFloatStateOf(1f) }
            var cpan by remember { mutableStateOf(Offset.Zero) }
            val cMul by remember { derivedStateOf { ceil(cz).coerceAtLeast(1f) } }
            val availPx = (vhDp - topPad - sab - 12.dp).value * dens
            fun cClamp(o: Offset, z: Float): Offset {
                val w = w0.value * dens * z
                val h = w0.value * dens * r0 * z
                val mx = ((w - vwPx) / 2f).coerceAtLeast(0f)
                val my = ((h - availPx) / 2f).coerceAtLeast(0f)
                return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
            }
            fun cZoom(z1: Float, c: Offset, delta: Offset = Offset.Zero) {
                val k = z1 / cz
                val origin = Offset(vwPx / 2f, topPad.value * dens + availPx / 2f)
                cpan = cClamp((c - origin) * (1f - k) + cpan * k + delta, z1)
                cz = z1
            }
            LaunchedEffect(cs) { snapshotFlow { cs.current }.collect { page = it; cz = 1f; cpan = Offset.Zero } }
            LaunchedEffect(goTo) {
                val h = goTo ?: return@LaunchedEffect
                goTo = null
                cs.snapTo(h.page)
            }
            Box(
                Modifier.fillMaxSize()
                    .pinch(
                        onPinch = { z, p, c -> cZoom((cz * z).coerceIn(1f, 4f), c, p) },
                        onEnd = { if (cz < 1.05f) { cz = 1f; cpan = Offset.Zero } }
                    )
                    .curlTouch(
                        scope, true, { cz > 1.02f }, { findOn }, { d -> cpan = cClamp(cpan + d, cz) },
                        onEdge = { d ->
                            val st = csNow
                            val to = st.current + d
                            if (to in 0 until count && flip?.isActive != true) {
                                flip = scope.launch {
                                    try {
                                        withTimeoutOrNull(1200) { if (d > 0) st.next() else st.prev() }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                    }
                                    baseP = to
                                    gen++
                                }
                            }
                        },
                        onTap ={ if (ui.sel != null) ui.sel = null else bars = !bars },
                        onDouble = { p ->
                            val z0 = cz
                            val z1 = if (z0 > 1.2f) 1f else 2.5f
                            scope.launch { animate(0f, 1f, animationSpec = spring(1f, 420f)) { v, _ -> cZoom(z0 + (z1 - z0) * v, p) } }
                        },
                        busy = { ui.drawing },
                        selBusy = { ui.selGrab }
                    )
                    .padding(top = topPad, bottom = sab + 12.dp),
                Alignment.Center
            ) {
                PageCurl(
                    count = count, state = cs, config = cfg,
                    modifier = Modifier.width(w0).height(w0 * r0).graphicsLayer {
                        scaleX = cz; scaleY = cz; translationX = cpan.x; translationY = cpan.y
                    }
                ) { p ->
                    PdfPage(ui, p, w0, cMul, Modifier.fillMaxSize(), active = p == cs.current)
                }
            }
        } else {
            // ---------------- mode halaman (geser per halaman) ----------------
            val pager = rememberPagerState(initialPage = page.coerceIn(0, count - 1), pageCount = { count })
            var pz by remember { mutableFloatStateOf(1f) }
            var pan by remember { mutableStateOf(Offset.Zero) }
            LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { page = it; pz = 1f; pan = Offset.Zero } }
            LaunchedEffect(goTo) {
                val h = goTo ?: return@LaunchedEffect
                goTo = null
                pager.scrollToPage(h.page)
            }
            fun fitW(p: Int): Dp {
                val r = ui.doc.ratioOf(p)
                val availH = vhDp - topPad - sab - 12.dp
                return minOf(vwDp, availH / r)
            }
            fun clampPan(o: Offset, z: Float): Offset {
                val w = fitW(pager.currentPage).value * dens * z
                val h = w * ui.doc.ratioOf(pager.currentPage)
                val vx = vwDp.value * dens
                val vy = (vhDp - topPad - sab - 12.dp).value * dens
                val mx = ((w - vx) / 2f).coerceAtLeast(0f)
                val my = ((h - vy) / 2f).coerceAtLeast(0f)
                return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
            }
            // pusat halaman di layar (px): titik asal skala graphicsLayer
            fun origin() = Offset(vwPx / 2f, topPad.value * dens + (vhDp - topPad - sab - 12.dp).value * dens / 2f)
            fun zoomAt(z1: Float, c: Offset, delta: Offset = Offset.Zero) {
                val k = z1 / pz
                val d = c - origin()
                pan = clampPan(d * (1f - k) + pan * k + delta, z1)
                pz = z1
            }
            Box(
                Modifier.fillMaxSize()
                    .pinch(
                        onPinch = { z, p, c -> zoomAt((pz * z).coerceIn(1f, 4f), c, p) },
                        onEnd = { if (pz < 1.05f) { pz = 1f; pan = Offset.Zero } },
                        onMulti = { ui.multi = it }
                    )
                    .curlTouch(
                        scope, !ui.inkOn, { pz > 1.05f }, { findOn }, { d -> pan = clampPan(pan + d, pz) },
                        onEdge = { d ->
                            val t = pager.currentPage + d
                            if (t in 0 until count) scope.launch { pager.animateScrollToPage(t) }
                        },
                        onTap = { if (ui.sel != null) ui.sel = null else bars = !bars },
                        onDouble = { p ->
                            val z0 = pz
                            val z1 = if (z0 > 1.2f) 1f else 2.5f
                            scope.launch { animate(0f, 1f, animationSpec = spring(1f, 420f)) { v, _ -> zoomAt(z0 + (z1 - z0) * v, p) } }
                        },
                        busy = { ui.drawing },
                        selBusy = { ui.selGrab }
                    )
            ) {
                HorizontalPager(
                    state = pager, modifier = Modifier.fillMaxSize(), pageSpacing = 12.dp,
                    userScrollEnabled = pz <= 1.05f && !ui.drawing && !findOn, beyondViewportPageCount = 1
                ) { p ->
                    val here = p == pager.currentPage
                    val w = fitW(p)
                    Box(Modifier.fillMaxSize().padding(top = topPad, bottom = sab + 12.dp), Alignment.Center) {
                        val mul = if (here) ceil(pz).coerceAtLeast(1f) else 1f
                        PdfPage(
                            ui, p, w, mul,
                            Modifier.width(w).height(w * ui.doc.ratioOf(p)).graphicsLayer {
                                if (here) { scaleX = pz; scaleY = pz; translationX = pan.x; translationY = pan.y }
                            }
                        )
                    }
                }
            }
        }

        // ---------------- bilah atas: hitam polos (seperti semula). Turun / naik dengan pegas; tombol berubah bentuk saat ditekan / aktif;
        // tombol cari membuat bilah cari tumbuh dari ikonnya sementara judul menggeser keluar, yang lain menyingkir ----------------
        val spRaw by androidx.compose.animation.core.animateFloatAsState(if (findOn) 1f else 0f, spring(.85f, 520f), label = "find")
        // spring bisa overshoot (>1 atau <0): padding/lebar negatif membuat app force close, jadi dikunci 0..1
        val sp = spRaw.coerceIn(0f, 1f)
        val white = Color.White
        AnimatedVisibility(
            bars || findOn, Modifier.align(Alignment.TopCenter),
            enter = fadeIn(spring(stiffness = 600f)) + androidx.compose.animation.slideInVertically(spring(.72f, 520f)) { -it / 3 },
            exit = fadeOut(spring(stiffness = 800f)) + androidx.compose.animation.slideOutVertically(spring(1f, 800f)) { -it / 3 }
        ) {
            Box(Modifier.fillMaxWidth().background(Color.Black).padding(top = sat).height(60.dp).padding(horizontal = 6.dp)) {
                Row(
                    Modifier.fillMaxSize().graphicsLayer { alpha = (1f - sp * 2f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PdfBtn({ vm.nav.pop() }, size = 46.dp) { Ic("back", it, 24.dp) }
                    T(
                        note.t.ifBlank { "PDF" }, 18f, 600, white,
                        Modifier.weight(1f).padding(horizontal = 6.dp).graphicsLayer { translationX = -sp * 28.dp.toPx() }, maxLines = 1
                    )
                    PdfBtn({ if (hasText) findOn = true else vm.show("This PDF is a scan, so there's no text to search") }) {
                        Ic("search", if (hasText) it else it.copy(alpha = .38f), 24.dp)
                    }
                    PdfBtn({ ui.inkOn = !ui.inkOn }, selected = ui.inkOn) { InkToolIcon(0, it) }
                    PdfBtn({ vm.nav.push(Scr.NoteMenu(id)) }) { Ic("more", it, 24.dp) }
                }
                if (sp > .01f) {
                    val full = vwDp - 12.dp
                    Box(
                        Modifier.align(Alignment.CenterEnd).padding(end = 88.dp * (1f - sp))
                            .width(44.dp + (full - 44.dp) * sp).height(46.dp).clip(CircleShape).background(pal.c3)
                    ) {
                        Row(
                            Modifier.fillMaxSize().graphicsLayer { alpha = ((sp - .45f) / .4f).coerceIn(0f, 1f) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBtn("back", white, { findOn = false }, size = 44.dp)
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                modifier = Modifier.weight(1f).focusRequester(fr),
                                textStyle = TextStyle(color = white, fontFamily = LocalFF.current, fontSize = 16.sp, fontWeight = FontWeight(500)),
                                cursorBrush = SolidColor(white),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { step(1) }),
                                decorationBox = { inner -> Box { if (query.isEmpty()) T("Search", 16f, 500, white.copy(alpha = .5f)); inner() } }
                            )
                            if (searching && ui.hits.isEmpty()) LoadingBlob(Modifier.size(22.dp))
                            else if (query.isNotBlank()) T(if (ui.hits.isEmpty()) "0" else "${ui.cur + 1}/${ui.hits.size}", 13f, 700, white.copy(alpha = .7f))
                            IconBtn("aup", white, { step(-1) }, size = 40.dp, enabled = ui.hits.isNotEmpty())
                            IconBtn("adn", white, { step(1) }, size = 40.dp, enabled = ui.hits.isNotEmpty())
                        }
                    }
                }
            }
        }

        // ---------------- penunjuk halaman ----------------
        AnimatedVisibility(
            bars && !ui.inkOn && !findOn && ui.sel == null, Modifier.align(Alignment.BottomCenter).padding(bottom = sab + 16.dp),
            enter = androidx.compose.animation.slideInVertically(M3E.spatial()) { it / 2 } +
                scaleIn(M3E.spatial(), .86f, TransformOrigin(.5f, 1f)),
            exit = androidx.compose.animation.slideOutVertically(M3E.spatial()) { it / 2 } +
                androidx.compose.animation.scaleOut(M3E.spatial(), .86f, TransformOrigin(.5f, 1f))
        ) {
            PageCapsule(page, count) { i ->
                page = i
                goTo = PHit(i, listOf(floatArrayOf(0f, 0f, 0f, 0f)), "", 0, 0)   // pindah halaman tanpa menggulir ke dalam halaman
            }
        }

        // ---------------- bilah seleksi teks: Copy / Select all / Cancel ----------------
        AnimatedVisibility(
            ui.sel != null && !ui.inkOn, Modifier.align(Alignment.BottomCenter).padding(bottom = sab + 16.dp),
            // naik dari bawah sambil membesar dengan pantulan; keluar menciut cepat tanpa pantulan (tanpa pudar biasa)
            enter = androidx.compose.animation.scaleIn(spring(.5f, 440f), initialScale = .6f, transformOrigin = TransformOrigin(.5f, 1f)) +
                androidx.compose.animation.slideInVertically(spring(.68f, 420f)) { it },
            exit = androidx.compose.animation.scaleOut(spring(1f, 900f), targetScale = .6f, transformOrigin = TransformOrigin(.5f, 1f)) +
                androidx.compose.animation.slideOutVertically(spring(1f, 900f)) { it / 2 }
        ) {
            PdfSelBar({ copySel() }, { selectAll() }, { ui.sel = null })
        }

        // ---------------- alat coret: dock bergaya editor (ToolDock / MorphDock di DocBar) ----------------
        // SATU wadah mengambang: masuk dengan geser + skala berpegas, melar seperti jeli saat berubah, tombol dalam grup tonal tersambung,
        // alat dipilih lewat SlideSeg (penanda meluncur), panel warna + tebal tumbuh ke atas DI DALAM wadah yang sama.
        // Saat mode cari aktif, dock selalu tampil (di atas keyboard) dan baru aktif setelah salah satu alatnya dipilih.
        val inkShown = ui.inkOn || findOn
        val imeDp = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
        AnimatedVisibility(
            visible = inkShown,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = maxOf(sab, imeDp) + 12.dp),
            enter = androidx.compose.animation.slideInVertically(M3E.spatial()) { it / 2 } +
                scaleIn(M3E.spatial(), .86f, TransformOrigin(.5f, 1f)),
            exit = androidx.compose.animation.slideOutVertically(M3E.spatial()) { it / 2 } +
                androidx.compose.animation.scaleOut(M3E.spatial(), .86f, TransformOrigin(.5f, 1f))
        ) {
            PdfDock(ui.inkOn to ui.tool) {
                Column(Modifier.width(DockW)) {
                    // panel warna + tebal: tumbuh ke atas di dalam wadah yang sama (pegas tanpa pantulan, seperti sub-bilah editor)
                    AnimatedVisibility(
                        visible = ui.inkOn,
                        enter = expandVertically(spring(1f, 520f)),
                        exit = shrinkVertically(spring(1f, 620f))
                    ) {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), Alignment.Center) { PdfInkSub(ui) { saveTool() } }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(DockH).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SlideSeg(
                            count = 3, sel = if (ui.inkOn) ui.tool else -1, i = 0,
                            onPick = { t ->
                                saveTool()
                                ui.tool = t
                                ui.color = vm.pr.ic.getOrElse(t) { 0 }
                                ui.wDp = vm.pr.iw.getOrElse(t) { if (t == 0) 3f else if (t == 1) 16f else 18f }
                                if (!ui.inkOn) ui.inkOn = true
                                // mode cari: sembunyikan keyboard supaya area coret lega
                                if (findOn) { focusMgr.clearFocus(); kb?.hide() }
                            }
                        ) { k, c -> InkToolIcon(k, c) }
                        XGroup(i = 1) {
                            XBtn(enabled = ui.ink.canUndo, onClick = { ui.ink.undo() }) { Ic("undo", it) }
                            XBtn(enabled = ui.ink.canRedo, onClick = { ui.ink.redo() }) {
                                Box(Modifier.graphicsLayer { scaleX = -1f }) { Ic("undo", it) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (findOn) {
                            // mode cari: X hanya mematikan mode coret, dock tetap di tempatnya
                            XBtn(i = 2, enabled = ui.inkOn, onClick = { saveTool(); ui.inkOn = false; ui.drawing = false }) { Ic("x", it) }
                        } else {
                            XBtn(tone = 2, i = 2, w = 68.dp, onClick = { saveTool(); ui.inkOn = false }) { Ic("ok", it) }
                        }
                    }
                }
            }
        }
    }
}


// ---------------------------------------------------------------- komponen M3 Expressive

/**
 * Tombol ikon M3 Expressive: bulat saat diam; ditekan = lapisan keadaan muncul + bentuk menciut jadi kotak membulat;
 * dipilih (fill = true) = kotak membulat berwarna utama. Bentuk, warna, dan skala semuanya berpegas.
 * fill = false: tombol tidak punya latar sendiri (dipakai bersama [PdfSlideMark]); hanya warna isinya yang berganti.
 */
@Composable
private fun PdfBtn(
    onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, tonal: Boolean = false,
    enabled: Boolean = true, size: Dp = 44.dp, fill: Boolean = true, content: @Composable (Color) -> Unit
) {
    val pal = LocalPal.current
    val hap = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) size * .26f else if (selected && fill) size * .34f else size / 2, spring(.55f, 700f), label = "pbr")
    val sc by animateFloatAsState(if (pr) .9f else 1f, spring(.45f, 650f), label = "pbs")
    val bg by animateColorAsState(
        if (selected && fill) pal.p else if (tonal) pal.c4 else if (pr) pal.os.copy(alpha = .14f) else Color.Transparent,
        spring(stiffness = 500f), label = "pbb"
    )
    val fg by animateColorAsState(if (selected) pal.op else pal.os, spring(stiffness = 500f), label = "pbf")
    Box(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else .38f }
            .size(size).clip(RoundedCornerShape(r.coerceAtLeast(0.dp))).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled) {
                hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        Alignment.Center
    ) { content(fg) }
}

// ---------------------------------------------------------------- gulir cepat (tepi kanan, mode gulir)

/** Keadaan pegangan gulir cepat; dibaca oleh penggambar ([PdfFastScrollUi]) dan oleh penangkap sentuhan di induk ([fastTouch]). */
@Stable
private class PdfFast {
    var vis by mutableStateOf(false)
    var drag by mutableStateOf(false)
    var dragF by mutableFloatStateOf(0f)
    var f = 0f          // posisi daftar 0..1 (diisi penggambar)
    var top = 0f        // px: tepi atas lintasan
    var track = 1f      // px: panjang lintasan gagang
    var thumbH = 0f     // px: tinggi area sentuh gagang
    var off = false     // mode coret aktif: pegangan tidak dipakai
}

/**
 * Penangkap sentuhan gagang, dipasang di induk (pass Initial) seperti [selHandleTouch]: bila jari mendarat di gagang yang sedang tampil,
 * sentuhan dimakan di sini sehingga daftar tidak ikut bergulir, lalu geseran jari memetakan posisi gagang -> halaman lewat [onFrac].
 */
private fun Modifier.fastTouch(fs: PdfFast, onFrac: (Float) -> Unit): Modifier = pointerInput(fs) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!fs.vis || fs.off) return@awaitEachGesture
        val y0 = fs.top + fs.track * (if (fs.drag) fs.dragF else fs.f)
        val zone = 44.dp.toPx()
        val pad = 10.dp.toPx()
        if (down.position.x < size.width - zone || down.position.y < y0 - pad || down.position.y > y0 + fs.thumbH + pad) return@awaitEachGesture
        down.consume()
        val grab = down.position.y - y0
        fs.dragF = fs.f
        fs.drag = true
        try {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (ev.changes.count { it.pressed } >= 2) break
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                c.consume()
                if (c.changedToUp()) break
                val nf = ((c.position.y - grab - fs.top) / fs.track.coerceAtLeast(1f)).coerceIn(0f, 1f)
                fs.dragF = nf
                onFrac(nf)
            }
        } finally { fs.drag = false }
    }
}

/**
 * Gagang gulir cepat M3 Expressive: muncul (menggeser masuk dari tepi) saat menggulir, lalu hilang sendiri. Tipis saat diam; disentuh =
 * membesar jadi pil tebal berpegangan, dan gelembung berisi pratinjau halaman + nomornya tumbuh dari gagang.
 */
@Composable
private fun PdfFastScrollUi(ui: PdfUi, fs: PdfFast, list: LazyListState, count: Int, topPad: Dp, botPad: Dp) {
    if (count < 2) return
    val pal = LocalPal.current
    val dens = LocalDensity.current.density
    var h by remember { mutableIntStateOf(0) }
    val thumbH = 64.dp
    val listF by remember(list, count) {
        derivedStateOf {
            if (!list.canScrollForward) 1f else {
                val f = list.layoutInfo.visibleItemsInfo.firstOrNull()
                val pos = if (f == null) 0f else list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset / f.size.toFloat().coerceAtLeast(1f)
                (pos / (count - 1)).coerceIn(0f, 1f)
            }
        }
    }
    SideEffect {
        fs.f = listF
        fs.off = ui.inkOn
        fs.top = topPad.value * dens
        fs.thumbH = thumbH.value * dens
        fs.track = (h - fs.top - botPad.value * dens - fs.thumbH).coerceAtLeast(1f)
    }
    val scrolling = list.isScrollInProgress
    LaunchedEffect(scrolling, fs.drag) { if (scrolling || fs.drag) fs.vis = true else { delay(1600); fs.vis = false } }
    val appear by animateFloatAsState(if (fs.vis && !ui.inkOn) 1f else 0f, spring(.7f, 420f), label = "fsa")
    val grow by animateFloatAsState(if (fs.drag) 1f else 0f, spring(.55f, 460f), label = "fsg")
    val f = if (fs.drag) fs.dragF else listF
    val shownPg = (f * (count - 1)).roundToInt().coerceIn(0, count - 1)

    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shownPg, fs.drag) {
        if (!fs.drag) return@LaunchedEffect
        val key = ui.fid + ":" + shownPg + "@256"
        val hit = PdfCache.get(key)
        if (hit != null) { thumb = hit.asImageBitmap(); return@LaunchedEffect }
        delay(70)
        val b = ui.doc.render(shownPg, 256) ?: return@LaunchedEffect
        PdfCache.put(key, b)
        thumb = b.asImageBitmap()
    }

    Box(Modifier.fillMaxSize().onSizeChanged { h = it.height }) {
        if (appear > .01f) {
            val yPx = fs.top + fs.track * f
            val g = grow.coerceAtLeast(0f)
            Box(
                Modifier.align(Alignment.TopEnd).offset { IntOffset(0, yPx.roundToInt()) }.size(44.dp, thumbH)
                    .graphicsLayer { alpha = appear.coerceIn(0f, 1f); translationX = (1f - appear.coerceIn(0f, 1f)) * 22.dp.toPx() }
            ) {
                if (g > .01f) {
                    val ar = ui.doc.ratioOf(shownPg).coerceIn(.5f, 2.2f)
                    Row(
                        Modifier.align(Alignment.CenterEnd).offset(x = (-40).dp)
                            .graphicsLayer { transformOrigin = TransformOrigin(1f, .5f); scaleX = g; scaleY = g; alpha = g.coerceIn(0f, 1f) }
                            .shadow(8.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(pal.pc)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Canvas(Modifier.width(84.dp).height((84f * ar).dp).clip(RoundedCornerShape(8.dp)).background(Color.White)) {
                            val b = thumb
                            if (b != null) drawImage(
                                b, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                filterQuality = FilterQuality.Low, colorFilter = if (ui.dark) INVERT else null
                            )
                        }
                        Column(Modifier.padding(end = 6.dp)) {
                            T((shownPg + 1).toString(), 26f, 900, pal.opc)
                            T("of $count", 12f, 600, pal.opc.copy(alpha = .7f))
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                        .width((5f + 15f * g).dp).height((46f + 18f * g).dp)
                        .shadow((2f + 4f * g).dp, CircleShape).clip(CircleShape).background(pal.p),
                    Alignment.Center
                ) {
                    Canvas(Modifier.size(10.dp, 16.dp).graphicsLayer { alpha = g.coerceIn(0f, 1f) }) {
                        val sw = 2.dp.toPx()
                        for (i in 0..2) drawLine(pal.op, Offset(0f, size.height * i / 2f), Offset(size.width, size.height * i / 2f), sw, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/** Ruas trek dengan ujung luar bulat penuh dan sisi dekat gagang bersudut kecil (gaya penggeser M3 Expressive). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.pdfSeg(l: Float, r: Float, cy: Float, th: Float, col: Color, leftBig: Boolean) {
    if (r - l < 1f) return
    val big = CornerRadius(th / 2f)
    val small = CornerRadius(2.dp.toPx())
    val p = androidx.compose.ui.graphics.Path()
    p.addRoundRect(
        RoundRect(
            l, cy - th / 2f, r, cy + th / 2f,
            if (leftBig) big else small, if (leftBig) small else big,
            if (leftBig) small else big, if (leftBig) big else small
        )
    )
    drawPath(p, col)
}

/** Garis bergelombang yang merambat (indikator progres bergelombang M3 Expressive). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.pdfWave(x0: Float, x1: Float, cy: Float, amp: Float, wl: Float, ph: Float, sw: Float, col: Color) {
    if (x1 - x0 < 1f) return
    val k = (2.0 * Math.PI / wl).toFloat()
    val p = androidx.compose.ui.graphics.Path()
    var x = x0
    p.moveTo(x, cy + amp * sin(x * k - ph))
    while (x < x1) { x = min(x + 3f, x1); p.lineTo(x, cy + amp * sin(x * k - ph)) }
    drawPath(p, col, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Progres bergelombang: bagian terisi bergelombang dan bergerak, sisanya garis lurus berujung bulat. */
@Composable
private fun PdfWavy(frac: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val inf = rememberInfiniteTransition(label = "wv")
    val ph by inf.animateFloat(0f, 6.2831855f, infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.LinearEasing)), label = "wvp")
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), spring(.8f, 200f), label = "wvf")
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val sw = 5.dp.toPx()
        val cy = size.height / 2f
        val end = sw / 2f + (size.width - sw) * f
        if (size.width - sw / 2f - (end + sw) > 1f) drawLine(pal.osv.copy(alpha = .28f), Offset(end + sw, cy), Offset(size.width - sw / 2f, cy), sw, StrokeCap.Round)
        pdfWave(sw / 2f, end, cy, 3.dp.toPx(), 20.dp.toPx(), ph, sw, pal.p)
    }
}

/** Penggeser tebal M3 Expressive: trek berujung bulat, celah di sekitar gagang tegak yang memipih saat disentuh. */
@Composable
private fun PdfSlider(frac: Float, onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val hap = LocalHapticFeedback.current
    val cb by androidx.compose.runtime.rememberUpdatedState(onChange)
    val fin by androidx.compose.runtime.rememberUpdatedState(onDone)
    var drag by remember { mutableStateOf(false) }
    val hw by animateDpAsState(if (drag) 3.dp else 5.dp, spring(.6f, 520f), label = "psh")
    Canvas(
        modifier.height(40.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pad = 8.dp.toPx()
                fun at(x: Float): Float = ((x - pad) / (size.width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                drag = true
                hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                cb(at(down.position.x)); down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    cb(at(c.position.x)); c.consume()
                }
                drag = false
                fin()
            }
        }
    ) {
        val pad = 8.dp.toPx()
        val th = 14.dp.toPx()
        val gap = 6.dp.toPx()
        val w = hw.toPx()
        val cy = size.height / 2f
        val x = pad + (size.width - 2f * pad) * frac.coerceIn(0f, 1f)
        pdfSeg(pad, x - w / 2f - gap, cy, th, pal.p, true)
        pdfSeg(x + w / 2f + gap, size.width - pad, cy, th, pal.os.copy(alpha = .22f), false)
        drawRoundRect(pal.p, Offset(x - w / 2f, cy - 18.dp.toPx()), Size(w, 36.dp.toPx()), CornerRadius(w / 2f))
    }
}

/**
 * Wadah dock bersama (meniru MorphDock editor): permukaan c3 mengambang yang MELAR ke samping dan memipih seperti jeli tiap kali [kick] berubah,
 * sudutnya sesaat lebih kotak, lalu pulih dengan pegas berayun. Semuanya di lapisan gambar, tidak menyentuh tata letak.
 */
@Composable
private fun PdfDock(kick: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pal = LocalPal.current
    val k = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(kick) {
        if (first[0]) first[0] = false                       // kemunculan pertama punya gerak masuk sendiri
        else {
            k.animateTo(1f, tween(110))                      // melar dan memipih
            k.animateTo(0f, spring<Float>(dampingRatio = .38f, stiffness = 240f))   // pulih berayun
        }
    }
    val elev = if (pal.dark) 10.dp else 6.dp
    Box(
        modifier.graphicsLayer {
            val v = k.value
            scaleX = 1f + .05f * v
            scaleY = 1f - .06f * v
            transformOrigin = TransformOrigin(.5f, 1f)
            shadowElevation = elev.toPx()
            shape = RoundedCornerShape((34.dp.toPx() - 9.dp.toPx() * abs(v).coerceAtMost(1.5f)).coerceAtLeast(16.dp.toPx()))
            clip = true
        }.background(pal.c3)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDockMotion provides true) { content() }
    }
}

/** Panel di atas alat (sub-bilah dock): pilihan warna lewat SlideSeg, lalu grup tonal berisi pratinjau tebal + penggeser. */
@Composable
private fun PdfInkSub(ui: PdfUi, onSave: () -> Unit) {
    val pal = LocalPal.current
    val t = ui.tool
    val lo = if (t == 0) 1f else 8f
    val hi = if (t == 0) 12f else if (t == 1) 32f else 40f
    val frac = ((ui.wDp - lo) / (hi - lo)).coerceIn(0f, 1f)
    val ip = ui.inkPal ?: pal
    val col by animateColorAsState(if (t < 2) inkColor(ip, ui.color, t == 1) else pal.os, spring(stiffness = 500f), label = "ipc")
    val fillA by animateFloatAsState(if (t == 2) 0f else 1f, spring(1f, 500f), label = "ipf")
    val dot by animateDpAsState(ui.wDp.coerceIn(4f, 30f).dp, spring(.6f, 500f), label = "ipd")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // baris warna menutup di dalam wadah saat memilih penghapus (tidak ada warna untuk dipilih)
        AnimatedVisibility(
            visible = t < 2,
            enter = expandVertically(spring(1f, 520f)),
            exit = shrinkVertically(spring(1f, 620f))
        ) {
            SlideSeg(count = 5, sel = ui.color, i = 0, onPick = { ui.color = it; onSave() }) { k, c ->
                Box(Modifier.size(26.dp).border(2.dp, c, CircleShape).padding(4.dp).clip(CircleShape).background(inkColor(ip, k, t == 1)))
            }
        }
        XGroup(i = 1) {
            // pratinjau: titik berisi (pulpen / stabilo) menjadi cincin kosong (penghapus) dan sebaliknya
            Box(Modifier.size(44.dp), Alignment.Center) {
                Box(
                    Modifier.size(dot).clip(CircleShape).background(col.copy(alpha = fillA))
                        .border(2.dp, pal.os.copy(alpha = 1f - fillA), CircleShape)
                )
            }
            PdfSlider(frac, { ui.wDp = lo + it * (hi - lo) }, onSave, Modifier.width(196.dp))
            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * Kapsul penunjuk halaman. Ketuk = kapsul melebar jadi penggeser bergelombang (gaya Material 3 Expressive: trek bergelombang yang
 * merambat, celah di sekitar gagang tegak yang memipih saat ditekan, titik akhir). Tekan lalu geser di mana saja pada trek untuk
 * pindah halaman; menciut sendiri kalau dibiarkan. Getar halus tiap berganti halaman.
 */
@Composable
private fun PageCapsule(page: Int, count: Int, onSeek: (Int) -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val seek by androidx.compose.runtime.rememberUpdatedState(onSeek)
    var open by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(false) }
    var live by remember { mutableIntStateOf(page) }
    var touch by remember { mutableIntStateOf(0) }
    LaunchedEffect(open, drag, touch) { if (open && !drag) { delay(3200); open = false } }
    val shown = (if (drag) live else page).coerceIn(0, (count - 1).coerceAtLeast(0))
    val frac = if (count <= 1) 0f else shown / (count - 1f)
    val animF by animateFloatAsState(frac, spring(1f, 1500f), label = "pcf")
    val prog = if (count <= 1) 1f else (page + 1f) / count
    val animP by animateFloatAsState(prog, spring(1f, 800f), label = "pcp")
    val handleW by animateDpAsState(if (drag) 3.dp else 6.dp, spring(.6f, 520f), label = "pch")
    val amp by animateDpAsState(if (drag) 1.dp else 3.5.dp, spring(.6f, 400f), label = "pca")
    // wadah + pergantian isi yang sama dengan dock editor: wadah melar seperti jeli, isi lama menciut, isi baru melompat masuk
    PdfDock(open) {
    MorphSwap(if (open) 1 else 0, dirOf = { a, b -> if (b > a) 1 else -1 }, label = "pcap") { kk ->
        val isOpen = kk == 1
        if (!isOpen) {
            Row(
                Modifier.clickable { live = page; touch++; open = true }.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Canvas(Modifier.size(22.dp)) {
                    val sw = 3.dp.toPx()
                    val tl = Offset(sw / 2f, sw / 2f)
                    val sz = Size(size.width - sw, size.height - sw)
                    drawArc(pal.os.copy(alpha = .16f), 0f, 360f, false, tl, sz, style = Stroke(sw))
                    drawArc(pal.p, -90f, 360f * animP.coerceIn(0f, 1f), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                T("Page ${page + 1} of $count", 14f, 700, pal.os)
            }
        } else {
            val inf = rememberInfiniteTransition(label = "pcw")
            val ph by inf.animateFloat(0f, 6.2831855f, infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "pcwp")
            Row(
                Modifier.height(52.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                T((shown + 1).toString(), 15f, 800, pal.os, Modifier.widthIn(min = 24.dp))
                Canvas(
                    Modifier.width(200.dp).height(52.dp).pointerInput(count) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pad = 6.dp.toPx()
                            fun at(x: Float): Int {
                                val f = ((x - pad) / (size.width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                return (f * (count - 1).coerceAtLeast(0) + .5f).toInt()
                            }
                            drag = true; touch++
                            live = at(down.position.x)
                            seek(live)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            down.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                val p = at(c.position.x)
                                if (p != live) {
                                    live = p
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    seek(p)
                                }
                                c.consume()
                            }
                            drag = false; touch++
                        }
                    }
                ) {
                    val pad = 6.dp.toPx()
                    val sw = 5.dp.toPx()
                    val gap = 6.dp.toPx()
                    val hw = handleW.toPx()
                    val cy = size.height / 2f
                    val x = pad + (size.width - 2f * pad) * animF
                    // bagian terisi: gelombang yang merambat; sisanya: garis lurus berujung bulat + titik akhir
                    pdfWave(pad + sw / 2f, x - hw / 2f - gap, cy, amp.toPx(), 18.dp.toPx(), ph, sw, pal.p)
                    val b0 = x + hw / 2f + gap
                    val b1 = size.width - pad
                    if (b1 - b0 > sw) drawLine(pal.os.copy(alpha = .18f), Offset(b0 + sw / 2f, cy), Offset(b1 - sw / 2f, cy), sw, StrokeCap.Round)
                    if (b1 - b0 > 16.dp.toPx()) drawCircle(pal.p, 2.dp.toPx(), Offset(b1 - 7.dp.toPx(), cy))
                    drawRoundRect(
                        pal.p, Offset(x - hw / 2f, cy - 18.dp.toPx()), Size(hw, 36.dp.toPx()), CornerRadius(hw / 2f)
                    )
                }
                T(count.toString(), 15f, 700, pal.os.copy(alpha = .55f))
            }
        }
    }
    }
}

/** Isi kapsul muncul bergantian (membesar + pudar) setelah kapsulnya mulai terbuka; i = urutan. */
private fun Modifier.pop(p: () -> Float, i: Int): Modifier = graphicsLayer {
    val t = ((p() - .15f - i * .06f) / .5f).coerceIn(0f, 1f)
    val k = .4f + .6f * t
    alpha = t; scaleX = k; scaleY = k
}

private suspend fun withFrameNanos0() { androidx.compose.runtime.withFrameNanos { } }

// ---------------------------------------------------------------- menu titik tiga untuk PDF

@Composable
fun PdfMenuPage(vm: AppVM, nav: Nav, act: Actions, id: String) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val d = vm.note(id)
    val pinned = d?.p == true
    val archived = d?.a == true
    var renaming by remember { mutableStateOf(false) }
    val mode = if (PdfBus.mode < 0) ctx.getSharedPreferences("dinduy", 0).getInt("pdfmode", 0) else PdfBus.mode
    val fname = (d?.t?.ifBlank { null } ?: "Document").replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".pdf"
    val file = d?.let { PdfFiles.file(ctx, it.pdf) }

    val total = Regex("\\d+").find(d?.x ?: "")?.value?.toIntOrNull() ?: 0
    val curPg = ((d?.pg ?: 0) + 1).coerceIn(1, max(1, total))

    PageScaffold("PDF options", d?.t?.ifEmpty { null } ?: "PDF", { nav.pop() }) {
        SGroup("Document", 0) {
            SCard(POS_ONE, null, PaddingValues(20.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(pal.pc), Alignment.Center) { PIc("page", pal.opc, 32.dp) }
                        Column(Modifier.weight(1f)) {
                            T(d?.t?.ifBlank { null } ?: "PDF", 18f, 700, pal.os, maxLines = 1)
                            T(if (total > 0) "Page $curPg of $total" else (d?.x ?: ""), 14f, 400, pal.osv, lh = 1.3f)
                        }
                    }
                    if (total > 0) {
                        Spacer(Modifier.height(16.dp))
                        PdfWavy(curPg / total.toFloat())
                    }
                }
            }
        }
        SGroup("Reading", 60) {
            SCard(POS_TOP) {
                T("Layout", 16f, 500, pal.os, Modifier.padding(bottom = 12.dp))
                SegPill(2, mode, {
                    PdfBus.mode = it
                    ctx.getSharedPreferences("dinduy", 0).edit().putInt("pdfmode", it).apply()
                }) { i, fg ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (i == 0) Ic("rows", fg, 20.dp) else PIc("page", fg, 20.dp)
                        T(if (i == 0) "Scroll" else "Pages", 15f, 700, fg)
                    }
                }
            }
            val curlOn = (if (PdfBus.curl < 0) ctx.getSharedPreferences("dinduy", 0).getInt("pdfcurl", 0) else PdfBus.curl) == 1
            val curlStyle = if (PdfBus.curlStyle < 0) ctx.getSharedPreferences("dinduy", 0).getInt("pdfcurlstyle", 0) else PdfBus.curlStyle
            AnimatedVisibility(
                visible = mode == 1,
                enter = expandVertically(spring(.9f, 420f)) + fadeIn(tween(220)),
                exit = shrinkVertically(spring(1f, 500f)) + fadeOut(tween(140))
            ) {
                SCard(POS_MID, onClick = {
                    val v = if (curlOn) 0 else 1
                    PdfBus.curl = v
                    ctx.getSharedPreferences("dinduy", 0).edit().putInt("pdfcurl", v).apply()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            T("3D page", 16f, 500, pal.os)
                            T("Pages curl like a real book. Swipe or tap the left / right edge to turn, zoom in to pan", 14f, 400, pal.osv, lh = 1.3f)
                        }
                        MorphSwitch(curlOn)
                    }
                }
            }
            AnimatedVisibility(
                visible = mode == 1 && curlOn,
                enter = expandVertically(spring(.9f, 420f)) + fadeIn(tween(220)),
                exit = shrinkVertically(spring(1f, 500f)) + fadeOut(tween(140))
            ) {
                SCard(POS_MID) {
                    T("Curl style", 16f, 500, pal.os, Modifier.padding(bottom = 12.dp))
                    SegPill(2, curlStyle, {
                        PdfBus.curlStyle = it
                        ctx.getSharedPreferences("dinduy", 0).edit().putInt("pdfcurlstyle", it).apply()
                    }) { i, fg ->
                        T(if (i == 0) "Classic curl" else "Silk curl", 15f, 700, fg)
                    }
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0..1) {
                            Column(Modifier.alpha(if (curlStyle == i) 1f else .55f)) {
                                T(if (i == 0) "Classic curl" else "Silk curl", 14f, 700, pal.os)
                                T(if (i == 0) "Page rolls up like a cylinder" else "Page bends smoothly like paper", 14f, 400, pal.osv, lh = 1.3f)
                            }
                        }
                    }
                }
            }
            SCard(POS_BOT) {
                T("Page color", 16f, 500, pal.os, Modifier.padding(bottom = 4.dp))
                T("Dark turns white pages to true black (AMOLED)", 14f, 400, pal.osv, Modifier.padding(bottom = 12.dp), lh = 1.3f)
                SegPill(3, d?.pdk ?: 0, { v -> vm.upd(id) { it.copy(pdk = v) } }) { i, fg ->
                    T(listOf("Auto", "Dark", "Light")[i], 15f, 700, fg)
                }
            }
        }
        SGroup("This PDF", 120) {
            MRow(POS_TOP, "search", "Search in PDF", if (PdfBus.hasText) "Find and highlight any word" else "Not available: this PDF is a scan") {
                if (PdfBus.hasText) { nav.pop(); PdfBus.find++ } else vm.show("This PDF is a scan, so there's no text to search")
            }
            MRow(POS_MID, "pin", if (pinned) "Unpin" else "Pin", "Pinned notes show at the top") {
                vm.mark(listOf(id), if (pinned) "Unpinned" else "Pinned") { it.copy(p = !pinned) }
            }
            MRow(POS_BOT, "label", "Labels", d?.lb?.joinToString(", ")?.ifEmpty { null } ?: "Set labels for this PDF", chevron = true) {
                nav.push(Scr.NoteLabels(id))
            }
        }
        SGroup("Share and save", 180) {
            MRow(POS_TOP, "up", "Share PDF", "Send it with WhatsApp, Files, and more") { if (file != null) act.sharePdf(fname, file) }
            MRow(POS_BOT, "dl", "Save a copy", "Pick where to save the PDF") { if (file != null) act.savePdf(fname, file) }
        }
        SGroup("Manage", 240) {
            MRow(POS_TOP, "pen", "Rename", "Change the title shown on the home screen") { renaming = true }
            MRow(POS_MID, if (archived) "unarc" else "arc", if (archived) "Unarchive" else "Archive", "Moved to Archive and hidden from home") {
                if (archived) vm.mark(listOf(id), "Unarchived") { it.copy(a = false) }
                else { nav.pop(); nav.pop(); vm.mark(listOf(id), "Archived") { it.copy(a = true, p = false) } }
            }
            MRow(POS_MID, "lib", "Duplicate", "Make a copy of this PDF") { vm.duplicate(id) }
            MRow(POS_BOT, "del", "Delete", "Kept in Trash for 30 days", danger = true) {
                nav.pop(); nav.pop(); vm.mark(listOf(id), "Moved to Trash") { it.copy(dt = System.currentTimeMillis()) }
            }
        }
    }
    if (renaming) {
        val init = d?.t ?: ""
        var tf by remember { mutableStateOf(TextFieldValue(init, TextRange(init.length))) }
        val rfr = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(150); try { rfr.requestFocus() } catch (e: Exception) { } }
        val done = {
            val t = tf.text.trim()
            if (t.isNotEmpty()) vm.upd(id) { it.copy(t = t) }
            renaming = false
        }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = pal.c3,
            title = { T("Rename PDF", 22f, 900, pal.os) },
            text = {
                // padding ada DI DALAM kolom teks (decorationBox), jadi seluruh pil bisa diketuk dan kursor bisa digeser sampai ujung
                BasicTextField(
                    value = tf, onValueChange = { if (it.text.length <= 120) tf = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.c4).focusRequester(rfr),
                    textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 18.sp, fontWeight = FontWeight(600)),
                    cursorBrush = SolidColor(pal.p),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { done() }),
                    decorationBox = { inner -> Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) { inner() } }
                )
            },
            confirmButton = { TextButton(onClick = { done() }) { T("Save", 16f, 900, pal.p) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { T("Cancel", 16f, 700, pal.osv) } }
        )
    }
}

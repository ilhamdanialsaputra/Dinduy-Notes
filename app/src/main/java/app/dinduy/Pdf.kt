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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
class PgText(val t: String, val r: List<FloatArray?>)

private class Grab : PDFTextStripper() {
    val sb = StringBuilder()
    val rs = ArrayList<FloatArray?>()
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
    override fun writeLineSeparator() { sb.append(' '); rs.add(null) }
}

@Stable
class PdfText(private val ctx: Context, private val file: File) {
    private var doc: PDDocument? = null
    private var broken = false
    private val cache = HashMap<Int, PgText>()
    private val lock = Mutex()

    private fun docNow(): PDDocument? {
        if (broken) return null
        doc?.let { return it }
        return try {
            PDFBoxResourceLoader.init(ctx)
            PDDocument.load(file).also { doc = it }
        } catch (e: Throwable) { broken = true; null }
    }

    suspend fun page(i: Int): PgText? = withContext(Dispatchers.IO) {
        lock.withLock {
            cache[i]?.let { return@withLock it }
            val d = docNow() ?: return@withLock null
            try {
                val g = Grab()
                g.startPage = i + 1
                g.endPage = i + 1
                g.getText(d)
                val p = PgText(g.sb.toString(), g.rs)
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
    var pulse: androidx.compose.runtime.State<Float> = mutableFloatStateOf(.6f)
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

private fun Modifier.pinch(onPinch: (Float, Offset, Offset) -> Unit, onEnd: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var multi = false
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.count { it.pressed } >= 2) {
                multi = true
                onPinch(ev.calculateZoom(), ev.calculatePan(), ev.calculateCentroid(useCurrent = true))
                ev.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (ev.changes.any { it.pressed })
        if (multi) onEnd()
    }
}

// ---------------------------------------------------------------- satu halaman

@Composable
private fun PdfPage(ui: PdfUi, index: Int, widthDp: Dp, mul: Float, modifier: Modifier = Modifier) {
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
    val live = remember(index) { mutableStateOf<PLive?>(null) }
    var tick by remember(index) { mutableIntStateOf(0) }
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
            .background(if (ui.dark) Color.Black else Color.White)
            .pointerInput(ui.inkOn, ui.tool, ui.ts, index) {
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
                    val W = size.width.toFloat().coerceAtLeast(1f)
                    val wPxTool = ui.wDp * dens
                    val lv = PLive(ui.tool, ui.color, wPxTool)
                    val erased = HashSet<PInk>()
                    ui.drawing = true
                    fun kOf(p: Float) = if (stylus && p > .01f) (.35f + .95f * p).coerceIn(.3f, 1.3f) else 1f
                    fun point(pos: Offset, pr: Float) {
                        if (ui.tool == 2) {
                            val r = wPxTool / 2f / W
                            (ui.ink.byPage[index] ?: emptyList()).forEach { s -> if (s !in erased && hitStroke(s, pos.x / W, pos.y / W, r)) erased.add(s) }
                        } else lv.add(pos.x, pos.y, kOf(pr))
                        live.value = lv; tick++
                    }
                    down.consume()
                    point(down.position, down.pressure)
                    var cancelled = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size >= 2 || ev.changes.any { it.isConsumed && it.id != down.id }) { cancelled = true; break }
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (c.changedToUp()) break
                        for (h in c.historical) point(h.position, c.pressure)
                        point(c.position, c.pressure)
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
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val b = bmp
            if (b != null) {
                drawImage(
                    b, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium, colorFilter = if (ui.dark) INVERT else null
                )
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
        if (bmp == null) Box(Modifier.align(Alignment.Center)) { LoadingBlob(Modifier.size(36.dp)) }
    }
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
    val ui = remember(file) { PdfUi(doc, ink, note.pdf).also { it.dark = dark; it.inkPal = vpal; it.ts = vm.pr.ts } }
    SideEffect { ui.dark = dark; ui.inkPal = vpal; ui.ts = vm.pr.ts }
    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !vpal.dark
    }
    DisposableEffect(appPal.dark) {
        onDispose {
            val w = (view.context as Activity).window
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !appPal.dark
        }
    }
    if (PdfBus.mode < 0) PdfBus.mode = ctx.getSharedPreferences("dinduy", 0).getInt("pdfmode", 0)

    androidx.compose.runtime.CompositionLocalProvider(
        LocalPal provides vpal,
        androidx.compose.material3.LocalContentColor provides vpal.os
    ) {
        Box(Modifier.fillMaxSize().background(if (dark) Color.Black else vpal.bg)) {
            when {
                doc.failed -> Column(
                    Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally
                ) {
                    T("This PDF can't be opened", 20f, 800, vpal.os)
                    Spacer(Modifier.height(8.dp))
                    T("The file is missing, damaged, or protected with a password.", 15f, 400, vpal.osv)
                    Spacer(Modifier.height(20.dp))
                    IconBtn("back", vpal.op, { vm.nav.pop() }, bg = vpal.p)
                }
                !doc.ok -> Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingBlob(Modifier.size(56.dp)) }
                else -> PdfReader(vm, id, act, ui, txt, note)
            }
        }
    }
}

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

    BackHandler(enabled = findOn || ui.inkOn) { if (findOn) findOn = false else { ui.inkOn = false; ui.drawing = false } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val vwDp = maxWidth
        val vhDp = maxHeight
        val topPad = sat + 64.dp
        val botPad = sab + 96.dp

        val vwPx = vwDp.value * dens
        if (mode == 0) {
            // ---------------- mode gulir (menerus) ----------------
            val list = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
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
            Box(
                Modifier.fillMaxSize().clipToBounds()
                    .pinch(
                        onPinch = { z, pan, c -> applyZoom((zoom * z).coerceIn(1f, 5f), c.x, c.y, pan.x, pan.y) },
                        onEnd = { }
                    )
                    .pointerInput(ui.inkOn) {
                        if (ui.inkOn) return@pointerInput
                        detectTapGestures(
                            onTap = { bars = !bars },
                            onDoubleTap = { p ->
                                val z0 = zoom
                                val z1 = if (z0 > 1.2f) 1f else 2.5f
                                scope.launch { animate(0f, 1f, animationSpec = spring(1f, 420f)) { v, _ -> applyZoom(z0 + (z1 - z0) * v, p.x, p.y) } }
                            }
                        )
                    }
                    .pointerInput(zoom > 1.01f, ui.inkOn) {
                        if (zoom <= 1.01f || ui.inkOn) return@pointerInput
                        detectHorizontalDragGestures { c, d -> c.consume(); hx = clampX(hx - d, zoom) }
                    }
            ) {
                val contentW = vwDp * zoom
                LazyColumn(
                    state = list,
                    modifier = Modifier.requiredWidth(contentW).fillMaxHeight().graphicsLayer { translationX = -hx },
                    contentPadding = PaddingValues(top = topPad, bottom = botPad),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(count, key = { it }) { i ->
                        PdfPage(ui, i, contentW, 1f, Modifier.width(contentW).height(contentW * ui.doc.ratioOf(i)))
                    }
                }
            }
        } else {
            // ---------------- mode halaman (geser per halaman) ----------------
            val pager = rememberPagerState(initialPage = startPage, pageCount = { count })
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
                        onEnd = { if (pz < 1.05f) { pz = 1f; pan = Offset.Zero } }
                    )
                    .pointerInput(ui.inkOn) {
                        if (ui.inkOn) return@pointerInput
                        detectTapGestures(
                            onTap = { bars = !bars },
                            onDoubleTap = { p ->
                                val z0 = pz
                                val z1 = if (z0 > 1.2f) 1f else 2.5f
                                scope.launch { animate(0f, 1f, animationSpec = spring(1f, 420f)) { v, _ -> zoomAt(z0 + (z1 - z0) * v, p) } }
                            }
                        )
                    }
                    .then(
                        if (pz > 1.05f && !ui.inkOn) Modifier.pointerInput(Unit) {
                            detectDragGestures { c, d -> c.consume(); pan = clampPan(pan + d, pz) }
                        } else Modifier
                    )
            ) {
                HorizontalPager(
                    state = pager, modifier = Modifier.fillMaxSize(), pageSpacing = 12.dp,
                    userScrollEnabled = pz <= 1.05f && !ui.drawing, beyondViewportPageCount = 1
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

        // ---------------- bilah atas ----------------
        AnimatedVisibility(
            bars || findOn, Modifier.align(Alignment.TopCenter),
            enter = fadeIn(spring(stiffness = 600f)) + expandVertically(spring(.8f, 500f)),
            exit = fadeOut(spring(stiffness = 700f)) + shrinkVertically(spring(1f, 600f))
        ) {
            Column(Modifier.fillMaxWidth().background(pal.s.copy(alpha = .96f)).padding(top = sat)) {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn("back", pal.os, { if (findOn) findOn = false else vm.nav.pop() }, size = 46.dp)
                    T(note.t.ifBlank { "PDF" }, 18f, 600, pal.os, Modifier.weight(1f).padding(horizontal = 6.dp), maxLines = 1)
                    IconBtn(
                        "search", if (hasText) pal.os else pal.os.copy(alpha = .32f),
                        { if (hasText) findOn = !findOn else vm.show("This PDF is a scan, so there's no text to search") },
                        size = 44.dp, bg = if (findOn) pal.pc else Color.Transparent
                    )
                    IconBtn("pen", if (ui.inkOn) pal.op else pal.os, { ui.inkOn = !ui.inkOn }, size = 44.dp, bg = if (ui.inkOn) pal.p else Color.Transparent)
                    IconBtn("more", pal.os, { vm.nav.push(Scr.NoteMenu(id)) }, size = 44.dp)
                }
                AnimatedVisibility(findOn) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.weight(1f).height(46.dp).clip(CircleShape).background(pal.c3).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                modifier = Modifier.weight(1f).focusRequester(fr),
                                textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 16.sp, fontWeight = FontWeight(500)),
                                cursorBrush = SolidColor(pal.p),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { step(1) }),
                                decorationBox = { inner -> Box { if (query.isEmpty()) T("Find in PDF", 16f, 500, pal.osv); inner() } }
                            )
                            if (searching) LoadingBlob(Modifier.size(24.dp))
                        }
                        IconBtn("aup", pal.os, { step(-1) }, size = 44.dp, enabled = ui.hits.isNotEmpty())
                        IconBtn("adn", pal.os, { step(1) }, size = 44.dp, enabled = ui.hits.isNotEmpty())
                        IconBtn("rows", if (listOn) pal.op else pal.os, { listOn = !listOn }, size = 44.dp, enabled = ui.hits.isNotEmpty(), bg = if (listOn) pal.p else Color.Transparent)
                    }
                    if (query.isNotBlank()) {
                        val h = ui.hits.getOrNull(ui.cur)
                        Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 10.dp)) {
                            T(
                                if (h != null) "Result ${ui.cur + 1} of ${ui.hits.size}  ·  now on page ${h.page + 1}" + (if (searching) "  ·  searching…" else "")
                                else if (searching) "Searching…" else "No results",
                                13f, 700, pal.p
                            )
                        }
                    }
                    AnimatedVisibility(listOn && ui.hits.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(bottom = 8.dp)) {
                            itemsIndexed(ui.hits) { i, h ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { jump(i); listOn = false; kb?.hide() }
                                        .background(if (i == ui.cur) pal.pc else Color.Transparent)
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(Modifier.clip(CircleShape).background(pal.c4).padding(horizontal = 10.dp, vertical = 4.dp)) { T("p.${h.page + 1}", 13f, 800, pal.os) }
                                    BasicText(
                                        buildAnnotatedString {
                                            append(h.snip.substring(0, h.ms))
                                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, background = Color(0xFFFFD54F).copy(alpha = .55f), color = Color.Black)) { append(h.snip.substring(h.ms, h.me)) }
                                            append(h.snip.substring(h.me))
                                        },
                                        style = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 14.sp), maxLines = 2,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- penunjuk halaman ----------------
        AnimatedVisibility(
            bars && !ui.inkOn, Modifier.align(Alignment.BottomCenter).padding(bottom = sab + 16.dp),
            enter = fadeIn() , exit = fadeOut()
        ) {
            Box(Modifier.clip(CircleShape).background(pal.bar2().copy(alpha = .94f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                T("Page ${page + 1} of $count", 14f, 700, pal.os)
            }
        }

        // ---------------- toolbar coret ----------------
        AnimatedVisibility(
            ui.inkOn, Modifier.align(Alignment.BottomCenter).padding(bottom = sab + 14.dp),
            enter = fadeIn(spring(stiffness = 600f)) + expandVertically(spring(.75f, 480f)),
            exit = fadeOut(spring(stiffness = 700f)) + shrinkVertically(spring(1f, 600f))
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(30.dp)).background(pal.bar2()).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (t in 0..2) {
                        val on = ui.tool == t
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(if (on) pal.p else Color.Transparent)
                                .clickable {
                                    saveTool()
                                    ui.tool = t
                                    ui.color = vm.pr.ic.getOrElse(t) { 0 }
                                    ui.wDp = vm.pr.iw.getOrElse(t) { if (t == 0) 3f else if (t == 1) 16f else 18f }
                                },
                            Alignment.Center
                        ) { InkToolIcon(t, if (on) pal.op else pal.os) }
                    }
                    Spacer(Modifier.width(6.dp))
                    IconBtn("undo", if (ui.ink.canUndo) pal.os else pal.os.copy(alpha = .3f), { ui.ink.undo() }, size = 44.dp, enabled = ui.ink.canUndo)
                    IconBtn("redo", if (ui.ink.canRedo) pal.os else pal.os.copy(alpha = .3f), { ui.ink.redo() }, size = 44.dp, enabled = ui.ink.canRedo)
                    IconBtn("ok", pal.op, { saveTool(); ui.inkOn = false }, size = 44.dp, bg = pal.p)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (ui.tool < 2) {
                        for (i in 0..4) {
                            val col = inkColor(pal, i, ui.tool == 1)
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).clickable { ui.color = i; saveTool() }
                                    .then(if (ui.color == i) Modifier.border(2.5.dp, pal.p, CircleShape) else Modifier),
                                Alignment.Center
                            ) { Box(Modifier.size(22.dp).clip(CircleShape).background(col).border(1.dp, pal.ol, CircleShape)) }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    val sizes = when (ui.tool) { 0 -> listOf(2f, 3f, 5f, 8f); 1 -> listOf(10f, 16f, 24f); else -> listOf(12f, 18f, 30f) }
                    for (s in sizes) {
                        val on = abs(ui.wDp - s) < .5f
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(if (on) pal.pc else Color.Transparent).clickable { ui.wDp = s; saveTool() },
                            Alignment.Center
                        ) {
                            val d = (4f + s * .55f).coerceAtMost(22f)
                            Box(Modifier.size(d.dp).clip(CircleShape).background(if (on) pal.opc else pal.os))
                        }
                    }
                }
            }
        }
    }
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

    PageScaffold("PDF options", d?.t?.ifEmpty { null } ?: "PDF", { nav.pop() }) {
        SGroup("Reading", 0) {
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
            SCard(POS_BOT) {
                T("Page color", 16f, 500, pal.os, Modifier.padding(bottom = 4.dp))
                T("Dark turns white pages to true black (AMOLED)", 14f, 400, pal.osv, Modifier.padding(bottom = 12.dp), lh = 1.3f)
                SegPill(3, d?.pdk ?: 0, { v -> vm.upd(id) { it.copy(pdk = v) } }) { i, fg ->
                    T(listOf("Auto", "Dark", "Light")[i], 15f, 700, fg)
                }
            }
        }
        SGroup("This PDF", 60) {
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
        SGroup("Share and save", 120) {
            MRow(POS_TOP, "up", "Share PDF", "Send it with WhatsApp, Files, and more") { if (file != null) act.sharePdf(fname, file) }
            MRow(POS_BOT, "dl", "Save a copy", "Pick where to save the PDF") { if (file != null) act.savePdf(fname, file) }
        }
        SGroup("Manage", 180) {
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
        var text by remember { mutableStateOf(d?.t ?: "") }
        val done = {
            val t = text.trim()
            if (t.isNotEmpty()) vm.upd(id) { it.copy(t = t) }
            renaming = false
        }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = pal.c3,
            title = { T("Rename PDF", 22f, 900, pal.os) },
            text = {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.c4).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(120) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 18.sp, fontWeight = FontWeight(600)),
                        cursorBrush = SolidColor(pal.p),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { done() })
                    )
                }
            },
            confirmButton = { TextButton(onClick = { done() }) { T("Save", 16f, 900, pal.p) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { T("Cancel", 16f, 700, pal.osv) } }
        )
    }
}

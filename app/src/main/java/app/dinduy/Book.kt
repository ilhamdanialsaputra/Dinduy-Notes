package app.dinduy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

// =====================================================================================
//  Buku 3D untuk item PDF di beranda. Semua gerak dikendalikan SATU progres: vm.openP (0 = tertutup di kartu, 1 = layar baca).
//  Membuka dan menutup memakai fungsi yang sama, jadi menutup otomatis kebalikan persis dari membuka (termasuk back gesture).
// =====================================================================================

/** Semua angka animasi buku ada di sini. */
object BookSpec {
    const val OPEN_MS = 820               // durasi buka total (ms): sampul mengayun ~540 ms lalu zoom ~280 ms
    const val CLOSE_MS = 700              // durasi tutup total (ms): sampul menutup ~460 ms, zoom ~240 ms
    // Sudut rebah sampul = 180° + asin(PAGES + BOARD) ≈ 191.7°. Sampul TIDAK melayang di bidang halaman (dulu 176°): ia turun mengikuti
    // ketebalan buku sampai ujung bebasnya menyentuh bidang papan belakang (bidang "meja"). Karena itu sampul yang terbuka miring
    // landai menjauh dari kamera, perspektifnya sama dengan buku tertutup, dan ketebalan blok halaman tetap terbaca.
    // Kalau PAGES / BOARD diubah, hitung ulang: 180 + asin(PAGES + BOARD) dalam derajat.
    const val COVER_DEG = 191.7f
    const val COVER_SIGN = -1f            // (tidak dipakai lagi oleh mesin 3D; dibiarkan supaya berkas lain tetap terkompilasi)
    const val COVER_END = .66f            // fase 1 (progres 0..COVER_END): sampul mengayun dan mendarat, buku masih di ukuran kartu, KAMERA DIAM
    const val ZOOM_FROM = .66f            // fase 2 (ZOOM_FROM..1): kamera mendekat ke halaman. Mulai tepat setelah sampul diam (tanpa tumpang tindih)
    const val SWING_IMPACT = .80f         // bagian fase 1 (0..1) saat sampul menyentuh bidang papan belakang; sisanya pantulan kecil lalu diam
    const val LENS_DONE = .80f            // bagian fase zoom (0..1) saat mata kamera sudah di tengah (tepi buku sudah di luar layar / tertutup latar)
    const val POSE_DONE = .365f            // pose miring buku di kartu selesai diluruskan pada progres ini (harus < ZOOM_FROM)
    const val REST_YAW = 0f               // pose buku diam di kartu: LURUS (tidak diputar). Ketebalan terlihat lewat lensa (REST_LENS_*), bukan kemiringan
    const val REST_PITCH = 0f             // ... lurus juga ke atas-bawah
    const val REST_SCALE = .94f           // buku sedikit lebih kecil dari kotak kartu supaya ada ruang untuk gerak bobot saat disentuh
    const val PEEK_YAW = 0f               // saat disentuh buku tidak lagi condong (tetap lurus); yang bereaksi: mengecil + lensa merapat
    const val PEEK_PITCH = 0f
    const val REST_LENS_X = 1.25f         // mata kamera bergeser ke kiri sejauh ini (x lebar buku): punggung terlihat, muka depan tetap lurus
    const val REST_LENS_Y = 1.1f          // ... dan ke atas: tepi atas (halaman bersusun) terlihat
    const val PEEK_LENS = -.45f           // saat disentuh lensa merapat sebesar ini (buku terasa ditekan masuk, tanpa miring)
    const val ROCK_LENS = 0f              // (tidak dipakai lagi: kamera tidak boleh bergoyang selama sampul berayun)
    const val PEEK_SCALE = -.035f         // buku sedikit menekan ke bawah (mengecil) saat disentuh, seperti benda berat ditekan
    const val SWING_BOUNCE = .022f        // pantulan papan sampul saat membentur bidang meja: terangkat sedikit (ke atas, bukan menembus meja), jatuh lagi, diam
    const val ROCK_YAW = 0f               // (tidak dipakai lagi: buku tidak diputar, supaya bidang halaman selalu pas dengan kotaknya)
    const val ROCK_SCALE = .012f          // buku sedikit tertekan (mengecil) tepat saat sampul membentur, lalu pulih
    const val BOARD = .042f               // tebal papan sampul (x lebar sampul)
    const val PAGES = .16f                // tebal blok halaman (x lebar sampul)
    const val PAGE_DONE = .8f             // dalam fase zoom: halaman isi sudah di posisi akhir layar baca
    const val READER_FROM = .85f          // dalam fase zoom: layar baca (bilah, halaman lain) mulai muncul
    const val CAMERA = 2.8f               // jarak kamera 3D = CAMERA x lebar sampul (makin besar = perspektif makin lembut)
    const val ASPECT = .70f               // lebar / tinggi sampul buku
    const val BACK_DRAG = .35f            // progres turun sampai segini selama back gesture ditarik penuh
    const val TXT_CHANGE = "Ganti sampul"
    const val TXT_RESTORE = "Kembalikan sampul asli"
    val EASE = androidx.compose.animation.core.LinearEasing   // easing per fase ada di fungsi smoothstep di bawah
    val openSpec: AnimationSpec<Float> = tween(OPEN_MS, easing = EASE)
    val closeSpec: AnimationSpec<Float> = tween(CLOSE_MS, easing = EASE)
    val backCancelSpec: AnimationSpec<Float> = spring(1f, 500f)
    val peekSpec: AnimationSpec<Float> = spring(.42f, 300f)   // pegas agak longgar: ada pantulan kecil, terasa berbobot
}

private fun sstep(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
/** 0 = pose miring di kartu, 1 = menghadap lurus (selesai sebelum zoom mulai). */
fun bookPoseFrac(t: Float): Float = sstep(t / BookSpec.POSE_DONE)
fun bookCoverFrac(t: Float): Float = sstep(t / BookSpec.COVER_END)
/**
 * Lensa perspektif (ketebalan terlihat). PENUH selama sampul mengayun dan mendarat: kamera diam, jadi punggung, tepi atas, dan tebal papan
 * konsisten dari kartu sampai sampul rebah (dulu memudar saat sampul masih berayun, sehingga sampul terbuka tampak datar total).
 * Baru dipusatkan selama zoom (kamera mendekat dan lurus ke halaman). Satu fungsi progres: buka dan tutup identik.
 */
fun bookLens(t: Float): Float = 1f - sstep(bookZoomFrac(t) / BookSpec.LENS_DONE)
/**
 * Sudut sampul (0..1 dari COVER_DEG). Mulai berat, mempercepat, lalu MEMBENTUR bidang papan belakang pada SWING_IMPACT dengan kecepatan masih
 * tersisa. Setelah itu papan terpental sedikit ke ATAS (sudut mengecil, tidak pernah melewati bidang meja), jatuh, pantul lebih kecil, diam.
 * Dipakai buka DAN tutup (progres sama), jadi menutup = kebalikan persis.
 */
fun bookSwing(t: Float): Float {
    val x = (t / BookSpec.COVER_END).coerceIn(0f, 1f)
    val imp = BookSpec.SWING_IMPACT
    if (x < imp) {
        val p = x / imp
        return .55f * sstep(p) + .45f * Math.pow(p.toDouble(), 1.7).toFloat()   // awal berat, akhir masih bergerak saat membentur
    }
    val u = ((x - imp) / (1f - imp)).coerceIn(0f, 1f)
    val hop = abs(sin(u * 2f * Math.PI.toFloat())) * Math.pow((1f - u).toDouble(), 1.5).toFloat()   // dua lompatan mengecil, tajam di titik benturan
    return (1f - BookSpec.SWING_BOUNCE * hop).coerceIn(0f, 1f)
}
/** Reaksi buku terhadap bobot sampul: 0 sampai sampul membentur, lalu denyut kecil selama pantulan, kembali 0 tepat saat zoom mulai. */
fun bookRock(t: Float): Float {
    val x = (t / BookSpec.COVER_END).coerceIn(0f, 1f)
    if (x <= BookSpec.SWING_IMPACT) return 0f
    val u = (x - BookSpec.SWING_IMPACT) / (1f - BookSpec.SWING_IMPACT)
    return sin(u * Math.PI.toFloat()) * (1f - u)
}
fun bookZoomFrac(t: Float): Float {
    val x = ((t - BookSpec.ZOOM_FROM) / (1f - BookSpec.ZOOM_FROM)).coerceIn(0f, 1f)
    return .6f * sstep(x) + .4f * (1f - (1f - x) * (1f - x) * (1f - x))   // lebih cepat di awal, mendarat lembut
}
/** Latar hitam + layar baca meluas ke seluruh layar hanya di ujung zoom (0 = bingkai = kertas, 1 = layar penuh). */
fun bookReveal(tt: Float): Float = sstep((tt - .78f) / .22f)

/** Ukuran layar / inset yang dibutuhkan untuk menghitung posisi akhir halaman. */
class BookGeo(val topPad: Float, val sab: Float, val dens: Float)

@Composable
fun rememberBookGeo(): BookGeo {
    val dens = LocalDensity.current
    val topPad = with(dens) { (satDp() + 64.dp).toPx() }
    val sab = with(dens) { sabDp().toPx() }
    return BookGeo(topPad, sab, dens.density)
}

/**
 * Kotak KERTAS pada progres zoom tt. Bingkai (clip wadah), kertas, dan engsel sampul 3D semuanya memakai kotak yang SAMA ini,
 * jadi tepinya selalu sejajar: yang terlihat adalah kertas yang membesar, bukan bingkai yang membesar lalu kertas menyusul.
 */
fun bookPageRect(vm: AppVM, w: Float, h: Float, tt: Float, g: BookGeo): Rect {
    val m = effMorph(vm) ?: return Rect(w * .12f, h * .4f, w * .88f, h * .6f)
    val fin = BookFx.live ?: bookFinalRect(w, h, g.topPad, g.sab, g.dens, PdfBus.mode.coerceAtLeast(0), BookFx.ratio)
    return Rect(lerp(m.l, fin.left, tt), lerp(m.t, fin.top, tt), lerp(m.r, fin.right, tt), lerp(m.b, fin.bottom, tt))
}
fun bookReaderAlpha(t: Float): Float = sstep((bookZoomFrac(t) - BookSpec.READER_FROM) / (1f - BookSpec.READER_FROM))

private val BOOK_SHAPE = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)

/** Keadaan buku yang sedang dibuka (hanya satu catatan terbuka sekaligus). */
object BookFx {
    var id: String? = null
    var page by mutableIntStateOf(0)       // halaman yang sedang dibaca (diperbarui layar baca)
    var ratio = 1.4142f                    // tinggi / lebar halaman tersebut
    var dark = false
    var peek = 0f                          // seberapa dalam kartu sedang ditekan saat diketuk (0..1), dilanjutkan mulus oleh lapisan animasi
    var tex: CoverTex? = null              // tekstur sampul yang dipakai kartu saat diketuk (cadangan bila tekstur baru belum jadi)
    var tilt = 0f                          // sudut miring sampul saat disentuh (derajat), dilanjutkan mulus oleh lapisan animasi
    var live: Rect? = null                 // posisi halaman di layar (koordinat jendela) saat menutup; null = posisi awal baca
    var snap: (() -> Rect?)? = null        // diisi layar baca
    @Volatile var pageTexSrc: android.graphics.Bitmap? = null   // bitmap halaman asal tekstur 3D (lihat BookTex.pageTex)
    @Volatile var pageTex: android.graphics.Bitmap? = null

    fun begin(id: String, page: Int, ratio: Float, dark: Boolean) {
        this.id = id; this.page = page; this.ratio = ratio; this.dark = dark; live = null; tilt = 0f; peek = 0f; tex = null
    }

    fun capture(noteId: String) {
        if (id == noteId) live = try { snap?.invoke() } catch (e: Throwable) { null }
    }

    /** Simpan halaman terakhir seketika saat menutup (tanpa menunggu penundaan 900 ms di layar baca). */
    fun persistPage(vm: AppVM, noteId: String) {
        val n = vm.note(noteId) ?: return
        if (id == noteId && n.pdf.isNotEmpty() && n.pg != page) vm.upd(noteId) { it.copy(pg = page) }
    }
}

class BookReady(val page: Int, val ratio: Float)

object BookArt {
    /**
     * Pastikan bitmap halaman terakhir ada di PdfCache (kunci sama dengan layar baca, jadi layar baca memakai bitmap yang SAMA,
     * tanpa render ulang). Dipanggil sebelum animasi mulai; bila gagal animasi tetap jalan memakai sampul sebagai isi.
     */
    suspend fun prepare(vm: AppVM, ctx: Context, d: Note, screenWpx: Int): BookReady = withContext(Dispatchers.IO) {
        if (d.cv.isNotEmpty()) try { Imgs.load(vm.imgDir, d.cv) } catch (e: Throwable) { }   // sampul sudah ada di cache sebelum animasi
        try { BookTex.ensure(d) } catch (e: Throwable) { }                                   // tekstur 3D sampul juga siap
        val sp = ctx.getSharedPreferences("dinduy", 0)
        if (PdfBus.mode < 0) PdfBus.mode = sp.getInt("pdfmode", 0)
        if (PdfBus.curl < 0) PdfBus.curl = sp.getInt("pdfcurl", 0)
        if (PdfBus.curlStyle < 0) PdfBus.curlStyle = sp.getInt("pdfcurlstyle", 0)
        var page = d.pg.coerceAtLeast(0)
        var ratio = 1.4142f
        try {
            val pfd = ParcelFileDescriptor.open(PdfFiles.file(ctx, d.pdf), ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val r = PdfRenderer(pfd)
                try {
                    page = page.coerceIn(0, r.pageCount - 1)
                    val pg = r.openPage(page)
                    try {
                        ratio = pg.height.toFloat() / pg.width.coerceAtLeast(1)
                        val have = PdfCache.best(d.pdf, page)
                        if (have == null || have.width < screenWpx * .8f) {
                            val bk = bucket(screenWpx)
                            val h = (bk * ratio).roundToInt().coerceIn(1, 6000)
                            val bm = Bitmap.createBitmap(bk, h, Bitmap.Config.ARGB_8888)
                            bm.eraseColor(android.graphics.Color.WHITE)
                            pg.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PdfCache.put(d.pdf + ":" + page + "@" + bk, bm)
                        }
                    } finally { pg.close() }
                } finally { r.close() }
            } finally { pfd.close() }
        } catch (e: Throwable) { }
        BookReady(page, ratio)
    }
}

/** Posisi halaman di layar baca pada kondisi awal (persis rumus PdfReader: mode gulir = menempel di bawah bilah atas, mode halaman = di tengah). */
fun bookFinalRect(vw: Float, vh: Float, topPad: Float, sab: Float, dens: Float, mode: Int, ratio: Float): Rect {
    if (mode == 0) return Rect(0f, topPad, vw, topPad + vw * ratio)
    val availH = vh - topPad - sab - 12f * dens
    var w = min(vw, availH / ratio)
    if (PdfBus.curl == 1 && PdfBus.curlStyle == 1) w *= min(1f, 1.8f / ratio)
    val h = w * ratio
    val x = (vw - w) / 2f
    val y = topPad + (availH - h) / 2f
    return Rect(x, y, x + w, y + h)
}

/** Wadah morph pada progres tt (rumus sama dengan NoteLayer untuk kartu). */
fun bookRect(vm: AppVM, w: Float, h: Float, tt: Float): Rect {
    val m = effMorph(vm) ?: return Rect(w * .12f, h * .4f, w * .88f, h * .6f)
    return Rect(lerp(m.l, 0f, tt), lerp(m.t, 0f, tt), lerp(m.r, w, tt), lerp(m.b, h, tt))
}

// ---------------------------------------------------------------- tampilan sampul

/** Muka sampul buku: dipakai kartu di beranda DAN sampul yang mengayun, jadi keduanya identik. */
@Composable
fun BookCoverFace(vm: AppVM, d: Note, modifier: Modifier = Modifier) {
    Box(modifier.clip(BOOK_SHAPE).background(Color(0xFFE9E4DA))) {
        val bmp = Imgs.peek(d.cv)
        if (bmp == null) CoverImage(vm, d, Modifier.fillMaxSize())
        else Canvas(Modifier.fillMaxSize()) {
            // gambar sendiri dari bitmap yang sudah di cache (tanpa pemuatan ulang saat ukuran berubah) -> tidak ada kedip
            val iw = bmp.width.toFloat(); val ih = bmp.height.toFloat()
            val s = maxOf(size.width / iw, size.height / ih) * d.cz.coerceAtLeast(1f)
            val vw = size.width / s; val vh = size.height / s
            drawImage(
                bmp, IntOffset(((iw - vw) * d.cx.coerceIn(0f, 1f)).roundToInt(), ((ih - vh) * d.cy.coerceIn(0f, 1f)).roundToInt()),
                IntSize(vw.roundToInt().coerceAtLeast(1), vh.roundToInt().coerceAtLeast(1)),
                IntOffset.Zero, IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // alur engsel di kiri
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(.40f), Color.Black.copy(.10f), Color.Transparent), 0f, w * .09f), size = Size(w * .09f, h))
            drawRect(Color.White.copy(.30f), Offset(w * .095f, 0f), Size(maxOf(1f, w * .006f), h))
            drawRect(Color.Black.copy(.18f), Offset(w * .088f, 0f), Size(maxOf(1f, w * .005f), h))
            // kilau lembut
            drawRect(Brush.linearGradient(listOf(Color.White.copy(.18f), Color.Transparent, Color.Transparent), Offset(w * .1f, 0f), Offset(w * .95f, h * .55f)))
            // tepi bawah / kanan sedikit gelap supaya terasa tebal
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.20f)), h * .7f, h))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(.14f)), w * .93f, w), Offset(w * .93f, 0f), Size(w * .07f, h))
        }
    }
}

// ---------------------------------------------------------------- kartu buku di beranda

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    d: Note, selected: Boolean, cols: Int, modifier: Modifier = Modifier, needPrep: Boolean,
    onLong: (() -> Unit)?, track: ((Morph) -> Unit)?, onClick: (Morph) -> Unit
) {
    val vm = LocalVM.current ?: return
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val screenW = (LocalConfiguration.current.screenWidthDp * dens).roundToInt()
    val pdark = when (d.pdk) { 1 -> true; 2 -> false; else -> pal.dark }
    var busy by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val peek by animateFloatAsState(if (pressed) 1f else 0f, BookSpec.peekSpec, label = "bkpeek")
    val selA by animateFloatAsState(if (selected) 1f else 0f, spring(.6f, 400f), label = "bksel")
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    fun morphNow(): Morph {
        val c = coords[0]
        val rc = if (c != null && c.isAttached) c.boundsInWindow() else Rect.Zero
        return Morph(
            rc.left, rc.top, rc.right, rc.bottom, 8f * dens, Color.Black,
            floatArrayOf(3f * dens, 8f * dens, 8f * dens, 3f * dens), null, card = true, book = true
        )
    }

    val total = remember(d.x) { Regex("\\d+").find(d.x)?.value?.toIntOrNull() ?: 0 }
    // tekstur sampul 3D dibuat di latar; sebelum jadi, kartu memakai sampul datar (tanpa kedip)
    val coverKey = "${d.cv}|${d.cx}|${d.cy}|${d.cz}"
    var texVer by remember { mutableIntStateOf(0) }
    LaunchedEffect(coverKey) {
        if (BookTex.peek(d) == null) {
            withContext(Dispatchers.IO) {
                if (d.cv.isNotEmpty()) try { Imgs.load(vm.imgDir, d.cv) } catch (e: Throwable) { }
                try { BookTex.ensure(d) } catch (e: Throwable) { }
            }
            texVer++
        }
    }
    // tekstur terakhir yang valid dipertahankan selama tekstur baru dibuat (ganti sampul): tidak ada kedip flat <-> 3D
    val texHold = remember { arrayOfNulls<CoverTex>(1) }
    val cur = remember(texVer, coverKey) { BookTex.peek(d) }
    if (cur != null) texHold[0] = cur
    val ct = cur ?: texHold[0]
    Box(
        modifier.fillMaxWidth().combinedClickable(
            interactionSource = src, indication = null,
            onClick = {
                if (!needPrep) onClick(morphNow())
                else if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            val r = BookArt.prepare(vm, ctx, d, screenW)
                            // halaman diperkecil sekali di latar supaya frame pertama animasi tidak membuatnya di thread gambar
                            val cw = coords[0]?.let { it.size.width } ?: 0
                            if (cw > 0) withContext(Dispatchers.IO) {
                                try { PdfCache.best(d.pdf, r.page)?.let { BookTex.pageTex(it, cw.toFloat()) } } catch (e: Throwable) { }
                            }
                            BookFx.begin(d.id, r.page, r.ratio, pdark)
                            BookFx.tilt = BookSpec.PEEK_YAW * peek
                            BookFx.peek = peek
                            BookFx.tex = BookTex.peek(d) ?: ct
                            onClick(morphNow())
                        } finally { busy = false }
                    }
                }
            },
            onLongClick = if (onLong != null) ({ haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLong() }) else null
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = if (cols == 1) 200.dp else 480.dp).fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 5.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(BookSpec.ASPECT)
                        .onGloballyPositioned { coords[0] = it; track?.invoke(morphNow()) }
                ) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            // selama lapisan animasi memegang buku (buka / menutup), kartu tidak menggambar bukunya sendiri
                            val own = vm.morph?.book == true && vm.openId == d.id &&
                                (vm.openP.value > .01f || vm.nav.stack.any { it is Scr.Note && it.id == d.id })
                            alpha = if (own) 0f else 1f
                        }
                    ) {
                        if (ct == null) BookCoverFace(vm, d, Modifier.fillMaxSize())
                        else Canvas(Modifier.fillMaxSize()) {
                            // buku 3D sungguhan: lurus; saat disentuh ditekan (mengecil, lensa merapat), lalu memantul balik (pegas longgar)
                            val yaw = BookSpec.REST_YAW + BookSpec.PEEK_YAW * peek
                            val pitch = BookSpec.REST_PITCH + BookSpec.PEEK_PITCH * peek
                            val k = BookSpec.REST_SCALE + BookSpec.PEEK_SCALE * peek
                            val lens = 1f + BookSpec.PEEK_LENS * peek
                            drawIntoCanvas {
                                Book3D.draw(
                                    it.nativeCanvas, 0f, 0f, size.width, size.height,
                                    yaw, pitch, k, 0f, ct, null, false, false,
                                    BookSpec.REST_LENS_X * lens, BookSpec.REST_LENS_Y * lens
                                )
                            }
                        }
                        // penanda terpilih: cincin yang mengikuti bidang depan buku (bukan bingkai datar bentuk lama)
                        if (selA > .01f) Canvas(Modifier.fillMaxSize()) {
                            val inset = (1f - BookSpec.REST_SCALE) / 2f
                            val sw = 2.dp.toPx()
                            drawRoundRect(
                                pal.p.copy(alpha = selA), Offset(size.width * inset, size.height * inset),
                                Size(size.width * (1f - 2f * inset), size.height * (1f - 2f * inset)),
                                CornerRadius(6.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            T(d.t.trim().ifBlank { "PDF" }, 14f, 600, pal.os, Modifier.padding(horizontal = 2.dp), lh = 1.25f, maxLines = 2)
            T(if (total > 0) "p. ${d.pg + 1} / $total" else d.x, 12f, 500, pal.osv, Modifier.padding(horizontal = 2.dp, vertical = 2.dp), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- lapisan animasi (dipasang di NoteLayer)

/** Halaman isi: tepat di bawah sampul, di dalam wadah morph (ikut terpotong wadah). Morph: potongan rasio sampul -> halaman utuh. */
@Composable
fun BookPageLayer(vm: AppVM) {
    val id = vm.openId ?: return
    val d = vm.note(id) ?: return
    val geo = rememberBookGeo()
    remember(id) { if (BookFx.id != id) BookFx.begin(id, d.pg, 1.4142f, false); 0 }
    Canvas(Modifier.fillMaxSize()) {
        val tRaw = vm.openP.value.coerceIn(0f, 1f)
        val tt = bookZoomFrac(tRaw)
        vm.morphTick
        if (tt <= .0005f) return@Canvas   // fase sampul terbuka: halaman digambar mesin 3D di BookCoverLayer (ikut miring bersama buku)
        val c = bookPageRect(vm, size.width, size.height, tt, geo)   // kotak kertas = kotak bingkai (lihat NoteLayer)
        val bmp = PdfCache.best(d.pdf, BookFx.page)?.asImageBitmap()
        if (bmp == null) { drawRect(Color.White, Offset(c.left, c.top), Size(c.width, c.height)); return@Canvas }   // tak pernah hitam
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        var cw = bw
        var ch = cw / BookSpec.ASPECT
        if (ch > bh) { ch = bh; cw = ch * BookSpec.ASPECT }
        // potongan rasio sampul -> halaman utuh dengan progres YANG SAMA dengan kotaknya (tidak lebih cepat), jadi isi tidak bergeser terhadap tepi
        val sx = lerp((bw - cw) / 2f, 0f, tt)
        val sw = lerp(cw, bw, tt); val sh = lerp(ch, bh, tt)
        drawImage(
            bmp, IntOffset(sx.roundToInt(), 0), IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)),
            IntOffset(c.left.roundToInt(), c.top.roundToInt()), IntSize(c.width.roundToInt().coerceAtLeast(1), c.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium, colorFilter = if (BookFx.dark) INVERT else null
        )
    }
}

/** Pengganti layar baca selama PDF belum siap: halaman yang SAMA di posisi yang SAMA, jadi tidak ada kosong / kedip. */
@Composable
fun BookPlaceholder(note: Note) {
    val dens = LocalDensity.current.density
    val topPad = with(LocalDensity.current) { (satDp() + 64.dp).toPx() }
    val sab = with(LocalDensity.current) { sabDp().toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val bmp = PdfCache.best(note.pdf, BookFx.page)?.asImageBitmap() ?: return@Canvas
        val r = BookFx.live ?: bookFinalRect(size.width, size.height, topPad, sab, dens, PdfBus.mode.coerceAtLeast(0), BookFx.ratio)
        drawImage(
            bmp, IntOffset.Zero, IntSize(bmp.width, bmp.height),
            IntOffset(r.left.roundToInt(), r.top.roundToInt()), IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium, colorFilter = if (BookFx.dark) INVERT else null
        )
    }
}

/**
 * Adegan buku 3D selama fase sampul terbuka / tertutup: blok halaman + halaman terakhir dibuka (tepat di balik sampul),
 * papan sampul tebal yang berputar kaku di engsel kiri lalu rebah ke kiri menimpa isi beranda di sampingnya, dan bayangannya.
 * Digambar di atas wadah morph. Saat zoom (wadah membesar) halaman diambil alih BookPageLayer dan sampul ikut tumbuh lalu keluar layar.
 * Kartu lain TIDAK bergerak selama animasi buku (lihat makeRoom).
 */
@Composable
fun BookCoverLayer(vm: AppVM) {
    val id = vm.openId ?: return
    val d = vm.note(id) ?: return
    val geo = rememberBookGeo()
    // tekstur sampul dipegang lapisan ini dan TIDAK PERNAH dibuat di fase gambar. Sampul diganti -> kuncinya berubah -> tekstur baru
    // dicari / dibuat di latar; selama itu tekstur sebelumnya (atau polos) tetap dipakai, jadi tidak ada patah frame
    val coverKey = "${d.cv}|${d.cx}|${d.cy}|${d.cz}"
    var texVer by remember { mutableIntStateOf(0) }
    LaunchedEffect(coverKey) {
        if (BookTex.peek(d) == null) {
            withContext(Dispatchers.IO) {
                if (d.cv.isNotEmpty()) try { Imgs.load(vm.imgDir, d.cv) } catch (e: Throwable) { }
                try { BookTex.ensure(d) } catch (e: Throwable) { }
            }
            texVer++
        }
    }
    val ct = remember(texVer, coverKey) { BookTex.peek(d) } ?: BookFx.tex ?: BookTex.plain()
    Canvas(Modifier.fillMaxSize()) {
        val tRaw = vm.openP.value.coerceIn(0f, 1f)
        val tt = bookZoomFrac(tRaw)
        vm.morphTick
        val r = bookPageRect(vm, size.width, size.height, tt, geo)   // engsel menempel di tepi kiri kertas yang membesar
        val pe = bookPoseFrac(tRaw)
        val rock = bookRock(tRaw)                                      // denyut tekan saat sampul membentur meja (0 sebelum benturan dan saat zoom)
        val pk = if (morphClosing(vm)) 0f else BookFx.peek              // tekanan jari dilanjutkan saat membuka; saat menutup kartu sendiri yang menerima
        val yaw = (BookSpec.REST_YAW + BookFx.tilt) * (1f - pe)        // tidak ada goyangan putar: bidang halaman (z = 0) selalu persis di kotaknya
        val pitch = BookSpec.REST_PITCH * (1f - pe)
        val k = lerp(BookSpec.REST_SCALE + BookSpec.PEEK_SCALE * pk, 1f, pe) - BookSpec.ROCK_SCALE * rock
        // kamera DIAM selama sampul mengayun (sudut pandang kartu bertahan sampai sampul rebah), baru bergeser ke tengah saat zoom
        val lensF = bookLens(tRaw) * (1f + BookSpec.PEEK_LENS * pk * (1f - pe))
        val lx = BookSpec.REST_LENS_X * lensF
        val ly = BookSpec.REST_LENS_Y * lensF
        val deg = (BookSpec.COVER_DEG * bookSwing(tRaw)).coerceAtMost(200f)
        // halaman terakhir dibuka: hanya sebelum zoom (setelah itu BookPageLayer yang menggambarnya)
        val pageOn = tt <= .0005f
        val page = if (pageOn) PdfCache.best(d.pdf, BookFx.page)?.let { BookTex.pageTex(it, r.width) } else null
        // sampul tetap menempel di engsel kertas selama zoom; baru memudar bersama latar hitam yang meluas di ujung zoom
        // (mode halaman: engsel tidak di tepi layar, jadi sampul tidak boleh tertinggal di atas layar baca)
        val fadeA = 1f - bookReveal(tt)
        if (fadeA <= .01f) return@Canvas
        drawIntoCanvas {
            val cnv = it.nativeCanvas
            val sv = if (fadeA < .999f) cnv.saveLayerAlpha(0f, 0f, size.width, size.height, (fadeA * 255f).roundToInt()) else -1
            Book3D.draw(
                cnv, r.left, r.top, r.right, r.bottom,
                yaw, pitch, k, deg, ct, page, pageOn, BookFx.dark, lx, ly
            )
            if (sv >= 0) cnv.restoreToCount(sv)
        }
    }
}

// ---------------------------------------------------------------- ganti sampul

/** Sampul asli = halaman 1 (cy = 0, dibuat makePdfCover). Sampul kustom dipangkas ke rasio buku (cy = .5). */
fun Note.hasCustomBookCover(): Boolean = pdf.isNotEmpty() && cv.isNotEmpty() && cy != 0f

/**
 * Gambar galeri (sudah disalin ke penyimpanan app oleh importImage) -> pangkas tengah ke rasio sampul buku, perkecil maks 900 px,
 * simpan sebagai berkas baru di img/ (ikut cadangan seperti sampul lain), lalu pasang ke catatan. Berkas sementara dihapus.
 */
suspend fun AppVM.setBookCover(id: String, rawId: String): Boolean = withContext(Dispatchers.IO) {
    val f = File(imgDir, "$rawId.jpg")
    try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1600) s *= 2
        val bm = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return@withContext false
        val a = BookSpec.ASPECT
        var cw = bm.width
        var ch = (cw / a).toInt()
        if (ch > bm.height) { ch = bm.height; cw = (ch * a).toInt() }
        val crop = Bitmap.createBitmap(bm, (bm.width - cw) / 2, (bm.height - ch) / 2, cw.coerceAtLeast(1), ch.coerceAtLeast(1))
        val outW = min(crop.width, 900)
        val out = Bitmap.createScaledBitmap(crop, outW, (outW / a).roundToInt().coerceAtLeast(1), true)
        val nid = uid()
        File(imgDir, "$nid.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        f.delete()
        Imgs.load(imgDir, nid)   // sudah ada di cache sebelum dipakai kartu
        // tekstur 3D sampul baru dibangun SEKARANG di latar, sebelum catatan diperbarui: kartu dan lapisan animasi langsung menemukannya
        // (tidak ada kartu flat, tidak ada pembuatan tekstur di fase gambar). Tekstur lama dibuang supaya tidak ada sisa usang.
        note(id)?.let { old ->
            BookTex.evict(old.cv)
            try { BookTex.ensure(old.copy(cv = nid, cx = .5f, cy = .5f, cz = 1f, cp = "")) } catch (e: Throwable) { }
        }
        withContext(Dispatchers.Main) { upd(id) { it.copy(cv = nid, cx = .5f, cy = .5f, cz = 1f, cp = "") } }
        true
    } catch (e: Throwable) { false }
}

/** Kembali ke sampul asli (halaman 1 PDF). */
suspend fun AppVM.restoreBookCover(id: String): Boolean = withContext(Dispatchers.IO) {
    val n = note(id) ?: return@withContext false
    val cv = makePdfCover(this@restoreBookCover, PdfFiles.file(getApplication<android.app.Application>(), n.pdf))
    if (cv.isEmpty()) return@withContext false
    Imgs.load(imgDir, cv)
    BookTex.evict(n.cv)   // bila id sampulnya sama, tekstur usang tidak boleh tersisa
    try { BookTex.ensure(n.copy(cv = cv, cx = .5f, cy = 0f, cz = 1f, cp = "")) } catch (e: Throwable) { }
    withContext(Dispatchers.Main) { upd(id) { it.copy(cv = cv, cx = .5f, cy = 0f, cz = 1f, cp = "") } }
    true
}

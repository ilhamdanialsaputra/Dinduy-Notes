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
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================================================
//  Buku 3D untuk item PDF di beranda. Semua gerak dikendalikan SATU progres: vm.openP (0 = tertutup di kartu, 1 = layar baca).
//  Membuka dan menutup memakai fungsi yang sama, jadi menutup otomatis kebalikan persis dari membuka (termasuk back gesture).
// =====================================================================================

/** Semua angka animasi buku ada di sini. */
object BookSpec {
    const val OPEN_MS = 900               // durasi buka total (ms): sampul terbuka dulu, baru zoom
    const val CLOSE_MS = 760              // durasi tutup total (ms)
    const val COVER_DEG = 176f            // sudut ayun sampul: rebah hampir datar di sisi kiri buku
    const val COVER_SIGN = -1f            // (tidak dipakai lagi oleh mesin 3D; dibiarkan supaya berkas lain tetap terkompilasi)
    const val COVER_END = .56f            // fase 1 (progres 0..COVER_END): sampul terbuka, buku masih di ukuran kartu
    const val ZOOM_FROM = .50f            // fase 2 (ZOOM_FROM..1): buku membesar ke layar baca (sedikit tumpang tindih dengan akhir fase 1)
    const val POSE_DONE = .30f            // pose miring buku di kartu selesai diluruskan pada progres ini (harus < ZOOM_FROM)
    const val REST_YAW = 5f               // pose buku diam di kartu: hampir lurus, hanya sedikit perspektif supaya tebal buku terasa
    const val REST_PITCH = -3f            // ... dan sedikit mengintip sisi atas
    const val REST_SCALE = .94f           // buku sedikit lebih kecil dari kotak kartu supaya ada ruang untuk gerak bobot saat disentuh
    const val PEEK_YAW = 9f               // tambahan putaran saat disentuh (buku "ditekan" dan condong)
    const val PEEK_PITCH = 4f             // tambahan condong ke depan saat disentuh
    const val PEEK_SCALE = -.035f         // buku sedikit menekan ke bawah (mengecil) saat disentuh, seperti benda berat ditekan
    const val SWING_BOUNCE = .022f        // pantulan kecil papan sampul saat mendarat di akhir ayunan (bobot papan)
    const val ROCK_YAW = 3.2f             // buku bergoyang sedikit mengimbangi bobot sampul yang berayun (derajat)
    const val ROCK_SCALE = .018f          // buku terangkat sedikit saat sampul diayun
    const val BOARD = .03f                // tebal papan sampul (x lebar sampul)
    const val PAGES = .10f                // tebal blok halaman (x lebar sampul)
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
/** Sudut sampul (0..1 dari COVER_DEG) dengan bobot: mulai berat, mempercepat, lalu mendarat dengan pantulan kecil. Dipakai buka DAN tutup (progres sama). */
fun bookSwing(t: Float): Float {
    val x = (t / BookSpec.COVER_END).coerceIn(0f, 1f)
    val e = x * x * x * (x * (x * 6f - 15f) + 10f)                       // smootherstep: awal berat, akhir lembut
    val land = if (x > .72f) kotlin.math.sin(((x - .72f) / .28f) * Math.PI.toFloat()) else 0f
    return (e + BookSpec.SWING_BOUNCE * land).coerceAtMost(1.0125f)  // lewat sedikit dari rebah lalu kembali
}
/** Goyangan buku mengimbangi ayunan sampul: 0 di awal / akhir, puncak di tengah. */
fun bookRock(t: Float): Float = kotlin.math.sin(bookCoverFrac(t) * Math.PI.toFloat())
fun bookZoomFrac(t: Float): Float = sstep((t - BookSpec.ZOOM_FROM) / (1f - BookSpec.ZOOM_FROM))
fun bookReaderAlpha(t: Float): Float = sstep((bookZoomFrac(t) - BookSpec.READER_FROM) / (1f - BookSpec.READER_FROM))

private val BOOK_SHAPE = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)

/** Keadaan buku yang sedang dibuka (hanya satu catatan terbuka sekaligus). */
object BookFx {
    var id: String? = null
    var page by mutableIntStateOf(0)       // halaman yang sedang dibaca (diperbarui layar baca)
    var ratio = 1.4142f                    // tinggi / lebar halaman tersebut
    var dark = false
    var tilt = 0f                          // sudut miring sampul saat disentuh (derajat), dilanjutkan mulus oleh lapisan animasi
    var live: Rect? = null                 // posisi halaman di layar (koordinat jendela) saat menutup; null = posisi awal baca
    var snap: (() -> Rect?)? = null        // diisi layar baca
    var pageTexSrc: android.graphics.Bitmap? = null   // bitmap halaman asal tekstur 3D (lihat BookTex.pageTex)
    var pageTex: android.graphics.Bitmap? = null

    fun begin(id: String, page: Int, ratio: Float, dark: Boolean) {
        this.id = id; this.page = page; this.ratio = ratio; this.dark = dark; live = null; tilt = 0f
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
    val ct = remember(texVer, coverKey) { BookTex.peek(d) }
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
                            BookFx.begin(d.id, r.page, r.ratio, pdark)
                            BookFx.tilt = BookSpec.PEEK_YAW * peek
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
                        .drawBehind {
                            // bayangan lembut di lantai di bawah buku; saat ditekan bayangan merapat + menggelap (buku menempel ke permukaan)
                            val w = size.width
                            val h = size.height
                            val pk = peek.coerceIn(0f, 1.3f)
                            for (i in 1..6) {
                                val e = i * 1.8.dp.toPx() * (1f - .35f * pk)
                                drawRoundRect(
                                    Color.Black.copy(alpha = .045f + .012f * pk), Offset(w * .07f - e * .2f + 2.dp.toPx(), h * .10f + e * .8f + 2.dp.toPx()),
                                    Size(w * .88f + e * .4f, h * .86f + e * .3f), CornerRadius(8.dp.toPx() + e)
                                )
                            }
                        }
                ) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            // selama lapisan animasi memegang buku (buka / menutup), kartu tidak menggambar bukunya sendiri
                            val own = vm.morph?.book == true && vm.openId == d.id &&
                                (vm.openP.value > .01f || vm.nav.stack.any { it is Scr.Note && it.id == d.id })
                            alpha = if (own) 0f else 1f
                        }.border(2.dp, pal.p.copy(alpha = selA), BOOK_SHAPE)
                    ) {
                        if (ct == null) BookCoverFace(vm, d, Modifier.fillMaxSize())
                        else Canvas(Modifier.fillMaxSize()) {
                            // buku 3D sungguhan: hampir lurus; saat disentuh ditekan (mengecil), condong, lalu memantul balik (pegas longgar)
                            val yaw = BookSpec.REST_YAW + BookSpec.PEEK_YAW * peek
                            val pitch = BookSpec.REST_PITCH + BookSpec.PEEK_PITCH * peek
                            val k = BookSpec.REST_SCALE + BookSpec.PEEK_SCALE * peek
                            drawIntoCanvas {
                                Book3D.draw(
                                    it.nativeCanvas, 0f, 0f, size.width, size.height,
                                    yaw, pitch, k, 0f, ct, null, false, false
                                )
                            }
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
    val dens = LocalDensity.current.density
    val topPad = with(LocalDensity.current) { (satDp() + 64.dp).toPx() }
    val sab = with(LocalDensity.current) { sabDp().toPx() }
    remember(id) { if (BookFx.id != id) BookFx.begin(id, d.pg, 1.4142f, false); 0 }
    Canvas(Modifier.fillMaxSize()) {
        val tRaw = vm.openP.value.coerceIn(0f, 1f)
        val tt = bookZoomFrac(tRaw)
        vm.morphTick
        if (tt <= .0005f) return@Canvas   // fase sampul terbuka: halaman digambar mesin 3D di BookCoverLayer (ikut miring bersama buku)
        val W = size.width
        val H = size.height
        val c = bookRect(vm, W, H, tt)
        val bmp = PdfCache.best(d.pdf, BookFx.page)?.asImageBitmap()
        if (bmp == null) { drawRect(Color.White, Offset(c.left, c.top), Size(c.width, c.height)); return@Canvas }   // tak pernah hitam
        val fin = BookFx.live ?: bookFinalRect(W, H, topPad, sab, dens, PdfBus.mode.coerceAtLeast(0), BookFx.ratio)
        val q = sstep(tt / BookSpec.PAGE_DONE)
        val dl = lerp(c.left, fin.left, q); val dt = lerp(c.top, fin.top, q)
        val dr = lerp(c.right, fin.right, q); val db = lerp(c.bottom, fin.bottom, q)
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        var cw = bw
        var ch = cw / BookSpec.ASPECT
        if (ch > bh) { ch = bh; cw = ch * BookSpec.ASPECT }
        val sx = lerp((bw - cw) / 2f, 0f, q); val sy = lerp(0f, 0f, q)
        val sw = lerp(cw, bw, q); val sh = lerp(ch, bh, q)
        drawImage(
            bmp, IntOffset(sx.roundToInt(), sy.roundToInt()), IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)),
            IntOffset(dl.roundToInt(), dt.roundToInt()), IntSize((dr - dl).roundToInt().coerceAtLeast(1), (db - dt).roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium, colorFilter = if (BookFx.dark) INVERT else null
        )
        // bayangan sampul yang jatuh di halaman dekat engsel
        val ang = bookCoverFrac(tRaw)
        val a = .30f * kotlin.math.sin(ang * Math.PI.toFloat() / 2f) * (1f - tt)
        if (a > .005f) {
            val sw2 = (dr - dl) * .28f
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(a), Color.Transparent), dl, dl + sw2), Offset(dl, dt), Size(sw2, db - dt))
        }
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
    Canvas(Modifier.fillMaxSize()) {
        val tRaw = vm.openP.value.coerceIn(0f, 1f)
        val tt = bookZoomFrac(tRaw)
        vm.morphTick
        val r = bookRect(vm, size.width, size.height, tt)
        val pe = bookPoseFrac(tRaw)
        val rock = bookRock(tRaw) * (1f - pe)                          // buku bergoyang mengimbangi bobot sampul (hilang saat sudah lurus)
        val yaw = (BookSpec.REST_YAW + BookFx.tilt) * (1f - pe) + BookSpec.ROCK_YAW * rock       // melanjutkan kemiringan dari kartu tanpa loncat
        val pitch = BookSpec.REST_PITCH * (1f - pe) - 1.5f * rock
        val k = lerp(BookSpec.REST_SCALE, 1f, pe) + BookSpec.ROCK_SCALE * rock
        val deg = (BookSpec.COVER_DEG * bookSwing(tRaw)).coerceAtMost(179f)
        val ct = BookTex.peek(d) ?: BookTex.ensure(d)
        // halaman terakhir dibuka: hanya sebelum zoom (setelah itu BookPageLayer yang menggambarnya)
        val pageOn = tt <= .0005f
        val page = if (pageOn) PdfCache.best(d.pdf, BookFx.page)?.let { BookTex.pageTex(it, r.width) } else null
        drawIntoCanvas {
            Book3D.draw(
                it.nativeCanvas, r.left, r.top, r.right, r.bottom,
                yaw, pitch, k, deg, ct, page, pageOn, BookFx.dark
            )
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
    withContext(Dispatchers.Main) { upd(id) { it.copy(cv = cv, cx = .5f, cy = 0f, cz = 1f, cp = "") } }
    true
}

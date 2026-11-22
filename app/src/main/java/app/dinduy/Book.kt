package app.dinduy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
    const val OPEN_MS = 540               // durasi buka total (ms) dari tertutup. Koreografi BERTUMPANG TINDIH: sampul mengayun (0..COVER_END), zoom mulai di tengah ayunan (ZOOM_FROM), kamera mendekat setelah sampul mendarat (DOLLY_FROM)
    const val CLOSE_MS = 400              // menutup sedikit lebih cepat daripada membuka (keluar = cepat, masuk = ada waktu menikmati)
    // Sudut rebah sampul = 180° + asin(PAGES + BOARD) ≈ 193.3° (PAGES .18 + BOARD .05). Sampul TIDAK melayang di bidang halaman (dulu 176°): ia turun mengikuti
    // ketebalan buku sampai ujung bebasnya menyentuh bidang papan belakang (bidang "meja"). Karena itu sampul yang terbuka miring
    // landai menjauh dari kamera, perspektifnya sama dengan buku tertutup, dan ketebalan blok halaman tetap terbaca.
    // Kalau PAGES / BOARD diubah, hitung ulang: 180 + asin(PAGES + BOARD) dalam derajat.
    const val COVER_DEG = 193.3f
    const val COVER_SIGN = -1f            // (tidak dipakai lagi oleh mesin 3D; dibiarkan supaya berkas lain tetap terkompilasi)
    const val COVER_END = .56f            // sampul mengayun dan mendarat pada progres ini
    const val ZOOM_FROM = .29f            // kertas mulai membesar menuju layar baca saat sampul masih di tengah ayunan (~125 derajat): satu gerak mengalir, bukan dua tahap bergantian
    const val DOLLY_END = .72f            // lensa + kamera sudah lurus di depan halaman pada progres ini: SELESAI sebelum kertas mendarat, supaya bagian 3D (sampul, punggung) tidak bergeser sendiri di belakang kertas
    const val DOLLY_FROM = .45f           // lensa memusat + kamera mendekat HANYA setelah sampul menyentuh meja (benda yang masih terangkat tidak boleh kena perspektif yang mengetat)
    const val PEEK_DEG = 0f               // saat jari menekan buku, sampul terangkat sedikit (mengintip). Ayunan buka melanjutkan dari sudut ini, tanpa lompatan
    const val SLIDE_1COL = .5f            // 1 kolom: buku bergeser ke kanan paling banyak setengah lebar buku supaya sampul yang rebah tetap di dalam layar
    const val SWING_IMPACT = .80f         // bagian fase 1 (0..1) saat sampul menyentuh bidang papan belakang; sisanya pantulan kecil lalu diam
    const val LENS_DONE = .80f            // bagian fase dolly (DOLLY_FROM..1) saat mata kamera sudah di tengah (tepi buku sudah di luar layar / tertutup latar)
    const val POSE_DONE = .26f            // pose miring buku di kartu selesai diluruskan pada progres ini (harus < ZOOM_FROM)
    const val REST_YAW = 0f               // pose buku diam di kartu: LURUS (tidak diputar). Ketebalan terlihat lewat lensa (REST_LENS_*), bukan kemiringan
    const val REST_PITCH = 0f             // ... lurus juga ke atas-bawah
    const val REST_SCALE = .94f           // buku sedikit lebih kecil dari kotak kartu supaya ada ruang untuk gerak bobot saat disentuh
    const val PEEK_YAW = 0f               // saat disentuh buku tidak lagi condong (tetap lurus); yang bereaksi: mengecil + lensa merapat
    const val PEEK_PITCH = 0f
    const val REST_LENS_X = 1.25f         // mata kamera bergeser ke kiri sejauh ini (x lebar buku): punggung terlihat, muka depan tetap lurus
    const val REST_LENS_Y = 1.1f          // ... dan ke atas: tepi atas (halaman bersusun) terlihat
    const val PEEK_LENS = 0f          // saat disentuh lensa merapat sebesar ini (buku terasa ditekan masuk, tanpa miring)
    const val ROCK_LENS = 0f              // (tidak dipakai lagi: kamera tidak boleh bergoyang selama sampul berayun)
    const val PEEK_SCALE = -.035f         // buku sedikit menekan ke bawah (mengecil) saat disentuh, seperti benda berat ditekan
    const val SWING_BOUNCE = .065f      // pantulan papan sampul saat membentur bidang meja: terangkat sedikit (ke atas, bukan menembus meja), jatuh lagi, diam
    const val ROCK_YAW = 0f               // (tidak dipakai lagi: buku tidak diputar, supaya bidang halaman selalu pas dengan kotaknya)
    const val ANTIC_SCALE = .02f          // antisipasi: buku menekan sedikit lebih dalam tepat sebelum sampul terangkat, lalu pulih (tanpa jeda tambahan: responsif di layar sentuh)
    const val LEAF_DEG = 24f              // sudut terangkat maksimum daun halaman yang terkibas saat sampul membuka / menutup
    const val ROCK_SCALE = .028f         // buku sedikit tertekan (mengecil) tepat saat sampul membentur, lalu pulih
    const val BOARD = .050f               // tebal papan sampul (x lebar sampul)
    const val PAGES = .18f                // tebal blok halaman (x lebar sampul)
    const val PAGE_DONE = .8f             // dalam fase zoom: halaman isi sudah di posisi akhir layar baca
    const val READER_FROM = .85f          // dalam fase zoom: layar baca (bilah, halaman lain) mulai muncul
    const val DIM_EARLY = .68f            // seberapa gelap seluruh layar di luar buku (beranda di balik sampul) pada DIM_FADE_FROM. Menghitam BERTAHAP sejak sampul mulai terbuka, jadi hitam layar baca nanti hanya menyelesaikan sisa (.32), bukan muncul tiba-tiba dari terang
    const val DIM_FADE_FROM = .75f        // progres saat kertas sudah ~95% layar penuh: dari sini hitam melebur ke penuh. Jangan dimajukan jauh: hitam tidak boleh bentrok dengan sampul yang masih terlihat di samping kertas
    const val DIM_FADE_TO = .90f          // hitam penuh tercapai di sini, SEBELUM komponen layar baca (UI_ALPHA_FROM) mulai pudar masuk. Hitam dibawa SEPENUHNYA oleh bookDim (PdfScreen tidak punya latar hitam sendiri saat animasi buku)
    const val READER_ALPHA_FROM = .78f    // wadah layar baca (halaman, bukan latar hitam) mulai tampil
    const val READER_ALPHA_TO = .86f      // wadah sudah penuh; sesudah ini yang tersisa hanya pudar komponen (UI_ALPHA) dan halaman lanjutan (TAIL_ALPHA)
    const val UI_ALPHA_FROM = .86f        // bilah atas (panah back, judul, cari, titik tiga) dan kapsul halaman mulai pudar masuk SETELAH hitam hampir penuh (bookDim ~98%), selesai di progres 1
    const val TAIL_ALPHA_FROM = .72f      // halaman lanjutan di mode gulir (halaman 2 dst, yang ikut terlihat di bawah halaman pertama) pudar masuk sambil naik pelan, supaya tidak muncul tiba-tiba
    const val TAIL_RISE_DP = 40f          // jarak naik halaman lanjutan (dp) selama pudar masuk
    const val ZOOM_CAMERA = 2.0f          // jarak kamera di ujung zoom: kamera MENDEKAT ke halaman (bukan sekadar gambar diperbesar), perspektif mengencang
    const val CAMERA = 2.8f               // jarak kamera 3D = CAMERA x lebar sampul (makin besar = perspektif makin lembut)
    const val ASPECT = .70f               // lebar / tinggi sampul buku bila rasio halaman belum diketahui (cadangan)
    const val MIN_ASPECT = .30f           // HANYA pengaman geometri (halaman rusak / ukuran 0): rasio asli halaman dipakai apa adanya di atas ini
    const val TOP_HINGE_ASPECT = 1.12f    // rasio >= ini (slide / landscape): engsel di ATAS, sampul terbuka ke atas. Di bawahnya: engsel kiri, buka ke kiri
    const val TOP_COVER_DEG = 320f        // buku berengsel atas: sampul terus berayun ke BELAKANG buku (melewati 180 = rebah di atas, 270 = menggantung di belakang), jadi menghilang di balik kertas, bukan terpotong layar
    const val MAX_ASPECT = 3.5f           // pengaman geometri; slide 16:9 (1,78), 4:3, A4 landscape semuanya akurat tanpa dipangkas
    const val BACK_DRAG = .50f            // progres turun sampai segini selama back gesture ditarik penuh
    const val TXT_CHANGE = "Change cover"
    const val TXT_RESTORE = "Restore original cover"
    val EASE = androidx.compose.animation.core.LinearEasing   // easing per fase ada di fungsi smoothstep di bawah
    val openSpec: AnimationSpec<Float> = tween(OPEN_MS, easing = EASE)
    /** Menutup: kebalikan ease-out ringan. Awalnya langsung melaju (zoom keluar tidak lagi 'menunggu' di kecepatan nol), ujungnya mendarat lembut di kartu. */
    val CLOSE_EASE = Easing { f -> 1f - Math.pow((1f - f).toDouble(), 1.35).toFloat() }
    val closeSpec: AnimationSpec<Float> = tween(CLOSE_MS, easing = CLOSE_EASE)
    /** Dibalik di tengah jalan (buka lalu langsung tutup, atau tarik back lalu lepas): durasi mengikuti sisa jarak, jadi kecepatan gerak tetap sama, bukan mengulang dari awal. */
    fun openFrom(p: Float): AnimationSpec<Float> = tween((OPEN_MS * (1f - p.coerceIn(0f, 1f)).coerceAtLeast(.18f)).toInt(), easing = EASE)
    fun closeFrom(p: Float): AnimationSpec<Float> = tween((CLOSE_MS * p.coerceIn(0f, 1f).coerceAtLeast(.18f)).toInt(), easing = CLOSE_EASE)
    val backCancelSpec: AnimationSpec<Float> = spring(1f, 500f)
    val peekSpec: AnimationSpec<Float> = spring(.75f, 450f)   // pegas agak longgar: ada pantulan kecil, terasa berbobot
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
fun bookLens(t: Float): Float = 1f - bookDolly(t)
/** 0 = kamera masih di pose kartu, 1 = kamera sudah lurus di depan halaman. Mulai setelah sampul mendarat (DOLLY_FROM), selesai di DOLLY_END (kertas sudah ~93% penuh). */
fun bookDolly(t: Float): Float = sstep((t - BookSpec.DOLLY_FROM) / (BookSpec.DOLLY_END - BookSpec.DOLLY_FROM))
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
        return .32f * sstep(p) + .68f * Math.pow(p.toDouble(), 1.6).toFloat()   // awal ringan (langsung terasa), akhir masih bergerak kencang saat membentur
    }
    val u = ((x - imp) / (1f - imp)).coerceIn(0f, 1f)
    val hop = sin(u * Math.PI.toFloat()) * (1f - u)   // SATU pantulan halus: dua lompatan dalam ~60 ms (durasi baru) terbaca sebagai kedip
    return (1f - BookSpec.SWING_BOUNCE * hop).coerceIn(0f, 1f)
}
/** Reaksi buku terhadap bobot sampul: 0 sampai sampul membentur, lalu denyut kecil selama pantulan, kembali 0 tepat saat zoom mulai. */
/** Denyut antisipasi 0..1..0 di awal ayunan (sebelum sampul benar-benar terangkat). */
fun bookAntic(t: Float): Float {
    val x = (t / BookSpec.COVER_END).coerceIn(0f, 1f) / .25f
    return if (x >= 1f) 0f else sin(x * Math.PI.toFloat())
}
fun bookRock(t: Float): Float {
    val x = (t / BookSpec.COVER_END).coerceIn(0f, 1f)
    if (x <= BookSpec.SWING_IMPACT) return 0f
    val u = (x - BookSpec.SWING_IMPACT) / (1f - BookSpec.SWING_IMPACT)
    return sin(u * Math.PI.toFloat()) * (1f - u)
}
/**
 * Jarak kamera. Tetap CAMERA selama sampul mengayun, lalu MENDEKAT ke ZOOM_CAMERA selama zoom (sejalan dengan lensa yang memusat).
 * Zoom seragam (jarak tetap) membuat sampul rebah / punggung / tebal blok sekadar ikut membesar seperti stiker datar di samping halaman.
 * Dengan kamera yang sungguh mendekat, benda yang lebih rendah dari bidang halaman membesar lebih lambat daripada halaman: sampul menyempit,
 * menjauh ke titik hilang, dan tertinggal di sisi layar, sesuai paralaks zoom sebenarnya. Satu fungsi progres: buka dan tutup identik.
 */
fun bookCamera(t: Float): Float = lerp(BookSpec.CAMERA, BookSpec.ZOOM_CAMERA, bookDolly(t))
/**
 * Pertumbuhan kertas (0 = ukuran kartu, 1 = layar baca). Kecepatan nol di awal (mulai pelan, menyambung dengan sampul yang masih mengayun),
 * lalu bergeser perlahan ke kurva ease-out kubik (mendarat panjang dan lembut). Kecepatan nol di kedua ujung, tanpa kekakuan.
 */
fun bookZoomFrac(t: Float): Float {
    val x = ((t - BookSpec.ZOOM_FROM) / (1f - BookSpec.ZOOM_FROM)).coerceIn(0f, 1f)
    val smoother = x * x * x * (x * (6f * x - 15f) + 10f)
    val out = 1f - (1f - x) * (1f - x) * (1f - x)
    val w = sstep(x / .5f)   // bobot halus 0..1 (kecepatan sambungan kontinu): mulai pelan, lalu cepat, mendarat lembut
    return smoother * (1f - w) + out * w
}
/**
 * Kedatangan di ujung zoom. Latar hitam layar baca TIDAK ikut morph: selama kertas membesar, yang bergerak hanya kertas (dan sampul yang menempel di engselnya).
 * Setelah kertas hampir penuh (~97%) dan sampul sudah di luar layar, bingkai dilepas (layar penuh) dan sampul sisa memudar. Hitamnya sendiri
 * TIDAK lagi memakai kurva ini: lihat bookDim (menggelap bertahap sejak awal, lalu melebur halus ke penuh). Berpatokan pada waktu (progres mentah), bukan ekor kurva zoom.
 */
fun bookReveal(t: Float): Float = sstep((t - .80f) / .14f)
fun bookReaderAlpha(t: Float): Float = sstep((t - BookSpec.READER_ALPHA_FROM) / (BookSpec.READER_ALPHA_TO - BookSpec.READER_ALPHA_FROM))   // wadah layar baca (halaman). Hitam dibawa bookDim, komponen dipudarkan bookUiAlpha
/**
 * Pudar komponen layar baca (bilah atas: panah back, judul, tombol cari, titik tiga; kapsul halaman di bawah). Terpisah dari alpha wadah supaya
 * komponen masuk / keluar lebih pelan dan menyusul setelah kertas dan hitam hampir selesai. Buku: dari UI_ALPHA_FROM ke 1; selain buku (PDF dibuka tanpa animasi buku): selalu 1.
 * Dibaca di fase gambar (graphicsLayer). Menutup = kebalikan persis (progres sama): komponen pudar keluar lebih dulu, baru hitam dan kertas menyusut.
 */
fun bookUiAlpha(vm: AppVM): Float =
    if (vm.morph?.book == true) sstep((vm.openP.value.coerceIn(0f, 1f) - BookSpec.UI_ALPHA_FROM) / (1f - BookSpec.UI_ALPHA_FROM)) else 1f

/**
 * Halaman lanjutan di mode gulir (halaman 2 dst di bawah halaman pertama): 0 = tersembunyi dan agak ke bawah, 1 = di tempatnya.
 * Halaman pertama tidak memakai ini (digambar BookPageLayer, mendarat bersama zoom). Mulai setelah kertas pertama hampir penuh dan menyusul bersama
 * hitam yang melebur; selain buku selalu 1. Menutup = kebalikan persis (halaman lanjutan turun dan pudar lebih dulu).
 */
fun bookTailFrac(vm: AppVM): Float =
    if (vm.morph?.book == true) sstep((vm.openP.value.coerceIn(0f, 1f) - BookSpec.TAIL_ALPHA_FROM) / (1f - BookSpec.TAIL_ALPHA_FROM)) else 1f

/**
 * Gelap latar di luar buku (0 = terang normal, 1 = hitam penuh layar baca). Satu kurva mulus tanpa patahan, buka DAN tutup identik (progres sama):
 *  - 0 .. DIM_FADE_FROM : beranda di balik sampul menggelap pelan sampai DIM_EARLY (mulai dari awal sampul terbuka, kecepatan nol di kedua ujung)
 *  - DIM_FADE_FROM .. DIM_FADE_TO : sisa gelap melebur ke hitam penuh, saat kertas sudah hampir sebesar layar (kecepatan nol di kedua ujung juga,
 *    jadi sambungan dengan fase sebelumnya tidak ada patahan dan ujungnya mendarat lembut)
 * Digambar DI BAWAH buku (kertas dan sampul 3D digambar di atasnya), jadi buku tidak ikut menggelap.
 */
fun bookDim(t: Float): Float {
    val from = BookSpec.DIM_FADE_FROM
    val early = BookSpec.DIM_EARLY * sstep(t / from)
    val late = sstep((t - from) / (BookSpec.DIM_FADE_TO - from))
    return early + (1f - BookSpec.DIM_EARLY) * late
}

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
    val dx = bookSlide(m, w, g.dens, vm.openP.value.coerceIn(0f, 1f))
    return Rect(lerp(m.l + dx, fin.left, tt), lerp(m.t, fin.top, tt), lerp(m.r + dx, fin.right, tt), lerp(m.b, fin.bottom, tt))
}

/**
 * Mode 1 kolom: buku di kartu duduk di tengah dengan margin kiri-kanan lebar, tapi sampul yang rebah menjulur selebar buku ke kiri dan akan terpotong tepi layar.
 * Jadi saat sampul terbuka, buku (halaman + sampul rebah) bergeser ke kanan sampai SEMUA yang terbuka berada di dalam layar, seperti membuka buku di tengah meja.
 * Geser hanya sebesar yang diperlukan (maks SLIDE_1COL x lebar buku, dan tidak melewati tepi kanan layar), mengikuti ayunan sampul (satu progres: menutup = kebalikan persis),
 * lalu lenyap sendiri karena kertas bergerak ke posisi layar baca. 2 kolom: tidak bergeser (kolom di sebelahnya tidak boleh terdorong).
 */
fun bookSlide(m: Morph, w: Float, dens: Float, raw: Float): Float {
    if (BookFx.cols != 1 || BookFx.top) return 0f   // engsel atas: sampul tidak menjulur ke kiri, tidak perlu bergeser
    val bw = m.r - m.l
    if (bw < 2f) return 0f
    val need = bw * .98f + 12f * dens - m.l
    val room = (w - 12f * dens - m.r).coerceAtLeast(0f)
    return need.coerceIn(0f, min(bw * BookSpec.SLIDE_1COL, room)) * bookCoverFrac(raw)
}

private val BOOK_SHAPE = RoundedCornerShape(topStart = 1.5.dp, bottomStart = 1.5.dp, topEnd = 3.dp, bottomEnd = 3.dp)

/** Keadaan buku yang sedang dibuka (hanya satu catatan terbuka sekaligus). */
object BookFx {
    var id: String? = null
    var page by mutableIntStateOf(0)       // halaman yang sedang dibaca (diperbarui layar baca)
    var ratio = 1.4142f                    // tinggi / lebar halaman tersebut
    var dark = false
    var peek = 0f                          // seberapa dalam kartu sedang ditekan saat diketuk (0..1), dilanjutkan mulus oleh lapisan animasi
    var tex: CoverTex? = null              // tekstur sampul yang dipakai kartu saat diketuk (cadangan bila tekstur baru belum jadi)
    var tilt = 0f                          // sudut miring sampul saat disentuh (derajat), dilanjutkan mulus oleh lapisan animasi
    /** Kartu yang bukunya sedang digambar lapisan animasi. State supaya kartu langsung menyembunyikan diri di frame yang sama dengan ketukan. */
    var hideId by mutableStateOf<String?>(null)
    var top = false                        // buku ini berengsel di atas (rasio lebar)
    var cols = 2                           // jumlah kolom beranda saat dibuka (1 = buku dipusatkan ulang agar sampul rebah tetap terlihat)
    var live: Rect? = null                 // posisi halaman di layar (koordinat jendela) saat menutup; null = posisi awal baca
    var snap: (() -> Rect?)? = null        // diisi layar baca
    @Volatile var pin: android.graphics.Bitmap? = null          // halaman terakhir yang dipegang kuat selama animasi (PdfCache LRU bisa membuangnya)
    @Volatile var pinPage = -1
    @Volatile var pageTexSrc: android.graphics.Bitmap? = null   // bitmap halaman asal tekstur 3D (lihat BookTex.pageTex)
    @Volatile var pageTex: android.graphics.Bitmap? = null
    /** Nama berkas PDF buku yang sedang dibuka (PdfUi.fid). Dipakai PdfPage untuk tahu bahwa ia halaman pertama dari animasi buku. */
    @Volatile var pdfName: String? = null
    /**
     * Layar baca SUDAH menggambar bitmap halaman pertamanya (bukan putih polos / kosong). Selama false, BookPageLayer terus menggambar kertas
     * statis di bawahnya, jadi tidak pernah ada celah kosong / putih antara kertas animasi dan layar baca (penyebab kedip saat buka).
     * Diset PdfPage (bitmap tergambar), PdfScreen (gagal buka / batas waktu). Direset di begin().
     */
    var readerReady by mutableStateOf(false)

    /** Bitmap yang dipin untuk halaman ini (bila cache LRU sudah membuangnya). Null kalau bukan halaman buku yang sedang dibuka. */
    fun pinFor(fid: String, index: Int): android.graphics.Bitmap? =
        if (pdfName == fid && pinPage == index) pin else null

    fun begin(id: String, page: Int, ratio: Float, dark: Boolean, cols: Int = 2, top: Boolean = false) {
        this.top = top
        this.id = id; this.page = page; this.ratio = ratio; this.dark = dark; this.cols = cols; live = null; tilt = 0f; peek = 0f; tex = null
        readerReady = false; pdfName = null
    }

    fun capture(noteId: String) {
        if (id == noteId) live = try { snap?.invoke() } catch (e: Throwable) { null }
    }

    /** Simpan halaman terakhir seketika saat menutup (tanpa menunggu penundaan 900 ms di layar baca). */
    private var pendingId: String? = null
    private var pendingPage = 0
    /** Catat halaman terakhir sekarang, tulis ke catatan SETELAH animasi tutup (vm.upd memicu recompose + urut ulang seluruh daftar). */
    fun deferPersist(vm: AppVM, noteId: String) {
        if (id != noteId) return
        pendingId = noteId; pendingPage = page
    }
    fun flushPersist(vm: AppVM) {
        val nid = pendingId ?: return
        pendingId = null
        val n = vm.note(nid) ?: return
        if (n.pdf.isNotEmpty() && n.pg != pendingPage) vm.upd(nid) { it.copy(pg = pendingPage) }
    }

    fun persistPage(vm: AppVM, noteId: String) {
        val n = vm.note(noteId) ?: return
        if (id == noteId && n.pdf.isNotEmpty() && n.pg != page) vm.upd(noteId) { it.copy(pg = page) }
    }
}

/**
 * Rasio (lebar / tinggi) sampul tiap buku = rasio gambar sampulnya (halaman 1 PDF, atau sampul kustom yang dipangkas ke rasio buku), dibatasi
 * MIN_ASPECT..MAX_ASPECT. Disimpan di memori per berkas sampul (tidak perlu field baru di Note); bila belum diketahui dipakai rasio standar.
 * `ver` adalah state: kartu yang membacanya otomatis tersusun ulang sekali saat rasionya baru diketahui.
 */
object BookAspect {
    private val map = java.util.concurrent.ConcurrentHashMap<String, Float>()
    var ver by mutableIntStateOf(0)

    fun put(cv: String, w: Int, h: Int) {
        if (cv.isEmpty() || w < 1 || h < 1) return
        val a = (((w.toFloat() / h) * 1000f).roundToInt() / 1000f).coerceIn(BookSpec.MIN_ASPECT, BookSpec.MAX_ASPECT)
        if (map.put(cv, a) != a) ver++
    }

    fun of(cv: String): Float {
        val v = ver   // dibaca supaya pemanggil di komposisi ikut berubah
        return if (v < 0 || cv.isEmpty()) BookSpec.ASPECT else (map[cv] ?: BookSpec.ASPECT)
    }
    fun of(d: Note): Float = of(d.cv)
    fun isTop(a: Float): Boolean = a >= BookSpec.TOP_HINGE_ASPECT
    /** Tebal blok halaman relatif standar (1 = standar ~20 halaman), dari jumlah halaman di teks catatan ("PDF · 48 pages"). Tidak terbaca => 1. */
    fun thick(d: Note): Float {
        val n = Regex("\\d+").find(d.x)?.value?.toIntOrNull() ?: return 1f
        if (n <= 0) return 1f
        return .55f + .95f * (Math.log(n.toDouble()) / Math.log(300.0)).toFloat().coerceIn(0f, 1f)
    }
    /** Sudut sampul rebah di bidang papan belakang untuk tebal blok halaman tertentu. */
    fun restDeg(thick: Float): Float = 180f + Math.toDegrees(Math.asin((BookSpec.PAGES * thick + BookSpec.BOARD).toDouble())).toFloat()

    /** Aman dari thread latar. Memakai bitmap di cache bila ada, kalau tidak hanya membaca ukuran berkas (murah, tanpa dekode piksel). */
    fun ensure(dir: File, cv: String) {
        if (cv.isEmpty() || map.containsKey(cv)) return
        Imgs.peek(cv)?.let { put(cv, it.width, it.height); return }
        val f = File(dir, "$cv.jpg")
        if (!f.exists()) return
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        put(cv, o.outWidth, o.outHeight)
    }
}

class BookReady(val page: Int, val ratio: Float)

/** Bitmap halaman terakhir untuk animasi: dari PdfCache, atau salinan yang dipin di BookFx bila cache sempat membuangnya (tidak pernah kosong / putih). */
fun bookPageBmp(pdf: String): android.graphics.Bitmap? =
    PdfCache.best(pdf, BookFx.page) ?: BookFx.pin?.takeIf { BookFx.pinPage == BookFx.page }

object BookArt {
    private val ready = java.util.concurrent.ConcurrentHashMap<String, BookReady>()
    private fun rkey(d: Note) = d.pdf + ":" + d.pg
    private fun tkey(d: Note) = "${d.cv}|${d.cx}|${d.cy}|${d.cz}|${BookAspect.of(d)}"

    // BookTex.cache hanya 24 MB (~10 sampul): dengan ratusan buku tekstur sering terbuang, lalu setiap ketukan jatuh ke jalur lambat dan
    // lapisan animasi bisa mulai dengan sampul polos. Tekstur buku yang sudah disiapkan dipegang di sini (maks 8, terlama dibuang).
    private val pins = object : LinkedHashMap<String, CoverTex>(16, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, CoverTex>?) = size > 8
    }
    @Synchronized fun pinTex(d: Note, t: CoverTex) { pins[tkey(d)] = t }
    @Synchronized fun pinned(d: Note): CoverTex? = pins[tkey(d)]
    fun tex(d: Note): CoverTex? = BookTex.peek(d) ?: pinned(d)

    /** Hanya beberapa kartu PDF pertama yang diprapanaskan saat app baru dibuka (jangan merender halaman untuk ratusan buku). */
    private val idleTaken = java.util.concurrent.atomic.AtomicInteger(0)
    val idleLock = kotlinx.coroutines.sync.Mutex()
    fun claimIdle(): Boolean = idleTaken.incrementAndGet() <= 4

    /**
     * Hasil prepare yang masih valid (halaman terakhir sudah di PdfCache, sampul + tekstur 3D sudah ada, preferensi sudah terbaca)
     * atau null. Murni baca memori (tanpa I/O), jadi aman dipanggil di thread utama saat ketukan: animasi bisa mulai SEKETIKA.
     */
    fun peekReady(d: Note, screenWpx: Int): BookReady? {
        val r = ready[rkey(d)] ?: return null
        if (PdfBus.mode < 0 || PdfBus.curl < 0 || PdfBus.curlStyle < 0) return null
        if (tex(d) == null) return null
        val have = PdfCache.best(d.pdf, r.page) ?: return null
        if (have.width < screenWpx * .8f) return null
        return r
    }

    /**
     * Pastikan bitmap halaman terakhir ada di PdfCache (kunci sama dengan layar baca, jadi layar baca memakai bitmap yang SAMA,
     * tanpa render ulang). Dipanggil sebelum animasi mulai; bila gagal animasi tetap jalan memakai sampul sebagai isi.
     */
    suspend fun prepare(vm: AppVM, ctx: Context, d: Note, screenWpx: Int): BookReady = withContext(Dispatchers.IO) {
        val key = rkey(d)
        if (d.cv.isNotEmpty()) try { Imgs.load(vm.imgDir, d.cv) } catch (e: Throwable) { }
        try {
            val t = BookTex.ensure(d)
            if (d.cv.isEmpty() || Imgs.peek(d.cv) != null) pinTex(d, t)   // tekstur asli (bukan polos cadangan) dipegang
        } catch (e: Throwable) { }
        run {
            val sp0 = ctx.getSharedPreferences("dinduy", 0)
            if (PdfBus.mode < 0) PdfBus.mode = sp0.getInt("pdfmode", 1)
            if (PdfBus.curl < 0) PdfBus.curl = sp0.getInt("pdfcurl", 1)
            if (PdfBus.curlStyle < 0) PdfBus.curlStyle = sp0.getInt("pdfcurlstyle", 0)
        }
        peekReady(d, screenWpx)?.let { return@withContext it }   // sudah pernah disiapkan: tanpa buka PdfRenderer lagi
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
        BookReady(page, ratio).also { ready[key] = it }
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
            // gaya kartun datar (sama dengan BookTex.build): satu pita engsel solid tipis + satu garis terang, tanpa gradasi
            if (BookAspect.isTop(BookAspect.of(d))) {   // buku lebar: pita engsel di atas
                drawRect(Color.Black.copy(.16f), Offset.Zero, Size(w, h * .07f))
                drawRect(Color.White.copy(.22f), Offset(0f, h * .07f), Size(w, maxOf(1.5f, h * .008f)))
            } else {
                drawRect(Color.Black.copy(.16f), Offset.Zero, Size(w * .07f, h))
                drawRect(Color.White.copy(.22f), Offset(w * .07f, 0f), Size(maxOf(1.5f, w * .008f), h))
            }
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
    val warmJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val peek by animateFloatAsState(if (pressed) 1f else 0f, BookSpec.peekSpec, label = "bkpeek")
    val selA by animateFloatAsState(if (selected) 1f else 0f, spring(.6f, 400f), label = "bksel")
    // "tuk" kecil saat sampul menutup di kartu: buku menekan sedikit lalu memantul balik (kebalikan logis dari ayunan buka yang membentur meja)
    val thud = remember { Animatable(0f) }
    LaunchedEffect(d.id) {
        var was = false
        snapshotFlow { val p = vm.openP.value; vm.openId == d.id && p > .05f }.collect { now ->
            if (was && !now && vm.openP.targetValue == 0f) {
                thud.snapTo(0f)
                thud.animateTo(1f, tween(70))
                thud.animateTo(0f, spring(.40f, 420f))
            }
            was = now
        }
    }
    // penanda sembunyi dilepas setelah animasi tutup selesai dan catatan sudah tidak ada di tumpukan
    LaunchedEffect(d.id) {
        snapshotFlow { vm.openP.value <= .01f && vm.nav.stack.none { it is Scr.Note && it.id == d.id } }.collect { done ->
            if (done && BookFx.hideId == d.id) BookFx.hideId = null
        }
    }
    // tekanan jari dilanjutkan mulus oleh lapisan animasi: nilai terkini selalu dibagikan lewat BookFx, jadi tidak ada lompatan saat lapisan mengambil alih
    LaunchedEffect(d.id) {
        snapshotFlow { peek }.collect { v -> if (BookFx.id == d.id && vm.openId == d.id) BookFx.peek = v }
    }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    /** Siapkan semuanya di latar: halaman terakhir, sampul, tekstur 3D, dan halaman yang sudah diperkecil untuk frame pertama. */
    suspend fun warm(): BookReady {
        val r = BookArt.prepare(vm, ctx, d, screenW)
        val cw = coords[0]?.size?.width ?: 0
        if (cw > 0) withContext(Dispatchers.IO) {
            try { PdfCache.best(d.pdf, r.page)?.let { BookTex.pageTex(it, cw.toFloat()) } } catch (e: Throwable) { }
            try { BookTex.inner() } catch (e: Throwable) { }   // sisi dalam sampul: dibuat sekali di latar, bukan di frame gambar pertama
        }
        return r
    }
    // app baru dibuka: siapkan beberapa buku PDF pertama di latar (satu per satu), supaya ketukan pertama tidak menunggu render halaman + tekstur
    LaunchedEffect(d.id, d.pdf, needPrep) {
        if (needPrep && BookArt.peekReady(d, screenW) == null && BookArt.claimIdle()) {
            delay(400)
            BookArt.idleLock.withLock {
                try { BookArt.prepare(vm, ctx, d, screenW); BookTex.inner() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { }
            }
        }
    }
    // jari baru menyentuh: mulai menyiapkan SEKARANG (jeda sentuh -> lepas ~80-150 ms terpakai), bukan menunggu ketukan selesai
    LaunchedEffect(pressed, needPrep) {
        if (pressed && needPrep && BookArt.peekReady(d, screenW) == null && warmJob[0]?.isActive != true) {
            warmJob[0] = scope.launch { try { warm() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { } }
        }
    }

    fun morphNow(): Morph {
        val c = coords[0]
        val rc = if (c != null && c.isAttached) c.boundsInWindow() else Rect.Zero
        return Morph(
            rc.left, rc.top, rc.right, rc.bottom, 3f * dens, Color.Black,
            floatArrayOf(1.5f * dens, 3f * dens, 3f * dens, 1.5f * dens), null, card = true, book = true
        )
    }

    // rasio buku mengikuti halaman 1 PDF (dibaca dari gambar sampulnya). Sebelum diketahui memakai rasio standar, lalu menyesuaikan sekali.
    val asp = BookAspect.of(d)
    val thick = remember(d.x) { BookAspect.thick(d) }   // tebal buku mengikuti jumlah halaman
    val night = LocalPal.current.dark                    // palet sisi halaman untuk mode gelap
    LaunchedEffect(d.cv) { if (d.cv.isNotEmpty()) withContext(Dispatchers.IO) { try { BookAspect.ensure(vm.imgDir, d.cv) } catch (e: Throwable) { } } }
    // tekstur sampul 3D dibuat di latar; sebelum jadi, kartu memakai sampul datar (tanpa kedip)
    val coverKey = "${d.cv}|${d.cx}|${d.cy}|${d.cz}|$asp"
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
                fun go(r: BookReady) {
                    BookFx.begin(d.id, r.page, r.ratio, pdark, cols, BookAspect.isTop(asp))
                    BookFx.pdfName = d.pdf
                    BookFx.tilt = BookSpec.PEEK_YAW * peek
                    BookFx.peek = peek
                    BookFx.tex = BookArt.tex(d) ?: ct
                    BookFx.pin = PdfCache.best(d.pdf, r.page); BookFx.pinPage = r.page
                    BookFx.hideId = d.id   // kartu menyembunyikan bukunya di frame yang sama dengan lapisan animasi mulai menggambar
                    onClick(morphNow())
                }
                if (!needPrep) onClick(morphNow())
                else {
                    // jalur cepat: semuanya sudah siap (prewarm saat jari menyentuh / ketukan sebelumnya) -> animasi mulai di frame ini juga
                    val fast = BookArt.peekReady(d, screenW)
                    val cw0 = coords[0]?.size?.width ?: 0
                    val pg0 = fast?.let { PdfCache.best(d.pdf, it.page) }
                    if (fast != null && (cw0 <= 0 || pg0 == null || pg0.width <= cw0 * 2f || BookFx.pageTexSrc === pg0)) go(fast)
                    else if (!busy) {
                        busy = true
                        scope.launch {
                            try {
                                warmJob[0]?.join()   // prewarm yang sudah jalan sejak jari menyentuh tidak diulang
                                go(warm())
                            } finally { busy = false }
                        }
                    }
                }
            },
            onLongClick = if (onLong != null) ({ haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLong() }) else null
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        // 1 kolom: buku lebar (slide) sedikit lebih lebar dari buku tegak, tapi tetap dibatasi supaya sampul yang rebah tidak melewati layar
        // buku lebar (engsel atas, sampul tidak menjulur ke kiri) dibuat sebesar layar 1 kolom (sisa margin kartu), bukan kecil
        val maxW1 = if (BookAspect.isTop(asp)) 440f else 200f + 32f * ((asp - BookSpec.ASPECT) / (BookSpec.TOP_HINGE_ASPECT - BookSpec.ASPECT)).coerceIn(0f, 1f)
        Column(Modifier.widthIn(max = if (cols == 1) maxW1.dp else 480.dp).fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 5.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(asp)
                        .onGloballyPositioned { coords[0] = it; track?.invoke(morphNow()) }
                ) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            // selama lapisan animasi memegang buku (buka / menutup), kartu tidak menggambar bukunya sendiri
                            // lapisan animasi masih hidup selama progres > .01 atau catatan masih di tumpukan (NoteLayer berhenti menggambar di luar itu).
                            // hideId dilepas SATU FRAME LEBIH LAMBAT lewat LaunchedEffect; kalau kartu tetap tersembunyi karena hideId saja, ada 1 frame
                            // di mana lapisan animasi sudah hilang DAN kartu belum tampil = sampul kosong (kedip di akhir menutup). Jadi hideId hanya
                            // berlaku selama lapisan animasi memang masih ada.
                            val layerAlive = vm.openP.value > .01f || vm.nav.stack.any { it is Scr.Note && it.id == d.id }
                            val own = vm.morph?.book == true && vm.openId == d.id && layerAlive
                            alpha = if (own || (BookFx.hideId == d.id && layerAlive)) 0f else 1f
                        }
                    ) {
                        if (ct == null) BookCoverFace(vm, d, Modifier.fillMaxSize())
                        else Canvas(Modifier.fillMaxSize()) {
                            // buku 3D sungguhan: lurus; saat disentuh ditekan (mengecil, lensa merapat), lalu memantul balik (pegas longgar)
                            val yaw = BookSpec.REST_YAW + BookSpec.PEEK_YAW * peek
                            val pitch = BookSpec.REST_PITCH + BookSpec.PEEK_PITCH * peek
                            val k = BookSpec.REST_SCALE + BookSpec.PEEK_SCALE * peek - .02f * thud.value
                            val lens = 1f + BookSpec.PEEK_LENS * peek
                            val lift = BookSpec.PEEK_DEG * peek.coerceAtLeast(0f)   // sampul terangkat sedikit saat ditekan: buku terasa bisa dibuka
                            drawIntoCanvas {
                                Book3D.draw(
                                    it.nativeCanvas, 0f, 0f, size.width, size.height,
                                    yaw, pitch, k, lift, ct, null, lift > .5f, false,
                                    BookSpec.REST_LENS_X * lens, BookSpec.REST_LENS_Y * lens, top = BookAspect.isTop(asp),
                                    thick = thick, night = night
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
                                CornerRadius(2.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            T(d.t.trim().ifBlank { "PDF" }, 14f, 600, pal.os, Modifier.padding(horizontal = 2.dp), lh = 1.25f, maxLines = 2)
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
    remember(id) { if (BookFx.id != id) { BookFx.begin(id, d.pg, 1.4142f, false, 2, BookAspect.isTop(BookAspect.of(d))); BookFx.pdfName = d.pdf }; 0 }
    // jaring pengaman: kertas statis tidak boleh tertinggal selamanya bila layar baca tak pernah menggambar halaman pertamanya
    // (mode 3D yang tidak memakai PdfPage, atau render yang tak kunjung selesai). Hitung 3 detik SETELAH animasi buka selesai.
    LaunchedEffect(id, BookFx.readerReady) {
        if (BookFx.readerReady) return@LaunchedEffect
        snapshotFlow { vm.openP.value >= .999f }.first { it }
        delay(3000)
        BookFx.readerReady = true
    }
    Canvas(Modifier.fillMaxSize()) {
        val tRaw = vm.openP.value.coerceIn(0f, 1f)
        val tt = bookZoomFrac(tRaw)
        vm.morphTick
        if (tt <= .0005f) return@Canvas   // fase sampul terbuka: halaman digambar mesin 3D di BookCoverLayer (ikut miring bersama buku)
        // wadah layar baca sudah penuh (alpha 1): ia membawa halamannya sendiri (PdfPage / BookPlaceholder) di kotak yang sama. Halaman statis ini TIDAK boleh
        // tetap digambar di belakangnya: di mode gulir, begitu daftar bergeser (atau di celah antar halaman) halaman statis ini tembus terlihat sebagai PDF kedua
        // DULU: berhenti begitu progres >= READER_ALPHA_TO, tanpa peduli layar baca sudah menggambar halamannya atau belum. Layar baca ternyata baru
        // punya bitmap ±2 detik kemudian (PdfRenderer antre), jadi yang terlihat: kertas -> kosong -> abu-abu -> putih polos -> baru teks (kedip).
        // SEKARANG: kertas statis ini baru berhenti setelah layar baca BENAR-BENAR menggambar bitmap halaman pertama (BookFx.readerReady).
        // Saat menutup readerReady sudah true, jadi perilaku menutup tidak berubah.
        if (tRaw >= BookSpec.READER_ALPHA_TO && BookFx.readerReady) return@Canvas
        val c = bookPageRect(vm, size.width, size.height, tt, geo)   // kotak kertas = kotak bingkai (lihat NoteLayer)
        val bmp = bookPageBmp(d.pdf)?.asImageBitmap()
        if (bmp == null) { drawRect(Color.White, Offset(c.left, c.top), Size(c.width, c.height)); return@Canvas }   // tak pernah hitam
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val asp = BookAspect.of(d)
        var cw = bw
        var ch = cw / asp
        if (ch > bh) { ch = bh; cw = ch * asp }
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
        val r = BookFx.live ?: bookFinalRect(size.width, size.height, topPad, sab, dens, PdfBus.mode.coerceAtLeast(0), BookFx.ratio)
        val bmp = bookPageBmp(note.pdf)?.asImageBitmap()
        // tanpa bitmap: JANGAN menggambar putih polos. Kertas statis BookPageLayer masih ada di bawahnya (readerReady belum true), dan putih polos
        // di atasnya adalah kedip putih yang terlihat saat buka.
        if (bmp == null) return@Canvas
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
    val coverKey = "${d.cv}|${d.cx}|${d.cy}|${d.cz}|${BookAspect.of(d)}"
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
    val thick = remember(d.x) { BookAspect.thick(d) }
    val night = LocalPal.current.dark
    val hap = LocalHapticFeedback.current
    val shadowPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    // haptik ringan tepat saat sampul mendarat (hanya buku engsel kiri: engsel atas tidak membentur meja). Hanya pada peralihan belum->sudah mendarat
    LaunchedEffect(id) {
        var prev = vm.openP.value / BookSpec.COVER_END >= BookSpec.SWING_IMPACT
        snapshotFlow { vm.openP.value / BookSpec.COVER_END >= BookSpec.SWING_IMPACT }.collect { now ->
            if (now && !prev && !BookAspect.isTop(BookAspect.of(d))) try { hap.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (e: Throwable) { }
            prev = now
        }
    }
    val ctHold = remember(id) { arrayOfNulls<CoverTex>(1) }
    (BookTex.peek(d) ?: BookFx.tex ?: BookArt.pinned(d))?.let { ctHold[0] = it }   // texVer ikut dibaca: tekstur baru menggantikan yang lama
    texVer
    val ct = ctHold[0] ?: BookTex.plain()   // sekali ada tekstur asli, tidak pernah turun lagi ke polos (kedip polos <-> sampul)
    // tekstur halaman pertama yang berhasil didapat dipakai terus sampai animasi selesai. PdfCache.best bisa berganti bitmap (pembaca merender
    // ulang) atau kosong sesaat; tanpa kunci ini halaman di bawah sampul berganti / hilang antar frame = kedip, dan pageTex dibangun ulang di fase gambar.
    val pageHold = remember(id) { arrayOfNulls<android.graphics.Bitmap>(1) }
    Canvas(Modifier.fillMaxSize()) {
        val tRaw = vm.openP.value.coerceIn(0f, 1f)
        val tt = bookZoomFrac(tRaw)
        vm.morphTick
        val r = bookPageRect(vm, size.width, size.height, tt, geo)   // engsel menempel di tepi kiri kertas yang membesar
        val pe = bookPoseFrac(tRaw)
        val topB = BookAspect.isTop(BookAspect.of(d))
        val rock = if (topB) 0f else bookRock(tRaw)                    // denyut tekan saat sampul membentur meja (0 sebelum benturan dan saat zoom; buku engsel atas tidak membentur meja)
        val pk = if (morphClosing(vm)) 0f else BookFx.peek              // tekanan jari dilanjutkan saat membuka; saat menutup kartu sendiri yang menerima
        val yaw = (BookSpec.REST_YAW + BookFx.tilt) * (1f - pe)        // tidak ada goyangan putar: bidang halaman (z = 0) selalu persis di kotaknya
        val pitch = BookSpec.REST_PITCH * (1f - pe)
        val k = lerp(BookSpec.REST_SCALE + BookSpec.PEEK_SCALE * pk, 1f, pe) - BookSpec.ROCK_SCALE * rock - BookSpec.ANTIC_SCALE * bookAntic(tRaw) * (1f - pe)
        // kamera DIAM selama sampul mengayun (sudut pandang kartu bertahan sampai sampul rebah), baru bergeser ke tengah saat zoom
        val lensF = bookLens(tRaw) * (1f + BookSpec.PEEK_LENS * pk * (1f - pe))
        val lx = BookSpec.REST_LENS_X * lensF
        val ly = BookSpec.REST_LENS_Y * lensF
        // engsel atas: ayunan monoton sampai di belakang buku (tanpa pantulan membentur meja)
        val swing = if (topB && tRaw / BookSpec.COVER_END >= BookSpec.SWING_IMPACT) 1f else bookSwing(tRaw)
        // sudut = ayunan + sisa intipan jari (memudar seiring ayunan), jadi dimulai TEPAT di sudut yang terlihat pada kartu
        val full = if (topB) BookSpec.TOP_COVER_DEG else BookAspect.restDeg(thick)   // rebah di papan belakang, sesuai tebal buku
        val deg = (full * swing + BookSpec.PEEK_DEG * pk.coerceAtLeast(0f) * (1f - swing)).coerceAtMost(full + 7f)
        // halaman terakhir dibuka: hanya sebelum zoom (setelah itu BookPageLayer yang menggambarnya)
        val pageOn = tt <= .0005f
        val page = if (!pageOn) null else (pageHold[0] ?: bookPageBmp(d.pdf)?.let { BookTex.pageTex(it, r.width) }?.also { pageHold[0] = it })
        // sampul menempel di engsel kertas sepanjang zoom (ikut membesar, keluar layar secara alami). Hanya sisa yang masih terlihat (mis. mode halaman,
        // engsel tidak di tepi layar) memudar singkat bersama latar hitam yang masuk, SETELAH kertas hampir penuh
        val fadeA = 1f - bookReveal(tRaw)
        if (fadeA <= .01f) return@Canvas
        drawIntoCanvas {
            val cnv = it.nativeCanvas
            val bw = r.width
            val sv = if (fadeA < .999f) cnv.saveLayerAlpha(
                maxOf(0f, r.left - bw * 1.2f), maxOf(0f, r.top - bw * .4f),
                minOf(size.width, r.right + bw * .4f), minOf(size.height, r.bottom + bw * .4f), (fadeA * 255f).roundToInt()
            ) else -1
            // bayangan kontak datar (oval solid, tanpa blur) melebar sesaat saat sampul membentur meja, lalu hilang. Tanpa denyut = tidak digambar
            val sh = (rock * 2.2f).coerceIn(0f, 1f)
            if (sh > .01f) {
                shadowPaint.color = ((if (night) 70 else 46) * sh).toInt() shl 24
                val gw = bw * (.04f - .05f * sh)
                cnv.drawOval(r.left + gw, r.bottom - bw * .012f, r.right - gw * .4f, r.bottom + bw * (.035f + .02f * sh), shadowPaint)
            }
            Book3D.draw(
                cnv, r.left, r.top, r.right, r.bottom,
                yaw, pitch, k, deg, ct, page, pageOn, BookFx.dark, lx, ly, bookCamera(tRaw), bookDolly(tRaw), topB,
                thick, night
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
        // sampul kustom dipangkas ke rasio buku itu sendiri (bukan rasio tetap), jadi bentuk bukunya tidak berubah
        val a = BookAspect.of(note(id)?.cv ?: "")
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
        BookAspect.put(nid, out.width, out.height)
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
    BookAspect.ensure(imgDir, cv)   // rasio halaman 1 diketahui sebelum catatan diperbarui: tidak ada lompatan bentuk
    BookTex.evict(n.cv)   // bila id sampulnya sama, tekstur usang tidak boleh tersisa
    try { BookTex.ensure(n.copy(cv = cv, cx = .5f, cy = 0f, cz = 1f, cp = "")) } catch (e: Throwable) { }
    withContext(Dispatchers.Main) { upd(id) { it.copy(cv = cv, cx = .5f, cy = 0f, cz = 1f, cp = "") } }
    true
}

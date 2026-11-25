package app.dinduy

/*
 * Note theme: warna (slider hue) + backdrop (ornamen) per catatan.
 *
 *  - Backdrop: "Morph" (bentuk expressive: amuba, capsule, cookie, squircle, clover, pebble yang saling berubah bentuk),
 *    "Canopy" (daun, ranting berkulit, sulur, kupu-kupu), "Skyline" (kota tiga lapis berperspektif, awan, matahari / bulan),
 *    "Reef" (terumbu karang, ikan, kura-kura, ubur-ubur). "Indonesia" (hutan tropis dengan reruntuhan candi, komodo, orangutan,
 *    cendrawasih, badak Jawa). Plain = tanpa ornamen.
 *  - Parallax: lapisan dekat dan jauh bergeser berlawanan arah mengikuti jari (parX); tanpa jari, kedalaman tetap hidup lewat
 *    gerak waktu per lapis. Ketukan memicu respons khas tiap adegan (riak + percikan, gelembung, dentuman lahar, dst.).
 *  - Selalu DI BELAKANG teks: digambar tepat setelah kertas dan sebelum sampul / isi catatan.
 *  - Ukuran semua elemen dihitung dari LEBAR wadah (u = w). Karena itu ornamen di kartu, di wadah morph, dan di editor
 *    adalah gambar yang sama persis, hanya skalanya berbeda -> buka/tutup kartu mulus tanpa lompatan.
 *  - Legibilitas: kontras ornamen dipagari (ornCap), ornamen meredup + melambat saat mengetik (OrnFx.calm), tepi bentuk berbulu,
 *    dan tepi bentuk berbulu.
 *  - Editor: tanpa parallax. Gerak murni waktu (orbit, napas, angin) + respons pegas: menghindari jari saat disentuh, berkumpul ke jari
 *    saat ditahan, bergelombang dari titik ketuk, dan "mekar" saat teks diangkat (ed.tl).
 *  - Kartu: pose diam (tanpa animasi), dengan pose terakhir dari editor supaya kartu tidak "loncat" setelah ditutup.
 *  - Performa: kartu merekam adegannya sekali ke GraphicsLayer luring lalu hanya menempel (cardOrnament); editor memakai OrnPacer
 *    (laju diam ~60 fps + penjaga beban, penuh saat interaksi); glow memakai gradien satuan yang di-cache; tak ada clipPath per frame
 *    di Skyline / Reef. Setel ORN_DEBUG = true untuk mencatat biaya gambar per frame ke logcat (tag OrnPerf).
 */

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpC
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// =====================================================================================================================
// Katalog backdrop
// =====================================================================================================================

class OrnInfo(val id: String, val name: String, val sub: String, val hue: Float)

/** hue < 0 = tidak punya rona khas. Rona khas hanya dipakai sebagai titik awal saat catatan masih "Auto". */
val ORN_LIST = listOf(
    OrnInfo("", "Plain", "Just the paper", -1f),
    OrnInfo("morph", "Morph", "Shapes that flow and change", -1f),
    OrnInfo("canopy", "Canopy", "Leaves swaying in the wind", 138f),
    OrnInfo("skyline", "Skyline", "A city under a slow sun", 214f),
    OrnInfo("reef", "Reef", "Coral, fish and drifting jellyfish", 172f),
    OrnInfo("indonesia", "Indonesia", "Temple ruins in a living jungle", 104f),
)

fun hueName(h: Float): String {
    val x = ((h % 360f) + 360f) % 360f
    return when {
        x < 15f -> "Red"; x < 45f -> "Orange"; x < 70f -> "Yellow"; x < 95f -> "Lime"; x < 165f -> "Green"
        x < 195f -> "Teal"; x < 225f -> "Sky"; x < 255f -> "Blue"; x < 285f -> "Indigo"; x < 320f -> "Violet"
        x < 345f -> "Pink"; else -> "Red"
    }
}

fun themeSummary(d: Note?): String {
    if (d == null) return ""
    val o = ORN_LIST.firstOrNull { it.id == d.orn }?.name?.takeIf { d.orn.isNotEmpty() }
    val c = if (d.th >= 0f) hueName(d.th) else null
    return if (o == null && c == null) "Color and backdrop for this note" else listOfNotNull(c, o).joinToString(" · ")
}

// =====================================================================================================================
// Palet per catatan
// =====================================================================================================================

/** Palet dari hue pilihan pengguna. Expressive memakai expressivePal (hue persis, tanpa exOff); mode biasa memakai buildPal. */
fun themedPal(gp: Pal, th: Float): Pal {
    val p = if (gp.ex) expressivePal(gp.dark, th, gp.exS)
    else buildPal(if (gp.dark) "dark" else "light", String.format("#%06X", hsl(th, 60f, 50f).toArgb() and 0xFFFFFF))
    return if (gp.mono) p.toMono() else p
}

/**
 * Satu-satunya tempat yang menentukan warna sebuah catatan (kartu DAN editor memakai ini, jadi selalu sama):
 * hue pilihan > warna sampul > rona Expressive per catatan > palet aplikasi.
 */
fun notePalFor(gp: Pal, d: Note): Pal {
    if (d.th >= 0f && d.pdf.isEmpty()) return themedPal(gp, d.th)
    return if (d.cp.isEmpty() || d.cv.isEmpty()) {
        if (gp.ex) expressivePal(gp.dark, gp.exH + exOff(d.id), gp.exS) else gp
    } else coverPal(gp.dark, d.cp, gp.mono, gp.ex)
}

// =====================================================================================================================
// Warna ornamen: semuanya turunan kertas (pal.s) -> kontras dengan teks tetap aman, ornamen selalu "tonal"
// =====================================================================================================================

@Immutable
class OrnPaint(
    val a: Color, val b: Color, val c: Color, val d: Color, val ink: Color, val paper: Color, val amt: Float = 1f, val dark: Boolean = false,
    /** Warna lahar (hot = oranye, hot2 = inti kuning) dan batu gelap (deep): satu-satunya warna di luar rona catatan, tetap dipagari kontras. */
    val hot: Color = d, val hot2: Color = d, val deep: Color = ink
) {
    fun pick(i: Int): Color = when (((i % 4) + 4) % 4) { 0 -> a; 1 -> b; 2 -> c; else -> d }
    fun alpha(k: Float): OrnPaint = if (k >= 1f) this else OrnPaint(
        a.copy(alpha = a.alpha * k), b.copy(alpha = b.alpha * k), c.copy(alpha = c.alpha * k),
        d.copy(alpha = d.alpha * k), ink.copy(alpha = ink.alpha * k), paper, amt * k, dark,
        hot.copy(alpha = hot.alpha * k), hot2.copy(alpha = hot2.alpha * k), deep.copy(alpha = deep.alpha * k)
    )
}

private fun ornLum(c: Color): Float = .2126f * c.red + .7152f * c.green + .0722f * c.blue

/**
 * Pagar kontras: jarak terang-gelap warna ornamen terhadap kertas dibatasi ([maxD]). Rona tetap, hanya "volume"-nya yang dijaga,
 * jadi apa pun warna catatan / sampul yang dipilih, ornamen tidak pernah cukup kuat untuk bersaing dengan tulisan.
 */
private fun ornCap(c: Color, paper: Color, maxD: Float): Color {
    val d = abs(ornLum(c) - ornLum(paper))
    return if (d <= maxD) c else lerpC(paper, c, maxD / d)
}

/**
 * [k] = intensitas ornamen pilihan pengguna (1 = bawaan). Pagar kontras ([ornCap]) dan kadar campuran warna ke kertas sama-sama
 * dikalikan k, jadi k > 1 benar-benar menebalkan ornamen (berguna kalau kertas berwarna pekat membuat ornamen samar), dan k < 1 menenangkannya.
 */
fun ornPaint(p: Pal, k: Float = 1f): OrnPaint {
    val dk = p.dark
    val s = p.s
    val kk = k.coerceIn(ORN_INT_MIN, ORN_INT_MAX)
    val cap = (if (dk) .12f else .10f) * kk
    fun mix(f: Float) = (f * kk).coerceAtMost(1f)
    return OrnPaint(
        ornCap(lerpC(s, p.p, mix(.20f)), s, cap),
        ornCap(lerpC(s, p.tc, mix(if (dk) .85f else .70f)), s, cap),
        ornCap(lerpC(s, p.sc, mix(if (dk) .85f else .75f)), s, cap),
        ornCap(lerpC(s, p.ac, mix(if (dk) .80f else .60f)), s, cap * 1.15f),
        ornCap(lerpC(s, p.p, mix(if (dk) .34f else .30f)), s, cap * 1.35f),
        s, dark = dk,
        hot = ornCap(lerpC(s, Color(0xFFFF5A1F), mix(.92f)), s, cap * 2.4f),
        hot2 = ornCap(lerpC(s, Color(0xFFFFB347), mix(.95f)), s, cap * 2.9f),
        deep = ornCap(lerpC(s, Color(0xFF2B1B1B), mix(.80f)), s, cap * 2.1f)
    )
}

const val ORN_INT_MIN = .4f
const val ORN_INT_MAX = 3f

// =====================================================================================================================
// Sinyal sentuhan / gulir
// =====================================================================================================================

@Stable
class OrnFx {
    var tx by mutableFloatStateOf(0f)          // posisi jari mentah
    var ty by mutableFloatStateOf(0f)
    var px by mutableFloatStateOf(0f)          // posisi jari yang mengikuti dengan pegas (sedikit melampaui lalu mengendap)
    var py by mutableFloatStateOf(0f)
    var press by mutableFloatStateOf(0f)       // 0..~1.15 jari menempel: pegas kurang teredam, jadi membal sedikit saat menyentuh / melepas
    var hold by mutableFloatStateOf(0f)        // 0..1 jari ditahan diam
    var lift by mutableFloatStateOf(0f)        // 0..1 teks diangkat
    var calm by mutableFloatStateOf(0f)        // 0..1 sedang mengetik / membaca fokus: ornamen meredup dan melambat
    var pop by mutableFloatStateOf(0f)         // ketukan: mulai 1 lalu berayun mengendap ke 0 (pegas kurang teredam); asal di (popX, popY)
    var popX = 0f
    var popY = 0f
    var tapN by mutableIntStateOf(0)           // bertambah tiap ketukan
    var tapAt = 0L                             // uptimeMillis ketukan terakhir (umur riak / percikan / dentuman)
    var down by mutableStateOf(false)
    var downAt = 0L
    var x0 = 0f
    var y0 = 0f
    var moved = 0f
}

/** Membaca sentuhan di fase Initial tanpa mengonsumsi apa pun, jadi gestur editor tidak terganggu. Ketukan singkat (tanpa geser) memicu [OrnFx.pop]. */
fun Modifier.ornTouch(fx: OrnFx): Modifier = pointerInput(fx) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.pressed }
            if (c == null) {
                if (fx.down) {
                    fx.down = false
                    val up = e.changes.firstOrNull()?.uptimeMillis ?: fx.downAt
                    if (up - fx.downAt < 300L && fx.moved < 24f) { fx.popX = fx.tx; fx.popY = fx.ty; fx.tapAt = SystemClock.uptimeMillis(); fx.tapN++ }
                }
            } else {
                if (!fx.down) { fx.downAt = c.uptimeMillis; fx.x0 = c.position.x; fx.y0 = c.position.y; fx.moved = 0f; fx.down = true }
                fx.tx = c.position.x; fx.ty = c.position.y
                fx.moved = max(fx.moved, hypot(c.position.x - fx.x0, c.position.y - fx.y0))
            }
        }
    }
}

/**
 * Penggerak sinyal. Semuanya pegas fisik (bukan interpolasi eksponensial): jari, tekan, dan tahan sedikit melampaui target lalu
 * mengendap, seperti gerak spasial Material 3 Expressive. Hanya berputar saat ada yang bergerak, lalu tidur lagi.
 * [lifted] true selama teks sedang diangkat; [calm] true selama pengguna mengetik / keyboard terbuka.
 */
@Composable
fun OrnDriver(fx: OrnFx, lifted: () -> Boolean, on: Boolean, calm: () -> Boolean = { false }) {
    if (!on) return
    LaunchedEffect(fx) {
        var lastT = 0L
        var pv = 0f; var hv = 0f; var fvx = 0f; var fvy = 0f
        snapshotFlow { fx.down }.collectLatest {
            var idle = 0
            while (idle < 24) {
                val n = withFrameNanos { it }
                val dt = if (lastT == 0L) .016f else ((n - lastT) / 1e9f).coerceIn(.004f, .05f)
                lastT = n
                // jari baru menyentuh: mulai dari titik sentuh (pengaruhnya sendiri naik lewat press), sesudah itu mengikuti dengan pegas (zeta ~ .62)
                if (fx.down && fx.press < .02f && abs(pv) < .05f) { fx.px = fx.tx; fx.py = fx.ty; fvx = 0f; fvy = 0f }
                fvx += (230f * (fx.tx - fx.px) - 19f * fvx) * dt; fx.px += fvx * dt
                fvy += (230f * (fx.ty - fx.py) - 19f * fvy) * dt; fx.py += fvy * dt
                // tekan: pegas zeta ~ .51 (membal ~15%): menyentuh terasa "mengisi", melepas terasa "melenting"
                pv += (280f * ((if (fx.down) 1f else 0f) - fx.press) - 17f * pv) * dt
                fx.press += pv * dt
                val heldMs = if (fx.down) SystemClock.uptimeMillis() - fx.downAt else 0L
                val ht = if (fx.down && fx.moved < 24f && heldMs > 280L) 1f else 0f
                hv += (95f * (ht - fx.hold) - 12.5f * hv) * dt
                fx.hold += hv * dt
                val quiet = !fx.down && abs(fx.press) < .004f && abs(pv) < .02f && abs(fx.hold) < .004f && abs(hv) < .02f &&
                    abs(fvx) < 1f && abs(fvy) < 1f
                idle = if (quiet) idle + 1 else 0
            }
            fx.press = 0f; fx.hold = 0f; pv = 0f; hv = 0f; fvx = 0f; fvy = 0f; lastT = 0L
        }
    }
    // ketukan: pegas kurang teredam (zeta ~ .32, ~3 ayunan) dari 1 menuju 0; makin tenang saat mengetik supaya tidak mengganggu
    LaunchedEffect(fx) {
        val a = Animatable(0f)
        snapshotFlow { fx.tapN }.collectLatest { n ->
            if (n == 0) return@collectLatest
            a.snapTo(0f)
            a.animateTo(1f, spring(.32f, 260f)) { fx.pop = (1f - value) * (1f - .6f * fx.calm) }
            fx.pop = 0f
        }
    }
    LaunchedEffect(fx) {
        val a = Animatable(0f)
        snapshotFlow { lifted() }.collect { l -> a.animateTo(if (l) 1f else 0f, spring(.72f, 170f)) { fx.lift = value } }
    }
    LaunchedEffect(fx) {
        val a = Animatable(0f)
        snapshotFlow { calm() }.collectLatest { c -> a.animateTo(if (c) 1f else 0f, tween(if (c) 600 else 900, easing = FastOutSlowInEasing)) { fx.calm = value } }
    }
}

// =====================================================================================================================
// Jam + pose terakhir per catatan (kartu memakai pose terakhir editor, jadi tidak ada lompatan setelah menutup)
// =====================================================================================================================

/**
 * Jam ornamen satu catatan. Dipegang bersama oleh lapisan di belakang sampul ([OrnamentLayer]) dan lapisan di atas sampul gradasi
 * ([OrnamentCoverOverlay]) supaya keduanya selalu menggambar adegan yang persis sama (waktu, pergantian backdrop, dan transisi).
 */
@Stable
class OrnClock(val base: Float) {
    var time by mutableFloatStateOf(0f)
    var cur by mutableStateOf("")
    var prev by mutableStateOf("")
    var sw by mutableFloatStateOf(1f)
    val now: Float get() = base + time
}

object OrnPose {
    /** [v] = versi pose per catatan: hanya kartu milik catatan itu yang digambar ulang saat editornya ditutup (dulu SEMUA kartu terlihat sekaligus). */
    private class Box(@JvmField var t: Float) { val v = mutableIntStateOf(0) }
    private val m = HashMap<String, Box>()
    /** Versi global (tetap ada untuk kompatibilitas pemanggil lain). */
    var ver by mutableIntStateOf(0)
    private fun dflt(id: String): Float = (id.hashCode() and 0xFFFF) / 700f
    private fun box(id: String): Box = m[id] ?: Box(dflt(id)).also { m[id] = it }
    fun get(id: String): Float = m[id]?.t ?: dflt(id)
    /** Dipanggil tiap frame: kotak waktu dipakai ulang, jadi tidak ada alokasi (boxing Float) yang memicu GC saat catatan baru dibuka. */
    fun put(id: String, t: Float) { val b = m[id]; if (b == null) m[id] = Box(t) else b.t = t }
    /** Dibaca di dalam gambar kartu: berubah hanya saat [bump] untuk [id] yang sama. */
    fun verOf(id: String): Int = box(id).v.intValue
    fun bump(id: String) { box(id).v.intValue++; ver++ }
}

// =====================================================================================================================
// Helper matematika
// =====================================================================================================================

private fun ornMod(x: Float, m: Float): Float = ((x % m) + m) % m
private fun ornHash(i: Int, s: Int): Float {
    var x = i * 374761393 + s * 668265263
    x = (x xor (x ushr 13)) * 1274126177.toInt()
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

/** Smootherstep: kecepatan dan percepatan nol di kedua ujung, jadi perubahan bentuk tidak pernah "tersentak". */
private fun ornSmooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * c * (c * (c * 6f - 15f) + 10f)
}

/**
 * Cahaya lembut (gradien radial): memberi kedalaman tanpa tepi keras yang mengganggu bacaan.
 *
 * Dulu tiap pemanggilan membuat Brush + shader gradien baru (sampai ~25 per frame di Skyline malam). Sekarang satu gradien satuan
 * (radius 1 di titik 0,0) disimpan per warna lalu diskalakan / digeser lewat transform, dan [k] menjadi alpha kuas.
 * Hasil pikselnya sama persis: rona dan bentuk gradien tidak berubah, alpha hanya dikalikan skalar.
 */
private val GLOW_CACHE = HashMap<Long, Brush>()

private fun DrawScope.glow(c: Color, cx: Float, cy: Float, r: Float, k: Float) {
    if (k <= .002f || c.alpha <= .002f || r <= 1f) return
    val key = c.value.toLong()
    val br: Brush = GLOW_CACHE[key] ?: run {
        if (GLOW_CACHE.size > 64) GLOW_CACHE.clear()
        Brush.radialGradient(listOf(c, Color.Transparent), center = Offset.Zero, radius = 1f).also { GLOW_CACHE[key] = it }
    }
    withTransform({ translate(cx, cy); scale(r, r, Offset.Zero) }) { drawCircle(br, 1f, Offset.Zero, alpha = k.coerceIn(0f, 1f)) }
}

// =====================================================================================================================
// Bentuk Morph: fungsi radial r(theta) dinormalisasi, diinterpolasi antar bentuk lalu dihaluskan jadi kurva Bezier
// =====================================================================================================================

private const val ON = 48   // jumlah titik kurva per bentuk

private object SH {
    private fun norm(f: (Float) -> Float): FloatArray {
        val a = FloatArray(ON) { f(it * (2f * PI.toFloat() / ON)) }
        var s = 0f
        for (v in a) s += v
        val m = s / ON
        for (i in a.indices) a[i] /= m
        return a
    }

    /** Superellipse |x/a|^n + |y/b|^n = 1: n besar -> kapsul / squircle. */
    private fun sq(a: Float, b: Float, n: Float): (Float) -> Float = { th ->
        val c = abs(cos(th)) / a
        val s = abs(sin(th)) / b
        (c.pow(n) + s.pow(n)).pow(-1f / n)
    }

    val all: List<FloatArray> = listOf(
        norm { 1f + .17f * sin(3f * it + .6f) + .09f * sin(5f * it + 2f) },   // 0 amuba
        norm { 1f + .08f * cos(9f * it) },                                    // 1 cookie
        norm(sq(1f, .56f, 5f)),                                               // 2 capsule
        norm(sq(1f, 1f, 3.2f)),                                               // 3 squircle
        norm { 1f + .24f * cos(4f * it) },                                    // 4 clover
        norm { 1f + .20f * sin(2f * it + 1f) + .07f * sin(7f * it) },          // 5 pebble
        norm { 1f + .17f * cos(5f * it) },                                    // 6 bunga
        norm { 1f + .07f * cos(8f * it) + .04f * cos(12f * it) },             // 7 sunny
    )
}

private val SP = Path()
private val SP2 = Path()
private val PT = FloatArray(ON * 2)

// Tabel sudut dihitung sekali. Dulu tiap frame: 6 bentuk x 48 titik x (cos + sin + sin riak) = ~860 pemanggilan trigonometri.
private val TC = FloatArray(ON) { cos(it * (2f * PI.toFloat() / ON)) }
private val TS = FloatArray(ON) { sin(it * (2f * PI.toFloat() / ON)) }
private val T3C = FloatArray(ON) { cos(3f * it * (2f * PI.toFloat() / ON)) }
private val T3S = FloatArray(ON) { sin(3f * it * (2f * PI.toFloat() / ON)) }

/**
 * Bentuk = campuran a -> b (m), lalu sedikit ke c (mc), ditambah riak halus (wob) supaya tepinya terasa hidup.
 * Rotasi dan riak memakai rumus penjumlahan sudut + tabel, jadi isi loop hanya perkalian / penjumlahan (tanpa sin / cos).
 */
private fun blobPath(
    p: Path, cx: Float, cy: Float, r0: Float, rot: Float, sx: Float, sy: Float,
    a: FloatArray, b: FloatArray, m: Float, c: FloatArray, mc: Float, wob: Float, wph: Float
) {
    val cr = cos(rot); val sr = sin(rot)
    val cw = cos(wph); val sw = sin(wph)
    for (i in 0 until ON) {
        val base = a[i] + (b[i] - a[i]) * m
        val ripple = T3S[i] * cw + T3C[i] * sw
        val r = r0 * (base + (c[i] - base) * mc) * (1f + wob * ripple)
        val ct = TC[i] * cr - TS[i] * sr
        val st = TS[i] * cr + TC[i] * sr
        PT[2 * i] = cx + ct * r * sx
        PT[2 * i + 1] = cy + st * r * sy
    }
    p.reset()
    p.moveTo(PT[0], PT[1])
    for (i in 0 until ON) {
        val i0 = (i - 1 + ON) % ON; val i2 = (i + 1) % ON; val i3 = (i + 2) % ON
        p.cubicTo(
            PT[2 * i] + (PT[2 * i2] - PT[2 * i0]) / 6f, PT[2 * i + 1] + (PT[2 * i2 + 1] - PT[2 * i0 + 1]) / 6f,
            PT[2 * i2] - (PT[2 * i3] - PT[2 * i]) / 6f, PT[2 * i2 + 1] - (PT[2 * i3 + 1] - PT[2 * i + 1]) / 6f,
            PT[2 * i2], PT[2 * i2 + 1]
        )
    }
    p.close()
}

/** Tiap blob berjalan melewati rantai tiga bentuk (a -> b -> c -> a) dengan easing lembut. */
private class Blob(val x: Float, val y: Float, val s: Float, val a: Int, val b: Int, val c: Int, val col: Int, val depth: Float, val spd: Float, val rot: Float)

private val BLOBS = listOf(
    Blob(.88f, .10f, .46f, 0, 3, 6, 0, .10f, .22f, .15f),
    Blob(.10f, .38f, .30f, 2, 1, 5, 1, .22f, .30f, -.2f),
    Blob(.80f, .62f, .36f, 4, 0, 7, 2, .16f, .18f, .3f),
    Blob(.18f, .88f, .42f, 3, 5, 6, 3, .28f, .26f, -.1f),
    Blob(.52f, .28f, .16f, 1, 4, 7, 0, .38f, .40f, .5f),
    Blob(.95f, .95f, .22f, 5, 2, 1, 1, .34f, .34f, .2f),
).sortedBy { it.depth }      // yang jauh digambar lebih dulu

private fun DrawScope.drawMorphScene(P: OrnPaint, w: Float, h: Float, t: Float, fx: OrnFx?) {
    val u = w
    val lf = fx?.lift ?: 0f
    val pop = fx?.pop ?: 0f
    val popX = fx?.popX ?: 0f; val popY = fx?.popY ?: 0f

    for (i in BLOBS.indices) {
        val b = BLOBS[i]
        // napas: pelan dan tipis supaya tidak menarik mata dari tulisan
        val breathe = .015f * sin(t * b.spd * .6f + i)
        val r0 = b.s * u * .8f * (1f + .06f * lf + breathe)
        val per = h + 3f * r0
        // melayang pelan: satu gelombang per sumbu, amplitudo kecil
        val cx0 = b.x * u + sin(t * b.spd * .45f + i) * u * .028f
        val cy0 = b.y * per - 1.5f * r0 + sin(t * b.spd * .33f + i * 1.7f) * u * .028f
        var cx = cx0; var cy = cy0
        if (cy + r0 * 1.7f < 0f || cy - r0 * 1.7f > h) continue

        // Tidak ada respons menghindar / tertarik ke jari: latar tetap tenang saat disentuh. Hanya saat teks diangkat bentuk merenggang pelan,
        // dan ketukan memberi "mekar" lembut yang berayun kembali.
        var ox = 0f; var oy = 0f
        if (lf > .001f) { ox += (cx - u / 2f) / u * lf * u * .04f; oy += (cy - h / 2f) / u * lf * u * .04f }
        var pk = 0f
        if (abs(pop) > .003f) {
            val dx = cx - popX; val dy = cy - popY
            val d = hypot(dx, dy) + 1f
            pk = pop * exp(-(d * d) / (u * u * .30f))
            ox += dx / d * pk * u * .03f; oy += dy / d * pk * u * .03f
        }

        // perubahan bentuk: berputar terus a -> b -> c -> a. Tiap bentuk ditahan sebentar, melesat lembut ke bentuk berikutnya, lalu
        // mengendap seperti pegas (sedikit melampaui lalu kembali). Saat melesat badan meregang-memipih (squash & stretch).
        val ph = t * (.07f + b.spd * .20f) + i * 1.7f
        val seg = ornMod(ph, 3f)
        val k0 = seg.toInt().coerceIn(0, 2)
        val sf = seg - k0
        val g1 = ((sf - .18f) / .42f).coerceIn(0f, 1f)
        val st = ((sf - .60f) / .40f).coerceIn(0f, 1f)
        val ring = if (sf > .60f) .16f * exp(-5f * st) * sin(st * 13f) else 0f
        val f = ornSmooth(g1) + ring
        val stretch = sin(g1 * PI.toFloat()) * (1f - st) + ring * 2.2f
        val sa = chainOf(b, k0); val sb = chainOf(b, (k0 + 1) % 3)
        val mc = (lf * .25f + abs(pk) * .22f).coerceIn(0f, .5f)
        val rot = b.rot + t * b.spd * .12f + .10f * sin(t * b.spd * .55f + i * 2.1f)
        val br = t * b.spd * 1.1f + i * 1.3f
        val dirS = if (i % 2 == 0) 1f else -1f
        val sx = 1f + .035f * sin(br) + .10f * stretch * dirS
        val sy = 1f - .035f * sin(br) - .10f * stretch * dirS

        val col = P.pick(b.col)
        val fill = col.copy(alpha = col.alpha * (.72f + .28f * (b.depth / .38f)))
        blobPath(SP, cx + ox, cy + oy, r0 * (1f + .06f * pk), rot, sx, sy, SH.all[sa], SH.all[sb], f, SH.all[6], mc, .034f, t * (.5f + b.spd * 1.4f) + i)
        drawPath(SP, fill)
        // bentuk dalam: lebih terang, berotasi berlawanan dan berganti bentuk satu langkah di depan, memberi kedalaman berlapis
        if (b.s >= .3f) {
            val sa2 = chainOf(b, (k0 + 1) % 3); val sb2 = chainOf(b, (k0 + 2) % 3)
            blobPath(
                SP, cx + ox - r0 * .10f, cy + oy - r0 * .08f, r0 * .54f * (1f + .05f * sin(br * 1.3f)), -rot * 1.4f, 1f, 1f,
                SH.all[sa2], SH.all[sb2], f, SH.all[6], 0f, .05f, -t * b.spd * 1.1f + i
            )
            drawPath(SP, mixc(col, P.paper, .30f).copy(alpha = col.alpha * .50f))
        }
    }
}

private fun chainOf(b: Blob, k: Int): Int = when (k) { 0 -> b.a; 1 -> b.b; else -> b.c }

// =====================================================================================================================
// Perkakas gambar bersama (dipakai semua adegan berdetail)
// =====================================================================================================================

private val SP3 = Path()
private val SP4 = Path()
private val RX = FloatArray(40)
private val RY = FloatArray(40)
private val RA = FloatArray(40)
private val RW = FloatArray(40)
private val LX = FloatArray(40)
private val LY = FloatArray(40)
private val QX = FloatArray(40)
private val QY = FloatArray(40)

private fun Color.al(k: Float): Color = copy(alpha = (alpha * k).coerceIn(0f, 1f))

/** Campur dua warna palet; alpha selalu milik [a] (supaya transisi backdrop tidak membuat warna campuran lebih pekat dari aslinya). */
private fun mixc(a: Color, b: Color, t: Float): Color = lerpC(a, b, t.coerceIn(0f, 1f)).copy(alpha = a.alpha)

/** Warna kertas dengan alpha [k], ikut meredup saat backdrop pudar. */
private fun OrnPaint.pp(k: Float): Color = paper.copy(alpha = (k * amt).coerceIn(0f, 1f))

/** Detik sejak ketukan terakhir (99 = belum pernah / pose diam). */
private fun tapAge(fx: OrnFx?): Float = if (fx == null || fx.tapN == 0) 99f else (SystemClock.uptimeMillis() - fx.tapAt) / 1000f

private fun fract(x: Float): Float = ornMod(x, 1f)

/** Geseran sumbu-x akibat jari menekan (-.6..+.6): lapisan dekat bergeser ke satu arah, lapisan jauh ke arah sebaliknya = parallax sentuh. */
private fun parX(fx: OrnFx?, u: Float): Float =
    if (fx == null) 0f else ((fx.px - u / 2f) / u).coerceIn(-.6f, .6f) * fx.press.coerceIn(0f, 1.1f)

/**
 * Pita meruncing sepanjang titik (RX, RY)[0..n]. Lebar w0 di pangkal -> w1 di ujung, atau tiap titik dari RW bila [useRW].
 * Sisi kiri / kanan tiap titik tersimpan di LX, LY / QX, QY (dipakai aliran lahar untuk mewarnai per ruas).
 */
private fun ribbon(p: Path, n: Int, w0: Float, w1: Float, useRW: Boolean = false) {
    p.reset()
    for (k in 0..n) {
        val a = if (k == 0) 0 else k - 1
        val b = if (k == n) n else k + 1
        var dx = RX[b] - RX[a]; var dy = RY[b] - RY[a]
        val l = hypot(dx, dy).coerceAtLeast(1e-4f)
        dx /= l; dy /= l
        val wd = (if (useRW) RW[k] else w0 + (w1 - w0) * k / n) * .5f
        LX[k] = RX[k] - dy * wd; LY[k] = RY[k] + dx * wd
        QX[k] = RX[k] + dy * wd; QY[k] = RY[k] - dx * wd
        if (k == 0) p.moveTo(LX[k], LY[k]) else p.lineTo(LX[k], LY[k])
    }
    for (k in n downTo 0) p.lineTo(QX[k], QY[k])
    p.close()
}

/** Separuh daun (side = +1 / -1) dengan tulang daun yang melengkung (bend). Dua separuh = satu daun dua nada. */
private fun leaf2(p: Path, x: Float, y: Float, ang: Float, len: Float, wid: Float, side: Float, bend: Float, reset: Boolean = true) {
    val ca = cos(ang); val sa = sin(ang)
    val mx = x + ca * len * .5f - sa * bend * len
    val my = y + sa * len * .5f + ca * bend * len
    if (reset) p.reset()
    p.moveTo(x, y)
    p.quadraticBezierTo(mx - sa * wid * side, my + ca * wid * side, x + ca * len, y + sa * len)
    p.quadraticBezierTo(mx, my, x, y)
    p.close()
}

/** Daun dua nada: separuh gelap + separuh kena cahaya + tulang daun. */
private fun DrawScope.leafArt(P: OrnPaint, col: Color, x: Float, y: Float, ang: Float, len: Float, wid: Float, side: Float, vein: Boolean = true) {
    val bend = .07f * side
    leaf2(SP, x, y, ang, len, wid, 1f, bend)
    drawPath(SP, col)
    leaf2(SP, x, y, ang, len, wid, -1f, bend)
    drawPath(SP, mixc(col, P.paper, .42f).al(.92f))
    if (vein && len > size.width * .045f) {
        val ca = cos(ang); val sa = sin(ang)
        SP3.reset(); SP3.moveTo(x, y)
        SP3.quadraticBezierTo(x + ca * len * .5f - sa * bend * len, y + sa * len * .5f + ca * bend * len, x + ca * len * .92f, y + sa * len * .92f)
        drawPath(SP3, P.paper.copy(alpha = col.alpha * .42f), style = Stroke(size.width * .003f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.birdWing(col: Color, bx: Float, by: Float, bs: Float, fl: Float, wd: Float) {
    SP2.reset()
    SP2.moveTo(bx - bs, by - bs * .5f * fl)
    SP2.quadraticBezierTo(bx - bs * .35f, by + bs * .05f, bx, by + bs * .25f)
    SP2.quadraticBezierTo(bx + bs * .35f, by + bs * .05f, bx + bs, by - bs * .5f * fl)
    drawPath(SP2, col, style = Stroke(wd, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// =====================================================================================================================
// Canopy: ranting berkulit, daun dua nada, sulur, berkas cahaya berdebu, kupu-kupu, daun jatuh, dan kunang-kunang
// =====================================================================================================================

// Daun dikumpulkan per warna (4 warna x 2 nada) + satu jalur tulang daun + satu jalur ranting kecil, lalu digambar sekali per cabang.
// Dulu tiap daun = 3 pemanggilan gambar; sekarang satu cabang penuh daun = sekitar 10.
private val LP = Array(8) { Path() }
private val LV = Path()
private val TW = Path()

private fun leafAdd(idx: Int, x: Float, y: Float, ang: Float, len: Float, wid: Float, side: Float, vein: Boolean) {
    val bend = .07f * side
    leaf2(LP[idx], x, y, ang, len, wid, 1f, bend, false)
    leaf2(LP[4 + idx], x, y, ang, len, wid, -1f, bend, false)
    if (vein) {
        val ca = cos(ang); val sa = sin(ang)
        LV.moveTo(x, y)
        LV.quadraticBezierTo(x + ca * len * .5f - sa * bend * len, y + sa * len * .5f + ca * bend * len, x + ca * len * .92f, y + sa * len * .92f)
    }
}

private fun leafBatchReset() { for (p in LP) p.reset(); LV.reset(); TW.reset() }

private fun DrawScope.leafBatchDraw(P: OrnPaint, fade: Float, veinW: Float, twigW: Float) {
    for (i in 0 until 4) {
        val col = P.pick(i).al(fade)
        drawPath(LP[i], col)
        drawPath(LP[4 + i], mixc(col, P.paper, .42f).al(.92f))
    }
    drawPath(LV, P.pp(.42f * fade), style = Stroke(veinW, cap = StrokeCap.Round))
    drawPath(TW, P.ink.al(.8f * fade), style = Stroke(twigW, cap = StrokeCap.Round))
}

private fun DrawScope.branch(
    P: OrnPaint, ox: Float, oy: Float, base: Float, len: Float, n: Int, curve: Float, sway: Float,
    t: Float, ph: Float, leaf: Float, flutter: Float, seed: Int, fade: Float = 1f, calm: Float = 0f
) {
    val u = size.width
    RX[0] = ox; RY[0] = oy; RA[0] = base
    val seg = len / n
    var x = ox; var y = oy
    for (k in 1..n) {
        val f = k.toFloat() / n
        // ujung bergoyang lebih jauh dari pangkal, ditambah gelombang kecil yang merambat sepanjang ranting
        val a = base + curve * k + sway * f * f + .045f * f * sin(t * .9f + ph + k * .55f)
        x += cos(a) * seg; y += sin(a) * seg
        RX[k] = x; RY[k] = y; RA[k] = a
    }
    // kulit kayu: pita meruncing + garis terang di tengahnya
    val bw = u * .017f * (.55f + .45f * fade)
    ribbon(SP2, n, bw, bw * .16f)
    drawPath(SP2, P.ink.al(fade))
    SP3.reset(); SP3.moveTo(RX[0], RY[0])
    for (k in 1..n) SP3.lineTo(RX[k], RY[k])
    drawPath(SP3, mixc(P.ink, P.paper, .55f).al(.5f * fade), style = Stroke(bw * .2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val flK = 1f - .5f * calm
    leafBatchReset()
    for (k in 2..n) {
        val f = k.toFloat() / n
        val side = if (k % 2 == 0) 1f else -1f
        val ls = leaf * (1.05f - .6f * f)
        val fl = sin(t * 1.5f + ph + k * .9f) * .14f * (1f + flutter) * flK
        val la = RA[k] + side * (.95f - .25f * f) + fl
        if (fade > .9f && k < n && k % 2 == 1) {
            val ta = RA[k] + side * 1.05f
            TW.moveTo(RX[k], RY[k]); TW.lineTo(RX[k] + cos(ta) * ls * .55f, RY[k] + sin(ta) * ls * .55f)
        }
        val round = (k + seed) % 3 == 0
        leafAdd((k + seed) % 4, RX[k], RY[k], la, ls, if (round) ls * 1.2f else ls * .9f, side, ls > u * .045f)
        if (fade > .9f && k % 3 == 0 && k < n) {
            leafAdd((k + seed + 2) % 4, RX[k], RY[k], RA[k] - side * (1.05f - .3f * f) - fl, ls * .8f, ls * .7f, -side, ls * .8f > u * .045f)
        }
    }
    leafAdd((n + seed) % 4, RX[n], RY[n], RA[n] + sin(t * 1.6f + ph) * .10f * flK, leaf * .6f, leaf * .5f, 1f, leaf * .6f > u * .045f)
    leafBatchDraw(P, fade, u * .003f, bw * .32f)
    // buah kecil di ujung (hanya ranting depan)
    if (fade > .9f && seed % 2 == 0) {
        val bx = RX[n - 1]; val by = RY[n - 1]
        drawCircle(P.c, u * .008f, Offset(bx + u * .012f, by + u * .012f))
        drawCircle(P.pp(.5f), u * .0028f, Offset(bx + u * .010f, by + u * .009f))
    }
}

/** Sulur menggantung dari tepi atas, ujungnya melambai mengikuti angin. */
private fun DrawScope.vine(P: OrnPaint, x0: Float, len: Float, t: Float, ph: Float, wind: Float, seed: Int) {
    val u = size.width
    val n = 9
    for (k in 0..n) {
        val f = k.toFloat() / n
        RX[k] = x0 + (sin(t * .55f + ph + f * 2.4f) * .035f + wind * 1.6f) * u * f
        RY[k] = -u * .02f + len * f
    }
    SP3.reset(); SP3.moveTo(RX[0], RY[0])
    for (k in 1..n) SP3.lineTo(RX[k], RY[k])
    drawPath(SP3, P.ink.al(.7f), style = Stroke(u * .0042f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    leafBatchReset()
    for (k in 2..n) {
        val side = if (k % 2 == 0) 1f else -1f
        val a = PI.toFloat() / 2f + side * (1.0f - .3f * k / n) + sin(t * 1.3f + ph + k) * .12f
        val ls = u * (.05f - .022f * k / n)
        leafAdd((k + seed) % 4, RX[k], RY[k], a, ls, ls, side, false)
    }
    leafBatchDraw(P, 1f, u * .003f, u * .003f)
}

private fun DrawScope.butterfly(P: OrnPaint, bx: Float, by: Float, ws: Float, t: Float, ang: Float) {
    val f = .22f + .78f * abs(sin(t * 7.5f))
    withTransform({ rotate(ang * 57.3f, Offset(bx, by)) }) {
        for (sd in 0..1) {
            val s = if (sd == 0) -1f else 1f
            SP2.reset(); SP2.moveTo(bx, by)
            SP2.cubicTo(bx + s * ws * f, by - ws * 2.4f, bx + s * ws * 2.8f * f, by - ws * 1.5f, bx + s * ws * 2.2f * f, by - ws * .1f)
            SP2.quadraticBezierTo(bx + s * ws * .8f * f, by + ws * .5f, bx, by)
            drawPath(SP2, P.c)
            SP2.reset(); SP2.moveTo(bx, by + ws * .1f)
            SP2.quadraticBezierTo(bx + s * ws * 2f * f, by + ws * .3f, bx + s * ws * 1.1f * f, by + ws * 1.6f)
            SP2.quadraticBezierTo(bx + s * ws * .3f, by + ws, bx, by + ws * .1f)
            drawPath(SP2, P.d)
            drawCircle(P.pp(.55f), ws * .28f, Offset(bx + s * ws * 1.6f * f, by - ws * .8f))
        }
        drawLine(P.ink, Offset(bx, by - ws * .6f), Offset(bx, by + ws * 1.1f), strokeWidth = ws * .34f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawCanopyScene(P: OrnPaint, w: Float, h: Float, t: Float, fx: OrnFx?) {
    val u = w
    val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val pop = fx?.pop ?: 0f; val popX = fx?.popX ?: 0f
    val tx = fx?.px ?: 0f
    val calm = fx?.calm ?: 0f
    val par = parX(fx, u)
    // angin bersama: dua gelombang lambat yang tak sebanding, jadi embusan tidak pernah berulang persis
    val wind = (.040f * sin(t * .21f) + .026f * sin(t * .53f + 1.3f)) * (1f - .5f * calm)
    fun kick(ox: Float): Float = if (abs(pop) > .003f) ((ox - popX) / u).coerceIn(-1.2f, 1.2f) * pop * .34f else 0f
    val flAdd = abs(pop) * 1.6f
    fun gust(ox: Float): Float = if (pr > .01f) ((ox - tx) / u).coerceIn(-1.2f, 1.2f) * pr * .18f else 0f
    val leafK = 1f + .05f * hd

    // berkas cahaya: lebar lembut + inti sempit, bernapas pelan, memudar ke bawah
    for (i in 0 until 3) {
        val x0 = u * (.52f + .22f * i) + sin(t * .12f + i * 2f) * u * .04f
        val wd = u * (.07f + .03f * i)
        val sk = u * .55f
        val br = .5f + .5f * sin(t * .21f + i * 1.9f)
        for (layer in 0..1) {
            val ww = if (layer == 0) wd else wd * .38f
            val xx = x0 + (wd - ww) * .5f
            SP.reset(); SP.moveTo(xx, 0f); SP.lineTo(xx + ww, 0f); SP.lineTo(xx + ww - sk, h); SP.lineTo(xx - sk, h); SP.close()
            val aa = (if (layer == 0) .12f + .12f * br else .10f + .10f * br) * (1f - .6f * calm)
            drawPath(SP, Brush.verticalGradient(listOf(P.d.al(aa), Color.Transparent), startY = 0f, endY = h))
        }
    }
    // debu cahaya
    for (i in 0 until 6) {
        val x = ornHash(i, 91) * u + sin(t * .25f + i * 1.7f) * u * .03f
        val y = ornMod(ornHash(i, 92) * h + t * u * .006f * (.5f + ornHash(i, 93)), h)
        val tw = .5f + .5f * sin(t * .8f + i * 2f)
        drawCircle(P.d.al(.20f + .30f * tw), u * .0035f * (.6f + ornHash(i, 94)), Offset(x, y))
    }

    // daun jatuh: berputar 3D (lebar daun berdenyut)
    for (i in 0 until 6) {
        val sz = u * (.035f + .02f * ornHash(i, 1)) * leafK
        val per = h + sz * 6f
        val y0 = ornHash(i, 3) * per + t * u * (.010f + .008f * ornHash(i, 4))
        val y = ornMod(y0, per) - sz * 3f
        val x = ornHash(i, 5) * u + sin(t * .5f + i * 2f) * u * .06f + wind * u * .6f + lf * u * .02f * (ornHash(i, 7) - .5f)
        val flip = abs(cos(t * (.45f + ornHash(i, 6) * .5f) + i))
        leafArt(P, P.pick(i + 1), x, y, t * (.35f + ornHash(i, 6) * .4f) + i + wind * 3f, sz * 2f, sz * .9f * (.28f + .72f * flip), 1f, false)
    }

    val sw = { ph: Float, amp: Float -> sin(t * .45f + ph) * amp + wind + lf * .12f }
    // lapis jauh: lebih kecil dan pudar; bergeser ke arah berlawanan saat disentuh
    withTransform({ translate(-par * u * .02f, 0f) }) {
        val f1 = u * .46f
        branch(P, f1, -u * .02f, 1.95f, u * .34f, 6, .05f, sw(2.4f, .08f) + gust(f1) + kick(f1), t, 2.4f, u * .07f * leafK, lf * 1.5f + flAdd, 4, .55f, calm)
        val f2 = u * .64f
        branch(P, f2, h + u * .02f, -1.35f, u * .30f, 5, -.05f, sw(5.2f, .09f) + gust(f2) + kick(f2), t, 5.2f, u * .065f * leafK, lf * 1.5f + flAdd, 5, .5f, calm)
    }
    // sulur
    vine(P, u * .20f, u * .26f, t, 1.1f, wind, 3)
    vine(P, u * .36f, u * .40f, t, 3.6f, wind, 5)
    // lapis dekat (besar)
    withTransform({ translate(par * u * .03f, 0f) }) {
        val o1 = u * 1.03f
        branch(P, o1, -u * .03f, 2.30f, u * .85f, 9, -.07f, sw(0f, .10f) + gust(o1) + kick(o1), t, 0f, u * .17f * leafK, lf * 2f + flAdd, 0, 1f, calm)
        val o2 = -u * .03f
        branch(P, o2, -u * .02f, .85f, u * .5f, 7, .07f, sw(1.7f, .12f) + gust(o2) + kick(o2), t, 1.7f, u * .12f * leafK, lf * 2f + flAdd, 1, 1f, calm)
        val o3 = -u * .04f
        branch(P, o3, h + u * .03f, -1.15f, u * .65f, 8, -.05f, sw(3.1f, .09f) + gust(o3) + kick(o3), t, 3.1f, u * .14f * leafK, lf * 2f + flAdd, 2, 1f, calm)
        val o4 = u * 1.04f
        branch(P, o4, h + u * .02f, -2.15f, u * .42f, 6, .06f, sw(4.6f, .11f) + gust(o4) + kick(o4), t, 4.6f, u * .10f * leafK, lf * 2f + flAdd, 3, 1f, calm)
    }

    // kupu-kupu: melintas melingkar-lingkar, mengepak cepat, miring mengikuti arah terbang
    run {
        val bx = u * (.50f + .30f * sin(t * .09f + 1f) + .06f * sin(t * .31f))
        val by = h * (.40f + .16f * sin(t * .07f)) + sin(t * 1.3f) * u * .02f
        val vx = cos(t * .09f + 1f) * .3f * .09f + cos(t * .31f) * .06f * .31f
        butterfly(P, bx, by, u * .016f, t, (vx * 3f).coerceIn(-.5f, .5f))
    }

    // kunang-kunang: titik cahaya kecil yang naik pelan dan berkedip lembut
    for (i in 0 until 7) {
        val rr = u * (.006f + .006f * ornHash(i, 8))
        val per = h + u * .2f
        val y = ornMod(ornHash(i, 9) * per - t * u * (.008f + .006f * ornHash(i, 10)), per) - u * .1f
        val x = ornHash(i, 12) * u + sin(t * .3f + i * 1.9f) * u * .05f + par * u * .04f
        val tw = .5f + .5f * sin(t * (.4f + ornHash(i, 13) * .6f) + i * 3f)
        val col = P.pick(i + 2)
        drawCircle(col.copy(alpha = col.alpha * (.10f + .10f * tw)), rr * 3.2f, Offset(x, y))
        drawCircle(col.copy(alpha = col.alpha * (.45f + .35f * tw)), rr, Offset(x, y))
    }
}

// =====================================================================================================================
// Skyline: matahari / bulan berhalo, awan kumulus berlapis, kota tiga lapis dengan perspektif satu titik hilang di lapis terdepan,
// balon bergaris, pesawat, jalan dengan lampu dan mobil
// =====================================================================================================================

/** Iterasi gedung satu lapis (jauh = 0 ... dekat = 2) pada ubin berulang; inline supaya tanpa alokasi per frame. */
private inline fun eachBldg(L: Int, u: Float, h: Float, block: (i: Int, bx: Float, bw: Float, bh: Float, roof: Int) -> Unit) {
    val tile = u * 1.5f
    var x = -u * .05f
    var i = 0
    while (x < tile) {
        val bw = u * (.075f + .075f * ornHash(i, 21 + L))
        val hb = when (L) { 0 -> .20f + .30f * ornHash(i, 31 + L); 1 -> .13f + .24f * ornHash(i, 31 + L); else -> .08f + .22f * ornHash(i, 31 + L) }
        val bh = min(u * hb, h * .62f)
        val roof = (ornHash(i, 41 + L) * 4f).toInt().coerceIn(0, 3)
        for (rep in 0..1) {
            val bx = ornMod(x, tile) - (if (rep == 1) tile else 0f)
            if (bx > u || bx + bw < 0f) continue
            block(i, bx, bw, bh, roof)
        }
        x += bw + u * .008f
        i++
    }
}

/** Bulan sabit dalam koordinat satuan (radius 1 di 0,0): piringan dikurangi lingkaran (.45, -.2) berjari-jari .86. */
private val MOON_CRESCENT: Path by lazy {
    val disc = Path().apply { addOval(Rect(Offset.Zero, 1f)) }
    val cut = Path().apply { addOval(Rect(Offset(.45f, -.2f), .86f)) }
    Path().apply { op(disc, cut, PathOperation.Difference) }
}

private fun DrawScope.drawSkylineScene(P: OrnPaint, w: Float, h: Float, t: Float, fx: OrnFx?) {
    val u = w
    val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val pop = fx?.pop ?: 0f; val popX = fx?.popX ?: 0f
    val tx = fx?.px ?: 0f
    val calm = fx?.calm ?: 0f
    val par = parX(fx, u)
    val dk = P.dark
    val litT = if (dk) .42f else .6f

    // langit: pita warna turun dari atas
    drawRect(
        Brush.verticalGradient(listOf(P.b.al(.38f), P.d.al(.10f), Color.Transparent), startY = 0f, endY = h * .62f),
        Offset.Zero, Size(w, h * .62f)
    )

    val sunR = u * .11f * (1f + .05f * hd + .08f * lf + .10f * pop)
    val sx = u * (.76f + .025f * sin(t * .05f)) - par * u * .01f
    val sy = u * .19f + sin(t * .35f) * u * .006f

    // bintang: siang hanya 5 kilau samar; malam 24 bintang yang berkedip bergantian
    val sn = if (dk) 24 else 5
    val starC = if (dk) P.ink else P.d
    for (i in 0 until sn) {
        val kx = u * (.05f + .90f * ornHash(i, 71)); val ky = u * (.03f + .34f * ornHash(i, 72))
        if (dk && hypot(kx - sx, ky - sy) < sunR * 2.4f) continue
        val tw = sin(t * (.55f + ornHash(i, 73) * .5f) + i * 2.1f)
        var a = (tw * .5f + .5f).let { it * it }
        if (dk) a = .3f + .7f * a else if (a < .04f) continue
        val r = u * (.006f + .006f * ornHash(i, 74)) * (.6f + .6f * a)
        val c = starC.copy(alpha = starC.alpha * a * (if (dk) 1f else .8f) * (1f - .6f * calm))
        if (dk && i >= 9) { drawCircle(c, r * .45f, Offset(kx, ky)); continue }
        drawLine(c, Offset(kx - r, ky), Offset(kx + r, ky), strokeWidth = u * .0028f, cap = StrokeCap.Round)
        drawLine(c, Offset(kx, ky - r), Offset(kx, ky + r), strokeWidth = u * .0028f, cap = StrokeCap.Round)
    }
    // bintang jatuh (malam): satu goresan tiap ~19 detik
    if (dk) {
        val k = ornMod(t + 7f, 19f) / 19f
        if (k < .05f) {
            val f = k / .05f
            val hx = u * (.95f - .55f * f); val hy = u * (.04f + .26f * f)
            val tl = u * .20f * sin(f * PI.toFloat())
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, P.ink.al(.9f)), start = Offset(hx + tl, hy - tl * .47f), end = Offset(hx, hy)),
                Offset(hx + tl, hy - tl * .47f), Offset(hx, hy), strokeWidth = u * .004f, cap = StrokeCap.Round
            )
        }
    }

    if (dk) {
        // bulan sabit: halo bertingkat, sabit, dan kawah tipis
        val mr = sunR * 1.2f
        glow(P.ink, sx, sy, mr * 3.2f, .20f)
        drawCircle(P.ink.al(.10f), mr * 1.9f, Offset(sx, sy))
        drawCircle(P.ink.al(.12f), mr * 1.4f, Offset(sx, sy))
        // sabit: bentuk satuan dihitung sekali (piringan dikurangi lingkaran pemotong), lalu diskalakan. Dulu clipPath per frame.
        // Kedua kawah letaknya sepenuhnya di luar lingkaran pemotong, jadi digambar langsung tanpa klip dengan hasil yang sama.
        withTransform({ translate(sx, sy); scale(mr, mr, Offset.Zero) }) { drawPath(MOON_CRESCENT, P.ink) }
        drawCircle(P.pp(.25f), mr * .12f, Offset(sx - mr * .55f, sy + mr * .1f))
        drawCircle(P.pp(.2f), mr * .08f, Offset(sx - mr * .3f, sy + mr * .45f))
    } else {
        // matahari siang: sinar garis bergantian panjang-pendek yang berputar pelan + piringan bergradasi. Tanpa halo / cahaya menyebar.
        val rot = t * .12f + lf * .6f + pop * .5f
        for (k in 0 until 14) {
            val a = rot + k * (2f * PI.toFloat() / 14f)
            val lng = if (k % 2 == 0) 1f else .62f
            val r1 = sunR * 1.40f
            val r2 = sunR * (1.40f + (.26f + .07f * sin(t * .9f + k * 1.3f)) * lng)
            drawLine(P.b, Offset(sx + cos(a) * r1, sy + sin(a) * r1), Offset(sx + cos(a) * r2, sy + sin(a) * r2), strokeWidth = sunR * .15f, cap = StrokeCap.Round)
        }
        drawCircle(Brush.radialGradient(listOf(mixc(P.d, P.paper, .45f), P.d), center = Offset(sx - sunR * .2f, sy - sunR * .2f), radius = sunR * 1.3f), sunR, Offset(sx, sy))
    }

    // burung: kawanan V, kepakan bergantian
    if (!dk) run {
        val span = u * 1.8f
        val hx = ornMod(ornHash(0, 61) * span + t * u * .022f, span) - u * .3f
        val hy = u * .17f + sin(t * .4f) * u * .012f
        for (i in 0 until 5) {
            val row = (i + 1) / 2; val sd = if (i % 2 == 0) -1f else 1f
            val bx = hx - row * u * .035f
            val by = hy + sd * row * u * .017f + sin(t * .6f + i * 1.3f) * u * .004f
            val fl = sin(t * (3.4f + i * .15f) + i * 1.1f)
            birdWing(P.ink.al(.75f - .5f * calm), bx, by, u * (.017f - .0015f * row), fl, u * .0045f)
        }
    }

    // balon udara: gores bergaris, tali, keranjang; bergoyang dan melayang pelan
    run {
        val span = u * 2.6f
        val bx = ornMod(u * .5f + t * u * .0085f, span) - u * .8f - par * u * .03f
        val by = u * .36f + sin(t * .27f) * u * .018f - lf * u * .02f - pop * u * .012f
        val br = u * .052f
        val sway = sin(t * .5f) * .06f
        withTransform({ rotate(sway * 57.3f, Offset(bx, by + br * 2f)) }) {
            SP2.reset(); SP2.moveTo(bx - br * .86f, by + br * .5f)
            SP2.quadraticBezierTo(bx - br * .45f, by + br * 1.2f, bx - br * .26f, by + br * 1.5f)
            SP2.lineTo(bx + br * .26f, by + br * 1.5f)
            SP2.quadraticBezierTo(bx + br * .45f, by + br * 1.2f, bx + br * .86f, by + br * .5f)
            SP2.close()
            drawPath(SP2, P.c)
            drawOval(P.c, Offset(bx - br, by - br), Size(br * 2f, br * 2.05f))
            drawOval(P.a, Offset(bx - br * .66f, by - br), Size(br * 1.32f, br * 2.05f))
            drawOval(P.c, Offset(bx - br * .30f, by - br), Size(br * .60f, br * 2.05f))
            drawOval(P.pp(.22f), Offset(bx - br * .72f, by - br * .72f), Size(br * .34f, br * .62f))
            drawLine(P.ink, Offset(bx - br * .28f, by + br * 1.5f), Offset(bx - br * .16f, by + br * 1.78f), strokeWidth = u * .0022f)
            drawLine(P.ink, Offset(bx + br * .28f, by + br * 1.5f), Offset(bx + br * .16f, by + br * 1.78f), strokeWidth = u * .0022f)
            drawRoundRect(P.ink, Offset(bx - br * .2f, by + br * 1.78f), Size(br * .4f, br * .3f), CornerRadius(br * .06f))
        }
    }

    // pesawat dengan jejak: sekali melintas tiap ~70 detik
    run {
        val per = 70f
        val k = ornMod(t + 23f, per) / per
        if (k < .55f) {
            val f = k / .55f
            val px = -u * .15f + f * u * 1.3f
            val py = u * .30f - f * u * .06f
            val tl = u * .30f
            val dx = cos(-.05f); val dy = sin(-.05f)
            val fade = sin(f * PI.toFloat()).coerceIn(0f, 1f)
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, P.b.al(.9f * fade)), start = Offset(px - dx * tl, py - dy * tl), end = Offset(px, py)),
                Offset(px - dx * tl, py - dy * tl), Offset(px, py), strokeWidth = u * .004f, cap = StrokeCap.Round
            )
            val ink = P.ink.al(fade)
            drawOval(ink, Offset(px - u * .010f, py - u * .0035f), Size(u * .022f, u * .007f))
            drawLine(ink, Offset(px - u * .002f, py), Offset(px - u * .010f, py - u * .009f), strokeWidth = u * .0032f, cap = StrokeCap.Round)
            drawLine(ink, Offset(px - u * .010f, py - u * .002f), Offset(px - u * .014f, py - u * .008f), strokeWidth = u * .0026f, cap = StrokeCap.Round)
        }
    }

    // cahaya senja di cakrawala, bernapas pelan
    run {
        val y0 = h * .50f
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, P.d.al((.16f + .05f * sin(t * .15f)) * (1f - .4f * calm))), startY = y0, endY = h),
            Offset(0f, y0), Size(w, h - y0)
        )
    }

    // ---- kota -------------------------------------------------------------------------------------------------
    val roadH = u * .05f
    val vpX = u / 2f; val vpY = h * .55f

    fun bumpOf(cxm: Float, L: Int): Float {
        val near = if (pr > .01f) exp(-((cxm - tx) * (cxm - tx)) / (u * u * .03f)) * pr else 0f
        val hop = if (abs(pop) > .003f) exp(-((cxm - popX) * (cxm - popX)) / (u * u * .05f)) * pop else 0f
        return near * u * .028f + hop * u * (.022f + .012f * L)
    }

    for (L in 0..2) {
        val col = if (L == 0) P.b else if (L == 1) P.a else P.ink
        val shade = mixc(col, P.ink, if (L == 2) .22f else .14f)
        val edge = mixc(col, P.paper, .22f)
        val yb = h - roadH + u * .012f - lf * u * .025f * (.6f + .3f * L)
        val dx0 = par * u * (if (L == 0) -.015f else if (L == 1) .0f else .02f)

        withTransform({ translate(dx0, 0f) }) {
            // lapis terdepan: sisi gedung yang menyurut ke titik hilang di tengah (perspektif satu titik)
            if (L == 2) eachBldg(2, u, h) { _, bx, bw, bh, _ ->
                val cxm = bx + bw / 2f
                val dxv = cxm - vpX
                if (abs(dxv) > u * .20f) {
                    val dep = bw * .30f * ((abs(dxv) - u * .20f) / (u * .30f)).coerceIn(.15f, 1f)
                    val ex = if (dxv < 0f) bx + bw else bx
                    val sdir = if (dxv < 0f) 1f else -1f
                    val top = yb - bh - bumpOf(cxm, L)
                    val kk = dep / abs(vpX - ex).coerceAtLeast(1f)
                    val fx2 = ex + sdir * dep
                    SP.reset(); SP.moveTo(ex, top); SP.lineTo(fx2, top + (vpY - top) * kk)
                    SP.lineTo(fx2, yb - (yb - vpY) * kk); SP.lineTo(ex, yb); SP.close()
                    drawPath(SP, shade)
                }
            }

            eachBldg(L, u, h) { i, bx, bw, bh, roof ->
                val cxm = bx + bw / 2f
                val near = if (pr > .01f) exp(-((cxm - tx) * (cxm - tx)) / (u * u * .03f)) * pr else 0f
                val bump = bumpOf(cxm, L)
                val top = yb - bh - bump
                val rad = if (roof == 1) bw * .5f else min(bw * .18f, u * .015f)
                drawRoundRect(col, Offset(bx, top), Size(bw + u * .009f, bh + bump + u), CornerRadius(rad))
                // sisi gelap + tepi terang memberi volume
                drawRect(shade.al(.55f), Offset(bx + bw * .80f, top + rad), Size(bw * .20f, bh + bump + u))
                drawRect(edge.al(.55f), Offset(bx, top + rad), Size(bw * .045f, bh + bump))
                // atap
                when (roof) {
                    0 -> {   // tangki air + unit AC
                        val tw = bw * .26f
                        drawRect(shade, Offset(bx + bw * .18f, top - tw * .9f), Size(tw, tw * .9f + 1f))
                        SP2.reset(); SP2.moveTo(bx + bw * .18f - tw * .08f, top - tw * .9f); SP2.lineTo(bx + bw * .18f + tw * .5f, top - tw * 1.35f); SP2.lineTo(bx + bw * .18f + tw * 1.08f, top - tw * .9f); SP2.close()
                        drawPath(SP2, edge)
                        drawRect(shade, Offset(bx + bw * .62f, top - tw * .38f), Size(tw * .8f, tw * .38f + 1f))
                    }
                    2 -> {   // antena dengan lampu kedip
                        drawRect(col, Offset(bx + bw * .46f, top - bh * .22f), Size(bw * .08f, bh * .22f + 1f))
                        val bl = .5f + .5f * sin(t * 1.1f + i * 1.7f + L)
                        drawCircle(P.d.al((.25f + .55f * bl) * (1f - .5f * calm)), u * .006f, Offset(bx + bw * .5f, top - bh * .22f))
                        glow(P.d, bx + bw * .5f, top - bh * .22f, u * .02f, .4f * bl)
                    }
                    3 -> {   // bertingkat (setback)
                        drawRoundRect(col, Offset(bx + bw * .14f, top - bh * .16f), Size(bw * .72f, bh * .16f + 2f), CornerRadius(u * .004f))
                        drawRect(col, Offset(bx + bw * .32f, top - bh * .30f), Size(bw * .36f, bh * .14f + 2f))
                        drawRect(edge.al(.5f), Offset(bx + bw * .14f, top - bh * .16f), Size(bw * .04f, bh * .16f))
                    }
                    else -> if (L == 2) {   // taman atap kecil
                        for (g in 0 until 3) drawCircle(P.a, u * (.010f + .004f * ornHash(i * 3 + g, 17)), Offset(bx + bw * (.22f + .28f * g), top - u * .004f))
                    }
                }
                // jendela: lapis tengah + depan. Dibatch jadi dua path per gedung; kedipnya per gedung, bukan per jendela (jauh lebih ringan)
                if (L >= 1 && bw > u * .06f) {
                    val cols = max(1, (bw / (u * .03f)).toInt())
                    val rows = min(8, (bh / (u * .04f)).toInt())
                    val cell = bw / cols
                    SP.reset(); SP3.reset()
                    for (r in 0 until rows) for (c in 0 until cols) {
                        val hh = ornHash(i * 131 + r * 17 + c * 3, 51 + L)
                        if (!(hh > litT || (near > .3f && hh > .25f))) continue
                        val wx = bx + c * cell + cell * .25f; val wy = top + u * .025f + r * u * .04f
                        if (L == 2 && hh > .86f) SP3.addRect(Rect(wx, wy, wx + cell * .5f, wy + u * .02f))
                        else SP.addRect(Rect(wx, wy, wx + cell * .5f, wy + u * .02f))
                    }
                    val wa = ((.30f + .35f * (.5f + .5f * sin(t * .25f + i * 1.7f))) * (if (L == 1) .6f else 1f) * (1f - .35f * calm) + near * .2f).coerceIn(0f, 1f)
                    drawPath(SP, P.pp(wa))
                    if (L == 2) drawPath(SP3, P.d.al(.9f))
                    // lantai: garis tipis antar baris pada lapis depan
                    if (L == 2) for (r in 1 until rows) drawLine(P.pp(.10f), Offset(bx + bw * .06f, top + u * .018f + r * u * .04f), Offset(bx + bw * .78f, top + u * .018f + r * u * .04f), strokeWidth = u * .0016f)
                }
                // lapis depan: kanopi toko di kaki gedung + papan neon di beberapa gedung
                if (L == 2 && bw > u * .06f) {
                    val ay = yb - u * .030f
                    for (s in 0 until 4) drawRect(if (s % 2 == 0) P.d.al(.8f) else P.pp(.5f), Offset(bx + bw * (.08f + .21f * s), ay), Size(bw * .21f, u * .012f))
                    drawRect(shade, Offset(bx + bw * .08f, ay + u * .012f), Size(bw * .84f, u * .004f))
                    if (ornHash(i, 19) > .6f) {
                        val ny = top + bh * .30f
                        drawRoundRect(P.d.al(.55f * (1f - .5f * calm)), Offset(bx + bw * .15f, ny), Size(bw * .5f, u * .016f), CornerRadius(u * .004f))
                        glow(P.d, bx + bw * .4f, ny + u * .008f, bw * .6f, .25f * (1f - .5f * calm))
                    }
                }
            }
        }

        // kabut: makin ke depan makin tipis (perspektif udara)
        if (L < 2) {
            val y0 = h * .45f
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, P.paper.copy(alpha = (.30f - .12f * L) * P.amt)), startY = y0, endY = h + u * .02f),
                Offset(0f, y0), Size(w, h - y0 + u * .02f)
            )
        }
    }

    // ---- jalan: trotoar, marka bergerak, lampu jalan, pohon, dan mobil -------------------------------------------------
    val ry = h - roadH
    drawRect(P.ink.al(.88f), Offset(0f, ry), Size(w, roadH + u * .02f))
    drawRect(mixc(P.ink, P.paper, .30f).al(.8f), Offset(0f, ry), Size(w, u * .009f))
    for (i in 0 until 16) {
        val dx = ornMod(i * u * .13f - t * u * .018f, u * 2.1f) - u * .1f
        drawRect(P.pp(.30f), Offset(dx, ry + roadH * .62f), Size(u * .05f, u * .003f))
    }
    // pohon trotoar
    for (i in 0 until 4) {
        val tx0 = u * (.16f + .27f * i + .03f * ornHash(i, 85))
        val sw2 = sin(t * .8f + i) * u * .004f
        drawRect(P.ink, Offset(tx0 - u * .002f, ry - u * .022f), Size(u * .004f, u * .024f))
        drawCircle(P.a, u * .016f, Offset(tx0 + sw2, ry - u * .034f))
        drawCircle(P.c, u * .011f, Offset(tx0 - u * .010f + sw2, ry - u * .028f))
        drawCircle(mixc(P.a, P.paper, .4f).al(.7f), u * .006f, Offset(tx0 + u * .004f + sw2, ry - u * .040f))
    }
    // lampu jalan
    for (i in 0 until 4) {
        val lx = u * (.08f + .27f * i)
        val lh = u * .105f
        drawLine(P.ink, Offset(lx, ry + u * .002f), Offset(lx, ry - lh), strokeWidth = u * .0034f, cap = StrokeCap.Round)
        drawLine(P.ink, Offset(lx, ry - lh), Offset(lx + u * .018f, ry - lh - u * .004f), strokeWidth = u * .0030f, cap = StrokeCap.Round)
        val on = (if (dk) 1f else .45f) * (1f - .5f * calm)
        SP2.reset(); SP2.moveTo(lx + u * .016f, ry - lh); SP2.lineTo(lx + u * .002f, ry); SP2.lineTo(lx + u * .034f, ry); SP2.close()
        drawPath(SP2, Brush.verticalGradient(listOf(P.d.al(.30f * on), Color.Transparent), startY = ry - lh, endY = ry))
        drawCircle(P.d.al(.9f * on), u * .0045f, Offset(lx + u * .018f, ry - lh - u * .002f))
        glow(P.d, lx + u * .018f, ry - lh, u * .035f, .55f * on)
    }
    // mobil: dua arah, lampu depan menyala dan lampu belakang merah-hangat
    for (c in 0 until 4) {
        val dirc = if (c % 2 == 1) -1f else 1f
        val s = u * (.020f + .003f * (c % 2))
        val cxp = ornMod(u * ornHash(c, 81) + dirc * t * u * (.03f + .01f * c), u * 1.3f) - u * .15f
        val cyp = ry + roadH * (if (dirc > 0f) .50f else .84f)
        val body = if (c % 2 == 0) P.c else P.d
        drawRoundRect(body, Offset(cxp - s, cyp - s * .62f), Size(s * 2f, s * .62f), CornerRadius(s * .18f))
        SP2.reset(); SP2.moveTo(cxp - s * .52f, cyp - s * .62f); SP2.lineTo(cxp - s * .30f, cyp - s * 1.12f)
        SP2.lineTo(cxp + s * .34f, cyp - s * 1.12f); SP2.lineTo(cxp + s * .62f, cyp - s * .62f); SP2.close()
        drawPath(SP2, body)
        drawRect(P.pp(.30f), Offset(cxp - s * .24f, cyp - s * 1.02f), Size(s * .50f, s * .34f))
        drawCircle(P.ink, s * .26f, Offset(cxp - s * .58f, cyp)); drawCircle(P.ink, s * .26f, Offset(cxp + s * .58f, cyp))
        val hx = cxp + dirc * s * .98f; val tx2 = cxp - dirc * s * .98f
        val lit = (.55f - .35f * calm)
        drawCircle(P.d.al(lit), s * .12f, Offset(hx, cyp - s * .36f)); glow(P.d, hx + dirc * s * .4f, cyp - s * .36f, s * 1.5f, .45f * (1f - .6f * calm))
        drawCircle(P.hot.al(.7f * (1f - .5f * calm)), s * .10f, Offset(tx2, cyp - s * .36f))
    }
}

// =====================================================================================================================
// Reef: ekosistem karang. Permukaan berkilau di atas, berkas cahaya, kura-kura, ikan bergerombol, ubur-ubur, pari manta jauh,
// dan di dasar: karang tanduk, karang otak, karang meja, kipas laut, spons tabung, anemon + ikan badut, rumput laut, bintang laut
// =====================================================================================================================

/** Tambahkan badan ikan (menghadap [dir]) ke path [p] tanpa menggambar: dipakai untuk membatch satu gerombolan jadi satu gambar. */
private fun fishBody(p: Path, x: Float, y: Float, s: Float, dir: Float, tp: Float) {
    val ts = sin(tp) * s * .22f
    p.moveTo(x + dir * s, y)
    p.quadraticBezierTo(x + dir * s * .2f, y - s * .62f, x - dir * s * .62f, y - s * .10f)
    p.lineTo(x - dir * s * 1.05f, y - s * .42f + ts)
    p.lineTo(x - dir * s * .90f, y + ts * .6f)
    p.lineTo(x - dir * s * 1.05f, y + s * .42f + ts)
    p.lineTo(x - dir * s * .62f, y + s * .10f)
    p.quadraticBezierTo(x + dir * s * .2f, y + s * .58f, x + dir * s, y)
    p.close()
}

/** Ikan menghadap [dir] (+1 kanan / -1 kiri). [detail] menambah garis tubuh, sirip, mata. */
private fun DrawScope.fish(P: OrnPaint, col: Color, x: Float, y: Float, s: Float, dir: Float, tp: Float, stripe: Boolean, detail: Boolean) {
    SP.reset()
    fishBody(SP, x, y, s, dir, tp)
    drawPath(SP, col)
    if (detail) {
        drawOval(P.pp(.22f), Offset(x - s * .4f, y + s * .05f), Size(s * 1.2f, s * .34f))
        if (stripe) {
            drawLine(P.pp(.8f), Offset(x + dir * s * .22f, y - s * .46f), Offset(x + dir * s * .10f, y + s * .40f), strokeWidth = s * .2f)
            drawLine(P.pp(.8f), Offset(x - dir * s * .30f, y - s * .30f), Offset(x - dir * s * .36f, y + s * .26f), strokeWidth = s * .13f)
        }
        SP.reset(); SP.moveTo(x - dir * s * .1f, y - s * .42f); SP.lineTo(x - dir * s * .42f, y - s * .78f); SP.lineTo(x - dir * s * .55f, y - s * .20f); SP.close()
        drawPath(SP, mixc(col, P.ink, .18f))
    }
    drawCircle(P.pp(.9f), s * .115f, Offset(x + dir * s * .58f, y - s * .10f))
    drawCircle(P.ink, s * .06f, Offset(x + dir * s * .60f, y - s * .10f))
}

/** Sirip berbentuk dayung (tetesan memanjang) dari pangkal (x0, y0) ke arah +x sepanjang L; dipakai dengan rotate. */
private fun flipperPath(p: Path, x0: Float, y0: Float, L: Float, wd: Float) {
    p.reset()
    p.moveTo(x0, y0 - wd * .35f)
    p.cubicTo(x0 + L * .40f, y0 - wd * .95f, x0 + L * .92f, y0 - wd * .35f, x0 + L, y0 + wd * .10f)
    p.cubicTo(x0 + L * .72f, y0 + wd * .62f, x0 + L * .30f, y0 + wd * .55f, x0, y0 + wd * .38f)
    p.close()
}

/** Penyu dari samping: empat sirip (dua depan besar yang mengayuh, dua belakang kecil), kepala berparuh, cangkang bersisik. */
private fun DrawScope.turtle(P: OrnPaint, x: Float, y: Float, s: Float, dir: Float, t: Float) {
    val skin = mixc(P.c, P.paper, .22f)
    val skinD = mixc(P.c, P.ink, .22f)
    val shell = mixc(P.a, P.ink, .12f)
    val ph = t * 1.25f
    val stroke = sin(ph)                       // -1..1: ayunan sirip depan
    withTransform({ scale(dir, 1f, Offset(x, y)) }) {
        // sirip sisi jauh (lebih gelap, di belakang badan)
        withTransform({ rotate(-20f - stroke * 24f, Offset(x + s * .20f, y + s * .02f)) }) {
            flipperPath(SP2, x + s * .20f, y + s * .02f, s * .44f, s * .15f); drawPath(SP2, skinD)
        }
        withTransform({ rotate(22f + stroke * 12f, Offset(x - s * .36f, y + s * .06f)) }) {
            flipperPath(SP2, x - s * .36f, y + s * .06f, s * .26f, s * .10f); drawPath(SP2, skinD)
        }
        // ekor
        SP2.reset(); SP2.moveTo(x - s * .46f, y); SP2.lineTo(x - s * .66f, y + s * .10f); SP2.lineTo(x - s * .44f, y + s * .09f); SP2.close()
        drawPath(SP2, skinD)
        // perut (plastron) dan leher
        drawOval(skinD, Offset(x - s * .46f, y - s * .06f), Size(s * .92f, s * .34f))
        SP2.reset(); SP2.moveTo(x + s * .30f, y - s * .10f)
        SP2.cubicTo(x + s * .46f, y - s * .20f, x + s * .58f, y - s * .22f, x + s * .64f, y - s * .16f)
        SP2.lineTo(x + s * .60f, y + s * .02f)
        SP2.cubicTo(x + s * .50f, y + s * .08f, x + s * .38f, y + s * .12f, x + s * .28f, y + s * .12f)
        SP2.close()
        drawPath(SP2, skin)
        // kepala: oval + paruh + mata + garis mulut
        val hx = x + s * .68f; val hy = y - s * .13f
        drawOval(skin, Offset(hx - s * .10f, hy - s * .075f), Size(s * .22f, s * .15f))
        SP2.reset(); SP2.moveTo(hx + s * .09f, hy - s * .035f); SP2.quadraticBezierTo(hx + s * .17f, hy, hx + s * .09f, hy + s * .04f); SP2.close()
        drawPath(SP2, skinD)
        drawCircle(P.pp(.9f), s * .022f, Offset(hx + s * .04f, hy - s * .025f))
        drawCircle(P.ink, s * .012f, Offset(hx + s * .045f, hy - s * .025f))
        drawLine(P.ink.al(.45f), Offset(hx + s * .02f, hy + s * .025f), Offset(hx + s * .13f, hy + s * .018f), strokeWidth = s * .008f, cap = StrokeCap.Round)
        // cangkang: kubah dengan tepi terang, sisik heksagonal, bayangan bawah
        drawOval(shell, Offset(x - s * .52f, y - s * .36f), Size(s * 1.04f, s * .66f))
        drawOval(mixc(shell, P.ink, .25f).al(.8f), Offset(x - s * .50f, y - s * .06f), Size(s * 1.0f, s * .34f))
        drawOval(mixc(shell, P.paper, .22f), Offset(x - s * .47f, y - s * .38f), Size(s * .94f, s * .56f))
        drawOval(mixc(shell, P.paper, .40f).al(.55f), Offset(x - s * .30f, y - s * .34f), Size(s * .52f, s * .20f))
        SP2.reset()
        for (r in 0 until 3) {
            val cx = x - s * .27f + r * s * .27f
            val cyy = y - s * .17f
            val hw = s * .115f; val hh = s * .085f
            SP2.moveTo(cx - hw, cyy); SP2.lineTo(cx - hw * .5f, cyy - hh); SP2.lineTo(cx + hw * .5f, cyy - hh)
            SP2.lineTo(cx + hw, cyy); SP2.lineTo(cx + hw * .5f, cyy + hh); SP2.lineTo(cx - hw * .5f, cyy + hh); SP2.close()
        }
        drawPath(SP2, P.ink.al(.28f), style = Stroke(s * .014f, join = StrokeJoin.Round))
        drawOval(mixc(shell, P.paper, .55f).al(.7f), Offset(x - s * .47f, y - s * .38f), Size(s * .94f, s * .56f), style = Stroke(s * .012f))
        // sirip depan sisi dekat: besar, mengayuh naik-turun, di depan cangkang
        withTransform({ rotate(34f + stroke * 30f, Offset(x + s * .22f, y + s * .10f)) }) {
            flipperPath(SP2, x + s * .22f, y + s * .10f, s * .56f, s * .19f); drawPath(SP2, skin)
            drawPath(SP2, P.ink.al(.20f), style = Stroke(s * .010f, join = StrokeJoin.Round))
            drawLine(P.pp(.30f), Offset(x + s * .30f, y + s * .10f), Offset(x + s * .62f, y + s * .12f), strokeWidth = s * .014f, cap = StrokeCap.Round)
        }
        // sirip belakang sisi dekat
        withTransform({ rotate(30f - stroke * 14f, Offset(x - s * .34f, y + s * .12f)) }) {
            flipperPath(SP2, x - s * .34f, y + s * .12f, s * .30f, s * .11f); drawPath(SP2, skin)
            drawPath(SP2, P.ink.al(.20f), style = Stroke(s * .008f, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.jellyfish(P: OrnPaint, x: Float, y: Float, r: Float, ph: Float, t: Float, col: Color, a: Float) {
    val pulse = sin(t * 1.3f + ph)
    val bw = r * (1f + .09f * pulse); val bh = r * (.85f - .14f * pulse)
    // tentakel: lima helai tipis yang tak bersinggungan -> satu path, satu gambar
    SP3.reset()
    for (k in 0 until 5) {
        val sx = x + (k - 2f) * bw * .30f
        SP3.moveTo(sx, y)
        for (m in 1..8) {
            val f = m / 8f
            SP3.lineTo(sx + sin(t * 1.1f + ph - m * .55f + k) * r * .11f * f * (1f + k % 2), y + f * r * (1.45f + .2f * (k % 3)) * (1f + .06f * pulse))
        }
    }
    drawPath(SP3, col.al(.55f * a), style = Stroke(r * .035f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // lengan mulut yang lebih tebal
    for (k in 0 until 2) {
        SP3.reset(); SP3.moveTo(x + (k - .5f) * bw * .3f, y)
        for (m in 1..6) {
            val f = m / 6f
            SP3.lineTo(x + (k - .5f) * bw * .3f + sin(t * 1.4f + ph - m * .6f + k * 2f) * r * .15f * f, y + f * r * 1.0f)
        }
        drawPath(SP3, col.al(.45f * a), style = Stroke(r * .09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // lonceng: kubah + tepi bergerigi
    SP2.reset(); SP2.moveTo(x - bw, y)
    SP2.cubicTo(x - bw, y - bh * 1.55f, x + bw, y - bh * 1.55f, x + bw, y)
    for (k in 5 downTo 0) {
        val ex = x - bw + bw * 2f * k / 5f
        val mx = x - bw + bw * 2f * (k + .5f) / 5f
        if (k < 5) SP2.quadraticBezierTo(mx, y + bh * .30f * (1f + .2f * pulse), ex, y)
    }
    SP2.close()
    drawPath(SP2, col.al(.50f * a))
    drawPath(SP2, Brush.verticalGradient(listOf(P.pp(.35f * a), Color.Transparent), startY = y - bh * 1.1f, endY = y + bh * .2f))
    drawPath(SP2, col.al(.85f * a), style = Stroke(r * .03f))
    for (k in 0 until 4) drawCircle(P.pp(.35f * a), r * .05f, Offset(x + (k - 1.5f) * bw * .3f, y - bh * (.42f - .06f * (k % 2))))
    glow(col, x, y, r * 2.1f, .26f * a)
}

private fun DrawScope.manta(P: OrnPaint, x: Float, y: Float, s: Float, dir: Float, t: Float, a: Float) {
    val fl = sin(t * .8f)
    withTransform({ scale(dir, 1f, Offset(x, y)) }) {
        SP2.reset(); SP2.moveTo(x + s * .55f, y)
        SP2.quadraticBezierTo(x + s * .1f, y - s * (.50f + .18f * fl), x - s * .5f, y - s * (.38f + .26f * fl))
        SP2.quadraticBezierTo(x - s * .1f, y - s * .06f, x - s * .22f, y + s * .02f)
        SP2.quadraticBezierTo(x - s * .1f, y + s * .20f, x + s * .55f, y)
        SP2.close()
        drawPath(SP2, P.ink.al(.20f * a))
        SP2.reset(); SP2.moveTo(x + s * .55f, y)
        SP2.quadraticBezierTo(x + s * .1f, y + s * (.50f - .18f * fl), x - s * .5f, y + s * (.38f - .26f * fl))
        SP2.quadraticBezierTo(x - s * .1f, y + s * .06f, x - s * .22f, y - s * .02f)
        SP2.close()
        drawPath(SP2, P.ink.al(.14f * a))
        drawLine(P.ink.al(.18f * a), Offset(x - s * .22f, y), Offset(x - s * .78f, y + s * .05f * fl), strokeWidth = s * .012f, cap = StrokeCap.Round)
    }
}

private val SB = Array(5) { Path() }    // 0..3 = cabang per kedalaman, 4 = ujung polip

private fun staghornAdd(x: Float, y: Float, len: Float, ang: Float, depth: Int, t: Float, sw: Float, seed: Int, tipR: Float) {
    val a = ang + sin(t * .8f + seed * 1.7f + depth * .9f) * .028f * (4 - depth) + sw * (4 - depth) * .25f
    val ex = x + sin(a) * len; val ey = y - cos(a) * len
    SB[depth].moveTo(x, y); SB[depth].lineTo(ex, ey)
    if (depth <= 0) { SB[4].addOval(Rect(ex - tipR, ey - tipR, ex + tipR, ey + tipR)); return }
    val n = if (depth == 3) 3 else 2
    for (k in 0 until n) {
        val da = (k - (n - 1) / 2f) * .66f + (ornHash(seed * 7 + k + depth * 3, 91) - .5f) * .32f
        staghornAdd(ex, ey, len * .74f, a + da, depth - 1, t, sw, seed * 3 + k + 1, tipR)
    }
}

/** Karang tanduk bercabang rekursif; seluruh cabang digambar per tingkat ketebalan (4 gambar + ujung), bukan per cabang. */
private fun DrawScope.staghorn(P: OrnPaint, x: Float, y: Float, len: Float, ang: Float, depth: Int, t: Float, sw: Float, col: Color, tip: Color, wd: Float, seed: Int) {
    for (p in SB) p.reset()
    staghornAdd(x, y, len, ang, depth, t, sw, seed, wd * (.72f).pow(depth) * .85f)
    for (d in depth downTo 0) drawPath(SB[d], col, style = Stroke(wd * (.72f).pow(depth - d), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(SB[4], tip)
}

private fun DrawScope.brainCoral(P: OrnPaint, x: Float, y: Float, r: Float) {
    SP.reset(); SP.moveTo(x - r, y); SP.cubicTo(x - r, y - r * 1.25f, x + r, y - r * 1.25f, x + r, y); SP.close()
    drawPath(SP, Brush.verticalGradient(listOf(mixc(P.c, P.paper, .30f), P.c, mixc(P.c, P.ink, .25f)), startY = y - r * .95f, endY = y))
    clipPath(SP) {
        SP2.reset()
        for (g in 0 until 7) {
            val yy = y - r * (.12f + g * .135f)
            var k = 0
            val seg = r * .20f
            var xx = x - r
            while (xx <= x + r) {
                val yo = yy + (if (k % 2 == 0) -1f else 1f) * r * .05f
                if (k == 0) SP2.moveTo(xx, yo) else SP2.lineTo(xx, yo)
                xx += seg; k++
            }
        }
        drawPath(SP2, P.ink.al(.28f), style = Stroke(r * .05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(SP2, P.pp(.22f), style = Stroke(r * .02f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawOval(P.pp(.18f), Offset(x - r * .62f, y - r * 1.02f), Size(r * .8f, r * .34f))
    }
}

private fun DrawScope.tableCoral(P: OrnPaint, x: Float, y: Float, r: Float, t: Float) {
    drawLine(mixc(P.b, P.ink, .25f), Offset(x, y), Offset(x, y - r * .75f), strokeWidth = r * .20f, cap = StrokeCap.Round)
    for (k in 0 until 3) {
        val rw = r * (1.0f - .26f * k); val yy = y - r * (.50f + .24f * k)
        drawOval(mixc(P.b, P.ink, .30f), Offset(x - rw, yy - rw * .06f), Size(rw * 2f, rw * .50f))
        drawOval(mixc(P.b, P.paper, .15f + .08f * k), Offset(x - rw, yy - rw * .20f), Size(rw * 2f, rw * .46f))
        drawOval(P.pp(.28f), Offset(x - rw, yy - rw * .20f), Size(rw * 2f, rw * .46f), style = Stroke(rw * .04f))
        for (d in 0 until 7) drawCircle(P.pp(.35f), rw * .028f, Offset(x + (d - 3f) * rw * .26f, yy + sin(d * 2.1f + k) * rw * .05f))
    }
}

private fun DrawScope.seaFan(P: OrnPaint, x: Float, y: Float, s: Float, t: Float, sw: Float, col: Color) {
    val hubX = x; val hubY = y - s * .16f
    drawLine(col, Offset(x, y), Offset(hubX, hubY), strokeWidth = s * .05f, cap = StrokeCap.Round)
    val rays = 9
    SP2.reset()
    for (i in 0 until rays) {
        val f = i.toFloat() / (rays - 1)
        val a0 = (f - .5f) * 2.5f + sin(t * .7f + i * .5f) * .03f + sw * .8f
        val L = s * (.80f + .18f * sin(f * PI.toFloat()))
        SP2.moveTo(hubX, hubY)
        for (m in 1..3) {
            val q = m / 3f
            val aa = a0 + (f - .5f) * .35f * q
            val px = hubX + sin(aa) * L * q + sin(t * 1.1f + i + q * 3f) * s * .012f * q
            val py = hubY - cos(aa) * L * q
            RX[i * 3 + m - 1] = px; RY[i * 3 + m - 1] = py
            SP2.lineTo(px, py)
        }
    }
    drawPath(SP2, col, style = Stroke(s * .028f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // jaring melintang antar cabang
    for (ring in 0 until 3) {
        SP3.reset()
        for (i in 0 until rays) { val k = i * 3 + ring; if (i == 0) SP3.moveTo(RX[k], RY[k]) else SP3.lineTo(RX[k], RY[k]) }
        drawPath(SP3, col.al(.55f), style = Stroke(s * .012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    SP2.reset()
    for (i in 0 until rays) SP2.addOval(Rect(RX[i * 3 + 2] - s * .016f, RY[i * 3 + 2] - s * .016f, RX[i * 3 + 2] + s * .016f, RY[i * 3 + 2] + s * .016f))
    drawPath(SP2, P.pp(.6f))
}

private fun DrawScope.anemone(P: OrnPaint, x: Float, y: Float, r: Float, t: Float, sw: Float, front: Boolean) {
    if (!front) {
        drawOval(mixc(P.c, P.ink, .25f), Offset(x - r * .5f, y - r * .7f), Size(r, r * .9f))
        drawOval(mixc(P.c, P.ink, .18f), Offset(x - r * .56f, y - r * .20f), Size(r * 1.12f, r * .22f))
    }
    val n = 12
    SP2.reset(); SP3.reset()
    for (i in 0 until n) {
        val f = i.toFloat() / (n - 1)
        if ((i % 2 == 0) != front) continue
        val bx = x + (f - .5f) * r * 1.0f
        val a = (f - .5f) * 1.3f + sin(t * .9f + i * .8f) * .22f + sw
        val L = r * (.85f + .2f * ornHash(i, 181))
        val ex = bx + sin(a) * L; val ey = y - r * .62f - cos(a) * L
        SP2.moveTo(bx, y - r * .62f)
        SP2.quadraticBezierTo(bx + sin(a) * L * .5f + sin(t * 1.2f + i) * r * .15f, y - r * .62f - cos(a) * L * .55f, ex, ey)
        SP3.addOval(Rect(ex - r * .045f, ey - r * .045f, ex + r * .045f, ey + r * .045f))
    }
    drawPath(SP2, (if (front) P.d else mixc(P.d, P.ink, .15f)).al(.9f), style = Stroke(r * .085f, cap = StrokeCap.Round))
    drawPath(SP3, P.pp(.75f))
}

private fun DrawScope.seagrass(P: OrnPaint, x0: Float, yb: Float, hgt: Float, ph: Float, t: Float, gust: Float, col: Color) {
    val u = size.width
    val n = 9
    for (k in 0..n) {
        val f = k.toFloat() / n
        RX[k] = x0 + (sin(t * .9f + ph + f * 3f) * u * .028f + gust * u * .05f) * f * f
        RY[k] = yb - hgt * f
    }
    ribbon(SP2, n, u * .016f, u * .002f)
    drawPath(SP2, col)
    drawLine(P.pp(.22f), Offset(RX[0], RY[0]), Offset(RX[n - 2], RY[n - 2]), strokeWidth = u * .002f, cap = StrokeCap.Round)
}

private fun DrawScope.starfish(P: OrnPaint, x: Float, y: Float, r: Float, rot: Float, col: Color) {
    SP2.reset()
    for (k in 0 until 10) {
        val a = rot + k * (PI.toFloat() / 5f)
        val rd = if (k % 2 == 0) r else r * .42f
        val px = x + cos(a) * rd; val py = y + sin(a) * rd * .55f
        if (k == 0) SP2.moveTo(px, py) else SP2.lineTo(px, py)
    }
    SP2.close()
    drawPath(SP2, col)
    drawPath(SP2, P.ink.al(.2f), style = Stroke(r * .06f, join = StrokeJoin.Round))
    for (k in 0 until 5) {
        val a = rot + k * (2f * PI.toFloat() / 5f)
        drawLine(P.pp(.45f), Offset(x, y), Offset(x + cos(a) * r * .85f, y + sin(a) * r * .85f * .55f), strokeWidth = r * .06f, cap = StrokeCap.Round)
        drawCircle(P.pp(.55f), r * .05f, Offset(x + cos(a) * r * .55f, y + sin(a) * r * .55f * .55f))
    }
}

private fun DrawScope.urchin(P: OrnPaint, x: Float, y: Float, r: Float, t: Float) {
    SP2.reset()
    for (k in 0 until 26) {
        val a = k * (2f * PI.toFloat() / 26f)
        val L = r * (1.6f + .5f * ornHash(k, 191)) * (1f + .05f * sin(t * 1.5f + k))
        SP2.moveTo(x + cos(a) * r * .8f, y + sin(a) * r * .8f); SP2.lineTo(x + cos(a) * L, y + sin(a) * L)
    }
    drawPath(SP2, P.ink.al(.85f), style = Stroke(r * .09f, cap = StrokeCap.Round))
    drawCircle(mixc(P.ink, P.d, .25f), r, Offset(x, y))
    drawCircle(P.pp(.25f), r * .3f, Offset(x - r * .3f, y - r * .35f))
}

private fun DrawScope.drawReefScene(P: OrnPaint, w: Float, h: Float, t: Float, fx: OrnFx?) {
    val u = w
    val pr = fx?.press ?: 0f; val lf = fx?.lift ?: 0f
    val pop = fx?.pop ?: 0f; val popX = fx?.popX ?: 0f; val popY = fx?.popY ?: 0f
    val tx = fx?.px ?: 0f
    val calm = fx?.calm ?: 0f
    val par = parX(fx, u)
    val ta = tapAge(fx)
    val cur = (sin(t * .23f) * .012f + sin(t * .51f + 1f) * .008f) * (1f - .5f * calm)      // arus: mengayun tumbuhan
    fun gust(ox: Float): Float = if (pr > .01f) ((ox - tx) / u).coerceIn(-1.2f, 1.2f) * pr * .10f else 0f
    val yb = h - u * .035f

    // ---- kedalaman: terang di atas, gelap biru di dasar ---------------------------------------------------------------------------------
    drawRect(Brush.verticalGradient(listOf(P.b.al(.34f), P.c.al(.10f), P.c.al(.22f), P.ink.al(.28f)), startY = 0f, endY = h), Offset.Zero, Size(w, h))

    // ---- permukaan air dari bawah: pita terang bergelombang + kilau ----------------------------------------------------------------------
    run {
        val sy = u * .05f
        SP.reset(); SP.moveTo(0f, 0f)
        var x = 0f
        while (x <= u + u * .05f) { SP.lineTo(x, sy + sin(x / u * 9f + t * .9f) * u * .009f + sin(x / u * 21f - t * 1.3f) * u * .004f); x += u * .05f }
        SP.lineTo(w, 0f); SP.close()
        drawPath(SP, mixc(P.b, P.paper, .55f).al(.60f))
        drawPath(SP, P.pp(.55f * (1f - .4f * calm)), style = Stroke(u * .0042f, join = StrokeJoin.Round))
        for (i in 0 until 9) {
            val gx = ornMod(ornHash(i, 201) * u + t * u * .012f, u)
            val tw = .5f + .5f * sin(t * (.8f + ornHash(i, 202)) + i * 2f)
            drawLine(P.pp(.55f * tw), Offset(gx - u * .025f, sy + u * .012f), Offset(gx + u * .025f, sy + u * .012f), strokeWidth = u * .003f, cap = StrokeCap.Round)
        }
    }

    // ---- berkas cahaya dari permukaan ----------------------------------------------------------------------------------------------------
    for (i in 0 until 4) {
        val x0 = u * (.10f + .26f * i) + sin(t * .12f + i * 1.7f) * u * .04f - par * u * .01f
        val wd = u * (.05f + .03f * ornHash(i, 211))
        val sk = u * .38f
        val br = .5f + .5f * sin(t * .21f + i * 1.9f)
        SP.reset(); SP.moveTo(x0, u * .05f); SP.lineTo(x0 + wd, u * .05f); SP.lineTo(x0 + wd * 2.2f - sk, h * .82f); SP.lineTo(x0 - wd * .6f - sk, h * .82f); SP.close()
        drawPath(SP, Brush.verticalGradient(listOf(P.pp((.16f + .12f * br) * (1f - .6f * calm)), Color.Transparent), startY = u * .05f, endY = h * .82f))
    }

    // ---- terumbu jauh yang berkabut (bergeser berlawanan arah dengan lapisan dekat) ----------------------------------------------------------------
    withTransform({ translate(-par * u * .015f, 0f) }) {
        for (layer in 0..1) {
            val base = yb - u * (.20f - .07f * layer) - lf * u * .01f
            SP.reset(); SP.moveTo(-u * .05f, h + 2f); SP.lineTo(-u * .05f, base)
            var x = -u * .05f
            while (x <= u * 1.05f) { SP.lineTo(x, base - (sin(x / u * (5f + 3f * layer) + layer * 2f) * .5f + .5f) * u * (.09f - .02f * layer) - sin(x / u * 17f) * u * .008f); x += u * .04f }
            SP.lineTo(u * 1.05f, h + 2f); SP.close()
            drawPath(SP, (if (layer == 0) mixc(P.b, P.ink, .12f) else mixc(P.a, P.ink, .16f)).al(if (layer == 0) .45f else .60f))
            // karang kecil di punggung bukit
            SP2.reset()
            for (i in 0 until 9) {
                val cx = u * (.06f + .11f * i + .02f * ornHash(i + layer * 9, 215))
                val cyy = base - (sin(cx / u * (5f + 3f * layer) + layer * 2f) * .5f + .5f) * u * (.09f - .02f * layer) + u * .004f
                val hh = u * (.035f + .03f * ornHash(i + layer * 9, 216))
                for (k in 0 until 3) {
                    val a = (k - 1) * .5f + sin(t * .7f + i + k) * .04f
                    SP2.moveTo(cx, cyy); SP2.lineTo(cx + sin(a) * hh, cyy - cos(a) * hh)
                }
            }
            drawPath(SP2, (if (layer == 0) P.b else P.a).al(.55f), style = Stroke(u * .006f, cap = StrokeCap.Round))
        }
        // pari manta melintas sangat jauh
        run {
            val per = 95f
            val k = ornMod(t + 31f, per) / per
            val f = k
            val x = -u * .3f + f * u * 1.6f
            val y = h * .30f + sin(f * 5f) * u * .03f
            if (x + u * .35f > 0f && x - u * .35f < u) manta(P, x, y, u * .30f, 1f, t, sin(f * PI.toFloat()).coerceIn(0f, 1f))
        }
    }

    // ---- kura-kura melintas pelan ---------------------------------------------------------------------------------------------------------------------------
    run {
        val per = 78f
        val k = ornMod(t + 40f, per) / per
        val x = -u * .3f + k * u * 1.6f
        val y = h * .42f + sin(k * 6f) * u * .03f - lf * u * .01f
        val ex = if (pop > .003f) (x - popX) else 0f
        // di luar layar (sekitar seperempat waktu perjalanannya) tidak perlu digambar sama sekali
        if (x + u * .30f > 0f && x - u * .30f < u) turtle(P, x + (if (abs(ex) < u * .3f) sign01(ex) * pop * u * .03f else 0f), y, u * .22f, 1f, t)
    }

    // ---- ikan bergerombol: gerombol kecil ke kanan, gerombol ikan bidadari ke kiri -----------------------------------------------------------------
    fun flee(x: Float, y: Float): Float {
        if (abs(pop) < .003f) return 0f
        val dx = x - popX; val dy = y - popY
        val d = hypot(dx, dy) + 1f
        return pop * exp(-(d * d) / (u * u * .25f)) * u * .10f * (if (dx >= 0f) 1f else -1f)
    }
    run {
        val span = u * 1.6f
        val cxs = ornMod(u * .15f + t * u * .030f, span) - u * .3f
        val cys = h * .28f + sin(t * .12f) * u * .05f
        SP.reset(); SP3.reset(); SP2.reset()
        for (j in 0 until 14) {
            val ox = (ornHash(j, 221) - .5f) * u * .15f + sin(t * .7f + j) * u * .012f
            val oy = (ornHash(j, 222) - .5f) * u * .09f + sin(t * .9f + j * 1.7f) * u * .010f
            val x = cxs + ox; val y = cys + oy
            if (x < -u * .1f || x > u * 1.1f) continue
            val sz = u * (.016f + .004f * ornHash(j, 223))
            val fxp = x + flee(x, y)
            fishBody(if (j % 3 == 0) SP else SP3, fxp, y, sz, 1f, t * 9f + j)
            SP2.addOval(Rect(fxp + sz * .50f, y - sz * .16f, fxp + sz * .70f, y + sz * .04f))
        }
        drawPath(SP, P.d)
        drawPath(SP3, mixc(P.c, P.paper, .15f))
        drawPath(SP2, P.ink.al(.85f))
    }
    run {
        val span = u * 1.6f
        val cxs = u * 1.3f - ornMod(u * .6f + t * u * .022f, span)
        val cys = h * .55f + sin(t * .10f + 1f) * u * .04f
        for (j in 0 until 5) {
            val ox = (ornHash(j, 231) - .5f) * u * .12f + sin(t * .6f + j) * u * .010f
            val oy = (ornHash(j, 232) - .5f) * u * .10f + sin(t * .8f + j * 1.7f) * u * .012f
            val x = cxs + ox; val y = cys + oy
            if (x < -u * .1f || x > u * 1.1f) continue
            fish(P, P.d, x + flee(x, y), y, u * (.026f + .006f * ornHash(j, 233)), -1f, t * 7f + j, true, true)
        }
    }

    // ---- ubur-ubur: berdenyut dan naik pelan ---------------------------------------------------------------------------------------------------------------------
    for (i in 0 until 2) {
        val per = h + u * .6f
        val y = per - ornMod(ornHash(i, 241) * per + t * u * (.016f + .006f * i), per) + u * .1f - u * .3f
        val x = u * (.22f + .50f * i) + sin(t * .25f + i * 2.4f) * u * .07f - par * u * (.02f + .01f * i)
        val edge = (min(y, h - y) / (u * .25f)).coerceIn(0f, 1f)
        if (edge > .02f) jellyfish(P, x, y, u * (.060f + .016f * i), i * 2.3f, t, if (i == 0) P.d else P.c, edge * (1f - .3f * calm))
    }

    // ---- dasar laut: pasir + riak + bayangan kaustik --------------------------------------------------------------------------------------------------------------------
    withTransform({ translate(par * u * .02f, 0f) }) {
        SP4.reset(); SP4.moveTo(-u * .1f, h + 2f); SP4.lineTo(-u * .1f, yb)
        var x = -u * .1f
        while (x <= u * 1.1f) { SP4.lineTo(x, yb + sin(x / u * 6f + .5f) * u * .012f - u * .005f); x += u * .05f }
        SP4.lineTo(u * 1.1f, h + 2f); SP4.close()
        drawPath(SP4, Brush.verticalGradient(listOf(mixc(P.b, P.paper, .55f), mixc(P.a, P.paper, .30f)), startY = yb - u * .02f, endY = h))
        // Tanpa clipPath: garis riak mulai >= .008u di bawah tepi atas pasir dan kilau paling tinggi hanya ~.002u melewati tepi (alpha <= 14%),
        // jadi hasilnya tak terbedakan, tetapi satu klip berbentuk path (mahal di GPU) per frame hilang.
        run {
            for (i in 0 until 5) {
                val yy = yb + u * (.012f + .006f * i)
                SP2.reset(); SP2.moveTo(-u * .05f, yy)
                var xx = -u * .05f
                while (xx <= u * 1.05f) { SP2.lineTo(xx, yy + sin(xx / u * 14f + i * 1.3f) * u * .004f); xx += u * .06f }
                drawPath(SP2, P.ink.al(.10f), style = Stroke(u * .0025f, cap = StrokeCap.Round))
            }
            for (i in 0 until 14) {
                val s = ornMod(ornHash(i, 251) * u + t * u * .01f * (.5f + ornHash(i, 252)), u)
                val tw = .5f + .5f * sin(t * (.8f + ornHash(i, 253)) + i * 2f)
                drawOval(P.pp(.14f * tw), Offset(s - u * .03f, yb + u * (.005f + .02f * ornHash(i, 254))), Size(u * .06f, u * .016f))
            }
        }
    }

    // ---- karang di dasar (lapis tengah) ---------------------------------------------------------------------------------------------------------------------------------------
    withTransform({ translate(par * u * .02f, 0f) }) {
        // rumput laut dan karang tanduk di belakang
        seagrass(P, u * .40f, yb, u * .21f, .3f, t, cur + gust(u * .4f), P.a)
        seagrass(P, u * .435f, yb, u * .27f, 1.6f, t, cur + gust(u * .43f), mixc(P.a, P.paper, .15f))
        seagrass(P, u * .575f, yb, u * .24f, 2.9f, t, cur + gust(u * .57f), P.a)
        seagrass(P, u * .605f, yb, u * .17f, 4.2f, t, cur + gust(u * .6f), mixc(P.a, P.paper, .15f))
        staghorn(P, u * .10f, yb + u * .004f, u * .115f, .08f, 3, t, cur * 3f + gust(u * .1f), P.d.al(.95f), P.pp(.8f), u * .016f, 1)
        staghorn(P, u * .70f, yb + u * .004f, u * .10f, -.10f, 3, t, cur * 3f + gust(u * .7f), mixc(P.d, P.c, .4f), P.pp(.8f), u * .015f, 5)
        tableCoral(P, u * .33f, yb + u * .004f, u * .10f, t)
        seaFan(P, u * .83f, yb + u * .004f, u * .20f, t, cur * 2f + gust(u * .83f), mixc(P.d, P.ink, .12f))
        brainCoral(P, u * .19f, yb + u * .004f, u * .115f)
        // spons tabung
        for (i in 0 until 4) {
            val tx0 = u * (.935f + .027f * i) - u * .06f
            val hh = u * (.07f + .045f * ornHash(i, 261)); val ww = u * (.030f + .008f * ornHash(i, 262))
            val xx = tx0 + u * .02f * i
            drawRoundRect(mixc(P.c, P.d, .3f), Offset(xx, yb - hh), Size(ww, hh + u * .01f), CornerRadius(ww * .4f))
            drawRect(P.ink.al(.14f), Offset(xx + ww * .62f, yb - hh + ww * .3f), Size(ww * .38f, hh))
            drawOval(P.ink.al(.55f), Offset(xx + ww * .1f, yb - hh - ww * .15f), Size(ww * .8f, ww * .38f))
            drawOval(P.pp(.35f), Offset(xx + ww * .1f, yb - hh - ww * .15f), Size(ww * .8f, ww * .38f), style = Stroke(ww * .07f))
        }
        // anemon + ikan badut
        anemone(P, u * .50f, yb + u * .004f, u * .085f, t, cur * 2f + gust(u * .5f), false)
        val fcx = u * .50f + sin(t * .5f) * u * .03f; val fcy = yb - u * .06f + sin(t * .8f) * u * .008f
        fish(P, P.d, fcx, fcy, u * .026f, if (cos(t * .5f) >= 0f) 1f else -1f, t * 12f, true, true)
        anemone(P, u * .50f, yb + u * .004f, u * .085f, t, cur * 2f + gust(u * .5f), true)
        // hewan di pasir
        starfish(P, u * .63f, yb + u * .028f, u * .030f, t * .02f, P.d)
        starfish(P, u * .26f, yb + u * .036f, u * .022f, 1f, mixc(P.c, P.d, .4f))
        urchin(P, u * .745f, yb + u * .018f, u * .016f, t)
        // kerang kipas
        run {
            val kx = u * .09f; val ky = yb + u * .030f; val kr = u * .028f
            SP2.reset(); SP2.moveTo(kx, ky)
            SP2.cubicTo(kx - kr * 1.3f, ky - kr * .1f, kx - kr * 1.1f, ky - kr * 1.7f, kx, ky - kr * 1.6f)
            SP2.cubicTo(kx + kr * 1.1f, ky - kr * 1.7f, kx + kr * 1.3f, ky - kr * .1f, kx, ky)
            SP2.close()
            drawPath(SP2, mixc(P.d, P.paper, .30f))
            for (r in -3..3) drawLine(P.ink.al(.22f), Offset(kx, ky), Offset(kx + r * kr * .36f, ky - kr * (1.5f - abs(r) * .12f)), strokeWidth = kr * .05f, cap = StrokeCap.Round)
            drawRoundRect(P.ink.al(.3f), Offset(kx - kr * .3f, ky - kr * .08f), Size(kr * .6f, kr * .16f), CornerRadius(kr * .07f))
        }
    }

    // ---- gelembung: dari ventilasi tetap + semburan saat diketuk -------------------------------------------------------------------------------------------------------------
    for (i in 0 until 10) {
        val srcX = u * (.12f + .74f * ornHash(i / 3, 271)) + par * u * .02f
        val rise = u * (.14f + .05f * ornHash(i, 272)) * .1f
        val per = yb - u * .05f
        val y = yb - ornMod(ornHash(i, 273) * per + t * rise * 4f, per)
        val x = srcX + sin(t * 1.2f + i * 1.9f + y / u * 8f) * u * .012f
        val br = u * (.004f + .006f * ornHash(i, 274))
        val a = (y / (h * .9f)).coerceIn(0f, 1f) * .9f + .1f
        drawCircle(P.b.al(.30f * a), br, Offset(x, y))
        drawCircle(P.pp(.55f * a), br, Offset(x, y), style = Stroke(br * .28f))
    }
    if (ta < 4f) {
        for (k in 0 until 12) {
            val age = ta - k * .06f
            if (age <= 0f) continue
            val vx = (ornHash(k, 281) - .5f) * u * .10f
            val x = popX + vx * age + sin(age * 5f + k) * u * .008f
            val y = popY - age * u * (.14f + .10f * ornHash(k, 282))
            val br = u * (.004f + .007f * ornHash(k, 283))
            val fa = (1f - age / 3.6f).coerceIn(0f, 1f)
            drawCircle(P.b.al(.30f * fa), br, Offset(x, y))
            drawCircle(P.pp(.65f * fa), br, Offset(x, y), style = Stroke(br * .28f))
        }
        if (ta < 1.6f) {
            val rad = u * .06f + ta * u * .22f
            drawCircle(P.pp(.45f * (1f - ta / 1.6f)), rad, Offset(popX, popY), style = Stroke(u * .004f))
        }
    }

    // ---- plankton: dua lapis kedalaman, berkelip -------------------------------------------------------------------------------------------------------------------------------------
    for (i in 0 until 18) {
        val deep = i % 2 == 0
        val x = ornMod(ornHash(i, 291) * u + t * u * (if (deep) .004f else .008f) * (1f + ornHash(i, 292)) + (if (deep) -par else par) * u * .03f, u)
        val y = ornMod(ornHash(i, 293) * h - t * u * .004f * (.5f + ornHash(i, 294)), h)
        val tw = .5f + .5f * sin(t * (.6f + ornHash(i, 295)) + i * 2.7f)
        drawCircle(P.pp((.12f + .30f * tw) * (if (deep) .6f else 1f)), u * (if (deep) .0022f else .0036f), Offset(x, y))
    }
}

private fun sign01(x: Float): Float = if (x >= 0f) 1f else -1f

// =====================================================================================================================
// Indonesia: hutan hujan tropis dengan reruntuhan candi. Dua punggung bukit berkabut + menara candi jauh, candi bertingkat yang
// runtuh dan berlumut (tangga, stupa, relung berpendar, balok jatuh), dua batang pohon pembingkai, dahan dengan cendrawasih
// (bulu sayap panjang + dua kawat ekor) dan orangutan yang bergelantung sambil makan buah, badak Jawa yang merumput, komodo yang
// berpatroli di tanah depan, bunga bangkai Rafflesia, pakis, kunang-kunang, burung terbang, dan daun jatuh.
// Semua warna turunan palet catatan. Jingga (orangutan, lidah, Rafflesia) dan kuning (bulu cendrawasih, buah) memakai hot / hot2
// yang sudah dipagari kontras oleh ornPaint, jadi tetap tidak pernah bersaing dengan tulisan.
// Ketukan: cendrawasih mengembang dan melompat, orangutan terayun menjauhi ketukan, badak mengangkat kepala + mengibas telinga,
// komodo menjulurkan lidah, dan sekumpulan daun beterbangan dari titik ketuk. Tahan / sentuh: dahan dan pakis condong ke jari.
// =====================================================================================================================

private val IP = Path()
private val IP2 = Path()
private val IP3 = Path()
private val IX = FloatArray(48)
private val IY = FloatArray(48)

// Tepi atas tingkat ke-4 candi yang patah: posisi relatif terhadap lebar tingkat dan tinggi (satuan s) tiap titik.
private val CJX = floatArrayOf(0f, .12f, .20f, .28f, .36f, .44f, .52f, .60f, .70f, .80f, 1f)
private val CJY = floatArrayOf(.17f, .18f, .15f, .17f, .11f, .13f, .07f, .09f, .04f, .06f, .01f)

/** Pakis: tujuh helai daun berlengkung memancar dari satu titik, dua nada (separuh gelap + separuh terang), ujungnya bergoyang. */
private fun DrawScope.idnFern(P: OrnPaint, x: Float, y: Float, len: Float, t: Float, sway: Float, ph: Float, col: Color) {
    IP.reset(); IP2.reset()
    for (k in 0 until 7) {
        val f = (k - 3) / 3f
        val ang = -PI.toFloat() / 2f + f * 1.05f + sway + .05f * sin(t * .9f + ph + k * .8f)
        val l = len * (1f - .26f * abs(f))
        leaf2(IP, x, y, ang, l, l * .17f, 1f, .22f * f, false)
        leaf2(IP2, x, y, ang, l, l * .17f, -1f, .22f * f, false)
    }
    drawPath(IP, col)
    drawPath(IP2, mixc(col, P.paper, .40f).al(.92f))
}

/** Menara candi jauh bergaya Prambanan: enam tingkat atap yang menyempit + puncak. Siluet saja, satu path. */
private fun DrawScope.idnSpire(cx: Float, yb: Float, s: Float, col: Color) {
    var y = yb
    var m = 0
    IX[m] = cx - s * .50f; IY[m] = y; m++
    for (i in 0 until 6) {
        val hw = s * .50f * (1f - .13f * i)
        val bh = s * (.15f - .008f * i)
        IX[m] = cx - hw - s * .016f; IY[m] = y; m++
        IX[m] = cx - hw; IY[m] = y - bh * .22f; m++
        IX[m] = cx - hw; IY[m] = y - bh; m++
        y -= bh
    }
    IX[m] = cx - s * .05f; IY[m] = y; m++
    IP.reset(); IP.moveTo(IX[0], IY[0])
    for (i in 1 until m) IP.lineTo(IX[i], IY[i])
    IP.lineTo(cx, y - s * .15f)
    for (i in m - 1 downTo 0) IP.lineTo(2f * cx - IX[i], IY[i])
    IP.close()
    drawPath(IP, col)
}

/** Batang pohon besar yang membingkai tepi layar: pita bergelombang, melebar jadi akar banir di bawah, dengan garis kulit kayu. */
private fun DrawScope.idnTrunk(P: OrnPaint, xc: Float, wTop: Float, wBot: Float, hgt: Float, seed: Float) {
    val u = size.width
    val n = 14
    fun hw(f: Float): Float {
        val fl = if (f > .84f) (f - .84f) / .16f else 0f
        return (wTop + (wBot - wTop) * f + u * .07f * fl * fl) * .5f
    }
    fun wv(f: Float): Float = sin(f * 7f + seed) * u * .006f
    IP.reset()
    for (k in 0..n) {
        val f = k / n.toFloat()
        val xx = xc - hw(f) + wv(f)
        if (k == 0) IP.moveTo(xx, -u * .02f) else IP.lineTo(xx, f * hgt)
    }
    for (k in n downTo 0) {
        val f = k / n.toFloat()
        IP.lineTo(xc + hw(f) + wv(f + .3f), f * hgt)
    }
    IP.close()
    drawPath(IP, mixc(P.a, P.ink, .30f))
    IP2.reset()
    for (j in 0 until 3) {
        for (k in 0..n) {
            val f = k / n.toFloat()
            val xx = xc + (j - 1) * hw(f) * .55f + wv(f + j * .2f)
            if (k == 0) IP2.moveTo(xx, 0f) else IP2.lineTo(xx, f * hgt)
        }
    }
    drawPath(IP2, P.ink.al(.30f), style = Stroke(u * .003f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    IP3.reset()
    for (k in 0..n) {
        val f = k / n.toFloat()
        val xx = xc - hw(f) * .82f + wv(f)
        if (k == 0) IP3.moveTo(xx, 0f) else IP3.lineTo(xx, f * hgt)
    }
    drawPath(IP3, P.pp(.20f), style = Stroke(u * .004f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Candi bertingkat yang sebagian runtuh: tiga tingkat batu + tingkat puncak yang patah, tangga, relung berpendar, panel relief,
 * stupa (satu utuh, satu retak), lumut di tepi, sulur menjuntai, pakis di atapnya, dan balok-balok jatuh di kakinya.
 * [s] = lebar alas; semua ukuran turunan s.
 */
private fun DrawScope.idnCandi(P: OrnPaint, cx: Float, yb: Float, s: Float, t: Float, wind: Float) {
    val stone = mixc(P.a, P.ink, .12f)
    val stoneL = mixc(P.b, P.paper, .34f)
    val stoneD = mixc(P.a, P.ink, .44f)
    val moss = mixc(P.c, P.a, .30f)
    val h1 = s * .13f; val h2 = s * .15f; val h3 = s * .22f
    val l1 = cx - s * .50f; val l2 = cx - s * .42f; val l3 = cx - s * .33f; val l4 = cx - s * .25f
    val y1 = yb - h1
    val y2 = y1 - h2
    val y3 = y2 - h3
    val wd4 = s * .50f

    fun tier(l: Float, top: Float, wd: Float, hh: Float) {
        drawRect(stone, Offset(l, top), Size(wd, hh))
        drawRect(stoneD.al(.50f), Offset(l, top + hh * .84f), Size(wd, hh * .16f))
        drawRect(stoneL, Offset(l - s * .012f, top), Size(wd + s * .024f, hh * .15f))
    }
    tier(l1, y1, s, h1)
    tier(l2, y2, s * .84f, h2)
    tier(l3, y3, s * .66f, h3)

    // tingkat puncak: tepi atas patah, sisi kanan hampir hilang
    IP.reset(); IP.moveTo(l4, y3)
    for (i in 0 until CJX.size) IP.lineTo(l4 + CJX[i] * wd4, y3 - CJY[i] * s)
    IP.lineTo(l4 + wd4, y3); IP.close()
    drawPath(IP, stone)
    drawPath(IP, stoneL.al(.85f), style = Stroke(s * .010f, join = StrokeJoin.Round))
    drawRect(stoneD.al(.40f), Offset(l4, y3 - s * .015f), Size(wd4, s * .015f))

    // garis susunan batu (satu path untuk semua tingkat)
    fun courses(l: Float, top: Float, wd: Float, hh: Float, rows: Int, cols: Int) {
        for (r in 0 until rows) {
            val ya = top + hh * r / rows
            val yc = top + hh * (r + 1) / rows
            if (r > 0) { IP2.moveTo(l, ya); IP2.lineTo(l + wd, ya) }
            val off = if (r % 2 == 0) 0f else .5f
            for (c in 0 until cols) {
                val x = l + wd * (c + off) / cols
                if (x > l + wd * .02f && x < l + wd * .98f) { IP2.moveTo(x, ya); IP2.lineTo(x, yc) }
            }
        }
    }
    IP2.reset()
    courses(l1, y1, s, h1, 2, 8)
    courses(l2, y2, s * .84f, h2, 2, 7)
    courses(l3, y3, s * .66f, h3, 3, 6)
    drawPath(IP2, P.ink.al(.20f), style = Stroke(s * .0035f))

    // panel relief di tingkat 2 (kiri dan kanan tangga)
    IP3.reset()
    for (i in 0 until 6) {
        val dx = (if (i < 3) -1f else 1f) * (.18f + .09f * (i % 3)) * s
        IP3.addRoundRect(RoundRect(cx + dx - s * .026f, y2 + h2 * .24f, cx + dx + s * .026f, y2 + h2 * .74f, s * .008f, s * .008f))
    }
    drawPath(IP3, P.ink.al(.18f))

    // tangga tengah
    val stepH = (h1 + h2) / 5f
    for (i in 0 until 5) {
        val top = yb - (i + 1) * stepH
        val wd = s * (.29f - .024f * i)
        drawRect(mixc(stone, P.paper, .16f), Offset(cx - wd / 2f, top), Size(wd, stepH + 1f))
        drawRect(stoneL, Offset(cx - wd / 2f, top), Size(wd, stepH * .30f))
    }

    // pintu candi + cahaya samar di dalamnya
    val dw = s * .075f; val dh = s * .13f
    IP.reset(); IP.moveTo(cx - dw, y2); IP.lineTo(cx - dw, y2 - dh * .62f)
    IP.quadraticBezierTo(cx - dw, y2 - dh, cx, y2 - dh)
    IP.quadraticBezierTo(cx + dw, y2 - dh, cx + dw, y2 - dh * .62f)
    IP.lineTo(cx + dw, y2); IP.close()
    drawPath(IP, P.ink.al(.66f))
    glow(P.d, cx, y2 - dh * .42f, dw * 2.6f, .20f + .07f * sin(t * .8f))
    drawPath(IP, stoneL.al(.80f), style = Stroke(s * .008f, join = StrokeJoin.Round))
    drawRect(stoneL, Offset(cx - dw * 1.4f, y2 - dh - s * .026f), Size(dw * 2.8f, s * .022f))
    // dua relung kecil di sisi pintu
    IP.reset()
    for (sg in 0..1) {
        val nx = cx + (if (sg == 0) -1f else 1f) * s * .22f
        IP.moveTo(nx - s * .03f, y2); IP.lineTo(nx - s * .03f, y2 - s * .05f)
        IP.quadraticBezierTo(nx - s * .03f, y2 - s * .085f, nx, y2 - s * .085f)
        IP.quadraticBezierTo(nx + s * .03f, y2 - s * .085f, nx + s * .03f, y2 - s * .05f)
        IP.lineTo(nx + s * .03f, y2); IP.close()
    }
    drawPath(IP, P.ink.al(.30f))

    // stupa: satu utuh di tingkat 3, satu utuh dan satu retak di tingkat 2
    fun stupa(x: Float, yTop: Float, r: Float, broken: Boolean) {
        drawRoundRect(stoneL, Offset(x - r * 1.1f, yTop - r * .38f), Size(r * 2.2f, r * .38f), CornerRadius(r * .1f))
        IP.reset()
        if (!broken) {
            IP.moveTo(x - r, yTop - r * .38f)
            IP.cubicTo(x - r, yTop - r * 2.0f, x + r, yTop - r * 2.0f, x + r, yTop - r * .38f)
            IP.close()
        } else {
            IP.moveTo(x - r, yTop - r * .38f)
            IP.cubicTo(x - r, yTop - r * 1.3f, x - r * .4f, yTop - r * 1.6f, x + r * .15f, yTop - r * 1.15f)
            IP.lineTo(x + r * .45f, yTop - r * .85f)
            IP.lineTo(x + r, yTop - r * .75f)
            IP.lineTo(x + r, yTop - r * .38f)
            IP.close()
        }
        drawPath(IP, stone)
        for (k in -1..1) drawRect(P.ink.al(.40f), Offset(x + k * r * .55f - r * .10f, yTop - r * 1.05f), Size(r * .20f, r * .34f))
        if (!broken) {
            drawRect(stoneL, Offset(x - r * .09f, yTop - r * 1.58f - r * .42f), Size(r * .18f, r * .42f))
            drawCircle(stoneL, r * .16f, Offset(x, yTop - r * 2.04f))
        }
    }
    stupa(l2 + s * .045f, y2, s * .032f, false)
    stupa(l2 + s * .795f, y2, s * .032f, true)
    stupa(l3 + s * .04f, y3, s * .028f, false)

    // lumut di tepi atas tiap tingkat
    IP3.reset()
    for (i in 0 until 6) {
        val mx = l1 + s * (.08f + .16f * i + .04f * ornHash(i, 411))
        IP3.addOval(Rect(mx - s * .045f, y1 - s * .008f, mx + s * .045f, y1 + s * .014f))
    }
    for (i in 0 until 5) {
        val mx = l2 + s * .84f * (.08f + .2f * i + .03f * ornHash(i, 412))
        IP3.addOval(Rect(mx - s * .04f, y2 - s * .008f, mx + s * .04f, y2 + s * .014f))
    }
    for (i in 0 until 7) {
        val f = ((i + .3f * ornHash(i, 413)) / 6.6f).coerceIn(0f, 1f)
        val mx = l4 + f * wd4
        val my = y3 - CJY[(f * 10f).toInt().coerceIn(0, 10)] * s
        IP3.addOval(Rect(mx - s * .035f, my - s * .008f, mx + s * .035f, my + s * .012f))
    }
    drawPath(IP3, moss.al(.85f))

    // sulur menjuntai dari tepi tingkat
    IP2.reset(); IP3.reset()
    fun creeper(x0: Float, y0: Float, len: Float, ph: Float) {
        for (m in 0..5) {
            val f = m / 5f
            val xx = x0 + sin(t * .5f + ph + f * 2.2f) * s * .014f * f + wind * s * .12f * f
            val yy = y0 + len * f
            if (m == 0) IP2.moveTo(xx, yy) else IP2.lineTo(xx, yy)
            if (m >= 1) IP3.addOval(Rect(xx - s * .018f, yy - s * .010f, xx + s * .018f, yy + s * .010f))
        }
    }
    creeper(l3 + s * .02f, y3 + s * .01f, s * .14f, .4f)
    creeper(l2 + s * .80f, y2 + s * .005f, s * .12f, 2.1f)
    creeper(l3 + s * .64f, y3 + s * .01f, s * .16f, 3.6f)
    drawPath(IP2, moss.al(.90f), style = Stroke(s * .006f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(IP3, mixc(P.a, P.paper, .15f))

    // pakis di atap dan di kaki candi
    idnFern(P, l4 + s * .04f, y3 - s * .17f, s * .15f, t, wind * 2f, 1.2f, P.a)
    idnFern(P, l2 + s * .70f, y2, s * .10f, t, wind * 2f, 2.4f, mixc(P.a, P.c, .4f))
    idnFern(P, cx - s * .46f, yb, s * .13f, t, wind * 2f, 3.1f, P.a)
    idnFern(P, cx + s * .50f, yb, s * .12f, t, wind * 2f, 4.3f, mixc(P.a, P.c, .4f))

    // balok jatuh di kaki candi
    for (i in 0 until 3) {
        val bx = cx + s * (.56f + .06f * i)
        val bw = s * (.075f + .025f * ornHash(i, 402))
        val bh = bw * .72f
        withTransform({ rotate((ornHash(i, 403) - .5f) * 44f, Offset(bx, yb)) }) {
            drawRoundRect(stone, Offset(bx - bw / 2f, yb - bh), Size(bw, bh), CornerRadius(bw * .10f))
            drawRect(stoneL.al(.85f), Offset(bx - bw / 2f, yb - bh), Size(bw, bh * .20f))
        }
    }
}

/** Bunga bangkai: lima kelopak bertotol dengan cakram tengah. Digambar dari atas lalu dipipihkan supaya terlihat di tanah. */
private fun DrawScope.idnRafflesia(P: OrnPaint, x: Float, y: Float, r: Float) {
    val pet = mixc(P.d, P.hot, .55f)
    val petD = mixc(pet, P.ink, .28f)
    drawOval(P.ink.al(.16f), Offset(x - r * 1.15f, y - r * .12f), Size(r * 2.3f, r * .34f))
    withTransform({ scale(1f, .42f, Offset(x, y)) }) {
        for (k in 0 until 5) {
            val a = k * (2f * PI.toFloat() / 5f) - PI.toFloat() / 2f
            drawCircle(pet, r * .50f, Offset(x + cos(a) * r * .56f, y + sin(a) * r * .56f))
        }
        IP.reset()
        for (k in 0 until 5) {
            val a = k * (2f * PI.toFloat() / 5f) - PI.toFloat() / 2f
            for (m in 0 until 3) {
                val b = a + (m - 1) * .42f
                val dx = x + cos(b) * r * .64f; val dy = y + sin(b) * r * .64f
                IP.addOval(Rect(dx - r * .07f, dy - r * .07f, dx + r * .07f, dy + r * .07f))
            }
        }
        drawPath(IP, P.pp(.34f))
        drawCircle(petD, r * .44f, Offset(x, y))
        drawCircle(P.ink.al(.50f), r * .27f, Offset(x, y))
        drawCircle(petD, r * .12f, Offset(x, y))
    }
}

/**
 * Badak Jawa dari samping (menghadap +x, lalu dicerminkan oleh [dir]): badan besar berlipatan seperti baju zirah, satu cula kecil,
 * bibir atas yang lentur, telinga yang berkibas. [graze] 0 = kepala terangkat, 1 = menunduk merumput; [ear] = kibasan telinga.
 */
private fun DrawScope.idnRhino(P: OrnPaint, x: Float, y: Float, s: Float, dir: Float, t: Float, graze: Float, ear: Float) {
    val skin = mixc(P.c, P.ink, .30f)
    val skinD = mixc(P.c, P.ink, .52f)
    val skinL = mixc(P.c, P.paper, .20f)
    val horn = mixc(P.b, P.paper, .50f)
    val tw = sin(t * 1.3f) * .03f
    val breathe = 1f + .010f * sin(t * .95f)
    fun X(v: Float) = x + v * s
    fun Y(v: Float) = y + v * s
    withTransform({ scale(dir, 1f, Offset(x, y)); scale(1f, breathe, Offset(x, y)) }) {
        drawOval(P.ink.al(.16f), Offset(X(-.50f), Y(-.025f)), Size(s * 1.02f, s * .06f))
        // kaki sisi jauh
        drawRoundRect(skinD, Offset(X(.13f), Y(-.22f)), Size(s * .105f, s * .22f), CornerRadius(s * .03f))
        drawRoundRect(skinD, Offset(X(-.43f), Y(-.22f)), Size(s * .115f, s * .22f), CornerRadius(s * .03f))
        // ekor kecil berjumbai
        IP.reset(); IP.moveTo(X(-.47f), Y(-.30f)); IP.quadraticBezierTo(X(-.55f), Y(-.24f), X(-.54f + tw), Y(-.12f))
        drawPath(IP, skinD, style = Stroke(s * .02f, cap = StrokeCap.Round))
        drawCircle(skinD, s * .017f, Offset(X(-.54f + tw), Y(-.11f)))
        // badan
        IP.reset()
        IP.moveTo(X(-.47f), Y(-.30f))
        IP.cubicTo(X(-.47f), Y(-.42f), X(-.30f), Y(-.43f), X(-.10f), Y(-.41f))
        IP.cubicTo(X(.06f), Y(-.40f), X(.18f), Y(-.46f), X(.30f), Y(-.41f))
        IP.cubicTo(X(.41f), Y(-.37f), X(.45f), Y(-.27f), X(.42f), Y(-.20f))
        IP.cubicTo(X(.39f), Y(-.13f), X(.30f), Y(-.12f), X(.20f), Y(-.13f))
        IP.cubicTo(X(.00f), Y(-.11f), X(-.30f), Y(-.12f), X(-.42f), Y(-.16f))
        IP.cubicTo(X(-.50f), Y(-.20f), X(-.51f), Y(-.26f), X(-.47f), Y(-.30f))
        IP.close()
        drawPath(IP, skin)
        drawOval(skinD.al(.40f), Offset(X(-.42f), Y(-.23f)), Size(s * .84f, s * .11f))
        IP.reset(); IP.moveTo(X(-.36f), Y(-.405f)); IP.quadraticBezierTo(X(-.06f), Y(-.445f), X(.26f), Y(-.43f))
        drawPath(IP, skinL.al(.55f), style = Stroke(s * .016f, cap = StrokeCap.Round))
        // lipatan kulit: bahu, pelana, pinggul, leher
        IP2.reset()
        IP2.moveTo(X(.16f), Y(-.43f)); IP2.cubicTo(X(.20f), Y(-.35f), X(.20f), Y(-.25f), X(.15f), Y(-.15f))
        IP2.moveTo(X(-.17f), Y(-.42f)); IP2.cubicTo(X(-.13f), Y(-.34f), X(-.13f), Y(-.24f), X(-.18f), Y(-.13f))
        IP2.moveTo(X(-.36f), Y(-.38f)); IP2.cubicTo(X(-.31f), Y(-.31f), X(-.31f), Y(-.23f), X(-.36f), Y(-.15f))
        IP2.moveTo(X(.32f), Y(-.41f)); IP2.cubicTo(X(.35f), Y(-.35f), X(.35f), Y(-.28f), X(.33f), Y(-.22f))
        drawPath(IP2, P.ink.al(.36f), style = Stroke(s * .013f, cap = StrokeCap.Round))
        withTransform({ translate(s * .008f, s * .006f) }) {
            drawPath(IP2, skinL.al(.30f), style = Stroke(s * .008f, cap = StrokeCap.Round))
        }
        // tekstur kulit: goresan pendek
        IP3.reset()
        for (k in 0 until 14) {
            val px = X(-.38f + .76f * ornHash(k, 411)); val py = Y(-.37f + .19f * ornHash(k, 412))
            IP3.moveTo(px, py); IP3.lineTo(px + s * .022f, py + s * .008f)
        }
        drawPath(IP3, P.ink.al(.20f), style = Stroke(s * .007f, cap = StrokeCap.Round))
        // kaki sisi dekat + lipatan pergelangan + kuku
        drawRoundRect(skin, Offset(X(.23f), Y(-.22f)), Size(s * .115f, s * .22f), CornerRadius(s * .03f))
        drawRoundRect(skin, Offset(X(-.37f), Y(-.22f)), Size(s * .125f, s * .22f), CornerRadius(s * .03f))
        drawRect(skinD.al(.28f), Offset(X(.23f), Y(-.22f)), Size(s * .028f, s * .22f))
        drawRect(skinD.al(.28f), Offset(X(-.37f), Y(-.22f)), Size(s * .030f, s * .22f))
        IP2.reset()
        IP2.moveTo(X(.23f), Y(-.09f)); IP2.lineTo(X(.345f), Y(-.09f))
        IP2.moveTo(X(-.37f), Y(-.09f)); IP2.lineTo(X(-.245f), Y(-.09f))
        drawPath(IP2, P.ink.al(.28f), style = Stroke(s * .008f, cap = StrokeCap.Round))
        for (k in 0..2) {
            drawRoundRect(skinD, Offset(X(.235f + .036f * k), Y(-.028f)), Size(s * .028f, s * .028f), CornerRadius(s * .008f))
            drawRoundRect(skinD, Offset(X(-.365f + .040f * k), Y(-.028f)), Size(s * .030f, s * .028f), CornerRadius(s * .008f))
        }
        // leher + kepala: menunduk merumput bila graze naik
        withTransform({ rotate(12f + 40f * graze, Offset(X(.34f), Y(-.31f))) }) {
            IP.reset()
            IP.moveTo(X(.26f), Y(-.43f))
            IP.cubicTo(X(.38f), Y(-.47f), X(.50f), Y(-.44f), X(.61f), Y(-.38f))
            IP.cubicTo(X(.69f), Y(-.34f), X(.72f), Y(-.29f), X(.68f), Y(-.25f))
            IP.cubicTo(X(.64f), Y(-.21f), X(.54f), Y(-.21f), X(.46f), Y(-.20f))
            IP.cubicTo(X(.38f), Y(-.19f), X(.30f), Y(-.20f), X(.26f), Y(-.24f))
            IP.close()
            drawPath(IP, skin)
            // cula
            IP.reset(); IP.moveTo(X(.58f), Y(-.395f)); IP.lineTo(X(.655f), Y(-.365f)); IP.lineTo(X(.625f), Y(-.465f)); IP.close()
            drawPath(IP, horn)
            // lipatan leher, bibir atas, mata, lubang hidung
            IP2.reset()
            IP2.moveTo(X(.30f), Y(-.44f)); IP2.quadraticBezierTo(X(.32f), Y(-.36f), X(.30f), Y(-.27f))
            IP2.moveTo(X(.50f), Y(-.25f)); IP2.quadraticBezierTo(X(.62f), Y(-.235f), X(.70f), Y(-.27f))
            drawPath(IP2, P.ink.al(.34f), style = Stroke(s * .009f, cap = StrokeCap.Round))
            drawCircle(P.ink, s * .015f, Offset(X(.47f), Y(-.37f)))
            drawCircle(P.pp(.80f), s * .005f, Offset(X(.472f), Y(-.375f)))
            drawCircle(P.ink.al(.75f), s * .011f, Offset(X(.68f), Y(-.30f)))
            // telinga
            withTransform({ rotate(ear * 24f, Offset(X(.31f), Y(-.43f))) }) {
                IP.reset(); IP.moveTo(X(.28f), Y(-.43f)); IP.quadraticBezierTo(X(.24f), Y(-.56f), X(.31f), Y(-.58f))
                IP.quadraticBezierTo(X(.37f), Y(-.52f), X(.35f), Y(-.43f)); IP.close()
                drawPath(IP, skinD)
            }
        }
    }
}

/**
 * Komodo dari samping (menghadap +x, dicerminkan oleh [dir]). [ph] = fase langkah (diturunkan dari JARAK tempuh, jadi kaki tidak
 * pernah meluncur di tanah dan diam bersama badan), [tongue] 0..1 = julur lidah bercabang. Gaya jalan diagonal.
 */
private fun DrawScope.idnKomodo(P: OrnPaint, x: Float, y: Float, s: Float, dir: Float, t: Float, ph: Float, tongue: Float) {
    val skin = mixc(P.c, P.ink, .40f)
    val skinD = mixc(P.c, P.ink, .60f)
    val skinL = mixc(P.c, P.paper, .14f)
    val tng = mixc(P.d, P.hot, .60f)
    val n = 22
    val yc0 = -.135f
    val amp = .06f
    fun X(v: Float) = x + v * s
    fun Y(v: Float) = y + v * s
    fun th(xs: Float): Float = when {
        xs < -.30f -> .012f + .075f * ornSmooth((xs + 1f) / .70f)
        xs < .14f -> .087f + .012f * sin((xs + .30f) / .44f * PI.toFloat())
        xs < .34f -> .087f - .033f * ornSmooth((xs - .14f) / .20f)
        xs < .42f -> .054f - .008f * ((xs - .34f) / .08f)
        else -> .046f - .016f * ((xs - .42f) / .14f)
    }
    fun yc(xs: Float): Float {
        val tail = (-.30f - xs).coerceAtLeast(0f)
        val head = (xs - .34f).coerceAtLeast(0f)
        return yc0 + tail * .075f - head * .10f +
            sin(ph * .5f - xs * 6f) * .014f * (tail * 2f).coerceAtMost(1f) + sin(t * .6f - xs * 5f) * .004f + sin(ph * 2f) * .004f
    }
    val q2 = 2f * PI.toFloat()
    fun leg(hx: Float, p0: Float, fore: Boolean, col: Color, wd: Float) {
        val q = ornMod(p0 / q2, 1f)
        val fxp: Float
        val lift: Float
        if (q < .5f) { fxp = amp * (1f - 4f * q); lift = 0f }
        else { val g = (q - .5f) * 2f; fxp = -amp + 2f * amp * ornSmooth(g); lift = sin(g * PI.toFloat()) * .035f }
        val hy = yc(hx) + th(hx) * .35f
        val fx = hx + fxp + (if (fore) .02f else -.02f)
        val fy = -lift
        val kx = (hx + fx) / 2f + (if (fore) -.05f else .05f)
        val ky = (hy + fy) / 2f - .02f
        IP.reset(); IP.moveTo(X(hx), Y(hy)); IP.lineTo(X(kx), Y(ky)); IP.lineTo(X(fx), Y(fy))
        drawPath(IP, col, style = Stroke(s * wd, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawOval(col, Offset(X(fx) - s * .02f, Y(fy) - s * .016f), Size(s * .07f, s * .03f))
        IP3.reset()
        for (k in 0..2) { IP3.moveTo(X(fx + .048f), Y(fy - .012f + .011f * k)); IP3.lineTo(X(fx + .082f), Y(fy - .004f + .011f * k)) }
        drawPath(IP3, skinL.al(.85f), style = Stroke(s * .007f, cap = StrokeCap.Round))
    }

    withTransform({ scale(dir, 1f, Offset(x, y)) }) {
        drawOval(P.ink.al(.14f), Offset(X(-1.0f), Y(-.02f)), Size(s * 1.56f, s * .045f))
        // sisi jauh
        leg(.22f, ph + PI.toFloat(), true, skinD, .055f)
        leg(-.20f, ph, false, skinD, .060f)
        // badan: satu path dari ujung ekor ke moncong, lalu kembali lewat perut
        IP.reset()
        for (i in 0..n) {
            val xs = -1f + 1.56f * i / n
            val tx = X(xs); val ty = Y(yc(xs) - th(xs))
            IX[i] = tx; IY[i] = ty
            if (i == 0) IP.moveTo(tx, ty) else IP.lineTo(tx, ty)
        }
        for (i in n downTo 0) {
            val xs = -1f + 1.56f * i / n
            IP.lineTo(X(xs), Y(yc(xs) + th(xs) * .85f))
        }
        IP.close()
        drawPath(IP, skin)
        drawCircle(skin, s * .030f, Offset(X(.548f), Y(yc(.548f))))
        // sorot punggung + sisik
        IP3.reset(); IP3.moveTo(IX[2], IY[2] + s * .012f)
        for (i in 3..n - 3) IP3.lineTo(IX[i], IY[i] + s * .012f)
        drawPath(IP3, skinL.al(.50f), style = Stroke(s * .014f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        IP2.reset()
        for (i in 3..n - 4) {
            val xs = -1f + 1.56f * i / n
            val bx = X(xs); val by = Y(yc(xs) - th(xs) * .85f)
            IP2.moveTo(bx, by); IP2.lineTo(bx + s * .012f, by + th(xs) * s * .9f)
        }
        drawPath(IP2, P.ink.al(.26f), style = Stroke(s * .007f, cap = StrokeCap.Round))
        // kepala: mata, lubang hidung, garis mulut
        val hy = yc(.48f)
        drawCircle(P.ink, s * .011f, Offset(X(.45f), Y(hy - .018f)))
        drawCircle(P.pp(.80f), s * .004f, Offset(X(.453f), Y(hy - .021f)))
        drawCircle(P.ink.al(.70f), s * .006f, Offset(X(.535f), Y(yc(.535f) - .022f)))
        drawLine(P.ink.al(.50f), Offset(X(.40f), Y(yc(.40f) + .010f)), Offset(X(.565f), Y(yc(.56f) + .004f)), strokeWidth = s * .006f, cap = StrokeCap.Round)
        // sisi dekat
        leg(.22f, ph, true, skin, .060f)
        leg(-.20f, ph + PI.toFloat(), false, skin, .065f)
        // lidah bercabang
        if (tongue > .02f) {
            val bx = X(.575f); val by = Y(yc(.56f) + .006f)
            val l = tongue * s * .20f
            val wob = sin(t * 28f) * s * .012f * tongue
            val ex = bx + l; val ey = by + wob
            drawLine(tng, Offset(bx, by), Offset(ex, ey), strokeWidth = s * .008f, cap = StrokeCap.Round)
            drawLine(tng, Offset(ex, ey), Offset(ex + s * .03f * tongue, ey - s * .018f * tongue), strokeWidth = s * .007f, cap = StrokeCap.Round)
            drawLine(tng, Offset(ex, ey), Offset(ex + s * .03f * tongue, ey + s * .018f * tongue), strokeWidth = s * .007f, cap = StrokeCap.Round)
        }
    }
}

/**
 * Orangutan bergelantung dengan satu lengan pada dahan (pegangan di (gx, gy)) sambil makan buah. Menghadap +x, dicerminkan oleh [dir];
 * seluruh badan berayun dengan sudut [ang] (radian) mengelilingi pegangan, kaki menjuntai dan tertinggal sedikit dari badan.
 */
private fun DrawScope.idnOrangutan(P: OrnPaint, gx: Float, gy: Float, s: Float, dir: Float, ang: Float, t: Float) {
    val fur = mixc(P.d, P.hot, .55f)
    val furD = mixc(fur, P.ink, .34f)
    val furL = mixc(fur, P.paper, .26f)
    val face = mixc(furD, P.ink, .40f)
    val muz = mixc(fur, P.paper, .46f)
    val fruit = mixc(P.d, P.hot2, .70f)
    fun X(v: Float) = gx + v * s
    fun Y(v: Float) = gy + v * s
    val pv = Offset(gx, gy)
    val chew = sin(t * 2.6f)
    val fk = .5f + .5f * sin(t * .7f)
    val lg = sin(t * 1.05f)
    withTransform({ rotate(ang * 57.2958f, pv); scale(dir, 1f, pv) }) {
        // kaki menjuntai
        IP.reset(); IP.moveTo(X(.14f), Y(1.88f)); IP.lineTo(X(.34f + .04f * lg), Y(2.18f)); IP.lineTo(X(.26f + .07f * lg), Y(2.50f))
        drawPath(IP, furD, style = Stroke(s * .19f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        IP.reset(); IP.moveTo(X(-.02f), Y(1.90f)); IP.lineTo(X(-.20f + .04f * lg), Y(2.22f)); IP.lineTo(X(.00f + .08f * lg), Y(2.56f))
        drawPath(IP, fur, style = Stroke(s * .20f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawOval(furD, Offset(X(.08f * lg - .12f), Y(2.52f)), Size(s * .26f, s * .13f))
        drawOval(furD, Offset(X(.26f + .07f * lg - .11f), Y(2.46f)), Size(s * .24f, s * .12f))
        // badan: bahu lebar, dada berbulu, perut lebih terang
        drawOval(furD, Offset(X(-.28f), Y(1.02f)), Size(s * .64f, s * 1.0f))
        drawOval(fur, Offset(X(-.24f), Y(1.04f)), Size(s * .56f, s * .93f))
        drawOval(fur, Offset(X(-.31f), Y(.90f)), Size(s * .68f, s * .46f))
        drawOval(furL.al(.55f), Offset(X(-.03f), Y(1.32f)), Size(s * .30f, s * .52f))
        // rumbai bulu di bawah badan
        IP3.reset()
        for (k in 0..5) {
            val x0 = -.24f + .10f * k
            IP3.moveTo(X(x0), Y(1.86f)); IP3.lineTo(X(x0 + .05f + .02f * sin(t * 1.4f + k)), Y(2.10f)); IP3.lineTo(X(x0 + .12f), Y(1.84f)); IP3.close()
        }
        drawPath(IP3, fur.al(.90f))
        // lengan yang bergantung + bulu panjang menjuntai di sepanjang lengan
        IP.reset(); IP.moveTo(X(.0f), Y(1.05f)); IP.quadraticBezierTo(X(-.12f), Y(.55f), X(.0f), Y(.04f))
        drawPath(IP, furD, style = Stroke(s * .24f, cap = StrokeCap.Round))
        drawPath(IP, fur, style = Stroke(s * .18f, cap = StrokeCap.Round))
        IP2.reset()
        for (k in 0..4) {
            val y0 = .25f + .16f * k
            val x0 = -.07f - .02f * sin(k.toFloat())
            IP2.moveTo(X(x0), Y(y0)); IP2.lineTo(X(x0 - .02f + .03f * sin(t * 1.3f + k)), Y(y0 + .22f)); IP2.lineTo(X(x0 + .08f), Y(y0 + .02f)); IP2.close()
        }
        drawPath(IP2, furD.al(.85f))
        // tangan mencengkeram dahan
        drawOval(furD, Offset(X(-.13f), Y(-.07f)), Size(s * .26f, s * .17f))
        for (k in -1..1) drawLine(furD, Offset(X(.06f * k), Y(-.02f)), Offset(X(.06f * k + .02f), Y(-.11f)), strokeWidth = s * .05f, cap = StrokeCap.Round)
        // kepala: jambul, tengkorak, wajah gelap, bantalan pipi, moncong, mata, alis, jenggot
        IP.reset()
        IP.moveTo(X(.10f), Y(.88f)); IP.lineTo(X(.12f), Y(.58f)); IP.lineTo(X(.22f), Y(.76f)); IP.lineTo(X(.28f), Y(.52f))
        IP.lineTo(X(.36f), Y(.74f)); IP.lineTo(X(.46f), Y(.60f)); IP.lineTo(X(.50f), Y(.90f)); IP.close()
        drawPath(IP, fur)
        drawOval(furD, Offset(X(.04f), Y(.78f)), Size(s * .54f, s * .50f))
        drawOval(fur, Offset(X(.07f), Y(.80f)), Size(s * .48f, s * .44f))
        drawOval(face, Offset(X(.24f), Y(.88f)), Size(s * .32f, s * .38f))
        drawOval(face.al(.90f), Offset(X(.18f), Y(.93f)), Size(s * .10f, s * .26f))
        drawOval(muz, Offset(X(.38f), Y(1.04f + .008f * chew)), Size(s * .20f, s * .15f))
        drawLine(P.ink.al(.55f), Offset(X(.40f), Y(1.14f + .010f * chew)), Offset(X(.56f), Y(1.12f)), strokeWidth = s * .018f, cap = StrokeCap.Round)
        drawCircle(P.ink.al(.80f), s * .012f, Offset(X(.50f), Y(1.07f)))
        drawCircle(P.ink, s * .026f, Offset(X(.38f), Y(.97f)))
        drawCircle(P.ink, s * .024f, Offset(X(.48f), Y(.98f)))
        drawCircle(P.pp(.85f), s * .009f, Offset(X(.389f), Y(.961f)))
        drawLine(P.ink.al(.60f), Offset(X(.33f), Y(.92f)), Offset(X(.52f), Y(.93f)), strokeWidth = s * .020f, cap = StrokeCap.Round)
        IP3.reset()
        for (k in 0..2) {
            val x0 = .30f + .06f * k
            IP3.moveTo(X(x0), Y(1.20f)); IP3.lineTo(X(x0 + .02f * sin(t * 1.5f + k) + .02f), Y(1.44f)); IP3.lineTo(X(x0 + .10f), Y(1.20f)); IP3.close()
        }
        drawPath(IP3, furL.al(.90f))
        // lengan yang memegang buah ke mulut
        val hx = .46f + .03f * fk; val hy = 1.20f - .05f * fk
        IP.reset(); IP.moveTo(X(.12f), Y(1.18f)); IP.quadraticBezierTo(X(.62f), Y(1.46f), X(hx), Y(hy))
        drawPath(IP, furD, style = Stroke(s * .21f, cap = StrokeCap.Round))
        drawPath(IP, fur, style = Stroke(s * .15f, cap = StrokeCap.Round))
        drawCircle(furD, s * .085f, Offset(X(hx), Y(hy)))
        drawCircle(fruit, s * .095f, Offset(X(hx + .04f), Y(hy - .05f)))
        drawCircle(P.pp(.50f), s * .025f, Offset(X(hx + .01f), Y(hy - .08f)))
        drawLine(P.ink.al(.60f), Offset(X(hx + .04f), Y(hy - .14f)), Offset(X(hx + .05f), Y(hy - .19f)), strokeWidth = s * .015f, cap = StrokeCap.Round)
    }
}

/**
 * Cendrawasih bertengger (kaki di (x, y)), menghadap +x, dicerminkan oleh [dir]: tujuh bulu sayap panjang menjuntai ke belakang,
 * dua kawat ekor yang berujung ikal, badan gelap, kepala kuning, dada hijau. [flare] melebarkan bulu, [hop] mengangkat burung.
 */
private fun DrawScope.idnParadise(P: OrnPaint, x: Float, y: Float, s: Float, dir: Float, t: Float, flare: Float, hop: Float) {
    val body = mixc(P.a, P.ink, .28f)
    val wing = mixc(P.c, P.ink, .16f)
    val head = mixc(P.d, P.hot2, .55f)
    val throat = mixc(P.c, P.paper, .08f)
    val plume = mixc(P.d, P.hot2, .62f)
    val beak = mixc(P.d, P.paper, .35f)
    val oy = -hop * s * .30f
    val bob = sin(t * .9f) * .012f
    fun X(v: Float) = x + v * s
    fun Y(v: Float) = y + oy + v * s
    withTransform({ scale(dir, 1f, Offset(x, y)) }) {
        // bulu sayap panjang
        val nn = 8
        for (k in 0 until 7) {
            val a0 = PI.toFloat() - .26f - .15f * k - flare * .10f * k
            val len = 1.55f + .16f * k + .10f * ornHash(k, 431)
            var px = X(-.14f - .035f * k); var py = Y(-.34f + .012f * k)
            RX[0] = px; RY[0] = py; RW[0] = s * .012f
            for (m in 1..nn) {
                val f = m / nn.toFloat()
                val a = a0 - .55f * f + sin(t * 1.3f + k * .9f + f * 2.6f) * .07f * f * (1f + flare * 2f)
                px += cos(a) * len * s / nn; py += sin(a) * len * s / nn
                RX[m] = px; RY[m] = py
                RW[m] = s * (.012f + .085f * sin(PI.toFloat() * f))
            }
            ribbon(IP2, nn, 0f, 0f, true)
            drawPath(IP2, plume.al(.46f + .06f * (k % 3)))
            IP3.reset(); IP3.moveTo(RX[0], RY[0])
            for (m in 1..nn) IP3.lineTo(RX[m], RY[m])
            drawPath(IP3, mixc(plume, P.ink, .30f).al(.55f), style = Stroke(s * .008f, cap = StrokeCap.Round))
        }
        // dua kawat ekor dengan ujung ikal
        for (j in 0 until 2) {
            val bx = X(-.50f); val by = Y(-.33f + .02f * j)
            val ln = (2.1f + .5f * j) * s
            IP3.reset(); IP3.moveTo(bx, by)
            var ex = bx; var ey = by
            val wn = 14
            for (m in 1..wn) {
                val f = m / wn.toFloat()
                ex = bx - ln * f
                ey = by + ln * (.10f + .10f * j) * f * f + sin(t * 1.1f + j * 1.7f + f * 3f) * s * .05f * f
                IP3.lineTo(ex, ey)
            }
            val rr = s * .11f
            for (q in 1..8) {
                val an = q / 8f * 2f * PI.toFloat() * 1.15f
                val k2 = 1f - q / 10f
                IP3.lineTo(ex - rr * sin(an) * k2, ey - rr + rr * cos(an) * k2)
            }
            drawPath(IP3, P.ink.al(.85f), style = Stroke(s * .016f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // kaki
        drawLine(P.ink, Offset(X(.02f), Y(-.24f)), Offset(X(.0f), y), strokeWidth = s * .035f, cap = StrokeCap.Round)
        drawLine(P.ink, Offset(X(.14f), Y(-.24f)), Offset(X(.16f), y), strokeWidth = s * .035f, cap = StrokeCap.Round)
        // badan, sayap lipat, dada hijau
        IP.reset()
        IP.moveTo(X(.38f), Y(-.50f))
        IP.cubicTo(X(.40f), Y(-.30f), X(.20f), Y(-.15f), X(-.10f), Y(-.20f))
        IP.lineTo(X(-.52f), Y(-.32f))
        IP.cubicTo(X(-.45f), Y(-.50f), X(-.15f), Y(-.62f), X(.10f), Y(-.62f))
        IP.cubicTo(X(.25f), Y(-.62f), X(.35f), Y(-.58f), X(.38f), Y(-.50f))
        IP.close()
        drawPath(IP, body)
        drawOval(throat, Offset(X(.26f), Y(-.50f)), Size(s * .22f, s * .20f))
        IP.reset()
        IP.moveTo(X(.18f), Y(-.52f)); IP.cubicTo(X(.08f), Y(-.40f), X(-.15f), Y(-.30f), X(-.46f), Y(-.36f))
        IP.cubicTo(X(-.30f), Y(-.52f), X(-.05f), Y(-.60f), X(.18f), Y(-.52f)); IP.close()
        drawPath(IP, wing)
        IP3.reset()
        for (k in 0..2) { IP3.moveTo(X(.06f - .13f * k), Y(-.50f + .03f * k)); IP3.lineTo(X(-.20f - .13f * k), Y(-.40f + .02f * k)) }
        drawPath(IP3, P.pp(.28f), style = Stroke(s * .010f, cap = StrokeCap.Round))
        // kepala + paruh + mata
        drawCircle(head, s * .15f, Offset(X(.40f), Y(-.66f + bob)))
        IP.reset(); IP.moveTo(X(.52f), Y(-.69f + bob)); IP.lineTo(X(.78f), Y(-.63f + bob)); IP.lineTo(X(.52f), Y(-.60f + bob)); IP.close()
        drawPath(IP, beak)
        drawCircle(P.ink, s * .030f, Offset(X(.46f), Y(-.68f + bob)))
        drawCircle(P.pp(.85f), s * .010f, Offset(X(.47f), Y(-.69f + bob)))
    }
}

/** Cendrawasih jauh yang terbang: siluet kecil dengan ekor panjang berayun dan sayap mengepak. */
private fun DrawScope.idnBirdFly(P: OrnPaint, x: Float, y: Float, s: Float, dir: Float, t: Float) {
    val c = mixc(P.ink, P.paper, .15f).al(.75f)
    val fl = sin(t * 5.5f)
    withTransform({ scale(dir, 1f, Offset(x, y)) }) {
        IP.reset(); IP.moveTo(x - s * .30f, y)
        IP.quadraticBezierTo(x - s * 1.4f, y + s * (.10f + .15f * sin(t * 1.4f)), x - s * 2.3f, y + s * (.55f + .20f * sin(t * 1.2f + 1f)))
        drawPath(IP, c, style = Stroke(s * .06f, cap = StrokeCap.Round))
        drawOval(c, Offset(x - s * .42f, y - s * .15f), Size(s * .84f, s * .30f))
        drawCircle(c, s * .13f, Offset(x + s * .42f, y - s * .06f))
        birdWing(c, x - s * .05f, y - s * .05f, s * .62f, fl, s * .10f)
    }
}

private fun DrawScope.drawIndonesiaScene(P: OrnPaint, w: Float, h: Float, t: Float, fx: OrnFx?) {
    val u = w
    val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val pop = fx?.pop ?: 0f; val popX = fx?.popX ?: 0f; val popY = fx?.popY ?: 0f
    val tx = fx?.px ?: 0f
    val calm = fx?.calm ?: 0f
    val par = parX(fx, u)
    val ta = tapAge(fx)
    // angin bersama: dua gelombang lambat yang tak sebanding
    val wind = (.036f * sin(t * .21f) + .024f * sin(t * .53f + 1.3f)) * (1f - .5f * calm)
    fun kick(ox: Float): Float = if (abs(pop) > .003f) ((ox - popX) / u).coerceIn(-1.2f, 1.2f) * pop * .30f else 0f
    fun gust(ox: Float): Float = if (pr > .01f) ((ox - tx) / u).coerceIn(-1.2f, 1.2f) * pr * .16f else 0f
    fun sw(ph: Float, a: Float): Float = sin(t * .45f + ph) * a + wind + lf * .10f
    val flAdd = abs(pop) * 1.4f
    val lk = 1f + .05f * hd
    val yg = h - u * .115f        // garis tanah lapis tengah (candi, badak)
    val yf = h - u * .030f        // garis tanah depan (komodo)

    // ---- langit hutan: terang di atas, hijau tua di dasar -----------------------------------------------------------------------------------------------
    drawRect(Brush.verticalGradient(listOf(P.b.al(.32f), P.d.al(.07f), P.c.al(.14f), P.ink.al(.24f)), startY = 0f, endY = h), Offset.Zero, Size(w, h))

    // ---- berkas cahaya menembus kanopi -----------------------------------------------------------------------------------------------------------------------
    for (i in 0 until 3) {
        val x0 = u * (.10f + .30f * i) + sin(t * .12f + i * 2.1f) * u * .04f
        val wd = u * (.06f + .025f * i)
        val sk = u * .45f
        val br = .5f + .5f * sin(t * .21f + i * 1.9f)
        SP.reset(); SP.moveTo(x0, 0f); SP.lineTo(x0 + wd, 0f); SP.lineTo(x0 + wd - sk, yg); SP.lineTo(x0 - sk, yg); SP.close()
        drawPath(SP, Brush.verticalGradient(listOf(P.d.al((.12f + .10f * br) * (1f - .6f * calm)), Color.Transparent), startY = 0f, endY = yg))
    }

    // ---- punggung bukit jauh, pohon menjulang, menara candi jauh ---------------------------------------------------------------------------------------
    fun ridgeY(layer: Int, x: Float): Float {
        val base = yg - u * (.17f - .075f * layer)
        val amp = u * (.10f - .03f * layer)
        return base - (sin(x / u * (4.2f + 2.4f * layer) + layer * 2.3f) * .5f + .5f) * amp - sin(x / u * 15f + layer) * u * .006f
    }
    fun ridge(layer: Int) {
        SP.reset(); SP.moveTo(-u * .05f, h + 2f); SP.lineTo(-u * .05f, ridgeY(layer, -u * .05f))
        var x = -u * .05f
        while (x <= u * 1.05f) { SP.lineTo(x, ridgeY(layer, x)); x += u * .04f }
        SP.lineTo(u * 1.05f, h + 2f); SP.close()
        drawPath(SP, (if (layer == 0) mixc(P.b, P.ink, .10f) else mixc(P.a, P.ink, .18f)).al(if (layer == 0) .42f else .60f))
        IP.reset()
        for (i in 0 until 12) {
            val cx = u * (.03f + .085f * i + .02f * ornHash(i + 12 * layer, 441))
            val r = u * (.026f + .018f * ornHash(i + 12 * layer, 442))
            val cy = ridgeY(layer, cx) + u * .004f
            IP.addOval(Rect(cx - r, cy - r * .75f, cx + r, cy + r * .75f))
        }
        drawPath(IP, (if (layer == 0) mixc(P.a, P.ink, .05f) else mixc(P.a, P.ink, .24f)).al(if (layer == 0) .40f else .55f))
    }
    withTransform({ translate(-par * u * .012f, 0f) }) {
        ridge(0)
        IP.reset(); IP2.reset()
        for (i in 0 until 3) {
            val tx0 = u * (.12f + .40f * i + .04f * ornHash(i, 451))
            val ty0 = ridgeY(0, tx0)
            val hh = u * (.24f + .06f * ornHash(i, 452))
            val sy = sin(t * .3f + i) * u * .004f
            IP2.moveTo(tx0, ty0 + u * .01f); IP2.lineTo(tx0 + sy, ty0 - hh)
            for (k in 0 until 3) {
                val ox = (k - 1) * u * .05f
                val oy = ty0 - hh + (k % 2) * u * .015f
                IP.addOval(Rect(tx0 + sy + ox - u * .055f, oy - u * .02f, tx0 + sy + ox + u * .055f, oy + u * .02f))
            }
        }
        drawPath(IP2, mixc(P.a, P.ink, .20f).al(.40f), style = Stroke(u * .008f, cap = StrokeCap.Round))
        drawPath(IP, mixc(P.a, P.ink, .06f).al(.40f))
        idnSpire(u * .76f, yg - u * .045f, u * .21f, mixc(P.a, P.paper, .35f).al(.50f))
        ridge(1)
    }

    // ---- kabut tipis di antara bukit dan candi ------------------------------------------------------------------------------------------------------------------
    run {
        val my = yg - u * .20f + sin(t * .07f) * u * .02f
        val bh = u * .14f
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, P.pp(.34f * (1f - .4f * calm)), Color.Transparent), startY = my - bh, endY = my + bh),
            Offset(0f, my - bh), Size(w, bh * 2f)
        )
    }

    // ---- lapis tengah: tanah, serasah, candi, bunga bangkai, badak Jawa -----------------------------------------------------------------------------------
    withTransform({ translate(par * u * .02f, 0f) }) {
        SP.reset(); SP.moveTo(-u * .1f, h + 2f); SP.lineTo(-u * .1f, yg)
        var gx = -u * .1f
        while (gx <= u * 1.1f) { SP.lineTo(gx, yg + sin(gx / u * 5f + .6f) * u * .008f - u * .004f); gx += u * .05f }
        SP.lineTo(u * 1.1f, h + 2f); SP.close()
        drawPath(SP, Brush.verticalGradient(listOf(mixc(P.b, P.paper, .50f), mixc(P.a, P.paper, .28f)), startY = yg - u * .02f, endY = h))
        // serasah daun di tanah
        IP.reset()
        for (i in 0 until 12) {
            val lx = u * (.03f + .08f * i + .03f * ornHash(i, 461))
            val ly = yg + u * (.012f + .05f * ornHash(i, 462))
            leaf2(IP, lx, ly, (ornHash(i, 463) - .5f) * 1.2f, u * .028f, u * .010f, 1f, .1f, false)
        }
        drawPath(IP, P.d.al(.35f))

        idnCandi(P, u * .26f, yg + u * .004f, u * .46f, t, wind)
        idnRafflesia(P, u * .13f, yg + u * .058f, u * .040f)

        val graze = (ornSmooth(.5f + .5f * sin(t * .33f + 1f)) * (1f - .85f * abs(pop).coerceAtMost(1f))).coerceIn(0f, 1f)
        val et = ornMod(t + 2f, 6.5f)
        val ear = (if (et < .6f) sin(et / .6f * 12.566f) * (1f - et / .6f) else 0f) + 1.6f * pop.coerceIn(-1f, 1f)
        idnRhino(P, u * .66f, yg + u * .018f, u * .36f, 1f, t, graze, ear)
    }

    // ---- dua batang pohon besar pembingkai ----------------------------------------------------------------------------------------------------------------------
    withTransform({ translate(par * u * .03f, 0f) }) {
        idnTrunk(P, u * 1.01f, u * .10f, u * .13f, yg + u * .02f, 1.1f)
        idnTrunk(P, -u * .01f, u * .09f, u * .12f, yg + u * .02f, 3.7f)
    }

    // ---- kanopi: dahan kiri (cendrawasih), dahan kanan (orangutan), sulur, burung terbang ----------------------------------------------------
    withTransform({ translate(par * u * .035f, 0f) }) {
        // dahan kiri: cendrawasih bertengger di dekat ujungnya
        val b1y = u * .05f + h * .085f
        branch(P, -u * .03f, b1y, .13f, u * .54f, 6, .012f, sw(1.7f, .05f) + gust(u * .3f) + kick(u * .3f), t, 1.7f, u * .09f * lk, lf * 1.2f + flAdd, 1, 1f, calm)
        val perchX = RX[5]; val perchY = RY[5]
        val flare = abs(pop).coerceAtMost(1f)
        idnParadise(P, perchX, perchY - u * .004f, u * .088f, 1f, t, flare, flare)

        // dahan kanan: orangutan bergelantung dari simpul keempat
        val b2y = u * .07f + h * .06f
        branch(P, u * 1.03f, b2y, 3.04f, u * .66f, 7, .010f, sw(4.2f, .05f) + gust(u * .75f) + kick(u * .75f), t, 4.2f, u * .10f * lk, lf * 1.2f + flAdd, 2, 1f, calm)
        val ogx = RX[4]; val ogy = RY[4]
        val swing = .075f * sin(t * .55f + .8f) * (1f - .5f * calm) - .22f * pop.coerceIn(-1f, 1f) * sign01(ogx - popX)
        idnOrangutan(P, ogx, ogy, u * .095f, -1f, swing, t)

        vine(P, u * .17f, u * .30f, t, 1.1f, wind, 3)
        vine(P, u * .90f, u * .20f, t, 3.6f, wind, 5)

        // sepasang cendrawasih jauh melintas
        run {
            val per = 70f
            val k = ornMod(t + 18f, per) / per
            val bx = -u * .2f + k * u * 1.4f
            val by = h * .30f + sin(k * 7f) * u * .02f
            if (bx > -u * .1f && bx < u * 1.1f) {
                idnBirdFly(P, bx, by, u * .022f, 1f, t)
                idnBirdFly(P, bx - u * .11f, by + u * .035f, u * .018f, 1f, t + 1.3f)
            }
        }
    }

    // ---- tanah depan + komodo berpatroli -----------------------------------------------------------------------------------------------------------------------------
    run {
        val gy2 = yf - u * .035f
        SP.reset(); SP.moveTo(-u * .1f, h + 2f); SP.lineTo(-u * .1f, gy2)
        var gx = -u * .1f
        while (gx <= u * 1.1f) { SP.lineTo(gx, gy2 + sin(gx / u * 7f + 2f) * u * .006f); gx += u * .05f }
        SP.lineTo(u * 1.1f, h + 2f); SP.close()
        drawPath(SP, Brush.verticalGradient(listOf(mixc(P.a, P.paper, .22f), mixc(P.a, P.ink, .10f)), startY = gy2, endY = h))

        // patroli bolak-balik: berjalan, berhenti di ujung (menjulurkan lidah), lalu berbalik. Fase langkah dari jarak tempuh.
        val s = u * .36f
        val cyc = t / 60f + .31f
        val kf = ornMod(cyc, 1f)
        val rightward = kf < .5f
        val pr2 = if (rightward) kf * 2f else kf * 2f - 1f
        val ease = ornSmooth(((pr2 - .12f) / .76f).coerceIn(0f, 1f))
        val xl = u * .42f
        val kx = u * .30f + xl * (if (rightward) ease else 1f - ease)
        val dist = xl * (2f * cyc.toInt().toFloat() + (if (rightward) ease else 1f + ease))
        val kph = dist / (.24f * s) * 2f * PI.toFloat()
        val resting = pr2 < .10f || pr2 > .90f
        val tl = if (resting) .30f else .12f
        val tf = ornMod(t, 4.7f) / 4.7f
        var tongue = if (tf < tl) sin(tf / tl * PI.toFloat()) else 0f
        if (ta < 1.1f) tongue = max(tongue, sin(ta / 1.1f * PI.toFloat()))
        idnKomodo(P, kx, yf, s, if (rightward) 1f else -1f, t, kph, tongue)
    }

    // ---- pakis di sudut depan -----------------------------------------------------------------------------------------------------------------------------------------------------
    withTransform({ translate(par * u * .045f, 0f) }) {
        idnFern(P, u * .02f, h + u * .01f, u * .24f, t, wind * 3f + gust(u * .05f) + kick(u * .05f), .5f, P.a)
        idnFern(P, u * .11f, h + u * .02f, u * .17f, t, wind * 3f + gust(u * .11f) + kick(u * .11f), 1.9f, mixc(P.a, P.c, .4f))
        idnFern(P, u * .98f, h + u * .01f, u * .26f, t, wind * 3f + gust(u * .98f) + kick(u * .98f), 3.3f, P.a)
        idnFern(P, u * .88f, h + u * .02f, u * .17f, t, wind * 3f + gust(u * .88f) + kick(u * .88f), 4.6f, mixc(P.a, P.c, .4f))
    }

    // ---- daun jatuh: berputar 3D ---------------------------------------------------------------------------------------------------------------------------------------------------------
    for (i in 0 until 4) {
        val sz = u * (.03f + .016f * ornHash(i, 471))
        val per = h + sz * 6f
        val y = ornMod(ornHash(i, 473) * per + t * u * (.009f + .007f * ornHash(i, 474)), per) - sz * 3f
        val x = ornHash(i, 475) * u + sin(t * .5f + i * 2f) * u * .06f + wind * u * .6f + lf * u * .02f * (ornHash(i, 477) - .5f)
        val flip = abs(cos(t * (.45f + ornHash(i, 476) * .5f) + i))
        leafArt(P, P.pick(i + 1), x, y, t * (.35f + ornHash(i, 476) * .4f) + i + wind * 3f, sz * 2f, sz * .9f * (.28f + .72f * flip), 1f, false)
    }

    // ---- kunang-kunang: titik cahaya yang naik pelan dan berkedip -------------------------------------------------------------------------------------------
    for (i in 0 until 7) {
        val rr = u * (.006f + .006f * ornHash(i, 481))
        val per = h + u * .2f
        val y = ornMod(ornHash(i, 482) * per - t * u * (.008f + .006f * ornHash(i, 483)), per) - u * .1f
        val x = ornHash(i, 484) * u + sin(t * .3f + i * 1.9f) * u * .05f + par * u * .04f
        val tw = .5f + .5f * sin(t * (.4f + ornHash(i, 485) * .6f) + i * 3f)
        val col = mixc(P.d, P.hot2, .60f)
        drawCircle(col.al((.10f + .10f * tw) * (1f + .5f * hd)), rr * 3.2f, Offset(x, y))
        drawCircle(col.al(.45f + .35f * tw), rr, Offset(x, y))
    }

    // ---- ketukan: cincin cahaya + segenggam daun yang beterbangan dari titik ketuk ------------------------------------------------------------
    if (ta < 3.4f) {
        for (k in 0 until 9) {
            val age = ta - k * .05f
            if (age <= 0f) continue
            val vx = (ornHash(k, 491) - .5f) * u * .22f
            val x = popX + vx * age * (1f - age * .15f) + sin(age * 4f + k) * u * .010f
            val y = popY - age * u * (.12f + .08f * ornHash(k, 492)) + age * age * u * .045f
            val sz = u * (.011f + .008f * ornHash(k, 493))
            val fa = (1f - age / 3.2f).coerceIn(0f, 1f)
            val a2 = age * 3f + k
            leafArt(P, P.pick(k + 1).al(fa), x, y, a2, sz * 2f, sz * .9f * (.3f + .7f * abs(cos(a2))), 1f, false)
        }
        if (ta < 1.4f) {
            val rad = u * .05f + ta * u * .20f
            drawCircle(P.pp(.40f * (1f - ta / 1.4f)), rad, Offset(popX, popY), style = Stroke(u * .004f))
        }
    }
}

// =====================================================================================================================
// Penggambar utama
// =====================================================================================================================

/**
 * [w] x [h] = ukuran wadah (kartu, atau layar editor menurut tinggi yang tampak). [t] detik, [fx] null = pose diam.
 * [alpha] dan [enter] dipakai untuk pergantian backdrop: yang lama mengecil sedikit + pudar, yang baru membesar sedikit dengan pegas lembut.
 */
fun DrawScope.drawOrnament(kind: String, P0: OrnPaint, w: Float, h: Float, t: Float, fx: OrnFx?, alpha: Float = 1f, enter: Float = 1f) {
    if (kind.isEmpty() || w <= 1f || h <= 1f || alpha <= .002f) return
    val P = P0.alpha(alpha)
    val s = .90f + .10f * enter.coerceIn(0f, 1.1f)
    if (s > .9995f && s < 1.0005f) drawScene(kind, P, w, h, t, fx)
    else withTransform({ scale(s, s, Offset(w / 2f, h / 2f)) }) { drawScene(kind, P, w, h, t, fx) }
}

private fun DrawScope.drawScene(kind: String, P: OrnPaint, w: Float, h: Float, t: Float, fx: OrnFx?) {
    when (kind) {
        "morph" -> drawMorphScene(P, w, h, t, fx)
        "canopy" -> drawCanopyScene(P, w, h, t, fx)
        "skyline" -> drawSkylineScene(P, w, h, t, fx)
        "reef" -> drawReefScene(P, w, h, t, fx)
        "indonesia" -> drawIndonesiaScene(P, w, h, t, fx)
    }
}

/**
 * Pemanasan sekali per proses: menggambar tiap backdrop sekali ke bitmap kecil supaya kode (JIT), path, dan shader gradien
 * sudah siap SEBELUM animasi buka editor, bukan saat itu. Itulah penyebab ornamen "berhenti sejenak" di bukaan pertama.
 */
object OrnWarm {
    private var done = false
    private var gpuDone = false

    /**
     * Pemanasan GPU: bitmap di atas memanaskan JIT, tetapi shader gradien dan jalur path Skia baru dikompilasi saat pertama kali
     * digambar di kanvas layar sungguhan. Itu yang membuat ornamen "berhenti sekedip" sekali setelah catatan dibuka (saat pertama kali
     * ada gulir cepat / awan / pesawat / mode gelap muncul). Di sini semua adegan (terang + gelap, semua gestur aktif) digambar sekali
     * dengan alpha nyaris nol di pojok kecil kanvas pertama yang tampil (kartu di daftar), jadi biayanya terbayar sebelum editor dibuka.
     */
    fun gpu(ds: DrawScope) {
        if (gpuDone) return
        gpuDone = true
        try {
            val c = Color(0xFFEEEEEE)
            val fx = OrnFx().also { it.press = 1f; it.hold = 1f; it.lift = 1f; it.pop = .6f; it.popX = 40f; it.popY = 60f; it.calm = .5f; it.px = 50f; it.py = 80f }
            with(ds) {
                clipRect(0f, 0f, 120f, 200f) {
                    for (dk in listOf(false, true)) {
                        val op = OrnPaint(c, c, c, c, c, c, 1f, dk)
                        for (k in listOf("morph", "canopy", "skyline", "reef", "indonesia")) {
                            drawOrnament(k, op, 120f, 200f, 3f, fx, alpha = .02f)
                            drawOrnament(k, op, 120f, 200f, 31f, fx, alpha = .02f)
                        }
                    }
                }
            }
        } catch (e: Throwable) { }
    }

    fun run() {
        if (done) return
        done = true
        try {
            val bmp = androidx.compose.ui.graphics.ImageBitmap(96, 160)
            val cv = androidx.compose.ui.graphics.Canvas(bmp)
            val ds = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
            val pal = Color(0xFFEEEEEE)
            val op = OrnPaint(pal, pal, pal, pal, pal, pal)
            val fx = OrnFx().also { it.press = .5f; it.pop = .3f; it.px = 40f; it.py = 60f }
            ds.draw(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, cv, Size(96f, 160f)) {
                for (k in listOf("morph", "canopy", "skyline", "reef", "indonesia")) drawOrnament(k, op, 96f, 160f, 3f, fx)
            }
        } catch (e: Throwable) { }
    }
}

/** Dua OrnPaint dianggap sama bila semua warna dan parameternya sama (pemanggil boleh membuat instans baru tiap rekomposisi). */
private fun OrnPaint.sameAs(o: OrnPaint): Boolean =
    this === o || (a == o.a && b == o.b && c == o.c && d == o.d && ink == o.ink && paper == o.paper &&
        amt == o.amt && dark == o.dark && hot == o.hot && hot2 == o.hot2 && deep == o.deep)

/** Ingatan terakhir yang direkam ke layer sebuah kartu: selama tak ada yang berubah, kartu cukup menempelkan layer-nya. */
private class OrnCardKey {
    var id = ""
    var kind = ""
    var p: OrnPaint? = null
    var w = 0f
    var h = 0f
    var ver = -1
    fun matches(i: String, k: String, pp: OrnPaint, ww: Float, hh: Float, v: Int): Boolean =
        ver == v && w == ww && h == hh && id == i && kind == k && p?.sameAs(pp) == true
    fun set(i: String, k: String, pp: OrnPaint, ww: Float, hh: Float, v: Int) { id = i; kind = k; p = pp; w = ww; h = hh; ver = v }
}

/**
 * Ornamen kartu: digambar di belakang isi kartu (dan di belakang sampul), pose diam. Pasang tepat setelah .background(...).
 *
 * Adegan direkam SEKALI ke GraphicsLayer luring (tekstur GPU) lalu hanya ditempel. Tanpa ini setiap kartu yang terlihat menjalankan ulang
 * ratusan operasi path / gradien di GPU pada SETIAP frame gulir (dan setiap kali border / riak / animasi kartu menyentuh node yang sama),
 * padahal gambarnya diam. Perekaman ulang hanya terjadi bila ukuran, backdrop, warna, atau pose catatan itu berubah. Butuh Compose UI 1.7+.
 */
fun Modifier.cardOrnament(kind: String, P: OrnPaint, id: String): Modifier =
    if (kind.isEmpty()) this else composed {
        val layer = rememberGraphicsLayer()
        val key = remember { OrnCardKey() }
        Modifier.drawBehind {
            OrnWarm.run()
            OrnWarm.gpu(this)
            val ver = OrnPose.verOf(id)      // dibaca di fase gambar: hanya kartu milik id ini yang tergambar ulang saat editornya ditutup
            val w = size.width; val h = size.height
            if (w >= 1f && h >= 1f) {
                if (!key.matches(id, kind, P, w, h, ver)) {
                    layer.compositingStrategy = CompositingStrategy.Offscreen
                    layer.record(this, layoutDirection, IntSize(w.toInt(), h.toInt())) {
                        drawOrnament(kind, P, size.width, size.height, OrnPose.get(id), null)
                    }
                    key.set(id, kind, P, w, h, ver)
                }
                drawLayer(layer)
            }
        }
    }

/**
 * Pengatur laju gambar ornamen di editor / pratinjau.
 *  - Laju diam dibatasi ~60 fps (layar 90 / 120 / 144 Hz menggambar tiap 2 vsync): gerak latar sangat lambat (hitungan piksel per detik),
 *    jadi 60 fps tak terbedakan dari 120 fps, tetapi bebannya separuh. Saat disentuh / diketuk / diangkat laju kembali penuh.
 *  - Penjaga beban: biaya gambar (ms di thread utama) diukur; bila melewati ~34% jatah satu periode, laju diturunkan satu tingkat
 *    (dan naik lagi bila sudah longgar), supaya ornamen tidak pernah menggerus anggaran frame bagi teks / gulir pada perangkat lemah.
 */
private class OrnPacer(hz: Float, private val tag: String) {
    val vsSec = 1f / hz.coerceIn(30f, 240f)
    private val vs = vsSec * 1000f
    private val base = (hz / 60f + .5f).toInt().coerceIn(1, 4)
    private var n = 0
    private var cost = 0f
    var stride = base
        private set

    fun sample(ms: Float) {
        n++
        if (n <= 10) return                         // beberapa frame awal: JIT dingin / kompilasi shader, bukan beban sebenarnya
        val x = ms.coerceIn(0f, 30f)
        cost = if (n == 11) x else cost + (x - cost) * .08f
        if (stride < 4 && cost > .34f * stride * vs) stride++
        else if (stride > base && cost < .20f * (stride - 1) * vs) stride--
        if (ORN_DEBUG && n % 180 == 0) Log.d("OrnPerf", "$tag cost=${"%.2f".format(cost)}ms stride=$stride vsync=${"%.1f".format(vs)}ms")
    }
}

/** true = catat biaya gambar rata-rata dan laju terpilih ke logcat (tag OrnPerf) tiap ~3 detik; untuk mengukur di perangkat. */
private const val ORN_DEBUG = false

/** Tinggi wadah yang tampak dalam koordinat isi editor. Saat morph dari kartu, isi editor diskalakan; hasilnya = tinggi kartu / skala. */
private fun ornViewH(vm: AppVM, size: Size): Float {
    val mm = effMorph(vm) ?: return size.height
    if (!mm.card || mm.up) return size.height
    val tt = vm.openP.value.coerceIn(0f, 1f)
    if (tt >= .999f) return size.height
    val l = lerp(mm.l, 0f, tt); val r = lerp(mm.r, size.width, tt)
    val tp = lerp(mm.t, 0f, tt); val b = lerp(mm.b, size.height, tt)
    val k = ((r - l) / size.width).coerceIn(.05f, 1f)
    return (b - tp) / k
}

/**
 * Lapisan ornamen di editor / pratinjau. [vm] null = pratinjau (tanpa morph). Letakkan SETELAH latar kertas dan SEBELUM sampul / isi.
 * Mengganti [kind] = backdrop lama mengecil + pudar, yang baru membesar dengan pegas lembut. [clock] boleh dibagi dengan [OrnamentCoverOverlay].
 */
@Composable
fun OrnamentLayer(
    vm: AppVM?, kind: String, pal: Pal, fx: OrnFx, id: String, modifier: Modifier = Modifier.fillMaxSize(),
    clock: OrnClock = remember(id) { OrnClock(OrnPose.get(id)).also { it.cur = kind } },
    inten: Float = 1f
) {
    LaunchedEffect(kind, clock) {
        if (kind != clock.cur) {
            clock.prev = clock.cur; clock.cur = kind
            clock.sw = 0f
            val a = Animatable(0f)
            a.animateTo(1f, spring(.9f, 85f)) { clock.sw = value }
            clock.prev = ""
        }
    }
    DisposableEffect(id) { onDispose { OrnPose.bump(id) } }
    if (clock.cur.isEmpty() && clock.prev.isEmpty()) return
    val P = remember(pal, inten) { ornPaint(pal, inten) }
    val view = LocalView.current
    val pacer = remember(view) { OrnPacer(view.display?.refreshRate ?: 60f, id) }
    LaunchedEffect(id, clock) {
        OrnWarm.run()
        var last = 0L
        var sm = .016f
        var skip = 0
        while (true) withFrameNanos { n ->
            // Laju gambar: penuh selama ada interaksi (jari, ketukan, teks diangkat: adegan toh sudah tergambar tiap frame karena sinyalnya
            // berubah); saat diam dibatasi pengatur laju (~60 fps, atau lebih rendah bila beban berat); saat mengetik (ornamen sudah
            // redup dan lambat) separuhnya lagi. Frame yang dilewati tidak mengubah apa pun, dan dt berikutnya mencakup semuanya.
            val busy = fx.down || fx.press > .01f || abs(fx.pop) > .003f || fx.hold > .01f || (fx.lift > .001f && fx.lift < .999f)
            val every = if (busy) 1 else pacer.stride * (if (fx.calm > .6f) 2 else 1)
            if (++skip < every) return@withFrameNanos
            skip = 0
            if (last != 0L) {
                // dt dihaluskan: frame yang tersendat (membuka editor yang berat, GC) hanya menggeser waktu sedikit, jadi gerak latar
                // tidak "berhenti lalu melompat". Selang yang memang disengaja (frame dilewati) bukan hitch, jadi batasnya ikut [every].
                val raw = ((n - last) / 1e9f).coerceIn(.001f, .25f)
                val cap = every * pacer.vsSec + .018f
                sm += (raw.coerceAtMost(cap) - sm) * .35f
                // frame yang tersendat hanya memajukan waktu sedikit (maks ~cap + 6 ms): setelah hitch gerak lanjut mulus, bukan melompat
                val dt = if (raw > cap) (sm + (raw - cap) * .12f).coerceAtMost(cap + .006f) else raw
                // saat mengetik, waktu ornamen berjalan lebih lambat (gerak latar yang tenang = mata tidak "tertarik" dari teks)
                clock.time += dt * (1f - .70f * fx.calm)
            }
            last = n
            OrnPose.put(id, clock.now)
        }
    }
    // layer sendiri: invalidasi per frame ornamen tidak ikut merekam ulang node tetangga
    Canvas(modifier.graphicsLayer { }) {
        val t0 = System.nanoTime()
        OrnWarm.gpu(this)
        val ph = if (vm != null) ornViewH(vm, size) else size.height
        val tn = clock.now
        val s = clock.sw
        val ca = 1f - .36f * fx.calm
        if (clock.prev.isNotEmpty()) drawOrnament(clock.prev, P, size.width, ph, tn, fx, alpha = (1f - s).coerceIn(0f, 1f) * ca, enter = (1f - s).coerceIn(0f, 1f))
        drawOrnament(clock.cur, P, size.width, ph, tn, fx, alpha = s.coerceIn(0f, 1f) * ca, enter = s)
        pacer.sample((System.nanoTime() - t0) / 1e6f)
    }
}


// =====================================================================================================================
// Halaman Theme (dibuka dari menu titik tiga)
// =====================================================================================================================

private class ThemeSt(val hue: Float, val orn: String, val ti: Float)

/** Nilai [value] yang disampel paling cepat tiap [ms]: perubahan beruntun (seret slider) digabung, nilai terbaru selalu sampai. */
@Composable
private fun <T> rememberThrottled(value: T, ms: Long = 90L): T {
    var out by remember { mutableStateOf(value) }
    val cur by rememberUpdatedState(value)
    LaunchedEffect(Unit) {
        snapshotFlow { cur }.collect { v -> out = v; delay(ms) }
    }
    return out
}

/** Kemajuan masuk/keluar (0..1, boleh sedikit melewati 1 karena pegas) untuk isi sebuah AnimatedVisibility. Dipakai slider untuk morphing. */
@Composable
private fun AnimatedVisibilityScope.rv(): Float =
    transition.animateFloat(transitionSpec = { spring(.62f, 340f) }, label = "rv") { s ->
        if (s == EnterExitState.Visible) 1f else 0f
    }.value

private val ExEnter = expandVertically(spring(.78f, 360f)) + fadeIn(tween(220)) + scaleIn(spring(.8f, 400f), initialScale = .92f)
private val ExExit = shrinkVertically(spring(.9f, 520f)) + fadeOut(tween(140)) + scaleOut(tween(180), targetScale = .94f)

@Composable
fun ThemePage(vm: AppVM, nav: Nav, id: String) {
    val gp = LocalPal.current
    val d = vm.note(id)
    val autoH = if (gp.ex) gp.exH else hueSat(vm.pr.seed).first
    var hue by remember(id) { mutableStateOf(d?.th ?: -1f) }       // < 0 = Auto (warna Kustom mati)
    var orn by remember(id) { mutableStateOf(d?.orn ?: "") }
    var ti by remember(id) { mutableFloatStateOf(d?.ti ?: 1f) }
    // rona terakhir yang dipilih: saat toggle Kustom dinyalakan lagi, slider kembali ke sana (awal: rona warna aplikasi)
    var lastHue by remember(id) { mutableFloatStateOf(if ((d?.th ?: -1f) >= 0f) d!!.th else autoH) }
    val custom = hue >= 0f

    // simpan dengan jeda (slider menghasilkan banyak nilai), dan pasti tersimpan saat halaman ditutup
    LaunchedEffect(hue, orn, ti) { delay(120); vm.setNoteTheme(id, hue, orn, ti) }
    val latest by rememberUpdatedState(ThemeSt(hue, orn, ti))
    DisposableEffect(id) { onDispose { vm.setNoteTheme(id, latest.hue, latest.orn, latest.ti) } }

    val pv = remember(gp, hue, d?.cp, d?.cv) { if (d != null) notePalFor(gp, d.copy(th = hue)) else gp }
    // Kotak backdrop memakai nilai yang dibatasi ~11 kali per detik: menyeret slider tidak lagi merekam ulang empat adegan sekaligus di
    // tiap frame (pratinjau besar di atas tetap mengikuti jari tanpa jeda).
    val tilePal = rememberThrottled(pv)
    val tileInten = rememberThrottled(ti)

    PageScaffold("Note theme", d?.t?.ifEmpty { null } ?: "Untitled", { nav.pop() }) {
        SGroup("Preview", 0) {
            ThemePreview(d?.t.orEmpty(), pv, orn, id, ti)
        }
        SGroup("Backdrop", 60) {
            // geser kiri-kanan: tiap kotak berukuran tetap, jadi berapa pun jumlah backdrop tidak perlu dijejalkan
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ORN_LIST.forEach { info ->
                    OrnTile(info, tilePal, tileInten, orn == info.id, Modifier.width(92.dp)) { orn = info.id }
                }
            }
            // intensitas hanya relevan kalau ada backdrop
            AnimatedVisibility(orn.isNotEmpty(), enter = ExEnter, exit = ExExit) {
                val r = rv()
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SCard(POS_ONE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T("Intensity", 16f, 500, gp.os)
                                T("How bold the backdrop looks", 14f, 400, gp.osv)
                            }
                            T("${(ti * 100f).toInt()}%", 15f, 700, gp.p)
                        }
                        Spacer(Modifier.height(10.dp))
                        ExpSlider(
                            frac = (ti - ORN_INT_MIN) / (ORN_INT_MAX - ORN_INT_MIN), reveal = r, rainbow = false,
                            active = gp.p, inactive = gp.pc, handle = gp.p
                        ) { f ->
                            val raw = Math.round((ORN_INT_MIN + f * (ORN_INT_MAX - ORN_INT_MIN)) * 20f) / 20f
                            ti = if (abs(raw - 1f) < .06f) 1f else raw      // detent di 100% (bawaan)
                        }
                    }
                }
            }
        }
        SGroup("Color", 120) {
            SCard(if (custom) POS_TOP else POS_ONE, onClick = {
                if (custom) { lastHue = hue; hue = -1f } else hue = lastHue
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        T("Custom color", 16f, 500, gp.os)
                        T(
                            if (custom) "${hueName(hue)} paper" else "Auto: follows your app theme, or the cover photo",
                            14f, 400, gp.osv, lh = 1.3f
                        )
                    }
                    MorphSwitch(custom)
                }
            }
            AnimatedVisibility(custom, enter = ExEnter, exit = ExExit) {
                val r = rv()
                SCard(POS_BOT) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Hue", 16f, 500, gp.os, Modifier.weight(1f))
                        val h = if (hue >= 0f) hue else lastHue
                        T("${hueName(h)} · ${h.toInt()}°", 15f, 700, gp.p)
                    }
                    Spacer(Modifier.height(10.dp))
                    val h = if (hue >= 0f) hue else lastHue
                    ExpSlider(
                        frac = h / 360f, reveal = r, rainbow = true,
                        active = gp.p, inactive = gp.pc, handle = hsl(h, 75f, 50f)
                    ) { f -> val v = f * 360f; hue = v; lastHue = v }
                }
            }
        }
    }
}

/** Pratinjau hidup: tahan, seret, atau geser vertikal untuk merasakan responsnya persis seperti di editor. */
@Composable
private fun ThemePreview(title: String, pal: Pal, kind: String, id: String, inten: Float) {
    val fx = remember { OrnFx() }
    OrnDriver(fx, { false }, kind.isNotEmpty())
    Box(
        Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(32.dp)).background(pal.s)
            .ornTouch(fx)
    ) {
        OrnamentLayer(null, kind, pal, fx, "preview_$id", inten = inten)
        Column(Modifier.padding(24.dp)) {
            T(title.ifEmpty { "Untitled" }, 26f, 800, pal.os, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            T("Tap, hold, or drag across it. The backdrop always stays behind the words.", 15f, 400, pal.os, alpha = .8f, lh = 1.35f)
        }
    }
}

@Composable
private fun OrnTile(info: OrnInfo, pal: Pal, inten: Float, sel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val gp = LocalPal.current
    val P = remember(pal, inten) { ornPaint(pal, inten) }
    val r by animateDpAsState(if (sel) 30.dp else 16.dp, spring(.55f, 420f), label = "otr")
    val k by animateFloatAsState(if (sel) 1f else .94f, spring(.5f, 500f), label = "otk")
    val bw by animateDpAsState(if (sel) 3.dp else 1.dp, spring(.8f, 500f), label = "otb")
    val shape = RoundedCornerShape(r)
    Column(modifier.graphicsLayer { scaleX = k; scaleY = k }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(.78f).clip(shape).background(pal.s)
                .cardOrnament(info.id, P, "tile_${info.id}")
                .border(bw, if (sel) gp.p else gp.ol.copy(alpha = .5f), shape)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
                .padding(12.dp)
        ) {
            // garis teks palsu: menunjukkan bahwa ornamen ada di belakang teks
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(.55f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(pal.os.copy(alpha = .55f)))
                Box(Modifier.fillMaxWidth(.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(pal.os.copy(alpha = .3f)))
                Box(Modifier.fillMaxWidth(.7f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(pal.os.copy(alpha = .3f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        T(info.name, 13f, if (sel) 700 else 500, if (sel) gp.p else gp.os, maxLines = 1)
    }
}

private const val SLIDER_PAD = 22f

/**
 * Slider Material 3 Expressive (dipakai slider warna dan intensitas): trek tebal terbelah dua di sekitar pegangan, sudut luar bulat
 * penuh dan sudut dalam kecil; pegangan berupa kapsul tegak yang menyempit dan memanjang saat ditekan. [reveal] (0..1+) = morphing
 * masuk: pegangan muncul lebih dulu, lalu trek menyebar ke kiri dan kanan dari pegangan sambil menebal dari garis tipis.
 * [rainbow] = trek berupa spektrum hue (posisi pegangan = warna di bawahnya); selain itu kiri = [active], kanan = [inactive].
 */
@Composable
private fun ExpSlider(
    frac: Float, reveal: Float, rainbow: Boolean, active: Color, inactive: Color, handle: Color,
    onFrac: (Float) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val hw by animateDpAsState(if (pressed) 2.dp else 4.dp, spring(.55f, 700f), label = "xhw")
    val hh by animateDpAsState(if (pressed) 56.dp else 48.dp, spring(.5f, 600f), label = "xhh")
    val stops = remember { (0..12).map { hsl(it * 30f, 72f, 55f) } }
    val pl = remember { Path() }
    val pr = remember { Path() }
    val onF by rememberUpdatedState(onFrac)
    Canvas(
        Modifier.fillMaxWidth().height(56.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                val pad = SLIDER_PAD.dp.toPx()
                fun f(x: Float) = ((x - pad) / (size.width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                onF(f(down.position.x))
                horizontalDrag(down.id) { ch -> ch.consume(); onF(f(ch.position.x)) }
                pressed = false
            }
        }
    ) {
        val r = reveal.coerceAtLeast(0f)
        if (r < .003f) return@Canvas
        val w = size.width; val h = size.height
        val pad = SLIDER_PAD.dp.toPx()
        val cx = pad + (w - 2f * pad) * frac.coerceIn(0f, 1f)
        val rc = r.coerceAtMost(1f)
        val trackH = 40.dp.toPx()
        val ty = (h - trackH) / 2f
        val gap = 6.dp.toPx()
        val hwp = hw.toPx()
        val hhp = hh.toPx() * min(r, 1.12f)
        val outer = trackH / 2f
        val inner = 4.dp.toPx()
        val lx1 = cx - hwp / 2f - gap
        val rx0 = cx + hwp / 2f + gap
        val sy = lerp(.3f, 1f, ornSmooth(rc))
        val brush: Brush? = if (rainbow) Brush.horizontalGradient(stops, startX = pad, endX = w - pad) else null

        clipRect(cx - r * w, 0f, cx + r * w) {
            withTransform({ scale(1f, sy, Offset(cx, h / 2f)) }) {
                if (lx1 > 1f) {
                    pl.reset()
                    pl.addRoundRect(RoundRect(0f, ty, lx1, ty + trackH, CornerRadius(outer), CornerRadius(inner), CornerRadius(inner), CornerRadius(outer)))
                    if (brush != null) drawPath(pl, brush) else drawPath(pl, active)
                }
                if (w - rx0 > 1f) {
                    pr.reset()
                    pr.addRoundRect(RoundRect(rx0, ty, w, ty + trackH, CornerRadius(inner), CornerRadius(outer), CornerRadius(outer), CornerRadius(inner)))
                    if (brush != null) drawPath(pr, brush) else drawPath(pr, inactive)
                }
            }
            drawRoundRect(handle, Offset(cx - hwp / 2f, (h - hhp) / 2f), Size(hwp, hhp), CornerRadius(hwp / 2f))
        }
    }
}

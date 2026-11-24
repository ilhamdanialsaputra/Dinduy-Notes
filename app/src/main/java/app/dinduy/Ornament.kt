package app.dinduy

/*
 * Note theme: warna (slider hue) + backdrop (ornamen) per catatan.
 *
 *  - Backdrop: "Morph" (bentuk expressive: amuba, capsule, cookie, squircle, clover, pebble yang saling berubah bentuk),
 *    "Canopy" (daun dan ranting tertiup angin), "Skyline" (kota, awan, matahari). Plain = tanpa ornamen.
 *  - Selalu DI BELAKANG teks: digambar tepat setelah kertas dan sebelum sampul / isi catatan.
 *  - Ukuran semua elemen dihitung dari LEBAR wadah (u = w). Karena itu ornamen di kartu, di wadah morph, dan di editor
 *    adalah gambar yang sama persis, hanya skalanya berbeda -> buka/tutup kartu mulus tanpa lompatan.
 *  - Legibilitas: kontras ornamen dipagari (ornCap), ornamen meredup + melambat saat mengetik (OrnFx.calm), tepi bentuk berbulu,
 *    dan di atas sampul gradasi ornamen digambar ulang dengan topeng pudar (OrnamentCoverOverlay / cardOrnOverCover) supaya tidak terpotong.
 *  - Editor: parallax (tiap lapisan punya kedalaman), meregang dan memantul mengikuti kecepatan gulir, menghindari
 *    jari saat disentuh, berkumpul ke jari saat ditahan, dan "mekar" saat teks diangkat (ed.tl).
 *  - Kartu: pose diam (tanpa animasi), dengan pose terakhir dari editor supaya kartu tidak "loncat" setelah ditutup.
 */

import android.os.SystemClock
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

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
class OrnPaint(val a: Color, val b: Color, val c: Color, val d: Color, val ink: Color, val paper: Color, val amt: Float = 1f) {
    fun pick(i: Int): Color = when (((i % 4) + 4) % 4) { 0 -> a; 1 -> b; 2 -> c; else -> d }
    fun alpha(k: Float): OrnPaint = if (k >= 1f) this else OrnPaint(
        a.copy(alpha = a.alpha * k), b.copy(alpha = b.alpha * k), c.copy(alpha = c.alpha * k),
        d.copy(alpha = d.alpha * k), ink.copy(alpha = ink.alpha * k), paper, amt * k
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

fun ornPaint(p: Pal): OrnPaint {
    val dk = p.dark
    val s = p.s
    val cap = if (dk) .12f else .10f
    return OrnPaint(
        ornCap(lerpC(s, p.p, .20f), s, cap),
        ornCap(lerpC(s, p.tc, if (dk) .85f else .70f), s, cap),
        ornCap(lerpC(s, p.sc, if (dk) .85f else .75f), s, cap),
        ornCap(lerpC(s, p.ac, if (dk) .80f else .60f), s, cap * 1.15f),
        ornCap(lerpC(s, p.p, if (dk) .34f else .30f), s, cap * 1.35f),
        s
    )
}

// =====================================================================================================================
// Sinyal sentuhan / gulir
// =====================================================================================================================

@Stable
class OrnFx {
    var scroll by mutableFloatStateOf(0f)      // px gulir
    var vel by mutableFloatStateOf(0f)         // px/detik (dihaluskan)
    var stretch by mutableFloatStateOf(0f)     // -.85..85, pegas hampir kritis: melar saat gulir cepat, kembali tenang nyaris tanpa memantul
    var tx by mutableFloatStateOf(0f)          // posisi jari mentah
    var ty by mutableFloatStateOf(0f)
    var px by mutableFloatStateOf(0f)          // posisi jari yang dihaluskan: inilah yang dipakai gambar (tidak ada lompatan saat menyentuh)
    var py by mutableFloatStateOf(0f)
    var press by mutableFloatStateOf(0f)       // 0..1 jari menempel
    var hold by mutableFloatStateOf(0f)        // 0..1 jari ditahan diam
    var lift by mutableFloatStateOf(0f)        // 0..1 teks diangkat
    var calm by mutableFloatStateOf(0f)        // 0..1 sedang mengetik / membaca fokus: ornamen meredup dan melambat
    var down by mutableStateOf(false)
    var downAt = 0L
    var x0 = 0f
    var y0 = 0f
    var moved = 0f
}

/** Membaca sentuhan di fase Initial tanpa mengonsumsi apa pun, jadi gestur editor tidak terganggu. */
fun Modifier.ornTouch(fx: OrnFx): Modifier = pointerInput(fx) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.pressed }
            if (c == null) {
                if (fx.down) fx.down = false
            } else {
                if (!fx.down) { fx.downAt = c.uptimeMillis; fx.x0 = c.position.x; fx.y0 = c.position.y; fx.moved = 0f; fx.down = true }
                fx.tx = c.position.x; fx.ty = c.position.y
                fx.moved = max(fx.moved, hypot(c.position.x - fx.x0, c.position.y - fx.y0))
            }
        }
    }
}

/**
 * Penggerak sinyal. Hanya berputar saat ada yang bergerak (gulir / jari / pegas belum tenang), lalu tidur lagi.
 * [scroll] dibaca tiap frame; [lifted] true selama teks sedang diangkat; [calm] true selama pengguna mengetik / keyboard terbuka.
 */
@Composable
fun OrnDriver(fx: OrnFx, scroll: () -> Float, lifted: () -> Boolean, on: Boolean, calm: () -> Boolean = { false }) {
    if (!on) return
    LaunchedEffect(fx) {
        var last = scroll(); fx.scroll = last
        var lastT = 0L
        var sv = 0f
        snapshotFlow { Triple(scroll(), fx.down, fx.tx) }.collectLatest {
            var idle = 0
            while (idle < 24) {
                val n = withFrameNanos { it }
                val dt = if (lastT == 0L) .016f else ((n - lastT) / 1e9f).coerceIn(.004f, .05f)
                lastT = n
                val s = scroll(); fx.scroll = s
                val v = (s - last) / dt; last = s
                fx.vel += (v - fx.vel) * (1f - exp(-dt * 14f))
                // pegas hampir kritis (zeta ~ .78): melar mengikuti gulir lalu kembali halus; target dilunakkan tanh supaya tidak pernah "mentok"
                val tgt = tanh(fx.vel / 3600f) * .85f
                sv += (120f * (tgt - fx.stretch) - 17f * sv) * dt
                fx.stretch += sv * dt
                // jari: posisi dihaluskan, dan saat baru menyentuh langsung mulai dari titik sentuh (pengaruhnya sendiri naik lewat press)
                if (fx.down && fx.press < .02f) { fx.px = fx.tx; fx.py = fx.ty }
                val kf = 1f - exp(-dt * 12f)
                fx.px += (fx.tx - fx.px) * kf
                fx.py += (fx.ty - fx.py) * kf
                fx.press += ((if (fx.down) 1f else 0f) - fx.press) * (1f - exp(-dt * 9f))
                val heldMs = if (fx.down) SystemClock.uptimeMillis() - fx.downAt else 0L
                val still = fx.moved < 24f && abs(fx.vel) < 80f
                val ht = if (fx.down && still && heldMs > 280L) 1f else 0f
                fx.hold += (ht - fx.hold) * (1f - exp(-dt * 5f))
                val quiet = abs(fx.vel) < 6f && abs(fx.stretch) < .004f && abs(sv) < .02f && !fx.down && fx.press < .005f && fx.hold < .005f
                idle = if (quiet) idle + 1 else 0
            }
            fx.vel = 0f; fx.stretch = 0f; sv = 0f; fx.press = 0f; fx.hold = 0f; lastT = 0L
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
    private val m = HashMap<String, Float>()
    val cardH = HashMap<String, Float>()          // tinggi kartu terakhir yang digambar (dipakai ornamen di atas sampul kartu)
    var ver by mutableIntStateOf(0)
    fun get(id: String): Float = m[id] ?: ((id.hashCode() and 0xFFFF) / 700f)
    fun put(id: String, t: Float) { m[id] = t }
}

// =====================================================================================================================
// Helper matematika
// =====================================================================================================================

private fun ornMod(x: Float, m: Float): Float = ((x % m) + m) % m
private fun ornSoft(x: Float, m: Float): Float = m * tanh(x / m)
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

/** Cahaya lembut (gradien radial): memberi kedalaman tanpa tepi keras yang mengganggu bacaan. */
private fun DrawScope.glow(c: Color, cx: Float, cy: Float, r: Float, k: Float) {
    if (k <= .002f || c.alpha <= .002f || r <= 1f) return
    drawCircle(
        Brush.radialGradient(listOf(c.copy(alpha = c.alpha * k), Color.Transparent), center = Offset(cx, cy), radius = r),
        r, Offset(cx, cy)
    )
}

// =====================================================================================================================
// Bentuk Morph: fungsi radial r(theta) dinormalisasi, diinterpolasi antar bentuk lalu dihaluskan jadi kurva Bezier
// =====================================================================================================================

private const val ON = 64

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
        norm { 1f + .07f * cos(8f * it) + .05f * cos(16f * it) },             // 7 sunny
    )
}

private val SP = Path()
private val SP2 = Path()
private val PT = FloatArray(ON * 2)

/** Bentuk = campuran a -> b (m), lalu sedikit ke c (mc), ditambah riak halus (wob) supaya tepinya terasa hidup. */
private fun blobPath(
    p: Path, cx: Float, cy: Float, r0: Float, rot: Float, sx: Float, sy: Float,
    a: FloatArray, b: FloatArray, m: Float, c: FloatArray, mc: Float, wob: Float, wph: Float
) {
    val step = 2f * PI.toFloat() / ON
    for (i in 0 until ON) {
        val base = a[i] + (b[i] - a[i]) * m
        val r = r0 * (base + (c[i] - base) * mc) * (1f + wob * sin(3f * i * step + wph))
        val th = i * step + rot
        PT[2 * i] = cx + cos(th) * r * sx
        PT[2 * i + 1] = cy + sin(th) * r * sy
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

private fun DrawScope.drawMorphScene(P: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?) {
    val u = w
    val st = fx?.stretch ?: 0f; val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val tx = fx?.px ?: 0f; val ty = fx?.py ?: 0f

    // dua cahaya aurora yang melayang pelan di belakang semua bentuk
    glow(P.c, u * (.80f + .05f * sin(t * .13f)), u * (.22f + .04f * cos(t * .11f)) - ornSoft(sc * .05f, u * .2f), u * .95f, .55f)
    glow(P.b, u * (.14f + .05f * cos(t * .10f)), h * (.80f + .04f * sin(t * .09f)) - ornSoft(sc * .04f, u * .2f), u * .80f, .50f)

    for (i in BLOBS.indices) {
        val b = BLOBS[i]
        val r0 = b.s * u * .8f * (1f + .06f * lf + .03f * hd + .02f * sin(t * b.spd * .8f + i))
        // keliling lebih panjang dari layar: saat membungkus, bentuk sudah sepenuhnya di luar layar (tidak ada yang "muncul tiba-tiba")
        val per = h + 3f * r0
        val cy0 = ornMod(b.y * per - sc * b.depth, per) - 1.5f * r0
        val cx = b.x * u + sin(t * b.spd * .6f + i) * u * .03f
        val cy = cy0 + sin(t * b.spd * .45f + i * 1.7f) * u * .03f + st * u * .06f * b.depth
        if (cy + r0 * 1.7f < 0f || cy - r0 * 1.7f > h) continue

        // jari: bentuk menyingkir sedikit; ditahan: pelan-pelan tertarik; diangkat: merenggang keluar. Semua amplitudo kecil dan sudah dihaluskan.
        var ox = 0f; var oy = 0f; var infl = 0f
        if (pr > .01f) {
            val dx = cx - tx; val dy = cy - ty
            val d = hypot(dx, dy) + 1f
            infl = exp(-(d * d) / (u * u * .22f)) * pr
            val k = infl * u * .07f * (1f - hd * 1.4f)
            ox = dx / d * k; oy = dy / d * k
        }
        if (lf > .001f) { ox += (cx - u / 2f) / u * lf * u * .04f; oy += (cy - h / 2f) / u * lf * u * .04f }

        // perubahan bentuk: berjalan terus pada kecepatan tetap (tanpa dipengaruhi sentuhan, jadi tidak pernah melonjak);
        // sentuhan hanya menambahkan sedikit "bunga" lewat nilai yang sudah dihaluskan
        val ph = t * b.spd * .30f + i * 1.7f
        val seg = ornMod(ph, 3f)
        val k0 = seg.toInt().coerceIn(0, 2)
        val f = ornSmooth(seg - k0)
        val sa = when (k0) { 0 -> b.a; 1 -> b.b; else -> b.c }
        val sb = when (k0) { 0 -> b.b; 1 -> b.c; else -> b.a }
        val mc = (hd * .5f + infl * .2f + lf * .25f).coerceIn(0f, .6f)

        // melar: volume terjaga (lebar mengecil sebanyak tinggi bertambah)
        val k2 = .5f + b.depth * 2f
        val sy = 1f + abs(st) * .10f * k2
        val sx = 1f / sy
        val rot = b.rot + t * b.spd * .22f + sc * .0004f * (1f + b.depth * 4f) + st * .08f

        val col = P.pick(b.col)
        val fill = col.copy(alpha = col.alpha * (.72f + .28f * (b.depth / .38f)))
        blobPath(SP, cx + ox, cy + oy, r0, rot, sx, sy, SH.all[sa], SH.all[sb], f, SH.all[6], mc, .022f, t * b.spd * 1.1f + i)
        // tepi berbulu (dua lingkar lebar berintensitas rendah): bentuk "menyatu" dengan kertas, bukan tertempel
        drawPath(SP, fill.copy(alpha = fill.alpha * .10f), style = Stroke(u * .075f, join = StrokeJoin.Round))
        drawPath(SP, fill.copy(alpha = fill.alpha * .16f), style = Stroke(u * .035f, join = StrokeJoin.Round))
        drawPath(SP, fill)
        // inti yang lebih terang, sedikit bergeser ke arah cahaya: memberi volume sekaligus meringankan bagian tengah tempat teks biasanya lewat
        blobPath(SP, cx + ox - r0 * .10f, cy + oy - r0 * .12f, r0 * .60f, rot, sx, sy, SH.all[sa], SH.all[sb], f, SH.all[6], mc, .03f, t * b.spd * 1.1f + i + 1.3f)
        drawPath(SP, lerpC(fill, P.paper, .5f).copy(alpha = fill.alpha * .75f))
    }
}

// =====================================================================================================================
// Canopy: ranting yang melengkung, daun berkibar, berkas cahaya, daun jatuh, dan kunang-kunang
// =====================================================================================================================

private val NX = FloatArray(24)
private val NY = FloatArray(24)
private val NA = FloatArray(24)

private fun leafPath(p: Path, x: Float, y: Float, ang: Float, len: Float, wid: Float) {
    val ca = cos(ang); val sa = sin(ang)
    val mx = x + ca * len * .45f; val my = y + sa * len * .45f
    p.reset()
    p.moveTo(x, y)
    p.quadraticBezierTo(mx - sa * wid, my + ca * wid, x + ca * len, y + sa * len)
    p.quadraticBezierTo(mx + sa * wid, my - ca * wid, x, y)
    p.close()
}

private fun DrawScope.branch(
    P: OrnPaint, ox: Float, oy: Float, base: Float, len: Float, n: Int, curve: Float, sway: Float,
    t: Float, ph: Float, leaf: Float, flutter: Float, seed: Int, fade: Float = 1f, calm: Float = 0f
) {
    val u = size.width
    NX[0] = ox; NY[0] = oy
    val seg = len / n
    var x = ox; var y = oy
    for (k in 1..n) {
        val f = k.toFloat() / n
        // ujung bergoyang lebih jauh dari pangkal, ditambah gelombang kecil yang merambat sepanjang ranting
        val a = base + curve * k + sway * f * f + .045f * f * sin(t * .9f + ph + k * .55f)
        x += cos(a) * seg; y += sin(a) * seg
        NX[k] = x; NY[k] = y; NA[k] = a
    }
    SP2.reset(); SP2.moveTo(NX[0], NY[0])
    for (k in 1..n) SP2.lineTo(NX[k], NY[k])
    drawPath(SP2, P.ink.copy(alpha = P.ink.alpha * fade), style = Stroke(width = u * .009f * (.7f + .3f * fade), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val flK = 1f - .5f * calm
    for (k in 2..n) {
        val f = k.toFloat() / n
        val side = if (k % 2 == 0) 1f else -1f
        val ls = leaf * (1.05f - .6f * f)
        val fl = sin(t * 1.5f + ph + k * .9f) * .14f * (1f + flutter) * flK
        val la = NA[k] + side * (.95f - .25f * f) + fl
        val col = P.pick(k + seed).let { it.copy(alpha = it.alpha * fade) }
        leafPath(SP, NX[k], NY[k], la, ls, ls * .9f)
        drawPath(SP, col)
        // sisi daun yang terkena cahaya
        leafPath(SP, NX[k], NY[k], la - side * .10f, ls * .92f, ls * .36f)
        drawPath(SP, lerpC(col, P.paper, .40f).copy(alpha = col.alpha * .55f))
        if (ls > u * .05f) drawLine(
            P.paper.copy(alpha = .4f * fade * (1f - .6f * calm)), Offset(NX[k], NY[k]),
            Offset(NX[k] + cos(la) * ls * .8f, NY[k] + sin(la) * ls * .8f), strokeWidth = u * .0035f, cap = StrokeCap.Round
        )
    }
    leafPath(SP, NX[n], NY[n], NA[n] + sin(t * 1.6f + ph) * .10f * flK, leaf * .55f, leaf * .5f)
    drawPath(SP, P.pick(n + seed).let { it.copy(alpha = it.alpha * fade) })
}

private fun DrawScope.drawCanopyScene(P: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?) {
    val u = w
    val st = fx?.stretch ?: 0f; val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val tx = fx?.px ?: 0f
    val calm = fx?.calm ?: 0f
    val wind = st * .14f
    fun gust(ox: Float): Float = if (pr > .01f) ((ox - tx) / u).coerceIn(-1.2f, 1.2f) * pr * .18f else 0f
    val leafK = 1f + .05f * hd

    // berkas cahaya miring yang bernapas pelan, memudar ke bawah
    for (i in 0 until 3) {
        val x0 = u * (.52f + .22f * i) + sin(t * .12f + i * 2f) * u * .04f
        val wd = u * (.07f + .03f * i)
        val sk = u * .55f
        val br = .5f + .5f * sin(t * .21f + i * 1.9f)
        SP.reset(); SP.moveTo(x0, 0f); SP.lineTo(x0 + wd, 0f); SP.lineTo(x0 + wd - sk, h); SP.lineTo(x0 - sk, h); SP.close()
        drawPath(
            SP,
            Brush.verticalGradient(listOf(P.d.copy(alpha = P.d.alpha * (.12f + .12f * br) * (1f - .6f * calm)), Color.Transparent), startY = 0f, endY = h)
        )
    }

    // daun jatuh: di belakang ranting; jatuh pada kecepatan tetap (gulir hanya menggeser lewat parallax), berputar 3D (lebar daun berdenyut)
    for (i in 0 until 6) {
        val sz = u * (.035f + .02f * ornHash(i, 1)) * leafK
        val dep = .15f + .3f * ornHash(i, 2)
        val per = h + sz * 6f
        val y0 = ornHash(i, 3) * per + t * u * (.010f + .008f * ornHash(i, 4)) - sc * dep
        val y = ornMod(y0, per) - sz * 3f
        val x = ornHash(i, 5) * u + sin(t * .5f + i * 2f) * u * .06f + st * u * .03f + lf * u * .02f * (ornHash(i, 7) - .5f)
        val flip = abs(cos(t * (.45f + ornHash(i, 6) * .5f) + i))
        leafPath(SP, x, y, t * (.35f + ornHash(i, 6) * .4f) + i + st * .2f, sz * 2f, sz * .9f * (.28f + .72f * flip))
        drawPath(SP, P.pick(i + 1))
    }

    val sw = { ph: Float, amp: Float -> sin(t * .45f + ph) * amp + wind + lf * .12f }
    // lapis jauh: lebih kecil dan pudar, memberi kedalaman
    val f1 = u * .46f
    branch(P, f1, -u * .02f - ornSoft(sc * .03f, u * .10f), 1.95f, u * .34f, 6, .05f, sw(2.4f, .08f) + gust(f1), t, 2.4f, u * .07f * leafK, lf * 1.5f, 4, .55f, calm)
    val f2 = u * .64f
    branch(P, f2, h + u * .02f + ornSoft(sc * .03f, u * .06f), -1.35f, u * .30f, 5, -.05f, sw(5.2f, .09f) + gust(f2), t, 5.2f, u * .065f * leafK, lf * 1.5f, 5, .5f, calm)
    // kanan-atas (besar)
    val o1 = u * 1.03f
    branch(P, o1, -u * .03f - ornSoft(sc * .06f, u * .18f), 2.30f, u * .85f, 9, -.07f, sw(0f, .10f) + gust(o1), t, 0f, u * .17f * leafK, lf * 2f, 0, 1f, calm)
    // kiri-atas
    val o2 = -u * .03f
    branch(P, o2, -u * .02f - ornSoft(sc * .10f, u * .14f), .85f, u * .5f, 7, .07f, sw(1.7f, .12f) + gust(o2), t, 1.7f, u * .12f * leafK, lf * 2f, 1, 1f, calm)
    // kiri-bawah (tumbuh ke atas)
    val o3 = -u * .04f
    branch(P, o3, h + u * .03f + ornSoft(sc * .05f, u * .10f), -1.15f, u * .65f, 8, -.05f, sw(3.1f, .09f) + gust(o3), t, 3.1f, u * .14f * leafK, lf * 2f, 2, 1f, calm)
    // kanan-bawah
    val o4 = u * 1.04f
    branch(P, o4, h + u * .02f + ornSoft(sc * .08f, u * .08f), -2.15f, u * .42f, 6, .06f, sw(4.6f, .11f) + gust(o4), t, 4.6f, u * .10f * leafK, lf * 2f, 3, 1f, calm)

    // kunang-kunang: titik cahaya kecil yang naik pelan dan berkedip lembut
    for (i in 0 until 7) {
        val dep = .1f + .25f * ornHash(i, 7)
        val rr = u * (.006f + .006f * ornHash(i, 8))
        val per = h + u * .2f
        val y = ornMod(ornHash(i, 9) * per - t * u * (.008f + .006f * ornHash(i, 10)) - sc * dep, per) - u * .1f
        val x = ornHash(i, 12) * u + sin(t * .3f + i * 1.9f) * u * .05f
        val tw = .5f + .5f * sin(t * (.4f + ornHash(i, 13) * .6f) + i * 3f)
        val col = P.pick(i + 2)
        drawCircle(col.copy(alpha = col.alpha * (.10f + .10f * tw)), rr * 3.2f, Offset(x, y))
        drawCircle(col.copy(alpha = col.alpha * (.45f + .35f * tw)), rr, Offset(x, y))
    }
}

// =====================================================================================================================
// Skyline: matahari berhalo, awan, burung, tiga lapis gedung berkabut dengan parallax
// =====================================================================================================================

private val DEP = floatArrayOf(.10f, .20f, .34f)

private fun DrawScope.drawSkylineScene(P: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?) {
    val u = w
    val st = fx?.stretch ?: 0f; val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val tx = fx?.px ?: 0f
    val calm = fx?.calm ?: 0f

    // matahari + halo
    val sunR = u * .11f * (1f + .05f * hd + .08f * lf)
    val sx = u * .76f
    val sy = u * .19f - ornSoft(sc * .07f, u * .2f) + sin(t * .35f) * u * .006f
    glow(P.d, sx, sy, sunR * 4.4f, .45f * (1f + .15f * sin(t * .4f)))
    glow(P.b, sx, sy, sunR * 2.7f, .55f)
    val rot = t * .12f + sc * .0008f + lf * .6f + st * .15f
    for (k in 0 until 12) {
        val a = rot + k * (2f * PI.toFloat() / 12f)
        val lng = if (k % 2 == 0) 1f else .62f
        val r1 = sunR * 1.38f
        val r2 = sunR * (1.38f + (.26f + .07f * sin(t * .9f + k * 1.3f)) * lng)
        drawLine(P.b, Offset(sx + cos(a) * r1, sy + sin(a) * r1), Offset(sx + cos(a) * r2, sy + sin(a) * r2), strokeWidth = sunR * .17f, cap = StrokeCap.Round)
    }
    drawCircle(P.d, sunR, Offset(sx, sy))
    drawCircle(lerpC(P.d, P.paper, .45f).copy(alpha = P.d.alpha * .8f), sunR * .66f, Offset(sx - sunR * .06f, sy - sunR * .08f))

    // awan: gulir menggeser mereka ke samping; tiga gumpalan + dasar rata
    for (i in 0 until 3) {
        val cw = u * (.28f + .06f * i); val ch = cw * .24f
        val dep = .10f + .06f * i
        val span = u * 1.8f
        val x = ornMod(ornHash(i, 11) * span + t * u * (.005f + .0025f * i) - sc * dep * .25f + st * u * .03f * dep, span) - cw
        val y = u * (.10f + .17f * i) - ornSoft(sc * dep * .3f, u * .1f) + sin(t * .2f + i * 2f) * u * .004f
        drawRoundRect(P.b, Offset(x, y), Size(cw, ch), CornerRadius(ch / 2f))
        drawCircle(P.b, ch * .62f, Offset(x + cw * .30f, y + ch * .1f))
        drawCircle(P.b, ch * .85f, Offset(x + cw * .55f, y))
        drawCircle(P.b, ch * .50f, Offset(x + cw * .78f, y + ch * .18f))
    }

    // burung: kepakan lembut, melintas pelan
    for (i in 0 until 3) {
        val span = u * 1.6f
        val bx = ornMod(ornHash(i, 61) * span + t * u * (.020f + .008f * i) - sc * .02f, span) - u * .2f
        val by = u * (.16f + .09f * i) + sin(t * .5f + i * 2f) * u * .012f - ornSoft(sc * .05f, u * .1f)
        val fl = sin(t * (3.2f + i * .4f) + i * 2.3f)
        val bs = u * (.016f + .004f * i)
        SP2.reset()
        SP2.moveTo(bx - bs, by - bs * .5f * fl)
        SP2.quadraticBezierTo(bx - bs * .35f, by + bs * .05f, bx, by + bs * .25f)
        SP2.quadraticBezierTo(bx + bs * .35f, by + bs * .05f, bx + bs, by - bs * .5f * fl)
        drawPath(SP2, P.ink.copy(alpha = P.ink.alpha * (.75f - .5f * calm)), style = Stroke(u * .0045f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // gedung: jauh -> dekat; setiap lapis jauh diselimuti kabut tipis (perspektif udara)
    val tile = u * 1.5f
    for (L in 0..2) {
        val dep = DEP[L]
        val col = if (L == 0) P.b else if (L == 1) P.a else P.ink
        val yb = h + u * .015f + ornSoft(sc * dep * .25f, u * .07f * (L + 1)) - lf * u * .025f * (.6f + .3f * L)
        val shift = -sc * dep * .30f + st * u * .05f * dep
        var x = -u * .05f
        var i = 0
        while (x < tile) {
            val bw = u * (.075f + .075f * ornHash(i, 21 + L))
            val hb = when (L) { 0 -> .20f + .26f * ornHash(i, 31 + L); 1 -> .13f + .22f * ornHash(i, 31 + L); else -> .08f + .20f * ornHash(i, 31 + L) }
            val bh = min(u * hb * (1f + abs(st) * .04f), h * .62f)
            val roof = (ornHash(i, 41 + L) * 3f).toInt()
            for (rep in 0..1) {
                val bx = ornMod(x + shift, tile) - (if (rep == 1) tile else 0f)
                if (bx > u || bx + bw < 0f) continue
                // jari mengangkat gedung di dekatnya dan menyalakan jendelanya
                val cxm = bx + bw / 2f
                val near = if (pr > .01f) exp(-((cxm - tx) * (cxm - tx)) / (u * u * .03f)) * pr else 0f
                val bump = near * u * .028f
                val top = yb - bh - bump
                val rad = when (roof) { 1 -> bw * .5f; else -> min(bw * .18f, u * .015f) }
                drawRoundRect(col, Offset(bx, top), Size(bw, bh + bump + u), CornerRadius(rad))
                if (roof == 2) {
                    drawRect(col, Offset(bx + bw * .46f, top - bh * .22f), Size(bw * .08f, bh * .22f + 1f))
                    val bl = .5f + .5f * sin(t * 1.1f + i * 1.7f + L)
                    drawCircle(P.d.copy(alpha = P.d.alpha * (.25f + .55f * bl) * (1f - .5f * calm)), u * .006f, Offset(bx + bw * .5f, top - bh * .22f))
                }
                if (L >= 1 && bw > u * .06f) {
                    val cols = max(1, (bw / (u * .03f)).toInt())
                    val rows = min(7, (bh / (u * .04f)).toInt())
                    val cell = bw / cols
                    for (r in 0 until rows) for (c in 0 until cols) {
                        val hh = ornHash(i * 131 + r * 17 + c * 3, 51 + L)
                        if (!(hh > .6f || (near > .3f && hh > .25f))) continue
                        val tw = .5f + .5f * sin(t * (.18f + hh * .3f) + hh * 20f)
                        val al = (.30f + .35f * tw) * (if (L == 1) .6f else 1f) * (1f - .35f * calm) + near * .2f
                        drawRoundRect(
                            P.paper.copy(alpha = al.coerceIn(0f, 1f) * P.amt),
                            Offset(bx + c * cell + cell * .25f, top + u * .025f + r * u * .04f),
                            Size(cell * .5f, u * .02f), CornerRadius(u * .004f)
                        )
                    }
                }
            }
            x += bw + u * .008f
            i++
        }
        // kabut: makin ke depan makin tipis
        if (L < 2) {
            val y0 = h * .45f
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, P.paper.copy(alpha = (.30f - .12f * L) * P.amt)), startY = y0, endY = h + u * .02f),
                Offset(0f, y0), Size(w, h - y0 + u * .02f)
            )
        }
    }
}

// =====================================================================================================================
// Penggambar utama
// =====================================================================================================================

/**
 * [w] x [h] = ukuran wadah (kartu, atau layar editor menurut tinggi yang tampak). [t] detik, [sc] gulir px, [fx] null = pose diam.
 * [alpha] dan [enter] dipakai untuk pergantian backdrop: yang lama mengecil sedikit + pudar, yang baru membesar sedikit dengan pegas lembut.
 */
fun DrawScope.drawOrnament(kind: String, P0: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?, alpha: Float = 1f, enter: Float = 1f) {
    if (kind.isEmpty() || w <= 1f || h <= 1f || alpha <= .002f) return
    val P = P0.alpha(alpha)
    val s = .90f + .10f * enter.coerceIn(0f, 1.1f)
    withTransform({ scale(s, s, Offset(w / 2f, h / 2f)) }) {
        when (kind) {
            "morph" -> drawMorphScene(P, w, h, t, sc, fx)
            "canopy" -> drawCanopyScene(P, w, h, t, sc, fx)
            "skyline" -> drawSkylineScene(P, w, h, t, sc, fx)
        }
    }
}

/**
 * Ornamen yang MUNCUL DI ATAS sampul gradasi, dengan topeng alfa vertikal: nol di dalam foto, penuh tepat di tepi bawah sampul
 * (di situ foto sudah larut jadi warna kertas). Hasilnya ornamen "keluar" dari kabut sampul dan menyambung mulus dengan ornamen
 * di bawahnya, bukan terpotong garis sampul. Digambar dalam layer kecil (hanya area sampul) supaya topengnya tidak menyentuh pixel lain.
 * [y1] = tepi bawah sampul, [rampLen] = panjang zona transisi ke atas dari tepi itu.
 */
fun DrawScope.drawOrnOverCover(
    kind: String, P0: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?,
    y1: Float, rampLen: Float, alpha: Float = 1f, enter: Float = 1f
) {
    if (kind.isEmpty() || y1 <= 2f || alpha <= .002f) return
    val yb = min(y1, size.height)
    val y0 = (y1 - rampLen).coerceIn(0f, yb - 1f)
    if (yb - y0 < 2f) return
    drawIntoCanvas { c ->
        c.saveLayer(Rect(0f, y0, w, yb), Paint())
        drawOrnament(kind, P0, w, h, t, sc, fx, alpha, enter)
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Black.copy(alpha = .30f), 1f to Color.Black, startY = y0, endY = yb),
            topLeft = Offset(0f, y0), size = Size(w, yb - y0), blendMode = BlendMode.DstIn
        )
        c.restore()
    }
}

/** Ornamen kartu: digambar di belakang isi kartu (dan di belakang sampul), pose diam. Pasang tepat setelah .background(...). */
fun Modifier.cardOrnament(kind: String, P: OrnPaint, id: String): Modifier =
    if (kind.isEmpty()) this else drawBehind {
        OrnPose.ver
        OrnPose.cardH[id] = size.height
        drawOrnament(kind, P, size.width, size.height, OrnPose.get(id), 0f, null)
    }

/**
 * Pasangan [cardOrnament] untuk kartu bersampul GRADASI: pasang pada Modifier sampul (setelah ukuran), supaya ornamen digambar di atas
 * foto dengan topeng pudar ke bawah (lihat [drawOrnOverCover]). Adegannya sama persis dengan yang di belakang sampul.
 */
fun Modifier.cardOrnOverCover(kind: String, P: OrnPaint, id: String): Modifier =
    if (kind.isEmpty()) this else drawWithContent {
        drawContent()
        OrnPose.ver
        val h = OrnPose.cardH[id] ?: (size.height * 2.2f)
        drawOrnOverCover(kind, P, size.width, h, OrnPose.get(id), 0f, null, size.height, size.height * .55f)
    }

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
    clock: OrnClock = remember(id) { OrnClock(OrnPose.get(id)).also { it.cur = kind } }
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
    DisposableEffect(id) { onDispose { OrnPose.ver++ } }
    if (clock.cur.isEmpty() && clock.prev.isEmpty()) return
    val P = remember(pal) { ornPaint(pal) }
    LaunchedEffect(id, clock) {
        var last = 0L
        while (true) withFrameNanos { n ->
            // saat mengetik, waktu ornamen berjalan lebih lambat (gerak latar yang tenang = mata tidak "tertarik" dari teks)
            if (last != 0L) clock.time += ((n - last) / 1e9f).coerceAtMost(.05f) * (1f - .70f * fx.calm)
            last = n
            OrnPose.put(id, clock.now)
        }
    }
    Canvas(modifier) {
        val ph = if (vm != null) ornViewH(vm, size) else size.height
        val tn = clock.now
        val s = clock.sw
        val ca = 1f - .36f * fx.calm
        if (clock.prev.isNotEmpty()) drawOrnament(clock.prev, P, size.width, ph, tn, fx.scroll, fx, alpha = (1f - s).coerceIn(0f, 1f) * ca, enter = (1f - s).coerceIn(0f, 1f))
        drawOrnament(clock.cur, P, size.width, ph, tn, fx.scroll, fx, alpha = s.coerceIn(0f, 1f) * ca, enter = s)
    }
}

/**
 * Pasangan [OrnamentLayer] untuk catatan bersampul GRADASI di editor. Letakkan TEPAT SETELAH sampul (dan sebelum isi / teks).
 * [coverH] = tinggi banner sampul (px, dalam koordinat isi), [scroll] = gulir saat ini (px): banner ikut tergulir, jadi tepi bawahnya = coverH - scroll.
 */
@Composable
fun OrnamentCoverOverlay(
    vm: AppVM?, pal: Pal, fx: OrnFx, clock: OrnClock, coverH: Float, modifier: Modifier = Modifier.fillMaxSize(), scroll: () -> Float
) {
    if (clock.cur.isEmpty() && clock.prev.isEmpty()) return
    val P = remember(pal) { ornPaint(pal) }
    Canvas(modifier) {
        val y1 = coverH - scroll()
        if (y1 <= 2f) return@Canvas
        val ph = if (vm != null) ornViewH(vm, size) else size.height
        val tn = clock.now
        val s = clock.sw
        val ca = 1f - .36f * fx.calm
        val ramp = coverH * .55f
        if (clock.prev.isNotEmpty()) drawOrnOverCover(clock.prev, P, size.width, ph, tn, fx.scroll, fx, y1, ramp, (1f - s).coerceIn(0f, 1f) * ca, (1f - s).coerceIn(0f, 1f))
        drawOrnOverCover(clock.cur, P, size.width, ph, tn, fx.scroll, fx, y1, ramp, s.coerceIn(0f, 1f) * ca, s)
    }
}

// =====================================================================================================================
// Halaman Theme (dibuka dari menu titik tiga)
// =====================================================================================================================

@Composable
fun ThemePage(vm: AppVM, nav: Nav, id: String) {
    val gp = LocalPal.current
    val d = vm.note(id)
    val autoH = if (gp.ex) gp.exH else hueSat(vm.pr.seed).first
    var hue by remember(id) { mutableStateOf(d?.th ?: -1f) }
    var orn by remember(id) { mutableStateOf(d?.orn ?: "") }

    // simpan dengan jeda (slider menghasilkan banyak nilai), dan pasti tersimpan saat halaman ditutup
    LaunchedEffect(hue, orn) { delay(120); vm.setNoteTheme(id, hue, orn) }
    val latest by rememberUpdatedState(hue to orn)
    DisposableEffect(id) { onDispose { vm.setNoteTheme(id, latest.first, latest.second) } }

    val pv = remember(gp, hue, d?.cp, d?.cv) { if (d != null) notePalFor(gp, d.copy(th = hue)) else gp }

    PageScaffold("Note theme", d?.t?.ifEmpty { null } ?: "Untitled", { nav.pop() }) {
        SGroup("Preview", 0) {
            ThemePreview(d?.t.orEmpty(), pv, orn, id)
        }
        SGroup("Backdrop", 60) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ORN_LIST.forEach { info ->
                    OrnTile(info, pv, orn == info.id, Modifier.weight(1f)) {
                        orn = info.id
                        // pertama kali memilih backdrop bertema (daun / kota) saat warna masih Auto: mulai dari rona khasnya
                        if (hue < 0f && info.hue >= 0f) hue = info.hue
                    }
                }
            }
        }
        SGroup("Color", 120) {
            HueSlider(if (hue >= 0f) hue else autoH, hue >= 0f) { hue = it }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val on = hue < 0f
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) gp.p else gp.dockIn())
                        .clickable(remember { MutableInteractionSource() }, null) { hue = -1f }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) { T("Auto", 14f, 600, if (on) gp.op else gp.os) }
                Spacer(Modifier.width(12.dp))
                T(if (on) "Follows your app color" else hueName(hue), 14f, 500, gp.osv)
            }
        }
    }
}

/** Pratinjau hidup: tahan, seret, atau geser vertikal untuk merasakan responsnya persis seperti di editor. */
@Composable
private fun ThemePreview(title: String, pal: Pal, kind: String, id: String) {
    val fx = remember { OrnFx() }
    var sim by remember { mutableFloatStateOf(0f) }
    OrnDriver(fx, { sim }, { false }, kind.isNotEmpty())
    Box(
        Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(32.dp)).background(pal.s)
            .ornTouch(fx)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> sim -= dy } }
    ) {
        OrnamentLayer(null, kind, pal, fx, "preview_$id")
        Column(Modifier.padding(24.dp)) {
            T(title.ifEmpty { "Untitled" }, 26f, 800, pal.os, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            T("Hold, drag, or swipe up and down. The backdrop always stays behind the words.", 15f, 400, pal.os, alpha = .8f, lh = 1.35f)
        }
    }
}

@Composable
private fun OrnTile(info: OrnInfo, pal: Pal, sel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val gp = LocalPal.current
    val P = remember(pal) { ornPaint(pal) }
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

/** Slider hue gaya Expressive: trek pelangi, pegangan menyempit dan membulat saat ditekan. */
@Composable
private fun HueSlider(value: Float, active: Boolean, onChange: (Float) -> Unit) {
    val gp = LocalPal.current
    var pressed by remember { mutableStateOf(false) }
    val tw by animateDpAsState(if (pressed) 4.dp else 10.dp, spring(.5f, 600f), label = "htw")
    val th by animateDpAsState(if (pressed) 40.dp else 52.dp, spring(.5f, 600f), label = "hth")
    val dim by animateFloatAsState(if (active) 1f else .55f, spring(.8f, 300f), label = "hd")
    val stops = remember { (0..12).map { hsl(it * 30f, 70f, 55f) } }
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .graphicsLayer { alpha = .55f + .45f * dim }
            .background(Brush.horizontalGradient(stops))
            .pointerInput(Unit) {
                detectTapGestures(onPress = { o ->
                    pressed = true
                    onChange((o.x / size.width * 360f).coerceIn(0f, 360f))
                    tryAwaitRelease(); pressed = false
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { pressed = true }, onDragEnd = { pressed = false }, onDragCancel = { pressed = false }
                ) { c, _ -> onChange((c.position.x / size.width * 360f).coerceIn(0f, 360f)) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 28.dp.toPx()
            val cx = pad + (size.width - 2f * pad) * (value / 360f)
            val tH = th.toPx(); val tW = tw.toPx()
            drawRoundRect(gp.setCard(), Offset(cx - tW / 2f - 3.dp.toPx(), (size.height - tH) / 2f - 3.dp.toPx()), Size(tW + 6.dp.toPx(), tH + 6.dp.toPx()), CornerRadius((tW + 6.dp.toPx()) / 2f))
            drawRoundRect(Color.White, Offset(cx - tW / 2f, (size.height - tH) / 2f), Size(tW, tH), CornerRadius(tW / 2f))
        }
    }
}

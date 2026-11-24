package app.dinduy

/*
 * Note theme: warna (slider hue) + backdrop (ornamen) per catatan.
 *
 *  - Backdrop: "Morph" (bentuk expressive: amuba, capsule, cookie, squircle, clover, pebble yang saling berubah bentuk),
 *    "Canopy" (daun dan ranting tertiup angin), "Skyline" (kota, awan, matahari). Plain = tanpa ornamen.
 *  - Selalu DI BELAKANG teks: digambar tepat setelah kertas dan sebelum sampul / isi catatan.
 *  - Ukuran semua elemen dihitung dari LEBAR wadah (u = w). Karena itu ornamen di kartu, di wadah morph, dan di editor
 *    adalah gambar yang sama persis, hanya skalanya berbeda -> buka/tutup kartu mulus tanpa lompatan.
 *  - Editor: parallax (tiap lapisan punya kedalaman), meregang dan memantul mengikuti kecepatan gulir, menghindari
 *    jari saat disentuh, berkumpul ke jari saat ditahan, dan "mekar" saat teks diangkat (ed.tl).
 *  - Kartu: pose diam (tanpa animasi), dengan pose terakhir dari editor supaya kartu tidak "loncat" setelah ditutup.
 */

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
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
class OrnPaint(val a: Color, val b: Color, val c: Color, val d: Color, val ink: Color, val paper: Color) {
    fun pick(i: Int): Color = when (((i % 4) + 4) % 4) { 0 -> a; 1 -> b; 2 -> c; else -> d }
    fun alpha(k: Float): OrnPaint = if (k >= 1f) this else OrnPaint(
        a.copy(alpha = a.alpha * k), b.copy(alpha = b.alpha * k), c.copy(alpha = c.alpha * k),
        d.copy(alpha = d.alpha * k), ink.copy(alpha = ink.alpha * k), paper
    )
}

fun ornPaint(p: Pal): OrnPaint {
    val dk = p.dark
    return OrnPaint(
        lerpC(p.s, p.p, if (dk) .20f else .20f),
        lerpC(p.s, p.tc, if (dk) .85f else .70f),
        lerpC(p.s, p.sc, if (dk) .85f else .75f),
        lerpC(p.s, p.ac, if (dk) .80f else .60f),
        lerpC(p.s, p.p, if (dk) .34f else .30f),
        p.s
    )
}

// =====================================================================================================================
// Sinyal sentuhan / gulir
// =====================================================================================================================

@Stable
class OrnFx {
    var scroll by mutableFloatStateOf(0f)      // px gulir
    var vel by mutableFloatStateOf(0f)         // px/detik (dihaluskan)
    var stretch by mutableFloatStateOf(0f)     // -1.3..1.3, pegas kurang teredam: melar saat gulir cepat, memantul saat berhenti
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
    var press by mutableFloatStateOf(0f)       // 0..1 jari menempel
    var hold by mutableFloatStateOf(0f)        // 0..1 jari ditahan diam
    var lift by mutableFloatStateOf(0f)        // 0..1 teks diangkat (pegas memantul)
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
 * [scroll] dibaca tiap frame; [lifted] true selama teks sedang diangkat.
 */
@Composable
fun OrnDriver(fx: OrnFx, scroll: () -> Float, lifted: () -> Boolean, on: Boolean) {
    if (!on) return
    LaunchedEffect(fx) {
        var last = scroll(); fx.scroll = last
        var lastT = 0L
        var sv = 0f
        snapshotFlow { Triple(scroll(), fx.down, fx.tx) }.collectLatest {
            var calm = 0
            while (calm < 24) {
                val n = withFrameNanos { it }
                val dt = if (lastT == 0L) .016f else ((n - lastT) / 1e9f).coerceIn(.004f, .05f)
                lastT = n
                val s = scroll(); fx.scroll = s
                val v = (s - last) / dt; last = s
                fx.vel += (v - fx.vel) * (1f - exp(-dt * 16f))
                // pegas kurang teredam (zeta ~ .57): melar ke arah gulir, lalu memantul sekali saat berhenti
                val tgt = (fx.vel / 3200f).coerceIn(-1.3f, 1.3f)
                sv += (170f * (tgt - fx.stretch) - 15f * sv) * dt
                fx.stretch += sv * dt
                fx.press += ((if (fx.down) 1f else 0f) - fx.press) * (1f - exp(-dt * 11f))
                val heldMs = if (fx.down) SystemClock.uptimeMillis() - fx.downAt else 0L
                val still = fx.moved < 24f && abs(fx.vel) < 80f
                val ht = if (fx.down && still && heldMs > 280L) 1f else 0f
                fx.hold += (ht - fx.hold) * (1f - exp(-dt * 6f))
                val quiet = abs(fx.vel) < 6f && abs(fx.stretch) < .004f && abs(sv) < .02f && !fx.down && fx.press < .005f && fx.hold < .005f
                calm = if (quiet) calm + 1 else 0
            }
            fx.vel = 0f; fx.stretch = 0f; sv = 0f; fx.press = 0f; fx.hold = 0f; lastT = 0L
        }
    }
    LaunchedEffect(fx) {
        val a = Animatable(0f)
        snapshotFlow { lifted() }.collect { l -> a.animateTo(if (l) 1f else 0f, spring(.42f, 260f)) { fx.lift = value } }
    }
}

// =====================================================================================================================
// Pose terakhir per catatan (kartu memakai pose terakhir editor, jadi tidak ada lompatan setelah menutup)
// =====================================================================================================================

object OrnPose {
    private val m = HashMap<String, Float>()
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

// =====================================================================================================================
// Bentuk Morph: fungsi radial r(theta) dinormalisasi, diinterpolasi antar bentuk lalu dihaluskan jadi kurva Bezier
// =====================================================================================================================

private const val ON = 56

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
        norm(sq(1f, .52f, 5f)),                                               // 2 capsule
        norm(sq(1f, 1f, 3.2f)),                                               // 3 squircle
        norm { 1f + .26f * cos(4f * it) },                                    // 4 clover
        norm { 1f + .20f * sin(2f * it + 1f) + .07f * sin(7f * it) },          // 5 pebble
    )
}

private val SP = Path()
private val SP2 = Path()
private val PT = FloatArray(ON * 2)

private fun blobPath(p: Path, cx: Float, cy: Float, r0: Float, rot: Float, sx: Float, sy: Float, a: FloatArray, b: FloatArray, m: Float) {
    val step = 2f * PI.toFloat() / ON
    for (i in 0 until ON) {
        val r = r0 * (a[i] + (b[i] - a[i]) * m)
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

private class Blob(val x: Float, val y: Float, val s: Float, val a: Int, val b: Int, val col: Int, val depth: Float, val spd: Float, val rot: Float)

private val BLOBS = listOf(
    Blob(.88f, .10f, .46f, 0, 3, 0, .10f, .22f, .15f),
    Blob(.10f, .38f, .30f, 2, 1, 1, .22f, .30f, -.2f),
    Blob(.80f, .62f, .36f, 4, 0, 2, .16f, .18f, .3f),
    Blob(.18f, .88f, .42f, 3, 5, 3, .28f, .26f, -.1f),
    Blob(.52f, .28f, .16f, 1, 4, 0, .38f, .40f, .5f),
    Blob(.95f, .95f, .22f, 5, 2, 1, .34f, .34f, .2f),
)

private fun DrawScope.drawMorphScene(P: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?) {
    val u = w
    val st = fx?.stretch ?: 0f; val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val tx = fx?.tx ?: 0f; val ty = fx?.ty ?: 0f
    for (i in BLOBS.indices) {
        val b = BLOBS[i]
        val r0 = b.s * u * .8f * (1f + .14f * lf + .05f * hd)
        val per = h + 2f * r0
        val cy0 = ornMod(b.y * per - sc * b.depth, per) - r0
        val cx = b.x * u + sin(t * b.spd * .7f + i) * u * .03f
        val cy = cy0 + sin(t * b.spd * .5f + i * 1.7f) * u * .03f + st * u * .15f * b.depth
        if (cy + r0 * 1.5f < 0f || cy - r0 * 1.5f > h) continue
        // jari: bentuk menghindar; ditahan: bentuk berkumpul ke jari (faktor jadi negatif); diangkat: menyebar keluar
        var ox = 0f; var oy = 0f; var infl = 0f
        if (pr > .01f) {
            val dx = cx - tx; val dy = cy - ty
            val d = hypot(dx, dy) + 1f
            infl = exp(-(d * d) / (u * u * .20f)) * pr
            val k = infl * u * .14f * (1f - hd * 1.6f)
            ox = dx / d * k; oy = dy / d * k
        }
        if (lf > .001f) { ox += (cx - u / 2f) / u * lf * u * .08f; oy += (cy - h / 2f) / u * lf * u * .08f }
        var m = .5f + .5f * sin(t * b.spd + i * 1.3f)
        m = m * m * (3f - 2f * m)
        m = (m + lf * .6f + hd * .25f + infl * .3f).coerceIn(0f, 1f)
        val k2 = .5f + b.depth * 2f
        val sy = 1f + abs(st) * .30f * k2
        val sx = 1f - abs(st) * .14f * k2
        val rot = b.rot + t * b.spd * .35f + sc * .0006f * (1f + b.depth * 4f) + st * .25f
        blobPath(SP, cx + ox, cy + oy, r0, rot, sx, sy, SH.all[b.a], SH.all[b.b], m)
        drawPath(SP, P.pick(b.col))
    }
}

// =====================================================================================================================
// Canopy: ranting yang melengkung, daun berkibar, daun jatuh
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
    t: Float, ph: Float, leaf: Float, flutter: Float, seed: Int
) {
    val u = size.width
    NX[0] = ox; NY[0] = oy
    val seg = len / n
    var x = ox; var y = oy
    for (k in 1..n) {
        val f = k.toFloat() / n
        val a = base + curve * k + sway * f * f      // ujung bergoyang lebih jauh dari pangkal
        x += cos(a) * seg; y += sin(a) * seg
        NX[k] = x; NY[k] = y; NA[k] = a
    }
    SP2.reset(); SP2.moveTo(NX[0], NY[0])
    for (k in 1..n) SP2.lineTo(NX[k], NY[k])
    drawPath(SP2, P.ink, style = Stroke(width = u * .009f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    for (k in 2..n) {
        val f = k.toFloat() / n
        val side = if (k % 2 == 0) 1f else -1f
        val ls = leaf * (1.05f - .6f * f)
        val fl = sin(t * 1.7f + ph + k * .9f) * .16f * (1f + flutter)
        val la = NA[k] + side * (.95f - .25f * f) + fl
        leafPath(SP, NX[k], NY[k], la, ls, ls * .9f)
        drawPath(SP, P.pick(k + seed))
        if (ls > u * .05f) drawLine(
            P.paper.copy(alpha = .4f), Offset(NX[k], NY[k]),
            Offset(NX[k] + cos(la) * ls * .8f, NY[k] + sin(la) * ls * .8f), strokeWidth = u * .0035f, cap = StrokeCap.Round
        )
    }
    leafPath(SP, NX[n], NY[n], NA[n] + sin(t * 1.9f + ph) * .12f, leaf * .55f, leaf * .5f)
    drawPath(SP, P.pick(n + seed))
}

private fun DrawScope.drawCanopyScene(P: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?) {
    val u = w
    val st = fx?.stretch ?: 0f; val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val tx = fx?.tx ?: 0f
    val wind = st * .20f
    fun gust(ox: Float): Float = if (pr > .01f) ((ox - tx) / u).coerceIn(-1.2f, 1.2f) * pr * .25f else 0f
    val leafK = 1f + .08f * hd

    // daun jatuh: di belakang ranting; kecepatan jatuh ditambah gulir (parallax), arah miring ditambah angin
    for (i in 0 until 6) {
        val sz = u * (.035f + .02f * ornHash(i, 1)) * leafK
        val dep = .15f + .3f * ornHash(i, 2)
        val per = h + sz * 6f
        val y0 = ornHash(i, 3) * per + t * u * (.012f + .01f * ornHash(i, 4)) * (1f + lf * 2f) - sc * dep
        val y = ornMod(y0, per) - sz * 3f
        val x = ornHash(i, 5) * u + sin(t * .5f + i * 2f) * u * .06f + st * u * .04f
        leafPath(SP, x, y, t * (.8f + ornHash(i, 6)) + i + st * .3f, sz * 2f, sz * .9f)
        drawPath(SP, P.pick(i + 1))
    }

    val sw = { ph: Float, amp: Float -> sin(t * .6f + ph) * amp + wind + lf * .2f }
    // kanan-atas (besar)
    val o1 = u * 1.03f
    branch(P, o1, -u * .03f - ornSoft(sc * .06f, u * .18f), 2.30f, u * .85f, 9, -.07f, sw(0f, .10f) + gust(o1), t, 0f, u * .17f * leafK, lf * 2.5f, 0)
    // kiri-atas
    val o2 = -u * .03f
    branch(P, o2, -u * .02f - ornSoft(sc * .10f, u * .14f), .85f, u * .5f, 7, .07f, sw(1.7f, .12f) + gust(o2), t, 1.7f, u * .12f * leafK, lf * 2.5f, 1)
    // kiri-bawah (tumbuh ke atas)
    val o3 = -u * .04f
    branch(P, o3, h + u * .03f + ornSoft(sc * .05f, u * .10f), -1.15f, u * .65f, 8, -.05f, sw(3.1f, .09f) + gust(o3), t, 3.1f, u * .14f * leafK, lf * 2.5f, 2)
    // kanan-bawah
    val o4 = u * 1.04f
    branch(P, o4, h + u * .02f + ornSoft(sc * .08f, u * .08f), -2.15f, u * .42f, 6, .06f, sw(4.6f, .11f) + gust(o4), t, 4.6f, u * .10f * leafK, lf * 2.5f, 3)
}

// =====================================================================================================================
// Skyline: matahari (rotasi + naik saat gulir), awan geser horizontal, tiga lapis gedung dengan parallax
// =====================================================================================================================

private val DEP = floatArrayOf(.10f, .20f, .34f)

private fun DrawScope.drawSkylineScene(P: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?) {
    val u = w
    val st = fx?.stretch ?: 0f; val pr = fx?.press ?: 0f; val hd = fx?.hold ?: 0f; val lf = fx?.lift ?: 0f
    val tx = fx?.tx ?: 0f

    // matahari
    val sunR = u * .11f * (1f + .08f * hd + .12f * lf)
    val sx = u * .76f
    val sy = u * .19f - ornSoft(sc * .07f, u * .2f) + sin(t * .45f) * u * .006f
    val rot = t * .22f + sc * .0011f + lf * 1.2f + st * .3f
    for (k in 0 until 12) {
        val a = rot + k * (2f * PI.toFloat() / 12f)
        val r1 = sunR * 1.38f
        val r2 = sunR * (1.62f + .12f * sin(t * 1.3f + k))
        drawLine(P.b, Offset(sx + cos(a) * r1, sy + sin(a) * r1), Offset(sx + cos(a) * r2, sy + sin(a) * r2), strokeWidth = sunR * .17f, cap = StrokeCap.Round)
    }
    drawCircle(P.d, sunR, Offset(sx, sy))

    // awan: gulir menggeser mereka ke samping
    for (i in 0 until 3) {
        val cw = u * (.26f + .06f * i); val ch = cw * .26f
        val dep = .10f + .06f * i
        val span = u * 1.8f
        val x = ornMod(ornHash(i, 11) * span + t * u * (.006f + .003f * i) - sc * dep * .25f, span) - cw
        val y = u * (.10f + .17f * i) - ornSoft(sc * dep * .3f, u * .1f)
        drawRoundRect(P.b, Offset(x, y), Size(cw, ch), CornerRadius(ch / 2f))
        drawCircle(P.b, ch * .62f, Offset(x + cw * .34f, y + ch * .1f))
        drawCircle(P.b, ch * .80f, Offset(x + cw * .60f, y))
    }

    // gedung: jauh -> dekat
    val tile = u * 1.5f
    for (L in 0..2) {
        val dep = DEP[L]
        val col = if (L == 0) P.b else if (L == 1) P.a else P.ink
        val yb = h + u * .015f + ornSoft(sc * dep * .25f, u * .07f * (L + 1)) - lf * u * .035f * (.6f + .3f * L)
        val shift = -sc * dep * .30f + st * u * .09f * dep
        var x = -u * .05f
        var i = 0
        while (x < tile) {
            val bw = u * (.075f + .075f * ornHash(i, 21 + L))
            val hb = when (L) { 0 -> .20f + .26f * ornHash(i, 31 + L); 1 -> .13f + .22f * ornHash(i, 31 + L); else -> .08f + .20f * ornHash(i, 31 + L) }
            val bh = min(u * hb * (1f + abs(st) * .05f), h * .62f)
            val roof = (ornHash(i, 41 + L) * 3f).toInt()
            for (rep in 0..1) {
                val bx = ornMod(x + shift, tile) - (if (rep == 1) tile else 0f)
                if (bx > u || bx + bw < 0f) continue
                // jari mengangkat gedung di dekatnya dan menyalakan jendelanya
                val cxm = bx + bw / 2f
                val near = if (pr > .01f) exp(-((cxm - tx) * (cxm - tx)) / (u * u * .03f)) * pr else 0f
                val bump = near * u * .04f
                val top = yb - bh - bump
                val rad = when (roof) { 1 -> bw * .5f; else -> min(bw * .18f, u * .015f) }
                drawRoundRect(col, Offset(bx, top), Size(bw, bh + bump + u), CornerRadius(rad))
                if (roof == 2) drawRect(col, Offset(bx + bw * .46f, top - bh * .22f), Size(bw * .08f, bh * .22f + 1f))
                if (L >= 1 && bw > u * .06f) {
                    val cols = max(1, (bw / (u * .03f)).toInt())
                    val rows = min(7, (bh / (u * .04f)).toInt())
                    val cell = bw / cols
                    for (r in 0 until rows) for (c in 0 until cols) {
                        val hh = ornHash(i * 131 + r * 17 + c * 3, 51 + L)
                        if (!(hh > .6f || (near > .3f && hh > .25f))) continue
                        val tw = .5f + .5f * sin(t * (.35f + hh) + hh * 20f)
                        val al = (.30f + .45f * tw) * (if (L == 1) .6f else 1f) + near * .2f
                        drawRoundRect(
                            P.paper.copy(alpha = al.coerceIn(0f, 1f)),
                            Offset(bx + c * cell + cell * .25f, top + u * .025f + r * u * .04f),
                            Size(cell * .5f, u * .02f), CornerRadius(u * .004f)
                        )
                    }
                }
            }
            x += bw + u * .008f
            i++
        }
    }
}

// =====================================================================================================================
// Penggambar utama
// =====================================================================================================================

/**
 * [w] x [h] = ukuran wadah (kartu, atau layar editor menurut tinggi yang tampak). [t] detik, [sc] gulir px, [fx] null = pose diam.
 * [alpha] dan [enter] dipakai untuk pergantian backdrop: yang lama mengecil + pudar, yang baru membesar dengan pegas memantul.
 */
fun DrawScope.drawOrnament(kind: String, P0: OrnPaint, w: Float, h: Float, t: Float, sc: Float, fx: OrnFx?, alpha: Float = 1f, enter: Float = 1f) {
    if (kind.isEmpty() || w <= 1f || h <= 1f || alpha <= .002f) return
    val P = P0.alpha(alpha)
    val s = .84f + .16f * enter
    withTransform({ scale(s, s, Offset(w / 2f, h / 2f)) }) {
        when (kind) {
            "morph" -> drawMorphScene(P, w, h, t, sc, fx)
            "canopy" -> drawCanopyScene(P, w, h, t, sc, fx)
            "skyline" -> drawSkylineScene(P, w, h, t, sc, fx)
        }
    }
}

/** Ornamen kartu: digambar di belakang isi kartu (dan di belakang sampul), pose diam. Pasang tepat setelah .background(...). */
fun Modifier.cardOrnament(kind: String, P: OrnPaint, id: String): Modifier =
    if (kind.isEmpty()) this else drawBehind {
        OrnPose.ver
        drawOrnament(kind, P, size.width, size.height, OrnPose.get(id), 0f, null)
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
 * Mengganti [kind] = backdrop lama mengecil + pudar, yang baru membesar dengan pegas.
 */
@Composable
fun OrnamentLayer(vm: AppVM?, kind: String, pal: Pal, fx: OrnFx, id: String, modifier: Modifier = Modifier.fillMaxSize()) {
    var cur by remember { mutableStateOf(kind) }
    var prev by remember { mutableStateOf("") }
    val sw = remember { Animatable(1f) }
    LaunchedEffect(kind) {
        if (kind != cur) {
            prev = cur; cur = kind
            sw.snapTo(0f)
            sw.animateTo(1f, spring(.62f, 110f))
            prev = ""
        }
    }
    DisposableEffect(id) { onDispose { OrnPose.ver++ } }
    if (cur.isEmpty() && prev.isEmpty()) return
    val P = remember(pal) { ornPaint(pal) }
    val base = remember(id) { OrnPose.get(id) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(id) {
        var last = 0L
        while (true) withFrameNanos { n ->
            if (last != 0L) time += ((n - last) / 1e9f).coerceAtMost(.05f)
            last = n
            OrnPose.put(id, base + time)
        }
    }
    Canvas(modifier) {
        val ph = if (vm != null) ornViewH(vm, size) else size.height
        val tn = base + time
        val s = sw.value
        if (prev.isNotEmpty()) drawOrnament(prev, P, size.width, ph, tn, fx.scroll, fx, alpha = (1f - s).coerceIn(0f, 1f), enter = (1f - s).coerceIn(0f, 1f))
        drawOrnament(cur, P, size.width, ph, tn, fx.scroll, fx, alpha = s.coerceIn(0f, 1f), enter = s)
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

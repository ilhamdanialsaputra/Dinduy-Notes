@file:OptIn(ExperimentalComposeUiApi::class)

package app.dinduy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.toImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================================
//  CORAT-CORET (fase 1): pulpen, stabilo, penghapus; data vektor menempel pada blok;
//  undo/redo lewat riwayat Ed yang sudah ada; jari dan stylus; palm rejection.
//  Belum ada: lasso, rapihkan bentuk, stabilo pintar, zoom cubit (lihat catatan di chat).
// =====================================================================================

/** Stylus pernah terdeteksi selama aplikasi berjalan (dipakai mode Otomatis). */
object InkEnv {
    var stylus by mutableStateOf(false)
}

/** Goresan yang sedang digambar. Titik disimpan tiga-tiga (x, y, k) dalam piksel ruang konten. */
class LiveInk(val tool: Int, val color: Int, val wDp: Float, val stylus: Boolean) {
    var pts = FloatArray(192)
    var n = 0
    var sx = 0f; var sy = 0f; var sk = 1f        // titik tersaring
    var lx = 0f; var ly = 0f; var lt = 0L        // sampel mentah terakhir
    var viewY = 0f; var lastX = 0f               // posisi jari di layar (untuk gulir otomatis)
    var com = false                              // sudah pasti coretan (bukan awal cubit / gulir dua jari)

    fun add(x: Float, y: Float, k: Float) {
        if (n * 3 + 3 > pts.size) pts = pts.copyOf(pts.size * 2)
        pts[n * 3] = x; pts[n * 3 + 1] = y; pts[n * 3 + 2] = k
        n++
    }
}

class CS(val top: Float, val w: Float, val d: Float, val path: Path)

class InkCtl {
    var on by mutableStateOf(false)
    var tool by mutableStateOf(0)                // 0 pulpen, 1 stabilo, 2 penghapus
    var pop by mutableStateOf(false)             // panel warna dan ukuran
    var hide by mutableStateOf(false)            // sembunyikan semua coretan (tanpa menghapus)
    var live by mutableStateOf<LiveInk?>(null)
    var tick by mutableStateOf(0)
    var erase by mutableStateOf<Set<String>>(emptySet())   // goresan yang akan terhapus (pratinjau)
    var eraser by mutableStateOf<Offset?>(null)
    var lay by mutableStateOf(0)                 // naik kalau posisi blok berubah
    var stamp by mutableStateOf(0L)              // terakhir menyentuh (untuk kembali otomatis)
    var sel by mutableStateOf<Set<String>>(emptySet())      // goresan terpilih (lasso)
    var xf by mutableStateOf<FloatArray?>(null)             // pratinjau pindah / ubah ukuran: dx, dy, skala, pivotX, pivotY
    var zoom by mutableStateOf(1f)
    var panX by mutableStateOf(0f)
    var panY by mutableStateOf(0f)
    var exporting by mutableStateOf(false)
    var layer: GraphicsLayer? = null
    var busy = false                             // sedang ada gestur coret
    var noRescue = false
    val cache = HashMap<String, CS>()

    fun toggle(ed: Ed) {
        if (on) { on = false; pop = false; sel = emptySet(); zoom = 1f; panX = 0f; panY = 0f }
        else {
            on = true; hide = false; pop = false
            ed.selImg = null; ed.miniBar = false
            ed.clearReq++                         // tutup keyboard dan fokus teks
        }
        stamp = System.currentTimeMillis()
    }
}

// ------------------------------------------------------------------ warna (ikut tema)
fun inkColor(pal: Pal, i: Int, hl: Boolean): Color {
    val dk = pal.dark
    if (!hl) return when (i) {
        0 -> pal.os
        1 -> pal.p
        2 -> hsl(4f, 78f, if (dk) 66f else 46f)
        3 -> hsl(145f, 60f, if (dk) 58f else 34f)
        else -> hsl(38f, 90f, if (dk) 62f else 46f)
    }
    return when (i) {
        0 -> hsl(52f, 100f, if (dk) 30f else 66f)
        1 -> pal.pc
        2 -> hsl(4f, 90f, if (dk) 32f else 76f)
        3 -> hsl(145f, 70f, if (dk) 28f else 70f)
        else -> hsl(38f, 95f, if (dk) 32f else 70f)
    }
}

// ------------------------------------------------------------------ posisi blok dan penyelamatan
/** Catat posisi blok; naikkan penanda hanya kalau benar-benar berubah. */
fun Ed.setPos(id: String, y: Float, h: Float) {
    val o = bpos[id]
    if (o == null || o[0] != y || o[1] != h) {
        bpos[id] = floatArrayOf(y, h)
        ink.lay++
    }
}

/**
 * Goresan yang tadinya ada di riwayat tapi sekarang tidak ada di blok mana pun (blok dihapus / digabung)
 * dipindah ke blok yang masih hidup: blok yang sama kalau id-nya masih ada, kalau tidak yang terdekat.
 */
internal fun inkRescue(prev: List<Blk>, cur: List<Blk>, pos: Map<String, FloatArray>, dens: Float): List<Blk> {
    if (prev.none { it.ik.isNotEmpty() }) return cur
    val have = HashSet<String>()
    cur.forEach { b -> b.ik.forEach { have.add(it.id) } }
    var res: MutableList<Blk>? = null
    for ((pi, pb) in prev.withIndex()) {
        for (s in pb.ik) {
            if (s.id in have) continue
            val r = res ?: cur.toMutableList().also { res = it }
            if (r.isEmpty()) continue
            var ti = r.indexOfFirst { it.id == pb.id }
            var dy = 0f
            if (ti < 0) {
                var k = 1
                while (ti < 0 && (pi - k >= 0 || pi + k < prev.size)) {
                    val a = prev.getOrNull(pi - k)?.id
                    val b = prev.getOrNull(pi + k)?.id
                    ti = r.indexOfFirst { it.id == a || it.id == b }.let { if (a == null && b == null) -1 else it }
                    k++
                }
                if (ti < 0) ti = r.lastIndex
                val oy = pos[pb.id]?.get(0)
                val ty = pos[r[ti].id]?.get(0)
                if (oy != null && ty != null && dens > 0f) dy = (oy - ty) / dens
            }
            val np = if (dy == 0f) s.p else s.p.mapIndexed { i, v -> if (i % 3 == 1) v + dy else v }
            r[ti] = r[ti].copy(ik = r[ti].ik + s.copy(p = np))
            have.add(s.id)
        }
    }
    return res ?: cur
}

/** Terapkan perubahan blok sebagai SATU langkah riwayat (tanpa menyentuh fokus teks). */
fun Ed.inkApply(nb: List<Blk>) {
    commit()
    blocks = nb
    dirty()
    val f = focus
    focus = null
    ink.noRescue = true
    commit()
    ink.noRescue = false
    focus = f
}

fun Ed.inkErase(ids: Set<String>) {
    if (ids.isEmpty()) return
    inkApply(blocks.map { b -> if (b.ik.any { it.id in ids }) b.copy(ik = b.ik.filter { it.id !in ids }) else b })
}

/** Blok terdekat dari sebuah titik y (piksel konten) beserta posisi atasnya. */
fun Ed.inkTarget(cy: Float): Pair<Blk, Float>? {
    var best: Blk? = null; var bt = 0f; var bg = Float.MAX_VALUE
    for (b in blocks) {
        val p = bpos[b.id] ?: continue
        val gap = if (cy < p[0]) p[0] - cy else if (cy > p[0] + p[1]) cy - (p[0] + p[1]) else 0f
        if (gap < bg) { bg = gap; best = b; bt = p[0] }
    }
    val b = best ?: blocks.firstOrNull() ?: return null
    return b to bt
}

private fun absPts(s: Ink, top: Float, W: Float, d: Float): FloatArray {
    val n = s.p.size / 3
    val a = FloatArray(n * 3)
    for (i in 0 until n) { a[i * 3] = s.p[i * 3] * W; a[i * 3 + 1] = top + s.p[i * 3 + 1] * d; a[i * 3 + 2] = s.p[i * 3 + 2] }
    return a
}

private fun relPts(a: FloatArray, n: Int, top: Float, W: Float, d: Float): List<Float> {
    fun r(v: Float, f: Float) = (v * f).roundToInt() / f
    val out = ArrayList<Float>(n * 3)
    for (i in 0 until n) { out.add(r(a[i * 3] / W, 10000f)); out.add(r((a[i * 3 + 1] - top) / d, 10f)); out.add(r(a[i * 3 + 2], 100f)) }
    return out
}

fun Ed.inkAdd(lv: LiveInk, W: Float, dens: Float) = inkAddRaw(lv.tool, lv.color, lv.wDp, lv.pts, lv.n, false, W, dens)

/** Simpan goresan baru: sederhanakan, cari blok terdekat, ubah ke koordinat relatif blok. */
fun Ed.inkAddRaw(tool: Int, color: Int, wDp: Float, pts: FloatArray, n: Int, sharp: Boolean, W: Float, dens: Float) {
    if (n == 0 || W <= 0f || blocks.isEmpty()) return
    val s = if (sharp) pts.copyOf(n * 3) else rdp(pts, n, (if (tool == 1) 1.0f else 0.45f) * dens)
    val m = s.size / 3
    var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
    for (i in 0 until m) { y0 = min(y0, s[i * 3 + 1]); y1 = max(y1, s[i * 3 + 1]) }
    val (tgt, bt) = inkTarget((y0 + y1) / 2f) ?: return
    val st = Ink(t = tool, c = color, w = wDp, p = relPts(s, m, bt, W, dens), sh = sharp)
    inkApply(blocks.map { if (it.id == tgt.id) it.copy(ik = it.ik + st) else it })
}

/** Kotak pembatas goresan terpilih (piksel konten, sudah termasuk tebal garis). */
fun Ed.inkBox(ids: Set<String>, W: Float): Rect? {
    var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
    var any = false
    for (b in blocks) {
        if (b.ik.isEmpty()) continue
        val top = bpos[b.id]?.get(0) ?: continue
        for (s in b.ik) {
            if (s.id !in ids) continue
            val pad = s.w * dens / 2f
            for (i in 0 until s.p.size / 3) {
                val x = s.p[i * 3] * W; val y = top + s.p[i * 3 + 1] * dens
                x0 = min(x0, x - pad); x1 = max(x1, x + pad); y0 = min(y0, y - pad); y1 = max(y1, y + pad); any = true
            }
        }
    }
    return if (any) Rect(x0, y0, x1, y1) else null
}

/** Pilih goresan yang minimal separuh titiknya berada di dalam lasso. */
fun Ed.inkInside(poly: FloatArray, pn: Int, W: Float): Set<String> {
    fun inside(x: Float, y: Float): Boolean {
        var c = false; var j = pn - 1
        for (i in 0 until pn) {
            val xi = poly[i * 3]; val yi = poly[i * 3 + 1]; val xj = poly[j * 3]; val yj = poly[j * 3 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }
    val out = HashSet<String>()
    for (b in blocks) {
        val top = bpos[b.id]?.get(0) ?: continue
        for (s in b.ik) {
            val n = s.p.size / 3
            if (n == 0) continue
            var k = 0
            for (i in 0 until n) if (inside(s.p[i * 3] * W, top + s.p[i * 3 + 1] * dens)) k++
            if (k * 2 >= n) out.add(s.id)
        }
    }
    return out
}

/** Pindah / ubah ukuran goresan terpilih sebagai satu langkah; tiap goresan menempel lagi ke blok terdekat. */
fun Ed.inkTransform(ids: Set<String>, dx: Float, dy: Float, sc: Float, px: Float, py: Float, W: Float) {
    val d = dens
    val adds = HashMap<String, ArrayList<Ink>>()
    val base = blocks.map { b -> if (b.ik.any { it.id in ids }) b.copy(ik = b.ik.filter { it.id !in ids }) else b }
    for (b in blocks) {
        val top = bpos[b.id]?.get(0) ?: continue
        for (s in b.ik) {
            if (s.id !in ids) continue
            val a = absPts(s, top, W, d)
            val n = a.size / 3
            var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            for (i in 0 until n) {
                a[i * 3] = px + (a[i * 3] - px) * sc + dx
                a[i * 3 + 1] = py + (a[i * 3 + 1] - py) * sc + dy
                y0 = min(y0, a[i * 3 + 1]); y1 = max(y1, a[i * 3 + 1])
            }
            val (tgt, tt) = inkTarget((y0 + y1) / 2f) ?: continue
            adds.getOrPut(tgt.id) { ArrayList() }.add(s.copy(w = (s.w * sc).coerceIn(.5f, 60f), p = relPts(a, n, tt, W, d)))
        }
    }
    inkApply(base.map { b -> adds[b.id]?.let { b.copy(ik = b.ik + it) } ?: b })
}

/** Rapikan goresan pulpen terpilih (garis, lingkaran / oval, persegi, segitiga). Yang tidak yakin dibiarkan. */
fun Ed.inkSnapSel(ids: Set<String>, W: Float) {
    var changed = false
    val d = dens
    val nb = blocks.map { b ->
        if (b.ik.none { it.id in ids && it.t == 0 }) return@map b
        val top = bpos[b.id]?.get(0) ?: return@map b
        b.copy(ik = b.ik.map { s ->
            if (s.id !in ids || s.t != 0 || s.sh) s else {
                val a = absPts(s, top, W, d)
                val r = recognize(a, a.size / 3, d)
                if (r == null) s else { changed = true; s.copy(p = relPts(r, r.size / 3, top, W, d), sh = true) }
            }
        })
    }
    if (changed) inkApply(nb)
}

/** Stabilo pintar: kalau goresan melintang di atas satu baris teks, ratakan ke baris itu. Mengembalikan (titik, tebal dp). */
fun Ed.inkSmartHl(a: FloatArray, n: Int): Pair<FloatArray, Float>? {
    if (n < 2) return null
    var x0 = a[0]; var x1 = a[0]; var y0 = a[1]; var y1 = a[1]
    for (i in 1 until n) { x0 = min(x0, a[i * 3]); x1 = max(x1, a[i * 3]); y0 = min(y0, a[i * 3 + 1]); y1 = max(y1, a[i * 3 + 1]) }
    val w = x1 - x0; val h = y1 - y0; val cy = (y0 + y1) / 2f
    val live = blocks.map { it.id }.toHashSet()
    var bd = Float.MAX_VALUE; var bc = 0f; var bh = 0f
    for ((key, lay) in lays) {
        val pos = kpos[key] ?: continue
        if (keyBlock(key) !in live) continue
        for (i in 0 until lay.lineCount) {
            val top = pos.y + lay.getLineTop(i); val bot = pos.y + lay.getLineBottom(i); val lh = bot - top
            if (lh <= 0f || h > lh * 1.1f || w < lh * 1.5f) continue
            if (cy < top - lh * .25f || cy > bot + lh * .25f) continue
            val dd = abs(cy - (top + bot) / 2f)
            if (dd < bd) { bd = dd; bc = (top + bot) / 2f; bh = lh }
        }
    }
    if (bh <= 0f) return null
    return tri(x0, bc, x1, bc) to (bh * .92f / dens).coerceIn(6f, 40f)
}

/** Goresan yang disentuh lingkaran penghapus (pusat x,y; radius r; semuanya piksel konten). */
fun Ed.inkHit(x: Float, y: Float, r: Float, W: Float): List<String> {
    val out = ArrayList<String>()
    val d = dens
    for (b in blocks) {
        if (b.ik.isEmpty()) continue
        val top = bpos[b.id]?.get(0) ?: continue
        for (s in b.ik) {
            val n = s.p.size / 3
            if (n == 0) continue
            val rr = r + s.w * d * .5f
            var hit = false
            var px = s.p[0] * W; var py = top + s.p[1] * d
            if (hypot(px - x, py - y) <= rr) hit = true
            var i = 1
            while (!hit && i < n) {
                val cx = s.p[i * 3] * W; val cy = top + s.p[i * 3 + 1] * d
                if (segDist(x, y, px, py, cx, cy) <= rr) hit = true
                px = cx; py = cy; i++
            }
            if (hit) out.add(s.id)
        }
    }
    return out
}

private fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val l2 = dx * dx + dy * dy
    val t = if (l2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
    return hypot(x - (ax + t * dx), y - (ay + t * dy))
}

// ------------------------------------------------------------------ penghalusan dan bentuk
/** Ramer-Douglas-Peucker (iteratif) pada titik tiga-tiga; titik ujung selalu dipertahankan. */
private fun rdp(a: FloatArray, n: Int, eps: Float): FloatArray {
    if (n <= 2) return a.copyOf(n * 3)
    val keep = BooleanArray(n)
    keep[0] = true; keep[n - 1] = true
    val st = ArrayList<IntArray>()
    st.add(intArrayOf(0, n - 1))
    while (st.isNotEmpty()) {
        val (s, e) = st.removeAt(st.size - 1)
        var md = 0f; var mi = -1
        for (i in s + 1 until e) {
            val d = segDist(a[i * 3], a[i * 3 + 1], a[s * 3], a[s * 3 + 1], a[e * 3], a[e * 3 + 1])
            if (d > md) { md = d; mi = i }
        }
        if (mi >= 0 && md > eps) { keep[mi] = true; st.add(intArrayOf(s, mi)); st.add(intArrayOf(mi, e)) }
    }
    val c = keep.count { it }
    val o = FloatArray(c * 3); var j = 0
    for (i in 0 until n) if (keep[i]) { o[j++] = a[i * 3]; o[j++] = a[i * 3 + 1]; o[j++] = a[i * 3 + 2] }
    return o
}

/** Chaikin: potong sudut, ujung tetap. Dua putaran cukup untuk kurva mulus tanpa melampaui titik. */
private fun chaikin(p: FloatArray): FloatArray {
    val n = p.size / 3
    if (n < 3) return p
    val o = FloatArray((2 * n - 2) * 3)
    var j = 0
    o[j++] = p[0]; o[j++] = p[1]; o[j++] = p[2]
    for (i in 0 until n - 1) {
        val a = i * 3; val b = a + 3
        if (i != 0) for (q in 0..2) o[j++] = .75f * p[a + q] + .25f * p[b + q]
        if (i != n - 2) for (q in 0..2) o[j++] = .25f * p[a + q] + .75f * p[b + q]
    }
    o[j++] = p[n * 3 - 3]; o[j++] = p[n * 3 - 2]; o[j++] = p[n * 3 - 1]
    return o
}

/** Path goresan. Pulpen: pita berisi dengan tebal berubah (tutup ujung bulat). Stabilo: garis tebal, ujung rata. */
fun inkPath(a: FloatArray, n: Int, tool: Int, wPx: Float, sharp: Boolean = false): Path {
    val path = Path()
    if (n == 0) return path
    var c = if (a.size == n * 3) a else a.copyOf(n * 3)
    if (!sharp) c = chaikin(chaikin(c))
    val m = c.size / 3
    if (tool == 1) {
        path.moveTo(c[0], c[1])
        if (m == 1) path.lineTo(c[0] + .01f, c[1])
        for (i in 1 until m) path.lineTo(c[i * 3], c[i * 3 + 1])
        return path
    }
    if (m == 1) {
        val r = max(wPx * c[2] / 2f, .6f)
        path.addOval(androidx.compose.ui.geometry.Rect(c[0] - r, c[1] - r, c[0] + r, c[1] + r))
        return path
    }
    val lx = FloatArray(m); val ly = FloatArray(m); val rx = FloatArray(m); val ry = FloatArray(m)
    val tx = FloatArray(m); val ty = FloatArray(m); val rad = FloatArray(m)
    for (i in 0 until m) {
        val p = max(i - 1, 0); val q = min(i + 1, m - 1)
        var dx = c[q * 3] - c[p * 3]; var dy = c[q * 3 + 1] - c[p * 3 + 1]
        val l = hypot(dx, dy)
        if (l < 1e-4f) { dx = 1f; dy = 0f } else { dx /= l; dy /= l }
        tx[i] = dx; ty[i] = dy
        rad[i] = max(wPx * c[i * 3 + 2] / 2f, .35f)
        val nx = -dy; val ny = dx
        lx[i] = c[i * 3] + nx * rad[i]; ly[i] = c[i * 3 + 1] + ny * rad[i]
        rx[i] = c[i * 3] - nx * rad[i]; ry[i] = c[i * 3 + 1] - ny * rad[i]
    }
    path.moveTo(lx[0], ly[0])
    for (i in 1 until m) path.lineTo(lx[i], ly[i])
    val e = m - 1
    for (k in 1..5) {                                    // tutup ujung akhir (kiri -> depan -> kanan)
        val th = PI.toFloat() * k / 6f
        val nx = -ty[e]; val ny = tx[e]
        path.lineTo(c[e * 3] + rad[e] * (nx * cos(th) + tx[e] * sin(th)), c[e * 3 + 1] + rad[e] * (ny * cos(th) + ty[e] * sin(th)))
    }
    for (i in e downTo 0) path.lineTo(rx[i], ry[i])
    for (k in 1..5) {                                    // tutup ujung awal (kanan -> belakang -> kiri)
        val th = PI.toFloat() * k / 6f
        val nx = -ty[0]; val ny = tx[0]
        path.lineTo(c[0] + rad[0] * (-nx * cos(th) - tx[0] * sin(th)), c[1] + rad[0] * (-ny * cos(th) - ty[0] * sin(th)))
    }
    path.close()
    return path
}

private fun storedPath(s: Ink, top: Float, W: Float, d: Float): Path {
    val n = s.p.size / 3
    val a = FloatArray(n * 3)
    for (i in 0 until n) { a[i * 3] = s.p[i * 3] * W; a[i * 3 + 1] = top + s.p[i * 3 + 1] * d; a[i * 3 + 2] = s.p[i * 3 + 2] }
    return inkPath(a, n, s.t, s.w * d, s.sh)
}

private fun tri(vararg v: Float): FloatArray {
    val k = v.size / 2
    val o = FloatArray(k * 3)
    for (i in 0 until k) { o[i * 3] = v[i * 2]; o[i * 3 + 1] = v[i * 2 + 1]; o[i * 3 + 2] = 1f }
    return o
}

private fun turnDeg(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
    val a1 = atan2(by - ay, bx - ax).toDouble(); val a2 = atan2(cy - by, cx - bx).toDouble()
    var d = abs(a2 - a1)
    if (d > PI) d = 2 * PI - d
    return (d * 180.0 / PI).toFloat()
}

/** Kenali goresan tunggal sebagai garis / elips / persegi / segitiga. null = tidak yakin (biarkan apa adanya). */
internal fun recognize(a: FloatArray, n: Int, dens: Float): FloatArray? {
    if (n < 4) return null
    var len = 0f; var x0 = a[0]; var x1 = a[0]; var y0 = a[1]; var y1 = a[1]
    for (i in 1 until n) {
        len += hypot(a[i * 3] - a[i * 3 - 3], a[i * 3 + 1] - a[i * 3 - 2])
        x0 = min(x0, a[i * 3]); x1 = max(x1, a[i * 3]); y0 = min(y0, a[i * 3 + 1]); y1 = max(y1, a[i * 3 + 1])
    }
    val w = x1 - x0; val h = y1 - y0; val diag = hypot(w, h)
    if (len < 40f * dens || diag < 24f * dens) return null
    val fx = a[0]; val fy = a[1]; val ex = a[n * 3 - 3]; val ey = a[n * 3 - 2]
    val gap = hypot(ex - fx, ey - fy)
    if (gap > .22f * len) {                                    // terbuka: hanya garis lurus
        if (gap < .85f * len) return null
        var md = 0f
        for (i in 0 until n) md = max(md, segDist(a[i * 3], a[i * 3 + 1], fx, fy, ex, ey))
        if (md > .06f * gap) return null
        var ax = fx; var ay = fy; var bx = ex; var by = ey
        val ang = atan2(ey - fy, ex - fx)
        if (abs(sin(ang)) < .14f) { val ym = (fy + ey) / 2f; ay = ym; by = ym }          // kunci horizontal
        else if (abs(cos(ang)) < .14f) { val xm = (fx + ex) / 2f; ax = xm; bx = xm }     // kunci vertikal
        return tri(ax, ay, bx, by)
    }
    val cx = (x0 + x1) / 2f; val cy = (y0 + y1) / 2f; val ra = w / 2f; val rb = h / 2f
    if (ra < 8f * dens || rb < 8f * dens) return null
    var err = 0f
    for (i in 0 until n) { val u = (a[i * 3] - cx) / ra; val v = (a[i * 3 + 1] - cy) / rb; err += abs(u * u + v * v - 1f) }
    err /= n
    if (err < .17f) {                                          // lingkaran atau oval
        val circ = abs(ra - rb) < .12f * max(ra, rb)
        val r0 = (ra + rb) / 2f
        val m = 48
        val o = FloatArray((m + 1) * 3)
        for (i in 0..m) {
            val t = 2f * PI.toFloat() * i / m
            o[i * 3] = cx + (if (circ) r0 else ra) * cos(t); o[i * 3 + 1] = cy + (if (circ) r0 else rb) * sin(t); o[i * 3 + 2] = 1f
        }
        return o
    }
    val pc = FloatArray((n + 1) * 3)
    a.copyInto(pc, 0, 0, n * 3); pc[n * 3] = fx; pc[n * 3 + 1] = fy; pc[n * 3 + 2] = 1f
    val v = rdp(pc, n + 1, .07f * diag)
    val vc = v.size / 3
    if (vc < 4) return null
    val cs = ArrayList<Float>()
    val pv = vc - 2
    if (turnDeg(v[pv * 3], v[pv * 3 + 1], v[0], v[1], v[3], v[4]) > 35f) { cs.add(v[0]); cs.add(v[1]) }   // titik awal ternyata sudut
    for (i in 1..vc - 2) { cs.add(v[i * 3]); cs.add(v[i * 3 + 1]) }
    val k = cs.size / 2
    if (k != 3 && k != 4) return null
    for (i in 0 until k) {
        val p = (i + k - 1) % k; val q = (i + 1) % k
        val t = turnDeg(cs[p * 2], cs[p * 2 + 1], cs[i * 2], cs[i * 2 + 1], cs[q * 2], cs[q * 2 + 1])
        if (k == 4 && (t < 60f || t > 120f)) return null
        if (k == 3 && t > 160f) return null
    }
    if (k == 4) {
        var axis = true
        for (i in 0 until 4) {
            val q = (i + 1) % 4
            val ddx = abs(cs[q * 2] - cs[i * 2]); val ddy = abs(cs[q * 2 + 1] - cs[i * 2 + 1])
            if (min(ddx, ddy) > .18f * max(ddx, ddy)) axis = false
        }
        if (axis) return tri(x0, y0, x1, y0, x1, y1, x0, y1, x0, y0)
        return tri(cs[0], cs[1], cs[2], cs[3], cs[4], cs[5], cs[6], cs[7], cs[0], cs[1])
    }
    return tri(cs[0], cs[1], cs[2], cs[3], cs[4], cs[5], cs[0], cs[1])
}

private fun pressureK(p: Float) = if (p <= .01f) .7f else (.35f + .95f * p).coerceIn(.3f, 1.3f)
private fun speedK(v: Float) = 1.15f - .6f * (v / 2.5f).coerceIn(0f, 1f)   // v dalam dp per ms: makin cepat makin tipis

/** Tambah satu sampel: abaikan getar kecil, saring (jari lebih kuat dari stylus), hitung tebal. */
private fun addSample(lv: LiveInk, x: Float, y: Float, t: Long, pressure: Float, dens: Float) {
    if (lv.n == 0) {
        val k0 = if (lv.tool == 1) 1f else if (lv.stylus) pressureK(pressure) else .9f
        lv.sx = x; lv.sy = y; lv.sk = k0; lv.lx = x; lv.ly = y; lv.lt = t
        lv.add(x, y, k0)
        return
    }
    val dist = hypot(x - lv.lx, y - lv.ly)
    if (dist < .7f * dens) return
    val dt = max(1L, t - lv.lt).toFloat()
    val a = if (lv.stylus) .55f else .30f
    lv.sx += (x - lv.sx) * a; lv.sy += (y - lv.sy) * a
    val target = if (lv.tool == 1) 1f else if (lv.stylus) pressureK(pressure) else speedK(dist / dt / dens)
    lv.sk += (target - lv.sk) * (if (lv.stylus) .4f else .18f)
    lv.lx = x; lv.ly = y; lv.lt = t
    lv.add(lv.sx, lv.sy, lv.sk)
}

// ------------------------------------------------------------------ zoom cubit
fun Modifier.inkZoom(ed: Ed): Modifier = graphicsLayer {
    val k = ed.ink
    scaleX = k.zoom; scaleY = k.zoom
    translationX = k.panX; translationY = k.panY
    transformOrigin = TransformOrigin(0f, 0f)
}

/** Cubit dua jari untuk zoom (dan geser) saat mode coret aktif. Dipasang di kotak layar, jadi koordinatnya koordinat layar. */
@Composable
fun Modifier.inkPinch(ed: Ed, scroll: ScrollState): Modifier {
    val ik = ed.ink
    return pointerInput(Unit) {
        awaitPointerEventScope {
            var active = false; var pinching = false
            var pd = 0f; var d0 = 0f; var pcx = 0f; var pcy = 0f
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val fs = ev.changes.filter { it.pressed && it.type == PointerType.Touch }
                val lv = ik.live
                if (!ik.on || fs.size < 2 || (lv != null && lv.com)) { active = false; pinching = false; continue }
                val a = fs[0].position; val b = fs[1].position
                val d = max(hypot(a.x - b.x, a.y - b.y), 1f)
                val cx = (a.x + b.x) / 2f; val cy = (a.y + b.y) / 2f
                if (!active) { active = true; pd = d; d0 = d; pcx = cx; pcy = cy; pinching = ik.zoom > 1f; continue }
                if (!pinching && abs(d / d0 - 1f) > .06f) pinching = true
                if (pinching) {
                    val z0 = ik.zoom
                    val z1 = (z0 * d / pd).coerceIn(1f, 3f)
                    val ux = (pcx - ik.panX) / z0; val uy = (pcy - ik.panY) / z0
                    val W = size.width.toFloat(); val H = size.height.toFloat()
                    val nx = (cx - ux * z1).coerceIn(W * (1f - z1), 0f)
                    val wy = cy - uy * z1
                    val ny = wy.coerceIn(H * (1f - z1), 0f)
                    ik.zoom = z1; ik.panX = nx; ik.panY = ny
                    if (wy != ny) scroll.dispatchRawDelta(-(wy - ny) / z1)     // sisa geseran vertikal jadi gulir biasa
                    for (c in fs) c.consume()
                }
                pd = d; pcx = cx; pcy = cy
            }
        }
    }
}

// ------------------------------------------------------------------ input (jari, stylus, palm rejection, lasso)
private class Hd(val del: Offset, val snap: Offset, val mv: Offset, val rs: Offset)

private fun inkHandles(b: Rect, d: Float): Hd {
    val ty = max(b.top - 30f * d, 24f * d)
    return Hd(
        Offset(b.left + 22f * d, ty), Offset(b.left + 72f * d, ty),
        Offset(b.center.x, b.bottom + 32f * d), Offset(b.right + 26f * d, b.bottom + 26f * d)
    )
}

@Composable
fun Modifier.inkInput(ed: Ed, vm: AppVM, scroll: ScrollState): Modifier {
    val ik = ed.ink
    val dens = LocalDensity.current.density
    val ts by rememberUpdatedState(vm.pr.ts)
    val prefs by rememberUpdatedState(vm.pr)

    // gulir otomatis kalau goresan mendekati tepi layar
    val lv0 = ik.live
    LaunchedEffect(lv0) {
        if (lv0 == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (ik.live !== lv0) break
            val top = 60f * dens
            val zone = 90f * dens
            val bot = ed.viewH - zone
            val vy = lv0.viewY
            val dy = when {
                vy < top -> -((top - vy) / top).coerceAtMost(1f) * 18f * dens
                vy > bot -> ((vy - bot) / zone).coerceAtMost(1f) * 18f * dens
                else -> 0f
            }
            if (dy != 0f) {
                val used = scroll.scrollBy(dy / ik.zoom)
                if (used != 0f && lv0.tool != 2) {
                    addSample(lv0, lv0.lastX, (vy - ik.panY) / ik.zoom + scroll.value, System.currentTimeMillis(), .5f, dens)
                    ik.tick++
                }
            }
        }
    }

    return pointerInput(Unit) {
        awaitPointerEventScope {
            var id: PointerId? = null
            var live: LiveInk? = null
            var committed = false
            var downT = 0L
            var lastMoveT = 0L
            var start = Offset.Zero
            var last = Offset.Zero
            var lastStylus = -10_000L
            var gm = 0                               // gestur seleksi: 1 pindah, 2 ubah ukuran, 3 hapus, 4 rapihkan
            var gBox: Rect? = null
            var gDown = Offset.Zero
            var gTap = true
            var gxf = floatArrayOf(0f, 0f, 1f)

            fun can(t: PointerType): Boolean {
                val st = t == PointerType.Stylus
                val fg = t == PointerType.Touch || t == PointerType.Mouse
                return when (ts) {
                    "stylus" -> st
                    "finger" -> st || fg
                    else -> if (InkEnv.stylus) st else (st || fg)
                }
            }

            fun hitAt(lv: LiveInk, p: Offset) {
                val ids = ed.inkHit(p.x, p.y, lv.wDp * dens, size.width.toFloat())
                if (ids.isNotEmpty()) ik.erase = ik.erase + ids
            }

            fun viewY(p: Offset) = (p.y - scroll.value) * ik.zoom + ik.panY

            fun finish(ok: Boolean, hold: Boolean) {
                val lv = live
                id = null; live = null; committed = false
                ik.busy = false; ik.live = null; ik.eraser = null
                ik.stamp = System.currentTimeMillis()
                val ids = ik.erase
                ik.erase = emptySet()
                val W = size.width.toFloat()
                if (lv == null || !ok) return
                if (lv.tool == 3) { ik.sel = if (lv.n >= 3) ed.inkInside(lv.pts, lv.n, W) else emptySet(); return }
                if (lv.tool == 2) { ed.inkErase(ids); return }
                if (lv.n > 0 && hypot(last.x - lv.lx, last.y - lv.ly) > .7f * dens) lv.add(last.x, last.y, lv.sk)   // buang jeda penyaring di ujung
                var pts = lv.pts; var n = lv.n; var sharp = false; var w = lv.wDp
                if (lv.tool == 0 && hold && prefs.isn) {                 // Rapihkan otomatis: ujung ditahan sekitar setengah detik
                    val r = recognize(pts, n, dens)
                    if (r != null) { pts = r; n = r.size / 3; sharp = true }
                } else if (lv.tool == 1) {                               // stabilo pintar: menempel ke baris teks
                    val r = ed.inkSmartHl(pts, n)
                    if (r != null) { pts = r.first; n = 2; sharp = true; w = r.second }
                }
                ed.inkAddRaw(lv.tool, lv.color, w, pts, n, sharp, W, dens)
            }

            fun endG(ok: Boolean) {
                val m = gm; val x = gxf; val box = gBox
                id = null; gm = 0; gBox = null
                ik.busy = false; ik.xf = null; ik.tick++
                ik.stamp = System.currentTimeMillis()
                if (!ok || box == null) return
                val W = size.width.toFloat()
                when (m) {
                    1, 2 -> if (x[0] != 0f || x[1] != 0f || x[2] != 1f) ed.inkTransform(ik.sel, x[0], x[1], x[2], box.left, box.top, W)
                    3 -> { ed.inkErase(ik.sel); ik.sel = emptySet() }
                    4 -> ed.inkSnapSel(ik.sel, W)
                }
            }

            fun near(p: Offset, o: Offset) = hypot(p.x - o.x, p.y - o.y) <= 26f * dens

            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val now = ev.changes[0].uptimeMillis
                if (ev.changes.any { it.type == PointerType.Stylus }) {
                    if (!InkEnv.stylus) InkEnv.stylus = true
                    lastStylus = now
                }
                if (!ik.on) { if (gm != 0) endG(false); if (id != null) finish(false, false); continue }

                // palm rejection: selama stylus menyentuh atau melayang dekat, telapak / jari diabaikan
                val stylusDown = ev.changes.any { it.type == PointerType.Stylus && it.pressed }
                if (stylusDown || now - lastStylus < 300L) {
                    for (c in ev.changes) if (c.type != PointerType.Stylus && (c.pressed || c.previousPressed)) c.consume()
                }

                if (id == null) {
                    val d = ev.changes.firstOrNull { it.pressed && !it.previousPressed && !it.isConsumed && can(it.type) } ?: continue
                    val styl = d.type == PointerType.Stylus
                    val fingers = ev.changes.count { it.pressed && it.type != PointerType.Stylus }
                    if (!styl && fingers != 1) continue          // dua jari sekaligus: biarkan menggulir / cubit
                    val W = size.width.toFloat()
                    if (ik.tool == 3 && ik.sel.isNotEmpty()) {   // sentuh pegangan frame seleksi?
                        val box = ed.inkBox(ik.sel, W)
                        if (box != null) {
                            val h = inkHandles(box, dens); val p = d.position
                            val m = when {
                                near(p, h.del) -> 3; near(p, h.snap) -> 4; near(p, h.rs) -> 2; near(p, h.mv) -> 1
                                box.inflate(6f * dens).contains(p) -> 1
                                else -> 0
                            }
                            if (m != 0) {
                                id = d.id; gm = m; gBox = box; gDown = p; gTap = true; gxf = floatArrayOf(0f, 0f, 1f)
                                ik.busy = true; ik.pop = false; ik.stamp = System.currentTimeMillis()
                                d.consume(); continue
                            }
                        }
                    }
                    if (ik.tool == 3) ik.sel = emptySet()
                    val p = prefs
                    val tool = ik.tool
                    val lv = LiveInk(tool, p.ic.getOrElse(tool) { 0 }, p.iw.getOrElse(tool) { if (tool == 1) 16f else 3f }, styl)
                    id = d.id; live = lv; committed = styl; lv.com = styl
                    downT = d.uptimeMillis; lastMoveT = d.uptimeMillis; start = d.position; last = d.position
                    lv.lastX = d.position.x; lv.viewY = viewY(d.position)
                    ik.busy = true; ik.live = lv; ik.pop = false
                    ik.stamp = System.currentTimeMillis()
                    if (tool == 2) { ik.eraser = d.position; hitAt(lv, d.position) }
                    else addSample(lv, d.position.x, d.position.y, d.uptimeMillis, d.pressure, dens)
                    ik.tick++
                    if (committed) d.consume()
                    continue
                }

                if (gm != 0) {                                   // sedang menarik pegangan seleksi
                    val c = ev.changes.firstOrNull { it.id == id }
                    if (c == null) { endG(false); continue }
                    c.consume()
                    if (!c.pressed) { endG(if (gm >= 3) gTap else true); continue }
                    val box = gBox ?: continue
                    if (gm >= 3) {
                        if (hypot(c.position.x - gDown.x, c.position.y - gDown.y) > 14f * dens) gTap = false
                        continue
                    }
                    val W = size.width.toFloat()
                    if (gm == 1) {
                        var dx = c.position.x - gDown.x; var dy = c.position.y - gDown.y
                        val mg = 8f * dens                       // magnet ke tepi konten dan tepi blok teks
                        if (abs(box.left + dx) < mg) dx = -box.left
                        else if (abs(box.right + dx - W) < mg) dx = W - box.right
                        var bestE = mg; var adj = 0f
                        for (b in ed.blocks) {
                            val bp = ed.bpos[b.id] ?: continue
                            for (e in floatArrayOf(bp[0], bp[0] + bp[1])) {
                                val t1 = e - (box.top + dy); val t2 = e - (box.bottom + dy)
                                if (abs(t1) < bestE) { bestE = abs(t1); adj = t1 }
                                if (abs(t2) < bestE) { bestE = abs(t2); adj = t2 }
                            }
                        }
                        dy += adj
                        gxf = floatArrayOf(dx, dy, 1f)
                    } else {
                        val sx = (c.position.x - 26f * dens - box.left) / max(box.width, 1f)
                        val sy = (c.position.y - 26f * dens - box.top) / max(box.height, 1f)
                        gxf = floatArrayOf(0f, 0f, max(sx, sy).coerceIn(.2f, 6f))
                    }
                    ik.xf = floatArrayOf(gxf[0], gxf[1], gxf[2], box.left, box.top); ik.tick++
                    continue
                }

                val lv = live ?: continue
                val c = ev.changes.firstOrNull { it.id == id }
                if (c == null) { finish(false, false); continue }
                val others = ev.changes.count { it.pressed && it.id != id && it.type != PointerType.Stylus }
                if (!committed && !lv.stylus && others >= 1) {   // jari kedua mendarat cepat: ini gulir / cubit dua jari, batalkan
                    finish(false, false); continue
                }
                if (!c.pressed) {                                // angkat: selesai (ketukan singkat jadi titik)
                    c.consume()
                    finish(true, c.uptimeMillis - lastMoveT >= 500L)
                    continue
                }
                last = c.position
                lv.lastX = c.position.x; lv.viewY = viewY(c.position)
                if (hypot(c.position.x - c.previousPosition.x, c.position.y - c.previousPosition.y) > 1.5f * dens) lastMoveT = c.uptimeMillis
                if (lv.tool == 2) {
                    ik.eraser = c.position
                    val prev = c.previousPosition
                    val steps = max(1, (hypot(c.position.x - prev.x, c.position.y - prev.y) / max(lv.wDp * dens * .6f, 1f)).toInt())
                    for (s in 1..steps) hitAt(lv, Offset(prev.x + (c.position.x - prev.x) * s / steps, prev.y + (c.position.y - prev.y) * s / steps))
                } else {
                    for (h in c.historical) addSample(lv, h.position.x, h.position.y, h.uptimeMillis, c.pressure, dens)
                    addSample(lv, c.position.x, c.position.y, c.uptimeMillis, c.pressure, dens)
                }
                ik.tick++
                if (!committed && (c.uptimeMillis - downT >= 60L || hypot(c.position.x - start.x, c.position.y - start.y) > 6f * dens)) { committed = true; lv.com = true }
                if (committed) for (x in ev.changes) if (x.pressed) x.consume()
            }
        }
    }
}

// ------------------------------------------------------------------ gambar
private fun DrawScope.drawStroke(s: Ink, path: Path, col: Color, a: Float, dark: Boolean) {
    if (s.t == 1) drawPath(
        path, col, alpha = a, style = Stroke(s.w * density, cap = StrokeCap.Butt, join = StrokeJoin.Round),
        blendMode = if (dark) BlendMode.Screen else BlendMode.Multiply
    ) else drawPath(path, col, alpha = a)
}

/** Dipakai di drawWithContent catatan: saat ekspor, isi catatan direkam ke layer supaya bisa dijadikan gambar utuh. */
fun ContentDrawScope.inkContent(ik: InkCtl) {
    val gl = ik.layer
    if (ik.exporting && gl != null) {
        gl.record { this@inkContent.drawContent() }
        drawLayer(gl)
    } else drawContent()
}

/** Siapkan layer ekspor dan jalankan ekspor ke Galeri (Pictures/Dinduy) saat diminta. */
@Composable
fun InkSetup(ed: Ed) {
    val ik = ed.ink
    val gl = rememberGraphicsLayer()
    SideEffect { ik.layer = gl }
    val ctx = LocalContext.current
    val pal = LocalPal.current
    LaunchedEffect(ik.exporting) {
        if (!ik.exporting) return@LaunchedEffect
        delay(150)
        val msg = try {
            val src = gl.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
            val m = (24f * ed.dens).toInt()
            val out = Bitmap.createBitmap(src.width + 2 * m, src.height + 2 * m, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(out)
            cv.drawColor(pal.s.toArgb())
            cv.drawBitmap(src, m.toFloat(), m.toFloat(), null)
            withContext(Dispatchers.IO) {
                val cvs = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Dinduy_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Dinduy")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cvs) ?: error("uri")
                ctx.contentResolver.openOutputStream(uri)?.use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            "Disimpan ke Galeri (Pictures/Dinduy)"
        } catch (e: Throwable) {
            "Gagal mengekspor gambar (catatan terlalu panjang atau izin penyimpanan ditolak)"
        }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        ik.exporting = false
    }
}

@Composable
fun BoxScope.InkLayers(ed: Ed, vm: AppVM) {
    val pal = LocalPal.current
    val ik = ed.ink
    // goresan selesai: path disimpan di cache, hanya dibangun ulang kalau posisi blok / lebar berubah
    Canvas(Modifier.matchParentSize()) {
        ik.lay
        if (ik.hide) return@Canvas
        val W = size.width
        val d = density
        val er = ik.erase
        val seen = HashSet<String>()
        for (b in ed.blocks) {
            if (b.ik.isEmpty()) continue
            val top = ed.bpos[b.id]?.get(0) ?: continue
            for (s in b.ik) {
                seen.add(s.id)
                var cs = ik.cache[s.id]
                if (cs == null || cs.top != top || cs.w != W || cs.d != d) {
                    cs = CS(top, W, d, storedPath(s, top, W, d)); ik.cache[s.id] = cs
                }
                val a = if (s.id in er) .22f else 1f
                val col = inkColor(pal, s.c, s.t == 1)
                val x = ik.xf
                if (x != null && s.id in ik.sel) withTransform({ translate(x[0], x[1]); scale(x[2], x[2], Offset(x[3], x[4])) }) {
                    drawStroke(s, cs.path, col, a, pal.dark)
                } else drawStroke(s, cs.path, col, a, pal.dark)
            }
        }
        if (ik.cache.size > seen.size + 32) ik.cache.keys.retainAll(seen)
    }
    // goresan yang sedang berjalan: lapisan terpisah, jadi coretan lama tidak ikut digambar ulang
    Canvas(Modifier.matchParentSize()) {
        ik.tick
        val lv = ik.live
        if (lv != null && lv.tool != 2 && lv.n > 0) {
            val path = inkPath(lv.pts, lv.n, lv.tool, lv.wDp * density)
            val col = inkColor(pal, lv.color, lv.tool == 1)
            if (lv.tool == 1) drawPath(
                path, col, style = Stroke(lv.wDp * density, cap = StrokeCap.Butt, join = StrokeJoin.Round),
                blendMode = if (pal.dark) BlendMode.Screen else BlendMode.Multiply
            ) else drawPath(path, col)
        }
        val e = ik.eraser
        if (e != null && lv != null) {
            drawCircle(pal.os, lv.wDp * density, e, alpha = .5f, style = Stroke(1.5f * density))
        }
        if (lv != null && lv.tool == 3 && lv.n > 1) {                       // jalur lasso
            val lp = Path()
            lp.moveTo(lv.pts[0], lv.pts[1])
            for (i in 1 until lv.n) lp.lineTo(lv.pts[i * 3], lv.pts[i * 3 + 1])
            drawPath(lp, pal.p.copy(alpha = .08f))
            drawPath(lp, pal.p, style = Stroke(2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density, 7f * density))))
        }
        ik.lay
        if (!ik.hide && lv == null && ik.sel.isNotEmpty()) {
            val bx = ed.inkBox(ik.sel, size.width)
            if (bx != null) {
                val x = ik.xf
                val box = if (x == null) bx else Rect(
                    x[3] + (bx.left - x[3]) * x[2] + x[0], x[4] + (bx.top - x[4]) * x[2] + x[1],
                    x[3] + (bx.right - x[3]) * x[2] + x[0], x[4] + (bx.bottom - x[4]) * x[2] + x[1]
                )
                val d = density
                drawRoundRect(pal.p, box.topLeft, box.size, androidx.compose.ui.geometry.CornerRadius(4f * d), style = Stroke(1.6f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f * d, 6f * d))))
                val h = inkHandles(box, d)
                val r = 18f * d
                drawLine(pal.p, box.bottomCenter, h.mv, 1.4f * d)
                drawLine(pal.p, box.bottomRight, h.rs, 1.4f * d)
                for (o in listOf(h.del, h.snap, h.mv, h.rs)) {
                    drawCircle(pal.c3, r, o); drawCircle(pal.p, r, o, style = Stroke(1.6f * d))
                }
                val q = 6f * d
                drawLine(pal.p, h.del + Offset(-q, -q), h.del + Offset(q, q), 2f * d, StrokeCap.Round)          // x = hapus
                drawLine(pal.p, h.del + Offset(-q, q), h.del + Offset(q, -q), 2f * d, StrokeCap.Round)
                drawRect(pal.p, h.snap + Offset(-q, -q), Size(2 * q, 2 * q), style = Stroke(2f * d))           // kotak = rapihkan
                drawLine(pal.p, h.mv + Offset(-q - 1f * d, 0f), h.mv + Offset(q + 1f * d, 0f), 2f * d, StrokeCap.Round)   // + = pindah
                drawLine(pal.p, h.mv + Offset(0f, -q - 1f * d), h.mv + Offset(0f, q + 1f * d), 2f * d, StrokeCap.Round)
                drawLine(pal.p, h.rs + Offset(-q, -q), h.rs + Offset(q, q), 2f * d, StrokeCap.Round)           // diagonal = ubah ukuran
            }
        }
    }
}

/** Bingkai tipis di tepi catatan selama mode coret aktif. Tidak menangkap sentuhan. */
@Composable
fun BoxScope.InkFrame(ed: Ed) {
    val pal = LocalPal.current
    AnimatedVisibility(ed.ink.on, Modifier.matchParentSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().border(2.dp, pal.p.copy(alpha = .55f), RoundedCornerShape(6.dp)))
    }
}

// ------------------------------------------------------------------ toolbar
@Composable
private fun ToolIcon(tool: Int, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        when (tool) {
            0 -> {                                              // pulpen: badan miring + ujung
                val p = Path().apply {
                    moveTo(4f * u, 20f * u); lineTo(5f * u, 15f * u); lineTo(16f * u, 4f * u); lineTo(20f * u, 8f * u)
                    lineTo(9f * u, 19f * u); close()
                }
                drawPath(p, tint)
            }
            1 -> {                                              // stabilo: kepala lebar rata + garis
                val p = Path().apply {
                    moveTo(14f * u, 3f * u); lineTo(21f * u, 10f * u); lineTo(12f * u, 19f * u); lineTo(5f * u, 12f * u); close()
                }
                drawPath(p, tint)
                drawRect(tint, Offset(3f * u, 20.5f * u), Size(18f * u, 2.5f * u))
            }
            3 -> {                                              // lasso: lingkaran putus-putus
                drawCircle(tint, 8.5f * u, Offset(12f * u, 12f * u), style = Stroke(2.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 3f * u))))
            }
            else -> {                                           // penghapus: balok miring
                val p = Path().apply {
                    moveTo(3f * u, 15f * u); lineTo(13f * u, 5f * u); lineTo(21f * u, 13f * u); lineTo(13f * u, 21f * u)
                    lineTo(8f * u, 21f * u); close()
                }
                drawPath(p, tint)
            }
        }
    }
}

@Composable
private fun ShapeIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawRect(tint, Offset(3f * u, 11f * u), Size(9f * u, 9f * u), style = Stroke(2f * u))
        drawCircle(tint, 5f * u, Offset(16f * u, 8f * u), style = Stroke(2f * u))
    }
}

@Composable
private fun ExportIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawLine(tint, Offset(12f * u, 4f * u), Offset(12f * u, 15f * u), 2.2f * u, StrokeCap.Round)
        drawLine(tint, Offset(7f * u, 10.5f * u), Offset(12f * u, 15.5f * u), 2.2f * u, StrokeCap.Round)
        drawLine(tint, Offset(17f * u, 10.5f * u), Offset(12f * u, 15.5f * u), 2.2f * u, StrokeCap.Round)
        drawLine(tint, Offset(5f * u, 20f * u), Offset(19f * u, 20f * u), 2.2f * u, StrokeCap.Round)
    }
}

@Composable
private fun EyeIcon(tint: Color, slashed: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawOval(tint, Offset(2f * u, 6.5f * u), Size(20f * u, 11f * u), style = Stroke(2f * u))
        drawCircle(tint, 3f * u, Offset(12f * u, 12f * u))
        if (slashed) drawLine(tint, Offset(4f * u, 20f * u), Offset(20f * u, 4f * u), 2.2f * u, StrokeCap.Round)
    }
}

@Composable
fun BoxScope.InkBar(ed: Ed, vm: AppVM) {
    val pal = LocalPal.current
    val ik = ed.ink
    val pr = vm.pr
    val left = pr.isd == "l"
    val density = LocalDensity.current.density

    LaunchedEffect(ik.on, ik.stamp, pr.iat) {
        if (ik.on && pr.iat > 0) {
            delay(pr.iat * 1000L)
            if (!ik.busy) { ik.on = false; ik.pop = false }
        }
    }

    AnimatedVisibility(
        visible = ik.on,
        modifier = Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd),
        enter = fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(240)) { it / 2 }
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp),
            horizontalAlignment = if (left) Alignment.Start else Alignment.End
        ) {
            val tool = ik.tool
            AnimatedVisibility(ik.pop && tool != 3, enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 3 }, exit = fadeOut(tween(120))) {
                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier.padding(bottom = 8.dp).widthIn(max = 320.dp).fillMaxWidth()
                        .shadow(if (pal.dark) 6.dp else 2.dp, shape).clip(shape).background(pal.c3).padding(16.dp)
                ) {
                    if (tool != 2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (i in 0..4) {
                                val sel = pr.ic.getOrElse(tool) { 0 } == i
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape)
                                        .background(if (tool == 1 && i == 0) hsl(52f, 100f, 55f) else inkColor(pal, i, false))
                                        .then(if (sel) Modifier.border(3.dp, pal.p, CircleShape) else Modifier.border(1.dp, pal.ol, CircleShape))
                                        .clickable {
                                            vm.setPr(pr.copy(ic = pr.ic.toMutableList().also { l -> while (l.size < 3) l.add(0); l[tool] = i }))
                                            ik.stamp = System.currentTimeMillis()
                                        }
                                )
                            }
                        }
                    }
                    val (lo, hi) = when (tool) { 0 -> 1f to 12f; 1 -> 8f to 32f; else -> 8f to 40f }
                    val cur = pr.iw.getOrElse(tool) { 3f }.coerceIn(lo, hi)
                    Row(Modifier.fillMaxWidth().padding(top = if (tool != 2) 14.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        // pratinjau ketebalan goresan
                        Canvas(Modifier.size(width = 56.dp, height = 44.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val col = if (tool == 2) pal.osv else inkColor(pal, pr.ic.getOrElse(tool) { 0 }, tool == 1)
                            when (tool) {
                                0 -> drawCircle(col, cur * density / 2f, c)
                                1 -> drawRect(col, Offset(c.x - 22.dp.toPx(), c.y - cur * density / 2f), Size(44.dp.toPx(), cur * density))
                                else -> drawCircle(col, (cur * density).coerceAtMost(21.dp.toPx()), c, style = Stroke(2f * density))
                            }
                        }
                        Box(Modifier.weight(1f).padding(start = 8.dp)) {
                            BigSlider(cur, lo, hi) { v ->
                                vm.setPr(pr.copy(iw = pr.iw.toMutableList().also { l -> while (l.size < 3) l.add(3f); l[tool] = v }))
                                ik.stamp = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
            val cap = RoundedCornerShape(30.dp)
            var drag by remember { mutableStateOf(0f) }
            Row(
                Modifier.shadow(if (pal.dark) 6.dp else 2.dp, cap).clip(cap).background(pal.c3).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // gagang: geser ke kiri / kanan untuk memindah sisi toolbar (pemakai kidal)
                Box(
                    Modifier.width(22.dp).height(46.dp).pointerInput(left) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag < -48f * density && !left) vm.setPr(pr.copy(isd = "l"))
                                else if (drag > 48f * density && left) vm.setPr(pr.copy(isd = "r"))
                                drag = 0f
                            }
                        ) { _, dx -> drag += dx }
                    },
                    Alignment.Center
                ) {
                    Canvas(Modifier.size(width = 8.dp, height = 20.dp)) {
                        for (r in 0..2) for (c in 0..1) drawCircle(pal.osv, 1.3f * density, Offset(size.width * (.2f + .6f * c), size.height * (.15f + .35f * r)))
                    }
                }
                for (t in 0..3) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Tbn({ ToolIcon(t, it) }, tool == t, true) {
                            ik.hide = false
                            if (tool == t) { if (t <= 2) ik.pop = !ik.pop } else { ik.tool = t; ik.pop = false; ik.sel = emptySet() }
                            ik.stamp = System.currentTimeMillis()
                        }
                        if (t <= 1) Box(
                            Modifier.padding(bottom = 3.dp).size(width = 14.dp, height = 3.dp).clip(CircleShape)
                                .background(inkColor(pal, pr.ic.getOrElse(t) { 0 }, false))
                        )
                    }
                }
                Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(24.dp).background(pal.ol.copy(alpha = .5f)))
                Tbn({ Ic("undo", it) }, false, ed.canUndo) { ed.undo() }
                Tbn({ Ic("redo", it) }, false, ed.canRedo) { ed.redo() }
                Tbn({ ShapeIcon(it) }, pr.isn, true) { vm.setPr(pr.copy(isn = !pr.isn)) }
                Tbn({ EyeIcon(it, ik.hide) }, ik.hide, true) { ik.hide = !ik.hide }
                Tbn({ ExportIcon(it) }, false, !ik.exporting) { ik.hide = false; ik.sel = emptySet(); ik.exporting = true }
                Tbn({ Ic("ok", it) }, false, true) { ik.toggle(ed) }
            }
        }
    }
}

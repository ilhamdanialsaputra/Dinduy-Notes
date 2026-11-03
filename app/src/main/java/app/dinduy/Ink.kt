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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    var moveMs = 0L                              // kapan jari terakhir bergerak (jam dinding), untuk Rapihkan otomatis
    var snap: FloatArray? = null                 // bentuk rapi hasil kenali; tampil selama jari masih ditahan
    var com = false                              // sudah pasti coretan (bukan awal cubit / gulir dua jari)

    fun add(x: Float, y: Float, k: Float) {
        if (n * 3 + 3 > pts.size) pts = pts.copyOf(pts.size * 2)
        pts[n * 3] = x; pts[n * 3 + 1] = y; pts[n * 3 + 2] = k
        n++
    }
}

class CS(val src: Ink, val top: Float, val w: Float, val d: Float, val path: Path)

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
    var box by mutableStateOf<Rect?>(null)                  // frame kotak alat Pilih (piksel konten)
    var sel by mutableStateOf<Set<String>>(emptySet())      // goresan yang ada di dalam frame
    var xf by mutableStateOf<FloatArray?>(null)             // pratinjau pindah / ubah ukuran: dx, dy, skala, pivotX, pivotY
    var zoom by mutableStateOf(1f)
    var panX by mutableStateOf(0f)
    var panY by mutableStateOf(0f)
    var lasso: ArrayList<Offset>? = null          // jejak lasso yang sedang digambar (konten px); gambar ulang lewat tick
    var exporting by mutableStateOf(false)
    var layer: GraphicsLayer? = null
    var busy = false                             // sedang ada gestur coret
    var noRescue = false
    val cache = HashMap<String, CS>()

    fun toggle(ed: Ed) {
        if (on) { on = false; pop = false; sel = emptySet(); box = null; zoom = 1f; panX = 0f; panY = 0f }
        else {
            on = true; hide = false; pop = false
            ed.selImg = null; ed.miniBar = false; ed.sheet = 0; ed.sub = 0
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

/** Goresan yang minimal separuh titiknya berada di dalam frame (piksel konten). */
fun Ed.inkInBox(r: Rect, W: Float): Set<String> {
    val out = HashSet<String>()
    for (b in blocks) {
        val top = bpos[b.id]?.get(0) ?: continue
        for (s in b.ik) {
            val n = s.p.size / 3
            if (n == 0) continue
            var k = 0
            for (i in 0 until n) {
                val x = s.p[i * 3] * W; val y = top + s.p[i * 3 + 1] * dens
                if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) k++
            }
            if (k * 2 >= n) out.add(s.id)
        }
    }
    return out
}

/** Goresan yang minimal separuh titiknya berada di dalam lasso (poligon, pasangan x,y piksel konten). */
fun Ed.inkInPoly(poly: FloatArray, W: Float): Set<String> {
    val m = poly.size / 2
    if (m < 3) return emptySet()
    var px0 = Float.MAX_VALUE; var px1 = -Float.MAX_VALUE; var py0 = Float.MAX_VALUE; var py1 = -Float.MAX_VALUE
    for (i in 0 until m) { px0 = min(px0, poly[i * 2]); px1 = max(px1, poly[i * 2]); py0 = min(py0, poly[i * 2 + 1]); py1 = max(py1, poly[i * 2 + 1]) }
    val out = HashSet<String>()
    for (b in blocks) {
        val top = bpos[b.id]?.get(0) ?: continue
        for (s in b.ik) {
            val n = s.p.size / 3
            if (n == 0) continue
            var k = 0
            for (i in 0 until n) {
                val x = s.p[i * 3] * W; val y = top + s.p[i * 3 + 1] * dens
                if (x < px0 || x > px1 || y < py0 || y > py1) continue
                var inside = false
                var j = m - 1
                for (q in 0 until m) {
                    val xi = poly[q * 2]; val yi = poly[q * 2 + 1]; val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
                    if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                    j = q
                }
                if (inside) k++
            }
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

/** Kenali goresan tunggal: garis, busur, garis patah, lingkaran / elips (miring juga), persegi / persegi panjang (miring juga),
 *  segitiga (sama sisi dikenali), segi-5..8 (beraturan dikenali). null = tidak yakin (biarkan apa adanya). */
internal fun recognize(a: FloatArray, n: Int, dens: Float): FloatArray? {
    if (n < 4) return null
    var len = 0f; var x0 = a[0]; var x1 = a[0]; var y0 = a[1]; var y1 = a[1]
    for (i in 1 until n) {
        len += hypot(a[i * 3] - a[i * 3 - 3], a[i * 3 + 1] - a[i * 3 - 2])
        x0 = min(x0, a[i * 3]); x1 = max(x1, a[i * 3]); y0 = min(y0, a[i * 3 + 1]); y1 = max(y1, a[i * 3 + 1])
    }
    val diag = hypot(x1 - x0, y1 - y0)
    if (len < 40f * dens || diag < 24f * dens) return null
    val gap = hypot(a[n * 3 - 3] - a[0], a[n * 3 - 2] - a[1])
    return if (gap > .22f * len) recognizeOpen(a, n, len, gap, dens) else recognizeClosed(a, n, len, diag, dens)
}

private fun recognizeOpen(a: FloatArray, n: Int, len: Float, gap: Float, dens: Float): FloatArray? {
    val fx = a[0]; val fy = a[1]; val ex = a[n * 3 - 3]; val ey = a[n * 3 - 2]
    if (gap >= .85f * len) {                                   // garis lurus
        var md = 0f
        for (i in 0 until n) md = max(md, segDist(a[i * 3], a[i * 3 + 1], fx, fy, ex, ey))
        if (md <= .06f * gap) {
            var ax = fx; var ay = fy; var bx = ex; var by = ey
            val ang = atan2(ey - fy, ex - fx)
            if (abs(sin(ang)) < .14f) { val ym = (fy + ey) / 2f; ay = ym; by = ym }          // kunci horizontal
            else if (abs(cos(ang)) < .14f) { val xm = (fx + ex) / 2f; ax = xm; bx = xm }     // kunci vertikal
            return tri(ax, ay, bx, by)
        }
    }
    fitArc(a, n, dens)?.let { return it }                      // busur lingkaran
    val v = rdp(a, n, .045f * len)                             // garis patah (zig-zag, panah, huruf V / Z)
    val k = v.size / 3
    if (k in 3..6) {
        for (i in 1 until k) if (hypot(v[i * 3] - v[i * 3 - 3], v[i * 3 + 1] - v[i * 3 - 2]) < .12f * len) return null
        for (i in 1 until k - 1) if (turnDeg(v[i * 3 - 3], v[i * 3 - 2], v[i * 3], v[i * 3 + 1], v[i * 3 + 3], v[i * 3 + 4]) < 25f) return null
        return v
    }
    return null
}

private fun fitArc(a: FloatArray, n: Int, dens: Float): FloatArray? {
    val m1 = n / 2
    val ax = a[0]; val ay = a[1]; val bx = a[m1 * 3]; val by = a[m1 * 3 + 1]; val cx = a[n * 3 - 3]; val cy = a[n * 3 - 2]
    val d = 2f * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
    if (abs(d) < 1e-3f) return null
    val a2 = ax * ax + ay * ay; val b2 = bx * bx + by * by; val c2 = cx * cx + cy * cy
    val ux = (a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by)) / d
    val uy = (a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / d
    val r = hypot(ax - ux, ay - uy)
    if (r < 12f * dens || r > 4000f * dens) return null
    var err = 0f
    for (i in 0 until n) err += abs(hypot(a[i * 3] - ux, a[i * 3 + 1] - uy) - r)
    if (err / n > .05f * r) return null
    val tp = 2f * PI.toFloat()
    fun nm(t: Float): Float { var x = t; while (x < 0f) x += tp; while (x >= tp) x -= tp; return x }
    val a0 = atan2(ay - uy, ax - ux)
    val s1 = nm(atan2(cy - uy, cx - ux) - a0); val sm = nm(atan2(by - uy, bx - ux) - a0)
    val sweep = if (sm <= s1) s1 else -(tp - s1)
    if (abs(sweep) < .45f || abs(sweep) > 5.2f) return null    // kira-kira 26° sampai 300°
    val steps = max(10, (abs(sweep) * 180f / PI.toFloat() / 4f).toInt())
    val o = FloatArray((steps + 1) * 3)
    for (i in 0..steps) {
        val t = a0 + sweep * i / steps
        o[i * 3] = ux + r * cos(t); o[i * 3 + 1] = uy + r * sin(t); o[i * 3 + 2] = 1f
    }
    return o
}

/** Elips terbaik untuk sudut [th]: [err, cx, cy, ra, rb, th] atau null. */
private fun fitEllipse(a: FloatArray, n: Int, th: Float, dens: Float): FloatArray? {
    val c = cos(th); val s = sin(th)
    var u0 = Float.MAX_VALUE; var u1 = -Float.MAX_VALUE; var v0 = Float.MAX_VALUE; var v1 = -Float.MAX_VALUE
    for (i in 0 until n) {
        val u = a[i * 3] * c + a[i * 3 + 1] * s; val v = -a[i * 3] * s + a[i * 3 + 1] * c
        u0 = min(u0, u); u1 = max(u1, u); v0 = min(v0, v); v1 = max(v1, v)
    }
    val cu = (u0 + u1) / 2f; val cv = (v0 + v1) / 2f; val ra = (u1 - u0) / 2f; val rb = (v1 - v0) / 2f
    if (ra < 8f * dens || rb < 8f * dens) return null
    var err = 0f
    for (i in 0 until n) {
        val u = (a[i * 3] * c + a[i * 3 + 1] * s - cu) / ra; val v = (-a[i * 3] * s + a[i * 3 + 1] * c - cv) / rb
        err += abs(u * u + v * v - 1f)
    }
    return floatArrayOf(err / n, cu * c - cv * s, cu * s + cv * c, ra, rb, th)
}

private fun regularPoly(cx: Float, cy: Float, r: Float, k: Int, phase: Float): FloatArray {
    val o = FloatArray((k + 1) * 3)
    for (i in 0..k) {
        val t = phase + 2f * PI.toFloat() * (i % k) / k
        o[i * 3] = cx + r * cos(t); o[i * 3 + 1] = cy + r * sin(t); o[i * 3 + 2] = 1f
    }
    return o
}

private fun closedPoly(cs: List<Float>): FloatArray {
    val k = cs.size / 2
    val o = FloatArray((k + 1) * 3)
    for (i in 0..k) { o[i * 3] = cs[(i % k) * 2]; o[i * 3 + 1] = cs[(i % k) * 2 + 1]; o[i * 3 + 2] = 1f }
    return o
}

/** Sudut-sudut poligon dari jalur tertutup: RDP, lalu titik awal dihitung sebagai sudut kalau memang tikungan. */
private fun cornersOf(a: FloatArray, n: Int, eps: Float): List<Float> {
    val pc = FloatArray((n + 1) * 3)
    a.copyInto(pc, 0, 0, n * 3); pc[n * 3] = a[0]; pc[n * 3 + 1] = a[1]; pc[n * 3 + 2] = 1f
    val v = rdp(pc, n + 1, eps)
    val vc = v.size / 3
    val cs = ArrayList<Float>()
    if (vc < 4) return cs
    val pv = vc - 2
    if (turnDeg(v[pv * 3], v[pv * 3 + 1], v[0], v[1], v[3], v[4]) > 35f) { cs.add(v[0]); cs.add(v[1]) }
    for (i in 1..vc - 2) { cs.add(v[i * 3]); cs.add(v[i * 3 + 1]) }
    return cs
}

private fun recognizeClosed(a: FloatArray, n: Int, len: Float, diag: Float, dens: Float): FloatArray? {
    var mx = 0f; var my = 0f
    for (i in 0 until n) { mx += a[i * 3]; my += a[i * 3 + 1] }
    mx /= n; my /= n
    var sxx = 0f; var syy = 0f; var sxy = 0f
    for (i in 0 until n) { val dx = a[i * 3] - mx; val dy = a[i * 3 + 1] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
    val e0 = fitEllipse(a, n, 0f, dens)
    val e1 = fitEllipse(a, n, .5f * atan2(2f * sxy, sxx - syy), dens)
    val e = if (e0 != null && (e1 == null || e0[0] <= e1[0] + .01f)) e0 else e1
    if (e != null && e[0] < .17f) {                            // lingkaran atau elips (boleh miring)
        val ra = e[3]; val rb = e[4]; val c = cos(e[5]); val s = sin(e[5])
        val circ = abs(ra - rb) < .12f * max(ra, rb)
        val m = 64
        val o = FloatArray((m + 1) * 3)
        for (i in 0..m) {
            val t = 2f * PI.toFloat() * i / m
            val u = (if (circ) (ra + rb) / 2f else ra) * cos(t); val v = (if (circ) (ra + rb) / 2f else rb) * sin(t)
            o[i * 3] = e[1] + u * c - v * s; o[i * 3 + 1] = e[2] + u * s + v * c; o[i * 3 + 2] = 1f
        }
        return o
    }
    for (eps in floatArrayOf(.07f, .05f)) {
        val cs = cornersOf(a, n, eps * diag)
        val k = cs.size / 2
        if (k < 3 || k > 8) continue
        var ok = true
        var emin = Float.MAX_VALUE; var emax = 0f
        for (i in 0 until k) {
            val p = (i + k - 1) % k; val q = (i + 1) % k
            val t = turnDeg(cs[p * 2], cs[p * 2 + 1], cs[i * 2], cs[i * 2 + 1], cs[q * 2], cs[q * 2 + 1])
            val el = hypot(cs[q * 2] - cs[i * 2], cs[q * 2 + 1] - cs[i * 2 + 1])
            emin = min(emin, el); emax = max(emax, el)
            if (el < .06f * len) ok = false
            if (k == 4 && (t < 60f || t > 120f)) ok = false
            if (k == 3 && t > 160f) ok = false
            if (k >= 5 && (t < 20f || t > 150f)) ok = false
        }
        if (!ok) continue
        val ccx = (0 until k).sumOf { cs[it * 2].toDouble() }.toFloat() / k
        val ccy = (0 until k).sumOf { cs[it * 2 + 1].toDouble() }.toFloat() / k
        val rr = (0 until k).sumOf { hypot(cs[it * 2] - ccx, cs[it * 2 + 1] - ccy).toDouble() }.toFloat() / k
        val ph = atan2(cs[1] - ccy, cs[0] - ccx)
        if (k == 3) return if (emin > .86f * emax) regularPoly(ccx, ccy, rr, 3, ph) else closedPoly(cs)
        if (k >= 5) return if (emin > .7f * emax) regularPoly(ccx, ccy, rr, k, ph) else closedPoly(cs)
        // empat sudut
        var axis = true
        for (i in 0 until 4) {
            val q = (i + 1) % 4
            val ddx = abs(cs[q * 2] - cs[i * 2]); val ddy = abs(cs[q * 2 + 1] - cs[i * 2 + 1])
            if (min(ddx, ddy) > .18f * max(ddx, ddy)) axis = false
        }
        var bx0 = Float.MAX_VALUE; var bx1 = -Float.MAX_VALUE; var by0 = Float.MAX_VALUE; var by1 = -Float.MAX_VALUE
        for (i in 0 until 4) { bx0 = min(bx0, cs[i * 2]); bx1 = max(bx1, cs[i * 2]); by0 = min(by0, cs[i * 2 + 1]); by1 = max(by1, cs[i * 2 + 1]) }
        if (axis) return tri(bx0, by0, bx1, by0, bx1, by1, bx0, by1, bx0, by0)
        var right = true
        for (i in 0 until 4) {
            val p = (i + 3) % 4; val q = (i + 1) % 4
            val t = turnDeg(cs[p * 2], cs[p * 2 + 1], cs[i * 2], cs[i * 2 + 1], cs[q * 2], cs[q * 2 + 1])
            if (t < 78f || t > 102f) right = false
        }
        if (!right) return closedPoly(cs)                      // jajar genjang / trapesium: sudut tetap, sisi diluruskan
        val ux = ((cs[2] - cs[0]) + (cs[4] - cs[6])) / 2f; val uy = ((cs[3] - cs[1]) + (cs[5] - cs[7])) / 2f
        val w = hypot(ux, uy); val dx = ux / w; val dy = uy / w
        val vx = ((cs[6] - cs[0]) + (cs[4] - cs[2])) / 2f; val vy = ((cs[7] - cs[1]) + (cs[5] - cs[3])) / 2f
        var h = vx * -dy + vy * dx
        var ww = w
        if (abs(abs(h) - w) < .08f * max(abs(h), w)) { val sq = (abs(h) + w) / 2f; ww = sq; h = if (h < 0f) -sq else sq }
        val nx = -dy; val ny = dx
        return tri(
            ccx - dx * ww / 2f - nx * h / 2f, ccy - dy * ww / 2f - ny * h / 2f,
            ccx + dx * ww / 2f - nx * h / 2f, ccy + dy * ww / 2f - ny * h / 2f,
            ccx + dx * ww / 2f + nx * h / 2f, ccy + dy * ww / 2f + ny * h / 2f,
            ccx - dx * ww / 2f + nx * h / 2f, ccy - dy * ww / 2f + ny * h / 2f,
            ccx - dx * ww / 2f - nx * h / 2f, ccy - dy * ww / 2f - ny * h / 2f
        )
    }
    return null
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
            var sel = false; var sd0 = 1f; var scx = 0f; var scy = 0f      // cubit pada seleksi: jarak & titik tengah awal (koordinat konten)
            var pd = 0f; var d0 = 0f; var pcx = 0f; var pcy = 0f
            fun commitSel() {
                if (!sel) return
                sel = false
                val x = ik.xf; ik.xf = null; ik.busy = false; ik.tick++
                val box = ik.box
                val W = (ed.contentCoords?.size?.width ?: 0).toFloat()
                if (x != null && x.size >= 5 && box != null && W > 0f && ik.sel.isNotEmpty() && (x[2] != 1f || x[0] != 0f || x[1] != 0f)) {
                    ed.inkTransform(ik.sel, x[0], x[1], x[2], x[3], x[4], W)
                    ik.box = box.xfm(x)
                }
            }
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val fs = ev.changes.filter { it.pressed && it.type == PointerType.Touch }
                val lv = ik.live
                // dua jari selalu berarti zoom / geser, bukan menggambar (goresan jari yang sedang berjalan dibatalkan oleh inkInput)
                if (!ik.on || fs.size < 2 || (lv != null && lv.stylus)) { commitSel(); active = false; pinching = false; continue }
                val a = fs[0].position; val b = fs[1].position
                val d = max(hypot(a.x - b.x, a.y - b.y), 1f)
                val cx = (a.x + b.x) / 2f; val cy = (a.y + b.y) / 2f
                if (!active) {
                    active = true; pd = d; d0 = d; pcx = cx; pcy = cy; pinching = ik.zoom > 1f
                    val box = ik.box
                    if (ik.tool == 3 && ik.sel.isNotEmpty() && box != null) {     // titik tengah di dalam seleksi: cubit mengubah ukuran gambar terpilih
                        val ccx = (cx - ik.panX) / ik.zoom; val ccy = (cy - ik.panY) / ik.zoom + scroll.value
                        if (box.inflate(24f * density).contains(Offset(ccx, ccy))) { sel = true; sd0 = d; scx = ccx; scy = ccy; ik.busy = true }
                    }
                    continue
                }
                if (sel) {
                    val ccx = (cx - ik.panX) / ik.zoom; val ccy = (cy - ik.panY) / ik.zoom + scroll.value
                    ik.xf = floatArrayOf(ccx - scx, ccy - scy, (d / sd0).coerceIn(.2f, 6f), scx, scy); ik.tick++
                    for (c in fs) c.consume()
                    pd = d; pcx = cx; pcy = cy
                    continue
                }
                if (!pinching && (abs(d / d0 - 1f) > .04f || hypot(cx - pcx, cy - pcy) > 10f * density)) pinching = true
                if (pinching) {
                    val z0 = ik.zoom
                    val z1 = (z0 * d / pd).coerceIn(1f, 4f)
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
/** Kotak setelah transformasi pratinjau [x] = (dx, dy, skala, pivotX, pivotY). */
private fun Rect.xfm(x: FloatArray): Rect {
    val k = x[2]; val px = x[3]; val py = x[4]
    return Rect(px + (left - px) * k + x[0], py + (top - py) * k + x[1], px + (right - px) * k + x[0], py + (bottom - py) * k + x[1])
}

private class BoxH(val del: Offset, val tl: Offset, val tr: Offset, val bl: Offset, val br: Offset, val mv: Offset)

/** Pegangan frame ditaruh agak menjauh dari kotaknya supaya tidak tertutup jari. */
private fun boxHandles(b: Rect, d: Float): BoxH {
    val o = 16f * d
    return BoxH(
        Offset(b.center.x, max(b.top - 38f * d, 24f * d)),
        Offset(b.left - o, b.top - o), Offset(b.right + o, b.top - o),
        Offset(b.left - o, b.bottom + o), Offset(b.right + o, b.bottom + o),
        Offset(b.center.x, b.bottom + 38f * d)
    )
}

@Composable
fun Modifier.inkInput(ed: Ed, vm: AppVM, scroll: ScrollState): Modifier {
    val ik = ed.ink
    val dens = LocalDensity.current.density
    val ts by rememberUpdatedState(vm.pr.ts)
    val prefs by rememberUpdatedState(vm.pr)
    val haptic = LocalHapticFeedback.current

    // gulir otomatis kalau goresan mendekati tepi layar
    val lv0 = ik.live
    LaunchedEffect(lv0) {
        if (lv0 == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (ik.live !== lv0) break
            // Rapihkan otomatis: jari ditahan ~0,4 detik di ujung goresan -> bentuk rapi langsung muncul + getar
            if (lv0.tool == 0 && lv0.snap == null && lv0.n >= 6 && System.currentTimeMillis() - lv0.moveMs >= 380L) {
                val r = recognize(lv0.pts, lv0.n, dens)
                if (r != null) { lv0.snap = r; haptic.performHapticFeedback(HapticFeedbackType.LongPress); ik.tick++ }
                else lv0.moveMs = Long.MAX_VALUE / 2          // tidak dikenali: jangan coba lagi sampai jari bergerak
            }
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
                if (lv.tool == 2) { ed.inkErase(ids); return }
                if (lv.n > 0 && hypot(last.x - lv.lx, last.y - lv.ly) > .7f * dens) lv.add(last.x, last.y, lv.sk)   // buang jeda penyaring di ujung
                var pts = lv.pts; var n = lv.n; var sharp = false; var w = lv.wDp
                if (lv.tool == 0) {                                      // Rapihkan otomatis (selalu aktif): pakai bentuk yang sudah tampil
                    val r = lv.snap ?: (if (hold) recognize(pts, n, dens) else null)
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
                    1 -> if (x[0] != 0f || x[1] != 0f) {
                        if (ik.sel.isNotEmpty()) ed.inkTransform(ik.sel, x[0], x[1], 1f, box.left, box.top, W)
                        ik.box = box.translate(x[0], x[1])
                    }
                    3 -> { ed.inkErase(ik.sel); ik.sel = emptySet(); ik.box = null }
                    5, 6, 7, 8 -> if (x.size >= 5 && x[2] != 1f && ik.sel.isNotEmpty()) {
                        ed.inkTransform(ik.sel, 0f, 0f, x[2], x[3], x[4], W)
                        ik.box = box.xfm(x)
                    }
                }
            }

            fun near(p: Offset, o: Offset) = hypot(p.x - o.x, p.y - o.y) <= 26f * dens

            fun finishLasso() {
                val pts = ik.lasso; ik.lasso = null
                id = null; gm = 0; ik.busy = false; ik.tick++
                ik.stamp = System.currentTimeMillis()
                if (pts == null || pts.isEmpty()) return
                val W = size.width.toFloat()
                var lx0 = Float.MAX_VALUE; var lx1 = -Float.MAX_VALUE; var ly0 = Float.MAX_VALUE; var ly1 = -Float.MAX_VALUE
                for (o in pts) { lx0 = min(lx0, o.x); lx1 = max(lx1, o.x); ly0 = min(ly0, o.y); ly1 = max(ly1, o.y) }
                val ids: Set<String> = if (max(lx1 - lx0, ly1 - ly0) < 16f * dens) {         // ketukan: pilih satu goresan di bawah jari
                    ed.inkHit(pts[0].x, pts[0].y, 14f * dens, W).take(1).toSet()
                } else {
                    val poly = FloatArray(pts.size * 2)
                    for (i in pts.indices) { poly[i * 2] = pts[i].x; poly[i * 2 + 1] = pts[i].y }
                    ed.inkInPoly(poly, W)
                }
                ik.sel = ids
                ik.box = if (ids.isEmpty()) null else ed.inkBox(ids, W)?.inflate(8f * dens)
                if (ids.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }

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
                    if (ik.tool == 3) {                          // alat Pilih (lasso): seret frame / sudut, atau lingkari goresan
                        val box = ik.box
                        val p = d.position
                        var m = 0
                        if (box != null) {
                            val h = boxHandles(box, dens)
                            m = when {
                                ik.sel.isNotEmpty() && near(p, h.del) -> 3
                                near(p, h.tl) -> 5; near(p, h.tr) -> 6; near(p, h.bl) -> 7; near(p, h.br) -> 8
                                near(p, h.mv) -> 1
                                box.inflate(6f * dens).contains(p) -> 1
                                else -> 0
                            }
                        }
                        id = d.id; gDown = p; gTap = true; gxf = floatArrayOf(0f, 0f, 1f)
                        ik.busy = true; ik.pop = false; ik.stamp = System.currentTimeMillis()
                        if (m != 0) { gm = m; gBox = box }
                        else { gm = 9; gBox = null; ik.lasso = arrayListOf(p); ik.tick++ }
                        d.consume()
                        continue
                    }
                    val p = prefs
                    val tool = ik.tool
                    val lv = LiveInk(tool, p.ic.getOrElse(tool) { 0 }, p.iw.getOrElse(tool) { if (tool == 1) 16f else 3f }, styl)
                    id = d.id; live = lv; committed = styl; lv.com = styl
                    downT = d.uptimeMillis; lastMoveT = d.uptimeMillis; lv.moveMs = System.currentTimeMillis(); start = d.position; last = d.position
                    lv.lastX = d.position.x; lv.viewY = viewY(d.position)
                    ik.busy = true; ik.live = lv; ik.pop = false
                    ik.stamp = System.currentTimeMillis()
                    if (tool == 2) { ik.eraser = d.position; hitAt(lv, d.position) }
                    else addSample(lv, d.position.x, d.position.y, d.uptimeMillis, d.pressure, dens)
                    ik.tick++
                    if (committed) d.consume()
                    continue
                }

                if (gm != 0) {                                   // sedang menarik frame / melingkari
                    val c = ev.changes.firstOrNull { it.id == id }
                    if (c == null) { ik.lasso = null; endG(false); continue }
                    // jari kedua mendarat: ini zoom / cubit, batalkan gestur satu jari
                    if (c.type != PointerType.Stylus && ev.changes.any { it.pressed && it.id != id && it.type != PointerType.Stylus }) {
                        ik.lasso = null; endG(false); continue
                    }
                    c.consume()
                    if (gm == 9) {
                        val ls = ik.lasso
                        if (!c.pressed) { finishLasso(); continue }
                        if (ls != null) {
                            val lp = ls[ls.size - 1]
                            if (hypot(c.position.x - lp.x, c.position.y - lp.y) > 3f * dens) { ls.add(c.position); ik.tick++ }
                        }
                        continue
                    }
                    if (!c.pressed) { endG(if (gm == 3) gTap else true); continue }
                    val box = gBox ?: continue
                    val dx = c.position.x - gDown.x; val dy = c.position.y - gDown.y
                    when (gm) {
                        3 -> if (hypot(dx, dy) > 14f * dens) gTap = false
                        1 -> {
                            gxf = floatArrayOf(dx, dy, 1f)
                            ik.xf = floatArrayOf(dx, dy, 1f, box.left, box.top); ik.tick++
                        }
                        else -> {                                    // tarik sudut: perbesar / perkecil gambar terpilih (proporsional)
                            val pv = when (gm) {
                                5 -> Offset(box.right, box.bottom); 6 -> Offset(box.left, box.bottom)
                                7 -> Offset(box.right, box.top); else -> Offset(box.left, box.top)
                            }
                            val dd = max(hypot(gDown.x - pv.x, gDown.y - pv.y), 1f)
                            val sc = (hypot(c.position.x - pv.x, c.position.y - pv.y) / dd).coerceIn(.2f, 6f)
                            gxf = floatArrayOf(0f, 0f, sc, pv.x, pv.y)
                            ik.xf = gxf; ik.tick++
                        }
                    }
                    continue
                }

                val lv = live ?: continue
                val c = ev.changes.firstOrNull { it.id == id }
                if (c == null) { finish(false, false); continue }
                val others = ev.changes.count { it.pressed && it.id != id && it.type != PointerType.Stylus }
                if (!lv.stylus && others >= 1) {                 // jari kedua mendarat (kapan pun): ini zoom / geser dua jari, goresan dibatalkan
                    finish(false, false); continue
                }
                if (!c.pressed) {                                // angkat: selesai (ketukan singkat jadi titik)
                    c.consume()
                    finish(true, c.uptimeMillis - lastMoveT >= 500L)
                    continue
                }
                last = c.position
                lv.lastX = c.position.x; lv.viewY = viewY(c.position)
                if (hypot(c.position.x - c.previousPosition.x, c.position.y - c.previousPosition.y) > 1.5f * dens) { lastMoveT = c.uptimeMillis; lv.moveMs = System.currentTimeMillis(); lv.snap = null }
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
fun BoxScope.InkLayers(ed: Ed, vm: AppVM, scroll: ScrollState) {
    val pal = LocalPal.current
    val ik = ed.ink
    // alat Pilih: tanpa frame bawaan; pilih dengan melingkari goresan (lasso) atau mengetuknya
    LaunchedEffect(ik.on, ik.tool) {
        if (!(ik.on && ik.tool == 3)) { ik.box = null; ik.sel = emptySet(); ik.lasso = null }
    }
    LaunchedEffect(ed.blocks) {
        if (ik.tool == 3 && ik.sel.isNotEmpty()) {
            val all = HashSet<String>()
            for (b in ed.blocks) for (s in b.ik) all.add(s.id)
            val keep = ik.sel.filter { it in all }.toSet()
            if (keep.size != ik.sel.size) { ik.sel = keep; if (keep.isEmpty()) ik.box = null }
        }
    }
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
                if (cs == null || cs.src !== s || cs.top != top || cs.w != W || cs.d != d) {
                    cs = CS(s, top, W, d, storedPath(s, top, W, d)); ik.cache[s.id] = cs
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
        if (lv != null && lv.tool <= 1 && lv.n > 0) {
            val sn = lv.snap
            val path = if (sn != null) inkPath(sn, sn.size / 3, 0, lv.wDp * density, true) else inkPath(lv.pts, lv.n, lv.tool, lv.wDp * density)
            val col = inkColor(pal, lv.color, lv.tool == 1)
            if (sn != null) drawPath(path, pal.p.copy(alpha = .28f), style = Stroke(lv.wDp * density + 14f * density, join = StrokeJoin.Round))   // lingkar cahaya: tanda sudah dirapikan
            if (lv.tool == 1) drawPath(
                path, col, style = Stroke(lv.wDp * density, cap = StrokeCap.Butt, join = StrokeJoin.Round),
                blendMode = if (pal.dark) BlendMode.Screen else BlendMode.Multiply
            ) else drawPath(path, col)
        }
        val e = ik.eraser
        if (e != null && lv != null) {
            drawCircle(pal.os, lv.wDp * density, e, alpha = .5f, style = Stroke(1.5f * density))
        }
        ik.lay
        val ls = ik.lasso
        if (ls != null && ls.size > 1) {                                                                       // jejak lasso
            val lp = Path()
            lp.moveTo(ls[0].x, ls[0].y)
            for (i in 1 until ls.size) lp.lineTo(ls[i].x, ls[i].y)
            lp.close()
            drawPath(lp, pal.p.copy(alpha = .10f))
            drawPath(lp, pal.p, style = Stroke(2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density, 7f * density))))
        }
        val bx0 = ik.box
        if (ik.on && ik.tool == 3 && bx0 != null && lv == null) {            // frame kotak alat Pilih
            val x = ik.xf
            val box = if (x == null || x.size < 5) bx0 else bx0.xfm(x)
            val d = density
            val h = boxHandles(box, d)
            drawRect(pal.p.copy(alpha = .07f), box.topLeft, box.size)
            drawRect(pal.p, box.topLeft, box.size, style = Stroke(1.8f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * d, 6f * d))))
            for ((c, o) in listOf(box.topLeft to h.tl, box.topRight to h.tr, box.bottomLeft to h.bl, box.bottomRight to h.br)) {
                drawLine(pal.p, c, o, 1.4f * d)
                drawCircle(pal.c3, 11f * d, o); drawCircle(pal.p, 11f * d, o, style = Stroke(2f * d))
            }
            val r = 18f * d; val q = 6f * d
            drawLine(pal.p, box.bottomCenter, h.mv, 1.4f * d)
            drawCircle(pal.c3, r, h.mv); drawCircle(pal.p, r, h.mv, style = Stroke(1.6f * d))
            drawLine(pal.p, h.mv + Offset(-q - d, 0f), h.mv + Offset(q + d, 0f), 2f * d, StrokeCap.Round)          // + = pindah
            drawLine(pal.p, h.mv + Offset(0f, -q - d), h.mv + Offset(0f, q + d), 2f * d, StrokeCap.Round)
            if (ik.sel.isNotEmpty()) {                                                                           // x = hapus isi frame
                drawLine(pal.p, box.topCenter, h.del, 1.4f * d)
                drawCircle(pal.c3, r, h.del); drawCircle(pal.p, r, h.del, style = Stroke(1.6f * d))
                drawLine(pal.p, h.del + Offset(-q, -q), h.del + Offset(q, q), 2f * d, StrokeCap.Round)
                drawLine(pal.p, h.del + Offset(-q, q), h.del + Offset(q, -q), 2f * d, StrokeCap.Round)
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
private val PENCIL by lazy {
    PathParser().parsePathString("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z").toPath()
}

/** Ikon alat coret: 0 pulpen (pensil), 1 stabilo (spidol miring + garis), 2 penghapus (blok miring), 3 lasso (tali melingkar), 4 menu "Coretan". */
@Composable
fun InkToolIcon(tool: Int, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        when (tool) {
            0 -> withTransform({ scale(u, u, Offset.Zero) }) { drawPath(PENCIL, tint) }
            1 -> {
                withTransform({ rotate(40f, Offset(12f * u, 10f * u)) }) {
                    drawRoundRect(tint, Offset(8.5f * u, 1.5f * u), Size(7f * u, 11f * u), CornerRadius(1.5f * u))
                    val tip = Path().apply {
                        moveTo(8.5f * u, 13.8f * u); lineTo(15.5f * u, 13.8f * u); lineTo(13.5f * u, 18f * u); lineTo(10.5f * u, 18f * u); close()
                    }
                    drawPath(tip, tint)
                }
                drawRoundRect(tint, Offset(4f * u, 19f * u), Size(13f * u, 3f * u), CornerRadius(1.5f * u))
            }
            2 -> {
                withTransform({ rotate(-45f, Offset(12f * u, 11f * u)) }) {
                    drawRoundRect(tint, Offset(4.5f * u, 6.5f * u), Size(15f * u, 9f * u), CornerRadius(2f * u), style = Stroke(2f * u))
                    drawRoundRect(tint, Offset(4.5f * u, 6.5f * u), Size(7.5f * u, 9f * u), CornerRadius(2f * u))
                }
                drawLine(tint, Offset(4f * u, 21f * u), Offset(20f * u, 21f * u), 2f * u, StrokeCap.Round)
            }
            3 -> {                                              // Pilih: empat sudut bingkai
                val p = Path().apply {
                    moveTo(4f * u, 9f * u); lineTo(4f * u, 4f * u); lineTo(9f * u, 4f * u)
                    moveTo(15f * u, 4f * u); lineTo(20f * u, 4f * u); lineTo(20f * u, 9f * u)
                    moveTo(20f * u, 15f * u); lineTo(20f * u, 20f * u); lineTo(15f * u, 20f * u)
                    moveTo(9f * u, 20f * u); lineTo(4f * u, 20f * u); lineTo(4f * u, 15f * u)
                }
                drawPath(p, tint, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            else -> {
                withTransform({ scale(u * .82f, u * .82f, Offset.Zero) }) { drawPath(PENCIL, tint) }
                val w = Path().apply { moveTo(8f * u, 21f * u); quadraticBezierTo(10.5f * u, 17.8f * u, 13f * u, 21f * u); quadraticBezierTo(15.5f * u, 24f * u, 18f * u, 21f * u) }
                drawPath(w, tint, style = Stroke(2f * u, cap = StrokeCap.Round))
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
    val density = LocalDensity.current.density

    LaunchedEffect(ik.on, ik.stamp, pr.iat) {
        if (ik.on && pr.iat > 0) {
            delay(pr.iat * 1000L)
            if (!ik.busy) { ik.on = false; ik.pop = false }
        }
    }

    AnimatedVisibility(
        visible = ik.on,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(240)) { it / 2 }
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val tool = ik.tool
            AnimatedVisibility(ik.pop && tool <= 2, enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 3 }, exit = fadeOut(tween(120))) {
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
            Row(
                Modifier.shadow(if (pal.dark) 6.dp else 2.dp, cap).clip(cap).background(pal.c3).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (t in 0..3) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Tbn({ InkToolIcon(t, it) }, tool == t, true) {
                            ik.hide = false
                            if (tool == t) { if (t <= 2) ik.pop = !ik.pop } else { ik.tool = t; ik.pop = false; ik.sel = emptySet(); ik.box = null }
                            ik.stamp = System.currentTimeMillis()
                        }
                        if (t <= 1) Box(
                            Modifier.padding(bottom = 3.dp).size(width = 14.dp, height = 3.dp).clip(CircleShape)
                                .background(inkColor(pal, pr.ic.getOrElse(t) { 0 }, false))
                        )
                    }
                }
                Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(24.dp).background(pal.ol.copy(alpha = .5f)))
                Tbn({ Ic("ok", it) }, false, true) { ik.toggle(ed) }
            }
        }
    }
}

package app.dinduy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** VM aplikasi untuk komponen yang jauh di dalam pohon (kartu catatan, bayangan morph). */
val LocalVM = staticCompositionLocalOf<AppVM?> { null }

/** Rasio lebar/tinggi banner sampul di editor. */
const val EDIT_RATIO = 1.28f

/** Rasio lebar/tinggi sampul di kartu beranda: 2 kolom lebih tinggi, 1 kolom berbentuk banner. */
fun coverAspect(c2: Boolean): Float = if (c2) .8f else 1.6f

private fun cSmooth(x: Float): Float {
    val k = x.coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

/** Ukuran konten gambar (px) saat menutup bingkai bw x bh dengan zoom z. */
private fun coverSize(o: Float, bw: Float, bh: Float, z: Float): Pair<Float, Float> =
    if (o > bw / bh) (bh * o * z) to (bh * z) else (bw * z) to (bw / o * z)

/** Posisi geser baru supaya pusat bagian yang terlihat tidak berpindah saat zoom berubah. */
private fun coverRezoom(p: Float, v0: Float, v1: Float): Float {
    if (v1 >= .999f) return .5f
    val c = (1f - v0) * p + v0 / 2f
    return ((c - v1 / 2f) / (1f - v1)).coerceIn(0f, 1f)
}

/** Gambar mengisi bingkai (cover-fit), dipangkas dengan titik fokus (px, py) dan zoom z. */
fun DrawScope.drawCover(bmp: ImageBitmap, z0: Float, px: Float, py: Float) {
    val bw = size.width
    val bh = size.height
    if (bw < 1f || bh < 1f) return
    val iw = bmp.width
    val ih = bmp.height
    val z = z0.coerceIn(1f, 4f)
    val (cw, ch) = coverSize(iw.toFloat() / ih, bw, bh, z)
    val vw = (iw * bw / cw).coerceIn(1f, iw.toFloat())
    val vh = (ih * bh / ch).coerceIn(1f, ih.toFloat())
    val ox = ((iw - vw) * px).coerceIn(0f, iw - vw)
    val oy = ((ih - vh) * py).coerceIn(0f, ih - vh)
    drawImage(
        bmp, IntOffset(ox.roundToInt(), oy.roundToInt()), IntSize(vw.roundToInt(), vh.roundToInt()),
        dstSize = IntSize(bw.roundToInt(), bh.roundToInt()), filterQuality = FilterQuality.Medium
    )
}

/** Sampul di kartu (beranda, arsip, sampah, bayangan morph). Tinggi ditentukan modifier (aspectRatio). */
@Composable
fun CoverImage(vm: AppVM, n: Note, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val bmp = rememberImg(vm, n.cv)
    Canvas(modifier.background(pal.c3)) {
        if (bmp != null) drawCover(bmp, n.cz, n.cx, n.cy)
    }
}

/**
 * Banner sampul di atas editor, digambar sebagai lapisan di belakang bilah atas dan ikut tergulir bersama isi.
 * Sampul ini TIDAK memudar saat morph: ia buram penuh sejak awal dan menimpa sampul kartu (bayangan kartu tidak menggambar sampul,
 * hanya menyisakan ruangnya), jadi tidak ada dua lapisan setengah-transparan yang bertumpuk menjadi pucat sesaat (kedip).
 * Saat morph buka/tutup, tingginya berinterpolasi dari tinggi sampul di kartu ke tinggi banner: karena isi editor
 * ikut diskalakan ke dalam kartu, sampul ini persis menimpa sampul di kartu (bayangan) sehingga pudar-silangnya tak terlihat,
 * dan bannernya terasa "tumbuh" dari kartu.
 */
@Composable
fun EditorCover(vm: AppVM, d: Note, scroll: () -> Int) {
    val pal = LocalPal.current
    val bmp = rememberImg(vm, d.cv)
    val clear = pal.s.copy(alpha = 0f)
    Box(
        Modifier.fillMaxWidth()
            .layout { m, c ->
                val w = c.maxWidth
                val t = vm.openP.value.coerceIn(0f, 1f)
                val he = w / EDIT_RATIO
                val mo = vm.morph
                val g = mo?.ghost
                val from = if (mo != null && mo.card && g != null) w / coverAspect(g.c2) else he
                val h = (from + (he - from) * t).roundToInt().coerceAtLeast(1)
                val p = m.measure(Constraints.fixed(w, h))
                layout(w, h) { p.place(0, 0) }
            }
            .graphicsLayer {
                // ikut tergulir saat catatan terbuka penuh; saat menutup, sampul meluncur kembali ke atas sebelum sampai di kartu,
                // jadi yang menimpa sampul kartu selalu bagian atas gambar (bukan sampul yang sudah tergulir keluar)
                val k = cSmooth((vm.openP.value - .5f) / .5f)
                translationY = -scroll().toFloat() * k
                clip = true
            }
            .background(pal.c3)
    ) {
        Canvas(Modifier.fillMaxSize()) { if (bmp != null) drawCover(bmp, d.cz, d.cx, d.cy) }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val e = cSmooth(vm.openP.value)
                drawRect(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to pal.s.copy(alpha = .5f * e),
                            .28f to clear,
                            .42f to clear,
                            1f to pal.s.copy(alpha = e)
                        )
                    )
                )
            }
        )
    }
}

/** Halaman Sampul: pilih foto, geser dan cubit untuk memangkas (beranda dan catatan memakai titik fokus yang sama). */
@Composable
fun CoverPage(vm: AppVM, nav: Nav, act: Actions, id: String) {
    val d = vm.note(id)
    val cv = d?.cv ?: ""
    PageScaffold("Sampul", d?.t?.ifEmpty { null } ?: "Tanpa judul", { nav.pop() }) {
        if (d == null || cv.isEmpty()) {
            SGroup("Gambar sampul", 0) {
                MRow(POS_ONE, "img", "Pilih foto", "Tampil di kartu beranda dan di bagian atas catatan") { act.pickCover(id) }
            }
        } else key(cv) {
            CoverEditor(vm, d)
            SGroup("Kelola", 60) {
                MRow(POS_TOP, "swap", "Ganti foto", "Pilih gambar lain dari galeri") { act.pickCover(id) }
                MRow(POS_MID, "pen", "Atur ulang posisi", "Kembali ke tengah gambar, tanpa zoom") {
                    vm.setCoverCrop(id, cv, .5f, .5f, 1f)
                    nav.pop(); nav.push(Scr.Cover(id))
                }
                MRow(POS_BOT, "del", "Hapus sampul", "Gambar dilepas dari catatan ini", danger = true) {
                    vm.removeCover(id)
                    nav.pop()
                }
            }
        }
    }
}

@Composable
private fun CoverEditor(vm: AppVM, d: Note) {
    val pal = LocalPal.current
    val bmp = rememberImg(vm, d.cv)
    var x by remember { mutableFloatStateOf(d.cx) }
    var y by remember { mutableFloatStateOf(d.cy) }
    var z by remember { mutableFloatStateOf(d.cz) }
    var frame by remember { mutableIntStateOf(0) }
    val cur = remember { floatArrayOf(d.cx, d.cy, d.cz) }
    val a = if (frame == 0) coverAspect(vm.pr.cols.coerceIn(1, 2) == 2) else EDIT_RATIO

    // simpan dengan jeda saat digeser, dan sekali lagi saat halaman ditutup
    LaunchedEffect(x, y, z) {
        delay(300)
        vm.setCoverCrop(d.id, d.cv, x, y, z)
    }
    DisposableEffect(Unit) {
        onDispose { vm.setCoverCrop(d.id, d.cv, cur[0], cur[1], cur[2]) }
    }

    SGroup("Atur posisi", 0) {
        SCard(POS_ONE) {
            SegPill(2, frame, { frame = it }) { i, fg -> T(if (i == 0) "Beranda" else "Catatan", 15f, 500, fg) }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth(if (a < 1f) .62f else 1f).aspectRatio(a)
                        .clip(RoundedCornerShape(12.dp)).background(pal.c3)
                        .pointerInput(bmp) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val b = bmp ?: return@detectTransformGestures
                                val bw = size.width.toFloat().coerceAtLeast(1f)
                                val bh = size.height.toFloat().coerceAtLeast(1f)
                                val o = b.width.toFloat() / b.height
                                val (cw0, ch0) = coverSize(o, bw, bh, z)
                                val nz = (z * zoom).coerceIn(1f, 4f)
                                val (cw1, ch1) = coverSize(o, bw, bh, nz)
                                var nx = coverRezoom(x, bw / cw0, bw / cw1)
                                var ny = coverRezoom(y, bh / ch0, bh / ch1)
                                val rx = cw1 - bw
                                val ry = ch1 - bh
                                if (rx > 2f) nx = (nx - pan.x / rx).coerceIn(0f, 1f)
                                if (ry > 2f) ny = (ny - pan.y / ry).coerceIn(0f, 1f)
                                z = nz; x = nx; y = ny
                                cur[0] = nx; cur[1] = ny; cur[2] = nz
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) { if (bmp != null) drawCover(bmp, z, x, y) }
                }
            }
            T(
                "Seret untuk menggeser, cubit untuk memperbesar. Posisi yang sama dipakai di beranda dan di dalam catatan.",
                13f, 400, pal.osv, Modifier.padding(top = 12.dp), lh = 1.4f
            )
        }
    }
}

package app.dinduy

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** VM aplikasi untuk komponen yang jauh di dalam pohon (kartu catatan, bayangan morph). */
val LocalVM = staticCompositionLocalOf<AppVM?> { null }

/** Rasio lebar/tinggi banner sampul di editor. */
const val EDIT_RATIO = 1.28f

// ---------- sampul gaya Frame (kotak bersudut bundar seperti Google Keep) ----------
/** Rasio lebar/tinggi frame di editor (sedikit lebih lebar dari banner gradasi). */
const val FRAME_EDIT_RATIO = 1.86f
/** Jarak frame ke tepi layar di editor (dp), dan sudutnya (sedang, tidak terlalu bundar). */
const val FRAME_EDIT_PAD = 24f
const val FRAME_EDIT_R = 12f
/** Jarak frame ke tepi kartu di beranda (dp), dan sudutnya. */
const val FRAME_CARD_PAD = 8f
const val FRAME_CARD_R = 8f
/** Rasio frame di kartu beranda: 2 kolom hampir persegi, 1 kolom lebar. */
fun frameAspect(c2: Boolean): Float = if (c2) 1.05f else 1.9f

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

/** Sedikit meredam warna foto di mode terang supaya sampul tak terlalu mencolok (kejenuhan 100% -> 84%). */
private val SOFT_COVER = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.84f) })

/** Gambar mengisi bingkai (cover-fit), dipangkas dengan titik fokus (px, py) dan zoom z. [soft] = redam warna (sampul di editor, mode terang). */
fun DrawScope.drawCover(bmp: ImageBitmap, z0: Float, px: Float, py: Float, soft: Boolean = false) {
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
        dstSize = IntSize(bw.roundToInt(), bh.roundToInt()), filterQuality = FilterQuality.Medium,
        colorFilter = if (soft) SOFT_COVER else null
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

private const val LIGHT_N = 28

/**
 * Kurva pudar sampul di mode terang (alpha kertas dari atas ke bawah banner).
 * Dibuat landai: kerudung tipis di tepi atas (ikon status tetap terbaca) yang meluruh sendiri, lalu foto larut perlahan ke kertas
 * lewat smoothstep yang dimulai di ~20% tinggi dan mendarat tepat di tepi bawah (tanpa tepi tegas).
 * Perkiraan: ~4% di 30% tinggi, ~32% di 50%, ~75% di 74% (awal judul, teks gelap tetap kontras), penuh di 100%.
 * Kemiringan maksimumnya sekitar setengah dari kurva lama, jadi peralihannya tidak terasa tajam.
 */
private val LIGHT_FADE = FloatArray(LIGHT_N) { i ->
    val p = i / (LIGHT_N - 1).toFloat()
    val top = .13f * (1f - cSmooth(p / .30f))
    val body = cSmooth((p - .20f) / .80f)
    1f - (1f - top) * (1f - body)
}

/** Seberapa lambat gambar bergerak dibanding isi saat digulir (0 = ikut isi, 1 = diam di layar). Makin besar, makin terasa parallax. */
private const val COVER_PARALLAX = .55f

/** Tambahan zoom gambar saat sampul tergulir habis (0.6 = membesar sampai 160%). */
private const val COVER_ZOOM = .6f

/** Kemiringan 3D (derajat) saat sampul tergulir habis. Kalau yang terlihat malah sisi atas maju ke arah kita, ubah tandanya jadi minus. */
private const val COVER_TILT = 20f

/** Blur maksimum (dp) saat sampul tergulir habis. Hanya Android 12+; di bawahnya dilewati. */
private const val COVER_BLUR = 22f

/** Kemajuan gulir sampul 0..1 (1 = sampul sudah tergulir keluar seluruhnya). Hanya aktif setelah catatan terbuka penuh, jadi morph buka/tutup tidak terganggu. */
private fun coverQ(open: Float, scrollPx: Int, h: Float): Float =
    (scrollPx / h.coerceAtLeast(1f)).coerceIn(0f, 1f) * cSmooth((open - .5f) / .5f)

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
    // Catatan berornamen: banner TIDAK lagi menimpa dirinya dengan kerudung warna kertas (yang menutupi ornamen di dalam banner,
    // sehingga ornamen "terpotong" di tepi bawah). Pudarnya dibuat lewat topeng alfa pada banner itu sendiri, jadi yang tersingkap
    // di bawahnya adalah kertas + ornamen asli. Kurva dan semua pewaktuannya (e, scroll) sama persis dengan kerudung lama.
    val orn = d.orn.isNotEmpty()
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
                if (orn) compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .let { if (orn) it else it.background(pal.c3) }
            .let {
                if (!orn) it else it.drawWithContent {
                    drawContent()
                    val e = cSmooth(vm.openP.value)
                    val k = Color.Black
                    drawRect(
                        Brush.verticalGradient(
                            colorStops = if (pal.dark) arrayOf(
                                0f to k.copy(alpha = 1f - .5f * e),
                                .28f to k,
                                .42f to k,
                                1f to k.copy(alpha = 1f - e)
                            ) else Array(LIGHT_N) { i ->
                                (i / (LIGHT_N - 1).toFloat()) to k.copy(alpha = 1f - LIGHT_FADE[i] * e)
                            }
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                    // larut saat digulir: sama dengan lapisan kertas lama (alpha .94), tapi sebagai topeng alfa
                    val dis = .94f * cSmooth((coverQ(vm.openP.value, scroll(), size.height) - .18f) / .82f)
                    if (dis > .001f) drawRect(k.copy(alpha = 1f - dis), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                }
            }
    ) {
        // lapisan gambar: tertinggal dari isi (parallax), membesar dari tepi bawah, miring ke belakang seperti halaman yang dilipat, dan memburam
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val sp = scroll()
                val open = vm.openP.value
                val q = coverQ(open, sp, size.height)
                translationY = sp * COVER_PARALLAX * cSmooth((open - .5f) / .5f)
                val z = 1f + COVER_ZOOM * cSmooth(q)
                scaleX = z; scaleY = z
                transformOrigin = TransformOrigin(.5f, 1f)
                cameraDistance = 14f * density
                rotationX = COVER_TILT * cSmooth(q)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    // dibulatkan per .5 supaya efek blur tidak dibuat ulang tiap piksel gulir
                    val r = (cSmooth((q - .12f) / .88f) * COVER_BLUR * density * 2f).roundToInt() / 2f
                    renderEffect = if (r > .5f) BlurEffect(r, r, TileMode.Clamp) else null
                }
            }
        ) { if (bmp != null) drawCover(bmp, d.cz, d.cx, d.cy, soft = !pal.dark) }
        if (!orn) Box(
            Modifier.fillMaxSize().drawBehind {
                val e = cSmooth(vm.openP.value)
                drawRect(
                    Brush.verticalGradient(
                        colorStops = if (pal.dark) arrayOf(
                            0f to pal.s.copy(alpha = .5f * e),
                            .28f to clear,
                            .42f to clear,
                            1f to pal.s.copy(alpha = e)
                        ) else Array(LIGHT_N) { i ->
                            // terang: lihat LIGHT_FADE (kurva landai, banyak titik henti supaya tidak ada pita / siku)
                            (i / (LIGHT_N - 1).toFloat()) to pal.s.copy(alpha = LIGHT_FADE[i] * e)
                        }
                    )
                )
            }
        )
        // sampul larut ke warna kertas saat digulir (satu lapisan polos, murah digambar)
        if (!orn) Box(
            Modifier.fillMaxSize().graphicsLayer {
                val q = coverQ(vm.openP.value, scroll(), size.height)
                alpha = .94f * cSmooth((q - .18f) / .82f)
            }.background(pal.s)
        )
    }
}

/**
 * Geometri frame di editor (px) pada progres morph t: [x, y, lebar, tinggi, radius].
 * t = 1: tata letak editor (24dp dari tepi, tepat di bawah bilah atas). t = 0 (morph dari kartu): persis frame di kartu.
 * Isi editor diskalakan ke dalam kartu selama morph (skala = lebar kartu / lebar layar), jadi ukuran kartu dibagi skala itu
 * supaya setelah diskalakan frame ini jatuh tepat di atas frame kartu, lalu tumbuh ke posisi akhirnya.
 */
private fun frameGeo(vm: AppVM, w: Float, den: Float, satPx: Float, o: FloatArray) {
    val t = vm.openP.value.coerceIn(0f, 1f)
    val contentW = minOf(w, 700f * den)
    val x1 = (w - contentW) / 2f + FRAME_EDIT_PAD * den
    val w1 = (w - 2f * x1).coerceAtLeast(1f)
    val h1 = w1 / FRAME_EDIT_RATIO
    val y1 = satPx + 72f * den
    var x0 = x1; var y0 = y1; var w0 = w1; var h0 = h1; var r0 = FRAME_EDIT_R * den
    val mo = vm.morph
    val g = mo?.ghost
    if (mo != null && mo.card && g != null) {
        val s0 = ((mo.r - mo.l) / w).coerceIn(.05f, 1f)
        x0 = FRAME_CARD_PAD * den / s0
        y0 = x0
        w0 = (w - 2f * x0).coerceAtLeast(1f)
        h0 = w0 / frameAspect(g.c2)
        r0 = FRAME_CARD_R * den / s0
    }
    o[0] = x0 + (x1 - x0) * t
    o[1] = y0 + (y1 - y0) * t
    o[2] = w0 + (w1 - w0) * t
    o[3] = h0 + (h1 - h0) * t
    o[4] = r0 + (FRAME_EDIT_R * den - r0) * t
}

/**
 * Sampul Frame di atas editor: kotak bersudut bundar yang ikut tergulir bersama isi. Seperti EditorCover, ia buram penuh sejak awal morph
 * dan menimpa frame di kartu (kartu bayangan hanya menyisakan ruangnya). Ruang frame di alur isi dipesan Spacer di NoteScreen
 * (4dp + tinggi frame), jadi label dan judul otomatis turun di bawahnya dan bilah atas tidak ikut berubah ukuran.
 */
@Composable
fun EditorFrame(vm: AppVM, d: Note, scroll: () -> Int) {
    val pal = LocalPal.current
    val bmp = rememberImg(vm, d.cv)
    val sat = satDp()
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    // tinggi lapisan TETAP (geometri akhir); animasi buka/tutup hanya menggambar ulang (draw phase), tanpa relayout tiap frame
    val layerH = sat + 72.dp + (minOf(screenW.value, 700f) - 2f * FRAME_EDIT_PAD).dp / FRAME_EDIT_RATIO
    val g = remember { FloatArray(5) }
    val path = remember { androidx.compose.ui.graphics.Path() }
    Canvas(
        Modifier.fillMaxWidth().height(layerH)
            .graphicsLayer {
                val k = cSmooth((vm.openP.value - .5f) / .5f)
                translationY = -scroll().toFloat() * k
            }
    ) {
        frameGeo(vm, size.width, density, sat.toPx(), g)
        val fw = g[2]; val fh = g[3]
        if (fw < 2f || fh < 2f) return@Canvas
        path.reset()
        path.addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                g[0], g[1], g[0] + fw, g[1] + fh,
                androidx.compose.ui.geometry.CornerRadius(g[4], g[4])
            )
        )
        clipPath(path) {
            drawRect(pal.c3)
            if (bmp != null) {
                inset(g[0], g[1], size.width - g[0] - fw, size.height - g[1] - fh) {
                    drawCover(bmp, d.cz, d.cx, d.cy, soft = !pal.dark)
                }
            }
        }
    }
}

/** Halaman Sampul: pilih foto, geser dan cubit untuk memangkas (beranda dan catatan memakai titik fokus yang sama). */
@Composable
fun CoverPage(vm: AppVM, nav: Nav, act: Actions, id: String) {
    val d = vm.note(id)
    val cv = d?.cv ?: ""
    val pal = LocalPal.current
    PageScaffold("Cover", d?.t?.ifEmpty { null } ?: "Untitled", { nav.pop() }) {
        if (d == null || cv.isEmpty()) {
            SGroup("Cover image", 0) {
                MRow(POS_ONE, "img", "Choose photo", "Shown on the home card and at the top of the note") { act.pickCover(id) }
            }
        } else key(cv) {
            SGroup("Style", 0) {
                SCard(POS_ONE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoverStyleTile(Modifier.weight(1f), "Gradient", false, d.cm != "f", vm, d) { vm.setCoverMode(d.id, "") }
                        CoverStyleTile(Modifier.weight(1f), "Frame", true, d.cm == "f", vm, d) { vm.setCoverMode(d.id, "f") }
                    }
                    T(
                        if (d.cm == "f") "A rounded picture above the title, like Google Keep." else "The picture fades into the note, edge to edge.",
                        13f, 400, pal.osv, Modifier.padding(top = 12.dp), lh = 1.4f
                    )
                }
            }
            CoverEditor(vm, d)
            SGroup("Manage", 80) {
                MRow(POS_TOP, "swap", "Change photo", "Pick another image from your gallery") { act.pickCover(id) }
                MRow(POS_BOT, "del", "Remove cover", "The image is removed from this note", danger = true) {
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
    val fr = d.cm == "f"
    val c2 = vm.pr.cols.coerceIn(1, 2) == 2
    val a = if (frame == 0) (if (fr) frameAspect(c2) else coverAspect(c2)) else (if (fr) FRAME_EDIT_RATIO else EDIT_RATIO)
    val pr by animateDpAsState(
        if (!fr) 12.dp else (if (frame == 0) FRAME_CARD_R else FRAME_EDIT_R).dp, M3E.spatial(), label = "pvr"
    )

    // simpan dengan jeda saat digeser, dan sekali lagi saat halaman ditutup
    LaunchedEffect(x, y, z) {
        delay(300)
        vm.setCoverCrop(d.id, d.cv, x, y, z)
    }
    DisposableEffect(Unit) {
        onDispose { vm.setCoverCrop(d.id, d.cv, cur[0], cur[1], cur[2]) }
    }

    SGroup("Adjust position", 40) {
        SCard(POS_ONE) {
            SegPill(2, frame, { frame = it }) { i, fg -> T(if (i == 0) "Home" else "Note", 15f, 500, fg) }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth(if (a < 1f) .62f else 1f).aspectRatio(a)
                        .clip(RoundedCornerShape(pr.nn())).background(pal.c3)
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
                "Drag to move, pinch to zoom. The same position is used on the home screen and in the note.",
                13f, 400, pal.osv, Modifier.padding(top = 12.dp), lh = 1.4f
            )
        }
    }
}

/**
 * Warna dasar palet catatan dari foto sampul: foto diperkecil, tiap piksel yang cukup berwarna menyumbang suara ke
 * salah satu dari 24 juring hue (bobot lebih besar untuk yang jenuh dan terang), juring terkuat (digabung dengan tetangganya)
 * menang. Foto nyaris abu-abu menghasilkan palet abu-abu. Hasil: "#RRGGBB" (hanya hue dan saturasinya yang dipakai buildPal).
 */
fun coverSeed(src: android.graphics.Bitmap): String {
    val w = 48
    val h = (src.height * w / src.width.coerceAtLeast(1)).coerceIn(1, 96)
    val bm = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    bm.getPixels(px, 0, w, 0, 0, w, h)
    val bins = 24
    val sc = FloatArray(bins); val hx = FloatArray(bins); val hy = FloatArray(bins); val ss = FloatArray(bins)
    val hsv = FloatArray(3)
    var r = 0L; var g = 0L; var b = 0L
    for (p in px) {
        r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p)
        android.graphics.Color.colorToHSV(p, hsv)
        if (hsv[2] < .18f || hsv[1] < .14f) continue
        val wt = hsv[1] * hsv[1] * (.4f + hsv[2])
        val k = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
        val rad = Math.toRadians(hsv[0].toDouble())
        sc[k] += wt; hx[k] += (cos(rad) * wt).toFloat(); hy[k] += (sin(rad) * wt).toFloat(); ss[k] += hsv[1] * wt
    }
    fun at(k: Int) = (k + bins) % bins
    var best = -1; var bestV = 0f
    for (k in 0 until bins) {
        val v = sc[k] + .5f * (sc[at(k - 1)] + sc[at(k + 1)])
        if (v > bestV) { bestV = v; best = k }
    }
    if (best < 0 || bestV < px.size * .01f) {
        val n = px.size.toLong().coerceAtLeast(1)
        return String.format("#%02X%02X%02X", (r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
    var x = 0f; var y = 0f; var s = 0f; var wsum = 0f
    for (d in -1..1) { val k = at(best + d); x += hx[k]; y += hy[k]; s += ss[k]; wsum += sc[k] }
    val hue = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 360.0) % 360.0).toFloat()
    val sat = (s / wsum.coerceAtLeast(1e-4f)).coerceIn(.2f, 1f)
    val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, .8f))
    return String.format("#%06X", c and 0xFFFFFF)
}


/**
 * Pratinjau gaya sampul (Material 3 Expressive): ubin kecil berisi catatan mini. Terpilih = sudut membulat besar dan berwarna wadah utama;
 * ditekan = lebih kotak; berpindah = pegas memantul.
 */
@Composable
private fun CoverStyleTile(modifier: Modifier, label: String, frame: Boolean, on: Boolean, vm: AppVM, d: Note, onClick: () -> Unit) {
    val pal = LocalPal.current
    val bmp = rememberImg(vm, d.cv)
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 12.dp else if (on) 30.dp else 20.dp, M3E.spatialFast(), label = "tr")
    val k by animateFloatAsState(if (on) 1f else 0f, M3E.spatial(), label = "tk")
    val sc by animateFloatAsState(if (pr) .96f else 1f, M3E.spatialFast(), label = "ts")
    val fg = if (on) pal.opc else pal.os
    Column(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(r.nn()))
            .background(androidx.compose.ui.graphics.lerp(pal.c3, pal.pc, k))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(.95f).clip(RoundedCornerShape(14.dp)).background(pal.s)) {
            if (frame) {
                Column(Modifier.fillMaxSize().padding(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(8.dp)).background(pal.c3)) {
                        if (bmp != null) drawCover(bmp, d.cz, d.cx, d.cy)
                    }
                    Box(Modifier.fillMaxWidth(.7f).height(7.dp).clip(RoundedCornerShape(50)).background(pal.os.copy(alpha = .55f)))
                    Box(Modifier.fillMaxWidth(.9f).height(5.dp).clip(RoundedCornerShape(50)).background(pal.os.copy(alpha = .22f)))
                    Box(Modifier.fillMaxWidth(.5f).height(5.dp).clip(RoundedCornerShape(50)).background(pal.os.copy(alpha = .22f)))
                }
            } else {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1.25f).background(pal.c3)) {
                    if (bmp != null) drawCover(bmp, d.cz, d.cx, d.cy)
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to pal.s))
                }
                Column(Modifier.fillMaxSize().padding(start = 7.dp, end = 7.dp, bottom = 7.dp), verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.fillMaxWidth(.7f).height(7.dp).clip(RoundedCornerShape(50)).background(pal.os.copy(alpha = .55f)))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(.9f).height(5.dp).clip(RoundedCornerShape(50)).background(pal.os.copy(alpha = .22f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, 15f, 600, fg, Modifier.weight(1f))
            Box(Modifier.size(20.dp).graphicsLayer { scaleX = k; scaleY = k }) { Ic("ok", fg, 20.dp) }
        }
    }
}

package app.dinduy

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pembuka PDF hasil ekspor untuk pratinjau. PdfRenderer hanya boleh membuka satu halaman sekali jalan, jadi semua dikunci. */
private class PdfView(file: File) {
    private var pfd: ParcelFileDescriptor? = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private var rd: PdfRenderer? = try {
        PdfRenderer(pfd!!)
    } catch (e: Throwable) {
        try { pfd?.close() } catch (x: Throwable) { }
        throw e
    }

    @Synchronized
    fun render(i: Int, wPx: Int): Bitmap? {
        val r = rd ?: return null
        if (i < 0 || i >= r.pageCount) return null
        val page = r.openPage(i)
        try {
            val hPx = (wPx * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(-1)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    @Synchronized
    fun close() {
        try { rd?.close() } catch (e: Throwable) { }
        try { pfd?.close() } catch (e: Throwable) { }
        rd = null
        pfd = null
    }
}

private fun fmtPt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()

private fun fmtSize(b: Long): String =
    if (b >= 1_048_576L) String.format(java.util.Locale.US, "%.1f MB", b / 1048576f) else "${max(1L, (b + 512L) / 1024L)} KB"

// ------------------------------------------------------------------ komponen

/**
 * Penanda memuat M3 Expressive: kue bergelombang yang berputar dan menarik-napas (bulat <-> bergelombang). Tidak ada gerak pudar,
 * hanya bentuk yang berubah.
 */
@Composable
private fun PdfLoader(dia: Dp, color: Color) {
    val tr = rememberInfiniteTransition(label = "pdfld")
    val rot by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "pdfr")
    val amp by tr.animateFloat(.04f, .17f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pdfa")
    Canvas(Modifier.size(dia)) {
        val rad = this.size.minDimension / 2f
        val mx = this.size.width / 2f
        val my = this.size.height / 2f
        val path = Path()
        val n = 90
        for (i in 0..n) {
            val th = (i.toFloat() / n) * 2f * PI.toFloat()
            val r = rad * (1f - amp) * (1f + amp * cos(7f * th))
            val x = mx + r * cos(th)
            val y = my + r * kotlin.math.sin(th)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        rotate(rot, Offset(mx, my)) { drawPath(path, color) }
    }
}

/** Tombol bulat 52dp untuk kurang / tambah ukuran huruf: ditekan = sudut membulat penuh + menyempit membal. */
@Composable
private fun StepBtn(plus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr && enabled) 26.dp else 17.dp, spring(.5f, 700f), label = "sbr")
    val sc by animateFloatAsState(if (pr && enabled) .86f else 1f, spring(.42f, 620f), label = "sbs")
    val fg = if (enabled) pal.os else pal.osv.copy(alpha = .45f)
    Box(
        Modifier.size(52.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(r)).background(pal.c4)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val sw = 2.4.dp.toPx()
            val m = this.size.width / 2f
            val e = 3.dp.toPx()
            drawLine(fg, Offset(e, m), Offset(this.size.width - e, m), sw, StrokeCap.Round)
            if (plus) drawLine(fg, Offset(m, e), Offset(m, this.size.height - e), sw, StrokeCap.Round)
        }
    }
}

/**
 * Slider M3 Expressive buatan sendiri: trek tebal berujung bulat dengan celah di sekitar pegangan, pegangan berupa kapsul tegak yang
 * menyempit saat disentuh, trek menebal saat digeser. Area sentuh 60dp, sentuh di mana pun pada trek langsung melompat ke sana.
 */
@Composable
private fun PdfSlider(value: Float, lo: Float, hi: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    val pal = LocalPal.current
    var drag by remember { mutableStateOf(false) }
    val trackH by animateDpAsState(if (drag) 34.dp else 28.dp, spring(.55f, 520f), label = "pst")
    val handleW by animateDpAsState(if (drag) 3.dp else 6.dp, spring(.5f, 600f), label = "psw")
    val handleH by animateDpAsState(if (drag) 54.dp else 46.dp, spring(.5f, 600f), label = "psh")
    val cb by rememberUpdatedState(onChange)
    val frac = if (hi > lo) ((value - lo) / (hi - lo)).coerceIn(0f, 1f) else 0f
    val colA = pal.p
    val colI = pal.pc
    Canvas(
        modifier.height(60.dp).pointerInput(lo, hi) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pad = 10.dp.toPx()
                fun upd(x: Float) {
                    val f = ((x - pad) / (size.width - 2f * pad)).coerceIn(0f, 1f)
                    cb(((lo + f * (hi - lo)) * 2f).roundToInt() / 2f)
                }
                drag = true
                try {
                    upd(down.position.x)
                    do {
                        val ev = awaitPointerEvent()
                        for (ch in ev.changes) {
                            if (ch.pressed) {
                                upd(ch.position.x)
                                ch.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                } finally {
                    drag = false
                }
            }
        }
    ) {
        val w = this.size.width
        val cy = this.size.height / 2f
        val th = trackH.toPx()
        val hw = handleW.toPx()
        val hh = handleH.toPx()
        val pad = 10.dp.toPx()
        val gap = 6.dp.toPx()
        val hx = pad + frac * (w - 2f * pad)
        val top = cy - th / 2f
        val outer = th / 2f
        val inner = 5.dp.toPx()
        fun pill(l: Float, r: Float, lc: Float, rc: Float): Path {
            val cap = min((r - l) / 2f, th / 2f)
            val a = CornerRadius(min(lc, cap))
            val b = CornerRadius(min(rc, cap))
            return Path().apply { addRoundRect(RoundRect(l, top, r, top + th, a, b, b, a)) }
        }
        val aEnd = hx - hw / 2f - gap
        if (aEnd > 1f) drawPath(pill(0f, aEnd, outer, inner), colA)
        val iStart = hx + hw / 2f + gap
        if (w - iStart > 1f) {
            drawPath(pill(iStart, w, inner, outer), colI)
            if (w - iStart > outer * 2f) drawCircle(colA, 2.dp.toPx(), Offset(w - outer, cy))
        }
        drawRoundRect(colA, Offset(hx - hw / 2f, cy - hh / 2f), Size(hw, hh), CornerRadius(hw / 2f))
    }
}

/**
 * Tombol praset dalam grup tersambung M3 Expressive: yang terpilih bulat penuh, yang lain bersudut kecil di sambungan.
 * Saat pilihan pindah, sudut tiap tombol berubah bentuk dengan pegas.
 */
@Composable
private fun PresetBtn(text: String, sel: Boolean, first: Boolean, last: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val full = 24.dp
    val mid = if (sel || pr) 24.dp else 9.dp
    val sC by animateDpAsState(if (first || sel || pr) full else mid, spring(.6f, 520f), label = "pbs")
    val eC by animateDpAsState(if (last || sel || pr) full else mid, spring(.6f, 520f), label = "pbe")
    val sc by animateFloatAsState(if (pr) .93f else 1f, spring(.45f, 650f), label = "pbx")
    Box(
        modifier.height(48.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(topStart = sC, bottomStart = sC, topEnd = eC, bottomEnd = eC))
            .background(if (sel) pal.p else pal.c4)
            .clickable(interactionSource = src, indication = LocalIndication.current) {
                if (!sel) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        Alignment.Center
    ) { T(text, 14f, if (sel) 800 else 600, if (sel) pal.op else pal.os, maxLines = 1) }
}

/** Tombol aksi 60dp (Simpan / Bagikan), anggota grup tersambung: ditekan = semua sudut bulat penuh + menyempit membal. */
@Composable
private fun PdfBtn(icon: String, label: String, primary: Boolean, enabled: Boolean, first: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val down = pr && enabled
    val outer = 30.dp
    val inner = if (down) 30.dp else 10.dp
    val sC by animateDpAsState(if (first) outer else inner, spring(.55f, 560f), label = "pgs")
    val eC by animateDpAsState(if (first) inner else outer, spring(.55f, 560f), label = "pge")
    val sc by animateFloatAsState(if (down) .94f else 1f, spring(.42f, 620f), label = "pgx")
    val bg = if (!enabled) pal.osv.copy(alpha = .16f) else if (primary) pal.p else pal.pc
    val fg = if (!enabled) pal.osv else if (primary) pal.op else pal.opc
    Row(
        modifier.height(60.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(topStart = sC, bottomStart = sC, topEnd = eC, bottomEnd = eC)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Ic(icon, fg, 22.dp)
        Spacer(Modifier.width(10.dp))
        T(label, 16f, 700, fg, maxLines = 1)
    }
}

@Composable
private fun PdfPageItem(view: PdfView, index: Int, wDp: Float, wPx: Int) {
    val pal = LocalPal.current
    val bmp by produceState<ImageBitmap?>(null, view, index, wPx) {
        value = withContext(Dispatchers.Default) {
            try { view.render(index, wPx)?.asImageBitmap() } catch (e: Throwable) { null }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.width(wDp.dp).aspectRatio(PdfExport.PW.toFloat() / PdfExport.PH)
            .shadow(if (pal.dark) 2.dp else 4.dp, shape).clip(shape).background(Color.White),
        Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else PdfLoader(36.dp, pal.p)
    }
}

// ------------------------------------------------------------------ halaman

/** Halaman "Export PDF" dari menu titik tiga: pratinjau PDF sungguhan, atur ukuran huruf, lalu simpan atau bagikan. */
@Composable
fun PdfExportPage(vm: AppVM, nav: Nav, act: Actions, id: String) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    val sat = satDp()
    val sab = sabDp()
    val note = vm.note(id)
    val pr = vm.pr.copy(fs = vm.noteFs(id))
    val imgDir = vm.imgDir
    val ed = vm.ed?.takeIf { it.noteId == id }
    val widthDp = remember(id) { ed?.widthDp ?: 312f }
    val maxSide = remember(id) { ed?.maxSideW() ?: .6f }
    val hasInk = remember(note) { note?.bl?.any { it.ik.isNotEmpty() } == true }

    // Catatan beroret: ukuran huruf awal = persis seperti di layar (coretan pas). Tanpa coretan: ukuran dokumen biasa (12 pt).
    val faithful = pr.fs * (PdfExport.CW / widthDp)
    val lo = 7f
    val hi = max(40f, faithful * 1.3f)
    val hiQ = floor(hi * 2f) / 2f
    val def = ((if (hasInk) faithful else 12f).coerceIn(lo, hiQ) * 2f).roundToInt() / 2f
    var pt by remember(id) { mutableFloatStateOf(def) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(true) }

    var view by remember { mutableStateOf<PdfView?>(null) }
    var info by remember { mutableStateOf<PdfExport.Result?>(null) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val viewRef by rememberUpdatedState(view)

    LaunchedEffect(id, pt, attempt) {
        val cur = pt
        busy = true
        if (view != null) delay(260)   // tunggu geseran berhenti dulu
        val res = withContext(Dispatchers.Default) {
            try {
                if (note == null) null else PdfExport.build(ctx, note, pr, imgDir, cur, widthDp, maxSide)
            } catch (e: Throwable) {
                null
            }
        }
        val pv = if (res != null) try { PdfView(res.file) } catch (e: Throwable) { null } else null
        if (res == null || pv == null) {
            failed = true
        } else {
            failed = false
            val oldV = view
            val oldF = info
            info = res
            view = pv
            oldV?.close()
            try { oldF?.file?.delete() } catch (e: Throwable) { }
        }
        busy = false
    }

    DisposableEffect(Unit) {
        onDispose {
            viewRef?.close()
            try { File(ctx.cacheDir, "pdfx").listFiles()?.forEach { it.delete() } } catch (e: Throwable) { }
        }
    }

    fun setPt(v: Float) {
        val n = v.coerceIn(lo, hiQ)
        if (n != pt) {
            if (n.toInt() != pt.toInt()) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pt = n
        }
    }

    val presets = remember(def, hasInk) {
        val base = listOf(10f, 12f, 14f)
        val l = ArrayList<Pair<String, Float>>()
        if (hasInk && def !in base) l.add("Screen" to def)
        for (b in base) l.add((if (hasInk && b == def) "Screen" else fmtPt(b) + " pt") to b)
        l
    }

    val wDp = minOf(LocalConfiguration.current.screenWidthDp - 32, 640).toFloat()
    val wPx = (wDp * dens).roundToInt().coerceIn(300, 1400)
    val ready = info != null && !busy && !failed
    val name = note?.let { notePdfName(it) } ?: "catatan.pdf"
    val listState = rememberLazyListState()
    val curPage by remember {
        derivedStateOf {
            val li = listState.layoutInfo
            val mid = (li.viewportStartOffset + li.viewportEndOffset) / 2
            li.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: 0
        }
    }

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize()) {
            // ---- bilah atas
            Row(
                Modifier.fillMaxWidth().height(sat + 68.dp).padding(top = sat, start = 8.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn("back", pal.os, { nav.pop() })
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    T("Export PDF", 22f, 400, pal.os, maxLines = 1)
                    T(name, 13f, 400, pal.osv, maxLines = 1)
                }
            }

            // ---- pratinjau
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val v = view
                val inf = info
                if (v != null && inf != null) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(inf.pages) { i -> PdfPageItem(v, i, wDp, wPx) }
                    }
                } else if (failed) {
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(34.dp)).background(pal.ec), Alignment.Center) {
                            T("!", 44f, 800, pal.oec)
                        }
                        T("Couldn't create the PDF", 18f, 600, pal.os, Modifier.padding(top = 20.dp))
                        T(
                            "Try a smaller text size or a shorter note.", 14f, 400, pal.osv,
                            Modifier.padding(top = 6.dp, bottom = 22.dp), lh = 1.4f
                        )
                        Chip("Try again", pal.p, pal.op, CircleShape, h = 52.dp, minW = 150.dp) { attempt++ }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Box(Modifier.size(104.dp).clip(CircleShape).background(pal.pc), Alignment.Center) {
                            PdfLoader(60.dp, pal.p)
                        }
                        T("Preparing your PDF", 18f, 600, pal.os, Modifier.padding(top = 20.dp))
                        T("Laying out text, images and tables", 14f, 400, pal.osv, Modifier.padding(top = 6.dp))
                    }
                }

                // kapsul mengambang: nomor halaman + penanda "sedang menyusun ulang"
                val pages = info?.pages ?: 0
                val pillOn = view != null && (busy || pages > 1)
                val pv by animateFloatAsState(if (pillOn) 1f else 0f, spring(.6f, 520f), label = "pill")
                if (pv > .01f) {
                    val cap = RoundedCornerShape(30.dp)
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                            .graphicsLayer {
                                val sc = .6f + .4f * pv
                                scaleX = sc
                                scaleY = sc
                                translationY = (1f - pv) * 80.dp.toPx()
                            }
                            .shadow(if (pal.dark) 8.dp else 5.dp, cap).clip(cap).background(pal.c3)
                            .border(1.dp, pal.ol.copy(alpha = .22f), cap)
                            .animateContentSize(spring(.8f, 420f))
                            .height(44.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (busy) {
                            PdfLoader(22.dp, pal.p)
                            Spacer(Modifier.width(10.dp))
                            T("Updating", 14f, 600, pal.os, maxLines = 1)
                        } else {
                            T("${(curPage + 1).coerceAtMost(max(pages, 1))} / $pages", 14f, 700, pal.os, maxLines = 1)
                        }
                    }
                }
            }

            // ---- panel kontrol (di zona jempol)
            val panelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            Column(
                Modifier.fillMaxWidth()
                    .shadow(if (pal.dark) 12.dp else 8.dp, panelShape)
                    .clip(panelShape).background(pal.c2)
                    .animateContentSize(spring(.8f, 420f))
            ) {
                val hw by animateDpAsState(if (open) 36.dp else 56.dp, spring(.55f, 500f), label = "hdl")
                Box(
                    Modifier.fillMaxWidth().height(36.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        open = !open
                    },
                    Alignment.Center
                ) {
                    Box(Modifier.size(hw, 5.dp).clip(CircleShape).background(pal.osv.copy(alpha = .5f)))
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp + sab)) {
                    // judul + nilai
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(pal.pc), Alignment.Center) { Ic("txt", pal.opc, 22.dp) }
                        T("Text size", 16f, 600, pal.os, Modifier.padding(start = 12.dp).weight(1f))
                        this@Row.AnimatedVisibility(
                            visible = pt != def,
                            enter = scaleIn(spring(.5f, 520f), .5f) + expandHorizontally(spring(.7f, 480f)),
                            exit = scaleOut(spring(1f, 700f), .5f) + shrinkHorizontally(spring(1f, 700f))
                        ) {
                            Row {
                                Chip("Reset", pal.c4, pal.os, CircleShape, h = 40.dp) { pt = def }
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        Box(
                            Modifier.animateContentSize(spring(.8f, 500f)).height(40.dp).clip(CircleShape).background(pal.p)
                                .padding(horizontal = 16.dp),
                            Alignment.Center
                        ) { T(fmtPt(pt) + " pt", 16f, 700, pal.op, maxLines = 1) }
                    }

                    if (open) {
                        // praset satu ketukan
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            presets.forEachIndexed { i, p ->
                                PresetBtn(p.first, pt == p.second, i == 0, i == presets.lastIndex, Modifier.weight(1f)) { setPt(p.second) }
                            }
                        }
                        // atur halus: kurang / slider / tambah
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StepBtn(false, pt > lo) { setPt(pt - 1f) }
                            PdfSlider(pt, lo, hiQ, Modifier.weight(1f).padding(horizontal = 4.dp)) { setPt(it) }
                            StepBtn(true, pt < hiQ) { setPt(pt + 1f) }
                        }
                        // ringkasan hasil
                        val sizeTxt = remember(info) { info?.let { fmtSize(it.file.length()) } }
                        val summary = when {
                            failed -> "Couldn't create the PDF at this size. Try another size."
                            info != null && sizeTxt != null -> cnt(info!!.pages, "page") + " · A4 · " + sizeTxt
                            else -> "Preparing…"
                        }
                        T(summary, 13f, 500, pal.osv, Modifier.padding(top = 2.dp), lh = 1.3f)
                        if (hasInk) {
                            T("Drawings keep their size. Only the text changes.", 13f, 400, pal.osv, Modifier.padding(top = 2.dp), lh = 1.3f)
                        }
                    }

                    // aksi utama
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PdfBtn("dl", "Save PDF", false, ready, true, Modifier.weight(1f)) { info?.let { act.savePdf(name, it.file) } }
                        PdfBtn("up", "Share PDF", true, ready, false, Modifier.weight(1f)) { info?.let { act.sharePdf(name, it.file) } }
                    }
                }
            }
        }
    }
}

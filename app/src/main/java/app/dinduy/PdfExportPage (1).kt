package app.dinduy

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
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

@Composable
private fun PdfBtn(icon: String, label: String, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    val bg = if (!enabled) pal.osv.copy(alpha = .16f) else if (primary) pal.p else pal.pc
    val fg = if (!enabled) pal.osv else if (primary) pal.op else pal.opc
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(bg).clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Ic(icon, fg, 22.dp)
        Spacer(Modifier.width(10.dp))
        T(label, 16f, 600, fg, maxLines = 1)
    }
}

@Composable
private fun PdfPageItem(view: PdfView, index: Int, total: Int, wDp: Float, wPx: Int) {
    val pal = LocalPal.current
    val bmp by produceState<ImageBitmap?>(null, view, index, wPx) {
        value = withContext(Dispatchers.Default) {
            try { view.render(index, wPx)?.asImageBitmap() } catch (e: Throwable) { null }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(wDp.dp).aspectRatio(PdfExport.PW.toFloat() / PdfExport.PH)
                .shadow(4.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).background(Color.White)
        ) {
            val b = bmp
            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        T("${index + 1} / $total", 12f, 500, pal.osv, Modifier.padding(top = 6.dp))
    }
}

private fun fmtPt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()

/** Halaman "Ekspor PDF" dari menu titik tiga: pratinjau PDF sungguhan, geser ukuran huruf, lalu simpan atau bagikan. */
@Composable
fun PdfExportPage(vm: AppVM, nav: Nav, act: Actions, id: String) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val sat = satDp()
    val sab = sabDp()
    val note = vm.note(id)
    val pr = vm.pr
    val imgDir = vm.imgDir
    val ed = vm.ed?.takeIf { it.noteId == id }
    val widthDp = remember(id) { ed?.widthDp ?: 312f }
    val maxSide = remember(id) { ed?.maxSideW() ?: .6f }
    val hasInk = remember(note) { note?.bl?.any { it.ik.isNotEmpty() } == true }

    // Catatan beroret: ukuran huruf awal = persis seperti di layar (coretan pas). Tanpa coretan: ukuran dokumen biasa (12 pt).
    val faithful = pr.fs * (PdfExport.CW / widthDp)
    val lo = 7f
    val hi = max(40f, faithful * 1.3f)
    val def = ((if (hasInk) faithful else 12f).coerceIn(lo, hi) * 2f).roundToInt() / 2f
    var pt by remember(id) { mutableFloatStateOf(def) }

    var view by remember { mutableStateOf<PdfView?>(null) }
    var info by remember { mutableStateOf<PdfExport.Result?>(null) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val viewRef by rememberUpdatedState(view)

    LaunchedEffect(id, pt) {
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

    val wDp = minOf(LocalConfiguration.current.screenWidthDp - 32, 640).toFloat()
    val wPx = (wDp * dens).roundToInt().coerceIn(300, 1400)
    val ready = info != null && !busy && !failed
    val name = note?.let { notePdfName(it) } ?: "catatan.pdf"

    Box(Modifier.fillMaxSize().background(pal.s)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(sat + 64.dp).padding(top = sat, start = 8.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn("back", pal.os, { nav.pop() })
                T("Ekspor PDF", 22f, 400, pal.os, Modifier.padding(start = 8.dp).weight(1f), maxLines = 1)
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = pal.p, strokeWidth = 2.5.dp)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val v = view
                val inf = info
                if (v != null && inf != null) {
                    LazyColumn(
                        Modifier.fillMaxSize().alpha(if (busy) .5f else 1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(inf.pages) { i -> PdfPageItem(v, i, inf.pages, wDp, wPx) }
                    }
                } else if (failed) {
                    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                        T("PDF gagal dibuat. Coba kecilkan ukuran huruf atau kurangi isi catatan.", 15f, 400, pal.osv, lh = 1.4f)
                    }
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = pal.p)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(pal.c2)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + sab)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic("txt", pal.p, 22.dp)
                    T("Ukuran huruf", 16f, 500, pal.os, Modifier.padding(start = 12.dp).weight(1f))
                    T(fmtPt(pt) + " pt", 16f, 600, pal.p)
                }
                Slider(
                    value = pt,
                    onValueChange = { pt = (it * 2f).roundToInt() / 2f },
                    valueRange = lo..hi,
                    colors = SliderDefaults.colors(thumbColor = pal.p, activeTrackColor = pal.p, inactiveTrackColor = pal.pc)
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    val pages = info?.pages
                    T(
                        (if (pages != null) "$pages halaman · A4. " else "") + "Coretan tetap, hanya teks yang berubah ukuran.",
                        13f, 400, pal.osv, Modifier.weight(1f), lh = 1.3f
                    )
                    if (pt != def) {
                        T(
                            "Setel ulang", 14f, 600, pal.p,
                            Modifier.padding(start = 12.dp).clip(RoundedCornerShape(12.dp)).clickable { pt = def }.padding(8.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PdfBtn("dl", "Simpan PDF", false, ready, Modifier.weight(1f)) { info?.let { act.savePdf(name, it.file) } }
                    PdfBtn("up", "Bagikan PDF", true, ready, Modifier.weight(1f)) { info?.let { act.sharePdf(name, it.file) } }
                }
            }
        }
    }
}

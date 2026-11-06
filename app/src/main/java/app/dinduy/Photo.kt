package app.dinduy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Permintaan buka menu foto: id gambar + posisi bingkainya di layar (px) sebagai titik awal morph. */
class PhotoReq(val imgId: String, val rect: Rect)

object PhotoHost {
    var req by mutableStateOf<PhotoReq?>(null)
}

/** Satu kata hasil OCR: kotak dalam piksel gambar, nomor baris dan blok (untuk menyusun teks salinan). */
class OWord(val t: String, val l: Float, val tp: Float, val r: Float, val b: Float, val line: Int, val block: Int)

private fun decodeFull(vm: AppVM, id: String): Bitmap? {
    val f = java.io.File(vm.imgDir, "$id.jpg")
    if (!f.exists()) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (max(o.outWidth, o.outHeight) / s > 2800) s *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
}

private suspend fun runOcr(bmp: Bitmap): List<OWord>? = suspendCancellableCoroutine { c ->
    try {
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        rec.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t ->
                val out = ArrayList<OWord>()
                var ln = 0
                t.textBlocks.forEachIndexed { bi, blk ->
                    blk.lines.forEach { line ->
                        line.elements.forEach { el ->
                            val bb = el.boundingBox
                            if (bb != null) out.add(OWord(el.text, bb.left.toFloat(), bb.top.toFloat(), bb.right.toFloat(), bb.bottom.toFloat(), ln, bi))
                        }
                        ln++
                    }
                }
                rec.close()
                if (c.isActive) c.resume(out)
            }
            .addOnFailureListener { rec.close(); if (c.isActive) c.resume(null) }
    } catch (e: Throwable) {
        if (c.isActive) c.resume(null)
    }
}

private fun joinWords(w: List<OWord>): String {
    val sb = StringBuilder()
    var prev: OWord? = null
    for (x in w) {
        val p = prev
        if (p != null) sb.append(if (p.block != x.block) "\n\n" else if (p.line != x.line) "\n" else " ")
        sb.append(x.t)
        prev = x
    }
    return sb.toString()
}

private fun copyOut(ctx: Context, s: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Dinduy", s))
}

/** Lapisan menu foto: morph dari bingkai gambar ke layar penuh dengan spring yang sama seperti buka/tutup catatan. */
@Composable
fun PhotoLayer(vm: AppVM) {
    val req = PhotoHost.req
    var shown by remember { mutableStateOf<PhotoReq?>(null) }
    val p = remember { Animatable(0f) }
    if (req != null && shown !== req) shown = req
    LaunchedEffect(req) {
        if (req != null) p.animateTo(1f, spring(.8f, 620f))
        else if (shown != null) { p.animateTo(0f, spring(.95f, 760f)); shown = null }
    }
    val r = shown ?: return
    BackHandler(enabled = req != null) { PhotoHost.req = null }
    val rad = with(LocalDensity.current) { 8.dp.toPx() }
    val start = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { }.drawWithContent {
            val tt = p.value.coerceIn(0f, 1f)
            val l = lerp(r.rect.left, 0f, tt)
            val t = lerp(r.rect.top, 0f, tt)
            val rt = lerp(r.rect.right, size.width, tt)
            val b = lerp(r.rect.bottom, size.height, tt)
            val cr = CornerRadius(lerp(rad, 0f, tt))
            val path = Path().apply { addRoundRect(RoundRect(Rect(l, t, rt, b), cr, cr, cr, cr)) }
            drawRect(Color.Black.copy(alpha = .5f * tt))
            clipPath(path) {
                drawRect(androidx.compose.ui.graphics.lerp(start, Color.Black, tt))
                this@drawWithContent.drawContent()
            }
        }
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val tt = p.value.coerceIn(0f, 1f)
                val l = lerp(r.rect.left, 0f, tt)
                val t = lerp(r.rect.top, 0f, tt)
                val rt = lerp(r.rect.right, size.width, tt)
                val sc = ((rt - l) / size.width).coerceIn(.05f, 1f)
                scaleX = sc; scaleY = sc
                translationX = l; translationY = t
                transformOrigin = TransformOrigin(0f, 0f)
                val x = ((tt - .08f) / .4f).coerceIn(0f, 1f)
                alpha = x * x * (3f - 2f * x)
            }
        ) { PhotoScreen(vm, r.imgId) { PhotoHost.req = null } }
    }
}

@Composable
private fun PhotoScreen(vm: AppVM, id: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    val dens = LocalDensity.current.density
    var bmp by remember(id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(id) { bmp = withContext(Dispatchers.IO) { decodeFull(vm, id) } }
    var scan by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf<List<OWord>>(emptyList()) }
    var sa by remember { mutableIntStateOf(-1) }
    var sb by remember { mutableIntStateOf(-1) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val white = Color.White

    fun start() {
        val b = bmp ?: return
        scan = true; busy = true; sa = -1; sb = -1; zoom = 1f; pan = Offset.Zero
        scope.launch {
            val w = withContext(Dispatchers.Default) { runOcr(b) }
            busy = false
            if (w == null) { vm.show("Couldn't read this photo"); scan = false }
            else if (w.isEmpty()) { vm.show("No text found"); scan = false }
            else words = w
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            var box by remember { mutableStateOf(IntSize.Zero) }
            // peta gambar -> layar (muat di layar, lalu zoom / geser)
            val fit = if (box.width > 0) min(box.width / b.width.toFloat(), box.height / b.height.toFloat()) else 1f
            val s0 = fit * zoom
            val ox = (box.width - b.width * s0) / 2f + pan.x
            val oy = (box.height - b.height * s0) / 2f + pan.y
            fun hit(pt: Offset, pad: Float): Int {
                var best = -1
                var bd = Float.MAX_VALUE
                words.forEachIndexed { i, w ->
                    val l = ox + w.l * s0 - pad; val t = oy + w.tp * s0 - pad; val r = ox + w.r * s0 + pad; val bt = oy + w.b * s0 + pad
                    if (pt.x in l..r && pt.y in t..bt) {
                        val d = (pt.x - (l + r) / 2f) * (pt.x - (l + r) / 2f) + (pt.y - (t + bt) / 2f) * (pt.y - (t + bt) / 2f)
                        if (d < bd) { bd = d; best = i }
                    }
                }
                return best
            }
            Canvas(
                Modifier.fillMaxSize()
                    .androidx_onSize { box = it }
                    .pointerInput(scan) {
                        if (scan) {
                            detectTapGestures(onTap = { pt -> val i = hit(pt, 10f * dens); if (i >= 0) { sa = i; sb = i } else { sa = -1; sb = -1 } })
                        } else detectTapGestures(onDoubleTap = { if (zoom > 1.2f) { zoom = 1f; pan = Offset.Zero } else zoom = 2.5f })
                    }
                    .pointerInput(scan) {
                        if (scan) {
                            var anchor = -1
                            detectDragGestures(
                                onDragStart = { pt -> anchor = hit(pt, 14f * dens); if (anchor >= 0) { sa = anchor; sb = anchor } },
                                onDrag = { c, _ ->
                                    c.consume()
                                    val i = hit(c.position, 14f * dens)
                                    if (anchor >= 0 && i >= 0) sb = i
                                }
                            )
                        } else detectTransformGestures { _, pn, z, _ ->
                            zoom = (zoom * z).coerceIn(1f, 5f)
                            pan = if (zoom <= 1.01f) Offset.Zero else pan + pn
                        }
                    }
            ) {
                drawImage(
                    img, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                    dstSize = IntSize((b.width * s0).roundToInt(), (b.height * s0).roundToInt()), filterQuality = FilterQuality.Medium
                )
                if (scan && words.isNotEmpty()) {
                    val lo = min(sa, sb); val hi = max(sa, sb)
                    words.forEachIndexed { i, w ->
                        val tl = Offset(ox + w.l * s0, oy + w.tp * s0)
                        val sz = Size((w.r - w.l) * s0, (w.b - w.tp) * s0)
                        if (sa >= 0 && i in lo..hi) drawRect(pal.p.copy(alpha = .45f), tl, sz)
                        else drawRect(white.copy(alpha = .55f), tl, sz, style = Stroke(1.2f * dens))
                    }
                }
            }
        }
        // bilah atas
        Row(
            Modifier.fillMaxWidth().padding(top = satDp()).height(60.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("back", white, { if (scan) { scan = false; words = emptyList(); sa = -1; sb = -1 } else onClose() }, bg = Color.Black.copy(alpha = .35f))
            Box(Modifier.weight(1f))
            if (!scan && bmp != null) IconBtn("x", white, onClose, bg = Color.Black.copy(alpha = .35f))
        }
        // bilah bawah
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = sabDp() + 20.dp)) {
            if (!scan) {
                Row(
                    Modifier.clip(CircleShape).background(Color(0xFF2B2B2E)).clickable(enabled = bmp != null) { start() }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) { PIc("scan", white, 22.dp); T("Scan text", 16f, 700, white) }
            } else if (busy) {
                Row(
                    Modifier.clip(CircleShape).background(Color(0xFF2B2B2E)).padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) { LoadingBlob(Modifier.size(32.dp)); T("Reading text…", 15f, 600, white) }
            } else {
                Row(
                    Modifier.clip(CircleShape).background(Color(0xFF2B2B2E)).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    @Composable
                    fun Chip(label: String, on: Boolean, f: () -> Unit) {
                        Box(
                            Modifier.clip(CircleShape).background(if (on) pal.p else Color.Transparent).clickable(enabled = on, onClick = f)
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                        ) { T(label, 15f, 700, if (on) pal.op else white.copy(alpha = .4f)) }
                    }
                    val has = sa >= 0
                    Chip("Select all", true) { sa = 0; sb = words.lastIndex }
                    Chip("Copy", has) {
                        copyOut(ctx, joinWords(words.subList(min(sa, sb), max(sa, sb) + 1)))
                        vm.show("Copied")
                    }
                    Chip("Copy all", true) { copyOut(ctx, joinWords(words)); vm.show("Copied all text") }
                }
            }
        }
    }
}

private fun Modifier.androidx_onSize(f: (IntSize) -> Unit): Modifier = this.onGloballyPositioned { f(it.size) }

/** Dipakai ImgCell untuk mencatat posisi bingkai di layar (untuk titik awal morph). */
fun Modifier.photoBounds(set: (Rect) -> Unit): Modifier = this.onGloballyPositioned { if (it.isAttached) set(it.boundsInRoot()) }

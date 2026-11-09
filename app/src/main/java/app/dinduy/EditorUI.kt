@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package app.dinduy

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextIndent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.BiasAlignment
import kotlinx.coroutines.delay

/** Lebar kapsul saat mengembang, sebagai pecahan dari lebar area. */
private const val BAR_FRAC = 0.84f

@androidx.compose.runtime.Immutable
class Dm(val fs: Float, val lh: Float, val ff: FontFamily = FontFamily.Default)

fun spanStyle(k: Int, pal: Pal, fs: Float): SpanStyle = when (k) {
    K.B -> SpanStyle(fontWeight = FontWeight(850))
    K.I -> SpanStyle(fontStyle = FontStyle.Italic)
    K.U -> SpanStyle(textDecoration = TextDecoration.Underline)
    K.S -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    K.M -> SpanStyle(background = pal.pc, color = pal.opc)
    K.C -> SpanStyle(fontFamily = FontFamily.Monospace, fontSize = (fs * .85f).sp, background = pal.sc, color = pal.osc)
    else -> SpanStyle(color = pal.p, fontWeight = FontWeight(700), textDecoration = TextDecoration.Underline)
}

/** Warna seleksi teks. Bagian yang bertanda stabilo dicampur ke warna stabilonya, jadi stabilo tetap kelihatan. off = awalan tak terlihat. */
fun AnnotatedString.Builder.addSel(pal: Pal, rt: Rt, a0: Int, b0: Int, off: Int, n: Int) {
    val a = a0.coerceIn(0, n); val b = b0.coerceIn(0, n)
    if (b <= a) return
    val sel = pal.p.copy(alpha = .32f)
    val onMark = Color(
        red = sel.red * sel.alpha + pal.pc.red * (1f - sel.alpha),
        green = sel.green * sel.alpha + pal.pc.green * (1f - sel.alpha),
        blue = sel.blue * sel.alpha + pal.pc.blue * (1f - sel.alpha)
    )
    var cur = a
    for ((ms, me) in rt.sp.filter { it.k == K.M }.map { (it.s + off).coerceIn(0, n) to (it.e + off).coerceIn(0, n) }.sortedBy { it.first }) {
        val s = maxOf(ms, cur); val e = minOf(me, b)
        if (e <= s) continue
        if (s > cur) addStyle(SpanStyle(background = sel), cur, s)
        addStyle(SpanStyle(background = onMark), s, e)
        cur = e
    }
    if (cur < b) addStyle(SpanStyle(background = sel), cur, b)
}

fun styled(rt: Rt, text: String, off: Int, pal: Pal, fs: Float): AnnotatedString = buildAnnotatedString {
    append(text)
    val n = text.length
    // awalan tak terlihat (Z): lebarnya dibuat nyaris nol, supaya kursor / teks rata tengah benar-benar di tengah (tidak geser setengah lebar Z).
    // Paragraf KOSONG (hanya Z, n == 1) tidak boleh dikecilkan: baris itu tak punya huruf lain, jadi tinggi baris dan kursornya ikut
    // menyusut ke 0,01sp dan kursor tak terlihat. Z tak berlebar (zero-width) sehingga tak menggeser kursor.
    if (off == 1 && n > 1 && text[0] == '\u200B') addStyle(SpanStyle(fontSize = 0.01.sp, letterSpacing = 0.sp), 0, 1)
    for (s in rt.sp) {
        val a = (s.s + off).coerceIn(0, n)
        val b = (s.e + off).coerceIn(0, n)
        if (b > a) addStyle(spanStyle(s.k, pal, fs), a, b)
    }
}

/** Sorot kecocokan "cari dalam catatan": yang aktif berwarna primer penuh, sisanya wadah tersier. off = awalan tak terlihat. */
fun AnnotatedString.Builder.addFind(pal: Pal, hits: List<FHit>, cur: FHit?, off: Int, n: Int) {
    for (h in hits) {
        val a = (h.s + off).coerceIn(0, n)
        val b = (h.e + off).coerceIn(0, n)
        if (b <= a) continue
        addStyle(if (h == cur) SpanStyle(background = pal.p, color = pal.op) else SpanStyle(background = pal.tc, color = pal.otc), a, b)
    }
}

fun baseStyle(pal: Pal, dm: Dm, color: Color = pal.os, size: Float = dm.fs, w: Int = 400, lhEm: Float = dm.lh, ls: Float = 0f) =
    TextStyle(color = color, fontFamily = dm.ff, fontSize = size.sp, fontWeight = FontWeight(w), lineHeight = (lhEm * size).sp, letterSpacing = ls.em)

/** Default Compose untuk gulir horizontal (kode / tabel); NoteScreen mematikan gulir vertikal bawaan lewat LocalBringIntoViewSpec. */
private val LocalHBiv = androidx.compose.runtime.staticCompositionLocalOf<BringIntoViewSpec?> { null }

@Composable
private fun HBox(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val d = LocalHBiv.current
    if (d == null) Box(modifier, content = content)
    else CompositionLocalProvider(LocalBringIntoViewSpec provides d) { Box(modifier, content = content) }
}

/** Catat posisi kolom teks di ruang konten, dipakai seleksi lintas blok (mode baca maupun mode ketik). */
private fun Modifier.trackPos(ed: Ed, key: String): Modifier = onGloballyPositioned { c ->
    val cc = ed.contentCoords
    if (cc != null && cc.isAttached && c.isAttached) {
        val o = cc.localPositionOf(c, Offset.Zero)
        if (ed.kpos[key] != o) ed.kpos[key] = o
    }
}

/**
 * Rata kiri-kanan buatan sendiri (justify bawaan Android tidak jalan di sebagian HP / font).
 * Ukur teks dengan rata kiri, lalu tiap baris (kecuali yang terakhir) dilebarkan dengan menambah jarak huruf
 * pada spasi di baris itu sampai pas selebar kolom. Teks tidak diubah, jadi kursor dan seleksi tetap benar.
 * Teks yang mengelilingi gambar (Fold) juga dirata: baris sempit di samping gambar dan baris lebar di bawahnya sama-sama pas ke tepinya,
 * hanya baris terakhir paragraf yang dibiarkan rata kiri.
 */
private fun justifySpans(
    a: AnnotatedString, style: TextStyle, w: Int, tm: androidx.compose.ui.text.TextMeasurer, d: androidx.compose.ui.unit.Density,
    cutR: Float = 0f
): AnnotatedString {
    if (w <= 0 || a.length < 2) return a
    val r = tm.measure(a, style = style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = w))
    if (r.lineCount < 2) return a
    val txt = a.text
    val maxExtra = with(d) { style.fontSize.toPx() } * 1.5f
    return buildAnnotatedString {
        append(a)
        for (i in 0 until r.lineCount - 1) {
            val s = r.getLineStart(i)
            val en = r.getLineEnd(i, visibleEnd = true).coerceAtMost(txt.length)
            // baris yang berakhir pemisah baris buatan (teks mengelilingi gambar di kanan) lebih sempit dari kolom: ratakan ke tepi gambar, bukan ke tepi kolom
            val hard = r.getLineEnd(i).let { it in 1..txt.length && txt[it - 1] == '\n' }
            val tw = if (hard && cutR > 0f) w - cutR else w.toFloat()
            val slack = tw - r.getLineRight(i)      // tepi kanan sudah termasuk inden (baris pertama / menggantung)
            var n = 0
            for (k in s until en) if (txt[k] == ' ') n++
            if (n == 0 || slack <= 1f) continue
            val extra = ((slack - 1f) / n).coerceAtMost(maxExtra)
            val sp = with(d) { extra.toSp() }
            for (k in s until en) if (txt[k] == ' ') addStyle(SpanStyle(letterSpacing = sp), k, k + 1)
        }
    }
}

/** Terapkan pemotongan baris buatan (teks mengelilingi gambar) pada teks bergaya. z = awalan tak terlihat di depan. */
private fun Fold.applyTo(a: AnnotatedString, z: Int): AnnotatedString = buildAnnotatedString {
    fun pad(j: Int) {
        val n = if (j < pn.size) pn[j] else 0
        if (n > 0) withStyle(SpanStyle(letterSpacing = pl[j].sp)) { append("\u2003".repeat(n)) }
    }
    var from = 0
    if (pn.isNotEmpty() && pn[0] > 0) {
        append(a.subSequence(0, z)); from = z
        pad(0)
    }
    for (k in cuts.indices) {
        val at = (cuts[k] + z).coerceIn(from, a.length)
        append(a.subSequence(from, at)); from = at
        append("\n")
        pad(k + 1)
    }
    append(a.subSequence(from, a.length))
}

/**
 * Deteksi ketuk sekali / dua kali tanpa mengonsumsi apa pun (dibaca di pass Final), jadi tidak mengganggu
 * ketukan bawaan BasicTextField (fokus, posisi kursor) maupun scroll.
 */
@Composable
private fun Modifier.wordTap(onTap: () -> Unit, onDouble: (Offset) -> Unit): Modifier {
    val t by rememberUpdatedState(onTap)
    val d by rememberUpdatedState(onDouble)
    return pointerInput(Unit) {
        var lastUp = -1L
        var lastPos = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val slop = viewConfiguration.touchSlop
            var up: PointerInputChange? = null
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Final)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if ((c.position - down.position).getDistance() > slop || c.uptimeMillis - down.uptimeMillis > 400L) break
                if (!c.pressed) { up = c; break }
            }
            val u = up
            if (u == null) { lastUp = -1L; return@awaitEachGesture }
            if (lastUp >= 0L && down.uptimeMillis - lastUp <= 300L && (down.position - lastPos).getDistance() < slop * 4f) {
                lastUp = -1L
                d(u.position)
            } else {
                lastUp = u.uptimeMillis
                lastPos = u.position
                t()
            }
        }
    }
}

/** Toolbar teks bawaan Android dimatikan: editor punya bubble seleksi sendiri (SelBubble), jadi bawaan hanya bikin dobel. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(
        rect: androidx.compose.ui.geometry.Rect,
        onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?
    ) {}
}

/** Satu kolom teks: BasicTextField saat mengedit, Text biasa saat mode baca. */
@Composable
fun TF(
    ed: Ed, key: String, base: TextStyle, modifier: Modifier = Modifier,
    hint: String? = null, wrap: Boolean = true, fold: Fold? = null
) {
    val pal = LocalPal.current
    val v = ed.tf[key] ?: return
    // Rt dibaca lewat derivedStateOf: blok lain yang berubah tidak membangunkan kolom ini (Rt blok yang tak berubah = instance sama)
    val rtS = remember(key) { androidx.compose.runtime.derivedStateOf { ed.rtOf(key) } }
    val rt = rtS.value
    val fs = base.fontSize.value
    val just = base.textAlign == androidx.compose.ui.text.style.TextAlign.Justify
    val base2 = if (base.textAlign == androidx.compose.ui.text.style.TextAlign.Justify)
        base.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start, lineBreak = androidx.compose.ui.text.style.LineBreak.Simple) else base
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val dn = androidx.compose.ui.platform.LocalDensity.current
    var jw by remember { mutableStateOf(0) }       // lebar kolom teks (px), dipakai rata kiri-kanan
    // Sel tabel yang tidak sedang diedit digambar sebagai Text ringan (sama seperti mode baca); BasicTextField hanya dipasang di sel aktif.
    val activeS = remember(key) { androidx.compose.runtime.derivedStateOf { ed.focus == key || ed.req?.key == key } }
    val lite = !ed.reading && isCellKey(key) && !activeS.value
    if (ed.reading || lite) {
        val sr = if (ed.selOn) ed.selRangeOf(key) else null
        val fh = if (ed.findOn) ed.hitsOf(key) else emptyList()
        val fc = if (fh.isEmpty()) null else ed.curHit()?.takeIf { it.key == key }
        val ann = remember(rt, pal, fs, sr, fold, fh, fc, jw, just, base.textIndent) {
            val a = styled(rt, rt.t, 0, pal, fs)
            val a2 = if (sr == null && fh.isEmpty()) a else buildAnnotatedString {
                append(a)
                if (sr != null) addSel(pal, rt, sr.first, sr.second, 0, rt.t.length)
                addFind(pal, fh, fc, 0, rt.t.length)
            }
            if (fold == null) (if (just && jw > 0) justifySpans(a2, base2, jw, tm, dn) else a2)
            else fold.applyTo(a2, 0).let { fa -> if (just && jw > 0) justifySpans(fa, base2, jw, tm, dn, fold.cutR) else fa }
        }
        val uri = LocalUriHandler.current
        var lay by remember { mutableStateOf<TextLayoutResult?>(null) }
        LaunchedEffect(lay) { lay?.let { ed.lays[key] = it; ed.lsh[key] = 0 } }
        Text(
            ann,
            modifier.heightIn(min = base.lineHeight.value.dp)
                .onGloballyPositioned { c ->
                    val cc = ed.contentCoords
                    if (cc != null && cc.isAttached && c.isAttached) {
                        val o = cc.localPositionOf(c, Offset.Zero)
                        if (ed.kpos[key] != o) ed.kpos[key] = o
                    }
                }
                .pointerInput(rt, ed.selOn, lite) {
                    if (ed.selOn && !lite) detectTapGestures { ed.clearSel() }
                    else detectTapGestures(
                        // mode baca: ketuk sekali di teks mana pun = langsung mengedit di situ (ketuk tautan = buka tautan)
                        // sel tabel ringan (mode ketik): ketuk = pasang kolom teks sungguhan di sel itu dan fokus ke sana
                        onTap = { pos ->
                            if (ed.selOn) ed.dropSel()
                            if (ed.ink.on) return@detectTapGestures
                            val l = lay ?: return@detectTapGestures
                            val o0 = l.getOffsetForPosition(pos)
                            val o = fold?.t2o(o0, 0) ?: o0
                            if (lite) {
                                val bk = keyBlock(key)
                                val inBlk = ed.selObj == bk || ed.focus?.let { keyBlock(it) } == bk
                                if (inBlk) ed.focusAt(key, o) else ed.selectObj(bk)   // ketuk pertama: pilih tabel (atur / geser), ketuk kedua: edit sel
                                return@detectTapGestures
                            }
                            val u = rt.sp.firstOrNull { it.k == K.L && o >= it.s && o < it.e }?.u
                            if (u != null) uri.openUri(u) else ed.tapFromReading(key, o)
                        }
                    )
                },
            style = base2, softWrap = wrap, onTextLayout = { lay = it; jw = it.layoutInput.constraints.maxWidth }
        )
    } else {
        val fr = remember { FocusRequester() }
        var lay by remember { mutableStateOf<TextLayoutResult?>(null) }
        var foc by remember { mutableStateOf(false) }
        var layN by remember { mutableStateOf(-1) }   // panjang teks saat layout 'lay' dibuat
        LaunchedEffect(lay) { lay?.let { ed.layLen[key] = layN; ed.lays[key] = it; ed.lsh[key] = 1 } }
        // permintaan fokus dibaca di dalam efek (bukan di komposisi): ed.req berubah tidak membangunkan semua kolom
        LaunchedEffect(key) {
            androidx.compose.runtime.snapshotFlow { ed.req }.collect { r ->
                if (r != null && r.key == key) {
                    try { fr.requestFocus() } catch (e: Exception) { }
                    ed.applyReq(r)
                }
            }
        }
        val kbc = LocalSoftwareKeyboardController.current
        // gulir ke kursor kini ditangani NoteScreen (ed.cursorScrollDelta): langsung ikut keyboard tiap frame, tanpa delay / animasi tambahan
        val sr = if (ed.selOn) ed.selRangeOf(key) else null
        val fh = if (ed.findOn) ed.hitsOf(key) else emptyList()
        val fc = if (fh.isEmpty()) null else ed.curHit()?.takeIf { it.key == key }
        val vt = remember(rt, pal, fs, fold, sr, fh, fc, jw, just, base.textIndent) {
            VisualTransformation { t ->
                val a0 = styled(rt, t.text, 1, pal, fs)
                val a = if (sr == null && fh.isEmpty()) a0 else buildAnnotatedString {
                    append(a0)
                    if (sr != null) {
                        addSel(pal, rt, sr.first + 1, sr.second + 1, 1, t.text.length)
                    }
                    addFind(pal, fh, fc, 1, t.text.length)
                }
                if (fold == null) TransformedText(if (just && jw > 0) justifySpans(a, base2, jw, tm, dn) else a, OffsetMapping.Identity)
                else {
                    val n = t.text.length
                    val tn = n + fold.total
                    val fa = fold.applyTo(a, 1)
                    TransformedText(if (just && jw > 0) justifySpans(fa, base2, jw, tm, dn, fold.cutR) else fa, object : OffsetMapping {
                        override fun originalToTransformed(offset: Int): Int = fold.o2t(offset, 1).coerceIn(0, tn)
                        override fun transformedToOriginal(offset: Int): Int = fold.t2o(offset, 1).coerceIn(0, n)
                    })
                }
            }
        }
        // tekan-tahan ditangani seleksi milik editor (satu jenis seleksi, keyboard tetap terbuka), bukan seleksi bawaan field
        val vcBase = LocalViewConfiguration.current
        val vcNoLong = remember(vcBase) {
            object : ViewConfiguration by vcBase { override val longPressTimeoutMillis: Long get() = 60_000L }
        }
        val selCols = androidx.compose.foundation.text.selection.LocalTextSelectionColors.current
        @Suppress("DEPRECATION")
        val clipM = androidx.compose.ui.platform.LocalClipboardManager.current
        val hideNative = ed.selOn   // seleksi editor aktif: sembunyikan kursor + pegangan bawaan field supaya tidak dobel
        val cols = if (hideNative) androidx.compose.foundation.text.selection.TextSelectionColors(Color.Transparent, Color.Transparent) else selCols
        CompositionLocalProvider(
            LocalViewConfiguration provides vcNoLong,
            LocalTextToolbar provides NoTextToolbar,
            androidx.compose.foundation.text.selection.LocalTextSelectionColors provides cols
        ) {
            BasicTextField(
                value = v, onValueChange = { ed.onText(key, it) },
                modifier = modifier.focusProperties { canFocus = !ed.ink.on }.focusRequester(fr).trackPos(ed, key)
                    .then(
                        // sheet bawah aktif => field readOnly (kursor bawaan tidak digambar), jadi kursor digambar manual agar tetap terlihat
                        if (foc && ed.sheet != 0) Modifier.drawWithContent {
                            drawContent()
                            val l = lay
                            if (l != null && v.selection.collapsed) {
                                val o = (fold?.o2t(v.selection.start, 1) ?: v.selection.start).coerceIn(0, l.layoutInput.text.length)
                                val r = l.getCursorRect(o)
                                val w = 2.dp.toPx()
                                drawLine(pal.p, Offset(r.left + w / 2f, r.top), Offset(r.left + w / 2f, r.bottom), w, androidx.compose.ui.graphics.StrokeCap.Round)
                            }
                        } else Modifier
                    )
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val ctrl = e.isCtrlPressed
                        when {
                            ctrl && e.key == Key.A -> { ed.selectAllKey(key); true }
                            (ctrl && e.key == Key.C) || e.key == Key.Copy ->
                                if (ed.selOn) { clipM.setText(androidx.compose.ui.text.AnnotatedString(ed.selMd())); true } else false
                            (ctrl && e.key == Key.X) || e.key == Key.Cut ->
                                if (ed.selOn) { clipM.setText(androidx.compose.ui.text.AnnotatedString(ed.selMd())); ed.selDelete(); true } else false
                            (ctrl && e.key == Key.V) || e.key == Key.Paste ->
                                if (ed.selOn) {
                                    val s = clipM.getText()?.text
                                    if (!s.isNullOrEmpty()) ed.selPaste(s)
                                    true
                                } else false
                            e.key == Key.Backspace && !ctrl && !e.isAltPressed -> ed.backKey(key)   // shift boleh aktif: Gboard mengirim Backspace dengan meta SHIFT saat mode kapital
                            e.key == Key.MoveHome -> ed.jumpKey(key, false, ctrl, e.isShiftPressed)
                            e.key == Key.MoveEnd -> ed.jumpKey(key, true, ctrl, e.isShiftPressed)
                            e.key == Key.PageUp -> ed.selOn && ed.jumpKey(key, false, true, e.isShiftPressed)
                            e.key == Key.PageDown -> ed.selOn && ed.jumpKey(key, true, true, e.isShiftPressed)
                            e.key == Key.DirectionLeft -> ed.navKey(key, 0, e.isShiftPressed)
                            e.key == Key.DirectionRight -> ed.navKey(key, 1, e.isShiftPressed)
                            e.key == Key.DirectionUp -> ed.navKey(key, 2, e.isShiftPressed)
                            e.key == Key.DirectionDown -> ed.navKey(key, 3, e.isShiftPressed)
                            else -> false
                        }
                    }
                    .onFocusChanged { foc = it.isFocused; if (it.isFocused) { ed.onFocus(key); if (ed.sheet != 0) kbc?.hide() } }
                    .wordTap(
                        onTap = { if (ed.selEdit) ed.dropSel() },
                        onDouble = { pos ->
                            val l = lay
                            if (l != null) {
                                val t = l.getOffsetForPosition(pos)
                                val o = (fold?.t2o(t, 1) ?: t) - 1
                                ed.dblWord(key, o.coerceAtLeast(0))
                            }
                        }
                    ),
                textStyle = base2, cursorBrush = SolidColor(if (hideNative) Color.Transparent else pal.p), visualTransformation = vt,
                onTextLayout = { lay = it; layN = v.text.length; jw = it.layoutInput.constraints.maxWidth },
                readOnly = foc && ed.sheet != 0,      // sheet bawah aktif: hanya kolom yang fokus yang membaca ed.sheet (kolom lain tidak ikut recompose)
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box {
                        if (hint != null && v.text.length <= 1) {
                            Text(hint, style = base.copy(color = base.color.copy(alpha = .4f)))
                        }
                        inner()
                    }
                }
            )
        }
    }
}

class QS(val bg: Color, val fg: Color, val shape: Shape, val label: String?, val bar: Boolean = false, val border: Boolean = false)

fun qs(k: String, p: Pal): QS = when (k) {
    "note" -> QS(p.pc, p.opc, RoundedCornerShape(28.dp), "Note")
    "tip" -> QS(p.sc, p.osc, RoundedCornerShape(40.dp, 12.dp, 40.dp, 12.dp), "Tip")
    "imp" -> QS(p.p, p.op, RoundedCornerShape(12.dp, 40.dp, 12.dp, 40.dp), "Important")
    "warn" -> QS(p.wc, p.owc, RoundedCornerShape(24.dp), "Warning", bar = true)
    "cau" -> QS(p.ec, p.oec, RoundedCornerShape(6.dp, 32.dp, 32.dp, 32.dp), "Caution")
    "idea" -> QS(p.ac, p.oac, RoundedCornerShape(44.dp, 44.dp, 12.dp, 44.dp), "Idea")
    "def" -> QS(Color.Transparent, p.os, RoundedCornerShape(32.dp), "Definition", border = true)
    else -> QS(p.tc, p.otc, RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp), null)
}

val VARS = listOf(
    "quote" to "Quote", "note" to "Note", "tip" to "Tip", "imp" to "Important",
    "warn" to "Warning", "cau" to "Caution", "idea" to "Idea", "def" to "Definition"
)

@Composable
fun BlockView(ed: Ed, vm: AppVM, b: Blk, ordN: Int, dm: Dm, fold: Fold? = null, mk: Float = 0f) {
    val pal = LocalPal.current
    val fs = dm.fs
    val bottom = (fs * 1.1f).dp
    when (b.ty) {
        "p", "li" -> {
            // paragraf dan poin memakai satu composable yang sama: mengubah poin <-> paragraf tidak membuat TextField baru (keyboard tetap)
            val only = b.ty == "p" && b.r.t.isEmpty() && ed.blocks.size == 1
            ParaRow(ed, b, ordN, dm, pal, fold, mk, if (only) "Write a note…" else null)
        }
        "h" -> {
            val st = when (b.lv) {
                1 -> baseStyle(pal, dm, pal.p, fs * 2.5f, 1000, 1.02f, -.02f)
                2 -> baseStyle(pal, dm, pal.os, fs * 1.85f, 1000, 1.02f, -.02f)
                3 -> baseStyle(pal, dm, pal.p, fs * 1.45f, 1000, 1.02f, -.02f)
                else -> baseStyle(pal, dm, pal.os, fs * 1.15f, 1000, 1.02f, -.02f)
            }
            val (il, ir) = fitInd(b.il, b.ir, ed.widthDp)
            TF(ed, b.id, st.aligned(b), Modifier.fillMaxWidth().padding(start = il.dp, end = ir.dp, top = (fs * .4f).dp, bottom = (fs * .5f).dp))
        }
        "q", "code", "hr" -> BoxBlock(ed, b, dm, pal)
        "tbl" -> TableView(ed, b, dm, pal)
        "img" -> ImgView(ed, vm, b, dm)
        "lnk" -> LinkCard(ed, b, dm)
    }
}

@Composable
fun ParaRow(ed: Ed, b: Blk, ordN: Int, dm: Dm, pal: Pal, fold: Fold?, mk: Float, hint: String?) {
    val fs = dm.fs
    val li = b.ty == "li"
    val lineH = fs * dm.lh
    val mw = when (b.lt) { "o", "a" -> 2.2f; "c" -> 1.9f; else -> 1.7f } * fs
    val done = li && b.lt == "c" && b.x
    val (il, ir) = fitInd(b.il, b.ir, ed.widthDp)
    val st = baseStyle(pal, dm).let {
        if (done) it.copy(color = it.color.copy(alpha = .6f), textDecoration = TextDecoration.LineThrough) else it
    }.aligned(b)
    val dn = LocalDensity.current
    val tm = rememberTextMeasurer()
    // garis dasar baris pertama (dp dari atas kolom teks): penanda disejajarkan dengan tengah huruf kecil baris pertama, bukan tengah kotak baris
    val baseDp = if (li) remember(st.fontFamily, st.fontSize, st.lineHeight, st.fontWeight, dn.density) {
        tm.measure("x", st).getLineBaseline(0) / dn.density
    } else 0f
    val fsDp = with(dn) { st.fontSize.toDp().value }
    val cy = baseDp - .31f * fsDp
    // butir terakhir sebuah daftar: jarak bawahnya sama dengan paragraf biasa (bukan jarak rapat antarbutir)
    val lastLi = li && androidx.compose.runtime.remember(b.id, ed) {
        androidx.compose.runtime.derivedStateOf {
            val l = ed.blocks
            val i = l.indexOfFirst { it.id == b.id }
            i < 0 || i == l.size - 1 || l[i + 1].ty != "li"
        }
    }.value
    // teks yang dipotong di sekitar gambar: inden baris pertama tidak dipakai (tiap pemotongan memulai paragraf baru), diganti penggeser di Fold
    val stF = if (fold == null) st else st.copy(
        textIndent = if (b.fi < 0 && (b.ty == "p" || b.ty == "li")) TextIndent(0.sp, with(dn) { (-b.fi).coerceIn(0, IND_MAX_FI).dp.toSp() }) else null
    )
    Row(
        Modifier.fillMaxWidth().padding(
            start = (if (li) (b.d * 1.4f * fs).dp else 0.dp) + il.dp,
            end = ir.dp,
            bottom = (if (li && !lastLi) fs * .5f else fs * 1.1f).dp
        )
    ) {
        if (li) {
            Box(
                Modifier.offset(mk.dp, 0.dp).width(mw.dp).height(lineH.dp).then(
                    if (b.lt == "c") Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { ed.toggleCheck(b.id) } else Modifier
                )
            ) {
                when (b.lt) {
                    "o", "a" -> {
                        val sz = 1.55f * fs
                        val label = if (b.lt == "a") letterOf(ordN) else ordN.toString()
                        Box(
                            Modifier.offset(0.dp, (cy - sz / 2f).dp).size(sz.dp).clip(CircleShape).background(pal.p),
                            Alignment.Center
                        ) { T(label, fs * (if (label.length > 1) .62f else .85f), 900, pal.op) }
                    }
                    "c" -> {
                        val sz = 1.15f * fs
                        val shape = RoundedCornerShape((.3f * sz).dp)
                        Box(
                            Modifier.offset((.1f * fs).dp, (cy - sz / 2f).dp).size(sz.dp).clip(shape)
                                .then(if (done) Modifier.background(pal.p) else Modifier.border((.14f * fs).dp, pal.p, shape)),
                            Alignment.Center
                        ) { if (done) Ic("ok", Color.White, (sz * .78f).dp) }
                    }
                    else -> {
                        val sz = .5f * fs
                        Box(
                            Modifier.offset((.3f * fs).dp, (cy - sz / 2f).dp).size(sz.dp).clip(
                                RoundedCornerShape(topStartPercent = 40, topEndPercent = 60, bottomEndPercent = 55, bottomStartPercent = 45)
                            ).background(pal.p)
                        )
                    }
                }
            }
        }
        TF(ed, b.id, stF, Modifier.weight(1f), hint = hint, fold = fold)
    }
}

/** Lebar bawaan satu kolom (dp): tabel 1-2 kolom dibuat ringkas supaya teks mengalir di sampingnya; lebih dari itu dibagi rata (minimal 110dp). */
fun tblDef(n: Int, viewDp: Float): Float {
    if (n <= 2) {
        val maxSide = (1f - (SIDE_MIN + SIDE_GAP) / viewDp.coerceAtLeast(200f)).coerceIn(.3f, .9f) * viewDp - 4f
        return minOf(150f, maxSide / n).coerceAtLeast(80f)
    }
    return maxOf(110f, viewDp / n)
}

/** Lebar tiap kolom tabel (dp): lebar yang disetel pengguna, atau lebar bawaan. */
fun tblWidths(b: Blk, viewDp: Float): List<Float> {
    val n = b.tb.firstOrNull()?.size ?: 1
    val def = tblDef(n, viewDp)
    return List(n) { c -> b.cw.getOrNull(c)?.takeIf { it >= 40f } ?: def }
}

const val COL_MIN = 56f
private const val COL_MAX = 640f

@Composable
fun TableView(ed: Ed, b: Blk, dm: Dm, pal: Pal, modifier: Modifier = Modifier) {
    val cols = b.tb.firstOrNull()?.size ?: 1
    val dens = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    val ws = tblWidths(b, ed.widthDp)
    val def = tblDef(cols, ed.widthDp)
    // sel yang sedang diedit di tabel ini (null = tidak ada). derivedStateOf: hanya tabel yang fokusnya berubah yang recompose.
    val fkS = remember(b.id) {
        androidx.compose.runtime.derivedStateOf {
            val f = ed.focus
            if (f != null && !ed.reading && isCellKey(f) && keyBlock(f) == b.id) f else null
        }
    }
    val fk = fkS.value
    val fRC = if (fk != null) cellRC(fk) else null
    val hs = rememberScrollState()
    var viewW by remember { mutableStateOf(0) }
    var dragC by remember { mutableStateOf(-1) }
    var snapOn by remember { mutableStateOf(false) }
    // [0] lebar yang ditampilkan (dp), [1] gulir-tepi sudah aktif (0/1), [2] lebar mentah (dp), [3] x jari sekarang (px, ruang layar),
    // [4] x jari saat mulai, [5] -, [6] lebar kolom saat mulai (dp), [7] total geseran otomatis (px), [8] tepi kanan tabel di layar (px)
    val dw = remember { FloatArray(9) }
    val bS by rememberUpdatedState(b)
    val wsS by rememberUpdatedState(ws)
    val lifted = ed.mv?.let { it.bid == b.id } == true
    val active = (fk != null || ed.selObj == b.id) && !ed.reading && ed.mv == null
    val showH = (active || dragC >= 0) && !lifted
    var headH by remember { mutableStateOf(0) }

    // Lebar kolom dihitung MUTLAK dari posisi jari di layar (bukan dari selisih per-frame): pegangan ikut bergeser saat kolom
    // melebar, jadi pendekatan selisih membuat lebar loncat-loncat. Dengan titik awal tetap, tepi kolom selalu mengikuti jari.
    fun applyW(c: Int) {
        val cap = COL_MAX
        val raw = dw[6] + ((dw[3] - dw[4]) + dw[7]) / dens
        dw[2] = raw.coerceIn(COL_MIN, maxOf(COL_MIN, cap))
        // menempel ke lebar bawaan saat sudah dekat (getar kecil), seperti gambar menempel ke rasio aslinya
        val near = kotlin.math.abs(dw[2] - def) < 8f && def <= cap
        if (near && !snapOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        snapOn = near
        dw[0] = if (near) def else dw[2]
        ed.tblCol(bS.id, c, dw[0])
    }

    // Jari ditahan di tepi kanan layar: tabel ikut tergulir dan kolom terus melebar. Hanya aktif bila jari BENAR-BENAR berada di zona tepi
    // setelah sengaja digeser ke sana; sebelumnya, kolom terakhir melebar sendiri begitu disentuh.
    LaunchedEffect(dragC) {
        if (dragC < 0) return@LaunchedEffect
        val c = dragC
        val zone = 40f * dens
        while (true) {
            delay(16)
            if (viewW <= 0) continue
            val inZone = dw[3] > dw[8] - zone
            if (dw[1] == 0f && (!inZone || dw[3] - dw[4] > 36f * dens)) dw[1] = 1f   // 'siap': jari pernah keluar zona atau sudah geser jauh ke kanan
            if (dw[1] == 1f && inZone) {
                val f = ((dw[3] - (dw[8] - zone)) / zone).coerceIn(0f, 1f)
                val v = f * f * 5f * dens          // maks ~5dp per frame, naik pelan-pelan
                val before = dw[2]
                dw[7] += v
                applyW(c)
                if (dw[2] == before) dw[7] -= v else hs.scrollBy(v)
            }
        }
    }

    Column(modifier.fillMaxWidth().padding(bottom = (dm.fs * 1.1f).dp)) {
        // tanpa pegangan: tekan-tahan di mana pun pada tabel = angkat dan pindahkan seluruh tabel (seperti foto), juga di mode baca.
        // Saat sebuah sel sedang diedit, tekan-tahan tepat di atas huruf tetap untuk seleksi teks; pegangan lebar kolom tidak ikut terangkat.
        HBox(
            Modifier.clip(RoundedCornerShape(4.dp)).background(pal.c2)
                .border(1.5.dp, if (active) pal.p.copy(alpha = .75f) else pal.osv.copy(alpha = .75f), RoundedCornerShape(4.dp))   // garis luar jelas di tema terang maupun gelap
                .onGloballyPositioned { viewW = it.size.width; dw[8] = it.positionInRoot().x + it.size.width }
                .objLift(
                    ed, b.id, fk != null,
                    textKeys = { bS.tb.flatMapIndexed { r, row -> row.indices.map { c -> "${bS.id}:$r:$c" } } },
                    skip = { pos ->
                        if (!showH || pos.y > headH) false
                        else {
                            val x = pos.x + hs.value
                            var e = 0f
                            var hit = false
                            val n = wsS.size
                            for (c in 0 until n) {
                                e += wsS[c] * dens
                                val half = if (c == n - 1) 32f * dens else 22f * dens
                                if (if (c == n - 1) x > e - half else kotlin.math.abs(x - e) < half) { hit = true; break }
                            }
                            hit
                        }
                    }
                )
                .horizontalScroll(hs)
        ) {
            Box(
                Modifier.pointerInput(ed.reading) {
                    // mode baca: ketuk di mana pun di tabel (termasuk bantalan sel) = pilih tabel
                    if (ed.reading) detectTapGestures(onTap = { if (!ed.ink.on) ed.tapObj(bS.id) })
                }
            ) {
            Column {
                b.tb.forEachIndexed { r, row ->
                    val head = r == 0
                    val fc = if (fRC != null && fRC.first == r) fRC.second else -1
                    // satu baris = satu Layout sendiri (satu kali ukur, tanpa IntrinsicSize): tinggi baris = sel tertinggi
                    TRow(ws.map { it.dp }, head, r > 0, fc, if (head) dragC else -1, pal, if (head) Modifier.onSizeChanged { headH = it.height } else Modifier) {
                        row.forEachIndexed { c, _ ->
                            val k = "${b.id}:$r:$c"
                            val focused = fc == c
                            val fg = if (focused) pal.opc else if (head) pal.osc else pal.os
                            val al = when (b.ca.getOrNull(c)) {
                                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                                "right" -> androidx.compose.ui.text.style.TextAlign.End
                                else -> androidx.compose.ui.text.style.TextAlign.Start
                            }
                            // dibungkus Box: tiap sel selalu tepat satu anak Layout (TF bisa kosong sesaat), supaya pegangan tak salah urut
                            Box(
                                Modifier.fillMaxWidth().pointerInput(active, k) {
                                    // tabel terpilih: ketuk di mana pun di sel (bukan cuma di huruf) = edit sel itu
                                    if (active && !ed.reading && !ed.ink.on) detectTapGestures(onTap = { ed.focusAt(k, ed.rtOf(k).t.length) })
                                }
                            ) {
                                TF(
                                    ed, k,
                                    TextStyle(
                                        color = fg, fontFamily = dm.ff, fontSize = (dm.fs * .9f).sp, lineHeight = (dm.fs * .9f * 1.45f).sp,
                                        fontWeight = FontWeight(if (head) 850 else 400), textAlign = al
                                    ),
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                        if (head) for (c in 0 until cols) key(c) {
                            ColHandle(
                                pal, c == cols - 1, showH, dragC == c,
                                onStart = { rx ->
                                    ed.commit()
                                    dragC = c; snapOn = false
                                    dw[0] = wsS.getOrElse(c) { def }; dw[1] = 0f; dw[2] = dw[0]
                                    dw[3] = rx; dw[4] = rx; dw[6] = dw[0]; dw[7] = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { rx -> dw[3] = rx; applyW(c) },
                                onEnd = { dragC = -1; snapOn = false; ed.commit() },
                                onReset = { ed.commit(); ed.tblCol(bS.id, c, 0f); ed.commit() }
                            )
                        }
                    }
                }
            }
            // belum terpilih: seluruh tabel (termasuk bantalan sel) satu area ketuk besar untuk memilih; ketuk kedua baru masuk sel
            if (!ed.reading && !ed.ink.on && !active && !lifted) {
                Box(Modifier.matchParentSize().pointerInput(b.id) { detectTapGestures(onTap = { ed.selectObj(bS.id) }) })
            }
            }
        }
    }
}

/**
 * Pegangan lebar kolom di tepi kanan sel kepala: batang kecil yang terlihat, area sentuh 44dp (kolom terakhir 32dp, di dalam tabel).
 * Seret ke kiri / kanan untuk mengubah lebar, ketuk dua kali untuk mengembalikan ke lebar otomatis.
 * Hanya geseran HORIZONTAL yang diambil (lewat ambang geser), jadi menggulir halaman ke atas / bawah dari sini tidak mengubah kolom.
 * Posisi jari dilaporkan dalam ruang layar (bukan relatif ke pegangan yang ikut bergeser).
 */
@Composable
private fun ColHandle(
    pal: Pal, last: Boolean, show: Boolean, dragging: Boolean,
    onStart: (Float) -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit, onReset: () -> Unit
) {
    val st by rememberUpdatedState(onStart)
    val dr by rememberUpdatedState(onDrag)
    val en by rememberUpdatedState(onEnd)
    val rs by rememberUpdatedState(onReset)
    val sc by animateFloatAsState(if (dragging) 1.45f else 1f, spring(.6f, 600f), label = "chs")
    val al by animateFloatAsState(if (show) 1f else 0f, tween(160), label = "cha")
    val co = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    Box(
        Modifier
            .onGloballyPositioned { co[0] = it }
            .pointerInput(show) { if (show) detectTapGestures(onDoubleTap = { rs() }) }
            .pointerInput(show) {
                if (show) awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val rootX = { p: Offset -> co[0]?.takeIf { it.isAttached }?.localToRoot(p)?.x ?: p.x }
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { ch, _ -> ch.consume() }
                        ?: return@awaitEachGesture
                    st(rootX(slop.position))
                    try {
                        horizontalDrag(slop.id) { ch -> ch.consume(); dr(rootX(ch.position)) }
                    } finally {
                        en()
                    }
                }
            },
        if (last) Alignment.CenterEnd else Alignment.Center
    ) {
        Box(
            Modifier.padding(end = if (last) 6.dp else 0.dp)
                .graphicsLayer { scaleX = sc; scaleY = sc; alpha = al }
                .size(5.dp, 28.dp).clip(CircleShape).background(pal.s).padding(1.dp).clip(CircleShape).background(pal.p)
        )
    }
}

/**
 * Satu baris tabel. Latar sel (kepala / sel aktif / kolom yang sedang dilebarkan) dan garis pemisah digambar di sini.
 * Anak pertama sampai ke-n = sel; sisanya (kalau ada) = pegangan lebar kolom, ditaruh di tepi kanan tiap kolom.
 */
@Composable
private fun TRow(ws: List<Dp>, head: Boolean, topLine: Boolean, fc: Int, dc: Int, pal: Pal, extra: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = extra.drawBehind {
            var x = 0f
            ws.forEachIndexed { c, wd ->
                val w = wd.toPx()
                if (c == fc) drawRect(pal.pc, Offset(x, 0f), Size(w, size.height))
                else if (head) drawRect(pal.sc, Offset(x, 0f), Size(w, size.height))
                if (c == dc) drawRect(pal.p.copy(alpha = .16f), Offset(x, 0f), Size(w, size.height))
                x += w
            }
            if (topLine) drawLine(pal.ol, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())   // garis pemisah baris
            var cx = 0f
            for (c in 0 until ws.size - 1) {
                cx += ws[c].toPx()
                drawLine(pal.ol, Offset(cx, 0f), Offset(cx, size.height), 1.dp.toPx())   // garis pemisah kolom
            }
        }
    ) { ms, _ ->
        val n = ws.size
        val px = ws.map { it.roundToPx() }
        val cells = (0 until n).map { i -> ms[i].measure(Constraints(minWidth = px[i], maxWidth = px[i])) }
        val h = cells.maxOfOrNull { it.height } ?: 0
        val hw = 44.dp.roundToPx()
        val hwL = 32.dp.roundToPx()   // pegangan kolom terakhir lebih sempit: tidak menutupi teks sel di tepi kanan
        val hands = (n until ms.size).mapIndexed { k, i -> ms[i].measure(Constraints.fixed(if (k == n - 1) hwL else hw, h)) }
        layout(px.sum(), h) {
            var x = 0
            cells.forEachIndexed { i, p -> p.placeRelative(x, 0); x += px[i] }
            var e = 0
            hands.forEachIndexed { i, p ->
                e += px[i]
                p.placeRelative(if (i == n - 1) e - hwL else e - hw / 2, 0)
            }
        }
    }
}

/** Menempel halus ke rasio asli gambar saat sudah dekat (ada getar kecil); tanpa angka apa pun di layar. */
private fun snapRatio(a: Float, o: Float): Pair<Float, Boolean> =
    if (abs(a / o - 1f) < .03f) o to true else a to false

/** Ukuran konten gambar (px) saat menutup bingkai bw x bh dengan zoom z. */
private fun cropSize(o: Float, bw: Float, bh: Float, z: Float): Pair<Float, Float> =
    if (o > bw / bh) (bh * o * z) to (bh * z) else (bw * z) to (bw / o * z)

/** Posisi geser baru agar pusat bagian yang terlihat tidak berpindah saat zoom berubah. */
private fun rezoom(p: Float, v0: Float, v1: Float): Float {
    if (v1 >= .999f) return .5f
    val c = (1f - v0) * p + v0 / 2f
    return ((c - v1 / 2f) / (1f - v1)).coerceIn(0f, 1f)
}

/**
 * Tekan-tahan lalu geser. Dibaca di pass Initial supaya anak (ketuk, geser pangkas) tidak ikut bereaksi
 * setelah tekan-tahan terpicu; ketukan dan scroll biasa tetap lewat seperti semula.
 */
@Composable
fun Modifier.holdDrag(
    enabled: Boolean,
    onStart: (Offset) -> Boolean,
    onMove: (Offset, Offset) -> Unit,
    onEnd: (Boolean) -> Unit,
    lead: Long = 0L,
    accept: (Offset) -> Boolean = { true }
): Modifier {
    val acc by rememberUpdatedState(accept)
    val st by rememberUpdatedState(onStart)
    val mo by rememberUpdatedState(onMove)
    val en by rememberUpdatedState(onEnd)
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!acc(down.position)) return@awaitEachGesture   // sentuhan di tempat yang bukan milik gestur ini (mis. di atas huruf): lewatkan
            val slop = viewConfiguration.touchSlop
            val early = withTimeoutOrNull((viewConfiguration.longPressTimeoutMillis - lead).coerceAtLeast(100L)) {
                var done = false
                while (!done) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed || (c.position - down.position).getDistance() > slop) done = true
                }
                true
            }
            if (early != null) return@awaitEachGesture
            if (!st(down.position)) return@awaitEachGesture
            var ok = true
            try {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == down.id }
                    if (c == null) { ok = false; break }
                    if (!c.pressed) { c.consume(); break }
                    val d = c.positionChange()
                    if (d != Offset.Zero) mo(c.position, d)
                    ev.changes.forEach { it.consume() }
                }
            } catch (e: Throwable) {
                ok = false; throw e
            } finally {
                en(ok)   // gesture dibatalkan di tengah jalan pun tetap diakhiri, jadi gulir-tepi seleksi tidak pernah nyangkut
            }
        }
    }
}

/**
 * Tekan-tahan di mana pun pada bentuk objek (tabel / kutipan / kode / garis) = angkat lalu pindahkan, seperti foto. Tanpa pegangan khusus.
 * Berlaku juga di mode baca (langsung masuk mode edit lalu terangkat). Saat teks objek sedang diedit ([guardText]), tekan-tahan tepat di atas
 * huruf tetap milik seleksi teks; di luar huruf (bantalan, label, ruang kosong) tetap mengangkat. [skip] = area yang dipakai gestur lain (pegangan lebar).
 */
@Composable
fun Modifier.objLift(
    ed: Ed, bid: String, guardText: Boolean, textKeys: () -> List<String>, skip: (Offset) -> Boolean = { false }
): Modifier {
    val co = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val id by rememberUpdatedState(bid)
    val guard by rememberUpdatedState(guardText)
    val keys by rememberUpdatedState(textKeys)
    val sk by rememberUpdatedState(skip)
    return this
        .onGloballyPositioned { co[0] = it }
        .holdDrag(
            !ed.ink.on,
            onStart = { pos ->
                if (ed.reading) { if (ed.ink.on) return@holdDrag false; ed.exitReading() }
                ed.mvBegin(id, "", pos.y)   // pos.y = letak jari di dalam objek: acuan gulir-tepi
                true
            },
            onMove = { _, d -> ed.mvDrag(d.x, d.y) },
            onEnd = { ok -> ed.mvEndInner(ok) },
            lead = 150L,   // sama dengan foto: terangkat cepat, tapi tetap beda dari gulir halaman
            accept = { pos ->
                if (sk(pos)) false
                else if (!guard) true
                else {
                    val cc = ed.contentCoords
                    val c = co[0]
                    if (cc == null || !cc.isAttached || c == null || !c.isAttached) true
                    else {
                        val p = cc.localPositionOf(c, pos)
                        val m = 8f * ed.dens
                        keys().none { k -> ed.glyphRect(k)?.inflate(m)?.contains(p) == true }
                    }
                }
            }
        )
}

/** Pegangan seret: area sentuh besar (44dp+), gagang kecil di dekat tepi. kind: 0 gagang tegak, 1 gagang datar, 2 titik sudut. */
@Composable
fun Grip(
    modifier: Modifier, kind: Int, pill: Alignment,
    onStart: () -> Unit, onDrag: (Float, Float) -> Unit, onEnd: () -> Unit
) {
    val pal = LocalPal.current
    val st by rememberUpdatedState(onStart)
    val dr by rememberUpdatedState(onDrag)
    val en by rememberUpdatedState(onEnd)
    var act by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (act) 1.3f else 1f, spring(.6f, 600f), label = "grip")
    Box(
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { act = true; st() },
                onDragEnd = { act = false; en() },
                onDragCancel = { act = false; en() }
            ) { ch, d -> ch.consume(); dr(d.x, d.y) }
        },
        pill
    ) {
        val (w, h) = when (kind) { 0 -> 5.dp to 30.dp; 1 -> 30.dp to 5.dp; else -> 16.dp to 16.dp }
        Box(Modifier.padding(10.dp).graphicsLayer { scaleX = sc; scaleY = sc }.size(w, h)) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(pal.s).padding(1.5.dp).clip(CircleShape).background(pal.p))
        }
    }
}

@Composable
fun ImgView(ed: Ed, vm: AppVM, b: Blk, dm: Dm) {
    val sel = ed.selImg?.takeIf { it.first == b.id }?.second
    val n = b.im.size
    val bottom = (dm.fs * 1.1f).dp
    if (n == 1) {
        val m = b.im[0]
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = bottom)) {
            val w = m.w.coerceIn(.2f, 1f)
            val imgW = maxWidth * w
            val ratio = m.a.coerceIn(.2f, 5f)
            val room = maxWidth.value * (1f - w) - SIDE_GAP
            val side = false   // teks di samping gambar kini mengalir otomatis (lihat FloatGroup)
            val cell: @Composable () -> Unit = {
                ImgCell(ed, vm, b, 0, Modifier.width(imgW).aspectRatio(ratio), sel == 0)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = when (m.al) { "l" -> Arrangement.Start; "r" -> Arrangement.End; else -> Arrangement.Center },
                verticalAlignment = Alignment.Top
            ) {
                if (side && m.al == "r") {
                    SideText(ed, b, dm, Modifier.weight(1f), imgW / ratio, sel == 0)
                    Spacer(Modifier.width(SIDE_GAP.dp))
                }
                cell()
                if (side && m.al == "l") {
                    Spacer(Modifier.width(SIDE_GAP.dp))
                    SideText(ed, b, dm, Modifier.weight(1f), imgW / ratio, sel == 0)
                }
            }
        }
    } else if (n > 1) {
        val sum = b.im.sumOf { it.a.toDouble() }.toFloat().coerceAtLeast(.1f)
        Row(
            Modifier.fillMaxWidth().padding(bottom = bottom).aspectRatio(sum / (1f - (n - 1) * GAP)),
            horizontalArrangement = Arrangement.spacedBy((ed.widthDp * GAP).dp)
        ) {
            b.im.forEachIndexed { j, m ->
                key(m.id) {
                    ImgCell(ed, vm, b, j, Modifier.weight(m.a.coerceAtLeast(.05f)).fillMaxHeight(), sel == j)
                }
            }
        }
    }
}

/** Sisi kosong di samping gambar: seluruh areanya bisa diketuk untuk mulai menulis. */
@Composable
fun SideText(ed: Ed, b: Blk, dm: Dm, modifier: Modifier, minH: Dp, imgSel: Boolean) {
    val pal = LocalPal.current
    if (ed.reading && b.r.t.isEmpty()) { Spacer(modifier); return }
    Box(
        modifier.heightIn(min = minH).then(
            if (ed.reading) Modifier
            else Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                ed.focusAt(b.id, b.r.t.length)
            }
        )
    ) {
        TF(
            ed, b.id, baseStyle(pal, dm), Modifier.fillMaxWidth(),
            hint = if (ed.reading) null else if (imgSel) "Tap here to write…" else "Write beside it…"
        )
    }
}

@Composable
fun ImgCell(ed: Ed, vm: AppVM, b: Blk, ix: Int, modifier: Modifier, selected: Boolean) {
    val pal = LocalPal.current
    val m = b.im[ix]
    val bmp = rememberImg(vm, m.id)
    val dens = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    var box by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var snapOn by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val row = b.im.size > 1
    val mS by rememberUpdatedState(m)
    val bS by rememberUpdatedState(b)
    val ixS by rememberUpdatedState(ix)
    val rs = remember { floatArrayOf(0f, 0f, 0f, 0f) }
    val mvNow = ed.mv
    val liftedRow = mvNow != null && mvNow.row && mvNow.bid == b.id && mvNow.imgId == m.id
    val liftedBlk = mvNow != null && !mvNow.row && mvNow.bid == b.id
    val lift by animateFloatAsState(if (liftedRow) 1.06f else 1f, spring(.6f, 500f), label = "lift")

    // pangkas: geser dan cubit (zoom) di dalam bingkai
    val cropBy: (Float, Offset) -> Unit = { zf, pn ->
        val mm = mS
        val bw = box.width.toFloat().coerceAtLeast(1f)
        val bh = box.height.toFloat().coerceAtLeast(1f)
        val (cw0, ch0) = cropSize(mm.o, bw, bh, mm.z)
        var z = mm.z
        var pxn = mm.px
        var pyn = mm.py
        if (zf != 1f) {
            val nz = (z * zf).coerceIn(1f, 4f)
            val (cw1, ch1) = cropSize(mm.o, bw, bh, nz)
            pxn = rezoom(pxn, bw / cw0, bw / cw1)
            pyn = rezoom(pyn, bh / ch0, bh / ch1)
            z = nz
        }
        val (cw, ch) = cropSize(mm.o, bw, bh, z)
        val rx = cw - bw
        val ry = ch - bh
        if (rx > 2f) pxn = (pxn - pn.x / rx).coerceIn(0f, 1f)
        if (ry > 2f) pyn = (pyn - pn.y / ry).coerceIn(0f, 1f)
        ed.imgLive(bS.id, ixS) { it.copy(z = z, px = pxn, py = pyn) }
    }

    // ubah bentuk gambar tunggal: lebar & tinggi bebas, menempel ke rasio umum
    val applyResize: (Float, Float) -> Unit = { wpx, hpx ->
        val cw = ed.widthDp * dens
        val wC = wpx.coerceIn(cw * .22f, cw)
        val hC = hpx.coerceIn(70f * dens, 1100f * dens)
        val (a, snapped) = snapRatio(wC / hC, mS.o)
        if (snapped && !snapOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        snapOn = snapped
        ed.imgLive(bS.id, ixS) { it.copy(w = wC / cw, a = a) }
    }

    Box(
        modifier
            .holdDrag(
                true,
                // mode baca: tahan lama pada foto langsung masuk mode edit dan foto terangkat (bisa diseret)
                onStart = {
                    if (ed.reading) { if (ed.ink.on) return@holdDrag false; ed.exitReading() }
                    ed.mvBegin(bS.id, mS.id); true
                },
                onMove = { _, d -> ed.mvDrag(d.x, d.y) },
                onEnd = { ok -> ed.mvEndInner(ok) },
                lead = 150L   // foto terangkat lebih cepat dari tekan-tahan biasa (~350 ms), jadi terasa langsung
            )
            .pointerInput(ed.reading) {
                if (!ed.reading) detectTapGestures(onTap = { ed.selectImg(bS.id, ixS) })
                else detectTapGestures(onTap = { ed.openImgFromReading(bS.id, ixS) })
            }
            .zIndex(if (liftedRow) 2f else 0f)
            .graphicsLayer {
                if (liftedRow) { translationX = ed.mvX; translationY = ed.mvY }
                scaleX = lift; scaleY = lift
            }
            .onSizeChanged { box = it }
            .photoBounds { frame = it }
            .onGloballyPositioned { c ->
                val cc = ed.contentCoords
                if (cc != null && cc.isAttached && c.isAttached) {
                    val o = cc.localPositionOf(c, Offset.Zero)
                    val nr = androidx.compose.ui.geometry.Rect(o, Size(c.size.width.toFloat(), c.size.height.toFloat()))
                    if (ed.ipos[mS.id] != nr) { ed.ipos[mS.id] = nr; ed.ink.lay++ }   // coretan di atas gambar ikut posisi / ukuran gambar
                }
            }
    ) {
        Box(
            Modifier.matchParentSize().clip(shape).background(pal.c3)
                .pointerInput(selected, m.id) {
                    if (!selected) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var acc = Offset.Zero
                        var slopOk = false
                        var panning = false
                        var began = false
                        do {
                            val ev = awaitPointerEvent()
                            if (ed.mv != null || ev.changes.any { it.isConsumed }) break
                            val down = ev.changes.count { it.pressed }
                            if (down >= 2) {
                                if (!began) { began = true; ed.commit() }
                                cropBy(ev.calculateZoom(), ev.calculatePan())
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                slopOk = true; panning = true
                            } else if (down == 1) {
                                val pn = ev.calculatePan()
                                if (!slopOk) {
                                    acc += pn
                                    if (acc.getDistance() > viewConfiguration.touchSlop) {
                                        slopOk = true
                                        val mm = mS
                                        val bw = box.width.toFloat().coerceAtLeast(1f)
                                        val bh = box.height.toFloat().coerceAtLeast(1f)
                                        val (cw, ch) = cropSize(mm.o, bw, bh, mm.z)
                                        panning = if (abs(acc.x) > abs(acc.y)) cw - bw > 2f else ch - bh > 2f
                                        if (panning) {
                                            began = true; ed.commit()
                                            cropBy(1f, acc)
                                            ev.changes.forEach { it.consume() }
                                        }
                                    }
                                } else if (panning) {
                                    cropBy(1f, pn)
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                        if (began) ed.commit()
                    }
                }
        ) {
            if (bmp != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val bw = size.width
                    val bh = size.height
                    val iw = bmp.width
                    val ih = bmp.height
                    val z = m.z.coerceIn(1f, 4f)
                    val (cw, ch) = cropSize(iw.toFloat() / ih, bw, bh, z)
                    val vw = (iw * bw / cw).coerceIn(1f, iw.toFloat())
                    val vh = (ih * bh / ch).coerceIn(1f, ih.toFloat())
                    val ox = ((iw - vw) * m.px).coerceIn(0f, iw - vw)
                    val oy = ((ih - vh) * m.py).coerceIn(0f, ih - vh)
                    drawImage(
                        bmp, IntOffset(ox.roundToInt(), oy.roundToInt()), IntSize(vw.roundToInt(), vh.roundToInt()),
                        dstSize = IntSize(bw.roundToInt(), bh.roundToInt()), filterQuality = FilterQuality.Medium
                    )
                }
            }
        }
        // gambar lain yang sedang ditimpa gambar seretan: sorot, dan tandai sisi masuknya (kiri / kanan)
        val mt = ed.mergeT
        if (mt != null && mt.imgId == m.id) {
            Box(Modifier.matchParentSize().clip(shape).background(pal.p.copy(alpha = .22f)).border(3.dp, pal.p, shape))
            Box(
                Modifier.align(if (mt.after) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 6.dp)
                    .size(7.dp, 64.dp).clip(CircleShape).background(pal.p)
            )
            val pop = remember { Animatable(0f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(.7f, 520f)) }
            Row(
                Modifier.align(Alignment.Center)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(CircleShape).background(pal.p).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { T("Side by side", 11f, 800, pal.op) }
        }
        if (ed.mv == null && bmp != null && !ed.reading) {
            // tombol perluas: buka menu foto (morph dari bingkai ini ke layar penuh)
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).size(32.dp).clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .5f))
                    .clickable { PhotoHost.req = PhotoReq(m.id, frame) },
                Alignment.Center
            ) { PIc("expand", androidx.compose.ui.graphics.Color.White, 16.dp) }
        }
        if (selected && !ed.reading && ed.mv == null) {
            Box(Modifier.matchParentSize().border(2.dp, pal.p, shape))
            // centang tutup mode edit gambar: menempel di gambarnya, bukan di toolbar
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(pal.p)
                    .clickable { ed.closeImg() },
                Alignment.Center
            ) { Ic("ok", pal.op, 20.dp) }
            val right = m.al != "r"
            val gx = if (right) 1f else -1f
            val kk = if (m.al == "c") 2f else 1f
            if (!row) {
                // tepi samping: ubah lebar saja (tinggi tetap) -> rasio berubah
                Grip(
                    Modifier.align(if (right) Alignment.CenterEnd else Alignment.CenterStart).size(46.dp, 96.dp),
                    0, if (right) Alignment.CenterEnd else Alignment.CenterStart,
                    onStart = { ed.commit(); rs[0] = box.width.toFloat(); rs[1] = box.height.toFloat(); rs[2] = 0f },
                    onDrag = { dx, _ -> rs[2] += dx * gx * kk; applyResize(rs[0] + rs[2], rs[1]) },
                    onEnd = { snapOn = false; ed.releaseSide(bS.id); ed.commit() }
                )
                // tepi bawah: ubah tinggi saja -> rasio berubah
                Grip(
                    Modifier.align(Alignment.BottomCenter).size(96.dp, 46.dp), 1, Alignment.BottomCenter,
                    onStart = { ed.commit(); rs[0] = box.width.toFloat(); rs[1] = box.height.toFloat(); rs[3] = 0f },
                    onDrag = { _, dy -> rs[3] += dy; applyResize(rs[0], rs[1] + rs[3]) },
                    onEnd = { snapOn = false; ed.commit() }
                )
                // sudut: perbesar/perkecil dengan rasio tetap
                Grip(
                    Modifier.align(if (right) Alignment.BottomEnd else Alignment.BottomStart).size(50.dp), 2,
                    if (right) Alignment.BottomEnd else Alignment.BottomStart,
                    onStart = { ed.commit(); rs[0] = box.width.toFloat(); rs[1] = box.height.toFloat(); rs[2] = 0f },
                    onDrag = { dx, dy ->
                        rs[2] += (dx * gx * kk + dy * (rs[0] / rs[1].coerceAtLeast(1f))) / 2f
                        val cw = ed.widthDp * dens
                        val wC = (rs[0] + rs[2]).coerceIn(cw * .22f, cw)
                        ed.imgLive(bS.id, ixS) { it.copy(w = wC / cw) }
                    },
                    onEnd = { ed.releaseSide(bS.id); ed.commit() }
                )
            } else {
                val last = b.im.size - 1
                // pembatas kolase: geser untuk menukar lebar dengan tetangga; tinggi baris tetap
                if (ix > 0) Grip(
                    Modifier.align(Alignment.CenterStart).size(46.dp, 96.dp), 0, Alignment.CenterStart,
                    onStart = { ed.commit() },
                    onDrag = { dx, _ ->
                        val h = box.height.toFloat().coerceAtLeast(1f)
                        ed.rowDivider(bS.id, ixS - 1, dx / h, 64f * dens / h)
                    },
                    onEnd = { ed.commit() }
                )
                if (ix < last) Grip(
                    Modifier.align(Alignment.CenterEnd).size(46.dp, 96.dp), 0, Alignment.CenterEnd,
                    onStart = { ed.commit() },
                    onDrag = { dx, _ ->
                        val h = box.height.toFloat().coerceAtLeast(1f)
                        ed.rowDivider(bS.id, ixS, dx / h, 64f * dens / h)
                    },
                    onEnd = { ed.commit() }
                )
                // tepi bawah: tinggi seluruh baris
                Grip(
                    Modifier.align(Alignment.BottomCenter).size(96.dp, 46.dp), 1, Alignment.BottomCenter,
                    onStart = { ed.commit(); rs[1] = box.height.toFloat(); rs[2] = rs[1]; rs[3] = 0f },
                    onDrag = { _, dy ->
                        rs[3] += dy
                        val hNew = (rs[1] + rs[3]).coerceIn(70f * dens, 1100f * dens)
                        ed.rowScale(bS.id, rs[2] / hNew); rs[2] = hNew
                    },
                    onEnd = { ed.commit() }
                )
            }
        }
    }
}

// ---------- gambar mengambang: teks mengalir di sekelilingnya ----------
/** Tambahan tata letak satu blok peserta (dp): geser kiri/kanan, ruang bawah ekstra, dan pemotongan baris bila blok melewati dasar gambar. */
class FSpec(val ps: Float, val pe: Float, var extra: Float, val fold: Fold?, val mk: Float = 0f)

/** Blok-blok setelah gambar yang ikut mengelilinginya, beserta tata letaknya. */
class FloatPlan(val parts: List<Pair<Blk, FSpec>>)

private class BM(val style: TextStyle, val top: Float, val bottom: Float, val inset: Float)

/** Gaya dan jarak tiap jenis blok (harus sama dengan BlockView / ListItem). null = bukan peserta (mengakhiri aliran). */
private fun blockBM(b: Blk, pal: Pal, dm: Dm): BM? {
    val fs = dm.fs
    return when (b.ty) {
        "p" -> BM(baseStyle(pal, dm), 0f, fs * 1.1f, 0f)
        "h" -> {
            val st = when (b.lv) {
                1 -> baseStyle(pal, dm, pal.p, fs * 2.5f, 1000, 1.02f, -.02f)
                2 -> baseStyle(pal, dm, pal.os, fs * 1.85f, 1000, 1.02f, -.02f)
                3 -> baseStyle(pal, dm, pal.p, fs * 1.45f, 1000, 1.02f, -.02f)
                else -> baseStyle(pal, dm, pal.os, fs * 1.15f, 1000, 1.02f, -.02f)
            }
            BM(st, fs * .4f, fs * .5f, 0f)
        }
        "li" -> {
            val mw = when (b.lt) { "o", "a" -> 2.2f; "c" -> 1.9f; else -> 1.7f } * fs
            BM(baseStyle(pal, dm), 0f, fs * .5f, b.d * 1.4f * fs + mw)
        }
        else -> null
    }
}

/** Perkiraan tinggi tabel (dp): sel diukur pada lebar kolomnya, tinggi baris = sel tertinggi. Termasuk tab pegangan saat mode ketik. */
private fun tblHeightDp(ed: Ed, b: Blk, dm: Dm, pal: Pal, tm: TextMeasurer, dn: Density): Float {
    val ws = tblWidths(b, ed.widthDp)
    val d = dn.density
    val fsz = dm.fs * .9f
    val lhPx = with(dn) { (fsz * 1.45f).sp.toPx() }
    var tot = 3f
    b.tb.forEachIndexed { r, row ->
        val st = TextStyle(
            fontFamily = dm.ff, fontSize = fsz.sp, lineHeight = (fsz * 1.45f).sp, fontWeight = FontWeight(if (r == 0) 850 else 400)
        )
        var mx = lhPx
        row.forEachIndexed { c, cell ->
            val avail = ((ws.getOrElse(c) { 110f } - 32f) * d).toInt().coerceAtLeast(1)
            val res = tm.measure(styled(cell, cell.t, 0, pal, fsz), st, constraints = Constraints(maxWidth = avail))
            mx = maxOf(mx, res.size.height.toFloat())
        }
        tot += mx / d + 24f + (if (r > 0) 1f else 0f)
    }
    return tot + (if (ed.reading) 0f else 28f)
}

/** Perkiraan tinggi (dp) kutipan / kode / garis sebelum tampilannya terukur. */
private fun boxHeightEst(ed: Ed, b: Blk, dm: Dm, pal: Pal, tm: TextMeasurer, dn: Density): Float {
    val fs = dm.fs
    val w = ed.widthDp * b.bw.coerceIn(.2f, 1f)
    return when (b.ty) {
        "hr" -> 8f + fs * 1.8f
        "q" -> {
            val q = qs(b.k, pal)
            val avail = ((w - 44f) * dn.density).toInt().coerceAtLeast(1)
            val fz = fs * 1.05f
            val h = tm.measure(styled(b.r, b.r.t, 0, pal, fz), baseStyle(pal, dm, q.fg, fz, 500), constraints = Constraints(maxWidth = avail)).size.height / dn.density
            h + 36f + (if (q.label != null) fs * .7f * 1.05f * 1.4f + fs * .45f * .7f else 0f)
        }
        else -> {
            val lines = b.r.t.count { it == '\n' } + 1
            lines * fs * .82f * 1.55f + 40f + (if (b.l.isNotEmpty()) 12f * 1.4f + 8f + 14f else 0f)
        }
    }
}

/**
 * Simulasi aliran teks: blok setelah gambar diukur pada lebar sempit sampai tinggi gambar habis.
 * Paragraf yang melewati dasar gambar dipotong barisnya (baris atas sempit, sisanya selebar penuh).
 * Judul / poin yang melewati dasar gambar tetap sempit seluruhnya.
 */
private fun planFloat(ed: Ed, i: Int, dm: Dm, pal: Pal, tm: TextMeasurer, dn: Density): FloatPlan {
    val b = ed.blocks[i]
    val d = dn.density
    val fs = dm.fs
    val left: Boolean
    val imgW: Float
    val imgH: Float
    if (b.ty == "tbl") {
        // tabel mengambang: lebar = jumlah lebar kolom, tinggi = hasil ukur isi sel
        left = b.alOf() != "r"
        imgW = tblWidths(b, ed.widthDp).sum() + 3f
        imgH = tblHeightDp(ed, b, dm, pal, tm, dn)
    } else if (b.isBox) {
        // kutipan / kode / garis: sama seperti foto; lebar = lebar relatif, tinggi = hasil ukur tampilan (perkiraan sebelum terukur)
        left = b.alOf() != "r"
        imgW = ed.widthDp * b.bw.coerceIn(.2f, 1f)
        imgH = ed.objH[b.id] ?: boxHeightEst(ed, b, dm, pal, tm, dn)
    } else {
        val m = b.im[0]
        left = m.al == "l"
        imgW = ed.widthDp * m.w.coerceIn(.2f, 1f)
        imgH = imgW / m.a.coerceIn(.2f, 5f)
    }
    val indent = imgW + SIDE_GAP
    var rem = (imgH + (if (b.ty == "hr") 0f else fs * 1.1f)) * d
    val out = ArrayList<Pair<Blk, FSpec>>()
    var j = i + 1
    while (j < ed.blocks.size && rem > 0f) {
        val x = ed.blocks[j]
        val bm = blockBM(x, pal, dm) ?: break
        val top = bm.top * d
        if (top >= rem) break
        val fsz = bm.style.fontSize.value
        val isLi = x.ty == "li"
        val mwLi = if (isLi) (when (x.lt) { "o", "a" -> 2.2f; "c" -> 1.9f; else -> 1.7f } * fs) else 0f
        // inden kiri / kanan paragraf ikut dihitung: kolom teks mulai di base0 (dp dari tepi kiri) dan berakhir di W - irf
        val (ilf, irf) = fitInd(x.il, x.ir, ed.widthDp)
        val base0 = (if (isLi) x.d * 1.4f * fs else 0f) + ilf
        val startX = if (left) maxOf(indent, base0) else base0
        val endX = if (left) ed.widthDp - irf else ed.widthDp - maxOf(indent, irf)
        val gapDp = startX - base0   // geseran baris sempit ke kanan gambar (gambar di kiri); 0 bila kolom sudah di kanan gambar
        val avail = ((endX - startX - mwLi - 4f) * d).toInt().coerceAtLeast(1)
        // inden baris pertama / menggantung dipakai juga saat mengukur, jadi pemenggalan baris sama dengan tampilan akhir
        val fiDp = x.fi.coerceIn(-IND_MAX_FI, IND_MAX_FI)
        val mst = if (fiDp != 0) bm.style.copy(
            textIndent = TextIndent(with(dn) { maxOf(fiDp, 0).dp.toSp() }, with(dn) { maxOf(-fiDp, 0).dp.toSp() })
        ) else bm.style
        val res = tm.measure(styled(x.r, x.r.t, 0, pal, fsz), mst, constraints = Constraints(maxWidth = avail))
        val h = maxOf(res.size.height.toFloat(), with(dn) { bm.style.lineHeight.toPx() })
        val fits = top + h <= rem
        var fold: Fold? = null
        var mk = 0f
        if (!fits && (x.ty == "p" || x.ty == "li") && x.r.t.indexOf('\n') < 0) {
            var k = 0
            while (k < res.lineCount && top + res.getLineTop(k) < rem) k++
            if (k in 1 until res.lineCount) {
                val cuts = IntArray(k) { res.getLineEnd(it) }
                val em = tm.measure("\u2003", bm.style).size.width.toFloat().let { if (it < 1f) fsz * d else it }
                val first = if (fiDp > 0) fiDp.toFloat() else 0f
                val hang = if (fiDp < 0) -fiDp.toFloat() else 0f
                // geseran tiap baris (dp): baris sempit = geseran gambar + inden; baris pertama setelah pemotongan terakhir = inden menggantung
                val pn = IntArray(k + 1)
                val pl = FloatArray(k + 1)
                for (jj in 0..k) {
                    val offDp = if (jj < k) gapDp + (if (jj == 0) first else hang) else hang
                    val offPx = offDp * d
                    if (offPx >= 1f) {
                        val n = (offPx / em).roundToInt().coerceIn(1, 40)
                        pn[jj] = n
                        pl[jj] = with(dn) { ((offPx - n * em) / n).toSp() }.value
                    }
                }
                mk = if (left) gapDp else 0f
                fold = Fold(cuts, pn, pl, if (!left) maxOf(0f, indent - irf) * d else 0f)
            }
        }
        val narrow = fold == null
        out.add(x to FSpec(if (narrow && left) gapDp else 0f, if (narrow && !left) maxOf(0f, indent - irf) else 0f, 0f, fold, mk))
        j++
        if (!fits) { rem = 0f; break }
        rem -= top + h + bm.bottom * d
    }
    if (out.isNotEmpty() && rem > 0f) out.last().second.extra = rem / d
    return FloatPlan(out)
}

/** Rencana untuk semua gambar mengambang (kiri/kanan dan cukup ruang) di catatan ini. */
@Composable
fun floatPlans(ed: Ed, dm: Dm): Map<String, FloatPlan> {
    val pal = LocalPal.current
    val tm = rememberTextMeasurer()
    val dn = LocalDensity.current
    var out: HashMap<String, FloatPlan>? = null
    val bl = ed.blocks
    for ((i, b) in bl.withIndex()) {
        // semua gambar tunggal rata kiri/kanan selalu lewat FloatGroup, walau tak ada teks di sampingnya,
        // supaya komponen gambar tidak berganti (dan gestur ubah ukuran tidak putus) saat teks samping habis
        if (b.ty == "tbl") {
            // semua tabel lewat TblFloatGroup (komposisi tidak berganti saat lebar kolom diseret melewati batas);
            // teks hanya mengalir di sampingnya bila tabel cukup sempit
            if (out == null) out = HashMap()
            out[b.id] = if (ed.tblSideOk(b)) planFloat(ed, i, dm, pal, tm, dn) else FloatPlan(emptyList())
            continue
        }
        if (b.isBox) {
            // semua kutipan / kode / garis (kiri, tengah, kanan) selalu lewat BoxFloatGroup: komposisi tidak berganti saat letak / lebar
            // berubah (juga saat diseret), jadi gestur sentuh tidak putus. Tengah = rencana kosong (tidak ada teks yang mengalir).
            if (out == null) out = HashMap()
            out[b.id] = if (ed.boxSideOk(b)) planFloat(ed, i, dm, pal, tm, dn) else FloatPlan(emptyList())
            continue
        }
        if (b.ty != "img" || b.im.size != 1) continue   // tengah juga lewat FloatGroup (rencana kosong): komposisi tidak berganti saat letak berubah
        val p = if (ed.sideOk(b)) planFloat(ed, i, dm, pal, tm, dn) else FloatPlan(emptyList())
        if (out == null) out = HashMap()
        out[b.id] = p
    }
    return out ?: emptyMap()
}

/** Membal tipis saat objek baru mendarat (a: sisa waktu pendaratan 1 -> 0). Mulai setelah luncuran masuk lubang, lalu mereda: +2,4% lalu -1% lalu diam. */
private fun landPop(a: Float): Float {
    val q = ((1f - a) - .3f).coerceAtLeast(0f) / .7f
    return 1f + kotlin.math.sin(q * 7.5f) * (1f - q) * .03f
}

/** Layer objek yang diseret / baru dilepas: terangkat mengikuti jari, lalu meluncur ke lubangnya (teks di sampingnya tetap di tempat). */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.liftLayer(ed: Ed, id: String, lifted: Boolean, slideDp: Float) {
    if (lifted) {
        translationX = ed.liftX(); translationY = ed.liftY()
        scaleX = ed.liftSc; scaleY = ed.liftSc; rotationZ = ed.liftRot
    } else {
        translationX = slideDp * density
        var sc = 1f
        val fp = ed.flipP
        if (fp != 0f && ed.flipId == id) {
            val now = ed.bpos[id]?.get(0)
            val from = ed.flipFrom[id]
            if (now != null && from != null) translationY = (from - now) * fp
            translationX += ed.flipDx * fp
            sc = 1f + (ed.flipSc - 1f) * fp
            rotationZ = ed.flipRot * fp
        }
        if (ed.landA > 0f && ed.landId == id) sc *= landPop(ed.landA)
        if (sc != 1f) { scaleX = sc; scaleY = sc }
    }
}

/**
 * Geseran horizontal paragraf (dp) yang meluruh ke 0 saat inset kirinya berubah (objek pindah sisi / paragraf masuk atau keluar kelompok).
 * Tata letak langsung ke hasil akhir (tanpa hitung ulang tiap frame); yang beranimasi hanya gambarnya, jadi ringan dan stabil.
 */
@Composable
private fun rememberInsetSlide(ed: Ed, key: String, ps: Float): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val h = remember(key) { floatArrayOf(if (ed.animReflow) (ed.padMemo[key] ?: 0f) else ps) }
    val a = remember(key, ps) {
        val from = h[0]
        h[0] = ps
        Animatable(if (ed.animReflow) from - ps else 0f)
    }
    SideEffect { ed.padMemo[key] = ps }
    LaunchedEffect(a) { if (a.value != 0f) a.animateTo(0f, spring(.9f, 260f)) }
    return a
}

/** Geseran horizontal objek (dp) saat letaknya berganti kiri / tengah / kanan lewat tombol: meluncur ke tempat barunya. */
@Composable
private fun rememberAlSlide(ed: Ed, al: String, objW: Float): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val h = remember { arrayOf(al) }
    val a = remember(al) {
        val from = h[0]
        h[0] = al
        val w = ed.widthDp
        fun xo(s: String): Float = when (s) { "l" -> 0f; "r" -> w - objW; else -> (w - objW) / 2f }
        Animatable(if (from != al && ed.mv == null) xo(from) - xo(al) else 0f)
    }
    LaunchedEffect(a) { if (a.value != 0f) a.animateTo(0f, spring(.88f, 300f)) }
    return a
}

/** Satu paragraf yang mengalir di samping objek. Geseran kiri/kanan, lebar kolom, dan posisi vertikalnya berubah mulus saat objek dipindah. */
@Composable
private fun FloatPart(ed: Ed, vm: AppVM, headId: String, x: Blk, sp: FSpec, od: Map<String, Int>, dm: Dm) {
    val slide = rememberInsetSlide(ed, x.id, sp.ps)
    val dn = LocalDensity.current.density
    Box(
        Modifier
            .padding(start = sp.ps.dp, end = sp.pe.dp, bottom = sp.extra.dp)
            .onGloballyPositioned { c ->
                val cc = ed.contentCoords
                if (cc != null && cc.isAttached && c.isAttached) {
                    ed.setPos(x.id, cc.localPositionOf(c, Offset.Zero).y, c.size.height.toFloat())
                }
            }
            .drawWithContent {
                // luncuran vertikal relatif terhadap kelompoknya (kelompok sendiri sudah meluncur lewat slotnya) + geseran samping
                val fp = ed.flipP
                var dy = 0f
                if (fp != 0f) {
                    val pn = ed.bpos[x.id]?.get(0)
                    val pf = ed.flipFrom[x.id]
                    if (pn != null && pf != null) dy = (pf - pn) * fp
                    if (ed.mv?.bid != headId && ed.flipId != headId) {
                        val hn = ed.bpos[headId]?.get(0)
                        val hf = ed.flipFrom[headId]
                        if (hn != null && hf != null) dy -= (hf - hn) * fp
                    }
                }
                val dx = slide.value * dn
                if (dy != 0f || dx != 0f) {
                    drawContext.canvas.save()
                    drawContext.transform.translate(dx, dy)
                    drawContent()
                    drawContext.canvas.restore()
                } else drawContent()
            }
    ) { BlockView(ed, vm, x, od[x.id] ?: 1, dm, sp.fold, sp.mk) }
}

/** Gambar di kiri/kanan dengan paragraf sesudahnya mengalir di sampingnya, lalu lanjut selebar penuh di bawahnya. */
@Composable
fun FloatGroup(ed: Ed, vm: AppVM, b: Blk, plan: FloatPlan, od: Map<String, Int>, dm: Dm) {
    if (b.ty == "tbl") { TblFloatGroup(ed, vm, b, plan, od, dm); return }
    if (b.isBox) { BoxFloatGroup(ed, vm, b, plan, od, dm); return }
    val m = b.im[0]
    val sel = ed.selImg?.takeIf { it.first == b.id }?.second
    val bottom = (dm.fs * 1.1f).dp
    val imgW = (ed.widthDp * m.w.coerceIn(.2f, 1f)).dp
    val ratio = m.a.coerceIn(.2f, 5f)
    val lifted = ed.mv?.let { it.bid == b.id && !it.row } == true
    val slide = rememberAlSlide(ed, m.al, imgW.value)
    Box(Modifier.fillMaxWidth().heightIn(min = imgW / ratio + bottom)) {
        Column(Modifier.fillMaxWidth()) {
            plan.parts.forEach { (x, sp) ->
                key(x.id) { FloatPart(ed, vm, b.id, x, sp, od, dm) }
            }
        }
        Box(
            Modifier.align(when (m.al) { "r" -> Alignment.TopEnd; "c" -> Alignment.TopCenter; else -> Alignment.TopStart })
                .zIndex(if (lifted) 3f else 1f)
                .graphicsLayer { liftLayer(ed, b.id, lifted, slide.value) }
        ) { ImgCell(ed, vm, b, 0, Modifier.width(imgW).aspectRatio(ratio), sel == 0) }
    }
}

/** Tabel di kiri/kanan dengan paragraf sesudahnya mengalir di sampingnya (sama seperti gambar mengambang). */
@Composable
private fun TblFloatGroup(ed: Ed, vm: AppVM, b: Blk, plan: FloatPlan, od: Map<String, Int>, dm: Dm) {
    val pal = LocalPal.current
    val narrow = ed.tblSideOk(b)
    val tw = (tblWidths(b, ed.widthDp).sum() + 3f).dp
    val lifted = ed.mv?.let { it.bid == b.id } == true
    val slide = rememberAlSlide(ed, b.alOf(), tw.value)
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            plan.parts.forEach { (x, sp) ->
                key(x.id) { FloatPart(ed, vm, b.id, x, sp, od, dm) }
            }
        }
        Box(
            Modifier.align(when (b.alOf()) { "r" -> Alignment.TopEnd; "c" -> Alignment.TopCenter; else -> Alignment.TopStart })
                .zIndex(if (lifted) 3f else 1f)
                .graphicsLayer { liftLayer(ed, b.id, lifted, slide.value) }
        ) { TableView(ed, b, dm, pal, if (narrow || (b.alOf() != "l" && tw.value < ed.widthDp)) Modifier.width(tw) else Modifier.fillMaxWidth()) }
    }
}

/** Kutipan / kode / garis pemisah di kiri / kanan dengan paragraf sesudahnya mengalir di sampingnya (sama seperti foto). */
@Composable
private fun BoxFloatGroup(ed: Ed, vm: AppVM, b: Blk, plan: FloatPlan, od: Map<String, Int>, dm: Dm) {
    val pal = LocalPal.current
    val bottom = if (b.ty == "hr") 0f else dm.fs * 1.1f
    val hDp = ed.objH[b.id] ?: 0f
    val w = (ed.widthDp * b.bw.coerceIn(.2f, 1f)).dp
    val lifted = ed.mv?.let { it.bid == b.id } == true
    val al = b.alOf()
    val ctr = al == "c"
    val full = b.bw >= .999f
    val (il, ir) = if (ctr && full && b.ty == "q") fitInd(b.il, b.ir, ed.widthDp) else 0f to 0f
    val slide = rememberAlSlide(ed, al, w.value)
    Box(Modifier.fillMaxWidth().heightIn(min = (hDp + bottom).dp)) {
        Column(Modifier.fillMaxWidth()) {
            plan.parts.forEach { (x, sp) ->
                key(x.id) { FloatPart(ed, vm, b.id, x, sp, od, dm) }
            }
        }
        Box(
            Modifier.align(when (al) { "r" -> Alignment.TopEnd; "c" -> Alignment.TopCenter; else -> Alignment.TopStart })
                .zIndex(if (lifted) 3f else 1f)
                .graphicsLayer { liftLayer(ed, b.id, lifted, slide.value) }
                .padding(start = il.dp, end = ir.dp, bottom = if (ctr) bottom.dp else 0.dp)
        ) { BoxCell(ed, b, dm, pal, if (ctr && full) Modifier.fillMaxWidth() else Modifier.width(w)) }
    }
}

/** Kutipan / kode / garis yang tidak mengambang: satu baris penuh, diletakkan kiri / tengah / kanan sesuai lebarnya. */
@Composable
private fun BoxBlock(ed: Ed, b: Blk, dm: Dm, pal: Pal) {
    val bottom = (if (b.ty == "hr") 0f else dm.fs * 1.1f).dp
    val full = b.bw >= .999f
    val (il, ir) = if (full && b.ty == "q") fitInd(b.il, b.ir, ed.widthDp) else 0f to 0f
    // satu pemanggilan BoxCell saja (tanpa cabang), supaya gestur ubah lebar tidak putus saat lebar menyentuh penuh
    Box(
        Modifier.fillMaxWidth().padding(start = il.dp, end = ir.dp, bottom = bottom),
        contentAlignment = when (b.alOf()) { "l" -> Alignment.TopStart; "r" -> Alignment.TopEnd; else -> Alignment.TopCenter }
    ) { BoxCell(ed, b, dm, pal, if (full) Modifier.fillMaxWidth() else Modifier.width((ed.widthDp * b.bw.coerceIn(.2f, 1f)).dp)) }
}

/**
 * Satu kutipan / kode / garis lengkap. Tanpa pegangan khusus: tekan-tahan di mana pun pada bentuknya = angkat dan pindahkan (seperti foto),
 * juga di mode baca. Ketuk pertama = objek terpilih (gagang tepi untuk lebar; teks belum terbuka). Ketuk kedua di teksnya = mulai mengedit;
 * selama teks diedit, tekan-tahan tepat di atas huruf tetap seleksi teks dan di luar huruf tetap mengangkat.
 */
@Composable
private fun BoxCell(ed: Ed, b: Blk, dm: Dm, pal: Pal, modifier: Modifier) {
    val dn = LocalDensity.current
    val bS by rememberUpdatedState(b)
    val hr = b.ty == "hr"
    val focusedHere by remember(b.id) {
        androidx.compose.runtime.derivedStateOf { val f = ed.focus; f != null && keyBlock(f) == b.id }
    }
    val lifted = ed.mv?.let { it.bid == b.id } == true
    val edit = !ed.reading && !ed.ink.on
    val al = b.alOf()
    val armed = ed.selObj == b.id || focusedHere
    val rs = remember { floatArrayOf(0f, 0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier.onSizeChanged { box = it; ed.setObjH(bS.id, with(dn) { it.height.toDp().value }) }
            .objLift(
                ed, b.id, focusedHere && !hr, textKeys = { listOf(bS.id) },
                skip = { pos ->
                    // gagang lebar (tepi, tengah vertikal) tampil hanya saat terpilih: sentuhan di sana milik gagang, bukan angkat
                    if (!(edit && armed && !lifted)) false
                    else {
                        val right = bS.alOf() != "r"
                        val gw = 32f * dn.density
                        val inX = if (right) pos.x > box.width - gw else pos.x < gw
                        inX && kotlin.math.abs(pos.y - box.height / 2f) < minOf(box.height / 2f, 48f * dn.density)
                    }
                }
            )
    ) {
        BoxBody(
            ed, b, dm, pal,
            Modifier.fillMaxWidth().then(
                if (hr && edit) Modifier.pointerInput(Unit) { detectTapGestures(onTap = { ed.selectObj(bS.id) }) }
                else if (ed.reading) Modifier.pointerInput(true) {
                    detectTapGestures(onTap = { if (!ed.ink.on) ed.tapObj(bS.id) })   // mode baca: seluruh blok bisa diketuk
                } else Modifier
            ),
            sel = edit && armed && !lifted   // saat diangkat tanpa bingkai pilihan: bingkai ikut terpangkas layer dan tampak jelek
        )
        // belum terpilih: lapisan ketuk menahan sentuhan supaya tidak langsung masuk ke teks (gulir tetap lewat)
        if (!hr && edit && !armed && !lifted) {
            Box(Modifier.matchParentSize().pointerInput(b.id) { detectTapGestures(onTap = { ed.selectObj(bS.id) }) })
        }
        if (edit && !lifted && armed) {
            val right = al != "r"
            val gx = if (right) 1f else -1f
            val kk = if (al == "c") 2f else 1f
            Grip(
                Modifier.align(if (right) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(32.dp).fillMaxHeight().heightIn(max = 96.dp),
                0, if (right) Alignment.CenterEnd else Alignment.CenterStart,
                onStart = { ed.commit(); rs[0] = ed.widthDp * dn.density * bS.bw.coerceIn(.2f, 1f); rs[1] = 0f },
                onDrag = { dx, _ ->
                    rs[1] += dx * gx * kk
                    val cw = ed.widthDp * dn.density
                    val minF = if (bS.ty == "hr") .2f else (150f / ed.widthDp.coerceAtLeast(150f)).coerceIn(.3f, .6f)
                    var f = ((rs[0] + rs[1]) / cw).coerceIn(minF, 1f)
                    if (f > .94f) f = 1f
                    ed.boxLive(bS.id, f)
                },
                onEnd = { ed.commit() }
            )
        }
    }
}

/** Cincin tipis di luar bentuk objek yang sedang dipilih (kutipan / kode / garis), setara garis luar tabel yang aktif. */
private fun Modifier.selRing(on: Boolean, shape: Shape, color: Color): Modifier = if (!on) this else drawBehind {
    val g = 3.dp.toPx()
    val o = shape.createOutline(Size(size.width + 2 * g, size.height + 2 * g), layoutDirection, this)
    if (o is androidx.compose.ui.graphics.Outline.Rounded) {
        val p = androidx.compose.ui.graphics.Path().apply { addRoundRect(o.roundRect) }
        translate(-g, -g) { drawPath(p, color, style = Stroke(1.5.dp.toPx())) }
    }
}

/** Isi kutipan / kode / garis tanpa letak dan pegangan. Lebarnya ditentukan pemanggil lewat [modifier]. */
@Composable
private fun BoxBody(ed: Ed, b: Blk, dm: Dm, pal: Pal, modifier: Modifier, sel: Boolean = false) {
    val fs = dm.fs
    val ring = pal.p.copy(alpha = .75f)
    when (b.ty) {
        "q" -> {
            val q = qs(b.k, pal)
            Box(
                modifier.selRing(sel, q.shape, ring).clip(q.shape).background(q.bg)
                    .then(if (q.border) Modifier.border(3.dp, pal.p, q.shape) else Modifier)
                    .drawBehind { if (q.bar) drawRect(pal.owc, size = Size(7.dp.toPx(), size.height)) }
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Column {
                    if (q.label != null) {
                        T(
                            q.label.uppercase(), fs * .7f * 1.05f, 900, if (b.k == "def") pal.p else q.fg,
                            Modifier.padding(bottom = (fs * .45f * .7f).dp), ls = .12f, alpha = if (b.k == "def") 1f else .85f
                        )
                    }
                    TF(ed, b.id, baseStyle(pal, dm, q.fg, fs * 1.05f, 500).aligned(b), Modifier.fillMaxWidth())
                }
            }
        }
        "code" -> {
            Box(modifier.selRing(sel, RoundedCornerShape(28.dp), ring).clip(RoundedCornerShape(28.dp)).background(pal.c3).padding(20.dp)) {
                Column {
                    if (b.l.isNotEmpty()) {
                        T(
                            b.l.uppercase(), 12f, 900, pal.op,
                            Modifier.padding(bottom = 14.dp).clip(RoundedCornerShape(12.dp)).background(pal.p)
                                .padding(horizontal = 12.dp, vertical = 4.dp), ls = .1f
                        )
                    }
                    HBox(Modifier.horizontalScroll(rememberScrollState())) {
                        TF(
                            ed, b.id,
                            TextStyle(
                                color = pal.os, fontFamily = FontFamily.Monospace, fontSize = (fs * .82f).sp,
                                lineHeight = (fs * .82f * 1.55f).sp
                            ),
                            Modifier.widthIn(min = if (b.bw >= .999f) 240.dp else 100.dp), wrap = false
                        )
                    }
                }
            }
        }
        else -> Box(modifier.padding(vertical = (fs * .9f).dp).height(8.dp).selRing(sel, RoundedCornerShape(4.dp), ring).clip(RoundedCornerShape(4.dp)).background(pal.sc))
    }
}

/** Pemisah tipis antar kelompok tombol di dalam kapsul. */
@Composable
fun CapDiv() {
    val pal = LocalPal.current
    Box(Modifier.padding(horizontal = 3.dp).width(2.dp).height(22.dp).clip(CircleShape).background(pal.ol.copy(alpha = .45f)))
}

/**
 * Wadah kapsul alat objek sisipan (gambar / tabel / kutipan / kode / garis), gaya M3 Expressive: pil penuh melayang di atas bilah,
 * lebarnya mengalir dengan pegas saat isinya berubah. Gerak masuk / keluarnya dikerjakan pemanggil (DocBar) lewat pegas;
 * tombol di dalamnya memantul masuk berurutan (lihat [CapBtn]).
 */
@Composable
fun CapShell(modifier: Modifier = Modifier, scroll: Boolean = false, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val pal = LocalPal.current
    val cap = RoundedCornerShape(34.dp)
    val ss = rememberScrollState()
    Row(
        modifier.shadow(if (pal.dark) 8.dp else 5.dp, cap).clip(cap).background(pal.c3)
            .border(1.dp, pal.ol.copy(alpha = .22f), cap)
            .animateContentSize(spring(.8f, 420f))
            .then(if (scroll) Modifier.horizontalScroll(ss) else Modifier)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}

/**
 * Tombol kapsul (area sentuh 48dp, ramah jempol). Memantul masuk berurutan menurut [i]; saat ditekan menyempit lebih dari menipis
 * dan sudutnya membulat penuh, lepas = membal. tone: 0 biasa, 1 bahaya (hapus), 2 utama (selesai).
 */
@Composable
fun CapBtn(
    i: Int, on: Boolean = false, tone: Int = 0, enabled: Boolean = true, wide: Boolean = false,
    onClick: () -> Unit, content: @Composable (Color) -> Unit
) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else 24.dp, spring(.5f, 700f), label = "cbr")
    val sx by animateFloatAsState(if (pr) .84f else 1f, spring(.42f, 620f), label = "cbx")
    val sy by animateFloatAsState(if (pr) .92f else 1f, spring(.42f, 620f), label = "cby")
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(50L + i * 34L); pop.animateTo(1f, spring(.5f, 520f)) }
    val bgT = when (tone) { 1 -> pal.ec; 2 -> pal.p; else -> if (on) pal.p else pal.p.copy(alpha = 0f) }
    val fgT = when (tone) { 1 -> pal.oec; 2 -> pal.op; else -> if (on) pal.op else pal.os }
    val bg by androidx.compose.animation.animateColorAsState(bgT, tween(200, easing = Emph), label = "cbb")
    val fg by androidx.compose.animation.animateColorAsState(fgT, tween(200, easing = Emph), label = "cbf")
    Box(
        Modifier.size(if (wide) 58.dp else 48.dp, 48.dp)
            .graphicsLayer { val p = pop.value.coerceAtLeast(0f); scaleX = p * sx; scaleY = p * sy; alpha = if (enabled) 1f else .3f }
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        Alignment.Center
    ) { content(fg) }
}

/**
 * Kelompok pilihan (mis. rata kiri / tengah / kanan): SATU penanda yang meluncur dan melar mengikuti pilihan dengan pegas memantul,
 * bukan tiap tombol berganti warna sendiri. sel = -1: tidak ada yang terpilih.
 */
@Composable
fun AlignSeg(i0: Int, sel: Int, icons: List<@Composable (Color) -> Unit>, onPick: (Int) -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val cw = 48.dp
    val chh = 44.dp
    val x by animateFloatAsState(sel.toFloat(), spring(.58f, 380f), label = "segx")
    val dist = if (sel < 0) 0f else abs(sel - x).coerceAtMost(1f)
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(50L + i0 * 34L); pop.animateTo(1f, spring(.55f, 480f)) }
    Box(
        Modifier.graphicsLayer { val p = pop.value.coerceAtLeast(0f); scaleX = p; scaleY = p }
            .clip(CircleShape).background(pal.c4).padding(3.dp)
    ) {
        if (sel >= 0) Box(
            Modifier.offset { IntOffset((x * cw.toPx() - cw.toPx() * .38f * dist / 2f).roundToInt(), 0) }
                .width(cw * (1f + .38f * dist)).height(chh).clip(CircleShape).background(pal.p)
        )
        Row {
            icons.forEachIndexed { k, ic ->
                val fg by androidx.compose.animation.animateColorAsState(if (sel == k) pal.op else pal.os, tween(180, easing = Emph), label = "segc")
                Box(
                    Modifier.size(cw, chh).clip(CircleShape).clickable {
                        if (sel != k) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(k)
                    },
                    Alignment.Center
                ) { ic(fg) }
            }
        }
    }
}

/** Chip di bilah alat tabel / kutipan: memantul masuk berurutan menurut [i], menyempit membal saat ditekan. */
@Composable
fun CapChip(i: Int, text: String, bg: Color, fg: Color, shape: Shape, h: Dp = 46.dp, minW: Dp = 0.dp, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pr) .9f else 1f, spring(.42f, 620f), label = "ccs")
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(40L + i * 38L); pop.animateTo(1f, spring(.52f, 500f)) }
    Box(
        Modifier.height(h).widthIn(min = minW)
            .graphicsLayer { val p = pop.value.coerceAtLeast(0f); scaleX = p * sc; scaleY = p * sc }
            .clip(shape).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp),
        Alignment.Center
    ) { T(text, 15f, 850, fg, ls = 0f) }
}

/** Kapsul alat objek sisipan (tabel / kutipan / kode / garis): rata kiri / tengah / kanan, hapus, selesai. */
@Composable
fun ObjCapsule(ed: Ed, ob: Blk) {
    val al = ob.alOf()
    Box(Modifier.fillMaxWidth(), Alignment.Center) {
        CapShell {
            AlignSeg(
                0, when (al) { "l" -> 0; "c" -> 1; "r" -> 2; else -> -1 },
                listOf<@Composable (Color) -> Unit>({ AlignArrow(0, it) }, { AlignArrow(1, it) }, { AlignArrow(2, it) })
            ) { ed.objAlign(ob.id, when (it) { 0 -> "l"; 1 -> "c"; else -> "r" }) }
            CapDiv()
            CapBtn(3, tone = 1, onClick = { ed.objDelete(ob.id) }) { Ic("del", it) }
            if (ed.selObj != null) CapBtn(4, tone = 2, wide = true, onClick = { ed.selObj = null }) { Ic("ok", it) }
        }
    }
}

/** Ikon panah rata objek: 0 = panah ke kiri + garis tepi kiri, 1 = dua panah menuju garis tengah, 2 = panah ke kanan + garis tepi kanan. */
@Composable
fun AlignArrow(kind: Int, c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val sw = 2.2.dp.toPx()
        val w = size.width; val h = size.height; val m = h / 2f; val hd = 4.dp.toPx()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float) = drawLine(c, Offset(x0, y0), Offset(x1, y1), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        fun arrow(x0: Float, x1: Float) {   // dari x0 menuju x1
            line(x0, m, x1, m)
            val d = if (x1 > x0) -1f else 1f
            line(x1, m, x1 + d * hd, m - hd); line(x1, m, x1 + d * hd, m + hd)
        }
        when (kind) {
            0 -> { line(3.dp.toPx(), 3.dp.toPx(), 3.dp.toPx(), h - 3.dp.toPx()); arrow(w - 3.dp.toPx(), 7.dp.toPx()) }
            2 -> { line(w - 3.dp.toPx(), 3.dp.toPx(), w - 3.dp.toPx(), h - 3.dp.toPx()); arrow(3.dp.toPx(), w - 7.dp.toPx()) }
            else -> { line(w / 2f, 3.dp.toPx(), w / 2f, h - 3.dp.toPx()); arrow(2.dp.toPx(), w / 2f - 3.dp.toPx()); arrow(w - 2.dp.toPx(), w / 2f + 3.dp.toPx()) }
        }
    }
}

// ---------- toolbar ----------
@Composable
fun Tbn(content: @Composable (Color) -> Unit, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else 23.dp, spring(.6f, 600f), label = "r")
    val sc by animateFloatAsState(if (pr) .88f else 1f, spring(.5f, 600f), label = "s")
    // status aktif berpindah dengan pudar warna (bukan kedip): latar dan ikon beranimasi bersama
    val fg by androidx.compose.animation.animateColorAsState(if (on) pal.op else pal.os, tween(190, easing = Emph), label = "tf")
    val bgc by androidx.compose.animation.animateColorAsState(if (on) pal.p else pal.p.copy(alpha = 0f), tween(190, easing = Emph), label = "tb")
    Box(
        Modifier.size(46.dp).graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else .28f }
            .clip(RoundedCornerShape(r)).background(bgc)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick),
        Alignment.Center
    ) { content(fg) }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color, shape: Shape, h: Dp = 46.dp, minW: Dp = 0.dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pr) .92f else 1f, spring(.5f, 600f), label = "c")
    Box(
        Modifier.height(h).widthIn(min = minW).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 18.dp),
        Alignment.Center
    ) { T(text, 15f, 850, fg, ls = 0f) }
}

@Composable
fun EdBar(ed: Ed, act: Actions, imeOpen: Boolean) {
    val pal = LocalPal.current
    val selMode = ed.selOn
    val key = if (selMode) null else ed.focus
    val blk = key?.let { ed.blockOfKey(it) }
    val cell = key != null && isCellKey(key)
    val fl = if (selMode) FMT.filter { ed.selAllFmt(it) }.toSet() else ed.activeFlags()
    val ty = blk?.ty
    val noI = ty == "code"
    val noB = noI || (ty == "h")
    val blkOff = cell || selMode
    val convOff = cell || ty == "img"   // teks samping gambar tidak bisa diubah jadi judul/daftar
    val capShape = RoundedCornerShape(30.dp)
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 10.dp)
    ) {
        // kapsul tambahan (gambar / tabel / kutipan): muncul dan hilang dengan animasi mulus
        val objNow = ed.selObj ?: blk?.takeIf { it.ty == "q" || it.ty == "code" }?.id
        var objLast by remember { mutableStateOf<String?>(null) }
        val kindNow = if (ed.miniBar) 0 else if (ed.selImg != null) 1 else if (cell) 2 else if (ed.pickQuote) 3 else if (objNow != null) 4 else 0
        var kindShown by remember { mutableStateOf(0) }
        var lastSel by remember { mutableStateOf<Pair<String, Int>?>(null) }
        SideEffect {
            if (kindNow != 0) kindShown = kindNow
            ed.selImg?.let { lastSel = it }
            objNow?.let { objLast = it }
        }
        val kind = if (kindNow != 0) kindNow else kindShown
        AnimatedVisibility(
            visible = kindNow != 0,
            enter = fadeIn(tween(220, easing = Emph)) + slideInVertically(tween(300, easing = Emph)) { it / 2 } +
                expandVertically(tween(300, easing = Emph)),
            exit = fadeOut(tween(140)) + slideOutVertically(tween(220, easing = Emph)) { it / 2 } +
                shrinkVertically(tween(240, easing = Emph))
        ) {
            Box(Modifier.padding(bottom = 8.dp)) {
                when (kind) {
                    1 -> {
                        val s = ed.selImg ?: lastSel
                        if (s != null) {
                            val b = ed.blocks.firstOrNull { it.id == s.first }
                            val inRow = (b?.im?.size ?: 1) > 1
                            // kapsul foto: tampilan sama dengan kapsul Ink / kapsul objek
                            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                                Row(
                                    Modifier.shadow(if (pal.dark) 6.dp else 2.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp))
                                        .background(pal.c3).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!inRow) {
                                        val al = b?.im?.getOrNull(s.second)?.al
                                        Tbn({ Ic("aL", it) }, al == "l", true) { ed.imgAlign("l") }
                                        Tbn({ Ic("aC", it) }, al == "c", true) { ed.imgAlign("c") }
                                        Tbn({ Ic("aR", it) }, al == "r", true) { ed.imgAlign("r") }
                                        CapDiv()
                                    }
                                    Tbn({ Ic(if (inRow) "rows" else "cols", it) }, false, true) { ed.toggleRow() }
                                    Tbn({ Ic("aup", it) }, false, true) { ed.imgMove(-1) }
                                    Tbn({ Ic("adn", it) }, false, true) { ed.imgMove(1) }
                                    CapDiv()
                                    Tbn({ Ic("del", it) }, false, true) { ed.imgDelete() }
                                    CapDiv()
                                    Tbn({ Ic("ok", it) }, false, true) { ed.closeImg() }
                                }
                            }
                        }
                    }
                    2 -> Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.c3)
                            .horizontalScroll(rememberScrollState()).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ar" to "+ Row", "ac" to "+ Column", "dr" to "− Row", "dc" to "− Column").forEach { (k, l) ->
                            Chip(l, pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.tOp(k) }
                        }
                        if (blk != null && blk.cw.isNotEmpty()) Chip("Reset widths", pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.tblResetCols(blk.id) }
                        Chip("Delete table", pal.ec, pal.oec, RoundedCornerShape(23.dp)) { ed.tOp("dt") }
                    }
                    4 -> {
                        val oid = objNow ?: objLast
                        val ob = ed.blocks.firstOrNull { it.id == oid }
                        if (ob != null) ObjCapsule(ed, ob)
                    }
                    3 -> Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.c3)
                            .horizontalScroll(rememberScrollState()).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VARS.forEach { (k, l) ->
                            val q = qs(k, pal)
                            Chip(l, if (k == "def") pal.c4 else q.bg, q.fg, q.shape, 52.dp, 104.dp) {
                                ed.pickQuote = false; ed.setQuote(k)
                            }
                        }
                        if (ty == "q") Chip("Plain text", pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.pickQuote = false; ed.setPlain() }
                    }
                }
            }
        }
        // satu kapsul: lebarnya mengecil ke kanan (tinggal dua tombol) dan mengembang lagi ke kiri
        val f by animateFloatAsState(if (ed.miniBar) 1f else 0f, tween(420, easing = Emph), label = "mini")
        val toolsScroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val full = maxWidth
            val expW = full * BAR_FRAC
            val miniW = 102.dp
            val w = expW + (miniW - expW) * f
            val endPad = (full - expW) / 2 * (1f - f)
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = endPad).width(w).height(56.dp)
                .shadow(if (pal.dark) 6.dp else 1.dp, capShape).clip(capShape)
                .background(pal.c4).padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().horizontalScroll(toolsScroll),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                @Composable fun I(id: String, icon: String, on: Boolean = false, off: Boolean = false, f: () -> Unit) =
                    Tbn({ Ic(icon, it) }, on, !off) { if (id != "quote") ed.pickQuote = false; f() }
                @Composable fun H(id: String, lv: Int) = Tbn(
                    { c ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            T("H", 19f, 1000, c); T(lv.toString(), 12f, 1000, c)
                        }
                    }, ty == "h" && blk?.lv == lv, !convOff
                ) { ed.pickQuote = false; ed.setHead(lv) }
                val isLi = ty == "li" && !cell
                I("undo", "undo", off = !ed.canUndo || selMode) { ed.undo() }
                I("redo", "redo", off = !ed.canRedo || selMode) { ed.redo() }
                Sep()
                H("h1", 1); H("h2", 2)
                I("b", "b", K.B in fl && !noB, noB) { ed.toggleFmt(K.B) }
                I("i", "i", K.I in fl && !noI, noI) { ed.toggleFmt(K.I) }
                I("u", "u", K.U in fl && !noI, noI) { ed.toggleFmt(K.U) }
                I("s", "s", K.S in fl && !noI, noI) { ed.toggleFmt(K.S) }
                I("mark", "mark", K.M in fl && !noI, noI) { ed.toggleFmt(K.M) }
                I("code", "code", K.C in fl && !noI, noI) { ed.toggleFmt(K.C) }
                Sep()
                I("ul", "ul", isLi && blk?.lt == "b", convOff) { ed.setList("b") }
                I("ol", "ol", isLi && blk?.lt == "o", convOff) { ed.setList("o") }
                Tbn({ LetterListIcon(it) }, isLi && blk?.lt == "a", !convOff) { ed.pickQuote = false; ed.setList("a") }
                I("chk", "chk", isLi && blk?.lt == "c", convOff) { ed.setList("c") }
                I("out", "out", off = !isLi) { ed.indent(-1) }
                I("ind", "ind", off = !isLi) { ed.indent(1) }
                Sep()
                I("quote", "quote", ty == "q" || ed.pickQuote, convOff) { if (ed.focus != null) ed.pickQuote = !ed.pickQuote }
                I("pre", "pre", ty == "code", convOff) { ed.setCode() }
                I("table", "table", off = blkOff) { ed.insTable() }
                I("img", "img", off = blkOff) { act.pickImages() }
                I("mic", "mic", ed.recActive, ed.recActive || selMode) {
                    act.requestMic {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        ed.startVoice()
                    }
                }
                I("hr", "hr", off = blkOff) { ed.insHr() }
            }
            Box(Modifier.width(10.dp * (1f - f)).clipToBounds().wrapContentWidth(Alignment.Start, unbounded = true)) { Sep() }
            // keyboard terbuka: ">" menciutkan kapsul ke kanan; "<" mengembangkannya lagi; keyboard tertutup: panah bawah = mode baca
            Tbn({ Ic("pen", it) }, false, !ed.reading) { ed.ink.toggle(ed) }
            Tbn({ Ic(if (ed.miniBar) "chl" else if (imeOpen) "chr" else "hide", it) }, false, true) {
                if (ed.selOn) ed.clearSel()
                else if (ed.miniBar) ed.miniBar = false
                else if (imeOpen) { ed.pickQuote = false; ed.miniBar = true }
                else ed.enterReading()
            }
            if (f > .01f) {
                Box(Modifier.width(46.dp * f).clipToBounds().wrapContentWidth(Alignment.Start, unbounded = true).graphicsLayer { alpha = f }) {
                    Tbn({ Ic("hide", it) }, false, ed.miniBar) { ed.enterReading() }
                }
            }
        }
        }
    }
}

/** Ikon daftar huruf: dua baris kecil "A —" dan "B —", sejajar dengan ikon daftar lain di toolbar. */
@Composable
fun LetterListIcon(c: Color) {
    Column(Modifier.size(24.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
        for (l in listOf("A", "B")) {
            Row(Modifier.fillMaxWidth().height(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(9.dp), Alignment.Center) { T(l, 10f, 1000, c, lh = 1f) }
                Box(Modifier.padding(start = 2.dp).weight(1f).height(2.5.dp).clip(RoundedCornerShape(1.dp)).background(c))
            }
        }
    }
}

@Composable
fun Sep() {
    val pal = LocalPal.current
    Box(Modifier.padding(horizontal = 4.dp).width(2.dp).height(24.dp).clip(RoundedCornerShape(1.dp)).background(pal.ol))
}

// ---------- penunjuk lepas (drop) ----------
/**
 * Bingkai tempat objek yang diseret AKAN mendarat. Tata letak di sekelilingnya (urutan, teks yang mengalir di samping objek)
 * sudah memakai hasil akhir yang sebenarnya, jadi bingkai ini menandai "lubang" yang benar-benar akan terisi: seukuran objek,
 * di letak kiri / tengah / kanan yang akan dipakai. Tanpa label, tanpa ikon: lubangnya sendiri sudah cukup jelas.
 *
 * Tidak ada fade sama sekali. Bingkai digambar di BELAKANG isi catatan, tepat di bawah objek sejak diangkat, jadi ia tersingkap
 * saat objek digeser menjauh. Muncul / hilang (mis. saat objek sedang di atas gambar lain untuk kolase) lewat ukuran: tumbuh /
 * menyusut dari pusatnya (gOn = skala 0..1). Pindah tempat = meluncur dengan pegas; denyut saat sasaran pindah = garis sesaat menebal.
 * Anggota kolase yang diseret keluar: garis tipis di celah tujuan.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.DropGhost(ed: Ed) {
    val alive by remember { androidx.compose.runtime.derivedStateOf { ed.mv != null || ed.gOn > 0f } }
    if (!alive) return
    val pal = LocalPal.current
    val line = ed.gLine
    Box(
        Modifier.matchParentSize().drawBehind {
            val k = ed.gOn
            if (k > .004f) {
                val w = ed.gw * k
                val h = ed.gHt * k
                val x = ed.gx + (ed.gw - w) / 2f
                val y = ed.gy + (ed.gHt - h) / 2f
                if (line) {
                    drawRoundRect(pal.p, Offset(x, y), Size(w, h), CornerRadius(h / 2f))
                } else {
                    val cr = CornerRadius(minOf(14.dp.toPx(), h / 2f))
                    drawRoundRect(pal.p.copy(alpha = .07f), Offset(x, y), Size(w, h), cr)
                    drawRoundRect(
                        pal.p.copy(alpha = .8f), Offset(x, y), Size(w, h), cr,
                        style = Stroke(
                            1.5.dp.toPx() + 1.5.dp.toPx() * ed.gPulse,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()), 0f)
                        )
                    )
                }
            }
        }
    )
}

/** Panah di tepi atas / bawah area catatan saat seretan berada di zona gulir otomatis: ukurannya mengikuti seberapa dalam jari di zona (makin cepat gulir, makin besar). Tanpa pita pudar. */
@Composable
fun EdgeGlow(ed: Ed) {
    val alive by remember { androidx.compose.runtime.derivedStateOf { ed.mv != null || ed.edgeV != 0f } }
    if (!alive) return
    val pal = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        for (top in listOf(true, false)) {
            Box(
                Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter).padding(vertical = 10.dp)
                    .graphicsLayer {
                        val d = (if (top) -ed.edgeV else ed.edgeV).coerceIn(0f, 1f)
                        scaleX = d; scaleY = d
                        translationY = (if (top) -1f else 1f) * kotlin.math.sin(ed.gT * 8f) * 2f * density
                    }
            ) { Ic(if (top) "aup" else "adn", pal.p, 28.dp) }
        }
    }
}

// ---------- slot blok (komposisi berjendela) ----------
private val NO_OD: Map<String, Int> = emptyMap()

/**
 * Satu slot di kolom isi catatan. Kotak luar SELALU ada (melaporkan posisi/tinggi); isi dalamnya hidup hanya bila [live],
 * selain itu diganti Spacer setinggi terakhir terukur (atau perkiraan). Pada catatan biasa [live] selalu true, jadi sama dengan sebelumnya.
 */
@Composable
private fun BlockSlot(ed: Ed, vm: AppVM, b: Blk, plan: FloatPlan?, ordN: Int, od: Map<String, Int>, dm: Dm, live: Boolean) {
    val pal = LocalPal.current
    val lifted = ed.mv?.let { it.bid == b.id && !it.row } == true
    val dragging = ed.mv?.bid == b.id
    val flying = ed.flipId == b.id
    val den = LocalDensity.current
    val outSlide = if (plan == null && ed.animReflow && (b.ty == "p" || b.ty == "li" || b.ty == "h")) rememberInsetSlide(ed, b.id, 0f) else null
    Box(
        Modifier
            .zIndex(if (dragging || flying) 3f else 0f)
            .onGloballyPositioned { c -> ed.setSlotPos(b.id, c.positionInParent().y, c.size.height.toFloat()) }
            .graphicsLayer {
                if (lifted && plan == null) {
                    translationX = ed.liftX(); translationY = ed.liftY()
                    scaleX = ed.liftSc; scaleY = ed.liftSc; rotationZ = ed.liftRot
                }
            }
            // Luncuran halus lewat gambar (bukan layer): posisi layout (bpos) tidak pernah tercemar, jadi penentuan sasaran tetap stabil.
            .drawWithContent {
                var dy = 0f
                var dx = 0f
                var sc = 1f
                var rot = 0f
                val fp = ed.flipP
                if (fp != 0f && !(lifted && plan == null) && ed.mv?.bid != b.id && !(plan != null && ed.flipId == b.id)) {
                    val now = ed.bpos[b.id]?.get(0)
                    val from = ed.flipFrom[b.id]
                    if (now != null && from != null) dy = (from - now) * fp
                    if (ed.flipId == b.id) { dx = ed.flipDx * fp; sc = 1f + (ed.flipSc - 1f) * fp; rot = ed.flipRot * fp }
                }
                val la = ed.landA
                if (plan == null && la > 0f && ed.landId == b.id) sc *= landPop(la)   // mendarat: membal tipis, bukan cincin yang memudar
                dx += (outSlide?.value ?: 0f) * den.density
                drawContext.canvas.save()
                drawContext.transform.translate(dx, dy)
                if (sc != 1f) drawContext.transform.scale(sc, sc, center)
                if (rot != 0f) drawContext.transform.rotate(rot, center)
                drawContent()
                drawContext.canvas.restore()
            }
    ) {
        if (live) {
            val blks = plan?.parts?.map { it.first }?.let { listOf(b) + it } ?: listOf(b)
            val cur by rememberUpdatedState(blks)
            DisposableEffect(b.id) { onDispose { ed.leaveSlot(cur, b.id) } }
            if (plan != null) FloatGroup(ed, vm, b, plan, od, dm)
            else BlockView(ed, vm, b, ordN, dm)
        } else {
            val h = ed.bpos[b.id]?.get(1) ?: ed.estimateSlot(b, plan, dm.fs, dm.lh)
            Spacer(Modifier.fillMaxWidth().height(with(den) { h.toDp() }))
        }
    }
}

// ---------- layar catatan ----------
@Composable
fun NoteScreen(vm: AppVM, ed: Ed, act: Actions) {
    val pal = LocalPal.current
    val ffNow = LocalFF.current
    val nfs = vm.noteFs(ed.noteId)
    val dm = remember(nfs, ffNow) { Dm(nfs.toFloat(), LH, ffNow) }   // instance stabil: blok tidak recompose tanpa sebab
    val scroll = rememberScrollState()
    InkSetup(ed)
    // Tombol back Android: tutup yang paling atas dulu. Mode ketik -> mode baca (tampilan penuh) -> baru keluar catatan.
    BackHandler(true) {
        when {
            ed.sheet != 0 && ed.sheetPage != 0 -> ed.sheetPage = 0
            ed.sheet != 0 -> ed.sheet = 0
            ed.sub != 0 -> ed.sub = 0
            ed.ink.on -> ed.ink.toggle(ed)
            ed.findOn -> ed.closeFind()
            ed.selOn -> ed.clearSel()
            !ed.reading -> ed.enterReading()
            else -> act.back()
        }
    }
    val focusMgr = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    LaunchedEffect(ed.clearReq) {
        if (ed.clearReq > 0) { focusMgr.clearFocus(); kb?.hide() }
    }
    val od = remember(ed.blocks) { ords(ed.blocks) }
    val plans = floatPlans(ed, dm)
    SideEffect {
        ed.folds.clear()
        plans.values.forEach { p -> p.parts.forEach { (x, s) -> s.fold?.let { ed.folds[x.id] = it } } }
    }
    // komposisi berjendela: himpunan slot yang hidup (hanya dipakai bila catatan sangat panjang)
    val winOn = ed.windowed
    val plansNow by rememberUpdatedState(plans)
    var liveSet by remember {
        mutableStateOf<Set<String>>(
            if (ed.windowed) androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { ed.computeLive(0, plans, dm.fs, dm.lh) } else emptySet()
        )
    }
    LaunchedEffect(winOn, dm) {
        if (!winOn) { liveSet = emptySet(); return@LaunchedEffect }
        androidx.compose.runtime.snapshotFlow { ed.computeLive(scroll.value, plansNow, dm.fs, dm.lh) }.collect { liveSet = it }
    }
    val titleFr = remember { FocusRequester() }
    val titleNote = vm.note(ed.noteId)
    val tBold = titleNote?.tbo ?: true
    val tItal = titleNote?.tit ?: false
    val tColor = titleColor(titleNote?.tcl ?: 0)
    LaunchedEffect(ed.titleReq) {
        if (ed.titleReq > 0) {
            delay(380)
            try { titleFr.requestFocus() } catch (e: Exception) { }
        }
    }

    @Composable
    fun TopBar() {
        // bilah cari menggantikan bilah biasa dengan geser + pudar (mode "cari dalam catatan")
        // pil pencarian tumbuh dari tombol titik tiga ke kiri dan menguncup kembali ke sana; tombol kembali satu-satunya
        // dan tidak pernah memudar, jadi tidak ada kedipan saat bilah berganti
        val fa by animateFloatAsState(if (ed.findOn) 1f else 0f, spring(.9f, 380f), label = "fa")
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DocTopLeft(ed, act)
                Spacer(Modifier.weight(1f))
                DocTopActions(ed)
                Row(Modifier.graphicsLayer {
                    val k = (((1f - fa) - .3f) / .7f).coerceIn(0f, 1f)
                    alpha = k; scaleX = .6f + .4f * k; scaleY = .6f + .4f * k
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, .5f)
                }) {
                    // pengingat: lonceng terisi bila sudah diatur, lonceng + bila belum (seperti Google Keep)
                    val hasRem = (vm.note(ed.noteId)?.rm ?: 0L) > 0L
                    IconBtn(if (hasRem) "bell" else "bellplus", if (hasRem) pal.p else pal.os, {
                        ed.flush(); focusMgr.clearFocus(); kb?.hide()
                        vm.remDlg = ed.noteId
                    }, enabled = !ed.findOn)
                    IconBtn("more", pal.os, {
                        ed.flush(); focusMgr.clearFocus(); kb?.hide()
                        vm.nav.push(Scr.NoteMenu(ed.noteId))
                    }, enabled = !ed.findOn)
                }
            }
            if (fa > .005f || ed.findOn) FindBar(ed, fa.coerceIn(0f, 1f))
        }
    }

    // judul ikut disorot saat mode cari dalam catatan
    val tFh = if (ed.findOn) ed.hitsOf("@t") else emptyList()
    val tFc = if (tFh.isEmpty()) null else ed.curHit()?.takeIf { it.key == "@t" }
    val titleVt = remember(tFh, tFc, pal) {
        if (tFh.isEmpty()) VisualTransformation.None
        else VisualTransformation { t ->
            TransformedText(buildAnnotatedString { append(t.text); addFind(pal, tFh, tFc, 0, t.text.length) }, OffsetMapping.Identity)
        }
    }

    // derivedStateOf: hanya berubah saat keyboard benar-benar buka/tutup, bukan tiap frame animasinya (dulu seluruh layar recompose per frame)
    val imeInsets = WindowInsets.ime
    val imeOpen by remember(density) { androidx.compose.runtime.derivedStateOf { imeInsets.getBottom(density) > 0 } }
    // tinggi keyboard yang stabil (px). Bukan state: hanya dibaca saat sheet dibuka, jadi tidak membuat NoteScreen recompose tiap frame keyboard.
    // Naik langsung mengikuti keyboard; turun baru diterapkan setelah tenang. Dibekukan selama sheet terbuka.
    val kbBox = remember { intArrayOf(0) }
    LaunchedEffect(imeInsets, density) {
        androidx.compose.runtime.snapshotFlow { imeInsets.getBottom(density) to ed.sheet }.collectLatest { (v, sh) ->
            if (sh != 0) return@collectLatest
            if (v > kbBox[0]) kbBox[0] = v
            else { delay(250); if (ed.sheet == 0) kbBox[0] = v }
        }
    }
    // Sheet terbuka => area catatan TIDAK lagi mengikuti keyboard (sheet menggantikan tempat keyboard), jadi layar tidak loncat saat keyboard turun.
    // Dibaca di fase ukur (bukan komposisi), jadi cukup re-layout, tidak recompose.
    val navInsets = WindowInsets.navigationBars
    val areaInsets = remember(imeInsets, navInsets, ed) {
        val base = imeInsets.union(navInsets)
        object : WindowInsets {
            override fun getLeft(density: androidx.compose.ui.unit.Density, layoutDirection: androidx.compose.ui.unit.LayoutDirection) = base.getLeft(density, layoutDirection)
            override fun getTop(density: androidx.compose.ui.unit.Density) = base.getTop(density)
            override fun getRight(density: androidx.compose.ui.unit.Density, layoutDirection: androidx.compose.ui.unit.LayoutDirection) = base.getRight(density, layoutDirection)
            override fun getBottom(density: androidx.compose.ui.unit.Density) = if (ed.sheet != 0) 0 else base.getBottom(density)
        }
    }
    val haptic = LocalHapticFeedback.current
    val hasAu = vm.note(ed.noteId)?.au?.isNotEmpty() == true
    // voice note baru: layar menggulir halus ke kartu rekaman di bawah catatan
    val auN = vm.note(ed.noteId)?.au?.size ?: 0
    val auSeen = remember { intArrayOf(auN) }
    val audBox = remember { floatArrayOf(0f, 0f) }   // [atas, tinggi] bagian rekaman di ruang gulir
    LaunchedEffect(auN) {
        if (auN > auSeen[0]) {
            delay(350)
            val m = 24f * ed.dens
            val want = audBox[0] + audBox[1] - (ed.viewH - ed.cursorClear()) + m
            if (audBox[1] > 0f && want > scroll.value + 1f) scroll.animateScrollTo(want.toInt().coerceIn(0, scroll.maxValue), tween(420, easing = Emph))
        }
        auSeen[0] = auN
    }
    SideEffect {
        ed.scroll = scroll
        ed.dens = density.density
        ed.buzz = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    // gulir otomatis ke kecocokan aktif bila belum terlihat
    val hitNow = if (ed.findOn) ed.curHit() else null
    LaunchedEffect(hitNow) {
        val h = hitNow ?: return@LaunchedEffect
        if (h.key == "@t") { scroll.animateScrollTo(0, tween(380, easing = Emph)); return@LaunchedEffect }
        // catatan berjendela: blok sasaran mungkin baru tersusun (lama) dan perkiraan tinggi bisa meleset, jadi tunggu lebih lama dan periksa ulang posisinya
        var fixes = 0
        repeat(if (ed.windowed) 30 else 8) {
            val lay = ed.lays[h.key]
            val pos = ed.kpos[h.key]
            if (lay != null && pos != null && lay.layoutInput.text.isNotEmpty()) {
                val o = ed.toLay(h.key, h.s).coerceIn(0, lay.layoutInput.text.length - 1)
                val r = lay.getBoundingBox(o)
                val y = pos.y + r.top
                val yb = pos.y + r.bottom
                val topV = scroll.value + 24f * ed.dens
                val botV = scroll.value + ed.viewH - 160f * ed.dens
                if (y < topV || yb > botV) {
                    scroll.animateScrollTo((y - ed.viewH * .3f).toInt().coerceIn(0, scroll.maxValue), tween(380, easing = Emph))
                    if (ed.windowed && fixes++ < 3) { delay(150); return@repeat }
                }
                return@LaunchedEffect
            }
            delay(50)
        }
    }

    // Gulir bawaan Compose (BringIntoView) untuk kolom catatan DIMATIKAN: dulu ia bersaing dengan gulir per-frame di bawah
    // (dua sistem menggulir untuk satu ketukan => loncat / ngegulir sendiri). Satu-satunya yang menggulir ke kursor
    // adalah Ed.cursorScrollDelta. Judul tetap memakai perilaku geser-seminimal lama.
    val defSpec = LocalBringIntoViewSpec.current
    val cursorSpec = remember {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = tween(120, easing = Emph)
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                if (!ed.titleFocus) return 0f
                val cs = containerSize - ed.cursorClear()
                val trail = offset + size
                return when {
                    size > cs -> 0f
                    offset >= 0f && trail <= cs -> 0f
                    offset < 0f -> offset
                    else -> trail - cs
                }
            }
        }
    }
    // kursor ikut naik bareng keyboard: selisih dihitung ulang saat kursor / teks / keyboard / kapsul berubah, tanpa delay & animasi
    LaunchedEffect(ed, scroll) {
        androidx.compose.runtime.snapshotFlow {
            val d = ed.cursorScrollDelta(androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { scroll.value })
            d to Any()
        }.collect { (d, _) -> if (d != 0f) ed.csApplied(d, scroll.dispatchRawDelta(d)) }
    }

    // sampul: lapisan di belakang bilah atas, ikut tergulir; judul turun supaya menimpa bagian bawah banner
    val coverNote = vm.note(ed.noteId)?.takeIf { it.cv.isNotEmpty() }
    val sat = satDp()
    val coverInset = if (coverNote == null) 0.dp
    else (LocalConfiguration.current.screenWidthDp.dp / EDIT_RATIO - sat - 64.dp - 72.dp - 48.dp).coerceAtLeast(0.dp)
    // Latar dan isi memudar sendiri-sendiri (noteFade), sedangkan sampul TIDAK ikut memudar: ia buram penuh sejak awal morph,
    // persis menimpa sampul kartu. Dulu seluruh isi (termasuk sampul) memudar sambil bayangan kartu juga memudar, dan dua lapisan
    // setengah-transparan yang identik itu bertumpuk menjadi ~75% sehingga sampul tampak pucat sesaat = "kedip".
    Box(Modifier.fillMaxSize()) {
    // latar: alpha digambar langsung (tanpa layer offscreen layar penuh yang dibuat ulang tiap frame morph)
    Box(Modifier.fillMaxSize().drawBehind { drawRect(pal.s, alpha = noteFade(vm)) })
    if (coverNote != null) EditorCover(vm, coverNote) { scroll.value }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = noteFade(vm) }) {
    Column(Modifier.fillMaxSize()) {
        TopBar()
        // area catatan berhenti tepat di atas keyboard / navbar; kapsul melayang di atasnya tanpa latar
        Box(
            Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(areaInsets)
                .onSizeChanged { ed.viewH = it.height.toFloat() }
                .clipToBounds()
                .inkPinch(ed, scroll)
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides cursorSpec, LocalHBiv provides defSpec) {
            Column(Modifier.fillMaxSize().inkZoom(ed).verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.widthIn(max = 700.dp).fillMaxWidth().padding(horizontal = 24.dp)
                        .onSizeChanged { ed.widthDp = with(density) { it.width.toDp().value } }
                        .onGloballyPositioned { ed.contentCoords = it }
                        .inkInput(ed, vm, scroll)
                        .holdDrag(
                            true,
                            onStart = { pos -> !ed.ink.on && !ed.ink.busy && ed.mv == null && ed.selLong(pos, pos.y - scroll.value) },
                            onMove = { pos, _ -> ed.selMove(pos, pos.y - scroll.value) },
                            onEnd = { ed.selEnd() },
                            lead = 70L
                        )
                        .drawWithContent { inkContent(ed.ink) }
                ) {
                    DropGhost(ed)
                    Column(Modifier.fillMaxWidth()) {
                        val firstText = { ed.blocks.firstOrNull { it.isText }?.let { ed.focusAt(it.id, 0) } }
                        // lencana label di atas judul; ketuk untuk membuka halaman label
                        val headNote = vm.note(ed.noteId)
                        val noteLb = headNote?.lb ?: emptyList()
                        val remOn = headNote != null && headNote.rm > 0L
                        Row(
                            Modifier.fillMaxWidth().animateContentSize(spring(.8f, 400f)).horizontalScroll(rememberScrollState())
                                .padding(top = if (noteLb.isEmpty() && !remOn) 0.dp else 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // lencana pengingat di depan lencana label; ketuk untuk mengubah atau menghapusnya
                            if (remOn && headNote != null) key("rem") {
                                ReminderBadge(Rem.stamp(headNote), Rem.overdue(headNote), headNote.rr.isNotEmpty()) {
                                    ed.flush(); focusMgr.clearFocus(); kb?.hide()
                                    vm.remDlg = ed.noteId
                                }
                            }
                            noteLb.forEach { n ->
                                key(n) {
                                    LabelBadge(n) {
                                        ed.flush(); focusMgr.clearFocus(); kb?.hide()
                                        vm.nav.push(Scr.NoteLabels(ed.noteId))
                                    }
                                }
                            }
                        }
                        Box(Modifier.padding(top = 48.dp + coverInset, bottom = 20.dp)) {
                            if (ed.title.isEmpty()) {
                                T("Title", 46f, if (tBold) 1000 else 400, pal.osv, alpha = .5f, ls = -.02f)
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = ed.title,
                                onValueChange = { v -> val nl = v.contains('\n'); ed.titleChanged(v); if (nl) firstText() },
                                readOnly = ed.reading,
                                visualTransformation = titleVt,
                                textStyle = TextStyle(
                                    color = tColor, fontFamily = dm.ff, fontSize = 46.sp, fontWeight = FontWeight(if (tBold) 1000 else 400),
                                    fontStyle = if (tItal) FontStyle.Italic else FontStyle.Normal,
                                    lineHeight = (46 * 1.1f).sp, letterSpacing = (-.02f).em
                                ),
                                cursorBrush = SolidColor(pal.p),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { firstText() }),
                                modifier = Modifier.fillMaxWidth().focusProperties { canFocus = !ed.ink.on }.focusRequester(titleFr)
                                    .onFocusChanged { ed.titleFocus = it.isFocused; if (it.isFocused) { ed.selImg = null; ed.selObj = null } }
                                    .then(
                                        if (ed.reading) Modifier.wordTap(
                                            onTap = { if (ed.selOn) ed.clearSel() else { ed.exitReading(); ed.titleReq++ } },
                                            onDouble = { if (ed.selOn) ed.clearSel() else { ed.exitReading(); ed.titleReq++ } }
                                        ) else Modifier
                                    )
                            )
                        }
                        for ((sb, splan) in ed.slotsOf(plans)) {
                            key(sb.id) {
                                BlockSlot(
                                    ed, vm, sb, splan,
                                    if (splan == null) (od[sb.id] ?: 1) else 1,
                                    if (splan == null) NO_OD else od,
                                    dm,
                                    !winOn || sb.id in liveSet
                                )
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().height(if (hasAu) 28.dp else 220.dp).pointerInput(ed.reading, ed.selOn) {
                                detectTapGestures {
                                    if (ed.selOn) ed.clearSel()
                                    else {
                                        val l = ed.blocks.lastOrNull { it.isText }
                                        if (l != null) ed.focusAt(l.id, l.r.t.length)
                                        else if (ed.reading) ed.exitReading()
                                    }
                                }
                            }
                        )
                    }
                    InkLayers(ed, vm, scroll)
                    if (ed.selOn) SelHandles(ed)
                    SelBubble(ed, vm, act) { scroll.value }   // selalu ada di komposisi supaya bisa animasi keluar; scroll dibaca lewat lambda (fase layout)
                }
                // kartu suara + transkrip: selalu di bawah isi catatan (editor dan mode baca), di luar area seleksi teks
                if (hasAu) {
                    Box(
                        Modifier.widthIn(max = 700.dp).fillMaxWidth().padding(horizontal = 24.dp)
                            .onGloballyPositioned { c -> audBox[0] = c.positionInParent().y; audBox[1] = c.size.height.toFloat() }
                    ) {
                        AudioSection(vm, ed.noteId, act)
                    }
                    Box(
                        Modifier.widthIn(max = 700.dp).fillMaxWidth().height(220.dp).pointerInput(ed.reading, ed.selOn) {
                            detectTapGestures {
                                if (ed.selOn) ed.clearSel()
                                else {
                                    val l = ed.blocks.lastOrNull { it.isText }
                                    if (l != null) ed.focusAt(l.id, l.r.t.length)
                                    else if (ed.reading) ed.exitReading()
                                }
                            }
                        }
                    )
                }
            }
            }
            EdgeGlow(ed)
            InkFrame(ed)
            BottomBars(ed, vm, act, (!ed.reading || ed.selEdit) && !ed.titleFocus && !ed.findOn && !ed.ink.on, imeOpen) { kbBox[0] }
            InkBar(ed, vm)
        }
    }
    }
    }
}

private class BubbleHold {
    var x = 0f
    var y = 0f
    var mode = -1          // 0 di atas seleksi, 1 di bawah, 2 menempel di area terlihat
    var has = false
    var edit = false
}

/**
 * Tombol gelembung seleksi = anggota grup tombol tersambung (M3 Expressive): ujung luar bulat penuh, sambungan antar tombol bersudut kecil.
 * Saat tetangga muncul / hilang, sudutnya ikut berubah bentuk dengan pegas. Ditekan = semua sudut membulat + menyempit membal.
 * tone: 0 tonal, 1 bahaya, 2 utama.
 */
@Composable
fun SelBtn(enabled: Boolean, first: Boolean, last: Boolean, tone: Int, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val full = 22.dp                          // ujung luar: bulat penuh (tinggi 44dp)
    val mid = if (pr) 22.dp else 14.dp       // sambungan antar tombol: cukup membulat supaya tiap tombol terbaca sebagai chip sendiri
    val sC by animateDpAsState(if (first) full else mid, spring(.6f, 520f), label = "ssc")
    val eC by animateDpAsState(if (last) full else mid, spring(.6f, 520f), label = "sec")
    val sc by animateFloatAsState(if (pr) .92f else 1f, spring(.45f, 650f), label = "ssx")
    val bg = when (tone) { 2 -> pal.p; 1 -> pal.ec; else -> pal.sc }
    val fg = when (tone) { 2 -> pal.op; 1 -> pal.oec; else -> pal.osc }
    Box(
        Modifier.height(44.dp).widthIn(min = 48.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(topStart = sC, bottomStart = sC, topEnd = eC, bottomEnd = eC)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 13.dp),
        Alignment.Center
    ) { content(fg) }
}

/**
 * Gelembung Potong / Salin / Tempel yang menempel di seleksi (di atasnya, atau di bawahnya bila tak muat).
 * Muncul/hilang dengan spring (membesar dari sisi yang menghadap seleksi, sedikit memantul), tidak berkedip
 * karena baru ditampilkan setelah ukurannya terukur, pindah atas/bawah digeser mulus, dan tombolnya
 * masuk/keluar dengan melebar-menyempit.
 */
@Composable
fun SelBubble(ed: Ed, vm: AppVM, act: Actions, scrollY: () -> Int) {
    val pal = LocalPal.current
    val dn = LocalDensity.current
    @Suppress("DEPRECATION")
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val hold = remember { BubbleHold() }
    // Salin lewat jalur mana pun (menu keyboard, tombol Salin Gboard, dll): isi clipboard diganti Markdown seleksinya
    val ctxClip = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(ed) {
        val cm = ctxClip.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val l = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            try {
                if (!ed.selOn) return@OnPrimaryClipChangedListener
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
                if (t != ed.selText()) return@OnPrimaryClipChangedListener
                val md = ed.selMd()
                if (md.isNotEmpty() && md != t) cm.setPrimaryClip(android.content.ClipData.newPlainText("Dinduy", md))
            } catch (_: Exception) {}
        }
        cm.addPrimaryClipChangedListener(l)
        onDispose { cm.removePrimaryClipChangedListener(l) }
    }
    var bs by remember { mutableStateOf(IntSize.Zero) }
    val vis = remember { Animatable(0f) }
    val corrX = remember { Animatable(0f) }
    val corrY = remember { Animatable(0f) }
    val span = if (ed.selOn) ed.selSpan() else null
    val r0 = span?.let { ed.ptRect(it.first) }
    val r1 = span?.let { ed.ptRect(it.second) }
    val live = ed.selOn && !ed.selDrag && span != null && r0 != null && r1 != null
    val measured = bs != IntSize.Zero
    // Posisi dihitung dari nilai scroll TERBARU. Fungsi ini dipanggil di fase layout (lambda offset di bawah), bukan di komposisi:
    // sebelumnya scroll dibaca saat komposisi, jadi tiap scroll cepat posisi bubble selalu telat satu frame dari isi
    // (bubble di ruang konten, harus digeser mengikuti scroll) dan terlihat bergetar / berkedip.
    // Hasil: (x, y, mode). Mode punya histeresis: kalau sisi lama (atas / bawah) masih muat, tetap di situ, supaya tidak bolak-balik di ambang.
    val calc: (Int) -> Triple<Float, Float, Int> = calc@{ s ->
        if (r0 == null || r1 == null) return@calc Triple(hold.x, hold.y, hold.mode)
        val gap = with(dn) { 10.dp.toPx() }
        val m = with(dn) { 8.dp.toPx() }
        val visTop = s + m
        val visBot = s + ed.viewH - (if (ed.barOn) ed.barH + with(dn) { 10.dp.toPx() } else 0f) - m
        val belowGap = with(dn) { 52.dp.toPx() }
        val aboveY = r0.top - bs.height - gap
        val belowY = r1.bottom + belowGap
        val canAbove = aboveY >= visTop && r0.top < visBot
        val canBelow = belowY + bs.height <= visBot && r1.bottom > visTop
        val md = when {
            hold.mode == 0 && canAbove -> 0
            hold.mode == 1 && canBelow -> 1
            canAbove -> 0
            canBelow -> 1
            else -> 2
        }
        val yRaw = when (md) {
            0 -> aboveY
            1 -> belowY
            else -> visTop + (visBot - visTop - bs.height).coerceAtLeast(0f) * .35f   // seleksi menutupi layar: tempel di area terlihat
        }
        val yy = yRaw.coerceIn(visTop, maxOf(visTop, visBot - bs.height))
        val maxX = ed.widthDp * dn.density
        val cx = if (md == 2) maxX / 2f else (r0.left + r1.left) / 2f
        val xx = (cx - bs.width / 2f).coerceIn(0f, (maxX - bs.width).coerceAtLeast(0f))
        Triple(xx, yy, md)
    }
    val calcNow by rememberUpdatedState(calc)
    // hanya mode yang ikut komposisi (berubah jarang); x / y mengikuti scroll di fase layout tanpa recompose
    val modeNow by remember { androidx.compose.runtime.derivedStateOf { calcNow(scrollY()).third } }
    var has = hold.has
    var edit = hold.edit
    var mode = hold.mode
    if (live && span != null && r0 != null && r1 != null) {
        has = span.first != span.second
        edit = ed.selEdit
        mode = modeNow
        // pindah atas <-> bawah: geser mulus dari posisi lama, bukan melompat
        val moved = measured && vis.value > .5f && hold.mode != -1 && hold.mode != mode
        val tgt = if (moved) calc(androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { scrollY() }) else null
        val sx = if (tgt != null) hold.x - tgt.first else 0f
        val sy = if (tgt != null) hold.y - tgt.second else 0f
        LaunchedEffect(mode) {
            if (sx != 0f || sy != 0f) {
                corrX.snapTo(sx); corrY.snapTo(sy)
                launch { corrX.animateTo(0f, spring(.78f, 420f)) }
                corrY.animateTo(0f, spring(.78f, 420f))
            }
        }
        val fm = mode; val fh = has; val fe = edit
        SideEffect { hold.mode = fm; hold.has = fh; hold.edit = fe }
    }
    // muncul hanya setelah ukuran terukur (tidak ada frame di posisi salah = tidak berkedip); hilang pakai spring tanpa pantulan
    LaunchedEffect(live && measured) {
        if (live && measured) vis.animateTo(1f, spring(.62f, 520f))
        else if (!live) { vis.animateTo(0f, spring(1f, 800f)); hold.mode = -1 }
    }
    if (!live && vis.value <= .001f) return
    val m0 = mode
    // tombol yang tampil, berurutan: Cut, Copy, Paste, Hapus, Select all. Bentuk sudut tiap tombol mengikuti posisinya di antara yang tampil.
    val flags = booleanArrayOf(edit && has, has, edit, edit && has, true)
    val fi = flags.indexOfFirst { it }
    val li = flags.indexOfLast { it }
    Row(
        Modifier.offset {
            // live: hitung dari scroll terbaru di fase layout; tidak live (animasi keluar): diam di posisi terakhir
            if (live) { val (px, py, _) = calc(scrollY()); hold.x = px; hold.y = py }
            IntOffset((hold.x + corrX.value).roundToInt(), (hold.y + corrY.value).roundToInt())
        }
            .graphicsLayer {
                val v = vis.value
                // membesar dari sisi yang menghadap seleksi dengan pantulan; alpha hanya menutup frame awal, bukan efek pudar
                alpha = if (measured) (v * 4f).coerceIn(0f, 1f) else 0f
                val sc = .5f + .5f * v
                scaleX = sc; scaleY = sc
                transformOrigin = TransformOrigin(.5f, if (m0 == 0) 1f else if (m0 == 1) 0f else .5f)
                translationY = (1f - v) * (if (m0 == 0) 14f else if (m0 == 1) -14f else 0f) * density
            }
            .onSizeChanged { bs = it }
            .shadow(if (pal.dark) 8.dp else 6.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
            .background(pal.c3).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val enter = expandHorizontally(spring(.72f, 520f)) + scaleIn(spring(.6f, 520f), .5f)
        val exit = shrinkHorizontally(spring(1f, 700f)) + scaleOut(tween(130, easing = Emph), .5f)
        // tiap tombol memantul masuk berurutan (stagger) mengikuti progres gelembung
        fun Modifier.stag(k: Int): Modifier = graphicsLayer {
            val p = ((vis.value - k * .06f) / .7f).coerceIn(0f, 1.12f)
            val s2 = .5f + .5f * p
            scaleX = s2; scaleY = s2
        }
        @Composable
        fun Item(k: Int, show: Boolean, tone: Int = 0, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
            AnimatedVisibility(show, enter = enter, exit = exit) {
                Box(Modifier.padding(horizontal = 3.dp).stag(k)) {   // jarak 6dp antar tombol
                    SelBtn(live, k == fi, k == li, tone, onClick, content)
                }
            }
        }
        Item(0, flags[0], onClick = { act.copyText(ed.selMd()); ed.selDelete() }) { T("Cut", 14f, 700, it, maxLines = 1) }
        Item(1, flags[1], 2, onClick = { act.copyText(ed.selMd()); vm.show("Copied"); ed.clearSel() }) { T("Copy", 14f, 700, it, maxLines = 1) }
        Item(2, flags[2], onClick = {
            val s = clip.getText()?.text
            if (!s.isNullOrEmpty()) ed.selPaste(s) else vm.show("Nothing to paste")
        }) { T("Paste", 14f, 700, it, maxLines = 1) }
        Item(3, flags[3], 1, onClick = { ed.selDelete() }) { Ic("del", it, 22.dp) }
        Item(4, true, onClick = { ed.selAll() }) { T("Select all", 14f, 700, it, maxLines = 1) }
    }
}

/**
 * Pegangan awal/akhir seleksi, digambar di ruang konten dan bisa diseret melewati blok mana pun.
 * Tetesan M3 Expressive (bulat penuh, satu sudut lancip menempel di tepi teks) 26dp, tapi area sentuhnya 60dp dan letaknya di BAWAH baris,
 * jadi jempol tidak menutupi huruf; seleksi mengikuti gerakan relatif jari (tidak meloncat saat disentuh).
 * Muncul memantul dari ujung lancipnya; saat diseret membesar, berhalo, dan menempel ke jari tanpa lag; saat tidak diseret
 * (pilih semua, ganti kata, dll) meluncur halus ke posisi barunya.
 */
@Composable
fun SelHandles(ed: Ed) {
    val a = ed.pa ?: return
    val f = ed.pf ?: return
    val pal = LocalPal.current
    val dn = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val lo = ed.selSpan()?.first
    @Composable
    fun H(which: Int, pt: Pt) {
        var dragging by remember(which) { mutableStateOf(false) }
        val pop = remember(which) { Animatable(0f) }
        LaunchedEffect(which) { pop.animateTo(1f, spring(.46f, 440f)) }
        val grow by animateFloatAsState(if (dragging) 1.24f else 1f, spring(.42f, 520f), label = "hg")
        val halo by animateFloatAsState(if (dragging) 1f else 0f, spring(.6f, 420f), label = "hh")
        val r = ed.ptRect(pt) ?: return
        val isStart = if (a == f) which == 1 else lo == pt
        val d = with(dn) { 13.dp.toPx() }          // jari-jari tetesan
        val hit = with(dn) { 60.dp.toPx() }
        val cxT = if (isStart) r.left - d else r.left + d
        val cyT = r.bottom + d
        // saat diseret menempel ke jari (snap); selain itu meluncur dengan pegas
        val ax by animateFloatAsState(cxT, if (ed.selDrag) androidx.compose.animation.core.snap() else spring(.8f, 520f), label = "hx")
        val ay by animateFloatAsState(cyT, if (ed.selDrag) androidx.compose.animation.core.snap() else spring(.8f, 520f), label = "hy")
        Box(
            Modifier.offset { IntOffset((ax - hit / 2f).roundToInt(), (ay + with(dn) { 5.dp.toPx() } - hit / 2f).roundToInt()) }
                .size(60.dp)
                .pointerInput(which) {
                    detectDragGestures(
                        onDragStart = { dragging = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); ed.selHandleStart(which) },
                        onDragEnd = { dragging = false; ed.selEnd() },
                        onDragCancel = { dragging = false; ed.selEnd() }
                    ) { ch, dd -> ch.consume(); ed.selNudge(Offset(dd.x, dd.y)) }
                },
            Alignment.Center
        ) {
            // pusat tetesan ada di (ax, ay); kotak sentuh digeser 5dp ke bawah, jadi tetesan digeser balik 5dp ke atas
            Box(Modifier.offset(0.dp, (-5).dp), Alignment.Center) {
                Box(
                    Modifier.size(58.dp).graphicsLayer { scaleX = halo; scaleY = halo; alpha = halo }
                        .clip(CircleShape).background(pal.p.copy(alpha = .16f))
                )
                Box(
                    Modifier.size(26.dp)
                        .graphicsLayer {
                            val k = (pop.value * grow).coerceAtLeast(0f)
                            scaleX = k; scaleY = k
                            // tumbuh dari sudut lancip yang menyentuh teks
                            transformOrigin = TransformOrigin(if (isStart) 1f else 0f, 0f)
                        }
                        .shadow(if (dragging) 6.dp else 2.dp, handleShape(isStart))
                        .clip(handleShape(isStart)).background(pal.p)
                        .border(1.5.dp, pal.s.copy(alpha = .85f), handleShape(isStart))
                )
            }
        }
    }
    H(1, a)
    H(2, f)
}

private fun handleShape(start: Boolean): Shape =
    if (start) RoundedCornerShape(topStart = 13.dp, topEnd = 3.dp, bottomEnd = 13.dp, bottomStart = 13.dp)
    else RoundedCornerShape(topStart = 3.dp, topEnd = 13.dp, bottomEnd = 13.dp, bottomStart = 13.dp)

/** Dipisah dari NoteScreen supaya tidak terjebak receiver ColumnScope milik Column di luarnya. */
@Composable
fun BottomBars(ed: Ed, vm: AppVM, act: Actions, showBar: Boolean, imeOpen: Boolean, holdH: () -> Int) {
    val recOn = ed.recActive
    // judul sedang diketik: kapsul judul (tebal / miring / warna) menggantikan kapsul isi catatan
    val titleBarOn = ed.titleFocus && !ed.reading && !ed.findOn && !ed.ink.on && ed.sheet == 0
    SideEffect { ed.barOn = showBar || recOn || ed.sheet != 0 || titleBarOn }
    // Tinggi bilah berubah tiap frame saat bilah / sheet beranimasi. Kalau langsung ditulis ke ed.barH, SETIAP blok teks
    // ikut recompose + restart efek tiap frame (penyebab sheet "+" dan "A" lemot). Sekarang baru diterapkan setelah animasi tenang.
    var rawBarH by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { rawBarH }.collectLatest { h -> delay(80); ed.barH = h }
    }
    Box(Modifier.fillMaxSize()) {
        // kartu rekaman + kapsul alat dalam satu kolom yang diukur bersama (barH), jadi teks / kursor tidak pernah tertutup
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { rawBarH = it.height.toFloat(); ed.barHLive = rawBarH }) {
            AnimatedVisibility(
                visible = recOn,
                enter = fadeIn(tween(220, easing = Emph)) +
                    scaleIn(spring(.8f, 380f), .86f, TransformOrigin(.9f, 1f)) +
                    expandVertically(tween(300, easing = Emph)),
                exit = fadeOut(tween(160)) +
                    scaleOut(tween(220, easing = Emph), .9f, TransformOrigin(.9f, 1f)) +
                    shrinkVertically(tween(260, easing = Emph))
            ) {
                RecordCard(vm)
            }
            DocSheet(ed, act, vm, holdH())
            // bilah datar: gerak kecil + pudar, karena keyboard sudah ikut bergerak sendiri
            AnimatedVisibility(
                visible = showBar && ed.sheet == 0,
                enter = fadeIn(tween(160, delayMillis = 140)) + expandVertically(tween(280, easing = EmphDecel), expandFrom = Alignment.Top),
                // sheet membuka => kapsul hilang seketika (tanpa menyusut), supaya tidak ada gerakan ganda di atas sheet
                exit = if (ed.sheet != 0) androidx.compose.animation.ExitTransition.None
                else fadeOut(tween(140)) + shrinkVertically(tween(260, easing = Emph), shrinkTowards = Alignment.Top)
            ) {
                DocBar(ed, act)
            }
            AnimatedVisibility(
                visible = titleBarOn,
                enter = fadeIn(tween(160, delayMillis = 140)) + expandVertically(tween(280, easing = EmphDecel), expandFrom = Alignment.Top),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(260, easing = Emph), shrinkTowards = Alignment.Top)
            ) {
                TitleBar(ed, vm)
            }
        }
    }
}

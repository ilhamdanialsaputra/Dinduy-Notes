@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package app.dinduy

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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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

fun styled(rt: Rt, text: String, off: Int, pal: Pal, fs: Float): AnnotatedString = buildAnnotatedString {
    append(text)
    val n = text.length
    for (s in rt.sp) {
        val a = (s.s + off).coerceIn(0, n)
        val b = (s.e + off).coerceIn(0, n)
        if (b > a) addStyle(spanStyle(s.k, pal, fs), a, b)
    }
}

fun baseStyle(pal: Pal, dm: Dm, color: Color = pal.os, size: Float = dm.fs, w: Int = 400, lhEm: Float = dm.lh, ls: Float = 0f) =
    TextStyle(color = color, fontFamily = dm.ff, fontSize = size.sp, fontWeight = FontWeight(w), lineHeight = (lhEm * size).sp, letterSpacing = ls.em)

/** Catat posisi kolom teks di ruang konten, dipakai seleksi lintas blok (mode baca maupun mode ketik). */
private fun Modifier.trackPos(ed: Ed, key: String): Modifier = onGloballyPositioned { c ->
    val cc = ed.contentCoords
    if (cc != null && cc.isAttached && c.isAttached) {
        val o = cc.localPositionOf(c, Offset.Zero)
        if (ed.kpos[key] != o) ed.kpos[key] = o
    }
}

/** Terapkan pemotongan baris buatan (teks mengelilingi gambar) pada teks bergaya. z = awalan tak terlihat di depan. */
private fun Fold.applyTo(a: AnnotatedString, z: Int): AnnotatedString = buildAnnotatedString {
    val pad = SpanStyle(letterSpacing = ls.sp)
    val ps = "\u2003".repeat(padLen)
    var from = 0
    if (padLen > 0) {
        append(a.subSequence(0, z)); from = z
        withStyle(pad) { append(ps) }
    }
    for (k in cuts.indices) {
        val at = (cuts[k] + z).coerceIn(from, a.length)
        append(a.subSequence(from, at)); from = at
        append("\n")
        if (k < padCnt) withStyle(pad) { append(ps) }
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

/** Satu kolom teks: BasicTextField saat mengedit, Text biasa saat mode baca. */
@Composable
fun TF(
    ed: Ed, key: String, base: TextStyle, modifier: Modifier = Modifier,
    hint: String? = null, wrap: Boolean = true, fold: Fold? = null
) {
    val pal = LocalPal.current
    val v = ed.tf[key] ?: return
    val rt = ed.rtOf(key)
    val fs = base.fontSize.value
    if (ed.reading) {
        val sr = if (ed.selOn) ed.selRangeOf(key) else null
        val ann = remember(rt, pal, fs, sr, fold) {
            val a = styled(rt, rt.t, 0, pal, fs)
            val a2 = if (sr == null) a else buildAnnotatedString {
                append(a)
                addStyle(SpanStyle(background = pal.p.copy(alpha = .32f)), sr.first, sr.second)
            }
            if (fold == null) a2 else fold.applyTo(a2, 0)
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
                .pointerInput(rt, ed.selOn) {
                    if (ed.selOn) detectTapGestures { ed.clearSel() }
                    else detectTapGestures(
                        // mode baca: ketuk sekali di teks mana pun = langsung mengedit di situ (ketuk tautan = buka tautan)
                        onTap = { pos ->
                            val l = lay ?: return@detectTapGestures
                            val o0 = l.getOffsetForPosition(pos)
                            val o = fold?.t2o(o0, 0) ?: o0
                            val u = rt.sp.firstOrNull { it.k == K.L && o >= it.s && o < it.e }?.u
                            if (u != null) uri.openUri(u) else ed.exitReading(key, o)
                        }
                    )
                },
            style = base, softWrap = wrap, onTextLayout = { lay = it }
        )
    } else {
        val fr = remember { FocusRequester() }
        val bring = remember { BringIntoViewRequester() }
        var lay by remember { mutableStateOf<TextLayoutResult?>(null) }
        var foc by remember { mutableStateOf(false) }
        LaunchedEffect(lay) { lay?.let { ed.lays[key] = it; ed.lsh[key] = 1 } }
        val r = ed.req
        LaunchedEffect(r) {
            if (r != null && r.key == key) {
                try { fr.requestFocus() } catch (e: Exception) { }
                ed.applyReq(r)
            }
        }
        val clear = if (ed.barOn) ed.barH + 16f * ed.dens else 0f   // tinggi kapsul (termasuk jarak bawahnya) + sedikit ruang napas
        LaunchedEffect(v.selection, v.text.length, foc, clear) {
            if (foc && v.selection.collapsed) {
                delay(30)
                lay?.let { l ->
                    val o = (fold?.o2t(v.selection.start, 1) ?: v.selection.start).coerceIn(0, l.layoutInput.text.length)
                    try { bring.bringIntoView(l.getCursorRect(o).let { c -> c.copy(bottom = c.bottom + clear) }) } catch (e: Exception) { }
                }
            }
        }
        val sr = if (ed.selOn) ed.selRangeOf(key) else null
        val vt = remember(rt, pal, fs, fold, sr) {
            VisualTransformation { t ->
                val a0 = styled(rt, t.text, 1, pal, fs)
                val a = if (sr == null) a0 else buildAnnotatedString {
                    append(a0)
                    val s0 = (sr.first + 1).coerceIn(0, t.text.length)
                    val e0 = (sr.second + 1).coerceIn(0, t.text.length)
                    if (e0 > s0) addStyle(SpanStyle(background = pal.p.copy(alpha = .32f)), s0, e0)
                }
                if (fold == null) TransformedText(a, OffsetMapping.Identity)
                else {
                    val n = t.text.length
                    val tn = n + fold.total
                    TransformedText(fold.applyTo(a, 1), object : OffsetMapping {
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
        val hideNative = ed.selOn   // seleksi editor aktif: sembunyikan kursor + pegangan bawaan field supaya tidak dobel
        val cols = if (hideNative) androidx.compose.foundation.text.selection.TextSelectionColors(Color.Transparent, selCols.backgroundColor) else selCols
        CompositionLocalProvider(
            LocalViewConfiguration provides vcNoLong,
            androidx.compose.foundation.text.selection.LocalTextSelectionColors provides cols
        ) {
            BasicTextField(
                value = v, onValueChange = { ed.onText(key, it) },
                modifier = modifier.focusRequester(fr).bringIntoViewRequester(bring).trackPos(ed, key)
                    .onFocusChanged { foc = it.isFocused; if (it.isFocused) ed.onFocus(key) }
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
                textStyle = base, cursorBrush = SolidColor(if (hideNative) Color.Transparent else pal.p), visualTransformation = vt,
                onTextLayout = { lay = it },
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
    "note" -> QS(p.pc, p.opc, RoundedCornerShape(28.dp), "Catatan")
    "tip" -> QS(p.sc, p.osc, RoundedCornerShape(40.dp, 12.dp, 40.dp, 12.dp), "Tips")
    "imp" -> QS(p.p, p.op, RoundedCornerShape(12.dp, 40.dp, 12.dp, 40.dp), "Penting")
    "warn" -> QS(p.wc, p.owc, RoundedCornerShape(24.dp), "Peringatan", bar = true)
    "cau" -> QS(p.ec, p.oec, RoundedCornerShape(6.dp, 32.dp, 32.dp, 32.dp), "Awas")
    "idea" -> QS(p.ac, p.oac, RoundedCornerShape(44.dp, 44.dp, 12.dp, 44.dp), "Ide")
    "def" -> QS(Color.Transparent, p.os, RoundedCornerShape(32.dp), "Definisi", border = true)
    else -> QS(p.tc, p.otc, RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp), null)
}

val VARS = listOf(
    "quote" to "Kutipan", "note" to "Catatan", "tip" to "Tips", "imp" to "Penting",
    "warn" to "Peringatan", "cau" to "Awas", "idea" to "Ide", "def" to "Definisi"
)

@Composable
fun BlockView(ed: Ed, vm: AppVM, b: Blk, ordN: Int, dm: Dm, fold: Fold? = null, mk: Float = 0f) {
    val pal = LocalPal.current
    val fs = dm.fs
    val bottom = (fs * 1.1f).dp
    when (b.ty) {
        "p" -> {
            val only = ed.blocks.size == 1 && b.r.t.isEmpty()
            TF(ed, b.id, baseStyle(pal, dm), Modifier.fillMaxWidth().padding(bottom = bottom), hint = if (only) "Tulis catatan…" else null, fold = fold)
        }
        "h" -> {
            val st = when (b.lv) {
                1 -> baseStyle(pal, dm, pal.p, fs * 2.5f, 1000, 1.02f, -.02f)
                2 -> baseStyle(pal, dm, pal.os, fs * 1.85f, 1000, 1.02f, -.02f)
                3 -> baseStyle(pal, dm, pal.p, fs * 1.45f, 1000, 1.02f, -.02f)
                else -> baseStyle(pal, dm, pal.os, fs * 1.15f, 1000, 1.02f, -.02f)
            }
            TF(ed, b.id, st, Modifier.fillMaxWidth().padding(top = (fs * .4f).dp, bottom = (fs * .5f).dp))
        }
        "li" -> ListItem(ed, b, ordN, dm, pal, fold, mk)
        "q" -> {
            val q = qs(b.k, pal)
            Box(
                Modifier.fillMaxWidth().padding(bottom = bottom).clip(q.shape).background(q.bg)
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
                    TF(ed, b.id, baseStyle(pal, dm, q.fg, fs * 1.05f, 500), Modifier.fillMaxWidth())
                }
            }
        }
        "code" -> {
            Box(Modifier.fillMaxWidth().padding(bottom = bottom).clip(RoundedCornerShape(28.dp)).background(pal.c3).padding(20.dp)) {
                Column {
                    if (b.l.isNotEmpty()) {
                        T(
                            b.l.uppercase(), 12f, 900, pal.op,
                            Modifier.padding(bottom = 14.dp).clip(RoundedCornerShape(12.dp)).background(pal.p)
                                .padding(horizontal = 12.dp, vertical = 4.dp), ls = .1f
                        )
                    }
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        TF(
                            ed, b.id,
                            TextStyle(
                                color = pal.os, fontFamily = FontFamily.Monospace, fontSize = (fs * .82f).sp,
                                lineHeight = (fs * .82f * 1.55f).sp
                            ),
                            Modifier.widthIn(min = 240.dp), wrap = false
                        )
                    }
                }
            }
        }
        "hr" -> Box(
            Modifier.fillMaxWidth().padding(vertical = (fs * .9f).dp).height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(pal.sc)
        )
        "tbl" -> TableView(ed, b, dm, pal)
        "img" -> ImgView(ed, vm, b, dm)
    }
}

@Composable
fun ListItem(ed: Ed, b: Blk, ordN: Int, dm: Dm, pal: Pal, fold: Fold? = null, mk: Float = 0f) {
    val fs = dm.fs
    val lineH = fs * dm.lh
    val mw = when (b.lt) { "o" -> 2.2f; "c" -> 1.9f; else -> 1.7f } * fs
    val done = b.lt == "c" && b.x
    Row(Modifier.fillMaxWidth().padding(start = (b.d * 1.4f * fs).dp, bottom = (fs * .5f).dp)) {
        // penanda dipusatkan secara vertikal pada tinggi baris, lalu sedikit dinaikkan agar sejajar dengan x-height teks
        Box(
            Modifier.offset(mk.dp, 0.dp).width(mw.dp).height(lineH.dp).then(
                if (b.lt == "c") Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { ed.toggleCheck(b.id) } else Modifier
            ),
            contentAlignment = Alignment.CenterStart
        ) {
            when (b.lt) {
                "o" -> Box(
                    Modifier.offset(0.dp, (-.04f * fs).dp).size((1.55f * fs).dp).clip(CircleShape).background(pal.p),
                    Alignment.Center
                ) { T(ordN.toString(), fs * .85f, 900, pal.op) }
                "c" -> {
                    val sz = (1.15f * fs).dp
                    val shape = RoundedCornerShape((.3f * 1.15f * fs).dp)
                    Box(
                        Modifier.offset((.1f * fs).dp, (-.04f * fs).dp).size(sz).clip(shape)
                            .then(if (done) Modifier.background(pal.p) else Modifier.border((.14f * fs).dp, pal.p, shape)),
                        Alignment.Center
                    ) { if (done) Ic("ok", Color.White, sz * .78f) }
                }
                else -> Box(
                    Modifier.offset((.3f * fs).dp, (-.04f * fs).dp).size((.5f * fs).dp).clip(
                        RoundedCornerShape(topStartPercent = 40, topEndPercent = 60, bottomEndPercent = 55, bottomStartPercent = 45)
                    ).background(pal.p)
                )
            }
        }
        val st = baseStyle(pal, dm).let {
            if (done) it.copy(color = it.color.copy(alpha = .6f), textDecoration = TextDecoration.LineThrough) else it
        }
        TF(ed, b.id, st, Modifier.weight(1f), fold = fold)
    }
}

@Composable
fun TableView(ed: Ed, b: Blk, dm: Dm, pal: Pal) {
    val cols = b.tb.firstOrNull()?.size ?: 1
    val colW = maxOf(110f, ed.widthDp / cols).dp
    Box(
        Modifier.fillMaxWidth().padding(bottom = (dm.fs * 1.1f).dp).clip(RoundedCornerShape(24.dp)).background(pal.c2)
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            b.tb.forEachIndexed { r, row ->
                Row {
                    row.forEachIndexed { c, _ ->
                        val k = "${b.id}:$r:$c"
                        val focused = ed.focus == k && !ed.reading
                        val head = r == 0
                        val bg = if (focused) pal.pc else if (head) pal.sc else Color.Transparent
                        val fg = if (focused) pal.opc else if (head) pal.osc else pal.os
                        Box(
                            Modifier.width(colW).background(bg)
                                .drawBehind { if (r > 0) drawLine(pal.ol, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            val al = when (b.ca.getOrNull(c)) {
                                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                                "right" -> androidx.compose.ui.text.style.TextAlign.End
                                else -> androidx.compose.ui.text.style.TextAlign.Start
                            }
                            TF(
                                ed, k,
                                TextStyle(
                                    color = fg, fontFamily = dm.ff, fontSize = (dm.fs * .9f).sp, lineHeight = (dm.fs * .9f * 1.45f).sp,
                                    fontWeight = FontWeight(if (head) 850 else 400), textAlign = al
                                ),
                                Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
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
    lead: Long = 0L
): Modifier {
    val st by rememberUpdatedState(onStart)
    val mo by rememberUpdatedState(onMove)
    val en by rememberUpdatedState(onEnd)
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id }
                if (c == null) { ok = false; break }
                if (!c.pressed) { c.consume(); break }
                val d = c.positionChange()
                if (d != Offset.Zero) mo(c.position, d)
                ev.changes.forEach { it.consume() }
            }
            en(ok)
        }
    }
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
            hint = if (ed.reading) null else if (imgSel) "Ketuk di sini untuk menulis…" else "Tulis di samping…"
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
    var snapOn by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
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
                !ed.reading,
                onStart = { ed.mvBegin(bS.id, mS.id); true },
                onMove = { _, d -> ed.mvDrag(d.x, d.y) },
                onEnd = { ok -> ed.mvEnd(ok) }
            )
            .pointerInput(ed.reading) {
                if (!ed.reading) detectTapGestures(onTap = { ed.selectImg(bS.id, ixS) })
                else detectTapGestures(onTap = { ed.openImgFromReading(bS.id, ixS) })
            }
            .zIndex(if (liftedRow) 2f else 0f)
            .graphicsLayer {
                if (liftedRow) translationX = ed.mvX
                scaleX = lift; scaleY = lift
                alpha = if (liftedRow) .94f else 1f
            }
            .onSizeChanged { box = it }
            .onGloballyPositioned { c ->
                val cc = ed.contentCoords
                if (cc != null && cc.isAttached && c.isAttached) {
                    val o = cc.localPositionOf(c, Offset.Zero)
                    ed.ipos[mS.id] = androidx.compose.ui.geometry.Rect(o, Size(c.size.width.toFloat(), c.size.height.toFloat()))
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
        if (liftedBlk) {
            val t = when (ed.mvAl) { "l" -> "Kiri · teks di kanan"; "r" -> "Kanan · teks di kiri"; else -> "Tengah" }
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(pal.p)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { T(t, 13f, 900, pal.op) }
        }
        if (selected && !ed.reading && ed.mv == null) {
            Box(Modifier.matchParentSize().border(2.dp, pal.p, shape))
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
            val mw = when (b.lt) { "o" -> 2.2f; "c" -> 1.9f; else -> 1.7f } * fs
            BM(baseStyle(pal, dm), 0f, fs * .5f, b.d * 1.4f * fs + mw)
        }
        else -> null
    }
}

/**
 * Simulasi aliran teks: blok setelah gambar diukur pada lebar sempit sampai tinggi gambar habis.
 * Paragraf yang melewati dasar gambar dipotong barisnya (baris atas sempit, sisanya selebar penuh).
 * Judul / poin yang melewati dasar gambar tetap sempit seluruhnya.
 */
private fun planFloat(ed: Ed, i: Int, dm: Dm, pal: Pal, tm: TextMeasurer, dn: Density): FloatPlan {
    val b = ed.blocks[i]
    val m = b.im[0]
    val d = dn.density
    val fs = dm.fs
    val left = m.al == "l"
    val imgW = ed.widthDp * m.w.coerceIn(.2f, 1f)
    val imgH = imgW / m.a.coerceIn(.2f, 5f)
    val indent = imgW + SIDE_GAP
    val narrowW = ed.widthDp - indent
    var rem = (imgH + fs * 1.1f) * d
    val out = ArrayList<Pair<Blk, FSpec>>()
    var j = i + 1
    while (j < ed.blocks.size && rem > 0f) {
        val x = ed.blocks[j]
        val bm = blockBM(x, pal, dm) ?: break
        val top = bm.top * d
        if (top >= rem) break
        val fsz = bm.style.fontSize.value
        val avail = ((narrowW - bm.inset - 4f) * d).toInt().coerceAtLeast(1)
        val res = tm.measure(styled(x.r, x.r.t, 0, pal, fsz), bm.style, constraints = Constraints(maxWidth = avail))
        val h = maxOf(res.size.height.toFloat(), with(dn) { bm.style.lineHeight.toPx() })
        val fits = top + h <= rem
        var fold: Fold? = null
        var mk = 0f
        if (!fits && (x.ty == "p" || x.ty == "li") && x.r.t.indexOf('\n') < 0) {
            var k = 0
            while (k < res.lineCount && top + res.getLineTop(k) < rem) k++
            if (k in 1 until res.lineCount) {
                val cuts = IntArray(k) { res.getLineEnd(it) }
                // poin/nomor: kolom teks berawal sesudah penanda (x0), jadi geseran baris sempit = indent - x0, dan penanda ikut digeser ke kanan gambar
                val x0 = if (x.ty == "li") bm.inset - when (x.lt) { "o" -> 2.2f; "c" -> 1.9f; else -> 1.7f } * fs else 0f
                val gapDp = maxOf(0f, indent - x0)
                fold = if (left && gapDp > 0f) {
                    val em = tm.measure("\u2003", bm.style).size.width.toFloat().let { if (it < 1f) fsz * d else it }
                    val indPx = gapDp * d
                    val n = (indPx / em).roundToInt().coerceIn(1, 40)
                    val ls = (indPx - n * em) / n
                    mk = gapDp
                    Fold(cuts, n, k - 1, with(dn) { ls.toSp() }.value)
                } else Fold(cuts, 0, 0, 0f)
            }
        }
        val narrow = fold == null
        out.add(x to FSpec(if (narrow && left) indent else 0f, if (narrow && !left) indent else 0f, 0f, fold, mk))
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
        if (b.ty != "img" || b.im.size != 1 || b.im[0].al == "c") continue
        val p = if (ed.sideOk(b)) planFloat(ed, i, dm, pal, tm, dn) else FloatPlan(emptyList())
        if (out == null) out = HashMap()
        out[b.id] = p
    }
    return out ?: emptyMap()
}

/** Gambar di kiri/kanan dengan paragraf sesudahnya mengalir di sampingnya, lalu lanjut selebar penuh di bawahnya. */
@Composable
fun FloatGroup(ed: Ed, vm: AppVM, b: Blk, plan: FloatPlan, od: Map<String, Int>, dm: Dm) {
    val m = b.im[0]
    val sel = ed.selImg?.takeIf { it.first == b.id }?.second
    val bottom = (dm.fs * 1.1f).dp
    val imgW = (ed.widthDp * m.w.coerceIn(.2f, 1f)).dp
    val ratio = m.a.coerceIn(.2f, 5f)
    val lifted = ed.mv?.let { it.bid == b.id && !it.row } == true
    Box(Modifier.fillMaxWidth().heightIn(min = imgW / ratio + bottom)) {
        Column(Modifier.fillMaxWidth()) {
            plan.parts.forEach { (x, sp) ->
                key(x.id) {
                    Box(
                        Modifier.padding(start = sp.ps.dp, end = sp.pe.dp, bottom = sp.extra.dp)
                            .onGloballyPositioned { c ->
                                val cc = ed.contentCoords
                                if (cc != null && cc.isAttached && c.isAttached) {
                                    ed.bpos[x.id] = floatArrayOf(cc.localPositionOf(c, Offset.Zero).y, c.size.height.toFloat())
                                }
                            }
                    ) { BlockView(ed, vm, x, od[x.id] ?: 1, dm, sp.fold, sp.mk) }
                }
            }
        }
        Box(
            Modifier.align(if (m.al == "r") Alignment.TopEnd else Alignment.TopStart)
                .zIndex(if (lifted) 3f else 1f)
                .graphicsLayer {
                    if (lifted) { translationX = ed.mvX; translationY = ed.mvY; scaleX = 1.03f; scaleY = 1.03f; alpha = .94f }
                }
        ) { ImgCell(ed, vm, b, 0, Modifier.width(imgW).aspectRatio(ratio), sel == 0) }
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
    val fg = if (on) pal.op else pal.os
    Box(
        Modifier.size(46.dp).graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else .28f }
            .clip(RoundedCornerShape(r)).background(if (on) pal.p else Color.Transparent)
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
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 10.dp)
    ) {
        // kapsul tambahan (gambar / tabel / kutipan): muncul dan hilang dengan animasi mulus
        val kindNow = if (ed.miniBar) 0 else if (ed.selImg != null) 1 else if (cell) 2 else if (ed.pickQuote) 3 else 0
        var kindShown by remember { mutableStateOf(0) }
        var lastSel by remember { mutableStateOf<Pair<String, Int>?>(null) }
        SideEffect {
            if (kindNow != 0) kindShown = kindNow
            ed.selImg?.let { lastSel = it }
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
                            Row(
                                Modifier.fillMaxWidth().shadow(if (pal.dark) 6.dp else 1.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                                    .background(pal.c3).horizontalScroll(rememberScrollState()).padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Tbn({ Ic("ok", it) }, true, true) { ed.closeImg() }
                                Sep()
                                if (!inRow) {
                                    val al = b?.im?.getOrNull(s.second)?.al
                                    Tbn({ Ic("aL", it) }, al == "l", true) { ed.imgAlign("l") }
                                    Tbn({ Ic("aC", it) }, al == "c", true) { ed.imgAlign("c") }
                                    Tbn({ Ic("aR", it) }, al == "r", true) { ed.imgAlign("r") }
                                }
                                Tbn({ Ic(if (inRow) "rows" else "cols", it) }, false, true) { ed.toggleRow() }
                                Tbn({ Ic("aup", it) }, false, true) { ed.imgMove(-1) }
                                Tbn({ Ic("adn", it) }, false, true) { ed.imgMove(1) }
                                Tbn({ Ic("del", it) }, false, true) { ed.imgDelete() }
                            }
                        }
                    }
                    2 -> Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.c3)
                            .horizontalScroll(rememberScrollState()).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ar" to "+ Baris", "ac" to "+ Kolom", "dr" to "− Baris", "dc" to "− Kolom").forEach { (k, l) ->
                            Chip(l, pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.tOp(k) }
                        }
                        Chip("Hapus tabel", pal.ec, pal.oec, RoundedCornerShape(23.dp)) { ed.tOp("dt") }
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
                        if (ty == "q") Chip("Teks biasa", pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.pickQuote = false; ed.setPlain() }
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
                I("chk", "chk", isLi && blk?.lt == "c", convOff) { ed.setList("c") }
                I("out", "out", off = !isLi) { ed.indent(-1) }
                I("ind", "ind", off = !isLi) { ed.indent(1) }
                Sep()
                I("quote", "quote", ty == "q" || ed.pickQuote, convOff) { if (ed.focus != null) ed.pickQuote = !ed.pickQuote }
                I("pre", "pre", ty == "code", convOff) { ed.setCode() }
                I("table", "table", off = blkOff) { ed.insTable() }
                I("img", "img", off = blkOff) { act.pickImages() }
                I("hr", "hr", off = blkOff) { ed.insHr() }
            }
            Box(Modifier.width(10.dp * (1f - f)).clipToBounds().wrapContentWidth(Alignment.Start, unbounded = true)) { Sep() }
            // keyboard terbuka: ">" menciutkan kapsul ke kanan; "<" mengembangkannya lagi; keyboard tertutup: panah bawah = mode baca
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

@Composable
fun Sep() {
    val pal = LocalPal.current
    Box(Modifier.padding(horizontal = 4.dp).width(2.dp).height(24.dp).clip(RoundedCornerShape(1.dp)).background(pal.ol))
}

// ---------- layar catatan ----------
@Composable
fun NoteScreen(vm: AppVM, ed: Ed, act: Actions) {
    val pal = LocalPal.current
    val dm = Dm(vm.pr.fs.toFloat(), vm.pr.lh, LocalFF.current)
    val scroll = rememberScrollState()
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
    val titleFr = remember { FocusRequester() }
    LaunchedEffect(ed.titleReq) {
        if (ed.titleReq > 0) {
            delay(380)
            try { titleFr.requestFocus() } catch (e: Exception) { }
        }
    }

    @Composable
    fun TopBar() {
        Row(
            Modifier.fillMaxWidth().background(pal.s).windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("back", pal.os, { act.back() })
            Spacer(Modifier.weight(1f))
            IconBtn("more", pal.os, { ed.flush(); vm.sheet = Sheet.Doc(ed.noteId) })
        }
    }

    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    val haptic = LocalHapticFeedback.current
    SideEffect {
        ed.scroll = scroll
        ed.dens = density.density
        ed.buzz = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Column(Modifier.fillMaxSize().background(pal.s)) {
        TopBar()
        // area catatan berhenti tepat di atas keyboard / navbar; kapsul melayang di atasnya tanpa latar
        Box(
            Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .onSizeChanged { ed.viewH = it.height.toFloat() }
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.widthIn(max = 700.dp).fillMaxWidth().padding(horizontal = 24.dp)
                        .onSizeChanged { ed.widthDp = with(density) { it.width.toDp().value } }
                        .onGloballyPositioned { ed.contentCoords = it }
                        .holdDrag(
                            true,
                            onStart = { pos -> ed.selLong(pos, pos.y - scroll.value) },
                            onMove = { pos, _ -> ed.selMove(pos, pos.y - scroll.value) },
                            onEnd = { ed.selEnd() },
                            lead = 70L
                        )
                        .drawWithContent {
                            drawContent()
                            val at = ed.dropAt
                            if (at >= 0) {
                                val th = 4.dp.toPx()
                                val y = if (at < ed.blocks.size) (ed.bpos[ed.blocks[at].id]?.get(0) ?: -1f) - th
                                else ed.blocks.lastOrNull()?.let { l -> ed.bpos[l.id]?.let { it[0] + it[1] } } ?: -100f
                                if (y >= -th) drawRoundRect(pal.p, Offset(0f, y), Size(size.width, th), CornerRadius(th / 2f))
                            }
                        }
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        val firstText = { ed.blocks.firstOrNull { it.isText }?.let { ed.focusAt(it.id, 0) } }
                        Box(Modifier.padding(top = 6.dp, bottom = 12.dp)) {
                            if (ed.title.isEmpty()) {
                                T("Judul", 34f, 1000, pal.osv, alpha = .5f, ls = -.02f)
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = ed.title,
                                onValueChange = { v -> val nl = v.contains('\n'); ed.titleChanged(v); if (nl) firstText() },
                                readOnly = ed.reading,
                                textStyle = TextStyle(
                                    color = pal.p, fontFamily = dm.ff, fontSize = 34.sp, fontWeight = FontWeight(1000),
                                    lineHeight = (34 * 1.1f).sp, letterSpacing = (-.02f).em
                                ),
                                cursorBrush = SolidColor(pal.p),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { firstText() }),
                                modifier = Modifier.fillMaxWidth().focusRequester(titleFr)
                                    .onFocusChanged { ed.titleFocus = it.isFocused; if (it.isFocused) ed.selImg = null }
                                    .then(
                                        if (ed.reading) Modifier.wordTap(
                                            onTap = { if (ed.selOn) ed.clearSel() else { ed.exitReading(); ed.titleReq++ } },
                                            onDouble = { if (ed.selOn) ed.clearSel() else { ed.exitReading(); ed.titleReq++ } }
                                        ) else Modifier
                                    )
                            )
                        }
                        var bi0 = 0
                        while (bi0 < ed.blocks.size) {
                            val bi = bi0
                            val b = ed.blocks[bi]
                            val plan = plans[b.id]
                            key(b.id) {
                                val lifted = ed.mv?.let { it.bid == b.id && !it.row } == true
                                Box(
                                    Modifier
                                        .zIndex(if (lifted) 3f else 0f)
                                        .onGloballyPositioned { c -> ed.bpos[b.id] = floatArrayOf(c.positionInParent().y, c.size.height.toFloat()) }
                                        .graphicsLayer {
                                            if (lifted && plan == null) {
                                                translationX = ed.mvX; translationY = ed.mvY
                                                scaleX = 1.03f; scaleY = 1.03f; alpha = .94f
                                            }
                                        }
                                ) {
                                    if (plan != null) FloatGroup(ed, vm, b, plan, od, dm)
                                    else BlockView(ed, vm, b, od[b.id] ?: 1, dm)
                                }
                            }
                            bi0 += 1 + plan.let { it?.parts?.size ?: 0 }
                        }
                        Box(
                            Modifier.fillMaxWidth().height(220.dp).pointerInput(ed.reading, ed.selOn) {
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
                    if (ed.selOn) SelHandles(ed)
                    SelBubble(ed, vm, act, scroll.value)   // selalu ada di komposisi supaya bisa animasi keluar
                }
            }
            BottomBars(ed, vm, act, (!ed.reading || ed.selEdit) && !ed.titleFocus, imeOpen)
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

/** Tombol di gelembung seleksi: sudut menyempit + sedikit mengecil saat ditekan (M3 Expressive). */
@Composable
fun SelBtn(enabled: Boolean, padH: Dp, padV: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 8.dp else 16.dp, spring(.55f, 700f), label = "r")
    val sc by animateFloatAsState(if (pr) .92f else 1f, spring(.5f, 700f), label = "sc")
    Box(
        Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(r))
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = padH, vertical = padV),
        Alignment.Center
    ) { content() }
}

/**
 * Gelembung Potong / Salin / Tempel yang menempel di seleksi (di atasnya, atau di bawahnya bila tak muat).
 * Muncul/hilang dengan spring (membesar dari sisi yang menghadap seleksi, sedikit memantul), tidak berkedip
 * karena baru ditampilkan setelah ukurannya terukur, pindah atas/bawah digeser mulus, dan tombolnya
 * masuk/keluar dengan melebar-menyempit.
 */
@Composable
fun SelBubble(ed: Ed, vm: AppVM, act: Actions, scrollY: Int) {
    val pal = LocalPal.current
    val dn = LocalDensity.current
    @Suppress("DEPRECATION")
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val hold = remember { BubbleHold() }
    var bs by remember { mutableStateOf(IntSize.Zero) }
    val vis = remember { Animatable(0f) }
    val corrX = remember { Animatable(0f) }
    val corrY = remember { Animatable(0f) }
    val span = if (ed.selOn) ed.selSpan() else null
    val r0 = span?.let { ed.ptRect(it.first) }
    val r1 = span?.let { ed.ptRect(it.second) }
    val live = ed.selOn && !ed.selDrag && span != null && r0 != null && r1 != null
    val measured = bs != IntSize.Zero
    var x = hold.x
    var y = hold.y
    var mode = hold.mode
    var has = hold.has
    var edit = hold.edit
    if (live && span != null && r0 != null && r1 != null) {
        has = span.first != span.second
        edit = ed.selEdit
        val gap = with(dn) { 10.dp.toPx() }
        // bubble selalu diusahakan tampil di area layar yang terlihat (di luar kapsul alat), mengikuti scroll
        val m = with(dn) { 8.dp.toPx() }
        val visTop = scrollY + m
        val visBot = scrollY + ed.viewH - (if (ed.barOn) ed.barH + with(dn) { 10.dp.toPx() } else 0f) - m
        val belowGap = with(dn) { 52.dp.toPx() }
        val aboveY = r0.top - bs.height - gap
        val belowY = r1.bottom + belowGap
        val canAbove = aboveY >= visTop && r0.top < visBot
        val canBelow = belowY + bs.height <= visBot && r1.bottom > visTop
        val pinned = !canAbove && !canBelow
        val yRaw = when {
            canAbove -> aboveY
            canBelow -> belowY
            else -> visTop + (visBot - visTop - bs.height).coerceAtLeast(0f) * .35f   // seleksi menutupi layar: tempel di area terlihat
        }
        y = yRaw.coerceIn(visTop, maxOf(visTop, visBot - bs.height))
        val maxX = ed.widthDp * dn.density
        val cx = if (pinned) maxX / 2f else (r0.left + r1.left) / 2f
        x = (cx - bs.width / 2f).coerceIn(0f, (maxX - bs.width).coerceAtLeast(0f))
        mode = if (canAbove) 0 else if (canBelow) 1 else 2
        // pindah atas <-> bawah: geser mulus dari posisi lama, bukan melompat
        val sx = if (measured && vis.value > .5f && hold.mode != -1 && hold.mode != mode) hold.x - x else 0f
        val sy = if (measured && vis.value > .5f && hold.mode != -1 && hold.mode != mode) hold.y - y else 0f
        LaunchedEffect(mode) {
            if (sx != 0f || sy != 0f) {
                corrX.snapTo(sx); corrY.snapTo(sy)
                launch { corrX.animateTo(0f, spring(.78f, 420f)) }
                corrY.animateTo(0f, spring(.78f, 420f))
            }
        }
        val fx = x; val fy = y; val fm = mode; val fh = has; val fe = edit
        SideEffect { hold.x = fx; hold.y = fy; hold.mode = fm; hold.has = fh; hold.edit = fe }
    }
    // muncul hanya setelah ukuran terukur (tidak ada frame di posisi salah = tidak berkedip); hilang pakai spring tanpa pantulan
    LaunchedEffect(live && measured) {
        if (live && measured) vis.animateTo(1f, spring(.62f, 520f))
        else if (!live) vis.animateTo(0f, spring(1f, 800f))
    }
    if (!live && vis.value <= .001f) return
    val m0 = mode
    Row(
        Modifier.offset { IntOffset((x + corrX.value).roundToInt(), (y + corrY.value).roundToInt()) }
            .graphicsLayer {
                val v = vis.value
                alpha = if (measured) (v * 1.5f).coerceIn(0f, 1f) else 0f
                val sc = .55f + .45f * v
                scaleX = sc; scaleY = sc
                transformOrigin = TransformOrigin(.5f, if (m0 == 0) 1f else if (m0 == 1) 0f else .5f)
                translationY = (1f - v) * (if (m0 == 0) 12f else if (m0 == 1) -12f else 0f) * density
            }
            .onSizeChanged { bs = it }
            .shadow(if (pal.dark) 6.dp else 3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(pal.c4).padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val enter = fadeIn(tween(180, 40)) + expandHorizontally(tween(260, easing = Emph))
        val exit = fadeOut(tween(110)) + shrinkHorizontally(tween(220, easing = Emph))
        @Composable
        fun Act(label: String, f: () -> Unit) {
            SelBtn(live, 10.dp, 9.dp, f) { T(label, 14f, 800, pal.os, maxLines = 1) }
        }
        AnimatedVisibility(edit && has, enter = enter, exit = exit) {
            Act("Potong") { act.copyText(ed.selText()); ed.selDelete() }
        }
        AnimatedVisibility(has, enter = enter, exit = exit) {
            Act("Salin") { act.copyText(ed.selText()); vm.show("Disalin"); ed.clearSel() }
        }
        AnimatedVisibility(edit, enter = enter, exit = exit) {
            Act("Tempel") {
                val s = clip.getText()?.text
                if (!s.isNullOrEmpty()) ed.selPaste(s) else vm.show("Tidak ada yang bisa ditempel")
            }
        }
        AnimatedVisibility(edit && has, enter = enter, exit = exit) {
            SelBtn(live, 9.dp, 7.dp, { ed.selDelete() }) { Ic("del", pal.os, 22.dp) }
        }
        Act("Semua") { ed.selAll() }
    }
}

/** Pegangan awal/akhir seleksi, digambar di ruang konten dan bisa diseret melewati blok mana pun. */
@Composable
fun SelHandles(ed: Ed) {
    val a = ed.pa ?: return
    val f = ed.pf ?: return
    val pal = LocalPal.current
    val dn = LocalDensity.current
    val lo = ed.selSpan()?.first
    @Composable
    fun H(which: Int, pt: Pt) {
        val r = ed.ptRect(pt) ?: return
        val isStart = if (a == f) which == 1 else lo == pt
        val h11 = with(dn) { 11.dp.toPx() }
        val t24 = with(dn) { 24.dp.toPx() }
        val cx = if (isStart) r.left - h11 else r.left + h11
        val cy = r.bottom + h11
        Box(
            Modifier.offset { IntOffset((cx - t24).roundToInt(), (cy - t24).roundToInt()) }.size(48.dp)
                .pointerInput(which) {
                    detectDragGestures(
                        onDragStart = { ed.selHandleStart(which) },
                        onDragEnd = { ed.selEnd() },
                        onDragCancel = { ed.selEnd() }
                    ) { ch, d -> ch.consume(); ed.selNudge(Offset(d.x, d.y)) }
                },
            Alignment.Center
        ) {
            Box(
                Modifier.size(22.dp).shadow(2.dp, CircleShape).clip(
                    if (isStart) RoundedCornerShape(50, 0, 50, 50) else RoundedCornerShape(0, 50, 50, 50)
                ).background(pal.p)
            )
        }
    }
    H(1, a)
    H(2, f)
}

/** Dipisah dari NoteScreen supaya tidak terjebak receiver ColumnScope milik Column di luarnya. */
@Composable
fun BottomBars(ed: Ed, vm: AppVM, act: Actions, showBar: Boolean, imeOpen: Boolean) {
    SideEffect { ed.barOn = showBar }
    Box(Modifier.fillMaxSize()) {
        // kapsul penuh: gerak kecil + pudar, karena keyboard sudah ikut bergerak sendiri
        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(260, easing = Emph)) + slideInVertically(tween(320, easing = Emph)) { it / 3 } +
                expandVertically(tween(320, easing = Emph)),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(260, easing = Emph)) { it / 4 } +
                shrinkVertically(tween(300, easing = Emph))
        ) {
            Box(
                Modifier.onSizeChanged { ed.barH = it.height.toFloat() }.padding(bottom = 10.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EdBar(ed, act, imeOpen)
            }
        }
    }
}

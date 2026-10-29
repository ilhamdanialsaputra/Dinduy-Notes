@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package app.dinduy

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.Image
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

class Dm(val fs: Float, val lh: Float)

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
    TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight(w), lineHeight = (lhEm * size).sp, letterSpacing = ls.em)

/** Satu kolom teks: BasicTextField saat mengedit, Text biasa saat mode baca. */
@Composable
fun TF(
    ed: Ed, key: String, base: TextStyle, modifier: Modifier = Modifier,
    hint: String? = null, wrap: Boolean = true
) {
    val pal = LocalPal.current
    val v = ed.tf[key] ?: return
    val rt = ed.rtOf(key)
    val fs = base.fontSize.value
    if (ed.reading) {
        val ann = remember(rt, pal, fs) { styled(rt, rt.t, 0, pal, fs) }
        val uri = LocalUriHandler.current
        var lay by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            ann,
            modifier.heightIn(min = base.lineHeight.value.dp).pointerInput(rt) {
                detectTapGestures { pos ->
                    val l = lay ?: return@detectTapGestures
                    val o = l.getOffsetForPosition(pos)
                    val lk = rt.sp.firstOrNull { it.k == K.L && o >= it.s && o < it.e }
                    val u = lk?.u
                    if (u != null) uri.openUri(u) else ed.exitReading(key, o)
                }
            },
            style = base, softWrap = wrap, onTextLayout = { lay = it }
        )
    } else {
        val fr = remember { FocusRequester() }
        val bring = remember { BringIntoViewRequester() }
        var lay by remember { mutableStateOf<TextLayoutResult?>(null) }
        var foc by remember { mutableStateOf(false) }
        val r = ed.req
        LaunchedEffect(r) {
            if (r != null && r.key == key) {
                try { fr.requestFocus() } catch (e: Exception) { }
                ed.applyReq(r)
            }
        }
        LaunchedEffect(v.selection, v.text.length, foc) {
            if (foc && v.selection.collapsed) {
                delay(30)
                lay?.let { l ->
                    val o = v.selection.start.coerceIn(0, l.layoutInput.text.length)
                    try { bring.bringIntoView(l.getCursorRect(o)) } catch (e: Exception) { }
                }
            }
        }
        val vt = remember(rt, pal, fs) {
            VisualTransformation { t -> TransformedText(styled(rt, t.text, 1, pal, fs), OffsetMapping.Identity) }
        }
        BasicTextField(
            value = v, onValueChange = { ed.onText(key, it) },
            modifier = modifier.focusRequester(fr).bringIntoViewRequester(bring)
                .onFocusChanged { foc = it.isFocused; if (it.isFocused) ed.onFocus(key) },
            textStyle = base, cursorBrush = SolidColor(pal.p), visualTransformation = vt,
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
fun BlockView(ed: Ed, vm: AppVM, b: Blk, ordN: Int, dm: Dm) {
    val pal = LocalPal.current
    val fs = dm.fs
    val bottom = (fs * 1.1f).dp
    when (b.ty) {
        "p" -> {
            val only = ed.blocks.size == 1 && b.r.t.isEmpty()
            TF(ed, b.id, baseStyle(pal, dm), Modifier.fillMaxWidth().padding(bottom = bottom), hint = if (only) "Tulis catatan…" else null)
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
        "li" -> ListItem(ed, b, ordN, dm, pal)
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
fun ListItem(ed: Ed, b: Blk, ordN: Int, dm: Dm, pal: Pal) {
    val fs = dm.fs
    val lineH = fs * dm.lh
    val mw = when (b.lt) { "o" -> 2.2f; "c" -> 1.9f; else -> 1.7f } * fs
    val done = b.lt == "c" && b.x
    Row(Modifier.fillMaxWidth().padding(start = (b.d * 1.4f * fs).dp, bottom = (fs * .5f).dp)) {
        // penanda dipusatkan secara vertikal pada tinggi baris, lalu sedikit dinaikkan agar sejajar dengan x-height teks
        Box(
            Modifier.width(mw.dp).height(lineH.dp).then(
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
        TF(ed, b.id, st, Modifier.weight(1f))
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
                                    color = fg, fontSize = (dm.fs * .9f).sp, lineHeight = (dm.fs * .9f * 1.45f).sp,
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

@Composable
fun ImgView(ed: Ed, vm: AppVM, b: Blk, dm: Dm) {
    val sel = ed.selImg?.takeIf { it.first == b.id }?.second
    val n = b.im.size
    val bottom = (dm.fs * 1.1f).dp
    if (n == 1) {
        val m = b.im[0]
        Box(
            Modifier.fillMaxWidth().padding(bottom = bottom),
            contentAlignment = when (m.al) { "l" -> Alignment.CenterStart; "r" -> Alignment.CenterEnd; else -> Alignment.Center }
        ) {
            ImgCell(
                ed, vm, b, 0, Modifier.fillMaxWidth(m.w.coerceIn(.2f, 1f)).aspectRatio(m.a.coerceIn(.2f, 5f)),
                sel == 0
            )
        }
    } else if (n > 1) {
        val sum = b.im.sumOf { it.a.toDouble() }.toFloat().coerceAtLeast(.1f)
        Row(
            Modifier.fillMaxWidth().padding(bottom = bottom).aspectRatio(sum / (1f - (n - 1) * GAP)),
            horizontalArrangement = Arrangement.spacedBy((ed.widthDp * GAP).dp)
        ) {
            b.im.forEachIndexed { j, m ->
                ImgCell(ed, vm, b, j, Modifier.weight(m.a.coerceAtLeast(.05f)).fillMaxHeight(), sel == j)
            }
        }
    }
}

@Composable
fun ImgCell(ed: Ed, vm: AppVM, b: Blk, ix: Int, modifier: Modifier, selected: Boolean) {
    val pal = LocalPal.current
    val m = b.im[ix]
    val bmp = rememberImg(vm, m.id)
    val dens = LocalDensity.current.density
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val shape = RoundedCornerShape(22.dp)
    val row = b.im.size > 1
    Box(
        modifier.onSizeChanged { box = it }
            .pointerInput(ed.reading, b.id, ix) {
                if (!ed.reading) detectTapGestures(onTap = { ed.selectImg(b.id, ix) })
            }
    ) {
        Box(
            Modifier.matchParentSize().clip(shape).background(pal.c3)
                .then(
                    if (selected) Modifier.pointerInput(m.o, box) {
                        detectDragGestures(onDragStart = { ed.commit() }, onDragEnd = { ed.commit() }) { ch, d ->
                            ch.consume()
                            val bw = box.width.toFloat().coerceAtLeast(1f)
                            val bh = box.height.toFloat().coerceAtLeast(1f)
                            val wider = m.o > bw / bh
                            val rx = if (wider) bh * m.o - bw else 0f
                            val ry = if (!wider) bw / m.o - bh else 0f
                            ed.imgLive(b.id, ix) {
                                it.copy(
                                    px = if (rx > 2f) (it.px - d.x / rx).coerceIn(0f, 1f) else it.px,
                                    py = if (ry > 2f) (it.py - d.y / ry).coerceIn(0f, 1f) else it.py
                                )
                            }
                        }
                    } else Modifier
                )
        ) {
            if (bmp != null) {
                Image(
                    bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(m.px * 2f - 1f, m.py * 2f - 1f)
                )
            }
        }
        if (selected && !ed.reading) {
            Box(Modifier.matchParentSize().border(2.5.dp, pal.p, shape))
            var w0 by remember { mutableStateOf(0f) }
            var h0 by remember { mutableStateOf(0f) }
            var hPrev by remember { mutableStateOf(0f) }
            var ax by remember { mutableStateOf(0f) }
            var ay by remember { mutableStateOf(0f) }
            Box(
                Modifier.align(Alignment.BottomEnd).offset(24.dp, 24.dp).size(48.dp)
                    .pointerInput(b.id, ix, row) {
                        detectDragGestures(
                            onDragStart = {
                                ed.commit(); w0 = box.width.toFloat(); h0 = box.height.toFloat(); hPrev = h0; ax = 0f; ay = 0f
                            },
                            onDragEnd = { ed.commit() }
                        ) { ch, d ->
                            ch.consume()
                            ax += d.x; ay += d.y
                            val cw = ed.widthDp * dens
                            val hNew = (h0 + ay).coerceIn(70f * dens, 1100f * dens)
                            if (row) {
                                ed.rowScale(b.id, hPrev / hNew); hPrev = hNew
                            } else {
                                val k = if (m.al == "c") 2f else 1f
                                val w = (w0 + ax * k).coerceIn(cw * .22f, cw)
                                var a = w / hNew
                                if (Math.abs(a / m.o - 1f) < .05f) a = m.o else if (Math.abs(a - 1f) < .05f) a = 1f
                                ed.imgLive(b.id, ix) { it.copy(w = w / cw, a = a) }
                            }
                        }
                    },
                Alignment.Center
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(pal.s).padding(3.dp).clip(CircleShape).background(pal.p))
            }
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
    val key = ed.focus
    val blk = key?.let { ed.blockOfKey(it) }
    val cell = key != null && isCellKey(key)
    val fl = ed.activeFlags()
    val ty = blk?.ty
    val noI = ty == "code"
    val noB = noI || (ty == "h")
    val blkOff = cell
    val capShape = RoundedCornerShape(30.dp)
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (ed.miniBar) Unit else if (ed.selImg != null) {
            val s = ed.selImg!!
            val b = ed.blocks.firstOrNull { it.id == s.first }
            val inRow = (b?.im?.size ?: 1) > 1
            Row(
                Modifier.fillMaxWidth().shadow(if (pal.dark) 6.dp else 1.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                    .background(pal.c3).horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
        } else if (cell) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.c3)
                    .horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ar" to "+ Baris", "ac" to "+ Kolom", "dr" to "− Baris", "dc" to "− Kolom").forEach { (k, l) ->
                    Chip(l, pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.tOp(k) }
                }
                Chip("Hapus tabel", pal.ec, pal.oec, RoundedCornerShape(23.dp)) { ed.tOp("dt") }
            }
        } else if (ed.pickQuote) {
            Row(
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
                    }, ty == "h" && blk?.lv == lv, !blkOff
                ) { ed.pickQuote = false; ed.setHead(lv) }
                val isLi = ty == "li" && !cell
                I("undo", "undo", off = !ed.canUndo) { ed.undo() }
                I("redo", "redo", off = !ed.canRedo) { ed.redo() }
                Sep()
                H("h1", 1); H("h2", 2)
                I("b", "b", K.B in fl && !noB, noB) { ed.toggleFmt(K.B) }
                I("i", "i", K.I in fl && !noI, noI) { ed.toggleFmt(K.I) }
                I("u", "u", K.U in fl && !noI, noI) { ed.toggleFmt(K.U) }
                I("s", "s", K.S in fl && !noI, noI) { ed.toggleFmt(K.S) }
                I("mark", "mark", K.M in fl && !noI, noI) { ed.toggleFmt(K.M) }
                I("code", "code", K.C in fl && !noI, noI) { ed.toggleFmt(K.C) }
                Sep()
                I("ul", "ul", isLi && blk?.lt == "b", blkOff) { ed.setList("b") }
                I("ol", "ol", isLi && blk?.lt == "o", blkOff) { ed.setList("o") }
                I("chk", "chk", isLi && blk?.lt == "c", blkOff) { ed.setList("c") }
                I("out", "out", off = !isLi) { ed.indent(-1) }
                I("ind", "ind", off = !isLi) { ed.indent(1) }
                Sep()
                I("quote", "quote", ty == "q" || ed.pickQuote, blkOff) { if (ed.focus != null) ed.pickQuote = !ed.pickQuote }
                I("pre", "pre", ty == "code", blkOff) { ed.setCode() }
                I("table", "table", off = blkOff) { ed.insTable() }
                I("img", "img", off = blkOff) { act.pickImages() }
                I("hr", "hr", off = blkOff) { ed.insHr() }
            }
            Box(Modifier.width(10.dp * (1f - f)).clipToBounds().wrapContentWidth(Alignment.Start, unbounded = true)) { Sep() }
            // keyboard terbuka: ">" menciutkan kapsul ke kanan; "<" mengembangkannya lagi; keyboard tertutup: panah bawah = mode baca
            Tbn({ Ic(if (ed.miniBar) "chl" else if (imeOpen) "chr" else "hide", it) }, false, true) {
                if (ed.miniBar) ed.miniBar = false
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
    val dm = Dm(vm.pr.fs.toFloat(), vm.pr.lh)
    val scroll = rememberScrollState()
    val focusMgr = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    LaunchedEffect(ed.clearReq) {
        if (ed.clearReq > 0) { focusMgr.clearFocus(); kb?.hide() }
    }
    val od = remember(ed.blocks) { ords(ed.blocks) }
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

    Column(Modifier.fillMaxSize().background(pal.s)) {
        TopBar()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.widthIn(max = 700.dp).fillMaxWidth().padding(horizontal = 24.dp)
                        .onSizeChanged { ed.widthDp = with(density) { it.width.toDp().value } }
                ) {
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
                                color = pal.p, fontSize = 34.sp, fontWeight = FontWeight(1000),
                                lineHeight = (34 * 1.1f).sp, letterSpacing = (-.02f).em
                            ),
                            cursorBrush = SolidColor(pal.p),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { firstText() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(titleFr)
                                .onFocusChanged { ed.titleFocus = it.isFocused; if (it.isFocused) ed.selImg = null }
                                .pointerInput(ed.reading) { if (ed.reading) detectTapGestures { ed.exitReading() } }
                        )
                    }
                    ed.blocks.forEach { b ->
                        key(b.id) { BlockView(ed, vm, b, od[b.id] ?: 1, dm) }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(220.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) {
                            if (ed.reading) ed.exitReading()
                            else {
                                val l = ed.blocks.lastOrNull { it.isText }
                                if (l != null) ed.focusAt(l.id, l.r.t.length)
                            }
                        }
                    )
                }
            }
        }
        BottomBars(ed, act, !ed.reading && !ed.titleFocus, imeOpen)
    }
}

/** Dipisah dari NoteScreen supaya tidak terjebak receiver ColumnScope milik Column di luarnya. */
@Composable
fun BottomBars(ed: Ed, act: Actions, showBar: Boolean, imeOpen: Boolean) {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
        // kapsul penuh: gerak kecil + pudar, karena keyboard sudah ikut bergerak sendiri
        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(260, easing = Emph)) + slideInVertically(tween(320, easing = Emph)) { it / 3 } +
                expandVertically(tween(320, easing = Emph)),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(260, easing = Emph)) { it / 4 } +
                shrinkVertically(tween(300, easing = Emph))
        ) {
            Box(Modifier.padding(bottom = 10.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EdBar(ed, act, imeOpen)
            }
        }
    }
}

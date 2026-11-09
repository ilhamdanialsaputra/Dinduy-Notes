package app.dinduy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape as RCS
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.unit.dp

// =====================================================================================
//  Toolbar Material 3 Expressive.
//  - Bilah atas (mode ketik): centang (masuk tampilan penuh), undo, redo, "+" (sheet Sisipkan). "+" berputar jadi "×".
//  - Dock bawah = SATU wadah mengambang (MorphDock) untuk toolbar ketik, gambar, tabel, kutipan, objek sisipan, dan coret.
//    Berpindah toolbar = wadah itu mengubah bentuk (lebar / tinggi / posisi mengalir dengan pegas), isi lama menciut,
//    isi baru melompat masuk. Tanpa fade.
//  - Tombol dikelompokkan dalam grup tonal tersambung (bukan garis pemisah). Terpilih = squircle, ditekan = lebih kotak.
//  - Perataan dan poin tumbuh ke atas di dalam wadah yang sama.
//  - Sheet bawah (Sisipkan / Format): ubin besar, pergantian halaman memakai morph yang sama, tinggi dibatasi dan bisa digulir.
// =====================================================================================

fun taOf(b: Blk): TextAlign = when (b.ta) {
    "c" -> TextAlign.Center
    "r" -> TextAlign.End
    "j" -> TextAlign.Justify
    else -> TextAlign.Start
}

// ------------------------------------------------------------------ bilah atas
/** Gaya teks + perataan. Rata kiri-kanan (Justify) dikerjakan sendiri oleh TF, lihat justifySpans di EditorUI. */
@Composable
fun TextStyle.aligned(b: Blk): TextStyle {
    val st = copy(textAlign = taOf(b))
    if (b.fi == 0 || !(b.ty == "p" || b.ty == "h" || b.ty == "li")) return st
    // dp -> sp lewat Density supaya sama persis dengan padding dp (tidak ikut skala huruf sistem)
    val d = androidx.compose.ui.platform.LocalDensity.current
    val first = with(d) { b.fi.coerceIn(0, IND_MAX_FI).dp.toSp() }
    val rest = with(d) { (-b.fi).coerceIn(0, IND_MAX_FI).dp.toSp() }
    return st.copy(textIndent = TextIndent(firstLine = first, restLine = rest))
}

/** Kiri: panah kembali dan pil centang bertukar dengan gerak "pop": pil membesar dari titik tengah, ikon berputar dan membesar / mengecil dengan pegas. */
@Composable
fun DocTopLeft(ed: Ed, act: Actions) {
    val pal = LocalPal.current
    val editing = !ed.reading && !ed.findOn
    val p by animateFloatAsState(if (editing) 1f else 0f, spring(dampingRatio = .6f, stiffness = 420f), label = "topleft")
    Box(
        Modifier.width((52f + 14f * p.coerceIn(0f, 1f)).dp).height(52.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (editing) ed.enterReading() else if (ed.findOn) ed.closeFind() else act.back()
            },
        Alignment.Center
    ) {
        Box(
            Modifier.width(64.dp).height(44.dp)
                .graphicsLayer { val s = p.coerceAtLeast(0f); scaleX = s; scaleY = s }
                .clip(RoundedCornerShape(22.dp)).background(pal.p)
        )
        Ic("back", pal.os, modifier = Modifier.graphicsLayer {
            val s = (1f - p * 2f).coerceIn(0f, 1f); scaleX = s; scaleY = s; rotationZ = -80f * p
        })
        Ic("ok", pal.op, modifier = Modifier.graphicsLayer {
            val s = ((p - .4f) / .6f).coerceIn(0f, 1.2f); scaleX = s; scaleY = s; rotationZ = 70f * (1f - p)
        })
    }
}

/** Undo, redo, "+": tombol tonal yang "melompat" masuk berurutan dari kanan. "+" berputar jadi "×" dan berubah bentuk saat sheet terbuka. */
@Composable
fun DocTopActions(ed: Ed) {
    val pal = LocalPal.current
    val kb = LocalSoftwareKeyboardController.current
    val editing = !ed.reading && !ed.findOn
    val anims = remember { List(3) { Animatable(0f) } }          // 0 = "+" (paling kanan), 1 = redo, 2 = undo
    LaunchedEffect(editing) {
        anims.forEachIndexed { i, a ->
            launch {
                delay(if (editing) i * 55L else (2 - i) * 35L)
                a.animateTo(if (editing) 1f else 0f, spring(dampingRatio = .55f, stiffness = 400f))
            }
        }
    }
    if (!editing && anims.all { it.value < .001f }) return
    @Composable
    fun Pop(i: Int, content: @Composable () -> Unit) {
        val s = anims[i].value
        Box(Modifier.width((52f * s.coerceIn(0f, 1f)).dp), Alignment.Center) {
            Box(
                Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
                    .graphicsLayer { val k = s.coerceAtLeast(0f); scaleX = k; scaleY = k; rotationZ = (1f - s) * -60f }
            ) { content() }
        }
    }
    val plusOn = ed.sheet == 1
    val rot by animateFloatAsState(if (plusOn) 1f else 0f, M3E.spatial(), label = "plusrot")
    Row {
        Pop(2) { XBtn(rest = pal.c3, enabled = ed.canUndo && !ed.selOn, onClick = { ed.undo() }) { Ic("undo", it) } }
        Pop(1) { XBtn(rest = pal.c3, enabled = ed.canRedo && !ed.selOn, onClick = { ed.redo() }) { Ic("redo", it) } }
        Pop(0) {
            XBtn(on = plusOn, rest = pal.c3, onClick = {
                kb?.hide(); ed.sub = 0; ed.pickQuote = false; ed.sheetPage = 0
                ed.sheet = if (ed.sheet == 1) 0 else 1
            }) { Ic("plus", it, modifier = Modifier.graphicsLayer { rotationZ = 135f * rot }) }
        }
    }
}

// ------------------------------------------------------------------ ikon kecil
@Composable
private fun JustifyIcon(c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        for (y in listOf(5f, 9.5f, 14f, 18.5f)) drawLine(c, Offset(3f * u, y * u), Offset(21f * u, y * u), 2f * u, StrokeCap.Round)
    }
}

@Composable
private fun AlignIcon(a: String, c: Color) = when (a) {
    "j" -> JustifyIcon(c)
    "c" -> Ic("aC", c)
    "r" -> Ic("aR", c)
    else -> Ic("aL", c)
}

/** Ikon gambar dengan tanda plus kecil di pojok kanan atas: tambah gambar lain. */
@Composable
private fun AddImgIcon(c: Color) {
    val pal = LocalPal.current
    Box(Modifier.size(26.dp)) {
        Ic("img", c, 22.dp, Modifier.align(Alignment.BottomStart))
        Canvas(Modifier.size(13.dp).align(Alignment.TopEnd)) {
            drawCircle(pal.c3)
            val u = size.width
            drawLine(c, Offset(u * .25f, u * .5f), Offset(u * .75f, u * .5f), 1.8f * density, StrokeCap.Round)
            drawLine(c, Offset(u * .5f, u * .25f), Offset(u * .5f, u * .75f), 1.8f * density, StrokeCap.Round)
        }
    }
}

/** Tombol dengan segitiga kecil di sudut: tanda bahwa ia mengembangkan sub-bilah. Segitiganya membesar saat sub-bilah terbuka. */
@Composable
private fun DropBtn(on: Boolean, open: Boolean, enabled: Boolean, i: Int = -1, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val pal = LocalPal.current
    val hot = on || open
    val k by animateFloatAsState(if (open) 1.55f else 1f, M3E.spatialFast(), label = "dropk")
    Box(Modifier.popIn(i), contentAlignment = Alignment.BottomEnd) {
        XBtn(on = hot, enabled = enabled, onClick = onClick, content = icon)
        Canvas(
            Modifier.padding(end = 6.dp, bottom = 7.dp).size(7.dp)
                .graphicsLayer { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(1f, 1f) }
        ) {
            val p = Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(p, (if (hot) pal.op else pal.os).copy(alpha = .75f))
        }
    }
}

// ------------------------------------------------------------------ dock (wadah toolbar tunggal)
private const val K_TYPE = 0
private const val K_IMG = 1
private const val K_TABLE = 2
private const val K_QUOTE = 3
private const val K_OBJ = 4
private const val K_INK = 5

/**
 * SATU wadah untuk semua toolbar: ketik, gambar, tabel, kutipan, objek, dan coret. Berpindah toolbar = wadah ini mengubah bentuk
 * (lebar / tinggi mengalir dengan pegas, naik sedikit saat coret), isi lama menciut ke arah tombol yang ditekan dan isi baru
 * melompat masuk. Tidak ada fade. Wadah juga memuat panel Ink dan sub-bilah, jadi tinggi mengalir di dalam wadah yang sama.
 */
@Composable
private fun MorphDock(kind: Int, content: @Composable (Int) -> Unit) {
    val pal = LocalPal.current
    val lift by animateDpAsState(if (kind == K_INK) 30.dp else 8.dp, M3E.spatial(), label = "dklift")
    val shape = RoundedCornerShape(34.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 6.dp, bottom = lift.nn()),
        Alignment.BottomCenter
    ) {
        Box(Modifier.shadow(if (pal.dark) 10.dp else 6.dp, shape).clip(shape).background(pal.c3)) {
            MorphSwap(kind, originX = { a, b -> if (a == K_INK || b == K_INK) .86f else .5f }, label = "dock") { k -> content(k) }
        }
    }
}

/** Isi dock objek sisipan (tabel / kode / kutipan / garis): rata kiri-tengah-kanan, hapus, selesai. */
@Composable
fun ObjBarContent(ed: Ed, ob: Blk) {
    val al = ob.alOf()
    Row(
        Modifier.height(DockH).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SlideSeg(
            count = 3, sel = when (al) { "l" -> 0; "c" -> 1; "r" -> 2; else -> -1 }, i = 0,
            onPick = { ed.objAlign(ob.id, when (it) { 0 -> "l"; 1 -> "c"; else -> "r" }) }
        ) { k, c -> AlignArrow(k, c) }
        XBtn(tone = 1, i = 1, onClick = { ed.objDelete(ob.id) }) { Ic("del", it) }
        if (ed.selObj != null) XBtn(tone = 2, i = 2, w = 68.dp, onClick = { ed.selObj = null }) { Ic("ok", it) }
    }
}

// ------------------------------------------------------------------ dock bawah
@Composable
fun ToolDock(ed: Ed, vm: AppVM, act: Actions, showBar: Boolean) {
    val pal = LocalPal.current
    val selMode = ed.selOn
    val key = if (selMode) null else ed.focus
    val blk = key?.let { ed.blockOfKey(it) }
    val cell = key != null && isCellKey(key)
    val fl = if (selMode) FMT.filter { ed.selAllFmt(it) }.toSet() else ed.activeFlags()
    val ty = blk?.ty
    val noI = ty == "code"
    val noB = noI || ty == "h"
    val convOff = cell || ty == "img"
    val isLi = ty == "li" && !cell
    val kb = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val kindNow = when {
        ed.ink.on -> K_INK
        ed.selImg != null -> K_IMG
        ed.selObj != null -> K_OBJ
        cell -> K_TABLE
        ed.pickQuote -> K_QUOTE
        else -> K_TYPE
    }
    var objLast by remember { mutableStateOf<String?>(null) }
    var lastSel by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var subShown by remember { mutableStateOf(1) }
    SideEffect {
        ed.selImg?.let { lastSel = it }
        ed.selObj?.let { objLast = it }
        if (ed.sub != 0) subShown = ed.sub
    }
    // dock ikut tampil saat coret aktif (bilah ketik disembunyikan oleh pemanggil, tapi wadahnya TETAP: isinya yang berubah bentuk)
    val visible = ed.ink.on || (showBar && ed.sheet == 0)
    var kindHeld by remember { mutableStateOf(K_TYPE) }
    SideEffect { if (visible) kindHeld = kindNow }
    val kind = if (visible) kindNow else kindHeld

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(M3E.spatial(), expandFrom = Alignment.Top) +
            scaleIn(M3E.spatial(), .86f, TransformOrigin(.5f, 1f)),
        // sheet membuka => dock hilang seketika, supaya tidak ada gerakan ganda di atas sheet
        exit = if (ed.sheet != 0) ExitTransition.None
        else shrinkVertically(M3E.spatial(), shrinkTowards = Alignment.Top) +
            scaleOut(M3E.spatial(), .86f, TransformOrigin(.5f, 1f))
    ) {
        MorphDock(kind) { k ->
            when (k) {
                K_IMG -> {
                    val s = ed.selImg ?: lastSel
                    if (s == null) Spacer(Modifier.height(DockH).width(1.dp)) else {
                        val b = ed.blocks.firstOrNull { it.id == s.first }
                        val inRow = (b?.im?.size ?: 1) > 1
                        Row(
                            Modifier.height(DockH).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            XGroup(i = 0) {
                                // ganti isi bingkai dengan foto lain (galeri / kamera); ukuran bingkai tidak berubah
                                XBtn(onClick = {
                                    val tgt = ed.selImg ?: lastSel
                                    ed.replT = tgt
                                    if (tgt != null) act.replaceImage(ed.noteId, tgt.first, tgt.second)
                                }) { Ic("swap", it) }
                                XBtn(onClick = { ed.replT = ed.selImg ?: lastSel; act.takePhoto(true) }) { Ic("cam", it) }
                                // tambah gambar lain (disisipkan tepat sesudah gambar ini)
                                XBtn(onClick = { ed.replT = null; act.pickImages() }) { AddImgIcon(it) }
                            }
                            if (!inRow) {
                                val al = b?.im?.getOrNull(s.second)?.al
                                SlideSeg(
                                    count = 3, sel = when (al) { "l" -> 0; "c" -> 1; "r" -> 2; else -> -1 }, i = 1,
                                    onPick = { ed.imgAlign(when (it) { 0 -> "l"; 1 -> "c"; else -> "r" }) }
                                ) { n, c -> Ic(when (n) { 0 -> "aL"; 1 -> "aC"; else -> "aR" }, c) }
                            }
                            XGroup(i = 2) {
                                XBtn(onClick = { ed.toggleRow() }) { Ic(if (inRow) "rows" else "cols", it) }
                                XBtn(onClick = { ed.imgMove(-1) }) { Ic("aup", it) }
                                XBtn(onClick = { ed.imgMove(1) }) { Ic("adn", it) }
                            }
                            // hapus dijauhkan dari tombol yang sering dipakai (ramah jempol: tidak salah ketuk)
                            XBtn(tone = 1, i = 3, onClick = { ed.imgDelete() }) { Ic("del", it) }
                        }
                    }
                }
                K_OBJ -> {
                    // objek sisipan (tabel / kode / kutipan / garis) terpilih tanpa keyboard
                    val ob = ed.blocks.firstOrNull { it.id == (ed.selObj ?: objLast) }
                    if (ob == null) Spacer(Modifier.height(DockH).width(1.dp)) else ObjBarContent(ed, ob)
                }
                K_TABLE -> Row(
                    Modifier.height(DockH).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ar" to "+ Row", "ac" to "+ Column", "dr" to "− Row", "dc" to "− Column").forEachIndexed { n, (op, l) ->
                        CapChip(n, l, pal.c4, pal.os, RoundedCornerShape(24.dp)) { ed.tOp(op) }
                    }
                    var n2 = 4
                    if (blk != null && blk.cw.isNotEmpty()) CapChip(n2++, "Reset widths", pal.c4, pal.os, RoundedCornerShape(24.dp)) { ed.tblResetCols(blk.id) }
                    CapChip(n2, "Delete table", pal.ec, pal.oec, RoundedCornerShape(24.dp)) { ed.tOp("dt") }
                }
                K_QUOTE -> Row(
                    Modifier.height(DockH).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VARS.forEachIndexed { n, (v, l) ->
                        val q = qs(v, pal)
                        CapChip(n, l, if (v == "def") pal.c4 else q.bg, q.fg, q.shape, 52.dp, 104.dp) { ed.pickQuote = false; ed.setQuote(v) }
                    }
                    if (ty == "q") CapChip(VARS.size, "Plain text", pal.c4, pal.os, RoundedCornerShape(24.dp)) { ed.pickQuote = false; ed.setPlain() }
                }
                K_INK -> InkDockContent(ed, vm)
                else -> Column(Modifier.fillMaxWidth()) {
                    // sub-bilah (perataan / poin): tumbuh ke atas DI DALAM wadah yang sama, jadi wadahnya yang membesar
                    AnimatedVisibility(
                        visible = ed.sub != 0,
                        enter = expandVertically(M3E.spatial()),
                        exit = shrinkVertically(M3E.spatial())
                    ) {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), Alignment.Center) {
                            MorphSwap(subShown, label = "sub") { sub ->
                                if (sub == 1) {
                                    val cur = blk?.ta ?: ""
                                    val alOk = !cell && ty != "code" && ty != "q" && ty != "hr" && ty != "tbl" && ty != "img"
                                    XGroup {
                                        listOf("" to "aL", "c" to "aC", "r" to "aR", "j" to "aJ").forEachIndexed { n, (a, ic) ->
                                            XBtn(i = n, on = cur == a, enabled = alOk, onClick = { ed.setAlign(a); ed.sub = 0 }) {
                                                if (ic == "aJ") JustifyIcon(it) else Ic(ic, it)
                                            }
                                        }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        XGroup {
                                            XBtn(i = 0, on = isLi && blk?.lt == "b", enabled = !convOff, onClick = { ed.setList("b"); ed.sub = 0 }) { Ic("ul", it) }
                                            XBtn(i = 1, on = isLi && blk?.lt == "o", enabled = !convOff, onClick = { ed.setList("o"); ed.sub = 0 }) { Ic("ol", it) }
                                            XBtn(i = 2, on = isLi && blk?.lt == "a", enabled = !convOff, onClick = { ed.setList("a"); ed.sub = 0 }) { LetterListIcon(it) }
                                            XBtn(i = 3, on = isLi && blk?.lt == "c", enabled = !convOff, onClick = { ed.setList("c"); ed.sub = 0 }) { Ic("chk", it) }
                                        }
                                        XGroup {
                                            XBtn(i = 4, enabled = isLi, onClick = { ed.indent(-1) }) { Ic("out", it) }
                                            XBtn(i = 5, enabled = isLi, onClick = { ed.indent(1) }) { Ic("ind", it) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(DockH).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        XGroup(i = 0) {
                            XBtn(on = ed.sheet == 2, onClick = {
                                kb?.hide(); ed.sub = 0
                                ed.sheet = if (ed.sheet == 2) 0 else 2
                            }) { c -> T("A", 21f, 900, c) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            XGroup(i = 1) {
                                XBtn(on = K.B in fl && !noB, enabled = !noB, onClick = { ed.sub = 0; ed.toggleFmt(K.B) }) { Ic("b", it) }
                                XBtn(on = K.I in fl && !noI, enabled = !noI, onClick = { ed.sub = 0; ed.toggleFmt(K.I) }) { Ic("i", it) }
                                XBtn(on = K.U in fl && !noI, enabled = !noI, onClick = { ed.sub = 0; ed.toggleFmt(K.U) }) { Ic("u", it) }
                                // stabilo coretan (alat Stabilo di mode coret)
                                XBtn(on = ed.ink.on && ed.ink.tool == 1, onClick = {
                                    ed.sub = 0
                                    val ik = ed.ink
                                    if (!ik.on) ik.toggle(ed)
                                    ik.hide = false
                                    ik.tool = 1; ik.pop = false; ik.sel = emptySet(); ik.box = null
                                    ik.stamp = System.currentTimeMillis()
                                }) { InkToolIcon(1, it) }
                            }
                            XGroup(i = 2) {
                                XBtn(on = ed.recActive, enabled = !ed.recActive && !selMode, onClick = {
                                    ed.sub = 0
                                    act.requestMic {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        ed.startVoice()
                                    }
                                }) { Ic("mic", it) }
                                DropBtn(isLi, ed.sub == 2, !convOff, onClick = { ed.sub = if (ed.sub == 2) 0 else 2 }) { Ic("ul", it) }
                            }
                            // tabel, kutipan, kode, garis, foto: letaknya diatur langsung dengan seret, jadi tombol perataan tidak ada
                            val ta = blk?.ta ?: ""
                            XGroup(i = 3) {
                                if (!cell && ty != "code" && ty != "q" && ty != "hr" && ty != "tbl" && ty != "img") {
                                    DropBtn(false, ed.sub == 1, true, onClick = { ed.sub = if (ed.sub == 1) 0 else 1 }) { AlignIcon(ta, it) }
                                }
                                XBtn(on = K.M in fl && !noI, enabled = !noI, onClick = { ed.sub = 0; ed.toggleFmt(K.M) }) { Ic("mark", it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ sheet bawah
/** Satu ubin di sheet Sisipkan. */
private class SheetTile(val label: String, val enabled: Boolean, val icon: @Composable (Color) -> Unit, val onClick: () -> Unit)

/** Ubin besar (ramah jempol): squircle tonal, ikon di atas label. Ditekan = sudutnya mengecil jadi lebih kotak dan menyempit membal. */
@Composable
private fun SheetTileView(t: SheetTile, i: Int, modifier: Modifier) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else 30.dp, M3E.spatialFast(), label = "tr")
    val sc by animateFloatAsState(if (pr) .93f else 1f, spring(.45f, 700f), label = "ts")
    Column(
        modifier.popIn(i, 40L)
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (t.enabled) 1f else .32f }
            .height(92.dp).clip(RoundedCornerShape(r.nn())).background(pal.c4)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = t.enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                t.onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        t.icon(pal.os)
        Spacer(Modifier.height(8.dp))
        T(t.label, 12.5f, 700, pal.os)
    }
}

@Composable
private fun SRow(on: Boolean = false, enabled: Boolean = true, icon: @Composable () -> Unit, label: @Composable () -> Unit, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else 22.dp, M3E.spatialFast(), label = "srr")
    val ir by animateDpAsState(if (on) 15.dp else 21.dp, M3E.spatialFast(), label = "sri")
    val bg by animateColorAsState(if (on) pal.pc.copy(alpha = .6f) else pal.pc.copy(alpha = 0f), M3E.effects(), label = "srb")
    val ibg by animateColorAsState(if (on) pal.p.copy(alpha = .16f) else pal.c4, M3E.effects(), label = "sribg")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp).height(58.dp)
            .clip(RoundedCornerShape(r.nn())).background(bg).alpha(if (enabled) 1f else .35f)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(ir.nn())).background(ibg), Alignment.Center) { icon() }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) { label() }
        if (on) Ic("ok", pal.p)
    }
}

@Composable
private fun Stepper(label: String, v: Int, lo: Int, hi: Int, onChange: (Int) -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 17f, 500, pal.os, Modifier.weight(1f))
        XGroup {
            XBtn(enabled = v > lo, onClick = { onChange(v - 1) }) { T("−", 24f, 700, it) }
            Box(Modifier.width(52.dp), Alignment.Center) { T("$v", 20f, 800, pal.os) }
            XBtn(enabled = v < hi, onClick = { onChange(v + 1) }) { T("+", 22f, 700, it) }
        }
    }
}

/** Ikon inden. kind: 0 kiri, 1 kanan, 2 baris pertama, 3 menggantung. */
@Composable
private fun IndentIcon(kind: Int, c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val ys = listOf(5f, 10f, 14f, 19f)
        ys.forEachIndexed { n, y ->
            val (a, b) = when (kind) {
                0 -> 10f to 21f
                1 -> 3f to 14f
                2 -> (if (n == 0) 9f else 3f) to 21f
                else -> (if (n == 0) 3f else 9f) to 21f
            }
            drawLine(c, Offset(a * u, y * u), Offset(b * u, y * u), 2f * u, StrokeCap.Round)
        }
    }
}

@Composable
fun DocSheet(ed: Ed, act: Actions, vm: AppVM, holdH: Int = 0) {
    val pal = LocalPal.current
    // nilai sheet terakhir yang aktif ditulis langsung saat komposisi (bukan SideEffect), jadi konten tidak pernah "lompat" saat sheet dibuka
    val last = remember { intArrayOf(1) }
    if (ed.sheet != 0) last[0] = ed.sheet
    val shown = last[0]
    LaunchedEffect(ed.sheet) {                                   // kembali ke daftar setelah sheet selesai menutup
        if (ed.sheet == 0) { delay(380); if (ed.sheet == 0) ed.sheetPage = 0 }
    }
    // geser-tutup: hanya dari bilah pegangan (di luar area gulir), jadi tidak pernah bentrok dengan gulir isi sheet
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val closeSheet by rememberUpdatedState({ ed.sheet = 0 })
    LaunchedEffect(ed.sheet) {
        if (ed.sheet != 0) drag.snapTo(0f) else { delay(420); if (ed.sheet == 0) drag.snapTo(0f) }
    }
    val selMode = ed.selOn
    val key = if (selMode) null else ed.focus
    val blk = key?.let { ed.blockOfKey(it) }
    val cell = key != null && isCellKey(key)
    val ty = blk?.ty
    val blkOff = cell || selMode
    val convOff = cell || ty == "img"
    val fl = if (selMode) FMT.filter { ed.selAllFmt(it) }.toSet() else ed.activeFlags()

    // dibuka dari keyboard (holdH > 0): sheet menempati persis tempat keyboard tadi, tingginya tetap, dan muncul tanpa expand / membal;
    // area catatan tidak bergerak sama sekali. Dibuka tanpa keyboard: perilaku lama (tinggi mengikuti isi).
    // Gerak sheet: MENGGESER dari bawah layar ke atas (dan sebaliknya saat menutup), tanpa fade dan tanpa pantulan.
    // Satu progres `prog` (tween dengan kurva emphasized, bukan spring) menggerakkan dua hal sekaligus:
    //  - posisi sheet (translasi), dan
    //  - tinggi yang dilaporkan ke tata letak, jadi area catatan di atasnya ikut naik/turun searah, tanpa lompatan.
    val prog = remember { Animatable(0f) }
    var alive by remember { mutableStateOf(false) }
    val open = ed.sheet != 0
    val fx = remember { booleanArrayOf(false) }        // mode "menggantikan keyboard" dikunci saat sheet mulai dibuka
    val startH = remember { floatArrayOf(0f) }         // tinggi bilah yang diganti sheet saat mulai dibuka
    if (open && prog.targetValue < .5f) {
        fx[0] = holdH > 0
        startH[0] = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { ed.barHLive }
    }
    LaunchedEffect(open) {
        if (open) {
            alive = true
            prog.animateTo(1f, tween(320, easing = EmphDecel))
        } else {
            prog.animateTo(0f, tween(230, easing = EmphAccel))
            alive = false
        }
    }
    if (!alive && !open) return
    val fixed = fx[0]
    val holdDp = with(androidx.compose.ui.platform.LocalDensity.current) { holdH.toDp() }
    Box(
        Modifier.fillMaxWidth()
            .layout { m, c ->
                val pl = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val p = prog.value
                val h = when {
                    fixed -> pl.height                                                       // menggantikan keyboard: tinggi tetap, hanya meluncur
                    ed.sheet != 0 -> (startH[0] + (pl.height - startH[0]) * p).roundToInt()  // membuka: dari tinggi bilah lama ke tinggi penuh
                    else -> (pl.height * p).roundToInt()                                     // menutup: mengecil ke nol
                }
                layout(pl.width, h) { pl.place(0, 0) }
            }
            .graphicsLayer {
                val p = prog.value
                translationY = when {
                    fixed -> (1f - p) * size.height
                    ed.sheet != 0 -> (1f - p) * startH[0]
                    else -> 0f
                }
            }
    ) {
        // kotak penuh: tanpa sudut bundar, tanpa pegangan
        Column(
            Modifier.fillMaxWidth().graphicsLayer { translationY = drag.value }
                .shadow(if (pal.dark) 12.dp else 8.dp, RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)).background(pal.c3)
                .then(if (fixed) Modifier.height(holdDp) else Modifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Box(
                Modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val vt = VelocityTracker()
                        vt.addPosition(down.uptimeMillis, down.position)
                        var acc = 0f
                        verticalDrag(down.id) { ch ->
                            vt.addPosition(ch.uptimeMillis, ch.position)
                            acc = (acc + ch.positionChange().y).coerceAtLeast(0f)
                            ch.consume()
                            val a = acc
                            scope.launch { drag.snapTo(a) }
                        }
                        val vy = vt.calculateVelocity().y
                        val end = acc
                        scope.launch {
                            if (end > 80f * dens || (end > 8f * dens && vy > 1100f)) closeSheet()
                            else drag.animateTo(0f, spring(dampingRatio = .8f, stiffness = 500f))
                        }
                    }
                },
                Alignment.Center
            ) {
                Box(Modifier.padding(top = 4.dp).size(width = 40.dp, height = 5.dp).clip(CircleShape).background(pal.osv.copy(alpha = .38f)))
            }
            val page = if (shown == 2) 2 else if (ed.sheetPage == 1) 1 else 0     // 0 Sisipkan, 1 Tabel, 2 Format
            // pergantian halaman: wadah mengalir ke tinggi halaman baru, isi lama menciut, isi baru melompat masuk (tanpa fade)
            MorphSwap(page, clip = true, label = "sheetpage") { pg ->
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pg == 1) { XBtn(rest = pal.c4, i = 0, onClick = { ed.sheetPage = 0 }) { Ic("chl", it) }; Spacer(Modifier.width(10.dp)) }
                        else Spacer(Modifier.width(12.dp))
                        T(if (pg == 2) "Format" else if (pg == 1) "Table" else "Insert", 22f, 800, pal.os, Modifier.weight(1f))
                        XBtn(rest = pal.c4, onClick = { ed.sheet = 0 }) { Ic("x", it) }
                    }
                    // tinggi dibatasi dan bisa digulir: daftar boleh bertambah tanpa membuat sheet memanjang
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        when (pg) {
                            0 -> {
                                val tiles = listOf(
                                    SheetTile("Image", !blkOff, { Ic("img", it) }) { ed.sheet = 0; act.pickImages() },
                                    SheetTile("Camera", !blkOff, { Ic("cam", it) }) { ed.sheet = 0; act.takePhoto(false) },
                                    SheetTile("Table", !blkOff, { Ic("table", it) }) { ed.sheetPage = 1 },     // geser ke pengaturan baris dan kolom
                                    SheetTile("Drawing", true, { InkToolIcon(4, it) }) { ed.sheet = 0; ed.ink.toggle(ed) },
                                    SheetTile("Quote", !convOff, { Ic("quote", it) }) { ed.sheet = 0; if (ed.focus != null) ed.pickQuote = true },
                                    SheetTile("Code", !convOff, { Ic("pre", it) }) { ed.sheet = 0; ed.setCode() },
                                    SheetTile("Divider", !blkOff, { Ic("hr", it) }) { ed.sheet = 0; ed.insHr() }
                                )
                                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    tiles.chunked(4).forEachIndexed { r, row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            row.forEachIndexed { c, t -> SheetTileView(t, r * 4 + c, Modifier.weight(1f)) }
                                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                Stepper("Rows", ed.tbR, 1, 20) { ed.tbR = it }
                                Stepper("Columns", ed.tbC, 1, 6) { ed.tbC = it }
                                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), Alignment.Center) {
                                    Canvas(Modifier.size(width = 150.dp, height = 64.dp)) {   // pratinjau kisi
                                        val st = Stroke(1.5f * density)
                                        drawRect(pal.osv, style = st)
                                        for (r in 1 until ed.tbR) { val y = size.height * r / ed.tbR; drawLine(pal.osv, Offset(0f, y), Offset(size.width, y), 1.2f * density) }
                                        for (c in 1 until ed.tbC) { val x = size.width * c / ed.tbC; drawLine(pal.osv, Offset(x, 0f), Offset(x, size.height), 1.2f * density) }
                                    }
                                }
                                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
                                    Chip("Insert table", pal.p, pal.op, RoundedCornerShape(28.dp), 56.dp, 240.dp) {
                                        ed.sheet = 0; ed.insTable(ed.tbR, ed.tbC)
                                    }
                                }
                            }
                            else -> {
                                // ---- ukuran teks: per catatan (Note.fs); 0 = ikut bawaan ----
                                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        T("Text size", 17f, 500, pal.os)
                                        T("${vm.noteFs(ed.noteId)} px", 16f, 600, pal.p)
                                    }
                                    BigSlider(vm.noteFs(ed.noteId).toFloat(), 14f, 30f) { v -> vm.upd(ed.noteId) { it.copy(fs = v.toInt()) } }
                                }
                                Spacer(Modifier.height(8.dp))
                                SRow(on = ty == "p", enabled = !convOff, icon = { T("T", 20f, 700, pal.os) }, label = { T("Plain text", 17f, 500, pal.os) }) {
                                    ed.setPlain()
                                }
                                listOf(1 to 26f, 2 to 23f, 3 to 20f, 4 to 18f).forEach { (lv, sz) ->
                                    SRow(on = ty == "h" && blk?.lv == lv, enabled = !convOff, icon = { T("H$lv", 15f, 900, pal.p) },
                                        label = { T(if (lv == 4) "Subheading" else "Heading $lv", sz, 900, pal.os) }) {
                                        ed.setHead(lv)
                                    }
                                }
                                SRow(on = K.S in fl, enabled = ty != "code", icon = { Ic("s", pal.os) }, label = { T("Strikethrough", 17f, 500, pal.os) }) {
                                    ed.toggleFmt(K.S)
                                }
                                SRow(on = K.C in fl, enabled = ty != "code", icon = { Ic("code", pal.os) }, label = { T("Inline code", 17f, 500, pal.os) }) {
                                    ed.toggleFmt(K.C)
                                }
                                // ---- inden paragraf: satu ketukan = satu inden, ketuk lagi = lepas ----
                                val indLR = !blkOff && blk != null && (ty == "p" || ty == "h" || ty == "li" || ty == "q")
                                val indSp = !blkOff && blk != null && (ty == "p" || ty == "h" || ty == "li")
                                Spacer(Modifier.height(8.dp))
                                T("Indent", 15f, 800, pal.p, Modifier.padding(start = 22.dp, top = 12.dp, bottom = 2.dp))
                                val il = blk?.il ?: 0
                                val ir = blk?.ir ?: 0
                                val fi = blk?.fi ?: 0
                                SRow(on = il > 0, enabled = indLR, icon = { IndentIcon(0, pal.os) }, label = { T("Left indent", 17f, 500, pal.os) }) {
                                    ed.setIndent(0, if (il > 0) 0 else IND_UNIT)
                                }
                                SRow(on = ir > 0, enabled = indLR, icon = { IndentIcon(1, pal.os) }, label = { T("Right indent", 17f, 500, pal.os) }) {
                                    ed.setIndent(1, if (ir > 0) 0 else IND_UNIT)
                                }
                                SRow(on = fi > 0, enabled = indSp, icon = { IndentIcon(2, pal.os) }, label = { T("First-line indent", 17f, 500, pal.os) }) {
                                    ed.setIndent(2, if (fi > 0) 0 else IND_UNIT)
                                }
                                SRow(on = fi < 0, enabled = indSp, icon = { IndentIcon(3, pal.os) }, label = { T("Hanging indent", 17f, 500, pal.os) }) {
                                    ed.setIndent(3, if (fi < 0) 0 else IND_UNIT)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

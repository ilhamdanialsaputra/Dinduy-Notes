package app.dinduy

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.unit.dp

// =====================================================================================
//  Toolbar gaya Google Docs.
//  - Bilah atas (mode ketik): centang (masuk tampilan penuh), undo, redo, "+" (sheet Sisipkan).
//  - Bilah bawah datar: "A" (sheet Format) | B  I  U  stabilo | perataan ▾ | poin ▾.
//  - Perataan dan poin membuka sub-bilah kecil di atas bilah utama (tombolnya tetap satu).
//  - Gambar / tabel / kutipan: bilah bawah berganti jadi alatnya sendiri.
//  - Sheet bawah tingginya dibatasi dan bisa digulir, jadi tidak memanjang kalau isinya bertambah.
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

/** Undo, redo, "+": masing-masing membuka ruangnya dan "melompat" masuk dengan pegas, berurutan dari kanan. */
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
    Row {
        Pop(2) { IconBtn("undo", pal.os, { ed.undo() }, enabled = ed.canUndo && !ed.selOn) }
        Pop(1) { IconBtn("redo", pal.os, { ed.redo() }, enabled = ed.canRedo && !ed.selOn) }
        Pop(0) {
            IconBtn("plus", if (ed.sheet == 1) pal.p else pal.os, {
                kb?.hide(); ed.sub = 0; ed.pickQuote = false; ed.sheetPage = 0
                ed.sheet = if (ed.sheet == 1) 0 else 1
            })
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

/** Tombol dengan segitiga kecil di sudut: tanda bahwa ia mengembangkan sub-bilah. */
@Composable
private fun DropBtn(on: Boolean, open: Boolean, enabled: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val pal = LocalPal.current
    val hot = on || open
    Box(contentAlignment = Alignment.BottomEnd) {
        Tbn(icon, hot, enabled, onClick)
        Canvas(Modifier.padding(end = 5.dp, bottom = 6.dp).size(7.dp)) {
            val p = Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(p, (if (hot) pal.op else pal.os).copy(alpha = .75f))
        }
    }
}

// ------------------------------------------------------------------ bilah bawah
@Composable
fun DocBar(ed: Ed, act: Actions) {
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
    val kindNow = if (ed.selImg != null) 1 else if (cell) 2 else if (ed.pickQuote) 3 else 0
    var lastSel by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var subShown by remember { mutableStateOf(1) }
    SideEffect {
        ed.selImg?.let { lastSel = it }
        if (ed.sub != 0) subShown = ed.sub
    }

    Column(Modifier.fillMaxWidth().background(pal.c3)) {
        // sub-bilah: perataan atau poin (mengembang di atas bilah utama)
        AnimatedVisibility(
            visible = ed.sub != 0 && kindNow == 0,
            enter = fadeIn(tween(200)) + expandVertically(tween(260, easing = Emph)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(200, easing = Emph))
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), Alignment.Center) {
                Row(
                    Modifier.clip(RoundedCornerShape(26.dp)).background(pal.c4).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    if (subShown == 1) {
                        val cur = blk?.ta ?: ""
                        listOf("" to "aL", "c" to "aC", "r" to "aR", "j" to "aJ").forEach { (a, ic) ->
                            Tbn({ if (ic == "aJ") JustifyIcon(it) else Ic(ic, it) }, cur == a, !cell && ty != "code" && ty != "img") {
                                ed.setAlign(a); ed.sub = 0
                            }
                        }
                    } else {
                        Tbn({ Ic("ul", it) }, isLi && blk?.lt == "b", !convOff) { ed.setList("b"); ed.sub = 0 }
                        Tbn({ Ic("ol", it) }, isLi && blk?.lt == "o", !convOff) { ed.setList("o"); ed.sub = 0 }
                        Tbn({ Ic("chk", it) }, isLi && blk?.lt == "c", !convOff) { ed.setList("c"); ed.sub = 0 }
                        Sep()
                        Tbn({ Ic("out", it) }, false, isLi) { ed.indent(-1) }
                        Tbn({ Ic("ind", it) }, false, isLi) { ed.indent(1) }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.ol.copy(alpha = .35f)))
        AnimatedContent(
            targetState = kindNow,
            transitionSpec = { (fadeIn(tween(220, easing = Emph)) + slideInVertically(tween(260, easing = Emph)) { it / 3 }).togetherWith(fadeOut(tween(120))) },
            label = "docbar"
        ) { kind ->
            when (kind) {
                1 -> {
                    val s = ed.selImg ?: lastSel
                    if (s == null) Spacer(Modifier.height(54.dp)) else {
                        val b = ed.blocks.firstOrNull { it.id == s.first }
                        val inRow = (b?.im?.size ?: 1) > 1
                        Row(
                            Modifier.fillMaxWidth().height(54.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ganti isi bingkai dengan foto lain (galeri / kamera); ukuran bingkai tidak berubah
                            Chip("Ganti foto", pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.replT = ed.selImg ?: lastSel; act.replaceImage() }
                            Tbn({ Ic("cam", it) }, false, true) { ed.replT = ed.selImg ?: lastSel; act.takePhoto(true) }
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
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ar" to "+ Baris", "ac" to "+ Kolom", "dr" to "− Baris", "dc" to "− Kolom").forEach { (k, l) ->
                        Chip(l, pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.tOp(k) }
                    }
                    Chip("Hapus tabel", pal.ec, pal.oec, RoundedCornerShape(23.dp)) { ed.tOp("dt") }
                }
                3 -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VARS.forEach { (k, l) ->
                        val q = qs(k, pal)
                        Chip(l, if (k == "def") pal.c4 else q.bg, q.fg, q.shape, 52.dp, 104.dp) { ed.pickQuote = false; ed.setQuote(k) }
                    }
                    if (ty == "q") Chip("Teks biasa", pal.c4, pal.os, RoundedCornerShape(23.dp)) { ed.pickQuote = false; ed.setPlain() }
                }
                else -> Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tbn({ c -> T("A", 21f, 900, c) }, ed.sheet == 2, true) {
                        kb?.hide(); ed.sub = 0
                        ed.sheet = if (ed.sheet == 2) 0 else 2
                    }
                    Sep()
                    Row(
                        Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tbn({ Ic("b", it) }, K.B in fl && !noB, !noB) { ed.sub = 0; ed.toggleFmt(K.B) }
                        Tbn({ Ic("i", it) }, K.I in fl && !noI, !noI) { ed.sub = 0; ed.toggleFmt(K.I) }
                        Tbn({ Ic("u", it) }, K.U in fl && !noI, !noI) { ed.sub = 0; ed.toggleFmt(K.U) }
                        Tbn({ Ic("mark", it) }, K.M in fl && !noI, !noI) { ed.sub = 0; ed.toggleFmt(K.M) }
                        Sep()
                        val ta = blk?.ta ?: ""
                        DropBtn(false, ed.sub == 1, !cell && ty != "code" && ty != "img", { ed.sub = if (ed.sub == 1) 0 else 1 }) { AlignIcon(ta, it) }
                        DropBtn(isLi, ed.sub == 2, !convOff, { ed.sub = if (ed.sub == 2) 0 else 2 }) { Ic("ul", it) }
                        Sep()
                        // voice note: sekarang di bilah utama (tidak lagi di sheet Sisipkan)
                        Tbn({ Ic("mic", it) }, ed.recActive, !ed.recActive && !selMode) {
                            ed.sub = 0
                            act.requestMic {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                ed.startVoice()
                            }
                        }
                        // stabilo coretan (alat Stabilo di mode coret). Stabilo teks (ikon di atas) tetap ada: jadi ada dua stabilo di bilah ini
                        Tbn({ InkToolIcon(1, it) }, ed.ink.on && ed.ink.tool == 1, true) {
                            ed.sub = 0
                            val ik = ed.ink
                            if (!ik.on) ik.toggle(ed)
                            ik.hide = false
                            ik.tool = 1; ik.pop = false; ik.sel = emptySet(); ik.box = null
                            ik.stamp = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ sheet bawah
@Composable
private fun SRow(on: Boolean = false, enabled: Boolean = true, icon: @Composable () -> Unit, label: @Composable () -> Unit, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp).height(56.dp).clip(RoundedCornerShape(18.dp))
            .background(if (on) pal.pc.copy(alpha = .6f) else Color.Transparent).alpha(if (enabled) 1f else .35f)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if (on) pal.p.copy(alpha = .16f) else pal.c4), Alignment.Center) { icon() }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) { label() }
        if (on) Ic("ok", pal.p)
    }
}

@Composable
private fun Stepper(label: String, v: Int, lo: Int, hi: Int, onChange: (Int) -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 17f, 500, pal.os, Modifier.weight(1f))
        Chip("−", pal.c4, pal.os, CircleShape, 44.dp, 44.dp) { if (v > lo) onChange(v - 1) }
        Box(Modifier.width(56.dp), Alignment.Center) { T("$v", 20f, 800, pal.os) }
        Chip("+", pal.c4, pal.os, CircleShape, 44.dp, 44.dp) { if (v < hi) onChange(v + 1) }
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
fun DocSheet(ed: Ed, act: Actions, vm: AppVM) {
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

    AnimatedVisibility(
        visible = ed.sheet != 0,
        enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = Emph), expandFrom = Alignment.Top),
        exit = fadeOut(tween(140)) + shrinkVertically(tween(260, easing = Emph), shrinkTowards = Alignment.Top)
    ) {
        // kotak penuh: tanpa sudut bundar, tanpa pegangan
        Column(
            Modifier.fillMaxWidth().graphicsLayer { translationY = drag.value }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(pal.c3)
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
                Box(Modifier.padding(top = 4.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(pal.osv.copy(alpha = .38f)))
            }
            val page = if (shown == 2) 2 else if (ed.sheetPage == 1) 1 else 0     // 0 Sisipkan, 1 Tabel, 2 Format
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(180, 60)).togetherWith(fadeOut(tween(100))) },
                label = "sheetpage"
            ) { pg ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = if (pg == 1) 6.dp else 22.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (pg == 1) IconBtn("chl", pal.os, { ed.sheetPage = 0 })
                        T(if (pg == 2) "Format" else if (pg == 1) "Tabel" else "Sisipkan", 20f, 800, pal.os, Modifier.weight(1f).padding(vertical = 12.dp))
                        IconBtn("x", pal.os, { ed.sheet = 0 })
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.ol.copy(alpha = .35f)))
                    // tinggi dibatasi dan bisa digulir: daftar boleh bertambah tanpa membuat sheet memanjang
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        when (pg) {
                            0 -> {
                                SRow(enabled = !blkOff, icon = { Ic("img", pal.os) }, label = { T("Gambar", 17f, 500, pal.os) }) {
                                    ed.sheet = 0; act.pickImages()
                                }
                                SRow(enabled = !blkOff, icon = { Ic("cam", pal.os) }, label = { T("Kamera", 17f, 500, pal.os) }) {
                                    ed.sheet = 0; act.takePhoto(false)
                                }
                                SRow(enabled = !blkOff, icon = { Ic("table", pal.os) }, label = { T("Tabel", 17f, 500, pal.os) }) {
                                    ed.sheetPage = 1                       // geser ke pengaturan baris dan kolom
                                }
                                SRow(icon = { InkToolIcon(4, pal.os) }, label = { T("Coretan", 17f, 500, pal.os) }) {
                                    ed.sheet = 0; ed.ink.toggle(ed)
                                }
                                SRow(enabled = !convOff, icon = { Ic("quote", pal.os) }, label = { T("Kutipan", 17f, 500, pal.os) }) {
                                    ed.sheet = 0; if (ed.focus != null) ed.pickQuote = true
                                }
                                SRow(enabled = !convOff, icon = { Ic("pre", pal.os) }, label = { T("Kode", 17f, 500, pal.os) }) {
                                    ed.sheet = 0; ed.setCode()
                                }
                                SRow(enabled = !blkOff, icon = { Ic("hr", pal.os) }, label = { T("Garis horizontal", 17f, 500, pal.os) }) {
                                    ed.sheet = 0; ed.insHr()
                                }
                            }
                            1 -> {
                                Stepper("Baris", ed.tbR, 1, 20) { ed.tbR = it }
                                Stepper("Kolom", ed.tbC, 1, 6) { ed.tbC = it }
                                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), Alignment.Center) {
                                    Canvas(Modifier.size(width = 150.dp, height = 64.dp)) {   // pratinjau kisi
                                        val st = Stroke(1.5f * density)
                                        drawRect(pal.osv, style = st)
                                        for (r in 1 until ed.tbR) { val y = size.height * r / ed.tbR; drawLine(pal.osv, Offset(0f, y), Offset(size.width, y), 1.2f * density) }
                                        for (c in 1 until ed.tbC) { val x = size.width * c / ed.tbC; drawLine(pal.osv, Offset(x, 0f), Offset(x, size.height), 1.2f * density) }
                                    }
                                }
                                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
                                    Chip("Sisipkan tabel", pal.p, pal.op, RoundedCornerShape(26.dp), 52.dp, 220.dp) {
                                        ed.sheet = 0; ed.insTable(ed.tbR, ed.tbC)
                                    }
                                }
                            }
                            else -> {
                                // ---- ukuran teks: per catatan (Note.fs); 0 = ikut bawaan ----
                                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        T("Ukuran teks", 17f, 500, pal.os)
                                        T("${vm.noteFs(ed.noteId)} px", 16f, 600, pal.p)
                                    }
                                    BigSlider(vm.noteFs(ed.noteId).toFloat(), 14f, 30f) { v -> vm.upd(ed.noteId) { it.copy(fs = v.toInt()) } }
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 22.dp).background(pal.ol.copy(alpha = .35f)))
                                SRow(on = ty == "p", enabled = !convOff, icon = { T("T", 20f, 700, pal.os) }, label = { T("Teks biasa", 17f, 500, pal.os) }) {
                                    ed.setPlain()
                                }
                                listOf(1 to 26f, 2 to 23f, 3 to 20f, 4 to 18f).forEach { (lv, sz) ->
                                    SRow(on = ty == "h" && blk?.lv == lv, enabled = !convOff, icon = { T("H$lv", 15f, 900, pal.p) },
                                        label = { T(if (lv == 4) "Sub-judul" else "Judul $lv", sz, 900, pal.os) }) {
                                        ed.setHead(lv)
                                    }
                                }
                                SRow(on = K.S in fl, enabled = ty != "code", icon = { Ic("s", pal.os) }, label = { T("Coret", 17f, 500, pal.os) }) {
                                    ed.toggleFmt(K.S)
                                }
                                SRow(on = K.C in fl, enabled = ty != "code", icon = { Ic("code", pal.os) }, label = { T("Kode sebaris", 17f, 500, pal.os) }) {
                                    ed.toggleFmt(K.C)
                                }
                                // ---- inden paragraf: satu ketukan = satu inden, ketuk lagi = lepas ----
                                val indLR = !blkOff && blk != null && (ty == "p" || ty == "h" || ty == "li" || ty == "q")
                                val indSp = !blkOff && blk != null && (ty == "p" || ty == "h" || ty == "li")
                                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 22.dp).background(pal.ol.copy(alpha = .35f)))
                                T("Inden", 15f, 800, pal.p, Modifier.padding(start = 22.dp, top = 12.dp, bottom = 2.dp))
                                val il = blk?.il ?: 0
                                val ir = blk?.ir ?: 0
                                val fi = blk?.fi ?: 0
                                SRow(on = il > 0, enabled = indLR, icon = { IndentIcon(0, pal.os) }, label = { T("Inden kiri", 17f, 500, pal.os) }) {
                                    ed.setIndent(0, if (il > 0) 0 else IND_UNIT)
                                }
                                SRow(on = ir > 0, enabled = indLR, icon = { IndentIcon(1, pal.os) }, label = { T("Inden kanan", 17f, 500, pal.os) }) {
                                    ed.setIndent(1, if (ir > 0) 0 else IND_UNIT)
                                }
                                SRow(on = fi > 0, enabled = indSp, icon = { IndentIcon(2, pal.os) }, label = { T("Inden baris pertama", 17f, 500, pal.os) }) {
                                    ed.setIndent(2, if (fi > 0) 0 else IND_UNIT)
                                }
                                SRow(on = fi < 0, enabled = indSp, icon = { IndentIcon(3, pal.os) }, label = { T("Inden menggantung", 17f, 500, pal.os) }) {
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

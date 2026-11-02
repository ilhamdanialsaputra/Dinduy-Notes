package app.dinduy

import androidx.compose.animation.AnimatedContent
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
@Composable
fun DocTopLeft(ed: Ed, act: Actions) {
    val pal = LocalPal.current
    val editing = !ed.reading && !ed.findOn
    Crossfade(editing, animationSpec = tween(200), label = "topleft") { e ->
        if (e) Box(
            Modifier.height(44.dp).width(64.dp).clip(RoundedCornerShape(22.dp)).background(pal.p)
                .clickable { ed.enterReading() },
            Alignment.Center
        ) { Ic("ok", pal.op) }
        else IconBtn("back", pal.os, { if (ed.findOn) ed.closeFind() else act.back() })
    }
}

@Composable
fun DocTopActions(ed: Ed) {
    val pal = LocalPal.current
    val kb = LocalSoftwareKeyboardController.current
    AnimatedVisibility(
        visible = !ed.reading && !ed.findOn,
        enter = fadeIn(tween(220)) + expandHorizontally(tween(280, easing = Emph), expandFrom = Alignment.End),
        exit = fadeOut(tween(120)) + shrinkHorizontally(tween(220, easing = Emph), shrinkTowards = Alignment.End)
    ) {
        Row {
            IconBtn("undo", pal.os, { ed.undo() }, enabled = ed.canUndo && !ed.selOn)
            IconBtn("redo", pal.os, { ed.redo() }, enabled = ed.canRedo && !ed.selOn)
            IconBtn("plus", if (ed.sheet == 1) pal.p else pal.os, {
                kb?.hide(); ed.sub = 0; ed.pickQuote = false
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
        Modifier.fillMaxWidth().height(58.dp).alpha(if (enabled) 1f else .35f)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), Alignment.Center) { icon() }
        Spacer(Modifier.width(20.dp))
        Box(Modifier.weight(1f)) { label() }
        if (on) Ic("ok", pal.p)
    }
}

@Composable
fun DocSheet(ed: Ed, act: Actions) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    var shown by remember { mutableStateOf(1) }
    SideEffect { if (ed.sheet != 0) shown = ed.sheet }
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
        enter = fadeIn(tween(200)) + slideInVertically(tween(340, easing = Emph)) { it },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(240, easing = Emph)) { it }
    ) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(Modifier.fillMaxWidth().shadow(if (pal.dark) 10.dp else 6.dp, shape).clip(shape).background(pal.c3)) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(pal.osv.copy(alpha = .4f)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(if (shown == 1) "Sisipkan" else "Format", 20f, 800, pal.os, Modifier.weight(1f).padding(vertical = 8.dp))
                IconBtn("x", pal.os, { ed.sheet = 0 })
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.ol.copy(alpha = .35f)))
            // tinggi dibatasi dan bisa digulir: daftar boleh bertambah tanpa membuat sheet memanjang
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                if (shown == 1) {
                    SRow(enabled = !blkOff, icon = { Ic("img", pal.os) }, label = { T("Gambar", 17f, 500, pal.os) }) {
                        ed.sheet = 0; act.pickImages()
                    }
                    SRow(enabled = !blkOff, icon = { Ic("table", pal.os) }, label = { T("Tabel", 17f, 500, pal.os) }) {
                        ed.sheet = 0; ed.insTable()
                    }
                    SRow(enabled = !ed.recActive && !selMode, icon = { Ic("mic", pal.os) }, label = { T("Voice note", 17f, 500, pal.os) }) {
                        ed.sheet = 0
                        act.requestMic {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            ed.startVoice()
                        }
                    }
                    SRow(icon = { Ic("pen", pal.os) }, label = { T("Coretan", 17f, 500, pal.os) }) {
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
                } else {
                    SRow(on = ty == "p", enabled = !convOff, icon = { T("T", 20f, 700, pal.os) }, label = { T("Teks biasa", 17f, 500, pal.os) }) {
                        ed.sheet = 0; ed.setPlain()
                    }
                    listOf(1 to 26f, 2 to 23f, 3 to 20f, 4 to 18f).forEach { (lv, sz) ->
                        SRow(on = ty == "h" && blk?.lv == lv, enabled = !convOff, icon = { T("H$lv", 15f, 900, pal.p) },
                            label = { T(if (lv == 4) "Sub-judul" else "Judul $lv", sz, 900, pal.os) }) {
                            ed.sheet = 0; ed.setHead(lv)
                        }
                    }
                    SRow(on = K.S in fl, enabled = ty != "code", icon = { Ic("s", pal.os) }, label = { T("Coret", 17f, 500, pal.os) }) {
                        ed.sheet = 0; ed.toggleFmt(K.S)
                    }
                    SRow(on = K.C in fl, enabled = ty != "code", icon = { Ic("code", pal.os) }, label = { T("Kode sebaris", 17f, 500, pal.os) }) {
                        ed.sheet = 0; ed.toggleFmt(K.C)
                    }
                }
            }
        }
    }
}

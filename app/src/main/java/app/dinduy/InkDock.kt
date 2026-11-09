package app.dinduy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

// =====================================================================================
//  Isi dock mode coret. Wadahnya (pil, bayangan, warna) milik MorphDock di DocBar.kt:
//  jadi berpindah antara toolbar ketik <-> coret adalah SATU wadah yang berubah bentuk, bukan dua toolbar yang bergantian.
//  - Alat (pulpen / stabilo / penghapus / pilih): satu penanda squircle meluncur dan melar mengikuti pilihan.
//  - Mengetuk alat yang sedang aktif membuka panel warna + ketebalan: wadah yang SAMA tumbuh ke atas dengan pegas.
//  - Swatch warna terpilih berubah bentuk dari lingkaran menjadi squircle.
// =====================================================================================

@Composable
fun InkDockContent(ed: Ed, vm: AppVM) {
    val pal = LocalPal.current
    val ik = ed.ink
    val pr = vm.pr
    val density = LocalDensity.current.density
    val tool = ik.tool

    Column(Modifier.width(304.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = ik.pop && tool <= 2,
            enter = expandVertically(M3E.spatial()),
            exit = shrinkVertically(M3E.spatial())
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 2.dp)) {
                if (tool != 2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 0..4) {
                            val col = if (tool == 1 && i == 0) hsl(52f, 100f, 55f).let { c -> if (pal.mono) c.gray() else c }
                            else inkColor(pal, i, false)
                            InkSwatch(i, col, pr.ic.getOrElse(tool) { 0 } == i) {
                                vm.setPr(pr.copy(ic = pr.ic.toMutableList().also { l -> while (l.size < 3) l.add(0); l[tool] = i }))
                                ik.stamp = System.currentTimeMillis()
                            }
                        }
                    }
                }
                val (lo, hi) = when (tool) { 0 -> 1f to 12f; 1 -> 8f to 32f; else -> 8f to 40f }
                val cur = pr.iw.getOrElse(tool) { 3f }.coerceIn(lo, hi)
                Row(Modifier.fillMaxWidth().padding(top = if (tool != 2) 10.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    // pratinjau ketebalan goresan
                    Canvas(Modifier.size(width = 56.dp, height = 44.dp)) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val col = if (tool == 2) pal.osv else inkColor(pal, pr.ic.getOrElse(tool) { 0 }, tool == 1)
                        when (tool) {
                            0 -> drawCircle(col, cur * density / 2f, c)
                            1 -> drawRect(col, Offset(c.x - 22.dp.toPx(), c.y - cur * density / 2f), Size(44.dp.toPx(), cur * density))
                            else -> drawCircle(col, (cur * density).coerceAtMost(21.dp.toPx()), c, style = Stroke(2f * density))
                        }
                    }
                    Box(Modifier.weight(1f).padding(start = 8.dp)) {
                        BigSlider(cur, lo, hi) { v ->
                            vm.setPr(pr.copy(iw = pr.iw.toMutableList().also { l -> while (l.size < 3) l.add(3f); l[tool] = v }))
                            ik.stamp = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(DockH).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SlideSeg(count = 4, sel = tool, i = 0, onPick = { t ->
                ik.hide = false
                if (tool == t) { if (t <= 2) ik.pop = !ik.pop }
                else { ik.tool = t; ik.pop = false; ik.sel = emptySet(); ik.box = null }
                ik.stamp = System.currentTimeMillis()
            }) { t, fg ->
                Box(Modifier.size(48.dp), Alignment.Center) {
                    InkToolIcon(t, fg)
                    // titik warna terakhir yang dipakai alat ini
                    if (t <= 1) Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 14.dp, height = 3.dp)
                            .clip(CircleShape).background(inkColor(pal, pr.ic.getOrElse(t) { 0 }, false))
                    )
                }
            }
            XBtn(tone = 2, i = 4, w = 68.dp, onClick = { ik.toggle(ed) }) { Ic("ok", it) }
        }
    }
}

/** Swatch warna: lingkaran; terpilih = squircle bercincin utama; ditekan = menyempit lebih kotak. */
@Composable
private fun InkSwatch(i: Int, color: Color, sel: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 10.dp else if (sel) 15.dp else 22.dp, M3E.spatialFast(), label = "swr")
    val ring by animateDpAsState(if (sel) 3.dp else 1.dp, M3E.spatialFast(), label = "swb")
    val sc by animateFloatAsState(if (pr) .86f else 1f, spring(.45f, 700f), label = "sws")
    val bc by animateColorAsState(if (sel) pal.p else pal.ol, M3E.effects(), label = "swc")
    val shape = RoundedCornerShape(r.nn())
    Box(
        Modifier.popIn(i, 40L).size(44.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(shape).background(color).border(ring.nn(), bc, shape)
            .clickable(interactionSource = src, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    )
}

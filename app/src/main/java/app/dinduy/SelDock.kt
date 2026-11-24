package app.dinduy

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Capsule bar seleksi (Material 3 Expressive).
 * Tombol "+" (FAB) atau tombol sebesar itu MELAR menjadi kapsul: lebar, tinggi, posisi dan sudut dianimasikan lewat SATU progres pegas (p),
 * semua dihitung di fase layout / gambar, jadi tidak ada rekomposisi per frame. Ukuran isi diukur SEKALI (onSizeChanged), tidak pernah dua kali.
 */
object SelDockFx {
    /** Progres kapsul beranda (0 = tombol FAB, 1 = kapsul penuh). Dibaca FAB agar tidak menerima sentuhan di bawah kapsul. */
    val p = Animatable(0f)
    /** Lebar FAB terakhir (px), dilaporkan oleh Fab. */
    var fabW = 0f
}

private fun sm(x: Float): Float { val k = x.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }

private val ShareD = "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z"

@Composable
private fun ShareIc(tint: Color, sz: Dp = 24.dp) {
    val path = remember { PathParser().parsePathString(ShareD).toPath() }
    androidx.compose.foundation.Canvas(Modifier.size(sz)) {
        val s = size.width / 24f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawPath(path, tint) }
    }
}

/**
 * Cangkang kapsul. [fromW] x [fromH] = ukuran tombol asal (FAB). [endPad] = jarak dari tepi kanan saat menjadi tombol (null = di tengah).
 * Saat tertutup dan animasi selesai, tidak ada yang dikomposisi sama sekali.
 */
@Composable
fun SelDockShell(
    on: Boolean, p: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    fromW: Dp, fromH: Dp, endPad: Dp?, lift: Dp,
    plus: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    LaunchedEffect(on) { p.animateTo(if (on) 1f else 0f, M3E.spatial()) }
    val alive = on || p.value > .002f || p.isRunning
    if (!alive) return
    val pal = LocalPal.current
    val sab = sabDp()
    val dn = LocalDensity.current
    var nat by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().padding(bottom = 24.dp + sab), Alignment.BottomStart) {
        Box(
            Modifier.fillMaxWidth().offset(y = -lift)
                .layout { m, c ->
                    val maxW = c.maxWidth.toFloat()
                    val pp = p.value
                    val fw = fromW.toPx(); val fh = fromH.toPx()
                    val endX = if (endPad == null) (maxW - fw) / 2f else maxW - endPad.toPx() - fw
                    val tw = min(if (nat > 0) nat.toFloat() else maxW, min(maxW - 24.dp.toPx(), 420.dp.toPx()))
                    val w = max(fw * .6f, fw + (tw - fw) * pp).roundToInt()
                    val h = max(fh * .6f, fh + (72.dp.toPx() - fh) * pp).roundToInt()
                    val x0 = endX
                    val x1 = (maxW - w) / 2f
                    val x = (x0 + (x1 - x0) * pp.coerceIn(0f, 1f)).roundToInt()
                    val pl = m.measure(Constraints.fixed(w, h))
                    layout(c.maxWidth, h) { pl.place(x, 0) }
                }
        ) {
            // Tanpa bayangan: kapsul dibingkai garis tebal berwarna utama supaya jelas terpisah dari kartu / latar (mode biasa maupun Expressive).
            // Garis dan warna tumbuh bersama progres morph. Kapsul arsip / sampah (plus = false) tidak berasal dari FAB, jadi tumbuh dari
            // titik kecil sambil memudar masuk dan TIDAK menampilkan tombol "+" sama sekali.
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val pp = p.value.coerceIn(0f, 1f)
                        val rr = min(size.height / 2f, (32f + 4f * pp).dp.toPx()).coerceAtLeast(0f)
                        shape = RoundedCornerShape(androidx.compose.foundation.shape.CornerSize(rr))
                        clip = true
                        if (!plus) {
                            val g = sm(pp / .45f)
                            alpha = g; val sc = .55f + .45f * g; scaleX = sc; scaleY = sc
                        }
                    }
                    .drawWithContent {
                        val pp = p.value.coerceIn(0f, 1f)
                        drawRect(if (plus) lerp(pal.pc, pal.dock(), sm(pp / .6f)) else pal.dock())
                        drawContent()
                        val sw = (if (plus) 3.dp.toPx() * sm((pp - .1f) / .5f) else 3.dp.toPx())
                        if (sw > .1f) {
                            val rr = min(size.height / 2f, (32f + 4f * pp).dp.toPx()).coerceAtLeast(0f)
                            drawRoundRect(
                                pal.p, androidx.compose.ui.geometry.Offset(sw / 2f, sw / 2f),
                                androidx.compose.ui.geometry.Size((size.width - sw).coerceAtLeast(0f), (size.height - sw).coerceAtLeast(0f)),
                                androidx.compose.ui.geometry.CornerRadius((rr - sw / 2f).coerceAtLeast(0f)),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
                            )
                        }
                    }
            ) {
                // "+" memudar dan berputar keluar saat kapsul melebar (hanya untuk kapsul beranda yang berasal dari FAB)
                if (plus) Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val a = 1f - sm(p.value / .35f)
                        alpha = a; rotationZ = 90f * (1f - a)
                    },
                    Alignment.Center
                ) { Ic("plus", pal.opc, 36.dp) }
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { val a = sm((p.value - .22f) / .45f); if (a < .999f) { alpha = a; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha } }
                        .horizontalScroll(rememberScrollState())
                ) {
                    Row(
                        Modifier.fillMaxHeight().onSizeChanged { if (it.width != nat) nat = it.width }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompositionLocalProvider(LocalDockMotion provides true) { content() }
                    }
                }
            }
        }
    }
}

private val inT = expandHorizontally(M3E.spatial()) + scaleIn(M3E.spatialFast(), .3f)
private val outT = shrinkHorizontally(spring<IntSize>(1f, 620f)) + scaleOut(tween(120), .3f)

/** Chip jumlah terpilih: angka menggelinding naik / turun sesuai arah perubahan. */
@Composable
fun CountChip(n: Int) {
    val pal = LocalPal.current
    Row(
        Modifier.height(48.dp).clip(CircleShape).background(pal.p).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // angka lama / baru digeser DI DALAM kotak berpotong (clipToBounds) lalu memudar, jadi tidak ada angka yang tertinggal di atas / bawah lalu hilang mendadak
        Box(Modifier.height(30.dp).clipToBounds(), Alignment.Center) {
            AnimatedContent(
                targetState = n,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(M3E.spatialFast()) { if (up) it else -it } + fadeIn(tween(110)) togetherWith
                        slideOutVertically(M3E.spatialFast()) { if (up) -it else it } + fadeOut(tween(90)))
                        .using(SizeTransform(clip = true))
                },
                label = "cnt"
            ) { v -> T(v.toString(), 17f, 900, pal.op) }
        }
        T(" selected", 15f, 700, pal.op, maxLines = 1)
    }
}

/** Kapsul beranda: morph dari FAB. */
@Composable
fun HomeSelDock(vm: AppVM, act: Actions, lift: Dp = 0.dp) {
    val pal = LocalPal.current
    val dens = LocalDensity.current
    val on = vm.sel.isNotEmpty()
    // saat menutup, isi tetap memakai pilihan terakhir supaya tidak berubah bentuk selagi menyusut
    val last = remember { arrayOf<List<String>>(emptyList()) }
    if (on) last[0] = vm.sel.toList()
    val ids = if (on) vm.sel.toList() else last[0]
    val single = ids.size == 1
    val note = if (single) vm.note(ids[0]) else null
    val allPinned = ids.isNotEmpty() && ids.all { vm.note(it)?.p == true }
    val fromW = if (SelDockFx.fabW > 0f) with(dens) { SelDockFx.fabW.toDp() } else 88.dp
    SelDockShell(on, SelDockFx.p, fromW, 88.dp, 20.dp, lift) {
        XBtn(onClick = { vm.sel.clear() }) { c -> Ic("x", c) }
        CountChip(ids.size)
        XGroup {
            XBtn(onClick = {
                val l = vm.sel.toList(); vm.sel.clear(); vm.duplicateAll(l)
            }) { c -> Ic("lib", c) }
            AnimatedVisibility(single && note != null && note.pdf.isEmpty(), enter = inT, exit = outT) {
                XBtn(onClick = {
                    note?.let { act.copyText(noteToMd(it)); vm.show("Copied to clipboard") }
                }) { c -> Ic("dup", c) }
            }
            AnimatedVisibility(single, enter = inT, exit = outT) {
                XBtn(onClick = { ids.firstOrNull()?.let { vm.onShareNote?.invoke(it) } }) { c -> ShareIc(c) }
            }
        }
        XGroup {
            XBtn(onClick = { vm.liveF().forEach { if (it.id !in vm.sel) vm.sel.add(it.id) } }) { c -> Ic("all", c) }
            XBtn(on = allPinned, onClick = {
                val l = vm.sel.toList()
                if (l.isNotEmpty()) {
                    val all = l.all { vm.note(it)?.p == true }
                    vm.sel.clear()
                    vm.mark(l, if (all) "Note unpinned" else "${cnt(l.size, "note")} pinned") { it.copy(p = !all) }
                }
            }) { c -> Ic("pin", c) }
        }
        XBtn(onClick = {
            val l = vm.sel.toList()
            if (l.isNotEmpty()) { vm.sel.clear(); vm.mark(l, "${cnt(l.size, "note")} archived") { it.copy(a = true, p = false) } }
        }) { c -> Ic("arc", c) }
        XBtn(tone = 1, onClick = {
            val l = vm.sel.toList()
            if (l.isNotEmpty()) { vm.sel.clear(); vm.mark(l, "${cnt(l.size, "note")} moved to Trash") { it.copy(dt = System.currentTimeMillis()) } }
        }) { c -> Ic("del", c) }
    }
}

private val listP = object { val p = Animatable(0f) }

/** Kapsul arsip / sampah: tombol bulat 64dp di tengah bawah melar menjadi kapsul. */
@Composable
fun ListSelDock(vm: AppVM, sel: androidx.compose.runtime.snapshots.SnapshotStateList<String>, all: List<Note>, trash: Boolean) {
    val on = sel.isNotEmpty()
    val last = remember { arrayOf<List<String>>(emptyList()) }
    if (on) last[0] = sel.toList()
    val ids = if (on) sel.toList() else last[0]
    SelDockShell(on, listP.p, 64.dp, 64.dp, null, 0.dp, plus = false) {
        XBtn(onClick = { sel.clear() }) { c -> Ic("x", c) }
        CountChip(ids.size)
        XGroup {
            if (trash) XBtn(onClick = {
                val l = sel.toList(); sel.clear(); vm.mark(l, "${cnt(l.size, "note")} restored") { it.copy(dt = 0L) }
            }) { c -> Ic("restore", c) }
            else XBtn(onClick = {
                val l = sel.toList(); sel.clear(); vm.mark(l, "${cnt(l.size, "note")} unarchived") { it.copy(a = false) }
            }) { c -> Ic("unarc", c) }
            XBtn(onClick = { all.forEach { if (it.id !in sel) sel.add(it.id) } }) { c -> Ic("all", c) }
        }
        XBtn(tone = 1, onClick = {
            val l = sel.toList(); sel.clear()
            if (trash) l.forEach { vm.purge(it) }
            else vm.mark(l, "${cnt(l.size, "note")} moved to Trash") { it.copy(dt = System.currentTimeMillis()) }
        }) { c -> Ic("del", c) }
    }
}

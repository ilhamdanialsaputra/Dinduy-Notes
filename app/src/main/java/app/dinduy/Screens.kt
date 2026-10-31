@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.dinduy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as gridItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween as tweenSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

interface Actions {
    fun pickImages()
    fun importMd()
    fun saveBackup()
    fun shareBackup()
    fun restoreBackup()
    fun saveMd(name: String, text: String)
    fun shareMd(name: String, text: String)
    fun copyText(t: String)
    fun askNotif()   // minta / arahkan ke izin notifikasi supaya pengingat bisa muncul
    fun back()
}

@Composable
fun satDp(): Dp = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()

@Composable
fun sabDp(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

private val SH = listOf(
    floatArrayOf(36f, 36f, 36f, 36f), floatArrayOf(52f, 20f, 52f, 20f), floatArrayOf(20f, 52f, 20f, 52f),
    floatArrayOf(60f, 28f, 28f, 28f), floatArrayOf(28f, 28f, 60f, 28f)
)

private fun fdate(u: Long): String {
    val d = Date(u)
    val same = SimpleDateFormat("yyyy", Locale.US).format(d) == SimpleDateFormat("yyyy", Locale.US).format(Date())
    return SimpleDateFormat(if (same) "d MMM" else "d MMM yyyy", Locale("id", "ID")).format(d)
}

/** Spring "spatial" Material 3 Expressive untuk perpindahan kartu antar posisi di daftar. */
private val PlaceSpec = spring<IntOffset>(0.8f, 380f, IntOffset.VisibilityThreshold)

/** Kartu masuk/keluar/berpindah dengan mulus (dipakai di beranda, hasil pencarian, arsip, sampah). */
fun LazyItemScope.flowItem(): Modifier = Modifier.animateItem(
    fadeInSpec = tweenSpec(280, 60), placementSpec = PlaceSpec, fadeOutSpec = tweenSpec(160)
)

/** Sama seperti flowItem, untuk grid beranda (1 atau 2 kolom). */
fun LazyStaggeredGridItemScope.flowItemG(): Modifier = Modifier.animateItem(
    fadeInSpec = tweenSpec(280, 60), placementSpec = PlaceSpec, fadeOutSpec = tweenSpec(160)
)

/**
 * Saat sebuah catatan membuka (morph kartu -> layar penuh), kartu lain tidak ditimpa begitu saja:
 * kartu di atas terdorong ke atas, kartu di bawah terdorong ke bawah mengikuti tepi kartu yang melebar,
 * sedikit mengecil dan memudar. Saat menutup, geraknya berbalik dengan spring yang sama.
 */
fun Modifier.makeRoom(vm: AppVM, id: String, idx: Int, openIdx: () -> Int, h: Float, dens: Float, cols: Int = 1, w: Float = 0f): Modifier = graphicsLayer {
    val oid = vm.openId
    val m = vm.morph
    val p = vm.openP.value.coerceIn(0f, 1f)
    if (p <= 0.001f || oid == null || m == null || oid == id) return@graphicsLayer
    val oi = openIdx()
    if (oi < 0) return@graphicsLayer
    // beranda (1 atau 2 kolom; w > 0 hanya diisi HomeCard): posisi kartu dibaca langsung, bukan sekadar urutan
    val grid = w > 0f && !vm.searchOn
    // morph dari kartu "Tambah catatan" (lebar penuh) atau menguncup ke tombol Baru tidak boleh menggeser kartu lain
    if (grid && (!m.card || vm.note(oid) == null)) return@graphicsLayer
    val wave = minOf(Math.abs(idx - oi), 5) * 10f * dens
    val me = if (grid) vm.cardMorph[id] else null
    if (me != null) {
        // kartu di atas terdorong ke atas, kartu di bawah ke bawah; di 2 kolom kartu di samping juga terdorong
        // ke kiri/kanan menjauhi kartu yang melebar (bukan satu aliran vertikal)
        val eps = 4f * dens
        val above = me.b <= m.t + eps
        val below = me.t >= m.b - eps
        if (!above && !below && cols == 2) {
            translationX = if (me.r <= m.l + eps || (me.l + me.r) < (m.l + m.r)) -(me.r + 8f * dens) * p else (w - me.l + 8f * dens) * p
        } else {
            val up = if (!above && !below) idx < oi else above
            translationY = if (up) -(maxOf(m.t, 0f) + wave) * p else (maxOf(h - m.b, 0f) + wave) * p
        }
    } else {
        translationY = if (idx < oi) -(maxOf(m.t, 0f) + wave) * p else (maxOf(h - m.b, 0f) + wave) * p
    }
    val s = 1f - .05f * p
    scaleX = s; scaleY = s
    alpha = 1f - .6f * p
}

@Composable
fun NoteCard(
    d: Note, i: Int, kind: String, selected: Boolean, modifier: Modifier = Modifier,
    delTime: Long = 0L, onLong: (() -> Unit)? = null, track: ((Morph) -> Unit)? = null, tick: Int = 0, cols: Int = 1,
    paper: Boolean = false, onClick: (Morph) -> Unit
) {
    val pal = LocalPal.current
    val c2 = cols == 2
    fun scheme(k: Int) = when (k % 4) {
        0 -> pal.pc to pal.opc
        1 -> pal.sc to pal.osc
        2 -> pal.tc to pal.otc
        else -> pal.c4 to pal.os
    }
    // warna & bentuk sudut tidak "pop" saat urutan berubah (pencarian, arsip, hapus): dicampur mulus
    var fromI by remember { mutableIntStateOf(i) }
    var toI by remember { mutableIntStateOf(i) }
    val mixA = remember { Animatable(1f) }
    LaunchedEffect(i) {
        if (i != toI) {
            fromI = toI; toI = i
            mixA.snapTo(0f)
            mixA.animateTo(1f, spring(.9f, 320f))
        }
    }
    val mx = mixA.value
    val csA = scheme(fromI)
    val csB = scheme(toI)
    // paper = true (dipakai di panel pencarian): kartu polos berwarna sama dengan kertas editor, bukan warna-warni,
    // supaya kontras dengan wadah pencarian yang sedang aktif
    val cs = if (paper) pal.s to pal.os
    else androidx.compose.ui.graphics.lerp(csA.first, csB.first, mx) to androidx.compose.ui.graphics.lerp(csA.second, csB.second, mx)
    val shA = SH[fromI % 5]
    val shB = SH[toI % 5]
    val sh = FloatArray(4) { shA[it] + (shB[it] - shA[it]) * mx }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val selA by animateFloatAsState(if (selected) 1f else 0f, spring(.6f, 400f), label = "sel")
    val pa by animateFloatAsState(if (pressed && !selected) 1f else 0f, spring(.7f, 300f), label = "pa")
    val sc by animateFloatAsState(if (selected || pressed) .97f else 1f, spring(.55f, 500f), label = "sc")
    fun cr(k: Int): Dp {
        // kartu sempit (2 kolom): sudut terpilih dibatasi supaya ceklis di pojok kanan atas tidak terpotong
        val a = sh[k] + ((if (c2) 36f else 58f) - sh[k]).coerceAtMost(if (c2) 0f else 999f) * pa
        return (a + ((if (c2) 44f else 84f) - a).coerceAtMost(if (c2) 0f else 999f) * selA).dp
    }
    val shape = RoundedCornerShape(cr(0), cr(1), cr(2), cr(3))
    // 2 kolom: sudut besar memotong teks di pojok, jadi teks digeser menjauhi lengkungan (kiri-atas untuk judul, kiri-bawah untuk lencana/footer)
    val insTL = if (c2) ((sh[0] - 40f).coerceAtLeast(0f) * .45f).dp else 0.dp
    // sudut kiri-bawah memakai bentuk statis (bukan yang beranimasi saat ditekan) supaya tata letak tidak bergetar; ambangnya
    // lebih rendah daripada kiri-atas karena baris teks terakhir (descender, footer) menempel di dasar kartu
    val insBL = if (c2) ((sh[3] - 28f).coerceAtLeast(0f) * .5f).dp else 0.dp
    val padV by animateDpAsState(if (selected) (if (c2) 16.dp else 26.dp) else if (c2) 16.dp else if (kind == "t") 22.dp else if (kind == "s") 20.dp else 24.dp, spring(.7f, 400f), label = "pv")
    val padH by animateDpAsState(if (selected) (if (c2) 16.dp else 32.dp) else if (c2) 16.dp else 24.dp, spring(.7f, 400f), label = "ph")
    var rect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val dens = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun morphNow(live: Boolean): Morph {
        val c = coords[0]
        val rc = if (c != null && c.isAttached) c.boundsInWindow() else rect
        return Morph(
            rc.left, rc.top, rc.right, rc.bottom, sh.average().toFloat() * dens, cs.first,
            FloatArray(4) { (if (live) cr(it).value else sh[it]) * dens },
            Ghost(kind, c2, cs.second, padH, padV, insTL, insBL), card = true
        )
    }
    // posisi dilaporkan ulang setelah layar selesai bergeser masuk (arsip/sampah), supaya morph keluar akurat
    LaunchedEffect(tick) { if (tick > 0) track?.invoke(morphNow(false)) }
    Box(
        modifier.onGloballyPositioned {
            coords[0] = it
            rect = it.boundsInWindow()
            track?.invoke(morphNow(false))
        }
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .fillMaxWidth().clip(shape).background(cs.first)
            .combinedClickable(
                interactionSource = src, indication = LocalIndication.current,
                onClick = { onClick(morphNow(true)) },
                onLongClick = if (onLong != null) ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong() }) else null
            )
    ) {
        CardBody(d, kind, c2, cs.second, padH, padV, insTL, insBL, delTime)
        if (d.p) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = if (c2) 14.dp else 22.dp, end = if (c2) 12.dp else 24.dp).graphicsLayer { rotationZ = 32f; alpha = .7f * (1f - selA) }) {
                Ic("pin", cs.second, if (c2) 18.dp else 20.dp)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = if (c2) 12.dp else 24.dp, end = if (c2) 12.dp else 28.dp).size(if (c2) 28.dp else 32.dp)
                .graphicsLayer { scaleX = selA; scaleY = selA }.clip(CircleShape).background(pal.p),
            Alignment.Center
        ) { Ic("ok", pal.op, 20.dp) }
    }
}

/** Isi teks kartu (judul, cuplikan, label, footer). Dipakai kartu asli dan bayangan teks saat morph buka/tutup. */
@Composable
fun CardBody(d: Note, kind: String, c2: Boolean, fg: Color, padH: Dp, padV: Dp, insTL: Dp, insBL: Dp, delTime: Long) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = padH, vertical = padV).let { if (kind == "f") it.defaultMinSize(minHeight = if (c2) 96.dp else 102.dp) else it },
        verticalArrangement = Arrangement.spacedBy(if (kind == "t") 0.dp else if (kind == "s") 6.dp else 10.dp)
    ) {
        // elemen terakhir yang menempel di pojok kiri-bawah ikut digeser menjauhi lengkungan (trik yang sama dengan kiri-atas)
        val remOn = d.rm > 0L && d.dt == 0L
        val titleLast = kind == "t" && d.lb.isEmpty() && !remOn
        val bodyLast = kind == "s" && d.lb.isEmpty() && !remOn
        T(
            d.t.ifEmpty { "Tanpa judul" }, if (c2) 16f else if (kind == "s") 19f else 21f, 900, fg,
            Modifier.padding(
                start = if (titleLast) maxOf(insTL, insBL) else insTL, top = insTL, end = if (c2) 30.dp else 44.dp,
                bottom = if (titleLast) insBL else 0.dp
            ), ls = -.015f, lh = 1.1f,
            maxLines = if (c2) 3 else Int.MAX_VALUE
        )
        if (kind != "t") {
            T(
                d.x.replace(Regex("\\s+"), " ").trim().take(150).ifEmpty { "Kosong" }, if (c2) 13.5f else 15f, 450, fg,
                modifier = Modifier.padding(start = if (bodyLast) insBL else 0.dp, bottom = if (bodyLast) insBL else 0.dp),
                maxLines = if (kind == "s") (if (c2) 2 else 1) else (if (c2) 4 else 3), lh = 1.45f, alpha = .8f
            )
        }
        if (remOn) {
            // chip pengingat (seperti Google Keep): jam bunyi berikutnya, dicoret bila sudah terlewat
            Box(
                Modifier.padding(top = if (kind == "t") 8.dp else 0.dp)
                    .padding(start = if (d.lb.isEmpty()) insBL else 0.dp, bottom = if (d.lb.isEmpty() && kind != "f") insBL else 0.dp)
            ) { RemChip(Rem.stamp(d), fg, Rem.overdue(d), d.rr.isNotEmpty()) }
        }
        if (d.lb.isNotEmpty()) {
            val maxShow = if (c2) 1 else 2
            Row(
                Modifier.fillMaxWidth().padding(top = if (kind == "t") 8.dp else 0.dp).padding(start = insBL, bottom = if (kind == "f") 0.dp else insBL),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                d.lb.take(maxShow).forEach { n -> Capsule(n, fg, Modifier.weight(1f, fill = false)) }
                if (d.lb.size > maxShow) Capsule("+" + (d.lb.size - maxShow), fg)
            }
        }
        if (kind == "f") {
            Row(Modifier.fillMaxWidth().padding(start = insBL, bottom = insBL), horizontalArrangement = if (c2) Arrangement.Start else Arrangement.SpaceBetween) {
                if (!c2) T(wordCount(d.x).toString() + " kata", 13f, 700, fg, ls = .02f, alpha = .75f)
                T((if (delTime != 0L) "Dihapus " else "") + fdate(if (delTime != 0L) delTime else d.u), if (c2) 12f else 13f, 700, fg, ls = .02f, alpha = .75f)
            }
        }
    }
}

/** Bayangan kartu: teks kartu yang pudar-silang dengan isi catatan selama morph, supaya teks tidak kedip. */
@Composable
fun GhostCard(d: Note, g: Ghost, modifier: Modifier = Modifier) {
    Box(modifier) {
        CardBody(d, g.kind, g.c2, g.fg, g.padH, g.padV, g.insTL, g.insBL, 0L)
        if (d.p) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = if (g.c2) 14.dp else 22.dp, end = if (g.c2) 12.dp else 24.dp).graphicsLayer { rotationZ = 32f; alpha = .7f }) {
                Ic("pin", g.fg, if (g.c2) 18.dp else 20.dp)
            }
        }
    }
}

/** Lencana label berbentuk kapsul di kartu catatan. */
@Composable
fun Capsule(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        T(text, 12f, 700, color, maxLines = 1)
    }
}

@Composable
fun SwipeCard(enabled: Boolean, shape: androidx.compose.ui.graphics.Shape, onArchive: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    val off = remember { Animatable(0f) }
    val col = remember { Animatable(1f) }
    var w by remember { mutableStateOf(1f) }
    val haptic = LocalHapticFeedback.current
    var hit by remember { mutableStateOf(false) }
    var dir by remember { mutableStateOf(1) }
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val p = (Math.abs(off.value) / (w * .34f)).coerceIn(0f, 1f)
    val errC = if (pal.dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    val pop = remember { Animatable(0f) }
    val wig = remember { Animatable(1f) }
    // pegas kurang teredam: melompat melewati nilai akhir lalu berosilasi mengendap = efek "pop" dan goyangan
    LaunchedEffect(hit) { pop.animateTo(if (hit) 1f else 0f, spring(.4f, 450f)) }
    LaunchedEffect(hit) { if (hit) { wig.snapTo(0f); wig.animateTo(1f, spring(.22f, 240f)) } else wig.snapTo(1f) }
    Box(
        Modifier.fillMaxWidth().onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .layout { m, c ->
                val pl = m.measure(c)
                val h = (pl.height * col.value).roundToInt()
                layout(pl.width, h) { pl.placeRelative(0, 0) }
            }
            .draggable(
                orientation = Orientation.Horizontal, enabled = enabled,
                state = rememberDraggableState { dx ->
                    scope.launch {
                        off.snapTo(off.value + dx)
                        if (off.value != 0f) dir = if (off.value > 0f) 1 else -1
                        val nh = Math.abs(off.value) >= w * .34f
                        if (nh && !hit) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        hit = nh
                    }
                },
                onDragStopped = {
                    scope.launch {
                        val ok = Math.abs(off.value) >= w * .34f
                        if (!ok) off.animateTo(0f, spring(1f, 500f))
                        else {
                            val right = off.value > 0
                            off.animateTo(if (right) w * 1.1f else -w * 1.1f, tween(280, easing = Emph))
                            col.animateTo(0f, tween(300, easing = Emph))
                            if (right) onArchive() else onDelete()
                        }
                    }
                }
            )
    ) {
        // tanpa latar: hanya ikon yang menyusul tepi kartu, lalu "menyala" saat melewati ambang
        val reveal = (Math.abs(off.value) / (14f * dens)).coerceIn(0f, 1f)
        if (reveal > 0f) {
            val r = dir > 0
            val tint = if (r) pal.p else errC
            val e = p * (2f - p)   // ease-out
            Box(Modifier.matchParentSize().graphicsLayer { alpha = reveal * col.value.coerceIn(0f, 1f) }) {
                Column(
                    Modifier.align(if (r) Alignment.CenterStart else Alignment.CenterEnd)
                        .offset {
                            // ikon berada di tengah celah yang terbuka, lalu berhenti 56dp dari tepi layar
                            val c = minOf(Math.abs(off.value) / 2f, 56.dp.toPx())
                            val x = (c - 28.dp.toPx()).roundToInt()
                            IntOffset(if (r) x else -x, 0)
                        }
                        .width(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(56.dp).graphicsLayer {
                            val s = (.4f + .6f * e) * (1f + .32f * pop.value)
                            scaleX = s; scaleY = s
                            // melewati ambang: tong sampah bergoyang meredam, arsip melompat naik lalu mendarat
                            val w0 = 1f - wig.value
                            rotationZ = if (r) 0f else -24f * w0
                            translationY = if (r) -16.dp.toPx() * w0 else 0f
                        },
                        Alignment.Center
                    ) { Ic(if (r) "arc" else "del", tint.copy(alpha = .5f + .5f * e), 30.dp) }
                    T(
                        if (r) "Arsip" else "Hapus", 12f, 800, tint,
                        Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f); translationY = (1f - pop.value) * 6.dp.toPx() }
                    )
                }
            }
        }
        Box(Modifier.offset { IntOffset(off.value.roundToInt(), 0) }) { content() }
    }
}

@Composable
fun Opt(i: Int, icon: String, title: String, sub: String? = null, check: Boolean = false, trailing: String? = null, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else 30.dp, spring(.6f, 500f), label = "r")
    val sc by animateFloatAsState(if (pr) .97f else 1f, spring(.5f, 600f), label = "s")
    val (bg, fg, ir) = when (i % 3) {
        1 -> Triple(pal.pc, pal.opc, 30.dp)
        2 -> Triple(pal.sc, pal.osc, 18.dp)
        else -> Triple(pal.tc, pal.otc, 30.dp)
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(r))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(ir)).background(bg), Alignment.Center) { Ic(icon, fg, 24.dp) }
        Column(Modifier.weight(1f)) {
            T(title, 19f, 800, pal.os)
            if (sub != null) T(sub, 14f, 500, pal.osv)
        }
        if (check) Ic("ok", pal.p, 26.dp)
        if (trailing != null) T(trailing, 19f, 900, pal.p)
    }
}

@Composable
fun FontRow(name: String, fam: FontFamily, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else if (on) 32.dp else 24.dp, spring(.6f, 500f), label = "r")
    val sc by animateFloatAsState(if (pr) .97f else 1f, spring(.5f, 600f), label = "s")
    val bg = androidx.compose.ui.graphics.lerp(pal.c4, pal.pc, animateFloatAsState(if (on) 1f else 0f, spring(.8f, 380f), label = "b").value)
    val fg = if (on) pal.opc else pal.os
    CompositionLocalProvider(LocalFF provides fam) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).graphicsLayer { scaleX = sc; scaleY = sc }
                .clip(RoundedCornerShape(r)).background(bg)
                .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                T(name, 21f, 700, fg)
                T("Catatan rapi, cepat, nyaman 0123", 14f, 400, fg, alpha = .75f)
            }
            if (on) Ic("ok", fg, 24.dp)
        }
    }
}

/** Pilihan "Ikuti perangkat" (Material You): warna aplikasi mengikuti wallpaper. */
@Composable
fun DeviceColorRow(on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else if (on) 32.dp else 24.dp, spring(.6f, 500f), label = "r")
    val sc by animateFloatAsState(if (pr) .97f else 1f, spring(.5f, 600f), label = "s")
    val k by animateFloatAsState(if (on) 1f else 0f, spring(.8f, 380f), label = "b")
    val bg = androidx.compose.ui.graphics.lerp(pal.c4, pal.pc, k)
    val fg = if (on) pal.opc else pal.os
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(if (on) 24.dp else 16.dp)).background(pal.p), Alignment.Center) {
            Ic("pal", pal.op, 24.dp)
        }
        Column(Modifier.weight(1f)) {
            T("Ikuti perangkat", 19f, 800, fg)
            T("Material You, warna mengikuti wallpaper", 14f, 400, fg, alpha = .75f)
        }
        if (on) Ic("ok", fg, 24.dp)
    }
}

// ---------- drawer ala Google Keep: panel persegi, baris berbentuk pil ----------
@Composable
fun DrawerItem(icon: String, title: String, selected: Boolean = false, trailing: String? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 18.dp else 32.dp, spring(.6f, 500f), label = "r")
    val bgA by animateFloatAsState(if (selected) 1f else 0f, spring(.8f, 380f), label = "bg")
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(r))
            .background(pal.pc.copy(alpha = bgA))
            .combinedClickable(
                interactionSource = src, indication = LocalIndication.current, onClick = onClick,
                onLongClick = if (onLong != null) ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong() }) else null
            )
            .padding(start = 26.dp, end = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic(icon, if (selected) pal.opc else pal.os, 24.dp)
        Spacer(Modifier.width(26.dp))
        T(title, 18f, if (selected) 700 else 500, if (selected) pal.opc else pal.os, Modifier.weight(1f), maxLines = 1)
        if (trailing != null) T(trailing, 15f, 700, if (selected) pal.opc else pal.osv)
    }
}

@Composable
fun DrawerContent(vm: AppVM, close: () -> Unit, go: (Scr) -> Unit) {
    val pal = LocalPal.current
    @Composable
    fun Divider() = Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).height(1.dp).background(pal.ol))
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            T("Dinduy", 28f, 800, pal.p, Modifier.padding(start = 16.dp, top = 28.dp, bottom = 24.dp), ls = -.01f)
            // sorotan (highlight) menandai posisi pengguna: "Catatan" = semua, atau satu label
            DrawerItem("bulb", "Catatan", selected = vm.filter == null && !vm.remFilter) { vm.filter = null; vm.remFilter = false; close() }
            DrawerItem(
                "bell", "Pengingat", selected = vm.remFilter,
                trailing = vm.live().count { it.rm > 0L }.takeIf { it > 0 }?.toString()
            ) { vm.filter = null; vm.remFilter = true; close() }
            T("Label", 14f, 700, pal.osv, Modifier.padding(start = 26.dp, top = 16.dp, bottom = 6.dp))
            vm.sortedLabels().forEach { n ->
                DrawerItem(
                    "label", n, selected = vm.filter == n,
                    trailing = vm.labelCount(n).takeIf { it > 0 }?.toString(),
                    onLong = { vm.sheet = Sheet.LblEdit(n) }
                ) { vm.remFilter = false; vm.filter = n; close() }
            }
            DrawerItem("plus", "Buat label baru") { close(); vm.lblDlg = LblDlg(null, null) }
            Divider()
            DrawerItem("arc", "Arsip", trailing = vm.archived().size.takeIf { it > 0 }?.toString()) { go(Scr.Archive) }
            DrawerItem("del", "Baru saja dihapus", trailing = vm.trashed().size.takeIf { it > 0 }?.toString()) { go(Scr.Trash) }
            Spacer(Modifier.height(12.dp))
        }
        Divider()
        DrawerItem("set", "Pengaturan") { go(Scr.Settings) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun LabelDialogHost(vm: AppVM) {
    val d = vm.lblDlg ?: return
    val pal = LocalPal.current
    var text by remember(d) { mutableStateOf(d.old ?: "") }
    val fr = remember(d) { FocusRequester() }
    val done = {
        val old = d.old
        val ok = if (old != null) vm.renameLabel(old, text) else vm.addLabel(text, d.forNote)
        if (ok) vm.lblDlg = null
    }
    LaunchedEffect(d) { delay(120); try { fr.requestFocus() } catch (e: Exception) { } }
    AlertDialog(
        onDismissRequest = { vm.lblDlg = null },
        containerColor = pal.c3,
        title = { T(if (d.old != null) "Ganti nama label" else "Label baru", 22f, 900, pal.os) },
        text = {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.c4).padding(horizontal = 20.dp, vertical = 16.dp)) {
                BasicTextField(
                    value = text, onValueChange = { text = it.take(30) },
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 18.sp, fontWeight = FontWeight(600)),
                    singleLine = true, cursorBrush = SolidColor(pal.p),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { done() }),
                    decorationBox = { inner -> Box { if (text.isEmpty()) T("Nama label", 18f, 600, pal.osv); inner() } }
                )
            }
        },
        confirmButton = { TextButton(onClick = { done() }) { T(if (d.old != null) "Simpan" else "Buat", 16f, 900, pal.p) } },
        dismissButton = { TextButton(onClick = { vm.lblDlg = null }) { T("Batal", 16f, 700, pal.osv) } }
    )
}

// ---------- beranda ----------
@Composable
fun HomeScreen(vm: AppVM, nav: Nav, act: Actions, openDrawer: () -> Unit, onSmall: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val sat = satDp()
    val sab = sabDp()
    val dens = LocalDensity.current
    val focusMgr = LocalFocusManager.current
    val state = rememberLazyStaggeredGridState()
    // tween tanpa overshoot supaya buka/tutup pencarian tidak bergetar
    val saRaw by animateFloatAsState(if (vm.searchOn) 1f else 0f, tween(420, easing = Emph), label = "sa")
    val sa = saRaw.coerceIn(0f, 1f)
    val fabConn = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                if (dy * acc < 0f) acc = 0f
                acc += dy
                val th = 64f * dens.density
                if (acc < -th) onSmall(true) else if (acc > th) onSmall(false)
                return Offset.Zero
            }
        }
    }
    val fr = remember { FocusRequester() }
    val sel = vm.sel
    val selMode = sel.isNotEmpty()
    val cols = vm.pr.cols.coerceIn(1, 2)
    val live = vm.sorted(vm.liveF()).let { l ->
        // 1 & 2 kolom: catatan kosong yang sedang dibuka disembunyikan dari grid supaya kartu lain tidak bergeser
        l.filterNot { d ->
            d.t.isBlank() && d.x.isBlank() && d.bl.none { it.isObj && it.ty != "hr" } && d.rm == 0L &&
                vm.openId == d.id && vm.nav.stack.any { s -> s is Scr.Note && s.id == d.id }
        }
    }
    val pos = remember(live) { live.withIndex().associate { it.value.id to it.index } }

    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.canScrollBackward }.collect { back -> if (!back) onSmall(false) }
    }
    LaunchedEffect(vm.filter, vm.remFilter) { try { state.scrollToItem(0) } catch (e: Exception) { } }
    // setelah catatan ditutup, kartunya (yang naik ke atas karena baru diedit) harus terlihat: gulir mulus ke atas,
    // bersamaan dengan morph yang menguncup ke kartu itu
    LaunchedEffect(vm.revealTick) {
        if (vm.revealTick == 0) return@LaunchedEffect
        val id = vm.revealId ?: return@LaunchedEffect
        val lv = vm.sorted(vm.liveF())
        val npin = lv.count { it.p }
        val sec = !vm.remFilter && npin > 0 && npin < lv.size
        val pin = if (sec) lv.filter { it.p } else lv
        val oth = if (sec) lv.filter { !it.p } else emptyList()
        var idx = pin.indexOfFirst { it.id == id }
        if (idx >= 0) { if (sec) idx += 1 }
        else {
            val j = oth.indexOfFirst { it.id == id }
            if (j < 0) return@LaunchedEffect
            idx = 1 + pin.size + 1 + j
        }
        try {
            val topPx = with(dens) { (sat + 72.dp).toPx() }
            val botPx = state.layoutInfo.viewportEndOffset - with(dens) { 24.dp.toPx() }
            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
            val ok = info != null && info.offset.y >= topPx - 2f && info.offset.y + info.size.height <= botPx
            if (info != null) {
                // kartu sudah tampil tapi terpotong: geser seminimal mungkin sampai utuh (atas maupun bawah), bukan lompat ke puncak
                if (!ok) {
                    val top = info.offset.y.toFloat()
                    val bot = (info.offset.y + info.size.height).toFloat()
                    val d = when {
                        top < topPx -> top - topPx
                        info.size.height <= botPx - topPx -> bot - botPx
                        else -> top - topPx
                    }
                    if (d != 0f) state.animateScrollBy(d, tween(320, easing = Emph))
                }
            } else {
                // kartu di luar layar: lompat dekat dulu (tersembunyi di balik editor yang masih besar), lalu geser mulus
                if (state.firstVisibleItemIndex > idx + 8) state.scrollToItem(idx + 8)
                state.animateScrollToItem(idx)
            }
        } catch (e: Exception) { }
    }
    LaunchedEffect(vm.searchOn) {
        if (vm.searchOn) {
            delay(60); try { fr.requestFocus() } catch (e: Exception) { }
        } else {
            focusMgr.clearFocus()
            delay(750)
            if (!vm.searchOn) vm.query = ""
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val imeH = with(dens) { WindowInsets.ime.getBottom(dens).toDp() }
        val hPx = with(dens) { maxHeight.toPx() }
        val wPx = with(dens) { maxWidth.toPx() }
        // perkiraan slot kartu paling atas: titik awal morph menutup saat kartu tujuan belum tampil di layar
        run {
            val gap = 12f * dens.density
            val padS = 16f * dens.density
            val cw = if (cols == 2) (wPx - 2 * padS - gap) / 2f else wPx - 2 * padS
            val t0 = with(dens) { (sat + 78.dp).toPx() }
            vm.topSlot = Morph(padS, t0, padS + cw, t0 + 92f * dens.density, 28f * dens.density, pal.pc, card = true)
        }
        val livePanelH = (maxHeight - imeH - (sat + 8.dp) - 10.dp).coerceAtLeast(160.dp)
        // saat menutup, tinggi panel dibekukan agar tidak ikut naik-turun mengikuti keyboard yang menghilang
        val panelHold = remember { arrayOf(livePanelH) }
        if (vm.searchOn) panelHold[0] = livePanelH
        val panelH = panelHold[0]

        // daftar (grid 1 atau 2 kolom)
        val np = live.count { it.p }
        val sections = !vm.remFilter && np > 0 && np < live.size
        val pinned = if (sections) live.filter { it.p } else live
        val others = if (sections) live.filter { !it.p } else emptyList()
        val full = StaggeredGridItemSpan.FullLine
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(cols),
            state = state, modifier = Modifier.fillMaxSize().nestedScroll(fabConn).graphicsLayer { alpha = 1f - sa },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = sat + 78.dp, bottom = 130.dp + sab),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (live.isEmpty()) {
                item(key = "empty", span = full) {
                    T(
                        if (vm.remFilter) "Belum ada pengingat. Buka catatan, lalu ketuk ikon lonceng."
                        else if (vm.filter != null) "Belum ada catatan berlabel" else "Ketuk Baru untuk memulai", 17f, 600, pal.osv,
                        Modifier.fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            if (sections) item(key = "h-pin", span = full) {
                T("Disematkan", 13f, 900, pal.p, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp), ls = .08f)
            }
            gridItems(pinned, key = { it.id }) { d ->
                HomeCard(vm, nav, d, pos[d.id] ?: 0, !sel.isEmpty(), cols, hPx, wPx, dens.density, pos, flowItemG())
            }
            if (sections) item(key = "h-other", span = full) {
                T("Lainnya", 13f, 900, pal.p, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp), ls = .08f)
            }
            gridItems(others, key = { it.id }) { d ->
                HomeCard(vm, nav, d, pos[d.id] ?: 0, !sel.isEmpty(), cols, hPx, wPx, dens.density, pos, flowItemG())
            }
        }

        // header lengket: tombol menu, pil pencarian, tombol 1/2 kolom
        Box(Modifier.fillMaxWidth().height(sat + 72.dp).background(pal.s).graphicsLayer { alpha = 1f - sa * 0f })
        Box(
            Modifier.padding(start = 10.dp, top = sat + 10.dp)
                .graphicsLayer { translationX = -84.dp.toPx() * sa; scaleX = 1f - .4f * sa; scaleY = 1f - .4f * sa; alpha = 1f - sa }
        ) { IconBtn("menu", pal.osv, { if (sa < .5f) openDrawer() }) }
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = sat + 10.dp)
                .graphicsLayer { translationX = 84.dp.toPx() * sa; scaleX = 1f - .4f * sa; scaleY = 1f - .4f * sa; alpha = 1f - sa }
        ) { IconBtn(if (cols == 1) "cols" else "rows", pal.osv, { if (sa < .5f) vm.setPr(vm.pr.copy(cols = if (cols == 1) 2 else 1)) }) }

        val padH = lerp(72.dp, 8.dp, sa)
        val pillH = lerp(56.dp, panelH, sa)
        Box(
            Modifier.padding(top = sat + 8.dp, start = padH, end = padH).fillMaxWidth().height(pillH)
                .clip(RoundedCornerShape(lerp(28.dp, 32.dp, sa))).background(pal.c3)
                .pointerInput(vm.searchOn) { if (vm.searchOn) detectTapGestures { } }
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !vm.searchOn
                    ) { vm.searchOn = true }.padding(start = 20.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ic("search", if (vm.searchOn) pal.p else pal.osv)
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = vm.query, onValueChange = { vm.query = it },
                        modifier = Modifier.weight(1f).focusRequester(fr).onFocusChanged { if (it.isFocused && !vm.searchOn) vm.searchOn = true },
                        textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 17.sp, fontWeight = FontWeight(500)),
                        singleLine = true, cursorBrush = SolidColor(pal.p),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusMgr.clearFocus() }),
                        decorationBox = { inner ->
                            Box { if (vm.query.isEmpty()) T(if (vm.remFilter) "Cari di Pengingat" else vm.filter?.let { "Cari di $it" } ?: "Cari catatan", 17f, 500, pal.osv, maxLines = 1); inner() }
                        }
                    )
                    Box(Modifier.graphicsLayer { alpha = sa; scaleX = .4f + .6f * sa; scaleY = .4f + .6f * sa }) {
                        IconBtn("x", pal.p, { vm.searchOn = false }, size = 44.dp, enabled = vm.searchOn)
                    }
                }
                val q = vm.query.trim().lowercase()
                val res = vm.sorted(vm.liveF().filter { q.isEmpty() || (it.t + " " + it.x).lowercase().contains(q) })
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = sa; translationY = (1f - sa) * 14.dp.toPx() },
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (res.isEmpty() && vm.searchOn) item(key = "empty-search") {
                        T(if (q.isNotEmpty()) "Tidak ada hasil" else "Belum ada catatan", 17f, 600, pal.osv,
                            flowItem().fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    // indeks warna/bentuk memakai posisi di daftar penuh, jadi kartu tidak berganti warna saat tersaring;
                    // kartu yang cocok bergeser mulus, yang tidak cocok memudar keluar
                    items(res, key = { "s" + it.id }) { d ->
                        val ri = res.indexOf(d)
                        NoteCard(
                            d, pos[d.id] ?: ri, vm.pr.cd, false,
                            flowItem().makeRoom(vm, d.id, ri, { res.indexOfFirst { it.id == vm.openId } }, hPx, dens.density),
                            paper = true
                        ) { m ->
                            focusMgr.clearFocus(); vm.cardMorph[d.id] = m; nav.openNote(d.id, m)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeCard(
    vm: AppVM, nav: Nav, d: Note, i: Int, selMode: Boolean, cols: Int, hPx: Float, wPx: Float, dens: Float,
    pos: Map<String, Int>, base: Modifier
) {
    val sel = vm.sel
    androidx.compose.runtime.DisposableEffect(d.id) { onDispose { vm.cardMorph.remove(d.id) } }
    Column(base.makeRoom(vm, d.id, i, { vm.openId?.let { pos[it] } ?: -1 }, hPx, dens, cols, wPx)) {
        val first = remember(d.id) { !vm.seen.contains(d.id) }
        val prog = remember(d.id) { Animatable(if (first) 0f else 1f) }
        LaunchedEffect(d.id) {
            if (first) {
                delay(minOf(i, 8) * 55L); vm.seen.add(d.id)
                prog.animateTo(1f, spring(.7f, 350f))
            }
        }
        SwipeCard(
            enabled = !selMode,
            shape = SH[i % 5].let { RoundedCornerShape(it[0].dp, it[1].dp, it[2].dp, it[3].dp) },
            onArchive = { vm.mark(listOf(d.id), "Catatan diarsipkan") { it.copy(a = true, p = false) } },
            onDelete = { vm.mark(listOf(d.id), "Dipindah ke sampah") { it.copy(dt = System.currentTimeMillis()) } }
        ) {
            NoteCard(
                d, i, vm.pr.cd, d.id in sel,
                Modifier.graphicsLayer {
                    val pv = prog.value
                    alpha = pv.coerceIn(0f, 1f); translationY = (1f - pv) * 32.dp.toPx()
                    val sc = .92f + .08f * pv.coerceIn(0f, 1f); scaleX = sc; scaleY = sc
                },
                onLong = { if (d.id !in sel) sel.add(d.id) },
                track = { m -> if (!vm.searchOn) vm.cardMorph[d.id] = m },
                cols = cols,
                onClick = { m ->
                    if (selMode) { if (d.id in sel) sel.remove(d.id) else sel.add(d.id) }
                    else nav.openNote(d.id, m)
                }
            )
        }
    }
}

@Composable
fun Fab(vm: AppVM, small: Boolean, hide: Boolean, lift: Dp, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (hide) .4f else if (pr) .93f else 1f, spring(.55f, 500f), label = "sc")
    val al by animateFloatAsState(if (hide) 0f else 1f, tween(250), label = "al")
    val r by animateDpAsState(if (pr) 16.dp else 26.dp, spring(.6f, 500f), label = "r")
    val ly by animateDpAsState(lift, spring(.7f, 400f), label = "ly")
    if (hide && al <= .01f) return
    val sab = sabDp()
    val dens = LocalDensity.current.density
    val shape = RoundedCornerShape(r)
    Box(Modifier.fillMaxSize().padding(end = 16.dp, bottom = 20.dp + sab), contentAlignment = Alignment.BottomEnd) {
        // tanpa animateContentSize (itu yang memotong bayangan); label dianimasikan lewat AnimatedVisibility
        Row(
            Modifier.offset(y = -ly)
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    if (!hide && b.width > 0f) vm.fabMorph = Morph(b.left, b.top, b.right, b.bottom, 26f * dens, pal.pc, card = true)
                }
                .graphicsLayer { scaleX = sc; scaleY = sc; alpha = al; transformOrigin = TransformOrigin(1f, 1f) }
                .shadow(
                    if (pal.dark) 4.dp else 3.dp, shape, clip = false,
                    ambientColor = pal.p.copy(alpha = if (pal.dark) .35f else .22f),
                    spotColor = pal.p.copy(alpha = if (pal.dark) .45f else .32f)
                )
                .height(72.dp).widthIn(min = 72.dp).clip(shape).background(pal.pc)
                .clickable(interactionSource = src, indication = LocalIndication.current, enabled = !hide, onClick = onClick)
                .padding(horizontal = 21.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Ic("plus", pal.opc, 30.dp)
            AnimatedVisibility(
                visible = !small,
                enter = expandHorizontally(tween(320, easing = Emph), expandFrom = Alignment.Start) + fadeIn(tween(240, 60)),
                exit = shrinkHorizontally(tween(280, easing = Emph), shrinkTowards = Alignment.Start) + fadeOut(tween(120))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(12.dp))
                    T("Baru", 20f, 900, pal.opc, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SelBar(vm: AppVM) {
    val pal = LocalPal.current
    val sat = satDp()
    var count by remember { mutableStateOf(0) }
    if (vm.sel.isNotEmpty()) count = vm.sel.size
    AnimatedVisibility(
        visible = vm.sel.isNotEmpty(),
        enter = slideInVertically(tween(450, easing = Emph)) { -it }, exit = slideOutVertically(tween(450, easing = Emph)) { -it }
    ) {
        Row(
            Modifier.fillMaxWidth().height(sat + 72.dp).background(pal.s).padding(top = sat + 12.dp, start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBtn("x", pal.os, { vm.sel.clear() }, size = 48.dp)
            Box(Modifier.weight(1f).height(48.dp).padding(start = 6.dp), Alignment.CenterStart) { T("$count dipilih", 18f, 900, pal.os) }
            IconBtn("all", pal.os, { vm.liveF().forEach { if (it.id !in vm.sel) vm.sel.add(it.id) } }, size = 48.dp)
            IconBtn("pin", pal.os, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) {
                    val all = ids.all { vm.note(it)?.p == true }
                    vm.sel.clear()
                    vm.mark(ids, if (all) "Sematan dilepas" else "${ids.size} catatan disematkan") { it.copy(p = !all) }
                }
            }, size = 48.dp)
            IconBtn("arc", pal.os, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) { vm.sel.clear(); vm.mark(ids, "${ids.size} catatan diarsipkan") { it.copy(a = true, p = false) } }
            }, size = 48.dp)
            IconBtn("del", pal.os, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) { vm.sel.clear(); vm.mark(ids, "${ids.size} catatan dipindah ke sampah") { it.copy(dt = System.currentTimeMillis()) } }
            }, size = 48.dp)
        }
    }
}

// ---------- arsip & sampah ----------
@Composable
fun ListScreen(
    vm: AppVM, title: String, notes: List<Note>, hint: String?, empty: String, trash: Boolean,
    onBack: () -> Unit, onClick: (Note) -> Unit, onClear: (() -> Unit)? = null
) {
    val pal = LocalPal.current
    val sat = satDp()
    val sab = sabDp()
    var hPx by remember { mutableFloatStateOf(0f) }
    val dens = LocalDensity.current.density
    var tick by remember { mutableIntStateOf(0) }
    val seen = remember { HashSet<String>() }
    LaunchedEffect(Unit) { delay(560); tick++ }
    Column(Modifier.fillMaxSize().background(pal.s).onSizeChanged { hPx = it.height.toFloat() }) {
        Row(
            Modifier.fillMaxWidth().padding(top = sat + 8.dp, bottom = 8.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("back", pal.os, onBack)
            T(title, 22f, 900, pal.os, Modifier.weight(1f).padding(start = 8.dp), ls = -.01f, maxLines = 1)
            if (onClear != null && notes.isNotEmpty()) IconBtn("del", pal.os, onClear)
        }
        if (hint != null) T(hint, 14f, 600, pal.osv, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp + sab),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (notes.isEmpty()) item(key = "empty-list") {
                T(empty, 17f, 600, pal.osv, flowItem().fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
            }
            items(notes, key = { it.id }) { d ->
                val ix = notes.indexOf(d)
                androidx.compose.runtime.DisposableEffect(d.id) { onDispose { vm.cardMorph.remove(d.id) } }
                val first = remember(d.id) { d.id !in seen }
                val prog = remember(d.id) { Animatable(if (first) 0f else 1f) }
                LaunchedEffect(d.id) {
                    if (first) {
                        delay(minOf(ix, 8) * 55L); seen.add(d.id)
                        prog.animateTo(1f, spring(.75f, 380f))
                    }
                }
                NoteCard(
                    d, ix, vm.pr.cd, false,
                    flowItem()
                        .makeRoom(vm, d.id, ix, { notes.indexOfFirst { it.id == vm.openId } }, hPx, dens)
                        .graphicsLayer {
                            val pv = prog.value
                            alpha = pv.coerceIn(0f, 1f); translationY = (1f - pv) * 28.dp.toPx()
                            val s = .94f + .06f * pv.coerceIn(0f, 1f); scaleX = s; scaleY = s
                        },
                    delTime = if (trash) d.dt else 0L,
                    track = { m -> vm.cardMorph[d.id] = m }, tick = tick
                ) { onClick(d) }
            }
        }
    }
}

// ---------- pengaturan: gaya aplikasi Google (Pixel Settings + Material 3 Expressive) ----------
const val POS_ONE = 0
const val POS_TOP = 1
const val POS_MID = 2
const val POS_BOT = 3

/** Pegas tinggi untuk semua kartu di Pengaturan: tinggi berubah mengikuti isi dengan gerak lembut tanpa memantul. */
val SizeSpring = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = .9f, stiffness = 420f)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.slab(c: Color, l: Float, r: Float, y: Float, h: Float, rl: Float, rr: Float) {
    if (r - l < 1f) return
    val p = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                l, y, r, y + h,
                topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(rl),
                topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(rr),
                bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(rr),
                bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(rl)
            )
        )
    }
    drawPath(p, c)
}

/** Slider M3 Expressive: trek tebal terbelah di sekitar gagang; gagang menipis dan memanjang saat disentuh. */
@Composable
fun BigSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val pal = LocalPal.current
    var w by remember { mutableStateOf(1f) }
    var down by remember { mutableStateOf(false) }
    val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val fa by animateFloatAsState(f, spring(1f, 1200f), label = "sf")
    val hw by animateFloatAsState(if (down) 2f else 4f, spring(.6f, 600f), label = "shw")
    val hh by animateFloatAsState(if (down) 56f else 44f, spring(.6f, 600f), label = "shh")
    fun at(x: Float) = onChange((min + (x / w).coerceIn(0f, 1f) * (max - min)).let { Math.round(it).toFloat().coerceIn(min, max) })
    Box(
        Modifier.fillMaxWidth().height(56.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(min, max) { detectTapGestures { at(it.x) } }
            .pointerInput(min, max) {
                detectDragGestures(
                    onDragStart = { down = true; at(it.x) },
                    onDragEnd = { down = false },
                    onDragCancel = { down = false }
                ) { ch, _ -> ch.consume(); at(ch.position.x) }
            }
            .drawBehind {
                val th = 18.dp.toPx()
                val y = (size.height - th) / 2
                val hwp = hw.dp.toPx()
                val gap = 6.dp.toPx()
                val tx = (size.width * fa).coerceIn(hwp / 2, size.width - hwp / 2)
                slab(pal.p, 0f, tx - hwp / 2 - gap, y, th, th / 2, 3.dp.toPx())
                slab(pal.sc, tx + hwp / 2 + gap, size.width, y, th, 3.dp.toPx(), th / 2)
                val hp = hh.dp.toPx()
                drawRoundRect(
                    pal.p, Offset(tx - hwp / 2, (size.height - hp) / 2), Size(hwp, hp),
                    androidx.compose.ui.geometry.CornerRadius(hwp / 2)
                )
            }
    )
}

/** Tombol segmen pil: indikator meluncur dengan pegas dan meregang selagi bergerak (morphing). */
@Composable
fun SegPill(n: Int, sel: Int, onSel: (Int) -> Unit, h: Dp = 52.dp, item: @Composable (Int, Color) -> Unit) {
    val pal = LocalPal.current
    val x = animateFloatAsState(sel.toFloat(), spring(.62f, 380f), label = "seg")
    Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(pal.c4).padding(4.dp)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(1f / n)
                .graphicsLayer {
                    val st = Math.abs(sel - x.value).coerceAtMost(1f)
                    translationX = x.value * size.width
                    scaleX = 1f + .22f * st
                    scaleY = 1f - .08f * st
                }
                .clip(CircleShape).background(pal.p)
        )
        Row(Modifier.fillMaxSize()) {
            for (i in 0 until n) {
                val fg by animateColorAsState(if (i == sel) pal.op else pal.os, tween(220), label = "segfg")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable { onSel(i) },
                    Alignment.Center
                ) { item(i, fg) }
            }
        }
    }
}

/** Sakelar M3 Expressive: gagang membesar dan bergeser, warna trek ikut berubah. */
@Composable
fun MorphSwitch(on: Boolean) {
    val pal = LocalPal.current
    val k by animateFloatAsState(if (on) 1f else 0f, spring(.7f, 420f), label = "sw")
    val track = androidx.compose.ui.graphics.lerp(pal.c4, pal.p, k)
    val edge = androidx.compose.ui.graphics.lerp(pal.osv, pal.p, k)
    val thumb = androidx.compose.ui.graphics.lerp(pal.osv, pal.op, k)
    Box(Modifier.size(width = 52.dp, height = 32.dp).clip(CircleShape).background(track).border(2.dp, edge, CircleShape)) {
        val d = 16f + 8f * k
        val cx = 16f + 20f * k
        Box(
            Modifier.size(d.dp).graphicsLayer { translationX = (cx - d / 2f).dp.toPx(); translationY = ((32f - d) / 2f - 0f).dp.toPx() }
                .clip(CircleShape).background(thumb),
            Alignment.Center
        ) { Box(Modifier.graphicsLayer { alpha = k; scaleX = k; scaleY = k }) { Ic("ok", pal.p, 16.dp) } }
    }
}

/** Grup pengaturan: judul kecil berwarna di atas, kartu bersambung di bawahnya. Masuk bergantian dengan pegas. */
@Composable
fun SGroup(title: String, wait: Int, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(wait.toLong()); a.animateTo(1f, spring(.8f, 320f)) }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 28.dp)
            .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 28.dp.toPx() }
    ) {
        T(title, 14f, 600, pal.p, Modifier.padding(start = 16.dp, bottom = 10.dp), ls = .01f)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

/** Kartu bersambung: sudut luar besar, sudut antar kartu kecil. Saat ditekan semua sudut membulat (morph). */
@Composable
fun SCard(
    pos: Int, onClick: (() -> Unit)? = null,
    pad: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    bg: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val press = pr && onClick != null
    val top = if (pos == POS_ONE || pos == POS_TOP) 28f else 6f
    val bot = if (pos == POS_ONE || pos == POS_BOT) 28f else 6f
    val tr by animateFloatAsState(if (press) 28f else top, spring(.6f, 500f), label = "tr")
    val br by animateFloatAsState(if (press) 28f else bot, spring(.6f, 500f), label = "br")
    val shape = RoundedCornerShape(tr.coerceAtLeast(0f).dp, tr.coerceAtLeast(0f).dp, br.coerceAtLeast(0f).dp, br.coerceAtLeast(0f).dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(bg ?: pal.c2)
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick) else Modifier)
            .animateContentSize(SizeSpring).padding(pad),
        content = content
    )
}

@Composable
fun SActionRow(pos: Int, icon: String, title: String, sub: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    SCard(pos, onClick, PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(pal.pc), Alignment.Center) { Ic(icon, pal.opc, 22.dp) }
            Column(Modifier.weight(1f)) {
                T(title, 16f, 500, pal.os)
                T(sub, 14f, 400, pal.osv, lh = 1.3f)
            }
        }
    }
}

@Composable
fun FontChoice(name: String, fam: FontFamily, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 12.dp else if (on) 28.dp else 16.dp, spring(.6f, 500f), label = "r")
    val k by animateFloatAsState(if (on) 1f else 0f, spring(.8f, 380f), label = "b")
    val fg = if (on) pal.opc else pal.os
    CompositionLocalProvider(LocalFF provides fam) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(r)).background(pal.pc.copy(alpha = k))
                .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                T(name, 18f, 500, fg)
                T("Catatan rapi, cepat, nyaman 0123", 14f, 400, fg, alpha = .75f)
            }
            Box(Modifier.size(24.dp).graphicsLayer { scaleX = k; scaleY = k; alpha = k }) { Ic("ok", fg, 24.dp) }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppVM, act: Actions, onBack: () -> Unit) {
    val pal = LocalPal.current
    val sat = satDp()
    val sab = sabDp()
    val pr = vm.pr
    val scroll = rememberScrollState()
    var custom by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(pal.s)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(sat + 64.dp))
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + sab)
            ) {
                // judul besar: mengecil dan memudar saat digulir, digantikan judul kecil di bilah atas
                T(
                    "Pengaturan", 40f, 400, pal.os,
                    Modifier.padding(start = 8.dp, top = 20.dp, bottom = 32.dp).graphicsLayer {
                        val k = (scroll.value / 110.dp.toPx()).coerceIn(0f, 1f)
                        alpha = 1f - (k * 1.6f).coerceAtMost(1f)
                        scaleX = 1f - .12f * k; scaleY = 1f - .12f * k
                        transformOrigin = TransformOrigin(0f, .5f)
                    },
                    ls = -.01f, maxLines = 1
                )
                SGroup("Tampilan", 0) {
                    SCard(POS_TOP) {
                        T("Tema", 16f, 500, pal.os)
                        val modes = listOf("auto" to "phone", "light" to "lm", "tonal" to "pal", "dark" to "dm", "amoled" to "am")
                        val cap = when (pr.mode) {
                            "auto" -> "Otomatis: terang atau gelap mengikuti perangkat"
                            "light" -> "Terang"
                            "tonal" -> "Tonal"
                            "dark" -> "Gelap"
                            else -> "AMOLED (hitam pekat)"
                        }
                        Crossfade(cap, Modifier.padding(top = 2.dp, bottom = 14.dp), tween(200), label = "cap") { T(it, 14f, 400, pal.osv) }
                        SegPill(5, modes.indexOfFirst { it.first == pr.mode }.coerceAtLeast(0), { vm.setPr(pr.copy(mode = modes[it].first)) }) { i, fg ->
                            Ic(modes[i].second, fg, 24.dp)
                        }
                    }
                    SCard(POS_BOT) {
                        T("Warna aplikasi", 16f, 500, pal.os, Modifier.padding(bottom = 12.dp))
                        val dynAvail = android.os.Build.VERSION.SDK_INT >= 31
                        val dynOn = pr.dyn && dynAvail
                        if (dynAvail) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(20.dp))
                                    .clickable { vm.setPr(pr.copy(dyn = !dynOn)) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    T("Warna dinamis", 16f, 400, pal.os)
                                    T("Material You, mengikuti wallpaper", 14f, 400, pal.osv)
                                }
                                MorphSwitch(dynOn)
                            }
                        }
                        val dim by animateFloatAsState(if (dynOn) .38f else 1f, tween(260), label = "dim")
                        val seeds = listOf("#6750A4", "#0B57D0", "#00796B", "#2E7D32", "#C77700", "#B3261E", "#C2185B", "#5D4037")
                        val all: List<String?> = seeds + listOf<String?>(null)
                        Column(Modifier.graphicsLayer { alpha = dim }) {
                            all.chunked(5).forEachIndexed { ri, row ->
                                Row(Modifier.fillMaxWidth().padding(top = if (ri == 0) 0.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (k in 0 until 5) {
                                        val c = row.getOrNull(k)
                                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                                            if (c != null) {
                                                val sel = !dynOn && c.equals(pr.seed, true)
                                                val rp by animateFloatAsState(if (sel) 30f else 50f, spring(.6f, 400f), label = "rp")
                                                val ck by animateFloatAsState(if (sel) 1f else 0f, spring(.6f, 400f), label = "ck")
                                                Box(
                                                    Modifier.fillMaxSize().clip(RoundedCornerShape(rp.toInt().coerceIn(0, 50)))
                                                        .background(Color(android.graphics.Color.parseColor(c)))
                                                        .clickable { vm.setPr(pr.copy(seed = c, dyn = false)) },
                                                    Alignment.Center
                                                ) { Box(Modifier.graphicsLayer { scaleX = ck; scaleY = ck; alpha = ck.coerceIn(0f, 1f) }) { Ic("ok", Color.White) } }
                                            } else if (k < row.size) {
                                                Box(
                                                    Modifier.fillMaxSize().clip(CircleShape).background(
                                                        androidx.compose.ui.graphics.Brush.sweepGradient(
                                                            listOf(Color(0xFFFF4444), Color(0xFFFFBB33), Color(0xFF44DD66), Color(0xFF33CCFF), Color(0xFF7744FF), Color(0xFFFF44AA), Color(0xFFFF4444))
                                                        )
                                                    ).clickable { custom = true },
                                                    Alignment.Center
                                                ) { Box(Modifier.size(30.dp).clip(CircleShape).background(pal.c2), Alignment.Center) { Ic("plus", pal.os, 18.dp) } }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                SGroup("Catatan", 60) {
                    SCard(POS_TOP) {
                        T("Tampilan kartu", 16f, 500, pal.os, Modifier.padding(bottom = 14.dp))
                        val kinds = listOf("t" to "Judul", "s" to "Ringkas", "f" to "Lengkap")
                        SegPill(3, kinds.indexOfFirst { it.first == pr.cd }.coerceAtLeast(0), { vm.setPr(pr.copy(cd = kinds[it].first)) }) { i, fg ->
                            T(kinds[i].second, 15f, 500, fg)
                        }
                        Box(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.s).animateContentSize(SizeSpring).padding(12.dp)) {
                            NoteCard(
                                Note(id = "pv", t = "Rencana minggu ini", x = "Selesaikan tinjauan pustaka, kirim proposal ke dosen, lalu rapikan catatan kuliah sebelum Jumat.", u = System.currentTimeMillis()),
                                0, pr.cd, false
                            ) { }
                        }
                    }
                    SCard(POS_BOT) {
                        T("Urutan catatan", 16f, 500, pal.os, Modifier.padding(bottom = 14.dp))
                        val sorts = listOf("u" to "Diubah", "c" to "Dibuat")
                        SegPill(2, sorts.indexOfFirst { it.first == pr.sort }.coerceAtLeast(0), { vm.setPr(pr.copy(sort = sorts[it].first)) }) { i, fg ->
                            T(sorts[i].second, 15f, 500, fg)
                        }
                        Crossfade(if (pr.sort == "c") "Yang terbaru dibuat di atas" else "Yang terakhir diedit di atas", Modifier.padding(top = 12.dp, start = 4.dp), tween(200), label = "scap") {
                            T(it, 14f, 400, pal.osv)
                        }
                    }
                }
                SGroup("Teks", 120) {
                    SCard(POS_TOP) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            T("Ukuran teks", 16f, 500, pal.os)
                            T("${pr.fs} px", 16f, 600, pal.p)
                        }
                        BigSlider(pr.fs.toFloat(), 14f, 30f) { vm.setPr(pr.copy(fs = it.toInt())) }
                        T("Jarak baris", 16f, 500, pal.os, Modifier.padding(top = 6.dp, bottom = 14.dp))
                        val lhs = listOf(1.45f to "Rapat", 1.65f to "Normal", 1.95f to "Lega")
                        SegPill(3, lhs.indexOfFirst { it.first == pr.lh }.let { if (it < 0) 1 else it }, { vm.setPr(pr.copy(lh = lhs[it].first)) }) { i, fg ->
                            T(lhs[i].second, 15f, 500, fg)
                        }
                    }
                    SCard(POS_MID) {
                        T("Pratinjau", pr.fs * 1.4f, 500, pal.p, lh = 1.1f, ls = -.01f)
                        Spacer(Modifier.height((pr.fs * .35f).dp))
                        T("Dinduy menjaga catatan tetap rapi, dengan huruf tebal dan jarak yang nyaman untuk mata.", pr.fs.toFloat(), 400, pal.os, lh = pr.lh)
                    }
                    SCard(POS_BOT, pad = PaddingValues(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp)) {
                        T("Jenis huruf", 16f, 500, pal.os, Modifier.padding(start = 8.dp, bottom = 10.dp))
                        val am = LocalContext.current.assets
                        listOf("def" to "Bawaan", "flex" to "Roboto Flex", "times" to "Times New Roman").forEach { (id, name) ->
                            val fam = remember(id) { loadFont(am, id) }
                            FontChoice(name, fam, pr.ff == id) { vm.setPr(pr.copy(ff = id)) }
                        }
                    }
                }
                SGroup("Cadangan", 180) {
                    val n = vm.docs.size
                    val ni = vm.imgIds().size
                    val old = vm.lastBk != 0L && System.currentTimeMillis() - vm.lastBk > 7 * 86400000L
                    val info = "$n catatan, $ni foto. " + (if (vm.lastBk != 0L) {
                        "Cadangan terakhir: " + SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id", "ID")).format(Date(vm.lastBk)) +
                            (if (old) ". Sudah lebih dari seminggu, cadangkan lagi." else ".")
                    } else "Belum pernah dicadangkan.") + " Berkas cadangan disimpan di perangkat, tanpa server."
                    SCard(POS_TOP) { T(info, 14f, 400, pal.osv, lh = 1.5f) }
                    SActionRow(POS_MID, "dl", "Simpan cadangan", "Satu berkas .dinduy berisi catatan dan foto") { act.saveBackup() }
                    SActionRow(POS_MID, "up", "Bagikan cadangan", "Kirim lewat WhatsApp, Berkas, dan lainnya") { act.shareBackup() }
                    SActionRow(POS_BOT, "restore", "Pulihkan cadangan", "Pilih berkas .dinduy dari perangkat (termasuk dari versi web)") { act.restoreBackup() }
                }
            }
        }
        // bilah atas: transparan di puncak, berubah jadi warna wadah saat digulir; judul kecil muncul menggantikan judul besar
        Row(
            Modifier.fillMaxWidth().height(sat + 64.dp)
                .drawBehind {
                    val k = (scroll.value / 80.dp.toPx()).coerceIn(0f, 1f)
                    drawRect(androidx.compose.ui.graphics.lerp(pal.s, pal.c2, k))
                }
                .padding(top = sat, start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("back", pal.os, onBack)
            T(
                "Pengaturan", 22f, 400, pal.os,
                Modifier.padding(start = 8.dp).graphicsLayer {
                    val k = (scroll.value / 110.dp.toPx()).coerceIn(0f, 1f)
                    val a = ((k - .5f) * 2f).coerceIn(0f, 1f)
                    alpha = a; translationY = (1f - a) * 12.dp.toPx()
                },
                maxLines = 1
            )
        }
    }
    if (custom) {
        val (h0, s0) = hueSat(pr.seed)
        var h by remember { mutableStateOf(h0) }
        var s by remember { mutableStateOf(s0.coerceIn(35f, 88f)) }
        val col = hsl(h, s, 40f)
        AlertDialog(
            onDismissRequest = { custom = false },
            containerColor = pal.c3,
            shape = RoundedCornerShape(28.dp),
            title = { T("Warna kustom", 24f, 400, pal.os) },
            text = {
                Column {
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(col))
                    Spacer(Modifier.height(16.dp))
                    T("Corak", 14f, 500, pal.osv)
                    BigSlider(h, 0f, 360f) { h = it }
                    T("Intensitas", 14f, 500, pal.osv)
                    BigSlider(s, 35f, 88f) { s = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setPr(pr.copy(seed = String.format("#%06X", hsl(h, s, 50f).toArgb() and 0xFFFFFF), dyn = false)); custom = false
                }) { T("Pakai", 15f, 600, pal.p) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { T("Batal", 15f, 500, pal.osv) } }
        )
    }
}

// ---------- sheet ----------
@Composable
fun SheetHost(vm: AppVM, nav: Nav, act: Actions) {
    val pal = LocalPal.current
    val s = vm.sheet ?: return
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = { vm.sheet = null }
    ModalBottomSheet(
        onDismissRequest = { vm.sheet = null }, sheetState = st,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp), containerColor = pal.c3,
        scrimColor = Color.Black.copy(alpha = .5f),
        dragHandle = {
            Box(Modifier.padding(top = 14.dp, bottom = 18.dp).width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(pal.ol))
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).navigationBarsPadding()) {
            fun title(id: String) = vm.note(id)?.t?.ifEmpty { "Tanpa judul" } ?: "Tanpa judul"
            when (s) {
                is Sheet.LblEdit -> {
                    T(s.name, 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "pen", "Ganti nama") { close(); vm.lblDlg = LblDlg(s.name, null) }
                    Opt(2, "del", "Hapus label", "Catatan tidak ikut terhapus") { close(); vm.deleteLabel(s.name) }
                }
                is Sheet.Arc -> {
                    T(title(s.id), 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "txt", "Buka catatan") { close(); nav.openNote(s.id, vm.cardMorph[s.id]) }
                    Opt(2, "unarc", "Keluarkan dari arsip") { close(); vm.mark(listOf(s.id), "Dikeluarkan dari arsip") { it.copy(a = false) } }
                    Opt(3, "del", "Hapus catatan") { close(); vm.mark(listOf(s.id), "Dipindah ke sampah") { it.copy(dt = System.currentTimeMillis()) } }
                }
                is Sheet.Trs -> {
                    T(title(s.id), 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "restore", "Pulihkan") { close(); vm.mark(listOf(s.id), "Catatan dipulihkan") { it.copy(dt = 0L) } }
                    Opt(2, "del", "Hapus permanen") { close(); vm.purge(s.id) }
                }
                is Sheet.Bk -> {
                    val p = vm.pending
                    T("Pulihkan cadangan", 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp), ls = -.01f)
                    if (p != null) T(
                        "${p.notes.size} catatan, ${p.nImg} foto, dibuat " + SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id", "ID")).format(Date(p.at)),
                        14f, 400, pal.osv, Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                    Opt(1, "lib", "Gabungkan", "Tambahkan dari cadangan, catatan yang ada tetap aman") { close(); vm.applyBackup(false) }
                    Opt(2, "restore", "Timpa semua", "Ganti semua catatan di perangkat ini dengan isi cadangan") { close(); vm.applyBackup(true) }
                }
                is Sheet.EmptyTrash -> {
                    T("Kosongkan sampah?", 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "del", "Hapus semua permanen", "Tidak bisa dibatalkan") { close(); vm.purgeAll() }
                }
            }
        }
    }
}

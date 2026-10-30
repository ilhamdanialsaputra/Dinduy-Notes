@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.dinduy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.unit.IntOffset
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
    fun copyText(t: String)
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

/**
 * Saat sebuah catatan membuka (morph kartu -> layar penuh), kartu lain tidak ditimpa begitu saja:
 * kartu di atas terdorong ke atas, kartu di bawah terdorong ke bawah mengikuti tepi kartu yang melebar,
 * sedikit mengecil dan memudar. Saat menutup, geraknya berbalik dengan spring yang sama.
 */
fun Modifier.makeRoom(vm: AppVM, id: String, idx: Int, openIdx: () -> Int, h: Float, dens: Float): Modifier = graphicsLayer {
    val oid = vm.openId
    val m = vm.morph
    val p = vm.openP.value.coerceIn(0f, 1f)
    if (p <= 0.001f || oid == null || m == null || oid == id) return@graphicsLayer
    val oi = openIdx()
    if (oi < 0) return@graphicsLayer
    val wave = minOf(Math.abs(idx - oi), 5) * 10f * dens
    translationY = if (idx < oi) -(maxOf(m.t, 0f) + wave) * p else (maxOf(h - m.b, 0f) + wave) * p
    val s = 1f - .05f * p
    scaleX = s; scaleY = s
    alpha = 1f - .6f * p
}

@Composable
fun NoteCard(
    d: Note, i: Int, kind: String, selected: Boolean, modifier: Modifier = Modifier,
    delTime: Long = 0L, onLong: (() -> Unit)? = null, track: ((Morph) -> Unit)? = null, tick: Int = 0, onClick: (Morph) -> Unit
) {
    val pal = LocalPal.current
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
    val cs = androidx.compose.ui.graphics.lerp(csA.first, csB.first, mx) to androidx.compose.ui.graphics.lerp(csA.second, csB.second, mx)
    val shA = SH[fromI % 5]
    val shB = SH[toI % 5]
    val sh = FloatArray(4) { shA[it] + (shB[it] - shA[it]) * mx }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val selA by animateFloatAsState(if (selected) 1f else 0f, spring(.6f, 400f), label = "sel")
    val pa by animateFloatAsState(if (pressed && !selected) 1f else 0f, spring(.7f, 300f), label = "pa")
    val sc by animateFloatAsState(if (selected || pressed) .97f else 1f, spring(.55f, 500f), label = "sc")
    fun cr(k: Int): Dp {
        val a = sh[k] + (58f - sh[k]) * pa
        return (a + (84f - a) * selA).dp
    }
    val shape = RoundedCornerShape(cr(0), cr(1), cr(2), cr(3))
    val padV by animateDpAsState(if (selected) 26.dp else if (kind == "t") 22.dp else if (kind == "s") 20.dp else 24.dp, spring(.7f, 400f), label = "pv")
    val padH by animateDpAsState(if (selected) 32.dp else 24.dp, spring(.7f, 400f), label = "ph")
    var rect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val dens = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun morphNow(live: Boolean): Morph {
        val c = coords[0]
        val rc = if (c != null && c.isAttached) c.boundsInWindow() else rect
        return Morph(
            rc.left, rc.top, rc.right, rc.bottom, sh.average().toFloat() * dens, cs.first,
            FloatArray(4) { (if (live) cr(it).value else sh[it]) * dens }
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
        Column(
            Modifier.fillMaxWidth().padding(horizontal = padH, vertical = padV).let { if (kind == "f") it.defaultMinSize(minHeight = 102.dp) else it },
            verticalArrangement = Arrangement.spacedBy(if (kind == "t") 0.dp else if (kind == "s") 6.dp else 10.dp)
        ) {
            T(d.t.ifEmpty { "Tanpa judul" }, if (kind == "s") 24f else 26f, 900, cs.second, Modifier.padding(end = 44.dp), ls = -.015f, lh = 1.05f)
            if (kind != "t") {
                T(
                    d.x.replace(Regex("\\s+"), " ").trim().take(150).ifEmpty { "Kosong" }, 15f, 450, cs.second,
                    maxLines = if (kind == "s") 1 else 3, lh = 1.45f, alpha = .8f
                )
                if (kind == "f") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        T(wordCount(d.x).toString() + " kata", 13f, 700, cs.second, ls = .02f, alpha = .75f)
                        T((if (delTime != 0L) "Dihapus " else "") + fdate(if (delTime != 0L) delTime else d.u), 13f, 700, cs.second, ls = .02f, alpha = .75f)
                    }
                }
            }
        }
        if (d.p) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 24.dp).graphicsLayer { rotationZ = 32f; alpha = .7f * (1f - selA) }) {
                Ic("pin", cs.second, 20.dp)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 28.dp).size(32.dp)
                .graphicsLayer { scaleX = selA; scaleY = selA }.clip(CircleShape).background(pal.p),
            Alignment.Center
        ) { Ic("ok", pal.op, 20.dp) }
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
        val reveal = (Math.abs(off.value) / (20f * dens)).coerceIn(0f, 1f)
        if (reveal > 0f) {
            val r = dir > 0
            Box(
                Modifier.matchParentSize().graphicsLayer { alpha = reveal }.clip(shape).background(if (r) pal.p else pal.ec)
                    .padding(horizontal = 34.dp),
                contentAlignment = if (r) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                val s = if (p >= 1f) 1.25f else .55f + p * .6f
                Box(Modifier.graphicsLayer { scaleX = s; scaleY = s }) {
                    Ic(if (r) "arc" else "del", if (r) pal.op else pal.oec, 32.dp)
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

// ---------- drawer ala Google Keep: panel persegi, baris berbentuk pil ----------
@Composable
fun DrawerItem(icon: String, title: String, selected: Boolean = false, trailing: String? = null, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 18.dp else 32.dp, spring(.6f, 500f), label = "r")
    val bgA by animateFloatAsState(if (selected) 1f else 0f, spring(.8f, 380f), label = "bg")
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(r))
            .background(pal.pc.copy(alpha = bgA))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
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
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        T("Dinduy", 28f, 800, pal.p, Modifier.padding(start = 16.dp, top = 28.dp, bottom = 24.dp), ls = -.01f)
        DrawerItem("bulb", "Catatan", selected = true) { close() }
        Divider()
        DrawerItem("arc", "Arsip", trailing = vm.archived().size.takeIf { it > 0 }?.toString()) { go(Scr.Archive) }
        DrawerItem("del", "Baru saja dihapus", trailing = vm.trashed().size.takeIf { it > 0 }?.toString()) { go(Scr.Trash) }
        Divider()
        DrawerItem("set", "Pengaturan") { go(Scr.Settings) }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- beranda ----------
@Composable
fun HomeScreen(vm: AppVM, nav: Nav, act: Actions, openDrawer: () -> Unit, onSmall: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val sat = satDp()
    val sab = sabDp()
    val dens = LocalDensity.current
    val focusMgr = LocalFocusManager.current
    val state = rememberLazyListState()
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
    val live = vm.sorted(vm.live())
    val pos = remember(live) { live.withIndex().associate { it.value.id to it.index } }

    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.canScrollBackward }.collect { back -> if (!back) onSmall(false) }
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
        val livePanelH = (maxHeight - imeH - (sat + 8.dp) - 10.dp).coerceAtLeast(160.dp)
        // saat menutup, tinggi panel dibekukan agar tidak ikut naik-turun mengikuti keyboard yang menghilang
        val panelHold = remember { arrayOf(livePanelH) }
        if (vm.searchOn) panelHold[0] = livePanelH
        val panelH = panelHold[0]

        // daftar
        val np = live.count { it.p }
        val labels = np > 0 && np < live.size
        LazyColumn(
            state = state, modifier = Modifier.fillMaxSize().nestedScroll(fabConn).graphicsLayer { alpha = 1f - sa },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = sat + 78.dp, bottom = 130.dp + sab),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (live.isEmpty()) {
                item(key = "empty") {
                    T("Ketuk Baru untuk memulai", 17f, 600, pal.osv, Modifier.fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            items(live, key = { it.id }) { d ->
                val i = pos[d.id] ?: 0
                androidx.compose.runtime.DisposableEffect(d.id) { onDispose { vm.cardMorph.remove(d.id) } }
                Column(
                    Modifier.flowItem().makeRoom(vm, d.id, i, { vm.openId?.let { pos[it] } ?: -1 }, hPx, dens.density)
                ) {
                    if (labels && i == 0) T("Disematkan", 13f, 900, pal.p, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp), ls = .08f)
                    if (labels && i == np) T("Lainnya", 13f, 900, pal.p, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp), ls = .08f)
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
                                val s = .92f + .08f * pv.coerceIn(0f, 1f); scaleX = s; scaleY = s
                            },
                            onLong = { if (d.id !in sel) sel.add(d.id) },
                            track = { m -> vm.cardMorph[d.id] = m },
                            onClick = { m ->
                                if (selMode) { if (d.id in sel) sel.remove(d.id) else sel.add(d.id) }
                                else nav.openNote(d.id, m)
                            }
                        )
                    }
                }
            }
        }

        // header lengket: tombol menu, pil pencarian, tombol urut
        Box(Modifier.fillMaxWidth().height(sat + 72.dp).background(pal.s).graphicsLayer { alpha = 1f - sa * 0f })
        Box(
            Modifier.padding(start = 10.dp, top = sat + 10.dp)
                .graphicsLayer { translationX = -84.dp.toPx() * sa; scaleX = 1f - .4f * sa; scaleY = 1f - .4f * sa; alpha = 1f - sa }
        ) { IconBtn("menu", pal.osv, { if (sa < .5f) openDrawer() }) }
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = sat + 10.dp)
                .graphicsLayer { translationX = 84.dp.toPx() * sa; scaleX = 1f - .4f * sa; scaleY = 1f - .4f * sa; alpha = 1f - sa }
        ) { IconBtn("sort", pal.osv, { if (sa < .5f) vm.sheet = Sheet.Sort }) }

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
                            Box { if (vm.query.isEmpty()) T("Cari catatan", 17f, 500, pal.osv); inner() }
                        }
                    )
                    Box(Modifier.graphicsLayer { alpha = sa; scaleX = .4f + .6f * sa; scaleY = .4f + .6f * sa }) {
                        IconBtn("x", pal.p, { vm.searchOn = false }, size = 44.dp, enabled = vm.searchOn)
                    }
                }
                val q = vm.query.trim().lowercase()
                val res = vm.sorted(vm.live().filter { q.isEmpty() || (it.t + " " + it.x).lowercase().contains(q) })
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = sa; translationY = (1f - sa) * 14.dp.toPx() },
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (res.isEmpty() && vm.searchOn) item(key = "empty-search") {
                        T(if (q.isNotEmpty()) "Tidak ada hasil" else "Belum ada catatan", 17f, 600, pal.osv,
                            Modifier.flowItem().fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    // indeks warna/bentuk memakai posisi di daftar penuh, jadi kartu tidak berganti warna saat tersaring;
                    // kartu yang cocok bergeser mulus, yang tidak cocok memudar keluar
                    items(res, key = { "s" + it.id }) { d ->
                        val ri = res.indexOf(d)
                        NoteCard(
                            d, pos[d.id] ?: ri, vm.pr.cd, false,
                            Modifier.flowItem().makeRoom(vm, d.id, ri, { res.indexOfFirst { it.id == vm.openId } }, hPx, dens.density)
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
                    if (!hide && b.width > 0f) vm.fabMorph = Morph(b.left, b.top, b.right, b.bottom, 26f * dens, pal.pc)
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
            Modifier.fillMaxWidth().background(pal.pc).padding(top = sat + 8.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("x", pal.opc, { vm.sel.clear() }, size = 46.dp)
            T("$count dipilih", 18f, 900, pal.opc, Modifier.weight(1f).padding(start = 6.dp))
            IconBtn("all", pal.opc, { vm.live().forEach { if (it.id !in vm.sel) vm.sel.add(it.id) } }, size = 46.dp)
            IconBtn("pin", pal.opc, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) {
                    val all = ids.all { vm.note(it)?.p == true }
                    vm.sel.clear()
                    vm.mark(ids, if (all) "Sematan dilepas" else "${ids.size} catatan disematkan") { it.copy(p = !all) }
                }
            }, size = 46.dp)
            IconBtn("arc", pal.opc, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) { vm.sel.clear(); vm.mark(ids, "${ids.size} catatan diarsipkan") { it.copy(a = true, p = false) } }
            }, size = 46.dp)
            IconBtn("del", pal.opc, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) { vm.sel.clear(); vm.mark(ids, "${ids.size} catatan dipindah ke sampah") { it.copy(dt = System.currentTimeMillis()) } }
            }, size = 46.dp)
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
                T(empty, 17f, 600, pal.osv, Modifier.flowItem().fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
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
                    Modifier.flowItem()
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

// ---------- pengaturan ----------
@Composable
fun BigSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val pal = LocalPal.current
    var w by remember { mutableStateOf(1f) }
    val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
    fun at(x: Float) = onChange((min + (x / w).coerceIn(0f, 1f) * (max - min)).let { Math.round(it).toFloat().coerceIn(min, max) })
    Box(
        Modifier.fillMaxWidth().height(48.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(min, max) { detectTapGestures { at(it.x) } }
            .pointerInput(min, max) { detectDragGestures(onDragStart = { at(it.x) }) { ch, _ -> ch.consume(); at(ch.position.x) } }
            .drawBehind {
                val th = 20.dp.toPx()
                val y = (size.height - th) / 2
                drawRoundRect(pal.c4, Offset(0f, y), Size(size.width, th), androidx.compose.ui.geometry.CornerRadius(th / 2))
                drawRoundRect(pal.p, Offset(0f, y), Size(size.width * f, th), androidx.compose.ui.geometry.CornerRadius(th / 2))
                val tx = (size.width * f).coerceIn(3.dp.toPx(), size.width - 3.dp.toPx())
                drawRoundRect(pal.c2, Offset(tx - 7.dp.toPx(), 0f), Size(14.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
                drawRoundRect(pal.p, Offset(tx - 3.dp.toPx(), 0f), Size(6.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
    )
}

@Composable
fun Seg(n: Int, sel: Int, onSel: (Int) -> Unit, item: @Composable (Int, Boolean) -> Unit) {
    val pal = LocalPal.current
    val x by animateFloatAsState(sel.toFloat(), spring(.6f, 350f), label = "seg")
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(pal.c4).padding(6.dp)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(1f / n).graphicsLayer { translationX = x * size.width }
                .clip(RoundedCornerShape(26.dp)).background(pal.p)
        )
        Row(Modifier.fillMaxSize()) {
            for (i in 0 until n) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).clickable { onSel(i) },
                    Alignment.Center
                ) { item(i, i == sel) }
            }
        }
    }
}

@Composable
fun Section(title: String, wait: Int, content: @Composable () -> Unit) {
    val pal = LocalPal.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(wait.toLong()); a.animateTo(1f, spring(.7f, 350f)) }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp)
            .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 32.dp.toPx() }
            .clip(RoundedCornerShape(36.dp)).background(pal.c2).padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        T(title.uppercase(), 15f, 900, pal.p, Modifier.padding(start = 4.dp, bottom = 18.dp), ls = .08f)
        content()
    }
}

@Composable
fun SettingsScreen(vm: AppVM, act: Actions, onBack: () -> Unit) {
    val pal = LocalPal.current
    val sat = satDp()
    val sab = sabDp()
    val pr = vm.pr
    var custom by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(pal.s).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(top = sat + 8.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)) {
            IconBtn("back", pal.os, onBack)
        }
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(start = 20.dp, end = 20.dp, bottom = 40.dp + sab)
        ) {
            T("Atur", 64f, 1000, pal.p, Modifier.padding(start = 4.dp, bottom = 28.dp), ls = -.02f, lh = .95f)
            Section("Mode", 0) {
                val modes = listOf("light" to "lm", "tonal" to "pal", "dark" to "dm", "amoled" to "am")
                Seg(4, modes.indexOfFirst { it.first == pr.mode }.coerceAtLeast(0), { vm.setPr(pr.copy(mode = modes[it].first)) }) { i, a ->
                    Ic(modes[i].second, if (a) pal.op else pal.os, 26.dp)
                }
            }
            Section("Warna aplikasi", 60) {
                val seeds = listOf("#6750A4", "#0B57D0", "#00796B", "#2E7D32", "#C77700", "#B3261E", "#C2185B", "#5D4037")
                val all: List<String?> = seeds + listOf<String?>(null)
                all.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (k in 0 until 4) {
                            val c = row.getOrNull(k)
                            Box(Modifier.weight(1f).aspectRatio(1f)) {
                                if (k < row.size) {
                                    if (c != null) {
                                        val act0 = c.equals(pr.seed, true)
                                        val rp by animateFloatAsState(if (act0) 28f else 50f, spring(.6f, 400f), label = "rp")
                                        val ck by animateFloatAsState(if (act0) 1f else 0f, spring(.6f, 400f), label = "ck")
                                        Box(
                                            Modifier.fillMaxSize().clip(RoundedCornerShape(rp.toInt())).background(Color(android.graphics.Color.parseColor(c)))
                                                .clickable { vm.setPr(pr.copy(seed = c)) },
                                            Alignment.Center
                                        ) { Box(Modifier.graphicsLayer { scaleX = ck; scaleY = ck }) { Ic("ok", Color.White) } }
                                    } else {
                                        Box(
                                            Modifier.fillMaxSize().clip(CircleShape).background(
                                                androidx.compose.ui.graphics.Brush.sweepGradient(
                                                    listOf(Color(0xFFFF4444), Color(0xFFFFBB33), Color(0xFF44DD66), Color(0xFF33CCFF), Color(0xFF7744FF), Color(0xFFFF44AA), Color(0xFFFF4444))
                                                )
                                            ).clickable { custom = true },
                                            Alignment.Center
                                        ) {
                                            Box(Modifier.size(34.dp).clip(CircleShape).background(pal.c4), Alignment.Center) { Ic("plus", pal.os, 20.dp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Section("Kartu catatan", 120) {
                val kinds = listOf("t" to "Judul", "s" to "Ringkas", "f" to "Lengkap")
                Seg(3, kinds.indexOfFirst { it.first == pr.cd }.coerceAtLeast(0), { vm.setPr(pr.copy(cd = kinds[it].first)) }) { i, a ->
                    T(kinds[i].second, 16f, 800, if (a) pal.op else pal.os)
                }
                Box(Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.s).padding(14.dp)) {
                    NoteCard(
                        Note(id = "pv", t = "Rencana minggu ini", x = "Selesaikan tinjauan pustaka, kirim proposal ke dosen, lalu rapikan catatan kuliah sebelum Jumat.", u = System.currentTimeMillis()),
                        0, pr.cd, false
                    ) { }
                }
            }
            Section("Tampilan teks", 180) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    T("Ukuran teks", 16f, 700, pal.os)
                    T("${pr.fs} px", 22f, 1000, pal.p)
                }
                BigSlider(pr.fs.toFloat(), 14f, 30f) { vm.setPr(pr.copy(fs = it.toInt())) }
                T("Jarak baris", 16f, 700, pal.os, Modifier.padding(start = 4.dp, top = 10.dp, bottom = 14.dp))
                val lhs = listOf(1.45f to "Rapat", 1.65f to "Normal", 1.95f to "Lega")
                Seg(3, lhs.indexOfFirst { it.first == pr.lh }.coerceAtLeast(1), { vm.setPr(pr.copy(lh = lhs[it].first)) }) { i, a ->
                    T(lhs[i].second, 16f, 800, if (a) pal.op else pal.os)
                }
                Column(Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.s).padding(20.dp)) {
                    T("Pratinjau", pr.fs * 1.4f, 1000, pal.p, lh = 1.05f, ls = -.02f)
                    Spacer(Modifier.height((pr.fs * .35f).dp))
                    T("Dinduy menjaga catatan tetap rapi, dengan huruf tebal dan jarak yang nyaman untuk mata.", pr.fs.toFloat(), 400, pal.os, lh = pr.lh)
                }
            }
            Section("Jenis huruf", 210) {
                val am = LocalContext.current.assets
                listOf("def" to "Bawaan", "flex" to "Roboto Flex", "times" to "Times New Roman").forEach { (id, name) ->
                    val fam = remember(id) { loadFont(am, id) }
                    FontRow(name, fam, pr.ff == id) { vm.setPr(pr.copy(ff = id)) }
                }
            }
            Section("Cadangan", 240) {
                val n = vm.docs.size
                val ni = vm.imgIds().size
                val old = vm.lastBk != 0L && System.currentTimeMillis() - vm.lastBk > 7 * 86400000L
                val info = "$n catatan, $ni foto. " + (if (vm.lastBk != 0L) {
                    "Cadangan terakhir: " + SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id", "ID")).format(Date(vm.lastBk)) +
                        (if (old) ". Sudah lebih dari seminggu, cadangkan lagi." else ".")
                } else "Belum pernah dicadangkan.") + " Berkas cadangan disimpan di perangkat, tanpa server."
                T(info, 14f, 400, pal.osv, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp), lh = 1.5f)
                Opt(1, "dl", "Simpan cadangan", "Satu berkas .dinduy berisi catatan dan foto") { act.saveBackup() }
                Opt(2, "up", "Bagikan cadangan", "Kirim lewat WhatsApp, Berkas, dan lainnya") { act.shareBackup() }
                Opt(3, "restore", "Pulihkan cadangan", "Pilih berkas .dinduy dari perangkat (termasuk dari versi web)") { act.restoreBackup() }
            }
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
            title = { T("Warna kustom", 22f, 900, pal.os) },
            text = {
                Column {
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(col))
                    Spacer(Modifier.height(16.dp))
                    T("Corak", 14f, 700, pal.osv)
                    BigSlider(h, 0f, 360f) { h = it }
                    T("Intensitas", 14f, 700, pal.osv)
                    BigSlider(s, 35f, 88f) { s = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setPr(pr.copy(seed = String.format("#%06X", hsl(h, s, 50f).toArgb() and 0xFFFFFF))); custom = false
                }) { T("Pakai", 16f, 900, pal.p) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { T("Batal", 16f, 700, pal.osv) } }
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
                is Sheet.Sort -> {
                    T("Urutkan catatan", 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "pen", "Tanggal diubah", "Yang terakhir diedit di atas", vm.pr.sort == "u") { vm.setPr(vm.pr.copy(sort = "u")); close() }
                    Opt(2, "cal", "Tanggal dibuat", "Yang terbaru dibuat di atas", vm.pr.sort == "c") { vm.setPr(vm.pr.copy(sort = "c")); close() }
                }
                is Sheet.Add -> {
                    T("Tambah catatan", 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "txt", "Tulis catatan", "Mulai dari halaman kosong") {
                        close(); val n = vm.newNote(); nav.openNote(n.id, vm.fabMorph, true)
                    }
                    Opt(2, "up", "Impor berkas", "Berkas .md atau .txt") { close(); act.importMd() }
                }
                is Sheet.Doc -> {
                    val d = vm.note(s.id)
                    T(title(s.id), 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "pin", if (d?.p == true) "Lepas sematan" else "Sematkan") {
                        close(); vm.mark(listOf(s.id), if (d?.p == true) "Sematan dilepas" else "Catatan disematkan") { it.copy(p = !it.p) }
                    }
                    Opt(2, if (d?.a == true) "unarc" else "arc", if (d?.a == true) "Keluarkan dari arsip" else "Arsipkan") {
                        close()
                        if (d?.a == true) vm.mark(listOf(s.id), "Dikeluarkan dari arsip") { it.copy(a = false) }
                        else { act.back(); vm.mark(listOf(s.id), "Catatan diarsipkan") { it.copy(a = true, p = false) } }
                    }
                    Opt(3, "dup", "Salin") {
                        close()
                        d?.let { act.copyText((if (it.t.isNotEmpty()) "# " + it.t + "\n\n" else "") + toMd(it.bl)) }
                        vm.show("Disalin ke papan klip")
                    }
                    Opt(4, "lib", "Gandakan") { close(); vm.duplicate(s.id) }
                    Opt(5, "dl", "Unduh sebagai .md") {
                        close()
                        d?.let {
                            act.saveMd(
                                (it.t.replace(Regex("[^\\w\\- ]+"), "").trim().ifEmpty { "catatan" }) + ".md",
                                (if (it.t.isNotEmpty()) "# " + it.t + "\n\n" else "") + toMd(it.bl)
                            )
                        }
                    }
                    Opt(6, "del", "Hapus catatan") {
                        close(); act.back(); vm.mark(listOf(s.id), "Dipindah ke sampah") { it.copy(dt = System.currentTimeMillis()) }
                    }
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

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
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.round
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
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
    fun pickCover(id: String)                // pilih satu foto galeri sebagai sampul catatan
    fun replaceImage(noteId: String, bid: String, ix: Int)   // pilih satu foto dari galeri untuk mengisi bingkai gambar (blok bid, urutan ix) di catatan noteId; ukuran bingkai tetap
    fun takePhoto(replace: Boolean)         // buka kamera (minta izin bila perlu); hasilnya jadi gambar baru, atau mengisi bingkai terpilih bila replace
    fun importMd()
    fun saveBackup()
    fun shareBackup()
    fun restoreBackup()
    fun saveMd(name: String, text: String)
    fun savePdf(name: String, file: java.io.File)    // simpan PDF hasil ekspor lewat pemilih berkas sistem
    fun sharePdf(name: String, file: java.io.File)   // bagikan PDF hasil ekspor lewat lembar bagikan
    fun copyText(t: String)
    fun askNotif()   // minta / arahkan ke izin notifikasi supaya pengingat bisa muncul
    fun requestMic(onGranted: () -> Unit)   // minta izin mikrofon saat dibutuhkan; onGranted dipanggil bila diizinkan
    fun saveAudio(aud: Aud, name: String)   // simpan berkas suara lewat pemilih berkas sistem (tanpa izin penyimpanan)
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

private val FD_ID = Locale.US
private val FD_SHORT = SimpleDateFormat("MMM d", FD_ID)
private val FD_LONG = SimpleDateFormat("MMM d, yyyy", FD_ID)
private val FD_YEAR = SimpleDateFormat("yyyy", Locale.US)

// dipanggil hanya dari thread UI; formatter dibuat sekali (SimpleDateFormat mahal dibuat berulang per kartu)
private fun fdate(u: Long): String {
    val d = Date(u)
    val same = FD_YEAR.format(d) == FD_YEAR.format(Date())
    return (if (same) FD_SHORT else FD_LONG).format(d)
}

private val WS_RX = Regex("\\s+")
private val SP_RX = Regex("[ \\t]+")
private val NL_RX = Regex("\\s*\\n\\s*\\n\\s*(\\n\\s*)*")

/** Kartu catatan ala Google Keep: persegi dengan sudut sedikit membulat, bergaris tipis, tanpa bentuk ekspresif. */
private const val CARD_R = 12f
private val CARD_PAD_H = 16.dp
private val CARD_PAD_V = 14.dp

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

/** Kartu yang sedang diseret: tetap memakai animateItem (supaya pelacak animasi tidak terputus) tetapi tanpa animasi apa pun. */
fun LazyStaggeredGridItemScope.holdItemG(): Modifier = Modifier.animateItem(
    fadeInSpec = null, placementSpec = null, fadeOutSpec = null
)

/**
 * Saat sebuah catatan membuka (morph kartu -> layar penuh), kartu lain tidak ditimpa begitu saja:
 * kartu di atas terdorong ke atas, kartu di bawah terdorong ke bawah mengikuti tepi kartu yang melebar,
 * sedikit mengecil dan memudar. Saat menutup, geraknya berbalik dengan spring yang sama.
 */
fun Modifier.makeRoom(vm: AppVM, id: String, idx: Int, openIdx: () -> Int, h: Float, dens: Float, cols: Int = 1, w: Float = 0f, flat: Boolean = false): Modifier = graphicsLayer {
    val oid = vm.openId
    val m = vm.morph
    val p = vm.openP.value.coerceIn(0f, 1f)
    if (p <= 0.001f || oid == null || m == null || oid == id) return@graphicsLayer
    val oi = openIdx()
    if (oi < 0) return@graphicsLayer
    // beranda (1 atau 2 kolom; w > 0 hanya diisi HomeCard): posisi kartu dibaca langsung, bukan sekadar urutan
    val grid = w > 0f && !vm.searchOn
    // morph dari kartu "Tambah catatan" (lebar penuh) atau menguncup ke tombol Baru tidak boleh menggeser kartu lain
    if (grid && (!m.card || m.up || vm.note(oid) == null)) return@graphicsLayer
    val wave = minOf(Math.abs(idx - oi), 5) * 10f * dens
    // posisi hasil boundsInWindow terpotong layar: kartu yang sudah terdorong keluar layar bisa tercatat sebagai Rect kosong (0,0,0,0)
    // lalu dianggap 'di atas' kartu yang dibuka -> melompat ke atas sesaat. Rect tak valid diperlakukan seperti belum ada.
    val me = if (grid) vm.cardMorph[id]?.takeIf { it.r - it.l > 1f && it.b - it.t > 1f } else null
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
    // kartu bersampul tidak ikut mengecil: skala yang berubah tiap frame membuat Skia berpindah level mipmap bitmap sampul
    // (FilterQuality.Medium) -> gambar tampak 'melompat' / berkedip, terutama pada kartu yang separuh terpotong di tepi layar
    if (!flat) {
        val s = 1f - .05f * p
        scaleX = s; scaleY = s
    }
    alpha = 1f - .6f * p
    // kartu bersampul: alpha<1 memaksa layer offscreen untuk bitmap besar -> kedip saat kartu terpotong tepi layar; ModulateAlpha menghindarinya
    if (flat) compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
}

@Composable
fun NoteCard(
    d: Note, i: Int, kind: String, selected: Boolean, modifier: Modifier = Modifier,
    delTime: Long = 0L, onLong: (() -> Unit)? = null, track: ((Morph) -> Unit)? = null, tick: Int = 0, cols: Int = 1,
    paper: Boolean = false, onClick: (Morph) -> Unit
) {
    // catatan bersampul: kartu memakai palet dari warna foto sampulnya (terpisah dari palet aplikasi)
    val gp = LocalPal.current
    val pal = if (d.cp.isEmpty() || d.cv.isEmpty()) gp else remember(d.cp, gp.dark, gp.mono) { coverPal(gp.dark, d.cp, gp.mono) }
    val cvm = LocalVM.current
    LaunchedEffect(d.id, d.cv, d.cp) { if (d.cv.isNotEmpty() && d.cp.isEmpty()) cvm?.ensureCoverPal(d.id) }
    val c2 = cols == 2
    // semua kartu satu warna (kertas) dengan garis tepi tipis; saat dipilih garisnya menebal sedikit dan berwarna utama
    val cardC = pal.s
    val fg = pal.os
    val edge = androidx.compose.ui.graphics.lerp(pal.bg, pal.os, .13f)
    val shape = RoundedCornerShape(CARD_R.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val selA by animateFloatAsState(if (selected) 1f else 0f, spring(.6f, 400f), label = "sel")
    val sc by animateFloatAsState(if (selected || pressed) .97f else 1f, spring(.55f, 500f), label = "sc")
    var rect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val dens = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun morphNow(live: Boolean): Morph {
        val c = coords[0]
        val rc = if (c != null && c.isAttached) c.boundsInWindow() else rect
        return Morph(
            rc.left, rc.top, rc.right, rc.bottom, CARD_R * dens, cardC,
            FloatArray(4) { CARD_R * dens },
            Ghost("f", c2, fg, CARD_PAD_H, CARD_PAD_V, 0.dp, 0.dp), card = true
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
            .fillMaxWidth().clip(shape).background(cardC)
            .border((1f + selA).dp, androidx.compose.ui.graphics.lerp(edge, pal.p, selA), shape)
            .combinedClickable(
                interactionSource = src, indication = LocalIndication.current,
                onClick = { onClick(morphNow(true)) },
                onLongClick = if (onLong != null) ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong() }) else null
            )
    ) {
        CompositionLocalProvider(LocalPal provides pal) {
            CardBody(d, "f", c2, fg, CARD_PAD_H, CARD_PAD_V, 0.dp, 0.dp, delTime)
        }
    }
}

/**
 * Isi teks kartu ala Keep: judul (bila ada), isi dengan tinggi mengikuti panjang teks (dibatasi supaya tidak terlalu tinggi),
 * lalu label / pengingat. Dipakai kartu asli dan bayangan teks saat morph buka/tutup.
 */
@Composable
fun CardBody(d: Note, kind: String, c2: Boolean, fg: Color, padH: Dp, padV: Dp, insTL: Dp, insBL: Dp, delTime: Long, drawCover: Boolean = true) {
    val vm = LocalVM.current
    if (d.cv.isEmpty() || vm == null) {
        CardText(d, kind, c2, fg, padH, padV, insTL, insBL, delTime)
        return
    }
    // sampul di atas, tepi ke tepi kartu; teks (bila ada) di bawahnya
    val hasText = d.t.isNotBlank() || d.x.isNotBlank() || d.au.isNotEmpty() || (d.rm > 0L && d.dt == 0L) || d.lb.isNotEmpty() || delTime != 0L
    Column(Modifier.fillMaxWidth()) {
        // drawCover = false (bayangan morph): ruangnya tetap ada supaya teks berada di tempat yang sama, tapi gambarnya tidak digambar
        // dua kali; sampul di editor sudah menimpanya dengan buram penuh (lihat EditorCover), jadi tidak ada pudar-silang yang kedip.
        if (drawCover) CoverImage(vm, d, Modifier.fillMaxWidth().aspectRatio(coverAspect(c2)))
        else Spacer(Modifier.fillMaxWidth().aspectRatio(coverAspect(c2)))
        if (hasText) CardText(d, kind, c2, fg, padH, padV, insTL, insBL, delTime)
    }
}

@Composable
private fun CardText(d: Note, kind: String, c2: Boolean, fg: Color, padH: Dp, padV: Dp, insTL: Dp, insBL: Dp, delTime: Long) {
    val remOn = d.rm > 0L && d.dt == 0L
    val body = remember(d.x) { d.x.take(900).replace(SP_RX, " ").replace(NL_RX, "\n\n").trim() }
    val hasTitle = d.t.isNotBlank()
    val shownBody = body.ifEmpty { if (!hasTitle) (if (d.au.isNotEmpty()) "Voice note" else if (d.cv.isNotEmpty()) "" else "Empty") else "" }
    // kartu bersampul: sampul tetap utuh, yang dipendekkan hanya kotak tulisannya (padding, jarak, dan jumlah baris)
    val cvd = d.cv.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = padH, vertical = if (cvd) padV * .72f else padV),
        verticalArrangement = Arrangement.spacedBy(if (cvd) 5.dp else 8.dp)
    ) {
        if (hasTitle) {
            T(d.t.trim(), 17f, 500, fg, Modifier.padding(end = if (d.p) 18.dp else 0.dp), lh = 1.25f, maxLines = if (cvd) 2 else 3)
        }
        if (shownBody.isNotEmpty()) {
            T(shownBody, 15f, 400, fg, lh = 1.4f, maxLines = if (cvd) (if (c2) 4 else 3) else (if (c2) 10 else 6), alpha = .88f)
        }
        if (remOn) {
            Box { RemChip(Rem.stamp(d), fg, Rem.overdue(d), d.rr.isNotEmpty()) }
        }
        if (d.lb.isNotEmpty()) {
            val maxShow = if (c2) 1 else 3
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                d.lb.take(maxShow).forEach { n -> KeepChip(n, fg, Modifier.weight(1f, fill = false)) }
                if (d.lb.size > maxShow) KeepChip("+" + (d.lb.size - maxShow), fg)
            }
        }
        if (delTime != 0L) {
            // hanya di sampah: tanggal penghapusan
            T("Deleted " + fdate(delTime), 12f, 600, fg, alpha = .65f)
        }
    }
}

/** Bayangan kartu: teks kartu yang pudar-silang dengan isi catatan selama morph, supaya teks tidak kedip. */
@Composable
fun GhostCard(d: Note, g: Ghost, modifier: Modifier = Modifier) {
    Box(modifier) {
        // tanpa ikon pin: kartu asli di beranda juga tidak menampilkannya, jadi tidak ada ikon yang berkedip saat morph
        CardBody(d, g.kind, g.c2, g.fg, g.padH, g.padV, g.insTL, g.insBL, 0L, drawCover = false)
    }
}

/** Label di kartu ala Keep: kotak membulat kecil, bukan kapsul. */
@Composable
fun KeepChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = .10f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        T(text, 12f, 500, color, maxLines = 1, alpha = .85f)
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
    var winL by remember { mutableStateOf(0f) }
    var winR by remember { mutableStateOf(0f) }
    val scrW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * androidx.compose.ui.platform.LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    var hit by remember { mutableStateOf(false) }
    var dir by remember { mutableStateOf(1) }
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val p = (Math.abs(off.value) / (w * .34f)).coerceIn(0f, 1f)
    val errC = (if (pal.dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)).let { if (pal.mono) it.gray() else it }
    val pop = remember { Animatable(0f) }
    val wig = remember { Animatable(1f) }
    // pegas kurang teredam: melompat melewati nilai akhir lalu berosilasi mengendap = efek "pop" dan goyangan
    LaunchedEffect(hit) { pop.animateTo(if (hit) 1f else 0f, spring(.4f, 450f)) }
    LaunchedEffect(hit) { if (hit) { wig.snapTo(0f); wig.animateTo(1f, spring(.22f, 240f)) } else wig.snapTo(1f) }
    Box(
        Modifier.fillMaxWidth().onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .onGloballyPositioned { val b = it.boundsInWindow(); winL = b.left; winR = b.right }
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
                            // buang sampai benar-benar keluar layar (kartu kolom kanan ke kiri / kolom kiri ke kanan harus menyeberang penuh)
                            val m = 24f * dens
                            val tgt = if (right) maxOf(w * 1.1f, scrW - winL + m) else -maxOf(w * 1.1f, winR + m)
                            off.animateTo(tgt, tween(280, easing = Emph))
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
                        if (r) "Archive" else "Delete", 12f, 800, tint,
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
                T("Neat notes, fast and cozy 0123", 14f, 400, fg, alpha = .75f)
            }
            if (on) Ic("ok", fg, 24.dp)
        }
    }
}

/** Pilihan "Follow device" (Material You): warna aplikasi mengikuti wallpaper. */
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
            T("Follow device", 19f, 800, fg)
            T("Material You, colors follow your wallpaper", 14f, 400, fg, alpha = .75f)
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
            DrawerItem("bulb", "Notes", selected = vm.filter == null && !vm.remFilter) { vm.filter = null; vm.remFilter = false; close() }
            DrawerItem(
                "bell", "Reminder", selected = vm.remFilter,
                trailing = vm.live().count { it.rm > 0L }.takeIf { it > 0 }?.toString()
            ) { vm.filter = null; vm.remFilter = true; close() }
            T("Labels", 14f, 700, pal.osv, Modifier.padding(start = 26.dp, top = 16.dp, bottom = 6.dp))
            vm.sortedLabels().forEach { n ->
                DrawerItem(
                    "label", n, selected = vm.filter == n,
                    trailing = vm.labelCount(n).takeIf { it > 0 }?.toString(),
                    onLong = { vm.sheet = Sheet.LblEdit(n) }
                ) { vm.remFilter = false; vm.filter = n; close() }
            }
            DrawerItem("plus", "Create new label") { close(); vm.lblDlg = LblDlg(null, null) }
            Divider()
            DrawerItem("up", "Import files") { close(); vm.importTick++ }
            DrawerItem("arc", "Archive", trailing = vm.archived().size.takeIf { it > 0 }?.toString()) { go(Scr.Archive) }
            DrawerItem("del", "Trash", trailing = vm.trashed().size.takeIf { it > 0 }?.toString()) { go(Scr.Trash) }
            Spacer(Modifier.height(12.dp))
        }
        Divider()
        DrawerItem("i", "Info") { go(Scr.Info) }
        DrawerItem("set", "Settings") { go(Scr.Settings) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun LabelDialogHost(vm: AppVM) {
    val d = vm.lblDlg ?: return
    val pal = LocalPal.current
    val renaming = d.old != null
    var text by remember(d) { mutableStateOf(d.old ?: "") }
    var focused by remember(d) { mutableStateOf(false) }
    var err by remember(d) { mutableStateOf(false) }
    var closing by remember(d) { mutableStateOf(false) }
    val fr = remember(d) { FocusRequester() }
    val scope = rememberCoroutineScope()
    val prog = remember(d) { Animatable(0f) }      // 0 -> 1: kartu masuk (pegas, sedikit membal)
    val iconP = remember(d) { Animatable(0f) }     // lencana ikon masuk menyusul sesaat kemudian

    // keluar: kartu mengecil + memudar dulu, baru dialog benar-benar dilepas
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                prog.animateTo(0f, androidx.compose.animation.core.tween(190, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                vm.lblDlg = null
            }
        }
    }
    val done: () -> Unit = {
        if (!closing && text.isNotBlank()) {
            val old = d.old
            val ok = if (old != null) vm.renameLabel(old, text) else vm.addLabel(text, d.forNote)
            if (ok) close() else err = true
        }
    }
    LaunchedEffect(d) {
        launch { prog.animateTo(1f, spring(.74f, 320f)) }
        launch { delay(110); iconP.animateTo(1f, spring(.42f, 340f)) }
        delay(260)
        try { fr.requestFocus() } catch (e: Exception) { }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // scrim digambar sendiri (ikut animasi), jadi redup bawaan jendela dimatikan
        val win = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect { win?.setDimAmount(0f); win?.setWindowAnimations(-1) }

        Box(
            Modifier.fillMaxSize()
                .labelScrim(prog)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
                .padding(horizontal = 24.dp),
            Alignment.Center
        ) {
            val cardShape = RoundedCornerShape(36.dp)
            Column(
                Modifier.widthIn(max = 380.dp).fillMaxWidth()
                    .graphicsLayer {
                        val p = prog.value
                        val sc = .84f + .16f * p
                        scaleX = sc; scaleY = sc
                        alpha = (p * 2.2f).coerceIn(0f, 1f)
                        translationY = (1f - p) * 36.dp.toPx()
                    }
                    .clip(cardShape).background(pal.c3)
                    .pointerInput(Unit) { detectTapGestures { } }       // ketuk di kartu tidak menutup
                    .padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // lencana ikon: kotak membulat besar, berputar sedikit sambil membal masuk
                Box(
                    Modifier.size(68.dp)
                        .graphicsLayer {
                            val k = iconP.value
                            scaleX = k; scaleY = k; rotationZ = (1f - k) * -28f
                        }
                        .clip(RoundedCornerShape(24.dp)).background(pal.pc),
                    Alignment.Center
                ) { Ic("label", pal.opc, 32.dp) }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), Alignment.Center) { T(if (renaming) "Rename label" else "New label", 26f, 900, pal.os) }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    T(if (renaming) "Notes keep this label" else if (d.forNote != null) "It will be added to this note" else "Use labels to group notes", 14f, 500, pal.osv, maxLines = 1)
                }
                Spacer(Modifier.height(22.dp))

                // kolom isian: kapsul tonal, bingkai primer menyala saat fokus
                val frame by animateColorAsState(
                    if (err) pal.oec else if (focused) pal.p else Color.Transparent,
                    androidx.compose.animation.core.tween(180), label = "lfr"
                )
                val tint by animateColorAsState(if (focused) pal.p else pal.osv, androidx.compose.animation.core.tween(180), label = "lti")
                val clearK by animateFloatAsState(if (text.isNotEmpty()) 1f else 0f, spring(.6f, 520f), label = "lcl")
                val fieldShape = RoundedCornerShape(28.dp)
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clip(fieldShape).background(pal.c4)
                        .border(2.dp, frame, fieldShape).padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ic("label", tint, 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = text, onValueChange = { text = it.take(30); err = false },
                            modifier = Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { focused = it.isFocused },
                            textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 18.sp, fontWeight = FontWeight(600)),
                            singleLine = true, cursorBrush = SolidColor(pal.p),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { done() }),
                            decorationBox = { inner -> Box { if (text.isEmpty()) T("Label name", 18f, 600, pal.osv, maxLines = 1); inner() } }
                        )
                    }
                    Box(
                        Modifier.size(40.dp).graphicsLayer { scaleX = clearK; scaleY = clearK; alpha = clearK }
                            .clip(CircleShape)
                            .clickable(enabled = text.isNotEmpty()) { text = ""; err = false; try { fr.requestFocus() } catch (e: Exception) { } },
                        Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
                            val sw = 2.2.dp.toPx()
                            drawLine(pal.osv, Offset(0f, 0f), Offset(size.width, size.height), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                            drawLine(pal.osv, Offset(size.width, 0f), Offset(0f, size.height), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (err) T("This label already exists", 13f, 600, pal.oec, maxLines = 1)
                    }
                    T("${text.length}/30", 13f, 600, if (text.length >= 26) pal.p else pal.osv)
                }
                Spacer(Modifier.height(18.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabelDlgBtn("Cancel", pal.c4, pal.os, true, Modifier.weight(1f)) { close() }
                    LabelDlgBtn(if (renaming) "Save" else "Create", pal.p, pal.op, text.isNotBlank(), Modifier.weight(1f)) { done() }
                }
            }
        }
    }
}

/** Scrim yang ikut progres animasi kartu (dibaca di fase gambar, jadi tidak memicu recompose tiap frame). */
private fun Modifier.labelScrim(p: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>): Modifier =
    this.drawBehind { drawRect(Color.Black.copy(alpha = .52f * p.value.coerceIn(0f, 1f))) }

/** Tombol dialog ala M3 Expressive: kapsul tinggi, sudut menyusut dan badan mengecil saat ditekan. */
@Composable
private fun LabelDlgBtn(text: String, bg: Color, fg: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 16.dp else 26.dp, spring(.6f, 520f), label = "lbr")
    val sc by animateFloatAsState(if (pr) .95f else 1f, spring(.5f, 600f), label = "lbs")
    val al by animateFloatAsState(if (enabled) 1f else .38f, androidx.compose.animation.core.tween(160), label = "lba")
    Box(
        modifier.height(52.dp).graphicsLayer { scaleX = sc; scaleY = sc; alpha = al }
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        Alignment.Center
    ) { T(text, 16f, 800, fg, maxLines = 1) }
}

// ---------- beranda ----------
/**
 * Keadaan seret-dan-lepas kartu beranda (ala Google Keep). Selama menyeret, urutan satu kelompok (disematkan / lainnya)
 * disimpan sementara di `order`; kartu lain bergeser dengan animasi karena grid mengikuti urutan itu.
 * Urutan baru baru disimpan permanen (AppVM.moveOrder) saat kartu dilepas.
 */
@androidx.compose.runtime.Stable
class NoteDrag {
    var id by mutableStateOf<String?>(null)
    var group = 0                                   // 0 = kelompok atas (disematkan / semua), 1 = lainnya
    var order by mutableStateOf<List<String>>(emptyList())
    var orig: List<String> = emptyList()
    var finger by mutableStateOf(Offset.Zero)       // posisi jari di dalam grid
    var grab = Offset.Zero                          // titik sentuh relatif terhadap pojok kiri-atas kartu
    var dropping by mutableStateOf(false)
    var ready by mutableStateOf(false)              // tata letak baru sudah terpasang; bayangan boleh meluncur ke slotnya
    var h = 0                                       // tinggi kartu seretan (px)
    var span = 0f                                   // tinggi kartu seretan + jarak antar kartu (px): jarak geser kartu lain di 1 kolom
    val lift = Animatable(0f)                       // 0 -> 1: kartu terangkat (bayangan & skala muncul perlahan)
    var resid = Offset.Zero                         // jarak kartu dari tempat barunya saat dilepas
    var touchId: String? = null                     // kartu yang baru saja tersentuh (dicatat oleh kartu itu sendiri, jadi pasti benar)
    var touchT = -1L                                // waktu sentuhan tadi, untuk mencocokkan dengan gestur di grid
    var touchLocal = Offset.Zero                    // titik sentuh tadi di dalam kartu (koordinat lokal kartu itu)
    // kartu bayangan (yang mengikuti jari) digambar di atas grid dengan koordinat dari jari saja, tidak dari layout grid,
    // jadi tidak pernah ketinggalan satu frame / meloncat saat urutan bertukar
    var startC = Offset.Zero                        // pojok kiri-atas kartu di dalam grid saat seretan dimulai
    var at0 = Offset.Zero                           // posisi jari saat seretan dimulai
    var off0 = Offset.Zero                          // offset item di layoutInfo saat seretan dimulai
    var w = 0                                       // lebar kartu (px)
    var dropAt = Offset.Zero                        // posisi bayangan saat dilepas
    val settle = Animatable(0f)                     // 1 -> 0: kartu meluncur ke tempatnya
    // 2 kolom: kolom (0 kiri / 1 kanan) tiap kartu di kelompok yang diseret, sementara selama seretan (disimpan permanen saat dilepas)
    var laneOv by mutableStateOf<Map<String, Int>>(emptyMap())

    fun begin(key: String, g: Int, ord: List<String>, grabAt: Offset, at: Offset) {
        group = g; orig = ord; order = ord; grab = grabAt; finger = at; dropping = false; ready = false; id = key
    }

    fun reset() { id = null; dropping = false; ready = false; order = emptyList(); laneOv = emptyMap() }

    /**
     * 1 kolom: tata letak grid TIDAK berubah selama menyeret (kartu tidak benar-benar dipindah di daftar), jadi tidak ada
     * lompatan gulir / pertukaran beruntun. Kartu di antara asal dan tujuan hanya digeser secara visual sejauh `span`.
     */
    fun shiftOf(key: String): Float {
        val d = id ?: return 0f
        if (key == d) return 0f
        val o = orig.indexOf(d)
        val t = order.indexOf(d)
        val k = orig.indexOf(key)
        if (o < 0 || t < 0 || k < 0 || o == t) return 0f
        return when {
            t > o && k > o && k <= t -> -span
            t < o && k >= t && k < o -> span
            else -> 0f
        }
    }
}

/**
 * Pergeseran tumpukan kartu saat pencarian dibuka/ditutup.
 * from = posisi tiap kartu (id -> offset di layar) di daftar ASAL: beranda saat membuka, hasil cari saat menutup. Tiap kartu meluncur
 * dari posisi asalnya ke posisi barunya, jadi gerak tetap terlihat walau beranda mentok di atas tetapi tata letaknya berubah
 * (judul "Pinned"/"Others" hilang, kartu pindah kolom). Kalau semua posisi sama (tidak ada yang berubah), tidak ada gerak.
 * px = cadangan untuk kartu yang tidak ada di daftar asal (beranda tergulir: jarak gulir, 0 = mentok di atas), p = progres 0..1.
 */
private class StackShift(
    val px: Float, val from: Map<String, Offset>,
    val p: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>
)


/** Satu blok isi beranda 2 kolom: judul ("Pinned" / "Others" / "empty") atau satu kelompok kartu. */
private class HBlock(val head: String?, val ids: List<String>)

/**
 * Kartu meluncur mulus ke posisi barunya (pengganti animateItem, yang hanya ada di daftar lazy).
 * Posisi diukur di induk yang sama untuk semua kartu, jadi kartu yang pindah kolom pun meluncur lintas kolom.
 */
private fun Modifier.slideTo(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    val holder = remember { arrayOfNulls<Animatable<IntOffset, androidx.compose.animation.core.AnimationVector2D>>(1) }
    this.onPlaced { c ->
        val t = c.positionInParent().round()
        // penempatan pertama: langsung di tempatnya (tanpa meluncur dari pojok)
        if (holder[0] == null) holder[0] = Animatable(t, IntOffset.VectorConverter)
        target = t
    }.offset {
        val a = holder[0]
        if (a == null) IntOffset.Zero else {
            if (a.targetValue != target) scope.launch { a.animateTo(target, PlaceSpec) }
            a.value - target
        }
    }
}

/**
 * Tata letak beranda 2 kolom dengan kolom EKSPLISIT: tiap kartu punya kolomnya sendiri (laneOf), urutan di dalam kolom mengikuti
 * urutan daftar. Kartu yang belum punya kolom masuk ke kolom yang paling pendek (seperti grid staggered). Karena kolom tetap,
 * menukar dua kartu hanya mengubah dua kartu itu; kartu lain tetap di kolomnya (hanya menyesuaikan tinggi).
 * Posisi hasil ukur ditulis ke `rects` (id -> [x, y, lebar, tinggi] dalam isi yang tergulir), kolom yang dipakai ke `lanesOut`,
 * dan kolom yang baru dihitung (belum tersimpan) ke `fresh`.
 */
@Composable
private fun TwoColLayout(
    blocks: List<HBlock>, laneOf: (String) -> Int?, dragId: String?,
    padS: Int, padTop: Int, padBottom: Int, gap: Int,
    rects: HashMap<String, IntArray>, lanesOut: HashMap<String, Int>, fresh: HashMap<String, Int>,
    modifier: Modifier = Modifier, content: @Composable (String) -> Unit
) {
    val flatKeys = ArrayList<String>().also { l -> for (b in blocks) { val h = b.head; if (h != null) l.add(h) else l.addAll(b.ids) } }
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = {
            // satu daftar datar ber-key: kartu yang pindah blok (mis. di-pin) tetap instance yang sama, jadi slideTo bisa
            // menganimasikannya dari posisi lama ke posisi baru (sebelumnya kartu dibuang & dibuat ulang = kedip)
            for (k in flatKeys) {
                androidx.compose.runtime.key(k) {
                    Box(
                        Modifier.layoutId(k).then(if (k == dragId) Modifier else Modifier.slideTo()),
                        propagateMinConstraints = true
                    ) { content(k) }
                }
            }
        }
    ) { ms, c ->
        val w = c.maxWidth
        val byId = HashMap<String, androidx.compose.ui.layout.Measurable>(ms.size * 2)
        for (m in ms) { val k = m.layoutId as? String ?: continue; byId[k] = m }
        val colW = ((w - 2 * padS - gap) / 2).coerceAtLeast(0)
        rects.clear(); lanesOut.clear(); fresh.clear()
        val placed = ArrayList<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>(ms.size)
        var y = padTop
        for (b in blocks) {
            val h = b.head
            if (h != null) {
                val m = byId[h] ?: continue
                val pl = m.measure(androidx.compose.ui.unit.Constraints.fixedWidth((w - 2 * padS).coerceAtLeast(0)))
                placed.add(Triple(pl, padS, y))
                rects[h] = intArrayOf(padS, y, pl.width, pl.height)
                y += pl.height + gap
            } else {
                val items = ArrayList<Pair<String, androidx.compose.ui.layout.Placeable>>(b.ids.size)
                for (id in b.ids) {
                    val m = byId[id] ?: continue
                    items.add(id to m.measure(androidx.compose.ui.unit.Constraints.fixedWidth(colW)))
                }
                val n = items.size
                val saved = IntArray(n) { laneOf(items[it].first)?.takeIf { v -> v == 0 || v == 1 } ?: -1 }
                // kolom tersimpan yang timpang parah (satu kolom hampir kosong) dianggap rusak: dihitung ulang seimbang
                if (dragId == null && n >= 3) {
                    val hs = intArrayOf(0, 0); var cnt = 0
                    for (k in 0 until n) if (saved[k] >= 0) { hs[saved[k]] += items[k].second.height + gap; cnt++ }
                    if (cnt >= 3 && minOf(hs[0], hs[1]) * 5 < maxOf(hs[0], hs[1])) java.util.Arrays.fill(saved, -1)
                }
                // kartu bersimpanan kolom dihitung dulu; kartu baru mengisi kolom yang paling pendek (kosong = paling awal, mulai dari kiri)
                val hh = intArrayOf(0, 0)
                for (k in 0 until n) if (saved[k] >= 0) hh[saved[k]] += items[k].second.height + gap
                val lane = IntArray(n)
                for (k in 0 until n) {
                    if (saved[k] >= 0) { lane[k] = saved[k]; continue }
                    val v = if (hh[0] <= hh[1]) 0 else 1
                    lane[k] = v
                    hh[v] += items[k].second.height + gap
                    fresh[items[k].first] = v
                }
                val ly = intArrayOf(y, y)
                for (k in 0 until n) {
                    val (id, pl) = items[k]
                    val ln = lane[k]
                    lanesOut[id] = ln
                    val x = padS + ln * (colW + gap)
                    placed.add(Triple(pl, x, ly[ln]))
                    rects[id] = intArrayOf(x, ly[ln], pl.width, pl.height)
                    ly[ln] += pl.height + gap
                }
                y = maxOf(ly[0], ly[1])
            }
        }
        val total = maxOf(padTop, y - gap) + padBottom
        layout(w, total) {
            for ((pl, px, py) in placed) pl.place(px, py)
        }
    }
}

@Composable
fun HomeScreen(vm: AppVM, nav: Nav, act: Actions, openDrawer: () -> Unit, onSmall: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val sat = satDp()
    val sab = sabDp()
    val dens = LocalDensity.current
    val focusMgr = LocalFocusManager.current
    val state = rememberLazyStaggeredGridState()
    // state daftar hasil cari diangkat ke sini supaya posisi kartunya bisa dibaca untuk animasi buka/tutup pencarian
    val resState = rememberLazyStaggeredGridState()
    val nd = remember { NoteDrag() }
    // 2 kolom: tata letak kolom eksplisit (bukan LazyVerticalStaggeredGrid), supaya kolom tiap kartu tetap dan seretan = tukar posisi
    val scroll = rememberScrollState()
    val rects = remember { HashMap<String, IntArray>() }       // id -> [x, y, lebar, tinggi] di dalam isi yang tergulir
    val lanesCur = remember { HashMap<String, Int>() }         // id -> kolom yang sedang dipakai tata letak
    val lanesNew = remember { HashMap<String, Int>() }         // kolom yang baru dihitung (kartu belum punya kolom); disimpan setelah tata letak
    val vpH = remember { IntArray(1) }                         // tinggi area tampil
    // tween tanpa overshoot supaya buka/tutup pencarian tidak bergetar
    val saRaw by animateFloatAsState(if (vm.searchOn) 1f else 0f, if (vm.searchOn) spring(.9f, 380f) else spring(.95f, 460f), label = "sa")
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
    // derivedStateOf: hanya dihitung ulang saat catatan / filter / urutan / layar berubah, bukan tiap frame animasi pencarian atau keyboard
    val live by remember(vm) {
        derivedStateOf {
            vm.sorted(vm.liveF()).let { l ->
                // 1 & 2 kolom: catatan kosong yang sedang dibuka disembunyikan dari grid supaya kartu lain tidak bergeser
                l.filterNot { d ->
                    d.t.isBlank() && d.x.isBlank() && d.bl.none { it.isObj && it.ty != "hr" } && d.rm == 0L && d.au.isEmpty() && d.cv.isEmpty() &&
                        vm.openId == d.id && vm.nav.stack.any { s -> s is Scr.Note && s.id == d.id }
                }
            }
        }
    }
    val pos = remember(live) { live.withIndex().associate { it.value.id to it.index } }

    LaunchedEffect(state, scroll, cols) {
        androidx.compose.runtime.snapshotFlow { if (cols == 2) scroll.value > 0 else state.canScrollBackward }.collect { back -> if (!back) onSmall(false) }
    }
    LaunchedEffect(vm.filter, vm.remFilter) {
        try { state.scrollToItem(0) } catch (e: Exception) { }
        try { scroll.scrollTo(0) } catch (e: Exception) { }
    }
    // pindah 1 <-> 2 kolom: mulai dari atas (posisi gulir kedua mode tidak sebanding)
    LaunchedEffect(cols) {
        try { state.scrollToItem(0) } catch (e: Exception) { }
        try { scroll.scrollTo(0) } catch (e: Exception) { }
    }
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
            if (cols == 2) {
                // 2 kolom: semua kartu sudah tersusun, posisinya diketahui; geser seminimal mungkin sampai kartu itu utuh terlihat
                withFrameNanos { }
                val r = rects[id] ?: return@LaunchedEffect
                val topPx2 = with(dens) { (sat + 72.dp).toPx() }
                val botPx2 = vpH[0] - with(dens) { 24.dp.toPx() }
                val top = r[1] - scroll.value.toFloat()
                val bot = top + r[3]
                val d2 = when {
                    top < topPx2 -> top - topPx2
                    bot > botPx2 -> if (r[3] <= botPx2 - topPx2) bot - botPx2 else top - topPx2
                    else -> 0f
                }
                if (d2 != 0f) scroll.animateScrollBy(d2, tween(320, easing = Emph))
                return@LaunchedEffect
            }
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
            try { resState.scrollToItem(0) } catch (e: Exception) { }
            delay(60); try { fr.requestFocus() } catch (e: Exception) { }
        } else {
            focusMgr.clearFocus()
            delay(750)
            if (!vm.searchOn) { vm.query = ""; try { resState.scrollToItem(0) } catch (e: Exception) { } }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val imeH = with(dens) { WindowInsets.ime.getBottom(dens).toDp() }
        val hPx = with(dens) { maxHeight.toPx() }
        val wPx = with(dens) { maxWidth.toPx() }
        // ---- gerak tumpukan kartu saat bilah cari dibuka / ditutup ----
        // Beranda yang sudah tergulir: kartu meluncur (spring, tanpa fade) menuju posisi awal saat cari dibuka, dan kembali
        // ke posisi gulir saat ditutup. Jaraknya dihitung dari posisi gulir saat itu. Kalau beranda mentok di atas: tanpa animasi.
        val flip = remember { intArrayOf(0, 0) }
        val onNow = if (vm.searchOn) 1 else 0
        if (flip[1] != onNow) { flip[0]++; flip[1] = onNow }
        val shift = remember(flip[0]) {
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
                val opening = vm.searchOn
                // posisi kartu di daftar asal: beranda (membuka) atau hasil cari (menutup; daftar itu baru dilepas, infonya masih terakhir)
                val src = if (opening) state else resState
                val mv = HashMap<String, Offset>()
                if (flip[0] > 0 && opening && cols == 2) {
                    for ((k, r) in rects) mv[k] = Offset(r[0].toFloat(), (r[1] - scroll.value).toFloat())
                } else if (flip[0] > 0) for (v in src.layoutInfo.visibleItemsInfo) {
                    val k = v.key as? String ?: continue
                    mv[if (opening) k else k.removePrefix("s")] = Offset(v.offset.x.toFloat(), v.offset.y.toFloat())
                }
                val px = if (flip[0] == 0) 0f else {
                    if (cols == 2) { if (scroll.value <= 0) 0f else scroll.value.toFloat().coerceIn(0f, hPx * .5f) }
                    else if (!state.canScrollBackward) 0f else {
                        val vis = state.layoutInfo.visibleItemsInfo
                        val avg = if (vis.isEmpty()) 0f else vis.map { it.size.height }.average().toFloat()
                        val est = (state.firstVisibleItemIndex.toFloat() / cols) * (avg + 8f * dens.density) + state.firstVisibleItemScrollOffset
                        est.coerceIn(0f, hPx * .5f)
                    }
                }
                val d = if (px < 2f) 0f else px
                StackShift(d, mv, Animatable(if (d > 0f || mv.isNotEmpty()) 0f else 1f))
            }
        }
        LaunchedEffect(shift) { if (shift.p.value < 1f) shift.p.animateTo(1f, spring(.72f, 320f)) }
        // gerak satu kartu saat pencarian dibuka (closing = false, kartu di daftar hasil) atau ditutup (closing = true, kartu di beranda):
        // meluncur dari posisi di daftar asal ke posisinya sekarang; kartu yang tidak ada di daftar asal memakai jarak gulir cadangan
        fun stackMove(id: String, closing: Boolean): Modifier = Modifier.graphicsLayer {
            if (vm.searchOn == closing) return@graphicsLayer
            val q = 1f - shift.p.value
            if (q <= .001f) return@graphicsLayer
            val f = shift.from[id]
            var curX = 0f
            var curY = 0f
            var has = false
            if (f != null) {
                if (closing && cols == 2) {
                    val r = rects[id]
                    if (r != null) { curX = r[0].toFloat(); curY = (r[1] - scroll.value).toFloat(); has = true }
                } else {
                    val k = if (closing) id else "s" + id
                    val cur = (if (closing) state else resState).layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }
                    if (cur != null) { curX = cur.offset.x.toFloat(); curY = cur.offset.y.toFloat(); has = true }
                }
            }
            if (f != null && has) {
                translationX = (f.x - curX) * q
                translationY = (f.y - curY) * q
            } else {
                translationY = (if (closing) shift.px else -shift.px) * q
            }
        }
        // perkiraan slot kartu paling atas: titik awal morph menutup saat kartu tujuan belum tampil di layar
        run {
            val gap = 8f * dens.density
            val padS = 8f * dens.density
            val cw = if (cols == 2) (wPx - 2 * padS - gap) / 2f else wPx - 2 * padS
            val t0 = with(dens) { (sat + 78.dp).toPx() }
            vm.topSlot = Morph(padS, t0, padS + cw, t0 + 92f * dens.density, 12f * dens.density, pal.s, card = true)
        }
        val livePanelH = (maxHeight - imeH - (sat + 8.dp) - 10.dp).coerceAtLeast(160.dp)
        // saat menutup, tinggi panel dibekukan agar tidak ikut naik-turun mengikuti keyboard yang menghilang
        val panelHold = remember { arrayOf(livePanelH) }
        if (vm.searchOn) panelHold[0] = livePanelH
        val panelH = panelHold[0]

        // daftar (grid 1 atau 2 kolom)
        val np = live.count { it.p }
        val sections = !vm.remFilter && np > 0 && np < live.size
        val pinnedBase = if (sections) live.filter { it.p } else live
        val othersBase = if (sections) live.filter { !it.p } else emptyList()
        // saat menyeret, satu kelompok memakai urutan sementara supaya kartu lain bergeser mengikuti
        fun shown(base: List<Note>, g: Int): List<Note> {
            // 1 kolom: urutan di grid dibiarkan tetap selama menyeret (kartu lain digeser secara visual, lihat NoteDrag.shiftOf)
            if (cols == 1 || nd.id == null || nd.group != g || nd.order.isEmpty()) return base
            val byId = base.associateBy { it.id }
            val o = nd.order.mapNotNull { byId[it] }
            return if (o.size == base.size) o else base
        }
        val pinned = shown(pinnedBase, 0)
        val others = shown(othersBase, 1)
        val full = StaggeredGridItemSpan.FullLine

        // ---- seret & lepas ----
        val scope = rememberCoroutineScope()
        val hapticDrag = LocalHapticFeedback.current
        val dragCtx by rememberUpdatedState(Triple(sections, pinnedBase.map { it.id }, othersBase.map { it.id }))
        // openId bukan state dan tidak pernah dikosongkan setelah catatan ditutup, jadi tidak boleh dipakai di sini;
        // status "catatan sedang terbuka / menutup" dicek langsung saat jari menyentuh (lihat pointerInput di bawah)
        val canDrag = !vm.searchOn && !vm.remFilter && live.size > 1
        // kolom tetap hanya berlaku di beranda penuh; di tampilan label kartunya tersaring, jadi kolom dihitung otomatis (kolom terpendek)
        val useLanes = vm.filter == null && !vm.remFilter
        fun endDrag() {
            val id = nd.id ?: return
            if (nd.dropping) return
            val ghost = nd.startC + (nd.finger - nd.at0)
            nd.dropAt = ghost
            val changed = nd.order != nd.orig
            if (cols == 1) {
                // 1 kolom: urutan baru baru dipasang ke grid sekarang. Kartu lain sudah berada di tempat visualnya, jadi
                // pemasangan tidak terlihat (animasi penempatan dimatikan selama ada seretan).
                val oldIdx = nd.orig.indexOf(id)
                val newIdx = nd.order.indexOf(id)
                val rangeKeys = HashSet<String>()
                if (oldIdx >= 0 && newIdx >= 0) rangeKeys.addAll(nd.orig.subList(minOf(oldIdx, newIdx), maxOf(oldIdx, newIdx) + 1))
                val before = HashMap<String, Int>()
                for (v in state.layoutInfo.visibleItemsInfo) { val k = v.key as? String ?: continue; before[k] = v.offset.y }
                nd.dropping = true
                if (changed) vm.moveOrder(nd.order)
                scope.launch {
                    nd.settle.snapTo(1f)
                    if (changed) {
                        withFrameNanos { }
                        withFrameNanos { }
                        // grid mengikuti kartu pertama yang tampil bila kartu itu ikut berpindah (gulir tiba-tiba bergeser):
                        // kartu di luar rentang pindahan tidak berubah posisi isinya, jadi selisihnya = lompatan gulir
                        val ref = state.layoutInfo.visibleItemsInfo.firstOrNull { v ->
                            val k = v.key as? String
                            k != null && k !in rangeKeys && before.containsKey(k)
                        }
                        if (ref != null) {
                            val jump = ref.offset.y - (before[ref.key as String] ?: ref.offset.y)
                            if (jump != 0) state.scrollBy(jump.toFloat())
                        }
                        withFrameNanos { }
                    }
                    val inf = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                    nd.resid = if (inf != null) ghost - (nd.startC + (Offset(inf.offset.x.toFloat(), inf.offset.y.toFloat()) - nd.off0)) else Offset.Zero
                    nd.ready = true
                    nd.settle.snapTo(1f)
                    nd.settle.animateTo(0f, spring(.85f, 340f))
                    nd.lift.snapTo(0f)
                    nd.reset()
                }
            } else {
                nd.resid = Offset.Zero
                val ov = nd.laneOv
                if (changed || (useLanes && ov.any { (k, v) -> vm.lanes[k] != v })) {
                    vm.moveOrder(nd.order, keepLanes = true)
                    if (useLanes) vm.setLanes(ov)
                }
                nd.ready = true
                nd.dropping = true
                scope.launch {
                    nd.settle.snapTo(1f)
                    nd.settle.animateTo(0f, spring(.85f, 340f))
                    nd.lift.snapTo(0f)
                    nd.reset()
                }
            }
        }
        LaunchedEffect(canDrag, cols) { if (nd.id != null) nd.reset() }
        // kartu terangkat perlahan (bayangan & skala), bukan muncul tiba-tiba
        LaunchedEffect(nd.id) { if (nd.id != null) nd.lift.animateTo(1f, spring(.8f, 480f)) }
        // tiap frame selama menyeret: gulir otomatis di tepi layar + cek kartu yang sedang ditindih
        LaunchedEffect(nd.id) {
            val id = nd.id ?: return@LaunchedEffect
            val density = dens.density
            var lastMove = 0L                 // 2 kolom: waktu (ns) pertukaran terakhir
            var blockId: String? = null       // 2 kolom: kartu yang baru saja ditukar (tidak boleh langsung ditukar balik)
            var lastSwapAt = Offset.Zero      // 2 kolom: posisi jari saat pertukaran terakhir
            var lastFrame = 0L
            while (nd.id == id && !nd.dropping) {
                val now = withFrameNanos { it }
                val dt = if (lastFrame == 0L) .016f else ((now - lastFrame) / 1_000_000_000f).coerceIn(.004f, .05f)
                lastFrame = now

                // ---- gulir otomatis: baru aktif setelah jari benar-benar bergerak, kecepatan naik halus (kuadrat) menuju tepi ----
                if ((nd.finger - nd.at0).getDistance() > 12f * density) {
                    val topLine = with(dens) { (sat + 72.dp + 28.dp).toPx() }
                    val botLine = (if (cols == 2) hPx else state.layoutInfo.viewportSize.height.toFloat()) - 120f * density
                    val zone = 90f * density
                    val fy = nd.finger.y
                    val up = fy < topLine
                    val down = fy > botLine
                    val mag = (if (up) topLine - fy else if (down) fy - botLine else 0f) / zone
                    if (mag > 0f) {
                        val m = mag.coerceIn(0f, 1f)
                        val dy = 760f * density * m * m * dt
                        if (cols == 2) scroll.scrollBy(if (up) -dy else dy) else state.scrollBy(if (up) -dy else dy)
                    }
                }

                val li = state.layoutInfo
                val vis = li.visibleItemsInfo
                val byKey = vis.associateBy { it.key }

                if (cols == 1) {
                    // ---- 1 KOLOM ----
                    // Tata letak grid tetap (urutan asli), jadi posisi semua kartu stabil dan keputusan di bawah tidak pernah
                    // berbalik oleh pergeseran tata letak / gulir otomatis. Tujuan = berapa banyak kartu yang pusatnya sudah
                    // dilewati pusat bayangan, dihitung pada tata letak asli. Dengan sedikit histeresis supaya tidak bolak-balik.
                    val orig = nd.orig
                    val o = orig.indexOf(id)
                    if (o < 0 || nd.h <= 0) continue
                    val cur = nd.order.indexOf(id)
                    val cy = nd.startC.y + (nd.finger.y - nd.at0.y) + nd.h / 2f
                    var fj = Int.MAX_VALUE
                    var lj = -1
                    for (j in orig.indices) if (byKey.containsKey(orig[j])) { if (j < fj) fj = j; lj = j }
                    if (lj < 0) continue
                    val hyst = 6f * density
                    val ghostTop = cy - nd.h / 2f
                    val ghostBot = cy + nd.h / 2f
                    // kedalaman tumpang-tindih yang membuat tetangga memberi tempat: 60% dari tinggi yang lebih kecil antara bayangan & tetangga
                    fun needOf(hn: Int): Float = .6f * minOf(hn, nd.h)
                    // magnet ke posisi paling atas: cukup tepi atas bayangan menyentuh area slot teratas (tidak perlu pusatnya melewati
                    // pusat kartu pertama, yang dulu membuat kartu harus nyaris tenggelam di bawah head bar)
                    val first0 = byKey[orig[0]]
                    val mg = minOf(nd.h * .5f, 48f * density) + (if (cur == 0) 12f * density else 0f)
                    val toTop = o > 0 && first0 != null && cy - nd.h / 2f < first0.offset.y + mg
                    var t = o
                    var j = o + 1
                    while (!toTop && j < orig.size) {
                        val inf = byKey[orig[j]]
                        // tepi BAWAH bayangan harus masuk ke kartu tetangga sedalam `need` (bukan pusat melewati pusat): jaraknya tidak lagi
                        // membengkak untuk kartu tipis yang melewati kartu tinggi
                        val pass = if (inf != null) ghostBot > inf.offset.y + needOf(inf.size.height) + (if (j <= cur) -hyst else hyst)
                        else j < fj   // di luar layar: di atas = sudah dilewati, di bawah = belum
                        if (!pass) break
                        t = j; j++
                    }
                    if (toTop) t = 0
                    else if (t == o) {
                        j = o - 1
                        while (j >= 0) {
                            val inf = byKey[orig[j]]
                            val pass = if (inf != null) ghostTop < inf.offset.y + inf.size.height - needOf(inf.size.height) + (if (j >= cur) hyst else -hyst)
                            else j > lj
                            if (!pass) break
                            t = j; j--
                        }
                    }
                    if (t != cur) {
                        nd.order = orig.toMutableList().also { it.removeAt(o); it.add(t, id) }
                        hapticDrag.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                } else {
                    // ---- 2 KOLOM: TUKAR POSISI ----
                    // Kolom tiap kartu tetap. Kartu seretan bertukar tempat (urutan + kolom) dengan kartu yang ditindih pusat bayangan;
                    // kartu lain tidak berpindah kolom, hanya menyesuaikan tinggi. Posisi dibaca dari tata letak sendiri (rects),
                    // jadi tidak ada tebakan / simulasi.
                    val grp = nd.order
                    val cur = grp.indexOf(id)
                    if (cur < 0 || nd.w <= 0 || nd.h <= 0) continue
                    // belum digeser sama sekali (baru tekan lama): jangan ubah apa pun
                    if ((nd.finger - nd.at0).getDistance() < 12f * density) continue
                    // beri waktu tata letak mengikuti pertukaran sebelumnya
                    if (now - lastMove < 120_000_000L) continue
                    val me = rects[id] ?: continue
                    val cx = nd.startC.x + (nd.finger.x - nd.at0.x) + nd.w / 2f
                    val cy = nd.startC.y + (nd.finger.y - nd.at0.y) + nd.h / 2f + scroll.value
                    var target: String? = null
                    for (k in grp) {
                        if (k == id) continue
                        val r = rects[k] ?: continue
                        // pusat bayangan harus masuk cukup dalam ke kartu (bukan sekadar menyentuh tepinya)
                        val ix = r[2] * .2f
                        val iy = r[3] * .2f
                        if (cx >= r[0] + ix && cx <= r[0] + r[2] - ix && cy >= r[1] + iy && cy <= r[1] + r[3] - iy) { target = k; break }
                    }
                    // pasangan yang baru ditukar tidak boleh langsung ditukar balik sebelum pusat bayangan keluar dari kartunya
                    if (blockId != null && target != blockId) blockId = null
                    // setelah menukar, jari harus bergerak dulu sebelum pertukaran berikutnya (mencegah tukar beruntun karena kartu di bawahnya naik)
                    val far = (nd.finger - lastSwapAt).getDistance() >= 28f * density
                    if (target != null && target != blockId && far) {
                        val j = grp.indexOf(target)
                        if (j >= 0 && j != cur) {
                            val nl = grp.toMutableList()
                            nl[cur] = target
                            nl[j] = id
                            if (useLanes) {
                                val lm = HashMap(nd.laneOv)
                                val la = lm[id]
                                val lb = lm[target]
                                if (la != null && lb != null) { lm[id] = lb; lm[target] = la }
                                nd.laneOv = lm
                            }
                            nd.order = nl
                            blockId = target
                            lastSwapAt = nd.finger
                            lastMove = now
                            hapticDrag.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    } else if (target == null && useLanes && far) {
                        // area kosong di bawah kartu terakhir sebuah kolom: pindah ke ujung kolom itu
                        val lane = if (cx < wPx / 2f) 0 else 1
                        var lastBot = Int.MIN_VALUE      // tepi bawah kartu terakhir di kolom itu (tanpa kartu seretan)
                        var gTop = Int.MAX_VALUE
                        var gBot = Int.MIN_VALUE
                        for (k in grp) {
                            val r = rects[k] ?: continue
                            if (r[1] < gTop) gTop = r[1]
                            if (r[1] + r[3] > gBot) gBot = r[1] + r[3]
                            if (k != id && nd.laneOv[k] == lane && r[1] + r[3] > lastBot) lastBot = r[1] + r[3]
                        }
                        val myLane = nd.laneOv[id]
                        val inside = if (lastBot == Int.MIN_VALUE) cy >= gTop else cy > lastBot
                        val alreadyLast = myLane == lane && me[1] + me[3] >= lastBot
                        if (myLane != null && inside && cy < gBot + 80f * density && !alreadyLast) {
                            val lm = HashMap(nd.laneOv)
                            lm[id] = lane
                            nd.laneOv = lm
                            nd.order = grp.filter { it != id } + id
                            lastSwapAt = nd.finger
                            lastMove = now
                            hapticDrag.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            }
        }
        // seret & lepas (satu gestur untuk kedua mode)
        val dragMod =                 Modifier.pointerInput(canDrag, cols, useLanes) {
                    if (!canDrag) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // catatan sedang dibuka / menutup (morph berjalan): jangan mulai seret
                        if (vm.openP.value > .001f || vm.nav.stack.any { it is Scr.Note }) return@awaitEachGesture
                        // kartu sebelumnya masih meluncur ke tempatnya: seretan baru menunggu, supaya reset-nya tidak menimpa seretan ini
                        if (nd.id != null) return@awaitEachGesture
                        val (_, pin, oth) = dragCtx
                        // pilihan sebelum tekan lama: setelah kartu dilepas dari seretan, pilihan dikembalikan ke ini (keluar dari mode pilih)
                        val selBefore = vm.sel.toList()
                        // tunggu tekan lama tanpa menyentuh kejadian: gulir dan ketuk biasa tetap jalan seperti semula.
                        // Batal bila jari terangkat atau bergeser melewati batas (itu gulir / geser arsip-hapus, bukan seret).
                        val slop = viewConfiguration.touchSlop
                        var last = down.position
                        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val c = ev.changes.firstOrNull { it.id == down.id }
                                if (c == null || !c.pressed || (c.position - down.position).getDistance() > slop) break
                                last = c.position
                            }
                        }
                        // held != null berarti loop berakhir sebelum waktunya (batal); null = tekan lama berhasil
                        if (held != null) return@awaitEachGesture
                        // kartu yang tersentuh dicatat oleh kartu itu sendiri (HomeCard) lewat hit-test sentuhan asli, jadi tidak meleset
                        // walau kartu sedang bergeser oleh animasi; mencocokkan koordinat layout di sini bisa memilih kartu yang salah.
                        val key = (if (nd.touchT == down.uptimeMillis) nd.touchId else null)
                            ?.takeIf { it in pin || it in oth } ?: return@awaitEachGesture
                        val m0x: Float
                        val m0y: Float
                        val m0w: Int
                        val m0h: Int
                        if (cols == 2) {
                            val r0 = rects[key] ?: return@awaitEachGesture
                            m0x = r0[0].toFloat(); m0y = (r0[1] - scroll.value).toFloat(); m0w = r0[2]; m0h = r0[3]
                        } else {
                            val me0 = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return@awaitEachGesture
                            m0x = me0.offset.x.toFloat(); m0y = me0.offset.y.toFloat(); m0w = me0.size.width; m0h = me0.size.height
                        }
                        // pojok kiri-atas kartu di dalam grid = posisi jari saat menyentuh dikurangi titik sentuh di dalam kartu
                        nd.startC = down.position - nd.touchLocal
                        nd.at0 = last
                        nd.off0 = Offset(m0x, m0y)
                        nd.w = m0w
                        nd.h = m0h
                        nd.span = m0h + 8.dp.toPx()   // + verticalItemSpacing grid
                        val g = if (key in pin) 0 else 1
                        if (key !in vm.sel) {
                            vm.sel.add(key)
                            hapticDrag.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        nd.begin(key, g, if (g == 0) pin else oth, last - Offset(m0x, m0y), last)
                        if (cols == 2) nd.laneOv = if (useLanes) HashMap<String, Int>().also { lm -> for (k in (if (g == 0) pin else oth)) lanesCur[k]?.let { lm[k] = it } } else emptyMap()
                        val start = last
                        var moved = false
                        try {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                ch.consume()
                                if (!ch.pressed) break
                                if ((ch.position - start).getDistance() > slop * 2f) moved = true
                                nd.finger = ch.position
                            }
                        } finally {
                            endDrag()
                            // seret selesai = kartu sudah dilepas di tempatnya: keluar dari mode pilih. Tekan lama tanpa menggeser tetap memilih.
                            if (moved) { vm.sel.clear(); vm.sel.addAll(selBefore.filter { id -> vm.note(id) != null }) }
                        }
                    }
                }
        if (cols == 2) {
            val noteById = live.associateBy { it.id }
            // 2 kolom menyusun SEMUA kartu sekaligus (bukan lazy): saat app baru dibuka, puluhan kartu dikomposisi dalam satu frame
            // (itu yang bikin lag awal). Jadi mulai dari layar pertama saja, sisanya ditambah bertahap per beberapa frame.
            var composeN by remember { mutableStateOf(14) }
            LaunchedEffect(live.size) { while (composeN < live.size) { withFrameNanos { }; composeN += 8 } }
            val pinnedC = pinned.take(composeN)
            val othersC = others.take((composeN - pinnedC.size).coerceAtLeast(0))
            val blocks = ArrayList<HBlock>().also { bl ->
                if (live.isEmpty()) bl.add(HBlock("empty", emptyList()))
                if (sections) bl.add(HBlock("h-pin", emptyList()))
                bl.add(HBlock(null, pinnedC.map { it.id }))
                if (sections) bl.add(HBlock("h-other", emptyList()))
                bl.add(HBlock(null, othersC.map { it.id }))
            }
            val laneOf: (String) -> Int? = { id -> if (useLanes) (nd.laneOv[id] ?: vm.lanes[id]) else null }
            val padS = with(dens) { 8.dp.roundToPx() }
            val padTop = with(dens) { (sat + 78.dp).roundToPx() }
            val padBottom = with(dens) { (160.dp + sab).roundToPx() }
            Box(
                Modifier.fillMaxSize().nestedScroll(fabConn).graphicsLayer {
                    alpha = if (vm.searchOn) 0f else 1f
                }.then(dragMod)
            ) {
                Column(Modifier.fillMaxSize().onSizeChanged { vpH[0] = it.height }.verticalScroll(scroll)) {
                    TwoColLayout(
                        blocks = blocks, laneOf = laneOf, dragId = nd.id,
                        padS = padS, padTop = padTop, padBottom = padBottom, gap = padS,
                        rects = rects, lanesOut = lanesCur, fresh = lanesNew,
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                            // kartu yang baru dapat kolom (baru dibuat / baru diedit): simpan, supaya kolomnya tidak berubah lagi
                            if (nd.id == null && useLanes && !vm.searchOn && lanesNew.isNotEmpty()) {
                                val m = HashMap(lanesNew)
                                lanesNew.clear()
                                vm.setLanes(m)
                            }
                        }
                    ) { k ->
                        when (k) {
                            "empty" -> T(
                                if (vm.remFilter) "No reminders yet. Open a note and tap the bell icon."
                                else if (vm.filter != null) "No notes with this label yet" else "Tap New to get started", 17f, 600, pal.osv,
                                Modifier.fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            "h-pin" -> T("Pinned", 13f, 500, pal.osv, Modifier.graphicsLayer { if (!vm.searchOn) alpha = shift.p.value.coerceIn(0f, 1f) }.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), ls = .01f)
                            "h-other" -> T("Others", 13f, 500, pal.osv, Modifier.graphicsLayer { if (!vm.searchOn) alpha = shift.p.value.coerceIn(0f, 1f) }.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), ls = .01f)
                            else -> {
                                val d = noteById[k]
                                if (d != null) HomeCard(vm, nav, d, pos[d.id] ?: 0, !sel.isEmpty(), cols, hPx, wPx, dens.density, pos, stackMove(d.id, true), nd, state)
                            }
                        }
                    }
                }
            }
        } else LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(cols),
            state = state,
            modifier = Modifier.fillMaxSize().nestedScroll(fabConn).graphicsLayer {
                    alpha = if (vm.searchOn) 0f else 1f
                }.then(dragMod),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = sat + 78.dp, bottom = 160.dp + sab),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (live.isEmpty()) {
                item(key = "empty", span = full) {
                    T(
                        if (vm.remFilter) "No reminders yet. Open a note and tap the bell icon."
                        else if (vm.filter != null) "No notes with this label yet" else "Tap New to get started", 17f, 600, pal.osv,
                        Modifier.fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            if (sections) item(key = "h-pin", span = full) {
                T("Pinned", 13f, 500, pal.osv, Modifier.graphicsLayer { if (!vm.searchOn) alpha = shift.p.value.coerceIn(0f, 1f) }.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), ls = .01f)
            }
            gridItems(pinned, key = { it.id }) { d ->
                HomeCard(vm, nav, d, pos[d.id] ?: 0, !sel.isEmpty(), cols, hPx, wPx, dens.density, pos, (if (nd.id != null && (cols == 1 || nd.id == d.id)) holdItemG() else flowItemG()).then(stackMove(d.id, true)), nd, state)
            }
            if (sections) item(key = "h-other", span = full) {
                T("Others", 13f, 500, pal.osv, Modifier.graphicsLayer { if (!vm.searchOn) alpha = shift.p.value.coerceIn(0f, 1f) }.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), ls = .01f)
            }
            gridItems(others, key = { it.id }) { d ->
                HomeCard(vm, nav, d, pos[d.id] ?: 0, !sel.isEmpty(), cols, hPx, wPx, dens.density, pos, (if (nd.id != null && (cols == 1 || nd.id == d.id)) holdItemG() else flowItemG()).then(stackMove(d.id, true)), nd, state)
            }
        }

        // kartu bayangan yang mengikuti jari. Posisinya dihitung dari jari saja; saat dilepas ia meluncur ke slot barunya.
        val ghostId = nd.id
        val ghostNote = ghostId?.let { vm.note(it) }
        if (ghostId != null && ghostNote != null && nd.w > 0) {
            Box(
                Modifier
                    .offset {
                        val q = if (nd.dropping) {
                            if (cols == 2) {
                                // 2 kolom: meluncur ke slot kartu di tata letak sendiri
                                val r = if (nd.ready) rects[ghostId] else null
                                if (r != null) {
                                    val sl = Offset(r[0].toFloat(), (r[1] - scroll.value).toFloat())
                                    sl + (nd.dropAt - sl) * nd.settle.value
                                } else nd.dropAt
                            } else {
                                val inf = if (nd.ready) state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == ghostId } else null
                                if (inf != null) {
                                    val slot = nd.startC + (Offset(inf.offset.x.toFloat(), inf.offset.y.toFloat()) - nd.off0)
                                    slot + nd.resid * nd.settle.value
                                } else nd.dropAt
                            }
                        } else nd.startC + (nd.finger - nd.at0)
                        IntOffset(q.x.toInt(), q.y.toInt())
                    }
                    .width(with(dens) { nd.w.toDp() })
                    .graphicsLayer {
                        val k = if (nd.dropping) (if (nd.ready) nd.settle.value.coerceIn(0f, 1f) else 1f) else nd.lift.value.coerceIn(0f, 1f)
                        val sc2 = 1f + .03f * k
                        scaleX = sc2; scaleY = sc2
                        // bayangan lembut: elevasi lebih besar (lebih menyebar) tetapi warnanya dibuat tipis, bukan gelap & tajam
                        shadowElevation = 16.dp.toPx() * k
                        ambientShadowColor = Color.Black.copy(alpha = if (pal.dark) .50f else .22f)
                        spotShadowColor = Color.Black.copy(alpha = if (pal.dark) .65f else .32f)
                        shape = RoundedCornerShape(12.dp)
                        clip = false
                    }
            ) {
                NoteCard(ghostNote, pos[ghostId] ?: 0, "f", true, Modifier, cols = cols) { }
            }
        }

        // header lengket: latar belakang di belakang bilah cari
        Box(Modifier.fillMaxWidth().height(sat + 72.dp).background(pal.bg))
        Box(
            Modifier.padding(start = 10.dp, top = sat + 10.dp)
                .graphicsLayer { translationX = -84.dp.toPx() * sa; scaleX = 1f - .4f * sa; scaleY = 1f - .4f * sa; alpha = 1f - sa }
        ) { IconBtn("menu", pal.osv, { if (sa < .5f) openDrawer() }) }

        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = sat + 10.dp)
                .graphicsLayer { translationX = 84.dp.toPx() * sa; scaleX = 1f - .4f * sa; scaleY = 1f - .4f * sa; alpha = 1f - sa }
        ) { IconBtn(if (cols == 1) "cols" else "rows", pal.osv, { if (sa < .5f) vm.setPr(vm.pr.copy(cols = if (cols == 1) 2 else 1)) }) }

        val q = vm.query.trim().lowercase()
        val res by remember(vm) {
            derivedStateOf {
                val qq = vm.query.trim().lowercase()
                vm.sorted(vm.liveF().filter { qq.isEmpty() || (it.t + " " + it.x).lowercase().contains(qq) })
            }
        }

        // Bilah cari ala Google Keep: saat diam ia pil kertas di antara tombol menu dan tepi layar. Saat dibuka, bilah HANYA melebar
        // ke kiri dan kanan (atas dan bawahnya tetap), sudutnya meluruh jadi 0 dan warnanya beralih ke latar, sehingga menyatu jadi
        // bilah atas selebar layar. Halaman hasil (latar + kartu + garis pemisah) muncul memudar DI BAWAH bilah, bukan ikut melebar.
        val padStartIdle = with(dens) { 72.dp.toPx() }
        val padEndIdle = with(dens) { 72.dp.toPx() }
        val topBar = with(dens) { (sat + 8.dp).toPx() }
        val botBar = with(dens) { (sat + 64.dp).toPx() }
        val radIdle = with(dens) { 28.dp.toPx() }
        val divY = with(dens) { (sat + 72.dp).toPx() }
        val divH = with(dens) { 1.dp.toPx() }
        val divC = androidx.compose.ui.graphics.lerp(pal.bg, pal.os, .13f)
        Box(Modifier.fillMaxSize()) {
            // halaman hasil pencarian
            // tanpa animasi (langsung muncul / hilang): hanya bilahnya yang bergerak, supaya tidak kedip
            if (vm.searchOn) {
                Box(
                    Modifier.fillMaxSize()
                        .drawBehind {
                            drawRect(pal.bg)
                            drawRect(divC, Offset(0f, divY), Size(size.width, divH))
                        }
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    // hasil pencarian: tampil di bawah bilah dengan tata letak yang sama seperti beranda (1 atau 2 kolom)
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(cols),
                        state = resState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = sat + 78.dp, bottom = imeH + 16.dp),
                        verticalItemSpacing = 8.dp,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (res.isEmpty() && vm.searchOn) item(key = "empty-search", span = full) {
                            T(if (q.isNotEmpty()) "No results" else "No notes yet", 17f, 600, pal.osv,
                                Modifier.fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        gridItems(res, key = { "s" + it.id }) { d ->
                            val ri = res.indexOfFirst { it.id == d.id }.coerceAtLeast(0)
                            NoteCard(
                                d, pos[d.id] ?: ri, "f", false,
                                flowItemG().makeRoom(vm, d.id, ri, { res.indexOfFirst { it.id == vm.openId } }, hPx, dens.density).then(stackMove(d.id, false)),
                                cols = cols
                            ) { m ->
                                focusMgr.clearFocus(); vm.cardMorph[d.id] = m; nav.openNote(d.id, m)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(sat + 72.dp).background(pal.bg))
                }
            }
        // bilah: hanya tepi kiri/kanan yang bergerak
        Box(
            Modifier.fillMaxSize()
                .drawBehind {
                    val k = sa
                    val l = androidx.compose.ui.util.lerp(padStartIdle, 0f, k)
                    val r = androidx.compose.ui.util.lerp(size.width - padEndIdle, size.width, k)
                    val rad = androidx.compose.ui.util.lerp(radIdle, 0f, k)
                    drawRoundRect(
                        androidx.compose.ui.graphics.lerp(pal.s, pal.bg, (k * 1.6f).coerceAtMost(1f)),
                        topLeft = Offset(l, topBar), size = Size(r - l, botBar - topBar), cornerRadius = CornerRadius(rad)
                    )
                }
        ) {
            // baris bilah: posisinya mengikuti tepi kiri/kanan wadah yang melebar
            Row(
                Modifier.padding(
                    top = sat + 8.dp,
                    start = androidx.compose.ui.unit.lerp(76.dp, 4.dp, sa),
                    end = androidx.compose.ui.unit.lerp(80.dp, 8.dp, sa)
                ).fillMaxWidth().height(56.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !vm.searchOn) { vm.searchOn = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ikon kiri: kaca pembesar berputar jadi panah kembali (seperti Keep)
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = vm.searchOn) { vm.searchOn = false }, Alignment.Center) {
                    Box(Modifier.graphicsLayer { alpha = 1f - sa; scaleX = 1f - .5f * sa; scaleY = 1f - .5f * sa; rotationZ = -90f * sa }) { Ic("search", pal.osv) }
                    Box(Modifier.graphicsLayer { alpha = sa; scaleX = .5f + .5f * sa; scaleY = .5f + .5f * sa; rotationZ = 90f * (1f - sa) }) { Ic("back", pal.os) }
                }
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = vm.query, onValueChange = { vm.query = it },
                    modifier = Modifier.weight(1f).focusRequester(fr).onFocusChanged { if (it.isFocused && !vm.searchOn) vm.searchOn = true },
                    textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 17.sp, fontWeight = FontWeight(400)),
                    singleLine = true, cursorBrush = SolidColor(pal.p),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusMgr.clearFocus() }),
                    decorationBox = { inner ->
                        Box { if (vm.query.isEmpty()) T(if (vm.remFilter) "Search in Reminders" else vm.filter?.let { "Search in $it" } ?: "Search notes", 17f, 400, pal.osv, maxLines = 1); inner() }
                    }
                )
                // hapus teks: hanya muncul saat ada ketikan
                AnimatedVisibility(
                    visible = vm.searchOn && vm.query.isNotEmpty(),
                    enter = fadeIn(tween(140)) + scaleIn(tween(180, easing = Emph), .6f),
                    exit = fadeOut(tween(100)) + scaleOut(tween(120), .6f)
                ) { IconBtn("x", pal.osv, { vm.query = "" }, size = 44.dp) }
            }
        }
        }
    }
}

@Composable
fun HomeCard(
    vm: AppVM, nav: Nav, d: Note, i: Int, selMode: Boolean, cols: Int, hPx: Float, wPx: Float, dens: Float,
    pos: Map<String, Int>, base: Modifier, nd: NoteDrag, gs: LazyStaggeredGridState
) {
    val sel = vm.sel
    androidx.compose.runtime.DisposableEffect(d.id) { onDispose { vm.cardMorph.remove(d.id) } }
    val dragging = nd.id == d.id
    // 1 kolom: kartu di antara asal & tujuan seretan bergeser secara visual (grid tidak berubah selama menyeret)
    val shiftT = if (cols == 1) nd.shiftOf(d.id) else 0f
    val shiftA = remember { Animatable(0f) }
    LaunchedEffect(shiftT, nd.id) {
        if (nd.id == null) shiftA.snapTo(0f) else shiftA.animateTo(shiftT, spring(.85f, 420f))
    }
    Column(
        base.makeRoom(vm, d.id, i, { vm.openId?.let { pos[it] } ?: -1 }, hPx, dens, cols, wPx, flat = d.cv.isNotEmpty()).then(
            // ruang kartu tetap ada (supaya kartu lain menata diri di sekitarnya) tetapi tidak terlihat: yang terlihat adalah
            // kartu bayangan di atas grid (lihat HomeScreen), yang posisinya hanya bergantung pada jari
            if (dragging) Modifier.graphicsLayer { alpha = 0f } else Modifier
        ).then(
            // saat dilepas, urutan baru dipasang ke grid dan pergeseran visual dihapus pada frame yang sama (posisinya identik)
            if (cols == 1) Modifier.graphicsLayer { translationY = if (nd.id == null || nd.dropping) 0f else shiftA.value } else Modifier
        ).pointerInput(d.id) {
            // hanya mencatat (tidak mengonsumsi): kartu ini yang benar-benar tersentuh, dibaca oleh gestur seret di grid
            awaitEachGesture {
                val dn = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                nd.touchId = d.id; nd.touchT = dn.uptimeMillis; nd.touchLocal = dn.position
            }
        }
    ) {
        // animasi masuk hanya untuk kartu di layar pertama (urutan < 8); sisanya langsung tampil, supaya tidak puluhan animasi
        // berjalan serentak saat app baru dibuka (2 kolom merender semua kartu sekaligus)
        val first = remember(d.id) { val f = !vm.seen.contains(d.id); vm.seen.add(d.id); f && i < 8 }
        val prog = remember(d.id) { Animatable(if (first) 0f else 1f) }
        LaunchedEffect(d.id) {
            if (first) {
                delay(i * 35L)
                prog.animateTo(1f, spring(.8f, 500f))
            }
        }
        SwipeCard(
            enabled = !selMode,
            shape = SH[i % 5].let { RoundedCornerShape(it[0].dp, it[1].dp, it[2].dp, it[3].dp) },
            onArchive = { vm.mark(listOf(d.id), "Note archived") { it.copy(a = true, p = false) } },
            onDelete = { vm.mark(listOf(d.id), "Moved to Trash") { it.copy(dt = System.currentTimeMillis()) } }
        ) {
            NoteCard(
                d, i, "f", d.id in sel,
                Modifier.graphicsLayer {
                    val pv = prog.value
                    alpha = pv.coerceIn(0f, 1f); translationY = (1f - pv) * 32.dp.toPx()
                    val sc = .92f + .08f * pv.coerceIn(0f, 1f); scaleX = sc; scaleY = sc
                },
                onLong = { if (d.id !in sel) sel.add(d.id) },
                track = { m ->
                    // selama kartu lain sedang membuka/menutup, posisi kartu ini jangan ditimpa: nilainya sudah ikut tergeser
                    // (dan terpotong layar) oleh makeRoom sendiri, sehingga klasifikasi atas/bawah bisa berbalik di tengah animasi
                    val busy = vm.openId != null && vm.openId != d.id && vm.openP.value > .001f
                    if (!vm.searchOn && !busy) vm.cardMorph[d.id] = m
                },
                cols = cols,
                onClick = { m ->
                    if (selMode) { if (d.id in sel) sel.remove(d.id) else sel.add(d.id) }
                    else nav.openNote(d.id, m)
                }
            )
        }
    }
}

/** Buka catatan baru dari tombol FAB (morph melar dari tombol ke satu halaman penuh). */
fun openNewFromFab(vm: AppVM) {
    val n = vm.newNote()
    vm.nav.openNote(n.id, vm.fabMorph, true)
}

/** Bilah voice note cepat sedang menggantikan tombol Baru (ditarik, merekam, atau sedang menguncup kembali). */
private fun quickCoversFab(vm: AppVM): Boolean =
    (QuickFx.drag || QuickFx.shown || QuickFx.noteBound || QuickFx.p.value > .001f) &&
        (vm.nav.top is Scr.Home || QuickFx.noteBound)

private const val FAB_HOLD_MS = 550L   // tahan sedikit lama untuk memunculkan sheet (suara dll.)

/**
 * FAB:
 *  - ketuk            -> catatan baru langsung terbuka (melar dari tombol)
 *  - tahan ~0,5 detik -> sheet tambah (ada catatan suara)
 *  - tarik ke atas    -> catatan mekar mengikuti jari; lepas di atas ambang = terbuka, kalau tidak = menguncup lagi
 *  - geser ke kiri    -> tombol melebar jadi bilah voice note cepat (lihat QuickVoice); lepas di atas ambang = merekam
 */
@Composable
fun Fab(vm: AppVM, act: Actions, small: Boolean, hide: Boolean, lift: Dp) {
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // morph dari tombol ini: wadah catatan menggambar tombolnya sendiri, jadi tombol asli tidak boleh mengecil / memudar
    // sendiri di atas catatan (itu yang tampak sebagai kedip di bawah layar). Cukup disembunyikan di fase gambar (fabCovered).
    val viaMorph = vm.morph?.fabg != null && (FabFx.drag || vm.nav.stack.any { it is Scr.Note })
    val gone = (hide || FabFx.drag) && !viaMorph
    // bilah voice note cepat (QuickVoice) menggambar bentuk yang sama persis di atas tombol ini; selama ia aktif tombol asli
    // harus benar-benar hilang (tanpa animasi, tanpa skala) supaya tidak ada dua tombol bertumpuk. Dibaca di lambda gambar
    // agar tidak memicu rekomposisi tiap frame saat bilah ditarik.
    val sc by animateFloatAsState(if (gone) .4f else if (pr) .93f else 1f, spring(.55f, 500f), label = "sc")
    val al by animateFloatAsState(if (gone) 0f else 1f, tween(if (FabFx.drag) 80 else 250), label = "al")
    val r by animateDpAsState(if (pr) 22.dp else 32.dp, spring(.6f, 500f), label = "r")
    val ly by animateDpAsState(lift, spring(.7f, 400f), label = "ly")
    // dilepas dari komposisi selama catatan terbuka: tombol tak terlihat TIDAK boleh tetap menangkap sentuhan di pojok kanan bawah
    if (hide && (viaMorph || al <= .01f)) return
    val sab = sabDp()
    val dens = LocalDensity.current.density
    val shape = RoundedCornerShape(r)
    Box(Modifier.fillMaxSize().padding(end = 20.dp, bottom = 24.dp + sab), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.offset(y = -ly)
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    if (!hide && b.width > 0f) vm.fabMorph = Morph(b.left, b.top, b.right, b.bottom, 32f * dens, pal.pc, card = true, fabg = FabGhost(!small, pal.opc))
                }
                .graphicsLayer {
                    val q = quickCoversFab(vm)
                    scaleX = if (q) 1f else sc; scaleY = if (q) 1f else sc
                    // catatan yang membuka (morph kartu) menutupi tombol secara bertahap mengikuti progresnya, tanpa animasi sendiri
                    val kp = (1f - vm.openP.value.coerceIn(0f, 1f) / .5f).coerceIn(0f, 1f)
                    alpha = if (q || fabCovered(vm)) 0f else al * kp * kp * (3f - 2f * kp)
                    transformOrigin = TransformOrigin(1f, 1f)
                }
                .shadow(
                    if (pal.dark) 4.dp else 3.dp, shape, clip = false,
                    ambientColor = pal.p.copy(alpha = if (pal.dark) .35f else .22f),
                    spotColor = pal.p.copy(alpha = if (pal.dark) .45f else .32f)
                )
                .height(88.dp).widthIn(min = 88.dp).clip(shape).background(pal.pc)
                .indication(src, LocalIndication.current)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick("New note") { if (!hide) openNewFromFab(vm); true }
                    onLongClick("Add voice note") { if (!hide) vm.addOn = true; true }
                }
                .pointerInput(hide) {
                    if (hide) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val press = PressInteraction.Press(down.position)
                        scope.launch { src.emit(press) }
                        val slop = viewConfiguration.touchSlop
                        val sx = down.position.x
                        val sy = down.position.y
                        var res = 0   // 0 batal, 1 ketuk, 2 tarik ke atas, 3 geser ke kiri
                        val held = withTimeoutOrNull(FAB_HOLD_MS) {
                            var done = false
                            while (!done) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id }
                                if (c == null || c.isConsumed) { res = 0; done = true }
                                else if (c.changedToUpIgnoreConsumed()) { res = 1; done = true }
                                else {
                                    val dx = c.position.x - sx
                                    val dy = c.position.y - sy
                                    if (dy < -slop && abs(dy) > abs(dx)) { res = 2; done = true }
                                    else if (dx < -slop && abs(dx) > abs(dy)) { res = 3; done = true }
                                    else if (abs(dx) > slop * 2f || dy > slop * 2f) { res = 0; done = true }
                                }
                            }
                            true
                        }
                        if (held == null) {
                            // ditahan cukup lama tanpa bergerak: sheet tambah
                            scope.launch { src.emit(PressInteraction.Release(press)) }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.addOn = true
                            return@awaitEachGesture
                        }
                        when (res) {
                            1 -> {
                                scope.launch { src.emit(PressInteraction.Release(press)) }
                                openNewFromFab(vm)
                            }
                            2 -> {
                                scope.launch { src.emit(PressInteraction.Cancel(press)) }
                                val n = vm.newNote()
                                FabFx.drag = true
                                scope.launch { vm.openP.snapTo(0f) }
                                vm.nav.openNote(n.id, vm.fabMorph, false)
                                val dist = 260.dp.toPx()
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var p = 0f
                                var passed = false
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(c.uptimeMillis, c.position)
                                    val up = c.changedToUpIgnoreConsumed()
                                    c.consume()
                                    if (up) break
                                    p = ((sy - c.position.y) / dist).coerceIn(0f, 1f)
                                    val v = p
                                    // progres mengejar jari lewat spring kaku (bukan loncat per sampel sentuh): gerak wadah mulus walau
                                    // sampel sentuh tidak rata, dan kecepatannya berlanjut mulus ke animasi lepas jari di bawah
                                    scope.launch { vm.openP.animateTo(v, spring(1f, 1400f)) }
                                    val nowPassed = p > .3f
                                    if (nowPassed != passed) { passed = nowPassed; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                }
                                val vy = tracker.calculateVelocity().y
                                val commit = p > .3f || vy < -1100f
                                scope.launch {
                                    if (commit) {
                                        vm.openP.animateTo(1f, spring(.85f, 560f))
                                        FabFx.drag = false
                                        vm.ed?.let { it.focusAt(it.blocks[0].id, 0) }   // keyboard baru muncul setelah halaman terbuka
                                    } else {
                                        vm.openP.animateTo(0f, spring(1f, 700f))
                                        vm.nav.pop()
                                        FabFx.drag = false
                                    }
                                }
                            }
                            3 -> {
                                scope.launch { src.emit(PressInteraction.Cancel(press)) }
                                if (vm.rec.active) {
                                    vm.show("Already recording")
                                    return@awaitEachGesture
                                }
                                if (!micOk(ctx)) {
                                    // belum ada izin mikrofon: minta dulu, geser lagi setelah diizinkan
                                    act.requestMic { }
                                    return@awaitEachGesture
                                }
                                QuickFx.drag = true
                                val dist = 220.dp.toPx()
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var p = 0f
                                var passed = false
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(c.uptimeMillis, c.position)
                                    val up = c.changedToUpIgnoreConsumed()
                                    c.consume()
                                    if (up) break
                                    p = ((sx - c.position.x - slop).coerceAtLeast(0f) / dist).coerceIn(0f, 1f)
                                    val v = p
                                    // progres mengejar jari lewat spring kaku (bukan snapTo): kecepatan jari terbawa mulus ke animasi lepas jari
                                    scope.launch { QuickFx.p.animateTo(v, spring(1f, 1400f)) }
                                    val nowPassed = p > .35f
                                    if (nowPassed != passed) { passed = nowPassed; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                }
                                val vx = tracker.calculateVelocity().x
                                QuickFx.drag = false
                                if (p > .35f || vx < -1000f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        // bilah mekar LEBIH DULU dan tidak menunggu perekam: MediaRecorder disiapkan di thread IO,
                                        // jadi main thread tidak terblokir dan gerak tidak berhenti di tengah
                                        val grow = launch { QuickFx.p.animateTo(1f, spring(.8f, 480f)) }
                                        if (!quickStartAsync(vm)) {
                                            grow.cancel()
                                            QuickFx.p.animateTo(0f, spring(1f, 600f))
                                        }
                                    }
                                } else {
                                    scope.launch { QuickFx.p.animateTo(0f, spring(1f, 600f)) }
                                }
                            }
                            else -> scope.launch { src.emit(PressInteraction.Cancel(press)) }
                        }
                    }
                }
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Ic("plus", pal.opc, 36.dp)
            AnimatedVisibility(
                visible = !small,
                enter = expandHorizontally(tween(320, easing = Emph), expandFrom = Alignment.Start) + fadeIn(tween(240, 60)),
                exit = shrinkHorizontally(tween(280, easing = Emph), shrinkTowards = Alignment.Start) + fadeOut(tween(120))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(14.dp))
                    T("New", 22f, 900, pal.opc, maxLines = 1)
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
            Modifier.fillMaxWidth().height(sat + 72.dp).background(pal.bg).padding(top = sat + 12.dp, start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBtn("x", pal.os, { vm.sel.clear() }, size = 48.dp)
            Box(Modifier.weight(1f).height(48.dp).padding(start = 6.dp), Alignment.CenterStart) { T("$count selected", 18f, 900, pal.os) }
            IconBtn("all", pal.os, { vm.liveF().forEach { if (it.id !in vm.sel) vm.sel.add(it.id) } }, size = 48.dp)
            IconBtn("pin", pal.os, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) {
                    val all = ids.all { vm.note(it)?.p == true }
                    vm.sel.clear()
                    vm.mark(ids, if (all) "Note unpinned" else "${cnt(ids.size, "note")} pinned") { it.copy(p = !all) }
                }
            }, size = 48.dp)
            IconBtn("arc", pal.os, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) { vm.sel.clear(); vm.mark(ids, "${cnt(ids.size, "note")} archived") { it.copy(a = true, p = false) } }
            }, size = 48.dp)
            IconBtn("del", pal.os, {
                val ids = vm.sel.toList()
                if (ids.isNotEmpty()) { vm.sel.clear(); vm.mark(ids, "${cnt(ids.size, "note")} moved to Trash") { it.copy(dt = System.currentTimeMillis()) } }
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
    Column(Modifier.fillMaxSize().background(pal.bg).onSizeChanged { hPx = it.height.toFloat() }) {
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 40.dp + sab),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (notes.isEmpty()) item(key = "empty-list") {
                T(empty, 17f, 600, pal.osv, flowItem().fillMaxWidth().padding(vertical = 48.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
            }
            itemsIndexed(notes, key = { _, it -> it.id }) { ix, d ->
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
                    d, ix, "f", false,
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
    val track = androidx.compose.ui.graphics.lerp(pal.neutral(if (pal.dark) .16f else .10f), pal.p, k)
    val edge = androidx.compose.ui.graphics.lerp(pal.neutral(.45f), pal.p, k)
    val thumb = androidx.compose.ui.graphics.lerp(pal.neutral(.45f), pal.op, k)
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

/** true di dalam PageScaffold (halaman opsi catatan, transkripsi, dst.): kartu memakai wadah tonal + border tipis supaya tidak menyatu dengan latar. */
val LocalToneCards = androidx.compose.runtime.compositionLocalOf { false }

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
    val tone = LocalToneCards.current
    Column(
        Modifier.fillMaxWidth().clip(shape).background(bg ?: if (tone) pal.tb else pal.c2)
            .then(if (tone) Modifier.border(1.dp, pal.ol, shape) else Modifier)
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
                T("Neat notes, fast and cozy 0123", 14f, 400, fg, alpha = .75f)
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
    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(sat + 64.dp))
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + sab)
            ) {
                // judul besar: mengecil dan memudar saat digulir, digantikan judul kecil di bilah atas
                T(
                    "Settings", 40f, 400, pal.os,
                    Modifier.padding(start = 8.dp, top = 20.dp, bottom = 32.dp).graphicsLayer {
                        val k = (scroll.value / 110.dp.toPx()).coerceIn(0f, 1f)
                        alpha = 1f - (k * 1.6f).coerceAtMost(1f)
                        scaleX = 1f - .12f * k; scaleY = 1f - .12f * k
                        transformOrigin = TransformOrigin(0f, .5f)
                    },
                    ls = -.01f, maxLines = 1
                )
                // Urutan kelompok: yang paling sering dipakai di atas (tampilan, catatan), yang jarang diubah di bawah (suara, cadangan)
                SGroup("Appearance", 0) {
                    SCard(POS_TOP) {
                        T("Theme", 16f, 500, pal.os)
                        val modes = listOf("auto" to "phone", "light" to "lm", "dark" to "dm")
                        val curMode = normMode(pr.mode)
                        val cap = when (curMode) {
                            "auto" -> "Auto: light or dark follows your device"
                            "light" -> "Light"
                            else -> "Dark (AMOLED, pure black)"
                        }
                        Crossfade(cap, Modifier.padding(top = 2.dp, bottom = 14.dp), tween(200), label = "cap") { T(it, 14f, 400, pal.osv) }
                        SegPill(3, modes.indexOfFirst { it.first == curMode }.coerceAtLeast(0), { vm.setPr(pr.copy(mode = modes[it].first)) }) { i, fg ->
                            Ic(modes[i].second, fg, 24.dp)
                        }
                    }
                    SCard(POS_MID) {
                        T("Colors", 16f, 500, pal.os, Modifier.padding(bottom = 12.dp))
                        val dynAvail = android.os.Build.VERSION.SDK_INT >= 31
                        val dynOn = pr.dyn && dynAvail
                        if (dynAvail) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .clickable { vm.setPr(pr.copy(dyn = !dynOn)) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    T("Dynamic color", 16f, 400, pal.os)
                                    T("Material You, follows your wallpaper", 14f, 400, pal.osv)
                                }
                                MorphSwitch(dynOn)
                            }
                        }
                        // satu-satunya pilihan warna manual: tombol warna kustom (dengan slider), hanya tampil saat Material You mati
                        AnimatedVisibility(
                            visible = !dynOn,
                            enter = expandVertically(spring(.9f, 420f)) + fadeIn(tween(220)),
                            exit = shrinkVertically(spring(1f, 500f)) + fadeOut(tween(140))
                        ) {
                            val (ch, cs) = hueSat(pr.seed)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .clickable { custom = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(hsl(ch, cs.coerceIn(35f, 88f), 50f).let { if (pal.mono) it.gray() else it }))
                                Column(Modifier.weight(1f)) {
                                    T("Custom color", 16f, 400, pal.os)
                                    T("Pick your own hue and intensity", 14f, 400, pal.osv)
                                }
                                Ic("chr", pal.osv, 24.dp)
                            }
                        }
                    }
                    SCard(POS_MID, onClick = { vm.setPr(pr.copy(mono = !pr.mono)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                T("Monochrome", 16f, 500, pal.os)
                                T("Turn all app colors to grayscale", 14f, 400, pal.osv)
                            }
                            MorphSwitch(pr.mono)
                        }
                    }
                    SCard(POS_BOT, pad = PaddingValues(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp)) {
                        T("Font", 16f, 500, pal.os, Modifier.padding(start = 8.dp, bottom = 10.dp))
                        val am = LocalContext.current.assets
                        listOf("def" to "Default", "flex" to "Roboto Flex", "times" to "Times New Roman", "reenie" to "Reenie Beanie").forEach { (id, name) ->
                            val fam = remember(id) { loadFont(am, id) }
                            FontChoice(name, fam, pr.ff == id) { vm.setPr(pr.copy(ff = id)) }
                        }
                    }
                }
                SGroup("Notes", 60) {
                    SCard(POS_ONE) {
                        T("Sort notes", 16f, 500, pal.os, Modifier.padding(bottom = 14.dp))
                        val sorts = listOf("u" to "Edited", "c" to "Created")
                        SegPill(2, sorts.indexOfFirst { it.first == pr.sort }.coerceAtLeast(0), { vm.setSort(sorts[it].first) }) { i, fg ->
                            T(sorts[i].second, 15f, 500, fg)
                        }
                        Crossfade(if (pr.sort == "c") "Most recently created on top" else "Most recently edited on top", Modifier.padding(top = 12.dp, start = 4.dp), tween(200), label = "scap") {
                            T(it, 14f, 400, pal.osv)
                        }
                        T("Press, hold, and drag a card on the home screen to move it. Picking an order here clears those moves.", 14f, 400, pal.osv, Modifier.padding(top = 8.dp, start = 4.dp), lh = 1.3f)
                    }
                }
                SGroup("Drawing", 90) {
                    SCard(POS_TOP) {
                        T("Touch input", 16f, 500, pal.os, Modifier.padding(bottom = 14.dp))
                        val tsl = listOf("auto" to "Auto", "stylus" to "Stylus only", "finger" to "Finger too")
                        SegPill(3, tsl.indexOfFirst { it.first == pr.ts }.coerceAtLeast(0), { vm.setPr(pr.copy(ts = tsl[it].first)) }) { i, fg ->
                            T(tsl[i].second, 14f, 500, fg)
                        }
                        Crossfade(
                            when (pr.ts) {
                                "stylus" -> "Only the stylus draws. Fingers still type and scroll."
                                "finger" -> "Stylus and fingers both draw. One finger draws, two fingers scroll."
                                else -> "With a stylus, only the stylus draws. Without one, one finger draws and two fingers scroll."
                            }, Modifier.padding(top = 12.dp, start = 4.dp), tween(200), label = "tscap"
                        ) { T(it, 14f, 400, pal.osv) }
                    }
                    SCard(POS_BOT) {
                        T("Back to text mode automatically", 16f, 500, pal.os, Modifier.padding(bottom = 14.dp))
                        val atl = listOf(0 to "Off", 5 to "5 s", 10 to "10 s", 30 to "30 s")
                        SegPill(4, atl.indexOfFirst { it.first == pr.iat }.coerceAtLeast(0), { vm.setPr(pr.copy(iat = atl[it].first)) }) { i, fg ->
                            T(atl[i].second, 14f, 500, fg)
                        }
                    }
                }
                SGroup("Voice", 120) {
                    val ready = vm.models.list.filter { it.status == 2 }.joinToString { it.info.name }
                    SActionRow(
                        POS_ONE, "mic", "Voice transcription",
                        if (vm.gqSet) "Groq API key saved" + (if (ready.isNotEmpty()) " · offline model: $ready" else "")
                        else if (ready.isNotEmpty()) "Offline model ready: $ready"
                        else "Add a Groq API key (fast) or download an offline model"
                    ) { vm.nav.push(Scr.Models) }
                }
                SGroup("Backup", 150) {
                    val n = vm.docs.size
                    val ni = vm.imgIds().size
                    val na = vm.docs.sumOf { it.au.size }
                    val old = vm.lastBk != 0L && System.currentTimeMillis() - vm.lastBk > 7 * 86400000L
                    val info = cnt(n, "note") + ", " + cnt(ni, "photo") + ", " + cnt(na, "audio file") + ". " + (if (vm.lastBk != 0L) {
                        "Last backup: " + SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.US).format(Date(vm.lastBk)) +
                            (if (old) ". It's been over a week, so back up again." else ".")
                    } else "No backup yet.") + " Backup files stay on your device. No server is involved."
                    SCard(POS_TOP) { T(info, 14f, 400, pal.osv, lh = 1.5f) }
                    SActionRow(POS_MID, "dl", "Save backup", "One .dinduy file with your notes, photos, and audio") { act.saveBackup() }
                    SActionRow(POS_MID, "up", "Share backup", "Send it with WhatsApp, Files, and more") { act.shareBackup() }
                    SActionRow(POS_BOT, "restore", "Restore backup", "Pick a .dinduy file from your device (web version files work too)") { act.restoreBackup() }
                }
            }
        }
        // bilah atas: transparan di puncak, berubah jadi warna wadah saat digulir; judul kecil muncul menggantikan judul besar
        Row(
            Modifier.fillMaxWidth().height(sat + 64.dp)
                .drawBehind {
                    val k = (scroll.value / 80.dp.toPx()).coerceIn(0f, 1f)
                    drawRect(androidx.compose.ui.graphics.lerp(pal.bg, pal.c2, k))
                }
                .padding(top = sat, start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("back", pal.os, onBack)
            T(
                "Settings", 22f, 400, pal.os,
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
            title = { T("Custom color", 24f, 400, pal.os) },
            text = {
                Column {
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(col))
                    Spacer(Modifier.height(16.dp))
                    T("Hue", 14f, 500, pal.osv)
                    BigSlider(h, 0f, 360f) { h = it }
                    T("Intensity", 14f, 500, pal.osv)
                    BigSlider(s, 35f, 88f) { s = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setPr(pr.copy(seed = String.format("#%06X", hsl(h, s, 50f).toArgb() and 0xFFFFFF), dyn = false)); custom = false
                }) { T("Use", 15f, 600, pal.p) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { T("Cancel", 15f, 500, pal.osv) } }
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
            fun title(id: String) = vm.note(id)?.t?.ifEmpty { "Untitled" } ?: "Untitled"
            when (s) {
                is Sheet.LblEdit -> {
                    T(s.name, 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "pen", "Rename") { close(); vm.lblDlg = LblDlg(s.name, null) }
                    Opt(2, "del", "Delete label", "Notes aren't deleted") { close(); vm.deleteLabel(s.name) }
                }
                is Sheet.Arc -> {
                    T(title(s.id), 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "txt", "Open note") { close(); nav.openNote(s.id, vm.cardMorph[s.id]) }
                    Opt(2, "unarc", "Unarchive") { close(); vm.mark(listOf(s.id), "Note unarchived") { it.copy(a = false) } }
                    Opt(3, "del", "Delete note") { close(); vm.mark(listOf(s.id), "Moved to Trash") { it.copy(dt = System.currentTimeMillis()) } }
                }
                is Sheet.Trs -> {
                    T(title(s.id), 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "restore", "Restore") { close(); vm.mark(listOf(s.id), "Note restored") { it.copy(dt = 0L) } }
                    Opt(2, "del", "Delete forever") { close(); vm.purge(s.id) }
                }
                is Sheet.Bk -> {
                    val p = vm.pending
                    T("Restore backup", 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp), ls = -.01f)
                    if (p != null) T(
                        cnt(p.notes.size, "note") + ", " + cnt(p.nImg, "photo") + ", " + (if (p.aids.isNotEmpty()) cnt(p.aids.size, "audio file") + ", " else "") + "created " + SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.US).format(Date(p.at)),
                        14f, 400, pal.osv, Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                    Opt(1, "lib", "Merge", "Add from the backup. Your current notes stay safe") { close(); vm.applyBackup(false) }
                    Opt(2, "restore", "Replace all", "Replace all notes on this device with the backup") { close(); vm.applyBackup(true) }
                }
                is Sheet.EmptyTrash -> {
                    T("Empty Trash?", 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "del", "Delete all forever", "This can't be undone") { close(); vm.purgeAll() }
                }
            }
        }
    }
}

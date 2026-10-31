@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.dinduy

import kotlin.math.roundToInt
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.withContext
import java.io.File

sealed class Scr {
    object Home : Scr()
    class Note(val id: String) : Scr()
    object Settings : Scr()
    object Archive : Scr()
    object Trash : Scr()
    class NoteMenu(val id: String) : Scr()     // halaman titik tiga di editor
    class NoteLabels(val id: String) : Scr()   // halaman label untuk satu catatan
}

class Nav(private val vm: AppVM) {
    val stack = mutableStateListOf<Scr>(Scr.Home)
    var noteId by mutableStateOf<String?>(null)
    var pendingTitle = false
    val top: Scr get() = stack.last()

    fun push(s: Scr) { stack.add(s) }

    fun pop() {
        if (stack.size > 1) {
            val t = top
            if (t is Scr.Note) {
                vm.ed?.leave()
                // morph keluar selalu menuju posisi kartu saat ini (bukan posisi saat dibuka),
                // jadi tetap konsisten walau catatan sudah pindah urutan setelah lama diedit
                val ex = vm.note(t.id)
                val live = ex?.let { !it.a && it.dt == 0L } == true
                vm.openId = t.id
                if (ex == null) {
                    // catatan kosong tidak jadi dibuat: menguncup kembali menjadi tombol Baru (+)
                    vm.closing = false
                    vm.morph = vm.fabMorph
                } else {
                    vm.morph = vm.cardMorph[t.id] ?: (if (live) vm.topSlot else null)
                    // kartu tujuan bisa di luar layar (daftar sedang digulir) atau berpindah urutan: morph mengikutinya
                    vm.closing = live && !vm.searchOn
                    if (vm.closing) { vm.revealId = t.id; vm.revealTick++ }
                }
            }
            stack.removeAt(stack.size - 1)
        }
    }

    fun openNote(id: String, m: Morph?, newNote: Boolean = false) {
        vm.morph = m
        vm.openId = id
        noteId = id
        pendingTitle = newNote
        stack.add(Scr.Note(id))
    }
}

@Composable
fun ViewLayer(on: Boolean, under: Boolean, keep: Boolean = false, base: Boolean = false, content: @Composable () -> Unit) {
    val pal = LocalPal.current
    // geser horizontal penuh (opak, tanpa fade): layar baru masuk dari kanan, layar di bawahnya bergeser pelan + meredup
    val t by animateFloatAsState(if (on || base) 1f else 0f, tween(460, easing = Emph), label = "t")
    val u by animateFloatAsState(if (under) 1f else 0f, tween(460, easing = Emph), label = "u")
    if (!keep && !on && t == 0f) return
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationX = (1f - t) * size.width - u * size.width * .26f }
            .drawBehind {
                val w = 28.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (pal.dark) .14f else .04f)), startX = -w, endX = 0f),
                    topLeft = Offset(-w, 0f), size = Size(w, size.height)
                )
            }
            .background(pal.s).pointerInput(Unit) { }
            .then(
                if (!on) Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            e.changes.forEach { it.consume() }
                        }
                    }
                } else Modifier
            )
    ) {
        content()
        if (u > .001f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (if (pal.dark) .16f else .05f) * u)))
    }
}

@Composable
fun NoteLayer(vm: AppVM, on: Boolean, morph: Morph?, under: Boolean = false, content: @Composable () -> Unit) {
    val pal = LocalPal.current
    // sama dengan ViewLayer: saat halaman opsi menutupi editor, editor bergeser pelan + meredup
    val u by animateFloatAsState(if (under) 1f else 0f, tween(460, easing = Emph), label = "nu")
    // satu progres bersama (vm.openP): morph kartu <-> layar penuh DAN kartu lain yang memberi ruang membaca nilai yang sama,
    // jadi selalu sinkron. Spring spatial M3 Expressive: membuka sedikit lentur, menutup lebih rapat (tanpa overshoot keluar layar).
    LaunchedEffect(on) {
        if (on) {
            vm.closing = false
            vm.openP.animateTo(1f, spring(.86f, 320f))
        } else {
            val follow = vm.closing
            val job = if (follow) launch { followCard(vm) } else null
            vm.openP.animateTo(0f, if (follow) spring(.9f, 330f) else spring(.92f, 400f))
            job?.cancel()
            vm.closing = false
        }
    }
    val t0 = vm.openP.value
    if (!on && t0 <= .01f) return
    vm.ghostVer
    Box(
        Modifier.fillMaxSize().graphicsLayer { translationX = -u * size.width * .26f }.pointerInput(Unit) { }.drawWithContent {
            val tt = vm.openP.value.coerceIn(0f, 1f)
            vm.morphTick
            val m = vm.morph ?: Morph(size.width * .12f, size.height * .4f, size.width * .88f, size.height * .6f, 32.dp.toPx(), pal.s)
            // tepi kiri/kanan sedikit lebih cepat dari tepi atas/bawah: wadah terasa "meregang" ke bawah/atas di akhir
            // kartu sempit (grid 2 kolom): semua tepi bergerak serempak supaya tidak miring/melar; kartu lebar: tepi kiri-kanan sedikit lebih cepat
            val narrow = (m.r - m.l) < size.width * .7f
            val tx = if (narrow) tt else (tt * 1.12f).coerceIn(0f, 1f)
            val l = lerp(m.l, 0f, tx)
            val r = lerp(m.r, size.width, tx)
            val tp = lerp(m.t, 0f, tt)
            val b = lerp(m.b, size.height, tt)
            val r4 = m.r4 ?: FloatArray(4) { m.rad }
            fun rr(k: Int) = CornerRadius(lerp(r4[k], 0f, tt))
            val path = Path().apply { addRoundRect(RoundRect(Rect(l, tp, r, b), rr(0), rr(1), rr(2), rr(3))) }
            clipPath(path) {
                drawRect(androidx.compose.ui.graphics.lerp(m.color, pal.s, tt))
                this@drawWithContent.drawContent()
            }
        }
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // isi catatan mulai muncul lebih awal dan bertumpang dengan teks kartu (pudar-silang), bukan menunggu kartu hilang dulu
                val x = ((vm.openP.value - .2f) / .5f).coerceIn(0f, 1f)
                val a = x * x * (3f - 2f * x)
                alpha = a
                translationY = (1f - a) * 24.dp.toPx()
                val s = .96f + .04f * a
                scaleX = s; scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)
            }
        ) { content() }
        // teks kartu ikut melebur: tetap di posisi aslinya di dalam wadah yang membesar/mengecil, lalu pudar seiring isi catatan muncul.
        // Saat menutup urutannya terbalik, jadi begitu wadah sampai di kartu, teksnya sudah identik dengan kartu asli (tidak ada lompatan).
        val morph = vm.morph
        val g = morph?.ghost
        val gn = if (g != null) vm.openId?.let { vm.note(it) } else null
        if (morph != null && g != null && gn != null) {
            val dn = androidx.compose.ui.platform.LocalDensity.current
            Box(
                Modifier.offset { vm.morphTick; val mm = vm.morph ?: morph; androidx.compose.ui.unit.IntOffset(mm.l.roundToInt(), mm.t.roundToInt()) }
                    .layout { meas, c ->
                        vm.morphTick
                        val mm = vm.morph ?: morph
                        val wpx = (mm.r - mm.l).roundToInt().coerceAtLeast(1)
                        val pl = meas.measure(c.copy(minWidth = wpx, maxWidth = wpx, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        layout(wpx, pl.height) { pl.place(0, 0) }
                    }
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer {
                        val x = (1f - (vm.openP.value - .02f) / .3f).coerceIn(0f, 1f)
                        alpha = x * x * (3f - 2f * x)
                    }
            ) { GhostCard(gn, g) }
        }
        if (u > .001f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (if (pal.dark) .16f else .05f) * u)))
    }
}

/**
 * Selama morph menutup, mengejar posisi kartu tujuan yang terkini (kartu bisa bergerak karena daftar digulir otomatis
 * atau karena catatan pindah urutan). Dikejar dengan peluruhan eksponensial supaya tidak melompat saat kartu baru muncul di layar.
 */
suspend fun followCard(vm: AppVM) {
    var last = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val dt = ((now - last) / 1e9f).coerceIn(0f, .05f)
        last = now
        val live = vm.openId?.let { vm.cardMorph[it] } ?: continue
        val cur = vm.morph ?: live
        val k = 1f - kotlin.math.exp(-dt * 14f)
        fun f(a: Float, b: Float) = a + (b - a) * k
        val r4a = cur.r4
        val r4b = live.r4
        val r4 = if (r4a != null && r4b != null) FloatArray(4) { f(r4a[it], r4b[it]) } else r4b ?: r4a
        val hadGhost = cur.ghost != null
        vm.morph = Morph(
            f(cur.l, live.l), f(cur.t, live.t), f(cur.r, live.r), f(cur.b, live.b), f(cur.rad, live.rad),
            androidx.compose.ui.graphics.lerp(cur.color, live.color, k), r4, live.ghost
        )
        if (!hadGhost && live.ghost != null) vm.ghostVer++
        vm.morphTick++
    }
}

@Composable
fun SnackHost(vm: AppVM, bottom: Dp, aboveKeyboard: Boolean) {
    val pal = LocalPal.current
    val sn = vm.snack
    var shown by remember { mutableStateOf<Snack?>(null) }
    LaunchedEffect(sn) {
        if (sn != null) {
            shown = sn
            delay(4200)
            if (vm.snack === sn) vm.snack = null
        }
    }
    Box(
        Modifier.fillMaxSize().then(if (aboveKeyboard) Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)) else Modifier)
            .padding(bottom = bottom, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = sn != null,
            enter = slideInVertically(spring(.7f, 400f)) { it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(250, easing = Emph)) { it * 2 } + fadeOut()
        ) {
            val s = shown
            if (s != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.os).padding(start = 22.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    T(s.msg, 15f, 600, pal.s, Modifier.weight(1f).padding(vertical = 18.dp))
                    if (s.undo != null) {
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp)).clickable { vm.snack = null; s.undo.invoke() }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) { T("Urungkan", 15f, 900, pal.pc) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppVM, act: Actions) {
    val nav = vm.nav
    val ctx = LocalContext.current
    // default: ikuti perangkat (Material You). Warna kustom dipakai hanya jika pengguna memilihnya, atau perangkat < Android 12.
    val dynOn = vm.pr.dyn && Build.VERSION.SDK_INT >= 31
    // mode "auto" = ikuti terang/gelap perangkat
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val effMode = if (vm.pr.mode == "auto") (if (sysDark) "dark" else "light") else vm.pr.mode
    val target = remember(effMode, vm.pr.seed, dynOn) {
        (if (dynOn) dynamicPal(ctx, effMode) else null) ?: buildPal(effMode, vm.pr.seed)
    }
    val anim = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target !== to) {
            from = lerpPal(from, to, anim.value)
            to = target
            anim.snapTo(0f)
            anim.animateTo(1f, tween(450))
        }
    }
    val pal = lerpPal(from, to, anim.value)
    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !pal.dark
        c.isAppearanceLightNavigationBars = !pal.dark
    }
    val cs = if (pal.dark) darkColorScheme(primary = pal.p, onPrimary = pal.op, surface = pal.s, onSurface = pal.os, background = pal.s)
    else lightColorScheme(primary = pal.p, onPrimary = pal.op, surface = pal.s, onSurface = pal.os, background = pal.s)

    val am = ctx.assets
    val ff = remember(vm.pr.ff) { loadFont(am, vm.pr.ff) }
    MaterialTheme(colorScheme = cs) {
        CompositionLocalProvider(
            LocalFF provides ff,
            LocalPal provides pal,
            LocalContentColor provides pal.os,
            LocalTextSelectionColors provides TextSelectionColors(handleColor = pal.p, backgroundColor = pal.pc)
        ) {
            val scope = rememberCoroutineScope()
            val drawer = rememberDrawerState(DrawerValue.Closed)
            var fabSmall by remember { mutableStateOf(false) }
            val top = nav.top
            val isHome = top is Scr.Home
            val isNote = top is Scr.Note
            // catatan tetap "terbuka" selama halaman titik tiga / label ada di atasnya
            val inNote = isNote || top is Scr.NoteMenu || top is Scr.NoteLabels
            val reading = vm.ed?.reading == true && isNote
            val imgSel = vm.ed?.selImg != null && isNote
            val selE = vm.ed?.selEdit == true && isNote
            val finding = vm.ed?.findOn == true && isNote
            val pageIds = remember { arrayOfNulls<String>(2) }
            (top as? Scr.NoteMenu)?.let { pageIds[0] = it.id }
            (top as? Scr.NoteLabels)?.let { pageIds[1] = it.id }

            BackHandler(enabled = nav.stack.size > 1 || drawer.isOpen || vm.addOn || vm.sel.isNotEmpty() || vm.searchOn || (isHome && vm.filter != null) || reading || imgSel || selE || finding) {
                when {
                    drawer.isOpen -> scope.launch { drawer.close() }
                    vm.addOn -> vm.addOn = false
                    isNote && finding -> vm.ed?.closeFind()
                    isHome && vm.sel.isNotEmpty() -> vm.sel.clear()
                    isHome && vm.searchOn -> vm.searchOn = false
                    isHome && vm.filter != null -> vm.filter = null
                    isNote && selE -> vm.ed?.clearSel()
                    isNote && reading -> vm.ed?.let { if (it.selOn) it.clearSel() else it.exitReading() }
                    isNote && imgSel -> vm.ed?.closeImg()
                    else -> nav.pop()
                }
            }

            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = isHome && vm.sel.isEmpty() && !vm.searchOn && !vm.addOn,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = .5f),
                drawerContent = {
                    val w = minOf(360.dp, (LocalConfiguration.current.screenWidthDp * .84f).dp)
                    ModalDrawerSheet(
                        modifier = Modifier.width(w), drawerContainerColor = pal.s, drawerShape = RectangleShape,
                        drawerTonalElevation = 0.dp, windowInsets = WindowInsets(0)
                    ) {
                        DrawerContent(
                            vm,
                            close = { scope.launch { drawer.close() } },
                            go = { sc -> scope.launch { drawer.close() }; nav.push(sc) }
                        )
                    }
                }
            ) {
                Box(Modifier.fillMaxSize().background(pal.s)) {
                    ViewLayer(
                        on = true, base = true, keep = true,
                        under = nav.stack.any { it is Scr.Settings || it is Scr.Archive || it is Scr.Trash }
                    ) {
                        HomeScreen(vm, nav, act, openDrawer = { scope.launch { drawer.open() } }, onSmall = { fabSmall = it })
                    }
                    ViewLayer(on = top is Scr.Archive || (inNote && nav.stack.any { it is Scr.Archive }), under = false) {
                        ListScreen(
                            vm, "Arsip", vm.archived(), "Catatan yang diarsipkan tidak muncul di beranda.", "Arsip kosong", false,
                            onBack = { nav.pop() }, onClick = { vm.sheet = Sheet.Arc(it.id) }
                        )
                    }
                    ViewLayer(on = top is Scr.Trash, under = false) {
                        ListScreen(
                            vm, "Baru saja dihapus", vm.trashed(), "Catatan di sini dihapus otomatis setelah 30 hari.", "Sampah kosong", true,
                            onBack = { nav.pop() }, onClick = { vm.sheet = Sheet.Trs(it.id) },
                            onClear = { vm.sheet = Sheet.EmptyTrash }
                        )
                    }
                    ViewLayer(on = top is Scr.Settings, under = false) {
                        SettingsScreen(vm, act, onBack = { nav.pop() })
                    }
                    AddScrim(vm)
                    NoteLayer(vm, on = inNote, morph = vm.morph, under = top is Scr.NoteMenu || top is Scr.NoteLabels) {
                        val id = nav.noteId
                        if (id != null) {
                            val sc = rememberCoroutineScope()
                            val ed = remember(id) {
                                Ed(vm, id, sc).also {
                                    if (nav.pendingTitle) { it.titleReq++; nav.pendingTitle = false }
                                }
                            }
                            SideEffect { vm.ed = ed }
                            NoteScreen(vm, ed, act)
                        }
                    }

                    // halaman titik tiga & label: satu halaman penuh bergaya Pengaturan, di atas editor
                    ViewLayer(on = top is Scr.NoteMenu, under = top is Scr.NoteLabels) {
                        pageIds[0]?.let { NoteMenuPage(vm, nav, act, it) }
                    }
                    ViewLayer(on = top is Scr.NoteLabels, under = false) {
                        pageIds[1]?.let { NoteLabelsPage(vm, nav, it) }
                    }

                    SelBar(vm)
                    Fab(
                        vm, small = fabSmall, hide = !isHome || vm.sel.isNotEmpty() || vm.searchOn || vm.addOn,
                        lift = if (vm.snack != null) 76.dp else 0.dp
                    ) { vm.addOn = true }
                    SnackHost(vm, if (inNote) 84.dp else 20.dp + sabDp(), inNote)
                    AddNoteLayer(vm, act)
                    SheetHost(vm, nav, act)
                    LabelDialogHost(vm)
                }
            }
        }
    }
}

class MainActivity : ComponentActivity(), Actions {
    private val vm: AppVM by viewModels()
    private var pendingMd: String = ""

    private val imgPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch {
            val imgs = uris.mapNotNull { vm.importImage(it) }
            if (imgs.isEmpty()) vm.show("Gambar gagal dimuat") else vm.ed?.addImages(imgs)
        }
    }
    private val mdPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch {
            val ns = vm.importMd(uris)
            if (ns.size == 1) vm.nav.openNote(ns[0].id, null)
            else vm.show(if (ns.isEmpty()) "Tidak ada berkas yang bisa dibaca" else "${ns.size} catatan diimpor")
        }
    }
    private val bkSaver = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { vm.writeBackup(it) }
                    true
                } catch (e: Exception) { false }
            }
            if (ok) { vm.markBackedUp(); vm.show("Cadangan tersimpan") } else vm.show("Gagal menyimpan cadangan")
        }
    }
    private val bkOpener = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            if (vm.stageBackup(uri)) vm.sheet = Sheet.Bk else vm.show("Berkas bukan cadangan Dinduy yang valid")
        }
    }
    private val mdSaver = registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(pendingMd.toByteArray(Charsets.UTF_8)) }
                vm.show("Berkas tersimpan")
            } catch (e: Exception) { vm.show("Gagal menyimpan berkas") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent { AppRoot(vm, this) }
    }

    override fun onStop() {
        super.onStop()
        vm.ed?.flush()
        vm.saveNow()
    }

    override fun pickImages() {
        imgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun importMd() {
        mdPicker.launch(arrayOf("text/markdown", "text/plain", "text/*", "application/octet-stream"))
    }

    override fun saveBackup() { bkSaver.launch(vm.backupName()) }

    override fun shareBackup() {
        lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                val file = File(cacheDir, vm.backupName())
                file.outputStream().use { vm.writeBackup(it) }
                file
            }
            vm.markBackedUp()
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Bagikan cadangan"))
        }
    }

    override fun restoreBackup() { bkOpener.launch(arrayOf("*/*")) }

    override fun saveMd(name: String, text: String) {
        pendingMd = text
        mdSaver.launch(name)
    }

    override fun shareMd(name: String, text: String) {
        lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                val file = File(cacheDir, name)
                file.writeText(text, Charsets.UTF_8)
                file
            }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name.removeSuffix(".md"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Bagikan catatan"))
        }
    }

    override fun copyText(t: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Dinduy", t))
    }

    override fun back() { vm.nav.pop() }
}

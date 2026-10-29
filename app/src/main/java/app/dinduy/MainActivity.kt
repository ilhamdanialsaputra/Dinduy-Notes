@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.dinduy

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
import kotlinx.coroutines.withContext
import java.io.File

sealed class Scr {
    object Home : Scr()
    class Note(val id: String) : Scr()
    object Settings : Scr()
    object Archive : Scr()
    object Trash : Scr()
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
                val live = vm.note(t.id)?.let { !it.a && it.dt == 0L } == true
                vm.morph = vm.cardMorph[t.id] ?: (if (live) vm.fabMorph else null)
            }
            stack.removeAt(stack.size - 1)
        }
    }

    fun openNote(id: String, m: Morph?, newNote: Boolean = false) {
        vm.morph = m
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
fun NoteLayer(on: Boolean, morph: Morph?, content: @Composable () -> Unit) {
    val pal = LocalPal.current
    // satu jalur saja: selalu morph kartu <-> layar penuh, tanpa geser/pudar UI lain
    val t by animateFloatAsState(if (on) 1f else 0f, spring(.85f, 260f), label = "nt")
    if (!on && t <= .002f) return
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { }.drawWithContent {
            val tt = t.coerceIn(0f, 1f)
            val m = morph ?: Morph(size.width * .12f, size.height * .4f, size.width * .88f, size.height * .6f, 32.dp.toPx(), pal.s)
            val l = lerp(m.l, 0f, tt)
            val tp = lerp(m.t, 0f, tt)
            val r = lerp(m.r, size.width, tt)
            val b = lerp(m.b, size.height, tt)
            val rad = lerp(m.rad, 0f, tt)
            val path = Path().apply { addRoundRect(RoundRect(Rect(l, tp, r, b), CornerRadius(rad, rad))) }
            clipPath(path) {
                drawRect(androidx.compose.ui.graphics.lerp(m.color, pal.s, tt))
                this@drawWithContent.drawContent()
            }
        }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((t - .4f) / .6f).coerceIn(0f, 1f) }) { content() }
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
    val target = remember(vm.pr.mode, vm.pr.seed) { buildPal(vm.pr.mode, vm.pr.seed) }
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

    MaterialTheme(colorScheme = cs) {
        CompositionLocalProvider(
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
            val reading = vm.ed?.reading == true && isNote

            BackHandler(enabled = nav.stack.size > 1 || drawer.isOpen || vm.sel.isNotEmpty() || vm.searchOn || reading) {
                when {
                    drawer.isOpen -> scope.launch { drawer.close() }
                    isHome && vm.sel.isNotEmpty() -> vm.sel.clear()
                    isHome && vm.searchOn -> vm.searchOn = false
                    isNote && reading -> vm.ed?.exitReading()
                    else -> nav.pop()
                }
            }

            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = isHome && vm.sel.isEmpty() && !vm.searchOn,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = .5f),
                drawerContent = {
                    val w = minOf(340.dp, (LocalConfiguration.current.screenWidthDp * .84f).dp)
                    ModalDrawerSheet(
                        modifier = Modifier.width(w), drawerContainerColor = pal.c3,
                        drawerShape = RoundedCornerShape(topEnd = 36.dp, bottomEnd = 36.dp),
                        windowInsets = WindowInsets(0)
                    ) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp)) {
                            T("Dinduy", 38f, 1000, pal.p, Modifier.padding(start = 12.dp, bottom = 24.dp), ls = -.02f)
                            Opt(1, "set", "Pengaturan") { scope.launch { drawer.close() }; nav.push(Scr.Settings) }
                            Opt(2, "arc", "Arsip", trailing = vm.archived().size.takeIf { it > 0 }?.toString()) {
                                scope.launch { drawer.close() }; nav.push(Scr.Archive)
                            }
                            Opt(3, "del", "Baru saja dihapus", trailing = vm.trashed().size.takeIf { it > 0 }?.toString()) {
                                scope.launch { drawer.close() }; nav.push(Scr.Trash)
                            }
                        }
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
                    ViewLayer(on = top is Scr.Archive || (isNote && nav.stack.any { it is Scr.Archive }), under = false) {
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
                    NoteLayer(on = isNote, morph = vm.morph) {
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

                    SelBar(vm)
                    Fab(
                        vm, small = fabSmall, hide = !isHome || vm.sel.isNotEmpty() || vm.searchOn,
                        lift = if (vm.snack != null) 76.dp else 0.dp
                    ) { vm.sheet = Sheet.Add }
                    SnackHost(vm, if (isNote) 84.dp else 20.dp + sabDp(), isNote)
                    SheetHost(vm, nav, act)
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

    override fun copyText(t: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Dinduy", t))
    }

    override fun back() { vm.nav.pop() }
}

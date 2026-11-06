@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.dinduy

import kotlin.math.roundToInt
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
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
    class PdfExport(val id: String) : Scr()    // halaman Ekspor PDF dari menu titik tiga
    class Cover(val id: String) : Scr()        // halaman sampul catatan (pilih & pangkas)
    object Models : Scr()                      // unduh model transkripsi
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
        vm.note(id)?.cv?.let { vm.prefetchImg(it) }   // sampul sudah ada di cache saat editor tampil (tanpa muncul belakangan)
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
    val t by animateFloatAsState(if (on || base) 1f else 0f, tween(340, easing = EmphDecel), label = "t")
    val u by animateFloatAsState(if (under) 1f else 0f, tween(340, easing = EmphDecel), label = "u")
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
            .background(pal.bg).pointerInput(Unit) { }
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
    val u by animateFloatAsState(if (under) 1f else 0f, tween(340, easing = EmphDecel), label = "nu")
    val gscope = rememberCoroutineScope()
    // satu progres bersama (vm.openP): morph kartu <-> layar penuh DAN kartu lain yang memberi ruang membaca nilai yang sama,
    // jadi selalu sinkron. Spring spatial M3 Expressive: membuka sedikit lentur, menutup lebih rapat (tanpa overshoot keluar layar).
    // Mode 1 kolom dan 2 kolom memakai satu animasi yang sama (container transform): wadah tumbuh dari kartu, isi catatan
    // ikut membesar dari dalam kartu. Hanya kartu "Tambah catatan" (lebar penuh) yang memakai gerak naik biasa.
    LaunchedEffect(on) {
        if (on) {
            vm.closing = false
            CloseFx.err = null
            // dibuka dengan tarikan jari dari FAB: progres dikendalikan jari, bukan animasi
            if (FabFx.drag) return@LaunchedEffect
            // spring cepat (kaku) dengan sedikit lentur: terasa snappy tapi tetap punya gerak
            vm.openP.animateTo(1f, spring(.8f, 620f))
        } else {
            val follow = vm.closing
            val job = if (follow) launch { followCard(vm) } else null
            vm.openP.animateTo(0f, if (follow) spring(.9f, 640f) else spring(.95f, 760f))
            job?.cancel()
            CloseFx.err = null
            vm.closing = false
        }
    }
    val t0 = vm.openP.value
    if (!on && t0 <= .01f) return
    vm.ghostVer
    Box(
        Modifier.fillMaxSize().graphicsLayer { translationX = -u * size.width * .26f }.pointerInput(Unit) { }
            // catatan masih kosong: seret ke bawah untuk menguncup kembali ke tombol FAB
            .pointerInput(on) {
                if (!on) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (vm.openP.value < .98f || !noteBlank(vm)) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    val dist = 320.dp.toPx()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var drag = false
                    var lp = 1f
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (c.changedToUpIgnoreConsumed()) break
                        tracker.addPosition(c.uptimeMillis, c.position)
                        val dx = c.position.x - down.position.x
                        val dy = c.position.y - down.position.y
                        if (!drag) {
                            if (dy < -slop || (abs(dx) > slop && abs(dx) > abs(dy))) return@awaitEachGesture
                            if (dy > slop) drag = true
                        }
                        if (drag) {
                            c.consume()
                            lp = (1f - (dy - slop).coerceAtLeast(0f) / dist).coerceIn(0f, 1f)
                            val v = lp
                            gscope.launch { vm.openP.snapTo(v) }
                        }
                    }
                    if (drag) {
                        val vy = tracker.calculateVelocity().y
                        if (lp < .82f || vy > 1000f) vm.nav.pop()
                        else gscope.launch { vm.openP.animateTo(1f, spring(.8f, 560f)) }
                    }
                }
            }.drawWithContent {
            val tt = vm.openP.value.coerceIn(0f, 1f)
            vm.morphTick
            val m = effMorph(vm) ?: Morph(size.width * .12f, size.height * .4f, size.width * .88f, size.height * .6f, 32.dp.toPx(), pal.s)
            // kartu (1 atau 2 kolom): semua tepi bergerak serempak supaya wadah tidak miring/melar;
            // selain kartu (Tambah catatan): tepi kiri-kanan sedikit lebih cepat dari atas-bawah
            val tx = if (m.card) tt else (tt * 1.12f).coerceIn(0f, 1f)
            val l = lerp(m.l, 0f, tx)
            val r = lerp(m.r, size.width, tx)
            val tp = lerp(m.t, 0f, tt)
            val b = lerp(m.b, size.height, tt)
            val r4 = m.r4 ?: FloatArray(4) { m.rad }
            fun rr(k: Int) = CornerRadius(lerp(r4[k], 0f, tt))
            val path = Path().apply { addRoundRect(RoundRect(Rect(l, tp, r, b), rr(0), rr(1), rr(2), rr(3))) }
            // latar di luar kartu meredup selama morph (ikut progres buka/tutup, jadi memudar balik saat ditutup)
            drawRect(Color.Black.copy(alpha = (if (pal.dark) .45f else .32f) * tt))
            clipPath(path) {
                drawRect(androidx.compose.ui.graphics.lerp(m.color, pal.s, tt))
                this@drawWithContent.drawContent()
            }
        }
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // isi catatan mulai muncul lebih awal dan bertumpang dengan teks kartu (pudar-silang), bukan menunggu kartu hilang dulu
                val tt = vm.openP.value.coerceIn(0f, 1f)
                vm.morphTick
                val mm = effMorph(vm)
                val x = ((vm.openP.value - .08f) / .4f).coerceIn(0f, 1f)
                val a = x * x * (3f - 2f * x)
                // morph dari kartu: NoteScreen memudarkan latar + isi sendiri (noteFade) supaya sampulnya tetap buram penuh
                alpha = if (mm != null && mm.card) 1f else a
                if (mm != null && mm.card) {
                    // kartu (1 atau 2 kolom): isi editor ikut mengecil ke dalam kartu lalu tumbuh bersama wadah (container transform);
                    // di 1 kolom kartunya lebar, jadi skalanya hanya sedikit di bawah 1 dan yang terasa adalah wadah yang memanjang
                    val l = lerp(mm.l, 0f, tt)
                    val r = lerp(mm.r, size.width, tt)
                    val tp = lerp(mm.t, 0f, tt)
                    val b = lerp(mm.b, size.height, tt)
                    // kartu "Tambah catatan": skala mengikuti tinggi wadah yang naik, isi tetap di tengah
                    val sc = (if (mm.up) (b - tp) / size.height else (r - l) / size.width).coerceIn(.05f, 1f)
                    scaleX = sc; scaleY = sc
                    translationX = if (mm.up) l + ((r - l) - sc * size.width) / 2f else l
                    translationY = tp
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                } else {
                    translationY = (1f - a) * 24.dp.toPx()
                    val s = .96f + .04f * a
                    scaleX = s; scaleY = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)
                }
            }
        ) { content() }
        // teks kartu ikut melebur: tetap di posisi aslinya di dalam wadah yang membesar/mengecil, lalu pudar seiring isi catatan muncul.
        // Saat menutup urutannya terbalik, jadi begitu wadah sampai di kartu, teksnya sudah identik dengan kartu asli (tidak ada lompatan).
        val morph = vm.morph
        val g = morph?.ghost
        val gn = if (g != null) vm.openId?.let { vm.note(it) } else null
        if (morph != null && g != null && gn != null) {
            Box(
                Modifier.layout { meas, c ->
                        vm.morphTick
                        val mm = effMorph(vm) ?: morph
                        val wpx = (mm.r - mm.l).roundToInt().coerceAtLeast(1)
                        val pl = meas.measure(c.copy(minWidth = wpx, maxWidth = wpx, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        layout(wpx, pl.height) { pl.place(0, 0) }
                    }
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer {
                        // teks menempel di sudut kiri-atas wadah yang sama dengan yang digambar di atas (rumus identik, fase draw yang sama),
                        // jadi bergerak serempak dengan bentuknya, bukan diam di posisi kartu lalu "menyusul"
                        val tt = vm.openP.value.coerceIn(0f, 1f)
                        vm.morphTick
                        val mm = effMorph(vm) ?: morph
                        translationX = lerp(mm.l, 0f, tt)
                        translationY = lerp(mm.t, 0f, tt)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        val x = (1f - (vm.openP.value - .02f) / .24f).coerceIn(0f, 1f)
                        alpha = x * x * (3f - 2f * x)
                    }
            ) { GhostCard(gn, g) }
        }
        if (u > .001f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (if (pal.dark) .16f else .05f) * u)))
    }
}

/**
 * Pudar isi catatan selama morph (kurva sama dengan yang dulu dipasang NoteLayer pada seluruh isi). Hanya untuk morph dari kartu;
 * selain itu NoteLayer sendiri yang memudarkan, jadi nilainya 1 supaya tidak memudar dua kali.
 */
fun noteFade(vm: AppVM): Float {
    val mm = effMorph(vm)
    if (mm == null || !mm.card) return 1f
    val x = ((vm.openP.value - .08f) / .4f).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Keadaan gestur tombol FAB (tarik ke atas untuk membuka catatan baru). */
object FabFx {
    var drag by mutableStateOf(false)
}

/** Catatan yang sedang terbuka belum berisi apa pun (syarat yang sama dengan dropIfEmpty). */
fun noteBlank(vm: AppVM): Boolean {
    if (vm.nav.top !is Scr.Note) return false
    val ed = vm.ed ?: return false
    if (ed.noteId != vm.nav.noteId) return false
    if (ed.sheet != 0 || ed.reading || ed.findOn || ed.selImg != null || ed.pickQuote || vm.rec.active) return false
    val n = vm.note(ed.noteId)
    return ed.title.isBlank() && plainOf(ed.blocks).isEmpty() &&
        ed.blocks.none { it.isObj && it.ty != "hr" } && ed.blocks.none { it.ik.isNotEmpty() } &&
        (n == null || (n.au.isEmpty() && n.rm == 0L && n.cv.isEmpty()))
}

/** Selisih posisi awal morph terhadap kartu tujuan; mengecil linear mengikuti progres menutup (tanpa pengejaran ekstra). */
object CloseFx {
    @JvmField var err: FloatArray? = null
}

/**
 * Posisi morph yang dipakai menggambar. Saat menutup dan kartu tujuan sedang tampil, bentuk = posisi kartu SAAT INI
 * (dibaca langsung di fase gambar, jadi tidak tertinggal satu frame walau daftar sedang bergulir) ditambah selisih awal
 * yang meluruh bersama openP. Hanya satu animasi (spring openP) yang menggerakkan bentuk, jadi mulus.
 */
fun effMorph(vm: AppVM): Morph? {
    val m = vm.morph ?: return null
    val e = CloseFx.err ?: return m
    val live = vm.openId?.let { vm.cardMorph[it] } ?: return m
    val tt = vm.openP.value.coerceIn(0f, 1f)
    return Morph(
        live.l + e[0] * tt, live.t + e[1] * tt, live.r + e[2] * tt, live.b + e[3] * tt,
        live.rad, live.color, live.r4, live.ghost, card = live.card, up = live.up
    )
}

/**
 * Selama morph menutup: begitu kartu tujuan tampil (mungkin setelah daftar digulir otomatis), catat selisihnya dari posisi
 * morph saat itu supaya tidak melompat, lalu hanya memicu gambar ulang tiap frame.
 */
suspend fun followCard(vm: AppVM) {
    CloseFx.err = null
    while (true) {
        withFrameNanos { }
        val live = vm.openId?.let { vm.cardMorph[it] }
        if (live != null && CloseFx.err == null) {
            val cur = vm.morph ?: live
            val k = vm.openP.value.coerceIn(.05f, 1f)
            CloseFx.err = floatArrayOf((cur.l - live.l) / k, (cur.t - live.t) / k, (cur.r - live.r) / k, (cur.b - live.b) / k)
            if (cur.ghost == null && live.ghost != null) vm.ghostVer++
        }
        effMorph(vm)?.let { vm.morph = it }
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
                        ) { T("Undo", 15f, 900, pal.pc) }
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
    val effMode = normMode(vm.pr.mode).let { if (it == "auto") (if (sysDark) "dark" else "light") else it }
    val target = remember(effMode, vm.pr.seed, dynOn, vm.pr.mono) {
        val base = (if (dynOn) dynamicPal(ctx, effMode) else null) ?: buildPal(effMode, vm.pr.seed)
        if (vm.pr.mono) base.toMono() else base
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
    // setelah animasi selesai pakai instance yang SAMA (stabil): tanpa ini setiap recompose AppRoot membuat Pal baru
    // sehingga SELURUH layar yang membaca LocalPal ikut recompose
    val av = anim.value
    val pal = if (av >= 1f) to else lerpPal(from, to, av)
    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !pal.dark
        c.isAppearanceLightNavigationBars = !pal.dark
    }
    val cs = remember(pal) {
        if (pal.dark) darkColorScheme(primary = pal.p, onPrimary = pal.op, surface = pal.s, onSurface = pal.os, background = pal.bg)
        else lightColorScheme(primary = pal.p, onPrimary = pal.op, surface = pal.s, onSurface = pal.os, background = pal.bg)
    }

    val am = ctx.assets
    val ff = remember(vm.pr.ff) { loadFont(am, vm.pr.ff) }
    MaterialTheme(colorScheme = cs) {
        CompositionLocalProvider(
            LocalFF provides ff,
            LocalVM provides vm,
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
            val modelsOnNote = top is Scr.Models && nav.stack.any { it is Scr.Note }
            val modelsOnSettings = top is Scr.Models && !modelsOnNote
            val inNote = isNote || top is Scr.NoteMenu || top is Scr.NoteLabels || top is Scr.PdfExport || top is Scr.Cover || modelsOnNote
            val reading = vm.ed?.reading == true && isNote
            val imgSel = vm.ed?.selImg != null && isNote
            val selE = vm.ed?.selEdit == true && isNote
            val finding = vm.ed?.findOn == true && isNote
            val menuKept = nav.stack.any { it is Scr.NoteMenu }   // halaman titik tiga tetap 'hidup' selama halaman anak (label/PDF/sampul) menumpuk di atasnya
            val pageIds = remember { arrayOfNulls<String>(4) }
            (top as? Scr.NoteMenu)?.let { pageIds[0] = it.id }
            (top as? Scr.NoteLabels)?.let { pageIds[1] = it.id }
            (top as? Scr.PdfExport)?.let { pageIds[2] = it.id }
            (top as? Scr.Cover)?.let { pageIds[3] = it.id }

            BackHandler(enabled = nav.stack.size > 1 || drawer.isOpen || vm.addOn || vm.sel.isNotEmpty() || vm.searchOn || (isHome && (vm.filter != null || vm.remFilter)) || reading || imgSel || selE || finding) {
                when {
                    drawer.isOpen -> scope.launch { drawer.close() }
                    vm.addOn -> vm.addOn = false
                    isNote && finding -> vm.ed?.closeFind()
                    isHome && vm.sel.isNotEmpty() -> vm.sel.clear()
                    isHome && vm.searchOn -> vm.searchOn = false
                    isHome && vm.filter != null -> vm.filter = null
                    isHome && vm.remFilter -> vm.remFilter = false
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
                Box(Modifier.fillMaxSize().background(pal.bg)) {
                    ViewLayer(
                        on = true, base = true, keep = true,
                        under = nav.stack.any { it is Scr.Settings || it is Scr.Archive || it is Scr.Trash }
                    ) {
                        HomeScreen(vm, nav, act, openDrawer = { scope.launch { drawer.open() } }, onSmall = { fabSmall = it })
                    }
                    ViewLayer(on = top is Scr.Archive || (inNote && nav.stack.any { it is Scr.Archive }), under = false) {
                        ListScreen(
                            vm, "Archive", vm.archived(), "Archived notes don't show on the home screen.", "Archive is empty", false,
                            onBack = { nav.pop() }, onClick = { vm.sheet = Sheet.Arc(it.id) }
                        )
                    }
                    ViewLayer(on = top is Scr.Trash, under = false) {
                        ListScreen(
                            vm, "Trash", vm.trashed(), "Notes here are deleted after 30 days.", "Trash is empty", true,
                            onBack = { nav.pop() }, onClick = { vm.sheet = Sheet.Trs(it.id) },
                            onClear = { vm.sheet = Sheet.EmptyTrash }
                        )
                    }
                    ViewLayer(on = top is Scr.Settings || modelsOnSettings, under = modelsOnSettings) {
                        SettingsScreen(vm, act, onBack = { nav.pop() })
                    }
                    AddScrim(vm)
                    NoteLayer(vm, on = inNote, morph = vm.morph, under = top is Scr.NoteMenu || top is Scr.NoteLabels || top is Scr.PdfExport || top is Scr.Cover || modelsOnNote) {
                        val id = nav.noteId
                        if (id != null && vm.note(id)?.pdf?.isNotEmpty() == true) {
                            SideEffect { vm.ed = null }
                            PdfScreen(vm, id, act)
                        } else if (id != null) {
                            val sc = rememberCoroutineScope()
                            val ed = remember(id) {
                                Ed(vm, id, sc).also {
                                    if (nav.pendingTitle) { it.focusAt(it.blocks[0].id, 0); nav.pendingTitle = false }
                                }
                            }
                            androidx.compose.runtime.DisposableEffect(ed) {
                                ed.alive = true
                                onDispose { ed.alive = false }
                            }
                            SideEffect { vm.ed = ed }
                            NoteScreen(vm, ed, act)
                        }
                    }

                    // halaman titik tiga & label: satu halaman penuh bergaya Pengaturan, di atas editor
                    ViewLayer(on = menuKept, under = menuKept && (top is Scr.NoteLabels || top is Scr.PdfExport || top is Scr.Cover || top is Scr.Models)) {
                        pageIds[0]?.let { if (vm.note(it)?.pdf?.isNotEmpty() == true) PdfMenuPage(vm, nav, act, it) else NoteMenuPage(vm, nav, act, it) }
                    }
                    ViewLayer(on = top is Scr.NoteLabels, under = false) {
                        pageIds[1]?.let { NoteLabelsPage(vm, nav, it) }
                    }
                    ViewLayer(on = top is Scr.PdfExport, under = false) {
                        pageIds[2]?.let { PdfExportPage(vm, nav, act, it) }
                    }
                    ViewLayer(on = top is Scr.Cover, under = false) {
                        pageIds[3]?.let { CoverPage(vm, nav, act, it) }
                    }
                    // halaman transkripsi: digambar PALING ATAS (di atas editor), kalau tidak tersembunyi di balik lapisan catatan
                    ViewLayer(on = top is Scr.Models, under = false) {
                        ModelsScreen(vm, act, onBack = { nav.pop() })
                    }

                    PhotoLayer(vm)
                    androidx.compose.runtime.LaunchedEffect(vm.importTick) { if (vm.importTick > 0) act.importMd() }

                    SelBar(vm)
                    Fab(
                        vm, small = fabSmall,
                        hide = (!isHome && !FabFx.drag) || vm.sel.isNotEmpty() || vm.searchOn || vm.addOn || vm.remFilter,
                        lift = if (vm.snack != null) 92.dp else 0.dp
                    )
                    SnackHost(vm, if (inNote) 84.dp else 20.dp + sabDp(), inNote)
                    AddNoteLayer(vm, act)
                    SheetHost(vm, nav, act)
                    ReminderHost(vm, act)
                    LabelDialogHost(vm)
                    ModelDialogHost(vm)
                }
            }
        }
    }
}

class MainActivity : ComponentActivity(), Actions {
    private val vm: AppVM by viewModels()
    private var pendingMd: String = ""
    private var pendingPdf: File? = null

    // ---- galeri ----
    // Tujuan pilihan dicatat di SharedPreferences sebelum galeri dibuka. Selama galeri terbuka, Activity bahkan prosesnya boleh dimatikan
    // sistem; kalau tujuan hanya disimpan di variabel Activity / editor, hasilnya tiba tanpa tahu mau dipasang ke mana dan diam-diam hilang.
    private class PickTarget(val kind: String, val noteId: String, val bid: String = "", val ix: Int = 0)

    private fun setPick(t: PickTarget?) {
        getSharedPreferences("dinduy", 0).edit().apply {
            if (t == null) remove("pk") else putString("pk", listOf(t.kind, t.noteId, t.bid, t.ix.toString()).joinToString("|"))
        }.apply()
    }

    private fun takePick(): PickTarget? {
        val sp = getSharedPreferences("dinduy", 0)
        val raw = sp.getString("pk", null)
        sp.edit().remove("pk").apply()
        val p = raw?.split("|") ?: return null
        if (p.size < 4) return null
        return PickTarget(p[0], p[1], p[2], p[3].toIntOrNull() ?: 0)
    }

    private fun importFailMsg(what: String): String = what + (vm.lastImgErr.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")

    private inline fun openPicker(f: () -> Unit) {
        try { f() } catch (e: Throwable) {
            setPick(null)
            vm.show("Can't open the gallery (" + e.javaClass.simpleName + ")")
        }
    }

    /** Pastikan catatan [id] terbuka (setelah proses dimatikan sistem, tumpukan layar kembali ke beranda). */
    private fun ensureNote(id: String) {
        if (vm.nav.stack.none { it is Scr.Note && it.id == id }) vm.nav.openNote(id, vm.cardMorph[id])
    }

    /** Serahkan gambar ke editor yang hidup; kalau editornya sudah tidak ada, tulis langsung ke data catatan lalu buka catatannya. */
    private fun deliver(noteId: String, imgs: List<Img>, rep: Pair<String, Int>?) {
        val ed = vm.ed?.takeIf { it.alive && it.noteId == noteId }
        if (ed != null) {
            if (rep != null) ed.replaceImgAt(rep.first, rep.second, imgs[0]) else ed.addImages(imgs)
            return
        }
        val ok = if (rep != null) vm.replaceImgData(noteId, rep.first, rep.second, imgs[0])
        else if (vm.note(noteId) != null) { vm.appendImages(noteId, imgs); true } else false
        if (!ok) { vm.show("Note not found, so the image wasn't added"); return }
        ensureNote(noteId)
    }

    // sisipkan gambar (bisa banyak): masuk di posisi kursor / setelah gambar terpilih
    private val imgPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        val t = takePick()
        if (uris.isEmpty()) return@registerForActivityResult
        val nid = t?.noteId?.takeIf { it.isNotEmpty() } ?: vm.nav.noteId ?: vm.ed?.noteId
        lifecycleScope.launch {
            val imgs = uris.mapNotNull { vm.importImage(it) }
            when {
                imgs.isEmpty() -> vm.show(importFailMsg("Couldn't load the image"))
                nid == null -> vm.show("Note not found, so the image wasn't inserted")
                else -> deliver(nid, imgs, null)
            }
        }
    }
    // ganti foto: satu gambar dari galeri mengisi bingkai gambar yang dicatat di PickTarget (ukuran bingkai tetap)
    private val replPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val t = takePick()
        if (uri == null || t == null || t.kind != "rep") {
            vm.ed?.replT = null
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val img = vm.importImage(uri)
            if (img == null) vm.show(importFailMsg("Couldn't load the image")) else deliver(t.noteId, listOf(img), t.bid to t.ix)
        }
    }

    // sampul catatan: satu gambar dari galeri; langsung dipasang lalu halaman pangkas dibuka
    private var coverFor: String? = null
    private val coverPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val t = takePick()
        val id = t?.takeIf { it.kind == "cov" }?.noteId ?: coverFor
        coverFor = null
        if (uri == null || id.isNullOrEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            val img = vm.importImage(uri)
            when {
                img == null -> vm.show(importFailMsg("Couldn't load the image"))
                vm.note(id) == null -> vm.show("Note not found, so the cover wasn't set")
                else -> {
                    vm.setCover(id, img.id)
                    ensureNote(id)
                    val tp = vm.nav.top
                    if (!(tp is Scr.Cover && tp.id == id)) vm.nav.push(Scr.Cover(id))
                }
            }
        }
    }

    // kamera: izin diminta saat dipakai; fotonya ditulis ke cache, diperkecil seperti gambar galeri, lalu langsung jadi gambar di catatan
    private var camCb: (() -> Unit)? = null
    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val cb = camCb
        camCb = null
        if (ok) cb?.invoke() else vm.show("Camera permission denied. Can't take a photo.")
    }
    private var camFile: File? = null
    private var camRepl = false
    private val camShot = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val sp = getSharedPreferences("dinduy", 0)
        // kalau proses sempat dimatikan sistem selama kamera terbuka, jalur berkas diambil dari penyimpanan kecil
        val f = camFile ?: sp.getString("cf", null)?.let { File(it) }
        val repl = camRepl
        camFile = null
        camRepl = false
        sp.edit().remove("cf").apply()
        if (!ok || f == null || !f.exists() || f.length() == 0L) {
            f?.delete()
            if (repl) vm.ed?.replT = null
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val img = vm.importImage(FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f))
            withContext(Dispatchers.IO) { f.delete() }
            val ed = vm.ed
            when {
                img == null -> vm.show("Couldn't load the photo")
                ed == null -> vm.show("No note is open, so the photo wasn't added")
                repl -> ed.replaceImg(img)
                else -> ed.addImages(listOf(img))
            }
        }
    }
    private val mdPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch {
            val pdfs = uris.filter { isPdfUri(this@MainActivity, it) }
            val ns = vm.importPdfs(pdfs) + vm.importMd(uris - pdfs.toSet())
            if (ns.size == 1) vm.nav.openNote(ns[0].id, null)
            else vm.show(if (ns.isEmpty()) "No readable files" else "Imported ${cnt(ns.size, "note")}")
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
            if (ok) { vm.markBackedUp(); vm.show("Backup saved") } else vm.show("Couldn't save the backup")
        }
    }
    private val bkOpener = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            if (vm.stageBackup(uri)) vm.sheet = Sheet.Bk else vm.show("This isn't a valid Dinduy backup")
        }
    }
    private var micCb: (() -> Unit)? = null
    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val cb = micCb
        micCb = null
        if (ok) cb?.invoke() else vm.show("Microphone permission denied. Can't record.")
    }
    private var pendingAud: Aud? = null
    private val audSaver = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri ->
        val a = pendingAud
        pendingAud = null
        if (uri != null && a != null) lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val src = AudioFiles.file(this@MainActivity, a.id)
                    contentResolver.openOutputStream(uri)?.use { os -> src.inputStream().use { it.copyTo(os) } }
                    true
                } catch (e: Exception) { false }
            }
            vm.show(if (ok) "Audio saved" else "Couldn't save the audio")
        }
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.permTick++
        if (!ok) vm.show("Notification permission denied. Reminders won't show.")
    }
    private val mdSaver = registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(pendingMd.toByteArray(Charsets.UTF_8)) }
                vm.show("File saved")
            } catch (e: Exception) { vm.show("Couldn't save the file") }
        }
    }

    private val pdfSaver = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val src = pendingPdf
        if (uri != null && src != null) {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } }
                        true
                    } catch (e: Exception) { false }
                }
                vm.show(if (ok) "PDF saved" else "Couldn't save the PDF")
            }
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
        Rem.ensureChannel(this)
        // minta izin notifikasi sekali saat pertama kali aplikasi dibuka (tanpa mengarahkan ke Pengaturan)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33) {
            val sp0 = getSharedPreferences("dinduy", 0)
            if (!sp0.getBoolean("np", false) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                sp0.edit().putBoolean("np", true).apply()
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        openFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.permTick++   // status izin notifikasi bisa berubah setelah pengguna kembali dari Pengaturan
    }

    /** Ketuk notifikasi pengingat: buka catatannya (menutup halaman lain dulu bila perlu). */
    private fun openFromIntent(i: Intent?) {
        // PDF dari aplikasi lain: "Buka dengan Dinduy" (VIEW) atau Bagikan (SEND / SEND_MULTIPLE)
        if (i != null && (i.action == Intent.ACTION_VIEW || i.action == Intent.ACTION_SEND || i.action == Intent.ACTION_SEND_MULTIPLE)) {
            val us = ArrayList<Uri>()
            @Suppress("DEPRECATION")
            when (i.action) {
                Intent.ACTION_VIEW -> i.data?.let { us.add(it) }
                Intent.ACTION_SEND -> i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { us.add(it) }
                else -> i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { us.addAll(it) }
            }
            if (us.isEmpty()) i.clipData?.let { cd -> for (k in 0 until cd.itemCount) cd.getItemAt(k).uri?.let { us.add(it) } }
            i.action = Intent.ACTION_MAIN
            i.data = null
            i.removeExtra(Intent.EXTRA_STREAM)
            if (us.isNotEmpty()) lifecycleScope.launch {
                val ns = vm.importPdfs(us)
                vm.remDlg = null; vm.sheet = null; vm.addOn = false
                if (vm.nav.stack.size > 1) {
                    while (vm.nav.stack.size > 1) vm.nav.pop()
                    delay(380)
                }
                if (ns.size == 1) vm.nav.openNote(ns[0].id, null)
                else vm.show(if (ns.isEmpty()) "Couldn't open this PDF" else "Imported ${cnt(ns.size, "PDF")}")
            }
            return
        }
        val id = i?.getStringExtra(Rem.EXTRA_NOTE) ?: return
        i.removeExtra(Rem.EXTRA_NOTE)
        if (vm.note(id) == null) return
        val top = vm.nav.top
        if (top is Scr.Note && top.id == id) return
        lifecycleScope.launch {
            vm.remDlg = null
            vm.sheet = null
            vm.addOn = false
            if (vm.nav.stack.size > 1) {
                while (vm.nav.stack.size > 1) vm.nav.pop()
                delay(380)
            }
            vm.remFilter = false
            vm.nav.openNote(id, vm.cardMorph[id])
        }
    }

    override fun askNotif() {
        val sp = getSharedPreferences("dinduy", 0)
        val asked = sp.getBoolean("np", false)
        val canAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            (!asked || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        if (canAsk) {
            sp.edit().putBoolean("np", true).apply()
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // sudah ditolak permanen (atau dimatikan di Pengaturan): arahkan ke halaman notifikasi aplikasi
            try {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Bg.fg = true
    }

    override fun onStop() {
        super.onStop()
        Bg.fg = false
        vm.player.pause()
        if (vm.rec.active && !Bg.running) vm.stopRec()   // rekaman lanjut di latar belakang lewat layanan; kalau layanan tak jalan, simpan yang sudah direkam
        vm.ed?.flush()
        vm.saveNow()
    }

    override fun requestMic(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onGranted()
            return
        }
        val sp = getSharedPreferences("dinduy", 0)
        val asked = sp.getBoolean("mp", false)
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // ditolak permanen: arahkan ke Pengaturan aplikasi
            vm.show("Microphone permission is off. Turn it on in app settings.")
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e: Exception) { }
            return
        }
        sp.edit().putBoolean("mp", true).apply()
        micCb = onGranted
        micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestCamera(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onGranted()
            return
        }
        val sp = getSharedPreferences("dinduy", 0)
        val asked = sp.getBoolean("cp", false)
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // ditolak permanen: arahkan ke Pengaturan aplikasi
            vm.show("Camera permission is off. Turn it on in app settings.")
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e: Exception) { }
            return
        }
        sp.edit().putBoolean("cp", true).apply()
        camCb = onGranted
        camPerm.launch(Manifest.permission.CAMERA)
    }

    override fun takePhoto(replace: Boolean) {
        requestCamera {
            try {
                cacheDir.listFiles { f -> f.name.startsWith("cam_") }?.forEach { it.delete() }   // sisa foto lama yang tak sempat diproses
                val f = File(cacheDir, "cam_" + System.currentTimeMillis() + ".jpg")
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                camFile = f
                camRepl = replace
                getSharedPreferences("dinduy", 0).edit().putString("cf", f.absolutePath).apply()
                camShot.launch(uri)
            } catch (e: Exception) {
                camFile = null
                vm.show("Can't open the camera")
            }
        }
    }

    override fun pickCover(id: String) {
        coverFor = id
        setPick(PickTarget("cov", id))
        openPicker { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }

    override fun replaceImage(noteId: String, bid: String, ix: Int) {
        setPick(PickTarget("rep", noteId, bid, ix))
        openPicker { replPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }

    override fun saveAudio(aud: Aud, name: String) {
        pendingAud = aud
        audSaver.launch(name)
    }

    override fun pickImages() {
        setPick(PickTarget("ins", vm.nav.noteId ?: vm.ed?.noteId ?: ""))
        openPicker { imgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }

    override fun importMd() {
        mdPicker.launch(arrayOf("application/pdf", "text/markdown", "text/plain", "text/*", "application/octet-stream"))
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
            startActivity(Intent.createChooser(i, "Share backup"))
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
            startActivity(Intent.createChooser(i, "Share note"))
        }
    }

    override fun savePdf(name: String, file: File) {
        pendingPdf = file
        pdfSaver.launch(name)
    }

    override fun sharePdf(name: String, file: File) {
        lifecycleScope.launch {
            // salin ke cache dengan nama catatan supaya penerima melihat nama yang rapi
            val f = withContext(Dispatchers.IO) {
                val out = File(cacheDir, name)
                file.copyTo(out, overwrite = true)
                out
            }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name.removeSuffix(".pdf"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Share PDF"))
        }
    }

    override fun copyText(t: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Dinduy", t))
    }

    override fun back() { vm.nav.pop() }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.dinduy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// =====================================================================================================
// Voice note cepat dari tombol "+":
//   geser tombol ke kiri -> tombol melebar jadi bilah rekam (rekaman langsung jalan)
//   bilah: ketuk tombol di kanan = selesai (catatan baru muncul di beranda), geser ke kanan = tutup (rekaman dibuang),
//          geser ke atas = bilah mekar jadi catatan terbuka dengan rekaman tetap jalan
//   catatan yang masih kosong: seret ke bawah = kembali menguncup jadi bilah rekam
// =====================================================================================================
object QuickFx {
    /** Id sementara selama rekaman belum punya catatan (catatan baru dibuat saat selesai). */
    const val QID = "__quick__"

    val p = Animatable(0f)                         // 0 = tombol +, 1 = bilah terbuka penuh
    var drag by mutableStateOf(false)              // jari sedang menggerakkan bentuk bilah
    var shown by mutableStateOf(false)             // sesi rekaman cepat berjalan (di bilah atau di catatan)
    var noteBound by mutableStateOf(false)         // rekaman sedang menempel di catatan terbuka, bilah disembunyikan
    var closeTick by mutableIntStateOf(0)          // dinaikkan agar bilah menguncup kembali ke tombol +
    var lastMs by mutableStateOf(0L)               // waktu terakhir, ditampilkan selama bilah menguncup
    @JvmField var starting = false                 // perekam sedang disiapkan di thread IO (bilah sudah mekar)
    @JvmField var returning = false                // catatan kosong menguncup balik ke bilah (bukan ke tombol +)
    @JvmField var barMorph: Morph? = null          // posisi bilah di layar, titik awal/akhir morph catatan
}

fun micOk(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Mulai merekam. Belum ada catatan: id sementara dipakai sampai selesai. */
/**
 * Versi tanpa memblokir main thread (dipakai gesture geser FAB): perekam disiapkan di thread IO sementara bilah terus mekar.
 * Gagal = false; pemanggil yang menguncupkan bilah kembali.
 */
suspend fun quickStartAsync(vm: AppVM, quiet: Boolean = false): Boolean {
    if (vm.rec.active || QuickFx.starting) return false
    QuickFx.starting = true
    try {
        val ok = vm.startRecAsync(QuickFx.QID, quiet)
        if (ok) { QuickFx.shown = true; QuickFx.lastMs = 0L }
        return ok
    } finally {
        QuickFx.starting = false
    }
}

fun quickStart(vm: AppVM, quiet: Boolean = false): Boolean {
    if (vm.rec.active) return false
    val ok = vm.startRec(QuickFx.QID, quiet)
    if (ok) { QuickFx.shown = true; QuickFx.lastMs = 0L }
    return ok
}

/** Selesai: simpan rekaman sebagai catatan baru di beranda, bilah menguncup jadi tombol +. */
fun quickFinish(vm: AppVM) {
    val a = vm.rec.finish()
    QuickFx.shown = false
    QuickFx.noteBound = false
    QuickFx.closeTick++
    if (a == null) {
        vm.show("Recording was too short or failed")
        return
    }
    val n = vm.newNote()
    vm.upd(n.id) { it.copy(au = listOf(a), u = System.currentTimeMillis()) }
    vm.audVer++
    vm.revealId = n.id
    vm.revealTick++
    vm.show("Voice note saved")
}

/** Tutup bilah: rekaman dibuang. */
fun quickCancel(vm: AppVM) {
    vm.cancelRec()
    QuickFx.shown = false
    QuickFx.noteBound = false
    QuickFx.closeTick++
}

/** Catatan kosong diseret turun saat rekaman cepat berjalan: rekaman dikembalikan ke bilah, catatan kosongnya dibuang. */
fun quickCollapseNote(vm: AppVM) {
    if (QuickFx.noteBound && vm.rec.active) {
        vm.rec.rebind(QuickFx.QID)
        QuickFx.returning = true
    }
}

private fun barAlpha(vm: AppVM): Float {
    if (QuickFx.noteBound) return (1f - vm.openP.value * 3f).coerceIn(0f, 1f)
    return if (vm.nav.top is Scr.Home) 1f else 0f
}

@Composable
private fun QTimer(vm: AppVM) {
    val pal = LocalPal.current
    val live = vm.rec.active
    val ms = if (live) vm.rec.ms else QuickFx.lastMs
    SideEffect { if (live) QuickFx.lastMs = ms }
    T(fmtTime(ms), 24f, 900, pal.opc, maxLines = 1)
}

private fun easeOutBack(t: Float): Float {
    val x = t.coerceIn(0f, 1f) - 1f
    return 1f + 2.70158f * x * x * x + 1.70158f * x * x
}

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Isi bilah. SEMUA gerak di sini hanya fungsi dari satu progres [QuickFx.p] (bukan timer sendiri-sendiri), jadi isi selalu sinkron
 * dengan bentuk wadah dan tidak pernah ada momen "kosong" di tengah morph:
 *   0.00-0.24  tombol + berputar 90 derajat sambil mengecil
 *   0.10-0.55  tombol stop tumbuh dari titik nol dengan pantulan kecil (menggantikan +)
 *   0.28-0.78  titik rekam, waktu, dan gelombang meluncur masuk dari arah tombol
 */
@Composable
private fun QuickVoiceBody(vm: AppVM, fabWdp: Dp, btnDown: Boolean) {
    val pal = LocalPal.current
    val tr = rememberInfiniteTransition(label = "qp")
    val halo by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearOutSlowInEasing)), label = "qh")
    val rc = if (pal.mono) pal.opc else Color(0xFFE5484D)
    // tombol stop: lingkaran saat diam, menyempit lebih kotak saat ditekan (bahasa bentuk yang sama dengan toolbar lain)
    val bR by animateDpAsState(if (btnDown) 20.dp else 30.dp, M3E.spatialFast(), label = "qbr")
    val bS by animateFloatAsState(if (btnDown) .9f else 1f, spring(.45f, 700f), label = "qbs")
    // gelombang meluncur halus antar sampel (sampel baru tiap 50 ms), bukan melompat
    val frac = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var ref: List<Float>? = null
        var t0 = 0L
        while (true) {
            withFrameNanos { now ->
                val a = vm.rec.amps
                if (a !== ref) { ref = a; t0 = now }
                frac.floatValue = ((now - t0) / 50_000_000f).coerceIn(0f, 1f)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        // isi tombol + (sama persis dengan FAB, menempel di sisi kanan)
        Row(
            Modifier.align(Alignment.CenterEnd).width(fabWdp).fillMaxHeight().padding(horizontal = 28.dp)
                .graphicsLayer { alpha = (1f - QuickFx.p.value / .24f).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.graphicsLayer {
                    val t = (QuickFx.p.value / .24f).coerceIn(0f, 1f)
                    rotationZ = 90f * t
                    val s = 1f - .4f * t
                    scaleX = s; scaleY = s
                }
            ) { Ic("plus", pal.opc, 36.dp) }
            if (fabWdp > 100.dp) {
                Spacer(Modifier.width(14.dp))
                T("New", 22f, 900, pal.opc, maxLines = 1)
            }
        }
        // isi bilah: titik rekam berhalo, waktu, gelombang suara
        Row(
            Modifier.fillMaxSize().padding(start = 22.dp, end = 96.dp)
                .graphicsLayer {
                    val t = smooth((QuickFx.p.value - .28f) / .5f)
                    alpha = t
                    translationX = (1f - t) * 48.dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp), Alignment.Center) {
                Box(
                    Modifier.size(34.dp).graphicsLayer {
                        val sc = .4f + .6f * halo
                        scaleX = sc; scaleY = sc
                        alpha = (1f - halo) * .45f
                    }.clip(CircleShape).background(rc)
                )
                Box(Modifier.size(14.dp).clip(CircleShape).background(rc))
            }
            Spacer(Modifier.width(8.dp))
            QTimer(vm)
            Spacer(Modifier.width(14.dp))
            Canvas(Modifier.weight(1f).height(48.dp).clipToBounds()) {
                val bw = 4.dp.toPx()
                val step = bw + 3.dp.toPx()
                val minH = 6.dp.toPx()
                val fade = 56.dp.toPx()
                val list = vm.rec.amps
                val f = frac.floatValue
                val n = (size.width / step).toInt() + 3
                for (k in 0 until n) {
                    val idx = list.size - 1 - k
                    val x = size.width - bw - (k - 1 + f) * step
                    if (x + bw < 0f || x > size.width) continue
                    val a = if (idx >= 0) {
                        val cur = list[idx]
                        (list.getOrElse(idx - 1) { cur } + 2f * cur + list.getOrElse(idx + 1) { cur }) / 4f
                    } else 0f
                    val h = minH + a * (size.height - minH)
                    // makin ke kiri makin pudar; sebelum ada sampel: titik-titik redup sebagai garis dasar
                    val al = (x / fade).coerceIn(0f, 1f) * (if (idx >= 0) 1f else .4f)
                    drawRoundRect(
                        pal.opc.copy(alpha = al), Offset(x, (size.height - h) / 2f), Size(bw, h), CornerRadius(bw / 2f)
                    )
                }
            }
        }
        // tombol selesai di kanan: tumbuh dari titik nol menggantikan +, lalu menjadi kotak stop
        Box(
            Modifier.align(Alignment.CenterEnd).size(88.dp).graphicsLayer {
                val s = easeOutBack((QuickFx.p.value - .10f) / .45f).coerceAtLeast(0f) * bS
                scaleX = s; scaleY = s
            },
            Alignment.Center
        ) {
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(bR.nn())).background(pal.p), Alignment.Center) {
                Box(
                    Modifier.graphicsLayer {
                        val t = smooth((QuickFx.p.value - .45f) / .3f)
                        alpha = t
                        val s = .5f + .5f * t
                        scaleX = s; scaleY = s
                    }
                ) { Ic("stop", pal.op, 28.dp) }
            }
        }
    }
}

/**
 * Bilah voice note cepat. Digambar di atas tombol +, bentuknya = tombol + saat p = 0 dan melebar ke kiri sampai p = 1.
 * Selama tidak dipakai komposabel ini tidak menggambar apa pun dan tidak menangkap sentuhan.
 */
@Composable
fun QuickVoice(vm: AppVM, lift: Dp) {
    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dens = LocalDensity.current
    val sab = sabDp()
    val rec = vm.rec
    var btnDown by remember { mutableStateOf(false) }   // jari sedang menahan tombol stop (bentuknya ikut menyempit)

    // bilah menguncup kembali ke tombol +
    LaunchedEffect(QuickFx.closeTick) {
        if (QuickFx.closeTick > 0 && !QuickFx.drag) QuickFx.p.animateTo(0f, spring(1f, 520f))
    }
    // rekaman berakhir di tempat lain (dihentikan dari editor / notifikasi / aplikasi ditutup): bersihkan
    LaunchedEffect(rec.active) {
        if (!rec.active && (QuickFx.shown || QuickFx.noteBound) && !QuickFx.drag) {
            QuickFx.shown = false
            QuickFx.noteBound = false
            QuickFx.p.snapTo(0f)
        }
    }
    // setelah catatan menguncup kembali (atau batal dibuka), bilah kembali normal
    LaunchedEffect(Unit) {
        snapshotFlow { vm.openP.value < .01f && !FabFx.drag && vm.nav.top is Scr.Home }.collect { ok ->
            if (ok && QuickFx.noteBound && rec.active) QuickFx.noteBound = false
        }
    }

    val pOn by remember { derivedStateOf { QuickFx.p.value > .001f } }
    // catatan sudah menutupi layar: bilah tidak perlu ada (dan tidak boleh menghalangi sentuhan)
    val noteCovers by remember { derivedStateOf { QuickFx.noteBound && !FabFx.drag && vm.openP.value > .34f } }
    val homeTop = vm.nav.top is Scr.Home
    if ((!QuickFx.shown && !QuickFx.drag && !pOn) || noteCovers || (!homeTop && !QuickFx.noteBound)) return

    val ly by animateDpAsState(lift, spring(.7f, 400f), label = "qly")
    val fabWpx = vm.fabMorph?.let { it.r - it.l } ?: with(dens) { 88.dp.toPx() }
    val fabWdp = with(dens) { fabWpx.toDp() }
    val shape = RoundedCornerShape(32.dp)

    Box(Modifier.fillMaxSize().padding(end = 20.dp, bottom = 44.dp + sab), contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier.offset(y = -ly)
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    if (b.width > 0f) QuickFx.barMorph = Morph(b.left, b.top, b.right, b.bottom, 32f * dens.density, pal.pc, card = true)
                }
                .graphicsLayer { alpha = barAlpha(vm); transformOrigin = TransformOrigin(1f, 1f) }
                .layout { m, c ->
                    val pp = QuickFx.p.value
                    val full = (c.maxWidth - 20.dp.roundToPx()).toFloat()
                    val w = lerp(fabWpx, full, pp).roundToInt().coerceAtLeast(1)
                    val h = lerp(88.dp.toPx(), 80.dp.toPx(), pp).roundToInt()
                    val pl = m.measure(Constraints.fixed(w, h))
                    layout(w, h) { pl.place(0, 0) }
                }
                .shadow(
                    if (pal.dark) 4.dp else 3.dp, shape, clip = false,
                    ambientColor = pal.p.copy(alpha = if (pal.dark) .35f else .22f),
                    spotColor = pal.p.copy(alpha = if (pal.dark) .45f else .32f)
                )
                .clip(shape).background(pal.pc)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick("Finish voice note") { if (QuickFx.shown) quickFinish(vm); true }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!QuickFx.shown || QuickFx.noteBound || vm.nav.top !is Scr.Home || QuickFx.p.value < .95f || barAlpha(vm) < .95f) {
                            return@awaitEachGesture
                        }
                        val slop = viewConfiguration.touchSlop
                        val btnX = size.width - 88.dp.toPx()
                        val onBtn = down.position.x >= btnX
                        btnDown = onBtn
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var mode = 0   // 0 menunggu, 1 geser kanan (tutup), 2 geser atas (buka catatan)
                        var p = 1f
                        val distClose = 220.dp.toPx()
                        val distOpen = 260.dp.toPx()
                        var tap = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(c.uptimeMillis, c.position)
                            val dx = c.position.x - down.position.x
                            val dy = c.position.y - down.position.y
                            val up = c.changedToUpIgnoreConsumed()
                            if (mode == 0) {
                                if (up) { tap = true; break }
                                if (abs(dx) > slop || abs(dy) > slop) {
                                    btnDown = false
                                    if (dx > 0f && abs(dx) > abs(dy)) {
                                        mode = 1
                                        QuickFx.drag = true
                                    } else if (dy < 0f && abs(dy) > abs(dx)) {
                                        mode = 2
                                        // catatan baru langsung dibuat; rekaman yang sedang jalan dipindah ke catatan itu
                                        val n = vm.newNote()
                                        vm.rec.rebind(n.id)
                                        QuickFx.noteBound = true
                                        FabFx.drag = true
                                        scope.launch { vm.openP.snapTo(0f) }
                                        vm.nav.openNote(n.id, QuickFx.barMorph, false)
                                    } else {
                                        return@awaitEachGesture   // geser kiri / bawah: abaikan
                                    }
                                } else continue
                            }
                            c.consume()
                            if (up) break
                            if (mode == 1) {
                                p = (1f - (dx - slop).coerceAtLeast(0f) / distClose).coerceIn(0f, 1f)
                                val v = p
                                // mengejar jari lewat spring kaku supaya kecepatan jari terbawa mulus ke animasi lepas jari
                                scope.launch { QuickFx.p.animateTo(v, spring(1f, 1400f)) }
                            } else {
                                p = ((-dy - slop).coerceAtLeast(0f) / distOpen).coerceIn(0f, 1f)
                                val v = p
                                scope.launch { vm.openP.animateTo(v, spring(1f, 1400f)) }
                            }
                        }
                        btnDown = false
                        when {
                            mode == 0 && tap && onBtn -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                quickFinish(vm)
                            }
                            mode == 1 -> {
                                val vx = tracker.calculateVelocity().x
                                QuickFx.drag = false
                                if (p < .62f || vx > 1000f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    quickCancel(vm)
                                } else {
                                    scope.launch { QuickFx.p.animateTo(1f, spring(.8f, 500f)) }
                                }
                            }
                            mode == 2 -> {
                                val vy = tracker.calculateVelocity().y
                                val commit = p > .3f || vy < -1100f
                                scope.launch {
                                    if (commit) {
                                        vm.openP.animateTo(1f, spring(.85f, 560f))
                                        FabFx.drag = false
                                        vm.ed?.let { it.focusAt(it.blocks[0].id, 0) }
                                    } else {
                                        vm.openP.animateTo(0f, spring(1f, 700f))
                                        // batal: rekaman dikembalikan ke bilah, catatan kosong dibuang
                                        vm.rec.rebind(QuickFx.QID)
                                        QuickFx.returning = true
                                        vm.nav.pop()
                                        FabFx.drag = false
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            QuickVoiceBody(vm, fabWdp, btnDown)
        }
    }
}

// =====================================================================================================
// Sheet izin saat pertama kali aplikasi dibuka
// =====================================================================================================
private class PermItem(val perm: String, val icon: String, val title: String, val why: String, val flag: String)

private fun permItems(): List<PermItem> = buildList {
    add(PermItem(Manifest.permission.RECORD_AUDIO, "mic", "Microphone", "Record voice notes", "mp"))
    if (Build.VERSION.SDK_INT >= 33) {
        add(PermItem(Manifest.permission.POST_NOTIFICATIONS, "bell", "Notifications", "Show reminders and recording progress", "np"))
    }
    add(PermItem(Manifest.permission.CAMERA, "cam", "Camera", "Take a photo straight into a note", "cp"))
}

private fun granted(ctx: Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) { }
}

/** Muncul sekali pada pemasangan baru (pengguna lama yang sudah pernah ditanya izin notifikasi tidak melihatnya). */
@Composable
fun PermissionHost(vm: AppVM) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("dinduy", 0) }
    var open by remember {
        mutableStateOf(!sp.getBoolean("pw", false) && !sp.getBoolean("np", false) && permItems().any { !granted(ctx, it.perm) })
    }
    if (!open) return

    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = remember { permItems() }
    var bump by remember { mutableIntStateOf(0) }
    val denied = remember { mutableStateMapOf<String, Boolean>() }
    var last by remember { mutableStateOf("") }

    fun close() {
        sp.edit().putBoolean("pw", true).apply()
        scope.launch { state.hide() }.invokeOnCompletion { open = false }
    }

    val one = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok && last.isNotEmpty()) denied[last] = true
        bump++; vm.permTick++
    }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        res.forEach { (k, v) -> if (!v) denied[k] = true }
        bump++; vm.permTick++
    }

    val missing = items.filter { bump >= 0 && vm.permTick >= 0 && !granted(ctx, it.perm) }
    LaunchedEffect(missing.isEmpty()) {
        if (missing.isEmpty()) { delay(500); close() }
    }

    fun ask(it: PermItem) {
        sp.edit().putBoolean(it.flag, true).apply()   // dicatat supaya alur izin lain tahu "sudah pernah ditanya"
        if (denied[it.perm] == true) { openAppSettings(ctx); return }
        last = it.perm
        one.launch(it.perm)
    }

    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = state,
        containerColor = pal.s,
        contentColor = pal.os,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = pal.ol) }
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            T("Let's get set up", 24f, 900, pal.os, ls = -.01f)
            T(
                "Turn these on so everything works. You can change them anytime in your phone's settings.",
                15f, 600, pal.osv, Modifier.padding(top = 6.dp, bottom = 16.dp), lh = 1.35f
            )
            items.forEach { it ->
                val ok = bump >= 0 && vm.permTick >= 0 && granted(ctx, it.perm)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(22.dp)).background(pal.c2)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(if (ok) pal.pc else pal.c4),
                        Alignment.Center
                    ) { Ic(it.icon, if (ok) pal.opc else pal.os, 24.dp) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        T(it.title, 17f, 800, pal.os, maxLines = 1)
                        T(it.why, 14f, 600, pal.osv, Modifier.padding(top = 1.dp), lh = 1.25f)
                    }
                    if (ok) {
                        Ic("ok", pal.p, 24.dp)
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp)).background(pal.p.copy(alpha = .14f))
                                .clickable { ask(it) }.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { T(if (denied[it.perm] == true) "Settings" else "Allow", 14f, 900, pal.p, maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(pal.p)
                    .clickable {
                        val todo = missing.filter { denied[it.perm] != true }
                        when {
                            missing.isEmpty() -> close()
                            todo.isEmpty() -> openAppSettings(ctx)
                            else -> {
                                todo.forEach { sp.edit().putBoolean(it.flag, true).apply() }
                                multi.launch(todo.map { it.perm }.toTypedArray())
                            }
                        }
                    },
                Alignment.Center
            ) { T(if (missing.isEmpty()) "Done" else "Allow all", 17f, 900, pal.op) }
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).clickable { close() },
                Alignment.Center
            ) { T("Not now", 16f, 800, pal.osv) }
        }
    }
}

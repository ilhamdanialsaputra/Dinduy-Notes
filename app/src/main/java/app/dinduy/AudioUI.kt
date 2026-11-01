package app.dinduy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Bahan dasar: ikon play <-> pause yang berubah bentuk, progres bergelombang, tombol grup
// ---------------------------------------------------------------------------------------------

private val PLAY_L = floatArrayOf(8f, 5f, 13.5f, 8.5f, 13.5f, 15.5f, 8f, 19f)
private val PAUSE_L = floatArrayOf(6f, 5f, 10f, 5f, 10f, 19f, 6f, 19f)
private val PLAY_R = floatArrayOf(13.5f, 8.5f, 19f, 12f, 19f, 12f, 13.5f, 15.5f)
private val PAUSE_R = floatArrayOf(14f, 5f, 18f, 5f, 18f, 19f, 14f, 19f)

/** f = 0 segitiga play, f = 1 dua batang pause; di antaranya titik-titiknya bergeser mulus (morph). */
@Composable
fun PlayPauseIcon(f: Float, tint: Color, size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 24f
        fun quad(a: FloatArray, b: FloatArray): Path {
            val p = Path()
            for (i in 0 until 4) {
                val x = (a[i * 2] + (b[i * 2] - a[i * 2]) * f) * k
                val y = (a[i * 2 + 1] + (b[i * 2 + 1] - a[i * 2 + 1]) * f) * k
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            return p
        }
        val l = quad(PLAY_L, PAUSE_L)
        val r = quad(PLAY_R, PAUSE_R)
        val st = Stroke(width = 2f * k, join = StrokeJoin.Round)
        drawPath(l, tint)
        drawPath(l, tint, style = st)
        drawPath(r, tint)
        drawPath(r, tint, style = st)
    }
}

/**
 * Progres linear bergelombang ala Material 3 Expressive: gelombang bergerak saat diputar, tenang saat dijeda.
 * Digambar sendiri (Canvas) supaya tidak bergantung pada API eksperimental. onSeek != null: bisa diketuk / digeser.
 */
@Composable
fun WavyBar(progress: () -> Float, playing: Boolean, modifier: Modifier = Modifier, onSeek: ((Float) -> Unit)? = null) {
    val pal = LocalPal.current
    val amp by animateFloatAsState(if (playing) 1f else .28f, spring(.8f, 220f), label = "wave")
    val phase = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) phase.animateTo(phase.value + 1f, tween(1500, easing = LinearEasing))
        }
    }
    var drag by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val gestures = if (onSeek != null) Modifier
        .pointerInput(Unit) {
            detectTapGestures { o -> seek?.invoke((o.x / size.width).coerceIn(0f, 1f)) }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                onDragEnd = { drag?.let { f -> seek?.invoke(f) }; drag = null },
                onDragCancel = { drag = null },
                onHorizontalDrag = { ch, _ -> ch.consume(); drag = (ch.position.x / size.width).coerceIn(0f, 1f) }
            )
        } else Modifier
    Canvas(modifier.fillMaxWidth().height(32.dp).then(gestures)) {
        val w = size.width
        val cy = size.height / 2f
        val f = (drag ?: progress()).coerceIn(0f, 1f)
        val th = 4.dp.toPx()
        val gap = 6.dp.toPx()
        val endX = (w * f).coerceIn(0f, w)
        val a = amp * 3.dp.toPx()
        val wl = 26.dp.toPx()
        val ts = endX + gap
        if (ts < w) drawLine(pal.pc, Offset(ts, cy), Offset(w, cy), th, StrokeCap.Round)
        val stop = (endX - gap * .2f).coerceAtLeast(0f)
        if (stop > 1f) {
            val path = Path()
            var x = 0f
            val ph = phase.value
            path.moveTo(0f, cy + a * sin(2.0 * PI * (0f / wl - ph)).toFloat())
            while (x < stop) {
                x = (x + 3f).coerceAtMost(stop)
                path.lineTo(x, cy + a * sin(2.0 * PI * (x / wl - ph)).toFloat())
            }
            drawPath(path, pal.p, style = Stroke(width = th, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawCircle(pal.p, th / 2f, Offset(th / 2f, cy))
        }
        val tw = 4.dp.toPx()
        val tx = (endX - tw / 2f).coerceIn(0f, w - tw)
        drawRoundRect(pal.p, Offset(tx, cy - 12.dp.toPx()), Size(tw, 24.dp.toPx()), CornerRadius(tw / 2f))
    }
}

/** Tombol dalam grup: ujung luar membulat penuh, sambungan antar tombol rapat; saat ditekan semua sudut membulat. */
@Composable
fun GBtn(
    pos: Int, icon: String?, text: String?, bg: Color, fg: Color,
    enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val o = 24.dp
    val n = 6.dp
    val l = if (pos == 0 || pos == 3) o else n
    val r = if (pos == 2 || pos == 3) o else n
    val lr by animateDpAsState(if (pr) o else l, spring(.6f, 500f), label = "gl")
    val rr by animateDpAsState(if (pr) o else r, spring(.6f, 500f), label = "gr")
    val sc by animateFloatAsState(if (pr) .96f else 1f, spring(.5f, 600f), label = "gs")
    Row(
        modifier.height(48.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else .38f }
            .clip(RoundedCornerShape(lr, rr, rr, lr)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (text != null) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) Ic(icon, fg, 20.dp)
        if (icon != null && text != null) Spacer(Modifier.width(8.dp))
        if (text != null) T(text, 14f, 700, fg, maxLines = 1)
    }
}

/** Indikator memuat ala Material 3 Expressive: bentuk bergelombang yang berputar dan berdenyut (digambar sendiri). */
@Composable
fun LoadingBlob(modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val tr = rememberInfiniteTransition(label = "blob")
    val rot by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "rot")
    val pul by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "pul")
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val rr = size.minDimension / 2f * .86f
        val lobes = 8
        val depth = .10f + .10f * pul
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val th = 2.0 * PI * i / steps
            val rad = rr * (1f - depth + depth * kotlin.math.cos(lobes * th).toFloat())
            val a = th + rot * PI / 180.0
            val x = c.x + rad * kotlin.math.cos(a).toFloat()
            val y = c.y + rad * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, pal.p)
    }
}

// ---------------------------------------------------------------------------------------------
// Kartu rekaman (melayang di atas kapsul alat, ikut menggeser area gulir supaya tidak menutupi kursor)
// ---------------------------------------------------------------------------------------------

@Composable
fun RecordCard(vm: AppVM) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val rec = vm.rec
    val pulse = rememberInfiniteTransition(label = "rec")
    val dot by pulse.animateFloat(
        .35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "dot"
    )
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 8.dp)
            .shadow(if (pal.dark) 6.dp else 1.dp, shape).clip(shape).background(pal.c3)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).graphicsLayer { alpha = dot }.clip(RoundedCornerShape(5.dp)).background(Color(0xFFE53935)))
        Spacer(Modifier.width(10.dp))
        T(fmtTime(rec.ms), 18f, 700, pal.os, Modifier.widthIn(min = 48.dp), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.weight(1f).height(40.dp)) {
            val bw = 3.dp.toPx()
            val gp = 3.dp.toPx()
            val n = (size.width / (bw + gp)).toInt().coerceAtLeast(1)
            val list = rec.amps
            val cy = size.height / 2f
            for (i in 0 until n) {
                val idx = list.size - n + i
                val v = if (idx >= 0 && idx < list.size) list[idx] else 0f
                val hh = (4.dp.toPx() + v * (size.height - 4.dp.toPx())).coerceAtMost(size.height)
                val x = size.width - (n - i) * (bw + gp) + gp
                drawRoundRect(
                    pal.p.copy(alpha = if (idx >= 0) 1f else .25f),
                    Offset(x, cy - hh / 2f), Size(bw, hh), CornerRadius(bw / 2f)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GBtn(0, "x", null, pal.c4, pal.os) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.cancelRec()
            }
            GBtn(2, "stop", "Selesai", pal.p, pal.op) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.stopRec()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Kartu pemutar + transkrip, ditampilkan di bawah isi catatan (editor dan mode baca)
// ---------------------------------------------------------------------------------------------

private fun safeName(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(60)

@Composable
fun AudioSection(vm: AppVM, noteId: String, act: Actions) {
    val n = vm.note(noteId) ?: return
    if (n.au.isEmpty()) return
    val title = n.t
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        n.au.forEachIndexed { i, a ->
            androidx.compose.runtime.key(a.id) {
                AudioCard(vm, noteId, a, i, n.au.size, safeName(title), act)
            }
        }
    }
}

@Composable
fun AudioCard(vm: AppVM, noteId: String, a: Aud, idx: Int, total: Int, title: String, act: Actions) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val pl = vm.player
    val exists = remember(a.id, vm.audVer) { AudioFiles.file(ctx, a.id).exists() }
    val mine = pl.id == a.id
    val playing = mine && pl.playing
    val dur = if (mine && pl.dur > 0L) pl.dur else a.du
    val busy = vm.trId == a.id
    val otherBusy = vm.trId != null && !busy
    val f by animateFloatAsState(if (playing) 1f else 0f, spring(.8f, 380f), label = "pp")
    val corner by animateDpAsState(if (playing) 18.dp else 28.dp, spring(.6f, 420f), label = "pc")
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val ps by animateFloatAsState(if (pr) .92f else 1f, spring(.5f, 600f), label = "ps")

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.c2)
                .animateContentSize(spring(.8f, 400f)).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).graphicsLayer { scaleX = ps; scaleY = ps; alpha = if (exists) 1f else .38f }
                        .clip(RoundedCornerShape(corner)).background(pal.p)
                        .clickable(interactionSource = src, indication = LocalIndication.current, enabled = exists) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pl.toggle(a)
                        },
                    Alignment.Center
                ) { PlayPauseIcon(f, pal.op, 28.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (total > 1) T("Suara ${idx + 1}", 13f, 700, pal.osv, maxLines = 1)
                    if (exists) {
                        WavyBar(
                            progress = { if (mine && pl.dur > 0L) pl.pos.toFloat() / pl.dur else 0f },
                            playing = playing,
                            onSeek = { fr -> pl.seek(a, fr) }
                        )
                        Row(Modifier.fillMaxWidth()) {
                            T(fmtTime(if (mine) pl.pos else 0L), 13f, 600, pal.osv)
                            Spacer(Modifier.weight(1f))
                            T(fmtTime(dur), 13f, 600, pal.osv)
                        }
                    } else {
                        T("Berkas suara tidak ada di perangkat ini", 14f, 500, pal.osv, Modifier.padding(vertical = 6.dp), lh = 1.3f)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(
                visible = busy,
                enter = fadeIn(tween(200)) + expandVertically(spring(.8f, 400f)),
                exit = fadeOut(tween(150)) + shrinkVertically(spring(.9f, 500f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(pal.pc)
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingBlob(Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    val st = when (vm.trStage) {
                        0 -> "Menyiapkan suara…"
                        1 -> "Memuat model…"
                        2 -> "Menranskripsi…"
                        4 -> "Mengirim ke Groq…"
                        5 -> "Merapikan format…"
                        else -> "Membatalkan…"
                    }
                    Column(Modifier.weight(1f)) {
                        T(st, 15f, 600, pal.opc, maxLines = 1)
                        if (vm.trInfo.isNotEmpty()) T(vm.trInfo, 12f, 500, pal.opc, alpha = .8f, maxLines = 1)
                    }
                    GBtn(3, null, "Batal", pal.c1, pal.os, enabled = vm.trStage != 3) { vm.cancelTranscribe() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                GBtn(0, "dl", "Simpan suara", pal.c4, pal.os, enabled = exists, modifier = Modifier.weight(1f)) {
                    val name = (title.ifBlank { "Suara" }) + (if (total > 1) "-${idx + 1}" else "") + ".m4a"
                    act.saveAudio(a, name)
                }
                GBtn(1, "notes", if (a.tb.isEmpty()) "Transkripsi" else "Ulangi", pal.sc, pal.osc, enabled = exists && !busy && !otherBusy, modifier = Modifier.weight(1f)) {
                    vm.transcribe(noteId, a.id)
                }
                GBtn(2, "del", null, pal.ec, pal.oec) { vm.removeAudio(noteId, a.id) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dialog: model belum diunduh
// ---------------------------------------------------------------------------------------------

@Composable
fun ModelDialogHost(vm: AppVM) {
    if (!vm.needModel) return
    val pal = LocalPal.current
    AlertDialog(
        onDismissRequest = { vm.needModel = false },
        containerColor = pal.c3,
        shape = RoundedCornerShape(28.dp),
        title = { T("Belum ada model", 24f, 400, pal.os) },
        text = {
            T(
                "Anda belum menerapkan model apa pun. Pilih API key Groq (online, cepat) atau unduh model offline di halaman berikutnya.",
                15f, 400, pal.osv, lh = 1.4f
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.needModel = false
                if (vm.nav.top !is Scr.Models) vm.nav.push(Scr.Models)
            }) { T("Atur model", 15f, 600, pal.p) }
        },
        dismissButton = { TextButton(onClick = { vm.needModel = false }) { T("Batal", 15f, 500, pal.osv) } }
    )
}

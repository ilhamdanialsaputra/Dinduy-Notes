package app.dinduy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =====================================================================================================
// Transkrip selesai -> layar menggulir halus ke transkrip, lalu transkrip "dipamerkan":
//   1. pil "Transcript ready" melompat turun dari atas (pegas memantul)
//   2. layar meluncur ke judul transkrip (judul berhenti di ~22% dari atas, bukan mentok di tepi)
//   3. wadah tonal mekar dari judul transkrip ke bawah (pegas lembut, sedikit melampaui), dengan aksen warna di kiri
//   4. kilau melintas dari atas ke bawah wadah, lalu semuanya memudar dan catatan kembali normal
// Tidak ada isi catatan yang diubah atau disembunyikan: semua ini digambar DI BELAKANG teks.
// =====================================================================================================

class TrFx {
    val bloom = Animatable(0f)   // 0..1: wadah mekar dari judul ke bawah
    val glow = Animatable(0f)    // 0..1: kilau melintas
    val fade = Animatable(0f)    // 0..1: keterlihatan keseluruhan sorotan
    val pill = Animatable(0f)    // 0..1: pil "Transcript ready" (memantul)
}

@Composable
fun rememberTranscriptFx(vm: AppVM, ed: Ed, scroll: ScrollState): TrFx {
    val fx = remember { TrFx() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(ed.tRevealTick) {
        if (ed.tRevealTick == 0) return@LaunchedEffect
        val ids = ed.tRevealIds
        // halaman lain (mis. pengaturan model) bisa sedang menutupi catatan: tunggu sampai catatan tampil paling atas
        while (vm.nav.top !is Scr.Note) delay(100)
        delay(280)   // beri waktu blok baru disusun
        var span = ed.spanOf(ids)
        var tries = 0
        while (span == null && tries < 40) {
            // catatan sangat panjang (berjendela): blok baru mungkin belum disusun; dekati dulu lalu periksa ulang
            if (tries == 10) scroll.animateScrollTo(scroll.maxValue, tween(300, easing = Emph))
            delay(50)
            span = ed.spanOf(ids)
            tries++
        }
        val sp = span ?: return@LaunchedEffect
        fx.bloom.snapTo(0f); fx.glow.snapTo(0f); fx.fade.snapTo(1f); fx.pill.snapTo(0f)
        val want = (sp.first - ed.viewH * .22f).toInt().coerceIn(0, scroll.maxValue)
        launch { fx.pill.animateTo(1f, spring(.55f, 520f)) }
        launch {
            delay(360)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            fx.bloom.animateTo(1f, spring(.72f, 140f))
        }
        launch {
            delay(560)
            fx.glow.animateTo(1f, tween(1100, easing = LinearEasing))
        }
        scroll.animateScrollTo(want, tween(720, easing = Emph))
        delay(2400)
        launch { fx.pill.animateTo(0f, spring(1f, 700f)) }
        fx.fade.animateTo(0f, tween(700))
        fx.bloom.snapTo(0f)
    }
    return fx
}

/** Digambar di belakang isi catatan (ruang koordinat sama dengan Ed.kpos), jadi teks tetap terbaca dan bisa diedit seperti biasa. */
fun Modifier.transcriptReveal(ed: Ed, fx: TrFx, pal: Pal): Modifier = drawBehind {
    val f = fx.fade.value
    if (f <= .001f) return@drawBehind
    val span = ed.spanOf(ed.tRevealIds) ?: return@drawBehind
    drawReveal(span.first, span.second, fx.bloom.value, fx.glow.value, f, pal)
}

private fun DrawScope.drawReveal(top: Float, bottom: Float, bloom: Float, glow: Float, fade: Float, pal: Pal) {
    val d = density
    val padX = 16f * d
    val padY = 14f * d
    val l = -padX
    val r = size.width + padX
    val t = top - padY
    val h = ((bottom - top) + padY * 2f) * bloom.coerceAtLeast(0f)
    if (h <= 1f) return
    val rr = CornerRadius(minOf(28f * d, h / 2f))
    // wadah tonal
    drawRoundRect(pal.pc.copy(alpha = .5f * fade), Offset(l, t), Size(r - l, h), rr)
    // aksen warna di tepi kiri
    val barH = (h - 28f * d).coerceAtLeast(0f)
    if (barH > 0f) drawRoundRect(pal.p.copy(alpha = fade), Offset(l + 6f * d, t + 14f * d), Size(4f * d, barH), CornerRadius(2f * d))
    // kilau melintas dari atas ke bawah, terpotong bentuk wadah
    if (glow > .001f && glow < .999f) {
        val band = 150f * d
        val y = t + (h + band) * glow - band
        val clip = Path().apply { addRoundRect(RoundRect(l, t, r, t + h, rr)) }
        clipPath(clip) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, pal.p.copy(alpha = .22f * fade), Color.Transparent),
                    startY = y, endY = y + band
                ),
                topLeft = Offset(l, y), size = Size(r - l, band)
            )
        }
    }
}

/** Pil kecil di atas area catatan: melompat turun dengan pegas memantul, lalu menciut keluar. */
@Composable
fun BoxScope.TranscriptPill(fx: TrFx) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
            .graphicsLayer {
                val p = fx.pill.value
                val s = p.coerceAtLeast(0f)
                scaleX = s; scaleY = s
                translationY = (1f - p) * -28.dp.toPx()
                alpha = (p * 2.5f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(.5f, 0f)
            }
            .height(52.dp)
            .shadow(
                6.dp, shape, clip = false,
                ambientColor = pal.p.copy(alpha = .3f), spotColor = pal.p.copy(alpha = .4f)
            )
            .clip(shape).background(pal.p).padding(start = 10.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(pal.op.copy(alpha = .18f)), Alignment.Center) {
            Ic("notes", pal.op, 20.dp)
        }
        Spacer(Modifier.width(10.dp))
        T("Transcript ready", 15f, 800, pal.op, maxLines = 1)
    }
}

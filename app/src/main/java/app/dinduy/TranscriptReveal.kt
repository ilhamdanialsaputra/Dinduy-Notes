package app.dinduy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

// =====================================================================================================
// Transkrip selesai -> transkrip "dipamerkan" dengan gerak Material 3 Expressive.
//
// GERAK (pegas M3 Expressive: spatial = boleh memantul, effects = tanpa pantulan)
//   1. Pil "Transcript ready" melompat dari atas sebagai LINGKARAN ikon (spatial bouncy), lalu MELEBAR jadi pil
//      penuh (morph lingkaran -> pil). Ikon ikut "pop".
//   2. Wadah tonal mekar dari judul transkrip: tinggi + lebar + radius sudut bermorf (44dp -> 28dp), pegas lembut
//      yang sedikit melampaui. Aksen kiri tumbuh menyusul (stagger), dua kilau bintang berputar muncul,
//      lalu seberkas cahaya miring menyapu wadah dari atas ke bawah.
//   3. Semuanya memudar dengan pegas effects (tanpa pantulan); wadah menciut sedikit sambil hilang.
//
// UX / ANTI-BENTROK (inti perubahan ini)
//   Layar HANYA menggulir otomatis bila pengguna sedang tidak melakukan apa-apa. Auto-scroll ditahan bila:
//     keyboard terbuka / ada fokus ketik (kursor-follow di Ed akan menyeret layar balik saat mengetik),
//     judul sedang fokus, ada seleksi, mode cari, mode coretan, sheet/sub-bar terbuka, blok sedang diseret,
//     atau jari pengguna sedang menggulir.
//   Dalam kasus itu pil berubah jadi PIL AKSI "View ↓/↑" (arah mengikuti posisi transkrip). Pengguna yang memutuskan:
//     - ketuk pil  -> layar menggulir ke transkrip, baru sorotan dimainkan
//     - menggulir sendiri sampai transkrip terlihat -> sorotan dimainkan tepat saat itu
//     - geser pil ke atas / ketuk saat sorotan -> tutup
//     - tidak diapa-apakan 9 detik -> pil menghilang tenang (tidak ada yang berubah di catatan)
//   Jika transkrip sudah terlihat di layar: tidak ada gulir sama sekali, hanya sorotan.
//   Jika pengguna menyentuh layar saat auto-scroll berjalan: auto-scroll langsung mengalah, tidak dilawan.
//   Tidak ada isi catatan yang diubah atau disembunyikan: sorotan digambar DI BELAKANG teks.
// =====================================================================================================

// Skala gerak M3 Expressive (damping, stiffness)
private val FastSpatial: SpringSpec<Float> = spring(0.6f, 800f)
private val SlowSpatial: SpringSpec<Float> = spring(0.8f, 200f)
private val BouncySpatial: SpringSpec<Float> = spring(0.55f, 480f)
private val FastEffects: SpringSpec<Float> = spring(1f, 3800f)
private val SlowEffects: SpringSpec<Float> = spring(1f, 800f)

class TrFx {
    val pill = Animatable(0f)        // 0..~1.1: pil masuk (spatial, memantul)
    val pillLabel = Animatable(0f)   // 0 = lingkaran ikon, 1 = pil penuh dengan label
    val drag = Animatable(0f)        // geser-untuk-tutup (px, negatif = ke atas)
    val bloom = Animatable(0f)       // 0..~1.05: wadah mekar
    val accent = Animatable(0f)      // 0..~1.1: aksen kiri tumbuh (menyusul bloom)
    val spark = Animatable(0f)       // 0..~1.2: kilau bintang
    val glow = Animatable(0f)        // 0..1: seberkas cahaya menyapu
    val fade = Animatable(0f)        // 0..1: keterlihatan keseluruhan sorotan

    /** -1: transkrip di atas layar, 0: sudah terlihat, 1: di bawah layar. Dipakai panah di pil aksi. */
    var dir by mutableStateOf(0)
    /** Pil menampilkan tombol "View" (auto-scroll ditahan, pengguna yang memutuskan). */
    var actionable by mutableStateOf(false)
    var jumpTick by mutableStateOf(0)
    var dismissTick by mutableStateOf(0)

    val pillOn: Boolean by derivedStateOf { pill.value > .001f }
}

@Composable
fun rememberTranscriptFx(vm: AppVM, ed: Ed, scroll: ScrollState): TrFx {
    val fx = remember { TrFx() }
    val haptic = LocalHapticFeedback.current
    val ime = WindowInsets.ime
    val density = LocalDensity.current
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
        val sp0 = span ?: return@LaunchedEffect

        // ---------- pembantu ----------
        fun cur() = ed.spanOf(ids) ?: sp0
        val dens = ed.dens.coerceAtLeast(1f)

        /** Pengguna sedang melakukan sesuatu yang bisa bentrok dengan gulir otomatis. */
        fun busy() = ed.focus != null || ed.titleFocus || ed.selOn || ed.findOn || ed.ink.on ||
            ed.sheet != 0 || ed.sub != 0 || ed.mv != null ||
            ime.getBottom(density) > 0 || scroll.isScrollInProgress

        /** Judul transkrip sudah cukup terlihat (atau transkrip panjang sudah memenuhi layar). */
        fun inView(): Boolean {
            val s = cur()
            val vt = scroll.value.toFloat()
            val vb = vt + ed.viewH
            val topOk = s.first >= vt - ed.viewH * .08f && s.first <= vb - ed.viewH * .3f
            val overlap = minOf(vb, s.second) - maxOf(vt, s.first)
            return topOk || overlap >= ed.viewH * .55f
        }
        fun dirNow() = if (cur().first < scroll.value) -1 else 1

        /** Gulir ke transkrip (judul berhenti ~22% dari atas). Mengalah bila pengguna menyentuh layar. */
        suspend fun goTo(): Boolean {
            val want = (cur().first - ed.viewH * .22f).toInt().coerceIn(0, scroll.maxValue)
            val dist = abs(want - scroll.value) / dens
            val ms = (460 + dist * .55f).toInt().coerceIn(520, 940)   // jarak jauh = sedikit lebih lama, tetap enak
            return try {
                scroll.animateScrollTo(want, tween(ms, easing = Emph))
                true
            } catch (e: CancellationException) {
                // dibatalkan jari pengguna (preempt)? lanjut dengan tenang. Coroutine-nya sendiri yang dibatalkan? teruskan.
                currentCoroutineContext().ensureActive()
                false
            }
        }

        suspend fun hidePill() = coroutineScope {
            launch { fx.pillLabel.animateTo(0f, FastEffects) }   // melipat kembali jadi lingkaran
            delay(110)
            fx.pill.animateTo(0f, spring(1f, 700f))
        }

        // ---------- reset ----------
        fx.bloom.snapTo(0f); fx.accent.snapTo(0f); fx.spark.snapTo(0f); fx.glow.snapTo(0f); fx.fade.snapTo(0f)
        fx.pill.snapTo(0f); fx.pillLabel.snapTo(0f); fx.drag.snapTo(0f)
        val dismiss0 = fx.dismissTick
        val jump0 = fx.jumpTick

        val wasBusy = busy()
        val seen0 = inView()
        val auto = !seen0 && !wasBusy
        fx.dir = if (seen0) 0 else dirNow()
        fx.actionable = !seen0 && !auto

        // ---------- 1. pil masuk ----------
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        launch { fx.pill.animateTo(1f, BouncySpatial) }
        launch { delay(140); fx.pillLabel.animateTo(1f, spring(.7f, 420f)) }
        if (!seen0) delay(if (auto) 320 else 120)

        // ---------- 2. bawa transkrip ke layar ----------
        if (!seen0) {
            if (auto) {
                val scrollJob = launch { goTo() }
                // sorotan mulai begitu judul sudah masuk layar (tumpang tindih dengan akhir gulir = terasa menyatu)
                withTimeoutOrNull(2500L) { snapshotFlow { inView() }.first { it } }
                if (!scrollJob.isActive && !inView()) fx.actionable = true
            }
            if (!inView()) {
                // gulir otomatis ditahan / diinterupsi: serahkan ke pengguna
                fx.actionable = true
                val res: Int? = withTimeoutOrNull(9000L) {
                    coroutineScope {
                        val j = launch { snapshotFlow { fx.jumpTick }.first { it > jump0 }; goTo() }
                        val k = launch { snapshotFlow { dirNow() }.collect { fx.dir = it } }
                        val r = snapshotFlow {
                            when {
                                fx.dismissTick > dismiss0 -> 2
                                inView() && !scroll.isScrollInProgress -> 1
                                else -> 0
                            }
                        }.first { it != 0 }
                        j.cancel(); k.cancel()
                        r
                    }
                }
                if (res != 1) {   // ditutup / waktu habis: pergi tenang, catatan tidak berubah
                    fx.actionable = false
                    hidePill()
                    return@LaunchedEffect
                }
            }
            fx.actionable = false
        }

        // ---------- 3. sorotan ----------
        delay(90)
        if (!wasBusy) haptic.performHapticFeedback(HapticFeedbackType.LongPress)   // tidak mengganggu orang yang sedang mengetik
        fx.fade.snapTo(1f)
        launch { fx.bloom.animateTo(1f, spring(.72f, 220f)) }
        launch { delay(90); fx.accent.animateTo(1f, spring(.6f, 300f)) }
        launch { delay(240); fx.spark.animateTo(1f, spring(.5f, 260f)) }
        launch { delay(420); fx.glow.animateTo(1f, tween(1000, easing = Emph)) }

        // tahan sebentar; ketuk / geser pil = tutup lebih cepat
        withTimeoutOrNull(2600L) { snapshotFlow { fx.dismissTick }.first { it > dismiss0 } }

        // ---------- 4. keluar ----------
        launch { hidePill() }
        launch { fx.spark.animateTo(0f, FastEffects) }
        launch { fx.accent.animateTo(0f, SlowEffects) }
        launch { fx.bloom.animateTo(.96f, SlowEffects) }
        fx.fade.animateTo(0f, SlowEffects)
        fx.bloom.snapTo(0f); fx.glow.snapTo(0f)
    }
    return fx
}

/** Digambar di belakang isi catatan (ruang koordinat sama dengan Ed.kpos), jadi teks tetap terbaca dan bisa diedit seperti biasa. */
fun Modifier.transcriptReveal(ed: Ed, fx: TrFx, pal: Pal): Modifier = drawBehind {
    val f = fx.fade.value
    if (f <= .001f) return@drawBehind
    val span = ed.spanOf(ed.tRevealIds) ?: return@drawBehind
    drawReveal(
        span.first, span.second,
        fx.bloom.value, fx.accent.value, fx.glow.value, fx.spark.value, f, pal
    )
}

private fun DrawScope.drawReveal(
    top: Float, bottom: Float,
    bloom: Float, accent: Float, glow: Float, spark: Float, fade: Float, pal: Pal
) {
    val d = density
    val a = fade.coerceIn(0f, 1f)
    val b = bloom.coerceAtLeast(0f)
    val bc = b.coerceAtMost(1f)
    val padX = 16f * d
    val padY = 14f * d
    // lebar ikut mekar: mulai menyempit dari kiri-kanan, lalu melebar penuh
    val inset = (1f - bc) * 28f * d
    val l = -padX + inset
    val r = size.width + padX - inset
    val t = top - padY
    val h = ((bottom - top) + padY * 2f) * b
    if (h <= 1f || r - l <= 1f) return
    // radius sudut ikut bermorf: besar (hampir pil) -> 28dp
    val cr = minOf(lerp(44f * d, 28f * d, bc), h / 2f, (r - l) / 2f)
    val rr = CornerRadius(cr)

    // wadah tonal: gradasi halus dari atas (lebih pekat) ke bawah, plus garis tepi tipis
    drawRoundRect(
        Brush.verticalGradient(
            listOf(pal.pc.copy(alpha = .72f * a), pal.pc.copy(alpha = .42f * a)),
            startY = t, endY = t + h
        ),
        Offset(l, t), Size(r - l, h), rr
    )
    drawRoundRect(pal.p.copy(alpha = .26f * a), Offset(l, t), Size(r - l, h), rr, style = Stroke(1.5f * d))

    // aksen kiri: berbentuk pil, tumbuh menyusul wadah dengan pegas sendiri
    val barMax = (h - 28f * d).coerceAtLeast(0f)
    val barH = minOf(barMax * accent.coerceAtLeast(0f), (h - 16f * d).coerceAtLeast(0f))
    if (barH > 0f) drawRoundRect(pal.p.copy(alpha = a), Offset(l + 8f * d, t + 14f * d), Size(4f * d, barH), CornerRadius(2f * d))

    // cahaya miring menyapu dari atas ke bawah, terpotong bentuk wadah
    if (glow > .001f && glow < .999f) {
        val band = 180f * d
        val y = t + (h + band) * glow - band
        val clip = Path().apply { addRoundRect(RoundRect(l, t, r, t + h, rr)) }
        clipPath(clip) {
            rotate(-6f, Offset((l + r) / 2f, y + band / 2f)) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, pal.p.copy(alpha = .22f * a), Color.Transparent),
                        startY = y, endY = y + band
                    ),
                    Offset(l - 40f * d, y), Size(r - l + 80f * d, band)
                )
            }
        }
    }

    // dua kilau bintang di selokan kanan (di luar teks), berputar sambil membesar
    if (spark > .001f && h > 56f * d) {
        val s = spark.coerceAtLeast(0f)
        val cx = r - 8f * d
        val cy = t + 22f * d
        rotate(s * 90f, Offset(cx, cy)) { drawPath(sparklePath(cx, cy, 7f * d * s), pal.p.copy(alpha = a)) }
        val cx2 = cx - 1f * d
        val cy2 = cy + 20f * d
        rotate(-s * 70f, Offset(cx2, cy2)) { drawPath(sparklePath(cx2, cy2, 4f * d * s), pal.p.copy(alpha = .7f * a)) }
    }
}

/** Bintang empat sudut yang melengkung ke dalam (gaya "sparkle" M3 Expressive). */
private fun sparklePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r)
    quadraticBezierTo(cx, cy, cx + r, cy)
    quadraticBezierTo(cx, cy, cx, cy + r)
    quadraticBezierTo(cx, cy, cx - r, cy)
    quadraticBezierTo(cx, cy, cx, cy - r)
    close()
}

/**
 * Pil kecil di atas area catatan. Masuk sebagai lingkaran ikon lalu melebar jadi pil. Bila auto-scroll ditahan
 * (keyboard dll.), pil memunculkan tombol "View" berpanah. Geser ke atas untuk menutup.
 */
@Composable
fun BoxScope.TranscriptPill(fx: TrFx) {
    if (!fx.pillOn) return
    val pal = LocalPal.current
    val shape = RoundedCornerShape(26.dp)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val press = animateFloatAsState(if (pressed) .94f else 1f, spring(.6f, 800f), label = "trPress")
    val arrow = animateFloatAsState(if (fx.dir < 0) 180f else 0f, spring(.6f, 500f), label = "trArrow")

    Row(
        Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            .graphicsLayer {
                val p = fx.pill.value
                val s = p.coerceAtLeast(0f) * press.value
                scaleX = s; scaleY = s
                translationY = (1f - p) * -36.dp.toPx() + fx.drag.value
                alpha = (p * 3f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(.5f, 0f)
            }
            .height(52.dp)
            .shadow(
                8.dp, shape, clip = false,
                ambientColor = pal.p.copy(alpha = .3f), spotColor = pal.p.copy(alpha = .4f)
            )
            .clip(shape).background(pal.p)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { if (fx.actionable) fx.jumpTick++ else fx.dismissTick++ }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        scope.launch { fx.drag.snapTo((fx.drag.value + dy).coerceAtMost(8.dp.toPx())) }
                    },
                    onDragEnd = {
                        if (fx.drag.value < -22.dp.toPx()) fx.dismissTick++
                        scope.launch { fx.drag.animateTo(0f, BouncySpatial) }
                    },
                    onDragCancel = { scope.launch { fx.drag.animateTo(0f, BouncySpatial) } }
                )
            }
            .padding(start = 9.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ikon: "pop" saat pil mulai melebar
        Box(
            Modifier.size(34.dp)
                .graphicsLayer {
                    val s = (.75f + .25f * fx.pillLabel.value.coerceIn(0f, 1.2f))
                    scaleX = s; scaleY = s
                }
                .clip(CircleShape).background(pal.op.copy(alpha = .18f)),
            Alignment.Center
        ) { Ic("notes", pal.op, 20.dp) }

        // label + tombol: lebar dianimasikan (lingkaran -> pil), dibaca di fase ukur sehingga tidak recompose per frame
        Box(
            Modifier
                .layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0))
                    val f = fx.pillLabel.value.coerceIn(0f, 1f)
                    layout((p.width * f).roundToInt(), p.height) { p.placeRelative(0, 0) }
                }
                .clipToBounds()
                .graphicsLayer { alpha = ((fx.pillLabel.value - .35f) * 1.6f).coerceIn(0f, 1f) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(10.dp))
                T("Transcript ready", 15f, 800, pal.op, maxLines = 1)
                AnimatedVisibility(
                    visible = fx.actionable,
                    enter = fadeIn(spring(1f, 1600f)) + expandHorizontally(spring(.7f, 420f)),
                    exit = fadeOut(spring(1f, 1600f)) + shrinkHorizontally(spring(1f, 700f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
                                .background(pal.op.copy(alpha = .16f))
                                .padding(start = 12.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            T("View", 13f, 800, pal.op, maxLines = 1)
                            Spacer(Modifier.width(2.dp))
                            Box(Modifier.graphicsLayer { rotationZ = arrow.value }) { Ic("adn", pal.op, 18.dp) }
                        }
                    }
                }
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}

package app.dinduy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// =====================================================================================
//  Material 3 Expressive: bahan dasar yang dipakai SEMUA toolbar (dock utama, dock coret, dock sisipan, sheet).
//
//  Prinsip yang dipegang di seluruh toolbar:
//   1. Gerak = pegas "spasial" (memantul, mengubah posisi / ukuran / bentuk) dan pegas "efek" (warna, tanpa pantulan).
//      Tidak ada fade in / fade out untuk berpindah toolbar. Yang berubah adalah BENTUK: lebar, tinggi, sudut, warna wadah.
//   2. Bentuk ikut keadaan: tombol diam = lingkaran, terpilih = squircle, ditekan = lebih kotak + menyempit.
//   3. Ramah jempol: area sentuh minimal 48dp, tombol bahaya (hapus) dijauhkan dari tombol yang sering dipakai.
//   4. Tanpa garis pemisah: kelompok tombol dipisah dengan wadah tonal (grup tombol tersambung), bukan garis.
// =====================================================================================

/** Spesifikasi gerak "expressive" (nilai mengikuti skema gerak M3 Expressive). */
object M3E {
    /** Spasial cepat: tombol kecil, bentuk yang berubah saat ditekan. Memantul jelas. */
    fun <T> spatialFast() = spring<T>(dampingRatio = .6f, stiffness = 800f)
    /** Spasial bawaan: ukuran / posisi wadah toolbar. */
    fun <T> spatial() = spring<T>(dampingRatio = .78f, stiffness = 380f)
    /** Spasial lambat: perubahan besar (sheet, panel). */
    fun <T> spatialSlow() = spring<T>(dampingRatio = .8f, stiffness = 200f)
    /** Efek: warna dan sejenisnya. Tidak memantul. */
    fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 1600f)
    /** Ukuran wadah saat berubah bentuk antar toolbar. Sedikit lebih bernyawa dari spasial bawaan. */
    fun sizeSpring() = spring<IntSize>(dampingRatio = .74f, stiffness = 420f)
}

/** Tinggi tetap satu baris toolbar. Semua toolbar setinggi ini, jadi perpindahan hanya mengubah lebar (dan panel). */
val DockH = 66.dp

/** Warna ikon bawaan di dalam wadah tertentu (null = pakai pal.os). */
val LocalDockFg = compositionLocalOf<Color?> { null }

/** Jari-jari sudut tidak boleh negatif walau pegas memantul melewati nol. */
fun Dp.nn(): Dp = if (this < 0.dp) 0.dp else this

/**
 * Masuk dengan "melompat": membesar dari titik nol dengan pegas, berurutan menurut [i]. Tanpa alpha sama sekali.
 * Jeda dasar [base] memberi waktu isi toolbar lama menciut dulu, jadi dua isi tidak pernah bertumpuk.
 */
@Composable
fun Modifier.popIn(i: Int, base: Long = 70L): Modifier {
    if (i < 0) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(base + i * 28L)
        a.animateTo(1f, spring(dampingRatio = .52f, stiffness = 520f))
    }
    return this.graphicsLayer { val p = a.value.coerceAtLeast(0f); scaleX = p; scaleY = p }
}

/**
 * Tombol ikon M3 Expressive (area sentuh 48dp).
 *  - diam: lingkaran;  terpilih ([on]): squircle berwarna utama;  ditekan: lebih kotak dan menyempit membal.
 *  - [rest]: warna latar saat tidak terpilih (bawaan: transparan).
 *  - [tone]: 0 biasa, 1 bahaya (hapus), 2 utama (selesai).
 *  - [i] >= 0: melompat masuk berurutan.
 */
@Composable
fun XBtn(
    modifier: Modifier = Modifier,
    on: Boolean = false,
    enabled: Boolean = true,
    tone: Int = 0,
    rest: Color = Color.Unspecified,
    i: Int = -1,
    w: Dp = 48.dp,
    h: Dp = 48.dp,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit
) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val rT = when {
        pr -> 12.dp
        on && tone == 0 -> 17.dp
        else -> h / 2
    }
    val r by animateDpAsState(rT, M3E.spatialFast(), label = "xr")
    val sx by animateFloatAsState(if (pr) .88f else 1f, spring(.45f, 700f), label = "xsx")
    val sy by animateFloatAsState(if (pr) .94f else 1f, spring(.45f, 700f), label = "xsy")
    val restC = if (rest == Color.Unspecified) pal.p.copy(alpha = 0f) else rest
    val bgT = when (tone) { 1 -> pal.ec; 2 -> pal.p; else -> if (on) pal.p else restC }
    val fgT = when (tone) { 1 -> pal.oec; 2 -> pal.op; else -> if (on) pal.op else (LocalDockFg.current ?: pal.os) }
    val bg by animateColorAsState(bgT, M3E.effects(), label = "xb")
    val fg by animateColorAsState(fgT, M3E.effects(), label = "xf")
    Box(
        modifier.popIn(i).size(w, h)
            .graphicsLayer { scaleX = sx; scaleY = sy; alpha = if (enabled) 1f else .3f }
            .clip(RoundedCornerShape(r.nn())).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        Alignment.Center
    ) { content(fg) }
}

/**
 * Grup tombol tersambung: satu wadah tonal berbentuk pil. Menggantikan garis pemisah, jadi toolbar tetap bersih.
 */
@Composable
fun XGroup(
    modifier: Modifier = Modifier,
    i: Int = -1,
    color: Color = LocalPal.current.c4,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier.popIn(i).clip(RoundedCornerShape(50)).background(color).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

/**
 * Kelompok pilihan dengan SATU penanda terpilih yang meluncur dan melar mengikuti pilihan (bukan tiap tombol ganti warna sendiri).
 * [sel] = -1: tidak ada yang terpilih. Mengetuk yang sudah terpilih tetap memanggil [onPick].
 */
@Composable
fun SlideSeg(
    count: Int,
    sel: Int,
    i: Int = -1,
    color: Color = LocalPal.current.c4,
    onPick: (Int) -> Unit,
    item: @Composable (Int, Color) -> Unit
) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val cw = 48.dp
    val ch = 48.dp
    val x by animateFloatAsState(sel.toFloat(), spring(.58f, 380f), label = "segx")
    val dist = if (sel < 0) 0f else abs(sel - x).coerceAtMost(1f)
    Box(Modifier.popIn(i).clip(RoundedCornerShape(50)).background(color).padding(3.dp)) {
        if (sel >= 0) Box(
            Modifier.offset { IntOffset((x * cw.toPx() - cw.toPx() * .38f * dist / 2f).roundToInt(), 0) }
                .width(cw * (1f + .38f * dist)).height(ch)
                .clip(RoundedCornerShape(17.dp)).background(pal.p)
        )
        Row {
            for (k in 0 until count) {
                val fg by animateColorAsState(
                    if (sel == k) pal.op else (LocalDockFg.current ?: pal.os), M3E.effects(), label = "segc"
                )
                Box(
                    Modifier.size(cw, ch).clip(RoundedCornerShape(17.dp)).clickable {
                        if (sel != k) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(k)
                    },
                    Alignment.Center
                ) { item(k, fg) }
            }
        }
    }
}

/**
 * Penukar isi yang MENGUBAH BENTUK wadahnya, bukan memudarkan isi.
 *  - Wadah (pemanggil) mengikuti ukuran isi baru dengan pegas, jadi lebar / tinggi mengalir.
 *  - Isi lama menciut cepat ke arah [originX] (titik tombol yang ditekan) dan terpotong oleh wadah. TANPA alpha.
 *  - Isi baru tidak ikut menciut: tiap tombolnya melompat masuk sendiri (lihat [popIn]) setelah isi lama hilang.
 */
@Composable
fun <T> MorphSwap(
    target: T,
    modifier: Modifier = Modifier,
    clip: Boolean = false,
    originX: (T, T) -> Float = { _, _ -> .5f },
    label: String = "morph",
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            (EnterTransition.None togetherWith ExitTransition.None)
                .using(SizeTransform(clip = clip) { _, _ -> M3E.sizeSpring() })
        },
        label = label
    ) { s ->
        val exiting = transition.targetState != EnterExitState.Visible
        val pr by transition.animateFloat(
            transitionSpec = { if (targetState == EnterExitState.Visible) tween(1) else tween(170) },
            label = "mph"
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val ox = originX(s, target)
        Box(
            Modifier.graphicsLayer {
                if (exiting) {
                    val k = ((pr.coerceIn(0f, 1f) - .15f) / .85f).coerceIn(0f, 1f)
                    scaleX = k
                    scaleY = .6f + .4f * k
                    transformOrigin = TransformOrigin(ox, .5f)
                }
            }
        ) { content(s) }
    }
}

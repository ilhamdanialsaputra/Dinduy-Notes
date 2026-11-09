package app.dinduy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

/** Jumlah pilihan warna judul. Urutan = nilai Note.tcl (0 = bawaan, sama seperti tampilan judul sebelum ada pilihan warna). */
const val TITLE_COLORS = 5

private fun hslOf(c: Color): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(c.toArgb(), it) }

private fun fromHsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/**
 * Warna judul. Semuanya mengikuti palet aktif (Material You / warna kustom / Monochrome):
 * 0 Utama (primary, bawaan), 1 Sekunder (rona sama, kroma diturunkan seperti peran secondary M3),
 * 2 Tersier (rona digeser +60 derajat seperti peran tertiary M3), 3 Pekat (on-primary-container), 4 Hitam.
 * Pada tema gelap, hitam murni tidak terbaca di atas latar gelap, jadi "Black" memakai warna teks tema (putih lembut).
 */
@Composable
fun titleColor(k: Int): Color {
    val pal = LocalPal.current
    return when (k) {
        1 -> hslOf(pal.p).let { fromHsl(it[0], it[1] * .5f, it[2]) }
        2 -> hslOf(pal.p).let { fromHsl(it[0] + 60f, it[1] * .95f, it[2]) }
        3 -> pal.opc
        4 -> if (pal.dark) pal.os else Color.Black
        else -> pal.p
    }
}

private val TITLE_COLOR_NAMES = listOf("Primary", "Secondary", "Tertiary", "Strong", "Black")

/**
 * Isi dock judul (tebal / miring, perataan kiri-tengah-kanan, warna huruf). Wadahnya (pil, bayangan) milik MorphDock di DocBar.kt,
 * jadi berpindah antara toolbar judul <-> isi catatan adalah satu wadah yang berubah bentuk, sama seperti toolbar lain.
 * Lebar dibatasi [DockW]; isi yang lebih panjang bisa digeser.
 */
@Composable
fun TitleBarContent(ed: Ed, vm: AppVM) {
    val n = vm.note(ed.noteId)
    val bold = n?.tbo ?: true
    val ital = n?.tit ?: false
    val al = n?.tal ?: ""
    val cur = (n?.tcl ?: 0).coerceIn(0, TITLE_COLORS - 1)
    Row(
        Modifier.widthIn(max = DockW).height(DockH).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        XGroup(i = 0) {
            XBtn(on = bold, onClick = { vm.setTitleStyle(ed.noteId, !bold, ital, cur, al) }) { Ic("b", it) }
            XBtn(on = ital, onClick = { vm.setTitleStyle(ed.noteId, bold, !ital, cur, al) }) { Ic("i", it) }
        }
        SlideSeg(
            count = 3, sel = when (al) { "c" -> 1; "r" -> 2; else -> 0 }, i = 1,
            onPick = { vm.setTitleStyle(ed.noteId, bold, ital, cur, when (it) { 1 -> "c"; 2 -> "r"; else -> "" }) }
        ) { k, c -> Ic(when (k) { 1 -> "aC"; 2 -> "aR"; else -> "aL" }, c) }
        for (k in 0 until TITLE_COLORS) {
            TitleSwatch(2 + k, titleColor(k), k == cur) { vm.setTitleStyle(ed.noteId, bold, ital, k, al) }
        }
    }
}

/** Swatch warna judul: lingkaran; terpilih = squircle bercincin; ditekan = menyempit lebih kotak. Masuk dengan melompat berurutan. */
@Composable
private fun TitleSwatch(i: Int, color: Color, sel: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 10.dp else if (sel) 15.dp else 22.dp, M3E.spatialFast(), label = "tsr")
    val ring by animateDpAsState(if (sel) 3.dp else 1.dp, M3E.spatialFast(), label = "tsb")
    val sc by animateFloatAsState(if (pr) .86f else 1f, spring(.45f, 700f), label = "tss")
    val bc by animateColorAsState(if (sel) pal.os else pal.ol, M3E.effects(), label = "tsc")
    val shape = RoundedCornerShape(r.nn())
    Box(
        Modifier.popIn(i, 40L).size(44.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(shape).background(color).border(ring.nn(), bc, shape)
            .clickable(interactionSource = src, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        Alignment.Center
    ) {
        if (sel) Ic("ok", if (color.luminance() > .5f) Color.Black else Color.White, 18.dp)
    }
}

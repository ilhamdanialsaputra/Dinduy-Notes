package app.dinduy

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Pada tema gelap, hitam murni tidak terbaca di atas latar gelap, jadi "Hitam" memakai warna teks tema (putih lembut).
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

private val TITLE_COLOR_NAMES = listOf("Utama", "Sekunder", "Tersier", "Pekat", "Hitam")

/** Kapsul alat untuk judul catatan: tebal, miring, dan warna huruf. Tampil selama judul sedang diketik. */
@Composable
fun TitleBar(ed: Ed, vm: AppVM) {
    val pal = LocalPal.current
    val n = vm.note(ed.noteId)
    val bold = n?.tbo ?: true
    val ital = n?.tit ?: false
    val cur = (n?.tcl ?: 0).coerceIn(0, TITLE_COLORS - 1)
    Column(Modifier.fillMaxWidth().background(pal.c3)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.ol.copy(alpha = .35f)))
        Row(
            Modifier.fillMaxWidth().height(54.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Tbn({ Ic("b", it) }, bold, true) { vm.setTitleStyle(ed.noteId, !bold, ital, cur) }
            Tbn({ Ic("i", it) }, ital, true) { vm.setTitleStyle(ed.noteId, bold, !ital, cur) }
            Sep()
            for (k in 0 until TITLE_COLORS) {
                TitleSwatch(titleColor(k), k == cur) { vm.setTitleStyle(ed.noteId, bold, ital, k) }
            }
        }
    }
}

@Composable
private fun TitleSwatch(color: Color, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pr) .85f else 1f, spring(.5f, 600f), label = "sw")
    val ring by animateDpAsState(if (on) 2.5.dp else 1.dp, spring(.7f, 500f), label = "swr")
    Box(
        Modifier.size(46.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        Alignment.Center
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(color)
                .border(ring, if (on) pal.os else pal.ol, CircleShape),
            Alignment.Center
        ) {
            if (on) Ic("ok", if (color.luminance() > .5f) Color.Black else Color.White, 18.dp)
        }
    }
}

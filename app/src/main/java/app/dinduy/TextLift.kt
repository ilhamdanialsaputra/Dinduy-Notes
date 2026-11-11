package app.dinduy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt

/**
 * Teks terseleksi yang diangkat (tekan-tahan di atas seleksi, lalu geser): kartu pratinjau melayang di atas jari,
 * dan penanda jatuh (kursor berkepala tetes + sorotan wadah) di sasaran. Semua gerak memakai pegas / morph, tanpa fade biasa.
 * Kartu muncul membal dari titik sentuh, mengikuti jari dengan lag lentur + miring tipis, berubah warna bila sasaran tidak sah,
 * lalu mengecil masuk ke titik jatuh (atau kembali ke asal seleksi bila batal).
 */
@Composable
fun BoxScope.TextLift(ed: Ed) {
    val live = ed.tl != null
    val vis = remember { Animatable(0f) }
    LaunchedEffect(live) {
        if (live) vis.animateTo(1f, spring(.5f, 420f)) else vis.animateTo(0f, spring(1f, 650f))
    }
    if (live || vis.value > .004f) LiftChip(ed) { vis.value }
    DropCaret(ed)
}

@Composable
private fun LiftChip(ed: Ed, v: () -> Float) {
    val pal = LocalPal.current
    val dn = LocalDensity.current
    val hold = remember { arrayOfNulls<TLift>(1) }
    ed.tl?.let { hold[0] = it }
    val t = hold[0] ?: return
    val live = ed.tl != null
    val tx = if (live) ed.tlX else (ed.tlLand?.x ?: ed.tlX)
    val ty = if (live) ed.tlY else (ed.tlLand?.y ?: ed.tlY)
    val ax = animateFloatAsState(tx, spring(.78f, 720f), label = "tlx")
    val ay = animateFloatAsState(ty, spring(.78f, 640f), label = "tly")
    val up = animateFloatAsState(if (live) 1f else 0f, spring(.7f, 500f), label = "tlu")
    val tone = animateColorAsState(if (!live || ed.tlDrop != null) pal.pc else pal.sc, spring(.9f, 500f), label = "tlc")
    var sz by remember { mutableStateOf(IntSize.Zero) }
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .offset {
                val w = sz.width.toFloat()
                val h = sz.height.toFloat()
                val gap = 44.dp.toPx()
                val sy = (ed.scroll?.value ?: 0).toFloat()
                val cy0 = ay.value - up.value * (h / 2f + gap)
                val cy = maxOf(cy0, sy + 8.dp.toPx() + h / 2f)
                val maxX = ed.widthDp * dn.density
                val cx = ax.value.coerceIn(w / 2f - 12.dp.toPx(), maxOf(w / 2f, maxX + 12.dp.toPx() - w / 2f))
                IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt())
            }
            .onSizeChanged { sz = it }
            .graphicsLayer {
                val k = v()
                val s = (.5f + .5f * k).coerceAtLeast(0f) * (1f + .04f * up.value)
                scaleX = s; scaleY = s
                rotationZ = ((ax.value - tx) / (60f * density)).coerceIn(-1f, 1f) * 5f
                alpha = if (sz == IntSize.Zero) 0f else (k * 4f).coerceIn(0f, 1f)
                shadowElevation = 16.dp.toPx() * k.coerceIn(0f, 1f)
                this.shape = shape
                clip = false
            }
            .background(tone.value, shape)
            .widthIn(max = 260.dp)
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp))
    ) {
        Column {
            T(t.text, 15f, 500, pal.opc, maxLines = 3)
            if (t.lines > 1) {
                Box(Modifier.padding(top = 8.dp).background(pal.p, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    T("${t.lines} baris", 12f, 700, pal.op, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DropCaret(ed: Ed) {
    val pal = LocalPal.current
    val d = ed.tlDrop
    val r = d?.let { ed.ptRect(it) }
    val gb = d?.let { ed.glyphRect(it.key) }
    val keep = remember { FloatArray(8) }
    if (r != null) {
        keep[0] = r.left; keep[1] = r.top; keep[2] = r.height
        if (gb != null) { keep[3] = gb.left; keep[4] = gb.top; keep[5] = gb.width; keep[6] = gb.height }
    }
    val was = remember { booleanArrayOf(false) }
    val motion = if (was[0]) spring<Float>(.85f, 700f) else snap<Float>()
    val on = animateFloatAsState(if (r != null) 1f else 0f, spring(.55f, 520f), label = "tdOn")
    val cx = animateFloatAsState(keep[0], motion, label = "tdx")
    val cy = animateFloatAsState(keep[1], motion, label = "tdy")
    val ch = animateFloatAsState(keep[2], motion, label = "tdh")
    val bx = animateFloatAsState(keep[3], motion, label = "tbx")
    val by = animateFloatAsState(keep[4], motion, label = "tby")
    val bw = animateFloatAsState(keep[5], motion, label = "tbw")
    val bh = animateFloatAsState(keep[6], motion, label = "tbh")
    SideEffect { was[0] = r != null }
    Canvas(Modifier.matchParentSize()) {
        val k = on.value
        if (k <= .004f) return@Canvas
        val pad = 6.dp.toPx()
        if (bw.value > 0f) {
            drawRoundRect(
                pal.p.copy(alpha = .08f * k.coerceIn(0f, 1f)),
                Offset(bx.value - pad, by.value - pad), Size(bw.value + pad * 2, bh.value + pad * 2), CornerRadius(14.dp.toPx())
            )
        }
        val w = 3.dp.toPx() * k
        drawRoundRect(pal.p, Offset(cx.value - w / 2f, cy.value - 1.dp.toPx()), Size(w, (ch.value + 2.dp.toPx()) * k.coerceIn(0f, 1f)), CornerRadius(w / 2f))
        drawCircle(pal.p, 5.dp.toPx() * k, Offset(cx.value, cy.value - 3.dp.toPx()))
    }
}

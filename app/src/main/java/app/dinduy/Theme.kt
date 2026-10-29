@file:OptIn(ExperimentalFoundationApi::class)

package app.dinduy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Emph = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Immutable
class Pal(
    val dark: Boolean,
    val p: Color, val op: Color, val pc: Color, val opc: Color,
    val sc: Color, val osc: Color, val tc: Color, val otc: Color,
    val s: Color, val os: Color, val osv: Color, val ol: Color,
    val c1: Color, val c2: Color, val c3: Color, val c4: Color,
    val wc: Color, val owc: Color, val ec: Color, val oec: Color, val ac: Color, val oac: Color
)

fun hsl(h: Float, s: Float, l: Float): Color {
    val hh = ((h % 360f) + 360f) % 360f
    val ss = s / 100f
    val ll = l / 100f
    val a = ss * minOf(ll, 1f - ll)
    fun f(n: Int): Float {
        val k = (n + hh / 30f) % 12f
        return (ll - a * maxOf(-1f, minOf(k - 3f, 9f - k, 1f))).coerceIn(0f, 1f)
    }
    return Color(f(0), f(8), f(4))
}

fun hueSat(hex: String): Pair<Float, Float> {
    val c = try { android.graphics.Color.parseColor(hex) } catch (e: Exception) { 0xFF6750A4.toInt() }
    val r = android.graphics.Color.red(c) / 255f
    val g = android.graphics.Color.green(c) / 255f
    val b = android.graphics.Color.blue(c) / 255f
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val d = mx - mn
    var h = 0f
    if (d != 0f) {
        h = if (mx == r) ((g - b) / d) % 6f else if (mx == g) (b - r) / d + 2f else (r - g) / d + 4f
    }
    h = (h * 60f + 360f) % 360f
    val l = (mx + mn) / 2f
    val s = if (d != 0f) d / (1f - Math.abs(2f * l - 1f)) else 0f
    return h to s * 100f
}

fun buildPal(mode: String, seed: String): Pal {
    val (h, s0) = hueSat(seed)
    val sat = maxOf(35f, minOf(s0, 88f))
    fun t(l: Float, k: Float = 1f, o: Float = 0f) = hsl(h + o, sat * k, l)
    val lt = mode == "light" || mode == "tonal"
    val amoled = mode == "amoled"
    val cl = if (lt) (if (mode == "light") 90f else 84f) else (if (amoled) 22f else 28f)
    val ol = if (lt) 14f else 90f
    val wc = hsl(38f, 70f, cl); val owc = hsl(38f, 70f, ol)
    val ec = hsl(4f, 70f, cl); val oec = hsl(4f, 70f, ol)
    val ac = hsl(h + 150f, 45f, cl); val oac = hsl(h + 150f, 45f, ol)
    return when (mode) {
        "light" -> Pal(
            false, t(40f), Color.White, t(90f), t(12f), t(88f, .4f), t(14f, .4f), t(88f, .7f, 60f), t(14f, .7f, 60f),
            t(98f, .25f), t(10f, .12f), t(35f, .15f), t(78f, .15f),
            t(96f, .25f), t(94f, .25f), t(92f, .25f), t(89f, .25f), wc, owc, ec, oec, ac, oac
        )
        "tonal" -> Pal(
            false, t(34f), Color.White, t(80f, .9f), t(10f), t(82f, .5f), t(12f, .5f), t(80f, .75f, 60f), t(12f, .75f, 60f),
            t(92f, .45f), t(10f, .2f), t(30f, .2f), t(70f, .2f),
            t(90f, .45f), t(87f, .45f), t(84f, .45f), t(80f, .45f), wc, owc, ec, oec, ac, oac
        )
        else -> Pal(
            true, t(80f), t(20f), t(if (amoled) 24f else 30f, .85f), t(90f),
            t(if (amoled) 22f else 26f, .3f), t(90f, .3f), t(if (amoled) 22f else 28f, .5f, 60f), t(90f, .5f, 60f),
            if (amoled) Color.Black else t(8f, .15f), t(90f, .1f), t(75f, .12f), t(30f, .1f),
            if (amoled) Color(0xFF080808) else t(11f, .15f), if (amoled) Color(0xFF0F0F0F) else t(13f, .15f),
            if (amoled) Color(0xFF161616) else t(17f, .15f), if (amoled) Color(0xFF1F1F1F) else t(21f, .15f),
            wc, owc, ec, oec, ac, oac
        )
    }
}

fun lerpPal(a: Pal, b: Pal, f: Float): Pal = Pal(
    b.dark, lerp(a.p, b.p, f), lerp(a.op, b.op, f), lerp(a.pc, b.pc, f), lerp(a.opc, b.opc, f),
    lerp(a.sc, b.sc, f), lerp(a.osc, b.osc, f), lerp(a.tc, b.tc, f), lerp(a.otc, b.otc, f),
    lerp(a.s, b.s, f), lerp(a.os, b.os, f), lerp(a.osv, b.osv, f), lerp(a.ol, b.ol, f),
    lerp(a.c1, b.c1, f), lerp(a.c2, b.c2, f), lerp(a.c3, b.c3, f), lerp(a.c4, b.c4, f),
    lerp(a.wc, b.wc, f), lerp(a.owc, b.owc, f), lerp(a.ec, b.ec, f), lerp(a.oec, b.oec, f),
    lerp(a.ac, b.ac, f), lerp(a.oac, b.oac, f)
)

val LocalPal = compositionLocalOf { buildPal("light", "#6750A4") }

// ---------- ikon (path SVG yang sama dengan versi web) ----------
object Pth {
    val m: Map<String, String> = mapOf(
        "set" to "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
        "more" to "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
        "up" to "M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z",
        "txt" to "M5 4v3h5.5v12h3V7H19V4z",
        "pen" to "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 000-1.41l-2.34-2.34a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
        "dl" to "M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z",
        "del" to "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
        "ok" to "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
        "plus" to "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
        "undo" to "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z",
        "redo" to "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z",
        "b" to "M15.6 10.79c.97-.67 1.65-1.77 1.65-2.79 0-2.26-1.75-4-4-4H7v14h7.04c2.09 0 3.71-1.7 3.71-3.79 0-1.52-.86-2.82-2.15-3.42zM10 6.5h3c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5h-3v-3zm3.5 9H10v-3h3.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5z",
        "i" to "M10 4v3h2.21l-3.42 8H6v3h8v-3h-2.21l3.42-8H18V4z",
        "u" to "M12 17c3.31 0 6-2.69 6-6V3h-2.5v8c0 1.93-1.57 3.5-3.5 3.5S8.5 12.93 8.5 11V3H6v8c0 3.31 2.69 6 6 6zm-7 2v2h14v-2H5z",
        "s" to "M10 19h4v-3h-4v3zM5 4v3h5v3h4V7h5V4H5zM3 14h18v-2H3v2z",
        "mark" to "M6 14l3 3v5h6v-5l3-3V9H6zm5-12h2v3h-2zM3.5 5.88l1.41-1.41 2.12 2.12L5.62 8zm13.46.71l2.12-2.12 1.41 1.41L18.38 8z",
        "code" to "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z",
        "ul" to "M4 10.5c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5 1.5-.67 1.5-1.5-.67-1.5-1.5-1.5zm0-6c-.83 0-1.5.67-1.5 1.5S3.17 7.5 4 7.5 5.5 6.83 5.5 6 4.83 4.5 4 4.5zm0 12c-.83 0-1.5.68-1.5 1.5s.68 1.5 1.5 1.5 1.5-.68 1.5-1.5-.67-1.5-1.5-1.5zM7 19h14v-2H7v2zm0-6h14v-2H7v2zm0-8v2h14V5H7z",
        "ol" to "M2 17h2v.5H3v1h1v.5H2v1h3v-4H2v1zm1-9h1V4H2v1h1v3zm-1 3h1.8L2 13.1v.9h3v-1H3.2L5 10.9V10H2v1zm5-6v2h14V5H7zm0 14h14v-2H7v2zm0-6h14v-2H7v2z",
        "chk" to "M22 7h-9v2h9V7zm0 8h-9v2h9v-2zM5.54 11L2 7.46l1.41-1.41 2.12 2.12 4.24-4.24 1.41 1.41L5.54 11zm0 8L2 15.46l1.41-1.41 2.12 2.12 4.24-4.24 1.41 1.41L5.54 19z",
        "quote" to "M6 17h3l2-4V7H5v6h3zm8 0h3l2-4V7h-6v6h3z",
        "pre" to "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8h16v10zm-2-1h-6v-2h6v2zM7.5 17l-1.41-1.41L8.67 13l-2.58-2.59L7.5 9l4 4-4 4z",
        "table" to "M3 3v18h18V3H3zm8 16H5v-5h6v5zm0-7H5V7h6v5zm8 7h-6v-5h6v5zm0-7h-6V7h6v5z",
        "hr" to "M4 11h16v2H4z",
        "ind" to "M3 21h18v-2H3v2zM3 8v8l4-4-4-4zm8 9h10v-2H11v2zM3 3v2h18V3H3zm8 6h10V7H11v2zm0 4h10v-2H11v2z",
        "out" to "M11 17h10v-2H11v2zm-8-5l4 4V8l-4 4zm0 9h18v-2H3v2zM3 3v2h18V3H3zm8 6h10V7H11v2zm0 4h10v-2H11v2z",
        "hide" to "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z",
        "x" to "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
        "dup" to "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
        "all" to "M3 5h2V3c-1.1 0-2 .9-2 2zm0 8h2v-2H3v2zm4 8h2v-2H7v2zM3 9h2V7H3v2zm10-6h-2v2h2V3zm6 0v2h2c0-1.1-.9-2-2-2zM5 21v-2H3c0 1.1.9 2 2 2zm-2-4h2v-2H3v2zM9 3H7v2h2V3zm2 18h2v-2h-2v2zm8-8h2v-2h-2v2zm0 8c1.1 0 2-.9 2-2h-2v2zm0-12h2V7h-2v2zm0 8h2v-2h-2v2zm-4 4h2v-2h-2v2zm0-16h2V3h-2v2zM7 17h10V7H7v10zm2-8h6v6H9V9z",
        "cols" to "M4 4h7v16H4zM13 4h7v16h-7z",
        "rows" to "M4 4h16v7H4zM4 13h16v7H4z",
        "sort" to "M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z",
        "cal" to "M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zM9 14H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2zm-8 4H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z",
        "lib" to "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9h-4v4h-2v-4H9V9h4V5h2v4h4v2z",
        "img" to "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z",
        "aL" to "M15 15H3v2h12v-2zm0-8H3v2h12V7zM3 13h18v-2H3v2zm0 8h18v-2H3v2zM3 3v2h18V3H3z",
        "aC" to "M7 15v2h10v-2H7zm-4 6h18v-2H3v2zm0-8h18v-2H3v2zm4-6v2h10V7H7zM3 3v2h18V3H3z",
        "aR" to "M3 21h18v-2H3v2zm6-4h12v-2H9v2zm-6-4h18v-2H3v2zm6-4h12V7H9v2zM3 3v2h18V3H3z",
        "menu" to "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z",
        "pin" to "M16 9V4h1c.55 0 1-.45 1-1s-.45-1-1-1H7c-.55 0-1 .45-1 1s.45 1 1 1h1v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3z",
        "arc" to "M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z",
        "unarc" to "M20.55 5.22l-1.39-1.68c-.28-.34-.69-.54-1.16-.54H6c-.47 0-.88.21-1.15.55L3.46 5.22C3.17 5.57 3 6.01 3 6.5V19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.49-.17-.93-.45-1.28zM12 9.5l5.5 5.5H14v2h-4v-2H6.5L12 9.5zM5.12 5l.82-1h12l.93 1H5.12z",
        "restore" to "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z",
        "lm" to "M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0 .39-.39.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z",
        "pal" to "M12 2C6.49 2 2 6.49 2 12s4.49 10 10 10c1.38 0 2.5-1.12 2.5-2.5 0-.61-.23-1.2-.64-1.67-.08-.1-.13-.21-.13-.33 0-.28.22-.5.5-.5H16c3.31 0 6-2.69 6-6 0-4.96-4.49-9-10-9zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 8 6.5 8 8 8.67 8 9.5 7.33 11 6.5 11zm3-4C8.67 7 8 6.33 8 5.5S8.67 4 9.5 4s1.5.67 1.5 1.5S10.33 7 9.5 7zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 4 14.5 4s1.5.67 1.5 1.5S15.33 7 14.5 7zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 8 17.5 8s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
        "dm" to "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z",
        "am" to "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18V4c4.41 0 8 3.59 8 8s-3.59 8-8 8z",
        "back" to "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
        "search" to "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
        "aup" to "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z",
        "adn" to "M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z",
        "chr" to "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z",
        "cut" to "M9.64 7.64c.23-.5.36-1.05.36-1.64 0-2.21-1.79-4-4-4S2 3.79 2 6s1.79 4 4 4c.59 0 1.14-.13 1.64-.36L10 12l-2.36 2.36C7.14 14.13 6.59 14 6 14c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4c0-.59-.13-1.14-.36-1.64L12 14l7 7h3v-1L9.64 7.64zM6 8c-1.1 0-2-.89-2-2s.9-2 2-2 2 .89 2 2-.9 2-2 2zm0 12c-1.1 0-2-.89-2-2s.9-2 2-2 2 .89 2 2-.9 2-2 2zm6-7.5c-.28 0-.5-.22-.5-.5s.22-.5.5-.5.5.22.5.5-.22.5-.5.5zM19 3l-6 6 2 2 7-7V3z",
        "chl" to "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"
    )
}

@Composable
fun Ic(n: String, tint: Color, sz: Dp = 24.dp, modifier: Modifier = Modifier) {
    val path = remember(n) { PathParser().parsePathString(Pth.m[n] ?: "").toPath() }
    Canvas(modifier.size(sz)) {
        val sc = this.size.width / 24f
        scale(sc, sc, pivot = Offset.Zero) { drawPath(path, tint) }
    }
}

@Composable
fun T(
    text: String, size: Float, w: Int, color: Color, modifier: Modifier = Modifier,
    ls: Float = 0f, maxLines: Int = Int.MAX_VALUE, lh: Float = 0f, alpha: Float = 1f,
    align: TextAlign? = null
) {
    Text(
        text, if (alpha < 1f) modifier.alpha(alpha) else modifier, color = color,
        fontSize = size.sp, fontWeight = FontWeight(w), letterSpacing = ls.em, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = if (lh > 0f) lh.em else TextUnit.Unspecified, textAlign = align
    )
}

@Composable
fun IconBtn(
    ic: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 52.dp, bg: Color = Color.Transparent, iconSize: Dp = 24.dp, enabled: Boolean = true
) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else size / 2, spring(0.6f, 600f), label = "r")
    val sc by animateFloatAsState(if (pr) .9f else 1f, spring(.5f, 600f), label = "s")
    Box(
        modifier.size(size).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick),
        Alignment.Center
    ) { Ic(ic, tint, iconSize) }
}

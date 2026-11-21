package app.dinduy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layar muat awal (Material 3 Expressive): tampil sekali tiap proses app dimulai, sampai sampul, tekstur 3D buku, dan halaman PDF teratas
 * selesai disiapkan (lihat AppVM.boot). Beranda sudah dikomposisi di baliknya, jadi saat layar ini memudar semua catatan (1 atau 2 kolom)
 * sudah jadi, tanpa animasi masuk satu-satu dan tanpa jeda di ketukan pertama.
 * Digambar paling atas dan menelan semua sentuhan selama tampil.
 */
@Composable
fun BootSplash(vm: AppVM) {
    val pal = LocalPal.current
    // masuk: blob + judul memantul masuk berurutan (spring spasial ekspresif)
    val blobIn = remember { Animatable(0f) }
    val titleIn = remember { Animatable(0f) }
    val subIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { blobIn.animateTo(1f, spring(.52f, 320f)) }
        launch { delay(120); titleIn.animateTo(1f, spring(.7f, 380f)) }
        launch { delay(260); subIn.animateTo(1f, tween(320)) }
    }
    // keluar: memudar + sedikit membesar (isi seperti 'tembus' ke beranda)
    val out by animateFloatAsState(
        if (vm.bootDone) 1f else 0f, tween(420, easing = CubicBezierEasing(.05f, .7f, .1f, 1f)), label = "bootOut"
    )
    if (vm.bootDone && out >= .999f) return
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - out }
            .background(pal.bg)
            .pointerInput(Unit) {
                // selama tampil, tidak ada sentuhan yang boleh tembus ke beranda
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.graphicsLayer { val s = 1f + .08f * out; scaleX = s; scaleY = s },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoadingBlob(
                Modifier.size(72.dp).graphicsLayer {
                    val p = blobIn.value.coerceAtLeast(0f)
                    scaleX = p; scaleY = p; alpha = p.coerceIn(0f, 1f)
                }
            )
            Spacer(Modifier.height(28.dp))
            T(
                "Dinduy", 30f, 900, pal.os,
                Modifier.graphicsLayer {
                    val p = titleIn.value
                    alpha = p.coerceIn(0f, 1f); translationY = (1f - p) * 14.dp.toPx()
                },
                ls = -.01f
            )
            Spacer(Modifier.height(6.dp))
            T("Getting your notes ready", 14f, 600, pal.osv, Modifier.graphicsLayer { alpha = subIn.value.coerceIn(0f, 1f) })
        }
    }
}

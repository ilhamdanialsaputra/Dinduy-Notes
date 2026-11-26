package app.dinduy

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val ABOUT = "A fast, private notes app with rich text, images, PDF reading, handwriting, " +
    "reminders and voice notes with on-device transcription. Your notes are stored on your phone."

const val REPO_URL = "https://github.com/ilhamdanialsaputra/Dinduy-Notes"

private fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (e: Exception) { "?" }

private const val DONATE_URL = "https://ko-fi.com/ilhamdanials#checkoutModal"

private fun openUrl(ctx: android.content.Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

/** Satu baris kartu bersambung (gaya sama dengan Pengaturan): ikon bulat tonal + judul + keterangan. busy = ikon diganti indikator memuat. */
@Composable
private fun InfoRow(pos: Int, icon: String, title: String, sub: String, busy: Boolean = false, onClick: (() -> Unit)?) {
    val pal = LocalPal.current
    SCard(pos, onClick, PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(pal.pc), Alignment.Center) {
                if (busy) LoadingBlob(Modifier.size(24.dp)) else Ic(icon, pal.opc, 22.dp)
            }
            Column(Modifier.weight(1f)) {
                T(title, 16f, 500, pal.os)
                T(sub, 14f, 400, pal.osv, lh = 1.3f)
            }
        }
    }
}

/**
 * Halaman Info (menu Info di laci). Satu halaman navigasi biasa seperti Pengaturan: masuk dari kanan lewat ViewLayer
 * (beranda di belakangnya bergeser pelan + meredup), judul besar yang mengecil saat digulir, bilah atas yang berubah warna,
 * dan kelompok kartu bersambung yang masuk bergantian. Tanpa ikon aplikasi besar.
 */
@Composable
fun InfoScreen(onBack: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val sat = satDp()
    val sab = sabDp()
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(sat + 64.dp))
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + sab)
            ) {
                T(
                    "Info", 40f, 400, pal.os,
                    Modifier.padding(start = 8.dp, top = 20.dp, bottom = 32.dp).graphicsLayer {
                        val k = (scroll.value / 110.dp.toPx()).coerceIn(0f, 1f)
                        alpha = 1f - (k * 1.6f).coerceAtMost(1f)
                        scaleX = 1f - .12f * k; scaleY = 1f - .12f * k
                        transformOrigin = TransformOrigin(0f, .5f)
                    },
                    ls = -.01f, maxLines = 1
                )
                SGroup("About", 0) {
                    SCard(POS_ONE, null, PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
                        T("Dinduy", 20f, 500, pal.os)
                        T("Version " + appVersion(ctx), 14f, 500, pal.p, Modifier.padding(top = 2.dp))
                        T(ABOUT, 14f, 400, pal.osv, Modifier.padding(top = 12.dp), lh = 1.4f)
                    }
                }
                SGroup("Project", 60) {
                    InfoRow(POS_TOP, "code", "Source code", REPO_URL.removePrefix("https://")) { openUrl(ctx, REPO_URL) }
                    InfoRow(POS_MID, "notes", "What's new", "Release notes for every version") { openUrl(ctx, REPO_URL + "/releases") }
                    InfoRow(POS_BOT, "pen", "Report an issue", "Found a bug or have an idea?") { openUrl(ctx, REPO_URL + "/issues") }
                }
                SGroup("Support", 120) {
                    InfoRow(POS_ONE, "heart", "Donate", "Support Dinduy's development on Ko-fi") { openUrl(ctx, DONATE_URL) }
                }
                SGroup("Privacy", 180) {
                    InfoRow(POS_ONE, "phone", "Stored on your phone", "Your notes stay on this device unless you share or back them up", onClick = null)
                }
            }
        }
        // bilah atas: transparan di puncak, berubah jadi warna wadah saat digulir; judul kecil menggantikan judul besar (sama dengan Pengaturan)
        Row(
            Modifier.fillMaxWidth().height(sat + 64.dp)
                .drawBehind {
                    val k = (scroll.value / 80.dp.toPx()).coerceIn(0f, 1f)
                    drawRect(androidx.compose.ui.graphics.lerp(pal.bg, pal.c2, k))
                }
                .padding(top = sat, start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("back", pal.os, onBack)
            T(
                "Info", 22f, 400, pal.os,
                Modifier.padding(start = 8.dp).graphicsLayer {
                    val k = (scroll.value / 110.dp.toPx()).coerceIn(0f, 1f)
                    val a = ((k - .5f) * 2f).coerceIn(0f, 1f)
                    alpha = a; translationY = (1f - a) * 12.dp.toPx()
                },
                maxLines = 1
            )
        }
    }
}

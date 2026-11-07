package app.dinduy

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val ABOUT = "Dinduy is a fast, private notes app with rich text, images, PDF reading, handwriting, " +
    "reminders and voice notes with on-device transcription. Your notes are stored on your phone."

private fun openUrl(ctx: android.content.Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

/** Satu baris daftar gaya aplikasi Google: ikon polos di kiri, judul + keterangan, tanpa kartu. */
@Composable
private fun InfoRow(icon: String, title: String, sub: String?, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp)) { Ic(icon, pal.osv, 24.dp) }
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            T(title, 16f, 400, pal.os)
            if (sub != null) T(sub, 14f, 400, pal.osv, Modifier.padding(top = 2.dp), lh = 1.3f)
        }
    }
}

/**
 * Halaman Info (menu Info di laci): satu halaman penuh bergaya "About" aplikasi resmi Google.
 * Bilah atas polos dengan panah kembali, ikon aplikasi besar di tengah, nama + versi, tombol Check for updates (pil terisi),
 * lalu daftar baris datar dengan pemisah tipis. Muncul dengan pudar + naik sedikit, hilang dengan pudar.
 */
@Composable
fun InfoHost() {
    val open = UpdState.infoOpen
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val p = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) { shown = true; p.animateTo(1f, tween(280, easing = Emph)) }
        else if (shown) { p.animateTo(0f, tween(180)); shown = false }
    }
    if (!shown) return
    BackHandler(enabled = open) { UpdState.infoOpen = false }
    val sat = satDp()
    val sab = sabDp()
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 28.dp.toPx() }
            .background(pal.s)
            .pointerInput(Unit) { }   // menelan sentuhan: halaman di belakang tidak ikut bereaksi
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(sat + 64.dp))
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(bottom = 24.dp + sab),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // hero: ikon aplikasi, nama, versi
                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(pal.pc), Alignment.Center) {
                    Ic("notes", pal.opc, 48.dp)
                }
                Spacer(Modifier.height(20.dp))
                T("Dinduy", 32f, 400, pal.os, ls = -.01f)
                Spacer(Modifier.height(4.dp))
                T("Version " + Upd.currentVersion(ctx), 14f, 500, pal.osv)
                Spacer(Modifier.height(24.dp))
                T(
                    ABOUT, 15f, 400, pal.os, Modifier.padding(horizontal = 32.dp), lh = 1.45f,
                    align = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                // tombol utama: pil terisi, berubah jadi progres saat memeriksa
                Box(
                    Modifier.height(48.dp).clip(CircleShape).background(if (UpdState.checking) pal.c4 else pal.p)
                        .clickable(enabled = !UpdState.checking) { Upd.check(ctx) }
                        .padding(horizontal = 28.dp),
                    Alignment.Center
                ) {
                    if (UpdState.checking) Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingBlob(Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        T("Checking…", 15f, 500, pal.os)
                    } else T("Check for updates", 15f, 500, pal.op)
                }
                val m = UpdState.message
                if (m != null) {
                    Spacer(Modifier.height(14.dp))
                    T(m, 14f, 400, pal.osv, Modifier.padding(horizontal = 32.dp), align = androidx.compose.ui.text.style.TextAlign.Center, lh = 1.35f)
                }
                Spacer(Modifier.height(28.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(pal.ol.copy(alpha = .6f)))
                T("Project", 14f, 500, pal.p, Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, bottom = 4.dp))
                InfoRow("code", "Source code", Upd.REPO_URL.removePrefix("https://")) { openUrl(ctx, Upd.REPO_URL) }
                InfoRow("notes", "What's new", "Release notes for every version") { openUrl(ctx, Upd.REPO_URL + "/releases") }
                InfoRow("pen", "Report an issue", "Found a bug or have an idea?") { openUrl(ctx, Upd.REPO_URL + "/issues") }
                Box(Modifier.fillMaxWidth().height(1.dp).background(pal.ol.copy(alpha = .6f)))
                T("Privacy", 14f, 500, pal.p, Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, bottom = 4.dp))
                InfoRow("phone", "Stored on your phone", "Your notes stay on this device unless you share or back them up", {})
            }
        }
        // bilah atas polos: hanya panah kembali (tanpa judul besar), seperti halaman About di aplikasi Google
        Row(
            Modifier.fillMaxWidth().height(sat + 64.dp).background(pal.s).padding(top = sat, start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("back", pal.os, { UpdState.infoOpen = false })
            T("About", 22f, 400, pal.os, Modifier.padding(start = 8.dp), maxLines = 1)
        }
    }
}

/** Dialog versi baru + unduhan berprogres + izin pasang. */
@Composable
fun UpdateHost() {
    val r = UpdState.release ?: return
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val ready = UpdState.ready

    // APK selesai diunduh: buka installer (efek ini jalan saat aplikasi tampil di depan)
    LaunchedEffect(ready) { if (ready != null) Upd.install(ctx, ready) }

    fun dismiss() {
        Upd.cancel()
        UpdState.release = null; UpdState.ready = null; UpdState.dlError = null
    }

    if (UpdState.needPerm) {
        AlertDialog(
            onDismissRequest = { UpdState.needPerm = false },
            containerColor = pal.c3,
            title = { T("Allow installs", 22f, 800, pal.os) },
            text = { T("To install updates, allow Dinduy to install apps in system settings. Then come back and tap Download again.", 15f, 500, pal.os) },
            confirmButton = {
                TextButton(onClick = { UpdState.needPerm = false; Upd.openInstallSettings(ctx) }) { T("Open settings", 16f, 900, pal.p) }
            },
            dismissButton = { TextButton(onClick = { UpdState.needPerm = false }) { T("Cancel", 16f, 700, pal.osv) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!UpdState.downloading) dismiss() },
        containerColor = pal.c3,
        title = { T("Update available", 22f, 800, pal.os) },
        text = {
            Column {
                T("Version " + r.tag.removePrefix("v").removePrefix("V") + " is available (you have " + Upd.currentVersion(ctx) + ").", 15f, 700, pal.p)
                if (r.notes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) { T(r.notes, 14f, 500, pal.os) }
                }
                if (UpdState.downloading || ready != null) {
                    Spacer(Modifier.height(16.dp))
                    val p = UpdState.progress
                    if (p in 0f..1f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = pal.p)
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = pal.p)
                    Spacer(Modifier.height(6.dp))
                    T(
                        when {
                            ready != null -> "Downloaded. Opening the installer..."
                            p in 0f..1f -> "Downloading " + (p * 100).toInt() + "%"
                            else -> "Downloading..."
                        }, 13f, 600, pal.osv
                    )
                }
                UpdState.dlError?.let { Spacer(Modifier.height(10.dp)); T(it, 14f, 600, pal.osv) }
            }
        },
        confirmButton = {
            when {
                ready != null -> TextButton(onClick = { Upd.install(ctx, ready) }) { T("Install", 16f, 900, pal.p) }
                UpdState.downloading -> {}
                else -> TextButton(onClick = { Upd.download(ctx) }) { T(if (UpdState.dlError != null) "Retry" else "Download", 16f, 900, pal.p) }
            }
        },
        dismissButton = {
            TextButton(onClick = { dismiss() }) { T(if (UpdState.downloading) "Cancel" else "Later", 16f, 700, pal.osv) }
        }
    )
}

package app.dinduy

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Menu Info: nama aplikasi, versi, deskripsi, tautan repo, tombol Check for updates. */
@Composable
fun InfoHost() {
    if (!UpdState.infoOpen) return
    val pal = LocalPal.current
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { UpdState.infoOpen = false },
        containerColor = pal.c3,
        title = { T("Dinduy", 24f, 800, pal.os) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                T("Version " + Upd.currentVersion(ctx), 15f, 700, pal.p)
                Spacer(Modifier.height(12.dp))
                T(ABOUT, 15f, 500, pal.os)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clickable {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Upd.REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }.padding(vertical = 6.dp)) { T(Upd.REPO_URL.removePrefix("https://"), 14f, 600, pal.p) }
                val m = UpdState.message
                if (m != null) {
                    Spacer(Modifier.height(10.dp))
                    T(m, 14f, 600, pal.osv)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !UpdState.checking, onClick = { Upd.check(ctx) }) {
                T(if (UpdState.checking) "Checking..." else "Check for updates", 16f, 900, pal.p)
            }
        },
        dismissButton = { TextButton(onClick = { UpdState.infoOpen = false }) { T("Close", 16f, 700, pal.osv) } }
    )
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

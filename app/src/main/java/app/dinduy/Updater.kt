package app.dinduy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext

/** Keadaan UI untuk menu Info dan pembaruan dari dalam aplikasi. */
object UpdState {
    var infoOpen by mutableStateOf(false)
    var checking by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)     // pesan singkat (sudah terbaru / gagal / offline)
    var release by mutableStateOf<Upd.Release?>(null) // ada versi baru: tampilkan dialog
    var downloading by mutableStateOf(false)
    var progress by mutableFloatStateOf(-1f)          // 0..1, -1 = ukuran tidak diketahui
    var dlError by mutableStateOf<String?>(null)
    var ready by mutableStateOf<File?>(null)          // APK selesai diunduh, siap dipasang
    var needPerm by mutableStateOf(false)             // pengguna belum mengizinkan "pasang dari aplikasi ini"
}

object Upd {
    const val REPO_URL = "https://github.com/ilhamdanialsaputra/Dinduy-Notes"
    private const val API = "https://api.github.com/repos/ilhamdanialsaputra/Dinduy-Notes/releases/latest"

    class Release(val tag: String, val notes: String, val apkUrl: String?, val apkName: String?, val size: Long)

    private sealed class Res {
        class Ok(val r: Release) : Res()
        object None : Res()
        class Err(val offline: Boolean) : Res()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    /** "v1.2.3-beta" -> [1, 2, 3]; awalan "v" dan akhiran non-angka diabaikan. */
    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            .split('.').map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun fetchLatest(): Res {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Dinduy-Notes")
            }
            when (c.responseCode) {
                200 -> {
                    val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    val tag = j.optString("tag_name")
                    if (tag.isEmpty()) return Res.Err(false)
                    var url: String? = null
                    var name: String? = null
                    var size = 0L
                    val assets = j.optJSONArray("assets")
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", true)) {
                            url = a.optString("browser_download_url"); name = a.optString("name"); size = a.optLong("size")
                            break
                        }
                    }
                    Res.Ok(Release(tag, shortNotes(j.optString("body")), url, name, size))
                }
                404 -> Res.None   // belum ada rilis
                else -> Res.Err(false)
            }
        } catch (e: UnknownHostException) { Res.Err(true)
        } catch (e: SocketTimeoutException) { Res.Err(true)
        } catch (e: IOException) { Res.Err(true)
        } catch (e: Exception) { Res.Err(false)
        } finally { c?.disconnect() }
    }

    /** Catatan rilis singkat: buang markup, baris "Full Changelog", dan batasi panjangnya. */
    private fun shortNotes(body: String): String {
        val lines = body.lines().map { it.trim().trimStart('#', '*', '>').replace("**", "").replace("`", "").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Full Changelog", true) && !it.startsWith("What's Changed", true) }
        val s = lines.joinToString("\n")
        return if (s.length > 500) s.take(500).trimEnd() + "..." else s
    }

    /** Cek versi terbaru; hasilnya mengisi UpdState (dialog update atau pesan singkat). */
    fun check(ctx: Context) {
        if (UpdState.checking) return
        UpdState.checking = true
        UpdState.message = null
        val app = ctx.applicationContext
        scope.launch {
            val res = fetchLatest()
            val cur = currentVersion(app)
            UpdState.checking = false
            when (res) {
                is Res.Err -> UpdState.message = if (res.offline) "No internet connection. Try again later." else "Couldn't check for updates."
                Res.None -> UpdState.message = "You're up to date ($cur)."
                is Res.Ok -> when {
                    !isNewer(res.r.tag, cur) -> UpdState.message = "You're up to date ($cur)."
                    res.r.apkUrl == null -> UpdState.message = "Version ${res.r.tag.removePrefix("v")} is out, but its APK isn't available yet."
                    else -> {
                        UpdState.dlError = null; UpdState.ready = null; UpdState.progress = -1f
                        UpdState.infoOpen = false
                        UpdState.release = res.r
                    }
                }
            }
        }
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Mulai unduh APK dari asset rilis (dengan progres). Bila izin pasang belum ada, minta dulu. */
    fun download(ctx: Context) {
        val r = UpdState.release ?: return
        val url = r.apkUrl ?: return
        if (UpdState.downloading) return
        if (!canInstall(ctx)) { UpdState.needPerm = true; return }
        val app = ctx.applicationContext
        UpdState.downloading = true
        UpdState.dlError = null
        UpdState.progress = -1f
        job = scope.launch {
            val dir = File(app.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "Dinduy-${r.tag}.apk")
            val part = File(dir, out.name + ".part")
            var c: HttpURLConnection? = null
            try {
                c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Dinduy-Notes")
                }
                if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
                val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
                c.inputStream.use { ins ->
                    part.outputStream().use { os ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        var last = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = ins.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - last > 100) { last = now; UpdState.progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f }
                        }
                        if (total > 0 && done != total) throw IOException("Incomplete download")
                    }
                }
                if (part.length() < 1024 || !part.renameTo(out)) throw IOException("Invalid file")
                UpdState.progress = 1f
                UpdState.ready = out
            } catch (e: kotlinx.coroutines.CancellationException) {
                part.delete(); throw e
            } catch (e: Exception) {
                part.delete()
                UpdState.dlError = if (e is UnknownHostException || e is SocketTimeoutException) "No internet connection." else "Download failed. Try again."
            } finally {
                c?.disconnect()
                UpdState.downloading = false
            }
        }
    }

    fun cancel() { job?.cancel(); job = null; UpdState.downloading = false }

    /** Buka installer sistem lewat FileProvider (file_paths.xml: cache-path). */
    fun install(ctx: Context, apk: File) {
        if (!canInstall(ctx)) { UpdState.needPerm = true; return }
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            UpdState.dlError = "Couldn't open the installer."
        }
    }

    /** Halaman izin "Pasang aplikasi tidak dikenal" khusus untuk Dinduy. */
    fun openInstallSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

package app.dinduy

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Data pratinjau satu tautan: judul halaman dan alamat gambar (og:image, bisa kosong). */
class LinkMeta(val title: String, val image: String)

/**
 * Ambil pratinjau tautan (judul + gambar) tanpa library: HttpURLConnection di thread latar, gambar diunduh dan di-decode
 * manual jadi Bitmap. Hasil di-cache (memori untuk data dan gambar, berkas di cacheDir untuk gambar) supaya tidak diunduh ulang.
 * Gagal / offline -> null (kartu tampil sebagai tautan biasa), dan tidak dicoba lagi selama beberapa menit.
 *
 * Kenapa cepat: HTML dibaca hanya sampai </head> (bukan seluruh halaman), gambar mulai diunduh begitu alamatnya diketahui
 * (tanpa menunggu kartu digambar ulang), permintaan yang sama dipakai bersama, dan unduhan tidak ikut batal saat kartu
 * keluar layar. YouTube tidak diambil dari halamannya (sering diganti halaman persetujuan / berat), tapi dari oEmbed +
 * thumbnail resmi i.ytimg.com.
 */
object LinkPreviews {
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    // beberapa situs (media sosial, toko) hanya memberi og:tag ke crawler pratinjau
    private const val UA_BOT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private const val RETRY_MS = 5 * 60 * 1000L
    private const val MAX_HTML = 300_000
    private const val MAX_IMG = 4_000_000

    private val metas = ConcurrentHashMap<String, LinkMeta>()
    private val failed = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metaFlight = ConcurrentHashMap<String, Deferred<LinkMeta?>>()
    private val imgFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val bitmaps = object : android.util.LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private class Conn(val c: HttpURLConnection, val url: String)

    private fun recentlyFailed(key: String): Boolean {
        val t = failed[key] ?: return false
        if (System.currentTimeMillis() - t < RETRY_MS) return true
        failed.remove(key)
        return false
    }

    /** Buka koneksi dan ikuti pengalihan (termasuk http -> https) sampai 6 kali. */
    private fun open(start: String, accept: String, ua: String = UA): Conn {
        var cur = start
        for (i in 0 until 6) {
            if (!cur.startsWith("http://") && !cur.startsWith("https://")) throw IOException("bad scheme")
            val c = URL(cur).openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 6000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", ua)
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("Accept-Language", "en,id;q=0.8")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) throw IOException("redirect without location")
                cur = URL(URL(cur), loc).toString()
                continue
            }
            if (code !in 200..299) {
                c.disconnect()
                throw IOException("http $code")
            }
            return Conn(c, cur)
        }
        throw IOException("too many redirects")
    }

    private fun readLimited(ins: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = ins.read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Baca HTML hanya sampai penutup </head>: og:title / og:image selalu ada di dalamnya. */
    private fun readHead(ins: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var tail = ""
        while (out.size() < max) {
            val n = ins.read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
            val chunk = tail + String(buf, 0, n, Charsets.ISO_8859_1)
            if (chunk.contains("</head", ignoreCase = true)) break
            tail = chunk.takeLast(8)
        }
        return out.toByteArray()
    }

    private fun clean(s: String): String = s.replace(Regex("\\s+"), " ").trim().take(300)

    private val RX_YT = Regex("""(?:youtu\.be/|youtube(?:-nocookie)?\.com/(?:watch\?(?:[^#]*&)?v=|shorts/|embed/|live/|v/))([A-Za-z0-9_-]{11})""")

    /** Id video kalau [url] tautan YouTube (watch, youtu.be, shorts, embed, live), selain itu null. */
    fun youtubeId(url: String): String? = RX_YT.find(url)?.groupValues?.get(1)

    /** Judul lewat oEmbed (JSON kecil), gambar dari thumbnail resmi. Gagal / offline / video privat -> exception -> tautan biasa. */
    private fun youtubeMeta(id: String): LinkMeta {
        val api = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8")
        val cn = open(api, "application/json")
        val title = try {
            val txt = String(cn.c.inputStream.use { readLimited(it, 60_000) }, Charsets.UTF_8)
            clean(JSONObject(txt).optString("title"))
        } finally {
            cn.c.disconnect()
        }
        return LinkMeta(title, "https://i.ytimg.com/vi/$id/maxresdefault.jpg")
    }

    /** Judul + og:image dari HTML halaman. null = halaman terbaca tapi tidak punya data; exception = gagal jaringan / HTTP. */
    private fun htmlMeta(url: String, ua: String): LinkMeta? {
        val cn = open(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5", ua)
        try {
            val ct = cn.c.contentType ?: ""
            val cs = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)
            val bytes = cn.c.inputStream.use { readHead(it, MAX_HTML) }
            val doc = try {
                Jsoup.parse(ByteArrayInputStream(bytes), cs, cn.url)
            } catch (e: Exception) {
                Jsoup.parse(ByteArrayInputStream(bytes), "UTF-8", cn.url)
            }
            fun pick(vararg keys: String): String {
                for (k in keys) {
                    val v = (doc.selectFirst("meta[property=\"$k\"]") ?: doc.selectFirst("meta[name=\"$k\"]"))
                        ?.attr("content")?.trim().orEmpty()
                    if (v.isNotEmpty()) return v
                }
                return ""
            }
            val title = clean(pick("og:title", "twitter:title").ifEmpty { doc.title() })
            var img = pick("og:image", "og:image:secure_url", "og:image:url", "twitter:image", "twitter:image:src")
            if (img.isNotEmpty()) img = try { URL(URL(cn.url), img).toString() } catch (e: Exception) { "" }
            return if (title.isEmpty() && img.isEmpty()) null else LinkMeta(title, img)
        } finally {
            cn.c.disconnect()
        }
    }

    private fun loadMeta(url: String): LinkMeta? = try {
        val yt = youtubeId(url)
        if (yt != null) youtubeMeta(yt)
        else {
            // ditolak (403 / 429 dll.) atau tanpa data untuk browser: coba sekali lagi sebagai crawler pratinjau
            val a = try { htmlMeta(url, UA) } catch (e: IOException) { if (e.message?.startsWith("http") == true) null else throw e }
            a ?: htmlMeta(url, UA_BOT)
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Judul dan gambar halaman [url]; null kalau gagal / offline / tidak ada isinya. Kalau [ctx] diberikan, gambarnya langsung
     * diunduh di latar begitu alamatnya ketemu. Hasilnya tetap tersimpan walau pemanggil sudah keluar dari layar.
     */
    suspend fun meta(url: String, ctx: Context? = null): LinkMeta? {
        metas[url]?.let { return it }
        if (recentlyFailed(url)) return null
        val app = ctx?.applicationContext
        val d = metaFlight.getOrPut(url) {
            scope.async {
                val r = loadMeta(url)
                if (r != null) {
                    metas[url] = r
                    if (app != null && r.image.isNotEmpty()) scope.launch { image(app, r.image) }
                } else failed[url] = System.currentTimeMillis()
                metaFlight.remove(url)
                r
            }
        }
        return d.await()
    }

    private fun keyOf(url: String) = Integer.toHexString(url.hashCode()) + "_" + url.length

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= 900) s *= 2
        val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        return bm.asImageBitmap()
    }

    private val RX_YT_MAX = Regex("^(https://i\\.ytimg\\.com/vi/[A-Za-z0-9_-]{11}/)maxresdefault\\.jpg$")

    /** Alamat yang dicoba berurutan. Thumbnail YouTube beresolusi penuh tidak selalu ada, jadi ada cadangan yang pasti ada. */
    private fun candidates(url: String): List<String> {
        val base = RX_YT_MAX.find(url)?.groupValues?.get(1) ?: return listOf(url)
        return listOf(url, base + "sddefault.jpg", base + "hqdefault.jpg")
    }

    private fun fetchDecode(ctx: Context, url: String): ImageBitmap? {
        val dir = File(ctx.cacheDir, "lp").also { it.mkdirs() }
        val f = File(dir, keyOf(url) + ".bin")
        val cached: ByteArray? = if (f.exists() && f.length() > 0L) f.readBytes() else null
        val bytes: ByteArray = cached ?: run {
            val cn = open(url, "image/*,*/*;q=0.5")
            val got = try {
                cn.c.inputStream.use { readLimited(it, MAX_IMG) }
            } finally {
                cn.c.disconnect()
            }
            if (got.isNotEmpty()) {
                val tmp = File(dir, keyOf(url) + ".tmp")
                tmp.writeBytes(got)
                tmp.renameTo(f)
            }
            got
        }
        return decode(bytes)
    }

    /** Berkas cache gambar (cacheDir/lp) yang sudah ada untuk alamat gambar ini, termasuk cadangan resolusi YouTube. Dipakai cadangan / bagikan supaya kartu tautan tetap bergambar saat offline. */
    fun cachedFiles(ctx: Context, url: String): List<File> {
        val dir = File(ctx.cacheDir, "lp")
        return candidates(url).map { File(dir, keyOf(it) + ".bin") }.filter { it.exists() && it.length() > 0L }
    }

    /** Gambar yang sudah ada di memori (tanpa menunggu apa pun); null kalau belum. */
    fun peek(url: String): ImageBitmap? = bitmaps.get(url)

    /** Gambar pratinjau: dari cache memori, lalu cache berkas, lalu diunduh. null kalau gagal / offline. */
    suspend fun image(ctx: Context, url: String): ImageBitmap? {
        bitmaps.get(url)?.let { return it }
        if (recentlyFailed("img:$url")) return null
        val app = ctx.applicationContext
        val d = imgFlight.getOrPut(url) {
            scope.async {
                var r: ImageBitmap? = null
                for (u in candidates(url)) {
                    r = try { fetchDecode(app, u) } catch (e: Exception) { null }
                    if (r != null) break
                }
                if (r != null) bitmaps.put(url, r) else failed["img:$url"] = System.currentTimeMillis()
                imgFlight.remove(url)
                r
            }
        }
        return d.await()
    }

    private val RX_HOST = Regex("^https?://([^/?#]+)", RegexOption.IGNORE_CASE)

    /** Nama domain tanpa "www." dan tanpa port / info pengguna. */
    fun domain(url: String): String {
        val h = RX_HOST.find(url)?.groupValues?.get(1) ?: return url
        return h.substringAfterLast('@').substringBefore(':').removePrefix("www.").ifEmpty { url }
    }
}

// =====================================================================================================================
// Kartu tautan = objek sisipan (seperti foto / kutipan / tabel): tekan-tahan untuk memindahkan, seret gagang tepi untuk
// mengubah lebar, teks mengalir di sampingnya, dan dihapus lewat toolbar (bukan Backspace). Letak, gestur, dan toolbar
// ditangani BoxCell / BoxFloatGroup / ObjBarContent (jalur yang sama dengan kutipan, kode, dan garis); file ini hanya isinya.
// =====================================================================================================================

/** Bentuk kartu tautan (M3 Expressive: sudut besar 28dp). Dipakai juga oleh cincin pilihan di BoxBody. */
val LinkShape: Shape = RoundedCornerShape(28.dp)

/** Buka [url] di aplikasi lain; gagal (tidak ada aplikasi) diabaikan. */
fun openLink(h: UriHandler, url: String) {
    try { h.openUri(url) } catch (e: Exception) { }
}

/** Perkiraan tinggi (dp) kartu selebar [w] sebelum tampilannya terukur (setelah terukur, tinggi sebenarnya dipakai: Ed.objH). */
fun linkHeightEst(b: Blk, w: Float, fs: Float): Float {
    val img = if (b.pi.isNotEmpty()) (w - 16f) / 1.91f + 10f else 6f
    val tw = (w - 16f - 12f - 40f - 12f - 44f).coerceAtLeast(40f)
    val per = (tw / (fs * .5f)).coerceAtLeast(4f)
    val lines = if (b.r.t.isEmpty()) 1 else kotlin.math.ceil(b.r.t.length / per).toInt().coerceIn(1, 2)
    val txt = lines * fs * .98f * 1.35f + fs * .8f * 1.35f + 2f
    return 16f + img + maxOf(40f, txt) + 6f
}

/** Ikon tautan (dua mata rantai). [slash] = dicoret (ubah jadi tautan biasa). */
@Composable
fun LinkIc(c: Color, sz: Dp = 24.dp, slash: Boolean = false) {
    Canvas(Modifier.size(sz)) {
        val u = this.size.width / 24f
        val sw = 2.2f * u
        rotate(-45f, Offset(this.size.width / 2f, this.size.height / 2f)) {
            val st = Stroke(sw, cap = StrokeCap.Round)
            drawRoundRect(c, Offset(2f * u, 8.5f * u), Size(11f * u, 7f * u), CornerRadius(3.5f * u), style = st)
            drawRoundRect(c, Offset(11f * u, 8.5f * u), Size(11f * u, 7f * u), CornerRadius(3.5f * u), style = st)
        }
        if (slash) drawLine(c, Offset(5f * u, 5f * u), Offset(19f * u, 19f * u), sw, StrokeCap.Round)
    }
}

/** Ikon panah keluar (buka di luar aplikasi). */
@Composable
fun LinkOutIc(c: Color, sz: Dp = 24.dp) {
    Canvas(Modifier.size(sz)) {
        val u = this.size.width / 24f
        val sw = 2.2f * u
        drawLine(c, Offset(7f * u, 17f * u), Offset(17f * u, 7f * u), sw, StrokeCap.Round)
        drawLine(c, Offset(9f * u, 7f * u), Offset(17f * u, 7f * u), sw, StrokeCap.Round)
        drawLine(c, Offset(17f * u, 7f * u), Offset(17f * u, 15f * u), sw, StrokeCap.Round)
    }
}

/**
 * Isi kartu tautan M3 Expressive: wadah tonal bersudut besar tanpa garis tepi, gambar bersudut dalam (28 - 8 = 20dp),
 * lencana ikon squircle, judul tebal, domain berwarna utama, dan penanda panah keluar bulat. Tanpa data (gagal / offline)
 * kartu tetap tampil ringkas: domain + alamat. Lebarnya ditentukan pemanggil lewat [modifier]; ketukan, seret, dan gagang
 * lebar ditangani BoxCell, jadi tidak ada gestur di sini.
 */
@Composable
fun LinkBody(ed: Ed, b: Blk, dm: Dm, pal: Pal, modifier: Modifier) {
    val ctx = LocalContext.current
    val fs = dm.fs
    val url = b.l
    val title = b.r.t

    // data belum ada: ambil di latar; hasilnya disimpan di blok (ikut tercadangkan), gagal = tetap kartu ringkas
    LaunchedEffect(b.id, url) {
        if (b.r.t.isEmpty() && b.pi.isEmpty()) {
            val m = LinkPreviews.meta(url, ctx)
            if (m != null) ed.setLinkMeta(b.id, m.title, m.image)
        }
    }
    // (selesai?, gambar): sebelum selesai, bingkai gambar sudah ditahan supaya kartu tidak melompat saat gambarnya tiba
    val imgSt by produceState(
        LinkPreviews.peek(b.pi).let { (it != null || b.pi.isEmpty()) to it }, b.pi
    ) {
        value = true to (if (b.pi.isEmpty()) null else LinkPreviews.image(ctx, b.pi))
    }
    val bmp = imgSt.second
    val showImg = b.pi.isNotEmpty() && (bmp != null || !imgSt.first)
    val domain = LinkPreviews.domain(url)

    BoxWithConstraints(modifier.clip(LinkShape).background(pal.c3)) {
        val showBadge = maxWidth >= 170.dp
        val showArrow = maxWidth >= 230.dp
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            if (showImg) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.91f).clip(RoundedCornerShape(20.dp)).background(pal.c4)) {
                    if (bmp != null) Image(bmp, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = if (showImg) 10.dp else 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBadge) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(pal.pc), Alignment.Center) {
                        LinkIc(pal.opc, 22.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    T(if (title.isNotEmpty()) title else domain, fs * .98f, 750, pal.os, maxLines = 2)
                    T(
                        if (title.isNotEmpty()) domain else url, fs * .8f, 600,
                        if (title.isNotEmpty()) pal.p else pal.osv, Modifier.padding(top = 2.dp), maxLines = 1
                    )
                }
                if (showArrow) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(pal.sc), Alignment.Center) {
                        LinkOutIc(pal.osc, 18.dp)
                    }
                }
            }
        }
    }
}

/** Dialog "Add link" dari ubin Link di sheet Sisipkan. Terisi otomatis kalau clipboard berisi alamat web. Menempel langsung di catatan tetap jalan seperti biasa. */
@Composable
fun LinkDialog(ed: Ed) {
    if (!ed.linkDlg) return
    val pal = LocalPal.current
    @Suppress("DEPRECATION")
    val clip = LocalClipboardManager.current
    var txt by remember { mutableStateOf(ed.normLink(clip.getText()?.text) ?: "") }
    val url = ed.normLink(txt)
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); try { fr.requestFocus() } catch (e: Exception) { } }
    val go = {
        val u = url
        if (u != null) { ed.linkDlg = false; ed.insLink(u) }
    }
    AlertDialog(
        onDismissRequest = { ed.linkDlg = false; ed.linkAt = null },
        containerColor = pal.c3,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(pal.pc), Alignment.Center) { LinkIc(pal.opc, 28.dp) }
        },
        title = { T("Add link", 24f, 400, pal.os) },
        text = {
            Column {
                T("Paste a web address. A preview card is added to your note.", 14f, 400, pal.osv)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.c4).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (txt.isEmpty()) T("https://", 16f, 400, pal.osv, alpha = .6f)
                    BasicTextField(
                        value = txt, onValueChange = { txt = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(fr),
                        singleLine = true,
                        textStyle = TextStyle(color = pal.os, fontSize = 16.sp),
                        cursorBrush = SolidColor(pal.p),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { go() })
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { go() }, enabled = url != null) { T("Insert", 15f, 600, if (url != null) pal.p else pal.osv, alpha = if (url != null) 1f else .5f) }
        },
        dismissButton = {
            TextButton(onClick = { ed.linkDlg = false; ed.linkAt = null }) { T("Cancel", 15f, 500, pal.osv) }
        }
    )
}

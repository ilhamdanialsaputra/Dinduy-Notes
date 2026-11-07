package app.dinduy

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
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

/** Kartu pratinjau tautan gaya Google Keep: gambar, judul, domain. Tanpa data (gagal / offline) = tautan biasa. */
@Composable
fun LinkCard(ed: Ed, b: Blk, dm: Dm) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val fs = dm.fs
    val url = b.l
    val title = b.r.t
    val open = { try { uri.openUri(url) } catch (e: Exception) { } }

    // data belum ada: ambil di latar; hasilnya disimpan di blok (ikut tercadangkan), gagal = tetap tautan biasa
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

    Box(Modifier.fillMaxWidth().padding(bottom = (fs * 1.1f).dp)) {
        if (title.isEmpty() && b.pi.isEmpty()) {
            Text(
                url, Modifier.fillMaxWidth().clickable { open() }, color = pal.p, fontSize = fs.sp,
                fontFamily = dm.ff, textDecoration = TextDecoration.Underline
            )
        } else {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(pal.c2).border(1.dp, pal.ol, shape).clickable { open() }
            ) {
                val img = bmp
                if (img != null) {
                    Image(img, null, Modifier.fillMaxWidth().aspectRatio(1.91f), contentScale = ContentScale.Crop)
                } else if (!imgSt.first && b.pi.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.91f).background(pal.c4))
                }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (title.isNotEmpty()) {
                        T(title, fs, 700, pal.os, maxLines = 2)
                    }
                    T(LinkPreviews.domain(url), fs * .82f, 500, pal.osv, Modifier.padding(top = 2.dp), maxLines = 1)
                }
            }
        }
        if (!ed.reading) {
            // ubah jadi tautan biasa (teks)
            IconBtn(
                "x", pal.os, { ed.linkToText(b.id) }, Modifier.align(Alignment.TopEnd).padding(6.dp),
                size = 32.dp, bg = pal.s.copy(alpha = .85f), iconSize = 18.dp
            )
        }
    }
}

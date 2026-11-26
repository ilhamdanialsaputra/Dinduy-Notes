package app.dinduy

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * OCR sepenuhnya di perangkat memakai Tesseract (Tesseract4Android, Apache-2.0), pengganti Google ML Kit yang closed-source.
 * Data bahasa (tessdata_fast) diunduh SEKALI atas persetujuan pengguna ke filesDir/ocr/tessdata.
 */
object Ocr {
    private val LANGS = listOf("eng", "ind")
    private const val BASE = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"
    private const val MIN = 100_000L

    private fun root(ctx: Context) = File(ctx.filesDir, "ocr")
    private fun tess(ctx: Context) = File(root(ctx), "tessdata")

    fun ready(ctx: Context): Boolean =
        LANGS.all { l -> File(tess(ctx), "$l.traineddata").let { it.exists() && it.length() > MIN } }

    suspend fun download(ctx: Context, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val dir = tess(ctx).apply { mkdirs() }
        for ((i, l) in LANGS.withIndex()) {
            val out = File(dir, "$l.traineddata")
            if (out.exists() && out.length() > MIN) { onProgress((i + 1f) / LANGS.size); continue }
            val part = File(dir, "$l.part")
            var c: HttpURLConnection? = null
            try {
                c = (URL(BASE + "$l.traineddata").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Dinduy-Notes")
                }
                if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
                val total = c.contentLengthLong
                c.inputStream.use { ins ->
                    part.outputStream().use { os ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        while (true) {
                            ensureActive()
                            val n = ins.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            done += n
                            val f = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                            onProgress((i + f) / LANGS.size)
                        }
                    }
                }
                if (part.length() < MIN || !part.renameTo(out)) throw IOException("Invalid file")
            } finally {
                c?.disconnect()
                part.delete()
            }
        }
        onProgress(1f)
    }

    /** Null = gagal. Daftar kata dengan kotak (piksel gambar), nomor baris dan blok. */
    @Synchronized
    fun recognize(ctx: Context, bmp: Bitmap): List<OWord>? {
        val api = TessBaseAPI()
        return try {
            if (!api.init(root(ctx).absolutePath, LANGS.joinToString("+"))) null
            else {
                api.setImage(bmp)
                val hocr = api.getHOCRText(0)
                if (hocr.isNullOrEmpty()) null else parse(hocr)
            }
        } catch (e: Throwable) {
            null
        } finally {
            try { api.recycle() } catch (e: Throwable) { }
        }
    }

    private val BBOX = Regex("bbox (\\d+) (\\d+) (\\d+) (\\d+)")

    private fun parse(hocr: String): List<OWord> {
        val out = ArrayList<OWord>()
        var block = -1
        var line = -1
        for (el in Jsoup.parse(hocr).select(".ocr_carea, .ocr_line, .ocr_header, .ocr_caption, .ocr_textfloat, .ocrx_word")) {
            val cls = el.classNames()
            when {
                "ocr_carea" in cls -> block++
                "ocrx_word" in cls -> {
                    val m = BBOX.find(el.attr("title")) ?: continue
                    val t = el.text().trim()
                    if (t.isEmpty()) continue
                    val (l, tp, r, b) = m.destructured
                    out.add(OWord(t, l.toFloat(), tp.toFloat(), r.toFloat(), b.toFloat(), maxOf(line, 0), maxOf(block, 0)))
                }
                else -> line++
            }
        }
        return out
    }
}

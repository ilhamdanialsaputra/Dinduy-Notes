package app.dinduy

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** Satu potongan hasil transkripsi (detik). */
data class TSeg(val st: Double, val en: Double, val tx: String)

/** Hasil rapikan Markdown satu bagian: [md] null berarti dipakai teks polos ([paras]). */
class Chunk(val paras: List<String>, val md: String?)

/** Pesan galat yang aman ditampilkan (tidak pernah memuat API key). */
class GroqException(msg: String, val code: Int = 0, val wait: Int = 0) : IOException(msg)

/**
 * Klien Groq (Speech-to-Text). Aturan keamanan:
 *  - hanya berbicara ke https://api.groq.com (alamat tetap di kode, bukan dari masukan),
 *  - API key hanya dikirim di header Authorization, tidak pernah di URL / log / pesan galat,
 *  - pengalihan (redirect) tidak diikuti, jadi key tidak bisa terbawa ke server lain,
 *  - tidak ada logging.
 * Tujuan: TRANSKRIPSI MURNI. Whisper hanya menulis ulang ucapan; tidak merangkum atau membuat poin.
 */
object Groq {
    private const val BASE = "https://api.groq.com/openai/v1"
    const val STT_MODEL = "whisper-large-v3"
    private const val NET = "Tidak bisa menghubungi Groq. Periksa koneksi internet lalu coba lagi."

    @Volatile private var live: HttpURLConnection? = null

    /** Putuskan permintaan yang sedang berjalan (tombol Batal). */
    fun abort() {
        try { live?.disconnect() } catch (e: Exception) { }
    }

    private class Resp(val code: Int, val body: String)

    private fun open(path: String, key: String, method: String): HttpURLConnection {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.requestMethod = method
        c.connectTimeout = 20000
        c.readTimeout = 240000
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "Dinduy")
        return c
    }

    private fun finish(c: HttpURLConnection): Resp {
        val code = c.responseCode
        val st = if (code in 200..299) c.inputStream else c.errorStream
        val txt = st?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        return Resp(code, txt)
    }

    private fun explain(r: Resp): String {
        val detail = try {
            J.parseToJsonElement(r.body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        } catch (e: Exception) { null }
        val base = when (r.code) {
            401 -> "API key ditolak Groq. Periksa lagi key-nya atau buat yang baru."
            403 -> "Akses ditolak oleh Groq (key tidak punya izin, atau wilayahmu diblokir)."
            413 -> "Berkas terlalu besar untuk Groq."
            429 -> "Batas pemakaian Groq tercapai. Tunggu sebentar lalu coba lagi."
            in 500..599 -> "Server Groq sedang bermasalah. Coba lagi nanti."
            else -> "Groq membalas dengan kode ${r.code}."
        }
        return if (detail != null && r.code != 401 && detail.isNotBlank()) base + " (" + detail.take(120) + ")" else base
    }

    /** Daftar model yang bisa dipakai key ini. Melempar GroqException bila gagal. */
    private fun listModels(key: String): List<String> {
        val c = open("/models", key, "GET")
        live = c
        try {
            val r = finish(c)
            if (r.code !in 200..299) throw GroqException(explain(r), r.code)
            val arr = J.parseToJsonElement(r.body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
            return arr.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
        } catch (e: GroqException) {
            throw e
        } catch (e: IOException) {
            throw GroqException(NET)
        } catch (e: Exception) {
            throw GroqException("Balasan Groq tidak dikenali.")
        } finally {
            live = null
            c.disconnect()
        }
    }

    /** Tes key: null = berhasil, selain itu pesan kegagalan untuk pengguna. */
    fun test(key: String): String? {
        return try {
            val ids = listModels(key)
            if (STT_MODEL !in ids) "Key valid, tetapi model $STT_MODEL tidak tersedia di akun ini." else null
        } catch (e: GroqException) {
            e.message ?: NET
        }
    }

    // ------------------------------------------------------------------------------------------
    // Transkripsi
    // ------------------------------------------------------------------------------------------

    private fun promptFor(lang: String): String = when (lang) {
        "id" -> "Halo, selamat pagi. Hari ini kita akan membahas beberapa hal, yaitu tugas, jadwal, dan rencana minggu depan. Baik, mari kita mulai."
        "en" -> "Hello, good morning. Today we will talk about a few things: the assignment, the schedule, and next week's plan. All right, let's begin."
        else -> "Halo, selamat pagi. Hello, good morning. Hari ini kita bahas beberapa hal: tugas, jadwal, dan rencana. Okay, let's begin."
    }

    fun transcribe(key: String, f: File, mime: String, lang: String): List<TSeg> {
        val b = "----dinduy" + java.lang.Long.toHexString(System.nanoTime())
        val nl = "\r\n"
        fun field(n: String, v: String) = "--$b$nl" + "Content-Disposition: form-data; name=\"$n\"$nl$nl$v$nl"
        val head = StringBuilder()
        head.append(field("model", STT_MODEL))
        head.append(field("response_format", "verbose_json"))
        head.append(field("temperature", "0"))
        if (lang.isNotEmpty()) head.append(field("language", lang))
        head.append(field("prompt", promptFor(lang)))   // contoh gaya tulis berpunctuation: membuat koma & titik lebih tepat
        head.append("--$b$nl")
        head.append("Content-Disposition: form-data; name=\"file\"; filename=\"${f.name}\"$nl")
        head.append("Content-Type: $mime$nl$nl")
        val hb = head.toString().toByteArray(Charsets.UTF_8)
        val tb = ("$nl--$b--$nl").toByteArray(Charsets.UTF_8)
        val c = open("/audio/transcriptions", key, "POST")
        live = c
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
            c.setFixedLengthStreamingMode(hb.size.toLong() + f.length() + tb.size.toLong())
            c.outputStream.use { o ->
                o.write(hb)
                f.inputStream().use { it.copyTo(o) }
                o.write(tb)
            }
            val r = finish(c)
            if (r.code !in 200..299) throw GroqException(explain(r), r.code)
            return parse(r.body)
        } catch (e: GroqException) {
            throw e
        } catch (e: IOException) {
            throw GroqException(NET)
        } finally {
            live = null
            c.disconnect()
        }
    }

    private fun parse(body: String): List<TSeg> {
        val o = try { J.parseToJsonElement(body).jsonObject } catch (e: Exception) { throw GroqException("Balasan Groq tidak dikenali.") }
        val segs = o["segments"] as? JsonArray
        val out = ArrayList<TSeg>()
        if (segs != null) {
            for (e in segs) {
                val s = e.jsonObject
                val t = s["text"]?.jsonPrimitive?.content?.trim() ?: ""
                if (t.isEmpty()) continue
                val ns = s["no_speech_prob"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val lp = s["avg_logprob"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                if (ns > 0.6 && lp < -1.0) continue   // bagian hening yang "dikarang" Whisper
                val st = s["start"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val en = s["end"]?.jsonPrimitive?.doubleOrNull ?: st
                out.add(TSeg(st, en, t))
            }
        } else {
            val t = o["text"]?.jsonPrimitive?.content?.trim() ?: ""
            if (t.isNotEmpty()) out.add(TSeg(0.0, 0.0, t))
        }
        return out
    }

    // ------------------------------------------------------------------------------------------
    // Menyiapkan audio panjang: m4a <= 24 MB dikirim apa adanya; yang lebih panjang dipotong
    // per ~8 menit (di titik paling hening) jadi WAV 16 kHz mono, lalu dikirim satu per satu.
    // ------------------------------------------------------------------------------------------

    fun prepare(ctx: Context, src: File, id: String, temps: MutableList<File>, check: () -> Unit): List<Pair<File, Double>> {
        if (src.length() <= 24_000_000L) return listOf(src to 0.0)
        val wav = File(ctx.cacheDir, "gq_$id.wav")
        temps.add(wav)
        WavDecoder.decode(src, wav, check)
        return splitWav(ctx, wav, id, temps, check)
    }

    private fun splitWav(ctx: Context, wav: File, id: String, temps: MutableList<File>, check: () -> Unit): List<Pair<File, Double>> {
        val sr = 16000L
        val total = (wav.length() - 44L) / 2L
        val per = sr * 480L
        val cuts = ArrayList<Long>()
        cuts.add(0L)
        RandomAccessFile(wav, "r").use { raf ->
            var pos = per
            while (pos < total - sr * 20L) {
                check()
                val from = (pos - sr * 20L).coerceAtLeast(cuts.last() + sr)
                val n = (pos - from).toInt()
                val buf = ByteArray(n * 2)
                raf.seek(44L + from * 2L)
                raf.readFully(buf)
                val frame = 3200
                var best = pos
                var bestE = Double.MAX_VALUE
                var f0 = 0
                while (f0 + frame <= n) {
                    var e = 0.0
                    for (k in 0 until frame) {
                        val lo = buf[(f0 + k) * 2].toInt() and 0xFF
                        val hi = buf[(f0 + k) * 2 + 1].toInt()
                        val s = (hi shl 8) or lo
                        e += s.toDouble() * s
                    }
                    if (e < bestE) { bestE = e; best = from + f0 + frame / 2 }
                    f0 += frame
                }
                cuts.add(best)
                pos = best + per
            }
            cuts.add(total)
        }
        val out = ArrayList<Pair<File, Double>>()
        RandomAccessFile(wav, "r").use { raf ->
            val buf = ByteArray(65536)
            for (i in 0 until cuts.size - 1) {
                check()
                val a = cuts[i]
                val z = cuts[i + 1]
                val f = File(ctx.cacheDir, "gq_${id}_$i.wav")
                temps.add(f)
                f.outputStream().use { o ->
                    o.write(WavDecoder.header((z - a) * 2L))
                    raf.seek(44L + a * 2L)
                    var left = (z - a) * 2L
                    while (left > 0L) {
                        val r = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (r < 0) break
                        o.write(buf, 0, r)
                        left -= r
                    }
                }
                out.add(f to a / sr.toDouble())
            }
        }
        return out
    }

    // ------------------------------------------------------------------------------------------
    // Rapikan jadi Markdown (opsional). Hanya menambah **tebal**, *miring*, paragraf, dan ---.
    // Hasilnya DICEK: urutan kata harus identik dengan aslinya; kalau tidak, dipakai teks polos.
    // ------------------------------------------------------------------------------------------

    private const val SYS =
        "Kamu editor transkrip suara. Tugasmu: merapikan teks hasil transkripsi menjadi Markdown yang enak dibaca TANPA mengubah isinya.\n" +
            "BOLEH:\n" +
            "- Memperbaiki tanda baca: titik, koma, tanda tanya, tanda seru, titik dua; dan huruf kapital di awal kalimat serta nama orang, tempat, dan merek.\n" +
            "- Memecah atau menggabungkan paragraf di pergantian ide (pisahkan dengan satu baris kosong).\n" +
            "- **Tebal** untuk 1 sampai 3 frasa terpenting di tiap paragraf: nama, istilah penting, angka, tanggal, keputusan, tenggat.\n" +
            "- *Miring* untuk kata asing atau bahasa daerah, judul karya, dan penekanan.\n" +
            "- Satu baris berisi --- (dengan baris kosong sebelum dan sesudahnya) hanya saat pindah topik besar.\n" +
            "DILARANG KERAS:\n" +
            "- Menambah, menghapus, mengganti, atau menukar urutan kata apa pun. Jangan benahi ejaan, kata tidak baku (gak, nih, banget), pengulangan, atau kata ragu (eh, hmm).\n" +
            "- Mengubah angka atau menerjemahkan.\n" +
            "- Merangkum, menyimpulkan, membuat judul, daftar, poin, kutipan, tabel, atau komentar.\n" +
            "Keluarkan HANYA teks hasilnya."

    private const val EX_IN =
        "halo semuanya hari ini kita bahas rapat tanggal 12 november jam 9 pagi di ruang meeting lantai 3 pak budi bilang tugas harus kelar sebelum jumat nah menurut saya itu cukup mepet sih tapi ya kita coba aja"
    private const val EX_OUT =
        "Halo semuanya. Hari ini kita bahas **rapat tanggal 12 November jam 9 pagi** di ruang meeting lantai 3.\n\n" +
            "**Pak Budi** bilang tugas harus kelar **sebelum Jumat**. Nah, menurut saya itu cukup *mepet* sih, tapi ya kita coba aja."

    @Volatile private var chatList: List<String>? = null

    private fun chatModels(key: String): List<String> {
        chatList?.let { return it }
        val ids = listModels(key)
        val l = ArrayList<String>()
        val pref = listOf(
            "llama-3.3-70b-versatile", "openai/gpt-oss-120b", "meta-llama/llama-4-scout-17b-16e-instruct",
            "openai/gpt-oss-20b", "llama-3.1-8b-instant"
        )
        for (m in pref) if (m in ids) l.add(m)
        if (l.isEmpty()) {
            val skip = listOf("whisper", "guard", "tts", "orpheus", "playai", "compound", "safeguard", "embed", "qwen", "prompt")
            ids.filter { id -> skip.none { id.contains(it) } }.take(2).forEach { l.add(it) }
        }
        chatList = l
        return l
    }

    private fun fmtChunk(key: String, model: String, text: String, temp: Double): String? {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", temp)
            put("messages", JsonArray(listOf(
                buildJsonObject { put("role", "system"); put("content", SYS) },
                buildJsonObject { put("role", "user"); put("content", EX_IN) },
                buildJsonObject { put("role", "assistant"); put("content", EX_OUT) },
                buildJsonObject { put("role", "user"); put("content", text) }
            )))
        }.toString().toByteArray(Charsets.UTF_8)
        val c = open("/chat/completions", key, "POST")
        live = c
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { o: OutputStream -> o.write(body) }
            val r = finish(c)
            if (r.code !in 200..299) throw GroqException(explain(r), r.code)
            val o = J.parseToJsonElement(r.body).jsonObject
            return o["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        } catch (e: GroqException) {
            throw e
        } catch (e: IOException) {
            throw GroqException(NET)
        } catch (e: Exception) {
            return null
        } finally {
            live = null
            c.disconnect()
        }
    }

    /** Model obrolan untuk menata struktur PDF (tanpa model penalaran gpt-oss supaya jatah token tidak habis untuk berpikir). */
    fun layoutModels(key: String): List<String> {
        val all = chatModels(key)
        return all.filter { !it.contains("gpt-oss") }.ifEmpty { all }
    }

    private fun waitFromMsg(body: String): Int {
        val m = Regex("try again in ((\\d+)h)?((\\d+)m(?!s))?(([\\d.]+)s)?").find(body) ?: return 0
        val h = m.groupValues[2].toIntOrNull() ?: 0
        val mi = m.groupValues[4].toIntOrNull() ?: 0
        val s = m.groupValues[6].toDoubleOrNull() ?: 0.0
        return h * 3600 + mi * 60 + kotlin.math.ceil(s).toInt()
    }

    /** Satu panggilan obrolan (suhu 0). Pada 429, GroqException.wait = detik tunggu dari Groq (0 bila tidak diketahui). */
    fun chatOnce(key: String, model: String, system: String, user: String, maxTokens: Int, temperature: Double = 0.0): String? {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("messages", JsonArray(listOf(
                buildJsonObject { put("role", "system"); put("content", system) },
                buildJsonObject { put("role", "user"); put("content", user) }
            )))
        }.toString().toByteArray(Charsets.UTF_8)
        val c = open("/chat/completions", key, "POST")
        live = c
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { o: OutputStream -> o.write(body) }
            val ra = c.getHeaderField("retry-after")
            val r = finish(c)
            if (r.code !in 200..299) {
                var w = ra?.trim()?.toDoubleOrNull()?.let { kotlin.math.ceil(it).toInt() } ?: 0
                if (r.code == 429 && w == 0) w = waitFromMsg(r.body)
                throw GroqException(explain(r), r.code, w)
            }
            val o = J.parseToJsonElement(r.body).jsonObject
            return o["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        } catch (e: GroqException) {
            throw e
        } catch (e: IOException) {
            throw GroqException(NET)
        } catch (e: Exception) {
            return null
        } finally {
            live = null
            c.disconnect()
        }
    }

    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val BAD = Regex("^(#{1,6}\\s|[-+*]\\s|\\d+[.)]\\s|>|\\||```)")

    private fun words(s: String): List<String> = WORD.findAll(s.lowercase()).map { it.value }.toList()

    /** Benar bila [out] hanya menambah format: kata-katanya sama persis dan tidak ada judul / poin / kutipan. */
    fun safe(orig: String, out: String): Boolean {
        if (words(orig) != words(out)) return false
        for (line in out.split("\n")) {
            val t = line.trim()
            if (t == "---") continue
            if (BAD.containsMatchIn(t)) return false
        }
        return true
    }

    private fun unfence(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.substringAfter("\n", "")
            t = t.trimEnd().removeSuffix("```")
        }
        return t.trim()
    }

    /** Garis --- harus berdiri sendiri (kalau menempel di teks, Markdown menganggapnya judul). */
    private fun spaceRules(s: String): String {
        val o = StringBuilder()
        for (ln in s.split("\n")) {
            if (ln.trim() == "---") o.append("\n\n---\n\n") else o.append(ln).append("\n")
        }
        return o.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun sleepCk(ms: Long, check: () -> Unit) {
        var t = 0L
        while (t < ms) { check(); Thread.sleep(250); t += 250 }
    }

    /** Hasil Markdown yang lolos cek, atau null bila jawaban model mengubah kata. Melempar GroqException bila semua model gagal. */
    private fun tryFormat(key: String, models: List<String>, text: String, check: () -> Unit): String? {
        var last: GroqException? = null
        for (m in models) {
            var wait = 0
            var att = 0
            while (true) {
                check()
                try {
                    val r = fmtChunk(key, m, text, if (att == 0) 0.0 else 0.3)?.let { spaceRules(unfence(it)) }
                    if (r != null && safe(text, r)) return r
                    att++
                    if (att >= 2) return null
                } catch (e: GroqException) {
                    last = e
                    if (e.code == 429 && wait == 0) { wait++; sleepCk(3500L, check); continue }
                    break
                }
            }
        }
        if (last != null) throw last
        return null
    }

    class FmtRes(val chunks: List<Chunk>, val ok: Int, val err: String?)

    /** Rapikan per bagian (~1800 huruf). Bagian yang gagal dicoba per paragraf; tetap gagal -> dipakai polos. Null bila fitur tak tersedia. */
    fun format(key: String, paras: List<String>, check: () -> Unit): FmtRes? {
        val models = try { chatModels(key) } catch (e: GroqException) { return FmtRes(emptyList(), 0, e.message ?: "Groq tidak bisa dihubungi") }
        if (models.isEmpty()) return FmtRes(emptyList(), 0, "model perapi Groq tidak tersedia di akun ini")
        val groups = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        var n = 0
        for (p in paras) {
            if (n + p.length > 1800 && cur.isNotEmpty()) { groups.add(cur); cur = ArrayList(); n = 0 }
            cur.add(p); n += p.length
        }
        if (cur.isNotEmpty()) groups.add(cur)
        val out = ArrayList<Chunk>()
        var err: String? = null
        var ok = 0
        for (g in groups) {
            check()
            var md: String? = null
            if (err == null) {
                try {
                    md = tryFormat(key, models, g.joinToString("\n\n"), check)
                    if (md == null && g.size > 1) {
                        val parts = ArrayList<String>()
                        var any = false
                        for (p in g) {
                            val r = tryFormat(key, models, p, check)
                            if (r != null) any = true
                            parts.add(r ?: p)
                        }
                        if (any) md = parts.joinToString("\n\n")
                    }
                } catch (e: GroqException) {
                    err = e.message ?: "gagal"   // mis. batas pemakaian: sisanya langsung polos
                }
            }
            if (md != null) ok++
            out.add(Chunk(g, md))
        }
        if (ok == 0 && err == null) err = "Groq mengubah isi katanya, jadi dipakai versi polos"
        return FmtRes(out, ok, err)
    }
}

/** Paragraf dan blok catatan dari hasil transkripsi. */
object Paras {
    fun build(segs: List<TSeg>): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var prevEnd = -1.0
        for (s in segs) {
            val t = s.tx.trim()
            if (t.isEmpty()) continue
            val gap = if (prevEnd < 0.0) 0.0 else s.st - prevEnd
            val ends = cur.isNotEmpty() && (cur.last() == '.' || cur.last() == '?' || cur.last() == '!' || cur.last() == '…')
            if (cur.isNotEmpty() && ((gap >= 1.6 && ends) || (cur.length > 450 && ends) || gap >= 4.0)) {
                out.add(cur.toString()); cur.setLength(0)
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(t)
            prevEnd = s.en
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    /** Judul "Transkrip N" + paragraf (Markdown bila lolos pengecekan, selain itu teks polos apa adanya). */
    fun blocks(label: String, paras: List<String>, chunks: List<Chunk>?): List<Blk> {
        val out = ArrayList<Blk>()
        out.add(Blk(ty = "h", lv = 2, r = Rt(label)))
        fun plain(l: List<String>) { for (p in l) out.add(Blk(ty = "p", r = Rt(p))) }
        if (chunks == null) {
            plain(paras)
        } else {
            for (c in chunks) {
                val m = c.md
                if (m == null) { plain(c.paras); continue }
                val b = md(m)
                if (b.all { it.ty == "p" || it.ty == "hr" }) out.addAll(b) else plain(c.paras)
            }
        }
        return out
    }
}

/** Ganti blok transkrip lama ([old]) dengan [nw] di tempat yang sama; kalau belum ada, sisipkan di akhir isi catatan. */
fun putBlocks(cur: List<Blk>, old: List<String>, nw: List<Blk>): List<Blk> {
    val l = cur.toMutableList()
    var at = l.indexOfFirst { it.id in old }
    l.removeAll { it.id in old }
    if (at < 0) {
        at = l.size
        while (at > 0 && l[at - 1].ty == "p" && l[at - 1].r.t.isEmpty()) at--
    }
    l.addAll(at.coerceIn(0, l.size), nw)
    if (l.last().isObj || l.last().isMulti) l.add(Blk())
    return l
}

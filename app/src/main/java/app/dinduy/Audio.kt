package app.dinduy

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder

object AudioFiles {
    fun dir(ctx: Context) = File(ctx.filesDir, "audio")
    fun file(ctx: Context, id: String) = File(dir(ctx), "$id.m4a")
    fun modelDir(ctx: Context) = File(ctx.filesDir, "models")
}

fun fmtTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val m = s / 60
    val r = s % 60
    return if (m >= 60) "${m / 60}:${(m % 60).toString().padStart(2, '0')}:${r.toString().padStart(2, '0')}"
    else "$m:${r.toString().padStart(2, '0')}"
}

class Rec(private val ctx: Context, private val scope: CoroutineScope) {
    var noteId by mutableStateOf<String?>(null)
        private set
    var ms by mutableStateOf(0L)
        private set
    var amps by mutableStateOf<List<Float>>(emptyList())
        private set

    private var mr: MediaRecorder? = null
    private var curId = ""
    private var t0 = 0L
    private var job: Job? = null

    val active: Boolean get() = noteId != null

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

    @Volatile var lastError: String? = null

    /** true selama MediaRecorder sedang disiapkan di thread IO (lihat [startAsync]). */
    @Volatile var starting = false
        private set

    /** Menyiapkan dan menjalankan MediaRecorder. Aman dipanggil dari thread mana pun; hasilnya null kalau gagal. */
    private fun prepareRecorder(nid: String): MediaRecorder? {
        val f = AudioFiles.file(ctx, nid)
        f.parentFile?.mkdirs()
        val r = newRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
            try { r.release() } catch (x: Exception) { }
            f.delete()
            return null
        }
        return r
    }

    /** Menyerahkan perekam yang sudah jalan ke state (harus di main thread). */
    private fun adopt(r: MediaRecorder, nid: String, note: String) {
        mr = r
        curId = nid
        t0 = SystemClock.elapsedRealtime()
        ms = 0L
        amps = emptyList()
        noteId = note
        job?.cancel()
        job = scope.launch {
            val buf = ArrayList<Float>()
            while (isActive) {
                delay(50)
                val a = try { mr?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
                val v = Math.sqrt(a / 32767.0).toFloat().coerceIn(0f, 1f)
                buf.add(v)
                if (buf.size > 64) buf.removeAt(0)
                amps = buf.toList()
                ms = SystemClock.elapsedRealtime() - t0
            }
        }
    }

    fun start(note: String): Boolean {
        lastError = null
        if (mr != null || starting) { lastError = "recorder busy"; return false }
        val nid = uid()
        val r = prepareRecorder(nid) ?: return false
        adopt(r, nid, note)
        return true
    }

    /**
     * Sama seperti [start], tetapi prepare()/start() MediaRecorder (bisa memblokir ratusan milidetik) berjalan di thread IO.
     * Dipakai saat bilah rekam sedang beranimasi: kalau dijalankan di main thread, animasi berhenti di tengah jalan.
     */
    suspend fun startAsync(note: String): Boolean {
        lastError = null
        if (mr != null || starting) { lastError = "recorder busy"; return false }
        starting = true
        val nid = uid()
        val r = try {
            // NonCancellable: perekam yang sudah menyala tidak boleh bocor kalau pemanggil keburu dibatalkan
            withContext(NonCancellable + Dispatchers.IO) { prepareRecorder(nid) }
        } finally { starting = false }
        if (r == null) return false
        adopt(r, nid, note)
        return true
    }

    fun finish(): Aud? {
        val r = mr ?: return null
        job?.cancel()
        val dur = SystemClock.elapsedRealtime() - t0
        var ok = true
        try { r.stop() } catch (e: Exception) { ok = false }
        try { r.release() } catch (e: Exception) { }
        mr = null
        val nid = curId
        noteId = null; ms = 0L; amps = emptyList()
        val f = AudioFiles.file(ctx, nid)
        if (!ok || !f.exists() || f.length() < 200L) {
            f.delete()
            return null
        }
        return Aud(id = nid, du = dur, at = System.currentTimeMillis())
    }

    fun rebind(note: String) {
        if (mr != null) noteId = note
    }

    fun cancel() {
        val r = mr ?: return
        job?.cancel()
        try { r.stop() } catch (e: Exception) { }
        try { r.release() } catch (e: Exception) { }
        mr = null
        AudioFiles.file(ctx, curId).delete()
        noteId = null; ms = 0L; amps = emptyList()
    }
}

class Player(private val ctx: Context, private val scope: CoroutineScope) {
    var id by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var pos by mutableStateOf(0L)
        private set
    var dur by mutableStateOf(0L)
        private set
    var onError: (String) -> Unit = { }

    private var mp: MediaPlayer? = null
    private var job: Job? = null

    private fun load(a: Aud): Boolean {
        if (id == a.id && mp != null) return true
        stop()
        val f = AudioFiles.file(ctx, a.id)
        if (!f.exists()) {
            onError("Audio file not found")
            return false
        }
        val p = MediaPlayer()
        try {
            p.setDataSource(f.absolutePath)
            p.prepare()
            p.setOnCompletionListener { m ->
                playing = false
                pos = 0L
                job?.cancel()
                try { m.seekTo(0) } catch (e: Exception) { }
            }
        } catch (e: Exception) {
            try { p.release() } catch (x: Exception) { }
            onError("Can't play this audio")
            return false
        }
        mp = p
        id = a.id
        dur = p.duration.toLong().coerceAtLeast(1L)
        pos = 0L
        return true
    }

    fun toggle(a: Aud) {
        if (id == a.id && mp != null && playing) {
            pause()
            return
        }
        if (!load(a)) return
        val p = mp ?: return
        try {
            p.start()
            playing = true
            tick()
        } catch (e: Exception) {
            stop()
            onError("Can't play this audio")
        }
    }

    fun pause() {
        try { mp?.pause() } catch (e: Exception) { }
        playing = false
        job?.cancel()
        try { pos = mp?.currentPosition?.toLong() ?: pos } catch (e: Exception) { }
    }

    fun seek(a: Aud, frac: Float) {
        if (!load(a)) return
        val p = mp ?: return
        val t = (frac.coerceIn(0f, 1f) * dur).toInt()
        try { p.seekTo(t) } catch (e: Exception) { }
        pos = t.toLong()
    }

    private fun tick() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                try { pos = mp?.currentPosition?.toLong() ?: 0L } catch (e: Exception) { }
                delay(40)
            }
        }
    }

    fun stop() {
        job?.cancel()
        val p = mp
        mp = null
        if (p != null) {
            try { p.stop() } catch (e: Exception) { }
            try { p.release() } catch (e: Exception) { }
        }
        id = null; playing = false; pos = 0L; dur = 0L
    }

    fun stopIf(aid: String) {
        if (id == aid) stop()
    }
}

object WavDecoder {
    private class Mono16k(private val out: OutputStream) {
        var rate = 44100
        var bytes = 0L
        private var idx = -1L
        private var t = 0.0
        private var step = 1.0
        private var prev = 0f
        private val buf = ByteArray(32768)
        private var n = 0

        private fun emit(v: Float) {
            val s = (v.coerceIn(-1f, 1f) * 32767f).toInt()
            buf[n++] = (s and 0xFF).toByte()
            buf[n++] = ((s shr 8) and 0xFF).toByte()
            if (n >= buf.size) flush()
            bytes += 2
        }

        fun feed(s: Float) {
            idx++
            if (idx == 0L) {
                step = rate / 16000.0
                prev = s
                emit(s)
                t = step
                return
            }
            while (t <= idx) {
                val frac = (t - (idx - 1)).toFloat()
                emit(prev + (s - prev) * frac)
                t += step
            }
            prev = s
        }

        fun flush() {
            if (n > 0) out.write(buf, 0, n)
            n = 0
        }
    }

    fun header(dataBytes: Long): ByteArray {
        val b = java.nio.ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + dataBytes).toInt())
        b.put("WAVE".toByteArray()); b.put("fmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(16000); b.putInt(32000); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataBytes.toInt())
        return b.array()
    }

    fun decode(src: File, dst: File, check: () -> Unit) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var total = 0L
        try {
            ex.setDataSource(src.absolutePath)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) { track = i; fmt = f; break }
            }
            val format: MediaFormat = fmt ?: throw IOException("No audio track")
            if (track < 0) throw IOException("No audio track")
            ex.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("Unknown format")
            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()
            var ch = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            var pcmFloat = false
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            FileOutputStream(dst).use { fo ->
                val os = BufferedOutputStream(fo, 65536)
                os.write(header(0L))
                val rs = Mono16k(os)
                rs.rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
                while (!outDone) {
                    check()
                    if (!inDone) {
                        val ii = dec.dequeueInputBuffer(10000)
                        if (ii >= 0) {
                            val ib = dec.getInputBuffer(ii)
                            val n = if (ib != null) ex.readSampleData(ib, 0) else -1
                            if (n < 0) {
                                dec.queueInputBuffer(ii, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inDone = true
                            } else {
                                dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val oi = dec.dequeueOutputBuffer(info, 10000)
                    if (oi >= 0) {
                        val ob = dec.getOutputBuffer(oi)
                        if (ob != null && info.size > 0) {
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            val bb = ob.order(ByteOrder.LITTLE_ENDIAN)
                            val c = if (ch < 1) 1 else ch
                            if (pcmFloat) {
                                val fb = bb.asFloatBuffer()
                                val frames = fb.remaining() / c
                                for (k in 0 until frames) {
                                    var s = 0f
                                    for (q in 0 until c) s += fb.get()
                                    rs.feed(s / c)
                                }
                            } else {
                                val sb = bb.asShortBuffer()
                                val frames = sb.remaining() / c
                                for (k in 0 until frames) {
                                    var s = 0f
                                    for (q in 0 until c) s += sb.get() / 32768f
                                    rs.feed(s / c)
                                }
                            }
                        }
                        dec.releaseOutputBuffer(oi, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                    } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val of = dec.outputFormat
                        if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rs.rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                }
                rs.flush()
                os.flush()
                total = rs.bytes
            }
            RandomAccessFile(dst, "rw").use { raf ->
                raf.seek(0)
                raf.write(header(total))
            }
            if (total <= 0L) throw IOException("Audio is empty")
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            try { codec?.release() } catch (e: Exception) { }
            try { ex.release() } catch (e: Exception) { }
        }
    }
}

class MInfo(val key: String, val file: String, val name: String, val size: String, val bytes: Long, val desc: String)

class MS(val info: MInfo) {
    var status by mutableStateOf(0)
    var got by mutableStateOf(0L)
    var total by mutableStateOf(0L)
    var err by mutableStateOf<String?>(null)
    var job: Job? = null
    var gen = 0
}

class Models(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onMsg: (String) -> Unit,
    private val onReady: (String) -> Unit,
    private val onBusy: () -> Unit = { }
) {
    val list: List<MS> = listOf(
        MInfo("base", "ggml-base.bin", "Base", "~142 MB", 147951465L, "Fast and light on memory. Good for most voice notes."),
        MInfo("small", "ggml-small.bin", "Small", "~466 MB", 487601967L, "More accurate, but larger and slower.")
    ).map { MS(it) }

    private val sp = ctx.getSharedPreferences("dinduy", 0)
    private val base = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    init {
        list.forEach { refresh(it) }
    }

    private fun finalFile(m: MS) = File(AudioFiles.modelDir(ctx), m.info.file)
    private fun partFile(m: MS) = File(AudioFiles.modelDir(ctx), m.info.file + ".part")

    private fun isReady(m: MS): Boolean {
        val f = finalFile(m)
        if (!f.exists()) return false
        val len = f.length()
        val v = sp.getLong("mdl_" + m.info.key, 0L)
        return len > 0L && (len == v || len == m.info.bytes)
    }

    fun refresh(m: MS) {
        if (m.status == 1) return
        if (isReady(m)) {
            m.status = 2
            m.got = finalFile(m).length()
            m.total = m.got
        } else {
            m.status = 0
            m.got = if (partFile(m).exists()) partFile(m).length() else 0L
            m.total = m.info.bytes
        }
    }

    fun refreshAll() { list.forEach { refresh(it) } }

    fun readyFile(prefKey: String): File? {
        val order = list.sortedBy { if (it.info.key == prefKey) 0 else 1 }
        val m = order.firstOrNull { it.status == 2 } ?: return null
        return finalFile(m)
    }

    fun anyReady(): Boolean = list.any { it.status == 2 }

    fun start(m: MS) {
        if (m.status != 0) return
        m.err = null
        m.status = 1
        onBusy()
        val g = ++m.gen
        val prev = m.job
        m.job = scope.launch {
            try {
                prev?.join()
                download(m)
                if (m.gen == g) {
                    m.status = 2
                    m.got = finalFile(m).length()
                    m.total = m.got
                    onReady(m.info.key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (m.gen == g) {
                    m.err = e.message ?: "Download failed"
                    onMsg("Download failed. Tap Resume to try again.")
                }
            } finally {
                if (m.gen == g && m.status == 1) {
                    m.status = 0
                    m.got = if (partFile(m).exists()) partFile(m).length() else 0L
                }
            }
        }
    }

    fun cancel(m: MS) {
        if (m.status != 1) return
        m.gen++
        m.job?.cancel()
        m.status = 0
        m.got = if (partFile(m).exists()) partFile(m).length() else 0L
    }

    fun delete(m: MS) {
        m.gen++
        m.job?.cancel()
        val j = m.job
        scope.launch {
            try { j?.join() } catch (e: Exception) { }
            finalFile(m).delete()
            partFile(m).delete()
            sp.edit().remove("mdl_" + m.info.key).apply()
            m.err = null
            m.status = 0
            m.got = 0L
            m.total = m.info.bytes
        }
    }

    private fun connect(start: String, have: Long): HttpURLConnection {
        var url = start
        var hops = 0
        while (true) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "Dinduy")
            c.setRequestProperty("Accept-Encoding", "identity")
            if (have > 0L) c.setRequestProperty("Range", "bytes=$have-")
            val code = c.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                hops++
                if (loc == null || hops > 8) throw IOException("Invalid download redirect")
                url = URL(URL(url), loc).toString()
                continue
            }
            return c
        }
    }

    private suspend fun download(m: MS) = withContext(Dispatchers.IO) {
        AudioFiles.modelDir(ctx).mkdirs()
        val part = partFile(m)
        var retry = true
        while (true) {
            var have = if (part.exists()) part.length() else 0L
            val c = connect(base + m.info.file, have)
            try {
                val code = c.responseCode
                if (code == 416) {
                    part.delete()
                    if (retry) { retry = false; continue }
                    throw IOException("Server refused to resume the download")
                }
                var total: Long
                if (code == 206) {
                    total = c.getHeaderField("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                        ?: (have + c.contentLengthLong)
                } else if (code == 200) {
                    have = 0L
                    total = c.contentLengthLong
                } else {
                    throw IOException("Server replied HTTP $code")
                }
                if (total <= 0L) total = m.info.bytes
                if (total < 30000000L) throw IOException("Unexpected file size")
                m.total = total
                m.got = have
                var last = 0L
                c.inputStream.use { ins ->
                    FileOutputStream(part, have > 0L).use { out ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            have += n
                            ensureActive()
                            val now = SystemClock.elapsedRealtime()
                            if (now - last > 120L) { m.got = have; last = now }
                        }
                    }
                }
                m.got = have
                if (part.length() != total) throw IOException("Download is incomplete")
                val f = finalFile(m)
                f.delete()
                if (!part.renameTo(f)) throw IOException("Couldn't save the model")
                sp.edit().putLong("mdl_" + m.info.key, total).apply()
                return@withContext
            } finally {
                c.disconnect()
            }
        }
    }
}

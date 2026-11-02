package app.dinduy

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Snack(val msg: String, val undo: (() -> Unit)?, val id: Long = System.nanoTime())

class Morph(
    val l: Float, val t: Float, val r: Float, val b: Float, val rad: Float, val color: Color,
    val r4: FloatArray? = null,  // radius 4 sudut (kiri-atas, kanan-atas, kanan-bawah, kiri-bawah) dalam px
    val ghost: Ghost? = null,    // gaya teks kartu, supaya teks kartu bisa pudar-silang dengan isi catatan saat morph
    val up: Boolean = false,     // true = kartu "Tambah catatan" dari bawah layar (skala isi mengikuti tinggi, bukan lebar)
    val card: Boolean = false    // true = asalnya kartu catatan / tombol Baru (wadah + isi mengecil ke dalamnya); false = kartu "Tambah catatan" atau posisi tengah
)

class Ghost(
    val kind: String, val c2: Boolean, val fg: Color,
    val padH: androidx.compose.ui.unit.Dp, val padV: androidx.compose.ui.unit.Dp,
    val insTL: androidx.compose.ui.unit.Dp, val insBL: androidx.compose.ui.unit.Dp
)

sealed class Sheet {
    class LblEdit(val name: String) : Sheet()    // ganti nama / hapus label
    class Arc(val id: String) : Sheet()
    class Trs(val id: String) : Sheet()
    object Bk : Sheet()
    object EmptyTrash : Sheet()
}

class Pending(
    val notes: List<Note>, val pr: Prefs?, val nImg: Int, val at: Long, val dir: File, val ids: List<String>,
    val lbl: List<String> = emptyList(), val aids: List<String> = emptyList()
)

/** Dialog nama label. old == null: label baru; forNote != null: langsung dipasang ke catatan itu. */
class LblDlg(val old: String?, val forNote: String?)

object Imgs {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun peek(id: String): ImageBitmap? = cache.get(id)

    fun load(dir: File, id: String): ImageBitmap? {
        cache.get(id)?.let { return it }
        val f = File(dir, "$id.jpg")
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1200) s *= 2
        val bm = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val ib = bm.asImageBitmap()
        cache.put(id, ib)
        return ib
    }
}

@Composable
fun rememberImg(vm: AppVM, id: String): ImageBitmap? {
    val st = produceState<ImageBitmap?>(Imgs.peek(id), id) {
        if (value == null) value = withContext(Dispatchers.IO) { Imgs.load(vm.imgDir, id) }
    }
    return st.value
}

const val WEL = "# Selamat datang di Dinduy\n\nCatatan yang **rapi**, cepat, dan nyaman di jempol.\n\n## Mulai dari sini\n\n" +
    "- Ketuk **Baru** untuk membuat catatan\n- Tahan kartu catatan untuk memilih, menghapus, atau menggandakan\n" +
    "- Geser kapsul di atas keyboard untuk memformat teks\n\n### Daftar tugas\n\n- [x] Buka Dinduy\n" +
    "- [ ] Tulis catatan pertamamu\n- [ ] Coba ketuk kotak centang\n\n1. Ketik teks\n2. Pilih gaya di kapsul\n" +
    "3. Ketuk panah bawah untuk mode baca\n\n> Kutipan biasa yang rapi.\n\n> [!TIP]\n> Ketuk ikon kutipan untuk memilih gaya lain.\n\n" +
    "> [!IDEA]\n> Satu catatan bisa memuat banyak jenis kotak.\n\n```js\nconst halo = (nama) => \"Halo \" + nama;\n```\n\n" +
    "| Fitur | Status |\n|---|---|\n| Tabel | Siap |\n| Kode | Siap |\n\n---\n\nSelamat mencatat."

class AppVM(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val file = File(ctx.filesDir, "dinduy.json")
    val imgDir = File(ctx.filesDir, "img")
    private val sp = ctx.getSharedPreferences("dinduy", 0)

    var pr by mutableStateOf(Prefs())
    val docs = mutableStateListOf<Note>()
    val labels = mutableStateListOf<String>()
    var filter by mutableStateOf<String?>(null)   // null = semua catatan, selain itu = nama label yang menyaring beranda
    var lblDlg by mutableStateOf<LblDlg?>(null)
    var snack by mutableStateOf<Snack?>(null)
    var sheet by mutableStateOf<Sheet?>(null)
    var ed by mutableStateOf<Ed?>(null)
    val nav = Nav(this)
    var morph: Morph? = null
    val openP = Animatable(0f)                 // progres morph catatan (0 = kartu, 1 = layar penuh), dibagi ke semua daftar
    var openId: String? = null                 // catatan yang sedang dibuka/ditutup, supaya kartu lain memberi ruang
    val cardMorph = HashMap<String, Morph>()   // posisi kartu beranda terkini, untuk morph keluar
    var fabMorph: Morph? = null                // posisi tombol Baru, untuk morph catatan baru
    var pending by mutableStateOf<Pending?>(null)

    // pengingat
    var remDlg by mutableStateOf<String?>(null)   // id catatan yang sedang diatur pengingatnya (lembar bawah)
    var remFilter by mutableStateOf(false)        // true = beranda hanya menampilkan catatan berpengingat
    var permTick by mutableStateOf(0)             // dinaikkan saat status izin notifikasi mungkin berubah
    private val scheduled = HashMap<String, Long>()   // id catatan -> waktu alarm yang sudah dipasang

    // penutupan catatan: kartu tujuan bisa sedang bergerak (daftar digulir otomatis), jadi morph mengikutinya
    var closing = false                        // true selama morph keluar mengikuti kartu yang bergerak
    var revealId: String? = null               // catatan yang kartunya harus terlihat setelah menutup
    var revealTick by mutableStateOf(0)        // dinaikkan tiap menutup supaya beranda menggulir ke kartu itu
    var topSlot: Morph? = null                 // perkiraan posisi kartu paling atas, dipakai saat kartu tujuan belum tampil
    var morphTick by mutableStateOf(0)         // dinaikkan tiap frame saat morph bergerak (hanya dibaca di lambda gambar)
    var ghostVer by mutableStateOf(0)          // berubah saat bayangan teks kartu baru tersedia

    // kartu "Tambah catatan" (muncul dari bawah, bisa ditarik ke atas jadi catatan baru)
    var addOn by mutableStateOf(false)         // kartu diminta tampil
    val addF = Animatable(0f)                  // pecahan tinggi layar yang tertutup kartu (0 = tersembunyi, 1 = layar penuh)
    val addFade = Animatable(1f)               // 1 = normal, menuju 0 saat morph catatan sudah mengambil alih bentuk kartu
    var addHand by mutableStateOf(false)       // true selama kartu menyerahkan bentuknya ke morph catatan
    var lastBk by mutableStateOf(sp.getLong("bk", 0L))

    // state beranda (dihoist supaya BackHandler di root bisa mengaturnya)
    var searchOn by mutableStateOf(false)
    var query by mutableStateOf("")
    val sel = mutableStateListOf<String>()
    val seen = HashSet<String>()

    // ---------- suara (rekam, putar, model, transkripsi) ----------
    val rec = Rec(ctx, viewModelScope)
    val player = Player(ctx, viewModelScope)
    val models = Models(ctx, viewModelScope,
        { m -> show(m); Bg.done(ctx, "Unduhan terhenti", "Buka Dinduy lalu ketuk Lanjutkan.") },
        { k -> onModelReady(k) },
        { bgSync() })
    var audVer by mutableStateOf(0)                // dinaikkan saat berkas suara berubah (rekam / pulihkan)
    var trId by mutableStateOf<String?>(null)      // id suara yang sedang ditranskripsi
    var trStage by mutableStateOf(0)               // 0 menyiapkan suara, 1 memuat model, 2 menranskripsi, 3 membatalkan
    var needModel by mutableStateOf(false)         // dialog "belum ada model / API key"
    var trInfo by mutableStateOf("")               // keterangan tambahan tahap (mis. "Bagian 2 dari 5")
    val vault = KeyVault(ctx)
    var gqSet by mutableStateOf(vault.has())       // ada API key Groq tersimpan
    var gqHint by mutableStateOf("")               // key yang disamarkan untuk ditampilkan (gsk_••••1a2b)
    var gqState by mutableStateOf(0)               // 0 diam, 1 menguji, 2 berhasil, 3 gagal
    var gqMsg by mutableStateOf("")
    private var trJob: Job? = null

    private var saveJob: Job? = null
    private val now get() = System.currentTimeMillis()

    init {
        Rem.hook = this
        Bg.onStopRec = { stopRec() }
        load()
        syncReminders()
        player.onError = { m -> show(m) }
        gcAudio()
        if (gqSet) viewModelScope.launch {
            val k = withContext(Dispatchers.IO) { vault.load() }
            if (k == null) gqSet = false else gqHint = KeyVault.mask(k)
        }
    }

    override fun onCleared() {
        if (Rem.hook === this) Rem.hook = null
        Bg.onStopRec = null
        Bg.stop(ctx)
        rec.cancel()
        player.stop()
        super.onCleared()
    }

    /** Samakan alarm sistem dengan data catatan (dipasang, diganti, atau dibatalkan seperlunya). */
    fun syncReminders() {
        try { Rem.sync(ctx, docs.toList(), scheduled) } catch (e: Exception) { }
    }

    private fun load() {
        var db: Db? = null
        try {
            if (file.exists()) db = J.decodeFromString(Db.serializer(), file.readText())
        } catch (e: Exception) {
        }
        if (db == null) {
            val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            pr = Prefs()   // mode otomatis + warna Material You mengikuti perangkat
            val t = now
            docs.add(Note(id = "w", t = "Selamat datang di Dinduy", bl = md(WEL), x = "", u = t, c = t))
            docs[0] = docs[0].copy(x = plainOf(docs[0].bl))
            save()
        } else {
            pr = db.pr
            docs.addAll(db.docs.filter { it.dt == 0L || now - it.dt < 2592000000L })
            labels.addAll(db.lbl)
            syncLabels()
        }
    }

    fun save() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(200)
            syncReminders()
            val db = Db(pr, docs.toList(), labels.toList())
            withContext(Dispatchers.IO) { write(db) }
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        syncReminders()
        write(Db(pr, docs.toList(), labels.toList()))
    }

    private fun write(db: Db) {
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(J.encodeToString(Db.serializer(), db))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText()); tmp.delete()
            }
        } catch (e: Exception) {
        }
    }

    @JvmName("applyPr")
    fun setPr(p: Prefs) {
        pr = p; save()
    }

    // ---------- catatan ----------
    fun note(id: String): Note? = docs.firstOrNull { it.id == id }

    fun upd(id: String, f: (Note) -> Note) {
        val i = docs.indexOfFirst { it.id == id }
        if (i >= 0) {
            docs[i] = f(docs[i]); save()
        }
    }

    fun newNote(): Note {
        val t = now
        // dibuat saat melihat sebuah label -> otomatis berlabel itu (seperti Keep), supaya tidak hilang dari tampilan
        val n = Note(id = uid(), u = t, c = t, lb = listOfNotNull(filter))
        docs.add(0, n); save()
        return n
    }

    fun saveNote(id: String, title: String, bl: List<Blk>) {
        upd(id) { it.copy(t = title, bl = bl, x = plainOf(bl), u = now) }
    }

    fun dropIfEmpty(id: String, title: String, bl: List<Blk>) {
        if (title.isBlank() && plainOf(bl).isEmpty() && bl.none { it.isObj && it.ty != "hr" } && bl.none { it.ik.isNotEmpty() }) {
            // catatan kosong yang diberi pengingat tetap disimpan (seperti Google Keep)
            if (docs.removeAll { it.id == id && it.rm == 0L && it.au.isEmpty() }) save()
        }
    }

    fun live() = docs.filter { !it.a && it.dt == 0L }
    /** Catatan beranda setelah disaring label aktif. */
    fun liveF() = live().filter { (filter == null || filter in it.lb) && (!remFilter || it.rm > 0L) }

    // ---------- label ----------
    fun sortedLabels() = labels.sortedBy { it.lowercase() }
    fun labelCount(n: String) = live().count { n in it.lb }

    /** Pastikan setiap label yang dipakai catatan terdaftar di daftar label. */
    private fun syncLabels() {
        docs.forEach { d -> d.lb.forEach { n -> if (labels.none { it.equals(n, true) }) labels.add(n) } }
    }

    fun addLabel(raw: String, forNote: String? = null): Boolean {
        val n = raw.trim().take(30)
        if (n.isEmpty()) return false
        val ex = labels.firstOrNull { it.equals(n, true) }
        if (ex != null && forNote == null) { show("Label sudah ada"); return false }
        val name = ex ?: n.also { labels.add(it) }
        if (forNote != null) upd(forNote) { if (name in it.lb) it else it.copy(lb = it.lb + name) }
        save()
        return true
    }

    fun renameLabel(old: String, raw: String): Boolean {
        val n = raw.trim().take(30)
        if (n.isEmpty()) return false
        if (n == old) return true
        if (labels.any { !it.equals(old, true) && it.equals(n, true) }) { show("Label sudah ada"); return false }
        val ix = labels.indexOf(old)
        if (ix >= 0) labels[ix] = n
        for (i in docs.indices) {
            val d = docs[i]
            if (old in d.lb) docs[i] = d.copy(lb = d.lb.map { if (it == old) n else it }.distinct())
        }
        if (filter == old) filter = n
        save()
        return true
    }

    fun deleteLabel(name: String) {
        labels.remove(name)
        for (i in docs.indices) {
            val d = docs[i]
            if (name in d.lb) docs[i] = d.copy(lb = d.lb - name)
        }
        if (filter == name) filter = null
        save(); show("Label dihapus")
    }

    fun toggleLabel(id: String, name: String) {
        upd(id) { it.copy(lb = if (name in it.lb) it.lb - name else it.lb + name) }
    }

    fun archived() = docs.filter { it.a && it.dt == 0L }.sortedByDescending { it.u }
    fun trashed() = docs.filter { it.dt != 0L }.sortedByDescending { it.dt }
    fun sortKey(n: Note) = if (pr.sort == "c") (if (n.c != 0L) n.c else n.u) else n.u
    fun sorted(l: List<Note>): List<Note> =
        if (remFilter) l.sortedBy { Rem.sortKey(it) }   // tampilan Pengingat: urut menurut waktu bunyi, yang terlewat di atas
        else l.sortedWith(compareByDescending<Note> { it.p }.thenByDescending { sortKey(it) })

    fun show(msg: String, undo: (() -> Unit)? = null) {
        snack = Snack(msg, undo)
    }

    fun mark(ids: List<String>, msg: String, f: (Note) -> Note) {
        val prev = ids.mapNotNull { note(it) }
        prev.forEach { p -> upd(p.id) { f(it) } }
        show(msg) { prev.forEach { p -> upd(p.id) { p } } }
    }

    // ---------- pengingat ----------
    fun setReminder(id: String, at: Long, rr: String) {
        val prev = note(id) ?: return
        upd(id) { it.copy(rm = at, rr = rr, rd = 0L) }
        show("Pengingat diatur: " + Rem.fmt(at)) { upd(id) { it.copy(rm = prev.rm, rr = prev.rr, rd = prev.rd) } }
    }

    fun clearReminder(id: String) {
        val prev = note(id) ?: return
        upd(id) { it.copy(rm = 0L, rr = "", rd = 0L) }
        show("Pengingat dihapus") { upd(id) { it.copy(rm = prev.rm, rr = prev.rr, rd = prev.rd) } }
    }

    fun duplicate(id: String) {
        val d = note(id) ?: return
        val t = now
        docs.add(0, d.copy(id = uid(), t = (d.t.ifEmpty { "Tanpa judul" }) + " (salinan)", u = t, c = t, p = false, a = false, rm = 0L, rr = "", rd = 0L))
        save(); show("Catatan digandakan")
    }

    fun purge(id: String) {
        docs.removeAll { it.id == id }; save(); gcAudio(); show("Dihapus permanen")
    }

    fun purgeAll() {
        docs.removeAll { it.dt != 0L }; save(); gcAudio(); show("Sampah dikosongkan")
    }

    // ---------- suara ----------
    fun audIds(): Set<String> {
        val s = LinkedHashSet<String>()
        docs.forEach { d -> d.au.forEach { s.add(it.id) } }
        return s
    }

    /** Hapus berkas suara yang tidak dipakai catatan mana pun (sisa rekaman batal, catatan terhapus, dll). */
    fun gcAudio() {
        if (rec.active) return
        val keep = audIds()
        viewModelScope.launch(Dispatchers.IO) {
            AudioFiles.dir(ctx).listFiles()?.forEach { f ->
                if (f.isFile && f.extension == "m4a" && f.nameWithoutExtension !in keep) f.delete()
            }
        }
    }

    // ---------- kerja latar belakang: notifikasi + layanan foreground ----------
    private var bgJob: Job? = null

    private fun bgSnap(): Bg.Snap? {
        val dl = models.list.firstOrNull { it.status == 1 }
        val tr = trId
        if (rec.active) return Bg.Snap("Merekam suara", fmtTime(rec.ms), -2, true, true)
        if (dl != null) {
            val p = if (dl.total > 0L) (dl.got * 100L / dl.total).toInt().coerceIn(0, 100) else -1
            val txt = if (p >= 0) "$p% · " + (dl.got / 1048576L) + " dari " + (dl.total / 1048576L) + " MB" else "Menyiapkan unduhan…"
            return Bg.Snap("Mengunduh model " + modelLabel(dl.info.key), txt, p, false, false)
        }
        if (tr != null) {
            val st = when (trStage) {
                0 -> "Menyiapkan suara…"
                1 -> "Memuat model…"
                2 -> "Mengubah suara jadi teks…"
                4 -> "Mengirim ke Groq…"
                5 -> "Merapikan format…"
                else -> "Membatalkan…"
            }
            return Bg.Snap("Transkripsi berjalan", if (trInfo.isNotEmpty()) st + " " + trInfo else st, -1, false, false)
        }
        return null
    }

    /** Menyalakan notifikasi berjalan selama ada unduhan / rekaman / transkripsi; mati sendiri saat semuanya selesai. */
    fun bgSync() {
        if (bgJob?.isActive == true) return
        bgJob = viewModelScope.launch {
            while (true) {
                val sn = bgSnap() ?: break
                Bg.push(ctx, sn)
                kotlinx.coroutines.delay(1000)
            }
            bgJob = null
            Bg.stop(ctx)
        }
    }

    fun startRec(noteId: String): Boolean {
        player.stop()
        val ok = rec.start(noteId)
        if (!ok) show("Perekaman tidak bisa dimulai") else bgSync()
        return ok
    }

    fun stopRec() {
        val nid = rec.noteId ?: return
        val a = rec.finish()
        if (a == null) {
            show("Rekaman terlalu singkat atau gagal")
            return
        }
        upd(nid) { it.copy(au = it.au + a, u = now) }
        audVer++
    }

    fun cancelRec() {
        rec.cancel()
    }

    fun removeAudio(noteId: String, audId: String) {
        val prev = note(noteId) ?: return
        val a = prev.au.firstOrNull { it.id == audId } ?: return
        if (trId == audId) cancelTranscribe()
        player.stopIf(audId)
        upd(noteId) { it.copy(au = it.au.filter { x -> x.id != audId }) }
        show("Suara dihapus") {
            upd(noteId) { n -> if (n.au.any { it.id == audId }) n else n.copy(au = (n.au + a).sortedBy { it.at }) }
        }
    }

    private fun onModelReady(key: String) {
        val cur = models.list.firstOrNull { it.info.key == pr.wm }
        if (cur == null || cur.status != 2) setPr(pr.copy(wm = key))
        show("Model transkripsi siap dipakai")
        Bg.done(ctx, "Model transkripsi siap", "Sekarang bisa dipakai tanpa internet.")
    }

    fun cancelTranscribe() {
        if (trId != null) {
            trStage = 3
            trJob?.cancel()
            Groq.abort()
        }
    }

    // ---------- API key Groq (terenkripsi, tidak pernah masuk cadangan) ----------
    fun saveGroqKey(raw: String) {
        if (gqState == 1) return
        val k = KeyVault.clean(raw)
        if (k == null) {
            gqState = 3
            gqMsg = "Bentuk key tidak dikenali. Key Groq diawali gsk_ dan tanpa spasi. Coba salin ulang dari halaman Groq."
            return
        }
        gqState = 1
        gqMsg = "Menghubungi Groq…"
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { Groq.test(k) }
            if (err != null) {
                gqState = 3
                gqMsg = err
            } else {
                val ok = withContext(Dispatchers.IO) { vault.save(k) }
                if (ok) {
                    gqSet = true
                    gqHint = KeyVault.mask(k)
                    gqState = 2
                    gqMsg = "Terhubung ke Groq. Key tersimpan terenkripsi di perangkat ini."
                    if (pr.eng != "groq") setPr(pr.copy(eng = "groq"))
                } else {
                    gqState = 3
                    gqMsg = "Key valid, tetapi gagal menyimpannya dengan aman di perangkat ini."
                }
            }
        }
    }

    fun testSavedKey() {
        if (gqState == 1) return
        gqState = 1
        gqMsg = "Menghubungi Groq…"
        viewModelScope.launch {
            val k = withContext(Dispatchers.IO) { vault.load() }
            if (k == null) {
                gqSet = false; gqHint = ""
                gqState = 3
                gqMsg = "Key tidak ditemukan di perangkat ini. Masukkan lagi."
            } else {
                val err = withContext(Dispatchers.IO) { Groq.test(k) }
                if (err == null) { gqState = 2; gqMsg = "Key berfungsi. Groq siap dipakai." } else { gqState = 3; gqMsg = err }
            }
        }
    }

    fun removeGroqKey() {
        vault.clear()
        gqSet = false; gqHint = ""; gqState = 0; gqMsg = ""
        show("API key dihapus dari perangkat ini")
    }

    /** "groq", "off", atau "" (belum ada mesin yang siap). Mengikuti pilihan pengguna, jatuh ke yang tersedia. */
    fun engineNow(): String {
        val g = gqSet
        val o = models.anyReady()
        return when {
            pr.eng == "off" && o -> "off"
            g -> "groq"
            o -> "off"
            else -> ""
        }
    }

    private suspend fun groqSegs(src: File, audId: String, key: String, temps: MutableList<File>): List<TSeg> {
        val parts = withContext(Dispatchers.IO) { Groq.prepare(ctx, src, audId, temps) { ensureActive() } }
        val segs = ArrayList<TSeg>()
        for ((i, p) in parts.withIndex()) {
            trStage = 4
            trInfo = if (parts.size > 1) "Bagian ${i + 1} dari ${parts.size}" else ""
            val f = p.first
            val off = p.second
            val mime = if (f.extension == "m4a") "audio/mp4" else "audio/wav"
            val r = withContext(Dispatchers.IO) { Groq.transcribe(key, f, mime, pr.gl) }
            for (s in r) segs.add(TSeg(s.st + off, s.en + off, s.tx))
        }
        return segs
    }

    private suspend fun offlineSegs(src: File, mf: File, temps: MutableList<File>): List<TSeg> {
        val w = File(ctx.cacheDir, "tr_" + src.nameWithoutExtension + ".wav")
        temps.add(w)
        withContext(Dispatchers.IO) { WavDecoder.decode(src, w) { ensureActive() } }
        trStage = 1
        val model = withContext(Dispatchers.Default) { Whisper.loadModel(ctx, mf.absolutePath) }
        try {
            trStage = 2
            val lang = if (pr.gl.isEmpty()) "auto" else pr.gl
            val res = withContext(Dispatchers.Default) {
                Whisper.transcribe(model, w.absolutePath, WhisperConfig(language = lang))
            }
            val out = ArrayList<TSeg>()
            for (s in res.segments) out.add(TSeg(s.startMs / 1000.0, s.endMs / 1000.0, s.text.trim()))
            if (out.isEmpty() && res.text.isNotBlank()) out.add(TSeg(0.0, 0.0, res.text.trim()))
            return out
        } finally {
            withContext(NonCancellable) {
                try { Whisper.releaseModel(model) } catch (e: Throwable) { }
            }
        }
    }

    /** Suara -> teks. Groq (online, prioritas) atau Whisper offline; hasilnya jadi blok "Transkrip N" di isi catatan. */
    fun transcribe(noteId: String, audId: String) {
        if (trId != null) return
        val eng = engineNow()
        if (eng.isEmpty()) {
            needModel = true
            return
        }
        val src = AudioFiles.file(ctx, audId)
        if (!src.exists()) {
            show("Berkas suara tidak ditemukan")
            return
        }
        val mf = if (eng == "off") models.readyFile(pr.wm) else null
        if (eng == "off" && mf == null) {
            needModel = true
            return
        }
        trId = audId
        trStage = 0
        trInfo = ""
        bgSync()
        trJob = viewModelScope.launch {
            val temps = ArrayList<File>()
            try {
                var key: String? = null
                val segs: List<TSeg>
                if (eng == "groq") {
                    val k = withContext(Dispatchers.IO) { vault.load() }
                        ?: throw GroqException("API key tidak ditemukan. Masukkan lagi di Model transkripsi.")
                    key = k
                    segs = groqSegs(src, audId, k, temps)
                } else {
                    segs = offlineSegs(src, mf!!, temps)
                }
                val paras = Paras.build(segs)
                if (paras.isEmpty()) {
                    show("Tidak ada ucapan yang terdeteksi")
                } else {
                    var chunks: List<Chunk>? = null
                    var fmtErr: String? = null
                    var fmtOk = 0
                    if (key != null && pr.gf) {
                        trStage = 5
                        trInfo = ""
                        val kk: String = key
                        val fr = withContext(Dispatchers.IO) { Groq.format(kk, paras) { ensureActive() } }
                        chunks = fr?.chunks?.ifEmpty { null }; fmtErr = fr?.err; fmtOk = fr?.ok ?: 0
                    }
                    applyTranscript(noteId, audId) { n -> Paras.blocks("Transkrip $n", paras, chunks) }
                    val fe = fmtErr
                    show(
                        when {
                            key == null || !pr.gf -> "Transkrip ditambahkan ke catatan"
                            fe != null -> "Transkrip ditambahkan. Format otomatis dilewati: " + fe
                            fmtOk == 0 -> "Transkrip ditambahkan tanpa format otomatis"
                            else -> "Transkrip ditambahkan ke catatan"
                        }
                    )
                    Bg.done(ctx, "Transkrip siap", "Teks suaramu sudah masuk ke catatan.")
                }
            } catch (e: CancellationException) {
                show("Transkripsi dibatalkan")
                throw e
            } catch (e: Throwable) {
                if (trStage == 3) show("Transkripsi dibatalkan")
                else {
                    val em = e.message ?: "kesalahan tidak dikenal"
                    show("Transkripsi gagal: " + em)
                    Bg.done(ctx, "Transkripsi gagal", em)
                }
            } finally {
                withContext(NonCancellable) { temps.forEach { try { it.delete() } catch (x: Exception) { } } }
                Groq.abort()
                trId = null
                trStage = 0
                trInfo = ""
            }
        }
    }

    private fun applyTranscript(noteId: String, audId: String, make: (Int) -> List<Blk>) {
        val n = note(noteId) ?: return
        val i = n.au.indexOfFirst { it.id == audId }
        if (i < 0) return
        val a = n.au[i]
        val blocks = make(i + 1)
        val ids = blocks.map { it.id }
        val e = ed
        val open = e != null && e.noteId == noteId && nav.stack.any { s -> s is Scr.Note && s.id == noteId }
        if (open && e != null) {
            e.putTranscript(a.tb, blocks)   // editor yang terbuka memegang isi catatan; ia yang menyimpannya
        } else {
            upd(noteId) { nn ->
                val nb = putBlocks(nn.bl, a.tb, blocks)
                nn.copy(bl = nb, x = plainOf(nb), u = now)
            }
        }
        upd(noteId) { nn -> nn.copy(au = nn.au.map { if (it.id == audId) it.copy(tb = ids) else it }) }
    }

    fun addMd(title: String, body: String): Note {
        val bl = md(body)
        val t = now
        val n = Note(id = uid(), t = title, bl = bl, x = plainOf(bl), u = t, c = t, lb = listOfNotNull(filter))
        docs.add(0, n); save()
        return n
    }

    suspend fun importMd(uris: List<Uri>): List<Note> = withContext(Dispatchers.IO) {
        val out = ArrayList<Note>()
        for (u in uris) {
            try {
                val text = ctx.contentResolver.openInputStream(u)?.bufferedReader()?.readText() ?: continue
                var name = "Catatan"
                ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex("_display_name")
                    if (c.moveToFirst() && i >= 0) name = c.getString(i)
                }
                out.add(withContext(Dispatchers.Main) { addMd(name.replace(Regex("\\.(md|markdown|txt)$", RegexOption.IGNORE_CASE), ""), text) })
            } catch (e: Exception) {
            }
        }
        out
    }

    // ---------- gambar ----------
    suspend fun importImage(uri: Uri): Img? = withContext(Dispatchers.IO) {
        try {
            val bm: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val mx = maxOf(info.size.width, info.size.height)
                    if (mx > 1600) {
                        val k = 1600f / mx
                        dec.setTargetSize(
                            (info.size.width * k).toInt().coerceAtLeast(1),
                            (info.size.height * k).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2
                val raw = ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
                } ?: return@withContext null
                // BitmapFactory tidak memutar gambar sesuai EXIF (foto kamera potret jadi miring), jadi diputar di sini
                val deg = try {
                    ctx.contentResolver.openInputStream(uri)?.use { st ->
                        when (android.media.ExifInterface(st).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    } ?: 0f
                } catch (e: Exception) { 0f }
                if (deg != 0f) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, android.graphics.Matrix().apply { postRotate(deg) }, true) else raw
            }
            val out = Bitmap.createBitmap(bm.width, bm.height, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(out)
            cv.drawColor(android.graphics.Color.WHITE)
            cv.drawBitmap(bm, 0f, 0f, null)
            val id = uid()
            imgDir.mkdirs()
            FileOutputStream(File(imgDir, "$id.jpg")).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            val a = bm.width.toFloat() / bm.height
            Img(id = id, a = a, o = a)
        } catch (e: Exception) {
            null
        }
    }

    fun imgIds(): Set<String> {
        val s = LinkedHashSet<String>()
        docs.forEach { d -> d.bl.forEach { b -> b.im.forEach { s.add(it.id) } } }
        return s
    }

    // ---------- cadangan: ZIP (data.json + img/ + audio/); format lama DINDUY01 tetap terbaca ----------
    fun backupName(): String {
        val c = java.util.Calendar.getInstance()
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "dinduy-" + c.get(java.util.Calendar.YEAR) + p(c.get(java.util.Calendar.MONTH) + 1) +
            p(c.get(java.util.Calendar.DAY_OF_MONTH)) + "-" + p(c.get(java.util.Calendar.HOUR_OF_DAY)) +
            p(c.get(java.util.Calendar.MINUTE)) + ".dinduy"
    }

    /** Format baru: ZIP berisi data.json, folder img (foto), dan folder audio (suara). Format lama (DINDUY01) tetap bisa dibaca saat memulihkan. */
    fun writeBackup(os: OutputStream) {
        val imgs = imgIds().filter { id -> File(imgDir, "$id.jpg").let { it.exists() && it.length() > 0L } }
        val auds = audIds().filter { id -> AudioFiles.file(ctx, id).let { it.exists() && it.length() > 0L } }
        val head = buildJsonObject {
            put("app", "dinduy")
            put("v", 3)
            put("at", now)
            put("pr", J.encodeToJsonElement(pr))
            put("docs", J.encodeToJsonElement(docs.toList()))
            put("lbl", J.encodeToJsonElement(labels.toList()))
            put("img", JsonArray(imgs.map { JsonPrimitive(it) }))
            put("aud", JsonArray(auds.map { JsonPrimitive(it) }))
        }
        val zos = ZipOutputStream(BufferedOutputStream(os, 65536))
        zos.setLevel(Deflater.DEFAULT_COMPRESSION)
        zos.putNextEntry(ZipEntry("data.json"))
        zos.write(head.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.setLevel(Deflater.NO_COMPRESSION)   // foto & suara sudah terkompresi
        for (id in imgs) {
            zos.putNextEntry(ZipEntry("img/$id.jpg"))
            File(imgDir, "$id.jpg").inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        for (id in auds) {
            zos.putNextEntry(ZipEntry("audio/$id.m4a"))
            AudioFiles.file(ctx, id).inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        zos.finish()
        zos.flush()
    }

    fun markBackedUp() {
        lastBk = now; sp.edit().putLong("bk", lastBk).apply()
    }

    private fun InputStream.readFully(b: ByteArray) {
        var o = 0
        while (o < b.size) {
            val r = read(b, o, b.size - o)
            if (r < 0) throw java.io.IOException("eof")
            o += r
        }
    }

    private fun JsonObject.num(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.truthy(k: String): Boolean {
        val p = this[k] as? JsonPrimitive ?: return false
        return p.booleanOrNull ?: ((p.longOrNull ?: 0L) != 0L)
    }

    private fun docFrom(o: JsonObject): Note? {
        val id = (o["id"] as? JsonPrimitive)?.content ?: return null
        return if (o["bl"] != null) {
            try { J.decodeFromJsonElement<Note>(o) } catch (e: Exception) { null }
        } else {
            val h = (o["h"] as? JsonPrimitive)?.content
            val bl = if (h != null) htmlToBlocks(h) else md((o["b"] as? JsonPrimitive)?.content ?: "")
            val u = o.num("u").let { if (it == 0L) now else it }
            Note(
                id = id, t = (o["t"] as? JsonPrimitive)?.content ?: "", bl = bl, x = plainOf(bl), u = u,
                c = o.num("c").let { if (it == 0L) u else it }, a = o.truthy("a"), p = o.truthy("p"), dt = o.num("dt")
            )
        }
    }

    suspend fun stageBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "bk")
            dir.deleteRecursively(); dir.mkdirs()
            val p: Pending? = ctx.contentResolver.openInputStream(uri)?.use { raw ->
                val ins = BufferedInputStream(raw, 65536)
                ins.mark(16)
                val h = ByteArray(8)
                var n = 0
                while (n < 8) {
                    val r = ins.read(h, n, 8 - n)
                    if (r < 0) break
                    n += r
                }
                ins.reset()
                if (n >= 8 && String(h, 0, 8) == "DINDUY01") stageLegacy(ins, dir)
                else if (n >= 4 && h[0] == 'P'.code.toByte() && h[1] == 'K'.code.toByte()) stageZip(ins, dir)
                else null
            }
            if (p == null) false else {
                withContext(Dispatchers.Main) { pending = p }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private val idRe = Regex("[\\w-]{1,40}")

    private fun prefsOf(m: JsonObject): Prefs? = try { m["pr"]?.let { J.decodeFromJsonElement<Prefs>(it) } } catch (e: Exception) { null }

    /** Cadangan lama: DINDUY01 + panjang header (LE) + header JSON + biner foto. */
    private fun stageLegacy(ins: InputStream, dir: File): Pending? {
        val h0 = ByteArray(12)
        ins.readFully(h0)
        if (String(h0, 0, 8) != "DINDUY01") return null
        val hl = ByteBuffer.wrap(h0, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (hl < 2 || hl > 64 * 1024 * 1024) return null
        val hb = ByteArray(hl)
        ins.readFully(hb)
        val m = J.parseToJsonElement(String(hb, Charsets.UTF_8)).jsonObject
        if ((m["app"] as? JsonPrimitive)?.content != "dinduy") return null
        val notes = m["docs"]!!.jsonArray.mapNotNull { (it as? JsonObject)?.let { o -> docFrom(o) } }
        val ids = ArrayList<String>()
        val buf = ByteArray(65536)
        for (e in (m["img"] as? JsonArray) ?: JsonArray(emptyList())) {
            val o = e as JsonObject
            val id = (o["id"] as JsonPrimitive).content
            var n = o.num("n")
            if (!idRe.matches(id) || n < 0) return null
            FileOutputStream(File(dir, "$id.jpg")).use { fo ->
                while (n > 0) {
                    val r = ins.read(buf, 0, minOf(buf.size.toLong(), n).toInt())
                    if (r < 0) throw java.io.IOException("eof")
                    fo.write(buf, 0, r); n -= r
                }
            }
            ids.add(id)
        }
        val lbl = (m["lbl"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
        return Pending(notes, prefsOf(m), ids.size, m.num("at").let { if (it == 0L) now else it }, dir, ids, lbl)
    }

    /** Cadangan baru: ZIP (data.json, img/, audio/). Nama berkas dibentuk sendiri dari id yang divalidasi. */
    private fun stageZip(raw: InputStream, dir: File): Pending? {
        val zin = ZipInputStream(raw)
        var json: String? = null
        val imgs = ArrayList<String>()
        val auds = ArrayList<String>()
        val buf = ByteArray(65536)
        while (true) {
            val e = zin.nextEntry ?: break
            if (e.isDirectory) continue
            val name = e.name
            if (name == "data.json") {
                json = String(zin.readBytes(), Charsets.UTF_8)
            } else if (name.startsWith("img/") && name.endsWith(".jpg")) {
                val id = name.substring(4, name.length - 4)
                if (!idRe.matches(id)) continue
                FileOutputStream(File(dir, "$id.jpg")).use { fo ->
                    var r = zin.read(buf)
                    while (r >= 0) { fo.write(buf, 0, r); r = zin.read(buf) }
                }
                imgs.add(id)
            } else if (name.startsWith("audio/") && name.endsWith(".m4a")) {
                val id = name.substring(6, name.length - 4)
                if (!idRe.matches(id)) continue
                FileOutputStream(File(dir, "$id.m4a")).use { fo ->
                    var r = zin.read(buf)
                    while (r >= 0) { fo.write(buf, 0, r); r = zin.read(buf) }
                }
                auds.add(id)
            }
        }
        val j = json ?: return null
        val m = J.parseToJsonElement(j).jsonObject
        if ((m["app"] as? JsonPrimitive)?.content != "dinduy") return null
        val arr = m["docs"] as? JsonArray ?: return null
        val notes = arr.mapNotNull { (it as? JsonObject)?.let { o -> docFrom(o) } }
        val lbl = (m["lbl"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
        return Pending(notes, prefsOf(m), imgs.size, m.num("at").let { if (it == 0L) now else it }, dir, imgs, lbl, auds)
    }

    fun applyBackup(replace: Boolean) {
        val p = pending ?: return
        pending = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                imgDir.mkdirs()
                p.ids.forEach { File(p.dir, "$it.jpg").copyTo(File(imgDir, "$it.jpg"), overwrite = true) }
                if (p.aids.isNotEmpty()) {
                    AudioFiles.dir(ctx).mkdirs()
                    p.aids.forEach { File(p.dir, "$it.m4a").copyTo(AudioFiles.file(ctx, it), overwrite = true) }
                }
            }
            player.stop()
            audVer++
            if (replace) {
                docs.clear(); docs.addAll(p.notes)
                labels.clear(); labels.addAll(p.lbl)
                filter = null
                p.pr?.let { pr = it.copy(fs = it.fs.coerceIn(14, 30)) }
            } else {
                for (d in p.notes) {
                    val i = docs.indexOfFirst { it.id == d.id }
                    if (i < 0) docs.add(d) else if (d.u > docs[i].u) docs[i] = d
                }
                p.lbl.forEach { n -> if (labels.none { it.equals(n, true) }) labels.add(n) }
            }
            syncLabels()
            save()
            if (replace) gcAudio()
            show(p.notes.size.toString() + " catatan, " + p.nImg + " foto, dan " + p.aids.size + " suara dipulihkan")
        }
    }
}

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

class Pending(val notes: List<Note>, val pr: Prefs?, val nImg: Int, val at: Long, val dir: File, val ids: List<String>, val lbl: List<String> = emptyList())

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

    private var saveJob: Job? = null
    private val now get() = System.currentTimeMillis()

    init {
        Rem.hook = this
        load()
        syncReminders()
    }

    override fun onCleared() {
        if (Rem.hook === this) Rem.hook = null
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
        if (title.isBlank() && plainOf(bl).isEmpty() && bl.none { it.isObj && it.ty != "hr" }) {
            // catatan kosong yang diberi pengingat tetap disimpan (seperti Google Keep)
            if (docs.removeAll { it.id == id && it.rm == 0L }) save()
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
        docs.removeAll { it.id == id }; save(); show("Dihapus permanen")
    }

    fun purgeAll() {
        docs.removeAll { it.dt != 0L }; save(); show("Sampah dikosongkan")
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
                ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
                } ?: return@withContext null
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

    // ---------- cadangan: DINDUY01 + panjang header (LE) + header JSON + biner foto ----------
    fun backupName(): String {
        val c = java.util.Calendar.getInstance()
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "dinduy-" + c.get(java.util.Calendar.YEAR) + p(c.get(java.util.Calendar.MONTH) + 1) +
            p(c.get(java.util.Calendar.DAY_OF_MONTH)) + "-" + p(c.get(java.util.Calendar.HOUR_OF_DAY)) +
            p(c.get(java.util.Calendar.MINUTE)) + ".dinduy"
    }

    fun writeBackup(os: OutputStream) {
        val files = imgIds().map { it to File(imgDir, "$it.jpg") }.filter { it.second.exists() && it.second.length() > 0 }
        val head = buildJsonObject {
            put("app", "dinduy")
            put("v", 2)
            put("at", now)
            put("pr", J.encodeToJsonElement(pr))
            put("docs", J.encodeToJsonElement(docs.toList()))
            put("lbl", J.encodeToJsonElement(labels.toList()))
            put("img", JsonArray(files.map { (id, f) ->
                buildJsonObject { put("id", id); put("n", f.length()); put("t", "image/jpeg") }
            }))
        }
        val hb = head.toString().toByteArray(Charsets.UTF_8)
        os.write("DINDUY01".toByteArray())
        os.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(hb.size).array())
        os.write(hb)
        for ((_, f) in files) f.inputStream().use { it.copyTo(os) }
        os.flush()
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
            ctx.contentResolver.openInputStream(uri)?.use { raw ->
                val ins = BufferedInputStream(raw)
                val h0 = ByteArray(12)
                ins.readFully(h0)
                if (String(h0, 0, 8) != "DINDUY01") return@withContext false
                val hl = ByteBuffer.wrap(h0, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (hl < 2 || hl > 64 * 1024 * 1024) return@withContext false
                val hb = ByteArray(hl)
                ins.readFully(hb)
                val m = J.parseToJsonElement(String(hb, Charsets.UTF_8)).jsonObject
                if ((m["app"] as? JsonPrimitive)?.content != "dinduy") return@withContext false
                val notes = m["docs"]!!.jsonArray.mapNotNull { (it as? JsonObject)?.let { o -> docFrom(o) } }
                val ids = ArrayList<String>()
                val buf = ByteArray(65536)
                for (e in (m["img"] as? JsonArray) ?: JsonArray(emptyList())) {
                    val o = e as JsonObject
                    val id = (o["id"] as JsonPrimitive).content
                    var n = o.num("n")
                    if (!Regex("[\\w-]{1,40}").matches(id) || n < 0) return@withContext false
                    FileOutputStream(File(dir, "$id.jpg")).use { fo ->
                        while (n > 0) {
                            val r = ins.read(buf, 0, minOf(buf.size.toLong(), n).toInt())
                            if (r < 0) throw java.io.IOException("eof")
                            fo.write(buf, 0, r); n -= r
                        }
                    }
                    ids.add(id)
                }
                val prf = try { m["pr"]?.let { J.decodeFromJsonElement<Prefs>(it) } } catch (e: Exception) { null }
                val lbl = (m["lbl"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                val p = Pending(notes, prf, ids.size, m.num("at").let { if (it == 0L) now else it }, dir, ids, lbl)
                withContext(Dispatchers.Main) { pending = p }
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun applyBackup(replace: Boolean) {
        val p = pending ?: return
        pending = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                imgDir.mkdirs()
                p.ids.forEach { File(p.dir, "$it.jpg").copyTo(File(imgDir, "$it.jpg"), overwrite = true) }
            }
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
            show(p.notes.size.toString() + " catatan dan " + p.nImg + " foto dipulihkan")
        }
    }
}

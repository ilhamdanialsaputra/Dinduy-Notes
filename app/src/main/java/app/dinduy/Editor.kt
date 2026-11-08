package app.dinduy

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Jarak antara gambar dan teks di sisinya (dp), dan lebar minimum kolom teks samping (dp). */
/** Ambang komposisi berjendela (biaya blok: 1 per blok, tabel 1 + sel/2). Histeresis: nyala di WIN_ON, mati di bawah WIN_OFF. */
const val WIN_ON = 140
const val WIN_OFF = 100
const val SIDE_GAP = 14f
const val SIDE_MIN = 110f
const val SIDE_W = 0.5f

private val RX_BUL = Regex("^([-*•+])\\s")
private val RX_NUM = Regex("^\\d{1,3}[.)]\\s")
private val RX_DASHL = Regex("^-\\p{L}")
private val RX_QT = Regex("^>\\s")
private val RX_HD = Regex("^(#{1,2})\\s")
private val RX_CK = Regex("^\\[([ xX]?)]\\s")
private val RX_URL = Regex("^https?://[^\\s<>\"]{4,}$")

/** Isi [s] kalau seluruhnya hanya satu alamat web, selain itu null. */
private fun asUrl(s: String): String? {
    val t = s.trim()
    return if (t.length <= 2000 && RX_URL.matches(t)) t else null
}

/**
 * Pemotongan baris buatan untuk satu paragraf yang melewati dasar gambar mengambang:
 * [cuts] = offset awal tiap baris yang disisipi pemisah baris visual; [pn] = jumlah spasi-em penggeser di awal tiap baris
 * (indeks 0 = baris pertama, indeks j = baris sesudah pemotongan ke-(j-1); ukurannya cuts.size + 1);
 * [pl] = letter-spacing (sp) tiap penggeser agar lebarnya pas. Penggeser dipakai untuk menggeser baris sempit di kanan gambar kiri
 * dan untuk inden baris pertama / menggantung (inden pada TextField tidak bisa dipakai karena tiap pemotongan memulai paragraf baru).
 * Hanya tampilan: teks aslinya tidak berubah.
 */

@androidx.compose.runtime.Immutable
class Fold(val cuts: IntArray, val pn: IntArray, val pl: FloatArray, val cutR: Float = 0f) {
    // [cutR] = px yang dikurangkan dari lebar kolom untuk baris sempit TANPA penggeser (gambar di kanan); dipakai rata kiri-kanan.
    override fun equals(other: Any?): Boolean =
        other is Fold && cutR == other.cutR && cuts.contentEquals(other.cuts) && pn.contentEquals(other.pn) && pl.contentEquals(other.pl)

    override fun hashCode(): Int =
        (((cuts.contentHashCode() * 31 + pn.contentHashCode()) * 31 + pl.contentHashCode()) * 31) + cutR.hashCode()

    private fun p0() = if (pn.isNotEmpty()) pn[0] else 0
    private fun pAt(j: Int) = if (j < pn.size) pn[j] else 0

    /** Jumlah karakter tambahan (pemisah baris + penggeser) di antara awal baris ke-1 dan pemotongan ke-m. */
    private fun cum(m: Int): Int {
        var s = 0
        for (j in 1..m) s += 1 + pAt(j)
        return s
    }

    val total: Int = p0() + cum(cuts.size)

    /** Offset asli (z = jumlah karakter awalan tak terlihat) -> offset tampilan. */
    fun o2t(o: Int, z: Int): Int {
        if (o < z) return o
        val r = o - z
        var c = 0
        while (c < cuts.size && cuts[c] <= r) c++
        return z + r + p0() + cum(c)
    }

    /** Offset tampilan -> offset asli. */
    fun t2o(t: Int, z: Int): Int {
        if (t < z) return t
        val tt = t - z - p0()
        if (tt <= 0) return z
        var passed = 0
        for (k in cuts.indices) {
            val start = cuts[k] + cum(k)
            if (tt <= start) break
            val len = 1 + pAt(k + 1)
            if (tt < start + len) return z + cuts[k]
            passed = k + 1
        }
        return z + maxOf(0, tt - cum(passed))
    }
}

/** Titik di dalam teks (kunci kolom + offset) untuk seleksi lintas blok. */
data class Pt(val key: String, val off: Int)

/** Gambar yang sedang diangkat (tekan-tahan lalu geser). */
class Mv(val bid: String, val imgId: String, val row: Boolean, val top: Float, val h: Float, val g: Float = -1f)

class Req(val key: String, val pos: Int, val n: Int, val sel: TextRange? = null)

/** Sasaran kolase saat gambar diseret ke atas gambar lain: gambar [imgId] di blok [bid]; [after] = true bila gambar yang diseret masuk di kanannya. */
class MergeT(val bid: String, val imgId: String, val after: Boolean)

/** Satu kecocokan pada mode "cari dalam catatan". key "@t" = judul, selain itu kunci kolom teks. */
data class FHit(val key: String, val s: Int, val e: Int)
class Snap(val bl: List<Blk>, val key: String?, val sel: TextRange)

fun keyBlock(k: String) = k.substringBefore(':')
fun isCellKey(k: String) = k.indexOf(':') >= 0
fun cellRC(k: String): Pair<Int, Int> {
    val p = k.split(':')
    return (p.getOrNull(1)?.toIntOrNull() ?: 0) to (p.getOrNull(2)?.toIntOrNull() ?: 0)
}

/**
 * Penyimpan TextFieldValue per kolom. Tiap kunci punya state sendiri, jadi mengetik di satu blok TIDAK membangunkan
 * kolom lain (mutableStateMapOf = satu objek state untuk seluruh peta: tiap put meng-invalidate SEMUA kolom yang membacanya).
 * [ver] naik saat kunci ditambah / dihapus dan hanya dibaca kalau kunci yang diminta belum ada.
 */
class TfStore {
    private val m = HashMap<String, androidx.compose.runtime.MutableState<TextFieldValue>>()
    private var ver by mutableStateOf(0)
    val keys: Set<String> get() = m.keys

    operator fun get(key: String): TextFieldValue? {
        val s = m[key]
        if (s == null) {
            if (ver == Int.MIN_VALUE) return null   // hanya untuk berlangganan: kunci baru akan membangunkan pembaca ini
            return null
        }
        return s.value
    }

    operator fun set(key: String, v: TextFieldValue) {
        val s = m[key]
        if (s == null) { m[key] = mutableStateOf(v); ver += 1 } else s.value = v
    }

    fun remove(key: String) { if (m.remove(key) != null) ver += 1 }
    fun clear() { if (m.isNotEmpty()) { m.clear(); ver += 1 } }
    fun containsKey(key: String): Boolean = m.containsKey(key)
    fun removePrefix(p: String) { if (m.keys.removeAll { it.startsWith(p) }) ver += 1 }
}

@androidx.compose.runtime.Stable
class Ed(private val vm: AppVM, val noteId: String, private val scope: CoroutineScope) {
    var title by mutableStateOf("")
    var blocks by mutableStateOf<List<Blk>>(listOf(Blk()))
    val tf = TfStore()
    var focus by mutableStateOf<String?>(null)
    var typing by mutableStateOf(setOf<Int>())
    var reading by mutableStateOf(false)
    var pickQuote by mutableStateOf(false)
    var selImg by mutableStateOf<Pair<String, Int>?>(null)
    /** Objek sisipan (tabel / kode / kutipan / garis) yang sedang dipilih tanpa masuk ke teksnya: mode atur (geser, ubah lebar). Ketukan kedua di teksnya baru mengedit. */
    var selObj by mutableStateOf<String?>(null)
    var req by mutableStateOf<Req?>(null)
    var titleFocus by mutableStateOf(false)
    var clearReq by mutableStateOf(0)
    var titleReq by mutableStateOf(0)
    var tick by mutableStateOf(0)
    var miniBar by mutableStateOf(false)
    var widthDp by mutableStateOf(340f)
    var barH by mutableStateOf(0f)        // tinggi kapsul alat (px) yang melayang di atas keyboard
    var barOn by mutableStateOf(false)    // kapsul alat sedang tampil
    val ink = InkCtl()                    // keadaan mode coret (lihat Ink.kt)
    var sheet by mutableStateOf(0)        // sheet bawah: 0 tutup, 1 Sisipkan, 2 Format
    var sub by mutableStateOf(0)          // sub-bilah toolbar: 0 tutup, 1 perataan, 2 poin
    var sheetPage by mutableStateOf(0)    // halaman dalam sheet Sisipkan: 0 daftar, 1 pengaturan tabel
    var tbR by mutableStateOf(3)          // jumlah baris tabel yang akan disisipkan
    var tbC by mutableStateOf(3)          // jumlah kolom

    // penanda konversi otomatis (mis. "1. " -> nomor), untuk menahan IME yang mengembalikan teks penanda
    private var convKey: String? = null
    private var convPrefix = ""
    private var convAt = 0L

    private var rq = 0
    private val hist = ArrayList<Snap>()
    private var hi = 0
    private var typed = 0
    private var cJob: Job? = null
    private var sJob: Job? = null
    private var chg = false

    init {
        val n = vm.note(noteId)
        title = n?.t ?: ""
        blocks = migrateImgText((n?.bl ?: emptyList()).ifEmpty { listOf(Blk()) })
        blocks.forEach { seed(it) }
        norm()
        hist.add(Snap(blocks, null, TextRange.Zero))
        // catatan yang sudah ada isinya dibuka di tampilan penuh; hanya catatan baru (kosong) langsung ke editor
        if (title.isNotBlank() || plainOf(blocks).isNotEmpty() || blocks.any { it.isObj || it.ik.isNotEmpty() }) reading = true
    }

    /** Catatan lama menyimpan teks "di samping gambar" di blok gambar; sekarang jadi paragraf biasa tepat setelah gambar. */
    private fun migrateImgText(l: List<Blk>): List<Blk> {
        val out = ArrayList<Blk>()
        for (b in l) {
            if (b.ty == "img" && b.r.t.isNotEmpty()) { out.add(b.copy(r = Rt())); out.add(Blk(ty = "p", r = b.r)) }
            else out.add(b)
        }
        return out
    }

    // ---------- util ----------
    private fun seed(b: Blk) {
        if (b.ty == "tbl") {
            b.tb.forEachIndexed { r, row ->
                row.forEachIndexed { c, cell -> tf["${b.id}:$r:$c"] = TextFieldValue(Z + cell.t, TextRange(1)) }
            }
        } else if (b.isText) tf[b.id] = TextFieldValue(Z + b.r.t, TextRange(1))
    }

    private fun reseedAll() {
        tf.clear(); blocks.forEach { seed(it) }
    }

    fun idx(id: String) = blocks.indexOfFirst { it.id == id }
    // peta id -> blok, dibangun ulang hanya kalau daftar blok berganti: pencarian O(1), bukan O(N) per kolom per ketikan
    private var idMapFor: List<Blk>? = null
    private var idMap = HashMap<String, Blk>()
    fun blockOfKey(k: String): Blk? {
        val bl = blocks
        if (idMapFor !== bl) {
            val mm = HashMap<String, Blk>(bl.size * 2 + 4)
            for (x in bl) if (x.id !in mm) mm[x.id] = x
            idMap = mm; idMapFor = bl
        }
        return idMap[keyBlock(k)]
    }

    fun rtOf(key: String): Rt {
        val b = blockOfKey(key) ?: return Rt()
        if (!isCellKey(key)) return b.r
        val (r, c) = cellRC(key)
        return b.tb.getOrNull(r)?.getOrNull(c) ?: Rt()
    }

    private fun putRt(key: String, rt: Rt) {
        val id = keyBlock(key)
        val rc = if (isCellKey(key)) cellRC(key) else null
        blocks = blocks.map { b ->
            if (b.id != id) b
            else if (rc != null) b.copy(tb = b.tb.mapIndexed { ri, row ->
                if (ri != rc.first) row else row.mapIndexed { ci, cell -> if (ci == rc.second) rt else cell }
            })
            else b.copy(r = rt, ik = inkRemap(b.ik, b.r.t, rt.t))   // jangkar coretan ikut bergeser mengikuti perubahan huruf
        }
    }

    private fun setBlock(i: Int, nb: Blk) {
        blocks = blocks.toMutableList().also { it[i] = nb }
    }

    fun focusAt(key: String, pos: Int) {
        req = Req(key, pos, ++rq)
    }

    fun applyReq(r: Req) {
        val v = tf[r.key] ?: return
        val rs = r.sel
        val p = (r.pos + 1).coerceIn(1, v.text.length.coerceAtLeast(1))
        tf[r.key] = if (rs != null) {
            val n = v.text.length
            v.copy(selection = TextRange(rs.start.coerceIn(0, n), rs.end.coerceIn(0, n)))
        } else v.copy(selection = TextRange(p))
        caretMoved(r.key)
        if (req === r) req = null
    }

    fun onFocus(key: String) {
        titleFocus = false
        selImg = null; selObj = null
        caretMoved(key)
    }

    fun caretMoved(key: String) {
        focus = key
        val v = tf[key] ?: return
        typing = rtOf(key).flags(v.selection.min - 1, v.selection.max - 1)
    }

    fun activeFlags(): Set<Int> {
        val k = focus ?: return emptySet()
        val v = tf[k] ?: return emptySet()
        val a = v.selection.min - 1
        val b = v.selection.max - 1
        return if (a == b) typing else rtOf(k).flags(a, b)
    }

    private fun clampSel(v: TextFieldValue): TextFieldValue =
        if (v.selection.min >= 1) v
        else v.copy(selection = TextRange(maxOf(1, v.selection.start), maxOf(1, v.selection.end)))

    // ---------- teks ----------
    fun onText(key: String, v0: TextFieldValue) {
        val old = tf[key] ?: return
        if (v0.text == old.text) {
            val whole = v0.text.isNotEmpty() && !v0.selection.collapsed &&
                v0.selection.min <= 1 && v0.selection.max >= v0.text.length
            if (whole) {
                // "Pilih semua" (Gboard / keyboard) memilih seluruh kolom: naikkan jadi seleksi seluruh catatan
                if (!selOn) selectAllKey(key) else selAll()
                return
            }
            // tombol "lompat ke awal/akhir teks" Gboard saat menyeleksi = setSelection(jangkar, ujung blok): perluas ke ujung catatan
            if (!v0.selection.collapsed && !isCellKey(key)) {
                val vl = v0.text.length
                val toEnd = v0.selection.end >= vl && old.selection.end < vl - 1
                val toStart = v0.selection.end <= 0 && old.selection.end > 1
                if (toEnd || toStart) {
                    ordRefresh()
                    val k = (if (toEnd) ordList.lastOrNull() else ordList.firstOrNull())
                    if (k != null && k != key) {
                        extendJump(key, (v0.selection.start - 1).coerceAtLeast(0), (old.selection.end - 1).coerceAtLeast(0), Pt(k, if (toEnd) rtOf(k).t.length else 0))
                        return
                    }
                }
            }
            val v = clampSel(v0)
            tf[key] = v
            if (v.selection != old.selection) { if (selEdit) dropSel(); caretMoved(key) }
            return
        }
        if (selEdit && selSpan()?.let { it.first != it.second } == true && typeOverSel(key, v0)) return
        val oldRt = rtOf(key)
        var v = v0
        if (!v.text.startsWith(Z)) {
            if (v.text == oldRt.t && v.selection.collapsed && v.selection.start == 0 && !isCellKey(key)) {
                tf[key] = TextFieldValue(Z + oldRt.t, TextRange(1))
                backStart(key)
                return
            }
            v = TextFieldValue(Z + v.text, TextRange(v.selection.min + 1, v.selection.max + 1))
        }
        if (v.text == old.text) {
            tf[key] = clampSel(v); return
        }
        val nt = v.text.substring(1)
        val b = blockOfKey(key) ?: return
        if (key == convKey && System.currentTimeMillis() - convAt < 1500 &&
            convPrefix.isNotEmpty() && nt.startsWith(convPrefix) && !oldRt.t.startsWith(convPrefix)
        ) {
            // IME mengembalikan "1. " / "- " yang sudah jadi penanda: buang lagi
            convKey = null
            val rt2 = oldRt.edit(nt.substring(convPrefix.length), typing)
            putRt(key, rt2)
            tf[key] = TextFieldValue(Z + rt2.t, TextRange((v.selection.max - convPrefix.length).coerceIn(1, rt2.t.length + 1)))
            dirty()
            return
        }
        val df = diff(oldRt.t, nt)
        val insStr = nt.substring(df.p, df.p + df.ins)
        val cell = isCellKey(key)
        // URL ditempel ke paragraf kosong -> kartu pratinjau tautan (ketikan biasa satu-satu tidak ikut, hanya tempel)
        if (df.ins >= 8 && !cell && b.ty == "p" && oldRt.t.isBlank()) {
            val u = asUrl(nt)
            if (u != null) { linkize(key, u); return }
        }
        if (df.ins > 0 && insStr.indexOf('\n') >= 0) {
            val without = nt.removeRange(df.p, df.p + df.ins)
            if (cell) {
                val rt2 = oldRt.edit(without, typing)
                putRt(key, rt2)
                tf[key] = TextFieldValue(Z + rt2.t, TextRange(df.p + 1))
                cellEnter(key)
                return
            }
            if (!b.isMulti) {
                val rt2 = oldRt.edit(without, typing)
                putRt(key, rt2)
                tf[key] = TextFieldValue(Z + rt2.t, TextRange(df.p + 1))
                if (insStr == "\n") enter(key, df.p) else paste(key, df.p, insStr, lastMd.isNotEmpty() && insStr.trimEnd('\n') == lastMd)
                return
            }
            if (insStr == "\n" && df.p == nt.length - 1 && (oldRt.t.isEmpty() || oldRt.t.endsWith("\n"))) {
                val keep = if (oldRt.t.endsWith("\n")) oldRt.slice(0, oldRt.t.length - 1) else oldRt
                exitMulti(key, keep)
                return
            }
        }
        val rt = oldRt.edit(nt, typing)
        putRt(key, rt)
        tf[key] = v
        typing = rt.flags(v.selection.min - 1, v.selection.max - 1)
        // pengelompokan undo per kalimat
        if (df.ins > 0) {
            typed += df.ins
            if (insStr.last() in ".!?") commit()
            else if (insStr == " " && typed >= 28) commit()
            else scheduleCommit()
        } else scheduleCommit()
        if (df.ins > 0 && v.selection.collapsed && !b.isMulti && !cell) {
            if (autoBlock(key, v, df)) return
        }
        if (df.ins > 0 && v.selection.collapsed && b.ty != "code") autoInline(key)
        dirty()
    }

    private fun scheduleCommit() {
        cJob?.cancel()
        cJob = scope.launch { delay(800); commit() }
    }

    private fun autoBlock(key: String, v: TextFieldValue, df: Df): Boolean {
        val i = idx(key)
        if (i < 0) return false
        val b = blocks[i]
        val rt = b.r
        val tx = rt.t
        if (tx.contains('\n')) return false
        val c = v.selection.start - 1
        var nb: Blk? = null
        var len = 0
        if (b.ty == "p") {
            var m = RX_BUL.find(tx)
            if (m != null && c == m.value.length) { nb = Blk(id = b.id, ik = b.ik, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi, ty = "li", lt = "b"); len = m.value.length }
            if (nb == null) {
                m = RX_NUM.find(tx)
                if (m != null && c == m.value.length) {
                    // "4. " -> butir bernomor 4 (nomor berikutnya melanjutkan dari angka itu)
                    val num = m.value.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                    nb = Blk(id = b.id, ik = b.ik, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi, ty = "li", lt = "o", st = num); len = m.value.length
                }
            }
            if (nb == null && RX_DASHL.containsMatchIn(tx) && c >= 2 && c <= 1 + df.ins) {
                nb = Blk(id = b.id, ik = b.ik, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi, ty = "li", lt = "b"); len = 1
            }
            if (nb == null) {
                m = RX_QT.find(tx)
                if (m != null && c == 2) { nb = Blk(id = b.id, ik = b.ik, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi, ty = "q", k = "quote", ba = "l"); len = 2 }
            }
            if (nb == null) {
                m = RX_HD.find(tx)
                if (m != null && c == m.value.length) { nb = Blk(id = b.id, ik = b.ik, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi, ty = "h", lv = m.groupValues[1].length); len = m.value.length }
            }
        } else if (b.ty == "li" && b.lt == "b") {
            val m = RX_CK.find(tx)
            if (m != null && c == m.value.length) {
                nb = Blk(id = b.id, ik = b.ik, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi, ty = "li", lt = "c", d = b.d, x = m.groupValues[1].isNotBlank()); len = m.value.length
            }
        }
        if (nb == null) return false
        commit()
        val nr = rt.slice(len, tx.length)
        setBlock(i, nb.copy(r = nr, ik = inkRemap(b.ik, tx, nr.t)))
        val np = (c - len).coerceAtLeast(0)
        tf[key] = TextFieldValue(Z + nr.t, TextRange(np + 1))
        typing = emptySet()
        convKey = key; convPrefix = tx.substring(0, len); convAt = System.currentTimeMillis()
        afterStructural()
        focusAt(key, np)
        return true
    }

    private fun autoInline(key: String) {
        val v = tf[key] ?: return
        val rt = rtOf(key)
        val c = v.selection.start - 1
        if (c < 2 || c > rt.t.length) return
        val tx = rt.t
        val ch = tx[c - 1]
        if ("*_~`".indexOf(ch) < 0) return
        val mks = if (tx[c - 2] == ch && ch != '`') listOf("$ch$ch", "$ch") else listOf("$ch")
        for (mk in mks) {
            val L = mk.length
            if (c - L < 0) continue
            val body = tx.substring(0, c - L)
            val oi = body.lastIndexOf(mk)
            if (oi < 0) continue
            val content = body.substring(oi + L)
            if (content.isEmpty()) continue
            if (ch != '`' && (content.first().isWhitespace() || content.last().isWhitespace() || content.contains(ch))) continue
            if (oi > 0 && " \t\n([{\"'\u201C\u2018".indexOf(body[oi - 1]) < 0) continue
            val kind = when (ch) { '*' -> K.B; '_' -> K.I; '~' -> K.S; else -> K.C }
            commit()
            val nr = rt.slice(0, oi) + Rt(content).withKind(kind) + rt.slice(c, tx.length)
            putRt(key, nr)
            tf[key] = TextFieldValue(Z + nr.t, TextRange(oi + content.length + 1))
            typing = emptySet()
            dirty(); commit()
            return
        }
    }

    // ---------- Enter / Backspace ----------
    private fun enter(key: String, pos: Int) {
        val i = idx(key)
        if (i < 0) return
        val b = blocks[i]
        commit()
        when {
            b.ty == "li" && b.r.t.isEmpty() -> {
                setBlock(i, if (b.d > 0) b.copy(d = b.d - 1) else b.copy(ty = "p"))
                focusAt(b.id, 0)
            }
            b.ty == "h" && b.r.t.isNotEmpty() && pos == 0 -> {
                val nb = Blk()
                blocks = blocks.toMutableList().also { it.add(i, nb) }
                seed(nb)
                focusAt(b.id, 0)
            }
            else -> split(i, pos, if (b.ty == "li") "li" else "p")
        }
        afterStructural()
    }

    private fun split(i: Int, pos: Int, nty: String) {
        val b = blocks[i]
        var left = b.r.slice(0, pos)
        var right = b.r.slice(pos, b.r.t.length)
        if (right.t.startsWith("\n")) right = right.slice(1, right.t.length)
        if (left.t.endsWith("\n")) left = left.slice(0, left.t.length - 1)
        val nb = if (nty == "li") Blk(ty = "li", lt = b.lt, d = b.d, r = right, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi) else Blk(ty = "p", r = right, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi)
        blocks = blocks.toMutableList().also { it[i] = b.copy(r = left, ik = inkRemap(b.ik, b.r.t, left.t)); it.add(i + 1, nb) }
        tf[b.id] = TextFieldValue(Z + left.t, TextRange(left.t.length + 1))
        seed(nb)
        focusAt(nb.id, 0)
    }

    private fun exitMulti(key: String, keep: Rt) {
        val i = idx(key)
        if (i < 0) return
        val b = blocks[i]
        commit()
        if (keep.t.isEmpty()) {
            setBlock(i, b.copy(ty = "p", r = Rt()))
            tf[b.id] = TextFieldValue(Z, TextRange(1))
            focusAt(b.id, 0)
        } else {
            val nb = Blk()
            blocks = blocks.toMutableList().also { it[i] = b.copy(r = keep, ik = inkRemap(b.ik, b.r.t, keep.t)); it.add(i + 1, nb) }
            tf[b.id] = TextFieldValue(Z + keep.t, TextRange(keep.t.length + 1))
            seed(nb)
            focusAt(nb.id, 0)
        }
        afterStructural()
    }

    private fun backStart(key: String) {
        val i = idx(key)
        if (i < 0) return
        val b = blocks[i]
        commit()
        if (b.ty == "li") {
            setBlock(i, if (b.d > 0) b.copy(d = b.d - 1) else b.copy(ty = "p"))
            focusAt(b.id, 0)
        } else if (b.ty == "h" || b.isMulti) {
            setBlock(i, b.copy(ty = "p"))
            focusAt(b.id, 0)
        } else {
            if (i == 0) return
            val pv = blocks[i - 1]
            when {
                pv.ty == "img" -> selectImg(pv.id, 0)   // jangan hapus gambar diam-diam: pilih dulu
                pv.ty == "hr" -> {
                    blocks = blocks.filterIndexed { j, _ -> j != i - 1 }
                    focusAt(b.id, 0)
                }
                pv.ty == "lnk" -> {
                    // kartu tautan kembali jadi tautan biasa (teks); Backspace berikutnya menghapusnya
                    val nb = Blk(id = pv.id, ty = "p", r = Rt(pv.l, listOf(Sp(0, pv.l.length, K.L, pv.l))))
                    blocks = blocks.toMutableList().also { it[i - 1] = nb }
                    seed(nb)
                    focusAt(nb.id, nb.r.t.length)
                }
                pv.ty == "tbl" -> {
                    val r = pv.tb.size - 1
                    val c = pv.tb[r].size - 1
                    val k = "${pv.id}:$r:$c"
                    focusAt(k, pv.tb[r][c].t.length)
                }
                else -> {
                    val at = pv.r.t.length
                    val merged = pv.r + b.r
                    blocks = blocks.toMutableList().also { it[i - 1] = pv.copy(r = merged); it.removeAt(i) }
                    tf.remove(b.id)
                    tf[pv.id] = TextFieldValue(Z + merged.t, TextRange(at + 1))
                    focusAt(pv.id, at)
                }
            }
        }
        afterStructural()
    }

    private fun paste(key: String, pos: Int, s: String, own: Boolean = false) {
        val i = idx(key)
        if (i < 0) return
        val b = blocks[i]
        commit()
        val ns0 = md(s.trimEnd('\n'), true, own)
        if (ns0.isEmpty()) return
        // satu alamat web saja yang ditempel ke paragraf kosong -> kartu pratinjau tautan
        val lone = if (ns0.size == 1 && ns0[0].ty == "p" && b.ty == "p" && b.r.t.isBlank()) asUrl(ns0[0].r.t) else null
        val ns = if (lone != null) listOf(Blk(ty = "lnk", l = lone)) else ns0
        val l = blocks.toMutableList()
        val rt = b.r
        if (b.ty == "img") l.addAll(i + 1, ns)
        else if (rt.t.isEmpty()) {
            l.removeAt(i); l.addAll(i, ns)
        } else if (pos >= rt.t.length) l.addAll(i + 1, ns)
        else if (pos <= 0) l.addAll(i, ns)
        else {
            val right = if (b.ty == "li") Blk(ty = "li", lt = b.lt, d = b.d, r = rt.slice(pos, rt.t.length), ta = b.ta, il = b.il, ir = b.ir, fi = b.fi)
            else Blk(ty = "p", r = rt.slice(pos, rt.t.length), ta = b.ta, il = b.il, ir = b.ir, fi = b.fi)
            l[i] = b.copy(r = rt.slice(0, pos), ik = inkRemap(b.ik, rt.t, rt.t.substring(0, pos.coerceIn(0, rt.t.length))))
            l.addAll(i + 1, ns + right)
        }
        blocks = l
        norm()
        reseedAll()
        val last = ns.last()
        if (last.isText) focusAt(last.id, last.r.t.length)
        else {
            val j = idx(last.id)
            blocks.getOrNull(j + 1)?.let { if (it.isText) focusAt(it.id, 0) }
        }
        afterStructural()
        if (lone != null) revealMedia(last.id)
    }

    // ---------- perintah toolbar ----------
    fun toggleFmt(kind: Int) {
        if (selOn) { selFmt(kind); return }
        val key = focus ?: return
        val v = tf[key] ?: return
        val blk = blockOfKey(key) ?: return
        if (blk.ty == "code") return
        if (blk.ty == "h" && kind == K.B) return
        val a = v.selection.min - 1
        val b = v.selection.max - 1
        commit()
        if (a == b) {
            typing = if (kind in typing) typing - kind else typing + kind
            return
        }
        putRt(key, rtOf(key).toggle(a, b, kind))
        typing = rtOf(key).flags(a, b)
        dirty(); commit()
    }

    private fun flat(r: Rt) = r.copy(t = r.t.replace('\n', ' '))

    /** Ubah jenis semua blok yang tersentuh seleksi (judul, poin, kutipan, ...). */
    private fun convertSel(f: (Blk) -> Blk) {
        val ids = selSegs().filter { !isCellKey(it.first) }.map { keyBlock(it.first) }.distinct()
        if (ids.isEmpty()) return
        commit()
        blocks = blocks.map { b -> if (b.id in ids && b.isText) f(b) else b }
        afterStructural()
    }

    private fun convertFocused(f: (Blk) -> Blk) {
        if (selOn) { convertSel(f); return }
        val key = focus ?: return
        if (isCellKey(key)) return
        val i = idx(key)
        if (i < 0) return
        if (blocks[i].ty == "img") return
        val keepSel = tf[key]?.selection
        commit()
        val nb = f(blocks[i])
        setBlock(i, nb)
        afterStructural()
        // jenis blok berubah = TextField dibuat ulang; minta fokus balik (dengan posisi kursor/seleksi sama) supaya keyboard tidak menutup
        req = Req(key, 0, ++rq, keepSel)
    }

    /** Paragraf berisi beberapa baris (\n) dipecah jadi satu blok per baris; kembalikan kunci blok di baris kursor. */
    private fun explodeLines(key: String): String {
        val i = idx(key)
        if (i < 0 || isCellKey(key)) return key
        val b = blocks[i]
        if ((b.ty != "p" && b.ty != "li") || !b.r.t.contains('\n')) return key
        val cur = ((tf[key]?.selection?.min ?: 1) - 1).coerceIn(0, b.r.t.length)
        commit()
        val parts = ArrayList<Blk>()
        var start = 0
        var target: Blk? = null
        var off = 0
        for ((n, ln) in b.r.t.split('\n').withIndex()) {
            val end = start + ln.length
            val r = b.r.slice(start, end)
            val nb = if (n == 0) b.copy(r = r)
                else if (b.ty == "li") Blk(ty = "li", lt = b.lt, d = b.d, r = r, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi)
                else Blk(ty = "p", r = r, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi)
            if (target == null && cur <= end) { target = nb; off = (cur - start).coerceAtLeast(0) }
            parts.add(nb)
            start = end + 1
        }
        val tg = target ?: parts.last()
        blocks = blocks.toMutableList().also { it[i] = parts[0]; it.addAll(i + 1, parts.drop(1)) }
        parts.forEach { seed(it) }
        tf[tg.id] = tf[tg.id]!!.copy(selection = TextRange(off + 1))
        focus = tg.id
        return tg.id
    }

    fun setHead(lv: Int) {
        if (!selOn) focus?.let { explodeLines(it) }
        setHeadRaw(lv)
    }

    private fun setHeadRaw(lv: Int) = convertFocused { b ->
        if (b.ty == "h" && b.lv == lv) b.copy(ty = "p") else b.copy(ty = "h", lv = lv, r = flat(b.r))
    }

    fun setList(t: String) {
        if (!selOn) focus?.let { explodeLines(it) }
        setListRaw(t)
    }

    private fun setListRaw(t: String) = convertFocused { b ->
        if (b.ty == "li" && b.lt == t) b.copy(ty = "p", st = 0)
        else b.copy(ty = "li", lt = t, d = if (b.ty == "li") b.d else 0, r = flat(b.r), st = if (b.ty == "li" && (t == "o" || t == "a")) b.st else 0)
    }

    fun setQuote(k: String) = convertFocused { b ->
        if (b.ty == "q" && b.k == k) b.copy(ty = "p") else b.copy(ty = "q", k = k, ba = b.ba.ifEmpty { "l" })   // sisipan baru: rata kiri
    }

    fun setPlain() = convertFocused { b -> b.copy(ty = "p") }

    /** Perataan paragraf: "" kiri, c tengah, r kanan, j rata kiri-kanan. Tidak berlaku untuk blok kode. */
    fun setAlign(a: String) = convertFocused { b -> if (b.isText && b.ty != "code" && b.ty != "q") b.copy(ta = a) else b }

    /**
     * Inden paragraf (dp, nilai mutlak). kind: 0 kiri, 1 kanan, 2 baris pertama, 3 menggantung.
     * Baris pertama dan menggantung saling meniadakan (satu angka bertanda di Blk.fi). Tidak berlaku untuk kode;
     * kutipan hanya mengenal inden kiri / kanan (baris pertama / menggantung per paragraf, bukan per baris kutipan).
     */
    fun setIndent(kind: Int, v: Int) = convertFocused { b ->
        if (!b.isText || b.ty == "code") b else when (kind) {
            0 -> b.copy(il = v.coerceIn(0, IND_MAX))
            1 -> b.copy(ir = v.coerceIn(0, IND_MAX))
            2 -> if (b.ty == "q") b else b.copy(fi = v.coerceIn(0, IND_MAX_FI))
            else -> if (b.ty == "q") b else b.copy(fi = -v.coerceIn(0, IND_MAX_FI))
        }
    }

    fun resetIndent() = convertFocused { b ->
        if (!b.isText || b.ty == "code") b else b.copy(il = 0, ir = 0, fi = 0)
    }

    fun setCode() = convertFocused { b ->
        if (b.ty == "code") b.copy(ty = "p") else b.copy(ty = "code", r = Rt(b.r.t), ba = b.ba.ifEmpty { "l" })   // sisipan baru: rata kiri
    }

    fun indent(dir: Int) {
        val key = focus ?: return
        val i = idx(key)
        if (i < 0 || blocks[i].ty != "li") return
        commit()
        setBlock(i, blocks[i].copy(d = (blocks[i].d + dir).coerceIn(0, 4)))
        afterStructural()
    }

    fun toggleCheck(id: String) {
        val i = idx(id)
        if (i < 0) return
        commit()
        setBlock(i, blocks[i].copy(x = !blocks[i].x))
        dirty(); commit()
    }

    fun grid(c: Int, r: Int): List<List<Rt>> = List(r) { List(c) { Rt() } }

    fun insertObjs(list: List<Blk>) {
        val key = focus?.let { keyBlock(it) } ?: selImg?.first ?: blocks.last().id
        var i = idx(key)
        if (i < 0) i = blocks.size - 1
        commit()
        val cur = blocks[i]
        val l = blocks.toMutableList()
        if (cur.ty == "p" && cur.r.t.isEmpty()) {
            l.removeAt(i); l.addAll(i, list)
        } else l.addAll(i + 1, list)
        blocks = l
        list.forEach { seed(it) }
        norm()
        val last = list.last()
        when (last.ty) {
            "tbl" -> focusAt("${last.id}:0:0", 0)
            "hr" -> blocks.getOrNull(idx(last.id) + 1)?.let { focusAt(it.id, 0) }
            "img" -> { selImg = last.id to 0; clearReq++ }
        }
        afterStructural()
    }

    fun insTable(r: Int = 3, c: Int = 3) = insertObjs(listOf(Blk(ty = "tbl", tb = grid(c, r), ca = List(c) { "left" }, ba = "l")))
    fun insHr() = insertObjs(listOf(Blk(ty = "hr", ba = "l")))
    fun addImages(imgs: List<Img>) {
        if (imgs.isEmpty()) return
        val list = imgs.map { Blk(ty = "img", im = listOf(it.copy(al = "l"))) }   // sisipan baru: rata kiri
        insertObjs(list)
        revealMedia(list.first().id)
    }

    /** URL ditempel di paragraf kosong [key]: ganti jadi kartu pratinjau tautan, kursor pindah ke paragraf sesudahnya. */
    private fun linkize(key: String, url: String) {
        val i = idx(key)
        if (i < 0) return
        val b = blocks[i]
        commit()
        val nb = Blk(ty = "lnk", l = url)
        blocks = blocks.toMutableList().also { it[i] = nb }
        tf.remove(b.id)
        afterStructural()
        val j = idx(nb.id)
        blocks.getOrNull(j + 1)?.let { if (it.isText) focusAt(it.id, 0) }
        revealMedia(nb.id)
    }

    /** Kartu tautan jadi tautan biasa (teks berisi alamatnya). */
    fun linkToText(id: String) {
        val i = idx(id)
        if (i < 0 || blocks[i].ty != "lnk") return
        val b = blocks[i]
        commit()
        val nb = Blk(id = b.id, ty = "p", r = Rt(b.l, listOf(Sp(0, b.l.length, K.L, b.l))))
        setBlock(i, nb)
        seed(nb)
        afterStructural()
        focusAt(nb.id, nb.r.t.length)
    }

    /** Hasil ambil data tautan (judul + alamat gambar) disimpan di kartu; tidak menambah langkah undo. */
    fun setLinkMeta(id: String, title: String, image: String) {
        val i = idx(id)
        if (i < 0) return
        val b = blocks[i]
        if (b.ty != "lnk" || (title.isEmpty() && image.isEmpty())) return
        val synced = hist[hi].bl === blocks || hist[hi].bl == blocks
        setBlock(i, b.copy(r = Rt(title.take(300)), pi = image))
        if (synced) hist[hi] = Snap(blocks, hist[hi].key, hist[hi].sel)
        dirty()
    }

    /** Gulir halus ke media yang baru disisipkan (gambar, kartu tautan) bila belum terlihat penuh. */
    fun revealMedia(id: String) {
        scope.launch {
            delay(280)   // tunggu keyboard turun dan tata letak selesai
            var n = 0
            while (n < 20) {
                val p = bpos[id]
                val sc = scroll
                if (p != null && sc != null && p[1] > 0f) {
                    delay(60)
                    val q = bpos[id] ?: p
                    val top = q[0]
                    val bot = top + q[1]
                    val m = 24f * dens
                    val vis = viewH - cursorClear()
                    val cur = sc.value.toFloat()
                    val want = when {
                        q[1] + 2f * m >= vis -> top - m
                        top < cur + m -> top - m
                        bot > cur + vis - m -> bot - vis + m
                        else -> return@launch
                    }
                    sc.animateScrollTo(want.toInt().coerceIn(0, sc.maxValue), tween(420, easing = Emph))
                    return@launch
                }
                n++
                delay(60)
            }
        }
    }

    // ---------- tabel ----------
    fun cellEnter(key: String) {
        val b = blockOfKey(key) ?: return
        val (r, c) = cellRC(key)
        if (r + 1 < b.tb.size) {
            val k = "${b.id}:${r + 1}:$c"
            focusAt(k, rtOf(k).t.length)
        } else {
            focus = key; tOp("ar")
        }
    }

    fun tOp(op: String) {
        val key = focus ?: return
        if (!isCellKey(key)) return
        val bid = keyBlock(key)
        val (r, c) = cellRC(key)
        val i = idx(bid)
        if (i < 0) return
        val b = blocks[i]
        commit()
        val cols = b.tb[0].size
        var nb = b
        var target = key
        when (op) {
            "ar" -> {
                val at = if (r == 0) 1 else r + 1
                nb = b.copy(tb = b.tb.toMutableList().also { it.add(at, List(cols) { Rt() }) })
                target = "$bid:$at:$c"
            }
            "ac" -> {
                nb = b.copy(
                    tb = b.tb.map { row -> row.toMutableList().also { it.add(c + 1, Rt()) } },
                    ca = b.ca.toMutableList().also { if (c + 1 <= it.size) it.add(c + 1, "left") },
                    cw = if (b.cw.isEmpty()) emptyList() else b.cw.toMutableList().also { while (it.size < cols) it.add(0f); it.add(c + 1, 0f) }
                )
                target = "$bid:$r:${c + 1}"
            }
            "dr" -> {
                if (r == 0 || b.tb.size < 3) { vm.show("This row can't be deleted"); return }
                nb = b.copy(tb = b.tb.filterIndexed { k, _ -> k != r })
                target = "$bid:${minOf(r, nb.tb.size - 1)}:$c"
            }
            "dc" -> {
                if (cols < 2) { vm.show("At least one column is needed"); return }
                nb = b.copy(tb = b.tb.map { row -> row.filterIndexed { k, _ -> k != c } }, ca = b.ca.filterIndexed { k, _ -> k != c }, cw = b.cw.filterIndexed { k, _ -> k != c })
                target = "$bid:$r:${minOf(c, cols - 2)}"
            }
            "dt" -> {
                blocks = blocks.filterIndexed { k, _ -> k != i }
                tf.removePrefix("$bid:")
                norm()
                focus = null
                afterStructural()
                blocks.getOrNull(minOf(i, blocks.size - 1))?.let { if (it.isText) focusAt(it.id, it.r.t.length) }
                return
            }
        }
        setBlock(i, nb)
        tf.removePrefix("$bid:")
        seed(nb)
        focusAt(target, 0)
        afterStructural()
    }

    // ---------- lebar kolom tabel ----------
    /** Ubah lebar kolom [c] (dp) langsung saat pegangan diseret; langkah undo dibuat lewat commit() di awal dan akhir seretan. */
    fun tblCol(bid: String, c: Int, w: Float) {
        val i = idx(bid)
        if (i < 0) return
        val b = blocks[i]
        val n = b.tb.firstOrNull()?.size ?: return
        if (c !in 0 until n) return
        val l = MutableList(n) { b.cw.getOrNull(it) ?: 0f }
        if (l[c] == w) return
        l[c] = w
        setBlock(i, b.copy(cw = if (l.all { it <= 0f }) emptyList() else l))
        dirty()
    }

    /** Kembalikan semua kolom ke lebar otomatis. */
    fun tblResetCols(bid: String) {
        val i = idx(bid)
        if (i < 0 || blocks[i].cw.isEmpty()) return
        commit()
        setBlock(i, blocks[i].copy(cw = emptyList()))
        dirty(); commit()
    }

    // ---------- gambar ----------
    /** Ketuk objek: dari mode baca masuk mode edit dulu (tanpa keyboard), lalu objek terpilih. */
    fun tapObj(bid: String) {
        if (reading) exitReading()
        selectObj(bid)
    }

    /** Rata kiri / tengah / kanan objek sisipan lewat tombol (alternatif seret). Hanya letak yang berubah, lebar tidak diubah. */
    fun objAlign(bid: String, al: String) {
        val i = idx(bid)
        if (i < 0) return
        commit()
        val b = blocks[i]
        setBlock(i, b.copy(ba = al))   // hanya letak yang berubah; lebar tidak pernah ikut diubah (objek penuh tetap penuh, hanya gagangnya pindah sisi)
        releaseSide(bid)
        dirty(); norm(); afterStructural()
    }

    /** Hapus kutipan / kode / garis / tabel. Tidak membuka keyboard. */
    fun objDelete(bid: String) {
        val i = idx(bid)
        if (i < 0) return
        commit()
        blocks = blocks.filterIndexed { k, _ -> k != i }.ifEmpty { listOf(Blk()) }
        tf.removePrefix("$bid:")
        selObj = null; selImg = null; focus = null
        norm(); afterStructural()
    }

    fun selectObj(bid: String) {
        if (selOn) dropSel()
        selObj = bid
        selImg = null
        focus = null
        clearReq++
    }

    fun selectImg(bid: String, ix: Int) {
        if (selOn) dropSel()
        selObj = null
        selImg = bid to ix
        focus = null
        clearReq++
    }

    /** Tutup pilihan gambar: tetap di mode edit (bar alat tetap ada), tanpa membuka keyboard dan tanpa masuk mode baca. */
    fun closeImg() {
        selImg = null; selObj = null
    }

    /** Dari mode baca: ketuk dua kali gambar = masuk mode edit dengan gambar itu terpilih. */
    fun openImgFromReading(bid: String, ix: Int) {
        exitReading()
        selectImg(bid, ix)
    }

    private fun imgSet(bid: String, ix: Int, f: (Img) -> Img) {
        blocks = blocks.map { b ->
            if (b.id == bid) b.copy(im = b.im.mapIndexed { j, m -> if (j == ix) f(m) else m }) else b
        }
    }

    /** Lebar relatif terbesar gambar supaya masih ada kolom teks di sampingnya. */
    fun maxSideW(): Float = (1f - (SIDE_MIN + SIDE_GAP) / widthDp.coerceAtLeast(200f)).coerceIn(.3f, .9f)

    fun sideOk(b: Blk): Boolean {
        val m = b.im.singleOrNull() ?: return false
        return b.ty == "img" && m.al != "c" && m.w.coerceIn(.2f, 1f) <= maxSideW() + .001f
    }

    /** Dulu memindahkan teks samping ke bawah gambar. Sekarang teks mengalir otomatis di sekitar gambar, jadi tak ada yang perlu dipindah. */
    fun releaseSide(bid: String, force: Boolean = false) {}

    /** Tabel cukup sempit (mis. 2 kolom) sehingga teks otomatis mengalir di sampingnya, tanpa pengaturan apa pun. */
    fun tblSideOk(b: Blk): Boolean =
        b.ty == "tbl" && b.alOf() != "c" && tblWidths(b, widthDp).sum() + 3f <= widthDp * maxSideW() + .001f

    // ---------- kutipan / kode / garis pemisah: sama seperti foto (seret, ubah lebar, teks mengelilingi) ----------
    /** Tinggi terukur (dp) objek yang teksnya mengalir di sampingnya; diisi tampilan supaya aliran teks memakai tinggi sebenarnya. */
    val objH = androidx.compose.runtime.mutableStateMapOf<String, Float>()
    fun setObjH(id: String, h: Float) {
        val o = objH[id]
        if (o == null || kotlin.math.abs(o - h) > .5f) objH[id] = h
    }
    fun boxSideOk(b: Blk): Boolean = b.isBox && b.alOf() != "c" && b.bw.coerceIn(.2f, 1f) <= maxSideW() + .001f
    fun boxLive(bid: String, w: Float) {
        blocks = blocks.map { if (it.id == bid) it.copy(bw = w.coerceIn(.2f, 1f)) else it }
        dirty()
    }

    fun imgAlign(al: String) {
        val s = selImg ?: return
        commit()
        imgSet(s.first, s.second) { it.copy(al = al) }   // hanya letak yang berubah; lebar tidak pernah ikut diubah
        releaseSide(s.first)
        dirty(); commit()
    }

    /** Ubah rasio tampil gambar terpilih (null = rasio asli). Di kolase, hanya gambar ini yang berubah; baris menyesuaikan. */
    fun imgRatio(r: Float?) {
        val s = selImg ?: return
        commit()
        imgSet(s.first, s.second) { it.copy(a = (r ?: it.o).coerceIn(.2f, 5f)) }
        dirty(); commit()
    }

    fun imgResetCrop() {
        val s = selImg ?: return
        commit()
        imgSet(s.first, s.second) { it.copy(px = .5f, py = .5f, z = 1f) }
        dirty(); commit()
    }

    fun imgOf(bid: String, ix: Int): Img? = blocks.firstOrNull { it.id == bid }?.im?.getOrNull(ix)

    /** Geser pembatas antara gambar [left] dan [left]+1 di kolase; tinggi baris tetap, jadi stabil di bawah jari. */
    fun rowDivider(bid: String, left: Int, da: Float, minA: Float) {
        blocks = blocks.map { b ->
            if (b.id != bid || left < 0 || left + 1 >= b.im.size) b
            else {
                val l = b.im[left]
                val r = b.im[left + 1]
                val lo = minA - l.a
                val hi = r.a - minA
                if (lo > hi) b
                else {
                    val d = da.coerceIn(lo, hi)
                    b.copy(im = b.im.mapIndexed { j, m ->
                        if (j == left) m.copy(a = m.a + d) else if (j == left + 1) m.copy(a = m.a - d) else m
                    })
                }
            }
        }
        dirty()
    }

    fun rowSwap(bid: String, ix: Int, dir: Int) {
        val i = idx(bid)
        if (i < 0) return
        val b = blocks[i]
        val j = ix + dir
        if (j !in b.im.indices) return
        val l = b.im.toMutableList()
        val t = l[ix]; l[ix] = l[j]; l[j] = t
        setBlock(i, b.copy(im = l))
        if (selImg?.first == bid) selImg = bid to j
        dirty()
    }

    fun imgLive(bid: String, ix: Int, f: (Img) -> Img) {
        imgSet(bid, ix, f); dirty()
    }

    fun rowScale(bid: String, k: Float) {
        blocks = blocks.map { b -> if (b.id == bid) b.copy(im = b.im.map { it.copy(a = it.a * k) }) else b }
        dirty()
    }

    fun imgDelete() {
        val s = selImg ?: return
        val i = idx(s.first)
        if (i < 0) return
        commit()
        val b = blocks[i]
        val rest = b.im.filterIndexed { j, _ -> j != s.second }
        if (rest.isEmpty()) {
            // teks di sisi gambar tidak ikut hilang: jadi paragraf biasa
            if (b.r.t.isNotEmpty()) setBlock(i, Blk(id = b.id, ik = b.ik, ta = b.ta, il = b.il, ir = b.ir, fi = b.fi, ty = "p", r = b.r))
            else blocks = blocks.filterIndexed { j, _ -> j != i }
        } else if (rest.size == 1) setBlock(i, b.copy(im = listOf(rest[0].copy(a = rest[0].o, w = 0.6f, al = "l"))))
        else setBlock(i, b.copy(im = rest))
        selImg = null; selObj = null
        norm(); afterStructural()
    }

    fun imgMove(dir: Int) {
        val s = selImg ?: return
        val i = idx(s.first)
        val j = i + dir
        if (i < 0 || j < 0 || j >= blocks.size) return
        commit()
        val l = blocks.toMutableList()
        val t = l[i]; l[i] = l[j]; l[j] = t
        blocks = l
        norm(); afterStructural()
    }

    fun toggleRow() {
        val s = selImg ?: return
        val i = idx(s.first)
        if (i < 0) return
        val b = blocks[i]
        commit()
        if (b.im.size == 1) releaseSide(b.id, true)
        if (b.im.size > 1) {
            val n = b.im.size
            val sum = b.im.sumOf { it.a.toDouble() }.toFloat()
            fun share(m: Img) = (m.a / sum * (1f - (n - 1) * GAP)).coerceIn(0.2f, 1f)
            val m = b.im[s.second]
            val solo = Blk(ty = "img", im = listOf(m.copy(a = m.o, w = share(m), al = "l")))
            val rest = b.im.filterIndexed { j, _ -> j != s.second }
            val restB = if (rest.size == 1) b.copy(im = listOf(rest[0].copy(a = rest[0].o, w = share(rest[0]), al = "l")))
            else b.copy(im = rest)
            val l = blocks.toMutableList()
            l.removeAt(i)
            if (s.second == 0) { l.add(i, restB); l.add(i, solo) } else { l.add(i, solo); l.add(i, restB) }
            blocks = l
            selImg = solo.id to 0
            norm(); afterStructural()
            return
        }
        fun emptyP(x: Blk) = x.ty == "p" && x.r.t.isEmpty()
        var j = i - 1
        while (j >= 0 && emptyP(blocks[j])) j--
        var prev = true
        var tgt = if (j >= 0 && blocks[j].ty == "img" && blocks[j].im.size < 3) j else -1
        if (tgt < 0) {
            j = i + 1
            while (j < blocks.size && emptyP(blocks[j])) j++
            prev = false
            tgt = if (j < blocks.size && blocks[j].ty == "img" && blocks[j].im.size < 3) j else -1
        }
        if (tgt < 0) { vm.show("No nearby image to place side by side"); return }
        val t = blocks[tgt]
        val me = b.im[0].copy(al = "c", w = 1f)
        val merged = if (prev) t.im + me else listOf(me) + t.im
        val n = merged.size
        val sum = merged.sumOf { it.a.toDouble() }.toFloat()
        val u = widthDp * (1f - (n - 1) * GAP)
        val h = u / sum
        val h2 = h.coerceIn(120f, 520f)
        val k = if (h2 != h) h / h2 else 1f
        val fin = merged.map { it.copy(a = it.a * k, al = "c", w = 1f) }
        val lo = minOf(tgt, i)
        val hiI = maxOf(tgt, i)
        val l = blocks.toMutableList()
        l[tgt] = t.copy(im = fin)
        blocks = l.filterIndexed { q, x -> q != i && !(q > lo && q < hiI && emptyP(x)) }
        selImg = t.id to (if (prev) n - 1 else 0)
        norm(); afterStructural()
    }


    // ---------- gambar: angkat & geser (tekan-tahan) ----------
    var mv by mutableStateOf<Mv?>(null)
    var mvX by mutableStateOf(0f)
    var mvY by mutableStateOf(0f)
    var mvAl by mutableStateOf("c")
    var dropAt by mutableStateOf(-1)
    var mergeT by mutableStateOf<MergeT?>(null)   // gambar yang sedang diseret berada di atas gambar lain: lepas = jadi kolase
    var replT: Pair<String, Int>? = null          // gambar yang akan diganti (dicatat saat tombol Ganti diketuk)
    var dens = 1f
    var viewH by mutableStateOf(0f)
    var barHLive by mutableStateOf(0f)            // tinggi kapsul/sheet langsung (tanpa debounce), dipakai gulir kursor
    // memakai tinggi kapsul yang sudah tenang (barH, debounce), BUKAN barHLive: saat fokus pindah judul<->isi kapsul beranimasi tiap frame,
    // dan tinggi per-frame itu memicu gulir kursor berulang (layar turun sendiri tanpa henti)
    fun cursorClear(): Float = if (barH > 0f) barH + 12f * dens else 12f * dens
    var scroll: ScrollState? = null

    // ---- komposisi berjendela (catatan sangat panjang) ----
    // Catatan yang sangat besar (ratusan blok / banyak sel tabel) tidak lagi menyusun SEMUA blok sekaligus:
    // hanya blok di sekitar layar (plus yang "wajib hidup": fokus, seleksi, hasil cari, gambar diangkat) yang disusun,
    // sisanya diganti Spacer setinggi terakhir terukur (atau perkiraan bila belum pernah diukur).
    // Catatan biasa tidak terpengaruh (ambang di bawah).
    var liveLo = 0f
    var liveHi = 4500f
    private var winFlag = false
    val windowed: Boolean by androidx.compose.runtime.derivedStateOf {
        var c = 0
        for (b in blocks) c += if (b.ty == "tbl") 1 + b.tb.sumOf { it.size } / 2 else 1
        winFlag = if (winFlag) c >= WIN_OFF else c >= WIN_ON
        winFlag
    }

    /** Blok (atau kelompok gambar-apung) beserta rencananya, urutan sama dengan yang digambar di NoteScreen. */
    fun slotsOf(plans: Map<String, FloatPlan>): List<Pair<Blk, FloatPlan?>> {
        val bl = blocks
        val out = ArrayList<Pair<Blk, FloatPlan?>>(bl.size)
        var i = 0
        while (i < bl.size) {
            val b = bl[i]
            val p = plans[b.id]
            out.add(b to p)
            i += 1 + (p?.parts?.size ?: 0)
        }
        return out
    }

    /** Perkiraan tinggi (px) satu blok yang belum pernah diukur. Kasar saja: selisihnya dikoreksi otomatis saat blok benar-benar tampil. */
    fun estimateH(b: Blk, fs: Float, lh: Float): Float {
        val w = (widthDp - b.il - b.ir - (if (b.ty == "li") 24f * (b.d + 1) else 0f)).coerceAtLeast(120f)
        val cw = (fs * 0.52f).coerceAtLeast(4f)
        fun lines(t: String, width: Float): Int {
            val cpl = (width / cw).toInt().coerceAtLeast(4)
            var n = 0
            var s = 0
            while (true) {
                val e = t.indexOf('\n', s)
                val len = (if (e < 0) t.length else e) - s
                n += maxOf(1, (len + cpl - 1) / cpl)
                if (e < 0) break
                s = e + 1
            }
            return n
        }
        val dp = when (b.ty) {
            "h" -> lines(b.r.t, w) * fs * 1.5f * 1.25f + 20f
            "tbl" -> {
                val nc = maxOf(1, b.tb.firstOrNull()?.size ?: 1)
                val colW = (w / nc).coerceAtLeast(90f)
                var sum = 8f
                for (row in b.tb) {
                    var mx = 1
                    for (cell in row) mx = maxOf(mx, lines(cell.t, colW - 16f))
                    sum += mx * fs * lh + 20f
                }
                sum
            }
            "img" -> {
                val m = b.im.firstOrNull()
                if (m == null) 120f else w * m.w.coerceIn(.2f, 1f) / m.a.coerceIn(.2f, 5f) + 16f
            }
            "hr" -> 28f
            "lnk" -> 110f
            "code", "q" -> lines(b.r.t, w - 24f) * fs * lh + 28f
            else -> lines(b.r.t, w) * fs * lh + 8f
        }
        return dp * dens
    }

    fun estimateSlot(b: Blk, plan: FloatPlan?, fs: Float, lh: Float): Float {
        val e0 = estimateH(b, fs, lh)
        if (plan == null) return e0
        var s = 0f
        for ((x, _) in plan.parts) s += estimateH(x, fs, lh)
        return maxOf(e0, s)
    }

    /** Himpunan id slot yang harus tersusun. Membaca state (dipakai di dalam snapshotFlow), jadi berlangganan semua yang relevan. */
    fun computeLive(scrollV: Int, plans: Map<String, FloatPlan>, fs: Float, lh: Float): Set<String> {
        val vh = if (viewH > 0f) viewH else 1600f
        val top = scrollV.toFloat()
        val bot = top + vh
        val m = .75f * vh
        val k = .35f * vh
        if (top - liveLo < k || liveHi - bot < k || liveHi - liveLo < vh + 1.2f * m) {
            liveLo = top - m
            liveHi = bot + m
        }
        ink.lay   // berlangganan perubahan posisi blok
        val forced = HashSet<String>()
        focus?.let { forced.add(keyBlock(it)) }
        req?.let { forced.add(keyBlock(it.key)) }
        selImg?.let { forced.add(it.first) }
        mv?.let { forced.add(it.bid) }
        mergeT?.let { forced.add(it.bid) }
        if (selOn) {
            pa?.let { forced.add(keyBlock(it.key)) }
            pf?.let { forced.add(keyBlock(it.key)) }
        }
        if (findOn) curHit()?.let { if (it.key != "@t") forced.add(keyBlock(it.key)) }
        val out = HashSet<String>()
        var y = 0f
        for ((b, p) in slotsOf(plans)) {
            val pos = bpos[b.id]
            val t = pos?.get(0) ?: y
            val h = pos?.get(1) ?: estimateSlot(b, p, fs, lh)
            y = t + h
            var hit = t <= liveHi && y >= liveLo
            if (!hit && forced.isNotEmpty()) {
                hit = b.id in forced || (p != null && p.parts.any { it.first.id in forced })
            }
            if (hit) out.add(b.id)
        }
        return out
    }

    /** Catat posisi slot. Bila slot yang seluruhnya di atas layar berubah tinggi, gulir digeser sebesar selisihnya agar isi di layar tidak melompat. */
    fun setSlotPos(id: String, y: Float, h: Float) {
        val mm = mv
        if (mm != null && !mm.row && mm.bid == id && mvLayTop != y) {
            mvLayTop = y   // slot yang diseret pindah di tata letak pratinjau: translasi seretan menyesuaikan di frame yang sama
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        val o = bpos[id]
        if (windowed && o != null && o[1] != h) {
            val sc = scroll
            if (sc != null && y + o[1] <= sc.value.toFloat()) sc.dispatchRawDelta(h - o[1])
        }
        setPos(id, y, h)
    }

    /** Slot keluar dari komposisi: buang layout/posisi basi supaya seleksi, hit-test, dan coretan tidak memakainya. */
    fun leaveSlot(blks: List<Blk>, leader: String) {
        if (!windowed) return
        val ids = HashSet<String>()
        for (x in blks) ids.add(x.id)
        lays.keys.filter { keyBlock(it) in ids }.forEach { lays.remove(it); lsh.remove(it); layLen.remove(it) }
        kpos.keys.filter { keyBlock(it) in ids }.forEach { kpos.remove(it) }
        for (x in blks) {
            for (m in x.im) ipos.remove(m.id)
            if (x.id != leader) bpos.remove(x.id)
        }
        ink.lay++
    }

    // ---- gulir otomatis ke kursor ----
    // Prinsip: HANYA satu sistem yang menggulir (fungsi ini). Gulir bawaan BasicTextField dimatikan di NoteScreen (cursorSpec).
    // Gulir hanya terjadi bila (a) kursor/teks/blok fokus berubah, atau (b) keyboard/kapsul berubah DAN kursor tadinya masih terlihat.
    // Perubahan layout kebetulan (blok lain berubah ukuran, dll) tidak boleh menyeret layar kembali ke kursor.
    private class CsSig(val key: String, val sel: Int, val len: Int, val vh: Float, val bh: Float) {
        override fun equals(other: Any?) = other is CsSig && key == other.key && sel == other.sel && len == other.len && vh == other.vh && bh == other.bh
        override fun hashCode() = ((key.hashCode() * 31 + sel) * 31 + len) * 31 + vh.hashCode() + bh.hashCode()
        fun sameCaret(o: CsSig) = key == o.key && sel == o.sel && len == o.len
    }
    private var csSig: CsSig? = null
    private var csPend = false          // ada perubahan yang belum sempat dihitung (menunggu layout yang sudah segar)
    private var csForce = false         // true: kursor berpindah / teks berubah -> wajib terlihat; false: hanya viewport berubah
    private var csLastForce = false
    private var csRefVh = 0f            // ukuran viewport sebelum perubahan (untuk cek "tadinya terlihat")
    private var csRefBh = 0f
    private var csTry = 0
    private var csBump by mutableStateOf(0)

    /** Dipanggil collector setelah menggulir: bila gulir terpotong (batas gulir belum ikut membesar), coba lagi beberapa frame. */
    fun csApplied(want: Float, used: Float) {
        if (kotlin.math.abs(want - used) > 1f && csTry < 5) {
            csTry++
            csPend = true
            csForce = csLastForce
            csBump++
        }
    }

    /** Selisih gulir agar kursor tetap terlihat di atas keyboard + kapsul; dipanggil lewat snapshotFlow. */
    fun cursorScrollDelta(scrollV: Int): Float {
        if (reading || selOn || ink.on || titleFocus || findOn) { csPend = false; return 0f }
        val key = focus ?: run { csSig = null; csPend = false; return 0f }
        val v = tf[key] ?: return 0f
        if (!v.selection.collapsed || viewH <= 0f) return 0f
        if (csBump < 0) return 0f   // hanya untuk berlangganan (dipakai coba-ulang)
        val sig = CsSig(key, v.selection.start, v.text.length, viewH, barH)
        val old = csSig
        if (sig != old) {
            csSig = sig
            if (old == null || !sig.sameCaret(old)) { csForce = true; csTry = 0; csPend = true }
            else if (!csPend) { csForce = false; csRefVh = old.vh; csRefBh = old.bh; csPend = true }
        }
        if (!csPend) return 0f
        val lay = lays[key] ?: return 0f
        val pos = kpos[key] ?: return 0f
        // layout harus layout dari kolom edit dan dari teks yang sekarang (kalau belum, tunggu: lays berubah => dihitung lagi)
        if (lsh[key] != 1 || layLen[key] != v.text.length) return 0f
        val lt = lay.layoutInput.text.length
        val o = toLay(key, v.selection.start - 1).coerceIn(0, lt)
        val r = lay.getCursorRect(o)
        val top = pos.y + r.top
        val bot = pos.y + r.bottom
        val vt = scrollV + 12f * dens
        val vb = scrollV + viewH - cursorClear()
        csPend = false
        csLastForce = csForce
        val force = csForce
        csForce = false
        if (!force) {
            // hanya keyboard/kapsul yang berubah: ikuti kursor hanya bila tadinya memang terlihat (pembaca yang sengaja menggulir menjauh tidak diseret balik)
            val clr0 = (if (csRefBh > 0f) csRefBh + 12f * dens else 12f * dens)
            val vb0 = scrollV + csRefVh - clr0
            if (!(bot <= vb0 + 2f && top >= vt - 2f)) return 0f
        }
        return when { bot > vb -> bot - vb; top < vt -> top - vt; else -> 0f }
    }
    var buzz: (() -> Unit)? = null
    val bpos = HashMap<String, FloatArray>()   // id blok -> [atas, tinggi] di ruang konten
    private var mvDir = 0
    private var autoJob: Job? = null

    private fun isEmptyP(x: Blk) = x.ty == "p" && x.r.t.isEmpty()

    // ---------- pratinjau lepas (drop) yang akurat ----------
    // Saat blok objek diseret, urutan + letaknya LANGSUNG dipasang ke daftar blok (pratinjau), jadi teks yang mengalir di
    // sekitarnya dihitung oleh mesin tata letak yang sama dengan hasil akhir: apa yang terlihat sebelum lepas = hasil setelah lepas.
    // Keputusan sasaran tetap memakai posisi SEBELUM pratinjau (bp0) supaya stabil, tidak saling memicu.
    // Blok lain meluncur halus ke posisi barunya (FLIP); "lubang" tempat objek akan mendarat ditandai bingkai putus-putus.
    var gOn by mutableStateOf(0f)           // 0..1: bingkai sasaran terlihat
    var gy by mutableStateOf(0f)            // atas bingkai (px, ruang konten)
    var gHt by mutableStateOf(0f)           // tinggi bingkai
    var gx by mutableStateOf(0f)
    var gw by mutableStateOf(0f)
    var gPulse by mutableStateOf(0f)        // denyut halus saat sasaran pindah
    var gT by mutableStateOf(0f)            // waktu berjalan (garis putus-putus bergerak, panah gulir)
    var gAdj by mutableStateOf(false)       // letak objek bisa diatur kiri / tengah / kanan
    var gKind by mutableStateOf("")         // jenis blok yang diseret (ikon di pil)
    var gLine by mutableStateOf(false)      // mode garis (anggota kolase yang diseret keluar)
    var edgeV by mutableStateOf(0f)         // dorongan gulir otomatis: -1 atas .. 1 bawah (sudah dihaluskan)
    var flipP by mutableStateOf(0f)         // 1 -> 0: blok meluncur dari tempat tampil lama ke tempat barunya
    var flipId by mutableStateOf("")
    var flipDx = 0f
    var landId by mutableStateOf("")
    var landA by mutableStateOf(0f)
    var mvLayTop by mutableStateOf(0f)      // atas slot blok yang diseret di tata letak pratinjau (px)
    var mvAdjX by mutableStateOf(0f)        // selisih letak kiri objek: tata letak asli - pratinjau
    val flipFrom = HashMap<String, Float>()
    private var ob: List<Blk> = emptyList() // daftar blok asli saat seretan dimulai
    private val bp0 = HashMap<String, FloatArray>()
    private var mvX0 = 0f
    private var pvKey = ""
    private var edgeRaw = 0f
    private var mvInk = false
    private var gKey = -2
    private var fxJob: Job? = null
    private class Spr {
        var x = 0f
        var v = 0f
        fun snap(t: Float) { x = t; v = 0f }
        fun step(t: Float, dt: Float, k: Float, z: Float): Float {
            val c = 2f * z * kotlin.math.sqrt(k)
            var left = dt
            while (left > 0f) {
                val h = if (left > .008f) .008f else left
                v += (k * (t - x) - c * v) * h
                x += v * h
                left -= h
            }
            return x
        }
    }
    private val sY = Spr()
    private val sH = Spr()
    private val sX = Spr()
    private val sW = Spr()
    private val sO = Spr()
    private val sF = Spr()

    /** Daftar blok yang dipakai keputusan sasaran: asli (tanpa pratinjau) untuk blok tunggal, daftar hidup untuk anggota kolase. */
    private fun src(): List<Blk> = if (mv?.row == false) ob else blocks

    private fun curAl(b: Blk): String = if (b.im.size == 1) b.im[0].al else b.alOf()

    /** Translasi seretan (px) relatif terhadap posisi slot di tata letak pratinjau: jari tetap memegang objek yang sama walau slotnya pindah. */
    fun liftY(): Float {
        val m = mv ?: return 0f
        return if (m.row) mvY else m.top + mvY - mvLayTop
    }
    fun liftX(): Float = mvX + (if (mv?.row == true) 0f else mvAdjX)

    /** Ukuran objek yang diseret: [x kiri, lebar, tinggi] dalam px bila diletakkan dengan perataan [al]. */
    private fun mvBox(m: Mv, b: Blk, q: FloatArray?, al: String): FloatArray {
        val w = widthDp * dens
        var wp = w
        var hp = q?.get(1) ?: 0f
        if (m.row) {
            val r = imgRect(b, b.im.indexOfFirst { it.id == m.imgId })
            if (r != null) { wp = r.width; hp = r.height }
            return floatArrayOf(0f, wp, hp)   // keluar dari kolase: jadi gambar sendiri di kiri
        }
        if (b.im.size == 1) {
            val mi = b.im[0]
            wp = w * mi.w.coerceIn(.2f, 1f)
            hp = wp / mi.a.coerceIn(.2f, 5f)
        } else if (b.ty == "tbl") {
            wp = ((tblWidths(b, widthDp).sum() + 3f) * dens).coerceAtMost(w)
            if (q != null) hp = minOf(q[1], m.h)
        } else if (b.isBox) {
            wp = w * b.bw.coerceIn(.2f, 1f)
            val oh = (objH[b.id] ?: 0f) * dens
            if (oh > 0f) hp = if (q != null) minOf(q[1], oh) else oh
        }
        val x = if (wp >= w * .98f) 0f else when (al) { "l" -> 0f; "r" -> w - wp; else -> (w - wp) / 2f }
        return floatArrayOf(x, wp, hp)
    }

    /** Hasil akhir bila blok [i] dilepas di celah [to] (-1 = tetap) dengan letak [al]. Dipakai pratinjau DAN pelepasan, jadi pasti sama. */
    private fun mvBuild(srcL: List<Blk>, i: Int, to: Int, al: String): List<Blk> {
        var b = srcL[i]
        if (b.im.size == 1 && al != b.im[0].al) b = b.copy(im = listOf(b.im[0].copy(al = al)))   // lebar tidak diubah
        if ((b.isBox || b.ty == "tbl") && al != b.alOf()) b = b.copy(ba = al)
        val l = srcL.toMutableList()
        if (to >= 0 && to != i && to != i + 1) {
            val anchor = if (to < srcL.size) srcL[to].id else null
            l.removeAt(i)
            if (i > 0 && i < l.size && isEmptyP(l[i - 1]) && isEmptyP(l[i])) l.removeAt(i)
            val at = if (anchor == null) l.size else l.indexOfFirst { it.id == anchor }.let { if (it < 0) l.size else it }
            l.add(at, b)
        } else l[i] = b
        return l
    }

    /** Pasang pratinjau ke daftar blok bila sasaran / letak berubah. Blok lain meluncur halus dari posisi lamanya. */
    private fun pvApply() {
        val m = mv ?: return
        if (m.row) return
        val i = ob.indexOfFirst { it.id == m.bid }
        if (i < 0) return
        val oal = curAl(ob[i])
        val to = if (mergeT != null) -1 else dropAt
        val al = if (mergeT != null) oal else mvAl
        val key = "$to|$al"
        if (key == pvKey) return
        pvKey = key
        gPulse = 1f
        if (!mvInk) flipCapture()   // catat posisi tampil sekarang SEBELUM tata letak berubah
        val l = if (to < 0 && al == oal) ob else mvBuild(ob, i, to, al)
        blocks = l
        val nb = l.firstOrNull { it.id == m.bid } ?: return
        mvAdjX = mvX0 - mvBox(m, nb, null, curAl(nb))[0]
    }

    /** Catat posisi tampil (termasuk sisa luncuran) semua slot; layout baru kemudian menarik mereka ke posisi barunya. */
    private fun flipCapture() {
        val fp = flipP
        val old = HashMap<String, Float>(flipFrom)
        flipFrom.clear()
        for ((id, q) in bpos) {
            val f = old[id]
            flipFrom[id] = if (fp != 0f && f != null) q[0] + (f - q[0]) * fp else q[0]
        }
        sF.snap(1f)
        flipP = 1f
        fxStart()
    }

    private fun fxStart() {
        if (fxJob?.isActive == true) return
        fxJob = scope.launch {
            var last = 0L
            while (true) {
                val t = androidx.compose.runtime.withFrameNanos { it }
                val dt = if (last == 0L) .016f else ((t - last) / 1_000_000_000f).coerceIn(.001f, .05f)
                last = t
                fxStep(dt)
                if (mv == null && flipP == 0f && landA <= 0f && gOn <= 0f && gPulse <= 0f && edgeV == 0f && edgeRaw == 0f) break
            }
        }
    }

    private fun fxStep(dt: Float) {
        val m = mv
        var th = sH.x
        var tx = sX.x
        var tw = sW.x
        var to = 0f
        var sig = -2
        val full = widthDp * dens
        if (m != null) {
            if (m.row) {
                // anggota kolase diseret keluar: garis tipis di celah tujuan
                val at = dropAt
                if (at >= 0 && mergeT == null) {
                    val top = if (at < blocks.size) bp0[blocks[at].id]?.get(0) else blocks.lastOrNull()?.let { l -> bp0[l.id]?.let { it[0] + it[1] } }
                    if (top != null) {
                        val ty = top - 2f * dens
                        if (sO.x < .03f) sY.snap(ty)
                        gy = sY.step(ty, dt, 520f, .9f)
                        th = 4f * dens; tx = 0f; tw = full; to = 1f; sig = at
                    }
                }
                gAdj = false
            } else {
                // bingkai sasaran = "lubang" yang benar-benar ditempati blok ini di tata letak pratinjau
                val i = idx(m.bid)
                val b = if (i >= 0) blocks[i] else null
                val q = bpos[m.bid]
                if (b != null && q != null && mergeT == null) {
                    val bx = mvBox(m, b, q, curAl(b))
                    val f = flipFrom[m.bid]
                    gy = if (flipP != 0f && f != null) q[0] + (f - q[0]) * flipP else q[0]
                    th = bx[2]; tx = bx[0]; tw = bx[1]; to = 1f
                    sig = pvKey.hashCode()
                    gAdj = bx[1] < full * .98f
                }
            }
        }
        if (gLine != (m?.row == true)) gLine = m?.row == true
        if (to > 0f && sO.x < .03f) { sH.snap(th); sX.snap(tx); sW.snap(tw) }
        if (sig != gKey) { gKey = sig }
        val o = sO.step(to, dt, 700f, 1f)
        if (to == 0f && o < .01f) { sO.snap(0f); gOn = 0f } else gOn = o.coerceIn(0f, 1f)
        gHt = sH.step(th, dt, 480f, .9f).coerceAtLeast(0f)
        gx = sX.step(tx, dt, 520f, .9f)
        gw = sW.step(tw, dt, 520f, .9f).coerceAtLeast(0f)
        gT += dt
        if (gPulse > 0f) gPulse = (gPulse - dt * 3f).coerceAtLeast(0f)
        if (flipP != 0f) {
            val f = sF.step(0f, dt, 300f, .9f)
            if (kotlin.math.abs(f) < .002f && kotlin.math.abs(sF.v) < .05f) { flipP = 0f; sF.snap(0f); flipFrom.clear(); flipId = "" }
            else flipP = f
        }
        if (landA > 0f) landA = (landA - dt * 1.5f).coerceAtLeast(0f)
        val ev = edgeV + (edgeRaw - edgeV) * (dt * 12f).coerceAtMost(1f)
        edgeV = if (kotlin.math.abs(ev) < .004f && edgeRaw == 0f) 0f else ev
    }

    /** Dipanggil tepat saat dilepas, SEBELUM daftar blok diubah: blok lain tidak bergerak (sudah di tempatnya); objek meluncur dari jari ke lubangnya. */
    private fun flipBegin(m: Mv, keep: Boolean) {
        sO.snap(0f); gOn = 0f
        if (mvInk) { flipFrom.clear(); flipId = ""; flipDx = 0f; flipP = 0f; sF.snap(0f); fxStart(); return }
        flipCapture()
        flipId = ""
        flipDx = 0f
        if (!m.row) {
            flipFrom[m.bid] = m.top + mvY
            flipDx = mvX + (if (keep) mvAdjX else 0f)
            flipId = m.bid
        }
    }

    private fun landOn(id: String) {
        landId = id
        landA = 1f
        fxStart()
    }

    fun mvBegin(bid: String, imgId: String, grab: Float = -1f) {
        val i = idx(bid)
        if (i < 0) return
        val b = blocks[i]
        val p = bpos[bid] ?: floatArrayOf(0f, 0f)
        commit()
        selImg = if (b.ty == "img") bid to maxOf(0, b.im.indexOfFirst { it.id == imgId }) else null
        focus = null; clearReq++
        val hh = if (b.im.size == 1 && sideOk(b)) {
            val m0 = b.im[0]
            widthDp * m0.w.coerceIn(.2f, 1f) / m0.a.coerceIn(.2f, 5f) * dens
        } else p[1]
        mv = Mv(bid, imgId, b.im.size > 1, p[0], hh, grab)
        mvX = 0f; mvY = 0f; dropAt = -1; mvDir = 0; mergeT = null
        mvAl = b.im.firstOrNull()?.al ?: b.alOf()
        mvInk = blocks.any { it.ik.isNotEmpty() }
        gLine = b.im.size > 1
        gKind = b.ty
        gKey = -2
        ob = blocks
        bp0.clear()
        for ((k, v) in bpos) bp0[k] = v.copyOf()
        mvLayTop = p[0]; mvAdjX = 0f; pvKey = ""
        mvX0 = if (b.im.size > 1) 0f else mvBox(mv!!, b, null, curAl(b))[0]
        sY.snap(p[0]); sH.snap(p[1]); sX.snap(0f); sW.snap(widthDp * dens); sO.snap(0f); sF.snap(0f)
        gOn = 0f; gPulse = 0f; gAdj = false; edgeRaw = 0f; edgeV = 0f
        flipP = 0f; flipId = ""; flipFrom.clear(); landId = ""; landA = 0f
        fxStart()
        buzz?.invoke()
        autoJob?.cancel()
        autoJob = scope.launch {
            while (mv != null) {
                val m = mv
                val sc = scroll
                edgeRaw = 0f
                if (m != null && sc != null && mvDir != 0 && viewH > 0f) {
                    val cy = mvRef(m) - sc.value
                    val z = viewH * .2f
                    val v = when {
                        mvDir < 0 && cy < z -> -(z - cy) / z
                        mvDir > 0 && cy > viewH - z -> (cy - (viewH - z)) / z
                        else -> 0f
                    }
                    edgeRaw = v.coerceIn(-1f, 1f)
                    if (v != 0f) {
                        val got = sc.scrollBy(v.coerceIn(-1f, 1f) * 16f * dens)
                        if (got != 0f) { mvY += got; mvRecalc() }
                    }
                }
                delay(16)
            }
        }
    }

    /**
     * Titik acuan seretan di ruang konten. Bila jari memegang blok di titik g (tab tabel), acuannya jari itu sendiri; kalau tidak, titik tengah blok
     * (dibatasi supaya blok yang sangat tinggi tidak punya acuan jauh di luar layar). Dulu tabel panjang memakai titik 30% layar di bawah jari,
     * sehingga seret ke atas tak pernah menyentuh zona gulir atas.
     */
    private fun mvRef(m: Mv): Float = m.top + (if (m.g >= 0f) m.g else minOf(m.h / 2f, viewH * .3f)) + mvY

    fun mvDrag(dx: Float, dy: Float) {
        val m = mv ?: return
        mvX += dx; mvY += dy
        if (dy > .5f) mvDir = 1 else if (dy < -.5f) mvDir = -1
        if (m.row) {
            if (mergeT == null && dropAt < 0) rowDragStep(m)   // di atas gambar lain / di luar baris: jangan tukar urutan di barisnya sendiri
            mergeCheck()
            rowOut(m)
        } else mvRecalc()
    }

    private fun rowDragStep(m: Mv) {
        val i = idx(m.bid)
        if (i < 0) return
        val b = blocks[i]
        val ix = b.im.indexOfFirst { it.id == m.imgId }
        if (ix < 0) return
        val n = b.im.size
        val sum = b.im.sumOf { it.a.toDouble() }.toFloat().coerceAtLeast(.1f)
        val rowW = widthDp * dens
        val u = rowW * (1f - (n - 1) * GAP)
        val gap = rowW * GAP
        if (mvX > 0f && ix + 1 < n) {
            val nw = u * b.im[ix + 1].a / sum
            if (mvX > nw * .6f + gap) { rowSwap(m.bid, ix, 1); mvX -= nw + gap; buzz?.invoke() }
        } else if (mvX < 0f && ix > 0) {
            val nw = u * b.im[ix - 1].a / sum
            if (-mvX > nw * .6f + gap) { rowSwap(m.bid, ix, -1); mvX += nw + gap; buzz?.invoke() }
        }
    }

    private fun mvRecalc() {
        val m = mv ?: return
        val sl = src()
        val i = sl.indexOfFirst { it.id == m.bid }
        if (i < 0) return
        mergeCheck()
        if (m.row) { rowOut(m); return }
        // blok yang sangat tinggi (tabel panjang): acuan = dekat bagian atasnya, bukan pusatnya yang jauh di luar layar
        val cy = mvRef(m)
        var gap = sl.size
        for ((j, b) in sl.withIndex()) {
            if (j == i) continue
            val q = bp0[b.id] ?: continue
            if (q[0] + minOf(q[1] / 2f, viewH * .3f) >= cy) { gap = j; break }
        }
        val nd = if (mergeT != null || gap == i || gap == i + 1) -1 else gap
        if (nd != dropAt) { dropAt = nd; if (nd >= 0) buzz?.invoke() }
        val b = sl[i]
        if (b.isBox || b.ty == "tbl") {
            val w = widthDp * dens
            val wp = if (b.ty == "tbl") (tblWidths(b, widthDp).sum() + 3f) * dens else w * b.bw.coerceIn(.2f, 1f)
            val base = when (b.alOf()) { "l" -> wp / 2f; "r" -> w - wp / 2f; else -> w / 2f }
            val cx = base + mvX
            // selebar catatan: tidak ada ruang geser ke samping, jadi hanya pindah urutan; letak dan lebar tidak berubah.
            // Yang lebih sempit: letak mengikuti tempat dilepas (sepertiga kiri / tengah / kanan), lebar tidak berubah.
            val al = if (wp >= w * .98f) b.alOf()
            else if (cx < w / 3f) "l" else if (cx > w * 2f / 3f) "r" else "c"
            if (al != mvAl) { mvAl = al; buzz?.invoke() }
        }
        if (b.im.size == 1) {
            val w = widthDp * dens
            val mi = b.im[0]
            val wp = w * mi.w.coerceIn(.2f, 1f)
            val base = when (mi.al) { "l" -> wp / 2f; "r" -> w - wp / 2f; else -> w / 2f }
            val cx = base + mvX
            // selebar catatan: hanya pindah urutan, letak tidak berubah; yang lebih sempit: letak mengikuti tempat dilepas
            val al = if (wp >= w * .98f) mi.al else if (cx < w / 3f) "l" else if (cx > w * 2f / 3f) "r" else "c"
            if (al != mvAl) { mvAl = al; buzz?.invoke() }
        }
        pvApply()
    }

    fun mvEnd(apply: Boolean) {
        val m = mv ?: return
        autoJob?.cancel()
        edgeRaw = 0f
        val to = dropAt
        val al = mvAl
        val mt = mergeT
        flipBegin(m, apply && mt == null)
        mv = null; mvX = 0f; mvY = 0f; dropAt = -1; mergeT = null
        if (m.row) {
            val i = idx(m.bid)
            if (!apply || i < 0) return
            val anchor = if (to in 0 until blocks.size) blocks[to].id else null
            if (mt != null) { landOn(mt.bid); mergeImg(m.bid, m.imgId, mt); return }   // dilepas di atas gambar lain: otomatis jadi kolase
            if (to >= 0) { extractImg(m.bid, m.imgId, anchor); return }   // kolase diseret keluar barisnya: jadi gambar sendiri
            val rb = blocks[i]
            releaseSide(rb.id)
            selImg = if (rb.ty == "img") rb.id to maxOf(0, rb.im.indexOfFirst { it.id == m.imgId }) else null
            norm(); afterStructural()
            return
        }
        val i = ob.indexOfFirst { it.id == m.bid }
        val keepPv = pvKey
        pvKey = ""
        if (i < 0) return
        if (!apply) { if (blocks != ob) blocks = ob; return }            // dibatalkan: kembali ke susunan asli
        if (mt != null) {                                                  // dilepas di atas gambar lain: otomatis jadi kolase
            if (blocks != ob) blocks = ob
            landOn(mt.bid); mergeImg(m.bid, m.imgId, mt)
            return
        }
        // pratinjau sudah terpasang dengan fungsi yang sama (mvBuild): hasil akhir identik, tidak ada yang berubah mendadak
        val l = mvBuild(ob, i, to, al)
        if (blocks != l) blocks = l
        val fb = l.first { it.id == m.bid }
        releaseSide(fb.id)
        landOn(fb.id)
        selImg = if (fb.ty == "img") fb.id to maxOf(0, fb.im.indexOfFirst { it.id == m.imgId }) else null
        norm(); afterStructural()
    }

    // ---------- gambar: kolase otomatis saat dijatuhkan di atas gambar lain ----------
    /** Kotak gambar ke-[ix] di blok [b] pada ruang konten (px). Dihitung dari model + posisi blok, jadi tidak ikut bergeser oleh gestur angkat. */
    private fun imgRect(b: Blk, ix: Int): Rect? {
        val top = (if (mv != null) bp0[b.id] else bpos[b.id])?.get(0) ?: return null
        val w = widthDp * dens
        if (w <= 0f || ix !in b.im.indices) return null
        val n = b.im.size
        if (n == 1) {
            val m = b.im[0]
            val wp = w * m.w.coerceIn(.2f, 1f)
            val x = when (m.al) { "l" -> 0f; "r" -> w - wp; else -> (w - wp) / 2f }
            return Rect(x, top, x + wp, top + wp / m.a.coerceIn(.2f, 5f))
        }
        val sum = b.im.sumOf { it.a.toDouble() }.toFloat().coerceAtLeast(.1f)
        val u = w * (1f - (n - 1) * GAP)
        val gap = w * GAP
        var x = 0f
        for (j in 0 until ix) x += u * b.im[j].a / sum + gap
        val cw = u * b.im[ix].a / sum
        return Rect(x, top, x + cw, top + u / sum)
    }

    /** Titik tengah gambar yang sedang diseret di ruang konten (px). */
    private fun mvCenter(m: Mv): Offset? {
        val b = src().firstOrNull { it.id == m.bid } ?: return null
        val ix = b.im.indexOfFirst { it.id == m.imgId }
        val r = imgRect(b, ix) ?: return null
        return Offset(r.center.x + mvX, r.center.y + mvY)
    }

    /** Cari gambar lain yang sedang ditimpa gambar seretan (titik tengahnya masuk kotak gambar itu). Baris yang sudah 3 gambar tidak bisa ditambah. */
    private fun mergeCheck() {
        val m = mv
        var found: MergeT? = null
        val c = if (m == null) null else mvCenter(m)
        if (m != null && c != null) {
            val pad = 10f * dens
            loop@ for (b in src()) {
                if (b.ty != "img" || b.id == m.bid || b.im.isEmpty() || b.im.size >= 3) continue
                for (j in b.im.indices) {
                    val r = imgRect(b, j) ?: continue
                    if (c.x >= r.left - pad && c.x <= r.right + pad && c.y >= r.top - pad && c.y <= r.bottom + pad) {
                        found = MergeT(b.id, b.im[j].id, c.x > r.center.x)
                        break@loop
                    }
                }
            }
        }
        val o = mergeT
        if (found?.bid != o?.bid || found?.imgId != o?.imgId || found?.after != o?.after) {
            mergeT = found
            if (found != null) buzz?.invoke()
        }
        if (found != null && dropAt != -1) dropAt = -1
    }

    /** Gambar kolase diseret keluar dari barisnya (tanpa sasaran kolase): tentukan celah antar blok tempat ia akan dilepas. */
    private fun rowOut(m: Mv) {
        val i = idx(m.bid)
        if (i < 0) return
        val b = blocks[i]
        val r = imgRect(b, b.im.indexOfFirst { it.id == m.imgId }) ?: return
        val c = mvCenter(m) ?: return
        val pad = 8f * dens
        var nd = -1
        if (mergeT == null && (c.y < r.top - pad || c.y > r.bottom + pad)) {
            var gap = blocks.size
            for ((j, x) in blocks.withIndex()) {
                val q = bp0[x.id] ?: continue
                if (q[0] + q[1] / 2f >= c.y) { gap = j; break }
            }
            nd = gap
        }
        if (nd != dropAt) { dropAt = nd; if (nd >= 0) buzz?.invoke() }
    }

    /** Keluarkan satu gambar dari kolase jadi blok gambar sendiri, ditaruh sebelum blok [anchor] (null = paling akhir). Gambar lain di kolase tetap. */
    private fun extractImg(bid: String, imgId: String, anchor: String?) {
        val i = idx(bid)
        if (i < 0) return
        val src = blocks[i]
        val n = src.im.size
        val mi = src.im.firstOrNull { it.id == imgId } ?: return
        if (n < 2) return
        val sum = src.im.sumOf { it.a.toDouble() }.toFloat().coerceAtLeast(.1f)
        fun share(x: Img) = (x.a / sum * (1f - (n - 1) * GAP)).coerceIn(.2f, 1f)   // lebar tampil lama, jadi tinggi gambar tidak berubah
        val rest = src.im.filter { it.id != imgId }
        val solo = Blk(ty = "img", im = listOf(mi.copy(w = share(mi), al = "l")))
        val l = blocks.toMutableList()
        l[i] = if (rest.size == 1) src.copy(im = listOf(rest[0].copy(w = share(rest[0]), al = "l"))) else src.copy(im = rest)
        val at = if (anchor == null) l.size else l.indexOfFirst { it.id == anchor }.let { if (it < 0) l.size else it }
        l.add(at, solo)
        blocks = l
        landOn(solo.id)
        selImg = solo.id to 0
        focus = null; clearReq++
        norm(); afterStructural()
        buzz?.invoke()
    }

    /** Samakan tinggi semua gambar dalam satu baris kolase (lebar = bobot rasio), dengan tinggi dibatasi 120..520 dp. */
    private fun fitRow(l: List<Img>): List<Img> {
        val n = l.size
        val sum = l.sumOf { it.a.toDouble() }.toFloat().coerceAtLeast(.1f)
        val u = widthDp * (1f - (n - 1) * GAP)
        val h = u / sum
        val h2 = h.coerceIn(120f, 520f)
        val k = if (h2 != h) h / h2 else 1f
        return l.map { it.copy(a = it.a * k, al = "c", w = 1f) }
    }

    /** Gambar [imgId] dari blok [srcBid] disatukan ke baris kolase gambar sasaran [t]. Sumbernya dilepas dari tempat lamanya. */
    private fun mergeImg(srcBid: String, imgId: String, t: MergeT) {
        val si = idx(srcBid)
        val ti = idx(t.bid)
        if (si < 0 || ti < 0 || si == ti) return
        val src = blocks[si]
        val tgt = blocks[ti]
        val mi = src.im.firstOrNull { it.id == imgId } ?: return
        if (tgt.im.size >= 3) { vm.show("A photo row holds up to 3 photos"); return }
        val tx = tgt.im.indexOfFirst { it.id == t.imgId }.coerceAtLeast(0)
        val ins = if (t.after) tx + 1 else tx
        val row = tgt.im.toMutableList().also { it.add(ins, mi) }
        val l = blocks.toMutableList()
        l[ti] = tgt.copy(im = fitRow(row))
        if (src.im.size > 1) {
            // sumber adalah kolase: hanya satu gambarnya yang pindah, sisanya tetap dengan ukuran tampil yang sama
            val n = src.im.size
            val sum = src.im.sumOf { it.a.toDouble() }.toFloat().coerceAtLeast(.1f)
            val rest = src.im.filter { it.id != imgId }
            l[si] = if (rest.size == 1) {
                val r0 = rest[0]
                src.copy(im = listOf(r0.copy(w = (r0.a / sum * (1f - (n - 1) * GAP)).coerceIn(.2f, 1f), al = "l")))
            } else src.copy(im = rest)
        } else {
            l.removeAt(si)
            if (si > 0 && si < l.size && isEmptyP(l[si - 1]) && isEmptyP(l[si])) l.removeAt(si)
        }
        blocks = l
        selImg = t.bid to ins
        focus = null; clearReq++
        norm(); afterStructural()
        buzz?.invoke()
    }

    // ---------- gambar: ganti foto (bingkai tetap) ----------
    /** Isi bingkai gambar terpilih dengan foto lain: ukuran bingkai, perataan, dan posisinya tidak berubah; pangkas diatur ulang. */
    /** false setelah komposisi editor ini dibuang (mis. Activity dibuat ulang); hasil galeri yang telat tiba tidak boleh dikirim ke editor mati. */
    @Volatile var alive = true

    /** Isi bingkai gambar [bid] / [ix] dengan foto baru (target dicatat Activity sebelum galeri dibuka, jadi tak bergantung pada seleksi). */
    fun replaceImgAt(bid: String, ix: Int, n: Img) {
        replT = bid to ix
        replaceImg(n)
    }

    fun replaceImg(n: Img) {
        val s = replT ?: selImg
        replT = null
        if (s == null) return
        val i = idx(s.first)
        if (i < 0 || s.second !in blocks[i].im.indices) return
        commit()
        imgSet(s.first, s.second) { it.copy(id = n.id, o = n.o, px = .5f, py = .5f, z = 1f) }
        selImg = s
        dirty(); commit()
    }

    // ---------- seleksi lintas blok ----------
    var selOn by mutableStateOf(false)
    var pa by mutableStateOf<Pt?>(null)        // titik jangkar
    var pf by mutableStateOf<Pt?>(null)        // titik lawan
    var selDrag by mutableStateOf(false)
    val lays = mutableStateMapOf<String, TextLayoutResult>()
    val kpos = mutableStateMapOf<String, Offset>()
    /** Kotak huruf sebuah kolom teks di ruang konten (px): hanya selebar baris yang benar-benar berisi huruf, bukan seluruh kolom. */
    fun glyphRect(key: String): Rect? {
        val lay = lays[key] ?: return null
        val kp = kpos[key] ?: return null
        var l = Float.MAX_VALUE
        var r = 0f
        for (i in 0 until lay.lineCount) { l = minOf(l, lay.getLineLeft(i)); r = maxOf(r, lay.getLineRight(i)) }
        if (l > r) { l = 0f; r = 0f }
        return Rect(kp.x + l, kp.y, kp.x + r, kp.y + lay.size.height)
    }
    val layLen = HashMap<String, Int>()       // panjang teks kolom edit saat layout di lays dibuat (deteksi layout basi)
    val lsh = HashMap<String, Int>()          // 1 bila layout berasal dari kolom edit (ada awalan Z di indeks 0)
    val ipos = HashMap<String, Rect>()        // kotak tiap gambar di ruang konten
    val folds = HashMap<String, Fold>()       // pemotongan baris buatan (teks mengelilingi gambar), per kolom teks
    var contentCoords: LayoutCoordinates? = null
    private var selBack: Pt? = null            // asal dari mode ketik: kembali ke sana saat selesai
    /** Seleksi yang dimulai dari mode ketik (bar alat tetap tampil, kursor kembali setelah selesai). */
    val selEdit: Boolean get() = selOn && selBack != null
    private var wordA: Pt? = null
    private var wordB: Pt? = null
    private var selWho = 0                     // 0 tekan-tahan, 1 pegangan jangkar, 2 pegangan lawan
    private var selTarget = Offset.Zero
    private var selFinger = 0f
    private var selJob: Job? = null
    private var ordFor: List<Blk>? = null
    private var ordMap: Map<String, Int> = emptyMap()
    private var ordList: List<String> = emptyList()

    private fun ordRefresh() {
        if (ordFor === blocks) return
        val l = ArrayList<String>()
        for (b in blocks) {
            when {
                b.ty == "tbl" -> b.tb.forEachIndexed { r, row -> row.indices.forEach { c -> l.add("${b.id}:$r:$c") } }
                b.isText -> l.add(b.id)
                b.ty == "img" && b.r.t.isNotEmpty() -> l.add(b.id)
            }
        }
        ordList = l
        ordMap = l.withIndex().associate { it.value to it.index }
        ordFor = blocks
    }

    fun selKeys(): List<String> { ordRefresh(); return ordList }

    private fun lessPt(a: Pt, b: Pt): Boolean {
        ordRefresh()
        val ia = ordMap[a.key] ?: return false
        val ib = ordMap[b.key] ?: return false
        return ia < ib || (ia == ib && a.off < b.off)
    }

    fun selSpan(): Pair<Pt, Pt>? {
        val a = pa ?: return null
        val f = pf ?: return null
        return if (lessPt(f, a)) f to a else a to f
    }

    /** Rentang terseleksi pada satu kolom teks, atau null. */
    fun selRangeOf(key: String): Pair<Int, Int>? {
        val (lo, hi) = selSpan() ?: return null
        val ik = ordMap[key] ?: return null
        val il = ordMap[lo.key] ?: return null
        val ih = ordMap[hi.key] ?: return null
        if (ik < il || ik > ih) return null
        val len = rtOf(key).t.length
        val s = if (ik == il) lo.off else 0
        val e = if (ik == ih) hi.off else len
        return if (e > s) s.coerceIn(0, len) to e.coerceIn(0, len) else null
    }

    /** Offset teks asli -> offset di layout yang tampil (memperhitungkan awalan Z dan pemotongan baris buatan). */
    fun toLay(key: String, off: Int): Int {
        val z = lsh[key] ?: 0
        val f = folds[key]
        return if (f == null) off + z else f.o2t(off + z, z)
    }

    fun fromLay(key: String, t: Int): Int {
        val z = lsh[key] ?: 0
        val f = folds[key]
        val o = if (f == null) t else f.t2o(t, z)
        return (o - z).coerceAtLeast(0)
    }

    fun ptRect(p: Pt): Rect? {
        val lay = lays[p.key] ?: return null
        val pos = kpos[p.key] ?: return null
        val o = toLay(p.key, p.off).coerceIn(0, lay.layoutInput.text.length)
        return lay.getCursorRect(o).translate(pos)
    }

    private fun nearest(p: Offset): Pair<String, Float>? {
        ordRefresh()
        var best: String? = null
        var bd = Float.MAX_VALUE
        for (k in ordList) {
            val lay = lays[k] ?: continue
            val pos = kpos[k] ?: continue
            val w = lay.size.width.toFloat()
            val h = lay.size.height.toFloat()
            val dy = if (p.y < pos.y) pos.y - p.y else if (p.y > pos.y + h) p.y - (pos.y + h) else 0f
            val dx = if (p.x < pos.x) pos.x - p.x else if (p.x > pos.x + w) p.x - (pos.x + w) else 0f
            val d = dy * 4f + dx
            if (d < bd) { bd = d; best = k }
        }
        return best?.let { it to bd }
    }

    fun hitPt(p: Offset): Pt? {
        val k = nearest(p)?.first ?: return null
        val lay = lays[k] ?: return null
        val pos = kpos[k] ?: return null
        val len = rtOf(k).t.length
        val local = Offset(p.x - pos.x, (p.y - pos.y).coerceIn(0f, maxOf(0f, lay.size.height - 1f)))
        return Pt(k, fromLay(k, lay.getOffsetForPosition(local)).coerceIn(0, len))
    }

    /** Masuk mode seleksi dari mode ketik: mulai dari seleksi/kata di kursor. */
    fun enterSelect() {
        if (selOn) { selAll(); return }
        val key = focus
        var a: Pt? = null
        var b: Pt? = null
        selBack = null
        if (key != null) {
            val v = tf[key]
            val rt = rtOf(key)
            if (v != null) {
                var s = (v.selection.min - 1).coerceIn(0, rt.t.length)
                var e = (v.selection.max - 1).coerceIn(0, rt.t.length)
                if (s == e && rt.t.isNotEmpty()) {
                    fun w(c: Char) = c.isLetterOrDigit() || c == '_'
                    var l = s
                    var r = s
                    while (l > 0 && w(rt.t[l - 1])) l--
                    while (r < rt.t.length && w(rt.t[r])) r++
                    if (r > l) { s = l; e = r } else { s = 0; e = rt.t.length }
                }
                if (e > s) { a = Pt(key, s); b = Pt(key, e) }
                selBack = Pt(key, e)
            }
        }
        commit()
        selOn = true
        pa = a; pf = b
        reading = true; selImg = null; selObj = null; pickQuote = false; miniBar = false; clearReq++
    }

    fun clearSel() {
        val back = selBack
        selOn = false; pa = null; pf = null; selBack = null; selDrag = false
        selJob?.cancel()
        if (back != null && tf.containsKey(back.key)) exitReading(back.key, back.off.coerceAtLeast(0))
    }

    /**
     * Tekan-tahan di teks: pilih satu kata, lalu geser untuk memperluas ke blok mana pun.
     * Berlaku di mode baca MAUPUN mode ketik; dari mode ketik, setelah selesai kursor kembali ke teks.
     */
    fun selLong(p: Offset, fingerY: Float): Boolean {
        val edit = !reading
        // tekan-tahan di atas gambar (mode baca MAUPUN ketik) bukan seleksi teks: biarkan gambar yang mengangkat dirinya
        if (ipos.values.any { it.contains(p) }) return false
        if (edit) {
            val nr = nearest(p) ?: return false
            if (nr.second > 32f * dens) return false                       // jauh dari teks (mis. judul / ruang kosong)
        }
        val h = hitPt(p) ?: return false
        val lay = lays[h.key] ?: return false
        val len = rtOf(h.key).t.length
        var a = h
        var b = h
        if (len > 0) {
            val lt = lay.layoutInput.text.length
            val w = lay.getWordBoundary(toLay(h.key, h.off).coerceIn(0, maxOf(0, lt - 1)))
            a = Pt(h.key, fromLay(h.key, w.start).coerceIn(0, len)); b = Pt(h.key, fromLay(h.key, w.end).coerceIn(0, len))
        }
        if (edit) {
            commit()
            selBack = Pt(b.key, b.off)
            selImg = null; selObj = null; pickQuote = false; miniBar = false
            // tetap di mode ketik: keyboard terbuka, ketikan langsung mengganti seleksi.
            // Seleksi field = kata itu (tidak kosong) supaya tombol Salin / Pilih semua / lompat Gboard aktif.
            if (a.key == b.key && b.off > a.off) req = Req(b.key, b.off, ++rq, TextRange(a.off + 1, b.off + 1))
            else focusAt(b.key, b.off)
        }
        wordA = a; wordB = b; selWho = 0
        pa = a; pf = b
        selOn = true
        selTarget = p; selFinger = fingerY; selDrag = true
        selAuto()
        buzz?.invoke()
        return true
    }

    /** Jari bergerak setelah tekan-tahan (posisi absolut di ruang konten). */
    fun selMove(p: Offset, fingerY: Float) {
        selTarget = p; selFinger = fingerY
        selApply(p)
    }

    /** Pegangan digeser sejauh d (relatif). */
    fun selNudge(d: Offset) {
        selTarget += d
        selFinger = selTarget.y - (scroll?.value ?: 0).toFloat() + 36f * dens
        selApply(selTarget)
    }

    fun selHandleStart(which: Int): Boolean {
        val pt = (if (which == 1) pa else pf) ?: return false
        val r = ptRect(pt) ?: return false
        selWho = which
        selTarget = Offset(r.left, r.center.y)
        selFinger = selTarget.y - (scroll?.value ?: 0).toFloat() + 36f * dens
        selDrag = true
        selAuto()
        return true
    }

    fun selEnd() {
        selDrag = false
        selJob?.cancel()
    }

    private fun selApply(p: Offset) {
        val h = hitPt(p) ?: return
        when (selWho) {
            0 -> {
                val wa = wordA ?: return
                val wb = wordB ?: return
                if (lessPt(h, wa)) { pa = wb; pf = h }
                else if (lessPt(wb, h)) { pa = wa; pf = h }
                else { pa = wa; pf = wb }
            }
            1 -> pa = h
            else -> pf = h
        }
    }

    private fun selAuto() {
        selJob?.cancel()
        selJob = scope.launch {
            while (selDrag) {
                val sc = scroll
                if (sc != null && viewH > 0f) {
                    val edge = 80f * dens
                    val v = when {
                        selFinger < edge -> -(edge - selFinger) / edge
                        selFinger > viewH - edge -> (selFinger - (viewH - edge)) / edge
                        else -> 0f
                    }
                    if (v != 0f) {
                        val got = sc.scrollBy(v.coerceIn(-1f, 1f) * 18f * dens)
                        if (got != 0f) { selTarget = Offset(selTarget.x, selTarget.y + got); selApply(selTarget) }
                    }
                }
                delay(16)
            }
        }
    }

    // ---------- keyboard: pilih semua, panah, shift+panah (panel "pengeditan teks" Gboard / keyboard fisik) ----------
    fun selectAllKey(key: String) {
        if (selOn) { selAll(); return }
        val v = tf[key]
        val len = rtOf(key).t.length
        val caret = ((v?.selection?.end ?: 1) - 1).coerceIn(0, len)
        commit()
        selBack = Pt(key, caret)
        selWho = 0; selDrag = false
        selImg = null; selObj = null; pickQuote = false; miniBar = false
        selAll()
        // seleksi field dibiarkan tidak kosong: Gboard menonaktifkan (abu-abu) tombol Salin/Potong kalau menganggap tak ada seleksi
        if (v != null) tf[key] = v.copy(selection = if (len > 0) TextRange(1, len + 1) else TextRange(0, 1))
        caretMoved(key)
    }

    private fun stepPt(p: Pt, dir: Int): Pt? {
        ordRefresh()
        val i = ordMap[p.key] ?: return null
        val len = rtOf(p.key).t.length
        if (dir == 0) return if (p.off > 0) Pt(p.key, p.off - 1) else ordList.getOrNull(i - 1)?.let { Pt(it, rtOf(it).t.length) }
        if (dir == 1) return if (p.off < len) Pt(p.key, p.off + 1) else ordList.getOrNull(i + 1)?.let { Pt(it, 0) }
        val up = dir == 2
        val lay = lays[p.key]
        val pos = kpos[p.key]
        val r = ptRect(p)
        if (lay != null && pos != null && r != null) {
            val ln = lay.getLineForOffset(toLay(p.key, p.off).coerceIn(0, lay.layoutInput.text.length))
            if (if (up) ln > 0 else ln < lay.lineCount - 1) {
                val local = Offset(r.left - pos.x, if (up) r.top - pos.y - 2f else r.bottom - pos.y + 2f)
                return Pt(p.key, fromLay(p.key, lay.getOffsetForPosition(local)).coerceIn(0, len))
            }
            val nk = ordList.getOrNull(if (up) i - 1 else i + 1) ?: return Pt(p.key, if (up) 0 else len)
            val l2 = lays[nk]
            val p2 = kpos[nk]
            val n2 = rtOf(nk).t.length
            if (l2 == null || p2 == null) return Pt(nk, if (up) n2 else 0)
            val local = Offset(r.left - p2.x, if (up) l2.size.height - 1f else 0f)
            return Pt(nk, fromLay(nk, l2.getOffsetForPosition(local)).coerceIn(0, n2))
        }
        val nk = ordList.getOrNull(if (up) i - 1 else i + 1) ?: return Pt(p.key, if (up) 0 else len)
        return Pt(nk, if (up) rtOf(nk).t.length else 0)
    }

    private fun extendJump(key: String, anchor: Int, oldCaret: Int, to: Pt) {
        val len = rtOf(key).t.length
        if (selOn) pf = to
        else {
            commit()
            selBack = Pt(key, oldCaret.coerceIn(0, len)); selWho = 0; selDrag = false
            selImg = null; selObj = null; pickQuote = false; miniBar = false
            pa = Pt(key, anchor.coerceIn(0, len)); pf = to; selOn = true
        }
        tf[key]?.let { tf[key] = it.copy(selection = if (len > 0) TextRange(1, len + 1) else TextRange(0, 1)) }
        caretMoved(key)
        revealPf()
    }

    /** Gulir supaya titik lawan seleksi (pf) terlihat. */
    private fun revealPf() {
        scope.launch {
            delay(32)
            val sc = scroll ?: return@launch
            val r = pf?.let { ptRect(it) } ?: return@launch
            if (viewH <= 0f) return@launch
            val m = 56f * dens
            val top = sc.value.toFloat()
            val d = when {
                r.bottom > top + viewH - m -> r.bottom - (top + viewH - m)
                r.top < top + m -> r.top - (top + m)
                else -> 0f
            }
            if (d != 0f) sc.scrollBy(d)
        }
    }

    /** Home/End (ctrl = awal/akhir seluruh teks). Saat seleksi aktif selalu lompat ke awal/akhir seluruh teks. */
    fun jumpKey(key: String, toEnd: Boolean, ctrl: Boolean, shift: Boolean): Boolean {
        if (!selOn && !ctrl) return false
        ordRefresh()
        val k = (if (toEnd) ordList.lastOrNull() else ordList.firstOrNull()) ?: return false
        val to = Pt(k, if (toEnd) rtOf(k).t.length else 0)
        if (selOn) {
            if (shift) { pf = to; revealPf() } else { dropSel(); focusAt(to.key, to.off) }
            return true
        }
        val v = tf[key] ?: return false
        val len = rtOf(key).t.length
        val caret = (v.selection.end - 1).coerceIn(0, len)
        val anchor = (v.selection.start - 1).coerceIn(0, len)
        if (shift) {
            commit()
            selBack = Pt(key, caret); selWho = 0; selDrag = false
            selImg = null; selObj = null; pickQuote = false; miniBar = false
            pa = Pt(key, anchor); pf = to; selOn = true
            revealPf()
        } else focusAt(to.key, to.off)
        return true
    }

    /** dir: 0 kiri, 1 kanan, 2 atas, 3 bawah. true = tombol sudah ditangani (field tidak perlu memprosesnya). */
    fun navKey(key: String, dir: Int, shift: Boolean): Boolean {
        if (selOn) {
            val (lo, hi) = selSpan() ?: return false
            if (shift) {
                val np = stepPt(pf ?: return false, dir)
                if (np != null) { pf = np; revealPf() }
                return true
            }
            val t = if (dir == 0 || dir == 2) lo else hi
            dropSel()
            focusAt(t.key, t.off)
            return true
        }
        val v = tf[key] ?: return false
        val len = rtOf(key).t.length
        if (!shift && !v.selection.collapsed) return false
        val caret = (v.selection.end - 1).coerceIn(0, len)
        val anchor = (v.selection.start - 1).coerceIn(0, len)
        val edge = when (dir) {
            0 -> caret <= 0
            1 -> caret >= len
            else -> {
                val lay = lays[key] ?: return false
                val ln = lay.getLineForOffset(toLay(key, caret).coerceIn(0, lay.layoutInput.text.length))
                if (dir == 2) ln == 0 else ln == lay.lineCount - 1
            }
        }
        if (!edge) return false
        val from = Pt(key, caret)
        val to = stepPt(from, dir) ?: return true
        if (to == from) return true
        if (shift) {
            commit()
            selBack = from; selWho = 0; selDrag = false
            selImg = null; selObj = null; pickQuote = false; miniBar = false
            pa = Pt(key, anchor); pf = to; selOn = true
            revealPf()
        } else focusAt(to.key, to.off)
        return true
    }

    fun selAll() {
        val ks = selKeys()
        if (ks.isEmpty()) return
        pa = Pt(ks.first(), 0)
        pf = Pt(ks.last(), rtOf(ks.last()).t.length)
        selOn = true
    }

    private fun selSegs(): List<Triple<String, Int, Int>> {
        val (lo, hi) = selSpan() ?: return emptyList()
        ordRefresh()
        val il = ordMap[lo.key] ?: return emptyList()
        val ih = ordMap[hi.key] ?: return emptyList()
        val out = ArrayList<Triple<String, Int, Int>>()
        for (j in il..ih) {
            val k = ordList[j]
            val len = rtOf(k).t.length
            val s = if (j == il) lo.off.coerceIn(0, len) else 0
            val e = if (j == ih) hi.off.coerceIn(0, len) else len
            out.add(Triple(k, s, maxOf(s, e)))
        }
        return out
    }

    fun selText(): String {
        val sb = StringBuilder()
        var prev: String? = null
        for ((k, s, e) in selSegs()) {
            if (prev != null) {
                val sameRow = isCellKey(k) && isCellKey(prev) && keyBlock(k) == keyBlock(prev) && cellRC(k).first == cellRC(prev).first
                sb.append(if (sameRow) '\t' else '\n')
            }
            sb.append(rtOf(k).t.substring(s, e))
            prev = k
        }
        return sb.toString()
    }

    /** Markdown yang terakhir disalin dari seleksi (dipakai supaya tempel di catatan ini memulihkan format aslinya). */
    var lastMd: String = ""
    private var lastMdBlocky = false

    /** Isi seleksi sebagai Markdown (tebal, miring, judul, butir, kutipan, kode, tabel ikut terbawa). */
    fun selMd(): String {
        val segs = selSegs()
        if (segs.isEmpty()) return ""
        val od = ords(blocks)
        val out = ArrayList<Blk>()
        var i = 0
        while (i < segs.size) {
            val (k, s, e) = segs[i]
            if (isCellKey(k)) {
                val bid = keyBlock(k)
                var j = i
                while (j < segs.size && isCellKey(segs[j].first) && keyBlock(segs[j].first) == bid) j++
                val grp = segs.subList(i, j)
                val tb = blocks.firstOrNull { it.id == bid }
                val total = tb?.tb?.sumOf { it.size } ?: -1
                val full = tb != null && grp.size == total && grp.all { (ck, cs, ce) -> cs == 0 && ce >= rtOf(ck).t.length }
                if (full && tb != null) out.add(tb)
                else {
                    val rows = ArrayList<String>()
                    var prev: String? = null
                    val cur = StringBuilder()
                    for ((ck, cs, ce) in grp) {
                        if (prev != null) {
                            if (cellRC(ck).first == cellRC(prev).first) cur.append('\t') else { rows.add(cur.toString()); cur.setLength(0) }
                        }
                        cur.append(rtOf(ck).t.substring(cs, ce))
                        prev = ck
                    }
                    rows.add(cur.toString())
                    rows.forEach { out.add(Blk(ty = "p", r = Rt(it))) }
                }
                i = j
                continue
            }
            val b = blockOfKey(k)
            if (b != null) {
                val rt = rtOf(k)
                val part = rt.slice(s, e)
                val whole = s <= 0 && e >= rt.t.length
                val keep = b.isText && (segs.size > 1 || whole)
                out.add(
                    if (keep) b.copy(r = part, ik = emptyList(), st = if (b.ty == "li" && (b.lt == "o" || b.lt == "a") && out.isEmpty()) (od[b.id] ?: 1) else b.st)
                    else Blk(ty = "p", r = part)
                )
            }
            i++
        }
        val md = toMd(out).trimEnd('\n')
        if (md.isBlank()) return selText()
        lastMd = md
        lastMdBlocky = out.size > 1 || out.firstOrNull()?.ty != "p"
        return md
    }

    private fun fmtTargets(kind: Int) = selSegs().filter { (k, s, e) ->
        e > s && blockOfKey(k)?.let { it.ty != "code" && !(it.ty == "h" && kind == K.B) } == true
    }

    fun selAllFmt(kind: Int): Boolean {
        val t = fmtTargets(kind)
        return t.isNotEmpty() && t.all { (k, s, e) -> rtOf(k).sp.any { it.k == kind && it.s <= s && it.e >= e } }
    }

    fun selFmt(kind: Int) {
        val t = fmtTargets(kind)
        if (t.isEmpty()) return
        val on = !selAllFmt(kind)
        commit()
        for ((k, s, e) in t) putRt(k, rtOf(k).setFmt(s, e, kind, on))
        dirty(); commit()
    }

    /** Hapus isi seleksi (lintas blok digabung seperti editor biasa), lalu kembali mengetik di titik awalnya. */
    fun selDelete() {
        val (lo, hi) = selSpan() ?: return
        commit()
        if (lo.key == hi.key) {
            val rt = rtOf(lo.key)
            putRt(lo.key, rt.slice(0, lo.off) + rt.slice(hi.off, rt.t.length))
        } else if (isCellKey(lo.key) || isCellKey(hi.key)) {
            for ((k, s, e) in selSegs()) if (e > s) {
                val rt = rtOf(k)
                putRt(k, rt.slice(0, s) + rt.slice(e, rt.t.length))
            }
        } else {
            val i0 = idx(lo.key)
            val i1 = idx(hi.key)
            if (i0 < 0 || i1 < 0) return
            val a = blocks[i0]
            val z = blocks[i1]
            val merged = a.r.slice(0, lo.off) + z.r.slice(hi.off, z.r.t.length)
            blocks = blocks.toMutableList().also { l ->
                l[i0] = a.copy(r = merged)
                for (j in i1 downTo i0 + 1) l.removeAt(j)
            }
        }
        reseedAll()
        afterStructural()
        selOn = false; pa = null; pf = null; selBack = null; selDrag = false
        exitReading(lo.key, lo.off)
    }

    /** Tempel teks di tempat seleksi (menggantikannya), lalu lanjut mengetik setelah teks yang ditempel. */
    fun selPaste(s: String) {
        if (s.isEmpty()) return
        val (lo, _) = selSpan() ?: return
        selDelete()
        // teks yang ditempel = Markdown yang baru disalin dari catatan: pulihkan formatnya (blok maupun sebaris)
        val mine = lastMd.isNotEmpty() && s.trimEnd('\n') == lastMd
        if (mine && lastMdBlocky && !isCellKey(lo.key) && blockOfKey(lo.key)?.isMulti != true) paste(lo.key, lo.off, s, true)
        else typeAt(lo.key, lo.off, s, mine)
    }

    /** Sisipkan teks s di posisi off (dipakai tempel dan mengetik di atas seleksi), lalu kursor di akhir teks itu. */
    private fun typeAt(key: String, off: Int, s: String, rich: Boolean = false) {
        val b = blockOfKey(key) ?: return
        if (s.indexOf('\n') >= 0 && !b.isMulti && !isCellKey(key)) {
            if (s == "\n") enter(key, off) else paste(key, off, s, rich)
            return
        }
        val clean = if (isCellKey(key)) s.replace('\n', ' ') else s
        val ins = if (rich && !b.isMulti && !isCellKey(key)) inl(clean) else Rt(clean)
        val rt = rtOf(key)
        val nr = rt.slice(0, off) + ins + rt.slice(off, rt.t.length)
        putRt(key, nr)
        tf[key] = TextFieldValue(Z + nr.t, TextRange(off + ins.t.length + 1))
        dirty(); commit()
        focusAt(key, off + ins.t.length)
    }

    /** Ada seleksi di mode ketik lalu pengguna mengetik / menekan hapus: seleksi diganti ketikan itu, seperti editor biasa. */
    private fun typeOverSel(key: String, v: TextFieldValue): Boolean {
        if (!v.text.startsWith(Z)) { dropSel(); return false }
        val (lo, _) = selSpan() ?: return false
        val oldT = rtOf(key).t
        val nt = v.text.substring(1)
        val df = diff(oldT, nt)
        val ins = nt.substring(df.p, df.p + df.ins)
        selDelete()
        if (ins.isNotEmpty()) typeAt(lo.key, lo.off, ins)
        return true
    }

    /** Lepas seleksi tanpa mengubah fokus / keyboard. */
    fun dropSel() {
        selOn = false; pa = null; pf = null; selBack = null; selDrag = false
        selJob?.cancel()
    }

    /** Ketuk dua kali di mode ketik: pilih kata di situ. Keyboard tetap terbuka. */
    fun dblWord(key: String, off: Int) {
        val t = rtOf(key).t
        if (t.isEmpty()) return
        fun w(c: Char) = c.isLetterOrDigit() || c == '_'
        val i = off.coerceIn(0, t.length)
        if (!((i < t.length && w(t[i])) || (i > 0 && w(t[i - 1])))) return
        var l = i
        var r = i
        while (l > 0 && w(t[l - 1])) l--
        while (r < t.length && w(t[r])) r++
        commit()
        val a = Pt(key, l)
        val b = Pt(key, r)
        selBack = b; wordA = a; wordB = b; selWho = 0
        pa = a; pf = b; selOn = true; selDrag = false
        selImg = null; selObj = null; pickQuote = false; miniBar = false
        // seleksi field ikut tidak kosong supaya Salin / Pilih semua / lompat Gboard aktif
        tf[key]?.let { tf[key] = it.copy(selection = TextRange(l + 1, r + 1)) }
        caretMoved(key)
        buzz?.invoke()
    }

    // ---------- struktur ----------
    private fun norm() {
        val out = ArrayList<Blk>()
        for (b in blocks) {
            // gambar / kartu tautan boleh di baris paling atas, tanpa paragraf kosong di atasnya
            val atTop = out.isEmpty() && (b.ty == "img" || b.ty == "lnk")
            if (b.isObj && !atTop && (out.isEmpty() || out.last().isObj)) out.add(Blk())
            out.add(b)
        }
        // paragraf kosong lama di atas gambar / kartu tautan paling atas ikut dibuang
        if (out.size > 1 && (out[1].ty == "img" || out[1].ty == "lnk")) {
            val f0 = out[0]
            if (f0.ty == "p" && f0.r.t.isEmpty() && f0.ik.isEmpty() && f0.il == 0 && f0.ir == 0 && f0.fi == 0 && f0.ta.isEmpty()) {
                tf.remove(f0.id)
                out.removeAt(0)
            }
        }
        if (out.isEmpty() || out.last().isObj || out.last().isMulti) out.add(Blk())
        if (out != blocks) {
            blocks = out
            out.forEach { if ((it.isText || it.ty == "img") && !tf.containsKey(it.id)) seed(it) }
        }
    }

    private fun afterStructural() {
        norm(); dirty(); commit()
    }

    // ---------- riwayat ----------
    fun commit() {
        cJob?.cancel()
        typed = 0
        if (!ink.noRescue) blocks = inkRescue(hist[hi].bl, blocks, bpos, dens)   // coretan dari blok yang hilang dipindah, bukan dibuang
        if (hist[hi].bl == blocks) return
        while (hist.size > hi + 1) hist.removeAt(hist.size - 1)
        hist.add(Snap(blocks, focus, focus?.let { tf[it]?.selection } ?: TextRange.Zero))
        if (hist.size > 150) hist.removeAt(0)
        hi = hist.size - 1
        tick++
    }

    val canUndo: Boolean get() { tick; return hi > 0 || hist[hi].bl != blocks }
    val canRedo: Boolean get() { tick; return hi < hist.size - 1 }

/**
     * Coretan lama (atau yang belum berjangkar) dipasangi jangkar huruf berdasarkan posisinya sekarang di layar, jadi tidak ada lompatan.
     * Diam-diam: tidak membuat langkah riwayat dan tidak menandai catatan berubah. Hanya jalan kalau tidak ada ketikan yang belum di-commit.
     */
    fun inkUpgrade() {
        if (blocks.none { b -> b.ik.any { !it.anc && !it.img } }) return
        if (hist[hi].bl !== blocks && hist[hi].bl != blocks) return
        val W = (contentCoords?.size?.width ?: 0).toFloat()
        if (W <= 0f) return
        val adds = HashMap<String, ArrayList<Ink>>()
        val drop = HashSet<String>()
        for (b in blocks) {
            if (b.ik.none { !it.anc && !it.img }) continue
            val top = bpos[b.id]?.get(0) ?: continue
            for (s in b.ik) {
                if (s.anc || s.img || s.p.isEmpty()) continue
                val raw = s.copy(an = emptyList(), ah = 0f, av = 0)
                val a = inkPts(s, b.id, top, W, dens)          // posisi sekarang (goresan berskala ikut ukuran huruf), bukan p mentah
                val ps = inkBuild(raw, a, a.size / 3, W)
                if (ps.none { it.second.anc }) continue          // tidak bisa dijangkarkan: biarkan seperti semula
                drop.add(s.id)
                for ((bid, st) in ps) adds.getOrPut(bid) { ArrayList() }.add(st)
            }
        }
        if (drop.isEmpty()) return
        val nb = blocks.map { b ->
            val keep = if (b.ik.any { it.id in drop }) b.ik.filter { it.id !in drop } else b.ik
            val add = adds[b.id]
            if (add == null && keep === b.ik) b else b.copy(ik = keep + (add ?: emptyList()))
        }
        blocks = nb
        hist[hi] = Snap(nb, hist[hi].key, hist[hi].sel)
        vm.syncInk(noteId, inkSyncP(nb))   // simpan jangkarnya supaya ekspor PDF catatan lama juga akurat
    }

    fun undo() {
        if (reading) return
        commit()
        if (hi <= 0) return
        hi--; load(hist[hi])
    }

    fun redo() {
        if (reading) return
        commit()
        if (hi >= hist.size - 1) return
        hi++; load(hist[hi])
    }

    private fun load(s: Snap) {
        blocks = s.bl
        reseedAll()
        selImg = null; selObj = null
        val k = s.key
        if (k != null && tf.containsKey(k)) focusAt(k, (s.sel.start - 1).coerceAtLeast(0))
        tick++
        dirty()
    }

    // ---------- simpan ----------
    fun dirty() {
        chg = true
        sJob?.cancel()
        sJob = scope.launch { delay(450); flush() }
    }

    fun titleChanged(t: String) {
        title = t.replace("\n", " ")
        dirty()
    }

    fun flush() {
        sJob?.cancel()
        if (!chg) return
        chg = false
        commit()
        vm.saveNote(noteId, title, inkSyncP(blocks))
    }

    /** Ganti blok transkrip lama dengan yang baru (atau sisipkan di akhir isi catatan). */
    fun putTranscript(old: List<String>, nw: List<Blk>) {
        commit()
        if (focus != null && keyBlock(focus!!) in old) focus = null
        blocks = putBlocks(blocks, old, nw)
        old.forEach { tf.remove(it) }
        nw.forEach { seed(it) }
        norm()
        afterStructural()
    }

    /** Rekaman suara sedang berjalan untuk catatan ini. */
    val recActive: Boolean get() = vm.rec.noteId == noteId

    fun startVoice() {
        flush()
        vm.startRec(noteId)
    }

    fun leave() {
        flush()
        if (vm.rec.noteId == noteId) vm.stopRec()   // keluar dari catatan = menyelesaikan rekaman
        vm.player.stop()                             // lepaskan pemutar
        vm.dropIfEmpty(noteId, title, blocks)
    }

    // ---------- cari dalam catatan ----------
    var findOn by mutableStateOf(false)
    var findQ by mutableStateOf("")
    var findI by mutableStateOf(0)
    private var hitsFor: List<Blk>? = null
    private var hitsT = ""
    private var hitsQ = ""
    private var hitsC: List<FHit> = emptyList()

    /** Semua kecocokan (judul dulu, lalu isi menurut urutan dokumen). Membaca state, jadi pemanggil ikut berlangganan. */
    fun hitList(): List<FHit> {
        val q = findQ
        val bl = blocks
        val tt = title
        if (!findOn || q.isBlank()) return emptyList()
        if (hitsFor === bl && hitsQ == q && hitsT == tt) return hitsC
        val out = ArrayList<FHit>()
        fun scan(key: String, t: String) {
            var i = t.indexOf(q, 0, ignoreCase = true)
            while (i >= 0) {
                out.add(FHit(key, i, i + q.length))
                i = t.indexOf(q, i + q.length, ignoreCase = true)
            }
        }
        scan("@t", tt)
        for (k in selKeys()) scan(k, rtOf(k).t)
        hitsFor = bl; hitsQ = q; hitsT = tt; hitsC = out
        return out
    }

    fun hitsOf(key: String): List<FHit> = hitList().filter { it.key == key }

    fun curHit(): FHit? {
        val l = hitList()
        return if (l.isEmpty()) null else l[findI.coerceIn(0, l.size - 1)]
    }

    fun findStep(d: Int) {
        val n = hitList().size
        if (n == 0) return
        findI = ((findI.coerceIn(0, n - 1) + d) % n + n) % n
    }

    fun openFind() {
        if (selOn) clearSel()
        selImg = null; selObj = null; pickQuote = false; miniBar = false
        findQ = ""; findI = 0
        findOn = true
    }

    /** Teks pencarian sengaja tidak dihapus: bilah cari masih terlihat selama animasi keluar. Direset saat openFind(). */
    fun closeFind() {
        findOn = false
    }

    fun enterReading() {
        commit()
        if (ink.on) ink.toggle(this); reading = true; selImg = null; selObj = null; pickQuote = false; miniBar = false; sheet = 0; sub = 0; sheetPage = 0; clearReq++
    }

    /** Ketuk di mode baca: tabel / kode / kutipan hanya terpilih (mode atur, tanpa keyboard); teks biasa langsung diedit. */
    fun tapFromReading(key: String, off: Int) {
        val bid = keyBlock(key)
        val ty = blocks.firstOrNull { it.id == bid }?.ty
        if (ty == "q" || ty == "code" || ty == "tbl") { exitReading(); selectObj(bid) } else exitReading(key, off)
    }

    fun exitReading(key: String? = null, off: Int = 0) {
        selOn = false; pa = null; pf = null; selBack = null; selDrag = false
        reading = false
        if (key != null) focusAt(key, off)
    }
}

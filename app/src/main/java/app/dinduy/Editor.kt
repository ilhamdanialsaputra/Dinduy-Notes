package app.dinduy

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
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
const val SIDE_GAP = 14f
const val SIDE_MIN = 110f
const val SIDE_W = 0.5f

/**
 * Pemotongan baris buatan untuk satu paragraf yang melewati dasar gambar mengambang:
 * [cuts] = offset awal tiap baris yang disisipi pemisah baris visual; [padLen] = jumlah spasi-em penggeser
 * (gambar di kiri) di awal tiap baris sempit; [padCnt] = berapa pemotongan pertama yang diikuti penggeser;
 * [ls] = letter-spacing (sp) agar lebar penggeser pas selebar gambar.
 * Hanya tampilan: teks aslinya tidak berubah.
 */
class Fold(val cuts: IntArray, val padLen: Int, val padCnt: Int, val ls: Float) {
    private val sp = if (padLen > 0) padLen else 0
    private fun cum(m: Int) = m + padLen * minOf(m, padCnt)
    val total: Int = sp + cum(cuts.size)

    /** Offset asli (z = jumlah karakter awalan tak terlihat) -> offset tampilan. */
    fun o2t(o: Int, z: Int): Int {
        if (o < z) return o
        val r = o - z
        var c = 0
        while (c < cuts.size && cuts[c] <= r) c++
        return z + r + sp + cum(c)
    }

    /** Offset tampilan -> offset asli. */
    fun t2o(t: Int, z: Int): Int {
        if (t < z) return t
        val tt = t - z - sp
        if (tt <= 0) return z
        var passed = 0
        for (k in cuts.indices) {
            val start = cuts[k] + cum(k)
            if (tt <= start) break
            val len = 1 + (if (k < padCnt) padLen else 0)
            if (tt < start + len) return z + cuts[k]
            passed = k + 1
        }
        return z + maxOf(0, tt - cum(passed))
    }
}

/** Titik di dalam teks (kunci kolom + offset) untuk seleksi lintas blok. */
data class Pt(val key: String, val off: Int)

/** Gambar yang sedang diangkat (tekan-tahan lalu geser). */
class Mv(val bid: String, val imgId: String, val row: Boolean, val top: Float, val h: Float)

class Req(val key: String, val pos: Int, val n: Int, val sel: TextRange? = null)

/** Satu kecocokan pada mode "cari dalam catatan". key "@t" = judul, selain itu kunci kolom teks. */
data class FHit(val key: String, val s: Int, val e: Int)
class Snap(val bl: List<Blk>, val key: String?, val sel: TextRange)

fun keyBlock(k: String) = k.substringBefore(':')
fun isCellKey(k: String) = k.indexOf(':') >= 0
fun cellRC(k: String): Pair<Int, Int> {
    val p = k.split(':')
    return (p.getOrNull(1)?.toIntOrNull() ?: 0) to (p.getOrNull(2)?.toIntOrNull() ?: 0)
}

class Ed(private val vm: AppVM, val noteId: String, private val scope: CoroutineScope) {
    var title by mutableStateOf("")
    var blocks by mutableStateOf<List<Blk>>(listOf(Blk()))
    val tf = mutableStateMapOf<String, TextFieldValue>()
    var focus by mutableStateOf<String?>(null)
    var typing by mutableStateOf(setOf<Int>())
    var reading by mutableStateOf(false)
    var pickQuote by mutableStateOf(false)
    var selImg by mutableStateOf<Pair<String, Int>?>(null)
    var req by mutableStateOf<Req?>(null)
    var titleFocus by mutableStateOf(false)
    var clearReq by mutableStateOf(0)
    var titleReq by mutableStateOf(0)
    var tick by mutableStateOf(0)
    var miniBar by mutableStateOf(false)
    var widthDp by mutableStateOf(340f)
    var barH by mutableStateOf(0f)        // tinggi kapsul alat (px) yang melayang di atas keyboard
    var barOn by mutableStateOf(false)    // kapsul alat sedang tampil
    val frs = HashMap<String, FocusRequester>()   // FocusRequester tiap kolom teks, supaya fokus bisa dipindah sebelum kolom lama dihapus

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
    fun blockOfKey(k: String): Blk? = blocks.firstOrNull { it.id == keyBlock(k) }

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
            else b.copy(r = rt)
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
        selImg = null
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
                if (insStr == "\n") enter(key, df.p) else paste(key, df.p, insStr)
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
            var m = Regex("^([-*•+])\\s").find(tx)
            if (m != null && c == m.value.length) { nb = Blk(id = b.id, ty = "li", lt = "b"); len = m.value.length }
            if (nb == null) {
                m = Regex("^\\d{1,3}[.)]\\s").find(tx)
                if (m != null && c == m.value.length) { nb = Blk(id = b.id, ty = "li", lt = "o"); len = m.value.length }
            }
            if (nb == null && Regex("^-\\p{L}").containsMatchIn(tx) && c >= 2 && c <= 1 + df.ins) {
                nb = Blk(id = b.id, ty = "li", lt = "b"); len = 1
            }
            if (nb == null) {
                m = Regex("^>\\s").find(tx)
                if (m != null && c == 2) { nb = Blk(id = b.id, ty = "q", k = "quote"); len = 2 }
            }
            if (nb == null) {
                m = Regex("^(#{1,2})\\s").find(tx)
                if (m != null && c == m.value.length) { nb = Blk(id = b.id, ty = "h", lv = m.groupValues[1].length); len = m.value.length }
            }
        } else if (b.ty == "li" && b.lt == "b") {
            val m = Regex("^\\[([ xX]?)]\\s").find(tx)
            if (m != null && c == m.value.length) {
                nb = Blk(id = b.id, ty = "li", lt = "c", d = b.d, x = m.groupValues[1].isNotBlank()); len = m.value.length
            }
        }
        if (nb == null) return false
        commit()
        val nr = rt.slice(len, tx.length)
        setBlock(i, nb.copy(r = nr))
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
        val left = b.r.slice(0, pos)
        val right = b.r.slice(pos, b.r.t.length)
        val nb = if (nty == "li") Blk(ty = "li", lt = b.lt, d = b.d, r = right) else Blk(ty = "p", r = right)
        blocks = blocks.toMutableList().also { it[i] = b.copy(r = left); it.add(i + 1, nb) }
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
            blocks = blocks.toMutableList().also { it[i] = b.copy(r = keep); it.add(i + 1, nb) }
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
                pv.ty == "tbl" -> {
                    val r = pv.tb.size - 1
                    val c = pv.tb[r].size - 1
                    val k = "${pv.id}:$r:$c"
                    focusAt(k, pv.tb[r][c].t.length)
                }
                else -> {
                    // fokus pindah dulu ke kolom tujuan sebelum kolom lama hilang, supaya keyboard tidak menutup
                    try { frs[pv.id]?.requestFocus() } catch (e: Exception) { }
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

    private fun paste(key: String, pos: Int, s: String) {
        val i = idx(key)
        if (i < 0) return
        val b = blocks[i]
        commit()
        val ns = md(s.trimEnd('\n'))
        if (ns.isEmpty()) return
        val l = blocks.toMutableList()
        val rt = b.r
        if (b.ty == "img") l.addAll(i + 1, ns)
        else if (rt.t.isEmpty()) {
            l.removeAt(i); l.addAll(i, ns)
        } else if (pos >= rt.t.length) l.addAll(i + 1, ns)
        else if (pos <= 0) l.addAll(i, ns)
        else {
            val right = if (b.ty == "li") Blk(ty = "li", lt = b.lt, d = b.d, r = rt.slice(pos, rt.t.length))
            else Blk(ty = "p", r = rt.slice(pos, rt.t.length))
            l[i] = b.copy(r = rt.slice(0, pos))
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

    fun setHead(lv: Int) = convertFocused { b ->
        if (b.ty == "h" && b.lv == lv) b.copy(ty = "p") else b.copy(ty = "h", lv = lv, r = flat(b.r))
    }

    fun setList(t: String) = convertFocused { b ->
        if (b.ty == "li" && b.lt == t) b.copy(ty = "p")
        else b.copy(ty = "li", lt = t, d = if (b.ty == "li") b.d else 0, r = flat(b.r))
    }

    fun setQuote(k: String) = convertFocused { b ->
        if (b.ty == "q" && b.k == k) b.copy(ty = "p") else b.copy(ty = "q", k = k)
    }

    fun setPlain() = convertFocused { b -> b.copy(ty = "p") }

    fun setCode() = convertFocused { b ->
        if (b.ty == "code") b.copy(ty = "p") else b.copy(ty = "code", r = Rt(b.r.t))
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
        val key = focus?.let { keyBlock(it) } ?: blocks.last().id
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

    fun insTable() = insertObjs(listOf(Blk(ty = "tbl", tb = grid(3, 3), ca = listOf("left", "left", "left"))))
    fun insHr() = insertObjs(listOf(Blk(ty = "hr")))
    fun addImages(imgs: List<Img>) {
        if (imgs.isNotEmpty()) insertObjs(imgs.map { Blk(ty = "img", im = listOf(it)) })
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
                    ca = b.ca.toMutableList().also { if (c + 1 <= it.size) it.add(c + 1, "left") }
                )
                target = "$bid:$r:${c + 1}"
            }
            "dr" -> {
                if (r == 0 || b.tb.size < 3) { vm.show("Baris ini tidak bisa dihapus"); return }
                nb = b.copy(tb = b.tb.filterIndexed { k, _ -> k != r })
                target = "$bid:${minOf(r, nb.tb.size - 1)}:$c"
            }
            "dc" -> {
                if (cols < 2) { vm.show("Minimal satu kolom"); return }
                nb = b.copy(tb = b.tb.map { row -> row.filterIndexed { k, _ -> k != c } }, ca = b.ca.filterIndexed { k, _ -> k != c })
                target = "$bid:$r:${minOf(c, cols - 2)}"
            }
            "dt" -> {
                blocks = blocks.filterIndexed { k, _ -> k != i }
                tf.keys.removeAll { it.startsWith("$bid:") }
                norm()
                focus = null
                afterStructural()
                blocks.getOrNull(minOf(i, blocks.size - 1))?.let { if (it.isText) focusAt(it.id, it.r.t.length) }
                return
            }
        }
        setBlock(i, nb)
        tf.keys.removeAll { it.startsWith("$bid:") }
        seed(nb)
        focusAt(target, 0)
        afterStructural()
    }

    // ---------- gambar ----------
    fun selectImg(bid: String, ix: Int) {
        if (selOn) dropSel()
        selImg = bid to ix
        focus = null
        clearReq++
    }

    /** Tutup pilihan gambar: tetap di mode edit (bar alat tetap ada), tanpa membuka keyboard dan tanpa masuk mode baca. */
    fun closeImg() {
        selImg = null
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

    fun imgAlign(al: String) {
        val s = selImg ?: return
        commit()
        imgSet(s.first, s.second) {
            val w = if (al != "c" && it.w > maxSideW()) minOf(SIDE_W, maxSideW()) else it.w
            it.copy(al = al, w = w)
        }
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
            if (b.r.t.isNotEmpty()) setBlock(i, Blk(id = b.id, ty = "p", r = b.r))
            else blocks = blocks.filterIndexed { j, _ -> j != i }
        } else if (rest.size == 1) setBlock(i, b.copy(im = listOf(rest[0].copy(a = rest[0].o, w = 0.6f, al = "c"))))
        else setBlock(i, b.copy(im = rest))
        selImg = null
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
            val solo = Blk(ty = "img", im = listOf(m.copy(a = m.o, w = share(m), al = "c")))
            val rest = b.im.filterIndexed { j, _ -> j != s.second }
            val restB = if (rest.size == 1) b.copy(im = listOf(rest[0].copy(a = rest[0].o, w = share(rest[0]), al = "c")))
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
        if (tgt < 0) { vm.show("Belum ada gambar lain di dekatnya untuk dijejerkan"); return }
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
    var dens = 1f
    var viewH = 0f
    var scroll: ScrollState? = null
    var buzz: (() -> Unit)? = null
    val bpos = HashMap<String, FloatArray>()   // id blok -> [atas, tinggi] di ruang konten
    private var mvDir = 0
    private var autoJob: Job? = null

    private fun isEmptyP(x: Blk) = x.ty == "p" && x.r.t.isEmpty()

    fun mvBegin(bid: String, imgId: String) {
        val i = idx(bid)
        if (i < 0) return
        val b = blocks[i]
        val p = bpos[bid] ?: floatArrayOf(0f, 0f)
        commit()
        selImg = bid to maxOf(0, b.im.indexOfFirst { it.id == imgId })
        focus = null; clearReq++
        val hh = if (b.im.size == 1 && sideOk(b)) {
            val m0 = b.im[0]
            widthDp * m0.w.coerceIn(.2f, 1f) / m0.a.coerceIn(.2f, 5f) * dens
        } else p[1]
        mv = Mv(bid, imgId, b.im.size > 1, p[0], hh)
        mvX = 0f; mvY = 0f; dropAt = -1; mvDir = 0
        mvAl = b.im.firstOrNull()?.al ?: "c"
        buzz?.invoke()
        autoJob?.cancel()
        autoJob = scope.launch {
            while (mv != null) {
                val m = mv
                val sc = scroll
                if (m != null && sc != null && !m.row && mvDir != 0 && viewH > 0f) {
                    val cy = m.top + m.h / 2f + mvY - sc.value
                    val z = viewH * .2f
                    val v = when {
                        mvDir < 0 && cy < z -> -(z - cy) / z
                        mvDir > 0 && cy > viewH - z -> (cy - (viewH - z)) / z
                        else -> 0f
                    }
                    if (v != 0f) {
                        val got = sc.scrollBy(v.coerceIn(-1f, 1f) * 16f * dens)
                        if (got != 0f) { mvY += got; mvRecalc() }
                    }
                }
                delay(16)
            }
        }
    }

    fun mvDrag(dx: Float, dy: Float) {
        val m = mv ?: return
        mvX += dx; mvY += dy
        if (dy > .5f) mvDir = 1 else if (dy < -.5f) mvDir = -1
        if (m.row) rowDragStep(m) else mvRecalc()
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
        val i = idx(m.bid)
        if (i < 0) return
        val cy = m.top + m.h / 2f + mvY
        var gap = blocks.size
        for ((j, b) in blocks.withIndex()) {
            if (j == i) continue
            val q = bpos[b.id] ?: continue
            if (q[0] + q[1] / 2f >= cy) { gap = j; break }
        }
        val nd = if (gap == i || gap == i + 1) -1 else gap
        if (nd != dropAt) { dropAt = nd; if (nd >= 0) buzz?.invoke() }
        val b = blocks[i]
        if (b.im.size == 1) {
            val w = widthDp * dens
            val mi = b.im[0]
            val wp = w * mi.w.coerceIn(.2f, 1f)
            val base = when (mi.al) { "l" -> wp / 2f; "r" -> w - wp / 2f; else -> w / 2f }
            val cx = base + mvX
            val al = if (cx < w / 3f) "l" else if (cx > w * 2f / 3f) "r" else "c"
            if (al != mvAl) { mvAl = al; buzz?.invoke() }
        }
    }

    fun mvEnd(apply: Boolean) {
        val m = mv ?: return
        autoJob?.cancel()
        val i = idx(m.bid)
        val to = dropAt
        val al = mvAl
        val anchor = if (to in 0 until blocks.size) blocks[to].id else null
        mv = null; mvX = 0f; mvY = 0f; dropAt = -1
        if (!apply || i < 0) return
        var b = blocks[i]
        if (!m.row && b.im.size == 1 && al != b.im[0].al) {
            val mi = b.im[0]
            val w = if (al != "c" && mi.w > maxSideW()) minOf(SIDE_W, maxSideW()) else mi.w
            b = b.copy(im = listOf(mi.copy(al = al, w = w)))
        }
        val l = blocks.toMutableList()
        if (!m.row && to >= 0 && to != i && to != i + 1) {
            l.removeAt(i)
            if (i > 0 && i < l.size && isEmptyP(l[i - 1]) && isEmptyP(l[i])) l.removeAt(i)
            val at = if (anchor == null) l.size else l.indexOfFirst { it.id == anchor }.let { if (it < 0) l.size else it }
            l.add(at, b)
        } else l[i] = b
        blocks = l
        releaseSide(b.id)
        selImg = b.id to maxOf(0, b.im.indexOfFirst { it.id == m.imgId })
        norm(); afterStructural()
    }

    // ---------- seleksi lintas blok ----------
    var selOn by mutableStateOf(false)
    var pa by mutableStateOf<Pt?>(null)        // titik jangkar
    var pf by mutableStateOf<Pt?>(null)        // titik lawan
    var selDrag by mutableStateOf(false)
    val lays = mutableStateMapOf<String, TextLayoutResult>()
    val kpos = mutableStateMapOf<String, Offset>()
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
        reading = true; selImg = null; pickQuote = false; miniBar = false; clearReq++
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
        if (edit) {
            if (ipos.values.any { it.contains(p) }) return false          // itu gambar, bukan teks
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
            selImg = null; pickQuote = false; miniBar = false
            focusAt(b.key, b.off)   // tetap di mode ketik: keyboard terbuka, ketikan langsung mengganti seleksi
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
        typeAt(lo.key, lo.off, s)
    }

    /** Sisipkan teks s di posisi off (dipakai tempel dan mengetik di atas seleksi), lalu kursor di akhir teks itu. */
    private fun typeAt(key: String, off: Int, s: String) {
        val b = blockOfKey(key) ?: return
        if (s.indexOf('\n') >= 0 && !b.isMulti && !isCellKey(key)) {
            if (s == "\n") enter(key, off) else paste(key, off, s)
            return
        }
        val clean = if (isCellKey(key)) s.replace('\n', ' ') else s
        val rt = rtOf(key)
        val nr = rt.slice(0, off) + Rt(clean) + rt.slice(off, rt.t.length)
        putRt(key, nr)
        tf[key] = TextFieldValue(Z + nr.t, TextRange(off + clean.length + 1))
        dirty(); commit()
        focusAt(key, off + clean.length)
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
        selImg = null; pickQuote = false; miniBar = false
        tf[key]?.let { tf[key] = it.copy(selection = TextRange(r + 1)) }
        caretMoved(key)
        buzz?.invoke()
    }

    // ---------- struktur ----------
    private fun norm() {
        val out = ArrayList<Blk>()
        for (b in blocks) {
            if (b.isObj && (out.isEmpty() || out.last().isObj)) out.add(Blk())
            out.add(b)
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
        if (hist[hi].bl == blocks) return
        while (hist.size > hi + 1) hist.removeAt(hist.size - 1)
        hist.add(Snap(blocks, focus, focus?.let { tf[it]?.selection } ?: TextRange.Zero))
        if (hist.size > 150) hist.removeAt(0)
        hi = hist.size - 1
        tick++
    }

    val canUndo: Boolean get() { tick; return hi > 0 || hist[hi].bl != blocks }
    val canRedo: Boolean get() { tick; return hi < hist.size - 1 }

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
        selImg = null
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
        vm.saveNote(noteId, title, blocks)
    }

    fun leave() {
        flush()
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
        selImg = null; pickQuote = false; miniBar = false
        findQ = ""; findI = 0
        findOn = true
    }

    /** Teks pencarian sengaja tidak dihapus: bilah cari masih terlihat selama animasi keluar. Direset saat openFind(). */
    fun closeFind() {
        findOn = false
    }

    fun enterReading() {
        commit()
        reading = true; selImg = null; pickQuote = false; miniBar = false; clearReq++
    }

    fun exitReading(key: String? = null, off: Int = 0) {
        selOn = false; pa = null; pf = null; selBack = null; selDrag = false
        reading = false
        if (key != null) focusAt(key, off)
    }
}

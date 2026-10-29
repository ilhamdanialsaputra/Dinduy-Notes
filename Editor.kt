package app.dinduy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Req(val key: String, val pos: Int, val n: Int)
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
    var widthDp = 340f

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
        blocks = (n?.bl ?: emptyList()).ifEmpty { listOf(Blk()) }
        blocks.forEach { seed(it) }
        norm()
        hist.add(Snap(blocks, null, TextRange.Zero))
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
        val p = (r.pos + 1).coerceIn(1, v.text.length.coerceAtLeast(1))
        tf[r.key] = v.copy(selection = TextRange(p))
        caretMoved(r.key)
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
            if (v.selection != old.selection) caretMoved(key)
            return
        }
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
        tf[key] = TextFieldValue(Z + nr.t, TextRange((c - len).coerceAtLeast(0) + 1))
        typing = emptySet()
        afterStructural()
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
                pv.ty == "hr" || pv.ty == "img" -> {
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
        if (rt.t.isEmpty()) {
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

    private fun convertFocused(f: (Blk) -> Blk) {
        val key = focus ?: return
        if (isCellKey(key)) return
        val i = idx(key)
        if (i < 0) return
        commit()
        val nb = f(blocks[i])
        setBlock(i, nb)
        afterStructural()
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
        selImg = bid to ix
        focus = null
        clearReq++
    }

    private fun imgSet(bid: String, ix: Int, f: (Img) -> Img) {
        blocks = blocks.map { b ->
            if (b.id == bid) b.copy(im = b.im.mapIndexed { j, m -> if (j == ix) f(m) else m }) else b
        }
    }

    fun imgAlign(al: String) {
        val s = selImg ?: return
        commit(); imgSet(s.first, s.second) { it.copy(al = al) }; dirty(); commit()
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
        if (rest.isEmpty()) blocks = blocks.filterIndexed { j, _ -> j != i }
        else if (rest.size == 1) setBlock(i, b.copy(im = listOf(rest[0].copy(a = rest[0].o, w = 0.6f, al = "c"))))
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
            out.forEach { if (it.isText && !tf.containsKey(it.id)) seed(it) }
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

    fun enterReading() {
        commit()
        reading = true; selImg = null; pickQuote = false; clearReq++
    }

    fun exitReading(key: String? = null, off: Int = 0) {
        reading = false
        if (key != null) focusAt(key, off)
    }
}

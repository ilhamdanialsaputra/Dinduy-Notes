package app.dinduy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Karakter tak terlihat di awal tiap kolom teks, supaya Backspace di awal blok terdeteksi. */
const val Z = "\u200B"
const val GAP = 0.02f

val J = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

fun uid(): String =
    java.lang.Long.toString(System.currentTimeMillis(), 36) +
        java.lang.Long.toString((Math.random() * 1679616).toLong(), 36).padStart(4, '0')

object K {
    const val B = 0
    const val I = 1
    const val U = 2
    const val S = 3
    const val M = 4
    const val C = 5
    const val L = 6
}

val FMT = listOf(K.B, K.I, K.U, K.S, K.M, K.C)

@Serializable
data class Sp(val s: Int, val e: Int, val k: Int, val u: String? = null)

@Serializable
data class Rt(val t: String = "", val sp: List<Sp> = emptyList())

internal fun normSp(list: List<Sp>, len: Int): List<Sp> {
    val out = ArrayList<Sp>()
    val groups = list
        .map { Sp(it.s.coerceIn(0, len), it.e.coerceIn(0, len), it.k, it.u) }
        .filter { it.e > it.s }
        .groupBy { it.k to it.u }
    for ((_, g) in groups) {
        val sorted = g.sortedBy { it.s }
        var cur = sorted[0]
        for (i in 1 until sorted.size) {
            val n = sorted[i]
            if (n.s <= cur.e) cur = cur.copy(e = maxOf(cur.e, n.e)) else {
                out.add(cur); cur = n
            }
        }
        out.add(cur)
    }
    return out.sortedWith(compareBy({ it.s }, { it.k }))
}

private fun subtract(list: MutableList<Sp>, k: Int, a: Int, b: Int) {
    val res = ArrayList<Sp>()
    for (x in list) {
        if (x.k != k || x.e <= a || x.s >= b) {
            res.add(x); continue
        }
        if (x.s < a) res.add(x.copy(e = a))
        if (x.e > b) res.add(x.copy(s = b))
    }
    list.clear(); list.addAll(res)
}

data class Df(val p: Int, val del: Int, val ins: Int)

fun diff(a: String, b: String): Df {
    val mx = minOf(a.length, b.length)
    var p = 0
    while (p < mx && a[p] == b[p]) p++
    var q = 0
    while (q < mx - p && a[a.length - 1 - q] == b[b.length - 1 - q]) q++
    return Df(p, a.length - p - q, b.length - p - q)
}

/** Terapkan perubahan teks, geser span, dan beri gaya `typing` pada teks yang baru diketik. */
fun Rt.edit(nt: String, typing: Set<Int>): Rt {
    val df = diff(t, nt)
    val p = df.p
    val del = df.del
    val ins = df.ins
    val mapped = sp.mapNotNull { x ->
        val s2 = when {
            x.s < p -> x.s
            x.s < p + del -> p + ins
            else -> x.s - del + ins
        }
        val e2 = when {
            x.e <= p -> x.e
            x.e < p + del -> p
            else -> x.e - del + ins
        }
        if (e2 > s2) x.copy(s = s2, e = e2) else null
    }.toMutableList()
    if (ins > 0) {
        for (k in FMT) {
            if (k in typing) mapped.add(Sp(p, p + ins, k)) else subtract(mapped, k, p, p + ins)
        }
    }
    return Rt(nt, normSp(mapped, nt.length))
}

fun Rt.toggle(a: Int, b: Int, k: Int): Rt {
    val lo = minOf(a, b)
    val hi = maxOf(a, b)
    if (hi <= lo) return this
    val covered = sp.any { it.k == k && it.s <= lo && it.e >= hi }
    val l = sp.toMutableList()
    if (covered) subtract(l, k, lo, hi) else l.add(Sp(lo, hi, k))
    return copy(sp = normSp(l, t.length))
}

/** Pasang / lepas gaya pada rentang tanpa membalik (dipakai seleksi lintas blok). */
fun Rt.setFmt(a: Int, b: Int, k: Int, on: Boolean): Rt {
    val lo = minOf(a, b)
    val hi = maxOf(a, b)
    if (hi <= lo) return this
    val l = sp.toMutableList()
    if (on) l.add(Sp(lo, hi, k)) else subtract(l, k, lo, hi)
    return copy(sp = normSp(l, t.length))
}

fun Rt.flags(a: Int, b: Int): Set<Int> {
    val lo = minOf(a, b).coerceAtLeast(0)
    val hi = maxOf(a, b).coerceAtLeast(0)
    return if (lo == hi) sp.filter { it.k in FMT && it.s < lo && lo <= it.e }.map { it.k }.toSet()
    else sp.filter { it.k in FMT && it.s <= lo && it.e >= hi }.map { it.k }.toSet()
}

fun Rt.slice(a: Int, b: Int): Rt {
    val aa = a.coerceIn(0, t.length)
    val bb = b.coerceIn(aa, t.length)
    val ns = sp.mapNotNull { x ->
        val s = maxOf(x.s, aa) - aa
        val e = minOf(x.e, bb) - aa
        if (e > s) x.copy(s = s, e = e) else null
    }
    return Rt(t.substring(aa, bb), normSp(ns, bb - aa))
}

operator fun Rt.plus(o: Rt): Rt {
    val off = t.length
    return Rt(t + o.t, normSp(sp + o.sp.map { it.copy(s = it.s + off, e = it.e + off) }, off + o.t.length))
}

fun Rt.withKind(k: Int): Rt = copy(sp = normSp(sp + Sp(0, t.length, k), t.length))

@Serializable
data class Img(
    val id: String,
    val a: Float = 1f,      // rasio tampil (lebar/tinggi); di baris = bobot lebar
    val o: Float = 1f,      // rasio asli
    val al: String = "c",   // l / c / r
    val w: Float = 1f,      // lebar relatif (gambar tunggal)
    val px: Float = 0.5f,
    val py: Float = 0.5f,
    val z: Float = 1f       // zoom pangkas (1 = pas bingkai)
)

/** ty: p, h, li, q, code, tbl, hr, img */
@Serializable
data class Blk(
    val id: String = uid(),
    val ty: String = "p",
    val lv: Int = 1,
    val lt: String = "b",
    val d: Int = 0,
    val x: Boolean = false,
    val k: String = "quote",
    val l: String = "",
    val r: Rt = Rt(),
    val tb: List<List<Rt>> = emptyList(),
    val ca: List<String> = emptyList(),
    val im: List<Img> = emptyList()
) {
    val isText: Boolean get() = ty == "p" || ty == "h" || ty == "li" || ty == "q" || ty == "code"
    val isObj: Boolean get() = ty == "tbl" || ty == "img" || ty == "hr"
    val isMulti: Boolean get() = ty == "q" || ty == "code"
}

@Serializable
data class Note(
    val id: String,
    val t: String = "",
    val bl: List<Blk> = listOf(Blk()),
    val x: String = "",
    val u: Long = 0L,
    val c: Long = 0L,
    val a: Boolean = false,
    val p: Boolean = false,
    val dt: Long = 0L
)

@Serializable
data class Prefs(
    val mode: String = "light",
    val seed: String = "#6750A4",
    val fs: Int = 19,
    val lh: Float = 1.65f,
    val sort: String = "u",
    val cd: String = "f"
)

@Serializable
data class Db(val pr: Prefs = Prefs(), val docs: List<Note> = emptyList())

fun plainOf(bl: List<Blk>): String = bl.joinToString("\n") {
    when (it.ty) {
        "hr" -> ""
        "img" -> it.r.t.replace('\n', ' ')
        "tbl" -> it.tb.flatten().joinToString(" ") { c -> c.t }
        else -> it.r.t.replace('\n', ' ')
    }
}.replace("\u200B", "").trim()

fun ords(bl: List<Blk>): Map<String, Int> {
    val m = HashMap<String, Int>()
    val c = IntArray(6)
    for (b in bl) {
        if (b.ty == "li") {
            val d = b.d.coerceIn(0, 4)
            for (k in d + 1 until 6) c[k] = 0
            if (b.lt == "o") {
                c[d]++; m[b.id] = c[d]
            } else c[d] = 0
        } else java.util.Arrays.fill(c, 0)
    }
    return m
}

fun wordCount(x: String): Int {
    val s = x.trim()
    return if (s.isEmpty()) 0 else s.split(Regex("\\s+")).size
}

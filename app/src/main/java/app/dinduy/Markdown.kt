package app.dinduy

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class RtB {
    val sb = StringBuilder()
    val sp = ArrayList<Sp>()
    fun text(s: String) { sb.append(s) }
    fun add(r: Rt) {
        val o = sb.length
        sb.append(r.t)
        r.sp.forEach { sp.add(it.copy(s = it.s + o, e = it.e + o)) }
    }
    fun wrap(k: Int, inner: Rt, u: String? = null) {
        val o = sb.length
        add(inner)
        if (sb.length > o) sp.add(Sp(o, sb.length, k, u))
    }
    fun build() = Rt(sb.toString(), normSp(sp, sb.length))
}

private val RX_LINK = Regex("""\[([^\]]+)\]\((https?:[^)\s]+|mailto:[^)\s]+)\)""")
private val RX_IMG = Regex("""!\[([^\]]*)\]\([^)\s]*\)""")
private val RX_REF = Regex("""\[([^\]]+)\]\[([^\]]*)\]""")
private val RX_AUTO = Regex("""<(https?:[^\s>]+)>""")
private const val ESCS = "\\`*_~=^[]()#+-.!|>"
private val DELIMS = listOf(
    "***" to listOf(K.B, K.I), "**" to listOf(K.B), "__" to listOf(K.B),
    "~~" to listOf(K.S), "==" to listOf(K.M), "++" to listOf(K.U)
)

fun inl(s: String, refs: Map<String, String> = emptyMap()): Rt {
    val b = RtB()
    var i = 0
    val n = s.length
    outer@ while (i < n) {
        val c = s[i]
        if (c == '\\' && i + 1 < n && ESCS.indexOf(s[i + 1]) >= 0) {
            b.text(s[i + 1].toString()); i += 2; continue
        }
        if (c == '`') {
            val j = s.indexOf('`', i + 1)
            if (j > i + 1) { b.wrap(K.C, Rt(s.substring(i + 1, j))); i = j + 1; continue }
        }
        if (c == '!' && i + 1 < n && s[i + 1] == '[') {
            val m = RX_IMG.find(s, i)
            if (m != null && m.range.first == i) { b.text(m.groupValues[1]); i = m.range.last + 1; continue }
        }
        if (c == '[') {
            val m = RX_LINK.find(s, i)
            if (m != null && m.range.first == i) {
                b.wrap(K.L, inl(m.groupValues[1], refs), m.groupValues[2]); i = m.range.last + 1; continue
            }
            val r = RX_REF.find(s, i)
            if (r != null && r.range.first == i) {
                val key = r.groupValues[2].ifEmpty { r.groupValues[1] }.lowercase()
                val u = refs[key]
                if (u != null) { b.wrap(K.L, inl(r.groupValues[1], refs), u); i = r.range.last + 1; continue }
            }
        }
        if (c == '<') {
            val m = RX_AUTO.find(s, i)
            if (m != null && m.range.first == i) {
                b.wrap(K.L, Rt(m.groupValues[1]), m.groupValues[1]); i = m.range.last + 1; continue
            }
        }
        if (c == 'h' && (i == 0 || s[i - 1].isWhitespace()) && (s.startsWith("http://", i) || s.startsWith("https://", i))) {
            var j = i
            while (j < n && !s[j].isWhitespace() && s[j] != '<') j++
            val u = s.substring(i, j).trimEnd('.', ',', ';', ':', '!', '?', ')')
            if (u.length > 8) { b.wrap(K.L, Rt(u), u); i += u.length; continue }
        }
        for ((mk, kinds) in DELIMS) {
            if (!s.startsWith(mk, i)) continue
            val j = s.indexOf(mk, i + mk.length)
            if (j <= i + mk.length) continue
            val inner = s.substring(i + mk.length, j)
            if (inner.isBlank()) continue
            var r = inl(inner, refs)
            for (k in kinds) r = r.withKind(k)
            b.add(r)
            i = j + mk.length
            continue@outer
        }
        if ((c == '*' || c == '_') && i + 1 < n && !s[i + 1].isWhitespace() &&
            (i == 0 || !(s[i - 1].isLetterOrDigit() || s[i - 1] == c))
        ) {
            val j = s.indexOf(c, i + 1)
            if (j > i + 1 && !s[j - 1].isWhitespace() && (j + 1 >= n || !s[j + 1].isLetterOrDigit())) {
                b.add(inl(s.substring(i + 1, j), refs).withKind(K.I))
                i = j + 1
                continue
            }
        }
        b.text(c.toString()); i++
    }
    return b.build()
}

private val RX_LI = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)""")
private val RX_BLK = Regex("""^(```|#{1,6}\s|>|\s*([-*+]|\d+[.)])\s+|(-{3,}|\*{3,}|_{3,})\s*$)""")
private val RX_ROW = Regex("""^\s*\|.*\|\s*$""")
private val RX_SEP = Regex("""^\s*\|?[\s:|-]*-[\s:|-]*\|?\s*$""")
private val RX_REFDEF = Regex("""^\s{0,3}\[([^\]^]+)\]:\s*(\S+)\s*$""")
private val RX_HR = Regex("""^(-{3,}|\*{3,}|_{3,})\s*$""")
private val RX_H = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val RX_FENCE = Regex("""^```\s*(\S*)""")
private val RX_TAG = Regex("""^\[!(\w+)]\s*$""")
private val RX_CHK = Regex("""^\[([ xX])]\s+(.*)""")
private val RX_SETEXT = Regex("""^(=+|-{2,})\s*$""")
private val VK = mapOf(
    "note" to "note", "tip" to "tip", "important" to "imp", "warning" to "warn",
    "caution" to "cau", "idea" to "idea", "def" to "def", "definition" to "def"
)

private fun isBl(l: String) = RX_BLK.containsMatchIn(l) || RX_ROW.matches(l)

fun md(src: String): List<Blk> {
    val refs = HashMap<String, String>()
    val L = src.replace("\r", "").split("\n").filter {
        val m = RX_REFDEF.find(it)
        if (m != null) { refs[m.groupValues[1].lowercase()] = m.groupValues[2]; false } else true
    }
    val o = ArrayList<Blk>()
    var i = 0
    while (i < L.size) {
        val l = L[i]
        if (l.isBlank()) { i++; continue }
        val f = RX_FENCE.find(l)
        if (f != null) {
            val c = ArrayList<String>()
            i++
            while (i < L.size && !L[i].startsWith("```")) c.add(L[i++])
            i++
            o.add(Blk(ty = "code", l = f.groupValues[1], r = Rt(c.joinToString("\n"))))
            continue
        }
        val h = RX_H.find(l)
        if (h != null) {
            o.add(Blk(ty = "h", lv = h.groupValues[1].length, r = inl(h.groupValues[2], refs))); i++; continue
        }
        if (RX_HR.matches(l)) { o.add(Blk(ty = "hr")); i++; continue }
        if (l.startsWith(">")) {
            val c = ArrayList<String>()
            while (i < L.size && L[i].startsWith(">")) c.add(L[i++].replace(Regex("^(>\\s?)+"), ""))
            var k = "quote"
            val t = RX_TAG.find(c[0])
            if (t != null && VK.containsKey(t.groupValues[1].lowercase())) {
                k = VK[t.groupValues[1].lowercase()]!!; c.removeAt(0)
            }
            while (c.isNotEmpty() && c.last().isBlank()) c.removeAt(c.size - 1)
            o.add(Blk(ty = "q", k = k, r = inl(c.joinToString("\n"), refs)))
            continue
        }
        if (RX_ROW.matches(l) && i + 1 < L.size && RX_SEP.matches(L[i + 1])) {
            fun sp(r: String): List<String> =
                r.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            val al = sp(L[i + 1]).map {
                if (Regex("^:-*:$").matches(it)) "center" else if (it.endsWith("-:") || it == "-:") "right" else "left"
            }
            val hd = sp(l)
            i += 2
            val rows = ArrayList<List<Rt>>()
            rows.add(hd.map { inl(it, refs) })
            while (i < L.size && L[i].contains('|') && L[i].isNotBlank()) {
                val rw = sp(L[i++])
                rows.add(hd.indices.map { inl(rw.getOrElse(it) { "" }, refs) })
            }
            if (rows.size == 1) rows.add(hd.map { Rt() })
            o.add(Blk(ty = "tbl", tb = rows, ca = al))
            continue
        }
        if (RX_LI.matches(l)) {
            while (i < L.size) {
                val m = RX_LI.find(L[i]) ?: break
                val d = minOf(4, m.groupValues[1].replace("\t", "    ").length / 2)
                val ord = m.groupValues[2][0].isDigit()
                var x = m.groupValues[3]
                var t = if (ord) "o" else "b"
                var done = false
                if (!ord) {
                    val k = RX_CHK.find(x)
                    if (k != null) { t = "c"; done = k.groupValues[1] != " "; x = k.groupValues[2] }
                }
                o.add(Blk(ty = "li", lt = t, d = d, x = done, r = inl(x, refs)))
                i++
            }
            continue
        }
        if (i + 1 < L.size && !isBl(l) && RX_SETEXT.matches(L[i + 1])) {
            o.add(Blk(ty = "h", lv = if (L[i + 1][0] == '=') 1 else 2, r = inl(l.trim(), refs))); i += 2; continue
        }
        val c = ArrayList<String>()
        while (i < L.size && L[i].isNotBlank() && (c.isEmpty() || !isBl(L[i]))) c.add(L[i++])
        o.add(Blk(ty = "p", r = inl(c.joinToString("\n"), refs)))
    }
    return o.ifEmpty { listOf(Blk()) }
}

// ---------- ekspor ----------

private val RX_ESC = Regex("""[\\`*_~=^\[\]|+]""")

fun rtMd(r: Rt): String {
    if (r.t.isEmpty()) return ""
    val cuts = java.util.TreeSet<Int>()
    cuts.add(0); cuts.add(r.t.length)
    r.sp.forEach { cuts.add(it.s); cuts.add(it.e) }
    val cl = cuts.toList()
    val sb = StringBuilder()
    for (x in 0 until cl.size - 1) {
        val a = cl[x]
        val b = cl[x + 1]
        if (b <= a) continue
        val seg = r.t.substring(a, b)
        val act = r.sp.filter { it.s <= a && it.e >= b }
        if (act.any { it.k == K.C } && !seg.contains('`')) { sb.append('`').append(seg).append('`'); continue }
        val out = seg.replace(RX_ESC) { "\\" + it.value }
        val lead = out.takeWhile { it.isWhitespace() }
        val trail = out.takeLastWhile { it.isWhitespace() }
        var core = out.trim()
        if (core.isNotEmpty()) {
            if (act.any { it.k == K.M }) core = "==" + core + "=="
            if (act.any { it.k == K.S }) core = "~~" + core + "~~"
            if (act.any { it.k == K.U }) core = "++" + core + "++"
            if (act.any { it.k == K.I }) core = "*" + core + "*"
            if (act.any { it.k == K.B }) core = "**" + core + "**"
            val ln = act.firstOrNull { it.k == K.L }
            if (ln != null) core = "[" + core + "](" + ln.u + ")"
        }
        sb.append(lead).append(core).append(trail)
    }
    return sb.toString()
}

private fun lineEsc(l: String) = l
    .replace(Regex("^(\\s*)([-#>])"), "$1\\\\$2")
    .replace(Regex("^(\\s*\\d+)([.)])(\\s)"), "$1\\\\$2$3")

private val TAGMD = mapOf(
    "note" to "NOTE", "tip" to "TIP", "imp" to "IMPORTANT", "warn" to "WARNING", "cau" to "CAUTION",
    "idea" to "IDEA", "def" to "DEF"
)

fun toMd(bl: List<Blk>): String {
    val od = ords(bl)
    val out = ArrayList<Pair<String, Boolean>>()
    for (b in bl) {
        var s: String
        var li = false
        when (b.ty) {
            "li" -> {
                val mk = when (b.lt) {
                    "o" -> (od[b.id] ?: 1).toString() + "."
                    "c" -> if (b.x) "- [x]" else "- [ ]"
                    else -> "-"
                }
                s = "  ".repeat(b.d) + mk + " " + rtMd(b.r).replace("\n", " ")
                li = true
            }
            "p" -> {
                s = rtMd(b.r).split("\n").joinToString("\n") { lineEsc(it) }
                if (s.isBlank()) continue
            }
            "h" -> s = "#".repeat(b.lv.coerceIn(1, 6)) + " " + rtMd(b.r).replace("\n", " ")
            "q" -> {
                val tg = TAGMD[b.k]
                s = (if (tg != null) "> [!$tg]\n" else "") + rtMd(b.r).split("\n").joinToString("\n") { "> $it" }
            }
            "code" -> s = "```" + b.l + "\n" + b.r.t + "\n```"
            "tbl" -> {
                if (b.tb.isEmpty()) continue
                fun row(r: List<Rt>) = "| " + r.joinToString(" | ") {
                    rtMd(it).replace("\n", " ").replace("|", "¦").ifEmpty { " " }
                } + " |"
                val al = b.tb[0].indices.map {
                    when (b.ca.getOrNull(it)) { "center" -> ":---:"; "right" -> "---:"; else -> "---" }
                }
                s = (listOf(row(b.tb[0]), "| " + al.joinToString(" | ") + " |") + b.tb.drop(1).map { row(it) })
                    .joinToString("\n")
            }
            "hr" -> s = "---"
            "img" -> s = b.im.joinToString(" ") { "![gambar]" }
            else -> continue
        }
        out.add(s to li)
    }
    val sb = StringBuilder()
    out.forEachIndexed { i, o ->
        if (i > 0) sb.append(if (o.second && out[i - 1].second) "\n" else "\n\n")
        sb.append(o.first)
    }
    return sb.append("\n").toString()
}

// ---------- impor catatan versi web (HTML) ----------

private fun inlH(n: Node, b: RtB) {
    for (c in n.childNodes()) {
        if (c is TextNode) b.text(c.wholeText.replace("\u200b", ""))
        else if (c is Element) {
            when (c.tagName().lowercase()) {
                "br" -> b.text("\n")
                "b", "strong" -> subH(b, c, K.B)
                "i", "em" -> subH(b, c, K.I)
                "u" -> subH(b, c, K.U)
                "s", "strike", "del" -> subH(b, c, K.S)
                "mark" -> subH(b, c, K.M)
                "code" -> subH(b, c, K.C)
                "a" -> subH(b, c, K.L, c.attr("href"))
                else -> inlH(c, b)
            }
        }
    }
}

private fun subH(b: RtB, c: Element, k: Int, u: String? = null) {
    val o = b.sb.length
    inlH(c, b)
    if (b.sb.length > o) b.sp.add(Sp(o, b.sb.length, k, u))
}

private fun rtH(e: Element, keepNl: Boolean = false): Rt {
    val b = RtB()
    inlH(e, b)
    var r = b.build()
    if (r.t.endsWith("\n")) r = r.slice(0, r.t.length - 1)
    return if (keepNl) r else r.copy(t = r.t.replace('\n', ' '))
}

private val RX_W = Regex("""width:\s*([\d.]+)%""")
private val RX_AR = Regex("""aspect-ratio:\s*([\d.]+)""")
private val RX_OP = Regex("""object-position:\s*([\d.]+)%\s+([\d.]+)%""")

private fun figImg(f: Element, inRow: Boolean): Img {
    val st = f.attr("style")
    val ist = f.selectFirst("img")?.attr("style") ?: ""
    val o = f.attr("data-o").toFloatOrNull() ?: 1f
    val a = if (inRow) (f.attr("data-a").toFloatOrNull() ?: o)
    else (RX_AR.find(st)?.groupValues?.get(1)?.toFloatOrNull() ?: o)
    val w = (RX_W.find(st)?.groupValues?.get(1)?.toFloatOrNull() ?: 100f) / 100f
    val op = RX_OP.find(ist)
    return Img(
        id = f.attr("data-i"), a = a, o = o, al = f.attr("data-al").ifEmpty { "c" },
        w = w.coerceIn(0.2f, 1f),
        px = (op?.groupValues?.get(1)?.toFloatOrNull() ?: 50f) / 100f,
        py = (op?.groupValues?.get(2)?.toFloatOrNull() ?: 50f) / 100f
    )
}

fun htmlToBlocks(h: String): List<Blk> {
    val body = Jsoup.parseBodyFragment(h).body()
    val o = ArrayList<Blk>()
    for (e in body.children()) {
        val tag = e.tagName().lowercase()
        when {
            tag == "p" -> o.add(Blk(ty = "p", r = rtH(e, true)))
            tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6' ->
                o.add(Blk(ty = "h", lv = tag[1] - '0', r = rtH(e)))
            tag == "div" && e.hasClass("li") -> o.add(
                Blk(
                    ty = "li", lt = e.attr("data-t").ifEmpty { "b" },
                    d = (e.attr("data-d").toIntOrNull() ?: 0).coerceIn(0, 4),
                    x = e.hasAttr("data-x"), r = rtH(e)
                )
            )
            tag == "blockquote" -> o.add(Blk(ty = "q", k = e.attr("data-k").ifEmpty { "quote" }, r = rtH(e, true)))
            tag == "pre" -> o.add(Blk(ty = "code", l = e.attr("data-l"), r = rtH(e, true)))
            tag == "hr" -> o.add(Blk(ty = "hr"))
            tag == "div" && e.hasClass("tw") -> {
                val rows = e.select("tr").map { tr -> tr.children().map { c -> rtH(c) } }
                if (rows.isNotEmpty()) {
                    val ca = e.select("tr").first()!!.children().map {
                        val st = it.attr("style")
                        if (st.contains("center")) "center" else if (st.contains("right")) "right" else "left"
                    }
                    o.add(Blk(ty = "tbl", tb = rows, ca = ca))
                }
            }
            tag == "figure" -> o.add(Blk(ty = "img", im = listOf(figImg(e, false))))
            tag == "div" && e.hasClass("ir") ->
                o.add(Blk(ty = "img", im = e.select("figure.im").map { figImg(it, true) }))
        }
    }
    return o.ifEmpty { listOf(Blk()) }
}

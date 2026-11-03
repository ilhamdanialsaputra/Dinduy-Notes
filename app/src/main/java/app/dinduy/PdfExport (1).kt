package app.dinduy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun notePdfName(n: Note): String = noteFileName(n).removeSuffix(".md") + ".pdf"

/**
 * Ekspor catatan ke PDF (A4, vektor). Teks ditata ulang per blok dengan StaticLayout lalu dipotong di batas blok / baris,
 * jadi tidak ada baris atau gambar yang terpotong di tengah. Coretan digambar sebagai vektor, menempel pada blok yang sama
 * seperti di editor (x = pecahan lebar, y = dp dari atas blok). Rekaman suara tidak ikut.
 *
 * Satuan: 1 pt halaman = 1 satuan kanvas. r = pt per dp (lebar konten PDF dibagi lebar konten editor), jadi bentuk catatan
 * sama dengan di layar. k = pengali ukuran huruf dari geser "Ukuran huruf"; coretan tidak ikut k.
 */
object PdfExport {
    const val PW = 595
    const val PH = 842
    const val ML = 42f
    const val MT = 46f
    const val MB = 54f
    const val CW = 595f - 84f          // lebar konten (pt)
    const val CH = 842f - 46f - 54f    // tinggi konten per halaman (pt)

    private val seq = AtomicInteger(0)

    class Result(val file: File, val pages: Int)

    // ------------------------------------------------------------------ struktur data
    private class Fonts(val reg: Typeface, val bold: Typeface, val ital: Typeface, val bi: Typeface, val mono: Typeface) {
        fun pick(b: Boolean, i: Boolean): Typeface = if (b && i) bi else if (b) bold else if (i) ital else reg
    }

    private class Piece(val y: Float, val h: Float, val x: Float, val back: ((Canvas) -> Unit)?, val front: ((Canvas) -> Unit)?)

    private class PStroke(val path: Path, val tool: Int, val color: Int, val w: Float, val top: Float, val bottom: Float)

    private class Flow {
        val pieces = ArrayList<Piece>()
        val cuts = ArrayList<Float>()          // posisi yang boleh jadi batas halaman
        val zones = ArrayList<FloatArray>()    // rentang yang tidak boleh dipotong (gambar, judul)
        val strokes = ArrayList<PStroke>()
        var y = 0f
    }

    private class Cx(
        val dir: File, val pal: Pal, val fonts: Fonts,
        val cw: Float, val r: Float, val fs: Float, val lh: Float, val u: Float, val h: Float,
        val ords: Map<String, Int>
    ) {
        val recycle = ArrayList<Bitmap>()
    }

    private class FloatCtx(val rem: Float, val indent: Float, val left: Boolean)

    private class Em(val total: Float, val textH: Float)

    private class QSt(val bg: Color, val fg: Color, val r: FloatArray, val label: String?, val bar: Boolean = false, val border: Boolean = false)

    private class TfSpan(private val tf: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(p: TextPaint) { p.typeface = tf }
        override fun updateMeasureState(p: TextPaint) { p.typeface = tf }
    }

    private class SzSpan(private val sz: Float) : MetricAffectingSpan() {
        override fun updateDrawState(p: TextPaint) { p.textSize = sz }
        override fun updateMeasureState(p: TextPaint) { p.textSize = sz }
    }

    private const val FL = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

    // ------------------------------------------------------------------ huruf
    private fun loadFonts(ctx: Context, ff: String): Fonts {
        val am = ctx.assets
        val mono = Typeface.MONOSPACE
        fun asset(n: String): Typeface? = try { Typeface.createFromAsset(am, "fonts/$n") } catch (e: Throwable) { null }
        fun sys(base: Typeface) = Fonts(
            Typeface.create(base, Typeface.NORMAL), Typeface.create(base, Typeface.BOLD),
            Typeface.create(base, Typeface.ITALIC), Typeface.create(base, Typeface.BOLD_ITALIC), mono
        )
        return try {
            when (ff) {
                "times" -> {
                    val r = asset("tinos_regular.ttf")
                    val b = asset("tinos_bold.ttf")
                    val i = asset("tinos_italic.ttf")
                    val bi = asset("tinos_bolditalic.ttf")
                    if (r != null && b != null && i != null && bi != null) Fonts(r, b, i, bi, mono) else sys(Typeface.SERIF)
                }
                "flex" -> {
                    fun v(w: Int): Typeface? = try {
                        Typeface.Builder(am, "fonts/roboto_flex.ttf").setFontVariationSettings("'wght' $w").build()
                    } catch (e: Throwable) { null }
                    val r = v(400)
                    val b = v(800)
                    if (r != null && b != null) Fonts(r, b, Typeface.create(r, Typeface.ITALIC), Typeface.create(b, Typeface.ITALIC), mono)
                    else sys(Typeface.SANS_SERIF)
                }
                else -> sys(Typeface.DEFAULT)
            }
        } catch (e: Throwable) {
            sys(Typeface.DEFAULT)
        }
    }

    // ------------------------------------------------------------------ teks
    /** Satu StaticLayout dengan gaya inline. narrow/indent: jumlah baris pertama yang digeser (teks mengelilingi gambar). */
    private fun mk(
        cx: Cx, rt: Rt, width: Float, size: Float, color: Int, lineH: Float,
        bold: Boolean = false, mono: Boolean = false, ls: Float = 0f, ta: String = "",
        strike: Boolean = false, narrow: Int = 0, indent: Int = 0, leftSide: Boolean = true
    ): StaticLayout {
        val text = rt.t
        val len = text.length
        val sb = SpannableStringBuilder(text)
        val fonts = cx.fonts
        val pal = cx.pal
        val sps = rt.sp.filter { it.e > it.s }
        val bounds = TreeSet<Int>()
        bounds.add(0)
        bounds.add(len)
        for (s in sps) {
            bounds.add(s.s.coerceIn(0, len))
            bounds.add(s.e.coerceIn(0, len))
        }
        val bl = bounds.toList()
        for (ix in 0 until bl.size - 1) {
            val a = bl[ix]
            val b = bl[ix + 1]
            if (b <= a) continue
            var bd = bold
            var ital = false
            var cd = false
            for (s in sps) {
                if (s.s <= a && s.e >= b) {
                    when (s.k) {
                        K.B, K.L -> bd = true
                        K.I -> ital = true
                        K.C -> cd = true
                    }
                }
            }
            if (cd) {
                sb.setSpan(TfSpan(fonts.mono), a, b, FL)
                sb.setSpan(SzSpan(size * .85f), a, b, FL)
            } else if (!mono && (bd != bold || ital)) {
                sb.setSpan(TfSpan(fonts.pick(bd, ital)), a, b, FL)
            } else if (mono && ital) {
                sb.setSpan(TfSpan(fonts.mono), a, b, FL)
            }
        }
        for (s in sps) {
            val a = s.s.coerceIn(0, len)
            val b = s.e.coerceIn(0, len)
            if (b <= a) continue
            when (s.k) {
                K.U -> sb.setSpan(UnderlineSpan(), a, b, FL)
                K.S -> sb.setSpan(StrikethroughSpan(), a, b, FL)
                K.M -> {
                    sb.setSpan(BackgroundColorSpan(pal.pc.toArgb()), a, b, FL)
                    sb.setSpan(ForegroundColorSpan(pal.opc.toArgb()), a, b, FL)
                }
                K.C -> {
                    sb.setSpan(BackgroundColorSpan(pal.sc.toArgb()), a, b, FL)
                    sb.setSpan(ForegroundColorSpan(pal.osc.toArgb()), a, b, FL)
                }
                K.L -> {
                    sb.setSpan(ForegroundColorSpan(pal.p.toArgb()), a, b, FL)
                    sb.setSpan(UnderlineSpan(), a, b, FL)
                }
            }
        }
        if (strike && len > 0) sb.setSpan(StrikethroughSpan(), 0, len, FL)

        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.color = color
        p.typeface = if (mono) fonts.mono else fonts.pick(bold, false)
        p.letterSpacing = ls
        val fm = p.fontMetrics
        val add = lineH - (fm.descent - fm.ascent)
        val wi = max(1, width.toInt())
        val builder = StaticLayout.Builder.obtain(sb, 0, len, p, wi)
            .setAlignment(
                when (ta) {
                    "c" -> Layout.Alignment.ALIGN_CENTER
                    "r" -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
            )
            .setLineSpacing(add, 1f)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        if (ta == "j") builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        if (narrow > 0 && indent > 0) {
            val arr = IntArray(narrow + 1) { if (it < narrow) indent else 0 }
            val zero = IntArray(narrow + 1)
            if (leftSide) builder.setIndents(arr, zero) else builder.setIndents(zero, arr)
        }
        return builder.build()
    }

    private fun lineCuts(f: Flow, lay: StaticLayout, top: Float) {
        for (i in 1 until lay.lineCount) f.cuts.add(top + lay.getLineTop(i))
    }

    // ------------------------------------------------------------------ paragraf, judul, poin
    private fun drawMarker(c: Canvas, cx: Cx, b: Blk, n: Int, x0: Float, lineH: Float) {
        val fs = cx.fs
        val pal = cx.pal
        val cy = lineH / 2f - .04f * fs
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (b.lt) {
            "o" -> {
                val d = 1.55f * fs
                p.color = pal.p.toArgb()
                c.drawCircle(x0 + d / 2f, cy, d / 2f, p)
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
                tp.textSize = fs * .85f
                tp.typeface = cx.fonts.bold
                tp.color = pal.op.toArgb()
                tp.textAlign = Paint.Align.CENTER
                val fm = tp.fontMetrics
                c.drawText(n.toString(), x0 + d / 2f, cy - (fm.ascent + fm.descent) / 2f, tp)
            }
            "c" -> {
                val sz = 1.15f * fs
                val l = x0 + .1f * fs
                val t = cy - sz / 2f
                val rr = .3f * sz
                if (b.x) {
                    p.color = pal.p.toArgb()
                    c.drawRoundRect(RectF(l, t, l + sz, t + sz), rr, rr, p)
                    p.color = -1
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = sz * .13f
                    p.strokeCap = Paint.Cap.ROUND
                    p.strokeJoin = Paint.Join.ROUND
                    val ck = Path()
                    ck.moveTo(l + sz * .24f, t + sz * .52f)
                    ck.lineTo(l + sz * .43f, t + sz * .70f)
                    ck.lineTo(l + sz * .77f, t + sz * .30f)
                    c.drawPath(ck, p)
                } else {
                    val sw = .14f * fs
                    p.color = pal.p.toArgb()
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = sw
                    c.drawRoundRect(RectF(l + sw / 2f, t + sw / 2f, l + sz - sw / 2f, t + sz - sw / 2f), rr, rr, p)
                }
            }
            else -> {
                val s = .5f * fs
                val l = x0 + .3f * fs
                p.color = pal.p.toArgb()
                c.drawOval(RectF(l, cy - s / 2f, l + s, cy + s / 2f), p)
            }
        }
    }

    private fun emitText(f: Flow, cx: Cx, b: Blk, fl: FloatCtx?): Em {
        val fs = cx.fs
        val pal = cx.pal
        val y0 = f.y
        f.cuts.add(y0)
        return when (b.ty) {
            "h" -> {
                val mul = when (b.lv) { 1 -> 2.5f; 2 -> 1.85f; 3 -> 1.45f; else -> 1.15f }
                val size = fs * mul
                val col = if (b.lv == 1 || b.lv == 3) pal.p else pal.os
                val top = fs * .4f
                val bot = fs * .5f
                val ind = fl?.indent ?: 0f
                val lay = mk(cx, b.r, cx.cw - ind, size, col.toArgb(), size * 1.02f, bold = true, ls = -.02f, ta = b.ta)
                val x = if (fl != null && fl.left) ind else 0f
                f.pieces.add(Piece(y0 + top, lay.height.toFloat(), x, null) { c -> lay.draw(c) })
                f.zones.add(floatArrayOf(y0, y0 + top + lay.height + bot + .5f))
                f.y = y0 + top + lay.height + bot
                Em(f.y - y0, lay.height.toFloat())
            }
            "li" -> {
                val mw = (when (b.lt) { "o" -> 2.2f; "c" -> 1.9f; else -> 1.7f }) * fs
                val ind0 = b.d.coerceIn(0, 8) * 1.4f * fs
                val flInd = fl?.indent ?: 0f
                val x0 = if (fl != null && fl.left) flInd else 0f
                val inset = ind0 + mw
                val width = (cx.cw - flInd - inset).coerceAtLeast(30f)
                val done = b.lt == "c" && b.x
                val col = (if (done) pal.os.copy(alpha = .6f) else pal.os).toArgb()
                val lineH = fs * cx.lh
                val lay = mk(cx, b.r, width, fs, col, lineH, strike = done, ta = b.ta)
                lineCuts(f, lay, y0)
                val n = cx.ords[b.id] ?: 1
                f.pieces.add(Piece(y0, lay.height.toFloat(), x0, null) { c ->
                    drawMarker(c, cx, b, n, ind0, lineH)
                    c.save()
                    c.translate(inset, 0f)
                    lay.draw(c)
                    c.restore()
                })
                f.y = y0 + lay.height + fs * .5f
                Em(f.y - y0, lay.height.toFloat())
            }
            else -> {
                val lineH = fs * cx.lh
                var narrow = 0
                var ind = 0
                if (fl != null && fl.rem > 0f) {
                    narrow = ceil(fl.rem / lineH).toInt().coerceAtLeast(0)
                    ind = fl.indent.roundToInt()
                }
                val lay = mk(
                    cx, b.r, cx.cw, fs, pal.os.toArgb(), lineH, ta = b.ta,
                    narrow = narrow, indent = ind, leftSide = fl?.left ?: true
                )
                lineCuts(f, lay, y0)
                f.pieces.add(Piece(y0, lay.height.toFloat(), 0f, null) { c -> lay.draw(c) })
                f.y = y0 + lay.height + fs * 1.1f
                Em(f.y - y0, lay.height.toFloat())
            }
        }
    }

    // ------------------------------------------------------------------ kutipan
    private fun qst(k: String, p: Pal): QSt = when (k) {
        "note" -> QSt(p.pc, p.opc, floatArrayOf(28f, 28f, 28f, 28f), "Catatan")
        "tip" -> QSt(p.sc, p.osc, floatArrayOf(40f, 12f, 40f, 12f), "Tips")
        "imp" -> QSt(p.p, p.op, floatArrayOf(12f, 40f, 12f, 40f), "Penting")
        "warn" -> QSt(p.wc, p.owc, floatArrayOf(24f, 24f, 24f, 24f), "Peringatan", bar = true)
        "cau" -> QSt(p.ec, p.oec, floatArrayOf(6f, 32f, 32f, 32f), "Awas")
        "idea" -> QSt(p.ac, p.oac, floatArrayOf(44f, 44f, 12f, 44f), "Ide")
        "def" -> QSt(Color.Transparent, p.os, floatArrayOf(32f, 32f, 32f, 32f), "Definisi", border = true)
        else -> QSt(p.tc, p.otc, floatArrayOf(28f, 28f, 28f, 8f), null)
    }

    private fun boxPath(w: Float, h: Float, rs: FloatArray): Path {
        val path = Path()
        path.addRoundRect(RectF(0f, 0f, w, h), rs, Path.Direction.CW)
        return path
    }

    private fun radii(r: FloatArray, scale: Float, w: Float, h: Float): FloatArray {
        val cap = min(w, h) / 2f
        fun v(x: Float) = min(x * scale, cap)
        return floatArrayOf(v(r[0]), v(r[0]), v(r[1]), v(r[1]), v(r[2]), v(r[2]), v(r[3]), v(r[3]))
    }

    private fun emitQuote(f: Flow, cx: Cx, b: Blk) {
        val pal = cx.pal
        val fs = cx.fs
        val u = cx.u
        val q = qst(b.k, pal)
        val padX = 22f * u
        val padY = 18f * u
        val size = fs * 1.05f
        val lay = mk(cx, b.r, cx.cw - 2f * padX, size, q.fg.toArgb(), size * cx.lh, ta = b.ta)
        val lsz = fs * .7f * 1.05f
        val lp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        lp.textSize = lsz
        lp.typeface = cx.fonts.bold
        lp.letterSpacing = .12f
        val isDef = b.k == "def"
        lp.color = (if (isDef) pal.p else q.fg).copy(alpha = if (isDef) 1f else .85f).toArgb()
        val lfm = lp.fontMetrics
        val label = q.label
        val labelH = if (label != null) (lfm.descent - lfm.ascent) + fs * .45f * .7f else 0f
        val boxH = padY * 2f + labelH + lay.height
        val y0 = f.y
        f.cuts.add(y0)
        for (i in 1 until lay.lineCount) f.cuts.add(y0 + padY + labelH + lay.getLineTop(i))
        val rs = radii(q.r, u, cx.cw, boxH)
        val w = cx.cw
        f.pieces.add(Piece(y0, boxH, 0f, { c ->
            val path = boxPath(w, boxH, rs)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            if (q.bg.alpha > 0f) {
                p.color = q.bg.toArgb()
                c.drawPath(path, p)
            }
            if (q.bar) {
                c.save()
                c.clipPath(path)
                p.color = pal.owc.toArgb()
                c.drawRect(0f, 0f, 7f * u, boxH, p)
                c.restore()
            }
            if (q.border) {
                val bw = 3f * u
                p.color = pal.p.toArgb()
                p.style = Paint.Style.STROKE
                p.strokeWidth = bw
                val ins = Path()
                ins.addRoundRect(RectF(bw / 2f, bw / 2f, w - bw / 2f, boxH - bw / 2f), rs, Path.Direction.CW)
                c.drawPath(ins, p)
            }
        }, { c ->
            if (label != null) c.drawText(label.uppercase(), padX, padY - lfm.ascent, lp)
            c.save()
            c.translate(padX, padY + labelH)
            lay.draw(c)
            c.restore()
        }))
        f.y = y0 + boxH + fs * 1.1f
    }

    // ------------------------------------------------------------------ kode
    private fun emitCode(f: Flow, cx: Cx, b: Blk) {
        val pal = cx.pal
        val fs = cx.fs
        val u = cx.u
        val pad = 20f * u
        val tsz = fs * .82f
        val lay = mk(cx, Rt(b.r.t), cx.cw - 2f * pad, tsz, pal.os.toArgb(), tsz * 1.55f, mono = true)
        val label = if (b.l.isNotEmpty()) b.l.uppercase() else null
        val cp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        cp.textSize = 12f * u
        cp.typeface = cx.fonts.bold
        cp.letterSpacing = .1f
        cp.color = pal.op.toArgb()
        val cfm = cp.fontMetrics
        val chipPx = 12f * u
        val chipPy = 4f * u
        val chipTextH = cfm.descent - cfm.ascent
        val chipH = if (label != null) chipTextH + 2f * chipPy + 14f * u else 0f
        val boxH = pad * 2f + chipH + lay.height
        val y0 = f.y
        f.cuts.add(y0)
        for (i in 1 until lay.lineCount) f.cuts.add(y0 + pad + chipH + lay.getLineTop(i))
        val rs = radii(floatArrayOf(28f, 28f, 28f, 28f), u, cx.cw, boxH)
        val w = cx.cw
        f.pieces.add(Piece(y0, boxH, 0f, { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = pal.c3.toArgb()
            c.drawPath(boxPath(w, boxH, rs), p)
        }, { c ->
            if (label != null) {
                val tw = cp.measureText(label)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = pal.p.toArgb()
                val rr = min(12f * u, (chipTextH + 2f * chipPy) / 2f)
                c.drawRoundRect(RectF(pad, pad, pad + tw + 2f * chipPx, pad + chipTextH + 2f * chipPy), rr, rr, p)
                c.drawText(label, pad + chipPx, pad + chipPy - cfm.ascent, cp)
            }
            c.save()
            c.translate(pad, pad + chipH)
            lay.draw(c)
            c.restore()
        }))
        f.y = y0 + boxH + fs * 1.1f
    }

    // ------------------------------------------------------------------ tabel
    private fun emitTable(f: Flow, cx: Cx, b: Blk) {
        val pal = cx.pal
        val rows = b.tb
        val y0 = f.y
        f.cuts.add(y0)
        if (rows.isEmpty()) return
        val cols = rows[0].size.coerceAtLeast(1)
        val tfs = min(cx.fs * .9f, 14f)
        val lineH = tfs * 1.45f
        val colW = cx.cw / cols
        val padX = tfs * .9f
        val padY = tfs * .7f
        val inner = (colW - 2f * padX).coerceAtLeast(10f)
        val lays = ArrayList<List<StaticLayout>>()
        val hts = ArrayList<Float>()
        rows.forEachIndexed { ri, row ->
            val head = ri == 0
            val ls = (0 until cols).map { ci ->
                val rt = row.getOrNull(ci) ?: Rt()
                val ta = when (b.ca.getOrNull(ci)) { "center" -> "c"; "right" -> "r"; else -> "" }
                mk(cx, rt, inner, tfs, (if (head) pal.osc else pal.os).toArgb(), lineH, bold = head, ta = ta)
            }
            lays.add(ls)
            hts.add(ls.maxOf { it.height }.toFloat() + 2f * padY)
        }
        val tops = ArrayList<Float>()
        var acc = 0f
        for (h in hts) { tops.add(acc); acc += h }
        val total = acc
        for (ri in 2 until rows.size) f.cuts.add(y0 + tops[ri])
        val cw = cx.cw
        val rr = min(24f * cx.u, 18f)
        f.pieces.add(Piece(y0, total, 0f, { c ->
            val path = Path()
            path.addRoundRect(RectF(0f, 0f, cw, total), rr, rr, Path.Direction.CW)
            c.save()
            c.clipPath(path)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = pal.c2.toArgb()
            c.drawRect(0f, 0f, cw, total, p)
            p.color = pal.sc.toArgb()
            c.drawRect(0f, 0f, cw, hts[0], p)
            p.color = pal.ol.toArgb()
            p.strokeWidth = .8f
            for (ri in 1 until rows.size) c.drawLine(0f, tops[ri], cw, tops[ri], p)
            for (ci in 1 until cols) c.drawLine(ci * colW, 0f, ci * colW, total, p)
            c.restore()
        }, { c ->
            for (ri in lays.indices) {
                for (ci in lays[ri].indices) {
                    c.save()
                    c.translate(ci * colW + padX, tops[ri] + padY)
                    lays[ri][ci].draw(c)
                    c.restore()
                }
            }
        }))
        f.y = y0 + total + cx.fs * 1.1f
    }

    // ------------------------------------------------------------------ garis pemisah
    private fun emitHr(f: Flow, cx: Cx) {
        val padV = cx.fs * .9f
        val bh = (8f * cx.u).coerceIn(2f, 8f)
        val y0 = f.y
        f.cuts.add(y0)
        val cw = cx.cw
        val col = cx.pal.ol.toArgb()
        f.pieces.add(Piece(y0, padV * 2f + bh, 0f, { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = col
            c.drawRoundRect(RectF(0f, padV, cw, padV + bh), bh / 2f, bh / 2f, p)
        }, null))
        f.y = y0 + padV * 2f + bh
    }

    // ------------------------------------------------------------------ gambar
    private fun loadBmp(dir: File, id: String): Bitmap? = try {
        val file = File(dir, "$id.jpg")
        if (!file.exists()) null else {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, o)
            var s = 1
            while (o.outWidth / (s * 2) >= 1800 && o.outHeight / (s * 2) >= 1800) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            BitmapFactory.decodeFile(file.path, o2)
        }
    } catch (e: Throwable) {
        null
    }

    /** Pangkas sesuai zoom / geser gambar di editor, lalu kecilkan ke ~160 dpi supaya PDF tidak membengkak. */
    private fun cellBitmap(cx: Cx, m: Img, bw: Float, bh: Float): Bitmap? {
        val src = loadBmp(cx.dir, m.id) ?: return null
        try {
            val iw = src.width
            val ih = src.height
            val z = m.z.coerceIn(1f, 4f)
            val o = iw.toFloat() / ih
            val wide = o > bw / bh
            val cw = if (wide) bh * o * z else bw * z
            val ch = if (wide) bh * z else bw / o * z
            val vw = (iw * bw / cw).coerceIn(1f, iw.toFloat())
            val vh = (ih * bh / ch).coerceIn(1f, ih.toFloat())
            val ox = ((iw - vw) * m.px).coerceIn(0f, iw - vw)
            val oy = ((ih - vh) * m.py).coerceIn(0f, ih - vh)
            val rect = Rect(
                ox.roundToInt(), oy.roundToInt(),
                (ox + vw).roundToInt().coerceAtMost(iw), (oy + vh).roundToInt().coerceAtMost(ih)
            )
            val tw = min(rect.width(), (bw * 2.2f).roundToInt()).coerceAtLeast(8)
            val th = (tw * bh / bw).roundToInt().coerceAtLeast(8)
            val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            out.setHasAlpha(false)
            val cv = Canvas(out)
            cv.drawColor(-1)
            val pt = Paint(Paint.FILTER_BITMAP_FLAG)
            cv.drawBitmap(src, rect, Rect(0, 0, tw, th), pt)
            cx.recycle.add(out)
            return out
        } finally {
            src.recycle()
        }
    }

    private fun drawCell(c: Canvas, cx: Cx, bmp: Bitmap?, x: Float, w: Float, h: Float) {
        val rad = min(22f * cx.u, min(w, h) / 2f)
        val path = Path()
        path.addRoundRect(RectF(x, 0f, x + w, h), rad, rad, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        if (bmp != null) {
            c.drawBitmap(bmp, null, RectF(x, 0f, x + w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = cx.pal.c3.toArgb()
            c.drawRect(x, 0f, x + w, h, p)
        }
        c.restore()
    }

    /** Mengembalikan jumlah blok yang terpakai (gambar + teks yang mengelilinginya). */
    private fun emitImg(f: Flow, cx: Cx, bl: List<Blk>, i: Int, last: Int, maxSide: Float): Int {
        val b = bl[i]
        val fs = cx.fs
        val bottom = fs * 1.1f
        val n = b.im.size
        val y0 = f.y
        f.cuts.add(y0)
        if (n == 0) return 1
        val maxH = cx.h * .9f
        if (n == 1) {
            val m = b.im[0]
            var w = cx.cw * m.w.coerceIn(.2f, 1f)
            val ratio = m.a.coerceIn(.2f, 5f)
            var h = w / ratio
            if (h > maxH) {
                val s = maxH / h
                w *= s
                h *= s
            }
            val cell = cellBitmap(cx, m, w, h)
            val float = m.al != "c" && m.w.coerceIn(.2f, 1f) <= maxSide + .001f
            val x = when (m.al) { "l" -> 0f; "r" -> cx.cw - w; else -> (cx.cw - w) / 2f }
            val ww = w
            val hh = h
            f.pieces.add(Piece(y0, h, x, null) { c -> drawCell(c, cx, cell, 0f, ww, hh) })
            if (!float) {
                f.zones.add(floatArrayOf(y0, y0 + h))
                f.y = y0 + h + bottom
                return 1
            }
            // gambar mengambang: teks setelahnya mengalir di sampingnya sampai tinggi gambar habis
            val indent = w + SIDE_GAP * cx.u
            val left = m.al == "l"
            val groupBottom = y0 + h + bottom
            f.zones.add(floatArrayOf(y0, groupBottom))
            var rem = h + bottom
            var j = i + 1
            f.y = y0
            while (j <= last && rem > 0f) {
                val x2 = bl[j]
                if (x2.ty != "p" && x2.ty != "h" && x2.ty != "li") break
                val top = if (x2.ty == "h") fs * .4f else 0f
                if (top >= rem) break
                val sy = f.y
                val em = emitText(f, cx, x2, FloatCtx(rem, indent, left))
                inkOf(f, cx, x2, sy)
                val fits = top + em.textH <= rem
                j++
                if (!fits) {
                    rem = 0f
                    break
                }
                rem -= em.total
            }
            f.y = max(f.y, groupBottom)
            return j - i
        }
        // baris beberapa gambar
        val sum = b.im.sumOf { it.a.toDouble() }.toFloat().coerceAtLeast(.1f)
        val wsum = b.im.sumOf { it.a.coerceAtLeast(.05f).toDouble() }.toFloat()
        val gap = cx.cw * GAP
        val avail = cx.cw * (1f - (n - 1) * GAP)
        var h = avail / sum
        var scale = 1f
        if (h > maxH) scale = maxH / h
        h *= scale
        val widths = b.im.map { avail * (it.a.coerceAtLeast(.05f) / wsum) * scale }
        val totalW = widths.sum() + (n - 1) * gap
        val x0 = (cx.cw - totalW) / 2f
        val cells = b.im.mapIndexed { ix, m -> cellBitmap(cx, m, widths[ix], h) }
        val hh = h
        f.pieces.add(Piece(y0, h, x0, null) { c ->
            var xx = 0f
            for (ix in widths.indices) {
                drawCell(c, cx, cells[ix], xx, widths[ix], hh)
                xx += widths[ix] + gap
            }
        })
        f.zones.add(floatArrayOf(y0, y0 + h))
        f.y = y0 + h + bottom
        return 1
    }

    // ------------------------------------------------------------------ coretan
    private fun inkOf(f: Flow, cx: Cx, b: Blk, top: Float) {
        for (s in b.ik) {
            val n = s.p.size / 3
            if (n == 0) continue
            val a = FloatArray(n * 3)
            for (q in 0 until n) {
                a[q * 3] = s.p[q * 3] * cx.cw
                a[q * 3 + 1] = top + s.p[q * 3 + 1] * cx.r
                a[q * 3 + 2] = s.p[q * 3 + 2]
            }
            val wPx = s.w * cx.r
            val ap = inkPath(a, n, s.t, wPx, s.sh).asAndroidPath()
            val rf = RectF()
            ap.computeBounds(rf, true)
            val pad = if (s.t == 1) wPx / 2f else 0f
            f.strokes.add(PStroke(ap, s.t, inkColor(cx.pal, s.c, s.t == 1).toArgb(), wPx, rf.top - pad, rf.bottom + pad))
        }
    }

    // ------------------------------------------------------------------ tata letak seluruh catatan
    private fun layoutAll(cx: Cx, note: Note, maxSide: Float): Flow {
        val f = Flow()
        val bl = note.bl
        val pal = cx.pal
        if (note.t.isNotBlank()) {
            val size = 34f * cx.u
            val lay = mk(cx, Rt(note.t), cx.cw, size, pal.p.toArgb(), size * 1.1f, bold = true, ls = -.02f)
            val top = 6f * cx.u
            val bot = 12f * cx.u
            f.pieces.add(Piece(top, lay.height.toFloat(), 0f, null) { c -> lay.draw(c) })
            f.zones.add(floatArrayOf(0f, top + lay.height + bot + .5f))
            f.y = top + lay.height + bot
        }
        // blok kosong di ujung bawah (paragraf kosong tanpa coretan) tidak perlu jadi halaman
        var last = bl.size - 1
        while (last >= 0) {
            val b = bl[last]
            val empty = b.ty == "p" && b.r.t.isBlank() && b.ik.isEmpty()
            if (!empty) break
            last--
        }
        var i = 0
        while (i <= last) {
            val b = bl[i]
            val y0 = f.y
            var used = 1
            when (b.ty) {
                "p", "h", "li" -> emitText(f, cx, b, null)
                "q" -> emitQuote(f, cx, b)
                "code" -> emitCode(f, cx, b)
                "tbl" -> emitTable(f, cx, b)
                "hr" -> emitHr(f, cx)
                "img" -> used = emitImg(f, cx, bl, i, last, maxSide)
            }
            inkOf(f, cx, b, y0)
            i += used
        }
        return f
    }

    // ------------------------------------------------------------------ paginasi
    private fun paginate(f: Flow, total: Float, h: Float): List<FloatArray> {
        val ok = f.cuts.filter { c -> c > 1f && c < total - 1f }
            .sorted().distinct()
            .filter { c -> f.zones.none { z -> c > z[0] + .01f && c < z[1] - .01f } }
        val crossable = f.strokes.filter { it.bottom - it.top < h * .9f }
        fun crosses(c: Float) = crossable.any { it.top + .5f < c && c < it.bottom - .5f }
        val out = ArrayList<FloatArray>()
        var y0 = 0f
        while (y0 < total - .5f && out.size < 400) {
            val limit = y0 + h
            if (limit >= total - .5f) {
                out.add(floatArrayOf(y0, total))
                break
            }
            var clean = -1f
            var any = -1f
            for (c in ok) {
                if (c <= y0 + 1f) continue
                if (c > limit) break
                any = c
                if (!crosses(c)) clean = c
            }
            val cut = when {
                clean > y0 + h * .4f -> clean
                any > y0 + h * .12f -> any
                else -> limit
            }
            out.add(floatArrayOf(y0, cut))
            y0 = cut
        }
        if (out.isEmpty()) out.add(floatArrayOf(0f, max(total, 1f)))
        return out
    }

    private fun pageOf(ranges: List<FloatArray>, y: Float): Int {
        for (i in ranges.indices) if (y < ranges[i][1]) return i
        return ranges.size - 1
    }

    private fun drawStrokes(c: Canvas, f: Flow, ranges: List<FloatArray>, page: Int, tool: Int) {
        val y0 = ranges[page][0]
        c.save()
        c.translate(ML, MT - y0)
        for (s in f.strokes) {
            if ((if (s.tool == 1) 1 else 0) != tool) continue
            if (pageOf(ranges, (s.top + s.bottom) / 2f) != page) continue
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = s.color
            if (s.tool == 1) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(s.w, .5f)
                p.strokeCap = Paint.Cap.BUTT
                p.strokeJoin = Paint.Join.ROUND
            } else {
                p.style = Paint.Style.FILL
            }
            c.drawPath(s.path, p)
        }
        c.restore()
    }

    // ------------------------------------------------------------------ titik masuk
    /**
     * Bangun PDF ke cacheDir/pdfx. bodyPt = ukuran huruf isi (pt). widthDp = lebar konten editor (dp), dipakai supaya
     * letak coretan sama dengan di layar. Jalankan di thread latar.
     */
    fun build(ctx: Context, note: Note, pr: Prefs, imgDir: File, bodyPt: Float, widthDp: Float, maxSide: Float): Result {
        val app = ctx.applicationContext
        val pal = (if (pr.dyn) dynamicPal(app, "light") else null) ?: buildPal("light", pr.seed)
        val fonts = loadFonts(app, pr.ff)
        val r = CW / widthDp.coerceAtLeast(120f)
        val fs = bodyPt.coerceIn(5f, 80f)
        val k = fs / (pr.fs * r)
        val cx = Cx(imgDir, pal, fonts, CW, r, fs, pr.lh, r * k, CH, ords(note.bl))
        val dir = File(app.cacheDir, "pdfx")
        dir.mkdirs()
        val out = File(dir, "p" + seq.incrementAndGet() + ".pdf")
        val doc = PdfDocument()
        try {
            val f = layoutAll(cx, note, maxSide)
            var total = max(f.y, 1f)
            for (s in f.strokes) total = max(total, min(s.bottom + 6f, f.y + CH))
            val ranges = paginate(f, total, CH)
            val footer = TextPaint(Paint.ANTI_ALIAS_FLAG)
            footer.textSize = 9f
            footer.typeface = fonts.reg
            footer.color = pal.osv.toArgb()
            footer.textAlign = Paint.Align.CENTER
            ranges.forEachIndexed { pi, rg ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, pi + 1).create())
                val c = page.canvas
                c.drawColor(-1)
                val y0 = rg[0]
                val y1 = rg[1]
                fun content(front: Boolean) {
                    c.save()
                    c.clipRect(0f, MT, PW.toFloat(), MT + (y1 - y0))
                    for (p in f.pieces) {
                        if (p.y >= y1 || p.y + p.h <= y0) continue
                        val fn = (if (front) p.front else p.back) ?: continue
                        c.save()
                        c.translate(ML + p.x, MT + p.y - y0)
                        fn(c)
                        c.restore()
                    }
                    c.restore()
                }
                content(false)
                drawStrokes(c, f, ranges, pi, 1)   // stabilo di bawah teks, seperti spidol sungguhan
                content(true)
                drawStrokes(c, f, ranges, pi, 0)   // pulpen di atas
                c.drawText((pi + 1).toString() + " / " + ranges.size, PW / 2f, PH - 26f, footer)
                doc.finishPage(page)
            }
            FileOutputStream(out).use { doc.writeTo(it) }
            return Result(out, ranges.size)
        } finally {
            doc.close()
            for (bm in cx.recycle) if (!bm.isRecycled) bm.recycle()
        }
    }
}

package app.dinduy

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================================
//  Mesin 3D buku. Bukan trik 2D: setiap sisi (sampul depan, sisi dalam sampul, tepi sampul, punggung buku, tepi halaman)
//  adalah bidang di ruang 3D yang diputar lalu diproyeksikan dengan perspektif. Sampul = papan tebal (kaku) berengsel di
//  punggung kiri. Tekstur sampul / halaman dipetakan dengan homografi (Matrix.setPolyToPoly + BitmapShader), jadi
//  perspektifnya benar. Sisi yang membelakangi kamera dibuang (culling), sisi yang terlihat diberi bayangan sesuai arah cahaya.
//  Dipakai oleh kartu buku di beranda (pose diam) DAN lapisan animasi (pose + sudut sampul berubah), jadi keduanya identik.
// =====================================================================================

private fun blk(a: Float): Int = (a * 255f).roundToInt().coerceIn(0, 255) shl 24
private fun wht(a: Float): Int = ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF

/** Tekstur muka sampul + warna tepi (rata-rata warna sampul, digelapkan). */
class CoverTex(val bm: Bitmap, val edge: Int)

object BookTex {
    private val cache = object : LruCache<String, CoverTex>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CoverTex): Int = value.bm.byteCount
    }
    private var innerBm: Bitmap? = null

    private fun key(d: Note) = "${d.cv}|${d.cx}|${d.cy}|${d.cz}"

    /** Tekstur yang sudah jadi (null bila belum dibuat). */
    fun peek(d: Note): CoverTex? = cache.get(key(d))

    /**
     * Pastikan tekstur ada. Aman dipanggil dari thread latar (dipanggil setelah Imgs.load). Bila gambar sampul belum ada di cache,
     * hasilnya tekstur polos sementara yang TIDAK disimpan (supaya nanti dibuat ulang dengan gambar asli).
     */
    fun ensure(d: Note): CoverTex {
        val k = key(d)
        cache.get(k)?.let { return it }
        val src = if (d.cv.isEmpty()) null else try { Imgs.peek(d.cv)?.asAndroidBitmap() } catch (e: Throwable) { null }
        val t = build(src, d)
        if (src != null || d.cv.isEmpty()) cache.put(k, t)
        return t
    }

    private fun build(src0: Bitmap?, d: Note): CoverTex {
        val tw = if (src0 != null) min(src0.width, 640).coerceAtLeast(160) else 480
        val th = (tw / BookSpec.ASPECT).roundToInt()
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(0xFFE9E4DA.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (src0 != null) {
            val src = if (android.os.Build.VERSION.SDK_INT >= 26 && src0.config == Bitmap.Config.HARDWARE) src0.copy(Bitmap.Config.ARGB_8888, false) else src0
            if (src != null) {
                val iw = src.width.toFloat(); val ih = src.height.toFloat()
                val s = max(tw / iw, th / ih) * d.cz.coerceAtLeast(1f)
                val vw = tw / s; val vh = th / s
                val sx = (iw - vw) * d.cx.coerceIn(0f, 1f); val sy = (ih - vh) * d.cy.coerceIn(0f, 1f)
                c.drawBitmap(
                    src,
                    android.graphics.Rect(sx.roundToInt(), sy.roundToInt(), min(src.width, (sx + vw).roundToInt()), min(src.height, (sy + vh).roundToInt())),
                    android.graphics.Rect(0, 0, tw, th), p
                )
            }
        }
        val w = tw.toFloat(); val h = th.toFloat()
        // alur engsel di kiri (sama dengan BookCoverFace)
        p.reset(); p.shader = LinearGradient(0f, 0f, w * .09f, 0f, intArrayOf(blk(.40f), blk(.10f), 0), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w * .09f, h, p)
        p.reset(); p.color = wht(.30f); c.drawRect(w * .095f, 0f, w * .095f + max(1f, w * .006f), h, p)
        p.reset(); p.color = blk(.18f); c.drawRect(w * .088f, 0f, w * .088f + max(1f, w * .005f), h, p)
        // kilau lembut
        p.reset(); p.shader = LinearGradient(w * .1f, 0f, w * .95f, h * .55f, intArrayOf(wht(.18f), 0, 0), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        // tepi bawah / kanan sedikit gelap supaya terasa tebal
        p.reset(); p.shader = LinearGradient(0f, h * .7f, 0f, h, intArrayOf(0, blk(.20f)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.reset(); p.shader = LinearGradient(w * .93f, 0f, w, 0f, intArrayOf(0, blk(.14f)), null, Shader.TileMode.CLAMP)
        c.drawRect(w * .93f, 0f, w, h, p)
        val px = Bitmap.createScaledBitmap(out, 1, 1, true).getPixel(0, 0)
        return CoverTex(out, mul(px, .62f))
    }

    /** Sisi dalam sampul (kertas pelapis): engsel di kiri (u = 0). */
    fun inner(): Bitmap {
        innerBm?.let { return it }
        val w = 256; val h = 366
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(0xFFD9D2C3.toInt(), 0xFFF1ECE0.toInt(), 0xFFE6DFD0.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.reset(); p.shader = LinearGradient(0f, 0f, w * .10f, 0f, intArrayOf(blk(.24f), 0), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w * .10f, h.toFloat(), p)
        p.reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = blk(.07f)
        c.drawRect(w * .06f, h * .04f, w * .96f, h * .96f, p)
        innerBm = b
        return b
    }

    /** Bitmap halaman diperkecil sekali (kualitas lebih baik daripada skala besar langsung oleh shader). */
    fun pageTex(bmp: Bitmap, targetW: Float): Bitmap {
        if (bmp.width <= targetW * 2f) return bmp
        if (BookFx.pageTexSrc === bmp) BookFx.pageTex?.let { return it }
        val nw = (targetW * 1.5f).roundToInt().coerceAtLeast(2)
        val nh = (bmp.height * nw.toFloat() / bmp.width).roundToInt().coerceAtLeast(2)
        val s = try { Bitmap.createScaledBitmap(bmp, nw, nh, true) } catch (e: Throwable) { bmp }
        BookFx.pageTexSrc = bmp; BookFx.pageTex = s
        return s
    }

    fun mul(color: Int, s: Float): Int {
        val r = ((color shr 16 and 255) * s).roundToInt().coerceIn(0, 255)
        val g = ((color shr 8 and 255) * s).roundToInt().coerceIn(0, 255)
        val b = ((color and 255) * s).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

object Book3D {
    private val LIGHT = floatArrayOf(-.398f, -.597f, .697f)   // arah cahaya (kiri-atas-depan), sudah ternormalisasi
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val mat = Matrix()
    private val src8 = FloatArray(8)
    private val dst8 = FloatArray(8)
    private val INV = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )))
    private const val PAPER = 0xFFF2EEE5.toInt()
    private const val PAPER_LINE = 0x33000000

    /**
     * Gambar buku di kotak (l,t,r,b) = bidang depan buku di layar (px).
     * yaw / pitch (derajat) = pose seluruh buku; k = skala; deg = sudut buka sampul (0 tertutup, 180 rebah ke kiri).
     * page = bitmap halaman yang dibuka terakhir (null = kertas putih); showPage = gambar halaman di balik sampul.
     */
    fun draw(
        cv: android.graphics.Canvas, l: Float, t: Float, r: Float, b: Float,
        yaw: Float, pitch: Float, k: Float, deg: Float,
        cover: CoverTex?, page: Bitmap?, showPage: Boolean, dark: Boolean
    ) {
        val W = r - l
        val H = b - t
        if (W < 2f || H < 2f) return
        val hw = W / 2f
        val hh = H / 2f
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val D = BookSpec.CAMERA * W
        val tc = BookSpec.BOARD * W      // tebal papan sampul
        val pg = BookSpec.PAGES * W      // tebal blok halaman
        val ry = Math.toRadians(yaw.toDouble()); val rp = Math.toRadians(pitch.toDouble())
        val cyw = cos(ry).toFloat(); val syw = sin(ry).toFloat()
        val cp = cos(rp).toFloat(); val sp = sin(rp).toFloat()
        val th = Math.toRadians(deg.toDouble())
        val ct = cos(th).toFloat(); val st = sin(th).toFloat()
        val rL = .018f * W
        val rR = .045f * W
        val edge = cover?.edge ?: 0xFF6B5A48.toInt()

        val po = FloatArray(3)   // titik terakhir setelah pose (x, y, z)
        val ps = FloatArray(2)   // titik terakhir di layar
        val q = FloatArray(12)   // 4 sudut bidang (O, O+U, O+U+V, O+V)

        fun pt(x: Float, y: Float, z: Float) {
            val xs = x * k; val ys = y * k; val zs = z * k
            val x1 = xs * cyw + zs * syw
            val z1 = -xs * syw + zs * cyw
            val y2 = ys * cp - z1 * sp
            val z2 = ys * sp + z1 * cp
            po[0] = x1; po[1] = y2; po[2] = z2
            val s = D / max(D * .15f, D - z2)
            ps[0] = cx + x1 * s; ps[1] = cy + y2 * s
        }

        // bidang di koordinat adegan (tidak berputar bersama sampul)
        fun sc(ox: Float, oy: Float, oz: Float, ux: Float, uy: Float, uz: Float, vx: Float, vy: Float, vz: Float) {
            for (i in 0..3) {
                val a = if (i == 1 || i == 2) 1f else 0f
                val c = if (i >= 2) 1f else 0f
                q[i * 3] = ox + a * ux + c * vx; q[i * 3 + 1] = oy + a * uy + c * vy; q[i * 3 + 2] = oz + a * uz + c * vz
            }
        }
        // bidang di koordinat sampul (rx dari engsel, y, rz tebal) lalu diputar mengelilingi engsel di punggung kiri
        fun cvq(ox: Float, oy: Float, oz: Float, ux: Float, uy: Float, uz: Float, vx: Float, vy: Float, vz: Float) {
            for (i in 0..3) {
                val a = if (i == 1 || i == 2) 1f else 0f
                val c = if (i >= 2) 1f else 0f
                val rx = ox + a * ux + c * vx; val y = oy + a * uy + c * vy; val rz = oz + a * uz + c * vz
                q[i * 3] = -hw + rx * ct - rz * st; q[i * 3 + 1] = y; q[i * 3 + 2] = tc / 2f + rx * st + rz * ct
            }
        }

        /** Proyeksikan q ke dst8 dan hitung terang bidang. -1 = membelakangi kamera (dibuang). */
        fun face(nx: Float, ny: Float, nz: Float): Float {
            var fx = 0f; var fy = 0f; var fz = 0f
            for (i in 0..3) {
                pt(q[i * 3], q[i * 3 + 1], q[i * 3 + 2])
                dst8[i * 2] = ps[0]; dst8[i * 2 + 1] = ps[1]
                if (i == 0) { fx = po[0]; fy = po[1]; fz = po[2] }
            }
            val n1x = nx * cyw + nz * syw
            val n1z = -nx * syw + nz * cyw
            val n2y = ny * cp - n1z * sp
            val n2z = ny * sp + n1z * cp
            if (n1x * (-fx) + n2y * (-fy) + n2z * (D - fz) <= 0f) return -1f
            val lit = n1x * LIGHT[0] + n2y * LIGHT[1] + n2z * LIGHT[2]
            return .62f + .38f * max(0f, lit)
        }

        fun quadPath() {
            path.reset()
            path.moveTo(dst8[0], dst8[1]); path.lineTo(dst8[2], dst8[3]); path.lineTo(dst8[4], dst8[5]); path.lineTo(dst8[6], dst8[7]); path.close()
        }
        fun solid(color: Int, shade: Float) {
            quadPath()
            paint.reset(); paint.isAntiAlias = true; paint.color = BookTex.mul(color, shade)
            cv.drawPath(path, paint)
        }
        // lintasan bersudut membulat (kiri kecil, kanan besar seperti sampul asli) di bidang q
        fun roundedPath(lu: Float, lv: Float) {
            path.reset()
            var first = true
            fun add(a: Float, bb: Float) {
                val u = a / lu; val v = bb / lv
                pt(
                    q[0] + u * (q[3] - q[0]) + v * (q[9] - q[0]),
                    q[1] + u * (q[4] - q[1]) + v * (q[10] - q[1]),
                    q[2] + u * (q[5] - q[2]) + v * (q[11] - q[2])
                )
                if (first) { path.moveTo(ps[0], ps[1]); first = false } else path.lineTo(ps[0], ps[1])
            }
            fun arc(ccx: Float, ccy: Float, rad: Float, a0: Int) {
                for (i in 0..5) {
                    val ang = Math.toRadians(a0 + 90.0 * i / 5.0)
                    add(ccx + (rad * cos(ang)).toFloat(), ccy + (rad * sin(ang)).toFloat())
                }
            }
            arc(lu - rR, rR, rR, -90)
            arc(lu - rR, lv - rR, rR, 0)
            arc(rL, lv - rL, rL, 90)
            arc(rL, rL, rL, 180)
            path.close()
        }
        // isi roundedPath dengan tekstur (homografi) lalu gelapkan sesuai cahaya
        fun textured(bm: Bitmap, sl: Float, stp: Float, sr: Float, sb: Float, shade: Float, inv: Boolean) {
            src8[0] = sl; src8[1] = stp; src8[2] = sr; src8[3] = stp; src8[4] = sr; src8[5] = sb; src8[6] = sl; src8[7] = sb
            if (!mat.setPolyToPoly(src8, 0, dst8, 0, 4)) return
            val sh = BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            sh.setLocalMatrix(mat)
            paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true; paint.shader = sh
            if (inv) paint.colorFilter = INV
            cv.drawPath(path, paint)
            if (shade < .995f) {
                paint.reset(); paint.isAntiAlias = true
                paint.color = ((1f - shade).coerceIn(0f, .6f) * 255f).roundToInt() shl 24
                cv.drawPath(path, paint)
            }
        }
        // bidang q diisi gradasi linear dari titik g0 ke g1 (koordinat adegan)
        fun gradQuad(g0x: Float, g1x: Float, c0: Int, cm: Int, c1: Int, midAt: Float) {
            quadPath()
            pt(g0x, 0f, 0f); val x0 = ps[0]; val y0 = ps[1]
            pt(g1x, 0f, 0f)
            paint.reset(); paint.isAntiAlias = true
            paint.shader = LinearGradient(x0, y0, ps[0], ps[1], intArrayOf(c0, cm, c1), floatArrayOf(0f, midAt.coerceIn(.01f, .99f), 1f), Shader.TileMode.CLAMP)
            cv.drawPath(path, paint)
        }
        fun line(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, shade: Float) {
            pt(x0, y0, z0); val ax = ps[0]; val ay = ps[1]
            pt(x1, y1, z1)
            paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, W * .004f); paint.color = PAPER_LINE
            cv.drawLine(ax, ay, ps[0], ps[1], paint)
        }

        // ---------------------------------------------------------------- 1. punggung dan tepi blok halaman (diam)
        sc(-hw, -hh, tc, 0f, 0f, -(pg + 2f * tc), 0f, H, 0f)
        run {
            val s = face(-1f, 0f, 0f)
            if (s > 0f) {
                solid(BookTex.mul(edge, 1.2f), s)
                quadPath()
                paint.reset(); paint.isAntiAlias = true
                paint.shader = LinearGradient(dst8[0], dst8[1], dst8[2], dst8[3], intArrayOf(0x4D000000, 0x14FFFFFF, 0x4D000000), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                cv.drawPath(path, paint)
            }
        }
        // atas, bawah (sepanjang x) dan sisi kanan (sepanjang y): papan belakang + halaman. Papan depan = tepi sampul yang berputar.
        for (side in 0..2) {
            for (strip in 0..1) {
                val z0 = if (strip == 0) -pg - tc else -pg
                val dz = if (strip == 0) tc else pg
                val col = if (strip == 0) BookTex.mul(edge, 1f) else PAPER
                val s: Float
                when (side) {
                    0 -> { sc(-hw, -hh, z0, W, 0f, 0f, 0f, 0f, dz); s = face(0f, -1f, 0f) }
                    1 -> { sc(-hw, hh, z0, W, 0f, 0f, 0f, 0f, dz); s = face(0f, 1f, 0f) }
                    else -> { sc(hw, -hh, z0, 0f, 0f, dz, 0f, H, 0f); s = face(1f, 0f, 0f) }
                }
                if (s > 0f) {
                    solid(col, s)
                    if (strip == 1) for (i in 1..3) {
                        val f = i / 4f
                        when (side) {
                            0 -> line(-hw, -hh, z0 + dz * f, hw, -hh, z0 + dz * f, s)
                            1 -> line(-hw, hh, z0 + dz * f, hw, hh, z0 + dz * f, s)
                            else -> line(hw, -hh, z0 + dz * f, hw, hh, z0 + dz * f, s)
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------- 2. halaman terakhir dibuka + bayangan sampul
        if (deg > .5f) {
            if (showPage) {
                sc(-hw, -hh, 0f, W, 0f, 0f, 0f, H, 0f)
                val s = face(0f, 0f, 1f)
                if (s > 0f) {
                    roundedPath(W, H)
                    if (page == null) {
                        paint.reset(); paint.isAntiAlias = true; paint.color = BookTex.mul(0xFFFFFFFF.toInt(), s)
                        cv.drawPath(path, paint)
                    } else {
                        val bw = page.width.toFloat(); val bh = page.height.toFloat()
                        var cw = bw; var ch = cw / BookSpec.ASPECT
                        if (ch > bh) { ch = bh; cw = ch * BookSpec.ASPECT }
                        val sl = (bw - cw) / 2f
                        textured(page, sl, 0f, sl + cw, ch, s, dark)
                    }
                }
            }
            // lekuk di dekat punggung (selalu ada saat terbuka)
            sc(-hw, -hh, 0f, W * .07f, 0f, 0f, 0f, H, 0f)
            gradQuad(-hw, -hw + W * .07f, blk(.12f), blk(.04f), 0, .5f)
        }

        // ---------------------------------------------------------------- 3. papan sampul (kaku, tebal)
        val hh2 = hh
        // sisi luar (muka sampul)
        cvq(0f, -hh2, tc / 2f, W, 0f, 0f, 0f, H, 0f)
        run {
            val s = face(-st, 0f, ct)
            if (s > 0f) {
                roundedPath(W, H)
                val tex = cover?.bm
                if (tex != null) textured(tex, 0f, 0f, tex.width.toFloat(), tex.height.toFloat(), s, false)
                else { paint.reset(); paint.isAntiAlias = true; paint.color = BookTex.mul(0xFFE9E4DA.toInt(), s); cv.drawPath(path, paint) }
            }
        }
        // sisi dalam
        cvq(0f, -hh2, -tc / 2f, W, 0f, 0f, 0f, H, 0f)
        run {
            val s = face(st, 0f, -ct)
            if (s > 0f) {
                roundedPath(W, H)
                val tex = BookTex.inner()
                textured(tex, 0f, 0f, tex.width.toFloat(), tex.height.toFloat(), s, false)
            }
        }
        // tepi atas, bawah, dan tepi bebas (tebal papan terlihat)
        for (e in 0..2) {
            val s: Float
            when (e) {
                0 -> { cvq(0f, -hh2, -tc / 2f, W, 0f, 0f, 0f, 0f, tc); s = face(0f, -1f, 0f) }
                1 -> { cvq(0f, hh2, -tc / 2f, W, 0f, 0f, 0f, 0f, tc); s = face(0f, 1f, 0f) }
                else -> { cvq(W, -hh2, -tc / 2f, 0f, 0f, tc, 0f, H, 0f); s = face(ct, 0f, st) }
            }
            if (s > 0f) solid(edge, s)
        }
    }
}

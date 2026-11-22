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
//  adalah bidang di ruang 3D yang diproyeksikan dengan perspektif. Sampul = papan tebal (kaku) berengsel di punggung kiri.
//  Tekstur sampul / halaman dipetakan dengan homografi (Matrix.setPolyToPoly + BitmapShader), jadi perspektifnya benar.
//  Sisi yang membelakangi kamera dibuang (culling).
//
//  Buku TIDAK dimiringkan untuk memperlihatkan ketebalan. Muka depan selalu lurus (sejajar layar); yang bergeser adalah
//  MATA kamera (lensa off-axis: lx, ly), jadi punggung dan tepi atas buku terlihat tanpa muka depan menjadi miring / trapesium.
//  Bidang halaman (z = 0) selalu memetakan identik ke kotak (l, t, r, b), berapa pun lx / ly, sehingga bingkai zoom tetap sejajar.
//
//  Gaya: material tonal datar (bukan bayangan lembut). Kedalaman datang dari ketebalan nyata, sambungan punggung, garis halaman,
//  dan gradasi tonal tipis per sisi.
//  Dipakai oleh kartu buku di beranda (pose diam) DAN lapisan animasi (pose + sudut sampul berubah), jadi keduanya identik.
// =====================================================================================

private fun blk(a: Float): Int = (a * 255f).roundToInt().coerceIn(0, 255) shl 24
private fun wht(a: Float): Int = ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF

/** Tekstur muka sampul + warna tepi (nada tonal gelap dari warna dominan sampul). */
class CoverTex(val bm: Bitmap, val edge: Int)

object BookTex {
    private val cache = object : LruCache<String, CoverTex>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CoverTex): Int = value.bm.byteCount
    }
    private var innerBm: Bitmap? = null
    private var plainTex: CoverTex? = null

    private fun key(d: Note) = "${d.cv}|${d.cx}|${d.cy}|${d.cz}"

    /** Tekstur yang sudah jadi (null bila belum dibuat). */
    fun peek(d: Note): CoverTex? = cache.get(key(d))

    /** Buang semua tekstur milik satu berkas sampul (dipanggil saat sampul diganti / dikembalikan). */
    fun evict(cv: String) {
        if (cv.isEmpty()) return
        val pre = "$cv|"
        for (k in cache.snapshot().keys) if (k.startsWith(pre)) cache.remove(k)
    }

    /** Tekstur polos cadangan (satu salinan untuk seluruh app). Murah dipakai berulang; tidak pernah dibuat per frame. */
    fun plain(): CoverTex {
        plainTex?.let { return it }
        val t = build(null, .5f, .5f, 1f)
        plainTex = t
        return t
    }

    /**
     * Pastikan tekstur ada. Aman dipanggil dari thread latar (setelah Imgs.load). Bila gambar sampul belum ada di cache,
     * hasilnya tekstur polos cadangan yang TIDAK disimpan (supaya nanti dibuat ulang dengan gambar asli).
     * JANGAN dipanggil dari fase gambar: membuat tekstur memakan beberapa milidetik.
     */
    fun ensure(d: Note): CoverTex {
        val k = key(d)
        cache.get(k)?.let { return it }
        if (d.cv.isEmpty()) {
            val p = plain()
            cache.put(k, p)
            return p
        }
        val src = try { Imgs.peek(d.cv)?.asAndroidBitmap() } catch (e: Throwable) { null }
        if (src == null) return plain()
        val t = build(src, d.cx, d.cy, d.cz)
        cache.put(k, t)
        return t
    }

    /** Nada tonal untuk tepi / punggung: lebih gelap dari sampul, warna tetap sama (gaya tonal, bukan abu-abu kusam). */
    private fun tone(px: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(px, hsv)
        hsv[1] = (hsv[1] * 1.30f + .06f).coerceAtMost(1f)   // jenuh: warna 'bibir' tebal ala tombol kartun
        hsv[2] = (hsv[2] * .66f).coerceIn(.28f, .68f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun build(src0: Bitmap?, cx: Float, cy: Float, cz: Float): CoverTex {
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
                val s = max(tw / iw, th / ih) * cz.coerceAtLeast(1f)
                val vw = tw / s; val vh = th / s
                val sx = (iw - vw) * cx.coerceIn(0f, 1f); val sy = (ih - vh) * cy.coerceIn(0f, 1f)
                c.drawBitmap(
                    src,
                    android.graphics.Rect(sx.roundToInt(), sy.roundToInt(), min(src.width, (sx + vw).roundToInt()), min(src.height, (sy + vh).roundToInt())),
                    android.graphics.Rect(0, 0, tw, th), p
                )
            }
        }
        val w = tw.toFloat(); val h = th.toFloat()
        // gaya kartun datar: tanpa gradasi. Satu pita engsel solid tipis di kiri + satu garis terang di sebelahnya
        p.reset(); p.color = blk(.16f); c.drawRect(0f, 0f, w * .07f, h, p)
        p.reset(); p.color = wht(.22f); c.drawRect(w * .07f, 0f, w * .07f + max(1.5f, w * .008f), h, p)
        // gaya tombol kartun: pita terang datar di tepi atas (sorot) dan pita gelap datar di tepi bawah ('bibir' tebal). Keduanya solid, tanpa gradasi
        p.reset(); p.color = wht(.28f); c.drawRect(w * .12f, h * .024f, w * .93f, h * .024f + max(2.5f, h * .020f), p)
        p.reset(); p.color = blk(.22f); c.drawRect(0f, h * .94f, w, h, p)
        p.reset(); p.color = wht(.28f); c.drawRect(w * .945f, h * .024f, w * .945f + max(2.5f, h * .020f), h * .024f + max(2.5f, h * .020f), p)
        return CoverTex(out, tone(dominant(out)))
    }

    /**
     * Warna dominan sampul untuk tepi / punggung. DULU: skala langsung ke 1x1 (hanya mencuplik segelintir piksel, warna punggung sering meleset
     * dan berganti-ganti antar sampul mirip). SEKARANG: rata-rata area seluruh sampul (tanpa alur engsel di kiri), dibobot kejenuhan supaya
     * warna yang hidup menang atas putih / abu-abu latar.
     */
    private fun dominant(b: Bitmap): Int {
        val gw = 24; val gh = 34
        val s = try { Bitmap.createScaledBitmap(b, gw, gh, true) } catch (e: Throwable) { return 0xFF6B5A48.toInt() }
        val a = IntArray(gw * gh)
        s.getPixels(a, 0, gw, 0, 0, gw, gh)
        var sr = 0f; var sg = 0f; var sb = 0f; var sw = 0f
        val hsv = FloatArray(3)
        for (y in 1 until gh - 1) for (x in 3 until gw - 1) {
            val p = a[y * gw + x]
            android.graphics.Color.colorToHSV(p, hsv)
            val wgt = .15f + hsv[1] * (1f - kotlin.math.abs(hsv[2] - .5f))
            sr += (p shr 16 and 255) * wgt; sg += (p shr 8 and 255) * wgt; sb += (p and 255) * wgt; sw += wgt
        }
        if (s !== b) s.recycle()
        if (sw <= 0f) return 0xFF6B5A48.toInt()
        return (0xFF shl 24) or ((sr / sw).roundToInt().coerceIn(0, 255) shl 16) or ((sg / sw).roundToInt().coerceIn(0, 255) shl 8) or (sb / sw).roundToInt().coerceIn(0, 255)
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
        // lipatan engsel: garis terang tipis di sebelah garis gelap
        p.reset(); p.color = blk(.16f); c.drawRect(w * .095f, 0f, w * .095f + 2f, h.toFloat(), p)
        p.reset(); p.color = wht(.40f); c.drawRect(w * .095f + 2f, 0f, w * .095f + 4f, h.toFloat(), p)
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
        BookFx.pageTex = s; BookFx.pageTexSrc = bmp
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
    private const val LIT_FRONT = .697f                         // terang muka yang menghadap lurus ke kamera
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val mat = Matrix()
    private val src8 = FloatArray(8)
    private val dst8 = FloatArray(8)
    private val po = FloatArray(3)   // titik terakhir setelah pose (x, y, z)
    private val ps = FloatArray(2)   // titik terakhir di layar
    private val q = FloatArray(12)   // 4 sudut bidang (O, O+U, O+U+V, O+V)
    private val INV = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )))
    private const val PAPER = 0xFFFBF8F1.toInt()
    private const val INNER_FLAT = 0xFFEFE8D8.toInt()
    private const val PAGE_LINE_A = 0x26000000
    private const val PAGE_LINE_B = 0x12000000
    private const val GROOVE = 0x59000000
    private const val GLINT = 0x40FFFFFF

    /**
     * Gambar buku di kotak (l,t,r,b) = bidang depan buku di layar (px).
     * yaw / pitch (derajat) = putaran buku (hanya dipakai untuk goyangan sangat kecil; pose diam = 0, buku lurus);
     * k = skala; deg = sudut buka sampul (0 tertutup, 90 tegak lurus, 180 sejajar halaman, ~191.7 = rebah di bidang papan belakang; lihat BookSpec.COVER_DEG).
     * lx / ly = pergeseran mata kamera (x lebar buku): lx > 0 mata di kiri (punggung terlihat), ly > 0 mata di atas (tepi atas terlihat).
     * Muka depan tetap lurus; 0 = kamera tepat di depan.
     * cam = jarak kamera (x lebar buku). Bidang halaman (z = 0) tetap pas di kotaknya berapa pun cam; yang berubah adalah kedalaman:
     * makin kecil cam, makin kuat perspektif, dan benda di bawah bidang halaman (sampul rebah, punggung, tebal blok) makin menjauh / menyempit.
     * page = bitmap halaman yang dibuka terakhir (null = kertas putih); showPage = gambar halaman di balik sampul.
     */
    fun draw(
        cv: android.graphics.Canvas, l: Float, t: Float, r: Float, b: Float,
        yaw: Float, pitch: Float, k: Float, deg: Float,
        cover: CoverTex?, page: Bitmap?, showPage: Boolean, dark: Boolean,
        lx: Float = 0f, ly: Float = 0f, cam: Float = BookSpec.CAMERA
    ) {
        val W = r - l
        val H = b - t
        if (W < 2f || H < 2f) return
        val hw = W / 2f
        val hh = H / 2f
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val D = cam * W                  // jarak kamera (x lebar buku); mengecil selama zoom = kamera mendekat (lihat bookCamera)
        val tc = BookSpec.BOARD * W      // tebal papan sampul
        val pg = BookSpec.PAGES * W      // tebal blok halaman
        val ex = -lx * W                 // posisi mata kamera (relatif pusat buku, px)
        val ey = -ly * W
        val ry = Math.toRadians(yaw.toDouble()); val rp = Math.toRadians(pitch.toDouble())
        val cyw = cos(ry).toFloat(); val syw = sin(ry).toFloat()
        val cp = cos(rp).toFloat(); val sp = sin(rp).toFloat()
        val th = Math.toRadians(deg.toDouble())
        val ct = cos(th).toFloat(); val st = sin(th).toFloat()
        val rL = .008f * W   // sudut membulat secukupnya (kiri = punggung hampir siku, kanan sedikit lebih bulat)
        val rR = .014f * W
        val edge = cover?.edge ?: 0xFF6B5A48.toInt()
        val pageLine = (BookTex.mul(edge, .5f) and 0xFFFFFF) or (0x80 shl 24)

        fun pt(x: Float, y: Float, z: Float) {
            val xs = x * k; val ys = y * k; val zs = z * k
            val x1 = xs * cyw + zs * syw
            val z1 = -xs * syw + zs * cyw
            val y2 = ys * cp - z1 * sp
            val z2 = ys * sp + z1 * cp
            po[0] = x1; po[1] = y2; po[2] = z2
            val s = D / max(D * .15f, D - z2)
            // lensa off-axis: bidang z = 0 (s = 1) selalu identik dengan kotak, kedalaman menyusut ke arah mata
            ps[0] = cx + ex + (x1 - ex) * s
            ps[1] = cy + ey + (y2 - ey) * s
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

        /** Proyeksikan q ke dst8 dan hitung terang bidang. -1 = membelakangi kamera (dibuang). Muka yang menghadap lurus = 1 (tanpa digelapkan). */
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
            if (n1x * (ex - fx) + n2y * (ey - fy) + n2z * (D - fz) <= 0f) return -1f
            val lit = n1x * LIGHT[0] + n2y * LIGHT[1] + n2z * LIGHT[2]
            return .80f + .20f * (max(0f, lit) / LIT_FRONT).coerceAtMost(1f)
        }

        fun quadPath() {
            path.reset()
            path.moveTo(dst8[0], dst8[1]); path.lineTo(dst8[2], dst8[3]); path.lineTo(dst8[4], dst8[5]); path.lineTo(dst8[6], dst8[7]); path.close()
        }
        // isi + garis tipis berwarna sama: menutup celah anti-alias di sambungan antar sisi (buku terlihat satu benda padat)
        // garis tinta tipis berwarna gelap (turunan warna sampul) di tepi bidang: tiap bagian buku terbaca tegas seperti ilustrasi kartun
        fun ink() {
            paint.reset(); paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = max(1.4f, W * .011f); paint.color = BookTex.mul(edge, .42f)
            cv.drawPath(path, paint)
        }
        fun solid(color: Int, shade: Float) {
            quadPath()
            paint.reset(); paint.isAntiAlias = true
            paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = .8f; paint.strokeJoin = Paint.Join.ROUND
            paint.color = BookTex.mul(color, shade)
            cv.drawPath(path, paint)
            ink()
        }
        // gradasi tonal dua warna sepanjang V (titik 0 -> 3) atau U (titik 0 -> 1) bidang q
        fun solidGrad(c0: Int, c1: Int, useV: Boolean, shade: Float) {
            quadPath()
            val e = if (useV) 6 else 2
            paint.reset(); paint.isAntiAlias = true
            paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = .8f; paint.strokeJoin = Paint.Join.ROUND
            paint.shader = LinearGradient(
                dst8[0], dst8[1], dst8[e], dst8[e + 1],
                BookTex.mul(c0, shade), BookTex.mul(c1, shade), Shader.TileMode.CLAMP
            )
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
        fun line(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int, wMul: Float) {
            pt(x0, y0, z0); val ax = ps[0]; val ay = ps[1]
            pt(x1, y1, z1)
            paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, W * .004f * wMul); paint.color = color
            cv.drawLine(ax, ay, ps[0], ps[1], paint)
        }

        // ---------------------------------------------------------------- 1. halaman terakhir dibuka (digambar PALING DULU)
        // Halaman (z = 0) harus di bawah punggung: pita depan punggung (z 0..tebal papan) tampak sedikit di kiri tepi kiri halaman karena mata kamera
        // di kiri. Kalau halaman digambar SETELAH punggung (urutan lama), halaman putih menimpa pita itu dan muncul garis putih tipis di sambungan
        // punggung dan sampul, hanya saat sampul mulai terbuka / hampir tertutup (deg > .5), jadi tampak berkedip.
        if (deg > .5f) {
            if (showPage) {
                sc(-hw, -hh, 0f, W, 0f, 0f, 0f, H, 0f)
                val s = face(0f, 0f, 1f)
                if (s > 0f) {
                    roundedPath(W, H)
                    if (page == null) {
                        paint.reset(); paint.isAntiAlias = true; paint.color = 0xFFFFFFFF.toInt()
                        cv.drawPath(path, paint)
                    } else {
                        val bw = page.width.toFloat(); val bh = page.height.toFloat()
                        var cw = bw; var ch = cw / BookSpec.ASPECT
                        if (ch > bh) { ch = bh; cw = ch * BookSpec.ASPECT }
                        val sl = (bw - cw) / 2f
                        // kertas tidak digelapkan (shade = 1): kecerahannya sama dengan BookPageLayer yang mengambil alih saat zoom
                        textured(page, sl, 0f, sl + cw, ch, 1f, dark)
                    }
                }
            }
        }

        // ---------------------------------------------------------------- 2. punggung dan tepi blok halaman (diam)
        // saat zoom halaman digambar BookPageLayer (bukan di sini): punggung / tepi tidak boleh menimpa isi kertas di dalam kotak halaman
        val cut = !showPage && deg > .5f
        val cutSave = if (cut) cv.save() else -1
        if (cut) {
            if (android.os.Build.VERSION.SDK_INT >= 26) cv.clipOutRect(l, t, r, b)
            else cv.clipRect(l, t, r, b, android.graphics.Region.Op.DIFFERENCE)
        }
        // punggung: satu bidang warna solid + dua pita datar (ciri buku bersampul keras). Tanpa gradasi, tanpa alur, tanpa sorot
        sc(-hw, -hh, tc, 0f, 0f, -(pg + 2f * tc), 0f, H, 0f)
        run {
            val s = face(-1f, 0f, 0f)
            if (s > 0f) {
                solid(edge, 1f)
                val b1 = -hh + H * .09f; val b2 = hh - H * .09f
                val bc = BookTex.mul(edge, 1.55f)
                line(-hw, b1, tc, -hw, b1, -pg - tc, bc, 3f)
                line(-hw, b2, tc, -hw, b2, -pg - tc, bc, 3f)
            }
        }
        // atas, bawah (sepanjang x) dan sisi kanan (sepanjang y): papan belakang + halaman. Bidang LURUS (geometri asli). Warna solid per sisi + garis tinta.
        // (Prisma bersudut membulat dicoba dan DIBATALKAN: sudut kanan-atasnya berujung runcing karena lensa miring, dan hasilnya tidak nyambung dengan engsel.)
        for (side in 0..2) {
            for (strip in 0..1) {
                val z0 = if (strip == 0) -pg - tc else -pg
                val dz = if (strip == 0) tc else pg
                val s: Float
                when (side) {
                    0 -> { sc(-hw, -hh, z0, W, 0f, 0f, 0f, 0f, dz); s = face(0f, -1f, 0f) }
                    1 -> { sc(-hw, hh, z0, W, 0f, 0f, 0f, 0f, dz); s = face(0f, 1f, 0f) }
                    else -> { sc(hw, -hh, z0, 0f, 0f, dz, 0f, H, 0f); s = face(1f, 0f, 0f) }
                }
                if (s > 0f) {
                    val sh = when (side) { 0 -> 1.30f; 1 -> .62f; else -> .86f }
                    if (strip == 0) {
                        solid(BookTex.mul(edge, .92f), sh)
                    } else {
                        // blok halaman: kertas datar dengan hanya dua garis halaman tegas
                        solid(PAPER, sh)
                        for (i in 1..2) {
                            val f = i / 3f
                            when (side) {
                                0 -> line(-hw, -hh, z0 + dz * f, hw, -hh, z0 + dz * f, pageLine, 1.5f)
                                1 -> line(-hw, hh, z0 + dz * f, hw, hh, z0 + dz * f, pageLine, 1.5f)
                                else -> line(hw, -hh, z0 + dz * f, hw, hh, z0 + dz * f, pageLine, 1.5f)
                            }
                        }
                    }
                }
            }
        }

        if (cutSave >= 0) cv.restoreToCount(cutSave)

        // ---------------------------------------------------------------- 3. papan sampul (kaku, tebal)
        val hh2 = hh
        // sisi luar (muka sampul): datar, kecerahan tetap (gaya kartun: tanpa shading menerus, tanpa kilau, tanpa lapisan hiasan)
        cvq(0f, -hh2, tc / 2f, W, 0f, 0f, 0f, H, 0f)
        run {
            val s = face(-st, 0f, ct)
            if (s > 0f) {
                roundedPath(W, H)
                val tex = cover?.bm
                if (tex != null) textured(tex, 0f, 0f, tex.width.toFloat(), tex.height.toFloat(), 1f, false)
                else { paint.reset(); paint.isAntiAlias = true; paint.color = 0xFFE9E4DA.toInt(); cv.drawPath(path, paint) }
                ink()
            }
        }
        // sisi dalam: satu warna krem datar
        cvq(0f, -hh2, -tc / 2f, W, 0f, 0f, 0f, H, 0f)
        run {
            val s = face(st, 0f, -ct)
            if (s > 0f) {
                roundedPath(W, H)
                paint.reset(); paint.isAntiAlias = true
                paint.color = INNER_FLAT
                cv.drawPath(path, paint)
                ink()
            }
        }
        // tepi atas, bawah, dan tepi bebas (tebal papan terlihat): satu warna solid per sisi + garis tinta
        for (e in 0..2) {
            val s: Float
            when (e) {
                0 -> { cvq(0f, -hh2, -tc / 2f, W, 0f, 0f, 0f, 0f, tc); s = face(0f, -1f, 0f) }
                1 -> { cvq(0f, hh2, -tc / 2f, W, 0f, 0f, 0f, 0f, tc); s = face(0f, 1f, 0f) }
                else -> { cvq(W, -hh2, -tc / 2f, 0f, 0f, tc, 0f, H, 0f); s = face(ct, 0f, st) }
            }
            if (s > 0f) solid(edge, when (e) { 0 -> 1.30f; 1 -> .62f; else -> .86f })
        }
    }
}

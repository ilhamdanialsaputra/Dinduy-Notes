package app.dinduy.silkcurl

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.opengl.EGL14
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================================================
//  Silk curl: efek balik halaman gaya Google Play Books.
//
//  Mesin kurva, shader, bayangan lipatan, drag / fling / settlement SEMUA kode asli PlayLikeCurl
//  (Arnab11/PlayLikeCurl, fork dari karankalsi/PlayLikeCurl, lisensi MIT; teks lisensi ada di header tiap
//  file .java di paket ini dan di licenses/PlayLikeCurl-LICENSE.txt). File ini hanya "lem" antara
//  mesin itu dan Compose / PdfCache: tidak ada satu pun rumus kurva di sini.
//
//  Cara kerja (production bitmap API v1):
//   - Klien menyuplai deck 3 halaman (sebelumnya, sekarang, berikutnya) berupa Bitmap ARGB_8888 buram.
//   - Bitmap harus tetap utuh sampai callback onDeckReleased. Bitmap milik PdfCache TIDAK PERNAH di-recycle di sini.
//   - Saat settlement dimulai, deck untuk halaman tujuan langsung disuplai (jadi "pending") dan diaktifkan
//     mesin tepat saat settlement selesai, tanpa kedip halaman lama.
//   - Geser jari diteruskan sebagai MotionEvent ke PageSurfaceView.onPageTouchEvent; ketukan tepi memakai turn().
// =====================================================================================

/**
 * Satu halaman siap-GL. [bitmap] ditampilkan APA ADANYA: mode gelap (invert) dan coretan harus sudah dibakar ke salinan
 * sebelum dibungkus (lihat [silkInverted]); [preInverted] hanya penanda, mesin asli tidak punya shader invert.
 */
class SilkPage(val bitmap: Bitmap, val preInverted: Boolean = false) {
    internal var glBitmap: Bitmap? = null
    internal var glMax = 0
}

/** Salinan bitmap dengan warna dibalik, rumus sama persis dengan INVERT di Pdf.kt (c * -0.88 + 224). Bitmap sumber tidak diubah. */
fun silkInverted(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val p = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -.88f, 0f, 0f, 0f, 224f,
                    0f, -.88f, 0f, 0f, 224f,
                    0f, 0f, -.88f, 0f, 224f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    Canvas(out).drawBitmap(src, 0f, 0f, p)
    out.setHasAlpha(false)
    return out
}

object SilkGl {
    @Volatile var failed = false
    private var probed: Boolean? = null

    /** GLES 2.0 ada dan EGL bisa memilih konfigurasi yang sama dengan yang diminta PageSurfaceView (RGBA8888 + depth 16). Hasil di-cache. */
    fun supported(ctx: Context): Boolean {
        if (failed) return false
        probed?.let { return it }
        val ok = try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.deviceConfigurationInfo.reqGlEsVersion >= 0x20000 && probeEgl()
        } catch (t: Throwable) { false }
        probed = ok
        return ok
    }

    private fun probeEgl(): Boolean {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (dpy == EGL14.EGL_NO_DISPLAY) return false
        val v = IntArray(2)
        if (!EGL14.eglInitialize(dpy, v, 0, v, 1)) return false
        val attr = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_NONE
        )
        val cfg = arrayOfNulls<android.opengl.EGLConfig>(1)
        val n = IntArray(1)
        // display TIDAK di-terminate: dipakai bersama oleh GLSurfaceView
        return EGL14.eglChooseConfig(dpy, attr, 0, cfg, 0, 1, n, 0) && n[0] > 0
    }
}

/**
 * PageSurfaceView yang tidak menelan sentuhan sendiri: Compose (pinch, ketuk, cari, coretan) tetap pemilik gestur, dan hanya geseran
 * halaman yang valid diteruskan lewat onPageTouchEvent(event, gestureId).
 */
internal class SilkSurfaceView(context: Context) : PageSurfaceView(context) {
    init {
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

private const val GPU_BUDGET_BYTES = 256L * 1024L * 1024L   // aktif + pengganti (default mesin 128 MB terlalu sempit untuk halaman 2400 px)
private const val CAMERA_SPAN = 1.6568542f                    // 4 * tan(22.5 derajat): tinggi dunia yang terlihat kamera mesin (fov 45, jarak 2)

/**
 * Pegangan dari sisi Compose ke mesin PlayLikeCurl. Semua method dipanggil dari thread utama.
 * [onFail] dipanggil (thread utama) sekali bila GL gagal dibuat / dipakai: pemanggil jatuh ke Classic curl.
 * [onPage] dipanggil (thread utama) setelah balikan selesai, dengan indeks halaman baru.
 */
class SilkController(private val onFail: () -> Unit) {
    /** true setelah deck pertama selesai di-upload (pemanggil boleh membuka penutup). */
    var ready by mutableStateOf(false)
        private set
    var settling by mutableStateOf(false)
        private set
    var touching by mutableStateOf(false)
        private set
    val busy: Boolean get() = settling || touching
    var onPage: (Int) -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private var view: SilkSurfaceView? = null
    private var caps: RenderCapabilities? = null
    private var failed = false
    private var visibleFlag = true

    private var count = 0
    private var curOrd = 0          // halaman yang sedang dibaca menurut mesin (dipegang controller, bukan Compose)
    private var lastCompose = -1
    private var win: Array<SilkPage?> = arrayOfNulls(5)   // bitmap halaman winCur-2 .. winCur+2
    private var winCur = 0
    private var dark = false
    private var nextGen = 1L
    private var lastSig: Sig? = null
    private var blank: Bitmap? = null
    private var blankDark = false
    private var gid = 0L
    private var downX = 0f
    private var downTime = 0L

    private data class Sig(val ord: Int, val prev: SilkPage?, val cur: SilkPage?, val next: SilkPage?, val dark: Boolean)

    private val listener = object : PageSurfaceListener {
        override fun onCapabilitiesAvailable(capabilities: RenderCapabilities) {
            caps = capabilities
            flush()
        }

        override fun onDeckPrepared(generationId: Long) {
            if (!ready) main.postDelayed({ ready = true }, 40)
        }

        override fun onDeckRejected(generationId: Long, reason: DeckRejectionReason) {
            // SESSION_DETACHED / CAPABILITIES_UNAVAILABLE: dicoba lagi saat attach / capabilities tiba (lastSig di-reset, tanpa loop)
            lastSig = null
            if (reason == DeckRejectionReason.INVALID_CONTENT) fail()
        }

        override fun onRenderFailure(failure: RenderFailure) {
            fail()
        }

        override fun onGestureRejected(gestureId: Long, generationId: Long, reason: GestureRejectionReason) {
            touching = false
        }

        override fun onGestureCancelled(gestureId: Long, generationId: Long) {
            touching = false
        }

        override fun onSettlementStarted(
            gestureId: Long, generationId: Long, sourceLogicalPageId: String, targetLogicalPageId: String, pageChange: PageChange
        ) {
            settling = true
            if (pageChange == PageChange.NONE) return
            // deck halaman tujuan disuplai SEKARANG (jadi pending): mesin mengaktifkannya tepat saat settlement selesai
            val t = ordOf(targetLogicalPageId) ?: if (pageChange == PageChange.NEXT) curOrd + 1 else curOrd - 1
            if (t in 0 until count) submit(t)
        }

        override fun onSettlementCompleted(
            gestureId: Long, generationId: Long, currentLogicalPageId: String, currentOrdinal: Int, pageChange: PageChange
        ) {
            settling = false
            touching = false
            if (pageChange != PageChange.NONE) {
                curOrd = currentOrdinal
                onPage(currentOrdinal)
            }
            flush()
        }

        override fun onSettlementCancelled(gestureId: Long, generationId: Long, currentLogicalPageId: String) {
            settling = false
            touching = false
            lastSig = null
            flush()
        }
    }

    internal fun bind(v: SilkSurfaceView) {
        view = v
        try {
            v.setGpuBudgetBytes(GPU_BUDGET_BYTES)   // harus sebelum deck pertama
            v.setPageSurfaceListener(listener)
        } catch (t: Throwable) {
            fail()
        }
    }

    internal fun onResume() {
        try {
            view?.attach()
            view?.setVisible(visibleFlag)
        } catch (t: Throwable) {
            fail()
        }
        flush()
    }

    internal fun onPause() {
        try { view?.detach() } catch (t: Throwable) { }
    }

    internal fun setVisible(visible: Boolean) {
        visibleFlag = visible
        try { view?.setVisible(visible) } catch (t: Throwable) { }
    }

    fun release() {
        val v = view
        view = null
        main.removeCallbacksAndMessages(null)
        try { v?.dispose() } catch (t: Throwable) { }
    }

    internal fun fail() {
        if (failed) return
        failed = true
        SilkGl.failed = true
        main.post(onFail)
    }

    /**
     * Sinkronkan dengan state Compose. [cur] = halaman yang dibaca menurut Compose; [pages] = bitmap halaman cur-2 .. cur+2 (null = belum siap / di luar
     * rentang). Perubahan [cur] yang BUKAN berasal dari balikan mesin (loncat dari pencarian / kapsul / halaman terakhir) membatalkan balikan yang
     * sedang jalan dan menyuplai deck baru.
     */
    fun sync(cur: Int, count: Int, pages: Array<SilkPage?>, dark: Boolean) {
        this.count = count
        win = pages
        winCur = cur
        this.dark = dark
        if (cur != lastCompose) {
            lastCompose = cur
            if (cur != curOrd) {
                curOrd = cur
                if (busy) {
                    try { view?.cancelGesture() } catch (t: Throwable) { }
                    settling = false
                    touching = false
                    lastSig = null
                }
            }
        }
        flush()
    }

    /** Ketukan tepi: memicu turn(PageChange) pada mesin. false bila tak bisa (di ujung buku, sibuk, deck belum siap). */
    fun turn(next: Boolean): Boolean {
        val v = view ?: return false
        if (busy || failed) return false
        if (next && curOrd + 1 >= count) return false
        if (!next && curOrd <= 0) return false
        return try { v.turn(if (next) PageChange.NEXT else PageChange.PREVIOUS) } catch (t: Throwable) { false }
    }

    /** Awal geseran (dir 1 = maju / geser ke kiri, -1 = mundur). Koordinat bebas asal konsisten (hanya selisih yang dipakai mesin). */
    fun dragBegin(dir: Int, x: Float, y: Float, t: Long): Boolean {
        if (view == null || busy || failed) return false
        if (dir > 0 && curOrd + 1 >= count) return false
        if (dir < 0 && curOrd <= 0) return false
        gid++
        downX = x
        downTime = t
        touching = true
        send(MotionEvent.ACTION_DOWN, x, y, t)
        return true
    }

    fun dragMove(x: Float, y: Float, t: Long) {
        if (!touching) return
        send(MotionEvent.ACTION_MOVE, clampX(x), y, t)
    }

    fun dragEnd(x: Float, y: Float, t: Long, cancelled: Boolean) {
        if (!touching) return
        send(if (cancelled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, clampX(x), y, t)
        touching = false   // bila settlement mulai, `settling` sudah true lewat callback (sinkron)
    }

    /** Di ujung buku mesin tak boleh diajak membalik ke halaman yang tak ada: jari tak boleh melewati titik sentuh awal ke arah itu. */
    private fun clampX(x: Float): Float {
        var xx = x
        if (curOrd <= 0) xx = min(xx, downX)
        if (curOrd + 1 >= count) xx = max(xx, downX)
        return xx
    }

    private fun send(action: Int, x: Float, y: Float, t: Long) {
        val v = view ?: return
        val ev = MotionEvent.obtain(downTime, t, action, x, y, 0)
        try {
            v.onPageTouchEvent(ev, gid)
        } catch (e: Throwable) {
            fail()
        } finally {
            ev.recycle()
        }
    }

    private fun pageAt(i: Int): SilkPage? {
        if (i !in 0 until count) return null
        val k = i - winCur + 2
        return if (k in 0..4) win[k] else null
    }

    private fun sigFor(o: Int) = Sig(o, pageAt(max(o - 1, 0)), pageAt(o), pageAt(min(o + 1, count - 1)), dark)

    /** Suplai deck untuk halaman [curOrd] bila isinya berubah. Tidak menyentuh mesin saat jari / settlement aktif (deck baru akan mereset model kurva). */
    private fun flush() {
        if (view == null || failed || caps == null || busy) return
        val o = curOrd
        if (o !in 0 until count || pageAt(o) == null) return
        if (sigFor(o) == lastSig) return
        submit(o)
    }

    private fun submit(o: Int) {
        val v = view ?: return
        val c = caps ?: return
        val sg = sigFor(o)
        val cur = sg.cur ?: return
        val gen = nextGen++
        try {
            val cb = glBitmap(cur, c.maxTextureSize)
            val pb = sg.prev?.let { glBitmap(it, c.maxTextureSize) } ?: blankLike(cb)
            val nb = sg.next?.let { glBitmap(it, c.maxTextureSize) } ?: blankLike(cb)
            val po = max(o - 1, 0)
            val no = min(o + 1, count - 1)
            val deck = PortraitPageDeck<Bitmap>(img(gen, po, pb), img(gen, o, cb), img(gen, no, nb))
            lastSig = sg
            v.submitDeck(deck)
        } catch (t: Throwable) {
            fail()
        }
    }

    private fun img(gen: Long, ord: Int, b: Bitmap) = PageImage<Bitmap>(gen, "silk-$ord", ord, b.width, b.height, b)

    private fun ordOf(id: String): Int? = id.removePrefix("silk-").toIntOrNull()

    /**
     * Bitmap yang dikirim ke mesin: mesin menolak bitmap yang punya alpha atau melebihi batas tekstur. Bitmap sumber (PdfCache) dipakai apa adanya,
     * hanya penanda alpha-nya dimatikan (semua piksel memang buram; isi piksel tidak diubah dan tidak di-recycle). Yang terlalu besar diganti salinan terkecil.
     */
    private fun glBitmap(sp: SilkPage, maxTex: Int): Bitmap {
        sp.glBitmap?.let { if (sp.glMax == maxTex && !it.isRecycled) return it }
        var b = sp.bitmap
        check(!b.isRecycled) { "silk bitmap recycled" }
        val big = max(b.width, b.height)
        if (big > maxTex) {
            val s = maxTex / big.toFloat()
            b = Bitmap.createScaledBitmap(b, max(1, (b.width * s).toInt()), max(1, (b.height * s).toInt()), true)
        }
        if (b.config != Bitmap.Config.ARGB_8888) b = b.copy(Bitmap.Config.ARGB_8888, false)
        if (b.hasAlpha()) b.setHasAlpha(false)
        sp.glBitmap = b
        sp.glMax = maxTex
        return b
    }

    /** Pengganti halaman tetangga yang belum selesai dirender: putih (hitam di mode gelap, sama dengan hasil invert halaman putih). */
    private fun blankLike(ref: Bitmap): Bitmap {
        val old = blank
        if (old != null && !old.isRecycled && old.width == ref.width && old.height == ref.height && blankDark == dark) return old
        val b = Bitmap.createBitmap(ref.width, ref.height, Bitmap.Config.ARGB_8888)
        b.eraseColor(if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        b.setHasAlpha(false)
        blank = b
        blankDark = dark
        return b
    }
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}

/**
 * PageSurfaceView di dalam Compose. Kamera mesin tetap (fov 45, jarak 2), jadi halaman diam hanya mengisi persis persegi
 * ([pageLeft], [pageTop], [pageWidth], [pageHeight]) bila lebar view = lebar halaman dan tinggi view = 1.6569 x lebar halaman, dengan pusat yang sama.
 * View ditempatkan begitu; margin atas / bawah (tempat lembar menggembung) hitam. Satuan px, koordinat induk. Lebar <= 0 = isi induk apa adanya.
 * onPause / onResume mengikuti lifecycle; resource dibebaskan saat keluar komposisi.
 */
@Composable
fun SilkGlView(
    controller: SilkController, visible: Boolean, modifier: Modifier = Modifier,
    pageLeft: Float = 0f, pageTop: Float = 0f, pageWidth: Float = 0f, pageHeight: Float = 0f
) {
    val ctx = LocalContext.current
    val owner = remember(ctx) { ctx.findLifecycleOwner() }
    AndroidView(
        factory = { c ->
            SilkSurfaceView(c).also { controller.bind(it) }
        },
        modifier = modifier.layout { measurable, constraints ->
            val pw = ceilPx(pageWidth)
            if (pw <= 0) {
                val p = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, 0) }
            } else {
                // Lebar viewport = lebar halaman PERSIS: di posisi diam mesin juga menggambar halaman sebelumnya yang menjorok ke kiri (x -0.79..0.03), dan
                // lembar yang dibalik keluar ke kiri; keduanya harus terjepit di tepi halaman (sama seperti scissor di kode lama). Tinggi = 1.6569 x lebar
                // (potret, sesuai syarat mesin) membuat skala piksel 1:1 dengan persegi halaman; sisa tinggi = margin atas / bawah tempat lembar menggembung.
                val wv = pw
                val hv = (CAMERA_SPAN * pageWidth).roundToInt().coerceAtLeast(wv + 1)
                val cx = pageLeft + pageWidth / 2f
                val cy = pageTop + pageHeight / 2f
                val p = measurable.measure(Constraints.fixed(wv, hv))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    p.place((cx - wv / 2f).roundToInt(), (cy - hv / 2f).roundToInt())
                }
            }
        },
        update = {
            it.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            controller.setVisible(visible)
        }
    )
    DisposableEffect(owner, controller) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> controller.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.onPause()
                else -> {}
            }
        }
        owner?.lifecycle?.addObserver(obs)   // pengamat baru langsung dikejar ke state saat ini (ON_RESUME bila sudah resumed)
        if (owner == null) controller.onResume()
        onDispose {
            owner?.lifecycle?.removeObserver(obs)
            controller.release()
        }
    }
}

private fun ceilPx(v: Float): Int = if (v > 0f) v.roundToInt().coerceAtLeast(1) else 0

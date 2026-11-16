package app.dinduy.silkcurl

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// =====================================================================================
//  Silk curl: balik halaman melengkung halus (OpenGL ES 2.0), ditulis sendiri, tanpa dependency baru.
//
//  Model: selembar kertas dengan tulang punggung di kiri. Bagian yang terangkat adalah kurva yang dibentuk dengan
//  mengintegralkan sudut tangen theta(s) = thetaEnd * (1 - (1 - u)^P): lengkungan paling rapat dekat titik lepas,
//  makin ke ujung makin lurus, jadi kertas melengkung lembut, bukan digulung jadi silinder rapat. Setelah lengkungan
//  melewati kira-kira separuh halaman, seluruh lembar ikut meluncur ke kiri sampai keluar layar.
//  Satu parameter t (0 = datar, 1 = sudah lewat) menentukan semuanya; x terjauh kurva (siluet) dipakai sebagai penggerak
//  yang bisa dibalik (tFor), jadi tepi halaman mengikuti jari secara linear.
// =====================================================================================

/** Satu halaman siap-GL. [preInverted] true = warna gelap sudah dibakar ke bitmap (halaman bercoretan di mode gelap): shader tidak membalik lagi. */
class SilkPage(val bitmap: Bitmap, val preInverted: Boolean = false)

object SilkGl {
    @Volatile var failed = false
    private var probed: Boolean? = null

    /** GLES 2.0 ada dan EGL bisa memilih konfigurasi RGB888. Hasil di-cache. */
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
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE
        )
        val cfg = arrayOfNulls<android.opengl.EGLConfig>(1)
        val n = IntArray(1)
        // display TIDAK di-terminate: dipakai bersama oleh GLSurfaceView
        return EGL14.eglChooseConfig(dpy, attr, 0, cfg, 0, 1, n, 0) && n[0] > 0
    }
}

/** Geometri lengkungan. Semua dalam satuan lebar halaman: x 0 = tepi kiri halaman, 1 = tepi kanan; y 0 = atas, 1 = bawah. */
internal object SilkShape {
    const val N = 48                    // ruas bagian yang terangkat
    const val VERTS = 2 * (N + 2)       // (1 kolom datar + N+1 kolom terangkat) x 2 baris
    private const val P = 1.9f          // makin besar, ujungnya makin lurus
    private const val SHIFT = 1.45f     // geser akhir seluruh lembar ke kiri
    private const val TBL = 256
    private val tbl = FloatArray(TBL + 1)
    val XR_GONE: Float

    private val xs = FloatArray(N + 2)
    private val zs = FloatArray(N + 2)
    private val th = FloatArray(N + 2)
    private val us = FloatArray(N + 2)

    class Mesh {
        val buf: FloatBuffer = ByteBuffer.allocateDirect(VERTS * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        var edgeT = 1f
        var edgeB = 1f
        var zMax = 0f
    }

    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    private fun curve(t0: Float, x: FloatArray, z: FloatArray, a: FloatArray, u: FloatArray) {
        val t = t0.coerceIn(0f, 1f)
        val len = max(t, 1e-3f)
        val c = 1f - len
        val te = (PI * t.pow(0.85f)).toFloat()
        val shift = SHIFT * smooth((t - .55f) / .45f)
        x[0] = -shift; z[0] = 0f; a[0] = 0f; u[0] = 0f
        var cx = c
        var cz = 0f
        var prev = 0f
        for (k in 0..N) {
            val f = k / N.toFloat()
            val ang = te * (1f - (1f - f).pow(P))
            if (k > 0) {
                val m = (prev + ang) * .5f
                cx += cos(m) * len / N
                cz += sin(m) * len / N
            }
            x[k + 1] = cx - shift; z[k + 1] = cz; a[k + 1] = ang; u[k + 1] = c + f * len
            prev = ang
        }
    }

    init {
        val lx = FloatArray(N + 2); val lz = FloatArray(N + 2); val la = FloatArray(N + 2); val lu = FloatArray(N + 2)
        for (i in 0..TBL) {
            curve(i / TBL.toFloat(), lx, lz, la, lu)
            var m = -99f
            for (v in lx) if (v > m) m = v
            tbl[i] = m
        }
        for (i in 1..TBL) if (tbl[i] > tbl[i - 1]) tbl[i] = tbl[i - 1]   // paksa monoton turun
        XR_GONE = tbl[TBL]
    }

    /** Dari posisi siluet (x terjauh) ke parameter t. */
    fun tFor(xr: Float): Float {
        if (xr >= tbl[0] - 1e-4f) return 0f
        if (xr <= tbl[TBL]) return 1f
        var lo = 0
        var hi = TBL
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (tbl[mid] >= xr) lo = mid else hi = mid
        }
        val a = tbl[lo]
        val b = tbl[hi]
        val f = if (a - b < 1e-6f) 0f else (a - xr) / (a - b)
        return (lo + f) / TBL
    }

    /** Isi [m] dengan strip segitiga: per kolom dua titik (atas, bawah): x, y, u, v, bayangan, cos(sudut). */
    fun fill(t: Float, tilt: Float, env: Float, m: Mesh) {
        curve(t, xs, zs, th, us)
        val b = m.buf
        b.clear()
        var eT = -99f
        var eB = -99f
        var zm = 0f
        for (j in 0 until N + 2) {
            val u = us[j]
            // lipatan miring: sisi bawah / atas maju beda jauh, makin besar ke ujung bebas
            val hd = tilt * env * 0.22f * u * sqrt(u.coerceAtLeast(0f))
            val a = th[j]
            val g = (a - 1.95f) / 0.35f
            val sh = 1f - 0.30f * sin(a) + 0.14f * exp(-g * g)
            val cs = cos(a)
            val xt = xs[j] + hd
            val xb = xs[j] - hd
            if (xt > eT) eT = xt
            if (xb > eB) eB = xb
            if (zs[j] > zm) zm = zs[j]
            b.put(xt); b.put(0f); b.put(u); b.put(0f); b.put(sh); b.put(cs)
            b.put(xb); b.put(1f); b.put(u); b.put(1f); b.put(sh); b.put(cs)
        }
        b.position(0)
        m.edgeT = eT; m.edgeB = eB; m.zMax = zm
    }
}

internal class SilkParams(
    val sheetSlot: Int,        // 1: lembar = halaman sekarang, di bawahnya halaman berikutnya; 0: lembar = halaman sebelumnya, di bawahnya halaman sekarang
    val xr: Float,             // posisi siluet lembar (1 = datar menutup halaman, XR_GONE = sudah lewat)
    val tilt: Float,           // -1..1, kemiringan lipatan
    val dark: Boolean,
    val zoom: Float,
    val panX: Float, val panY: Float,          // pecahan lebar / tinggi layar GL
    val rl: Float, val rt: Float, val rw: Float, val rh: Float   // persegi halaman, pecahan layar GL
) {
    companion object { val REST = SilkParams(1, 1f, 0f, false, 1f, 0f, 0f, 0f, 0f, 1f, 1f) }
}

private const val VS = """
attribute vec2 aPos;
attribute vec4 aA;
uniform vec4 uRect;
uniform vec2 uView;
uniform float uZoom;
uniform vec2 uPan;
varying vec2 vTex;
varying float vShade;
varying float vCos;
varying vec2 vPos;
void main() {
  vec2 p = (aPos - 0.5) * uZoom + 0.5;
  vec2 px = uRect.xy + p * uRect.zw + uPan;
  gl_Position = vec4(px.x / uView.x * 2.0 - 1.0, 1.0 - px.y / uView.y * 2.0, 0.0, 1.0);
  vTex = aA.xy;
  vShade = aA.z;
  vCos = aA.w;
  vPos = aPos;
}
"""

private const val FS = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTex;
uniform float uHas;
uniform float uInv;
uniform vec3 uBack;
uniform float uMode;
uniform float uShadow;
uniform vec2 uEdge;
varying vec2 vTex;
varying float vShade;
varying float vCos;
varying vec2 vPos;
void main() {
  vec3 c = mix(vec3(1.0), texture2D(uTex, vTex).rgb, uHas);
  c = mix(c, c * -0.88 + vec3(0.8784), uInv);
  float back = step(vCos, 0.0) * uMode;
  c = mix(c, mix(uBack, c, 0.16), back);
  float edge = mix(uEdge.x, uEdge.y, vPos.y);
  float d = vPos.x - edge;
  float s = d > 0.0 ? exp(-d * 5.0) : 1.0;
  c *= 1.0 - (1.0 - uMode) * uShadow * 0.5 * s;
  c *= mix(1.0, vShade, uMode);
  gl_FragColor = vec4(c, 1.0);
}
"""

internal class SilkRenderer(
    private val onFail: () -> Unit,
    private val onFirst: () -> Unit
) : GLSurfaceView.Renderer {
    @Volatile var params: SilkParams = SilkParams.REST
    @Volatile var pages: Array<SilkPage?> = arrayOfNulls(3)

    private var prog = 0
    private var aPos = 0; private var aA = 0
    private var uRect = 0; private var uView = 0; private var uZoom = 0; private var uPan = 0
    private var uTex = 0; private var uHas = 0; private var uInv = 0; private var uBack = 0
    private var uMode = 0; private var uShadow = 0; private var uEdge = 0
    private val texIds = IntArray(3)
    private val texPage = arrayOfNulls<SilkPage>(3)
    private var slotTex = intArrayOf(-1, -1, -1)
    private var synced: Array<SilkPage?>? = null
    private var viewW = 1
    private var viewH = 1
    private var maxTex = 2048
    private val mesh = SilkShape.Mesh()
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(4 * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(
            0f, 0f, 0f, 0f, 1f, 1f,
            0f, 1f, 0f, 1f, 1f, 1f,
            1f, 0f, 1f, 0f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f
        ))
        position(0)
    }
    private var failed = false
    private var first = true

    private fun fail() {
        if (failed) return
        failed = true
        onFail()
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { GLES20.glDeleteShader(s); throw RuntimeException("silk shader") }
        return s
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            val vs = shader(GLES20.GL_VERTEX_SHADER, VS)
            val fs = shader(GLES20.GL_FRAGMENT_SHADER, FS)
            prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw RuntimeException("silk link")
            aPos = GLES20.glGetAttribLocation(prog, "aPos")
            aA = GLES20.glGetAttribLocation(prog, "aA")
            uRect = GLES20.glGetUniformLocation(prog, "uRect")
            uView = GLES20.glGetUniformLocation(prog, "uView")
            uZoom = GLES20.glGetUniformLocation(prog, "uZoom")
            uPan = GLES20.glGetUniformLocation(prog, "uPan")
            uTex = GLES20.glGetUniformLocation(prog, "uTex")
            uHas = GLES20.glGetUniformLocation(prog, "uHas")
            uInv = GLES20.glGetUniformLocation(prog, "uInv")
            uBack = GLES20.glGetUniformLocation(prog, "uBack")
            uMode = GLES20.glGetUniformLocation(prog, "uMode")
            uShadow = GLES20.glGetUniformLocation(prog, "uShadow")
            uEdge = GLES20.glGetUniformLocation(prog, "uEdge")
            GLES20.glGenTextures(3, texIds, 0)
            for (k in 0..2) texPage[k] = null
            slotTex = intArrayOf(-1, -1, -1)
            synced = null
            val mt = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, mt, 0)
            maxTex = max(1024, mt[0])
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDisable(GLES20.GL_DITHER)
        } catch (t: Throwable) {
            prog = 0
            fail()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = max(1, width)
        viewH = max(1, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (failed || prog == 0) return
        try {
            draw()
            if (first) { first = false; onFirst() }
        } catch (t: Throwable) {
            fail()
        }
    }

    /** Pasang bitmap ke tekstur. Halaman yang sudah punya tekstur dipakai ulang (identitas objek), jadi geser halaman tidak meng-upload ulang. */
    private fun syncTextures() {
        val want = pages
        if (synced === want) return
        val slot = intArrayOf(-1, -1, -1)
        val used = BooleanArray(3)
        for (i in 0..2) {
            val p = want[i] ?: continue
            for (k in 0..2) if (!used[k] && texPage[k] === p) { slot[i] = k; used[k] = true; break }
        }
        for (i in 0..2) {
            val p = want[i] ?: continue
            if (slot[i] >= 0) continue
            var k = 0
            while (k < 2 && used[k]) k++
            upload(texIds[k], p.bitmap)
            texPage[k] = p
            slot[i] = k
            used[k] = true
        }
        for (k in 0..2) if (!used[k]) texPage[k] = null
        slotTex = slot
        synced = want
    }

    private fun upload(id: Int, src: Bitmap) {
        if (src.isRecycled) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        var b = src
        var tmp: Bitmap? = null
        val big = max(src.width, src.height)
        if (big > maxTex) {
            val s = maxTex / big.toFloat()
            tmp = Bitmap.createScaledBitmap(src, max(1, (src.width * s).toInt()), max(1, (src.height * s).toInt()), true)
            b = tmp
        }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        if (tmp != null && tmp !== src) tmp.recycle()   // hanya salinan milik sendiri; bitmap sumber (bersama PdfCache) tidak pernah di-recycle
    }

    private fun draw() {
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        syncTextures()
        val pr = params
        val want = pages
        val vw = viewW.toFloat()
        val vh = viewH.toFloat()

        // jepit ke persegi halaman (yang ikut diperbesar), supaya lembar yang keluar ke kiri tidak tampak di margin
        val cx = (pr.rl + pr.rw / 2f + pr.panX) * vw
        val cy = (pr.rt + pr.rh / 2f + pr.panY) * vh
        val hw = pr.rw * vw * pr.zoom / 2f
        val hh = pr.rh * vh * pr.zoom / 2f
        val x0 = floor(cx - hw).toInt().coerceIn(0, viewW)
        val x1 = ceil(cx + hw).toInt().coerceIn(0, viewW)
        val y0 = floor(cy - hh).toInt().coerceIn(0, viewH)
        val y1 = ceil(cy + hh).toInt().coerceIn(0, viewH)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(x0, viewH - y1, max(0, x1 - x0), max(0, y1 - y0))

        GLES20.glUseProgram(prog)
        GLES20.glUniform4f(uRect, pr.rl * vw, pr.rt * vh, pr.rw * vw, pr.rh * vh)
        GLES20.glUniform2f(uView, vw, vh)
        GLES20.glUniform1f(uZoom, pr.zoom)
        GLES20.glUniform2f(uPan, pr.panX * vw, pr.panY * vh)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (pr.dark) GLES20.glUniform3f(uBack, .11f, .11f, .118f) else GLES20.glUniform3f(uBack, 1f, 1f, 1f)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aA)

        val sheetI = if (pr.sheetSlot == 0) 0 else 1
        val underI = sheetI + 1
        val xr = pr.xr.coerceIn(SilkShape.XR_GONE, 1f)
        val t = SilkShape.tFor(xr)
        val prog01 = ((1f - xr) / (1f - SilkShape.XR_GONE)).coerceIn(0f, 1f)
        val env = sin(PI.toFloat() * prog01)
        SilkShape.fill(t, pr.tilt, env, mesh)

        // 1) halaman di bawah lembar (dengan bayangan jatuh dari siluet lembar), dilewati saat lembar datar menutup penuh
        if (xr < 0.9999f) {
            val under = want[underI]
            bindPage(under, slotTex[underI], pr.dark)
            GLES20.glUniform1f(uMode, 0f)
            // bayangan hanya ada selama siluet lembar benar-benar tampak di layar (tidak muncul tiba-tiba saat lembar masih di luar kiri)
            val vis = (max(mesh.edgeT, mesh.edgeB) / 0.25f).coerceIn(0f, 1f)
            GLES20.glUniform1f(uShadow, (mesh.zMax * 2.2f).coerceIn(0f, 1f) * vis * vis * (3f - 2f * vis))
            GLES20.glUniform2f(uEdge, mesh.edgeT, mesh.edgeB)
            drawBuf(quad, 4)
        }
        // 2) lembar yang melengkung (urutan titik = urutan s, jadi bagian yang lebih jauh menumpuk di atas: tanpa depth buffer)
        val sheet = want[sheetI]
        bindPage(sheet, slotTex[sheetI], pr.dark)
        GLES20.glUniform1f(uMode, 1f)
        GLES20.glUniform1f(uShadow, 0f)
        GLES20.glUniform2f(uEdge, 9f, 9f)
        drawBuf(mesh.buf, SilkShape.VERTS)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aA)
    }

    private fun bindPage(p: SilkPage?, tex: Int, dark: Boolean) {
        if (p != null && tex >= 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[tex])
            GLES20.glUniform1f(uHas, 1f)
            GLES20.glUniform1f(uInv, if (dark && !p.preInverted) 1f else 0f)
        } else {
            GLES20.glUniform1f(uHas, 0f)
            GLES20.glUniform1f(uInv, if (dark) 1f else 0f)   // putih kosong -> hitam di mode gelap, sama dengan halaman lain
        }
    }

    private fun drawBuf(b: FloatBuffer, n: Int) {
        b.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 24, b)
        b.position(2)
        GLES20.glVertexAttribPointer(aA, 4, GLES20.GL_FLOAT, false, 24, b)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, n)
        b.position(0)
    }

    /** Dipanggil lewat queueEvent saat layar ditutup. */
    fun releaseGl() {
        try {
            if (prog != 0) {
                GLES20.glDeleteTextures(3, texIds, 0)
                GLES20.glDeleteProgram(prog)
            }
        } catch (t: Throwable) { }
        prog = 0
        for (k in 0..2) texPage[k] = null
        synced = null
    }
}

/** Pegangan dari sisi Compose ke renderer. Semua setter aman dipanggil dari thread utama. [onFail] dipanggil di thread utama bila GL gagal dibuat / dipakai. */
class SilkController(private val onFail: () -> Unit) {
    var ready by mutableStateOf(false)
        private set
    private val main = Handler(Looper.getMainLooper())
    internal val renderer = SilkRenderer(
        onFail = { fail() },
        onFirst = { main.post { ready = true } }
    )
    internal var view: GLSurfaceView? = null

    internal fun fail() {
        SilkGl.failed = true
        main.post(onFail)
    }

    /** Hanya tiga halaman ini yang dipegang renderer: sebelumnya, sekarang, berikutnya. */
    fun setPages(prev: SilkPage?, cur: SilkPage?, next: SilkPage?) {
        val o = renderer.pages
        if (o[0] === prev && o[1] === cur && o[2] === next) return
        renderer.pages = arrayOf(prev, cur, next)
        view?.requestRender()
    }

    /** [sheetSlot] 1 = lembar halaman sekarang (maju), 0 = lembar halaman sebelumnya (mundur). Satuan px untuk pan, rect, dan ukuran layar. */
    fun setState(
        sheetSlot: Int, xr: Float, tilt: Float, dark: Boolean,
        zoom: Float, panX: Float, panY: Float,
        rectL: Float, rectT: Float, rectW: Float, rectH: Float, viewW: Float, viewH: Float
    ) {
        val vw = max(1f, viewW)
        val vh = max(1f, viewH)
        renderer.params = SilkParams(
            sheetSlot, xr, tilt, dark, zoom, panX / vw, panY / vh,
            rectL / vw, rectT / vh, rectW / vw, rectH / vh
        )
        view?.requestRender()
    }

    fun release() {
        renderer.pages = arrayOfNulls(3)
        val v = view
        view = null
        try { v?.queueEvent { renderer.releaseGl() } } catch (t: Throwable) { }
        try { v?.onPause() } catch (t: Throwable) { }
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

/** GLSurfaceView di dalam Compose. onPause / onResume mengikuti lifecycle; resource dibebaskan saat keluar komposisi. */
@Composable
fun SilkGlView(controller: SilkController, visible: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = remember(ctx) { ctx.findLifecycleOwner() }
    AndroidView(
        factory = { c ->
            GLSurfaceView(c).apply {
                try {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 0, 0, 0)
                    holder.setFormat(PixelFormat.OPAQUE)
                    setRenderer(controller.renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    preserveEGLContextOnPause = true
                    isClickable = false
                    isFocusable = false
                } catch (t: Throwable) {
                    controller.fail()
                }
                controller.view = this
            }
        },
        modifier = modifier,
        update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE }
    )
    DisposableEffect(owner, controller) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> controller.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.view?.onPause()
                else -> {}
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose {
            owner?.lifecycle?.removeObserver(obs)
            controller.release()
        }
    }
}

package app.dinduy

import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// =====================================================================================
//  Google Sans Flex "expressive": judul catatan dan heading H1-H3.
//  Berkas fontnya ada di assets/fonts/google_sans_flex.ttf (OFL-1.1), diisi workflow "Prepare F-Droid" lalu di-commit. Build tidak butuh internet.
//  Sumbu yang dipakai: wght (tebal-tipis), wdth (sempit-lebar), ROND (sudut tajam-bulat), opsz (ukuran optik), slnt (miring).
//  Kalau berkas tidak ada, semuanya jatuh ke font aplikasi (tidak crash, tidak ada animasi huruf).
// =====================================================================================
@OptIn(ExperimentalTextApi::class)
object GSX {
    /** Anak tangga kurva animasi (0..N) dan jumlah tingkat miring. Semua kata berbagi (N+1) x SL keluarga huruf (sengaja sedikit: hemat memori). */
    const val N = 16
    const val SL = 5
    /** Tingkat acuan tata letak judul (tebal sedang, tegak). Lebar SEMUA kata judul disamakan dengan lebar kata ini, apa pun gayanya. */
    const val REF_IX = 9
    const val REST_BOLD_IX = 10
    const val REST_LIGHT_IX = 4

    // Font disalin SEKALI dari assets ke filesDir lalu dibuka lewat berkas (mmap, dipakai bersama oleh semua varian).
    // Lewat assets, tiap varian sumbu mengekstrak ulang seluruh TTF (terkompresi di APK) ke memori -> lag parah dan OOM / force close.
    @Volatile private var file: File? = null
    private var tried = false
    private val cache = HashMap<String, FontFamily?>()

    fun init(ctx: android.content.Context) {
        ReenieFont.init(ctx)
        if (tried) return
        tried = true
        try {
            val out = File(ctx.filesDir, "google_sans_flex_v1.ttf")
            if (!out.exists() || out.length() < 20_000) {
                val tmp = File(out.path + ".tmp")
                ctx.assets.open("fonts/google_sans_flex.ttf").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                if (tmp.length() > 20_000) tmp.renameTo(out) else tmp.delete()
            }
            if (out.exists() && out.length() > 20_000) file = out
        } catch (e: Exception) { }
    }

    fun file(): File? = file
    fun available(): Boolean = file != null

    /** Keluarga huruf dengan sumbu tetap. null = font tidak tersedia. */
    fun fam(w: Int, wd: Float, r: Float, o: Float, sl: Float = 0f): FontFamily? {
        if (!available()) return null
        val fl = file ?: return null
        val sl = if (sl == 0f) 0f else sl   // -0.0 dan 0.0 satu kunci
        val key = "$w/$wd/$r/$o/$sl"
        if (cache.containsKey(key)) return cache[key]
        val f = try {
            FontFamily(
                Font(
                    fl, FontWeight(w.coerceIn(1, 1000)), FontStyle.Normal,
                    FontVariation.Settings(
                        FontVariation.weight(w.coerceIn(1, 1000)),
                        FontVariation.width(wd.coerceIn(25f, 151f)),
                        FontVariation.Setting("ROND", r.coerceIn(0f, 100f)),
                        FontVariation.Setting("opsz", o.coerceIn(1f, 144f)),
                        FontVariation.Setting("slnt", sl.coerceIn(-10f, 0f))
                    )
                )
            )
        } catch (e: Throwable) { null }
        cache[key] = f
        return f
    }

    // Kurva satu dimensi u (0..1): tipis, lebar, tajam -> gendut, ramping, bulat. Lebar huruf dibuat berlawanan dengan ketebalan
    // supaya lebar total kata nyaris tetap; sisa selisihnya dikunci oleh jarak antar huruf (lihat TitleEditor).
    fun curveW(i: Int): Int = (200f + 800f * (i.coerceIn(0, N) / N.toFloat())).roundToInt()
    fun curve(i: Int, s: Int = 0): FontFamily? {
        val u = i.coerceIn(0, N) / N.toFloat()
        return fam(curveW(i), 118f - 38f * u, 100f * u, 72f, -2f * s.coerceIn(0, SL - 1))
    }
    fun idx(u: Float): Int = (u.coerceIn(0f, 1f) * N).roundToInt()
}

/** Rentang tiap kata (tanpa spasi) di [text]. */
fun wordRanges(text: String): List<IntRange> {
    val o = ArrayList<IntRange>()
    var i = 0
    val n = text.length
    while (i < n) {
        while (i < n && text[i].isWhitespace()) i++
        if (i >= n) break
        val s = i
        while (i < n && !text[i].isWhitespace()) i++
        o.add(s until i)
    }
    return o
}

/**
 * Gaya heading catatan (H1-H4). Tebal, lebar, bulat seperti contoh Google Sans Flex; makin kecil levelnya makin tenang.
 * Ukuran, warna, dan tinggi baris sama persis dengan versi lama, hanya jenis hurufnya yang berganti.
 */
fun headStyle(pal: Pal, dm: Dm, lv: Int): TextStyle {
    val fs = dm.fs
    val size = when (lv) { 1 -> fs * 2.5f; 2 -> fs * 1.85f; 3 -> fs * 1.45f; else -> fs * 1.15f }
    val color = when (lv) { 1 -> pal.p; 3 -> pal.p; else -> pal.os }
    val fam = when (lv) {
        1 -> GSX.fam(900, 118f, 100f, 96f)
        2 -> GSX.fam(800, 108f, 70f, 72f)
        3 -> GSX.fam(700, 100f, 40f, 48f)
        else -> GSX.fam(650, 100f, 20f, 24f)
    } ?: return baseStyle(pal, dm, color, size, 1000, 1.02f, -.02f)
    val w = when (lv) { 1 -> 900; 2 -> 800; 3 -> 700; else -> 650 }
    return TextStyle(
        color = color, fontFamily = fam, fontWeight = FontWeight(w), fontSize = size.sp,
        lineHeight = (1.02f * size).sp, letterSpacing = (-.02f).em, lineHeightStyle = TextLHS
    )
}

/** Satu-satunya tempat mengingat catatan mana yang intro judulnya sudah diputar (supaya tidak diputar ulang saat kembali dari menu). */
object TitleFx { @JvmField var played: Any? = null }

/**
 * Jam animasi judul. Tiap KATA punya fase sendiri (ketebalan, bentuk, miring).
 * Intro: kata melompat dari tipis ke gendut dengan pegas memantul. Setelah itu terus bergelombang pelan selama catatan terbuka
 * ([loop] 0..1 = seberapa kuat; 0 saat judul sedang diketik).
 * PENTING: nilainya HARUS dibaca saat komposisi (lewat derivedStateOf di TitleEditor), bukan di dalam VisualTransformation.
 */
class TitleAnim(intro: Boolean) {
    var t by mutableFloatStateOf(if (intro) -1f else 0f)
    var introOn by mutableStateOf(intro)
    var lt by mutableFloatStateOf(0f)
    var loop by mutableFloatStateOf(0f)

    private fun spring(tau: Float, rest: Float): Float {
        val z = .36f
        val w = 13f
        val s = sqrt(1f - z * z)
        val e = exp(-z * w * tau)
        return rest * (1f - e * (cos(w * s * tau) + (z / s) * sin(w * s * tau)))
    }

    fun u(k: Int, rest: Float): Float {
        var u = rest
        if (introOn) {
            val tau = t - k * .055f
            u = if (tau <= 0f) .02f else spring(tau, rest)
        }
        if (loop > 0f) u += loop * (.30f * sin(lt * 1.25f - k * 1.1f) + .10f * sin(lt * 2.3f + k * 2.3f))
        return u
    }

    /** Tingkat miring kata (0 tegak .. SL-1 paling miring). */
    fun slant(k: Int): Int {
        if (loop < .02f) return 0
        val v = (sin(lt * .7f + k * 1.7f) + 1f) / 2f
        return (v * v * (GSX.SL - 1) * loop).roundToInt()   // naik-turun bertahap lewat tingkat antara: miringnya mengalir, tidak patah
    }
}


// =====================================================================================
//  Animasi heading H1-H3 di dalam catatan (sama gayanya dengan judul, hemat dan stabil).
//  Satu jam dipakai bersama semua heading yang tampil. Gaya diam (d = 0, tegak) = persis gaya heading statis (headStyle),
//  jadi animasi berangkat dan kembali ke tampilan yang sama. Lebar tiap kata dikunci ke lebar diamnya (jarak antar huruf), jadi
//  baris dan tinggi heading tidak pernah berubah.
// =====================================================================================
const val HEAD_SL = 3

class HeadVar(val fam: FontFamily?, val w: Int)

/** Sumbu heading statis per level: bobot, lebar, bulat, ukuran optik. Harus sama dengan headStyle. */
private fun headBase(lv: Int): FloatArray = when (lv) {
    1 -> floatArrayOf(900f, 118f, 100f, 96f)
    2 -> floatArrayOf(800f, 108f, 70f, 72f)
    else -> floatArrayOf(700f, 100f, 40f, 48f)
}

/** d = langkah bobot dari gaya diam (negatif = lebih tipis, 40 satuan per langkah), sl = tingkat miring 0..HEAD_SL-1. */
fun GSX.headVar(lv: Int, d: Int, sl: Int): HeadVar {
    val b = headBase(lv)
    val w = (b[0] + d * 40f).toInt().coerceIn(200, 1000)
    val wd = b[1] - (w - b[0]) * .05f
    val r = (b[2] + (w - b[0]) * .08f).coerceIn(0f, 100f)
    return HeadVar(fam(w, wd, r, b[3], -4f * sl.coerceIn(0, HEAD_SL - 1)), w)
}

class HeadAnim {
    var lt by mutableFloatStateOf(0f)
    var loop by mutableFloatStateOf(0f)
    val wc = HashMap<String, Float>()
    private val preloaded = HashSet<Int>()

    fun below(lv: Int): Int = ((headBase(lv)[0] - 250f) / 40f).toInt().coerceIn(2, 9)
    fun above(lv: Int): Int = ((1000f - headBase(lv)[0]) / 40f).toInt().coerceIn(1, 4)

    /** Gaya tiap kata: (d + 16) * 4 + miring. Dibaca lewat derivedStateOf supaya hanya berubah saat tingkatnya berundak. */
    fun specs(lv: Int, n: Int): List<Int> = List(n) { k ->
        val s = if (loop <= 0f) 0f else (loop * (.8f * sin(lt * 1.1f - k * 1.3f) + .2f * sin(lt * 2.0f + k * 2.1f))).coerceIn(-1f, 1f)
        val d = if (s < 0f) (s * below(lv)).roundToInt() else (s * above(lv)).roundToInt()
        val v = (sin(lt * .62f + k * 1.9f) + 1f) / 2f
        val sl = (v * v * (HEAD_SL - 1) * loop).roundToInt()
        (d + 16) * 4 + sl
    }

    suspend fun preload(lv: Int, res: androidx.compose.ui.text.font.FontFamily.Resolver) {
        if (!preloaded.add(lv)) return
        try {
            kotlinx.coroutines.delay(1200)
            for (sl in 0 until HEAD_SL) for (d in -below(lv)..above(lv)) {
                GSX.headVar(lv, d, sl).fam?.let { res.preload(it) }
                kotlinx.coroutines.delay(20)
            }
        } catch (e: Throwable) { }
    }
}

private val headFxMap = java.util.WeakHashMap<Any, HeadAnim>()
/** Jam animasi heading milik satu editor (tanpa mengubah kelas Ed). */
fun headFxOf(ed: Any): HeadAnim = headFxMap.getOrPut(ed) { HeadAnim() }

/**
 * Reenie Beanie (OFL-1.1), dibundel di assets/fonts/reenie_beanie.ttf (diisi workflow "Prepare F-Droid").
 * Pengganti Google Fonts downloadable (butuh Google Play services, dilarang F-Droid). Diinisialisasi dari GSX.init(ctx).
 * Kalau berkasnya tidak ada, jatuh ke FontFamily.Cursive.
 */
object ReenieFont {
    @Volatile var family: FontFamily = FontFamily.Cursive
    private var tried = false
    fun init(ctx: android.content.Context) {
        if (tried) return
        tried = true
        try {
            ctx.assets.open("fonts/reenie_beanie.ttf").close()
            family = FontFamily(Font("fonts/reenie_beanie.ttf", ctx.assets, FontWeight.Normal, FontStyle.Normal))
        } catch (e: Throwable) { }
    }
}

/** Dipakai EditorUI. Selalu mengembalikan keluarga huruf yang valid. */
val ReenieFamily: FontFamily get() = ReenieFont.family

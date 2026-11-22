package app.dinduy

import android.content.res.AssetManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
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
//  Berkas fontnya diunduh Gradle saat build (assets/fonts/google_sans_flex.ttf), TIDAK ada di repository.
//  Sumbu yang dipakai: wght (tebal-tipis), wdth (sempit-lebar), ROND (sudut tajam-bulat), opsz (ukuran optik).
//  Kalau berkas tidak ada, semuanya jatuh ke font aplikasi (tidak crash, tidak ada animasi huruf).
// =====================================================================================
@OptIn(ExperimentalTextApi::class)
object GSX {
    private const val FILE = "fonts/google_sans_flex.ttf"
    /** Jumlah anak tangga kurva animasi. Semua kata berbagi N+1 keluarga huruf ini, jadi hanya ada N+1 Typeface di memori. */
    const val N = 24

    @Volatile private var am: AssetManager? = null
    private var has: Boolean? = null
    private val cache = HashMap<String, FontFamily?>()

    fun init(a: AssetManager) { if (am == null) am = a }

    fun available(): Boolean {
        has?.let { return it }
        val a = am ?: return false
        val r = try { a.open("fonts/google_sans_flex.ttf").close(); true } catch (e: Exception) { false }
        has = r
        return r
    }

    /** Keluarga huruf dengan sumbu tetap. null = font tidak tersedia. */
    fun fam(w: Int, wd: Float, r: Float, o: Float): FontFamily? {
        if (!available()) return null
        val a = am ?: return null
        val key = "$w/$wd/$r/$o"
        if (cache.containsKey(key)) return cache[key]
        val f = try {
            FontFamily(
                Font(
                    FILE, a, FontWeight(w.coerceIn(1, 1000)), FontStyle.Normal,
                    FontVariation.Settings(
                        FontVariation.weight(w.coerceIn(1, 1000)),
                        FontVariation.width(wd.coerceIn(25f, 151f)),
                        FontVariation.Setting("ROND", r.coerceIn(0f, 100f)),
                        FontVariation.Setting("opsz", o.coerceIn(1f, 144f))
                    )
                )
            )
        } catch (e: Throwable) { null }
        cache[key] = f
        return f
    }

    // Kurva satu dimensi u (0..1): tipis-sempit-tajam -> gendut-lebar-bulat. Semua kata bergerak di kurva ini.
    fun curveW(i: Int): Int = (200f + 800f * (i.coerceIn(0, N) / N.toFloat())).roundToInt()
    fun curve(i: Int): FontFamily? {
        val u = i.coerceIn(0, N) / N.toFloat()
        return fam(curveW(i), 80f + 60f * u, 100f * u, 72f)
    }
    fun idx(u: Float): Int = (u.coerceIn(0f, 1f) * N).roundToInt()

    const val REST_BOLD = .55f
    const val REST_LIGHT = .2f
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
 * Animasi judul: tiap KATA punya fase sendiri. Intro (saat catatan dibuka): kata melompat dari tipis-sempit ke gendut-lebar dengan pegas
 * yang memantul. Mode baca: bernafas pelan (tebal-tipis bergelombang antar kata). Dibaca hanya oleh TitleEditor, jadi yang recompose per frame
 * cuma judulnya.
 */
class TitleAnim(intro: Boolean) {
    var t by mutableFloatStateOf(if (intro) -1f else 0f)
    var introOn by mutableStateOf(intro)
    var lt by mutableFloatStateOf(0f)
    var loop by mutableFloatStateOf(0f)
    var loopOn by mutableStateOf(false)
    val animating: Boolean get() = introOn || loopOn

    private fun spring(tau: Float, rest: Float): Float {
        val z = .36f
        val w = 10.5f
        val s = sqrt(1f - z * z)
        val e = exp(-z * w * tau)
        return rest * (1f - e * (cos(w * s * tau) + (z / s) * sin(w * s * tau)))
    }

    fun u(k: Int, rest: Float): Float {
        var u = rest
        if (introOn) {
            val tau = t - k * .07f
            u = if (tau <= 0f) .02f else spring(tau, rest)
        }
        if (loop > 0f) u += loop * .17f * sin(lt * 1.4f - k * .9f)
        return u
    }
}

/** Beri gaya Google Sans Flex per kata. [uOf] = posisi kata di kurva (0..1) menurut nomor kata. */
fun AnnotatedString.Builder.addWordStyles(text: String, uOf: (Int) -> Float) {
    var k = 0
    var i = 0
    val n = text.length
    while (i < n) {
        while (i < n && text[i].isWhitespace()) i++
        if (i >= n) break
        val s = i
        while (i < n && !text[i].isWhitespace()) i++
        val ix = GSX.idx(uOf(k))
        GSX.curve(ix)?.let { addStyle(SpanStyle(fontFamily = it, fontWeight = FontWeight(GSX.curveW(ix))), s, i) }
        k++
    }
}

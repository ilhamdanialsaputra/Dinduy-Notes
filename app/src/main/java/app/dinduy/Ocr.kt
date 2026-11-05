package app.dinduy

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * OCR offline (ML Kit, model Latin ikut di dalam aplikasi) untuk satu foto catatan.
 * Mengembalikan teks per paragraf (dipisah baris baru), "" bila tidak ada teks, null bila foto gagal dibaca.
 */
suspend fun AppVM.ocrImage(imgId: String): String? = withContext(Dispatchers.IO) {
    val f = File(imgDir, "$imgId.jpg")
    if (!f.exists()) return@withContext null
    val bm = BitmapFactory.decodeFile(f.absolutePath) ?: return@withContext null
    val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        suspendCancellableCoroutine<String?> { c ->
            rec.process(InputImage.fromBitmap(bm, 0))
                .addOnSuccessListener { r -> c.resume(r.textBlocks.joinToString("\n") { b -> b.lines.joinToString(" ") { l -> l.text } }) }
                .addOnFailureListener { c.resume(null) }
        }
    } catch (e: Throwable) {
        null
    } finally {
        rec.close()
    }
}

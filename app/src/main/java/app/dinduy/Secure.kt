package app.dinduy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Brankas API key. Key TIDAK PERNAH:
 *  - ditulis ke kode / repository (pengguna mengetiknya di aplikasi),
 *  - masuk ke dinduy.json, Prefs, atau berkas cadangan (.dinduy),
 *  - disimpan sebagai teks biasa.
 * Disimpan terenkripsi AES-256-GCM dengan kunci Android Keystore (tidak bisa diekspor, terikat ke perangkat ini;
 * di banyak HP tersimpan di chip keamanan). Berkas terenkripsi ada di filesDir/secure dan dikecualikan dari
 * auto-backup Google lewat res/xml/backup_rules.xml dan res/xml/data_extraction_rules.xml.
 */
class KeyVault(private val ctx: Context) {
    private val alias = "dinduy_groq_v1"
    private val aad = "app.dinduy:groq".toByteArray(Charsets.UTF_8)
    private fun dir() = File(ctx.filesDir, "secure")
    private fun file() = File(dir(), "groq.bin")

    fun has(): Boolean = file().exists()

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val ex = ks.getKey(alias, null)
        if (ex is SecretKey) return ex
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun save(key: String): Boolean {
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, secretKey())
            c.updateAAD(aad)
            val iv = c.iv
            val ct = c.doFinal(key.toByteArray(Charsets.UTF_8))
            val out = ByteArray(1 + iv.size + ct.size)
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(ct, 0, out, 1 + iv.size, ct.size)
            dir().mkdirs()
            val tmp = File(dir(), "groq.tmp")
            tmp.writeBytes(out)
            if (!tmp.renameTo(file())) {
                file().delete()
                if (!tmp.renameTo(file())) { tmp.delete(); return false }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Null bila belum ada, atau tidak bisa didekripsi (mis. berkas dipindah ke HP lain): berkas rusak langsung dihapus. */
    fun load(): String? {
        val f = file()
        if (!f.exists()) return null
        return try {
            val b = f.readBytes()
            val n = b[0].toInt()
            if (n < 8 || n > 32 || b.size <= 1 + n) throw IllegalStateException("bad")
            val iv = b.copyOfRange(1, 1 + n)
            val ct = b.copyOfRange(1 + n, b.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            c.updateAAD(aad)
            String(c.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        try { file().delete() } catch (e: Exception) { }
        try { File(dir(), "groq.tmp").delete() } catch (e: Exception) { }
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        } catch (e: Exception) { }
    }

    companion object {
        /** Rapikan dan validasi hasil tempel. Null bila bentuknya tidak seperti key Groq (gsk_...). */
        fun clean(raw: String): String? {
            val k = raw.trim().replace("\"", "").replace("'", "")
            if (k.length < 20 || k.length > 200) return null
            if (!k.startsWith("gsk_")) return null
            if (!k.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return null
            return k
        }

        fun mask(k: String): String = if (k.length <= 8) "gsk_••••" else "gsk_••••••" + k.takeLast(4)
    }
}

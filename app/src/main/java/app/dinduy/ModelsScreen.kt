package app.dinduy

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun mb(b: Long): String = String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)

private val LANGS = listOf("" to "Otomatis", "id" to "Indonesia", "en" to "English")

/** Kartu pilihan mesin (Groq / offline): satu terpilih, ditandai kotak centang yang berubah bentuk. */
@Composable
private fun EngineCard(pos: Int, on: Boolean, icon: String, title: String, badge: String?, sub: String, status: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    SCard(pos, onClick, PaddingValues(horizontal = 20.dp, vertical = 16.dp), bg = if (on) pal.pc else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) pal.p else pal.c4), Alignment.Center) {
                Ic(icon, if (on) pal.op else pal.os, 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(title, 17f, 600, if (on) pal.opc else pal.os)
                    if (badge != null) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(pal.p).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            T(badge, 11f, 800, pal.op, maxLines = 1)
                        }
                    }
                }
                T(sub, 14f, 400, if (on) pal.opc else pal.osv, lh = 1.3f)
                T(status, 13f, 600, if (on) pal.opc else pal.p, Modifier.padding(top = 4.dp), lh = 1.3f)
            }
            MorphCheck(on)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    val pal = LocalPal.current
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T("•", 14f, 700, pal.p)
        T(text, 14f, 400, pal.osv, Modifier.weight(1f), lh = 1.45f)
    }
}

/** Halaman penuh "Transkripsi suara": pilih API key Groq (prioritas) atau model offline. */
@Composable
fun ModelsScreen(vm: AppVM, onBack: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    var keyText by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }   // mengganti key yang sudah tersimpan
    val enter = fadeIn(tween(260)) + expandVertically(spring(.85f, 380f))
    val exit = fadeOut(tween(140)) + shrinkVertically(spring(.95f, 520f))

    // selama mengisi key: layar tidak bisa di-screenshot / muncul di aplikasi terbaru
    DisposableEffect(focused) {
        val w = (ctx as? Activity)?.window
        if (focused) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(vm.gqState, vm.gqSet) {
        if (vm.gqState == 2 && vm.gqSet) { keyText = ""; changing = false }   // key sudah tersimpan terenkripsi; jangan biarkan tersisa di layar
    }

    val readyNames = vm.models.list.filter { it.status == 2 }.joinToString { it.info.name }
    val grOn = vm.pr.eng != "off"

    PageScaffold("Transkripsi suara", "Ubah suara jadi teks", onBack) {
        SGroup("Pilih cara transkripsi", 0) {
            EngineCard(
                POS_TOP, grOn, "mic", "API key Groq", "DISARANKAN",
                "Online. Cepat dan paling akurat, bisa untuk rekaman panjang.",
                if (vm.gqSet) "Siap · key " + vm.gqHint else "Belum ada API key. Isi di bawah (±2 menit)."
            ) { vm.setPr(vm.pr.copy(eng = "groq")) }
            EngineCard(
                POS_BOT, !grOn, "phone", "Model offline", null,
                "Tanpa internet, tetapi jauh lebih lambat.",
                if (readyNames.isEmpty()) "Belum ada model diunduh" else "Siap · $readyNames"
            ) { vm.setPr(vm.pr.copy(eng = "off")) }
            T(
                "Ketuk untuk memilih. Hanya pilihanmu yang ditampilkan di bawah; yang lain tetap tersimpan dan bisa diganti kapan saja.",
                13f, 400, pal.osv, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), lh = 1.4f
            )
        }

        AnimatedVisibility(visible = grOn, enter = enter, exit = exit) {
        SGroup("API key Groq", 0) {
            SCard(POS_TOP, onClick = { infoOpen = !infoOpen }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Apa itu API key, dan kenapa lebih baik?", 16f, 600, pal.os, Modifier.weight(1f))
                    Ic(if (infoOpen) "hide" else "chr", pal.osv, 24.dp)
                }
                if (infoOpen) {
                    Spacer(Modifier.height(10.dp))
                    T("API key itu apa?", 14f, 700, pal.p)
                    T(
                        "Seperti kata sandi khusus yang mengizinkan aplikasi memakai layanan Groq atas namamu. " +
                            "Dinduy tidak punya server: key dipakai langsung dari HP-mu ke Groq.",
                        14f, 400, pal.osv, Modifier.padding(top = 4.dp), lh = 1.45f
                    )
                    T("Cara mendapatkannya", 14f, 700, pal.p, Modifier.padding(top = 14.dp))
                    Bullet("Ketuk \"Buka halaman Groq\" di bawah, lalu daftar atau masuk (bisa dengan Google).")
                    Bullet("Pilih \"Create API Key\" dan beri nama bebas, misalnya Dinduy.")
                    Bullet("Salin key yang diawali gsk_ (Groq hanya menampilkannya sekali).")
                    Bullet("Kembali ke sini, tempel di kolom, lalu ketuk \"Simpan & tes\".")
                    T("Kenapa lebih baik dari model offline?", 14f, 700, pal.p, Modifier.padding(top = 14.dp))
                    Bullet("Jauh lebih cepat: rekaman berjalan menit selesai dalam hitungan detik, tidak membebani HP.")
                    Bullet("Lebih akurat: memakai Whisper large-v3, jauh lebih besar dari model offline, dan paham banyak bahasa.")
                    Bullet("Tanpa unduhan ratusan MB. Rekaman panjang dipotong dan diproses otomatis.")
                    T("Perlu diketahui", 14f, 700, pal.p, Modifier.padding(top = 14.dp))
                    Bullet("Butuh internet, dan audio dikirim ke Groq hanya saat kamu menekan Transkripsi.")
                    Bullet("Groq punya paket gratis dengan batas pemakaian; selebihnya mengikuti harga Groq. Cek rinciannya di situs Groq.")
                    Bullet("Groq hanya diminta menulis ulang ucapan apa adanya: tidak merangkum dan tidak membuat poin.")
                }
            }
            SCard(POS_MID) {
                if (vm.gqSet && !changing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(pal.pc), Alignment.Center) { Ic("ok", pal.opc, 24.dp) }
                        Column(Modifier.weight(1f)) {
                            T("Siap dipakai", 17f, 600, pal.os)
                            T("Key tersimpan · " + vm.gqHint, 14f, 400, pal.osv)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        GBtn(0, "restore", "Tes", pal.c4, pal.os, enabled = vm.gqState != 1, modifier = Modifier.weight(1f)) { vm.testSavedKey() }
                        GBtn(1, "pen", "Ganti", pal.c4, pal.os, modifier = Modifier.weight(1f)) { vm.gqState = 0; changing = true }
                        GBtn(2, "del", "Hapus", pal.ec, pal.oec, modifier = Modifier.weight(1f)) { vm.removeGroqKey() }
                    }
                } else {
                    T("Langkah 1", 13f, 700, pal.p)
                    T("Buat API key di Groq", 16f, 500, pal.os)
                    Spacer(Modifier.height(10.dp))
                    GBtn(3, "up", "Buka halaman Groq", pal.sc, pal.osc, modifier = Modifier.fillMaxWidth()) {
                        try { uri.openUri("https://console.groq.com/keys") } catch (e: Exception) { vm.show("Tidak ada peramban untuk membuka tautan") }
                    }
                    Spacer(Modifier.height(18.dp))
                    T("Langkah 2", 13f, 700, pal.p)
                    T("Tempel API key di sini", 16f, 500, pal.os)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.c4).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        if (keyText.isEmpty()) T("gsk_…", 16f, 400, pal.osv)
                        BasicTextField(
                            value = keyText,
                            onValueChange = { v -> keyText = v.filter { c -> !c.isWhitespace() }.take(200) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = TextStyle(color = pal.os, fontSize = 16.sp, fontFamily = LocalFF.current),
                            cursorBrush = SolidColor(pal.p),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (keyText.isNotBlank()) vm.saveGroqKey(keyText) }),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (vm.gqSet) {
                            GBtn(0, "x", "Batal", pal.c4, pal.os, modifier = Modifier.weight(1f)) { keyText = ""; changing = false; vm.gqState = 0 }
                        }
                        GBtn(
                            if (vm.gqSet) 2 else 3, "ok", "Simpan & tes", pal.p, pal.op,
                            enabled = keyText.isNotBlank() && vm.gqState != 1, modifier = Modifier.weight(1f)
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.saveGroqKey(keyText)
                        }
                    }
                }
                if (vm.gqState != 0) {
                    Spacer(Modifier.height(12.dp))
                    val ok = vm.gqState == 2
                    val bad = vm.gqState == 3
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(if (bad) pal.ec else pal.pc).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (vm.gqState == 1) LoadingBlob(Modifier.size(32.dp))
                        else Ic(if (ok) "ok" else "x", if (bad) pal.oec else pal.opc, 24.dp)
                        Spacer(Modifier.width(12.dp))
                        T(vm.gqMsg, 14f, 500, if (bad) pal.oec else pal.opc, Modifier.weight(1f), lh = 1.35f)
                    }
                }
            }
            SCard(POS_BOT) {
                T("Keamanan API key-mu", 16f, 600, pal.os)
                Bullet("Key hanya diketik di aplikasi ini. Tidak ada di kode, repository, atau berkas cadangan (.dinduy).")
                Bullet("Disimpan terenkripsi (AES-256) dengan kunci Android Keystore yang terikat ke HP ini dan tidak bisa dikeluarkan. Tidak ikut auto-backup Google.")
                Bullet("Dikirim hanya ke api.groq.com lewat HTTPS, tidak pernah ke server lain, dan tidak pernah ditulis di log.")
                Bullet("Saat kolom key aktif, layar tidak bisa di-screenshot.")
                Bullet("Saran: buat key khusus untuk Dinduy. Kalau HP hilang, hapus key itu di console.groq.com.")
            }
        }
        }

        AnimatedVisibility(visible = !grOn, enter = enter, exit = exit) {
        SGroup("Model offline", 0) {
            val list = vm.models.list
            SCard(POS_TOP) {
                T(
                    "Dipakai tanpa internet. Diunduh sekali ke penyimpanan aplikasi (tidak ikut cadangan). " +
                        "Lebih lambat dan kurang akurat dibanding Groq. Unduhan bisa dilanjutkan kalau terputus.",
                    14f, 400, pal.osv, lh = 1.5f
                )
            }
            list.forEachIndexed { i, m ->
                val pos = if (i == list.size - 1) POS_BOT else POS_MID
                val info = m.info
                val active = m.status == 2 && vm.models.readyFile(vm.pr.wm)?.name == info.file
                SCard(pos, pad = PaddingValues(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(info.name, 20f, 600, pal.os)
                            T(info.size + " · multibahasa", 14f, 500, pal.osv)
                        }
                        val label = when (m.status) {
                            2 -> if (active) "Dipakai" else "Siap"
                            1 -> "Mengunduh"
                            else -> if (m.got > 0L) "Terjeda" else "Belum diunduh"
                        }
                        val bg = if (m.status == 2) pal.pc else pal.c4
                        val fg = if (m.status == 2) pal.opc else pal.osv
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            T(label, 13f, 700, fg, maxLines = 1)
                        }
                    }
                    T(info.desc, 14f, 400, pal.osv, Modifier.padding(top = 8.dp), lh = 1.4f)
                    if (m.status == 1 || (m.status == 0 && m.got > 0L)) {
                        val frac = if (m.total > 0L) (m.got.toFloat() / m.total).coerceIn(0f, 1f) else 0f
                        Spacer(Modifier.height(10.dp))
                        WavyBar(progress = { if (m.total > 0L) (m.got.toFloat() / m.total).coerceIn(0f, 1f) else 0f }, playing = m.status == 1)
                        T(
                            mb(m.got) + " dari " + mb(m.total) + " · " + (frac * 100f).toInt() + "%",
                            13f, 600, pal.osv, Modifier.padding(top = 2.dp)
                        )
                    }
                    val er = m.err
                    if (er != null && m.status == 0) {
                        T("Terakhir gagal: $er", 13f, 500, pal.oec, Modifier.padding(top = 8.dp), lh = 1.3f)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        when (m.status) {
                            0 -> {
                                GBtn(3, "dl", if (m.got > 0L) "Lanjutkan" else "Unduh " + info.size, pal.p, pal.op) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.models.start(m)
                                }
                                if (m.got > 0L) {
                                    GBtn(2, "del", null, pal.ec, pal.oec) { vm.models.delete(m) }
                                }
                            }
                            1 -> GBtn(3, "x", "Batal", pal.c4, pal.os) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.models.cancel(m)
                            }
                            else -> {
                                GBtn(0, "ok", if (active) "Model aktif" else "Pakai model ini", if (active) pal.c4 else pal.p, if (active) pal.os else pal.op, enabled = !active) {
                                    vm.setPr(vm.pr.copy(wm = info.key, eng = "off"))
                                }
                                GBtn(2, "del", "Hapus", pal.ec, pal.oec) { vm.models.delete(m) }
                            }
                        }
                    }
                }
            }
        }
        }

        SGroup("Hasil transkrip", 120) {
            SCard(if (grOn) POS_TOP else POS_ONE) {
                T("Bahasa ucapan", 16f, 500, pal.os, Modifier.padding(bottom = 12.dp))
                SegPill(3, LANGS.indexOfFirst { it.first == vm.pr.gl }.coerceAtLeast(0), { vm.setPr(vm.pr.copy(gl = LANGS[it].first)) }) { i, fg ->
                    T(LANGS[i].second, 15f, 500, fg)
                }
                T(
                    "Memilih bahasa yang tepat membuat hasil lebih akurat. Otomatis cocok untuk campuran bahasa.",
                    13f, 400, pal.osv, Modifier.padding(top = 10.dp), lh = 1.4f
                )
            }
            AnimatedVisibility(visible = grOn, enter = enter, exit = exit) {
            SCard(POS_BOT, onClick = { vm.setPr(vm.pr.copy(gf = !vm.pr.gf)) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        T("Rapikan jadi Markdown", 16f, 500, pal.os)
                        T(
                            "Khusus Groq: tebal, miring, dan paragraf otomatis. Kata-katamu tidak diubah, dicek otomatis; kalau ada yang berbeda, dipakai versi polos.",
                            13f, 400, pal.osv, lh = 1.4f
                        )
                    }
                    MorphSwitch(vm.pr.gf)
                }
            }
            }
            T(
                "Hasilnya masuk ke catatan sebagai bagian \"Transkrip 1\", \"Transkrip 2\", dan seterusnya, dan bisa kamu edit. Kartu suara tetap di bawah.",
                13f, 400, pal.osv, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), lh = 1.4f
            )
        }
    }
}

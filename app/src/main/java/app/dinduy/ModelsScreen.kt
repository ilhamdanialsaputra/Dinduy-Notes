package app.dinduy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

private fun mb(b: Long): String = String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)

/** Halaman penuh "Unduh model transkripsi" (dari Pengaturan, atau dari dialog saat model belum ada). */
@Composable
fun ModelsScreen(vm: AppVM, onBack: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    PageScaffold("Model transkripsi", "Suara jadi teks, tanpa internet", onBack) {
        SGroup("Tentang", 0) {
            SCard(POS_ONE) {
                T(
                    "Model diunduh sekali ke penyimpanan aplikasi (tidak ikut dalam cadangan) lalu dipakai sepenuhnya offline. " +
                        "Disarankan memakai Wi-Fi. Unduhan bisa dilanjutkan kalau terputus.",
                    14f, 400, pal.osv, lh = 1.5f
                )
            }
        }
        SGroup("Model", 60) {
            val list = vm.models.list
            list.forEachIndexed { i, m ->
                val pos = if (list.size == 1) POS_ONE else if (i == 0) POS_TOP else if (i == list.size - 1) POS_BOT else POS_MID
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
                                    vm.setPr(vm.pr.copy(wm = info.key))
                                }
                                GBtn(2, "del", "Hapus", pal.ec, pal.oec) { vm.models.delete(m) }
                            }
                        }
                    }
                }
            }
        }
    }
}

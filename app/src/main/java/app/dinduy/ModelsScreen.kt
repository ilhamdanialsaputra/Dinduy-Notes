package app.dinduy

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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

private fun mb(b: Long): String = String.format(java.util.Locale.US, "%.0f MB", b / 1048576.0)

private val LANGS = listOf("" to "Auto", "id" to "Indonesian", "en" to "English")

/** Kartu pilihan (satu terpilih), ditandai kotak centang yang berubah bentuk. */
@Composable
private fun EngineCard(pos: Int, on: Boolean, icon: String, title: String, badge: String?, sub: String, status: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    SCard(pos, onClick, PaddingValues(horizontal = 20.dp, vertical = 16.dp), bg = if (on) pal.pc else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) pal.p else pal.tb2), Alignment.Center) {
                Ic(icon, if (on) pal.op else pal.os, 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(title, 17f, 600, if (on) pal.opc else pal.os)
                    if (badge != null) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(pal.p).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            T(badge, 11f, 700, pal.op, maxLines = 1)
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
private fun Step(n: Int, text: String) {
    val pal = LocalPal.current
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(pal.tb2), Alignment.Center) { T(n.toString(), 13f, 700, pal.os) }
        T(text, 14f, 400, pal.osv, Modifier.weight(1f), lh = 1.4f)
    }
}

@Composable
private fun StatusBanner(state: Int, msg: String) {
    val pal = LocalPal.current
    val bad = state == 3
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (bad) pal.ec else pal.pc)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state == 1) LoadingBlob(Modifier.size(32.dp))
        else Ic(if (bad) "x" else "ok", if (bad) pal.oec else pal.opc, 24.dp)
        Spacer(Modifier.width(12.dp))
        T(msg, 14f, 500, if (bad) pal.oec else pal.opc, Modifier.weight(1f), lh = 1.35f)
    }
}

/** Halaman "Voice transcription": sesederhana mungkin. Pilih cara, siapkan, selesai. */
@Composable
fun ModelsScreen(vm: AppVM, act: Actions, onBack: () -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    var keyText by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var howOpen by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    val enter = fadeIn(tween(260)) + expandVertically(spring(.85f, 380f))
    val exit = fadeOut(tween(140)) + shrinkVertically(spring(.95f, 520f))

    // selama mengisi kunci: layar tidak bisa di-screenshot / muncul di aplikasi terbaru
    DisposableEffect(focused) {
        val w = (ctx as? Activity)?.window
        if (focused) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(vm.gqState, vm.gqSet) {
        if (vm.gqState == 2 && vm.gqSet) { keyText = ""; changing = false }
    }

    // minta izin notifikasi sekali saja (supaya kemajuan unduhan terlihat di luar aplikasi)
    fun askNotifOnce() {
        val sp = ctx.getSharedPreferences("dinduy", 0)
        if (Build.VERSION.SDK_INT >= 33 && !sp.getBoolean("np", false) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) act.askNotif()
    }

    val offReady = vm.models.anyReady()
    val grOn = vm.pr.eng != "off"
    val linked = vm.gqSet && !changing

    PageScaffold("Voice transcription", "Turn speech into text", onBack) {
        SGroup("Choose a method", 0) {
            EngineCard(
                POS_TOP, grOn, "mic", "Online (Groq)", "Recommended",
                "Fast and most accurate",
                if (vm.gqSet) "Ready" else "Needs a Groq key"
            ) { vm.setPr(vm.pr.copy(eng = "groq")) }
            EngineCard(
                POS_BOT, !grOn, "phone", "No internet needed", null,
                "Runs on your phone, slower",
                if (offReady) "Ready" else "Needs a model download"
            ) { vm.setPr(vm.pr.copy(eng = "off")) }
        }

        AnimatedVisibility(visible = grOn, enter = enter, exit = exit) {
            SGroup("Groq account", 0) {
                SCard(if (linked) POS_ONE else POS_TOP) {
                    if (linked) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(pal.tb2), Alignment.Center) { Ic("ok", pal.os, 24.dp) }
                            Column(Modifier.weight(1f)) {
                                T("Connected", 17f, 600, pal.os)
                                T("Key " + vm.gqHint, 14f, 400, pal.osv)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            GBtn(0, "restore", "Test", pal.tb2, pal.os, enabled = vm.gqState != 1, modifier = Modifier.weight(1f)) { vm.testSavedKey() }
                            GBtn(1, "pen", "Replace", pal.tb2, pal.os, modifier = Modifier.weight(1f)) { vm.gqState = 0; changing = true }
                            GBtn(2, "del", "Remove", pal.ec, pal.oec, modifier = Modifier.weight(1f)) { vm.removeGroqKey() }
                        }
                    } else {
                        T("Paste your Groq key", 17f, 600, pal.os)
                        T("Free, and you only need to make it once.", 14f, 400, pal.osv, Modifier.padding(top = 2.dp, bottom = 12.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.tb2).padding(horizontal = 16.dp, vertical = 14.dp)) {
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
                                GBtn(0, "x", "Cancel", pal.tb2, pal.os, modifier = Modifier.weight(1f)) { keyText = ""; changing = false; vm.gqState = 0 }
                            } else {
                                GBtn(0, "up", "Open Groq", pal.sc, pal.osc, modifier = Modifier.weight(1f)) {
                                    try { uri.openUri("https://console.groq.com/keys") } catch (e: Exception) { vm.show("No browser found to open the link") }
                                }
                            }
                            GBtn(
                                2, "ok", "Save", pal.p, pal.op,
                                enabled = keyText.isNotBlank() && vm.gqState != 1, modifier = Modifier.weight(1f)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.saveGroqKey(keyText)
                            }
                        }
                    }
                    if (vm.gqState != 0) {
                        Spacer(Modifier.height(12.dp))
                        StatusBanner(vm.gqState, vm.gqMsg)
                    }
                }
                if (!linked) {
                    SCard(POS_BOT, onClick = { howOpen = !howOpen }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T("How to get a key", 16f, 600, pal.os, Modifier.weight(1f))
                            Ic(if (howOpen) "hide" else "chr", pal.osv, 24.dp)
                        }
                        if (howOpen) {
                            Step(1, "Tap Open Groq, then sign in with Google.")
                            Step(2, "Choose Create API Key, then copy the key.")
                            Step(3, "Come back here, paste it, and tap Save.")
                            T(
                                "The key is stored encrypted on this phone and only used to contact Groq.",
                                13f, 400, pal.osv, Modifier.padding(top = 12.dp), lh = 1.4f
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = !grOn, enter = enter, exit = exit) {
            SGroup("Models on your phone", 0) {
                val list = vm.models.list
                list.forEachIndexed { i, m ->
                    val pos = if (i == list.size - 1) POS_BOT else if (i == 0) POS_TOP else POS_MID
                    val info = m.info
                    val active = m.status == 2 && vm.models.readyFile(vm.pr.wm)?.name == info.file
                    SCard(pos, pad = PaddingValues(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T(modelLabel(info.key), 19f, 600, pal.os)
                                T(mb(info.bytes), 14f, 500, pal.osv)
                            }
                            if (m.status == 2) {
                                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(pal.pc).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    T(if (active) "In use" else "Ready", 13f, 700, pal.opc, maxLines = 1)
                                }
                            }
                        }
                        T(
                            if (info.key == "small") "More accurate, but slower." else "Fast and good enough for most notes.",
                            14f, 400, pal.osv, Modifier.padding(top = 6.dp), lh = 1.4f
                        )
                        if (m.status == 1 || (m.status == 0 && m.got > 0L)) {
                            val frac = if (m.total > 0L) (m.got.toFloat() / m.total).coerceIn(0f, 1f) else 0f
                            Spacer(Modifier.height(10.dp))
                            WavyBar(progress = { if (m.total > 0L) (m.got.toFloat() / m.total).coerceIn(0f, 1f) else 0f }, playing = m.status == 1)
                            T(
                                (if (m.status == 1) "Downloading " else "Paused ") + (frac * 100f).toInt() + "%",
                                13f, 600, pal.osv, Modifier.padding(top = 2.dp)
                            )
                            if (m.status == 1) {
                                T("Safe to leave. The download keeps running in the background.", 13f, 400, pal.osv, Modifier.padding(top = 2.dp), lh = 1.4f)
                            }
                        }
                        if (m.err != null && m.status == 0) {
                            T("Download stopped. Tap Resume to try again.", 13f, 500, pal.oec, Modifier.padding(top = 8.dp), lh = 1.3f)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            when (m.status) {
                                0 -> {
                                    GBtn(if (m.got > 0L) 0 else 3, "dl", if (m.got > 0L) "Resume" else "Download", pal.p, pal.op, modifier = Modifier.weight(1f)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        askNotifOnce()
                                        vm.models.start(m)
                                    }
                                    if (m.got > 0L) GBtn(2, "del", null, pal.ec, pal.oec) { vm.models.delete(m) }
                                }
                                1 -> GBtn(3, "x", "Cancel", pal.tb2, pal.os, modifier = Modifier.fillMaxWidth()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.models.cancel(m)
                                }
                                else -> {
                                    GBtn(0, "ok", if (active) "In use" else "Use", if (active) pal.tb2 else pal.p, if (active) pal.os else pal.op, enabled = !active, modifier = Modifier.weight(1f)) {
                                        vm.setPr(vm.pr.copy(wm = info.key, eng = "off"))
                                    }
                                    GBtn(2, "del", null, pal.ec, pal.oec) { vm.models.delete(m) }
                                }
                            }
                        }
                    }
                }
            }
        }

        SGroup("Result", 120) {
            SCard(if (grOn) POS_TOP else POS_ONE) {
                T("Spoken language", 16f, 500, pal.os, Modifier.padding(bottom = 12.dp))
                SegPill(3, LANGS.indexOfFirst { it.first == vm.pr.gl }.coerceAtLeast(0), { vm.setPr(vm.pr.copy(gl = LANGS[it].first)) }) { i, fg ->
                    T(LANGS[i].second, 15f, 500, fg)
                }
                T("Pick the language you speak for better results.", 13f, 400, pal.osv, Modifier.padding(top = 10.dp), lh = 1.4f)
            }
            AnimatedVisibility(visible = grOn, enter = enter, exit = exit) {
                SCard(POS_BOT, onClick = { vm.setPr(vm.pr.copy(gf = !vm.pr.gf)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            T("Auto format", 16f, 500, pal.os)
                            T("Punctuation, bold and italics. Your words stay the same.", 13f, 400, pal.osv, lh = 1.4f)
                        }
                        MorphSwitch(vm.pr.gf)
                    }
                }
            }
        }
    }
}

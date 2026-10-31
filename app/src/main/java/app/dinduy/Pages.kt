@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.dinduy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------- util berbagi / ekspor ----------
fun noteToMd(n: Note): String = (if (n.t.isNotEmpty()) "# " + n.t + "\n\n" else "") + toMd(n.bl)

fun noteFileName(n: Note): String = (n.t.replace(Regex("[^\\w\\- ]+"), "").trim().ifEmpty { "catatan" }) + ".md"

private fun smooth(x: Float): Float {
    val k = x.coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

// ======================================================================================
// Kartu "Tambah catatan": muncul dari bawah, bisa diketuk (Tulis catatan) atau ditarik ke atas.
// Keduanya berakhir sama: bentuk kartu SAAT ITU diserahkan ke morph catatan (NoteLayer), jadi
// kartu melebar mulus menjadi halaman editor tanpa lompatan, entah dari posisi diam maupun
// dari posisi jari saat dilepas.
// ======================================================================================

/** Latar gelap di belakang kartu. Berada DI BAWAH NoteLayer supaya morph catatan tidak ikut digelapkan. */
@Composable
fun AddScrim(vm: AppVM) {
    if (!vm.addOn && !vm.addHand && vm.addF.value <= .0005f) return
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = (vm.addF.value / .3f).coerceIn(0f, 1f) * vm.addFade.value }
            .background(Color.Black.copy(alpha = .5f))
            .pointerInput(Unit) { detectTapGestures { if (vm.addOn) vm.addOn = false } }
    )
}

@Composable
fun AddNoteLayer(vm: AppVM, act: Actions) {
    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val sat = satDp()
    val sab = sabDp()
    var contentH by remember { mutableFloatStateOf(0f) }
    var boxH by remember { mutableFloatStateOf(0f) }
    var boxW by remember { mutableFloatStateOf(0f) }
    val st = remember { floatArrayOf(0f, 0f) }   // [0] pecahan saat menyeret, [1] 1 bila sudah melewati ambang
    // pecahan tinggi layar saat kartu diam (tinggi isi / tinggi layar)
    val restF = if (boxH > 0f && contentH > 0f) (contentH / boxH).coerceIn(.1f, .9f) else 0f
    val fSpec = spring<Float>(.82f, 380f, .0004f)

    fun pull(f: Float): Float = if (restF <= 0f) 0f else ((f - restF) / (1f - restF) * 1.8f).coerceIn(0f, 1f)

    LaunchedEffect(vm.addOn, restF) {
        if (vm.addOn) {
            if (restF > 0f) {
                vm.addFade.snapTo(1f)
                vm.addF.animateTo(restF, fSpec)
            }
        } else if (!vm.addHand && vm.addF.value > 0f) {
            vm.addF.animateTo(0f, spring(1f, 520f, .0004f))
        }
    }

    fun openNew() {
        if (vm.addHand || boxH <= 0f) return
        val f = vm.addF.value
        val pc = pull(f)
        val top = boxH * (1f - f)
        val rad = 40f * dens * (1f - pc)   // sama persis dengan radius yang sedang digambar kartu
        val col = androidx.compose.ui.graphics.lerp(pal.c3, pal.s, pc)
        val n = vm.newNote()
        vm.addHand = true
        vm.addOn = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.nav.openNote(n.id, Morph(0f, top, boxW, boxH, rad, col, floatArrayOf(rad, rad, 0f, 0f)), true)
        scope.launch {
            vm.addFade.animateTo(0f, tween(240))
            vm.addF.snapTo(0f)
            vm.addFade.snapTo(1f)
            vm.addHand = false
        }
    }

    val show = vm.addOn || vm.addHand || vm.addF.value > .0005f
    Box(Modifier.fillMaxSize().onSizeChanged { boxH = it.height.toFloat(); boxW = it.width.toFloat() }) {
        if (show) {
            Box(
                Modifier
                    .offset { IntOffset(0, (boxH * (1f - vm.addF.value)).roundToInt()) }
                    .fillMaxWidth()
                    .layout { m, c ->
                        val h = (boxH * vm.addF.value).roundToInt().coerceAtLeast(0)
                        val p = m.measure(Constraints.fixed(c.maxWidth, h))
                        layout(c.maxWidth, h) { p.place(0, 0) }
                    }
                    .graphicsLayer {
                        val r = 40.dp.toPx() * (1f - pull(vm.addF.value))
                        shape = RoundedCornerShape(CornerSize(r), CornerSize(r), CornerSize(0f), CornerSize(0f))
                        clip = true
                    }
                    .drawBehind {
                        if (!vm.addHand) drawRect(androidx.compose.ui.graphics.lerp(pal.c3, pal.s, pull(vm.addF.value)))
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { dy ->
                            if (boxH > 0f && !vm.addHand) {
                                st[0] = (st[0] - dy / boxH).coerceIn(.02f, 1f)
                                val crossed = st[0] - restF > (1f - restF) * .28f
                                if (crossed != (st[1] > 0f)) {
                                    st[1] = if (crossed) 1f else 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                scope.launch { vm.addF.snapTo(st[0]) }
                            }
                        },
                        onDragStarted = { vm.addF.stop(); st[0] = vm.addF.value; st[1] = 0f },
                        onDragStopped = { v ->
                            if (!vm.addHand && boxH > 0f) {
                                val f = vm.addF.value
                                when {
                                    (f - restF) > (1f - restF) * .28f || v < -1500f -> openNew()
                                    (restF - f) > restF * .38f || v > 1300f -> vm.addOn = false
                                    else -> vm.addF.animateTo(restF, fSpec, -v / boxH)
                                }
                            }
                        }
                    )
            ) {
                // pratinjau halaman catatan baru: muncul saat kartu ditarik naik, posisinya sama dengan bilah atas + judul editor
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = smooth((pull(vm.addF.value) - .25f) / .55f) * (vm.addFade.value * 1.4f - .4f).coerceIn(0f, 1f)
                    }
                ) {
                    Box(Modifier.padding(start = 12.dp, top = sat + 8.dp).size(52.dp), Alignment.Center) { Ic("back", pal.os, 24.dp) }
                    T("Judul", 34f, 1000, pal.osv, Modifier.padding(start = 24.dp, top = sat + 74.dp), ls = -.02f, alpha = .5f)
                }
                Column(
                    Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { contentH = it.height.toFloat() }
                        .graphicsLayer {
                            alpha = (1f - smooth(pull(vm.addF.value) * 2.2f)) * (vm.addFade.value * 1.7f - .7f).coerceIn(0f, 1f)
                        }
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + sab)
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), Alignment.Center) {
                        Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(pal.ol))
                    }
                    T("Tambah catatan", 26f, 1000, pal.os, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Opt(1, "txt", "Tulis catatan", "Ketuk, atau tarik kartu ini ke atas") { openNew() }
                    Opt(2, "up", "Impor berkas", "Berkas .md atau .txt") {
                        if (!vm.addHand) { vm.addOn = false; act.importMd() }
                    }
                }
            }
        }
    }
}

// ======================================================================================
// Halaman bergaya Pengaturan (Pixel Settings + Material 3 Expressive)
// ======================================================================================

/** Kerangka halaman: judul besar yang mengecil saat digulir, bilah atas yang berubah warna, isi dalam grup kartu bersambung. */
@Composable
fun PageScaffold(title: String, sub: String?, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    val sat = satDp()
    val sab = sabDp()
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(pal.s)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(sat + 64.dp))
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + sab)
            ) {
                Column(
                    Modifier.padding(start = 8.dp, top = 20.dp, bottom = 28.dp).graphicsLayer {
                        val k = (scroll.value / 110.dp.toPx()).coerceIn(0f, 1f)
                        alpha = 1f - (k * 1.6f).coerceAtMost(1f)
                        scaleX = 1f - .12f * k; scaleY = 1f - .12f * k
                        transformOrigin = TransformOrigin(0f, .5f)
                    }
                ) {
                    T(title, 40f, 400, pal.os, ls = -.01f, maxLines = 1)
                    if (sub != null) T(sub, 16f, 400, pal.osv, Modifier.padding(top = 6.dp), maxLines = 1)
                }
                content()
            }
        }
        Row(
            Modifier.fillMaxWidth().height(sat + 64.dp)
                .drawBehind {
                    val k = (scroll.value / 80.dp.toPx()).coerceIn(0f, 1f)
                    drawRect(androidx.compose.ui.graphics.lerp(pal.s, pal.c2, k))
                }
                .padding(top = sat, start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("back", pal.os, onBack)
            T(
                title, 22f, 400, pal.os,
                Modifier.padding(start = 8.dp).graphicsLayer {
                    val k = (scroll.value / 110.dp.toPx()).coerceIn(0f, 1f)
                    val a = ((k - .5f) * 2f).coerceIn(0f, 1f)
                    alpha = a; translationY = (1f - a) * 12.dp.toPx()
                },
                maxLines = 1
            )
        }
    }
}

/** Baris aksi di grup kartu bersambung (sama dengan SActionRow, plus mode berbahaya dan panah). */
@Composable
fun MRow(pos: Int, icon: String, title: String, sub: String? = null, danger: Boolean = false, chevron: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPal.current
    SCard(pos, onClick, PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (danger) pal.ec else pal.pc), Alignment.Center) {
                Ic(icon, if (danger) pal.oec else pal.opc, 22.dp)
            }
            Column(Modifier.weight(1f)) {
                T(title, 16f, 500, pal.os)
                if (sub != null) T(sub, 14f, 400, pal.osv, lh = 1.3f)
            }
            if (chevron) Ic("chr", pal.osv, 24.dp)
        }
    }
}

/** Kotak centang M3 Expressive: sudut membulat jadi lingkaran saat terpilih, ceklis membesar dengan pegas. */
@Composable
fun MorphCheck(on: Boolean) {
    val pal = LocalPal.current
    val k by animateFloatAsState(if (on) 1f else 0f, spring(.7f, 420f), label = "mc")
    val shape = RoundedCornerShape((8f + 6f * k).dp)
    val bg = androidx.compose.ui.graphics.lerp(Color.Transparent, pal.p, k)
    val edge = androidx.compose.ui.graphics.lerp(pal.osv, pal.p, k)
    Box(Modifier.size(28.dp).clip(shape).background(bg).border(2.dp, edge, shape), Alignment.Center) {
        Box(Modifier.graphicsLayer { alpha = k; scaleX = k; scaleY = k }) { Ic("ok", pal.op, 18.dp) }
    }
}

/** Halaman titik tiga di editor. */
@Composable
fun NoteMenuPage(vm: AppVM, nav: Nav, act: Actions, id: String) {
    val d = vm.note(id)
    val pinned = d?.p == true
    val archived = d?.a == true
    PageScaffold("Opsi catatan", d?.t?.ifEmpty { null } ?: "Tanpa judul", { nav.pop() }) {
        SGroup("Catatan ini", 0) {
            MRow(POS_TOP, "search", "Cari dalam catatan", "Temukan dan sorot kata di catatan ini") {
                nav.pop(); vm.ed?.openFind()
            }
            MRow(POS_MID, "pin", if (pinned) "Lepas sematan" else "Sematkan", "Catatan yang disematkan tampil di bagian atas") {
                vm.mark(listOf(id), if (pinned) "Sematan dilepas" else "Catatan disematkan") { it.copy(p = !it.p) }
            }
            MRow(POS_BOT, "label", "Label", d?.lb?.joinToString(", ")?.ifEmpty { null } ?: "Atur label untuk catatan ini", chevron = true) {
                nav.push(Scr.NoteLabels(id))
            }
        }
        SGroup("Bagikan dan ekspor", 60) {
            MRow(POS_TOP, "up", "Bagikan sebagai .md", "Kirim catatan sebagai berkas Markdown lewat WhatsApp, Berkas, dan lainnya") {
                d?.let { act.shareMd(noteFileName(it), noteToMd(it)) }
            }
            MRow(POS_MID, "dl", "Simpan sebagai .md", "Simpan berkas Markdown ke perangkat") {
                d?.let { act.saveMd(noteFileName(it), noteToMd(it)) }
            }
            MRow(POS_BOT, "dup", "Salin sebagai Markdown", "Salin teks catatan ke papan klip") {
                d?.let { act.copyText(noteToMd(it)); vm.show("Disalin ke papan klip") }
            }
        }
        SGroup("Kelola", 120) {
            MRow(POS_TOP, "lib", "Gandakan", "Buat salinan catatan ini") { vm.duplicate(id) }
            MRow(POS_MID, if (archived) "unarc" else "arc", if (archived) "Keluarkan dari arsip" else "Arsipkan", "Disimpan di Arsip, tidak muncul di beranda") {
                if (archived) vm.mark(listOf(id), "Dikeluarkan dari arsip") { it.copy(a = false) }
                else { nav.pop(); nav.pop(); vm.mark(listOf(id), "Catatan diarsipkan") { it.copy(a = true, p = false) } }
            }
            MRow(POS_BOT, "del", "Hapus catatan", "Dipindah ke sampah selama 30 hari", danger = true) {
                nav.pop(); nav.pop(); vm.mark(listOf(id), "Dipindah ke sampah") { it.copy(dt = System.currentTimeMillis()) }
            }
        }
    }
}

/** Halaman label untuk satu catatan: buat label baru langsung di halaman, lalu pilih label yang dipasang. */
@Composable
fun NoteLabelsPage(vm: AppVM, nav: Nav, id: String) {
    val pal = LocalPal.current
    val d = vm.note(id)
    var text by remember { mutableStateOf("") }
    val labels = vm.sortedLabels()
    val add = { if (text.isNotBlank() && vm.addLabel(text, id)) text = "" }
    PageScaffold("Label", d?.t?.ifEmpty { null } ?: "Tanpa judul", { nav.pop() }) {
        SGroup("Buat label baru", 0) {
            SCard(POS_ONE, pad = PaddingValues(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic("label", pal.p, 24.dp)
                    Spacer(Modifier.width(16.dp))
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(30) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 17.sp, fontWeight = FontWeight(500)),
                        singleLine = true, cursorBrush = SolidColor(pal.p),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        decorationBox = { inner -> Box { if (text.isEmpty()) T("Nama label", 17f, 500, pal.osv, maxLines = 1); inner() } }
                    )
                    IconBtn("plus", if (text.isNotBlank()) pal.p else pal.osv.copy(alpha = .4f), { add() }, size = 48.dp, enabled = text.isNotBlank())
                }
            }
        }
        SGroup("Pilih label", 60) {
            Column(Modifier.animateContentSize(SizeSpring), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
                if (labels.isEmpty()) {
                    SCard(POS_ONE) { T("Belum ada label. Buat satu di atas, otomatis terpasang ke catatan ini.", 14f, 400, pal.osv, lh = 1.5f) }
                } else labels.forEachIndexed { ix, n ->
                    val pos = if (labels.size == 1) POS_ONE else if (ix == 0) POS_TOP else if (ix == labels.lastIndex) POS_BOT else POS_MID
                    val on = d != null && n in d.lb
                    SCard(pos, { vm.toggleLabel(id, n) }, PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(pal.pc), Alignment.Center) { Ic("label", pal.opc, 22.dp) }
                            T(n, 16f, 500, pal.os, Modifier.weight(1f), maxLines = 1)
                            MorphCheck(on)
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================================
// Bilah "cari dalam catatan" (menggantikan bilah atas editor)
// ======================================================================================
@Composable
fun FindBar(ed: Ed, fa: Float) {
    val pal = LocalPal.current
    val fr = remember { FocusRequester() }
    val focusMgr = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    // tunggu halaman opsi selesai bergeser keluar, baru fokus + keyboard
    LaunchedEffect(Unit) { delay(430); try { fr.requestFocus() } catch (e: Exception) { } }
    val n = ed.hitList().size
    val cur = if (n == 0) 0 else ed.findI.coerceIn(0, n - 1) + 1
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer { alpha = fa; translationY = (1f - fa) * 10.dp.toPx() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn("back", pal.os, { ed.closeFind(); focusMgr.clearFocus(); kb?.hide() })
        Box(Modifier.weight(1f).height(52.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
            Row(
                Modifier.fillMaxWidth(.35f + .65f * fa).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(pal.c3)
                    .padding(start = 18.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = ed.findQ, onValueChange = { ed.findQ = it; ed.findI = 0 },
                    modifier = Modifier.weight(1f).focusRequester(fr),
                    textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 17.sp, fontWeight = FontWeight(500)),
                    singleLine = true, cursorBrush = SolidColor(pal.p),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { ed.findStep(1) }),
                    decorationBox = { inner -> Box { if (ed.findQ.isEmpty()) T("Cari dalam catatan", 17f, 500, pal.osv, maxLines = 1); inner() } }
                )
                if (ed.findQ.isNotBlank()) T("$cur/$n", 14f, 700, if (n == 0) pal.osv else pal.p, Modifier.padding(start = 8.dp), maxLines = 1)
            }
        }
        IconBtn("aup", if (n > 0) pal.os else pal.osv.copy(alpha = .4f), { ed.findStep(-1) }, size = 44.dp, enabled = n > 0)
        IconBtn("adn", if (n > 0) pal.os else pal.osv.copy(alpha = .4f), { ed.findStep(1) }, size = 44.dp, enabled = n > 0)
    }
}

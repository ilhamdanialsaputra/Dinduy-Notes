@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.dinduy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

/** Nama berkas bagikan Dinduy: judul catatan + .dinduy. */
fun noteShareName(n: Note): String = noteFileName(n).removeSuffix(".md") + ".dinduy"

private fun smooth(x: Float): Float {
    val k = x.coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

// ======================================================================================
// Kartu "Tambah catatan": naik dari bawah layar dengan pegas (sedikit memantul), warnanya sama dengan kertas
// editor. Bisa diketuk (Tulis catatan) atau ditarik ke atas. Keduanya berakhir sama: bentuk kartu SAAT ITU
// diserahkan ke morph catatan (NoteLayer), jadi kartu melebar mulus menjadi editor tanpa lompatan warna
// maupun bentuk, entah dari posisi diam maupun dari posisi jari saat dilepas.
// ======================================================================================

/** Latar gelap di belakang kartu. Berada DI BAWAH NoteLayer supaya morph catatan tidak ikut digelapkan. */
@Composable
fun AddScrim(vm: AppVM) {
    if (!vm.addOn && !vm.addHand && vm.addF.value <= .0005f) return
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = smooth(vm.addF.value / .28f) * vm.addFade.value }
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
    val inSpec = spring<Float>(.74f, 300f, .0004f)    // masuk: naik lalu sedikit memantul sebelum diam
    val fSpec = spring<Float>(.82f, 380f, .0004f)     // kembali ke posisi diam setelah ditarik

    fun pull(f: Float): Float = if (restF <= 0f) 0f else ((f - restF) / (1f - restF) * 1.8f).coerceIn(0f, 1f)
    /** Kemunculan kartu 0..1 (1 = sudah di posisi diam); mengatur stagger isi. */
    fun rise(): Float = if (restF <= 0f) 0f else (vm.addF.value / restF).coerceIn(0f, 1f)
    fun colorNow(): Color = androidx.compose.ui.graphics.lerp(pal.s, pal.s, 0f)

    LaunchedEffect(vm.addOn, restF) {
        if (vm.addOn) {
            if (restF > 0f) {
                vm.addFade.snapTo(1f)
                vm.addF.animateTo(restF, inSpec)
            }
        } else if (!vm.addHand && vm.addF.value > 0f) {
            vm.addF.animateTo(0f, spring(1f, 520f, .0004f))
        }
    }

    fun openNew(voice: Boolean = false) {
        if (vm.addHand || boxH <= 0f) return
        val f = vm.addF.value
        val top = boxH * (1f - f)
        val rad = 40f * dens * (1f - pull(f))
        val n = vm.newNote()
        vm.addHand = true
        vm.addOn = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.nav.openNote(n.id, Morph(0f, top, boxW, boxH, rad, colorNow(), floatArrayOf(rad, rad, 0f, 0f), card = true, up = true), !voice)
        if (voice) vm.startRec(n.id)   // izin mikrofon sudah didapat sebelum catatan dibuat
        scope.launch {
            vm.addFade.animateTo(0f, tween(240))
            vm.addF.snapTo(0f); vm.addFade.snapTo(1f)
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
                        val h = (boxH * vm.addF.value).roundToInt().coerceIn(0, c.maxHeight)
                        val p = m.measure(Constraints.fixed(c.maxWidth, h))
                        layout(c.maxWidth, h) { p.place(0, 0) }
                    }
                    .graphicsLayer {
                        val r = 40.dp.toPx() * (1f - pull(vm.addF.value))
                        shape = RoundedCornerShape(CornerSize(r), CornerSize(r), CornerSize(0f), CornerSize(0f))
                        clip = true
                    }
                    .drawBehind { if (!vm.addHand) drawRect(colorNow()) }
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
                    T("Title", 34f, 1000, pal.osv, Modifier.padding(start = 24.dp, top = sat + 74.dp), ls = -.02f, alpha = .5f)
                }
                // tiap elemen naik + muncul bergiliran (stagger) mengikuti kartu yang sedang naik;
                // semuanya pudar saat ditarik jauh atau diserahkan ke editor
                fun stag(i: Int): Modifier = Modifier.graphicsLayer {
                    val k = smooth((rise() - (.35f + .12f * i)) / .5f)
                    alpha = k * (1f - smooth(pull(vm.addF.value) * 2.2f)) * (vm.addFade.value * 1.7f - .7f).coerceIn(0f, 1f)
                    translationY = (1f - k) * 28.dp.toPx()
                }
                Column(
                    Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { contentH = it.height.toFloat() }
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + sab)
                ) {
                    Box(stag(0).fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), Alignment.Center) {
                        Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(pal.ol))
                    }
                    T("Add note", 26f, 1000, pal.os, stag(1).padding(start = 12.dp, end = 12.dp, bottom = 14.dp), ls = -.01f)
                    Box(stag(2)) { Opt(1, "txt", "Write a note", "Tap, or pull this card up") { openNew() } }
                    Box(stag(3)) {
                        Opt(2, "up", "Import file", "PDF, .md, or .txt files. Pick as many as you like") {
                            if (!vm.addHand) { vm.addOn = false; act.importMd() }
                        }
                    }
                    Box(stag(4)) {
                        Opt(3, "mic", "Voice note", "New note that starts recording") {
                            if (!vm.addHand) act.requestMic { if (vm.addOn && !vm.addHand) openNew(true) }
                        }
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
    Box(Modifier.fillMaxSize().background(pal.bg)) {
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
                    drawRect(androidx.compose.ui.graphics.lerp(pal.bg, pal.c2, k))
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

/** Ikon bagikan universal: tiga titik (node) yang tersambung, seperti di Android / Google. */
@Composable
private fun ShareNodesBtn(tint: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            val k = size.width / 24f
            val a = androidx.compose.ui.geometry.Offset(18f * k, 5f * k)
            val b = androidx.compose.ui.geometry.Offset(6f * k, 12f * k)
            val c = androidx.compose.ui.geometry.Offset(18f * k, 19f * k)
            val sw = 1.9f * k
            drawLine(tint, b, a, sw, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(tint, b, c, sw, androidx.compose.ui.graphics.StrokeCap.Round)
            val r = 3.1f * k
            drawCircle(tint, r, a); drawCircle(tint, r, b); drawCircle(tint, r, c)
        }
    }
}

/** Satu baris untuk dua aksi Markdown: simpan, salin. (Bagikan Markdown diganti berkas Dinduy.) */
@Composable
private fun MdRow(pos: Int, onSave: () -> Unit, onCopy: () -> Unit) {
    val pal = LocalPal.current
    SCard(pos, null, PaddingValues(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Text only", 16f, 500, pal.os)
                T("Save or copy the words", 14f, 400, pal.osv, lh = 1.3f)
            }
            IconBtn("dl", pal.os, onSave, size = 44.dp, bg = pal.c4)
            Spacer(Modifier.width(6.dp))
            IconBtn("dup", pal.os, onCopy, size = 44.dp, bg = pal.c4)
        }
    }
}

/** Bagikan satu catatan sebagai berkas Dinduy: hanya bisa dibuka di Dinduy, gambar, coretan, suara, dan PDF ikut. */
@Composable
private fun DinduyRow(pos: Int, onShare: () -> Unit) {
    val pal = LocalPal.current
    SCard(pos, null, PaddingValues(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Share note", 16f, 500, pal.os)
                T("Opens in Dinduy, with everything in it", 14f, 400, pal.osv, lh = 1.3f)
            }
            ShareNodesBtn(pal.os, pal.c4, onShare)
        }
    }
}

/**
 * Halaman titik tiga di editor, diringkas dan diurutkan dari yang paling sering dipakai.
 * Pengingat tidak ada di sini karena sudah ada tombol lonceng di bilah atas editor.
 * Bagikan berkas Dinduy satu baris; simpan / salin Markdown satu baris.
 */
@Composable
fun NoteMenuPage(vm: AppVM, nav: Nav, act: Actions, id: String) {
    val d = vm.note(id)
    val pinned = d?.p == true
    val archived = d?.a == true
    PageScaffold("Note options", d?.t?.ifEmpty { null } ?: "Untitled", { nav.pop() }) {
        SGroup("This note", 0) {
            MRow(POS_TOP, "pin", if (pinned) "Unpin" else "Pin", "Pinned notes show at the top") {
                vm.mark(listOf(id), if (pinned) "Note unpinned" else "Note pinned") { it.copy(p = !it.p) }
            }
            MRow(POS_MID, "img", "Cover", if (d?.cv.isNullOrEmpty()) "Add an image at the top of the note" else "Change or crop the cover image", chevron = true) {
                if (d?.cv.isNullOrEmpty()) act.pickCover(id) else nav.push(Scr.Cover(id))
            }
            MRow(POS_MID, "label", "Labels", d?.lb?.joinToString(", ")?.ifEmpty { null } ?: "Set labels for this note", chevron = true) {
                nav.push(Scr.NoteLabels(id))
            }
            MRow(POS_BOT, "search", "Search in note", "Find and highlight words in this note") {
                nav.pop(); vm.ed?.openFind()
            }
        }
        SGroup("Share and export", 60) {
            MRow(POS_TOP, "txt", "Export PDF", "Preview each page, set the text size, then save or share. Drawings are included, voice recordings are not", chevron = true) {
                nav.push(Scr.PdfExport(id))
            }
            DinduyRow(POS_MID) { vm.onShareNote?.invoke(id) }
            MdRow(
                POS_BOT,
                onSave = { d?.let { act.saveMd(noteFileName(it), noteToMd(it)) } },
                onCopy = { d?.let { act.copyText(noteToMd(it)); vm.show("Copied to clipboard") } }
            )
        }
        SGroup("Manage", 120) {
            MRow(POS_TOP, if (archived) "unarc" else "arc", if (archived) "Unarchive" else "Archive", "Moved to Archive and hidden from home") {
                if (archived) vm.mark(listOf(id), "Note unarchived") { it.copy(a = false) }
                else { nav.pop(); nav.pop(); vm.mark(listOf(id), "Note archived") { it.copy(a = true, p = false) } }
            }
            MRow(POS_MID, "lib", "Duplicate", "Make a copy of this note") { vm.duplicate(id) }
            MRow(POS_BOT, "del", "Delete note", "Kept in Trash for 30 days", danger = true) {
                nav.pop(); nav.pop(); vm.mark(listOf(id), "Moved to Trash") { it.copy(dt = System.currentTimeMillis()) }
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
    PageScaffold("Labels", d?.t?.ifEmpty { null } ?: "Untitled", { nav.pop() }) {
        SGroup("Create new label", 0) {
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
                        decorationBox = { inner -> Box { if (text.isEmpty()) T("Label name", 17f, 500, pal.osv, maxLines = 1); inner() } }
                    )
                    IconBtn("plus", if (text.isNotBlank()) pal.p else pal.osv.copy(alpha = .4f), { add() }, size = 48.dp, enabled = text.isNotBlank())
                }
            }
        }
        SGroup("Choose labels", 60) {
            Column(Modifier.animateContentSize(SizeSpring), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
                if (labels.isEmpty()) {
                    SCard(POS_ONE) { T("No labels yet. Create one above and it's added to this note.", 14f, 400, pal.osv, lh = 1.5f) }
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
// Lencana label di atas judul editor
// ======================================================================================
@Composable
fun LabelBadge(name: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    val inK = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { inK.animateTo(1f, spring(.6f, 420f)) }
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by androidx.compose.animation.core.animateDpAsState(if (pr) 10.dp else 18.dp, spring(.6f, 500f), label = "lr")
    Row(
        Modifier
            .graphicsLayer { val k = inK.value; alpha = k.coerceIn(0f, 1f); scaleX = .7f + .3f * k; scaleY = .7f + .3f * k }
            .clip(RoundedCornerShape(r)).background(pal.pc)
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic("label", pal.opc, 15.dp)
        Spacer(Modifier.width(6.dp))
        T(name, 13f, 700, pal.opc, maxLines = 1)
    }
}

// ======================================================================================
// Bilah "cari dalam catatan" (menggantikan bilah atas editor). Pil tumbuh dari tombol titik tiga
// ke kiri; isinya (kolom, hitungan, panah) muncul belakangan dan memudar lebih dulu saat menutup.
// ======================================================================================
@Composable
fun FindBar(ed: Ed, fa: Float) {
    val pal = LocalPal.current
    val fr = remember { FocusRequester() }
    val focusMgr = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    // buka: tunggu halaman opsi selesai bergeser keluar, baru fokus + keyboard. tutup: keyboard turun bersamaan dengan pil menguncup
    LaunchedEffect(ed.findOn) {
        if (ed.findOn) { delay(430); try { fr.requestFocus() } catch (e: Exception) { } }
        else { focusMgr.clearFocus(); kb?.hide() }
    }
    // hitungan dibekukan saat menutup (hitList kosong begitu findOn = false), supaya tidak berkedip "0/0"
    val keep = remember { intArrayOf(0, 0) }
    if (ed.findOn) {
        val nn = ed.hitList().size
        keep[0] = nn
        keep[1] = if (nn == 0) 0 else ed.findI.coerceIn(0, nn - 1) + 1
    }
    val n = keep[0]
    val cur = keep[1]
    val inK = smooth((fa - .4f) / .5f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Spacer(Modifier.width(52.dp))
        Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.CenterEnd) {
            Row(
                Modifier
                    .layout { m, c ->
                        val minW = 52.dp.toPx()
                        val w = (minW + (c.maxWidth - minW) * fa).roundToInt().coerceIn(0, c.maxWidth)
                        val p = m.measure(Constraints.fixed(w, c.maxHeight))
                        layout(w, c.maxHeight) { p.place(0, 0) }
                    }
                    .graphicsLayer { shape = RoundedCornerShape(26.dp); clip = true; alpha = smooth(fa / .45f) }
                    .background(pal.c3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.fillMaxSize().graphicsLayer { alpha = inK }.padding(start = 18.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = ed.findQ, onValueChange = { ed.findQ = it; ed.findI = 0 },
                        modifier = Modifier.weight(1f).focusRequester(fr),
                        textStyle = TextStyle(color = pal.os, fontFamily = LocalFF.current, fontSize = 17.sp, fontWeight = FontWeight(500)),
                        singleLine = true, cursorBrush = SolidColor(pal.p),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { ed.findStep(1) }),
                        decorationBox = { inner -> Box { if (ed.findQ.isEmpty()) T("Search in note", 17f, 500, pal.osv, maxLines = 1); inner() } }
                    )
                    if (ed.findQ.isNotBlank()) T("$cur/$n", 13f, 700, if (n == 0) pal.osv else pal.p, Modifier.padding(horizontal = 6.dp), maxLines = 1)
                    IconBtn("aup", if (n > 0) pal.os else pal.osv.copy(alpha = .4f), { ed.findStep(-1) }, size = 40.dp, enabled = n > 0 && ed.findOn)
                    IconBtn("adn", if (n > 0) pal.os else pal.osv.copy(alpha = .4f), { ed.findStep(1) }, size = 40.dp, enabled = n > 0 && ed.findOn)
                }
            }
        }
    }
}

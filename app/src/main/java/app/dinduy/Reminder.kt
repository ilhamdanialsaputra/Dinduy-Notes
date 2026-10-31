@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dinduy

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ======================================================================================
// Pengingat catatan (seperti Google Keep): alarm tepat waktu -> notifikasi di HP.
//
// Data pengingat disimpan di Note: rm = waktu bunyi pertama, rr = aturan ulang (d/w/m/y).
// Waktu bunyi berikutnya SELALU dihitung dari rm + rr (tidak ada data yang diubah oleh alarm),
// jadi pengingat berulang tidak pernah "melenceng" dan tidak ada tabrakan tulis dengan aplikasi.
// ======================================================================================
object Rem {
    const val CHANNEL = "dinduy_reminders"
    const val EXTRA_NOTE = "dinduy.note"
    const val A_FIRE = "app.dinduy.REM_FIRE"      // alarm utama
    const val A_SNZ = "app.dinduy.REM_SNZ"        // alarm tunda satu kali (untuk pengingat berulang)
    const val A_DONE = "app.dinduy.REM_DONE"      // tombol "Selesai" di notifikasi
    const val A_LATER = "app.dinduy.REM_LATER"    // tombol "Tunda 1 jam"
    const val A_TMR = "app.dinduy.REM_TMR"        // tombol "Besok pagi"
    private const val DAY = 86_400_000L
    private val ID = Locale("id", "ID")

    /** ViewModel yang sedang hidup (bila ada), supaya perubahan dari notifikasi lewat jalur data yang sama dengan aplikasi. */
    @Volatile
    var hook: AppVM? = null

    val REPEATS = listOf("" to "Tidak berulang", "d" to "Setiap hari", "w" to "Setiap minggu", "m" to "Setiap bulan", "y" to "Setiap tahun")
    fun repeatName(rr: String): String = REPEATS.firstOrNull { it.first == rr }?.second ?: "Tidak berulang"

    // ---------- waktu ----------
    private fun occ(base: Long, rr: String, k: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = base
        when (rr) {
            "d" -> c.add(Calendar.DAY_OF_YEAR, k)
            "w" -> c.add(Calendar.WEEK_OF_YEAR, k)
            "m" -> c.add(Calendar.MONTH, k)
            "y" -> c.add(Calendar.YEAR, k)
        }
        return c.timeInMillis
    }

    /** Waktu bunyi pertama yang lebih besar dari [after]; 0 bila tidak ada (pengingat sekali yang sudah lewat). */
    fun next(rm: Long, rr: String, after: Long): Long {
        if (rm <= 0L) return 0L
        if (rm > after) return rm
        if (rr.isEmpty()) return 0L
        val unit = when (rr) {
            "d" -> DAY
            "w" -> 7 * DAY
            "m" -> 32 * DAY
            else -> 367 * DAY
        }
        // perkiraan indeks dibulatkan ke bawah, lalu naik satu-satu sampai melewati [after]
        var k = ((after - rm) / unit).toInt() - 1
        if (k < 0) k = 0
        var t = occ(rm, rr, k)
        var guard = 0
        while (t <= after && guard < 2000) {
            k++; t = occ(rm, rr, k); guard++
        }
        return if (t > after) t else 0L
    }

    fun overdue(n: Note, now: Long = System.currentTimeMillis()): Boolean = n.rm > 0L && next(n.rm, n.rr, now) == 0L

    fun sortKey(n: Note): Long {
        val t = next(n.rm, n.rr, System.currentTimeMillis())
        return if (t > 0L) t else n.rm
    }

    /** Teks singkat waktu pengingat (bunyi berikutnya, atau waktu yang terlewat). */
    fun stamp(n: Note): String {
        val t = next(n.rm, n.rr, System.currentTimeMillis())
        return fmt(if (t > 0L) t else n.rm)
    }

    private fun midnight(t: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun dayDiff(t: Long, now: Long): Int = Math.round((midnight(t) - midnight(now)) / DAY.toDouble()).toInt()

    private fun year(t: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        return c.get(Calendar.YEAR)
    }

    fun fmt(t: Long, now: Long = System.currentTimeMillis()): String {
        val tm = SimpleDateFormat("HH.mm", ID).format(Date(t))
        val dd = dayDiff(t, now)
        return when {
            dd == 0 -> "Hari ini, $tm"
            dd == 1 -> "Besok, $tm"
            dd == -1 -> "Kemarin, $tm"
            dd in 2..6 -> SimpleDateFormat("EEE", ID).format(Date(t)) + ", $tm"
            year(t) == year(now) -> SimpleDateFormat("d MMM", ID).format(Date(t)) + ", $tm"
            else -> SimpleDateFormat("d MMM yyyy", ID).format(Date(t)) + ", $tm"
        }
    }

    /** Hari ke-[dayOff] dari [base] pada pukul [h]:[m]. */
    fun at(base: Long, dayOff: Int, h: Int, m: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = base
        c.add(Calendar.DAY_OF_YEAR, dayOff)
        c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun tomorrowMorning(now: Long = System.currentTimeMillis()): Long = at(now, 1, 8, 0)

    /** Jam bulat berikutnya (dipakai sebagai nilai awal pilihan tanggal & waktu). */
    fun defaultAt(now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        val t = at(now, 0, c.get(Calendar.HOUR_OF_DAY) + 1, 0)
        return if (t - now < 20 * 60_000L) t + 3_600_000L else t
    }

    class RQ(val title: String, val icon: String, val at: Long, val tail: String)

    /** Pilihan cepat ala Google Keep: nanti hari ini, besok pagi, minggu depan. */
    fun quick(now: Long = System.currentTimeMillis()): List<RQ> {
        val out = ArrayList<RQ>()
        val hm = SimpleDateFormat("HH.mm", ID)
        for (h in intArrayOf(13, 18, 20)) {
            val t = at(now, 0, h, 0)
            if (t - now >= 45 * 60_000L) {
                out.add(RQ("Nanti hari ini", if (h >= 18) "dm" else "clock", t, hm.format(Date(t))))
                break
            }
        }
        val tm = at(now, 1, 8, 0)
        out.add(RQ("Besok pagi", "lm", tm, hm.format(Date(tm))))
        val c = Calendar.getInstance()
        c.timeInMillis = now
        var add = (Calendar.MONDAY - c.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (add == 0) add = 7
        if (add != 1) {
            val nw = at(now, add, 8, 0)
            out.add(RQ("Minggu depan", "cal", nw, SimpleDateFormat("EEE, HH.mm", ID).format(Date(nw))))
        }
        return out
    }

    // ---------- alarm ----------
    private fun pi(ctx: Context, id: String, action: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("dinduy://rem/" + Uri.encode(id) + "/" + action.substringAfterLast('_')))
            .putExtra(EXTRA_NOTE, id)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("ScheduleExactAlarm")
    fun setAt(ctx: Context, id: String, at: Long, action: String = A_FIRE) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p = pi(ctx, id, action)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    private fun cancelAlarm(ctx: Context, id: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, id, A_FIRE))
    }

    private fun cancelNotif(ctx: Context, id: String) {
        NotificationManagerCompat.from(ctx).cancel(id.hashCode())
    }

    /** Samakan alarm dengan [docs]: pasang yang baru/berubah, batalkan yang sudah tidak berlaku. [cur] = catatan alarm yang sudah dipasang. */
    fun sync(ctx: Context, docs: List<Note>, cur: MutableMap<String, Long>) {
        val now = System.currentTimeMillis()
        val want = HashMap<String, Long>()
        for (d in docs) {
            if (d.dt == 0L && d.rm > 0L) {
                val t = next(d.rm, d.rr, now)
                if (t > 0L) want[d.id] = t
            }
        }
        for ((id, t) in want) {
            if (cur[id] != t) {
                setAt(ctx, id, t); cur[id] = t
            }
        }
        for (id in cur.keys.filter { it !in want }) {
            cancelAlarm(ctx, id); cur.remove(id)
        }
    }

    // ---------- akses data tanpa UI (dipakai penerima siaran) ----------
    private fun dbFile(ctx: Context) = File(ctx.filesDir, "dinduy.json")

    private fun readDb(ctx: Context): Db? = try {
        val f = dbFile(ctx)
        if (f.exists()) J.decodeFromString(Db.serializer(), f.readText()) else null
    } catch (e: Exception) {
        null
    }

    private fun findNote(ctx: Context, id: String): Note? =
        hook?.note(id) ?: readDb(ctx)?.docs?.firstOrNull { it.id == id }

    /** Ubah satu catatan: lewat ViewModel bila aplikasi sedang hidup (aman dari tabrakan), kalau tidak langsung ke berkas. */
    private fun mutate(ctx: Context, id: String, f: (Note) -> Note) {
        val h = hook
        if (h != null) {
            h.upd(id, f); return
        }
        val db = readDb(ctx) ?: return
        val i = db.docs.indexOfFirst { it.id == id }
        if (i < 0) return
        val list = db.docs.toMutableList()
        list[i] = f(list[i])
        try {
            val file = dbFile(ctx)
            val tmp = File(file.path + ".tmp")
            tmp.writeText(J.encodeToString(Db.serializer(), db.copy(docs = list)))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText()); tmp.delete()
            }
        } catch (e: Exception) {
        }
    }

    // ---------- notifikasi ----------
    fun notifOk(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Pengingat", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Pengingat catatan pada tanggal dan waktu yang kamu atur"
            ch.enableVibration(true)
            ch.enableLights(true)
            ch.setShowBadge(true)
            nm.createNotificationChannel(ch)
        }
    }

    private const val BELL = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"

    /** Ikon status bar: lonceng putih dari path yang sama dengan ikon di aplikasi (tanpa berkas resource tambahan). */
    private fun statIcon(): IconCompat {
        val sz = 96
        val bm = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val path = androidx.core.graphics.PathParser.createPathFromPathData(BELL)
        val m = Matrix()
        m.setScale(sz / 24f, sz / 24f)
        path.transform(m)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.WHITE
        Canvas(bm).drawPath(path, p)
        return IconCompat.createWithBitmap(bm)
    }

    fun notify(ctx: Context, n: Note) {
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        ensureChannel(ctx)
        val title = n.t.trim().ifEmpty { "Pengingat" }
        val body = n.x.replace(Regex("\\s+"), " ").trim()
        val open = Intent(ctx, MainActivity::class.java)
            .setData(Uri.parse("dinduy://open/" + Uri.encode(n.id)))
            .putExtra(EXTRA_NOTE, n.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openPi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(statIcon())
            .setContentTitle(title)
            .setContentText(body.ifEmpty { "Ketuk untuk membuka catatan" }.take(140))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifEmpty { "Ketuk untuk membuka catatan" }.take(500)))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(0xFF6750A4.toInt())
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .addAction(0, "Selesai", pi(ctx, n.id, A_DONE))
            .addAction(0, "Tunda 1 jam", pi(ctx, n.id, A_LATER))
            .addAction(0, "Besok pagi", pi(ctx, n.id, A_TMR))
        try {
            nmc.notify(n.id.hashCode(), b.build())
        } catch (e: SecurityException) {
        }
    }

    // ---------- kejadian ----------
    fun onFire(ctx: Context, id: String, snooze: Boolean) {
        val n = findNote(ctx, id) ?: return
        if (n.dt != 0L || n.rm <= 0L) return
        notify(ctx, n)
        if (snooze) return
        if (n.rr.isNotEmpty()) {
            val t = next(n.rm, n.rr, System.currentTimeMillis() + 1000L)
            if (t > 0L) setAt(ctx, id, t)
        } else {
            mutate(ctx, id) { it.copy(rd = it.rm) }
        }
    }

    fun onDone(ctx: Context, id: String) {
        cancelNotif(ctx, id)
        val n = findNote(ctx, id) ?: return
        // pengingat sekali: selesai = pengingat dihapus. Pengingat berulang tetap berjalan ke jadwal berikutnya.
        if (n.rr.isEmpty()) mutate(ctx, id) { it.copy(rm = 0L, rd = 0L) }
    }

    fun onSnooze(ctx: Context, id: String, at: Long) {
        cancelNotif(ctx, id)
        val n = findNote(ctx, id) ?: return
        if (n.rr.isEmpty()) {
            mutate(ctx, id) { it.copy(rm = at, rd = 0L) }
            setAt(ctx, id, at)
        } else {
            setAt(ctx, id, at, A_SNZ)   // seri pengingat berulang tidak diubah, hanya satu bunyi tambahan
        }
    }

    /** Setelah HP menyala ulang / waktu diubah / aplikasi diperbarui: pasang ulang semua alarm dan bunyikan yang terlewat. */
    fun restore(ctx: Context) {
        val db = readDb(ctx) ?: return
        val now = System.currentTimeMillis()
        for (d in db.docs) {
            if (d.dt != 0L || d.rm <= 0L) continue
            val t = next(d.rm, d.rr, now)
            if (t > 0L) setAt(ctx, d.id, t)
            else if (d.rr.isEmpty() && d.rd < d.rm && now - d.rm < 12 * 3_600_000L) onFire(ctx, d.id, false)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Rem.EXTRA_NOTE) ?: return
        val ctx = context.applicationContext
        when (intent.action) {
            Rem.A_FIRE -> Rem.onFire(ctx, id, false)
            Rem.A_SNZ -> Rem.onFire(ctx, id, true)
            Rem.A_DONE -> Rem.onDone(ctx, id)
            Rem.A_LATER -> Rem.onSnooze(ctx, id, System.currentTimeMillis() + 3_600_000L)
            Rem.A_TMR -> Rem.onSnooze(ctx, id, Rem.tomorrowMorning())
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Rem.restore(context.applicationContext)
    }
}

// ======================================================================================
// Komponen kecil: chip di kartu, lencana di editor
// ======================================================================================

/** Chip pengingat di kartu catatan (warna mengikuti kartu). Dicoret bila sudah terlewat. */
@Composable
fun RemChip(label: String, fg: Color, over: Boolean, repeat: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.graphicsLayer { alpha = if (over) .72f else 1f }
            .clip(RoundedCornerShape(50)).background(fg.copy(alpha = .14f))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic("bell", fg, 13.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            label, color = fg, fontSize = 12.sp, fontWeight = FontWeight(700), fontFamily = LocalFF.current,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (over) TextDecoration.LineThrough else null
        )
        if (repeat) {
            Spacer(Modifier.width(4.dp))
            Ic("repeat", fg, 13.dp)
        }
    }
}

/** Lencana pengingat di atas judul editor (sejajar dengan lencana label). Ketuk untuk mengubah / menghapus. */
@Composable
fun ReminderBadge(label: String, over: Boolean, repeat: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val inK = remember { Animatable(0f) }
    LaunchedEffect(Unit) { inK.animateTo(1f, spring(.6f, 420f)) }
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 10.dp else 18.dp, spring(.6f, 500f), label = "rbr")
    val bg = if (over) pal.ec else pal.tc
    val fg = if (over) pal.oec else pal.otc
    Row(
        Modifier
            .graphicsLayer { val k = inK.value; alpha = k.coerceIn(0f, 1f); scaleX = .7f + .3f * k; scaleY = .7f + .3f * k }
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic("bell", fg, 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            label, color = fg, fontSize = 13.sp, fontWeight = FontWeight(700), fontFamily = LocalFF.current,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (over) TextDecoration.LineThrough else null
        )
        if (repeat) {
            Spacer(Modifier.width(6.dp))
            Ic("repeat", fg, 14.dp)
        }
    }
}

// ======================================================================================
// Lembar pengingat (Material 3 Expressive): pilihan cepat, lalu halaman tanggal / waktu / ulangi
// ======================================================================================

private fun palScheme(p: Pal) = if (p.dark) darkColorScheme(
    primary = p.p, onPrimary = p.op, primaryContainer = p.pc, onPrimaryContainer = p.opc,
    secondaryContainer = p.sc, onSecondaryContainer = p.osc, tertiaryContainer = p.tc, onTertiaryContainer = p.otc,
    surface = p.s, onSurface = p.os, onSurfaceVariant = p.osv, background = p.s, outline = p.osv, outlineVariant = p.ol,
    surfaceContainerLow = p.c1, surfaceContainer = p.c2, surfaceContainerHigh = p.c3, surfaceContainerHighest = p.c4,
    errorContainer = p.ec, onErrorContainer = p.oec
) else lightColorScheme(
    primary = p.p, onPrimary = p.op, primaryContainer = p.pc, onPrimaryContainer = p.opc,
    secondaryContainer = p.sc, onSecondaryContainer = p.osc, tertiaryContainer = p.tc, onTertiaryContainer = p.otc,
    surface = p.s, onSurface = p.os, onSurfaceVariant = p.osv, background = p.s, outline = p.osv, outlineVariant = p.ol,
    surfaceContainerLow = p.c1, surfaceContainer = p.c2, surfaceContainerHigh = p.c3, surfaceContainerHighest = p.c4,
    errorContainer = p.ec, onErrorContainer = p.oec
)

private fun palType(ff: FontFamily): Typography {
    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = ff)
    return Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f()
    )
}

/** Komponen Material 3 resmi (pemilih tanggal & jam) memakai palet dan huruf aplikasi, bukan ungu bawaan. */
@Composable
private fun PickerTheme(content: @Composable () -> Unit) {
    val pal = LocalPal.current
    val ff = LocalFF.current
    MaterialTheme(colorScheme = palScheme(pal), typography = palType(ff), content = content)
}

@Composable
private fun RRow(pos: Int, icon: String, title: String, tail: String? = null, sub: String? = null, chevron: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPal.current
    SCard(pos, onClick, PaddingValues(horizontal = 16.dp, vertical = 12.dp), bg = pal.c4) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(pal.pc), Alignment.Center) { Ic(icon, pal.opc, 22.dp) }
            Column(Modifier.weight(1f)) {
                T(title, 16f, 600, pal.os)
                if (sub != null) T(sub, 13f, 500, pal.osv, lh = 1.3f)
            }
            if (tail != null) T(tail, 15f, 700, pal.p)
            if (chevron) Ic("chr", pal.osv, 24.dp)
        }
    }
}

/** Tombol pil M3 Expressive: sudut menyempit dan sedikit mengecil saat ditekan. */
@Composable
private fun PillBtn(text: String, icon: String?, bg: Color, fg: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 16.dp else 26.dp, spring(.6f, 500f), label = "pbr")
    val sc by animateFloatAsState(if (pr) .96f else 1f, spring(.5f, 600f), label = "pbs")
    Row(
        modifier.height(52.dp).graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else .4f }
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Ic(icon, fg, 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        T(text, 16f, 800, fg, maxLines = 1)
    }
}

/** Chip pilihan ulang: sudut membulat jadi pil dan muncul ceklis saat terpilih. */
@Composable
private fun RChip(text: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val k by animateFloatAsState(if (on) 1f else 0f, spring(.7f, 420f), label = "rck")
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape((14f + 12f * k).dp))
            .background(androidx.compose.ui.graphics.lerp(pal.c4, pal.pc, k))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = on,
            enter = expandHorizontally(spring(.8f, 400f)) + fadeIn(),
            exit = shrinkHorizontally(spring(.8f, 400f)) + fadeOut()
        ) {
            Row {
                Ic("ok", pal.opc, 18.dp)
                Spacer(Modifier.width(6.dp))
            }
        }
        T(text, 15f, 700, androidx.compose.ui.graphics.lerp(pal.os, pal.opc, k), maxLines = 1)
    }
}

/** Ubin besar untuk tanggal / waktu. */
@Composable
private fun PickTile(icon: String, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pr) 14.dp else 28.dp, spring(.6f, 500f), label = "ptr")
    val sc by animateFloatAsState(if (pr) .97f else 1f, spring(.5f, 600f), label = "pts")
    Column(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(r)).background(pal.c4)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, pal.p, 18.dp)
            Spacer(Modifier.width(6.dp))
            T(label, 13f, 700, pal.osv)
        }
        T(value, 20f, 900, pal.os, Modifier.padding(top = 6.dp), ls = -.01f, maxLines = 1)
    }
}

private fun utcMidnight(y: Int, m: Int, d: Int): Long {
    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    c.clear()
    c.set(y, m, d)
    return c.timeInMillis
}

@Composable
private fun DateDlg(initial: Long, onDone: (Int, Int, Int) -> Unit, onDismiss: () -> Unit) {
    val pal = LocalPal.current
    val c = Calendar.getInstance()
    c.timeInMillis = initial
    val today = Calendar.getInstance()
    val todayUtc = utcMidnight(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
    val thisYear = today.get(Calendar.YEAR)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(todayUtc, utcMidnight(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
            override fun isSelectableYear(year: Int): Boolean = year >= thisYear
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        confirmButton = {
            TextButton(onClick = {
                val s = state.selectedDateMillis
                if (s != null) {
                    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    u.timeInMillis = s
                    onDone(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
                } else onDismiss()
            }) { T("Pilih", 16f, 800, pal.p) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { T("Batal", 16f, 700, pal.osv) } }
    ) { DatePicker(state) }
}

@Composable
private fun TimeDlg(h: Int, m: Int, is24: Boolean, onDone: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val pal = LocalPal.current
    val st = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = is24)
    var typing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = pal.c3, tonalElevation = 0.dp) {
            Column(Modifier.padding(24.dp)) {
                T("Pilih waktu", 14f, 600, pal.osv, Modifier.padding(bottom = 20.dp), ls = .01f)
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    if (typing) TimeInput(state = st) else TimePicker(state = st)
                }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(if (typing) "clock" else "kbd", pal.osv, { typing = !typing }, size = 48.dp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { T("Batal", 16f, 700, pal.osv) }
                    TextButton(onClick = { onDone(st.hour, st.minute) }) { T("OK", 16f, 800, pal.p) }
                }
            }
        }
    }
}

@Composable
fun ReminderHost(vm: AppVM, act: Actions) {
    val id = vm.remDlg ?: return
    PickerTheme { ReminderSheet(vm, act, id) }
}

@Composable
private fun ReminderSheet(vm: AppVM, act: Actions, id: String) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val note = vm.note(id)
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    val notifOk = remember(vm.permTick) { Rem.notifOk(ctx) }

    // 0 = pilihan cepat, 1 = atur tanggal / waktu / ulangi
    var page by remember { mutableStateOf(0) }
    val now0 = remember { System.currentTimeMillis() }
    var at by remember { mutableLongStateOf(initialAt(note, now0)) }
    var rrSel by remember { mutableStateOf(note?.rr ?: "") }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val hide: () -> Unit = { scope.launch { st.hide(); vm.remDlg = null } }
    val commit: (Long, String) -> Unit = { t, r ->
        vm.setReminder(id, t, r)
        if (!Rem.notifOk(ctx)) act.askNotif()
        hide()
    }

    ModalBottomSheet(
        onDismissRequest = { vm.remDlg = null }, sheetState = st,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp), containerColor = pal.c3,
        scrimColor = Color.Black.copy(alpha = .5f),
        dragHandle = {
            Box(Modifier.padding(top = 14.dp, bottom = 18.dp).width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(pal.ol))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp).navigationBarsPadding()
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val fwd = targetState > initialState
                    (slideInHorizontally(spring(.9f, 380f)) { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(220, 60)))
                        .togetherWith(slideOutHorizontally(spring(.9f, 380f)) { if (fwd) -it / 3 else it / 3 } + fadeOut(tween(140)))
                        .using(SizeTransform(clip = true) { _, _ -> spring(.9f, 380f) })
                },
                label = "rem-page"
            ) { pg ->
                if (pg == 0) {
                    MainPage(vm, act, id, note, notifOk, commit, onCustom = {
                        at = initialAt(note, System.currentTimeMillis()); rrSel = note?.rr ?: ""; page = 1
                    }, onClear = { vm.clearReminder(id); hide() })
                } else {
                    CustomPage(
                        at = at, rr = rrSel, is24 = is24,
                        onBack = { page = 0 },
                        onPickDate = { showDate = true }, onPickTime = { showTime = true },
                        onRepeat = { rrSel = it },
                        onSave = { commit(at, rrSel) }
                    )
                }
            }
        }
    }

    if (showDate) DateDlg(
        initial = at,
        onDone = { y, m, d ->
            val c = Calendar.getInstance()
            c.timeInMillis = at
            c.set(y, m, d)
            at = c.timeInMillis
            showDate = false
        },
        onDismiss = { showDate = false }
    )
    if (showTime) {
        val c = Calendar.getInstance()
        c.timeInMillis = at
        TimeDlg(
            h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE), is24 = is24,
            onDone = { h, m ->
                val k = Calendar.getInstance()
                k.timeInMillis = at
                k.set(Calendar.HOUR_OF_DAY, h); k.set(Calendar.MINUTE, m); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0)
                at = k.timeInMillis
                showTime = false
            },
            onDismiss = { showTime = false }
        )
    }
}

private fun initialAt(n: Note?, now: Long): Long {
    if (n != null && n.rm > 0L) {
        val t = Rem.next(n.rm, n.rr, now)
        if (t > 0L) return t
    }
    return Rem.defaultAt(now)
}

@Composable
private fun MainPage(
    vm: AppVM, act: Actions, id: String, note: Note?, notifOk: Boolean,
    commit: (Long, String) -> Unit, onCustom: () -> Unit, onClear: () -> Unit
) {
    val pal = LocalPal.current
    val hasRem = note != null && note.rm > 0L
    val over = note != null && Rem.overdue(note)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(pal.pc), Alignment.Center) { Ic("bell", pal.opc, 26.dp) }
            Column(Modifier.weight(1f)) {
                T("Pengingat", 26f, 1000, pal.os, ls = -.01f)
                T(note?.t?.ifEmpty { null } ?: "Tanpa judul", 14f, 500, pal.osv, maxLines = 1)
            }
        }
        if (!notifOk) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(24.dp)).background(pal.ec)
                    .padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic("belloff", pal.oec, 24.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T("Notifikasi dimatikan", 15f, 800, pal.oec)
                    T("Pengingat baru muncul setelah notifikasi diizinkan.", 13f, 500, pal.oec, lh = 1.3f, alpha = .85f)
                }
                TextButton(onClick = { act.askNotif() }) { T("Aktifkan", 15f, 800, pal.oec) }
            }
        }
        if (hasRem && note != null) {
            val bg = if (over) pal.ec else pal.pc
            val fg = if (over) pal.oec else pal.opc
            Column(Modifier.fillMaxWidth().padding(bottom = 22.dp).clip(RoundedCornerShape(32.dp)).background(bg).padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic("bell", fg, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    T(if (over) "Terlewat" else "Diatur untuk", 13f, 700, fg, ls = .02f, alpha = .85f)
                }
                T(Rem.stamp(note), 28f, 1000, fg, Modifier.padding(top = 6.dp), ls = -.01f)
                if (note.rr.isNotEmpty()) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ic("repeat", fg, 16.dp)
                        Spacer(Modifier.width(6.dp))
                        T(Rem.repeatName(note.rr), 14f, 600, fg, alpha = .85f)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillBtn("Hapus", "del", pal.s.copy(alpha = .55f), fg, Modifier.weight(1f), onClick = onClear)
                    PillBtn("Ubah", "pen", pal.p, pal.op, Modifier.weight(1f), onClick = onCustom)
                }
            }
        }
        T(if (hasRem) "Ganti ke" else "Ingatkan saya", 14f, 600, pal.p, Modifier.padding(start = 12.dp, bottom = 10.dp), ls = .01f)
        val quick = remember { Rem.quick() }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            quick.forEachIndexed { i, q ->
                RRow(if (i == 0) POS_TOP else POS_MID, q.icon, q.title, tail = q.tail) { commit(q.at, "") }
            }
            RRow(POS_BOT, "cal", "Pilih tanggal dan waktu", sub = "Atur juga pengulangannya", chevron = true, onClick = onCustom)
        }
    }
}

@Composable
private fun CustomPage(
    at: Long, rr: String, is24: Boolean,
    onBack: () -> Unit, onPickDate: () -> Unit, onPickTime: () -> Unit, onRepeat: (String) -> Unit, onSave: () -> Unit
) {
    val pal = LocalPal.current
    val ok = at > System.currentTimeMillis()
    val ID = Locale("id", "ID")
    val dateS = SimpleDateFormat("EEE, d MMM yyyy", ID).format(Date(at))
    val timeS = SimpleDateFormat(if (is24) "HH.mm" else "hh.mm a", ID).format(Date(at))
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn("back", pal.os, onBack)
            T("Atur pengingat", 26f, 1000, pal.os, Modifier.padding(start = 6.dp), ls = -.01f)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PickTile("cal", "Tanggal", dateS, Modifier.weight(1.45f), onPickDate)
            PickTile("clock", "Waktu", timeS, Modifier.weight(1f), onPickTime)
        }
        T("Ulangi", 14f, 600, pal.p, Modifier.padding(start = 12.dp, bottom = 10.dp), ls = .01f)
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Rem.REPEATS.forEach { (code, name) -> RChip(name, rr == code) { onRepeat(code) } }
        }
        AnimatedVisibility(visible = !ok, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            T("Waktu ini sudah lewat. Pilih waktu yang akan datang.", 14f, 600, pal.oec, Modifier.padding(start = 12.dp, bottom = 14.dp))
        }
        PillBtn("Simpan pengingat", "bell", pal.p, pal.op, Modifier.fillMaxWidth(), enabled = ok, onClick = onSave)
    }
}

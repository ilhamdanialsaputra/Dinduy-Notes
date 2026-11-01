package app.dinduy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

/** Nama model yang ramah untuk pengguna awam. */
fun modelLabel(key: String): String = if (key == "small") "Akurat" else "Standar"

/**
 * Kerja latar belakang (unduh model, rekam suara, transkripsi). Satu layanan foreground + satu notifikasi.
 * Berhenti sendiri saat semua selesai, atau saat jendela aplikasi ditutup dari daftar aplikasi terbaru.
 */
object Bg {
    private const val CH = "dinduy_bg"
    private const val CH_DONE = "dinduy_done"
    const val ID = 7001
    const val A_STOP = "app.dinduy.STOP_REC"
    private const val MIC = "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z"

    /** prog: -2 tanpa bar, -1 bar berjalan terus, 0..100 persen. */
    class Snap(val title: String, val text: String, val prog: Int, val mic: Boolean, val stop: Boolean)

    @Volatile var fg = false          // jendela aplikasi sedang terlihat
    @Volatile var running = false     // layanan foreground sedang jalan
    @Volatile var snap: Snap? = null
    var onStopRec: (() -> Unit)? = null
    private var lastMic = false
    private var doneId = 7100
    private var icon: IconCompat? = null

    private fun icon(): IconCompat = icon ?: run {
        val sz = 96
        val bm = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val path = androidx.core.graphics.PathParser.createPathFromPathData(MIC)
        val m = Matrix()
        m.setScale(sz / 24f, sz / 24f)
        path.transform(m)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.WHITE
        Canvas(bm).drawPath(path, p)
        IconCompat.createWithBitmap(bm)
    }.also { icon = it }

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null) {
            val c = NotificationChannel(CH, "Proses latar belakang", NotificationManager.IMPORTANCE_LOW)
            c.description = "Unduhan model, perekaman, dan transkripsi yang sedang berjalan"
            c.setShowBadge(false)
            nm.createNotificationChannel(c)
        }
        if (nm.getNotificationChannel(CH_DONE) == null) {
            val c = NotificationChannel(CH_DONE, "Proses selesai", NotificationManager.IMPORTANCE_DEFAULT)
            c.description = "Pemberitahuan saat unduhan atau transkripsi selesai"
            nm.createNotificationChannel(c)
        }
    }

    private fun openPi(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun build(ctx: Context, s: Snap): Notification {
        channels(ctx)
        val b = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(icon())
            .setContentTitle(s.title)
            .setContentText(s.text)
            .setContentIntent(openPi(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setColor(0xFF6750A4.toInt())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.prog >= 0) b.setProgress(100, s.prog, false) else if (s.prog == -1) b.setProgress(0, 0, true)
        if (s.stop) {
            val pi = PendingIntent.getService(
                ctx, 1, Intent(ctx, BgService::class.java).setAction(A_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(0, "Selesai", pi)
        }
        return b.build()
    }

    /** Dipanggil berkala selama ada kerja: menyalakan layanan atau memperbarui notifikasinya. */
    fun push(ctx: Context, s: Snap) {
        snap = s
        if (!running || s.mic != lastMic) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, BgService::class.java))
                running = true
                lastMic = s.mic
            } catch (e: Exception) {
                running = false   // ditolak sistem: kerja tetap jalan selama aplikasi terbuka
            }
        } else {
            val n = NotificationManagerCompat.from(ctx)
            if (n.areNotificationsEnabled()) {
                try { n.notify(ID, build(ctx, s)) } catch (e: SecurityException) { }
            }
        }
    }

    fun stop(ctx: Context) {
        snap = null
        if (running) {
            running = false
            try { ctx.stopService(Intent(ctx, BgService::class.java)) } catch (e: Exception) { }
        }
    }

    /** Notifikasi hasil (selesai / gagal). Hanya muncul kalau aplikasi sedang tidak terlihat. */
    fun done(ctx: Context, title: String, text: String) {
        if (fg) return
        val n = NotificationManagerCompat.from(ctx)
        if (!n.areNotificationsEnabled()) return
        channels(ctx)
        val b = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(icon())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPi(ctx))
            .setAutoCancel(true)
            .setColor(0xFF6750A4.toInt())
        try { n.notify(doneId++, b.build()) } catch (e: SecurityException) { }
    }
}

class BgService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Bg.A_STOP) Bg.onStopRec?.invoke()
        val s = Bg.snap
        val snap = s ?: Bg.Snap("Dinduy", "Menyelesaikan…", -1, false, false)
        val type = if (Build.VERSION.SDK_INT >= 30 && snap.mic) 129 else 1   // 1 = dataSync, 128 = microphone
        try {
            ServiceCompat.startForeground(this, Bg.ID, Bg.build(this, snap), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (s == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        Bg.running = false
        super.onDestroy()
    }
}

package app.dinduy

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import java.io.File

/** Pembantu bersama untuk widget homescreen (data dibaca langsung dari berkas catatan, tanpa AppVM). */
object WidgetUtil {
    const val ACTION_NEW = "app.dinduy.widget.NEW"      // catatan baru
    const val ACTION_VOICE = "app.dinduy.widget.VOICE"  // voice note baru

    class Row(val id: String, val title: String, val snippet: String, val pinned: Boolean)

    /** PendingIntent yang membuka MainActivity dengan aksi tertentu (atau hanya membuka aplikasi bila action null). */
    fun launch(ctx: Context, req: Int, action: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (action != null) i.action = action
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Catatan untuk widget: yang disematkan dulu, lalu terbaru. Arsip, sampah, dan catatan kosong tidak ikut. */
    fun loadRows(ctx: Context, limit: Int = 40): List<Row> {
        return try {
            val f = File(ctx.filesDir, "dinduy.json")
            if (!f.exists()) return emptyList()
            val db = J.decodeFromString(Db.serializer(), f.readText())
            val byCreated = db.pr.sort == "c"
            fun key(n: Note) = if (n.o != 0L) n.o else if (byCreated) (if (n.c != 0L) n.c else n.u) else n.u
            db.docs.asSequence()
                .filter { !it.a && it.dt == 0L && (it.t.isNotBlank() || it.x.isNotBlank() || it.pdf.isNotEmpty()) }
                .sortedWith(compareByDescending<Note> { it.p }.thenByDescending { key(it) })
                .take(limit)
                .map { n ->
                    val body = n.x.replace('\n', ' ').replace("\u200B", "").trim()
                    val title = n.t.trim().ifEmpty { if (n.pdf.isNotEmpty()) "PDF document" else "" }
                    Row(n.id, title, if (n.pdf.isNotEmpty() && n.t.isBlank()) "" else body.take(160), n.p)
                }
                .toList()
        } catch (e: Exception) { emptyList() }
    }

    /** Dipanggil tiap catatan tersimpan: segarkan daftar di widget "Notes". */
    fun refresh(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NotesWidget::class.java))
            if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        } catch (e: Exception) { }
    }
}

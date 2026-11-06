package app.dinduy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Widget 2 "Notes": daftar catatan (disematkan / terbaru) yang bisa digulir; tombol "+" di header. */
class NotesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, build(context, id))
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    private fun build(ctx: Context, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_notes)
        val svc = Intent(ctx, NotesWidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))   // unik per widget
        rv.setRemoteAdapter(R.id.widget_list, svc)
        rv.setEmptyView(R.id.widget_list, R.id.widget_empty)
        rv.setOnClickPendingIntent(R.id.widget_add, WidgetUtil.launch(ctx, 21, WidgetUtil.ACTION_NEW))
        rv.setOnClickPendingIntent(R.id.widget_title, WidgetUtil.launch(ctx, 22, null))
        // template klik item: tiap baris mengisi id catatannya (extra yang sama dengan notifikasi pengingat)
        val tpl = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        rv.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getActivity(ctx, 23, tpl, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        )
        return rv
    }
}

class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
        private var rows: List<WidgetUtil.Row> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() { rows = WidgetUtil.loadRows(ctx) }
        override fun onDestroy() { rows = emptyList() }
        override fun getCount() = rows.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = rows.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_note_item)
            val r = rows.getOrNull(position) ?: return rv
            rv.setTextViewText(R.id.item_title, r.title)
            rv.setViewVisibility(R.id.item_title, if (r.title.isEmpty()) View.GONE else View.VISIBLE)
            rv.setTextViewText(R.id.item_snippet, r.snippet)
            rv.setViewVisibility(R.id.item_snippet, if (r.snippet.isEmpty()) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.item_pin, if (r.pinned) View.VISIBLE else View.GONE)
            rv.setOnClickFillInIntent(R.id.item_root, Intent().putExtra(Rem.EXTRA_NOTE, r.id))
            return rv
        }
    }
}

package app.dinduy

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Widget 1 "Quick note": tombol besar "+" (catatan baru) dan tombol mic (voice note baru). */
class QuickNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val rv = RemoteViews(context.packageName, R.layout.widget_quick)
        rv.setOnClickPendingIntent(R.id.widget_new, WidgetUtil.launch(context, 11, WidgetUtil.ACTION_NEW))
        rv.setOnClickPendingIntent(R.id.widget_voice, WidgetUtil.launch(context, 12, WidgetUtil.ACTION_VOICE))
        mgr.updateAppWidget(ids, rv)
    }
}

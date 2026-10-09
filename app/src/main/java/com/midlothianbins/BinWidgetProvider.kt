package com.midlothianbins

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BinWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    companion object {
        /** The five bins shown on the widget, in display order (garden waste is seasonal, so it's left out). */
        private val tiles = listOf(
            BinType.Blue to "Plastic",
            BinType.Red to "Glass",
            BinType.Grey to "General",
            BinType.Green to "Paper",
            BinType.Food to "Food"
        )
        private val bgIds = intArrayOf(R.id.tile_bg_1, R.id.tile_bg_2, R.id.tile_bg_3, R.id.tile_bg_4, R.id.tile_bg_5)
        private val numIds = intArrayOf(R.id.tile_num_1, R.id.tile_num_2, R.id.tile_num_3, R.id.tile_num_4, R.id.tile_num_5)
        private val lblIds = intArrayOf(R.id.tile_lbl_1, R.id.tile_lbl_2, R.id.tile_lbl_3, R.id.tile_lbl_4, R.id.tile_lbl_5)

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, BinWidgetProvider::class.java))
            ids.forEach { mgr.updateAppWidget(it, buildViews(context)) }
        }

        fun relativeLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String =
            when (val d = ChronoUnit.DAYS.between(today, date)) {
                0L -> "Today"
                1L -> "Tomorrow"
                else -> "In $d days"
            }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bins)
            val today = LocalDate.now()
            val upcoming = ScheduleRepo.upcoming(context, today)

            tiles.forEachIndexed { i, (bin, label) ->
                val next = upcoming.firstOrNull { bin in it.bins }
                val days = next?.let { ChronoUnit.DAYS.between(today, it.date) }
                views.setInt(bgIds[i], "setColorFilter", bin.color)
                views.setTextViewText(numIds[i], days?.toString() ?: "-")
                views.setTextViewText(lblIds[i], label)
            }

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}

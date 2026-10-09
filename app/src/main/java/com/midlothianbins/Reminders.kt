package com.midlothianbins

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("binday", Context.MODE_PRIVATE)
    fun remindersEnabled(ctx: Context) = p(ctx).getBoolean("enabled", false)
    fun setRemindersEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("enabled", v).apply()
    fun setupDone(ctx: Context) = p(ctx).getBoolean("setup_done", false)
    fun setSetupDone(ctx: Context) = p(ctx).edit().putBoolean("setup_done", true).apply()
    fun reminderHour(ctx: Context) = p(ctx).getInt("hour", 18)
    fun setReminderHour(ctx: Context, v: Int) = p(ctx).edit().putInt("hour", v).apply()
}

object ReminderScheduler {
    private const val CHANNEL_ID = "bin_reminders"
    private const val REQUEST_CODE = 1001

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQUEST_CODE, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Schedules one alarm for the next "evening before a collection" and cancels any previous one. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
        if (!Prefs.remindersEnabled(ctx)) return

        val hour = Prefs.reminderHour(ctx)
        val now = LocalDateTime.now()
        val next = ScheduleRepo.get(ctx).pickups
            .map { it.date.minusDays(1).atTime(hour, 0) }
            .firstOrNull { it.isAfter(now) } ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact (no special permission needed); may fire a few minutes late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(ctx))
    }

    fun hasNotificationPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyTomorrow(ctx: Context) {
        val pickup = ScheduleRepo.on(ctx, LocalDate.now().plusDays(1)) ?: return
        if (!hasNotificationPermission(ctx)) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Bin reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bins out tonight")
            .setContentText("Tomorrow: ${pickup.longNames}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Tomorrow's collection: ${pickup.longNames}"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(2001, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.notifyTomorrow(context)
        ReminderScheduler.schedule(context)
        BinWidgetProvider.updateAll(context)
    }
}

/** Re-arms the alarm after reboot, app update or clock/timezone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.schedule(context)
        BinWidgetProvider.updateAll(context)
    }
}

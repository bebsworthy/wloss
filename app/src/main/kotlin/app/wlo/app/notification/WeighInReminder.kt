package app.wlo.app.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The soft daily weigh-in reminder (F06 §4 entry points, WLO-0040): one
 * notification a day at the user's chosen minute, zero guilt in the copy,
 * tap → wlo://weight/log. WorkManager carries the schedule across reboots
 * (periodic work is persisted); inexact firing is by design — a weigh-in
 * reminder that fights for exactness fights the whole tone.
 */
public class WeighInReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    // The immediately preceding notificationsPermitted guard checks the API
    // 33 runtime permission; lint cannot follow that helper across the call.
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val context = applicationContext
        ensureChannel(context)
        if (!notificationsPermitted(context)) return Result.success()
        val tap =
            PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_VIEW, Uri.parse("wlo://weight/log"))
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle(TITLE)
                .setContentText(BODY)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        return Result.success()
    }

    public companion object {
        public const val CHANNEL_ID: String = "weighin_reminder"
        public const val NOTIFICATION_ID: Int = 4001
        public const val WORK_NAME: String = "weigh-in-reminder"

        /** Reminder copy per F06 §2/§6 tone rules: an invitation, never a verdict. */
        public const val TITLE: String = "Time for a weigh-in"
        public const val BODY: String = "Log one if this is a useful moment for you."

        public fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Weigh-in reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        public fun notificationsPermitted(context: Context): Boolean {
            return availability(context) == ReminderAvailability.AVAILABLE
        }

        public fun availability(context: Context): ReminderAvailability {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return ReminderAvailability.PERMISSION_REQUIRED
            }
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return ReminderAvailability.APP_BLOCKED
            if (Build.VERSION.SDK_INT >= 26) {
                val systemManager = context.getSystemService(NotificationManager::class.java)
                val channel = systemManager?.getNotificationChannel(CHANNEL_ID)
                if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    return ReminderAvailability.CHANNEL_BLOCKED
                }
            }
            return ReminderAvailability.AVAILABLE
        }
    }
}

/** The schedule door: one 24 h periodic work, anchored to the chosen minute. */
public object WeighInReminder {
    public fun reschedule(
        context: Context,
        enabled: Boolean,
        minuteOfDay: Int,
    ) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WeighInReminderWorker.WORK_NAME)
            return
        }
        WeighInReminderWorker.ensureChannel(context)
        val request =
            PeriodicWorkRequestBuilder<WeighInReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntilNext(minuteOfDay, ZonedDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
                .build()
        manager.enqueueUniquePeriodicWork(
            WeighInReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Millis until the next occurrence of [minuteOfDay] — today if it is still
     * ahead by a few minutes, otherwise tomorrow (a just-chosen time must not
     * fire "immediately" from a slot that passed sixty seconds ago).
     */
    public fun delayUntilNext(
        minuteOfDay: Int,
        now: LocalDateTime,
    ): Duration = delayUntilNext(minuteOfDay, now.atZone(java.time.ZoneId.systemDefault()))

    /** Zone-aware form used by production and DST/timezone regression tests. */
    public fun delayUntilNext(
        minuteOfDay: Int,
        now: ZonedDateTime,
    ): Duration {
        val todayAt = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        val grace = now.plusMinutes(GRACE_MINUTES)
        var next = todayAt.atZone(now.zone)
        if (!next.isAfter(grace)) next = todayAt.plusDays(1).atZone(now.zone)
        return Duration.between(now, next)
    }

    private const val GRACE_MINUTES: Long = 5
}

public enum class ReminderAvailability {
    AVAILABLE,
    PERMISSION_REQUIRED,
    APP_BLOCKED,
    CHANNEL_BLOCKED,
}

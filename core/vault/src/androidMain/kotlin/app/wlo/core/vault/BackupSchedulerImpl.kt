package app.wlo.core.vault

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.wlo.core.ports.BackupRequest
import app.wlo.core.ports.BackupScheduler
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * The BackupScheduler port's WorkManager impl (ARCHITECTURE §2.4: WHEN to run
 * is Android glue). Default posture per F13 §3: periodic daily once a backup
 * folder is chosen; a failed run raises ONE respectful notification (R-U1
 * spirit: no toast spam).
 */
public class WorkManagerBackupScheduler(
    private val context: Context,
) : BackupScheduler {
    override fun schedule(request: BackupRequest) {
        val workManager = WorkManager.getInstance(context)
        val work =
            PeriodicWorkRequestBuilder<BackupWorker>(BACKUP_PERIOD_DAYS, TimeUnit.DAYS)
                .setInputData(
                    workDataOf(
                        KEY_DESTINATION to request.destinationUri,
                        KEY_INCLUDE_VAULT to request.includeVault,
                    ),
                ).build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            work,
        )
    }

    public companion object {
        public const val WORK_NAME: String = "wlo-auto-backup"
        public const val KEY_DESTINATION: String = "destination"
        public const val KEY_INCLUDE_VAULT: String = "includeVault"
        public const val BACKUP_PERIOD_DAYS: Long = 1
    }
}

/**
 * The scheduled run: passphrase-less (the [AutoBackupKeyVault] key), fail →
 * notify. Resolves [BackupManager] through the app's Koin graph (the worker
 * runs in the app process; the composition root is the registry).
 */
public class BackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val destination =
            inputData.getString(WorkManagerBackupScheduler.KEY_DESTINATION)
                ?: return Result.failure()
        val includeVault = inputData.getBoolean(WorkManagerBackupScheduler.KEY_INCLUDE_VAULT, false)
        val manager = GlobalContext.get().get<BackupManager>()
        return try {
            manager.backupNow(
                destination = destination,
                passphrase = null,
                options = BackupOptions(includeVault = includeVault),
            )
            Result.success()
        } catch (failure: Exception) {
            if (runAttemptCount >= MAX_ATTEMPTS) {
                notifyFailure()
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    /** F13 §4: "notification after a failed backup" — one, quiet, actionable. */
    private fun notifyFailure() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Backup", NotificationManager.IMPORTANCE_MIN),
            )
        }
        // POST_NOTIFICATIONS (API 33+) is PART B's permission ask; until it is
        // granted the system drops this notification and the F10 backup-health
        // dot stays the in-app signal.
        val notification: Notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Backup didn't complete")
                .setContentText("Your data is safe on this phone — check the backup folder in Settings.")
                .setAutoCancel(true)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "wlo.backup"
        const val NOTIFICATION_ID = 41
        const val MAX_ATTEMPTS = 3
    }
}

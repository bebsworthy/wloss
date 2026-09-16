package app.wlo.core.vault

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.ports.BackupRequest
import app.wlo.core.ports.BackupScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * The BackupScheduler port's WorkManager impl (ARCHITECTURE §2.4: WHEN to run
 * is Android glue). Default posture per F13 §3: periodic daily once a backup
 * folder is chosen; a failed run raises ONE respectful notification (R-U1
 * spirit: no toast spam).
 */
@Suppress("InjectDispatcher") // Blocking WorkManager futures are confined at this Android-only boundary.
public class WorkManagerBackupScheduler(
    private val context: Context,
    private val settings: SettingsStore,
) : BackupScheduler {
    override suspend fun setEnabled(
        enabled: Boolean,
        request: BackupRequest?,
    ) {
        val workManager by lazy { WorkManager.getInstance(context) }
        BackupScheduleReconciler(
            persistEnabled = settings::setBackupAutoEnabled,
            enqueue = { activeRequest ->
                val work =
                    PeriodicWorkRequestBuilder<BackupWorker>(BACKUP_PERIOD_DAYS, TimeUnit.DAYS)
                        .setInputData(
                            workDataOf(
                                KEY_DESTINATION to activeRequest.destinationUri,
                                KEY_INCLUDE_VAULT to activeRequest.includeVault,
                            ),
                        ).build()
                withContext(Dispatchers.IO) {
                    workManager
                        .enqueueUniquePeriodicWork(
                            WORK_NAME,
                            ExistingPeriodicWorkPolicy.UPDATE,
                            work,
                        ).result
                        .get()
                }
            },
            cancel = {
                withContext(Dispatchers.IO) {
                    workManager.cancelUniqueWork(WORK_NAME).result.get()
                }
            },
        ).setEnabled(enabled, request)
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
        val graph = GlobalContext.get()
        val settings = graph.get<SettingsStore>()
        return when (
            ScheduledBackupRunner(
                isEnabled = { settings.backupAutoEnabled.first() },
                backup = {
                    graph.get<BackupManager>().backupNow(
                        destination = destination,
                        passphrase = null,
                        options = BackupOptions(includeVault = includeVault),
                    )
                },
            ).run(runAttemptCount = runAttemptCount, maxAttempts = MAX_ATTEMPTS)
        ) {
            ScheduledBackupResult.SUCCESS -> Result.success()
            ScheduledBackupResult.RETRY -> Result.retry()
            ScheduledBackupResult.FAILURE -> {
                notifyFailure()
                Result.failure()
            }
        }
    }

    /** F13 §4: "notification after a failed backup" — one, quiet, actionable. */
    private fun notifyFailure() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Backup", NotificationManager.IMPORTANCE_MIN),
            )
        }
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

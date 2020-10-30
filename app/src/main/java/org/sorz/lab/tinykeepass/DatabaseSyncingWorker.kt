package org.sorz.lab.tinykeepass

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.*
import org.sorz.lab.tinykeepass.keepass.KeePassStorage
import java.io.IOException

const val PARAM_URL = "param-url"
const val PARAM_USERNAME = "param-username"
const val PARAM_PASSWORD = "param-password"
const val PARAM_MASTER_KEY = "param-master-key"
const val RESULT_ERROR = "result-error"
const val RESULT_TIMESTAMP = "result-timestamp"
const val DATABASE_SYNCING_WORK_NAME = "work-database-syncing"

private const val CHANNEL_ID_SYNCING = "channel-syncing"
private const val NOTIFICATION_OK_TIMEOUT_MILLS = 4 * 1000L


private var NOTIFICATION_ID = 1

class DatabaseSyncingWorker(val context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {
    private val notificationManager: NotificationManager = context.getSystemService()!!
    private val notificationId = ++NOTIFICATION_ID

    override suspend fun doWork(): Result {
        // Get parameters
        val url = inputData.getString(PARAM_URL) ?: return Result.failure()
        val masterKey = inputData.getString(PARAM_MASTER_KEY) ?: return Result.failure()
        val authUsername = inputData.getString(PARAM_USERNAME)
        val authPassword = inputData.getString(PARAM_PASSWORD)
        val basicAuth =
            if (authUsername == null || authPassword == null) BasicAuthCfg()
            else BasicAuthCfg(true, authUsername, authPassword)
        val uri = Uri.parse(url)

        // Set notification
        setForeground(createForegroundInfo(url))

        // Fetch then notify
        try {
            fetchDatabase(context, uri, masterKey, basicAuth)
        } catch (err: Exception) {
            notifyResult(err)
            return Result.failure(workDataOf(
                    RESULT_TIMESTAMP to System.currentTimeMillis(),
                    RESULT_ERROR to (err.localizedMessage ?: err.toString()),
            ))
        }
        notifyResult()
        return Result.success(workDataOf(RESULT_TIMESTAMP to System.currentTimeMillis()))
    }

    private fun createForegroundInfo(url: String): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID_SYNCING,
                    context.getString(R.string.channel_syncing),
                    NotificationManager.IMPORTANCE_LOW
            ))
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYNCING)
            .setSmallIcon(R.drawable.ic_cloud_white_black_24dp)
            .setContentTitle(context.getString(R.string.fetching_database))
            .setContentText(url)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(android.R.string.cancel),
                    WorkManager.getInstance(context).createCancelPendingIntent(id)
            ).build()
        return ForegroundInfo(notificationId, notification)
    }

    private fun notifyResult(err: Exception? = null) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SYNCING)
        if (err != null) {
            builder
                .setSmallIcon(R.drawable.ic_report_problem_white_24dp)
                .setContentTitle(context.getString(R.string.fetch_fail))
                .setContentText(err.localizedMessage ?: "I/O error")
        } else {
            builder
                .setContentTitle(context.getString(R.string.fetch_ok))
                .setSmallIcon(R.drawable.ic_cloud_done_white_24dp)
                .setTimeoutAfter(NOTIFICATION_OK_TIMEOUT_MILLS)
            KeePassStorage.get(context)?.name?.let { dbName ->
                builder.setContentText(dbName)
            }
        }
        // FIXME: notification not showing
        notificationManager.notify(notificationId, builder.build())
    }

}
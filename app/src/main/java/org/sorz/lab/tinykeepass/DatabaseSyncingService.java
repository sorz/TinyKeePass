package org.sorz.lab.tinykeepass;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;

import de.slackspace.openkeepass.domain.KeePassFile;

public class DatabaseSyncingService extends Service {
    private static final String TAG = DatabaseSyncingService.class.getName();
    private static final int NOTIFICATION_OK_TIMEOUT_MILLS = 2 * 1000;

    private static final String ACTION_CANCEL = "action-cancel";
    public static final String ACTION_FETCH = "action-fetch";
    public static final String EXTRA_URL = "extra-url";
    public static final String EXTRA_MASTER_KEY = "extra-master-key";
    public static final String EXTRA_USERNAME = "extra-username";
    public static final String EXTRA_PASSWORD = "extra-password";
    public static final String BROADCAST_SYNC_FINISHED = "broadcast-sync-finished";
    public static final String EXTRA_SYNC_ERROR = "extra-sync-error";

    private FetchTask fetchTask;


    public DatabaseSyncingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w(TAG, "null intent");
        } else if (ACTION_FETCH.equals(intent.getAction())) {
            if (fetchTask != null
                    && fetchTask.getStatus() == AsyncTask.Status.RUNNING) {
                return START_NOT_STICKY;
            }
            try {
                URL url = new URL(intent.getStringExtra(EXTRA_URL));
                String masterKey = intent.getStringExtra(EXTRA_MASTER_KEY);
                String username = intent.getStringExtra(EXTRA_USERNAME);
                String password = intent.getStringExtra(EXTRA_PASSWORD);
                fetchTask = new FetchTask(this, url, masterKey, username, password);
            } catch (MalformedURLException e) {
                Log.e(TAG, "illegal url", e);
            }
            fetchTask.execute();
        } else if (ACTION_CANCEL.equals(intent.getAction())) {
            if (fetchTask != null && fetchTask.getStatus() == AsyncTask.Status.RUNNING) {
                boolean cancel = fetchTask.cancel(true);
                Log.d(TAG, "cancel task: " + cancel);
            }
        } else {
            Log.w(TAG, "unknown action");
        }
        return START_NOT_STICKY;
    }

    private static class FetchTask extends FetchDatabaseTask {
        private static int nextNotificationId = 1;

        private final WeakReference<Context> context;
        private final URL url;
        private final NotificationManager notificationManager;
        private final int notificationId;

        FetchTask(Context context, URL url, String masterPwd, String username, String password) {
            super(context, url, masterPwd, username, password);
            this.context = new WeakReference<>(context);
            this.url = url;
            notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            notificationId = nextNotificationId++;
        }

        @Override
        protected void onPreExecute() {
            Context context = this.context.get();
            if (context == null)
                return;

            Intent intent = new Intent(context, DatabaseSyncingService.class);
            intent.setAction(ACTION_CANCEL);
            PendingIntent pendingIntent =
                    PendingIntent.getService(context, 0, intent, 0);
            Notification.Action cancel = new Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    context.getString(android.R.string.cancel),
                    pendingIntent).build();

            Notification.Builder builder = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.ic_cloud_white_black_24dp)
                    .setContentTitle(context.getString(R.string.fetching_database))
                    .setContentText(url.toString())
                    .setOngoing(true)
                    .setProgress(0, 0, true)
                    .setActions(cancel);

            notificationManager.notify(notificationId, builder.build());
        }

        @Override
        protected void onPostExecute(String error) {
            Context context = this.context.get();
            if (context == null) {
                notificationManager.cancel(notificationId);
                Log.w(TAG, "task done after service exited");
                return;
            }

            Notification.Builder builder = new Notification.Builder(context);
            if (error == null) {
                builder.setContentTitle(context.getString(R.string.fetch_ok));
                KeePassFile db = KeePassStorage.get();
                if (db != null && db.getMeta().getDatabaseName() != null)
                    builder.setSmallIcon(R.drawable.ic_cloud_done_white_24dp)
                            .setContentText(db.getMeta().getDatabaseName());
                new Handler().postDelayed(() -> notificationManager.cancel(notificationId),
                        NOTIFICATION_OK_TIMEOUT_MILLS);
                notifyFinish(null);
            } else {
                builder.setSmallIcon(R.drawable.ic_report_problem_white_24dp)
                        .setContentTitle(context.getString(R.string.fetch_fail))
                        .setContentText(error);
                notifyFinish(error);
            }
            notificationManager.notify(notificationId, builder.build());
        }

        @Override
        protected void onCancelled(String error) {
            notificationManager.cancel(notificationId);
            if (context.isEnqueued())
                notifyFinish(context.get().getString(R.string.fetch_cancel_by_user));
        }

        private void notifyFinish(String error) {
            Intent intent = new Intent(BROADCAST_SYNC_FINISHED);
            if (error != null)
                intent.putExtra(EXTRA_SYNC_ERROR, error);
            if (context.isEnqueued())
                LocalBroadcastManager.getInstance(context.get()).sendBroadcast(intent);
        }
    }
}

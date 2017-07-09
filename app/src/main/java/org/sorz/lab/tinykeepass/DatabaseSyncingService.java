package org.sorz.lab.tinykeepass;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;

import de.slackspace.openkeepass.domain.KeePassFile;

public class DatabaseSyncingService extends Service {
    private static final String TAG = DatabaseSyncingService.class.getName();
    private static final int NOTIFICATION_OK_TIMEOUT_MILLS = 2 * 1000;

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
        } else {
            Log.w(TAG, "unknown action");
        }
        return START_NOT_STICKY;
    }

    private static class FetchTask extends FetchDatabaseTask {
        private static int nextNotificationId = 1;

        private final Context context;
        private final URL url;
        private final NotificationManager notificationManager;
        private final int notificationId;

        FetchTask(Context context, URL url, String masterPwd, String username, String password) {
            super(context, url, masterPwd, username, password);
            this.context = context;
            this.url = url;
            notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            notificationId = nextNotificationId++;
        }

        @Override
        protected void onPreExecute() {
            Notification.Builder builder = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.ic_cloud_white_black_24dp)
                    .setContentTitle("Fetching database")
                    .setContentText(url.toString())
                    .setOngoing(true)
                    .setProgress(0, 0, true);
            notificationManager.notify(notificationId, builder.build());
        }

        @Override
        protected void onPostExecute(String error) {
            Notification.Builder builder = new Notification.Builder(context);
            Intent intent = new Intent(BROADCAST_SYNC_FINISHED);
            if (error == null) {
                builder.setContentTitle("Database fetched");
                KeePassFile db = KeePassStorage.getKeePassFile();
                if (db != null && db.getMeta().getDatabaseName() != null)
                    builder.setSmallIcon(R.drawable.ic_cloud_done_white_24dp)
                            .setContentText(db.getMeta().getDatabaseName());
                new Handler().postDelayed(() -> notificationManager.cancel(notificationId),
                        NOTIFICATION_OK_TIMEOUT_MILLS);
            } else {
                builder.setSmallIcon(R.drawable.ic_report_problem_white_24dp)
                        .setContentTitle("Database fetch failed")
                        .setContentText(error);
                intent.putExtra(EXTRA_SYNC_ERROR, error);
            }
            notificationManager.notify(notificationId, builder.build());
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }
}

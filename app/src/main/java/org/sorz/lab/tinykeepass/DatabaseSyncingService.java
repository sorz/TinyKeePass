package org.sorz.lab.tinykeepass;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;

import de.slackspace.openkeepass.domain.KeePassFile;

public class DatabaseSyncingService extends Service {
    private static final String TAG = DatabaseSyncingService.class.getName();
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_FETCH = "action-fetch";
    public static final String EXTRA_URL = "extra-url";
    public static final String EXTRA_MASTER_KEY = "extra-master-key";
    public static final String EXTRA_USERNAME = "extra-username";
    public static final String EXTRA_PASSWORD = "extra-password";
    public static final String BROADCAST_DATABASE_UPDATED = "intent-database-updated";

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
        private final Context context;
        private final URL url;
        private final NotificationManager notificationManager;

        FetchTask(Context context, URL url, String masterPwd, String username, String password) {
            super(context, url, masterPwd, username, password);
            this.context = context;
            this.url = url;
            notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        }

        @Override
        protected void onPreExecute() {
            Notification.Builder builder = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.ic_cloud_white_black_24dp)
                    .setContentTitle("Fetching database")
                    .setContentText(url.toString())
                    .setProgress(0, 0, true);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        @Override
        protected void onPostExecute(String error) {
            Notification.Builder builder = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.ic_cloud_white_black_24dp);
            if (error == null) {
                builder.setContentTitle("Database fetched");
                KeePassFile db = KeePassStorage.getKeePassFile();
                if (db != null && db.getMeta().getDatabaseName() != null)
                    builder.setContentText(db.getMeta().getDatabaseName());
                Intent intent = new Intent(BROADCAST_DATABASE_UPDATED);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            } else {
                builder.setContentTitle("Database fetch failed")
                        .setContentText(error);
            }
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }
}

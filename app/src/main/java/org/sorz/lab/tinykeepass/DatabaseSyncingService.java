package org.sorz.lab.tinykeepass;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import java.lang.ref.WeakReference;

import de.slackspace.openkeepass.domain.KeePassFile;

public class DatabaseSyncingService extends Service {
    private static final String TAG = DatabaseSyncingService.class.getName();
    private static final int NOTIFICATION_OK_TIMEOUT_MILLS = 2 * 1000;
    private static final String CHANNEL_ID_SYNCING = "channel-syncing";

    private static final String ACTION_CANCEL = "action-cancel";
    public static final String ACTION_FETCH = "action-fetch";
    public static final String EXTRA_URL = "extra-url";
    public static final String EXTRA_MASTER_KEY = "extra-master-key";
    public static final String EXTRA_USERNAME = "extra-username";
    public static final String EXTRA_PASSWORD = "extra-password";
    public static final String BROADCAST_SYNC_FINISHED = "broadcast-sync-finished";
    public static final String EXTRA_SYNC_ERROR = "extra-sync-error";

    private static boolean running = false;

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
            Uri uri = Uri.parse(intent.getStringExtra(EXTRA_URL));
            String masterKey = intent.getStringExtra(EXTRA_MASTER_KEY);
            String username = intent.getStringExtra(EXTRA_USERNAME);
            String password = intent.getStringExtra(EXTRA_PASSWORD);
            fetchTask = new FetchTask(this, uri, masterKey, username, password);
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

    public static Intent getFetchIntent(Context context, String url, String masterKey,
                                        String username, String password) {
        Intent intent = new Intent(context, DatabaseSyncingService.class);
        intent.setAction(DatabaseSyncingService.ACTION_FETCH);
        intent.putExtra(DatabaseSyncingService.EXTRA_URL, url);
        intent.putExtra(DatabaseSyncingService.EXTRA_MASTER_KEY, masterKey);
        intent.putExtra(DatabaseSyncingService.EXTRA_USERNAME, username);
        intent.putExtra(DatabaseSyncingService.EXTRA_PASSWORD, password);
        return intent;
    }

    public static boolean isRunning() {
        return running;
    }

    private static class FetchTask extends FetchDatabaseTask {
        private static int nextNotificationId = 1;

        private final WeakReference<Service> service;
        private final Uri uri;
        private final NotificationManager notificationManager;
        private final int notificationId;

        FetchTask(Service service, Uri uri, String masterPwd, String username, String password) {
            super(service, uri, masterPwd, username, password);
            this.service = new WeakReference<>(service);
            this.uri = uri;
            notificationManager = (NotificationManager) service.getSystemService(NOTIFICATION_SERVICE);
            notificationId = nextNotificationId++;
        }

        @Override
        protected void onPreExecute() {
            Context context = this.service.get();
            if (context == null)
                return;
            running = true;

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
                    .setContentText(uri.toString())
                    .setOngoing(true)
                    .setProgress(0, 0, true)
                    .setActions(cancel);

            // Channel for Oreo+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID_SYNCING,
                        context.getString(R.string.channel_syncing),
                        NotificationManager.IMPORTANCE_LOW);
                notificationManager.createNotificationChannel(channel);
                builder.setChannelId(CHANNEL_ID_SYNCING);
            }

            notificationManager.notify(notificationId, builder.build());
        }

        @Override
        protected void onPostExecute(String error) {
            Service service = this.service.get();
            if (service == null) {
                notificationManager.cancel(notificationId);
                Log.w(TAG, "task done after service exited");
                return;
            }

            Notification.Builder builder = new Notification.Builder(service);
            if (error == null) {
                builder.setContentTitle(service.getString(R.string.fetch_ok));
                KeePassFile db = KeePassStorage.get(service);
                if (db != null && db.getMeta().getDatabaseName() != null)
                    builder.setSmallIcon(R.drawable.ic_cloud_done_white_24dp)
                            .setContentText(db.getMeta().getDatabaseName());
                new Handler().postDelayed(() -> notificationManager.cancel(notificationId),
                        NOTIFICATION_OK_TIMEOUT_MILLS);
                notifyFinish(service, null);
            } else {
                builder.setSmallIcon(R.drawable.ic_report_problem_white_24dp)
                        .setContentTitle(service.getString(R.string.fetch_fail))
                        .setContentText(error);
                notifyFinish(service, error);
            }
            notificationManager.notify(notificationId, builder.build());
        }

        @Override
        protected void onCancelled(String error) {
            Service service = this.service.get();
            if (service == null) {
                notificationManager.cancel(notificationId);
                Log.w(TAG, "task cancelled after service exited");
                return;
            }
            notificationManager.cancel(notificationId);
            notifyFinish(service, service.getString(R.string.fetch_cancel_by_user));
        }

        private void notifyFinish(Service service, String error) {
            running = false;
            Intent intent = new Intent(BROADCAST_SYNC_FINISHED);
            if (error != null)
                intent.putExtra(EXTRA_SYNC_ERROR, error);
            service.sendBroadcast(intent);
        }
    }
}

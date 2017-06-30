package org.sorz.lab.tinykeepass;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class PasswordCopingService extends Service {
    private static final String TAG = PasswordCopingService.class.getName();
    private static final int NOTIFICATION_ID_COPY_PASSWORD = 1;
    private static final int PASSWORD_IN_CLIPBOARD_SECS = 10;
    private static final String ACTION_COPY_PASSWORD = "action-copy-password";
    private static final String ACTION_CLEAN_CLIPBOARD = "action-clean-clipboard";
    public static final String ACTION_NEW_NOTIFICATION = "action-new-notification";
    public static final String EXTRA_PASSWORD = "extra-password";
    public static final String EXTRA_USERNAME = "extra-username";
    public static final String EXTRA_ENTRY_TITLE = "extra-entry-title";

    private NotificationManager notificationManager;
    private ClipboardManager clipboardManager;
    private Thread countingDownTask;

    public PasswordCopingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w(TAG, "null intent");
        } else if (ACTION_NEW_NOTIFICATION.equals(intent.getAction())) {
            String password = intent.getStringExtra(EXTRA_PASSWORD);
            String username = intent.getStringExtra(EXTRA_USERNAME);
            String title = intent.getStringExtra(EXTRA_ENTRY_TITLE);
            if (password == null) {
                Log.e(TAG, "password is null");
                return START_NOT_STICKY;
            }
            if (countingDownTask != null)
                countingDownTask.interrupt();
            newNotification(password, username, title);
        } else if (ACTION_COPY_PASSWORD.equals(intent.getAction())) {
            String password = intent.getStringExtra(EXTRA_PASSWORD);
            copyPassword(password);
        } else if (ACTION_CLEAN_CLIPBOARD.equals(intent.getAction())) {
            cleanPassword();
        } else {
            Log.w(TAG, "unknown action");
        }
        return START_NOT_STICKY;
    }

    private void newNotification(String password, String username, String title) {
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_vpn_key_white_24dp)
                .setContentTitle("Click to copy password")
                .setVisibility(Notification.VISIBILITY_SECRET);
        if (username!= null)
            builder.setContentText(
                    String.format("Copy %s's password to clipboard.", username));
        else if (title != null)
            builder.setContentText("Copy password for " + title);
        else
            builder.setContentText("Copy password to clipboard.");

        Intent copyIntent = new Intent(this, PasswordCopingService.class);
        copyIntent.setAction(ACTION_COPY_PASSWORD);
        copyIntent.putExtra(EXTRA_PASSWORD, password);
        PendingIntent copyPendingIntent = PendingIntent.getService(
                this, 0, copyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(copyPendingIntent);

        notificationManager.notify(NOTIFICATION_ID_COPY_PASSWORD, builder.build());
    }

    private void copyPassword(String password) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Password", password));
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_vpn_key_white_24dp)
                .setContentTitle("Password copied")
                .setContentText("It will be cleaned on timeout.");

        Intent cleanIntent = new Intent(this, PasswordCopingService.class);
        cleanIntent.setAction(ACTION_CLEAN_CLIPBOARD);
        PendingIntent cleanPendingIntent = PendingIntent.getService(
                this, 0, cleanIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setDeleteIntent(cleanPendingIntent);

        countingDownTask = new Thread(() -> {
            for (int secs = PASSWORD_IN_CLIPBOARD_SECS; secs > 0; --secs) {
                builder.setProgress(PASSWORD_IN_CLIPBOARD_SECS, secs, false);
                notificationManager.notify(NOTIFICATION_ID_COPY_PASSWORD, builder.build());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    cleanPassword();
                    countingDownTask = null;
                    return;
                }
            }
            countingDownTask = null;
            notificationManager.cancel(NOTIFICATION_ID_COPY_PASSWORD);
            cleanPassword();
        });
        countingDownTask.start();
    }

    private void cleanPassword() {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("",""));
    }
}

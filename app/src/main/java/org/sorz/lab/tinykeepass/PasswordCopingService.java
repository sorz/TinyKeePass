package org.sorz.lab.tinykeepass;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class PasswordCopingService extends Service {
    private static final String TAG = PasswordCopingService.class.getName();
    private static final int PASSWORD_IN_CLIPBOARD_SECS = 15;
    private static final int NOTIFICATION_TIMEOUT_SECS = 3 * 60;
    private static final String CHANNEL_ID_COPYING = "channel-copying";
    private static final String ACTION_SHOW_PASSWORD = "action-show-password";
    public static final String ACTION_CLEAN_CLIPBOARD = "action-clean-clipboard";
    public static final String ACTION_COPY_PASSWORD = "action-copy-password";
    public static final String ACTION_NEW_NOTIFICATION = "action-new-notification";
    public static final String EXTRA_PASSWORD = "extra-password";
    public static final String EXTRA_USERNAME = "extra-username";
    public static final String EXTRA_ENTRY_TITLE = "extra-entry-title";

    private NotificationManager notificationManager;
    private ClipboardManager clipboardManager;
    private Thread cleanNotificationTimer;
    private Thread countingDownTask;
    private int notificationId = 1;

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

        // Create notification channel for Oreo+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_COPYING,
                    getApplicationContext().getString(R.string.channel_coping),
                    NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
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
            newNotification(password, username, title);
        } else if (ACTION_COPY_PASSWORD.equals(intent.getAction())) {
            String password = intent.getStringExtra(EXTRA_PASSWORD);
            copyPassword(password);
        } else if (ACTION_SHOW_PASSWORD.equals(intent.getAction())) {
            String password = intent.getStringExtra(EXTRA_PASSWORD);
            showPassword(password);
        } else if (ACTION_CLEAN_CLIPBOARD.equals(intent.getAction())) {
            stopTask(countingDownTask);
            cleanPassword();
        } else {
            Log.w(TAG, "unknown action");
        }
        return START_NOT_STICKY;
    }


    private PendingIntent getCopyPendingIntent(String password) {
        Intent copyIntent = new Intent(this, PasswordCopingService.class);
        copyIntent.setAction(ACTION_COPY_PASSWORD);
        copyIntent.putExtra(EXTRA_PASSWORD, password);
        return PendingIntent.getService(
                this, 0, copyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }


    private void newNotification(String password, String username, String title) {
        stopTask(countingDownTask);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_vpn_key_white_24dp)
                .setContentTitle("Touch to copy password")
                .setVisibility(Notification.VISIBILITY_SECRET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setChannelId(CHANNEL_ID_COPYING);
        if (username!= null && !username.isEmpty())
            builder.setContentText(
                    String.format("Copy %s's password to clipboard.", username));
        else if (title != null && !title.isEmpty())
            builder.setContentText("Copy password for " + title);
        else
            builder.setContentText("Copy password to clipboard.");

        builder.setContentIntent(getCopyPendingIntent(password));

        Intent showIntent = new Intent(this, PasswordCopingService.class);
        showIntent.setAction(ACTION_SHOW_PASSWORD);
        showIntent.putExtra(EXTRA_PASSWORD, password);
        PendingIntent showPendingIntent = PendingIntent.getService(
                this, 0, showIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action actionShow = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_visibility_white_24dp),
                "Show me", showPendingIntent).build();
        builder.addAction(actionShow);

        notificationManager.cancel(notificationId);
        notificationManager.notify(++notificationId, builder.build());

        int myNotificationId = notificationId;
        stopTask(cleanNotificationTimer);
        cleanNotificationTimer = new Thread(() -> {
            try {
                Thread.sleep(NOTIFICATION_TIMEOUT_SECS * 1000);
            } catch (InterruptedException e) {
                cleanNotificationTimer = null;
                return;
            }
            notificationManager.cancel(myNotificationId);
        });
        cleanNotificationTimer.start();
    }

    private void showPassword(String password) {
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_vpn_key_white_24dp)
                .setContentTitle("Your password is")
                .setContentText(password)
                .setVisibility(Notification.VISIBILITY_SECRET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setChannelId(CHANNEL_ID_COPYING);

        builder.setContentIntent(getCopyPendingIntent(password));
        notificationManager.notify(notificationId, builder.build());
    }

    private void copyPassword(String password) {
        stopTask(cleanNotificationTimer);

        clipboardManager.setPrimaryClip(ClipData.newPlainText("Password", password));
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_vpn_key_white_24dp)
                .setContentTitle("Password copied")
                .setContentText("Swipe to clean clipboard now");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setChannelId(CHANNEL_ID_COPYING);

        Intent cleanIntent = new Intent(this, PasswordCopingService.class);
        cleanIntent.setAction(ACTION_CLEAN_CLIPBOARD);
        PendingIntent cleanPendingIntent = PendingIntent.getService(
                this, 0, cleanIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setDeleteIntent(cleanPendingIntent);

        int myNotificationId = notificationId;
        countingDownTask = new Thread(() -> {
            int maxPos = 500;
            long posDurationMills = PASSWORD_IN_CLIPBOARD_SECS * 1000 / maxPos;
            for (int pos = maxPos; pos > 0; --pos) {
                builder.setProgress(maxPos, pos, false);
                notificationManager.notify(myNotificationId, builder.build());
                try {
                    Thread.sleep(posDurationMills);
                } catch (InterruptedException e) {
                    break;
                }
            }
            countingDownTask = null;
            notificationManager.cancel(myNotificationId);
            cleanPassword();
        });
        countingDownTask.start();
    }

    private void cleanPassword() {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("",""));
    }

    private void stopTask(Thread task) {
        if (task != null && task.isAlive())
            task.interrupt();
    }
}

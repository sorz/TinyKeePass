package org.sorz.lab.tinykeepass;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class PasswordCopingService extends Service {
    private static final String TAG = PasswordCopingService.class.getName();
    private static final int NOTIFICATION_ID_COPY_PASSWORD = 1;

    public static final String ACTION_NEW_NOTIFICATION = "action-new-notification";
    public static final String EXTRA_PASSWORD = "extra-password";
    public static final String EXTRA_USERNAME = "extra-username";
    public static final String EXTRA_ENTRY_TITLE = "extra-entry-title";

    private NotificationManager notificationManager;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_NEW_NOTIFICATION.equals(intent.getAction())) {
            Log.w(TAG, "null intent or unknown action");
            return START_NOT_STICKY;
        }
        String password = intent.getStringExtra(EXTRA_PASSWORD);
        String username = intent.getStringExtra(EXTRA_USERNAME);
        String title = intent.getStringExtra(EXTRA_ENTRY_TITLE);
        if (password == null) {
            Log.e(TAG, "password is null");
            return START_NOT_STICKY;
        }
        newNotification(password, username, title);

        return START_NOT_STICKY;
    }

    private void newNotification(String password, String username, String title) {
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_vpn_key_white_24dp)
                .setContentTitle("Click to copy password");
        if (username!= null)
            builder.setContentText(
                    String.format("Copy %s's password to clipboard.", username));
        else if (title != null)
            builder.setContentText("Copy password for " + title);
        else
            builder.setContentText("Copy password to clipboard.");
        notificationManager.notify(NOTIFICATION_ID_COPY_PASSWORD, builder.build());
    }
}

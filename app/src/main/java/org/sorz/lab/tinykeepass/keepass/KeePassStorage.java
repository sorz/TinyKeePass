package org.sorz.lab.tinykeepass.keepass;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.util.Log;

import com.kunzisoft.keepass.database.element.Database;

/**
 * Keep unlocked KeePass file here statically & globally.
 * It will be dropped when:
 *   - process killed by the system (of course;
 *   - just after screen off;
 *   - call get() after AUTH_TIMEOUT_MILLS since last set().
 */
public class KeePassStorage {
    private static final String TAG = KeePassStorage.class.getName();
    private static final long AUTH_TIMEOUT_MILLS = 5 * 60 * 1000;  // 5 minutes
    private static Database keePassFile;
    private static long lastAuthTime;

    public static @Nullable Database get(Context context) {
        if (keePassFile != null &&
                SystemClock.elapsedRealtime() - lastAuthTime > AUTH_TIMEOUT_MILLS)
            set(context, null);
        return keePassFile;
    }

    public static void set(Context context, @Nullable Database file) {
        if (keePassFile == null && file != null) {
            // first set file, register screen-off receiver.
            registerBroadcastReceiver(context);
        } else if (keePassFile != null && file == null) {
            // clear file, unregister it.
            context.getApplicationContext().unregisterReceiver(broadcastReceiver);
            keePassFile.closeAndClear(null);
        }
        keePassFile = file;
        lastAuthTime = SystemClock.elapsedRealtime();
    }

    public static void registerBroadcastReceiver(Context context) {
        IntentFilter screenOffFilter = new IntentFilter();
        screenOffFilter.addAction(Intent.ACTION_SCREEN_OFF);
        // use application context because it's bound to the process instead of an activity.
        context.getApplicationContext()
                .registerReceiver(broadcastReceiver, screenOffFilter);
    }

    private static BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                Log.d(TAG, "screen off, clean keepass file");
                set(context, null);
            }
        }
    };
}

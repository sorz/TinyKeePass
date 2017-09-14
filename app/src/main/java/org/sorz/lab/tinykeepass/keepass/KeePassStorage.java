package org.sorz.lab.tinykeepass.keepass;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import de.slackspace.openkeepass.domain.KeePassFile;

/**
 * Created by xierch on 2017/6/30.
 */
public class KeePassStorage {
    private final static String TAG = KeePassStorage.class.getName();
    private static KeePassFile keePassFile;

    public static KeePassFile get() {
        return keePassFile;
    }

    public static void set(Context context, KeePassFile file) {
        // TODO: check auth timeout here
        if (keePassFile == null && file != null) {
            registerBroadcastReceiver(context);
        } else if (keePassFile != null && file == null) {
            unregisterBroadcastReceiver(context);
        }
        keePassFile = file;
    }

    public static void registerBroadcastReceiver(Context context) {
        IntentFilter screenOffFilter = new IntentFilter();
        screenOffFilter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(broadcastReceiver, screenOffFilter);
    }

    public static void unregisterBroadcastReceiver(Context context) {
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (IllegalArgumentException e) {
            // ignore no registered error
        }
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

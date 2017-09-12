package org.sorz.lab.tinykeepass;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import java.io.File;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

public class MainActivity extends BaseActivity {
    private final static String TAG = MainActivity.class.getName();

    private SharedPreferences preferences;
    private File databaseFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        databaseFile = new File(getNoBackupFilesDir(), FetchDatabaseTask.DB_FILENAME);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new DatabaseLockedFragment())
                    .commit();

            if (!hasConfiguredDatabase()) {
                doConfigureDatabase();
                finish();
            } else {
                doUnlockDatabase();
            }
        } else if (KeePassStorage.get() != null) {
            // Restarting activity
            KeePassStorage.registerBroadcastReceiver(this);
        }
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isFinishing())
            KeePassStorage.set(this, null);
        else
            // Restarting activity, re-register it after restart done.
            KeePassStorage.unregisterBroadcastReceiver(this);
    }

    public boolean hasConfiguredDatabase() {
        return KeePassStorage.get() != null || databaseFile.canRead();
    }

    public void doUnlockDatabase() {
        if (KeePassStorage.get() != null) {
            showEntryList();
        } else {
            getDatabaseKeys(keys -> {
                try {
                    KeePassFile db = KeePassDatabase.getInstance(databaseFile)
                            .openDatabase(keys.get(0));
                    KeePassStorage.set(this, db);
                } catch (KeePassDatabaseUnreadableException | UnsupportedOperationException e) {
                    Log.w(TAG, "cannot open database.", e);
                    snackbar(e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
                    return;
                }
                showEntryList();
            }, this::showError);
        }
    }

    public void doLockDatabase() {
        KeePassStorage.set(this, null);
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DatabaseLockedFragment())
                .commit();
    }

    public void doConfigureDatabase() {
        KeePassStorage.set(this, null);
        startActivity(new Intent(this, DatabaseSetupActivity.class));
    }

    public void doSyncDatabase() {
        getDatabaseKeys(keys -> {
            String url = preferences.getString("db-url", "");
            String masterKey = keys.get(0);
            String username = null;
            String password = null;
            if (preferences.getBoolean("db-auth-required", false)) {
                username = preferences.getString("db-auth-username", "");
                password = keys.get(1);
            }
            Intent intent = DatabaseSyncingService.getFetchIntent(
                    this, url, masterKey, username, password);
            startService(intent);
        }, this::showError);
    }

    public void doCleanDatabase() {
        KeePassStorage.set(this, null);
        if (!databaseFile.delete())
            Log.w(TAG, "fail to delete database file");
        getSecureStringStorage().clear();
        snackbar(getString(R.string.clean_config_ok), Snackbar.LENGTH_SHORT).show();
    }

    private void showEntryList() {
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EntryFragment.newInstance())
                .commit();
    }

    private void showError(String message) {
        snackbar(message, Snackbar.LENGTH_LONG).show();
    }

    private Snackbar snackbar(CharSequence text, int duration) {
        return Snackbar.make(findViewById(R.id.fragment_container), text, duration);
    }
}

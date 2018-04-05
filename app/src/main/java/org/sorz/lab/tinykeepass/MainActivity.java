package org.sorz.lab.tinykeepass;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.getDatabaseFile;
import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.hasDatabaseConfigured;

public class MainActivity extends BaseActivity {
    private final static String TAG = MainActivity.class.getName();
    private final static int REQUEST_SETUP_DATABASE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new DatabaseLockedFragment())
                    .commit();

            if (!hasDatabaseConfigured(this)) {
                doConfigureDatabase();
            } else {
                doUnlockDatabase();
            }
        } else if (KeePassStorage.get(this) != null) {
            // Restarting activity
            KeePassStorage.registerBroadcastReceiver(this);
        }
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (KeePassStorage.get(this) != null)
            showEntryList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isFinishing())
            KeePassStorage.set(this, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SETUP_DATABASE:
                if (resultCode == RESULT_OK)
                    doUnlockDatabase();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    public void doUnlockDatabase() {
        if (KeePassStorage.get(this) != null) {
            showEntryList();
        } else {
            getDatabaseKeys(keys ->
                    openDatabase(keys.get(0), db -> showEntryList())
            , this::showError);
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
        Intent intent = new Intent(this, DatabaseSetupActivity.class);
        startActivityForResult(intent, REQUEST_SETUP_DATABASE);
    }

    public void doSyncDatabase() {
        getDatabaseKeys(keys -> {
            String url = getPreferences().getString("db-url", "");
            String masterKey = keys.get(0);
            String username = null;
            String password = null;
            if (getPreferences().getBoolean("db-auth-required", false)) {
                username = getPreferences().getString("db-auth-username", "");
                password = keys.get(1);
            }
            Intent intent = DatabaseSyncingService.getFetchIntent(
                    this, url, masterKey, username, password);
            startService(intent);
        }, this::showError);
    }

    public void doCleanDatabase() {
        KeePassStorage.set(this, null);
        if (!getDatabaseFile(this).delete())
            Log.w(TAG, "fail to delete database file");
        getSecureStringStorage().clear();
        DatabaseSetupActivity.clearDatabaseConfigs(getPreferences());
        showMessage(getString(R.string.clean_config_ok), Snackbar.LENGTH_SHORT);
    }

    private void showEntryList() {
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EntryFragment.newInstance())
                .commit();
    }

    private void showError(String message) {
        showMessage(message, Snackbar.LENGTH_LONG);
    }

    private void showMessage(CharSequence text, int duration) {
        View view = findViewById(R.id.fragment_container);
        // view will be null if this method called on onCreate().
        if (view != null) {
            Snackbar.make(view, text, duration).show();
        } else {
            duration = duration == Snackbar.LENGTH_SHORT ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
            Toast.makeText(this, text, duration).show();
        }
    }
}

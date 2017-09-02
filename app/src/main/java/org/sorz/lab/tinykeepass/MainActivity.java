package org.sorz.lab.tinykeepass;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import java.io.File;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

public class MainActivity extends AppCompatActivity
        implements FingerprintDialogFragment.OnFragmentInteractionListener {
    private final static String TAG = MainActivity.class.getName();
    private final static int REQUEST_CONFIRM_DEVICE_CREDENTIAL = 0;
    private final static int ACTION_OPEN_DB = 1;
    private final static int ACTION_SYNC_DB = 2;

    private SharedPreferences preferences;
    private KeyguardManager keyguardManager;
    private SecureStringStorage secureStringStorage;
    private int actionAfterGetKey;

    private File databaseFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        databaseFile = new File(getNoBackupFilesDir(), FetchDatabaseTask.DB_FILENAME);
        try {
            secureStringStorage = new SecureStringStorage(this);
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException(e);
        }

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
            getKeyThen(ACTION_OPEN_DB);
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
        getKeyThen(ACTION_SYNC_DB);
    }

    public void doCleanDatabase() {
        KeePassStorage.set(this, null);
        if (!databaseFile.delete())
            Log.w(TAG, "fail to delete database file");
        secureStringStorage.clear();
        snackbar(getString(R.string.clean_config_ok), Snackbar.LENGTH_SHORT).show();
    }

    private void getKeyThen(int action) {
        actionAfterGetKey = action;
        int authMethod = preferences.getInt("key-auth-method", 0);
        switch (authMethod) {
            case DatabaseSetupActivity.AUTH_METHOD_NONE:
            case DatabaseSetupActivity.AUTH_METHOD_SCREEN_LOCK:
                try {
                    Cipher cipher = secureStringStorage.getDecryptCipher();
                    getKey(cipher);
                } catch (UserNotAuthenticatedException e) {
                    // should do authentication
                    Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            getString(R.string.auth_key_title),
                            getString(R.string.auth_key_description));
                    startActivityForResult(intent, REQUEST_CONFIRM_DEVICE_CREDENTIAL);
                } catch (SecureStringStorage.SystemException e) {
                    throw new RuntimeException(e);
                }
                break;
            case DatabaseSetupActivity.AUTH_METHOD_FINGERPRINT:
                getFragmentManager().beginTransaction()
                        .add(FingerprintDialogFragment.newInstance(Cipher.DECRYPT_MODE),
                                "fingerprint")
                        .commit();
                break;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void getKey(Cipher cipher) {
        List<String> keys;
        try {
            keys = secureStringStorage.get(cipher);
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException | IllegalBlockSizeException | UserNotAuthenticatedException e) {
            Log.w(TAG, "fail to decrypt keys", e);
            snackbar(getString(R.string.fail_to_decrypt), Snackbar.LENGTH_LONG).show();
            return;
        }
        if (keys.size() < 2) {
            snackbar(getString(R.string.broken_keys), Snackbar.LENGTH_LONG).show();
            return;
        }

        switch (actionAfterGetKey) {
            case ACTION_OPEN_DB:
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
                break;
            case ACTION_SYNC_DB:
                String url = preferences.getString("db-url", "");
                String masterKey = keys.get(0);
                String username = null;
                String password = null;
                if (preferences.getBoolean("db-auth-required", false)) {
                    username = preferences.getString("db-auth-username", "");
                    password = keys.get(1);
                }
                Intent intent = new Intent(this, DatabaseSyncingService.class);
                intent.setAction(DatabaseSyncingService.ACTION_FETCH);
                intent.putExtra(DatabaseSyncingService.EXTRA_URL, url);
                intent.putExtra(DatabaseSyncingService.EXTRA_MASTER_KEY, masterKey);
                intent.putExtra(DatabaseSyncingService.EXTRA_USERNAME, username);
                intent.putExtra(DatabaseSyncingService.EXTRA_PASSWORD, password);
                startService(intent);
                break;
            default:
                break;
        }

    }

    private void showEntryList() {
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EntryFragment.newInstance())
                .commit();
    }

    private Snackbar snackbar(CharSequence text, int duration) {
        return Snackbar.make(findViewById(R.id.fragment_container), text, duration);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONFIRM_DEVICE_CREDENTIAL:
                if (resultCode == RESULT_OK)
                    getKeyThen(actionAfterGetKey);
                else
                    snackbar(getString(R.string.fail_to_auth), Snackbar.LENGTH_LONG).show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onFingerprintCancel() {
        snackbar(getString(R.string.fail_to_auth), Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onFingerprintSuccess(Cipher cipher) {
        getKey(cipher);
    }
}

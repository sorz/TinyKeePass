package org.sorz.lab.tinykeepass;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Entry;
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
    private ClipboardManager clipboardManager;
    private SecureStringStorage secureStringStorage;
    private int actionAfterGetKey;

    private File databaseFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
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

            if (KeePassStorage.getKeePassFile() == null && !databaseFile.canRead()) {
                doConfigureDatabase();
                finish();
            } else {
                doUnlockDatabase();
            }
        }
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    public void doUnlockDatabase() {
        if (KeePassStorage.getKeePassFile() != null) {
            showEntryList();
        } else {
            getKeyThen(ACTION_OPEN_DB);
        }
    }

    public void doLockDatabase() {
        KeePassStorage.setKeePassFile(null);
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DatabaseLockedFragment())
                .commit();
    }

    public void doConfigureDatabase() {
        KeePassStorage.setKeePassFile(null);
        startActivity(new Intent(this, DatabaseSetupActivity.class));
    }

    public void doSyncDatabase() {
        getKeyThen(ACTION_SYNC_DB);
    }

    public void copyEntry(Entry entry) {
        if (entry.getUsername() != null) {
            clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("Username", entry.getUsername()));
            Snackbar snackbar = snackbar(
                    String.format("Username \"%s\" copied", entry.getUsername()),
                    Snackbar.LENGTH_LONG);
            snackbar.setAction("Copy password", v -> {
                Intent intent = new Intent(this, PasswordCopingService.class);
                intent.setAction(PasswordCopingService.ACTION_COPY_PASSWORD);
                intent.putExtra(PasswordCopingService.EXTRA_PASSWORD, entry.getPassword());
                startService(intent);
                snackbar("Password copied", Snackbar.LENGTH_SHORT).show();
            }).show();
        }
        if (entry.getPassword() != null) {
            Intent intent = new Intent(this, PasswordCopingService.class);
            intent.setAction(PasswordCopingService.ACTION_NEW_NOTIFICATION);
            intent.putExtra(PasswordCopingService.EXTRA_PASSWORD, entry.getPassword());
            if (entry.getUsername() != null)
                intent.putExtra(PasswordCopingService.EXTRA_USERNAME, entry.getUsername());
            if (entry.getTitle() != null)
                intent.putExtra(PasswordCopingService.EXTRA_ENTRY_TITLE, entry.getTitle());
            startService(intent);
        }
    }

    private void getKeyThen(int action) {
        actionAfterGetKey = action;
        int authMethod = preferences.getInt("key-auth-method", 0);
        switch (authMethod) {
            case 0: // no auth
            case 1: // screen lock
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
            case 2: // fingerprint
                getFragmentManager().beginTransaction()
                        .add(FingerprintDialogFragment.newInstance(), "fingerprint")
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
            snackbar("Failed to decrypt keys", Snackbar.LENGTH_LONG).show();
            return;
        }
        if (keys.size() < 2) {
            snackbar("Broken keys", Snackbar.LENGTH_LONG).show();
            return;
        }

        switch (actionAfterGetKey) {
            case ACTION_OPEN_DB:
                try {
                    KeePassFile db = KeePassDatabase.getInstance(databaseFile)
                            .openDatabase(keys.get(0));
                    KeePassStorage.setKeePassFile(db);
                } catch (KeePassDatabaseUnreadableException | UnsupportedOperationException e) {
                    Log.w(TAG, "cannot open database.", e);
                    snackbar(e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
                    return;
                }
                showEntryList();
                break;
            case ACTION_SYNC_DB:
                URL url;
                try {
                    url = new URL(preferences.getString("db-url", ""));
                } catch (MalformedURLException e) {
                    snackbar(e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                    return;
                }
                String username = null;
                String password = null;
                if (preferences.getBoolean("db-auth-required", false)) {
                    username = preferences.getString("db-auth-username", "");
                    password = keys.get(1);
                }
                new FetchDatabaseTask(this, url, keys.get(0), username, password) {
                    @Override
                    protected void onPostExecute(String error) {
                        if (error != null) {
                            snackbar(error, Snackbar.LENGTH_SHORT).show();
                        } else {
                            snackbar("Synchronize finished", Snackbar.LENGTH_SHORT).show();
                            showEntryList();
                        }
                    }
                }.execute();
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
        return Snackbar.make(findViewById(R.id.toolbar), text, duration);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONFIRM_DEVICE_CREDENTIAL:
                if (resultCode == RESULT_OK)
                    getKeyThen(actionAfterGetKey);
                else
                    snackbar("Failed to authenticate user", Snackbar.LENGTH_LONG).show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onFingerprintCancel() {
        snackbar("Failed to authenticate user", Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onFingerprintSuccess(Cipher cipher) {
        getKey(cipher);
    }
}

package org.sorz.lab.tinykeepass;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.sorz.lab.tinykeepass.auth.FingerprintDialogFragment;
import org.sorz.lab.tinykeepass.auth.SecureStringStorage;
import org.sorz.lab.tinykeepass.keepass.OpenKeePassTask;

import java.security.KeyException;
import java.util.List;
import java.util.function.Consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import de.slackspace.openkeepass.domain.KeePassFile;

import static org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_FINGERPRINT;
import static org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_NONE;
import static org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_SCREEN_LOCK;
import static org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_UNDEFINED;
import static org.sorz.lab.tinykeepass.DatabaseSetupActivity.PREF_KEY_AUTH_METHOD;


public abstract class BaseActivity extends AppCompatActivity
        implements FingerprintDialogFragment.OnFragmentInteractionListener {
    private final static String TAG = MainActivity.class.getName();
    private final static int REQUEST_CONFIRM_DEVICE_CREDENTIAL = 100;
    private final static int REQUEST_SETUP_DATABASE = 101;

    private SharedPreferences preferences;
    private KeyguardManager keyguardManager;
    private SecureStringStorage secureStringStorage;
    private Consumer<List<String>> onKeyRetrieved;
    private Consumer<String> onKeyAuthFailed;
    private Runnable onKeySaved;
    private List<String> keysToEncrypt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        try {
            secureStringStorage = new SecureStringStorage(this);
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONFIRM_DEVICE_CREDENTIAL:
                if (resultCode == RESULT_OK)
                    getKey();
                else
                    authFail(getString(R.string.fail_to_auth));
                break;
            case REQUEST_SETUP_DATABASE:
                if (resultCode == RESULT_OK)
                    getKey();
                else
                    authFail(getString(R.string.fail_to_decrypt));
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void authFail(String message) {
        if (onKeyAuthFailed != null)
            onKeyAuthFailed.accept(message);
        onKeyAuthFailed = null;
        onKeySaved = null;
        onKeyRetrieved = null;
        keysToEncrypt = null;
    }

    @Override
    public void onFingerprintCancel() {
        authFail(getString(R.string.fail_to_auth));
    }

    @Override
    public void onFingerprintSuccess(Cipher cipher) {
        if (onKeyRetrieved != null)
            decryptKey(cipher);
        else if (onKeySaved != null)
            encryptKeys(cipher);
    }

    protected void getDatabaseKeys(Consumer<List<String>> onKeyRetrieved,
                                   Consumer<String> onKeyAuthFailed) {
        this.onKeyRetrieved = onKeyRetrieved;
        this.onKeyAuthFailed = onKeyAuthFailed;
        getKey();
    }

    protected void saveDatabaseKeys(@NonNull List<String> keys, Runnable onKeySaved,
                                    Consumer<String> onKeyAuthFailed) {
        this.onKeySaved = onKeySaved;
        this.onKeyAuthFailed = onKeyAuthFailed;
        saveKey(keys);
    }

    @Override
    public void onKeyException(KeyException e) {
        // Key is invalided, have to reconfigure passwords.
        Intent intent = new Intent(this, DatabaseSetupActivity.class);
        startActivityForResult(intent, REQUEST_SETUP_DATABASE);
    }

    /**
     * Authenticate user, then call {@link #encryptKeys(Cipher)} to save keys.
     * Finally, either {@link #onKeySaved} or {@link #onKeyAuthFailed} will be called.
     */
    private void saveKey(@NonNull List<String> keys) {
        keysToEncrypt = keys;
        int authMethod = preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED);
        try {
            switch (authMethod) {
                case AUTH_METHOD_NONE:
                    secureStringStorage.generateNewKey(false, -1);
                    encryptKeys(null);
                    break;
                case AUTH_METHOD_SCREEN_LOCK:
                    secureStringStorage.generateNewKey(true, 60);
                    Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            getString(R.string.auth_key_title),
                            getString(R.string.auth_key_description));
                    startActivityForResult(intent, REQUEST_CONFIRM_DEVICE_CREDENTIAL);
                    break;
                case AUTH_METHOD_FINGERPRINT:
                    secureStringStorage.generateNewKey(true, -1);
                    getFragmentManager().beginTransaction()
                            .add(FingerprintDialogFragment.newInstance(Cipher.ENCRYPT_MODE),
                                "fingerprint")
                            .commit();
                    break;
            }
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException("cannot generate new key", e);
        }
    }

    private void encryptKeys(Cipher cipher) {
        try {
            if (cipher == null)
                cipher = secureStringStorage.getEncryptCipher();
            secureStringStorage.put(cipher, keysToEncrypt);
        } catch (UserNotAuthenticatedException e) {
            Log.e(TAG, "cannot get cipher from system", e);
            onKeyAuthFailed.accept("cannot get cipher from system");
            return;
        } catch (SecureStringStorage.SystemException | KeyException e) {
            throw new RuntimeException("cannot get save keys", e);
        }
        onKeySaved.run();
        onKeySaved = null;
        onKeyAuthFailed = null;
        keysToEncrypt = null;
    }

    /**
     * Authenticate user, then call {@link #decryptKey(Cipher)} to get keys.
     * Finally, either {@link #onKeyRetrieved} or {@link #onKeyAuthFailed} will be called.
     */
    private void getKey() {
        int authMethod = preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED);
        switch (authMethod) {
            case AUTH_METHOD_UNDEFINED:
                authFail(getString(R.string.broken_keys));
                break;
            case AUTH_METHOD_NONE:
            case AUTH_METHOD_SCREEN_LOCK:
                try {
                    Cipher cipher = secureStringStorage.getDecryptCipher();
                    decryptKey(cipher);
                } catch (UserNotAuthenticatedException e) {
                    // should do authentication
                    Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            getString(R.string.auth_key_title),
                            getString(R.string.auth_key_description));
                    startActivityForResult(intent, REQUEST_CONFIRM_DEVICE_CREDENTIAL);
                } catch (KeyException e) {
                    onKeyException(e);
                } catch (SecureStringStorage.SystemException e) {
                    throw new RuntimeException(e);
                }
                break;
            case AUTH_METHOD_FINGERPRINT:
                getFragmentManager().beginTransaction()
                        .add(FingerprintDialogFragment.newInstance(Cipher.DECRYPT_MODE),
                                "fingerprint")
                        .commit();
                break;
        }
    }

    private void decryptKey(Cipher cipher) {
        List<String> keys;
        try {
            keys = secureStringStorage.get(cipher);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Log.w(TAG, "fail to decrypt keys", e);
            authFail(getString(R.string.fail_to_decrypt));
            return;
        }
        if (keys == null || keys.size() < 2) {
            authFail(getString(R.string.broken_keys));
            return;
        }
        onKeyRetrieved.accept(keys);
        onKeyAuthFailed = null;
        onKeyRetrieved = null;
    }

    protected void openDatabase(String masterKey, Consumer<KeePassFile> onSuccess) {
        new OpenTask(this, masterKey, onSuccess).execute();
    }

    protected SecureStringStorage getSecureStringStorage() {
        return secureStringStorage;
    }

    protected SharedPreferences getPreferences() {
        return preferences;
    }

    private static class OpenTask extends OpenKeePassTask {
        private final Consumer<KeePassFile> onSuccess;

        OpenTask(Activity activity, String masterKey, Consumer<KeePassFile> onSuccess) {
            super(activity, masterKey);
            // TODO: memory leaks?
            this.onSuccess = onSuccess;
        }

        @Override
        protected void onPostExecute(KeePassFile db) {
            super.onPostExecute(db);
            if (db != null)
                onSuccess.accept(db);
        }
    }
}

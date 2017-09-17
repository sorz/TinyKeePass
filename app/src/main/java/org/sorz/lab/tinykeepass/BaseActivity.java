package org.sorz.lab.tinykeepass;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.sorz.lab.tinykeepass.auth.FingerprintDialogFragment;
import org.sorz.lab.tinykeepass.auth.SecureStringStorage;
import org.sorz.lab.tinykeepass.keepass.KeePassStorage;
import org.sorz.lab.tinykeepass.keepass.OpenKeePassTask;

import java.util.List;
import java.util.function.Consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import de.slackspace.openkeepass.domain.KeePassFile;


public abstract class BaseActivity extends AppCompatActivity
        implements FingerprintDialogFragment.OnFragmentInteractionListener {
    private final static String TAG = MainActivity.class.getName();
    private final static int REQUEST_CONFIRM_DEVICE_CREDENTIAL = 0;

    private SharedPreferences preferences;
    private KeyguardManager keyguardManager;
    private SecureStringStorage secureStringStorage;
    private Consumer<List<String>> onKeyRetrieved;
    private Consumer<String> onKeyAuthFailed;

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
                    onKeyAuthFailed.accept(getString(R.string.fail_to_auth));
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isFinishing())
            KeePassStorage.set(this, null);
    }

    @Override
    public void onFingerprintCancel() {
        onKeyAuthFailed.accept(getString(R.string.fail_to_auth));
    }

    @Override
    public void onFingerprintSuccess(Cipher cipher) {
        decryptKey(cipher);
    }

    protected void getDatabaseKeys(Consumer<List<String>> onKeyRetrieved,
                                   Consumer<String> onKeyAuthFailed) {
        this.onKeyRetrieved = onKeyRetrieved;
        this.onKeyAuthFailed = onKeyAuthFailed;
        getKey();
    }

    private void getKey() {
        int authMethod = preferences.getInt("key-auth-method", 0);
        switch (authMethod) {
            case DatabaseSetupActivity.AUTH_METHOD_NONE:
            case DatabaseSetupActivity.AUTH_METHOD_SCREEN_LOCK:
                try {
                    Cipher cipher = secureStringStorage.getDecryptCipher();
                    decryptKey(cipher);
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

    private void decryptKey(Cipher cipher) {
        List<String> keys;
        try {
            keys = secureStringStorage.get(cipher);
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException | IllegalBlockSizeException | UserNotAuthenticatedException e) {
            Log.w(TAG, "fail to decrypt keys", e);
            onKeyAuthFailed.accept(getString(R.string.fail_to_decrypt));
            return;
        }
        if (keys.size() < 2) {
            onKeyAuthFailed.accept(getString(R.string.broken_keys));
            return;
        }
        onKeyRetrieved.accept(keys);
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

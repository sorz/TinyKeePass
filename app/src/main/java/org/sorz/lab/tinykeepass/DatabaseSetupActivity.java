package org.sorz.lab.tinykeepass;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.fingerprint.FingerprintManager;
import android.preference.PreferenceManager;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;


public class DatabaseSetupActivity extends AppCompatActivity {
    final private static String TAG = DatabaseSetupActivity.class.getName();
    final private static int REQUEST_CONFIRM_DEVICE_CREDENTIAL = 0;
    private KeyguardManager keyguardManager;
    private FingerprintManager fingerprintManager;
    private SecureStringStorage secureStringStorage;

    private CheckBox checkBasicAuth;
    private EditText editDatabaseUrl;
    private EditText editAuthUsername;
    private EditText editAuthPassword;
    private EditText editMasterPassword;
    private Spinner spinnerAuthMethod;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_setup);

        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

        checkBasicAuth = (CheckBox) findViewById(R.id.checkBasicAuth);
        editDatabaseUrl = (EditText) findViewById(R.id.editDatabaseUrl);
        editAuthUsername = (EditText) findViewById(R.id.editAuthUsername);
        editAuthPassword = (EditText) findViewById(R.id.editAuthPassword);
        editMasterPassword = (EditText) findViewById(R.id.editMasterPassword);
        spinnerAuthMethod = (Spinner) findViewById(R.id.spinnerAuthMethod);
        CheckBox checkShowPassword = (CheckBox) findViewById(R.id.checkShowPassword);
        Button buttonConfirm = (Button) findViewById(R.id.buttonConfirm);

        checkBasicAuth.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) -> {
            editAuthUsername.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            editAuthPassword.setVisibility(editAuthUsername.getVisibility());
        });
        checkShowPassword.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) -> {
            editMasterPassword.setInputType(isChecked ?
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                    InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        });

        buttonConfirm.setOnClickListener((View button) -> {
            if (isInputValid())
                submit();
        });
    }

    private boolean isInputValid() {
        List<EditText> notEmptyInputs = new ArrayList<>(4);
        notEmptyInputs.add(editDatabaseUrl);
        notEmptyInputs.add(editMasterPassword);
        if (checkBasicAuth.isChecked()) {
            notEmptyInputs.add(editAuthUsername);
            notEmptyInputs.add(editAuthPassword);
        }
        for (EditText edit : notEmptyInputs) {
            if (edit.getText().toString().isEmpty()) {
                edit.setError("Cannot be empty");
                return false;
            }
        }

        switch (spinnerAuthMethod.getSelectedItemPosition()) {
            case 0: // no auth
                break;
            case 1: // lock screen
                if (!keyguardManager.isDeviceSecure()) {
                    Toast.makeText(this, R.string.no_screen_lock, Toast.LENGTH_LONG).show();
                    return false;
                }
                break;
            case 2: // fingerprint
                if (!fingerprintManager.isHardwareDetected()) {
                    Toast.makeText(this, R.string.no_fingerprint_detected, Toast.LENGTH_LONG).show();
                    return false;
                }
                if (!fingerprintManager.hasEnrolledFingerprints()) {
                    Toast.makeText(this, R.string.no_fingerprint_enrolled, Toast.LENGTH_LONG).show();
                    return false;
                }
                break;
        }

        return true;
    }

    @SuppressLint("StaticFieldLeak")
    private void submit() {
        URL url;
        try {
            url = new URL(editDatabaseUrl.getText().toString());
        } catch (MalformedURLException e) {
            editDatabaseUrl.setError("Not a valid URL");
            return;
        }

        String username = null;
        String password = null;
        if (checkBasicAuth.isChecked()) {
            username = editAuthUsername.getText().toString();
            password = editAuthPassword.getText().toString();
        }
        String masterPwd = editMasterPassword.getText().toString();
        new FetchDatabaseTask(this, url, masterPwd, username, password) {
            @Override
            protected void onPostExecute(String error) {
                if (error != null) {
                    Toast.makeText(DatabaseSetupActivity.this, error, Toast.LENGTH_SHORT).show();
                } else {
                    saveDatabaseConfigs();
                }
            }
        }.execute();
    }

    private void saveDatabaseConfigs() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit()
                .putString("db-url", editDatabaseUrl.getText().toString())
                .putString("db-auth-username", editAuthUsername.getText().toString())
                .putBoolean("db-auth-required", checkBasicAuth.isChecked())
                .putInt("key-auth-method", spinnerAuthMethod.getSelectedItemPosition())
                .apply();

        if (secureStringStorage == null) {
            try {
                secureStringStorage = new SecureStringStorage(this);
            } catch (SecureStringStorage.SystemException e) {
                throw new RuntimeException("cannot get SecureStringStorage", e);
            }
        }

        try {
            switch (spinnerAuthMethod.getSelectedItemPosition()) {
                case 0: // no auth
                    secureStringStorage.generateNewKey(false, -1);
                    saveKeys(null);
                    break;
                case 1: // lock screen
                    secureStringStorage.generateNewKey(true, 60);
                    Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            getString(R.string.auth_key_title),
                            getString(R.string.auth_key_decription));
                    startActivityForResult(intent, REQUEST_CONFIRM_DEVICE_CREDENTIAL);
                    break;
                case 2: // fingerprint
                    secureStringStorage.generateNewKey(true, -1);
                    break;
            }
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException("cannot generate new key", e);
        }
    }

    private void saveKeys(Cipher cipher) {
        try {
            if (cipher == null)
                cipher = secureStringStorage.getEncryptCipher();
            List<String> strings = new ArrayList<>(2);
            strings.add(editMasterPassword.getText().toString());
            strings.add(editAuthPassword.getText().toString());
            secureStringStorage.put(cipher, strings);
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException("cannot get save keys", e);
        } catch (UserNotAuthenticatedException e) {
            Log.e(TAG, "cannot get cipher from system", e);
            return;
        }
        Toast.makeText(DatabaseSetupActivity.this, "ok", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONFIRM_DEVICE_CREDENTIAL:
                if (resultCode == RESULT_OK)
                    saveKeys(null);
                break;
            default:
                break;
        }
    }

}

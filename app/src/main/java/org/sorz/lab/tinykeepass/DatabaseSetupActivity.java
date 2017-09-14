package org.sorz.lab.tinykeepass;

import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
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
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import org.sorz.lab.tinykeepass.auth.FingerprintDialogFragment;
import org.sorz.lab.tinykeepass.auth.SecureStringStorage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;


public class DatabaseSetupActivity extends AppCompatActivity
        implements FingerprintDialogFragment.OnFragmentInteractionListener {
    final private static String TAG = DatabaseSetupActivity.class.getName();
    final private static int REQUEST_CONFIRM_DEVICE_CREDENTIAL = 0;
    final private static int REQUEST_OPEN_FILE = 1;
    public static final int AUTH_METHOD_NONE = 0;
    public static final int AUTH_METHOD_SCREEN_LOCK = 1;
    public static final int AUTH_METHOD_FINGERPRINT = 2;

    private KeyguardManager keyguardManager;
    private FingerprintManager fingerprintManager;
    private SecureStringStorage secureStringStorage;
    private SharedPreferences preferences;

    private CheckBox checkBasicAuth;
    private EditText editDatabaseUrl;
    private EditText editAuthUsername;
    private EditText editAuthPassword;
    private EditText editMasterPassword;
    private CheckBox checkShowPassword;
    private Button buttonConfirm;
    private Button buttonOpenFile;
    private Spinner spinnerAuthMethod;
    private ProgressBar progressBar;
    private List<View> disabledViews = new ArrayList<>(8);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_setup);

        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        checkBasicAuth = findViewById(R.id.checkBasicAuth);
        editDatabaseUrl = findViewById(R.id.editDatabaseUrl);
        editAuthUsername = findViewById(R.id.editAuthUsername);
        editAuthPassword = findViewById(R.id.editAuthPassword);
        editMasterPassword = findViewById(R.id.editMasterPassword);
        spinnerAuthMethod = findViewById(R.id.spinnerAuthMethod);
        checkShowPassword = findViewById(R.id.checkShowPassword);
        progressBar = findViewById(R.id.progressBar);
        buttonConfirm = findViewById(R.id.buttonConfirm);
        buttonOpenFile = findViewById(R.id.buttonOpenFIle);

        editDatabaseUrl.setText(preferences.getString("db-url", ""));
        editAuthUsername.setText(preferences.getString("db-auth-username", ""));

        checkBasicAuth.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) -> {
            editAuthUsername.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            editAuthPassword.setVisibility(editAuthUsername.getVisibility());
        });
        checkShowPassword.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) ->
                editMasterPassword.setInputType(isChecked
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT));

        buttonOpenFile.setOnClickListener((View button) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_OPEN_FILE);
        });

        buttonConfirm.setOnClickListener((View button) -> {
            if (isInputValid())
                submit();
        });

        Intent intent = getIntent();
        if (intent != null && intent.getData() != null)
            editDatabaseUrl.setText(intent.getData().toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disabledViews.clear();
        checkBasicAuth = null;
        editDatabaseUrl = null;
        editAuthUsername = null;
        editAuthPassword = null;
        editMasterPassword = null;
        spinnerAuthMethod = null;
        checkShowPassword = null;
        buttonConfirm = null;
        buttonOpenFile = null;
    }

    private boolean isInputValid() {
        List<EditText> notEmptyInputs = new ArrayList<>(4);
        notEmptyInputs.add(editDatabaseUrl);
        notEmptyInputs.add(editMasterPassword);
        if (checkBasicAuth.isChecked()) {
            if (!editDatabaseUrl.getText().toString().startsWith("http")) {
                checkBasicAuth.setError(getString(R.string.basic_auth_with_non_http));
                return false;
            }
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

    private void submit() {
        Uri uri = Uri.parse(editDatabaseUrl.getText().toString());

        disabledViews.clear();
        disabledViews.add(checkBasicAuth);
        disabledViews.add(editDatabaseUrl);
        disabledViews.add(editAuthUsername);
        disabledViews.add(editAuthPassword);
        disabledViews.add(editMasterPassword);
        disabledViews.add(spinnerAuthMethod);
        disabledViews.add(checkBasicAuth);
        disabledViews.add(buttonConfirm);
        for (View v : disabledViews)
            v.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        String username = null;
        String password = null;
        if (checkBasicAuth.isChecked()) {
            username = editAuthUsername.getText().toString();
            password = editAuthPassword.getText().toString();
        }
        String masterPwd = editMasterPassword.getText().toString();
        new FetchTask(this, uri, masterPwd, username, password).execute();
    }

    private void cancelSubmit() {
        for (View v : disabledViews)
            v.setEnabled(true);
        disabledViews.clear();
        progressBar.setVisibility(View.INVISIBLE);
    }

    private void saveDatabaseConfigs() {
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
                case AUTH_METHOD_NONE:
                    secureStringStorage.generateNewKey(false, -1);
                    saveKeys(null);
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
                    requestFingerprintToSaveKeys();
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
            cancelSubmit();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void requestFingerprintToSaveKeys() {
        getFragmentManager().beginTransaction()
                .add(FingerprintDialogFragment.newInstance(Cipher.ENCRYPT_MODE), "fingerprint")
                .commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONFIRM_DEVICE_CREDENTIAL:
                if (resultCode == RESULT_OK)
                    saveKeys(null);
                else
                    cancelSubmit();
                break;
            case REQUEST_OPEN_FILE:
                if (resultCode == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    editDatabaseUrl.setText(uri.toString());
                    checkBasicAuth.setChecked(false);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onFingerprintCancel() {
        cancelSubmit();
    }

    @Override
    public void onFingerprintSuccess(Cipher cipher) {
        saveKeys(cipher);
    }


    private static class FetchTask extends FetchDatabaseTask {
        private final WeakReference<DatabaseSetupActivity> activity;

        FetchTask(DatabaseSetupActivity activity, Uri uri, String masterPwd,
                         String username, String password) {
            super(activity, uri, masterPwd, username, password);
            this.activity = new WeakReference<>(activity);
        }

        @Override
        protected void onPostExecute(String error) {
            DatabaseSetupActivity activity = this.activity.get();
            if (activity == null)
                return;

            if (error != null) {
                Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                activity.cancelSubmit();
            } else {
                activity.saveDatabaseConfigs();
            }
        }
    }
}
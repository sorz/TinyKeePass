package org.sorz.lab.tinykeepass;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.hardware.fingerprint.FingerprintManager;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class DatabaseSetupActivity extends AppCompatActivity {
    final static private String DB_FILE_NAME = "database.kdbx";
    final static private String TEMP_DB_FILE_NAME = "~database.kdbx";

    private KeyguardManager keyguardManager;
    private FingerprintManager fingerprintManager;

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
        SecureStringStorage storage = null;
        try {
            storage = new SecureStringStorage(this);
            switch (spinnerAuthMethod.getSelectedItemPosition()) {
                case 0: // no auth
                    storage.generateNewKey(false, -1);
                case 1: // lock screen
                    storage.generateNewKey(true, 60);
                case 2: // fingerprint
                    storage.generateNewKey(true, -1);
            }
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException("cannot get SecureStringStorage", e);
        }

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
        FileOutputStream output;
        try {
            output = openFileOutput("db.tmp", MODE_PRIVATE);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("cannot open db.tmp", e);
        }

        new FetchFileTask(output, url, username, password) {
            @Override
            protected void onPostExecute(String error) {
                if (error != null) {
                    Toast.makeText(DatabaseSetupActivity.this, error, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DatabaseSetupActivity.this, "ok", Toast.LENGTH_SHORT).show();
                    submitAfterDownload();
                }
            }
        }.execute();
    }

    private void submitAfterDownload() {
        // TODO: check new downloaded db file
        // TODO: save url, password, ...

    }


}

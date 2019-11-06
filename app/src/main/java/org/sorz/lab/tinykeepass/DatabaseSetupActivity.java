package org.sorz.lab.tinykeepass;

import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 *  Provide UI for configure a new db.
 *
 */
public class DatabaseSetupActivity extends BaseActivity {
    final private static String TAG = DatabaseSetupActivity.class.getName();
    final private static int REQUEST_OPEN_FILE = 1;
    public static final int AUTH_METHOD_UNDEFINED = -1;
    public static final int AUTH_METHOD_NONE = 0;
    public static final int AUTH_METHOD_SCREEN_LOCK = 1;
    public static final int AUTH_METHOD_FINGERPRINT = 2;
    public static final String PREF_DB_URL = "db-url";
    public static final String PREF_DB_AUTH_USERNAME = "db-auth-username";
    public static final String PREF_DB_AUTH_REQUIRED = "db-auth-required";
    public static final String PREF_KEY_AUTH_METHOD = "key-auth-method";

    private FingerprintManager fingerprintManager;

    private boolean launchMainActivityAfterSave = false;

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

        fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

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

        editDatabaseUrl.setText(getPreferences().getString("db-url", ""));
        editAuthUsername.setText(getPreferences().getString("db-auth-username", ""));
        checkBasicAuth.setChecked(getPreferences().getBoolean(PREF_DB_AUTH_REQUIRED, false));

        // Handle VIEW action
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            editDatabaseUrl.setText(intent.getData().toString());
            editDatabaseUrl.setEnabled(false);
            buttonOpenFile.setEnabled(false);
            launchMainActivityAfterSave = true;

            //int authMethod = preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED);
            //if (authMethod != AUTH_METHOD_UNDEFINED)
            // TODO: try open with saved keys.
        }
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
            case 1: // fingerprint
                if (!fingerprintManager.isHardwareDetected()) {
                    Toast.makeText(this,
                            R.string.no_fingerprint_detected, Toast.LENGTH_LONG).show();
                    return false;
                }
                if (!fingerprintManager.hasEnrolledFingerprints()) {
                    Toast.makeText(this,
                            R.string.no_fingerprint_enrolled, Toast.LENGTH_LONG).show();
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

    static void clearDatabaseConfigs(SharedPreferences preferences) {
        preferences.edit()
                .remove(PREF_DB_URL)
                .remove(PREF_DB_AUTH_USERNAME)
                .remove(PREF_DB_AUTH_REQUIRED)
                .remove(PREF_KEY_AUTH_METHOD)
                .apply();
    }

    private void saveDatabaseConfigs() {
        int authMethod = spinnerAuthMethod.getSelectedItemPosition();
        if (authMethod == AUTH_METHOD_SCREEN_LOCK)
            authMethod = AUTH_METHOD_FINGERPRINT;
        getPreferences().edit()
                .putString(PREF_DB_URL, editDatabaseUrl.getText().toString())
                .putString(PREF_DB_AUTH_USERNAME, editAuthUsername.getText().toString())
                .putBoolean(PREF_DB_AUTH_REQUIRED, checkBasicAuth.isChecked())
                .putInt(PREF_KEY_AUTH_METHOD, authMethod)
                .apply();

        List<String> keys = new ArrayList<>(2);
        keys.add(editMasterPassword.getText().toString());
        keys.add(editAuthPassword.getText().toString());
        saveDatabaseKeys(keys, () -> {
            setResult(RESULT_OK);
            if (launchMainActivityAfterSave)
                startActivity(new Intent(this, MainActivity.class));
            finish();
        }, error -> {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            getPreferences().edit()
                    .putInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)
                    .apply();
            KeePassStorage.set(this, null);
            cancelSubmit();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_OPEN_FILE:
                if (resultCode == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    editDatabaseUrl.setText(uri.toString());
                    checkBasicAuth.setChecked(false);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
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
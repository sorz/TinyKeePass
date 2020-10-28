package org.sorz.lab.tinykeepass

import android.content.Intent
import android.content.SharedPreferences
import android.hardware.fingerprint.FingerprintManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_database_setup.*
import org.sorz.lab.tinykeepass.keepass.KeePassStorage
import java.lang.ref.WeakReference
import java.util.*


private const val REQUEST_OPEN_FILE = 1
const val AUTH_METHOD_UNDEFINED = -1
const val AUTH_METHOD_NONE = 0
const val AUTH_METHOD_SCREEN_LOCK = 1
const val AUTH_METHOD_FINGERPRINT = 2
const val PREF_DB_URL = "db-url"
const val PREF_DB_AUTH_USERNAME = "db-auth-username"
const val PREF_DB_AUTH_REQUIRED = "db-auth-required"
const val PREF_KEY_AUTH_METHOD = "key-auth-method"

/**
 * Provide UI for configure a new db.
 *
 */
class DatabaseSetupActivity : BaseActivity() {
    private var fingerprintManager: FingerprintManager? = null
    private var launchMainActivityAfterSave = false

    private val disabledViews: MutableList<View?> = ArrayList(8)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_database_setup)
        fingerprintManager = getSystemService(FINGERPRINT_SERVICE) as FingerprintManager

        checkBasicAuth.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editAuthUsername.visibility = if (isChecked) View.VISIBLE else View.GONE
            editAuthPassword.visibility = editAuthUsername.visibility
        }
        checkShowPassword.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editMasterPassword.inputType =
                    if (isChecked) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    else InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
        }
        buttonOpenFile.setOnClickListener(View.OnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            startActivityForResult(intent, REQUEST_OPEN_FILE)
        })
        buttonConfirm.setOnClickListener(View.OnClickListener { if (isInputValid) submit() })
        editDatabaseUrl.setText(preferences.getString("db-url", ""))
        editAuthUsername.setText(preferences.getString("db-auth-username", ""))
        checkBasicAuth.isChecked = preferences.getBoolean(PREF_DB_AUTH_REQUIRED, false)

        // Handle VIEW action
        val intent = intent
        if (intent != null && intent.data != null) {
            editDatabaseUrl.setText(intent.data.toString())
            editDatabaseUrl.isEnabled = false
            buttonOpenFile.isEnabled = false
            launchMainActivityAfterSave = true

            //int authMethod = preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED);
            //if (authMethod != AUTH_METHOD_UNDEFINED)
            // TODO: try open with saved keys.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disabledViews.clear()
    }

    private val isInputValid: Boolean
        get() {
            val notEmptyInputs: MutableList<EditText?> = ArrayList(4)
            notEmptyInputs.add(editDatabaseUrl)
            notEmptyInputs.add(editMasterPassword)
            if (checkBasicAuth!!.isChecked) {
                if (!editDatabaseUrl!!.text.toString().startsWith("http")) {
                    checkBasicAuth!!.error = getString(R.string.basic_auth_with_non_http)
                    return false
                }
                notEmptyInputs.add(editAuthUsername)
                notEmptyInputs.add(editAuthPassword)
            }
            for (edit in notEmptyInputs) {
                if (edit!!.text.toString().isEmpty()) {
                    edit.error = "Cannot be empty"
                    return false
                }
            }
            when (spinnerAuthMethod!!.selectedItemPosition) {
                0 -> Unit
                1 -> {
                    if (!fingerprintManager!!.isHardwareDetected) {
                        Toast.makeText(this,
                                R.string.no_fingerprint_detected, Toast.LENGTH_LONG).show()
                        return false
                    }
                    if (!fingerprintManager!!.hasEnrolledFingerprints()) {
                        Toast.makeText(this,
                                R.string.no_fingerprint_enrolled, Toast.LENGTH_LONG).show()
                        return false
                    }
                }
            }
            return true
        }

    private fun submit() {
        val uri = Uri.parse(editDatabaseUrl!!.text.toString())
        disabledViews.clear()
        disabledViews.add(checkBasicAuth)
        disabledViews.add(editDatabaseUrl)
        disabledViews.add(editAuthUsername)
        disabledViews.add(editAuthPassword)
        disabledViews.add(editMasterPassword)
        disabledViews.add(spinnerAuthMethod)
        disabledViews.add(checkBasicAuth)
        disabledViews.add(buttonConfirm)
        for (v in disabledViews) v!!.isEnabled = false
        progressBar!!.visibility = View.VISIBLE
        var username: String? = null
        var password: String? = null
        if (checkBasicAuth!!.isChecked) {
            username = editAuthUsername!!.text.toString()
            password = editAuthPassword!!.text.toString()
        }
        val masterPwd = editMasterPassword!!.text.toString()
        FetchTask(this, uri, masterPwd, username, password).execute()
    }

    private fun cancelSubmit() {
        for (v in disabledViews) v!!.isEnabled = true
        disabledViews.clear()
        progressBar!!.visibility = View.INVISIBLE
    }

    private fun saveDatabaseConfigs() {
        var authMethod = spinnerAuthMethod!!.selectedItemPosition
        if (authMethod == AUTH_METHOD_SCREEN_LOCK) authMethod = AUTH_METHOD_FINGERPRINT
        preferences.edit()
                .putString(PREF_DB_URL, editDatabaseUrl!!.text.toString())
                .putString(PREF_DB_AUTH_USERNAME, editAuthUsername!!.text.toString())
                .putBoolean(PREF_DB_AUTH_REQUIRED, checkBasicAuth!!.isChecked)
                .putInt(PREF_KEY_AUTH_METHOD, authMethod)
                .apply()
        val keys: MutableList<String> = ArrayList(2)
        keys.add(editMasterPassword!!.text.toString())
        keys.add(editAuthPassword!!.text.toString())
        saveDatabaseKeys(keys, {
            setResult(RESULT_OK)
            if (launchMainActivityAfterSave) startActivity(Intent(this, MainActivity::class.java))
            finish()
        }) { error: String? ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            preferences.edit()
                    .putInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)
                    .apply()
            KeePassStorage.set(this, null)
            cancelSubmit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_OPEN_FILE -> if (resultCode == RESULT_OK && data != null) {
                val uri = data.data
                editDatabaseUrl!!.setText(uri.toString())
                checkBasicAuth!!.isChecked = false
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private class FetchTask internal constructor(
            activity: DatabaseSetupActivity,
            uri: Uri?,
            masterPwd: String?,
            username: String?,
            password: String?) : FetchDatabaseTask(activity, uri, masterPwd, username, password) {
        private val activity: WeakReference<DatabaseSetupActivity> = WeakReference(activity)

        override fun onPostExecute(error: String) {
            val activity = activity.get() ?: return
            if (error != null) {
                Toast.makeText(activity, error, Toast.LENGTH_SHORT).show()
                activity.cancelSubmit()
            } else {
                activity.saveDatabaseConfigs()
            }
        }

    }
}

fun clearDatabaseConfigs(preferences: SharedPreferences) {
    preferences.edit()
            .remove(PREF_DB_URL)
            .remove(PREF_DB_AUTH_USERNAME)
            .remove(PREF_DB_AUTH_REQUIRED)
            .remove(PREF_KEY_AUTH_METHOD)
            .apply()
}
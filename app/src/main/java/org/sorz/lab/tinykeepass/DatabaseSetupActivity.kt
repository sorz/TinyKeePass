package org.sorz.lab.tinykeepass

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.fingerprint.FingerprintManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.setContent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import org.sorz.lab.tinykeepass.ui.BasicAuthCfg
import org.sorz.lab.tinykeepass.ui.Setup
import java.lang.ref.WeakReference
import java.util.*


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
    private val viewModel by viewModels<SetupViewModel>()
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) viewModel.path.value = it.toString()
    }
    private val disabledViews: MutableList<View?> = ArrayList(8)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle VIEW action
        val viewActionUri = intent?.data?.also {
            viewModel.path.value = it.toString()
            viewModel.basicAuth.value = BasicAuthCfg(enabled = false)
        }
        val forViewAction = viewActionUri != null

        setContent {
            val path by viewModel.path.observeAsState("")
            val basicAuth by viewModel.basicAuth.observeAsState(BasicAuthCfg())
            val enableAuthentication by viewModel.enableAuthentication.observeAsState(false)

            Setup(
                path = path,
                onPathChange = if (forViewAction) null else viewModel.path::setValue,
                onOpenFile = if (forViewAction) null else {{ openDocument.launch(arrayOf("*/*")) }},
                basicAuthCfg = basicAuth,
                onBasicAuthCfgChange = viewModel.basicAuth::setValue,
                enableAuthentication = enableAuthentication,
                onEnabledAuthenticationChange = viewModel.enableAuthentication::setValue,
                onSubmit = { masterPassword ->
                    // TODO
                },
            )
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
                //activity.cancelSubmit()
            } else {
                //activity.saveDatabaseConfigs()
            }
        }

    }
}

class SetupViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(app)
    val path = MutableLiveData(prefs.getString(PREF_DB_URL, "") ?: "")
    val basicAuth = MutableLiveData(BasicAuthCfg(
        enabled = prefs.getBoolean(PREF_DB_AUTH_REQUIRED, false),
        username = prefs.getString(PREF_DB_AUTH_USERNAME, "") ?: "",
    ))
    val enableAuthentication = MutableLiveData(false)
}

fun clearDatabaseConfigs(preferences: SharedPreferences) {
    preferences.edit()
            .remove(PREF_DB_URL)
            .remove(PREF_DB_AUTH_USERNAME)
            .remove(PREF_DB_AUTH_REQUIRED)
            .remove(PREF_KEY_AUTH_METHOD)
            .apply()
}
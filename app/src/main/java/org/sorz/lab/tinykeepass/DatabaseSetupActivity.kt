package org.sorz.lab.tinykeepass

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.setContent
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.kunzisoft.keepass.database.exception.LoadDatabaseException
import org.sorz.lab.tinykeepass.keepass.KeePassStorage
import org.sorz.lab.tinykeepass.ui.Setup
import java.io.IOException


const val AUTH_METHOD_UNDEFINED = -1
const val AUTH_METHOD_NONE = 0
const val AUTH_METHOD_SCREEN_LOCK = 1
const val AUTH_METHOD_FINGERPRINT = 2
const val PREF_DB_URL = "db-url"
const val PREF_DB_AUTH_USERNAME = "db-auth-username"
const val PREF_DB_AUTH_REQUIRED = "db-auth-required"
const val PREF_KEY_AUTH_METHOD = "key-auth-method"

private const val TAG = "DatabaseSetupActivity"

/**
 * Provide UI for configure a new db.
 *
 */
class DatabaseSetupActivity : BaseActivity() {
    private val keyguardManager by lazy { getSystemService() as KeyguardManager? }
    private val viewModel by viewModels<SetupViewModel>()
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) viewModel.path.value = it.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authSupported = keyguardManager?.isDeviceSecure ?: false
        if (!authSupported) viewModel.enableAuth.value = false

        // Handle VIEW action
        val viewActionUri = intent?.data?.also {
            viewModel.path.value = it.toString()
            viewModel.basicAuth.value = BasicAuthCfg(enabled = false)
        }
        val forViewAction = viewActionUri != null

        // Setup UI
        setContent {
            val path by viewModel.path.observeAsState("")
            val basicAuth by viewModel.basicAuth.observeAsState(BasicAuthCfg())
            val enableAuth by viewModel.enableAuth.observeAsState(false)
            val masterPassword by viewModel.masterPassword.observeAsState("")
            val state by viewModel.state.observeAsState(SetupState.EDITING)

            Setup(
                path = path,
                onPathChange = if (forViewAction) null else viewModel.path::setValue,
                onOpenFile = if (forViewAction) null else {{ openDocument.launch(arrayOf("*/*")) }},
                basicAuthCfg = basicAuth,
                onBasicAuthCfgChange = viewModel.basicAuth::setValue,
                enableAuth = enableAuth,
                onEnabledAuthChange = if (authSupported) viewModel.enableAuth::setValue else null,
                masterPassword = masterPassword,
                onMasterPasswordChange = viewModel.masterPassword::setValue,
                onSubmit = {
                    viewModel.state.value = SetupState.VALIDATING
                    doFetchDatabase()
                },
                isInProgress = state != SetupState.EDITING,
            )
        }

        // Saving config after database fetched
        viewModel.state.observe(this) { state ->
            if (state != SetupState.DONG_WITHOUT_ERROR) return@observe
            val authMethod = if (viewModel.enableAuth.value!!) AUTH_METHOD_FINGERPRINT else 0
            val basicAuth = viewModel.basicAuth.value!!
            preferences.edit()
                    .putString(PREF_DB_URL, viewModel.path.value!!)
                    .putString(PREF_DB_AUTH_USERNAME, basicAuth.username)
                    .putBoolean(PREF_DB_AUTH_REQUIRED, basicAuth.enabled)
                    .putInt(PREF_KEY_AUTH_METHOD, authMethod)
                    .apply()
            val keys = arrayListOf(viewModel.masterPassword.value!!, basicAuth.password)
            lifecycleScope.launchWhenStarted {
                doSaveDatabaseKeys(keys, forViewAction)
            }
        }
    }

    private suspend fun doSaveDatabaseKeys(keys: List<String>, launchMainActivity: Boolean) {
        try {
            saveDatabaseKeys(keys)
        } catch (err: AuthKeyError) {
            Toast.makeText(this, err.message!!, Toast.LENGTH_SHORT).show()
            preferences.edit()
                    .putInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)
                    .apply()
            KeePassStorage.set(applicationContext, null)
            viewModel.state.value = SetupState.EDITING
            return
        }
        setResult(RESULT_OK)
        if (launchMainActivity)
            startActivity(Intent(applicationContext, MainActivity::class.java))
        finish()
    }

    private fun doFetchDatabase() = lifecycleScope.launchWhenCreated {
        val path = Uri.parse(viewModel.path.value!!)
        val masterPwd = viewModel.masterPassword.value!!
        val basicAuth = viewModel.basicAuth.value!!
        try {
            fetchDatabase(baseContext, path, masterPwd, basicAuth)
        } catch (err: IOException) {
            Log.w(TAG, "error on fetch db", err)
            val msg = getString(R.string.fail_to_sync, err.localizedMessage ?: "I/O error")
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            viewModel.state.value = SetupState.EDITING
            return@launchWhenCreated
        }  catch (err: LoadDatabaseException) {
            Log.w(TAG, "error on open db", err)
            val msg = getString(R.string.open_db_dialog_fail)
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            viewModel.state.value = SetupState.EDITING
            return@launchWhenCreated
        }
        viewModel.state.value = SetupState.DONG_WITHOUT_ERROR
    }

}

enum class SetupState {
    EDITING,
    VALIDATING,
    DONG_WITHOUT_ERROR,
}

class SetupViewModel(val app: Application) : AndroidViewModel(app) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(app)
    val path = MutableLiveData(prefs.getString(PREF_DB_URL, "") ?: "")
    val basicAuth = MutableLiveData(BasicAuthCfg(
        enabled = prefs.getBoolean(PREF_DB_AUTH_REQUIRED, false),
        username = prefs.getString(PREF_DB_AUTH_USERNAME, "") ?: "",
    ))
    val masterPassword = MutableLiveData("")
    val enableAuth = MutableLiveData(prefs.getBoolean(PREF_DB_AUTH_REQUIRED, false))
    val state = MutableLiveData(SetupState.EDITING)
}

fun clearDatabaseConfigs(preferences: SharedPreferences) {
    preferences.edit()
            .remove(PREF_DB_URL)
            .remove(PREF_DB_AUTH_USERNAME)
            .remove(PREF_DB_AUTH_REQUIRED)
            .remove(PREF_KEY_AUTH_METHOD)
            .apply()
}
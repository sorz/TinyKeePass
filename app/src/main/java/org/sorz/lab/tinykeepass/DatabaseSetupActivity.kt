package org.sorz.lab.tinykeepass

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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.jetbrains.anko.startActivityForResult
import org.sorz.lab.tinykeepass.ui.Setup
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
    private val viewModel by viewModels<SetupViewModel>()
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.path.value = it.toString()
    }

    private var fingerprintManager: FingerprintManager? = null
    private var launchMainActivityAfterSave = false

    private val disabledViews: MutableList<View?> = ArrayList(8)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val path by viewModel.path.observeAsState("")

            Setup(
                path = path,
                onPathChange = viewModel.path::setValue,
                onOpenFile = { openDocument.launch(arrayOf("*/*")) }
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

class SetupViewModel : ViewModel() {
    val path = MutableLiveData("")
}

fun clearDatabaseConfigs(preferences: SharedPreferences) {
    preferences.edit()
            .remove(PREF_DB_URL)
            .remove(PREF_DB_AUTH_USERNAME)
            .remove(PREF_DB_AUTH_REQUIRED)
            .remove(PREF_KEY_AUTH_METHOD)
            .apply()
}
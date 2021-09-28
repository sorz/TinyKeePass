package org.sorz.lab.tinykeepass.ui

import android.util.Log
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.auth.SecureStorage
import org.sorz.lab.tinykeepass.auth.SystemException
import org.sorz.lab.tinykeepass.auth.UserAuthException
import org.sorz.lab.tinykeepass.keepass.DatabaseState
import org.sorz.lab.tinykeepass.keepass.RemoteKeePass
import org.sorz.lab.tinykeepass.keepass.Repository
import java.io.IOException

private const val TAG = "SyncDatabase"

@Composable
fun SyncDatabaseFloatingActionButton(
    repo: Repository,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dbState by repo.databaseState.collectAsState()
    var isSyncing by remember { mutableStateOf(false) }
    var isShowingSnackbar by remember { mutableStateOf(false) }

    suspend fun syncDatabase() {
        suspend fun onError(err: String) {
            isShowingSnackbar = true
            val result = snackbarHostState.showSnackbar(err, context.getString(R.string.retry))
            if (result == SnackbarResult.ActionPerformed) syncDatabase()
            isShowingSnackbar = false
        }
        try {
            val pref = SecureStorage(context).run {
                getEncryptedPreferences(getExistingMasterKey())
            }
            val remoteDb = RemoteKeePass.loadFromPrefs(pref)
                ?: return onError(context.getString(R.string.no_remote_db))
            repo.syncDatabase(remoteDb)
        } catch (err: SystemException) {
            Log.e(TAG, "fail to get master key", err) // FIXME: proper error message
            return onError(context.getString(R.string.error_get_master_key, err.toString()))
        } catch (err: UserAuthException) {
            Log.e(TAG, "user auth fail", err) // FIXME: proper error message
            return onError(err.message ?: err.toString())
        } catch (err: IOException) {
            Log.e(TAG, "io error", err) // FIXME: proper error message
            return onError(err.message ?: err.toString())
        }
        isShowingSnackbar = true
        snackbarHostState.showSnackbar(context.getString(R.string.sync_done))
        isShowingSnackbar = false
    }

    if ((isSyncing || dbState == DatabaseState.UNLOCKED) && !isShowingSnackbar)
        FloatingActionButton(
            onClick = {
                if (!isSyncing) {
                    isSyncing = true
                    scope.launch { syncDatabase() }.invokeOnCompletion {
                        isSyncing = false
                    }
                }
            }
        ) {
            if (isSyncing)
                CircularProgressIndicator()
            else
                Icon(Icons.Filled.CloudDownload, stringResource(R.string.action_sync))
        }

}
package org.sorz.lab.tinykeepass.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.auth.SecureStorage
import org.sorz.lab.tinykeepass.auth.SystemException
import org.sorz.lab.tinykeepass.auth.UserAuthException
import org.sorz.lab.tinykeepass.keepass.DatabaseState
import org.sorz.lab.tinykeepass.keepass.LocalKeePass
import org.sorz.lab.tinykeepass.keepass.RealRepository
import org.sorz.lab.tinykeepass.keepass.Repository

private const val TAG = "LockScreen"


@Preview(showSystemUi = true)
@Composable
private fun LockScreenPreview() {
    LockScreen(RealRepository(LocalContext.current))
}

@Composable
fun LockScreen(
    repo: Repository,
    nav: NavController? = null,
    snackbarHostState: SnackbarHostState? = null,
    ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbState by repo.databaseState.collectAsState()
    var unlocking by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }

    val waiting = unlocking || resetting

    Log.d(TAG, "dbState $dbState unlocking $unlocking")

    LaunchedEffect(dbState, unlocking) {
        Log.d(TAG, "!!!!checking $dbState ${!unlocking}")
        if (dbState == DatabaseState.UNLOCKED && !unlocking && nav != null) {
            Log.d(TAG, "!!!Lock database")
            //repo.lockDatabase()
        }
    }

    fun showError(msg: String) {
        scope.launch {
            snackbarHostState?.showSnackbar(msg)
        }
    }

    fun unlock() = scope.launch {
        unlocking = true
        val prefs = try {
            Log.d(TAG, "Get encrypted prefs")
            SecureStorage(context).run {
                getEncryptedPreferences(getExistingMasterKey())
            }
        } catch (err: SystemException) {
            Log.e(TAG, "fail to get master key", err) // FIXME: proper error message
            return@launch showError(context.getString(R.string.error_get_master_key, err.toString()))
        } catch (err: UserAuthException) {
            Log.e(TAG, "user auth fail", err) // FIXME: proper error message
            return@launch showError(err.message ?: err.toString())
        }
        val local = LocalKeePass.loadFromPrefs(prefs) ?: return@launch showError(context.getString(R.string.no_master_key))
        repo.unlockDatabase(local)
        nav?.let { NavActions(it).list() }
    }.invokeOnCompletion {
        unlocking = false
    }

    fun reset() = scope.launch {
        resetting = true
        repo.clearDatabase()
    }.invokeOnCompletion {
        resetting = false
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.db_is_locked_or_unconfigured),
            style = MaterialTheme.typography.subtitle1
        )
        Spacer(Modifier.height(32.dp))
        val buttonModifier = Modifier.defaultMinSize(200.dp)
        val configured = dbState != DatabaseState.UNCONFIGURED
        OutlinedButton(
            onClick = { unlock() },
            enabled = configured && !waiting,
            modifier = buttonModifier,
        ) {
            Text(stringResource(R.string.unlock))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { nav?.let { NavActions(it).setup() } },
            enabled = !configured && !waiting,
            modifier = buttonModifier,
        ) {
            Text(stringResource(R.string.configure))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { reset() },
            enabled = configured && !waiting,
            modifier = buttonModifier,
        ) {
            Text(stringResource(R.string.clean_config))
        }
        if (waiting) {
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator()
        }
    }
}
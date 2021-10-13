package org.sorz.lab.tinykeepass.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.auth.SecureStorage
import org.sorz.lab.tinykeepass.auth.SystemException
import org.sorz.lab.tinykeepass.auth.UserAuthException
import org.sorz.lab.tinykeepass.keepass.*
import org.sorz.lab.tinykeepass.settingsDataStore
import java.io.IOException
import java.util.*

private const val TAG = "SetupScreen"

@Preview(showSystemUi = true)
@Composable
private fun SetupScreenPreview() {
    SetupScreen(RealRepository(LocalContext.current))
}

@Composable
fun SetupScreen(
    repo: Repository,
    nav: NavController? = null,
    uri: Uri? = null,
) {
    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.screen_title_setup))
                },
            )
        },
    ) {
        Content(repo, nav, scaffoldState.snackbarHostState, uri)
    }
}

@Composable
private fun Content(
    repo: Repository,
    nav: NavController? = null,
    snackbarHostState: SnackbarHostState? = null,
    uri: Uri? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedSettings by context.settingsDataStore.data.collectAsState(null)
    var databaseUrl by rememberSaveable { mutableStateOf("") }
    var httpAuthRequired by rememberSaveable { mutableStateOf(false) }
    var httpAuthUsername by rememberSaveable { mutableStateOf("") }
    var httpAuthPassword  by remember { mutableStateOf("") }  // Do not save password
    var masterPassword by remember { mutableStateOf("") }
    var userAuthRequired by rememberSaveable { mutableStateOf(false) }
    var selectedFileUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var confirmClicked by rememberSaveable { mutableStateOf(false) }
    var isSettingUp by remember { mutableStateOf(false) }

    val userAuthIsAvailable = remember {
        BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { selectedFileUri = it }
    val typedDatabaseUri = Uri.parse(databaseUrl)
    val isOverHttp = selectedFileUri == null
            && typedDatabaseUri.scheme?.lowercase()?.matches(Regex("https?")) == true

    // Restore saved config
    LaunchedEffect(savedSettings) {
        savedSettings?.let { cfg ->
            if (databaseUrl.isEmpty() && selectedFileUri == null) {
                if (isOverHttp) databaseUrl = cfg.databaseUri
                else selectedFileUri = Uri.parse(cfg.databaseUri)
            }
            if (httpAuthUsername.isEmpty()) httpAuthUsername = cfg.httpAuthUsername
        }
    }

    // Download database & save config
    suspend fun setup(remoteDb: RemoteKeePass, onError: (msg: String) -> Unit) {
        // Try download & open the database
        try {
            Log.d(TAG, "Syncing database")
            repo.syncDatabase(remoteDb)
        } catch (err: IOException) {
            Log.w(TAG, "fail to setup database", err)
            val msg = err.cause?.localizedMessage // FIXME: proper error message
                ?: err.cause?.toString()
                ?: err.localizedMessage
                ?: err.toString()
            return onError(context.getString(R.string.fail_to_sync, msg))
        }
        // Save plaintext config
        Log.d(TAG, "Save plaintext config")
        context.settingsDataStore.updateData { current ->
            current.toBuilder()
                .setDatabaseUri(remoteDb.uri.toString())
                .setHttpAuthUsername(remoteDb.httpAuth?.username ?: "")
                .setUserAuthRequired(userAuthRequired && userAuthIsAvailable)
                .build()
        }
        // Save encrypted config
        val prefs = try {
            Log.d(TAG, "Get encrypted prefs")
            SecureStorage(context).run {
                getEncryptedPreferences(generateNewMasterKey())
            }
        } catch (err: SystemException) {
            Log.e(TAG, "fail to get master key", err) // FIXME: proper error message
            return onError(context.getString(R.string.error_get_master_key, err.toString()))
        } catch (err: UserAuthException) {
            Log.e(TAG, "user auth fail", err) // FIXME: proper error message
            return onError(err.message ?: err.toString())
        }
        Log.d(TAG, "Save encrypted config")
        remoteDb.writeToPrefs(prefs)
        LocalKeePass(remoteDb.masterPassword).writeToPrefs(prefs)
        nav?.let { NavActions(it).list() }
    }

    // Validate input before trigger saving
    fun submit() {
        confirmClicked = true
        val validatedUri = uri ?: selectedFileUri ?: typedDatabaseUri?.takeIf { isOverHttp } ?: return
        if (masterPassword.isEmpty()) return
        val httpAuth = if (isOverHttp && httpAuthRequired) {
            if (httpAuthUsername.isEmpty() || httpAuthPassword.isEmpty()
                || !isValidHttpBasicAuthValue(httpAuthUsername)
                || !isValidHttpBasicAuthValue(httpAuthPassword)
            ) return
            HttpAuth(httpAuthUsername, httpAuthPassword)
        } else null
        isSettingUp = true
        val remoteDb = RemoteKeePass(validatedUri, masterPassword, httpAuth)
        scope.launch {
            setup(remoteDb) { err ->
                scope.launch {
                    val result = snackbarHostState?.showSnackbar(err, context.getString(R.string.retry))
                    if (result == SnackbarResult.ActionPerformed) submit()
                }
            }
        }.invokeOnCompletion {
            isSettingUp = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        // Database URL
        OutlinedTextField(
            value = uri?.toString() ?: selectedFileUri?.toString() ?: databaseUrl,
            onValueChange = {
                if (selectedFileUri != null) {
                    selectedFileUri = null
                    if (!it.startsWith("http", true)) {
                        databaseUrl = ""
                        return@OutlinedTextField
                    }
                }
                databaseUrl = it
            },
            enabled = !isSettingUp && uri == null,
            placeholder = { Text(stringResource(R.string.database_url)) },
            label = { Text(stringResource(R.string.database)) },
            isError = confirmClicked && selectedFileUri == null && !isOverHttp,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { openFileLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FolderOpen, stringResource(R.string.open_file))
                }
            },
        )

        // HTTP basic auth
        Spacer(modifier = Modifier.height(24.dp))
        CheckboxWithLabel(
            enabled = !isSettingUp && isOverHttp,
            checked = isOverHttp && httpAuthRequired,
            onCheckedChange = { httpAuthRequired = it },
            label = R.string.require_http_auth,
        )
        if (httpAuthRequired) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = httpAuthUsername,
                    onValueChange = { httpAuthUsername = it },
                    isError =
                        (!isValidHttpBasicAuthValue(httpAuthUsername)) ||
                        (confirmClicked && httpAuthUsername.isEmpty()),
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    enabled = !isSettingUp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PasswordTextField(
                    value = httpAuthPassword,
                    onValueChange = { httpAuthPassword = it },
                    isError =
                        (!isValidHttpBasicAuthValue(httpAuthPassword)) ||
                        (confirmClicked && httpAuthPassword.isEmpty()),
                    label = R.string.password,
                    enabled = !isSettingUp,
                )
            }
        }

        // Master password
        Spacer(modifier = Modifier.height(24.dp))
        PasswordTextField(
            value = masterPassword,
            onValueChange = { masterPassword = it },
            label = R.string.master_password,
            modifier = Modifier.fillMaxWidth(),
            isError = confirmClicked && masterPassword.isEmpty(),
            enabled = !isSettingUp,
        )

        // User auth option
        Spacer(modifier = Modifier.height(8.dp))
        CheckboxWithLabel(
            checked = userAuthRequired && userAuthIsAvailable,
            onCheckedChange = { userAuthRequired = it },
            label = R.string.require_user_auth,
            enabled = !isSettingUp && userAuthIsAvailable,
        )

        // Confirm
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSettingUp)
                CircularProgressIndicator()
            Spacer(Modifier.width(16.dp))
            Button(onClick = { submit() }, enabled = !isSettingUp) {
                Text(stringResource(R.string.button_confirm))
            }
        }
    }
}

@Composable
private fun CheckboxWithLabel(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
    @StringRes label: Int,
    enabled: Boolean = true,
) {
    val modifier = Modifier.clickable {
        onCheckedChange(!checked)
    }
    Row(modifier = if (enabled) { modifier } else { Modifier }) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(start = 4.dp),
            color =
                if (enabled) Color.Unspecified
                else MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes label: Int,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    var showPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation =
            if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = {showPassword = !showPassword}) {
                Icon(
                    if (showPassword) Icons.Default.Visibility
                    else Icons.Default.VisibilityOff,
                    stringResource(R.string.show)
                )
            }
        },
        modifier = modifier,
        enabled = enabled,
    )
}


private fun isValidHttpBasicAuthValue(username: String): Boolean =
    username.all { c -> c != ':' || !c.isISOControl() }
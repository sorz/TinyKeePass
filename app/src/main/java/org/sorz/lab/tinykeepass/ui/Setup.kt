package org.sorz.lab.tinykeepass.ui

import android.net.Uri
import androidx.compose.foundation.ScrollableColumn
import androidx.compose.foundation.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.runtime.savedinstancestate.savedInstanceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Preview
import org.sorz.lab.tinykeepass.BasicAuthCfg
import org.sorz.lab.tinykeepass.R



@Preview
@Composable
private fun PreviewSetup() {
    Setup("https://example", {}, {},  BasicAuthCfg(), {}, false, {},
            "", {}, {})
}

@Composable
fun Setup (
    path: String,
    onPathChange: ((path: String) -> Unit)?,
    onOpenFile: (() -> Unit)?,
    basicAuthCfg: BasicAuthCfg,
    onBasicAuthCfgChange: (cfg: BasicAuthCfg) -> Unit,
    enableAuth: Boolean,
    onEnabledAuthChange: ((enabled: Boolean) -> Unit)?,
    masterPassword: String,
    onMasterPasswordChange: (password: String) -> Unit,
    onSubmit: () -> Unit,
    isInProgress: Boolean = false,
) {
    val res = ContextAmbient.current.resources
    val isHttpOrHttps = path.matches("^https?://.*".toRegex(RegexOption.IGNORE_CASE))
    val isValid =
        path.isValidPath() &&
        basicAuthCfg.isValid &&
        masterPassword != ""

    TinyTheme {
        if (isInProgress) {
            val filename = Uri.parse(path).lastPathSegment ?: path
            InProgress(filename)
        } else {
            ScrollableColumn(
                modifier = Modifier.padding(16.dp).fillMaxSize()
            ) {
                FileSelection(path, onPathChange, onOpenFile)
                Spacer(Modifier.height(16.dp))
                BasicAuth(isHttpOrHttps, basicAuthCfg, onBasicAuthCfgChange)
                Spacer(Modifier.height(24.dp))
                MasterPasswordInput(masterPassword, onMasterPasswordChange)
                Spacer(Modifier.height(16.dp))
                AuthenticationSwitch(enableAuth, onEnabledAuthChange)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.align(Alignment.End),
                    enabled = isValid,
                ) {
                    Text(
                        text = res.getString(R.string.button_confirm),
                        style = MaterialTheme.typography.button,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun InProgress(
    filename: String = "database.kdbx"
) {
    val res = ContextAmbient.current.resources

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    ) {
        Text(
            text = filename,
            style = MaterialTheme.typography.subtitle1
        )
        Spacer(Modifier.height(24.dp))

        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))

        Text(
            text = res.getString(R.string.waiting),
            style = MaterialTheme.typography.body1,
        )
    }
}

@Composable
private fun FileSelection(
    path: String,
    onPathChange: ((path: String) -> Unit)?,
    onOpenFile: (() -> Unit)?,
) {
    val res = ContextAmbient.current.resources
    TextField(
        path, { if (!it.contains('\n')) onPathChange?.invoke(it) },
        isErrorValue = !path.isValidPath(),
        placeholder = { Text(res.getString(R.string.database_url)) },
        keyboardType = KeyboardType.Uri,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (onOpenFile != null) {
                OutlinedButton(onClick = onOpenFile) {
                    Text(
                        text = res.getString(R.string.open_file),
                        style = MaterialTheme.typography.button,
                    )
                }
            }
        },
    )
}

@Composable
private fun BasicAuth(
    enabled: Boolean,
    cfg: BasicAuthCfg,
    onCfgChange: (cfg: BasicAuthCfg) -> Unit,
) {
    val res = ContextAmbient.current.resources
    var showPassword by savedInstanceState { true }

    Column {
        // Switch
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = res.getString(R.string.require_http_auth),
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onBackground,
            )
            Switch(
                enabled = enabled,
                checked = enabled && cfg.enabled,
                onCheckedChange = { onCfgChange(cfg.copy(enabled = it)) },
            )
        }
        // Fields
        if (enabled && cfg.enabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cfg.username,
                isErrorValue = cfg.username == "",
                onValueChange = { onCfgChange(cfg.copy(username = it))},
                placeholder = { Text(res.getString(R.string.username)) },
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cfg.password,
                isErrorValue = cfg.password == "",
                onValueChange = { onCfgChange(cfg.copy(password = it)) },
                placeholder = { Text(res.getString(R.string.password)) },
                visualTransformation =
                    if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                modifier = Modifier.align(Alignment.End),
                trailingIcon = {
                    ShowPasswordIcon(showPassword) { showPassword = it }
                },
            )
        }
    }
}

@Composable
private fun MasterPasswordInput(
    password: String,
    onPasswordChange: (password: String) -> Unit,
) {
    val res = ContextAmbient.current.resources
    var showPassword by savedInstanceState { true }

    TextField(
        value = password,
        isErrorValue = password == "",
        onValueChange = onPasswordChange,
        placeholder = { Text(res.getString(R.string.master_password)) },
        visualTransformation =
            if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
        keyboardType = KeyboardType.Password,
        trailingIcon = {
            ShowPasswordIcon(showPassword) { showPassword = it }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthenticationSwitch(
    enabled: Boolean,
    onEnableChange: ((enabled: Boolean) -> Unit)?,
) {
    val res = ContextAmbient.current.resources
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = res.getString(R.string.enable_authentication),
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onBackground,
        )
        Switch(
            checked = enabled,
            onCheckedChange = { onEnableChange?.invoke(it) },
            enabled = onEnableChange != null,
        )
    }
}

@Composable
private fun ShowPasswordIcon(
    show: Boolean,
    onShowChange: (show: Boolean) -> Unit
) {
    Icon(
        asset =
            if (show) Icons.Filled.Visibility
            else Icons.Filled.VisibilityOff,
        modifier = Modifier.clickable {
            onShowChange(!show)
        }
    )
}

private fun String.isValidPath(): Boolean =
    matches("^(http|https|content)://.+".toRegex())
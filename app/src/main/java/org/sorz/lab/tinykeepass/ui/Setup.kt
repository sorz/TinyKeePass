package org.sorz.lab.tinykeepass.ui

import androidx.compose.foundation.AmbientTextStyle
import androidx.compose.foundation.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.savedinstancestate.savedInstanceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Preview
import org.sorz.lab.tinykeepass.R



@Preview
@Composable
private fun PreviewSetup() {
    Setup("https://example", {}, {}, BasicAuthCfg(true), {}, "", {},
        false, {})
}


data class BasicAuthCfg(
    val enabled: Boolean = false,
    val username: String = "",
    val password: String = "",
)

@Composable
fun Setup (
    path: String,
    onPathChange: (path: String) -> Unit,
    onOpenFile: () -> Unit,
    basicAuthCfg: BasicAuthCfg,
    onBasicAuthCfgChange: (cfg: BasicAuthCfg) -> Unit,
    masterPassword: String,
    onMasterPasswordChange: (password: String) -> Unit,
    enableAuthentication: Boolean,
    onEnabledAuthenticationChange: (enabled: Boolean) -> Unit,
) {
    val ctx = ContextAmbient.current
    val res = ctx.resources
    val isHttpOrHttps =
            path.startsWith("http://") || path.startsWith("https://")
    TinyTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            FileSelection(path, onPathChange, onOpenFile)
            Spacer(Modifier.height(16.dp))
            BasicAuth(isHttpOrHttps, basicAuthCfg, onBasicAuthCfgChange)
            Spacer(Modifier.height(24.dp))
            MasterPasswordInput(masterPassword, onMasterPasswordChange)
            Spacer(Modifier.height(16.dp))
            AuthenticationSwitch(enableAuthentication, onEnabledAuthenticationChange)
        }
    }
}

@Composable
private fun FileSelection(
    path: String,
    onPathChange: (path: String) -> Unit,
    onOpenFile: () -> Unit,
) {
    val res = ContextAmbient.current.resources
    TextField(
        path, { if (!it.contains('\n')) onPathChange(it) },
        placeholder = { Text(res.getString(R.string.database_url)) },
        keyboardType = KeyboardType.Uri,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            OutlinedButton(onClick = onOpenFile) {
                Text(res.getString(R.string.open_file))
            }
        }
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
            Text(res.getString(R.string.require_http_auth))
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
                onValueChange = { onCfgChange(cfg.copy(username = it))},
                placeholder = { Text(res.getString(R.string.username)) },
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cfg.password,
                onValueChange = { onCfgChange(cfg.copy(password = it)) },
                placeholder = { Text(res.getString(R.string.password)) },
                visualTransformation =
                    if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                modifier = Modifier.align(Alignment.End),
                trailingIcon = {
                    ShowPasswordIcon(showPassword) { showPassword = it }
                }
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

    OutlinedTextField(
        value = password,
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
    onEnableChange: (enabled: Boolean) -> Unit,
) {
    val res = ContextAmbient.current.resources
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(res.getString(R.string.enable_authentication))
        Switch(
            checked = enabled,
            onCheckedChange = onEnableChange,
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

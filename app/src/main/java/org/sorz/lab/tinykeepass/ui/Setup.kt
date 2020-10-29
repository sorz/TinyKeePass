package org.sorz.lab.tinykeepass.ui

import androidx.compose.foundation.AmbientTextStyle
import androidx.compose.foundation.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Preview
import org.sorz.lab.tinykeepass.R



@Preview
@Composable
private fun PreviewSetup() {
    Setup("https://example", {}, {}, BasicAuthCfg(true), {})
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
                onValueChange = { onCfgChange(cfg.copy(password = it))},
                placeholder = { Text(res.getString(R.string.password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
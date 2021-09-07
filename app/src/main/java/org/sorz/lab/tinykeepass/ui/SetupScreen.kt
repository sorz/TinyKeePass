package org.sorz.lab.tinykeepass.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.sorz.lab.tinykeepass.keepass.DummyRepository
import org.sorz.lab.tinykeepass.keepass.Repository
import org.sorz.lab.tinykeepass.R

@Preview(showSystemUi = true)
@Composable
private fun SetupScreenPreview() {
    SetupScreen(DummyRepository)
}

@Composable
fun SetupScreen(
    repo: Repository,
    nav: NavController? = null,
) {
    var databaseUrl by rememberSaveable { mutableStateOf("") }
    var httpAuthRequired by rememberSaveable { mutableStateOf(true) }
    var httpAuthUsername by rememberSaveable { mutableStateOf("") }
    var httpAuthPassword  by rememberSaveable { mutableStateOf("") }
    var masterPassword by rememberSaveable { mutableStateOf("") }
    var userAuthRequired by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
    ) {
        // Database URL
        OutlinedTextField(
            value = databaseUrl,
            onValueChange = { databaseUrl = it },
            placeholder = { Text(stringResource(R.string.database_url)) },
            label = { Text(stringResource(R.string.database)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { /*TODO*/ }) {
                    Icon(Icons.Default.FolderOpen, stringResource(R.string.open_file))
                }
            },
        )

        // HTTP basic auth
        Spacer(modifier = Modifier.height(24.dp))
        CheckboxWithLabel(
            checked = httpAuthRequired,
            onCheckedChange = { httpAuthRequired = it },
            label = R.string.require_http_auth,
        )
        if (httpAuthRequired) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = httpAuthUsername,
                    onValueChange = { httpAuthUsername = it },
                    isError = !isValidHttpBasicAuthValue(httpAuthUsername),
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PasswordTextField(
                    value = httpAuthPassword,
                    onValueChange = { httpAuthPassword = it },
                    isError = !isValidHttpBasicAuthValue(httpAuthPassword),
                    label = R.string.password,
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
        )

        // User auth option
        Spacer(modifier = Modifier.height(8.dp))
        CheckboxWithLabel(
            checked = userAuthRequired,
            onCheckedChange = { userAuthRequired = it },
            label = R.string.require_user_auth,
        )
    }
}

@Composable
private fun CheckboxWithLabel(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
    @StringRes label: Int,
) {
    Row(modifier = Modifier.clickable {
        onCheckedChange(!checked)
    }) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(start = 8.dp)
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
) {
    var showPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        label = { Text(stringResource(label)) },
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
    )
}


private fun isValidHttpBasicAuthValue(username: String): Boolean =
    username.all { c -> c != ':' || !c.isISOControl() }
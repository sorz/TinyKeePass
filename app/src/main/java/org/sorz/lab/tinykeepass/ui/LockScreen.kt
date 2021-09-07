package org.sorz.lab.tinykeepass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.keepass.DatabaseState
import org.sorz.lab.tinykeepass.keepass.DummyRepository
import org.sorz.lab.tinykeepass.keepass.Repository

@Preview(showSystemUi = true)
@Composable
private fun LockScreenPreview() {
    LockScreen(DummyRepository)
}

@Composable
fun LockScreen(
    repo: Repository,
    nav: NavController? = null,
) {
    val dbState by repo.databaseState.collectAsState()
    var unlocking by rememberSaveable { mutableStateOf(false) }

    if (dbState == DatabaseState.UNLOCKED && nav != null) {
        NavActions(nav).list()
    }

    LaunchedEffect(unlocking) {
        if (!unlocking) return@LaunchedEffect
        repo.unlockDatabase()
        unlocking = false
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
            onClick = { unlocking = true },
            enabled = configured,
            modifier = buttonModifier,
        ) {
            Text(stringResource(R.string.unlock))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {},
            enabled = !configured,
            modifier = buttonModifier,
        ) {
            Text(stringResource(R.string.configure))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {},
            enabled = configured,
            modifier = buttonModifier,
        ) {
            Text(stringResource(R.string.clean_config))
        }

    }
}
package org.sorz.lab.tinykeepass.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.composethemeadapter.MdcTheme
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.keepass.DatabaseState
import org.sorz.lab.tinykeepass.keepass.Repository

@Composable
fun AutofillAuthScreen(repo: Repository) {
    val state by repo.databaseState.collectAsState()

    MdcTheme {
        when (state) {
            DatabaseState.UNLOCKED -> Unit
            DatabaseState.UNCONFIGURED -> Unit
            DatabaseState.LOCKED -> UnlockingDialog()
        }
    }
}

@Composable
private fun UnlockingDialog() {
    AlertDialog(
        onDismissRequest = {},
        buttons = {},
        title = {
            Text(stringResource(R.string.open_db_dialog_title))
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_db_dialog_note))
            }
        }
    )
}
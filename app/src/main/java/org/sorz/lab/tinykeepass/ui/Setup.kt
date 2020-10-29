package org.sorz.lab.tinykeepass.ui

import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.OutlinedButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Preview
import org.sorz.lab.tinykeepass.R



@Preview
@Composable
private fun PreviewSetup() {
    Setup(
        path = "",
        onPathChange = {},
        onOpenFile = {},
    )
}

@Composable
fun Setup (
    path: String,
    onPathChange: (path: String) -> Unit,
    onOpenFile: () -> Unit,
) {
    val ctx = ContextAmbient.current
    val res = ctx.resources
    TinyTheme {
        Column(
                modifier = Modifier.padding(16.dp)
        ) {
            FileSelection(path, onPathChange, onOpenFile)
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            path, { if (!it.contains('\n')) onPathChange(it) },
            placeholder = { res.getString(R.string.database_url) },
            modifier = Modifier.padding(end = 16.dp)
        )
        OutlinedButton(
            onClick = onOpenFile,
        ) {
            Text(res.getString(R.string.open_file))
        }
    }
}
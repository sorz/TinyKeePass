package org.sorz.lab.tinykeepass.ui

import android.content.res.Resources
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.savedinstancestate.savedInstanceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Preview
import org.sorz.lab.tinykeepass.R


@Preview
@Composable
fun Setup () {
    val ctx = ContextAmbient.current
    val res = ctx.resources
    TinyTheme {
        Column(
                modifier = Modifier.padding(16.dp)
        ) {
            FileSelection(res)
        }
    }
}

@Composable
private fun FileSelection(res: Resources) {
    var path by savedInstanceState { "" }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            path, { if (!it.contains('\n')) path = it },
            Modifier.padding(end = 16.dp),
            placeholder = { Text(res.getString(R.string.database_url)) }
        )
        OutlinedButton(
            onClick = {},
            Modifier.wrapContentWidth()
        ) {
            Text(res.getString(R.string.open_file))
        }
    }
}
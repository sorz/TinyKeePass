package org.sorz.lab.tinykeepass.ui

import android.util.Log
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import org.sorz.lab.tinykeepass.keepass.DatabaseState
import org.sorz.lab.tinykeepass.keepass.RealRepository
import org.sorz.lab.tinykeepass.keepass.Repository

private const val TAG = "ListScreen"

@Preview(showSystemUi = true)
@Composable
private fun ListScreenPreview() {
    ListScreen(RealRepository(LocalContext.current))
}

@Composable
fun ListScreen(
    repo: Repository,
    nav: NavController? = null,
) {
    val dbState by repo.databaseState.collectAsState()

    Log.d(TAG, "dbState $dbState")

    if (dbState != DatabaseState.UNLOCKED && nav != null) {
        NavActions(nav).locked()
    }

    Text("TODO: List screen")

}

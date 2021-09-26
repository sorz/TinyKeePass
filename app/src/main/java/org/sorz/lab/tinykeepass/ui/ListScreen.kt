package org.sorz.lab.tinykeepass.ui

import android.util.Log
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.icons.IconDrawableFactory
import kotlinx.coroutines.launch
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.auth.SecureStorage
import org.sorz.lab.tinykeepass.auth.SystemException
import org.sorz.lab.tinykeepass.auth.UserAuthException
import org.sorz.lab.tinykeepass.keepass.*
import java.io.IOException

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
    scaffoldState: ScaffoldState? = null,
    floatingActionButton: MutableState<@Composable () -> Unit>? = null,
    ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbState by repo.databaseState.collectAsState()

    if (dbState != DatabaseState.UNLOCKED && nav != null) {
        NavActions(nav).locked()
    }

    fun copyEntry(entry: Entry) {
        if (entry.username == "") {
            // No username, copy password immediately
            entry.copyPassword(context)
        } else {
            // Copy username and show the notification to copying password
            entry.copyUsername(context)
            entry.copyPasswordPostponed(context)
            scope.launch {
                val action = scaffoldState?.snackbarHostState?.showSnackbar(
                    context.getString(R.string.username_copied, entry.username),
                    context.getString(R.string.copy_password),
                    SnackbarDuration.Long,
                )
                if (action == SnackbarResult.ActionPerformed) {
                    entry.copyPassword(context)
                }
            }
        }
    }

    EntryList(
        repo = repo,
        onClick = { copyEntry(it) },
        onClickLabel = stringResource(R.string.click_label_copy_password),
        scaffoldState = scaffoldState,
        floatingActionButton = floatingActionButton,
    ) { entry ->
        Text(
            text = entry.password,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily(
                Font(R.font.fira_mono_regular)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp, 16.dp),
            fontSize = 24.sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryList(
    repo: Repository,
    onClick: (entry: Entry) -> Unit,
    onClickLabel: String? = null,
    scaffoldState: ScaffoldState? = null,
    floatingActionButton: MutableState<@Composable () -> Unit>? = null,
    expanded: (@Composable ColumnScope.(Entry) -> Unit)? = null,
) {
    val entries by repo.databaseEntries.collectAsState()
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    val iconFactory = remember { repo.iconFactory }

    if (scaffoldState != null && floatingActionButton != null)
        SyncDatabaseFloatingActionButton(repo, scaffoldState, floatingActionButton)

    LazyColumn(
        Modifier.fillMaxWidth()
    ) {
        items(
            items = entries,
            key = { it.nodeId }
        ) { entry ->
            Column(Modifier.fillMaxWidth()) {
                // Item
                Surface(
                    modifier = Modifier
                        .combinedClickable(
                            onClickLabel = onClickLabel,
                            onLongClickLabel = stringResource(R.string.click_label_select_entry),
                            onClick = {
                                if (selectedEntry != null) {
                                    selectedEntry = null
                                } else {
                                    onClick(entry)
                                }
                            },
                            onLongClick = { selectedEntry = entry }.takeIf { expanded != null },
                        )
                        .padding(vertical = 12.dp)
                ) {
                    Column {
                        EntryListItem(iconFactory, entry)
                        expanded?.takeIf { selectedEntry == entry }?.let { content ->
                            content(entry)
                        }
                    }
                }
                // Divider
                if (entry != entries.last()) {
                    Divider(Modifier.padding(start=36.dp))
                }

            }
        }
    }
}


@Composable
private fun EntryListItem(iconFactory: IconDrawableFactory, entry: Entry) {
    val context = LocalContext.current
    val iconDrawable = iconFactory.getIconSuperDrawable(context, entry.icon, 24).drawable

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Icon
        Spacer(Modifier.width(12.dp))
        AndroidView(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(16.dp),
            factory = {
                ImageView(context).apply {
                    setImageDrawable(iconDrawable)
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Column {
            // Title
            Text(
                text = entry.title,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Second line
            Row {
                // Username
                if (entry.username != "") {
                    Text(
                        text = entry.username,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                // URL
                if (entry.url != "") {
                    val color = colorResource(android.R.color.darker_gray)
                    Text(
                        text = entry.urlHostname,
                        maxLines = 1,
                        color = color,
                    )
                    entry.urlPath?.let { path ->
                        Text(
                            text = path,
                            color = color.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncDatabaseFloatingActionButton(
    repo: Repository,
    scaffoldState: ScaffoldState,
    floatingActionButton: MutableState<@Composable () -> Unit>,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSyncing by remember { mutableStateOf(false) }

    suspend fun syncDatabase() {
        suspend fun onError(err: String) {
            val result = scaffoldState.snackbarHostState
                .showSnackbar(err, context.getString(R.string.retry))
            if (result == SnackbarResult.ActionPerformed) syncDatabase()
        }
        try {
            val pref = SecureStorage(context).run {
                getEncryptedPreferences(getExistingMasterKey())
            }
            val remoteDb = RemoteKeePass.loadFromPrefs(pref)
                ?: return onError(context.getString(R.string.no_remote_db))
            repo.syncDatabase(remoteDb)
        } catch (err: SystemException) {
            Log.e(TAG, "fail to get master key", err) // FIXME: proper error message
            return onError(context.getString(R.string.error_get_master_key, err.toString()))
        } catch (err: UserAuthException) {
            Log.e(TAG, "user auth fail", err) // FIXME: proper error message
            return onError(err.message ?: err.toString())
        } catch (err: IOException) {
            Log.e(TAG, "io error", err) // FIXME: proper error message
            return onError(err.message ?: err.toString())
        }
        scaffoldState.snackbarHostState.showSnackbar(context.getString(R.string.sync_done))
    }

    floatingActionButton.value = {
        if (!isSyncing)
            FloatingActionButton(
                onClick = {
                    isSyncing = true
                    scope.launch { syncDatabase() }.invokeOnCompletion {
                        isSyncing = false
                    }
                }
            ) {
                Icon(Icons.Filled.CloudDownload, stringResource(R.string.action_sync))
            }
    }

}
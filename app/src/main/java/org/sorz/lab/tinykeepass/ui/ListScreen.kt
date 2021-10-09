package org.sorz.lab.tinykeepass.ui

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import org.sorz.lab.tinykeepass.keepass.*

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
    val scaffoldState = rememberScaffoldState()
    val name by repo.databaseName.collectAsState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text(
                    name.takeIf { it != "" } ?: stringResource(R.string.app_name)
                ) },
            )
        },
        floatingActionButton = {
            SyncDatabaseFloatingActionButton(
                repo = repo,
                snackbarHostState = scaffoldState.snackbarHostState
            )
        }
    ) {
        Content(repo, nav, scaffoldState.snackbarHostState)
    }
}

@Composable
private fun Content(
    repo: Repository,
    nav: NavController? = null,
    snackbarHostState: SnackbarHostState? = null,
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
                val action = snackbarHostState?.showSnackbar(
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
    ) { entry -> EntryListItemExpandedArea(entry, snackbarHostState) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryList(
    repo: Repository,
    onClick: (entry: Entry) -> Unit,
    onClickLabel: String? = null,
    expanded: (@Composable ColumnScope.(Entry) -> Unit)? = null,
) {
    val entries by repo.databaseEntries.collectAsState()
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    val iconFactory = remember { repo.iconFactory }

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
fun EntryListItemExpandedArea(entry: Entry, snackbarHostState: SnackbarHostState? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPassword by remember { mutableStateOf(false) }

    fun showSnackbar(msg: String) {
        scope.launch {
            snackbarHostState?.showSnackbar(msg, duration=SnackbarDuration.Short)
        }
    }

    // Password
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Spacer(Modifier.width(32.dp))
        Text(
            text = if (showPassword) entry.password else "******",
            textAlign = TextAlign.Center,
            fontFamily = FontFamily(
                Font(R.font.fira_mono_regular)
            ),
            fontSize = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        IconButton(
            onClick = { showPassword = !showPassword },
        ) {
            if (showPassword)
                Icon(Icons.Default.Visibility, stringResource(R.string.hide_password_title))
            else
                Icon(Icons.Default.VisibilityOff, stringResource(R.string.title_show_password))
        }
    }

    // Actions
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (entry.username != "")
            IconButton(onClick = {
                entry.copyUsername(context)
                showSnackbar(context.getString(R.string.username_copied, entry.username))
            }) {
                Icon(Icons.Default.Person, stringResource(R.string.action_copy_username))
            }
        if (entry.password != "")
            IconButton(onClick = {
                entry.copyPassword(context)
                showSnackbar(context.getString(R.string.password_copied))
            }) {
                Icon(Icons.Default.Password, stringResource(R.string.action_copy_password))
            }
        if (entry.url != "") {
            IconButton(onClick = {
                entry.copyUrl(context)
                showSnackbar(context.getString(R.string.url_copied))
            }) {
                Icon(Icons.Default.Link, stringResource(R.string.action_copy_url))
            }
            IconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.url)))
                if (entry.username != "") entry.copyUsername(context)
                if (entry.password != "") entry.copyPasswordPostponed(context)
            }) {
                Icon(Icons.Default.OpenInBrowser, stringResource(R.string.action_open_in_browser))
            }
        }
    }

}
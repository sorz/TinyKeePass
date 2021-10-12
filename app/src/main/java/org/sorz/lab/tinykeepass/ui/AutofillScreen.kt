package org.sorz.lab.tinykeepass.ui

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.composethemeadapter.MdcTheme
import com.kunzisoft.keepass.database.element.Entry
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.auth.SecureStorage
import org.sorz.lab.tinykeepass.auth.SystemException
import org.sorz.lab.tinykeepass.auth.UserAuthException
import org.sorz.lab.tinykeepass.autofill.AutofillUtils
import org.sorz.lab.tinykeepass.autofill.StructureParser
import org.sorz.lab.tinykeepass.autofill.getAutofillIntentSender
import org.sorz.lab.tinykeepass.keepass.DatabaseState
import org.sorz.lab.tinykeepass.keepass.LocalKeePass
import org.sorz.lab.tinykeepass.keepass.Repository
import org.sorz.lab.tinykeepass.search.SearchIndex
import java.lang.StringBuilder
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.streams.asSequence

private const val TAG = "AutofillScreen"
private const val MAX_NUM_CANDIDATE_ENTRIES = 5

enum class AutofillAction {
    AUTH_USER,
    SHOW_ALL_ENTRIES,
}

@Composable
fun AutofillScreen(
    repo: Repository,
    action: AutofillAction,
    structure: StructureParser.Result,
    finishWithResult: (resultCode: Int, intent: Intent?) -> Unit,
) {
    val context = LocalContext.current
    val state by repo.databaseState.collectAsState()
    var forceShowALlEntries by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MdcTheme {
        when (state) {
            DatabaseState.UNCONFIGURED -> {
                LaunchedEffect(state) {
                    Toast.makeText(context, R.string.error_database_not_configured, Toast.LENGTH_SHORT).show()
                    finishWithResult(RESULT_CANCELED, null)
                }
            }
            DatabaseState.LOCKED -> UnlockingDialog(repo) { err ->
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                finishWithResult(RESULT_CANCELED, null)
            }
            DatabaseState.UNLOCKED -> {
                if (forceShowALlEntries || action == AutofillAction.SHOW_ALL_ENTRIES) {
                    FullEntriesList(repo) { entry ->
                        val dataset = AutofillUtils.buildDataset(
                            context,
                            entry,
                            repo.iconFactory,
                            structure
                        ) ?: return@FullEntriesList finishWithResult(RESULT_CANCELED, null)
                        val intent = generateResponse(context, structure, listOf(dataset))
                        finishWithResult(RESULT_OK, intent)
                    }
                } else {
                    LaunchedEffect(state) {
                        val datasets = getCandidateDatasets(context, repo, structure)
                        if (datasets.isEmpty()) {
                            forceShowALlEntries = true
                        } else {
                            val intent = generateResponse(context, structure, datasets)
                            finishWithResult(RESULT_OK, intent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlockingDialog(repo: Repository, onError: (msg: String) -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(true) {
        val prefs = try {
            SecureStorage(context).run {
                getEncryptedPreferences(getExistingMasterKey())
            }
        } catch (err: SystemException) {
            Log.e(TAG, "fail to get master key", err) // FIXME: proper error message
            return@LaunchedEffect onError(context.getString(R.string.error_get_master_key, err.toString()))
        } catch (err: UserAuthException) {
            Log.e(TAG, "user auth fail", err) // FIXME: proper error message
            return@LaunchedEffect onError(err.message ?: err.toString())
        }
        val local = LocalKeePass.loadFromPrefs(prefs)
            ?: return@LaunchedEffect onError(context.getString(R.string.no_master_key))
        repo.unlockDatabase(local)
    }

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

@Composable
private fun FullEntriesList(
    repo: Repository,
    onSelect: (entry: Entry) -> Unit,
) {
    val scaffoldState = rememberScaffoldState()
    val name by repo.databaseName.collectAsState()
    var keyword by rememberSaveable { mutableStateOf("") }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            SearchableTopAppBar(
                title = name.takeIf { it != "" } ?: stringResource(R.string.app_name),
                keyword = keyword,
                onChange = { keyword = it },
            )
        },
        floatingActionButton = {
            SyncDatabaseFloatingActionButton(
                repo = repo,
                snackbarHostState = scaffoldState.snackbarHostState
            )
        }
    ) {
        EntryList(
            repo = repo,
            onClick = onSelect,
        )
    }
}

private fun getCandidateDatasets(context: Context, repo: Repository, result: StructureParser.Result): List<Dataset> {
    Log.d(TAG, "Enumerate entries")

    val index = SearchIndex(repo.databaseEntries.value)
    val queryBuilder = StringBuilder()
    result.title.forEach(Consumer { title: CharSequence? ->
        queryBuilder.append(title).append(' ')
    })
    val entryStream: Stream<Entry> = index.search(queryBuilder.toString())
        .map { uuid -> repo.findEntryByUUID(uuid) }

    return entryStream.asSequence()
        .mapNotNull { entry: Entry? ->
            AutofillUtils.buildDataset(
                context,
                entry!!,
                repo.iconFactory,
                result
            )
        }
        .take(MAX_NUM_CANDIDATE_ENTRIES)
        .toList()
}

private fun generateResponse(context: Context, result: StructureParser.Result, datasets: List<Dataset>): Intent {
    val responseBuilder = FillResponse.Builder()
    datasets.forEach(responseBuilder::addDataset)

    // add "show all" item
    val presentation = AutofillUtils.getRemoteViews(
        context,
        context.getString(R.string.autofill_item_show_all),
        R.drawable.ic_more_horiz_gray_24dp
    )
    presentation.setTextColor(R.id.textView, context.getColor(R.color.hint))
    val datasetBuilder = Dataset.Builder(presentation)
        .setAuthentication(context.getAutofillIntentSender(AutofillAction.SHOW_ALL_ENTRIES))
    result.allAutofillIds().forEach { id: AutofillId? ->
        datasetBuilder.setValue(
            id!!, null
        )
    }
    responseBuilder.addDataset(datasetBuilder.build())
    return Intent().apply {
        putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, responseBuilder.build());
    }
}
package org.sorz.lab.tinykeepass.autofill

import org.sorz.lab.tinykeepass.autofill.AutofillUtils.buildDataset
import org.sorz.lab.tinykeepass.autofill.AutofillUtils.getRemoteViews
import org.sorz.lab.tinykeepass.search.SearchIndex
import android.service.autofill.FillResponse
import android.service.autofill.Dataset
import org.sorz.lab.tinykeepass.R
import android.view.autofill.AutofillId
import android.content.IntentSender
import android.content.Intent
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kunzisoft.keepass.database.element.Entry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import org.sorz.lab.tinykeepass.auth.SecureStorage
import org.sorz.lab.tinykeepass.auth.SystemException
import org.sorz.lab.tinykeepass.auth.UserAuthException
import org.sorz.lab.tinykeepass.keepass.*
import org.sorz.lab.tinykeepass.ui.AutofillAuthScreen
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.util.*
import java.util.function.Consumer
import java.util.stream.Stream
import javax.inject.Inject


private const val TAG = "AuthActivity"

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {
    @Inject lateinit var repo: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenStarted {
            repo.databaseState.collect { state ->
                Log.d(TAG, "repo state: $state")
                when (state) {
                    DatabaseState.UNCONFIGURED ->
                        finishWithError(getString(R.string.error_database_not_configured))
                    DatabaseState.LOCKED -> unlockDatabase()
                    DatabaseState.UNLOCKED -> finishWithEntries()
                }
            }
        }
        
        setContent {
            AutofillAuthScreen(repo)
        }
    }

    private suspend fun unlockDatabase() {
        Log.d(TAG, "Locked, try to unlock")

        val prefs = try {
            SecureStorage(this).run {
                getEncryptedPreferences(getExistingMasterKey())
            }
        } catch (err: SystemException) {
            Log.e(TAG, "fail to get master key", err) // FIXME: proper error message
            return finishWithError(getString(R.string.error_get_master_key, err.toString()))
        } catch (err: UserAuthException) {
            Log.e(TAG, "user auth fail", err) // FIXME: proper error message
            return finishWithError(err.message ?: err.toString())
        }
        val local = LocalKeePass.loadFromPrefs(prefs)
            ?: return finishWithError(getString(R.string.no_master_key))
        repo.unlockDatabase(local)
    }

    private fun finishWithEntries() {
        Log.d(TAG, "Unlocked, enumerate entries")
        val structure = intent.getParcelableExtra<AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)
            ?: throw IllegalArgumentException("Missing intent extra EXTRA_ASSIST_STRUCTURE")
        val result = StructureParser(structure).parse()

        val index = SearchIndex(repo.databaseEntries.value)
        val queryBuilder = StringBuilder()
        result.title.forEach(Consumer { title: CharSequence? ->
            queryBuilder.append(title).append(' ')
        })
        val entryStream: Stream<Entry> = index.search(queryBuilder.toString())
            .map { uuid -> repo.findEntryByUUID(uuid) }
        val responseBuilder = FillResponse.Builder()
        // add matched entities
        entryStream
            .map { entry: Entry? -> buildDataset(this, entry!!, repo.iconFactory, result) }
            .filter { obj: Dataset? -> Objects.nonNull(obj) }
            .limit(MAX_NUM_CANDIDATE_ENTRIES.toLong())
            .forEach { dataset: Dataset? -> responseBuilder.addDataset(dataset) }
        // add "show all" item
        val presentation = getRemoteViews(
            this,
            getString(R.string.autofill_item_show_all),
            R.drawable.ic_more_horiz_gray_24dp
        )
        presentation.setTextColor(R.id.textView, getColor(R.color.hint))
        val datasetBuilder = Dataset.Builder(presentation)
            .setAuthentication(EntrySelectActivity.getAuthIntentSenderForResponse(this))
        result.allAutofillIds().forEach { id: AutofillId? ->
            datasetBuilder.setValue(
                id!!, null
            )
        }
        responseBuilder.addDataset(datasetBuilder.build())
        setResult(RESULT_OK, Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, responseBuilder.build());
        })
        finish()
    }


    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val MAX_NUM_CANDIDATE_ENTRIES = 5
        fun getAuthIntentSenderForResponse(context: Context?): IntentSender {
            val intent = Intent(context, AuthActivity::class.java)
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
            ).intentSender
        }
    }
}
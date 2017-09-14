package org.sorz.lab.tinykeepass.autofill;

import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.sorz.lab.tinykeepass.BaseActivity;
import org.sorz.lab.tinykeepass.R;
import org.sorz.lab.tinykeepass.autofill.search.SearchIndex;
import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import java.util.List;
import java.util.stream.Stream;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Entry;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.notEmpty;


@RequiresApi(api = Build.VERSION_CODES.O)
public class AuthActivity extends BaseActivity {
    private final static String TAG = AuthActivity.class.getName();
    private final static int MAX_NUM_CANDIDATE_ENTRIES = 5;
    private Intent replyIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (KeePassStorage.get() != null) {
            buildAndReturnAuthenticationResult();
        } else {
            getDatabaseKeys(keys -> {
                unlockDatabase(keys);
                buildAndReturnAuthenticationResult();
            }, error -> {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }

    @Override
    public void finish() {
        if (replyIntent != null) {
            setResult(RESULT_OK, replyIntent);
        } else {
            setResult(RESULT_CANCELED);
        }

        super.finish();
    }

    void buildAndReturnAuthenticationResult() {
        AssistStructure structure =
                getIntent().getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE);
        StructureParser.Result result = new StructureParser(structure).parse();
        KeePassFile keePass = KeePassStorage.get();
        SearchIndex index = new SearchIndex(keePass);
        StringBuilder queryBuilder = new StringBuilder();
        result.title.forEach(title -> queryBuilder.append(title).append(' '));
        Stream<Entry> entryStream = index.search(queryBuilder.toString())
                .map(keePass::getEntryByUUID)
                .limit(MAX_NUM_CANDIDATE_ENTRIES);


        FillResponse.Builder responseBuilder = new FillResponse.Builder();
        entryStream.forEach(entry -> {
            RemoteViews presentation = AutofillUtils.getRemoteViews(this,
                    makeEntryTitle(entry),
                    R.drawable.ic_person_blue_24dp);
            Dataset.Builder datasetBuilder = new Dataset.Builder(presentation);

            if (notEmpty(entry.getPassword())) {
                AutofillValue value = AutofillValue.forText(entry.getPassword());
                result.password.forEach(id -> datasetBuilder.setValue(id, value));
            }
            if (notEmpty(entry.getUsername())) {
                AutofillValue value = AutofillValue.forText(entry.getUsername());
                Stream<AutofillId> ids = result.username.stream();
                if (entry.getUsername().contains("@") || result.username.isEmpty())
                    ids = Stream.concat(ids, result.email.stream());
                ids.forEach(id -> datasetBuilder.setValue(id, value));
            }
            responseBuilder.addDataset(datasetBuilder.build());
        });

        replyIntent = new Intent();
        replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, responseBuilder.build());
        finish();
    }

    private void unlockDatabase(List<String> keys) {
        try {
            KeePassFile db = KeePassDatabase.getInstance(getDatabaseFile())
                    .openDatabase(keys.get(0));
            KeePassStorage.set(this, db);
        } catch (KeePassDatabaseUnreadableException | UnsupportedOperationException e) {
            Log.w(TAG, "cannot open database.", e);
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private String makeEntryTitle(Entry entry) {
        if (notEmpty(entry.getTitle()) && notEmpty(entry.getUsername()))
            return String.format("%s (%s)", entry.getTitle(), entry.getUsername());
        if (notEmpty(entry.getTitle()))
            return entry.getTitle();
        if (notEmpty(entry.getUsername()))
            return entry.getUsername();
        if (notEmpty(entry.getNotes()))
            return entry.getNotes().trim();
        return getString(R.string.autofill_not_title);
    }

    static IntentSender getAuthIntentSenderForResponse(Context context) {
        Intent intent = new Intent(context, AuthActivity.class);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT).getIntentSender();
    }

}

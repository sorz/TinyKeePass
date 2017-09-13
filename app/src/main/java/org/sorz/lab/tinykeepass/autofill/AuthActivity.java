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
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.sorz.lab.tinykeepass.BaseActivity;
import org.sorz.lab.tinykeepass.KeePassStorage;
import org.sorz.lab.tinykeepass.R;

import java.util.List;
import java.util.stream.Stream;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Entry;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;


@RequiresApi(api = Build.VERSION_CODES.O)
public class AuthActivity extends BaseActivity {
    private final static String TAG = AuthActivity.class.getName();

    private Intent replyIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_autofill);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getDatabaseKeys(this::unlockDatabase, error -> {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            finish();
        });
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

    void onEntrySelected(Entry entry) {
        AssistStructure structure =
                getIntent().getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE);
        StructureParser.Result result = new StructureParser(structure).parse();

        String title = getString(R.string.autofill_touch_to_fill);
        if (notEmpty(entry.getUsername()))
            title = entry.getUsername();
        else if (notEmpty(entry.getTitle()))
            title = entry.getTitle();

        RemoteViews presentation = AutofillUtils.getRemoteViews(this, title,
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
        FillResponse response = new FillResponse.Builder()
                .addDataset(datasetBuilder.build())
                .build();

        replyIntent = new Intent();
        replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response);
        finish();
    }

    static boolean notEmpty(String string) {
        return string != null && !string.isEmpty();
    }

    protected void unlockDatabase(List<String> keys) {
        try {
            KeePassFile db = KeePassDatabase.getInstance(getDatabaseFile())
                    .openDatabase(keys.get(0));
            KeePassStorage.set(this, db);
        } catch (KeePassDatabaseUnreadableException | UnsupportedOperationException e) {
            Log.w(TAG, "cannot open database.", e);
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EntrySelectFragment.newInstance())
                .commit();
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(R.string.title_autofill_select);
    }

    static IntentSender getAuthIntentSenderForResponse(Context context) {
        Intent intent = new Intent(context, AuthActivity.class);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT).getIntentSender();
    }

}

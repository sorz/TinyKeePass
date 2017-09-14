package org.sorz.lab.tinykeepass.autofill;

import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.service.autofill.FillResponse;
import android.support.annotation.RequiresApi;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.autofill.AutofillManager;
import android.widget.Toast;

import org.sorz.lab.tinykeepass.BaseActivity;
import org.sorz.lab.tinykeepass.R;
import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import java.util.List;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Entry;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;


@RequiresApi(api = Build.VERSION_CODES.O)
public class EntrySelectActivity extends BaseActivity {
    private final static String TAG = EntrySelectActivity.class.getName();

    private Intent replyIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (KeePassStorage.get() != null) {
            showList();
        } else {
            getDatabaseKeys(keys -> {
                unlockDatabase(keys);
                showList();
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

    void onEntrySelected(Entry entry) {
        AssistStructure structure =
                getIntent().getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE);
        StructureParser.Result result = new StructureParser(structure).parse();

        FillResponse response = new FillResponse.Builder()
                .addDataset(AutofillUtils.buildDataset(this, entry, result))
                .build();

        replyIntent = new Intent();
        replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response);
        finish();
    }

    private void showList() {
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EntrySelectFragment.newInstance())
                .commit();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_autofill_select);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
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


    static IntentSender getAuthIntentSenderForResponse(Context context) {
        Intent intent = new Intent(context, EntrySelectActivity.class);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT).getIntentSender();
    }
}

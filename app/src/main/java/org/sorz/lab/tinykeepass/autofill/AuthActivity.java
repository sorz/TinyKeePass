package org.sorz.lab.tinykeepass.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import org.sorz.lab.tinykeepass.BaseActivity;
import org.sorz.lab.tinykeepass.KeePassStorage;
import org.sorz.lab.tinykeepass.R;

import java.util.List;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Entry;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

public class AuthActivity extends BaseActivity {
    private final static String TAG = AuthActivity.class.getName();

    private Toolbar toolbar;
    private Intent replyIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
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
        System.out.println(entry + " selected");
        finish();
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

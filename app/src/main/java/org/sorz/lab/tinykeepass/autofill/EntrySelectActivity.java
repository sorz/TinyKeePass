package org.sorz.lab.tinykeepass.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.service.autofill.FillResponse;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;

import org.sorz.lab.tinykeepass.R;

import de.slackspace.openkeepass.domain.Entry;


@RequiresApi(api = Build.VERSION_CODES.O)
public class EntrySelectActivity extends BaseActivity {

    void onEntrySelected(Entry entry) {
        StructureParser.Result result = parseStructure();
        FillResponse response = new FillResponse.Builder()
                .addDataset(AutofillUtils.buildDataset(this, entry, result))
                .build();
        setFillResponse(response);
        finish();
    }

    @Override
    protected void onDatabaseOpened() {
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EntrySelectFragment.newInstance())
                .commit();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_autofill_select);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    static IntentSender getAuthIntentSenderForResponse(Context context) {
        Intent intent = new Intent(context, EntrySelectActivity.class);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT).getIntentSender();
    }
}

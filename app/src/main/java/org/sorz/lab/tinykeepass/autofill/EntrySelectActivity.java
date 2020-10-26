package org.sorz.lab.tinykeepass.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;

import org.sorz.lab.tinykeepass.R;
import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import com.kunzisoft.keepass.database.element.Entry;
import com.kunzisoft.keepass.icons.IconDrawableFactory;


@RequiresApi(api = Build.VERSION_CODES.O)
public class EntrySelectActivity extends BaseActivity {

    void onEntrySelected(Entry entry) {
        IconDrawableFactory iconFactory = KeePassStorage.get(this).getDrawFactory();
        StructureParser.Result result = parseStructure();
        Dataset dataset = AutofillUtils.INSTANCE.buildDataset(this, entry, iconFactory, result);
        FillResponse response = new FillResponse.Builder()
                .addDataset(dataset)
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

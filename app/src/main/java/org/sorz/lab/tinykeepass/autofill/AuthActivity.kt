package org.sorz.lab.tinykeepass.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import androidx.annotation.RequiresApi;

import android.widget.RemoteViews;

import org.sorz.lab.tinykeepass.R;
import org.sorz.lab.tinykeepass.search.SearchIndex;
import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import java.util.Objects;
import java.util.stream.Stream;

import com.kunzisoft.keepass.database.element.Database;
import com.kunzisoft.keepass.database.element.Entry;
import com.kunzisoft.keepass.database.element.node.NodeIdUUID;
import com.kunzisoft.keepass.icons.IconDrawableFactory;


@RequiresApi(api = Build.VERSION_CODES.O)
public class AuthActivity extends BaseActivity {
    private final static int MAX_NUM_CANDIDATE_ENTRIES = 5;

    @Override
    protected void onDatabaseOpened() {
        StructureParser.Result result = parseStructure();
        Database keePass = KeePassStorage.get(this);
        IconDrawableFactory iconFactory = keePass.getDrawFactory();
        SearchIndex index = new SearchIndex(keePass);
        StringBuilder queryBuilder = new StringBuilder();
        result.title.forEach(title -> queryBuilder.append(title).append(' '));
        Stream<Entry> entryStream = index.search(queryBuilder.toString())
                .map(entry -> keePass.getEntryById(new NodeIdUUID(entry)));

        FillResponse.Builder responseBuilder = new FillResponse.Builder();
        // add matched entities
        entryStream
                .map(entry -> AutofillUtils.INSTANCE.buildDataset(this, entry, iconFactory, result))
                .filter(Objects::nonNull)
                .limit(MAX_NUM_CANDIDATE_ENTRIES)
                .forEach(responseBuilder::addDataset);
        // add "show all" item
        RemoteViews presentation = AutofillUtils.INSTANCE.getRemoteViews(this,
                getString(R.string.autofill_item_show_all),
                R.drawable.ic_more_horiz_gray_24dp);
        presentation.setTextColor(R.id.textView, getColor(R.color.hint));
        Dataset.Builder datasetBuilder = new Dataset.Builder(presentation)
                .setAuthentication(EntrySelectActivity.getAuthIntentSenderForResponse(this));
        result.allAutofillIds().forEach(id -> datasetBuilder.setValue(id, null));
        responseBuilder.addDataset(datasetBuilder.build());

        setFillResponse(responseBuilder.build());
        finish();
    }

    static IntentSender getAuthIntentSenderForResponse(Context context) {
        Intent intent = new Intent(context, AuthActivity.class);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT).getIntentSender();
    }
}

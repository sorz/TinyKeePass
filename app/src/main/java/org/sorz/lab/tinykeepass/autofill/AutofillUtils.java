package org.sorz.lab.tinykeepass.autofill;

import android.content.Context;
import android.os.Build;
import android.service.autofill.Dataset;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import org.sorz.lab.tinykeepass.R;

import java.util.stream.Stream;

import de.slackspace.openkeepass.domain.Entry;

import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.getIcon;
import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.notEmpty;

@RequiresApi(api = Build.VERSION_CODES.O)
class AutofillUtils {

    static RemoteViews getRemoteViews(Context context, String text, @DrawableRes int icon) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.autofill_service_list_item);
        views.setTextViewText(R.id.textView, text);
        views.setImageViewResource(R.id.imageIcon, icon);
        return views;
    }

    static @Nullable Dataset buildDataset(Context context, Entry entry,
                                          StructureParser.Result struct) {
        String title = makeEntryTitle(context, entry);
        RemoteViews views = getRemoteViews(context, title, R.drawable.ic_person_blue_24dp);
        views.setImageViewBitmap(R.id.imageIcon, getIcon(entry));
        Dataset.Builder builder = new Dataset.Builder(views);
        builder.setId(entry.getUuid().toString());

        if (notEmpty(entry.getPassword())) {
            AutofillValue value = AutofillValue.forText(entry.getPassword());
            struct.password.forEach(id -> builder.setValue(id, value));
        }
        if (notEmpty(entry.getUsername())) {
            AutofillValue value = AutofillValue.forText(entry.getUsername());
            Stream<AutofillId> ids = struct.username.stream();
            if (entry.getUsername().contains("@") || struct.username.isEmpty())
                ids = Stream.concat(ids, struct.email.stream());
            ids.forEach(id -> builder.setValue(id, value));
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            // if not value be set
            return null;
        }
    }


    static private String makeEntryTitle(Context context, Entry entry) {
        if (notEmpty(entry.getTitle()) && notEmpty(entry.getUsername()))
            return String.format("%s (%s)", entry.getTitle(), entry.getUsername());
        if (notEmpty(entry.getTitle()))
            return entry.getTitle();
        if (notEmpty(entry.getUsername()))
            return entry.getUsername();
        if (notEmpty(entry.getNotes()))
            return entry.getNotes().trim();
        return context.getString(R.string.autofill_not_title);
    }

}

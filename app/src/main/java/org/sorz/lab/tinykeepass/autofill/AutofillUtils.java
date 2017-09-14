package org.sorz.lab.tinykeepass.autofill;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.widget.RemoteViews;

import org.sorz.lab.tinykeepass.R;

import de.slackspace.openkeepass.domain.Entry;

import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.getIcon;
import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.notEmpty;

class AutofillUtils {

    static RemoteViews getRemoteViews(Context context, String text, @DrawableRes int icon) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.autofill_service_list_item);
        views.setTextViewText(R.id.textView, text);
        views.setImageViewResource(R.id.imageIcon, icon);
        return views;
    }

    static RemoteViews getRemoteViews(Context context, Entry entry) {
        String title = makeEntryTitle(context, entry);
        RemoteViews views = getRemoteViews(context, title, R.drawable.ic_person_blue_24dp);
        views.setImageViewBitmap(R.id.imageIcon, getIcon(entry));
        return views;
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

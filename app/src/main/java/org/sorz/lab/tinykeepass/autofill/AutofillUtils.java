package org.sorz.lab.tinykeepass.autofill;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.widget.RemoteViews;

import org.sorz.lab.tinykeepass.R;

class AutofillUtils {

    static RemoteViews getRemoteViews(Context context, String text, @DrawableRes int icon) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.autofill_service_list_item);
        views.setTextViewText(R.id.textView, text);
        views.setTextViewCompoundDrawables(R.id.textView, icon, 0, 0, 0);
        return views;
    }
}

package org.sorz.lab.tinykeepass.autofill;


import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.view.View;

import org.sorz.lab.tinykeepass.BaseEntryFragment;
import org.sorz.lab.tinykeepass.R;

import de.slackspace.openkeepass.domain.Entry;


@RequiresApi(api = Build.VERSION_CODES.O)
public class EntrySelectFragment extends BaseEntryFragment {
    private EntrySelectActivity activity;

    public EntrySelectFragment() {
        // Required empty public constructor
    }

    public static EntrySelectFragment newInstance() {
        return new EntrySelectFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (EntrySelectActivity) context;
    }

    @Override
    protected int getFragmentLayout() {
        return R.layout.fragment_entry_select;
    }

    @Override
    protected void onEntryClick(View view, Entry entry) {
        activity.onEntrySelected(entry);
    }

    @Override
    protected boolean onEntryLongClick(View view, Entry entry) {
        return false;
    }

}

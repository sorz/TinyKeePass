package org.sorz.lab.tinykeepass;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.domain.Entry;


public class EntryRecyclerViewAdapter extends RecyclerView.Adapter<EntryRecyclerViewAdapter.ViewHolder> {
    private final List<Entry> entries;
    private final MainActivity activity;

    public EntryRecyclerViewAdapter(MainActivity activity) {
        this.activity = activity;
        KeePassFile db = KeePassStorage.getKeePassFile();
        if (db != null)
            entries = db.getEntries();
        else
            entries = new ArrayList<>();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = entries.get(position);
        holder.mIdView.setText(entries.get(position).getTitle());
        holder.mContentView.setText(entries.get(position).getUsername());

        holder.view.setOnClickListener(v -> {
            if (null != activity) {
                // Notify the active callbacks interface (the activity, if the
                // fragment is attached to one) that an item has been selected.
                //activity.onListFragmentInteraction(holder.mItem);
            }
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final View view;
        final TextView mIdView;
        final TextView mContentView;
        Entry mItem;

        ViewHolder(View view) {
            super(view);
            this.view = view;
            mIdView = (TextView) view.findViewById(R.id.id);
            mContentView = (TextView) view.findViewById(R.id.content);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }

}

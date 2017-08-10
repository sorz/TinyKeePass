package org.sorz.lab.tinykeepass;

import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.domain.Entry;


public class EntryRecyclerViewAdapter extends RecyclerView.Adapter<EntryRecyclerViewAdapter.ViewHolder> {
    private final static String TAG = EntryRecyclerViewAdapter.class.getName();
    private final BiConsumer<View, Entry> onClickHandler;
    private final BiPredicate<View, Entry> onLongClickHandler;
    private List<Entry> allEntries;
    private List<Entry> entries;
    private String filter;
    private int selectedItem = -1;
    private int passwordShownItem = -1;


    public EntryRecyclerViewAdapter(BiConsumer<View, Entry> onClickHandler,
                                    BiPredicate<View, Entry> onLongClickHandler) {
        this.onClickHandler = onClickHandler;
        this.onLongClickHandler = onLongClickHandler;
        reloadEntries();
    }

    public void reloadEntries() {
        KeePassFile db = KeePassStorage.get();
        if (db != null) {
            if (db.getMeta().getRecycleBinEnabled()) {
                allEntries = new ArrayList<>();
                UUID recycleBin = db.getMeta().getRecycleBinUuid();
                db.getGroups().stream()
                        .filter(g -> !g.getUuid().equals(recycleBin))
                        .forEach(g -> allEntries.addAll(g.getEntries()));
            } else {
                allEntries = db.getEntries();
            }
            allEntries.sort((a, b) -> {
                if (a.getTitle() != null && b.getTitle() != null)
                    return a.getTitle().compareTo(b.getTitle());
                else if (a.getUsername() != null && b.getUsername() != null)
                    return a.getUsername().compareTo(b.getUsername());
                else if (a.getUrl() != null && b.getUrl() != null)
                    return a.getUrl().compareTo(b.getUrl());
                else
                    return 0;
            });

            Log.d(TAG, allEntries.size() + " entries loaded");
        } else {
            Log.w(TAG, "database is locked");
            allEntries = new ArrayList<>();
        }
        setFilter(filter);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        Entry entry = entries.get(position);
        holder.entry = entry;
        holder.view.setSelected(selectedItem == position);
        holder.imageIcon.setImageBitmap(
                BitmapFactory.decodeByteArray(entry.getIconData(), 0,
                        entry.getIconData().length));
        holder.textTitle.setText(parse(entry.getTitle()));
        holder.textUsername.setText(parse(entry.getUsername()));

        String url = parse(entry.getUrl()).replaceFirst("https?://(www\\.)?", "");
        String[] hostnamePath = url.split("/", 2);
        holder.textUrlHostname.setText(hostnamePath[0]);
        holder.textUrlPath.setText(hostnamePath.length > 1 && !hostnamePath[1].isEmpty() ?
                "/" + hostnamePath[1] : "");

        if (position == passwordShownItem) {
            if (entry.getPassword() != null && !entry.getPassword().isEmpty())
                holder.textPassword.setText(entry.getPassword());
            else
                holder.textPassword.setText(R.string.no_password);
            holder.textPassword.setVisibility(View.VISIBLE);
        } else {
            holder.textPassword.setVisibility(View.GONE);
            holder.textPassword.setText("");
        }

        if (onClickHandler != null)
            holder.view.setOnClickListener(v -> {
                if (passwordShownItem == position) {
                    hidePassword();
                } else {
                    hidePassword();
                    onClickHandler.accept(v, entry);
                }
            });
        if (onLongClickHandler != null)
            holder.view.setOnLongClickListener(v -> {
                setSelectedItem(position);
                return onLongClickHandler.test(v, entry);
            });
    }

    private static String parse(String s) {
        return s == null ? "" : s;
    }

    private void setSelectedItem(int position) {
        if (selectedItem >= 0)
            notifyItemChanged(selectedItem);
        selectedItem = position;
        notifyItemChanged(position);
    }

    @Nullable
    public Entry getSelectedItem() {
        if (selectedItem >= 0 && selectedItem < entries.size())
            return entries.get(selectedItem);
        return null;
    }

    public void clearSelection() {
        int item = selectedItem;
        selectedItem = -1;
        if (item >= 0)
            notifyItemChanged(item);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final View view;
        final ImageView imageIcon;
        final TextView textTitle;
        final TextView textUsername;
        final TextView textUrlHostname;
        final TextView textUrlPath;
        final TextView textPassword;
        Entry entry;

        ViewHolder(View view) {
            super(view);
            this.view = view;
            imageIcon = view.findViewById(R.id.imageIcon);
            textTitle = view.findViewById(R.id.textTitle);
            textUsername = view.findViewById(R.id.textUsername);
            textUrlHostname = view.findViewById(R.id.textUrlHostname);
            textUrlPath = view.findViewById(R.id.textUrlPath);
            textPassword = view.findViewById(R.id.textPassword);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + textTitle.getText() + "'";
        }
    }

    private static boolean contains(String string, String query) {
        return string != null && string.toLowerCase().contains(query);
    }

    public void setFilter(String query) {
        selectedItem = -1;
        passwordShownItem = -1;
        if (query == null || query.isEmpty()) {
            entries = allEntries;
        } else {
            final String q = query.toLowerCase().trim();
            entries = allEntries.parallelStream().filter(e ->
                    contains(e.getTitle(), q) ||
                    contains(e.getUrl(), q)  ||
                    contains(e.getNotes(), q)  ||
                    contains(e.getUsername(), q)
            ).collect(Collectors.toList());
        }
        filter = query;
        notifyDataSetChanged();
    }

    public void showPassword(Entry entry) {
        passwordShownItem = entries.indexOf(entry);
        notifyItemChanged(passwordShownItem);
    }

    private void hidePassword() {
        int item = passwordShownItem;
        passwordShownItem = -1;
        if (item < 0 || item >= entries.size())
            return;
        notifyItemChanged(item);
    }

}

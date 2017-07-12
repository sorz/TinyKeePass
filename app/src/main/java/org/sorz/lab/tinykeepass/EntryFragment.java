package org.sorz.lab.tinykeepass;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import de.slackspace.openkeepass.domain.Entry;


public class EntryFragment extends Fragment implements SearchView.OnQueryTextListener {
    private static final long INACTIVE_AUTO_LOCK_MILLIS = 3 * 60 * 1000;

    private MainActivity activity;
    private EntryRecyclerViewAdapter entryAdapter;
    private ClipboardManager clipboardManager;
    private LocalBroadcastManager localBroadcastManager;
    private FloatingActionButton fab;
    private ActionMode actionMode;
    private Entry selectedEntry;
    private long lastPauseTimeMillis;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public EntryFragment() {
    }


    public static EntryFragment newInstance() {
        return new EntryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_entry_list, container, false);

        // Set the adapter

        Context context = view.getContext();
        RecyclerView recyclerView = view.findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        entryAdapter = new EntryRecyclerViewAdapter((v, e) -> copyEntry(e), this::onEntryLongClick);
        recyclerView.setAdapter(entryAdapter);

        fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            if (activity != null) {
                fab.hide();
                activity.doSyncDatabase();
            }
        });
        return view;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (MainActivity) context;
        localBroadcastManager = LocalBroadcastManager.getInstance(activity);
        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        localBroadcastManager.registerReceiver(broadcastReceiver,
                new IntentFilter(DatabaseSyncingService.BROADCAST_SYNC_FINISHED));
    }

    @Override
    public void onStop() {
        super.onStop();
        localBroadcastManager.unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (KeePassStorage.get() == null)
            activity.doLockDatabase();
        if (lastPauseTimeMillis > 0
                && SystemClock.elapsedRealtime() - lastPauseTimeMillis > INACTIVE_AUTO_LOCK_MILLIS)
            activity.doLockDatabase();
    }

    @Override
    public void onPause() {
        super.onPause();
        lastPauseTimeMillis = SystemClock.elapsedRealtime();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        MenuItem searchMenu = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchMenu.getActionView();
        searchView.setOnQueryTextListener(this);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_lock_db:
                if (activity != null)
                    activity.doLockDatabase();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return onQueryTextChange(query);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        entryAdapter.setFilter(newText);
        return true;
    }

    private void copyEntry(Entry entry) {
        if (entry.getUsername() != null) {
            clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.username), entry.getUsername()));
            String message = getString(R.string.username_copied, entry.getUsername());
            if (getView() == null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            } else {
                Snackbar.make(getView(), message, Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.copy_password),v -> {
                            Intent intent = new Intent(getContext(), PasswordCopingService.class);
                            intent.setAction(PasswordCopingService.ACTION_COPY_PASSWORD);
                            intent.putExtra(PasswordCopingService.EXTRA_PASSWORD,
                                    entry.getPassword());
                            getContext().startService(intent);
                            Snackbar.make(getView(), R.string.password_copied,
                                    Snackbar.LENGTH_SHORT).show();
                }).show();
            }
        }
        if (entry.getPassword() != null) {
            Intent intent = new Intent(getContext(), PasswordCopingService.class);
            intent.setAction(PasswordCopingService.ACTION_NEW_NOTIFICATION);
            intent.putExtra(PasswordCopingService.EXTRA_PASSWORD, entry.getPassword());
            if (entry.getUsername() != null)
                intent.putExtra(PasswordCopingService.EXTRA_USERNAME, entry.getUsername());
            if (entry.getTitle() != null)
                intent.putExtra(PasswordCopingService.EXTRA_ENTRY_TITLE, entry.getTitle());
            getContext().startService(intent);
        }
    }

    private void openEntryUrl(Entry entry) {
        if (entry.getUrl() == null || entry.getUrl().isEmpty())
            return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(entry.getUrl()));
        startActivity(intent);
        copyEntry(entry);
    }

    private boolean onEntryLongClick(View view, Entry entry) {
        if (getActivity() == null || actionMode != null)
            return false;
        selectedEntry = entry;
        Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
        if (toolbar == null)
            return false;
        actionMode = toolbar.startActionMode(entryLongClickActionModeCallback);
        view.setSelected(true);
        return true;
    }

    private ActionMode.Callback entryLongClickActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.entry_context, menu);
            if (selectedEntry.getUrl() == null || selectedEntry.getUrl().isEmpty())
                mode.getMenu().getItem(R.id.action_open).setEnabled(false);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_open:
                    openEntryUrl(selectedEntry);
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            selectedEntry = null;
        }
    };

        private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DatabaseSyncingService.BROADCAST_SYNC_FINISHED.equals(intent.getAction())) {
                fab.show();
                String error = intent.getStringExtra(DatabaseSyncingService.EXTRA_SYNC_ERROR);

                if (error != null) {
                    if (getView() != null)
                        Snackbar.make(getView(), getString(R.string.fail_to_sync, error),
                                Snackbar.LENGTH_LONG).show();
                } else {
                    if (getView() != null)
                        Snackbar.make(getView(), R.string.sync_done, Snackbar.LENGTH_SHORT).show();
                    entryAdapter.reloadEntries();
                }
            }
        }
    };
}

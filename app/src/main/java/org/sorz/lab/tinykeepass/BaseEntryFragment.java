package org.sorz.lab.tinykeepass;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import de.slackspace.openkeepass.domain.Entry;


public abstract class BaseEntryFragment extends Fragment implements SearchView.OnQueryTextListener {
    private static final int ENTRY_MAX_WIDTH_DP = 350;

    private EntryRecyclerViewAdapter entryAdapter;

    abstract protected @LayoutRes int getFragmentLayout();

    abstract protected void onEntryClick(View view, Entry entry);

    abstract protected boolean onEntryLongClick(View view, Entry entry);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                      Bundle savedInstanceState) {
        View view =  inflater.inflate(getFragmentLayout(), container, false);

        // Set the adapter
        RecyclerView recyclerView = view.findViewById(R.id.list);
        int spanCount = getResources().getConfiguration().screenWidthDp / ENTRY_MAX_WIDTH_DP;
        RecyclerView.LayoutManager layoutManager = spanCount <= 1
                ? new LinearLayoutManager(getContext())
                : new GridLayoutManager(getContext(), spanCount);
        recyclerView.setLayoutManager(layoutManager);

        entryAdapter = new EntryRecyclerViewAdapter(getContext(),
                this::onEntryClick, this::onEntryLongClick);
        recyclerView.setAdapter(entryAdapter);

        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)  {
        inflater.inflate(R.menu.menu_search, menu);
        MenuItem searchMenu = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchMenu.getActionView();
        searchView.setOnQueryTextListener(this);
        super.onCreateOptionsMenu(menu, inflater);
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
}

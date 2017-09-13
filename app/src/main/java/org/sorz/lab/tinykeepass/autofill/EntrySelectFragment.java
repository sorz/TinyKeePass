package org.sorz.lab.tinykeepass.autofill;


import android.content.Context;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sorz.lab.tinykeepass.EntryRecyclerViewAdapter;
import org.sorz.lab.tinykeepass.R;


public class EntrySelectFragment extends Fragment {
    private AuthActivity activity;

    public EntrySelectFragment() {
        // Required empty public constructor
    }

    public static EntrySelectFragment newInstance() {
        return new EntrySelectFragment();
    }
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (AuthActivity) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view =  inflater.inflate(R.layout.fragment_entry_select, container, false);

        // Set the adapter
        RecyclerView recyclerView = view.findViewById(R.id.list);
        recyclerView.setLayoutManager(EntryRecyclerViewAdapter
                .getDefaultLayoutManager(getContext()));

        EntryRecyclerViewAdapter entryAdapter = new EntryRecyclerViewAdapter(
                getContext(),
                (v, e) -> activity.onEntrySelected(e),
                (v, e) -> false);
        recyclerView.setAdapter(entryAdapter);

        return view;
    }

}

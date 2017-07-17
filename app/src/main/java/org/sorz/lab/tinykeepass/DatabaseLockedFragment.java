package org.sorz.lab.tinykeepass;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * A placeholder fragment containing a simple view.
 */
public class DatabaseLockedFragment extends Fragment {
    private MainActivity activity;

    public DatabaseLockedFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (MainActivity) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_database_locked, container, false);
        Button unlockDb = view.findViewById(R.id.buttonUnlockDb);
        Button configDb = view.findViewById(R.id.buttonConfigDb);
        unlockDb.setOnClickListener(v -> activity.doUnlockDatabase());
        configDb.setOnClickListener(v -> activity.doConfigureDatabase());
        return view;
    }
}

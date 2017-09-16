package org.sorz.lab.tinykeepass.keepass;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.sorz.lab.tinykeepass.R;

/**
 * Open KeePassFile with a loading dialog.
 */
public class OpenKeePassDialogFragment extends DialogFragment {

    public OpenKeePassDialogFragment() {
    }

    public static OpenKeePassDialogFragment newInstance() {
        return new OpenKeePassDialogFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(R.string.open_db_dialog_title);
        getDialog().setCancelable(false);
        getDialog().setCanceledOnTouchOutside(true);
        return inflater.inflate(R.layout.fragment_open_database_dialog,
                container, false);
    }

    void onOpenError(String message) {
        if (getView() == null) {
            dismiss();
            return;
        }
        TextView note = getView().findViewById(R.id.textView);
        ProgressBar progressBar = getView().findViewById(R.id.progressBar);
        getDialog().setTitle(R.string.open_db_dialog_fail);
        note.setText(message);
        progressBar.setVisibility(View.INVISIBLE);
        setCancelable(true);
    }

    void onOpenOk() {
        dismiss();
    }
}

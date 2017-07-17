package org.sorz.lab.tinykeepass;

import android.app.Fragment;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import javax.crypto.Cipher;

/**
 * A placeholder fragment containing a simple view.
 */
public class DatabaseLockedFragment extends Fragment {
    private MainActivity activity;
    private SharedPreferences preferences;
    private FingerprintUiHelper fingerprintUiHelper;

    public DatabaseLockedFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (MainActivity) context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_database_locked, container, false);
        View fingerprint = view.findViewById(R.id.fingerprint);
        Button unlockDb = view.findViewById(R.id.buttonUnlockDb);
        Button configDb = view.findViewById(R.id.buttonConfigDb);
        unlockDb.setOnClickListener(v -> activity.doUnlockDatabase());
        configDb.setOnClickListener(v -> activity.doConfigureDatabase());

        if (activity.hasConfiguredDatabase() &&
                preferences.getInt("key-auth-method", -1)
                        == DatabaseSetupActivity.AUTH_METHOD_FINGERPRINT) {
            fingerprint.setVisibility(View.VISIBLE);
            fingerprintUiHelper = new FingerprintUiHelper(activity, fingerprint,
                    this::onFingerprintFinish);
        }
        return view;
    }


    private void onFingerprintFinish(Cipher cipher) {
        if (cipher == null) {
            if (getView() != null)
                getView().findViewById(R.id.fingerprint).setVisibility(View.INVISIBLE);
        } else {
            if (activity != null)
                activity.doUnlockDatabase(cipher);
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        fingerprintUiHelper.start(Cipher.DECRYPT_MODE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (fingerprintUiHelper != null)
            fingerprintUiHelper.stop();
    }


}

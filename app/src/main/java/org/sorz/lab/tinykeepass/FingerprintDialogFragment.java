package org.sorz.lab.tinykeepass;

import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import javax.crypto.Cipher;


/**
 * Display a fingerprint dialog.
 * Return authenticated cipher if auth passed.
 */
public class FingerprintDialogFragment extends DialogFragment {
    private static final String ARGS_CIPHER_MODE = "args-cipher-mode";

    private OnFragmentInteractionListener listener;
    private FingerprintUiHelper fingerprintUiHelper;

    public FingerprintDialogFragment() {
        // Required empty public constructor
    }

    public static FingerprintDialogFragment newInstance(int cipherMode) {
        FingerprintDialogFragment fragment = new FingerprintDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARGS_CIPHER_MODE, cipherMode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(R.string.fingerprint_dialog_title);
        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);
        View view = inflater.inflate(R.layout.fragment_fingerprint_dialog, container, false);
        fingerprintUiHelper = new FingerprintUiHelper(getContext(), view, this::onFingerprintFinish);
        return view;
    }

    private void onFingerprintFinish(Cipher cipher) {
        if (listener != null)
            if (cipher == null)
                listener.onFingerprintCancel();
            else
                listener.onFingerprintSuccess(cipher);
        dismiss();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            listener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
        fingerprintUiHelper = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        fingerprintUiHelper.start(getArguments().getInt(ARGS_CIPHER_MODE));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (fingerprintUiHelper != null)
            fingerprintUiHelper.stop();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (listener != null)
            listener.onFingerprintCancel();
    }

    public interface OnFragmentInteractionListener {
        void onFingerprintCancel();
        void onFingerprintSuccess(Cipher cipher);
    }
}

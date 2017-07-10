package org.sorz.lab.tinykeepass;

import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.app.Fragment;
import android.os.CancellationSignal;
import android.security.keystore.UserNotAuthenticatedException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import javax.crypto.Cipher;

import static android.content.Context.FINGERPRINT_SERVICE;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link FingerprintDialogFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link FingerprintDialogFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FingerprintDialogFragment extends DialogFragment {
    private static final long ERROR_TIMEOUT_MILLIS = 1500;
    private static final long SUCCESS_TIMEOUT_MILLIS = 300;
    private static final String ARGS_CIPHER_MODE = "args-cipher-mode";

    private OnFragmentInteractionListener listener;
    private ImageView imageFingerprintIcon;
    private TextView textFingerprintStatus;
    private CancellationSignal cancellationSignal;

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
        imageFingerprintIcon = view.findViewById(R.id.imageFingerprintIcon);
        textFingerprintStatus = view.findViewById(R.id.textFingerprintStatus);
        return view;
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
    }

    @Override
    public void onResume() {
        super.onResume();
        Cipher cipher;
        try {
            SecureStringStorage storage = new SecureStringStorage(getContext());
            switch (getArguments().getInt(ARGS_CIPHER_MODE)) {
                case Cipher.ENCRYPT_MODE:
                    cipher = storage.getEncryptCipher();
                    break;
                case Cipher.DECRYPT_MODE:
                    cipher = storage.getDecryptCipher();
                    break;
                default:
                    throw new UnsupportedOperationException("not support such cipher mode");
            }
        } catch (UserNotAuthenticatedException | SecureStringStorage.SystemException e) {
            throw new RuntimeException("cannot get cipher", e);
        }
        FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(cipher);
        FingerprintManager fingerprintManager = (FingerprintManager)
                getContext().getSystemService(FINGERPRINT_SERVICE);
        cancellationSignal = new CancellationSignal();
        fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0,
            new FingerprintManager.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                    Context context = getContext();
                    if (context == null)
                        return;
                    textFingerprintStatus.removeCallbacks(resetErrorTextRunnable);
                    imageFingerprintIcon.setImageResource(R.drawable.ic_fingerprint_success);
                    textFingerprintStatus.setText(R.string.fingerprint_success);
                    textFingerprintStatus.setTextColor(context.getColor(R.color.success));
                    imageFingerprintIcon.postDelayed(() -> {
                        if (listener != null)
                            listener.onFingerprintSuccess(result.getCryptoObject().getCipher());
                        dismiss();
                    }, SUCCESS_TIMEOUT_MILLIS);
                }

                @Override
                public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                    showError(helpString);
                }

                @Override
                public void onAuthenticationFailed() {
                    showError(getContext().getString(R.string.fingerprint_failed));
                }

                @Override
                public void onAuthenticationError(int errMsgId, CharSequence errString) {
                    showError(errString);
                    imageFingerprintIcon.postDelayed(() -> {
                        if (listener != null)
                            listener.onFingerprintCancel();
                        dismiss();
                    }, ERROR_TIMEOUT_MILLIS * 2);
                }
            }, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (cancellationSignal != null)
            cancellationSignal.cancel();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (listener != null)
            listener.onFingerprintCancel();
    }

    private void showError(CharSequence error) {
        Context context = getContext();
        if (context == null)
            return;
        imageFingerprintIcon.setImageResource(R.drawable.ic_fingerprint_error);
        textFingerprintStatus.setText(error);
        textFingerprintStatus.setTextColor(context.getColor(R.color.warning));
        textFingerprintStatus.removeCallbacks(resetErrorTextRunnable);
        textFingerprintStatus.postDelayed(resetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
    }

    private Runnable resetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            Context context = getContext();
            if (context == null)
                return;
            imageFingerprintIcon.setImageResource(R.mipmap.ic_fp_40px);
            textFingerprintStatus.setText(R.string.fingerprint_hint);
            textFingerprintStatus.setTextColor(context.getColor(R.color.hint));
        }
    };

    public interface OnFragmentInteractionListener {
        void onFingerprintCancel();
        void onFingerprintSuccess(Cipher cipher);
    }
}

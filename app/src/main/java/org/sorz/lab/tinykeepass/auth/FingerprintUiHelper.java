package org.sorz.lab.tinykeepass.auth;

import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;
import android.security.keystore.UserNotAuthenticatedException;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.sorz.lab.tinykeepass.R;

import java.security.KeyException;
import java.util.function.Consumer;

import javax.crypto.Cipher;

import static android.content.Context.FINGERPRINT_SERVICE;

/**
 * Maintain fingerprint manager and related UI components.
 */
public class FingerprintUiHelper extends FingerprintManager.AuthenticationCallback {
    private static final long ERROR_TIMEOUT_MILLIS = 1500;
    private static final long SUCCESS_TIMEOUT_MILLIS = 300;

    private final Context context;
    private final ImageView imageFingerprintIcon;
    private final TextView textFingerprintStatus;
    private final Consumer<Cipher> finishHandler;

    private CancellationSignal cancellationSignal;


    /**
     * Create FingerprintUiHelper.
     * @param view must contains imageFingerprintIcon and textFingerprintStatus
     * @param finishHandler authenticated cipher or null if failed.
     */
    public FingerprintUiHelper(Context context, View view, Consumer<Cipher> finishHandler) {
        this.context = context;
        this.finishHandler = finishHandler;
        imageFingerprintIcon = view.findViewById(R.id.imageFingerprintIcon);
        textFingerprintStatus = view.findViewById(R.id.textFingerprintStatus);
        if (imageFingerprintIcon == null || textFingerprintStatus == null)
            throw new IllegalArgumentException("view must contains imageFingerprintIcon " +
                    "and textFingerprintStatus");
    }

    public void start(int cipherMode) throws KeyException {
        Cipher cipher;
        try {
            SecureStringStorage storage = new SecureStringStorage(context);
            switch (cipherMode) {
                case Cipher.ENCRYPT_MODE:
                    cipher = storage.getEncryptCipher();
                    break;
                case Cipher.DECRYPT_MODE:
                    cipher = storage.getDecryptCipher();
                    break;
                default:
                    throw new UnsupportedOperationException("not support such cipher mode");
            }
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException("cannot get cipher", e);
        }
        FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(cipher);
        FingerprintManager fingerprintManager = (FingerprintManager)
                context.getSystemService(FINGERPRINT_SERVICE);
        cancellationSignal = new CancellationSignal();
        fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0, this, null);
    }

    public void stop() {
        if (cancellationSignal != null)
            cancellationSignal.cancel();
        textFingerprintStatus.removeCallbacks(resetErrorTextRunnable);
        imageFingerprintIcon.removeCallbacks(authenticationErrorRunnable);
    }

    private void showError(CharSequence error) {
        imageFingerprintIcon.setImageResource(R.drawable.ic_fingerprint_error);
        textFingerprintStatus.setText(error);
        textFingerprintStatus.setTextColor(context.getColor(R.color.warning));
        textFingerprintStatus.removeCallbacks(resetErrorTextRunnable);
        textFingerprintStatus.postDelayed(resetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        textFingerprintStatus.removeCallbacks(resetErrorTextRunnable);
        imageFingerprintIcon.setImageResource(R.drawable.ic_fingerprint_success);
        textFingerprintStatus.setText(R.string.fingerprint_success);
        textFingerprintStatus.setTextColor(context.getColor(R.color.success));
        imageFingerprintIcon.postDelayed(() ->
                finishHandler.accept(result.getCryptoObject().getCipher()), SUCCESS_TIMEOUT_MILLIS);
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        showError(helpString);
    }

    @Override
    public void onAuthenticationFailed() {
        showError(context.getString(R.string.fingerprint_failed));
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        showError(errString);
        if (!cancellationSignal.isCanceled())
            imageFingerprintIcon.postDelayed(authenticationErrorRunnable, ERROR_TIMEOUT_MILLIS);
    }

    private Runnable resetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            imageFingerprintIcon.setImageResource(R.mipmap.ic_fp_40px);
            textFingerprintStatus.setText(R.string.fingerprint_hint);
            textFingerprintStatus.setTextColor(context.getColor(R.color.hint));
        }
    };

    private Runnable authenticationErrorRunnable = new Runnable() {
        @Override
        public void run() {
            finishHandler.accept(null);
        }
    };

}

package org.sorz.lab.tinykeepass;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Base64;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Created by xierch on 2017/6/29.
 */

public class SecureStringStorage {
    final private static String KEY_ALIAS = "my-key";
    final private static String PREF_PREFIX = "secure-string-";
    final private static String PREF_PREFIX_IV = "secure-string-iv";

    final private SharedPreferences preferences;
    final private KeyStore keyStore;

    public SecureStringStorage(Context context) throws SystemException {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
        } catch (KeyStoreException | CertificateException
                | NoSuchAlgorithmException | IOException e) {
            throw new SystemException(e);
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void generateNewKey(boolean auth, int authSecs) throws SystemException {
        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(auth)
                .setUserAuthenticationValidityDurationSeconds(authSecs)
                .build();

        KeyGenerator keyGenerator = null;
        try {
            keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(keySpec);
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException e) {
            throw new SystemException(e);
        }
        keyGenerator.generateKey();
    }

    private Cipher getCipher(int mode, byte[] iv) throws SystemException,
            UserNotAuthenticatedException {
        try {
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            if (iv != null) {
                GCMParameterSpec params = new GCMParameterSpec(128, iv);
                cipher.init(mode, key, params);
            } else {
                cipher.init(mode, key);
            }
            return cipher;
        } catch (UserNotAuthenticatedException e) {
            throw e;
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidAlgorithmParameterException
                | InvalidKeyException e) {
            throw new SystemException(e);
        }
    }

    public void put(String key, String value) throws SystemException, UserNotAuthenticatedException {
        Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, null);
        cipher.updateAAD(key.getBytes());
        byte[] bytes;
        try {
            bytes = cipher.doFinal(value.getBytes());
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new SystemException(e);
        }
        byte[] ciphertext = Base64.encode(bytes, Base64.DEFAULT);
        byte[] iv = Base64.encode(cipher.getIV(), Base64.DEFAULT);
        preferences.edit()
                .putString(PREF_PREFIX + key, new String(ciphertext))
                .putString(PREF_PREFIX_IV + key, new String(iv))
                .apply();
    }

    public String get(String key) throws SystemException, BadPaddingException,
            IllegalBlockSizeException, UserNotAuthenticatedException {
        String ciphertext = preferences.getString(PREF_PREFIX + key, null);
        String iv = preferences.getString(PREF_PREFIX_IV + key, null);
        if (ciphertext == null || iv == null)
            return null;
        Cipher cipher = getCipher(Cipher.DECRYPT_MODE, Base64.decode(iv, Base64.DEFAULT));
        cipher.updateAAD(key.getBytes());
        byte[] cleartext = cipher.doFinal(Base64.decode(ciphertext, Base64.DEFAULT));
        return new String(cleartext);
    }

    public void remove(String key) {
        preferences.edit()
                .remove(PREF_PREFIX + key)
                .remove(PREF_PREFIX_IV + key)
                .apply();
    }

    public static class SystemException extends Exception {
        public SystemException(Throwable cause) {
            super(cause);
        }
    }
}

package org.sorz.lab.tinykeepass.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

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
    final static private String TAG = SecureStringStorage.class.getName();
    final private static String KEY_ALIAS = "my-key";
    final private static String PREF_VALUE_NAME = "secure-strings";
    final private static String PREF_IV_NAME = "secure-strings-iv";

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

        KeyGenerator keyGenerator;
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

    private Cipher getCipher(int mode, byte[] iv) throws SystemException, KeyException {
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
        } catch (KeyException e) {
            throw e;
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidAlgorithmParameterException e) {
            throw new SystemException(e);
        }
    }

    public Cipher getEncryptCipher() throws KeyException, SystemException {
        return getCipher(Cipher.ENCRYPT_MODE, null);
    }

    public Cipher getDecryptCipher() throws KeyException, SystemException {
        String iv = preferences.getString(PREF_IV_NAME, null);
        if (iv == null)
            return null;
        return getCipher(Cipher.DECRYPT_MODE, Base64.decode(iv, Base64.DEFAULT));
    }

    public void put(Cipher cipher, List<String> strings) throws SystemException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        byte[] ciphertext;
        try {
            for (String s : strings)
                output.writeUTF(s);
            output.flush();
            ciphertext = cipher.doFinal(bytes.toByteArray());
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new SystemException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ciphertext = Base64.encode(ciphertext, Base64.DEFAULT);
        byte[] iv = Base64.encode(cipher.getIV(), Base64.DEFAULT);
        preferences.edit()
                .putString(PREF_VALUE_NAME, new String(ciphertext))
                .putString(PREF_IV_NAME, new String(iv))
                .apply();
    }

    @Nullable
    public List<String> get(Cipher cipher) throws BadPaddingException, IllegalBlockSizeException {
        String ciphertext = preferences.getString(PREF_VALUE_NAME , null);
        if (ciphertext == null)
            return null;
        byte[] cleartext = cipher.doFinal(Base64.decode(ciphertext, Base64.DEFAULT));
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(cleartext));
        List<String> strings =new ArrayList<>();
        try {
            // noinspection InfiniteLoopStatement
            while (true)
                strings.add(input.readUTF());
        } catch (EOFException e) {
            return strings;
        } catch (UTFDataFormatException e) {
            Log.e(TAG,"fail to parse string", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public void clear() {
        preferences.edit()
                .remove(PREF_VALUE_NAME)
                .remove(PREF_IV_NAME)
                .apply();
    }

    public static class SystemException extends Exception {
        SystemException(Throwable cause) {
            super(cause);
        }
    }
}

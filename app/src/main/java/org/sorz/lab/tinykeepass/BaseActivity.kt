package org.sorz.lab.tinykeepass

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT
import android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL
import android.os.Build
import android.os.Handler
import android.security.keystore.UserNotAuthenticatedException
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.preference.PreferenceManager

import org.sorz.lab.tinykeepass.auth.SecureStringStorage
import org.sorz.lab.tinykeepass.keepass.OpenKeePassTask

import java.security.KeyException

import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException

import com.kunzisoft.keepass.database.element.Database


import org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_FINGERPRINT
import org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_NONE
import org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_SCREEN_LOCK
import org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_UNDEFINED
import org.sorz.lab.tinykeepass.DatabaseSetupActivity.PREF_KEY_AUTH_METHOD
import java.util.concurrent.Executor
import android.os.Looper
import androidx.fragment.app.FragmentActivity


private val TAG = MainActivity::class.java.name
private const val REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_READ_KEY = 100
private const val REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_SAVE_KEY = 101
private const val REQUEST_SETUP_DATABASE = 102

abstract class BaseActivity : AppCompatActivity() {
    protected val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceManager.getDefaultSharedPreferences(this)
    }
    private val keyguardManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }
    private val biometricPrompt by lazy(LazyThreadSafetyMode.NONE) {
        BiometricPrompt(this, mainExecutor(), authenticationCallback)
    }
    protected val secureStringStorage by lazy(LazyThreadSafetyMode.NONE) {
        try {
            SecureStringStorage(this)
        } catch (e: SecureStringStorage.SystemException) {
            throw RuntimeException(e)
        }
    }
    private var onKeyRetrieved: ((List<String>) -> Unit)? = null
    private var onKeyAuthFailed: ((String) -> Unit)? = null
    private var onKeySaved: Runnable? = null
    private var keysToEncrypt: List<String>? = null

    private val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationFailed() {
            authFail(getString(R.string.fail_to_auth))
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val cipher = result.cryptoObject!!.cipher!!
            if (onKeyRetrieved != null)
                decryptKey(cipher)
            else if (onKeySaved != null)
                encryptKeys(cipher)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            authFail(errString.toString())
            if (errorCode == BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL || errorCode == BIOMETRIC_ERROR_HW_NOT_PRESENT) {
                // Reconfigure passwords
                val intent = Intent(this@BaseActivity, DatabaseSetupActivity::class.java)
                startActivityForResult(intent, REQUEST_SETUP_DATABASE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_READ_KEY, REQUEST_SETUP_DATABASE ->
                if (resultCode == RESULT_OK) getKey()
                else authFail(getString(R.string.fail_to_auth))
            REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_SAVE_KEY ->
                if (resultCode == RESULT_OK) encryptKeys(null)
                else authFail(getString(R.string.fail_to_auth))
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun authFail(message: String) {
        onKeyAuthFailed?.invoke(message)
        onKeyAuthFailed = null
        onKeySaved = null
        onKeyRetrieved = null
        keysToEncrypt = null
    }


    protected fun getDatabaseKeys(onKeyRetrieved: ((List<String>) -> Unit),
                                  onKeyAuthFailed: ((String) -> Unit)) {
        this.onKeyRetrieved = onKeyRetrieved
        this.onKeyAuthFailed = onKeyAuthFailed
        getKey()
    }

    protected fun saveDatabaseKeys(keys: List<String>, onKeySaved: Runnable,
                                   onKeyAuthFailed: ((String) -> Unit)) {
        this.onKeySaved = onKeySaved
        this.onKeyAuthFailed = onKeyAuthFailed
        saveKey(keys)
    }

    /**
     * Authenticate user, then call [.encryptKeys] to save keys.
     * Finally, either [.onKeySaved] or [.onKeyAuthFailed] will be called.
     */
    private fun saveKey(keys: List<String>) {
        keysToEncrypt = keys
        when (preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)) {
            AUTH_METHOD_NONE -> {
                secureStringStorage.generateNewKey(false, -1)
                encryptKeys(null)
            }
            AUTH_METHOD_SCREEN_LOCK -> {
                secureStringStorage.generateNewKey(true, 60)
                val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                        getString(R.string.auth_key_title),
                        getString(R.string.auth_key_description))
                startActivityForResult(intent, REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_SAVE_KEY)
            }
            AUTH_METHOD_FINGERPRINT -> {
                secureStringStorage.generateNewKey(true, -1)
                val prompt = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.auth_key_title))
                        .setDescription(getString(R.string.auth_key_description))
                        .setNegativeButtonText(getText(android.R.string.cancel))
                        .build()
                val crypto = BiometricPrompt.CryptoObject(secureStringStorage.encryptCipher)
                biometricPrompt.authenticate(prompt, crypto)
            }
        }
    }

    private fun encryptKeys(cipher: Cipher?) {
        try {
            (cipher ?: secureStringStorage.encryptCipher).apply {
                secureStringStorage.put(this, keysToEncrypt)
            }
        } catch (e: UserNotAuthenticatedException) {
            Log.e(TAG, "cannot get cipher from system", e)
            onKeyAuthFailed?.invoke("cannot get cipher from system")
            return
        }

        onKeySaved?.run()
        onKeySaved = null
        onKeyAuthFailed = null
        keysToEncrypt = null
    }

    /**
     * Authenticate user, then call [decryptKey] to get keys.
     * Finally, either [onKeyRetrieved] or [onKeyAuthFailed] will be called.
     */
    private fun getKey() {
        when (preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)) {
            AUTH_METHOD_UNDEFINED -> authFail(getString(R.string.broken_keys))
            AUTH_METHOD_NONE, AUTH_METHOD_SCREEN_LOCK ->
                try {
                    decryptKey(secureStringStorage.decryptCipher)
                } catch (e: UserNotAuthenticatedException) {
                    // should do authentication
                    val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            getString(R.string.auth_key_title),
                            getString(R.string.auth_key_description))
                    startActivityForResult(intent, REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_READ_KEY)
                } catch (e: KeyException) {
                    // Reconfigure passwords
                    val intent = Intent(this, DatabaseSetupActivity::class.java)
                    startActivityForResult(intent, REQUEST_SETUP_DATABASE)
                } catch (e: SecureStringStorage.SystemException) {
                    throw RuntimeException(e)
                }

            AUTH_METHOD_FINGERPRINT -> {
                val prompt = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.auth_key_title))
                        .setDescription(getString(R.string.auth_key_description))
                        .setNegativeButtonText(getText(android.R.string.cancel))
                        .build()
                val crypto = BiometricPrompt.CryptoObject(secureStringStorage.decryptCipher)
                biometricPrompt.authenticate(prompt, crypto)
            }
        }
    }

    private fun decryptKey(cipher: Cipher?) {
        val keys: List<String>?
        try {
            keys = secureStringStorage.get(cipher)
        } catch (e: BadPaddingException) {
            Log.w(TAG, "fail to decrypt keys", e)
            authFail(getString(R.string.fail_to_decrypt))
            return
        } catch (e: IllegalBlockSizeException) {
            Log.w(TAG, "fail to decrypt keys", e)
            authFail(getString(R.string.fail_to_decrypt))
            return
        }

        if (keys == null || keys.size < 2) {
            authFail(getString(R.string.broken_keys))
            return
        }
        onKeyRetrieved?.invoke(keys)
        onKeyAuthFailed = null
        onKeyRetrieved = null
    }

    protected fun openDatabase(masterKey: String, onSuccess: ((Database) -> Unit)) {
        OpenTask(this, masterKey, onSuccess).execute()
    }

    private class OpenTask internal constructor(
            activity: FragmentActivity,  // TODO: memory leaks?
            masterKey: String,
            private val onSuccess: ((Database) -> Unit)
    ) : OpenKeePassTask(activity, masterKey) {

        override fun onPostExecute(db: Database?) {
            super.onPostExecute(db)
            if (db != null)
                onSuccess.invoke(db)
        }
    }
}

fun Context.mainExecutor(): Executor {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        mainExecutor
    } else {
        HandlerExecutor(mainLooper)
    }
}

class HandlerExecutor(looper: Looper) : Executor {
    private val handler = Handler(looper)

    override fun execute(r: Runnable) {
        handler.post(r)
    }
}
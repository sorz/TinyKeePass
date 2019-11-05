package org.sorz.lab.tinykeepass

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.security.keystore.UserNotAuthenticatedException
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.preference.PreferenceManager

import org.sorz.lab.tinykeepass.auth.FingerprintDialogFragment
import org.sorz.lab.tinykeepass.auth.SecureStringStorage
import org.sorz.lab.tinykeepass.keepass.OpenKeePassTask

import java.security.KeyException
import java.util.function.Consumer

import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException

import de.slackspace.openkeepass.domain.KeePassFile

import org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_FINGERPRINT
import org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_NONE
import org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_SCREEN_LOCK
import org.sorz.lab.tinykeepass.DatabaseSetupActivity.AUTH_METHOD_UNDEFINED
import org.sorz.lab.tinykeepass.DatabaseSetupActivity.PREF_KEY_AUTH_METHOD


private val TAG = MainActivity::class.java.name
private const val REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_READ_KEY = 100
private const val REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_SAVE_KEY = 101
private const val REQUEST_SETUP_DATABASE = 102

abstract class BaseActivity : AppCompatActivity(), FingerprintDialogFragment.OnFragmentInteractionListener {
    protected val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceManager.getDefaultSharedPreferences(this)
    }
    private val keyguardManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }
    protected val secureStringStorage by lazy(LazyThreadSafetyMode.NONE) {
        try {
            SecureStringStorage(this)
        } catch (e: SecureStringStorage.SystemException) {
            throw RuntimeException(e)
        }
    }
    private var onKeyRetrieved: Consumer<List<String>>? = null
    private var onKeyAuthFailed: Consumer<String>? = null
    private var onKeySaved: Runnable? = null
    private var keysToEncrypt: List<String>? = null


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_READ_KEY -> {
                if (resultCode == Activity.RESULT_OK) {
                    getKey()
                    encryptKeys(null)
                } else {
                    authFail(getString(R.string.fail_to_auth))
                }
            }
            REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_SAVE_KEY ->
                if (resultCode == Activity.RESULT_OK) encryptKeys(null)
                else authFail(getString(R.string.fail_to_auth))
            REQUEST_SETUP_DATABASE ->
                if (resultCode == Activity.RESULT_OK) getKey()
                else authFail(getString(R.string.fail_to_decrypt))
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun authFail(message: String) {
        onKeyAuthFailed?.accept(message)
        onKeyAuthFailed = null
        onKeySaved = null
        onKeyRetrieved = null
        keysToEncrypt = null
    }

    override fun onFingerprintCancel() {
        authFail(getString(R.string.fail_to_auth))
    }

    override fun onFingerprintSuccess(cipher: Cipher) {
        if (onKeyRetrieved != null)
            decryptKey(cipher)
        else if (onKeySaved != null)
            encryptKeys(cipher)
    }

    protected fun getDatabaseKeys(onKeyRetrieved: Consumer<List<String>>,
                                  onKeyAuthFailed: Consumer<String>) {
        this.onKeyRetrieved = onKeyRetrieved
        this.onKeyAuthFailed = onKeyAuthFailed
        getKey()
    }

    protected fun saveDatabaseKeys(keys: List<String>, onKeySaved: Runnable,
                                   onKeyAuthFailed: Consumer<String>) {
        this.onKeySaved = onKeySaved
        this.onKeyAuthFailed = onKeyAuthFailed
        saveKey(keys)
    }

    override fun onKeyException(e: KeyException) {
        // Key is invalided, have to reconfigure passwords.
        val intent = Intent(this, DatabaseSetupActivity::class.java)
        startActivityForResult(intent, REQUEST_SETUP_DATABASE)
    }

    /**
     * Authenticate user, then call [.encryptKeys] to save keys.
     * Finally, either [.onKeySaved] or [.onKeyAuthFailed] will be called.
     */
    private fun saveKey(keys: List<String>) {
        keysToEncrypt = keys
        val authMethod = preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)
        try {
            when (authMethod) {
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
                    supportFragmentManager.beginTransaction()
                            .add(FingerprintDialogFragment.newInstance(Cipher.ENCRYPT_MODE),
                                    "fingerprint")
                            .commit()
                }
            }
        } catch (e: SecureStringStorage.SystemException) {
            throw RuntimeException("cannot generate new key", e)
        }

    }

    private fun encryptKeys(cipher: Cipher?) {
        try {
            cipher ?: secureStringStorage.encryptCipher.apply {
                secureStringStorage.put(this, keysToEncrypt)
            }
        } catch (e: UserNotAuthenticatedException) {
            Log.e(TAG, "cannot get cipher from system", e)
            onKeyAuthFailed?.accept("cannot get cipher from system")
            return
        } catch (e: SecureStringStorage.SystemException) {
            throw RuntimeException("cannot get save keys", e)
        } catch (e: KeyException) {
            throw RuntimeException("cannot get save keys", e)
        }

        onKeySaved?.run()
        onKeySaved = null
        onKeyAuthFailed = null
        keysToEncrypt = null
    }

    /**
     * Authenticate user, then call [.decryptKey] to get keys.
     * Finally, either [.onKeyRetrieved] or [.onKeyAuthFailed] will be called.
     */
    private fun getKey() {
        when (preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)) {
            AUTH_METHOD_UNDEFINED -> authFail(getString(R.string.broken_keys))
            AUTH_METHOD_NONE, AUTH_METHOD_SCREEN_LOCK -> try {
                decryptKey(secureStringStorage.decryptCipher)
            } catch (e: UserNotAuthenticatedException) {
                // should do authentication
                val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                        getString(R.string.auth_key_title),
                        getString(R.string.auth_key_description))
                startActivityForResult(intent, REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_READ_KEY)
            } catch (e: KeyException) {
                onKeyException(e)
            } catch (e: SecureStringStorage.SystemException) {
                throw RuntimeException(e)
            }

            AUTH_METHOD_FINGERPRINT -> supportFragmentManager.beginTransaction()
                    .add(FingerprintDialogFragment.newInstance(Cipher.DECRYPT_MODE),
                            "fingerprint")
                    .commit()
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
        onKeyRetrieved?.accept(keys)
        onKeyAuthFailed = null
        onKeyRetrieved = null
    }

    protected fun openDatabase(masterKey: String, onSuccess: Consumer<KeePassFile>) {
        OpenTask(this, masterKey, onSuccess).execute()
    }

    private class OpenTask internal constructor(
            activity: Activity,  // TODO: memory leaks?
            masterKey: String,
            private val onSuccess: Consumer<KeePassFile>
    ) : OpenKeePassTask(activity, masterKey) {

        override fun onPostExecute(db: KeePassFile?) {
            super.onPostExecute(db)
            if (db != null)
                onSuccess.accept(db)
        }
    }
}

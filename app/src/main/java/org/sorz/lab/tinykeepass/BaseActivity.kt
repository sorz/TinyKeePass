package org.sorz.lab.tinykeepass

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.security.keystore.UserNotAuthenticatedException
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_HW_NOT_PRESENT
import androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
import androidx.preference.PreferenceManager

import org.sorz.lab.tinykeepass.auth.SecureStringStorage
import org.sorz.lab.tinykeepass.keepass.OpenKeePassTask

import java.security.KeyException

import javax.crypto.Cipher

import com.kunzisoft.keepass.database.element.Database

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.Exception
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


private val TAG = MainActivity::class.java.name
private const val REQUEST_CONFIRM_DEVICE_CREDENTIAL_FOR_SAVE_KEY = 101

abstract class BaseActivity : AppCompatActivity() {
    protected val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceManager.getDefaultSharedPreferences(this)
    }
    private val keyguardManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }
    private lateinit var biometricPrompt: BiometricPrompt
    protected val secureStringStorage by lazy(LazyThreadSafetyMode.NONE) {
        try {
            SecureStringStorage(this)
        } catch (e: SecureStringStorage.SystemException) {
            throw RuntimeException(e)
        }
    }
    private val setupDatabase = registerForActivityResult(StartActivityForResult()) {
        // if (it.resultCode == RESULT_OK) getKey()
        // TODO
    }
    private var onKeyAuthFailed: ((String) -> Unit)? = null
    private var onKeySaved: Runnable? = null
    private var keysToEncrypt: List<String>? = null

    private var biometricAuthResult: Continuation<Cipher>? = null


    private fun launchSetupDatabase() {
        val intent = Intent(this@BaseActivity, DatabaseSetupActivity::class.java)
        setupDatabase.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("!!!! BiometricPrompt inited")
        biometricPrompt = BiometricPrompt(this, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                biometricAuthResult?.resume(result.cryptoObject!!.cipher!!)
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                biometricAuthResult?.resumeWithException(BiometricAuthError(code, msg.toString()))
                if (code == ERROR_NO_DEVICE_CREDENTIAL || code == ERROR_HW_NOT_PRESENT) {
                    // Reconfigure passwords
                    launchSetupDatabase()
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
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
        keysToEncrypt = null
    }

    protected suspend fun getDatabaseKeys(): List<String> {
        val cipher = when (preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)) {
            AUTH_METHOD_NONE, AUTH_METHOD_SCREEN_LOCK ->
                try {
                    return decryptKey(secureStringStorage.decryptCipher)
                } catch (e: UserNotAuthenticatedException) {
                    // should do authentication
                    authenticateCipher(secureStringStorage.decryptCipher, true)
                } catch (e: KeyException) {
                    // Reconfigure passwords
                    launchSetupDatabase()
                    throw GetKeyError(getString(R.string.broken_keys))
                } catch (e: SecureStringStorage.SystemException) {
                    throw RuntimeException(e)
                }
            AUTH_METHOD_FINGERPRINT -> {
                authenticateCipher(secureStringStorage.decryptCipher, false)
            }
            else -> throw GetKeyError(getString(R.string.broken_keys))
        }
        return decryptKey(cipher)
    }

    private suspend fun authenticateCipher(cipher: Cipher, useDeviceCredential: Boolean): Cipher {
        val prompt = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.auth_key_title))
                .setDescription(getString(R.string.auth_key_description))
                .setNegativeButtonText(getText(android.R.string.cancel))
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(
                    if (useDeviceCredential) BIOMETRIC_WEAK or DEVICE_CREDENTIAL
                    else BIOMETRIC_STRONG
                )
                .build()
        val crypto = BiometricPrompt.CryptoObject(cipher)
        return suspendCancellableCoroutine { cont ->
            biometricAuthResult = cont
            biometricPrompt.authenticate(prompt, crypto)
        }
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

    private fun decryptKey(cipher: Cipher?): List<String> {
        val keys = try {
            secureStringStorage.get(cipher)
        } catch (e: Exception) {
            Log.w(TAG, "fail to decrypt keys", e)
            throw GetKeyError(getString(R.string.fail_to_decrypt))
        }
        if (keys == null || keys.size < 2)
            throw GetKeyError(getString(R.string.broken_keys))
        return keys
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

class BiometricAuthError(val code: Int, message: String) : GetKeyError(message)
open class GetKeyError(message: String) : Exception(message)

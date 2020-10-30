package org.sorz.lab.tinykeepass

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

import javax.crypto.Cipher
import java.security.KeyException
import java.lang.Exception

import kotlinx.coroutines.suspendCancellableCoroutine
import org.sorz.lab.tinykeepass.keepass.OpenDatabaseError
import org.sorz.lab.tinykeepass.keepass.openKeePassTask
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


private val TAG = MainActivity::class.java.name

abstract class BaseActivity : AppCompatActivity() {
    protected val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceManager.getDefaultSharedPreferences(this)
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
    private var biometricAuthResult: Continuation<Cipher>? = null


    private fun launchSetupDatabase() {
        val intent = Intent(this@BaseActivity, DatabaseSetupActivity::class.java)
        setupDatabase.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricPrompt = BiometricPrompt(this, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                biometricAuthResult?.resume(result.cryptoObject!!.cipher!!)
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                biometricAuthResult?.resumeWithException(AuthKeyError(msg.toString()))
                if (code == ERROR_NO_DEVICE_CREDENTIAL || code == ERROR_HW_NOT_PRESENT) {
                    // Reconfigure passwords
                    launchSetupDatabase()
                }
            }
        })
    }

    protected suspend fun getDatabaseKeys(): List<String> {
        val decryptCipher = secureStringStorage.decryptCipher
        val cipher = when (preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)) {
            AUTH_METHOD_NONE, AUTH_METHOD_SCREEN_LOCK ->
                try {
                    return decryptKey(decryptCipher)
                } catch (e: UserNotAuthenticatedException) {
                    // should do authentication
                    authenticateCipher(decryptCipher, true)
                } catch (e: KeyException) {
                    // Reconfigure passwords
                    launchSetupDatabase()
                    throw AuthKeyError(getString(R.string.broken_keys))
                } catch (e: SecureStringStorage.SystemException) {
                    throw RuntimeException(e)
                }
            AUTH_METHOD_FINGERPRINT -> authenticateCipher(decryptCipher, false)
            else -> throw AuthKeyError(getString(R.string.broken_keys))
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

    suspend fun saveDatabaseKeys(keys: List<String>) {
        val encryptCipher = secureStringStorage.encryptCipher
        val cipher = when (preferences.getInt(PREF_KEY_AUTH_METHOD, AUTH_METHOD_UNDEFINED)) {
            AUTH_METHOD_NONE -> {
                secureStringStorage.generateNewKey(false, -1)
                return encryptKeys(encryptCipher, keys)
            }
            AUTH_METHOD_SCREEN_LOCK -> {
                secureStringStorage.generateNewKey(true, 60)
                authenticateCipher(encryptCipher, true)
            }
            AUTH_METHOD_FINGERPRINT -> {
                secureStringStorage.generateNewKey(true, -1)
                authenticateCipher(encryptCipher, false)
            }
            else -> throw AuthKeyError(getString(R.string.broken_keys))
        }
        encryptKeys(cipher, keys)
    }

    private fun encryptKeys(cipher: Cipher, keys: List<String>) {
        try {
            secureStringStorage.put(cipher, keys)
        } catch (e: UserNotAuthenticatedException) {
            Log.e(TAG, "cannot get cipher from system", e)
            throw AuthKeyError("cannot get cipher from system")
        }
    }

    private fun decryptKey(cipher: Cipher): List<String> {
        val keys = try {
            secureStringStorage.get(cipher)
        } catch (e: Exception) {
            Log.w(TAG, "fail to decrypt keys", e)
            throw AuthKeyError(getString(R.string.fail_to_decrypt))
        }
        if (keys == null || keys.size < 2)
            throw AuthKeyError(getString(R.string.broken_keys))
        return keys
    }

    @Throws(AuthKeyError::class, OpenDatabaseError::class)
    protected suspend fun openDatabase() {
        val keys = getDatabaseKeys()
        openKeePassTask(this, keys[0])
    }
}

open class AuthKeyError(message: String) : Exception(message)

package org.sorz.lab.tinykeepass.auth

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.settingsDataStore
import java.io.IOException
import java.lang.IllegalArgumentException
import java.security.GeneralSecurityException
import java.security.KeyStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SecureStorage"
private const val USER_AUTH_VALID_SECS = 10 // TODO: allow custom user auth valid duration

class SecureStorage(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val prefsFileName: String = "secret_shared_prefs",
    private val keyAlias: String = "keepass-test-key"
) {
    private var userAuthResult: Continuation<BiometricPrompt.AuthenticationResult>? = null
    private val userAuthCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            userAuthResult?.resume(result)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Log.i(TAG, "user auth error $errorCode: $errString")
            userAuthResult?.resumeWithException(UserAuthException(errorCode, errString.toString()))
        }
    }

    @Throws(KeyStoreException::class)
    private suspend fun getKeyStore(): KeyStore = withContext(ioDispatcher) {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }
        } catch (err: Exception) {
            throw KeyStoreException(err)
        }
    }

    @Throws(MasterKeyException::class)
    private suspend fun getOrGenerateMasterKey(): MasterKey {
        val userAuthRequired = context.settingsDataStore.data.first().userAuthRequired
        val strongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        Log.d(TAG, "strong box: $strongBox")
        return try {
            withContext(ioDispatcher) {
                MasterKey.Builder(context, keyAlias)
                    .setRequestStrongBoxBacked(strongBox)
                    .setUserAuthenticationRequired(userAuthRequired, USER_AUTH_VALID_SECS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            }
        } catch (err: GeneralSecurityException) {
            throw MasterKeyException(err)
        } catch (err: IllegalArgumentException) {
            throw MasterKeyException(err)
        } catch (err: IOException) {
            throw MasterKeyException(err)
        }
    }

    @Throws(KeyStoreException::class, MasterKeyException::class)
    suspend fun generateNewMasterKey(): MasterKey {
        getKeyStore().apply {
            if (containsAlias(keyAlias))
                deleteEntry(keyAlias)
        }
        withContext(ioDispatcher) {
            context.deleteSharedPreferences(prefsFileName)
        }
        return getOrGenerateMasterKey()
    }

    @Throws(KeyStoreException::class, UserAuthException::class)
    suspend fun getExistingMasterKey(): MasterKey {
        return getOrGenerateMasterKey()
    }

    @Throws(KeyStoreException::class, UserAuthException::class)
    private suspend fun authUserForMasterKey(masterKey: MasterKey) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.auth_key_title))
            .setDescription(context.getString(R.string.auth_key_description))
            .setNegativeButtonText(context.getText(android.R.string.cancel))
            .build()
        val prompt = BiometricPrompt(context as FragmentActivity, userAuthCallback)
        try {
            suspendCancellableCoroutine<BiometricPrompt.AuthenticationResult> { cont ->
                userAuthResult = cont
                prompt.authenticate(promptInfo)
            }
        } catch (err: CancellationException) {
            prompt.cancelAuthentication()
            throw err
        }
    }

    @Throws(KeyStoreException::class, UserAuthException::class)
    suspend fun getEncryptedPreferences(masterKey: MasterKey): SharedPreferences {
        val getPrefs = suspend {
            withContext(Dispatchers.IO) {
                EncryptedSharedPreferences.create(
                    context, prefsFileName, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }
        }
        return try {
            getPrefs()
        } catch (err: java.security.KeyStoreException) {
            if (err.cause is UserNotAuthenticatedException) {
                authUserForMasterKey(masterKey)
                getPrefs()
            } else {
                throw err
            }
        }
    }

    suspend fun clear() {
        withContext(ioDispatcher) {
            context.deleteSharedPreferences(prefsFileName)
        }
        try {
            getKeyStore().apply {
                if (containsAlias(keyAlias))
                    deleteEntry(keyAlias)
            }
        } catch (err: KeyStoreException) {
            Log.w(TAG, "Fail to delete master key", err)
        }
    }
}

class UserAuthException(val code: Int, message: String): Exception(message)
open class SystemException(throwable: Throwable): Exception(throwable)
class KeyStoreException(throwable: Throwable): SystemException(throwable)
class MasterKeyException(throwable: Throwable): SystemException(throwable)

package org.sorz.lab.tinykeepass.auth

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.sorz.lab.tinykeepass.settingsDataStore
import java.io.IOException
import java.lang.IllegalArgumentException
import java.security.GeneralSecurityException
import java.security.KeyStore

private const val TAG = "SecureStorage"
private const val USER_AUTH_VALID_SECS = 10 // TODO: allow custom user auth valid duration

class SecureStorage(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val prefsFileName: String = "secret_shared_prefs",
    private val keyAlias: String = "keepass-test-key"
) {
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
    suspend fun generateMasterKey(): MasterKey {
        getKeyStore().apply {
            if (containsAlias(keyAlias))
                deleteEntry(keyAlias)
        }
        withContext(ioDispatcher) {
            context.deleteSharedPreferences(prefsFileName)
        }
        return getOrGenerateMasterKey()
    }

    suspend fun getEncryptedPreferences(masterKey: MasterKey): SharedPreferences =
        withContext(Dispatchers.IO) {
            EncryptedSharedPreferences.create(
                context, prefsFileName, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
}

open class SystemException(throwable: Throwable): Exception(throwable)
class KeyStoreException(throwable: Throwable): SystemException(throwable)
class MasterKeyException(throwable: Throwable): SystemException(throwable)

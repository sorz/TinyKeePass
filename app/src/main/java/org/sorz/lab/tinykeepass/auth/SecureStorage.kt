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

private const val TAG = "SecureStorage"

class SecureStorage(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val prefsFileName: String = "secret_shared_prefs",
) {
    @Throws(MasterKeyGenerateException::class)
    suspend fun getMasterKey(): MasterKey {
        val userAuthRequired = context.settingsDataStore.data.first().userAuthRequired
        val strongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        Log.d(TAG, "strong box: $strongBox")
        val masterKey = try {
            withContext(ioDispatcher) {
                MasterKey.Builder(context)
                    .setRequestStrongBoxBacked(strongBox)
                    .setUserAuthenticationRequired(userAuthRequired)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            }
        } catch (err: GeneralSecurityException) {
            throw MasterKeyGenerateException(err)
        } catch (err: IllegalArgumentException) {
            throw MasterKeyGenerateException(err)
        } catch (err: IOException) {
            throw MasterKeyGenerateException(err)
        }
        return masterKey
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

class MasterKeyGenerateException(throwable: Throwable): Exception(throwable)
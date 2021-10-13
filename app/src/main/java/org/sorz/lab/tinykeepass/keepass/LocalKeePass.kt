package org.sorz.lab.tinykeepass.keepass

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext


private const val KEY_MASTER_PASSWORD = "local-keepass-master-password"


class LocalKeePass(
    val masterPassword: String,
) {
    suspend fun writeToPrefs(
        prefs: SharedPreferences,
        ioDispatcher: CoroutineContext = Dispatchers.IO
    ) = withContext(ioDispatcher) {
        prefs.edit()
            .putString(KEY_MASTER_PASSWORD, masterPassword)
            .commit()
    }

    override fun toString(): String = "LocalKeePass(masterPassword=***)"

    companion object {
        fun loadFromPrefs(prefs: SharedPreferences): LocalKeePass? {
            val pwd = prefs.getString(KEY_MASTER_PASSWORD, null)
            return if (pwd.isNullOrEmpty()) null
            else LocalKeePass(pwd)
        }
    }

}
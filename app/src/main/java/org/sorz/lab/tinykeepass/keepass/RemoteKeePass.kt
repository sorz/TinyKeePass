package org.sorz.lab.tinykeepass.keepass

import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext


private const val KEY_URI = "remote-keepass-uri"
private const val KEY_MASTER_PASSWORD = "remote-keepass-master-password"
private const val KEY_HTTP_USERNAME = "remote-keepass-http-username"
private const val KEY_HTTP_PASSWORD = "remote-keepass-http-password"

data class HttpAuth(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "HttpAuth(username=$username, password=***)"
}

data class RemoteKeePass(
    val uri: Uri,
    val masterPassword: String,
    val httpAuth: HttpAuth?
) {
    suspend fun writeToPrefs(
        prefs: SharedPreferences,
        ioDispatcher: CoroutineContext = Dispatchers.IO
    ) = withContext(ioDispatcher) {
        prefs.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_MASTER_PASSWORD, masterPassword)
            .putString(KEY_HTTP_USERNAME, httpAuth?.username)
            .putString(KEY_HTTP_PASSWORD, httpAuth?.password)
            .commit()
    }

    override fun toString(): String =
        "RemoteKeePass(uri=$uri, masterPassword=*** httpAuth=$httpAuth)"

    companion object {
        fun loadFromPrefs(prefs: SharedPreferences): RemoteKeePass? {
            val httpUsername = prefs.getString(KEY_HTTP_USERNAME, null)
            val httpPassword = prefs.getString(KEY_HTTP_PASSWORD, null)
            val httpAuth =
                if (httpUsername.isNullOrEmpty() || httpPassword.isNullOrEmpty()) null
                else HttpAuth(httpUsername, httpPassword)
            val uri = prefs.getString(KEY_URI, null)
            val pwd = prefs.getString(KEY_MASTER_PASSWORD, null)
            return if (uri.isNullOrEmpty() || pwd.isNullOrEmpty()) null
                else RemoteKeePass(Uri.parse(uri), pwd, httpAuth)
        }
    }
}
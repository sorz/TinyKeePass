package org.sorz.lab.tinykeepass

import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.kunzisoft.keepass.database.element.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sorz.lab.tinykeepass.keepass.KeePassStorage
import java.io.File
import java.io.IOException
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URL
import java.util.*


const val DB_FILENAME: String = "database.kdbx"
private const val TAG = "FetchDatabase"

data class BasicAuthCfg(
        val enabled: Boolean = false,
        val username: String = "",
        val password: String = "",
) {
    val isValid get() = !enabled || (username != "" && password != "")
    val authenticator get() = PasswordAuthentication(username, password.toCharArray())
}

suspend fun fetchDatabase(
        context: Context,
        path: Uri,
        masterPwd: String,
        basicAuth: BasicAuthCfg,
) {
    val isHttpOrHttps = path.scheme?.toLowerCase(Locale.US)?.startsWith("http") == true
    val tempDbFile = File(context.cacheDir, DB_FILENAME)
    val dbFile = File(context.noBackupFilesDir, DB_FILENAME)

    // Setup HTTP Basic Auth
    if (isHttpOrHttps && basicAuth.enabled) {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                return if (requestingURL.authority != path.authority) null
                else basicAuth.authenticator
            }
        })
    }

    // Download file
    withContext(Dispatchers.IO) {
        if (isHttpOrHttps) {
            URL(path.toString()).openStream().buffered()
        } else {
            context.contentResolver.run {
                takePersistableUriPermission(path, FLAG_GRANT_READ_URI_PERMISSION)
                openInputStream(path)?.buffered() ?: throw IOException("provider crashed")
            }
        }.use { input ->
            tempDbFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }
    }

    // Open database
    val db = Database.Companion.getInstance()
    if (db.loaded) db.closeAndClear(null)
    withContext(Dispatchers.IO) {
        db.loadData(tempDbFile.toUri(), masterPwd, null, true,
                context.contentResolver, context.cacheDir, true, null)
    }
    Log.d(TAG, "Database opened, name: " + db.name)

    // Move database file
    if (!tempDbFile.renameTo(dbFile)) {
        withContext(Dispatchers.IO) {
            tempDbFile.copyTo(dbFile, true)
            if (!tempDbFile.delete())
                tempDbFile.deleteOnExit()
        }
    }
    KeePassStorage.set(context, db)
}
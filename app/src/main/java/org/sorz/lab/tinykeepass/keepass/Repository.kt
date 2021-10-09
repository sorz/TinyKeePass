package org.sorz.lab.tinykeepass.keepass

import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.MutableState
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.database.exception.LoadDatabaseException
import com.kunzisoft.keepass.icons.IconDrawableFactory
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.features.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.sorz.lab.tinykeepass.auth.SecureStorage
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

private const val TAG = "Repository"

enum class DatabaseState {
    UNCONFIGURED, LOCKED, UNLOCKED,
}

interface Repository{
    val databaseState: StateFlow<DatabaseState>
    val databaseEntries: StateFlow<List<Entry>>
    val databaseName: StateFlow<String>
    val iconFactory: IconDrawableFactory
    suspend fun unlockDatabase(local: LocalKeePass)
    suspend fun syncDatabase(remote: RemoteKeePass)
    suspend fun lockDatabase()
    suspend fun clearDatabase()
}

class RealRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cacheDir: File = context.cacheDir,
    private val filesDir: File = context.noBackupFilesDir,
    private val databaseFilename: String = "database.kdbx",
    private val database: Database = Database.getInstance(),
) : Repository {
    private val persistentDbFile = File(filesDir, databaseFilename)
    private val state = MutableStateFlow(if (persistentDbFile.isFile) DatabaseState.LOCKED else DatabaseState.UNCONFIGURED)
    private val entries = MutableStateFlow<List<Entry>>(listOf())
    private val name = MutableStateFlow("")

    override val databaseState = state
    override val databaseEntries: StateFlow<List<Entry>> = entries
    override val databaseName: StateFlow<String> = name
    override val iconFactory: IconDrawableFactory get() = database.drawFactory

    @Throws(LoadDatabaseException::class)
    override suspend fun unlockDatabase(local: LocalKeePass) {
        Log.d(TAG, "Unlocking database")
        reloadDatabase(persistentDbFile, local.masterPassword)
    }

    @Throws(IOException::class)
    override suspend fun syncDatabase(remote: RemoteKeePass) {
        Log.d(TAG, "Syncing database")
        val tempDbFile = File(cacheDir, databaseFilename)

        // Copy file from uri to temp dir
        if (remote.uri.scheme?.startsWith("http", true) == true) {
            val client = HttpClient(Android) {
                remote.httpAuth?.let { auth ->
                    install(Auth) {
                        basic {
                            credentials {
                                BasicAuthCredentials(auth.username, auth.password)
                            }
                            sendWithoutRequest { request ->
                                request.url.host == remote.uri.host
                            }
                        }
                    }
                }
            }
            val resp = try {
                client.get<HttpResponse>(remote.uri.toString())
            } catch (err: ResponseException) {
                throw IOException("got response: ${err.response.status}")
            }
            resp.content.toInputStream()
        } else {
            val resolver = context.contentResolver
            resolver
                .runCatching { takePersistableUriPermission(remote.uri, FLAG_GRANT_READ_URI_PERMISSION) }
                .onFailure { Log.w(TAG, "cannot take persistable permission", it) }
                // Ignore it, we can still read it once w/o syncing in future
            try {
                withContext(ioDispatcher) {
                    resolver.openInputStream(remote.uri) ?:
                    throw IOException("content provider crashed")
                }
            } catch (err: SecurityException) {
                throw IOException("cannot open from provider", err)
            }
        }.use { input ->
            // Copy to temp file
            tempDbFile.deleteOnExit()
            tempDbFile.outputStream().use { output ->
                copyStream(input, output, ioDispatcher)
            }
        }

        // Try to open db in temp dir
        try {
            reloadDatabase(tempDbFile, remote.masterPassword)
        } catch (err: LoadDatabaseException) {
            throw IOException("fail to unlock database", err)
        }

        // Move db from temp dir
        if (!tempDbFile.renameTo(persistentDbFile)) {
            // Fallback to copy stream
            tempDbFile.inputStream().use { input ->
                persistentDbFile.outputStream().use { output ->
                    copyStream(input, output)
                }
            }
        }
        tempDbFile.delete()
    }

    override suspend fun lockDatabase() {
        Log.d(TAG, "Locking database")
        database.closeAndClear()
        entries.value = listOf()
        name.value = ""
        state.value = DatabaseState.LOCKED
    }

    override suspend fun clearDatabase() {
        Log.d(TAG, "Clearing database")
        database.closeAndClear()
        entries.value = listOf()
        name.value = ""
        state.value = DatabaseState.UNCONFIGURED
        // Remove configures
        SecureStorage(context, ioDispatcher).clear()
        // Delete database file
        withContext(ioDispatcher) {
            if (!persistentDbFile.delete())
                persistentDbFile.deleteOnExit()
        }
    }

    @Throws(LoadDatabaseException::class)
    private suspend fun reloadDatabase(file: File, password: String) {
        try {
            if (database.loaded) database.closeAndClear()
            withContext(ioDispatcher) {
                database.loadData(
                    uri = Uri.fromFile(file),
                    password = password,
                    keyfile = null,
                    readOnly = true,
                    contentResolver = context.contentResolver,
                    cacheDirectory = context.cacheDir,
                    fixDuplicateUUID = true,
                    progressTaskUpdater = null,
                )
                name.value = database.name
                entries.value = database.allEntriesNotInRecycleBin
                    .sortedBy { it.creationTime.date }
                    .sortedBy { it.url }
                    .sortedBy { it.username }
                    .sortedBy { it.title }
                    .toList()
            }
            state.value = DatabaseState.UNLOCKED
        } catch (err: Exception) {
            lockDatabase()
            throw err
        }
    }
}

private suspend fun copyStream(
    input: InputStream,
    output: OutputStream,
    dispatcher: CoroutineContext = Dispatchers.IO,
) = withContext(dispatcher) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytes = input.read(buffer)
    while (bytes >= 0) {
        ensureActive()
        output.write(buffer, 0, bytes)
        ensureActive()
        bytes = input.read(buffer)
    }
}

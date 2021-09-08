package org.sorz.lab.tinykeepass.keepass

import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.util.Log
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.exception.LoadDatabaseException
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
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

private const val TAG = "Repository"

enum class DatabaseState {
    UNCONFIGURED, LOCKED, UNLOCKED,
}

data class HttpAuth(
    val username: String,
    val password: String,
)

interface Repository{
    val databaseState: StateFlow<DatabaseState>

    suspend fun unlockDatabase()
    suspend fun syncDatabase(
        uri: Uri,
        masterPassword: String,
        httpAuth: HttpAuth? = null
    )
    suspend fun cleanDatabase()
}


class RealRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cacheDir: File = context.cacheDir,
    private val filesDir: File = context.noBackupFilesDir,
    private val databaseFilename: String = "database.kdbx",
    private val database: Database = Database.getInstance(),
) : Repository {
    private val state = MutableStateFlow(DatabaseState.UNCONFIGURED)

    override val databaseState = state

    override suspend fun unlockDatabase() {
        state.value = DatabaseState.UNLOCKED
    }

    @Throws(IOException::class)
    override suspend fun syncDatabase(
        uri: Uri,
        masterPassword: String,
        httpAuth: HttpAuth?,
    ) {
        val tempDbFile = File(cacheDir, databaseFilename)
        val persistentDbFile = File(filesDir, databaseFilename)

        // Copy file from uri to temp dir
        if (uri.scheme?.startsWith("http", true) == true) {
            val client = HttpClient(Android) {
                httpAuth?.let { auth ->
                    install(Auth) {
                        basic {
                            credentials {
                                BasicAuthCredentials(auth.username, auth.password)
                            }
                            sendWithoutRequest { request ->
                                request.url.host == uri.host
                            }
                        }
                    }
                }
            }
            val resp = try {
                client.get<HttpResponse>(uri.toString())
            } catch (err: ResponseException) {
                throw IOException("got response: ${err.response.status}")
            }
            resp.content.toInputStream()
        } else {
            val resolver = context.contentResolver
            resolver
                .runCatching { takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION) }
                .onFailure { Log.w(TAG, "cannot take persistable permission", it) }
                // Ignore it, we can still read it once w/o syncing in future
            try {
                withContext(ioDispatcher) {
                    resolver.openInputStream(uri) ?:
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
            loadDatabase(tempDbFile, masterPassword)
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

    override suspend fun cleanDatabase() {
        database.closeAndClear()
        state.value = DatabaseState.UNCONFIGURED
    }

    @Throws(LoadDatabaseException::class)
    private suspend fun loadDatabase(file: File, password: String) {
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
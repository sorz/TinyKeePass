package org.sorz.lab.tinykeepass.database

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.GeneralSecurityException
import java.util.zip.DeflaterInputStream
import java.util.zip.DeflaterOutputStream

private const val DB_FILENAME: String = "database.enc"
private const val DB_VERSION: Int = 1

class Database(
    val context: Context,
) {
    private val storageFile = File(context.noBackupFilesDir, DB_FILENAME)
    val entries: MutableList<Entry> = mutableListOf()

    private fun getEncryptedFile(masterKey: MasterKey) = EncryptedFile.Builder(
        context,
            storageFile,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    @Throws(GeneralSecurityException::class, IOException::class)
    suspend fun readIn(masterKey: MasterKey) {
        val file = getEncryptedFile(masterKey)

        val db = withContext(Dispatchers.IO) {
            ObjectInputStream(DeflaterInputStream(file.openFileInput())).use { input ->
                if (input.readInt() != DB_VERSION)
                    throw IOException("Mismatched version of database")
                input.readObject() as DatabaseInFile
            }
        }

        entries.clear()
        entries.addAll(db.entries)
    }

    @Throws(IOException::class)
    suspend fun writeOut(masterKey: MasterKey) {
        val file = getEncryptedFile(masterKey)
        withContext(Dispatchers.IO) {
            ObjectOutputStream(DeflaterOutputStream(file.openFileOutput())).use { output ->
                output.writeInt(DB_VERSION)
                output.writeObject(DatabaseInFile(entries))
            }
        }
    }
}

private data class DatabaseInFile(
    val entries: List<Entry>,
)

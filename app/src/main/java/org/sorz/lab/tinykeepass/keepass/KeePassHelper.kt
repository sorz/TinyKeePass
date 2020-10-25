package org.sorz.lab.tinykeepass.keepass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log


import org.sorz.lab.tinykeepass.FetchDatabaseTask

import java.io.File

import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.Entry
import java.util.logging.Logger
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

/**
 * Stream of all entries without ones in recycle bin.
 */
val Database.allEntriesNotInRecycleBin: Sequence<Entry>?
    get() =
    if (isRecycleBinEnabled) {
            val recycleBinUuid = recycleBin?.nodeId
            rootGroup?.getChildEntries()?.stream()?.filter {
                it.parent?.nodeId != recycleBinUuid
            }?.asSequence()

        } else {
            rootGroup?.getChildEntries()?.stream()?.asSequence()
        }


val Database.allEntriesNotInRecycleBinStream: Stream<Entry>?
    get() =
    allEntriesNotInRecycleBin?.asStream()


val Context.databaseFile: File get() =
    File(noBackupFilesDir, FetchDatabaseTask.DB_FILENAME)

val Context.hasDatabaseConfigured: Boolean get() =
    KeePassStorage.get(this) != null || databaseFile.canRead()

// related files: AutofillUtils.kt
// FIXME: keepassdx implements its own icon data type, data can be null and cause npe..
val tmpImage: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
fun Entry.getIconDrawable(context: Context): Drawable = BitmapDrawable(context.resources, tmpImage)

private val Entry.cleanUrl get() = url.replace("^https?://(www\\.)?".toRegex(), "")

val Entry.urlHostname get() = cleanUrl.split("/".toRegex(), 2).first()
val Entry.urlPath get() = cleanUrl.split("/".toRegex(), 2).getOrNull(1)
        ?.takeIf { it.isNotBlank() }?.let { "/$it" }

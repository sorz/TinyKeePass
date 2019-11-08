package org.sorz.lab.tinykeepass.keepass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

import org.sorz.lab.tinykeepass.FetchDatabaseTask

import java.io.File

import de.slackspace.openkeepass.domain.Entry
import de.slackspace.openkeepass.domain.KeePassFile
import java.util.stream.Stream
import kotlin.streams.asStream

/**
 * Stream of all entries without ones in recycle bin.
 */
val KeePassFile.allEntriesNotInRecycleBin: Sequence<Entry> get() =
    if (meta.recycleBinEnabled) {
        val recycleBinUuid = meta.recycleBinUuid
        groups.asSequence()
                .filter { group -> group.uuid != recycleBinUuid }
                .flatMap { group -> group.entries.asSequence() }
    } else {
        entries.asSequence()
    }

val KeePassFile.allEntriesNotInRecycleBinStream: Stream<Entry> get() =
    allEntriesNotInRecycleBin.asStream()


val Context.databaseFile: File get() =
    File(noBackupFilesDir, FetchDatabaseTask.DB_FILENAME)

val Context.hasDatabaseConfigured: Boolean get() =
    KeePassStorage.get(this) != null || databaseFile.canRead()

val Entry.icon: Bitmap get() =
    BitmapFactory.decodeByteArray(iconData, 0, iconData.size)

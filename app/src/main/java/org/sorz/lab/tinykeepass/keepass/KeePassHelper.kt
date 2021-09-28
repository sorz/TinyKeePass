package org.sorz.lab.tinykeepass.keepass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable


import org.sorz.lab.tinykeepass.FetchDatabaseTask

import java.io.File

import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.Entry
import org.jetbrains.anko.startService
import org.sorz.lab.tinykeepass.PasswordCopingService
import org.sorz.lab.tinykeepass.R
import java.util.stream.Stream
import kotlin.streams.asStream

/**
 * Stream of all entries without ones in recycle bin.
 */
val Database.allEntriesNotInRecycleBin: Sequence<Entry>
    get() = sequence {
        val root = rootGroup ?: return@sequence
        val recycleBinUuid = if (isRecycleBinEnabled) recycleBin?.nodeId else null
        val groups = mutableListOf(root)
        while (groups.isNotEmpty()) {
            val group = groups.removeLast()
            if (group.nodeId != null && group.nodeId == recycleBinUuid)
                continue
            groups.addAll(group.getChildGroups())
            yieldAll(group.getChildEntries())
        }
    }

val Database.allEntriesNotInRecycleBinStream: Stream<Entry>
    get() =
    allEntriesNotInRecycleBin.asStream()


val Context.databaseFile: File get() =
    File(noBackupFilesDir, FetchDatabaseTask.DB_FILENAME)

val Context.hasDatabaseConfigured: Boolean get() =
    KeePassStorage.get(this) != null || databaseFile.canRead()

private val Entry.cleanUrl get() = url.replace("^https?://(www\\.)?".toRegex(), "")

val Entry.urlHostname get() = cleanUrl.split("/".toRegex(), 2).first()
val Entry.urlPath get() = cleanUrl.split("/".toRegex(), 2).getOrNull(1)
        ?.takeIf { it.isNotBlank() }?.let { "/$it" }

fun Entry.copyUrl(context: Context) {
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("URL", url)
    clipboardManager.setPrimaryClip(clipData)
}

fun Entry.copyUsername(context: Context) {
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(context.getString(R.string.username), username)
    clipboardManager.setPrimaryClip(clipData)
}

fun Entry.copyPassword(context: Context) {
    Intent(context, PasswordCopingService::class.java).apply {
        action = PasswordCopingService.ACTION_COPY_PASSWORD
        putExtra(PasswordCopingService.EXTRA_PASSWORD, password)
        context.startService(this)
    }
}

fun Entry.copyPasswordPostponed(context: Context) {
    Intent(context, PasswordCopingService::class.java).apply {
        action = PasswordCopingService.ACTION_NEW_NOTIFICATION
        putExtra(PasswordCopingService.EXTRA_PASSWORD, password)
        putExtra(PasswordCopingService.EXTRA_USERNAME, username)
        putExtra(PasswordCopingService.EXTRA_ENTRY_TITLE, title)
        context.startService(this)
    }
}

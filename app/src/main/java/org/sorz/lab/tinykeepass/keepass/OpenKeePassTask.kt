package org.sorz.lab.tinykeepass.keepass

import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.exception.LoadDatabaseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

/**
 * Open the KeePass file with a progress dialog.
 */
suspend fun openKeePassTask(activity: FragmentActivity, key: String) {

    activity.supportFragmentManager.beginTransaction()
            .add(OpenKeePassDialogFragment.newInstance(), "dialog")
            .commit()

    val keePassFile = Database.getInstance()
    var error: String? = null
    if (!keePassFile.loaded) {
        try {
            withContext(Dispatchers.Default) {
                keePassFile.loadData(activity.databaseFile.toUri(), key, null, false,
                        activity.contentResolver, activity.cacheDir, true, null)
            }
        } catch (err: LoadDatabaseException) {
            error = err.localizedMessage ?: err.toString()
        }
    }

    activity.supportFragmentManager.findFragmentByTag("dialog")?.let {
        val dialogFragment = it as OpenKeePassDialogFragment
        if (error != null) dialogFragment.onOpenError(error)
        else dialogFragment.onOpenOk()
    }

    if (error != null)
        throw OpenDatabaseError(error)
     KeePassStorage.set(activity, keePassFile)
}


class OpenDatabaseError(message: String) : Exception(message)

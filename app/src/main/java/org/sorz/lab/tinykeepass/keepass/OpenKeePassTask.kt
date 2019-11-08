package org.sorz.lab.tinykeepass.keepass

import android.os.AsyncTask

import androidx.fragment.app.FragmentActivity

import java.io.File
import java.lang.ref.WeakReference

import de.slackspace.openkeepass.KeePassDatabase
import de.slackspace.openkeepass.domain.KeePassFile
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.warn


/**
 * The AsyncTask to open KeePass file.
 */
open class OpenKeePassTask(
        activity: FragmentActivity,
        private val key: String
) : AsyncTask<Void, Void, KeePassFile>(), AnkoLogger {

    private val activity: WeakReference<FragmentActivity> = WeakReference(activity)
    private val path: File = activity.databaseFile
    private var errorMessage: String? = null

    override fun doInBackground(vararg voids: Void): KeePassFile? {
        try {
            var t = System.currentTimeMillis()
            val instance = KeePassDatabase.getInstance(path)
            debug { "get instance in ${System.currentTimeMillis() - t} ms" }
            t = System.currentTimeMillis()
            val keePassFile = instance.openDatabase(key)
            debug { "open db in ${System.currentTimeMillis() - t} ms" }
            return keePassFile
        } catch (e: KeePassDatabaseUnreadableException) {
            warn("cannot open database.", e)
            errorMessage = e.localizedMessage
        } catch (e: UnsupportedOperationException) {
            warn("cannot open database.", e)
            errorMessage = e.localizedMessage
        }
        return null
    }

    override fun onPreExecute() {
        val activity = this.activity.get() ?: return
        activity.supportFragmentManager.beginTransaction()
                .add(OpenKeePassDialogFragment.newInstance(), "dialog")
                .commit()
    }

    override fun onPostExecute(result: KeePassFile?) {
        this.activity.get()?.supportFragmentManager?.findFragmentByTag("dialog")?.let {
            val dialogFragment = it as OpenKeePassDialogFragment
            if (result == null) dialogFragment.onOpenError(errorMessage!!)
            else dialogFragment.onOpenOk()
        }
        if (result != null)
            activity.get()?.let { KeePassStorage.set(it, result) }
    }
}

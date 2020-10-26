package org.sorz.lab.tinykeepass.keepass

import android.os.AsyncTask
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.kunzisoft.keepass.database.element.Database
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.warn
import java.io.File
import java.lang.ref.WeakReference


/**
 * The AsyncTask to open KeePass file.
 */
open class OpenKeePassTask(
        fragActivity: FragmentActivity,
        private val key: String
) : AsyncTask<Void, Void, Database>(), AnkoLogger {

    private val activity: WeakReference<FragmentActivity> = WeakReference(fragActivity)
    private val path: File = fragActivity.databaseFile
    private var errorMessage: String? = null

    override fun doInBackground(vararg voids: Void): Database? {
        errorMessage = try {
            val t = System.currentTimeMillis()
            val keePassFile = Database.getInstance()
            if (!keePassFile.loaded) {
                val act = activity.get()
                keePassFile.loadData(path.toUri(), key, null, false,
                act!!.contentResolver, act.cacheDir, true, null)
            }
            debug { "open db in ${System.currentTimeMillis() - t} ms" }
            return keePassFile
        } catch (e: UnsupportedOperationException) {
            warn("cannot open database.", e)
            e.localizedMessage
        }
        return null
    }

    override fun onPreExecute() {
        val activity = this.activity.get() ?: return
        activity.supportFragmentManager.beginTransaction()
                .add(OpenKeePassDialogFragment.newInstance(), "dialog")
                .commit()
    }

    override fun onPostExecute(result: Database?) {
        this.activity.get()?.supportFragmentManager?.findFragmentByTag("dialog")?.let {
            val dialogFragment = it as OpenKeePassDialogFragment
            if (result == null) dialogFragment.onOpenError(errorMessage!!)
            else dialogFragment.onOpenOk()
        }
        if (result != null)
            activity.get()?.let { KeePassStorage.set(it, result) }
    }
}

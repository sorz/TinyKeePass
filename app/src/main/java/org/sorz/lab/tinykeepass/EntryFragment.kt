package org.sorz.lab.tinykeepass

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import com.google.android.material.snackbar.Snackbar
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton

import org.sorz.lab.tinykeepass.keepass.KeePassStorage

import de.slackspace.openkeepass.domain.Entry
import kotlinx.android.synthetic.main.fragment_entry_list.*

import org.sorz.lab.tinykeepass.keepass.KeePassHelper.notEmpty


private const val INACTIVE_AUTO_LOCK_MILLIS = (3 * 60 * 1000).toLong()


class EntryFragment : BaseEntryFragment() {
    private val clipboardManager by lazy(LazyThreadSafetyMode.NONE) {
        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private var actionMode: ActionMode? = null
    private var lastPauseTimeMillis: Long = 0

    private val entryShowPasswordActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = true
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
        override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean = false
        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            hidePassword()
        }
    }

    private val entryLongClickActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.entry_context, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val entry = entryAdapter.selectedItem ?: return false
            menu.run {
                findItem(R.id.action_copy_username).isVisible = !entry.username.isNullOrBlank()
                findItem(R.id.action_copy_password).isVisible = !entry.password.isNullOrBlank()
                findItem(R.id.action_copy_url).isVisible = !entry.url.isNullOrBlank()
                findItem(R.id.action_open).isVisible = !entry.url.isNullOrBlank()
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val entry = entryAdapter.selectedItem ?: return false
            when (item.itemId) {
                R.id.action_copy_username -> copyEntry(entry, true, false)
                R.id.action_copy_password -> copyEntry(entry, false, true)
                R.id.action_show_password -> {
                    mode.finish()
                    showPassword(entry)
                }
                R.id.action_copy_url -> if (notEmpty(entry.url)) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("URL", entry.url))
                    if (view != null)
                        Snackbar.make(view!!, R.string.url_copied,
                                Snackbar.LENGTH_SHORT).show()
                }
                R.id.action_open -> openEntryUrl(entry)
                else -> return false
            }
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            entryAdapter.clearSelection()
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (DatabaseSyncingService.BROADCAST_SYNC_FINISHED == intent.action) {
                fab?.show()
                val error = intent.getStringExtra(DatabaseSyncingService.EXTRA_SYNC_ERROR)
                if (error == null) entryAdapter.reloadEntries(context)
                view?.also { view ->
                    if (error == null)
                        Snackbar.make(view, R.string.sync_done, Snackbar.LENGTH_SHORT).show()
                    else
                        Snackbar.make(view, getString(R.string.fail_to_sync, error),
                                Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun getFragmentLayout(): Int {
        return R.layout.fragment_entry_list
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)!!

        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            fab.hide()
            activity.run {
                if (this is MainActivity) doSyncDatabase()
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        if (KeePassStorage.get(context) == null || lastPauseTimeMillis > 0
                && SystemClock.elapsedRealtime() - lastPauseTimeMillis > INACTIVE_AUTO_LOCK_MILLIS) {
            // time outed, lock and exit to unlock dialog.
            (activity as MainActivity).run {
                doLockDatabase()
                Handler().post { doUnlockDatabase() }
            }
        } else {
            requireActivity().registerReceiver(broadcastReceiver,
                    IntentFilter(DatabaseSyncingService.BROADCAST_SYNC_FINISHED))
            // sync done event may have lost, check its state now
            if (!DatabaseSyncingService.isRunning())
                fab!!.show()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireActivity().unregisterReceiver(broadcastReceiver)
        } catch (_: IllegalArgumentException) {
            // not register, ignored
        }
        lastPauseTimeMillis = SystemClock.elapsedRealtime()
        if (actionMode?.tag == entryShowPasswordActionModeCallback)
            actionMode?.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_lock_db -> {
            (activity as MainActivity).doLockDatabase()
            true
        }
        R.id.action_exit -> {
            requireActivity().finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * Immediately copy password & show count down notification.
     * @param entry to copy
     */
    private fun copyPassword(entry: Entry) {
        Intent(context, PasswordCopingService::class.java).apply {
            action = PasswordCopingService.ACTION_COPY_PASSWORD
            putExtra(PasswordCopingService.EXTRA_PASSWORD, entry.password)
            requireContext().startService(this)
        }
        view?.also { view ->
            Snackbar.make(view, R.string.password_copied, Snackbar.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, R.string.password_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * Display password.
     * @param entry to show
     */
    private fun showPassword(entry: Entry) {
        requireActivity().window.setFlags(FLAG_SECURE, FLAG_SECURE)
        actionMode?.finish()
        actionMode = requireActivity().startActionMode(entryShowPasswordActionModeCallback)?.apply {
            tag = entryShowPasswordActionModeCallback
            title = getString(R.string.title_show_password)
            entryAdapter.showPassword(entry)
        }
    }

    private fun hidePassword() {
        entryAdapter.hidePassword()
        requireActivity().window.setFlags(0, FLAG_SECURE)
    }

    private fun copyEntry(entry: Entry, copyUsername: Boolean, copyPassword: Boolean) {
        if (copyUsername && !entry.username.isNullOrEmpty()) {
            ClipData.newPlainText(getString(R.string.username), entry.username).apply {
                clipboardManager.setPrimaryClip(this)
            }
            val message = getString(R.string.username_copied, entry.username)
            view.also { view ->
                Snackbar.make(view!!, message, Snackbar.LENGTH_LONG).apply {
                    if (copyPassword) setAction(R.string.copy_password) { copyPassword(entry) }
                    show()
                }
            }
        }
        if (copyPassword && !entry.password.isNullOrEmpty()) {
            if (copyUsername && !entry.username.isNullOrEmpty()) {
                // username already copied, waiting for user's action before copy password.
                val intent = Intent(context, PasswordCopingService::class.java).apply {
                    action = PasswordCopingService.ACTION_NEW_NOTIFICATION
                    putExtra(PasswordCopingService.EXTRA_PASSWORD, entry.password)
                    entry.username?.also { username ->
                        putExtra(PasswordCopingService.EXTRA_USERNAME, username)
                    }
                    entry.title?.also { title ->
                        putExtra(PasswordCopingService.EXTRA_ENTRY_TITLE, title)
                    }
                }
                requireContext().startService(intent)
            } else {
                // username not copied, copy password immediately.
                copyPassword(entry)
            }
        }
    }

    private fun openEntryUrl(entry: Entry) {
        if (entry.url.isNullOrEmpty())
            return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.url))
        startActivity(intent)
        copyEntry(entry, true, true)
    }

    override fun onEntryClick(view: View, entry: Entry) {
        actionMode?.run { finish() }
                ?: copyEntry(entry, true, true)
    }

    override fun onEntryLongClick(view: View, entry: Entry): Boolean {
        actionMode?.run {
            if (tag == entryLongClickActionModeCallback) {
                invalidate()
                return true
            } else {
                finish()
            }
        }

        actionMode = requireActivity().startActionMode(entryLongClickActionModeCallback)?.apply {
            tag = entryLongClickActionModeCallback
        } ?: return false
        return true
    }

    companion object {
        fun newInstance(): EntryFragment = EntryFragment()
    }
}

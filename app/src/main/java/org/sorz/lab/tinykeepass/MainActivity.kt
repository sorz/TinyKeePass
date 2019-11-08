package org.sorz.lab.tinykeepass

import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar

import android.view.View
import android.widget.Toast
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.warn

import org.sorz.lab.tinykeepass.keepass.KeePassStorage
import org.sorz.lab.tinykeepass.keepass.databaseFile

import org.sorz.lab.tinykeepass.keepass.hasDatabaseConfigured


private const val REQUEST_SETUP_DATABASE = 1

class MainActivity : BaseActivity(), AnkoLogger {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, DatabaseLockedFragment())
                    .commit()

            if (!hasDatabaseConfigured) {
                doConfigureDatabase()
            } else {
                doUnlockDatabase()
            }
        } else if (KeePassStorage.get(this) != null) {
            // Restarting activity
            KeePassStorage.registerBroadcastReceiver(this)
        }
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (KeePassStorage.get(this) != null)
            showEntryList()
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (isFinishing)
            KeePassStorage.set(this, null)
    }

    override fun onResume() {
        super.onResume()
        if (KeePassStorage.get(this) != null) {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (fragment !is EntryFragment) {
                showEntryList()
            }
        }
    }

    fun doUnlockDatabase() {
        if (KeePassStorage.get(this) != null) {
            showEntryList()
        } else {
            getDatabaseKeys({ keys ->
                openDatabase(keys[0]) { showEntryList() }
            }, { err ->
                showError(err)
            })
        }
    }

    fun doLockDatabase() {
        KeePassStorage.set(this, null)
        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DatabaseLockedFragment())
                .commit()
    }

    fun doConfigureDatabase() {
        KeePassStorage.set(this, null)
        val intent = Intent(this, DatabaseSetupActivity::class.java)
        startActivityForResult(intent, REQUEST_SETUP_DATABASE)
    }

    fun doSyncDatabase() {
        getDatabaseKeys({ keys ->
            val url = preferences.getString("db-url", "")
            val masterKey = keys.get(0)
            var username: String? = null
            var password: String? = null
            if (preferences.getBoolean("db-auth-required", false)) {
                username = preferences.getString("db-auth-username", "")
                password = keys.get(1)
            }
            val intent = DatabaseSyncingService.getFetchIntent(
                    this, url, masterKey, username, password)
            startService(intent)
        }) { showError(it) }
    }

    fun doCleanDatabase() {
        KeePassStorage.set(this, null)
        if (!databaseFile.delete())
            warn("fail to delete database file")
        secureStringStorage.clear()
        DatabaseSetupActivity.clearDatabaseConfigs(preferences)
        showMessage(getString(R.string.clean_config_ok), Snackbar.LENGTH_SHORT)
    }

    private fun showEntryList() {
        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, EntryFragment.newInstance())
                .commit()
    }

    private fun showError(message: String) {
        showMessage(message, Snackbar.LENGTH_LONG)
    }

    private fun showMessage(text: CharSequence, duration: Int) {
        findViewById<View?>(R.id.fragment_container)?.let { view ->
            Snackbar.make(view, text, duration).show()
        } ?: run {
            // view will be null if this method called on onCreate().
            val t = if (duration == Snackbar.LENGTH_SHORT) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            Toast.makeText(this, text, t).show()
        }
    }

}

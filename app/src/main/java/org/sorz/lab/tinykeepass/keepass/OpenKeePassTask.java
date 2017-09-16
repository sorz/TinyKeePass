package org.sorz.lab.tinykeepass.keepass;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.getDatabaseFile;

/**
 * The AsyncTask to open KeePass file.
 */
public class OpenKeePassTask extends AsyncTask<Void, Void, KeePassFile> {
    static private final String TAG = OpenKeePassTask.class.getName();

    private final WeakReference<Activity> activity;
    private final File path;
    private final String key;
    private String errorMessage;

    public OpenKeePassTask(Activity activity, String masterKey) {
        this.activity = new WeakReference<>(activity);
        path = getDatabaseFile(activity);
        key = masterKey;
    }

    @Override
    protected KeePassFile doInBackground(Void... voids) {
        try {
            long t = System.currentTimeMillis();
            KeePassDatabase instance = KeePassDatabase.getInstance(path);
            Log.d(TAG, "get instance in " + (System.currentTimeMillis() - t) + "ms");
            t = System.currentTimeMillis();
            KeePassFile keePassFile = instance.openDatabase(key);
            Log.d(TAG, "open db in " + (System.currentTimeMillis() - t) + "ms");
            return keePassFile;
        } catch (KeePassDatabaseUnreadableException | UnsupportedOperationException e) {
            Log.w(TAG, "cannot open database.", e);
            errorMessage = e.getLocalizedMessage();
        }
        return null;
    }

    @Override
    protected void onPreExecute(){
        Activity activity = this.activity.get();
        if (activity == null)
            return;
        activity.getFragmentManager().beginTransaction()
                .add(OpenKeePassDialogFragment.newInstance(), "dialog")
                .commit();
    }

    @Override
    protected void onPostExecute(KeePassFile result){
        Activity activity = this.activity.get();
        if (activity == null)
            return;
        OpenKeePassDialogFragment dialogFragment = (OpenKeePassDialogFragment)
                activity.getFragmentManager().findFragmentByTag("dialog");
        if (dialogFragment != null) {
            // TODO: handle cancel
            if (result == null)
                dialogFragment.onOpenError(errorMessage);
            else
                dialogFragment.onOpenOk();
        }
        if (result != null) {
            KeePassStorage.set(activity, result);
        }
    }
}

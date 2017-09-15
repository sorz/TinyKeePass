package org.sorz.lab.tinykeepass.keepass;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.sorz.lab.tinykeepass.R;

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

    private final WeakReference<Context> context;
    private final File path;
    private final String key;
    private ProgressDialog dialog;

    public OpenKeePassTask(Context context, String masterKey) {
        this.context = new WeakReference<>(context);
        path = getDatabaseFile(context);
        key = masterKey;
    }

    protected void onErrorMessage(String error) {
        // To be override
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
            onErrorMessage(e.getLocalizedMessage());
        }
        return null;
    }

    @Override
    protected void onPreExecute(){
        Context context = this.context.get();
        if (context == null)
            return;
        dialog = new ProgressDialog(context);
        dialog.setTitle(R.string.title_db_decrypting);
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.show();
    }

    @Override
    protected void onPostExecute(KeePassFile result){
        if (dialog != null)
            dialog.dismiss();
    }
}

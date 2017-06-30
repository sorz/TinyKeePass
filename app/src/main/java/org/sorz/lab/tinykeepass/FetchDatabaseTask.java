package org.sorz.lab.tinykeepass;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.domain.Meta;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

/**
 * Download database and overwrite old one after checking its integrity.
 *
 * Created by xierch on 2017/6/29.
 */

public class FetchDatabaseTask extends AsyncTask<Void, Void, String> {
    final static private String TAG = FetchDatabaseTask.class.getName();
    final static public String DB_FILENAME = "database.kdbx";
    final private URL url;
    final private String masterPassword;
    final private File cacheDir;
    final private File filesDir;

    public FetchDatabaseTask(Context context, URL url, String masterPwd,
                             String username, String password) {
        this.url = url;
        masterPassword = masterPwd;
        cacheDir = context.getCacheDir();
        filesDir = context.getNoBackupFilesDir();

        if (username != null && password != null) {
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (!getRequestingURL().getAuthority()
                            .equals(FetchDatabaseTask.this.url.getAuthority()))
                        return null;
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
        }
    }

    @Override
    protected String doInBackground(Void... voids) {
        File tmpDbFile = new File(cacheDir, DB_FILENAME);
        File dbFile = new File(filesDir, DB_FILENAME);
        try {
            OutputStream output = new BufferedOutputStream(new FileOutputStream(tmpDbFile));
            InputStream input = url.openStream();
            IOUtils.copy(input, output);
            input.close();
            output.close();
        } catch (IOException e) {
            Log.w(TAG, "fail to download database file.", e);
            return e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
        }
        KeePassFile db;
        try {
            db = KeePassDatabase.getInstance(tmpDbFile).openDatabase(masterPassword);
        } catch (KeePassDatabaseUnreadableException | UnsupportedOperationException e) {
            Log.w(TAG, "cannot open database.", e);
            return e.getLocalizedMessage();
        }
        Meta meta = db.getMeta();
        Log.d(TAG, "Database opened, name: " + meta.getDatabaseName());

        if (!tmpDbFile.renameTo(dbFile)) {
            try {
                InputStream input = new FileInputStream(tmpDbFile);
                OutputStream output = new FileOutputStream(dbFile);
                IOUtils.copy(input, output);
                if (!tmpDbFile.delete())
                    Log.w(TAG, "fail to delete temp database on cache");
                input.close();
                output.close();
            } catch (IOException e) {
                Log.e(TAG, "cannot copy new database.", e);
                return "Fail to save database";
            }
        }
        KeePassStorage.setKeePassFile(db);
        return null;
    }
}

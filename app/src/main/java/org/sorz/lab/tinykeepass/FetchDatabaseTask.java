package org.sorz.lab.tinykeepass;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;

import com.kunzisoft.keepass.database.element.Database;
import com.kunzisoft.keepass.database.exception.LoadDatabaseException;


import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

/**
 * Download database and overwrite old one after checking its integrity.
 *
 * Created by xierch on 2017/6/29.
 */

public class FetchDatabaseTask extends AsyncTask<Void, Void, String> {
    final static private String TAG = FetchDatabaseTask.class.getName();
    final static public String DB_FILENAME = "database.kdbx";

    final private WeakReference<Context> context;
    final private Uri uri;
    final private String masterPassword;
    final private File cacheDir;
    final private File filesDir;

    public FetchDatabaseTask(Context context, Uri uri, String masterPwd,
                             String username, String password) {
        this.context = new WeakReference<>(context);
        this.uri = uri;
        masterPassword = masterPwd;
        cacheDir = context.getCacheDir();
        filesDir = context.getNoBackupFilesDir();

        if (uri.getScheme().startsWith("http") && username != null && password != null) {
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (!getRequestingURL().getAuthority()
                            .equals(FetchDatabaseTask.this.uri.getAuthority()))
                        return null;
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
        }
    }

    private InputStream getInputStream(Uri uri) throws IOException {
        if (uri.getScheme().toLowerCase().matches("https?")) {
            URL url = new URL(uri.toString());
            return url.openStream();
        } else {
            Context context = this.context.get();
            if (context == null)
                throw new IOException("context collected");
            ContentResolver contentResolver = context.getContentResolver();
            try {
                // works for OPEN_DOCUMENT, but not VIEW
                contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Log.i(TAG, "cannot take persistable permission", e);
            }
            try {
                return contentResolver.openInputStream(uri);
            } catch (SecurityException e) {
                Log.i(TAG, "cannot open from provider", e);
                throw new IOException(e);
            }
        }
    }

    @Override
    protected String doInBackground(Void... voids) {
        File tmpDbFile = new File(cacheDir, DB_FILENAME);
        File dbFile = new File(filesDir, DB_FILENAME);
        try {
            OutputStream output = new BufferedOutputStream(new FileOutputStream(tmpDbFile));
            InputStream input = getInputStream(uri);
            IOUtils.copy(input, output);
            input.close();
            output.close();
        } catch (InterruptedIOException e) {
            // task cancelled
            return null;
        } catch (IOException e) {
            Log.w(TAG, "fail to open database file.", e);
            return e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
        }

        Database db;
        try {
            // singeleton db, can only clear and reload
            db = Database.Companion.getInstance();
            if (db.getLoaded()) {
                db.closeAndClear(null);
            }
            Context context = this.context.get();
            if (context == null) {
                String msg = "failed get context when reloading db";
                Log.e(TAG, msg);
                return msg;
            }

            db.loadData(android.net.Uri.fromFile(tmpDbFile), masterPassword, null, false,
                    context.getContentResolver(), context.getCacheDir(), true, null);
        } catch (UnsupportedOperationException | LoadDatabaseException e) {
            Log.w(TAG, "cannot open database.", e);
            return e.getLocalizedMessage();
        } catch (NullPointerException e) {
            // happen on try to open a KDBX 4 database with Argon2 (openkeepass 8.0)
            Log.e(TAG, "Underlying library throw null pointer exception", e);
            return "Database broken or not support";
        }
        Log.d(TAG, "Database opened, name: " + db.getName());

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
        Context context = this.context.get();
        if (context != null)
            KeePassStorage.set(context, db);
        return null;
    }
}

package org.sorz.lab.tinykeepass;

import android.content.Context;
import android.os.AsyncTask;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * Created by xierch on 2017/6/29.
 */

public class FetchFileTask extends AsyncTask<Void, Void, String> {
    final private URL url;
    final private OutputStream output;

    public FetchFileTask(OutputStream dest, URL url, String username, String password) {
        this.url = url;
        output = dest;

        if (username != null && password != null) {
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (!getRequestingURL().getAuthority()
                            .equals(FetchFileTask.this.url.getAuthority()))
                        return null;
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
        }
    }

    public FetchFileTask(OutputStream dest, URL url) throws MalformedURLException {
        this(dest, url, null, null);
    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            InputStream input = url.openStream();
            IOUtils.copy(input, output);
        } catch (IOException e) {
            e.printStackTrace();
            return e.getMessage();
        }
        return null;
    }
}

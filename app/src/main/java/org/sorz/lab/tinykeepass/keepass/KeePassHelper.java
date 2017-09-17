package org.sorz.lab.tinykeepass.keepass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.sorz.lab.tinykeepass.FetchDatabaseTask;

import java.io.File;
import java.util.UUID;
import java.util.stream.Stream;

import de.slackspace.openkeepass.domain.Entry;
import de.slackspace.openkeepass.domain.KeePassFile;

/**
 * Common helper methods related to KeePass.
 */
public class KeePassHelper {
    /**
     * Get all entries without ones in recycle bin.
     * @param keePass opened Keepass database file
     * @return stream of all entries not in recycle bin
     */
    static public Stream<Entry> allEntriesNotInRecycleBin(KeePassFile keePass) {
        if (keePass.getMeta().getRecycleBinEnabled()) {
            UUID recycleBinUuid = keePass.getMeta().getRecycleBinUuid();
            return keePass.getGroups().stream()
                    .filter(group -> !group.getUuid().equals(recycleBinUuid))
                    .flatMap(group -> group.getEntries().stream());
        } else {
            return keePass.getEntries().stream();
        }
    }

    /**
     * @param str string or null
     * @return true if str is not null and not empty
     */
    static public boolean notEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    static public Bitmap getIcon(Entry entry) {
        return BitmapFactory.decodeByteArray(entry.getIconData(), 0,
                entry.getIconData().length);
    }

    static public File getDatabaseFile(Context context) {
        return new File(context.getNoBackupFilesDir(), FetchDatabaseTask.DB_FILENAME);
    }

    static public boolean hasDatabaseConfigured(Context context) {
        return KeePassStorage.get(context) != null || getDatabaseFile(context).canRead();
    }
}

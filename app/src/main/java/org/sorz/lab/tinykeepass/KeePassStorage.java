package org.sorz.lab.tinykeepass;

import de.slackspace.openkeepass.domain.KeePassFile;

/**
 * Created by xierch on 2017/6/30.
 */

public class KeePassStorage {
    private static KeePassFile keePassFile;

    public static KeePassFile getKeePassFile() {
        return keePassFile;
    }

    public static void setKeePassFile(KeePassFile file) {
        keePassFile = file;
    }
}

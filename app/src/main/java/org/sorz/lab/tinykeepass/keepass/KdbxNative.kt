package org.sorz.lab.tinykeepass.keepass

class KdbxNative {
    companion object {
        init {
            System.loadLibrary("kdbx")
        }
        @JvmStatic external fun loadDatabase(path: String, password: String): ByteArray
    }
}

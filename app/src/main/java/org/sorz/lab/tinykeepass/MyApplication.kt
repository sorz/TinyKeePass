package org.sorz.lab.tinykeepass

import android.app.Application
import com.kunzisoft.keepass.icons.IconPackChooser

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        IconPackChooser.build(this)
        IconPackChooser.setSelectedIconPack("classic")
    }
}

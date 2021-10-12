package org.sorz.lab.tinykeepass

import android.app.Application
import com.kunzisoft.keepass.icons.IconPackChooser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import org.sorz.lab.tinykeepass.keepass.RealRepository
import org.sorz.lab.tinykeepass.keepass.Repository
import javax.inject.Singleton

@HiltAndroidApp
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        IconPackChooser.build(this)
        IconPackChooser.setSelectedIconPack("classic")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object RepoModule {
    @Provides
    @Singleton
    fun provideRepo(app: Application): Repository =
        RealRepository(app.applicationContext, Dispatchers.IO)
}

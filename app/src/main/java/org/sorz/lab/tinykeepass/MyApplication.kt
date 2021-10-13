package org.sorz.lab.tinykeepass

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.kunzisoft.keepass.icons.IconPackChooser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.sorz.lab.tinykeepass.keepass.DatabaseState
import org.sorz.lab.tinykeepass.keepass.RealRepository
import org.sorz.lab.tinykeepass.keepass.Repository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MyApplication"
private val DB_AUTO_LOCK_BACKGROUND = Duration.ofSeconds(30)  // TODO: make it customizable
private val DB_AUTO_LOCK_CHECKING_INTERVAL = Duration.ofSeconds(10)

@HiltAndroidApp
class MyApplication : Application() {
    @Inject lateinit var repo: Repository
    private val activityCounter = ActivityCounter()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoLockDatabaseJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityCounter)
        IconPackChooser.build(this)
        IconPackChooser.setSelectedIconPack("classic")

        repo.databaseState.onEach { state ->
            if (state == DatabaseState.UNLOCKED) {
                if (autoLockDatabaseJob == null || autoLockDatabaseJob?.isActive == false) {
                    autoLockDatabaseJob = appScope.launch {
                        checkAndLockDatabaseWhenTimedOut()
                    }
                }
            } else {
                autoLockDatabaseJob?.cancel()
            }
        }.launchIn(appScope)
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterActivityLifecycleCallbacks(activityCounter)
    }

    private suspend fun checkAndLockDatabaseWhenTimedOut() {
        Log.d(TAG, "Database unlocked, auto-locking enabled")
        while (repo.databaseState.value == DatabaseState.UNLOCKED) {
            if (activityCounter.hasStartedActivity) {
                delay(DB_AUTO_LOCK_CHECKING_INTERVAL.toMillis())
                continue
            }
            val duration = activityCounter.durationSinceLastActivityStopped ?: continue
            if (duration >= DB_AUTO_LOCK_BACKGROUND) {
                Log.i(TAG, "Auto-lock database (${duration.seconds}secs in background)")
                repo.lockDatabase()
                break
            } else {
                delay((DB_AUTO_LOCK_BACKGROUND - duration).toMillis())
            }
        }
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

private class ActivityCounter : Application.ActivityLifecycleCallbacks {
    private var startedActivityCount = 0
    private var lastActivityStoppedAt: Instant? = null

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { }
    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
    }
    override fun onActivityResumed(activity: Activity) { }
    override fun onActivityPaused(activity: Activity) { }
    override fun onActivityStopped(activity: Activity) {
        lastActivityStoppedAt = Instant.now()
        startedActivityCount -= 1
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) { }
    override fun onActivityDestroyed(activity: Activity) { }

    val hasStartedActivity get() = startedActivityCount > 0
    val durationSinceLastActivityStopped get() = lastActivityStoppedAt?.let {
        Duration.between(it, Instant.now())
    }
}
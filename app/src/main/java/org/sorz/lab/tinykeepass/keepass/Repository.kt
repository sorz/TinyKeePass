package org.sorz.lab.tinykeepass.keepass

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DatabaseState {
    UNCONFIGURED, LOCKED, UNLOCKED,
}

interface Repository {
    val databaseState: StateFlow<DatabaseState>
    suspend fun unlockDatabase()
    suspend fun syncDatabase()
    suspend fun cleanDatabase()
}

object DummyRepository : Repository {
    private val state = MutableStateFlow(DatabaseState.LOCKED)

    override val databaseState = state

    override suspend fun unlockDatabase() {
        state.value = DatabaseState.UNLOCKED
    }

    override suspend fun syncDatabase() {
        TODO("Not yet implemented")
    }

    override suspend fun cleanDatabase() {
        state.value = DatabaseState.UNCONFIGURED
    }
}

class RealRepository : Repository {
    private val state = MutableStateFlow(DatabaseState.UNCONFIGURED)

    override val databaseState = state

    override suspend fun unlockDatabase() {
        state.value = DatabaseState.UNLOCKED
    }

    override suspend fun syncDatabase() {
        TODO("Not yet implemented")
    }

    override suspend fun cleanDatabase() {
        state.value = DatabaseState.UNCONFIGURED
    }
}

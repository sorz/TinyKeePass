package org.sorz.lab.tinykeepass.database

import java.io.Serializable
import java.time.Instant



data class Entry(
        val username: String,
        val password: String,
        val url: String = "",
        val notes: String = "",
        val creationTime: Instant = Instant.now(),
        val icon: String? = null,
) : Serializable

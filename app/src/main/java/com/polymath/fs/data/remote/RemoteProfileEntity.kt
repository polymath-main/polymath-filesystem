package com.polymath.fs.data.remote

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_profiles")
data class RemoteProfileEntity(
    @PrimaryKey val id: String,
    val label: String,
    val scheme: String = "sftp",
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String = "password", // "password" | "key"
    val keyPath: String? = null,
    val encryptedSecretRef: String? = null,
    val hostKeyFingerprint: String? = null,
    val defaultPath: String = ""
)

package com.porteiro.server.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "apartments", indices = [Index(value = ["number", "block"], unique = true)])
data class Apartment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val block: String? = null
)

@Entity(
    tableName = "residents",
    foreignKeys = [
        ForeignKey(
            entity = Apartment::class,
            parentColumns = ["id"],
            childColumns = ["apartment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("apartment_id")]
)
data class Resident(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "apartment_id") val apartmentId: Long,
    val name: String,
    val phone: String,
    @ColumnInfo(name = "is_admin") val isAdmin: Boolean = false
)

@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = Resident::class,
            parentColumns = ["id"],
            childColumns = ["resident_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("resident_id"), Index(value = ["auth_token"], unique = true)]
)
data class Device(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "resident_id") val residentId: Long,
    @ColumnInfo(name = "device_name") val deviceName: String,
    @ColumnInfo(name = "auth_token") val authToken: String,
    @ColumnInfo(name = "paired_at") val pairedAt: Long? = null,
    @ColumnInfo(name = "last_active") val lastActive: Long? = null
)

@Entity(
    tableName = "facial_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = Resident::class,
            parentColumns = ["id"],
            childColumns = ["resident_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("resident_id")]
)
data class FacialEmbedding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "resident_id") val residentId: Long,
    @ColumnInfo(name = "vector_data") val vectorData: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "access_logs",
    foreignKeys = [
        ForeignKey(
            entity = Resident::class,
            parentColumns = ["id"],
            childColumns = ["resident_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("resident_id"), Index("timestamp")]
)
data class AccessLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "resident_id") val residentId: Long? = null,
    @ColumnInfo(name = "trigger_type") val triggerType: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)

object TriggerType {
    const val FACIAL_RECOGNITION = "FACIAL_RECOGNITION"
    const val APP_REMOTE = "APP_REMOTE"
    const val MANUAL_PORTARIA = "MANUAL_PORTARIA"
}

object AccessStatus {
    const val SUCCESS = "SUCCESS"
    const val FAILED_UNAUTHORIZED = "FAILED_UNAUTHORIZED"
    const val FAILED_FACE_NOT_MATCH = "FAILED_FACE_NOT_MATCH"
    const val FAILED_GATEWAY = "FAILED_GATEWAY"
}

package com.porteiro.server.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApartmentDao {
    @Insert
    suspend fun insert(apartment: Apartment): Long

    @Update
    suspend fun update(apartment: Apartment)

    @Delete
    suspend fun delete(apartment: Apartment)

    @Query("SELECT * FROM apartments ORDER BY block, number")
    fun observeAll(): Flow<List<Apartment>>

    @Transaction
    @Query("SELECT * FROM apartments ORDER BY block, number")
    fun observeWithResidents(): Flow<List<ApartmentWithResidents>>

    @Query("SELECT * FROM apartments WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Apartment?

    @Query("SELECT * FROM apartments WHERE number = :number LIMIT 1")
    suspend fun byNumber(number: String): Apartment?
}

@Dao
interface ResidentDao {
    @Insert
    suspend fun insert(resident: Resident): Long

    @Update
    suspend fun update(resident: Resident)

    @Delete
    suspend fun delete(resident: Resident)

    @Query("SELECT * FROM residents WHERE apartment_id = :apartmentId ORDER BY name")
    fun observeByApartment(apartmentId: Long): Flow<List<Resident>>

    @Query("SELECT * FROM residents WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Resident?

    @Query("SELECT * FROM residents WHERE apartment_id = :apartmentId ORDER BY name")
    suspend fun byApartment(apartmentId: Long): List<Resident>

    @Query("SELECT * FROM residents ORDER BY name")
    suspend fun all(): List<Resident>
}

@Dao
interface DeviceDao {
    @Insert
    suspend fun insert(device: Device): Long

    @Update
    suspend fun update(device: Device)

    @Delete
    suspend fun delete(device: Device)

    @Query("SELECT * FROM devices WHERE auth_token = :token LIMIT 1")
    suspend fun byToken(token: String): Device?

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Device?

    @Query("SELECT * FROM devices WHERE resident_id = :residentId ORDER BY paired_at DESC")
    fun observeByResident(residentId: Long): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE resident_id = :residentId")
    suspend fun byResident(residentId: Long): List<Device>

    @Query(
        """
        SELECT d.* FROM devices d
        INNER JOIN residents r ON d.resident_id = r.id
        WHERE r.apartment_id = :apartmentId
        """
    )
    suspend fun byApartment(apartmentId: Long): List<Device>

    @Transaction
    @Query(
        """
        SELECT r.* FROM residents r
        INNER JOIN devices d ON d.resident_id = r.id
        WHERE d.auth_token = :token LIMIT 1
        """
    )
    suspend fun residentByToken(token: String): Resident?

    @Query(
        """
        SELECT COUNT(*) FROM devices d
        INNER JOIN residents r ON d.resident_id = r.id
        WHERE r.apartment_id = :apartmentId AND d.paired_at IS NOT NULL
        """
    )
    suspend fun countActiveInApartment(apartmentId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM devices d
        INNER JOIN residents r ON d.resident_id = r.id
        WHERE r.apartment_id = :apartmentId
        """
    )
    suspend fun countInApartment(apartmentId: Long): Int
}

@Dao
interface FacialEmbeddingDao {
    @Insert
    suspend fun insert(embedding: FacialEmbedding): Long

    @Delete
    suspend fun delete(embedding: FacialEmbedding)

    @Query("DELETE FROM facial_embeddings WHERE resident_id = :residentId")
    suspend fun deleteByResident(residentId: Long)

    @Query("SELECT * FROM facial_embeddings WHERE resident_id = :residentId")
    suspend fun byResident(residentId: Long): List<FacialEmbedding>

    @Query("SELECT * FROM facial_embeddings")
    suspend fun all(): List<FacialEmbedding>
}

@Dao
interface AccessLogDao {
    @Insert
    suspend fun insert(log: AccessLog): Long

    @Query("SELECT * FROM access_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AccessLog>>

    @Query("SELECT * FROM access_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<AccessLog>

    @Query(
        """
        SELECT COUNT(*) FROM access_logs
        WHERE resident_id = :residentId
          AND status = :status
          AND timestamp >= :since
        """
    )
    suspend fun countStatusSince(residentId: Long, status: String, since: Long): Int

    @Query("DELETE FROM access_logs")
    suspend fun clear()
}

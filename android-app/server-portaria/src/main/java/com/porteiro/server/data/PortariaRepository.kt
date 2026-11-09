package com.porteiro.server.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PortariaRepository(private val db: PortariaDatabase) {

    private val pendingPairings = ConcurrentHashMap<String, Long>()

    val apartments: Flow<List<ApartmentWithResidents>> = db.apartmentDao().observeWithResidents()
    val accessLogs: Flow<List<AccessLog>> = db.accessLogDao().observeRecent()

    // ---------------------------------------------------------------- unidades

    suspend fun addApartment(number: String, block: String?): Long =
        db.apartmentDao().insert(Apartment(number = number.trim(), block = block?.trim()?.ifBlank { null }))

    suspend fun updateApartment(apartment: Apartment) = db.apartmentDao().update(apartment)

    suspend fun deleteApartment(apartment: Apartment) = db.apartmentDao().delete(apartment)

    suspend fun apartmentByNumber(number: String): Apartment? =
        db.apartmentDao().byNumber(number.trim())

    suspend fun apartmentById(id: Long): Apartment? = db.apartmentDao().byId(id)

    // ---------------------------------------------------------------- moradores

    suspend fun addResident(apartmentId: Long, name: String, phone: String, isAdmin: Boolean = false): Long =
        db.residentDao().insert(
            Resident(
                apartmentId = apartmentId,
                name = name.trim(),
                phone = phone.trim(),
                isAdmin = isAdmin
            )
        )

    suspend fun updateResident(resident: Resident) = db.residentDao().update(resident)

    suspend fun deleteResident(resident: Resident) = db.residentDao().delete(resident)

    suspend fun residentById(id: Long): Resident? = db.residentDao().byId(id)

    suspend fun residentsOfApartment(apartmentId: Long): List<Resident> =
        db.residentDao().byApartment(apartmentId)

    // ---------------------------------------------------------------- pareamento

    /**
     * Cria um dispositivo pendente e um token de pareamento (QR Code) válido por 5 minutos.
     * RN-04: no máximo 5 dispositivos por unidade.
     */
    suspend fun createPairing(residentId: Long, deviceName: String): PairingResult {
        val resident = db.residentDao().byId(residentId) ?: return PairingResult.NotFound
        if (db.deviceDao().countInApartment(resident.apartmentId) >= MAX_DEVICES_PER_APARTMENT) {
            return PairingResult.LimitReached
        }
        val token = UUID.randomUUID().toString()
        val deviceId = db.deviceDao().insert(
            Device(
                residentId = residentId,
                deviceName = deviceName.trim().ifBlank { "Celular do morador" },
                authToken = token,
                pairedAt = null,
                lastActive = null
            )
        )
        val expiresAt = System.currentTimeMillis() + PAIRING_LIFETIME_MS
        pendingPairings[token] = expiresAt
        return PairingResult.Success(db.deviceDao().byId(deviceId)!!, resident, expiresAt)
    }

    suspend fun cancelPairing(token: String) {
        pendingPairings.remove(token)
        db.deviceDao().byToken(token)?.let { device ->
            if (device.pairedAt == null) db.deviceDao().delete(device)
        }
    }

    /** Valida o token lido do QR Code e converte o dispositivo pendente em pareado. */
    suspend fun claimPairing(token: String, deviceName: String): ClaimResult {
        val device = db.deviceDao().byToken(token) ?: return ClaimResult.InvalidToken
        val resident = db.residentDao().byId(device.residentId) ?: return ClaimResult.InvalidToken
        val apartment = db.apartmentDao().byId(resident.apartmentId) ?: return ClaimResult.InvalidToken

        if (device.pairedAt == null) {
            val expiresAt = pendingPairings[token]
            if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
                pendingPairings.remove(token)
                db.deviceDao().delete(device)
                return ClaimResult.ExpiredToken
            }
            pendingPairings.remove(token)
        }

        val now = System.currentTimeMillis()
        val updated = device.copy(
            deviceName = deviceName.trim().ifBlank { device.deviceName },
            pairedAt = device.pairedAt ?: now,
            lastActive = now
        )
        db.deviceDao().update(updated)
        return ClaimResult.Success(updated, resident, apartment)
    }

    suspend fun touchDevice(token: String) {
        db.deviceDao().byToken(token)?.let {
            db.deviceDao().update(it.copy(lastActive = System.currentTimeMillis()))
        }
    }

    suspend fun deviceByToken(token: String): Device? = db.deviceDao().byToken(token)

    suspend fun residentByToken(token: String): Resident? = db.deviceDao().residentByToken(token)

    suspend fun apartmentByToken(token: String): Apartment? {
        val resident = residentByToken(token) ?: return null
        return apartmentById(resident.apartmentId)
    }

    suspend fun devicesOfApartment(apartmentId: Long): List<Device> =
        db.deviceDao().byApartment(apartmentId)

    suspend fun devicesOfResident(residentId: Long): List<Device> =
        db.deviceDao().byResident(residentId)

    // ---------------------------------------------------------------- biometria

    suspend fun addFacialEmbedding(residentId: Long, vector: FloatArray) {
        db.facialEmbeddingDao().insert(
            FacialEmbedding(
                residentId = residentId,
                vectorData = encodeVector(vector),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun facialEmbeddings(): List<FacialEmbedding> =
        db.facialEmbeddingDao().all()

    suspend fun facialEmbeddingsOf(residentId: Long): List<FacialEmbedding> =
        db.facialEmbeddingDao().byResident(residentId)

    // ---------------------------------------------------------------- auditoria

    suspend fun log(residentId: Long?, triggerType: String, status: String) {
        db.accessLogDao().insert(
            AccessLog(
                residentId = residentId,
                triggerType = triggerType,
                status = status,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun recentLogs(limit: Int = 100): List<AccessLog> = db.accessLogDao().recent(limit)

    suspend fun countFailuresSince(residentId: Long, since: Long): Int =
        db.accessLogDao().countStatusSince(residentId, AccessStatus.FAILED_FACE_NOT_MATCH, since)

    companion object {
        const val MAX_DEVICES_PER_APARTMENT = 5
        const val PAIRING_LIFETIME_MS = 5 * 60 * 1000L

        fun encodeVector(vector: FloatArray): String = vector.joinToString(",")

        fun decodeVector(data: String): FloatArray =
            if (data.isBlank()) floatArrayOf()
            else data.split(',').mapNotNull { it.toFloatOrNull() }.toFloatArray()
    }
}

sealed class PairingResult {
    data class Success(
        val device: Device,
        val resident: Resident,
        val expiresAt: Long
    ) : PairingResult()

    data object LimitReached : PairingResult()
    data object NotFound : PairingResult()
}

sealed class ClaimResult {
    data class Success(
        val device: Device,
        val resident: Resident,
        val apartment: Apartment
    ) : ClaimResult()

    data object InvalidToken : ClaimResult()
    data object ExpiredToken : ClaimResult()
}

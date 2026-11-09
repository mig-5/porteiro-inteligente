package com.porteiro.server.network

import com.porteiro.server.data.AccessStatus
import com.porteiro.server.data.PortariaRepository
import com.porteiro.server.data.TriggerType
import com.porteiro.server.settings.ServerSettingsStore
import kotlinx.coroutines.flow.first

sealed class AccessOutcome {
    data object Success : AccessOutcome()
    data object Unauthorized : AccessOutcome()
    data object GatewayFailure : AccessOutcome()
}

class AccessController(
    private val repository: PortariaRepository,
    private val esp32Client: Esp32Client,
    private val settingsStore: ServerSettingsStore
) {

    suspend fun unlockByToken(token: String, triggerType: String): AccessOutcome {
        val device = repository.deviceByToken(token)
        if (device == null || device.pairedAt == null) {
            repository.log(null, triggerType, AccessStatus.FAILED_UNAUTHORIZED)
            return AccessOutcome.Unauthorized
        }
        repository.touchDevice(token)
        return unlockForResident(device.residentId, triggerType)
    }

    suspend fun unlockForResident(residentId: Long?, triggerType: String): AccessOutcome {
        val settings = settingsStore.settings.first()
        val result = esp32Client.openGate(
            ip = settings.esp32Ip,
            port = settings.esp32Port,
            sharedSecret = settings.sharedSecret,
            durationMs = settings.relayDurationMs,
            triggeredBy = triggerType
        )
        if (result.success) {
            repository.log(residentId, triggerType, AccessStatus.SUCCESS)
            return AccessOutcome.Success
        }
        repository.log(residentId, triggerType, AccessStatus.FAILED_GATEWAY)
        return AccessOutcome.GatewayFailure
    }

    suspend fun unlockManually(): AccessOutcome =
        unlockForResident(null, TriggerType.MANUAL_PORTARIA)
}

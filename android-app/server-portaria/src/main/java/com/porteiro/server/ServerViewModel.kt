package com.porteiro.server

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.porteiro.server.data.AccessLog
import com.porteiro.server.data.Apartment
import com.porteiro.server.data.ApartmentWithResidents
import com.porteiro.server.data.PairingResult
import com.porteiro.server.data.Resident
import com.porteiro.server.network.LocalNetwork
import com.porteiro.server.network.PairingPayload
import com.porteiro.server.network.QrCodeGenerator
import com.porteiro.server.settings.ServerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PairingUi(
    val bitmap: Bitmap,
    val payload: PairingPayload,
    val expiresAt: Long
)

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortariaApp
    private val repository = app.repository
    private val settingsStore = app.settingsStore
    val controller = app.controller

    val apartments: StateFlow<List<ApartmentWithResidents>> =
        repository.apartments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<AccessLog>> =
        repository.accessLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<ServerSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerSettings())

    val serverState = controller.serverState
    val callState = controller.callState
    val faceState = controller.faceState

    fun addApartment(number: String, block: String?) {
        if (number.isBlank()) return
        viewModelScope.launch { repository.addApartment(number, block) }
    }

    fun deleteApartment(apartment: Apartment) {
        viewModelScope.launch { repository.deleteApartment(apartment) }
    }

    fun addResident(apartmentId: Long, name: String, phone: String, isAdmin: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addResident(apartmentId, name, phone, isAdmin) }
    }

    fun deleteResident(resident: Resident) {
        viewModelScope.launch { repository.deleteResident(resident) }
    }

    fun startPairing(
        residentId: Long,
        deviceName: String,
        onReady: (PairingUi) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val ip = LocalNetwork.localIpv4()
            if (ip == null) {
                onError("Conecte o celular da portaria a uma rede Wi-Fi")
                return@launch
            }
            when (val result = repository.createPairing(residentId, deviceName)) {
                is PairingResult.Success -> {
                    val payload = PairingPayload(
                        serverIp = ip,
                        port = settings.serverPort,
                        authToken = result.device.authToken,
                        deviceId = controller.serverState.value.deviceId
                    )
                    val bitmap = withContext(Dispatchers.Default) {
                        QrCodeGenerator.generate(payload.toJson())
                    }
                    withContext(Dispatchers.Main) {
                        onReady(PairingUi(bitmap, payload, result.expiresAt))
                    }
                }

                PairingResult.LimitReached ->
                    onError("Limite de 5 dispositivos por unidade atingido")

                PairingResult.NotFound ->
                    onError("Morador não encontrado")
            }
        }
    }

    fun cancelPairing(payload: PairingPayload) {
        controller.cancelPairing(payload.authToken)
    }

    fun dial(apartment: Apartment) = controller.dial(apartment)

    fun hangUp() = controller.hangUp()

    fun resetCall() = controller.resetCall()

    fun manualUnlock(onResult: (Boolean) -> Unit) = controller.manualUnlock(onResult)

    fun saveSettings(settings: ServerSettings) {
        viewModelScope.launch {
            settingsStore.save(settings)
            controller.restartServer()
        }
    }

    fun restartServer() = controller.restartServer()
}

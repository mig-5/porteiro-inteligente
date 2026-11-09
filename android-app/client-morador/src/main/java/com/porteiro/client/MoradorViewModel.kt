package com.porteiro.client

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.porteiro.client.data.PairingConfig
import com.porteiro.client.data.PairingPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoradorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MoradorApp
    private val store = app.store
    private val controller = app.controller

    val config: StateFlow<PairingConfig?> =
        store.config.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state = controller.state
    val callState = controller.callState
    val callManager = controller.callManager

    fun savePairing(payload: PairingPayload) {
        viewModelScope.launch(Dispatchers.IO) {
            store.save(payload.toConfig())
        }
    }

    fun saveManual(serverIp: String, port: Int, token: String, deviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.save(PairingConfig(serverIp, port, token, deviceId))
        }
    }

    fun disconnect() = controller.disconnect()

    fun reconnect() = controller.reconnect()

    fun unlock(onResult: ((Boolean, String) -> Unit)? = null) = controller.unlock(onResult)

    fun enrollFace(bitmap: Bitmap) = controller.enrollFace(bitmap)

    fun acceptCall() = controller.acceptCall()
    fun declineCall() = controller.declineCall()
    fun hangUp() = controller.hangUp()
    fun dismissCall() = controller.dismissCall()

    fun clearUnlockMessage() = controller.clearUnlockMessage()
    fun clearEnrollmentMessage() = controller.clearEnrollmentMessage()
}

package com.porteiro.server

import android.content.Context
import android.provider.Settings
import com.porteiro.server.data.Apartment
import com.porteiro.server.data.PortariaRepository
import com.porteiro.server.face.FaceRecognitionEngine
import com.porteiro.server.network.AccessController
import com.porteiro.server.network.FaceEnrollResult
import com.porteiro.server.network.LocalNetwork
import com.porteiro.server.network.PortariaServer
import com.porteiro.server.settings.ServerSettings
import com.porteiro.server.settings.ServerSettingsStore
import com.porteiro.server.webrtc.CallManager
import com.porteiro.server.webrtc.CameraStream
import com.porteiro.server.webrtc.WebRtcCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ServerRuntimeState(
    val running: Boolean = false,
    val ip: String? = null,
    val port: Int = 8080,
    val deviceId: String = "",
    val error: String? = null,
    val cameraReady: Boolean = false
)

class PortariaController(
    private val context: Context,
    private val repository: PortariaRepository,
    private val settingsStore: ServerSettingsStore,
    private val accessController: AccessController,
    private val camera: CameraStream,
    private val callManager: CallManager,
    private val faceEngine: FaceRecognitionEngine
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startLock = Mutex()

    private val _serverState = MutableStateFlow(ServerRuntimeState())
    val serverState: StateFlow<ServerRuntimeState> = _serverState.asStateFlow()

    private val deviceId: String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "portaria-android"

    private var server: PortariaServer? = null
    private var cameraInitialized = false
    private var lastPort: Int? = null

    val callState = callManager.state
    val faceState = faceEngine.state

    fun startNetwork() {
        scope.launch {
            startLock.withLock {
            val settings = settingsStore.settings.first()
            if (server?.isAlive == true && lastPort == settings.serverPort) return@launch
            runCatching {
                server?.stopServer()
                val instance = PortariaServer(
                    repository = repository,
                    accessController = accessController,
                    deviceId = deviceId,
                    port = settings.serverPort
                )
                instance.callDelegate = callManager
                instance.faceEnrollmentHandler = { token, jpeg ->
                    val device = repository.deviceByToken(token)
                    val resident = repository.residentByToken(token)
                    if (device == null || device.pairedAt == null || resident == null) {
                        FaceEnrollResult.Unauthorized
                    } else {
                        faceEngine.enroll(resident.id, jpeg)
                    }
                }
                instance.startServer()
                callManager.attachServer(instance)
                server = instance
                lastPort = settings.serverPort
                val ip = LocalNetwork.localIpv4()
                _serverState.value = ServerRuntimeState(
                    running = true,
                    ip = ip,
                    port = instance.portNumber,
                    deviceId = deviceId,
                    error = if (ip == null) "Sem rede Wi-Fi local" else null,
                    cameraReady = cameraInitialized
                )
            }.onFailure { error ->
                _serverState.value = ServerRuntimeState(
                    running = false,
                    deviceId = deviceId,
                    error = error.message ?: "Falha ao iniciar servidor"
                )
            }
            }
        }
    }

    fun startCameraPipeline() {
        scope.launch {
            if (!cameraInitialized) {
                runCatching {
                    WebRtcCore.initialize(context)
                    camera.initialize()
                    cameraInitialized = true
                }.onFailure { error ->
                    _serverState.value = _serverState.value.copy(
                        error = "Câmera indisponível: ${error.message}"
                    )
                    return@launch
                }
            }
            faceEngine.start()
            _serverState.value = _serverState.value.copy(cameraReady = true)
        }
    }

    fun stopCameraPipeline() {
        faceEngine.stop()
    }

    fun dial(apartment: Apartment) {
        callManager.startCall(apartment)
    }

    fun attachPreview(sink: org.webrtc.VideoSink) {
        runCatching { camera.addSink(sink) }
    }

    fun detachPreview(sink: org.webrtc.VideoSink) {
        runCatching { camera.removeSink(sink) }
    }

    fun hangUp() = callManager.hangUp()

    fun resetCall() = callManager.reset()

    fun manualUnlock(onResult: (Boolean) -> Unit) {
        scope.launch {
            val outcome = accessController.unlockManually()
            onResult(outcome is com.porteiro.server.network.AccessOutcome.Success)
        }
    }

    fun restartServer() {
        scope.launch {
            runCatching { server?.stopServer() }
            server = null
            lastPort = null
            startNetwork()
        }
    }

    suspend fun createPairing(residentId: Long, deviceName: String) =
        repository.createPairing(residentId, deviceName)

    fun cancelPairing(token: String) {
        scope.launch { repository.cancelPairing(token) }
    }

    fun currentSettings(): ServerSettings? =
        runCatching { kotlinx.coroutines.runBlocking { settingsStore.settings.first() } }.getOrNull()

    fun release() {
        callManager.dispose()
        faceEngine.release()
        runCatching { server?.stopServer() }
        server = null
        camera.release()
    }
}

package com.porteiro.server.webrtc

import android.content.Context
import com.porteiro.server.data.Apartment
import com.porteiro.server.data.Device
import com.porteiro.server.data.PortariaRepository
import com.porteiro.server.data.Resident
import com.porteiro.server.network.CallDelegate
import com.porteiro.server.network.PortariaServer
import com.porteiro.server.network.WsType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

enum class CallStatus { IDLE, RINGING, CONNECTED, ENDED, FAILED }

data class CallState(
    val status: CallStatus = CallStatus.IDLE,
    val callId: String? = null,
    val apartmentLabel: String = "",
    val residentNames: List<String> = emptyList(),
    val remoteDeviceName: String? = null,
    val message: String? = null,
    val startedAt: Long? = null
)

/**
 * Gerencia a chamada do terminal da portaria para o celular do morador.
 * O servidor é o ofertante (envia áudio/vídeo) e recebe a resposta do cliente.
 */
class CallManager(
    context: Context,
    private val repository: PortariaRepository,
    private val camera: CameraStream
) : CallDelegate {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val factory: PeerConnectionFactory = WebRtcCore.initialize(context)

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var server: PortariaServer? = null

    private var peerConnection: PeerConnection? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var audioTrack: org.webrtc.AudioTrack? = null

    private var currentCallId: String? = null
    private var remoteToken: String? = null
    private var remoteDescriptionSet = false
    private val pendingIce = CopyOnWriteArrayList<IceCandidate>()
    private var disposed = false

    fun attachServer(server: PortariaServer) {
        this.server = server
    }

    fun startCall(apartment: Apartment) {
        if (disposed) return
        if (_state.value.status == CallStatus.RINGING || _state.value.status == CallStatus.CONNECTED) {
            terminate("Nova chamada iniciada", notifyRemote = true)
        }
        scope.launch {
            val residents = repository.residentsOfApartment(apartment.id)
            val devices = repository.devicesOfApartment(apartment.id)
                .filter { it.pairedAt != null && server?.isOnline(it.authToken) == true }
            if (devices.isEmpty()) {
                _state.value = CallState(
                    status = CallStatus.FAILED,
                    apartmentLabel = labelOf(apartment),
                    residentNames = residents.map { it.name },
                    message = "Nenhum morador conectado a este apartamento"
                )
                return@launch
            }
            placeCall(apartment, residents, devices.first())
        }
    }

    private fun placeCall(apartment: Apartment, residents: List<Resident>, device: Device) {
        val callId = UUID.randomUUID().toString()
        currentCallId = callId
        remoteToken = device.authToken
        remoteDescriptionSet = false
        pendingIce.clear()

        _state.value = CallState(
            status = CallStatus.RINGING,
            callId = callId,
            apartmentLabel = labelOf(apartment),
            residentNames = residents.map { it.name },
            remoteDeviceName = device.deviceName,
            message = "Chamando..."
        )

        val pc = createPeerConnection()
        peerConnection = pc

        runCatching {
            val audioConstraints = MediaConstraints()
            val source = factory.createAudioSource(audioConstraints)
            val track = factory.createAudioTrack("portaria-audio", source)
            audioSource = source
            audioTrack = track
            pc.addTrack(track, listOf("portaria-stream"))
            pc.addTrack(camera.track, listOf("portaria-stream"))
        }.onFailure { error ->
            terminate("Falha ao iniciar mídia: ${error.message}", notifyRemote = false)
            return
        }

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(offer: SessionDescription?) {
                if (offer == null) {
                    terminate("Oferta SDP vazia", notifyRemote = false)
                    return
                }
                pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        sendOffer(callId, offer.description)
                    }

                    override fun onSetFailure(error: String?) {
                        terminate("Falha ao preparar chamada: $error", notifyRemote = false)
                    }
                }, offer)
            }

            override fun onCreateFailure(error: String?) {
                terminate("Falha ao criar chamada: $error", notifyRemote = false)
            }
        }, constraints)
    }

    private fun sendOffer(callId: String, sdp: String) {
        val token = remoteToken ?: return
        val state = _state.value
        val incoming = JSONObject().apply {
            put("type", WsType.INCOMING_CALL)
            put("callId", callId)
            put("apartment", state.apartmentLabel)
            put("residents", JSONArray(state.residentNames))
        }
        val offer = JSONObject().apply {
            put("type", WsType.WEBRTC_OFFER)
            put("callId", callId)
            put("sdp", sdp)
        }
        val ok = server?.sendToToken(token, incoming.toString()) == true &&
            server?.sendToToken(token, offer.toString()) == true
        if (!ok) {
            terminate("Morador desconectou", notifyRemote = false)
        }
    }

    private fun createPeerConnection(): PeerConnection {
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        return factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                if (newState == PeerConnection.IceConnectionState.CONNECTED ||
                    newState == PeerConnection.IceConnectionState.COMPLETED
                ) {
                    _state.value = _state.value.copy(
                        status = CallStatus.CONNECTED,
                        message = "Chamada conectada",
                        startedAt = _state.value.startedAt ?: System.currentTimeMillis()
                    )
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val token = remoteToken ?: return
                val callId = currentCallId ?: return
                val json = JSONObject().apply {
                    put("type", WsType.WEBRTC_ICE)
                    put("callId", callId)
                    put("sdp", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                }
                server?.sendToToken(token, json.toString())
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) = Unit
        }) ?: error("Não foi possível criar a conexão WebRTC")
    }

    // ------------------------------------------------------- CallDelegate

    override fun onDeviceOnline(token: String, deviceName: String) = Unit

    override fun onDeviceOffline(token: String) {
        if (token == remoteToken && _state.value.status == CallStatus.RINGING) {
            terminate("Morador desconectou", notifyRemote = false)
        }
    }

    override fun onWebRtcAnswer(token: String, callId: String, sdp: String) {
        if (token != remoteToken || callId != currentCallId) return
        val pc = peerConnection ?: return
        val description = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                pendingIce.forEach { pc.addIceCandidate(it) }
                pendingIce.clear()
            }
        }, description)
    }

    override fun onWebRtcIce(token: String, callId: String, sdp: String, sdpMid: String?, mLineIndex: Int) {
        if (token != remoteToken || callId != currentCallId) return
        val candidate = IceCandidate(sdpMid, mLineIndex, sdp)
        val pc = peerConnection
        if (pc == null || !remoteDescriptionSet) {
            pendingIce.add(candidate)
        } else {
            pc.addIceCandidate(candidate)
        }
    }

    override fun onHangup(token: String, callId: String) {
        if (token == remoteToken && callId == currentCallId) {
            terminate("Chamada encerrada pelo morador", notifyRemote = false)
        }
    }

    override fun onReject(token: String, callId: String) {
        if (token == remoteToken && callId == currentCallId) {
            terminate("Chamada recusada pelo morador", notifyRemote = false)
        }
    }

    // ------------------------------------------------------- controle

    fun hangUp() = terminate("Chamada encerrada", notifyRemote = true)

    fun reset() {
        if (_state.value.status != CallStatus.RINGING && _state.value.status != CallStatus.CONNECTED) {
            _state.value = CallState()
        }
    }

    private fun terminate(message: String, notifyRemote: Boolean) {
        val token = remoteToken
        val callId = currentCallId
        if (notifyRemote && token != null && callId != null) {
            server?.sendToToken(
                token,
                JSONObject().put("type", WsType.CALL_ENDED).put("callId", callId).toString()
            )
        }
        closePeerConnection()
        currentCallId = null
        remoteToken = null
        remoteDescriptionSet = false
        pendingIce.clear()
        _state.value = _state.value.copy(
            status = if (_state.value.status == CallStatus.RINGING) CallStatus.FAILED else CallStatus.ENDED,
            message = message
        )
    }

    private fun closePeerConnection() {
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        peerConnection = null
        audioTrack = null
        audioSource = null
    }

    fun dispose() {
        disposed = true
        terminate("Sistema encerrado", notifyRemote = true)
    }

    private fun labelOf(apartment: Apartment): String =
        if (apartment.block.isNullOrBlank()) "Apt. ${apartment.number}"
        else "${apartment.block} - ${apartment.number}"

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}

package com.porteiro.client.webrtc

import android.content.Context
import com.porteiro.client.net.WsType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.util.concurrent.CopyOnWriteArrayList

enum class ClientCallPhase { IDLE, RINGING, CONNECTING, CONNECTED, ENDED, FAILED }

data class ClientCallState(
    val phase: ClientCallPhase = ClientCallPhase.IDLE,
    val callId: String? = null,
    val apartment: String = "",
    val residents: List<String> = emptyList(),
    val message: String? = null
)

/** Chamada recebida pelo morador. O servidor da portaria é o ofertante. */
class ClientCallManager(
    context: Context,
    private val send: (JSONObject) -> Unit
) {
    private val factory: PeerConnectionFactory = WebRtcCore.initialize(context)

    private val _state = MutableStateFlow(ClientCallState())
    val state: StateFlow<ClientCallState> = _state.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var audioTrack: org.webrtc.AudioTrack? = null
    private var remoteTrack: VideoTrack? = null
    private var renderer: SurfaceViewRenderer? = null

    private var pendingOffer: String? = null
    private var remoteDescriptionSet = false
    private val pendingIce = CopyOnWriteArrayList<Pair<String, IceCandidate>>()
    private var currentCallId: String? = null

    fun onIncomingCall(callId: String, apartment: String, residents: List<String>) {
        if (_state.value.phase == ClientCallPhase.RINGING ||
            _state.value.phase == ClientCallPhase.CONNECTED ||
            _state.value.phase == ClientCallPhase.CONNECTING
        ) return
        currentCallId = callId
        pendingOffer = null
        remoteDescriptionSet = false
        pendingIce.clear()
        _state.value = ClientCallState(
            phase = ClientCallPhase.RINGING,
            callId = callId,
            apartment = apartment,
            residents = residents,
            message = "Chamada recebida na portaria"
        )
    }

    fun onOffer(callId: String, sdp: String) {
        if (callId != currentCallId) {
            currentCallId = callId
        }
        pendingOffer = sdp
    }

    fun onIce(callId: String, sdp: String, sdpMid: String?, mLineIndex: Int) {
        val candidate = IceCandidate(sdpMid, mLineIndex, sdp)
        val pc = peerConnection
        if (pc != null && remoteDescriptionSet) {
            pc.addIceCandidate(candidate)
        } else {
            pendingIce.add(callId to candidate)
        }
    }

    fun accept() {
        val callId = currentCallId ?: return
        val offer = pendingOffer
        if (offer == null) {
            _state.value = _state.value.copy(
                phase = ClientCallPhase.FAILED,
                message = "Mídia da chamada ainda não recebida. Tente novamente."
            )
            return
        }
        _state.value = _state.value.copy(phase = ClientCallPhase.CONNECTING, message = "Conectando...")

        val pc = createPeerConnection()
        peerConnection = pc

        runCatching {
            val source = factory.createAudioSource(MediaConstraints())
            val track = factory.createAudioTrack("morador-audio", source)
            audioSource = source
            audioTrack = track
            pc.addTrack(track, listOf("morador-stream"))
        }

        val description = SessionDescription(SessionDescription.Type.OFFER, offer)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushIce(callId)
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        if (answer == null) return
                        pc.setLocalDescription(object : SdpObserverAdapter() {
                            override fun onSetSuccess() {
                                send(
                                    JSONObject()
                                        .put("type", WsType.WEBRTC_ANSWER)
                                        .put("callId", callId)
                                        .put("sdp", answer.description)
                                )
                            }
                        }, answer)
                    }
                }, constraints)
            }

            override fun onSetFailure(error: String?) {
                fail("Falha ao atender chamada: $error")
            }
        }, description)
    }

    fun decline() {
        val callId = currentCallId ?: return
        send(JSONObject().put("type", WsType.REJECT).put("callId", callId))
        finish("Chamada recusada", ended = false)
    }

    fun hangUp() {
        val callId = currentCallId ?: return
        send(JSONObject().put("type", WsType.HANGUP).put("callId", callId))
        finish("Chamada encerrada", ended = true)
    }

    fun onRemoteEnded(message: String) {
        if (_state.value.phase == ClientCallPhase.IDLE) return
        finish(message, ended = true)
    }

    fun dismiss() {
        if (_state.value.phase == ClientCallPhase.ENDED || _state.value.phase == ClientCallPhase.FAILED) {
            _state.value = ClientCallState()
        }
    }

    fun attachRenderer(view: SurfaceViewRenderer) {
        renderer = view
        remoteTrack?.addSink(view)
    }

    fun detachRenderer(view: SurfaceViewRenderer) {
        remoteTrack?.removeSink(view)
        if (renderer === view) renderer = null
    }

    private fun flushIce(callId: String) {
        val pc = peerConnection ?: return
        pendingIce.filter { it.first == callId }.forEach { pc.addIceCandidate(it.second) }
        pendingIce.clear()
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
                        phase = ClientCallPhase.CONNECTED,
                        message = "Chamada em andamento"
                    )
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val callId = currentCallId ?: return
                send(
                    JSONObject()
                        .put("type", WsType.WEBRTC_ICE)
                        .put("callId", callId)
                        .put("sdp", candidate.sdp)
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex)
                )
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteTrack = track
                    renderer?.let { track.addSink(it) }
                }
            }
        }) ?: error("Não foi possível criar a conexão WebRTC")
    }

    private fun finish(message: String, ended: Boolean) {
        closePeerConnection()
        currentCallId = null
        pendingOffer = null
        remoteDescriptionSet = false
        pendingIce.clear()
        _state.value = _state.value.copy(
            phase = if (ended) ClientCallPhase.ENDED else ClientCallPhase.FAILED,
            message = message
        )
    }

    private fun fail(message: String) {
        finish(message, ended = false)
    }

    private fun closePeerConnection() {
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        remoteTrack = null
        peerConnection = null
        audioTrack = null
        audioSource = null
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}

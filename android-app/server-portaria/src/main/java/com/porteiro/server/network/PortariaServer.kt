package com.porteiro.server.network

import com.porteiro.server.data.AccessStatus
import com.porteiro.server.data.ClaimResult
import com.porteiro.server.data.PortariaRepository
import com.porteiro.server.data.TriggerType
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

interface CallDelegate {
    fun onDeviceOnline(token: String, deviceName: String)
    fun onDeviceOffline(token: String)
    fun onWebRtcAnswer(token: String, callId: String, sdp: String)
    fun onWebRtcIce(token: String, callId: String, sdp: String, sdpMid: String?, mLineIndex: Int)
    fun onHangup(token: String, callId: String)
    fun onReject(token: String, callId: String)
}

sealed class FaceEnrollResult {
    data object Success : FaceEnrollResult()
    data object NoFaceDetected : FaceEnrollResult()
    data object Unauthorized : FaceEnrollResult()
    data object Unavailable : FaceEnrollResult()
}

/**
 * Servidor local da portaria: HTTP + WebSocket.
 *
 * - GET  /api/health
 * - POST /api/unlock          (X-Auth-Token) -> aciona ESP32
 * - POST /api/face            (X-Auth-Token + image/jpeg) -> cadastra biometria
 * - WS   /ws?token=...&device_name=...
 */
class PortariaServer(
    private val repository: PortariaRepository,
    private val accessController: AccessController,
    private val deviceId: String,
    port: Int
) : NanoWSD(port) {

    var callDelegate: CallDelegate? = null
    var faceEnrollmentHandler: (suspend (token: String, jpeg: ByteArray) -> FaceEnrollResult)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sockets = ConcurrentHashMap<String, PortariaSocket>()

    val portNumber: Int get() = listeningPort

    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
    }

    fun stopServer() {
        sockets.keys.toList().forEach { sendToToken(it, errorJson("Servidor encerrado")) }
        sockets.values.toList().forEach { runCatching { it.close(NanoWSD.WebSocketFrame.CloseCode.GoingAway, "server stop", false) } }
        sockets.clear()
        stop()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    fun isOnline(token: String): Boolean = sockets.containsKey(token)

    fun onlineTokens(): Set<String> = sockets.keys.toSet()

    fun sendToToken(token: String, message: String): Boolean {
        val socket = sockets[token] ?: return false
        return runCatching { socket.send(message); true }.getOrDefault(false)
    }

    fun broadcast(message: String) {
        sockets.values.forEach { runCatching { it.send(message) } }
    }

    override fun serveHttp(session: IHTTPSession): Response {
        return when {
            session.uri == "/api/health" && session.method == Method.GET -> {
                val body = JSONObject()
                    .put("ok", true)
                    .put("device_id", deviceId)
                    .put("server_time", System.currentTimeMillis())
                    .toString()
                newFixedLengthResponse(Response.Status.OK, "application/json", body)
            }

            session.uri == "/api/unlock" && session.method == Method.POST -> handleUnlock(session)

            session.uri == "/api/face" && session.method == Method.POST -> handleFace(session)

            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                errorJson("Rota não encontrada")
            )
        }
    }

    private fun handleUnlock(session: IHTTPSession): Response {
        val token = session.headers["x-auth-token"]
            ?: session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
        if (token.isNullOrBlank()) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                errorJson("Token ausente")
            )
        }
        val outcome = runBlocking {
            accessController.unlockByToken(token, TriggerType.APP_REMOTE)
        }
        return when (outcome) {
            AccessOutcome.Success -> newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JSONObject().put("ok", true).put("message", "Portão acionado").toString()
            )

            AccessOutcome.Unauthorized -> newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                errorJson("Dispositivo não autorizado")
            )

            AccessOutcome.GatewayFailure -> newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                errorJson("Falha ao acionar o ESP32")
            )
        }
    }

    private fun handleFace(session: IHTTPSession): Response {
        val token = session.headers["x-auth-token"]
        if (token.isNullOrBlank()) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                errorJson("Token ausente")
            )
        }
        val handler = faceEnrollmentHandler ?: return newFixedLengthResponse(
            Response.Status.SERVICE_UNAVAILABLE,
            "application/json",
            errorJson("Reconhecimento facial indisponível")
        )
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength <= 0L || contentLength > 8L * 1024 * 1024) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                errorJson("Imagem inválida")
            )
        }
        val bytes = ByteArray(contentLength.toInt())
        var read = 0
        try {
            val input = session.inputStream
            while (read < bytes.size) {
                val n = input.read(bytes, read, bytes.size - read)
                if (n <= 0) break
                read += n
            }
        } catch (_: IOException) {
            // tratado abaixo
        }
        if (read <= 0) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                errorJson("Corpo vazio")
            )
        }
        val result = runBlocking { handler(token, bytes) }
        val (status, message) = when (result) {
            FaceEnrollResult.Success -> Response.Status.OK to "Biometria cadastrada"
            FaceEnrollResult.NoFaceDetected -> Response.Status.BAD_REQUEST to "Nenhum rosto detectado"
            FaceEnrollResult.Unauthorized -> Response.Status.UNAUTHORIZED to "Dispositivo não autorizado"
            FaceEnrollResult.Unavailable -> Response.Status.SERVICE_UNAVAILABLE to "Cadastro biométrico indisponível"
        }
        val ok = result == FaceEnrollResult.Success
        return newFixedLengthResponse(
            status,
            "application/json",
            JSONObject().put("ok", ok).put("message", message).toString()
        )
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val token = handshake.parms["token"].orEmpty()
        val deviceName = handshake.parms["device_name"].orEmpty()
        if (token.isBlank()) {
            return RejectedSocket(handshake, NanoWSD.WebSocketFrame.CloseCode.PolicyViolation, "token ausente")
        }
        val claim = runBlocking { repository.claimPairing(token, deviceName) }
        return when (claim) {
            is ClaimResult.Success -> PortariaSocket(
                handshake = handshake,
                token = token,
                deviceName = claim.device.deviceName,
                residentName = claim.resident.name,
                apartmentLabel = if (claim.apartment.block.isNullOrBlank())
                    "Apt. ${claim.apartment.number}"
                else "${claim.apartment.block} - ${claim.apartment.number}"
            )
            ClaimResult.ExpiredToken -> RejectedSocket(
                handshake,
                NanoWSD.WebSocketFrame.CloseCode.PolicyViolation,
                "QR Code expirado"
            )

            ClaimResult.InvalidToken -> RejectedSocket(
                handshake,
                NanoWSD.WebSocketFrame.CloseCode.PolicyViolation,
                "token inválido"
            )
        }
    }

    private inner class PortariaSocket(
        handshake: IHTTPSession,
        private val token: String,
        private val deviceName: String,
        private val residentName: String,
        private val apartmentLabel: String
    ) : WebSocket(handshake) {

        override fun onOpen() {
            val previous = sockets.put(token, this)
            if (previous != null && previous !== this) {
                runCatching { previous.close(WebSocketFrame.CloseCode.NormalClosure, "nova conexão", false) }
            }
            callDelegate?.onDeviceOnline(token, deviceName)
            val hello = JSONObject()
                .put("type", WsType.HELLO)
                .put("device_id", deviceId)
                .put("device_name", deviceName)
                .put("resident_name", residentName)
                .put("apartment", apartmentLabel)
                .put("server_time", System.currentTimeMillis())
            send(hello.toString())
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode,
            reason: String,
            initiatedByRemote: Boolean
        ) {
            if (sockets[token] === this) {
                sockets.remove(token)
                callDelegate?.onDeviceOffline(token)
            }
        }

        override fun onMessage(message: WebSocketFrame) {
            val text = message.textPayload ?: return
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (json.optString("type")) {
                WsType.UNLOCK -> {
                    scope.launch {
                        val trigger = json.optString("trigger", TriggerType.APP_REMOTE)
                            .ifBlank { TriggerType.APP_REMOTE }
                        val outcome = accessController.unlockByToken(token, trigger)
                        val ok = outcome == AccessOutcome.Success
                        send(
                            JSONObject()
                                .put("type", WsType.UNLOCK_RESULT)
                                .put("ok", ok)
                                .put("message", if (ok) "Portão acionado" else "Falha ao acionar portão")
                                .toString()
                        )
                    }
                }

                WsType.WEBRTC_ANSWER -> callDelegate?.onWebRtcAnswer(
                    token,
                    json.optString("callId"),
                    json.optString("sdp")
                )

                WsType.WEBRTC_ICE -> callDelegate?.onWebRtcIce(
                    token,
                    json.optString("callId"),
                    json.optString("sdp"),
                    json.optString("sdpMid").ifBlank { null },
                    json.optInt("sdpMLineIndex", 0)
                )

                WsType.HANGUP -> callDelegate?.onHangup(token, json.optString("callId"))

                WsType.REJECT -> callDelegate?.onReject(token, json.optString("callId"))

                WsType.PING -> send(JSONObject().put("type", WsType.PONG).toString())
            }
        }

        override fun onPong(payload: WebSocketFrame) = Unit

        override fun onException(exception: IOException) {
            if (sockets[token] === this) {
                sockets.remove(token)
                callDelegate?.onDeviceOffline(token)
            }
        }
    }

    private class RejectedSocket(
        handshake: IHTTPSession,
        private val code: WebSocketFrame.CloseCode,
        private val reason: String
    ) : WebSocket(handshake) {
        override fun onOpen() {
            runCatching { close(code, reason, false) }
        }

        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) = Unit
        override fun onMessage(message: WebSocketFrame) = Unit
        override fun onPong(payload: WebSocketFrame) = Unit
        override fun onException(exception: IOException) = Unit
    }

    private fun errorJson(message: String): String =
        JSONObject().put("ok", false).put("error", message).toString()

    @Suppress("unused")
    private fun denied(residentId: Long?) : String {
        scope.launch { repository.log(residentId, TriggerType.APP_REMOTE, AccessStatus.FAILED_UNAUTHORIZED) }
        return errorJson("não autorizado")
    }
}

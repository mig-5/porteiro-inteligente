package com.porteiro.client

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import com.porteiro.client.data.PairingConfig
import com.porteiro.client.data.PairingStore
import com.porteiro.client.net.WsType
import com.porteiro.client.webrtc.ClientCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class ClientUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val residentName: String? = null,
    val apartment: String? = null,
    val serverAddress: String? = null,
    val message: String? = null,
    val unlockMessage: String? = null,
    val enrollmentMessage: String? = null
)

class MoradorController(
    private val context: Context,
    private val store: PairingStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(ClientUiState())
    val state: StateFlow<ClientUiState> = _state.asStateFlow()

    val callManager = ClientCallManager(context) { json ->
        webSocketRef.get()?.send(json.toString())
    }
    val callState = callManager.state

    private val webSocketRef = AtomicReference<WebSocket?>(null)
    private var config: PairingConfig? = null
    private var reconnectJob: Job? = null
    private var backoffMs = 1_000L
    private var intentionalClose = false
    @Volatile
    private var generation = 0
    private var unlockCallback: ((Boolean, String) -> Unit)? = null

    init {
        createNotificationChannel()
        scope.launch {
            store.identity.collect { (name, apartment) ->
                _state.value = _state.value.copy(residentName = name, apartment = apartment)
            }
        }
        scope.launch {
            var previous: PairingConfig? = null
            store.config.collect { newConfig ->
                if (newConfig != previous) {
                    previous = newConfig
                    config = newConfig
                    reconnectJob?.cancel()
                    closeSocket()
                    if (newConfig == null) {
                        _state.value = ClientUiState()
                    } else {
                        connect(newConfig)
                    }
                }
            }
        }
    }

    private fun connect(cfg: PairingConfig) {
        intentionalClose = false
        _state.value = _state.value.copy(
            status = ConnectionStatus.CONNECTING,
            serverAddress = "${cfg.serverIp}:${cfg.port}",
            message = null
        )
        val deviceName = URLEncoder.encode(Build.MODEL ?: "Celular do morador", "UTF-8")
        val url = "ws://${cfg.serverIp}:${cfg.port}/ws?token=${cfg.authToken}&device_name=$deviceName"
        val request = Request.Builder().url(url).build()
        val myGeneration = ++generation
        http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (myGeneration != generation) {
                    webSocket.close(1000, "conexão substituída")
                    return
                }
                webSocketRef.set(webSocket)
                backoffMs = 1_000L
                _state.value = _state.value.copy(
                    status = ConnectionStatus.CONNECTED,
                    message = null
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGeneration != generation) return
                webSocketRef.compareAndSet(webSocket, null)
                if (!intentionalClose) {
                    _state.value = _state.value.copy(
                        status = ConnectionStatus.DISCONNECTED,
                        message = "Conexão encerrada"
                    )
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGeneration != generation) return
                webSocketRef.compareAndSet(webSocket, null)
                if (!intentionalClose) {
                    _state.value = _state.value.copy(
                        status = ConnectionStatus.ERROR,
                        message = "Sem conexão com a portaria: ${t.message ?: "erro de rede"}"
                    )
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            WsType.HELLO -> {
                val resident = json.optString("resident_name").ifBlank { null }
                val apartment = json.optString("apartment").ifBlank { null }
                _state.value = _state.value.copy(residentName = resident, apartment = apartment)
                scope.launch { store.saveIdentity(resident, apartment) }
            }

            WsType.INCOMING_CALL -> {
                val callId = json.optString("callId")
                val apartment = json.optString("apartment")
                val residents = json.optJSONArray("residents").toStringList()
                callManager.onIncomingCall(callId, apartment, residents)
                showIncomingCallNotification(apartment, residents)
            }

            WsType.WEBRTC_OFFER -> callManager.onOffer(
                json.optString("callId"),
                json.optString("sdp")
            )

            WsType.WEBRTC_ICE -> callManager.onIce(
                json.optString("callId"),
                json.optString("sdp"),
                json.optString("sdpMid").ifBlank { null },
                json.optInt("sdpMLineIndex", 0)
            )

            WsType.CALL_ENDED, WsType.CALL_CANCELLED -> {
                callManager.onRemoteEnded(
                    if (json.optString("type") == WsType.CALL_CANCELLED) "Chamada cancelada"
                    else "Chamada encerrada pela portaria"
                )
            }

            WsType.UNLOCK_RESULT -> {
                val ok = json.optBoolean("ok", false)
                val message = json.optString("message").ifBlank {
                    if (ok) "Portão acionado" else "Falha ao acionar o portão"
                }
                _state.value = _state.value.copy(unlockMessage = message)
                unlockCallback?.invoke(ok, message)
                unlockCallback = null
            }

            WsType.ERROR -> {
                _state.value = _state.value.copy(message = json.optString("error"))
            }

            WsType.PING -> webSocketRef.get()?.send(
                JSONObject().put("type", WsType.PONG).toString()
            )
        }
    }

    private fun scheduleReconnect() {
        val cfg = config ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(20_000L)
            if (config == cfg && webSocketRef.get() == null) connect(cfg)
        }
    }

    private fun closeSocket() {
        intentionalClose = true
        generation++
        webSocketRef.getAndSet(null)?.let {
            runCatching { it.close(1000, "desconectado") }
        }
    }

    fun disconnect() {
        scope.launch {
            closeSocket()
            store.clear()
            _state.value = ClientUiState()
        }
    }

    fun reconnect() {
        val cfg = config ?: return
        reconnectJob?.cancel()
        closeSocket()
        connect(cfg)
    }

    fun acceptCall() = callManager.accept()
    fun declineCall() = callManager.decline()
    fun hangUp() = callManager.hangUp()
    fun dismissCall() = callManager.dismiss()

    fun clearUnlockMessage() {
        _state.value = _state.value.copy(unlockMessage = null)
    }

    fun clearEnrollmentMessage() {
        _state.value = _state.value.copy(enrollmentMessage = null)
    }

    fun unlock(onResult: ((Boolean, String) -> Unit)? = null) {
        val socket = webSocketRef.get()
        if (socket != null && _state.value.status == ConnectionStatus.CONNECTED) {
            unlockCallback = onResult
            socket.send(JSONObject().put("type", WsType.UNLOCK).toString())
        } else {
            val cfg = config
            if (cfg == null) {
                onResult?.invoke(false, "Não configurado")
                return
            }
            scope.launch {
                val result = runCatching {
                    val request = Request.Builder()
                        .url("${cfg.httpBase}/api/unlock")
                        .header("X-Auth-Token", cfg.authToken)
                        .post("".toRequestBody("application/json".toMediaType()))
                        .build()
                    http.newCall(request).execute().use { response ->
                        val body = JSONObject(response.body?.string().orEmpty())
                        val ok = response.isSuccessful && body.optBoolean("ok", false)
                        ok to body.optString("message", if (ok) "Portão acionado" else "Falha")
                    }
                }.getOrElse { false to (it.message ?: "Falha de rede") }
                _state.value = _state.value.copy(unlockMessage = result.second)
                withContext(Dispatchers.Main) { onResult?.invoke(result.first, result.second) }
            }
        }
    }

    fun enrollFace(bitmap: Bitmap) {
        val cfg = config ?: return
        _state.value = _state.value.copy(enrollmentMessage = "Enviando biometria...")
        scope.launch {
            val jpeg = withContext(Dispatchers.Default) {
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                }
            }
            val result = runCatching {
                val request = Request.Builder()
                    .url("${cfg.httpBase}/api/face")
                    .header("X-Auth-Token", cfg.authToken)
                    .post(jpeg.toRequestBody("image/jpeg".toMediaType()))
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = JSONObject(response.body?.string().orEmpty())
                    response.isSuccessful to body.optString(
                        "message",
                        if (response.isSuccessful) "Biometria cadastrada" else "Falha no cadastro"
                    )
                }
            }.getOrElse { false to (it.message ?: "Falha de rede") }
            _state.value = _state.value.copy(enrollmentMessage = result.second)
        }
    }

    private fun showIncomingCallNotification(apartment: String, residents: List<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = apartment.ifBlank { "Portaria" }
        val text = residents.joinToString(", ").ifBlank { "Visitante na portaria" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Chamada da portaria — $title")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(2001, notification) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Chamadas da portaria",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    fun dispose() {
        closeSocket()
    }

    companion object {
        const val CHANNEL_ID = "porteiro_chamadas"

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                optString(index).ifBlank { null }
            }
        }
    }
}

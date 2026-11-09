package com.porteiro.server.network

import org.json.JSONObject

/**
 * Conteúdo do QR Code de pareamento (seção 3.1 da especificação).
 *
 * {
 *   "server_ip": "192.168.1.150",
 *   "port": 8080,
 *   "auth_token": "<uuid>",
 *   "device_id": "<identificador da portaria>"
 * }
 */
data class PairingPayload(
    val serverIp: String,
    val port: Int,
    val authToken: String,
    val deviceId: String
) {
    fun toJson(): String = JSONObject().apply {
        put("server_ip", serverIp)
        put("port", port)
        put("auth_token", authToken)
        put("device_id", deviceId)
    }.toString()

    companion object {
        fun fromJson(raw: String): PairingPayload? = runCatching {
            val json = JSONObject(raw)
            val ip = json.optString("server_ip").ifBlank { return null }
            val port = json.optInt("port", 8080)
            val token = json.optString("auth_token").ifBlank { return null }
            val deviceId = json.optString("device_id")
            PairingPayload(ip, port, token, deviceId)
        }.getOrNull()
    }
}

object WsType {
    const val HELLO = "hello"
    const val INCOMING_CALL = "incoming_call"
    const val CALL_CANCELLED = "call_cancelled"
    const val CALL_ENDED = "call_ended"
    const val WEBRTC_OFFER = "webrtc_offer"
    const val WEBRTC_ANSWER = "webrtc_answer"
    const val WEBRTC_ICE = "webrtc_ice"
    const val UNLOCK = "unlock"
    const val HANGUP = "hangup"
    const val REJECT = "reject"
    const val UNLOCK_RESULT = "unlock_result"
    const val ERROR = "error"
    const val PING = "ping"
    const val PONG = "pong"
}

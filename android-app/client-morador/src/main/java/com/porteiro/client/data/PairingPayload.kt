package com.porteiro.client.data

import org.json.JSONObject

data class PairingPayload(
    val serverIp: String,
    val port: Int,
    val authToken: String,
    val deviceId: String
) {
    fun toConfig(): PairingConfig = PairingConfig(serverIp, port, authToken, deviceId)

    companion object {
        fun fromJson(raw: String): PairingPayload? = runCatching {
            val json = JSONObject(raw)
            val ip = json.optString("server_ip").ifBlank { return null }
            val port = json.optInt("port", 8080)
            val token = json.optString("auth_token").ifBlank { return null }
            PairingPayload(ip, port, token, json.optString("device_id"))
        }.getOrNull()

        fun fromRaw(raw: String): PairingPayload? = fromJson(raw)
    }
}

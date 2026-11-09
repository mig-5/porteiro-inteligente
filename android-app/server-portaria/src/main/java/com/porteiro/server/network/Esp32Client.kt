package com.porteiro.server.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Esp32CommandResult(
    val success: Boolean,
    val httpCode: Int = 0,
    val message: String = ""
)

/**
 * Cliente HTTP responsável por acionar o relé do portão no ESP32.
 * Endpoint: POST http://<IP_DO_ESP32>/api/unlock
 */
class Esp32Client {

    private val client = OkHttpClient.Builder()
        .connectTimeout(900, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .writeTimeout(1200, TimeUnit.MILLISECONDS)
        .build()

    suspend fun openGate(
        ip: String,
        port: Int,
        sharedSecret: String,
        durationMs: Int,
        triggeredBy: String
    ): Esp32CommandResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("action", "open")
            put("duration_ms", durationMs.coerceIn(100, 5000))
            put("triggered_by", triggeredBy)
        }.toString()

        val request = Request.Builder()
            .url("http://$ip:$port/api/unlock")
            .header("X-App-Secret", sharedSecret)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val ok = response.isSuccessful && runCatching {
                    JSONObject(body).optBoolean("ok", false)
                }.getOrDefault(false)
                Esp32CommandResult(ok, response.code, body)
            }
        }.getOrElse { error ->
            Esp32CommandResult(false, 0, error.message ?: "falha de rede")
        }
    }
}

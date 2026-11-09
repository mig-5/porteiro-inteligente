package com.porteiro.server.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "server_settings")

data class ServerSettings(
    val serverPort: Int = 8080,
    val esp32Ip: String = "192.168.1.100",
    val esp32Port: Int = 80,
    val sharedSecret: String = "porteiro-secret-change-me",
    val similarityThreshold: Float = 0.75f,
    val relayDurationMs: Int = 1500,
    val autoUnlockEnabled: Boolean = true
)

class ServerSettingsStore(private val context: Context) {

    private object Keys {
        val PORT = intPreferencesKey("server_port")
        val ESP_IP = stringPreferencesKey("esp32_ip")
        val ESP_PORT = intPreferencesKey("esp32_port")
        val SECRET = stringPreferencesKey("shared_secret")
        val THRESHOLD = floatPreferencesKey("similarity_threshold")
        val DURATION = intPreferencesKey("relay_duration_ms")
        val AUTO_UNLOCK = androidx.datastore.preferences.core.booleanPreferencesKey("auto_unlock")
    }

    val settings: Flow<ServerSettings> = context.serverDataStore.data.map { prefs ->
        ServerSettings(
            serverPort = prefs[Keys.PORT] ?: 8080,
            esp32Ip = prefs[Keys.ESP_IP] ?: "192.168.1.100",
            esp32Port = prefs[Keys.ESP_PORT] ?: 80,
            sharedSecret = prefs[Keys.SECRET] ?: "porteiro-secret-change-me",
            similarityThreshold = prefs[Keys.THRESHOLD] ?: 0.75f,
            relayDurationMs = prefs[Keys.DURATION] ?: 1500,
            autoUnlockEnabled = prefs[Keys.AUTO_UNLOCK] ?: true
        )
    }

    suspend fun save(settings: ServerSettings) {
        context.serverDataStore.edit { prefs ->
            prefs[Keys.PORT] = settings.serverPort
            prefs[Keys.ESP_IP] = settings.esp32Ip
            prefs[Keys.ESP_PORT] = settings.esp32Port
            prefs[Keys.SECRET] = settings.sharedSecret
            prefs[Keys.THRESHOLD] = settings.similarityThreshold
            prefs[Keys.DURATION] = settings.relayDurationMs
            prefs[Keys.AUTO_UNLOCK] = settings.autoUnlockEnabled
        }
    }
}

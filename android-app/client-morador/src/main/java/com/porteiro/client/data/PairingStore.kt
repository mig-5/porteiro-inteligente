package com.porteiro.client.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.clientDataStore: DataStore<Preferences> by preferencesDataStore(name = "morador_config")

data class PairingConfig(
    val serverIp: String,
    val port: Int,
    val authToken: String,
    val deviceId: String
) {
    val wsUrl: String
        get() = "ws://$serverIp:$port/ws?token=$authToken&device_name=${java.net.URLEncoder.encode("Celular do morador", "UTF-8")}"

    val httpBase: String
        get() = "http://$serverIp:$port"
}

class PairingStore(private val context: Context) {

    private object Keys {
        val IP = stringPreferencesKey("server_ip")
        val PORT = intPreferencesKey("port")
        val TOKEN = stringPreferencesKey("auth_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val RESIDENT_NAME = stringPreferencesKey("resident_name")
        val APARTMENT = stringPreferencesKey("apartment")
    }

    val config: Flow<PairingConfig?> = context.clientDataStore.data.map { prefs ->
        val ip = prefs[Keys.IP]
        val token = prefs[Keys.TOKEN]
        if (ip.isNullOrBlank() || token.isNullOrBlank()) null
        else PairingConfig(
            serverIp = ip,
            port = prefs[Keys.PORT] ?: 8080,
            authToken = token,
            deviceId = prefs[Keys.DEVICE_ID] ?: ""
        )
    }

    val identity: Flow<Pair<String?, String?>> = context.clientDataStore.data.map { prefs ->
        prefs[Keys.RESIDENT_NAME] to prefs[Keys.APARTMENT]
    }

    suspend fun save(config: PairingConfig) {
        context.clientDataStore.edit { prefs ->
            prefs[Keys.IP] = config.serverIp
            prefs[Keys.PORT] = config.port
            prefs[Keys.TOKEN] = config.authToken
            prefs[Keys.DEVICE_ID] = config.deviceId
        }
    }

    suspend fun saveIdentity(residentName: String?, apartment: String?) {
        context.clientDataStore.edit { prefs ->
            if (residentName != null) prefs[Keys.RESIDENT_NAME] = residentName
            if (apartment != null) prefs[Keys.APARTMENT] = apartment
        }
    }

    suspend fun clear() {
        context.clientDataStore.edit { it.clear() }
    }
}

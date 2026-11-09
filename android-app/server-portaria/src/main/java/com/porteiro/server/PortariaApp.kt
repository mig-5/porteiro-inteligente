package com.porteiro.server

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.porteiro.server.data.PortariaDatabase
import com.porteiro.server.data.PortariaRepository
import com.porteiro.server.face.FaceRecognitionEngine
import com.porteiro.server.network.AccessController
import com.porteiro.server.network.Esp32Client
import com.porteiro.server.settings.ServerSettingsStore
import com.porteiro.server.webrtc.CallManager
import com.porteiro.server.webrtc.CameraStream

class PortariaApp : Application() {

    lateinit var repository: PortariaRepository
        private set
    lateinit var settingsStore: ServerSettingsStore
        private set
    lateinit var controller: PortariaController
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        repository = PortariaRepository(PortariaDatabase.get(this))
        settingsStore = ServerSettingsStore(this)

        val accessController = AccessController(repository, Esp32Client(), settingsStore)
        val camera = CameraStream(this)
        val callManager = CallManager(this, repository, camera)
        val faceEngine = FaceRecognitionEngine(
            context = this,
            repository = repository,
            accessController = accessController,
            camera = camera,
            settingsStore = settingsStore
        )

        controller = PortariaController(
            context = this,
            repository = repository,
            settingsStore = settingsStore,
            accessController = accessController,
            camera = camera,
            callManager = callManager,
            faceEngine = faceEngine
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.server_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "porteiro_server"
    }
}

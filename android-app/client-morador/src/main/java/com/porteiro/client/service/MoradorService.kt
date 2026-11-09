package com.porteiro.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.porteiro.client.MainActivity

/**
 * Mantém o processo do morador vivo para receber chamadas da portaria mesmo com
 * o aplicativo em segundo plano. A conexão WebSocket é gerenciada pelo
 * MoradorController (escopo da Application).
 */
class MoradorService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Porteiro Morador")
            .setContentText("Aguardando chamadas da portaria")
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Conexão com a portaria",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "porteiro_morador_service"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MoradorService::class.java))
        }
    }
}

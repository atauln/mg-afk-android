package com.mgafk.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mgafk.app.MainActivity
import com.mgafk.app.MgAfkApp

/**
 * Foreground service that keeps the app alive in background for AFK sessions.
 * The actual WebSocket connections live in the ViewModel/RoomClient;
 * this service just prevents Android from killing the process.
 */
class AfkService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Acquire partial wake lock to prevent CPU sleep
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mgafk::afk").apply {
            acquire(24 * 60 * 60 * 1000L) // 24h max
        }

        return START_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, MgAfkApp.CHANNEL_SERVICE)
            .setContentTitle("MG AFK")
            .setContentText("Session active in background")
            .setSmallIcon(android.R.drawable.ic_menu_rotate) // placeholder icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}

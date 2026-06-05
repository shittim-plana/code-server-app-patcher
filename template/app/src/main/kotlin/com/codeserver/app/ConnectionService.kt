package com.codeserver.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class ConnectionService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "codeserver_connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE_WAKELOCK = "com.codeserver.app.TOGGLE_WAKELOCK"
        private const val ACTION_TOGGLE_MOUSE = "com.codeserver.app.TOGGLE_MOUSE"
        private const val ACTION_TOGGLE_NAVBAR = "com.codeserver.app.TOGGLE_NAVBAR"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var mouseMode = false
            private set

        @Volatile
        var navBarHidden = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("ConnectionService", "Failed to start: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            val notification = buildNotification("세션 유지 중")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            isRunning = true
        } catch (e: Exception) {
            android.util.Log.e("ConnectionService", "onCreate failed: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_WAKELOCK -> {
                if (wakeLock?.isHeld == true) releaseWakeLock() else acquireWakeLock()
            }
            ACTION_TOGGLE_MOUSE -> { mouseMode = !mouseMode }
            ACTION_TOGGLE_NAVBAR -> {
                navBarHidden = !navBarHidden
                sendBroadcast(Intent("com.codeserver.app.NAVBAR_CHANGED"))
            }
        }
        updateNotification(statusText())
        return START_STICKY
    }

    private fun statusText(): String {
        val parts = mutableListOf<String>()
        if (wakeLock?.isHeld == true) parts.add("Wakelock")
        if (navBarHidden) parts.add("내비바 숨김")
        return if (parts.isEmpty()) "세션 유지 중" else "세션 유지 중 (${parts.joinToString(", ")})"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        fun serviceAction(requestCode: Int, action: String) = PendingIntent.getService(
            this, requestCode,
            Intent(this, ConnectionService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun activityAction(requestCode: Int, action: String) = PendingIntent.getActivity(
            this, requestCode,
            Intent(this, MainActivity::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wlLabel = if (wakeLock?.isHeld == true) "Wakelock 해제" else "Wakelock 설정"
        val navLabel = if (navBarHidden) "내비바 표시" else "내비바 숨기기"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(activityAction(0, "com.codeserver.app.COPY_URL"))
            .addAction(0, "설정", activityAction(1, "com.codeserver.app.SETTINGS"))
            .addAction(0, wlLabel, serviceAction(2, ACTION_TOGGLE_WAKELOCK))
            .addAction(0, navLabel, serviceAction(3, ACTION_TOGGLE_NAVBAR))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "codeserver::KeepAlive"
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}

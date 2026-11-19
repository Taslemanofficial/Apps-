package com.example.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Play-policy compliant dataSync Foreground Service that keeps simultaneous YouTube, Facebook,
 * and Instagram RTMPS broadcasts alive when the screen locks or the user switches apps.
 */
class BroadcastForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StreamCast::LiveBroadcastLock").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // 6h safety ceiling
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                MultiStreamBroadcaster.stopBroadcast(applicationContext)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val count = intent.getIntExtra(EXTRA_PLATFORM_COUNT, 1)
                val elapsed = intent.getLongExtra(EXTRA_ELAPSED_SEC, 0L)
                val kbps = intent.getIntExtra(EXTRA_BITRATE_KBPS, 0)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(count, elapsed, kbps))
                return START_STICKY
            }
            else -> {
                val count = intent?.getIntExtra(EXTRA_PLATFORM_COUNT, 1) ?: 1
                val notification = buildNotification(count, 0L, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
    }

    private fun buildNotification(platformCount: Int, elapsedSec: Long, bitrateKbps: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BroadcastForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mins = elapsedSec / 60
        val secs = elapsedSec % 60
        val timeText = "%02d:%02d".format(mins, secs)
        val detailText = "$platformCount প্ল্যাটফর্মে লাইভ • $timeText • $bitrateKbps kbps"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_live_title))
            .setContentText(detailText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPending)
            .addAction(0, getString(R.string.action_stop_live), stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "streamcast_live_broadcast"
        private const val NOTIFICATION_ID = 4041
        private const val ACTION_START = "com.example.streaming.START"
        private const val ACTION_UPDATE = "com.example.streaming.UPDATE"
        private const val ACTION_STOP = "com.example.streaming.STOP"
        private const val EXTRA_PLATFORM_COUNT = "platform_count"
        private const val EXTRA_ELAPSED_SEC = "elapsed_sec"
        private const val EXTRA_BITRATE_KBPS = "bitrate_kbps"

        fun start(context: Context, platformCount: Int) {
            val intent = Intent(context, BroadcastForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PLATFORM_COUNT, platformCount)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun updateStatus(context: Context, platformCount: Int, elapsedSec: Long, bitrateKbps: Int) {
            val intent = Intent(context, BroadcastForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PLATFORM_COUNT, platformCount)
                putExtra(EXTRA_ELAPSED_SEC, elapsedSec)
                putExtra(EXTRA_BITRATE_KBPS, bitrateKbps)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BroadcastForegroundService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}

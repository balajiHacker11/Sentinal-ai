package com.example.service

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
import com.example.MainActivity
import com.example.R

/**
 * Foreground Service that keeps the Power Button double-tap detector active
 * even when the screen is locked, phone in pocket, or app minimized.
 */
class PowerButtonSosService : Service() {

    private var detector: PowerButtonEmergencyDetector? = null

    companion object {
        const val CHANNEL_ID = "sentinel_power_button_guard_channel"
        const val NOTIFICATION_ID = 2024
        const val ACTION_START = "ACTION_START_POWER_GUARD"
        const val ACTION_STOP = "ACTION_STOP_POWER_GUARD"
        const val ACTION_TRIGGER_SOS = "ACTION_TRIGGER_SOS"

        fun start(context: Context) {
            val intent = Intent(context, PowerButtonSosService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PowerButtonSosService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        detector = PowerButtonEmergencyDetector(this).apply {
            setDangerListener {
                triggerDangerProtocol()
            }
            startListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                detector?.stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_SOS -> {
                triggerDangerProtocol()
            }
            else -> {
                val notification = buildForegroundNotification()
                startForeground(NOTIFICATION_ID, notification)
                detector?.startListening()
            }
        }
        return START_STICKY
    }

    private fun triggerDangerProtocol() {
        val sosIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_AUTO_TRIGGER_POWER_SOS", true)
        }
        startActivity(sosIntent)

        val sosManager = SosManager(this)
        val sirenPlayer = SirenPlayer(this)
        val audioRecorder = AudioRecorder(this)

        sosManager.triggerDirectCall("1091")
        sirenPlayer.startSiren()
        audioRecorder.startRecording()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Power Button Danger Guard",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active protection: Tap power button twice for emergency call, siren buzzer, and audio recording."
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sosIntent = Intent(this, PowerButtonSosService::class.java).apply {
            action = ACTION_TRIGGER_SOS
        }
        val sosPendingIntent = PendingIntent.getService(
            this,
            1,
            sosIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Power Button Danger Guard Active")
            .setContentText("Tap Power Button 2x for Instant Emergency Call, Buzzer & Audio Record")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_launcher_foreground, "🚨 Trigger SOS Now", sosPendingIntent)
            .build()
    }

    override fun onDestroy() {
        detector?.stopListening()
        detector = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

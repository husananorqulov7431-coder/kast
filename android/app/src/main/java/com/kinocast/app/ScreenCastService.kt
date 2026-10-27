package com.kinocast.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * Fon rejimida kino uzatishni boshqaruvchi yangilangan Foreground Service.
 * - Ekranni o'chirganda ham (WakeLock) audio davom etadi.
 * - 4 xonali PIN kod orqali ulanishni qo'llab-quvvatlaydi.
 */
class ScreenCastService : Service() {

    companion object {
        private const val TAG = "ScreenCastService"
        private const val CHANNEL_ID = "kino_cast_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_PIN_CODE = "EXTRA_PIN_CODE"

        @Volatile
        var isRunning = false
        @Volatile
        var currentPin = "----"
    }

    private var mediaProjection: MediaProjection? = null
    private var streamServer: LocalStreamServer? = null
    private var videoEncoder: VideoEncoderHelper? = null
    private var audioCapture: AudioCaptureHelper? = null
    private var orientationDetector: OrientationDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Ekranni o'chirganda ham jarayon uxlamasligi uchun WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KinoCast::WakeLock")
        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 soatgacha zaxira
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val pin = intent.getStringExtra(EXTRA_PIN_CODE) ?: "1234"
                currentPin = pin

                if (resultCode != 0 && resultData != null) {
                    startStreaming(resultCode, resultData, pin)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopStreaming()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startStreaming(resultCode: Int, resultData: Intent, pin: String) {
        val notification = createNotification("PIN: $pin | 16:9 kino rejimini kutilmoqda")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

            val mp = mediaProjection ?: run {
                Log.e(TAG, "MediaProjection olinmadi!")
                stopSelf()
                return
            }

            // 1. PIN-kodli lokal serverni boshlash
            streamServer = LocalStreamServer(this, 8080, 8081, pin) { clientCount ->
                Log.d(TAG, "Ulangan kompyuterlar: $clientCount")
            }.apply {
                start()
            }

            // 2. Ekran o'lchamlari
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            // 3. Video kodlovchini sozlash
            videoEncoder = VideoEncoderHelper(mp, metrics.densityDpi) { data, isKeyFrame ->
                streamServer?.broadcastVideo(data)
            }.apply {
                start()
            }
            streamServer?.setInitialVideoConfig(videoEncoder?.getCachedConfig())

            // 4. Faqat Media ovozlari (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                audioCapture = AudioCaptureHelper(mp) { audioData ->
                    streamServer?.broadcastAudio(audioData)
                }.apply {
                    start()
                }
            }

            // 5. 16:9 Orientatsiya detektori
            orientationDetector = OrientationDetector(this) { isLandscape16x9 ->
                videoEncoder?.isLandscape16x9 = isLandscape16x9
                streamServer?.broadcastStatus(isLandscape16x9)

                val statusText = if (isLandscape16x9) {
                    "PIN: $pin | Kino uzatilmoqda (16:9)"
                } else {
                    "PIN: $pin | Kutish rejimi (Telefonni 16:9 holatga buring)"
                }
                updateNotification(statusText)
            }.apply {
                start()
            }

            isRunning = true
            Log.d(TAG, "KinoCast v2.0 xizmati muvaffaqiyatli ishga tushdi (PIN: $pin)")
        } catch (e: Exception) {
            Log.e(TAG, "Uzatishni boshlashda xatolik: ${e.message}", e)
            stopSelf()
        }
    }

    private fun stopStreaming() {
        isRunning = false
        try {
            orientationDetector?.stop()
            orientationDetector = null

            audioCapture?.stop()
            audioCapture = null

            videoEncoder?.stop()
            videoEncoder = null

            streamServer?.stop()
            streamServer = null

            mediaProjection?.stop()
            mediaProjection = null

            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "KinoCast xizmati to'liq to'xtatildi va xotiradan tozalandi")
        } catch (e: Exception) {
            Log.e(TAG, "To'xtatishda xatolik: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KinoCast Xizmati",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kino uzatish xizmati holati"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ScreenCastService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activityIntent = Intent(this, MainActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎬 KinoCast (PIN: $currentPin)")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setContentIntent(activityPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "To'xtatish", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }
}

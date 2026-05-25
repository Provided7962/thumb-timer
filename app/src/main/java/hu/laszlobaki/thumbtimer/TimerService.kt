package hu.laszlobaki.thumbtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import android.app.PendingIntent

class TimerService : Service() {

    companion object {
        const val ACTION_STOP = "hu.laszlobaki.thumbtimer.ACTION_STOP"
        const val ACTION_RESET = "hu.laszlobaki.thumbtimer.ACTION_RESET"
        val remainingSecondsFlow = MutableStateFlow(0)
        val isRunningFlow = MutableStateFlow(false)
        val resetSignalFlow = MutableStateFlow(0)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var remainingSeconds = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET) {
            serviceScope.coroutineContext.cancelChildren()
            remainingSecondsFlow.value = 0
            isRunningFlow.value = false
            resetSignalFlow.value += 1
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            serviceScope.coroutineContext.cancelChildren()
            isRunningFlow.value = false
            stopSelf()
            return START_NOT_STICKY
        }
        remainingSeconds = intent?.getIntExtra("seconds", 0) ?: 0
        remainingSecondsFlow.value = remainingSeconds
        isRunningFlow.value = true

        createNotificationChannel()
        startForeground(1, buildNotification(remainingSeconds))

        serviceScope.launch {

            while (remainingSeconds > 0) {

                remainingSecondsFlow.value = remainingSeconds

                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE)
                            as NotificationManager

                notificationManager.notify(
                    1,
                    buildNotification(remainingSeconds)
                )

                delay(1000)

                remainingSeconds--
            }

            remainingSecondsFlow.value = 0
            isRunningFlow.value = false
            vibrate()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(seconds: Int): Notification {
        val minutes = seconds / 60
        val remaining = seconds % 60

        val timeText = "%02d:%02d".format(minutes, remaining)

        val pauseIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP
        }

        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_RESET
        }

        val resetPendingIntent = PendingIntent.getService(
            this,
            2,
            resetIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "thumb_timer_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Thumb Timer")
            .setContentText("Remaining: $timeText")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.mipmap.ic_launcher,
                "Pause",
                pausePendingIntent
            )
            .addAction(
                R.mipmap.ic_launcher,
                "Reset",
                resetPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "thumb_timer_channel",
                "Thumb Timer",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(
            VibrationEffect.createOneShot(
                800,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }
}
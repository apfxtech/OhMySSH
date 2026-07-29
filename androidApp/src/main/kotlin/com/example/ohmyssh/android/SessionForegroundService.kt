package com.example.ohmyssh.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.ohmyssh.session.SessionManager

private const val CHANNEL_ID = "ssh_sessions"
private const val SERVICE_ID = 3001

class SessionForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val count = intent?.getIntExtra(EXTRA_SESSION_COUNT, 0) ?: 0
        val notification = buildNotification(count)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SERVICE_ID, notification)
        }

        if (wakeLock == null) {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ohmyssh:sessions").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun buildNotification(count: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ohmyssh")
            .setContentText(if (count == 1) "1 session open" else "$count sessions open")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SSH sessions",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps open SSH sessions alive in the background"
            },
        )
    }

    companion object {
        const val EXTRA_SESSION_COUNT = "sessionCount"
    }
}

/// Follows the session list and starts/stops the service. Start requests made
/// while backgrounded are deferred to the next resume, which is what Android 12+
/// requires.
class SessionServiceController(private val context: Context) {
    private var running = false
    private var foreground = true
    private var wantRunning = false
    private var sessionCount = 0

    fun attach() {
        SessionManager.onSessionsChanged = {
            sessionCount = SessionManager.sessions.size
            wantRunning = SessionManager.hasLiveSessions
            sync()
        }
    }

    fun detach() {
        SessionManager.onSessionsChanged = null
        wantRunning = false
        sync()
    }

    fun onForeground(isForeground: Boolean) {
        val wasForeground = foreground
        foreground = isForeground
        if (isForeground && !wasForeground) sync()
    }

    private fun sync() {
        val intent = Intent(context, SessionForegroundService::class.java)
        when {
            wantRunning && foreground -> {
                intent.putExtra(SessionForegroundService.EXTRA_SESSION_COUNT, sessionCount)
                runCatching { context.startForegroundService(intent) }
                running = true
            }
            !wantRunning && running -> {
                runCatching { context.stopService(intent) }
                running = false
            }
        }
    }
}

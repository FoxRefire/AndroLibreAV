package com.example.yaraxsample

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that runs the YARA scan and shows progress in a notification.
 */
class ScanForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SCAN) {
            startForegroundWithNotification(0, 0, 0)
            runScan()
        } else if (intent?.action == ACTION_STOP_SCAN) {
            scanJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun runScan() {
        val rulesRepo = RulesRepository(this)
        val scanEngine = ScanEngine(this)
        scanJob?.cancel()

        scanJob = serviceScope.launch {
            scanEngine.scan(rulesRepo)
                .catch { e ->
                    ScanProgressHolder.setError(e.message ?: "Unknown error")
                    updateNotificationComplete(getString(R.string.rules_update_failed), isError = true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(false)
                    }
                    stopSelf()
                }
                .collectLatest { progress ->
                    ScanProgressHolder.update(
                        scanning = progress.scannedApps < progress.totalApps || progress.totalApps == 0,
                        scanned = progress.scannedApps,
                        total = progress.totalApps,
                        results = progress.results
                    )

                    if (progress.totalApps == 0 && progress.results.isEmpty()) {
                        updateNotificationComplete(getString(R.string.rules_update_failed), isError = true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(false)
                        }
                        stopSelf()
                        return@collectLatest
                    }

                    if (progress.totalApps > 0) {
                        val percent = if (progress.totalApps > 0) (progress.scannedApps * 100) / progress.totalApps else 0
                        updateNotification(progress.scannedApps, progress.totalApps, percent)

                        if (progress.scannedApps >= progress.totalApps) {
                            val message = if (progress.results.isEmpty()) {
                                getString(R.string.no_threats)
                            } else {
                                getString(R.string.scan_complete_matches, progress.results.size)
                            }
                            updateNotificationComplete(message, isError = false)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                stopForeground(STOP_FOREGROUND_DETACH)
                            } else {
                                @Suppress("DEPRECATION")
                                stopForeground(false)
                            }
                            stopSelf()
                        }
                    }
                }
        }
    }

    private fun startForegroundWithNotification(scanned: Int, total: Int, percent: Int) {
        createNotificationChannel()
        val notification = buildNotification(scanned, total, percent, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(scanned: Int, total: Int, percent: Int) {
        val notification = buildNotification(scanned, total, percent, null)
        getNotificationManager().notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationComplete(message: String, @Suppress("UNUSED_PARAMETER") isError: Boolean) {
        val notification = buildNotification(-1, -1, 100, message)
        getNotificationManager().notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(scanned: Int, total: Int, @Suppress("UNUSED_PARAMETER") percent: Int, completeMessage: String?): android.app.Notification {
        val contentText = when {
            completeMessage != null -> completeMessage
            total > 0 -> getString(R.string.scanning, scanned, total, (scanned * 100) / total)
            else -> getString(R.string.scanning_preparing)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_scan_title))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(completeMessage == null)
            .apply {
                if (total > 0 && completeMessage == null) {
                    setProgress(total, scanned, false)
                } else if (completeMessage != null) {
                    setProgress(0, 0, false)
                    setAutoCancel(true)
                }
            }
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            getNotificationManager().createNotificationChannel(channel)
        }
    }

    private fun getNotificationManager() =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "scan_progress"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_SCAN = "com.example.yaraxsample.START_SCAN"
        const val ACTION_STOP_SCAN = "com.example.yaraxsample.STOP_SCAN"
    }
}

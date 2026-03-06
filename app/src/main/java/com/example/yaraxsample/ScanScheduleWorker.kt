package com.example.yaraxsample

import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Worker that triggers a YARA scan by starting the ScanForegroundService.
 */
class ScanScheduleWorker(
    context: android.content.Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val intent = Intent(applicationContext, ScanForegroundService::class.java).apply {
                action = ScanForegroundService.ACTION_START_SCAN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

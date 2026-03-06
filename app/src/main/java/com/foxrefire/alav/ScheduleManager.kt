package com.foxrefire.alav

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules and cancels periodic scan and rules update workers.
 */
object ScheduleManager {

    private const val RULES_UPDATE_WORK_NAME = "rules_update"
    private const val SCAN_SCHEDULE_WORK_NAME = "scan_schedule"

    /**
     * Apply schedule based on current preferences. Call on app start and when settings change.
     */
    fun applySchedule(context: Context) {
        val workManager = WorkManager.getInstance(context)

        if (SchedulePreferences.getAutoRulesUpdateEnabled(context)) {
            val interval = SchedulePreferences.rulesIntervalMinutes(
                SchedulePreferences.getAutoRulesUpdateInterval(context)
            )
            val request = PeriodicWorkRequestBuilder<RulesUpdateWorker>(
                interval, TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(
                RULES_UPDATE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(RULES_UPDATE_WORK_NAME)
        }

        if (SchedulePreferences.getPeriodicScanEnabled(context)) {
            val interval = SchedulePreferences.scanIntervalMinutes(
                SchedulePreferences.getPeriodicScanInterval(context)
            )
            val request = PeriodicWorkRequestBuilder<ScanScheduleWorker>(
                interval, TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(
                SCAN_SCHEDULE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(SCAN_SCHEDULE_WORK_NAME)
        }
    }
}

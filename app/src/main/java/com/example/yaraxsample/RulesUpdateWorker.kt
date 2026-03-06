package com.example.yaraxsample

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker that downloads and updates yara-forge rules in the background.
 */
class RulesUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val rulesRepo = RulesRepository(applicationContext)
            rulesRepo.updateRules().getOrThrow()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

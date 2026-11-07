package com.matrixco.browser

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Refreshes the existing remote threat/domain feeds in the background.
 * RuleUpdater validates each source and only replaces the active database
 * after a sufficiently complete update; failed refreshes preserve old rules.
 */
class ThreatFeedWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        return try {
            val result = RuleUpdater.performScheduledUpdate(applicationContext)
            if (result.success) {
                android.util.Log.i(
                    "MaTRIX.ThreatFeeds",
                    "Feed refresh succeeded: ${result.domainsCount} domains"
                )
                Result.success()
            } else {
                android.util.Log.w("MaTRIX.ThreatFeeds", "Feed refresh not applied: ${result.message}")
                if (runAttemptCount < 3) Result.retry() else Result.success()
            }
        } catch (t: Throwable) {
            android.util.Log.e("MaTRIX.ThreatFeeds", "Feed refresh failed", t)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK = "matrix-threat-feed-refresh"
        private const val PERIOD_HOURS = 6L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = androidx.work.PeriodicWorkRequestBuilder<ThreatFeedWorker>(
                PERIOD_HOURS,
                java.util.concurrent.TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK)
                .build()

            androidx.work.WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK,
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}

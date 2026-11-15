package com.vuhpquang.appdepot.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Owns the WorkManager jobs: a 6 h periodic check, plus one check 10 s after each app start. */
object BackgroundScheduler {
    const val PERIODIC = "appdepot.check"
    const val STARTUP = "appdepot.check.startup"
    private const val PERIOD_HOURS = 6L
    private const val STARTUP_DELAY_S = 10L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CheckWorker>(PERIOD_HOURS, TimeUnit.HOURS).setConstraints(network).build(),
        )
        wm.enqueueUniqueWork(
            STARTUP,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CheckWorker>()
                .setInitialDelay(STARTUP_DELAY_S, TimeUnit.SECONDS)
                .setConstraints(network)
                .setInputData(workDataOf(CheckWorker.KEY_STARTUP to true))
                .build(),
        )
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(STARTUP)
    }
}

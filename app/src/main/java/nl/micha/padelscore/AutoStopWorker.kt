package nl.micha.padelscore

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.sync.ScoreSync
import java.util.concurrent.TimeUnit

/**
 * Stopt een partij die 3 uur geen punt meer kreeg, ook als de app dicht is:
 * het icoon op de wijzerplaat en de melding op de telefoon verdwijnen dan.
 * Elk punt plant hem opnieuw, dus tijdens het spelen gebeurt er niets.
 */
class AutoStopWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(MatchViewModel.PREFS, Context.MODE_PRIVATE)
        val state = prefs.getString(MatchViewModel.KEY_STATE, null)?.let { MatchState.decode(it) }
            ?: return Result.success()
        val idle = System.currentTimeMillis() - prefs.getLong(MatchViewModel.KEY_LAST_ACTIVITY, 0)
        // Iets speling: WorkManager kan net iets te vroeg starten.
        if (idle < MatchViewModel.AUTO_STOP_MS - 60_000) return Result.success()
        prefs.edit().remove(MatchViewModel.KEY_STATE).remove(MatchViewModel.KEY_HISTORY).apply()
        ScoreSync.send(applicationContext, state, active = false)
        MatchNotification.hide(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "auto-stop"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoStopWorker>()
                .setInitialDelay(MatchViewModel.AUTO_STOP_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

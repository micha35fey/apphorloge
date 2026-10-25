package nl.micha.padelscore.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import nl.micha.padelscore.sync.UpdateMessages

/** Zoekt en installeert een update op de achtergrond, bijvoorbeeld op verzoek van de telefoon. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val updater = Updater(
            applicationContext,
            tagPrefix = inputData.getString(KEY_TAG_PREFIX) ?: return Result.failure(),
            assetName = inputData.getString(KEY_ASSET) ?: return Result.failure(),
            silent = true,
        )
        return try {
            val current = inputData.getInt(KEY_VERSION_CODE, 0)
            val info = updater.checkOrThrow(current)
            if (info == null) {
                UpdateMessages.report(applicationContext, "Al actueel (1.$current)")
            } else {
                UpdateMessages.report(applicationContext, "Downloadt ${info.versionName}...")
                updater.install(info, fromBackground = true)
            }
            Result.success()
        } catch (e: Exception) {
            UpdateMessages.report(applicationContext, "Mislukt: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        private const val KEY_TAG_PREFIX = "tag_prefix"
        private const val KEY_ASSET = "asset"
        private const val KEY_VERSION_CODE = "version_code"

        fun enqueue(context: Context, tagPrefix: String, assetName: String, currentVersionCode: Int) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TAG_PREFIX to tagPrefix,
                        KEY_ASSET to assetName,
                        KEY_VERSION_CODE to currentVersionCode,
                    ),
                )
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("update", ExistingWorkPolicy.KEEP, request)
        }
    }
}

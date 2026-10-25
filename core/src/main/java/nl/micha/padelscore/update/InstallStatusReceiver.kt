package nl.micha.padelscore.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import nl.micha.padelscore.core.R
import nl.micha.padelscore.sync.UpdateMessages

/** Ontvangt de uitkomst van een installatiesessie van [Updater]. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val background = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        val text = when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) {
                    // Vanaf de achtergrond mag geen scherm openen; dan via een melding.
                    if (background) askConfirm(context, confirm) else runCatching { context.startActivity(confirm) }
                }
                "Bevestig de installatie"
            }
            PackageInstaller.STATUS_SUCCESS -> "Bijgewerkt"
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $status"
                "Mislukt: $message"
            }
        }
        UpdateEvents.status.value = text
        // Een update die de telefoon aanvroeg: laat de telefoon weten hoe het ging.
        if (background) UpdateMessages.report(context, text)
    }

    private fun askConfirm(context: Context, confirm: Intent) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            1,
            confirm,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_padel)
            .setContentTitle("Update klaar")
            .setContentText("Tik om Winner Padel bij te werken")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_BACKGROUND = "background"
        private const val CHANNEL_ID = "update"
        private const val NOTIFICATION_ID = 2
    }
}

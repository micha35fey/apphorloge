package nl.micha.padelscore.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import nl.micha.padelscore.score.Team
import nl.micha.padelscore.sync.ScoreSync
import nl.micha.padelscore.sync.SyncedScore
import nl.micha.padelscore.core.R as CoreR

/** Houdt een melding met de stand bij zolang er op het horloge een partij bezig is. */
class ScoreListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != ScoreSync.PATH) continue
            val score = ScoreSync.read(DataMapItem.fromDataItem(event.dataItem).dataMap) ?: continue
            ScoreNotification.update(this, score)
            Announcer.onScore(this, score)
        }
    }
}

object ScoreNotification {
    private const val CHANNEL_ID = "score"
    private const val NOTIFICATION_ID = 1

    fun update(context: Context, score: SyncedScore) {
        val manager = NotificationManagerCompat.from(context)
        if (!score.active) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        // Vanaf Android 13 moet je meldingen eerst toestaan.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Stand", NotificationManager.IMPORTANCE_LOW),
        )
        val state = score.state
        val details = listOfNotNull(
            state.setsText(),
            state.statusText ?: if (state.winner == null) "${state.server.label} serveert" else null,
        ).joinToString(" · ")
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CoreR.drawable.ic_stat_padel)
            .setContentTitle(state.headline())
            .setContentText(details)
            .setContentIntent(open)
            .setOngoing(state.winner == null)
            .setOnlyAlertOnce(true)
            .setColor(if (state.server == Team.A) 0xFF1565C0.toInt() else 0xFFC62828.toInt())
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

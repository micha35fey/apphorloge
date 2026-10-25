package nl.micha.padelscore

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
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.Team
import nl.micha.padelscore.core.R as CoreR

/**
 * Lopende activiteit tijdens een partij: een icoon op de wijzerplaat en in de app-lijst,
 * en Wear OS gaat bij het wakker worden terug naar de app in plaats van naar de wijzerplaat.
 */
object MatchNotification {
    private const val CHANNEL_ID = "match"
    private const val NOTIFICATION_ID = 10

    fun show(context: Context, state: MatchState) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Partij bezig", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val score = "${Team.A.label} ${state.pointLabel(Team.A)} - ${state.pointLabel(Team.B)} ${Team.B.label}" +
            "  (${state.gamesA}-${state.gamesB})"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CoreR.drawable.ic_stat_padel)
            .setContentTitle("Partij bezig")
            .setContentText(score)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(CoreR.drawable.ic_stat_padel)
            .setTouchIntent(open)
            .setStatus(Status.Builder().addTemplate(score).build())
            .build()
            .apply(context)

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

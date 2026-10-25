package nl.micha.padelscore.mobile

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import nl.micha.padelscore.sync.SyncedScore

/**
 * Niet storen tijdens een partij: meldingen van andere apps zijn stil, maar telefoontjes,
 * wekkers en media (de scheidsrechter) komen door. Na de partij komt de oude instelling terug.
 */
object DoNotDisturb {
    private const val PREFS = "phone"
    private const val KEY_ENABLED = "dnd"
    private const val KEY_SAVED = "dnd_saved"
    private const val KEY_FILTER = "dnd_filter"
    private const val KEY_CATEGORIES = "dnd_categories"
    private const val KEY_CALLERS = "dnd_callers"
    private const val KEY_MESSAGES = "dnd_messages"
    private const val KEY_VISUAL = "dnd_visual"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Android moet deze app eerst toegang geven tot Niet storen. */
    fun hasAccess(context: Context): Boolean = manager(context).isNotificationPolicyAccessGranted

    fun requestAccess(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) restore(context)
    }

    fun onScore(context: Context, score: SyncedScore) {
        val playing = score.active && score.state.winner == null
        if (playing && isEnabled(context)) silence(context) else restore(context)
    }

    private fun silence(context: Context) {
        val manager = manager(context)
        if (!manager.isNotificationPolicyAccessGranted) return
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_SAVED, false)) {
            val old = manager.notificationPolicy
            prefs.edit()
                .putBoolean(KEY_SAVED, true)
                .putInt(KEY_FILTER, manager.currentInterruptionFilter)
                .putInt(KEY_CATEGORIES, old.priorityCategories)
                .putInt(KEY_CALLERS, old.priorityCallSenders)
                .putInt(KEY_MESSAGES, old.priorityMessageSenders)
                .putInt(KEY_VISUAL, old.suppressedVisualEffects)
                .apply()
        }
        manager.notificationPolicy = NotificationManager.Policy(
            NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA,
            NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            NotificationManager.Policy.PRIORITY_SENDERS_ANY,
        )
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun restore(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_SAVED, false)) return
        val manager = manager(context)
        if (manager.isNotificationPolicyAccessGranted) {
            manager.notificationPolicy = NotificationManager.Policy(
                prefs.getInt(KEY_CATEGORIES, 0),
                prefs.getInt(KEY_CALLERS, 0),
                prefs.getInt(KEY_MESSAGES, 0),
                prefs.getInt(KEY_VISUAL, 0),
            )
            manager.setInterruptionFilter(prefs.getInt(KEY_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL))
        }
        prefs.edit().putBoolean(KEY_SAVED, false).apply()
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

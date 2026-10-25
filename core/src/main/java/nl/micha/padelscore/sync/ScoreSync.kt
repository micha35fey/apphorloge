package nl.micha.padelscore.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import nl.micha.padelscore.score.MatchState

/** Stand die het horloge deelt met de telefoon via de Wear Data Layer. */
data class SyncedScore(val state: MatchState, val active: Boolean)

object ScoreSync {
    const val PATH = "/score"
    private const val KEY_STATE = "state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_TIME = "time"

    /** Alle data items op dit pad, van elk gekoppeld apparaat. */
    val uri: Uri = Uri.Builder().scheme("wear").path(PATH).build()

    /** Horloge: stuur de huidige stand. Mislukt stil als er geen telefoon gekoppeld is. */
    fun send(context: Context, state: MatchState, active: Boolean) {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString(KEY_STATE, state.encode())
            dataMap.putBoolean(KEY_ACTIVE, active)
            // Tijd erbij zodat elke update als wijziging telt en echt verstuurd wordt.
            dataMap.putLong(KEY_TIME, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    /** Telefoon: lees de stand uit een ontvangen data item. */
    fun read(dataMap: DataMap): SyncedScore? {
        val state = dataMap.getString(KEY_STATE)?.let { MatchState.decode(it) } ?: return null
        return SyncedScore(state, dataMap.getBoolean(KEY_ACTIVE))
    }
}

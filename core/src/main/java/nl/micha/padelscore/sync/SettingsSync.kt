package nl.micha.padelscore.sync

import android.content.Context
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import nl.micha.padelscore.score.MatchSettings
import nl.micha.padelscore.score.decodeSettings
import nl.micha.padelscore.score.encode

/** Instellingen die je op de telefoon klaarzet voor de volgende partij op het horloge. */
object SettingsSync {
    const val PATH = "/settings"
    private const val KEY_SETTINGS = "settings"

    /** Horloge: laatst ontvangen instellingen, zodat een open startscherm ze meteen toont. */
    val incoming = MutableStateFlow<MatchSettings?>(null)

    /** Telefoon: stuur de instellingen. Komt ook aan als het horloge nu niet verbonden is. */
    fun send(context: Context, settings: MatchSettings) {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString(KEY_SETTINGS, settings.encode())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    fun read(dataMap: DataMap): MatchSettings? = dataMap.getString(KEY_SETTINGS)?.let { decodeSettings(it) }
}

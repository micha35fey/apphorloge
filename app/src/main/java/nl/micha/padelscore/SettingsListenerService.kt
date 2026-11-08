package nl.micha.padelscore

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import nl.micha.padelscore.score.encode
import nl.micha.padelscore.sync.SettingsSync

/** Ontvangt de instellingen die op de telefoon zijn klaargezet, ook als de app dicht is. */
class SettingsListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != SettingsSync.PATH) continue
            val settings = SettingsSync.read(DataMapItem.fromDataItem(event.dataItem).dataMap) ?: continue
            getSharedPreferences(MatchViewModel.PREFS, Context.MODE_PRIVATE).edit()
                .putString(MatchViewModel.KEY_SETTINGS, settings.encode())
                .apply()
            SettingsSync.incoming.value = settings
        }
    }
}

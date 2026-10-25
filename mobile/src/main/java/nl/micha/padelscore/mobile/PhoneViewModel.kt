package nl.micha.padelscore.mobile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import nl.micha.padelscore.sync.ScoreSync
import nl.micha.padelscore.sync.SyncedScore
import nl.micha.padelscore.update.UpdateInfo
import nl.micha.padelscore.update.Updater

class PhoneViewModel(app: Application) : AndroidViewModel(app) {

    private val dataClient = Wearable.getDataClient(app)

    /** Laatst ontvangen stand van het horloge, of null als er nog niets binnen is. */
    var score by mutableStateOf<SyncedScore?>(null)
        private set

    private val updater = Updater(
        app,
        tagPrefix = "telefoon-v",
        assetName = "padelscore-telefoon.apk",
        statusActivity = MainActivity::class.java,
    )

    var update by mutableStateOf<UpdateInfo?>(null)
        private set

    var updateStatus by mutableStateOf<String?>(null)

    private val listener = DataClient.OnDataChangedListener { events ->
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == ScoreSync.PATH) {
                ScoreSync.read(DataMapItem.fromDataItem(event.dataItem).dataMap)?.let { score = it }
            }
        }
    }

    init {
        dataClient.addListener(listener, ScoreSync.uri, DataClient.FILTER_PREFIX)
        // De stand die er al was voordat de app openging.
        dataClient.getDataItems(ScoreSync.uri, DataClient.FILTER_PREFIX).addOnSuccessListener { items ->
            items.forEach { item ->
                if (item.uri.path == ScoreSync.PATH) {
                    ScoreSync.read(DataMapItem.fromDataItem(item).dataMap)?.let { score = it }
                }
            }
            items.release()
        }
        viewModelScope.launch { update = updater.check(BuildConfig.VERSION_CODE) }
    }

    fun installUpdate() {
        val info = update ?: return
        updateStatus = "Downloaden..."
        viewModelScope.launch {
            updateStatus = runCatching { updater.install(info) }
                .fold(onSuccess = { "Installeren..." }, onFailure = { "Mislukt: ${it.message}" })
        }
    }

    override fun onCleared() {
        dataClient.removeListener(listener)
    }
}

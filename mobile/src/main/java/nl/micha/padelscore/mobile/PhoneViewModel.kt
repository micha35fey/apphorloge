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
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import nl.micha.padelscore.sync.ScoreSync
import nl.micha.padelscore.sync.SyncedScore
import nl.micha.padelscore.sync.UpdateMessages
import nl.micha.padelscore.update.UpdateEvents
import nl.micha.padelscore.update.UpdateInfo
import nl.micha.padelscore.update.Updater

class PhoneViewModel(app: Application) : AndroidViewModel(app) {

    private val dataClient = Wearable.getDataClient(app)

    /** Laatst ontvangen stand van het horloge, of null als er nog niets binnen is. */
    var score by mutableStateOf<SyncedScore?>(null)
        private set

    private val updater = Updater(app, tagPrefix = "telefoon-v", assetName = "padelscore-telefoon.apk", silent = false)

    var update by mutableStateOf<UpdateInfo?>(null)
        private set

    var updateStatus by mutableStateOf<String?>(null)

    /** Stand omroepen als een scheidsrechter. */
    var announce by mutableStateOf(Announcer.isEnabled(app))
        private set

    /** Niet storen tijdens een partij. */
    var dnd by mutableStateOf(DoNotDisturb.isEnabled(app))
        private set

    /** Of Android de app toegang tot Niet storen heeft gegeven. */
    var dndAccess by mutableStateOf(DoNotDisturb.hasAccess(app))
        private set

    /** Of er een eigen applausgeluid gekozen is. */
    var customApplause by mutableStateOf(Announcer.customApplause(app) != null)
        private set

    /** Welke stem er spreekt, bijvoorbeeld "Stem 2 van 5". */
    var voiceLabel by mutableStateOf("Standaardstem")
        private set

    /** Laatste melding van het horloge over zijn update. */
    var watchUpdateStatus by mutableStateOf<String?>(null)
        private set

    private val messageClient = Wearable.getMessageClient(app)

    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path == UpdateMessages.PATH_STATUS) watchUpdateStatus = String(event.data)
    }

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
        messageClient.addListener(messageListener)
        Announcer.prepare(app) { voiceLabel = Announcer.voiceLabel() }
        viewModelScope.launch { update = updater.check(BuildConfig.VERSION_CODE) }
        viewModelScope.launch { UpdateEvents.status.collect { if (it != null) updateStatus = it } }
    }

    fun updateAnnounce(on: Boolean) {
        announce = on
        Announcer.setEnabled(getApplication(), on)
    }

    fun updateDnd(on: Boolean) {
        dnd = on
        DoNotDisturb.setEnabled(getApplication(), on)
        if (on && !dndAccess) DoNotDisturb.requestAccess(getApplication())
    }

    /** Na terugkomen uit de instellingen: kijken of er nu toegang is. */
    fun refreshDndAccess() {
        dndAccess = DoNotDisturb.hasAccess(getApplication())
    }

    fun setApplause(uri: android.net.Uri?) {
        Announcer.setCustomApplause(getApplication(), uri)
        customApplause = uri != null
    }

    fun testApplause() = Announcer.testApplause(getApplication())

    fun nextVoice() {
        Announcer.nextVoice(getApplication())
        voiceLabel = Announcer.voiceLabel()
    }

    /** Vraag het horloge om zichzelf bij te werken. */
    fun updateWatch() {
        watchUpdateStatus = "Verzoek verstuurd..."
        UpdateMessages.requestWatchUpdate(getApplication())
    }

    fun installUpdate() {
        val info = update ?: return
        updateStatus = "Downloaden..."
        viewModelScope.launch {
            runCatching { updater.install(info) }.onFailure { updateStatus = "Mislukt: ${it.message}" }
        }
    }

    override fun onCleared() {
        dataClient.removeListener(listener)
        messageClient.removeListener(messageListener)
    }
}

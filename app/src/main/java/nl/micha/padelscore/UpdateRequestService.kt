package nl.micha.padelscore

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import nl.micha.padelscore.sync.UpdateMessages
import nl.micha.padelscore.update.UpdateWorker

/** De telefoon vraagt om bij te werken: zoek en installeer de nieuwste horloge-versie. */
class UpdateRequestService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != UpdateMessages.PATH_REQUEST) return
        UpdateWorker.enqueue(this, UPDATE_TAG_PREFIX, UPDATE_ASSET, BuildConfig.VERSION_CODE)
    }
}

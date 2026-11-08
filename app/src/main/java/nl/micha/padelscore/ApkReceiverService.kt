package nl.micha.padelscore

import android.net.Uri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import nl.micha.padelscore.sync.UpdateMessages
import nl.micha.padelscore.sync.WatchTransfer
import nl.micha.padelscore.update.UpdateWorker

/** Ontvangt een update die de telefoon heeft gedownload en stuurt, en laat hem installeren. */
class ApkReceiverService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != WatchTransfer.APK_PATH) return
        UpdateMessages.report(this, "Ontvangt update...")
        val file = WatchTransfer.apkFile(this)
        file.delete()
        Wearable.getChannelClient(this).receiveFile(channel, Uri.fromFile(file), false)
    }

    override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
        if (channel.path != WatchTransfer.APK_PATH) return
        // 0 = CLOSE_REASON_NORMAL: de telefoon heeft alles gestuurd.
        if (closeReason == 0) {
            UpdateWorker.enqueueFile(this, WatchTransfer.apkFile(this).path)
        } else {
            UpdateMessages.report(this, "Overdracht mislukt (code $closeReason)")
        }
    }
}

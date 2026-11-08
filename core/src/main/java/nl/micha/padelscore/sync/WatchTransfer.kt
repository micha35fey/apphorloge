package nl.micha.padelscore.sync

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bijwerken van het horloge via de telefoon: de telefoon downloadt de APK en stuurt hem over
 * de koppeling naar het horloge. Het horloge laat zijn versie weten, zodat de telefoon weet
 * of het nodig is.
 */
object WatchTransfer {
    /** Kanaal waarover de APK gaat. */
    const val APK_PATH = "/apk"
    private const val INFO_PATH = "/watch-info"
    private const val KEY_VERSION = "version"

    /** Waar het horloge de ontvangen APK neerzet. */
    fun apkFile(context: Context) = File(context.filesDir, "update.apk")

    /** Horloge: laat de telefoon weten welke versie er draait. */
    fun sendVersion(context: Context, versionCode: Int) {
        val request = PutDataMapRequest.create(INFO_PATH).apply {
            dataMap.putInt(KEY_VERSION, versionCode)
        }.asPutDataRequest()
        Wearable.getDataClient(context).putDataItem(request)
    }

    /** Telefoon: versie van het horloge, of null als die (nog) niet bekend is. */
    suspend fun readVersion(context: Context): Int? = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.Builder().scheme("wear").path(INFO_PATH).build()
        val items = Tasks.await(Wearable.getDataClient(context).getDataItems(uri, DataClient.FILTER_PREFIX))
        try {
            items.mapNotNull { DataMapItem.fromDataItem(it).dataMap.getInt(KEY_VERSION, 0).takeIf { v -> v > 0 } }
                .maxOrNull()
        } finally {
            items.release()
        }
    }

    /** Telefoon: stuur [file] naar elk verbonden horloge. Geeft het aantal horloges terug. */
    suspend fun sendApk(context: Context, file: File): Int = withContext(Dispatchers.IO) {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
        val channels = Wearable.getChannelClient(context)
        for (node in nodes) {
            val channel = Tasks.await(channels.openChannel(node.id, APK_PATH))
            Tasks.await(channels.getOutputStream(channel)).use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            Tasks.await(channels.close(channel))
        }
        nodes.size
    }
}

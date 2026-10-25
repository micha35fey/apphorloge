package nl.micha.padelscore.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable

/** Berichten tussen telefoon en horloge over updates. */
object UpdateMessages {
    /** Telefoon -> horloge: zoek een update en installeer die. */
    const val PATH_REQUEST = "/update"

    /** Horloge -> telefoon: tekst over hoe de update gaat. */
    const val PATH_STATUS = "/update-status"

    fun requestWatchUpdate(context: Context) = sendToAll(context, PATH_REQUEST, ByteArray(0))

    fun report(context: Context, text: String) = sendToAll(context, PATH_STATUS, text.toByteArray())

    private fun sendToAll(context: Context, path: String, data: ByteArray) {
        val messages = Wearable.getMessageClient(context)
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { messages.sendMessage(it.id, path, data) }
        }
    }
}

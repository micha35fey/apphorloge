package nl.micha.padelscore.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plakt WAV-bestanden (16-bit PCM, zoals de spraak van Android ze maakt) aan elkaar tot één
 * bestand, met stilte vooraf en tussen de stukken. Eén bestand speelt via Bluetooth in één keer
 * af; losse stukken starten de geluidsstroom steeds opnieuw en dan valt er makkelijk iets weg.
 */
object WavJoin {

    data class Pcm(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val data: ByteArray)

    /** Leest de PCM-gegevens uit een WAV-bestand, of null als het geen bruikbaar WAV is. */
    fun read(bytes: ByteArray): Pcm? {
        if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var sampleRate = 0
        var channels = 0
        var bits = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }
                "data" -> {
                    if (sampleRate == 0) return null
                    // Sommige maken laten de lengte op 0 of te groot: neem dan tot het einde.
                    val end = if (size <= 0 || body + size > bytes.size) bytes.size else body + size
                    return Pcm(sampleRate, channels, bits, bytes.copyOfRange(body, end))
                }
            }
            pos = body + size + (size and 1)
        }
        return null
    }

    /**
     * Grootte van het "stille" geluid: ongeveer -75 dB, niet te horen. Echte digitale stilte
     * (nullen) zien sommige Bluetooth-speakers als "geen geluid", en dan blijven ze slapen.
     */
    const val QUIET_LEVEL = 6

    /** Eén WAV met [leadMs] bijna-stilte vooraf en [pauseMs] bijna-stilte tussen de stukken. */
    fun join(parts: List<Pcm>, leadMs: Int, pauseMs: Int): ByteArray {
        require(parts.isNotEmpty())
        val first = parts.first()
        val bytesPerMs = first.sampleRate * first.channels * (first.bitsPerSample / 8) / 1000
        val random = java.util.Random(1)
        fun silence(ms: Int): ByteArray {
            val bytes = ByteArray(bytesPerMs * ms)
            if (first.bitsPerSample != 16) return bytes
            // Zachte ruis in plaats van nullen, 16-bit little-endian.
            for (i in 0 until bytes.size / 2) {
                val sample = random.nextInt(2 * QUIET_LEVEL + 1) - QUIET_LEVEL
                bytes[2 * i] = (sample and 0xFF).toByte()
                bytes[2 * i + 1] = (sample shr 8).toByte()
            }
            return bytes
        }
        val pcm = ByteArrayOutputStream()
        pcm.write(silence(leadMs))
        parts.forEachIndexed { i, part ->
            if (i > 0) pcm.write(silence(pauseMs))
            pcm.write(part.data)
        }
        return wav(first.sampleRate, first.channels, first.bitsPerSample, pcm.toByteArray())
    }

    private fun wav(sampleRate: Int, channels: Int, bits: Int, data: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
        header.putInt(sampleRate).putInt(sampleRate * channels * bits / 8)
        header.putShort((channels * bits / 8).toShort()).putShort(bits.toShort())
        header.put("data".toByteArray()).putInt(data.size)
        return header.array() + data
    }
}

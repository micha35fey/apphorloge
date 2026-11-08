package nl.micha.padelscore.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Korte gong (twee tonen, zoals een omroep in een sporthal), als 16-bit PCM. */
object Chime {
    fun pcm(sampleRate: Int, channels: Int): WavJoin.Pcm {
        val notes = listOf(660.0 to 0.45, 523.0 to 0.6)   // toonhoogte, duur in seconden
        val out = java.io.ByteArrayOutputStream()
        for ((freq, seconds) in notes) {
            val count = (sampleRate * seconds).toInt()
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val attack = minOf(1.0, t / 0.01)
                val decay = exp(-t * 4.5)
                val wave = sin(2 * PI * freq * t) + 0.3 * sin(2 * PI * freq * 2 * t)
                val sample = (wave * attack * decay * 0.3 * Short.MAX_VALUE).toInt()
                repeat(channels) {
                    out.write(sample and 0xFF)
                    out.write((sample shr 8) and 0xFF)
                }
            }
        }
        return WavJoin.Pcm(sampleRate, channels, 16, out.toByteArray())
    }
}

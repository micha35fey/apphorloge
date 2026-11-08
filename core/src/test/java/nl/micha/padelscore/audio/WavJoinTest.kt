package nl.micha.padelscore.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavJoinTest {
    private val rate = 16_000

    private fun part(samples: Int) = WavJoin.Pcm(rate, 1, 16, ByteArray(samples * 2) { 7 })

    @Test
    fun joinsWithLeadAndPauses() {
        val joined = WavJoin.join(listOf(part(100), part(50)), leadMs = 10, pauseMs = 20)
        val pcm = WavJoin.read(joined)!!
        assertEquals(rate, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertEquals(16, pcm.bitsPerSample)
        // 10 ms stilte + 100 samples + 20 ms stilte + 50 samples, 2 bytes per sample
        val expected = (rate / 1000 * 10 + 100 + rate / 1000 * 20 + 50) * 2
        assertEquals(expected, pcm.data.size)
        // Vooraf geen echte stilte maar onhoorbaar zachte ruis.
        val lead = (0 until rate / 1000 * 10).map { i ->
            ((pcm.data[2 * i + 1].toInt() shl 8) or (pcm.data[2 * i].toInt() and 0xFF)).toShort().toInt()
        }
        assertTrue(lead.all { kotlin.math.abs(it) <= WavJoin.QUIET_LEVEL })
        assertTrue(lead.any { it != 0 })
        assertEquals(7.toByte(), pcm.data[rate / 1000 * 10 * 2])
    }

    @Test
    fun rejectsNonWav() {
        assertNull(WavJoin.read("geen wav".toByteArray()))
    }

    @Test
    fun chimeComesBeforeTheWords() {
        val chime = Chime.pcm(rate, 1)
        val joined = WavJoin.read(WavJoin.join(listOf(part(10)), leadMs = 0, pauseMs = 0, prefix = chime))!!
        assertEquals(chime.data.size + rate / 1000 * 150 * 2 + 20, joined.data.size)
        assertTrue(chime.data.size > rate)  // ruim een halve seconde
    }
}

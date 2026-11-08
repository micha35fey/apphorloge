package nl.micha.padelscore.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(0.toByte(), pcm.data[0])
        assertEquals(7.toByte(), pcm.data[rate / 1000 * 10 * 2])
    }

    @Test
    fun rejectsNonWav() {
        assertNull(WavJoin.read("geen wav".toByteArray()))
    }
}

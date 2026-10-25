package nl.micha.padelscore.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun parsesBuildNumberFromTag() {
        assertEquals(12, ReleaseVersion.codeFromTag("v1.12"))
        assertEquals(3, ReleaseVersion.codeFromTag("1.3"))
    }

    @Test
    fun rejectsUnknownTags() {
        assertNull(ReleaseVersion.codeFromTag("v1"))
        assertNull(ReleaseVersion.codeFromTag("latest"))
        assertNull(ReleaseVersion.codeFromTag("v1.beta"))
    }
}

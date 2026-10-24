package com.orbit.home

import com.orbit.home.preferences.CentreChoice
import com.orbit.home.preferences.CentreChoicesCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CentreChoicesCodecTest {
    @Test
    fun `legacy centre migrates to wheel zero only`() {
        val migrated = CentreChoicesCodec.migrateLegacy("lib:orbit")
        assertEquals(listOf(CentreChoice.Library(CentreChoice.LIBRARY_ORBIT)), migrated)
        assertEquals("v1\n0=lib:orbit", CentreChoicesCodec.encode(migrated))
    }

    @Test
    fun `missing legacy choice migrates to no entry`() {
        val migrated = CentreChoicesCodec.migrateLegacy(null)
        assertTrue(migrated.isEmpty())
        assertEquals("v1", CentreChoicesCodec.encode(migrated))
    }

    @Test
    fun `explicit empty legacy value remains an explicit default`() {
        assertEquals(
            listOf(CentreChoice.Default),
            CentreChoicesCodec.migrateLegacy("")
        )
    }

    @Test
    fun `sparse wheel choices including explicit default round trip`() {
        val choices = listOf(
            CentreChoice.Library(CentreChoice.LIBRARY_ORBIT),
            null,
            CentreChoice.Default,
            CentreChoice.Imported
        )
        assertEquals(choices, CentreChoicesCodec.decode(CentreChoicesCodec.encode(choices)))
    }

    @Test
    fun `unknown storage version safely drops all entries`() {
        assertTrue(CentreChoicesCodec.decode("v9\n0=lib:orbit").isEmpty())
    }

    @Test
    fun `removing one wheel shifts later choices rather than dropping them`() {
        val choices = listOf(
            CentreChoice.Library(CentreChoice.LIBRARY_ORBIT),
            CentreChoice.Imported,
            CentreChoice.Video(
                com.orbit.home.preferences.CentreTransform(1f, 0f, 0f, 0)
            )
        )
        assertEquals(
            listOf(
                CentreChoice.Library(CentreChoice.LIBRARY_ORBIT),
                choices[2]
            ),
            CentreChoicesCodec.removeAt(choices, 1)
        )
    }
}

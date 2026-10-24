package com.orbit.home

import com.orbit.home.preferences.CentreChoice
import com.orbit.home.preferences.CentreCodec
import com.orbit.home.preferences.CentreLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The centre as it is stored.
 *
 * A stored value outlives the build that wrote it, so the thing worth testing is that nothing
 * unrecognised can turn into anything worse than the ordinary centre.
 */
class CentreChoiceTest {

    @Test
    fun `a choice survives the round trip`() {
        val choices = listOf(
            CentreChoice.Default,
            CentreChoice.Library(CentreChoice.LIBRARY_ORBIT),
            CentreChoice.Imported
        )
        for (choice in choices) {
            assertEquals(choice, CentreCodec.decode(CentreCodec.encode(choice)))
        }
    }

    @Test
    fun `nothing stored is the ordinary centre`() {
        assertEquals(CentreChoice.Default, CentreCodec.decode(null))
        assertEquals(CentreChoice.Default, CentreCodec.decode(""))
        assertEquals(CentreChoice.Default, CentreCodec.decode("   "))
    }

    @Test
    fun `an unrecognised value is the ordinary centre, never a crash`() {
        for (junk in listOf("nonsense", "lib:", "lib:  ", ":", "file:", "LIB:orbit", "2")) {
            assertEquals("$junk did not fall back", CentreChoice.Default, CentreCodec.decode(junk))
        }
    }

    @Test
    fun `a library id is carried through as it is`() {
        assertEquals(
            CentreChoice.Library("something-new"),
            CentreCodec.decode(CentreCodec.encode(CentreChoice.Library("something-new")))
        )
    }

    @Test
    fun `the library lists a way back and the app's own mark`() {
        assertEquals(CentreChoice.Default, CentreLibrary.DEFAULT.choice())
        assertEquals(
            CentreChoice.Library(CentreChoice.LIBRARY_ORBIT),
            CentreLibrary.ORBIT.choice()
        )
    }
}

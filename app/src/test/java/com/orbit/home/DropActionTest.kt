package com.orbit.home

import com.orbit.home.ui.DropAction
import com.orbit.home.ui.dropActionAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a carried app has to be for Remove or Uninstall to take it. */
class DropActionTest {
    private fun at(x: Float, y: Float) =
        dropActionAt(x, y, widthPx = 1000f, bottomPx = 160f, marginPx = 36f, gapPx = 30f)

    @Test fun leftChipRemoves() = assertEquals(DropAction.REMOVE, at(200f, 60f))

    @Test fun rightChipUninstalls() = assertEquals(DropAction.UNINSTALL, at(800f, 60f))

    @Test fun aboveTheGridIsNeither() = assertNull(at(200f, -5f))

    @Test fun belowTheBandIsNeither() = assertNull(at(200f, 160f))

    @Test fun sideMarginsAreNeither() {
        assertNull(at(10f, 60f))
        assertNull(at(990f, 60f))
    }

    @Test fun gapBetweenChipsIsNeither() = assertNull(at(500f, 60f))
}

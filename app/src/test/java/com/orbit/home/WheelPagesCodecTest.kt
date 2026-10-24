package com.orbit.home

import com.orbit.home.animation.WheelGeometry
import com.orbit.home.preferences.WheelLook
import com.orbit.home.preferences.WheelPage
import com.orbit.home.preferences.WheelPagesCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading and writing the list of wheels.
 *
 * The codec is what stands between a user's several wheels and losing them, so it is
 * tested for the awkward cases — a corrupt line, an unknown version, and a value
 * outside the range the wheel can actually use.
 */
class WheelPagesCodecTest {

    private val first = WheelPage(
        apps = listOf("com.a/A", "com.b/B"),
        initialized = true,
        look = WheelLook(visibleCount = 10, iconScale = 1.1f, radiusScale = 1.0f, centreScale = 1.2f, offsetY = 0.06f)
    )
    private val second = WheelPage(
        apps = listOf("com.c/C"),
        initialized = true,
        look = WheelLook(visibleCount = 6, iconScale = 1.4f, radiusScale = 0.9f, centreScale = 1.0f, offsetY = -0.09f)
    )

    @Test
    fun `one wheel survives a round trip`() {
        assertEquals(listOf(first), WheelPagesCodec.decode(WheelPagesCodec.encode(listOf(first))))
    }

    @Test
    fun `several wheels survive, in order and distinct`() {
        val decoded = WheelPagesCodec.decode(WheelPagesCodec.encode(listOf(first, second)))
        assertEquals(2, decoded.size)
        assertEquals(first, decoded[0])
        assertEquals(second, decoded[1])
    }

    @Test
    fun `component ids keep their slashes and dots`() {
        val page = WheelPage(apps = listOf("com.spotify.music/com.spotify.Main"), initialized = true)
        val decoded = WheelPagesCodec.decode(WheelPagesCodec.encode(listOf(page)))
        assertEquals(listOf("com.spotify.music/com.spotify.Main"), decoded[0].apps)
    }

    @Test
    fun `nothing stored means nothing decoded`() {
        assertEquals(emptyList<WheelPage>(), WheelPagesCodec.decode(null))
        assertEquals(emptyList<WheelPage>(), WheelPagesCodec.decode(""))
        assertEquals(emptyList<WheelPage>(), WheelPagesCodec.decode("   "))
    }

    @Test
    fun `a corrupt line is skipped and the rest still load`() {
        val raw = WheelPagesCodec.encode(listOf(first, second))
        val damaged = raw.split("\n").let { listOf(it[0], "garbage|nonsense", it[1]) }.joinToString("\n")
        val decoded = WheelPagesCodec.decode(damaged)
        assertEquals(2, decoded.size)
        assertEquals(first, decoded[0])
        assertEquals(second, decoded[1])
    }

    @Test
    fun `a line from an unknown version is ignored, not guessed at`() {
        assertEquals(emptyList<WheelPage>(), WheelPagesCodec.decode("v9|8|1.0|1.0|1.0|0.0|1|com.a/A"))
    }

    @Test
    fun `a stored value outside the usable range is clamped`() {
        // A count of 99 or an offset of 5 could not be shown; storing them back
        // unchanged would leave the panel unable to represent its own setting.
        val decoded = WheelPagesCodec.decode("v1|99|9.0|9.0|9.0|5.0|1|com.a/A")
        val look = decoded[0].look
        assertEquals(WheelGeometry.MAX_SLOTS, look.visibleCount)
        assertTrue(look.iconScale in WheelLook.ICON_RANGE)
        assertTrue(look.radiusScale in WheelLook.RADIUS_RANGE)
        assertTrue(look.centreScale in WheelLook.CENTRE_RANGE)
        assertTrue(look.offsetY in -WheelLook.MAX_OFFSET..WheelLook.MAX_OFFSET)
    }

    @Test
    fun `an untouched wheel keeps its untouched flag`() {
        val page = WheelPage()  // not initialized: shows every installed app
        val decoded = WheelPagesCodec.decode(WheelPagesCodec.encode(listOf(page)))
        assertEquals(false, decoded[0].initialized)
        assertEquals(emptyList<String>(), decoded[0].apps)
    }

    @Test
    fun `a wheel with no apps chosen is distinct from one never chosen`() {
        val choseNothing = WheelPage(apps = emptyList(), initialized = true)
        val decoded = WheelPagesCodec.decode(WheelPagesCodec.encode(listOf(choseNothing)))
        assertTrue(decoded[0].initialized)
    }
}

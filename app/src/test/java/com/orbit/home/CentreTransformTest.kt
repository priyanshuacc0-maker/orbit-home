package com.orbit.home

import com.orbit.home.preferences.CentreChoice
import com.orbit.home.preferences.CentreCodec
import com.orbit.home.preferences.CentreTransform
import com.orbit.home.preferences.CentreTransformCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A video's framing as it is stored.
 *
 * It matters more than a picture's framing does. A picture's framing is applied once and what is kept
 * is the result, so losing the numbers costs nothing; a video is framed by them every time a frame
 * is drawn, so numbers that come back wrong come back as a video inside out.
 *
 * The numbers are fractions of the frame rather than pixels, which is what lets the same framing
 * mean the same thing at the framing screen's size and at the centre's — and is why the encoding has
 * to survive a different frame size without changing its meaning.
 */
class CentreTransformTest {

    private val framing = CentreTransform(
        widthFraction = 1.25f,
        offsetXFraction = -0.03f,
        offsetYFraction = 0.0445f,
        quarterTurns = 3
    )

    @Test
    fun `a framing survives the round trip`() {
        val back = CentreTransformCodec.decode(CentreTransformCodec.encode(framing))!!
        assertEquals(framing.widthFraction, back.widthFraction, 0.0001f)
        assertEquals(framing.offsetXFraction, back.offsetXFraction, 0.0001f)
        assertEquals(framing.offsetYFraction, back.offsetYFraction, 0.0001f)
        assertEquals(framing.quarterTurns, back.quarterTurns)
    }

    @Test
    fun `negative offsets survive, because a picture may sit left or above the frame`() {
        val back = CentreTransformCodec.decode(
            CentreTransformCodec.encode(framing.copy(offsetXFraction = -0.9f, offsetYFraction = -0.0025f))
        )!!
        assertEquals(-0.9f, back.offsetXFraction, 0.0001f)
        assertEquals(-0.0025f, back.offsetYFraction, 0.0001f)
    }

    @Test
    fun `a whole choice carries its framing`() {
        val choice = CentreChoice.Video(framing)
        assertEquals(choice, CentreCodec.decode(CentreCodec.encode(choice)))
    }

    @Test
    fun `framing that cannot be read is no video at all`() {
        // Not the ordinary centre either: a video drawn without its framing would be a differently
        // framed centre, which is worse than none.
        for (junk in listOf(null, "", "video", "video:", "video:1,2,3", "video:1,2,3,4,5", "video:a,b,c,d")) {
            assertEquals("$junk did not fall back", CentreChoice.Default, CentreCodec.decode(junk))
        }
    }

    @Test
    fun `a framing with a width that cannot be drawn is refused`() {
        assertNull(CentreTransformCodec.decode("0,0,0,0"))
        assertNull(CentreTransformCodec.decode("-1,0,0,0"))
        assertNull(CentreTransformCodec.decode("NaN,0,0,0"))
        assertNull(CentreTransformCodec.decode("1,Infinity,0,0"))
    }

    @Test
    fun `whitespace does not change a framing`() {
        val back = CentreTransformCodec.decode(" 1.25 , -0.03 , 0.0445 , 3 ")!!
        assertEquals(1.25f, back.widthFraction, 0.0001f)
        assertEquals(3, back.quarterTurns)
    }

    @Test
    fun `a framing made at one frame size means the same at another`() {
        // The whole point of fractions. A video drawn at a quarter of a 400px frame is a tenth of a
        // 1000px one, and the stored numbers are the same either way.
        val madeAt400 = CentreTransform(widthFraction = 0.25f, 0f, 0f, 0)
        val drawnAt1000 = 1000f * madeAt400.widthFraction
        val drawnAt400 = 400f * madeAt400.widthFraction
        assertEquals(250f, drawnAt1000, 0.001f)
        assertEquals(100f, drawnAt400, 0.001f)
        // The same share of the frame in both, which is what "the same framing" means.
        assertEquals(drawnAt1000 / 1000f, drawnAt400 / 400f, 0.0001f)
    }

    @Test
    fun `a framing that is wider than its frame is allowed`() {
        // Zooming in is the normal case: the video is wider than the frame and is cropped by it.
        assertTrue(framing.widthFraction > 1f)
        assertEquals(
            CentreChoice.Video(framing),
            CentreCodec.decode(CentreCodec.encode(CentreChoice.Video(framing)))
        )
    }
}

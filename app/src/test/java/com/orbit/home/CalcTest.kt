package com.orbit.home

import com.orbit.home.ui.Calc
import com.orbit.home.ui.CalcState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The calculator's arithmetic and key rules. */
class CalcTest {

    private fun typed(vararg keys: String): CalcState =
        keys.fold(CalcState()) { state, key -> Calc.press(state, key) }

    private fun eval(expr: String): Double? = Calc.evaluate(expr)

    @Test
    fun `multiplication binds tighter than addition`() {
        assertEquals(14.0, eval("2+3\u00D74")!!, 0.0)
    }

    @Test
    fun `the screenshot sum`() {
        assertEquals(10000.0, eval("100\u00D7100")!!, 0.0)
    }

    @Test
    fun `brackets, squares, roots and percent`() {
        assertEquals(20.0, eval("(2+3)\u00D74")!!, 0.0)
        assertEquals(9.0, eval("3\u00B2")!!, 0.0)
        assertEquals(3.0, eval("\u221A(9)")!!, 0.0)
        assertEquals(0.5, eval("50%")!!, 0.0)
    }

    @Test
    fun `an open bracket is closed for the user`() {
        assertEquals(5.0, eval("(2+3")!!, 0.0)
    }

    @Test
    fun `brackets next to a value multiply`() {
        assertEquals(6.0, eval("2(3)")!!, 0.0)
    }

    @Test
    fun `division by zero and negative roots fail without crashing`() {
        assertTrue(eval("1\u00F70")!!.isNaN())
        assertTrue(eval("\u221A(\u22124)")!!.isNaN())
    }

    @Test
    fun `nonsense is null`() {
        assertNull(eval("2++"))
        assertNull(eval("."))
        assertNull(eval("2)"))
        assertNull(eval(""))
    }

    @Test
    fun `a dangling operator is ignored in the preview`() {
        assertEquals("5", Calc.preview("5+"))
        assertEquals("5", Calc.preview("5+("))
    }

    @Test
    fun `digits do not stack leading zeros and a number has one point`() {
        assertEquals("7", typed("0", "7").expr)
        assertEquals("1.5", typed("1", ".", "5", ".").expr)
        assertEquals("0.", typed(".").expr)
    }

    @Test
    fun `a second operator replaces the first`() {
        assertEquals("5\u00D7", typed("5", "+", "\u00D7").expr)
    }

    @Test
    fun `equals keeps the sum above and continues from the result`() {
        val done = typed("1", "0", "0", "\u00D7", "1", "0", "0", "=")
        assertEquals("10000", done.expr)
        assertEquals("100\u00D7100", done.previous)
        assertEquals("10000+", Calc.press(done, "+").expr)
        assertEquals("5", Calc.press(done, "5").expr)
    }

    @Test
    fun `equals on a failed sum changes nothing`() {
        val bad = typed("1", "\u00F7", "0")
        assertEquals(bad, Calc.press(bad, "="))
    }

    @Test
    fun `backspace removes a root and its bracket together`() {
        assertEquals("", typed("\u221A", "\u232B").expr)
        assertEquals("1", typed("1", "2", "\u232B").expr)
    }

    @Test
    fun `display puts spaces round joining operators only`() {
        assertEquals("100 \u00D7 100", Calc.display("100\u00D7100"))
        assertEquals("\u22125 + 3", Calc.display("\u22125+3"))
    }

    @Test
    fun `format groups thousands and drops trailing zeros`() {
        val grouped = Calc.format(10000.0)
        assertEquals(6, grouped.length)
        assertTrue(grouped.startsWith("10") && grouped.endsWith("000"))
        assertEquals("Error", Calc.format(Double.NaN))
    }
}

package com.orbit.home.ui

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormatSymbols
import kotlin.math.sqrt

/**
 * What the calculator shows and what it will do with the next key.
 *
 * [expr] is the expression as typed. After "=" it holds the result, [previous] holds the expression
 * that produced it (shown small above), and [evaluated] makes the next digit start afresh while an
 * operator carries on from the result.
 */
data class CalcState(
    val expr: String = "",
    val previous: String? = null,
    val evaluated: Boolean = false
)

/**
 * The calculator's arithmetic and key handling, pure so it can be tested without a screen.
 *
 * Operators are the display characters (minus, times, divide), so what the user sees is what is stored.
 * Unclosed brackets are closed for them, and a trailing operator is ignored while the result is only
 * being previewed.
 */
object Calc {
    const val MINUS = '\u2212'
    const val TIMES = '\u00D7'
    const val DIVIDE = '\u00F7'
    const val ROOT = '\u221A'
    const val SQUARE = '\u00B2'

    private const val BINARY_OPS = "+\u2212\u00D7\u00F7"

    /** The value of [expr], null if it is not a valid expression, NaN or infinite if the maths fails. */
    fun evaluate(expr: String): Double? {
        if (expr.isEmpty()) return null
        return try {
            val parser = Parser(expr)
            val value = parser.parseExpr()
            if (parser.atEnd()) value else null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** [expr] without the dangling operator or bracket the user is still in the middle of typing. */
    fun trimForPreview(expr: String): String =
        expr.trimEnd { it in BINARY_OPS || it == '(' || it == ROOT }

    /** The live result for [expr], formatted for the screen, or null while it cannot be worked out. */
    fun preview(expr: String): String? {
        val value = evaluate(trimForPreview(expr)) ?: return null
        return format(value)
    }

    /** [value] as the screen shows it: grouped, at most twelve significant digits, "Error" if it failed. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        val rounded = BigDecimal(value).round(MathContext(12)).stripTrailingZeros()
        val abs = rounded.abs()
        if (abs.signum() != 0 && (abs >= BigDecimal("1E15") || abs < BigDecimal("1E-9"))) {
            return rounded.round(MathContext(7)).stripTrailingZeros().toString()
        }
        val symbols = DecimalFormatSymbols.getInstance()
        val plain = rounded.toPlainString()
        val negative = plain.startsWith("-")
        val unsigned = plain.removePrefix("-")
        val whole = unsigned.substringBefore('.')
        val fraction = unsigned.substringAfter('.', "")
        val grouped = StringBuilder()
        whole.forEachIndexed { index, c ->
            if (index > 0 && (whole.length - index) % 3 == 0) grouped.append(symbols.groupingSeparator)
            grouped.append(c)
        }
        val sign = if (negative) "-" else ""
        return if (fraction.isEmpty()) sign + grouped else sign + grouped + symbols.decimalSeparator + fraction
    }

    /** [value] as a plain number that can go back into an expression. */
    private fun plain(value: Double): String {
        val rounded = BigDecimal(value).round(MathContext(12)).stripTrailingZeros()
        return rounded.toPlainString().replace('-', MINUS)
    }

    /** [expr] with spaces around the operators that join two things, so "100x100" reads "100 x 100". */
    fun display(expr: String): String {
        val out = StringBuilder()
        expr.forEachIndexed { index, c ->
            if (c in BINARY_OPS) {
                val before = expr.getOrNull(index - 1)
                val joins = before != null &&
                    (before.isDigit() || before == ')' || before == SQUARE || before == '%' || before == '.')
                if (joins) out.append(' ').append(c).append(' ') else out.append(c)
            } else {
                out.append(c)
            }
        }
        return out.toString()
    }

    /** The state after [key] is pressed: a digit, ".", an operator, a bracket, "=", backspace or "C". */
    fun press(state: CalcState, key: String): CalcState {
        val fresh = state.evaluated
        val base = if (fresh) "" else state.expr
        val carried = state.expr
        val last = if (fresh) null else state.expr.lastOrNull()
        return when (key) {
            "C" -> CalcState()
            "\u232B" -> {
                var next = state.expr.dropLast(1)
                // "root(" goes as one, or backspace would leave a bare root behind.
                if (state.expr.endsWith("$ROOT(")) next = state.expr.dropLast(2)
                CalcState(next)
            }
            "=" -> {
                val value = evaluate(trimForPreview(state.expr))
                val tidy = trimForPreview(state.expr)
                if (value == null || value.isNaN() || value.isInfinite() || tidy.matches(NUMBER)) state
                else CalcState(expr = plain(value), previous = tidy, evaluated = true)
            }
            "." -> {
                val run = base.takeLastWhile { it.isDigit() || it == '.' }
                when {
                    '.' in run -> state
                    run.isEmpty() -> CalcState(base + "0.")
                    else -> CalcState("$base.")
                }
            }
            "+", "\u2212", "\u00D7", "\u00F7" -> {
                val op = key[0]
                val from = if (fresh) carried else base
                val end = from.lastOrNull()
                when {
                    from.isEmpty() -> if (op == MINUS) CalcState(MINUS.toString()) else state
                    end == '(' -> if (op == MINUS) CalcState(from + op) else state
                    end == ROOT -> state
                    end != null && end in BINARY_OPS -> {
                        // A second operator replaces the first, unless it would leave a lone sign at the start.
                        val head = from.dropLast(1)
                        if (head.isEmpty() && op != MINUS) state else CalcState(head + op)
                    }
                    else -> CalcState(from + op)
                }
            }
            "%", "\u00B2" -> {
                val from = if (fresh) carried else base
                val end = from.lastOrNull()
                if (end != null && (end.isDigit() || end == ')' || end == SQUARE || end == '%')) CalcState(from + key)
                else state
            }
            "(" -> CalcState(base + "(")
            ")" -> {
                val open = base.count { it == '(' } - base.count { it == ')' }
                val end = base.lastOrNull()
                if (open > 0 && end != null && (end.isDigit() || end == ')' || end == SQUARE || end == '%' || end == '.'))
                    CalcState("$base)")
                else state
            }
            "\u221A" -> CalcState("$base$ROOT(")
            else -> {
                if (key.length == 1 && key[0].isDigit()) {
                    val run = base.takeLastWhile { it.isDigit() || it == '.' }
                    // A lone zero is replaced rather than followed, so 0 then 7 is 7 and not 07.
                    if (run == "0") CalcState(base.dropLast(1) + key) else CalcState(base + key)
                } else state
            }
        }
    }

    private val NUMBER = Regex("[0-9.]+")

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        private fun peek(): Char? = s.getOrNull(i)

        fun parseExpr(): Double {
            var value = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { i++; value += parseTerm() }
                    MINUS -> { i++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseUnary()
            while (true) {
                val c = peek()
                when {
                    c == TIMES -> { i++; value *= parseUnary() }
                    c == DIVIDE -> {
                        i++
                        val divisor = parseUnary()
                        value = if (divisor == 0.0) Double.NaN else value / divisor
                    }
                    // Brackets and roots next to a value multiply it: 2(3) is 6.
                    c != null && (c.isDigit() || c == '.' || c == '(' || c == ROOT) ->
                        value *= parsePostfix()
                    else -> return value
                }
            }
        }

        private fun parseUnary(): Double = when (peek()) {
            MINUS -> { i++; -parseUnary() }
            '+' -> { i++; parseUnary() }
            else -> parsePostfix()
        }

        private fun parsePostfix(): Double {
            var value = parsePrimary()
            while (true) {
                when (peek()) {
                    SQUARE -> { i++; value *= value }
                    '%' -> { i++; value /= 100.0 }
                    else -> return value
                }
            }
        }

        private fun parsePrimary(): Double {
            val c = peek() ?: throw IllegalArgumentException("unexpected end")
            return when {
                c.isDigit() || c == '.' -> parseNumber()
                c == '(' -> {
                    i++
                    val inner = parseExpr()
                    // A bracket left open at the end is closed for the user.
                    if (peek() == ')') i++ else if (!atEnd()) throw IllegalArgumentException("expected )")
                    inner
                }
                c == ROOT -> {
                    i++
                    val inner = parsePrimary()
                    if (inner < 0) Double.NaN else sqrt(inner)
                }
                else -> throw IllegalArgumentException("unexpected $c")
            }
        }

        private fun parseNumber(): Double {
            val start = i
            var dots = 0
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                if (s[i] == '.') dots++
                i++
            }
            val text = s.substring(start, i)
            if (dots > 1 || text == ".") throw IllegalArgumentException("bad number")
            return text.toDouble()
        }
    }
}

package com.orbit.home.preferences

/** Which wheel's centre a choice belongs to. */
enum class CentreTarget(val id: String) {
    HOME("home"),
    FLOATING("floating");

    /** A distinct private-file stem for each home wheel; the floating wheel is singular. */
    fun storageId(wheelIndex: Int = 0): String =
        if (this == HOME && wheelIndex > 0) "home-$wheelIndex" else id
}

/**
 * How a video is framed in the centre, as fractions of the frame.
 *
 * A picture's framing is applied once, when it is saved, and what is kept is the framed square. A
 * video cannot be treated that way — every frame would have to be re-rendered and re-encoded — so
 * its framing is kept as numbers and applied while drawing.
 *
 * Fractions rather than pixels, because the frame is a different size every time it is drawn: the
 * framing screen's square is the width of the screen, the home wheel's centre is a fraction of the
 * wheel, and both differ from device to device. Pixels would mean the same numbers described a
 * different framing on every screen — which is what they did.
 */
data class CentreTransform(
    /** The video's drawn width, as a share of the frame's side. */
    val widthFraction: Float,
    /** The video's centre, measured from the frame's centre, as a share of the frame's side. */
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val quarterTurns: Int
)

/**
 * A video's framing as it is stored: four numbers in a line.
 *
 * Written by this build and read by it, and anything it cannot read is null — so a centre that has
 * lost its framing falls back to something sensible rather than drawing a video inside out.
 */
object CentreTransformCodec {

    fun encode(transform: CentreTransform): String = listOf(
        transform.widthFraction,
        transform.offsetXFraction,
        transform.offsetYFraction,
        transform.quarterTurns.toFloat()
    ).joinToString(",")

    fun decode(raw: String?): CentreTransform? {
        val parts = raw?.split(",") ?: return null
        if (parts.size != 4) return null
        val numbers = parts.map { it.trim().toFloatOrNull() ?: return null }
        if (numbers.any { !it.isFinite() }) return null
        if (numbers[0] <= 0f) return null
        return CentreTransform(
            widthFraction = numbers[0],
            offsetXFraction = numbers[1],
            offsetYFraction = numbers[2],
            quarterTurns = numbers[3].toInt()
        )
    }
}

/**
 * What a wheel's centre shows.
 *
 * Every home wheel and the floating wheel have separate choices: they are seen in different
 * places and there is no reason a picture chosen for one should take over another.
 */
sealed interface CentreChoice {

    /** Nothing chosen, so the wheel shows what it always did. */
    data object Default : CentreChoice

    /** One of the built-in centres, by id. */
    data class Library(val id: String) : CentreChoice

    /** A picture the user brought in, kept in the app's own storage. */
    data object Imported : CentreChoice

    /**
     * A video the user brought in, with the framing it was given.
     *
     * The framing is part of the choice rather than a setting of its own: a video without its
     * framing is a different centre, and keeping the two together means they cannot come apart.
     */
    data class Video(val transform: CentreTransform) : CentreChoice

    companion object {
        /** The built-in centre that is the app's own mark. */
        const val LIBRARY_ORBIT = "orbit"
    }
}

/**
 * A centre as it is stored: one short string.
 *
 * A single line keeps this out of the wheel codec's way, and an unrecognised value reads as
 * [CentreChoice.Default] — so a value written by an older build, or a newer one, is a wheel that
 * shows its ordinary centre rather than a crash or a blank middle.
 */
object CentreCodec {

    private const val LIBRARY_PREFIX = "lib:"
    private const val IMPORTED = "file"
    private const val VIDEO_PREFIX = "video"

    fun encode(choice: CentreChoice): String = when (choice) {
        CentreChoice.Default -> ""
        is CentreChoice.Library -> LIBRARY_PREFIX + choice.id
        CentreChoice.Imported -> IMPORTED
        is CentreChoice.Video -> VIDEO_PREFIX + CentreTransformCodec.encode(choice.transform)
    }

    fun decode(raw: String?): CentreChoice {
        val value = raw?.trim().orEmpty()
        return when {
            value.isEmpty() -> CentreChoice.Default
            value == IMPORTED -> CentreChoice.Imported
            value.startsWith(VIDEO_PREFIX) -> {
                // A video with framing that cannot be read is no video at all: drawing it without
                // the framing would put a differently-framed centre on the wheel.
                CentreTransformCodec.decode(value.removePrefix(VIDEO_PREFIX))
                    ?.let { CentreChoice.Video(it) }
                    ?: CentreChoice.Default
            }
            value.startsWith(LIBRARY_PREFIX) -> {
                val id = value.removePrefix(LIBRARY_PREFIX)
                if (id.isEmpty()) CentreChoice.Default else CentreChoice.Library(id)
            }
            else -> CentreChoice.Default
        }
    }
}



/**
 * The home wheel centres, stored sparsely by wheel index.
 *
 * This is deliberately separate from [CentreCodec]: that codec still describes one choice and
 * remains the decoder for the legacy `centre_home` value and the single floating wheel. The
 * leading version makes a future storage change fail closed in the same way as [WheelPagesCodec].
 * Missing indices are represented by null, while an explicit [CentreChoice.Default] remains a
 * real entry (`0=`). This lets the migration distinguish “never chosen” from “the user chose the
 * ordinary centre”, and lets choices at higher indices survive a temporary reduction in wheel count.
 */
object CentreChoicesCodec {
    private const val VERSION = "v1"
    private const val MAX_INDEX = 63

    fun encode(choices: List<CentreChoice?>): String = buildList {
        add(VERSION)
        choices.forEachIndexed { index, choice ->
            if (choice != null && index <= MAX_INDEX) {
                add("$index=${CentreCodec.encode(choice)}")
            }
        }
    }.joinToString("\n")

    /** Unknown versions and malformed entries are discarded, never allowed to crash a reader. */
    fun decode(raw: String?): List<CentreChoice?> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.split('\n')
        if (lines.firstOrNull() != VERSION) return emptyList()
        val result = mutableListOf<CentreChoice?>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val index = line.substring(0, separator).toIntOrNull() ?: continue
            if (index !in 0..MAX_INDEX) continue
            while (result.size <= index) result.add(null)
            // Empty after '=' is meaningful: it is an explicitly chosen Default centre.
            result[index] = CentreCodec.decode(line.substring(separator + 1))
        }
        return result
    }

    /**
     * The one-time legacy migration. A missing key means the user never chose a centre, so it
     * produces no entry. An existing empty string is different: it is an explicit Default choice.
     */
    fun migrateLegacy(legacyRaw: String?): List<CentreChoice?> =
        if (legacyRaw == null) emptyList() else listOf(CentreCodec.decode(legacyRaw))

    fun setAt(
        choices: List<CentreChoice?>,
        index: Int,
        choice: CentreChoice
    ): List<CentreChoice?> {
        if (index !in 0..MAX_INDEX) return choices
        val result = choices.toMutableList()
        while (result.size <= index) result.add(null)
        result[index] = choice
        return result
    }

    /** Removes the selected wheel's entry and shifts later entries with their wheel positions. */
    fun removeAt(choices: List<CentreChoice?>, index: Int): List<CentreChoice?> {
        if (index < 0 || index !in choices.indices) return choices
        val result = choices.toMutableList().apply { removeAt(index) }
        while (result.isNotEmpty() && result.last() == null) result.removeAt(result.lastIndex)
        return result
    }
}

/** One of the centres that ships with the app, for the Library to list. */
enum class CentreLibrary(val id: String) {
    /** Not really a centre: the way back to the wheel's own middle. */
    DEFAULT(""),
    ORBIT(CentreChoice.LIBRARY_ORBIT);

    /** The choice this entry stands for. */
    fun choice(): CentreChoice =
        if (this == DEFAULT) CentreChoice.Default else CentreChoice.Library(id)
}

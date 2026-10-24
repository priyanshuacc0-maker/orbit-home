package com.orbit.home.ui

/**
 * What a home screen holds the first time it is looked at.
 *
 * A fresh phone's home screen is not empty. It has a few apps low on the first page and the rest on the
 * pages after, because a home screen you have to fill before it is any use is a home screen nobody
 * uses.
 *
 * It is a starting arrangement, not a rule: it is written down once, as the apps the screen holds, and
 * from then on it is the user's. That is why this produces cells and not a layout - the grid stays one
 * grid, and only the apps themselves sit low.
 *
 * Nothing here names an app. Which apps a phone starts with is a matter of taste, and the taste this
 * app should have is the user's.
 */
object HomeSeed {

    /** How many rows the first page's apps start from. */
    const val FIRST_PAGE_ROWS = 2

    /** How many pages' worth of apps a new home screen starts with. */
    private const val PAGES = 2

    /**
     * [ids] arranged so the first page has its top clear, as cells.
     *
     * The first page holds its apps at the bottom and every page after it is full, so it looks like a
     * home screen rather than a list. The leading empties are real empties - the drawer's apps are not
     * this screen's apps, so there is nothing left over to fill them with, and the space stays free for
     * the user to put something in.
     */
    fun cells(
        ids: List<String>,
        columns: Int,
        rowsPerPage: Int,
        firstPageRows: Int = FIRST_PAGE_ROWS
    ): List<String> {
        if (ids.isEmpty() || columns <= 0 || rowsPerPage <= 0) return emptyList()
        val perPage = columns * rowsPerPage
        val onFirst = (columns * firstPageRows).coerceIn(0, perPage)
        // Two pages at most: a home screen that goes on for ever is a drawer with extra steps.
        val wanted = (onFirst + perPage * (PAGES - 1)).coerceAtMost(ids.size)

        val out = ArrayList<String>(wanted + perPage - onFirst)
        repeat(perPage - onFirst) { out.add("") }
        for (index in 0 until wanted) out.add(ids[index])
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        return out
    }
}

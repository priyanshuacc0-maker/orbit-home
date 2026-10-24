package com.orbit.home

import com.orbit.home.preferences.GridFolder
import com.orbit.home.preferences.GridFolderCodec
import com.orbit.home.ui.FolderLayout
import com.orbit.home.ui.GridFolders
import com.orbit.home.ui.GridOrder.EMPTY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Folders on the grid: making them, filling them, emptying them, and storing them.
 *
 * The thing every one of these protects is that an app is in exactly one place - one cell or one
 * folder, never both and never neither - because that is what a drag that duplicates or loses an app
 * looks like.
 */
class GridFoldersTest {

    private val f1 = "folder:one"

    private fun layout(cells: List<String>, vararg folders: GridFolder) =
        FolderLayout(cells, folders.toList())

    /** Every app id the layout holds, in cells or in folders. */
    private fun everyApp(l: FolderLayout): List<String> =
        l.cells.filter { it != EMPTY && !GridFolders.isFolderId(it) } + l.folders.flatMap { it.apps }

    // ---- make ----

    @Test
    fun `dropping an app on another makes a folder where that one was`() {
        val out = GridFolders.make(layout(listOf("a", "b", "c")), 1, "a", f1, "Folder")
        assertEquals(listOf(EMPTY, f1, "c"), out.cells)
        assertEquals(listOf(GridFolder(f1, "Folder", listOf("b", "a"))), out.folders)
    }

    @Test
    fun `an app from outside the grid makes a folder too, and nothing else moves`() {
        val out = GridFolders.make(layout(listOf("a", EMPTY, "c")), 2, "new", f1, "Folder")
        assertEquals(listOf("a", EMPTY, f1), out.cells)
        assertEquals(listOf("c", "new"), out.folders.single().apps)
    }

    @Test
    fun `a folder cannot be made on an empty cell, on a folder, or on the app itself`() {
        val start = layout(listOf("a", EMPTY, f1), GridFolder(f1, "x", listOf("p", "q")))
        assertEquals(start, GridFolders.make(start, 1, "a", "folder:new", "n"))
        assertEquals(start, GridFolders.make(start, 2, "a", "folder:new", "n"))
        assertEquals(start, GridFolders.make(start, 0, "a", "folder:new", "n"))
    }

    @Test
    fun `a folder cannot be dropped into a new folder`() {
        val start = layout(listOf("a", f1), GridFolder(f1, "x", listOf("p", "q")))
        assertEquals(start, GridFolders.make(start, 0, f1, "folder:new", "n"))
    }

    // ---- putInto ----

    @Test
    fun `an app dropped on a folder joins it and leaves its cell`() {
        val start = layout(listOf("a", f1, "c"), GridFolder(f1, "x", listOf("p", "q")))
        val out = GridFolders.putInto(start, f1, "a")
        assertEquals(listOf(EMPTY, f1, "c"), out.cells)
        assertEquals(listOf("p", "q", "a"), out.folders.single().apps)
    }

    @Test
    fun `a full folder takes nothing more`() {
        val apps = (1..GridFolders.MAX_APPS).map { "app$it" }
        val start = layout(listOf("a", f1), GridFolder(f1, "x", apps))
        assertEquals(start, GridFolders.putInto(start, f1, "a"))
    }

    @Test
    fun `an app already in the folder is not added twice`() {
        val start = layout(listOf(f1), GridFolder(f1, "x", listOf("p", "q")))
        assertEquals(start, GridFolders.putInto(start, f1, "p"))
    }

    @Test
    fun `an app moved from one folder to another leaves the first`() {
        val f2 = "folder:two"
        val start = layout(
            listOf(f1, f2),
            GridFolder(f1, "x", listOf("p", "q", "r")),
            GridFolder(f2, "y", listOf("s", "t"))
        )
        val out = GridFolders.putInto(start, f2, "p")
        assertEquals(listOf("q", "r"), out.folders.first { it.id == f1 }.apps)
        assertEquals(listOf("s", "t", "p"), out.folders.first { it.id == f2 }.apps)
    }

    // ---- takeOut ----

    @Test
    fun `taking an app out of a big folder leaves the folder`() {
        val start = layout(listOf(f1), GridFolder(f1, "x", listOf("p", "q", "r")))
        val out = GridFolders.takeOut(start, "q")
        assertEquals(listOf(f1), out.cells)
        assertEquals(listOf("p", "r"), out.folders.single().apps)
    }

    @Test
    fun `a folder left with one app becomes that app, in the same cell`() {
        val start = layout(listOf("a", f1, "c"), GridFolder(f1, "x", listOf("p", "q")))
        val out = GridFolders.takeOut(start, "p")
        assertEquals(listOf("a", "q", "c"), out.cells)
        assertTrue(out.folders.isEmpty())
    }

    @Test
    fun `an app that is in no folder changes nothing`() {
        val start = layout(listOf("a", f1), GridFolder(f1, "x", listOf("p", "q")))
        assertEquals(start, GridFolders.takeOut(start, "a"))
    }

    // ---- the one-place rule ----

    @Test
    fun `no operation loses or doubles an app`() {
        val start = layout(
            listOf("a", "b", f1, "c", EMPTY, "d"),
            GridFolder(f1, "x", listOf("p", "q", "r"))
        )
        val before = everyApp(start).sorted()
        val results = listOf(
            GridFolders.make(start, 1, "a", "folder:n", "n"),
            GridFolders.make(start, 0, "d", "folder:n", "n"),
            GridFolders.putInto(start, f1, "a"),
            GridFolders.putInto(start, f1, "d")
        )
        for (out in results) assertEquals(before, everyApp(out).sorted())

        // Taking an app out hands it to the caller to put down, so it is the one app not accounted for.
        val one = GridFolders.takeOut(start, "p")
        assertEquals(before - "p", everyApp(one).sorted())
        val two = GridFolders.takeOut(one, "q")
        assertEquals(before - "p" - "q", everyApp(two).sorted())
    }

    @Test
    fun `removing a folder empties its cell and keeps the rest where it was`() {
        val start = layout(listOf("a", f1, "c"), GridFolder(f1, "x", listOf("p", "q")))
        val out = GridFolders.remove(start, f1)
        assertEquals(listOf("a", EMPTY, "c"), out.cells)
        assertTrue(out.folders.isEmpty())
    }

    // ---- which ids have a cell ----

    @Test
    fun `apps in a folder have no cell of their own, and the folder has one`() {
        val ids = GridFolders.cellIds(
            homeIds = listOf("a", "p", "q"),
            folders = listOf(GridFolder(f1, "x", listOf("p", "q"))),
            installed = setOf("a", "p", "q")
        )
        assertEquals(listOf("a", f1), ids)
    }

    @Test
    fun `a folder whose apps are all gone has no cell`() {
        val ids = GridFolders.cellIds(
            homeIds = listOf("a", "p"),
            folders = listOf(GridFolder(f1, "x", listOf("p", "q"))),
            installed = setOf("a")
        )
        assertEquals(listOf("a"), ids)
    }

    @Test
    fun `a new id is not one already used`() {
        val taken = setOf("folder:aaaa")
        repeat(50) { assertTrue(GridFolders.newId(taken) !in taken) }
        assertTrue(GridFolders.isFolderId(GridFolders.newId(emptySet())))
    }

    // ---- storage ----

    @Test
    fun `folders survive a round trip through the stored form`() {
        val folders = listOf(
            GridFolder("folder:1", "Games", listOf("a/b", "c/d")),
            GridFolder("folder:2", "", listOf("e/f"))
        )
        assertEquals(folders, GridFolderCodec.decode(GridFolderCodec.encode(folders)))
    }

    @Test
    fun `a name with a tab or a line break cannot break the record`() {
        val out = GridFolderCodec.decode(
            GridFolderCodec.encode(listOf(GridFolder("folder:1", "a\tb\nc", listOf("x/y"))))
        )
        assertEquals("a b c", out.single().name)
        assertEquals(listOf("x/y"), out.single().apps)
    }

    @Test
    fun `an app is kept in the first folder that claims it`() {
        val out = GridFolderCodec.normalise(
            listOf(
                GridFolder("folder:1", "a", listOf("x", "y")),
                GridFolder("folder:2", "b", listOf("y", "z"))
            )
        )
        assertEquals(listOf("x", "y"), out[0].apps)
        assertEquals(listOf("z"), out[1].apps)
    }

    @Test
    fun `bad records are skipped rather than failing the rest`() {
        val raw = "garbage\nfolder:1\tName\tx/y\n\t\t\nfolder:2\tOnlyName"
        assertEquals(listOf("folder:1"), GridFolderCodec.decode(raw).map { it.id })
    }

    @Test
    fun `a folder with no apps, or with a repeated id, is dropped`() {
        val out = GridFolderCodec.normalise(
            listOf(
                GridFolder("folder:1", "a", listOf("x")),
                GridFolder("folder:1", "again", listOf("y")),
                GridFolder("folder:3", "empty", listOf("", " "))
            )
        )
        assertEquals(listOf("folder:1"), out.map { it.id })
        assertEquals("a", out.single().name)
    }

    @Test
    fun `a long name is cut`() {
        val out = GridFolderCodec.normalise(listOf(GridFolder("folder:1", "x".repeat(100), listOf("a"))))
        assertEquals(GridFolderCodec.MAX_NAME, out.single().name.length)
    }

    @Test
    fun `nothing stored reads as no folders`() {
        assertTrue(GridFolderCodec.decode(null).isEmpty())
        assertTrue(GridFolderCodec.decode("").isEmpty())
    }
}

package com.orbit.home.ui

import com.orbit.home.preferences.GridFolder
import com.orbit.home.ui.GridOrder.EMPTY
import java.util.UUID

/** The grid's cells and its folders, which have to change together or not at all. */
data class FolderLayout(val cells: List<String>, val folders: List<GridFolder>)

/**
 * Folders on the grid home, as pure operations on a [FolderLayout].
 *
 * A folder is a cell. Its cell holds the folder's id, and the apps inside it are in no cell at all -
 * so every app is in exactly one place, which is what keeps a drag from ever duplicating or losing
 * one. Everything that moves an app in or out of a folder is here, and returns the whole new layout,
 * so the cells and the folders that describe them cannot be saved out of step.
 *
 * Pure and separate for the same reason [GridOrder] is: these mistakes are silent. An app left in its
 * old cell as well as the folder is on the screen twice; a folder left with one app is a folder that
 * holds nothing worth opening.
 */
object GridFolders {

    /** What marks a cell as a folder. App ids are `package/activity`, which can never start with this. */
    const val PREFIX = "folder:"

    /** The most apps a folder takes. The open folder shows them all at once, so it has to fit. */
    const val MAX_APPS = 12

    fun isFolderId(id: String): Boolean = id.startsWith(PREFIX)

    /** A new folder id, different from every one in [taken]. */
    fun newId(taken: Set<String>): String {
        var id: String
        do {
            id = PREFIX + UUID.randomUUID().toString().substring(0, 8)
        } while (id in taken)
        return id
    }

    /**
     * What [GridOrder.cells] should treat as the things that have a cell: the home screen's apps that
     * are not in a folder, and the folders that still hold at least one installed app.
     *
     * A folder whose every app has been uninstalled is left out, so its cell empties like an
     * uninstalled app's does and nothing shifts.
     */
    fun cellIds(
        homeIds: List<String>,
        folders: List<GridFolder>,
        installed: Set<String>
    ): List<String> {
        val inFolders = HashSet<String>()
        for (folder in folders) inFolders.addAll(folder.apps)
        val visible = folders.filter { folder -> folder.apps.any { it in installed } }.map { it.id }
        return homeIds.filter { it !in inFolders } + visible
    }

    /**
     * [appId] taken out of whatever folder holds it. It is NOT given a cell: the caller puts it down
     * somewhere, which is what drag-out is.
     *
     * A folder left with one app stops being a folder - its cell becomes that app. A folder left with
     * none is removed and its cell is emptied.
     */
    fun takeOut(layout: FolderLayout, appId: String): FolderLayout {
        val folder = layout.folders.firstOrNull { appId in it.apps } ?: return layout
        val rest = folder.apps.filterNot { it == appId }
        return when {
            rest.size >= 2 -> layout.copy(
                folders = layout.folders.map { if (it.id == folder.id) it.copy(apps = rest) else it }
            )
            rest.size == 1 -> FolderLayout(
                cells = layout.cells.map { if (it == folder.id) rest[0] else it },
                folders = layout.folders.filterNot { it.id == folder.id }
            )
            else -> FolderLayout(
                cells = trim(layout.cells.map { if (it == folder.id) EMPTY else it }),
                folders = layout.folders.filterNot { it.id == folder.id }
            )
        }
    }

    /**
     * [appId] added to the folder [folderId]. Its own cell, if it had one, is emptied, and it leaves
     * any other folder it was in.
     *
     * Nothing happens if the folder is full, already holds the app, or [appId] is itself a folder:
     * folders do not go inside folders.
     */
    fun putInto(layout: FolderLayout, folderId: String, appId: String): FolderLayout {
        val folder = layout.folders.firstOrNull { it.id == folderId } ?: return layout
        if (isFolderId(appId) || appId in folder.apps || folder.apps.size >= MAX_APPS) return layout
        val freed = takeOut(layout, appId)
        return FolderLayout(
            cells = trim(freed.cells.map { if (it == appId) EMPTY else it }),
            folders = freed.folders.map {
                if (it.id == folderId) it.copy(apps = it.apps + appId) else it
            }
        )
    }

    /**
     * A new folder, [newId], in cell [targetCell] holding the app that was there and [appId].
     *
     * The folder takes the cell the app was dropped on; [appId]'s own cell is emptied. Nothing happens
     * if that cell does not hold a plain app, or it is [appId] itself.
     */
    fun make(
        layout: FolderLayout,
        targetCell: Int,
        appId: String,
        newId: String,
        name: String
    ): FolderLayout {
        if (isFolderId(appId)) return layout
        val freed = takeOut(layout, appId)
        val target = freed.cells.getOrNull(targetCell)
        if (target.isNullOrEmpty() || isFolderId(target) || target == appId) return layout
        val cells = freed.cells.toMutableList()
        for (index in cells.indices) if (cells[index] == appId) cells[index] = EMPTY
        cells[targetCell] = newId
        return FolderLayout(
            cells = trim(cells),
            folders = freed.folders + GridFolder(newId, name, listOf(target, appId))
        )
    }

    /** The folder [folderId] gone, its cell emptied. Its apps are left to the caller. */
    fun remove(layout: FolderLayout, folderId: String): FolderLayout = FolderLayout(
        cells = trim(layout.cells.map { if (it == folderId) EMPTY else it }),
        folders = layout.folders.filterNot { it.id == folderId }
    )

    private fun trim(cells: List<String>): List<String> = cells.dropLastWhile { it == EMPTY }
}

package com.orbit.home.preferences

/**
 * A folder on the grid home: an id, a name, and the apps in it, in the order they are shown.
 *
 * The id is what the folder's cell holds in the grid layout (see [com.orbit.home.ui.GridFolders]), so a
 * folder moves, is picked up and is dropped exactly like an app does - it is just a cell whose id is
 * looked up here instead of in the installed apps.
 */
data class GridFolder(
    val id: String,
    val name: String,
    val apps: List<String>
)

/**
 * How folders are stored: one folder per line, fields separated by tabs - id, name, then the app ids.
 *
 * App ids are `package/activity` and folder ids are generated, so neither can contain a tab or a
 * newline. A name can, because the user typed it, so those are turned into spaces on the way in rather
 * than being allowed to break the record.
 *
 * Reading is forgiving, because this is read on every launch and a bad line must not take the home
 * screen with it: it is skipped. [normalise] is the one place the rules live - every caller, reading or
 * writing, gets the same guarantees.
 */
object GridFolderCodec {

    /** The longest name kept. A longer one is cut, not refused: the user has already typed it. */
    const val MAX_NAME = 24

    fun encode(folders: List<GridFolder>): String =
        normalise(folders).joinToString("\n") { folder ->
            (listOf(folder.id, folder.name) + folder.apps).joinToString("\t")
        }

    fun decode(raw: String?): List<GridFolder> {
        if (raw.isNullOrEmpty()) return emptyList()
        val parsed = raw.split('\n').mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size < 3) return@mapNotNull null
            GridFolder(id = fields[0], name = fields[1], apps = fields.drop(2))
        }
        return normalise(parsed)
    }

    /**
     * The folders as they should be kept:
     * - a folder with no id, or an id seen before, is dropped (the first one wins);
     * - blank app ids are dropped, and an app is in at most one folder - the first one that has it;
     * - a folder left with no apps is dropped, because there is nothing to open;
     * - names have their line breaks and tabs flattened, are trimmed, and are cut to [MAX_NAME].
     */
    fun normalise(folders: List<GridFolder>): List<GridFolder> {
        val ids = HashSet<String>()
        val claimed = HashSet<String>()
        val out = ArrayList<GridFolder>(folders.size)
        for (folder in folders) {
            val id = folder.id.trim()
            if (id.isEmpty() || !ids.add(id)) continue
            val apps = ArrayList<String>()
            for (app in folder.apps) {
                if (app.isBlank() || '\t' in app || '\n' in app) continue
                if (claimed.add(app)) apps.add(app)
            }
            if (apps.isEmpty()) continue
            out.add(GridFolder(id, clean(folder.name), apps))
        }
        return out
    }

    private fun clean(name: String): String =
        name.map { if (it == '\t' || it == '\n' || it == '\r') ' ' else it }
            .joinToString("")
            .trim()
            .take(MAX_NAME)
}

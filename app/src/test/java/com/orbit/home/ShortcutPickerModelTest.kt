package com.orbit.home

import com.orbit.home.shortcuts.ResolvedShortcut
import com.orbit.home.shortcuts.ShortcutItem
import com.orbit.home.shortcuts.ShortcutPickerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picker's search and grouping rules. Pure, so they can be pinned down without a device. */
class ShortcutPickerModelTest {

    private fun item(pkg: String, id: String, label: String, app: String) =
        ResolvedShortcut(ShortcutItem(pkg, id, label), app, null, null)

    private val chat = item("com.a", "new_chat", "New chat", "Chatter")
    private val voice = item("com.a", "voice", "Voice mode", "Chatter")
    private val route = item("com.b", "route", "Directions home", "Maps")
    private val all = listOf(chat, voice, route)

    @Test
    fun `an empty query returns everything`() {
        assertEquals(all, ShortcutPickerModel.filter(all, ""))
        assertEquals(all, ShortcutPickerModel.filter(all, "   "))
    }

    @Test
    fun `search matches the shortcut label`() {
        assertEquals(listOf(voice), ShortcutPickerModel.filter(all, "voice"))
        assertEquals(listOf(chat), ShortcutPickerModel.filter(all, "NEW"))
    }

    @Test
    fun `search matches the app name, so every shortcut of that app shows`() {
        assertEquals(listOf(chat, voice), ShortcutPickerModel.filter(all, "chatter"))
        assertEquals(listOf(route), ShortcutPickerModel.filter(all, "maps"))
    }

    @Test
    fun `no match returns empty, not everything`() {
        assertTrue(ShortcutPickerModel.filter(all, "nothing here").isEmpty())
    }

    @Test
    fun `grouping keeps the catalog order and one group per app`() {
        val groups = ShortcutPickerModel.group(all)
        assertEquals(2, groups.size)
        assertEquals("com.a", groups[0].packageName)
        assertEquals("Chatter", groups[0].appLabel)
        assertEquals(listOf(chat, voice), groups[0].items)
        assertEquals(listOf(route), groups[1].items)
    }

    @Test
    fun `an empty list groups to nothing`() {
        assertTrue(ShortcutPickerModel.group(emptyList()).isEmpty())
    }
}

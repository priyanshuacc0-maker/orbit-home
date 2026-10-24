package com.orbit.home

import com.orbit.home.shortcuts.ResolvedShortcut
import com.orbit.home.shortcuts.ShortcutItem
import com.orbit.home.shortcuts.ShortcutSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide what the shortcut ring shows.
 *
 * Pure, and worth pinning down: these are the rules that could quietly lose a user's selection,
 * and a selection lost to a failed query is a selection the user has to make again.
 */
class ShortcutSelectionTest {

    private fun resolved(packageName: String, id: String, label: String) =
        ResolvedShortcut(ShortcutItem(packageName, id, label), label, null, null)

    private val chat = resolved("com.a", "new_chat", "New chat")
    private val voice = resolved("com.a", "voice", "Voice")
    private val note = resolved("com.b", "note", "New note")
    private val available = listOf(chat, voice, note)

    @Test
    fun `resolve keeps the user's order, not the catalog's`() {
        val saved = listOf(note.key, chat.key, voice.key)
        assertEquals(listOf(note, chat, voice), ShortcutSelection.resolve(saved, available))
    }

    @Test
    fun `a shortcut that is gone is left out, not faked`() {
        // No placeholder: a ring of dead icons is worse than a shorter ring.
        val saved = listOf(chat.key, "com.c#missing")
        assertEquals(listOf(chat), ShortcutSelection.resolve(saved, available))
    }

    @Test
    fun `a missing shortcut is reported, never silently forgotten`() {
        val saved = listOf(chat.key, "com.c#missing")
        assertEquals(listOf("com.c#missing"), ShortcutSelection.missing(saved, available))
    }

    @Test
    fun `an empty catalog makes everything missing, and loses nothing`() {
        // The case the brief calls out: a failed query must not erase the selection. It is
        // reported as missing, and the saved list is untouched.
        val saved = listOf(chat.key, voice.key)
        assertEquals(saved, ShortcutSelection.missing(saved, emptyList()))
        assertTrue(ShortcutSelection.resolve(saved, emptyList()).isEmpty())
    }

    @Test
    fun `adding is refused when it is already there, so no duplicates`() {
        val saved = listOf(chat.key)
        assertEquals(saved, ShortcutSelection.add(saved, chat.key))
        assertEquals(listOf(chat.key, voice.key), ShortcutSelection.add(saved, voice.key))
    }

    @Test
    fun `removing takes out only that one`() {
        val saved = listOf(chat.key, voice.key, note.key)
        assertEquals(listOf(chat.key, note.key), ShortcutSelection.remove(saved, voice.key))
    }

    @Test
    fun `toggle flips one and leaves the rest in order`() {
        val saved = listOf(chat.key, voice.key)
        assertEquals(listOf(chat.key), ShortcutSelection.toggle(saved, voice.key))
        assertEquals(listOf(chat.key, voice.key, note.key), ShortcutSelection.toggle(saved, note.key))
    }

    @Test
    fun `idsByPackage groups by package and keeps saved order`() {
        val saved = listOf("com.a#chat", "com.b#route", "com.a#voice")
        val grouped = ShortcutSelection.idsByPackage(saved)
        assertEquals(listOf("com.a", "com.b"), grouped.keys.toList())
        assertEquals(listOf("chat", "voice"), grouped["com.a"])
        assertEquals(listOf("route"), grouped["com.b"])
    }

    @Test
    fun `idsByPackage collapses a duplicate id`() {
        // The same shortcut saved twice would otherwise be asked for twice.
        val grouped = ShortcutSelection.idsByPackage(listOf("com.a#chat", "com.a#chat"))
        assertEquals(listOf("chat"), grouped["com.a"])
    }

    @Test
    fun `idsByPackage drops malformed keys rather than guessing`() {
        val grouped = ShortcutSelection.idsByPackage(
            listOf("nohash", "pkg#", "#id", "", "com.a#chat")
        )
        assertEquals(1, grouped.size)
        assertEquals(listOf("chat"), grouped["com.a"])
    }

    @Test
    fun `idsByPackage on an empty list is empty`() {
        assertTrue(ShortcutSelection.idsByPackage(emptyList()).isEmpty())
    }
}

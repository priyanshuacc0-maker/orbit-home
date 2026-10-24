package com.orbit.home

import com.orbit.home.shortcuts.ShortcutItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The stored shortcut identifier.
 *
 * This is the only part of the shortcut feature that is pure, and it is the part that decides
 * whether a launch is even attempted — so it is worth pinning down. A malformed key must come
 * back as null rather than as a pair that would be handed to startShortcut.
 */
class ShortcutItemTest {

    @Test
    fun `a well-formed key splits into package and shortcut id`() {
        assertEquals("com.example" to "new_chat", ShortcutItem.split("com.example#new_chat"))
    }

    @Test
    fun `a shortcut id may itself contain a hash`() {
        // Only the first separator counts: the id is everything after it.
        assertEquals("com.example" to "a#b", ShortcutItem.split("com.example#a#b"))
    }

    @Test
    fun `malformed keys are rejected rather than guessed at`() {
        assertNull(ShortcutItem.split(""))
        assertNull(ShortcutItem.split("no-separator"))
        assertNull(ShortcutItem.split("#shortcutId"))
        assertNull(ShortcutItem.split("com.example#"))
    }

    @Test
    fun `fromKey round-trips a key and rejects a malformed one`() {
        val item = ShortcutItem("com.example", "new_chat", "New chat")
        assertEquals(item.key, "com.example#new_chat")

        val back = ShortcutItem.fromKey(item.key, "New chat")
        assertEquals(item, back)
        assertNull(ShortcutItem.fromKey("nonsense"))
    }

    @Test
    fun `the key is what gets stored, and it is stable`() {
        // Package and shortcut id, nothing else — no Drawable, no ShortcutInfo, no index.
        val a = ShortcutItem("com.example", "voice", "Voice")
        val b = ShortcutItem("com.example", "voice", "Voice mode")
        assertEquals("the key ignores the label", a.key, b.key)
    }
}

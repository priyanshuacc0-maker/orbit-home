package com.orbit.home

import com.orbit.home.apps.AppSearch
import com.orbit.home.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for app search (directive Phase 6): case-insensitivity, partial
 * matching and the ranking that decides what surfaces first.
 */
class AppSearchTest {

    private fun app(label: String) = InstalledApp(
        id = "com.test/$label",
        packageName = "com.test",
        activityName = label,
        label = label
    )

    @Test
    fun emptyOrBlankQueryReturnsEverything() {
        val apps = listOf(app("Chrome"), app("Phone"))
        assertEquals(apps, AppSearch.filter(apps, ""))
        assertEquals(apps, AppSearch.filter(apps, "   "))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(listOf("Phone"), AppSearch.filter(listOf(app("Phone")), "PHONE").map { it.label })
        assertEquals(listOf("Phone"), AppSearch.filter(listOf(app("Phone")), "ph").map { it.label })
    }

    @Test
    fun rankingIsExactThenPrefixThenWordThenContains() {
        val apps = listOf(
            app("Chrome"),
            app("Google Chrome"),
            app("Chrome Beta"),
            app("Chromecast")
        )
        assertEquals(
            listOf("Chrome", "Chrome Beta", "Chromecast", "Google Chrome"),
            AppSearch.filter(apps, "chrome").map { it.label }
        )
    }

    @Test
    fun midStringMatchRanksLast() {
        val apps = listOf(app("Xylophone"), app("Phone"))
        assertEquals(listOf("Phone", "Xylophone"), AppSearch.filter(apps, "phone").map { it.label })
    }

    @Test
    fun rankValuesAreStable() {
        assertEquals(0, AppSearch.rank("Chrome", "chrome"))
        assertEquals(1, AppSearch.rank("Chrome Beta", "chrome"))
        assertEquals(2, AppSearch.rank("Google Chrome", "chrome"))
        assertEquals(4, AppSearch.rank("Chromecast", "ome"))
    }

    @Test
    fun separatorsCountAsWordBoundaries() {
        assertEquals(2, AppSearch.rank("Fire-fox", "fox"))
        assertEquals(2, AppSearch.rank("com.android.chrome", "chrome"))
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(AppSearch.filter(listOf(app("Phone")), "zzz").isEmpty())
    }

    @Test
    fun queryIsTrimmedBeforeMatching() {
        assertEquals(listOf("Phone"), AppSearch.filter(listOf(app("Phone")), "  phone  ").map { it.label })
    }

    @Test
    fun tiesAreBrokenAlphabetically() {
        val apps = listOf(app("Beta Chat"), app("Alpha Chat"))
        assertEquals(listOf("Alpha Chat", "Beta Chat"), AppSearch.filter(apps, "chat").map { it.label })
    }

    @Test
    fun initialsMatchRanksAboveContains() {
        // "gm" finds "Google Maps" (P2). Note "Gmail Maps" would rank 1, not 3,
        // because the label itself begins with "gm" — prefix beats initials.
        assertEquals(3, AppSearch.rank("Google Maps", "gm"))
        assertEquals(1, AppSearch.rank("Gmail Maps", "gm"))
        assertEquals(3, AppSearch.rank("Sound Cloud", "sc"))
    }

    @Test
    fun accentsAreIgnored() {
        // "emile" finds "Émile", and the other way round (P2).
        val apps = listOf(app("Émile"), app("Zoe"))
        assertEquals(listOf("Émile"), AppSearch.filter(apps, "emile").map { it.label })
        assertEquals(listOf("Émile"), AppSearch.filter(apps, "ÉMILE").map { it.label })
    }

    @Test
    fun packageNamesAreSearchable() {
        // Searching for the package finds the app (P2).
        val spotify = InstalledApp(
            id = "com.spotify.music/com.spotify.Main",
            packageName = "com.spotify.music",
            activityName = "com.spotify.Main",
            label = "Music"
        )
        assertEquals(listOf("Music"), AppSearch.filter(listOf(spotify), "spotify").map { it.label })
    }
}

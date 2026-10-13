package com.openswift.keyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    @Test
    fun categoriesIncludeRecentsFavoritesAndSearchableGroups() {
        assertEquals(EmojiCatalog.RECENTS, EmojiCatalog.categories.first())
        assertTrue(EmojiCatalog.categories.contains(EmojiCatalog.FAVORITES))
        assertTrue(EmojiCatalog.categories.containsAll(listOf("Smile", "Food", "Nature", "Travel", "Symbol")))
    }

    @Test
    fun searchMatchesKeywordsAcrossCategories() {
        val heart = EmojiCatalog.search("heart").map { it.value }
        val hand = EmojiCatalog.search("pray").map { it.value }

        assertTrue("❤️" in heart)
        assertTrue("🙏" in hand)
    }

    @Test
    fun everyEntryIsAddressableByValue() {
        assertEquals(EmojiCatalog.entries.size, EmojiCatalog.byValue.size)
    }

    @Test
    fun shortcutsContainAllRequestedEmojis() {
        val expected = listOf("🤲", "🙏", "❤️", "🌹", "👍", "🤍", "👆", "👋", "🫡", "☑️", "👇", "⬆️")
        assertTrue(EmojiCatalog.categories.contains(EmojiCatalog.SHORTCUTS))
        assertEquals(expected.size, EmojiCatalog.shortcuts.size)
        assertEquals(expected, EmojiCatalog.shortcuts.map { it.value })
    }
}

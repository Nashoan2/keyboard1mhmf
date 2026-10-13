package com.openswift.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardHistoryTest {

    @Test
    fun captureRequiresOptInAndNonPrivateNonSensitiveText() {
        assertFalse(ClipboardHistory.shouldCapture(false, false, false, "copy"))
        assertFalse(ClipboardHistory.shouldCapture(true, true, false, "copy"))
        assertFalse(ClipboardHistory.shouldCapture(true, false, true, "copy"))
        assertFalse(ClipboardHistory.shouldCapture(true, false, false, "  "))
        assertTrue(ClipboardHistory.shouldCapture(true, false, false, "copy"))
    }

    @Test
    fun emptyAndAlreadyFirstValuesAreUnchanged() {
        val current = listOf("newest", "older")

        assertSame(current, ClipboardHistory.withCapturedItem(current, ""))
        assertSame(current, ClipboardHistory.withCapturedItem(current, "newest"))
    }

    @Test
    fun olderCopiedItemIsPromotedToTop() {
        val current = listOf("newest", "older", "ancient")

        val updated = ClipboardHistory.withCapturedItem(current, "older")
        assertEquals(listOf("older", "newest", "ancient"), updated)
    }

    @Test
    fun newItemsAreNewestFirstAndRetentionIsBounded() {
        val current = (1..ClipboardHistory.MAX_ITEMS).map { "item-$it" }

        val updated = ClipboardHistory.withCapturedItem(current, "latest")

        assertEquals(ClipboardHistory.MAX_ITEMS, updated.size)
        assertEquals("latest", updated.first())
        assertEquals("item-${ClipboardHistory.MAX_ITEMS - 1}", updated.last())
    }

    @Test
    fun initialInstallClipboardIsEmpty() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_clipboard_history", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)
        assertTrue("Clipboard recent items should be empty", history.items().isEmpty())
        assertTrue("Clipboard pinned items should be empty", history.pinnedItems().isEmpty())
    }

    @Test
    fun pinnedItemIsNotDuplicatedIntoUnpinnedItemsWhenCopied() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_clipboard_pinned", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        history.pin("Pinned Greeting")
        assertTrue(history.isPinned("Pinned Greeting"))
        assertTrue("Pinned item should not be in unpinned items", "Pinned Greeting" !in history.items())

        // Simulate user copying the pinned greeting text again
        val added = history.add("Pinned Greeting")
        assertFalse("Adding an already-pinned item returns false and should not add to unpinned", added)
        assertTrue("Still pinned", history.isPinned("Pinned Greeting"))
        assertTrue("Should still not appear in unpinned items", "Pinned Greeting" !in history.items())
        assertEquals(listOf("Pinned Greeting"), history.pinnedItems())
    }

    @Test
    fun multipleCopiedTextsArePreservedInOrder() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_clipboard_multi", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        history.add("First text from web")
        history.add("Second text from WhatsApp")
        history.add("Third text from notes")

        val items = history.items()
        assertEquals(3, items.size)
        assertEquals("Third text from notes", items[0])
        assertEquals("Second text from WhatsApp", items[1])
        assertEquals("First text from web", items[2])
    }

    @Test
    fun newPinsArePrependedToPinnedList() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_clipboard_pin_order", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        history.pin("First Pin")
        history.pin("Second Pin")

        assertEquals(listOf("Second Pin", "First Pin"), history.pinnedItems())
    }

    @Test
    fun captureSystemRefusesWhenAnotherImeIsDefault() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        android.provider.Settings.Secure.putString(
            context.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        )
        val prefs = context.getSharedPreferences("test_clipboard_other_ime", android.content.Context.MODE_PRIVATE)
        val history = ClipboardHistory(prefs)

        val captured = history.captureSystem(context, enabled = true, privateField = false)
        assertFalse("Should not capture when another IME is selected", captured)
    }

    @Test
    fun captureSystemRefusesWhenOlderThanMinTimestamp() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        android.provider.Settings.Secure.putString(
            context.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
            "${context.packageName}/com.openswift.keyboard.OpenSwiftIME"
        )
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("test", "Old copied text from other keyboard"))

        val prefs = context.getSharedPreferences("test_clipboard_old_timestamp", android.content.Context.MODE_PRIVATE)
        val history = ClipboardHistory(prefs)

        // Require timestamp in the future; current clip is older
        val futureTimestamp = System.currentTimeMillis() + 3600000L
        val captured = history.captureSystem(context, enabled = true, privateField = false, minTimestamp = futureTimestamp)
        assertFalse("Should not capture clip with timestamp older than minTimestamp", captured)
        assertEquals(0, history.items().size)
    }

    @Test
    fun captureManyItemsPreservesAllInOrder() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_clipboard_many", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        // Add 25 distinct copied texts
        for (i in 1..25) {
            history.add("Copied snippet #$i")
        }

        val items = history.items()
        assertEquals(25, items.size)
        assertEquals("Copied snippet #25", items[0])
        assertEquals("Copied snippet #1", items[24])
    }
}

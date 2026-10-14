package com.openswift.keyboard.data

import android.content.ClipboardManager as SystemClipboard
import android.content.ClipDescription
import android.content.Context
import org.json.JSONArray

class ClipboardHistory internal constructor(private val prefs: android.content.SharedPreferences) {

    constructor(ctx: Context) : this(SecurePreferences.open(ctx, TypedDataStores.CLIPBOARD_HISTORY))

    @Volatile
    private var cachedItems: List<String>? = null

    @Volatile
    private var cachedPinnedItems: List<String>? = null

    @Volatile
    private var cachedPinnedSet: Set<String>? = null

    // Transient deleted texts cooldown so an item deleted in UI isn't immediately bounced back by system buffer
    private val deletedTimestamps = mutableMapOf<String, Long>()
    private var deletedItemsLoaded = false

    private fun ensureDeletedLoaded() {
        if (deletedItemsLoaded) return
        deletedItemsLoaded = true
        // Clean up legacy deleted items so no copied texts are blacklisted
        prefs.edit().remove("deleted_items").apply()
        deletedTimestamps.clear()
    }

    private fun persistDeletedItems() {
        // In-memory cooldown is sufficient
    }

    fun invalidateCache() {
        synchronized(this) {
            cachedItems = null
            cachedPinnedItems = null
            cachedPinnedSet = null
        }
    }

    fun items(): List<String> {
        cachedItems?.let { return it }
        synchronized(this) {
            cachedItems?.let { return it }
            val raw = prefs.getString("items", null)
            val list = if (raw == null) {
                emptyList()
            } else {
                runCatching {
                    val arr = JSONArray(raw)
                    List(arr.length()) { arr.getString(it) }
                }.getOrDefault(emptyList())
            }
            // Exclude any items that are currently pinned from the unpinned items list
            val pinned = pinnedSet()
            val filtered = if (pinned.isNotEmpty()) list.filter { it !in pinned } else list
            cachedItems = filtered
            return filtered
        }
    }

    fun add(text: String): Boolean {
        ensureDeletedLoaded()
        if (deletedTimestamps.remove(text) != null) {
            persistDeletedItems()
        }

        // If the text is already pinned, keep it pinned and do NOT add to unpinned items!
        if (isPinned(text)) {
            val unpinned = items()
            if (text in unpinned) {
                save(unpinned.filter { it != text })
            }
            val currentPinned = pinnedItems()
            if (currentPinned.firstOrNull() != text) {
                savePinned(listOf(text) + currentPinned.filter { it != text })
            }
            return false
        }

        val current = items()
        val updated = withCapturedItem(current, text)
        if (updated == current) return false
        save(updated)
        return true
    }

    fun onSystemClipChanged() {
        // When the user performs a new copy in the system, clear transient deletion blocks
        ensureDeletedLoaded()
        if (deletedTimestamps.isNotEmpty()) {
            deletedTimestamps.clear()
            persistDeletedItems()
        }
    }

    fun markAsDeleted(text: String) {
        ensureDeletedLoaded()
        deletedTimestamps[text] = System.currentTimeMillis()
        persistDeletedItems()
    }

    fun remove(text: String, ctx: Context? = null) {
        markAsDeleted(text)
        save(items().filter { it != text })
        clearSystemClipIfMatching(ctx, text)
    }

    fun clear(ctx: Context? = null) {
        ensureDeletedLoaded()
        items().forEach { deletedTimestamps[it] = System.currentTimeMillis() }
        persistDeletedItems()
        save(emptyList())
        if (ctx != null) {
            clearSystemClip(ctx)
        }
    }

    private fun clearSystemClipIfMatching(ctx: Context?, text: String) {
        if (ctx == null || isPinned(text)) return
        try {
            val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? SystemClipboard ?: return
            val clip = cb.primaryClip ?: return
            if (clip.itemCount > 0) {
                val clipText = clip.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (clipText == text) {
                    clearSystemClip(ctx)
                }
            }
        } catch (_: Exception) {}
    }

    private fun clearSystemClip(ctx: Context) {
        try {
            val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? SystemClipboard ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                cb.clearPrimaryClip()
            } else {
                cb.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {}
    }

    fun pinnedItems(): List<String> {
        cachedPinnedItems?.let { return it }
        synchronized(this) {
            cachedPinnedItems?.let { return it }
            val raw = prefs.getString("pinned_items", null)
            val list = if (raw == null) {
                emptyList()
            } else {
                runCatching {
                    val arr = JSONArray(raw)
                    List(arr.length()) { arr.getString(it) }
                }.getOrDefault(emptyList())
            }
            cachedPinnedItems = list
            cachedPinnedSet = list.toHashSet()
            return list
        }
    }

    fun pinnedSet(): Set<String> {
        cachedPinnedSet?.let { return it }
        pinnedItems()
        return cachedPinnedSet ?: emptySet()
    }

    fun isPinned(text: String): Boolean = pinnedSet().contains(text)

    fun removePinned(text: String) {
        savePinned(pinnedItems().filter { it != text })
    }

    fun pin(text: String) {
        val current = pinnedItems()
        if (text !in current) {
            savePinned((listOf(text) + current).take(MAX_PINNED_ITEMS))
        }
        val currentUnpinned = items()
        if (text in currentUnpinned) {
            save(currentUnpinned.filter { it != text })
        }
    }

    fun clearPinned() = savePinned(emptyList())

    fun clearAll() {
        clear()
        clearPinned()
    }

    internal fun savePinned(list: List<String>) {
        cachedPinnedItems = list
        cachedPinnedSet = list.toHashSet()
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("pinned_items", arr.toString()).apply()
    }

    internal fun save(list: List<String>) {
        cachedItems = list
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun captureSystem(
        ctx: Context,
        enabled: Boolean,
        privateField: Boolean,
        minTimestamp: Long = 0L,
        ignoredBaselineText: String? = null
    ): Boolean {
        if (!enabled || privateField) return false
        val defaultIme = try {
            android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            ).orEmpty()
        } catch (_: Exception) {
            ""
        }
        val isDefault = defaultIme.isEmpty() ||
            defaultIme.contains(ctx.packageName) ||
            defaultIme.contains("com.openswift.keyboard")
        if (!isDefault) {
            // Another keyboard is active; do NOT capture system clipboard!
            return false
        }
        ensureDeletedLoaded()
        val cb = try {
            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? SystemClipboard
        } catch (_: Exception) {
            null
        } ?: return false

        val clip = try {
            cb.primaryClip
        } catch (_: Exception) {
            null
        } ?: return false

        if (clip.itemCount == 0 || isSensitive(clip.description)) return false

        if (minTimestamp > 0L) {
            val clipTime = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    clip.description.timestamp
                } catch (_: Exception) {
                    0L
                }
            } else {
                0L
            }
            if (clipTime in 1 until minTimestamp) {
                // Text was copied before this keyboard became active; do not capture!
                return false
            }
        }

        val text = try {
            clip.getItemAt(0)?.coerceToText(ctx)?.toString()
        } catch (_: Exception) {
            null
        } ?: return false

        if (text.isBlank()) return false

        if (ignoredBaselineText != null && text == ignoredBaselineText) {
            // Text was already on system clipboard prior to OpenSwift activation; ignore
            return false
        }

        // Check if this text was deleted earlier within a transient cooldown
        val deletedTime = deletedTimestamps[text]
        if (deletedTime != null) {
            val now = System.currentTimeMillis()
            val clipTime = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    clip.description.timestamp
                } catch (_: Exception) {
                    0L
                }
            } else {
                0L
            }
            if ((clipTime > 0L && clipTime > deletedTime) || (now - deletedTime > 2500L)) {
                // User re-copied this text or cooldown expired; accept it!
                deletedTimestamps.remove(text)
            } else {
                // Transient bounce right after deletion; ignore
                return false
            }
        }

        if (!shouldCapture(enabled, privateField, sensitiveClip = false, text)) return false
        return add(text)
    }

    private fun isSensitive(description: ClipDescription): Boolean {
        val extras = description.extras ?: return false
        return extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ||
            extras.getBoolean(SENSITIVE_CLIPBOARD_EXTRA, false)
    }

    companion object {
        const val MAX_ITEMS = 3000
        const val MAX_PINNED_ITEMS = 1000
        private const val SENSITIVE_CLIPBOARD_EXTRA = "android.content.extra.IS_SENSITIVE"

        internal fun shouldCapture(
            enabled: Boolean,
            privateField: Boolean,
            sensitiveClip: Boolean,
            text: String?
        ): Boolean = enabled && !privateField && !sensitiveClip && !text.isNullOrBlank()

        internal fun withCapturedItem(current: List<String>, text: String): List<String> {
            if (text.isBlank() || current.firstOrNull() == text) return current
            val filtered = current.filter { it != text }
            return (listOf(text) + filtered).take(MAX_ITEMS)
        }
    }
}

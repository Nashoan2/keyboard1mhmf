package com.openswift.keyboard.ui

data class EmojiEntry(
    val value: String,
    val category: String,
    val keywords: Set<String>
)

object EmojiCatalog {
    const val RECENTS = "Recent"
    const val SHORTCUTS = "Shortcuts"
    const val FAVORITES = "Star"

    val categories = listOf(
        RECENTS,
        SHORTCUTS,
        FAVORITES,
        "Smile",
        "Hand",
        "Heart",
        "Food",
        "Nature",
        "Travel",
        "Object",
        "Symbol"
    )

    val shortcutValues = listOf(
        "🤲", "🙏", "❤️", "🌹", "👍", "🤍", "👆", "👋", "🫡", "☑️", "👇", "⬆️"
    )

    val entries = listOf(
        e("🤲", "Hand", "dua pray hands palms دعاء تضرع يدين كف"),
        e("🙏", "Hand", "pray thanks please hand رجاء شكر يدين"),
        e("❤️", "Heart", "heart red love حب قلب احمر"),
        e("🌹", "Nature", "rose flower nature وردة زهرة جوري"),
        e("👍", "Hand", "thumb up yes like hand نعم اعجاب لايك"),
        e("🤍", "Heart", "heart white love قلب ابيض"),
        e("👆", "Hand", "finger index up hand اشارة سبابة اعلى فوق"),
        e("👋", "Hand", "wave hello bye hand سلام مرحبا مع السلامة"),
        e("🫡", "Smile", "salute respect face تحية احترام تمام"),
        e("☑️", "Symbol", "check box done tick صح تم مربع علامة"),
        e("👇", "Hand", "finger index down hand اشارة سبابة اسفل تحت"),
        e("⬆️", "Symbol", "arrow up top سهم فوق للاعلى")
    )

    val byValue = entries.associateBy { it.value }

    val shortcuts: List<EmojiEntry> by lazy {
        shortcutValues.mapNotNull { byValue[it] }
    }

    fun search(query: String): List<EmojiEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries
        return entries.filter { entry ->
            entry.keywords.any { it.contains(q) } || entry.category.lowercase().contains(q)
        }
    }

    private fun e(value: String, category: String, keywords: String): EmojiEntry {
        return EmojiEntry(value, category, keywords.split(' ').filter { it.isNotBlank() }.toSet())
    }
}

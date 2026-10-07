package com.wynime.app.ui.subject.details.components

data class EpisodePaging(
    val totalCount: Int,
    val capacity: Int,
) {
    init {
        require(capacity >= 1) { "capacity must be >= 1, but was $capacity" }
    }

    val pageCount: Int = if (totalCount <= 0) 0 else (totalCount + capacity - 1) / capacity

    val isPaged: Boolean get() = totalCount > capacity

    fun pageOf(itemIndex: Int): Int {
        if (pageCount == 0) return 0
        return (itemIndex.coerceAtLeast(0) / capacity).coerceIn(0, pageCount - 1)
    }

    fun initialPage(currentIndex: Int): Int = if (currentIndex < 0) 0 else pageOf(currentIndex)

    fun itemRange(page: Int): IntRange {
        if (pageCount == 0) return IntRange.EMPTY
        val clamped = page.coerceIn(0, pageCount - 1)
        val start = clamped * capacity
        val end = (start + capacity).coerceAtMost(totalCount)
        return start until end
    }
}

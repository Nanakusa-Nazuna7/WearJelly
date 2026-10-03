package dev.wearjelly.playback

import kotlin.random.Random

/** Tracks actual playback transitions and supports navigation through the history cursor. */
class PlayHistoryStack(private val capacity: Int = DEFAULT_CAPACITY) {

    private val entries = mutableListOf<String>()
    private val maxEntries = capacity.coerceAtLeast(1)
    private var cursor = -1

    val size: Int get() = cursor + 1

    /** A new transition after going back starts a new history branch. */
    fun record(trackId: String) {
        if (trackId.isBlank() || entries.getOrNull(cursor) == trackId) return
        if (entries.getOrNull(cursor + 1) == trackId) {
            cursor++
            return
        }
        while (entries.size > cursor + 1) entries.removeAt(entries.lastIndex)
        entries += trackId
        cursor = entries.lastIndex
        while (entries.size > maxEntries) {
            entries.removeAt(0)
            cursor--
        }
    }

    /** Recent actual playback, newest first; future entries after back-navigation are omitted. */
    fun recent(count: Int): List<String> =
        entries.take(cursor + 1).asReversed().take(count.coerceAtLeast(0))

    fun contains(trackId: String): Boolean = entries.take(cursor + 1).contains(trackId)

    /** Move to and return the nearest earlier entry accepted by the caller. */
    fun goToPrevious(isAvailable: (String) -> Boolean): String? {
        for (index in cursor - 1 downTo 0) {
            val trackId = entries[index]
            if (isAvailable(trackId)) {
                cursor = index
                return trackId
            }
        }
        return null
    }

    fun remove(trackId: String) {
        var index = entries.indexOf(trackId)
        while (index >= 0) {
            entries.removeAt(index)
            if (index <= cursor) cursor--
            index = entries.indexOf(trackId)
        }
        cursor = cursor.coerceAtLeast(-1)
    }

    fun clear() {
        entries.clear()
        cursor = -1
    }

    companion object {
        const val DEFAULT_CAPACITY = 50
    }
}

/** Pure shuffle logic over indices; the Media3 timeline remains in original queue order. */
object ShuffleQueue {

    const val RECENTLY_PLAYED_AVOID_COUNT = 5

    /**
     * Generate a shuffle order with the current item first. Recent tracks are deferred
     * until no eligible track remains; the exclusion window shrinks one item at a time
     * when a short queue cannot satisfy all five exclusions.
     */
    fun generateOrder(
        size: Int,
        currentIndex: Int,
        recentTrackIds: List<String>,
        trackIdAt: (Int) -> String,
        random: Random = Random.Default
    ): IntArray {
        if (size <= 0) return IntArray(0)
        val current = currentIndex.coerceIn(0, size - 1)
        val others = (0 until size).filter { it != current }.shuffled(random)
        if (others.isEmpty()) return intArrayOf(current)

        val recent = recentTrackIds.take(RECENTLY_PLAYED_AVOID_COUNT)
        var excludedCount = recent.size
        var fresh = others.filter { trackIdAt(it) !in recent.take(excludedCount) }
        while (fresh.isEmpty() && excludedCount > 0) {
            excludedCount--
            fresh = others.filter { trackIdAt(it) !in recent.take(excludedCount) }
        }
        val deferred = others.filterNot { it in fresh }
        return intArrayOf(current) + fresh.toIntArray() + deferred.toIntArray()
    }
}

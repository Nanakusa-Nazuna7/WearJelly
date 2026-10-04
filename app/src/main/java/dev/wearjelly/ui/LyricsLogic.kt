package dev.wearjelly.ui

/**
 * 歌词显示逻辑（REQ-LYRICS-PAGE-002，纯函数无 Android 依赖，JVM 单测覆盖）。
 *
 * 偏移语义：offsetMs > 0 = 歌词整体延后（判定时刻 = positionMs - offsetMs）。
 */
internal object LyricsLogic {

    /**
     * 当前行下标：时间戳升序数组中最后一个 startMs ≤ effectiveMs 的行。
     * 返回 -1 表示尚未唱到第一行（当前行留白，下一行=第 0 行）。
     * 非空 startMs 视为 -1L（不同步的行不参与判定；全为 null 时返回 -2 表示非同步歌词）。
     */
    fun currentIndex(startMsList: List<Long?>, positionMs: Long, offsetMs: Long = 0L): Int {
        if (startMsList.isEmpty()) return -1
        if (startMsList.all { it == null }) return -2
        val effective = positionMs - offsetMs
        // 反向线性扫描：时间戳数组可能含 null（不同步行），不满足二分的单调前提
        for (i in startMsList.indices.reversed()) {
            val start = startMsList[i]
            if (start != null && start <= effective) return i
        }
        return -1
    }

    /** 三行窗口：返回 (上一行, 当前行, 下一行) 下标，越界为 null。 */
    fun threeLineWindow(currentIndex: Int, lineCount: Int): Triple<Int?, Int?, Int?> {
        if (lineCount <= 0) return Triple(null, null, null)
        val cur = if (currentIndex in 0 until lineCount) currentIndex else null
        val prev = currentIndex - 1
        val next = currentIndex + 1
        return Triple(prev.takeIf { it in 0 until lineCount }, cur, next.takeIf { it in 0 until lineCount })
    }
}

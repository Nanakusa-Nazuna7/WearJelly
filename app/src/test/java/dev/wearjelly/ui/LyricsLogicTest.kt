package dev.wearjelly.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsLogicTest {

    // 行起始时刻：0s, 10s, 20s, 30s（第 2 行 null=不同步行）
    private val starts = listOf(0L, 10_000L, null, 30_000L)

    @Test
    fun `empty list returns -1`() {
        assertEquals(-1, LyricsLogic.currentIndex(emptyList(), 5_000L))
    }

    @Test
    fun `all unsynced lines return -2`() {
        assertEquals(-2, LyricsLogic.currentIndex(listOf(null, null), 5_000L))
    }

    @Test
    fun `before first line returns -1`() {
        assertEquals(-1, LyricsLogic.currentIndex(starts, 0L - 1))
        assertEquals(-1, LyricsLogic.currentIndex(starts, -1L))
    }

    @Test
    fun `exact boundary selects that line`() {
        assertEquals(0, LyricsLogic.currentIndex(starts, 0L))
        assertEquals(1, LyricsLogic.currentIndex(starts, 10_000L))
    }

    @Test
    fun `middle of line selects it and skips null rows`() {
        assertEquals(0, LyricsLogic.currentIndex(starts, 9_999L))
        // 20s 段的行 startMs 为 null：20s-30s 之间仍应落在上一行（index 1）
        assertEquals(1, LyricsLogic.currentIndex(starts, 25_000L))
        assertEquals(3, LyricsLogic.currentIndex(starts, 30_000L))
    }

    @Test
    fun `past last line returns last index`() {
        assertEquals(3, LyricsLogic.currentIndex(starts, 120_000L))
    }

    @Test
    fun `positive offset delays lyric line change`() {
        // +0.5s 延后：10.2s 时仍是第 0 行（无偏移已切第 1 行），10.6s 才切换
        assertEquals(0, LyricsLogic.currentIndex(starts, 10_200L, offsetMs = 500L))
        assertEquals(1, LyricsLogic.currentIndex(starts, 10_600L, offsetMs = 500L))
    }

    @Test
    fun `negative offset advances lyric line change`() {
        assertEquals(1, LyricsLogic.currentIndex(starts, 9_800L, offsetMs = -500L))
    }

    @Test
    fun `three line window handles edges`() {
        assertEquals(Triple(null, null, 0), LyricsLogic.threeLineWindow(-1, 3))
        assertEquals(Triple(null, 0, 1), LyricsLogic.threeLineWindow(0, 3))
        assertEquals(Triple(0, 1, 2), LyricsLogic.threeLineWindow(1, 3))
        assertEquals(Triple(1, 2, null), LyricsLogic.threeLineWindow(2, 3))
        assertEquals(Triple(null, null, null), LyricsLogic.threeLineWindow(0, 0))
    }
}

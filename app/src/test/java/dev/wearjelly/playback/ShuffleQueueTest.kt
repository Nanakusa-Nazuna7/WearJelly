package dev.wearjelly.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleQueueTest {

    private fun orderOf(order: IntArray, ids: List<String>): List<String> =
        order.map { ids[it] }

    @Test
    fun `empty queue yields empty order`() {
        assertTrue(ShuffleQueue.generateOrder(0, 0, emptyList(), { "" }).isEmpty())
    }

    @Test
    fun `current index stays first`() {
        val ids = (1..20).map { "track$it" }
        val order = ShuffleQueue.generateOrder(20, 7, emptyList(), { i -> ids[i] })
        assertEquals(7, order[0])
        assertEquals(20, order.size)
        assertEquals((0 until 20).toSet(), order.toSet())
    }

    @Test
    fun `current index out of bounds is clamped`() {
        val ids = listOf("a", "b", "c")
        val order = ShuffleQueue.generateOrder(3, 99, emptyList(), { i -> ids[i] })
        assertEquals(2, order[0])
    }

    @Test
    fun `recently played tracks are pushed to end of round`() {
        val ids = (1..20).map { "track$it" }
        val recent = listOf("track3", "track5", "track8", "track11", "track14")
        // 固定随机源使断言稳定；fresh/stale 分组与随机无关
        val order = ShuffleQueue.generateOrder(
            20, 0, recent, { i -> ids[i] }, Random(42)
        ).drop(1) // 去掉当前曲目

        val lastFive = order.takeLast(5).map { ids[it] }.toSet()
        assertEquals(recent.toSet(), lastFive)
    }

    @Test
    fun `single track queue keeps that track`() {
        val order = ShuffleQueue.generateOrder(1, 0, emptyList(), { "only" })
        assertEquals(listOf(0), order.toList())
    }

    @Test
    fun `small queue fallback - all remaining tracks recent still returns full order`() {
        val ids = listOf("a", "b", "c")
        val order = ShuffleQueue.generateOrder(
            3, 0, listOf("b", "c"), { i -> ids[i] }, Random(1)
        )
        // 当前曲目 a 固定在首位，其余全部是最近播放，也必须完整出现
        assertEquals(setOf(0, 1, 2), order.toSet())
        assertEquals(0, order[0])
    }

    @Test
    fun `small queue relaxes the recent exclusion window one item at a time`() {
        // WV-007：recent 混入 3 个队列外 id（q1-q3）——初始 excludedCount=5 时 fresh 必为空，
        // 放宽循环须连续 4 步降到 1 才能得到 fresh，这才真正覆盖"逐条放宽"路径
        val ids = listOf("a", "b", "c")
        val order = ShuffleQueue.generateOrder(
            ids.size, 0, listOf("b", "c", "q1", "q2", "q3"), { i -> ids[i] }, Random(7)
        )
        // 降到 excludedCount=1 时 excluded={b}：fresh=[c]（紧随当前曲目），deferred=[b]（末尾）
        assertEquals(listOf(0, 2, 1), order.toList())
    }

    @Test
    fun `order is a permutation of all indices`() {
        val ids = (1..50).map { "t$it" }
        val order = ShuffleQueue.generateOrder(50, 25, emptyList(), { i -> ids[i] })
        assertEquals((0 until 50).sorted(), order.sorted())
    }

    @Test
    fun `recent beyond five is not avoided`() {
        val ids = (1..10).map { "t$it" }
        val recent = (1..8).map { "t$it" } // 只有 5 首被避开
        val order = ShuffleQueue.generateOrder(
            10, 9, recent, { i -> ids[i] }, Random(3)
        ).drop(1)
        val lastFive = order.takeLast(5).map { ids[it] }.toSet()
        assertEquals(recent.take(5).toSet(), lastFive)
    }

    // --- WV-008：外部控制器（蓝牙/通知栏）「上一首」→ 内部历史回退的路由决策 ---

    @Test
    fun `external previous commands route to history only when shuffle is enabled`() {
        assertTrue(shouldRouteExternalPreviousToHistory(Player.COMMAND_SEEK_TO_PREVIOUS, true))
        assertTrue(shouldRouteExternalPreviousToHistory(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, true))
        assertTrue(!shouldRouteExternalPreviousToHistory(Player.COMMAND_SEEK_TO_PREVIOUS, false))
        assertTrue(!shouldRouteExternalPreviousToHistory(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, false))
        assertTrue(!shouldRouteExternalPreviousToHistory(Player.COMMAND_PLAY_PAUSE, true))
    }
}

class PlayHistoryStackTest {

    @Test
    fun `previous walks actual history without bouncing and new playback branches`() {
        val h = PlayHistoryStack()
        h.record("a")
        h.record("b")
        h.record("c")

        assertEquals("b", h.goToPrevious { true })
        assertEquals(listOf("b", "a"), h.recent(5))
        assertEquals("a", h.goToPrevious { true })
        assertNull(h.goToPrevious { true })

        h.record("d")
        assertEquals(listOf("d", "a"), h.recent(5))
    }

    @Test
    fun `previous skips unavailable history items`() {
        val h = PlayHistoryStack()
        listOf("a", "b", "c").forEach(h::record)

        assertEquals("a", h.goToPrevious { it == "a" })
    }

    @Test
    fun `capacity and remove are enforced`() {
        val h = PlayHistoryStack(capacity = 3)
        listOf("a", "b", "c", "d").forEach(h::record)
        assertEquals(listOf("d", "c", "b"), h.recent(10))
        h.remove("c")
        assertEquals(listOf("d", "b"), h.recent(10))
        h.clear()
        assertEquals(0, h.size)
        assertNull(h.goToPrevious { true })
    }

    @Test
    fun `blank ids are ignored`() {
        val h = PlayHistoryStack()
        h.record("")
        h.record("  ")
        assertEquals(0, h.size)
    }
}

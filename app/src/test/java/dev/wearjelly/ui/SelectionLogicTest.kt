package dev.wearjelly.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionLogicTest {

    private val ordered = listOf("a", "b", "c", "d", "e")

    @Test
    fun `enterFromSwipe selects the swiped item and anchors it`() {
        val s = SelectionLogic.enterFromSwipe("a")
        assertTrue(s.active)
        assertEquals(setOf("a"), s.selectedIds)
        assertEquals("a", s.anchorId)
    }

    @Test
    fun `toggle adds item and moves anchor to it`() {
        val s = SelectionLogic.toggled(SelectionLogic.enterFromSwipe("a"), "c")
        assertEquals(setOf("a", "c"), s.selectedIds)
        assertEquals("c", s.anchorId)
    }

    @Test
    fun `toggle removes item without moving anchor`() {
        var s = SelectionLogic.enterFromSwipe("a")
        s = SelectionLogic.toggled(s, "c")
        s = SelectionLogic.toggled(s, "c")
        assertEquals(setOf("a"), s.selectedIds)
        // 取消选中不移动锚点：锚点仍是最近一次被单击的 C（即使它已被取消）
        assertEquals("c", s.anchorId)
        assertTrue(s.active)
    }

    @Test
    fun `toggle to zero exits selection mode`() {
        var s = SelectionLogic.enterFromSwipe("a")
        s = SelectionLogic.toggled(s, "a")
        assertFalse(s.active)
        assertTrue(s.selectedIds.isEmpty())
        assertNull(s.anchorId)
    }

    @Test
    fun `rangeSelect fills between target and anchor and anchors at target`() {
        // 已选 A，左滑 C：A..C 全选，锚点变 C（验收用例 3）
        var s = SelectionLogic.enterFromSwipe("a")
        s = SelectionLogic.rangeSelected(s, "c", ordered)
        assertEquals(setOf("a", "b", "c"), s.selectedIds)
        assertEquals("c", s.anchorId)
    }

    @Test
    fun `rangeSelect uses current anchor not the set membership`() {
        // 已选 A、左滑 C（锚点 C）、单击 E（锚点 E）后左滑 B：B..E 连续段全选，锚点变 B（验收用例 4 变体）
        var s = SelectionLogic.enterFromSwipe("a")
        s = SelectionLogic.rangeSelected(s, "c", ordered)
        s = SelectionLogic.toggled(s, "e")
        s = SelectionLogic.rangeSelected(s, "b", ordered)
        assertEquals(setOf("a", "b", "c", "d", "e"), s.selectedIds)
        assertEquals("b", s.anchorId)
    }

    @Test
    fun `rangeSelect falls back to single select when anchor is gone`() {
        var s = SelectionSnapshot(active = true, selectedIds = setOf("x"), anchorId = "x")
        s = SelectionLogic.rangeSelected(s, "c", ordered)
        assertEquals(setOf("c"), s.selectedIds)
        assertEquals("c", s.anchorId)
    }

    @Test
    fun `rangeSelect on unknown target is a no-op`() {
        val s = SelectionLogic.enterFromSwipe("a")
        assertEquals(s, SelectionLogic.rangeSelected(s, "zzz", ordered))
    }

    @Test
    fun `inverted flips within list and exits when result is empty`() {
        val s = SelectionSnapshot(active = true, selectedIds = setOf("a", "b", "d"), anchorId = "d")
        val inv = SelectionLogic.inverted(s, ordered)
        assertEquals(setOf("c", "e"), inv.selectedIds)
        assertTrue(inv.active)

        val full = SelectionSnapshot(active = true, selectedIds = ordered.toSet(), anchorId = "a")
        val zero = SelectionLogic.inverted(full, ordered)
        assertFalse(zero.active)
    }

    @Test
    fun `allSelected keeps active with everything selected`() {
        val s = SelectionLogic.allSelected(SelectionLogic.enterFromSwipe("a"), ordered)
        assertEquals(ordered.toSet(), s.selectedIds)
        assertTrue(s.active)
    }

    @Test
    fun `cleared resets everything`() {
        val s = SelectionLogic.cleared()
        assertFalse(s.active)
        assertTrue(s.selectedIds.isEmpty())
        assertNull(s.anchorId)
    }

    // ---- LastPlaybackStore.capQueueAroundIndex（REQ-PLAYBACK-101）----

    private fun items(n: Int) = (1..n).map { dev.wearjelly.data.JellyfinItem(id = "$it", name = "t$it") }

    @Test
    fun `capQueue keeps queue under limit unchanged`() {
        val (q, idx) = dev.wearjelly.data.LastPlaybackStore.capQueueAroundIndex(items(100), 50)
        assertEquals(100, q.size)
        assertEquals(50, idx)
    }

    @Test
    fun `capQueue truncates to window around current index`() {
        val (q, idx) = dev.wearjelly.data.LastPlaybackStore.capQueueAroundIndex(items(500), 250)
        assertEquals(200, q.size)
        assertEquals(100, idx)
        assertEquals("151", q.first().id)
        assertEquals("350", q.last().id)
    }

    @Test
    fun `capQueue clamps out of range index`() {
        val (q, idx) = dev.wearjelly.data.LastPlaybackStore.capQueueAroundIndex(items(500), 9999)
        assertEquals(101, q.size)
        assertEquals(100, idx)
        assertEquals("400", q.first().id)
        assertEquals("500", q.last().id)
    }
}

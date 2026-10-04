package dev.wearjelly.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekLogicTest {

    @Test
    fun `targetMs maps dx proportionally to bar width`() {
        // 条宽 300px、总长 300s：1px = 1s
        assertEquals(35_000L, SeekLogic.targetMs(30_000L, dxPx = 5f, barWidthPx = 300f, totalMs = 300_000L))
        assertEquals(25_000L, SeekLogic.targetMs(30_000L, dxPx = -5f, barWidthPx = 300f, totalMs = 300_000L))
    }

    @Test
    fun `targetMs clamps to valid range`() {
        assertEquals(0L, SeekLogic.targetMs(1_000L, dxPx = -500f, barWidthPx = 300f, totalMs = 300_000L))
        assertEquals(300_000L, SeekLogic.targetMs(299_000L, dxPx = 500f, barWidthPx = 300f, totalMs = 300_000L))
    }

    @Test
    fun `targetMs is safe with degenerate inputs`() {
        assertEquals(5_000L, SeekLogic.targetMs(5_000L, dxPx = 100f, barWidthPx = 0f, totalMs = 300_000L))
        assertEquals(0L, SeekLogic.targetMs(5_000L, dxPx = 100f, barWidthPx = 300f, totalMs = 0L))
    }

    @Test
    fun `isIntentionalDrag uses the minimum drag distance`() {
        assertTrue(SeekLogic.isIntentionalDrag(24f, 24f))
        assertFalse(SeekLogic.isIntentionalDrag(23.9f, 24f))
        assertFalse(SeekLogic.isIntentionalDrag(-10f, 24f))
        assertTrue(SeekLogic.isIntentionalDrag(-30f, 24f))
    }
}

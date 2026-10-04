package dev.wearjelly.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinSortTest {

    // --- pinyinKey ---

    @Test
    fun `pinyinKey keeps English as-is`() {
        assertEquals("abc", PinyinSort.pinyinKey("abc"))
        assertEquals("Hello World", PinyinSort.pinyinKey("Hello World"))
        assertEquals("123", PinyinSort.pinyinKey("123"))
    }

    @Test
    fun `pinyinKey converts Chinese to lowercase pinyin without tones`() {
        assertEquals("zhongwen", PinyinSort.pinyinKey("中文"))
        assertEquals("liudehua", PinyinSort.pinyinKey("刘德华"))
    }

    @Test
    fun `pinyinKey handles mixed content`() {
        assertEquals("zhoujielunJay", PinyinSort.pinyinKey("周杰伦Jay"))
    }

    @Test
    fun `pinyinKey empty string`() {
        assertEquals("", PinyinSort.pinyinKey(""))
    }

    // --- letterBucket ---

    @Test
    fun `letterBucket uppercase English maps to its letter`() {
        assertEquals("A", PinyinSort.letterBucket("Abc"))
        assertEquals("Z", PinyinSort.letterBucket("Zebra"))
    }

    @Test
    fun `letterBucket lowercase English maps to uppercase letter`() {
        assertEquals("A", PinyinSort.letterBucket("abc"))
        assertEquals("B", PinyinSort.letterBucket("beatles"))
    }

    @Test
    fun `letterBucket Chinese maps to pinyin initial`() {
        assertEquals("L", PinyinSort.letterBucket("刘德华"))
        assertEquals("Z", PinyinSort.letterBucket("周杰伦"))
        assertEquals("Z", PinyinSort.letterBucket("中文"))
    }

    @Test
    fun `letterBucket digits map to hash`() {
        assertEquals("#", PinyinSort.letterBucket("123"))
        assertEquals("#", PinyinSort.letterBucket("9 Songs"))
    }

    @Test
    fun `letterBucket punctuation and other symbols map to hash`() {
        assertEquals("#", PinyinSort.letterBucket("!"))
        assertEquals("#", PinyinSort.letterBucket("..."))
        assertEquals("#", PinyinSort.letterBucket("☆ Special"))
    }

    @Test
    fun `letterBucket empty and blank map to hash`() {
        assertEquals("#", PinyinSort.letterBucket(""))
        assertEquals("#", PinyinSort.letterBucket("   "))
    }

    @Test
    fun `letterBucket leading whitespace is trimmed`() {
        assertEquals("A", PinyinSort.letterBucket("  Abc"))
        assertEquals("L", PinyinSort.letterBucket("\t刘德华"))
    }

    @Test
    fun `letterBucket covers full letter range`() {
        for (c in 'A'..'Z') {
            // Use the letter itself; '#' for O/X etc is impossible here — direct mapping
            assertEquals(c.toString(), PinyinSort.letterBucket(c.toString()))
            assertEquals(c.toString(), PinyinSort.letterBucket(c.lowercaseChar().toString()))
        }
    }

    // --- LETTERS index / ordering ---

    @Test
    fun `letters has 27 entries with hash last`() {
        assertEquals(27, PinyinSort.LETTERS.length)
        assertEquals('#', PinyinSort.LETTERS.last())
    }

    // --- compareItems / sort ---

    @Test
    fun `compareItems is case-insensitive within same bucket`() {
        val a = item("apple")
        val b = item("Banana")
        assertTrue(PinyinSort.compareItems(a, b) < 0)
        assertTrue(PinyinSort.compareItems(b, a) > 0)
        assertEquals(0, PinyinSort.compareItems(item("abc"), item("ABC")))
    }

    @Test
    fun `sort groups English and Chinese by bucket`() {
        val sorted = PinyinSort.sort(
            listOf(
                item("中文"),
                item("banana"),
                item("123track"),
                item("Apple"),
                item("!bang"),
                item("beatles"),
            )
        )
        assertEquals(
            listOf("Apple", "banana", "beatles", "中文", "!bang", "123track"),
            sorted.map { it.name }
        )
    }

    @Test
    fun `sort falls back to pinyin key ignoring case within bucket`() {
        val sorted = PinyinSort.sort(
            listOf(item("liudehua"), item("刘德华"), item("Liude Hua"))
        )
        // all bucket L; pinyinKey identical prefix, ties allowed in any stable order
        assertTrue(sorted.all { PinyinSort.letterBucket(it.name) == "L" })
        val keys = sorted.map { PinyinSort.pinyinKey(it.name).lowercase() }
        assertEquals(keys, keys.sorted())
    }

    private fun item(name: String) = JellyfinItem(id = name, name = name)

    // --- WV-003：多选批量操作页「已下载」数据源必须与下载页展示序一致 ---

    @Test
    fun `downloadedBatchItems orders downloads the same way as the downloads screen`() {
        val input = listOf(item("banana"), item("中文"), item("Apple"), item("123track"))
        val screenOrder = PinyinSort.sort(input) // 下载页展示序（WearJellyApp DownloadsScreen）
        assertEquals(
            screenOrder.map { it.id },
            PinyinSort.downloadedBatchItems(input).map { it.id }
        )
    }

    @Test
    fun `downloadedBatchItems keeps every item`() {
        val input = listOf(item("banana"), item("Apple"), item("中文"))
        assertEquals(3, PinyinSort.downloadedBatchItems(input).size)
    }

    // --- WV-002：列表右缘内边距必须覆盖索引条宽度（防止重叠带内双触发） ---

    @Test
    fun `list end padding must cover the index strip width`() {
        assertTrue(
            "LIST_END_PADDING_DP=" + dev.wearjelly.ui.LIST_END_PADDING_DP +
                "dp must be >= INDEX_STRIP_WIDTH_DP=" + dev.wearjelly.ui.INDEX_STRIP_WIDTH_DP + "dp (WV-002)",
            dev.wearjelly.ui.LIST_END_PADDING_DP >= dev.wearjelly.ui.INDEX_STRIP_WIDTH_DP
        )
    }

    // --- WV-005：列表锚点恢复解析（原内联于 rememberSongListAnchor，无覆盖） ---

    @Test
    fun `anchor resolves by id when the item still exists`() {
        val items = listOf(item("a"), item("b"), item("c"))
        assertEquals(2, dev.wearjelly.ui.resolveSongListAnchorIndex("c", 0, 0, items))
    }

    @Test
    fun `anchor falls back to saved index when the id vanished`() {
        val items = listOf(item("a"), item("b"), item("c"))
        assertEquals(1, dev.wearjelly.ui.resolveSongListAnchorIndex("gone", 1, 0, items))
    }

    @Test
    fun `anchor without any saved id uses initial index and stays in range`() {
        val items = listOf(item("a"), item("b"))
        assertEquals(1, dev.wearjelly.ui.resolveSongListAnchorIndex(null, 0, 9, items))
        assertEquals(0, dev.wearjelly.ui.resolveSongListAnchorIndex(null, 0, -3, items))
    }

    @Test
    fun `anchor index out of range is clamped`() {
        val items = listOf(item("a"), item("b"), item("c"))
        assertEquals(2, dev.wearjelly.ui.resolveSongListAnchorIndex("gone", 99, 0, items))
        assertEquals(0, dev.wearjelly.ui.resolveSongListAnchorIndex("gone", -5, 0, items))
    }
}

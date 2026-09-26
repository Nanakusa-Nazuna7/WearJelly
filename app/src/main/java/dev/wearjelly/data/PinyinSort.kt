package dev.wearjelly.data

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

object PinyinSort {
    const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    private fun pinyinOf(c: Char): String? = try {
        PinyinHelper.toHanyuPinyinStringArray(c, format)?.firstOrNull()
    } catch (_: Exception) {
        null
    }

    /** 名称转排序键：汉字逐字转拼音，其余原样 */
    fun pinyinKey(name: String): String {
        val sb = StringBuilder(name.length)
        for (c in name) {
            val py = pinyinOf(c)
            if (py != null) sb.append(py) else sb.append(c)
        }
        return sb.toString()
    }

    /** 取条目首字（中文转拼音）的字母桶：A-Z，其余归 # */
    fun letterBucket(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "#"
        val c = trimmed[0]
        if (c in 'A'..'Z') return c.toString()
        if (c in 'a'..'z') return c.uppercaseChar().toString()
        if (c in '0'..'9') return "#"
        pinyinOf(c)?.let { py ->
            val first = py.firstOrNull()?.uppercaseChar()
            if (first != null && first in 'A'..'Z') return first.toString()
        }
        return "#"
    }

    fun compareItems(a: JellyfinItem, b: JellyfinItem): Int {
        val la = letterBucket(a.name)
        val lb = letterBucket(b.name)
        if (la != lb) return LETTERS.indexOf(la) - LETTERS.indexOf(lb)
        return pinyinKey(a.name).compareTo(pinyinKey(b.name), ignoreCase = true)
    }

    fun sort(items: List<JellyfinItem>): List<JellyfinItem> =
        items.sortedWith { a, b -> compareItems(a, b) }
}

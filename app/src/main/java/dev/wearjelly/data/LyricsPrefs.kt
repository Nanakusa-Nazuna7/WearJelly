package dev.wearjelly.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 歌词字号三档（REQ-LYRICS-MIN-003 保留的极简设置）。
 *  scale 作用于 v1 歌词排版（body1/caption1）；MEDIUM ×1.0 = 与第一版逐像素一致。 */
enum class LyricsFontSize(val scale: Float, val displayName: String) {
    SMALL(0.85f, "小"),
    MEDIUM(1.0f, "中"),
    LARGE(1.25f, "大"),
}

/**
 * 歌词偏好：每首歌的偏移（±0.5s 步进，按 itemId 记忆，重启生效）+ 全局字号。
 * 翻译开关、多源、编辑等均按需求砍掉。
 */
class LyricsPrefs(private val context: Context, private val json: Json) {

    private val _offsets = MutableStateFlow<Map<String, Long>>(emptyMap())
    val offsets: StateFlow<Map<String, Long>> = _offsets.asStateFlow()

    private val _fontSize = MutableStateFlow(LyricsFontSize.MEDIUM)
    val fontSize: StateFlow<LyricsFontSize> = _fontSize.asStateFlow()

    init {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _offsets.value = try {
            sp.getString(KEY_OFFSETS, null)?.let { json.decodeFromString<Map<String, Long>>(it) } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        _fontSize.value = runCatching { LyricsFontSize.valueOf(sp.getString(KEY_FONT, null) ?: "") }
            .getOrDefault(LyricsFontSize.MEDIUM)
    }

    fun offsetFor(itemId: String?): Long =
        itemId?.let { _offsets.value[it] } ?: 0L

    /** 步进调整某首歌的偏移；结果夹在 ±5s 内防误操作。 */
    fun adjustOffset(itemId: String, deltaMs: Long) {
        val next = ((offsetFor(itemId) + deltaMs).coerceIn(-5_000L, 5_000L))
        val map = _offsets.value.toMutableMap()
        map[itemId] = next
        persist(map)
        _offsets.value = map
    }

    fun resetOffset(itemId: String) {
        val map = _offsets.value.toMutableMap()
        map.remove(itemId)
        persist(map)
        _offsets.value = map
    }

    fun setFontSize(size: LyricsFontSize) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FONT, size.name).apply()
        _fontSize.value = size
    }

    private fun persist(map: Map<String, Long>) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_OFFSETS, json.encodeToString(map)).apply()
        } catch (_: Exception) {
            // 持久化失败时保留内存值
        }
    }

    private companion object {
        const val PREFS = "wearjelly_lyrics"
        const val KEY_OFFSETS = "lyrics_offsets"
        const val KEY_FONT = "lyrics_font_size"
    }
}

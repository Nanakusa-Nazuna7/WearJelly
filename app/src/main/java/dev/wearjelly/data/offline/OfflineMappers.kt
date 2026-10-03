package dev.wearjelly.data.offline

import androidx.room.TypeConverter
import dev.wearjelly.data.JellyfinItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object OfflineJson {
    private val codec = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    fun encodeItem(item: JellyfinItem): String = codec.encodeToString(item)
    fun decodeItem(value: String): JellyfinItem = codec.decodeFromString(value)
}

class OfflineConverters {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? = value?.let(json::encodeToString)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? = value?.let { json.decodeFromString(it) }

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>?): String? = value?.let(json::encodeToString)

    @TypeConverter
    fun jsonToStringMap(value: String?): Map<String, String>? = value?.let { json.decodeFromString(it) }
}
<<<<<<< HEAD

/**
 * 本地快照的作用域键：同一台服务器下的不同账号各自拥有独立曲库快照。
 * 归一化 URL 去掉尾部斜杠与大小写差异，保证同一服务器只产生一个 key。
 */
fun offlineServerKey(serverUrl: String, userId: String): String =
    "${dev.wearjelly.data.normalizeServerUrl(serverUrl)}|$userId"
=======
>>>>>>> aceddd7 (feat: add offline library database)

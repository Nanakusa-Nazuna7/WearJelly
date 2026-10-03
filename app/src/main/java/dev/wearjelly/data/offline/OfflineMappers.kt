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
    @TypeConverter fun stringListToJson(value: List<String>?): String? = value?.let(json::encodeToString)
    @TypeConverter fun jsonToStringList(value: String?): List<String>? = value?.let { json.decodeFromString(it) }
    @TypeConverter fun stringMapToJson(value: Map<String, String>?): String? = value?.let(json::encodeToString)
    @TypeConverter fun jsonToStringMap(value: String?): Map<String, String>? = value?.let { json.decodeFromString(it) }
}

fun offlineServerKey(serverUrl: String, userId: String): String =
    "${dev.wearjelly.data.normalizeServerUrl(serverUrl)}|$userId"

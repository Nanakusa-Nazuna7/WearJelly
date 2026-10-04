package dev.wearjelly.data.offline

import androidx.room.TypeConverter
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.SongLyrics
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object OfflineJson {
    private val codec = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    fun encodeItem(item: JellyfinItem): String = codec.encodeToString(item)
    fun decodeItem(value: String): JellyfinItem = codec.decodeFromString(value)
    fun encodeStringList(value: List<String>): String = codec.encodeToString(value)
    fun encodeSources(value: List<dev.wearjelly.data.MediaSourceInfo>): String = codec.encodeToString(value)
    fun encodeLyrics(value: SongLyrics): String = codec.encodeToString(value)
    fun decodeLyrics(value: String): SongLyrics = codec.decodeFromString(value)
}

fun JellyfinItem.toTrackEntity(serverKey: String): TrackEntity = TrackEntity(
    serverKey = serverKey, itemId = id, name = name, type = type, albumId = albumId,
    album = album, artistText = artistText, durationTicks = runTimeTicks, container = container,
    metadataJson = OfflineJson.encodeItem(this), composerText = composers.joinToString(", "),
    lyricistText = lyricists.joinToString(", "), genreJson = OfflineJson.encodeStringList(genres),
    overview = overview, productionYear = productionYear, premiereDate = premiereDate,
    sortName = sortName, bitrate = bitrate, mediaSourceJson = OfflineJson.encodeSources(mediaSources)
)

fun TrackEntity.toJellyfinItem(): JellyfinItem = OfflineJson.decodeItem(metadataJson)
class OfflineConverters {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    @TypeConverter fun stringListToJson(value: List<String>?): String? = value?.let(json::encodeToString)
    @TypeConverter fun jsonToStringList(value: String?): List<String>? = value?.let { json.decodeFromString(it) }
    @TypeConverter fun stringMapToJson(value: Map<String, String>?): String? = value?.let(json::encodeToString)
    @TypeConverter fun jsonToStringMap(value: String?): Map<String, String>? = value?.let { json.decodeFromString(it) }
}

fun offlineServerKey(serverUrl: String, userId: String): String =
    "${dev.wearjelly.data.normalizeServerUrl(serverUrl)}|$userId"

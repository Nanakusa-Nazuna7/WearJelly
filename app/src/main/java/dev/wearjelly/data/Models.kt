package dev.wearjelly.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerSession(
    val serverUrl: String,
    val userId: String,
    val userName: String,
    val accessToken: String
)

@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Type") val type: String? = null,
    @SerialName("Artists") val artists: List<String> = emptyList(),
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null
) {
    val artistText: String
        get() = when {
            artists.isNotEmpty() -> artists.joinToString(", ")
            !albumArtist.isNullOrBlank() -> albumArtist
            else -> "未知艺术家"
        }

    val durationMs: Long
        get() = (runTimeTicks ?: 0L) / 10000L
}

@Serializable
data class ItemPage(
    val items: List<JellyfinItem>,
    val totalRecordCount: Int
)

@Serializable
data class LyricsLine(
    val text: String,
    val startTicks: Long? = null
) {
    val startMs: Long?
        get() = startTicks?.let { it / 10000L }
}

@Serializable
data class SongLyrics(
    val lines: List<LyricsLine>,
    val isSynchronized: Boolean
)

enum class LibraryKind {
    ARTISTS,
    ALBUMS,
    SONGS,
    DOWNLOADS
}

enum class AudioBitrate(val kbps: Int, val displayName: String) {
    ORIGINAL(0, "无损/原音频"),
    LOW(96, "96 kbps (省流)"),
    MEDIUM(160, "160 kbps (流畅)"),
    HIGH(320, "320 kbps (高品质)")
}

@Serializable
data class DownloadedSong(
    val item: JellyfinItem,
    val localFilePath: String,
    val localCoverPath: String? = null,
    val downloadedTimeMs: Long,
    val qualityLabel: String = "未知音质"
)

@Serializable
data class HistoryEntry(
    val item: JellyfinItem,
    val playedAtMs: Long
)

data class DownloadProgress(
    val itemId: String,
    val itemName: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val estimated: Boolean = false,
    val qualityLabel: String = ""
) {
    val fraction: Float?
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
}

enum class PlaybackEvent {
    START,
    PROGRESS,
    STOP
}

// 内部交互 DTO
@Serializable
internal data class JellyfinAuthenticateByNameRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String
)

@Serializable
internal data class JellyfinAuthenticationResult(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("User") val user: JellyfinUserDto? = null
)

@Serializable
internal data class JellyfinUserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String
)

@Serializable
internal data class JellyfinItemsResult(
    @SerialName("Items") val items: List<JellyfinItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)

@Serializable
internal data class JellyfinPlaybackProgressRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("PlayMethod") val playMethod: String = "DirectStream",
    @SerialName("RepeatMode") val repeatMode: String = "RepeatNone"
)

@Serializable
internal data class JellyfinPlaybackStopRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long
)

@Serializable
internal data class JellyfinLyricsDto(
    @SerialName("Lyrics") val lyrics: List<JellyfinLyricLineDto> = emptyList()
)

@Serializable
internal data class JellyfinLyricLineDto(
    @SerialName("Text") val text: String = "",
    @SerialName("Start") val start: Long? = null
)

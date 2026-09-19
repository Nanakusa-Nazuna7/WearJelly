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
    SONGS
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

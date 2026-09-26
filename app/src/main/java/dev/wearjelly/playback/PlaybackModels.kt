package dev.wearjelly.playback

import dev.wearjelly.data.JellyfinItem
import kotlinx.serialization.Serializable

@Serializable
data class QueueTrack(
    val item: JellyfinItem,
    val artworkUrl: String? = null
)

data class PlaybackState(
    val connected: Boolean = false,
    val queue: List<QueueTrack> = emptyList(),
    val currentIndex: Int = -1,
    val current: JellyfinItem? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val bufferedPercentage: Int = 0,
    val error: String? = null,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val volumePercent: Int = 0
)

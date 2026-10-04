package dev.wearjelly.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 上次播放记忆（LastPlayback）：播放服务在切歌/暂停/销毁及播放中周期性写入；
 * 应用冷启动时由 PlaybackService 只恢复队列与进度，不自动播放。
 */
@Serializable
data class LastPlayback(
    val trackId: String,
    val queue: List<JellyfinItem>,
    val queueIndex: Int,
    val positionMs: Long,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val updatedAt: Long,
)

class LastPlaybackStore(private val context: Context, private val json: Json) {

    private val _lastPlayback = MutableStateFlow<LastPlayback?>(null)
    val lastPlayback: StateFlow<LastPlayback?> = _lastPlayback.asStateFlow()

    private val file: File
        get() = File(context.filesDir, "last_playback.json")

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) {
            _lastPlayback.value = null
            return
        }
        try {
            _lastPlayback.value = json.decodeFromString<LastPlayback>(file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            _lastPlayback.value = null
        }
    }

    @Synchronized
    fun save(snapshot: LastPlayback) {
        try {
            file.writeText(json.encodeToString(snapshot))
            _lastPlayback.value = snapshot
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun clear() {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
        _lastPlayback.value = null
    }

    companion object {
        const val QUEUE_LIMIT = 200

        /**
         * 队列超过上限时截断为当前曲目附近的窗口，并返回调整后的索引。
         * 纯函数便于 JVM 单测。
         */
        fun capQueueAroundIndex(queue: List<JellyfinItem>, index: Int): Pair<List<JellyfinItem>, Int> {
            if (queue.size <= QUEUE_LIMIT) return queue to index
            val clamped = index.coerceIn(0, queue.size - 1)
            val from = (clamped - QUEUE_LIMIT / 2).coerceAtLeast(0)
            val to = (from + QUEUE_LIMIT).coerceAtMost(queue.size)
            return queue.subList(from, to) to (clamped - from)
        }
    }
}

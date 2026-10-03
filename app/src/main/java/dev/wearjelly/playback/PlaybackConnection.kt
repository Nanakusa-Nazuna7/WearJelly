package dev.wearjelly.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.JellyfinRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(UnstableApi::class)
class PlaybackConnection(
    private val context: Context,
    private val repository: JellyfinRepository,
    private val downloadManager: dev.wearjelly.data.DownloadManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionTickerJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null

    init {
        connect()
    }

    fun connect() {
        if (controller != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val c = controllerFuture.get()
                controller = c
                _state.update { it.copy(connected = true) }
                setupPlayerListener(c)
                updateStateFromPlayer(c)
            } catch (e: Exception) {
                _state.update { it.copy(connected = false, error = "连接后台播放服务失败: ${e.localizedMessage}") }
            }
        }, { r -> r.run() })
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _state.update { it.copy(buffering = isBuffering) }
                if (isBuffering || player.isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
                updateStateFromPlayer(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateStateFromPlayer(player)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateStateFromPlayer(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(error = error.localizedMessage ?: "播放错误") }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _state.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            }
        })
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.coerceAtLeast(0L),
                            bufferedPositionMs = c.bufferedPosition.coerceAtLeast(0L),
                            bufferedPercentage = c.bufferedPercentage.coerceIn(0, 100)
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    private fun updateStateFromPlayer(player: Player) {
        val currentMediaItem = player.currentMediaItem
        var currentJellyfinItem: JellyfinItem? = null

        val currentJson = currentMediaItem?.mediaMetadata?.extras?.getString("jellyfin_item_json")
        if (!currentJson.isNullOrBlank()) {
            try {
                currentJellyfinItem = json.decodeFromString<JellyfinItem>(currentJson)
            } catch (_: Exception) {}
        }

        val queue = mutableListOf<QueueTrack>()
        val count = player.mediaItemCount
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            val raw = item.mediaMetadata.extras?.getString("jellyfin_item_json")
            if (!raw.isNullOrBlank()) {
                try {
                    val jItem = json.decodeFromString<JellyfinItem>(raw)
                    queue.add(QueueTrack(item = jItem, artworkUrl = item.mediaMetadata.artworkUri?.toString()))
                } catch (_: Exception) {}
            }
        }

        _state.update {
            it.copy(
                queue = queue,
                currentIndex = player.currentMediaItemIndex,
                current = currentJellyfinItem,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                bufferedPercentage = player.bufferedPercentage.coerceIn(0, 100),
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                volumePercent = currentVolumePercent()
            )
        }
    }

    fun play(items: List<JellyfinItem>, index: Int = 0) {
        val c = controller ?: return
        val args = Bundle().apply {
            putString(PlaybackService.EXTRA_ITEMS_JSON, json.encodeToString(items))
            putInt(PlaybackService.EXTRA_START_INDEX, index)
        }
        c.sendCustomCommand(SessionCommand(PlaybackService.CUSTOM_COMMAND_SET_QUEUE, Bundle.EMPTY), args)
    }

    suspend fun enqueue(item: JellyfinItem): Result<Unit> = enqueueMany(listOf(item))

    suspend fun enqueueMany(items: List<JellyfinItem>): Result<Unit> =
        sendItemsCommand(PlaybackService.CUSTOM_COMMAND_ENQUEUE_MANY, items)

    /** 下一首播放：插到当前曲目之后；队列为空时由服务退化为直接建队播放。 */
    suspend fun insertNext(items: List<JellyfinItem>): Result<Unit> =
        sendItemsCommand(PlaybackService.CUSTOM_COMMAND_INSERT_NEXT, items)

    private suspend fun sendItemsCommand(action: String, items: List<JellyfinItem>): Result<Unit> {
        if (items.isEmpty()) return Result.success(Unit)
        val c = controller ?: return Result.failure(IllegalStateException("播放器尚未连接"))
        val args = Bundle().apply {
            putString(PlaybackService.EXTRA_ITEMS_JSON, json.encodeToString(items))
        }
        return try {
            val future = c.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
            val result = suspendCancellableCoroutine<SessionResult> { continuation ->
                future.addListener(
                    {
                        try {
                            continuation.resume(future.get())
                        } catch (error: Exception) {
                            continuation.resume(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
                        }
                    },
                    { runnable -> runnable.run() }
                )
                continuation.invokeOnCancellation { future.cancel(true) }
            }
            if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                updateStateFromPlayer(c)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("播放器拒绝加入队列 (${result.resultCode})"))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            c.play()
        }
    }

    /** 从 LastPlayback 恢复态继续播放；队列不存在或未连接返回 false。 */
    fun resumePlayback(): Boolean {
        val c = controller ?: return false
        if (c.mediaItemCount == 0) return false
        c.play()
        return true
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.let {
            if (it.currentPosition > 3000L) {
                it.seekTo(0L)
            } else {
                it.seekToPreviousMediaItem()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs)
        // 控制器 seekTo 是异步会话命令，立即读取 currentPosition 仍是旧值：
        // 先刷新其他字段，再乐观更新位置，保证进度条/歌词即时同步；下一次 ticker 以真实位置校正
        updateStateFromPlayer(c)
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun playQueueIndex(index: Int) {
        controller?.seekTo(index, 0L)
    }

    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        val args = Bundle().apply {
            putInt(PlaybackService.EXTRA_INDEX, index)
        }
        c.sendCustomCommand(SessionCommand(PlaybackService.CUSTOM_COMMAND_REMOVE_AT, Bundle.EMPTY), args)
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        val args = Bundle().apply {
            putInt(PlaybackService.EXTRA_FROM_INDEX, from)
            putInt(PlaybackService.EXTRA_TO_INDEX, to)
        }
        c.sendCustomCommand(SessionCommand(PlaybackService.CUSTOM_COMMAND_MOVE_ITEM, Bundle.EMPTY), args)
    }

    fun adjustVolume(direction: Int) {
        val adjustment = if (direction < 0) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustment, 0)
        _state.update { it.copy(volumePercent = currentVolumePercent()) }
    }

    private fun currentVolumePercent(): Int {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current.toFloat() / maximum.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
    }

    fun stopAndClear() {
        val c = controller ?: return
        c.sendCustomCommand(SessionCommand(PlaybackService.CUSTOM_COMMAND_CLEAR_QUEUE, Bundle.EMPTY), Bundle.EMPTY)
        c.stop()
    }

    fun release() {
        stopPositionTicker()
        controller?.release()
        controller = null
    }
}

package dev.wearjelly.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import java.io.File
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

// t26/H2：队列中转文件的 cache 子目录名与孤儿文件回收时限（命令未送达时的残留）
// 目录名需被 PlaybackService.readItemsPayload 校验使用，故为 internal
internal const val QUEUE_PAYLOAD_DIR = "queue_payloads"
private const val QUEUE_PAYLOAD_TTL_MS = 60 * 60 * 1000L

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
        val controllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                // t26/M3：服务进程被杀时 media3 走 binder 死亡 → 自动 release() → 回调本方法
                // （MediaControllerImplBase.deathRecipient → MediaController.release() 内
                // notifyControllerListener(onDisconnected)）。原实现不监听：字段与 connected
                // 残留 true → UI 假连接、waitForConnected 立即放行而命令全部静默丢弃。
                // 此处清空字段、停位置表、置断连，并延迟重绑——AppViewModel.connect() 仅在
                // 启动时调用一次，这里是断连后唯一的恢复入口；重绑失败走下方 catch，
                // 不会形成重试循环。
                override fun onDisconnected(controller: MediaController) {
                    if (this@PlaybackConnection.controller !== controller) return
                    this@PlaybackConnection.controller = null
                    stopPositionTicker()
                    _state.update { it.copy(connected = false, isPlaying = false, buffering = false) }
                    scope.launch {
                        delay(1_000L)
                        connect()
                    }
                }
            })
            .buildAsync()
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
            // t26/H2：整包队列 JSON 不再直接进 Bundle 走 Binder（~1MB 上限），改走 cache 中转文件
            putString(PlaybackService.EXTRA_ITEMS_FILE, writeQueuePayload(items))
            putInt(PlaybackService.EXTRA_START_INDEX, index)
        }
        c.sendCustomCommand(SessionCommand(PlaybackService.CUSTOM_COMMAND_SET_QUEUE, Bundle.EMPTY), args)
    }

    /**
     * t26/H2：队列载荷中转——写入应用私有 cache 目录，Bundle 只携带路径，
     * 规避数百首队列整包 JSON 触发的 TransactionTooLargeException。
     * 选型权衡（临时文件 vs 进程内 holder）：holder 虽零 IO，但单槽位在 play()
     * （fire-and-forget 不等回执）与 enqueue 连发时会互相覆盖，且强依赖服务同进程
     * （当前 manifest 无 android:process 确实同进程，但语义脆弱）；文件方案每次命令
     * 独立命名、读取即删，无竞态且进程隔离下同样成立。编码成本与原实现同阶
     * （原先也在本线程 encodeToString），文件写入为毫秒级。
     */
    private fun writeQueuePayload(items: List<JellyfinItem>): String {
        val dir = File(context.cacheDir, QUEUE_PAYLOAD_DIR).apply { mkdirs() }
        // 顺手清理超过 1 小时的孤儿文件（命令未送达服务端时的残留）
        val cutoff = System.currentTimeMillis() - QUEUE_PAYLOAD_TTL_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, "q_${System.nanoTime()}.json")
        file.writeText(json.encodeToString(items))
        return file.absolutePath
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
            // t26/H2：同 play()——载荷走中转文件，Bundle 只携带路径
            putString(PlaybackService.EXTRA_ITEMS_FILE, writeQueuePayload(items))
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
        val c = controller ?: return
        // 由服务端裁决：随机模式下沿实际播放历史回退；否则重启当前曲目或顺序回退
        c.sendCustomCommand(
            SessionCommand(PlaybackService.CUSTOM_COMMAND_PREVIOUS_HISTORY, Bundle.EMPTY),
            Bundle.EMPTY
        )
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

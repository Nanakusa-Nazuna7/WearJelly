package dev.wearjelly.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(UnstableApi::class)
class PlaybackConnection(
    private val context: Context,
    private val repository: JellyfinRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

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
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _state.update { it.copy(buffering = isBuffering) }
                updateStateFromPlayer(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
                            durationMs = c.duration.coerceAtLeast(0L)
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
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled
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

    fun enqueue(item: JellyfinItem) {
        val c = controller ?: return
        val args = Bundle().apply {
            putString(PlaybackService.EXTRA_ITEMS_JSON, json.encodeToString(item))
        }
        c.sendCustomCommand(SessionCommand(PlaybackService.CUSTOM_COMMAND_ENQUEUE, Bundle.EMPTY), args)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            c.play()
        }
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
        controller?.seekTo(positionMs)
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

package dev.wearjelly.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.wearjelly.MainActivity
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.JellyfinRepository
import dev.wearjelly.data.PlaybackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val repository: JellyfinRepository by inject()
    private val downloadManager: dev.wearjelly.data.DownloadManager by inject()
    private val historyStore: dev.wearjelly.data.HistoryStore by inject()
    private val lastPlaybackStore: dev.wearjelly.data.LastPlaybackStore by inject()
    private val json: Json by inject()

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressReportJob: Job? = null
    private var currentPlayingItemId: String? = null
    private var lastPlaybackPersistAt = 0L
    private val playHistory = PlayHistoryStack()

    companion object {
        const val CUSTOM_COMMAND_SET_QUEUE = "dev.wearjelly.SET_QUEUE"
        const val CUSTOM_COMMAND_ENQUEUE = "dev.wearjelly.ENQUEUE"
        const val CUSTOM_COMMAND_ENQUEUE_MANY = "dev.wearjelly.ENQUEUE_MANY"
        const val CUSTOM_COMMAND_INSERT_NEXT = "dev.wearjelly.INSERT_NEXT"
        const val CUSTOM_COMMAND_REMOVE_AT = "dev.wearjelly.REMOVE_AT"
        const val CUSTOM_COMMAND_MOVE_ITEM = "dev.wearjelly.MOVE_ITEM"
        const val CUSTOM_COMMAND_CLEAR_QUEUE = "dev.wearjelly.CLEAR_QUEUE"
        const val CUSTOM_COMMAND_PREVIOUS_HISTORY = "dev.wearjelly.PREVIOUS_HISTORY"

        const val EXTRA_ITEMS_JSON = "extra_items_json"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_FROM_INDEX = "extra_from_index"
        const val EXTRA_TO_INDEX = "extra_to_index"
        const val EXTRA_INDEX = "extra_index"

        // LastPlayback 播放中位置保存节流间隔
        private const val LAST_PLAYBACK_SAVE_INTERVAL_MS = 5_000L
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val isOwnApp = controller.packageName == packageName
                val isSystemController = controller.controllerVersion == 0
                if (!isOwnApp && !isSystemController) {
                    return MediaSession.ConnectionResult.reject()
                }
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_COMMAND_SET_QUEUE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_ENQUEUE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_ENQUEUE_MANY, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_INSERT_NEXT, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_REMOVE_AT, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_MOVE_ITEM, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_CLEAR_QUEUE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_PREVIOUS_HISTORY, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.accept(
                    commands,
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                )
            }

            // WV-008：随机模式下外部控制器的「上一首」转内部历史回退，阻断原生 no-op 路径
            @Suppress("DEPRECATION")
            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                if (shouldRouteExternalPreviousToHistory(playerCommand, session.player.shuffleModeEnabled)) {
                    handlePrevious()
                    // 拒绝随后的原生执行（MediaSessionLegacyStub/MediaSessionStub 在非 SUCCESS 时跳过命令）
                    return SessionResult.RESULT_ERROR_PERMISSION_DENIED
                }
                return SessionResult.RESULT_SUCCESS
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    CUSTOM_COMMAND_SET_QUEUE -> {
                        val itemsJson = args.getString(EXTRA_ITEMS_JSON)
                        val startIndex = args.getInt(EXTRA_START_INDEX, 0)
                        if (!itemsJson.isNullOrBlank()) {
                            try {
                                val items = json.decodeFromString<List<JellyfinItem>>(itemsJson)
                                handleSetQueue(items, startIndex)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    CUSTOM_COMMAND_ENQUEUE -> {
                        val itemJson = args.getString(EXTRA_ITEMS_JSON)
                        if (!itemJson.isNullOrBlank()) {
                            try {
                                val item = json.decodeFromString<JellyfinItem>(itemJson)
                                handleEnqueue(item)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    CUSTOM_COMMAND_ENQUEUE_MANY -> {
                        val itemsJson = args.getString(EXTRA_ITEMS_JSON)
                        if (!itemsJson.isNullOrBlank()) {
                            try {
                                val items = json.decodeFromString<List<JellyfinItem>>(itemsJson)
                                handleEnqueueMany(items)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                return Futures.immediateFuture(
                                    SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                                )
                            }
                        }
                    }
                    CUSTOM_COMMAND_INSERT_NEXT -> {
                        val itemsJson = args.getString(EXTRA_ITEMS_JSON)
                        if (!itemsJson.isNullOrBlank()) {
                            try {
                                val items = json.decodeFromString<List<JellyfinItem>>(itemsJson)
                                handleInsertNext(items)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                return Futures.immediateFuture(
                                    SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                                )
                            }
                        }
                    }
                    CUSTOM_COMMAND_REMOVE_AT -> {
                        val index = args.getInt(EXTRA_INDEX, -1)
                        if (index in 0 until (player?.mediaItemCount ?: 0)) {
                            // WV-009：队列移除同步清掉随机历史，避免「上一首」回退到已删曲目
                            player?.getMediaItemAt(index)?.mediaId?.let { playHistory.remove(it) }
                            player?.removeMediaItem(index)
                        }
                    }
                    CUSTOM_COMMAND_MOVE_ITEM -> {
                        val from = args.getInt(EXTRA_FROM_INDEX, -1)
                        val to = args.getInt(EXTRA_TO_INDEX, -1)
                        val count = player?.mediaItemCount ?: 0
                        if (from in 0 until count && to in 0 until count && from != to) {
                            player?.moveMediaItem(from, to)
                        }
                    }
                    CUSTOM_COMMAND_CLEAR_QUEUE -> {
                        player?.clearMediaItems()
                        playHistory.clear()
                        stopProgressReporting()
                        lastPlaybackStore.clear()
                    }
                    CUSTOM_COMMAND_PREVIOUS_HISTORY -> handlePrevious()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        val session = MediaSession.Builder(this, exo)
            .setCallback(callback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        player = exo
        mediaSession = session

        restoreLastPlayback()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startProgressReporting()
                    // 兜底补记：缓冲完成进入播放、或从 LastPlayback 恢复后继续播放时
                    // 没有新的转场事件，历史在这里落账（HistoryStore 自身按曲目去重）
                    exo.currentMediaItem?.mediaId?.let { recordHistory(it) }
                    reportEvent(PlaybackEvent.START)
                } else {
                    stopProgressReporting()
                    reportEvent(PlaybackEvent.PROGRESS)
                    persistLastPlayback(force = true)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val oldId = currentPlayingItemId
                val newId = mediaItem?.mediaId
                if (oldId != null && oldId != newId) {
                    reportEvent(PlaybackEvent.STOP, specificItemId = oldId)
                }
                currentPlayingItemId = newId
                persistLastPlayback(force = true)
                // 用 playWhenReady 判定播放意图而非 isPlaying：网络流转场时处于
                // BUFFERING，isPlaying 为 false 会导致历史永远记不上（旧 bug）
                if (newId != null && player?.playWhenReady == true) {
                    recordHistory(newId)
                    reportEvent(PlaybackEvent.START)
                }
                // 随机模式下每进入新曲目即开启新一轮随机：避开最近播放的曲目
                if (player?.shuffleModeEnabled == true) {
                    regenerateShuffleOrder()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // 打开随机时立刻按播放历史生成顺序，当前曲目保持不变
                if (shuffleModeEnabled) {
                    regenerateShuffleOrder()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    reportEvent(PlaybackEvent.STOP)
                    stopProgressReporting()
                    persistLastPlayback(force = true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stopProgressReporting()
            }
        })

        // WV-011：restoreLastPlayback 在监听注册前恢复 shuffleModeEnabled，不会触发
        // onShuffleModeEnabledChanged；冷启动随机开启时在此补生成一次随机序（当前曲目固定首位）
        if (exo.shuffleModeEnabled) {
            regenerateShuffleOrder()
        }
    }

    private fun handleSetQueue(items: List<JellyfinItem>, startIndex: Int) {
        val exo = player ?: return
        val mediaItems = items.map { item ->
            createMediaItem(item)
        }
        exo.setMediaItems(mediaItems, startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0)), 0L)
        exo.prepare()
        exo.play()
    }

    private fun handleEnqueue(item: JellyfinItem) {
        handleEnqueueMany(listOf(item))
    }

    private fun handleEnqueueMany(items: List<JellyfinItem>) {
        if (items.isEmpty()) return
        val exo = player ?: return
        exo.addMediaItems(items.map(::createMediaItem))
        if (exo.playbackState == Player.STATE_IDLE) {
            exo.prepare()
        }
    }

    /** 下一首播放：插到当前曲目之后；队列为空时退化为直接建队播放。 */
    private fun handleInsertNext(items: List<JellyfinItem>) {
        if (items.isEmpty()) return
        val exo = player ?: return
        val mediaItems = items.map(::createMediaItem)
        if (exo.mediaItemCount == 0) {
            exo.setMediaItems(mediaItems, 0, 0L)
            exo.prepare()
            exo.play()
        } else {
            exo.addMediaItems(exo.currentMediaItemIndex + 1, mediaItems)
        }
    }

    private fun recordHistory(itemId: String) {
        val exo = player ?: return
        val raw = exo.currentMediaItem?.mediaMetadata?.extras?.getString("jellyfin_item_json")
        if (!raw.isNullOrBlank()) {
            try {
                historyStore.record(json.decodeFromString<JellyfinItem>(raw))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // WV-010：extras 缺失/解析失败也记随机历史，否则随机避让与「上一首」历史静默失效
        playHistory.record(itemId)
    }

    /**
     * 生成新的随机播放顺序：timeline（原队列顺序）不变，仅替换 ShuffleOrder。
     * 当前曲目固定在新顺序首位，最近播放的五首尽量排到本轮末尾。
     */
    private fun regenerateShuffleOrder() {
        val exo = player ?: return
        val count = exo.mediaItemCount
        if (count <= 1) return
        val order = ShuffleQueue.generateOrder(
            size = count,
            currentIndex = exo.currentMediaItemIndex,
            recentTrackIds = playHistory.recent(ShuffleQueue.RECENTLY_PLAYED_AVOID_COUNT),
            trackIdAt = { i -> exo.getMediaItemAt(i).mediaId }
        )
        exo.setShuffleOrder(DefaultShuffleOrder(order, System.nanoTime()))
    }

    /** 上一首：随机模式下沿实际播放历史回退；否则保持原地重启/顺序回退的默认行为。 */
    private fun handlePrevious() {
        val exo = player ?: return
        if (exo.shuffleModeEnabled) {
            var previousIndex = -1
            val previousId = playHistory.goToPrevious { trackId ->
                previousIndex = (0 until exo.mediaItemCount).firstOrNull {
                    exo.getMediaItemAt(it).mediaId == trackId
                } ?: -1
                previousIndex >= 0
            }
            if (previousId != null) {
                exo.seekTo(previousIndex, 0L)
                exo.play()
                return
            }
        }
        if (exo.currentPosition > 3_000L) {
            exo.seekTo(0L)
        } else {
            exo.seekToPreviousMediaItem()
        }
        exo.play()
    }

    /**
     * LastPlayback 持久化：force=true 用于切歌/暂停/销毁等关键节点；
     * 播放中由进度循环按节流间隔保存位置。
     */
    private fun persistLastPlayback(force: Boolean) {
        val exo = player ?: return
        if (exo.mediaItemCount == 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPlaybackPersistAt < LAST_PLAYBACK_SAVE_INTERVAL_MS) return
        lastPlaybackPersistAt = now
        val items = mutableListOf<JellyfinItem>()
        for (i in 0 until exo.mediaItemCount) {
            val raw = exo.getMediaItemAt(i).mediaMetadata.extras?.getString("jellyfin_item_json")
            if (raw.isNullOrBlank()) continue
            try {
                items += json.decodeFromString<JellyfinItem>(raw)
            } catch (_: Exception) {
            }
        }
        if (items.isEmpty()) return
        val index = exo.currentMediaItemIndex
        val (cappedQueue, cappedIndex) = dev.wearjelly.data.LastPlaybackStore.capQueueAroundIndex(items, index)
        val trackId = exo.currentMediaItem?.mediaId ?: cappedQueue.getOrNull(cappedIndex)?.id ?: return
        serviceScope.launch {
            lastPlaybackStore.save(
                dev.wearjelly.data.LastPlayback(
                    trackId = trackId,
                    queue = cappedQueue,
                    queueIndex = cappedIndex,
                    positionMs = exo.currentPosition.coerceAtLeast(0L),
                    repeatMode = exo.repeatMode,
                    shuffleEnabled = exo.shuffleModeEnabled,
                    updatedAt = now,
                )
            )
        }
    }

    /** 冷启动恢复：只载入队列与进度并 prepare，不自动播放。 */
    private fun restoreLastPlayback() {
        val exo = player ?: return
        val snapshot = lastPlaybackStore.lastPlayback.value ?: return
        if (snapshot.queue.isEmpty()) {
            lastPlaybackStore.clear()
            return
        }
        val index = snapshot.queueIndex.coerceIn(0, snapshot.queue.size - 1)
        exo.setMediaItems(snapshot.queue.map(::createMediaItem), index, snapshot.positionMs.coerceAtLeast(0L))
        exo.repeatMode = snapshot.repeatMode
        exo.shuffleModeEnabled = snapshot.shuffleEnabled
        exo.prepare()
    }

    private fun createMediaItem(item: JellyfinItem): MediaItem {
        val localPath = downloadManager.getLocalFilePath(item.id)
        val streamUri = if (!localPath.isNullOrBlank()) {
            Uri.fromFile(java.io.File(localPath))
        } else {
            Uri.parse(repository.streamUrl(item.id))
        }
        val localCoverPath = downloadManager.getLocalCoverPath(item.id)
        val artworkUri = localCoverPath?.let { Uri.fromFile(java.io.File(it)) }
            ?: repository.imageUrl(item)?.let { Uri.parse(it) }

        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setArtist(item.artistText)
            .setAlbumTitle(item.album)
            .setArtworkUri(artworkUri)
            .setExtras(Bundle().apply {
                putString("jellyfin_item_json", json.encodeToString(item))
            })
            .build()

        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(streamUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun startProgressReporting() {
        progressReportJob?.cancel()
        progressReportJob = serviceScope.launch {
            while (isActive) {
                delay(10000L) // 每 10 秒定期向 Jellyfin 上报一次播放进度
                reportEvent(PlaybackEvent.PROGRESS)
                persistLastPlayback(force = false) // 同节流保存 LastPlayback 位置
            }
        }
    }

    private fun stopProgressReporting() {
        progressReportJob?.cancel()
        progressReportJob = null
    }

    private fun reportEvent(event: PlaybackEvent, specificItemId: String? = null) {
        val exo = player ?: return
        val itemId = specificItemId ?: exo.currentMediaItem?.mediaId ?: return
        val pos = exo.currentPosition
        val isPaused = !exo.isPlaying
        serviceScope.launch {
            repository.reportPlayback(itemId, pos, isPaused, event)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        persistLastPlayback(force = true)
        stopProgressReporting()
        reportEvent(PlaybackEvent.STOP)
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}

/**
 * WV-008：外部控制器（蓝牙/通知栏/系统媒体键）的「上一首」是否转内部历史回退。
 * 随机开启时原生 seekToPrevious/seekToPreviousMediaItem 因「当前曲目固定 ShuffleOrder 首位」
 * 是 no-op，必须转 handlePrevious()；非随机时保持原生行为不变。
 */
internal fun shouldRouteExternalPreviousToHistory(playerCommand: Int, shuffleEnabled: Boolean): Boolean =
    shuffleEnabled &&
        (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
            playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)

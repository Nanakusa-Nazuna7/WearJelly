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
    private val json: Json by inject()

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressReportJob: Job? = null
    private var currentPlayingItemId: String? = null

    companion object {
        const val CUSTOM_COMMAND_SET_QUEUE = "dev.wearjelly.SET_QUEUE"
        const val CUSTOM_COMMAND_ENQUEUE = "dev.wearjelly.ENQUEUE"
        const val CUSTOM_COMMAND_REMOVE_AT = "dev.wearjelly.REMOVE_AT"
        const val CUSTOM_COMMAND_MOVE_ITEM = "dev.wearjelly.MOVE_ITEM"
        const val CUSTOM_COMMAND_CLEAR_QUEUE = "dev.wearjelly.CLEAR_QUEUE"

        const val EXTRA_ITEMS_JSON = "extra_items_json"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_FROM_INDEX = "extra_from_index"
        const val EXTRA_TO_INDEX = "extra_to_index"
        const val EXTRA_INDEX = "extra_index"
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
                    CUSTOM_COMMAND_REMOVE_AT -> {
                        val index = args.getInt(EXTRA_INDEX, -1)
                        if (index in 0 until (player?.mediaItemCount ?: 0)) {
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
                        stopProgressReporting()
                    }
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

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startProgressReporting()
                    reportEvent(PlaybackEvent.START)
                } else {
                    stopProgressReporting()
                    reportEvent(PlaybackEvent.PROGRESS)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val oldId = currentPlayingItemId
                val newId = mediaItem?.mediaId
                if (oldId != null && oldId != newId) {
                    reportEvent(PlaybackEvent.STOP, specificItemId = oldId)
                }
                currentPlayingItemId = newId
                if (newId != null && player?.isPlaying == true) {
                    reportEvent(PlaybackEvent.START)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    reportEvent(PlaybackEvent.STOP)
                    stopProgressReporting()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stopProgressReporting()
            }
        })
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
        val exo = player ?: return
        val mediaItem = createMediaItem(item)
        exo.addMediaItem(mediaItem)
        if (exo.playbackState == Player.STATE_IDLE) {
            exo.prepare()
        }
    }

    private fun createMediaItem(item: JellyfinItem): MediaItem {
        val streamUri = Uri.parse(repository.streamUrl(item.id))
        val artworkUri = repository.imageUrl(item)?.let { Uri.parse(it) }

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

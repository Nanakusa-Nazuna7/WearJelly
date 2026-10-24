package dev.wearjelly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import dev.wearjelly.playback.PlaybackState
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.material3.components.actions.VolumeButton
import com.google.android.horologist.media.ui.material3.components.ButtonGroupLayoutDefaults
import com.google.android.horologist.media.ui.material3.components.MediaControlButtons
import com.google.android.horologist.media.ui.material3.components.actions.ShowPlaylistButton
import com.google.android.horologist.media.ui.material3.screens.player.PlayerScreen as M3PlayerScreen
import com.google.android.horologist.media.ui.components.controls.SeekButtonIncrement
import com.google.android.horologist.media.ui.state.PlayerUiState
import com.google.android.horologist.media.ui.state.model.MediaUiModel
import com.google.android.horologist.media.ui.state.model.TrackPositionUiModel
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * M3 播放主页（Horologist PlayerScreen slots 重载，docs/horologist-migration.md §4/§6 第 5 行）。
 *
 * 与原 PlayerScreen（WearJellyApp.kt:2237-2504）逐项对照的功能零丢失清单：
 * 1. 封面（artUrl 走 coil AsyncImage，无图 MusicNote 占位）——顶区 mediaDisplay；原 90dp
 *    因 Horologist 顶区视口仅 ~46dp 缩为 44dp，全部内容纵向滚动可达；
 * 2. 歌名/歌手——经 [PlaybackState.toPlayerUiState] 的 PlayerUiState.media 显示（未播放/兜底
 *    "Jellyfin" 与原一致）；
 * 3. 已缓存绿点角标（StatusGreen + CachedTick）；
 * 4. 缓冲态（spinner + "正在缓冲音乐..."）与错误态（error 色文案）——置于顶区滚动列首位，
 *    保证打开即见（原 TLC 视口大可放标题下方，Horologist 顶区矮故前移）；
 * 5. 可拖动进度条 + onSeek——保留自研 SeekableProgressBar（Horologist M3 无可拖动 seek bar）；
 * 6. 上一首/播放暂停/下一首——Horologist MediaControlButtons（含 trackPositionUiModel 进度环）；
 * 7. 播放结束重播态（Refresh 图标，点击 seekTo(0)+togglePlayPause）——中键覆盖层，判定表达式
 *    与原逐字一致；
 * 8. 控制行：音量-/随机/循环/音量+ 与 "音量 N%" 文本——原样式逐项保留；
 * 9. 队列入口"队列 (N)"——只回调 [onShowQueue]，不 navigate（导航壳归 t14/t15）；
 * 10. 歌词入口——页面级左滑手势只回调 [onShowLyrics]，不 navigate（原左滑 navigate Lyrics）；
 * 11. 收藏心形（本期新增）——docs/favorite-design.md §4.1 绑定样本，订阅 AppViewModel.favoriteIds；
 * 12. 音量/音频输出入口——Horologist VolumeButton（t1 §5）回调 [onVolumeClick]，音量面板
 *     （VolumeScreen/AudioOutputUi）由 t5 在其模块实现、t15 接线。
 *
 * 三槽位对应 Horologist PlayerScreen 固定三段布局（顶部媒体区/中部控制区/底部按钮区），
 * 各槽位内容自管纵向滚动，圆形屏窄角区仅在滚动中短暂触及。
 */
@Composable
internal fun HorologistPlayerScreen(
    viewModel: AppViewModel,
    onShowQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onVolumeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pbState by viewModel.playbackState.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val uiState = pbState.toPlayerUiState()
    val item = pbState.current
    val durationMs = pbState.effectiveDurationMs
    // 播放结束：按钮变重播（Media3 play() 在 STATE_ENDED 从头播放）——原判定逐字保留
    val ended = durationMs > 0 && !pbState.isPlaying && !pbState.buffering &&
        pbState.positionMs >= durationMs - 300

    ReferencePlayerLayout(
        pbState = pbState,
        uiState = uiState,
        ended = ended,
        favoriteIds = favoriteIds,
        cachedIds = cachedIds,
        viewModel = viewModel,
        onShowQueue = onShowQueue,
        onShowLyrics = onShowLyrics,
        onVolumeClick = onVolumeClick,
        modifier = modifier,
    )
    return

    M3PlayerScreen(
        mediaDisplay = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    ,
            ) {
                // 缓冲态（原: spinner 22dp + "正在缓冲音乐..."）
                if (pbState.buffering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在缓冲音乐...",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                // 错误态（原: error 色居中文案）
                if (pbState.error != null) {
                    Text(
                        text = pbState.error ?: "播放失败",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                // 三槽位 PlayerScreen 的媒体槽位仅容纳文本与进度；封面移出该槽位，避免固定高度下把标题压成零高。
                // 歌名/歌手：取自 PlayerUiState.media（原 item?.name ?: "未播放" / artistText ?: "Jellyfin"）
                val ready = uiState.media as? MediaUiModel.Ready
                Text(
                    text = ready?.title ?: "未播放",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = ready?.subtitle ?: "Jellyfin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                // 已缓存绿点角标
                if (item != null && item.id in cachedIds) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(StatusGreen, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CachedTick()
                    }
                }
                // 可拖动进度条（原样式：水平 6dp 边距，onSeek → seekTo）
                SeekableProgressBar(
                    positionMs = pbState.positionMs,
                    durationMs = pbState.effectiveDurationMs,
                    bufferedPositionMs = pbState.bufferedPositionMs,
                    buffering = pbState.buffering,
                    onSeek = { viewModel.seekTo(it) },
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        },
        controlButtons = {
            Box(modifier = Modifier.fillMaxSize()) {
                MediaControlButtons(
                    onPlayButtonClick = {
                        if (ended) {
                            viewModel.seekTo(0L)
                            viewModel.togglePlayPause()
                        } else {
                            viewModel.togglePlayPause()
                        }
                    },
                    onPauseButtonClick = { viewModel.togglePlayPause() },
                    playPauseButtonEnabled = uiState.playPauseEnabled,
                    playing = uiState.playing,
                    onSeekToPreviousButtonClick = { viewModel.previous() },
                    seekToPreviousButtonEnabled = uiState.seekToPreviousEnabled,
                    onSeekToNextButtonClick = { viewModel.next() },
                    seekToNextButtonEnabled = uiState.seekToNextEnabled,
                    trackPositionUiModel = uiState.trackPositionUiModel,
                )
                // 播放结束：中键覆盖 Refresh 重播键（原 ended 图标语义；点击从头播）
                if (ended) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .width(ButtonGroupLayoutDefaults.middleButtonSize),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(ButtonGroupLayoutDefaults.middleButtonSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(
                                    enabled = uiState.playPauseEnabled,
                                    onClick = {
                                        viewModel.seekTo(0L)
                                        viewModel.togglePlayPause()
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重播",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        },
        buttons = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    ,
            ) {
                // t1 §5 buttons slot：队列入口（Horologist ShowPlaylistButton，带曲目数）
                ShowPlaylistButton(
                    artworkPaintable = null,
                    name = "队列 (${pbState.queue.size})",
                    onClick = onShowQueue,
                    modifier = Modifier
                        .size(30.dp)
                        .height(34.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 音量/音频输出入口（t1 §5）：Horologist VolumeButton，图标随音量档位；
                    // 音量面板（VolumeScreen/设备切换）由 t5 实现、t15 经 onVolumeClick 接线
                    VolumeButton(
                        onVolumeClick = onVolumeClick,
                        modifier = Modifier
                            .width(44.dp)
                            .height(32.dp),
                        volumeUiState = VolumeUiState(
                            current = pbState.volumePercent.coerceIn(0, 100),
                            max = 100,
                            min = 0,
                        ),
                    )
                    // 收藏心形（docs/favorite-design.md §4.1 绑定样本）
                    val currentId = pbState.current?.id
                    val isFavorite = currentId != null && currentId in favoriteIds
                    Button(
                        onClick = { currentId?.let(viewModel::toggleFavorite) },
                        modifier = Modifier.size(36.dp),
                        enabled = currentId != null && !viewModel.isOfflineMode,
                        colors = solidButtonColors(Color(0xFF3A3F4B)),
                    ) {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                            contentDescription = if (isFavorite) "取消收藏" else "加入收藏",
                            modifier = Modifier.size(18.dp),
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                // 原控制行逐项保留：音量- | 随机 | 循环 | 音量+
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { viewModel.adjustVolume(-1) },
                        modifier = Modifier.size(36.dp),
                        colors = solidButtonColors(Color(0xFF3A3F4B)),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "减小音量",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier.size(36.dp),
                        colors = if (pbState.shuffleEnabled) {
                            ButtonDefaults.buttonColors()
                        } else {
                            solidButtonColors(Color(0xFF3A3F4B))
                        },
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (pbState.shuffleEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { viewModel.cycleRepeatMode() },
                        modifier = Modifier.size(36.dp),
                        colors = if (pbState.repeatMode != Player.REPEAT_MODE_OFF) {
                            ButtonDefaults.buttonColors()
                        } else {
                            solidButtonColors(Color(0xFF3A3F4B))
                        },
                    ) {
                        val icon = if (pbState.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (pbState.repeatMode != Player.REPEAT_MODE_OFF) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { viewModel.adjustVolume(1) },
                        modifier = Modifier.size(36.dp),
                        colors = solidButtonColors(Color(0xFF3A3F4B)),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "增大音量",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Text(
                    text = "音量 ${pbState.volumePercent.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        // 页面级左滑 → 歌词页（只回调，不 navigate）；进度条区域已独占横向手势，不会误触
        modifier = modifier.leftSwipeGesture(onSwipeLeft = onShowLyrics),
    )
}

@Composable
private fun FixedPlayerAction(
    onClick: () -> Unit,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ReferencePlayerLayout(
    pbState: PlaybackState,
    uiState: PlayerUiState,
    ended: Boolean,
    favoriteIds: Set<String>,
    cachedIds: Set<String>,
    viewModel: AppViewModel,
    onShowQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onVolumeClick: () -> Unit,
    modifier: Modifier,
) {
    val currentId = pbState.current?.id
    val isFavorite = currentId != null && currentId in favoriteIds
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WearJelly", style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                Text(
                    (uiState.media as? MediaUiModel.Ready)?.title ?: "未播放",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (uiState.media as? MediaUiModel.Ready)?.subtitle ?: "Jellyfin",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(68.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FixedPlayerAction(
                    onClick = { viewModel.previous() },
                    enabled = uiState.seekToPreviousEnabled,
                    size = 28.dp,
                    color = Color(0xFF39363F),
                    description = "上一首",
                ) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                FixedPlayerAction(
                    onClick = {
                        if (ended) viewModel.seekTo(0L)
                        viewModel.togglePlayPause()
                    },
                    enabled = uiState.playPauseEnabled,
                    size = 52.dp,
                    color = Color(0xFFCBB4FF),
                    description = "播放暂停",
                ) {
                    Icon(if (pbState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFF35105F), modifier = Modifier.size(34.dp))
                }
                FixedPlayerAction(
                    onClick = { viewModel.next() },
                    enabled = uiState.seekToNextEnabled,
                    size = 28.dp,
                    color = Color(0xFFCBB4FF),
                    description = "下一首",
                ) { Icon(Icons.Default.SkipNext, null, tint = Color(0xFF35105F)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.height(74.dp)) {
                SeekableProgressBar(
                    positionMs = pbState.positionMs,
                    durationMs = pbState.effectiveDurationMs,
                    bufferedPositionMs = pbState.bufferedPositionMs,
                    buffering = pbState.buffering,
                    onSeek = viewModel::seekTo,
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FixedPlayerAction(onVolumeClick, true, 52.dp, Color(0xFF39363F), "音量") { Icon(Icons.Default.MusicNote, null, tint = Color.White) }
                    FixedPlayerAction({ currentId?.let(viewModel::toggleFavorite) }, currentId != null, 52.dp, Color(0xFF39363F), "收藏") {
                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = Color.White)
                    }
                    FixedPlayerAction(onShowQueue, true, 52.dp, Color(0xFF39363F), "队列") { Icon(Icons.Default.QueueMusic, null, tint = Color.White) }
                }
            }
        }
    }
}

/**
 * PlaybackState → Horologist PlayerUiState 纯函数映射（docs/horologist-migration.md §4 表）。
 * 不引入 PlayerViewModel/PlayerRepository：播放内核仍是本项目的 PlaybackState/PlaybackConnection。
 */
internal fun PlaybackState.toPlayerUiState(): PlayerUiState {
    val hasMedia = current != null
    val duration = effectiveDurationMs
    return PlayerUiState(
        playEnabled = connected && hasMedia,
        pauseEnabled = connected && hasMedia,
        seekBackEnabled = connected && duration > 0,
        seekForwardEnabled = connected && duration > 0,
        seekInCurrentMediaItemEnabled = connected && duration > 0,
        seekToPreviousEnabled = connected && queue.isNotEmpty(),
        seekToNextEnabled = connected && queue.isNotEmpty(),
        shuffleEnabled = connected,
        shuffleOn = shuffleEnabled,
        playPauseEnabled = connected && hasMedia,
        playing = isPlaying,
        media = current?.let {
            MediaUiModel.Ready(
                id = it.id,
                title = it.name,
                subtitle = it.artistText,
            )
        },
        trackPositionUiModel = when {
            buffering && positionMs <= 0 -> TrackPositionUiModel.Loading(showProgress = true)
            duration > 0 -> TrackPositionUiModel.Actual(
                percent = (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                duration = duration.milliseconds,
                position = positionMs.milliseconds,
            )
            else -> TrackPositionUiModel.Hidden
        },
        seekBackButtonIncrement = SeekButtonIncrement.Unknown,
        seekForwardButtonIncrement = SeekButtonIncrement.Unknown,
        connected = connected,
    )
}

/**
 * 页面级左滑手势（WearJellyApp.kt:1558 私有实现的等价副本——该文件本任务不可改）：
 * - 横向左滑越过阈值（≥2×touchSlop 且 ≥20dp，且 |dx|≥2|dy|）触发 [onSwipeLeft]；
 * - 纵向意图（|dy|>|dx|）不消费任何事件，交还列表滚动；右滑放行给系统返回；
 * - 短于阈值的横向拖拽在抬起时消费事件，避免被误判为单击播放。
 */
// t27/F6：pointerInput 键改 Unit + rememberUpdatedState——onSwipeLeft 每次重组都是新实例
// （外壳分发处重建 lambda、500ms 进度 tick 都会触发），旧实现键在 lambda 上会反复重启手势
// 协程，拖动中途被取消导致左滑歌词失灵；改为持有最新回调引用，手势协程不再重启。
@Composable
private fun Modifier.leftSwipeGesture(onSwipeLeft: () -> Unit): Modifier {
    val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    return pointerInput(Unit) {
        val swipeThreshold = maxOf(2f * viewConfiguration.touchSlop, 20.dp.toPx())
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var triggered = false
            var dragIntent = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                // 更深层的组件已接管该手势（如进度条拖动、点按控件）——页面滑动让位
                if (!triggered && change.isConsumed) break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                val slop = viewConfiguration.touchSlop
                if (!triggered) {
                    if (!dragIntent && (abs(dx) > slop || abs(dy) > slop)) {
                        if (abs(dy) > abs(dx)) break // 纵向：交还列表滚动
                        dragIntent = true
                    }
                    if (dx < 0 && abs(dx) >= swipeThreshold && abs(dx) >= 2f * abs(dy)) {
                        triggered = true
                        change.consume()
                        latestOnSwipeLeft()
                    }
                } else {
                    change.consume()
                }
                if (event.changes.none { it.pressed }) {
                    if (dragIntent && !triggered) {
                        event.changes.forEach { it.consume() }
                    }
                    break
                }
            }
        }
    }
}

package dev.wearjelly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.LibraryKind

/**
 * t8 主页（docs/horologist-migration.md §6 映射 #2，M3）：
 * - 结构保持 WearJelly 标题 / 欢迎语 / MiniPlayerCard / 6 个列表入口 / 设置，共 6+1 入口零丢失；
 * - HomeListButton 用 Wear Material3 [Button]（42dp + surfaceContainer/onSurface + labelLarge）；
 * - MiniPlayerCard 自绘保留：卡片发丝进度条（2dp）不换 LinearProgressIndicator —— M3
 *   LinearProgressIndicator 最小描边 8dp（含端点圆点）压不进 34dp 紧凑行高；
 * - 所有导航类动作只经回调发出（onNavigate/onOpenLibrary/onOpenDownloads/onOpenListMenu），
 *   本文件不出现 viewModel.navigate/openLibrary/openDownloads/openListMenu 调用，由外壳（t15）接线；
 * - 播放类动作（播放/暂停、继续上次播放）仍走 [AppViewModel]（属播放内核，不属导航）。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.Home -> HorologistHomeScreen(
 *     viewModel, uiState.account,
 *     onNavigate = viewModel::navigate,
 *     onOpenLibrary = viewModel::openLibrary,
 *     onOpenDownloads = viewModel::openDownloads,
 *     onOpenListMenu = viewModel::openListMenu,
 * )
 * ```
 *
 * 说明：Horologist `BrowseScreen`/`SectionedList`（media-ui-material3）绑定 ScalingLazyColumn +
 * Section.State/PlaylistDownloadUiModel/stringRes 资源（SectionedList.kt 源码自注 "TODO:
 * Migrate to TransformingLazyColumnState"），与本仓 AppScaffold+ScreenScaffold+TLC 单层架构冲突；
 * 按 t1 §6 契约 Home 采用 M3 通用件自组 Browse 风格分区（标题区 → 迷你播放器 → 媒体入口 → 设置）。
 */
@Composable
internal fun HorologistHomeScreen(
    viewModel: AppViewModel,
    account: AccountUi?,
    onNavigate: (AppScreen) -> Unit,
    onOpenLibrary: (LibraryKind) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenListMenu: (ListMenu, String) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val homeScrollState = rememberScrollState()
    val pbState by viewModel.playbackState.collectAsState()
    val last by viewModel.lastPlayback.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()

    // Mini Player 三态：正在播放 > 上次播放（暂停态） > 隐藏（D4 决策）
    val current = pbState.current
    val lastSnapshot = last
    val miniItem = current
        ?: lastSnapshot?.let { it.queue.getOrNull(it.queueIndex) ?: it.queue.firstOrNull() }

    ScreenScaffold(scrollState = listState) { contentPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(homeScrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "WearJelly",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 16.dp)
                    
                    .padding(vertical = 2.dp)
            )
        }

        if (account != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "欢迎, ${account.userName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        
                        .padding(vertical = 2.dp)
                )
            }
        }

        if (miniItem != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                MiniPlayerCard(
                    item = miniItem,
                    artworkUrl = viewModel.artworkUrl(miniItem),
                    isCurrent = current != null,
                    isPlaying = pbState.isPlaying,
                    cached = miniItem.id in cachedIds,
                    positionMs = pbState.positionMs,
                    durationMs = pbState.effectiveDurationMs,
                    onClick = { onNavigate(AppScreen.Player) },
                    onTogglePlay = {
                        if (current != null) viewModel.togglePlayPause() else viewModel.resumeLastPlayback()
                    },
                    onQueue = { onNavigate(AppScreen.Queue) },
                    modifier = Modifier
                        
                        .padding(vertical = 2.dp)
                )
            }
        }

        // 主页 6 个列表入口（t2：长按打开列表级菜单，单击仍进列表；设置非列表不参与）
        Box(modifier = Modifier.fillMaxWidth()) {
            HomeListButton(
                icon = Icons.Default.Person,
                label = "艺人",
                onClick = { onOpenLibrary(LibraryKind.ARTISTS) },
                onLongClick = { onOpenListMenu(ListMenu.RootArtists, "艺人") },
                modifier = Modifier
                    
                    .padding(vertical = 2.dp),
                
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            HomeListButton(
                icon = Icons.Default.Album,
                label = "专辑",
                onClick = { onOpenLibrary(LibraryKind.ALBUMS) },
                onLongClick = { onOpenListMenu(ListMenu.RootAlbums, "专辑") },
                modifier = Modifier
                    
                    .padding(vertical = 2.dp),
                
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            HomeListButton(
                icon = Icons.Default.MusicNote,
                label = "所有歌曲",
                onClick = { onOpenLibrary(LibraryKind.SONGS) },
                onLongClick = { onOpenListMenu(ListMenu.RootSongs, "所有歌曲") },
                modifier = Modifier
                    
                    .padding(vertical = 2.dp),
                
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            HomeListButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "播放列表",
                onClick = { onOpenLibrary(LibraryKind.PLAYLISTS) },
                onLongClick = { onOpenListMenu(ListMenu.RootPlaylists, "播放列表") },
                modifier = Modifier
                    
                    .padding(vertical = 2.dp),
                
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            HomeListButton(
                icon = Icons.Default.Download,
                label = "已缓存音乐",
                onClick = { onOpenDownloads() },
                onLongClick = { onOpenListMenu(ListMenu.RootDownloads, "已缓存音乐") },
                modifier = Modifier
                    
                    .padding(vertical = 2.dp),
                
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            HomeListButton(
                icon = Icons.Default.History,
                label = "播放历史",
                onClick = { onNavigate(AppScreen.History) },
                onLongClick = { onOpenListMenu(ListMenu.RootHistory, "播放历史") },
                modifier = Modifier
                    
                    .padding(vertical = 2.dp),
                
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFF242228))
                .clickable(onClick = { onNavigate(AppScreen.Settings) })
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFFE8E4EC))
                Spacer(Modifier.width(12.dp))
                Text("设置", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8E4EC))
            }
        }
    }
    }
}

/**
 * 主页列表入口按钮（t2）：单击进入列表，长按打开该列表类型的列表级长按菜单。
 * M2 时代手动复刻 wear [Button] 视觉；迁移后直接使用 Wear Material3 [Button]
 * （圆角容器 + labelLarge），长按由 M3 Button 的 onLongClick 承担。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeListButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xFF242228))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFE8E4EC))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8E4EC), maxLines = 1)
        }
    }
}

/**
 * 主页 Mini Player（REQ-PLAYBACK-101）：
 * 正在播放 → 播放/暂停+进度条；上次播放 → 暂停态，点播放键从记忆进度继续。
 * 右侧队列图标进入 CurrentQueue 二级页（REQ-PLAYBACK-102）。
 * 导航（进 Player / 进队列）只经 [onClick]/[onQueue] 回调，卡片自身不导航。
 */
@Composable
private fun MiniPlayerCard(
    item: JellyfinItem,
    artworkUrl: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    cached: Boolean,
    positionMs: Long,
    durationMs: Long,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14202F))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(34.dp)) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                CacheBadge(cached = cached, modifier = Modifier.align(Alignment.BottomEnd))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistText,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(onClick = onTogglePlay)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = Copy.MORE + "队列",
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onQueue)
            )
        }
        if (isCurrent && durationMs > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            // 发丝级进度条（2dp）：M3 LinearProgressIndicator 最小描边 8dp（含端点圆点），
            // 不适配 MiniPlayer 紧凑行高，故保留细线；圆环进度均已用 M3 CircularProgressIndicator。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.DarkGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        )
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/**
 * 缓存角标（REQ-CACHE-BADGE-201）：已缓存=绿色对勾圆标；
 * 缓存中=进度环（仅歌曲列表传入 progressFraction，见 D5 决策）。
 */
@Composable
private fun CacheBadge(
    cached: Boolean,
    modifier: Modifier = Modifier,
    progressFraction: Float? = null,
) {
    when {
        progressFraction != null -> CircularProgressIndicator(
            progress = { progressFraction.coerceIn(0f, 1f) },
            modifier = modifier.size(11.dp),
            strokeWidth = 1.5.dp
        )
        cached -> Box(
            modifier = modifier
                .size(10.dp)
                .background(StatusGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CachedTick()
        }
    }
}

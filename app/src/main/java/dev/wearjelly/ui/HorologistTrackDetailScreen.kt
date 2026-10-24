package dev.wearjelly.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.LibraryKind

/**
 * t10 曲目详情（docs/horologist-migration.md §6 映射 #4，M3 · Horologist EntityScreen 风格）：
 * - EntityScreen 风格 = 单一 headerContent（ListHeader 标题区，含艺人副行）→ 操作按钮列表，
 *   按 t1 §6 契约以 M3 [ListHeader] + Button 列表 + [solidButtonColors] 自组；
 *   Horologist `EntityScreen`（media-ui-material3）内部绑定 ScalingLazyColumn 且忽略
 *   ScreenScaffold contentPadding，与本仓 AppScaffold+ScreenScaffold+TLC 单层架构冲突，
 *   故取其风格（headerContent → buttons → content 分段）不取其组件；
 * - 详情字段/操作入口功能零丢失：标题/艺人两字段 + 9 个操作入口——播放、下一首播放、
 *   加入待播队列、[SONGS]以此开始播放全部、[在线]缓存这首（进度条/转圈两态）、
 *   [已缓存]删除这首的缓存、查看信息、分享、[SONGS]从当前列表移除；
 * - 导航类动作只经回调发出（onNavigate/onGoBack），本文件不出现 viewModel.navigate/goBack
 *   调用，由外壳（t15）接线；播放/缓存/分享/移除等非导航动作仍走 [AppViewModel]。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.Track -> HorologistTrackDetailScreen(
 *     viewModel, screen.item, screen.source,
 *     onNavigate = viewModel::navigate,
 *     onGoBack = viewModel::goBack,
 * )
 * ```
 */
@Composable
internal fun HorologistTrackDetailScreen(
    viewModel: AppViewModel,
    item: JellyfinItem,
    source: LibraryQuery,
    onNavigate: (AppScreen) -> Unit,
    onGoBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading = item.id in downloadingIds
    val progress = downloadProgress[item.id]
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val isDownloaded = item.id in cachedIds
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // EntityScreen.headerContent：ListHeader 标题区（名 + 艺人）
        item {
            ListHeader(
                modifier = Modifier.fillMaxWidth()
                    .minimumVerticalContentPadding(
                        ListHeaderDefaults.minimumTopListContentPadding,
                        ListHeaderDefaults.minimumBottomListContentPadding
                    )
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Column {
                    Text(text = item.name)
                    Text(
                        text = item.artistText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.playTrack(item) },
                modifier = Modifier.fillMaxWidth().height(42.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(Copy.PLAY)
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.playItemsNext(listOf(item)); onGoBack() },
                modifier = Modifier.fillMaxWidth().height(42.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(Copy.PLAY_NEXT, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item {
            Button(
                onClick = { viewModel.enqueue(item); onGoBack() },
                modifier = Modifier.fillMaxWidth().height(42.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(Copy.ADD_TO_QUEUE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (source.kind == LibraryKind.SONGS) {
            item {
                Button(
                    onClick = { viewModel.playLoaded(source, item.id) },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(Copy.PLAY_ALL_FROM_HERE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // 离线模式无法联网下载：剔除"缓存这首歌"
        if (!viewModel.isOfflineMode) {
        item {
            Button(
                onClick = { viewModel.downloadTrack(item); onGoBack() },
                enabled = !isDownloading && !isDownloaded,
                modifier = Modifier.fillMaxWidth().height(42.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val fraction = progress?.fraction
                        if (fraction != null) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)).background(Color.DarkGray)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(fraction).height(5.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            val prefix = if (progress.estimated) "约 " else ""
                            val quality = progress.qualityLabel.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
                            Text(
                                text = "${quality}${prefix}${(fraction * 100).toInt()}% · ${formatBytes(progress.downloadedBytes)}" + (if (progress.totalBytes > 0) " / ${formatBytes(progress.totalBytes)}" else ""),
                                style = MaterialTheme.typography.bodyExtraSmall
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            val quality = progress?.qualityLabel?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
                            Text(
                                text = "${quality}已缓存 ${formatBytes(progress?.downloadedBytes ?: 0L)}",
                                style = MaterialTheme.typography.bodyExtraSmall
                            )
                        }
                    }
                } else {
                    Text(Copy.CACHE_THIS)
                }
            }
        }
        }

        if (isDownloaded) {
            item {
                Button(
                    onClick = { viewModel.deleteDownloadedTrack(item.id) },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = solidButtonColors(Color(0xFF4A5568)),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(Copy.DELETE_THIS_CACHE, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Button(
                onClick = { onNavigate(AppScreen.SongInfo(item)) },
                modifier = Modifier.fillMaxWidth().height(42.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(Copy.SONG_INFO, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item {
            Button(
                onClick = { shareItems(context, uiState.account?.serverUrl, listOf(item)); onGoBack() },
                modifier = Modifier.fillMaxWidth().height(42.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(Copy.SHARE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (source.kind == LibraryKind.SONGS) {
            item {
                Button(
                    onClick = {
                        viewModel.removeFromLibraryView(source, setOf(item.id))
                        onGoBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = solidButtonColors(Color(0xFF4A5568)),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(Copy.REMOVE_FROM_LIST, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    }
}

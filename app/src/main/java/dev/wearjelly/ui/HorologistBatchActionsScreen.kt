package dev.wearjelly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * t11 批量操作页（docs/horologist-migration.md §6 映射 #11，M3 通用件 Button 列表）：
 * - Horologist 无菜单组件 → ListHeader 标题区（标题 + 已选数）+ M3 Button 列表，最多 11 项操作；
 *   条件结构与顺序逐项保留：[!ARTISTS] 加入待播队列、[!ARTISTS && !在线] 缓存所选(4A5568)/
 *   下一首播放/删除所选缓存(4A5568)、[QUEUE] 从队列移除(4A5568)、[LIBRARY] 从当前列表移除(4A5568)、
 *   分享、全选、清空选择、反选、返回(36dp)；标题/已选数文案保留；
 * - 反选后为 0 自动退出多选并离开本页的分支逻辑保留（读 selection.active）；
 * - 导航只经回调（本文件不出现 viewModel.navigate/goBack），退出 = onGoBack()；
 *   批量动作/分享仍走 [AppViewModel] 与 [shareItems]，不动 playback/。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.BatchActions -> HorologistBatchActionsScreen(
 *     viewModel, screen, onGoBack = viewModel::goBack,
 * )
 * ```
 */
@Composable
internal fun HorologistBatchActionsScreen(
    viewModel: AppViewModel,
    screen: AppScreen.BatchActions,
    onGoBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloaded by viewModel.downloadedSongs.collectAsState()
    val history by viewModel.history.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val items = when (screen.source) {
        BatchSource.LIBRARY -> uiState.libraries[screen.query]?.items.orEmpty()
        BatchSource.DOWNLOADS -> dev.wearjelly.data.PinyinSort.downloadedBatchItems(downloaded.map { it.item })
        BatchSource.HISTORY -> history.map { it.item }
        BatchSource.QUEUE -> playbackState.queue.map { it.item }
        BatchSource.ARTISTS -> uiState.libraries[screen.query]?.items.orEmpty()
    }
    val selectedItems = items.filter { it.id in uiState.selectedSongIds }
    val context = LocalContext.current

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                    Text(screen.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = Copy.selectedCount(uiState.selectedSongIds.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        if (screen.source != BatchSource.ARTISTS) {
            item {
                Button(
                    onClick = {
                        viewModel.batchSelectedSongsToQueue(items)
                        onGoBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = ButtonDefaults.buttonColors(),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(Copy.ADD_TO_QUEUE, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (screen.source != BatchSource.ARTISTS && !viewModel.isOfflineMode) {
            item {
                Button(
                    onClick = {
                        viewModel.batchSelectedSongsToCache(items)
                        onGoBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = solidButtonColors(Color(0xFF4A5568)),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(Copy.cacheSelected(uiState.selectedSongIds.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.playItemsNext(selectedItems)
                        onGoBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(Copy.PLAY_NEXT, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.deleteCachedTracks(uiState.selectedSongIds)
                        viewModel.exitSelectionMode()
                        onGoBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = solidButtonColors(Color(0xFF4A5568)),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(Copy.DELETE_SELECTED_CACHES, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (screen.source == BatchSource.QUEUE) {
            item {
                Button(
                    onClick = {
                        viewModel.removeSelectedFromQueue(items)
                        onGoBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = solidButtonColors(Color(0xFF4A5568)),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("从队列移除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (screen.source == BatchSource.LIBRARY) {
            item {
                Button(
                    onClick = {
                        viewModel.removeFromLibraryView(screen.query, uiState.selectedSongIds)
                        viewModel.exitSelectionMode()
                        onGoBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    colors = solidButtonColors(Color(0xFF4A5568)),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(Copy.REMOVE_FROM_LIST, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Button(
                onClick = { shareItems(context, uiState.account?.serverUrl, selectedItems) },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(Copy.SHARE, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = { viewModel.selectAllItems(items) },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(Copy.SELECT_ALL, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.exitSelectionMode()
                    onGoBack()
                },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(Copy.SELECT_NONE, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.invertSelectionItems(items)
                    // 反选后为 0：自动退出多选并离开本页
                    if (!viewModel.uiState.value.selection.active) onGoBack()
                },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(Copy.INVERT_SELECTION, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = { onGoBack() },
                modifier = Modifier.fillMaxWidth().height(36.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text("返回", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    }
}

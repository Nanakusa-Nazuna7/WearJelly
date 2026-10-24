package dev.wearjelly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.google.android.horologist.media.ui.material3.composables.Section
import com.google.android.horologist.media.ui.material3.composables.SectionedList
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.LibraryKind

/**
 * t6 播放队列（docs/horologist-migration.md §6 映射 #6，Horologist）：
 * - 结构按契约换 [SectionedList] + [ScalingLazyListState]（Horologist PlaylistsScreen 范式：
 *   rememberScalingLazyListState → ScreenScaffold(scrollState) → SectionedList），分组为
 *   “已播放 / 正在播放 / 接下来”（契约要求的“正在播放/接下来”为主体，“已播放”是零丢失补充：
 *   原平铺列表从 0 号起展示全部队列，不补该段则 currentIndex 之前的曲目会从界面上消失）；
 *   各分组按 M3 [ListHeader] 出表头，序号 "${index+1}." 仍是**全局队列序号**。
 * - 功能零丢失（对位原 QueueScreen WearJellyApp.kt:2507-2641）：标题“播放队列”、清空队列确认
 *   （0xFF4A1515）、空态“队列为空”、当前曲目序号高亮（primary/outline）、点按切换（多选态
 *   改为勾选）、长按进入/切换多选（t30：原“长按打开曲目详情”入口被多选入口取代，与其余
 *   可多选列表一致）、左滑起多选/多选中左滑选段、行尾移除（trailingAction）、
 *   底部 56dp 多选避让 spacer、多选栏 MultiSelectBar（退出/已选 n 首/更多→BatchActions QUEUE）。
 * - 导航类动作只经回调发出（onNavigate），本文件不出现
 *   viewModel.navigate/requestConfirmation 调用（清空确认=onNavigate(Confirm)、
 *   更多=onNavigate(BatchActions)，与 requestConfirmation/navigate 的内部构造完全一致），
 *   由外壳（t15）接线；播放/移除/多选等非导航动作仍走 [AppViewModel]。
 * - 私有助手（MultiSelectBar）在 WearJellyApp.kt 中是 private 且本任务不可改该文件，故同名
 *   私有副本随本文件落地；原 rememberSongListAnchor 是 TLC 版（绑定 TransformingLazyColumnState
 *   的 centerItem），ScalingLazyListState 锚点语义为 centerItemIndex/ScrollOffset（正值=锚点在
 *   中心线上方，scrollToItem 即拉到中心），故锚点助手按新语义重写；internal 共用件
 *   （LibraryItemRow/solidButtonColors/Copy/resolveSongListAnchorIndex）直接复用。
 * - 与原实现的物理差异（不属功能项）：行距与首尾 contentPadding 交由 SectionedList 内部
 *   ScalingLazyColumn / ScreenScaffold 框架默认（原 TLC 的 spacedBy(6.dp) 与手动
 *   contentPadding、M3 transformation/transformedHeight 不再逐行传入）。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.Queue -> HorologistQueueScreen(
 *     viewModel,
 *     onNavigate = viewModel::navigate,
 * )
 * ```
 */
@Composable
internal fun HorologistQueueScreen(
    viewModel: AppViewModel,
    onNavigate: (AppScreen) -> Unit,
) {
    val pbState by viewModel.playbackState.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedIds = uiState.selectedSongIds
    val selectionMode = uiState.selectionMode
    val queueItems = remember(pbState.queue) { pbState.queue.map { it.item } }
    val sections = buildQueueSections(pbState.queue.size, pbState.currentIndex)
    val scrollState = rememberScalingLazyListState()
    rememberQueueListAnchor(
        scrollState,
        queueItems,
        sections,
        initialIndex = pbState.currentIndex.coerceAtLeast(0),
    )

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(scrollState = scrollState) {
            SectionedList(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                scrollState = scrollState,
            ) {
                // 标题 + 清空队列（原实现 headerItems = 2，锚点平铺偏移同为 2）
                section {
                    header {
                        Text(
                            text = "播放队列",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    loaded {
                        Button(
                            onClick = { onNavigate(AppScreen.Confirm(ConfirmAction.CLEAR_QUEUE)) },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(36.dp),
                            colors = solidButtonColors(Color(0xFF4A1515)),
                        ) {
                            Text(
                                "清空队列",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }

                if (queueItems.isEmpty()) {
                    section(state = Section.State.Empty) {
                        empty {
                            Text(
                                "队列为空",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                } else {
                    sections.forEach { group ->
                        section(list = group.range.toList()) {
                            if (group.title != null) {
                                header {
                                    ListHeader(modifier = Modifier.padding(bottom = 12.dp)) {
                                        Text(group.title)
                                    }
                                }
                            }
                            loaded { index ->
                                val item = pbState.queue[index].item
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (index == pbState.currentIndex) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        modifier = Modifier.width(24.dp),
                                    )
                                    LibraryItemRow(
                                        item = item,
                                        query = LibraryQuery(LibraryKind.SONGS),
                                        selected = item.id in selectedIds,
                                        selectionMode = selectionMode,
                                        isCurrent = item.id == pbState.current?.id,
                                        isPlaying = pbState.isPlaying,
                                        cached = item.id in cachedIds,
                                        offline = viewModel.isOfflineMode,
                                        trailingAction = { viewModel.removeQueueItem(index, item.id) },
                                        onClick = {
                                            if (selectionMode) viewModel.toggleSongSelection(item.id)
                                            else viewModel.playQueueIndex(index, item.id)
                                        },
                                        onLongClick = {
                                            if (selectionMode) viewModel.toggleSongSelection(item.id)
                                            else viewModel.beginSelectionFromSwipe(item.id)
                                        },
                                        onSwipeLeft = {
                                            if (selectionMode) {
                                                viewModel.selectRangeFromSwipe(
                                                    item.id,
                                                    pbState.queue.map { it.item.id },
                                                )
                                            } else {
                                                viewModel.beginSelectionFromSwipe(item.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectionMode && pbState.queue.isNotEmpty()) {
                    section { loaded { Spacer(Modifier.height(56.dp)) } }
                }
            }
        }

        if (selectionMode) {
            MultiSelectBar(
                selectedCount = selectedIds.size,
                onExit = { viewModel.exitSelectionMode() },
                onMore = {
                    onNavigate(
                        AppScreen.BatchActions(
                            LibraryQuery(LibraryKind.SONGS),
                            "播放队列",
                            BatchSource.QUEUE,
                        )
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 队列分组段：[range] 为全局队列下标（闭开区间 IntRange，天然含首下标）。 */
private data class QueueSection(val title: String?, val range: IntRange)

/** 标题 + 清空按钮在 SectionedList 中的固定前置条目数（对位原 headerItems = 2）。 */
private const val QUEUE_HEADER_ITEM_COUNT = 2

/**
 * 把队列切成 “已播放 / 正在播放 / 接下来” 段（契约 §6 #6 的分组主体）：
 * - currentIndex 越界（未播放，PlaybackState.currentIndex 默认 -1）→ 单段无表头，等价原平铺；
 * - currentIndex == 0 无“已播放”，currentIndex 到尾无“接下来”；各段非空。
 */
private fun buildQueueSections(queueSize: Int, currentIndex: Int): List<QueueSection> {
    if (queueSize <= 0) return emptyList()
    if (currentIndex !in 0 until queueSize) {
        return listOf(QueueSection(title = null, range = 0 until queueSize))
    }
    return buildList {
        if (currentIndex > 0) add(QueueSection("已播放", 0 until currentIndex))
        add(QueueSection("正在播放", currentIndex..currentIndex))
        if (currentIndex < queueSize - 1) {
            add(QueueSection("接下来", (currentIndex + 1) until queueSize))
        }
    }
}

/** 队列下标 → SectionedList 平铺条目下标（前缀 2 个标题/清空条目 + 各段表头）。 */
private fun flatIndexOfQueueIndex(sections: List<QueueSection>, queueIndex: Int): Int? {
    var flat = QUEUE_HEADER_ITEM_COUNT
    sections.forEach { group ->
        val headerCount = if (group.title != null) 1 else 0
        if (queueIndex in group.range) return flat + headerCount + (queueIndex - group.range.first)
        flat += headerCount + group.range.count()
    }
    return null
}

/** SectionedList 平铺条目下标 → 队列下标；落在标题/清空/分组表头/尾部 spacer 上时返回 null。 */
private fun queueIndexAtFlatIndex(sections: List<QueueSection>, flatIndex: Int): Int? {
    var rest = flatIndex - QUEUE_HEADER_ITEM_COUNT
    if (rest < 0) return null
    sections.forEach { group ->
        val headerCount = if (group.title != null) 1 else 0
        if (rest < headerCount) return null
        rest -= headerCount
        val count = group.range.count()
        if (rest < count) return group.range.first + rest
        rest -= count
    }
    return null
}

/**
 * 队列锚点（ScalingLazyListState 版，对位原 WearJellyApp.kt:1067 rememberSongListAnchor）：
 * - 进入/队列变化时按 “锚点 id → 保存索引 → 初始索引（当前曲目）” 解析目标（复用 internal
 *   resolveSongListAnchorIndex），换算平铺下标后 scrollToItem 拉到视口中心并带上次偏移；
 * - 持续回存中心项（centerItemIndex/ScrollOffset 同值往返），移除条目后不漂移。
 */
@Composable
private fun rememberQueueListAnchor(
    scrollState: ScalingLazyListState,
    queueItems: List<JellyfinItem>,
    sections: List<QueueSection>,
    initialIndex: Int,
) {
    var anchorId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorIndex by rememberSaveable { mutableStateOf(0) }
    var anchorOffset by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(scrollState, queueItems, sections, initialIndex) {
        if (queueItems.isEmpty()) return@LaunchedEffect
        val index = resolveSongListAnchorIndex(anchorId, anchorIndex, initialIndex, queueItems)
        val flat = flatIndexOfQueueIndex(sections, index) ?: QUEUE_HEADER_ITEM_COUNT
        scrollState.scrollToItem(flat, anchorOffset)
    }
    LaunchedEffect(scrollState, queueItems, sections) {
        snapshotFlow { scrollState.centerItemIndex to scrollState.centerItemScrollOffset }
            .collect { (flat, offset) ->
                val index = queueIndexAtFlatIndex(sections, flat)
                if (index != null && index in queueItems.indices) {
                    anchorIndex = index
                    anchorId = queueItems[index].id
                    anchorOffset = offset
                }
            }
    }
}

/** 多选底栏（原 WearJellyApp.kt:1601 private 同名副本，本任务不可改原文件）。 */
@Composable
private fun MultiSelectBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(bottom = 8.dp)
            // 圆形表盘底部可视宽度收窄，避免两端文字被圆边裁剪（MT-005 真机反馈）
            .fillMaxWidth(0.74f)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xCC14202F))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Button(
            onClick = onExit,
            modifier = Modifier.height(30.dp),
            colors = solidButtonColors(Color(0xFF4A5568))
        ) {
            Text(Copy.EXIT_SELECTION, style = MaterialTheme.typography.bodyExtraSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        }
        Text(
            text = Copy.selectedCount(selectedCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onMore,
            modifier = Modifier.height(30.dp),
            colors = solidButtonColors(Color(0xFF22304A))
        ) {
            Text(Copy.MORE, style = MaterialTheme.typography.bodyExtraSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        }
    }
}

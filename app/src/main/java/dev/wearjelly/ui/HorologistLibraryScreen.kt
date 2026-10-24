package dev.wearjelly.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.LibraryKind
import java.lang.Math.cos
import java.lang.Math.sin
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * t8 媒体库（docs/horologist-migration.md §6 映射 #3，M3+自研）：
 * - 结构保持 TransformingLazyColumn + AlphabetListScaffold（WearOS 6 字母环）+ LibraryItemRow
 *   （combinedClickable + leftSwipeGesture，internal 复用不复制）；
 * - 状态、加载/错误/分页/多选/离线/缓存进度全部语义与原 LibraryScreen 一致（功能零丢失）；
 *   t30 修复：可多选行（歌曲/艺人/专辑）长按 = 进入/切换多选（原单曲详情/实体菜单长按入口
 *   被多选入口取代，"更多"→BatchActions 经 onNavigate），播放列表行（不可多选）保留原菜单；
 * - 导航类动作只经回调发出（onNavigate/onOpenItem/onOpenTrack/onOpenListMenu），本文件不出现
 *   viewModel.navigate/openItem/openTrack/openListMenu 调用，由外壳（t15）接线；播放/多选/分页/
 *   重试等非导航动作仍走 [AppViewModel]。
 * - 私有助手（AlphabetListScaffold/MultiSelectBar/rememberSongListAnchor/centerItem）在
 *   WearJellyApp.kt 中是 private 且本任务不可改该文件，故同名私有副本随本文件落地；
 *   internal 共用件（LibraryItemRow/resolveSongListAnchorIndex/solidButtonColors/Copy/
 *   LIST_END_PADDING_DP/INDEX_STRIP_WIDTH_DP）直接复用。
 *
 * 说明：Horologist media-ui-material3 的 BrowseScreen/SectionedList 绑定 ScalingLazyColumn +
 * Section.State + PlaylistDownloadUiModel + stringRes（SectionedList.kt 源码自注 TODO 迁 TLC），
 * 与本仓 AppScaffold+ScreenScaffold+TLC 单层架构冲突；按 t1 §6 契约，Library 保持 TLC 自组分区
 * （标题 → 加载/错误 → 列表行 → 底部避让），Browse 风格体现在分区结构而非直接调 BrowseScreen。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.Library -> HorologistLibraryScreen(
 *     viewModel, screen,
 *     onNavigate = viewModel::navigate,
 *     onOpenItem = viewModel::openItem,
 *     onOpenTrack = viewModel::openTrack,
 *     onOpenListMenu = viewModel::openListMenu,
 * )
 * ```
 */
@Composable
internal fun HorologistLibraryScreen(
    viewModel: AppViewModel,
    screen: AppScreen.Library,
    onNavigate: (AppScreen) -> Unit,
    onOpenItem: (LibraryQuery, JellyfinItem) -> Unit,
    onOpenTrack: (JellyfinItem, LibraryQuery) -> Unit,
    onOpenListMenu: (ListMenu, String) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val uiState by viewModel.uiState.collectAsState()
    val lib = uiState.libraries[screen.query] ?: LibraryUi()
    val selectedIds = uiState.selectedSongIds
    val selectionMode = uiState.selectionMode
    val pbState by viewModel.playbackState.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val offlineCachedEntities by viewModel.offlineCachedEntityIds.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isSongsScreen = screen.query.kind == LibraryKind.SONGS
    val isSelectable = isSongsScreen || screen.query.kind == LibraryKind.ARTISTS || screen.query.kind == LibraryKind.ALBUMS
    // t2：详情页顶部"更多"已移除（能力迁移到上一级长按菜单），头项仅剩标题
    val headerItems = 1
    rememberSongListAnchor(listState, lib.items, headerItems)

    ScreenScaffold(scrollState = listState) { contentPadding ->
    AlphabetListScaffold(
        items = lib.items,
        listState = listState,
        headerItems = headerItems,
        overlay = {
            if (selectionMode && isSelectable) {
                MultiSelectBar(
                    selectedCount = selectedIds.size,
                    onExit = { viewModel.exitSelectionMode() },
                    onMore = {
                        onNavigate(
                            AppScreen.BatchActions(screen.query, screen.title, if (screen.query.kind == LibraryKind.ARTISTS) BatchSource.ARTISTS else BatchSource.LIBRARY)
                        )
                    }
                )
            }
        }
    ) { scaleModifier ->
        TransformingLazyColumn(
            contentPadding = contentPadding,
                modifier = scaleModifier.fillMaxSize().padding(start = 14.dp, end = LIST_END_PADDING_DP.dp),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = screen.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                        .transformedHeight(this, transformationSpec)
                )
            }

            if (lib.loading && lib.items.isEmpty()) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(16.dp)
                            .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                            .transformedHeight(this, transformationSpec)
                    )
                }
            } else if (lib.error != null && lib.items.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Text(text = lib.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.retryLibrary(screen.query) }) {
                            Text("重试")
                        }
                    }
                }
            } else {
                itemsIndexed(lib.items) { index, item ->
                    LibraryItemRow(
                        item = item,
                        query = screen.query,
                        modifier = Modifier
                            .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                            .transformedHeight(this, transformationSpec),
                         selected = item.id in selectedIds,
                         selectionMode = selectionMode && isSelectable,
                        isCurrent = pbState.current?.id == item.id,
                        isPlaying = pbState.isPlaying,
                        cached = item.id in cachedIds || item.id in offlineCachedEntities,
                         offline = viewModel.isOfflineMode,
                        downloadProgressFraction =
                            if (item.id in downloadingIds) downloadProgress[item.id]?.fraction else null,
                        onClick = {
                            when {
                                selectionMode && isSelectable -> viewModel.toggleSongSelection(item.id)
                                screen.query.kind == LibraryKind.SONGS ||
                                    screen.query.kind == LibraryKind.DOWNLOADS -> onOpenTrack(item, screen.query)
                                else -> onOpenItem(screen.query, item)
                            }
                        },
                        onLongClick = {
                            if (isSelectable) {
                                // t30：可多选行（歌曲/艺人/专辑）长按 = 进入/切换多选，与左滑入口
                                // 一致且稳定可复现；原长按菜单/单曲详情入口由多选入口取代。
                                if (selectionMode) viewModel.toggleSongSelection(item.id)
                                else viewModel.beginSelectionFromSwipe(item.id)
                            } else {
                                // 不可多选行（播放列表）保留原长按列表级菜单（作用域=行条目）；
                                // 全 kind 分支原样保留，防 isSelectable 口径日后变化时菜单回归丢失
                                when (screen.query.kind) {
                                    LibraryKind.SONGS, LibraryKind.DOWNLOADS ->
                                        onOpenTrack(item, screen.query)
                                    LibraryKind.ARTISTS -> onOpenListMenu(
                                        ListMenu.Entity(
                                            screen.query.copy(kind = LibraryKind.SONGS, artistId = item.id)
                                        ),
                                        item.name,
                                    )
                                    LibraryKind.ALBUMS -> onOpenListMenu(
                                        ListMenu.Entity(
                                            screen.query.copy(kind = LibraryKind.SONGS, parentId = item.id)
                                        ),
                                        item.name,
                                    )
                                    LibraryKind.PLAYLISTS -> onOpenListMenu(
                                        ListMenu.Entity(
                                            screen.query.copy(kind = LibraryKind.SONGS, playlistId = item.id)
                                        ),
                                        item.name,
                                    )
                                }
                            }
                        },
                        onSwipeLeft = {
                            if (selectionMode && isSelectable) {
                                // MT-002：范围选择
                                viewModel.selectRangeFromSwipe(item.id, lib.items.map { it.id })
                            } else if (isSelectable) {
                                viewModel.beginSelectionFromSwipe(item.id)
                            }
                        },
                    )

                    if (index == lib.items.lastIndex && !lib.endReached && !lib.loading) {
                        viewModel.loadMore(screen.query)
                    }
                }

                if (selectionMode && isSelectable) {
                    // 底部固定操作栏的避让空间
                    item {
                        Spacer(
                            Modifier
                                .height(56.dp)
                                .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }

                if (lib.loading) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp)
                                .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * M2 ScalingLazyListState 的 centerItemIndex/centerItemScrollOffset 在 TransformingLazyColumnState
 * 上不存在：改从 layoutInfo.visibleItems 计算「离视口中心最近的条目」及其中心线偏移（像素）。
 * 偏移语义与 TransformingLazyColumnState.scrollToItem(index, scrollOffset) 对齐：
 * 条目中心位于视口中心线下方为正（正偏移表示需要把条目向上滚），存取往返一致。
 * （WearJellyApp.kt 中同名扩展为 private，此处为本文件私有副本。）
 */
private fun TransformingLazyColumnState.centerItem(): Pair<Int, Int> {
    val info = layoutInfo
    val viewportCenter = info.viewportSize.height / 2
    val nearest = info.visibleItems.minByOrNull { abs(it.offset + it.transformedHeight / 2 - viewportCenter) }
    return if (nearest == null) {
        anchorItemIndex to anchorItemScrollOffset
    } else {
        nearest.index to (nearest.offset + nearest.transformedHeight / 2 - viewportCenter)
    }
}

@Composable
private fun rememberSongListAnchor(
    listState: TransformingLazyColumnState,
    items: List<JellyfinItem>,
    headerItems: Int,
    initialIndex: Int = 0,
) {
    var anchorId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorIndex by rememberSaveable { mutableStateOf(0) }
    var anchorOffset by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(listState, items, headerItems, initialIndex) {
        if (items.isEmpty()) return@LaunchedEffect
        val index = resolveSongListAnchorIndex(anchorId, anchorIndex, initialIndex, items)
        listState.scrollToItem(headerItems + index, anchorOffset)
    }
    LaunchedEffect(listState, items, headerItems) {
        snapshotFlow {
            val (centerIndex, centerOffset) = listState.centerItem()
            (centerIndex - headerItems) to centerOffset
        }.collect { (index, offset) ->
            if (index in items.indices) {
                anchorIndex = index
                anchorId = items[index].id
                anchorOffset = offset
            }
        }
    }
}

/**
 * 歌曲列表通用脚手架（WearOS 6 风格字母滚动条）：
 * - 平时右缘只显示 1/4 屏高的细轨道 + 位置指示块（随列表滚动移动），列表完全居中
 * - 手指贴边竖滑/点按时展开固定 A-Z# 弧形字母环（贴边、直立字母），列表缩放下沉
 * - 手势判定：竖向意图(│dy│≥0.8│dx│)进入选择并整段消费；横向意图立即放行给返回
 * （WearJellyApp.kt 中同名组件为 private，此处为本文件私有副本，逻辑逐行一致。）
 */
@Composable
private fun AlphabetListScaffold(
    items: List<JellyfinItem>,
    listState: TransformingLazyColumnState,
    headerItems: Int,
    showIndex: Boolean = true,
    overlay: (@Composable () -> Unit)? = null,
    list: @Composable (Modifier) -> Unit
) {
    var activeLetterIdx by remember { mutableStateOf(-1) }
    var scrubbing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isRound = LocalContext.current.resources.configuration.isScreenRound
    val listScale by animateFloatAsState(
        targetValue = if (scrubbing) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)
    )

    // 字母分组首索引：letter -> items 中第一个该字母条目的下标
    val groupFirst = remember(items) {
        val map = linkedMapOf<String, Int>()
        items.forEachIndexed { index, item ->
            val bucket = dev.wearjelly.data.PinyinSort.letterBucket(item.name)
            if (!map.containsKey(bucket)) map[bucket] = index
        }
        map
    }

    Box(Modifier.fillMaxSize()) {
        list(Modifier.graphicsLayer {
            scaleX = listScale
            scaleY = listScale
        })

        overlay?.let { content ->
            // 固定底部操作栏：不随列表滚动，不参与字母环缩放
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(4f)
            ) {
                content()
            }
        }

        if (showIndex && items.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                val cx = widthPx / 2f
                val cy = heightPx / 2f
                val densityPx = with(density) { 1.dp.toPx() }
                val radius = min(widthPx, heightPx) / 2f - 3f * densityPx
                val maxSpanRad = Math.toRadians(112.0)
                val spanRad = min(maxSpanRad, (16f * densityPx * (RING_LETTERS.length - 1)) / radius.coerceAtLeast(1f).toDouble())
                val spanDeg = Math.toDegrees(spanRad).toFloat()

                fun ringY(i: Int): Float {
                    val angle = -spanDeg / 2f + spanDeg * (i.toFloat() / (RING_LETTERS.length - 1))
                    return cy + radius * sin(Math.toRadians(angle.toDouble())).toFloat()
                }

                fun jumpToLetter(letterIdx: Int) {
                    val clamped = letterIdx.coerceIn(0, RING_LETTERS.length - 1)
                    activeLetterIdx = clamped
                    var target = -1
                    // 从该字母开始向后找第一个存在的分组；都不存在则跳到 #
                    for (i in clamped until RING_LETTERS.length) {
                        val first = groupFirst[RING_LETTERS[i].toString()]
                        if (first != null) {
                            target = first
                            break
                        }
                    }
                    if (target < 0) {
                        groupFirst["#"]?.let { target = it }
                    }
                    if (target >= 0) {
                        scope.launch { listState.scrollToItem(headerItems + target) }
                    }
                }

                val centerItemIdx = listState.centerItem().first - headerItems
                val scrollLetterIdx = items.getOrNull(centerItemIdx)?.let {
                    dev.wearjelly.data.PinyinSort.LETTERS.indexOf(
                        dev.wearjelly.data.PinyinSort.letterBucket(it.name)
                    )
                } ?: -1
                val shownLetterIdx = if (scrubbing) activeLetterIdx else scrollLetterIdx
                // Active window shows the selected letter and up to three neighbors on either side.
                val ringSpanF by animateFloatAsState(
                    if (scrubbing) 3f else 1f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)
                )
                val ringSpan = ringSpanF.toInt()
                if (shownLetterIdx >= 0) {
                    val stepDeg = if (scrubbing) 10f else 14f
                    for (role in -3..3) {
                        if (abs(role) > ringSpanF.toInt()) continue
                        val letterIdx = shownLetterIdx + role
                        if (letterIdx !in RING_LETTERS.indices) continue
                        val letter = RING_LETTERS[letterIdx].toString()
                        val isCenter = role == 0
                        val targetSize = when {
                            scrubbing && isCenter -> 22f
                            scrubbing && abs(role) == 1 -> 15f
                            scrubbing -> 12f
                            isCenter -> 17f
                            else -> 12f
                        }
                        val fontSize by animateFloatAsState(
                            targetSize,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)
                        )
                        val alpha by animateFloatAsState(
                            when {
                                isCenter -> 1f
                                scrubbing && abs(role) == 2 -> 0.45f
                                else -> 0.6f
                            },
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)
                        )
                        val theta = Math.toRadians((role * stepDeg).toDouble())
                        val arcY = cy + radius * sin(theta).toFloat()
                        val arcX = cx + radius * cos(theta).toFloat()
                        val boxW = 26f * densityPx
                        val boxH = 32f * densityPx
                        // 盒子右缘贴住弧线内侧，保证字形完整显示
                        val anchorX = arcX - boxW - 1f * densityPx
                        val anchorY = arcY - boxH / 2f
                        Box(
                            modifier = Modifier
                                .zIndex(2f)
                                .offset { IntOffset(anchorX.roundToInt(), anchorY.roundToInt()) }
                                .width(with(density) { boxW.toDp() })
                                .height(with(density) { boxH.toDp() }),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = letter,
                                transitionSpec = {
                                    val up = targetState > initialState
                                    (slideInVertically { h -> if (up) h / 2 else -h / 2 } + fadeIn()) togetherWith
                                        (slideOutVertically { h -> if (up) -h / 2 else h / 2 } + fadeOut())
                                },
                                label = "letter$role"
                            ) { l ->
                                Text(
                                    text = l,
                                    fontSize = fontSize.sp,
                                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCenter) MaterialTheme.colorScheme.onBackground else Color(0xFF9DB8E8),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.graphicsLayer { this.alpha = alpha }
                                )
                            }
                        }
                    }
                }

                // 手势层：仅右缘窄条；竖向意图整段消费（允许手指弧线漂移），横向意图立即放行
                val stripWidthDp = INDEX_STRIP_WIDTH_DP.dp
                val ringTop = ringY(0)
                val ringBottom = ringY(RING_LETTERS.length - 1)
                fun nearestLetterIdx(y: Float): Int {
                    var best = 0
                    var bestDist = Float.MAX_VALUE
                    for (i in RING_LETTERS.indices) {
                        val d = abs(y - ringY(i))
                        if (d < bestDist) { bestDist = d; best = i }
                    }
                    return best
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(stripWidthDp)
                        .zIndex(3f)
                        .pointerInput(items, isRound) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var moved = false
                                var released = false
                                var horizontal = false
                                var movedY = down.position.y
                                val hold = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) {
                                            released = true
                                            return@withTimeoutOrNull false
                                        }
                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y
                                        if (dx * dx + dy * dy > viewConfiguration.touchSlop * viewConfiguration.touchSlop) {
                                            moved = true
                                            movedY = change.position.y
                                            horizontal = abs(dx) > abs(dy) / 0.8f
                                            return@withTimeoutOrNull false
                                        }
                                    }
                                    false
                                }

                                if (hold == null) {
                                    scrubbing = true
                                    var selected = nearestLetterIdx(down.position.y)
                                    activeLetterIdx = selected
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.pressed) {
                                            selected = nearestLetterIdx(change.position.y)
                                            activeLetterIdx = selected
                                            change.consume()
                                        } else {
                                            break
                                        }
                                    }
                                    jumpToLetter(selected)
                                } else if (released && !moved) {
                                    jumpToLetter(nearestLetterIdx(down.position.y))
                                } else if (moved && !horizontal) {
                                    scrubbing = true
                                    var selected = nearestLetterIdx(movedY)
                                    jumpToLetter(selected)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) break
                                        selected = nearestLetterIdx(change.position.y)
                                        change.consume()
                                        jumpToLetter(selected)
                                    }
                                }
                                scrubbing = false
                            }
                        }
                )
            }
        }
    }
}

private const val RING_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"

/**
 * 多选模式固定底部操作栏（单行）：退出多选 / 已选 N 首 / 更多。
 * 批量操作收进"更多"二级页（BatchActionsScreen）。
 * （WearJellyApp.kt 中同名组件为 private，此处为本文件私有副本。）
 */
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

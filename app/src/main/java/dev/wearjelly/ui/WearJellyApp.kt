package dev.wearjelly.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.Typography
import androidx.wear.compose.material3.contentColorFor
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.LibraryKind
import kotlin.math.abs

private val WearThemeColors = ColorScheme(
    primary = Color(0xFF8CB4FF),
    primaryDim = Color(0xFF5B8DEF),
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF000000),
    surfaceContainer = Color(0xFF1E1E1E),
    onPrimary = Color(0xFF001A40),
    onSecondary = Color(0xFF001A40),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFE0E0E0),
    // 次级文本三档（原散落的灰色字面量收口到主题：轮廓 / 次级提示 / 浅灰内容）
    outline = Color.Gray,
    outlineVariant = Color(0xFF8A93A5),
    onSurfaceVariant = Color.LightGray,
    onError = Color(0xFFFFFFFF),
    error = Color(0xFFFF6B6B)
)

/** 业务状态强调色（M3 ColorScheme 无 success 位）：已缓存绿，缓存失败红。 */
internal val StatusGreen = Color(0xFF7BD88F)
internal val StatusRed = Color(0xFFE05A5A)

/**
 * 统一排版（t4：一个 MaterialTheme 只出一套 typography）。
 * 尺寸对齐迁移前 Wear M2 视觉，避免默认 M3 token 漂移：
 * titleSmall=原 title3 16sp、labelLarge=原 button 15sp、labelMedium=原 caption1 14sp、
 * labelSmall=原 caption2 12sp、bodyLarge=原 body1 16sp、bodyMedium=原 body2 14sp、
 * bodyExtraSmall=原 caption3 10sp（吸收 9/10sp 字面量），其余走 M3 默认。
 */
private val WearThemeTypography = Typography(
    titleSmall = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 19.sp,
        letterSpacing = 0.38.sp
    ),
    labelMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        letterSpacing = 0.18.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    bodyExtraSmall = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp,
        letterSpacing = 0.1.sp
    )
)

/**
 * M2 `solidButtonColors(X)` 的 M3 等价：
 * containerColor 对应 backgroundColor，contentColor 按官方 contentColorFor 配对
 * （与 M2 的默认 contentColor = contentColorFor(backgroundColor) 语义一致）。
 */
@Composable
internal fun solidButtonColors(backgroundColor: Color): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = backgroundColor,
        contentColor = contentColorFor(backgroundColor)
    )

@Composable
fun WearJellyApp(viewModel: AppViewModel) {
    MaterialTheme(
        colorScheme = WearThemeColors,
        typography = WearThemeTypography,
        motionScheme = MotionScheme.standard()
    ) {
        val uiState by viewModel.uiState.collectAsState()
        val stateHolder = rememberSaveableStateHolder()
        val isRootScreen = uiState.backStack.size <= 1
        BackHandler(enabled = !isRootScreen) {
            viewModel.goBack()
        }
        // 多选模式下返回键优先退出多选，不返回上一页（后注册的 enabled BackHandler 优先生效）；
        // 仅在列表页拦截，二级操作页仍走正常返回导航
        BackHandler(
            enabled = uiState.selectionMode && (
                uiState.screen is AppScreen.Library ||
                    uiState.screen == AppScreen.Downloads ||
                    uiState.screen == AppScreen.History ||
                    uiState.screen == AppScreen.Queue
                )
        ) {
            viewModel.exitSelectionMode()
        }

        AppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            timeText = {}
        ) {
            // t27/F5：音量面板开关提升到 SwipeToDismissBox 之外（原先在 per-screen 状态里：
            // 面板开着时左缘滑动仍触发整页退出，且残留导致返回播放页面板自动重开）
            var showVolume by rememberSaveable { mutableStateOf(false) }

            val screenContent: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // 页面转场：Player↔Lyrics 左右滑动呼应（左滑进歌词页从右滑入，不再闪现），其余快速淡入淡出
                    AnimatedContent(
                        targetState = uiState.screen,
                        transitionSpec = {
                            val pushToLyrics = initialState is AppScreen.Player && targetState is AppScreen.Lyrics
                            val backToPlayer = initialState is AppScreen.Lyrics && targetState is AppScreen.Player
                            when {
                                pushToLyrics ->
                                    (slideInHorizontally { it } + fadeIn(tween(340, easing = FastOutSlowInEasing))) togetherWith
                                        (slideOutHorizontally { -it / 3 } + fadeOut(tween(340, easing = FastOutSlowInEasing)))
                                backToPlayer ->
                                    (slideInHorizontally { -it / 3 } + fadeIn(tween(340, easing = FastOutSlowInEasing))) togetherWith
                                        (slideOutHorizontally { it } + fadeOut(tween(340, easing = FastOutSlowInEasing)))
                                else -> fadeIn(tween(340, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(160, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)))
                            }
                        },
                        label = "screen"
                    ) { screen ->
                        stateHolder.SaveableStateProvider(screen.toString()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                // t5 音量面板全屏覆盖层：Player 页音量按钮开，返回手势关
                                when (screen) {
                                    is AppScreen.Login -> HorologistLoginScreen(viewModel, uiState.login)
                                    is AppScreen.Home -> HorologistHomeScreen(
                                        viewModel, uiState.account,
                                        onNavigate = viewModel::navigate,
                                        onOpenLibrary = viewModel::openLibrary,
                                        onOpenDownloads = viewModel::openDownloads,
                                        onOpenListMenu = viewModel::openListMenu,
                                    )
                                    is AppScreen.Library -> HorologistLibraryScreen(
                                        viewModel, screen,
                                        onNavigate = viewModel::navigate,
                                        onOpenItem = viewModel::openItem,
                                        onOpenTrack = viewModel::openTrack,
                                        onOpenListMenu = viewModel::openListMenu,
                                    )
                                    is AppScreen.Track -> HorologistTrackDetailScreen(
                                        viewModel, screen.item, screen.source,
                                        onNavigate = viewModel::navigate,
                                        onGoBack = viewModel::goBack,
                                    )
                                    is AppScreen.Player -> HorologistPlayerScreen(
                                        viewModel,
                                        onShowQueue = { viewModel.navigate(AppScreen.Queue) },
                                        onShowLyrics = { viewModel.navigate(AppScreen.Lyrics) },
                                        onVolumeClick = { showVolume = true },
                                    )
                                    is AppScreen.Queue -> HorologistQueueScreen(
                                        viewModel,
                                        onNavigate = viewModel::navigate,
                                    )
                                    is AppScreen.Lyrics -> LyricsPage(viewModel)
                                    is AppScreen.LyricsSettings -> LyricsSettingsScreen(viewModel)
                                    is AppScreen.Downloads -> HorologistDownloadsScreen(viewModel, onNavigate = viewModel::navigate)
                                    is AppScreen.History -> HorologistHistoryScreen(
                                        viewModel,
                                        onNavigate = viewModel::navigate,
                                    )
                                    is AppScreen.BatchActions -> HorologistBatchActionsScreen(viewModel, screen, onGoBack = viewModel::goBack)
                                    is AppScreen.ListActions -> HorologistListActionsScreen(viewModel, screen, onGoBack = viewModel::goBack)
                                    is AppScreen.SongInfo -> HorologistSongInfoScreen(screen.item)
                                    is AppScreen.Settings -> HorologistSettingsScreen(viewModel, uiState.account)
                                    is AppScreen.Confirm -> HorologistConfirmScreen(
                                        viewModel, screen.action, screen.query, screen.menu,
                                        onGoBack = viewModel::goBack,
                                    )
                                }
                                // 音量面板：覆盖在屏内容之上（Player 页 onVolumeClick 打开）
                                VolumeOverlay(
                                    viewModel = viewModel,
                                    visible = showVolume,
                                    onDismissRequest = { showVolume = false },
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isRootScreen) {
                    screenContent()
                } else {
                    SwipeToDismissBox(
                        onDismissed = { viewModel.goBack() },
                        userSwipeEnabled = !showVolume,
                        modifier = Modifier.fillMaxSize(),
                        content = { isBackground ->
                            if (isBackground) {
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                            } else {
                                screenContent()
                            }
                        }
                    )
                }
                uiState.notice?.let { notice ->
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp, start = 24.dp, end = 24.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
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

@Composable
private fun OfflineCacheBadge(cached: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(11.dp).background(if (cached) StatusGreen else StatusRed, CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            if (cached) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(8.dp),
            tint = MaterialTheme.colorScheme.background
        )
    }
}

/** 已缓存小圆标（统一角标图案）。 */
@Composable
internal fun CachedTick() {
    Icon(
        Icons.Default.Check,
        contentDescription = null,
        modifier = Modifier.size(8.dp),
        tint = MaterialTheme.colorScheme.background
    )
}

/**
 * WV-005：列表锚点恢复目标索引（从 rememberSongListAnchor 抽出便于单测）：
 * - 锚点 id 仍在列表中 → 用其最新位置（数据重排后不漂移）
 * - 有 id 但已不在列表 → 回落保存的索引；从未有锚点 → 用初始索引
 * - 最终夹在有效范围内（列表缩短也不越界）
 */
internal fun resolveSongListAnchorIndex(
    anchorId: String?,
    anchorIndex: Int,
    initialIndex: Int,
    items: List<JellyfinItem>,
): Int {
    val savedIndex = anchorId?.let { id -> items.indexOfFirst { it.id == id } }
        ?.takeIf { it >= 0 }
        ?: if (anchorId == null) initialIndex else anchorIndex
    return savedIndex.coerceIn(items.indices)
}

// WV-002：列表右缘内边距必须 ≥ 索引条宽度，否则行可点击区伸进索引条下形成重叠带（曾为 20dp vs 26dp，重叠 6dp，带内点击可能同时触发跳转与行点击）
internal const val INDEX_STRIP_WIDTH_DP = 26
internal const val LIST_END_PADDING_DP = 26

/** 长文本末尾渐隐（非生硬省略号） */
@Composable
private fun FadingEdgeText(
    text: String,
    style: TextStyle,
    color: Color,
    fadeColor: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    var overflowing by remember(text) { mutableStateOf(false) }
    Box(modifier) {
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            onTextLayout = { overflowing = it.hasVisualOverflow },
            modifier = Modifier.fillMaxWidth()
        )
        if (overflowing) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(20.dp)
                    .height(style.fontSize.value.dp * 1.4f)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, fadeColor)))
            )
        }
    }
}

/** 动态音柱：播放时跳动，暂停时静止 */
@Composable
private fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val bases = listOf(0.4f, 0.75f, 0.55f, 0.9f)
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = bases.mapIndexed { i, base ->
        if (playing) {
            transition.animateFloat(
                initialValue = base,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(320 + i * 110, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$i"
            ).value
        } else {
            base
        }
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(12.dp * h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun LibraryItemRow(
    item: JellyfinItem,
    query: LibraryQuery,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    cached: Boolean = false,
    offline: Boolean = false,
    downloadProgressFraction: Float? = null,
    trailingAction: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
) {
    val unavailableOffline = offline && !cached
    val rowBg = when {
        selected -> MaterialTheme.colorScheme.primary
        isCurrent -> Color(0xFF123B63)
        else -> Color(0xFF242228)
    }
    val rowAlpha = if (unavailableOffline) 0.45f else 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(rowAlpha)
            .clip(RoundedCornerShape(percent = 50))
            .background(rowBg)
            .combinedClickable(
                onClick = if (unavailableOffline) ({}) else onClick,
                onLongClick = if (unavailableOffline) ({}) else onLongClick
            )
            .leftSwipeGesture(onSwipeLeft = if (unavailableOffline) ({}) else onSwipeLeft)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (query.kind) {
                LibraryKind.ARTISTS -> Icons.Default.Person
                LibraryKind.ALBUMS -> Icons.Default.Album
                LibraryKind.SONGS, LibraryKind.DOWNLOADS -> Icons.Default.MusicNote
                LibraryKind.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
            }
            val showBadge = query.kind == LibraryKind.SONGS || query.kind == LibraryKind.DOWNLOADS
            if (isCurrent && query.kind != LibraryKind.ARTISTS && query.kind != LibraryKind.ALBUMS) {
                EqualizerBars(playing = isPlaying, color = MaterialTheme.colorScheme.primary)
            } else {
                Box(modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (showBadge) {
                         if (offline) OfflineCacheBadge(cached, Modifier.align(Alignment.BottomEnd))
                         else CacheBadge(cached, Modifier.align(Alignment.BottomEnd), downloadProgressFraction)
                     }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (selectionMode) {
                Text(
                    if (selected) "✓" else "○",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                FadingEdgeText(
                    text = item.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    fadeColor = rowBg
                )
                if (query.kind != LibraryKind.ARTISTS) {
                    FadingEdgeText(
                        text = item.artistText,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        fadeColor = rowBg
                    )
                }
            }
            if (!selectionMode && trailingAction != null) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp).clickable(onClick = trailingAction)
                )
            }
        }
    }
}

/**
 * 行级左滑手势（MT 管理器式多选入口）：
 * - 单击：位移 < touchSlop 且未判定为拖拽，交给 combinedClickable 处理。
 * - 左滑：|dx| ≥ max(2×touchSlop, 20dp)、dx<0 且 |dx| ≥ 2|dy| 时立即触发 onSwipeLeft，
 *   并消费本手势后续全部事件（防止 TransformingLazyColumn 滚动 / SwipeToDismissBox 抢走）。
 * - 纵向意图（|dy|>|dx|）不消费任何事件，交还列表滚动；右滑放行给系统返回。
 * - 短于阈值的横向拖拽在抬起时消费事件，避免被误判为单击播放。
 */
private fun Modifier.leftSwipeGesture(onSwipeLeft: () -> Unit): Modifier =
    pointerInput(onSwipeLeft) {
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
                        onSwipeLeft()
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

/** 系统分享：文本含歌名/艺人及 Jellyfin 网页端链接。 */
internal fun shareItems(context: Context, serverUrl: String?, items: List<JellyfinItem>) {
    if (items.isEmpty()) return
    val text = items.joinToString("\n") { item ->
        val url = serverUrl?.trimEnd('/')?.let { "$it/web/index.html#!/item?id=${item.id}" }
        "《${item.name}》 - ${item.artistText}" + (url?.let { "\n$it" } ?: "")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, Copy.SHARE))
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "未知"
    if (bytes < 1024L) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return "%.1f KB".format(kib)
    val mib = kib / 1024.0
    if (mib < 1024.0) return "%.1f MB".format(mib)
    return "%.2f GB".format(mib / 1024.0)
}

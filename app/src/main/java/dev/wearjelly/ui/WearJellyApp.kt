package dev.wearjelly.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.LibraryKind
import dev.wearjelly.playback.PlaybackState
import java.lang.Math.cos
import java.lang.Math.sin
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val WearThemeColors = Colors(
    primary = Color(0xFF8CB4FF),
    primaryVariant = Color(0xFF5B8DEF),
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF000000),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color(0xFF001A40),
    onSecondary = Color(0xFF001A40),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFE0E0E0),
    onError = Color(0xFFFFFFFF),
    error = Color(0xFFFF6B6B)
)

@Composable
fun WearJellyApp(viewModel: AppViewModel) {
    MaterialTheme(colors = WearThemeColors) {
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

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val screenContent: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // 页面转场：Player↔Lyrics 左右滑动呼应（左滑进歌词页从右滑入，不再闪现），其余快速淡入淡出
                    AnimatedContent(
                        targetState = uiState.screen,
                        transitionSpec = {
                            val pushToLyrics = initialState is AppScreen.Player && targetState is AppScreen.Lyrics
                            val backToPlayer = initialState is AppScreen.Lyrics && targetState is AppScreen.Player
                            when {
                                pushToLyrics ->
                                    (slideInHorizontally { it } + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally { -it / 3 } + fadeOut(tween(220)))
                                backToPlayer ->
                                    (slideInHorizontally { -it / 3 } + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally { it } + fadeOut(tween(220)))
                                else -> fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                            }
                        },
                        label = "screen"
                    ) { screen ->
                        stateHolder.SaveableStateProvider(screen.toString()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            ) {
                                when (screen) {
                        is AppScreen.Login -> LoginScreen(viewModel, uiState.login)
                        is AppScreen.Home -> HomeScreen(viewModel, uiState.account)
                        is AppScreen.Library -> LibraryScreen(viewModel, screen)
                        is AppScreen.Track -> TrackDetailScreen(viewModel, screen.item, screen.source)
                        is AppScreen.Player -> PlayerScreen(viewModel)
                        is AppScreen.Queue -> QueueScreen(viewModel)
                        is AppScreen.Lyrics -> LyricsPage(viewModel)
                        is AppScreen.LyricsSettings -> LyricsSettingsScreen(viewModel)
                        is AppScreen.Downloads -> DownloadsScreen(viewModel)
                        is AppScreen.History -> HistoryScreen(viewModel)
                        is AppScreen.BatchActions -> BatchActionsScreen(viewModel, screen)
                        is AppScreen.ListActions -> ListActionsScreen(viewModel, screen)
                        is AppScreen.SongInfo -> SongInfoScreen(screen.item)
                        is AppScreen.Settings -> SettingsScreen(viewModel, uiState.account)
                        is AppScreen.Confirm -> ConfirmScreen(viewModel, screen.action, screen.query, screen.menu)
                            }
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
                        modifier = Modifier.fillMaxSize(),
                        content = { isBackground ->
                            if (isBackground) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                            } else {
                                screenContent()
                            }
                        }
                    )
                }
                uiState.notice?.let { notice ->
                    Text(
                        text = notice,
                        fontSize = 11.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp, start = 24.dp, end = 24.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC223044))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * 参考图的列表缩放：中央条目保持原始尺寸，越靠近视野上下边缘的条目按距离等比缩小并渐隐，
 * 让圆形屏幕上滚出视野的条目自然淡出，而不是被圆边硬切。
 *
 * 注意：minElementHeight/maxElementHeight/minTransitionArea/maxTransitionArea 的单位是
 * 「占视口高度的比例」(0f..1f)，不是像素——早期按像素填 4000f/12000f 会让所有条目
 * 永远落在缩放区间内，全部被压到 edgeScale（45% 问题）。
 */
internal val ListScalingParams = ScalingLazyColumnDefaults.scalingParams(
    edgeScale = 0.6f,
    edgeAlpha = 0.35f,
    minElementHeight = 0.2f,
    maxElementHeight = 0.6f,
    minTransitionArea = 0.25f,
    maxTransitionArea = 0.5f,
    viewportVerticalOffsetResolver = { (it.maxHeight * 0.12f).toInt() },
)

@Composable
internal fun LoginScreen(viewModel: AppViewModel, loginUi: LoginUi) {
    val listState = rememberScalingLazyListState()
    val focusManager = LocalFocusManager.current

    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "登录 Jellyfin",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (loginUi.error != null) {
            item {
                Text(
                    text = loginUi.error,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            OutlinedTextField(
                value = loginUi.server,
                onValueChange = { viewModel.changeServer(it) },
                label = { Text("服务器地址", fontSize = 10.sp, color = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            OutlinedTextField(
                value = loginUi.username,
                onValueChange = { viewModel.changeUsername(it) },
                label = { Text("用户名", fontSize = 10.sp, color = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            OutlinedTextField(
                value = loginUi.password,
                onValueChange = { viewModel.changePassword(it) },
                label = { Text("密码", fontSize = 10.sp, color = Color.Gray) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login()
                },
                enabled = !loginUi.submitting,
                modifier = Modifier.fillMaxWidth(0.9f).height(40.dp),
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                if (loginUi.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("登录", style = MaterialTheme.typography.button)
                }
            }
        }
    }
}

@Composable
internal fun HomeScreen(viewModel: AppViewModel, account: AccountUi?) {
    val listState = rememberScalingLazyListState()
    val pbState by viewModel.playbackState.collectAsState()
    val last by viewModel.lastPlayback.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()

    // Mini Player 三态：正在播放 > 上次播放（暂停态） > 隐藏（D4 决策）
    val current = pbState.current
    val lastSnapshot = last
    val miniItem = current
        ?: lastSnapshot?.let { it.queue.getOrNull(it.queueIndex) ?: it.queue.firstOrNull() }

    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "WearJelly",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (account != null) {
            item {
                Text(
                    text = "欢迎, ${account.userName}",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray
                )
            }
        }

        if (miniItem != null) {
            item {
                MiniPlayerCard(
                    item = miniItem,
                    artworkUrl = viewModel.artworkUrl(miniItem),
                    isCurrent = current != null,
                    isPlaying = pbState.isPlaying,
                    cached = miniItem.id in cachedIds,
                    positionMs = pbState.positionMs,
                    durationMs = pbState.effectiveDurationMs,
                    onClick = { viewModel.navigate(AppScreen.Player) },
                    onTogglePlay = {
                        if (current != null) viewModel.togglePlayPause() else viewModel.resumeLastPlayback()
                    },
                    onQueue = { viewModel.navigate(AppScreen.Queue) }
                )
            }
        }

        // 主页 6 个列表入口（t2：长按打开列表级菜单，单击仍进列表；设置非列表不参与）
        item {
            HomeListButton(
                icon = Icons.Default.Person,
                label = "艺人",
                onClick = { viewModel.openLibrary(LibraryKind.ARTISTS) },
                onLongClick = { viewModel.openListMenu(ListMenu.RootArtists, "艺人") },
            )
        }

        item {
            HomeListButton(
                icon = Icons.Default.Album,
                label = "专辑",
                onClick = { viewModel.openLibrary(LibraryKind.ALBUMS) },
                onLongClick = { viewModel.openListMenu(ListMenu.RootAlbums, "专辑") },
            )
        }

        item {
            HomeListButton(
                icon = Icons.Default.MusicNote,
                label = "所有歌曲",
                onClick = { viewModel.openLibrary(LibraryKind.SONGS) },
                onLongClick = { viewModel.openListMenu(ListMenu.RootSongs, "所有歌曲") },
            )
        }

        item {
            HomeListButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "播放列表",
                onClick = { viewModel.openLibrary(LibraryKind.PLAYLISTS) },
                onLongClick = { viewModel.openListMenu(ListMenu.RootPlaylists, "播放列表") },
            )
        }

        item {
            HomeListButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "下载管理",
                onClick = { viewModel.openDownloads() },
                onLongClick = { viewModel.openListMenu(ListMenu.RootDownloads, "已缓存音乐") },
            )
        }

        item {
            HomeListButton(
                icon = Icons.Default.Refresh,
                label = "播放历史",
                onClick = { viewModel.navigate(AppScreen.History) },
                onLongClick = { viewModel.openListMenu(ListMenu.RootHistory, "播放历史") },
            )
        }

        item {
            Button(
                onClick = { viewModel.navigate(AppScreen.Settings) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("设置")
                }
            }
        }
    }
}

/**
 * 主页列表入口按钮（t2）：单击进入列表，长按打开该列表类型的列表级长按菜单。
 * 复刻 wear [Button] 内部 RoundButton 视觉（CircleShape + secondaryButtonColors +
 * typography.button），在其上叠加 [combinedClickable] 长按，保证入口样式不变。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeListButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = ButtonDefaults.secondaryButtonColors()
    val contentColor = colors.contentColor(enabled = true).value
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(colors.backgroundColor(enabled = true).value)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.button, color = contentColor, maxLines = 1)
        }
    }
}

/**
 * 主页 Mini Player（REQ-PLAYBACK-101）：
 * 正在播放 → 播放/暂停+进度条；上次播放 → 暂停态，点播放键从记忆进度继续。
 * 右侧队列图标进入 CurrentQueue 二级页（REQ-PLAYBACK-102）。
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
) {
    Column(
        modifier = Modifier
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
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                CacheBadge(cached = cached, modifier = Modifier.align(Alignment.BottomEnd))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistText,
                    fontSize = 9.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colors.primary,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(onClick = onTogglePlay)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = Copy.MORE + "队列",
                tint = Color(0xFF8A93A5),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onQueue)
            )
        }
        if (isCurrent && durationMs > 0) {
            Spacer(modifier = Modifier.height(4.dp))
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
                        .background(MaterialTheme.colors.primary)
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
            progress = progressFraction.coerceIn(0f, 1f),
            modifier = modifier.size(11.dp),
            strokeWidth = 1.5.dp
        )
        cached -> Box(
            modifier = modifier
                .size(10.dp)
                .background(Color(0xFF7BD88F), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CachedTick()
        }
    }
}

@Composable
private fun OfflineCacheBadge(cached: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(11.dp).background(if (cached) Color(0xFF7BD88F) else Color(0xFFE05A5A), CircleShape), contentAlignment = Alignment.Center) {
        Text(if (cached) "✓" else "✗", fontSize = 7.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

/** 已缓存小圆标（统一角标图案）。 */
@Composable
internal fun CachedTick() {
    Text(text = "✓", fontSize = 7.sp, color = Color.Black, fontWeight = FontWeight.Bold)
}

@Composable
internal fun LibraryScreen(viewModel: AppViewModel, screen: AppScreen.Library) {
    val listState = rememberScalingLazyListState()
    val uiState by viewModel.uiState.collectAsState()
    val lib = uiState.libraries[screen.query] ?: LibraryUi()
    val selectedIds = uiState.selectedSongIds
    val selectionMode = uiState.selectionMode
    val pbState by viewModel.playbackState.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isSongsScreen = screen.query.kind == LibraryKind.SONGS
    val isSelectable = isSongsScreen || screen.query.kind == LibraryKind.ARTISTS || screen.query.kind == LibraryKind.ALBUMS
    // t2：详情页顶部"更多"已移除（能力迁移到上一级长按菜单），头项仅剩标题
    val headerItems = 1
    rememberSongListAnchor(listState, lib.items, headerItems)

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
                        viewModel.navigate(
                            AppScreen.BatchActions(screen.query, screen.title, if (screen.query.kind == LibraryKind.ARTISTS) BatchSource.ARTISTS else BatchSource.LIBRARY)
                        )
                    }
                )
            }
        }
    ) { scaleModifier ->
        ScalingLazyColumn(
            scalingParams = ListScalingParams,
                modifier = scaleModifier.fillMaxSize().padding(start = 14.dp, end = LIST_END_PADDING_DP.dp),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = screen.title,
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (lib.loading && lib.items.isEmpty()) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            } else if (lib.error != null && lib.items.isEmpty()) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = lib.error, color = MaterialTheme.colors.error, fontSize = 12.sp)
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
                         selected = item.id in selectedIds,
                         selectionMode = selectionMode && isSelectable,
                        isCurrent = pbState.current?.id == item.id,
                        isPlaying = pbState.isPlaying,
                        cached = item.id in cachedIds,
                        downloadProgressFraction =
                            if (item.id in downloadingIds) downloadProgress[item.id]?.fraction else null,
                        onClick = {
                            android.util.Log.d("WJ", "row tap ${item.name} kind=${screen.query.kind} sel=$selectionMode")
                            when {
                                selectionMode && isSelectable -> viewModel.toggleSongSelection(item.id)
                                screen.query.kind == LibraryKind.SONGS ||
                                    screen.query.kind == LibraryKind.DOWNLOADS -> viewModel.playTrack(item)
                                else -> viewModel.openItem(screen.query, item)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode || !isSelectable) {
                                android.util.Log.d("WJ", "row long ${item.name} kind=${screen.query.kind}")
                                // 歌曲行=单曲菜单（保留）；实体行=该实体歌曲的列表级菜单（作用域=行条目）
                                when (screen.query.kind) {
                                    LibraryKind.SONGS, LibraryKind.DOWNLOADS ->
                                        viewModel.openTrack(item, screen.query)
                                    LibraryKind.ARTISTS -> viewModel.openListMenu(
                                        ListMenu.Entity(
                                            screen.query.copy(kind = LibraryKind.SONGS, artistId = item.id)
                                        ),
                                        item.name,
                                    )
                                    LibraryKind.ALBUMS -> viewModel.openListMenu(
                                        ListMenu.Entity(
                                            screen.query.copy(kind = LibraryKind.SONGS, parentId = item.id)
                                        ),
                                        item.name,
                                    )
                                    LibraryKind.PLAYLISTS -> viewModel.openListMenu(
                                        ListMenu.Entity(
                                            screen.query.copy(kind = LibraryKind.SONGS, playlistId = item.id)
                                        ),
                                        item.name,
                                    )
                                    else -> Unit
                                }
                            }
                            // 多选模式下长按无操作（菜单留给普通模式），与左滑不冲突
                        },
                        onSwipeLeft = {
                            android.util.Log.d("WJ", "row swipe ${item.name} sel=$selectionMode")
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
                    item { Spacer(Modifier.height(56.dp)) }
                }

                if (lib.loading) {
                    item {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp))
                    }
                }
            }
        }
        }
}

@Composable
private fun rememberSongListAnchor(
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
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
            (listState.centerItemIndex - headerItems) to listState.centerItemScrollOffset
        }.collect { (index, offset) ->
            if (index in items.indices) {
                anchorIndex = index
                anchorId = items[index].id
                anchorOffset = offset
            }
        }
    }
}

private fun firstLetterOf(name: String): String {
    val c = name.trim().uppercase().firstOrNull() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
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

/**
 * 歌曲列表通用脚手架（WearOS 6 风格字母滚动条）：
 * - 平时右缘只显示 1/4 屏高的细轨道 + 位置指示块（随列表滚动移动），列表完全居中
 * - 手指贴边竖滑/点按时展开固定 A-Z# 弧形字母环（贴边、直立字母），列表缩放下沉
 * - 手势判定：竖向意图(│dy│≥0.8│dx│)进入选择并整段消费；横向意图立即放行给返回
 */
@Composable
private fun AlphabetListScaffold(
    items: List<JellyfinItem>,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
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
    val listScale by animateFloatAsState(targetValue = if (scrubbing) 0.82f else 1f)

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

                val centerItemIdx = listState.centerItemIndex - headerItems
                val scrollLetterIdx = items.getOrNull(centerItemIdx)?.let {
                    dev.wearjelly.data.PinyinSort.LETTERS.indexOf(
                        dev.wearjelly.data.PinyinSort.letterBucket(it.name)
                    )
                } ?: -1
                val shownLetterIdx = if (scrubbing) activeLetterIdx else scrollLetterIdx
                // Active window shows the selected letter and up to three neighbors on either side.
                val ringSpanF by animateFloatAsState(if (scrubbing) 3f else 1f)
                val ringSpan = ringSpanF.toInt()
                if (shownLetterIdx >= 0) {
                    val stepDeg = if (scrubbing) 10f else 14f
                    for (role in -3..3) {
                        if (abs(role) > ringSpan.toInt()) continue
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
                        val fontSize by animateFloatAsState(targetSize)
                        val alpha by animateFloatAsState(
                            when {
                                isCenter -> 1f
                                scrubbing && abs(role) == 2 -> 0.45f
                                else -> 0.6f
                            }
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
                                    color = if (isCenter) Color.White else Color(0xFF9DB8E8),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.graphicsLayer { this.alpha = alpha }
                                )
                            }
                        }
                    }
                }

                // 手势层：仅右缘窄条；竖向意图整段消费（允许手指弧线漂移），横向意图立即放行
                val stripWidthDp = INDEX_STRIP_WIDTH_DP.dp
                val stripWidthPx = with(density) { stripWidthDp.toPx() }
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

// WV-002：列表右缘内边距必须 ≥ 索引条宽度，否则行可点击区伸进索引条下形成重叠带（曾为 20dp vs 26dp，重叠 6dp，带内点击可能同时触发跳转与行点击）
internal const val INDEX_STRIP_WIDTH_DP = 26
internal const val LIST_END_PADDING_DP = 26

/** 长文本末尾渐隐（非生硬省略号） */
@Composable
private fun FadingEdgeText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fadeColor: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    var overflowing by remember(text) { mutableStateOf(false) }
    Box(modifier) {
        Text(
            text = text,
            fontSize = fontSize,
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
                    .height(fontSize.value.dp * 1.4f)
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
        selected -> MaterialTheme.colors.primary
        isCurrent -> Color(0xFF123B63)
        else -> MaterialTheme.colors.surface
    }
    val rowAlpha = if (unavailableOffline) 0.45f else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
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
                EqualizerBars(playing = isPlaying, color = MaterialTheme.colors.primary)
            } else {
                Box(modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.primary,
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
                    fontSize = 18.sp,
                    color = if (selected) MaterialTheme.colors.onPrimary else Color.LightGray
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                FadingEdgeText(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fadeColor = rowBg
                )
                if (query.kind != LibraryKind.ARTISTS) {
                    FadingEdgeText(
                        text = item.artistText,
                        fontSize = 9.sp,
                        color = Color(0xFF8A93A5),
                        fadeColor = rowBg
                    )
                }
            }
            if (!selectionMode && trailingAction != null) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "移除",
                    tint = Color.Gray,
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
 *   并消费本手势后续全部事件（防止 ScalingLazyColumn 滚动 / SwipeToDismissBox 抢走）。
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

/**
 * 多选模式固定底部操作栏（单行）：退出多选 / 已选 N 首 / 更多。
 * 批量操作收进"更多"二级页（BatchActionsScreen）。
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
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
        ) {
            Text(Copy.EXIT_SELECTION, fontSize = 10.sp, color = Color.White, maxLines = 1)
        }
        Text(
            text = Copy.selectedCount(selectedCount),
            fontSize = 11.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onMore,
            modifier = Modifier.height(30.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF22304A))
        ) {
            Text(Copy.MORE, fontSize = 10.sp, color = Color.White, maxLines = 1)
        }
    }
}

/** 多选"更多"二级操作页：常用批量 + 状态操作（全选/取消全选/反选）。 */
@Composable
internal fun BatchActionsScreen(viewModel: AppViewModel, screen: AppScreen.BatchActions) {
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

    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = rememberScalingLazyListState(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = screen.title,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item {
            Text(
                text = Copy.selectedCount(uiState.selectedSongIds.size),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        if (screen.source != BatchSource.ARTISTS) {
            item {
                Button(
                    onClick = {
                        viewModel.batchSelectedSongsToQueue(items)
                        viewModel.goBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Text(Copy.ADD_TO_QUEUE, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (screen.source != BatchSource.ARTISTS) {
            item {
                Button(
                    onClick = {
                        viewModel.batchSelectedSongsToCache(items)
                        viewModel.goBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Text(Copy.cacheSelected(uiState.selectedSongIds.size), fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.playItemsNext(selectedItems)
                        viewModel.goBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text(Copy.PLAY_NEXT, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.deleteCachedTracks(uiState.selectedSongIds)
                        viewModel.exitSelectionMode()
                        viewModel.goBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Text(Copy.DELETE_SELECTED_CACHES, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (screen.source == BatchSource.QUEUE) {
            item {
                Button(
                    onClick = {
                        viewModel.removeSelectedFromQueue(items)
                        viewModel.goBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Text("从队列移除", fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (screen.source == BatchSource.LIBRARY) {
            item {
                Button(
                    onClick = {
                        viewModel.removeFromLibraryView(screen.query, uiState.selectedSongIds)
                        viewModel.exitSelectionMode()
                        viewModel.goBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Text(Copy.REMOVE_FROM_LIST, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Button(
                onClick = { shareItems(context, uiState.account?.serverUrl, selectedItems) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(Copy.SHARE, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = { viewModel.selectAllItems(items) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(Copy.SELECT_ALL, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.exitSelectionMode()
                    viewModel.goBack()
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(Copy.SELECT_NONE, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.invertSelectionItems(items)
                    // 反选后为 0：自动退出多选并离开本页
                    if (!viewModel.uiState.value.selection.active) viewModel.goBack()
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(Copy.INVERT_SELECTION, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = { viewModel.goBack() },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("返回", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 列表级长按菜单（t2：主页 6 入口与艺人/专辑/播放列表实体条目长按进入；
 * 原详情页顶部"更多"与 ScopeActions 页合并于此）。
 * 菜单项集合与顺序由 [ListMenu.items] 按类型决定，动作统一走 [AppViewModel.onListMenuAction]。
 */
@Composable
internal fun ListActionsScreen(viewModel: AppViewModel, screen: AppScreen.ListActions) {
    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = rememberScalingLazyListState(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = screen.title,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        items(screen.menu.items()) { entry ->
            val (background, textColor) = when (entry.action) {
                ListMenuAction.PLAY_ALL -> null to null
                ListMenuAction.CACHE_ALL -> Color(0xFF4A5568) to Color.White
                ListMenuAction.DELETE_CACHES -> Color(0xFF4A1515) to Color.White
                else -> null to null
            }
            Button(
                onClick = { viewModel.onListMenuAction(screen.menu, entry.action) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = when (entry.action) {
                    ListMenuAction.PLAY_ALL -> ButtonDefaults.primaryButtonColors()
                    else -> background?.let {
                        ButtonDefaults.buttonColors(backgroundColor = it)
                    } ?: ButtonDefaults.secondaryButtonColors()
                }
            ) {
                if (entry.action == ListMenuAction.SHUFFLE_ALL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(entry.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(
                        entry.label,
                        fontSize = 12.sp,
                        color = textColor ?: Color.Unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            Button(
                onClick = { viewModel.goBack() },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("取消", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SongInfoScreen(item: JellyfinItem) {
    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = rememberScalingLazyListState(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = Copy.SONG_INFO,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item { SongInfoRow("名称", item.name) }
        item { SongInfoRow("艺人", item.artistText) }
        item { SongInfoRow("专辑", item.album ?: "未知") }
        item { SongInfoRow("时长", formatTime(item.durationMs)) }
        item { SongInfoRow("格式", item.container ?: "未知") }
        item { SongInfoRow("ID", item.id) }
    }
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 9.sp, color = Color(0xFF8A93A5))
        Text(
            text = value,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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

@Composable
internal fun TrackDetailScreen(viewModel: AppViewModel, item: JellyfinItem, source: LibraryQuery) {
    val listState = rememberScalingLazyListState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading = item.id in downloadingIds
    val progress = downloadProgress[item.id]
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val isDownloaded = item.id in cachedIds
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = item.name,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        item {
            Text(
                text = item.artistText,
                style = MaterialTheme.typography.caption1,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        item {
            Button(
                onClick = { viewModel.playTrack(item) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.primaryButtonColors()
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
                onClick = { viewModel.playItemsNext(listOf(item)); viewModel.goBack() },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(Copy.PLAY_NEXT, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item {
            Button(
                onClick = { viewModel.enqueue(item); viewModel.goBack() },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(Copy.ADD_TO_QUEUE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (source.kind == LibraryKind.SONGS) {
            item {
                Button(
                    onClick = { viewModel.playLoaded(source, item.id) },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(Copy.PLAY_ALL_FROM_HERE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.downloadTrack(item); viewModel.goBack() },
                enabled = !isDownloading && !isDownloaded,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
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
                                        .background(MaterialTheme.colors.primary)
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            val prefix = if (progress.estimated) "约 " else ""
                            val quality = progress.qualityLabel.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
                            Text(
                                text = "${quality}${prefix}${(fraction * 100).toInt()}% · ${formatBytes(progress.downloadedBytes)}" + (if (progress.totalBytes > 0) " / ${formatBytes(progress.totalBytes)}" else ""),
                                fontSize = 10.sp
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            val quality = progress?.qualityLabel?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
                            Text(
                                text = "${quality}已缓存 ${formatBytes(progress?.downloadedBytes ?: 0L)}",
                                fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    Text(Copy.CACHE_THIS)
                }
            }
        }

        if (isDownloaded) {
            item {
                Button(
                    onClick = { viewModel.deleteDownloadedTrack(item.id) },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Text(Copy.DELETE_THIS_CACHE, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.navigate(AppScreen.SongInfo(item)) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(Copy.SONG_INFO, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item {
            Button(
                onClick = { shareItems(context, uiState.account?.serverUrl, listOf(item)); viewModel.goBack() },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(Copy.SHARE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (source.kind == LibraryKind.SONGS) {
            item {
                Button(
                    onClick = {
                        viewModel.removeFromLibraryView(source, setOf(item.id))
                        viewModel.goBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Text(Copy.REMOVE_FROM_LIST, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun PlayerScreen(viewModel: AppViewModel) {
    val pbState by viewModel.playbackState.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val listState = rememberScalingLazyListState()
    val item = pbState.current
    // 播放结束：按钮变重播（Media3 play() 在 STATE_ENDED 从头播放）
    val durationMs = pbState.effectiveDurationMs
    val ended = durationMs > 0 && !pbState.isPlaying && !pbState.buffering &&
        pbState.positionMs >= durationMs - 300

    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            // 页面级左滑 → LyricsPage（进度条区域已独占横向手势，不会误触）
            .leftSwipeGesture(onSwipeLeft = { viewModel.navigate(AppScreen.Lyrics) }),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            val artUrl = item?.let { viewModel.artworkUrl(it) }
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                }
            }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item?.name ?: "未播放",
                    style = MaterialTheme.typography.title3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = item?.artistText ?: "Jellyfin",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (item != null && item.id in cachedIds) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF7BD88F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CachedTick()
                    }
                }
            }
        }

        if (pbState.buffering) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在缓冲音乐...", color = MaterialTheme.colors.primary, fontSize = 12.sp)
                }
            }
        }

        if (pbState.error != null) {
            item {
                Text(
                    text = pbState.error ?: "播放失败",
                    color = MaterialTheme.colors.error,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            SeekableProgressBar(
                positionMs = pbState.positionMs,
                durationMs = pbState.effectiveDurationMs,
                bufferedPositionMs = pbState.bufferedPositionMs,
                buffering = pbState.buffering,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (ended) {
                            viewModel.seekTo(0L)
                            viewModel.togglePlayPause()
                        } else {
                            viewModel.togglePlayPause()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Icon(
                        imageVector = when {
                            ended -> Icons.Default.Refresh
                            pbState.isPlaying -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = if (ended) "重播" else null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colors.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                }
            }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.adjustVolume(-1) },
                        modifier = Modifier.size(36.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3F4B))
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "减小音量", modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier.size(36.dp),
                        colors = if (pbState.shuffleEnabled) ButtonDefaults.primaryButtonColors() else ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3F4B))
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (pbState.shuffleEnabled) MaterialTheme.colors.onPrimary else Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { viewModel.cycleRepeatMode() },
                        modifier = Modifier.size(36.dp),
                        colors = if (pbState.repeatMode != Player.REPEAT_MODE_OFF) ButtonDefaults.primaryButtonColors() else ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3F4B))
                    ) {
                        val icon = if (pbState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (pbState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colors.onPrimary else Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { viewModel.adjustVolume(1) },
                        modifier = Modifier.size(36.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3F4B))
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "增大音量", modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "音量 ${pbState.volumePercent.coerceIn(0, 100)}%",
                    fontSize = 9.sp,
                    color = Color.LightGray
                )
            }
        }

        item {
            // 歌词按钮已删（REQ-HOME-SEEK-001 决策）：左滑是进入歌词页的唯一入口
            Button(
                onClick = { viewModel.navigate(AppScreen.Queue) },
                modifier = Modifier.height(34.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("队列 (${pbState.queue.size})", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun QueueScreen(viewModel: AppViewModel) {
    val pbState by viewModel.playbackState.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedIds = uiState.selectedSongIds
    val selectionMode = uiState.selectionMode
    val listState = rememberScalingLazyListState()
    val queueItems = remember(pbState.queue) { pbState.queue.map { it.item } }
    rememberSongListAnchor(
        listState,
        queueItems,
        headerItems = 2,
        initialIndex = pbState.currentIndex.coerceAtLeast(0),
    )

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "播放队列",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        item {
            Button(
                onClick = { viewModel.requestConfirmation(ConfirmAction.CLEAR_QUEUE) },
                modifier = Modifier.fillMaxWidth(0.8f).height(36.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A1515))
            ) {
                Text("清空队列", fontSize = 12.sp, color = Color.White)
            }
        }

        if (pbState.queue.isEmpty()) {
            item {
                Text("队列为空", color = Color.Gray, style = MaterialTheme.typography.caption2)
            }
        } else {
            itemsIndexed(pbState.queue) { index, track ->
                val item = track.item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}.",
                        fontSize = 11.sp,
                        color = if (index == pbState.currentIndex) MaterialTheme.colors.primary else Color.Gray,
                        modifier = Modifier.width(24.dp)
                    )
                    LibraryItemRow(
                        item = item,
                        query = LibraryQuery(LibraryKind.SONGS),
                        selected = item.id in selectedIds,
                        selectionMode = selectionMode,
                        isCurrent = item.id == pbState.current?.id,
                        isPlaying = pbState.isPlaying,
                        cached = item.id in cachedIds,
                        trailingAction = { viewModel.removeQueueItem(index, item.id) },
                        onClick = {
                            if (selectionMode) viewModel.toggleSongSelection(item.id)
                            else viewModel.playQueueIndex(index, item.id)
                        },
                        onLongClick = {
                            if (!selectionMode) viewModel.openTrack(item, LibraryQuery(LibraryKind.SONGS))
                        },
                        onSwipeLeft = {
                            if (selectionMode) {
                                viewModel.selectRangeFromSwipe(item.id, pbState.queue.map { it.item.id })
                            } else {
                                viewModel.beginSelectionFromSwipe(item.id)
                            }
                        },
                    )
                }
            }
        }
        if (selectionMode && pbState.queue.isNotEmpty()) {
            item { Spacer(Modifier.height(56.dp)) }
        }
    }
    if (selectionMode) {
        MultiSelectBar(
            selectedCount = selectedIds.size,
            onExit = { viewModel.exitSelectionMode() },
            onMore = {
                viewModel.navigate(
                    AppScreen.BatchActions(
                        LibraryQuery(LibraryKind.SONGS),
                        "播放队列",
                        BatchSource.QUEUE,
                    )
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun DownloadsScreen(viewModel: AppViewModel) {
    val listState = rememberScalingLazyListState()
    val items by viewModel.downloadManagementItems.collectAsState()
    val pbState by viewModel.playbackState.collectAsState()
    val queue by viewModel.downloadQueue.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedIds = uiState.selectedSongIds
    val selectionMode = uiState.selectionMode
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val offline = viewModel.isOfflineMode
    var sectionExpanded by remember { mutableStateOf(true) }

    val headerItems = 1 + (if (queue.isNotEmpty()) 1 else 0) +
        (if (queue.isNotEmpty() && sectionExpanded) queue.size else 0)
    rememberSongListAnchor(listState, items, headerItems)

    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = LIST_END_PADDING_DP.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("下载管理", style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary, modifier = Modifier.padding(top = 12.dp))
        }
        if (queue.isNotEmpty()) {
            item {
                Text("缓存队列 (${queue.size}) " + if (sectionExpanded) "▲" else "▼",
                    fontSize = 11.sp, color = Color(0xFF8A93A5), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { sectionExpanded = !sectionExpanded }.padding(vertical = 4.dp))
            }
            if (sectionExpanded) {
                items(queue) { entry ->
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF14202F)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (entry.status) {
                                dev.wearjelly.data.DownloadStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                dev.wearjelly.data.DownloadStatus.DONE -> Text("✓", color = Color(0xFF7BD88F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                dev.wearjelly.data.DownloadStatus.FAILED -> Text("✗", color = MaterialTheme.colors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                dev.wearjelly.data.DownloadStatus.WAITING -> Text("…", color = Color(0xFF8A93A5), fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            FadingEdgeText(entry.itemName, fontSize = 11.sp, color = Color.White,
                                fadeColor = Color(0xFF14202F), modifier = Modifier.weight(1f))
                        }
                        if (entry.status == dev.wearjelly.data.DownloadStatus.RUNNING) {
                            val fraction = entry.totalBytes.takeIf { it > 0 }?.let { entry.downloadedBytes.toFloat() / it }
                            if (fraction != null) {
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.fillMaxWidth().height(4.dp).background(Color.DarkGray)) { Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colors.primary)) }
                                Text("${entry.qualityLabel} · ${(fraction * 100).toInt()}% · ${formatBytes(entry.downloadedBytes)}", fontSize = 9.sp, color = Color.LightGray)
                            }
                        } else if (entry.status == dev.wearjelly.data.DownloadStatus.WAITING) {
                            Text("排队中 · ${entry.qualityLabel}", fontSize = 9.sp, color = Color(0xFF8A93A5))
                        } else if (entry.status == dev.wearjelly.data.DownloadStatus.FAILED) {
                            Text("缓存失败，请检查网络后重试", fontSize = 9.sp, color = Color(0xFF8A93A5))
                        }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item { Text(if (offline) "暂无歌曲元数据" else "暂无下载记录", color = Color.Gray, fontSize = 12.sp) }
        } else {
            itemsIndexed(items) { _, item ->
                LibraryItemRow(item = item, query = LibraryQuery(LibraryKind.DOWNLOADS),
                    selected = item.id in selectedIds, selectionMode = selectionMode,
                    isCurrent = pbState.current?.id == item.id, isPlaying = pbState.isPlaying,
                    cached = item.id in cachedIds, offline = offline,
                    downloadProgressFraction = if (item.id in downloadingIds) downloadProgress[item.id]?.fraction else null,
                    onClick = { if (selectionMode) viewModel.toggleSongSelection(item.id) else viewModel.playTrack(item) },
                    onLongClick = { if (!selectionMode) viewModel.openTrack(item, LibraryQuery(LibraryKind.DOWNLOADS)) },
                    onSwipeLeft = { if (selectionMode) viewModel.selectRangeFromSwipe(item.id, items.map { it.id }) else viewModel.beginSelectionFromSwipe(item.id) })
            }
        }
        if (selectionMode && items.isNotEmpty()) { item { Spacer(Modifier.height(56.dp)) } }
    }
}
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun HistoryScreen(viewModel: AppViewModel) {
    val listState = rememberScalingLazyListState()
    val history by viewModel.history.collectAsState()
    val pbState by viewModel.playbackState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedIds = uiState.selectedSongIds
    val selectionMode = uiState.selectionMode
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val historyItems = remember(history) { history.map { it.item } }
    rememberSongListAnchor(listState, historyItems, headerItems = 1)

    AlphabetListScaffold(
        items = historyItems,
        listState = listState,
        headerItems = 1,
        showIndex = false,
        overlay = {
            if (selectionMode) {
                MultiSelectBar(
                    selectedCount = selectedIds.size,
                    onExit = { viewModel.exitSelectionMode() },
                    onMore = {
                        viewModel.navigate(
                            AppScreen.BatchActions(
                                LibraryQuery(LibraryKind.SONGS),
                                "播放历史",
                                BatchSource.HISTORY,
                            )
                        )
                    }
                )
            }
        }
    ) { scaleModifier ->
    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = scaleModifier.fillMaxSize().padding(horizontal = 8.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "播放历史",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (history.isEmpty()) {
            item { Text("暂无播放记录", color = Color.Gray, fontSize = 12.sp) }
        } else {
            itemsIndexed(history) { index, entry ->
                val item = entry.item
                LibraryItemRow(
                    item = item,
                    query = LibraryQuery(LibraryKind.SONGS),
                    selected = item.id in selectedIds,
                    selectionMode = selectionMode,
                    isCurrent = pbState.current?.id == item.id,
                    isPlaying = pbState.isPlaying,
                    cached = item.id in cachedIds,
                    onClick = {
                        if (selectionMode) viewModel.toggleSongSelection(item.id) else viewModel.playTrack(item)
                    },
                    onLongClick = {
                        if (!selectionMode) viewModel.openTrack(item, LibraryQuery(LibraryKind.SONGS))
                    },
                    onSwipeLeft = {
                        if (selectionMode) {
                            viewModel.selectRangeFromSwipe(item.id, historyItems.map { it.id })
                        } else {
                            viewModel.beginSelectionFromSwipe(item.id)
                        }
                    },
                )
            }
        }
        if (selectionMode && history.isNotEmpty()) {
            item { Spacer(Modifier.height(56.dp)) }
        }
    }
    }
}

@Composable
internal fun SettingsScreen(viewModel: AppViewModel, account: AccountUi?) {
    val listState = rememberScalingLazyListState()
    val currentBitrate by viewModel.bitrate.collectAsState()

    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (account != null) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("当前用户", style = MaterialTheme.typography.caption2, color = Color.Gray)
                    Text(account.userName, style = MaterialTheme.typography.body2)
                    Spacer(Modifier.height(4.dp))
                    Text("服务器", style = MaterialTheme.typography.caption2, color = Color.Gray)
                    Text(account.serverUrl, style = MaterialTheme.typography.caption2, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Text(
                text = "在线播放音质",
                style = MaterialTheme.typography.caption1,
                color = Color.LightGray
            )
        }

        items(dev.wearjelly.data.AudioBitrate.entries.toTypedArray()) { bitrate ->
            Button(
                onClick = { viewModel.setAudioBitrate(bitrate) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = if (currentBitrate == bitrate) {
                    ButtonDefaults.primaryButtonColors()
                } else {
                    ButtonDefaults.secondaryButtonColors()
                }
            ) {
                Text(
                    text = if (currentBitrate == bitrate) "✓ ${bitrate.displayName}" else bitrate.displayName,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        item {
            Text(
                text = "转码仅影响新开始的在线播放",
                fontSize = 9.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        item {
            Button(
                onClick = { viewModel.requestConfirmation(ConfirmAction.SWITCH_SERVER) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("切换服务器")
            }
        }

        item {
            Button(
                onClick = { viewModel.requestConfirmation(ConfirmAction.LOGOUT) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6B1B1B))
            ) {
                Text("退出登录", color = Color.White)
            }
        }
    }
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

@Composable
internal fun ConfirmScreen(
    viewModel: AppViewModel,
    action: ConfirmAction,
    query: LibraryQuery? = null,
    menu: ListMenu? = null,
) {
    val title = when (action) {
        ConfirmAction.CLEAR_QUEUE -> "确定清空播放队列？"
        ConfirmAction.SWITCH_SERVER -> "确定切换服务器？\n将清除当前登录"
        ConfirmAction.LOGOUT -> "确定退出登录？"
        ConfirmAction.DELETE_LIST_CACHES -> "确定删除当前列表全部缓存？"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.goBack() },
                modifier = Modifier.size(44.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("否")
            }
            Button(
                onClick = { viewModel.confirm(action, query, menu) },
                modifier = Modifier.size(44.dp),
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                Text("是")
            }
        }
    }
}
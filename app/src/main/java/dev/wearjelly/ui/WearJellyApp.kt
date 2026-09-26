package dev.wearjelly.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
        val isRootScreen = uiState.backStack.size <= 1
        BackHandler(enabled = !isRootScreen) {
            viewModel.goBack()
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
                    when (val screen = uiState.screen) {
                        is AppScreen.Login -> LoginScreen(viewModel, uiState.login)
                        is AppScreen.Home -> HomeScreen(viewModel, uiState.account)
                        is AppScreen.Library -> LibraryScreen(viewModel, screen)
                        is AppScreen.Track -> TrackDetailScreen(viewModel, screen.item, screen.source)
                        is AppScreen.Player -> PlayerScreen(viewModel)
                        is AppScreen.Queue -> QueueScreen(viewModel)
                        is AppScreen.Lyrics -> LyricsScreen(viewModel)
                        is AppScreen.Downloads -> DownloadsScreen(viewModel)
                        is AppScreen.History -> HistoryScreen(viewModel)
                        is AppScreen.ScopeActions -> ScopeActionsScreen(viewModel, screen)
                        is AppScreen.Settings -> SettingsScreen(viewModel, uiState.account)
                        is AppScreen.Confirm -> ConfirmScreen(viewModel, screen.action)
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

@Composable
internal fun LoginScreen(viewModel: AppViewModel, loginUi: LoginUi) {
    val listState = rememberScalingLazyListState()
    val focusManager = LocalFocusManager.current

    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
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

    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
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

        if (pbState.current != null) {
            item {
                Button(
                    onClick = { viewModel.navigate(AppScreen.Player) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (pbState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = pbState.current?.name ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.caption1
                            )
                            Text(
                                text = pbState.current?.artistText ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.caption2,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.openLibrary(LibraryKind.ARTISTS) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("艺人")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.openLibrary(LibraryKind.ALBUMS) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Album, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("专辑")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.openLibrary(LibraryKind.SONGS) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("所有歌曲")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.navigate(AppScreen.Downloads) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("已缓存音乐", maxLines = 1)
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.navigate(AppScreen.History) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("播放历史", maxLines = 1)
                }
            }
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

@Composable
internal fun LibraryScreen(viewModel: AppViewModel, screen: AppScreen.Library) {
    val listState = rememberScalingLazyListState()
    val uiState by viewModel.uiState.collectAsState()
    val lib = uiState.libraries[screen.query] ?: LibraryUi()
    val selectedIds = uiState.selectedSongIds
    val selectionMode = uiState.selectionMode
    val pbState by viewModel.playbackState.collectAsState()
    val isSongsScreen = screen.query.kind == LibraryKind.SONGS
    val headerItems = 1 +
        (if (selectionMode && isSongsScreen) 1 else 0) +
        (if (screen.query.kind == LibraryKind.ALBUMS && screen.query.artistId != null) 1 else 0)

    AlphabetListScaffold(
        items = lib.items,
        listState = listState,
        headerItems = headerItems
    ) { scaleModifier ->
        ScalingLazyColumn(
            scalingParams = EdgeScalingParams,
            modifier = scaleModifier.fillMaxSize().padding(start = 14.dp, end = 20.dp),
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

            if (selectionMode && isSongsScreen) {
                item {
                    Button(
                        onClick = { viewModel.exitSelectionMode() },
                        modifier = Modifier.fillMaxWidth(0.9f).height(38.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                    ) {
                        Text(
                            "退出多选 (${selectedIds.size})",
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (screen.query.kind == LibraryKind.ALBUMS && screen.query.artistId != null) {
                item {
                    Button(
                        onClick = { viewModel.openArtistSongs(screen) },
                        modifier = Modifier.fillMaxWidth(0.9f).height(38.dp),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Text("查看该艺人全部歌曲", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
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
                        selectionMode = selectionMode && isSongsScreen,
                        isCurrent = pbState.current?.id == item.id,
                        isPlaying = pbState.isPlaying,
                        onClick = {
                            android.util.Log.d("WJ", "row tap ${item.name} kind=${screen.query.kind} sel=$selectionMode")
                            when {
                                selectionMode && isSongsScreen -> viewModel.toggleSongSelection(item.id)
                                screen.query.kind == LibraryKind.SONGS ||
                                    screen.query.kind == LibraryKind.DOWNLOADS -> viewModel.playTrack(item)
                                else -> viewModel.openItem(screen.query, item)
                            }
                        },
                        onLongClick = {
                            android.util.Log.d("WJ", "row long ${item.name} kind=${screen.query.kind}")
                            when (screen.query.kind) {
                                LibraryKind.SONGS, LibraryKind.DOWNLOADS ->
                                    viewModel.openTrack(item, screen.query)
                                LibraryKind.ARTISTS, LibraryKind.ALBUMS ->
                                    viewModel.openScopeActions(screen.query, item.name)
                                else -> Unit
                            }
                        },
                    )

                    if (index == lib.items.lastIndex && !lib.endReached && !lib.loading) {
                        viewModel.loadMore(screen.query)
                    }
                }

                if (selectionMode && isSongsScreen) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { viewModel.batchSelectedSongsToQueue(lib.items) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.primaryButtonColors()
                            ) {
                                Text("选中入队", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = { viewModel.batchSelectedSongsToCache(lib.items) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                            ) {
                                Text("选中缓存", fontSize = 10.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
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

private fun firstLetterOf(name: String): String {
    val c = name.trim().uppercase().firstOrNull() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
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

        if (items.isNotEmpty()) {
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

                // 极简字母指示器：
                // 闲置 —— 当前字母±1，大字、保持弧度，跟随滚动位置灵动滑动
                // 使用 —— 当前字母±2 居中放大高亮，纵向均匀分布在右缘 3/4 高度
                val centerItemIdx = listState.centerItemIndex - headerItems
                val scrollLetterIdx = items.getOrNull(centerItemIdx)?.let {
                    RING_LETTERS.indexOf(dev.wearjelly.data.PinyinSort.letterBucket(it.name))
                } ?: -1
                val shownLetterIdx = if (scrubbing) activeLetterIdx else scrollLetterIdx
                val ringSpanF by animateFloatAsState(if (scrubbing) 2f else 1f)
                val ringSpan = ringSpanF.toInt()
                if (shownLetterIdx >= 0) {
                    // 按角度布点：可见弧度明显，字形整体内收不裁边
                    val stepDeg = if (scrubbing) 8f else 11f
                    for (role in -2..2) {
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
                val stripWidthDp = 26.dp
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
                                var engaged = false
                                var decided = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!decided) {
                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y
                                        if (dx * dx + dy * dy > viewConfiguration.touchSlop * viewConfiguration.touchSlop) {
                                            decided = true
                                            engaged = abs(dy) >= abs(dx) * 0.8f
                                            if (engaged) {
                                                scrubbing = true
                                                jumpToLetter(nearestLetterIdx(change.position.y))
                                            } else {
                                                break // 横向：交还返回手势
                                            }
                                        }
                                    }
                                    if (engaged) {
                                        change.consume()
                                        jumpToLetter(nearestLetterIdx(change.position.y))
                                    }
                                    if (!event.changes.any { it.pressed }) break
                                }
                                if (!decided) {
                                    scrubbing = true
                                    jumpToLetter(nearestLetterIdx(down.position.y))
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
 * 全局列表缩放（WearOS 6 参考）：中央两项保持最大，向上下边缘快速等比缩小。
 * transitionArea 收窄使衰减集中在中心附近，边缘元素明显更小。
 */
private val EdgeScalingParams = androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults.scalingParams(
    edgeScale = 0.45f,
    edgeAlpha = 0.75f,
    minElementHeight = 20f,
    maxElementHeight = 48f,
    minTransitionArea = 4000f,
    maxTransitionArea = 12000f,
    viewportVerticalOffsetResolver = { constraints -> constraints.maxHeight / 2 },
)

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
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val rowBg = when {
        selected -> MaterialTheme.colors.primary
        isCurrent -> Color(0xFF123B63)
        else -> MaterialTheme.colors.surface
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(rowBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
            }
            if (isCurrent && query.kind != LibraryKind.ARTISTS && query.kind != LibraryKind.ALBUMS) {
                EqualizerBars(playing = isPlaying, color = MaterialTheme.colors.primary)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.primary
                )
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
        }
    }
}

@Composable
internal fun ScopeActionsScreen(viewModel: AppViewModel, screen: AppScreen.ScopeActions) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = screen.title,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item {
            Button(
                onClick = {
                    viewModel.batchAllSongsToQueue(screen.query)
                    viewModel.goBack()
                },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                Text("全部歌曲入队", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.batchAllSongsToCache(screen.query)
                    viewModel.goBack()
                },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
            ) {
                Text("全部歌曲缓存", fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
internal fun TrackDetailScreen(viewModel: AppViewModel, item: JellyfinItem, source: LibraryQuery) {
    val listState = rememberScalingLazyListState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading = item.id in downloadingIds
    val progress = downloadProgress[item.id]
    val isDownloaded = viewModel.isDownloaded(item.id)

    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
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

        if (source.kind == LibraryKind.SONGS) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { viewModel.batchAllSongsToQueue(source) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Text("全部入队", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { viewModel.batchAllSongsToCache(source) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                    ) {
                        Text("全部缓存", fontSize = 10.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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
                    Text("单曲播放")
                }
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
                        Text("以此开始播放全部")
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.enterSelectionMode()
                        viewModel.goBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Text("多选歌曲", color = Color.White, maxLines = 1)
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (isDownloaded) viewModel.deleteDownloadedTrack(item.id) else viewModel.downloadTrack(item)
                },
                enabled = !isDownloading,
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
                    Text(if (isDownloaded) "删除本地缓存" else "下载并离线缓存")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.enqueue(item) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("加入待播队列")
            }
        }
    }
}

@Composable
internal fun PlayerScreen(viewModel: AppViewModel) {
    val pbState by viewModel.playbackState.collectAsState()
    val listState = rememberScalingLazyListState()
    val item = pbState.current

    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
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
            val currentMs = pbState.positionMs
            val totalMs = pbState.effectiveDurationMs
            val playedFraction = if (totalMs > 0) (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
            val bufferedFraction = if (totalMs > 0) {
                (pbState.bufferedPositionMs.toFloat() / totalMs.toFloat()).coerceIn(playedFraction, 1f)
            } else {
                (pbState.bufferedPercentage / 100f).coerceIn(0f, 1f)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.85f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.DarkGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferedFraction)
                            .height(5.dp)
                            .background(Color(0xFF6B7280))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(playedFraction)
                            .height(5.dp)
                            .background(MaterialTheme.colors.primary)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentMs), fontSize = 10.sp, color = Color.Gray)
                    Text(text = formatTime(totalMs), fontSize = 10.sp, color = Color.Gray)
                }
            }
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
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Icon(
                        imageVector = if (pbState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.navigate(AppScreen.Queue) },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("队列 (${pbState.queue.size})", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { viewModel.navigate(AppScreen.Lyrics) },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("歌词", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
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
                val isCurrent = index == pbState.currentIndex
                Button(
                    onClick = { viewModel.playQueueIndex(index, track.item.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isCurrent) Color(0xFF1E3A66) else MaterialTheme.colors.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 11.sp,
                            color = if (isCurrent) MaterialTheme.colors.primary else Color.Gray,
                            modifier = Modifier.width(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.item.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = track.item.artistText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "移除",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    viewModel.removeQueueItem(index, track.item.id)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LyricsScreen(viewModel: AppViewModel) {
    val listState = rememberScalingLazyListState()
    val uiState by viewModel.uiState.collectAsState()
    val pbState by viewModel.playbackState.collectAsState()
    val lyrics = uiState.lyrics.lyrics

    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "歌词",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (uiState.lyrics.loading) {
            item {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        } else if (lyrics == null || lyrics.lines.isEmpty()) {
            item {
                Text("暂无歌词", color = Color.Gray, style = MaterialTheme.typography.body2)
            }
        } else {
            itemsIndexed(lyrics.lines) { index, line ->
                val isHighlighted = if (lyrics.isSynchronized && line.startMs != null) {
                    val nextStart = lyrics.lines.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
                    pbState.positionMs in (line.startMs!! until nextStart)
                } else false

                Text(
                    text = line.text,
                    textAlign = TextAlign.Center,
                    style = if (isHighlighted) MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.caption1,
                    color = if (isHighlighted) MaterialTheme.colors.primary else Color.LightGray,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun DownloadsScreen(viewModel: AppViewModel) {
    val listState = rememberScalingLazyListState()
    val downloaded by viewModel.downloadedSongs.collectAsState()
    val pbState by viewModel.playbackState.collectAsState()
    val queue by viewModel.downloadQueue.collectAsState()
    var sectionExpanded by remember { mutableStateOf(true) }

    val sortedDownloaded = remember(downloaded, viewModel.history) {
        val history = viewModel.history.value
        downloaded.sortedByDescending { song ->
            val playedAt = history.firstOrNull { it.item.id == song.item.id }?.playedAtMs ?: 0L
            if (playedAt > 0L) playedAt else song.downloadedTimeMs
        }
    }

    val headerItems = 1 + (if (queue.isNotEmpty()) 1 else 0) + (if (queue.isNotEmpty() && sectionExpanded) queue.size else 0)

    AlphabetListScaffold(
        items = sortedDownloaded.map { it.item },
        listState = listState,
        headerItems = headerItems
    ) { scaleModifier ->
    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
        modifier = scaleModifier.fillMaxSize().padding(start = 14.dp, end = 20.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "已缓存音乐",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (queue.isNotEmpty()) {
            item {
                Text(
                    text = "缓存队列 (${queue.size}) " + if (sectionExpanded) "▲" else "▼",
                    fontSize = 11.sp,
                    color = Color(0xFF8A93A5),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { sectionExpanded = !sectionExpanded }
                        .padding(vertical = 4.dp)
                )
            }

            if (sectionExpanded) {
                items(queue) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF14202F))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (entry.status) {
                                dev.wearjelly.data.DownloadStatus.RUNNING ->
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                dev.wearjelly.data.DownloadStatus.DONE ->
                                    Text("✓", color = Color(0xFF7BD88F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                dev.wearjelly.data.DownloadStatus.FAILED ->
                                    Text("✗", color = MaterialTheme.colors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                dev.wearjelly.data.DownloadStatus.WAITING ->
                                    Text("…", color = Color(0xFF8A93A5), fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            FadingEdgeText(
                                entry.itemName,
                                fontSize = 11.sp,
                                color = Color.White,
                                fadeColor = Color(0xFF14202F),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        when (entry.status) {
                            dev.wearjelly.data.DownloadStatus.RUNNING -> {
                                Spacer(Modifier.height(3.dp))
                                val fraction = (entry.totalBytes.takeIf { it > 0 }?.let { entry.downloadedBytes.toFloat() / it } )
                                if (fraction != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.DarkGray)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                                .height(4.dp)
                                                .background(MaterialTheme.colors.primary)
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    val prefix = if (entry.estimated) "约 " else ""
                                    Text(
                                        text = "${entry.qualityLabel} · ${prefix}${(fraction * 100).toInt()}% · ${formatBytes(entry.downloadedBytes)}",
                                        fontSize = 9.sp,
                                        color = Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = "${entry.qualityLabel} · 已下载 ${formatBytes(entry.downloadedBytes)}",
                                        fontSize = 9.sp,
                                        color = Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            dev.wearjelly.data.DownloadStatus.WAITING ->
                                Text("排队中 · ${entry.qualityLabel}", fontSize = 9.sp, color = Color(0xFF8A93A5))
                            dev.wearjelly.data.DownloadStatus.DONE -> Unit
                            dev.wearjelly.data.DownloadStatus.FAILED ->
                                Text("缓存失败，请检查网络后重试", fontSize = 9.sp, color = Color(0xFF8A93A5))
                        }
                    }
                }
            }
        }

        if (sortedDownloaded.isEmpty()) {
            item { Text("暂无已缓存歌曲", color = Color.Gray, fontSize = 12.sp) }
        } else {
            itemsIndexed(sortedDownloaded) { _, downloadedSong ->
                val item = downloadedSong.item
                val rowBg = if (pbState.current?.id == item.id) Color(0xFF123B63) else MaterialTheme.colors.surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(rowBg)
                        .combinedClickable(
                            onClick = { viewModel.playTrack(item) },
                            onLongClick = { viewModel.openTrack(item, LibraryQuery(LibraryKind.DOWNLOADS)) }
                        )
                        .padding(horizontal = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        if (pbState.current?.id == item.id) {
                            EqualizerBars(playing = pbState.isPlaying, color = MaterialTheme.colors.primary)
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colors.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            FadingEdgeText(item.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fadeColor = rowBg)
                            FadingEdgeText(
                                "${item.artistText} · ${downloadedSong.qualityLabel}",
                                fontSize = 9.sp, color = Color(0xFF8A93A5), fadeColor = rowBg
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun HistoryScreen(viewModel: AppViewModel) {
    val listState = rememberScalingLazyListState()
    val history by viewModel.history.collectAsState()
    val pbState by viewModel.playbackState.collectAsState()

    AlphabetListScaffold(
        items = history.map { it.item },
        listState = listState,
        headerItems = 1
    ) { scaleModifier ->
    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
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
            itemsIndexed(history) { _, entry ->
                val item = entry.item
                val rowBg = if (pbState.current?.id == item.id) Color(0xFF123B63) else MaterialTheme.colors.surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(rowBg)
                        .combinedClickable(
                            onClick = { viewModel.playTrack(item) },
                            onLongClick = { viewModel.openTrack(item, LibraryQuery(LibraryKind.DOWNLOADS)) }
                        )
                        .padding(horizontal = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        if (pbState.current?.id == item.id) {
                            EqualizerBars(playing = pbState.isPlaying, color = MaterialTheme.colors.primary)
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colors.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            FadingEdgeText(item.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fadeColor = rowBg)
                            FadingEdgeText(item.artistText, fontSize = 9.sp, color = Color(0xFF8A93A5), fadeColor = rowBg)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun SettingsScreen(viewModel: AppViewModel, account: AccountUi?) {
    val listState = rememberScalingLazyListState()
    val currentBitrate by viewModel.bitrate.collectAsState()

    ScalingLazyColumn(
        scalingParams = EdgeScalingParams,
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
internal fun ConfirmScreen(viewModel: AppViewModel, action: ConfirmAction) {
    val title = when (action) {
        ConfirmAction.CLEAR_QUEUE -> "确定清空播放队列？"
        ConfirmAction.SWITCH_SERVER -> "确定切换服务器？\n将清除当前登录"
        ConfirmAction.LOGOUT -> "确定退出登录？"
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
                onClick = { viewModel.confirm(action) },
                modifier = Modifier.size(44.dp),
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                Text("是")
            }
        }
    }
}

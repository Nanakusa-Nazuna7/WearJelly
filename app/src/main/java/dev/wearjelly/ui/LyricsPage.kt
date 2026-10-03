package dev.wearjelly.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import dev.wearjelly.data.LyricsFontSize
import kotlinx.coroutines.delay

/**
 * 歌词页（REQ-LYRICS-PAGE-002）：显示效果与第一版完全一致——
 * ScalingLazyColumn 全量歌词行列表（缩放渐隐动效、当前行 primary/其余 LightGray），
 * 只叠加新功能：
 * - 进入：HomePlayer 左滑（唯一入口）；退出：右滑 / 返回键 → 回 HomePlayer。
 * - 贴边环形进度条（细环、不可拖动）+ 顶部中央时刻。
 * - 点击歌词行 = 预览（D1），2 秒内再点确认 seek；预览行白色、右缘字母条风格时间戳。
 * - 自动滚动跟随播放（手动滚动后 3s 内暂停；预览中暂停）；seek 后立即同步。
 * - 长按空白 → 歌词设置二级菜单（字号三档 / 每歌偏移）。
 */
@Composable
internal fun LyricsPage(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val pbState by viewModel.playbackState.collectAsState()
    val cachedIds by viewModel.cachedTrackIds.collectAsState()
    val offsets by viewModel.lyricsOffsets.collectAsState()
    val fontSize by viewModel.lyricsFontSize.collectAsState()
    val lyricsUi = uiState.lyrics
    val lyrics = lyricsUi.lyrics
    val item = pbState.current
    val offsetMs = offsets[item?.id] ?: 0L

    // 预览确认（D1-B）：首次点行仅预览，2 秒内再点确认 seek，超时取消；切歌时清除
    var preview by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    LaunchedEffect(preview) {
        if (preview != null) {
            delay(2_000)
            preview = null
        }
    }
    LaunchedEffect(item?.id) {
        preview = null
    }

    val listState = rememberScalingLazyListState()
    val startMsList = lyrics?.lines?.map { it.startMs } ?: emptyList()
    val playingIdx = LyricsLogic.currentIndex(startMsList, pbState.positionMs, offsetMs)
    // 顶部时刻/环形进度：预览时显示目标时刻，否则显示播放位置
    val displayMs = preview?.second ?: pbState.positionMs
    val durationMs = pbState.effectiveDurationMs
    val ringFraction = if (durationMs > 0) (displayMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // 自动跟随：当前行变化时滚过去；用户手动滚动后 3s 内、预览中不跟随。
    // followTarget 在组合期与列表内容同一份状态一起计算，避免跨组合竞态导致下标越界
    var lastUserScrollMs by remember { mutableStateOf(0L) }
    val stateItemCount = when {
        lyricsUi.loading -> 1
        lyricsUi.notFound -> 1
        lyricsUi.error != null -> 1
        lyrics == null || lyrics.lines.isEmpty() -> 1
        else -> 0
    }
    // 自动跟随：当前行变化时滚过去；用户手动滚动后 3s 内、预览中不跟随。
    // followTarget 在组合期与列表内容同一份状态一起计算，避免跨组合竞态导致下标越界
    val followTarget = if (playingIdx in (lyrics?.lines?.indices ?: IntRange.EMPTY)) {
        1 + stateItemCount + playingIdx
    } else {
        null
    }
    LaunchedEffect(followTarget, preview) {
        if (preview != null || followTarget == null) return@LaunchedEffect
        if (System.currentTimeMillis() - lastUserScrollMs < 3_000) return@LaunchedEffect
        listState.animateScrollToItem(followTarget)
    }

    fun onLineTap(index: Int, timeMs: Long?) {
        if (timeMs == null) return
        val current = preview
        if (current != null && current.first == index) {
            viewModel.seekTo(timeMs)
            preview = null
        } else {
            preview = index to timeMs
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { viewModel.navigate(AppScreen.LyricsSettings) })
            }
    ) {
        // 叠加层 1：贴边环形进度（细环、不可拖、与全页同一 primary）
        val ringColor = MaterialTheme.colors.primary
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = 2.dp.toPx() + stroke / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = Color(0xFF1B2A40),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (ringFraction > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * ringFraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        // 叠加层 2：顶部中央时刻（预览时白色）+ 预览提示；半透明胶囊底避免与滚动歌词行重叠混排
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCC14202F))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatTime(displayMs),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (preview != null) Color.White else MaterialTheme.colors.primary
            )
            if (preview != null) {
                Text("预览中 · 再点确认跳转", fontSize = 9.sp, color = Color(0xFF8A93A5))
            }
        }

        // 叠加层 3：右缘预览时间戳（字母条同款：粗体白字 + 纵向滑动切换 + 渐隐）
        if (preview != null) {
            AnimatedContent(
                targetState = preview?.second,
                transitionSpec = {
                    val down = (targetState ?: 0L) > (initialState ?: 0L)
                    (slideInVertically { h -> if (down) h / 2 else -h / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { h -> if (down) -h / 2 else h / 2 } + fadeOut())
                },
                label = "previewTime",
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { timeMs ->
                if (timeMs != null) {
                    Text(
                        text = formatTime(timeMs),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 3.dp, height = 22.dp)
                    .background(MaterialTheme.colors.primary, CircleShape)
            )
        }

        // ===== v1 原样：全量歌词行列表（ListScalingParams 缩放渐隐动效与第一版一致）=====
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                // 用户触屏即暂停自动跟随（手指离开再计时 3s）
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        lastUserScrollMs = System.currentTimeMillis()
                        while (true) {
                            val event = awaitPointerEvent()
                            lastUserScrollMs = System.currentTimeMillis()
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                },
            scalingParams = ListScalingParams,
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(
                        text = "歌词",
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.primary
                    )
                    if (item != null && item.id in cachedIds) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(Color(0xFF7BD88F), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { CachedTick() }
                    }
                }
            }

            if (lyricsUi.loading) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            } else if (lyricsUi.notFound) {
                item {
                    Text("纯音乐，请欣赏", color = Color.Gray, style = MaterialTheme.typography.body2)
                }
            } else if (lyricsUi.error != null) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("歌词加载失败", color = MaterialTheme.colors.error, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.loadLyrics(force = true) },
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) {
                            Text("重试", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            } else if (lyrics == null || lyrics.lines.isEmpty()) {
                item {
                    Text("纯音乐，请欣赏", color = Color.Gray, style = MaterialTheme.typography.body2)
                }
            } else {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isCurrent = index == playingIdx
                    val isPreview = preview?.first == index
                    val base = if (isCurrent) {
                        MaterialTheme.typography.body1
                    } else {
                        MaterialTheme.typography.caption1
                    }
                    val style = if (fontSize.scale == 1f) {
                        base
                    } else {
                        base.copy(fontSize = base.fontSize * fontSize.scale)
                    }
                    Text(
                        text = line.text,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = style.copy(
                            fontWeight = if (isCurrent || isPreview) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isPreview -> Color.White
                                isCurrent -> MaterialTheme.colors.primary
                                else -> Color.LightGray
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (line.startMs != null) {
                                    Modifier.clickable { onLineTap(index, line.startMs) }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }
    }
}

/** 歌词设置二级菜单（长按歌词页进入）：字号三档 + 每歌偏移。视觉风格与全站一致。 */
@Composable
internal fun LyricsSettingsScreen(viewModel: AppViewModel) {
    val pbState by viewModel.playbackState.collectAsState()
    val offsets by viewModel.lyricsOffsets.collectAsState()
    val fontSize by viewModel.lyricsFontSize.collectAsState()
    val currentOffset = offsets[pbState.current?.id] ?: 0L

    ScalingLazyColumn(
        scalingParams = ListScalingParams,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = rememberScalingLazyListState(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "歌词设置",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        item {
            Text("字号", fontSize = 11.sp, color = Color(0xFF8A93A5))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LyricsFontSize.entries.forEach { size ->
                    Button(
                        onClick = { viewModel.setLyricsFontSize(size) },
                        modifier = Modifier.height(34.dp),
                        colors = if (fontSize == size) {
                            ButtonDefaults.primaryButtonColors()
                        } else {
                            ButtonDefaults.secondaryButtonColors()
                        }
                    ) {
                        Text(size.displayName, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        item {
            Text("歌词偏移（当前歌曲）", fontSize = 11.sp, color = Color(0xFF8A93A5))
        }
        item {
            val seconds = currentOffset / 1000.0
            Text(
                text = (if (currentOffset > 0) "+" else "") +
                    String.format(java.util.Locale.US, "%.1f", seconds) + "s",
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { viewModel.adjustLyricsOffset(-500L) },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("-0.5s", fontSize = 11.sp, maxLines = 1)
                }
                Button(
                    onClick = { viewModel.adjustLyricsOffset(500L) },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("+0.5s", fontSize = 11.sp, maxLines = 1)
                }
                Button(
                    onClick = { viewModel.resetLyricsOffset() },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A5568))
                ) {
                    Text("重置", fontSize = 11.sp, color = Color.White, maxLines = 1)
                }
            }
        }

        item {
            Text("偏移仅对当前歌曲记忆，重启后仍生效", fontSize = 9.sp, color = Color.Gray)
        }
        item {
            Button(
                onClick = { viewModel.goBack() },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("返回", fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

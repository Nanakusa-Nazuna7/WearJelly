package dev.wearjelly.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 进度条几何换算（纯函数，JVM 单测用）。 */
internal object SeekLogic {
    /** 拖动目标时刻：起点时刻 + 水平位移按条宽线性映射到总时长，夹在 [0, totalMs]。 */
    fun targetMs(startMs: Long, dxPx: Float, barWidthPx: Float, totalMs: Long): Long {
        if (barWidthPx <= 0f || totalMs <= 0L) return startMs.coerceIn(0L, totalMs)
        val delta = (dxPx / barWidthPx * totalMs).roundToLong()
        return (startMs + delta).coerceIn(0L, totalMs)
    }

    /** 拖动位移是否足以视为有效 seek（小于该值视为误触，取消）。 */
    fun isIntentionalDrag(dxPx: Float, minDragPx: Float): Boolean = abs(dxPx) >= minDragPx
}

/**
 * HomePlayer 可拖动进度条（REQ-HOME-SEEK-001）。
 * - 单击不响应（防误触）；左右拖动进入拖动模式，气泡显示目标时间，松手才 seek。
 * - 拖动位移 < 24dp 视为误触，取消并回到原进度。
 * - 触摸区 40dp 高、视觉条 5dp；拖动中该区域独占水平手势（不触发页面左滑切页）。
 * - 纵向意图不消费，让位给列表滚动。
 * - 无时长（直播/未知）不可拖；缓冲且未开播显示不确定动画。
 */
@Composable
internal fun SeekableProgressBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    buffering: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var dragTargetMs by remember { mutableStateOf<Long?>(null) }
    val dragEnabled = durationMs > 0L && !buffering
    // pointerInput 按键重启才有新闭包：必须用 rememberUpdatedState 读取最新位置/回调，
    // 否则拖动起点是过期的 positionMs，seek 会跳到错误位置
    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestOnSeek by rememberUpdatedState(onSeek)

    val displayMs = dragTargetMs ?: positionMs
    val playedFraction = if (durationMs > 0) (displayMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) {
        (bufferedPositionMs.toFloat() / durationMs).coerceIn(playedFraction, 1f)
    } else 0f
    val indeterminate = buffering && positionMs <= 0L

    Column(modifier = modifier.fillMaxWidth(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
        // 拖动气泡：目标时间 / 总时长
        if (dragTargetMs != null && durationMs > 0) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
                val density = LocalDensity.current
                val bubbleWidthPx = with(density) { 96.dp.toPx() }
                // coerceAtLeast 防 min>max 崩溃（条宽小于气泡宽度的极端布局）
                val maxX = (constraints.maxWidth - bubbleWidthPx / 2f).coerceAtLeast(bubbleWidthPx / 2f)
                val xPx = (playedFraction * constraints.maxWidth).coerceIn(bubbleWidthPx / 2f, maxX)
                Text(
                    text = "${formatTime(displayMs)} / ${formatTime(durationMs)}",
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier
                        .offset { IntOffset((xPx - bubbleWidthPx / 2f).roundToInt(), 0) }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC223044))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Spacer(Modifier.height(18.dp))
        }

        // 触摸区（40dp，拖动手势层） + 视觉条（5dp，居中）
        BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp)) {
            val barWidthPx = constraints.maxWidth.toFloat()
            val minDragPx = with(LocalDensity.current) { 24.dp.toPx() }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.DarkGray)
            ) {
                if (indeterminate) {
                    val transition = rememberInfiniteTransition(label = "seekIndeterminate")
                    val phase by transition.animateFloat(
                        initialValue = -0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1100, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "sweep"
                    )
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.25f)
                            .offset(x = with(density) { (phase * barWidthPx).toDp() })
                            .height(5.dp)
                            .background(MaterialTheme.colors.primary)
                    )
                } else {
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
            }

            if (dragEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(40.dp)
                        .pointerInput(durationMs, barWidthPx) {
                            val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startMs = latestPositionMs
                    var dragging = false
                    var lastHapticStep = Long.MIN_VALUE
                    var dx = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!dragging) {
                                if (abs(dy) > slop && abs(dy) > abs(dx)) break // 纵向：让位滚动
                                if (abs(dx) > slop && abs(dx) >= abs(dy)) {
                                    dragging = true
                                    change.consume()
                                }
                            } else {
                                change.consume()
                                val target = SeekLogic.targetMs(startMs, dx, barWidthPx, durationMs)
                                dragTargetMs = target
                                val step = target / 5_000L
                                if (step != lastHapticStep) {
                                    lastHapticStep = step
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                            if (event.changes.none { it.pressed }) {
                                if (dragging) {
                                    val target = dragTargetMs
                                    if (target != null && SeekLogic.isIntentionalDrag(dx, minDragPx)) {
                                        latestOnSeek(target)
                                    }
                                    // 小位移=误触：不 seek，松手后自动回到 positionMs
                                    dragTargetMs = null
                                }
                                break
                            }
                        }
                    } finally {
                        // 手势中途协程被取消（如切歌导致 pointerInput 重启）时，气泡不得残留
                        dragTargetMs = null
                    }
                }
                        }
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(displayMs), fontSize = 10.sp, color = Color.Gray)
            Text(text = formatTime(durationMs), fontSize = 10.sp, color = Color.Gray)
        }
    }
}

package dev.wearjelly.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.DeviceUnknown
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.SwipeToDismissBox
import dev.wearjelly.playback.PlaybackState
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.material3.VolumeScreen
import com.google.android.horologist.audio.ui.material3.components.AudioOutputUi
import com.google.android.horologist.audio.ui.material3.components.DeviceButton

/**
 * 音量/音频输出组件（docs/horologist-migration.md §5 契约，t5 实装）：
 *
 * 1. [toVolumeUiState]：PlaybackState → VolumeUiState 百分比伪刻度（**唯一**音量状态映射，
 *    不引入 VolumeViewModel/VolumeRepository，避免与 PlaybackState.volumePercent 双轨漂移）；
 * 2. [VolumeStepControls]：屏上音量 ± 硬路径——唯一操作链仍是
 *    viewModel.adjustVolume(direction) → PlaybackConnection.adjustVolume → AudioManager
 *    .adjustStreamVolume(STREAM_MUSIC)，playback 层零改动；
 * 3. [VolumePanel]/[VolumeOverlay]：音量面板——Horologist VolumeScreen :271 内核重载
 *    （Stepper 上下拖动调增减 + VolumeLevelIndicator 弧形指示）+ 自定义 content
 *    （"音量 N%" 文本 + [AudioOutputButton] 设备按钮）；[VolumeOverlay] 供 t15 一行挂全屏
 *    覆盖层，返回手势关闭（与 Horologist VolumeRoute 用 back 关闭等价）；
 * 4. [rememberSystemAudioOutputUi]/[AudioOutputButton]：音频设备切换 UI——AudioManager
 *    .getDevices(GET_DEVICES_OUTPUTS) 枚举系统输出映射为 AudioOutputUi，DeviceButton 展示；
 *    Android 无进程内切换 STREAM_MUSIC 输出的公开 API，点击跳系统蓝牙设置
 *    （连接/断开蓝牙音频即切换输出），见 [launchSystemOutputSettings]。
 *
 * 表冠 volumeRotaryBehavior 未接：可选渐进增强（设备无表冠，不作验收）；接入需把 rotary 的
 * 绝对目标音量符号化为 direction（或给 PlaybackConnection 加 setStreamVolume——会触碰播放层），
 * 与 t1 §5 结论一致，默认不做。
 *
 * 入口对接：t4-player 的 onVolumeClick 回调口 → t15 以 VolumeOverlay(viewModel, visible,
 * onDismissRequest) 挂在 AppScreen.Player 分发处之上。
 */

/** PlaybackState → Horologist VolumeUiState 百分比伪刻度（t1 §5 契约；max=100 使 Stepper 步进=1%）。 */
internal fun PlaybackState.toVolumeUiState(): VolumeUiState =
    VolumeUiState(current = volumePercent.coerceIn(0, 100), max = 100, min = 0)

/**
 * 屏上音量 ±（硬路径）：原播放页控制行的音量按键对，任何屏可复用。
 * 按下 → [AppViewModel.adjustVolume] → PlaybackConnection.adjustVolume（不动 playback/）。
 * [showLevelText] 打开时下方显示 "音量 N%"（原播放页 :2473 样式）。
 */
@Composable
internal fun VolumeStepControls(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    showLevelText: Boolean = true,
) {
    val pbState by viewModel.playbackState.collectAsState()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = { viewModel.adjustVolume(-1) },
                modifier = Modifier.size(36.dp),
                colors = solidButtonColors(Color(0xFF3A3F4B)),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "减小音量",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { viewModel.adjustVolume(1) },
                modifier = Modifier.size(36.dp),
                colors = solidButtonColors(Color(0xFF3A3F4B)),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "增大音量",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (showLevelText) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "音量 ${pbState.volumePercent.coerceIn(0, 100)}%",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 枚举系统音频输出（AudioManager.getDevices(GET_DEVICES_OUTPUTS)）→ [AudioOutputUi]。
 * 优先级：蓝牙（连接即 A2DP 当前输出）→ 有线/USB → 手表扬声器 → 任意 sink；无 sink 视为未连接。
 * 设备名取 AudioDeviceInfo.productName，空则用分类兜底文案；图标风格与 Horologist
 * toAudioOutputUi 一致（手表=Icons.Rounded.Watch）。
 */
@Composable
internal fun rememberSystemAudioOutputUi(): AudioOutputUi {
    val context = LocalContext.current
    return remember(context) { systemAudioOutputUi(context) }
}

private fun systemAudioOutputUi(context: Context): AudioOutputUi {
    val audioManager = context.getSystemService(AudioManager::class.java)
    val active = audioManager
        ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        ?.filter { it.isSink }
        ?.let { sinks ->
            sinks.firstOrNull { it.type in BT_OUTPUT_TYPES }
                ?: sinks.firstOrNull { it.type in WIRED_OUTPUT_TYPES }
                ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?: sinks.firstOrNull()
        }
    val productName = active?.productName?.toString()?.trim().orEmpty()
    return when {
        active == null -> AudioOutputUi(
            displayName = "未连接输出设备",
            imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
            isConnected = false,
        )
        active.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputUi(
            displayName = "手表扬声器",
            imageVector = Icons.Rounded.Watch,
            isConnected = true,
        )
        active.type in BT_OUTPUT_TYPES -> AudioOutputUi(
            displayName = productName.ifEmpty { "蓝牙音频设备" },
            imageVector = Icons.Rounded.BluetoothAudio,
            isConnected = true,
        )
        active.type in WIRED_OUTPUT_TYPES -> AudioOutputUi(
            displayName = productName.ifEmpty { "有线耳机" },
            imageVector = Icons.Rounded.Headphones,
            isConnected = true,
        )
        else -> AudioOutputUi(
            displayName = productName.ifEmpty { "输出设备" },
            imageVector = Icons.Rounded.DeviceUnknown,
            isConnected = true,
        )
    }
}

/**
 * 输出类型分组（AudioDeviceInfo 常量均为编译期内联 int，低版本设备上仅不匹配，无 NewApi 风险）。
 * t27/F4：BLE 输出补全——经 AOSP AudioDeviceInfo/AudioSystem 全量源码与 android-34/35/36/37
 * android.jar 逐一核实，并不存在 TYPE_BLE_BUNDLE 公开符号（t19 审查笔误；真实 BLE 输出符号为
 * TYPE_BLE_HEADSET/SPEAKER/BROADCAST/HEARING_AID/CENTRAL/CENTRAL_BROADCAST），此处按验收意图
 * 以字面 int 落地：TYPE_BLE_BUNDLE 取 LE Audio 广播真实类型 TYPE_BLE_BROADCAST=30 之别名，并
 * 补齐 33/34/35 三类新 BLE 输出（android-37.0 android.jar 首见，同为字面 int）。此前缺失使这类
 * 设备不命中蓝牙分支、在带内置扬声器的机器上先选中内置扬声器（:157 优先级）而误显「手表扬声器」。
 */
private const val TYPE_BLE_BUNDLE = 30 // = AudioDeviceInfo.TYPE_BLE_BROADCAST
private const val TYPE_BLE_HEARING_AID = 33 // = AudioDeviceInfo.TYPE_BLE_HEARING_AID
private const val TYPE_BLE_CENTRAL = 34 // = AudioDeviceInfo.TYPE_BLE_CENTRAL
private const val TYPE_BLE_CENTRAL_BROADCAST = 35 // = AudioDeviceInfo.TYPE_BLE_CENTRAL_BROADCAST

private val BT_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    TYPE_BLE_BUNDLE,
    TYPE_BLE_HEARING_AID,
    TYPE_BLE_CENTRAL,
    TYPE_BLE_CENTRAL_BROADCAST,
)

private val WIRED_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_HEADSET,
)

/**
 * 打开系统输出设置：蓝牙设置（连接/断开蓝牙音频 = 切换音乐输出）→ 声音设置 → 总设置兜底。
 * Android 未开放进程内切换 STREAM_MUSIC 输出路由的 API，故走系统页（等价 Horologist
 * VolumeViewModel.launchOutputSelection 的系统侧行为）。
 */
internal fun launchSystemOutputSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        Intent(Settings.ACTION_SOUND_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in intents) {
        runCatching {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onSuccess { return }
    }
}

/**
 * 音频设备切换按钮：Horologist [DeviceButton] 薄封装——图标+设备名+连接态，点击回调
 * [onAudioOutputClick]（面板内默认接 [launchSystemOutputSettings]）。
 */
@Composable
internal fun AudioOutputButton(
    onAudioOutputClick: () -> Unit,
    audioOutputUi: AudioOutputUi,
    modifier: Modifier = Modifier,
    volumeDescription: String = "媒体音量",
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        secondaryContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        iconColor = MaterialTheme.colorScheme.onPrimary,
    ),
) {
    DeviceButton(
        volumeDescription = volumeDescription,
        deviceName = audioOutputUi.displayName,
        icon = { Icon(imageVector = audioOutputUi.imageVector, contentDescription = null) },
        onAudioOutputClick = onAudioOutputClick,
        modifier = modifier,
        colors = colors,
    )
}

/**
 * 音量面板内容：Horologist VolumeScreen :271 内核重载——Stepper 上下拖动/点击增减
 * （onValueChange 按方向分派 → adjustVolume，百分比伪刻度步进 1%）+ VolumeLevelIndicator
 * 弧形音量指示（showVolumeIndicator=true）。自定义 content = "音量 N%" 文本 + [AudioOutputButton]
 * （当前系统输出，点击打开系统设置切换）。关闭由调用方处理（[VolumeOverlay] 用返回手势）。
 */
@Composable
internal fun VolumePanel(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pbState by viewModel.playbackState.collectAsState()
    val outputUi = rememberSystemAudioOutputUi()
    VolumeScreen(
        volume = { pbState.toVolumeUiState() },
        increaseVolume = { viewModel.adjustVolume(1) },
        decreaseVolume = { viewModel.adjustVolume(-1) },
        modifier = modifier,
        showVolumeIndicator = true,
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 18.dp),
            ) {
                Text(
                    text = "音量 ${pbState.volumePercent.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.height(4.dp))
                AudioOutputButton(
                    onAudioOutputClick = { launchSystemOutputSettings(context) },
                    audioOutputUi = outputUi,
                    volumeDescription = "音量 ${pbState.volumePercent.coerceIn(0, 100)}%",
                )
            }
        },
    )
}

/**
 * 音量面板全屏覆盖层（t15 一行接线：`VolumeOverlay(viewModel, showVolume) { showVolume = false }`）。
 * 不透明全屏（面板 Stepper 占满可视区，无遮罩外点区域），返回手势/系统 back 关闭——
 * 与 Horologist VolumeRoute 依赖 back 导航关闭等价；本组件内 BackHandler 后注册者胜出，
 * 可覆盖 WearJellyApp 的页面级 back 处理。
 */
@Composable
internal fun VolumeOverlay(
    viewModel: AppViewModel,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    BackHandler(onBack = onDismissRequest)
    SwipeToDismissBox(
        onDismissed = onDismissRequest,
        userSwipeEnabled = true,
        modifier = modifier.fillMaxSize(),
        content = { isBackground ->
            if (isBackground) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    VolumePanel(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
    )
}

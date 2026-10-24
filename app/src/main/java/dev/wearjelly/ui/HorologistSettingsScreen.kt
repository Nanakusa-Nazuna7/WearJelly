package dev.wearjelly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * t12 设置页（docs/horologist-migration.md §6 映射 #14，M3 表单）：
 * - §6 契约 = Button 行；音质选择保持 ✓ 前缀（不换 RadioButton，最小回归）；
 * - 全部设置项零丢失（逐项核对原 :2883-2996，本屏原状无开关/滑条/数字输入类设置项，
 *   音质即按钮列表）：账号信息块（当前用户/用户名/服务器/URL，account==null 时隐藏）、
 *   "在线播放音质"标签、[dev.wearjelly.data.AudioBitrate] 全项按钮列表（选中=主色默认配色
 *   且文案加 ✓ 前缀、未选=surfaceContainer，点击 setAudioBitrate）、
 *   "转码仅影响新开始的在线播放"提示、切换服务器（→requestConfirmation(SWITCH_SERVER)）、
 *   退出登录（solidButtonColors(0xFF6B1B1B) → requestConfirmation(LOGOUT)）；
 * - 本屏无导航调用（确认页由 [AppViewModel.requestConfirmation] 内部入栈），签名与原屏一致；
 *   业务动作仍走 AppViewModel，不动 playback/。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.Settings -> HorologistSettingsScreen(viewModel, uiState.account)
 * ```
 */
@Composable
internal fun HorologistSettingsScreen(viewModel: AppViewModel, account: AccountUi?) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val currentBitrate by viewModel.bitrate.collectAsState()

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
                Text("设置", maxLines = 1)
            }
        }

        if (account != null) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                        .transformedHeight(this, transformationSpec)
                ) {
                    Text("当前用户", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(account.userName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("服务器", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(account.serverUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Text(
                text = "在线播放音质",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                    .transformedHeight(this, transformationSpec)
            )
        }

        items(dev.wearjelly.data.AudioBitrate.entries) { bitrate ->
            Button(
                onClick = { viewModel.setAudioBitrate(bitrate) },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = if (currentBitrate == bitrate) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                },
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(
                    text = if (currentBitrate == bitrate) "✓ ${bitrate.displayName}" else bitrate.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }

        item {
            Text(
                text = "转码仅影响新开始的在线播放",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                    .transformedHeight(this, transformationSpec)
            )
        }

        item {
            Button(
                onClick = { viewModel.requestConfirmation(ConfirmAction.SWITCH_SERVER) },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text("切换服务器")
            }
        }

        item {
            Button(
                onClick = { viewModel.requestConfirmation(ConfirmAction.LOGOUT) },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = solidButtonColors(Color(0xFF6B1B1B)),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text("退出登录", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
    }
}

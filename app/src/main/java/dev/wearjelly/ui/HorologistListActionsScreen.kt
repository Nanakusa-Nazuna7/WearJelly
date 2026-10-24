package dev.wearjelly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * t11 列表级长按菜单（docs/horologist-migration.md §6 映射 #12，M3 通用件 Button 列表）：
 * - Horologist 无菜单/对话框组件 → 用 M3 通用件规范化：ListHeader 标题 + 按 [ListMenuAction]
 *   配色的 Button 列表；菜单项集合与顺序仍由 [ListMenu.items] 按类型决定，功能零丢失；
 * - 配色零丢失：PLAY_ALL=主色默认、CACHE_ALL=solidButtonColors(0xFF4A5568)+onBackground、
 *   DELETE_CACHES=solidButtonColors(0xFF4A1515)+onBackground、其余=surfaceContainer；
 *   SHUFFLE_ALL 保留 Shuffle 图标行；末尾"取消"(38dp) 保留；
 * - 动作统一走 [AppViewModel.onListMenuAction]（ViewModel 业务入口，其内部可能异步导航，
 *   属播放/业务层，不动 playback/）；导航只经回调（本文件不出现 viewModel.navigate/goBack），
 *   取消 = onGoBack()，由外壳（t15）接线；
 * - 不引 Horologist 组件（§6 契约"操作/表单屏纯 M3"）。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.ListActions -> HorologistListActionsScreen(
 *     viewModel, screen, onGoBack = viewModel::goBack,
 * )
 * ```
 */
@Composable
internal fun HorologistListActionsScreen(
    viewModel: AppViewModel,
    screen: AppScreen.ListActions,
    onGoBack: () -> Unit,
) {
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
                Text(screen.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        items(screen.menu.items(offline = viewModel.isOfflineMode)) { entry ->
            val (background, textColor) = when (entry.action) {
                ListMenuAction.PLAY_ALL -> null to null
                ListMenuAction.CACHE_ALL -> Color(0xFF4A5568) to MaterialTheme.colorScheme.onBackground
                ListMenuAction.DELETE_CACHES -> Color(0xFF4A1515) to MaterialTheme.colorScheme.onBackground
                else -> null to null
            }
            Button(
                onClick = { viewModel.onListMenuAction(screen.menu, entry.action) },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = when (entry.action) {
                    ListMenuAction.PLAY_ALL -> ButtonDefaults.buttonColors()
                    else -> background?.let {
                        solidButtonColors(it)
                    } ?: ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                },
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                if (entry.action == ListMenuAction.SHUFFLE_ALL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(entry.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor ?: Color.Unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            Button(
                onClick = { onGoBack() },
                modifier = Modifier.fillMaxWidth().height(38.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text("取消", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    }
}

package dev.wearjelly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * t11 确认页（docs/horologist-migration.md §6 映射 #15，M3 通用件）：
 * - §6 契约"是/否 44dp Button 对；全屏语义保留（不换 AlertDialog）"——Horologist 无对话框组件，
 *   M3 ConfirmationDialog 会把全屏确认改为对话框语义，故保留原全屏 Column 布局：
 *   4 条 [ConfirmAction] 文案 + 居中 44dp 否/是按钮对，确认流程零丢失；
 * - 导航只经回调（本文件不出现 viewModel.navigate/goBack）：否 = onGoBack()；
 *   是 = [AppViewModel.confirm]（ViewModel 业务入口，内部自行 goBack + 执行动作，不动 playback/）。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.Confirm -> HorologistConfirmScreen(
 *     viewModel, screen.action, screen.query, screen.menu,
 *     onGoBack = viewModel::goBack,
 * )
 * ```
 */
@Composable
internal fun HorologistConfirmScreen(
    viewModel: AppViewModel,
    action: ConfirmAction,
    query: LibraryQuery? = null,
    menu: ListMenu? = null,
    onGoBack: () -> Unit,
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
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onGoBack() },
                modifier = Modifier.size(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text("否")
            }
            Button(
                onClick = { viewModel.confirm(action, query, menu) },
                modifier = Modifier.size(44.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("是")
            }
        }
    }
}

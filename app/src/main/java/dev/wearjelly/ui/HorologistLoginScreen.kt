package dev.wearjelly.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
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
 * t12 登录页（docs/horologist-migration.md §6 映射 #1，M3 表单）：
 * - §6 契约 = TLC + Button + 自绘 [LoginTextField]（Wear Material3 无 OutlinedTextField，
 *   按官方迁移指引维持自绘）+ 20dp CircularProgressIndicator；
 * - 登录流程零丢失：标题"登录 Jellyfin"、错误提示（error 色，出现即显示）、
 *   服务器地址（Uri 键盘/Next）、用户名（Next）、密码（PasswordVisualTransformation/Password/Done）
 *   三输入框、登录按钮（enabled=!submitting，提交中转圈、否则"登录"，点击先 clearFocus 再 viewModel.login()）；
 * - 本屏无导航调用（登录成功由 [AppViewModel] 内部会话处理），签名与原屏一致；
 * - [LoginTextField] 是 WearJellyApp.kt 同名私有助手的逐字等价副本（该文件本任务不可改，private 跨文件不可见）。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.Login -> HorologistLoginScreen(viewModel, uiState.login)
 * ```
 */
@Composable
internal fun HorologistLoginScreen(viewModel: AppViewModel, loginUi: LoginUi) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val focusManager = LocalFocusManager.current

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
                Text("登录 Jellyfin", maxLines = 1)
            }
        }

        if (loginUi.error != null) {
            item {
                Text(
                    text = loginUi.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                        .transformedHeight(this, transformationSpec)
                )
            }
        }

        item {
            LoginTextField(
                value = loginUi.server,
                onValueChange = { viewModel.changeServer(it) },
                label = "服务器地址",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                    .transformedHeight(this, transformationSpec)
            )
        }

        item {
            LoginTextField(
                value = loginUi.username,
                onValueChange = { viewModel.changeUsername(it) },
                label = "用户名",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                    .transformedHeight(this, transformationSpec)
            )
        }

        item {
            LoginTextField(
                value = loginUi.password,
                onValueChange = { viewModel.changePassword(it) },
                label = "密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding)
                    .transformedHeight(this, transformationSpec)
            )
        }

        item {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login()
                },
                enabled = !loginUi.submitting,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(40.dp)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.buttonColors(),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                if (loginUi.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("登录", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    }
}

/**
 * 登录表单输入框（自 WearJellyApp.kt:361 逐字复制的等价私有副本）：手机版 Compose Material 的
 * OutlinedTextField 在 Wear Material3 中没有对应组件，按官方迁移指引用 foundation BasicTextField
 * + 自绘描边装饰盒还原原样式（聚焦描边 primary、未聚焦描边 DarkGray、白色输入文字、
 * 空值时显示灰色标签）。
 */
@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (focused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (value.isEmpty()) {
                    Text(label, style = MaterialTheme.typography.bodyExtraSmall, color = MaterialTheme.colorScheme.outline)
                }
                innerTextField()
            }
        }
    )
}

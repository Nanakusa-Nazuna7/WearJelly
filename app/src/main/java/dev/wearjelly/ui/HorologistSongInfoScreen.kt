package dev.wearjelly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.wearjelly.data.JellyfinItem

/**
 * t10 歌曲信息（docs/horologist-migration.md §6 映射 #13，M3 · Horologist EntityScreen 风格）：
 * - EntityScreen 风格 = 单一 headerContent（ListHeader 标题区，文案 [Copy.SONG_INFO]）→
 *   内容区六行信息卡片（10dp 圆角 + background 底色，§6 契约的"六行卡片"）；
 *   Horologist `EntityScreen` 内部绑定 ScalingLazyColumn，与本仓 AppScaffold+ScreenScaffold+TLC
 *   单层架构冲突，故取其风格不取其组件；
 * - 详情字段功能零丢失：名称/艺人/专辑（空则"未知"）/时长（[formatTime]）/格式（空则"未知"）/ID；
 * - 本屏无导航动作（返回由外壳 SwipeToDismissBox 承担），无回调参数；
 * - 私有 [SongInfoRow] 是 WearJellyApp.kt 同名私有助手的等价副本（该文件本任务不可改）。
 *
 * t15 接线示例：
 * ```
 * is AppScreen.SongInfo -> HorologistSongInfoScreen(screen.item)
 * ```
 */
@Composable
internal fun HorologistSongInfoScreen(item: JellyfinItem) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                Text(Copy.SONG_INFO, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item { SongInfoRow("名称", item.name, Modifier.minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding).transformedHeight(this, transformationSpec)) }
        item { SongInfoRow("艺人", item.artistText, Modifier.minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding).transformedHeight(this, transformationSpec)) }
        item { SongInfoRow("专辑", item.album ?: "未知", Modifier.minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding).transformedHeight(this, transformationSpec)) }
        item { SongInfoRow("时长", formatTime(item.durationMs), Modifier.minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding).transformedHeight(this, transformationSpec)) }
        item { SongInfoRow("格式", item.container ?: "未知", Modifier.minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding).transformedHeight(this, transformationSpec)) }
        item { SongInfoRow("ID", item.id, Modifier.minimumVerticalContentPadding(TextDefaults.minimumTopListContentPadding, TextDefaults.minimumBottomListContentPadding).transformedHeight(this, transformationSpec)) }
    }
    }
}

/** 信息行卡片：label（bodyExtraSmall/outlineVariant）+ value（labelSmall/onBackground，最多两行省略）。 */
@Composable
private fun SongInfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyExtraSmall, color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

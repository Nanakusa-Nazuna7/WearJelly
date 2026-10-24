# Horologist M3 全量迁移决策文档（t1-research）

- 团队：horologist-fullui-r3 · 作者：architect（t1-research 交付物）
- 证据基线：horologist **0.8.4-alpha** sources jar 已解包至 `D:\wearmusic\.horologist-src\`（含 horologist-media-ui-material3 / horologist-audio-ui-material3 / horologist-media-ui-model / horologist-audio-ui-model 四模块全量源码），本文引用的 `Horologist 源码` 行号均指该目录；版本事实以 repo1.maven.org 的 maven-metadata.xml / `.module` 文件为准。
- 结论速览：
  1. 引入 `horologist-media-ui-material3:0.8.4-alpha` + `horologist-audio-ui-material3:0.8.4-alpha`（仅 Maven Central）；horologist-composables 不显式声明（api 传递已带）。
  2. minSdk 25→26 仅改 `app/build.gradle.kts` 一处。
  3. 媒体导航**退回**：`MediaRoute` 为 sealed interface 且框架 pager 写死 2 页，无法承载 14 屏导航；"框架化"落到组件层（逐屏组件替换，第 6 节）。
  4. PlayerUiState 由 `PlaybackState` 纯函数映射（第 4 节契约）；PlayerScreen 优先用 slots 重载，不强依赖 PlayerUiState/PlayerViewModel。
  5. 音量保持 `adjustVolume` 路径，不引入 VolumeViewModel；VolumeScreen 用低层重载 + `VolumeUiState(current=volumePercent, max=100)` 百分比伪刻度。
  6. 14 屏逐屏映射见第 6 节；Player/Queue 是 Horologist 组件主战场，其余为 M3 通用件。

## 1. 依赖选型与版本

### 1.1 直接依赖（t3 落地片段）

```kotlin
// app/build.gradle.kts dependencies
implementation("com.google.android.horologist:horologist-media-ui-material3:0.8.4-alpha")
implementation("com.google.android.horologist:horologist-audio-ui-material3:0.8.4-alpha")
// 可选，仅当需要 CoilPaintable 适配 MediaUiModel.Ready.artwork 时：
// implementation("com.google.android.horologist:horologist-images-coil:0.8.4-alpha")

// Horologist 公开 API 全量 @ExperimentalHorologistApi 标注，统一 opt-in：
// kotlin { freeCompilerArgs += "-opt-in=com.google.android.horologist.annotations.ExperimentalHorologistApi" }
```

- **horologist-composables：不显式引入**。media-ui-material3 的 api 变体已传递 `horologist-composables:0.8.4-alpha`；应用源码确需直接 import 其 API 时再补显式声明（同版本）。
- **horologist-images-coil 是 media-ui-material3 的 runtimeOnly 传递件**（编译期不可见）。默认不用（mediaDisplay 继续用应用自带的 coil-compose 2.7.0 AsyncImage）；要用 `CoilPaintable` 才显式加。
- 无新插件、无 version catalog（本仓无 libs.versions.toml，版本硬编码于 app/build.gradle.kts 的现状保持）。

### 1.2 版本事实（权威：repo1.maven.org）

- 两个 M3 模块与 horologist-composables / horologist-media-ui 的 `<latest>=<release>=0.8.4-alpha`；版本线 `0.7.13-beta / 0.7.14-beta / 0.7.15 / 0.8.0-alpha … 0.8.4-alpha`；**M3 模块自 0.7.13-beta 才诞生**；stable 线终点 **0.7.15 无 M3 模块** → M3 迁移必须取 0.8.x-alpha，选最新 **0.8.4-alpha**（2026-08-10 发布）。
- 仅发布在 Maven Central（dl.google.com 无该组产物）；`settings.gradle.kts` dependencyResolutionManagement 已含 mavenCentral()（FAIL_ON_PROJECT_REPOS）→ **仓库零改动**。
- 教训：search.maven.org 的 solrsearch 索引对 horologist 全部显示 latest=0.7.14-beta，陈旧不可信，勿据此选型。

### 1.3 传递依赖与版本对齐（0.8.4-alpha `.module` 实测）

- 库编译环境：Gradle 9.7.0、Kotlin **2.4.10**、compose-bom **2026.04.01**（platform）、androidx.wear.compose:\* **1.6.2**、androidx.navigation3:\* **1.1.5**。
- app 现值更高，Gradle 冲突解析取高者：Kotlin 2.4.20（stdlib 2.4.10 元数据向后兼容）、compose-bom 2026.09.00、wear-compose **1.7.0**、compileSdk 37、AGP 9.1.1 → 兼容预期成立，最终以 assembleDebug 验收。
- media-ui-material3 **api** 变体依赖：horologist-annotations / horologist-media / horologist-tiles / horologist-composables / horologist-audio-ui-model / horologist-media-ui-model、wear compose-material3 + foundation + navigation + navigation3 1.6.2、navigation3-runtime/ui 1.1.5。
- **runtime** 追加（不进编译类路径）：horologist-audio、horologist-compose-layout、horologist-images-coil、lottie-compose 6.7.1、constraintlayout-compose 1.1.2、androidx.wear:wear 1.4.0、tiles 1.6.2、protolayout-material 1.4.2、watchface-complications-data-source-ktx 1.3.0、kotlinx-serialization-core 1.11.0、material-icons-core/extended 1.7.8、graphics-shapes 1.1.0、lifecycle 2.10.0。
- **M2（`androidx.wear.compose:compose-material:1.6.2`）传递不可避免**：horologist-compose-layout（经 media-ui-material3 runtime 传递）与 horologist-composables（**api** 传递）的 api 依赖均含它。处理口径（即验收基准）：
  - app/build.gradle.kts **不显式声明** compose-material；
  - 应用源码（app/src/main/java/\*\*）**零** `androidx.wear.compose.material` 导入（M3 一律 `androidx.wear.compose.material3`）；
  - 传递性 M2 与 M3 共存于 classpath（不同 maven 构件：compose-material vs compose-material3），可接受、不算违规。
- **verifyNoGms 安全**：该自定义任务在 releaseRuntimeClasspath 禁止 com.google.android.gms / firebase / billingclient / play / com.android.support 组；horologist（com.google.android.horologist）与上述全部传递件均不在禁组 → 不触发。
- lint：`abortOnError=true` 只影响 release lintVital（assembleDebug 不跑 lint）；release 阶段若报 horologist 传递件的 lint 条目，按条目单独处置。

## 2. minSdk 25 → 26

- **单点变更**：`app/build.gradle.kts` `minSdk = 25` → `minSdk = 26`。AndroidManifest.xml（40 行）无 uses-sdk/minSdk 属性 → manifest 侧无需改动。
- 原因：Horologist Min SDK (Wear) = 26，其 AAR manifest 声明 minSdk 26；不升级则 manifest merger 直接构建失败（uses-sdk:minSdkVersion 25 cannot be smaller than 26 declared in library）。
- lint abortOnError=true：升级后 horologist 内部 API 级守卫（如 VolumeViewModel.kt:113 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` 才震动）不会在应用侧触发 NewApi。
- 生态核查：media3 1.4.1、wear-compose 1.7.0、room 2.8.5、koin 3.5.6、coil 2.7.0 均无 >26 的 minSdk 要求 → 无二次连锁升级。
- 影响：API 25 设备（Wear OS 2.x 老表）不再可安装；团队目标已定 minSdk 26，接受并记录。

## 3. 媒体导航可行性：不可行 → 退回（判据记录）

Horologist M3 **确实提供**媒体导航框架件（0.8.4-alpha 实测源码）：

- `navigation/MediaPlayerScaffold.kt:57` `MediaPlayerScaffold(volumeViewModel, playerScreen, libraryScreen, categoryEntityScreen: (id: String, name: String) -> Unit, mediaEntityScreen: () -> Unit, playlistsScreen, settingsScreen, deepLinkPrefix, backStack: NavBackStack<MediaRoute>, modifier, volumeScreen = { VolumeScreen(volumeViewModel) }, timeText = { TimeText() }, additionalEntries)`：内部 AppScaffold + navigation3 `NavDisplay` + `rememberSwipeDismissableSceneStrategy()`；缺省 entry：PlayerRoute→PlayerLibraryPagerScreen（2 页 pager：player + library，第 0 页带 VolumeLevelIndicator 滚动指示）、VolumeRoute→ScreenScaffold{VolumeScreen}、CollectionsRoute/SettingsRoute，另有 `additionalEntries: EntryProviderScope<MediaRoute>.() -> Unit`。
- `navigation/NavigationScreens.kt:126` `public sealed interface MediaRoute : NavKey`；具体键：`PlayerRoute(page: Int)`、`VolumeRoute`、`MediaItemRoute(id, collectionId?)`、`LoginRoute`、`SettingsRoute`、`CollectionsRoute`、`CollectionRoute(id, name)`、`CustomRoute(route: String)`。
- `navigation/MediaNavController.kt`：`NavBackStack<MediaRoute>` 扩展 navigateToPlayer/ToLibrary/ToVolume/ToCollection(s)/ToSettings/ToMediaItem。
- `screens/playerlibrarypager/PlayerLibraryPagerScreen.kt:41`：HorizontalPagerScaffold + `rememberPagerState(initialPage = 0, pageCount = { 2 })`。

**退回判据（4 条，均实测，即 t14"不可行退回"的依据）**：

1. `MediaRoute` 是 **sealed interface**：应用模块无法定义 LyricsRoute / BatchActionsRoute / SongInfoRoute 等键（Kotlin sealed 限同模块实现）；唯一逃生门 `CustomRoute(route: String)` 是字符串 deeplink 语义（MediaPlayerScaffold.kt:105 `DeepLinkRequest.fromUriString("app://" + route)`），与现有类型化导航（LibraryQuery/JellyfinItem 直接传参）根本冲突。
2. `PlayerLibraryPagerScreen` 写死 `pageCount = { 2 }`：Lyrics 无法作为第三页纳入框架 pager；现有 Player↔Lyrics 340ms AnimatedContent 左右滑转场无法映射。
3. 内置 entry 的 `mediaEntityScreen: () -> Unit` **无参**（MediaPlayerScaffold.kt:62、:100）：Track/SongInfo 携带完整 JellyfinItem 的需求只能绕外部单例，破坏现有状态流。
4. 强制注入 `volumeViewModel: VolumeViewModel`（MediaPlayerScaffold.kt:58 参数必填）：与第 5 节"不引入 VolumeViewModel"决策直接冲突。

**结论（t14 按此执行，无需二次调研）**：导航壳退回现有 `AppUiState.backStack` + AnimatedContent/SwipeToDismissBox（其语义已等价 rememberSwipeDismissableSceneStrategy 的滑动返回）；"媒体导航框架化"降级为**组件层框架化**——逐屏采用 Horologist M3 组件（第 6 节）。若后续版本开放 MediaRoute（非 sealed）或可变页数 pager，可重评。

## 4. PlayerUiState 绑定契约（供 t4-player）

前提：底层播放逻辑不动。**不引入** horologist PlayerRepository / PlayerViewModel / PlayerUiStateProducer（`PlayerViewModel.kt:28` 要求 `PlayerRepository` 实现，且 Producer 组合 connected/availableCommands/currentMedia/latestPlaybackState 四个 repository flow，与"播放逻辑不动"冲突）。

**首选：PlayerScreen slots 重载**（`media/ui/material3/screens/player/PlayerScreen.kt:140`）：

```kotlin
@Composable public fun PlayerScreen(
  mediaDisplay: @Composable () -> Unit,
  controlButtons: @Composable () -> Unit,
  buttons: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  background: @Composable BoxScope.() -> Unit = {},
)
```

布局：三段 SpaceBetween（mediaDisplay 底对齐于顶部区、controlButtons 居中、buttons 在底部 weight(1f) 区居中；各段高度按 `LocalConfiguration.isLargeScreen` 常量自适应）。slots 重载不要求 PlayerUiState/PlayerViewModel/VolumeViewModel——收藏心形按钮（见 docs/favorite-design.md，t13）、音量/队列按钮放 `buttons` slot；`background` slot 可选 ArtworkImageBackground / RadialBackground（增强项，非验收）。
（备选 stateful 重载 PlayerScreen.kt:72 `PlayerScreen(playerViewModel: PlayerViewModel, volumeViewModel: VolumeViewModel, mediaDisplay: MediaDisplay, controlButtons: ControlButtons, buttons: SettingsButtons, background, focusRequester)` 自带 rotary 音量绑定，但需 PlayerRepository 实现 + VolumeViewModel，不推荐。）

**兼容映射**（t4 若想复用 DefaultPlayerScreenControlButtons / MediaControlButtons 的进度环，按此纯函数构造）：

```kotlin
// 建议 ui/PlayerUiStateMapping.kt（internal）
internal fun PlaybackState.toPlayerUiState(): PlayerUiState
```

| PlayerUiState 字段 | 来自 PlaybackState（pb） |
|---|---|
| connected | pb.connected |
| playing | pb.isPlaying |
| playPauseEnabled / playEnabled / pauseEnabled | pb.connected && pb.current != null |
| seekToPreviousEnabled / seekToNextEnabled | pb.connected && pb.queue.isNotEmpty() |
| seekBackEnabled / seekForwardEnabled / seekInCurrentMediaItemEnabled | pb.connected && pb.effectiveDurationMs > 0 |
| shuffleEnabled | pb.connected |
| shuffleOn | pb.shuffleEnabled |
| media | pb.current?.let { MediaUiModel.Ready(id = it.id, title = it.name, subtitle = it.artistText) }（artwork 默认 null；要图则显式引 horologist-images-coil 用 CoilPaintable(artworkUrl)） |
| trackPositionUiModel | 三分支，见下 |
| seekBackButtonIncrement / seekForwardButtonIncrement | 默认 `SeekButtonIncrement.Unknown`（应用无 ±N 秒按钮） |

trackPositionUiModel 分支（percent 语义照抄 `media-ui-model` 的 TrackPositionUiModelMapper.kt:49 `percent = currentPositionMs / durationMs`，0..1 浮点）：

- `pb.buffering && pb.positionMs <= 0` → `TrackPositionUiModel.Loading(showProgress = true)`
- `pb.effectiveDurationMs > 0` → `TrackPositionUiModel.Actual(percent = pb.positionMs / effectiveDurationMs.toFloat(), duration = pb.effectiveDurationMs.milliseconds, position = pb.positionMs.milliseconds)`（`effectiveDurationMs` 用 AppViewModel.kt:1086 扩展：durationMs<=0 时回退 runTimeTicks/10000；`milliseconds` 来自 kotlin.time.Duration.Companion）
- 其余 → `TrackPositionUiModel.Hidden`
- 用户拖动中的乐观投影（可选）：`TrackPositionUiModel.SeekProjection(percent = SeekLogic.targetMs(...)/duration, duration, position)`——与 mapper 的 seekProjection 语义一致。

消费件（实测签名）：

- `MediaControlButtons(onPlayButtonClick, onPauseButtonClick, playPauseButtonEnabled: Boolean, playing: Boolean, onSeekToPreviousButtonClick, seekToPreviousButtonEnabled, onSeekToNextButtonClick, seekToNextButtonEnabled, modifier = Modifier, trackPositionUiModel: TrackPositionUiModel, colorScheme = MaterialTheme.colorScheme)`（components/MediaControlButtons.kt:66；无进度重载在 :36，内部传 `TrackPositionUiModel.Hidden`）。内部组装 SeekToPreviousButton + PlayPauseProgressButton(weight 0.5) + SeekToNextButton。
- `PlayPauseProgressButton(onPlayClick, onPauseClick, playing, trackPositionUiModel, modifier, colorScheme = MaterialTheme.colorScheme, enabled = true, colors = MediaButtonDefaults.playPauseButtonDefaultColors(colorScheme), iconSize = IconButtonDefaults.LargeIconSize, progressStrokeWidth = 4.dp, indicatorColor = colorScheme.secondaryDim, trackColor = colorScheme.secondary.copy(alpha = 0.3f))`（components/PlayPauseButton.kt:112）——中键四周进度环。
- ended 重播态（Refresh 图标）是应用特有：在自定义 controlButtons 内用 `PlayPauseButton(playing = false)` + 覆盖图标实现，不经 PlayerUiState。
- `PlayerUiState.NotConnected`（PlayerUiState.kt:64）作 connected=false 初值。
- 注意：PlayerUiState / MediaUiModel / TrackPositionUiModel / PlayerUiController 均在 `com.google.android.horologist.media.ui.state`（media-ui-model），全部 @ExperimentalHorologistApi。

## 5. 音量/音频输出 API 映射（供 t5-volume 实现、t4-player 消费）

现状（不动）：`PlaybackConnection.adjustVolume(direction: Int)`（playback/PlaybackConnection.kt:296）→ `AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER|ADJUST_RAISE, 0)`；`volumePercent = current/max*100`（PlaybackConnection.kt:302-306 currentVolumePercent）。UI：音量 ± 按钮 WearJellyApp.kt:2439 / :2464、文本 "音量 N%" :2473。

**决策：不引入 VolumeViewModel / VolumeRepository / SystemAudioRepository。** 依据：`audio-ui-model` VolumeViewModel.kt:55 `public open class VolumeViewModel(internal val volumeRepository: VolumeRepository, internal val audioOutputRepository: AudioOutputRepository, private val onCleared: () -> Unit = {}, private val vibrator: Vibrator) : ViewModel`，其 `Factory`（:143）`SystemAudioRepository.fromContext(application)` 直控 AudioManager.STREAM_MUSIC——音量状态会与 `PlaybackState.volumePercent` 双轨漂移，且 repository 生命周期（onCleared close）需自行管理。音量操作唯一路径保持 `viewModel.adjustVolume(direction)`。

**VolumeUiState 适配（百分比伪刻度）**：VolumeUiState.kt:20 `public data class VolumeUiState(val current: Int = 0, val max: Int = 1, val min: Int = 0)`（isMax/isMin；Unknown = -1/1/0）→

```kotlin
internal fun PlaybackState.toVolumeUiState() =
    VolumeUiState(current = volumePercent.coerceIn(0, 100), max = 100, min = 0)
```

VolumeScreen 内部 `volumePercent = (100f * current / max).roundToInt()`（VolumeScreen.kt:283）→ 恒等还原 0..100。

**VolumeScreen 重载族**（audio-ui-material3/VolumeScreen.kt，全 M3）：

1. :78 stateful 自含 `VolumeScreen(modifier, volumeViewModel = viewModel(factory = VolumeViewModel.Factory), showVolumeIndicator = true, increaseIcon, decreaseIcon, colorScheme)`，自带 `rotaryScrollable(volumeRotaryBehavior(...))`。**不用**（依赖 VolumeViewModel）。
2. :112 `VolumeScreen(volume: () -> VolumeUiState, audioOutputUi: AudioOutputUi, increaseVolume, decreaseVolume, onAudioOutputClick, modifier, increaseIcon, decreaseIcon, showVolumeIndicator, colorScheme)`——content 固定 DeviceButton；`onAudioOutputClick = {}` 占位。
3. :159 `VolumeWithDefaultLabel(volume, increaseVolume, decreaseVolume, modifier, increaseIcon, decreaseIcon, showVolumeIndicator, colorScheme)`——content 为 "Volume" 标签。
4. :191 `VolumeWithAudioOutputAsLabel(volume, audioOutputUi, increaseVolume, decreaseVolume, ...)`——标签区显示输出图标 + 设备名（audioSourceDisplayName 两行）。
5. :271 最内核 `VolumeScreen(volume, increaseVolume, decreaseVolume, modifier, increaseIcon, decreaseIcon, showVolumeIndicator, colorScheme, content: @Composable () -> Unit)`——Stepper（value=current、steps=max-1、onValueChange 按增减方向分派）+ VolumeLevelIndicator。**推荐主用**：content 自定义 "音量 N%" 文本。

共用件：`VolumeLevelIndicator(volumeUiState, colorScheme)`（同包 VolumeLevelIndicator.kt）；带事件版用法见 PlayerLibraryPagerScreen.kt:72 `VolumeLevelIndicator(volumeUiState = ..., displayIndicatorEvents = ...)`——`displayIndicatorEvents: Flow<Unit>` 可用应用侧 `MutableSharedFlow<Unit>`（adjustVolume 时 emit）替代 VolumeViewModel.displayIndicatorEvents。

**音频输出件**（实测 0.8.4-alpha 全模块无 `AudioOutputSelector`，等价物如下）：

- `components/AudioOutputUi.kt:36` `public data class AudioOutputUi(val displayName: String, val imageVector: ImageVector, val isConnected: Boolean, val audioSourceDisplayName: String? = null)`；`AudioOutput.toAudioOutputUi()`（:44）按 type（TYPE_WATCH/HEADPHONES/NONE/其他）映射图标与文案。
- `components/DeviceButton.kt:37` `DeviceButton(volumeDescription: String, deviceName: String, icon: @Composable BoxScope.() -> Unit, onAudioOutputClick: () -> Unit, modifier, colors)`。
- `components/actions/VolumeButton.kt`：`VolumeButton(onVolumeClick, modifier, imageVector = Icons.Rounded.Radio, enabled, alignment, buttonColors = SettingsButtonDefaults.buttonColors(), contentDescription, border)`；带状态版 :92 `VolumeButton(onVolumeClick, modifier, volumeUiState: VolumeUiState?, ...)` 动态图标（isMin→VolumeMute、非 max→VolumeDown、否则 VolumeUp，:220 getVolumeIndicatorIcon）；`VolumeButtonWithBadge(onOutputClick, audioOutputUi, volumeUiState, ...)`（:181，断连显示 rounded_media_output_off_24，:214 getAudioOutputConnectionIcon）。
- wearmusic 无输出切换功能 → 音量面板建议用 3/5 号重载；若要手表扬声器静态图标，可伪造 `AudioOutputUi(displayName = "手表扬声器", imageVector = Icons.Rounded.Watch, isConnected = true)` 配 2/4 号重载（纯展示，不接 onAudioOutputClick）。
- PlayerScreen `buttons` slot：`VolumeButton(onVolumeClick = navigate(音量面板))` + 队列（可选 `components/actions/ShowPlaylistButton`）+ 收藏心形并列。

**表冠（可选渐进增强，不作验收；用户手表无表冠）**：`volumeRotaryBehavior(volumeUiStateProvider: () -> VolumeUiState, onRotaryVolumeInput: (Int) -> Unit)`（RotaryVolumeControls.kt:40，包 `com.google.android.horologist.audio.ui.material3`；内部分 lowRes/highRes；像素→音量 convertPixelToVolume :130；触顶/底 LONG_PRESS 否则 KEYBOARD_TAP 震动 :145）。注意 `onRotaryVolumeInput` 传**绝对目标 int**：若接入，需把 (new-old) 符号化为 direction 调 adjustVolume，或给 PlaybackConnection 加一行 `setVolumePercent(int)`（audioManager.setStreamVolume）——后者触碰播放层，默认不做，仅记录。

## 6. 14 屏逐屏映射表（AppScreen 分支实为 15 项）

统一规则：保留 `AppScaffold` 单层 + 各屏 ScreenScaffold/TLC；TLC item 统一 `.minimumVerticalContentPadding(...)` + `.transformedHeight(this, transformationSpec)` 模式不变；文案继续走 `object Copy`；多选（SelectionState/MultiSelectBar）、notice 浮字、AlphabetListScaffold 等自研件保留。Horologist 组件证据源码在 `.horologist-src/`。

| # | 屏（现位置） | 方案 | 组件/要点 |
|---|---|---|---|
| 1 | Login（WearJellyApp.kt:400-504） | M3 | TLC + Button + 自绘 LoginTextField（Wear M3 无 OutlinedTextField，维持）；20dp CircularProgressIndicator |
| 2 | Home（:507-674） | M3 | HomeListButton 6+1 保留；MiniPlayerCard 自绘保留（发丝进度条结论不变：M3 LinearProgressIndicator 最小描边 8dp 不适配） |
| 3 | Library（:866-1047） | M3+自研 | TransformingLazyColumn + AlphabetListScaffold + LibraryItemRow（combinedClickable+leftSwipeGesture）；长标题可选 horologist `MarqueeText` |
| 4 | Track 详情（:2017-2234） | M3 | Button 列表 + solidButtonColors；可选 horologist `RepeatableClickableButton` |
| 5 | Player（:2237-2504） | **Horologist** | PlayerScreen slots 重载（mediaDisplay/controlButtons/buttons/background）；controlButtons 用 MediaControlButtons + trackPositionUiModel 进度环（第 4 节映射）或保留三键自绘 + ended 重播；SeekableProgressBar 保留（Horologist M3 无可拖动 seek bar）；buttons slot = VolumeButton + 队列 + 收藏心形（docs/favorite-design.md，t13）；background 可选 ArtworkImageBackground |
| 6 | Queue（:2507-2641） | **Horologist** | `composables/SectionedList`（SectionedList.kt）分"正在播放/接下来"分组 + LibraryItemRow 复用；清空按钮 0xFF4A1515 |
| 7 | Lyrics（LyricsPage.kt） | M3 规范化 | Canvas 环形进度/预览/自动跟随全自研保留，仅色/字接入 MaterialTheme；无 Horologist 对应件 |
| 8 | LyricsSettings（LyricsPage.kt） | M3 | Button 行 + solidButtonColors(0xFF4A5568) |
| 9 | Downloads（:2645-2769） | M3 | TLC + 缓存队列状态行（自研）+ AlphabetListScaffold |
| 10 | History（:2772-2880） | M3 | TLC + AlphabetListScaffold(showIndex=false) |
| 11 | BatchActions（:1645-1863） | M3 | Button 列表（11 项操作） |
| 12 | ListActions（:1871-1947） | M3 | Button 列表（按 ListMenuAction 配色） |
| 13 | SongInfo（:1950-2000） | M3 | 六行卡片（10dp 圆角，background 底色） |
| 14 | Settings（:2883-2996） | M3 | Button 行；音质选择保持 ✓ 前缀（不换 RadioButton，最小回归） |
| 15 | Confirm（:3009-3052） | M3 | 是/否 44dp Button 对；全屏语义保留（不换 AlertDialog） |

**验收对照（t16 用）**：`./gradlew :app:assembleDebug` 通过；应用源码 grep `androidx.wear.compose.material`（排除 material3）0 命中；app/build.gradle.kts 无 compose-material 显式声明；minSdk=26；15 屏分支人工清单比对无功能缺失；收藏功能按 docs/favorite-design.md 生效。

# UI / ViewModel 增量独立验证报告（列表排序、字母索引、滚动恢复、多选）

- 验证任务：t2 [v2] 独立验证列表排序、字母索引、滚动恢复与多选
- 验证者：ui-verifier（AgentTeams wearmusic-verify，只读审查，未修改任何生产/测试代码）
- 日期：2026-06-09　基线 HEAD：`5b2a9f2`
- 审查范围（只读）：
  - `app/src/main/java/dev/wearjelly/data/PinyinSort.kt`（58 行）
  - `app/src/main/java/dev/wearjelly/ui/SelectionState.kt`（71 行）
  - `app/src/main/java/dev/wearjelly/ui/AppViewModel.kt`（859 行）
  - `app/src/main/java/dev/wearjelly/ui/WearJellyApp.kt`（2654 行）
  - `app/src/test/java/dev/wearjelly/data/PinyinSortTest.kt`（138 行）
  - 辅助证据（非审查对象，仅取证）：`HistoryStore.kt`、`PlaybackConnection.kt`、`PlaybackService.kt`、`Copy.kt`、`docs/superpowers/specs/2026-06-09-list-playback-incremental-plan-7f1c2d.md`
- 结论摘要：**6 条验收全部通过（代码层）**；发现 0 个阻塞缺陷、3 个低级问题、2 个低风险观察项；JVM 单测 59/59 通过；另有 5 项只能真机/圆屏人工验收。

---

## 一、必跑验证命令（实际执行）

工作目录 `D:\wearmusic`：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat testDebugUnitTest
```

| 轮次 | 命令 | 结果 | 说明 |
|---|---|---|---|
| 第 1 次 | 上述命令（带 `2>&1`） | exit 1（job pwsh-193） | Gradle 输出 `> Task :app:testDebugUnitTest UP-TO-DATE`、`BUILD SUCCESSFUL in 1s`。exit 1 是 PowerShell NativeCommandError 造成的假失败：stderr 仅有 `.\gradlew.bat : Warning: SDK processing. This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered...`，经 `2>&1` 合流后被记为错误流。**测试实际未重新执行。** |
| 第 2 次 | 同命令追加 `--rerun`（不重定向 stderr） | **exit 0**（job pwsh-215） | `> Task :app:testDebugUnitTest`（executed）、`BUILD SUCCESSFUL in 2s`、`28 actionable tasks: 1 executed, 27 up-to-date` —— 测试**真实重跑并通过**。 |
| 第 3 次（报告定稿前复核） | 原命令，无重定向 | **exit 0** | `compileDebugKotlin UP-TO-DATE`、`testDebugUnitTest UP-TO-DATE`、`28 actionable tasks: 28 up-to-date` —— 证明**当前工作树源码与第 2 次真实执行时完全一致**，上述 59/59 结果对定稿报告仍然有效。 |

> 说明：工作树由团队共享，审查期间有其他成员改动 `PlaybackService.kt` / `PlaybackConnection.kt` / `WearJellyApp.kt` 等文件。本报告所有 file:line 证据已在定稿前逐一按**当前文件状态**复核（复核方式：按关键标识重新 grep 行号，结果与引用一致）。

测试结果（`app/build/test-results/testDebugUnitTest/*.xml`，本轮执行）：

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `dev.wearjelly.data.PinyinSortTest` | 16 | 0 | 0 | 0 |
| `dev.wearjelly.ui.SelectionLogicTest` | 14 | 0 | 0 | 0 |
| `dev.wearjelly.playback.ShuffleQueueTest` | 9 | 0 | 0 | 0 |
| `dev.wearjelly.playback.PlayHistoryStackTest` | 4 | 0 | 0 | 0 |
| `dev.wearjelly.ui.LyricsLogicTest` | 9 | 0 | 0 | 0 |
| `dev.wearjelly.ui.SeekLogicTest` | 4 | 0 | 0 | 0 |
| `dev.wearjelly.data.JellyfinRepositoryTest` | 3 | 0 | 0 | 0 |
| **合计** | **59** | **0** | **0** | **0** |

与 REQ-TEST-008 相关的新增覆盖：`PinyinSortTest.kt` 16 个用例（pinyinKey 4、letterBucket 7、`LETTERS` 27 位含 `#` 末位 92-95、compareItems 大小写不敏感、sort 分桶 109-124、sort 回退 pinyin key 126-135）；`SelectionLogicTest` 覆盖 `toggle to zero exits selection mode`、`rangeSelect falls back to single select when anchor is gone`、`inverted flips within list and exits when result is empty` 等 14 个。按 spec 第 66 行，仓库无 Compose UI 手势测试依赖，本次不引入新 UI 测试框架——故手势/滚动恢复列人工验收（见第四节）。

---

## 二、逐条验收判定

### 第 1 条　已缓存列表按 PinyinSort 排序且不丢条目；播放历史最新在前且不显示字母索引

**判定：通过**

- 库列表排序入口唯一：`AppViewModel.kt:338` `val sorted = dev.wearjelly.data.PinyinSort.sort(all.distinctBy { it.id })`，随后 `AppViewModel.kt:339-349` `copy(items = sorted, total = sorted.size, nextOffset = sorted.size, endReached = true)` —— 排序后整体原子发布，排序在分页拉取循环（`AppViewModel.kt:324-337`）之后执行，不丢条目。
- 排序本身不丢数据：`PinyinSort.kt:56-57` `items.sortedWith(...)`（`sortedWith` 稳定排序，长度守恒）；比较器 `PinyinSort.kt:49-54` 先比 `letterBucket` 在 `LETTERS`（`PinyinSort.kt:10` = `"ABCDEFGHIJKLMNOPQRSTUVWXYZ#"`）中的位置，再比 `pinyinKey`（`PinyinSort.kt:25-32`）忽略大小写。
- 已缓存（Downloads）列表排序：`WearJellyApp.kt:2252-2255` `PinyinSort.sort(downloaded.map { it.item }).mapNotNull { byId[it.id] }`，`byId` 由同一批 `downloaded` 构建（`WearJellyApp.kt:2253`），排序后每个 id 都能取回 → 不丢条目；展示用 `WearJellyApp.kt:2394` `itemsIndexed(sortedDownloaded)`。
- 播放历史最新在前：`HistoryStore.kt:41-42` `listOf(HistoryEntry(item, now)) + _entries.filter { it.item.id != item.id }`（新记录前插 + 同 id 去重）；UI 直接按 store 顺序渲染 `WearJellyApp.kt:2438` `historyItems = remember(history) { history.map { it.item } }`、`WearJellyApp.kt:2482` `itemsIndexed(history)`，**无任何 comparator 干预**（符合 spec 第 9 行 "HistoryList 维持最近播放优先，不使用该 comparator"）。
- 历史不显示字母索引：`WearJellyApp.kt:2445` `showIndex = false`（`AlphabetListScaffold` 参数定义 `WearJellyApp.kt:847`，渲染门 `WearJellyApp.kt:885` `if (showIndex && items.isNotEmpty())`）。

### 第 2 条　长按字母索引出现 ±3 共 7 个字母预览，点击/短滑跳转到该字母首项

**判定：通过（预览宽度、跳转逻辑在代码层确认；手势体感列真机验收）**

- 长按进入预览：`WearJellyApp.kt:1020-1038` 用 `withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis)` 等待；`hold == null`（长按超时且未移动）→ `WearJellyApp.kt:1040-1055` `scrubbing = true`、`nearestLetterIdx` 实时更新 `activeLetterIdx`、松手 `jumpToLetter(selected)`。
- 预览 7 个字母：`WearJellyApp.kt:930` `animateFloatAsState(if (scrubbing) 3f else 1f)` → `WearJellyApp.kt:934-938` `for (role in -3..3) { if (abs(role) > ringSpan.toInt()) continue; ... }` —— `scrubbing=true` 时 `ringSpan=3`，`role∈[-3,+3]` 即**中心 ±3 共 7 个**。
- 边界不越界：`WearJellyApp.kt:937` `if (letterIdx !in RING_LETTERS.indices) continue`（对应 spec 第 11 行 "边界处只显示可用字母"），`RING_LETTERS = "ABC…#"`（`WearJellyApp.kt:1080`）。
- 点击跳转：`WearJellyApp.kt:1056-1057` `else if (released && !moved) jumpToLetter(nearestLetterIdx(down.position.y))`。
- 短滑跳转：`WearJellyApp.kt:1058-1070` `else if (moved && !horizontal)` → `scrubbing=true`、`jumpToLetter(selected)` 并跟手循环（每次移动 `change.consume()` 后再 `jumpToLetter`）；横向意图（`horizontal=true`）不跳转，交还列表（`WearJellyApp.kt:1033`、1070 分支不命中）。
- 跳到"该字母首项"：`WearJellyApp.kt:902-920` `jumpToLetter` → `clamped` 后从该字母向后找第一个存在的分组（`groupFirst`，`WearJellyApp.kt:858-866` 用 `PinyinSort.letterBucket` 建立首个下标），都没有则回落 `"#"`（`WearJellyApp.kt:914-916`），最后 `listState.scrollToItem(headerItems + target)`（`WearJellyApp.kt:917-919`）。
- 分组键与排序键同源：`WearJellyApp.kt:862` `PinyinSort.letterBucket(item.name)`，与 `PinyinSort.kt:35-47` 一致（空/数字/符号→`#`，前导空白 trim），保证索引分组与列表分组严格一致。

### 第 3 条　列表滚动位置在进入/返回批量操作页后按曲目 id 恢复；锚点已被删除时按保存的相邻索引兜底

**判定：通过（机制在代码层闭合；返回体感列真机验收）**

- 三元锚点保存（按曲目 id）：`WearJellyApp.kt:806-808`
  ```kotlin
  var anchorId by rememberSaveable { mutableStateOf<String?>(null) }
  var anchorIndex by rememberSaveable { mutableStateOf(0) }
  var anchorOffset by rememberSaveable { mutableStateOf(0) }
  ```
- 恢复逻辑（id 优先 + 相邻索引兜底）：`WearJellyApp.kt:810-817`
  ```kotlin
  val savedIndex = anchorId?.let { id -> items.indexOfFirst { it.id == id } }
      ?.takeIf { it >= 0 }
      ?: if (anchorId == null) initialIndex else anchorIndex
  val index = savedIndex.coerceIn(items.indices)
  listState.scrollToItem(headerItems + index, anchorOffset)
  ```
  - `indexOfFirst` 命中 → 按 id 恢复；`anchorId` 非空但已不在列表（锚点被删除/移除）→ 落到 `anchorIndex`（**已保存的相邻索引兜底**）；`anchorId == null`（首次进入）→ `initialIndex`；越界 → `coerceIn(items.indices)`（`WearJellyApp.kt:815`）。
- 锚点持续跟踪：`WearJellyApp.kt:818-828` `snapshotFlow { (centerItemIndex - headerItems) to centerItemScrollOffset }` 回写三元组。
- 四个列表全部接入：Library `WearJellyApp.kt:672`、Queue `WearJellyApp.kt:2136-2141`（含 `initialIndex = pbState.currentIndex`）、Downloads `WearJellyApp.kt:2259`、History `WearJellyApp.kt:2439`。
- 跨页面状态保留：`WearJellyApp.kt:194` `stateHolder.SaveableStateProvider(screen.toString())` 按 screen 键保存 `rememberSaveable` 与 `ScalingLazyListState`；从 BatchActions 返回后 `items` 未变则由列表自身 saveable 状态复位，`items` 变化（如"从队列移除"）则 `LaunchedEffect(listState, items, headerItems, initialIndex)`（`WearJellyApp.kt:810`）重新按 id/索引恢复 —— 两条路径互补。
- `headerItems` 与实际 header 数一致，避免恢复错位：Library `WearJellyApp.kt:671` `1 + (if (isSongsScreen) 1 else 0)`（标题 + 更多按钮 707-717）、Queue `WearJellyApp.kt:2136-2141`（2）、Downloads `WearJellyApp.kt:2257`（1 + 缓存队列段）、History `WearJellyApp.kt:2439`（1）。

### 第 4 条　首页存在当前队列入口；"随机播放全部"开启随机后从随机起点开始播放

**判定：通过**

- 首页队列入口：`WearJellyApp.kt:419-436` HomeScreen 中 Mini Player 存在时渲染 `MiniPlayerCard`，其队列图标 `WearJellyApp.kt:595-602`（`Icons.AutoMirrored.Filled.QueueMusic`、`onClick = onQueue`）→ `WearJellyApp.kt:433` `viewModel.navigate(AppScreen.Queue)`。
- "随机播放全部"入口：`WearJellyApp.kt:1610` `onClick = { viewModel.playAllShuffled(screen.query) }`，文案 `Copy.kt:19` `SHUFFLE_PLAY_ALL = "随机播放全部"`；该按钮仅在歌曲列表出现（`WearJellyApp.kt:707` `if (isSongsScreen)`，`isSongsScreen = screen.query.kind == LibraryKind.SONGS` `WearJellyApp.kt:670`）。
- 开随机：`AppViewModel.kt:549-554`
  ```kotlin
  internal fun playAllShuffled(query: LibraryQuery) {
      if (!playback.state.value.shuffleEnabled) { playbackAction { playback.toggleShuffle() } }
      playLoaded(query, randomizedStart = true)
  }
  ```
  `playbackAction` 先校验会话与连接（`AppViewModel.kt:691-697`，未连接则 `reconnectPlayback()` + 提示并返回 false）；`toggleShuffle` `PlaybackConnection.kt:308-311` 翻转 `shuffleModeEnabled`，状态回写 `PlaybackConnection.kt:115-116`。
- 随机起点：`AppViewModel.kt:416-420` `randomizedStart -> kotlin.random.Random.nextInt(items.size)`，随后 `AppViewModel.kt:425` `playback.play(items, index)` → `PlaybackConnection.kt:186-193` `CUSTOM_COMMAND_SET_QUEUE` 携带 `EXTRA_START_INDEX` → `PlaybackService.kt:277-285` `exo.setMediaItems(mediaItems, startIndex.coerceIn(...), 0L); prepare(); play()` —— **起点曲目即随机索引**，其后沿随机顺序推进（`PlaybackService.kt:330-341` 生成 ShuffleOrder；此部分属 SHUFFLE-005 另一成员范围，仅作取证）。

### 第 5 条　点击正在播放的曲目/队列项只跳转到播放器，不重启播放、不重置队列；同歌不同位置仍能正确定位

**判定：通过**

- 列表曲目：`AppViewModel.kt:385-388`
  ```kotlin
  if (playback.state.value.current?.id == item.id) { navigate(AppScreen.Player); return }
  ```
  在 `waitForConnected`/`playback.play(listOf(item))` 之前短路 → **不重建队列、不重启播放**。四个列表均经此入口：Library `WearJellyApp.kt:751`、Downloads `WearJellyApp.kt:2406`、History `WearJellyApp.kt:2493`（Queue 见下）。
- 队列项：UI 传 `index + expectedId` `WearJellyApp.kt:2196-2199` `viewModel.playQueueIndex(index, item.id)`；VM 侧 `AppViewModel.kt:636-643`
  ```kotlin
  if (!queueMatches(index, expectedId)) return
  if (index == playback.state.value.currentIndex && playback.state.value.current?.id == expectedId) {
      navigate(AppScreen.Player); return
  }
  if (playbackAction { playback.playQueueIndex(index) }) navigate(AppScreen.Player)
  ```
  - 命中当前曲目 → 仅导航 `AppScreen.Player`，无 `seekTo`、无 SET_QUEUE。
  - 同一歌曲出现在队列多个位置时：`index == currentIndex` 才短路；点另一处相同 id 的位置 → `index` 不同 → 执行 `playback.playQueueIndex(index)`（`PlaybackConnection.kt:275-277` `controller?.seekTo(index, 0L)`）→ **按 index 精确定位到用户点击的那个位置**；`queueMatches`（`AppViewModel.kt:654-658`）校验 `queue[index].id == expectedId`，不匹配则提示"播放队列已变化"并放弃操作。
- 队列一致性校验同样覆盖删除/移动：`removeQueueItem` `AppViewModel.kt:645-647`、`moveQueueItem` `AppViewModel.kt:649-652`。

### 第 6 条　Library/Downloads/History/Queue 四列表多选交互一致：左滑进入、全选/反选、BatchActions 文案按来源区分、"从队列移除"仅 QUEUE；1→0 自动退出多选

**判定：通过**

左滑进入 / 范围选择（四列表同构）：

| 列表 | 左滑进入（非多选） | 范围选择（多选中） | MultiSelectBar | BatchActions 来源 |
|---|---|---|---|---|
| Library（歌曲） | `WearJellyApp.kt:773-774` `beginSelectionFromSwipe` | `WearJellyApp.kt:770-772` `selectRangeFromSwipe(item.id, lib.items.map{it.id})` | `WearJellyApp.kt:679-685` | `WearJellyApp.kt:683` 默认 `BatchSource.LIBRARY` |
| Downloads | `WearJellyApp.kt:2415` | `WearJellyApp.kt:2411-2414` | `WearJellyApp.kt:2266-2280` | `WearJellyApp.kt:2275` `BatchSource.DOWNLOADS`、标题"已缓存音乐"（2274） |
| History | `WearJellyApp.kt:2502` | `WearJellyApp.kt:2499-2501` | `WearJellyApp.kt:2447-2461` | `WearJellyApp.kt:2456` `BatchSource.HISTORY`、标题"播放历史"（2455） |
| Queue | `WearJellyApp.kt:2207` | `WearJellyApp.kt:2204-2206` | `WearJellyApp.kt:2218-2233` | `WearJellyApp.kt:2227` `BatchSource.QUEUE`、标题"播放队列"（2226） |

- 手势实现共用 `LibraryItemRow`（`WearJellyApp.kt:1151`）+ `leftSwipeGesture`（`WearJellyApp.kt:1258-1294`，阈值 `max(2×touchSlop, 20.dp)`、`|dx| ≥ 2|dy|` 判为左滑、纵向意图交还列表）→ **四列表手势判定同一份代码**，交互一致。
- 单击语义一致：多选中一律 `toggleSongSelection`，否则 `playTrack`（`WearJellyApp.kt:749-751`、2406、2493、2196-2198）。
- 来源区分的数据源：`WearJellyApp.kt:1350-1355` 按 `screen.source` 取 `LIBRARY→libraries[query].items / DOWNLOADS→downloaded.map{it.item} / HISTORY→history.map{it.item} / QUEUE→playbackState.queue.map{it.item}`；标题按来源传入（见上表），计数 `WearJellyApp.kt:1379` `Copy.selectedCount(uiState.selectedSongIds.size)`。
- **"从队列移除"仅 QUEUE**：`WearJellyApp.kt:1433-1446` `if (screen.source == BatchSource.QUEUE)` → `removeSelectedFromQueue(items)`（`AppViewModel.kt:538-546`：`mapIndexedNotNull` 收集索引 → `asReversed()` 倒序逐个 `removeQueueItem` → `showNotice("已从队列移除 N 首")` → `exitSelectionMode()`；倒序删除保证索引不失效）。
- 对应的"从当前列表移除"仅 LIBRARY：`WearJellyApp.kt:1447-1461` `if (screen.source == BatchSource.LIBRARY)`。
- 全选/反选/取消：`WearJellyApp.kt:1471-1478` 全选 → `selectAllItems(items)`（`AppViewModel.kt:518-526`）；`WearJellyApp.kt:1480-1490` 取消全选 → `exitSelectionMode()` + `goBack()`；`WearJellyApp.kt:1492-1503` 反选 → `invertSelectionItems(items)` 后 `if (!viewModel.uiState.value.selection.active) viewModel.goBack()`（**反选归零自动退出多选并离开本页**）。
- **1→0 自动退出**：`SelectionState.kt:31-33`（`toggled` 归零 → `cleared()`）、`SelectionState.kt:67`（`inverted` 归零 → `cleared()`）、`SelectionState.kt:51`（`rangeSelected` 锚点失效 → 降级 `enterFromSwipe(targetId)` 单选）；纯逻辑测试覆盖 `SelectionLogicTest` 的 `toggle to zero exits selection mode`、`inverted flips within list and exits when result is empty`、`rangeSelect falls back to single select when anchor is gone`。
- 系统返回键优先退出多选（不离开列表页）：`WearJellyApp.kt:152-163` `BackHandler(enabled = selectionMode && screen ∈ {Library, Downloads, History, Queue}) { exitSelectionMode() }`，二级操作页仍走正常返回。
- 底栏遮挡规避：列表末尾加 56.dp 占位 `WearJellyApp.kt:784-787`、`2214-2216`、`2508-2510`。
- 批量动作后选择态清理：`removeSelectedFromQueue`（`AppViewModel.kt:545`）、`batchSelectedSongsToQueue`（`AppViewModel.kt:455`）、`deleteCachedTracks`（`WearJellyApp.kt:1424`）均调用 `exitSelectionMode()`。

---

## 三、问题清单（含严重级别 + 最小修复建议）

| # | 级别 | 位置 | 问题 | 最小修复建议 |
|---|---|---|---|---|
| P1 | 低 | `AppViewModel.kt:548` | KDoc 与实现不符：注释写"随机播放全部：确保随机模式开启后**从列表头播放**"，实现是 `playLoaded(query, randomizedStart = true)` 从随机起点播放（`AppViewModel.kt:553`、418）。误导后续维护。 | 仅改注释为"开启随机后从随机索引开始播放"。 |
| P2 | 低 | `WearJellyApp.kt:1056-1057` | 字母索引条 tap 分支不 `change.consume()`（长按/短滑分支分别在 1050、1067 有 consume）。索引条宽 `26.dp`（`WearJellyApp.kt:994`）与列表行末内边距 `end 20.dp`（`WearJellyApp.kt:690`）存在 **6dp 重叠带**，该带内点按可能同时触发"跳转字母"与"行点击（播放/选择）"。 | 在 down/tap 分支补 `change.consume()`（或把索引条收到 `end 20.dp` 之外）；先按第四节真机复现，未复现则保留观察。 |
| P3 | 低 | `WearJellyApp.kt:1352` | BatchActions 的 `DOWNLOADS` 数据源用 `downloaded.map { it.item }` **未排序**，而列表展示为 `PinyinSort` 序（`WearJellyApp.kt:2254`）。集合类操作（加入队列/全选）按 id 过滤不受影响，但 `playItemsNext(selectedItems)`（`WearJellyApp.kt:1411`）与 `shareItems`（1464）的顺序与列表展示顺序不一致。 | 与列表共用同一 `remember(sortedDownloaded)` 结果（把排序结果提升为共用状态），或在 `BatchActions` 内对 DOWNLOADS 复用 `PinyinSort.sort`。 |
| P4 | 低（存量，非本增量引入） | `AppViewModel.kt:324`、`346`、`836-837` | 全量拉取上限 `MAX_FETCH_ITEMS = 3000`，超限后仍写 `endReached = true` → 超过 3000 首的媒体库**静默截断**且不再分页（`WearJellyApp.kt:779-781` 的 loadMore 永不触发）。`PinyinSort` 排序本身不丢条目，但总量 >3000 时用户看不到尾部。 | 达到上限时不置 `endReached = true`（继续分页）或给出"仅显示前 3000 首"提示。本增量未改动该循环，建议单独立项。 |
| P5 | 低（测试缺口） | `WearJellyApp.kt:810-816` | 锚点恢复的"id → 相邻索引 → coerce"解析逻辑内联在 `@Composable` 中，无 JVM 单测覆盖（现有 `SelectionLogicTest`/`PinyinSortTest` 不含）。滚动恢复是 REQ-LIST-SCROLL-003 的核心，回归只能靠人工。 | 把解析抽成纯函数 `resolveAnchorIndex(anchorId, anchorIndex, initialIndex, ids): Int` 并补 3 个单测（命中/删除兜底/越界）。 |
| O1 | 观察 | `WearJellyApp.kt:930-931`、935 | `ringSpanF` 由 1→3 需经动画，`ringSpan = ringSpanF.toInt()` 使长按后预览字母**渐进展开**（3→5→7），非即时 7 个。功能达标，仅时序观感问题。 | 无需改动；如需即时展开改 `animateFloatAsState(..., animationSpec = snap())`。 |
| O2 | 观察 | `WearJellyApp.kt:419-436` | 首页队列入口挂在 Mini Player 上，仅当存在"正在播放/上次播放"快照时渲染；从未播放过则首页无队列入口（与设计 D4 三态一致，且此时队列为空）。 | 无需改动；若产品要求"空队列也可进队列页"，另立需求。 |

**阻塞问题：无。** 未发现导致下游工作必须停止的真实缺陷。

---

## 四、只能真机 / 圆屏人工验收的项

仓库按 spec 第 66 行决定不引入 Compose UI 测试框架，以下项无法由 JVM 单测或代码审查证明，必须在真机/圆形模拟器人工验收：

1. **长按字母索引手势**：长按 7 字母预览是否出现、跟手高亮是否实时、松手是否跳到目标字母首项、中心字母字号/透明度分层（`WearJellyApp.kt:940-954`）是否符合预期；短滑与横向滑动的意图区分（`WearJellyApp.kt:1033`）在真机上是否误判。
2. **索引条 6dp 重叠带双触发（P2）**：在索引条与行末内边距重叠区域点按，确认是否同时发生"字母跳转"和"行点击播放/选中"。
3. **圆屏安全区与弧形索引**：半径 `min(w,h)/2 - 3dp`、弧顶角上限 112°（`WearJellyApp.kt:892-895`）、字母盒贴右缘 `anchorX = arcX - boxW - 1dp`（`WearJellyApp.kt:961`）在真实圆屏上是否被曲面裁切、A/# 两端是否落在可触区。
4. **滚动恢复体感**：列表滚到中部 → 进入批量操作页 → 返回，是否无"顶部闪回"；执行"从队列移除/从当前列表移除"删除锚点后返回，是否落在相邻位置（spec 验收用例：第 50 首附近）；锚点在队列页用 `initialIndex = currentIndex`（`WearJellyApp.kt:2136-2141`）首次进入时的落位。
5. **左滑阈值与底栏遮挡**：`max(2×touchSlop, 20.dp)` 阈值在真机上的跟手感、四列表底部 56.dp 占位在圆屏上是否足以避开 MultiSelectBar。

---

## 五、证据索引（快速定位）

- 排序：`PinyinSort.kt:10,25-32,35-47,49-54,56-57`；`AppViewModel.kt:309-349,836-837`；`WearJellyApp.kt:2252-2255,2394`
- 历史：`HistoryStore.kt:41-42`；`WearJellyApp.kt:2438,2445,2482`
- 索引：`WearJellyApp.kt:843-850,858-866,885,902-920,928-938,994-1013,1020-1074,1080`
- 滚动恢复：`WearJellyApp.kt:672,799-829,194,2136-2141,2259,2439`
- 首页队列 / 随机：`WearJellyApp.kt:419-436,433,595-602,707,1610`；`AppViewModel.kt:409-426,548-554,691-697`；`PlaybackConnection.kt:115-116,186-193,308-311`；`PlaybackService.kt:277-285`
- 点当前曲目：`AppViewModel.kt:385-388,636-643,654-658`；`WearJellyApp.kt:751,2196-2199,2406,2493`；`PlaybackConnection.kt:275-277`
- 多选：`SelectionState.kt:20-70`；`AppViewModel.kt:31,45-49,428-442,518-546,518-536`；`WearJellyApp.kt:152-163,679-685,768-776,1258-1294,1301-1341,1345-1515,2203-2209,2214-2233,2266-2280,2411-2417,2447-2461,2498-2504`
- 测试：`app/build/test-results/testDebugUnitTest/*.xml`（59/59）、`PinyinSortTest.kt:12-137`、`SelectionLogicTest.kt:14-102`

# REQ-SELECT-MT 改造方案（MT 管理器式多选 + 菜单重组）

状态：待用户确认。确认后按 MT-001 → MT-002 → MT-003 → MT-004 → MT-005 分步实现。

## 一、现状盘点（阅读结论）

- 多选状态在 `AppUiState`（`app/src/main/java/dev/wearjelly/ui/AppViewModel.kt:86-87`）：只有 `selectionMode: Boolean` + `selectedSongIds: Set<String>`，**没有锚点**，`toggleSongSelection()`（AppViewModel.kt:403-410）归零不退出，`enterSelectionMode()` 进入时就是 0 首。
- 多选只在 `LibraryScreen` 且 `kind == SONGS` 生效（`WearJellyApp.kt:469-472`）；"已缓存音乐"DownloadsScreen / HistoryScreen 是独立 composable，不参与多选。
- 条目交互用 `combinedClickable`（WearJellyApp.kt:910-913），**没有任何滑动手势**；单击在普通模式=播放（SONGS/DOWNLOADS）或下钻，长按=打开 TrackDetail 页（事实上就是现在的"单曲菜单"）。
- "退出多选 (N)" 和 "选中入队/选中缓存" 是**列表内滚动 item**（WearJellyApp.kt:497-513、576-595），会随列表滚走，违反"固定底部栏"要求。
- 返回键只有导航 BackHandler（WearJellyApp.kt:138-140），多选模式不拦截返回。
- 提示基础设施：`showNotice()` 底部浮层（WearJellyApp.kt:186-200），可直接当 Snackbar 用。
- 手势环境冲突源：
  - 右缘字母条（AlphabetListScaffold，WearJellyApp.kt:771-812）：只在右缘 26dp 竖向意图(|dy|≥0.8|dx|)时整段消费，横向放行。
  - 应用级 `SwipeToDismissBox`（WearJellyApp.kt:174-185）：非根页面整个屏幕包一层，负责"右滑返回"。
  - ScalingLazyColumn：垂直滚动 + 缩放。
- 播放层 `PlaybackConnection`（playback/PlaybackConnection.kt）现有 `play/enqueueMany/moveQueueItem/removeQueueItem/...`，**没有"插入到下一首"**，需要新增 `insertNext()`（Media3 `addMediaItems(currentIndex+1, …)`，低成本）。
- 现有批量能力：`batchSelectedSongsToQueue/Cache`（AppViewModel.kt:420-447）完成后会 exitSelectionMode + showNotice，模式已对。列表级批量 `batchAllSongsToQueue/Cache`（449-473）存在。"删除列表全部缓存/从列表移除/分享/查看信息" 缺失。
- 测试现状：仅 `app/src/test/.../data/JellyfinRepositoryTest.kt`，UI/VM 层无测试。多选逻辑应抽成纯函数才可 JVM 单测。

## 二、总体改造方案

1. **新增 `ui/SelectionState.kt`**：把多选状态机做成纯 Kotlin 数据类 + 纯函数（enter / toggle / rangeSelect / selectAll / invert / clear），ViewModel 只做转发。AppUiState 增加 `selectionAnchorId: String?`（或整体替换为 `selection: SelectionState`，实现时二选一，倾向前者=最小 diff）。
2. **LibraryItemRow 增加行级左滑手势**：自定义 `pointerInput`（awaitEachGesture），与现有 `combinedClickable`（单击/长按）共存；普通模式左滑→选中并进入多选；多选模式左滑→范围选择。
3. **固定底部多选操作栏 `MultiSelectBar`**：在 LibraryScreen 内容之上 Box 对齐 BottomCenter 叠加（zIndex 高于列表），列表加底部 contentPadding 避让；不再使用列表内"退出多选/选中入队/选中缓存"item。
4. **返回键拦截**：WearJellyApp 中在导航 BackHandler 之后追加 `BackHandler(enabled = selectionMode) { viewModel.exitSelectionMode() }`（Compose 中后注册且 enabled 的优先）。
5. **菜单重组**：单曲菜单=改造现有 TrackDetailScreen（去掉"全部入队/全部缓存"违规项，补齐单项操作）；新增列表级菜单页 `ListActionsScreen`（AppScreen.ListActions）；多选"更多"=二级批量操作页（列表页形态，简单可靠）。
6. **批量完成统一走** `showNotice("…")` + `exitSelectionMode()`。
7. 本期多选范围 = Library SONGS 三种列表（全部歌曲 / 专辑内歌曲 / 艺人歌曲）；Downloads/History 暂不纳入（见决策点 D3）。

## 三、状态机

```
状态：NORMAL ⇄ MULTI(active, selectedIds:Set<String>, anchorId:String?)

NORMAL --左滑条目i-->           MULTI(ids={i}, anchor=i)          [MT-001]
NORMAL --单击条目i-->            播放（SONGS/DOWNLOADS）或下钻，状态不变
NORMAL --长按条目i-->            打开单曲菜单（TrackDetail），状态不变
MULTI --单击条目i(未选)-->       ids+=i, anchor=i                  [MT-002]
MULTI --单击条目i(已选)-->       ids-=i, anchor 不变；若 ids 空 → NORMAL
MULTI --左滑条目i-->             范围选择：ids ∪= [i..anchor]（按当前列表顺序），
                                 anchor=i；anchor 为空/失效时退化为只选 i   [MT-002]
MULTI --长按-->                  无操作（不弹菜单）
MULTI --"退出多选"-->            NORMAL（清空）
MULTI --系统返回键-->            NORMAL（清空），不返回上一页
MULTI --批量操作成功-->          NORMAL + showNotice(结果)
不变式：MULTI 状态下禁止 ids 为空（除"列表级菜单→进入多选"这一显式入口，见 D2）；
        任何 1→0 的转换必须立即落回 NORMAL。
```

锚点定义（与需求一致）：anchor = 最近一次【左滑进入多选选中的项 | 单击后变为选中的项 | 范围选择的终点】。单击取消选中不移动锚点。

## 四、手势判定规则（行级 pointerInput）

常量（实现时以 viewConfiguration 实测微调）：
- `TAP_SLOP = viewConfiguration.touchSlop`（≈8dp）：单击位移上限。
- `SWIPE_TRIGGER = max(2 × touchSlop, 20dp)`：左滑触发位移。
- 方向闸：判定意图时要求 `|dx| ≥ 2|dy|`（与字母条的竖向闸 `|dy|≥0.8|dx|` 正交，互不重叠）。

单个手势的生命周期（awaitEachGesture 内）：
1. `awaitFirstDown(requireUnconsumed = false)`，记录起点。
2. 移动阶段：先比位移是否超过 TAP_SLOP 再定意图——
   - 未超 TAP_SLOP：什么都不做（保留给 click/long-click）。
   - 超出且纵向占优（|dy|>|dx|）：立即退出消费循环，事件交给 ScalingLazyColumn 滚动（本手势作废，不算点击也不算滑动）。
   - 超出且横向、`dx<0`、`|dx| ≥ SWIPE_TRIGGER`：**判定为左滑**——立刻触发回调（普通模式→`onSwipeLeftSelect(item)`；多选模式→`onSwipeLeftRange(item)`），并持续消费本手势后续所有 move/up 事件（防止滚动/返回抢走）。
   - 横向但 `dx≥0`（右滑）：不消费，放行给 SwipeToDismissBox 返回。
3. 抬起时：若全程未超 TAP_SLOP 且未触发左滑 → 交给 `combinedClickable` 的 onClick/onLongClick（我们消费过 move 时 clickable 会自动取消，二者天然互斥）。
4. 多选模式：`onLongClick` 传空实现（不弹菜单，与左滑不冲突——长按要求位移 <TAP_SLOP，左滑早已消费事件）。

冲突清单与对策：
| 冲突源 | 对策 |
|---|---|
| ScalingLazyColumn 垂直滚动 | 纵向意图不消费任何事件；横向判定阈值 > touchSlop |
| 右缘字母条 | 字母条 zIndex 更高且占右缘 26dp，先于行命中；其横向放行不影响列表区左滑。左滑手势不建议从最右 26dp 起手（MT-005 实测） |
| SwipeToDismissBox 右滑返回 | 左滑 dx<0 本就不触发返回；且行内消费后父级收不到 |
| combinedClickable 长按 | 长按=静止按压；一旦发生位移消费即取消，天然互斥 |

## 五、菜单矩阵

| 菜单 | 入口 | 条目 |
|---|---|---|
| 单曲菜单（=改造 TrackDetailScreen） | 普通模式长按歌曲 | 播放 / 下一首播放 / 加入待播队列 / 以此开始播放全部 / 缓存这首 / 删除这首的缓存 / 查看信息 / 分享 / 从当前列表移除。**移除现有"全部入队、全部缓存"两钮（违规项）** |
| 列表级菜单（新增 ListActionsScreen） | 歌曲列表头部"更多"按钮 | 播放全部 / 随机播放全部 / 全部加入待播队列 / 缓存当前列表全部 / 删除当前列表全部缓存 / 进入多选 / 排序·筛选（见 D6） |
| 多选底部栏（固定） | 多选模式 | 第 1 行：✕退出多选 · 已选 N 首；第 2 行：加入待播队列 · 缓存选中项(N) · 更多（见 D1） |
| 多选"更多"二级页 | 底部栏"更多" | 下一首播放 / 删除选中项缓存 / 从当前列表移除 / 分享 / 全选 / 取消全选 / 反选 |

新增 ViewModel 能力：`insertNext(items)`（PlaybackConnection 加 insertNext）、`removeSelectedFromList(query)`、`deleteSelectedCaches()`、`shareItems(items)`（ACTION_SEND）、`viewSongInfo(item)`（信息页）、`selectAll()/invertSelection(items)`、`shuffleAll(query)`（playback.toggleShuffle + playLoaded，或已支持）。

## 六、文件修改清单（按 ticket）

### REQ-SELECT-MT-001（普通模式左滑进多选 + 固定底部栏 + 退出规则）
- 新增 `app/src/main/java/dev/wearjelly/ui/SelectionState.kt`：状态 + enter/clear 纯函数。
- `AppViewModel.kt`：AppUiState 加 `selectionAnchorId`；新增 `beginSelectionFromSwipe(itemId)`；`toggleSongSelection` 加 1→0 自动退出；`enterSelectionMode` 保留（0 首提示态，见 D2）。
- `WearJellyApp.kt`：
  - LibraryItemRow 增加左滑 pointerInput（普通模式生效，多选模式 MT-001 阶段暂为 no-op）。
  - 新增 MultiSelectBar（固定底部：退出多选 / 已选 N 首 / 更多占位）；删除列表内"退出多选 (N)"item 与"选中入队/选中缓存"item；ScalingLazyColumn 底部 contentPadding 避让。
  - WearJellyApp 加 selectionMode BackHandler。
- 测试：SelectionState JVM 单测（进入/清空/归零自动退出不变式）。
- 对照验收：1、5、10、11（+归零退出规则）。

### REQ-SELECT-MT-002（单击切换 + 左滑范围选择 + 锚点）
- `SelectionState.kt`：toggle(anchor 更新规则)、rangeSelect(anchor→target，按当前列表序)、selectAll/invert 纯函数 + 单测。
- `AppViewModel.kt`：`toggleSongSelection` 按锚点规则改写；新增 `selectRange(itemId, orderedIds)`；`selectAll/invertSelection`。
- `WearJellyApp.kt`：多选模式左滑接 `selectRange`（从 `lib.items` 取有序 id）；确认多选模式单击=toggle（现有行为保留）；多选模式 onLongClick 置空。
- 测试：锚点/范围/归零全分支单测。
- 对照验收：2、3、4（6 的全选/反选 UI 依赖 MT-003 的二级菜单，逻辑本 ticket 完成）。

### REQ-MENU-MT-003（菜单重组）
- `AppViewModel.kt`：AppScreen.ListActions、BatchActions 页；新增 insertNext/removeFromList/deleteSelectedCaches/share/viewInfo/shuffleAll；PlaybackConnection 加 `insertNext`；列表"更多"入口。
- `WearJellyApp.kt`：TrackDetailScreen 重排（去违规项、补"下一首播放/查看信息/分享/从当前列表移除"）；新增 ListActionsScreen、BatchActionsScreen；MultiSelectBar "更多"接二级页；MultiSelectBar 第 2 行补"加入待播队列 / 缓存选中项(N)"。
- 对照验收：7、8、9（+6 UI 闭环）。

### REQ-COPY-MT-004（统一文案）
- `WearJellyApp.kt` / `AppViewModel.kt` 内字符串常量化（`ui/Copy.kt` 新增常量对象）：
  - "单曲播放"→"播放"；"下载并离线缓存"→"缓存这首"；"删除本地缓存"→"删除这首的缓存"
  - "全部入队"/"全部歌曲入队"→"全部加入待播队列"；"全部缓存"/"全部歌曲缓存"→"缓存当前列表全部"
  - "选中入队"→"加入待播队列"；"选中缓存"→"缓存选中项（N）"
  - 新增固定文案："下一首播放""删除选中项缓存""从当前列表移除""全选""取消全选""反选""退出多选""已选 N 首"
- 对照验收：7、8、9 文案逐字核对。

### REQ-TEST-MT-005（回归）
- 真机（TicWatch Pro X）回归验收 1–11；重点：左滑 vs 垂直滚动 vs 字母条 vs 右滑返回 四方冲突；单击/长按误触发率；固定栏遮挡与滚动避让；返回键行为。
- 自动化：`./gradlew test`（SelectionState 全分支）；构建 `./gradlew assembleDebug`。

## 七、待确认决策点

- **D1 底部栏布局**：规格 三.3 要求常用项含"加入待播队列、缓存选中项"，但 一.2 只要 3 元素。方案：两行紧凑栏（行1：✕退出 · 已选 N 首；行2：入队 · 缓存(N) · 更多），总高约 70dp，圆表 454px 下不遮列表中心。若你坚持单行 3 元素，则入队/缓存移入二级菜单。
- **D2 "已选 0 首"矛盾**：需求禁止 0 首停留，但列表级"进入多选"天然从 0 开始。建议：显式"进入多选"入口允许 0 首（栏内提示"点按歌曲开始选择"）；任何由选中态变化导致的 1→0 立即退出。若严格执行"0 首即退"，则"进入多选"入口改为进入即全选。
- **D3 多选范围**：MT-001/002 只做 Library SONGS 三种列表；"已缓存音乐"页是否纳入（其数据源是本地 DownloadManager，实现路径不同），建议后续 ticket。
- **D4 单曲菜单形态**：手表惯例是全屏菜单页。建议复用 TrackDetailScreen 作为"单曲菜单"（长按进入），而非弹出式菜单——与现状一致、改动小。
- **D5 "下一首播放"**：需在 PlaybackConnection 新增 `insertNext()`（Media3 addMediaItems 到 currentIndex+1），并处理未在播放时的回退（退化为 enqueue）。
- **D6 "排序/筛选"**：列表现为服务端全量拉取 + 拼音排序，无筛选基建。建议 MT-003 先放占位项或延后（单独需求）。
- **D7 "从当前列表移除"语义**：是"从 Jellyfin 播放列表/专辑里删条目"（服务端多数只读，不可行）还是"仅从本屏列表视图隐藏（本次会话）"？建议：本次会话内从视图移除并提示。

## 八、验收用例 → ticket 映射

| 用例 | 落点 |
|---|---|
| 1 左滑 A 进多选，底部"已选 1 首"+退出 | MT-001 |
| 2 单击 B 切换，归零自动退出 | MT-002 |
| 3 已选 A 左滑 C：A..C 全选，锚点=C | MT-002 |
| 4 已选 A、C 左滑 B：按锚点选 B..锚点 | MT-002 |
| 5 返回键退多选不退页 | MT-001 |
| 6 全选/反选，反选为 0 自动退出 | MT-002（逻辑）+ MT-003（UI） |
| 7 单曲菜单无"全部缓存/全部入队" | MT-003 + MT-004 |
| 8 列表级菜单含"缓存当前列表全部/全部加入待播队列" | MT-003 |
| 9 底部栏含"缓存选中项（N）/删除选中项缓存" | MT-003（缓存(N) 本 ticket 就位，删除缓存项 MT-003 二级菜单） |
| 10 退出按钮固定底部不随滚动 | MT-001 |
| 11 手势不冲突 | MT-001/002 实现，MT-005 真机验证 |

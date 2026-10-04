# 随机播放与播放历史 实现验证报告（REQ-SHUFFLE-005 / REQ-PLAY-CURRENT-006 播放层部分）

- 任务：t1 独立验证随机播放与播放历史实现（只读审查）
- 审查者：playback-verifier（AgentTeams wearmusic-verify）
- 工作目录：`D:\wearmusic`
- 审查方式：**只读**（未修改任何生产代码/测试代码；本文件是本任务新建的唯一文件）
- 审查文件：
  - `app/src/main/java/dev/wearjelly/playback/ShuffleQueue.kt`
  - `app/src/main/java/dev/wearjelly/playback/PlaybackService.kt`
  - `app/src/main/java/dev/wearjelly/playback/PlaybackConnection.kt`
  - `app/src/test/java/dev/wearjelly/playback/ShuffleQueueTest.kt`
- 外部基准（只读引用，用于核对 ExoPlayer 语义）：Gradle 缓存中的 `media3-exoplayer-1.4.1-sources.jar`、`media3-common-1.4.1-sources.jar`（项目依赖版本见 `app/build.gradle.kts:69-71`）

---

## 1. 必跑命令与结果

| # | 命令（工作目录 `D:\wearmusic`） | 结果 |
|---|---|---|
| 1 | `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat testDebugUnitTest` | `BUILD SUCCESSFUL`，exit 0（首次执行时 `:app:testDebugUnitTest UP-TO-DATE`，28 actionable tasks） |
| 2 | 同上 + `--rerun`（强制重新执行测试任务，排除缓存） | `BUILD SUCCESSFUL`，`28 actionable tasks: 1 executed, 27 up-to-date`（`:app:testDebugUnitTest` 实际执行） |

测试结果（`app/build/test-results/testDebugUnitTest/*.xml`，本次 rerun 时间戳 `2026-10-03T18:10:17/18`）：

| 测试套件 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `dev.wearjelly.playback.ShuffleQueueTest` | 9 | 0 | 0 | 0 |
| `dev.wearjelly.playback.PlayHistoryStackTest` | 4 | 0 | 0 | 0 |
| 其余（PinyinSort/SelectionLogic/LyricsLogic/SeekLogic/JellyfinRepository） | 46 | 0 | 0 | 0 |
| **合计** | **59** | **0** | **0** | **0** |

播放层 13 个测试全部通过，测试名见下文各条判定的"测试证据"。

---

## 2. 逐条判定

### 判定 1：打开随机不切换当前曲目，也不重置播放位置 —— **通过**

**实现证据**

- `PlaybackConnection.kt:308-311` `toggleShuffle()` 仅翻转 `c.shuffleModeEnabled`，不做任何 seek/播放控制。
- `PlaybackService.kt:256-261` `onShuffleModeEnabledChanged` → 只调用 `regenerateShuffleOrder()`。
- `PlaybackService.kt:330-341` `regenerateShuffleOrder()`：构造 `ShuffleQueue.generateOrder(...)` 后仅 `exo.setShuffleOrder(DefaultShuffleOrder(order, System.nanoTime()))`；**该函数体内没有 `seekTo` / `prepare` / `play` / `setMediaItems` 调用**，因此不会切歌、不会重置位置。
- `ShuffleQueue.kt:86` `currentIndex.coerceIn(0, size-1)`、`ShuffleQueue.kt:88` 与 `ShuffleQueue.kt:98` `intArrayOf(current) + fresh + deferred`：当前曲目固定在新 ShuffleOrder 首位，保证后续"下一首"从生成的本轮顺序继续。

**Media3 语义佐证（media3 1.4.1 源码）**

- `ExoPlayerImpl.setShuffleOrder`（`ExoPlayerImpl.java:788-810`）：`checkArgument(shuffleOrder.getLength() == mediaSourceHolderSnapshots.size())`（:791，服务端用 `exo.mediaItemCount` 作为 size，长度恒等，不会抛错）；随后 `maskTimelineAndPosition(playbackInfo, timeline, maskWindowPositionMsOrGetPeriodPositionUs(timeline, getCurrentMediaItemIndex(), getCurrentPosition()))`，`positionDiscontinuity = false`、`repeatCurrentMediaItem = false`（:794-809）——即保持当前索引与当前位置不变。
- `ExoPlayerImpl.setShuffleModeEnabled`（`ExoPlayerImpl.java:861-873`）：仅设置标志位并派发 `EVENT_SHUFFLE_MODE_ENABLED_CHANGED`，不触碰 timeline/播放位置。

**测试证据**

- `ShuffleQueueTest.kt:19-26` `current index stays first`（断言 `order[0]==7`、长度 20、全排列）
- `ShuffleQueueTest.kt:28-33` `current index out of bounds is clamped`
- `ShuffleQueueTest.kt:48-52` `single track queue keeps that track`、`ShuffleQueueTest.kt:14-17` `empty queue yields empty order`

**说明**：单元测试只覆盖"当前曲目排首位"的纯逻辑；"开关随机不切歌/不重位置"依赖服务层集成行为，由上述代码路径 + Media3 源码共同佐证，无自动化测试直接覆盖（见问题 F1）。

---

### 判定 2：随机顺序只通过 `ExoPlayer.setShuffleOrder` 替换 ShuffleOrder；timeline 原序不被改写，队列页保持原序 —— **通过**

**实现证据**

- `PlaybackService.kt:330-341`：整个随机顺序应用点只有 `exo.setShuffleOrder(...)` 一处（`grep` 全仓仅此一个调用）；`regenerateShuffleOrder` 不调用 `moveMediaItem` / `setMediaItems` / `removeMediaItem`，`ShuffleQueue.kt:69` 的 KDoc 亦声明 "the Media3 timeline remains in original queue order"。
- 队列页数据源：`PlaybackConnection.kt:156-167` 按 `for (i in 0 until player.mediaItemCount) player.getMediaItemAt(i)` 构建 `queue` —— 即 timeline 窗口索引序；`PlaybackConnection.kt:103-105` `onTimelineChanged` 只刷新状态，不重排；`PlaybackConnection.kt:169-183` `updateStateFromPlayer` 写入 `queue` 与 `currentIndex = player.currentMediaItemIndex`。
- 队列重排仅由用户显式操作触发：`PlaybackService.kt:191-198` `CUSTOM_COMMAND_MOVE_ITEM`（来自 `PlaybackConnection.kt:287-294` 的用户拖动），与随机逻辑无关。

**Media3 语义佐证**

- `PlaylistTimeline.java:42-69`：窗口索引由 playlist（`mediaSourceInfoHolders` 顺序）决定（`firstWindowInChildIndices` 按 holder 顺序累加），`shuffleOrder` 仅作为参数传给父类 `AbstractConcatenatedTimeline`（:49 `super(/* isAtomic= */ false, shuffleOrder)`），只影响 `getFirstWindowIndex/getNextWindowIndex/getPreviousWindowIndex`，**不改变窗口枚举顺序**——因此 `getMediaItemAt(i)`、`currentMediaItemIndex` 永远是原队列序。
- `ExoPlayerImpl.java:2418-2420`（`cloneAndInsert`）、`:2476`（`cloneAndRemove`）：播放项增删时 ExoPlayer 自动克隆 ShuffleOrder 并保持长度一致，服务端 `setShuffleOrder` 的长度校验（:791）不会因增删失配。

**测试证据**

- `ShuffleQueueTest.kt:75-80` `order is a permutation of all indices`（生成结果是索引的全排列，仅是对 timeline 索引的重排方案，不产生任何 playlist 写操作——`ShuffleQueue` 为纯函数，`ShuffleQueue.kt:68-99` 无副作用）

---

### 判定 3：最近播放 5 首排到本轮末尾；小队列逐条放宽排除窗口；绝不丢曲目（全排列 + 当前曲目首位） —— **通过**

**实现证据**

- `ShuffleQueue.kt:71` `RECENTLY_PLAYED_AVOID_COUNT = 5`；`PlaybackService.kt:337` `recentTrackIds = playHistory.recent(ShuffleQueue.RECENTLY_PLAYED_AVOID_COUNT)`。
- `ShuffleQueue.kt:90` `recent = recentTrackIds.take(5)` —— 只取 5 首。
- `ShuffleQueue.kt:91-96`：`excludedCount` 从 `recent.size` 起，仅当 `fresh.isEmpty()`（其余曲目全部落入排除窗口、无曲目可播）时 `excludedCount--` **逐条放宽**（每次减 1，从窗口尾部即最旧记录开始放宽），直到 `excludedCount == 0` 保证循环终止。
- `ShuffleQueue.kt:97-98`：`deferred = others.filterNot { it in fresh }` 与 `fresh` 构成 `others` 的一个划分（partition），返回 `intArrayOf(current) + fresh + deferred`。由构造可知：结果恒为 `0 until size` 的**全排列**（`current` 恰好一次 + `others` = 其余全部索引，二者无交、无遗漏），**绝不丢曲目**；`fresh`（未被排除的曲目）在前，`deferred`（最近播放曲目）**恒在末尾**。
- `ShuffleQueue.kt:85-88`：`size<=0` 返回空数组；`others` 为空（单曲队列）返回 `[current]`。
- `PlaybackService.kt:337` 的 `recent(5)` 为"最新在前"（`ShuffleQueue.kt:31-32` `entries.take(cursor+1).asReversed()`），因此放宽时先放弃的是最旧的排除项、保留最新的排除项，方向正确。

**关于"当前曲目同时属于最近 5 首"**：条目 3 要求"当前曲目固定在首位"，与"5 首排末尾"天然冲突时以首位为准——此时最近 5 首中的其余 4 首仍被 defer 到末尾；测试用例也按"current 不在 recent 集合内"构造，与实现语义一致（见下）。

**测试证据（均为本次 rerun 通过）**

- `ShuffleQueueTest.kt:35-46` `recently played tracks are pushed to end of round`：断言 `order.takeLast(5)` 的 id 集合 == recent 集合（固定随机源 `Random(42)`）。
- `ShuffleQueueTest.kt:82-91` `recent beyond five is not avoided`：8 首 recent 只避开前 5 首，断言 `lastFive == recent.take(5)`。
- `ShuffleQueueTest.kt:54-63` `small queue fallback - all remaining tracks recent still returns full order`：3 曲队列、其余全部 recent 时仍返回全排列且 `order[0]==0`（实际触发排除窗口 2→1 的放宽一步）。
- `ShuffleQueueTest.kt:65-73` `small queue relaxes the recent exclusion window one item at a time`：断言 `order[0]=="current"`、`order[1]=="t5"`（唯一未被排除的曲目排最前，recent 殿后）。
- `ShuffleQueueTest.kt:19-26` `current index stays first`、`ShuffleQueueTest.kt:75-80` `order is a permutation of all indices`、`ShuffleQueueTest.kt:14-17` `empty queue yields empty order`。

**说明**：放宽循环的"多步"路径（`excludedCount` 连续递减 ≥2 次）经人工推演正确（仅当窗口内存在不在队列的旧 id/当前曲目占用槽位时才会多步触发），但现有测试最多只覆盖一步放宽（见问题 F2）。

---

### 判定 4：随机模式下"上一首"沿真实播放历史回退、不来回弹跳；回退后再向前开启新分支；历史中已不在队列的曲目被跳过 —— **通过**

**实现证据**

- 入口统一：`PlaybackConnection.kt:257-264` `previous()` 发送 `CUSTOM_COMMAND_PREVIOUS_HISTORY` → `PlaybackService.kt:205` → `handlePrevious()`；UI 按钮 `WearJellyApp.kt:2016 → AppViewModel.kt:622 → playback.previous()` 走同一条路径。
- `PlaybackService.kt:344-366` `handlePrevious()`：`exo.shuffleModeEnabled` 时进入历史分支。
  - **不弹跳**：`PlaybackService.kt:348-353` 谓词查找曲目在队列中的索引；`ShuffleQueue.kt:37-46` `goToPrevious` 严格 `for (index in cursor - 1 downTo 0)` 单向向下移动游标并落位（`cursor = index`），每次回退必然更早，不会在两首间往返。
  - 回退 `seekTo(previousIndex, 0L)`（`PlaybackService.kt:355`）触发的 `onMediaItemTransition` 再次 `recordHistory` 时命中 `ShuffleQueue.kt:16` 的同曲去重（`entries[cursor] == trackId`），不会把刚回退到的曲目重复压栈或把游标推回。
  - **历史走尽的安全回落**：`PlaybackService.kt:360-364` —— 位置 >3s 重启当前，否则 `seekToPreviousMediaItem()`。由于每次转场/开启随机都会 `regenerateShuffleOrder()`（`PlaybackService.kt:251-253`、`:256-261`）把当前曲目固定在 ShuffleOrder 首位，`DefaultShuffleOrder.getPreviousIndex(current)` 返回 `C.INDEX_UNSET`（`ShuffleOrder.java:97-100`），而 `Player#seekToPreviousMediaItem` 文档明确 "Does nothing if `hasPreviousMediaItem()` is false"（`Player.java:2679-2691`），因此回落路径不会随机乱跳，**结构上排除了两首来回弹跳**。
  - **向前开启新分支**：`ShuffleQueue.kt:17-22` —— 回退后（`cursor` 已下移）记录新曲目时 `entries[cursor+1] != trackId`，先截断 `cursor+1` 之后的全部条目再追加，形成新分支；若恰好等于原下一首则仅 `cursor++` 复用分支（`ShuffleQueue.kt:17-20`）。
  - **不在队列的历史被跳过**：`PlaybackService.kt:348-353` 谓词中 `firstOrNull { ... } ?: -1`，找不到时 `previousIndex >= 0` 为 false，`goToPrevious` 继续向更早条目查找（`ShuffleQueue.kt:38-44`），不会 seek 到非法索引。
- 记录时机：`PlaybackService.kt:246-249`（转场 + `playWhenReady` 判定，规避 BUFFERING 旧 bug）、`:227`（`onIsPlayingChanged(true)` 兜底补记）、`:314-324` `recordHistory`。

**测试证据（PlayHistoryStackTest，位于 `ShuffleQueueTest.kt:94-139`，本次 rerun 全部通过）**

- `ShuffleQueueTest.kt:96-110` `previous walks actual history without bouncing and new playback branches`：c→b→a 单向回退、`goToPrevious` 到底返回 `null`、回退后 `record("d")` 断言 `recent(5) == [d, a]`（c、b 被截断 = 新分支）。
- `ShuffleQueueTest.kt:112-118` `previous skips unavailable history items`：断言跳过不可用条目后返回 `"a"`。

**说明**：本判定覆盖 App 内"上一首"按钮路径；系统/外部控制器（蓝牙等）走 Player 原生 `seekToPrevious`，不经该命令（见问题 F3）。

---

### 判定 5：PlayHistoryStack 容量上限、重复记录、remove/clear 与 recent 语义 —— **通过**

**实现证据**

- **容量上限**：`ShuffleQueue.kt:6-9` `maxEntries = capacity.coerceAtLeast(1)`（capacity≤0 退化为 1，不会出现 0 容量死循环/负游标）；`ShuffleQueue.kt:24-27` 追加后 `while (entries.size > maxEntries) { entries.removeAt(0); cursor-- }`——从头部淘汰并同步下移游标，保持 `cursor == entries.lastIndex`（活跃历史恒 ≤ 容量）。默认容量 `ShuffleQueue.kt:64` `DEFAULT_CAPACITY = 50`，服务端 `PlaybackService.kt:55` 使用默认值。
- **重复记录**：`ShuffleQueue.kt:16` 空白 id 与"与当前条目相同"的 id 直接忽略（连播同曲不重复压栈）；`ShuffleQueue.kt:17-20` 回退后重放原下一首 → 仅 `cursor++` 复用原分支（保留更远历史）；`ShuffleQueue.kt:21-23` 其余情况截断未来并追加 → 开新分支。三类语义互不冲突。
- **remove**：`ShuffleQueue.kt:48-56` 删除**所有**同 id 条目；`if (index <= cursor) cursor--` 保证游标仍指向同一活跃条目；末尾 `cursor = cursor.coerceAtLeast(-1)` 兜底防空游标。
- **clear**：`ShuffleQueue.kt:58-61` 清空并 `cursor = -1`；服务端在清队列时调用（`PlaybackService.kt:201`）。
- **recent 语义**：`ShuffleQueue.kt:31-32` `entries.take(cursor + 1).asReversed().take(count.coerceAtLeast(0))` —— ①最新在前；②只取 `cursor` 及以前的"实际已播放"部分，**回退后留在 `cursor` 之后的未来条目不出现在 recent 中**（与 KDoc `ShuffleQueue.kt:30` 一致）；③负数/0 计数返回空。`size = cursor + 1`（`ShuffleQueue.kt:12`）与该"活跃历史"口径一致。

**测试证据**

- `ShuffleQueueTest.kt:120-130` `capacity and remove are enforced`：capacity=3 记录 4 首后 `recent(10) == [d, c, b]`（头部淘汰）；`remove("c")` 后 `[d, b]`；`clear()` 后 `size == 0`、`goToPrevious` 返回 `null`。
- `ShuffleQueueTest.kt:132-138` `blank ids are ignored`：空/空白 id 记录后 `size == 0`。
- `ShuffleQueueTest.kt:96-110` `previous walks actual history without bouncing and new playback branches`：断言回退后 `recent(5) == [b, a]`（未来条目 `c` 被剔除）、新分支后 `recent(5) == [d, a]`。

**说明**：`remove`/`contains` 在生产代码中暂无调用点（见问题 F4），语义本身由测试覆盖且与 `goToPrevious` 谓词跳过机制互补。

---

## 3. 问题清单（无阻塞项；均为低/中等级）

| ID | 严重级别 | 问题 | 证据 | 最小修复建议 |
|---|---|---|---|---|
| F1 | 低 | "打开随机不切歌/不重置位置"仅有代码审查 + Media3 源码佐证，**无自动化测试**直接覆盖服务层该行为（`ShuffleQueue` 纯函数测试无法验证 `PlaybackService` 的监听链路）。 | `PlaybackService.kt:256-261, 330-341`；测试目录 `app/src/test/java/dev/wearjelly/playback/` 无服务层用例 | 补一条 Robolectric/instrumentation 测试：构造 ExoPlayer → 播至第 k 首 → `shuffleModeEnabled = true` → 断言 `currentMediaItemIndex` 与 `currentPosition` 不变、`ShuffleOrder` 已替换。 |
| F2 | 低 | 测试名 `small queue relaxes the recent exclusion window one item at a time` 与实际覆盖不符：该用例中 `fresh` 在 `excludedCount=5` 时即非空（`t5` 未被排除），**放宽循环根本未执行**；多步放宽（`excludedCount` 连续递减 ≥2）无任何测试覆盖。 | `ShuffleQueueTest.kt:65-73` vs `ShuffleQueue.kt:91-96` | 补一个真正触发多步放宽的用例：recent 中混入 ≥2 个不在队列的 id（如 `size=3, currentIndex=0, recent=["c","q1","q2","a","b"]`），断言结果仍为全排列且最旧的在队曲目被放宽到前段。 |
| F3 | 中 | 系统/外部控制器（蓝牙耳机、通知栏、Android Auto 等）的"上一首"走 Player 原生 `seekToPrevious`，**不经 `CUSTOM_COMMAND_PREVIOUS_HISTORY`**，随机模式下不会沿历史回退；叠加"每次转场后当前曲目固定在 ShuffleOrder 首位"，该路径在重复模式关闭时表现为**无操作**（`hasPreviousMediaItem()==false`），用户按耳机上一首无反应。 | `PlaybackService.kt:121-124`（接受 `DEFAULT_PLAYER_COMMANDS`）；`PlaybackConnection.kt:257-264`（仅 App 内自定义命令走历史）；`Player.java:2679-2691`；`ShuffleOrder.java:97-100` | 在 `MediaSession.Callback.onPlayerCommand` 中拦截 `COMMAND_SEEK_PREVIOUS` 转发到 `handlePrevious()`；或（更保守）在随机开启时对外部 previous 统一走同一历史回退逻辑。 |
| F4 | 低 | `PlayHistoryStack.remove` / `contains` 无生产调用点（队列移除曲目时历史条目保留，依赖 `goToPrevious` 谓词跳过——功能正确，但 `remove` 属于未接线代码；重复 id 队列中 `goToPrevious` 只匹配首个索引）。 | `ShuffleQueue.kt:48-56`、`:34`；`PlaybackService.kt:185-190`（REMOVE_AT 只调 `player.removeMediaItem`） | 二选一：①在 `CUSTOM_COMMAND_REMOVE_AT` 中同步 `playHistory.remove(mediaId)` 保持历史与队列一致；②删除未用 API 并在注释中说明"由谓词跳过"的取舍。 |
| F5 | 低 | `recordHistory` 依赖 `mediaMetadata.extras` 中的 `jellyfin_item_json`，缺失/反序列化失败时**该次转场既不记 HistoryStore 也不记 playHistory**（静默 `return`），会导致随机避让与历史回退缺账。当前 `createMediaItem`（`PlaybackService.kt:437-439`）恒写入 extras，风险低，但缺少告警。 | `PlaybackService.kt:314-324` | 退化路径也记录 `playHistory.record(itemId)`（纯 id 栈不依赖 JSON），仅 HistoryStore 依赖完整实体；或至少打一条日志。 |
| F6 | 低 | 冷启动 `restoreLastPlayback()`（`PlaybackService.kt:219`）在 `exo.addListener(...)`（`PlaybackService.kt:221`）**之前**设置 `exo.shuffleModeEnabled`，该次开关不会触发 `onShuffleModeEnabledChanged` → 首个随机顺序仍是 ExoPlayer 默认随机序（非 recent-避让），直到第一次转场才被 `regenerateShuffleOrder()` 纠正。 | `PlaybackService.kt:219` vs `:221`、`:256-261` | 注册 listener 之后再恢复 shuffle 标志，或在 `restoreLastPlayback()` 末尾对 `shuffleModeEnabled == true` 主动调用一次 `regenerateShuffleOrder()`。 |
| F7 | 信息 | 随机模式下 `handlePrevious` 走历史分支时**从不"重启当前曲目"**（总是直接回到上一历史曲目），而历史走尽后的回落分支在位置 >3s 时会重启当前——两条路径的"上一首"语义不一致。 | `PlaybackService.kt:344-359` vs `:360-364` | 产品决策项：如需与顺序模式一致，可在历史分支前加"当前曲目播放 >3s 且 `cursor == 最近一次转场` 则先重启当前"的判断；否则在注释中明确该差异为有意设计。 |
| F8 | 信息 | `REPEAT_MODE_ALL` + 随机且历史走尽时，回落的 `seekToPreviousMediaItem()` 在 repeat-all 下 `hasPreviousMediaItem()` 恒为 true，会环绕到 ShuffleOrder 末位（非历史曲目），随后由 `record()` 截断成新分支——不构成弹跳，但与"沿历史回退"表述略有出入。 | `PlaybackService.kt:360-364`；`Player.java:2679-2691`（repeat mode 参与 previous 判定） | 如需严格历史语义：`shuffleModeEnabled && playHistory.size > 1` 之外的场景直接 `exo.seekTo(0L)`（重启当前）。 |

**阻塞项：无。** 未发现导致下游不得继续的真实缺陷；5 条验收全部通过。

---

## 4. 结论

| 条目 | 结论 | 关键证据 |
|---|---|---|
| 1. 打开随机不切歌、不重置位置 | **通过** | `PlaybackService.kt:256-261, 330-341`（无 seek/prepare/play）；`ShuffleQueue.kt:98`；`ExoPlayerImpl.java:788-810`（positionDiscontinuity=false）；测试 `current index stays first` |
| 2. 仅替换 ShuffleOrder、timeline 原序不改写、队列页原序 | **通过** | `PlaybackService.kt:340`（唯一 setShuffleOrder）；`PlaybackConnection.kt:156-167`；`PlaylistTimeline.java:42-69`；测试 `order is a permutation of all indices` |
| 3. 最近 5 首末尾 + 逐条放宽 + 全排列 + 当前首位 | **通过** | `ShuffleQueue.kt:71, 90-98`；测试 `recently played tracks are pushed to end of round`、`recent beyond five is not avoided`、`small queue fallback...`、`current index stays first`、`order is a permutation...` |
| 4. 上一首沿历史回退不弹跳、新分支、跳过失效曲目 | **通过** | `PlaybackService.kt:344-366`；`ShuffleQueue.kt:15-23, 37-46`；测试 `previous walks actual history without bouncing and new playback branches`、`previous skips unavailable history items` |
| 5. 容量/重复/remove/clear/recent 语义 | **通过** | `ShuffleQueue.kt:6-66`；测试 `capacity and remove are enforced`、`blank ids are ignored`、`previous walks actual history...` |

- 必跑命令 `gradlew testDebugUnitTest` 两次执行（含 `--rerun` 强制重跑）均 `BUILD SUCCESSFUL`，59 个单测 0 失败，播放层 13 个全绿。
- 问题清单共 8 项（1 中 / 5 低 / 2 信息），无阻塞项，均给出最小修复建议；按任务约束**未修改任何代码**。

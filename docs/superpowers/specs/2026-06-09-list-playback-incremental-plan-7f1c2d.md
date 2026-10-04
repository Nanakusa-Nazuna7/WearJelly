# 列表与播放需求增量方案

> 状态：研究方案，等待用户确认；未开始实现。

## 现状与增量方案

现有实现已包含拼音排序工具、多选状态逻辑、主页 Mini Player 队列入口、播放器页队列入口和队列页当前曲目高亮。缺口主要在能力未统一覆盖歌曲列表，或播放行为依赖 Media3 默认逻辑。

- **排序**：复用 `PinyinSort` comparator，不为列表单独实现规则。现有行为是英文大小写不敏感、中文转拼音、数字和符号归入 `#` 并排在字母后；不忽略 `The`、`A` 等冠词。混合语言使用同一排序键，不依赖设备 locale。下载列表改为此排序，其他歌曲列表也复用同一入口。HistoryList 维持最近播放优先，不使用该 comparator。
- **HistoryList**：关闭字母索引，按播放时间倒序，并接入现有 MultiSelect 与缓存状态展示。
- **字母索引**：扩展现有 `AlphabetListScaffold`，支持普通点击/短滑跳转与长按预览。长按后根据手指位置显示最多 7 个邻近字母；边界处只显示可用字母。松手跳转；圆形屏安全区与实际触控列入设备验收。
- **滚动恢复**：离开列表执行歌曲操作前保存歌曲 ID、索引和偏移；返回时优先按歌曲 ID 恢复，歌曲被移除则恢复到相邻位置。关闭操作页、清除多选和恢复列表位置由同一返回状态转换协调，避免顶部闪回。
- **主页队列入口**：保留现有 Mini Player 队列按钮并回归验证；队列页继续读取唯一的 `CurrentQueue`。
- **随机播放**：保持 `CurrentQueue` 原始顺序；增加 `ShuffleQueue` 索引顺序与洗牌位置，开关随机时不切换当前歌曲。`PlayHistoryStack` 记录真实曲目转场供上一首回退。随机列表循环重新洗牌时排除最近 5 首；队列太短无法满足时逐步放宽排除范围。单曲循环优先于随机。队列页可以按统一排序展示歌曲投影，但不得改变底层队列顺序。
- **点击当前曲目**：列表点中当前曲目时只导航到 HomePlayer，不重新设置队列或从头播放；队列页同样按歌曲 ID 判断。
- **多选**：复用 `SelectionLogic` 与现有底部操作栏，将相同手势接入下载、歌手/专辑详情、播放列表、搜索、队列和历史歌曲列表。不改非歌曲列表，也不重做现有 AllSongsList MT 多选。

## 状态机

| 转换 | 行为 |
|---|---|
| 单选模式 + 行左滑 | 进入 MultiSelect 并选中该行 |
| MultiSelect + 单击 | 切换选择；选中数归零时退出 |
| MultiSelect + 左滑 | 从最近已选项锚点扩展范围 |
| MultiSelect + 系统返回 | 优先退出多选，不离开当前页面 |
| 列表 + 长按歌曲 | 保存滚动锚点并打开歌曲操作页 |
| 操作完成或取消 | 关闭操作页并恢复锚点；歌曲已移除则恢复到附近 |
| 索引普通点击/短滑 | 跳到目标字母分组 |
| 索引长按 | 进入预览；移动实时更新；松手跳转并退出预览 |
| 随机关闭 → 开启 | 以当前歌曲为锚点生成 ShuffleQueue，不切歌 |
| 随机开启 + 下一首 | 沿 ShuffleQueue 前进；单曲循环时继续当前歌曲 |
| 随机开启 + 上一首 | 回退 PlayHistoryStack，而不是反向遍历 ShuffleQueue |
| 随机关闭 | 保持当前曲目，后续按 CurrentQueue 原序播放 |
| 歌曲列表点击当前曲目 | 打开 HomePlayer，不重置队列或播放进度 |

## Ticket 与文件范围

| Ticket | 增量范围 | 主要文件 |
|---|---|---|
| `REQ-LIST-SORT-001` | 下载及其他歌曲列表统一排序；History 按时间倒序并关闭索引 | `app/src/main/java/dev/wearjelly/data/PinyinSort.kt`、`app/src/main/java/dev/wearjelly/ui/WearJellyApp.kt`、必要时 `app/src/main/java/dev/wearjelly/ui/AppViewModel.kt` |
| `REQ-LIST-INDEX-002` | 索引点击、短滑、长按 7 字母预览和松手跳转 | `app/src/main/java/dev/wearjelly/ui/WearJellyApp.kt` |
| `REQ-LIST-SCROLL-003` | 长按操作后恢复歌曲位置；批量操作退出多选后恢复 | `app/src/main/java/dev/wearjelly/ui/WearJellyApp.kt`、`app/src/main/java/dev/wearjelly/ui/AppViewModel.kt` |
| `REQ-HOME-QUEUE-004` | 保留并验证现有主页入口；队列视图读取同一 CurrentQueue | `app/src/main/java/dev/wearjelly/ui/WearJellyApp.kt`、`app/src/main/java/dev/wearjelly/ui/AppViewModel.kt` |
| `REQ-SHUFFLE-005` | 洗牌顺序、最近 5 首排除、真实历史回退、原序恢复 | `app/src/main/java/dev/wearjelly/playback/PlaybackModels.kt`、`app/src/main/java/dev/wearjelly/playback/PlaybackConnection.kt`、`app/src/main/java/dev/wearjelly/playback/PlaybackService.kt` |
| `REQ-PLAY-CURRENT-006` | 当前曲目点击只打开播放器，不重新 set queue/seek 到零 | `app/src/main/java/dev/wearjelly/ui/AppViewModel.kt`、`app/src/main/java/dev/wearjelly/ui/WearJellyApp.kt` |
| `REQ-MULTISELECT-ALL-007` | 将现有 MultiSelect 接入各歌曲类列表和操作栏 | `app/src/main/java/dev/wearjelly/ui/SelectionState.kt`、`app/src/main/java/dev/wearjelly/ui/WearJellyApp.kt`、`app/src/main/java/dev/wearjelly/ui/AppViewModel.kt` |
| `REQ-TEST-008` | 排序、索引状态、播放队列和多选回归；圆屏/手表设备验收 | 现有 `app/src/test/java/dev/wearjelly/ui/SelectionLogicTest.kt`；新增纯逻辑测试；设备手工验收 |

建议依赖顺序：`SORT → INDEX`；`SCROLL`、`SHUFFLE`、`PLAY-CURRENT` 可独立；`HOME-QUEUE` 和 `MULTISELECT-ALL` 在队列页接入处协调；最后 `TEST` 汇总回归。现有主页与播放器页已有队列入口，不重复新增。

## 验收用例映射

| 验收 | Ticket | 方式 |
|---|---|---|
| 全部歌曲、缓存、详情、搜索等顺序一致；大小写、拼音、数字/符号边界固定 | SORT | JVM comparator 测试 + 多列表核对 |
| History 最新播放在前且无字母索引，空/非空均成立 | SORT、INDEX | 列表核对 |
| 第 50 首长按加入队列后仍在附近；移除后恢复到相邻位置 | SCROLL | 手表实际滚动操作 |
| 长按索引预览最多 7 字母；移动高亮更新；松手定位；顶部/底部不越界 | INDEX | 模拟器/真机手势与圆形屏检查 |
| 主页一键进入同一队列，当前曲目高亮 | HOME-QUEUE | 从主页、播放器、歌词页分别进入核对 |
| 随机开启不切歌；下一首按洗牌；上一首按真实历史；关闭后按原序 | SHUFFLE | 播放状态/转场测试 |
| 随机列表循环不立即重复最近 5 首；单曲循环优先 | SHUFFLE | 队列算法测试，覆盖小队列边界 |
| 点击当前曲目不重置播放进度或队列，并打开播放器 | PLAY-CURRENT | 全部歌曲和队列页验证 |
| 缓存、详情、队列、历史多选行为一致；History 无索引 | MULTISELECT-ALL | 左滑、单击、范围、归零、返回键检查 |
| 圆形屏触控、安全区、长列表滚动性能 | TEST | 手表/圆形模拟器手工验收 |

现有配置有 JVM 单元测试，但未见 Compose UI 手势测试依赖。因此圆屏、索引触控和滚动恢复先列为设备手工验收，不为此需求默认引入新的 UI 测试框架。

## 现状依据

- `PinyinSort` 已处理中文拼音及 `#` 分组；AllSongs 已使用，下载列表仍按最近播放/下载时间自定义排序。
- HistoryStore 当前按最近记录优先，但 History 页面仍复用带字母索引的 scaffold，且没有 MultiSelect。
- 字母索引现有活动窗口为 5 个字母，按移动手势 scrub；尚无长按预览和普通点击跳转。
- 歌曲长按打开 TrackDetail 页面；歌曲操作有的留在页面，有的返回列表。列表状态为页面本地 remember 状态，离开页面时缺少可靠的歌曲锚点恢复。
- 播放随机依赖 Media3 内置 shuffle；上一首依赖 Media3 previous，不是显式真实播放历史栈。点击歌曲会重新调用播放队列设置。
- SelectionLogic 已有纯逻辑测试；下载、历史和队列列表尚未统一接入完整的多选手势。

本文件记录的是增量方案，不代表用户已批准开始实现。

# t32 视觉验收与实机复测脚本（v1）

> 前置：t30/t31 完成后执行。设备 = TicWatch Pro X（192.168.137.203:5555，Android 9，454x454 圆屏）。
> outOfScope：不改 `app/src/main/java/dev/wearjelly/` 任何源码。

## 0. APK 与安装

| 步骤 | 命令/动作 | 通过判据 |
|---|---|---|
| 0.1 | 确认 t30/t31 均 completed（agent_teams_status） | 两任务 completed |
| 0.2 | `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug --console=plain` | exit 0 |
| 0.3 | 检查 `app\build\outputs\apk\debug\app-debug.apk` LastWriteTime **晚于 t30/t31 完成时刻**（旧基线 = 2026/10/6 10:55:12, 33,562,337 bytes） | 时间戳更新 |
| 0.4 | `adb -s 192.168.137.203:5555 install -r app\build\outputs\apk\debug\app-debug.apk` | Success |
| 0.5 | `adb shell am force-stop dev.wearjelly` + `adb shell monkey -p dev.wearjelly -c android.intent.category.LAUNCHER 1`（t32 verify 命令） | 应用启动到首页 |

## 1. 音量面板退出路径（captain 增补，最高优先）

背景：用户实机反馈「音量界面无法退出」。源码静态面（m00386 时刻）：`VolumeOverlay`（AudioVolumeControls.kt:317-336）`if (!visible) return` + `BackHandler(onBack=onDismissRequest)`；WearJellyApp.kt:301 `userSwipeEnabled = !showVolume` —— 面板开启时左缘滑动被禁，唯一关闭路径 = 系统返回手势。若该手势在实机被表冠/滑动条拦截，则无退出路径。

| # | 场景 | 操作 | 通过判据 |
|---|---|---|---|
| 1.1 | 打开 | 播放页 → 点音量入口 | 音量面板全屏出现（OCR 含「音量 N%」） |
| 1.2 | 返回键关闭 | 按 back（`android_interact button:back`） | 面板消失，回到播放页 |
| 1.3 | 重开再关 | 再开面板 → 再按 back | 同 1.2（排除一次性偶然） |
| 1.4 | 左滑关闭（替代路径） | 面板开启时从左缘向右滑（若 userSwipeEnabled 已放开则 SwipeToDismissBox 接管） | 记录实际行为；若禁用属预期（t27 F5），不算缺陷但记录 |
| 1.5 | 深层页面打开 | 从队列/歌词/设置等页进入音量（若入口存在）再返回 | 返回后仍在原页面，非跳首页 |

任一失败 → 记录 blocker/high + 截图，建议修复方向：VolumeOverlay 增加屏上关闭按钮（不依赖手势）。

## 2. 播放页布局（t31 修复面，454x454 圆屏）

对照参考图：.review\ 下 em_XX 系列、08_ring、09_bottom、verify-v17-launch.png。

| # | 检查项 | 通过判据 |
|---|---|---|
| 2.1 | 启动播放 → 截图 | 主控（上一首/暂停/下一首）位于圆心区域，OCR 可见 |
| 2.2 | 曲目信息 | 标题/艺人位于顶部时间安全区内，无裁切 |
| 2.3 | 圆形进度环 | 进度环完整渲染，不被屏幕圆边裁切 |
| 2.4 | 底部操作行 | 音量/队列/收藏/歌词入口在底部可见且互不重叠（OCR 逐一确认文本） |
| 2.5 | 滚动策略 | 页面可滚动时无控件被永久裁切（滚动前后各一截图） |
| 2.6 | 重叠检测 | `android_ui_tree` bounds 两两比对：主控区/信息区/底部区无相交矩形 |

## 3. 多选长按菜单（t30 修复面）

对照参考图：05/06/07_selection 系列。

覆盖列表：艺人/专辑/所有歌曲/下载/历史/队列（t30 objective 六列表）。

| # | 检查项 | 通过判据 |
|---|---|---|
| 3.1 | 进入列表 | 各列表页 OCR 到条目文本 |
| 3.2 | 长按条目 | `android_interact` gesture（按住 ~600ms）→ 出现多选栏/选中态 |
| 3.3 | 多选栏 | OCR/ui_tree 读到选中计数或多选栏控件 |
| 3.4 | 更多按钮 | 点多选栏「更多/操作」→ 导航到 BatchActions 页（OCR 到批量操作标题/按钮） |
| 3.5 | 退出多选 | back 退出多选（非退出页面），再 back 才离开列表 |
| 3.6 | 六列表抽查 | 至少艺人+歌曲+下载三列表实机复测，其余记录 |

## 4. 回归抽查（次要）

- 收藏心形：播放页点收藏 → OCR/状态变化。
- 队列页：进队列 → OCR 条目 → 返回。
- 歌词页：进歌词 → OCR 文本 → 返回。
- 崩溃监控：每场景后 `adb logcat -d -b crash` 零 dev.wearjelly FATAL。

## 5. 证据与报告

- 截图存 `.review\t32\`：`v01_volume_open.png`、`v02_player_main.png`、`v03_selection_bar.png`、`v04_batchactions.png` …
- `agent_teams_update_task`：acceptanceResults 逐条 + commandsRun（install/launch/logcat）+ changedPaths=[]（不改源码）。
- 结论报告：通过项/缺陷项（含 severity + 截图 ref）→ `agent_teams_send_message` to=captain。

## 6. 已知基线数据

- 旧 APK：33,562,337 bytes @ 2026-10-06 10:55:12；设备已装 v1.4.0 (vc 5)，lastUpdateTime 2026-10-06 15:52:04。
- 设备运行 pid 基线：19041（t30/t31 前旧版本）。
- verify 命令（契约）：`adb shell am force-stop dev.wearjelly` / `adb shell monkey -p dev.wearjelly -c android.intent.category.LAUNCHER 1`。

# t35 复验结果 — 播放页与列表修复（attempt 4）

- 日期：2026-10-06（模拟器会话 10:20–11:05 本地）
- 设备：emulator-5554（Wear33 AVD，sdk_gwear_x86_64，454×454 圆屏，Android 13）
- APK：app-debug.apk mtime 2026-10-06 18:18:53 本地；安装后 `dumpsys package dev.wearjelly` lastUpdateTime=2026-10-06 10:27:59 UTC（> t33/t34 updatedAt，验收①）
- 环境：mock-jellyfin http://127.0.0.1:8096 运行中（node.exe D:\wearmusic\.review\mock-jellyfin.js）；/Artists 200；/Audio/s1/stream.mp3 200 audio/mpeg（ID3）。登录态 nazuna 保留。
- 判定总览：**验收①②③⑤ PASS；验收④ FAIL（第二路径不存在）**。任务 objective 允许"或如仍失败则提供可复现证据"——④的失败已附源码+运行时双重可复现证据。

## 验收①：最新 APK 安装启动成功且版本时间晚于 t33/t34 — PASS
- `adb install -r` → Success；force-stop + monkey LAUNCHER 启动正常。
- lastUpdateTime（2026-10-06 10:27:59 UTC）晚于 t33/t34 任务 updatedAt；APK 构建时间 18:18:53 本地为最新构建。

## 验收②：播放页曲名/歌手/进度条可见，队列文字完整，底部触控不重叠 — PASS
- ui_tree：WearJelly(153,28,149×38)/Alpha Song(143,66,168×38)/Mock Artist(163,104,129×28) 文本节点齐全；上排 3 钮 y153–261、下排 3 钮 y298–402，两排分离不重叠。
- 队列入口为 52dp 图标钮（t32 B2 布局重设计，不再有截断的"队列 (N)"文字）。
- 与 ReferencePlayerLayout（HorologistPlayerScreen.kt:427-518）配色/布局吻合。
- 存证：01-player.png(24647B)、02-player-bottom.png(24437B)。

## 验收③：单击→TrackDetail；首次长按稳定进入多选；更多→BatchActions — PASS
- ③① 单击 Alpha Song → TrackDetail（曲名/歌手 + 播放(56,196,342×96)/下一首播放/加入待播队列钮可见）；详情页「播放」钮 tap 后正确回播放页（openTrack 源码 HorologistTrackDetailScreen 接线一致，AppViewModel.openTrack 仅 navigate 不播放）。
- ③② 长按（adb shell input swipe 214 190 214 190 1200）首次即进多选：Alpha 行 ✓、Delta 行 ○、MultiSelectBar（退出多选/已选 1 首/更多）——1/1 次稳定。
- ③③ tap 更多 → BatchActions 进入成功。首轮失败一次（多选栏在 uiautomator dump ~1s 窗口内受扰消失，环境性；多选状态机 SelectionState.kt 无自动退出路径，排除逻辑原因），最小间隔复测（长按→600ms 内直击更多坐标，零 dump 干预）成功。计划允许 1 次重试（共 2 次判定）。
- 存证：03-trackdetail.png(27385B)、04-multiselect.png(35187B)、05-batchactions.png(31832B)。

## 验收④：音量面板可通过系统返回和屏上可识别操作关闭 — FAIL（第二路径不存在）
- **第一路径（系统返回）PASS**：Set Volume → VolumeOverlay 全屏开（Increase Volume(167,24,120×96)/音量 73%/手表扬声器 DeviceButton(98,203,258×96)/Decrease Volume(167,334,120×96)，根 contentDesc="Volume set to 73%"）；发 BACK → 面板关闭、仍在播放页（ui_tree 复核无音量节点）。存证 06-volume-open.png(20455B)、07-volume-back-closed.png(24572B)。
- **第二路径（屏上可识别操作关闭）FAIL**：开面板状态下 ui_tree 全树 23 节点中无任何关闭控件（无「关闭/close」contentDesc、无 ✕）；源码 AudioVolumeControls.kt 全文 grep 0 处屏上关闭钮（VolumeOverlay :317-336 仅 BackHandler(onBack=onDismissRequest) :325 + 全屏 Box + VolumePanel；VolumePanel :275-309 = Horologist VolumeScreen 内核，Stepper ±/VolumeLevelIndicator/AudioOutputButton，无关闭件；仅注释提及 back 关闭 :62/:273/:313-314）。
- 上游对照：Horologist VolumeScreen.kt 三重载 :78/:112/:271 同样无 close——上游靠 VolumeRoute 的 SwipeDismissableNavHost 边缘滑动关闭，本应用无 NavHost，该替代路径不存在；且 WearJellyApp.kt:301 SwipeToDismissBox userSwipeEnabled=!showVolume 显式禁用了面板开启时的边缘滑动。
- 复现步骤：播放页 → tap Set Volume → ui_tree/dump 检查全树 → 仅 Increase/Decrease/手表扬声器/音量% 节点，无关闭控件 → 仅 BACK 可关。
- 复证：重开面板截图与首次字节级一致（同一 sha256），08-volume-reopen.png(20455B)。
- 修复建议（供 t36+）：在 VolumePanel 顶部或底部加显式关闭钮（contentDescription="关闭"），或为面板外区域加 tap-to-dismiss；均不影响播放逻辑层。

## 验收⑤：crash buffer 无新增致命崩溃，playback 目录无改动 — PASS
- `logcat -b crash` 2h 窗口：0 行（零 FATAL）。
- `git diff --name-only -- app/src/main/java/dev/wearjelly/playback` → 仅 PlaybackConnection.kt/PlaybackService.kt = t26 已接受修复（H1 appScope/onDestroy 快照、H2 queue_payloads 中转+canonical 守卫+内联回退、M3 onDisconnected 重绑），计划允许口径。

## verify 三条（均已执行）
1. `./gradlew.bat :app:assembleDebug --console=plain` → EXIT:0（pwsh-158，b38 证据）
2. `git diff --check` → EXIT:0（CRLF/LF warnings 无害）
3. `git diff --name-only -- playback` → EXIT:0，输出=上述两文件（t26 已接受修复）

## 结论
播放页布局、列表交互、多选、批量菜单的 t33/t34 修复经真实模拟器复测成立；唯一遗留为音量面板缺少屏上关闭控件（验收④第二路径），属新增缺口而非回归——上游 Horologist 亦无此件，需后续任务在 AudioVolumeControls.kt 补显式关闭钮。全部 8 份截图存证于本目录。

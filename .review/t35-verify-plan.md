# t35 复验播放页与列表修复 — 验收计划 v2
前提：t33（播放页布局修复，B1/B2/M3）+ t34（详情入口与多选手势，M4/M5/L6）completed 后才启动。设备仅 emulator-5554（Wear33，454x454 圆屏，Android 13）。禁改 app/src/main/java/dev/wearjelly/playback。

## §0 APK 门槛
- pwsh 读 app\build\outputs\apk\debug\app-debug.apk LastWriteTime，须晚于 team.json 中 t33/t34 的 updatedAt（完成后 grep "t33"/"t34" 读 updatedAt）。
- 不满足则自己跑 assembleDebug（JAVA_HOME=C:\Program Files\Android\Android Studio\jbr 前置）。
- 记录 versionName/versionCode（上次 v1.4.0/5）与 lastUpdateTime（android_app_info dev.wearjelly）。

## §1 环境准备
- adb devices 确认 emulator-5554 state=device。
- mock-jellyfin：上次 pwsh-86/90 已死。重启：& "C:\Users\ASUS\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe" D:\wearmusic\.review\mock-jellyfin.js（后台 job）；curl http://127.0.0.1:8096/Artists 须 200；curl /Audio/s1/stream.mp3 须 200 + audio/mpeg + ID3 头（真 MP3 版 mock，勿回退 WAV）。
- adb install -r app-debug.apk → Success；force-stop + monkey LAUNCHER（合同 verify）。
- 登录态应保留（nazuna）；若丢则用户名 nazuna 任意密码重登。

## §2 播放页复验（t32 B1/B2/M3 回归）
进播放页（点 Alpha Song 播放卡或行；确保 mock 在线否则 Source error 无效化验收 2）：
1. ui_tree：曲名/歌手 TextView 节点存在且高度>0（B1；源码 HorologistPlayerScreen.kt:171-185）；进度条节点存在（:198-205）。
2. read_image 像素复核：曲名/歌手/进度条肉眼可见（对照 .review\08_ring.png / 09_bottom.png 参考图）。
3. 队列入口 TextView 宽度 >30px、文字完整（B2；:271-278）。
4. 触控区两两比对：Set Volume/取消收藏/队列 Button bounds 与底行 4 按钮(y403+) 不相交（M3）。
5. 回调保留：播放/暂停 toggle、Previous/Next、seek（进度条）、队列入口、Set Volume、收藏（t33 acceptance 4）。
存证 01-player-top.png / 02-player-bottom.png（screencap /sdcard + adb pull）。

## §3 列表交互复验（t32 M4/M5/L6 回归）
- 首页 → 所有歌曲 → 列表。
- 单击 Alpha Song → 须进 TrackDetail：标题=Alpha Song，含 播放/下一首播放/加入队列 等按钮（M4；t34 acceptance 1）。back 返回列表。
- 首次长按 pwsh `adb shell input swipe 214 190 214 190 1200` → 稳定进多选（✓ + MultiSelectBar 退出多选/已选 N 首/更多）。若首次未注册重试一次并记录（L6）。最多 2 次判定。
- 多选态对已选 Alpha 再长按 → 按 t34 实现语义判定（须「明确且可达批量菜单」：若直接开批量菜单记 passed 新语义；若仍 toggle 退出且无替代直达路径则 failed M5 未修）。
- 「更多」→ BatchActions 菜单（加入待播队列/缓存选中项/下一首播放）可读可点（t34 acceptance 4）。
- 存证 03-trackdetail.png / 04-multiselect.png / 05-batchactions.png。

## §4 音量面板复验（t32 通过项回归 + t33 新增屏上关闭）
- Set Volume → VolumeOverlay 全屏（音量 %/手表扬声器/Increase/Decrease）。
- BACK → 关闭且回播放页（第一次验证）。
- 重开 → 找屏上关闭控件（t33 应在 AudioVolumeControls.kt 加；contentDesc 找「关闭/close」或新增可见按钮）→ 点击关闭回播放页（t35 acceptance 4 双路径都验）。
- 存证 06-volume-open.png / 07-volume-closed-back.png / 08-volume-closed-onscreen.png。

## §5 终检与合同 verify
- logcat -b crash snapshot 2h：零新增 FATAL。
- pwsh：git diff --check（exit 0）；git diff --name-only -- app/src/main/java/dev/wearjelly/playback（空输出）。
- commandsRun 按合同 3 条记录：assembleDebug / git diff --check / git diff --name-only playback。

## §6 汇总
- 证据目录 D:\wearmusic\.review\t35\。截图管道：adb shell screencap -p /sdcard/x.png + adb pull（pwsh `>` 会 UTF-16 损坏 PNG，禁用）。
- acceptanceResults 5 条逐项 passed/failed，附 bounds/截图证据；任何 failed → findings（low/medium/high/blocker）。
- 失败不粉饰：布局仍挤压/裁切/重叠 → 判 failed 如实上报（t32 教训）。

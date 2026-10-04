# 完整离线曲库元数据同步任务企划

日期：2026-06-09
项目：WearJelly
状态：待实施

## 1. 目标

用户连接 Jellyfin 后，应用应把当前账号可见的曲库元数据同步到本地，使断网时仍能浏览和使用最近一次完整快照。同步范围包括：

- 歌曲：标题、艺人、专辑、专辑 ID、曲目/碟片序号、时长、文件格式、媒体源信息、音质信息、作曲家、作词家、发行信息、流派、年份、评论、音频 ID 和完整 Jellyfin 原始字段。
- 艺人：名称、艺人 ID、艺人类型、简介/外部 ID（服务器提供时）、图片引用。
- 专辑：名称、专辑 ID、专辑艺人、发行日期/年份、曲目数、流派、简介、图片引用。
- 播放列表：名称、描述、歌曲数量、图片引用、最后修改时间以及有序歌曲关系。
- 图片：歌曲、专辑、艺人、播放列表的图片 tag、类型、远程地址和本地文件状态；封面按图片键去重。
- 歌词：逐行文本、时间戳、同步/非同步状态、语言或来源（服务器提供时）。
- 媒体缓存状态：已缓存音频、封面、歌词的本地路径和校验/完成状态。

音频仍然按需缓存，不因为元数据同步而自动下载全部歌曲。同步完成的标准是元数据和所配置的本地资源任务完整落库，失败时保留旧快照。

## 2. 当前差距

当前实现已经具备 Room、服务器 profile、歌曲/专辑/艺人/播放列表关系、图片 tag、下载任务和离线查询，但仍有这些缺口：

- `JellyfinItem` 只包含有限字段，没有作曲家、作词家、流派、年份、评论、媒体源等字段。
- `TrackEntity`、`AlbumEntity`、`ArtistEntity`、`PlaylistEntity` 只保存部分结构化字段和有限 `metadataJson`。
- `OfflineLibrarySync` 只从播放列表分页抓取歌曲，没有独立的全库歌曲/专辑/艺人同步策略。
- 歌词仅通过 `/Audio/{itemId}/Lyrics` 在线请求，没有 Room 歌词表或 `lyricsJson`。
- `ImageEntity` 当前主要保存 tag，封面实际文件和下载状态没有完整的资源任务链路。
- 离线歌词页面不能从本地读取已同步或已缓存歌词。
- 网络失败自动切换离线模式已经存在，但需要保证所有元数据查询和资源读取都不再触发网络。

## 3. 设计原则

1. **完整原始数据 + 可查询字段**：保留服务器返回的完整 JSON，同时将排序、过滤和展示所需字段结构化，避免未来字段丢失。
2. **元数据同步与音频缓存解耦**：同步所有元数据；音频只由用户下载任务缓存。
3. **本地优先**：封面、歌词和音频都按本地完成状态优先；离线模式禁止构造远程 URL。
4. **完整快照原子可见**：远程分页全部成功后再更新快照；资源下载使用临时文件和原子改名；失败保留上一份可用快照。
5. **可恢复、可重试、幂等**：同步、图片、歌词任务都支持重启恢复、重复执行和失败重试。
6. **按账号隔离**：所有数据以 `serverKey + userId` 隔离，切换服务器或账号不会误显示旧曲库。
7. **向后兼容**：Room migration 必须可从当前数据库 version 1 升级；旧 JSON 下载索引继续可导入。

## 4. 数据模型改造

### 4.1 扩展 Jellyfin DTO

扩展 `JellyfinItem` 及相关 DTO，使用可选字段兼容不同 Jellyfin 版本：

- `Overview`、`Genres`、`ProductionYear`、`PremiereDate`、`DateCreated`。
- `Composers`、`Lyricists`、`Contributors`、`AlbumArtists`。
- `DiscNumber`、`TrackNumber`、`IndexNumber`、`ParentIndexNumber`。
- `MediaSources`、`Bitrate`、`Size`、`AudioBitrate`、`AudioChannels`、`SampleRate`、`BitDepth`、`Codec`、`Container`。
- `ProviderIds`、`SortName`、`UserData` 中必要的播放状态字段。
- `ImageTags`、`BackdropImageTags`、`AlbumPrimaryImageTag`。

不把 token、密码或带鉴权参数的 URL 写入数据库或日志。

### 4.2 TrackEntity

新增或迁移字段：

- `composerText`、`lyricistText`、`contributorJson`。
- `genreJson`、`overview`、`productionYear`、`premiereDate`。
- `discNumber`、`trackNumber`、`sortName`。
- `mediaSourceJson`、`bitrate`、`audioCodec`、`audioChannels`、`sampleRate`、`bitDepth`、`fileSize`。
- `lyricsState`：`UNKNOWN`、`AVAILABLE`、`NOT_FOUND`、`FAILED`。
- `lyricsLocalPath` 或关联 `LyricsEntity` 的外键。
- 保留现有 `metadataJson`、`localAudioPath`、`localCoverPath`、`downloadedTimeMs`、`qualityLabel`。

优先采用独立 `LyricsEntity`，避免歌词更新导致 Track 大字段频繁重写。

### 4.3 LyricsEntity

建议字段：

- 主键：`serverKey + itemId + language/source`。
- `itemId`、`format`（同步歌词/纯文本）、`lyricsJson` 或本地相对路径。
- `isSynchronized`、`lineCount`、`fetchedAtMs`、`contentHash`。
- `state`：`AVAILABLE`、`NOT_FOUND`、`FAILED`。
- `lastError`、`updatedAtMs`。

歌词没有内容时记录 `NOT_FOUND`，避免每次离线或重启重复请求。

### 4.4 ImageEntity 和资源任务

扩展图片记录：

- `remotePath`、`contentHash`、`byteSize`、`mimeType`、`localPath`。
- `state`：`METADATA`、`QUEUED`、`DOWNLOADING`、`READY`、`FAILED`。
- `referenceCount` 或通过关系查询计算引用数。

新增统一 `OfflineResourceTaskEntity` 或扩展 `DownloadTaskEntity`，区分 `AUDIO`、`IMAGE`、`LYRICS`，记录临时路径、重试次数、进度、错误和更新时间。音频任务保持现有行为。

## 5. 同步流程

### 阶段 A：发现和分页抓取

1. 登录成功或恢复有效 session 后启动唯一同步协程，使用 Mutex 防止并发。
2. 读取全部当前账号可见播放列表及其完整元数据。
3. 读取每个播放列表的有序歌曲关系。
4. 对歌曲去重后补齐完整字段；必要时按歌曲 ID 批量/分页请求详情。
5. 根据歌曲关联补齐专辑和艺人实体；如果产品目标是“全部服务器可见曲库”，额外分页抓取全部 Audio、MusicAlbum、MusicArtist，而不是只抓播放列表中的项目。
6. 为所有歌曲、专辑、艺人、播放列表生成图片索引。
7. 为歌曲请求歌词；歌词抓取失败不应让其他元数据丢失，但必须记录失败状态和可重试原因。

### 阶段 B：事务提交

1. 所有远程分页和允许失败的资源抓取结束后，在 Room 事务中写入临时同步批次。
2. 校验歌曲、父级实体和播放列表关系的 serverKey 一致。
3. 替换当前 serverKey 的播放列表关系，清理服务器已删除关系，但不删除用户已有音频文件。
4. upsert Track/Album/Artist/Playlist/Lyrics/Image 元数据，并保留本地音频路径、封面路径和歌词本地路径。
5. 只有事务成功后更新 `lastSuccessfulSyncMs`、快照版本和 `isOfflineAvailable=true`。
6. 同步失败时保留上一个完整快照，新的临时批次全部清理。

### 阶段 C：本地资源任务

按配置执行资源策略：

- 默认：同步歌词内容和必要的小型元数据；歌曲封面进入图片下载队列。
- 用户已缓存音频：自动补齐对应歌词和封面。
- 用户明确开启“完整离线资源”：下载当前快照所有歌曲/专辑/艺人/播放列表封面和所有可用歌词，但不下载音频。
- 所有资源都先写 `.part` 临时文件，校验后原子改名并更新 Room 状态。

## 6. 离线读取与播放

1. 启动先检查最新成功快照；无网络或在线请求失败时进入离线模式。
2. 所有列表、搜索、筛选、排序和播放列表顺序从 Room 读取。
3. 音频按 `localAudioPath` 优先；没有本地音频时离线提示需要缓存。
4. 封面按 `localPath` 优先，无本地文件时离线显示占位图，不发 HTTP 请求。
5. 歌词 `loadLyrics()` 顺序：Room `LyricsEntity` → 本地歌词文件 → 在线 Jellyfin（仅在线模式）→ 明确的未找到/失败状态。
6. 离线播放已缓存歌曲时，歌词页必须立即展示本地歌词，并继续根据播放器位置高亮同步歌词。
7. 断网状态不能因为歌词、封面或播放器初始化而触发 Jellyfin 请求。

## 7. 迁移与兼容

- Room version 从 1 升到 2，必要时拆分为 version 3，避免一次迁移过大。
- 为新增字段提供默认值，确保已有 Track/Album/Artist 数据可读。
- 为旧 `metadataJson` 运行一次 backfill，能解析的字段写入结构化列，解析失败保留原 JSON。
- 旧 `downloads_index.json` 继续幂等导入，并保留已缓存音频路径。
- 迁移失败不能删除原数据库；增加 schema JSON 和迁移测试。
- 不在迁移中发网络请求；联网补齐由后台同步负责。

## 8. 任务分解与交付物

### T1：字段与数据契约

范围：扩展 Jellyfin DTO、Track/Album/Artist/Playlist 字段和序列化映射。

验收：完整原始 JSON 可 round-trip；作曲家、作词家、流派、媒体源和发行信息有测试覆盖；无敏感信息落库。

### T2：Room schema 与 migration

范围：LyricsEntity、扩展 ImageEntity/资源任务、Room version 迁移、DAO、schema 输出。

验收：version 1 数据可迁移；本地音频路径和下载状态保留；重复迁移幂等；migration test 通过。

### T3：完整元数据同步器

范围：全库/播放列表同步策略、详情补齐、批次快照、陈旧关系清理、失败回滚。

验收：新增/删除/顺序变化/字段更新正确；服务器分页失败保留旧快照；重复同步幂等；同步不删除音频缓存。

### T4：歌词同步和离线歌词读取

范围：歌词 API、LyricsEntity、404 记忆、同步歌词解析、离线优先读取、播放器时间联动。

验收：联网同步后断网打开歌词页可查看；同步歌词可滚动高亮；无歌词不会反复请求；歌词请求失败可重试。

### T5：图片和本地资源管理

范围：封面 URL/tag 解析、去重、资源队列、临时文件、原子提交、引用清理。

验收：歌曲/专辑/艺人/播放列表共享封面不重复下载；损坏文件可重试；删除缓存不会删除仍被引用图片。

### T6：离线模式与 UI 状态

范围：启动判定、离线横幅、上次同步时间、同步失败/重试、元数据与媒体缓存状态展示。

验收：断网冷启动无 HTTP；有快照进入离线首页；无快照进入登录页；在线恢复后后台同步且不打断播放。

### T7：端到端测试和发布

范围：Room migration、MockWebServer、离线读取、歌词、图片、下载恢复、Wear OS 圆屏 UI、无 GMS、Debug/Release 构建。

验收命令：

```text
:app:testDebugUnitTest --offline
:app:assembleDebug --offline
:app:assembleRelease --offline
:app:verifyNoGms --offline
```

完成后更新版本号、生成 release notes、推送主线和 GitHub Release，并在目标手表执行安装验证。

## 9. 风险与取舍

- “所有元数据”必须限定为服务器当前账号可见范围；服务器插件自定义字段只能通过原始 JSON 尽量保留。
- 全库歌词和图片会增加请求数及存储，应使用分页、并发上限、缓存 hash 和失败重试退避。
- 歌词接口在不同 Jellyfin 版本上的字段可能不同，解析器必须允许缺失字段。
- 资源下载不能阻塞主同步事务；元数据快照先可用，资源任务异步完成。
- 在线请求失败自动离线回退时，不应把鉴权失败误判为网络断线；401/403 需要保留在线错误并要求重新登录。
- 保留孤立的本地音频和歌词，直到用户明确删除，避免同步清理误删用户资产。

## 10. 最终验收标准

1. 连接服务器后，当前账号可见曲库的完整元数据能进入 Room 快照。
2. 歌曲的艺人、专辑、作曲家、作词家、流派、序号、媒体信息和原始 JSON 可离线读取。
3. 歌曲、专辑、艺人和播放列表封面按 tag/hash 去重并可按策略离线显示。
4. 已同步歌词的歌曲断网播放时可以打开歌词页并显示同步高亮。
5. 断网冷启动和在线请求失败回退均不发起 HTTP 请求来展示本地曲库。
6. 元数据同步失败不会破坏上一份完整快照，也不会删除已缓存音频、封面或歌词。
7. 应用重启、数据库迁移、资源任务失败和重复同步均可恢复。
8. 在线登录、播放、队列、下载、歌词、无 GMS 检查和 Wear OS 圆屏布局全部回归通过。

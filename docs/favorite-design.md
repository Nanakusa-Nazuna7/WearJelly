# 收藏（Favorite）功能改造设计 — 只读调研结论

> 任务 t2 产出。本文档为 **t4-player（播放页心形按钮）** 与 **t13-favorite（收藏屏）** 的消费契约，签名**以本文档为准**。
> 调研方式：只读（未改任何代码）；REST 部分用 web + Jellyfin 官方源码逐版本核实（见 §2.5 来源）。

---

## 1. 本项目现有结构与可插入点（行级）

### 1.1 `app/src/main/java/dev/wearjelly/data/JellyfinRepository.kt`

| 行 | 内容 | 对收藏的意义 |
|---|---|---|
| 32-36 | `class JellyfinRepository(client, json, store)` | 网络层唯一入口；收藏网络方法**加在这里** |
| 37 | `val session: StateFlow<ServerSession?>` | 取 `userId`/`serverUrl`/`accessToken` 的唯一来源 |
| 39 | `jsonMediaType` | POST/DELETE 空体或 JSON 体复用 |
| 41-48 | `authHeader(token?)` | 所有请求都要带 `X-Emby-Authorization` |
| 92-153 | `getItems(...)`：103-135 是 URL 分支，137 追加 `&Fields=...`，139-143 建请求，145-152 解析 `JellyfinItemsResult` | **查询收藏列表的样板**（`isFavorite=true` 即挂在同类 URL 上）；10.10+ 的 `/Users/{userId}/Items` 路由就是这个 |
| 155-174 | `getPlaylistItems(...)` | 分页 + `URLEncoder.encode` 的样板 |
| **174 / 176** | `getPlaylistItems` 结束与 `val bitrate` 之间 | **建议插入点 A**：`getFavoriteIds()` / `setFavorite()` 放这里 |
| 219-253 | `lyrics(itemId)` | “404 视为可降级” 的容错样板（收藏路由 404 回退可参考） |
| 255-303 | `reportPlayback(...)` | 尽力而为（`try/catch` 吞异常）样板，收藏上报可参考 |
| 16 | `JellyfinException(message, statusCode)` | 失败统一抛它，`AppViewModel.userMessage()`(1057-1069) 已映射 401/403/404 文案 |

仓库 **没有 CoroutineScope**，只做 IO；状态必须留在 ViewModel（与 `downloadManager`/`historyStore` 同模式）。

### 1.2 `app/src/main/java/dev/wearjelly/data/Models.kt`

| 行 | 内容 | 对收藏的意义 |
|---|---|---|
| 7-12 | `ServerSession(serverUrl, userId, userName, accessToken)` | **`userId` 就在这里**（login 时由 `/Users/AuthenticateByName` 的 `user.id` 写入，JellyfinRepository.kt:77-82） |
| 15-48 | `data class JellyfinItem`（`@Serializable`，字段全部 PascalCase `@SerialName`） | 15-37 是字段区；**37 行 `MediaSources` 之后、38 行 `)` 之前 = 建议插入点 B**（若要行内心形角标，见 §4.3 可选字段） |
| 39-47 | 计算属性 `artistText` / `durationMs` | 同位置可加 `val favorite: Boolean` |
| 63-66 | `ItemPage` | 收藏分页返回可直接复用 |
| 90-96 | `enum class LibraryKind { ARTISTS, ALBUMS, SONGS, DOWNLOADS, PLAYLISTS }` | t13 若走列表屏方案 A，**加 `FAVORITES` 枚举值**（编译器会强制更新所有 `when`，见 §4.4） |
| 142-191 | `internal` 内部 DTO 区（`JellyfinItemsResult`、`JellyfinPlaybackProgressRequest`…） | **建议插入点 C**：`JellyfinUserItemDataDto`（toggle 响应体）加在此区 |
| 162-165 | `JellyfinItemsResult` | 服务端序列化用 PascalCase，但服务器**默认回 camelCase JSON**，故响应 DTO 的 `@SerialName` 要写 `"isFavorite"`（见 §2.4；`Json` 配置 `ignoreUnknownKeys/coerceInputValues`，WearJellyApplication.kt:34-39，未知字段安全） |

### 1.3 `app/src/main/java/dev/wearjelly/ui/AppViewModel.kt`

| 行 | 内容 | 对收藏的意义 |
|---|---|---|
| 37-62 | `AppScreen` 导航密封接口 | t13 新增 `data object Favorites : AppScreen`（或复用 `Library`）的插入位置；`Confirm` 屏在 57-61 |
| 113-122 | 构造函数（8 个依赖，Koin 位置参数注入） | 若改成注入 `FavoriteStore` 要同步改 WearJellyApplication.kt:62 —— **本文档方案不需要改构造** |
| 123-143 | 状态暴露区：`downloadedSongs`/`downloadingIds`/`cachedTrackIds`… | **建议插入点 D（首选）**：`val favoriteIds: StateFlow<Set<String>>` 加在 132 行 `cachedTrackIds` 附近 |
| 136-138 | `isOfflineMode` / `offlineMode` | 收藏必须判离线：离线不可改服务端数据（同 `playTrack` 506-510 的处理） |
| 152-176 | `init{}`：session collect、playback collect | 备选刷新触发点 |
| 178-210 | `acceptSession(...)`：183 `generation += 1`，185-187 取消旧 job | **登录/切服后刷新收藏的钩子**；用 `generation` 防串台（同 `loadLibrary` 366/392 的 `requestGeneration` 守卫） |
| 488-498 | `isDownloaded` / `downloadTrack` / `deleteDownloadedTrack`（都带 `showNotice`） | **建议插入点 E**：`toggleFavorite(itemId)` 放这组旁边，风格对齐 |
| 630-642 | `removeFromLibraryView`（仅本地视图，不动服务端） | 收藏与它相反：**服务端为准 + 本地乐观更新** |
| 1003-1052 | `requestConfirmation` / `confirm` | 收藏无需二次确认 |
| 1055 | `showNotice(message)` | 收藏失败/离线提示复用它 |
| 1071-1074 | `PAGE_SIZE=60`、`MAX_FETCH_ITEMS=3000` | 收藏拉全量沿用同上限 |

### 1.4 `app/src/main/java/dev/wearjelly/ui/WearJellyApp.kt`（3052 行）

| 行 | 内容 | 对收藏的意义 |
|---|---|---|
| 49-70 | material icons 导入块 | 需补 `androidx.compose.material.icons.filled.Favorite` 与 `FavoriteBorder`（二者都在 core icons 集内，无需 icons-extended） |
| 276-301 | 屏幕 `when` 分发：`is AppScreen.Player -> PlayerScreen(viewModel)`(301) | t13 新屏的分发分支插入处 |
| 1448-1463 | `LibraryItemRow(item, query, …, cached, trailingAction, onClick…)` | 列表行统一组件；若要在行尾加小红心，t13 用 `favorite: Boolean = false` 参数扩展（1495-1506 徽标区） |
| **2236-2504** | `PlayerScreen(viewModel)` | **t4 心形按钮宿主**：2239 已有 `cachedTrackIds.collectAsState()` 订阅样板；2293-2326 标题 Column（歌名/歌手/缓存点）；2371-2424 上一曲/播放/下一曲 Row；2426-2478 音量/随机/循环 Row；2480-2492 “队列”按钮 |
| 866-1054 | `LibraryScreen`（874 `cachedTrackIds` 订阅样板） | 列表页读 `favoriteIds` 的样板 |

### 1.5 DI（`WearJellyApplication.kt`）

- 49-62：`single { SessionStore }`、50 `single { JellyfinRepository(get(), get(), get()) }`、62 `viewModel { AppViewModel(get()×8) }`。
- **本方案把状态放 AppViewModel、网络放 JellyfinRepository → 零 DI 改动**。（备选 `FavoriteStore` 独立类：需新增 51-55 行风格的 `single` + 改 113-122 构造与 62 行注入 —— 无本地持久化需求时不建议。）
- 测试：`app/src/test/java/dev/wearjelly/data/JellyfinRepositoryTest.kt`（54-93 有序列化测试样板，可加 `UserData` 映射用例）；**全仓只有 Koin 一处构造 AppViewModel**，改构造函数不会破坏测试，但没必要改。

---

## 2. Jellyfin REST 收藏 API（web 核实）

### 2.1 ⚠️ 先纠正任务书里的路径假设

任务书写的 `POST/DELETE /Users/{userId}/Items/{itemId}/Favorite` **在 Jellyfin 任何版本都不存在**（10.7.7 / 10.8.13 / 10.9.11 / 10.10.7 源码与 12.1.0 OpenAPI 全部核过）——那是 **Emby** 的路由。Jellyfin 实际是 `FavoriteItems` 段（10.x）与 `UserFavoriteItems` 段（10.9+），见下表。**不要按任务书字面实现，否则 404。**

### 2.2 写操作（toggle 的两半）

| 版本（核实方式） | 首选路由 | 兼容路由 |
|---|---|---|
| 10.7.7 / 10.8.13（源码） | `POST` / `DELETE /Users/{userId}/FavoriteItems/{itemId}`（`userId` 必填，在 path） | — |
| 10.9.11（源码） | `POST` / `DELETE /UserFavoriteItems/{itemId}` | `POST` / `DELETE /Users/{userId}/FavoriteItems/{itemId}` |
| 10.10.7（源码） | `POST` / `DELETE /UserFavoriteItems/{itemId}`，`userId` 为 **可选 query**（`[FromQuery] Guid? userId`，`RequestHelpers.GetUserId(User, userId)` 从 token 取用户） | 同上，标了 `[Obsolete("Kept for backwards compatibility")]` + `IgnoreApi=true`，仍可用 |
| 12.1.0（官方 stable OpenAPI） | 仅文档化 `POST` / `DELETE /UserFavoriteItems/{itemId}`（`userId` 可选 query） | 未文档化（是否仍在代码中未证实） |

- 响应：`200` → `UserItemDataDto`，字段 `IsFavorite`（服务器实际回 **camelCase** `isFavorite`）→ **可用响应体确认 toggle 后的真实状态**；另有 `401`/`403`/`503`。
- 无请求体要求；带 `X-Emby-Authorization … Token="…"` 头（本项目 `authHeader(session.accessToken)`，JellyfinRepository.kt:41-48）。
- **推荐调用策略（兼容 10.7→12.x）**：
  1. `POST /UserFavoriteItems/{itemId}?userId={userId}`（`DELETE` 同理）；
  2. 若 `404`，回退 `POST /Users/{userId}/FavoriteItems/{itemId}`（老服务器）；回退结果缓存（一个 `private var legacyFavoriteRoute: Boolean`），避免每次双请求。
- 只有“收藏/取消”两个动作，**没有独立的“toggle”端点**——本地用 `itemId in favoriteIds` 算出目标态再调 POST 或 DELETE。

### 2.3 查询接口

| 目的 | 路由 | 备注 |
|---|---|---|
| 拉全部收藏 id（t13 列表 & 全量集合） | `GET /Users/{userId}/Items?isFavorite=true&IncludeItemTypes=Audio,…&Recursive=true&StartIndex=&Limit=` | 本项目**已在用**该路由（JellyfinRepository.kt:109/120/132）；`isFavorite`/`filters=IsFavorite` 参数在 10.7–10.10 的 `ItemsController.GetItems` 均核实 |
| 同上的现代化写法 | `GET /Items?userId={userId}&isFavorite=true&recursive=true&…` | 10.9+ 支持；**12.1.0 OpenAPI 中 `/Users/{userId}/Items` 已消失（0 命中），只剩 `/Items`** |
| 单曲收藏状态 | `GET /Users/{userId}/Items/{itemId}/UserData` → `UserItemDataDto` | 10.x；12.1 对应 `/UserItems/{itemId}/UserData` |
| 顺带读状态（列表项自带） | `BaseItemDto.userData.isFavorite` | 12.1 schema 确认有 `userData`；`/Users/{userId}/Items` 因 path 带 userId 会自动返回（`enableUserData` 默认 true） |
| 排序 | `enableFavoriteSorting` | 可选，暂不需要 |

### 2.4 userId 要求（任务书要求注明）

- **查询类**：官方 OpenAPI 原文 —— `userId`: *"The user id supplied as query parameter; **this is required when not using an API key**."* 本项目用的是**用户 access token**（登录换发），不是 API key → **`userId` 必填**（`/Items?userId=…` 场景），或放在 path（`/Users/{userId}/Items…`，本项目现有写法）。
- **写类（toggle）**：10.9+ 首选路由 `userId` 可省略（token 即用户），但**为兼容 10.7/10.8 的 path 必填路由，始终显式带上 `userId`**。
- 取值：`repository.session.value?.userId`（`ServerSession.userId`，Models.kt:9）；未登录时 `session.value == null` 直接 no-op（同 `getItems` 首行 `?: throw JellyfinException("未登录", 401)`，JellyfinRepository.kt:99）。
- `itemId` 在服务端是 UUID；本项目 id 全程字符串透传，无需转换。

### 2.5 来源

- 官方 OpenAPI（stable, `info.version = 12.1.0`）：`https://api.jellyfin.org/openapi/jellyfin-openapi-stable.json`（入口 `https://api.jellyfin.org/` ReDoc）——`MarkFavoriteItem`/`UnmarkFavoriteItem`、`isFavorite`、`userData`、`UserItemDataDto` 均取自该 spec。
- 服务端源码（jsDelivr 按 tag 固定版本，逐版核实）：
  - `https://cdn.jsdelivr.net/gh/jellyfin/jellyfin@v10.10.7/Jellyfin.Api/Controllers/UserLibraryController.cs`（L212 `HttpPost("UserFavoriteItems/{itemId}")`、L243 `HttpPost("Users/{userId}/FavoriteItems/{itemId}")` `[Obsolete]`、L259/L290 DELETE 对应；L218 `RequestHelpers.GetUserId`）
  - `https://cdn.jsdelivr.net/gh/jellyfin/jellyfin@v10.10.7/Jellyfin.Api/Controllers/ItemsController.cs`（`[HttpGet("Users/{userId}/Items")]`、`[HttpGet("Users/{userId}/Items/{itemId}/UserData")]`、`[FromQuery] bool? isFavorite`）
  - `https://cdn.jsdelivr.net/gh/jellyfin/jellyfin@v10.9.11/…/UserLibraryController.cs`（两套路由并存）
  - `https://cdn.jsdelivr.net/gh/jellyfin/jellyfin@v10.8.13/…/UserLibraryController.cs`、`…@v10.7.7/…/UserLibraryController.cs`（仅 `Users/{userId}/FavoriteItems/{itemId}`）
- 官方文档旁证：`https://jellyfin-jellyfin.mintlify.app/api/media/items`（Items API：Get/Update Item User Data）、`https://jellyfin-jellyfin.mintlify.app/development/api-overview`。

---

## 3. 新增方法与状态签名（**最终契约**）

### 3.1 `data/Models.kt`

```kotlin
// 插入点 C：内部 DTO 区（142-191 行内，L165 之后）—— toggle 响应体
@Serializable
internal data class JellyfinUserItemDataDto(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("Played") val played: Boolean = false,
)
```
> 服务器默认回 camelCase，而 `Json` 配了 `coerceInputValues=true` 且字段带默认值 → 即使大小写不匹配也不会崩；若要严格命中，可同时容忍（推荐直接按响应体解析前先用 `ignoreUnknownKeys` 兜底，已具备）。

可选（t13 列表行要显示小红心时再加，插在 37 行 `MediaSources` 后）：
```kotlin
@Serializable
data class JellyfinUserData(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
)
// JellyfinItem 内：
@SerialName("UserData") val userData: JellyfinUserData? = null,
// 计算属性（46-47 行旁）：val favorite: Boolean get() = userData?.isFavorite == true
```
不加也不影响 §3.2 方案（`favoriteIds` 是独立全量集合，不依赖单条 item 自带 userData）。

### 3.2 `data/JellyfinRepository.kt`（插入点 A：L174 与 L176 之间）

```kotlin
/**
 * 全量收藏 id 集合。内部分页（StartIndex/Limit），上限 maxItems（默认 3000，对齐 AppViewModel.MAX_FETCH_ITEMS）。
 * 未登录抛 JellyfinException("未登录", 401)。
 * 查询路由：首选 /Users/{userId}/Items?isFavorite=true&...（本项目既有路由）；
 * 404 时回退 /Items?userId=...&isFavorite=true&recursive=true（Jellyfin 12.x 移除旧路由）。
 */
suspend fun getFavoriteIds(
    startIndex: Int = 0,
    limit: Int = 300,
    maxItems: Int = 3000,
): Set<String>

/**
 * 设置收藏状态（非“盲目 toggle”）。返回服务端确认后的 isFavorite（解析 200 响应体 UserItemDataDto.isFavorite）。
 * 路由：POST/DELETE /UserFavoriteItems/{itemId}?userId=...；404 回退 POST/DELETE /Users/{userId}/FavoriteItems/{itemId}。
 * 失败抛 JellyfinException。
 */
suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean
```
> 分页参数暴露（而不是仓库内部写死循环）是为了复用 `AppViewModel` 现有的 `getItems` 分页心智；若实现者选择仓库内循环到 `maxItems`，**签名可收敛为 `suspend fun getFavoriteIds(): Set<String>`，语义不变**。
> 每个方法都要带 `authHeader(session.accessToken)`（L142/L166 同款）与 `withContext(Dispatchers.IO)`。

### 3.3 `ui/AppViewModel.kt`

```kotlin
// 插入点 D（L132 cachedTrackIds 附近），对外只读：
val favoriteIds: StateFlow<Set<String>>          // 内部 private val _favoriteIds = MutableStateFlow(emptySet())

// 插入点 E（L490 downloadTrack 附近）：
/** 乐观更新：先翻本地集合 → 调 repository.setFavorite → 以响应体校准；失败回滚并 showNotice。离线/未登录直接 no-op + notice。 */
internal fun toggleFavorite(itemId: String)

/** 便捷判断（UI 可直接用 `itemId in favoriteIds`，此方法为非 Compose 调用方准备）。 */
internal fun isFavorite(itemId: String): Boolean = itemId in favoriteIds.value

// 私有：登录/切服后刷新（在 acceptSession L178-210 末尾、value != null 分支调用，带 generation 守卫）
private fun refreshFavorites()
```

行为约定（**t4/t13 依赖这些语义**）：
1. **数据源**：收藏是服务端事实，本地仅缓存于 `_favoriteIds`，**不落盘**（账号切换即随 `acceptSession` 清空重拉）。
2. **刷新时机**：`acceptSession()`（登录成功 / 冷启动已存会话 / 切服）拉一次全量；`toggleFavorite` 成功后就地增删。不做轮询。
3. **乐观更新**：点按钮 → 立即翻转 `_favoriteIds` → 请求；失败（非 200）回滚并 `showNotice(userMessage(error))`（1057-1069 已有 401/403/404 文案）。
4. **离线模式**（`offlineMode == true`）：`toggleFavorite` 不发请求，`showNotice("离线模式下无法更改收藏。")`；`favoriteIds` 保持空集。
5. **未登录**：`session.value == null` → no-op。
6. **竞态**：沿用 `generation`/`requestGeneration` 模式（同 `loadLibrary` L366、L392），会话变化后丢弃过期响应。
7. **可降级**：`getFavoriteIds` 拉取失败只把集合置空/保持，**不弹全局错误**（收藏是次要能力，避免打扰主流程）；`setFavorite` 失败必须提示。

> 不改 `AppViewModel` 构造函数（Koin WearJellyApplication.kt:62 保持 8 参），不新增 DI 单例 —— 满足“底层播放逻辑不动”的约束（Media3/ViewModel/Service 全程只加状态与方法）。

---

## 4. 消费方接入点

### 4.1 t4-player：播放页心形按钮（`WearJellyApp.kt`）

```kotlin
// PlayerScreen (L2237) 函数体，紧随 L2239 cachedIds 订阅之后：
val favoriteIds by viewModel.favoriteIds.collectAsState()
val currentId = item?.id
val isFavorite = currentId != null && currentId in favoriteIds
```
建议落位（二选一）：
- **方案 1（推荐）**：标题 Column（L2293-2326）内包一层 Row，歌名/歌手居中，右侧放心形 `IconButton`（尺寸 32dp，避免挤压 90dp 封面）；
- 方案 2：L2326 之后新增独立 `item {}`，与“缓冲/错误”块同级。

```kotlin
IconButton(
    onClick = { currentId?.let { viewModel.toggleFavorite(it) } },
    enabled = currentId != null && !offline,   // offline 可用 viewModel.isOfflineMode
) {
    Icon(
        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = if (isFavorite) "取消收藏" else "收藏",
        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```
配套：L49-70 图标导入块补 `androidx.compose.material.icons.filled.Favorite`、`androidx.compose.material.icons.filled.FavoriteBorder`（core icons，无新依赖）。**按钮订阅只依赖 `favoriteIds`，切歌无需额外刷新**（集合里已含全部收藏 id）。

### 4.2 t13-favorite：收藏屏（两条实现路线，任选）

- **路线 A（推荐，复用列表基建）**：`LibraryKind` 加 `FAVORITES`（Models.kt:90-96）→ `JellyfinRepository.getItems` 的 `when`（L103-135）加 `FAVORITES` 分支：`/Users/{userId}/Items?IncludeItemTypes=Audio,MusicAlbum,Playlist,MusicArtist&isFavorite=true&Recursive=true…` → `AppViewModel.chineseTitle`（L1077-1084）加 `"收藏"`、`openItem`（L286-299）按类型分发、`LibraryItemRow` 图标 `when`（WearJellyApp.kt:1489-1494）加 `FAVORITES -> Icons.Default.Favorite`。枚举加值会触发**编译期**遗漏点提示。
- **路线 B（纯前端过滤）**：`AppScreen.Favorites` 新屏（AppScreen 枚举 L37-62），列表 = 已加载曲库 ∩ `favoriteIds`；分发分支插在 WearJellyApp.kt:301 附近。缺点：只覆盖已加载条目。
- 无论哪条路线，**心形状态读 `viewModel.favoriteIds.collectAsState()`**，点击/长按动作走 `viewModel.toggleFavorite(id)`；行内小红心若需要，给 `LibraryItemRow`（L1448-1463）加 `favorite: Boolean = false` 参数。

### 4.3 明确不做的事（范围护栏）

- 不动 `PlaybackService` / `PlaybackConnection` / Media3 队列（收藏与播放解耦）；
- 不新增本地收藏持久化（服务端即真值）；
- 不做“喜欢/评分”（`/UserItems/{itemId}/Rating`，PersonalRating ≠ Favorite）；
- 不做 WebSocket `UserDataChanged` 订阅（首期用拉取 + 乐观更新足够）。

### 4.4 风险与已知兼容性事实

1. **任务书路由是 Emby 的**（§2.1）——实现按 §2.2。
2. **Jellyfin 12.1 移除了 `/Users/{userId}/Items`**（OpenAPI 0 命中）：本项目现有 `getItems` 也会受影响，属存量风险（非本任务范围，仅记录）；收藏查询请按 §2.2/§2.3 带 404 回退，**不要引入新的单点依赖**。
3. 收藏 id 全量拉取在超大曲库上限 3000（与现有 `MAX_FETCH_ITEMS` 一致），超出部分的心形会显示为“未收藏”——属已知取舍。
4. 响应 JSON 是 camelCase（`isFavorite`），请求路径参数不受影响；DTO 必须带默认值以容忍缺字段。

---

## 5. 验证建议（供下游任务 verify 使用）

1. `./gradlew assembleDebug` —— 编译通过（含 `LibraryKind` 加值引发的所有 `when` 补全）。
2. `./gradlew testDebugUnitTest` —— 现有 `JellyfinRepositoryTest` 不回归；可新增 toggle 响应体解析用例。
3. 静态检查：`grep -rn "M2\|material2" app/src/main` 结果为空（团队总目标：零 M2 导入）。
4. 真机冒烟：播放页点心形 → 服务端 Web UI 同账号可见收藏；切歌状态保持；断网点心形 → 提示且状态回滚。

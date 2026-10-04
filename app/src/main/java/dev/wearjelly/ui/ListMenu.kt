package dev.wearjelly.ui

import dev.wearjelly.data.LibraryKind

/**
 * 列表级长按菜单（D1/D2/D3 决策）：主页 6 个列表入口与艺人/专辑/播放列表具体条目统一走本模型。
 *
 * - D1：主页艺人/专辑/播放列表根入口管理各自实体列表，只提供现有可实现的入口级操作
 *   （播放全部/随机播放/加入队列/缓存全部/删除缓存），不新增新建、重命名、删除、排序筛选能力；
 *   长按具体实体条目时作用于该实体的歌曲，提供同样五项歌曲操作。
 * - D2：播放历史菜单 = 播放、随机播放、加入队列、缓存全部，不提供删除全部缓存；
 *   已缓存音乐菜单 = 播放全部、随机播放、加入队列、删除全部缓存，不显示"缓存全部"空操作。
 * - D3：播放、随机、删除缓存等列表操作统一按完整列表处理（非仅已加载分页）。
 *
 * 本文件为纯函数模型，不依赖 Compose/Android：[items]（菜单项与顺序）、[source]（数据来源）、
 * [scopeQuery]（歌曲作用域查询）由 ListMenuTest 做来源/类型/顺序回归断言。
 */
internal sealed interface ListMenu {
    /** 主页"所有歌曲"入口：全部歌曲列表。 */
    data object RootSongs : ListMenu

    /** 主页"艺人"入口：全部艺人之歌曲（等价全库歌曲）。 */
    data object RootArtists : ListMenu

    /** 主页"专辑"入口：全部专辑之歌曲（等价全库歌曲）。 */
    data object RootAlbums : ListMenu

    /** 主页"播放列表"入口：所有播放列表内歌曲的并集。 */
    data object RootPlaylists : ListMenu

    /** 主页"播放历史"入口。 */
    data object RootHistory : ListMenu

    /** 主页"已缓存音乐"入口。 */
    data object RootDownloads : ListMenu

    /**
     * 艺人/专辑/播放列表列表内的具体条目（长按行）：作用于该实体的歌曲。
     * [query] 必须是 kind=SONGS 且带 artistId/parentId/playlistId 之一的作用域查询。
     */
    data class Entity(val query: LibraryQuery) : ListMenu
}

/** 列表级菜单动作类型（D1-D3 迁移的现有能力，不含未实现的重命名/删除/排序）。 */
internal enum class ListMenuAction {
    PLAY_ALL,
    SHUFFLE_ALL,
    QUEUE_ALL,
    CACHE_ALL,
    DELETE_CACHES,
}

/** 菜单数据来源类型（回归测试断言"来源"）。 */
internal enum class ListMenuSource {
    /** 服务端/离线快照的歌曲查询（[ListMenu.scopeQuery]）。 */
    SONGS_LIBRARY,

    /** 所有播放列表内歌曲的并集（逐播放列表分页）。 */
    PLAYLIST_UNION,

    /** 播放历史（本地 HistoryStore）。 */
    HISTORY,

    /** 已缓存音乐（本地 DownloadManager）。 */
    DOWNLOADS,
}

/** 菜单项：动作类型 + 统一文案（文案逐字取自 [Copy]）。 */
internal data class ListMenuItem(val action: ListMenuAction, val label: String)

/** 菜单数据来源。 */
internal fun ListMenu.source(): ListMenuSource = when (this) {
    is ListMenu.Entity -> ListMenuSource.SONGS_LIBRARY
    ListMenu.RootSongs, ListMenu.RootArtists, ListMenu.RootAlbums -> ListMenuSource.SONGS_LIBRARY
    ListMenu.RootPlaylists -> ListMenuSource.PLAYLIST_UNION
    ListMenu.RootHistory -> ListMenuSource.HISTORY
    ListMenu.RootDownloads -> ListMenuSource.DOWNLOADS
}

/**
 * 歌曲作用域查询：仅 [ListMenuSource.SONGS_LIBRARY] 来源非空。
 * 根入口（所有歌曲/艺人/专辑）= 全库歌曲；实体条目 = 该实体的歌曲（artistId/parentId/playlistId）。
 */
internal fun ListMenu.scopeQuery(): LibraryQuery? = when (this) {
    ListMenu.RootSongs, ListMenu.RootArtists, ListMenu.RootAlbums -> LibraryQuery(LibraryKind.SONGS)
    // 实体条目：无论构造时 kind 如何，作用域查询统一规范为 SONGS + 实体作用域字段
    is ListMenu.Entity -> LibraryQuery(
        kind = LibraryKind.SONGS,
        parentId = query.parentId,
        artistId = query.artistId,
        playlistId = query.playlistId,
    )
    ListMenu.RootPlaylists, ListMenu.RootHistory, ListMenu.RootDownloads -> null
}

/**
 * 菜单项（含顺序）：来源+类型+顺序由 ListMenuTest 回归锁定。
 * 歌曲类菜单（所有歌曲根、艺人/专辑/播放列表根、实体条目）= 原详情页顶部"更多"迁移的五项；
 * 历史/已缓存按 D2 各自裁剪。
 */
internal fun ListMenu.items(): List<ListMenuItem> = when (this) {
    ListMenu.RootHistory -> listOf(
        ListMenuItem(ListMenuAction.PLAY_ALL, Copy.PLAY_ALL),
        ListMenuItem(ListMenuAction.SHUFFLE_ALL, Copy.SHUFFLE_PLAY_ALL),
        ListMenuItem(ListMenuAction.QUEUE_ALL, Copy.QUEUE_ALL),
        ListMenuItem(ListMenuAction.CACHE_ALL, Copy.CACHE_ALL),
    )
    ListMenu.RootDownloads -> listOf(
        ListMenuItem(ListMenuAction.PLAY_ALL, Copy.PLAY_ALL),
        ListMenuItem(ListMenuAction.SHUFFLE_ALL, Copy.SHUFFLE_PLAY_ALL),
        ListMenuItem(ListMenuAction.QUEUE_ALL, Copy.QUEUE_ALL),
        ListMenuItem(ListMenuAction.DELETE_CACHES, Copy.DELETE_ALL_CACHES),
    )
    ListMenu.RootSongs,
    ListMenu.RootArtists,
    ListMenu.RootAlbums,
    ListMenu.RootPlaylists,
    is ListMenu.Entity,
    -> listOf(
        ListMenuItem(ListMenuAction.PLAY_ALL, Copy.PLAY_ALL),
        ListMenuItem(ListMenuAction.SHUFFLE_ALL, Copy.SHUFFLE_PLAY_ALL),
        ListMenuItem(ListMenuAction.QUEUE_ALL, Copy.QUEUE_ALL),
        ListMenuItem(ListMenuAction.CACHE_ALL, Copy.CACHE_ALL),
        ListMenuItem(ListMenuAction.DELETE_CACHES, Copy.DELETE_ALL_CACHES),
    )
}

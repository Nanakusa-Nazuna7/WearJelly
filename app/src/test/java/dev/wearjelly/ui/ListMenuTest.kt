package dev.wearjelly.ui

import dev.wearjelly.data.LibraryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * t2 列表级长按菜单回归：菜单**来源**（ListMenuSource）、**类型**（ListMenuAction 集合与顺序）、
 * **作用域**（scopeQuery）与文案（Copy 逐字）全部锁定。
 */
class ListMenuTest {

    private val fiveItemMenus = listOf(
        ListMenu.RootSongs,
        ListMenu.RootArtists,
        ListMenu.RootAlbums,
        ListMenu.RootPlaylists,
        ListMenu.Entity(LibraryQuery(LibraryKind.SONGS, artistId = "artist-1")),
    )

    private val allMenus = fiveItemMenus + listOf(ListMenu.RootHistory, ListMenu.RootDownloads)

    // ---- 来源（ListMenuSource）----

    @Test
    fun `song menus come from songs library`() {
        assertEquals(ListMenuSource.SONGS_LIBRARY, ListMenu.RootSongs.source())
        assertEquals(ListMenuSource.SONGS_LIBRARY, ListMenu.RootArtists.source())
        assertEquals(ListMenuSource.SONGS_LIBRARY, ListMenu.RootAlbums.source())
        assertEquals(
            ListMenuSource.SONGS_LIBRARY,
            ListMenu.Entity(LibraryQuery(LibraryKind.SONGS, parentId = "album-1")).source(),
        )
    }

    @Test
    fun `playlist root comes from playlist union`() {
        assertEquals(ListMenuSource.PLAYLIST_UNION, ListMenu.RootPlaylists.source())
    }

    @Test
    fun `history comes from history store and downloads from local cache`() {
        assertEquals(ListMenuSource.HISTORY, ListMenu.RootHistory.source())
        assertEquals(ListMenuSource.DOWNLOADS, ListMenu.RootDownloads.source())
    }

    // ---- 作用域（scopeQuery）----

    @Test
    fun `root song-like menus scope to the whole song library`() {
        val expected = LibraryQuery(LibraryKind.SONGS)
        assertEquals(expected, ListMenu.RootSongs.scopeQuery())
        assertEquals(expected, ListMenu.RootArtists.scopeQuery())
        assertEquals(expected, ListMenu.RootAlbums.scopeQuery())
    }

    @Test
    fun `entity row scopes to that entity's songs`() {
        assertEquals(
            LibraryQuery(LibraryKind.SONGS, artistId = "artist-1"),
            ListMenu.Entity(LibraryQuery(LibraryKind.ARTISTS, artistId = "artist-1")).scopeQuery(),
        )
        assertEquals(
            LibraryQuery(LibraryKind.SONGS, parentId = "album-9"),
            ListMenu.Entity(LibraryQuery(LibraryKind.ALBUMS, parentId = "album-9")).scopeQuery(),
        )
        assertEquals(
            LibraryQuery(LibraryKind.SONGS, playlistId = "pl-3"),
            ListMenu.Entity(LibraryQuery(LibraryKind.PLAYLISTS, playlistId = "pl-3")).scopeQuery(),
        )
    }

    @Test
    fun `local-source menus have no server scope query`() {
        assertNull(ListMenu.RootPlaylists.scopeQuery())
        assertNull(ListMenu.RootHistory.scopeQuery())
        assertNull(ListMenu.RootDownloads.scopeQuery())
    }

    // ---- 类型 + 顺序（ListMenuAction / items）----

    @Test
    fun `action enum only contains the five migrated operations`() {
        // 未实现的重命名/删除/排序筛选等能力不得混入动作枚举
        assertEquals(
            listOf(
                ListMenuAction.PLAY_ALL,
                ListMenuAction.SHUFFLE_ALL,
                ListMenuAction.QUEUE_ALL,
                ListMenuAction.CACHE_ALL,
                ListMenuAction.DELETE_CACHES,
            ),
            ListMenuAction.entries.toList(),
        )
    }

    @Test
    fun `song menus expose the five migrated top-menu actions in order`() {
        fiveItemMenus.forEach { menu ->
            assertEquals(
                "menu=$menu",
                listOf(
                    ListMenuAction.PLAY_ALL,
                    ListMenuAction.SHUFFLE_ALL,
                    ListMenuAction.QUEUE_ALL,
                    ListMenuAction.CACHE_ALL,
                    ListMenuAction.DELETE_CACHES,
                ),
                menu.items().map { it.action },
            )
        }
    }

    @Test
    fun `history menu plays shuffles queues caches but never deletes`() {
        val actions = ListMenu.RootHistory.items().map { it.action }
        assertEquals(
            listOf(
                ListMenuAction.PLAY_ALL,
                ListMenuAction.SHUFFLE_ALL,
                ListMenuAction.QUEUE_ALL,
                ListMenuAction.CACHE_ALL,
            ),
            actions,
        )
        assertFalse(actions.contains(ListMenuAction.DELETE_CACHES))
    }

    @Test
    fun `downloads menu drops the no-op cache-all action`() {
        val actions = ListMenu.RootDownloads.items().map { it.action }
        assertEquals(
            listOf(
                ListMenuAction.PLAY_ALL,
                ListMenuAction.SHUFFLE_ALL,
                ListMenuAction.QUEUE_ALL,
                ListMenuAction.DELETE_CACHES,
            ),
            actions,
        )
        assertFalse(actions.contains(ListMenuAction.CACHE_ALL))
    }

    // ---- 文案（逐字取自 Copy）----

    @Test
    fun `menu labels match Copy verbatim`() {
        allMenus.forEach { menu ->
            menu.items().forEach { entry ->
                val expected = when (entry.action) {
                    ListMenuAction.PLAY_ALL -> Copy.PLAY_ALL
                    ListMenuAction.SHUFFLE_ALL -> Copy.SHUFFLE_PLAY_ALL
                    ListMenuAction.QUEUE_ALL -> Copy.QUEUE_ALL
                    ListMenuAction.CACHE_ALL -> Copy.CACHE_ALL
                    ListMenuAction.DELETE_CACHES -> Copy.DELETE_ALL_CACHES
                }
                assertEquals("menu=$menu action=${entry.action}", expected, entry.label)
            }
        }
    }

    @Test
    fun `every menu exposes at least play and queue`() {
        allMenus.forEach { menu ->
            val actions = menu.items().map { it.action }
            assertTrue("menu=$menu", actions.contains(ListMenuAction.PLAY_ALL))
            assertTrue("menu=$menu", actions.contains(ListMenuAction.QUEUE_ALL))
        }
    }
}

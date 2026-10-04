package dev.wearjelly.data.offline

import dev.wearjelly.data.ItemPage
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.LibraryKind

/** Reads the last complete Room snapshot without touching the network. */
class OfflineLibraryReader(private val database: OfflineDatabase) {
    suspend fun hasSnapshot(): Boolean = database.profiles().lastUsable() != null

    suspend fun lyrics(itemId: String): dev.wearjelly.data.LyricsResult {
        val profile = database.profiles().lastUsable() ?: return dev.wearjelly.data.LyricsResult.Error("无离线曲库")
        val entity = database.lyrics().get(profile.serverKey, itemId)
            ?: return dev.wearjelly.data.LyricsResult.NotFound
        return when (entity.state) {
            "AVAILABLE" -> dev.wearjelly.data.LyricsResult.Found(OfflineJson.decodeLyrics(entity.lyricsJson))
            "NOT_FOUND" -> dev.wearjelly.data.LyricsResult.NotFound
            else -> dev.wearjelly.data.LyricsResult.Error(entity.lastError ?: "歌词暂不可用")
        }
    }


    suspend fun page(
        query: LibraryKind,
        playlistId: String?,
        parentId: String? = null,
        artistId: String? = null,
        offset: Int,
        limit: Int
    ): ItemPage {
        val profile = database.profiles().lastUsable() ?: return ItemPage(emptyList(), 0)
        val key = profile.serverKey
        val items = when (query) {
            LibraryKind.PLAYLISTS -> database.playlists().all(key).map { playlist ->
                JellyfinItem(
                    id = playlist.itemId,
                    name = playlist.name,
                    type = "Playlist",
                    imageTags = playlist.imageTag?.let { mapOf("Primary" to it) } ?: emptyMap()
                )
            }
            LibraryKind.SONGS -> songs(key, playlistId, parentId, artistId)
            LibraryKind.DOWNLOADS -> database.tracks().all(key)
                .filter { it.localAudioPath?.let { path -> java.io.File(path).isFile } == true }
                .map(TrackEntity::toJellyfinItem)
            LibraryKind.ALBUMS -> database.tracks().all(key)
                .filter { !it.albumId.isNullOrBlank() }
                .distinctBy { it.albumId }
                .map { track ->
                    JellyfinItem(
                        id = track.albumId!!,
                        name = track.album ?: "未知专辑",
                        type = "MusicAlbum",
                        albumArtist = track.artistText
                    )
                }
            LibraryKind.ARTISTS -> database.tracks().all(key)
                .flatMap { track ->
                    val names = track.toJellyfinItem().artists.ifEmpty {
                        listOfNotNull(track.toJellyfinItem().albumArtist)
                    }
                    names.map { name -> JellyfinItem(id = "name:$name", name = name, type = "MusicArtist") }
                }
                .distinctBy { it.id }
        }
        val from = offset.coerceIn(0, items.size)
        val to = (from + limit).coerceAtMost(items.size)
        return ItemPage(items.subList(from, to), items.size)
    }

    private suspend fun songs(
        serverKey: String,
        playlistId: String?,
        parentId: String?,
        artistId: String?
    ): List<JellyfinItem> {
        val tracks = if (playlistId == null) {
            database.tracks().all(serverKey)
        } else {
            val ids = database.playlistTracks().tracksInOrder(serverKey, playlistId).map { it.trackId }
            ids.mapNotNull { database.tracks().get(serverKey, it) }
        }
        return tracks.map(TrackEntity::toJellyfinItem).filter { item ->
            (parentId == null || item.albumId == parentId) &&
                (artistId == null || item.artists.any { "name:$it" == artistId } || "name:${item.albumArtist}" == artistId)
        }
    }
}

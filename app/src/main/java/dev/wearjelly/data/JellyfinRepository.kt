package dev.wearjelly.data

import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class JellyfinException(message: String, val statusCode: Int? = null) : IOException(message)

fun normalizeServerUrl(rawUrl: String): String {
    var trimmed = rawUrl.trim().replace("\\s+".toRegex(), "")
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed = "http://$trimmed"
    }
    while (trimmed.endsWith("/")) {
        trimmed = trimmed.substring(0, trimmed.length - 1)
    }
    val parsed = trimmed.toHttpUrlOrNull() ?: throw JellyfinException("无效的服务器地址: $rawUrl")
    val portPart = if (parsed.port != 80 && parsed.port != 443) ":${parsed.port}" else ""
    val pathPart = parsed.encodedPath.trimEnd('/')
    return "${parsed.scheme}://${parsed.host}$portPart$pathPart"
}

class JellyfinRepository(
    private val client: OkHttpClient,
    private val json: Json,
    private val store: SessionStore
) {
    val session: StateFlow<ServerSession?> = store.session

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun authHeader(token: String? = null): String {
        val clientName = "WearJelly"
        val deviceName = "TicWatch Pro X"
        val deviceId = store.deviceId
        val version = "1.0.0"
        val base = "MediaBrowser Client=\"$clientName\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$version\""
        return if (token.isNullOrBlank()) base else "$base, Token=\"$token\""
    }

    suspend fun login(server: String, username: String, password: String): ServerSession = withContext(Dispatchers.IO) {
        val normalizedServer = normalizeServerUrl(server)
        val loginUrl = "$normalizedServer/Users/AuthenticateByName"

        val reqBodyDto = JellyfinAuthenticateByNameRequest(
            username = username,
            pw = password
        )
        val requestBody = json.encodeToString(reqBodyDto).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(loginUrl)
            .post(requestBody)
            .header("X-Emby-Authorization", authHeader())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 400) {
                    throw JellyfinException("用户名或密码错误", response.code)
                }
                throw JellyfinException("连接服务器失败 (HTTP ${response.code})", response.code)
            }
            val bodyString = response.body?.string() ?: throw JellyfinException("服务器未返回数据")
            val authResult = json.decodeFromString<JellyfinAuthenticationResult>(bodyString)
            val user = authResult.user ?: throw JellyfinException("未获取到用户信息")

            val session = ServerSession(
                serverUrl = normalizedServer,
                userId = user.id,
                userName = user.name,
                accessToken = authResult.accessToken
            )
            store.save(session)
            session
        }
    }

    fun logout() {
        store.clear()
    }

    suspend fun getItems(
        kind: LibraryKind,
        parentId: String? = null,
        artistId: String? = null,
        startIndex: Int = 0,
        limit: Int = 60
    ): ItemPage = withContext(Dispatchers.IO) {
        val currentSession = session.value ?: throw JellyfinException("未登录", 401)
        val server = currentSession.serverUrl
        val userId = currentSession.userId

        val urlBuilder = when (kind) {
            LibraryKind.ARTISTS -> {
                val url = "$server/Artists?StartIndex=$startIndex&Limit=$limit&Recursive=true&SortBy=SortName&SortOrder=Ascending"
                StringBuilder(url)
            }
            LibraryKind.ALBUMS -> {
                val url = "$server/Users/$userId/Items?IncludeItemTypes=MusicAlbum&Recursive=true&StartIndex=$startIndex&Limit=$limit&SortBy=SortName&SortOrder=Ascending"
                val sb = StringBuilder(url)
                if (!artistId.isNullOrBlank()) {
                    sb.append("&ArtistIds=").append(URLEncoder.encode(artistId, "UTF-8"))
                }
                if (!parentId.isNullOrBlank()) {
                    sb.append("&ParentId=").append(URLEncoder.encode(parentId, "UTF-8"))
                }
                sb
            }
            LibraryKind.SONGS -> {
                val url = "$server/Users/$userId/Items?IncludeItemTypes=Audio&Recursive=true&StartIndex=$startIndex&Limit=$limit&SortBy=SortName&SortOrder=Ascending"
                val sb = StringBuilder(url)
                if (!parentId.isNullOrBlank()) {
                    sb.append("&ParentId=").append(URLEncoder.encode(parentId, "UTF-8"))
                }
                if (!artistId.isNullOrBlank()) {
                    sb.append("&ArtistIds=").append(URLEncoder.encode(artistId, "UTF-8"))
                }
                sb
            }
            LibraryKind.DOWNLOADS -> throw JellyfinException("已缓存音乐从本地读取")
            LibraryKind.PLAYLISTS -> {
                val url = "$server/Users/$userId/Items?IncludeItemTypes=Playlist&Recursive=true&StartIndex=$startIndex&Limit=$limit&SortBy=SortName&SortOrder=Ascending"
                StringBuilder(url)
            }
        }

        urlBuilder.append("&Fields=ItemCounts,PrimaryImageAspectRatio,CanDelete,MediaSourceCount")

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .get()
            .header("X-Emby-Authorization", authHeader(currentSession.accessToken))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw JellyfinException("获取曲库失败 (HTTP ${response.code})", response.code)
            }
            val bodyString = response.body?.string() ?: throw JellyfinException("曲库数据为空")
            val result = json.decodeFromString<JellyfinItemsResult>(bodyString)
            ItemPage(items = result.items, totalRecordCount = result.totalRecordCount)
        }
    }

    suspend fun getPlaylistItems(
        playlistId: String,
        startIndex: Int = 0,
        limit: Int = 200
    ): ItemPage = withContext(Dispatchers.IO) {
        val currentSession = session.value ?: throw JellyfinException("未登录", 401)
        val encodedId = URLEncoder.encode(playlistId, "UTF-8")
        val url = "${currentSession.serverUrl}/Playlists/$encodedId/Items?UserId=${currentSession.userId}&StartIndex=$startIndex&Limit=$limit&Fields=PrimaryImageAspectRatio,MediaSources"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("X-Emby-Authorization", authHeader(currentSession.accessToken))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw JellyfinException("获取播放列表失败 (HTTP ${response.code})", response.code)
            val bodyString = response.body?.string() ?: throw JellyfinException("播放列表数据为空")
            val result = json.decodeFromString<JellyfinItemsResult>(bodyString)
            ItemPage(result.items, result.totalRecordCount)
        }
    }

    /**
     * true = 服务器不认 10.9+ 的 /UserFavoriteItems 路由，写收藏直接走老路由 /Users/{userId}/FavoriteItems。
     * 任一路由 404 时翻转并缓存，避免每次双请求；切换服务器后若新服务器也不认老路由会自愈重置。
     */
    private @Volatile var legacyFavoriteRoute: Boolean = false
    /** true = /Users/{userId}/Items?isFavorite 查询路由已确认 404（Jellyfin 12.x 移除），改走 /Items?userId= 根路由。 */
    private @Volatile var rootFavoriteQuery: Boolean = false

    /**
     * 全量收藏 id 集合。内部分页（StartIndex/Limit），本次最多取 maxItems 条（默认 3000，对齐 AppViewModel.MAX_FETCH_ITEMS）。
     * 查询路由：首选 /Users/{userId}/Items?isFavorite=true（本项目既有路由，Jellyfin 10.7-10.10）；
     * 404 回退 /Items?userId=...&isFavorite=true（Jellyfin 12.x 唯一文档化写法）。
     * 未登录抛 JellyfinException("未登录", 401)。契约见 docs/favorite-design.md §3.2。
     */
    suspend fun getFavoriteIds(
        startIndex: Int = 0,
        limit: Int = 300,
        maxItems: Int = 3000
    ): Set<String> = withContext(Dispatchers.IO) {
        val currentSession = session.value ?: throw JellyfinException("未登录", 401)
        val server = currentSession.serverUrl
        val userId = currentSession.userId
        val token = currentSession.accessToken
        val ids = LinkedHashSet<String>()
        var offset = startIndex
        val pageSize = limit.coerceAtLeast(1)
        var switched = false
        while (ids.size < maxItems) {
            val pageLimit = minOf(pageSize, maxItems - ids.size)
            val page = fetchFavoritePage(server, userId, token, offset, pageLimit, allowSwitch = !switched)
            if (page == null) {
                // 已切换到回退路由，同 offset 重试；每次调用最多切换一次，防止两路由 404 时来回震荡
                switched = true
                continue
            }
            if (page.items.isEmpty()) break
            ids.addAll(page.items.map { it.id })
            offset += page.items.size
            if (page.totalRecordCount in 1..offset) break
            if (page.items.size < pageLimit) break
        }
        ids
    }

    /** 收藏列表单页；null = 已翻转路由标志需要以同参数重试。 */
    private fun fetchFavoritePage(
        server: String,
        userId: String,
        token: String,
        startIndex: Int,
        limit: Int,
        allowSwitch: Boolean
    ): JellyfinItemsResult? {
        val types = "IncludeItemTypes=Audio,MusicAlbum,Playlist,MusicArtist"
        val path = if (!rootFavoriteQuery) {
            "/Users/$userId/Items?isFavorite=true&Recursive=true&$types" +
                "&StartIndex=$startIndex&Limit=$limit&SortBy=SortName&SortOrder=Ascending"
        } else {
            "/Items?userId=$userId&isFavorite=true&recursive=true&$types" +
                "&startIndex=$startIndex&limit=$limit&sortBy=SortName&sortOrder=Ascending"
        }
        val request = Request.Builder()
            .url("$server$path")
            .get()
            .header("X-Emby-Authorization", authHeader(token))
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 404 && allowSwitch) {
                rootFavoriteQuery = !rootFavoriteQuery
                return@use null
            }
            if (!response.isSuccessful) {
                throw JellyfinException("获取收藏失败 (HTTP ${response.code})", response.code)
            }
            val bodyString = response.body?.string() ?: throw JellyfinException("收藏数据为空")
            json.decodeFromString<JellyfinItemsResult>(bodyString)
        }
    }

    /**
     * 设置收藏状态（非盲目 toggle）。返回服务端确认后的 isFavorite（解析 200 响应体 UserItemDataDto.favorite）。
     * 路由：POST/DELETE /UserFavoriteItems/{itemId}?userId=...（10.9+）；
     * 404 回退 POST/DELETE /Users/{userId}/FavoriteItems/{itemId}（10.7/10.8 老路由，回退成功后缓存 legacyFavoriteRoute）。
     * 失败抛 JellyfinException。契约见 docs/favorite-design.md §3.2。
     */
    suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        val currentSession = session.value ?: throw JellyfinException("未登录", 401)
        val encodedId = URLEncoder.encode(itemId, "UTF-8")
        val userId = currentSession.userId
        val method = if (favorite) "POST" else "DELETE"

        fun call(path: String): Boolean? {
            val request = Request.Builder()
                .url("${currentSession.serverUrl}$path")
                .method(method, if (favorite) ByteArray(0).toRequestBody(jsonMediaType) else null)
                .header("X-Emby-Authorization", authHeader(currentSession.accessToken))
                .build()
            return client.newCall(request).execute().use { response ->
                if (response.code == 404) return@use null // 路由不存在 → 交给回退
                if (!response.isSuccessful) {
                    throw JellyfinException("收藏操作失败 (HTTP ${response.code})", response.code)
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use favorite // 空响应 → 采纳请求目标态
                try {
                    json.decodeFromString<JellyfinUserItemDataDto>(body).favorite
                } catch (_: Exception) {
                    favorite // 响应体不可解析 → 采纳请求目标态
                }
            }
        }

        val primaryPath = "/UserFavoriteItems/$encodedId?userId=$userId"
        val legacyPath = "/Users/$userId/FavoriteItems/$encodedId"
        var confirmed: Boolean? = if (legacyFavoriteRoute) null else call(primaryPath)
        if (confirmed == null) {
            confirmed = call(legacyPath)
            if (confirmed != null) {
                legacyFavoriteRoute = true
            } else if (legacyFavoriteRoute) {
                // 老路由也 404：可能切换了服务器，自愈重置后再试现代路由一次
                legacyFavoriteRoute = false
                confirmed = call(primaryPath)
            }
        }
        confirmed ?: throw JellyfinException("收藏操作失败 (HTTP 404)", 404)
    }

    val bitrate: StateFlow<AudioBitrate> = store.bitrate

    fun setBitrate(newBitrate: AudioBitrate) {
        store.setBitrate(newBitrate)
    }

    fun streamUrl(itemId: String): String {
        val currentSession = session.value ?: return ""
        val server = currentSession.serverUrl
        val userId = currentSession.userId
        val deviceId = store.deviceId
        val token = currentSession.accessToken
        val currentBitrate = store.bitrate.value

        return if (currentBitrate == AudioBitrate.ORIGINAL) {
            "$server/Audio/$itemId/stream?static=true&UserId=$userId&DeviceId=$deviceId&api_key=$token"
        } else {
            val bps = currentBitrate.kbps * 1000
            "$server/Audio/$itemId/stream.mp3?audioCodec=mp3&audioBitRate=$bps&maxStreamingBitrate=$bps&UserId=$userId&DeviceId=$deviceId&api_key=$token"
        }
    }

    fun imageUrl(item: JellyfinItem, maxWidth: Int = 300): String? {
        val currentSession = session.value ?: return null
        val server = currentSession.serverUrl
        val token = currentSession.accessToken

        return when {
            item.imageTags.containsKey("Primary") -> {
                val tag = item.imageTags["Primary"]
                "$server/Items/${item.id}/Images/Primary?maxWidth=$maxWidth&tag=$tag&api_key=$token"
            }
            !item.albumId.isNullOrBlank() && !item.albumPrimaryImageTag.isNullOrBlank() -> {
                val tag = item.albumPrimaryImageTag
                "$server/Items/${item.albumId}/Images/Primary?maxWidth=$maxWidth&tag=$tag&api_key=$token"
            }
            item.type == "MusicArtist" && item.imageTags.containsKey("Primary") -> {
                "$server/Items/${item.id}/Images/Primary?maxWidth=$maxWidth&api_key=$token"
            }
            else -> null
        }
    }

    suspend fun lyrics(itemId: String): LyricsResult = withContext(Dispatchers.IO) {
        val currentSession = session.value ?: return@withContext LyricsResult.Error("未登录")
        val server = currentSession.serverUrl
        val token = currentSession.accessToken

        val url = "$server/Audio/$itemId/Lyrics"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("X-Emby-Authorization", authHeader(token))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404 || response.code == 405 || response.code == 501) {
                    return@withContext LyricsResult.NotFound
                }
                if (!response.isSuccessful) {
                    return@withContext LyricsResult.Error("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return@withContext LyricsResult.NotFound
                val dto = try {
                    json.decodeFromString<JellyfinLyricsDto>(body)
                } catch (_: Exception) {
                    return@withContext LyricsResult.Error("歌词格式无法解析")
                }
                val lines = dto.lyrics.map { LyricsLine(text = it.text, startTicks = it.start) }
                if (lines.isEmpty()) return@withContext LyricsResult.NotFound
                val hasSync = lines.any { it.startTicks != null }
                LyricsResult.Found(SongLyrics(lines = lines, isSynchronized = hasSync))
            }
        } catch (e: Exception) {
            LyricsResult.Error(e.localizedMessage)
        }
    }

    suspend fun reportPlayback(
        itemId: String,
        positionMs: Long,
        isPaused: Boolean,
        event: PlaybackEvent
    ) = withContext(Dispatchers.IO) {
        val currentSession = session.value ?: return@withContext
        val server = currentSession.serverUrl
        val token = currentSession.accessToken
        val ticks = positionMs * 10000L

        val (endpoint, requestBody) = when (event) {
            PlaybackEvent.START -> {
                val dto = JellyfinPlaybackProgressRequest(
                    itemId = itemId,
                    positionTicks = ticks,
                    isPaused = false
                )
                "/Sessions/Playing" to json.encodeToString(dto).toRequestBody(jsonMediaType)
            }
            PlaybackEvent.PROGRESS -> {
                val dto = JellyfinPlaybackProgressRequest(
                    itemId = itemId,
                    positionTicks = ticks,
                    isPaused = isPaused
                )
                "/Sessions/Playing/Progress" to json.encodeToString(dto).toRequestBody(jsonMediaType)
            }
            PlaybackEvent.STOP -> {
                val dto = JellyfinPlaybackStopRequest(
                    itemId = itemId,
                    positionTicks = ticks
                )
                "/Sessions/Playing/Stopped" to json.encodeToString(dto).toRequestBody(jsonMediaType)
            }
        }

        val request = Request.Builder()
            .url("$server$endpoint")
            .post(requestBody)
            .header("X-Emby-Authorization", authHeader(token))
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {
            // 上报进度属于尽力而为机制，不打断主播放逻辑
        }
    }
}

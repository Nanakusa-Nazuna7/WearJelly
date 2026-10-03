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

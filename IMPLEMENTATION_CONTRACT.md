# WearJelly implementation contract

Package: `dev.wearjelly`. Kotlin 2.0.21, JVM 17, minSdk 26, compile/target 35, AGP 8.5.2, Gradle 8.7. Compose BOM 2024.09.03, Wear Compose 1.4.1, Media3 1.4.1, Koin 3.5.6, OkHttp 4.12.0, kotlinx serialization 1.7.3, coroutines 1.9.0, Coil Compose 2.7.0. No GMS/Play/Firebase dependencies.

## Data layer (package dev.wearjelly.data)

- `@Serializable data class ServerSession(val serverUrl: String, val userId: String, val userName: String, val accessToken: String)`
- `SessionStore(context: Context, json: Json)` exposes `val session: StateFlow<ServerSession?>`, `val deviceId: String`, `fun save(value: ServerSession)`, `fun clear()`. Token persisted using Android Keystore AES/GCM, never password. Writes must keep in-memory/session storage consistent. Backup disabled. Store decryption failures clear corrupt data safely.
- `@Serializable data class JellyfinItem` fields with PascalCase `@SerialName`: `id: String`, `name: String`, `type: String?`, `artists: List<String>`, `albumArtist: String?`, `album: String?`, `albumId: String?`, `runTimeTicks: Long?`, `imageTags: Map<String,String>`, `albumPrimaryImageTag: String?`, `indexNumber: Int?`, `parentIndexNumber: Int?`. Defaults for nullable/collections. More fields as needed. Property `artistText: String` convenience.
- `data class ItemPage(val items: List<JellyfinItem>, val totalRecordCount: Int)`.
- `data class LyricsLine(val text: String, val startTicks: Long?)` and `data class SongLyrics(val lines: List<LyricsLine>, val synchronized: Boolean)`.
- `enum class LibraryKind { ARTISTS, ALBUMS, SONGS }`
- `JellyfinRepository(client: OkHttpClient, json: Json, store: SessionStore)`:
  - `val session: StateFlow<ServerSession?>`
  - `suspend fun login(server: String, username: String, password: String)` (persists session)
  - `fun logout()` (clear session)
  - `suspend fun getItems(kind: LibraryKind, parentId: String? = null, artistId: String? = null, startIndex: Int = 0, limit: Int = 60): ItemPage`
  - `fun streamUrl(itemId: String): String` direct static stream URL with API token query for Media3. Preserve reverse proxy base path, encoded query values. Use `/Audio/{id}/stream?static=true&UserId=...&DeviceId=...&api_key=...`; don't force codec transcode.
  - `fun imageUrl(item: JellyfinItem): String?` prefer item Primary tag, otherwise album Primary.
  - `suspend fun lyrics(itemId: String): SongLyrics?` returns null for optional endpoint 404/405/501, not for auth/network errors.
  - `suspend fun reportPlayback(itemId: String, positionMs: Long, paused: Boolean, event: PlaybackEvent)` with `enum class PlaybackEvent { START, PROGRESS, STOP }`.
- `JellyfinException(message: String, val statusCode: Int? = null): IOException` user-safe message, no token/password/raw URL logged.
- Public helper `normalizeServerUrl(value: String): String` for validation/testing; http/https only, no userinfo/query/fragment, preserve reverse proxy prefix.

## Playback (package dev.wearjelly.playback)

- `data class QueueTrack(val item: JellyfinItem, val artworkUrl: String?)`.
- `data class PlaybackState(val connected: Boolean = false, val queue: List<QueueTrack> = emptyList(), val currentIndex: Int = -1, val current: JellyfinItem? = null, val isPlaying: Boolean = false, val buffering: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0, val error: String? = null, val repeatMode: Int = 0, val shuffleEnabled: Boolean = false)`.
- `PlaybackConnection(context: Context, repository: JellyfinRepository)` Koin singleton, `val state: StateFlow<PlaybackState>`, `fun connect()`, `fun play(items: List<JellyfinItem>, index: Int = 0)`, `fun enqueue(item: JellyfinItem)`, `fun togglePlayPause()`, `fun next()`, `fun previous()`, `fun seekTo(positionMs: Long)`, `fun playQueueIndex(index: Int)`, `fun removeQueueItem(index: Int)`, `fun moveQueueItem(from: Int, to: Int)`, `fun toggleShuffle()`, `fun cycleRepeatMode()`, `fun stopAndClear()`, `fun release()`.
- `PlaybackService: MediaSessionService` owns ExoPlayer and MediaSession, automatic media foreground notification, PendingIntent to MainActivity. Audio focus/noisy handling/WAKE_MODE_NETWORK. Queue lives in player service; no fake Jellyfin queue endpoint. Report session playback best effort without stopping playback on report failures. Revoke stop service/controller queue on session change/logout. Use Koin via `getKoin().get<JellyfinRepository>()` or `by inject`.
- UI constructs playback from `JellyfinItem` and uses `state.current`; controller MediaMetadata extras may store Json encoded item; Media3 custom extras don't need cross-library models from other packages.

## UI (package dev.wearjelly.ui)

Main agent implements application/DI and Manifest. UI agent writes `AppViewModel` and `WearJellyApp` and screens. Expose `@Composable fun WearJellyApp(viewModel: AppViewModel)` entry point. `AppViewModel(repository: JellyfinRepository, playback: PlaybackConnection)` constructor for Koin viewModel. ViewModel has public `val session` or observes internally; all screens managed within root. Include login server/user/password, library Artists/Albums/Songs drilldown, paging load more, player artwork/time/seek/play/prev/next, queue add/remove/reorder/clear/shuffle/repeat, optional lyrics, settings switch server/logout. `ScalingLazyColumn`, `SwipeToDismissBox`, safe round screen padding, scrolling forms and rotary support. MainActivity uses `koinViewModel<AppViewModel>()`. Keep strings in Chinese, make basic screens useful on 454x454 round TicWatch. Login entry Material Compose TextField allowed using androidx.compose.material:material from BOM within Wear theme. Server switching stops playback, clears token and reopens login after confirmation. UI should not silently swallow errors, retry button, optional lyrics explain unavailable. Never log tokens.

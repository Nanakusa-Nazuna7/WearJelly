package dev.wearjelly.data

import android.content.Context
import androidx.room.withTransaction
import dev.wearjelly.data.offline.OfflineDatabase
import dev.wearjelly.data.offline.offlineServerKey
import dev.wearjelly.data.offline.toTrackEntity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DownloadStatus { WAITING, RUNNING, DONE, FAILED }

data class QueueEntry(
    val itemId: String,
    val itemName: String,
    val artistText: String,
    val status: DownloadStatus,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val estimated: Boolean = false,
    val qualityLabel: String = ""
)

/** 缓存队列：所有任务入队即可见（排队中/进度/完成/失败），串行 worker 依次下载 */
class DownloadManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val repository: JellyfinRepository,
    private val json: Json,
    private val database: OfflineDatabase
) {
    private val _downloads = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloads: StateFlow<List<DownloadedSong>> = _downloads.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val workerActive = AtomicBoolean(false)
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** CacheIndex：已缓存歌曲 id 集合（由 downloads 派生，删除/完成后自动实时更新）。 */
    val cachedTrackIds: StateFlow<Set<String>> = _downloads
        .map { list -> list.mapTo(mutableSetOf()) { it.item.id } }
        .stateIn(workerScope, SharingStarted.Eagerly, emptySet())

    private val downloadsDir: File
        get() = File(context.filesDir, "audio_downloads").apply { if (!exists()) mkdirs() }

    /** 旧版全局缓存索引，一次性归因迁入 Room 后删除（见 [migrateLegacyIndex]）。 */
    private val legacyIndexFile: File
        get() = File(context.filesDir, "downloads_index.json")

    /** 当前 _downloads/_queue 的作用域 key；被 session 收集协程写、worker/UI 协程读 → @Volatile。 */
    @Volatile
    private var downloadsServerKey: String? = null
    private var sessionScopeLoaded = false

    init {
        // F13：_downloads 按 serverKey 作用域，会话每次变化（含首个值）重载；
        // StateFlow 等值不重发，故 key 不变时 reloadSessionState 直接返回。
        workerScope.launch { repository.session.collect { reloadSessionState() } }
    }

    private fun currentServerKey(): String? = repository.session.value?.let {
        offlineServerKey(it.serverUrl, it.userId)
    }

    /** 会话 key 缺失（登出后的离线模式）时回退到最近一次同步成功的账号，与 OfflineLibraryReader 一致。 */
    private suspend fun effectiveServerKey(): String? =
        currentServerKey() ?: database.profiles().lastUsable()?.serverKey

    private suspend fun reloadSessionState() {
        val key = effectiveServerKey()
        migrateLegacyIndex(key)
        if (sessionScopeLoaded && key == downloadsServerKey) return
        sessionScopeLoaded = true
        downloadsServerKey = key
        _downloads.value = loadDownloadsFromRoom(key)
        _progress.value = emptyMap()
        restoreQueue(key)
    }

    /** Room 权威读：该账号下已落本地音频文件的缓存曲目 → DownloadedSong。 */
    private suspend fun loadDownloadsFromRoom(key: String?): List<DownloadedSong> {
        if (key == null) return emptyList()
        return database.tracks().all(key)
            .filter { track -> track.localAudioPath?.let { path -> File(path).isFile } == true }
            .mapNotNull { track ->
                val item = runCatching { json.decodeFromString<JellyfinItem>(track.metadataJson) }.getOrNull()
                    ?: return@mapNotNull null
                DownloadedSong(
                    item = item,
                    localFilePath = track.localAudioPath!!,
                    localCoverPath = track.localCoverPath,
                    downloadedTimeMs = track.downloadedTimeMs ?: System.currentTimeMillis(),
                    qualityLabel = track.qualityLabel ?: "未知音质",
                )
            }
    }

    /**
     * 旧全局 downloads_index.json 按归属归因迁入 Room：条目 item 已有其它账号 TrackEntity 的归到该账号，
     * 无归属的归到当前 key（只有当前会话能见到）；迁移完成删除文件。无会话时保留文件等下次归因。
     */
    private suspend fun migrateLegacyIndex(currentKey: String?) {
        val legacy = legacyIndexFile
        if (!legacy.exists() || currentKey == null) return
        val records = runCatching { json.decodeFromString<List<DownloadedSong>>(legacy.readText()) }.getOrNull()
            ?: return // 解析失败保留原文件，避免毁数据
        val profiles = database.profiles().all()
        database.withTransaction {
            records.forEach { record ->
                if (!File(record.localFilePath).exists()) return@forEach
                val owner = profiles.firstOrNull { profile -> database.tracks().get(profile.serverKey, record.item.id) != null }
                when {
                    owner == null -> database.tracks().upsert(
                        record.item.toTrackEntity(currentKey).copy(
                            localAudioPath = record.localFilePath,
                            localCoverPath = record.localCoverPath,
                            downloadedTimeMs = record.downloadedTimeMs,
                            qualityLabel = record.qualityLabel,
                        )
                    )
                    owner.serverKey == currentKey && database.tracks().get(owner.serverKey, record.item.id)?.localAudioPath == null ->
                        database.tracks().updateMediaState(owner.serverKey, record.item.id, record.localFilePath, record.localCoverPath, record.downloadedTimeMs, record.qualityLabel)
                    else -> Unit // 已归属其它账号或已回填
                }
            }
        }
        legacy.delete()
    }

    /** 按 serverKey 替换式恢复持久下载队列（换账号时旧账号排队项从 UI 清除）。 */
    private suspend fun restoreQueue(serverKey: String?) {
        if (serverKey == null) {
            _queue.value = emptyList()
            return
        }
        database.downloadTasks().recoverRunning(serverKey)
        val tasks = database.downloadTasks().all(serverKey)
        val restored = tasks.filter { it.status == "WAITING" || it.status == "RUNNING" }
            .mapNotNull { task ->
                runCatching { json.decodeFromString<JellyfinItem>(task.itemJson) }.getOrNull()?.let { item ->
                    QueueEntry(item.id, item.name, item.artistText, DownloadStatus.WAITING, task.downloadedBytes, task.totalBytes, qualityLabel = task.qualityLabel.orEmpty())
                }
            }
        _queue.value = restored
        if (restored.isNotEmpty()) startWorker()
    }

    fun isDownloaded(itemId: String): Boolean {
        return _downloads.value.any { it.item.id == itemId && File(it.localFilePath).exists() }
    }

    fun getLocalFilePath(itemId: String): String? {
        return _downloads.value.firstOrNull { it.item.id == itemId && File(it.localFilePath).exists() }?.localFilePath
    }

    fun getLocalCoverPath(itemId: String): String? {
        return _downloads.value.firstOrNull { it.item.id == itemId }
            ?.localCoverPath
            ?.takeIf { File(it).exists() }
    }

    fun enqueue(items: List<JellyfinItem>) {
        val additions = items
            .filter { !isDownloaded(it.id) }
            .filter { candidate -> _queue.value.none { e -> e.itemId == candidate.id && e.status != DownloadStatus.FAILED } }
        if (additions.isEmpty()) return
        val label = repository.bitrate.value.displayName
        val newEntries = additions.map {
            QueueEntry(
                itemId = it.id,
                itemName = it.name,
                artistText = it.artistText,
                status = DownloadStatus.WAITING,
                qualityLabel = label
            )
        }
        _queue.value = newEntries + _queue.value
        workerScope.launch {
            val serverKey = currentServerKey() ?: return@launch
            additions.forEach { item ->
                database.downloadTasks().upsert(
                    dev.wearjelly.data.offline.DownloadTaskEntity(
                        serverKey = serverKey,
                        taskId = item.id,
                        itemId = item.id,
                        itemJson = json.encodeToString(item),
                        status = "WAITING",
                        qualityLabel = label,
                        updatedAtMs = System.currentTimeMillis(),
                    )
                )
            }
        }
        startWorker()
    }

    private fun updateEntry(itemId: String, change: (QueueEntry) -> QueueEntry) {
        _queue.value = _queue.value.map { if (it.itemId == itemId) change(it) else it }
    }

    private fun startWorker() {
        if (workerActive.compareAndSet(false, true)) {
            workerScope.launch {
                try {
                    while (true) {
                        val next = _queue.value.firstOrNull { it.status == DownloadStatus.WAITING } ?: break
                        updateEntry(next.itemId) { it.copy(status = DownloadStatus.RUNNING) }
                        _downloadingIds.value = _downloadingIds.value + next.itemId
                        val ok = downloadNow(next.itemId, next.itemName, next.artistText)
                        _downloadingIds.value = _downloadingIds.value - next.itemId
                        updateEntry(next.itemId) {
                            it.copy(status = if (ok) DownloadStatus.DONE else DownloadStatus.FAILED)
                        }
                        if (ok) _progress.value = _progress.value - next.itemId
                    }
                } finally {
                    workerActive.set(false)
                    if (_queue.value.any { it.status == DownloadStatus.WAITING }) startWorker()
                }
            }
        }
    }

    private suspend fun downloadNow(itemId: String, itemName: String, artistText: String): Boolean = withContext(Dispatchers.IO) {
        val item = _downloads.value.firstOrNull { it.item.id == itemId }?.item
            ?: JellyfinItem(id = itemId, name = itemName, artists = listOf(artistText))
        val serverKey = currentServerKey()
        serverKey?.let {
            database.downloadTasks().upsert(
                dev.wearjelly.data.offline.DownloadTaskEntity(
                    serverKey = it,
                    taskId = item.id,
                    itemId = item.id,
                    itemJson = json.encodeToString(item),
                    status = "RUNNING",
                    qualityLabel = repository.bitrate.value.displayName,
                    updatedAtMs = System.currentTimeMillis(),
                )
            )
        }
        val url = repository.streamUrl(item.id)
        if (url.isBlank()) {
            serverKey?.let { database.downloadTasks().updateStatus(it, item.id, "FAILED", "未找到音频流地址", 0L, -1L, System.currentTimeMillis()) }
            return@withContext false
        }

        val selectedBitrate = repository.bitrate.value
        val qualityLabel = selectedBitrate.displayName
        val extension = if (selectedBitrate == AudioBitrate.ORIGINAL) {
            item.container?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "audio"
        } else {
            "mp3"
        }
        val targetFile = File(downloadsDir, "${item.id}.$extension")
        val tempFile = File(downloadsDir, "${item.id}.tmp")

        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                val estimatedBytes = if (selectedBitrate != AudioBitrate.ORIGINAL && item.durationMs > 0L) {
                    ((item.durationMs / 1000.0) * selectedBitrate.kbps * 1000.0 / 8.0 * 1.03).toLong()
                } else {
                    -1L
                }
                val totalBytes = contentLength.takeIf { it > 0L } ?: estimatedBytes
                val isEstimated = contentLength <= 0L && estimatedBytes > 0L
                var downloadedBytes = 0L
                suspend fun pushProgress() {
                    _progress.value = _progress.value + (
                        item.id to DownloadProgress(
                            itemId = item.id,
                            itemName = item.name,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            estimated = isEstimated,
                            qualityLabel = qualityLabel,
                        )
                    )
                    serverKey?.let {
                        database.downloadTasks().updateProgress(it, item.id, downloadedBytes, totalBytes, System.currentTimeMillis())
                    }
                    updateEntry(item.id) {
                        it.copy(downloadedBytes = downloadedBytes, totalBytes = totalBytes, estimated = isEstimated)
                    }
                }
                pushProgress()
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            pushProgress()
                        }
                        output.fd.sync()
                    }
                }
                if (tempFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    check(tempFile.renameTo(targetFile)) { "无法原子移动下载文件" }
                }
            }

            if (targetFile.exists()) {
                val coverFile = File(downloadsDir, "${item.id}.jpg")
                val coverSaved = if (coverFile.exists()) {
                    true
                } else try {
                    repository.imageUrl(item, maxWidth = 500)?.let { coverUrl ->
                        client.newCall(Request.Builder().url(coverUrl).build()).execute().use { coverResponse ->
                            if (coverResponse.isSuccessful) {
                                coverResponse.body?.byteStream()?.use { input ->
                                    FileOutputStream(coverFile).use { output -> input.copyTo(output) }
                                }
                                true
                            } else false
                        }
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                if (!coverSaved && coverFile.exists()) coverFile.delete()
                serverKey?.let { key ->
                    database.images().updateLocalState(
                        key,
                        "${item.id}:Primary",
                        coverFile.absolutePath.takeIf { coverSaved },
                        if (coverSaved) "READY" else "FAILED"
                    )
                }

                val record = DownloadedSong(
                    item = item,
                    localFilePath = targetFile.absolutePath,
                    localCoverPath = if (coverSaved) coverFile.absolutePath else null,
                    downloadedTimeMs = System.currentTimeMillis(),
                    qualityLabel = qualityLabel,
                )
                // F13：Room 事务（下方）是权威落库；内存态只在作用域仍是本下载的 key 时更新，
                // 防止下载中途切账号把旧账号记录写进新账号的 _downloads。
                if (serverKey == downloadsServerKey) {
                    _downloads.value = _downloads.value.filter { it.item.id != item.id } + record
                }
                serverKey?.let { key ->
                    database.withTransaction {
                        database.tracks().updateMediaState(key, item.id, targetFile.absolutePath, if (coverSaved) coverFile.absolutePath else null, record.downloadedTimeMs, qualityLabel)
                        database.downloadTasks().updateStatus(key, item.id, "DONE", null, targetFile.length(), targetFile.length(), record.downloadedTimeMs)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    fun deleteDownload(itemId: String) {
        val record = _downloads.value.firstOrNull { it.item.id == itemId } ?: return
        try {
            File(record.localFilePath).takeIf { it.exists() }?.delete()
            record.localCoverPath?.let { coverPath ->
                File(coverPath).takeIf { it.exists() }?.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // F13：按当前加载作用域清理 Room 媒体状态（原用 session key，登出离线时为 null 会漏清）。
        downloadsServerKey?.let { key ->
            workerScope.launch {
                database.tracks().updateMediaState(key, itemId, null, null, null, null)
                database.images().updateLocalState(key, "${itemId}:Primary", null, "METADATA")
            }
        }
        _downloads.value = _downloads.value.filter { it.item.id != itemId }
        _queue.value = _queue.value.filter { it.itemId != itemId }
    }
}

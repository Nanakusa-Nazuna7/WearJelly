package dev.wearjelly.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val repository: JellyfinRepository,
    private val json: Json
) {
    private val _downloads = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloads: StateFlow<List<DownloadedSong>> = _downloads.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val downloadsDir: File
        get() = File(context.filesDir, "audio_downloads").apply { if (!exists()) mkdirs() }

    private val indexFile: File
        get() = File(context.filesDir, "downloads_index.json")

    init {
        loadIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) {
            _downloads.value = emptyList()
            return
        }
        try {
            val content = indexFile.readText()
            val list = json.decodeFromString<List<DownloadedSong>>(content)
            // 过滤掉实际文件已被删除的记录
            val valid = list.filter { File(it.localFilePath).exists() }
            _downloads.value = valid
        } catch (e: Exception) {
            e.printStackTrace()
            _downloads.value = emptyList()
        }
    }

    private fun saveIndex(list: List<DownloadedSong>) {
        try {
            val content = json.encodeToString(list)
            indexFile.writeText(content)
            _downloads.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    suspend fun downloadSong(item: JellyfinItem): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(item.id) || _downloadingIds.value.contains(item.id)) {
            return@withContext true
        }

        val url = repository.streamUrl(item.id)
        if (url.isBlank()) return@withContext false

        _downloadingIds.value = _downloadingIds.value + item.id

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
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                val estimatedBytes = if (selectedBitrate != AudioBitrate.ORIGINAL && item.durationMs > 0L) {
                    // MP3 output is variable bitrate; include a small allowance for headers and encoder variance.
                    ((item.durationMs / 1000.0) * selectedBitrate.kbps * 1000.0 / 8.0 * 1.03).toLong()
                } else {
                    -1L
                }
                val totalBytes = contentLength.takeIf { it > 0L } ?: estimatedBytes
                val isEstimated = contentLength <= 0L && estimatedBytes > 0L
                var downloadedBytes = 0L
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
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
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
                        }
                        output.fd.sync()
                    }
                }
                if (tempFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                }
            }

            if (targetFile.exists()) {
                val coverFile = File(downloadsDir, "${item.id}.jpg")
                val coverSaved = try {
                    repository.imageUrl(item, maxWidth = 500)?.let { coverUrl ->
                        val request = Request.Builder().url(coverUrl).build()
                        client.newCall(request).execute().use { coverResponse ->
                            if (coverResponse.isSuccessful) {
                                coverResponse.body?.byteStream()?.use { input ->
                                    FileOutputStream(coverFile).use { output -> input.copyTo(output) }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                if (!coverSaved && coverFile.exists()) coverFile.delete()

                val record = DownloadedSong(
                    item = item,
                    localFilePath = targetFile.absolutePath,
                    localCoverPath = if (coverSaved) coverFile.absolutePath else null,
                    downloadedTimeMs = System.currentTimeMillis(),
                    qualityLabel = qualityLabel,
                )
                val updated = _downloads.value.filter { it.item.id != item.id } + record
                saveIndex(updated)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            false
        } finally {
            _downloadingIds.value = _downloadingIds.value - item.id
            _progress.value = _progress.value - item.id
        }
    }

    fun deleteDownload(itemId: String) {
        val record = _downloads.value.firstOrNull { it.item.id == itemId } ?: return
        try {
            val file = File(record.localFilePath)
            if (file.exists()) {
                file.delete()
            }
            record.localCoverPath?.let { coverPath ->
                val cover = File(coverPath)
                if (cover.exists()) cover.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val updated = _downloads.value.filter { it.item.id != itemId }
        saveIndex(updated)
    }
}

package dev.wearjelly.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HistoryStore(
    private val context: Context,
    private val json: Json
) {
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private val historyFile: File
        get() = File(context.filesDir, "play_history.json")

    init {
        load()
    }

    private fun load() {
        if (!historyFile.exists()) {
            _entries.value = emptyList()
            return
        }
        try {
            _entries.value = json.decodeFromString<List<HistoryEntry>>(historyFile.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            _entries.value = emptyList()
        }
    }

    @Synchronized
    fun record(item: JellyfinItem) {
        try {
            val updated = listOf(HistoryEntry(item = item, playedAtMs = System.currentTimeMillis())) +
                _entries.value.filter { it.item.id != item.id }
            val trimmed = updated.take(MAX_ENTRIES)
            historyFile.writeText(json.encodeToString(trimmed))
            _entries.value = trimmed
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun lastPlayedMs(itemId: String): Long {
        return _entries.value.firstOrNull { it.item.id == itemId }?.playedAtMs ?: 0L
    }

    private companion object { const val MAX_ENTRIES = 200 }
}

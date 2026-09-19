package dev.wearjelly.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JellyfinRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: JellyfinRepository
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private class MemorySessionStore(
        private val json: Json
    ) {
        private val _session = MutableStateFlow<ServerSession?>(null)
        val session: StateFlow<ServerSession?> = _session.asStateFlow()
        val deviceId: String = "test-device-id"

        fun save(newSession: ServerSession) {
            _session.value = newSession
        }

        fun clear() {
            _session.value = null
        }
    }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testNormalizeServerUrl() {
        assertEquals("http://192.168.1.100:8096", normalizeServerUrl("192.168.1.100:8096/"))
        assertEquals("https://jellyfin.example.com", normalizeServerUrl("https://jellyfin.example.com/"))
        assertEquals("http://10.0.0.2:8096/music", normalizeServerUrl("http://10.0.0.2:8096/music/"))
    }

    @Test
    fun testModelSerialization() {
        val jsonStr = """
            {
                "Id": "song_1",
                "Name": "Sample Track",
                "Artists": ["Singer A", "Singer B"],
                "Album": "Greatest Hits",
                "RunTimeTicks": 2400000000
            }
        """.trimIndent()
        val item = json.decodeFromString<JellyfinItem>(jsonStr)
        assertEquals("song_1", item.id)
        assertEquals("Sample Track", item.name)
        assertEquals("Singer A, Singer B", item.artistText)
        assertEquals(240000L, item.durationMs)
    }

    @Test
    fun testLyricsParsing() {
        val lyricsJson = """
            {
                "Lyrics": [
                    { "Text": "Hello world", "Start": 10000000 },
                    { "Text": "Second line", "Start": 50000000 }
                ]
            }
        """.trimIndent()
        val dto = json.decodeFromString<JellyfinLyricsDto>(lyricsJson)
        val lines = dto.lyrics.map { LyricsLine(text = it.text, startTicks = it.start) }
        val songLyrics = SongLyrics(lines = lines, isSynchronized = lines.any { it.startTicks != null })

        assertTrue(songLyrics.isSynchronized)
        assertEquals(2, songLyrics.lines.size)
        assertEquals(1000L, songLyrics.lines[0].startMs)
    }
}

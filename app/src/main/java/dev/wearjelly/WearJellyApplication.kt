package dev.wearjelly

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.wearjelly.data.JellyfinRepository
import dev.wearjelly.data.SessionStore
import dev.wearjelly.playback.PlaybackConnection
import dev.wearjelly.ui.AppViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.dsl.module

class WearJellyApplication : Application(), ImageLoaderFactory, KoinComponent {
    /** 离线同步的宿主作用域：登录/会话出现后后台触发，不占用 UI 线程。 */
    private val offlineSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalSerializationApi::class)
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WearJellyApplication)
            modules(module {
                single {
                    Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                        explicitNulls = false
                    }
                }
                single {
                    OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .build()
                }
                single { SessionStore(androidContext(), get()) }
                single { JellyfinRepository(get(), get(), get()) }
                single { dev.wearjelly.data.DownloadManager(androidContext(), get(), get(), get(), get()) }
                single { dev.wearjelly.data.HistoryStore(androidContext(), get()) }
                single { dev.wearjelly.data.LastPlaybackStore(androidContext(), get()) }
                single { dev.wearjelly.data.LyricsPrefs(androidContext(), get()) }
                single { PlaybackConnection(androidContext(), get(), get()) }
                single { dev.wearjelly.data.offline.OfflineDatabase.get(androidContext()) }
                single { dev.wearjelly.data.offline.OfflineLibrarySync(get(), get()) }
                 single { dev.wearjelly.data.offline.OfflineLibraryReader(get()) }
                single {
                    dev.wearjelly.data.offline.OfflineSyncCoordinator(get(), get(), offlineSyncScope)
                }
                viewModel { AppViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
            })
        }
        // Koin single 是懒创建：立即解析协调器，让已保存的会话（冷启动）也能自动同步
        get<dev.wearjelly.data.offline.OfflineSyncCoordinator>()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(get<OkHttpClient>())
        .crossfade(false)
        .diskCache(null)
        .build()
}

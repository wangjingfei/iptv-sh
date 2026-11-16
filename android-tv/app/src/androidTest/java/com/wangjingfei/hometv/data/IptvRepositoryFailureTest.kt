package com.wangjingfei.hometv.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IptvRepositoryFailureTest {
    @Test
    fun refreshFailuresKeepCachedDataAndDiagnosticsIdentifyEachBackend() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        val originalConfig = settings.config.first()
        val originalLastSyncAt = settings.lastSyncAt.first()
        val originalLastEpgSyncAt = settings.lastEpgSyncAt.first()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val playlistFails = AtomicBoolean(false)
        val epgFails = AtomicBoolean(false)
        val hlsFails = AtomicBoolean(false)

        try {
            MockWebServer().use { server ->
                server.dispatcher = backendDispatcher(server, playlistFails, epgFails, hlsFails)
                val playlistUrl = server.url("/playlist.m3u").toString()
                val epgUrl = server.url("/epg.xml").toString()
                val hlsHealthUrl = server.url("/hls/healthz").toString()
                val repository = repository(database, settings)

                val initial = repository.sync(playlistUrl, epgUrl)
                assertThat(initial.channelCount).isEqualTo(1)
                assertThat(initial.programmeCount).isEqualTo(1)
                assertThat(database.snapshot()).isEqualTo(CacheSnapshot(1, 1, "新闻直播"))

                epgFails.set(true)
                val degraded = repository.sync(playlistUrl, epgUrl)

                assertThat(degraded.channelCount).isEqualTo(1)
                assertThat(degraded.programmeCount).isEqualTo(-1)
                assertThat(degraded.warning).contains("节目单更新失败，已保留原缓存")
                assertThat(database.snapshot()).isEqualTo(CacheSnapshot(1, 1, "新闻直播"))

                hlsFails.set(true)
                val epgDiagnostic = repository.checkUrl("节目单", epgUrl)
                val hlsDiagnostic = repository.checkUrl("HLS 服务", hlsHealthUrl)

                assertThat(epgDiagnostic.status).isEqualTo(DiagnosticStatus.ERROR)
                assertThat(epgDiagnostic.name).isEqualTo("节目单")
                assertThat(epgDiagnostic.detail).contains("HTTP 503")
                assertThat(hlsDiagnostic.status).isEqualTo(DiagnosticStatus.ERROR)
                assertThat(hlsDiagnostic.name).isEqualTo("HLS 服务")
                assertThat(hlsDiagnostic.detail).contains("HTTP 503")

                playlistFails.set(true)
                val failedRefresh = runCatching { repository.sync(playlistUrl, epgUrl) }

                assertThat(failedRefresh.isFailure).isTrue()
                assertThat(database.snapshot()).isEqualTo(CacheSnapshot(1, 1, "新闻直播"))
            }
        } finally {
            settings.saveConfig(originalConfig.playlistUrl, originalConfig.epgUrl, originalConfig.setupComplete)
            settings.setLastSyncAt(originalLastSyncAt)
            settings.setLastEpgSyncAt(originalLastEpgSyncAt)
            database.close()
        }
    }

    private fun repository(database: AppDatabase, settings: SettingsRepository): IptvRepository {
        val client = OkHttpClient()
        val validator = SourceValidator()
        val sourceHttpClient = SourceHttpClient(client)
        val probeService = SourceProbeService(sourceHttpClient, M3uParser(), XmlTvParser(), validator)
        return IptvRepository(
            client = client,
            dao = database.iptvDao(),
            m3uParser = M3uParser(),
            xmlTvParser = XmlTvParser(),
            validator = validator,
            settings = settings,
            sourceHttpClient = sourceHttpClient,
            sourceProbeService = probeService,
        )
    }

    private fun backendDispatcher(
        server: MockWebServer,
        playlistFails: AtomicBoolean,
        epgFails: AtomicBoolean,
        hlsFails: AtomicBoolean,
    ) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
            "/playlist.m3u" -> if (playlistFails.get()) {
                MockResponse().setResponseCode(503)
            } else {
                MockResponse().setBody(
                    "#EXTM3U x-tvg-url=\"${server.url("/epg.xml")}\"\n" +
                        "#EXTINF:-1 tvg-id=\"news\" tvg-name=\"新闻\",新闻\n" +
                        "${server.url("/live/index.m3u8")}\n",
                )
            }
            "/epg.xml" -> if (epgFails.get()) {
                MockResponse().setResponseCode(503)
            } else {
                MockResponse().setBody(xmlTv())
            }
            "/live/index.m3u8" -> MockResponse().setBody("#EXTM3U\n#EXT-X-VERSION:3\n")
            "/hls/healthz" -> if (hlsFails.get()) {
                MockResponse().setResponseCode(503)
            } else {
                MockResponse().setBody("ok\n")
            }
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun xmlTv(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
        val now = OffsetDateTime.now(ZoneOffset.ofHours(8))
        return """
            <tv>
              <programme channel="news" start="${now.minusMinutes(10).format(formatter)}" stop="${now.plusMinutes(50).format(formatter)}">
                <title>新闻直播</title>
              </programme>
            </tv>
        """.trimIndent()
    }

    private fun AppDatabase.snapshot(): CacheSnapshot {
        val channelCount = query(SimpleSQLiteQuery("SELECT COUNT(*) FROM channels")).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        val programme = query(SimpleSQLiteQuery("SELECT COUNT(*), MIN(title) FROM programmes")).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) to cursor.getString(1)
        }
        return CacheSnapshot(channelCount, programme.first, programme.second)
    }

    private data class CacheSnapshot(
        val channelCount: Int,
        val programmeCount: Int,
        val firstProgrammeTitle: String?,
    )
}

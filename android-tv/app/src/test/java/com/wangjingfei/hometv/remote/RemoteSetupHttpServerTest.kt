package com.wangjingfei.hometv.remote

import com.google.common.truth.Truth.assertThat
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test

class RemoteSetupHttpServerTest {
    private val now = AtomicLong(1_000L)
    private val submissions = mutableListOf<RemoteSourceSubmission>()
    private val client = OkHttpClient()
    private lateinit var server: RemoteSetupHttpServer
    private lateinit var baseUrl: String

    @Before
    fun startServer() {
        server = RemoteSetupHttpServer(
            endpoint = LanEndpoint(InetAddress.getByName("127.0.0.1") as Inet4Address, 8),
            sessionKey = SESSION_KEY,
            expiresAt = 10_000L,
            initialPlaylistUrl = "http://192.168.31.200/list.m3u?x=1&y=2",
            initialEpgUrl = "http://192.168.31.200/epg.xml",
            now = now::get,
            onSubmit = { submissions += it; true },
        )
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        baseUrl = "http://127.0.0.1:${server.listeningPort}"
    }

    @After
    fun stopServer() = server.stop()

    @Test
    fun servesEscapedFormOnlyWithValidToken() {
        client.newCall(Request.Builder().url("$baseUrl/?key=$SESSION_KEY").build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            assertThat(response.code).isEqualTo(200)
            assertThat(body).contains("家庭电视设置")
            assertThat(body).contains("x=1&amp;y=2")
            assertThat(response.header("Cache-Control")).isEqualTo("no-store")
        }

        client.newCall(Request.Builder().url("$baseUrl/?key=wrong").build()).execute().use { response ->
            assertThat(response.code).isEqualTo(403)
        }
    }

    @Test
    fun acceptsFormOnceAndPublishesTestResult() {
        val form = FormBody.Builder()
            .add("playlist", "http://192.168.31.200:8091/new.m3u")
            .add("epg", "http://192.168.31.200:8091/new.xml")
            .build()
        client.newCall(
            Request.Builder().url("$baseUrl/submit?key=$SESSION_KEY").post(form).build(),
        ).execute().use { response ->
            assertThat(response.code).isEqualTo(200)
            assertThat(response.body?.string()).contains("电视正在测试来源")
        }
        assertThat(submissions).containsExactly(
            RemoteSourceSubmission(SESSION_KEY, "http://192.168.31.200:8091/new.m3u", "http://192.168.31.200:8091/new.xml"),
        )

        server.updateResult(RemoteSetupPhase.SUCCESS, "测试通过：112 个频道")
        client.newCall(Request.Builder().url("$baseUrl/status?key=$SESSION_KEY").build()).execute().use { response ->
            assertThat(response.body?.string()).contains("测试通过：112 个频道")
        }
    }

    @Test
    fun rejectsExpiredSessionAndOversizedInput() {
        now.set(10_000L)
        client.newCall(Request.Builder().url("$baseUrl/?key=$SESSION_KEY").build()).execute().use { response ->
            assertThat(response.code).isEqualTo(410)
        }
        now.set(1_000L)

        val oversized = "x".repeat(RemoteSetupHttpServer.MAX_BODY_BYTES.toInt())
        val body = FormBody.Builder().add("playlist", oversized).build()
        client.newCall(Request.Builder().url("$baseUrl/submit?key=$SESSION_KEY").post(body).build()).execute().use { response ->
            assertThat(response.code).isEqualTo(413)
        }
    }

    private companion object {
        const val SESSION_KEY = "test-session-key"
    }
}

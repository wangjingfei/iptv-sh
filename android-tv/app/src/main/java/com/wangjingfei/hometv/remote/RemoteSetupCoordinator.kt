package com.wangjingfei.hometv.remote

import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

@Singleton
class RemoteSetupCoordinator @Inject constructor(
    private val endpointResolver: LanEndpointResolver,
) {
    private val lock = Any()
    private val secureRandom = SecureRandom()
    private val _session = MutableStateFlow<RemoteSetupSession?>(null)
    private val submissionChannel = Channel<RemoteSourceSubmission>(capacity = 1)
    private var server: RemoteSetupHttpServer? = null

    val session: StateFlow<RemoteSetupSession?> = _session.asStateFlow()
    val submissions: Flow<RemoteSourceSubmission> = submissionChannel.receiveAsFlow()

    fun start(playlistUrl: String, epgUrl: String): Result<RemoteSetupSession> = runCatching {
        synchronized(lock) {
            stopLocked()
            val endpoint = endpointResolver.resolve() ?: error("电视没有可供手机访问的局域网 IPv4 地址")
            val sessionKey = createSessionKey()
            val expiresAt = System.currentTimeMillis() + SESSION_DURATION_MS
            val newServer = RemoteSetupHttpServer(
                endpoint = endpoint,
                sessionKey = sessionKey,
                expiresAt = expiresAt,
                initialPlaylistUrl = playlistUrl.trim(),
                initialEpgUrl = epgUrl.trim(),
                onSubmit = { submission -> acceptSubmission(submission) },
            )
            try {
                newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            } catch (error: Throwable) {
                newServer.stop()
                throw error
            }
            val url = "http://${endpoint.address.hostAddress}:${newServer.listeningPort}/?key=$sessionKey"
            val newSession = RemoteSetupSession(
                id = sessionKey,
                url = url,
                expiresAt = expiresAt,
                playlistUrl = playlistUrl.trim(),
                epgUrl = epgUrl.trim(),
            )
            server = newServer
            _session.value = newSession
            newSession
        }
    }

    fun updateResult(sessionId: String, phase: RemoteSetupPhase, message: String) {
        synchronized(lock) {
            val current = _session.value ?: return
            if (current.id != sessionId) return
            server?.updateResult(phase, message)
            _session.value = current.copy(phase = phase, message = message)
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun acceptSubmission(submission: RemoteSourceSubmission): Boolean {
        synchronized(lock) {
            val current = _session.value ?: return false
            if (current.id != submission.sessionId || System.currentTimeMillis() >= current.expiresAt) return false
            val accepted = submissionChannel.trySend(submission).isSuccess
            if (accepted) {
                _session.value = current.copy(
                    phase = RemoteSetupPhase.TESTING,
                    message = "手机已提交，正在测试来源",
                    playlistUrl = submission.playlistUrl,
                    epgUrl = submission.epgUrl,
                    submissionVersion = current.submissionVersion + 1,
                )
            }
            return accepted
        }
    }

    private fun stopLocked() {
        server?.stop()
        server = null
        while (submissionChannel.tryReceive().isSuccess) {
            // Discard submissions belonging to the session that just ended.
        }
        _session.value = null
    }

    private fun createSessionKey(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val SESSION_DURATION_MS = 10 * 60_000L
    }
}

package com.wangjingfei.hometv.remote

import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.InetAddress

internal class RemoteSetupHttpServer(
    private val endpoint: LanEndpoint,
    private val sessionKey: String,
    private val expiresAt: Long,
    private val initialPlaylistUrl: String,
    private val initialEpgUrl: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val onSubmit: (RemoteSourceSubmission) -> Boolean,
) : NanoHTTPD(endpoint.address.hostAddress, 0) {
    @Volatile
    private var phase = RemoteSetupPhase.WAITING

    @Volatile
    private var resultMessage = "等待手机提交"

    @Volatile
    private var currentPlaylistUrl = initialPlaylistUrl

    @Volatile
    private var currentEpgUrl = initialEpgUrl

    override fun serve(session: IHTTPSession): Response {
        if (!isAllowedClient(session.remoteIpAddress)) {
            return html(Response.Status.FORBIDDEN, messagePage("无法访问", "手机必须与电视处于同一局域网。"))
        }
        if (session.parameters["key"]?.firstOrNull() != sessionKey) {
            return html(Response.Status.FORBIDDEN, messagePage("链接无效", "请重新扫描电视上的二维码。"))
        }
        if (now() >= expiresAt) {
            return html(Response.Status.GONE, messagePage("链接已过期", "请在电视上重新打开手机设置。"))
        }
        return when {
            session.method == Method.GET && session.uri == "/" -> html(Response.Status.OK, formPage())
            session.method == Method.GET && session.uri == "/status" -> html(Response.Status.OK, statusPage())
            session.method == Method.POST && session.uri == "/submit" -> receiveSubmission(session)
            else -> html(Response.Status.NOT_FOUND, messagePage("页面不存在", "请重新扫描电视上的二维码。"))
        }
    }

    @Synchronized
    fun updateResult(newPhase: RemoteSetupPhase, message: String) {
        phase = newPhase
        resultMessage = message
    }

    private fun receiveSubmission(session: IHTTPSession): Response {
        val contentLength = session.headers["content-length"]?.toLongOrNull()
            ?: return html(Response.Status.BAD_REQUEST, messagePage("提交失败", "请求缺少有效长度。"))
        if (contentLength !in 1..MAX_BODY_BYTES) {
            return html(Response.Status.PAYLOAD_TOO_LARGE, messagePage("提交失败", "输入内容过长。"))
        }
        if (!session.headers["content-type"].orEmpty().startsWith(FORM_CONTENT_TYPE)) {
            return html(Response.Status.UNSUPPORTED_MEDIA_TYPE, messagePage("提交失败", "请求格式不受支持。"))
        }
        synchronized(this) {
            if (phase == RemoteSetupPhase.TESTING || phase == RemoteSetupPhase.SUCCESS) {
                return html(Response.Status.OK, statusPage())
            }
            runCatching { session.parseBody(HashMap()) }
                .getOrElse { return html(Response.Status.BAD_REQUEST, messagePage("提交失败", "无法解析输入内容。")) }
            val playlistUrl = session.parameters["playlist"]?.firstOrNull()?.trim().orEmpty()
            val epgUrl = session.parameters["epg"]?.firstOrNull()?.trim().orEmpty()
            if (playlistUrl.isBlank() || playlistUrl.length > MAX_URL_LENGTH || epgUrl.length > MAX_URL_LENGTH) {
                return html(Response.Status.BAD_REQUEST, messagePage("提交失败", "频道列表地址不能为空，且单个地址不能超过 2048 个字符。"))
            }
            currentPlaylistUrl = playlistUrl
            currentEpgUrl = epgUrl
            phase = RemoteSetupPhase.TESTING
            resultMessage = "电视正在测试来源"
            val accepted = onSubmit(RemoteSourceSubmission(sessionKey, playlistUrl, epgUrl))
            if (!accepted) {
                phase = RemoteSetupPhase.ERROR
                resultMessage = "电视暂时无法接收，请稍后重试"
            }
            return html(Response.Status.OK, statusPage())
        }
    }

    private fun isAllowedClient(remoteIpAddress: String): Boolean {
        val remote = runCatching { InetAddress.getByName(remoteIpAddress) }.getOrNull() as? Inet4Address ?: return false
        val allowedScope = if (endpoint.address.isLoopbackAddress) remote.isLoopbackAddress else remote.isSiteLocalAddress
        return allowedScope && isSameSubnet(endpoint.address, remote, endpoint.prefixLength)
    }

    private fun formPage(): String = page(
        title = "家庭电视设置",
        body = """
            <h1>家庭电视</h1>
            <p class="muted">修改来源后发送到电视进行测试。</p>
            <form method="post" action="/submit?key=${htmlEscape(sessionKey)}">
              <label for="playlist">频道列表 URL</label>
              <input id="playlist" name="playlist" type="url" maxlength="$MAX_URL_LENGTH" required value="${htmlEscape(currentPlaylistUrl)}">
              <label for="epg">XMLTV URL</label>
              <input id="epg" name="epg" type="url" maxlength="$MAX_URL_LENGTH" value="${htmlEscape(currentEpgUrl)}">
              <button type="submit">发送并测试</button>
            </form>
        """.trimIndent(),
    )

    private fun statusPage(): String {
        val refresh = if (phase == RemoteSetupPhase.TESTING) {
            "<meta http-equiv=\"refresh\" content=\"2;url=/status?key=${htmlEscape(sessionKey)}\">"
        } else {
            ""
        }
        val action = if (phase == RemoteSetupPhase.ERROR) {
            "<p><a href=\"/?key=${htmlEscape(sessionKey)}\">返回修改</a></p>"
        } else {
            ""
        }
        return page(
            title = "电视测试状态",
            head = refresh,
            body = "<h1>${htmlEscape(resultMessage)}</h1><p class=\"muted\">测试通过后，请在电视上确认应用。</p>$action",
        )
    }

    private fun messagePage(title: String, detail: String): String = page(
        title = title,
        body = "<h1>${htmlEscape(title)}</h1><p class=\"muted\">${htmlEscape(detail)}</p>",
    )

    private fun page(title: String, body: String, head: String = ""): String = """
        <!doctype html>
        <html lang="zh-CN">
          <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="no-referrer">
            <title>${htmlEscape(title)}</title>
            $head
            <style>
              :root { color-scheme: dark; font-family: system-ui, sans-serif; background: #0b1012; color: #f4f7f6; }
              body { margin: 0 auto; max-width: 42rem; padding: 2rem 1.25rem; }
              h1 { font-size: 1.65rem; margin: 0 0 1rem; }
              .muted { color: #aeb9b5; line-height: 1.55; }
              label { display: block; margin: 1.25rem 0 .45rem; font-weight: 650; }
              input { box-sizing: border-box; width: 100%; min-height: 3rem; border: 1px solid #64716c; border-radius: 6px; padding: .75rem; background: #151c1f; color: #fff; font-size: 1rem; }
              button { width: 100%; min-height: 3.25rem; margin-top: 1.5rem; border: 0; border-radius: 6px; background: #70e0b6; color: #08110e; font-size: 1rem; font-weight: 750; }
              a { color: #70e0b6; }
            </style>
          </head>
          <body>$body</body>
        </html>
    """.trimIndent()

    private fun html(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, "text/html; charset=utf-8", body).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("X-Frame-Options", "DENY")
        }

    internal companion object {
        const val MAX_BODY_BYTES = 8 * 1024L
        const val MAX_URL_LENGTH = 2_048
        private const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"

        fun htmlEscape(value: String): String = buildString(value.length) {
            value.forEach { char ->
                append(
                    when (char) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&#39;"
                        else -> char
                    },
                )
            }
        }
    }
}

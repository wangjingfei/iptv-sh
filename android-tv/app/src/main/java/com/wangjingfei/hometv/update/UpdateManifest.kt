package com.wangjingfei.hometv.update

import java.net.URI
import org.json.JSONObject

data class UpdateManifest(
    val schemaVersion: Int,
    val versionCode: Long,
    val versionName: String,
    val packageName: String,
    val minSdk: Int,
    val apkUrl: String,
    val apkSha256: String,
    val apkSize: Long,
    val certificateSha256: String,
    val releaseNotes: String,
) {
    init {
        require(schemaVersion == SUPPORTED_SCHEMA) { "不支持的更新清单版本" }
        require(versionCode > 0L) { "更新版本号无效" }
        require(versionName.isNotBlank()) { "更新版本名称为空" }
        require(PACKAGE_PATTERN.matches(packageName)) { "更新包名无效" }
        require(minSdk > 0) { "最低 Android 版本无效" }
        require(apkSize in 1..MAX_APK_BYTES) { "更新包大小无效" }
        require(SHA256_PATTERN.matches(apkSha256)) { "更新包 SHA-256 无效" }
        require(SHA256_PATTERN.matches(certificateSha256)) { "签名证书 SHA-256 无效" }
        val uri = runCatching { URI(apkUrl) }.getOrNull()
        require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
            "更新包必须使用 HTTPS"
        }
    }

    companion object {
        const val SUPPORTED_SCHEMA = 1
        const val MAX_APK_BYTES = 500L * 1024L * 1024L
        private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
        private val PACKAGE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        fun parse(json: String): UpdateManifest {
            val value = JSONObject(json)
            return UpdateManifest(
                schemaVersion = value.getInt("schemaVersion"),
                versionCode = value.getLong("versionCode"),
                versionName = value.getString("versionName").trim(),
                packageName = value.getString("packageName").trim(),
                minSdk = value.getInt("minSdk"),
                apkUrl = value.getString("apkUrl").trim(),
                apkSha256 = value.getString("apkSha256").trim().lowercase(),
                apkSize = value.getLong("apkSize"),
                certificateSha256 = value.getString("certificateSha256").trim().replace(":", "").lowercase(),
                releaseNotes = value.optString("releaseNotes").trim(),
            )
        }
    }
}

package com.wangjingfei.hometv.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wangjingfei.hometv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val updateDirectory = File(context.cacheDir, "updates")
    private val manifestClient = httpClient.newBuilder()
        .followSslRedirects(false)
        .build()
    private val downloadClient = httpClient.newBuilder()
        .followSslRedirects(false)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    suspend fun check(manual: Boolean = false) {
        if (!BuildConfig.AUTO_UPDATE_ENABLED) {
            if (manual) _state.value = UpdateState.Error("开发版不参与正式自动更新，请安装正式版后使用此功能")
            return
        }
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Verifying) return
        val now = System.currentTimeMillis()
        if (!manual && now - preferences.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        _state.value = UpdateState.Checking(manual)
        withContext(Dispatchers.IO) { runCatching { fetchManifest() } }
            .onSuccess { manifest ->
                _state.value = if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                    UpdateState.Available(manifest)
                } else {
                    preferences.edit().putLong(KEY_LAST_CHECK, now).apply()
                    UpdateState.UpToDate(now)
                }
            }
            .onFailure { error ->
                _state.value = if (manual) {
                    UpdateState.Error(error.message ?: "无法检查更新")
                } else {
                    UpdateState.Idle
                }
            }
    }

    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        val available = when (val current = _state.value) {
            is UpdateState.Available -> current.manifest
            is UpdateState.Error -> current.manifest
            else -> null
        } ?: return@withContext false
        updateDirectory.mkdirs()
        val partial = File(updateDirectory, "home-tv-${available.versionCode}.apk.part")
        val completed = File(updateDirectory, "home-tv-${available.versionCode}.apk")
        runCatching {
            partial.delete()
            completed.delete()
            _state.value = UpdateState.Downloading(available, 0f)
            val request = Request.Builder()
                .url(available.apkUrl)
                .header("User-Agent", "HomeTV/${BuildConfig.VERSION_NAME}")
                .build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("下载更新失败：HTTP ${response.code}")
                if (!response.request.url.isHttps) error("更新包重定向到了非 HTTPS 地址")
                val body = response.body ?: error("更新服务器返回了空文件")
                val responseLength = body.contentLength()
                if (responseLength > 0 && responseLength != available.apkSize) error("更新包大小与清单不一致")
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                        var downloaded = 0L
                        var lastProgress = -1f
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            if (downloaded > UpdateManifest.MAX_APK_BYTES || downloaded > available.apkSize) {
                                error("更新包超过清单声明的大小")
                            }
                            output.write(buffer, 0, count)
                            val progress = (downloaded.toFloat() / available.apkSize).coerceIn(0f, 1f)
                            if (progress - lastProgress >= PROGRESS_STEP || progress == 1f) {
                                _state.value = UpdateState.Downloading(available, progress)
                                lastProgress = progress
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (partial.length() != available.apkSize) error("更新包未完整下载")
            _state.value = UpdateState.Verifying(available)
            verifyApk(partial, available)
            check(partial.renameTo(completed)) { "无法保存已验证的更新包" }
            deleteOtherDownloads(completed)
            _state.value = UpdateState.Ready(available, completed)
        }.onFailure { error ->
            partial.delete()
            completed.delete()
            _state.value = UpdateState.Error(error.message ?: "更新失败", available, showPrompt = true)
        }.isSuccess
    }

    fun dismissPrompt() {
        _state.value = when (val current = _state.value) {
            is UpdateState.Available -> current.copy(showPrompt = false)
            is UpdateState.Ready -> current.copy(showPrompt = false)
            is UpdateState.PermissionRequired -> current.copy(showPrompt = false)
            is UpdateState.Error -> current.copy(showPrompt = false)
            else -> current
        }
    }

    fun requiresInstallPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    fun markPermissionRequired() {
        val current = when (val state = _state.value) {
            is UpdateState.Ready -> state
            is UpdateState.Error -> state.apk?.let { UpdateState.Ready(requireNotNull(state.manifest), it) }
            else -> null
        } ?: return
        _state.value = UpdateState.PermissionRequired(current.manifest, current.apk)
    }

    fun markReadyAfterPermission() {
        val current = _state.value as? UpdateState.PermissionRequired ?: return
        _state.value = UpdateState.Ready(current.manifest, current.apk)
    }

    fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    fun reportExternalActionFailure() {
        val manifest = when (val current = _state.value) {
            is UpdateState.Ready -> current.manifest
            is UpdateState.PermissionRequired -> current.manifest
            else -> null
        }
        val apk = when (val current = _state.value) {
            is UpdateState.Ready -> current.apk
            is UpdateState.PermissionRequired -> current.apk
            else -> null
        }
        _state.value = UpdateState.Error(
            "电视系统无法打开安装授权或 APK 安装界面",
            manifest,
            apk,
            showPrompt = true,
        )
    }

    fun installerIntent(): Intent? {
        val ready = when (val current = _state.value) {
            is UpdateState.Ready -> current
            is UpdateState.PermissionRequired -> UpdateState.Ready(current.manifest, current.apk, current.showPrompt)
            is UpdateState.Error -> current.apk?.let { UpdateState.Ready(requireNotNull(current.manifest), it) }
            else -> null
        } ?: return null
        if (!ready.apk.isFile) {
            _state.value = UpdateState.Error("已下载的更新包不存在，请重新下载", ready.manifest)
            return null
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update-files", ready.apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun fetchManifest(): UpdateManifest {
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_MANIFEST_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "HomeTV/${BuildConfig.VERSION_NAME}")
            .build()
        return manifestClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("检查更新失败：HTTP ${response.code}")
            if (!response.request.url.isHttps) error("更新清单重定向到了非 HTTPS 地址")
            val manifest = UpdateManifest.parse(response.body?.string() ?: error("更新清单为空"))
            require(manifest.packageName == context.packageName) { "更新清单的包名与当前应用不一致" }
            require(manifest.minSdk <= Build.VERSION.SDK_INT) { "此更新需要更高版本的 Android" }
            manifest
        }
    }

    private fun verifyApk(file: File, manifest: UpdateManifest) {
        require(sha256(file) == manifest.apkSha256) { "更新包 SHA-256 校验失败" }
        val archive = archivePackageInfo(file) ?: error("无法读取更新包信息")
        require(archive.packageName == context.packageName && archive.packageName == manifest.packageName) {
            "更新包名与当前应用不一致"
        }
        require(archive.longVersionCodeCompat() == manifest.versionCode) { "更新包版本与清单不一致" }
        require(manifest.versionCode > BuildConfig.VERSION_CODE) { "更新版本必须高于当前版本" }
        val candidateCertificates = archive.certificateDigests()
        require(manifest.certificateSha256 in candidateCertificates) { "更新包签名与清单不一致" }
        val installedCertificates = installedPackageInfo().certificateDigests()
        require(candidateCertificates.any(installedCertificates::contains)) { "更新包签名与当前应用不一致" }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(file: File): PackageInfo? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        else -> context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        else -> context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun PackageInfo.certificateDigests(): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptySet()
            info.apkContentsSigners
        } else {
            signatures
        }
        return certificates.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun deleteOtherDownloads(keep: File) {
        updateDirectory.listFiles()?.filter { it != keep }?.forEach(File::delete)
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        const val PROGRESS_STEP = 0.01f
        const val CHECK_INTERVAL_MS = 24 * 60 * 60_000L
        const val KEY_LAST_CHECK = "last_check_at"
    }
}

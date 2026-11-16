package com.wangjingfei.hometv.update

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun parsesAndNormalizesSignedHttpsManifest() {
        val manifest = UpdateManifest.parse(
            """
            {
              "schemaVersion": 1,
              "versionCode": 5,
              "versionName": "0.5.0",
              "packageName": "com.wangjingfei.hometv",
              "minSdk": 23,
              "apkUrl": "https://github.com/wangjingfei/iptv-sh/releases/download/v0.5.0/home-tv-0.5.0.apk",
              "apkSha256": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
              "apkSize": 123456,
              "certificateSha256": "BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB",
              "releaseNotes": "新增自动更新"
            }
            """.trimIndent(),
        )

        assertThat(manifest.versionCode).isEqualTo(5)
        assertThat(manifest.apkSha256).isEqualTo("a".repeat(64))
        assertThat(manifest.certificateSha256).isEqualTo("b".repeat(64))
        assertThat(manifest.releaseNotes).isEqualTo("新增自动更新")
    }

    @Test
    fun rejectsPlainHttpAndInvalidDigest() {
        val invalidJson = """
            {
              "schemaVersion": 1,
              "versionCode": 5,
              "versionName": "0.5.0",
              "packageName": "com.wangjingfei.hometv",
              "minSdk": 23,
              "apkUrl": "http://example.test/home-tv.apk",
              "apkSha256": "not-a-digest",
              "apkSize": 123456,
              "certificateSha256": "${"b".repeat(64)}"
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(invalidJson) }
    }

    @Test
    fun rejectsOversizedPackage() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifest(
                schemaVersion = 1,
                versionCode = 5,
                versionName = "0.5.0",
                packageName = "com.wangjingfei.hometv",
                minSdk = 23,
                apkUrl = "https://example.test/home-tv.apk",
                apkSha256 = "a".repeat(64),
                apkSize = UpdateManifest.MAX_APK_BYTES + 1,
                certificateSha256 = "b".repeat(64),
                releaseNotes = "",
            )
        }
    }

    @Test
    fun resumesInstallerOnlyAfterPendingPermissionIsGranted() {
        val manifest = UpdateManifest(
            schemaVersion = 1,
            versionCode = 5,
            versionName = "0.5.0",
            packageName = "com.wangjingfei.hometv",
            minSdk = 23,
            apkUrl = "https://example.test/home-tv.apk",
            apkSha256 = "a".repeat(64),
            apkSize = 123456,
            certificateSha256 = "b".repeat(64),
            releaseNotes = "",
        )
        val pending = UpdateState.PermissionRequired(manifest, File("home-tv.apk"))

        assertThat(shouldResumePendingInstall(pending, requiresPermission = true)).isFalse()
        assertThat(shouldResumePendingInstall(pending, requiresPermission = false)).isTrue()
        assertThat(shouldResumePendingInstall(UpdateState.Idle, requiresPermission = false)).isFalse()
    }
}

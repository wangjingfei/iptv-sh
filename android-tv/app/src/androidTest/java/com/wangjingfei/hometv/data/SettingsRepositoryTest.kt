package com.wangjingfei.hometv.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Test
    fun clearingEpgMetadataForcesNextDownload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        val url = "http://192.168.31.200:8091/epg.xml"

        settings.setEpgMetadata(url, HttpMetadata(etag = "test-etag", lastModified = "test-date"))
        assertEquals(HttpMetadata("test-etag", "test-date"), settings.epgMetadata(url))

        settings.clearEpgMetadata()

        assertEquals(HttpMetadata(), settings.epgMetadata(url))
    }
}

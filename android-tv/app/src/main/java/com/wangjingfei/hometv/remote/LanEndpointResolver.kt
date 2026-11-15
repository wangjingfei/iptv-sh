package com.wangjingfei.hometv.remote

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanEndpointResolver @Inject constructor(@ApplicationContext context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    fun resolve(): LanEndpoint? {
        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        return linkProperties.linkAddresses
            .asSequence()
            .filter { it.address is Inet4Address }
            .map { LanEndpoint(it.address as Inet4Address, it.prefixLength) }
            .firstOrNull { !it.address.isLoopbackAddress && it.address.isSiteLocalAddress }
    }
}

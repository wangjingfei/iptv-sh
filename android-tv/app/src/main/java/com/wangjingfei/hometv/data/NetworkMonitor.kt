package com.wangjingfei.hometv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun diagnose(): DiagnosticItem {
        val network = connectivity.activeNetwork
            ?: return DiagnosticItem("电视网络", DiagnosticStatus.ERROR, "没有活动网络，请检查 Wi-Fi 或网线")
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: return DiagnosticItem("电视网络", DiagnosticStatus.ERROR, "无法读取当前网络状态")
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线网络"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动网络"
            else -> "其他网络"
        }
        return DiagnosticItem("电视网络", DiagnosticStatus.OK, "$transport 已连接；继续检查局域网服务")
    }
}

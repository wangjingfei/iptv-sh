package com.wangjingfei.hometv.remote

import com.google.common.truth.Truth.assertThat
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Test

class RemoteSetupModelsTest {
    @Test
    fun comparesIpv4SubnetsUsingPrefixLength() {
        val local = ipv4("192.168.31.10")

        assertThat(isSameSubnet(local, ipv4("192.168.31.200"), 24)).isTrue()
        assertThat(isSameSubnet(local, ipv4("192.168.30.200"), 24)).isFalse()
        assertThat(isSameSubnet(ipv4("10.66.66.5"), ipv4("10.66.66.200"), 24)).isTrue()
        assertThat(isSameSubnet(ipv4("172.16.4.1"), ipv4("172.16.7.1"), 22)).isTrue()
    }

    private fun ipv4(value: String): Inet4Address = InetAddress.getByName(value) as Inet4Address
}

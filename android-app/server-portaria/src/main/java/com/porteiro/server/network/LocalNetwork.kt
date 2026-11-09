package com.porteiro.server.network

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalNetwork {
    /** Retorna o IPv4 da rede Wi-Fi/local, ignorando loopback e interfaces virtuais. */
    fun localIpv4(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}

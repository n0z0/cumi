package id.biz.id.cumi.proxy

import java.net.Inet4Address
import java.net.NetworkInterface

enum class InterfaceKind { WIFI, HOTSPOT, USB, BLUETOOTH, ETHERNET, CELLULAR, VPN, OTHER }

data class LanAddress(val kind: InterfaceKind, val interfaceName: String, val address: String)

object NetworkInfo {
    /** Lists IPv4 addresses on active interfaces, most useful ones (Wi-Fi, hotspot) first. */
    fun lanAddresses(): List<LanAddress> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .map { LanAddress(kindOf(nif.name), nif.name, it.hostAddress!!) }
            }
            .distinctBy { it.address }
            .sortedBy { it.kind.ordinal }
    } catch (_: Exception) {
        emptyList()
    }

    private fun kindOf(name: String): InterfaceKind = when {
        name.startsWith("wlan") -> InterfaceKind.WIFI
        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> InterfaceKind.HOTSPOT
        name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("ncm") -> InterfaceKind.USB
        name.startsWith("bt") -> InterfaceKind.BLUETOOTH
        name.startsWith("eth") -> InterfaceKind.ETHERNET
        name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") ||
            name.startsWith("seth") || name.startsWith("v4-") -> InterfaceKind.CELLULAR
        name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("ipsec") -> InterfaceKind.VPN
        else -> InterfaceKind.OTHER
    }
}

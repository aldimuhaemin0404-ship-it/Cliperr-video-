package com.example.data.network

import okhttp3.Dns
import okhttp3.Interceptor
import java.io.IOException
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale

object UrlSecurityValidator {

    private val SCHEME_PREFIX_REGEX = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):.*$""")
    private val IPV4_QUAD_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val NUMERIC_LABEL_REGEX = Regex("""^(0x[0-9a-f]+|0[0-7]*|[0-9]+)$""", RegexOption.IGNORE_CASE)

    private val BLOCKED_HOSTNAMES = setOf(
        "localhost",
        "localhost.localdomain",
        "ip6-localhost",
        "ip6-loopback",
        "broadcasthost",
        "metadata",
        "metadata.google.internal",
        "metadata.goog"
    )

    private val BLOCKED_HOST_SUFFIXES = listOf(
        ".localhost",
        ".local",
        ".internal",
        ".localdomain",
        ".home.arpa",
        ".arpa",
        ".lan",
        ".intranet",
        ".corp"
    )

    fun isSafeExternalHttpUrl(rawInput: String?): Boolean =
        normalizeAndValidateExternalHttpUrl(rawInput) != null

    fun normalizeAndValidateExternalHttpUrl(rawInput: String?): String? {
        val trimmed = rawInput?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) return null

        val schemeMatch = SCHEME_PREFIX_REGEX.matchEntire(trimmed)
        val candidate = if (schemeMatch != null) {
            val scheme = schemeMatch.groupValues[1].lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return null
            trimmed
        } else {
            if (trimmed.startsWith("/") || trimmed.startsWith("\\") || !trimmed.contains('.')) {
                return null
            }
            "https://$trimmed"
        }

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!uri.rawUserInfo.isNullOrEmpty() || !uri.userInfo.isNullOrEmpty()) return null

        val rawHost = uri.host?.trim()?.trim('[', ']') ?: return null
        if (rawHost.isEmpty()) return null
        if (uri.port != -1 && (uri.port < 1 || uri.port > 65535)) return null

        val asciiHost = runCatching { IDN.toASCII(rawHost, IDN.ALLOW_UNASSIGNED) }.getOrNull()
            ?: return null
        if (!isSafeHostname(asciiHost)) return null

        return uri.toASCIIString()
    }

    fun isSafeHostname(rawHost: String): Boolean {
        val host = rawHost.trim().trimEnd('.').trim('[', ']').lowercase(Locale.ROOT)
        if (host.isEmpty()) return false
        if (host.any { it.isWhitespace() || it.code < 0x20 }) return false
        if (host in BLOCKED_HOSTNAMES) return false
        if (BLOCKED_HOST_SUFFIXES.any { host.endsWith(it) }) return false

        // IPv6 literal (contains ':')
        if (host.contains(':')) {
            if (host.contains('%')) return false // Reject scoped IPv6 link-local zone IDs
            val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
            return !isBlockedAddress(addr)
        }

        // Standard dotted-quad IPv4 literal
        val quadMatch = IPV4_QUAD_REGEX.matchEntire(host)
        if (quadMatch != null) {
            val octets = (1..4).map { idx -> quadMatch.groupValues[idx].toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            val bytes = ByteArray(4) { i -> octets[i].toByte() }
            return !isBlockedIpv4Bytes(bytes)
        }

        // Reject obfuscated numeric/octal/hex IPv4 forms (e.g., 2130706433, 0x7f.1, 0177.0.0.1)
        val labels = host.split('.')
        if (labels.any { it.isEmpty() }) return false
        val lastLabel = labels.last()
        if (NUMERIC_LABEL_REGEX.matches(lastLabel)) return false
        if (labels.all { NUMERIC_LABEL_REGEX.matches(it) }) return false

        return true
    }

    fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }

        return when (address) {
            is Inet4Address -> isBlockedIpv4Bytes(address.address)
            is Inet6Address -> isBlockedIpv6Bytes(address.address)
            else -> true
        }
    }

    private fun isBlockedIpv4Bytes(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return true
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF

        return when {
            b0 == 0 -> true // 0.0.0.0/8 ("this network")
            b0 == 10 -> true // 10.0.0.0/8 (RFC1918)
            b0 == 100 && (b1 and 0xC0) == 64 -> true // 100.64.0.0/10 (CGNAT / RFC6598)
            b0 == 127 -> true // 127.0.0.0/8 (loopback)
            b0 == 169 && b1 == 254 -> true // 169.254.0.0/16 (link-local / cloud metadata)
            b0 == 172 && b1 in 16..31 -> true // 172.16.0.0/12 (RFC1918)
            b0 == 192 && b1 == 0 && b2 == 0 -> true // 192.0.0.0/24 (IETF protocol assignments)
            b0 == 192 && b1 == 0 && b2 == 2 -> true // 192.0.2.0/24 (TEST-NET-1)
            b0 == 192 && b1 == 168 -> true // 192.168.0.0/16 (RFC1918)
            b0 == 198 && b1 in 18..19 -> true // 198.18.0.0/15 (benchmarking)
            b0 == 198 && b1 == 51 && b2 == 100 -> true // 198.51.100.0/24 (TEST-NET-2)
            b0 == 203 && b1 == 0 && b2 == 113 -> true // 203.0.113.0/24 (TEST-NET-3)
            b0 >= 224 -> true // Multicast (224.0.0.0/4) + reserved/broadcast (240.0.0.0/4)
            else -> false
        }
    }

    private fun isBlockedIpv6Bytes(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return true
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF

        // Unique Local Addresses (fc00::/7)
        if ((b0 and 0xFE) == 0xFC) return true
        // Link-local unicast (fe80::/10)
        if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
        // Multicast (ff00::/8)
        if (b0 == 0xFF) return true

        // IPv4-mapped IPv6 (::ffff:0:0/96) or IPv4-compatible IPv6 (::0:0/96)
        val prefixZero = (0..9).all { bytes[it].toInt() == 0 }
        val isIpv4Mapped = prefixZero &&
            (bytes[10].toInt() and 0xFF) == 0xFF &&
            (bytes[11].toInt() and 0xFF) == 0xFF
        val isIpv4Compat = prefixZero && bytes[10].toInt() == 0 && bytes[11].toInt() == 0
        if (isIpv4Mapped || isIpv4Compat) {
            val allZero = (12..15).all { bytes[it].toInt() == 0 }
            val isLoopbackV6 = (12..14).all { bytes[it].toInt() == 0 } && (bytes[15].toInt() and 0xFF) == 1
            if (allZero || isLoopbackV6) return true
            if (isIpv4Mapped) {
                return isBlockedIpv4Bytes(bytes.copyOfRange(12, 16))
            }
        }
        return false
    }

    val safeDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (!isSafeHostname(hostname)) {
                throw UnknownHostException("Akses ke host lokal/internal diblokir: $hostname")
            }
            val resolved = Dns.SYSTEM.lookup(hostname)
            if (resolved.isEmpty() || resolved.any { isBlockedAddress(it) }) {
                throw UnknownHostException("Akses ke alamat jaringan internal/privat diblokir.")
            }
            return resolved
        }
    }

    val safeApplicationInterceptor: Interceptor = Interceptor { chain ->
        val req = chain.request()
        if (!isSafeExternalHttpUrl(req.url.toString())) {
            throw IOException("URL tidak valid atau mengarah ke jaringan lokal/internal yang diblokir.")
        }
        chain.proceed(req)
    }

    val safeNetworkInterceptor: Interceptor = Interceptor { chain ->
        val req = chain.request()
        if (!isSafeExternalHttpUrl(req.url.toString())) {
            throw IOException("Redirect ke alamat tidak valid atau jaringan internal diblokir.")
        }
        val remoteAddress = chain.connection()?.socket()?.inetAddress
        if (remoteAddress != null && isBlockedAddress(remoteAddress)) {
            throw IOException("Koneksi ke alamat IP internal/privat diblokir.")
        }
        chain.proceed(req)
    }
}

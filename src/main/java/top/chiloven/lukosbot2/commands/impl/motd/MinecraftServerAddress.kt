package top.chiloven.lukosbot2.commands.impl.motd

/**
 * Parsed Minecraft server address.
 *
 * Supports the common forms used by Java Edition server addresses:
 * - `example.com`
 * - `example.com:25565`
 * - `[2001:db8::1]`
 * - `[2001:db8::1]:25565`
 * - `2001:db8::1` (IPv6 literal without port)
 */
@ConsistentCopyVisibility
data class MinecraftServerAddress private constructor(
    val host: String,
    val port: Int?,
    val rawInput: String,
) {

    val hasExplicitPort: Boolean
        get() = port != null

    fun normalized(defaultPort: Int? = null): String =
        format(host, port ?: defaultPort)

    fun apiAddress(defaultPort: Int? = null): String = normalized(defaultPort)

    fun hostForDisplay(): String = displayHost(host)

    fun socketHost(): String = host

    fun socketPort(defaultPort: Int): Int = port ?: defaultPort

    fun looksLikeLiteralIp(): Boolean =
        host.contains(':') || IPV4_LITERAL.matches(host)

    fun looksLikeDomainName(): Boolean =
        !looksLikeLiteralIp() && host.contains('.')

    companion object {

        private val BRACKETED_IPV6 = Regex("^\\[(.+)](?::(\\d{1,5}))?$")
        private val HOST_WITH_OPTIONAL_PORT = Regex("^([^:\\s]+)(?::(\\d{1,5}))?$")
        private val IPV4_LITERAL = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")

        @JvmStatic
        fun parse(raw: String): MinecraftServerAddress {
            val trimmed = raw.trim()
                .removePrefix("minecraft://")
                .removePrefix("mc://")
                .trim()
                .trimEnd('/')

            require(trimmed.isNotBlank()) { "Address can not be empty" }
            require(trimmed.none { it.isWhitespace() }) { "Incorrect address format: cannot include white spaces" }

            BRACKETED_IPV6.matchEntire(trimmed)?.let { match ->
                val host = match.groupValues[1]
                val port = parsePort(match.groupValues.getOrNull(2))
                return MinecraftServerAddress(host = host, port = port, rawInput = raw)
            }

            if (trimmed.count { it == ':' } > 1) {
                return MinecraftServerAddress(host = trimmed, port = null, rawInput = raw)
            }

            HOST_WITH_OPTIONAL_PORT.matchEntire(trimmed)?.let { match ->
                val host = match.groupValues[1]
                val port = parsePort(match.groupValues.getOrNull(2))
                return MinecraftServerAddress(host = host, port = port, rawInput = raw)
            }

            throw IllegalArgumentException("Incorrect address format")
        }

        @JvmStatic
        fun format(host: String, port: Int?): String {
            val displayHost = displayHost(host)
            return if (port != null) "$displayHost:$port" else displayHost
        }

        private fun displayHost(host: String): String =
            if (host.contains(':') && !host.startsWith("[") && !host.endsWith("]")) {
                "[$host]"
            } else {
                host
            }

        private fun parsePort(rawPort: String?): Int? {
            if (rawPort.isNullOrBlank()) return null
            val port = rawPort.toIntOrNull() ?: throw IllegalArgumentException("Incorrect port format")
            require(port in 1..65535) { "The port should be between 1 and 65535" }
            return port
        }

    }

}

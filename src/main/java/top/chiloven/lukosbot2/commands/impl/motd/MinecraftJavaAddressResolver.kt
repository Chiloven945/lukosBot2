package top.chiloven.lukosbot2.commands.impl.motd

import org.apache.logging.log4j.LogManager
import java.util.*
import javax.naming.Context
import javax.naming.directory.InitialDirContext

/**
 * Resolves candidate Java Edition endpoints for a user-provided server address.
 *
 * The primary goal is to mimic the address forms that the Minecraft client accepts:
 * when the user does not specify a port and the host looks like a domain, a
 * `_minecraft._tcp.<host>` SRV lookup is attempted before falling back to the
 * default `host:25565` endpoint.
 */
object MinecraftJavaAddressResolver {

    private const val DEFAULT_MC_PORT = 25565
    private const val SRV_INITIAL_TIMEOUT_MS = "2000"
    private const val SRV_RETRIES = "1"

    private val log = LogManager.getLogger(MinecraftJavaAddressResolver::class.java)

    data class Endpoint(
        val host: String,
        val port: Int,
        val viaSrv: Boolean,
        val srvName: String? = null,
    ) {

        fun displayAddress(): String = MinecraftServerAddress.format(host, port)

    }

    fun resolveCandidates(address: MinecraftServerAddress): List<Endpoint> {
        val direct = Endpoint(
            host = address.socketHost(),
            port = address.socketPort(DEFAULT_MC_PORT),
            viaSrv = false,
        )

        if (address.hasExplicitPort || !address.looksLikeDomainName()) {
            return listOf(direct)
        }

        val endpoints = linkedSetOf<Endpoint>()
        resolveSrv(address.host)?.let { srv ->
            endpoints += Endpoint(
                host = srv.target,
                port = srv.port,
                viaSrv = true,
                srvName = "_minecraft._tcp.${address.host}",
            )
        }
        endpoints += direct
        return endpoints.toList()
    }

    private fun resolveSrv(host: String): SrvRecord? {
        val lookupName = "_minecraft._tcp.$host"
        val env = Hashtable<String, String>().apply {
            put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory")
            put("com.sun.jndi.dns.timeout.initial", SRV_INITIAL_TIMEOUT_MS)
            put("com.sun.jndi.dns.timeout.retries", SRV_RETRIES)
        }

        return runCatching {
            val ctx = InitialDirContext(env)
            try {
                val attr = ctx.getAttributes(lookupName, arrayOf("SRV")).get("SRV") ?: return@runCatching null
                val records = (0 until attr.size())
                    .mapNotNull { index -> attr.get(index)?.toString()?.let(::parseSrvRecord) }
                    .sortedWith(compareBy<SrvRecord> { it.priority }.thenByDescending { it.weight })
                records.firstOrNull()
            } finally {
                runCatching { ctx.close() }
            }
        }.onFailure { ex ->
            log.debug("Unable to resolve SRV for {}: {}", host, ex.message)
        }.getOrNull()
    }

    private fun parseSrvRecord(raw: String): SrvRecord? {
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 4) return null

        val priority = parts[0].toIntOrNull() ?: return null
        val weight = parts[1].toIntOrNull() ?: return null
        val port = parts[2].toIntOrNull() ?: return null
        val target = parts.drop(3).joinToString(" ")
            .trim()
            .removeSuffix(".")
            .takeIf { it.isNotBlank() && it != "." }
            ?: return null

        if (port !in 1..65535) return null
        return SrvRecord(priority, weight, port, target)
    }

    private data class SrvRecord(
        val priority: Int,
        val weight: Int,
        val port: Int,
        val target: String,
    )

}

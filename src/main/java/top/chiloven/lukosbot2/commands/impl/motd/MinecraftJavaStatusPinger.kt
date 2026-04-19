package top.chiloven.lukosbot2.commands.impl.motd

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.JsonUtils
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Direct Java Edition status ping implementation.
 */
object MinecraftJavaStatusPinger {

    private const val DEFAULT_PROTOCOL_VERSION = 760

    data class Status(
        val version: String,
        val protocol: Int,
        val maxPlayers: Int,
        val onlinePlayers: Int,
        val description: String,
        val favicon: String?,
        val remoteIp: String?,
    )

    @JvmStatic
    @Throws(IOException::class)
    fun ping(
        address: MinecraftServerAddress,
        proxyConfigProp: ProxyConfigProp? = null,
        timeoutMs: Int = 7_000,
    ): Status = ping(
        endpoint = MinecraftJavaAddressResolver.Endpoint(
            host = address.socketHost(),
            port = address.socketPort(25565),
            viaSrv = false,
        ),
        proxyConfigProp = proxyConfigProp,
        timeoutMs = timeoutMs,
    )

    @JvmStatic
    @Throws(IOException::class)
    fun ping(
        endpoint: MinecraftJavaAddressResolver.Endpoint,
        proxyConfigProp: ProxyConfigProp? = null,
        timeoutMs: Int = 7_000,
    ): Status {
        val javaProxy = proxyConfigProp
            ?.toJavaProxy()
            ?.takeIf { it != Proxy.NO_PROXY && it.type() == Proxy.Type.SOCKS }
            ?: Proxy.NO_PROXY

        val socket = if (javaProxy == Proxy.NO_PROXY) Socket() else Socket(javaProxy)
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMs)

        socket.use { connected ->
            DataOutputStream(connected.getOutputStream()).use { output ->
                DataInputStream(connected.getInputStream()).use { input ->
                    writeHandshake(output, endpoint.host, endpoint.port)
                    writeStatusRequest(output)

                    readVarInt(input)
                    val packetId = readVarInt(input)
                    if (packetId != 0x00) {
                        throw IOException("Invalid packet id (status): $packetId")
                    }

                    val jsonLength = readVarInt(input)
                    if (jsonLength <= 0) {
                        throw IOException("Invalid JSON length: $jsonLength")
                    }

                    val jsonBytes = ByteArray(jsonLength)
                    input.readFully(jsonBytes)
                    val json = String(jsonBytes, StandardCharsets.UTF_8)

                    writePing(output)
                    readVarInt(input)
                    val pongPacketId = readVarInt(input)
                    if (pongPacketId != 0x01) {
                        throw IOException("Invalid packet id (pong): $pongPacketId")
                    }
                    input.readLong()

                    return parse(json, connected.inetAddress?.hostAddress)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun writeHandshake(output: DataOutputStream, host: String, port: Int) {
        val handshakeBytes = ByteArrayOutputStream()
        DataOutputStream(handshakeBytes).use { handshake ->
            handshake.writeByte(0x00)
            writeVarInt(handshake, DEFAULT_PROTOCOL_VERSION)

            val hostBytes = host.toByteArray(StandardCharsets.UTF_8)
            writeVarInt(handshake, hostBytes.size)
            handshake.write(hostBytes)
            handshake.writeShort(port)
            writeVarInt(handshake, 1)
        }

        writeVarInt(output, handshakeBytes.size())
        output.write(handshakeBytes.toByteArray())
        output.flush()
    }

    @Throws(IOException::class)
    private fun writeStatusRequest(output: DataOutputStream) {
        output.writeByte(0x01)
        output.writeByte(0x00)
        output.flush()
    }

    @Throws(IOException::class)
    private fun writePing(output: DataOutputStream) {
        output.writeByte(0x09)
        output.writeByte(0x01)
        output.writeLong(System.currentTimeMillis())
        output.flush()
    }

    private fun parse(json: String, remoteIp: String?): Status {
        val root = JsonUtils.MAPPER.readTree(json).asObjectOpt().orElseThrow {
            IOException("The status response is not a JSON object")
        }

        val versionObj = root.pathObject("version")
        val playersObj = root.pathObject("players")

        val version = versionObj?.get("name")?.takeIf { !it.isNull }?.asString()?.takeIf { it.isNotBlank() }
            ?: "Unknown"
        val protocol = versionObj?.get("protocol")?.takeIf { !it.isNull }?.asInt() ?: -1
        val maxPlayers = playersObj?.get("max")?.takeIf { !it.isNull }?.asInt() ?: 0
        val onlinePlayers = playersObj?.get("online")?.takeIf { !it.isNull }?.asInt() ?: 0
        val description = parseDescription(root.get("description")).ifBlank { "A Minecraft Server" }
        val favicon = root.get("favicon")?.takeIf { !it.isNull }?.asString()?.takeIf { it.isNotBlank() }

        return Status(
            version = version,
            protocol = protocol,
            maxPlayers = maxPlayers,
            onlinePlayers = onlinePlayers,
            description = description,
            favicon = favicon,
            remoteIp = remoteIp,
        )
    }

    private fun parseDescription(node: JsonNode?): String {
        if (node == null || node.isNull) return ""
        if (node.isValueNode) return sanitizeMinecraftText(node.asString())

        val out = StringBuilder()
        appendDescription(node, out)
        return sanitizeMinecraftText(out.toString())
    }

    private fun appendDescription(node: JsonNode?, out: StringBuilder) {
        if (node == null || node.isNull) return

        when {
            node.isValueNode -> out.append(node.asString())
            node.isArray -> node.forEach { appendDescription(it, out) }
            node.isObject -> {
                node.get("text")?.takeIf { it.isValueNode }?.asString()?.let(out::append)
                node.get("extra")?.takeIf { it.isArray }?.forEach { appendDescription(it, out) }
            }
        }
    }

    private fun sanitizeMinecraftText(text: String): String = text
        .replace(Regex("§."), "")
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trim()

    @Throws(IOException::class)
    private fun writeVarInt(output: DataOutputStream, value: Int) {
        var mutable = value
        while ((mutable and 0xFFFFFF80.toInt()) != 0) {
            output.writeByte((mutable and 0x7F) or 0x80)
            mutable = mutable ushr 7
        }
        output.writeByte(mutable and 0x7F)
    }

    @Throws(IOException::class)
    private fun readVarInt(input: DataInputStream): Int {
        var numRead = 0
        var result = 0
        var read: Int
        do {
            read = input.readUnsignedByte()
            val value = read and 0x7F
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > 5) {
                throw IOException("VarInt too long")
            }
        } while ((read and 0x80) != 0)
        return result
    }

    private fun ObjectNode.pathObject(field: String): ObjectNode? =
        get(field)?.asObjectOpt()?.orElse(null)

}

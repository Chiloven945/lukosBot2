package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.config.ProxyConfigProp;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;
import top.chiloven.lukosbot2.util.spring.SpringBeans;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static top.chiloven.lukosbot2.util.JsonUtils.MAPPER;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "motd",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class MotdCommand implements IBotCommand {

    // TODO: refactor like other api-based commands

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            sendUsage(ctx.getSource());
                            return 0;
                        })
                        .then(argument("address", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CommandSource src = ctx.getSource();
                                    String address = StringArgumentType.getString(ctx, "address");

                                    try {
                                        MotdData data = query(address);

                                        src.reply(data.formatted());

                                        String favicon = data.favicon();
                                        if (favicon != null && !favicon.isBlank()
                                                && favicon.startsWith("data:image/png;base64,")) {

                                            String base64 = favicon.substring("data:image/png;base64,".length());
                                            byte[] bytes = Base64.getDecoder().decode(base64);

                                            src.reply(OutboundMessage.imageBytesPng(
                                                    src.addr(),
                                                    bytes,
                                                    "favicon.png"
                                            ));
                                        }

                                    } catch (IllegalArgumentException e) {
                                        src.reply(e.getMessage());
                                    } catch (Exception e) {
                                        log.warn("Unable to get MOTD for address: {}", address, e);
                                        src.reply("获取 MOTD 失败：" + e.getMessage());
                                    }

                                    return 0;
                                })
                        )
        );
    }

    @Override
    public String name() {
        return "motd";
    }

    @Override
    public String description() {
        return "获取 Minecraft MOTD 信息";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("查询 Minecraft Java 版服务器 MOTD", UsageNode.arg("address[:port]"))
                .param("address[:port]", "服务器地址（可选端口，默认 25565）")
                .example(
                        "motd play.example.com",
                        "motd play.example.com:25565"
                )
                .build();
    }

    private MotdData query(String address) throws IOException {
        Matcher matcher = Pattern
                .compile("^\\s*([^\\s:]+)(?::(\\d+))?\\s*$")
                .matcher(address);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("地址格式不正确");
        }

        String host = matcher.group(1);
        int port = (matcher.group(2) == null) ? 25565 : Integer.parseInt(matcher.group(2));

        return ping(host, port);
    }

    /**
     * Ping Minecraft server and get MOTD data.
     *
     * @param host the host name of the server
     * @param port the port to query
     * @return a MotdData stored with server information
     * @throws IOException if a network error occurs
     * @since Minecraft 1.7
     */
    private MotdData ping(String host, int port) throws IOException {
        ProxyConfigProp proxyConfigProp = SpringBeans.getBean(ProxyConfigProp.class);
        Proxy javaProxy = proxyConfigProp.toJavaProxy();
        if (javaProxy == null || javaProxy == Proxy.NO_PROXY || javaProxy.type() != Proxy.Type.SOCKS) {
            javaProxy = Proxy.NO_PROXY;
        }

        Socket socket = (javaProxy == Proxy.NO_PROXY) ? new Socket() : new Socket(javaProxy);
        int timeout = 7000;

        socket.setSoTimeout(timeout);
        socket.connect(new InetSocketAddress(host, port), timeout);

        try (socket;
             OutputStream outputStream = socket.getOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
             InputStream inputStream = socket.getInputStream();
             DataInputStream dataInputStream = new DataInputStream(inputStream)) {

            // === Handshake ===
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(b);

            handshake.writeByte(0x00);
            writeVarInt(handshake, 4);
            writeVarInt(handshake, host.length());
            handshake.writeBytes(host);
            handshake.writeShort(port);
            writeVarInt(handshake, 1);

            writeVarInt(dataOutputStream, b.size());
            dataOutputStream.write(b.toByteArray());

            // === Status Request ===
            dataOutputStream.writeByte(0x01);
            dataOutputStream.writeByte(0x00);
            dataOutputStream.flush();

            // === Status Response ===
            int size = readVarInt(dataInputStream);
            int id = readVarInt(dataInputStream);

            if (id != 0x00) {
                throw new IOException("Invalid packetID (status), got " + id);
            }

            int length = readVarInt(dataInputStream);
            if (length <= 0) {
                throw new IOException("Invalid JSON string length: " + length);
            }

            byte[] in = new byte[length];
            dataInputStream.readFully(in);
            String json = new String(in, StandardCharsets.UTF_8);

            dataOutputStream.writeByte(0x09);
            dataOutputStream.writeByte(0x01);
            dataOutputStream.writeLong(System.currentTimeMillis());
            dataOutputStream.flush();

            readVarInt(dataInputStream);
            id = readVarInt(dataInputStream);
            if (id != 0x01) {
                throw new IOException("Invalid packetID (pong), got " + id);
            }

            return parseMotd(json);
        }
    }

    private MotdData parseMotd(String json) {
        ObjectNode root = MAPPER.readTree(json).asObject();

        JsonNode versionNode = root.get("version");
        ObjectNode versionObj = versionNode == null ? null : versionNode.asObjectOpt().orElse(null);
        String version = versionObj != null && versionObj.has("name")
                ? versionObj.get("name").asString()
                : "Unknown";

        int protocol = versionObj != null && versionObj.has("protocol")
                ? versionObj.get("protocol").asInt()
                : -1;

        JsonNode playersNode = root.get("players");
        ObjectNode playersObj = playersNode == null ? null : playersNode.asObjectOpt().orElse(null);
        int max = playersObj != null && playersObj.has("max")
                ? playersObj.get("max").asInt()
                : 0;
        int online = playersObj != null && playersObj.has("online")
                ? playersObj.get("online").asInt()
                : 0;

        String desc = parseDescription(root.get("description"));

        String favicon = root.has("favicon") && !root.get("favicon").isNull()
                ? root.get("favicon").asString()
                : null;

        return new MotdData(version, protocol, max, online, desc, favicon);
    }

    private String parseDescription(JsonNode el) {
        if (el == null || el.isNull()) return "A Minecraft Server";

        String raw;
        if (el.isValueNode()) {
            raw = el.asString();
        } else if (el.isObject()) {
            ObjectNode obj = el.asObject();
            if (obj.has("text") && obj.get("text").isValueNode()) {
                raw = obj.get("text").asString();
            } else {
                raw = obj.toString();
            }
        } else {
            raw = el.toString();
        }

        return Pattern.compile("§.")
                .matcher(raw)
                .replaceAll("");
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) {
                throw new IOException("VarInt 太长");
            }
        } while ((read & 0x80) != 0);
        return result;
    }

    public record MotdData(
            String version,
            int protocol,
            int max,
            int online,
            String desc,
            String favicon
    ) {

        public String formatted() {
            return String.format("""
                            描述：
                            %s
                            版本：%s（%d）
                            玩家：%d/%d
                            """,
                    (desc != null && !desc.isBlank()) ? desc : "A Minecraft Server",
                    version,
                    protocol,
                    online,
                    max
            );
        }

    }

}

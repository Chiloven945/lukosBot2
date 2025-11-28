package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.config.ProxyConfig;
import chiloven.lukosbot2.core.CommandSource;
import chiloven.lukosbot2.support.SpringBeans;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MotdCommand implements BotCommand {
    private static final Logger log = LogManager.getLogger(MotdCommand.class);

    @Override
    public String name() {
        return "motd";
    }

    @Override
    public String description() {
        return "获取 Minecraft MOTD 信息";
    }

    @Override
    public String usage() {
        return """
                用法：
                /motd <address[:port]>
                示例：
                /motd play.example.com
                /motd play.example.com:25565
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 0;
                        })
                        .then(RequiredArgumentBuilder
                                .<CommandSource, String>argument("address", StringArgumentType.greedyString())
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

                                            src.replyImageBytes("favicon.png", bytes, "image/png");
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
        ProxyConfig proxyConfig = SpringBeans.getBean(ProxyConfig.class);
        Proxy javaProxy = proxyConfig.toJavaProxy();
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
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        JsonObject versionObj = root.getAsJsonObject("version");
        String version = versionObj != null && versionObj.has("name")
                ? versionObj.get("name").getAsString()
                : "Unknown";

        int protocol = versionObj != null && versionObj.has("protocol")
                ? versionObj.get("protocol").getAsInt()
                : -1;

        JsonObject playersObj = root.getAsJsonObject("players");
        int max = playersObj != null && playersObj.has("max")
                ? playersObj.get("max").getAsInt()
                : 0;
        int online = playersObj != null && playersObj.has("online")
                ? playersObj.get("online").getAsInt()
                : 0;

        String desc = parseDescription(root.get("description"));

        String favicon = root.has("favicon") && !root.get("favicon").isJsonNull()
                ? root.get("favicon").getAsString()
                : null;

        return new MotdData(version, protocol, max, online, desc, favicon);
    }

    private String parseDescription(JsonElement el) {
        if (el == null || el.isJsonNull()) return "A Minecraft Server";

        String raw;
        if (el.isJsonPrimitive()) {
            raw = el.getAsString();
        } else if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
                raw = obj.get("text").getAsString();
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

package top.chiloven.lukosbot2.util.feature;

import top.chiloven.lukosbot2.util.Base64Utils;
import top.chiloven.lukosbot2.util.HttpJson;
import top.chiloven.lukosbot2.util.JsonUtils;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

public final class MojangApi {
    public static final HttpJson hj = HttpJson.getHttpJson();
    public static final JsonUtils ju = JsonUtils.getJsonUtils();
    public static final Base64Utils b64 = Base64Utils.getBase64Utils();

    public String getUuidFromName(String name) {
        try {
            return hj.get(
                    "https://api.mojang.com/users/profiles/minecraft/" + name,
                    null,
                    6000,
                    10000
            ).get("id").getAsString();
        } catch (IOException e) {
            return null;
        }
    }

    public String getNameFromUuid(String uuid) {
        try {
            return hj.get(
                    "https://api.minecraftservices.com/minecraft/profile/lookup/" + uuid,
                    null,
                    6000,
                    10000
            ).get("name").getAsString();
        } catch (IOException e) {
            return null;
        }
    }

    public McPlayer getMcPlayerInfo(String data) {
        String uuid = (data.length() <= 16) ? getUuidFromName(data) : data;

        try {
            JsonObject info = hj.get(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid,
                    null,
                    6000,
                    10000
            );

            JsonObject value = b64.decodeToJsonObject(
                    ju.getStringByPath(info, "properties[0].value", "")
            );

            return new McPlayer(
                    ju.getString(info, "name", ""),
                    ju.getString(info, "id", ""),
                    ju.getStringByPath(value, "textures.SKIN.url", null),
                    ju.getStringByPath(value, "textures.CAPE.url", null)
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record McPlayer(
            String name,
            String uuid,
            String skin,
            String cape
    ) {
        @Override
        public @NonNull String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("玩家名：").append(name).append("\n");
            sb.append("UUID：").append(uuid).append("\n");
            if (skin != null) {
                sb.append("皮肤：").append(skin).append("\n");
            }
            if (cape != null) {
                sb.append("披风：").append(cape).append("\n");
            }
            return sb.toString();
        }
    }
}

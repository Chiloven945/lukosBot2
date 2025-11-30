package chiloven.lukosbot2.util.feature;

import chiloven.lukosbot2.util.Base64Utils;
import chiloven.lukosbot2.util.HttpJson;
import chiloven.lukosbot2.util.JsonUtils;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

public final class MojangApi {
    public static final HttpJson HJ = new HttpJson();
    public static final JsonUtils JU = new JsonUtils();
    public static final Base64Utils B64 = new Base64Utils();

    public String getUuidFromName(String name) {
        try {
            return HJ.get(
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
            return HJ.get(
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
            JsonObject info = HJ.get(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid,
                    null,
                    6000,
                    10000
            );

            JsonObject value = B64.decodeToJsonObject(
                    JU.getStringByPath(info, "properties[0].value", "")
            );

            return new McPlayer(
                    JU.getString(info, "name", ""),
                    JU.getString(info, "id", ""),
                    JU.getStringByPath(value, "textures.SKIN.url", null),
                    JU.getStringByPath(value, "textures.CAPE.url", null)
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

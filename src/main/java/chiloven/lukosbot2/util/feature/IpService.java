package chiloven.lukosbot2.util.feature;

import chiloven.lukosbot2.util.HttpJson;
import chiloven.lukosbot2.util.JsonUtils;
import chiloven.lukosbot2.util.SafeStringBuilder;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

public final class IpService {
    public static final HttpJson HJ = new HttpJson();
    public static final JsonUtils JU = new JsonUtils();

    public IpService() {
    }

    public IpInfo getIpInfo(String ip) throws IOException {
        try {
            JsonObject result = HJ.get(
                    "https://api.ip.sb/geoip/" + ip,
                    null,
                    6000,
                    10000
            );

            return new IpInfo(
                    JU.getString(result, "ip", null),
                    JU.getString(result, "country", null),
                    JU.getString(result, "country_code", null),
                    JU.getString(result, "region", null),
                    JU.getString(result, "region_code", null),
                    JU.getString(result, "city", null),
                    JU.getString(result, "postal_code", null),
                    JU.getString(result, "latitude", null),
                    JU.getString(result, "longitude", null),
                    JU.getString(result, "organization", null),
                    JU.getString(result, "timezone", null),
                    JU.getString(result, "asn", null)
            );
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    public record IpInfo(
            String ip,
            String country,
            String countryCode,
            String region,
            String regionCode,
            String city,
            String postalCode,
            String latitude,
            String longitude,
            String org,
            String timezone,
            String asn
    ) {
        @Override
        public @NonNull String toString() {
            SafeStringBuilder sb = new SafeStringBuilder();

            sb.append("IP 地址 - ").append(ip).ln()
                    .add("国家/地区：{0}{1?}\n", country,
                            countryCode == null ? null : " (" + countryCode + ")")
                    .add("区域：{0}{1?}\n", region,
                            regionCode == null ? null : " (" + regionCode + ")")
                    .add("城市：{0}{1?}\n", city,
                            postalCode == null ? null : " (" + postalCode + ")")
                    .add("位置：{0}, {1}\n", latitude, longitude)
                    .add("时区：{0}\n", timezone)
                    .add("组织：{0}\n", org)
                    .add("ASN：{0}", asn);

            return sb.toString();
        }
    }
}

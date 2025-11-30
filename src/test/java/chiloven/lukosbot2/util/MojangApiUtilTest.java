package chiloven.lukosbot2.util;

import chiloven.lukosbot2.util.feature.MojangApi;

public class MojangApiUtilTest {
    public static final MojangApi MAPI = new MojangApi();

    static void main() {
        String uuid = MAPI.getUuidFromName("Chiloven945");
        IO.println(uuid);
        IO.println(MAPI.getMcPlayerInfo(uuid));
    }
}

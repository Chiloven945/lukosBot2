package chiloven.lukosbot2.util;

public class MojangApiUtilTest {
    public static final MojangApi MAPI = new MojangApi();

    static void main() {
        String uuid = MAPI.getUuidFromName("Chiloven945");
        IO.println(uuid);
        IO.println(MAPI.getMcPlayerInfo(uuid));
    }
}

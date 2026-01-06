package chiloven.lukosbot2;

import org.springframework.core.SpringVersion;

/**
 * Constants class for holding application-wide constants.
 */
public class Constants {
    public static final String VERSION = "Alpha.0.0.1";
    public static final String APP_NAME = "lukosBot2";
    public final String javaVersion = System.getProperty("java.version");
    public final String springVersion = SpringVersion.getVersion();
    public final String tgVersion = "9.2.1";
    public final String jdaVersion = getImplVersion("net.dv8tion.jda.api.JDA");
    public final String shiroVersion = "2.5.0";

    public Constants() {
    }

    private String getImplVersion(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Package p = clazz.getPackage();
            if (p != null && p.getImplementationVersion() != null) {
                return p.getImplementationVersion();
            }
        } catch (ClassNotFoundException ignored) {
        }
        return "unknown";
    }
}

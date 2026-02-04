package top.chiloven.lukosbot2;

import java.util.Optional;
import java.util.Properties;

/**
 * Constants class for holding application-wide constants.
 */
public class Constants {
    public static final String VERSION = "0.1.0-SNAPSHOT";
    public static final String APP_NAME = "lukosBot2";

    public final String javaVersion =
            "%s (%s)".formatted(System.getProperty("java.version"), System.getProperty("java.vendor.version"));
    public final String springBootVersion =
            getImplVersion("org.springframework.boot.SpringApplication");
    public final String tgVersion =
            getMavenVersion("org.telegram", "telegrambots-client");
    public final String jdaVersion =
            getImplVersion("net.dv8tion.jda.api.JDA");
    public final String shiroVersion =
            getImplVersion("com.mikuac.shiro.boot.Shiro");

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

    private String getMavenVersion(String groupId, String artifactId) {
        String path = String.format("META-INF/maven/%s/%s/pom.properties", groupId, artifactId);

        return Optional.ofNullable(Thread.currentThread().getContextClassLoader().getResourceAsStream(path))
                .map(in -> {
                    try (in) {
                        Properties p = new Properties();
                        p.load(in);
                        return p.getProperty("version");
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(version -> !version.isBlank())
                .orElse("unknown");
    }
}

package chiloven.lukosbot2.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.List;

/**
 * Unified proxy configuration (SOCKS5 / HTTPS CONNECT).<br>
 * Read lukos.proxy from application.yml and on startup:<br>
 * 1. Set JVM system properties (overriding most libraries).<br>
 * 2. Provide {@link #applyTo(OkHttpClient.Builder)}, {@link #toSeleniumProxy()},
 * and {@link #chromiumProxyArg()} for libraries that require explicit injection.<br>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "lukos.proxy")
public class ProxyConfig {

    ///  Whether to enable the proxy, default is false.
    private boolean enabled = false;
    /// The type of the proxy. <code>SOCKS</code>, <code>HTTPS</code>, and <code>NONE</code> is acceptable.
    private String type = "NONE";
    /// The host of the proxy to connect.
    private String host;
    /// The port of that proxy is on the host.
    private int port;

    /// (Optional) The username to access the proxy.
    private String username;
    /// (Optional) The password of the user.
    private String password;

    /// The list of hosts to bypass proxy.
    private List<String> nonProxyHostsList;

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String printableHost(InetSocketAddress addr) {
        String host = addr.getHostString();
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    // ===== Apply to JVM (ASAP) =====
    @PostConstruct
    public void applyJvmWide() {
        if (!enabled) return;

        // 1) nonProxyHostsList 允许为 null，安全合并
        String result = (nonProxyHostsList == null || nonProxyHostsList.isEmpty())
                ? ""
                : String.join("|", nonProxyHostsList);
        if (!result.isBlank()) {
            System.setProperty("http.nonProxyHosts", result);
            System.setProperty("socksNonProxyHosts", result);
        }

        // 2) 基本健壮性：host/port 未配置就直接返回（避免 NPE/无意义设置）
        if (host == null || host.isBlank() || port <= 0) {
            // 可选：换成你的日志体系
            System.err.println("[ProxyConfig] proxy enabled but host/port is invalid, skip applying JVM proxy: host=" + host + ", port=" + port);
            return;
        }

        // 3) 设置系统代理
        String t = (type == null) ? "NONE" : type.trim().toUpperCase();
        switch (t) {
            case "HTTPS" -> {
                System.setProperty("http.proxyHost", host);
                System.setProperty("http.proxyPort", String.valueOf(port));
                System.setProperty("https.proxyHost", host);
                System.setProperty("https.proxyPort", String.valueOf(port));
            }
            case "SOCKS5" -> {
                System.setProperty("socksProxyHost", host);
                System.setProperty("socksProxyPort", String.valueOf(port));
                System.setProperty("socksProxyVersion", "5");
                if (notBlank(username)) {
                    Authenticator.setDefault(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, (password == null ? "" : password).toCharArray());
                        }
                    });
                }
            }
        }
    }

    // ===== OkHttp / JDA =====
    public void applyTo(OkHttpClient.Builder b) {
        if (!enabled) return;

        Proxy javaProxy = toJavaProxy();
        if (javaProxy != Proxy.NO_PROXY) {
            b.proxy(javaProxy);
        }
        if (notBlank(username) && isHttps()) {
            String cred = Credentials.basic(username, password == null ? "" : password);
            b.proxyAuthenticator((route, response) -> response.request().newBuilder()
                    .header("Proxy-Authorization", cred)
                    .build());
        }
    }

    // ===== Selenium / Chromium =====

    /**
     * Return an argument like <code>--proxy-server=socks5://host:port</code> or <code>http://host:port</code> (supported by Chromium).
     */
    public String chromiumProxyArg() {
        if (!enabled) return null;
        Proxy p = toJavaProxy();
        if (p == Proxy.NO_PROXY) return null;
        if (!(p.address() instanceof InetSocketAddress addr)) return null;
        String scheme = (p.type() == Proxy.Type.SOCKS) ? "socks5" : "http";
        return "--proxy-server=" + scheme + "://" + printableHost(addr) + ":" + addr.getPort();
    }

    /**
     * Return a Selenium Proxy object (recognized by Edge/Chrome).
     */
    public org.openqa.selenium.Proxy toSeleniumProxy() {
        if (!enabled) return null;
        java.net.Proxy p = toJavaProxy();
        if (p == java.net.Proxy.NO_PROXY) return null;
        if (!(p.address() instanceof InetSocketAddress addr)) return null;

        String hostPort = printableHost(addr) + ":" + addr.getPort();
        org.openqa.selenium.Proxy sp = new org.openqa.selenium.Proxy(); // ← 显式写全名
        if (p.type() == java.net.Proxy.Type.SOCKS) {
            sp.setSocksProxy(hostPort);
            sp.setSocksVersion(5);
        } else {
            sp.setHttpProxy(hostPort);
            sp.setSslProxy(hostPort);
        }
        return sp;
    }

    /**
     * A standard Java Proxy (for libraries that need an explicitly injected Proxy).
     */
    public java.net.Proxy toJavaProxy() {
        if (!enabled) return Proxy.NO_PROXY;
        if (isSocks()) {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
        } else if (isHttps()) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        }
        return Proxy.NO_PROXY;
    }

    // ===== Helpers =====
    private boolean isSocks() {
        return "SOCKS5".equalsIgnoreCase(type);
    }

    private boolean isHttps() {
        return "HTTPS".equalsIgnoreCase(type) || "HTTP".equalsIgnoreCase(type);
    }

}

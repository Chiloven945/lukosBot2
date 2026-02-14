package top.chiloven.lukosbot2.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.*;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Objects;

/**
 * Unified proxy configuration (SOCKS5 / HTTPS CONNECT).<br>
 * Read lukos.proxy from application.yml and on startup:<br>
 * 1. Set JVM system properties (overriding most libraries).<br>
 * 2. Provide {@link #applyTo(OkHttpClient.Builder)}, {@link #toSeleniumProxy()},
 * and {@link #chromiumProxyArg()} for libraries that require explicit injection.<br>
 */
@Log4j2
@Data
@Configuration
@ConfigurationProperties(prefix = "lukos.proxy")
public class ProxyConfigProp {

    /* TODO: refactor this Proxy abomination fully, supports SOCKS and HTTPS simultaneously because something
         requires SOCKS while something requires HTTPS (and this implementation is garbage >:( )
         (fuck you git i have to write this again) */

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

        String result = (nonProxyHostsList == null || nonProxyHostsList.isEmpty())
                ? ""
                : String.join("|", nonProxyHostsList);
        if (!result.isBlank()) {
            System.setProperty("http.nonProxyHosts", result);
            System.setProperty("socksNonProxyHosts", result);
        }

        if (host == null || host.isBlank() || port <= 0) {
            log.error("[ProxyConfig] proxy enabled but host/port is invalid, skip applying JVM proxy: host={}, port={}", host, port);
            return;
        }

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

    // ===== JDK / HttpClient =====
    public HttpClient.Builder applyTo(HttpClient.Builder b) {
        HttpClient.Builder builder = (b != null) ? b : HttpClient.newBuilder();
        if (!isEnabled()) return builder;

        Proxy p = toJavaProxy();
        if (p != null && p != Proxy.NO_PROXY) {
            builder.proxy(ProxySelector.of((InetSocketAddress) p.address()));
        }

        String user = getUsername();
        if (user != null && !user.isBlank()) {
            String pass = Objects.requireNonNullElse(getPassword(), "");
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass.toCharArray());
                }
            });
        }

        return builder;
    }

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
        org.openqa.selenium.Proxy sp = new org.openqa.selenium.Proxy();
        if (p.type() == java.net.Proxy.Type.SOCKS) {
            sp.setSocksProxy(hostPort);
            sp.setSocksVersion(5);
        } else {
            sp.setHttpProxy(hostPort);
            sp.setSslProxy(hostPort);
        }
        return sp;
    }

    public java.net.Proxy toJavaProxy() {
        if (!enabled || type == null) return Proxy.NO_PROXY;

        InetSocketAddress address = new InetSocketAddress(host, port);

        return switch (type.toUpperCase()) {
            case "SOCKS5", "SOCKS" -> new Proxy(Proxy.Type.SOCKS, address);
            case "HTTPS", "HTTP" -> new Proxy(Proxy.Type.HTTP, address);
            case "NONE" -> Proxy.NO_PROXY;
            default -> {
                log.warn("[ProxyConfig] Unknown proxy type: {}. You might enter a wrong name! Available types are 'SOCKS5', 'HTTPS', and 'NONE'.", type);
                yield Proxy.NO_PROXY;
            }
        };
    }

    private boolean isHttps() {
        return "HTTPS".equalsIgnoreCase(type) || "HTTP".equalsIgnoreCase(type);
    }
}

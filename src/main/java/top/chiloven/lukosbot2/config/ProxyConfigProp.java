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
 * Proxy configuration for the application.
 *
 * <p>Properties are read from {@code lukos.proxy.*} (application.yml / application.properties).</p>
 *
 * <h2>Supported proxy types</h2>
 * <ul>
 *   <li>{@code NONE} - disable proxy</li>
 *   <li>{@code HTTP}/{@code HTTPS} - HTTP proxy (HTTPS is treated as HTTP CONNECT)</li>
 *   <li>{@code SOCKS}/{@code SOCKS5} - SOCKS5 proxy</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <ol>
 *   <li>On startup, {@link #applyJvmWide()} optionally sets JVM-wide proxy system properties.</li>
 *   <li>{@link #applyTo(OkHttpClient.Builder)} applies proxy settings explicitly to OkHttp.</li>
 *   <li>{@link #applyTo(HttpClient.Builder)} applies proxy settings explicitly to JDK HttpClient.</li>
 *   <li>{@link #toSeleniumProxy()} / {@link #chromiumProxyArg()} provide helper outputs for browsers.</li>
 * </ol>
 *
 * <p><b>Note about SOCKS authentication</b>:
 * SOCKS username/password (RFC 1929) is not directly supported by OkHttp.
 * If you configure SOCKS credentials here, this class will install a JVM {@link Authenticator}
 * (global) in {@link #applyJvmWide()} as a best-effort approach.</p>
 *
 * <p><b>TODO</b>: Allowing two proxies simultaneously (e.g., one SOCKS + one HTTP). This class currently models a single proxy endpoint at a time.</p>
 */
@Log4j2
@Data
@Configuration
@ConfigurationProperties(prefix = "lukos.proxy")
public class ProxyConfigProp {

    private boolean enabled = false;
    private String type = "NONE";
    private String host;
    private int port;
    private String username;
    private String password;
    private List<String> nonProxyHostsList;

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String printableHost(InetSocketAddress addr) {
        String h = addr.getHostString();
        return h.contains(":") && !h.startsWith("[") ? "[" + h + "]" : h;
    }

    private NormalizedType normalizedType() {
        String t = (type == null) ? "NONE" : type.trim().toUpperCase();
        return switch (t) {
            case "HTTP", "HTTPS" -> NormalizedType.HTTP;
            case "SOCKS", "SOCKS5" -> NormalizedType.SOCKS5;
            case "NONE" -> NormalizedType.NONE;
            default -> {
                log.warn("[ProxyConfig] Unknown proxy type: {} (expected NONE/HTTP/HTTPS/SOCKS/SOCKS5). Using NONE.", type);
                yield NormalizedType.NONE;
            }
        };
    }

    private boolean hasValidEndpoint() {
        return enabled && notBlank(host) && port > 0 && normalizedType() != NormalizedType.NONE;
    }

    private InetSocketAddress toInetSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    /**
     * Apply proxy configuration JVM-wide ASAP.
     *
     * <p>This sets standard system properties for HTTP/HTTPS or SOCKS proxies.
     * It may affect libraries that read JVM proxy properties implicitly.</p>
     */
    @PostConstruct
    public void applyJvmWide() {
        if (!enabled) return;

        String bypass = (nonProxyHostsList == null || nonProxyHostsList.isEmpty())
                ? ""
                : String.join("|", nonProxyHostsList);
        if (!bypass.isBlank()) {
            System.setProperty("http.nonProxyHosts", bypass);
            System.setProperty("socksNonProxyHosts", bypass);
        }

        if (!hasValidEndpoint()) {
            if (normalizedType() != NormalizedType.NONE) {
                log.error("[ProxyConfig] proxy enabled but host/port is invalid, skip applying JVM proxy: host={}, port={}", host, port);
            }
            return;
        }

        InetSocketAddress addr = toInetSocketAddress();
        switch (normalizedType()) {
            case HTTP -> {
                System.setProperty("http.proxyHost", host);
                System.setProperty("http.proxyPort", String.valueOf(port));
                System.setProperty("https.proxyHost", host);
                System.setProperty("https.proxyPort", String.valueOf(port));
            }
            case SOCKS5 -> {
                System.setProperty("socksProxyHost", host);
                System.setProperty("socksProxyPort", String.valueOf(port));
                System.setProperty("socksProxyVersion", "5");

                // Best-effort: install JVM Authenticator for SOCKS auth (global).
                if (notBlank(username)) {
                    final String u = username;
                    final String p = Objects.requireNonNullElse(password, "");
                    Authenticator.setDefault(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(u, p.toCharArray());
                        }
                    });
                }
            }
            case NONE -> {
            }
        }

        log.info("[ProxyConfig] Applied JVM proxy: type={}, host={}, port={}", normalizedType(), addr.getHostString(), addr.getPort());
    }

    /**
     * Apply proxy configuration to OkHttp.
     *
     * <p>HTTP proxy authentication will be set via {@code Proxy-Authorization} header.
     * For SOCKS proxy, OkHttp does not support username/password directly; see class note.</p>
     *
     * @param builder OkHttp client builder to apply settings to
     */
    public void applyTo(OkHttpClient.Builder builder) {
        if (builder == null) return;
        if (!hasValidEndpoint()) return;

        Proxy proxy = toJavaProxy();
        if (proxy != null && proxy != Proxy.NO_PROXY) {
            builder.proxy(proxy);
        }

        if (normalizedType() == NormalizedType.HTTP && notBlank(username)) {
            String cred = Credentials.basic(username, Objects.requireNonNullElse(password, ""));
            builder.proxyAuthenticator((route, response) -> response.request().newBuilder()
                    .header("Proxy-Authorization", cred)
                    .build());
        }

        // SOCKS auth: best-effort only; recommend JVM-wide authenticator
        if (normalizedType() == NormalizedType.SOCKS5 && notBlank(username)) {
            log.warn("[ProxyConfig] SOCKS username/password is best-effort only for OkHttp. Consider relying on JVM Authenticator (applyJvmWide).");
        }
    }

    /**
     * Apply proxy configuration to JDK {@link HttpClient}.
     *
     * @param b existing builder (nullable)
     * @return builder with proxy settings applied
     */
    public HttpClient.Builder applyTo(HttpClient.Builder b) {
        HttpClient.Builder builder = (b != null) ? b : HttpClient.newBuilder();
        if (!hasValidEndpoint()) return builder;

        Proxy p = toJavaProxy();
        if (p != null && p != Proxy.NO_PROXY && p.address() instanceof InetSocketAddress addr) {
            builder.proxy(ProxySelector.of(addr));
        }

        if (notBlank(username)) {
            String u = username;
            String p0 = Objects.requireNonNullElse(password, "");
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(u, p0.toCharArray());
                }
            });
        }

        return builder;
    }

    /**
     * Chromium proxy argument.
     *
     * @return argument string like {@code --proxy-server=socks5://host:port} or {@code --proxy-server=http://host:port},
     * or {@code null} if proxy is disabled / invalid.
     */
    public String chromiumProxyArg() {
        if (!hasValidEndpoint()) return null;

        Proxy p = toJavaProxy();
        if (p == Proxy.NO_PROXY) return null;
        if (!(p.address() instanceof InetSocketAddress addr)) return null;

        String scheme = (p.type() == Proxy.Type.SOCKS) ? "socks5" : "http";
        return "--proxy-server=" + scheme + "://" + printableHost(addr) + ":" + addr.getPort();
    }

    /**
     * Selenium proxy object for Edge/Chrome.
     *
     * @return Selenium {@link org.openqa.selenium.Proxy} or {@code null} if proxy disabled/invalid
     */
    public org.openqa.selenium.Proxy toSeleniumProxy() {
        if (!hasValidEndpoint()) return null;

        Proxy p = toJavaProxy();
        if (p == Proxy.NO_PROXY) return null;
        if (!(p.address() instanceof InetSocketAddress addr)) return null;

        String hostPort = printableHost(addr) + ":" + addr.getPort();
        org.openqa.selenium.Proxy sp = new org.openqa.selenium.Proxy();

        if (p.type() == Proxy.Type.SOCKS) {
            sp.setSocksProxy(hostPort);
            sp.setSocksVersion(5);
        } else {
            sp.setHttpProxy(hostPort);
            sp.setSslProxy(hostPort);
        }
        return sp;
    }

    /**
     * Convert configuration to {@link java.net.Proxy}.
     *
     * @return {@link Proxy#NO_PROXY} if disabled/invalid/none
     */
    public Proxy toJavaProxy() {
        if (!hasValidEndpoint()) return Proxy.NO_PROXY;

        InetSocketAddress address = toInetSocketAddress();
        return switch (normalizedType()) {
            case SOCKS5 -> new Proxy(Proxy.Type.SOCKS, address);
            case HTTP -> new Proxy(Proxy.Type.HTTP, address);
            case NONE -> Proxy.NO_PROXY;
        };
    }

    private enum NormalizedType {
        NONE, HTTP, SOCKS5
    }
}

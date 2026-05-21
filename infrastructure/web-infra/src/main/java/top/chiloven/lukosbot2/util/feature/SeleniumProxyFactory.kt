package top.chiloven.lukosbot2.util.feature

import org.openqa.selenium.Proxy
import top.chiloven.lukosbot2.config.ProxyConfigProp
import java.net.InetSocketAddress
import java.net.Proxy as JavaProxy

object SeleniumProxyFactory {

    /**
     * Chromium proxy argument from [ProxyConfigProp].
     *
     * @return argument string like `--proxy-server=socks5://host:port` or
     * `--proxy-server=http://host:port`, or `null` if proxy is disabled / invalid.
     */
    @JvmStatic
    fun chromiumProxyArg(cfg: ProxyConfigProp): String? {
        if (!cfg.hasValidEndpoint()) return null

        val proxy = cfg.toJavaProxy()
        if (proxy == JavaProxy.NO_PROXY) return null

        val addr = proxy.address() as? InetSocketAddress ?: return null
        val scheme = if (proxy.type() == JavaProxy.Type.SOCKS) "socks5" else "http"
        return "--proxy-server=$scheme://${printableHost(addr)}:${addr.port}"
    }

    /**
     * Selenium [Proxy] object for Edge/Chrome from [ProxyConfigProp].
     *
     * @return Selenium proxy or `null` if proxy disabled/invalid.
     */
    @JvmStatic
    fun toSeleniumProxy(cfg: ProxyConfigProp): Proxy? {
        if (!cfg.hasValidEndpoint()) return null

        val proxy = cfg.toJavaProxy()
        if (proxy == JavaProxy.NO_PROXY) return null

        val addr = proxy.address() as? InetSocketAddress ?: return null
        val hostPort = "${printableHost(addr)}:${addr.port}"
        val sp = Proxy()

        if (proxy.type() == JavaProxy.Type.SOCKS) {
            sp.setSocksProxy(hostPort)
            sp.setSocksVersion(5)
        } else {
            sp.setHttpProxy(hostPort)
            sp.setSslProxy(hostPort)
        }

        return sp
    }

    private fun printableHost(addr: InetSocketAddress): String {
        val h = addr.hostString
        return if (h.contains(":") && !h.startsWith("[")) "[$h]" else h
    }

}

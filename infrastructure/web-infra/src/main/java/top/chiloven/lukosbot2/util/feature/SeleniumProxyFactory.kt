/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
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

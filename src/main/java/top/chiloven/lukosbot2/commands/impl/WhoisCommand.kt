package top.chiloven.lukosbot2.commands.impl

import jakarta.annotation.PostConstruct
import org.apache.commons.net.whois.WhoisClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.definition.dsl.arg
import top.chiloven.lukosbot2.commands.definition.dsl.botCommand
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["whois"],
    havingValue = "true",
    matchIfMissing = true
)
class WhoisCommand : IBotCommand {

    private val whoisServerMap = mutableMapOf<String, String>()

    @PostConstruct
    private fun initWhoisServers() {
        whoisServerMap["com"] = WhoisClient.DEFAULT_HOST
        whoisServerMap["net"] = WhoisClient.DEFAULT_HOST
        whoisServerMap["edu"] = WhoisClient.DEFAULT_HOST
        whoisServerMap["cn"] = "whois.cnnic.cn"
        whoisServerMap["中国"] = "whois.cnnic.cn"
    }

    override fun definition() = botCommand("whois") {
        description = "查询域名 Whois 信息"

        raw("domain", required = true) { domain ->
            source.reply(runQuery(domain))
        }

        syntax("查询域名 Whois 信息", arg("domain"))
        param("domain", "域名，例如 example.com")

        example(
            "whois example.com",
            "whois google.com"
        )
    }

    fun runQuery(domain: String): String {
        val wc = WhoisClient()
        return try {
            wc.connect(chooseWhoisHost(domain))
            val raw = wc.query(domain)
            cleanWhois(raw)
        } catch (e: IOException) {
            "查询 Whois 信息失败：${e.message}"
        } finally {
            try {
                if (wc.isConnected) wc.disconnect()
            } catch (_: IOException) {
            }
        }
    }

    fun cleanWhois(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val boilerplateStart = Pattern.compile(
            "(?im)^For more information on Whois status codes.*$|(?im)^NOTICE:.*$|(?im)^TERMS OF USE:.*$|(?im)^WHOIS Terms of Use.*$|(?im)^>>> Last update of whois database:.*$"
        )
        val m = boilerplateStart.matcher(raw)

        var cutPos = -1
        if (m.find()) cutPos = m.start()
        var cleaned = if (cutPos >= 0) raw.substring(0, cutPos) else raw

        cleaned = cleaned.replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("(?m)^[^\\n:]+:\\s*$\\n?"), "")
        cleaned = compressDuplicateKeyLines(cleaned)
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")
            .replace(Regex("^\\s+|\\s+$"), "")

        return cleaned
    }

    private fun compressDuplicateKeyLines(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        val kvPattern = Pattern.compile("^\\s*([^:\\n]+)\\s*:\\s*(.+)\\s*$")
        val valuesByKey = LinkedHashMap<String, StringBuilder>()
        val order = ArrayList<Any>()
        val seen = HashSet<String>()

        for (line in text.split(Regex("\n"))) {
            val m = kvPattern.matcher(line)
            if (m.matches()) {
                val key = m.group(1).trim()
                val value = m.group(2).trim()

                if (value.isEmpty()) {
                    order.add(line)
                    continue
                }

                if (key !in seen) {
                    seen.add(key)
                    valuesByKey[key] = StringBuilder(value)
                    order.add(key)
                } else {
                    valuesByKey[key]?.append(", ")?.append(value)
                }
            } else order.add(line)
        }

        return buildString {
            order.forEachIndexed { i, item ->
                if (item is String && item in valuesByKey) {
                    append(item).append(": ").append(valuesByKey[item])
                } else append(item.toString())

                if (i < order.size - 1) append('\n')
            }
        }
    }

    fun chooseWhoisHost(domain: String?): String {
        if (domain == null) return WhoisClient.DEFAULT_HOST
        var d = domain.trim().lowercase(Locale.ROOT)
        if (d.endsWith(".")) d = d.substring(0, d.length - 1)
        val tld = d.substringAfterLast('.', "")
        if (tld.isEmpty()) return WhoisClient.DEFAULT_HOST
        return whoisServerMap[tld] ?: lookupWhoisFromIana(tld) ?: WhoisClient.DEFAULT_HOST
    }

    private fun lookupWhoisFromIana(tld: String): String? {
        val wc = WhoisClient()
        return try {
            wc.connect("whois.iana.org")
            val res = wc.query(tld)
            val m = Pattern.compile("(?im)^whois:\\s*(\\S+)").matcher(res)
            if (m.find()) m.group(1).trim() else null
        } catch (_: IOException) {
            null
        } finally {
            try {
                if (wc.isConnected) wc.disconnect()
            } catch (_: IOException) {
            }
        }
    }

}

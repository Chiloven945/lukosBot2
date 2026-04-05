package top.chiloven.lukosbot2.util

import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.model.message.Address
import java.io.InputStreamReader
import java.net.JarURLConnection
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarFile

object I18n {

    private const val BASE = "i18n.messages"

    private val langResolverRef =
        AtomicReference<((Address?, Long?) -> String?)?>(null)

    private val bundleCache = ConcurrentHashMap<Locale, ResourceBundle>()

    @JvmStatic
    fun installLangResolver(resolver: (Address?, Long?) -> String?) {
        langResolverRef.set(resolver)
    }

    @JvmStatic
    fun getLang(addr: Address?, userId: Long?): Locale {
        val tag = langResolverRef.get()?.invoke(addr, userId)
        return locale(tag)
    }

    @JvmStatic
    fun get(src: CommandSource, key: String, vararg args: Any?): String {
        val loc = getLang(
            src.addr(),
            src.userIdOrNull()
        )
        return get(loc, key, *args)
    }

    @JvmStatic
    fun get(langTag: String?, key: String, vararg args: Any?): String =
        get(locale(langTag), key, *args)

    @JvmStatic
    fun locale(tag: String?): Locale {
        val t = tag?.trim().orEmpty()
        if (t.isEmpty()) return Locale.SIMPLIFIED_CHINESE
        return Locale.forLanguageTag(t)
    }

    private val UTF8_CONTROL = object : ResourceBundle.Control() {
        override fun newBundle(
            baseName: String,
            locale: Locale,
            format: String,
            loader: ClassLoader,
            reload: Boolean
        ): ResourceBundle? {
            val bundleName = toBundleName(baseName, locale)
            val resourceName = toResourceName(bundleName, "properties")
            val stream = loader.getResourceAsStream(resourceName) ?: return null
            stream.use { s ->
                InputStreamReader(s, Charsets.UTF_8).use { r ->
                    return PropertyResourceBundle(r)
                }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun get(locale: Locale? = null, key: String, vararg args: Any?): String {
        val loc = locale ?: Locale.SIMPLIFIED_CHINESE
        val bundle = bundleCache.computeIfAbsent(loc) {
            try {
                ResourceBundle.getBundle(BASE, it, UTF8_CONTROL)
            } catch (_: MissingResourceException) {
                ResourceBundle.getBundle(BASE, Locale.SIMPLIFIED_CHINESE, UTF8_CONTROL)
            }
        }

        val pattern = try {
            bundle.getString(key)
        } catch (_: MissingResourceException) {
            "!!$key!!"
        }

        return if (args.isEmpty()) pattern else MessageFormat(pattern, loc).format(args)
    }

    @JvmStatic
    fun supportLanguages(): List<String> {
        val folder = "language"
        val prefix = "language_"
        val suffix = ".properties"

        val resource = Thread.currentThread().contextClassLoader.getResource(folder)
            ?: return emptyList()

        return if (resource.protocol == "jar") {
            val connection = resource.openConnection() as JarURLConnection
            val jarFile: JarFile = connection.jarFile

            jarFile.entries().asSequence()
                .filter { it.name.startsWith("$folder/$prefix") && it.name.endsWith(suffix) }
                .map { it.name.substringAfter(prefix).removeSuffix(suffix) }
                .toList()
        } else {
            java.io.File(resource.toURI()).listFiles()
                ?.map { it.name.removePrefix(prefix).removeSuffix(suffix) } ?: emptyList()
        }.sorted()
    }

}

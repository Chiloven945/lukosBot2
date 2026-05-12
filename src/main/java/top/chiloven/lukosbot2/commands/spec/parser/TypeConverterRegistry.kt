package top.chiloven.lukosbot2.commands.spec.parser

import top.chiloven.lukosbot2.commands.spec.ArgType
import top.chiloven.lukosbot2.commands.spec.CommandParseException
import kotlin.reflect.KClass

class TypeConverterRegistry {

    private val converters = mutableMapOf<KClass<*>, (String) -> Any>()

    fun <T : Any> register(type: KClass<T>, converter: (String) -> T) {
        @Suppress("UNCHECKED_CAST")
        converters[type] = converter as (String) -> Any
    }

    fun convert(type: ArgType, raw: String): Any {
        return when (type) {
            ArgType.StringType -> raw
            ArgType.IntType -> raw.toInt()
            ArgType.LongType -> raw.toLong()
            ArgType.BooleanType -> raw.toBooleanStrict()
            is ArgType.EnumType -> {
                if (raw !in type.values) {
                    throw CommandParseException("参数值无效：$raw，可选值：${type.values.joinToString(", ")}")
                }
                raw
            }

            is ArgType.CustomType<*> -> {
                val converter = converters[type.klass]
                    ?: throw IllegalStateException("未注册的类型转换器：${type.klass}")
                converter(raw)
            }
        }
    }

    companion object {

        val default = TypeConverterRegistry()

    }

}

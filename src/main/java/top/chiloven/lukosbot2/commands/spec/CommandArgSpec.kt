package top.chiloven.lukosbot2.commands.spec

import kotlin.reflect.KClass

sealed interface ArgType {

    data object StringType : ArgType

    data object IntType : ArgType

    data object LongType : ArgType

    data object BooleanType : ArgType

    data class EnumType(
        val values: List<String>
    ) : ArgType

    data class CustomType<T : Any>(
        val klass: KClass<T>
    ) : ArgType

}

fun interface ValueValidator {

    fun validate(value: Any?): String?

}

data class CommandArgSpec(
    val name: String,
    val type: ArgType,
    val required: Boolean = true,
    val defaultValue: Any? = null,
    val greedy: Boolean = false,
    val description: String = "",
    val choices: List<String> = emptyList(),
    val validator: ValueValidator? = null
)

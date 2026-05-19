package top.chiloven.lukosbot2.core.command.definition.dsl

import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.ValueValidator

class OptionConfigBuilder(val canonicalName: String) {

    var names: List<String> = listOf("--$canonicalName")
    var type: ArgType = ArgType.StringType
    var required: Boolean = false
    var default: Any? = null
    var repeatable: Boolean = false
    var splitBy: String? = null
    var description: String = ""
    var choices: List<String> = emptyList()
    var validator: ValueValidator? = null

}

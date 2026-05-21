package top.chiloven.lukosbot2.core.command.definition.dsl

import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.ValueValidator

class PositionalConfigBuilder(val type: ArgType) {

    var required: Boolean = true
    var default: Any? = null
    var greedy: Boolean = false
    var description: String = ""
    var choices: List<String> = emptyList()
    var validator: ValueValidator? = null

}

package top.chiloven.lukosbot2.config

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.core.state.StateService
import top.chiloven.lukosbot2.core.state.definition.LangState
import top.chiloven.lukosbot2.util.I18n

@Component
class I18nWiring(
    private val states: StateService,
    private val langDef: LangState
) {

    @PostConstruct
    fun init() {
        I18n.installLangResolver { addr, userId ->
            if (addr == null) return@installLangResolver langDef.defaultValue()
            states.resolve(langDef, addr, userId)
        }
    }

}

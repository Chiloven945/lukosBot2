package top.chiloven.lukosbot2.util.spring

import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

/**
 * Utility class to access Spring beans statically.
 */
@Component
object SpringBeans : ApplicationContextAware {
    @Volatile
    private var ctx: ApplicationContext? = null

    /**
     * Set the ApplicationContext for this object.
     *
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws BeansException if the context could not be set
     */
    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        ctx = applicationContext
    }

    /**
     * Get a Spring bean by its class type.
     *
     * @param type   the class type of the bean
     * @param T the type of the bean
     * @return the Spring bean instance
     */
    @JvmStatic
    fun <T : Any> getBean(type: Class<T>): T =
        requireNotNull(ctx) { "Spring ApplicationContext has not been initialized yet." }
            .getBean(type)

    /**
     * Get a Spring bean by its class type.
     *
     * @param T the type of the bean
     * @return the Spring bean instance
     */
    inline fun <reified T : Any> getBean(): T = getBean(T::class.java)
}

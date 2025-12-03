package chiloven.lukosbot2.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Utility class to access Spring beans statically.
 */
@Component
public final class SpringBeans implements ApplicationContextAware {
    private static ApplicationContext CTX;

    /**
     * Get a Spring bean by its class type.
     *
     * @param t   the class type of the bean
     * @param <T> the type of the bean
     * @return the Spring bean instance
     */
    public static <T> T getBean(Class<T> t) {
        return CTX.getBean(t);
    }

    /**
     * Set the ApplicationContext for this object.
     *
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws BeansException if the context could not be set
     */
    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        CTX = applicationContext;
    }
}

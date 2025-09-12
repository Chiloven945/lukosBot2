package chiloven.lukosbot2.support;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public final class SpringBeans implements ApplicationContextAware {
    private static ApplicationContext CTX;

    public static <T> T getBean(Class<T> t) {
        return CTX.getBean(t);
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        CTX = applicationContext;
    }
}

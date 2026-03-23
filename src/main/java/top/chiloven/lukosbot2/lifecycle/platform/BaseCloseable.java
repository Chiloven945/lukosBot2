package top.chiloven.lukosbot2.lifecycle.platform;

import java.util.ArrayList;
import java.util.List;

public final class BaseCloseable implements AutoCloseable {

    private final List<AutoCloseable> list = new ArrayList<>();

    /**
     * Add a closeable resource to be closed when this is closed.
     *
     * @param c the closeable resource
     * @return this instance for chaining
     */
    public BaseCloseable add(AutoCloseable c) {
        if (c != null && c != this) list.add(c);
        return this;
    }

    /**
     * Add multiple closeable resources to be closed when this is closed.
     *
     * @param cs the list of closeable resources
     * @return this instance for chaining
     */
    public BaseCloseable addAll(List<? extends AutoCloseable> cs) {
        if (cs != null) {
            cs.stream()
                    .filter(c -> c != null && c != this)
                    .forEach(list::add);
        }
        return this;
    }

    @Override
    public void close() {
        for (int i = list.size() - 1; i >= 0; i--) {
            try {
                list.get(i).close();
            } catch (Exception _) {
            }
        }
    }

}

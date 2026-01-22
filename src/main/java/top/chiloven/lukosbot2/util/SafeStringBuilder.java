package top.chiloven.lukosbot2.util;

public final class SafeStringBuilder {
    private final StringBuilder sb = new StringBuilder();

    /**
     * Add a string. The whole string will not be added if one of the parameters is null.
     *
     * @param template the string template
     * @param args     parameters
     * @return SafeStringBuilder itself
     */
    public SafeStringBuilder add(String template, Object... args) {
        for (int i = 0; i < args.length; i++) {
            String must = "{" + i + "}";
            if (template.contains(must) && args[i] == null) {
                return this;
            }
        }

        String result = template;
        for (int i = 0; i < args.length; i++) {
            String optional = "{" + i + "?}";
            if (result.contains(optional)) {
                if (args[i] == null) {
                    result = result.replace(optional, "");
                } else {
                    result = result.replace(optional, args[i].toString());
                }
            }
        }

        for (int i = 0; i < args.length; i++) {
            String must = "{" + i + "}";
            if (result.contains(must)) {
                result = result.replace(must, args[i].toString());
            }
        }

        sb.append(result);
        return this;
    }

    /**
     * Original StringBuilder style add.
     *
     * @param obj object to be appended
     * @return SafeStringBuilder itself
     */
    public SafeStringBuilder append(Object obj) {
        sb.append(obj);
        return this;
    }

    /**
     * Add a line.
     *
     * @return SafeStringBuilder itself
     */
    public SafeStringBuilder ln() {
        sb.append('\n');
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    /**
     * Empty the StringBuilder.
     *
     * @return SafeStringBuilder itself
     */
    public SafeStringBuilder clear() {
        sb.setLength(0);
        return this;
    }
}

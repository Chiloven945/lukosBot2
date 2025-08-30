package chiloven.lukosbot2.bootstrap;

/**
 * 启动阶段错误（携带退出码）
 */
public final class BootStepError extends RuntimeException {
    private final int code;

    public BootStepError(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int code() {
        return code;
    }
}

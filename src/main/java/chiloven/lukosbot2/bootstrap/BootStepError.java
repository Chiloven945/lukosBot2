package chiloven.lukosbot2.bootstrap;

public final class BootStepError extends RuntimeException {
    private final int code;

    /**
     * Exception during bootstrap step
     *
     * @param code error code
     * @param message error message
     * @param cause the cause of the error
     */
    public BootStepError(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int code() {
        return code;
    }
}

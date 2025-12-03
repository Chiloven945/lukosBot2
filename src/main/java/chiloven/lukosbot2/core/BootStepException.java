package chiloven.lukosbot2.core;

public class BootStepException extends RuntimeException {
    private final int code;

    /**
     * Exception during bootstrap step
     *
     * @param code error code
     * @param message error message
     * @param cause the cause of the error
     */
    public BootStepException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int code() {
        return code;
    }
}

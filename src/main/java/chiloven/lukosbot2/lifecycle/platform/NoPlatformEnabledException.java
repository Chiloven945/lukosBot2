package chiloven.lukosbot2.lifecycle.platform;

import chiloven.lukosbot2.bootstrap.BootStepException;

/**
 * Thrown when no chat/message platform is enabled during bootstrap; this is a typed
 * {@link BootStepException} with a stable error code to simplify handling and logging.
 */
public class NoPlatformEnabledException extends BootStepException {
    /**
     * Stable error code for "no platform enabled".
     */
    public static final int CODE = 6;

    /**
     * Creates an exception with a default message.
     */
    public NoPlatformEnabledException() {
        super(CODE, "No platform enabled (set lukos.telegram/onebot/discord.enabled in application.yml)", null);
    }
}

package top.chiloven.lukosbot2.core.model.message.media;

/**
 * Fully loaded binary media ready to be sent to a platform SDK.
 *
 * <p>This DTO lives in core-model so platform-api implementations can return
 * loaded media without depending on core-runtime.</p>
 */
public record LoadedPlatformMedia(
        byte[] bytes,
        String name,
        String mime
) {

}

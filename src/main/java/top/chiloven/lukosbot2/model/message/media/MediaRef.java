package top.chiloven.lukosbot2.model.message.media;

/**
 * Reference to a media resource.
 */
public sealed interface MediaRef permits UrlRef, BytesRef, PlatformFileRef {

}

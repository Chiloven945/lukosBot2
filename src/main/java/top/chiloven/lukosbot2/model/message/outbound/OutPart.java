package top.chiloven.lukosbot2.model.message.outbound;

/**
 * A part of an outbound message.
 */
public sealed interface OutPart permits OutText, OutImage, OutFile {

    OutPartType type();

}

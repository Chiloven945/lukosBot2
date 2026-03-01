package top.chiloven.lukosbot2.model.message.inbound;

/**
 * A part of an inbound message.
 */
public sealed interface InPart permits InText, InImage, InFile {

    InPartType type();

}

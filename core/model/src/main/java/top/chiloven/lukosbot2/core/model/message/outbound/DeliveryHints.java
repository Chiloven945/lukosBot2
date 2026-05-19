package top.chiloven.lukosbot2.core.model.message.outbound;

/**
 * Optional hints for planning outbound messages.
 */
public record DeliveryHints(
        boolean preserveOrder,
        boolean preferSingleMessage,
        boolean preferCaption
) {

    public static DeliveryHints defaults() {
        return new DeliveryHints(
                true,
                false,
                true
        );
    }

}

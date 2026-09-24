package hotshop.service;

/** What a participant needs to do next on a sale; screens may show the description directly. */
public enum NextStep {
    CONFIRM_AFTER_HANDOVER("Meet to hand over the item, then confirm completion"),
    WAIT_FOR_CONFIRMATION("Waiting for the other participant to confirm"),
    RESPOND_TO_CANCELLATION_REQUEST("Respond to the other participant's cancellation request"),
    WAIT_FOR_CANCELLATION_RESPONSE("Waiting for the other participant to respond to your request"),
    NONE("None");

    private final String description;

    NextStep(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

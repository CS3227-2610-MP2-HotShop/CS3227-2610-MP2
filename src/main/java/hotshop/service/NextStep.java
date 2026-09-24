package hotshop.service;

/**
 * What a participant needs to do next on a sale; screens may show the description directly,
 * alongside the meetup's time and place from the sale's meetup summary.
 */
public enum NextStep {
    OFFER_MEETUP_TIMES("Offer meetup times"),
    WAIT_FOR_MEETUP_TIMES("Waiting for the seller to offer meetup times"),
    CHOOSE_MEETUP_TIME("Choose one of the offered times"),
    WAIT_FOR_MEETUP_CHOICE("Waiting for the buyer to choose a time"),
    MEET_THEN_CONFIRM("Meet at the booked time and place, then confirm completion"),
    CONFIRM_AFTER_PAST_MEETUP("Did the handover happen? Confirm completion"),
    RESPOND_TO_MOVE_PROPOSAL("Respond to the proposal to move the meetup"),
    WAIT_FOR_MOVE_RESPONSE("Waiting for them to respond to your proposed time"),
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

package hotshop.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When and where a handover happens, shared by meetup slots, meetups, and move proposals. Whether
 * a time is in the future or too far ahead depends on the clock, so services check that.
 */
public record MeetupTime(Instant startAt, Instant endAt, String location) {
    private static final Duration MIN_LENGTH = Duration.ofMinutes(15);
    private static final Duration MAX_LENGTH = Duration.ofHours(4);
    private static final int MAX_LOCATION_LENGTH = 200;

    public MeetupTime {
        Objects.requireNonNull(startAt, "Start time");
        Objects.requireNonNull(endAt, "End time");
        Duration length = Duration.between(startAt, endAt);
        if (length.compareTo(MIN_LENGTH) < 0 || length.compareTo(MAX_LENGTH) > 0) {
            throw new IllegalArgumentException("A meetup must last between " + MIN_LENGTH.toMinutes()
                    + " minutes and " + MAX_LENGTH.toHours() + " hours");
        }
        location = ModelValidation.text(location, MAX_LOCATION_LENGTH, "Meetup location");
    }

    public Duration length() {
        return Duration.between(startAt, endAt);
    }

    /** True when the two times share any moment; one ending exactly as the other starts does not overlap. */
    public boolean overlaps(MeetupTime other) {
        return startAt.isBefore(other.endAt) && other.startAt.isBefore(endAt);
    }
}

package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeetupTimeTest {
    static final Instant START = Instant.parse("2026-09-25T07:00:00Z");

    static MeetupTime lasting(long minutes) {
        return new MeetupTime(START, START.plus(Duration.ofMinutes(minutes)), "Library entrance");
    }

    @ParameterizedTest
    @ValueSource(longs = {15, 240})
    void constructor_boundaryLength_acceptsTime(long minutes) {
        assertEquals(Duration.ofMinutes(minutes), lasting(minutes).length());
    }

    @ParameterizedTest
    @ValueSource(longs = {14, 241})
    void constructor_lengthOutsideLimits_throwsException(long minutes) {
        assertThrows(IllegalArgumentException.class, () -> lasting(minutes));
    }

    @Test
    void constructor_endBeforeStart_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeetupTime(START, START.minusSeconds(60), "Library"));
    }

    @Test
    void constructor_location_trimsAndAcceptsBoundaries() {
        assertEquals("L", new MeetupTime(START, START.plusSeconds(900), " L ").location());
        assertEquals(200, new MeetupTime(START, START.plusSeconds(900), "x".repeat(200)).location().length());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void constructor_blankLocation_throwsException(String location) {
        assertThrows(IllegalArgumentException.class, () -> new MeetupTime(START, START.plusSeconds(900), location));
    }

    @Test
    void constructor_overlongLocation_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeetupTime(START, START.plusSeconds(900), "x".repeat(201)));
    }

    @Test
    void constructor_nullStart_throwsException() {
        assertThrows(NullPointerException.class, () -> new MeetupTime(null, START, "Library"));
    }

    @Test
    void overlaps_touchingAndOverlappingTimes_onlyOverlappingCount() {
        MeetupTime first = lasting(30);
        MeetupTime touching = new MeetupTime(START.plusSeconds(1800), START.plusSeconds(3600), "Library");
        MeetupTime overlapping = new MeetupTime(START.plusSeconds(1200), START.plusSeconds(3000), "Library");
        assertFalse(first.overlaps(touching));
        assertTrue(first.overlaps(overlapping));
        assertTrue(overlapping.overlaps(first));
    }
}
